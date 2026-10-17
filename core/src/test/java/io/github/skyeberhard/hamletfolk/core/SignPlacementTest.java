package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R2.9: where a building the village raises carries its name. */
class SignPlacementTest {
    /** A 7 by 5 by 7 hut: a door in the middle of its north wall, a lintel over it, and a bed inside. */
    private static Blueprint hut() {
        List<Blueprint.Block> blocks = new ArrayList<>();
        for (int x = 0; x < 7; x++) {
            for (int y = 0; y < 3; y++) {
                blocks.add(new Blueprint.Block(x, y, 0, "minecraft:oak_planks"));
            }
        }
        blocks.removeIf(b -> b.x() == 3 && b.y() < 2 && b.z() == 0);
        blocks.add(new Blueprint.Block(3, 0, 0, "minecraft:oak_door[facing=north,half=lower]"));
        blocks.add(new Blueprint.Block(3, 1, 0, "minecraft:oak_door[facing=north,half=upper]"));
        blocks.add(new Blueprint.Block(3, 0, 4, "minecraft:red_bed[facing=south,part=foot]"));
        blocks.add(new Blueprint.Block(3, 0, 5, "minecraft:red_bed[facing=south,part=head]"));
        return new Blueprint("hut", 7, 5, 7, blocks);
    }

    @Test
    void aHouseHasNoSignAndABusinessAHangingOneAndAFieldAPost() {
        assertEquals(SignPlacement.Form.NONE, SignPlacement.formOf(BuildingType.HOUSE));
        for (BuildingType business : List.of(BuildingType.SHOP, BuildingType.SMITHY, BuildingType.MINE, BuildingType.TREASURY, BuildingType.LIBRARY,
                BuildingType.SAWMILL, BuildingType.GUARD_POST, BuildingType.TRADING_POST)) {
            assertEquals(SignPlacement.Form.HANGING, SignPlacement.formOf(business), business.name());
        }
        for (BuildingType field : List.of(BuildingType.FARM, BuildingType.PENS, BuildingType.APIARY, BuildingType.HARBOUR)) {
            assertEquals(SignPlacement.Form.POST, SignPlacement.formOf(field), field.name());
        }
        for (BuildingType type : BuildingType.values()) {
            assertTrue(SignPlacement.formOf(type) != null, type.name());
        }
    }

    @Test
    void aHangingSignGoesOverTheDoorFirstThenBesideIt() {
        List<SignPlacement.Hanging> spots = SignPlacement.hangingSpots(hut());
        assertEquals(3, spots.size());
        SignPlacement.Hanging over = spots.get(0);
        assertEquals(3, over.supportX());
        assertEquals(2, over.supportY(), "hung from the wall above the door");
        assertEquals(0, over.supportZ());
        assertEquals(3, over.x());
        assertEquals(2, over.y());
        assertEquals(-1, over.z(), "in the cell outside it: the door is in the north wall, so north is out");
        assertEquals(1, over.facing(), "a board hangs across a north wall, so its faces point east and west");
        for (SignPlacement.Hanging beside : spots.subList(1, 3)) {
            assertEquals(1, beside.y(), "beside the door, at the height of its upper half");
            assertEquals(beside.supportZ() - 1, beside.z());
            assertTrue(Math.abs(beside.supportX() - 3) == 1, "one block to a side of the door");
        }
    }

    @Test
    void aTurnedBuildingTurnsItsSignSpotsWithIt() {
        Blueprint east = hut().rotated(1); // the door, once in the north wall, is in the east wall
        assertEquals(java.util.OptionalInt.of(1), east.front());
        SignPlacement.Hanging over = SignPlacement.hangingSpots(east).get(0);
        assertEquals(over.supportX() + 1, over.x(), "out to the east");
        assertEquals(over.supportZ(), over.z());
        assertEquals(0, over.facing(), "a board hangs across an east wall, so its faces point north and south");
        assertEquals(2, over.supportY());
    }

    @Test
    void aBuildingWithNoDoorHasNowhereToHangASign() {
        Blueprint field = new Blueprint("field", 5, 1, 5, List.of(new Blueprint.Block(0, 0, 0, "minecraft:farmland")));
        assertTrue(SignPlacement.hangingSpots(field).isEmpty());
        assertTrue(SignPlacement.marker(field).isEmpty());
    }

    @Test
    void aHouseIsMarkedByTheHeadOfItsBed() {
        Blueprint.Block marker = SignPlacement.marker(hut()).orElseThrow();
        assertEquals(3, marker.x());
        assertEquals(5, marker.z(), "the head, not the foot");
        Blueprint noPart = new Blueprint("h", 3, 2, 3, List.of(new Blueprint.Block(1, 0, 1, "minecraft:red_bed")));
        assertTrue(SignPlacement.marker(noPart).isPresent(), "a bed that does not say which part it is still marks the house");
    }

    @Test
    void everyFrontGetsItsSignOutsideTheWallAcrossIt() {
        // the hut turned a quarter at a time: north, east, south, west
        int[][] outward = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int[] facing = {1, 0, 1, 0};
        for (int turns = 0; turns < 4; turns++) {
            Blueprint bp = hut().rotated(turns);
            assertEquals(java.util.OptionalInt.of(turns), bp.front(), "front after " + turns + " turns");
            SignPlacement.Hanging over = SignPlacement.hangingSpots(bp).get(0);
            assertEquals(outward[turns][0], over.x() - over.supportX(), "out in x after " + turns);
            assertEquals(outward[turns][1], over.z() - over.supportZ(), "out in z after " + turns);
            assertEquals(facing[turns], over.facing(), "faces after " + turns);
            assertTrue(over.x() < 0 || over.x() >= bp.width() || over.z() < 0 || over.z() >= bp.depth(), "outside the footprint");
        }
    }

    @Test
    void aDoorOnAPlatformHasItsSignTwoAboveItsBase() {
        List<Blueprint.Block> raised = new ArrayList<>();
        for (Blueprint.Block b : hut().blocks()) {
            raised.add(new Blueprint.Block(b.x(), b.y() + 2, b.z(), b.material()));
        }
        SignPlacement.Hanging over = SignPlacement.hangingSpots(new Blueprint("raised", 7, 7, 7, raised)).get(0);
        assertEquals(4, over.y());
        assertEquals(4, over.supportY());
    }

    @Test
    void aDoorInsideTheBuildingIsNotHungWithASignInARoom() {
        List<Blueprint.Block> blocks = new ArrayList<>(hut().blocks());
        blocks.removeIf(b -> b.material().contains("_door"));
        blocks.add(new Blueprint.Block(3, 0, 3, "minecraft:oak_door[facing=north,half=lower]")); // an inner door, lowest
        blocks.add(new Blueprint.Block(3, 1, 3, "minecraft:oak_door[facing=north,half=upper]"));
        blocks.add(new Blueprint.Block(3, 1, 0, "minecraft:oak_door[facing=north,half=lower]")); // the front one, a step up
        Blueprint bp = new Blueprint("two", 7, 5, 7, blocks);
        assertTrue(SignPlacement.hangingSpots(bp).isEmpty(), "no spot in a room: the village falls back on a sign on the ground");
    }
}
