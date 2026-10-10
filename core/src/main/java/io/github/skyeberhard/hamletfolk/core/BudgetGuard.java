package io.github.skyeberhard.hamletfolk.core;

/**
 * R9.4: decides when a behaviour has cost too much. It is told the total time each tick took (all its calls on all villagers
 * added up) and trips once {@link #OVER} of the last {@link #WINDOW} ticks it was told about were over the budget: one slow
 * tick (a garbage collection, the disk) is not enough, a behaviour that is too slow most of the time is. Once tripped it
 * stays tripped until {@link #reset()}. Pure; main thread only.
 */
public final class BudgetGuard {
    /** How many of the latest measured ticks it looks at, and how many of them over the budget trip it. */
    public static final int WINDOW = 100;
    public static final int OVER = 10;

    private final boolean[] over = new boolean[WINDOW];
    private int next;
    private int seen;
    private int overCount;
    private boolean tripped;

    /** One tick's total, against a budget. Returns true only on the tick that trips it. A budget of 0 or less means none. */
    public boolean closeTick(long nanos, long budgetNanos) {
        if (tripped || budgetNanos <= 0) {
            return false;
        }
        boolean isOver = nanos > budgetNanos;
        if (seen == WINDOW && over[next]) {
            overCount--; // the oldest tick leaves the window
        }
        over[next] = isOver;
        overCount += isOver ? 1 : 0;
        next = (next + 1) % WINDOW;
        seen = Math.min(WINDOW, seen + 1);
        if (overCount >= OVER) {
            tripped = true;
            return true;
        }
        return false;
    }

    public boolean tripped() {
        return tripped;
    }

    /** How many of the ticks in the window were over the budget. */
    public int overCount() {
        return overCount;
    }

    /** Forgets everything, so the behaviour can be tried again. */
    public void reset() {
        java.util.Arrays.fill(over, false);
        next = 0;
        seen = 0;
        overCount = 0;
        tripped = false;
    }
}
