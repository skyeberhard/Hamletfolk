package io.github.skyeberhard.hamletfolk.core;

/**
 * R9.4: one behaviour's cost: its timing figures ({@link BrainStats}) and its budget ({@link BudgetGuard}) together. The module
 * records every call; the meter says, on the call that closes a tick, whether that tipped the behaviour over its budget.
 * Main thread only.
 */
public final class BehaviourMeter {
    /**
     * How many measured ticks after it starts (or is switched on again) the budget is not judged: the first calls run before the
     * server has compiled them (0.7 ms for a tick of 8 villagers was seen) and would trip any budget.
     */
    public static final int WARM_UP = 100;

    private final BrainStats stats = new BrainStats();
    private final BudgetGuard guard = new BudgetGuard();
    private long budgetNanos;
    private int warming = WARM_UP;

    public BehaviourMeter(int budgetMicros) {
        setBudgetMicros(budgetMicros);
    }

    /** Records one call. Returns true only on the call that trips the budget (the behaviour should then stand aside). */
    public boolean record(long tick, long nanos) {
        long closed = stats.record(tick, nanos);
        if (closed < 0) {
            return false;
        }
        if (warming > 0) {
            warming--;
            return false;
        }
        return guard.closeTick(closed, budgetNanos);
    }

    public BrainStats stats() {
        return stats;
    }

    public boolean tripped() {
        return guard.tripped();
    }

    /** What tripped it, for the log: e.g. "10 of the last 100 ticks over, worst tick 812.0 microseconds". */
    public String figures() {
        return String.format(java.util.Locale.ROOT, "%d of the last %d ticks over, worst tick %.1f microseconds", guard.overCount(),
                BudgetGuard.WINDOW, stats.summary().worstTickMicros());
    }

    public int budgetMicros() {
        return (int) (budgetNanos / 1000);
    }

    /** The most all its calls in one tick may take, in microseconds; 0 or less for no budget. */
    public void setBudgetMicros(int micros) {
        budgetNanos = Math.max(0, micros) * 1000L;
    }

    /** Starts afresh: figures and budget window (when it is switched on again). */
    public void reset() {
        stats.reset();
        guard.reset();
        warming = WARM_UP;
    }
}
