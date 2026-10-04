package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiPredicate;
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
            Occupation.FARMER, Occupation.LUMBERJACK, Occupation.MASON, Occupation.FISHERMAN, Occupation.MINER,
            Occupation.TOOLSMITH);
    /** R4.3: stock per resident below which a resource counts as short. */
    static final int FOOD_WANTED_PER_HEAD = 10;
    static final int STOCK_WANTED_PER_HEAD = 3;
    /** R3.3: the resources the village posts requests for when short, in the order it tries. */
    static final List<ResourceType> REQUESTABLE = List.of(
            ResourceType.FOOD, ResourceType.WOOD, ResourceType.STONE, ResourceType.METAL, ResourceType.TOOLS);
    /** R3.3: a request asks for at least this many units, and pays this many times the merchant's rate. */
    static final int REQUEST_MIN_UNITS = 8;
    static final int REQUEST_PREMIUM = 2;
    /** R3.3: a request lapses (and its unpaid reward returns to the treasury) after this many days. */
    static final int REQUEST_EXPIRY_DAYS = 30;
    /** R3.3: after a request closes, the same resource waits this long before asking again. */
    static final int REQUEST_COOLDOWN_DAYS = 7;
    static final int MAX_OPEN_REQUESTS = 3;
    /**
     * R3.9: a merchant sells stock above this many per resident, all well over what R4.3 counts as
     * short so selling never starts a shortage. Food keeps 24: the newcomer rule (R4.1) needs 20 a
     * head after the day's meal (2 a head) and spoilage, which happen after the merchant sells.
     * Tools and metal keep more, since smiths and tool wear need them and a source only through a mine and a smith (R2.3).
     */
    static final int FOOD_KEPT_PER_HEAD = 24;
    static final int TOOLS_METAL_KEPT_PER_HEAD = 20;
    static final int STOCK_KEPT_PER_HEAD = 6;
    /** R3.9: whole batches a merchant sells in a day, each earning one emerald. */
    static final int MERCHANT_BATCHES_PER_DAY = 4;
    /** R3.5: what a merchant keeps of each emerald a sale brings the treasury, in hundredths of an emerald. */
    static final int MERCHANT_COMMISSION = 20;
    /** R3.9: one merchant for this many residents, and at least one once there is anything to sell. */
    static final int RESIDENTS_PER_MERCHANT = 15;
    /** R4.15: output multiplier for elders. */
    static final double ELDER_OUTPUT = 0.6;
    /** R4.1: a newcomer needs this much food per resident in store, and at least this many days between arrivals. */
    static final int NEWCOMER_FOOD_PER_HEAD = 20;
    static final int NEWCOMER_COOLDOWN_DAYS = 3;
    /** R4.9: a need at or above this costs no output; below it output falls off linearly. */
    static final int NEED_COMFORTABLE = 50;
    /** R4.9: the share of output a worker with a need at zero still manages. */
    static final double MIN_NEEDS_OUTPUT = 0.5;
    /** R3.10: storage limit is a base amount plus room per resident (food needs more of it). */
    static final int BASE_STORAGE = 100;
    static final int FOOD_STORAGE_PER_RESIDENT = 40;
    static final int STORAGE_PER_RESIDENT = 10;
    /** R3.10: percent of the food stock that spoils each day (whole units, so a small stock keeps). */
    static final int FOOD_SPOILAGE_PERCENT = 2;
    /** A shortage returning within this many days of its recorded end isn't recorded again. */
    static final int SHORTAGE_QUIET_DAYS = 7;

    /**
     * Whether gatherers slow down (and a tool shortage is recorded) while the village has no tools.
     * It only applies in a settlement with a registered mine (R2.3), the one way to get more metal for
     * tools, so a village with no way out is not punished. Tools wear out either way.
     */
    private final boolean toollessPenalty;

    /**
     * R4.3 and R2.3: whether there is somewhere for an unemployed resident to work as an occupation.
     * By default that means a registered building with a free place (see {@link #buildingsAllow});
     * tests substitute their own rule.
     */
    private final BiPredicate<Settlement, Occupation> workstationFree;

    /** The rule that reads the registered buildings; true when {@link #workstationFree} is this one. */
    private static final BiPredicate<Settlement, Occupation> BUILDINGS = SettlementSimulator::buildingsAllow;

    /**
     * R4.15: whether residents die of old age. Elders slow down either way. The Paper layer turns
     * this off by default until a playtest shows how villages hold up, since a death removes a villager.
     */
    private final boolean oldAgeDeaths;

    public SettlementSimulator() {
        this(false);
    }

    SettlementSimulator(boolean toollessPenalty) {
        this(BUILDINGS, toollessPenalty, true);
    }

    SettlementSimulator(boolean toollessPenalty, Predicate<Occupation> workstationFree) {
        this((settlement, occupation) -> workstationFree.test(occupation), toollessPenalty, true);
    }

    private SettlementSimulator(BiPredicate<Settlement, Occupation> workstationFree, boolean toollessPenalty, boolean oldAgeDeaths) {
        this.toollessPenalty = toollessPenalty;
        this.workstationFree = workstationFree;
        this.oldAgeDeaths = oldAgeDeaths;
    }

    /** The simulator the Paper layer uses: jobs need registered buildings, with the two settings as configured. */
    public static SettlementSimulator configured(boolean oldAgeDeaths, boolean toollessPenalty) {
        return new SettlementSimulator(BUILDINGS, toollessPenalty, oldAgeDeaths);
    }

    /** As {@link #configured} with the tool penalty off (what core tests use). */
    public static SettlementSimulator withOldAgeDeaths(boolean oldAgeDeaths) {
        return configured(oldAgeDeaths, false);
    }

    /**
     * R2.3 and R2.5: whether a resident can be handed this occupation, by the registered buildings.
     * Lumberjacks need no building. A farmer needs a registered farm and a miner a registered mine, four
     * places each, and a merchant a registered shop, one place each; occupations with no building behind them (a mason, a fisher) are only ever
     * taken from the villager's own vanilla profession.
     */
    static boolean buildingsAllow(Settlement settlement, Occupation occupation) {
        if (occupation == Occupation.LUMBERJACK) {
            return true;
        }
        int places = placesFor(settlement, occupation);
        if (places == 0) {
            return false;
        }
        long holders = settlement.residents().stream().filter(r -> r.adult() && r.occupation() == occupation).count();
        return holders < places;
    }

    /** R2.3 and R2.5: how many residents the registered buildings give work to in this occupation. */
    static int placesFor(Settlement settlement, Occupation occupation) {
        int places = 0;
        for (Building building : settlement.buildings()) {
            if (building.type().job().filter(job -> job == occupation).isPresent()) {
                places += building.type().places();
            }
        }
        return places;
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
        ageOut(settlement, day);
        releaseMerchant(settlement);
        assignJob(settlement, day);
        Random random = new Random(settlement.id().getMostSignificantBits() ^ (day * 0x9E3779B97F4A7C15L));
        Ledger ledger = settlement.ledger();

        Random wearRandom = new Random(settlement.id().getLeastSignificantBits() ^ (day * 0x9E3779B97F4A7C15L) ^ 0x700157L);
        Map<ResourceType, Integer> idleForLack = new EnumMap<>(ResourceType.class);
        // R3.6: tools are only a real shortage where there is a way to get more: a registered mine for metal.
        boolean penalty = toollessPenalty && settlement.buildingCount(BuildingType.MINE) > 0;
        for (Resident resident : workOrder(settlement)) {
            if (resident.adult() && resident.occupation() == Occupation.MERCHANT) {
                sell(settlement, resident, day);
                continue;
            }
            work(resident, settlement.flow(), ledger, day, random, wearRandom, idleForLack, penalty);
        }

        int demand = 0;
        for (Resident resident : settlement.residents()) {
            demand += resident.adult() ? ADULT_FOOD_PER_DAY : CHILD_FOOD_PER_DAY;
        }
        int eaten = ledger.take(ResourceType.FOOD, demand);
        settlement.flow().recordConsumed(ResourceType.FOOD, day, eaten);
        int shortfall = demand - eaten;
        double fedFraction = demand == 0 ? 1.0 : (double) eaten / demand;

        for (Resident resident : settlement.residents()) {
            if (resident.adult()) {
                resident.spendWealth(Wealth.mealCost(fedFraction)); // R3.5: they pay for their own meals
            }
        }

        spoilAndCap(settlement);
        updateRequests(settlement, day);

        settlement.setThreat(settlement.threat() * THREAT_DECAY);
        for (Resident resident : settlement.residents()) {
            Needs needs = resident.needs();
            // R4.9: half-fed holds steady and better than that recovers, so a village that can feed
            // itself at its worst output always climbs back instead of starving at half output for good.
            needs.adjustFood(Math.min(10, (int) Math.round(20 * (fedFraction - 0.5))));
            double nerve = 1.5 - resident.traits().bravery() / 100.0;
            needs.setSafety((int) Math.round(100 - settlement.threat() * nerve));
        }

        updateFamine(settlement, day, shortfall);
        updateShortages(settlement, day, idleForLack);
        updateMilestones(settlement, day);
    }

    /**
     * R4.1: whether a new villager should arrive now: the food stores are well stocked, there is a
     * free bed, and nobody has arrived in the last few days. The caller supplies the bed count
     * (the Paper layer counts beds, R2.2 will replace that) and, if it does add a villager, must
     * call {@link #newcomerArrived}. Arrival is recorded in history when that villager is enrolled.
     */
    public boolean newcomerDue(Settlement settlement, int freeBeds) {
        int population = settlement.population();
        // Not on the founding day: a villager enrolled then counts as a founder, with no arrival line.
        if (population == 0 || settlement.isAbandoned() || freeBeds <= 0
                || settlement.lastSimulatedDay() <= settlement.foundedDay()
                || settlement.ledger().get(ResourceType.FOOD) < population * NEWCOMER_FOOD_PER_HEAD) {
            return false;
        }
        Long last = settlement.conditions().get("newcomerAt");
        return last == null || settlement.lastSimulatedDay() - last >= NEWCOMER_COOLDOWN_DAYS;
    }

    /** R4.1: starts the cooldown after a newcomer was added. */
    public void newcomerArrived(Settlement settlement) {
        settlement.conditions().put("newcomerAt", settlement.lastSimulatedDay());
    }

    /**
     * R3.10: the most of a resource the settlement can hold. M2 storage buildings will raise it.
     */
    public static int capacity(Settlement settlement, ResourceType type) {
        int perResident = type == ResourceType.FOOD ? FOOD_STORAGE_PER_RESIDENT : STORAGE_PER_RESIDENT;
        return BASE_STORAGE + perResident * settlement.population();
    }

    /** R3.11: how much more of a resource the stores can take before the limit; 0 if already at or over it. */
    public static int room(Settlement settlement, ResourceType type) {
        return Math.max(0, capacity(settlement, type) - settlement.ledger().get(type));
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
     * R4.15: residents past their own maximum age die of old age. They leave the population, the
     * history records it, and their id is kept so the Minecraft layer can remove their villager.
     */
    private void ageOut(Settlement settlement, long day) {
        if (!oldAgeDeaths) {
            return;
        }
        List<Resident> old = new ArrayList<>();
        for (Resident resident : settlement.residents()) {
            if (resident.adult() && resident.age(day) >= resident.maxAge()) {
                old.add(resident);
            }
        }
        for (Resident resident : old) {
            settlement.removeResident(resident.id());
            settlement.markDeparted(resident.id(), day);
            settlement.record(day, HistoryEvent.Kind.DEATH, resident.fullName() + " died of old age, at "
                    + resident.age(day) + " days.");
        }
    }

    /**
     * R4.3: one unemployed adult takes the occupation the village is shortest of, if a workstation
     * is free for it. At most one a day, so a shortage draws people in gradually. The simulation
     * owns the occupation from here on; the vanilla profession only seeds it (see the Paper layer).
     */
    private void assignJob(Settlement settlement, long day) {
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
        // Food first, but only where it can be acted on: while food is short and a food job has a free place,
        // that job is taken. Otherwise the shortest need that has somewhere to work, so a missing mine never
        // blocks the lumberjack. Only an actual famine freezes the other jobs: then the unemployed keep
        // foraging (R1.24). A village that merely holds less than 10 food a head is not in famine.
        boolean foodShort = cover(settlement, Occupation.FARMER) < 1.0;
        // Famine, or stores that cannot cover today's meals: then nothing but food work will do.
        boolean famine = settlement.hasCondition("famine") || settlement.ledger().get(ResourceType.FOOD) <= dailyFoodDemand(settlement);
        Occupation best = null;
        double bestCover = Double.MAX_VALUE;
        for (Occupation candidate : JOB_CANDIDATES) {
            if (!workstationFree.test(settlement, candidate) || !(foodShort && candidate.produces() == ResourceType.FOOD)) {
                continue;
            }
            double cover = cover(settlement, candidate);
            if (cover < 1.0 && cover < bestCover) {
                best = candidate;
                bestCover = cover;
            }
        }
        if (best == null && !famine) {
            for (Occupation candidate : JOB_CANDIDATES) {
                if (!workstationFree.test(settlement, candidate)) {
                    continue;
                }
                double cover = cover(settlement, candidate);
                if (cover < 1.0 && cover < bestCover) {
                    best = candidate;
                    bestCover = cover;
                }
            }
        }
        // R3.9: with nothing short and goods to spare, someone takes up selling them.
        if (best == null && merchantNeeded(settlement) && workstationFree.test(settlement, Occupation.MERCHANT)) {
            best = Occupation.MERCHANT;
        }
        // R4.10: a grown child (one born to residents) follows a parent's trade when it is also needed.
        Occupation parentTrade = parentTrade(settlement, jobless);
        boolean apprentice = jobless.parentA() != null;
        boolean parentTradeNeeded = parentTrade == Occupation.MERCHANT
                ? best == null && merchantNeeded(settlement)
                : parentTrade != null && cover(settlement, parentTrade) < 1.0
                        && !(famine && parentTrade.produces() != ResourceType.FOOD)
                        && !(foodShort && best != null && best.produces() == ResourceType.FOOD
                                && parentTrade.produces() != ResourceType.FOOD);
        if (parentTradeNeeded && workstationFree.test(settlement, parentTrade)) {
            best = parentTrade;
        }
        if (best != null) {
            jobless.setOccupation(best);
            if (apprentice) {
                settlement.record(day, HistoryEvent.Kind.MILESTONE, jobless.fullName()
                        + " was apprenticed as a " + best.title() + ".");
            }
        }
    }

    /** What the whole settlement eats in a day. */
    private static int dailyFoodDemand(Settlement settlement) {
        int demand = 0;
        for (Resident resident : settlement.residents()) {
            demand += resident.adult() ? ADULT_FOOD_PER_DAY : CHILD_FOOD_PER_DAY;
        }
        return demand;
    }

    /** How well stocked the settlement is with what an occupation makes; below 1 means short. */
    private static double cover(Settlement settlement, Occupation occupation) {
        double cover = coverOf(settlement, occupation.produces());
        // A miner is wanted when either stone or metal is short.
        return Math.min(cover, coverOf(settlement, occupation.secondaryProduces()));
    }

    private static double coverOf(Settlement settlement, ResourceType resource) {
        if (resource == null) {
            return Double.MAX_VALUE;
        }
        double wanted = (double) settlement.population()
                * (resource == ResourceType.FOOD ? FOOD_WANTED_PER_HEAD : STOCK_WANTED_PER_HEAD);
        return settlement.ledger().get(resource) / wanted;
    }

    private static Occupation parentTrade(Settlement settlement, Resident child) {
        for (UUID parent : new UUID[] {child.parentA(), child.parentB()}) {
            Occupation trade = parent == null ? null
                    : settlement.resident(parent).map(Resident::occupation).orElse(null);
            if (trade != null && (JOB_CANDIDATES.contains(trade) || trade == Occupation.MERCHANT)) {
                return trade;
            }
        }
        return null;
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
                             Map<ResourceType, Integer> idleForLack, boolean toollessPenalty) {
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
        double ageFactor = resident.stage(day) == LifeStage.ELDER ? ELDER_OUTPUT : 1.0;
        int output = (int) Math.floor(occupation.baseOutput() * diligence * toolFactor * needsFactor(resident.needs())
                * ageFactor + random.nextDouble());
        ledger.add(occupation.produces(), output);
        flow.recordProduced(occupation.produces(), day, output);
        resident.addWealth(Wealth.worth(occupation.produces(), output)); // R3.5
        if (occupation.secondaryProduces() != null) {
            int extra = (int) Math.floor(occupation.secondaryBaseOutput() * diligence * toolFactor
                    * needsFactor(resident.needs()) * ageFactor + random.nextDouble());
            ledger.add(occupation.secondaryProduces(), extra);
            flow.recordProduced(occupation.secondaryProduces(), day, extra);
            resident.addWealth(Wealth.worth(occupation.secondaryProduces(), extra)); // R3.5
        }
        resident.needs().adjustPurpose(4);
    }

    /** The stock per resident below which a resource counts as short (the same test R4.3 uses). */
    private static int wantedLevel(Settlement settlement, ResourceType type) {
        return settlement.population() * (type == ResourceType.FOOD ? FOOD_WANTED_PER_HEAD : STOCK_WANTED_PER_HEAD);
    }

    private static String closedKey(ResourceType type) {
        return "requestClosed:" + type.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * R3.3: closes requests that are no longer needed or have lapsed, and posts one for each
     * resource that has run short, if the treasury can fund the reward. The reward is taken out
     * of the treasury at once and held, so a request is never a promise the village can't keep.
     */
    private static void updateRequests(Settlement settlement, long day) {
        for (Request request : new ArrayList<>(settlement.requests())) {
            boolean emptied = settlement.population() == 0;
            boolean covered = !emptied && settlement.ledger().get(request.type()) >= wantedLevel(settlement, request.type());
            if (emptied || covered || day - request.postedDay() >= REQUEST_EXPIRY_DAYS) {
                settlement.requestMap().remove(request.type());
                settlement.ledger().addTreasury(request.unpaid());
                settlement.conditions().put(closedKey(request.type()), day);
                settlement.record(day, HistoryEvent.Kind.MILESTONE, "The request for " + request.describe()
                        + (emptied ? " was withdrawn: no one is left in " + settlement.name()
                        : covered ? " was withdrawn: the stores recovered" : " lapsed")
                        + (request.unpaid() > 0 ? ", and " + request.unpaid() + " emeralds went back to the treasury." : "."));
            }
        }
        if (settlement.population() == 0) {
            return;
        }
        for (ResourceType type : REQUESTABLE) {
            if (settlement.requestMap().size() >= MAX_OPEN_REQUESTS) {
                return;
            }
            int wanted = wantedLevel(settlement, type);
            int stock = settlement.ledger().get(type);
            Long closed = settlement.conditions().get(closedKey(type));
            if (settlement.requestMap().containsKey(type) || stock >= wanted
                    || (closed != null && day - closed < REQUEST_COOLDOWN_DAYS)) {
                continue;
            }
            int units = Math.max(REQUEST_MIN_UNITS, 2 * wanted - stock);
            int reward = (int) Math.ceil((double) units * REQUEST_PREMIUM / unitsPerEmerald(type));
            if (!settlement.ledger().spendTreasury(reward)) {
                continue; // the village can't afford to pay for it
            }
            Request request = new Request(type, units, 0, reward, 0, day);
            settlement.requestMap().put(type, request);
            settlement.record(day, HistoryEvent.Kind.MILESTONE, settlement.name() + " is asking for "
                    + request.describe() + " and will pay " + reward + " emeralds for it.");
        }
    }

    /**
     * R3.3: a visitor hands over {@code units} of a resource. If the village has an open request for
     * it, the units count toward it and the emeralds now owed are returned; the Minecraft layer
     * hands them over. The units themselves are credited to the stores by the caller either way.
     * Units beyond what the request needs are an ordinary donation and pay nothing.
     */
    public int fulfil(Settlement settlement, ResourceType type, int units, long day, String donor) {
        return fulfil(settlement, type, units, day, donor, Integer.MAX_VALUE);
    }

    /**
     * R3.13: as above, but never paying more than {@code maxPayout} emeralds for this delivery. When
     * the cap binds, the delivery only counts for as many units as it can pay for, so what is
     * left stays in the request and nothing is lost or carried over. The units still reach the stores.
     */
    public int fulfil(Settlement settlement, ResourceType type, int units, long day, String donor, int maxPayout) {
        Request request = settlement.requestMap().get(type);
        if (request == null || units <= 0) {
            return 0;
        }
        int counted = Math.min(units, request.remaining());
        while (counted > 0 && request.owedWith(counted) > maxPayout) {
            counted--;
        }
        request.fill(counted);
        int owed = request.settle();
        if (request.remaining() == 0) {
            settlement.requestMap().remove(type);
            settlement.conditions().put(closedKey(type), day);
            settlement.record(day, HistoryEvent.Kind.MILESTONE, donor + " filled " + settlement.name()
                    + "'s request for " + request.describe() + ".");
        }
        return owed;
    }

    /**
     * R3.13: the most a request may pay for a crafted tool in the given condition: what the raw
     * materials in it would pay on a request of their own at the same rates, in whole emeralds. So
     * crafting cheap materials into a tool never pays more than handing them in as they are. Anything
     * that is not a tool has no cap.
     */
    public static int maxPayout(String material, double condition) {
        java.util.List<ResourceMapper.Value> parts = ResourceMapper.toolMaterials(material);
        if (parts.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        double emeralds = 0;
        for (ResourceMapper.Value part : parts) {
            double units = (double) part.numerator() / part.denominator();
            emeralds += units * REQUEST_PREMIUM / unitsPerEmerald(part.type());
        }
        return (int) Math.floor(emeralds * Math.max(0.0, Math.min(1.0, condition)) + 1e-9);
    }

    /** R3.9: units of a resource that make one emerald when sold. Tools are worth the most. */
    static int unitsPerEmerald(ResourceType type) {
        return switch (type) {
            case FOOD -> 10;
            case WOOD -> 6;
            case STONE -> 5;
            case METAL -> 2;
            case GOODS -> 2;
            case TOOLS -> 1;
        };
    }

    /** R3.9: stock above what the village should keep, the most a merchant may sell of it. */
    static int surplus(Settlement settlement, ResourceType type) {
        int perHead = switch (type) {
            case FOOD -> FOOD_KEPT_PER_HEAD;
            case TOOLS, METAL -> TOOLS_METAL_KEPT_PER_HEAD;
            default -> STOCK_KEPT_PER_HEAD;
        };
        int kept = settlement.population() * perHead;
        return Math.max(0, settlement.ledger().get(type) - kept);
    }

    private static boolean hasSurplus(Settlement settlement) {
        for (ResourceType type : ResourceType.values()) {
            if (surplus(settlement, type) >= unitsPerEmerald(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean merchantNeeded(Settlement settlement) {
        long merchants = settlement.residents().stream()
                .filter(r -> r.adult() && r.occupation() == Occupation.MERCHANT).count();
        long wanted = Math.max(1, (settlement.population() + RESIDENTS_PER_MERCHANT - 1) / RESIDENTS_PER_MERCHANT);
        // Not while food is merely adequate: an unemployed resident forages, and a merchant doesn't.
        return merchants < wanted && hasSurplus(settlement) && cover(settlement, Occupation.FARMER) >= 2.0;
    }

    /** True if a gathering resource is short (the same test assignJob uses to pick a job). */
    private boolean somethingShort(Settlement settlement) {
        if (cover(settlement, Occupation.FARMER) < 1.0) {
            return true; // food is always something to work on: an unemployed resident forages
        }
        for (Occupation candidate : JOB_CANDIDATES) {
            // Only a shortage someone could work on: a missing mine does not count, or a village
            // without one would always look short of metal and never keep a merchant.
            if (workstationFree.test(settlement, candidate) && cover(settlement, candidate) < 1.0) {
                return true;
            }
        }
        return false;
    }

    /**
     * R3.9: nothing left to sell while something else is short, so one merchant goes back to being
     * unemployed and the job assignment can send them where they are needed. At most one a day.
     */
    private void releaseMerchant(Settlement settlement) {
        // R2.5: a merchant with no storefront to work in goes back to being unemployed, one a day.
        if (workstationFree == BUILDINGS) {
            long merchants = settlement.residents().stream()
                    .filter(r -> r.adult() && r.occupation() == Occupation.MERCHANT).count();
            if (merchants > placesFor(settlement, Occupation.MERCHANT)) {
                for (Resident resident : settlement.residents()) {
                    if (resident.adult() && resident.occupation() == Occupation.MERCHANT) {
                        resident.setOccupation(Occupation.UNEMPLOYED);
                        return;
                    }
                }
            }
        }
        if (hasSurplus(settlement) || !somethingShort(settlement)) {
            return;
        }
        for (Resident resident : settlement.residents()) {
            if (resident.adult() && resident.occupation() == Occupation.MERCHANT) {
                resident.setOccupation(Occupation.UNEMPLOYED);
                return;
            }
        }
    }

    /**
     * R3.9: a merchant sells surplus for emeralds into the treasury. Each batch is a fixed number
     * of units of whichever resource has the most surplus worth, so what is most plentiful goes first.
     * Only whole batches are sold, and never stock the village needs to keep.
     */
    private static void sell(Settlement settlement, Resident merchant, long day) {
        Ledger ledger = settlement.ledger();
        double pace = (merchant.stage(day) == LifeStage.ELDER ? ELDER_OUTPUT : 1.0) * needsFactor(merchant.needs());
        int batches = (int) Math.round(MERCHANT_BATCHES_PER_DAY * pace);
        int sold = 0;
        for (int batch = 0; batch < batches; batch++) {
            ResourceType best = null;
            double bestWorth = 0;
            for (ResourceType type : ResourceType.values()) {
                int each = unitsPerEmerald(type);
                int available = surplus(settlement, type);
                if (available >= each && (double) available / each > bestWorth) {
                    best = type;
                    bestWorth = (double) available / each;
                }
            }
            if (best == null) {
                break;
            }
            int units = unitsPerEmerald(best);
            settlement.flow().recordConsumed(best, day, ledger.take(best, units));
            ledger.addTreasury(1);
            merchant.addWealth(MERCHANT_COMMISSION); // R3.5: a fifth of each emerald
            sold++;
        }
        if (sold > 0) {
            merchant.needs().adjustPurpose(4);
        }
    }

    /**
     * R4.9: how much of their normal output a worker manages given their needs. Set by their
     * worst need: a starving or terrified worker produces noticeably less, down to
     * {@link #MIN_NEEDS_OUTPUT} so a village can still climb out of trouble.
     */
    static double needsFactor(Needs needs) {
        int worst = Math.min(needs.food(), Math.min(needs.safety(), needs.purpose()));
        return MIN_NEEDS_OUTPUT + (1 - MIN_NEEDS_OUTPUT) * Math.min(1.0, (double) worst / NEED_COMFORTABLE);
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
