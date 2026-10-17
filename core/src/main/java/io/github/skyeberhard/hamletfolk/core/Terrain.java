package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R8.27: the lie of the land round a village, for the dashboard's slope layer. A coarse grid of ground heights (one sample every
 * {@link #STEP} blocks) is turned into a slope class for each cell: the steepest rise or fall to a neighbouring sample. Pure: the Paper
 * layer samples the world (loaded ground only; unknown cells stay unknown and an older sample is kept where a newer one cannot be had).
 */
public final class Terrain {
    /** Blocks between samples. */
    public static final int STEP = 4;
    /** The change in height between neighbouring samples, at most, for each class: flat, gentle, steep (more is a cliff). A building pad changes by up to 8 in all. */
    static final int FLAT_MAX = 2;
    static final int GENTLE_MAX = 4;
    static final int STEEP_MAX = 8;
    /** The most cells either way (a village plan is a few hundred blocks across). */
    public static final int MAX_CELLS = 64;

    private Terrain() {
    }

    /**
     * Samples of ground height on a regular grid. A height of {@link HeightSource#UNKNOWN} is a cell nobody could measure; {@code wet} marks water.
     */
    public record Grid(int x0, int z0, int step, int cols, int rows, int[] heights, boolean[] wet) {
        public int height(int col, int row) {
            return heights[row * cols + col];
        }

        public boolean wet(int col, int row) {
            return wet[row * cols + col];
        }
    }

    /** Samples the ground over an area (at most {@link #MAX_CELLS} cells each way, centred if the area is bigger). */
    public static Grid sample(HeightSource source, Rect area, int step) {
        int cols = Math.max(1, Math.min(MAX_CELLS, area.width() / step));
        int rows = Math.max(1, Math.min(MAX_CELLS, area.depth() / step));
        int x0 = area.x() + (area.width() - cols * step) / 2;
        int z0 = area.z() + (area.depth() - rows * step) / 2;
        int[] heights = new int[cols * rows];
        boolean[] wet = new boolean[cols * rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = x0 + c * step + step / 2;
                int z = z0 + r * step + step / 2;
                int h = source.floor(x, z);
                heights[r * cols + c] = h;
                wet[r * cols + c] = h != HeightSource.UNKNOWN && source.water(x, z);
            }
        }
        return new Grid(x0, z0, step, cols, rows, heights, wet);
    }

    /** A grid with what is known of the newer sample, and what an older sample of the same grid knew where the newer cannot say. */
    public static Grid merge(Grid older, Grid newer) {
        if (older == null || older.x0() != newer.x0() || older.z0() != newer.z0() || older.step() != newer.step() || older.cols() != newer.cols()
                || older.rows() != newer.rows()) {
            return newer;
        }
        int[] heights = newer.heights().clone();
        boolean[] wet = newer.wet().clone();
        for (int i = 0; i < heights.length; i++) {
            if (heights[i] == HeightSource.UNKNOWN) {
                heights[i] = older.heights()[i];
                wet[i] = older.wet()[i];
            }
        }
        return new Grid(newer.x0(), newer.z0(), newer.step(), newer.cols(), newer.rows(), heights, wet);
    }

    /** '0' flat, '1' gentle, '2' steep, '3' cliff, '~' water, '.' not measured. */
    static char slopeClass(int maxChange) {
        return maxChange <= FLAT_MAX ? '0' : maxChange <= GENTLE_MAX ? '1' : maxChange <= STEEP_MAX ? '2' : '3';
    }

    /** The share of cells nobody has measured (0 to 1). */
    public static double unknownShare(Grid g) {
        int unknown = 0;
        for (int h : g.heights()) {
            if (h == HeightSource.UNKNOWN) {
                unknown++;
            }
        }
        return g.heights().length == 0 ? 1.0 : (double) unknown / g.heights().length;
    }

    /** The grid as plain data for a file ({@code heights} are plain numbers, {@code wet} a string of 0 and 1). */
    public static Map<String, Object> toFile(Grid g) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("x0", g.x0());
        out.put("z0", g.z0());
        out.put("step", g.step());
        out.put("cols", g.cols());
        out.put("rows", g.rows());
        List<Integer> heights = new ArrayList<>();
        for (int h : g.heights()) {
            heights.add(h);
        }
        out.put("heights", heights);
        StringBuilder wet = new StringBuilder();
        for (boolean w : g.wet()) {
            wet.append(w ? '1' : '0');
        }
        out.put("wet", wet.toString());
        return out;
    }

    /** Reads what {@link #toFile} wrote; null if it is not a grid of the right size (a damaged or hand-edited file). */
    public static Grid fromFile(Map<?, ?> m) {
        try {
            int cols = ((Number) m.get("cols")).intValue();
            int rows = ((Number) m.get("rows")).intValue();
            List<?> heights = (List<?>) m.get("heights");
            String wet = String.valueOf(m.get("wet"));
            if (cols < 1 || rows < 1 || cols > MAX_CELLS || rows > MAX_CELLS || heights.size() != cols * rows || wet.length() != cols * rows) {
                return null;
            }
            int[] h = new int[cols * rows];
            boolean[] w = new boolean[cols * rows];
            for (int i = 0; i < h.length; i++) {
                h[i] = ((Number) heights.get(i)).intValue();
                w[i] = wet.charAt(i) == '1';
            }
            return new Grid(((Number) m.get("x0")).intValue(), ((Number) m.get("z0")).intValue(), ((Number) m.get("step")).intValue(), cols, rows, h, w);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** One string a row: the slope class of each cell. */
    public static List<String> slopeRows(Grid g) {
        List<String> out = new ArrayList<>();
        for (int r = 0; r < g.rows(); r++) {
            StringBuilder row = new StringBuilder();
            for (int c = 0; c < g.cols(); c++) {
                int h = g.height(c, r);
                if (h == HeightSource.UNKNOWN) {
                    row.append('.');
                } else if (g.wet(c, r)) {
                    row.append('~');
                } else {
                    int worst = 0;
                    for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nc = c + d[0];
                        int nr = r + d[1];
                        if (nc < 0 || nr < 0 || nc >= g.cols() || nr >= g.rows()) {
                            continue;
                        }
                        int nh = g.height(nc, nr);
                        if (nh != HeightSource.UNKNOWN && !g.wet(nc, nr)) {
                            worst = Math.max(worst, Math.abs(nh - h));
                        }
                    }
                    row.append(slopeClass(worst));
                }
            }
            out.add(row.toString());
        }
        return out;
    }

    /** The grid as plain data for the dashboard: where it starts, the step, and the slope rows. */
    public static Map<String, Object> asData(Grid g) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("x", g.x0());
        out.put("z", g.z0());
        out.put("step", g.step());
        out.put("cols", g.cols());
        out.put("rows", g.rows());
        out.put("slope", slopeRows(g));
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int h : g.heights()) {
            if (h != HeightSource.UNKNOWN) {
                low = Math.min(low, h);
                high = Math.max(high, h);
            }
        }
        out.put("low", low == Integer.MAX_VALUE ? null : low);
        out.put("high", high == Integer.MIN_VALUE ? null : high);
        return out;
    }

    /** The area a village's plan covers, with a margin: every road, lot and the square, and what is registered. Null if it has no plan. */
    public static Rect areaOf(Settlement s, int margin) {
        VillagePlan plan = s.plan();
        if (plan == null) {
            return null;
        }
        int x0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        List<Rect> rects = new ArrayList<>();
        plan.roads().forEach(r -> rects.add(r.rect()));
        plan.lots().forEach(l -> rects.add(l.rect()));
        if (plan.square() != null) {
            rects.add(plan.square());
        }
        for (Rect r : rects) {
            x0 = Math.min(x0, r.x());
            z0 = Math.min(z0, r.z());
            x1 = Math.max(x1, r.x() + r.width());
            z1 = Math.max(z1, r.z() + r.depth());
        }
        if (x0 == Integer.MAX_VALUE) {
            return null;
        }
        return new Rect(x0 - margin, z0 - margin, x1 - x0 + 2 * margin, z1 - z0 + 2 * margin);
    }
}
