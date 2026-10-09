package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * R8.14: the districts of a village. Each stage of the plan is a district: the first is the old town and the second the
 * new quarter; later ones are named for what their lots hold (the farm quarter, the market quarter). A district is the ground
 * its stage added, so they lie one outside another, and the wall round an earlier stage stays as the boundary between them.
 * Pure: the plan (and, for the walls, the settlement's finished works) in, names and bounds out.
 */
public final class Districts {
    private Districts() {
    }

    /** One district: the plan stage that made it, its name, the ground that stage added, and how many lots it has. */
    public record District(int stage, String name, Rect bounds, int lots, int built) {
    }

    /** The districts of a plan, innermost first. A stage that added nothing (the ground gave out) has none. */
    public static List<District> of(VillagePlan plan) {
        List<District> out = new ArrayList<>();
        if (plan == null) {
            return out;
        }
        Set<String> used = new HashSet<>();
        for (int stage = 1; stage <= plan.stage(); stage++) {
            List<Rect> rects = new ArrayList<>();
            int lots = 0;
            int built = 0;
            Map<BuildingType, Integer> held = new EnumMap<>(BuildingType.class);
            for (VillagePlan.Lot lot : plan.lots()) {
                if (lot.stage() == stage) {
                    rects.add(lot.rect());
                    lots++;
                    built += lot.status() == VillagePlan.LotStatus.FILLED ? 1 : 0;
                    held.merge(lot.type(), 1, Integer::sum);
                }
            }
            for (VillagePlan.Road road : plan.roads()) {
                if (road.stage() == stage) {
                    rects.add(road.rect());
                }
            }
            if (stage == 1 && plan.square() != null) {
                rects.add(plan.square());
            }
            if (rects.isEmpty()) {
                continue;
            }
            String name = nameFor(stage, held, used);
            if (!used.add(name)) {
                name = "The outer " + name.substring(4); // "The farm quarter" -> "The outer farm quarter"
                used.add(name);
            }
            out.add(new District(stage, name, bounds(rects), lots, built));
        }
        return out;
    }

    /** The name of a stage's district from what its lots hold. */
    static String nameFor(int stage, Map<BuildingType, Integer> held) {
        return nameFor(stage, held, Set.of());
    }

    /**
     * As above; of the kinds that tie for most, one whose name is not already a district's is chosen, so a village's districts
     * are not all "the farm quarter" because every stage's lots include a farm.
     */
    static String nameFor(int stage, Map<BuildingType, Integer> held, Set<String> taken) {
        if (stage == 1) {
            return "The old town";
        }
        if (stage == 2) {
            return "The new quarter";
        }
        Map<String, Integer> byKind = new java.util.LinkedHashMap<>(); // (in the order of the building types)
        for (Map.Entry<BuildingType, Integer> entry : new java.util.TreeMap<>(held).entrySet()) {
            if (entry.getKey() != BuildingType.HOUSE) {
                byKind.merge(kind(entry.getKey()), entry.getValue(), Integer::sum);
            }
        }
        String best = null;
        for (Map.Entry<String, Integer> entry : byKind.entrySet()) {
            boolean better = best == null || entry.getValue() > byKind.get(best)
                    || (entry.getValue().equals(byKind.get(best)) && taken.contains(quarter(best)) && !taken.contains(quarter(entry.getKey())));
            if (better) {
                best = entry.getKey();
            }
        }
        return best == null ? "The housing quarter" : quarter(best);
    }

    private static String quarter(String kind) {
        return "The " + kind + " quarter";
    }

    private static String kind(BuildingType type) {
        return switch (type) {
            case FARM, GRANARY -> "farm";
            case SHOP, TREASURY, TRADING_POST -> "market";
            case MINE, FORGE, MASONS_YARD -> "mining";
            case SMITHY, ARMOURY -> "smiths'";
            case GUARD_POST -> "garrison";
            case SAWMILL -> "timber";
            default -> type.label().toLowerCase(Locale.ROOT);
        };
    }

    /** The district a spot is in: the innermost one whose ground, with everything inside it, takes the spot in. */
    public static Optional<District> at(VillagePlan plan, int x, int z) {
        for (District district : of(plan)) {
            Optional<Rect> reach = Works.extent(plan, district.stage());
            if (reach.isPresent() && reach.get().contains(x, z)) {
                return Optional.of(district);
            }
        }
        return Optional.empty();
    }

    /** The wall round a stage's ring that has been built: 0 none, 1 a fence, 2 a plank rampart, 3 a stone one. */
    public static int wall(Settlement settlement, int stage) {
        int tier = 0;
        for (ConstructionProject p : settlement.projects()) {
            if (p.status() != ConstructionProject.Status.DONE) {
                continue;
            }
            if (p.type() == BuildingType.PALISADE && p.tier() == stage && !fenceReplaced(settlement, stage)) {
                tier = Math.max(tier, 1);
            } else if (p.type() == BuildingType.RAMPART && p.stage() == stage) {
                tier = Math.max(tier, p.tier());
            }
        }
        return tier;
    }

    /** True if a fence for a later stage has been built: the earlier fence was taken down (a rampart is never taken down). */
    private static boolean fenceReplaced(Settlement settlement, int stage) {
        return settlement.projects().stream().anyMatch(p -> p.type() == BuildingType.PALISADE
                && p.status() == ConstructionProject.Status.DONE && p.tier() > stage);
    }

    /** One line for each district, for {@code /settlement plan}: its name, its lots, and the wall round it. */
    public static List<String> describe(Settlement settlement) {
        List<String> lines = new ArrayList<>();
        for (District d : of(settlement.plan())) {
            int wall = wall(settlement, d.stage());
            lines.add(d.name() + " (stage " + d.stage() + "): " + d.lots() + " lots, " + d.built() + " built on; "
                    + switch (wall) {
                        case 0 -> "no wall round it";
                        case 1 -> "a fence round it";
                        case 2 -> "a plank rampart round it";
                        default -> "a stone rampart round it";
                    });
        }
        return lines;
    }

    private static Rect bounds(List<Rect> rects) {
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
        return new Rect(minX, minZ, maxX - minX + 1, maxZ - minZ + 1);
    }
}
