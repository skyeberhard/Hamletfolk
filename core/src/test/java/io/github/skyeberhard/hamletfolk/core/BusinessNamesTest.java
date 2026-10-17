package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R2.10: a name for each business, the same every time, and none for a house. */
class BusinessNamesTest {
    private final Settlement village = new Settlement(new UUID(190, 1), "Nameford", "world", 0, 0, 0);

    private static Building at(BuildingType type, int x, int z) {
        return new Building(type, x, 64, z, 1, "test");
    }

    @Test
    void aBuildingAlwaysHasTheSameName() {
        Building shop = at(BuildingType.SHOP, 10, 20);
        assertEquals(BusinessNames.of(village, shop), BusinessNames.of(village, shop));
        assertTrue(BusinessNames.of(village, shop).isPresent());
        Settlement again = new Settlement(new UUID(190, 1), "Nameford", "world", 0, 0, 0);
        assertEquals(BusinessNames.of(village, shop), BusinessNames.of(again, shop), "from the village and the place, nothing saved");
    }

    @Test
    void aHouseTheSquareAndTheWorksHaveNoName() {
        for (BuildingType none : new BuildingType[] {BuildingType.HOUSE, BuildingType.SQUARE, BuildingType.STREET_LIGHTS, BuildingType.PALISADE,
                BuildingType.RAMPART, BuildingType.GATEHOUSE, BuildingType.TOWER}) {
            assertEquals(Optional.empty(), BusinessNames.of(village, at(none, 1, 1)), none.name());
        }
    }

    @Test
    void everyOtherKindOfBuildingHasANameThatFitsASign() {
        for (BuildingType type : BuildingType.values()) {
            if (type.isWorks() || type.isPart() || type == BuildingType.HOUSE || type == BuildingType.SQUARE) {
                continue;
            }
            for (int i = 0; i < 40; i++) {
                String name = BusinessNames.of(village, at(type, i * 7, i * 13)).orElseThrow(() -> new AssertionError(type + " has no name"));
                assertFalse(name.contains("{"), "a slot left unfilled: " + name);
                assertTrue(name.length() <= 26, name);
                assertTrue(BusinessNames.lines(name, 14, 2).size() <= 2);
            }
        }
    }

    @Test
    void aKindOfBusinessHasAVarietyOfNamesAcrossAVillage() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            BusinessNames.of(village, at(BuildingType.SHOP, i * 11, i * 5)).ifPresent(names::add);
        }
        assertTrue(names.size() >= 12, "" + names.size());
        assertTrue(names.stream().anyMatch(n -> n.contains("'s ") || n.contains("&") || n.startsWith("The ")), names.toString());
    }

    @Test
    void aVillagesNameFillsItsSlot() {
        boolean found = false;
        for (int i = 0; i < 200 && !found; i++) {
            found = BusinessNames.of(village, at(BuildingType.TREASURY, i, i * 3)).orElse("").contains("Nameford");
        }
        assertTrue(found, "a treasury is sometimes the village's own");
    }

    @Test
    void aNameIsSplitAtSpacesOverAtMostTheLinesGiven() {
        assertEquals(List.of("Mercer's", "Wares"), BusinessNames.lines("Mercer's Wares", 8, 2));
        assertEquals(List.of("Hammer & Tongs"), BusinessNames.lines("Hammer & Tongs", 14, 2));
        assertEquals(List.of("Hammer &", "Tongs"), BusinessNames.lines("Hammer & Tongs", 10, 2));
        assertEquals(2, BusinessNames.lines("One Two Three Four Five Six", 7, 2).size(), "what does not fit is left off");
        assertEquals(List.of("Supercalifrag"), BusinessNames.lines("Supercalifragilistic", 13, 2), "a long word is cut to fit");
        assertTrue(BusinessNames.lines("", 10, 2).isEmpty());
    }
}
