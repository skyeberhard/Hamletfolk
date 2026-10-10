package io.github.skyeberhard.hamletfolk.brain;

import io.github.skyeberhard.hamletfolk.core.BrainBehaviours;
import io.github.skyeberhard.hamletfolk.core.Journey;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * R9.5: the shared pathing service on one villager. It holds at most one {@link Journey} and drives it every tick it has
 * one, through the villager's walk target (the brain's own way of moving it), with the journey's fallbacks when the brain
 * drops the path. Any behaviour that needs the villager somewhere asks the module ({@link VillagerBrains#travel}) and is told
 * how it ended. It never moves an asleep villager, and switching the module or the pathing family off ends the journey
 * (STOPPED, with the reason). Every call is timed and every error caught, like every added behaviour.
 */
final class JourneyBehaviour extends Behavior<Villager> {
    /** The walk speed (vanilla villagers stroll at 0.5 and go to work at 0.6). */
    private static final float SPEED = 0.6f;
    /** A journey longer than this (ten minutes) is stopped by the brain itself; the journey's own limits come long before. */
    private static final int LONGEST = 20 * 60 * 10;
    /** How far up and down a column is searched for somewhere to stand. */
    private static final int SEARCH = 6;

    private final VillagerBrains module;
    private final UUID id;
    private final String name;
    private final String debugName;
    private Journey journey;
    private Consumer<Journey.Result> done;
    private org.bukkit.entity.Villager bukkit;
    private WalkTarget ours;
    /** Game time the journey began (so only the walk failures since then are cleared). */
    private long startedAt;

    JourneyBehaviour(VillagerBrains module, UUID id, BrainBehaviours.Spec spec) {
        super(Map.of(), LONGEST, LONGEST);
        this.module = module;
        this.id = id;
        this.name = spec.name();
        this.debugName = spec.debugName();
    }

    /** A new journey (main thread). One already under way ends STOPPED first. {@code whenDone} is called once, inside a tick: it must only log or note. */
    void begin(org.bukkit.entity.Villager villager, Journey next, Consumer<Journey.Result> whenDone) {
        Journey old = journey;
        Consumer<Journey.Result> oldDone = done;
        // the new one is in place before the old caller hears: if that caller starts another journey, that one wins, not this
        bukkit = villager;
        journey = next;
        done = whenDone;
        startedAt = villager.getWorld().getGameTime();
        if (old != null) {
            report(old.stop("a new journey replaced it"), oldDone);
        }
    }

    /** Ends the journey under way, if any, as STOPPED (main thread). */
    void abandon(String why) {
        if (journey != null) {
            end(journey.stop(why));
        }
    }

    /** Where it is going, for inspect; empty if it is not going anywhere. */
    String describe() {
        return journey == null ? "" : "on a journey to " + journey.goal();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long t = System.nanoTime();
        try {
            if (journey == null) {
                return false;
            }
            if (villager.isSleeping()) {
                end(journey.stop("the villager is asleep"));
                return false;
            }
            return module.active(name);
        } catch (Throwable e) {
            module.fault(id, e);
            return false;
        } finally {
            recordSafely(level, t);
        }
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        step(level, villager);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        step(level, villager);
    }

    private void step(ServerLevel level, Villager villager) {
        long t = System.nanoTime();
        try {
            if (journey != null) {
                Optional<Journey.Result> result = journey.tick(new Mover(villager));
                result.ifPresent(this::end);
            }
        } catch (Throwable e) {
            module.fault(id, e);
        } finally {
            recordSafely(level, t);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        try {
            return journey != null && module.active(name) && !villager.isSleeping();
        } catch (Throwable e) {
            module.fault(id, e);
            return false;
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        try {
            if (journey != null) {
                end(journey.stop(villager.isSleeping() ? "the villager fell asleep"
                        : !module.active(name) ? "the brain module or the pathing behaviour was switched off"
                        : timedOut(gameTime) ? "the brain stopped it (ten minutes)"
                        : "the game stopped the villager's brain (a change of job, or a new brain)"));
            }
            if (ours != null && villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == ours) {
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET); // leave no walk target of ours behind
            }
            // nor our failures: vanilla lets go of a workstation or bed it has "not reached" for a long time
            if (ours != null && villager.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                    && villager.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).orElse(Long.MIN_VALUE) >= startedAt) {
                villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            }
            ours = null;
        } catch (Throwable e) {
            module.fault(id, e);
        }
    }

    /** The journey is over: tell whoever asked for it (once; a failing handler is ignored, it must not cost the villager its tick). */
    private void end(Journey.Result result) {
        Consumer<Journey.Result> whenDone = done;
        journey = null;
        done = null;
        bukkit = null; // hold no entity after the journey
        report(result, whenDone);
    }

    private static void report(Journey.Result result, Consumer<Journey.Result> whenDone) {
        if (whenDone != null) {
            try {
                whenDone.accept(result);
            } catch (Throwable ignored) {
                // the caller's report failed; the journey is over all the same
            }
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

    /** Line for the debug log, if it is on. */
    void log(String line) {
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

    /** The villager, as a journey sees it: its walk target, the blocks around it (through the public API) and a safe teleport. */
    private final class Mover implements Journey.Mover {
        private final Villager villager;

        Mover(Villager villager) {
            this.villager = villager;
        }

        @Override
        public Journey.Point position() {
            BlockPos p = villager.blockPosition();
            return new Journey.Point(p.getX(), p.getY(), p.getZ());
        }

        @Override
        public void walkTo(Journey.Point target, int closeEnough) {
            ours = new WalkTarget(new BlockPos(target.x(), target.y(), target.z()), SPEED, closeEnough);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, ours);
        }

        @Override
        public boolean walking() {
            return ours != null && villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == ours;
        }

        /** Solid below, two blocks a villager can pass through, no water or lava; never in a chunk that is not loaded (that would load it). */
        @Override
        public Optional<Journey.Point> standingNear(Journey.Point point) {
            World world = bukkit.getWorld();
            if (!world.isChunkLoaded(point.x() >> 4, point.z() >> 4)) {
                return Optional.empty();
            }
            for (int i = 0; i <= 2 * SEARCH; i++) {
                int y = point.y() + (i % 2 == 0 ? i / 2 : -(i + 1) / 2); // 0, -1, +1, -2, +2 ...
                if (y - 1 < world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
                    continue;
                }
                Block feet = world.getBlockAt(point.x(), y, point.z());
                Block head = world.getBlockAt(point.x(), y + 1, point.z());
                Block below = world.getBlockAt(point.x(), y - 1, point.z());
                if (below.getType().isSolid() && feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()) {
                    return Optional.of(new Journey.Point(point.x(), y, point.z()));
                }
            }
            return Optional.empty();
        }

        @Override
        public boolean teleport(Journey.Point point) {
            Optional<Journey.Point> spot = standingNear(point);
            if (spot.isEmpty()) {
                return false;
            }
            Journey.Point s = spot.get();
            // the game's own safe teleport (the one endermen use): it checks for room and liquid, and puts it back if there is none
            boolean moved = villager.randomTeleport(s.x() + 0.5, s.y(), s.z() + 0.5, false);
            if (moved && walking()) {
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                ours = null;
            }
            return moved;
        }
    }
}
