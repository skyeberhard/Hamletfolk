package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

/** R4.23: streets are levelled and filled; a hole in the ground does not stop a street. */
class StreetGradeTest {
    private static final Rect STREET = new Rect(0, 0, 30, 3); // runs east, three wide

    /** The height the street ends up at, per column, from the surface and bridge changes. */
    private static Map<Long, Integer> surface(List<StreetGrade.Change> changes) {
        Map<Long, Integer> out = new HashMap<>();
        for (StreetGrade.Change c : changes) {
            if (c.role() == StreetGrade.Role.SURFACE || c.role() == StreetGrade.Role.BRIDGE) {
                assertNull(out.put(TerrainPad.key(c.x(), c.z()), c.y()), "one surface per column at " + c.x() + "," + c.z());
            }
        }
        return out;
    }

    private static Integer at(Map<Long, Integer> surface, int x, int z) {
        return surface.get(TerrainPad.key(x, z));
    }

    @Test
    void aPieceOfStreetIsGradedTheWayItsStreetRunsNotTheWayItsShapeLooks() {
        // Three wide and two long: a north-south street clipped to a small window looks like it runs east-west.
        Rect window = new Rect(0, 0, 3, 2);
        IntBinaryOperator rises = (x, z) -> 64 + x; // the ground climbs a block for each step east
        HeightSource ground = HeightSource.of(rises);

        Map<Long, Integer> northSouth = surface(StreetGrade.computeStreets(List.of(new StreetGrade.Street(window, false)), ground));
        // each slice runs across the street's width (the three columns of a row), so a row is one height: the middle one
        for (int z = 0; z < 2; z++) {
            for (int x = 0; x < 3; x++) {
                assertEquals(65, at(northSouth, x, z), "row " + z + " is levelled across at " + x);
            }
        }
        Map<Long, Integer> guessed = surface(StreetGrade.compute(List.of(window), ground));
        assertEquals(64, at(guessed, 0, 0), "the shape guess grades it the other way, following the slope");
        assertEquals(66, at(guessed, 2, 0));
        assertNotEquals(northSouth, guessed);

        Map<Long, Integer> eastWest = surface(StreetGrade.computeStreets(List.of(new StreetGrade.Street(window, true)), ground));
        assertEquals(guessed, eastWest, "and the guess is exactly an east-west street");
    }

    private static List<StreetGrade.Change> grade(IntBinaryOperator ground) {
        return StreetGrade.compute(List.of(STREET), HeightSource.of(ground));
    }

    @Test
    void flatGroundIsJustPaved() {
        List<StreetGrade.Change> changes = grade((x, z) -> 64);
        assertEquals(30 * 3, changes.size());
        assertTrue(changes.stream().allMatch(c -> c.role() == StreetGrade.Role.SURFACE && c.y() == 64));
    }

    @Test
    void aHoleInTheGroundIsFilledToItsRimSoTheStreetRunsOn() {
        // A pit five long and four deep, across the whole street.
        List<StreetGrade.Change> changes = grade((x, z) -> x >= 10 && x <= 14 ? 60 : 64);
        Map<Long, Integer> surface = surface(changes);
        for (int x = 0; x < 30; x++) {
            assertEquals(64, at(surface, x, 1), "level at x=" + x);
        }
        // The pit is filled with soil from the bottom up, three blocks of it under a path on top.
        long fillInColumn = changes.stream().filter(c -> c.x() == 12 && c.z() == 1 && c.role() == StreetGrade.Role.FILL).count();
        assertEquals(3, fillInColumn);
        assertTrue(changes.stream().anyMatch(c -> c.x() == 12 && c.z() == 1 && c.y() == 61 && c.role() == StreetGrade.Role.FILL));
    }

    @Test
    void aDipOnOneSideOfTheStreetIsAveragedNotLeftAHole() {
        // Only the middle row has a hole: the slice takes the median of its three columns, so the hole is filled.
        List<StreetGrade.Change> changes = grade((x, z) -> z == 1 && x >= 10 && x <= 12 ? 58 : 64);
        Map<Long, Integer> surface = surface(changes);
        for (int x = 0; x < 30; x++) {
            for (int z = 0; z < 3; z++) {
                assertEquals(64, at(surface, x, z));
            }
        }
    }

    @Test
    void aShortBumpIsCutDown() {
        List<StreetGrade.Change> changes = grade((x, z) -> x >= 10 && x <= 12 ? 67 : 64);
        Map<Long, Integer> surface = surface(changes);
        assertEquals(64, at(surface, 11, 1));
        assertEquals(3, changes.stream().filter(c -> c.x() == 11 && c.z() == 1 && c.role() == StreetGrade.Role.AIR).count());
    }

    @Test
    void noStepIsMoreThanOneBlockEvenAcrossACliff() {
        // 64 on the left, 70 on the right: a six-block rise that the street climbs a block at a time.
        Map<Long, Integer> surface = surface(grade((x, z) -> x < 12 ? 64 : 70));
        for (int x = 1; x < 30; x++) {
            assertTrue(Math.abs(at(surface, x, 1) - at(surface, x - 1, 1)) <= 1, "step at x=" + x);
        }
        assertEquals(64, at(surface, 0, 1));
        assertEquals(70, at(surface, 29, 1));
    }

    @Test
    void aSlopeIsFollowedNotFlattened() {
        Map<Long, Integer> surface = surface(grade((x, z) -> 64 + x / 3));
        assertEquals(64, at(surface, 0, 1));
        assertEquals(73, at(surface, 29, 1));
    }

    @Test
    void waterUpToSixWideIsCrossedByABridgeAtTheWaterSurface() {
        HeightSource river = HeightSource.of((x, z) -> x >= 12 && x <= 15 ? 62 : 64, (x, z) -> x >= 12 && x <= 15);
        List<StreetGrade.Change> changes = StreetGrade.compute(List.of(STREET), river);
        Map<Long, Integer> surface = surface(changes);
        for (int x = 12; x <= 15; x++) {
            assertTrue(changes.stream().anyMatch(c -> c.x() == 13 && c.role() == StreetGrade.Role.BRIDGE && c.y() == 62));
            assertEquals(62, at(surface, x, 1));
        }
        assertTrue(Math.abs(at(surface, 11, 1) - at(surface, 12, 1)) <= 1, "the approach meets the deck");
        assertTrue(Math.abs(at(surface, 16, 1) - at(surface, 15, 1)) <= 1);
    }

    @Test
    void aLakeTooWideToCrossIsLeftAlone() {
        HeightSource lake = HeightSource.of((x, z) -> x >= 10 && x <= 20 ? 62 : 64, (x, z) -> x >= 10 && x <= 20);
        Map<Long, Integer> surface = surface(StreetGrade.compute(List.of(STREET), lake));
        for (int x = 10; x <= 20; x++) {
            assertNull(at(surface, x, 1), "no deck over eleven blocks of water, x=" + x);
        }
        assertNotNull(at(surface, 5, 1));
        assertNotNull(at(surface, 25, 1));
    }

    @Test
    void waterAtTheEndOfAStreetIsNotBridgedBecauseThereIsNoFarBank() {
        HeightSource shore = HeightSource.of((x, z) -> x >= 25 ? 62 : 64, (x, z) -> x >= 25);
        Map<Long, Integer> surface = surface(StreetGrade.compute(List.of(STREET), shore));
        assertNull(at(surface, 27, 1));
        assertEquals(64, at(surface, 10, 1));
    }

    @Test
    void unmeasuredAndImpossibleSlicesAreLeftAsTheyAre() {
        Map<Long, Integer> unknown = surface(grade((x, z) -> x == 8 ? HeightSource.UNKNOWN : 64));
        assertNull(at(unknown, 8, 1));
        assertEquals(64, at(unknown, 7, 1));
        assertEquals(64, at(unknown, 9, 1));

        Map<Long, Integer> shaft = surface(grade((x, z) -> x == 8 ? 58 : 64));
        assertEquals(64, at(shaft, 8, 1), "a one-block-wide shaft is a dip like any other, if it is within the limit");
        Map<Long, Integer> well = surface(grade((x, z) -> x == 8 ? 40 : 64));
        assertNull(at(well, 8, 1), "but one 24 blocks deep is more than a street fills");
        Map<Long, Integer> chasm = surface(grade((x, z) -> x >= 8 && x <= 25 ? 40 : 64));
        assertNull(at(chasm, 15, 1), "a chasm too wide to fill and too deep to follow is not graded");
    }

    @Test
    void aColumnWithSomethingBuiltOnItIsSkippedWithoutSpoilingTheRestOfTheSlice() {
        List<StreetGrade.Change> changes = grade((x, z) -> x == 8 && z == 0 ? HeightSource.UNKNOWN : 64);
        assertTrue(changes.stream().noneMatch(c -> c.x() == 8 && c.z() == 0), "the built column is left alone");
        assertTrue(changes.stream().anyMatch(c -> c.x() == 8 && c.z() == 1), "its neighbours are paved");
        assertEquals(30 * 3 - 1, changes.size());
    }

    @Test
    void whereTwoStreetsCrossTheColumnsKeepTheFirstOnesHeight() {
        Rect across = new Rect(14, -10, 3, 25);
        List<StreetGrade.Change> changes = StreetGrade.compute(List.of(STREET, across), HeightSource.of((x, z) -> 64 + z / 5));
        Map<Long, Integer> surface = surface(changes); // fails if a column got two surfaces
        assertFalse(changes.isEmpty());
        for (int z = 0; z < 3; z++) {
            assertEquals(64, at(surface, 15, z), "the crossing keeps the first street's height");
        }
        for (int z = -9; z < 15; z++) {
            assertTrue(Math.abs(at(surface, 15, z) - at(surface, 15, z - 1)) <= 1, "no step along the second street at z=" + z);
        }
        for (int x = 1; x < 30; x++) {
            assertTrue(Math.abs(at(surface, x, 1) - at(surface, x - 1, 1)) <= 1, "no step along the first street at x=" + x);
        }
    }
}
