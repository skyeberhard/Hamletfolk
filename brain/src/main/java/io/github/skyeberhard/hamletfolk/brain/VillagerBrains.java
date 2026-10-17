package io.github.skyeberhard.hamletfolk.brain;

import io.github.skyeberhard.hamletfolk.core.BehaviourMeter;
import io.github.skyeberhard.hamletfolk.core.BrainBehaviours;
import io.github.skyeberhard.hamletfolk.core.BrainStats;
import io.github.skyeberhard.hamletfolk.core.GuardDuty;
import io.github.skyeberhard.hamletfolk.paper.BrainModule;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.bukkit.craftbukkit.entity.CraftVillager;

/**
 * R9.1: the brain module. Adds behaviours to a villager's brain and takes them off again, leaving it as vanilla built it.
 *
 * <p>A behaviour is put straight into the brain's own table of behaviours ({@code availableBehaviorsByPriority}: priority, then
 * activity, then a set), in the CORE activity, which a villager always runs. {@code Brain.addActivity} is not used: it would
 * also replace the activity's requirements. Nothing is written into the world, and a brain the game rebuilds (on a change of
 * profession) simply comes back without them, which {@link #attach} notices and puts right.
 *
 * <p>R9.4: each behaviour has a name (from {@link BrainBehaviours#ALL}), a maker here, and its own meter: one that goes over
 * its budget stands aside by itself (the others carry on) and tells the plugin, which takes it off every villager.
 */
public final class VillagerBrains implements BrainModule {
    /** Priority of the added behaviours in CORE. If vanilla uses it too, the set is shared and only ours is ever taken out. */
    static final int PRIORITY = 2;

    /**
     * Where a behaviour goes in CORE: {@link #PRIORITY}, except the guard's (-2) and the patrol's (-1), which must come before
     * vanilla's panic trigger and walks (0 and up), the guard first.
     */
    static int priority(Behavior<Villager> behaviour) {
        return behaviour instanceof GuardBehaviour ? GuardBehaviour.PRIORITY
                : behaviour instanceof PatrolBehaviour ? PatrolBehaviour.PRIORITY
                : behaviour instanceof WorkBehaviour ? WorkBehaviour.PRIORITY : PRIORITY;
    }

    /** Makes one villager's instance of a behaviour. */
    @FunctionalInterface
    interface Maker {
        Behavior<Villager> make(VillagerBrains module, UUID villager, BrainBehaviours.Spec spec);
    }

    /** Every behaviour the module can make. Keep in step with {@link BrainBehaviours#ALL}. */
    private static final Map<String, Maker> MAKERS = Map.of(
            "attention", AttentionBehaviour::new,
            "journey", JourneyBehaviour::new,
            GuardDuty.BEHAVIOUR, GuardBehaviour::new,
            GuardDuty.PATROL, PatrolBehaviour::new,
            io.github.skyeberhard.hamletfolk.core.WorkDuty.BEHAVIOUR, WorkBehaviour::new);

    private final Field table;
    private final Map<UUID, Attachment> attached = new HashMap<>();
    private volatile boolean active;
    private BiConsumer<UUID, Throwable> faults = (id, e) -> { };
    private BiConsumer<String, String> overBudget = (name, figures) -> { };
    final BrainStats stats = new BrainStats();
    private final Map<String, BrainBehaviours.Spec> specs = new LinkedHashMap<>();
    private final Map<String, BehaviourMeter> meters = new HashMap<>();
    /** Behaviours standing aside (over their budget) until the plugin lets them run again. Main thread only. */
    private final Set<String> aside = new HashSet<>();
    private volatile boolean debug;
    private BiConsumer<UUID, String> decisions = (id, text) -> { };

    /** One villager's added behaviours by name, and the brain they were put in (a new brain means they are gone). */
    private record Attachment(Villager villager, Brain<Villager> brain, Map<String, Behavior<Villager>> behaviours) {
    }

    public VillagerBrains() throws ReflectiveOperationException {
        table = Brain.class.getDeclaredField("availableBehaviorsByPriority");
        table.setAccessible(true);
        for (BrainBehaviours.Spec spec : BrainBehaviours.ALL) {
            if (MAKERS.containsKey(spec.name())) {
                specs.put(spec.name(), spec);
                meters.put(spec.name(), new BehaviourMeter(spec.budgetMicros()));
            }
        }
    }

    @Override
    public List<String> known() {
        return List.copyOf(specs.keySet());
    }

    @Override
    public void attach(org.bukkit.entity.Villager bukkit, Set<String> wanted) {
        UUID id = bukkit.getUniqueId();
        Villager villager = ((CraftVillager) bukkit).getHandle();
        Brain<Villager> brain = villager.getBrain();
        Attachment a = attached.get(id);
        if (a != null && (a.brain() != brain || a.villager() != villager)) {
            takeAllOff(a); // a rebuilt brain lost them already; the same brain under a new entity object still has them
            attached.remove(id);
            a = null;
        }
        if (wanted.isEmpty()) {
            if (a != null) {
                takeAllOff(a);
                attached.remove(id);
            }
            return;
        }
        if (a == null) {
            a = new Attachment(villager, brain, new LinkedHashMap<>());
        }
        for (Iterator<Map.Entry<String, Behavior<Villager>>> it = a.behaviours().entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Behavior<Villager>> entry = it.next();
            if (!wanted.contains(entry.getKey())) {
                takeOff(a, entry.getValue());
                it.remove();
            }
        }
        for (String name : wanted) {
            BrainBehaviours.Spec spec = specs.get(name);
            if (spec != null && !a.behaviours().containsKey(name)) {
                Behavior<Villager> behaviour = MAKERS.get(name).make(this, id, spec);
                behaviours(brain).computeIfAbsent(priority(behaviour), p -> new HashMap<>())
                        .computeIfAbsent(Activity.CORE, x -> new LinkedHashSet<>()).add(behaviour);
                a.behaviours().put(name, behaviour);
            }
        }
        if (a.behaviours().isEmpty()) {
            attached.remove(id);
        } else {
            attached.put(id, a);
        }
    }

    @Override
    public void detach(org.bukkit.entity.Villager bukkit) {
        Attachment a = attached.remove(bukkit.getUniqueId());
        if (a != null) {
            takeAllOff(a);
        }
    }

    @Override
    public void forget(UUID villager) {
        Attachment a = attached.remove(villager); // its brain went with it
        if (a != null) {
            for (Behavior<Villager> behaviour : a.behaviours().values()) {
                if (behaviour instanceof JourneyBehaviour journey) {
                    journey.abandon("the villager left the world");
                }
            }
        }
    }

    @Override
    public boolean travel(org.bukkit.entity.Villager bukkit, io.github.skyeberhard.hamletfolk.core.Journey.Point goal,
            List<io.github.skyeberhard.hamletfolk.core.Journey.Point> route, boolean mayTeleport, int closeEnough,
            java.util.function.Consumer<io.github.skyeberhard.hamletfolk.core.Journey.Result> done) {
        Attachment a = attached.get(bukkit.getUniqueId());
        if (a == null || a.villager().getBrain() != a.brain() || !(a.behaviours().get("journey") instanceof JourneyBehaviour journey)) {
            return false;
        }
        journey.begin(bukkit, new io.github.skyeberhard.hamletfolk.core.Journey(goal, route, mayTeleport, closeEnough, journey::log), done);
        return true;
    }

    /** The guard behaviour on a villager, if it has one (main thread; the patrol asks). */
    GuardBehaviour guard(UUID villager) {
        Attachment a = attached.get(villager);
        return a != null && a.behaviours().get(GuardDuty.BEHAVIOUR) instanceof GuardBehaviour g ? g : null;
    }

    /** The pathing behaviour on a villager, if it has one. */
    JourneyBehaviour journey(UUID villager) {
        Attachment a = attached.get(villager);
        return a != null && a.behaviours().get("journey") instanceof JourneyBehaviour j ? j : null;
    }

    @Override
    public void setPatrol(UUID villager, List<io.github.skyeberhard.hamletfolk.core.PatrolRoute.Spot> route, boolean watchesNight) {
        Attachment a = attached.get(villager);
        if (a != null && a.behaviours().get(GuardDuty.PATROL) instanceof PatrolBehaviour patrol) {
            patrol.set(route, watchesNight);
        }
    }

    @Override
    public void setWork(UUID villager, io.github.skyeberhard.hamletfolk.core.PatrolRoute.Spot workplace) {
        Attachment a = attached.get(villager);
        if (a != null && a.behaviours().get(io.github.skyeberhard.hamletfolk.core.WorkDuty.BEHAVIOUR) instanceof WorkBehaviour work) {
            work.set(workplace);
        }
    }

    /** How many residents are walking to their workplaces now (main thread; each asks before it sets out, to keep the number at once down). */
    int walkingToWork() {
        int walking = 0;
        for (Attachment a : attached.values()) {
            if (a.behaviours().get(io.github.skyeberhard.hamletfolk.core.WorkDuty.BEHAVIOUR) instanceof WorkBehaviour work && work.walking()) {
                walking++;
            }
        }
        return walking;
    }

    @Override
    public void detachAll() {
        for (Attachment a : attached.values()) {
            takeAllOff(a);
        }
        attached.clear();
    }

    @Override
    public void detachEverywhere(String name) {
        for (Iterator<Attachment> it = attached.values().iterator(); it.hasNext();) {
            Attachment a = it.next();
            Behavior<Villager> behaviour = a.behaviours().remove(name);
            if (behaviour != null) {
                takeOff(a, behaviour);
            }
            if (a.behaviours().isEmpty()) {
                it.remove();
            }
        }
    }

    private void takeAllOff(Attachment a) {
        for (Behavior<Villager> behaviour : a.behaviours().values()) {
            takeOff(a, behaviour);
        }
    }

    /** Stops one of our behaviours if it is running (so it cleans up after itself) and takes it out of the brain's table. */
    private void takeOff(Attachment a, Behavior<Villager> behaviour) {
        if (behaviour instanceof JourneyBehaviour journey) {
            journey.abandon("the pathing behaviour was taken off this villager"); // whoever asked is told, brain rebuilt or not
        }
        if (a.villager().getBrain() != a.brain()) {
            return; // the game rebuilt the brain: ours went with the old one
        }
        if (behaviour instanceof GuardBehaviour guard) {
            guard.release(a.villager()); // it never runs, so doStop would not clean up after it
        }
        if (behaviour instanceof PatrolBehaviour patrol) {
            patrol.release();
        }
        if (behaviour instanceof WorkBehaviour work) {
            work.release();
        }
        if (behaviour.getStatus() == Behavior.Status.RUNNING && a.villager().level() instanceof ServerLevel level) {
            behaviour.doStop(level, a.villager(), level.getGameTime());
        }
        Map<Integer, Map<Activity, Set<BehaviorControl<? super Villager>>>> byPriority = behaviours(a.brain());
        int priority = priority(behaviour);
        Map<Activity, Set<BehaviorControl<? super Villager>>> byActivity = byPriority.get(priority);
        if (byActivity == null) {
            return;
        }
        Set<BehaviorControl<? super Villager>> set = byActivity.get(Activity.CORE);
        if (set != null) {
            set.remove(behaviour);
            if (set.isEmpty()) {
                byActivity.remove(Activity.CORE); // only if nothing of vanilla's is in it
            }
        }
        if (byActivity.isEmpty()) {
            byPriority.remove(priority);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Map<Activity, Set<BehaviorControl<? super Villager>>>> behaviours(Brain<Villager> brain) {
        try {
            return (Map<Integer, Map<Activity, Set<BehaviorControl<? super Villager>>>>) table.get(brain);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    /** Whether a behaviour may act: the module is on and the behaviour is not standing aside over its budget. */
    boolean active(String name) {
        return active && !aside.contains(name);
    }

    @Override
    public void setRunning(String name, boolean running) {
        if (running) {
            BehaviourMeter meter = meters.get(name);
            if (aside.remove(name) && meter != null) {
                meter.reset(); // only one that stood aside starts afresh: switching on what already runs keeps its figures
            }
        } else {
            aside.add(name);
        }
    }

    @Override
    public void setBudget(String name, int micros) {
        BehaviourMeter meter = meters.get(name);
        if (meter != null) {
            meter.setBudgetMicros(micros);
        }
    }

    @Override
    public void onOverBudget(BiConsumer<String, String> handler) {
        overBudget = handler;
    }

    @Override
    public void onFault(BiConsumer<UUID, Throwable> handler) {
        faults = handler;
    }

    /** A behaviour threw: it stands aside and the plugin is told (it switches the module off). Never throws itself. */
    void fault(UUID villager, Throwable error) {
        active = false;
        try {
            faults.accept(villager, error);
        } catch (Throwable ignored) {
            // the module is already standing aside; a failing report must not escape into the villager's tick
        }
    }

    @Override
    public int attached() {
        return attached.size();
    }

    @Override
    public int attached(String name) {
        int count = 0;
        for (Attachment a : attached.values()) {
            count += a.behaviours().containsKey(name) ? 1 : 0;
        }
        return count;
    }

    @Override
    public Set<UUID> villagers() {
        return Set.copyOf(attached.keySet());
    }

    @Override
    public boolean has(UUID villager) {
        return attached.containsKey(villager);
    }

    @Override
    public BrainStats stats() {
        return stats;
    }

    @Override
    public BrainStats stats(String name) {
        BehaviourMeter meter = meters.get(name);
        return meter == null ? new BrainStats() : meter.stats();
    }

    /**
     * Records one call of a behaviour: the server tick it ran in and the nanoseconds it took. If that tips the behaviour over its
     * budget, it stands aside now and the plugin is told (it takes it off every villager, between ticks).
     */
    void record(String name, long serverTick, long nanos) {
        stats.record(serverTick, nanos);
        BehaviourMeter meter = meters.get(name);
        if (meter != null && meter.record(serverTick, nanos)) {
            aside.add(name);
            try {
                overBudget.accept(name, meter.figures());
            } catch (Throwable ignored) {
                // it already stands aside; a failing report must not escape into the villager's tick
            }
        }
    }

    @Override
    public void setDebug(boolean on) {
        debug = on;
    }

    boolean debugging() {
        return debug;
    }

    @Override
    public void onDecision(BiConsumer<UUID, String> handler) {
        decisions = handler;
    }

    /** A behaviour made a decision. Only call when {@link #debugging()}; a failing handler is ignored (it is only a log). */
    void decided(UUID villager, String debugName, String text) {
        try {
            decisions.accept(villager, debugName + ": " + text);
        } catch (Throwable ignored) {
            // debug output must never break a villager's tick
        }
    }

    /** A memory's value as one short line; where a villager is walking to is worth spelling out (it has no toString of its own). */
    private static String show(Object value) {
        if (value instanceof WalkTarget walk) {
            try {
                var at = walk.getTarget().currentPosition();
                return String.format(java.util.Locale.ROOT, "walk to x=%.1f y=%.1f z=%.1f (speed %.2f, close enough at %d)", at.x(), at.y(), at.z(),
                        walk.getSpeedModifier(), walk.getCloseEnoughDist());
            } catch (Throwable e) {
                return "a walk target that could not be read: " + e;
            }
        }
        return io.github.skyeberhard.hamletfolk.core.BrainReport.shorten(value, 90);
    }

    @Override
    public Snapshot inspect(org.bukkit.entity.Villager bukkit) {
        try {
            Villager villager = ((CraftVillager) bukkit).getHandle();
            Brain<Villager> brain = villager.getBrain();
            java.util.List<String> active = new java.util.ArrayList<>();
            for (Activity a : brain.getActiveActivities()) {
                active.add(a.getName());
            }
            java.util.Collections.sort(active);
            String main = brain.getActiveNonCoreActivity().map(Activity::getName).orElse("core only");
            java.util.List<String> memories = new java.util.ArrayList<>();
            brain.forEach(new Brain.Visitor() {
                @Override
                public <U> void acceptEmpty(MemoryModuleType<U> type) {
                    // a memory with nothing in it is not shown
                }

                @Override
                public <U> void accept(MemoryModuleType<U> type, U value) {
                    memories.add(type + " = " + show(value));
                }

                @Override
                public <U> void accept(MemoryModuleType<U> type, U value, long ticksToLive) {
                    memories.add(type + " = " + show(value) + " (forgotten in " + ticksToLive + " ticks)");
                }
            });
            java.util.Collections.sort(memories);
            java.util.List<String> running = new java.util.ArrayList<>();
            for (BehaviorControl<? super Villager> b : brain.getRunningBehaviors()) {
                running.add(b.debugString());
            }
            Attachment ours = attached.get(bukkit.getUniqueId());
            java.util.List<String> added = new java.util.ArrayList<>();
            if (ours != null && ours.brain() == brain) {
                for (Map.Entry<String, Behavior<Villager>> b : ours.behaviours().entrySet()) {
                    String doing = b.getValue() instanceof JourneyBehaviour journey ? journey.describe()
                            : b.getValue() instanceof GuardBehaviour guard ? guard.describe()
                            : b.getValue() instanceof PatrolBehaviour patrol ? patrol.describe()
                            : b.getValue() instanceof WorkBehaviour work ? work.describe() : "";
                    added.add(b.getValue().debugString() + (aside.contains(b.getKey()) ? " (standing aside: over its budget)" : "")
                            + (doing.isEmpty() ? "" : " (" + doing + ")"));
                }
            }
            return new Snapshot("activity: " + main + " (active: " + String.join(", ", active) + ")", memories, running, added);
        } catch (Throwable e) {
            return new Snapshot("could not read its brain: " + e, java.util.List.of(), java.util.List.of(), java.util.List.of());
        }
    }
}
