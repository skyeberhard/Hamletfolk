package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R3.10: storage limits and food spoilage. */
class StorageTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();

    private Settlement village(int librarians) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < librarians; i++) {
            // Born in the far future of this run: aging (R4.15) would otherwise kill some off by day 60.
            s.addResident(new Resident(UUID.randomUUID(), "Test", "Person", Gender.FEMALE, new Traits(50, 50, 50, 50),
                    Occupation.LIBRARIAN, true, 10_000, null, null, Needs.initial()));
        }
        return s;
    }

    private static long granariesFull(Settlement s) {
        return s.history().stream().filter(e -> e.text().contains("granaries")).mapToInt(HistoryEvent::count).sum();
    }

    @Test
    void stockBeyondTheStorageLimitIsWasted() {
        Settlement s = village(4);
        s.ledger().add(ResourceType.STONE, 100_000);
        s.ledger().add(ResourceType.FOOD, 100_000);
        simulator.simulateTo(s, 1, 100);
        assertEquals(SettlementSimulator.capacity(s, ResourceType.STONE), s.ledger().get(ResourceType.STONE));
        assertTrue(s.ledger().get(ResourceType.FOOD) <= SettlementSimulator.capacity(s, ResourceType.FOOD));
    }

    @Test
    void foodSpoilsButASmallStockKeeps() {
        Settlement s = village(0);
        registry.enroll(s, UUID.randomUUID(), Occupation.NITWIT, false, 0, null, null);
        s.ledger().add(ResourceType.FOOD, 40);
        s.ledger().add(ResourceType.WOOD, 40);
        simulator.simulateTo(s, 1, 100);
        // a child eats 1; 39 * 2% rounds down to nothing spoiled
        assertEquals(39, s.ledger().get(ResourceType.FOOD));
        assertEquals(40, s.ledger().get(ResourceType.WOOD)); // only food spoils

        Settlement big = village(0);
        registry.enroll(big, UUID.randomUUID(), Occupation.NITWIT, false, 0, null, null);
        big.ledger().add(ResourceType.FOOD, 100);
        simulator.simulateTo(big, 1, 100);
        assertEquals(98, big.ledger().get(ResourceType.FOOD)); // 99 left after the child eats, 1 spoils
    }

    @Test
    void aSurplusCanEndAndTheGranariesFullMilestoneRecurs() {
        Settlement s = village(4);
        s.ledger().add(ResourceType.FOOD, 1000);
        simulator.simulateTo(s, 1, 100);
        assertTrue(s.hasCondition("surplus"));
        assertEquals(1, granariesFull(s));

        simulator.simulateTo(s, 60, 100); // no farmers: eating and spoilage drain it
        assertFalse(s.hasCondition("surplus"));
        assertTrue(s.ledger().get(ResourceType.FOOD) < 4 * 5);

        s.ledger().add(ResourceType.FOOD, 1000);
        simulator.simulateTo(s, 61, 100);
        assertTrue(s.hasCondition("surplus"));
        assertEquals(2, granariesFull(s));
    }
}
