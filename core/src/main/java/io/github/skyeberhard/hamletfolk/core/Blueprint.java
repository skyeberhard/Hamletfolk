package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * R4.6, R4.18: a building as a list of blocks, with no game types in it. Positions are relative to the
 * building's footprint: x and z run from 0 to {@code width - 1} and {@code depth - 1}, y is 0 on the floor and
 * negative below it (a mine shaft). "AIR" means "clear this block". The same shape comes from the generator
 * ({@link BuildingGenerator}) and, later, from a captured build (the Paper layer reads a world area into one),
 * so costs, biome substitution and the diff against the world work on either.
 */
public record Blueprint(String key, int width, int height, int depth, List<Block> blocks) {

    /** One block of a building. */
    public record Block(int x, int y, int z, String material) {
    }

    public Blueprint {
        if (width < 1 || height < 1 || depth < 1) {
            throw new IllegalArgumentException("a blueprint needs a size");
        }
        blocks = blocks.stream()
                .sorted(Comparator.comparingInt(Block::y).thenComparingInt(Block::z).thenComparingInt(Block::x))
                .toList();
    }

    /** The ground it covers. */
    public Rect footprint(int originX, int originZ) {
        return new Rect(originX, originZ, width, depth);
    }

    /** How many of each material it takes, air and signs left out (nothing to buy). */
    public Map<String, Integer> materialCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Block block : blocks) {
            if (!"AIR".equals(block.material())) {
                counts.merge(block.material(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * What it costs the ledger: the resources its blocks are made of, in whole units, rounded up. Blocks the village
     * has no resource for (torches, doors, chests, glass panes) are free: they are fittings, not materials.
     */
    public Map<ResourceType, Integer> cost() {
        // Work in halves so slabs (half a block) add up exactly before rounding.
        EnumMap<ResourceType, Integer> halves = new EnumMap<>(ResourceType.class);
        for (Map.Entry<String, Integer> entry : materialCounts().entrySet()) {
            Optional<ResourceMapper.Value> value = blockValue(name(entry.getKey()));
            if (value.isEmpty()) {
                continue;
            }
            int perBlockHalves = Math.max(1, value.get().numerator() * 2 / value.get().denominator());
            if (name(entry.getKey()).endsWith("_SLAB")) {
                perBlockHalves = Math.max(1, perBlockHalves / 2);
            }
            halves.merge(value.get().type(), perBlockHalves * entry.getValue(), Integer::sum);
        }
        EnumMap<ResourceType, Integer> units = new EnumMap<>(ResourceType.class);
        halves.forEach((type, h) -> units.put(type, (h + 1) / 2));
        return units;
    }

    /**
     * What one placed block is worth in resources: a plank or cobblestone block is itself, a slab, stair, wall or
     * fence is made of its base block (OAK_SLAB of OAK_PLANKS, STONE_BRICK_STAIRS of STONE_BRICKS).
     */
    static Optional<ResourceMapper.Value> blockValue(String material) {
        String name = material.toUpperCase(Locale.ROOT);
        Optional<ResourceMapper.Value> direct = ResourceMapper.value(name);
        if (direct.isPresent()) {
            return direct;
        }
        for (String suffix : new String[] {"_SLAB", "_STAIRS", "_WALL", "_FENCE_GATE", "_FENCE"}) {
            if (name.endsWith(suffix)) {
                String base = name.substring(0, name.length() - suffix.length());
                for (String candidate : new String[] {base + "_PLANKS", base, base + "S", base + "_BRICKS"}) {
                    Optional<ResourceMapper.Value> value = ResourceMapper.value(candidate);
                    if (value.isPresent()) {
                        return value;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** A material without its block state: "LADDER[facing=south]" is "LADDER". */
    static String name(String material) {
        int bracket = material.indexOf('[');
        return bracket < 0 ? material : material.substring(0, bracket);
    }

    /** The wall sign that carries the building's name, if it has one. */
    public Optional<Block> sign() {
        return blocks.stream().filter(b -> name(b.material()).endsWith("_WALL_SIGN")).findFirst();
    }

    /** The same building in another biome's materials (see {@link BiomeSet#substitute}). */
    public Blueprint inBiome(String biomeSet) {
        List<Block> swapped = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            swapped.add(new Block(block.x(), block.y(), block.z(), BiomeSet.substitute(name(block.material()), biomeSet)
                            + block.material().substring(name(block.material()).length())));
        }
        return new Blueprint(key, width, height, depth, swapped);
    }

    /**
     * R4.6: what has to change to build this where the world is: the blocks that do not already match, in the
     * order they should be placed (floor up, so nothing floats). {@code existing} says what is at a relative
     * position now ("AIR" when nothing); a block that already matches is left out, so a half-built or upgraded
     * building only pays for what is missing.
     */
    public List<Block> diff(java.util.function.Function<Block, String> existing) {
        List<Block> todo = new ArrayList<>();
        for (Block block : blocks) {
            String now = existing.apply(block);
            if (!block.material().equalsIgnoreCase(now == null ? "AIR" : now)) {
                todo.add(block);
            }
        }
        return todo;
    }

    /** The cost of a list of blocks to place (as {@link #cost()} but for a diff). */
    public static Map<ResourceType, Integer> costOf(List<Block> toPlace) {
        return new Blueprint("diff", 1, 1, 1, toPlace).cost();
    }
}
