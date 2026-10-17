package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R2.9: a house marked by its bed is registered where the finder (R2.8) puts a found house. */
class FoundHouseRegistrationTest {
    @Test
    void aBuildingTheVillageRaisedReplacesAFoundOneAtTheSameBlock() {
        Settlement s = new Settlement(new UUID(1, 1), "Foundham", "world", 0, 0, 0);
        assertEquals(Settlement.Registration.REGISTERED, s.registerBuilding(new Building(BuildingType.HOUSE, 5, 64, 5, 1, BuildingFinder.FOUND_BY)));
        assertEquals(Settlement.Registration.REGISTERED, s.registerBuilding(new Building(BuildingType.HOUSE, 5, 64, 5, 2, "the builders")),
                "not ALREADY_REGISTERED: it would never be upgraded");
        assertEquals(1, s.buildingCount(BuildingType.HOUSE));
        assertEquals("the builders", s.buildings().iterator().next().registeredBy());
        assertEquals(Settlement.Registration.ALREADY_REGISTERED, s.registerBuilding(new Building(BuildingType.HOUSE, 5, 64, 5, 3, "someone")),
                "a second registration of a raised building is still ignored");
        assertEquals(Settlement.Registration.ALREADY_REGISTERED, s.registerBuilding(new Building(BuildingType.HOUSE, 5, 64, 5, 4, BuildingFinder.FOUND_BY)),
                "and a find never replaces a raised one");
    }

    @Test
    void aFoundBuildingOfAnotherKindAtTheSameBlockIsReplacedAsBefore() {
        Settlement s = new Settlement(new UUID(1, 2), "Foundham", "world", 0, 0, 0);
        s.registerBuilding(new Building(BuildingType.HOUSE, 5, 64, 5, 1, BuildingFinder.FOUND_BY));
        assertEquals(Settlement.Registration.REGISTERED, s.registerBuilding(new Building(BuildingType.SHOP, 5, 64, 5, 2, "the builders")));
        assertEquals(0, s.buildingCount(BuildingType.HOUSE));
        assertEquals(1, s.buildingCount(BuildingType.SHOP));
    }
}
