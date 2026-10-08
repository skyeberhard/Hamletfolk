package io.github.skyeberhard.hamletfolk.core;

/**
 * The categories of a settlement's ledger. Since R3.16 each holds {@link Commodity commodities}, and a category's amount
 * is their sum; FUEL (coal and charcoal) is the category R3.16 added.
 */
public enum ResourceType {
    FOOD,
    WOOD,
    STONE,
    METAL,
    FUEL,
    TOOLS,
    GOODS
}
