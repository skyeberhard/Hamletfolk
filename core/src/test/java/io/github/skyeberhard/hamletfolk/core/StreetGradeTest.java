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
            if (c.role() == StreetGrade.Role.SURFACE || c.role() == StreetGrade.Role.BRIDGE || c.role() == StreetGrade.Role.STAIR) {
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
        HeightSource lake = HeightSource.of((x, z) -> x >= 6 && x <= 24 ? 62 : 64, (x, z) -> x >= 6 && x <= 24);
        Map<Long, Integer> surface = surface(StreetGrade.compute(List.of(STREET), lake));
        for (int x = 6; x <= 24; x++) {
            assertNull(at(surface, x, 1), "no deck over nineteen blocks of water, x=" + x);
        }
        assertNotNull(at(surface, 3, 1));
        assertNotNull(at(surface, 27, 1));
    }

    /** R8.22: a river wider than six blocks, up to sixteen, is crossed too. */
    @Test
    void aRiverUpToSixteenWideIsBridged() {
        HeightSource river = HeightSource.of((x, z) -> x >= 8 && x <= 21 ? 62 : 64, (x, z) -> x >= 8 && x <= 21);
        Map<Long, Integer> surface = surface(StreetGrade.compute(List.of(STREET), river));
        for (int x = 8; x <= 21; x++) {
            assertEquals(62, at(surface, x, 1), "a deck over fourteen blocks of water, x=" + x);
        }
    }

    private static HeightSource gorge(int from, int to, int bottom, int rim) {
        return HeightSource.of((x, z) -> x >= from && x <= to ? bottom : rim);
    }

    private static long count(List<StreetGrade.Change> changes, StreetGrade.Role role) {
        return changes.stream().filter(c -> c.role() == role).count();
    }

    /** R8.22: a gorge is crossed by a deck at the height of the banks, with a rail on each side and a lamp at each end. */
    @Test
    void aGorgeIsCrossedByADeckWithRailsAndLamps() {
        List<StreetGrade.Change> changes = StreetGrade.compute(List.of(STREET), gorge(10, 19, 30, 64));
        Map<Long, Integer> surface = surface(changes);
        for (int x = 10; x <= 19; x++) {
            for (int z = 0; z < 3; z++) {
                assertEquals(64, at(surface, x, z), "a deck across the whole street, x=" + x);
            }
            assertTrue(changes.stream().anyMatch(c -> c.x() == 14 && c.z() == 1 && c.role() == StreetGrade.Role.BRIDGE), "a deck block, not fill");
        }
        assertEquals(0, changes.stream().filter(c -> c.x() >= 10 && c.x() <= 19 && c.role() == StreetGrade.Role.FILL).count(), "the gorge is not filled");
        assertEquals(2 * 10, count(changes, StreetGrade.Role.RAIL), "a rail on each side of every deck slice (ten slices, two sides)");
        assertTrue(changes.stream().allMatch(c -> c.role() != StreetGrade.Role.RAIL || (c.y() == 65 && (c.z() == 0 || c.z() == 2))));
        List<StreetGrade.Change> lamps = changes.stream().filter(c -> c.role() == StreetGrade.Role.LAMP).toList();
        assertEquals(4, lamps.size(), "a lamp on each side at each end");
        assertTrue(lamps.stream().allMatch(c -> c.x() == 9 || c.x() == 20));
    }

    @Test
    void aGorgeWithBanksAtDifferentHeightsClimbsEvenlyAcrossIt() {
        HeightSource uneven = HeightSource.of((x, z) -> x < 10 ? 64 : x > 19 ? 70 : 30);
        Map<Long, Integer> surface = surface(StreetGrade.compute(List.of(STREET), uneven));
        for (int x = 10; x < 20; x++) {
            assertTrue(Math.abs(at(surface, x, 1) - at(surface, x - 1, 1)) <= 1, "a step of at most a block at " + x);
        }
        assertTrue(at(surface, 19, 1) >= 69 && at(surface, 10, 1) <= 65);
    }

    @Test
    void aGorgeTooWideOrTooLopsidedToCrossIsNotBridged() {
        List<StreetGrade.Change> wide = StreetGrade.compute(List.of(STREET), gorge(6, 24, 30, 64)); // 19 wide
        assertEquals(0, count(wide, StreetGrade.Role.BRIDGE));
        assertEquals(0, count(wide, StreetGrade.Role.RAIL));
        HeightSource cliff = HeightSource.of((x, z) -> x < 10 ? 64 : x > 19 ? 100 : 30); // banks 36 apart over ten blocks
        assertEquals(0, count(StreetGrade.compute(List.of(STREET), cliff), StreetGrade.Role.BRIDGE));
        List<StreetGrade.Change> atTheEnd = StreetGrade.compute(List.of(STREET), gorge(22, 29, 30, 64)); // no far bank
        assertEquals(0, count(atTheEnd, StreetGrade.Role.BRIDGE));
    }

    /** A dip the street can fill stays a dip: no deck. */
    @Test
    void aDipThatCanBeFilledIsFilledNotBridged() {
        List<StreetGrade.Change> changes = StreetGrade.compute(List.of(STREET), gorge(10, 15, 58, 64)); // six long, six deep
        assertEquals(0, count(changes, StreetGrade.Role.BRIDGE));
        assertEquals(64, at(surface(changes), 12, 1));
        // the filled stretch stands six blocks above the ground beside it, so it is fenced on both sides (and only there)
        List<StreetGrade.Change> rails = changes.stream().filter(c -> c.role() == StreetGrade.Role.RAIL).toList();
        assertEquals(12, rails.size());
        assertTrue(rails.stream().allMatch(c -> c.x() >= 10 && c.x() <= 15));
    }

    private static boolean isPost(StreetGrade.Change c) {
        return c.role() == StreetGrade.Role.RAIL || c.role() == StreetGrade.Role.LAMP;
    }

    /** A post at a junction or a crossing deck would block it for good, as nothing takes one away. */
    @Test
    void noPostStandsInOrBesideAnotherStreet() {
        Rect spine = new Rect(0, 0, 30, 5);
        Rect branch = new Rect(11, 5, 3, 10); // joins the spine's south edge
        HeightSource dip = HeightSource.of((x, z) -> x >= 10 && x <= 15 ? 58 : 64); // the spine is filled across a dip
        List<StreetGrade.Change> changes = StreetGrade.computeStreets(
                List.of(new StreetGrade.Street(spine, true), new StreetGrade.Street(branch, false)), dip);
        for (StreetGrade.Change c : changes) {
            if (isPost(c)) {
                assertFalse(branch.inflated(1).contains(c.x(), c.z()), "a post at " + c.x() + "," + c.z() + " beside the branch");
            }
        }
        assertTrue(changes.stream().anyMatch(StreetGradeTest::isPost), "the rest of the filled stretch is still fenced");

        // a deck across a street that crosses it: the crossing street's way is left clear
        Rect cross = new Rect(0, 0, 3, 30); // runs south, crossing the gorge's deck
        Rect road = new Rect(0, 12, 30, 3); // an east-west street that meets the deck halfway across
        HeightSource gorge = HeightSource.of((x, z) -> z >= 5 && z <= 20 ? 30 : 64);
        List<StreetGrade.Change> crossing = StreetGrade.computeStreets(
                List.of(new StreetGrade.Street(cross, false), new StreetGrade.Street(road, true)), gorge);
        for (StreetGrade.Change c : crossing) {
            if (isPost(c)) {
                assertFalse(road.inflated(1).contains(c.x(), c.z()) && cross.contains(c.x(), c.z()) , "no rail across the crossing street at " + c.x() + "," + c.z());
            }
        }
    }

    /** A piece of a street clipped to the ground round a building has an edge that is not the street's. */
    @Test
    void aClippedPiecesCutEdgeIsNotFenced() {
        HeightSource falling = HeightSource.of((x, z) -> 64 - 2 * z); // the ground falls away to the south
        Rect piece = new Rect(0, 0, 30, 3);
        Rect whole = new Rect(0, 0, 30, 5);
        long clipped = StreetGrade.computeStreets(List.of(new StreetGrade.Street(piece, true, whole)), falling).stream()
                .filter(StreetGradeTest::isPost).count();
        assertEquals(0, clipped, "the south edge of the piece is the middle of the street");
        long whole3 = StreetGrade.computeStreets(List.of(new StreetGrade.Street(piece, true)), falling).stream().filter(StreetGradeTest::isPost).count();
        assertTrue(whole3 > 0, "a street that really ends there is fenced");
    }

    /** A ledge and then a small rise is a slope the street can follow, not a gorge. */
    @Test
    void aTerracedHillStaysARamp() {
        HeightSource terraces = HeightSource.of((x, z) -> x < 10 ? 70 : x < 20 ? 66 : 67);
        List<StreetGrade.Change> changes = StreetGrade.compute(List.of(STREET), terraces);
        assertEquals(0, count(changes, StreetGrade.Role.BRIDGE), "no deck over a meadow");
        assertEquals(0, count(changes, StreetGrade.Role.RAIL));
        assertTrue(surface(changes).size() >= 60);
    }

    /** A ravine with a river at the bottom is crossed at the rim, not by a bridge on the river. */
    @Test
    void aRavineWithARiverInItIsBridgedAtTheRim() {
        HeightSource ravine = HeightSource.of((x, z) -> x < 10 || x > 19 ? 64 : x >= 13 && x <= 16 ? 40 : 30, (x, z) -> x >= 13 && x <= 16);
        List<StreetGrade.Change> changes = StreetGrade.compute(List.of(STREET), ravine);
        Map<Long, Integer> surface = surface(changes);
        for (int x = 10; x <= 19; x++) {
            assertEquals(64, at(surface, x, 1), "the deck is at the rim over x=" + x);
        }
        assertTrue(changes.stream().noneMatch(c -> c.role() == StreetGrade.Role.BRIDGE && c.y() < 60), "no deck at the river");
    }

    /** Two scans that find overlapping gaps must not leave a wall in the deck. */
    @Test
    void aDeckNeverStepsMoreThanABlock() {
        int[] heights = {80, 80, 80, 80, 80, 60, 60, 60, 60, 60, 60, 77, 60, 60, 60, 82, 82, 82, 82, 82};
        HeightSource pillar = HeightSource.of((x, z) -> x < heights.length ? heights[x] : 82);
        Map<Long, Integer> surface = surface(StreetGrade.compute(List.of(new Rect(0, 0, 20, 3)), pillar));
        for (int x = 1; x < 20; x++) {
            Integer here = at(surface, x, 1);
            Integer before = at(surface, x - 1, 1);
            if (here != null && before != null) {
                assertTrue(Math.abs(here - before) <= 1, "a step of " + (here - before) + " at x=" + x);
            }
        }
    }

    @Test
    void aNarrowStreetGetsNoRailsOnItsDeck() {
        Rect lane = new Rect(0, 0, 30, 1);
        List<StreetGrade.Change> changes = StreetGrade.compute(List.of(lane), gorge(10, 19, 30, 64));
        assertTrue(count(changes, StreetGrade.Role.BRIDGE) > 0);
        assertEquals(0, count(changes, StreetGrade.Role.RAIL), "a rail on a one-wide street would block it");
        assertEquals(0, count(changes, StreetGrade.Role.LAMP));
    }

    /** R8.22: a street that runs along a drop of more than three blocks is fenced on that side. */
    @Test
    void aStreetAlongADropIsFencedOnThatSide() {
        HeightSource edge = HeightSource.of((x, z) -> z < 0 ? 56 : 64); // the ground falls away north of the street
        List<StreetGrade.Change> changes = StreetGrade.compute(List.of(STREET), edge);
        List<StreetGrade.Change> rails = changes.stream().filter(c -> c.role() == StreetGrade.Role.RAIL).toList();
        assertEquals(30, rails.size(), "one post for each block along the street");
        assertTrue(rails.stream().allMatch(c -> c.z() == 0 && c.y() == 65), "on the north edge, one block above the path");
        HeightSource gentle = HeightSource.of((x, z) -> z < 0 ? 62 : 64);
        assertEquals(0, count(StreetGrade.compute(List.of(STREET), gentle), StreetGrade.Role.RAIL), "two blocks is not a drop");
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
        assertEquals(64, at(well, 8, 1), "but one 24 blocks deep is more than a street fills: it is decked over (R8.22)");
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

    // ----- R8.21: steps in the road -----

    private static List<StreetGrade.Change> stairs(List<StreetGrade.Change> changes) {
        return changes.stream().filter(c -> c.role() == StreetGrade.Role.STAIR).toList();
    }

    /** A road up a gentle hill: a block of rise every few blocks, each rise a stair facing uphill on the higher slice. */
    @Test
    void aRoadThatRisesABlockHasAStairFacingUphillAtEachRise() {
        // east-west street: the ground steps up one block at x=10 and again at x=20
        List<StreetGrade.Change> changes = grade((x, z) -> 64 + (x >= 20 ? 2 : x >= 10 ? 1 : 0));
        List<StreetGrade.Change> stairs = stairs(changes);
        assertEquals(6, stairs.size(), "three columns at each of two rises: " + stairs);
        for (StreetGrade.Change c : stairs) {
            assertEquals(1, c.facing(), "east: the way it climbs");
            assertTrue((c.x() == 10 && c.y() == 65) || (c.x() == 20 && c.y() == 66), "the first slice of each higher stretch: " + c);
        }
        Map<Long, Integer> surface = surface(changes);
        assertEquals(64, at(surface, 5, 1));
        assertEquals(65, at(surface, 10, 1), "the stair stands at the higher level, its back level with the higher street");
        assertEquals(65, at(surface, 15, 1));
        assertEquals(66, at(surface, 25, 1));
        assertTrue(changes.stream().noneMatch(c -> c.role() != StreetGrade.Role.STAIR && c.x() == 10 && c.y() == 65 && c.role() == StreetGrade.Role.SURFACE),
                "a stair, not also a plain block, in the same cell");
    }

    @Test
    void aRoadThatFallsHasItsStairOnTheHigherSideFacingBackUpTheSlope() {
        List<StreetGrade.Change> stairs = stairs(grade((x, z) -> x >= 15 ? 64 : 65));
        assertEquals(3, stairs.size());
        for (StreetGrade.Change c : stairs) {
            assertEquals(14, c.x(), "the last slice of the higher stretch, nearest the drop");
            assertEquals(3, c.facing(), "west: back up towards the high end");
            assertEquals(65, c.y());
        }
    }

    @Test
    void aNorthSouthRoadClimbsNorthOrSouth() {
        Rect lane = new Rect(0, 0, 3, 30);
        List<StreetGrade.Change> up = stairs(StreetGrade.compute(List.of(lane), HeightSource.of((x, z) -> z >= 12 ? 65 : 64)));
        assertEquals(3, up.size());
        assertTrue(up.stream().allMatch(c -> c.facing() == 2 && c.z() == 12), "rising to the south: faces south");
        List<StreetGrade.Change> down = stairs(StreetGrade.compute(List.of(lane), HeightSource.of((x, z) -> z >= 12 ? 64 : 65)));
        assertTrue(down.stream().allMatch(c -> c.facing() == 0 && c.z() == 11), "falling to the south: the stair is on the high side, facing north");
    }

    @Test
    void aFlatRoadAPeakAndABridgeHaveNoStairs() {
        assertTrue(stairs(grade((x, z) -> 64)).isEmpty());
        // a crest: higher than both neighbours, so it cannot climb two ways (smoothing cuts a one-slice bump before this is asked, so ask directly)
        int[] crest = StreetGrade.stairs(new int[] {64, 65, 64}, new boolean[] {true, true, true}, new boolean[3], true);
        assertEquals(-1, crest[1], "no stair on the crest");
        int[] valley = StreetGrade.stairs(new int[] {65, 64, 65}, new boolean[] {true, true, true}, new boolean[3], true);
        assertEquals(1, valley[2], "the far side of a dip climbs east");
        assertEquals(3, valley[0], "and the near side climbs back west");
        assertEquals(-1, valley[1]);
        int[] undecked = StreetGrade.stairs(new int[] {64, 65, 66}, new boolean[] {true, true, true}, new boolean[] {false, true, false}, true);
        assertEquals(-1, undecked[1], "a deck slice has none");
        HeightSource river = HeightSource.of((x, z) -> x >= 12 && x <= 15 ? 62 : 64, (x, z) -> x >= 12 && x <= 15);
        assertTrue(stairs(StreetGrade.compute(List.of(STREET), river)).stream().noneMatch(c -> c.x() >= 12 && c.x() <= 15), "no stair on a deck");
    }

    @Test
    void aStaircaseOfOneBlockPerBlockHasAStairOnEverySlice() {
        List<StreetGrade.Change> changes = grade((x, z) -> 64 + Math.min(Math.max(x - 5, 0), 6));
        List<StreetGrade.Change> stairs = stairs(changes);
        long slices = stairs.stream().map(StreetGrade.Change::x).distinct().count();
        assertEquals(6, slices, "x=6..11: each a block higher than the one before");
        assertTrue(stairs.stream().allMatch(c -> c.facing() == 1));
        Map<Long, Integer> surface = surface(changes);
        for (int x = 6; x <= 11; x++) {
            assertEquals(64 + (x - 5), at(surface, x, 1));
        }
    }

    /** R8.21: a plan's grading is laid a little at a time, a whole column in each batch. */
    @Test
    void aGradingIsSplitIntoBatchesThatEndOnWholeColumns() {
        List<StreetGrade.Change> changes = grade((x, z) -> 64 + (x % 7 < 3 ? 0 : 3));
        List<List<StreetGrade.Change>> batches = StreetGrade.batches(changes, 25);
        assertTrue(batches.size() > 1, "" + changes.size());
        assertEquals(changes, batches.stream().flatMap(List::stream).toList(), "nothing lost, nothing reordered");
        for (int i = 0; i + 1 < batches.size(); i++) {
            List<StreetGrade.Change> batch = batches.get(i);
            StreetGrade.Change last = batch.get(batch.size() - 1);
            StreetGrade.Change next = batches.get(i + 1).get(0);
            assertTrue(last.x() != next.x() || last.z() != next.z(), "a column is never split between two batches at " + last);
            assertTrue(batch.size() >= 25);
        }
        assertTrue(StreetGrade.batches(List.of(), 25).isEmpty());
        assertEquals(1, StreetGrade.batches(changes, Integer.MAX_VALUE).size());
    }

    @Test
    void aGradingIsSummarisedByRole() {
        List<StreetGrade.Change> changes = grade((x, z) -> x >= 15 ? 65 : 64);
        Map<StreetGrade.Role, Integer> counts = StreetGrade.summarize(changes);
        assertEquals(3, counts.get(StreetGrade.Role.STAIR));
        assertEquals(changes.size(), counts.values().stream().mapToInt(Integer::intValue).sum());
    }

    /** Stairs where two streets cross: each column still has exactly one surface block, and the street laid second keeps what the first gave it. */
    @Test
    void aJunctionOfTwoSlopingStreetsHasOneSurfaceBlockPerColumn() {
        Rect eastWest = new Rect(0, 10, 30, 3);
        Rect northSouth = new Rect(12, 0, 3, 30);
        HeightSource hill = HeightSource.of((x, z) -> 64 + x / 8 + z / 10);
        List<StreetGrade.Change> changes = StreetGrade.computeStreets(
                List.of(new StreetGrade.Street(eastWest, true), new StreetGrade.Street(northSouth, false)), hill);
        Map<Long, Integer> surface = surface(changes); // asserts one surface block per column
        assertTrue(surface.size() > 100);
        assertTrue(changes.stream().anyMatch(c -> c.role() == StreetGrade.Role.STAIR));
        for (StreetGrade.Change c : changes) {
            if (c.role() == StreetGrade.Role.STAIR) {
                assertTrue(c.facing() >= 0 && c.facing() <= 3);
            }
        }
    }
}
