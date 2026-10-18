package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * R8.31: the shape of a vanilla building the plugin found. A found building is one point (its bed, its job site); this reads the ground round
 * it, column by column, for the connected columns whose top block is something people built (planks, cobblestone, stairs, a roof) and gives their
 * bounding rectangle, so the dashboard can draw the building and not just a dot. The Paper layer says what a column is; nothing here touches the world.
 */
public final class Footprints {
    /** How far from the marker the search goes, so two buildings that touch are not read as one (a house is at most about this wide). */
    public static final int REACH = 11;
    /** The most a footprint is wider or deeper than. */
    public static final int MAX_SIDE = 2 * REACH + 1;
    /** Fewest columns that make a building (a lone fence post or a lamp is not one). */
    public static final int MIN_COLUMNS = 6;

    private Footprints() {
    }

    /** What the world says about a column. */
    public interface Columns {
        /** True if the column's chunk is loaded, so {@link #built} can be answered. */
        boolean loaded(int x, int z);

        /** True if the top block of the column is something built (not terrain, a plant, water or air). */
        boolean built(int x, int z);
    }

    /**
     * The bounding rectangle of the built columns connected (including diagonally) to the marker's, within {@link #REACH} of it, or null if the marker's
     * ground is not loaded, the marker is not on or beside anything built, or what is there is too small to be a building. The marker may stand on
     * a column that is not built itself (a bed in a doorway, a lectern): the nearest built column within two blocks starts the search.
     */
    public static Rect of(Columns world, int markerX, int markerZ) {
        return of(world, markerX, markerZ, 1);
    }

    /**
     * As {@link #of(Columns, int, int)}, joining columns up to {@code link} blocks apart (2 reads a field with a ditch of water through it as one field).
     */
    public static Rect of(Columns world, int markerX, int markerZ, int link) {
        if (!world.loaded(markerX, markerZ)) {
            return null;
        }
        int[] start = null;
        for (int r = 0; r <= 2 && start == null; r++) {
            for (int dx = -r; dx <= r && start == null; dx++) {
                for (int dz = -r; dz <= r && start == null; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r && world.loaded(markerX + dx, markerZ + dz) && world.built(markerX + dx, markerZ + dz)) {
                        start = new int[] {markerX + dx, markerZ + dz};
                    }
                }
            }
        }
        if (start == null) {
            return null;
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(key(start[0], start[1]));
        int minX = start[0];
        int maxX = start[0];
        int minZ = start[1];
        int maxZ = start[1];
        int columns = 0;
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            columns++;
            minX = Math.min(minX, at[0]);
            maxX = Math.max(maxX, at[0]);
            minZ = Math.min(minZ, at[1]);
            maxZ = Math.max(maxZ, at[1]);
            for (int dx = -link; dx <= link; dx++) {
                for (int dz = -link; dz <= link; dz++) {
                    int x = at[0] + dx;
                    int z = at[1] + dz;
                    if ((dx == 0 && dz == 0) || Math.abs(x - markerX) > REACH || Math.abs(z - markerZ) > REACH || !seen.add(key(x, z))) {
                        continue;
                    }
                    if (world.loaded(x, z) && world.built(x, z)) {
                        queue.add(new int[] {x, z});
                    }
                }
            }
        }
        return columns < MIN_COLUMNS ? null : new Rect(minX, minZ, maxX - minX + 1, maxZ - minZ + 1);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
