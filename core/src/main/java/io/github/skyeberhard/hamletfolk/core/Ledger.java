package io.github.skyeberhard.hamletfolk.core;

import java.util.EnumMap;
import java.util.Map;

/** A settlement's shared stockpile and treasury. */
public final class Ledger {
    private final EnumMap<ResourceType, Integer> stock = new EnumMap<>(ResourceType.class);
    private int treasury;

    public int get(ResourceType type) {
        return stock.getOrDefault(type, 0);
    }

    public void add(ResourceType type, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        stock.merge(type, amount, Integer::sum);
    }

    /** Removes up to {@code amount} and returns how much was actually taken. */
    public int take(ResourceType type, int amount) {
        int taken = Math.min(get(type), Math.max(0, amount));
        stock.put(type, get(type) - taken);
        return taken;
    }

    public int treasury() {
        return treasury;
    }

    public void addTreasury(int emeralds) {
        treasury += emeralds;
    }

    Map<ResourceType, Integer> stock() {
        return stock;
    }

    void setTreasury(int treasury) {
        this.treasury = treasury;
    }
}
