package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * R4.7, R4.8: deciding what the village builds, handing the job to a builder, and the work itself in terms the Paper
 * layer only has to carry out: the steps still to do against the world as it is, and the payment for each from the
 * ledger. No game types here. The Paper layer supplies the blocks it finds (as strings), and places the ones it is told to.
 */
public final class Construction {
    /** While the stores hold less food than this a head and a farm has free places, the jobless farm rather than build. */
    static final int FOOD_FIRST_PER_RESIDENT = 10;
    /** A village needs this many people before it starts building. */
    public static final int MIN_POPULATION = 3;
    /** The ground height meaning "not measured" (the chunk is not loaded). */
    public static final int UNKNOWN_GROUND = Integer.MIN_VALUE;

    /** One thing for the builder to do: put a block down, or knock one of the old building's blocks out. */
    public record Step(Blueprint.Block block, boolean demolish) {
    }

    private Construction() {
    }

    /**
     * R4.7: what villagers pay, as a percentage of what the blocks would cost to craft. The game's own buildings are
     * big (a smithy is nearly 300 wood), more than a village's stores can hold, so a village could never afford one;
     * 100 in core so tests are exact, set from the config (default 35) by the Paper layer, like the lifespan scale.
     */
    private static volatile int costPercent = 100;

    public static void setCostPercent(int percent) {
        costPercent = Math.max(1, Math.min(100, percent));
    }

    public static int costPercent() {
        return costPercent;
    }

    /** A blueprint's cost at the going rate, whole units rounded up. */
    public static Map<ResourceType, Integer> priceOf(Blueprint blueprint) {
        Map<ResourceType, Integer> price = new EnumMap<>(ResourceType.class);
        blueprint.cost().forEach((type, units) -> price.put(type, Math.max(1, (int) Math.ceil(units * costPercent / 100.0))));
        return price;
    }

    // ----- comparing blocks -----

    /**
     * True if what the world holds ("minecraft:oak_planks", "ladder[facing=south,waterlogged=false]", null for air)
     * is what the blueprint wants ("OAK_PLANKS", "LADDER[facing=south]"): the same block, and every property the
     * blueprint names the same (properties it does not name are not compared).
     */
    public static boolean matches(String world, String wanted) {
        String have = world == null || world.isBlank() ? "AIR" : world;
        if (!blockName(have).equals(blockName(wanted))) {
            return false;
        }
        Map<String, String> wantedStates = states(wanted);
        if (wantedStates.isEmpty()) {
            return true;
        }
        Map<String, String> haveStates = states(have);
        for (Map.Entry<String, String> entry : wantedStates.entrySet()) {
            if (CHANGES_BY_ITSELF.contains(entry.getKey())) {
                continue;
            }
            if (!entry.getValue().equals(haveStates.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /** Properties a block changes on its own once placed (a crop grows, a door is opened, a bed is slept in). */
    private static final java.util.Set<String> CHANGES_BY_ITSELF = java.util.Set.of("age", "open", "occupied", "powered",
            "snowy", "moisture", "lit", "triggered", "waterlogged");

    /** One property of a block ("facing" of "LADDER[facing=south]"), lower case, or null if it has none. */
    static String stateOf(String material, String property) {
        return states(material).get(property);
    }

    private static String blockName(String material) {
        String name = Blueprint.name(material).toUpperCase(Locale.ROOT);
        int colon = name.indexOf(':');
        name = colon >= 0 ? name.substring(colon + 1) : name;
        return name.equals("CAVE_AIR") || name.equals("VOID_AIR") ? "AIR" : name;
    }

    private static Map<String, String> states(String material) {
        Map<String, String> out = new HashMap<>();
        int open = material.indexOf('[');
        int close = material.lastIndexOf(']');
        if (open < 0 || close < open) {
            return out;
        }
        for (String pair : material.substring(open + 1, close).split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                out.put(pair.substring(0, eq).trim().toLowerCase(Locale.ROOT), pair.substring(eq + 1).trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    // ----- the work -----

    /**
     * What is left to do to turn the world into the target: first knock out blocks of the building it replaces that the
     * new one has no block for, then place every block that is not already right, floor up. {@code existing} says what
     * the world holds at a block's relative position.
     */
    public static List<Step> worklist(Blueprint target, Blueprint old, Function<Blueprint.Block, String> existing) {
        List<Step> steps = new ArrayList<>();
        if (old != null) {
            Map<Long, String> wanted = new HashMap<>();
            for (Blueprint.Block block : target.blocks()) {
                wanted.put(key(block), block.material());
            }
            for (Blueprint.Block block : old.blocks()) {
                if ("AIR".equalsIgnoreCase(Blueprint.name(block.material())) || wanted.containsKey(key(block))) {
                    continue;
                }
                if (matches(existing.apply(block), block.material())) {
                    steps.add(new Step(new Blueprint.Block(block.x(), block.y(), block.z(), "AIR"), true));
                }
            }
        }
        for (Blueprint.Block block : target.blocks()) {
            if (!matches(existing.apply(block), block.material())) {
                steps.add(new Step(block, false));
            }
        }
        return steps;
    }

    private static long key(Blueprint.Block block) {
        return ((long) (block.y() + 512) << 40) | ((long) (block.z() + 512) << 20) | (block.x() + 512);
    }

    /**
     * Pays for one block out of the ledger. Returns false (and records what is missing) when the stores cannot cover
     * it, so the builder waits. Slabs cost half a unit: a whole unit is taken and the other half kept as credit.
     */
    public static boolean charge(Settlement settlement, ConstructionProject project, String material, long day) {
        Optional<Blueprint.Halves> cost = Blueprint.halvesOf(material);
        if (cost.isEmpty()) {
            project.setWaitingFor(null);
            return true;
        }
        ResourceType type = cost.get().type();
        // Credit is counted in 1/200ths of a unit: a half-unit is 100, so a block costs halves x percent of those.
        int price = cost.get().halves() * costPercent;
        int have = project.credit().getOrDefault(type, 0);
        int taken = 0;
        while (have < price) {
            if (settlement.ledger().take(type, 1) < 1) {
                project.credit().put(type, have);
                if (taken > 0) {
                    settlement.flow().recordConsumed(type, day, taken);
                }
                project.setWaitingFor(type);
                return false;
            }
            have += 200;
            taken++;
        }
        project.credit().put(type, have - price);
        if (taken > 0) {
            settlement.flow().recordConsumed(type, day, taken);
        }
        project.setWaitingFor(null);
        return true;
    }

    /** The building is up: the project is done, the lot is built on, the builder is free, and the history says so. */
    public static void finish(Settlement settlement, ConstructionProject project, long day) {
        String who = project.builder() == null ? null : settlement.resident(project.builder()).map(Resident::fullName).orElse(null);
        project.finish(day);
        if (settlement.plan() != null && project.lotId() >= 0) {
            settlement.plan().fill(project.lotId());
        }
        settlement.record(day, HistoryEvent.Kind.BUILDING, (who == null ? "The builders" : who) + " finished "
                + (project.isUpgrade() ? "upgrading the " : "building a ") + project.type().label().toLowerCase(Locale.ROOT)
                + " (tier " + project.tier() + ").");
    }

    /** The project is given up (the site was built on, or the village is gone); the lot is left alone. */
    public static void cancel(Settlement settlement, ConstructionProject project, long day, String reason) {
        cancel(settlement, project, day, reason, true);
    }

    /** As {@link #cancel(Settlement, ConstructionProject, long, String)}; {@code abandonLot} marks the lot used up. */
    public static void cancel(Settlement settlement, ConstructionProject project, long day, String reason, boolean abandonLot) {
        project.cancel(day);
        if (abandonLot && settlement.plan() != null && project.lotId() >= 0) {
            settlement.plan().fill(project.lotId()); // so the plan moves on to another lot instead of trying again
        }
        settlement.record(day, HistoryEvent.Kind.BUILDING, "Work on the " + project.type().label().toLowerCase(Locale.ROOT)
                + " was given up: " + reason + ".");
    }

    // ----- builders -----

    /**
     * One builder per open project. A jobless adult (not a child or an elder) takes a queued project unless the village
     * is short of food; a builder whose project is gone goes back to being jobless, and a project whose builder has gone
     * (left, died, or been called up as a guard) goes back in the queue.
     */
    static void staffBuilders(Settlement settlement, long day, boolean famine) {
        for (ConstructionProject project : settlement.projects()) {
            if (project.status() == ConstructionProject.Status.ACTIVE
                    && (famine || settlement.resident(project.builder()).filter(r -> r.occupation() == Occupation.BUILDER).isEmpty())) {
                project.release(); // a famine puts every hand back on food
            }
        }
        // Food first (R2.3): while the farms have free places and there is not enough food, the jobless go to them.
        long farmers = settlement.residents().stream().filter(r -> r.adult() && r.occupation() == Occupation.FARMER).count();
        boolean foodShort = famine || (SettlementSimulator.placesFor(settlement, Occupation.FARMER) > farmers
                && settlement.ledger().get(ResourceType.FOOD) < FOOD_FIRST_PER_RESIDENT * Math.max(1, settlement.population()));
        for (Resident r : settlement.residents()) {
            if (r.occupation() == Occupation.BUILDER && settlement.projects().stream().noneMatch(
                    p -> p.status() == ConstructionProject.Status.ACTIVE && r.id().equals(p.builder()))) {
                r.setOccupation(Occupation.UNEMPLOYED);
            }
        }
        if (foodShort) {
            return;
        }
        Optional<ConstructionProject> queued = settlement.projects().stream()
                .filter(p -> p.status() == ConstructionProject.Status.QUEUED).findFirst();
        if (queued.isEmpty()) {
            return;
        }
        for (Resident r : settlement.residents()) {
            if (r.adult() && r.stage(day) != LifeStage.ELDER && r.occupation() == Occupation.UNEMPLOYED) {
                r.setOccupation(Occupation.BUILDER);
                queued.get().claim(r.id());
                settlement.record(day, HistoryEvent.Kind.MILESTONE, r.fullName() + " took on the work of building the "
                        + queued.get().type().label().toLowerCase(Locale.ROOT) + ".");
                return;
            }
        }
        // Nobody is out of work. A project that has waited a few days for a builder takes someone off a job that does not
        // feed the village: a village that is fully employed still has to be able to put up a house.
        if (day - queued.get().queuedDay() >= DRAFT_AFTER_DAYS) {
            Resident drafted = null;
            boolean foodPlentiful = settlement.ledger().get(ResourceType.FOOD)
                    >= 2 * FOOD_FIRST_PER_RESIDENT * Math.max(1, settlement.population());
            long foodWorkers = settlement.residents().stream()
                    .filter(r -> r.adult() && r.occupation().produces() == ResourceType.FOOD).count();
            for (Resident r : settlement.residents()) {
                Occupation job = r.occupation();
                if (!r.adult() || r.stage(day) == LifeStage.ELDER || job == Occupation.BUILDER || job == Occupation.GUARD
                        || job == Occupation.MERCHANT || SettlementSimulator.lastToolMaker(settlement, r)) {
                    continue;
                }
                // Food workers only when the larder is well stocked, and never the last one: otherwise a village whose
                // people all farm and fish could never build anything.
                if (job.produces() == ResourceType.FOOD && (!foodPlentiful || foodWorkers <= 1)) {
                    continue;
                }
                // Idlers first, then whoever is least busy; ties go to the first in the list so it is repeatable.
                if (drafted == null || (job == Occupation.NITWIT && drafted.occupation() != Occupation.NITWIT)) {
                    drafted = r;
                }
            }
            if (drafted != null) {
                String was = drafted.occupation().title();
                drafted.setOccupation(Occupation.BUILDER);
                queued.get().claim(drafted.id());
                settlement.record(day, HistoryEvent.Kind.MILESTONE, drafted.fullName() + " put down the work of a " + was
                        + " to build the " + queued.get().type().label().toLowerCase(Locale.ROOT) + ", as there was no one else.");
            }
        }
    }

    /** Days a project waits for someone out of work before a worker in another trade is taken off it. */
    static final int DRAFT_AFTER_DAYS = 3;

    // ----- deciding what to build -----

    /**
     * R4.7: if the village lacks a building the planner says it needs, and has a reserved lot for it that a template
     * fits, and can pay for the best tier it can afford, queues a project for it. With nothing lacking, a building the
     * village raised itself may be upgraded to a better tier it can comfortably afford (twice the cost in stock). A
     * building a player made is never touched: only projects on record are upgraded. One project at a time, and at
     * most one decision a day.
     *
     * @param blueprints   the blocks of a template (the Paper layer reads vanilla pieces); empty if it has none
     * @param groundHeight the height of the surface block at an x and z, or {@link #UNKNOWN_GROUND}
     */
    public static Optional<ConstructionProject> propose(Settlement settlement, long day, String style, int treasuryLimit,
            TemplateCatalog catalog, Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints,
            HeightSource terrain) {
        VillagePlan plan = settlement.plan();
        dropStaleQueuedProject(settlement, day, treasuryLimit);
        if (plan == null || settlement.population() < MIN_POPULATION || settlement.isAbandoned()
                || settlement.openProject().isPresent() || decidedToday(settlement, day)) {
            return Optional.empty();
        }
        String biome = BiomeSet.normalize(style);
        boolean squareTried = false;
        for (Planner.Directive directive : Planner.directives(settlement, day, treasuryLimit)) {
            if (directive.kind() != Planner.Kind.BUILD) {
                continue;
            }
            Optional<BuildingType> type = BuildingType.fromSign("[" + directive.target() + "]");
            if (type.isEmpty()) {
                continue;
            }
            if (!squareTried && directive.tier() != Planner.Tier.FOOD) {
                squareTried = true; // food comes first, then the meeting place, then the rest
                Optional<ConstructionProject> square = proposeSquare(settlement, day, biome, catalog, blueprints, terrain);
                if (square.isPresent()) {
                    return square;
                }
            }
            boolean first = true;
            for (VillagePlan.Lot lot : plan.candidatesFor(type.get())) {
                Optional<ConstructionProject> project = queue(settlement, day, biome, type.get(), 0, lot, 1, catalog,
                        blueprints, terrain, directive.reason(), null, first);
                if (project.isPresent()) {
                    return project;
                }
                first = false;
            }
            // No lot, or no way to pay for another: the need is met by improving a building the village already has, if it can.
            if (directive.tier() != Planner.Tier.GROWTH) { // a want (a shop, a treasury) is not a reason to spend everything
                Optional<ConstructionProject> improved = upgrade(settlement, day, biome, catalog, blueprints, terrain,
                        t -> t == type.get(), 1, false, directive.reason());
                if (improved.isPresent()) {
                    return improved;
                }
            }
        }
        boolean foodNeeded = Planner.directives(settlement, day, treasuryLimit).stream()
                .anyMatch(d -> d.kind() == Planner.Kind.BUILD && d.tier() == Planner.Tier.FOOD);
        if (!squareTried && !foodNeeded) { // never ahead of a farm a hungry village cannot yet build
            Optional<ConstructionProject> square = proposeSquare(settlement, day, biome, catalog, blueprints, terrain);
            if (square.isPresent()) {
                return square;
            }
        }
        // Every need is met. What the village wants now follows what it is good at: the buildings that serve its direction
        // are improved first, then any other, at most one a week and never a building just finished.
        Long lastUpgrade = settlement.conditions().get(UPGRADED);
        if (lastUpgrade != null && day - lastUpgrade < UPGRADE_EVERY_DAYS) {
            return Optional.empty();
        }
        Direction direction = direction(settlement, day);
        Optional<ConstructionProject> wanted = upgrade(settlement, day, biome, catalog, blueprints, terrain,
                t -> direction.serves(t), 2, true, "it is a " + direction.label() + " village and can afford a better one");
        if (wanted.isPresent()) {
            return wanted;
        }
        return upgrade(settlement, day, biome, catalog, blueprints, terrain, t -> true, 2, true, "it can afford a better one");
    }

    /**
     * Improves a building the village raised itself (never one a player made): the next tier of one of the kinds, if the
     * stores hold margin times its cost. A need passes margin 1 and no pacing; a want, margin 2 and a building that has
     * stood a few days.
     */
    private static Optional<ConstructionProject> upgrade(Settlement settlement, long day, String biome, TemplateCatalog catalog,
            Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints, HeightSource terrain,
            java.util.function.Predicate<BuildingType> kinds, int margin, boolean paced, String reason) {
        VillagePlan plan = settlement.plan();
        List<ConstructionProject> built = new ArrayList<>(settlement.projects());
        for (int i = built.size() - 1; i >= 0; i--) {
            ConstructionProject done = built.get(i);
            if (done.status() != ConstructionProject.Status.DONE || !kinds.test(done.type()) || !isLatestOnLot(built, done, day)
                    || !standing(settlement, done) || (paced && day - done.finishedDay() < UPGRADE_AFTER_DAYS)) {
                continue;
            }
            Optional<VillagePlan.Lot> lot = plan.lots().stream().filter(l -> l.id() == done.lotId()).findFirst();
            if (lot.isEmpty()) {
                continue;
            }
            Optional<ConstructionProject> project = queue(settlement, day, biome, done.type(), done.tier(), lot.get(),
                    margin, catalog, blueprints, terrain, reason, done, false);
            if (project.isPresent()) {
                if (paced) {
                    settlement.conditions().put(UPGRADED, day);
                }
                return project;
            }
        }
        return Optional.empty();
    }

    /**
     * What a village is good at, from what it produces and trades over the last week: its direction once every need is met.
     * It decides which buildings are improved first.
     */
    public enum Direction {
        FARMING("farming", BuildingType.FARM, BuildingType.HOUSE),
        FORESTRY("timber", BuildingType.HOUSE, BuildingType.SHOP),
        MINING("mining", BuildingType.MINE, BuildingType.SMITHY),
        CRAFT("craft", BuildingType.SMITHY, BuildingType.SHOP),
        TRADE("trading", BuildingType.SHOP, BuildingType.TREASURY),
        UNDECIDED("all-round");

        private final String label;
        private final List<BuildingType> serves;

        Direction(String label, BuildingType... serves) {
            this.label = label;
            this.serves = List.of(serves);
        }

        public String label() {
            return label;
        }

        /** True for a building this direction is built around. */
        public boolean serves(BuildingType type) {
            return serves.contains(type);
        }
    }

    /**
     * The village's direction: a trading village if it has merchants and has banked a good sum, otherwise whatever it
     * produces most of (food counted lightly, as every village makes plenty), and undecided if it has made little.
     */
    public static Direction direction(Settlement settlement, long day) {
        ResourceFlow flow = settlement.flow();
        boolean merchants = settlement.residents().stream().anyMatch(r -> r.occupation() == Occupation.MERCHANT);
        if (merchants && SettlementSimulator.banked(settlement) >= TRADE_TREASURY) {
            return Direction.TRADE;
        }
        double food = flow.produced(ResourceType.FOOD, day) / 4.0;
        double wood = flow.produced(ResourceType.WOOD, day);
        double stone = flow.produced(ResourceType.STONE, day) + flow.produced(ResourceType.METAL, day);
        double craft = flow.produced(ResourceType.TOOLS, day) + flow.produced(ResourceType.GOODS, day);
        double best = Math.max(Math.max(food, wood), Math.max(stone, craft));
        if (best < MIN_DIRECTION_OUTPUT) {
            return Direction.UNDECIDED;
        }
        return best == food ? Direction.FARMING : best == wood ? Direction.FORESTRY : best == stone ? Direction.MINING : Direction.CRAFT;
    }

    /** Emeralds banked before a village with merchants counts as a trading village. */
    static final int TRADE_TREASURY = 100;
    /** Least weekly output (food counted at a quarter) before a village has any direction at all. */
    static final int MIN_DIRECTION_OUTPUT = 20;

    /** Days a finished building must stand before the village thinks of improving it, and between two upgrades. */
    static final int UPGRADE_AFTER_DAYS = 5;
    static final int UPGRADE_EVERY_DAYS = 7;
    static final String UPGRADED = "constructionUpgraded";
    /** Days before a given-up town square is tried again (the square has no lot to use up, so it would be tried daily). */
    static final int SQUARE_RETRY_DAYS = 10;
    /** After this many given-up attempts the village stops trying (something is in the way that will not move). */
    static final int SQUARE_ATTEMPTS = 2;

    /**
     * The village's meeting place: once there is food, the game's own town centre (with its bell) is built on the plan's
     * main square. Only one is ever built; a given-up attempt waits ten days, and after two the village stops trying.
     */
    private static Optional<ConstructionProject> proposeSquare(Settlement settlement, long day, String biome,
            TemplateCatalog catalog, Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints, HeightSource terrain) {
        VillagePlan plan = settlement.plan();
        if (plan == null || plan.square() == null || settlement.buildingCount(BuildingType.SQUARE) > 0
                || settlement.projects().stream().filter(p -> p.type() == BuildingType.SQUARE
                        && p.status() == ConstructionProject.Status.CANCELLED).count() >= SQUARE_ATTEMPTS
                || settlement.projects().stream().anyMatch(p -> p.type() == BuildingType.SQUARE
                        && (p.status() != ConstructionProject.Status.CANCELLED || day - p.finishedDay() < SQUARE_RETRY_DAYS))) {
            return Optional.empty();
        }
        VillagePlan.Lot lot = new VillagePlan.Lot(-1, plan.square(), BuildingType.SQUARE, biome, 0, VillagePlan.LotStatus.RESERVED);
        return queue(settlement, day, biome, BuildingType.SQUARE, 0, lot, 1, catalog, blueprints, terrain,
                "the village has no meeting place of its own", null, true);
    }

    /**
     * A new building still waiting for a builder is dropped once the planner no longer asks for that kind (the farm that
     * was wanted when food was short, say, when the larders have since filled), so the village can get on with what it
     * needs now. Work already begun, and upgrades, carry on.
     */
    private static void dropStaleQueuedProject(Settlement settlement, long day, int treasuryLimit) {
        Optional<ConstructionProject> open = settlement.openProject();
        if (open.isEmpty() || open.get().status() != ConstructionProject.Status.QUEUED || open.get().isUpgrade()
                || open.get().type() == BuildingType.SQUARE || open.get().queuedDay() >= day) { // the planner never asks for a square
            return;
        }
        String target = open.get().type().name().toLowerCase(Locale.ROOT);
        boolean wanted = Planner.directives(settlement, day, treasuryLimit).stream()
                .anyMatch(d -> d.kind() == Planner.Kind.BUILD && d.target().equalsIgnoreCase(target));
        if (!wanted) {
            cancel(settlement, open.get(), day, "it is not needed any more", false);
        }
    }

    static final String DECIDED = "constructionDecided";

    /** True if the village has already made (or declined to make) its building decision today. */
    public static boolean decidedToday(Settlement settlement, long day) {
        Long decided = settlement.conditions().get(DECIDED);
        return decided != null && decided >= day;
    }

    /** Days before a given-up upgrade of a building may be tried again. */
    static final int UPGRADE_RETRY_DAYS = 20;

    private static boolean isLatestOnLot(List<ConstructionProject> all, ConstructionProject project, long day) {
        // A later project on the lot, even a given-up upgrade, means this one is not ours to try again.
        return all.stream().noneMatch(p -> p != project && p.lotId() == project.lotId() && p.id() > project.id()
                && !(p.status() == ConstructionProject.Status.CANCELLED && day - p.finishedDay() >= UPGRADE_RETRY_DAYS));
    }

    /** The building the project made still has its sign registered, so it still stands and was not replaced. */
    private static boolean standing(Settlement settlement, ConstructionProject project) {
        return project.signY() != ConstructionProject.NO_SIGN && settlement.hasBuildingAt(project.signX(), project.signY(), project.signZ());
    }

    /**
     * Queues the best tier above {@code currentTier} that fits the lot and the stores can pay for (with {@code margin}
     * times the cost in stock: 1 for something the village lacks, 2 for an upgrade).
     */
    private static Optional<ConstructionProject> queue(Settlement settlement, long day, String biome, BuildingType type,
            int currentTier, VillagePlan.Lot lot, int margin, TemplateCatalog catalog,
            Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints, HeightSource terrain, String reason,
            ConstructionProject replacing, boolean explain) {
        List<TemplateCatalog.Template> ladder = catalog.ladder(type, biome);
        Map<ResourceType, Integer> stock = new EnumMap<>(ResourceType.class);
        for (ResourceType resource : ResourceType.values()) {
            stock.put(resource, settlement.ledger().get(resource) / margin);
        }
        // An upgrade is built over the old building, centred on it; a new building is centred on its lot.
        // Every building is turned so its entrance faces the nearest street or the square; an upgrade keeps the old facing.
        int desired = settlement.plan().facingFor(lot.rect());
        Optional<Blueprint> oldBlueprint = replacing == null ? Optional.empty()
                : ladder.stream().filter(t -> t.tier() == replacing.tier()).findFirst().flatMap(blueprints)
                        .map(b -> b.rotated(replacing.turns()));
        if (replacing != null && oldBlueprint.isEmpty()) {
            return Optional.empty();
        }
        // The way it is turned: with its entrance on the street if it fits like that, otherwise the nearest turn that fits.
        Map<String, Optional<Oriented>> read2 = new HashMap<>();
        Function<TemplateCatalog.Template, Optional<Oriented>> oriented = t -> read2.computeIfAbsent(t.key(), k ->
                blueprints.apply(t).flatMap(b -> orient(b, desired, lot, replacing, oldBlueprint.orElse(null))));
        // The plainest design the village can pay for: a village spends on what it lacks, not on the grandest version of it.
        Optional<TemplateCatalog.Template> best = TemplateCatalog.plainestAffordable(ladder, currentTier,
                t -> oriented.apply(t).map(o -> priceOf(o.blueprint())).orElse(UNBUILDABLE), stock);
        if (best.isEmpty()) {
            if (replacing == null && explain) {
                explainWhyNot(settlement, day, type, ladder, blueprints, lot);
            }
            return Optional.empty();
        }
        Blueprint blueprint = oriented.apply(best.get()).orElseThrow().blueprint();
        int turns = oriented.apply(best.get()).orElseThrow().turns();
        int[] origin = originFor(blueprint, lot, replacing, oldBlueprint.orElse(null)).orElseThrow();
        int x = origin[0];
        int z = origin[1];
        // An upgrade keeps the old floor level (the ground there is now the old building's roof). A new building is set at
        // the median height of its ground, and the ground is graded to it (see TerrainPad); a lot that cannot be graded is passed over.
        int ground;
        if (replacing != null) {
            ground = replacing.y();
        } else {
            TerrainPad.Pad pad = TerrainPad.compute(new Rect(x, z, blueprint.width(), blueprint.depth()), PAD_BUFFER, terrain);
            if (!pad.valid()) {
                return Optional.empty();
            }
            ground = pad.targetHeight();
        }
        ConstructionProject project = new ConstructionProject(settlement.nextProjectId(), type, best.get().tier(),
                currentTier, biome, x, ground, z, lot.id(), day);
        project.setTurns(turns);
        if (replacing != null) {
            project.setShift(replacing.x() - x, replacing.z() - z);
            project.setOldTurns(replacing.turns());
        }
        settlement.addProject(project);
        settlement.conditions().put(DECIDED, day); // at most one new project a day
        settlement.record(day, HistoryEvent.Kind.BUILDING, settlement.name() + " set out to "
                + (currentTier > 0 ? "upgrade its " : "build a ") + type.label().toLowerCase(Locale.ROOT) + " (tier "
                + best.get().tier() + "): " + reason + ".");
        return Optional.of(project);
    }

    /** Days between reminders that the village wants a building it cannot build. */
    static final int BLOCKED_REMINDER_DAYS = 5;
    static final String BLOCKED = "constructionBlocked";

    /**
     * The village wants a building and has a lot for it but cannot start: say what it lacks in the history, now and then,
     * so a player can see why nothing is happening (and donate what is missing).
     */
    private static void explainWhyNot(Settlement settlement, long day, BuildingType type, List<TemplateCatalog.Template> ladder,
            Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints, VillagePlan.Lot lot) {
        Long last = settlement.conditions().get(BLOCKED + ":" + type.name());
        if ((last != null && day - last < BLOCKED_REMINDER_DAYS) || ladder.isEmpty()) {
            return;
        }
        Optional<Blueprint> first = blueprints.apply(ladder.get(0));
        String name = type.label().toLowerCase(Locale.ROOT);
        String text;
        if (first.isEmpty()) {
            text = settlement.name() + " wants a " + name + " but has no design for one.";
        } else if (!(first.get().width() <= lot.rect().width() && first.get().depth() <= lot.rect().depth())
                && !(first.get().depth() <= lot.rect().width() && first.get().width() <= lot.rect().depth())) {
            text = settlement.name() + " wants a " + name + " but its lot is too small for the plainest design.";
        } else {
            StringBuilder lacks = new StringBuilder();
            priceOf(first.get()).forEach((resource, units) -> {
                int have = settlement.ledger().get(resource);
                if (have < units) {
                    lacks.append(lacks.length() == 0 ? "" : ", ").append(units).append(' ')
                            .append(resource.name().toLowerCase(Locale.ROOT)).append(" (it has ").append(have).append(')');
                }
            });
            text = settlement.name() + " wants a " + name + " but cannot afford the plainest one yet: it needs "
                    + (lacks.length() == 0 ? "more of something" : lacks.toString()) + ".";
        }
        settlement.conditions().put(BLOCKED + ":" + type.name(), day);
        settlement.record(day, HistoryEvent.Kind.BUILDING, text);
    }

    /** The blocks of blended edge round a graded building. */
    public static final int PAD_BUFFER = 2;

    /** The quarter turns that put a building's entrance on the side facing {@code desired}; none for one with no entrance. */
    static int turnsFor(Blueprint original, int desired) {
        java.util.OptionalInt front = original.front();
        return front.isPresent() ? ((desired - front.getAsInt()) % 4 + 4) % 4 : 0;
    }

    /** A template turned so it fits its lot, and by how much. */
    record Oriented(int turns, Blueprint blueprint) {
    }

    /**
     * Turns a template so its entrance faces {@code desired} if it fits the lot like that; failing that, the nearest turn
     * to it that does (a sideways entrance beats no building), the way round last. Empty if no turn fits.
     */
    private static Optional<Oriented> orient(Blueprint original, int desired, VillagePlan.Lot lot, ConstructionProject replacing,
            Blueprint old) {
        int wanted = turnsFor(original, desired);
        for (int offset : new int[] {0, 1, 3, 2}) {
            int turns = (wanted + offset) % 4;
            Blueprint turned = original.rotated(turns);
            boolean fits = originFor(turned, lot, replacing, old).map(
                    o -> o[0] >= lot.rect().x() && o[1] >= lot.rect().z()
                            && o[0] + turned.width() <= lot.rect().x() + lot.rect().width()
                            && o[1] + turned.depth() <= lot.rect().z() + lot.rect().depth()).orElse(false);
            if (fits) {
                return Optional.of(new Oriented(turns, turned));
            }
            if (original.front().isEmpty()) {
                break; // nothing to face the street with: only the plain orientation is tried
            }
        }
        return Optional.empty();
    }

    /** The corner (x, z) a building goes at: centred on its lot, or over the building it replaces, centred on that. */
    private static Optional<int[]> originFor(Blueprint b, VillagePlan.Lot lot, ConstructionProject replacing, Blueprint old) {
        if (replacing == null) {
            return Optional.of(new int[] {lot.rect().x() + (lot.rect().width() - b.width()) / 2,
                    lot.rect().z() + (lot.rect().depth() - b.depth()) / 2});
        }
        return Optional.of(new int[] {replacing.x() - (b.width() - old.width()) / 2, replacing.z() - (b.depth() - old.depth()) / 2});
    }

    /** A cost no stock can meet, for a template with no blocks or that does not fit the lot. */
    private static final Map<ResourceType, Integer> UNBUILDABLE = Map.of(ResourceType.FOOD, Integer.MAX_VALUE);
}
