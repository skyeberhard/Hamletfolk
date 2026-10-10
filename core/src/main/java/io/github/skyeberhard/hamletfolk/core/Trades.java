package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * R8.13: the trades the land opens. Each is opened by something real within reach of the village (see {@link LandCounts})
 * once the village is the size for it, and is worked at a building that holds its workstation. A trade that is open and has
 * no such building is what the planner asks for; one that is open with a building is one the simulation staffs.
 */
public final class Trades {
    private Trades() {
    }

    /** What opens a trade: a land check and the population it needs. */
    private record Rule(Occupation trade, BuildingType building, int minPopulation, String reason) {
    }

    /** Village size (8) for a land trade; a library needs a town (20). */
    static final int VILLAGE = 8;
    static final int TOWN = 20;

    private static final List<Rule> RULES = List.of(
            new Rule(Occupation.FISHERMAN, BuildingType.HARBOUR, VILLAGE, "there is water to fish"),
            new Rule(Occupation.SHEPHERD, BuildingType.PENS, VILLAGE, "there are sheep to shear"),
            new Rule(Occupation.BUTCHER, BuildingType.SMOKEHOUSE, VILLAGE, "there are cattle, pigs and chickens to butcher"),
            new Rule(Occupation.LEATHERWORKER, BuildingType.TANNERY, VILLAGE, "there are cattle to give leather"),
            new Rule(Occupation.HORSE_TRAINER, BuildingType.STABLE, VILLAGE, "there are horses to train"),
            new Rule(Occupation.BEEKEEPER, BuildingType.APIARY, VILLAGE, "there are bees to keep"),
            new Rule(Occupation.CARTOGRAPHER, BuildingType.MAP_ROOM, VILLAGE, "there is sugar cane to make paper"),
            new Rule(Occupation.LIBRARIAN, BuildingType.LIBRARY, TOWN, "there is paper and leather to make books"),
            new Rule(Occupation.GLASSBLOWER, BuildingType.GLASSWORKS, VILLAGE, "there is sand to melt into glass"),
            // R8.15: the trades that need a smithy, a mine, a threat or a town
            new Rule(Occupation.FLETCHER, BuildingType.BOWYER, VILLAGE, "there is flint and there are feathers for arrows"),
            new Rule(Occupation.MASON, BuildingType.MASONS_YARD, VILLAGE, "there is stone to cut and a mine to supply it"),
            new Rule(Occupation.WEAPONSMITH, BuildingType.ARMOURY, VILLAGE, "it has a smithy and has been attacked lately"),
            new Rule(Occupation.ARMORER, BuildingType.ARMOURY, VILLAGE, "it has a smithy, metal to spare, and the need to arm itself"),
            new Rule(Occupation.CLERIC, BuildingType.CHAPEL, TOWN, "it is a town with people to care for"));

    /** True if the building is the workplace of a land trade (so the trade's rules, not a leaning's, decide when it is asked for). */
    public static boolean isTradeBuilding(BuildingType building) {
        return RULES.stream().anyMatch(r -> r.building() == building);
    }

    /** The trades this class governs. */
    public static List<Occupation> landTrades() {
        return RULES.stream().map(Rule::trade).toList();
    }

    /** The building that holds each trade's workstation (the first rule for a trade: an armoury holds two). */
    private static final java.util.Map<Occupation, BuildingType> BUILDING_FOR = new java.util.EnumMap<>(Occupation.class);

    static {
        for (Rule rule : RULES) {
            BUILDING_FOR.putIfAbsent(rule.trade(), rule.building());
        }
    }

    /** The building that holds a land trade's workstation, if the trade is one of these. */
    public static Optional<BuildingType> buildingFor(Occupation trade) {
        return Optional.ofNullable(BUILDING_FOR.get(trade));
    }

    /** True if the land within reach and the village's size open this trade (the building is a separate matter). */
    public static boolean opens(Settlement settlement, Occupation trade) {
        if (!LandCounts.surveyed(settlement) && !WORKSHOP_TRADES.contains(trade)) {
            return false; // nothing counted yet, so no land trade is open (and no lookup is made for it)
        }
        Optional<Rule> rule = RULES.stream().filter(r -> r.trade() == trade).findFirst();
        if (rule.isEmpty() || settlement.population() < rule.get().minPopulation()) {
            return false;
        }
        int sheep = LandCounts.get(settlement, LandCounts.Feature.SHEEP);
        int cattle = LandCounts.get(settlement, LandCounts.Feature.CATTLE);
        int pigs = LandCounts.get(settlement, LandCounts.Feature.PIGS);
        int chickens = LandCounts.get(settlement, LandCounts.Feature.CHICKENS);
        return switch (trade) {
            case FISHERMAN -> LandCounts.get(settlement, LandCounts.Feature.WATER) >= 40;
            case SHEPHERD -> sheep >= 4;
            case BUTCHER -> cattle + pigs + chickens >= 6;
            case LEATHERWORKER -> cattle >= 4;
            case HORSE_TRAINER -> LandCounts.get(settlement, LandCounts.Feature.HORSES) >= 3;
            case BEEKEEPER -> LandCounts.get(settlement, LandCounts.Feature.BEES) >= 2;
            case CARTOGRAPHER -> LandCounts.get(settlement, LandCounts.Feature.SUGAR_CANE) >= 16;
            case LIBRARIAN -> LandCounts.get(settlement, LandCounts.Feature.SUGAR_CANE) >= 8 && cattle >= 2;
            case GLASSBLOWER -> LandCounts.get(settlement, LandCounts.Feature.SAND) >= 30;
            case FLETCHER -> LandCounts.get(settlement, LandCounts.Feature.GRAVEL) >= 16 && chickens >= 3;
            case MASON -> LandCounts.get(settlement, LandCounts.Feature.STONE) >= 40 && settlement.buildingCount(BuildingType.MINE) > 0;
            case WEAPONSMITH -> settlement.buildingCount(BuildingType.SMITHY) > 0
                    && settlement.incidentDaysSince(settlement.lastSimulatedDay() - Planner.THREAT_MEMORY_DAYS) > 0;
            case ARMORER -> settlement.buildingCount(BuildingType.SMITHY) > 0 && settlement.ledger().get(ResourceType.METAL) >= 20
                    && (settlement.population() >= TOWN || VillageCharacter.temperament(settlement) == VillageCharacter.Temperament.MARTIAL);
            case CLERIC -> true; // (the size is in the rule)
            default -> false;
        };
    }

    /** Condition key marking that a trade's opening has been written up. */
    private static final String NOTED = "tradeOpened:";

    /** Writes up, once each, the land trades that have just opened, and a leaning the counts have just given. Returns how many. */
    public static int noteOpened(Settlement settlement, long day) {
        int noted = 0;
        Leaning leaning = settlement.leaning();
        String leaningKey = NOTED + "leaning:" + leaning.name();
        if (leaning.ordinal() > Leaning.ALL_ROUND.ordinal() && !settlement.conditions().containsKey(leaningKey)) {
            settlement.conditions().put(leaningKey, day);
            settlement.record(day, HistoryEvent.Kind.MILESTONE, "What the land round " + settlement.name() + " offers (" + LandCounts.describe(settlement)
                    + ") gives it " + leaning.reason() + ": it is a " + leaning.label() + " village.");
            noted++;
        }
        for (Rule rule : RULES) {
            String key = NOTED + rule.trade().name();
            if (!settlement.conditions().containsKey(key) && opens(settlement, rule.trade())) {
                settlement.conditions().put(key, day);
                settlement.record(day, HistoryEvent.Kind.MILESTONE, settlement.name() + " could now support a " + rule.trade().title()
                        + ": " + rule.reason() + ". It needs a " + rule.building().label().toLowerCase(java.util.Locale.ROOT) + ".");
                noted++;
            }
        }
        return noted;
    }

    /** The trades that open on something other than the land counts (a smithy, a mine, an attack, a town). */
    private static final java.util.Set<Occupation> WORKSHOP_TRADES = java.util.EnumSet.of(Occupation.MASON, Occupation.WEAPONSMITH,
            Occupation.ARMORER, Occupation.CLERIC);

    /** True if the trade is open and the village has the building that holds its workstation. */
    public static boolean worked(Settlement settlement, Occupation trade) {
        BuildingType building = BUILDING_FOR.get(trade); // (called for every worker every day: no stream, no Optional)
        return building != null && settlement.buildingCount(building) > 0;
    }

    /** The trade buildings the village could use now: the trade is open and it has none. In a stable order. */
    public static List<BuildingType> wanted(Settlement settlement) {
        List<BuildingType> out = new ArrayList<>();
        for (Rule rule : RULES) {
            if (opens(settlement, rule.trade()) && settlement.buildingCount(rule.building()) == 0 && !out.contains(rule.building())) {
                out.add(rule.building()); // (an armoury is wanted once, for either of its trades)
            }
        }
        return out;
    }

    /** Why a trade's building is wanted, in the planner's words. */
    public static String reasonFor(BuildingType building) {
        return RULES.stream().filter(r -> r.building() == building).findFirst()
                .map(r -> r.reason() + ", and a " + building.label().toLowerCase(java.util.Locale.ROOT) + " is where its "
                        + r.trade().title() + " works")
                .orElse("the land offers a trade");
    }
}
