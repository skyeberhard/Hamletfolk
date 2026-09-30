package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.3: unemployed residents take the occupation the village is shortest of. */
class JobAssignmentTest {
    private final SettlementRegistry registry = new SettlementRegistry();

    private Resident enroll(Settlement s, Occupation job, boolean adult) {
        return registry.enroll(s, UUID.randomUUID(), job, adult, 0, null, null);
    }

    @Test
    void unemployedTakesSimOwnedJobWhenItsResourceIsShort() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident jobless = enroll(s, Occupation.UNEMPLOYED, true);
        s.ledger().add(ResourceType.FOOD, 1000);
        new SettlementSimulator().simulateTo(s, 1, 100);
        assertEquals(Occupation.LUMBERJACK, jobless.occupation());
    }

    @Test
    void takesTheShortResourceThenStopsOnceNothingIsShort() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident jobless = enroll(s, Occupation.UNEMPLOYED, true);
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.WOOD, 1000);
        new SettlementSimulator(false, o -> true).simulateTo(s, 3, 100);
        assertEquals(Occupation.MASON, jobless.occupation()); // stone is the only thing short
        Resident another = enroll(s, Occupation.UNEMPLOYED, true);
        s.ledger().add(ResourceType.STONE, 1000);
        new SettlementSimulator(false, o -> true).simulateTo(s, 6, 100);
        assertEquals(Occupation.UNEMPLOYED, another.occupation());
    }

    @Test
    void noFreeWorkstationForTheShortestJobMeansNoJob() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident jobless = enroll(s, Occupation.UNEMPLOYED, true);
        s.ledger().add(ResourceType.WOOD, 1); // short too, but food (0) is shorter and has no farm to staff
        new SettlementSimulator().simulateTo(s, 3, 100);
        assertEquals(Occupation.UNEMPLOYED, jobless.occupation());
    }

    @Test
    void shortestResourceWinsAndOnlyOnePersonMovesPerDay() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident a = enroll(s, Occupation.UNEMPLOYED, true);
        Resident b = enroll(s, Occupation.UNEMPLOYED, true);
        s.ledger().add(ResourceType.WOOD, 1000);
        s.ledger().add(ResourceType.STONE, 1000);
        s.ledger().add(ResourceType.FOOD, 5);
        new SettlementSimulator(false, o -> true).simulateTo(s, 1, 100);
        long farmers = s.residents().stream().filter(r -> r.occupation() == Occupation.FARMER).count();
        assertEquals(1, farmers);
    }

    @Test
    void childrenAndEmployedResidentsAreLeftAlone() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident child = enroll(s, Occupation.UNEMPLOYED, false);
        Resident librarian = enroll(s, Occupation.LIBRARIAN, true);
        s.ledger().add(ResourceType.FOOD, 1000);
        new SettlementSimulator().simulateTo(s, 5, 100);
        assertEquals(Occupation.UNEMPLOYED, child.occupation());
        assertEquals(Occupation.LIBRARIAN, librarian.occupation());
    }

    @Test
    void vanillaProfessionSeedsOnlyAnUnemployedResident() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident newborn = enroll(s, Occupation.UNEMPLOYED, false);
        Resident lumberjack = enroll(s, Occupation.LUMBERJACK, true);
        newborn.seedOccupation(Occupation.FARMER);
        lumberjack.seedOccupation(Occupation.UNEMPLOYED);
        assertEquals(Occupation.FARMER, newborn.occupation());
        assertEquals(Occupation.LUMBERJACK, lumberjack.occupation());
        newborn.seedOccupation(Occupation.LIBRARIAN);
        assertEquals(Occupation.FARMER, newborn.occupation());
    }
}
