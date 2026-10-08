package io.github.skyeberhard.hamletfolk.core;

/**
 * R4.27: the arithmetic of running a village faster than its world. At speed {@code s} a village gains {@code s - 1} days
 * on top of every day the world itself passes (the world's own day is simulated as usual), so each game tick owes it
 * {@code (s - 1) / 24000} of a day. The fraction left over is carried to the next pass, and a pass never runs more than
 * a set number of days, so a slow server falls behind rather than freezing; the backlog it can carry is capped too.
 */
public final class FastForward {
    /** Game ticks in a Minecraft day. */
    public static final int TICKS_PER_DAY = 24_000;
    public static final int MIN_SPEED = 2;
    public static final int MAX_SPEED = 100;

    private FastForward() {
    }

    /** Whole days to run this pass and the fraction (or capped backlog) carried to the next. */
    public record Step(int days, double carry) {
    }

    /**
     * The days owed after {@code ticks} game ticks at {@code speed}, given what was carried from the last pass. At most
     * {@code maxDays} are run; anything beyond that is carried, up to {@code maxDays} more.
     */
    public static Step advance(double carried, int speed, long ticks, int maxDays) {
        int s = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
        double owed = Math.max(0, carried) + (double) (s - 1) * Math.max(0, ticks) / TICKS_PER_DAY;
        int days = (int) Math.min(maxDays, Math.floor(owed));
        double left = Math.min(owed - days, maxDays);
        return new Step(days, left);
    }
}
