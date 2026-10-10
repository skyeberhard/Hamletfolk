package io.github.skyeberhard.hamletfolk.brain;

import io.github.skyeberhard.hamletfolk.core.BrainBehaviours;
import io.github.skyeberhard.hamletfolk.core.GuardDuty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raider;

/**
 * R9.3: a guard on duty fights. The plugin gives this behaviour only to a resident the simulation has made a guard, while its
 * village has tools in its stores ({@link GuardDuty#onDuty}); this acts it out with the rules in {@link GuardDuty}.
 *
 * <p>A villager's own brain makes it panic and run from any monster near it or anything that hurt it (the panic trigger in
 * CORE, priority 0, reads the memories NEAREST_HOSTILE and HURT_BY, which the brain's sensors fill in just before). This
 * behaviour sits at priority {@link #PRIORITY}, just before it, and clears those two memories for a guard that is fit to fight,
 * so the guard stands its ground; then it goes for the nearest zombie or raider (never a zombified piglin) and strikes it when
 * in reach. A badly hurt guard leaves them alone, so its own panic takes it away: that is its retreat.
 *
 * <p>It never "runs" in the brain's sense: everything happens in {@link #checkExtraStartConditions}, which the brain calls once
 * a tick at this priority, and it always answers false. Chasing a monster that moves is the one place an added behaviour sets
 * WALK_TARGET itself (with the monster as the target, which the brain's walking follows); the pathing service is for places.
 * Every call is timed (less its strikes: a kill runs the monster's death and everyone's listeners) and every error caught.
 */
final class GuardBehaviour extends Behavior<Villager> {
    /** Before vanilla's panic trigger (priority 0 in CORE). */
    static final int PRIORITY = -1;
    /** Run at, a little faster than a villager's ordinary walk (0.5) and its walk to work (0.6). */
    private static final float SPEED = 0.75f;
    /** Ticks between looks around for monsters. */
    private static final int SCAN_TICKS = 10;

    private final VillagerBrains module;
    private final UUID id;
    private final String name;
    private final String debugName;
    private LivingEntity foe;
    private GuardDuty.Foe seen;
    /** Where the fight began: a chase ends {@link GuardDuty#LEASH} from it (held until it is back, or a while has passed). */
    private boolean hasPost;
    private double postX;
    private double postZ;
    private boolean retreating;
    private long nextScan;
    private long nextStrike;
    private long lastFight;
    private long lastHeal;
    /** Nanoseconds of this call spent in a strike (a kill runs the monster's death, loot and every plugin's listeners): not ours. */
    private long excluded;
    private WalkTarget ours;

    GuardBehaviour(VillagerBrains module, UUID id, BrainBehaviours.Spec spec) {
        super(Map.of(), 60, 60); // (it never runs: everything is in checkExtraStartConditions)
        this.module = module;
        this.id = id;
        this.name = spec.name();
        this.debugName = spec.debugName();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long t = System.nanoTime();
        excluded = 0;
        try {
            if (!module.active(name) || villager.isSleeping()) {
                letGo(villager, null);
                return false;
            }
            long now = level.getGameTime();
            // villagers do not heal by themselves: a guard out of a fight does, slowly, so a retreat is not for ever
            if (GuardDuty.heals(foe != null, now - Math.max(lastHeal, lastFight)) && villager.getHealth() < villager.getMaxHealth()) {
                villager.heal(GuardDuty.HEAL);
                lastHeal = now;
            }
            double health = villager.getHealth() / villager.getMaxHealth();
            boolean was = retreating;
            retreating = GuardDuty.retreats(health, retreating);
            if (retreating) {
                if (!was) {
                    letGo(villager, null);
                    log("badly hurt (" + Math.round(health * 100) + "% health): retreating");
                }
                return false; // its own panic takes it away
            }
            if (was) {
                log("has " + Math.round(health * 100) + "% health back: on duty again");
            }
            standItsGround(villager);
            if (villager.isTrading()) {
                letGo(villager, null); // never walk off from a player in the middle of a trade
                return false;
            }
            fight(level, villager, now);
        } catch (Throwable e) {
            module.fault(id, e);
        } finally {
            recordSafely(level, t);
        }
        return false;
    }

    /**
     * The panic trigger that comes next sees nothing to run from, as far as the monsters it fights go. Anything else (a vex, a
     * zoglin, an arrow, fire, a player who hit it) is left in place, so the guard still runs from what it will not fight.
     */
    private void standItsGround(Villager villager) {
        var brain = villager.getBrain();
        if (brain.getMemory(MemoryModuleType.NEAREST_HOSTILE).filter(GuardBehaviour::hostile).isPresent()) {
            brain.eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
        }
        if (brain.hasMemoryValue(MemoryModuleType.HURT_BY)
                && brain.getMemory(MemoryModuleType.HURT_BY_ENTITY).filter(GuardBehaviour::hostile).isPresent()) {
            brain.eraseMemory(MemoryModuleType.HURT_BY);
            brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        }
    }

    private void fight(ServerLevel level, Villager villager, long now) {
        if (foe != null && (!foe.isAlive() || foe.isRemoved() || foe.level() != villager.level())) {
            log(foe.isAlive() ? "lost sight of " + label(foe) : label(foe) + " is dead");
            letGo(villager, null);
            nextScan = now; // look around again at once
        }
        if (now >= nextScan) {
            nextScan = now + SCAN_TICKS;
            choose(level, villager);
        }
        if (foe == null) {
            if (hasPost && !GuardDuty.keepsPost(Math.hypot(villager.getX() - postX, villager.getZ() - postZ), now - lastFight)) {
                hasPost = false;
            }
            return;
        }
        lastFight = now;
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(foe, true));
        double distance = Math.sqrt(villager.distanceToSqr(foe));
        if (GuardDuty.inReach(distance) && villager.hasLineOfSight(foe)) {
            if (now >= nextStrike) {
                nextStrike = now + GuardDuty.STRIKE_TICKS;
                villager.swing(InteractionHand.MAIN_HAND);
                long s = System.nanoTime();
                boolean hit = foe.hurtServer(level, level.damageSources().mobAttack(villager), GuardDuty.DAMAGE);
                excluded += System.nanoTime() - s;
                log((hit ? "struck " : "swung at ") + label(foe) + " (" + Math.round(foe.getHealth()) + " health left)");
            }
        } else if (ours == null || villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) != ours) {
            ours = new WalkTarget(foe, SPEED, 1);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, ours);
        }
    }

    /** Looks around: the monster it can see to go for (keeping the one it has unless another is much nearer), or none. */
    private void choose(ServerLevel level, Villager villager) {
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(GuardDuty.NOTICE),
                e -> hostile(e) && villager.hasLineOfSight(e));
        List<GuardDuty.Foe> foes = new ArrayList<>(near.size());
        for (LivingEntity e : near) {
            foes.add(new GuardDuty.Foe(e.getId(), e.getX(), e.getY(), e.getZ()));
        }
        double px = hasPost ? postX : villager.getX();
        double pz = hasPost ? postZ : villager.getZ();
        Optional<GuardDuty.Foe> pick = GuardDuty.choose(foes, villager.getX(), villager.getY(), villager.getZ(), px, pz, seen);
        if (pick.isEmpty()) {
            if (foe != null) {
                log("let " + label(foe) + " go: out of sight, or too far from its post");
            }
            letGo(villager, null);
            return;
        }
        if (!hasPost) {
            hasPost = true; // where this fight began
            postX = px;
            postZ = pz;
        }
        LivingEntity next = near.get(foes.indexOf(pick.get()));
        if (next != foe) {
            log((foe == null ? "saw " : "turned to ") + label(next) + " " + Math.round(Math.sqrt(villager.distanceToSqr(next)))
                    + " blocks away: going for it");
            ours = null; // a new walk target for the new foe
        }
        foe = next;
        seen = pick.get();
    }

    /**
     * Zombies, husks and drowned, and raiders. Never a zombified piglin (it leaves villagers alone) or a zombie villager (a player
     * may be curing it, and it may be one of the village's own, kept on record to come back).
     */
    private static boolean hostile(LivingEntity e) {
        return e.isAlive() && ((e instanceof Zombie && !(e instanceof ZombifiedPiglin) && !(e instanceof ZombieVillager))
                || e instanceof Raider);
    }

    /** Stops fighting: takes off the walk target it set (only its own), and forgets the foe. */
    void letGo(Villager villager, String why) {
        if (ours != null && villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == ours) {
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        ours = null;
        foe = null;
        seen = null;
        if (why != null) {
            log(why);
        }
    }

    /** Taken off this villager (main thread, between ticks): leave nothing of the fight behind. */
    void release(Villager villager) {
        try {
            letGo(villager, null);
            retreating = false;
            hasPost = false;
        } catch (Throwable e) {
            module.fault(id, e);
        }
    }

    /** Where it is, for inspect. */
    String describe() {
        LivingEntity f = foe;
        return retreating ? "retreating, badly hurt" : f == null ? "on watch" : "fighting " + label(f);
    }

    private static String label(LivingEntity e) {
        return e.getType().toShortString();
    }

    private void recordSafely(ServerLevel level, long startedAt) {
        try {
            module.record(name, level.getServer().getTickCount(), System.nanoTime() - startedAt - excluded);
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
