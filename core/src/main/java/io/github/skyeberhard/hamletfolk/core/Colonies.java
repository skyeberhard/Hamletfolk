package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * R10.1: daughter villages. A village that has grown as far as its plan goes can send a party of its own people, with a kit and a gift from
 * its treasury, to found another, and the two remember each other. This is the rule: whether a village is ready, who goes, what it costs, and
 * what changes when they have gone. Where the new village stands and the walking there are the Minecraft layer's (R10.3, R10.4); the
 * new settlement is made by the caller and handed to {@link #settle}.
 *
 * <p>A party is whole households (a couple and their young children together, or a person on their own), and always has a builder's
 * household in it, since a village with nobody who has finished buildings cannot improve a house (R4.56). It never takes the last worker of a
 * trade the village lives on, a guard, a merchant, an elder, a pinned resident or one who moved lately, and it leaves the parent with at
 * least {@link Rules#minLeft} people. The people are real residents moved over: their names, ages, experience, buildings finished, families and
 * marriages come with them; only their jobs are theirs to find again.
 *
 * <p>The tie between the two villages is two {@code colony...} conditions (no save-format change).
 */
public final class Colonies {
    /** What a village needs and does before it sends a party. Every number is a setting. */
    public record Rules(int minPopulation, int minLeft, int minParty, int maxParty, int cooldownDays, int maxVillagesInWorld, int minSpacing,
            int treasuryGift) {
        public Rules {
            minParty = Math.max(2, minParty);
            maxParty = Math.max(minParty, maxParty);
        }
    }

    /** 30 people to send a party of 4 to 8 and keep 20 behind, one party in 90 days, at most 12 villages in a world, 300 blocks apart, 20 emeralds. */
    public static Rules defaults() {
        return new Rules(30, 20, 4, 8, 90, 12, 300, 20);
    }

    /** What each member of the party carries, as a founder's kit (see {@link Resettlement}). */
    public static final int FOOD_PER_HEAD = Resettlement.FOOD_PER_SETTLER;
    public static final int WOOD_PER_HEAD = Resettlement.WOOD_PER_SETTLER;
    public static final int STONE_PER_HEAD = Resettlement.STONE_PER_SETTLER;

    /** Condition: the day this village last sent a party. */
    static final String LAST = "colonyAt";
    /** Conditions: {@code colonyDaughter:<id>} on the parent and {@code colonyParent:<id>} on the daughter; the value is the day. */
    static final String DAUGHTER = "colonyDaughter:";
    static final String PARENT = "colonyParent:";
    /** A plan that was outgrown (a building found no lot) this recently counts as full. */
    static final int OUTGROWN_DAYS = 30;
    /** A village attacked this recently does not send people away. */
    static final int ATTACK_QUIET_DAYS = 30;

    private Colonies() {
    }

    /** The people who go, by id, in a stable order, with the households they come from. */
    public record Party(List<UUID> members, List<String> households, int adults) {
        public int size() {
            return members.size();
        }
    }

    /** What the parent pays: the kit it gives each member and the treasury gift. */
    public record Cost(int food, int wood, int stone, int emeralds) {
    }

    /** Whether a village is ready, and if not, every reason why (the first is the main one). */
    public record Report(boolean ready, List<String> problems, Optional<Party> party, Optional<Cost> cost) {
    }

    // ----- the tie -----

    /** The villages this one sent a party to found. */
    public static List<UUID> daughters(Settlement settlement) {
        return ids(settlement, DAUGHTER);
    }

    /** The village this one was founded from, if it was founded from one. */
    public static Optional<UUID> parent(Settlement settlement) {
        return ids(settlement, PARENT).stream().findFirst();
    }

    /** True if the two are parent and daughter, either way round. */
    public static boolean kin(Settlement a, Settlement b) {
        return a.hasCondition(DAUGHTER + b.id()) || a.hasCondition(PARENT + b.id());
    }

    private static List<UUID> ids(Settlement settlement, String prefix) {
        List<UUID> out = new ArrayList<>();
        for (String key : settlement.conditions().keySet()) {
            if (key.startsWith(prefix)) {
                try {
                    out.add(UUID.fromString(key.substring(prefix.length())));
                } catch (IllegalArgumentException e) {
                    // a damaged key: ignore it
                }
            }
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    // ----- whether, who, how much -----

    /**
     * Whether the village could send a party today and, if not, why not. {@code villagesInWorld} is how many villages the world already
     * has (the caller counts them), for the cap.
     */
    public static Report report(Settlement parent, long day, int villagesInWorld, Rules rules) {
        List<String> problems = new ArrayList<>();
        if (parent.isAbandoned() || parent.population() == 0) {
            problems.add(parent.name() + " is empty or abandoned.");
        }
        if (parent.population() < rules.minPopulation()) {
            problems.add(parent.name() + " has " + parent.population() + " people; a village sends a party from " + rules.minPopulation() + ".");
        }
        Long last = parent.conditions().get(LAST);
        if (last != null && day >= last && day - last < rules.cooldownDays()) {
            problems.add("It sent a party " + (day - last) + " days ago; it waits " + rules.cooldownDays() + " days between parties.");
        }
        if (villagesInWorld >= rules.maxVillagesInWorld()) {
            problems.add("The world already has " + villagesInWorld + " villages (the most is " + rules.maxVillagesInWorld() + ").");
        }
        if (parent.hasCondition("famine") || SettlementSimulator.inDanger(parent, day)) {
            problems.add("It is in famine or in danger.");
        }
        if (parent.incidentDaysSince(day - ATTACK_QUIET_DAYS) > 0) {
            problems.add("It was attacked in the last " + ATTACK_QUIET_DAYS + " days.");
        }
        if (!full(parent, day)) {
            problems.add("It still has room to grow: free beds and lots in its plan.");
        }
        Optional<Party> party = chooseParty(parent, day, rules);
        Optional<Cost> cost = party.map(p -> costOf(parent, p, rules));
        if (party.isEmpty()) {
            problems.add("Nobody can be spared: a party needs a builder's household and " + rules.minParty() + " to " + rules.maxParty()
                    + " people without taking the last worker of a trade or leaving fewer than " + rules.minLeft() + " behind.");
        } else {
            Cost c = cost.get();
            int staying = parent.population() - party.get().size();
            if (spare(parent, ResourceType.FOOD, staying) < c.food()) {
                problems.add("The larder cannot spare " + c.food() + " food for the kit and still feed the people who stay.");
            }
            if (spare(parent, ResourceType.WOOD, staying) < c.wood()) {
                problems.add("The stores cannot spare " + c.wood() + " wood for the kit.");
            }
            if (parent.ledger().treasury() < c.emeralds()) {
                problems.add("The treasury cannot spare " + c.emeralds() + " emeralds.");
            }
        }
        return new Report(problems.isEmpty(), problems, party, cost);
    }

    /** A village is full when its beds are nearly all taken, or a building it wanted lately found no lot in its plan. */
    static boolean full(Settlement parent, long day) {
        Long outgrown = parent.conditions().get(Construction.OUTGROWN);
        return (parent.housingCapacity() > 0 && parent.freeBeds() <= 2) || (outgrown != null && day - outgrown <= OUTGROWN_DAYS);
    }

    /** What the people who stay must still hold, a head, after the kit has gone: twice what a village aims to keep of food, and of wood and stone. */
    static final int FOOD_RESERVE_PER_HEAD = 2 * SettlementSimulator.FOOD_WANTED_PER_HEAD;
    static final int STOCK_RESERVE_PER_HEAD = 2 * SettlementSimulator.STOCK_WANTED_PER_HEAD;

    /** How much of a material the village can give without leaving the people who stay short. */
    static int spare(Settlement parent, ResourceType type, int staying) {
        int reserve = staying * (type == ResourceType.FOOD ? FOOD_RESERVE_PER_HEAD : STOCK_RESERVE_PER_HEAD);
        return Math.max(0, parent.ledger().get(type) - reserve);
    }

    /** The kit and gift for a party; the stone is what can actually be spared (a village short of it sends less). */
    static Cost costOf(Settlement parent, Party party, Rules rules) {
        int n = party.size();
        int stone = Math.min(STONE_PER_HEAD * n, spare(parent, ResourceType.STONE, parent.population() - n));
        return new Cost(FOOD_PER_HEAD * n, WOOD_PER_HEAD * n, stone, rules.treasuryGift());
    }

    private static boolean canGo(Settlement parent, Resident r, long day) {
        if (parent.isPinned(r.id()) || parent.hasCondition(Births.AWAITING + r.id()) || parent.hasCondition(Migration.MOVING + r.id())) {
            return false;
        }
        if (!r.adult()) {
            return true; // a child goes with the household
        }
        Occupation job = r.occupation();
        boolean building = parent.projects().stream().anyMatch(p -> p.status() == ConstructionProject.Status.ACTIVE && r.id().equals(p.builder()));
        return r.stage(day) != LifeStage.ELDER && job != Occupation.GUARD && job != Occupation.MERCHANT && !building
                && !SettlementSimulator.recentlyMoved(parent, r, day);
    }

    private static boolean builder(Resident r) {
        return r.adult() && (r.built() > 0 || r.occupation() == Occupation.BUILDER);
    }

    /**
     * Who goes: the household of the village's most experienced builder, then households of the trades the village has most of, as long as the
     * party stays within its size, no trade the village lives on loses its last worker, the food workers do not fall below two and the
     * village keeps {@link Rules#minLeft} people. Empty if no such party of at least {@link Rules#minParty} can be made.
     */
    public static Optional<Party> chooseParty(Settlement parent, long day, Rules rules) {
        int room = Math.min(rules.maxParty(), parent.population() - rules.minLeft());
        if (room < rules.minParty()) {
            return Optional.empty();
        }
        List<Households.Household> eligible = new ArrayList<>();
        for (Households.Household h : Households.of(parent)) {
            if (h.members().stream().anyMatch(Resident::adult) && h.members().stream().allMatch(r -> canGo(parent, r, day))) {
                eligible.add(h); // (never children on their own)
            }
        }
        Map<Occupation, Integer> workers = new HashMap<>();
        for (Resident r : parent.residents()) {
            if (r.adult()) {
                workers.merge(r.occupation(), 1, Integer::sum);
            }
        }
        // the builder's household first
        long builders = parent.residents().stream().filter(Colonies::builder).count();
        Households.Household first = eligible.stream().filter(h -> h.members().stream().anyMatch(Colonies::builder))
                .filter(h -> builders - h.members().stream().filter(Colonies::builder).count() >= 1) // the parent keeps someone who can build
                .max(Comparator.comparingInt((Households.Household h) -> h.members().stream().filter(Colonies::builder).mapToInt(Resident::built).max().orElse(0))
                        .thenComparing(h -> h.head().id(), Comparator.reverseOrder()))
                .orElse(null);
        if (first == null || first.members().size() > room) {
            return Optional.empty();
        }
        Set<UUID> members = new LinkedHashSet<>();
        List<String> names = new ArrayList<>();
        if (!take(first, members, names, workers, room)) {
            return Optional.empty();
        }
        // then the households whose head's trade the village has most workers in, the biggest households first among equals
        List<Households.Household> rest = new ArrayList<>(eligible);
        rest.remove(first);
        rest.sort(Comparator.comparingInt((Households.Household h) -> -workers.getOrDefault(h.head().occupation(), 0))
                .thenComparingInt(h -> -h.members().size()).thenComparing(h -> h.head().id()));
        for (Households.Household h : rest) {
            if (members.size() >= room) {
                break;
            }
            take(h, members, names, workers, room);
        }
        if (members.size() < rules.minParty()) {
            return Optional.empty();
        }
        int adults = (int) members.stream().map(id -> parent.resident(id).orElseThrow()).filter(Resident::adult).count();
        return Optional.of(new Party(List.copyOf(members), List.copyOf(names), adults));
    }

    private static boolean take(Households.Household h, Set<UUID> members, List<String> names, Map<Occupation, Integer> workers, int room) {
        List<Resident> people = h.members();
        if (members.size() + people.size() > room) {
            return false;
        }
        Map<Occupation, Integer> after = new HashMap<>(workers);
        for (Resident r : people) {
            if (r.adult()) {
                after.merge(r.occupation(), -1, Integer::sum);
            }
        }
        for (Resident r : people) {
            if (!r.adult()) {
                continue;
            }
            int left = after.getOrDefault(r.occupation(), 0);
            boolean lives = r.occupation().produces() != null;
            int food = after.entrySet().stream().filter(e -> e.getKey().produces() == ResourceType.FOOD).mapToInt(Map.Entry::getValue).sum();
            if ((lives && left < 1) || (r.occupation().produces() == ResourceType.FOOD && food < 2)) {
                return false;
            }
        }
        workers.clear();
        workers.putAll(after);
        for (Resident r : people) {
            members.add(r.id());
        }
        names.add(h.describe());
        return true;
    }

    // ----- the departure -----

    /**
     * The party sets out and the new village is founded: the members move to {@code daughter} (a settlement the caller has made and added to the
     * registry; in a world with no clock ahead of it, the same day), the kit leaves the parent's stores and the gift its treasury, the daughter
     * starts with them, both remember the tie, and both histories say so. Marriages made before survive the move. The members are marked as moved
     * on paper, for the Minecraft layer to carry out in person (as a migration is). Returns false and changes nothing if the village is not
     * ready. {@code villagesInWorld} counts the villages the world had before the daughter was made, so the daughter itself is not counted against
     * the cap.
     */
    public static boolean settle(SettlementRegistry registry, Settlement parent, Settlement daughter, long day, int villagesInWorld, Rules rules) {
        Report report = report(parent, day, villagesInWorld, rules);
        if (!report.ready() || report.party().isEmpty() || report.cost().isEmpty()) {
            return false;
        }
        Party party = report.party().get();
        Cost cost = report.cost().get();
        // marriages, to put back after the moves (a move ends one)
        Map<UUID, UUID> wed = new HashMap<>();
        Map<UUID, Long> wedDay = new HashMap<>();
        for (UUID id : party.members()) {
            Resident r = parent.resident(id).orElseThrow();
            if (r.spouse() != null && party.members().contains(r.spouse())) {
                wed.put(id, r.spouse());
                wedDay.put(id, r.marriedDay());
            }
        }
        for (UUID id : wed.keySet()) {
            parent.resident(id).orElseThrow().endMarriage(); // (done here, so the parent's history does not say a spouse was left behind)
        }
        List<String> people = new ArrayList<>();
        for (UUID id : party.members()) {
            Resident r = parent.resident(id).orElseThrow();
            if (r.adult()) {
                people.add(r.fullName());
            }
            registry.transfer(r, parent, daughter, day, r.fullName() + " set out from " + parent.name() + " to found " + daughter.name() + ".",
                    r.fullName() + " came with the founders of " + daughter.name() + " from " + parent.name() + ".", true);
        }
        long shift = daughter.clockAhead() - parent.clockAhead();
        for (Map.Entry<UUID, UUID> pair : wed.entrySet()) {
            Resident a = daughter.resident(pair.getKey()).orElse(null);
            if (a != null && daughter.resident(pair.getValue()).isPresent()) {
                a.marry(pair.getValue(), wedDay.get(pair.getKey()) + shift);
            }
        }
        Ledger from = parent.ledger();
        Ledger to = daughter.ledger();
        from.take(ResourceType.FOOD, cost.food());
        from.take(ResourceType.WOOD, cost.wood());
        from.take(ResourceType.STONE, cost.stone());
        to.add(Commodity.BREAD, cost.food());
        to.add(ResourceType.WOOD, cost.wood());
        to.add(ResourceType.STONE, cost.stone());
        parent.ledger().spendTreasury(cost.emeralds());
        daughter.ledger().addTreasury(cost.emeralds());
        parent.conditions().put(LAST, day);
        parent.conditions().put(DAUGHTER + daughter.id(), day);
        daughter.conditions().put(PARENT + parent.id(), day + shift);
        String who = String.join("; ", party.households());
        parent.record(day, HistoryEvent.Kind.MILESTONE, party.size() + " people set out from " + parent.name() + " to found " + daughter.name()
                + ": " + who + ". They took " + cost.food() + " food, " + cost.wood() + " wood, " + cost.stone() + " stone and " + cost.emeralds() + " emeralds.");
        daughter.record(day + shift, HistoryEvent.Kind.MILESTONE, daughter.name() + " was founded from " + parent.name() + " by "
                + party.size() + " of its people (" + String.join(", ", people.subList(0, Math.min(people.size(), 4)))
                + (people.size() > 4 ? " and others" : "") + ").");
        return true;
    }

    /** e.g. "Elderbrook is the daughter of Mossmoor, and has sent no party of its own." */
    public static String describe(Settlement settlement, SettlementRegistry registry) {
        StringBuilder text = new StringBuilder();
        parent(settlement).flatMap(registry::get).ifPresent(p -> text.append(settlement.name()).append(" was founded from ").append(p.name()).append("."));
        List<String> daughters = new ArrayList<>();
        for (UUID id : daughters(settlement)) {
            registry.get(id).ifPresent(d -> daughters.add(d.name()));
        }
        if (!daughters.isEmpty()) {
            text.append(text.length() > 0 ? " " : "").append(settlement.name()).append(" founded ").append(String.join(", ", daughters)).append(".");
        }
        return text.toString();
    }
}
