package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** R4.50: nobody idles while a workplace stands empty. */
class IdleFillTest {
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(140, next++), "T", "P" + next, Gender.FEMALE, new Traits(50, 50, 50, 80), job, true,
                -30, null, null, Needs.initial());
    }

    /** Four jobless adults, food and everything else well stocked: nothing is short. */
    private Settlement wellStocked() {
        Settlement s = new Settlement(new UUID(141, 141), "Idleham", "world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(person(Occupation.UNEMPLOYED));
        }
        for (ResourceType type : ResourceType.values()) {
            s.ledger().add(type, Math.min(SettlementSimulator.capacity(s, type), 60));
        }
        return s;
    }

    private long count(Settlement s, Occupation job) {
        return s.residents().stream().filter(r -> r.occupation() == job).count();
    }

    @Test
    void anIdleHandTakesAFreePlaceWhenNothingIsShort() {
        Settlement s = wellStocked();
        SettlementSimulator onlyFarms = new SettlementSimulator(false, (Predicate<Occupation>) o -> o == Occupation.FARMER);
        for (long day = 1; day <= 6; day++) {
            onlyFarms.simulateDay(s, day);
        }
        assertEquals(4, count(s, Occupation.FARMER), "the farm is the only workplace, and nobody stays idle beside it");
        assertEquals(0, count(s, Occupation.UNEMPLOYED));
    }

    @Test
    void theTradeWithTheLeastInStoreIsTakenFirst() {
        SettlementSimulator two = new SettlementSimulator(false, (Predicate<Occupation>) o -> o == Occupation.FARMER || o == Occupation.LUMBERJACK);
        Settlement plentyOfWood = threeIdle();
        plentyOfWood.ledger().take(ResourceType.FOOD, plentyOfWood.ledger().get(ResourceType.FOOD) - 40); // food 40 of 30 wanted: least
        two.simulateDay(plentyOfWood, 1);
        assertEquals(1, count(plentyOfWood, Occupation.FARMER), "the farm, since food is what the village holds least of");
        Settlement plentyOfFood = threeIdle();
        plentyOfFood.ledger().take(ResourceType.WOOD, plentyOfFood.ledger().get(ResourceType.WOOD) - 10); // wood 10 of 9 wanted: least
        two.simulateDay(plentyOfFood, 1);
        assertEquals(1, count(plentyOfFood, Occupation.LUMBERJACK), "the lumberjack, since wood is");
    }

    /** Three idle adults (under four, so no permanent lumberjack or miner, R4.24) with plenty of everything. */
    private Settlement threeIdle() {
        Settlement s = wellStocked();
        s.removeResident(s.residents().iterator().next().id());
        for (ResourceType type : ResourceType.values()) {
            s.ledger().add(type, 60);
        }
        return s;
    }

    @Test
    void anIdleHandPlacedByIdleFillIsTheOneADraftedBuilderIsTaken() {
        Settlement s = threeIdle();
        VillagePlan plan = PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64));
        s.setPlan(plan);
        SettlementSimulator onlyFarms = new SettlementSimulator(false, (Predicate<Occupation>) o -> o == Occupation.FARMER);
        for (long day = 1; day <= 3; day++) {
            onlyFarms.simulateDay(s, day);
        }
        assertEquals(3, count(s, Occupation.FARMER), "everyone found work");
        s.addProject(new ConstructionProject(1, BuildingType.HOUSE, 1, 0, "plains", 0, 64, 0, plan.lots().get(0).id(), 4));
        for (long day = 4; day <= 8; day++) {
            onlyFarms.simulateDay(s, day);
        }
        assertEquals(1, count(s, Occupation.BUILDER), "a project waiting three days is built: a farmer just placed is not held back");
    }

    @Test
    void nobodyTakesAPlaceWhoseProductTheStoresCannotHold() {
        Settlement s = wellStocked();
        s.removeResident(s.residents().iterator().next().id()); // under four, so no permanent lumberjack or miner (R4.24)
        for (ResourceType type : ResourceType.values()) {
            s.ledger().add(type, SettlementSimulator.capacity(s, type)); // every store full
        }
        SettlementSimulator all = new SettlementSimulator(false, o -> true);
        for (long day = 1; day <= 4; day++) {
            all.simulateDay(s, day);
        }
        // (food spoils a little every day, so there is always room for more of that; wood, stone and tools are full)
        assertEquals(0, count(s, Occupation.LUMBERJACK) + count(s, Occupation.MASON) + count(s, Occupation.MINER)
                + count(s, Occupation.TOOLSMITH), "nobody takes a place whose product the stores cannot hold");
    }

    @Test
    void aFamineLeavesTheIdleToForage() {
        Settlement s = wellStocked();
        s.conditions().put("famine", 0L);
        SettlementSimulator all = new SettlementSimulator(false, o -> o != Occupation.FARMER);
        all.simulateDay(s, 1);
        assertEquals(4, count(s, Occupation.UNEMPLOYED), "only food work will do in a famine, and there is no farm place");
    }
}
