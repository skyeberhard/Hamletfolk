package io.github.skyeberhard.hamletfolk.brain;

import io.github.skyeberhard.hamletfolk.core.BrainBehaviours;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;

/**
 * R9.1: the module's test behaviour, there to show that adding and taking off behaviours works: a villager of a village
 * stops and turns to look at a player who comes within three blocks, for two to five seconds, then carries on for at least
 * ten before it will stop for them again. Only while it is idling, working or meeting (never when it is panicking, hiding in a
 * raid, resting, trading or asleep). Harmless, and easy to see: with the module off they wander past you as vanilla
 * villagers do. Every call is timed and every error (any Throwable: an error escaping a villager's tick makes Paper remove
 * the villager) is caught and reported, so a fault switches the module off instead of costing the village a villager.
 */
final class AttentionBehaviour extends Behavior<Villager> {
    private static final double NOTICE = 3.0;
    private static final double LOSE_INTEREST = 6.0;
    /** Ticks after it lets a player go before it will stop for one again. */
    private static final long REST_TICKS = 200;
    private static final java.util.Set<Activity> ORDINARY = java.util.Set.of(Activity.IDLE, Activity.WORK, Activity.MEET);

    private final VillagerBrains module;
    private final UUID id;
    /** R9.4: its name in the config and its debug name ("hamletfolk:stewards/attention"). */
    private final String name;
    private final String debugName;
    private Player watching;
    private long restUntil;

    AttentionBehaviour(VillagerBrains module, UUID id, BrainBehaviours.Spec spec) {
        super(Map.of(), 40, 100);
        this.module = module;
        this.id = id;
        this.name = spec.name();
        this.debugName = spec.debugName();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long t = System.nanoTime();
        try {
            if (!module.active(name) || villager.isSleeping() || villager.isTrading() || level.getGameTime() < restUntil
                    || !villager.getBrain().getActiveNonCoreActivity().map(ORDINARY::contains).orElse(false)) {
                return false;
            }
            Player near = level.getNearestPlayer(villager, NOTICE);
            if (near == null || near.isSpectator()) {
                return false;
            }
            watching = near;
            if (module.debugging()) {
                module.decided(id, debugName, "noticed " + near.getScoreboardName() + " " + Math.round(Math.sqrt(villager.distanceToSqr(near)) * 10) / 10.0
                        + " blocks away: will look at them");
            }
            return true;
        } catch (Throwable e) {
            module.fault(id, e);
            return false;
        } finally {
            recordSafely(level, t); // (the server's tick count: each world has its own game time)
        }
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        face(level, villager);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        face(level, villager);
    }

    private void face(ServerLevel level, Villager villager) {
        long t = System.nanoTime();
        try {
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(watching, true));
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET); // stand still while looking
        } catch (Throwable e) {
            module.fault(id, e);
        } finally {
            recordSafely(level, t);
        }
    }

    /** Timing must never be what throws into a villager's tick. */
    private void recordSafely(ServerLevel level, long startedAt) {
        try {
            module.record(name, level.getServer().getTickCount(), System.nanoTime() - startedAt);
        } catch (Throwable e) {
            module.fault(id, e);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        try {
            return module.active(name) && watching != null && watching.isAlive() && !watching.isRemoved() && !villager.isTrading()
                    && !villager.isSleeping() && watching.level() == villager.level()
                    && villager.distanceToSqr(watching) < LOSE_INTEREST * LOSE_INTEREST
                    && villager.getBrain().getActiveNonCoreActivity().map(ORDINARY::contains).orElse(false);
        } catch (Throwable e) {
            module.fault(id, e);
            return false;
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        try {
            if (villager.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET)) {
                villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            }
        } catch (Throwable e) {
            module.fault(id, e);
        } finally {
            try {
                if (module.debugging()) {
                    module.decided(id, debugName, "stopped looking at " + (watching == null ? "nobody" : watching.getScoreboardName())
                            + ": will not stop for anyone for " + REST_TICKS / 20 + " seconds");
                }
            } catch (Throwable ignored) {
                // a debug line must never cost the villager its tick
            }
            watching = null;
            restUntil = gameTime + REST_TICKS;
        }
    }

    @Override
    public String debugString() {
        return debugName;
    }
}
