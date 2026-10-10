package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** R9.2: the last decisions the added behaviours made (one line each), kept for a bug report. A fixed size: the oldest drop out. */
public final class DecisionLog {
    private final int capacity;
    private final Deque<String> lines = new ArrayDeque<>();
    private long total;

    public DecisionLog(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** Adds a decision: {@code "[tick 12000] Mira Oakes: noticed Skye"}. */
    public void add(long gameTick, String villager, String text) {
        if (lines.size() >= capacity) {
            lines.removeFirst();
        }
        lines.addLast("[tick " + gameTick + "] " + villager + ": " + text);
        total++;
    }

    /** The last {@code count} decisions, oldest first. */
    public List<String> last(int count) {
        List<String> all = new ArrayList<>(lines);
        return all.subList(Math.max(0, all.size() - Math.max(0, count)), all.size());
    }

    /** How many decisions there have been in all, including ones that have dropped out. */
    public long total() {
        return total;
    }

    public int size() {
        return lines.size();
    }

    public void clear() {
        lines.clear();
    }
}
