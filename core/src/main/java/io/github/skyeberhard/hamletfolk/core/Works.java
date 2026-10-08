package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * R5.6: where a village's defensive works go, worked out from its plan alone. The lights are a post with a torch on top
 * every {@link #LIGHT_SPACING} blocks along the outer row of every street and at the corners of the square, so the streets
 * and the yards beside them are lit and monsters do not spawn there. The palisade is a one-block fence in a ring round
 * everything the plan covers ({@link #FENCE_MARGIN} blocks out), with a gap where a street leaves it. Pure geometry:
 * the Paper layer finds the ground under each spot, pays for the fence post from the ledger and places it. A spot is
 * a column; what stands in it is decided there.
 */
public final class Works {
    /** Blocks between two street lights along a street. */
    public static final int LIGHT_SPACING = 6;
    /** Blocks between the outermost lot, street or square and the fence. */
    public static final int FENCE_MARGIN = 4;
    private Works() {
    }

    /** One column a work stands in. */
    public record Spot(int x, int z) {
    }

    /** True for the building types that are works on the plan rather than a building on a lot. */
    public static boolean isWorks(BuildingType type) {
        return type.isWorks();
    }

    /** The columns a kind of work stands in for the plan as it is now; empty for a type that is not a work, or no plan. */
    public static List<Spot> spots(BuildingType type, VillagePlan plan) {
        return plan == null ? List.of() : spots(type, plan, plan.stage());
    }

    /**
     * R5.8: the columns a kind of work stands in for a stage of the plan: only the lots and streets laid out by then. The
     * lights of a later stage include those of the earlier ones (a light already standing is just found standing).
     */
    public static List<Spot> spots(BuildingType type, VillagePlan plan, int stage) {
        if (plan == null) {
            return List.of();
        }
        return switch (type) {
            case STREET_LIGHTS -> lights(plan, stage);
            case PALISADE -> palisade(plan, stage);
            default -> List.of();
        };
    }

    /**
     * R5.8: the posts of the ring for stage {@code stage - 1} that are not on the ring for {@code stage}: what is taken down
     * once the ring has moved out.
     */
    public static List<Spot> oldRing(VillagePlan plan, int stage) {
        return oldRing(plan, stage - 1, stage);
    }

    /**
     * R5.8: the posts of the ring laid out for stage {@code from} that neither the ring for stage {@code to} nor a light of
     * that stage stands on: what is taken down once the ring has moved out. Empty unless {@code from} is an earlier stage.
     */
    public static List<Spot> oldRing(VillagePlan plan, int from, int to) {
        if (plan == null || from < 1 || from >= to) {
            return List.of();
        }
        Set<Spot> kept = new LinkedHashSet<>(palisade(plan, to));
        kept.addAll(lights(plan, to));
        List<Spot> out = new ArrayList<>();
        for (Spot spot : palisade(plan, from)) {
            if (!kept.contains(spot)) {
                out.add(spot);
            }
        }
        return out;
    }

    /**
     * R5.8: what a work for a stage costs when the village already has one for an earlier stage: only the posts that are
     * new (a light already standing, or a ring post on both lines, is not paid for again).
     */
    public static Map<ResourceType, Integer> price(BuildingType type, VillagePlan plan, int from, int to) {
        if (plan == null || from < 1 || from >= to) {
            return price(type, plan);
        }
        Set<Spot> before = new LinkedHashSet<>(spots(type, plan, from));
        int fresh = 0;
        for (Spot spot : spots(type, plan, to)) {
            if (!before.contains(spot)) {
                fresh++;
            }
        }
        return priceOf(new EnumMap<>(ResourceType.class), fresh);
    }

    /** A light every {@link #LIGHT_SPACING} blocks along the outer row of each street, and outside each corner of the square. */
    public static List<Spot> lights(VillagePlan plan) {
        return lights(plan, plan.stage());
    }

    /** As {@link #lights(VillagePlan)}, for the streets laid out by a stage of the plan. */
    public static List<Spot> lights(VillagePlan plan, int stage) {
        Set<Spot> out = new LinkedHashSet<>();
        for (VillagePlan.Road road : plan.roads()) {
            if (road.stage() > stage) {
                continue;
            }
            Rect r = road.rect();
            boolean alongX = r.width() >= r.depth();
            int length = alongX ? r.width() : r.depth();
            for (int i = LIGHT_SPACING / 2; i < length; i += LIGHT_SPACING) {
                out.add(alongX ? new Spot(r.x() + i, r.z()) : new Spot(r.x(), r.z() + i));
            }
        }
        Rect square = plan.square();
        if (square != null) {
            // Just outside its corners: the town centre fills the square, and a post inside would be in its way.
            out.add(new Spot(square.x() - 1, square.z() - 1));
            out.add(new Spot(square.maxX() + 1, square.z() - 1));
            out.add(new Spot(square.x() - 1, square.maxZ() + 1));
            out.add(new Spot(square.maxX() + 1, square.maxZ() + 1));
        }
        if (square != null) {
            out.removeIf(spot -> square.contains(spot.x(), spot.z())); // a street through the square: its building fills it
        }
        return new ArrayList<>(out);
    }

    /** The bounds everything in the plan fits in, or empty for an empty plan. */
    static Optional<Rect> extent(VillagePlan plan) {
        return extent(plan, plan.stage());
    }

    /** The bounds of what the plan had laid out by a stage. */
    static Optional<Rect> extent(VillagePlan plan, int stage) {
        List<Rect> rects = new ArrayList<>();
        plan.lots().stream().filter(l -> l.stage() <= stage).forEach(l -> rects.add(l.rect()));
        plan.roads().stream().filter(r -> r.stage() <= stage).forEach(r -> rects.add(r.rect()));
        if (plan.square() != null) {
            rects.add(plan.square());
        }
        if (rects.isEmpty()) {
            return Optional.empty();
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Rect r : rects) {
            minX = Math.min(minX, r.x());
            minZ = Math.min(minZ, r.z());
            maxX = Math.max(maxX, r.maxX());
            maxZ = Math.max(maxZ, r.maxZ());
        }
        return Optional.of(new Rect(minX, minZ, maxX - minX + 1, maxZ - minZ + 1));
    }

    /** The fence ring round the plan, clockwise from its north-west corner, without the gaps where streets leave it. */
    public static List<Spot> palisade(VillagePlan plan) {
        return palisade(plan, plan.stage());
    }

    /** As {@link #palisade(VillagePlan)}, round what the plan had laid out by a stage, with gates for the streets of that stage. */
    public static List<Spot> palisade(VillagePlan plan, int stage) {
        Optional<Rect> extent = extent(plan, stage);
        if (extent.isEmpty()) {
            return List.of();
        }
        Rect ring = extent.get().inflated(FENCE_MARGIN);
        List<Spot> cells = new ArrayList<>();
        for (int x = ring.x(); x <= ring.maxX(); x++) {
            cells.add(new Spot(x, ring.z()));
        }
        for (int z = ring.z() + 1; z <= ring.maxZ(); z++) {
            cells.add(new Spot(ring.maxX(), z));
        }
        for (int x = ring.maxX() - 1; x >= ring.x(); x--) {
            cells.add(new Spot(x, ring.maxZ()));
        }
        for (int z = ring.maxZ() - 1; z > ring.z(); z--) {
            cells.add(new Spot(ring.x(), z));
        }
        List<Spot> out = new ArrayList<>();
        for (Spot cell : cells) {
            if (!gate(plan, stage, ring, cell)) {
                out.add(cell);
            }
        }
        return out;
    }

    /** True if a street leaves the ring through this cell: it lies on the line of a street, at either end of it. */
    private static boolean gate(VillagePlan plan, int stage, Rect ring, Spot cell) {
        for (VillagePlan.Road road : plan.roads()) {
            if (road.stage() > stage) {
                continue;
            }
            Rect r = road.rect();
            boolean alongX = r.width() >= r.depth();
            if (alongX && cell.z() >= r.z() && cell.z() <= r.maxZ() && (cell.x() == ring.x() || cell.x() == ring.maxX())) {
                return true;
            }
            if (!alongX && cell.x() >= r.x() && cell.x() <= r.maxX() && (cell.z() == ring.z() || cell.z() == ring.maxZ())) {
                return true;
            }
        }
        return false;
    }

    /** What a post of the fence costs, in half-units: the lights and the palisade are both built of fence posts. */
    static final String POST = "OAK_FENCE";

    /**
     * The cost of a work at the going rate (see {@link Construction#costPercent}): one fence post a spot, whole units
     * rounded up. Torches are free, as in a building.
     */
    public static Map<ResourceType, Integer> price(BuildingType type, VillagePlan plan) {
        Map<ResourceType, Integer> price = new EnumMap<>(ResourceType.class);
        int spots = spots(type, plan).size();
        return priceOf(price, spots);
    }

    /** What one post is worth back when an old ring is taken down: half its price, in whole units (R5.8). */
    public static int refund(int posts) {
        return Blueprint.halvesOf(POST).map(post -> (int) ((long) posts * post.halves() * Construction.costPercent() / 400))
                .orElse(0);
    }

    private static Map<ResourceType, Integer> priceOf(Map<ResourceType, Integer> price, int spots) {
        Blueprint.halvesOf(POST).ifPresent(post -> price.put(post.type(),
                Math.max(1, (int) Math.ceil((long) spots * post.halves() * Construction.costPercent() / 200.0))));
        return price;
    }
}
