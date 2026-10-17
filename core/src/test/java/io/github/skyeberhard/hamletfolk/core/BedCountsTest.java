package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R8.30: claimed beds and which house they belong to. */
class BedCountsTest {
    private Settlement village() {
        Settlement s = new Settlement(new UUID(70, 1), "Bedford", "world", 0, 64, 0);
        s.registerBuilding(new Building(BuildingType.HOUSE, 0, 64, 0, 1, "village"));
        s.registerBuilding(new Building(BuildingType.HOUSE, 30, 64, 0, 1, "village"));
        return s;
    }

    @Test
    void bedsAreCountedForTheHouseTheyAreNear() {
        Settlement s = village();
        List<BedCounts.Bed> beds = List.of(new BedCounts.Bed(2, 64, 3, true), new BedCounts.Bed(-3, 65, 1, false), new BedCounts.Bed(31, 64, 2, true));
        Map<String, int[]> counts = BedCounts.perHouse(s, beds);
        assertArrayEquals(new int[] {2, 1}, counts.get("0,64,0"));
        assertArrayEquals(new int[] {1, 1}, counts.get("30,64,0"));
    }

    @Test
    void aBedFarFromEveryHouseIsNotCountedForOne() {
        Map<String, int[]> counts = BedCounts.perHouse(village(), List.of(new BedCounts.Bed(15, 64, 15, true), new BedCounts.Bed(1, 90, 1, true)));
        assertArrayEquals(new int[] {0, 0}, counts.get("0,64,0"));
        assertArrayEquals(new int[] {0, 0}, counts.get("30,64,0"));
    }

    @Test
    void aBedBetweenTwoHousesGoesToTheNearer() {
        Settlement s = village();
        s.registerBuilding(new Building(BuildingType.HOUSE, 6, 64, 0, 1, "village"));
        Map<String, int[]> counts = BedCounts.perHouse(s, List.of(new BedCounts.Bed(5, 64, 0, false)));
        assertArrayEquals(new int[] {1, 0}, counts.get("6,64,0"));
        assertArrayEquals(new int[] {0, 0}, counts.get("0,64,0"));
    }

    @Test
    void theDataHasTotalsAndPlaces() {
        Map<String, Object> data = BedCounts.asData(List.of(new BedCounts.Bed(1, 64, 2, true), new BedCounts.Bed(3, 64, 4, false)));
        assertEquals(2, data.get("total"));
        assertEquals(1, data.get("claimed"));
        assertEquals(1, data.get("free"));
        assertEquals(2, ((List<?>) data.get("places")).size());
    }
}
