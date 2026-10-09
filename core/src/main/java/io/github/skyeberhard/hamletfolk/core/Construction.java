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
        Optional<Resident> builder = project.builder() == null ? Optional.empty() : settlement.resident(project.builder());
        String who = builder.map(Resident::fullName).orElse(null);
        project.finish(day);
        if (settlement.plan() != null && project.lotId() >= 0) {
            settlement.plan().fill(project.lotId());
        }
        String what = switch (project.type()) {
            case STREET_LIGHTS -> project.isUpgrade() ? "lighting the new streets." : "lighting the streets.";
            case PALISADE -> project.isUpgrade() ? "moving the palisade out round the grown village." : "raising the palisade.";
            case RAMPART -> project.tier() >= Rampart.LAST_TIER ? "rebuilding the rampart in stone." : "raising the rampart.";
            default -> (project.isUpgrade() ? "upgrading the " : "building a ") + project.type().label().toLowerCase(Locale.ROOT)
                    + " (tier " + project.tier() + ").";
        };
        settlement.record(day, HistoryEvent.Kind.BUILDING, (who == null ? "The builders" : who) + " finished " + what);
        // R4.21: every building finished makes the builder better at it.
        builder.ifPresent(r -> {
            int before = builderLevel(r.built());
            r.setBuilt(r.built() + 1);
            if (builderLevel(r.built()) > before) {
                settlement.record(day, HistoryEvent.Kind.MILESTONE, r.fullName() + " has finished " + r.built()
                        + " buildings and is now a " + builderTitle(r.built()) + ".");
            }
        });
    }

    // ----- builder skill (R4.21) -----

    /** Buildings finished to reach each level, what each level is called, and the blocks a builder places a second. */
    static final int[] LEVEL_AT = {0, 3, 6, 10};
    private static final String[] LEVEL_TITLES = {"apprentice builder", "builder", "journeyman builder", "master builder"};
    private static final int[] BLOCKS_PER_PASS = {4, 6, 8, 10};

    /** The level (0 to 3) a builder with this many finished buildings has reached. */
    public static int builderLevel(int built) {
        int level = 0;
        for (int i = 0; i < LEVEL_AT.length; i++) {
            if (built >= LEVEL_AT[i]) {
                level = i;
            }
        }
        return level;
    }

    /** e.g. "journeyman builder". */
    public static String builderTitle(int built) {
        return LEVEL_TITLES[builderLevel(built)];
    }

    /** How many blocks a builder with this many finished buildings places each pass (about a second). */
    public static int blocksPerPass(int built) {
        return BLOCKS_PER_PASS[builderLevel(built)];
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
     * One builder per open project. A builder keeps the trade between buildings and takes the next project before anyone
     * else is asked (R4.21); one with nothing to build for {@link #BUILDER_IDLE_DAYS} days goes back to other work. With
     * no builder free, a jobless adult (not a child or an elder) takes a queued project unless the village is short of
     * food. A famine puts every hand back on food, except on a farm, which is what ends it. A project whose builder has
     * gone (left, died, or been called up as a guard) goes back in the queue.
     */
    static void staffBuilders(Settlement settlement, long day, boolean famine) {
        for (ConstructionProject project : settlement.projects()) {
            if (project.status() == ConstructionProject.Status.ACTIVE
                    && ((famine && project.type() != BuildingType.FARM)
                            || settlement.resident(project.builder()).filter(r -> r.occupation() == Occupation.BUILDER).isEmpty())) {
                project.release();
            }
        }
        List<Resident> idle = new ArrayList<>();
        for (Resident r : settlement.residents()) {
            if (r.occupation() == Occupation.BUILDER && settlement.projects().stream().noneMatch(
                    p -> p.status() == ConstructionProject.Status.ACTIVE && r.id().equals(p.builder()))) {
                idle.add(r);
            }
        }
        List<Resident> pinnedIdle = idle.stream().filter(r -> settlement.isPinned(r.id())).toList(); // R1.31: never let go
        if (idle.isEmpty()) {
            settlement.removeCondition(BUILDER_IDLE);
        } else {
            long since = settlement.conditions().computeIfAbsent(BUILDER_IDLE, k -> day);
            if (famine || day - since >= BUILDER_IDLE_DAYS) {
                idle.stream().filter(r -> !settlement.isPinned(r.id())).forEach(r -> r.setOccupation(Occupation.UNEMPLOYED));
                idle.clear();
                idle.addAll(pinnedIdle);
                settlement.removeCondition(BUILDER_IDLE);
            }
        }
        Optional<ConstructionProject> queued = settlement.projects().stream()
                .filter(p -> p.status() == ConstructionProject.Status.QUEUED).findFirst();
        if (queued.isEmpty()) {
            return;
        }
        String what = queued.get().type().label().toLowerCase(Locale.ROOT);
        if (!idle.isEmpty() && famine && queued.get().type() != BuildingType.FARM) {
            return; // a builder kept on (pinned) still waits out a famine, as everyone else does
        }
        if (!idle.isEmpty()) {
            queued.get().claim(idle.get(0).id());
            settlement.removeCondition(BUILDER_IDLE);
            settlement.record(day, HistoryEvent.Kind.MILESTONE, idle.get(0).fullName() + " began work on the " + what + ".");
            return;
        }
        // Food first (R2.3): while the farms have free places and there is not enough food, the jobless go to them.
        long farmers = settlement.residents().stream().filter(r -> r.adult() && r.occupation() == Occupation.FARMER).count();
        boolean foodShort = famine || (SettlementSimulator.placesFor(settlement, Occupation.FARMER) > farmers
                && settlement.ledger().get(ResourceType.FOOD) < FOOD_FIRST_PER_RESIDENT * Math.max(1, settlement.population()));
        if (foodShort && queued.get().type() != BuildingType.FARM) {
            return;
        }
        for (Resident r : settlement.residents()) {
            if (r.adult() && r.stage(day) != LifeStage.ELDER && r.occupation() == Occupation.UNEMPLOYED
                    && !settlement.isPinned(r.id())) {
                r.setOccupation(Occupation.BUILDER);
                queued.get().claim(r.id());
                settlement.record(day, HistoryEvent.Kind.MILESTONE, r.fullName() + " took on the work of building the " + what + ".");
                return;
            }
        }
        // Nobody is out of work. A project that has waited a few days for a builder takes someone off a job the village
        // can spare: a village that is fully employed still has to be able to put up a house.
        if (day - queued.get().queuedDay() >= DRAFT_AFTER_DAYS) {
            Resident drafted = SettlementSimulator.spareWorker(settlement, day);
            if (drafted != null) {
                String was = drafted.occupation().title();
                drafted.setOccupation(Occupation.BUILDER);
                queued.get().claim(drafted.id());
                settlement.record(day, HistoryEvent.Kind.MILESTONE, drafted.fullName() + " put down the work of a " + was
                        + " to build the " + what + ", as there was no one else.");
            }
        }
    }

    // ----- works on the plan (R5.6) -----

    /** Days before a given-up work is tried again. */
    static final int WORKS_RETRY_DAYS = 10;
    /** A work is started once the stores hold this share of the storage limit, or its price if that is less, and then paid for as it goes. */
    static final double WORKS_START_SHARE = 0.7;

    /** True if the village has the work, or is putting it up (a finished project, or one in hand), for any stage. */
    public static boolean hasWorks(Settlement settlement, BuildingType type) {
        return hasWorks(settlement, type, 1);
    }

    /**
     * R5.8: true if the village has the work for this stage of its plan or a later one, or is putting it up. A work's
     * tier is the plan stage it was laid out for.
     */
    public static boolean hasWorks(Settlement settlement, BuildingType type, int stage) {
        return settlement.projects().stream().anyMatch(p -> p.type() == type && p.tier() >= stage
                && (p.status() == ConstructionProject.Status.DONE || p.isOpen()));
    }

    /**
     * R5.10: how strong the wall round the village is for this stage of its plan: 0 with no palisade, 1 the fence, 2 or 3 the
     * rampart of that tier if one was laid out for this stage or a later one.
     */
    public static int wallTier(Settlement settlement, int stage) {
        int tier = worksDone(settlement, BuildingType.PALISADE, stage) ? 1 : 0;
        for (ConstructionProject p : settlement.projects()) {
            if (p.type() == BuildingType.RAMPART && p.status() == ConstructionProject.Status.DONE && p.stage() >= stage) {
                tier = Math.max(tier, p.tier());
            }
        }
        return tier;
    }

    /** R5.10: true if a rampart is being built. */
    public static boolean rampartOpen(Settlement settlement) {
        return settlement.projects().stream().anyMatch(p -> p.type() == BuildingType.RAMPART && p.isOpen());
    }

    /** True if the village has finished the work, for any stage. */
    public static boolean worksDone(Settlement settlement, BuildingType type) {
        return worksDone(settlement, type, 1);
    }

    /** R5.8: true if the village has finished the work for this stage of its plan or a later one. */
    public static boolean worksDone(Settlement settlement, BuildingType type, int stage) {
        return settlement.projects().stream().anyMatch(p -> p.type() == type && p.tier() >= stage
                && p.status() == ConstructionProject.Status.DONE);
    }

    /** True if there is nothing to start: it stands or is in hand, was given up lately, or the plan has no place for it. */
    private static boolean worksSettled(Settlement settlement, BuildingType type, long day) {
        if (type == BuildingType.RAMPART) {
            return rampartOpen(settlement) || Works.spots(BuildingType.PALISADE, settlement.plan()).isEmpty()
                    || settlement.projects().stream().anyMatch(p -> p.type() == type && p.status() == ConstructionProject.Status.CANCELLED
                            && day - p.finishedDay() < WORKS_RETRY_DAYS);
        }
        return hasWorks(settlement, type, settlement.plan().stage()) || Works.spots(type, settlement.plan()).isEmpty()
                || settlement.projects().stream().anyMatch(p -> p.type() == type
                        && p.status() == ConstructionProject.Status.CANCELLED && day - p.finishedDay() < WORKS_RETRY_DAYS);
    }

    /**
     * Queues the work if the stores can start it: they hold its price, or most of the storage limit if the price is more than
     * that. Otherwise notes what is lacking (so the trade that makes it is taken on, R4.20) and says so in the history now and
     * then.
     */
    private static Optional<ConstructionProject> proposeWorks(Settlement settlement, long day, BuildingType type, String reason) {
        VillagePlan plan = settlement.plan();
        int before = settlement.projects().stream().filter(p -> p.type() == type && p.status() == ConstructionProject.Status.DONE)
                .mapToInt(ConstructionProject::tier).max().orElse(0);
        // R5.10: a rampart is the next tier up from the wall there is now, over the whole ring
        int nextTier = Math.min(Rampart.LAST_TIER, Math.max(Rampart.FIRST_TIER, wallTier(settlement, plan.stage()) + 1));
        Map<ResourceType, Integer> price = type == BuildingType.RAMPART ? Rampart.price(plan, plan.stage(), nextTier)
                : Works.price(type, plan, before, plan.stage()); // R5.8: only the new posts
        StringBuilder lacks = new StringBuilder();
        price.forEach((resource, units) -> {
            int needed = Math.min(units, (int) (SettlementSimulator.capacity(settlement, resource) * WORKS_START_SHARE));
            int have = settlement.ledger().get(resource);
            if (have < needed) {
                settlement.conditions().put(LACKS + resource.name(), day);
                lacks.append(lacks.length() == 0 ? "" : ", ").append(needed).append(' ')
                        .append(resource.name().toLowerCase(Locale.ROOT)).append(" (it has ").append(have).append(')');
            }
        });
        String name = type == BuildingType.STREET_LIGHTS ? "the street lights"
                : type == BuildingType.RAMPART ? "a tier " + nextTier + " rampart" : "the palisade";
        if (lacks.length() > 0) {
            Long last = settlement.conditions().get(BLOCKED + ":" + type.name());
            if (last == null || day - last >= BLOCKED_REMINDER_DAYS) {
                settlement.conditions().put(BLOCKED + ":" + type.name(), day);
                settlement.record(day, HistoryEvent.Kind.BUILDING, settlement.name() + " wants " + name
                        + " but cannot start yet: it needs " + lacks + ".");
            }
            return Optional.empty();
        }
        // R5.8: a work's tier is the plan stage it is laid out for; the previous tier is the ring it replaces, if any.
        int previous = settlement.projects().stream().filter(p -> p.type() == type && p.status() == ConstructionProject.Status.DONE)
                .mapToInt(ConstructionProject::tier).max().orElse(0);
        ConstructionProject project = type == BuildingType.RAMPART
                ? new ConstructionProject(settlement.nextProjectId(), type, nextTier, nextTier - 1, "plains", plan.centerX(), 0,
                        plan.centerZ(), -1, day)
                : new ConstructionProject(settlement.nextProjectId(), type, plan.stage(), previous, "plains",
                        plan.centerX(), 0, plan.centerZ(), -1, day);
        project.setStage(plan.stage());
        settlement.addProject(project);
        settlement.conditions().put(DECIDED, day);
        settlement.record(day, HistoryEvent.Kind.BUILDING, settlement.name() + " set out to "
                + (type == BuildingType.STREET_LIGHTS ? "light its streets" : type == BuildingType.RAMPART
                        ? (nextTier == Rampart.FIRST_TIER ? "raise a rampart of planks, with gates and watch towers"
                                : "rebuild its rampart in stone")
                        : "raise a palisade") + ": " + reason + ".");
        return Optional.of(project);
    }

    /** R4.21: days a builder waits for something to build before going back to other work. */
    static final int BUILDER_IDLE_DAYS = 7;
    static final String BUILDER_IDLE = "builderIdleSince";

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
        boolean needUnmet = false;
        for (Planner.Directive directive : Planner.directives(settlement, day, treasuryLimit)) {
            if (directive.kind() != Planner.Kind.BUILD) {
                continue;
            }
            Optional<BuildingType> type = BuildingType.fromTarget(directive.target());
            if (type.isEmpty() || (needUnmet && directive.tier() == Planner.Tier.GROWTH)) {
                continue; // (a want waits for a need the village is saving up for)
            }
            if (type.get().isWorks()) {
                // R5.6: lights and a palisade are laid out on the plan, not on a lot. One the village cannot yet pay for is saved up for.
                if (!worksSettled(settlement, type.get(), day)) {
                    Optional<ConstructionProject> works = proposeWorks(settlement, day, type.get(), directive.reason());
                    if (works.isPresent()) {
                        return works;
                    }
                    // Lights and a fence are saved up for ahead of everything else; a stronger wall is not worth stopping the
                    // village's other building for (it still notes what it lacks, so a lumberjack is taken on).
                    needUnmet |= type.get() != BuildingType.RAMPART;
                }
                continue;
            }
            if (!squareTried && !needUnmet && directive.tier() != Planner.Tier.FOOD) {
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
            // R4.20: a need with somewhere to go that the village cannot pay for yet, for want of something it can make, is
            // saved up for, not spent past. Not the mine: a village with no stone and no mine could never make the stone.
            needUnmet |= !first && directive.tier() != Planner.Tier.GROWTH && directive.tier() != Planner.Tier.SUPPLY
                    && savingFor(settlement, catalog.ladder(type.get(), biome), blueprints);
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
        if (!squareTried && !foodNeeded && !needUnmet) { // never ahead of a farm a hungry village cannot yet build, or a need it is saving for
            Optional<ConstructionProject> square = proposeSquare(settlement, day, biome, catalog, blueprints, terrain);
            if (square.isPresent()) {
                return square;
            }
        }
        if (needUnmet) {
            return Optional.empty();
        }
        // Every need is met. What the village wants now follows what it is good at: the buildings that serve its direction
        // are improved first, then any other, at most one a week and never a building just finished.
        Long lastUpgrade = settlement.conditions().get(UPGRADED);
        if (lastUpgrade != null && day - lastUpgrade < VillageCharacter.temperament(settlement).upgradeEveryDays()) {
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
        FARMING("farming", BuildingType.FARM, BuildingType.HOUSE, BuildingType.GRANARY),
        FORESTRY("timber", BuildingType.HOUSE, BuildingType.SHOP, BuildingType.SAWMILL),
        MINING("mining", BuildingType.MINE, BuildingType.SMITHY, BuildingType.FORGE),
        CRAFT("craft", BuildingType.SMITHY, BuildingType.SHOP),
        TRADE("trading", BuildingType.SHOP, BuildingType.TREASURY),
        FISHING("fishing", BuildingType.HOUSE, BuildingType.SHOP),
        PASTORAL("pastoral", BuildingType.FARM, BuildingType.HOUSE, BuildingType.GRANARY),
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
        // R8.11: what the land makes the village comes before what it happened to make last week.
        switch (settlement.leaning()) {
            case TIMBER -> {
                return Direction.FORESTRY;
            }
            case MINING -> {
                return Direction.MINING;
            }
            case FARMING -> {
                return Direction.FARMING;
            }
            case FISHING -> {
                return Direction.FISHING;
            }
            case PASTORAL -> {
                return Direction.PASTORAL;
            }
            default -> {
                // all-round: it goes by what it makes
            }
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
                || open.get().type() == BuildingType.SQUARE || open.get().queuedDay() >= day // the planner never asks for a square
                || open.get().type().isWorks()) { // and stops asking for a work once it is in hand, so it would look unwanted
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
            // A want keeps twice its cost in store, or, where the storage limit is too low for that, enough that the
            // material is not left short (R4.20): otherwise a full store could never pay for a bigger building.
            int have = settlement.ledger().get(resource);
            stock.put(resource, margin <= 1 ? have
                    : Math.max(have / margin, have - SettlementSimulator.wantedLevel(settlement, resource)));
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
        // A house is the exception (R4.26): the one with the most beds for its cost, and an upgrade only to more beds.
        Function<TemplateCatalog.Template, Map<ResourceType, Integer>> costOf =
                t -> oriented.apply(t).map(o -> priceOf(o.blueprint())).orElse(UNBUILDABLE);
        Function<TemplateCatalog.Template, Integer> bedsOf = t -> oriented.apply(t).map(o -> o.blueprint().beds()).orElse(0);
        boolean anyBeds = ladder.stream().anyMatch(t -> bedsOf.apply(t) > 0);
        Optional<TemplateCatalog.Template> best = type == BuildingType.HOUSE && anyBeds
                ? TemplateCatalog.mostBedsAffordable(ladder, currentTier, costOf, bedsOf, stock, oldBlueprint.map(Blueprint::beds).orElse(0))
                : TemplateCatalog.plainestAffordable(ladder, currentTier, costOf, stock); // (a ladder with no beds read: as before)
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
    /** R4.20: the last day the plainest design of a wanted building lacked a material, one condition per material. */
    static final String LACKS = "constructionLacks:";
    /** R4.20: how long a material the village could not build for still counts as wanted. */
    static final int LACK_MEMORY_DAYS = 10;

    /**
     * R4.20: the materials the village's building is waiting on: what the open project ran out of, and what the plainest
     * design of a building it wanted lacked in the last {@link #LACK_MEMORY_DAYS} days. The trades that make them are
     * taken on (see SettlementSimulator), whatever the stock per head.
     */
    public static java.util.Set<ResourceType> lacking(Settlement settlement, long day) {
        java.util.Set<ResourceType> out = java.util.EnumSet.noneOf(ResourceType.class);
        settlement.openProject().map(ConstructionProject::waitingFor).ifPresent(out::add);
        for (ResourceType type : ResourceType.values()) {
            Long seen = settlement.conditions().get(LACKS + type.name());
            if (seen != null && day >= seen && day - seen <= LACK_MEMORY_DAYS) {
                out.add(type);
            }
        }
        return out;
    }

    /**
     * R4.20: true if the plainest design of a building is short of a material the village can make itself, so waiting
     * will pay for it: wood and food always, stone where a mine has places or a mason works, metal where a mine has places.
     * A lot that cannot be graded, or a material nobody can make, is not something to save up for.
     */
    private static boolean savingFor(Settlement settlement, List<TemplateCatalog.Template> ladder,
            Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints) {
        if (ladder.isEmpty()) {
            return false;
        }
        Optional<Blueprint> plainest = blueprints.apply(ladder.get(0));
        if (plainest.isEmpty()) {
            return false;
        }
        for (Map.Entry<ResourceType, Integer> cost : priceOf(plainest.get()).entrySet()) {
            if (settlement.ledger().get(cost.getKey()) < cost.getValue() && canMake(settlement, cost.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static boolean canMake(Settlement settlement, ResourceType resource) {
        boolean mine = SettlementSimulator.placesFor(settlement, Occupation.MINER) > 0;
        return switch (resource) {
            case WOOD, FOOD -> true;
            case STONE -> mine || settlement.residents().stream().anyMatch(r -> r.occupation() == Occupation.MASON);
            case METAL -> mine;
            case FUEL -> mine || (settlement.residents().stream().anyMatch(r -> r.occupation().isSmith())
                    && settlement.residents().stream().anyMatch(r -> r.occupation() == Occupation.LUMBERJACK)); // coal, or charcoal
            default -> false;
        };
    }

    /**
     * The village wants a building and has a lot for it but cannot start: notes what it lacks (R4.20) and says so in the
     * history, now and then, so a player can see why nothing is happening (and donate what is missing).
     */
    private static void explainWhyNot(Settlement settlement, long day, BuildingType type, List<TemplateCatalog.Template> ladder,
            Function<TemplateCatalog.Template, Optional<Blueprint>> blueprints, VillagePlan.Lot lot) {
        if (ladder.isEmpty()) {
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
                    settlement.conditions().put(LACKS + resource.name(), day);
                    lacks.append(lacks.length() == 0 ? "" : ", ").append(units).append(' ')
                            .append(resource.name().toLowerCase(Locale.ROOT)).append(" (it has ").append(have).append(')');
                }
            });
            text = settlement.name() + " wants a " + name + " but cannot afford the plainest one yet: it needs "
                    + (lacks.length() == 0 ? "more of something" : lacks.toString()) + ".";
        }
        Long last = settlement.conditions().get(BLOCKED + ":" + type.name());
        if (last != null && day - last < BLOCKED_REMINDER_DAYS) {
            return;
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
