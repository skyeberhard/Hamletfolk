package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * R3.16: what the stores actually hold, within each {@link ResourceType}. A category's amount is the sum of its
 * commodities, counted in the category's units (a log is four units of wood, as a donated log always was). Within a
 * category the commodities are declared in the order they are taken when something takes from the category as a whole
 * (eating, spoilage, the storage limit, a merchant's sale, a building's cost): the most perishable, or the least use to
 * the village, first. People eat produce, fish and meat before grain and bread (bread is kept for births), the cheapest
 * tool wears out first, and iron is the last metal to go. One commodity per category is its plain one, where anything added to the category as a whole goes.
 */
public enum Commodity {
    PRODUCE(ResourceType.FOOD, "produce", true),
    FISH(ResourceType.FOOD, "fish", false),
    MEAT(ResourceType.FOOD, "meat", false),
    COOKED(ResourceType.FOOD, "cooked food", false),
    GRAIN(ResourceType.FOOD, "grain", false),
    BREAD(ResourceType.FOOD, "bread", false),

    PLANKS(ResourceType.WOOD, "planks", true),
    LOGS(ResourceType.WOOD, "logs", false),

    COBBLESTONE(ResourceType.STONE, "cobblestone", true),
    GRAVEL(ResourceType.STONE, "gravel", false),
    SAND(ResourceType.STONE, "sand", false),
    CLAY(ResourceType.STONE, "clay", false),
    STONE_BLOCKS(ResourceType.STONE, "stone blocks", false),
    BRICKS(ResourceType.STONE, "bricks", false),

    // Metal: what the village has no use for yet goes first, so the iron its smiths need is the last thrown away at the
    // storage limit (or sold, or charged for a building). Diamonds are rare enough to sit after it.
    RAW_COPPER(ResourceType.METAL, "raw copper", false),
    COPPER(ResourceType.METAL, "copper", false),
    RAW_GOLD(ResourceType.METAL, "raw gold", false),
    GOLD(ResourceType.METAL, "gold", false),
    REDSTONE(ResourceType.METAL, "redstone", false),
    LAPIS(ResourceType.METAL, "lapis", false),
    RAW_IRON(ResourceType.METAL, "raw iron", false),
    IRON(ResourceType.METAL, "iron", true),
    DIAMOND(ResourceType.METAL, "diamonds", false),

    CHARCOAL(ResourceType.FUEL, "charcoal", false),
    COAL(ResourceType.FUEL, "coal", true),

    STONE_TOOLS(ResourceType.TOOLS, "stone tools", false),
    IRON_TOOLS(ResourceType.TOOLS, "iron tools", true),
    DIAMOND_TOOLS(ResourceType.TOOLS, "diamond tools", false),

    WARES(ResourceType.GOODS, "wares", true),
    WOOL(ResourceType.GOODS, "wool", false),
    STRING(ResourceType.GOODS, "string", false),
    LEATHER(ResourceType.GOODS, "leather", false),
    PAPER(ResourceType.GOODS, "paper", false),
    GLASS(ResourceType.GOODS, "glass", false),
    BOOKS(ResourceType.GOODS, "books", false);

    private final ResourceType category;
    private final String label;
    private final boolean plain;

    Commodity(ResourceType category, String label, boolean plain) {
        this.category = category;
        this.label = label;
        this.plain = plain;
    }

    public ResourceType category() {
        return category;
    }

    /** e.g. "raw iron". */
    public String label() {
        return label;
    }

    private static final Map<ResourceType, Commodity> PLAIN = new EnumMap<>(ResourceType.class);
    private static final Map<ResourceType, List<Commodity>> BY_CATEGORY = new EnumMap<>(ResourceType.class);

    static {
        for (Commodity c : values()) {
            BY_CATEGORY.computeIfAbsent(c.category, k -> new ArrayList<>()).add(c);
            if (c.plain) {
                PLAIN.put(c.category, c);
            }
        }
        for (ResourceType type : ResourceType.values()) {
            if (!PLAIN.containsKey(type)) {
                throw new IllegalStateException("no plain commodity for " + type);
            }
        }
    }

    /** Where anything added to the category as a whole goes. */
    public static Commodity plainOf(ResourceType category) {
        return PLAIN.get(category);
    }

    /** A category's commodities, in the order they are taken. */
    public static List<Commodity> of(ResourceType category) {
        return List.copyOf(BY_CATEGORY.get(category));
    }

    /** The commodity named in a save ("RAW_IRON"), or the plain one of a category named in an older save ("METAL"). */
    static Commodity fromSave(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        for (Commodity c : values()) {
            if (c.name().equals(upper)) {
                return c;
            }
        }
        return plainOf(ResourceType.valueOf(upper));
    }
}
