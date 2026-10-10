package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * R9.5: the way along a village's streets (the plan's roads and its square, R8.3) from one place to another, as waypoints for a
 * {@link Journey}. The villager walks to the nearest street, along the streets (each straight run that stays on the street is
 * one waypoint, at most {@link #MAX_LEG} blocks long), and off the street to the goal. A short trip, one far from every street,
 * or one the streets would make much longer goes straight (just the goal). Pure.
 */
public final class RoadRoute {
    private RoadRoute() {
    }

    /** Trips shorter than this go straight. */
    public static final int SHORT = 16;
    /** A start or goal further than this from every street goes straight. */
    public static final int NEAR_ROAD = 24;
    /** The longest leg between two waypoints. */
    public static final int MAX_LEG = 24;

    /** The route through {@code plan}'s streets, ending with {@code to}; just {@code [to]} when the streets do not help. */
    public static List<Journey.Point> route(VillagePlan plan, Journey.Point from, Journey.Point to) {
        if (plan == null) {
            return List.of(to);
        }
        List<Rect> areas = new ArrayList<>();
        plan.roads().forEach(r -> areas.add(r.rect()));
        if (plan.square() != null) {
            areas.add(plan.square());
        }
        return route(areas, from, to);
    }

    /** The same, over any set of street rectangles. */
    static List<Journey.Point> route(Collection<Rect> areas, Journey.Point from, Journey.Point to) {
        if (from.horizontalDistance(to) < SHORT) {
            return List.of(to);
        }
        Set<Long> cells = new HashSet<>();
        for (Rect r : areas) {
            for (int x = r.x(); x <= r.maxX(); x++) {
                for (int z = r.z(); z <= r.maxZ(); z++) {
                    cells.add(key(x, z));
                }
            }
        }
        if (cells.isEmpty()) {
            return List.of(to);
        }
        long entry = nearest(cells, from);
        long exit = nearest(cells, to);
        double onRamp = distance(entry, from);
        double offRamp = distance(exit, to);
        if (onRamp > NEAR_ROAD || offRamp > NEAR_ROAD) {
            return List.of(to);
        }
        List<Long> path = search(cells, entry, exit);
        if (path.isEmpty()) {
            return List.of(to);
        }
        List<Long> turns = straighten(cells, path);
        double along = 0;
        for (int i = 1; i < turns.size(); i++) {
            along += Math.hypot(x(turns.get(i)) - x(turns.get(i - 1)), z(turns.get(i)) - z(turns.get(i - 1)));
        }
        double direct = from.horizontalDistance(to);
        if (onRamp + along + offRamp > 2 * direct + SHORT) {
            return List.of(to); // the streets would be a long way round
        }
        double total = onRamp + along + offRamp;
        List<Journey.Point> out = new ArrayList<>();
        double walked = onRamp;
        for (int i = 0; i < turns.size(); i++) {
            if (i > 0) {
                walked += Math.hypot(x(turns.get(i)) - x(turns.get(i - 1)), z(turns.get(i)) - z(turns.get(i - 1)));
            }
            int y = (int) Math.round(from.y() + (to.y() - from.y()) * (total == 0 ? 1 : walked / total));
            out.add(new Journey.Point(x(turns.get(i)), y, z(turns.get(i))));
        }
        if (out.get(0).horizontalDistance(from) < Journey.WAYPOINT_NEAR) {
            out.remove(0); // already on the street
        }
        if (!out.isEmpty() && out.get(out.size() - 1).horizontalDistance(to) < Journey.WAYPOINT_NEAR) {
            out.remove(out.size() - 1); // the goal is on the street
        }
        out.add(to);
        return out;
    }

    /** Breadth-first over the street cells (four neighbours); the cells from {@code start} to {@code end}, or empty if not joined. */
    private static List<Long> search(Set<Long> cells, long start, long end) {
        Map<Long, Long> parent = new HashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        parent.put(start, start);
        queue.add(start);
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            long cell = queue.poll();
            if (cell == end) {
                List<Long> path = new ArrayList<>();
                for (long c = end; c != start; c = parent.get(c)) {
                    path.add(c);
                }
                path.add(start);
                java.util.Collections.reverse(path);
                return path;
            }
            for (int[] s : steps) {
                long n = key(x(cell) + s[0], z(cell) + s[1]);
                if (cells.contains(n) && !parent.containsKey(n)) {
                    parent.put(n, cell);
                    queue.add(n);
                }
            }
        }
        return List.of();
    }

    /** The path as straight legs that stay on the street, each at most {@link #MAX_LEG} long: the first cell, the turns, the last. */
    private static List<Long> straighten(Set<Long> cells, List<Long> path) {
        List<Long> out = new ArrayList<>();
        int anchor = 0;
        out.add(path.get(0));
        for (int i = 1; i < path.size(); i++) {
            long a = path.get(anchor);
            long c = path.get(i);
            boolean tooLong = Math.hypot(x(c) - x(a), z(c) - z(a)) > MAX_LEG;
            if (tooLong || !onStreet(cells, a, c)) {
                anchor = i - 1;
                out.add(path.get(anchor));
            }
        }
        if (out.get(out.size() - 1) != path.get(path.size() - 1).longValue()) {
            out.add(path.get(path.size() - 1));
        }
        return out;
    }

    /** Whether the straight line between two cells stays on street cells (checked every half block). */
    private static boolean onStreet(Set<Long> cells, long a, long b) {
        double dx = x(b) - x(a);
        double dz = z(b) - z(a);
        int samples = (int) Math.ceil(Math.hypot(dx, dz) * 2);
        for (int i = 1; i < samples; i++) {
            double t = (double) i / samples;
            if (!cells.contains(key((int) Math.round(x(a) + dx * t), (int) Math.round(z(a) + dz * t)))) {
                return false;
            }
        }
        return true;
    }

    private static long nearest(Set<Long> cells, Journey.Point p) {
        long best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (long c : cells) {
            double d = distance(c, p);
            if (d < bestDistance || (d == bestDistance && c < best)) {
                bestDistance = d;
                best = c;
            }
        }
        return best;
    }

    private static double distance(long cell, Journey.Point p) {
        return Math.hypot(x(cell) - p.x(), z(cell) - p.z());
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static int x(long key) {
        return (int) (key >> 32);
    }

    private static int z(long key) {
        return (int) key;
    }
}
