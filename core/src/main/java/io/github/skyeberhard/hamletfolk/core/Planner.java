package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * R8.1: the village's planner, the decision layer on top of the simulation. Once a day it reads the ledger and the
 * village's needs and says what the village wants next and why, working through four tiers in order (food,
 * shelter, safety, then trade and growth) and not moving on until the one before is met. A tier counts as met at
 * {@link #MET} and slips at {@link #SLIPS}, so priorities do not flap with a day's luck. For a missing resource it
 * walks a production dependency graph back to the root (tools need a smithy, which needs metal, which needs a
 * mine). Every decision goes into a bounded log with its reason.
 *
 * <p>For now the directives are advice, shown by {@code /settlement plan} and in dialogue: lots and the builder
 * that acts on them come later (R8.3, R4.8). The planner never changes the ledger or the residents; it is
 * deterministic for a given settlement and day.
 */
public final class Planner {
    /** A tier counts as met when its ratio reaches this... */
    static final double MET = 1.0;
    /** ...and slips when it falls below this. */
    static final double SLIPS = 0.7;
    /** A village nobody has visited for this many days plans at the slow pace. */
    public static final int AUTOPILOT_AFTER_DAYS = 7;
    /** On autopilot the planner runs on every day that is a multiple of this. */
    static final int AUTOPILOT_EVERY = 3;
    /** The same decision is not logged again within this many days. */
    static final int REPEAT_QUIET_DAYS = 10;
    static final String MET_PREFIX = "planner:met:";
    static final String VISITED = "visited";
    static final String PLANNED = "plannedDay";

    private Planner() {
    }

    public enum Tier {
        /** R4.20: SUPPLY (a mine, for stone and metal) is asked for alongside the tier after food, not as a gate. */
        FOOD, SUPPLY, SHELTER, SAFETY, GROWTH;

        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Kind {
        /** Put up a building of a kind (a lot is reserved once lots exist). */
        BUILD,
        /** A building has places that nobody holds yet. */
        OPEN_JOB,
        /** The village cannot make it (yet) and needs it brought in. */
        IMPORT,
        /** Nothing can be done today; the tier is working its way back. */
        WAIT
    }

    /** What the village has decided to do next, and why. {@code target} names a building, occupation or resource. */
    public record Directive(Tier tier, Kind kind, String target, String reason) {
        /** e.g. "Build a mine: guards have no tools; metal comes from a mine; the village has none." */
        public String text() {
            String name = target.toLowerCase(Locale.ROOT).replace('_', ' ');
            String text = switch (kind) {
                case BUILD -> (BuildingType.fromTarget(target).filter(BuildingType::isWorks).isPresent() ? "Put up the " : "Build a ")
                    + name + ": " + reason;
                case OPEN_JOB -> "Take on " + name + "s: " + reason;
                case IMPORT -> "Bring in " + name + ": " + reason;
                case WAIT -> "Waiting on " + name + ": " + reason;
            };
            return text.endsWith(".") ? text : text + ".";
        }

        /** What makes the same decision the same: its tier, kind and target, not the numbers in its reason. */
        public String key() {
            return tier.name() + "|" + kind.name() + "|" + target;
        }
    }

    /** One line in the decision log: the day, the tier it served, what was decided and why. {@code key} says which decision it was. */
    public record Decision(long day, Tier tier, String key, String text) {
    }

    /** Records that a player has been to this village, which keeps the planner at its normal pace. */
    public static void visited(Settlement settlement, long day) {
        settlement.conditions().put(VISITED, day);
    }

    /** True when somebody has visited before but not for a week, so the planner runs at its slow pace. */
    static boolean onAutopilot(Settlement settlement, long day) {
        Long visited = settlement.conditions().get(VISITED);
        return visited != null && day - visited > AUTOPILOT_AFTER_DAYS;
    }

    // ----- how well each tier is doing, 0 to 2 (1 is just enough) -----

    static double ratio(Settlement settlement, Tier tier, long day) {
        int population = settlement.population();
        if (population == 0) {
            return 1.0;
        }
        return switch (tier) {
            case FOOD -> settlement.hasCondition("famine") ? 0.0
                    : Math.min(2.0, (double) settlement.ledger().get(ResourceType.FOOD)
                            / PriceModel.wanted(settlement, ResourceType.FOOD));
            // Beds nobody has counted yet are not the same as no beds: say nothing until they have been.
            case SHELTER -> settlement.housing().counted()
                    ? Math.min(2.0, (double) settlement.housingCapacity() / population) : 1.0;
            case SAFETY -> safetyRatio(settlement, day);
            case SUPPLY -> settlement.buildingCount(BuildingType.MINE) > 0 ? 1.0 : 0.0;
            case GROWTH -> 2.0;
        };
    }

    private static double safetyRatio(Settlement settlement, long day) {
        SettlementSimulator.Alert alert = SettlementSimulator.alertLevel(settlement, day);
        int wanted = SettlementSimulator.guardsWanted(settlement, alert);
        if (wanted == 0) {
            return 1.0;
        }
        double manned = Math.min(1.0, (double) guards(settlement) / wanted);
        return settlement.ledger().get(ResourceType.TOOLS) > 0 ? manned : manned * 0.6;
    }

    private static long guards(Settlement settlement) {
        return settlement.residents().stream().filter(r -> r.adult() && r.occupation() == Occupation.GUARD).count();
    }

    /** Whether the tier is met now: a met tier stays met down to {@link #SLIPS}, an unmet one needs {@link #MET}. */
    static boolean met(Settlement settlement, Tier tier, long day) {
        double ratio = ratio(settlement, tier, day);
        boolean was = settlement.hasCondition(MET_PREFIX + tier.name());
        return was ? ratio >= SLIPS : ratio >= MET;
    }

    /**
     * Whether a tier is met for the purpose of saying what comes next. Once the planner has run, its saved answer
     * (which carries the hysteresis); before that (a village just adopted or just upgraded) the numbers as they are.
     */
    private static boolean metForAdvice(Settlement settlement, Tier tier, long day) {
        if (settlement.conditions().containsKey(PLANNED)) {
            return settlement.hasCondition(MET_PREFIX + tier.name());
        }
        return ratio(settlement, tier, day) >= MET;
    }

    // ----- the daily run -----

    /**
     * Runs the planner for a day: updates which tiers are met, works out what the village wants next, and logs any
     * new decision with its reason. Returns the directives. Does nothing (and returns none) on a day an autopilot village
     * skips, and runs at most once for a day. {@code treasuryLimit} is the most emeralds the village can bank (R2.6).
     */
    public static List<Directive> run(Settlement settlement, long day, int treasuryLimit) {
        Long planned = settlement.conditions().get(PLANNED);
        if (settlement.population() == 0 || (planned != null && planned >= day)
                || (onAutopilot(settlement, day) && day % AUTOPILOT_EVERY != 0)) {
            return List.of();
        }
        settlement.conditions().put(PLANNED, day);
        for (Tier tier : new Tier[] {Tier.FOOD, Tier.SHELTER, Tier.SAFETY}) {
            if (met(settlement, tier, day)) {
                settlement.conditions().putIfAbsent(MET_PREFIX + tier.name(), day);
            } else {
                settlement.conditions().remove(MET_PREFIX + tier.name());
            }
        }
        List<Directive> directives = directives(settlement, day, treasuryLimit);
        for (Directive directive : directives) {
            settlement.recordDecision(day, directive.tier(), directive.key(), directive.text(), REPEAT_QUIET_DAYS);
        }
        return directives;
    }

    /**
     * What the village wants next: the directives for the first tier that is not met (the last tier, growth, is
     * always open). A tier that is not met always says something, even if only that it is waiting, so the plan never
     * moves on while one is open. Reads the saved state and changes nothing.
     */
    public static List<Directive> directives(Settlement settlement, long day, int treasuryLimit) {
        if (settlement.population() == 0) {
            return List.of();
        }
        for (Tier tier : new Tier[] {Tier.FOOD, Tier.SHELTER, Tier.SAFETY}) {
            if (!metForAdvice(settlement, tier, day)) {
                List<Directive> found = switch (tier) {
                    case FOOD -> food(settlement);
                    case SHELTER -> shelter(settlement);
                    case SAFETY -> safety(settlement, day);
                    case SUPPLY, GROWTH -> List.of();
                };
                List<Directive> out = found.isEmpty() ? List.of(new Directive(tier, Kind.WAIT, tier.label(), "it is at "
                        + Math.round(ratio(settlement, tier, day) * 100) + "% and the village is working back to full")) : found;
                return withLots(settlement, tier == Tier.FOOD ? out : withDefence(settlement, day, withSupply(settlement, out)));
            }
        }
        return withLots(settlement, withDefence(settlement, day, withSupply(settlement, growth(settlement, treasuryLimit))));
    }

    /**
     * R5.6: once a village has been attacked it wants lights along its streets, and when they stand, a palisade round the
     * village. Both go first in the list: the lights are cheap, and the sooner they stand the fewer monsters spawn. Not asked
     * for again once built (or while a project for them is open). A starving village sees food first, as for everything else.
     */
    private static List<Directive> withDefence(Settlement settlement, long day, List<Directive> rest) {
        int attacks = settlement.incidentsSince(0);
        if (attacks == 0 || settlement.plan() == null) {
            return rest;
        }
        List<Directive> out = new ArrayList<>();
        int stage = settlement.plan().stage(); // R5.8: each stage of the plan gets its own lights and ring
        boolean grown = Construction.worksDone(settlement, BuildingType.PALISADE, 1);
        if (!Construction.hasWorks(settlement, BuildingType.STREET_LIGHTS, stage)) {
            out.add(new Directive(Tier.SAFETY, Kind.BUILD, "street_lights", grown
                    ? "the village has grown, and its new streets are dark"
                    : "the village has been attacked, and monsters spawn where it is dark"));
        } else if (Construction.worksDone(settlement, BuildingType.STREET_LIGHTS, stage)
                && !Construction.hasWorks(settlement, BuildingType.PALISADE, stage)) {
            out.add(new Directive(Tier.SAFETY, Kind.BUILD, "palisade", grown
                    ? "the village has grown past its palisade"
                    : "the village has been attacked " + (attacks == 1 ? "once" : attacks + " times")
                            + ", and a fence keeps monsters from walking in"));
        }
        // R5.10: a town that has been threatened strengthens the wall it has, a tier at a time.
        if (Construction.worksDone(settlement, BuildingType.PALISADE, stage) && !Construction.rampartOpen(settlement)) {
            int built = Construction.wallTier(settlement, stage);
            int wanted = wallTierWanted(settlement, day);
            if (wanted > built) {
                int next = Math.max(Rampart.FIRST_TIER, built + 1);
                out.add(new Directive(Tier.SAFETY, Kind.BUILD, "rampart", "it is a " + VillageCharacter.Stage.of(settlement.population()).label()
                        + " that has been attacked lately, and " + (next == Rampart.FIRST_TIER
                                ? "a wall of planks with gates and watch towers keeps out more than a fence"
                                : "a wall of stone outlasts one of wood")));
            }
        }
        out.addAll(rest);
        return out;
    }

    /**
     * R5.10: the strongest wall a village wants: none beyond the fence unless it has been attacked within the last three
     * months; then planks for a town (20 residents) and stone for a city (50). (A martial or wary village has been attacked
     * lately by definition.)
     */
    static int wallTierWanted(Settlement settlement, long day) {
        boolean threatened = settlement.incidentDaysSince(day - THREAT_MEMORY_DAYS) > 0;
        if (!threatened) {
            return 1;
        }
        VillageCharacter.Stage size = VillageCharacter.Stage.of(settlement.population());
        return size == VillageCharacter.Stage.CITY ? Rampart.LAST_TIER : size == VillageCharacter.Stage.TOWN ? Rampart.FIRST_TIER : 1;
    }

    /** Days after an attack that a village still counts as threatened when it considers its walls. */
    static final int THREAT_MEMORY_DAYS = 90;

    /**
     * R4.20: once food is covered, a village with no mine wants one ahead of everything else, as stone and metal come
     * from nowhere else. It goes first in the list rather than holding the rest up, so a village that cannot build a mine
     * yet still gets on with its houses.
     */
    private static List<Directive> withSupply(Settlement settlement, List<Directive> rest) {
        if (settlement.buildingCount(BuildingType.MINE) > 0) {
            return rest;
        }
        List<Directive> out = new ArrayList<>();
        out.add(new Directive(Tier.SUPPLY, Kind.BUILD, "mine", "stone and metal come from a mine; the village has none"));
        out.addAll(rest);
        return out;
    }

    /** R8.3: a directive to build something says which reserved lot it would go on, once the village has a plan. */
    private static List<Directive> withLots(Settlement settlement, List<Directive> directives) {
        VillagePlan plan = settlement.plan();
        if (plan == null) {
            return directives;
        }
        List<Directive> out = new ArrayList<>();
        for (Directive d : directives) {
            out.add(d.kind() == Kind.BUILD ? withLot(plan, d) : d);
        }
        return out;
    }

    private static Directive withLot(VillagePlan plan, Directive d) {
        try {
            return plan.nextLot(BuildingType.valueOf(d.target().toUpperCase(Locale.ROOT)))
                    .map(lot -> new Directive(d.tier(), d.kind(), d.target(), d.reason() + ". Lot reserved at "
                            + lot.rect().centerX() + ", " + lot.rect().centerZ() + " (" + lot.rect().width() + " by "
                            + lot.rect().depth() + ")"))
                    .orElse(d);
        } catch (IllegalArgumentException e) {
            return d; // not a building kind
        }
    }

    // ----- the tiers -----

    private static List<Directive> food(Settlement s) {
        boolean famine = s.hasCondition("famine");
        String why = famine ? "the village is going hungry" : "food is short";
        List<Directive> out = new ArrayList<>();
        Directive farm = needBuilding(s, Tier.FOOD, BuildingType.FARM, Occupation.FARMER, why + "; food comes from farms");
        out.add(farm);
        if (famine && farm.kind() == Kind.BUILD) {
            out.add(new Directive(Tier.FOOD, Kind.IMPORT, "food", "until a farm is running, food has to be brought in"));
        }
        return out;
    }

    private static List<Directive> shelter(Settlement s) {
        int missing = Math.max(1, s.population() - s.housingCapacity());
        return List.of(new Directive(Tier.SHELTER, Kind.BUILD, "house", s.population() + " residents but only "
                + s.housingCapacity() + " beds; " + missing + (missing == 1 ? " more bed" : " more beds") + " would house the rest"));
    }

    private static List<Directive> safety(Settlement s, long day) {
        SettlementSimulator.Alert alert = SettlementSimulator.alertLevel(s, day);
        String situation = alert == SettlementSimulator.Alert.CALM ? "the village has no tools to arm guards with"
                : "the village is " + alert.label() + " and its guards have no tools";
        List<Directive> out = new ArrayList<>();
        if (s.ledger().get(ResourceType.TOOLS) == 0) {
            Directive blocker = rootBlocker(s, Tier.SAFETY, ResourceType.TOOLS, situation);
            out.add(blocker);
            if (blocker.kind() == Kind.BUILD) {
                out.add(new Directive(Tier.SAFETY, Kind.IMPORT, "tools", "until tools can be made, they have to be brought in"));
            }
        }
        int wanted = SettlementSimulator.guardsWanted(s, alert);
        long guards = guards(s);
        if (guards < wanted) {
            boolean foodShort = s.hasCondition("famine") || ratio(s, Tier.FOOD, day) < MET;
            out.add(new Directive(Tier.SAFETY, Kind.OPEN_JOB, "guard", "the village is " + alert.label() + " and wants "
                    + wanted + ", it has " + guards + (foodShort && alert != SettlementSimulator.Alert.SIEGE
                    ? "; nobody can be spared while food is short" : "; it needs a brave adult who is free to take up arms")));
        }
        if (alert.compareTo(SettlementSimulator.Alert.ALARMED) >= 0 && s.buildingCount(BuildingType.GUARD_POST) == 0) {
            out.add(new Directive(Tier.SAFETY, Kind.BUILD, "guard_post",
                    "the village is " + alert.label() + " and has nowhere to gather its guards"));
        }
        return out;
    }

    private static List<Directive> growth(Settlement s, int limit) {
        List<Directive> out = new ArrayList<>();
        if (s.buildingCount(BuildingType.SHOP) == 0 && hasSurplus(s)) {
            out.add(new Directive(Tier.GROWTH, Kind.BUILD, "shop", "the village has more than it needs and no one to sell it"));
        }
        int banked = SettlementSimulator.banked(s);
        if (s.buildingCount(BuildingType.TREASURY) == 0 && limit > 0 && banked * 10L >= limit * 6L) {
            out.add(new Directive(Tier.GROWTH, Kind.BUILD, "treasury", "the treasury is more than half full (" + banked
                    + " of " + limit + ") and a treasury building raises the limit"));
        }
        if (out.isEmpty() && s.freeBeds() == 0 && s.housing().counted() && s.housingCapacity() > 0) {
            out.add(new Directive(Tier.GROWTH, Kind.BUILD, "house", "every bed is taken, and a spare bed is what lets newcomers settle"));
        }
        // R8.12: what the land calls for, once the village is big enough to afford the room: one of its signature building.
        BuildingType signature = s.leaning().signature();
        // (still asked for while one is queued or being built: the planner dropping the ask would cancel the queued project)
        if (signature != null && !Trades.isTradeBuilding(signature) // (a trade building is asked for below, once its trade is open)
                && s.population() >= VillageCharacter.Stage.VILLAGE.from() && s.buildingCount(signature) == 0) {
            out.add(new Directive(Tier.GROWTH, Kind.BUILD, signature.name().toLowerCase(Locale.ROOT),
                    "it is a " + s.leaning().label() + " village, and " + switch (signature) {
                        case SAWMILL -> "a sawmill makes its lumberjacks' wood go a quarter further";
                        case FORGE -> "a forge lets each smith smelt four more ore a day";
                        case HARBOUR -> "a harbour is where its fishermen work, and they land a quarter more";
                        case PENS -> "pens are where its shepherds work, and they shear a quarter more";
                        default -> "a granary halves how fast its food spoils";
                    }));
        }
        // R8.13: what the land offers: the first trade that is open and has no building to work in. (Still asked for while one is
        // queued or being built, as above.)
        // All of them, in a stable order: one the village cannot pay for does not hide the others.
        List<BuildingType> trades = Trades.wanted(s);
        for (BuildingType building : trades) {
            out.add(new Directive(Tier.GROWTH, Kind.BUILD, building.name().toLowerCase(Locale.ROOT), Trades.reasonFor(building)));
        }
        if (trades.isEmpty() && (s.buildingCount(BuildingType.TRADING_POST) == 0 && s.buildingCount(BuildingType.SHOP) > 0 && s.population() >= 12
                && s.residents().stream().anyMatch(r -> r.adult() && r.occupation() == Occupation.MERCHANT))) {
            out.add(new Directive(Tier.GROWTH, Kind.BUILD, "trading_post",
                    "it has merchants and a shop, and a trading post gives each merchant one more sale a day"));
        }
        return out;
    }

    private static boolean hasSurplus(Settlement s) {
        for (ResourceType type : ResourceType.values()) {
            if (SettlementSimulator.surplus(s, type) >= SettlementSimulator.unitsPerEmerald(type)) {
                return true;
            }
        }
        return false;
    }

    // ----- the production dependency graph -----

    /**
     * For a job that needs a building (a farmer needs a farm): build one if the village has none or every place is
     * taken, otherwise say the places are open for someone to take.
     */
    private static Directive needBuilding(Settlement s, Tier tier, BuildingType building, Occupation job, String why) {
        String site = building.label().toLowerCase(Locale.ROOT);
        int places = SettlementSimulator.placesFor(s, job);
        long holders = s.residents().stream().filter(r -> r.adult() && r.occupation() == job).count();
        if (places == 0) {
            return new Directive(tier, Kind.BUILD, building.name().toLowerCase(Locale.ROOT), why + "; the village has no " + site);
        }
        if (holders >= places) {
            return new Directive(tier, Kind.BUILD, building.name().toLowerCase(Locale.ROOT),
                    why + "; every place at the " + site + " is taken");
        }
        long free = places - holders;
        return new Directive(tier, Kind.OPEN_JOB, job.name().toLowerCase(Locale.ROOT),
                why + "; the " + site + " has " + free + " free " + (free == 1 ? "place" : "places"));
    }

    /**
     * Walks back from something that is short to the thing that is actually missing: tools need a smithy, a smithy needs
     * metal, metal needs a mine. Returns the directive for the first thing in the chain that is missing; if the whole
     * chain is in place, the open jobs that would make it.
     */
    static Directive rootBlocker(Settlement s, Tier tier, ResourceType lacking, String why) {
        switch (lacking) {
            case TOOLS -> {
                if (s.buildingCount(BuildingType.SMITHY) == 0) {
                    return new Directive(tier, Kind.BUILD, "smithy", why + "; tools come from a smithy, and the village has none");
                }
                if (s.ledger().get(ResourceType.METAL) == 0) {
                    return rootBlocker(s, tier, ResourceType.METAL, why + "; the smithy has no metal to work");
                }
                // R3.17: ore that cannot be smelted for want of fuel
                if (s.ledger().get(Commodity.IRON) == 0 && s.ledger().get(Commodity.RAW_IRON) > 0 && s.ledger().get(ResourceType.FUEL) == 0) {
                    return new Directive(tier, Kind.IMPORT, "fuel", why + "; the smith has raw iron but no coal or charcoal to smelt it");
                }
                return needBuilding(s, tier, BuildingType.SMITHY, Occupation.TOOLSMITH, why);
            }
            case METAL -> {
                return needBuilding(s, tier, BuildingType.MINE, Occupation.MINER, why + "; metal comes from a mine");
            }
            case STONE -> {
                return needBuilding(s, tier, BuildingType.MINE, Occupation.MINER, why + "; stone comes from a mine");
            }
            case FOOD -> {
                return needBuilding(s, tier, BuildingType.FARM, Occupation.FARMER, why + "; food comes from a farm");
            }
            default -> {
                return new Directive(tier, Kind.IMPORT, lacking.name().toLowerCase(Locale.ROOT), why);
            }
        }
    }
}
