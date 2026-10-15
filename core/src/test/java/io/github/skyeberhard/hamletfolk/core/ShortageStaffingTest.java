package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.35: a shortage with a free place is filled by moving a worker even when nobody is out of work. */
class ShortageStaffingTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator(false, o -> true);
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(60, next++), "T", "P" + next, next % 2 == 0 ? Gender.FEMALE : Gender.MALE,
                new Traits(50, 50, 50, 80), job, true, -30, null, null, Needs.initial());
    }

    private long count(Settlement s, Occupation job) {
        return s.residents().stream().filter(r -> r.adult() && r.occupation() == job).count();
    }

    private Settlement village(int farmers, int lumberjacks) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < farmers; i++) {
            s.addResident(person(Occupation.FARMER));
        }
        for (int i = 0; i < lumberjacks; i++) {
            s.addResident(person(Occupation.LUMBERJACK));
        }
        return s;
    }

    @Test
    void aHungryVillageMovesALumberjackOntoAFreeFarmPlace() {
        Settlement s = village(3, 3);
        assertEquals(0, count(s, Occupation.UNEMPLOYED));
        simulator.simulateDay(s, 1);
        assertEquals(4, count(s, Occupation.FARMER), "one worker a day moves to the farm");
        assertEquals(2, count(s, Occupation.LUMBERJACK));
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("to work as a farmer")));
    }

    @Test
    void aPinnedWorkerAndTheOnlyLumberjackAreNotMoved() {
        Settlement s = village(3, 1);
        simulator.simulateDay(s, 1);
        assertEquals(1, count(s, Occupation.LUMBERJACK), "the only lumberjack stays");
        Settlement t = village(3, 2);
        t.residents().stream().filter(r -> r.occupation() == Occupation.LUMBERJACK).forEach(r -> t.setPinned(r.id(), true, 1));
        simulator.simulateDay(t, 1);
        assertEquals(2, count(t, Occupation.LUMBERJACK), "pinned workers stay");
    }

    @Test
    void aFedVillageWithMetalButNoSmithGetsOne() {
        Settlement s = village(5, 3);
        s.ledger().add(ResourceType.FOOD, 5000);
        s.ledger().add(Commodity.IRON, 40);
        assertEquals(0, count(s, Occupation.TOOLSMITH));
        simulator.simulateDay(s, 1);
        assertEquals(1, count(s, Occupation.TOOLSMITH));
        simulator.simulateDay(s, 2);
        assertEquals(1, count(s, Occupation.TOOLSMITH), "one is enough");
    }

    @Test
    void aFamineStillGetsASmithOnceTheFarmsAreFull() {
        Settlement s = village(4, 3);
        SettlementSimulator farmsFull = new SettlementSimulator(false, o -> o.produces() != ResourceType.FOOD);
        s.ledger().add(Commodity.IRON, 40);
        farmsFull.simulateDay(s, 1);
        assertEquals(1, count(s, Occupation.TOOLSMITH));
        assertEquals(4, count(s, Occupation.FARMER));
    }

    @Test
    void theLeastExperiencedIsSparedAndNobodyIsMovedTwiceInAWeek() {
        Settlement s = village(3, 3);
        java.util.List<Resident> jacks = s.residents().stream().filter(r -> r.occupation() == Occupation.LUMBERJACK).toList();
        jacks.get(0).setXp(40);
        jacks.get(2).setXp(40);
        assertEquals(jacks.get(1), SettlementSimulator.spareWorker(s, 1), "the novice goes, not the masters");
        simulator.simulateDay(s, 1);
        assertEquals(Occupation.FARMER, jacks.get(1).occupation());
        assertTrue(SettlementSimulator.spareWorker(s, 2) != jacks.get(1), "just moved: not moved again");
        SettlementSimulator.noteMoved(s, jacks.get(0), 1);
        SettlementSimulator.noteMoved(s, jacks.get(2), 1);
        assertEquals(null, SettlementSimulator.spareWorker(s, 3), "everyone spare was moved this week");
        assertTrue(SettlementSimulator.spareWorker(s, 1 + SettlementSimulator.MOVE_COOLDOWN_DAYS) != null);
        assertTrue(s.conditions().keySet().stream().noneMatch(k -> k.startsWith("movedDay:") && k.endsWith(jacks.get(0).id().toString())),
                "an old record is dropped once read");
    }

    @Test
    void movesStopWhenTheFarmIsFullUsingRealBuildings() {
        SettlementSimulator real = SettlementSimulator.withOldAgeDeaths(false);
        Settlement s = village(3, 3);
        s.registerBuilding(new Building(BuildingType.FARM, 5, 64, 5, 0, "test"));
        s.ledger().add(ResourceType.FOOD, 1);
        for (long day = 1; day <= 12; day++) {
            s.ledger().take(ResourceType.FOOD, s.ledger().get(ResourceType.FOOD));
            real.simulateDay(s, day);
        }
        assertEquals(4, count(s, Occupation.FARMER), "the farm has four places and no more are filled");
    }

    @Test
    void aFamineWithAFreeFarmFillsItBeforeAnySmith() {
        Settlement s = village(3, 3);
        s.conditions().put("famine", 0L);
        s.ledger().add(Commodity.IRON, 40);
        simulator.simulateDay(s, 1);
        assertEquals(4, count(s, Occupation.FARMER));
        assertEquals(0, count(s, Occupation.TOOLSMITH));
    }

    @Test
    void aFamineWithFullFarmsTakesTheSmithFromTheJoblessToo() {
        Settlement s = village(4, 2);
        s.addResident(person(Occupation.UNEMPLOYED));
        s.conditions().put("famine", 0L);
        s.ledger().add(Commodity.IRON, 40);
        new SettlementSimulator(false, o -> o.produces() != ResourceType.FOOD).simulateDay(s, 1);
        assertEquals(1, count(s, Occupation.TOOLSMITH));
        assertEquals(2, count(s, Occupation.LUMBERJACK), "nobody was taken off a job for it");
    }

    @Test
    void noSmithWithNothingForThemToWorkOn() {
        Settlement s = village(5, 3);
        s.ledger().add(ResourceType.FOOD, 5000);
        s.ledger().add(Commodity.LAPIS, 40); // metal by category, but not what a smith works
        s.ledger().add(Commodity.RAW_IRON, 40); // ore, with no fuel to smelt it
        simulator.simulateDay(s, 1);
        assertEquals(0, count(s, Occupation.TOOLSMITH));
    }

    @Test
    void aJoblessResidentIsGivenFoodWorkAndNobodyElseMovesThatDay() {
        Settlement s = village(3, 3);
        s.addResident(person(Occupation.UNEMPLOYED));
        simulator.simulateDay(s, 1);
        assertEquals(4, count(s, Occupation.FARMER), "the jobless one goes to the farm");
        assertEquals(3, count(s, Occupation.LUMBERJACK), "and no worker is moved as well");
    }

    /** R4.41: Hollowstead's case: food made equals food eaten, so the stores never fill, and every other trade is the only one of its kind. */
    @Test
    void aSupplierWithFullStoresIsMovedOntoTheFarmEvenIfItIsTheOnlyOne() {
        Settlement s = village(2, 1);
        s.addResident(person(Occupation.MINER));
        s.ledger().add(ResourceType.WOOD, SettlementSimulator.capacity(s, ResourceType.WOOD));
        simulator.simulateDay(s, 1);
        assertEquals(1, count(s, Occupation.LUMBERJACK), "the first day it is not yet resting");
        simulator.simulateDay(s, 2);
        assertEquals(3, count(s, Occupation.FARMER), "resting at the storage limit, the lumberjack goes to the farm");
        assertEquals(0, count(s, Occupation.LUMBERJACK));
        assertEquals(1, count(s, Occupation.MINER), "the miner, whose stores are not full, stays");
    }

    @Test
    void aSupplierWhoseStoresAreNotFullIsStillNotMovedAsTheOnlyOne() {
        Settlement s = village(2, 1);
        s.ledger().add(ResourceType.WOOD, 10);
        for (long day = 1; day <= 5; day++) {
            simulator.simulateDay(s, day);
        }
        assertEquals(1, count(s, Occupation.LUMBERJACK));
    }

    @Test
    void aTradeGivenUpForTheFarmsIsTakenUpAgainOnceItsStoresRunLow() {
        Settlement s = village(4, 1);
        s.addResident(person(Occupation.MINER));
        s.ledger().add(ResourceType.WOOD, SettlementSimulator.capacity(s, ResourceType.WOOD));
        simulator.simulateDay(s, 1);
        simulator.simulateDay(s, 2);
        assertEquals(0, count(s, Occupation.LUMBERJACK), "given up for the farms");
        assertTrue(s.hasCondition("gaveUp:LUMBERJACK"));
        s.ledger().take(ResourceType.WOOD, s.ledger().get(ResourceType.WOOD)); // the wood is used up
        s.ledger().add(ResourceType.FOOD, 20 * 6 * 2); // and food is plentiful, so a farmer can be spared
        for (long day = 3; day <= 12; day++) {
            simulator.simulateDay(s, day);
        }
        assertEquals(1, count(s, Occupation.LUMBERJACK), "wood is made again");
        assertTrue(!s.hasCondition("gaveUp:LUMBERJACK"));
    }

    @Test
    void aVillageThatIsFedButNotPlentifulKeepsItsFarmersAtTheirFarms() {
        Settlement s = village(5, 0);
        s.ledger().add(ResourceType.FOOD, 15 * 5); // fed, but below twice the wanted level
        for (long day = 1; day <= 5; day++) {
            simulator.simulateDay(s, day);
        }
        assertEquals(0, count(s, Occupation.LUMBERJACK), "food workers are only spared from a full larder");
    }
}
