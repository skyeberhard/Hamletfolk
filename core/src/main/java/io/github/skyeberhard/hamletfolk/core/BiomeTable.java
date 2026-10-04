package io.github.skyeberhard.hamletfolk.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * R8.2: what a biome offers a village. Resources decide where a village goes and the biome decides how it looks, so
 * each biome is worth some fraction (0 to 1) of each {@link SiteResource} to someone living on it. Pure data: the
 * Paper layer only supplies the biome's key, taken from the game's computed biome lookup, which does not generate
 * chunks. An unknown biome (a modded one) is treated as mildly useful for everything.
 */
public final class BiomeTable {
    private static final Map<String, double[]> TABLE = new HashMap<>();
    private static final double[] UNKNOWN = {0.2, 0.2, 0.2, 0.2, 0.2, 0.2};
    private static final double[] NOTHING = {0, 0, 0, 0, 0, 0};

    // water, lumber, farmland, livestock, stone, ore
    private static void put(double water, double lumber, double farmland, double livestock, double stone, double ore,
            String... biomes) {
        for (String biome : biomes) {
            TABLE.put(biome, new double[] {water, lumber, farmland, livestock, stone, ore});
        }
    }

    static {
        put(0.1, 0.1, 1.0, 0.7, 0.1, 0.1, "plains", "sunflower_plains");
        put(0.2, 0.2, 0.8, 0.8, 0.2, 0.1, "meadow");
        put(0.2, 1.0, 0.3, 0.3, 0.2, 0.1, "forest", "flower_forest", "birch_forest", "old_growth_birch_forest");
        put(0.1, 0.9, 0.2, 0.2, 0.2, 0.1, "dark_forest");
        put(0.2, 0.7, 0.4, 0.5, 0.2, 0.1, "cherry_grove");
        put(0.2, 0.9, 0.1, 0.2, 0.4, 0.2, "taiga", "snowy_taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga");
        put(0.1, 0.2, 0.6, 0.8, 0.2, 0.1, "savanna", "savanna_plateau", "windswept_savanna");
        put(0.0, 0.0, 0.0, 0.0, 0.3, 0.1, "desert");
        put(0.0, 0.1, 0.1, 0.1, 0.6, 0.6, "badlands", "eroded_badlands", "wooded_badlands");
        put(0.9, 0.5, 0.2, 0.3, 0.1, 0.0, "swamp", "mangrove_swamp");
        put(1.0, 0.1, 0.3, 0.1, 0.1, 0.0, "river", "frozen_river");
        put(0.6, 0.0, 0.1, 0.1, 0.2, 0.0, "beach", "snowy_beach", "stony_shore");
        put(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, "ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean", "cold_ocean",
                "frozen_ocean", "deep_lukewarm_ocean", "deep_cold_ocean", "deep_frozen_ocean");
        put(0.3, 0.3, 0.1, 0.1, 1.0, 0.7, "windswept_hills", "windswept_gravelly_hills", "windswept_forest");
        put(0.3, 0.1, 0.0, 0.0, 1.0, 0.8, "jagged_peaks", "frozen_peaks", "stony_peaks");
        put(0.3, 0.7, 0.0, 0.1, 0.9, 0.5, "grove", "snowy_slopes");
        put(0.3, 0.0, 0.1, 0.3, 0.1, 0.0, "snowy_plains", "ice_spikes");
        put(0.3, 0.0, 0.5, 0.3, 0.1, 0.0, "mushroom_fields");
        put(0.0, 0.0, 0.0, 0.0, 0.3, 0.5, "dripstone_caves", "lush_caves", "deep_dark");
        put(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "the_void", "nether_wastes", "soul_sand_valley", "crimson_forest",
                "warped_forest", "basalt_deltas", "the_end", "end_highlands", "end_midlands", "small_end_islands",
                "end_barrens");
    }

    private BiomeTable() {
    }

    /** The key without its namespace, in lower case: "minecraft:Plains" is "plains". */
    static String normalize(String biomeKey) {
        if (biomeKey == null) {
            return "";
        }
        String key = biomeKey.toLowerCase(Locale.ROOT);
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }

    /** What the biome is worth for each resource, in the order of {@link SiteResource#values()}. */
    public static double[] resources(String biomeKey) {
        String key = normalize(biomeKey);
        if (key.isEmpty()) {
            return NOTHING.clone();
        }
        return TABLE.getOrDefault(key, UNKNOWN).clone();
    }

    /** True if the biome is one the table knows by name. */
    public static boolean known(String biomeKey) {
        return TABLE.containsKey(normalize(biomeKey));
    }

    /** How much of the biome can be built on: none of an ocean, the whole of the rest. */
    public static boolean buildable(String biomeKey) {
        String key = normalize(biomeKey);
        return !(key.endsWith("ocean") || key.equals("the_void") || key.startsWith("end_")
                || key.equals("the_end") || key.equals("small_end_islands"));
    }
}
