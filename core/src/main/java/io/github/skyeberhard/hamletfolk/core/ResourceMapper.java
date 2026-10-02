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

    /**
     * What a donated item is worth to a settlement: which resource, and how many units each, as a
     * fraction ({@code numerator / denominator}) so a stick can be worth half a plank. A whole stack
     * is valued at once and rounded down, so splitting a donation into single items gains nothing.
     */
    public record Value(ResourceType type, int numerator, int denominator) {
        public Value {
            if (denominator < 1) {
                throw new IllegalArgumentException("denominator must be positive");
            }
        }

        public Value(ResourceType type, int unitsPerItem) {
            this(type, unitsPerItem, 1);
        }

        /** Whole units a stack of {@code count} is worth, rounded down. */
        public int unitsFor(int count) {
            return (int) ((long) Math.max(0, count) * numerator / denominator);
        }

        /** The fewest items worth at least {@code units} (rounding up): what a donation needs to give to earn them. */
        public int itemsFor(int units) {
            if (units <= 0 || numerator <= 0) {
                return 0;
            }
            return (int) Math.min(Integer.MAX_VALUE, ((long) units * denominator + numerator - 1) / numerator);
        }

        /** The most items worth no more than {@code units} (rounding down): what fits in that much room. */
        public int itemsThatFit(int units) {
            if (units <= 0 || numerator <= 0) {
                return 0;
            }
            return (int) Math.min(Integer.MAX_VALUE, (long) units * denominator / numerator);
        }

        /** Whole units one item is worth: 0 for something worth less than one unit (a stick, a worn tool). */
        public int unitsPerItem() {
            return numerator / denominator;
        }
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
        return value(material, 1.0);
    }

    /**
     * R3.12: as {@link #value(String)}, for an item in the given {@code condition} (1.0 is new, 0.0 is
     * about to break). Only tools lose value with wear. Wood is worth what it is made of: a log is
     * four planks and a plank is two sticks, so the same timber is worth the same in any form.
     */
    public static Optional<Value> value(String material, double condition) {
        String name = normalize(material);
        Optional<ResourceType> type = classify(name);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        if (type.get() == ResourceType.TOOLS) {
            double kept = Math.max(0.0, Math.min(1.0, condition));
            return Optional.of(new Value(ResourceType.TOOLS, (int) Math.floor(toolTier(name) * kept + 1e-9)));
        }
        if (type.get() == ResourceType.WOOD) {
            return Optional.of(woodValue(name));
        }
        return Optional.of(new Value(type.get(), STORAGE_BLOCKS.getOrDefault(name, 1)));
    }

    /** Wood in planks: a log, wood block or stem is 4, a plank 1, a stick half, a bamboo stalk a quarter. */
    private static Value woodValue(String name) {
        if (name.endsWith("_PLANKS")) {
            return new Value(ResourceType.WOOD, 1, 1);
        }
        if (name.equals("STICK")) {
            return new Value(ResourceType.WOOD, 1, 2);
        }
        if (name.equals("BAMBOO")) {
            return new Value(ResourceType.WOOD, 1, 4);
        }
        return new Value(ResourceType.WOOD, 4, 1); // _LOG, _WOOD, _STEM
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

    /**
     * R3.13: the raw materials in a crafted tool, as whole-recipe totals: the head (3 for a pickaxe or
     * axe, 2 for a hoe or sword, 1 for a shovel) of the tier's material, and the sticks (2, or 1 for a
     * sword). Diamond and netherite heads are not raw materials a village accepts, so they count as
     * nothing. Empty for anything that is not a tool.
     */
    public static java.util.List<Value> toolMaterials(String material) {
        String name = normalize(material);
        if (classify(name).orElse(null) != ResourceType.TOOLS) {
            return java.util.List.of();
        }
        String kind = name.substring(name.indexOf('_') + 1);
        int head = switch (kind) {
            case "PICKAXE", "AXE" -> 3;
            case "HOE", "SWORD" -> 2;
            default -> 1; // shovel
        };
        int sticks = "SWORD".equals(kind) ? 1 : 2;
        ResourceType headType = switch (name.substring(0, Math.max(0, name.indexOf('_')))) {
            case "WOODEN" -> ResourceType.WOOD;
            case "STONE" -> ResourceType.STONE;
            case "IRON", "GOLDEN", "COPPER" -> ResourceType.METAL;
            default -> null;
        };
        java.util.List<Value> parts = new java.util.ArrayList<>();
        if (headType != null) {
            parts.add(new Value(headType, head, 1));
        }
        parts.add(new Value(ResourceType.WOOD, sticks, 2)); // a stick is half a plank
        return parts;
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
        // (a huge mushroom's stem is a silk-touch block that crafts into nothing, so it is not timber)
        if (name.endsWith("_LOG") || name.endsWith("_PLANKS") || name.endsWith("_WOOD")
                || (name.endsWith("_STEM") && !name.equals("MUSHROOM_STEM"))
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
