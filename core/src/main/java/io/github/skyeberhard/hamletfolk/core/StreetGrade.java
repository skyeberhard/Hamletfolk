package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * R4.23: grading a village's streets, as a plan of blocks. A street is a rectangle with a long axis; across its width
 * every slice is given one height (the median of its columns), and along the axis the heights are smoothed so that a
 * hole or dip is filled to the rim, a short bump is cut down, and no step is more than one block. Water up to
 * {@link #MAX_BRIDGE} slices wide, with land at both ends, is crossed by a bridge at the water's surface. A slice that
 * cannot be graded (unmeasured ground, a change of more than {@link TerrainPad#MAX_CHANGE} blocks, water too wide to
 * cross) is left exactly as it is. Streets are done in order and a column already given a height by an earlier street
 * keeps it, so a crossing has one height. Pure: heights in, blocks out; the Paper layer maps roles to the biome's
 * blocks and never touches a column that has something built on it.
 */
public final class StreetGrade {
    /**
     * Longest dip (or bump) along a street that is filled (or cut) outright, in blocks. A longer one is only partly filled:
     * the street ramps down into it a block at a time.
     */
    public static final int MAX_DIP = 8;
    /** Widest water, measured along the street, that a bridge crosses. */
    public static final int MAX_BRIDGE = 6;

    private StreetGrade() {
    }

    /** What a block is: the walking surface (path), soil under it, a block cut away above it, or a bridge deck. */
    public enum Role {
        SURFACE, FILL, AIR, BRIDGE
    }

    /** One block to change. */
    public record Change(int x, int y, int z, Role role) {
    }

    /**
     * R4.33: a piece of street and the way it runs. A piece clipped to a small window of the ground (round a building) can be
     * wider than it is long whichever way its street goes, so the direction is carried, not guessed from the shape.
     */
    public record Street(Rect rect, boolean alongX) {
    }

    /** As {@link #computeStreets}, guessing each street's direction from its shape (its longer side): for whole streets only. */
    public static List<Change> compute(List<Rect> streets, HeightSource terrain) {
        return computeStreets(streets.stream().map(r -> new Street(r, r.width() >= r.depth())).toList(), terrain);
    }

    /** The changes that grade these streets, in the order given; each column's changes run from the bottom up. */
    public static List<Change> computeStreets(List<Street> streets, HeightSource terrain) {
        List<Change> out = new ArrayList<>();
        Map<Long, Integer> done = new HashMap<>();
        for (Street street : streets) {
            grade(street.rect(), street.alongX(), terrain, done, out);
        }
        return out;
    }

    private static void grade(Rect street, boolean alongX, HeightSource terrain, Map<Long, Integer> done, List<Change> out) {
        int length = alongX ? street.width() : street.depth();
        int across = alongX ? street.depth() : street.width();
        int[] height = new int[length];
        boolean[] water = new boolean[length];
        boolean[] measured = new boolean[length];
        boolean[] fixed = new boolean[length]; // a slice crossing a street already graded keeps that street's height
        for (int i = 0; i < length; i++) {
            // Columns the source cannot measure (unloaded, or with something built on them) are left out of the slice.
            int[] column = new int[across];
            int known = 0;
            int wet = 0;
            for (int j = 0; j < across; j++) {
                int x = alongX ? street.x() + i : street.x() + j;
                int z = alongX ? street.z() + j : street.z() + i;
                Integer already = done.get(TerrainPad.key(x, z));
                int h = already != null ? already : terrain.height(x, z);
                if (h == HeightSource.UNKNOWN) {
                    continue;
                }
                fixed[i] |= already != null;
                column[known++] = h;
                if (already == null && terrain.water(x, z)) {
                    wet++;
                }
            }
            measured[i] = known > 0;
            if (known > 0) {
                Arrays.sort(column, 0, known);
                height[i] = column[known / 2];
                water[i] = wet * 2 > known;
            }
        }
        // Water that is too wide to cross, or has no land at one end, is not built across: those slices stay as they are.
        boolean[] usable = measured.clone();
        for (int i = 0; i < length; ) {
            if (!(usable[i] && water[i])) {
                i++;
                continue;
            }
            int end = i;
            while (end + 1 < length && usable[end + 1] && water[end + 1]) {
                end++;
            }
            boolean anchored = i > 0 && end < length - 1 && usable[i - 1] && usable[end + 1];
            if (end - i + 1 > MAX_BRIDGE || !anchored) {
                Arrays.fill(usable, i, end + 1, false);
            }
            i = end + 1;
        }
        int[] target = height.clone();
        for (int start = 0; start < length; ) {
            if (!usable[start]) {
                start++;
                continue;
            }
            int end = start;
            while (end + 1 < length && usable[end + 1]) {
                end++;
            }
            boolean[] lock = new boolean[length];
            for (int i = start; i <= end; i++) {
                lock[i] = water[i] || fixed[i];
            }
            smooth(height, water, lock, target, start, end);
            start = end + 1;
        }
        for (int i = 0; i < length; i++) {
            if (!usable[i]) {
                continue;
            }
            // A slice whose ground is too far from its height is left alone, rather than cut or filled for ever.
            boolean tooFar = false;
            for (int j = 0; j < across && !tooFar; j++) {
                int x = alongX ? street.x() + i : street.x() + j;
                int z = alongX ? street.z() + j : street.z() + i;
                int h = terrain.height(x, z);
                tooFar = h != HeightSource.UNKNOWN && done.get(TerrainPad.key(x, z)) == null && !water[i]
                        && Math.abs(target[i] - h) > TerrainPad.MAX_CHANGE;
            }
            if (tooFar) {
                continue;
            }
            for (int j = 0; j < across; j++) {
                int x = alongX ? street.x() + i : street.x() + j;
                int z = alongX ? street.z() + j : street.z() + i;
                if (done.containsKey(TerrainPad.key(x, z)) || terrain.height(x, z) == HeightSource.UNKNOWN) {
                    continue;
                }
                column(x, z, target[i], water[i] && terrain.water(x, z), terrain, done, out);
            }
        }
    }

    /**
     * Smooths the slices {@code lo..hi} (all measured): dips of up to {@link #MAX_DIP} slices are filled to the lower rim,
     * bumps of that length cut to the higher base, and steps limited to one block. Bridge slices keep the water's height,
     * and so do slices that cross a street already graded.
     */
    private static void smooth(int[] height, boolean[] water, boolean[] lock, int[] target, int lo, int hi) {
        int[] closed = height.clone();
        for (int i = lo; i <= hi; i++) {
            if (lock[i]) {
                continue;
            }
            int left = height[i];
            int right = height[i];
            for (int k = 1; k <= MAX_DIP; k++) {
                if (i - k >= lo && !water[i - k]) {
                    left = Math.max(left, height[i - k]);
                }
                if (i + k <= hi && !water[i + k]) {
                    right = Math.max(right, height[i + k]);
                }
            }
            closed[i] = Math.min(left, right);
        }
        int[] opened = closed.clone();
        for (int i = lo; i <= hi; i++) {
            if (lock[i]) {
                continue;
            }
            int left = closed[i];
            int right = closed[i];
            for (int k = 1; k <= MAX_DIP; k++) {
                if (i - k >= lo && !water[i - k]) {
                    left = Math.min(left, closed[i - k]);
                }
                if (i + k <= hi && !water[i + k]) {
                    right = Math.min(right, closed[i + k]);
                }
            }
            opened[i] = Math.max(left, right);
        }
        int[] t = opened;
        for (int pass = 0; pass < 3; pass++) {
            for (int i = lo + 1; i <= hi; i++) {
                if (!lock[i]) {
                    t[i] = Math.max(t[i - 1] - 1, Math.min(t[i - 1] + 1, t[i]));
                }
            }
            for (int i = hi - 1; i >= lo; i--) {
                if (!lock[i]) {
                    t[i] = Math.max(t[i + 1] - 1, Math.min(t[i + 1] + 1, t[i]));
                }
            }
        }
        System.arraycopy(t, lo, target, lo, hi - lo + 1);
    }

    /** The changes that bring one column to the street's height: soil up to it, rock cut down to it, or a bridge deck. */
    private static void column(int x, int z, int level, boolean bridge, HeightSource terrain, Map<Long, Integer> done,
            List<Change> out) {
        if (bridge) {
            out.add(new Change(x, level, z, Role.BRIDGE));
            done.put(TerrainPad.key(x, z), level);
            return;
        }
        int ground = terrain.height(x, z);
        if (terrain.water(x, z)) {
            ground = terrain.floor(x, z); // a puddle or pond: filled from its bed
            if (terrain.height(x, z) - ground > TerrainPad.MAX_WATER) {
                return;
            }
        }
        for (int y = ground + 1; y < level; y++) {
            out.add(new Change(x, y, z, Role.FILL));
        }
        for (int y = level + 1; y <= ground; y++) {
            out.add(new Change(x, y, z, Role.AIR));
        }
        out.add(new Change(x, level, z, Role.SURFACE));
        done.put(TerrainPad.key(x, z), level);
    }
}
