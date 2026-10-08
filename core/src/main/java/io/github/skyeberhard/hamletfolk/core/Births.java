package io.github.skyeberhard.hamletfolk.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * R4.28: villagers born in the simulation, whether or not a player is near. A well-fed village with a free bed and two
 * adults who are a man and a woman (not elders, not close kin) has a child at most once every {@link #COOLDOWN_DAYS}
 * days. The child is a resident at once, under a stand-in id until the Paper layer spawns its villager and moves the
 * resident onto the villager's id ({@link SettlementRegistry#bringToLife}); until then it grows up on the village's
 * clock. Vanilla breeding near a player still happens as well, and is recorded as before.
 */
public final class Births {
    /** Food in store per head before a village has children: what it keeps back from its merchant (R3.9). */
    static final int FOOD_PER_HEAD = SettlementSimulator.FOOD_KEPT_PER_HEAD;
    /** What a birth costs the stores: the game's villagers eat 12 food points each, three bread, to breed. */
    static final int FOOD_COST = 6;
    static final int COOLDOWN_DAYS = 3;
    /** Days a child born in the simulation, with no villager yet, takes to grow up (a vanilla baby takes one game day). */
    static final int GROW_UP_DAYS = 1;
    static final String LAST_BIRTH = "bornAt";
    /** Prefix of the condition marking a resident born in the simulation who has no villager yet. */
    public static final String AWAITING = "awaitingVillager:";

    private Births() {
    }

    /**
     * Has a child if one is due today; returns it. Call once a day per village (more often does nothing, by the cooldown).
     */
    public static Optional<Resident> run(SettlementRegistry registry, Settlement settlement, long day) {
        Optional<Resident[]> parents = parentsIfDue(settlement, day);
        if (parents.isEmpty()) {
            return Optional.empty();
        }
        Resident mother = parents.get()[0];
        Resident father = parents.get()[1];
        UUID childId = UUID.nameUUIDFromBytes(("hamletfolk-born:" + settlement.id() + ":" + day).getBytes(StandardCharsets.UTF_8));
        if (registry.resident(childId).isPresent()) {
            return Optional.empty();
        }
        settlement.ledger().take(ResourceType.FOOD, FOOD_COST);
        settlement.flow().recordConsumed(ResourceType.FOOD, day, FOOD_COST);
        Resident child = registry.enroll(settlement, childId, Occupation.UNEMPLOYED, false, day, mother.id(), father.id());
        settlement.conditions().put(AWAITING + childId, day);
        settlement.conditions().put(LAST_BIRTH, day);
        settlement.record(day, HistoryEvent.Kind.BIRTH, child.fullName() + " was born to " + mother.fullName() + " and "
                + father.fullName() + ".");
        return Optional.of(child);
    }

    /** The two residents who would have a child today, mother first, or empty if no child is due. */
    static Optional<Resident[]> parentsIfDue(Settlement settlement, long day) {
        Long last = settlement.conditions().get(LAST_BIRTH);
        if (settlement.isAbandoned() || settlement.freeBeds() <= 0 || settlement.hasCondition("famine")
                || SettlementSimulator.inDanger(settlement, day) || (last != null && day - last < COOLDOWN_DAYS)
                || settlement.ledger().get(ResourceType.FOOD) < FOOD_PER_HEAD * Math.max(1, settlement.population())) {
            return Optional.empty();
        }
        List<Resident> adults = new ArrayList<>();
        for (Resident r : settlement.residents()) {
            // Not one born here who has no villager yet: their children would carry an id that is about to change.
            if (r.adult() && r.stage(day) == LifeStage.ADULT && !settlement.hasCondition(AWAITING + r.id())) {
                adults.add(r);
            }
        }
        adults.sort(Comparator.comparing(Resident::id)); // repeatable
        for (Resident woman : adults) {
            if (woman.gender() != Gender.FEMALE) {
                continue;
            }
            for (Resident man : adults) {
                if (man.gender() == Gender.MALE && !kin(woman, man)) {
                    return Optional.of(new Resident[] {woman, man});
                }
            }
        }
        return Optional.empty();
    }

    /** True for a parent and child, or brother and sister. */
    static boolean kin(Resident a, Resident b) {
        if (a.id().equals(b.parentA()) || a.id().equals(b.parentB()) || b.id().equals(a.parentA()) || b.id().equals(a.parentB())) {
            return true;
        }
        return (a.parentA() != null && (a.parentA().equals(b.parentA()) || a.parentA().equals(b.parentB())))
                || (a.parentB() != null && (a.parentB().equals(b.parentA()) || a.parentB().equals(b.parentB())));
    }

    /** A child born in the simulation with no villager yet grows up on the village's clock. */
    static void growUp(Settlement settlement, long day) {
        for (Resident r : settlement.residents()) {
            if (!r.adult() && settlement.hasCondition(AWAITING + r.id()) && day - r.bornDay() >= GROW_UP_DAYS) {
                r.setAdult(true);
            }
        }
    }

    /** The residents of a village born in the simulation who still have no villager. */
    public static List<Resident> awaiting(Settlement settlement) {
        List<Resident> out = new ArrayList<>();
        for (Resident r : settlement.residents()) {
            if (settlement.hasCondition(AWAITING + r.id())) {
                out.add(r);
            }
        }
        return out;
    }
}
