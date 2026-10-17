package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R8.30: the beds the game knows of in a village and which are claimed (a villager has taken them as home), for the dashboard. The Paper layer
 * reads them from the game's points of interest (loaded ground only); this pairs them with the houses. Pure and read-only.
 */
public final class BedCounts {
    /** How far from a house sign a bed counts as that house's (blocks across, blocks up or down): a house is a few blocks wide and its sign is at its door. */
    static final int NEAR = 8;
    static final int HIGH = 6;

    private BedCounts() {
    }

    /** One bed and whether a villager has claimed it. */
    public record Bed(int x, int y, int z, boolean claimed) {
    }

    /** The beds near each registered house, keyed by the building key: {@code {beds, claimed}}. A bed near two houses is counted for the nearer. */
    public static Map<String, int[]> perHouse(Settlement s, List<Bed> beds) {
        List<Building> houses = new ArrayList<>();
        for (Building b : s.buildings()) {
            if (b.type() == BuildingType.HOUSE) {
                houses.add(b);
            }
        }
        Map<String, int[]> out = new HashMap<>();
        houses.forEach(h -> out.put(h.key(), new int[2]));
        for (Bed bed : beds) {
            Building best = null;
            long bestD = Long.MAX_VALUE;
            for (Building h : houses) {
                long dx = bed.x() - h.x();
                long dz = bed.z() - h.z();
                if (Math.abs(dx) <= NEAR && Math.abs(dz) <= NEAR && Math.abs(bed.y() - h.y()) <= HIGH && dx * dx + dz * dz < bestD) {
                    bestD = dx * dx + dz * dz;
                    best = h;
                }
            }
            if (best != null) {
                int[] counts = out.get(best.key());
                counts[0]++;
                counts[1] += bed.claimed() ? 1 : 0;
            }
        }
        return out;
    }

    /** The beds for a village page: totals and each bed place ({@code [x, z, 1 if claimed]}). */
    public static Map<String, Object> asData(List<Bed> beds) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<int[]> places = new ArrayList<>();
        int claimed = 0;
        for (Bed b : beds) {
            places.add(new int[] {b.x(), b.z(), b.claimed() ? 1 : 0});
            claimed += b.claimed() ? 1 : 0;
        }
        out.put("total", beds.size());
        out.put("claimed", claimed);
        out.put("free", beds.size() - claimed);
        out.put("places", places);
        return out;
    }
}
