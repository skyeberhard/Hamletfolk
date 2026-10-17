package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * R8.4: grading the ground under a lot, as a plan of blocks. Given the heights of a lot's footprint and a buffer around
 * it, it works out the pad: a target height (the median of the footprint), what to cut above it, the fill below it, a
 * foundation so nothing floats (fill goes down to solid ground, through shallow water), and a blended edge that
 * slopes no more than one block per block from the pad out to the natural ground. It is computed when a building is
 * built, not at founding, so nothing is bulldozed for a building that never comes. Pure: heights in, blocks out.
 *
 * <p>The plan says what role each block has ({@link Role}); the Paper layer maps roles to the biome's blocks (grass
 * and dirt on plains, sand and sandstone in a desert) and places them.
 *
 * <p>R8.21: ground too steep for that (more than {@link #MAX_CHANGE} blocks to cut or fill) can still be built on as a terrace
 * ({@link #terrace}): the pad is levelled to the median as before, up to {@link #TERRACE_CHANGE} blocks, and instead of a blended
 * edge the ring of blocks round the building is a retaining wall where it stands above the ground (filled solid, in wall blocks) and
 * the cut face of a hill beside it is armoured with wall blocks, so the building sits in a clean cut-out.
 */
public final class TerrainPad {
    /** The most a column may be cut or filled, in blocks; a lot needing more is refused. */
    public static final int MAX_CHANGE = 8;
    /** The deepest water a pad will be built into (it is filled to the surface). */
    public static final int MAX_WATER = 3;
    /** R8.21: the most a column of a terraced pad may be cut or filled, in blocks. */
    public static final int TERRACE_CHANGE = 16;
    /** How many blocks below the top the loose soil goes, before the fill is foundation. */
    static final int SOIL_DEPTH = 3;

    private TerrainPad() {
    }

    /** What a placed block is: nothing (a cut), the surface on top, soil below it, or foundation under that. */
    public enum Role {
        AIR, SURFACE, FILL, FOUNDATION,
        /** R8.21: a block of a retaining wall (stone brick; cut sandstone in a desert): the fill under a terrace's edge, and the cut face above it. */
        WALL
    }

    /** One block to change. */
    public record Change(int x, int y, int z, Role role) {
    }

    /**
     * The result for one lot.
     *
     * @param valid         false if the lot cannot be graded (unmeasured ground, water too deep, a cut or fill beyond limits)
     * @param reason        why not, or empty
     * @param targetHeight  the height of the pad's surface
     * @param changes       the blocks to change, from the ground up in each column
     * @param finalHeights  the surface height of every column of the footprint and buffer once graded, keyed by {@link #key}
     */
    public record Pad(Rect footprint, int buffer, boolean valid, String reason, int targetHeight, List<Change> changes,
            Map<Long, Integer> finalHeights, boolean terraced) {
        /** A pad with a blended edge (not terraced). */
        public Pad(Rect footprint, int buffer, boolean valid, String reason, int targetHeight, List<Change> changes, Map<Long, Integer> finalHeights) {
            this(footprint, buffer, valid, reason, targetHeight, changes, finalHeights, false);
        }

        /** How many blocks are removed. */
        public int cut() {
            return (int) changes.stream().filter(c -> c.role() == Role.AIR).count();
        }

        /** How many blocks are added (surface, soil and foundation). */
        public int fill() {
            return (int) changes.stream().filter(c -> c.role() != Role.AIR).count();
        }

        /** The surface height of a column after grading, if it is part of the pad or its buffer. */
        public Integer finalHeight(int x, int z) {
            return finalHeights.get(key(x, z));
        }
    }

    static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static Pad invalid(Rect footprint, int buffer, String reason) {
        return new Pad(footprint, buffer, false, reason, 0, List.of(), Map.of());
    }

    /**
     * Grades {@code footprint} with a blended edge {@code buffer} blocks wide. The target height is the median of the
     * footprint's surface heights. Water columns inside the footprint are filled to the target if they are at most
     * {@link #MAX_WATER} deep; the buffer is left alone where it is water.
     */
    public static Pad compute(Rect footprint, int buffer, HeightSource terrain) {
        return compute(footprint, buffer, terrain, null);
    }

    /**
     * As {@link #compute(Rect, int, HeightSource)} but to a height already chosen ({@code fixedTarget}, or null for the
     * median): a building half graded would otherwise find a different median and move the goalposts.
     */
    public static Pad compute(Rect footprint, int buffer, HeightSource terrain, Integer fixedTarget) {
        List<Integer> heights = new ArrayList<>();
        for (int x = footprint.x(); x <= footprint.maxX(); x++) {
            for (int z = footprint.z(); z <= footprint.maxZ(); z++) {
                int h = terrain.height(x, z);
                if (h == HeightSource.UNKNOWN) {
                    return invalid(footprint, buffer, "the ground at " + x + ", " + z + " has not been measured");
                }
                heights.add(h);
            }
        }
        int[] sorted = heights.stream().mapToInt(Integer::intValue).sorted().toArray();
        int target = fixedTarget != null ? fixedTarget : sorted[(sorted.length - 1) / 2];

        List<Change> changes = new ArrayList<>();
        Map<Long, Integer> finals = new HashMap<>();
        Rect area = footprint.inflated(buffer);
        for (int x = area.x(); x <= area.maxX(); x++) {
            for (int z = area.z(); z <= area.maxZ(); z++) {
                int h = terrain.height(x, z);
                if (h == HeightSource.UNKNOWN) {
                    continue; // beyond what is loaded: the edge simply stops
                }
                int outward = Math.max(Math.max(footprint.x() - x, x - footprint.maxX()),
                        Math.max(Math.max(footprint.z() - z, z - footprint.maxZ()), 0));
                boolean wet = terrain.water(x, z);
                if (wet && outward > 0) {
                    finals.put(key(x, z), h);
                    continue; // the blended edge does not build out into water
                }
                // At the pad itself the surface is the target; further out it may differ from it by no more than the distance.
                int desired = target + Math.max(-outward, Math.min(outward, h - target));
                int floor = wet ? terrain.floor(x, z) : h;
                if (wet && h - floor > MAX_WATER) {
                    return invalid(footprint, buffer, "the water at " + x + ", " + z + " is " + (h - floor) + " blocks deep");
                }
                if (Math.abs(desired - h) > MAX_CHANGE) {
                    return invalid(footprint, buffer, "grading " + x + ", " + z + " needs " + Math.abs(desired - h)
                            + " blocks of " + (desired < h ? "cut" : "fill"));
                }
                if (desired < h) {
                    for (int y = desired + 1; y <= h; y++) {
                        changes.add(new Change(x, y, z, Role.AIR));
                    }
                    changes.add(new Change(x, desired, z, Role.SURFACE)); // the newly exposed block becomes the surface
                } else if (desired > h || wet) {
                    // From solid ground (under the water, if wet) up to the top, so nothing floats.
                    for (int y = floor + 1; y <= desired; y++) {
                        Role role = y == desired ? Role.SURFACE : y >= desired - SOIL_DEPTH ? Role.FILL : Role.FOUNDATION;
                        changes.add(new Change(x, y, z, role));
                    }
                }
                finals.put(key(x, z), desired);
            }
        }
        return new Pad(footprint, buffer, true, "", target, List.copyOf(changes), Map.copyOf(finals));
    }

    /**
     * R8.21: the pad for a lot, by the usual grading if that will do, and if it refuses for the steepness of the ground (and only then) as
     * a terrace. Unmeasured ground or deep water is still refused, as no terrace helps with those.
     */
    public static Pad fit(Rect footprint, int buffer, HeightSource terrain, Integer fixedTarget) {
        Pad usual = compute(footprint, buffer, terrain, fixedTarget);
        if (usual.valid() || !usual.reason().contains(" blocks of ")) {
            return usual;
        }
        Pad terraced = terrace(footprint, terrain, fixedTarget);
        return terraced.valid() ? terraced : usual; // (the usual refusal is the one to tell)
    }

    /**
     * R8.21: a terraced pad: the footprint levelled to the median height (or {@code fixedTarget}) by up to {@link #TERRACE_CHANGE} blocks of
     * cut or fill, the ring one block outside it levelled too and made a retaining wall where it is filled, and the hillside one block
     * further out, where it stands above the pad, armoured with wall blocks up to its top. Water inside the footprint is filled to the
     * target as usual; water outside it is left alone. Nothing beyond that second ring is touched.
     */
    public static Pad terrace(Rect footprint, HeightSource terrain, Integer fixedTarget) {
        List<Integer> heights = new ArrayList<>();
        for (int x = footprint.x(); x <= footprint.maxX(); x++) {
            for (int z = footprint.z(); z <= footprint.maxZ(); z++) {
                int h = terrain.height(x, z);
                if (h == HeightSource.UNKNOWN) {
                    return invalid(footprint, 2, "the ground at " + x + ", " + z + " has not been measured");
                }
                heights.add(h);
            }
        }
        int[] sorted = heights.stream().mapToInt(Integer::intValue).sorted().toArray();
        int target = fixedTarget != null ? fixedTarget : sorted[(sorted.length - 1) / 2];
        int wetColumns = 0; // a lot that is mostly water is not built into a lake, terrace or not (as the plan keeps only lots a quarter wet at most)
        for (int x = footprint.x(); x <= footprint.maxX(); x++) {
            for (int z = footprint.z(); z <= footprint.maxZ(); z++) {
                wetColumns += terrain.water(x, z) ? 1 : 0;
            }
        }
        if (wetColumns * 4 > footprint.width() * footprint.depth()) {
            return invalid(footprint, 2, "more than a quarter of the ground is water");
        }

        List<Change> changes = new ArrayList<>();
        Map<Long, Integer> finals = new HashMap<>();
        Rect area = footprint.inflated(2);
        for (int x = area.x(); x <= area.maxX(); x++) {
            for (int z = area.z(); z <= area.maxZ(); z++) {
                int h = terrain.height(x, z);
                if (h == HeightSource.UNKNOWN) {
                    continue; // beyond what is loaded: the edge simply stops
                }
                int outward = Math.max(Math.max(footprint.x() - x, x - footprint.maxX()),
                        Math.max(Math.max(footprint.z() - z, z - footprint.maxZ()), 0));
                boolean wet = terrain.water(x, z);
                if (outward == 2) {
                    finals.put(key(x, z), h);
                    // the face of a hill standing above the pad: armoured with wall, so the cut is clean and does not slump into the building
                    if (!wet && h > target + 1 && h - target <= TERRACE_CHANGE + 2) {
                        for (int y = target + 1; y <= h; y++) {
                            changes.add(new Change(x, y, z, Role.WALL));
                        }
                    }
                    continue;
                }
                if (wet && outward == 1) {
                    finals.put(key(x, z), h);
                    continue; // the ring does not build out into water
                }
                int floor = wet ? terrain.floor(x, z) : h;
                if (wet && h - floor > MAX_WATER) {
                    return invalid(footprint, 2, "the water at " + x + ", " + z + " is " + (h - floor) + " blocks deep");
                }
                if (Math.abs(target - h) > TERRACE_CHANGE) {
                    return invalid(footprint, 2, "a terrace at " + x + ", " + z + " needs " + Math.abs(target - h) + " blocks of "
                            + (target < h ? "cut" : "fill"));
                }
                if (target < h) {
                    for (int y = target + 1; y <= h; y++) {
                        changes.add(new Change(x, y, z, Role.AIR));
                    }
                    changes.add(new Change(x, target, z, Role.SURFACE));
                } else if (target > h || wet) {
                    for (int y = floor + 1; y <= target; y++) {
                        Role role = y == target ? Role.SURFACE : outward == 1 ? Role.WALL : y >= target - SOIL_DEPTH ? Role.FILL : Role.FOUNDATION;
                        changes.add(new Change(x, y, z, role));
                    }
                }
                finals.put(key(x, z), target);
            }
        }
        return new Pad(footprint, 2, true, "", target, List.copyOf(changes), Map.copyOf(finals), true);
    }

    // ----- roads -----

    /** How a step of a road is built: level, a stair, or (inside a longer slope) a slab. */
    public enum StepKind {
        PATH, STAIR_UP, STAIR_DOWN, SLAB_UP, SLAB_DOWN
    }

    /** One step along a road: the height of its surface and how it is built. */
    public record Step(int index, int height, StepKind kind) {
    }

    /**
     * A road over the natural heights along it, as steps that rise or fall at most one block each. The profile follows the
     * ground and is clamped to one block of change per step. A single step up or down is a stair; in a longer slope the
     * steps alternate with slabs, which is gentler to walk and ride.
     */
    public static List<Step> roadProfile(int[] natural) {
        int n = natural.length;
        List<Step> steps = new ArrayList<>(n);
        if (n == 0) {
            return steps;
        }
        int[] p = Arrays.copyOf(natural, n);
        for (int i = 1; i < n; i++) {
            p[i] = Math.max(p[i - 1] - 1, Math.min(p[i - 1] + 1, p[i]));
        }
        for (int i = 0; i < n; i++) {
            int delta = i == 0 ? 0 : p[i] - p[i - 1];
            int next = i + 1 < n ? p[i + 1] - p[i] : 0;
            StepKind kind = StepKind.PATH;
            if (delta != 0) {
                boolean run = next == delta; // the slope carries on
                boolean after = i >= 2 && p[i - 1] - p[i - 2] == delta;
                if (run || after) {
                    kind = delta > 0 ? (after ? StepKind.SLAB_UP : StepKind.STAIR_UP)
                            : (after ? StepKind.SLAB_DOWN : StepKind.STAIR_DOWN);
                } else {
                    kind = delta > 0 ? StepKind.STAIR_UP : StepKind.STAIR_DOWN;
                }
            }
            steps.add(new Step(i, p[i], kind));
        }
        return steps;
    }
}
