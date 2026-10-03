package io.github.skyeberhard.hamletfolk.core;

/**
 * R3.4: how a settlement regards a player, from -100 to 100, kept per player per settlement. Gifts and
 * filled requests raise it; killing a resident lowers it, much faster than a gift raises it. It starts at 0
 * (strangers) and does not fade. The rules live here so they are tested without a server.
 */
public final class Reputation {
    public static final int MIN = -100;
    public static final int MAX = 100;
    /** What killing one resident costs the killer. */
    public static final int KILLING = 25;
    /** The most one delivery to a request can earn, so a huge payout does not buy instant honour. */
    static final int REQUEST_MAX_GAIN = 10;
    /** The most a price moves for the best (or worst) standing: a tenth. */
    static final double PRICE_SWING = 0.10;

    private Reputation() {
    }

    /** How a settlement's people see a player, from {@link #standing}. */
    public enum Standing {
        HOSTILE, WARY, STRANGER, FRIENDLY, HONOURED
    }

    public static int clamp(int score) {
        return Math.max(MIN, Math.min(MAX, score));
    }

    public static Standing standing(int score) {
        if (score <= -50) {
            return Standing.HOSTILE;
        }
        if (score <= -10) {
            return Standing.WARY;
        }
        if (score >= 60) {
            return Standing.HONOURED;
        }
        if (score >= 15) {
            return Standing.FRIENDLY;
        }
        return Standing.STRANGER;
    }

    /**
     * What a gift of {@code units} of a resource earns: its worth in emeralds, rounded down, so a handful
     * of sticks earns nothing and splitting a gift gains nothing. Emeralds given to the treasury count 1 each.
     */
    public static int donationGain(ResourceType type, int units, boolean emeralds) {
        if (units <= 0) {
            return 0;
        }
        return emeralds ? units : units / SettlementSimulator.unitsPerEmerald(type);
    }

    /** What delivering to a request earns: 1 per emerald it paid, at most {@link #REQUEST_MAX_GAIN}. */
    public static int requestGain(int emeraldsPaid) {
        return Math.max(0, Math.min(REQUEST_MAX_GAIN, emeraldsPaid));
    }

    /**
     * The factor a standing puts on a price the player pays: 0.9 at 100, 1.1 at -100. For something the
     * player is paid for, the amount handed over is divided by it instead, so good standing pays better too.
     */
    public static double priceFactor(int score) {
        return 1.0 + PRICE_SWING * -clamp(score) / MAX;
    }
}
