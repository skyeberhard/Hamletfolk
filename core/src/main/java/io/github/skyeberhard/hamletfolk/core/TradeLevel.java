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
            settlement.record(day, HistoryEvent.Kind.MILESTONE, resident.fullName() + " is now a " + title(resident) + ".");
        }
    }
}
