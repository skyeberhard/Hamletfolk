package io.github.skyeberhard.hamletfolk.brain;

import io.github.skyeberhard.hamletfolk.core.BrainBehaviours;
import io.github.skyeberhard.hamletfolk.core.Journey;
import io.github.skyeberhard.hamletfolk.core.PatrolRoute;
import io.github.skyeberhard.hamletfolk.core.WorkDuty;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * R4.17: a resident whose trade has a registered workplace walks there in the village's working hours (the villager's own schedule
 * says when: its WORK activity), stays a while swinging its arm and looking at the place, and then goes about its day as any villager
 * does, until the next visit. The plugin gives it the place ({@link WorkDuty}) and only while a player is near; this walks it through the
 * shared pathing service (R9.5). It is presentation only: it reads nothing of the village and changes nothing in it. Never more than
 * {@link WorkDuty#MAX_WALKING} walk at once on the server, and it gives way to anything urgent (a trade, a panic, hiding, a raid).
 *
 * <p>Like {@link PatrolBehaviour} it never "runs": it works in {@link #checkExtraStartConditions} at a priority just before vanilla's
 * walks, and always answers false. Every call is timed and every error caught.
 */
final class WorkBehaviour extends Behavior<Villager> {
    /** Before vanilla's behaviours (0 and up), as the patrol: after the guard behaviour (-2), which a worker has none of. */
    static final int PRIORITY = -1;
    /** Ticks between swings of the arm while working. */
    private static final int SWING_EVERY = 30;
    /** Ticks between turning its head to the place again (the brain lets it drift back to whatever it was looking at). */
    private static final int LOOK_EVERY = 10;
    /** Ticks to wait before trying again after a place it could not reach. */
    private static final int RETRY_TICKS = 400;

    private final VillagerBrains module;
    private final UUID id;
    private final String name;
    private final String debugName;
    private final Random random;
    private PatrolRoute.Spot place;
    private boolean travelling;
    private long workUntil;
    private long nextSwing;
    private long nextLook;
    private long waitUntil;

    WorkBehaviour(VillagerBrains module, UUID id, BrainBehaviours.Spec spec) {
        super(Map.of(), 60, 60); // (it never runs: everything is in checkExtraStartConditions)
        this.module = module;
        this.id = id;
        this.name = spec.name();
        this.debugName = spec.debugName();
        this.random = new Random(id.getLeastSignificantBits() * 31 ^ id.getMostSignificantBits());
        this.waitUntil = Long.MIN_VALUE; // (the first visit is as soon as it is time for work)
    }

    /** The plugin's word on where this resident works, or null for nowhere (main thread, between ticks). */
    void set(PatrolRoute.Spot workplace) {
        if (workplace == null ? place != null : !workplace.equals(place)) {
            place = workplace;
            if (travelling) {
                stopWalking("its workplace changed");
            }
            workUntil = 0;
        }
    }

    /** Whether it is walking to its workplace now (the module counts these to keep the number at once down). */
    boolean walking() {
        return travelling;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long t = System.nanoTime();
        try {
            work(level, villager);
        } catch (Throwable e) {
            module.fault(id, e);
        } finally {
            recordSafely(level, t);
        }
        return false;
    }

    private void work(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        // (the world's clock, not vanilla's WORK activity: that is only active for a villager that has claimed a workstation, which
        // not every resident the simulation gives a trade to has)
        boolean atWorkTime = WorkDuty.workHours(((org.bukkit.entity.Villager) villager.getBukkitEntity()).getWorld().getTime())
                && !villager.getBrain().isActive(Activity.REST);
        if (!module.active(name) || place == null || villager.isTrading() || villager.isSleeping() || inDanger(villager) || !atWorkTime) {
            if (travelling) {
                stopWalking(!atWorkTime ? "the working day is over" : "something else came first");
            }
            workUntil = 0;
            return;
        }
        if (workUntil > now) { // at the workplace: swing, and look at it, until the time is up or it has wandered off
            if (now >= nextLook) {
                nextLook = now + LOOK_EVERY;
                look(villager);
            }
            if (now >= nextSwing) {
                nextSwing = now + SWING_EVERY;
                ((org.bukkit.entity.Villager) villager.getBukkitEntity()).swingMainHand();
            }
            if (!WorkDuty.atWork(distance(villager))) {
                workUntil = 0;
                waitUntil = now + WorkDuty.rest(random);
                log("wandered away from its workplace");
            }
            return;
        }
        if (workUntil != 0) { // the time at the workplace is up
            workUntil = 0;
            waitUntil = now + WorkDuty.rest(random);
            log("done at the workplace, back to its day for " + (waitUntil - now) / 20 + " seconds");
            return;
        }
        if (travelling || now < waitUntil) {
            return;
        }
        JourneyBehaviour journey = module.journey(id);
        if (journey == null || journey.busy() || !WorkDuty.maySetOut(module.walkingToWork())) {
            return; // no pathing behaviour, someone else has the villager on a journey, or enough are walking already
        }
        int ground = place.y() == PatrolRoute.NO_GROUND ? villager.blockPosition().getY() : place.y();
        Journey.Point goal = new Journey.Point(place.x(), ground, place.z());
        travelling = true;
        log("walking to its workplace (" + place + ")");
        journey.begin((org.bukkit.entity.Villager) villager.getBukkitEntity(),
                new Journey(goal, List.of(), false, WorkDuty.CLOSE_ENOUGH, journey::log), result -> arrived(level, result));
    }

    /** The journey is over (inside a tick, or from a take-off): work if it got there, else try again later. */
    private void arrived(ServerLevel level, Journey.Result result) {
        travelling = false;
        long now = level.getGameTime();
        if (result.outcome().arrived()) {
            workUntil = now + WorkDuty.dwell(random);
            nextSwing = now;
            nextLook = now;
            log("at its workplace, working for " + (workUntil - now) / 20 + " seconds");
        } else {
            waitUntil = now + RETRY_TICKS;
            log("did not get there (" + result.describe() + ")");
        }
    }

    /** Turns its head to the place, through the brain's look target (a Bukkit look is overwritten by the brain within a tick or two). */
    private void look(Villager villager) {
        int y = place.y() == PatrolRoute.NO_GROUND ? villager.blockPosition().getY() : place.y();
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(new BlockPos(place.x(), y, place.z())));
    }

    private double distance(Villager villager) {
        return Math.hypot(villager.getX() - (place.x() + 0.5), villager.getZ() - (place.z() + 0.5));
    }

    /** Panicking, hiding or in a raid: not the time for a visit. */
    private static boolean inDanger(Villager villager) {
        var brain = villager.getBrain();
        return brain.isActive(Activity.PANIC) || brain.isActive(Activity.HIDE) || brain.isActive(Activity.PRE_RAID)
                || brain.isActive(Activity.RAID);
    }

    private void stopWalking(String why) {
        travelling = false;
        JourneyBehaviour journey = module.journey(id);
        if (journey != null) {
            journey.abandon("work stopped: " + why);
        }
    }

    /** Taken off this villager (main thread, between ticks): end the walk, if any. */
    void release() {
        try {
            if (travelling) {
                stopWalking("the work behaviour was taken off");
            }
            workUntil = 0;
            waitUntil = Long.MIN_VALUE;
        } catch (Throwable e) {
            module.fault(id, e);
        }
    }

    /** For inspect. */
    String describe() {
        if (place == null) {
            return "no workplace";
        }
        return travelling ? "walking to its workplace" : workUntil != 0 ? "working at its workplace" : "going about its day";
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
