package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R5.9: smiths forge iron golems for a village that has been attacked. */
class GolemsTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private int next = 1;

    private Settlement village(int residents, boolean smith) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < residents; i++) {
            Occupation job = smith && i == 0 ? Occupation.TOOLSMITH : Occupation.FARMER;
            s.addResident(new Resident(new UUID(80, next++), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50), job, true, 10_000,
                    null, null, Needs.initial()));
        }
        s.ledger().add(Commodity.IRON, 200);
        s.ledger().add(Commodity.PRODUCE, 50);
        return s;
    }

    @Test
    void aVillageNeverAttackedWantsNoGolems() {
        Settlement s = village(12, true);
        assertEquals(0, Golems.wanted(s));
        assertTrue(Golems.forge(s, 10).isEmpty());
        assertEquals(200, s.ledger().get(Commodity.IRON));
    }

    @Test
    void anAttackedVillageWantsOneGolemPerTenResidentsAndASmithForgesThem() {
        Settlement small = village(4, true);
        small.recordIncident(1);
        assertEquals(1, Golems.wanted(small), "at least one");
        Settlement big = village(25, true);
        big.recordIncident(1);
        assertEquals(2, Golems.wanted(big));

        assertTrue(Golems.forge(big, 10).isPresent());
        assertEquals(200 - Golems.IRON, big.ledger().get(Commodity.IRON));
        assertEquals(50 - Golems.PRODUCE, big.ledger().get(Commodity.PRODUCE));
        assertEquals(1, Golems.awaiting(big));
        assertTrue(big.history().stream().anyMatch(e -> e.text().contains("forged an iron golem")));
        assertTrue(Golems.forge(big, 12).isEmpty(), "one every five days at most");
        assertTrue(Golems.forge(big, 15).isPresent());
        assertTrue(Golems.forge(big, 20).isEmpty(), "two wanted, two forged");
    }

    @Test
    void noSmithNoIronOrNoPumpkinMeansNoGolem() {
        Settlement noSmith = village(5, false);
        noSmith.recordIncident(1);
        assertTrue(Golems.forge(noSmith, 10).isEmpty());
        Settlement noIron = village(5, true);
        noIron.recordIncident(1);
        noIron.ledger().take(Commodity.IRON, 200 - Golems.IRON + 1);
        assertTrue(Golems.forge(noIron, 10).isEmpty(), "35 iron is not enough");
        Settlement noPumpkin = village(5, true);
        noPumpkin.recordIncident(1);
        noPumpkin.ledger().take(Commodity.PRODUCE, 50);
        assertTrue(Golems.forge(noPumpkin, 10).isEmpty());
    }

    @Test
    void aGolemInTheWorldIsTheVillagesAndALostOneIsForgedAgain() {
        Settlement s = village(5, true);
        s.recordIncident(1);
        Golems.forge(s, 10);
        UUID golem = new UUID(81, 1);
        Golems.arrived(s, golem, 11);
        assertEquals(0, Golems.awaiting(s));
        assertEquals(1, Golems.owned(s));
        assertTrue(Golems.owns(s, golem));
        assertTrue(Golems.forge(s, 30).isEmpty(), "it has the one it wants");

        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertTrue(Golems.owns(loaded, golem), "remembered across a save");

        Golems.lost(s, golem, 31);
        assertFalse(Golems.owns(s, golem));
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("golem guarding")));
        assertTrue(Golems.forge(s, 31).isPresent(), "and another is forged");
    }

    @Test
    void theDailySimulationForges() {
        Settlement s = village(5, true);
        s.recordIncident(1);
        SettlementSimulator.withOldAgeDeaths(false).simulateDay(s, 2);
        assertEquals(1, Golems.awaiting(s));
    }
}
