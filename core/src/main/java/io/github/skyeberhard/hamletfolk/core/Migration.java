package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * R4.2: unemployed or unhappy residents leave for a better-off settlement nearby. At most one resident
 * leaves a settlement per day, and each candidate has only a chance to go on a given day, so a bad day
 * does not empty a village and a lasting bad time does. The rule is here; the Minecraft layer only moves
 * the villager afterwards. Whether to let people leave at all is the caller's choice (it calls
 * {@link #run}).
 */
public final class Migration {
    /** How far a resident will go, in blocks between settlement centers. */
    public static final int MAX_DISTANCE = 600;
    /** The chance a candidate sets out on a day when they have reason to. */
    static final double CHANCE = 0.2;
    /** Below this mood a resident is unhappy enough to go (the "troubled" band, see the villager's mood word). */
    static final int UNHAPPY_MOOD = 35;
    /** A village is never left with fewer than this many people by someone leaving. */
    static final int MIN_LEFT_BEHIND = 2;
    /** How much better off (see {@link #score}) the destination must be. */
    static final int BETTER_BY = 5;
    /** Key of the settlement condition holding the last day this settlement was checked (once a day only). */
    static final String CHECKED = "migrationChecked";
    /** Prefix of the condition that marks a resident as moved on paper but not yet in person (R4.2). */
    public static final String MOVING = "moving:";
    /** Prefix of the condition holding the day a moved resident's villager arrived (kept for {@link #ARRIVAL_GRACE_DAYS}). */
    public static final String ARRIVED = "arrived:";
    /** For this many days after arriving, R1.8 does not count a migrant as straying back to where they came from. */
    public static final int ARRIVAL_GRACE_DAYS = 7;

    private Migration() {
    }

    /** Who moved, from where, to where. */
    public record Move(Resident resident, Settlement from, Settlement to) {
    }

    /**
     * How well off a settlement is: food in store per head (up to 50) less a fifth of its threat. Only
     * the comparison between two settlements matters.
     */
    static double score(Settlement settlement) {
        if (settlement.population() == 0) {
            return 0;
        }
        double food = Math.min(50.0, (double) settlement.ledger().get(ResourceType.FOOD) / settlement.population());
        return food - settlement.threat() / 5.0;
    }

    /** True if this resident has reason to leave: a grown adult, not an elder, with no work or low spirits. */
    static boolean wantsToLeave(Resident resident, long day) {
        if (!resident.adult() || resident.stage(day) == LifeStage.ELDER) {
            return false;
        }
        return resident.occupation() == Occupation.UNEMPLOYED || resident.needs().mood() < UNHAPPY_MOOD;
    }

    /**
     * Checks a settlement once for the given day and moves at most one resident to the nearest
     * better-off settlement that has a free bed and is not in famine. Returns the move, if any.
     * Both settlements' histories record it. The mover loses their job (they have none where they
     * are going) and is marked as moved, for the Minecraft layer to carry out.
     */
    public static Optional<Move> run(SettlementRegistry registry, Settlement from, long day) {
        Long checked = from.conditions().get(CHECKED);
        if (checked != null && checked >= day) {
            return Optional.empty();
        }
        from.conditions().put(CHECKED, day);
        if (from.isAbandoned() || from.population() <= MIN_LEFT_BEHIND) {
            return Optional.empty();
        }
        Optional<Settlement> destination = destination(registry, from);
        if (destination.isEmpty()) {
            return Optional.empty();
        }
        Random random = new Random(from.id().getMostSignificantBits() ^ (day * 0x9E3779B97F4A7C15L) ^ 0x4D16A7EL);
        List<Resident> candidates = new ArrayList<>(from.residents());
        candidates.sort(Comparator.comparing(Resident::id));
        for (Resident resident : candidates) {
            if (wantsToLeave(resident, day) && random.nextDouble() < CHANCE) {
                registry.migrate(resident, from, destination.get(), day);
                return Optional.of(new Move(resident, from, destination.get()));
            }
        }
        return Optional.empty();
    }

    private static Optional<Settlement> destination(SettlementRegistry registry, Settlement from) {
        long limit = (long) MAX_DISTANCE * MAX_DISTANCE;
        double bar = score(from) + BETTER_BY;
        Settlement best = null;
        for (Settlement candidate : registry.settlements()) {
            if (candidate == from || !candidate.world().equals(from.world()) || candidate.isAbandoned()
                    || candidate.hasCondition("famine") || candidate.freeBeds() <= 0 || score(candidate) < bar) {
                continue;
            }
            long distance = candidate.distanceSquared(from.centerX(), from.centerZ());
            if (distance > limit) {
                continue;
            }
            if (best == null || distance < best.distanceSquared(from.centerX(), from.centerZ())) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }
}
