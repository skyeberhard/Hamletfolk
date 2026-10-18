package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** R8.31: the footprint of a building found standing. */
class FootprintsTest {
    /** A world where the given rectangles are built, and ground outside the loaded area is not loaded. */
    private static Footprints.Columns world(Rect loaded, Rect... built) {
        Set<Long> cells = new HashSet<>();
        for (Rect r : built) {
            for (int x = r.x(); x < r.x() + r.width(); x++) {
                for (int z = r.z(); z < r.z() + r.depth(); z++) {
                    cells.add(((long) x << 32) ^ (z & 0xffffffffL));
                }
            }
        }
        return new Footprints.Columns() {
            @Override
            public boolean loaded(int x, int z) {
                return loaded.contains(x, z);
            }

            @Override
            public boolean built(int x, int z) {
                return cells.contains(((long) x << 32) ^ (z & 0xffffffffL));
            }
        };
    }

    private static final Rect ALL = new Rect(-100, -100, 200, 200);

    @Test
    void aHouseIsFoundFromAMarkerInsideIt() {
        Rect house = new Rect(10, 20, 7, 5);
        Rect found = Footprints.of(world(ALL, house), 13, 22);
        assertEquals(house, found);
    }

    @Test
    void aMarkerBesideTheWallStillFindsTheBuilding() {
        Rect house = new Rect(10, 20, 7, 5);
        assertEquals(house, Footprints.of(world(ALL, house), 9, 22), "a bed or lectern in a doorway, one block outside");
        assertNull(Footprints.of(world(ALL, house), 3, 22), "too far from anything built");
    }

    @Test
    void ConnectedPartsAreOneBuildingButASeparateOneIsNot() {
        Rect main = new Rect(0, 0, 6, 6);
        Rect wing = new Rect(6, 1, 3, 3); // touches
        Rect other = new Rect(20, 0, 5, 5); // a street away
        Rect found = Footprints.of(world(ALL, main, wing, other), 2, 2);
        assertEquals(new Rect(0, 0, 9, 6), found);
    }

    @Test
    void aLoneLampPostIsNotABuilding() {
        assertNull(Footprints.of(world(ALL, new Rect(5, 5, 1, 2)), 5, 5));
    }

    @Test
    void groundThatIsNotLoadedGivesNothing() {
        Rect house = new Rect(10, 20, 7, 5);
        assertNull(Footprints.of(world(new Rect(0, 0, 5, 5), house), 13, 22), "the marker's chunk is not loaded");
    }

    @Test
    void theSearchStopsAtTheReachSoTwoTouchingBuildingsAreNotMerged() {
        Rect row = new Rect(0, 0, 60, 6); // a long terrace of houses
        Rect found = Footprints.of(world(ALL, row), 5, 3);
        assertNotNull(found);
        assertTrue(found.width() <= Footprints.MAX_SIDE, "no wider than " + Footprints.MAX_SIDE + ": " + found);
        assertEquals(6, found.depth());
    }
}
