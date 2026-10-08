package io.github.skyeberhard.hamletfolk.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * A settlement's shared stockpile and treasury. Since R3.16 the stockpile holds {@link Commodity commodities}; a
 * category's amount is the sum of its commodities, adding to a category adds its plain commodity, and taking from a
 * category takes its commodities in their order.
 */
public final class Ledger {
    private final EnumMap<Commodity, Integer> stock = new EnumMap<>(Commodity.class);
    private int treasury;

    /** How much of a category the stores hold: the sum of its commodities. */
    public int get(ResourceType type) {
        int total = 0;
        for (Commodity c : Commodity.of(type)) {
            total += get(c);
        }
        return total;
    }

    public int get(Commodity commodity) {
        return stock.getOrDefault(commodity, 0);
    }

    /** Adds to a category's plain commodity. */
    public void add(ResourceType type, int amount) {
        add(Commodity.plainOf(type), amount);
    }

    public void add(Commodity commodity, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (amount > 0) {
            stock.merge(commodity, amount, Integer::sum);
        }
    }

    /** Removes up to {@code amount} of a category, its commodities in order, and returns how much was actually taken. */
    public int take(ResourceType type, int amount) {
        int left = Math.max(0, amount);
        int taken = 0;
        for (Commodity c : Commodity.of(type)) {
            if (left == 0) {
                break;
            }
            int t = take(c, left);
            taken += t;
            left -= t;
        }
        return taken;
    }

    /** Removes up to {@code amount} of one commodity and returns how much was actually taken. */
    public int take(Commodity commodity, int amount) {
        int have = get(commodity);
        int taken = Math.min(have, Math.max(0, amount));
        if (have - taken == 0) {
            stock.remove(commodity);
        } else {
            stock.put(commodity, have - taken);
        }
        return taken;
    }

    /**
     * Takes up to {@code amount}, as much as it can of {@code preferred} first and the rest from its category in order.
     * Returns how much was taken in all.
     */
    public int takePreferring(Commodity preferred, int amount) {
        int taken = take(preferred, amount);
        return taken + take(preferred.category(), amount - taken);
    }

    public int treasury() {
        return treasury;
    }

    public void addTreasury(int emeralds) {
        treasury += emeralds;
    }

    /** Takes {@code emeralds} out of the treasury if it holds that many; returns whether it did. */
    boolean spendTreasury(int emeralds) {
        if (emeralds < 0 || emeralds > treasury) {
            return false;
        }
        treasury -= emeralds;
        return true;
    }

    /** The commodities held, in their declared order. */
    public Map<Commodity, Integer> stock() {
        return java.util.Collections.unmodifiableMap(stock);
    }

    void setTreasury(int treasury) {
        this.treasury = treasury;
    }
}
