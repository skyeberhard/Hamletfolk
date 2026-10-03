package io.github.skyeberhard.hamletfolk.core;

/**
 * R3.5: what a resident has put by. A working adult earns the worth of what they produce, at the same
 * rates the merchants and requests use (R3.9), and pays for their own meals out of it. Wealth is kept in
 * hundredths of an emerald so a day's wage of a few units is not rounded away. It only shows in dialogue
 * and in {@code /settlement residents}: the treasury and the stores are untouched, so it can neither
 * mint nor drain what the village holds.
 */
public final class Wealth {
    /** 100 makes one emerald. */
    public static final int PER_EMERALD = 100;
    /** The most one resident can put by, so a long-lived farmer cannot overflow. */
    public static final int MAX = 1000 * PER_EMERALD;
    /** Below a day's meal a resident is broke; then modest to 5 emeralds, comfortable to 20, and wealthy above. */
    static final int COMFORTABLE_FROM = 5 * PER_EMERALD;
    static final int WEALTHY_FROM = 20 * PER_EMERALD;

    private Wealth() {
    }

    public enum Tier {
        BROKE, MODEST, COMFORTABLE, WEALTHY
    }

    /** What {@code units} of a resource are worth to the person who made them, in hundredths of an emerald. */
    public static int worth(ResourceType type, int units) {
        return (int) ((long) Math.max(0, units) * PER_EMERALD / SettlementSimulator.unitsPerEmerald(type));
    }

    /** What an adult's meal costs when they are given {@code fedFraction} of a full ration (1.0 is a full day). */
    public static int mealCost(double fedFraction) {
        double fed = Math.max(0.0, Math.min(1.0, fedFraction));
        return (int) Math.round(SettlementSimulator.ADULT_FOOD_PER_DAY * fed * PER_EMERALD
                / SettlementSimulator.unitsPerEmerald(ResourceType.FOOD));
    }

    public static Tier tier(int wealth) {
        if (wealth < mealCost(1.0)) {
            return Tier.BROKE;
        }
        if (wealth >= WEALTHY_FROM) {
            return Tier.WEALTHY;
        }
        return wealth >= COMFORTABLE_FROM ? Tier.COMFORTABLE : Tier.MODEST;
    }
}
