package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.9: a resident's food, safety and purpose affect how much they produce. */
class NeedsOutputTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();

    @Test
    void factorFollowsTheWorstNeed() {
        assertEquals(1.0, SettlementSimulator.needsFactor(new Needs(100, 100, 100)), 1e-9);
        assertEquals(1.0, SettlementSimulator.needsFactor(new Needs(50, 80, 90)), 1e-9);
        assertEquals(0.75, SettlementSimulator.needsFactor(new Needs(100, 25, 100)), 1e-9);
        assertEquals(SettlementSimulator.MIN_NEEDS_OUTPUT, SettlementSimulator.needsFactor(new Needs(0, 100, 100)), 1e-9);
    }

    /** Total food twenty farmers make over ten days, with every need held at {@code level}. */
    private int farmedWithNeedsAt(int level) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 20; i++) {
            registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
        }
        int total = 0;
        for (int day = 1; day <= 10; day++) {
            for (Resident r : s.residents()) {
                r.needs().adjustFood(level - r.needs().food());
                r.needs().setSafety(level);
                r.needs().adjustPurpose(level - r.needs().purpose());
            }
            simulator.simulateTo(s, day, 100);
            total += s.flow().produced(ResourceType.FOOD, day);
        }
        return total;
    }

    @Test
    void aStarvingOrTerrifiedWorkforceProducesNoticeablyLess() {
        int content = farmedWithNeedsAt(100);
        int miserable = farmedWithNeedsAt(0);
        // 200 worker-days, about 4 a day when content and about 2 when miserable: far apart.
        assertTrue(miserable < content * 0.7, content + " content vs " + miserable + " miserable");
        assertTrue(miserable > 0, "output never falls to nothing");
    }

    /** Ten farmers plus twelve children: demand 32 a day against roughly 40 made when content. */
    private Settlement barelySelfSufficient() {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 10; i++) {
            // Born on day 40, so they stay in their prime for the whole run (R4.15 would age plain enrollees).
            s.addResident(new Resident(UUID.randomUUID(), "Farmer" + i, "Test", Gender.MALE,
                    new Traits(50, 50, 50, 50), Occupation.FARMER, true, 40, null, null, Needs.initial()));
        }
        for (int i = 0; i < 12; i++) {
            registry.enroll(s, UUID.randomUUID(), Occupation.NITWIT, false, 0, null, null);
        }
        return s;
    }

    @Test
    void aVillageThatCanFeedItselfClimbsBackFromStarvation() {
        // Regression: while food need only recovered when everyone was fully fed, a village like
        // this one, started hungry, stayed stuck at half output and never got there.
        Settlement s = barelySelfSufficient();
        for (Resident r : s.residents()) {
            r.needs().adjustFood(20 - r.needs().food());
        }
        simulator.simulateTo(s, 80, 100);
        int food = s.residents().iterator().next().needs().food();
        assertTrue(food >= 50, "food need should have recovered, was " + food);
    }
}
