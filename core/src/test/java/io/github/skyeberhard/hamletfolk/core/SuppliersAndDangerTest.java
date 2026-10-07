package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.24 permanent suppliers that rest at the limit; R5.7 no newcomers into danger; R3.15 purchases pay the treasury. */
class SuppliersAndDangerTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = SettlementSimulator.withOldAgeDeaths(false);
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(40, next++), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50), job, true, 10_000,
                null, null, Needs.initial());
    }

    /** A fed village with {@code jobless} jobless adults and plenty of everything but what a test takes away. */
    private Settlement village(int jobless) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < jobless; i++) {
            s.addResident(person(Occupation.UNEMPLOYED));
        }
        s.ledger().add(ResourceType.FOOD, 2000);
        s.ledger().add(ResourceType.WOOD, 1000);
        s.ledger().add(ResourceType.STONE, 1000);
        s.ledger().add(ResourceType.METAL, 1000);
        s.ledger().add(ResourceType.TOOLS, 1000);
        return s;
    }

    private static long count(Settlement s, Occupation job) {
        return s.residents().stream().filter(r -> r.occupation() == job).count();
    }

    @Test
    void aVillageKeepsALumberjackAndOnceItHasAMineAMinerWhateverItHolds() {
        Settlement s = village(6);
        simulator.simulateDay(s, 1);
        assertEquals(1, count(s, Occupation.LUMBERJACK), "wood is plentiful, but the village keeps one");
        assertEquals(0, count(s, Occupation.MINER), "no mine, no miner");
        s.registerBuilding(new Building(BuildingType.MINE, 0, 64, 0, 1, "test"));
        simulator.simulateDay(s, 2);
        assertEquals(1, count(s, Occupation.MINER));
        simulator.simulateDay(s, 3);
        assertEquals(1, count(s, Occupation.LUMBERJACK) + 0 * count(s, Occupation.MINER), "one is enough while nothing is short");
        assertEquals(1, count(s, Occupation.MINER));
    }

    @Test
    void notInAFamineNotWhileFoodIsShortAndNotForATinyVillage() {
        Settlement famine = village(6);
        famine.conditions().put("famine", 1L);
        simulator.simulateDay(famine, 2);
        assertEquals(0, count(famine, Occupation.LUMBERJACK));

        Settlement tiny = village(3);
        simulator.simulateDay(tiny, 1);
        assertEquals(0, count(tiny, Occupation.LUMBERJACK), "four residents first");
    }

    @Test
    void aSupplierRestsAtTheLimitAndGoesBackToWorkBelowNineTenthsOfIt() {
        Settlement s = village(0);
        Resident lumberjack = person(Occupation.LUMBERJACK);
        s.addResident(lumberjack);
        for (int i = 0; i < 3; i++) {
            s.addResident(person(Occupation.FARMER));
        }
        int limit = SettlementSimulator.capacity(s, ResourceType.WOOD);
        s.ledger().take(ResourceType.WOOD, 10_000);
        s.ledger().add(ResourceType.WOOD, limit);
        long produced = s.flow().produced(ResourceType.WOOD, 1);
        simulator.simulateDay(s, 1);
        assertTrue(SettlementSimulator.isResting(s, lumberjack));
        assertEquals(produced, s.flow().produced(ResourceType.WOOD, 1), "nothing made while the stores are full");
        assertEquals(limit, s.ledger().get(ResourceType.WOOD));

        s.ledger().take(ResourceType.WOOD, limit / 20); // 95%: still resting
        simulator.simulateDay(s, 2);
        assertTrue(SettlementSimulator.isResting(s, lumberjack));

        s.ledger().take(ResourceType.WOOD, limit / 5); // 75%: back to work
        simulator.simulateDay(s, 3);
        assertFalse(SettlementSimulator.isResting(s, lumberjack));
        assertTrue(s.flow().produced(ResourceType.WOOD, 3) > 0);

        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        loaded.ledger().add(ResourceType.WOOD, 10_000);
        simulator.simulateDay(loaded, 4);
        assertTrue(loaded.hasCondition(SettlementSimulator.RESTING + "LUMBERJACK"), "the rest survives a save");
    }

    @Test
    void aVillageUnderAttackOrInDangerTakesNoNewcomersAndNoMigrants() {
        Settlement s = village(4);
        simulator.simulateTo(s, 5, 100);
        assertTrue(simulator.newcomerDue(s, 3), "calm");
        s.recordIncident(5);
        assertFalse(simulator.newcomerDue(s, 3), "attacked today");
        simulator.simulateTo(s, 5 + SettlementSimulator.INCIDENT_WINDOW_DAYS, 100);
        assertTrue(simulator.newcomerDue(s, 3), "the window has passed");
        s.raiseThreat(60);
        assertFalse(simulator.newcomerDue(s, 3), "danger of 30 or more");

        assertTrue(migrantsArrive(0), "a calm, well-fed village with a free bed draws people from a poor one");
        assertFalse(migrantsArrive(40), "but not while its danger is high");
    }

    /** Whether anyone moves from a poor village to a rich one with a free bed and the given danger, over two months. */
    private boolean migrantsArrive(double threat) {
        SettlementRegistry own = new SettlementRegistry();
        Settlement rich = own.found("world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            rich.addResident(person(Occupation.FARMER));
        }
        rich.ledger().add(ResourceType.FOOD, 5000);
        rich.housing().setChunk(0, 0, 20);
        rich.raiseThreat(threat);
        Settlement poor = own.found("world", 100, 0, 0);
        for (int i = 0; i < 5; i++) {
            poor.addResident(person(Occupation.UNEMPLOYED));
        }
        for (long day = 1; day <= 60; day++) {
            if (Migration.run(own, poor, day).isPresent()) {
                return true;
            }
        }
        return false;
    }

    @Test
    void whatAPlayerPaysForGoodsGoesToTheTreasuryUpToItsRoom() {
        Settlement s = village(4);
        assertEquals(3, Trading.purchaseIncome(s, "EMERALD", 3, "BREAD", 100));
        assertEquals(3, s.ledger().treasury());
        assertEquals(2, Trading.purchaseIncome(s, "EMERALD", 5, "WHEAT", 2), "only as much as fits");
        assertEquals(18, Trading.purchaseIncome(s, "EMERALD_BLOCK", 2, "IRON_INGOT", 100), "a block is nine");
        assertEquals(0, Trading.purchaseIncome(s, "WHEAT", 20, "EMERALD", 100), "a sale to a villager pays nothing in");
        assertEquals(0, Trading.purchaseIncome(s, "EMERALD", 1, "EMERALD_BLOCK", 100), "not an exchange of money");
        assertEquals(23, s.ledger().treasury());
    }
}
