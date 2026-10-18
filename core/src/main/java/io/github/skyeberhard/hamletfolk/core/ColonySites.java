package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * R10.3: where a daughter village goes. The Minecraft layer proposes places (a ring round the parent, in chunks that are already generated, each
 * checked against the ground: water, steepness, things built on it, protected or exempt ground) and reads the land round each with the site
 * survey; this chooses among them. A place must be far enough from every village ({@link Colonies.Rules#minSpacing}) and near enough to trade
 * with the parent, be land a village could stand on, have water, and be able to feed itself. Of those it prefers the best all-round land,
 * land that has what the parent is short of (stone for a village with no stone), and the nearer. Pure: the same places always give the same choice.
 */
public final class ColonySites {
    /** The farthest a daughter may be from its parent, in blocks: well inside the trade range ({@link Exchange#DEFAULT_RANGE}). */
    public static final int MAX_DISTANCE = 600;
    /** Least share of the surroundings that is land a village could stand on. */
    static final double MIN_BUILDABLE = 0.6;
    /** Least reach (0 to 100) of water and of food (farmland and grazing together). */
    static final double MIN_WATER = SiteSurvey.DRY;
    static final double MIN_FOOD = 20.0;
    /** The most that having what the parent lacks adds to a place's score, and the most the distance takes off. */
    static final double COMPLEMENT_BONUS = 12.0;
    static final double DISTANCE_PENALTY = 6.0;

    private ColonySites() {
    }

    /**
     * A proposed place.
     *
     * @param rejected why the Minecraft layer ruled it out (water, steep, built on, protected, not generated...), or null if it did not
     */
    public record Candidate(int x, int z, int y, SiteSurvey.Profile profile, String rejected) {
    }

    /** A place that is chosen, with its score and in plain words why. */
    public record Choice(Candidate site, double score, int distance, String direction, List<String> reasons) {
    }

    /** The best place (if any), the next best, and how many places were ruled out for each reason. */
    public record Result(Optional<Choice> best, List<Choice> others, Map<String, Integer> ruledOut, int considered) {
    }

    /** A village's centre, to keep the spacing from. */
    public record Point(int x, int z) {
    }

    /** One of eight points of the compass for a direction (north is toward negative z, as in the game). */
    public static String direction(int dx, int dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        int index = (int) Math.round(((degrees % 360) + 360) % 360 / 45.0) % 8;
        return names[index];
    }

    public static Result choose(Settlement parent, List<Candidate> candidates, List<Point> others, Colonies.Rules rules) {
        Map<String, Integer> ruledOut = new LinkedHashMap<>();
        List<Choice> good = new ArrayList<>();
        List<SiteResource> lacking = new ArrayList<>();
        for (SiteResource resource : SiteResource.values()) {
            if (parent.hasLeaning() && parent.landScore(resource) < SiteSurvey.SCARCE
                    && (resource == SiteResource.STONE || resource == SiteResource.LUMBER || resource == SiteResource.ORE)) {
                lacking.add(resource);
            }
        }
        for (Candidate c : candidates) {
            String no = no(parent, c, others, rules);
            if (no != null) {
                ruledOut.merge(no, 1, Integer::sum);
                continue;
            }
            SiteSurvey.Profile p = c.profile();
            int dx = c.x() - parent.centerX();
            int dz = c.z() - parent.centerZ();
            int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
            double score = p.overall() + 5.0 * p.foodBalance();
            List<String> reasons = new ArrayList<>();
            double bonus = 0;
            for (SiteResource resource : lacking) {
                double stands = p.score(resource); // (stone and ore are reachable under any land: what counts is where they stand out)
                if (stands >= SiteSurvey.SCARCE * 2) {
                    bonus += COMPLEMENT_BONUS / lacking.size() * Math.min(1.0, stands / 60.0);
                    reasons.add("has " + resource.label() + ", which " + parent.name() + " is short of");
                }
            }
            score += Math.min(COMPLEMENT_BONUS, bonus);
            int span = Math.max(1, MAX_DISTANCE - rules.minSpacing());
            score -= DISTANCE_PENALTY * Math.max(0, distance - rules.minSpacing()) / span;
            if (p.reach(SiteResource.FARMLAND) >= 50) {
                reasons.add("good farmland");
            }
            if (p.reach(SiteResource.WATER) >= 50) {
                reasons.add("plenty of water");
            }
            if (p.imports().isEmpty()) {
                reasons.add("every resource within reach");
            } else {
                reasons.add("would have to import " + p.imports().stream().map(r -> r.name().toLowerCase(Locale.ROOT)).toList());
            }
            good.add(new Choice(c, score, distance, direction(dx, dz), List.copyOf(reasons)));
        }
        good.sort(Comparator.comparingDouble(Choice::score).reversed().thenComparingInt(ch -> ch.site().x()).thenComparingInt(ch -> ch.site().z()));
        Optional<Choice> best = good.isEmpty() ? Optional.empty() : Optional.of(good.get(0));
        List<Choice> next = good.size() <= 1 ? List.of() : List.copyOf(good.subList(1, Math.min(good.size(), 4)));
        return new Result(best, next, ruledOut, candidates.size());
    }

    /** Why a place cannot be used, or null if it can. */
    static String no(Settlement parent, Candidate c, List<Point> others, Colonies.Rules rules) {
        if (c.rejected() != null) {
            return c.rejected();
        }
        long dx = c.x() - parent.centerX();
        long dz = c.z() - parent.centerZ();
        double distance = Math.sqrt((double) (dx * dx + dz * dz));
        if (distance > MAX_DISTANCE) {
            return "too far from " + parent.name();
        }
        List<Point> villages = new ArrayList<>(others);
        villages.add(new Point(parent.centerX(), parent.centerZ())); // (the parent too)
        for (Point other : villages) {
            long ox = c.x() - other.x();
            long oz = c.z() - other.z();
            if (ox * ox + oz * oz < (long) rules.minSpacing() * rules.minSpacing()) {
                return "too close to another village";
            }
        }
        SiteSurvey.Profile p = c.profile();
        if (p.buildable() < MIN_BUILDABLE) {
            return "too much water or unbuildable land";
        }
        if (p.reach(SiteResource.WATER) < MIN_WATER) {
            return "too dry";
        }
        double food = 0.7 * p.reach(SiteResource.FARMLAND) + 0.3 * p.reach(SiteResource.LIVESTOCK);
        if (food < MIN_FOOD) {
            return "could not feed itself";
        }
        return null;
    }

    /** e.g. "-1200 70 -800, 410 blocks north-east of Motherton: good farmland, has stone, which Motherton is short of". */
    public static String describe(Choice choice, Settlement parent) {
        Candidate c = choice.site();
        return c.x() + " " + c.y() + " " + c.z() + ", " + choice.distance() + " blocks " + choice.direction() + " of " + parent.name()
                + ": " + String.join(", ", choice.reasons());
    }
}
