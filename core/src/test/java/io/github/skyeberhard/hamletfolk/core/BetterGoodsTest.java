package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.57: what a skilled hand makes: diamond tools from an expert smith, more bread from a better farmer. */
class BetterGoodsTest {
    private static int n;
    private final SettlementSimulator simulator = SettlementSimulator.withOldAgeDeaths(false);

    private Resident person(Occupation job, int days) {
        Resident r = new Resident(new UUID(82, n++), "Test", "Smith" + n, Gender.MALE, new Traits(70, 50, 50, 50), job, true, 10_000, null, null, Needs.initial());
        if (days > 0) {
            r.restoreXp(job, days);
        }
        return r;
    }

    private Settlement village(Occupation job, int days) {
        Settlement s = new Settlement(new UUID(83, n++), "Goodsford", "world", 0, 0, 0);
        for (int i = 0; i < 3; i++) {
            s.addResident(person(Occupation.FARMER, 0));
        }
        s.addResident(person(job, days));
        s.housing().setChunk(0, 0, 10);
        s.setLastSimulatedDay(0);
        s.ledger().add(Commodity.BREAD, 500);
        return s;
    }

    @Test
    void anExpertSmithForgesDiamondToolsOnceTheVillageHasItsTools() {
        Settlement s = village(Occupation.TOOLSMITH, 60); // level 4
        s.ledger().add(Commodity.IRON_TOOLS, 100);
        s.ledger().add(Commodity.IRON, 50);
        s.ledger().add(Commodity.DIAMOND, 12);
        simulator.simulateTo(s, 10, 100);
        assertTrue(s.ledger().get(Commodity.DIAMOND_TOOLS) > 0, "diamond tools were forged");
        assertTrue(s.ledger().get(Commodity.DIAMOND) < 12 && s.ledger().get(Commodity.DIAMOND) % 3 == 0, "three diamonds a batch");
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("forged the first diamond tools")));
    }

    @Test
    void aJourneymanOrASmithWithTheVillageShortOfToolsDoesNot() {
        Settlement journeyman = village(Occupation.TOOLSMITH, 30); // level 3
        journeyman.ledger().add(Commodity.IRON_TOOLS, 100);
        journeyman.ledger().add(Commodity.IRON, 50);
        journeyman.ledger().add(Commodity.DIAMOND, 12);
        simulator.simulateTo(journeyman, 10, 100);
        assertEquals(0, journeyman.ledger().get(Commodity.DIAMOND_TOOLS));
        assertEquals(12, journeyman.ledger().get(Commodity.DIAMOND));
        Settlement short_ = village(Occupation.TOOLSMITH, 60);
        short_.ledger().add(Commodity.IRON, 50);
        short_.ledger().add(Commodity.DIAMOND, 12);
        simulator.simulateTo(short_, 3, 100);
        assertFalse(short_.hasCondition(SettlementSimulator.FIRST_DIAMOND_TOOLS), "the plain tools come first");
    }

    @Test
    void anExpertSmithWithFewDiamondsFallsBackToIron() {
        Settlement s = village(Occupation.TOOLSMITH, 60);
        s.ledger().add(Commodity.IRON_TOOLS, 100);
        s.ledger().add(Commodity.IRON, 5);
        s.ledger().add(Commodity.DIAMOND, 2); // not enough for a batch
        simulator.simulateTo(s, 2, 100);
        assertEquals(0, s.ledger().get(Commodity.DIAMOND_TOOLS));
        assertTrue(s.flow().produced(ResourceType.TOOLS, s.lastSimulatedDay()) > 0 || s.ledger().get(Commodity.DIAMOND) == 2, "it carried on with iron, or nothing happened");
        assertEquals(2, s.ledger().get(Commodity.DIAMOND), "the two diamonds are not spent");
    }

    @Test
    void aSkilledFarmerBakesMoreBread() {
        assertEquals(SettlementSimulator.BAKE_PER_FARMER, TradeLevel.bakes(1));
        assertEquals(SettlementSimulator.BAKE_PER_FARMER, TradeLevel.bakes(2));
        assertEquals(SettlementSimulator.BAKE_PER_FARMER + 1, TradeLevel.bakes(3));
        assertEquals(SettlementSimulator.BAKE_PER_FARMER + 2, TradeLevel.bakes(TradeLevel.MASTER));
        Settlement novice = new Settlement(new UUID(84, 1), "Bakeford", "world", 0, 0, 0);
        novice.addResident(person(Occupation.FARMER, 0));
        novice.ledger().add(Commodity.GRAIN, 100);
        SettlementSimulator.process(novice, 1);
        Settlement master = new Settlement(new UUID(84, 2), "Bakeford", "world", 0, 0, 0);
        master.addResident(person(Occupation.FARMER, 100));
        master.ledger().add(Commodity.GRAIN, 100);
        SettlementSimulator.process(master, 1);
        assertTrue(master.ledger().get(Commodity.BREAD) > novice.ledger().get(Commodity.BREAD), "a master bakes more");
    }
}
