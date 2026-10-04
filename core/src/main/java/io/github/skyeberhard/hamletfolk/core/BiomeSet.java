package io.github.skyeberhard.hamletfolk.core;

import java.util.List;
import java.util.Locale;

/**
 * R4.6: the five village styles the game ships (plains, desert, savanna, snowy, taiga) and how a plains-palette
 * building is rebuilt in each one's materials. Authoring once and substituting is the whole point: oak becomes
 * acacia, spruce or sandstone, and the stone in a desert is sandstone.
 */
public final class BiomeSet {
    public static final String PLAINS = "plains";
    public static final String DESERT = "desert";
    public static final String SAVANNA = "savanna";
    public static final String SNOWY = "snowy";
    public static final String TAIGA = "taiga";

    /** Every style, plains first. */
    public static final List<String> ALL = List.of(PLAINS, DESERT, SAVANNA, SNOWY, TAIGA);

    private BiomeSet() {
    }

    /** A known style, or plains for anything else (null, a modded biome, a typo): "plains if unknown" (R4.6). */
    public static String normalize(String biomeSet) {
        if (biomeSet == null) {
            return PLAINS;
        }
        String lower = biomeSet.toLowerCase(Locale.ROOT);
        return ALL.contains(lower) ? lower : PLAINS;
    }

    /**
     * The village style a biome's key belongs to ("minecraft:snowy_taiga" is snowy; "desert" is desert), plains
     * when it has no village style of its own.
     */
    public static String forBiome(String biomeKey) {
        if (biomeKey == null) {
            return PLAINS;
        }
        String name = biomeKey.toLowerCase(Locale.ROOT);
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        if (name.contains("snowy") || name.contains("frozen") || name.equals("ice_spikes") || name.equals("grove")) {
            return SNOWY;
        }
        if (name.contains("taiga")) {
            return TAIGA;
        }
        if (name.contains("desert") || name.contains("badlands")) {
            return DESERT;
        }
        if (name.contains("savanna")) {
            return SAVANNA;
        }
        return PLAINS;
    }

    /** One plains-palette material in the given style. Anything with no style of its own comes through unchanged. */
    public static String substitute(String material, String biomeSet) {
        String set = normalize(biomeSet);
        if (PLAINS.equals(set)) {
            return material;
        }
        String name = material.toUpperCase(Locale.ROOT);
        if (DESERT.equals(set)) {
            if (name.equals("COBBLESTONE") || name.equals("STONE_BRICKS") || name.equals("OAK_PLANKS")
                    || name.equals("OAK_LOG")) {
                return "SANDSTONE";
            }
            if (name.equals("OAK_SLAB") || name.equals("COBBLESTONE_SLAB")) {
                return "SANDSTONE_SLAB";
            }
            if (name.equals("OAK_STAIRS") || name.equals("COBBLESTONE_STAIRS")) {
                return "SANDSTONE_STAIRS";
            }
            return name.startsWith("OAK_") ? "ACACIA_" + name.substring(4) : material;
        }
        String wood = SAVANNA.equals(set) ? "ACACIA_" : "SPRUCE_";
        return name.startsWith("OAK_") ? wood + name.substring(4) : material;
    }
}
