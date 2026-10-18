package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;

/**
 * R4.29: the level a resident has reached in their trade, from the days they have worked at it: 1 at the start, then 2
 * at 10 days, 3 at 30, 4 at 60 and 5 at 100, the game's novice to master. What each level gives is here too, so the
 * simulation and the Paper layer read the same numbers. Builders keep their own levels (R4.21, {@link Construction}).
 */
public final class TradeLevel {
    /** Days of work to reach each level, from level 1. */
    static final int[] DAYS_AT = {0, 10, 30, 60, 100};
    public static final int MASTER = DAYS_AT.length;
    /** The level (4) at which a smith can forge diamond tools. */
    public static final int EXPERT = 4;
    private static final String[] NAMES = {"novice", "apprentice", "journeyman", "expert", "master"};

    private TradeLevel() {
    }

    /** The level (1 to 5) for this many days of work. */
    public static int of(int xp) {
        int level = 1;
        for (int i = 1; i < DAYS_AT.length; i++) {
            if (xp >= DAYS_AT[i]) {
                level = i + 1;
            }
        }
        return level;
    }

    /** e.g. "journeyman", the game's own names for villager levels. */
    public static String name(int level) {
        return NAMES[Math.max(1, Math.min(MASTER, level)) - 1];
    }

    /** What a gatherer or craftsman makes, as a multiple of a novice's: a tenth more for each level above the first. */
    public static double outputFactor(int level) {
        return 1.0 + 0.1 * (Math.max(1, level) - 1);
    }

    /** Grain a farmer of this level bakes into bread a day: the base, one more from journeyman and another from master (R4.57). */
    public static int bakes(int level) {
        int l = Math.max(1, level);
        return SettlementSimulator.BAKE_PER_FARMER + (l >= 3 ? 1 : 0) + (l >= MASTER ? 1 : 0);
    }

    /** Raw ore a smith of this level smelts a day. */
    public static int smelts(int level) {
        return SettlementSimulator.SMELT_PER_SMITH + 2 * (Math.max(1, level) - 1);
    }

    /** Extra batches a merchant of this level sells a day: one for every two levels above the first. */
    public static int extraBatches(int level) {
        return (Math.max(1, level) - 1) / 2;
    }

    /** How much a guard of this level counts for on duty: one, and one more at level 3 and again at level 5. */
    public static int guardWeight(int level) {
        return 1 + (Math.max(1, level) - 1) / 2;
    }

    /** How much faster a villager walks than a novice: three hundredths for each level above the first. */
    public static double speedFactor(int level) {
        return 1.0 + 0.03 * (Math.max(1, level) - 1);
    }

    /** A guard's health: the game's 20, and 4 more for each level above the first. */
    public static double guardHealth(int level) {
        return 20 + 4 * (Math.max(1, level) - 1);
    }

    /** "master farmer". */
    static String title(Resident resident) {
        return name(of(resident.xp())) + " " + resident.occupation().title().toLowerCase(Locale.ROOT);
    }

    /**
     * A day's work done: one more day of experience, and the history notes reaching master. Builders are not counted here
     * (their skill is the buildings they finish) and nor is someone with no trade.
     */
    static void worked(Settlement settlement, Resident resident, long day) {
        Occupation job = resident.occupation();
        if (job == Occupation.UNEMPLOYED || job == Occupation.NITWIT || job == Occupation.BUILDER) {
            return;
        }
        int before = of(resident.xp());
        resident.addXp(1);
        if (before < MASTER && of(resident.xp()) == MASTER) {
            settlement.record(day, HistoryEvent.Kind.MILESTONE, Masterpieces.promotion(resident)); // R4.60
        }
    }

    /** R4.59: a merchant's haggling. Units of goods a merchant of this level gives for an emerald, from {@code base}: 4% fewer for each level above the first. */
    public static int haggled(int base, int level) {
        return Math.max(1, base * (100 - 4 * (Math.max(1, level) - 1)) / 100);
    }

    /** R4.59: the best merchant's level among the village's working merchants (1 if there are none). */
    static int bestMerchant(Settlement settlement) {
        int best = 1;
        for (Resident r : settlement.residents()) {
            if (r.adult() && r.occupation() == Occupation.MERCHANT) {
                best = Math.max(best, r.level());
            }
        }
        return best;
    }

    /** R4.59: the best level among the village's adults whose trade makes this kind of goods (1 if none does). */
    static int bestMakerOf(Settlement settlement, ResourceType type) {
        int best = 1;
        for (Resident r : settlement.residents()) {
            if (r.adult() && r.occupation().produces() == type) {
                best = Math.max(best, r.level());
            }
        }
        return best;
    }

    /** R4.59: what a village pays for goods its own masters make: 4% less for each level of its best maker above the first. */
    static int paid(Settlement settlement, ResourceType type, double emeralds) {
        double share = (100 - 4 * (bestMakerOf(settlement, type) - 1)) / 100.0;
        return (int) Math.ceil(emeralds * share);
    }
}
