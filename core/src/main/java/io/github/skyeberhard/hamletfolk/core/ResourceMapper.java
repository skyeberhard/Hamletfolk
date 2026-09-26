package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Classifies Minecraft material names (e.g. {@code "IRON_INGOT"}) into ledger resources,
 * so players can donate real items to a settlement. Emeralds go to the treasury instead.
 */
public final class ResourceMapper {
    private static final Set<String> FOOD = Set.of(
            "WHEAT", "BREAD", "CARROT", "POTATO", "BAKED_POTATO", "BEETROOT", "APPLE", "MELON_SLICE",
            "PUMPKIN", "SWEET_BERRIES", "GLOW_BERRIES", "COOKIE", "PUMPKIN_PIE", "CAKE", "HAY_BLOCK",
            "BEEF", "PORKCHOP", "CHICKEN", "MUTTON", "RABBIT", "COD", "SALMON", "DRIED_KELP");
    private static final Set<String> STONE = Set.of(
            "COBBLESTONE", "STONE", "DEEPSLATE", "COBBLED_DEEPSLATE", "ANDESITE", "DIORITE", "GRANITE",
            "BRICKS", "STONE_BRICKS", "SANDSTONE", "TUFF");
    private static final Set<String> METAL = Set.of(
            "IRON_INGOT", "COPPER_INGOT", "GOLD_INGOT", "RAW_IRON", "RAW_COPPER", "RAW_GOLD", "IRON_BLOCK");
    private static final Set<String> GOODS = Set.of(
            "WHITE_WOOL", "LEATHER", "PAPER", "BOOK", "STRING", "FEATHER", "GLASS", "CANDLE");

    private ResourceMapper() {
    }

    public static boolean isCurrency(String material) {
        return "EMERALD".equals(normalize(material));
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
