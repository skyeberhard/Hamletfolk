package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;

/**
 * R9.2: what the brain module's added behaviours cost. Every call is recorded with the game tick it ran in and the time it took,
 * so the figures are per call and per tick (all the calls of one tick added up, which is what a server owner feels). Pure:
 * the module supplies the tick and the nanoseconds. Main thread only.
 */
public final class BrainStats {
    /** What has been recorded so far; the tick still open is counted in. */
    public record Summary(long calls, long ticks, double averageCallMicros, double worstCallMicros, double averageTickMicros,
            double worstTickMicros, double totalMillis) {
        /** e.g. "34 calls in 12 ticks: 3.1 microseconds a call (worst 88.0), 9.0 a tick (worst 41.0), 0.1 ms in all." */
        public String describe() {
            if (calls == 0) {
                return "The added behaviours have not run yet.";
            }
            return String.format(Locale.ROOT, "%d calls in %d ticks: %.1f microseconds a call (worst %.1f), %.1f a tick (worst %.1f), %.2f ms in all.",
                    calls, ticks, averageCallMicros, worstCallMicros, averageTickMicros, worstTickMicros, totalMillis);
        }
    }

    private long calls;
    private long nanos;
    private long worstCall;
    private long ticks;
    private long worstTick;
    private long openTick = Long.MIN_VALUE;
    private long openNanos;

    /** Records one call of an added behaviour, in game tick {@code tick}, that took {@code spent} nanoseconds. */
    public void record(long tick, long spent) {
        long took = Math.max(0, spent);
        if (tick != openTick) {
            closeTick();
            openTick = tick;
        }
        openNanos += took;
        calls++;
        nanos += took;
        worstCall = Math.max(worstCall, took);
    }

    private void closeTick() {
        if (openTick != Long.MIN_VALUE) {
            ticks++;
            worstTick = Math.max(worstTick, openNanos);
        }
        openNanos = 0;
    }

    /** The figures so far. */
    public Summary summary() {
        long tickCount = ticks + (openTick == Long.MIN_VALUE ? 0 : 1);
        long worstOfTicks = Math.max(worstTick, openNanos);
        return new Summary(calls, tickCount, calls == 0 ? 0 : nanos / 1000.0 / calls, worstCall / 1000.0,
                tickCount == 0 ? 0 : nanos / 1000.0 / tickCount, worstOfTicks / 1000.0, nanos / 1_000_000.0);
    }

    /** Starts the figures again from nothing. */
    public void reset() {
        calls = 0;
        nanos = 0;
        worstCall = 0;
        ticks = 0;
        worstTick = 0;
        openTick = Long.MIN_VALUE;
        openNanos = 0;
    }
}
