package io.github.skyeberhard.hamletfolk.core;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * R9.3: the rules a guard (R5.1) fights by, for the brain module's guard behaviour. The simulation decides who is on duty (a
 * resident whose trade is guard, while the village has tools in its stores); the behaviour acts it out: it goes for the nearest
 * hostile monster near it, strikes when in reach, and retreats (lets the villager's own panic take over) when badly hurt, until
 * it has some health back. Villagers do not heal by themselves, so a guard out of a fight heals slowly ({@link #heals}); and it
 * holds its post: a chase ends {@link #LEASH} blocks from where it began, and that place stays its post until it is back near it
 * or a while has passed ({@link #keepsPost}). Pure, so the decisions are tested here.
 */
public final class GuardDuty {
    private GuardDuty() {
    }

    /** How far from the guard (blocks) a monster is noticed. */
    public static final double NOTICE = 16;
    /** How far up or down (blocks) a monster may be: one under the floor or on a roof is not its business. */
    public static final double VERTICAL = 4;
    /** How far from where it took up the fight a guard will chase before it lets the monster go. */
    public static final double LEASH = 24;
    /** Within this many blocks of its post a guard is back at it (and the post is forgotten). */
    public static final double POST_NEAR = 4;
    /** A post is forgotten this many ticks after the last fight, wherever the guard is (30 seconds). */
    public static final int POST_TICKS = 600;
    /** A guard heals {@link #HEAL} every this many ticks while it is not fighting (a full 20 in some 2 minutes). */
    public static final int HEAL_TICKS = 120;
    /** What it heals each time (half-hearts). */
    public static final float HEAL = 1;
    /** Reach of a strike (blocks, centre to centre). */
    public static final double REACH = 2.5;
    /** Ticks between strikes. */
    public static final int STRIKE_TICKS = 20;
    /** Damage of a strike (half-hearts): about a stone sword's. */
    public static final float DAMAGE = 5;
    /** Below this share of its health a guard retreats. */
    public static final double RETREAT_BELOW = 0.35;
    /** A retreated guard fights again once it has this share of its health back. */
    public static final double RETURN_AT = 0.6;

    /** The behaviour's name in {@link BrainBehaviours#ALL} and the config. */
    public static final String BEHAVIOUR = "guard";

    /** Whether a resident is on duty: a guard, in a village with tools in its stores. */
    public static boolean onDuty(Settlement settlement, Resident resident) {
        return resident.adult() && resident.occupation() == Occupation.GUARD && settlement.ledger().get(ResourceType.TOOLS) > 0;
    }

    /** R9.7: what a guard on duty carries: the best weapon the village's tools make. */
    public enum Blade {
        NONE, WOOD, STONE, IRON, DIAMOND
    }

    /**
     * R9.7: the weapon a guard carries: as good as the best tools in the village's stores (diamond, iron, stone), a wooden one if
     * there are tools of some other kind (the simulation's tools are all of these three, so that is only a safety net), none
     * if there are no tools (and then the guard is not on duty anyway).
     */
    public static Blade blade(Settlement settlement) {
        Ledger ledger = settlement.ledger();
        if (ledger.get(Commodity.DIAMOND_TOOLS) > 0) {
            return Blade.DIAMOND;
        }
        if (ledger.get(Commodity.IRON_TOOLS) > 0) {
            return Blade.IRON;
        }
        if (ledger.get(Commodity.STONE_TOOLS) > 0) {
            return Blade.STONE;
        }
        return ledger.get(ResourceType.TOOLS) > 0 ? Blade.WOOD : Blade.NONE;
    }

    /** R9.6: the patrol behaviour's name. */
    public static final String PATROL = "patrol";

    /** The behaviours only a guard on duty has. */
    public static final Set<String> GUARD_ONLY = Set.of(BEHAVIOUR, PATROL);

    /** The behaviours a resident near a player gets: those that run, less the guard's unless it is on duty. */
    public static Set<String> fit(Set<String> running, boolean onDuty) {
        if (onDuty || running.stream().noneMatch(GUARD_ONLY::contains)) {
            return running;
        }
        Set<String> out = new HashSet<>(running);
        out.removeAll(GUARD_ONLY);
        return out;
    }

    /** Whether a guard retreats now, given its health share and whether it is already retreating (it waits for {@link #RETURN_AT}). */
    public static boolean retreats(double health, boolean retreating) {
        return retreating ? health < RETURN_AT : health < RETREAT_BELOW;
    }

    /** A monster as the guard sees it: its entity id, and where it is. */
    public record Foe(int id, double x, double y, double z) {
    }

    /**
     * The monster to go for: the nearest within {@link #NOTICE} of the guard (and within {@link #LEASH} of its post), keeping the
     * current one unless another is much nearer (half the distance), so it does not dither between two. Empty if none.
     */
    public static Optional<Foe> choose(List<Foe> foes, double gx, double gy, double gz, double px, double pz, Foe current) {
        Foe best = null;
        double bestD = Double.MAX_VALUE;
        double currentD = Double.MAX_VALUE;
        for (Foe f : foes) {
            double d = distance(f, gx, gy, gz);
            if (d > NOTICE || Math.abs(f.y() - gy) > VERTICAL || Math.hypot(f.x() - px, f.z() - pz) > LEASH) {
                continue;
            }
            if (current != null && f.id() == current.id()) {
                currentD = d;
            }
            if (d < bestD) {
                bestD = d;
                best = f;
            }
        }
        if (currentD < Double.MAX_VALUE && bestD > currentD / 2) {
            for (Foe f : foes) {
                if (f.id() == current.id()) {
                    return Optional.of(f);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Whether a guard with no foe still holds its post: it is not back near it and the last fight was not long ago. */
    public static boolean keepsPost(double distanceToPost, long ticksSinceFight) {
        return distanceToPost > POST_NEAR && ticksSinceFight < POST_TICKS;
    }

    /** Whether a guard heals now: not fighting, and {@link #HEAL_TICKS} since it last healed or fought. */
    public static boolean heals(boolean fighting, long ticksSinceHealOrFight) {
        return !fighting && ticksSinceHealOrFight >= HEAL_TICKS;
    }

    /** Whether a monster at this distance (blocks) can be struck. */
    public static boolean inReach(double distance) {
        return distance <= REACH;
    }

    static double distance(Foe f, double x, double y, double z) {
        double dx = f.x() - x;
        double dy = f.y() - y;
        double dz = f.z() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
