package io.github.skyeberhard.hamletfolk.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * R4.18: the buildings the game ships no template for (mine, guard post, shop, treasury), laid out in code in the
 * plains palette so nobody has to hand-build them. (No barrels, lecterns, looms and the like: they are villagers' job
 * sites, and a villager would claim one and take up that trade.) Two tiers each: a crude one in wood and cobblestone, and a
 * solid, larger one. Other biomes come from {@link Blueprint#inBiome}. A building captured from the world
 * replaces the generated one (the catalog prefers it).
 *
 * <p>Every building has the same plan: a hut whose body is {@code width} by {@code depth - 1}, a doorway in the
 * middle of the south wall, and a one-block strip in front of it that carries the building's sign
 * ({@code [Mine]} and so on, which the Paper layer writes and registers).
 */
public final class BuildingGenerator {
    public static final int TIERS = 2;

    private BuildingGenerator() {
    }

    /** True for the building types that are generated (the rest come from vanilla village pieces). */
    public static boolean generates(BuildingType type) {
        return switch (type) {
            case MINE, GUARD_POST, SHOP, TREASURY, SAWMILL, FORGE, GRANARY -> true;
            case FARM, SMITHY, HOUSE, SQUARE, STREET_LIGHTS, PALISADE, RAMPART, GATEHOUSE, TOWER -> false;
        };
    }

    /** The plains-palette building of this type and tier (1 or 2); empty for a type vanilla ships a piece for. */
    public static Optional<Blueprint> generate(BuildingType type, int tier) {
        if (!generates(type)) {
            return Optional.empty();
        }
        if (tier < 1 || tier > TIERS) {
            throw new IllegalArgumentException("tier must be 1 to " + TIERS);
        }
        int size = tier == 1 ? 5 : 7;
        boolean stone = tier == 2 || type == BuildingType.TREASURY || type == BuildingType.FORGE;
        Plan plan = new Plan(size, size);
        plan.hut(stone ? "STONE_BRICKS" : "OAK_PLANKS", stone ? "STONE_BRICKS" : "OAK_LOG",
                stone ? "COBBLESTONE" : "OAK_PLANKS", type == BuildingType.TREASURY ? "IRON_BARS" : "GLASS_PANE");
        switch (type) {
            case MINE -> plan.mine(tier);
            case GUARD_POST -> plan.guardPost(tier);
            case SHOP -> plan.shop();
            case TREASURY -> plan.treasury(tier);
            case SAWMILL -> plan.sawmill(tier);
            case FORGE -> plan.forge(tier);
            case GRANARY -> plan.granary(tier);
            default -> throw new IllegalStateException();
        }
        plan.sign();
        return Optional.of(plan.toBlueprint("generated:" + type.name().toLowerCase(java.util.Locale.ROOT) + "/tier" + tier));
    }

    /** A building under construction: later puts replace earlier ones at the same place. */
    private static final class Plan {
        private final int width;
        private final int body; // depth of the hut itself; the sign strip is one more
        private final Map<Long, Blueprint.Block> cells = new LinkedHashMap<>();

        Plan(int width, int body) {
            this.width = width;
            this.body = body;
        }

        private int door() {
            return width / 2;
        }

        void put(int x, int y, int z, String material) {
            cells.put(((long) (y + 64) << 40) | ((long) z << 20) | x, new Blueprint.Block(x, y, z, material));
        }

        /** Floor, walls three high, windows, doorway and a slab roof. */
        void hut(String wall, String corner, String floor, String window) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < body; z++) {
                    put(x, 0, z, floor);
                    put(x, 4, z, "OAK_SLAB");
                    boolean edge = x == 0 || z == 0 || x == width - 1 || z == body - 1;
                    for (int y = 1; y <= 3 && edge; y++) {
                        boolean post = (x == 0 || x == width - 1) && (z == 0 || z == body - 1);
                        put(x, y, z, post ? corner : wall);
                    }
                }
            }
            put(0, 2, body / 2, window);
            put(width - 1, 2, body / 2, window);
            put(width / 2, 2, 0, window);
            put(door(), 1, body - 1, "AIR");
            put(door(), 2, body - 1, "AIR");
        }

        void mine(int tier) {
            put(1, 1, 1, "CHEST");
            put(width - 2, 1, 1, "CHEST");
            put(width - 2, 2, 1, "LANTERN");
            // The shaft: cut through the floor and down, a ladder on its north face.
            int x = width / 2;
            int z = body / 2;
            int depth = tier == 1 ? 3 : 5;
            put(x, 0, z, "AIR");
            for (int y = -1; y >= -depth; y--) {
                put(x, y, z, "LADDER[facing=south]");
                put(x, y, z - 1, "COBBLESTONE");
            }
            put(x, -depth - 1, z, "COBBLESTONE");
            put(x, 1, z, "AIR");
            put(x - 1, 1, z, "OAK_FENCE");
            put(x + 1, 1, z, "OAK_FENCE");
        }

        void guardPost(int tier) {
            put(1, 1, 1, "CHEST");
            put(width - 2, 1, 1, "CHEST");
            put(width - 2, 2, 1, "LANTERN");
            put(1, 1, body - 2, "CRAFTING_TABLE");
            if (tier == 2) {
                // Battlements round the roof edge.
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < body; z++) {
                        if (x == 0 || z == 0 || x == width - 1 || z == body - 1) {
                            put(x, 5, z, (x + z) % 2 == 0 ? "COBBLESTONE_WALL" : "AIR");
                        }
                    }
                }
            }
        }

        void shop() {
            // A counter across the room with the stock behind it; the right-hand end is left open to walk through.
            for (int x = 1; x <= width - 3; x++) {
                put(x, 1, 2, "OAK_PLANKS");
                put(x, 2, 2, "OAK_SLAB");
                put(x, 1, 1, "CHEST");
            }
            put(width - 2, 2, 1, "LANTERN");
            put(width - 2, 1, 1, "CHEST");
        }

        void treasury(int tier) {
            put(1, 1, 1, "CHEST");
            put(2, 1, 1, "CHEST");
            put(width - 2, 1, 1, "CHEST");
            put(width - 2, 2, 1, "LANTERN");
            if (tier == 2) {
                put(1, 1, 2, "CHEST");
                put(width - 2, 1, 2, "CHEST");
            }
        }

        /** R8.12: stacked logs and planks, a chest of tools, a sawing bench (a fence and slab) and a lantern. */
        void sawmill(int tier) {
            put(1, 1, 1, "OAK_LOG");
            put(2, 1, 1, "OAK_LOG");
            put(1, 2, 1, "OAK_LOG");
            put(width - 2, 1, 1, "OAK_PLANKS");
            put(width - 2, 2, 1, "OAK_PLANKS");
            put(width - 2, 1, 2, "CHEST");
            put(width / 2, 1, 2, "OAK_FENCE");
            put(width / 2, 2, 2, "OAK_SLAB");
            put(width - 2, 2, 2, "LANTERN"); // on the chest, as in the other designs
            if (tier == 2) {
                put(1, 1, 2, "OAK_LOG");
                put(1, 2, 2, "OAK_LOG");
                put(2, 2, 1, "OAK_LOG");
                put(width - 3, 1, 1, "OAK_PLANKS");
            }
        }

        /** R8.12: furnaces and an anvil, iron bars at the window, a chest of ore and a lantern. (No job-site blocks.) */
        void forge(int tier) {
            put(1, 1, 1, "FURNACE[facing=south]");
            put(2, 1, 1, "FURNACE[facing=south]");
            put(width - 2, 1, 1, "CHEST");
            put(width / 2, 1, 2, "ANVIL[facing=east]");
            put(width - 2, 2, 1, "LANTERN"); // on the chest, as in the other designs
            put(0, 3, body / 2, "IRON_BARS");
            if (tier == 2) {
                put(3, 1, 1, "FURNACE[facing=south]");
                put(1, 1, 2, "CHEST");
                put(width - 2, 1, 2, "CHEST");
            }
        }

        /** R8.12: bales of hay and chests of grain, raised on planks off the floor. */
        void granary(int tier) {
            int bales = tier == 1 ? 4 : 8;
            for (int i = 0; i < bales; i++) {
                put(1 + i % 2, 1 + i / 4, 1 + (i / 2) % 2, "HAY_BLOCK");
            }
            put(width - 2, 1, 1, "CHEST");
            put(width - 2, 1, 2, "CHEST");
            put(width - 2, 2, 1, "LANTERN");
            if (tier == 2) {
                put(width - 3, 1, 1, "CHEST");
            }
        }

        /** The sign next to the doorway, outside the south wall. */
        void sign() {
            put(door() + 1, 2, body, "OAK_WALL_SIGN[facing=south]");
        }

        Blueprint toBlueprint(String key) {
            return new Blueprint(key, width, 6, body + 1, java.util.List.copyOf(cells.values()));
        }
    }
}
