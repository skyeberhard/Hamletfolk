package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.1: a food surplus and a free bed bring a newcomer. */
class NewcomerTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();

    private Settlement village(int food) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            registry.enroll(s, UUID.randomUUID(), Occupation.LIBRARIAN, true, 0, null, null);
        }
        s.ledger().add(ResourceType.FOOD, food);
        return s;
    }

    @Test
    void aSurplusAndAFreeBedBringANewcomer() {
        Settlement s = village(200);
        simulator.simulateTo(s, 1, 100); // not on the founding day, or the arrival would go unrecorded
        assertTrue(simulator.newcomerDue(s, 1));
    }

    @Test
    void noNewcomerWithoutSurplusBedOrPeople() {
        Settlement lean = village(79); // just under 20 per resident
        simulator.simulateTo(lean, 1, 100);
        assertFalse(simulator.newcomerDue(lean, 5));
        Settlement bedless = village(200);
        simulator.simulateTo(bedless, 1, 100);
        assertFalse(simulator.newcomerDue(bedless, 0));
        assertFalse(simulator.newcomerDue(registry.found("world", 500, 500, 0), 5));
    }

    @Test
    void noNewcomerOnTheFoundingDay() {
        assertFalse(simulator.newcomerDue(village(200), 5));
    }

    @Test
    void arrivalsAreSpacedOutAndTheSurplusMustLast() {
        Settlement s = village(10_000); // storage caps this at 260 on the first day, still well over 80
        simulator.simulateTo(s, 10, 100);
        assertTrue(simulator.newcomerDue(s, 3));
        simulator.newcomerArrived(s);
        assertFalse(simulator.newcomerDue(s, 3));
        simulator.simulateTo(s, 10 + SettlementSimulator.NEWCOMER_COOLDOWN_DAYS - 1, 100);
        assertFalse(simulator.newcomerDue(s, 3));
        simulator.simulateTo(s, 10 + SettlementSimulator.NEWCOMER_COOLDOWN_DAYS, 100);
        assertTrue(simulator.newcomerDue(s, 3));
        s.ledger().take(ResourceType.FOOD, 10_000);
        assertFalse(simulator.newcomerDue(s, 3));
    }

    @Test
    void anAbandonedSettlementGetsNoNewcomers() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.ledger().add(ResourceType.FOOD, 1000);
        simulator.simulateTo(s, SettlementSimulator.ABANDONMENT_DAYS + 1, 100);
        assertTrue(s.isAbandoned());
        assertEquals(false, simulator.newcomerDue(s, 5));
    }
}
