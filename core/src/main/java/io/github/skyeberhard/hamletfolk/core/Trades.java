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
            new Rule(Occupation.GLASSBLOWER, BuildingType.GLASSWORKS, VILLAGE, "there is sand to melt into glass"));

    /** True if the building is the workplace of a land trade (so the trade's rules, not a leaning's, decide when it is asked for). */
    public static boolean isTradeBuilding(BuildingType building) {
        return RULES.stream().anyMatch(r -> r.building() == building);
    }

    /** The trades this class governs. */
    public static List<Occupation> landTrades() {
        return RULES.stream().map(Rule::trade).toList();
    }

    /** The building that holds a land trade's workstation, if the trade is one of these. */
    public static Optional<BuildingType> buildingFor(Occupation trade) {
        return RULES.stream().filter(r -> r.trade() == trade).map(Rule::building).findFirst();
    }

    /** True if the land within reach and the village's size open this trade (the building is a separate matter). */
    public static boolean opens(Settlement settlement, Occupation trade) {
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

    /** True if the trade is open and the village has the building that holds its workstation. */
    public static boolean worked(Settlement settlement, Occupation trade) {
        return buildingFor(trade).filter(b -> settlement.buildingCount(b) > 0).isPresent();
    }

    /** The trade buildings the village could use now: the trade is open and it has none. In a stable order. */
    public static List<BuildingType> wanted(Settlement settlement) {
        List<BuildingType> out = new ArrayList<>();
        for (Rule rule : RULES) {
            if (opens(settlement, rule.trade()) && settlement.buildingCount(rule.building()) == 0) {
                out.add(rule.building());
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
