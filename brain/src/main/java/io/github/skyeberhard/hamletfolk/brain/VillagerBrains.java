package io.github.skyeberhard.hamletfolk.brain;

import io.github.skyeberhard.hamletfolk.paper.BrainModule;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
 */
public final class VillagerBrains implements BrainModule {
    /** Priority of the added behaviours in CORE. If vanilla uses it too, the set is shared and only ours is ever taken out. */
    static final int PRIORITY = 2;

    private final Field table;
    private final Map<UUID, Attachment> attached = new HashMap<>();
    private volatile boolean active;
    private BiConsumer<UUID, Throwable> faults = (id, e) -> { };
    final io.github.skyeberhard.hamletfolk.core.BrainStats stats = new io.github.skyeberhard.hamletfolk.core.BrainStats();
    private volatile boolean debug;
    private BiConsumer<UUID, String> decisions = (id, text) -> { };

    /** One villager's added behaviours, and the brain they were put in (a new brain means they are gone). */
    private record Attachment(Villager villager, Brain<Villager> brain, Behavior<Villager> behaviour) {
    }

    public VillagerBrains() throws ReflectiveOperationException {
        table = Brain.class.getDeclaredField("availableBehaviorsByPriority");
        table.setAccessible(true);
    }

    @Override
    public void attach(org.bukkit.entity.Villager bukkit) {
        Villager villager = ((CraftVillager) bukkit).getHandle();
        Brain<Villager> brain = villager.getBrain();
        Attachment old = attached.get(bukkit.getUniqueId());
        if (old != null && old.brain() == brain && old.villager() == villager) {
            return; // already there
        }
        if (old != null && old.brain() == brain) {
            remove(old); // (the same brain under a new entity object: take ours out before putting them back)
        }
        Behavior<Villager> behaviour = new AttentionBehaviour(this, bukkit.getUniqueId());
        behaviours(brain).computeIfAbsent(PRIORITY, p -> new HashMap<>())
                .computeIfAbsent(Activity.CORE, a -> new LinkedHashSet<>()).add(behaviour);
        attached.put(bukkit.getUniqueId(), new Attachment(villager, brain, behaviour));
    }

    @Override
    public void detach(org.bukkit.entity.Villager bukkit) {
        Attachment a = attached.remove(bukkit.getUniqueId());
        if (a != null) {
            remove(a);
        }
    }

    @Override
    public void forget(UUID villager) {
        attached.remove(villager); // its brain went with it
    }

    @Override
    public void detachAll() {
        for (Attachment a : attached.values()) {
            remove(a);
        }
        attached.clear();
    }

    /** Stops our behaviour if it is running (so it cleans up after itself) and takes it out of the brain's table. */
    private void remove(Attachment a) {
        if (a.villager().getBrain() != a.brain()) {
            return; // the game rebuilt the brain: ours went with the old one
        }
        if (a.behaviour().getStatus() == Behavior.Status.RUNNING && a.villager().level() instanceof ServerLevel level) {
            a.behaviour().doStop(level, a.villager(), level.getGameTime());
        }
        Map<Integer, Map<Activity, Set<BehaviorControl<? super Villager>>>> byPriority = behaviours(a.brain());
        Map<Activity, Set<BehaviorControl<? super Villager>>> byActivity = byPriority.get(PRIORITY);
        if (byActivity == null) {
            return;
        }
        Set<BehaviorControl<? super Villager>> set = byActivity.get(Activity.CORE);
        if (set != null) {
            set.remove(a.behaviour());
            if (set.isEmpty()) {
                byActivity.remove(Activity.CORE); // only if nothing of vanilla's is in it
            }
        }
        if (byActivity.isEmpty()) {
            byPriority.remove(PRIORITY);
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

    boolean active() {
        return active;
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
    public io.github.skyeberhard.hamletfolk.core.BrainStats stats() {
        return stats;
    }

    /** Records one call of an added behaviour: the game tick it ran in and the nanoseconds it took. */
    void record(long gameTick, long nanos) {
        stats.record(gameTick, nanos);
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
    void decided(UUID villager, String text) {
        try {
            decisions.accept(villager, text);
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
            java.util.List<String> added = ours != null && ours.brain() == brain ? java.util.List.of(ours.behaviour().debugString())
                    : java.util.List.of();
            return new Snapshot("activity: " + main + " (active: " + String.join(", ", active) + ")", memories, running, added);
        } catch (Throwable e) {
            return new Snapshot("could not read its brain: " + e, java.util.List.of(), java.util.List.of(), java.util.List.of());
        }
    }
}
