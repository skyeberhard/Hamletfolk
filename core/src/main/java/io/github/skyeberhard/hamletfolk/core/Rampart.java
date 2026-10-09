package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * R5.10: the rampart that strengthens a village's palisade, tier by tier, as a list of pieces on the palisade's ring: a
 * wall column for every post of the ring, a pillar either side of each gap with a lintel over it (the gate), and a watch
 * tower at each corner. Tier 1 is the fence (R5.6); tier 2 is planks two high under a fence rail with log pillars and
 * planked towers; tier 3 is stone bricks three high under a wall with stone pillars and taller stone towers. Pure geometry
 * and cost: the Paper layer finds the ground under each piece's anchor column and places the blocks.
 */
public final class Rampart {
    /** The lowest tier of the rampart proper; tier 1 is the fence. */
    public static final int FIRST_TIER = 2;
    public static final int LAST_TIER = 3;

    private Rampart() {
    }

    /**
     * One block of a piece, relative to the ground block of the piece's anchor column (dy 1 is the first block above it).
     * {@code ifEmpty} blocks (foundations) are only placed where there is nothing, and count as standing wherever
     * there is something solid.
     */
    public record Block(int dx, int dy, int dz, String material, boolean ifEmpty) {
        Block(int dx, int dy, int dz, String material) {
            this(dx, dy, dz, material, false);
        }
    }

    /** A wall column, a gate pillar or lintel, or a tower: blocks round one anchor column. */
    public record Piece(int x, int z, List<Block> blocks) {
    }

    /** What the wall is made of, bottom to top, for a tier. */
    static String[] wall(int tier) {
        return tier >= 3 ? new String[] {"STONE_BRICKS", "STONE_BRICKS", "STONE_BRICKS", "STONE_BRICK_WALL"}
                : new String[] {"OAK_PLANKS", "OAK_PLANKS", "OAK_FENCE"};
    }

    private static String body(int tier) {
        return tier >= 3 ? "STONE_BRICKS" : "OAK_PLANKS";
    }

    /** The pieces of the rampart of a tier round the ring of a stage: walls, then gates, then towers. Empty without a ring. */
    public static List<Piece> pieces(VillagePlan plan, int stage, int tier) {
        Optional<Works.Ring> found = plan == null ? Optional.empty() : Works.ring(plan, stage);
        if (found.isEmpty() || tier < FIRST_TIER) {
            return List.of();
        }
        Works.Ring ring = found.get();
        List<Works.Spot> cells = ring.cells();
        int n = cells.size();
        boolean[] pillar = new boolean[n];
        // The lintel over a gate is carried by a pillar (it has no ground of its own to stand on): the blocks that go over the
        // gap's cells, at the height of the pillar's ground, with the pillar's piece.
        java.util.Map<Integer, List<Block>> lintels = new java.util.HashMap<>();
        // Each run of gap cells is a gate: a pillar on the wall cell either side (unless that is a corner, which has a tower).
        for (int i = 0; i < n; i++) {
            if (!ring.gap()[i] || ring.gap()[(i - 1 + n) % n]) {
                continue; // (not the start of a run)
            }
            int end = i;
            while (ring.gap()[(end + 1) % n] && (end + 1) % n != i) {
                end++;
            }
            int before = (i - 1 + n) % n;
            int after = (end + 1) % n;
            if (!ring.gap()[before] && !ring.corner(before)) {
                pillar[before] = true;
            }
            if (!ring.gap()[after] && !ring.corner(after)) {
                pillar[after] = true;
            }
            int carrier = pillar[before] ? before : pillar[after] ? after : -1;
            if (carrier >= 0) {
                List<Block> over = new ArrayList<>();
                for (int g = i; ; g = (g + 1) % n) {
                    over.add(new Block(cells.get(g).x() - cells.get(carrier).x(), 4, cells.get(g).z() - cells.get(carrier).z(), body(tier)));
                    if (g == end) {
                        break;
                    }
                }
                lintels.computeIfAbsent(carrier, k -> new ArrayList<>()).addAll(over);
            }
        }
        List<Piece> out = new ArrayList<>();
        String[] profile = wall(tier);
        for (int i = 0; i < n; i++) {
            if (ring.gap()[i] || ring.corner(i) || pillar[i]) {
                continue;
            }
            List<Block> blocks = new ArrayList<>();
            for (int h = 0; h < profile.length; h++) {
                blocks.add(new Block(0, h + 1, 0, profile[h]));
            }
            out.add(new Piece(cells.get(i).x(), cells.get(i).z(), blocks));
        }
        for (int i = 0; i < n; i++) {
            if (pillar[i]) {
                out.add(pillarAt(cells.get(i), tier, lintels.getOrDefault(i, List.of())));
            }
        }
        for (int i = 0; i < n; i++) {
            if (ring.corner(i) && !ring.gap()[i]) {
                out.add(tower(cells.get(i), ring.rect(), tier));
            }
        }
        return out;
    }

    private static Piece pillarAt(Works.Spot cell, int tier, List<Block> lintel) {
        List<Block> blocks = new ArrayList<>();
        int height = tier >= 3 ? 6 : 5;
        for (int dy = -2; dy <= 0; dy++) {
            blocks.add(new Block(0, dy, 0, "COBBLESTONE", true));
        }
        for (int dy = 1; dy <= height; dy++) {
            blocks.add(new Block(0, dy, 0, tier >= 3 ? "STONE_BRICKS" : "OAK_LOG"));
        }
        blocks.add(new Block(0, height + 1, 0, "TORCH"));
        blocks.addAll(lintel);
        return new Piece(cell.x(), cell.z(), blocks);
    }

    /**
     * A watch tower three by three, reaching inward from a corner of the ring: walls round a hollow with a ladder up to a
     * platform, a parapet along the two outer edges and a torch at the corner. The doorway is on the side facing inward.
     */
    private static Piece tower(Works.Spot corner, Rect ring, int tier) {
        int sx = corner.x() == ring.x() ? 1 : -1;
        int sz = corner.z() == ring.z() ? 1 : -1;
        int height = tier >= 3 ? 6 : 4;
        String body = body(tier);
        String rail = tier >= 3 ? "STONE_BRICK_WALL" : "OAK_FENCE";
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i == 1 && j == 1) {
                    continue; // the hollow
                }
                int dx = sx * i;
                int dz = sz * j;
                for (int dy = -2; dy <= 0; dy++) {
                    blocks.add(new Block(dx, dy, dz, "COBBLESTONE", true));
                }
                for (int dy = 1; dy <= height; dy++) {
                    boolean doorway = i == 1 && j == 2 && dy <= 2;
                    if (!doorway) {
                        blocks.add(new Block(dx, dy, dz, body));
                    }
                }
                blocks.add(new Block(dx, height + 1, dz, body)); // the platform
                if (i == 0 || j == 0) {
                    blocks.add(new Block(dx, height + 2, dz, rail)); // the parapet along the two outer edges
                }
            }
        }
        // The ladder in the hollow, on the outer wall's inner face, up to the platform.
        String facing = sz > 0 ? "south" : "north";
        for (int dy = 1; dy <= height + 1; dy++) {
            blocks.add(new Block(sx, dy, sz, "LADDER[facing=" + facing + "]"));
        }
        blocks.add(new Block(0, height + 3, 0, "TORCH"));
        return new Piece(corner.x(), corner.z(), blocks);
    }

    /** A position relative to a piece's ground, for {@link #replaced}: block x, height above that column's ground, block z. */
    public record Pos(int x, int dy, int z) {
    }

    /**
     * What the tier below put where a tier will build, position by position, as a bare material name: the fence posts of the
     * ring for tier 2 (the only thing the village put there), the pieces of tier 2 for tier 3. The Paper layer replaces a block
     * only if it is exactly this, so a player's own planks, logs or cobblestone on the ring line are never built over.
     */
    public static Map<Pos, String> replaced(VillagePlan plan, int stage, int tier) {
        Map<Pos, String> out = new java.util.HashMap<>();
        if (tier <= FIRST_TIER) {
            for (Works.Spot post : Works.palisade(plan, stage)) {
                out.put(new Pos(post.x(), 1, post.z()), "OAK_FENCE");
            }
            return out;
        }
        for (Piece piece : pieces(plan, stage, tier - 1)) {
            for (Block block : piece.blocks()) {
                if (!block.ifEmpty()) {
                    String material = block.material();
                    int bracket = material.indexOf('[');
                    out.put(new Pos(piece.x() + block.dx(), block.dy(), piece.z() + block.dz()),
                            bracket < 0 ? material : material.substring(0, bracket));
                }
            }
        }
        return out;
    }

    /** What the rampart of a tier costs at the going rate: every block that is paid for, by what it is made of. */
    public static Map<ResourceType, Integer> price(VillagePlan plan, int stage, int tier) {
        Map<ResourceType, Integer> halves = new EnumMap<>(ResourceType.class);
        for (Piece piece : pieces(plan, stage, tier)) {
            for (Block block : piece.blocks()) {
                if (!block.ifEmpty()) {
                    Blueprint.halvesOf(block.material()).ifPresent(h -> halves.merge(h.type(), h.halves(), Integer::sum));
                }
            }
        }
        Map<ResourceType, Integer> price = new EnumMap<>(ResourceType.class);
        halves.forEach((type, h) -> price.put(type, Math.max(1, (int) Math.ceil((long) h * Construction.costPercent() / 200.0))));
        return price;
    }
}
