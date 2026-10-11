package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.34: travellers come to a village that has lost its people. */
class ResettlementTest {
    private int next = 1;

    private Settlement village(int residents) {
        Settlement s = new Settlement(new UUID(21, 21), "Hollowgate", "world", 0, 0, 0);
        for (int i = 0; i < residents; i++) {
            s.addResident(new Resident(new UUID(22, next++), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, 50),
                    Occupation.UNEMPLOYED, true, 10_000, null, null, Needs.initial()));
        }
        return s;
    }

    @Test
    void onlyAVillageThatHasLostItsPeopleCanBeResettled() {
        assertTrue(Resettlement.refusal(village(0)).isEmpty());
        assertTrue(Resettlement.refusal(village(1)).isEmpty());
        assertTrue(Resettlement.refusal(village(Resettlement.MAX_EXISTING)).isEmpty());
        String refused = Resettlement.refusal(village(Resettlement.MAX_EXISTING + 1)).orElseThrow();
        assertTrue(refused.contains("still has 4 people"), refused);
    }

    @Test
    void anEmptyOrAbandonedVillageIsLostButOneWithPeopleIsNot() {
        assertTrue(Resettlement.isLost(village(0)));
        Settlement abandoned = village(2);
        abandoned.conditions().put("abandoned", 5L);
        assertTrue(Resettlement.isLost(abandoned), "marked abandoned");
        assertFalse(Resettlement.isLost(village(1)));
    }

    @Test
    void theNumberSentIsKeptWithinReason() {
        assertEquals(1, Resettlement.settlers(0));
        assertEquals(1, Resettlement.settlers(-4));
        assertEquals(4, Resettlement.settlers(4));
        assertEquals(Resettlement.MAX_SETTLERS, Resettlement.settlers(500));
    }

    @Test
    void settlersBringAKitAndALostVillageStartsClearOfItsOldDanger() {
        Settlement s = village(1);
        s.conditions().put("abandoned", 5L);
        s.setThreat(38.7);
        long food = s.ledger().get(ResourceType.FOOD);
        Resettlement.arrive(s, 100, 3, true);
        assertEquals(food + 3 * Resettlement.FOOD_PER_SETTLER, s.ledger().get(ResourceType.FOOD));
        assertEquals(3 * Resettlement.WOOD_PER_SETTLER, s.ledger().get(ResourceType.WOOD));
        assertEquals(3 * Resettlement.STONE_PER_SETTLER, s.ledger().get(ResourceType.STONE));
        assertEquals(0.0, s.threat(), "the danger a lost village was left with is gone");
        assertFalse(s.isAbandoned(), "and it is not treated as abandoned a moment longer");
        assertFalse(s.conditions().containsKey("emptySince"));
        assertTrue(s.history().stream().anyMatch(e -> e.text().equals("3 travellers came to settle in Hollowgate, which had been left empty, bringing food, wood and stone.")),
                s.history().toString());
    }

    @Test
    void aVillageThatStillHasPeopleKeepsItsOwnDanger() {
        Settlement s = village(2);
        s.setThreat(20);
        Resettlement.arrive(s, 100, 1, false);
        assertEquals(20.0, s.threat());
        assertTrue(s.history().stream().anyMatch(e -> e.text().equals("A traveller came to settle in Hollowgate, bringing food, wood and stone.")));
    }

    @Test
    void theFoodIsEnoughForTheFirstDaysAndForNewcomersToBeInvited() {
        // three settlers eat 6 a day: fifteen days of food each is 45 days for the three, and the newcomer rule's 20 a head is met at once
        Settlement s = village(3);
        Resettlement.arrive(s, 1, 3, false);
        assertTrue(s.ledger().get(ResourceType.FOOD) >= s.population() * SettlementSimulator.NEWCOMER_FOOD_PER_HEAD,
                "20 food a head, so the village can attract more people after the settlers");
    }
}
