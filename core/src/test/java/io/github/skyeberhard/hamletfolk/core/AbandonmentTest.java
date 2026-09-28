package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R1.5: a settlement with no residents for 10 days is marked abandoned, stops simulating, keeps its history. */
class AbandonmentTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();

    @Test
    void emptySettlementIsNotAbandonedBeforeTenDays() {
        Settlement s = registry.found("world", 0, 0, 0);
        simulator.simulateTo(s, 9, 100);
        assertFalse(s.isAbandoned());
        assertTrue(s.history().stream().noneMatch(e -> e.kind() == HistoryEvent.Kind.ABANDONED));
    }

    @Test
    void emptySettlementIsAbandonedAtTenDays() {
        Settlement s = registry.found("world", 0, 0, 0);
        simulator.simulateTo(s, 10, 100);
        assertTrue(s.isAbandoned());
        assertEquals(1, s.history().stream().filter(e -> e.kind() == HistoryEvent.Kind.ABANDONED).count());
    }

    @Test
    void abandonmentIsRecordedOnceNotEveryDay() {
        Settlement s = registry.found("world", 0, 0, 0);
        simulator.simulateTo(s, 30, 100);
        assertEquals(1, s.history().stream().filter(e -> e.kind() == HistoryEvent.Kind.ABANDONED).count());
    }

    @Test
    void abandonedSettlementDoesNothingUntilResettled() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.ledger().add(ResourceType.FOOD, 5);
        s.raiseThreat(40);
        simulator.simulateTo(s, 50, 100);

        // Nothing touches ledger/threat once abandoned: no residents to feed, no threat decay applied.
        assertEquals(5, s.ledger().get(ResourceType.FOOD));
        assertTrue(s.threat() > 0);

        registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 50, null, null);
        simulator.simulateTo(s, 51, 100);

        assertFalse(s.isAbandoned());
        assertTrue(s.history().stream().anyMatch(e -> e.kind() == HistoryEvent.Kind.MILESTONE
                && e.text().contains("resettled")));
    }

    @Test
    void villageThatNeverEmptiesIsNeverAbandoned() {
        Settlement s = registry.found("world", 0, 0, 0);
        registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
        simulator.simulateTo(s, 30, 100);
        assertFalse(s.isAbandoned());
    }

    @Test
    void residentsLeavingAndReturningBeforeTenDaysResetsTheClock() {
        Settlement s = registry.found("world", 0, 0, 0);
        UUID id = UUID.randomUUID();
        registry.enroll(s, id, Occupation.FARMER, true, 0, null, null);
        simulator.simulateTo(s, 5, 100);

        registry.remove(id);
        simulator.simulateTo(s, 12, 100); // 7 empty days, under the 10-day threshold
        registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 12, null, null);
        simulator.simulateTo(s, 25, 100); // clock should have reset, not carried over

        assertFalse(s.isAbandoned());
        assertTrue(s.history().stream().noneMatch(e -> e.kind() == HistoryEvent.Kind.ABANDONED));
    }
}
