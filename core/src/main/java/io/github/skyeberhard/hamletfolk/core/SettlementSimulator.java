package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Advances a settlement one in-game day at a time. Runs purely on data, so it costs the
 * same whether or not any villager is loaded. Deterministic for a given settlement and day.
 */
public final class SettlementSimulator {
    static final int ADULT_FOOD_PER_DAY = 2;
    static final int CHILD_FOOD_PER_DAY = 1;
    static final double THREAT_DECAY = 0.9;
    static final int[] POPULATION_MILESTONES = {10, 25, 50, 100, 250};
    static final int ABANDONMENT_DAYS = 10;

    /**
     * Simulates every day from the settlement's last simulated day up to {@code targetDay}.
     * If more than {@code maxDays} are pending, the oldest are skipped rather than replayed.
     *
     * @return the number of days simulated
     */
    public int simulateTo(Settlement settlement, long targetDay, int maxDays) {
        if (targetDay - settlement.lastSimulatedDay() > maxDays) {
            settlement.setLastSimulatedDay(targetDay - maxDays);
        }
        int simulated = 0;
        while (settlement.lastSimulatedDay() < targetDay) {
            long day = settlement.lastSimulatedDay() + 1;
            simulateDay(settlement, day);
            settlement.setLastSimulatedDay(day);
            simulated++;
        }
        return simulated;
    }

    void simulateDay(Settlement settlement, long day) {
        if (updateAbandonment(settlement, day)) {
            return; // R1.5: no residents for ABANDONMENT_DAYS straight; nothing left to simulate.
        }
        Random random = new Random(settlement.id().getMostSignificantBits() ^ (day * 0x9E3779B97F4A7C15L));
        Ledger ledger = settlement.ledger();

        Map<ResourceType, Integer> idleForLack = new EnumMap<>(ResourceType.class);
        for (Resident resident : workOrder(settlement)) {
            work(resident, ledger, day, random, idleForLack);
        }

        int demand = 0;
        for (Resident resident : settlement.residents()) {
            demand += resident.adult() ? ADULT_FOOD_PER_DAY : CHILD_FOOD_PER_DAY;
        }
        int eaten = ledger.take(ResourceType.FOOD, demand);
        int shortfall = demand - eaten;
        double fedFraction = demand == 0 ? 1.0 : (double) eaten / demand;

        settlement.setThreat(settlement.threat() * THREAT_DECAY);
        for (Resident resident : settlement.residents()) {
            Needs needs = resident.needs();
            needs.adjustFood(fedFraction >= 1.0 ? 10 : -(int) Math.ceil(25 * (1 - fedFraction)));
            double nerve = 1.5 - resident.traits().bravery() / 100.0;
            needs.setSafety((int) Math.round(100 - settlement.threat() * nerve));
        }

        updateFamine(settlement, day, shortfall);
        updateShortages(settlement, day, idleForLack);
        updateMilestones(settlement, day);
    }

    /**
     * R1.20: the order residents claim scarce inputs in. Whoever went without most recently
     * goes first, so when there isn't enough for everyone the shortfall rotates round-robin
     * instead of always landing on the most recently enrolled. Stable, so it stays deterministic.
     */
    static List<Resident> workOrder(Settlement settlement) {
        List<Resident> order = new ArrayList<>(settlement.residents());
        order.sort(Comparator.comparingLong(Resident::lastBlockedDay).reversed());
        return order;
    }

    private static void work(Resident resident, Ledger ledger, long day, Random random,
                             Map<ResourceType, Integer> idleForLack) {
        Occupation occupation = resident.occupation();
        if (!resident.adult() || occupation.produces() == null) {
            return;
        }
        ResourceType input = occupation.consumes();
        if (input != null && ledger.take(input, 1) == 0) {
            resident.setLastBlockedDay(day);
            resident.needs().adjustPurpose(-8);
            idleForLack.merge(input, 1, Integer::sum);
            return;
        }
        double diligence = 0.5 + resident.traits().workEthic() / 100.0;
        int output = (int) Math.floor(occupation.baseOutput() * diligence + random.nextDouble());
        ledger.add(occupation.produces(), output);
        resident.needs().adjustPurpose(4);
    }

    /**
     * Tracks how long a settlement has had no residents. Marks it abandoned once that streak
     * reaches {@code ABANDONMENT_DAYS}, and clears the mark (with a history entry) the moment
     * someone lives there again.
     *
     * @return true if the settlement is abandoned and still empty as of this day
     */
    private static boolean updateAbandonment(Settlement settlement, long day) {
        Map<String, Long> conditions = settlement.conditions();
        if (settlement.population() == 0) {
            Long emptySince = conditions.get("emptySince");
            if (emptySince == null) {
                conditions.put("emptySince", day);
            } else if (day - emptySince >= ABANDONMENT_DAYS - 1 && !conditions.containsKey("abandoned")) {
                conditions.put("abandoned", day);
                settlement.record(day, HistoryEvent.Kind.ABANDONED, settlement.name()
                        + " was abandoned. No one has lived there for " + ABANDONMENT_DAYS + " days.");
            }
            return conditions.containsKey("abandoned");
        }
        conditions.remove("emptySince");
        if (conditions.remove("abandoned") != null) {
            settlement.record(day, HistoryEvent.Kind.MILESTONE, settlement.name() + " was resettled.");
        }
        return false;
    }

    private static void updateFamine(Settlement settlement, long day, int shortfall) {
        Map<String, Long> conditions = settlement.conditions();
        if (shortfall > 0 && !conditions.containsKey("famine")) {
            conditions.put("famine", day);
            settlement.record(day, HistoryEvent.Kind.FAMINE,
                    "The food stores ran empty. " + settlement.population() + " people went hungry.");
        } else if (shortfall == 0 && conditions.containsKey("famine")) {
            long days = day - conditions.remove("famine");
            settlement.record(day, HistoryEvent.Kind.RECOVERY,
                    "The famine ended after " + days + (days == 1 ? " day." : " days."));
        }
    }

    private static void updateShortages(Settlement settlement, long day, Map<ResourceType, Integer> idleForLack) {
        Map<String, Long> conditions = settlement.conditions();
        for (ResourceType type : ResourceType.values()) {
            String key = "shortage:" + type.name().toLowerCase(Locale.ROOT);
            int idle = idleForLack.getOrDefault(type, 0);
            String resource = type.name().toLowerCase(Locale.ROOT);
            if (idle > 0 && !conditions.containsKey(key)) {
                conditions.put(key, day);
                settlement.record(day, HistoryEvent.Kind.SHORTAGE, "Work stopped for lack of " + resource
                        + ". " + idle + (idle == 1 ? " worker" : " workers") + " sat idle.");
            } else if (idle == 0 && conditions.containsKey(key)) {
                conditions.remove(key);
                settlement.record(day, HistoryEvent.Kind.RECOVERY, "Supplies of " + resource + " were restored.");
            }
        }
    }

    private static void updateMilestones(Settlement settlement, long day) {
        Map<String, Long> conditions = settlement.conditions();
        int population = settlement.population();
        for (int milestone : POPULATION_MILESTONES) {
            String key = "population:" + milestone;
            if (population >= milestone && !conditions.containsKey(key)) {
                conditions.put(key, day);
                settlement.record(day, HistoryEvent.Kind.MILESTONE,
                        settlement.name() + " grew to " + milestone + " residents.");
            }
        }

        int food = settlement.ledger().get(ResourceType.FOOD);
        if (population > 0 && food >= population * 20 && !conditions.containsKey("surplus")) {
            conditions.put("surplus", day);
            settlement.record(day, HistoryEvent.Kind.MILESTONE, "The granaries were full to bursting.");
        } else if (food < population * 5) {
            conditions.remove("surplus");
        }
    }
}
