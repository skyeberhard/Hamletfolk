package io.github.skyeberhard.hamletfolk.brain;

import io.github.skyeberhard.hamletfolk.core.BrainBehaviours;
import io.github.skyeberhard.hamletfolk.core.Journey;
import io.github.skyeberhard.hamletfolk.core.PatrolRoute;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * R9.6: a guard on duty walks the village between fights. The plugin gives it the route ({@link PatrolRoute}: the square and
 * the streets, as a loop) and whether it keeps the night watch; this walks it, one spot after another, through the shared
 * pathing service (R9.5), staying a while at each (long by day, hardly at all at night, when a villager with no walk target
 * would head for its bed). A guard that keeps the night watch is woken at dusk and walks until dawn; the others sleep as any
 * villager does, and patrol by day. It gives way to the guard behaviour: a guard with a foe, or retreating, stops walking
 * and its journey is ended, and it never patrols while it is trading, panicking, hiding or in a raid.
 *
 * <p>Like {@link GuardBehaviour} it never "runs": it works in {@link #checkExtraStartConditions} at a priority just before
 * vanilla's walks, and always answers false. Every call is timed and every error caught.
 */
final class PatrolBehaviour extends Behavior<Villager> {
    /** After the guard behaviour (-2) and before vanilla's behaviours (0 and up). */
    static final int PRIORITY = -1;
    /** The most often a sleeping guard is woken for the night watch (a bed it goes back to would otherwise wake it every tick). */
    private static final int WAKE_EVERY = 200;
    /** Ticks to wait after a spot it could not reach (or a fight) before it tries the next. */
    private static final int RETRY_TICKS = 100;
    /** The same at night, when waiting means going to bed: the next spot at once. */
    private static final int RETRY_NIGHT_TICKS = 20;
    /** How near (blocks) counts as being at a spot. */
    private static final int CLOSE_ENOUGH = 2;

    private final VillagerBrains module;
    private final UUID id;
    private final String name;
    private final String debugName;
    private final Random random;
    private List<PatrolRoute.Spot> route = List.of();
    private boolean nightWatch = true;
    private int index = -1;
    private boolean travelling;
    private long waitUntil;
    private long wokeAt = Long.MIN_VALUE / 2;

    PatrolBehaviour(VillagerBrains module, UUID id, BrainBehaviours.Spec spec) {
        super(Map.of(), 60, 60); // (it never runs: everything is in checkExtraStartConditions)
        this.module = module;
        this.id = id;
        this.name = spec.name();
        this.debugName = spec.debugName();
        this.random = new Random(id.getLeastSignificantBits() ^ id.getMostSignificantBits());
    }

    /** The plugin's word on where to walk and whether this guard keeps the night watch (main thread, between ticks). */
    void set(List<PatrolRoute.Spot> spots, boolean watchesNight) {
        if (!spots.equals(route)) {
            route = List.copyOf(spots);
            index = -1; // a new route: start again at this guard's own place on it
        }
        nightWatch = watchesNight;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long t = System.nanoTime();
        try {
            patrol(level, villager);
        } catch (Throwable e) {
            module.fault(id, e);
        } finally {
            recordSafely(level, t);
        }
        return false;
    }

    private void patrol(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        GuardBehaviour guard = module.guard(id);
        boolean busy = (guard != null && guard.busy()) || villager.isTrading() || !module.active(name) || route.isEmpty()
                || inDanger(villager);
        if (busy) {
            if (travelling) {
                stopWalking("busy");
                waitUntil = now + RETRY_NIGHT_TICKS; // (a fight ended, say: back to the patrol soon, not off to bed)
            }
            return;
        }
        // night is the villager's own bedtime (its schedule), not a clock: a guard on the night watch is up exactly when it would
        // have gone to bed
        boolean night = villager.getBrain().isActive(Activity.REST);
        if (villager.isSleeping()) {
            if (!(night && nightWatch)) {
                return; // off shift: it sleeps like any villager
            }
            if (now - wokeAt < WAKE_EVERY) {
                return;
            }
            wokeAt = now;
            villager.stopSleeping();
            waitUntil = now; // out of bed and on its way at once: a guard left standing would walk straight back to bed
            log("woken for the night watch");
        } else if (night && !nightWatch) {
            if (travelling) {
                stopWalking("off shift"); // the others take the night: it goes home to bed
            }
            return;
        }
        if (travelling || now < waitUntil) {
            return;
        }
        JourneyBehaviour journey = module.journey(id);
        if (journey == null || journey.busy()) {
            return; // no pathing behaviour, or someone else has the villager on a journey
        }
        index = index < 0 ? PatrolRoute.startIndex(id, route.size()) : PatrolRoute.next(index, route.size());
        PatrolRoute.Spot spot = route.get(index % route.size());
        int ground = spot.y() == PatrolRoute.NO_GROUND ? villager.blockPosition().getY() : spot.y();
        Journey.Point goal = new Journey.Point(spot.x(), ground, spot.z());
        travelling = true;
        log("walking to spot " + (index % route.size() + 1) + " of " + route.size() + " (" + spot + ")");
        journey.begin((org.bukkit.entity.Villager) villager.getBukkitEntity(),
                new Journey(goal, List.of(), false, CLOSE_ENOUGH, journey::log), result -> arrived(level, result, night));
    }

    /** Panicking, hiding or in a raid: not the time for a stroll. */
    private static boolean inDanger(Villager villager) {
        var brain = villager.getBrain();
        return brain.isActive(Activity.PANIC) || brain.isActive(Activity.HIDE) || brain.isActive(Activity.PRE_RAID)
                || brain.isActive(Activity.RAID);
    }

    /** The journey is over (inside a tick, or from a take-off): note it and wait a while at the spot. */
    private void arrived(ServerLevel level, Journey.Result result, boolean night) {
        travelling = false;
        long now = level.getGameTime();
        waitUntil = now + (result.outcome().arrived() ? PatrolRoute.dwell(night, random) : night ? RETRY_NIGHT_TICKS : RETRY_TICKS);
        log(result.outcome().arrived() ? "at the spot, waiting " + (waitUntil - now) / 20 + " seconds" : "did not get there (" + result.describe() + ")");
    }

    private void stopWalking(String why) {
        travelling = false;
        JourneyBehaviour journey = module.journey(id);
        if (journey != null) {
            journey.abandon("the patrol stopped: " + why);
        }
    }

    /** Taken off this villager (main thread, between ticks): end the walk, if any. */
    void release() {
        try {
            if (travelling) {
                stopWalking("the patrol behaviour was taken off");
            }
            index = -1;
            waitUntil = 0;
        } catch (Throwable e) {
            module.fault(id, e);
        }
    }

    /** For inspect. */
    String describe() {
        if (route.isEmpty()) {
            return "no route";
        }
        return travelling ? "walking to spot " + (index % route.size() + 1) + " of " + route.size()
                : "waiting at spot " + (Math.max(index, 0) % route.size() + 1) + " of " + route.size()
                        + (nightWatch ? ", keeps the night watch" : ", sleeps at night");
    }

    private void recordSafely(ServerLevel level, long startedAt) {
        try {
            module.record(name, level.getServer().getTickCount(), System.nanoTime() - startedAt);
        } catch (Throwable e) {
            module.fault(id, e);
        }
    }

    private void log(String line) {
        try {
            if (module.debugging()) {
                module.decided(id, debugName, line);
            }
        } catch (Throwable ignored) {
            // a debug line must never cost the villager its tick
        }
    }

    @Override
    public String debugString() {
        return debugName;
    }
}
