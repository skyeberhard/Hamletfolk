package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.10: grown children take a needed trade, preferring a parent's. */
class ApprenticeTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator anyJob = new SettlementSimulator(false, o -> true);

    private static boolean apprenticed(Settlement s) {
        return s.history().stream().anyMatch(e -> e.text().contains("apprenticed"));
    }

    @Test
    void childFollowsAParentsTradeWhenItIsNeeded() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident parent = registry.enroll(s, UUID.randomUUID(), Occupation.MASON, true, 0, null, null);
        Resident child = registry.enroll(s, UUID.randomUUID(), Occupation.UNEMPLOYED, false, 0, parent.id(), null);
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.WOOD, 0); // wood is shortest, but stone is short too
        anyJob.simulateTo(s, 1, 100);
        assertEquals(Occupation.UNEMPLOYED, child.occupation()); // still a child
        child.setAdult(true);
        anyJob.simulateTo(s, 2, 100);
        assertEquals(Occupation.MASON, child.occupation());
        assertTrue(apprenticed(s));
    }

    @Test
    void childTakesTheShortestTradeWhenTheParentsIsNotNeeded() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident parent = registry.enroll(s, UUID.randomUUID(), Occupation.MASON, true, 0, null, null);
        Resident child = registry.enroll(s, UUID.randomUUID(), Occupation.UNEMPLOYED, true, 0, parent.id(), null);
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.STONE, 1000); // stone is plentiful, wood is not
        anyJob.simulateTo(s, 1, 100);
        assertEquals(Occupation.LUMBERJACK, child.occupation());
        assertTrue(apprenticed(s));
    }

    @Test
    void adultsWithNoParentsOnRecordAreNotRecordedAsApprentices() {
        Settlement s = registry.found("world", 0, 0, 0);
        registry.enroll(s, UUID.randomUUID(), Occupation.UNEMPLOYED, true, 0, null, null);
        s.ledger().add(ResourceType.FOOD, 1000);
        anyJob.simulateTo(s, 1, 100);
        assertEquals(false, apprenticed(s));
    }
}
