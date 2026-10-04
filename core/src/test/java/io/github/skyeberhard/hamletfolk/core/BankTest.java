package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** R2.7: with a treasury building, donations and request payouts happen at its counter. */
class BankTest {
    private final SettlementRegistry registry = new SettlementRegistry();

    private Settlement village() {
        return registry.found("world", 0, 0, 0);
    }

    private static void treasury(Settlement s, int x, int y, int z) {
        s.registerBuilding(new Building(BuildingType.TREASURY, x, y, z, 0, "test"));
    }

    @Test
    void aSettlementWithNoTreasuryBuildingKeepsTheOldRule() {
        Settlement s = village();
        assertTrue(Bank.check(s, 500, 70, -300).allowed(), "anywhere in the settlement");
        // Other buildings do not make a bank.
        s.registerBuilding(new Building(BuildingType.SHOP, 0, 64, 0, 0, "test"));
        s.registerBuilding(new Building(BuildingType.FARM, 5, 64, 5, 0, "test"));
        assertTrue(Bank.check(s, 500, 70, -300).allowed());
    }

    @Test
    void withATreasuryBuildingOnlyItsCounterWorks() {
        Settlement s = village();
        treasury(s, 100, 64, 100);
        assertTrue(Bank.check(s, 100, 64, 100).allowed(), "at the sign");
        assertTrue(Bank.check(s, 100 + Bank.RANGE, 64, 100).allowed(), "at the edge of the range");
        assertFalse(Bank.check(s, 100 + Bank.RANGE + 1, 64, 100).allowed(), "just beyond it");
        assertTrue(Bank.check(s, 100, 64 + Bank.HEIGHT, 100).allowed());
        assertFalse(Bank.check(s, 100, 64 + Bank.HEIGHT + 1, 100).allowed(), "another floor");
        assertFalse(Bank.check(s, 100, 64 - Bank.HEIGHT - 1, 100).allowed(), "a cellar");
    }

    @Test
    void awayFromTheCounterItSaysWhereTheNearestOneIs() {
        Settlement s = village();
        treasury(s, 100, 64, 0);
        treasury(s, 0, 70, 30);
        Bank.Access away = Bank.check(s, 0, 70, 0);
        assertFalse(away.allowed());
        Building nearest = away.nearest().orElseThrow();
        assertEquals(0, nearest.x());
        assertEquals(30, nearest.z());
        assertEquals(30, away.distance());
        // Standing at either counter is fine.
        assertTrue(Bank.check(s, 100, 64, 0).allowed());
        assertTrue(Bank.check(s, 0, 70, 30).allowed());
    }

    @Test
    void beingOnTheWrongFloorIsNotZeroBlocksAway() {
        Settlement s = village();
        treasury(s, 0, 64, 0);
        Bank.Access above = Bank.check(s, 0, 64 + Bank.HEIGHT + 5, 0); // right above the sign, too high
        assertFalse(above.allowed());
        assertEquals(Bank.HEIGHT + 5, above.distance());
        // Just outside the range level: rounded up, so it never reads as inside it.
        Bank.Access edge = Bank.check(s, Bank.RANGE, 64, 2);
        assertFalse(edge.allowed());
        assertTrue(edge.distance() > Bank.RANGE);
    }

    @Test
    void breakingTheOnlyTreasurySignGoesBackToTheOldRule() {
        Settlement s = village();
        treasury(s, 100, 64, 100);
        assertFalse(Bank.check(s, 0, 64, 0).allowed());
        s.removeBuilding(100, 64, 100, 1);
        assertTrue(Bank.check(s, 0, 64, 0).allowed());
    }

    @Test
    void anAllowedCheckHasNoDirections() {
        Settlement s = village();
        treasury(s, 0, 64, 0);
        Bank.Access at = Bank.check(s, 1, 64, 1);
        assertTrue(at.allowed());
        assertTrue(at.nearest().isEmpty());
        assertEquals(0, at.distance());
    }
}
