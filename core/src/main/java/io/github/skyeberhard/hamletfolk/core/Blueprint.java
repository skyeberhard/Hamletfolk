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
            halvesOf(entry.getKey()).ifPresent(h -> halves.merge(h.type(), h.halves() * entry.getValue(), Integer::sum));
        }
        EnumMap<ResourceType, Integer> units = new EnumMap<>(ResourceType.class);
        halves.forEach((type, h) -> units.put(type, (h + 1) / 2));
        return units;
    }

    /** R4.20: crops a farm is laid out with grow from seed, so a hungry village can still plant its first farm. */
    private static final java.util.Set<String> PLANTED = java.util.Set.of("WHEAT", "CARROTS", "POTATOES", "BEETROOTS",
            "MELON_STEM", "PUMPKIN_STEM", "ATTACHED_MELON_STEM", "ATTACHED_PUMPKIN_STEM");

    /**
     * R4.26: how many beds the blueprint holds. A bed is two blocks; where a block names its part, the heads are counted,
     * otherwise half the bed blocks.
     */
    public int beds() {
        int heads = 0;
        int halves = 0;
        for (Block block : blocks) {
            String material = block.material();
            if (!name(material).toUpperCase(Locale.ROOT).endsWith("_BED")) {
                continue;
            }
            String lower = material.toLowerCase(Locale.ROOT);
            if (lower.contains("part=head")) {
                heads++;
            } else if (!lower.contains("part=")) {
                halves++;
            }
        }
        return heads + halves / 2;
    }

    /** What one block of a material costs, in half-units of a resource. */
    public record Halves(ResourceType type, int halves) {
    }

    /** R4.8: the cost of placing one block, or empty if it is free (air, torches, doors, chests, crops). */
    public static Optional<Halves> halvesOf(String material) {
        String name = name(material);
        if ("AIR".equalsIgnoreCase(name) || PLANTED.contains(name.toUpperCase(Locale.ROOT))) {
            return Optional.empty();
        }
        Optional<ResourceMapper.Value> value = blockValue(name);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        int perBlock = Math.max(1, value.get().numerator() * 2 / value.get().denominator());
        if (name.toUpperCase(Locale.ROOT).endsWith("_SLAB")) {
            perBlock = Math.max(1, perBlock / 2);
        }
        return Optional.of(new Halves(value.get().type(), perBlock));
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
        ResourceMapper.Value station = WORKSTATIONS.get(name);
        if (station != null) {
            return Optional.of(station); // R8.13: a villager's workstation is worth what it is made of
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

    /** R8.13: what the workstations and furnishings of the trade buildings cost, in planks (or the unit of their material). */
    private static final java.util.Map<String, ResourceMapper.Value> WORKSTATIONS = java.util.Map.of(
            "BARREL", new ResourceMapper.Value(ResourceType.WOOD, 8),
            "LOOM", new ResourceMapper.Value(ResourceType.WOOD, 6),
            "LECTERN", new ResourceMapper.Value(ResourceType.WOOD, 10),
            "BOOKSHELF", new ResourceMapper.Value(ResourceType.WOOD, 6),
            "CARTOGRAPHY_TABLE", new ResourceMapper.Value(ResourceType.WOOD, 6),
            "BEEHIVE", new ResourceMapper.Value(ResourceType.WOOD, 6),
            "SMOKER", new ResourceMapper.Value(ResourceType.STONE, 8),
            "CAULDRON", new ResourceMapper.Value(ResourceType.METAL, 7));

    /** A material without its block state: "LADDER[facing=south]" is "LADDER". */
    static String name(String material) {
        int bracket = material.indexOf('[');
        return bracket < 0 ? material : material.substring(0, bracket);
    }

    /** The wall sign that carries the building's name, if it has one. */
    public Optional<Block> sign() {
        return blocks.stream().filter(b -> name(b.material()).endsWith("_WALL_SIGN")).findFirst();
    }

    private static final String[] COMPASS = {"north", "east", "south", "west"};

    private static int compass(String direction) {
        for (int i = 0; i < 4; i++) {
            if (COMPASS[i].equals(direction)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The direction the building's entrance faces, as 0 north, 1 east, 2 south, 3 west: where its wall sign faces if it
     * has one, otherwise the side of the building its (lowest) door is on. Empty if it has neither (a farm).
     */
    public java.util.OptionalInt front() {
        for (Block block : blocks) {
            if (name(block.material()).toUpperCase(Locale.ROOT).endsWith("_WALL_SIGN")) {
                int facing = compass(Construction.stateOf(block.material(), "facing"));
                if (facing >= 0) {
                    return java.util.OptionalInt.of(facing);
                }
            }
        }
        Block door = null;
        for (Block block : blocks) {
            if (name(block.material()).toUpperCase(Locale.ROOT).endsWith("_DOOR")
                    && !"upper".equals(Construction.stateOf(block.material(), "half"))
                    && (door == null || block.y() < door.y())) {
                door = block;
            }
        }
        if (door == null) {
            return java.util.OptionalInt.empty();
        }
        double dx = door.x() - (width - 1) / 2.0;
        double dz = door.z() - (depth - 1) / 2.0;
        // Which wall the door is in comes from the way the door faces (a north- or south-facing door is in a north or
        // south wall); where it is on that axis says which of the two.
        int facing = compass(Construction.stateOf(door.material(), "facing"));
        boolean eastWest = facing >= 0 ? facing % 2 == 1 : Math.abs(dx) > Math.abs(dz);
        return java.util.OptionalInt.of(eastWest ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0));
    }

    /** The building turned clockwise (seen from above) by whole quarter turns, blocks and the way they face included. */
    public Blueprint rotated(int quarterTurns) {
        Blueprint turned = this;
        for (int i = 0; i < ((quarterTurns % 4) + 4) % 4; i++) {
            List<Block> out = new ArrayList<>(turned.blocks.size());
            for (Block block : turned.blocks) {
                out.add(new Block(turned.depth - 1 - block.z(), block.y(), block.x(), turnState(block.material())));
            }
            turned = new Blueprint(key, turned.depth, turned.height, turned.width, out);
        }
        return turned;
    }

    /** One clockwise quarter turn of a block's properties: which way it faces, its axis and sign rotation, its connections. */
    static String turnState(String material) {
        int open = material.indexOf('[');
        int close = material.lastIndexOf(']');
        if (open < 0 || close < open) {
            return material;
        }
        java.util.Map<String, String> turned = new java.util.LinkedHashMap<>();
        for (String pair : material.substring(open + 1, close).split(",")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                turned.put(pair, "");
                continue;
            }
            String key = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            if (compass(key) >= 0) { // a fence, wall or pane side: the side moves, not the value
                turned.put(COMPASS[(compass(key) + 1) % 4], value);
            } else if (key.equals("facing") && compass(value) >= 0) {
                turned.put(key, COMPASS[(compass(value) + 1) % 4]);
            } else if (key.equals("axis")) {
                turned.put(key, value.equals("x") ? "z" : value.equals("z") ? "x" : value);
            } else if (key.equals("rotation")) {
                try {
                    turned.put(key, String.valueOf((Integer.parseInt(value) + 4) % 16));
                } catch (NumberFormatException e) {
                    turned.put(key, value);
                }
            } else if (key.equals("shape") && value.contains("_") && (value.startsWith("north") || value.startsWith("east")
                    || value.startsWith("south") || value.startsWith("ascending"))) {
                turned.put(key, turnRail(value));
            } else {
                turned.put(key, value);
            }
        }
        StringBuilder out = new StringBuilder(material.substring(0, open)).append('[');
        boolean first = true;
        for (java.util.Map.Entry<String, String> entry : turned.entrySet()) {
            out.append(first ? "" : ",").append(entry.getKey());
            if (!entry.getValue().isEmpty()) {
                out.append('=').append(entry.getValue());
            }
            first = false;
        }
        return out.append(']').toString();
    }

    private static String turnRail(String shape) {
        return switch (shape) {
            case "north_south" -> "east_west";
            case "east_west" -> "north_south";
            case "ascending_north" -> "ascending_east";
            case "ascending_east" -> "ascending_south";
            case "ascending_south" -> "ascending_west";
            case "ascending_west" -> "ascending_north";
            case "south_east" -> "south_west";
            case "south_west" -> "north_west";
            case "north_west" -> "north_east";
            case "north_east" -> "south_east";
            default -> shape;
        };
    }

    /** The same blocks moved by a whole number of blocks (to put an old building in a new building's frame). */
    public Blueprint shifted(int dx, int dy, int dz) {
        List<Block> moved = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            moved.add(new Block(block.x() + dx, block.y() + dy, block.z() + dz, block.material()));
        }
        return new Blueprint(key, width, height, depth, moved);
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
            if (!Construction.matches(now, block.material())) {
                todo.add(block);
            }
        }
        return todo;
    }

    /** The cost of a list of blocks to place (as {@link #cost()} but for a diff). */
    public static Map<ResourceType, Integer> costOf(List<Block> toPlace) {
        return new Blueprint("diff", 1, 1, 1, toPlace).cost();
    }

    /** R4.18: a plain-text form, one block per line, for saving a captured building ({@link #fromText}). */
    public String toText() {
        StringBuilder out = new StringBuilder("hamletfolk-blueprint 1\n");
        out.append("key ").append(key).append('\n');
        out.append("size ").append(width).append(' ').append(height).append(' ').append(depth).append('\n');
        for (Block block : blocks) {
            out.append(block.x()).append(' ').append(block.y()).append(' ').append(block.z()).append(' ')
                    .append(block.material()).append('\n');
        }
        return out.toString();
    }

    /** Reads {@link #toText}; any malformed line is an {@link IllegalArgumentException} naming it. */
    public static Blueprint fromText(String text) {
        String[] lines = text.split("\\R");
        if (lines.length < 3 || !lines[0].trim().equals("hamletfolk-blueprint 1")
                || !lines[1].startsWith("key ") || !lines[2].startsWith("size ")) {
            throw new IllegalArgumentException("not a blueprint file");
        }
        try {
            String[] size = lines[2].trim().split(" ");
            List<Block> blocks = new ArrayList<>();
            for (int i = 3; i < lines.length; i++) {
                if (lines[i].isBlank()) {
                    continue;
                }
                String[] part = lines[i].trim().split(" ", 4);
                blocks.add(new Block(Integer.parseInt(part[0]), Integer.parseInt(part[1]), Integer.parseInt(part[2]),
                        part[3]));
            }
            return new Blueprint(lines[1].substring(4).trim(), Integer.parseInt(size[1]), Integer.parseInt(size[2]),
                    Integer.parseInt(size[3]), blocks);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("bad blueprint file: " + e.getMessage(), e);
        }
    }
}
