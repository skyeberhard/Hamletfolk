package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * R4.23, R8.22: grading a village's streets, as a plan of blocks. A street is a rectangle with a long axis; across its width
 * every slice is given one height (the median of its columns), and along the axis the heights are smoothed so that a
 * hole or dip is filled to the rim, a short bump is cut down, and no step is more than one block. Water up to
 * {@link #MAX_BRIDGE} slices wide, with land at both ends, is crossed by a bridge at the water's surface. A slice that
 * cannot be graded (unmeasured ground, a change of more than {@link TerrainPad#MAX_CHANGE} blocks, water too wide to
 * cross) is left exactly as it is. Streets are done in order and a column already given a height by an earlier street
 * keeps it, so a crossing has one height. R8.22: a gap too big to level (a gorge, a ravine, water too wide for a bridge of {@link #MAX_BRIDGE}) is
 * crossed by a deck up to {@link #MAX_SPAN} slices long with rails along it and a lamp at each end, when both banks are within reach, and a
 * street that runs along a drop of more than {@link #FENCE_DROP} blocks is fenced on that side. Pure: heights in, blocks out; the Paper layer maps roles to the biome's
 * blocks and never touches a column that has something built on it.
 */
public final class StreetGrade {
    /**
     * Longest dip (or bump) along a street that is filled (or cut) outright, in blocks. A longer one is only partly filled:
     * the street ramps down into it a block at a time.
     */
    public static final int MAX_DIP = 8;
    /** Widest gap (water or a drop), measured along the street, that a bridge crosses (R8.22: it was 6 blocks of water). */
    public static final int MAX_SPAN = 16;
    /** Widest water, measured along the street, that a bridge crosses. */
    public static final int MAX_BRIDGE = MAX_SPAN;
    /** How far below both banks a stretch of ground must be to count as a gap rather than a dip. */
    public static final int GAP_DEPTH = 4;
    /** A street along a drop of more than this many blocks is fenced on that side. */
    public static final int FENCE_DROP = 3;

    private StreetGrade() {
    }

    /**
     * What a block is: the walking surface (path), soil under it, a block cut away above it, or a bridge deck. R8.22: RAIL is a fence post
     * on the block at the change's height, LAMP a fence post there with a torch on the block above it.
     */
    public enum Role {
        SURFACE, FILL, AIR, BRIDGE, RAIL, LAMP,
        /** R8.25: a stair on the street's surface where it rises or falls a block, its {@code facing} the way it climbs. */
        STAIR
    }

    /** One block to change. {@code facing} (0 north, 1 east, 2 south, 3 west) is only meaningful for a {@link Role#STAIR}: the way it climbs; -1 otherwise. */
    public record Change(int x, int y, int z, Role role, int facing) {
        public Change(int x, int y, int z, Role role) {
            this(x, y, z, role, -1);
        }
    }

    /**
     * R4.33: a piece of street and the way it runs. A piece clipped to a small window of the ground (round a building) can be
     * wider than it is long whichever way its street goes, so the direction is carried, not guessed from the shape.
     */
    public record Street(Rect rect, boolean alongX, Rect whole) {
        /** A piece that is the whole street. */
        public Street(Rect rect, boolean alongX) {
            this(rect, alongX, rect);
        }
    }

    /** A rail or lamp post that a street asks for; kept until every street is graded, as what stands beside it is not known before. */
    private record Post(int x, int y, int z, Role role, int street) {
    }

    /** As {@link #computeStreets}, guessing each street's direction from its shape (its longer side): for whole streets only. */
    public static List<Change> compute(List<Rect> streets, HeightSource terrain) {
        return computeStreets(streets.stream().map(r -> new Street(r, r.width() >= r.depth())).toList(), terrain);
    }

    /**
     * R8.26: splits a grading into batches of about {@code size} changes, each ending where a column ends (a column's changes run from the
     * bottom up and must be placed together), so a big plan can be laid a little at a time. Order is kept; an empty list gives none.
     */
    public static List<List<Change>> batches(List<Change> changes, int size) {
        List<List<Change>> out = new ArrayList<>();
        List<Change> batch = new ArrayList<>();
        for (int i = 0; i < changes.size(); i++) {
            Change c = changes.get(i);
            batch.add(c);
            boolean columnEnds = i + 1 == changes.size() || changes.get(i + 1).x() != c.x() || changes.get(i + 1).z() != c.z();
            if (batch.size() >= size && columnEnds) {
                out.add(batch);
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) {
            out.add(batch);
        }
        return out;
    }

    /** R8.26: how many changes of each role a grading holds (for the history line of a street check). */
    public static Map<Role, Integer> summarize(List<Change> changes) {
        Map<Role, Integer> out = new java.util.EnumMap<>(Role.class);
        for (Change c : changes) {
            out.merge(c.role(), 1, Integer::sum);
        }
        return out;
    }

    /** The changes that grade these streets, in the order given; each column's changes run from the bottom up. */
    public static List<Change> computeStreets(List<Street> streets, HeightSource terrain) {
        List<Change> out = new ArrayList<>();
        Map<Long, Integer> done = new HashMap<>();
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < streets.size(); i++) {
            grade(streets.get(i), i, terrain, done, out, posts);
        }
        // R8.22: a post only where this street graded the column at that height, and never in or beside another street (a junction,
        // a crossing deck, a door's walkway: a post there would block it for good, as nothing takes one away)
        for (Post post : posts) {
            Integer level = done.get(TerrainPad.key(post.x(), post.z()));
            if (level == null || level != post.y() - 1) {
                continue;
            }
            boolean clear = true;
            for (int i = 0; i < streets.size() && clear; i++) {
                clear = i == post.street() || !streets.get(i).rect().inflated(1).contains(post.x(), post.z());
            }
            if (clear) {
                out.add(new Change(post.x(), post.y(), post.z(), post.role()));
            }
        }
        return out;
    }

    /** Whether the column beside this street's edge is outside the whole street: an edge made by clipping a piece is not an edge. */
    private static boolean realEdge(Rect whole, int nx, int nz) {
        return !whole.contains(nx, nz);
    }

    private static void grade(Street spec, int index, HeightSource terrain, Map<Long, Integer> done, List<Change> out, List<Post> posts) {
        Rect street = spec.rect();
        boolean alongX = spec.alongX();
        Rect whole = spec.whole();
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
        boolean[] span = new boolean[length]; // R8.22: slices that are a deck over a drop (water is `water`)
        markGaps(height, water, measured, span);
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
        int[] stair = stairs(target, usable, water, alongX); // R8.25: where the street climbs or falls a block, a stair makes it a step
        boolean[] graded = new boolean[length];
        boolean[] deck = new boolean[length]; // a slice with a bridge deck in it
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
                tooFar = h != HeightSource.UNKNOWN && done.get(TerrainPad.key(x, z)) == null && !water[i] && !span[i]
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
                boolean bridge = span[i] || (water[i] && terrain.water(x, z));
                column(x, z, target[i], bridge, terrain, done, out, bridge ? -1 : stair[i]);
                deck[i] |= bridge;
                graded[i] = true;
                if (bridge && across >= 3 && (j == 0 || j == across - 1)
                        && realEdge(whole, alongX ? x : (j == 0 ? x - 1 : x + 1), alongX ? (j == 0 ? z - 1 : z + 1) : z)) {
                    posts.add(new Post(x, target[i] + 1, z, Role.RAIL, index)); // a rail along each side of the deck
                }
            }
        }
        for (int i = 0; i < length; i++) {
            if (!graded[i]) {
                continue;
            }
            for (int j = 0; j < across; j++) {
                if (j != 0 && j != across - 1 || across < 3) {
                    continue;
                }
                int x = alongX ? street.x() + i : street.x() + j;
                int z = alongX ? street.z() + j : street.z() + i;
                int nx = alongX ? x : (j == 0 ? x - 1 : x + 1);
                int nz = alongX ? (j == 0 ? z - 1 : z + 1) : z;
                if (!realEdge(whole, nx, nz)) {
                    continue;
                }
                boolean nearEnd = (i > 0 && deck[i - 1] && !deck[i]) || (i < length - 1 && deck[i + 1] && !deck[i]);
                if (nearEnd) {
                    posts.add(new Post(x, target[i] + 1, z, Role.LAMP, index)); // a lamp on each bank where the deck begins
                } else if (!deck[i]) {
                    // a street along a drop of more than FENCE_DROP blocks is fenced on that side
                    int beside = terrain.height(nx, nz);
                    if (beside != HeightSource.UNKNOWN && target[i] - beside > FENCE_DROP && !done.containsKey(TerrainPad.key(nx, nz))) {
                        posts.add(new Post(x, target[i] + 1, z, Role.RAIL, index));
                    }
                }
            }
        }
    }

    /**
     * R8.22: finds the gaps: a bank, a drop of {@link #GAP_DEPTH} or more, and within {@link #MAX_SPAN} slices ground that is back up at the
     * bank's height, which a street cannot level (the stretch is longer than a dip is filled, {@link #MAX_DIP}, or deeper than a slice is
     * cut or filled, {@link TerrainPad#MAX_CHANGE}) and whose banks are no more than a block of height apart for each slice between them.
     * Its slices are marked {@code span} and {@code water} (a bridge: its height does not move) and given the deck's height, climbing
     * evenly from one bank to the other. A gap is looked for from each end, so a far bank lower than the near one is found too.
     */
    private static void markGaps(int[] height, boolean[] water, boolean[] measured, boolean[] span) {
        int length = height.length;
        int[] deck = new int[length];
        boolean[] found = new boolean[length];
        scanGaps(height, water, measured, found, deck);
        int[] rh = new int[length];
        boolean[] rw = new boolean[length];
        boolean[] rm = new boolean[length];
        for (int i = 0; i < length; i++) {
            rh[i] = height[length - 1 - i];
            rw[i] = water[length - 1 - i];
            rm[i] = measured[length - 1 - i];
        }
        boolean[] rfound = new boolean[length];
        int[] rdeck = new int[length];
        scanGaps(rh, rw, rm, rfound, rdeck);
        for (int i = 0; i < length; i++) {
            if (rfound[length - 1 - i] && !found[i]) {
                found[i] = true;
                deck[i] = rdeck[length - 1 - i];
            }
        }
        // a deck made of two scans that disagree is not one that climbs evenly: such a stretch is left as it was
        for (int a = 0; a < length; ) {
            if (!found[a]) {
                a++;
                continue;
            }
            int b = a;
            while (b + 1 < length && found[b + 1]) {
                b++;
            }
            boolean even = a > 0 && b < length - 1;
            int previous = a > 0 ? height[a - 1] : 0;
            for (int k = a; k <= b && even; k++) {
                even = Math.abs(deck[k] - previous) <= 1;
                previous = deck[k];
            }
            even = even && Math.abs(height[b + 1 < length ? b + 1 : b] - previous) <= 1;
            for (int k = a; k <= b; k++) {
                if (even) {
                    span[k] = true;
                    water[k] = true;
                    height[k] = deck[k];
                }
            }
            a = b + 1;
        }
    }

    /** One direction of {@link #markGaps}: marks the slices of each gap found, and the height of the deck over them. */
    private static void scanGaps(int[] height, boolean[] water, boolean[] measured, boolean[] found, int[] deck) {
        int length = height.length;
        for (int a = 1; a < length; a++) {
            if (!measured[a - 1] || !measured[a] || water[a - 1] || water[a] || height[a] > height[a - 1] - GAP_DEPTH) {
                continue; // not the edge of a drop
            }
            int bank = height[a - 1];
            int end = -1; // the first slice that is back up at the bank's height
            for (int e = a + 1; e < length && e <= a + MAX_SPAN; e++) {
                if (!measured[e]) {
                    break;
                }
                if (water[e]) {
                    continue; // a river at the bottom of a ravine is under the deck
                }
                if (height[e] >= bank - (GAP_DEPTH - 1)) {
                    end = e;
                    break;
                }
            }
            if (end < 0) {
                continue;
            }
            int n = end - a;
            int rim = Math.min(bank, height[end]);
            int deepest = 0;
            int low = 0; // the slices well below both banks: a ledge or a shallow hollow is not a gap
            for (int k = a; k < end; k++) {
                deepest = Math.max(deepest, rim - height[k]);
                if (rim - height[k] >= GAP_DEPTH) {
                    low++;
                }
            }
            if ((n > MAX_DIP || deepest > TerrainPad.MAX_CHANGE) && low * 2 >= n && Math.abs(bank - height[end]) <= n + 1) {
                for (int k = a; k < end; k++) {
                    found[k] = true;
                    deck[k] = bank + (int) Math.round((height[end] - bank) * (k - a + 1) / (double) (n + 1));
                }
                a = end; // (the for loop moves on from here)
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
            List<Change> out, int stairFacing) {
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
        out.add(stairFacing >= 0 ? new Change(x, level, z, Role.STAIR, stairFacing) : new Change(x, level, z, Role.SURFACE));
        done.put(TerrainPad.key(x, z), level);
    }

    /**
     * R8.25: for each slice, the way a stair on it climbs (0 north, 1 east, 2 south, 3 west), or -1 for a plain surface. Where the street
     * rises or falls a block between two slices, the higher one gets a stair facing up the slope: its low step is half a block above the
     * lower slice and its back is level with the higher surface, so a slope walks as a staircase instead of needing a jump. A slice that is
     * higher than both its neighbours (a crest) cannot climb two ways and stays plain; so do bridge decks and slices that are not graded.
     */
    static int[] stairs(int[] target, boolean[] usable, boolean[] water, boolean alongX) {
        int length = target.length;
        int[] stair = new int[length];
        Arrays.fill(stair, -1);
        boolean[] twoWays = new boolean[length];
        int forward = alongX ? 1 : 2; // the way of rising index
        int backward = alongX ? 3 : 0;
        for (int i = 1; i < length; i++) {
            if (!usable[i - 1] || !usable[i] || water[i - 1] || water[i] || target[i] == target[i - 1]) {
                continue;
            }
            int higher = target[i] > target[i - 1] ? i : i - 1;
            int facing = higher == i ? forward : backward;
            if (stair[higher] >= 0 && stair[higher] != facing) {
                twoWays[higher] = true;
            }
            stair[higher] = facing;
        }
        for (int i = 0; i < length; i++) {
            if (twoWays[i]) {
                stair[i] = -1;
            }
        }
        return stair;
    }
}
