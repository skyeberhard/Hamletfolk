package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.59: a better merchant gets more emeralds for the same goods. */
class HagglingTest {
    private static int n;

    private int emeraldsFor(int merchantDays) {
        Settlement s = new Settlement(new UUID(95, n++), "Hagglebury", "world", 0, 0, 0);
        for (int i = 0; i < 3; i++) {
            s.addResident(new Resident(new UUID(96, n++), "T", "F" + n, Gender.MALE, new Traits(50, 50, 50, 50), Occupation.FARMER, true, 10_000, null, null, Needs.initial()));
        }
        Resident m = new Resident(new UUID(97, n++), "T", "M" + n, Gender.MALE, new Traits(50, 50, 50, 50), Occupation.MERCHANT, true, 10_000, null, null, Needs.initial());
        m.restoreXp(Occupation.MERCHANT, merchantDays);
        s.addResident(m);
        s.registerBuilding(new Building(BuildingType.SHOP, 5, 64, 5, 1, "village"));
        s.housing().setChunk(0, 0, 10);
        s.setLastSimulatedDay(0);
        s.conditions().put(SettlementSimulator.TREASURY_LEGACY, 100_000L); // R2.6: room to bank
        s.ledger().add(ResourceType.WOOD, 2000);
        SettlementSimulator.withOldAgeDeaths(false).simulateTo(s, 3, 100);
        return s.ledger().treasury();
    }

    @Test
    void theHaggledPriceFallsWithLevel() {
        assertEquals(10, TradeLevel.haggled(10, 1));
        assertEquals(8, TradeLevel.haggled(10, TradeLevel.MASTER)); // 16% fewer units, floored
        assertTrue(TradeLevel.haggled(1, TradeLevel.MASTER) >= 1);
    }

    @Test
    void aMasterMerchantEarnsAtLeastAsMuchAsANovice() {
        int novice = emeraldsFor(0);
        int master = emeraldsFor(100);
        assertTrue(master > novice, master + " vs " + novice);
        assertTrue(master > 0);
    }
}
