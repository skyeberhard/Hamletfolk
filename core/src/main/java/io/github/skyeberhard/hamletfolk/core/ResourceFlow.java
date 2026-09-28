package io.github.skyeberhard.hamletfolk.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * R3.7: what a settlement produced and consumed of each resource over the last
 * {@code WINDOW_DAYS} days, so "why is food dropping?" has an answer. Only work and eating
 * count; donations and other outside changes to the ledger are not production.
 */
public final class ResourceFlow {
    public static final int WINDOW_DAYS = 7;

    /** Per resource, per day: {produced, consumed}. */
    private final EnumMap<ResourceType, TreeMap<Long, int[]>> days = new EnumMap<>(ResourceType.class);

    public void recordProduced(ResourceType type, long day, int amount) {
        record(type, day, 0, amount);
    }

    public void recordConsumed(ResourceType type, long day, int amount) {
        record(type, day, 1, amount);
    }

    private void record(ResourceType type, long day, int slot, int amount) {
        if (amount <= 0) {
            return;
        }
        TreeMap<Long, int[]> byDay = days.computeIfAbsent(type, t -> new TreeMap<>());
        byDay.computeIfAbsent(day, d -> new int[2])[slot] += amount;
        byDay.headMap(day - WINDOW_DAYS + 1).clear();
    }

    /** Total produced in the {@code WINDOW_DAYS} days ending on {@code today}. */
    public int produced(ResourceType type, long today) {
        return total(type, today, 0);
    }

    /** Total consumed in the {@code WINDOW_DAYS} days ending on {@code today}. */
    public int consumed(ResourceType type, long today) {
        return total(type, today, 1);
    }

    private int total(ResourceType type, long today, int slot) {
        TreeMap<Long, int[]> byDay = days.get(type);
        if (byDay == null) {
            return 0;
        }
        int sum = 0;
        for (int[] row : byDay.subMap(today - WINDOW_DAYS + 1, true, today, true).values()) {
            sum += row[slot];
        }
        return sum;
    }

    Map<ResourceType, TreeMap<Long, int[]>> days() {
        return days;
    }
}
