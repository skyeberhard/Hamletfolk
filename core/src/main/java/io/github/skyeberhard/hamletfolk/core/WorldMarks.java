package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * R4.25: the marks the village's work leaves on the land. The wood a lumberjack gathers and the stone a miner digs are
 * owed to the world as they are counted; the Paper layer pays the debt whenever the ground is loaded, by felling trees
 * (a log pays {@link #UNITS_PER_LOG} wood) and digging a quarry (a block pays one stone). The debt is capped at
 * {@link #MAX_OWED}, so a village left alone for months clears a few trees, not a forest. What the quarry turns up is kept:
 * an ore block adds what it drops to the stores ({@link #drop}), over and above the miner's fixed table (R3.17).
 */
public final class WorldMarks {
    /** The most of each that is ever owed. */
    public static final int MAX_OWED = 256;
    public static final int UNITS_PER_LOG = 4;
    static final String WOOD_OWED = "woodOwed";
    static final String STONE_OWED = "stoneOwed";

    private WorldMarks() {
    }

    private static String key(ResourceType type) {
        return type == ResourceType.WOOD ? WOOD_OWED : STONE_OWED;
    }

    /** Adds to what is owed of wood or stone, up to the cap. Anything else is ignored. */
    static void owe(Settlement settlement, ResourceType type, int units) {
        if ((type != ResourceType.WOOD && type != ResourceType.STONE) || units <= 0) {
            return;
        }
        long owed = Math.min(MAX_OWED, owed(settlement, type) + (long) units);
        settlement.conditions().put(key(type), owed);
    }

    /** How much wood or stone is owed to the world. */
    public static int owed(Settlement settlement, ResourceType type) {
        return (int) (long) settlement.conditions().getOrDefault(key(type), 0L);
    }

    /** Keeps what a block of ore dropped: it goes into the stores and into the flow, as the miners' own ore does. */
    public static void keep(Settlement settlement, Drop drop, long day) {
        settlement.ledger().add(drop.commodity(), drop.count());
        settlement.flow().recordProduced(drop.commodity().category(), day, drop.count());
    }

    /** Pays some of the debt (never below nothing). */
    public static void pay(Settlement settlement, ResourceType type, int units) {
        long left = Math.max(0, owed(settlement, type) - (long) Math.max(0, units));
        if (left == 0) {
            settlement.conditions().remove(key(type));
        } else {
            settlement.conditions().put(key(type), left);
        }
    }

    // ----- the quarry -----

    static final String QUARRY_X = "quarryX";
    static final String QUARRY_Z = "quarryZ";
    static final String QUARRY_Y = "quarryY";
    static final String QUARRY_BOTTOM = "quarryBottom";

    /** The village's quarry: its north-west corner, the layer being dug, and how deep it goes. */
    public record Quarry(int x, int z, int layer, int bottom) {
        public boolean exhausted() {
            return layer < bottom;
        }
    }

    /** The village's quarry, once it has opened one. */
    public static Optional<Quarry> quarry(Settlement settlement) {
        Long x = settlement.conditions().get(QUARRY_X);
        if (x == null) {
            return Optional.empty();
        }
        return Optional.of(new Quarry((int) (long) x, (int) (long) settlement.conditions().get(QUARRY_Z),
                (int) (long) settlement.conditions().get(QUARRY_Y), (int) (long) settlement.conditions().get(QUARRY_BOTTOM)));
    }

    /** Opens the village's quarry at a corner, digging from {@code top} down to {@code bottom}; the history says so. */
    public static void openQuarry(Settlement settlement, int x, int z, int top, int bottom, long day) {
        settlement.conditions().put(QUARRY_X, (long) x);
        settlement.conditions().put(QUARRY_Z, (long) z);
        settlement.conditions().put(QUARRY_Y, (long) top);
        settlement.conditions().put(QUARRY_BOTTOM, (long) bottom);
        settlement.record(day, HistoryEvent.Kind.BUILDING, settlement.name() + " opened a quarry beside its mine.");
    }

    /** A layer of the quarry is dug out: the next one down, and the history notes when it has gone as deep as it goes. */
    public static void nextLayer(Settlement settlement, long day) {
        Optional<Quarry> q = quarry(settlement);
        if (q.isEmpty() || q.get().exhausted()) {
            return;
        }
        settlement.conditions().put(QUARRY_Y, (long) q.get().layer() - 1);
        if (q.get().layer() - 1 < q.get().bottom()) {
            settlement.record(day, HistoryEvent.Kind.BUILDING, "The quarry of " + settlement.name() + " was dug as deep as it goes.");
        }
    }

    /** A quarry is dug out of the ground at most this wide; the Paper layer checks the plan and zones round all of it. */
    public static final int QUARRY_SIZE = 5;

    /** What an ore drops, as the game drops it without enchantments: a commodity and how many. */
    public record Drop(Commodity commodity, int count) {
    }

    private static final Map<String, Drop> ORES = Map.of(
            "COAL_ORE", new Drop(Commodity.COAL, 1),
            "IRON_ORE", new Drop(Commodity.RAW_IRON, 1),
            "COPPER_ORE", new Drop(Commodity.RAW_COPPER, 3),
            "GOLD_ORE", new Drop(Commodity.RAW_GOLD, 1),
            "REDSTONE_ORE", new Drop(Commodity.REDSTONE, 4),
            "LAPIS_ORE", new Drop(Commodity.LAPIS, 6),
            "DIAMOND_ORE", new Drop(Commodity.DIAMOND, 1));

    /** What an ore block drops (its deepslate form too); empty for anything that is not an ore the village keeps. */
    public static Optional<Drop> drop(String block) {
        String name = block.toUpperCase(Locale.ROOT);
        int colon = name.indexOf(':');
        name = colon >= 0 ? name.substring(colon + 1) : name;
        if (name.startsWith("DEEPSLATE_")) {
            name = name.substring("DEEPSLATE_".length());
        }
        return Optional.ofNullable(ORES.get(name));
    }

    /** The ground a quarry may take: natural stone and soil. Never anything built, and never sand or gravel (they fall). */
    private static final Set<String> QUARRYABLE = Set.of("STONE", "DEEPSLATE", "ANDESITE", "DIORITE", "GRANITE", "TUFF",
            "CALCITE", "DIRT", "GRASS_BLOCK", "COARSE_DIRT", "ROOTED_DIRT", "PODZOL", "CLAY", "SANDSTONE", "RED_SANDSTONE");

    /** True for a block the quarry may dig out: natural stone or soil, or an ore. */
    public static boolean quarryable(String block) {
        String name = block.toUpperCase(Locale.ROOT);
        int colon = name.indexOf(':');
        name = colon >= 0 ? name.substring(colon + 1) : name;
        return QUARRYABLE.contains(name) || drop(name).isPresent();
    }
}
