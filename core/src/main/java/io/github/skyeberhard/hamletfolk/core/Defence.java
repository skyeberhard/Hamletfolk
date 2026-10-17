package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R8.28: what the dashboard's defence and lights layer draws for a village: where its street lights and wall go (from the plan alone), the
 * wall's strength, the guards' patrol, and how much danger it is in. Whether each light and post actually stands is read from the world by
 * the Paper layer, which adds it. Pure and read-only.
 */
public final class Defence {
    private Defence() {
    }

    /** The layer's data for a village with a plan; empty if it has none. {@code day} is the village's day. */
    public static Map<String, Object> asData(Settlement s, long day) {
        Map<String, Object> out = new LinkedHashMap<>();
        VillagePlan plan = s.plan();
        if (plan == null) {
            return out;
        }
        int stage = plan.stage();
        int tier = Construction.wallTier(s, stage);
        out.put("stage", stage);
        out.put("tier", tier);
        out.put("lights", spots(Works.lights(plan, stage)));
        // the wall as it is, or as it is planned if there is none yet: a palisade's ring of posts, or a rampart's columns
        List<int[]> wall = new ArrayList<>();
        if (tier >= Rampart.FIRST_TIER) {
            for (Rampart.Piece piece : Rampart.pieces(plan, stage, tier)) {
                wall.add(new int[] {piece.x(), piece.z()});
            }
        } else {
            for (Works.Spot spot : Works.palisade(plan, stage)) {
                wall.add(new int[] {spot.x(), spot.z()});
            }
        }
        out.put("wall", wall);
        List<int[]> patrol = new ArrayList<>();
        for (PatrolRoute.Spot spot : PatrolRoute.route(plan, s.centerX(), s.centerZ())) {
            patrol.add(new int[] {spot.x(), spot.z()});
        }
        out.put("patrol", patrol);
        out.put("guards", s.residents().stream().filter(r -> r.adult() && r.occupation() == Occupation.GUARD).count());
        out.put("guardPosts", s.buildingCount(BuildingType.GUARD_POST));
        out.put("threat", Math.round(s.threat()));
        out.put("danger", SettlementSimulator.inDanger(s, day));
        out.put("incidents", SettlementSimulator.recentIncidents(s, day));
        out.put("lightsDone", Construction.worksDone(s, BuildingType.STREET_LIGHTS, stage));
        return out;
    }

    private static List<int[]> spots(List<Works.Spot> spots) {
        List<int[]> out = new ArrayList<>();
        for (Works.Spot spot : spots) {
            out.add(new int[] {spot.x(), spot.z()});
        }
        return out;
    }
}
