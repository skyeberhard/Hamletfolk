package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

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
    /** R3.6: chance per working day that a gatherer wears out one tool. */
    static final double TOOL_WEAR_CHANCE = 0.15;
    /** R3.6: output multiplier for gatherers while the village has no tools. */
    static final double TOOLLESS_OUTPUT = 0.75;
    /** R4.3: occupations an unemployed resident can take up, in tie-break order. */
    static final List<Occupation> JOB_CANDIDATES = List.of(
            Occupation.FARMER, Occupation.LUMBERJACK, Occupation.MASON, Occupation.FISHERMAN);
    /** R4.3: stock per resident below which a resource counts as short. */
    static final int FOOD_WANTED_PER_HEAD = 10;
    static final int STOCK_WANTED_PER_HEAD = 3;
    /** R3.10: storage limit is a base amount plus room per resident (food needs more of it). */
    static final int BASE_STORAGE = 100;
    static final int FOOD_STORAGE_PER_RESIDENT = 40;
    static final int STORAGE_PER_RESIDENT = 10;
    /** R3.10: percent of the food stock that spoils each day (whole units, so a small stock keeps). */
    static final int FOOD_SPOILAGE_PERCENT = 2;
    /** A shortage returning within this many days of its recorded end isn't recorded again. */
    static final int SHORTAGE_QUIET_DAYS = 7;

    /**
     * Whether gatherers slow down (and a tool shortage is recorded) while the village has no
     * tools. Off until R2.3 gives METAL a source: smiths need metal to make tools, and without
     * a miner every village would stay toolless for good. Tools still wear out either way.
     */
    private final boolean toollessPenalty;

    /**
     * R4.3: whether a workstation is free for an occupation. Until buildings exist (M2) only
     * simulation-owned occupations, which need no vanilla workstation, can be handed out.
     */
    private final Predicate<Occupation> workstationFree;

    public SettlementSimulator() {
        this(false);
    }

    SettlementSimulator(boolean toollessPenalty) {
        this(toollessPenalty, Occupation::simOwned);
    }

    SettlementSimulator(boolean toollessPenalty, Predicate<Occupation> workstationFree) {
        this.toollessPenalty = toollessPenalty;
        this.workstationFree = workstationFree;
    }

    /**
     * Simulates every day from the settlement's last simulated day up to {@code targetDay}.
     * If more than {@code maxDays} are pending, the oldest are skipped rather than replayed,
     * and the history says how many (R1.22).
     *
     * @return the number of days simulated
     */
    public int simulateTo(Settlement settlement, long targetDay, int maxDays) {
        if (targetDay - settlement.lastSimulatedDay() > maxDays) {
            long skipped = targetDay - settlement.lastSimulatedDay() - maxDays;
            settlement.setLastSimulatedDay(targetDay - maxDays);
            // Usually server downtime or a big /time add. An abandoned settlement has nothing to miss.
            if (!settlement.isAbandoned()) {
                settlement.record(targetDay - maxDays, HistoryEvent.Kind.MILESTONE, skipped
                        + (skipped == 1 ? " day" : " days") + " passed that no one in " + settlement.name()
                        + " wrote down.");
            }
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
        assignJob(settlement);
        Random random = new Random(settlement.id().getMostSignificantBits() ^ (day * 0x9E3779B97F4A7C15L));
        Ledger ledger = settlement.ledger();

        Random wearRandom = new Random(settlement.id().getLeastSignificantBits() ^ (day * 0x9E3779B97F4A7C15L) ^ 0x700157L);
        Map<ResourceType, Integer> idleForLack = new EnumMap<>(ResourceType.class);
        for (Resident resident : workOrder(settlement)) {
            work(resident, settlement.flow(), ledger, day, random, wearRandom, idleForLack);
        }

        int demand = 0;
        for (Resident resident : settlement.residents()) {
            demand += resident.adult() ? ADULT_FOOD_PER_DAY : CHILD_FOOD_PER_DAY;
        }
        int eaten = ledger.take(ResourceType.FOOD, demand);
        settlement.flow().recordConsumed(ResourceType.FOOD, day, eaten);
        int shortfall = demand - eaten;
        double fedFraction = demand == 0 ? 1.0 : (double) eaten / demand;

        spoilAndCap(settlement);

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
     * R3.10: the most of a resource the settlement can hold. M2 storage buildings will raise it.
     */
    static int capacity(Settlement settlement, ResourceType type) {
        int perResident = type == ResourceType.FOOD ? FOOD_STORAGE_PER_RESIDENT : STORAGE_PER_RESIDENT;
        return BASE_STORAGE + perResident * settlement.population();
    }

    /** R3.10: food spoils, then anything beyond a resource's storage limit is wasted. */
    private static void spoilAndCap(Settlement settlement) {
        Ledger ledger = settlement.ledger();
        ledger.take(ResourceType.FOOD, ledger.get(ResourceType.FOOD) * FOOD_SPOILAGE_PERCENT / 100);
        for (ResourceType type : ResourceType.values()) {
            ledger.take(type, ledger.get(type) - capacity(settlement, type));
        }
    }

    /**
     * R4.3: one unemployed adult takes the occupation the village is shortest of, if a workstation
     * is free for it. At most one a day, so a shortage draws people in gradually. The simulation
     * owns the occupation from here on; the vanilla profession only seeds it (see the Paper layer).
     */
    private void assignJob(Settlement settlement) {
        Resident jobless = null;
        for (Resident resident : workOrder(settlement)) {
            if (resident.adult() && resident.occupation() == Occupation.UNEMPLOYED) {
                jobless = resident;
                break;
            }
        }
        if (jobless == null) {
            return;
        }
        Occupation best = null;
        double bestCover = Double.MAX_VALUE;
        int population = settlement.population();
        for (Occupation candidate : JOB_CANDIDATES) {
            ResourceType resource = candidate.produces();
            double wanted = (double) population * (resource == ResourceType.FOOD ? FOOD_WANTED_PER_HEAD : STOCK_WANTED_PER_HEAD);
            double cover = settlement.ledger().get(resource) / wanted; // below 1 means short
            if (cover < 1.0 && cover < bestCover) {
                best = candidate;
                bestCover = cover;
            }
        }
        // Shortest over every candidate, then check its workstation: a village short of food but
        // with no farm to staff doesn't send its forager off to cut wood instead.
        if (best != null && workstationFree.test(best)) {
            jobless.setOccupation(best);
        }
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

    private void work(Resident resident, ResourceFlow flow, Ledger ledger, long day, Random random,
                             Random wearRandom,
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
        if (input != null) {
            flow.recordConsumed(input, day, 1);
        }
        double toolFactor = 1.0;
        if (occupation.usesTools()) {
            if (ledger.get(ResourceType.TOOLS) == 0) {
                if (toollessPenalty) {
                    toolFactor = TOOLLESS_OUTPUT;
                    idleForLack.merge(ResourceType.TOOLS, 1, Integer::sum);
                }
            } else if (wearRandom.nextDouble() < TOOL_WEAR_CHANCE) {
                flow.recordConsumed(ResourceType.TOOLS, day, ledger.take(ResourceType.TOOLS, 1));
            }
        }
        double diligence = 0.5 + resident.traits().workEthic() / 100.0;
        int output = (int) Math.floor(occupation.baseOutput() * diligence * toolFactor + random.nextDouble());
        ledger.add(occupation.produces(), output);
        flow.recordProduced(occupation.produces(), day, output);
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

    private static String shortageText(ResourceType type, String resource, int workers) {
        String who = workers + (workers == 1 ? " worker" : " workers");
        return type == ResourceType.TOOLS
                ? "Work slowed for lack of tools. " + who + " made do with worn-out ones."
                : "Work stopped for lack of " + resource + ". " + who + " sat idle.";
    }

    private static void updateShortages(Settlement settlement, long day, Map<ResourceType, Integer> idleForLack) {
        Map<String, Long> conditions = settlement.conditions();
        for (ResourceType type : ResourceType.values()) {
            String key = "shortage:" + type.name().toLowerCase(Locale.ROOT);
            int idle = idleForLack.getOrDefault(type, 0);
            String resource = type.name().toLowerCase(Locale.ROOT);
            // R1.21: a shortage that returns within SHORTAGE_QUIET_DAYS of ending is the same
            // trouble flickering on and off, so it isn't written down again (nor its end).
            String recoveredKey = "recovered:" + resource;
            String quietKey = "quiet:" + resource;
            if (idle > 0 && !conditions.containsKey(key)) {
                conditions.put(key, day);
                Long recovered = conditions.get(recoveredKey);
                if (recovered != null && day - recovered <= SHORTAGE_QUIET_DAYS) {
                    conditions.put(quietKey, day);
                } else {
                    settlement.record(day, HistoryEvent.Kind.SHORTAGE, shortageText(type, resource, idle));
                }
            } else if (idle > 0 && conditions.containsKey(quietKey)
                    && day - conditions.get(key) >= SHORTAGE_QUIET_DAYS) {
                // Not a flicker after all: it has lasted, so it goes in the history.
                conditions.remove(quietKey);
                settlement.record(day, HistoryEvent.Kind.SHORTAGE, shortageText(type, resource, idle));
            } else if (idle == 0 && conditions.containsKey(key)) {
                conditions.remove(key);
                conditions.put(recoveredKey, day); // the quiet window runs from the latest end
                if (conditions.remove(quietKey) == null) {
                    settlement.record(day, HistoryEvent.Kind.RECOVERY, "Supplies of " + resource + " were restored.");
                }
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
