package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * R8.3: a village's plan, stored with the settlement: a main square, a main street (the spine), branches off it, and
 * reserved lots along them, each lot tied to a building type, a biome set and a stage. Growth fills the next lot
 * rather than looking for space. The plan is plain data (rectangles in block coordinates); {@link PlanGenerator} makes
 * it from the ground and the planner and builder use it.
 */
public final class VillagePlan {
    public enum RoadKind {
        SPINE, BRANCH
    }

    public enum LotStatus {
        RESERVED, FILLED
    }

    /** A stretch of road. */
    public record Road(int id, RoadKind kind, Rect rect, int stage) {
    }

    /** A reserved plot for one building. */
    public record Lot(int id, Rect rect, BuildingType type, String biomeSet, int stage, LotStatus status) {
        Lot filled() {
            return new Lot(id, rect, type, biomeSet, stage, LotStatus.FILLED);
        }
    }

    private final long seed;
    private final String biomeSet;
    private final int centerX;
    private final int centerZ;
    private final boolean spineAlongX;
    private int stage;
    private int dropped;
    private int nextId = 1;
    private Rect square;
    private final List<Road> roads = new ArrayList<>();
    private final List<Lot> lots = new ArrayList<>();

    VillagePlan(long seed, String biomeSet, int centerX, int centerZ, boolean spineAlongX, int stage) {
        this.seed = seed;
        this.biomeSet = biomeSet;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.spineAlongX = spineAlongX;
        this.stage = stage;
    }

    public long seed() {
        return seed;
    }

    public String biomeSet() {
        return biomeSet;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    /** True if the main street runs east-west (along x); otherwise north-south. */
    public boolean spineAlongX() {
        return spineAlongX;
    }

    /** The village stage the plan has grown to: 1, then 2. */
    public int stage() {
        return stage;
    }

    void setStage(int stage) {
        this.stage = stage;
    }

    /** How many lots were dropped because their ground was too steep, wet or unknown. */
    public int dropped() {
        return dropped;
    }

    void addDropped(int count) {
        dropped += count;
    }

    public Rect square() {
        return square;
    }

    void setSquare(Rect square) {
        this.square = square;
    }

    /** True if a road runs east-west: the main street along the spine's axis, a branch across it (whatever its length). */
    public boolean runsAlongX(Road road) {
        return road.kind() == RoadKind.SPINE ? spineAlongX : !spineAlongX;
    }

    public List<Road> roads() {
        return java.util.Collections.unmodifiableList(roads);
    }

    public List<Lot> lots() {
        return java.util.Collections.unmodifiableList(lots);
    }

    int newId() {
        return nextId++;
    }

    void addRoad(Road road) {
        roads.add(road);
    }

    void replaceRoad(Road old, Road replacement) {
        roads.set(roads.indexOf(old), replacement);
    }

    void addLot(Lot lot) {
        lots.add(lot);
    }

    public int reservedCount() {
        return (int) lots.stream().filter(l -> l.status() == LotStatus.RESERVED).count();
    }

    public int filledCount() {
        return lots.size() - reservedCount();
    }

    /**
     * The next lot to fill for a kind of building: the reserved lot of that type nearest the main square (ties by id),
     * so a village grows outward from its centre.
     */
    public Optional<Lot> nextLot(BuildingType type) {
        return lots.stream().filter(l -> l.status() == LotStatus.RESERVED && l.type() == type)
                .min(Comparator.comparingLong((Lot l) -> distanceSquared(l.rect())).thenComparingInt(Lot::id));
    }

    /**
     * The lots a building could go on, best first: the reserved lots made for that kind, nearest the square first. When
     * the plan has none (a farm lot dropped for steep or wet ground, say) any other reserved lot will do, house lots
     * first as there are most of those, then the rest nearest the square; the caller checks the template fits.
     */
    public List<Lot> candidatesFor(BuildingType type) {
        List<Lot> same = lots.stream().filter(l -> l.status() == LotStatus.RESERVED && l.type() == type)
                .sorted(Comparator.comparingLong((Lot l) -> distanceSquared(l.rect())).thenComparingInt(Lot::id)).toList();
        if (!same.isEmpty()) {
            return same;
        }
        return lots.stream().filter(l -> l.status() == LotStatus.RESERVED)
                .sorted(Comparator.comparingInt((Lot l) -> l.type() == BuildingType.HOUSE ? 0 : 1)
                        .thenComparingLong(l -> distanceSquared(l.rect())).thenComparingInt(Lot::id)).toList();
    }

    /**
     * The side of a lot that faces the nearest street or the main square, as 0 north, 1 east, 2 south, 3 west: where a
     * building's entrance goes so it opens onto the street.
     */
    public int facingFor(Rect lot) {
        double cx = lot.x() + (lot.width() - 1) / 2.0;
        double cz = lot.z() + (lot.depth() - 1) / 2.0;
        double bestDistance = Double.MAX_VALUE;
        double bestDx = centerX - cx;
        double bestDz = centerZ - cz;
        java.util.List<Rect> streets = new ArrayList<>();
        for (Road road : roads) {
            streets.add(road.rect());
        }
        if (square != null) {
            streets.add(square);
        }
        for (Rect street : streets) {
            double px = Math.max(street.x(), Math.min(street.maxX(), cx));
            double pz = Math.max(street.z(), Math.min(street.maxZ(), cz));
            double distance = (px - cx) * (px - cx) + (pz - cz) * (pz - cz);
            if (distance > 0 && distance < bestDistance) {
                bestDistance = distance;
                bestDx = px - cx;
                bestDz = pz - cz;
            }
        }
        return Math.abs(bestDx) > Math.abs(bestDz) ? (bestDx > 0 ? 1 : 3) : (bestDz > 0 ? 2 : 0);
    }

    /** Marks a lot as built on. Returns false if there is no such lot or it is already filled. */
    public boolean fill(int lotId) {
        for (int i = 0; i < lots.size(); i++) {
            Lot lot = lots.get(i);
            if (lot.id() == lotId) {
                if (lot.status() == LotStatus.FILLED) {
                    return false;
                }
                lots.set(i, lot.filled());
                return true;
            }
        }
        return false;
    }

    private long distanceSquared(Rect rect) {
        long dx = rect.centerX() - centerX;
        long dz = rect.centerZ() - centerZ;
        return dx * dx + dz * dz;
    }

    // ----- the saved form: plain maps and lists -----

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("seed", seed);
        map.put("biomeSet", biomeSet);
        map.put("centerX", centerX);
        map.put("centerZ", centerZ);
        map.put("spineAlongX", spineAlongX);
        map.put("stage", stage);
        map.put("dropped", dropped);
        map.put("nextId", nextId);
        map.put("square", rectToList(square));
        List<Object> roadList = new ArrayList<>();
        for (Road road : roads) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", road.id());
            r.put("kind", road.kind().name());
            r.put("rect", rectToList(road.rect()));
            r.put("stage", road.stage());
            roadList.add(r);
        }
        map.put("roads", roadList);
        List<Object> lotList = new ArrayList<>();
        for (Lot lot : lots) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("id", lot.id());
            l.put("rect", rectToList(lot.rect()));
            l.put("type", lot.type().name());
            l.put("biomeSet", lot.biomeSet());
            l.put("stage", lot.stage());
            l.put("status", lot.status().name());
            lotList.add(l);
        }
        map.put("lots", lotList);
        return map;
    }

    private static List<Object> rectToList(Rect r) {
        return List.of(r.x(), r.z(), r.width(), r.depth());
    }

    /** Reads a plan back; throws {@code IllegalArgumentException} on a damaged one, which the caller drops. */
    public static VillagePlan fromMap(Map<?, ?> map) {
        VillagePlan plan = new VillagePlan(number(map, "seed").longValue(), String.valueOf(map.get("biomeSet")),
                number(map, "centerX").intValue(), number(map, "centerZ").intValue(),
                Boolean.TRUE.equals(map.get("spineAlongX")), number(map, "stage").intValue());
        plan.dropped = number(map, "dropped").intValue();
        plan.nextId = Math.max(1, number(map, "nextId").intValue());
        plan.square = rect(map.get("square"));
        if (map.get("roads") instanceof List<?> list) {
            for (Object o : list) {
                Map<?, ?> r = (Map<?, ?>) o;
                plan.roads.add(new Road(number(r, "id").intValue(), RoadKind.valueOf(String.valueOf(r.get("kind"))),
                        rect(r.get("rect")), number(r, "stage").intValue()));
            }
        }
        if (map.get("lots") instanceof List<?> list) {
            for (Object o : list) {
                Map<?, ?> l = (Map<?, ?>) o;
                plan.lots.add(new Lot(number(l, "id").intValue(), rect(l.get("rect")),
                        BuildingType.valueOf(String.valueOf(l.get("type"))), String.valueOf(l.get("biomeSet")),
                        number(l, "stage").intValue(), LotStatus.valueOf(String.valueOf(l.get("status")))));
            }
        }
        // (the main street is one piece at stage 1 and a piece more for every stage that lengthened it: R8.3, R8.14)
        if (plan.roads.stream().filter(r -> r.kind() == RoadKind.SPINE).count() < 1) {
            throw new IllegalArgumentException("a plan needs a main street");
        }
        return plan;
    }

    /**
     * Marks the reserved lot a registered building stands on as built on: the lot of the building's kind whose ground
     * (with a small margin, as a sign is often at a building's edge) contains the sign. Returns whether a lot was filled.
     */
    public boolean fillFor(BuildingType type, int x, int z) {
        for (int i = 0; i < lots.size(); i++) {
            Lot lot = lots.get(i);
            if (lot.status() == LotStatus.RESERVED && lot.type() == type && lot.rect().inflated(2).contains(x, z)) {
                lots.set(i, lot.filled());
                return true;
            }
        }
        return false;
    }

    /** Marks every lot that already has a registered building on it. */
    public void syncBuildings(java.util.Collection<Building> buildings) {
        for (Building building : buildings) {
            fillFor(building.type(), building.x(), building.z());
        }
    }

    private static Number number(Map<?, ?> map, String key) {
        if (map.get(key) instanceof Number n) {
            return n;
        }
        throw new IllegalArgumentException("missing number: " + key);
    }

    private static Rect rect(Object value) {
        if (value instanceof List<?> l && l.size() == 4 && l.stream().allMatch(n -> n instanceof Number)) {
            return new Rect(((Number) l.get(0)).intValue(), ((Number) l.get(1)).intValue(),
                    ((Number) l.get(2)).intValue(), ((Number) l.get(3)).intValue());
        }
        throw new IllegalArgumentException("not a rectangle: " + value);
    }

    @Override
    public String toString() {
        return "VillagePlan[" + biomeSet.toLowerCase(Locale.ROOT) + ", stage " + stage + ", " + lots.size() + " lots]";
    }
}
