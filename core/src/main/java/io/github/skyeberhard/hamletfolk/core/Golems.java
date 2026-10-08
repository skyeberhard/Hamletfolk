package io.github.skyeberhard.hamletfolk.core;

import java.util.Optional;
import java.util.UUID;

/**
 * R5.9: a village that has been attacked keeps iron golems, one for every {@link #RESIDENTS_PER_GOLEM} residents (at least
 * one), forged by a smith from {@link #IRON} iron and a pumpkin's worth of produce, at most one every
 * {@link #COOLDOWN_DAYS} days. A forged golem waits to be put in the world (the Paper layer spawns it when the village is
 * loaded and records it with {@link #arrived}); one that is killed is forgotten ({@link #lost}) and forged again.
 */
public final class Golems {
    static final int RESIDENTS_PER_GOLEM = 10;
    /** Four blocks of iron, as a player would build one. */
    public static final int IRON = 36;
    /** A carved pumpkin, counted as produce. */
    public static final int PRODUCE = 1;
    static final int COOLDOWN_DAYS = 5;
    static final String OWNED = "golem:";
    static final String AWAITING = "golemsAwaiting";
    static final String FORGED = "golemForged";

    private Golems() {
    }

    /** How many golems the village wants: none until it has been attacked, then one per ten residents, at least one. */
    public static int wanted(Settlement settlement) {
        if (settlement.incidentsSince(0) == 0 || settlement.population() == 0) {
            return 0;
        }
        return Math.max(1, settlement.population() / RESIDENTS_PER_GOLEM);
    }

    /** Golems the village has in the world. */
    public static int owned(Settlement settlement) {
        return (int) settlement.conditions().keySet().stream().filter(k -> k.startsWith(OWNED)).count();
    }

    /** Golems forged that have not been put in the world yet. */
    public static int awaiting(Settlement settlement) {
        return (int) (long) settlement.conditions().getOrDefault(AWAITING, 0L);
    }

    /** A smith forges a golem today if the village wants one more and has the iron and a pumpkin. Returns the smith. */
    static Optional<Resident> forge(Settlement settlement, long day) {
        Long last = settlement.conditions().get(FORGED);
        if (owned(settlement) + awaiting(settlement) >= wanted(settlement) || (last != null && day - last < COOLDOWN_DAYS)
                || settlement.ledger().get(Commodity.IRON) < IRON || settlement.ledger().get(Commodity.PRODUCE) < PRODUCE) {
            return Optional.empty();
        }
        Optional<Resident> smith = settlement.residents().stream().filter(r -> r.adult() && r.occupation().isSmith()).findFirst();
        if (smith.isEmpty()) {
            return Optional.empty();
        }
        settlement.ledger().take(Commodity.IRON, IRON);
        settlement.ledger().take(Commodity.PRODUCE, PRODUCE);
        settlement.flow().recordConsumed(ResourceType.METAL, day, IRON);
        settlement.flow().recordConsumed(ResourceType.FOOD, day, PRODUCE);
        settlement.conditions().put(AWAITING, (long) awaiting(settlement) + 1);
        settlement.conditions().put(FORGED, day);
        settlement.record(day, HistoryEvent.Kind.MILESTONE, smith.get().fullName() + " forged an iron golem to guard "
                + settlement.name() + ".");
        return smith;
    }

    /** A forged golem is in the world: it is the village's from now on. */
    public static void arrived(Settlement settlement, UUID golem, long day) {
        long left = awaiting(settlement) - 1;
        if (left > 0) {
            settlement.conditions().put(AWAITING, left);
        } else {
            settlement.conditions().remove(AWAITING);
        }
        settlement.conditions().put(OWNED + golem, day);
    }

    /** True if this golem is one of the village's. */
    public static boolean owns(Settlement settlement, UUID golem) {
        return settlement.hasCondition(OWNED + golem);
    }

    /** One of the village's golems was killed: it is forgotten, and the history says so. */
    public static void lost(Settlement settlement, UUID golem, long day) {
        if (settlement.conditions().remove(OWNED + golem) != null) {
            settlement.record(day, HistoryEvent.Kind.DEATH, "An iron golem guarding " + settlement.name() + " was destroyed.");
        }
    }
}
