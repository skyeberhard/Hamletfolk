package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R1.23: if the world's day goes backwards, new residents and history use the settlement's own day. */
class ClockBackwardsTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();

    private Settlement villageSimulatedTo(long day) {
        Settlement s = registry.found("world", 0, 0, 0);
        registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
        simulator.simulateTo(s, day, 100);
        return s;
    }

    @Test
    void newcomersAreNotBornBeforeDaysAlreadySimulated() {
        Settlement s = villageSimulatedTo(50);
        // An admin ran /time set and the world is now on day 10.
        // A child, whose birth day is the day they arrive (grown arrivals are given an age, R4.15).
        Resident newcomer = registry.enroll(s, UUID.randomUUID(), Occupation.UNEMPLOYED, false, 10, null, null);
        assertEquals(50, newcomer.bornDay());
    }

    @Test
    void aGrownNewcomerGetsAPlausibleAgeFromTheSettlementsDay() {
        Settlement s = villageSimulatedTo(50);
        Resident newcomer = registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 10, null, null);
        assertTrue(newcomer.age(50) >= Resident.ADULT_AGE_MIN && newcomer.age(50) <= Resident.ADULT_AGE_MAX,
                "age " + newcomer.age(50));
    }

    @Test
    void historyIsNeverRecordedOutOfOrder() {
        Settlement s = villageSimulatedTo(50);
        s.record(10, HistoryEvent.Kind.BIRTH, "A child was born.");
        s.recordDonation(5, "Skye", 3, "wheat");
        long previous = Long.MIN_VALUE;
        for (HistoryEvent event : s.history()) {
            assertTrue(event.day() >= previous, "event on day " + event.day() + " follows day " + previous);
            previous = event.day();
        }
        assertEquals(50, s.history().get(s.history().size() - 1).day());
    }

    @Test
    void anAccurateWorldDayIsLeftAlone() {
        Settlement s = villageSimulatedTo(50);
        Resident newcomer = registry.enroll(s, UUID.randomUUID(), Occupation.UNEMPLOYED, false, 52, null, null);
        assertEquals(52, newcomer.bornDay());
        s.record(52, HistoryEvent.Kind.BIRTH, "A child was born.");
        assertEquals(52, s.history().get(s.history().size() - 1).day());
    }

    @Test
    void loadingASaveDoesNotFlattenOldHistoryToTheLastDay() {
        Settlement s = villageSimulatedTo(50);
        s.record(50, HistoryEvent.Kind.BIRTH, "Later.");
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(SettlementCodec.encode(s), SettlementCodec.encode(loaded));
        assertEquals(0, loaded.history().get(0).day());
    }
}
