package io.github.skyeberhard.hamletfolk.core;

import java.util.Optional;

/**
 * R4.34: travellers come to settle in a village that has lost its people, sent by an admin. The village keeps its buildings, plan
 * and history; what it lacks is anyone to run them, and it cannot get anyone by itself: a newcomer needs a resident already there
 * and twenty food a head (R4.1), and an abandoned village is never simulated or visited by newcomers again. The settlers bring a
 * kit of food, wood and stone like a founder's, so the first days are fed and the first building can be paid for, and a village
 * that was empty starts clear of the threat it was left with (frozen while it is abandoned); attacks in the last week still count, so a
 * village raided yesterday is not safe for newcomers yet. The Minecraft layer
 * spawns the villagers; the rules are here.
 */
public final class Resettlement {
    private Resettlement() {
    }

    /** How many travellers an admin sends by default, and at most: enough for a farmer, a builder and a spare pair of hands. */
    public static final int DEFAULT_SETTLERS = 3;
    public static final int MAX_SETTLERS = 6;
    /** A village with more people than this has not lost its people: it is left to grow by itself. */
    public static final int MAX_EXISTING = 3;
    /** What each traveller brings: food for fifteen days of an adult's eating, and the wood and stone of a founder's kit. */
    public static final int FOOD_PER_SETTLER = 30;
    public static final int WOOD_PER_SETTLER = 30;
    public static final int STONE_PER_SETTLER = 15;

    /** Why a village cannot be resettled, or empty if it can. */
    public static Optional<String> refusal(Settlement settlement) {
        if (settlement.population() > MAX_EXISTING) {
            return Optional.of(settlement.name() + " still has " + settlement.population() + " people: resettling is for villages that have lost "
                    + "theirs (" + MAX_EXISTING + " or fewer).");
        }
        return Optional.empty();
    }

    /** Whether a village is lost: nobody lives there, so its danger is as it was left. */
    public static boolean isLost(Settlement settlement) {
        return settlement.population() == 0 || settlement.isAbandoned();
    }

    /** How many settlers to send: what was asked, within 1 to {@link #MAX_SETTLERS}. */
    public static int settlers(int asked) {
        return Math.max(1, Math.min(MAX_SETTLERS, asked));
    }

    /**
     * The travellers have arrived ({@code settlers} of them were spawned and enrolled): the kit goes into the stores, the danger a
     * lost village was left with is cleared (a village that still had people keeps its own), and the history says so. The village
     * stops being abandoned at once.
     */
    public static void arrive(Settlement settlement, long day, int settlers, boolean wasLost) {
        Ledger ledger = settlement.ledger();
        ledger.add(Commodity.BREAD, FOOD_PER_SETTLER * settlers);
        ledger.add(ResourceType.WOOD, WOOD_PER_SETTLER * settlers);
        ledger.add(ResourceType.STONE, STONE_PER_SETTLER * settlers);
        if (wasLost) {
            settlement.setThreat(0);
            // The simulation clears these on the village's next day, which can be twenty minutes away; until then the village would
            // be treated as abandoned (not planned, no newcomers) with people living in it.
            settlement.conditions().remove("emptySince");
            settlement.conditions().remove("abandoned");
        }
        settlement.record(day, HistoryEvent.Kind.MILESTONE, (settlers == 1 ? "A traveller" : settlers + " travellers")
                + " came to settle in " + settlement.name() + (wasLost ? ", which had been left empty" : "")
                + ", bringing food, wood and stone.");
    }
}
