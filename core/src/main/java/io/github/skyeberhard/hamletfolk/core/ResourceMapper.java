package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Classifies Minecraft material names (e.g. {@code "IRON_INGOT"}) into ledger resources,
 * so players can donate real items to a settlement, and says what each item is worth
 * (R3.8). Emeralds go to the treasury instead.
 */
public final class ResourceMapper {
    private static final Set<String> FOOD = Set.of(
            "WHEAT", "BREAD", "CARROT", "POTATO", "BAKED_POTATO", "BEETROOT", "APPLE", "MELON_SLICE",
            "PUMPKIN", "SWEET_BERRIES", "GLOW_BERRIES", "COOKIE", "PUMPKIN_PIE", "CAKE", "HAY_BLOCK",
            "BEEF", "PORKCHOP", "CHICKEN", "MUTTON", "RABBIT", "COD", "SALMON", "DRIED_KELP", "MELON",
            "DRIED_KELP_BLOCK");
    private static final Set<String> STONE = Set.of(
            "COBBLESTONE", "STONE", "DEEPSLATE", "COBBLED_DEEPSLATE", "ANDESITE", "DIORITE", "GRANITE",
            "BRICKS", "STONE_BRICKS", "SANDSTONE", "TUFF");
    private static final Set<String> METAL = Set.of(
            "IRON_INGOT", "COPPER_INGOT", "GOLD_INGOT", "RAW_IRON", "RAW_COPPER", "RAW_GOLD", "IRON_BLOCK",
            "GOLD_BLOCK", "COPPER_BLOCK", "RAW_IRON_BLOCK", "RAW_GOLD_BLOCK", "RAW_COPPER_BLOCK");
    private static final Set<String> GOODS = Set.of(
            "WHITE_WOOL", "LEATHER", "PAPER", "BOOK", "STRING", "FEATHER", "GLASS", "CANDLE");

    /** Storage blocks hold nine of their item, so they're worth nine. Anything else counts as one. */
    private static final Map<String, Integer> STORAGE_BLOCKS = Map.ofEntries(
            Map.entry("IRON_BLOCK", 9), Map.entry("GOLD_BLOCK", 9), Map.entry("COPPER_BLOCK", 9),
            Map.entry("RAW_IRON_BLOCK", 9), Map.entry("RAW_GOLD_BLOCK", 9), Map.entry("RAW_COPPER_BLOCK", 9),
            Map.entry("HAY_BLOCK", 9), Map.entry("MELON", 9), Map.entry("DRIED_KELP_BLOCK", 9));

    /**
     * How many tool-lifetimes of wear a tool of each material is worth: a rough guide to
     * durability, capped so a diamond tool isn't worth a hundred wooden ones.
     */
    private static final Map<String, Integer> TOOL_TIERS = Map.of(
            "WOODEN", 1, "GOLDEN", 1, "STONE", 2, "IRON", 3, "DIAMOND", 5, "NETHERITE", 6);

    private ResourceMapper() {
    }

    /** What a donated item is worth to a settlement: which resource, and how many units each. */
    public record Value(ResourceType type, int unitsPerItem) {
    }

    /** True for items that go into the treasury as emeralds. */
    public static boolean isCurrency(String material) {
        return currencyValue(material) > 0;
    }

    /** Emeralds an item is worth in the treasury: 1 for an emerald, 9 for a block, 0 otherwise. */
    public static int currencyValue(String material) {
        String name = normalize(material);
        if ("EMERALD".equals(name)) {
            return 1;
        }
        return "EMERALD_BLOCK".equals(name) ? 9 : 0;
    }

    /** R3.8: the ledger resource and per-item value of a donated item, if the village can use it. */
    public static Optional<Value> value(String material) {
        String name = normalize(material);
        Optional<ResourceType> type = classify(name);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        if (type.get() == ResourceType.TOOLS) {
            return Optional.of(new Value(ResourceType.TOOLS, toolTier(name)));
        }
        return Optional.of(new Value(type.get(), STORAGE_BLOCKS.getOrDefault(name, 1)));
    }

    /**
     * Parses the amount argument of {@code /settlement donate}: {@code all}, or a whole number
     * from 1 up to how many the player holds. Empty if it's anything else.
     */
    public static OptionalInt parseQuantity(String argument, int held) {
        if (argument == null || held < 1) {
            return OptionalInt.empty();
        }
        if (argument.equalsIgnoreCase("all")) {
            return OptionalInt.of(held);
        }
        try {
            int amount = Integer.parseInt(argument.trim());
            return amount >= 1 && amount <= held ? OptionalInt.of(amount) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private static int toolTier(String name) {
        int underscore = name.indexOf('_');
        return underscore < 0 ? 1 : TOOL_TIERS.getOrDefault(name.substring(0, underscore), 1);
    }

    public static Optional<ResourceType> classify(String material) {
        String name = normalize(material);
        if (FOOD.contains(name) || name.startsWith("COOKED_")) {
            return Optional.of(ResourceType.FOOD);
        }
        if (name.endsWith("_LOG") || name.endsWith("_PLANKS") || name.endsWith("_WOOD") || name.endsWith("_STEM")
                || name.equals("STICK") || name.equals("BAMBOO")) {
            return Optional.of(ResourceType.WOOD);
        }
        if (STONE.contains(name)) {
            return Optional.of(ResourceType.STONE);
        }
        if (METAL.contains(name)) {
            return Optional.of(ResourceType.METAL);
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE") || name.endsWith("_SWORD")) {
            return Optional.of(ResourceType.TOOLS);
        }
        if (GOODS.contains(name) || name.endsWith("_WOOL")) {
            return Optional.of(ResourceType.GOODS);
        }
        return Optional.empty();
    }

    private static String normalize(String material) {
        String name = material.toUpperCase(Locale.ROOT);
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }
}
