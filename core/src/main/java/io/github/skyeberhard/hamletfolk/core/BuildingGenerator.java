package io.github.skyeberhard.hamletfolk.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * R4.18: the buildings the game ships no template for (mine, guard post, shop, treasury), laid out in code in the
 * plains palette so nobody has to hand-build them. (No barrels, lecterns, looms and the like: they are villagers' job
 * sites, and a villager would claim one and take up that trade.) Four tiers each: a crude one in wood and cobblestone, a
 * solid, larger one, a hall (R8.32: the biggest footprint a spare 9 by 9 lot holds, timber-framed in stone, with a carpet and hanging lanterns), and the
 * same hall with a loft over it. Other biomes come from {@link Blueprint#inBiome}. A building captured from the world
 * replaces the generated one (the catalog prefers it).
 *
 * <p>Every building has the same plan: a hut whose body is {@code width} by {@code depth - 1}, a doorway in the
 * middle of the south wall, and a one-block strip in front of it that carries the building's sign
 * ({@code [Mine]} and so on, which the Paper layer writes and registers).
 */
public final class BuildingGenerator {
    public static final int TIERS = 4;

    private BuildingGenerator() {
    }

    /** True for the building types that are generated (the rest come from vanilla village pieces). */
    public static boolean generates(BuildingType type) {
        return switch (type) {
            case MINE, GUARD_POST, SHOP, TREASURY, SAWMILL, FORGE, GRANARY, HARBOUR, PENS, SMOKEHOUSE, TANNERY, STABLE, APIARY,
                    MAP_ROOM, LIBRARY, GLASSWORKS, TRADING_POST, BOWYER, MASONS_YARD, ARMOURY, CHAPEL -> true;
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
        int size = tier == 1 ? 5 : tier == 2 ? 7 : 9;
        int body = tier >= 3 ? 8 : size; // (the sign strip makes it 9 deep: a spare 9 by 9 lot holds it)
        boolean stone = tier >= 2 || type == BuildingType.TREASURY || type == BuildingType.FORGE;
        Plan plan = new Plan(size, body, tier);
        if (tier >= 3) {
            plan.hut("STONE_BRICKS", "OAK_LOG", "OAK_PLANKS", type == BuildingType.TREASURY ? "IRON_BARS" : "GLASS_PANE");
        } else {
            plan.hut(stone ? "STONE_BRICKS" : "OAK_PLANKS", stone ? "STONE_BRICKS" : "OAK_LOG",
                    stone ? "COBBLESTONE" : "OAK_PLANKS", type == BuildingType.TREASURY ? "IRON_BARS" : "GLASS_PANE");
        }
        switch (type) {
            case MINE -> plan.mine(tier);
            case GUARD_POST -> plan.guardPost(tier);
            case SHOP -> plan.shop();
            case TREASURY -> plan.treasury(tier);
            case SAWMILL -> plan.sawmill(tier);
            case FORGE -> plan.forge(tier);
            case GRANARY -> plan.granary(tier);
            case HARBOUR -> plan.furnish(tier, "BARREL", "OAK_FENCE", "CHEST");
            case PENS -> plan.furnish(tier, "LOOM[facing=south]", "HAY_BLOCK", "OAK_FENCE");
            case SMOKEHOUSE -> plan.furnish(tier, "SMOKER[facing=south]", "OAK_FENCE", "CHEST");
            case TANNERY -> plan.furnish(tier, "CAULDRON", "OAK_FENCE", "CHEST");
            case STABLE -> plan.furnish(tier, "HAY_BLOCK", "OAK_FENCE", "CHEST");
            case APIARY -> plan.furnish(tier, "BEEHIVE[facing=south]", "BEEHIVE[facing=south]", "CHEST");
            case MAP_ROOM -> plan.furnish(tier, "CARTOGRAPHY_TABLE", "BOOKSHELF", "CHEST");
            case LIBRARY -> plan.furnish(tier, "LECTERN[facing=south]", "BOOKSHELF", "BOOKSHELF");
            case GLASSWORKS -> plan.furnish(tier, "FURNACE[facing=south]", "COBBLESTONE", "CHEST");
            case BOWYER -> plan.furnish(tier, "FLETCHING_TABLE", "OAK_FENCE", "CHEST");
            case MASONS_YARD -> plan.furnish(tier, "STONECUTTER", "COBBLESTONE", "CHEST");
            case ARMOURY -> plan.furnish(tier, "GRINDSTONE[face=floor,facing=south]", "BLAST_FURNACE[facing=south]", "CHEST");
            case CHAPEL -> plan.furnish(tier, "BOOKSHELF", "OAK_FENCE", "CHEST");
            case TRADING_POST -> {
                plan.shop();
                plan.furnish(tier, "CHEST", "CHEST", "CHEST");
            }
            default -> throw new IllegalStateException();
        }
        plan.grand();
        plan.sign();
        return Optional.of(plan.toBlueprint("generated:" + type.name().toLowerCase(java.util.Locale.ROOT) + "/tier" + tier));
    }

    /** A building under construction: later puts replace earlier ones at the same place. */
    private static final class Plan {
        private final int width;
        private final int body; // depth of the hut itself; the sign strip is one more
        private final int tier;
        /** The level of the roof: 4 for the single storey, 7 for a hall with a loft. */
        private final int roof;
        private final Map<Long, Blueprint.Block> cells = new LinkedHashMap<>();

        Plan(int width, int body, int tier) {
            this.width = width;
            this.body = body;
            this.tier = tier;
            this.roof = tier >= 4 ? 7 : 4;
        }

        private boolean free(int x, int y, int z) {
            return !cells.containsKey(((long) (y + 64) << 40) | ((long) z << 20) | x);
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
                    put(x, roof, z, tier >= 3 ? "STONE_BRICK_SLAB" : "OAK_SLAB");
                    boolean edge = x == 0 || z == 0 || x == width - 1 || z == body - 1;
                    for (int y = 1; y < roof && edge; y++) {
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

        /**
         * R8.32: what the third and fourth tiers add to any design: a carpet runner from the door, lanterns hanging at the back, and (the fourth)
         * a loft with a ladder up to it and windows above. Later than the design itself, and never over something it put there.
         */
        void grand() {
            if (tier < 3) {
                return;
            }
            for (int z = 1; z < body - 1; z++) {
                if (free(door(), 1, z)) {
                    put(door(), 1, z, "RED_CARPET");
                }
            }
            put(1, roof - 1, body - 2, "LANTERN[hanging=true]");
            put(width - 2, roof - 1, body - 2, "LANTERN[hanging=true]");
            if (tier >= 4) {
                for (int x = 1; x < width - 1; x++) {
                    for (int z = 1; z < body - 1; z++) {
                        if (!(x == width - 2 && z == body - 2)) {
                            put(x, 4, z, "OAK_PLANKS"); // the loft floor, with the ladder's opening left
                        }
                    }
                }
                for (int y = 1; y <= 5; y++) {
                    put(width - 2, y, body - 2, "LADDER[facing=west]");
                }
                put(0, 5, body / 2, "GLASS_PANE");
                put(width - 1, 5, body / 2, "GLASS_PANE");
                put(width / 2, 5, 0, "GLASS_PANE");
                put(width / 2, 5, body - 1, "GLASS_PANE");
            }
        }

        void mine(int tier) {
            put(1, 1, 1, "CHEST");
            put(width - 2, 1, 1, "CHEST");
            put(width - 2, 2, 1, "LANTERN");
            // The shaft: cut through the floor and down, a ladder on its north face.
            int x = width / 2;
            int z = body / 2;
            int depth = 3 + 2 * (tier - 1);
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
            if (tier >= 2) {
                // Battlements round the roof edge.
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < body; z++) {
                        if (x == 0 || z == 0 || x == width - 1 || z == body - 1) {
                            put(x, roof + 1, z, (x + z) % 2 == 0 ? "COBBLESTONE_WALL" : "AIR");
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
            if (tier >= 2) {
                put(1, 1, 2, "CHEST");
                put(width - 2, 1, 2, "CHEST");
            }
            if (tier >= 3) {
                put(3, 1, 1, "CHEST");
                put(width - 3, 1, 1, "CHEST");
                put(1, 1, 3, "CHEST");
                put(width - 2, 1, 3, "CHEST");
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
            if (tier >= 2) {
                put(1, 1, 2, "OAK_LOG");
                put(1, 2, 2, "OAK_LOG");
                put(2, 2, 1, "OAK_LOG");
                put(width - 3, 1, 1, "OAK_PLANKS");
            }
            if (tier >= 3) {
                put(width - 3, 2, 1, "OAK_PLANKS");
                put(3, 1, 2, "OAK_LOG");
                put(1, 1, 3, "OAK_LOG");
                put(1, 2, 3, "OAK_LOG");
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
            if (tier >= 2) {
                put(3, 1, 1, "FURNACE[facing=south]");
                put(1, 1, 2, "CHEST");
                put(width - 2, 1, 2, "CHEST");
            }
            if (tier >= 3) {
                put(4, 1, 1, "FURNACE[facing=south]");
                put(width - 3, 1, 1, "FURNACE[facing=south]");
                put(width / 2, 1, 4, "ANVIL[facing=north]");
            }
        }

        /**
         * R8.13: a trade building's fittings: its workstation (or main fitting) by the west wall, a second and third fitting
         * beside it, and a chest and lantern; a second tier adds another of each of the second and third and a spare chest.
         */
        void furnish(int tier, String station, String second, String third) {
            put(1, 1, 1, station);
            put(2, 1, 1, second);
            put(1, 1, 2, third);
            put(width - 2, 1, 1, "CHEST");
            put(width - 2, 2, 1, "LANTERN"); // on the chest, as in the other designs
            if (tier >= 3) {
                put(4, 1, 1, second);
                put(3, 1, 2, third);
                put(width - 2, 1, 3, "CHEST");
                put(1, 1, 3, third);
            }
            if (tier >= 2) {
                put(3, 1, 1, second);
                put(1, 2, 1, second.startsWith("BOOKSHELF") || second.startsWith("HAY") ? second : "AIR");
                put(2, 1, 2, third);
                put(width - 2, 1, 2, "CHEST");
            }
        }

        /** R8.12: bales of hay and chests of grain, raised on planks off the floor. */
        void granary(int tier) {
            int bales = switch (tier) {
                case 1 -> 4;
                case 2 -> 8;
                case 3 -> 12;
                default -> 18;
            };
            int columns = tier >= 3 ? 3 : 2;
            for (int i = 0; i < bales; i++) {
                put(1 + i % columns, 1 + i / (2 * columns), 1 + (i / columns) % 2, "HAY_BLOCK");
            }
            put(width - 2, 1, 1, "CHEST");
            put(width - 2, 1, 2, "CHEST");
            put(width - 2, 2, 1, "LANTERN");
            if (tier >= 2) {
                put(width - 3, 1, 1, "CHEST");
            }
            if (tier >= 3) {
                put(width - 4, 1, 1, "CHEST");
                put(width - 3, 1, 2, "CHEST");
            }
        }

        /** The sign next to the doorway, outside the south wall. */
        void sign() {
            put(door() + 1, 2, body, "OAK_WALL_SIGN[facing=south]");
        }

        Blueprint toBlueprint(String key) {
            int height = 6;
            for (Blueprint.Block block : cells.values()) {
                height = Math.max(height, block.y() + 1);
            }
            return new Blueprint(key, width, height, body + 1, java.util.List.copyOf(cells.values()));
        }
    }
}
