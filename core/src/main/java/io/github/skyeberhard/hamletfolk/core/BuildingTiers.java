package io.github.skyeberhard.hamletfolk.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * R8.29: each registered building's tier and beds, and whether an admin may ask for it to be upgraded to the next tier, for the dashboard's
 * plan screen; and that order itself. Only buildings the village's own builders raised are ever improved (a player's building is never touched).
 * An upgrade is queued like any other (paid from the stores, built by the village's builder, one project at a time).
 */
public final class BuildingTiers {
    private BuildingTiers() {
    }

    /**
     * One building.
     *
     * @param tier      its tier now (1 for one a player registered)
     * @param maxTier   the best tier the village has a design for (the same as {@code tier} if it is at its best)
     * @param beds      the beds a house has at this tier (0 for anything but a house, or if the design is not known here)
     * @param nextBeds  the beds at the next tier, or 0
     * @param upgradable true if an admin may ask for the next tier now
     * @param why       when it cannot be upgraded, why, in words
     * @param cost      what the next tier costs the stores (empty if there is no next tier or its design is not known here)
     * @param lacking   how much of each the stores are short of now (empty if they can pay)
     * @param estimated true if the tier is a guess from the building size or beds (a vanilla building the village did not raise has no tier of its own)
     */
    public record Info(int tier, int maxTier, int beds, int nextBeds, boolean upgradable, String why, Map<ResourceType, Integer> cost,
            Map<ResourceType, Integer> lacking, boolean estimated) {
    }

    /** What is known of the buildings the village did not raise, to guess their tier from: footprints ({@code x, z, width, depth}) and live beds, by building key. */
    public record Evidence(Map<String, int[]> footprints, Map<String, Integer> liveBeds) {
        public static final Evidence NONE = new Evidence(Map.of(), Map.of());
    }

    /** What an order did. */
    public record Order(boolean ok, String message) {
    }

    /** The tier, beds and upgrade state of every registered building, keyed by the building's key (its sign's position). */
    public static Map<String, Info> of(Settlement s, TemplateCatalog catalog, Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints) {
        return of(s, catalog, blueprints, Evidence.NONE);
    }

    /** As above, guessing the tier of a vanilla building the village did not raise from what is known of its size and beds. */
    public static Map<String, Info> of(Settlement s, TemplateCatalog catalog, Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints, Evidence evidence) {
        Map<String, Info> out = new LinkedHashMap<>();
        String biome = s.plan() == null ? "plains" : s.plan().biomeSet();
        for (Building b : s.buildings()) {
            out.put(b.key(), info(s, b, biome, catalog, blueprints, evidence));
        }
        return out;
    }

    /** As {@link #of} but as plain data for the dashboard: {@code tier}, {@code maxTier}, {@code beds}, {@code nextBeds}, {@code upgradable}, {@code why}. */
    public static Map<String, Map<String, Object>> asData(Settlement s, TemplateCatalog catalog, Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints,
            Evidence evidence) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        of(s, catalog, blueprints, evidence).forEach((key, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tier", i.tier());
            row.put("maxTier", i.maxTier());
            row.put("beds", i.beds());
            row.put("nextBeds", i.nextBeds());
            row.put("upgradable", i.upgradable());
            row.put("why", i.why());
            row.put("estimated", i.estimated());
            Map<String, Integer> cost = new LinkedHashMap<>();
            i.cost().forEach((t, n) -> cost.put(t.name().toLowerCase(java.util.Locale.ROOT), n));
            Map<String, Integer> lacking = new LinkedHashMap<>();
            i.lacking().forEach((t, n) -> lacking.put(t.name().toLowerCase(java.util.Locale.ROOT), n));
            row.put("cost", cost);
            row.put("lacking", lacking);
            out.put(key, row);
        });
        return out;
    }

    static Info info(Settlement s, Building b, String biome, TemplateCatalog catalog, Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints) {
        return info(s, b, biome, catalog, blueprints, Evidence.NONE);
    }

    static Info info(Settlement s, Building b, String biome, TemplateCatalog catalog, Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints, Evidence evidence) {
        int tier = s.tierOf(b);
        var ladder = catalog.ladder(b.type(), biome);
        boolean estimated = false;
        if (built(s, b) == null) { // not raised by the village builders: a vanilla or player building has no tier of its own, so guess one
            Integer guess = estimate(ladder, b, evidence, blueprints);
            if (guess != null) {
                tier = guess;
                estimated = true;
            }
        }
        int max = ladder.stream().mapToInt(TemplateCatalog.Template::tier).max().orElse(tier);
        int beds = 0;
        int next = 0;
        if (b.type() == BuildingType.HOUSE) {
            beds = bedsAt(ladder, tier, blueprints);
            next = tier < max ? bedsAt(ladder, tier + 1, blueprints) : 0;
        }
        String why = refusal(s, b, tier, max);
        Map<ResourceType, Integer> cost = new java.util.EnumMap<>(ResourceType.class);
        Map<ResourceType, Integer> lacking = new java.util.EnumMap<>(ResourceType.class);
        final int nextTier = tier + 1;
        if (tier < max) { // what the next tier would cost, and what the stores lack of it
            ladder.stream().filter(t -> t.tier() == nextTier).findFirst().flatMap(blueprints).ifPresent(bp -> {
                Construction.priceFor(s, b.type(), true, bp).forEach((type, amount) -> {
                    if (amount > 0) {
                        cost.put(type, amount);
                        int have = s.ledger().get(type);
                        if (have < amount) {
                            lacking.put(type, amount - have);
                        }
                    }
                });
            });
        }
        return new Info(tier, Math.max(tier, max), beds, next, why == null, why == null ? "" : why, cost, lacking, estimated);
    }

    /**
     * Which rung of the ladder a building not raised by the village is nearest to: a house by its beds (the game count, if read), anything else
     * by the area of its outline against the area of each design. Null if nothing is known to go on.
     */
    private static Integer estimate(java.util.List<TemplateCatalog.Template> ladder, Building b, Evidence evidence,
            Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints) {
        int best = -1;
        long bestGap = Long.MAX_VALUE;
        Integer live = evidence.liveBeds().get(b.key());
        int[] rect = evidence.footprints().get(b.key());
        for (TemplateCatalog.Template t : ladder) {
            Optional<Blueprint> bp = blueprints.apply(t);
            if (bp.isEmpty()) {
                continue;
            }
            long gap;
            if (b.type() == BuildingType.HOUSE && live != null && live > 0) {
                gap = Math.abs(bp.get().beds() - live);
            } else if (rect != null && rect.length == 4) {
                gap = Math.abs((long) bp.get().width() * bp.get().depth() - (long) rect[2] * rect[3]);
            } else {
                return null;
            }
            if (gap < bestGap) {
                bestGap = gap;
                best = t.tier();
            }
        }
        return best < 0 ? null : best;
    }

    private static int bedsAt(java.util.List<TemplateCatalog.Template> ladder, int tier, Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints) {
        return ladder.stream().filter(t -> t.tier() == tier).findFirst().flatMap(blueprints).map(Blueprint::beds).orElse(0);
    }

    /** Why this building cannot be upgraded now, or null if it can be asked for. */
    private static String refusal(Settlement s, Building b, int tier, int max) {
        ConstructionProject done = built(s, b);
        if (done == null) {
            return "built by a player, or found standing: the builders never change those";
        }
        if (tier >= max) {
            return "already at its best tier";
        }
        if (PlanOverrides.isForbidden(s, b.type())) {
            return "an admin has forbidden this kind of building";
        }
        if (s.openProject().isPresent()) {
            return "the builders are busy with another project";
        }
        if (!Construction.isLatestOnLot(new java.util.ArrayList<>(s.projects()), done, s.lastSimulatedDay())) {
            return "a later project is on its lot";
        }
        return null;
    }

    /** The finished project of the village's own builders that made this building and still stands, or null. */
    private static ConstructionProject built(Settlement s, Building b) {
        ConstructionProject found = null;
        for (ConstructionProject p : s.projects()) {
            if (p.status() == ConstructionProject.Status.DONE && p.signY() != ConstructionProject.NO_SIGN
                    && p.signX() == b.x() && p.signY() == b.y() && p.signZ() == b.z() && Construction.standing(s, p)) {
                found = p; // (the latest, if the lot has been built on more than once)
            }
        }
        return found;
    }

    /**
     * Queues an upgrade of one building to the next tier the stores can pay for now (no margin: it is an order, not a want). Refused with the
     * reason if it cannot be done.
     *
     * @param key the building's key (its sign's position, "x,y,z")
     */
    public static Order order(Settlement s, String key, long day, TemplateCatalog catalog,
            Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints, HeightSource terrain) {
        Building b = s.buildings().stream().filter(x -> x.key().equals(key)).findFirst().orElse(null);
        if (b == null || s.plan() == null) {
            return new Order(false, "no such building");
        }
        String biome = s.plan().biomeSet();
        Info info = info(s, b, biome, catalog, blueprints);
        if (!info.upgradable()) {
            return new Order(false, s.name() + "'s " + b.type().label().toLowerCase(java.util.Locale.ROOT) + " cannot be upgraded: " + info.why() + ".");
        }
        ConstructionProject done = built(s, b);
        var lot = s.plan().lots().stream().filter(l -> l.id() == done.lotId()).findFirst();
        if (lot.isEmpty()) {
            return new Order(false, "its lot is no longer in the plan");
        }
        Optional<ConstructionProject> project = Construction.queue(s, day, biome, b.type(), done.tier(), lot.get(), 1, catalog, blueprints, terrain,
                "an admin asked for it", done, false);
        if (project.isEmpty()) {
            return new Order(false, "The stores cannot pay for a better " + b.type().label().toLowerCase(java.util.Locale.ROOT) + " yet, or no better design fits its lot.");
        }
        s.record(day, HistoryEvent.Kind.BUILDING, "An admin asked for the " + b.type().label().toLowerCase(java.util.Locale.ROOT) + " to be improved to tier "
                + project.get().tier() + ".");
        return new Order(true, "The builders of " + s.name() + " will upgrade the " + b.type().label().toLowerCase(java.util.Locale.ROOT) + " to tier "
                + project.get().tier() + ".");
    }
}
