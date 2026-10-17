package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

/** R8.21: a building on ground too steep to level in the usual way is set in a terrace with a retaining wall. */
class TerracePadTest {
    private static final Rect LOT = new Rect(20, 20, 9, 9);

    /** Ground that climbs {@code rise} blocks for every block east, so a lot nine wide spans {@code 8 * rise}. */
    private static HeightSource slope(int rise) {
        IntBinaryOperator h = (x, z) -> 64 + rise * (x - 20) / 1;
        return HeightSource.of(h);
    }

    private static long count(TerrainPad.Pad pad, TerrainPad.Role role) {
        return pad.changes().stream().filter(c -> c.role() == role).count();
    }

    @Test
    void groundTheUsualGradingRefusesIsBuiltAsATerrace() {
        HeightSource steep = slope(2); // 16 blocks across the lot
        TerrainPad.Pad usual = TerrainPad.compute(LOT, 2, steep);
        assertFalse(usual.valid(), "the usual grading gives up: " + usual.reason());
        assertTrue(usual.reason().contains(" blocks of "));
        TerrainPad.Pad fit = TerrainPad.fit(LOT, 2, steep, null);
        assertTrue(fit.valid(), fit.reason());
        assertTrue(fit.terraced());
        assertEquals(64 + 2 * 4, fit.targetHeight(), "levelled to the median, the middle column");
    }

    @Test
    void groundTheUsualGradingAcceptsIsNotTerracedAtAll() {
        TerrainPad.Pad fit = TerrainPad.fit(LOT, 2, slope(1), null); // 8 across
        assertTrue(fit.valid());
        assertFalse(fit.terraced());
        assertEquals(TerrainPad.compute(LOT, 2, slope(1)).changes(), fit.changes(), "exactly as before");
    }

    @Test
    void theRingIsAWallWhereItIsFilledAndTheHillFaceIsArmouredWhereItIsCut() {
        TerrainPad.Pad pad = TerrainPad.terrace(LOT, slope(2), null);
        int target = pad.targetHeight(); // 72
        // downhill (west) ring column x=19: the natural ground there is 62, so it is filled to 72, in wall blocks under a surface
        List<TerrainPad.Change> west = pad.changes().stream().filter(c -> c.x() == 19 && c.z() == 24).toList();
        assertEquals(TerrainPad.Role.SURFACE, west.get(west.size() - 1).role());
        assertEquals(target, west.get(west.size() - 1).y());
        assertTrue(west.stream().filter(c -> c.role() == TerrainPad.Role.WALL).count() >= 9, "a wall from the low ground up: " + west.size());
        // uphill (east) ring column x=29: natural 82, cut to 72
        List<TerrainPad.Change> east = pad.changes().stream().filter(c -> c.x() == 29 && c.z() == 24).toList();
        assertTrue(east.stream().anyMatch(c -> c.role() == TerrainPad.Role.AIR));
        assertEquals(target, pad.finalHeight(29, 24));
        // the hill face one block further out (x=30, natural 84) is wall from the pad's level up to its top
        List<TerrainPad.Change> face = pad.changes().stream().filter(c -> c.x() == 30 && c.z() == 24).toList();
        assertTrue(face.stream().allMatch(c -> c.role() == TerrainPad.Role.WALL));
        assertEquals(84 - target, face.size(), "from target+1 to the top of the hill");
        assertEquals(target + 1, face.get(0).y());
        // nothing is changed beyond the second ring
        assertTrue(pad.changes().stream().noneMatch(c -> c.x() < 18 || c.x() > 30 || c.z() < 18 || c.z() > 30));
    }

    @Test
    void everyColumnOfThePadEndsAtTheTargetAndNoColumnIsChangedTwice() {
        TerrainPad.Pad pad = TerrainPad.terrace(LOT, slope(2), null);
        for (int x = 19; x <= 29; x++) {
            for (int z = 19; z <= 29; z++) {
                assertEquals(pad.targetHeight(), pad.finalHeight(x, z), x + "," + z);
            }
        }
        // each column's changes run from the ground up (so a placer can lay them in order)
        for (int x = 18; x <= 30; x++) {
            for (int z = 18; z <= 30; z++) {
                final int fx = x;
                final int fz = z;
                List<Integer> ys = pad.changes().stream().filter(c -> c.x() == fx && c.z() == fz).map(TerrainPad.Change::y).toList();
                assertEquals(ys.stream().sorted().toList().size(), ys.stream().distinct().count(), "one change per block at " + x + "," + z);
            }
        }
    }

    @Test
    void groundBeyondTheTerracesReachIsStillRefused() {
        TerrainPad.Pad sheer = TerrainPad.fit(LOT, 2, slope(5), null); // 40 across
        assertFalse(sheer.valid());
        assertTrue(sheer.reason().contains(" blocks of "), sheer.reason());
        assertFalse(TerrainPad.terrace(LOT, slope(5), null).valid());
        // unmeasured ground and deep water are not helped by a terrace
        HeightSource unknown = HeightSource.of((x, z) -> x == 22 && z == 22 ? HeightSource.UNKNOWN : 64);
        assertFalse(TerrainPad.fit(LOT, 2, unknown, null).valid());
        assertTrue(TerrainPad.fit(LOT, 2, unknown, null).reason().contains("not been measured"));
    }

    @Test
    void aFixedTargetIsKeptSoAHalfBuiltTerraceDoesNotMoveItsGoalposts() {
        TerrainPad.Pad pad = TerrainPad.fit(LOT, 2, slope(2), 70);
        assertTrue(pad.valid());
        assertEquals(70, pad.targetHeight());
    }

    @Test
    void thePlanKeepsASteepLotAsRoughNotDroppedAndOnlyTerraceableOnes() {
        assertTrue(PlanGenerator.terraceable(slope(2), LOT));
        assertFalse(PlanGenerator.terraceable(slope(5), LOT));
        assertTrue(PlanGenerator.terraceable(slope(0), LOT), "flat ground is terraceable too (the usual grading is tried first)");
        VillagePlan plan = PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.of((x, z) -> 64 + 2 * (x / 4)));
        long rough = plan.lots().stream().filter(l -> l.status() == VillagePlan.LotStatus.ROUGH).count();
        assertTrue(rough > 0, "a hillside plan has rough lots: " + plan.lots().size() + " lots, " + plan.dropped() + " dropped");
    }

    /** The placer stops only between columns, so every column's changes must be one run in the list (else it could stop inside one). */
    @Test
    void eachColumnsChangesAreOneRunInTheList() {
        for (TerrainPad.Pad pad : List.of(TerrainPad.terrace(LOT, slope(2), null), TerrainPad.compute(LOT, 2, slope(1)),
                TerrainPad.terrace(LOT, HeightSource.of((x, z) -> 64 + (x - 20) + 2 * (z - 20)), null))) {
            assertTrue(pad.valid(), pad.reason());
            java.util.Set<Long> seen = new java.util.HashSet<>();
            long previous = Long.MIN_VALUE;
            for (TerrainPad.Change c : pad.changes()) {
                long column = TerrainPad.key(c.x(), c.z());
                if (column != previous) {
                    assertTrue(seen.add(column), "column " + c.x() + "," + c.z() + " comes back after another: the placer could stop inside it");
                    previous = column;
                }
            }
        }
    }

    /** A diagonal slope: every column ends at the target and nothing is placed twice. */
    @Test
    void aDiagonalSlopeTerracesToo() {
        HeightSource diagonal = HeightSource.of((x, z) -> 64 + (x - 20) + (z - 20));
        TerrainPad.Pad pad = TerrainPad.terrace(LOT, diagonal, null);
        assertTrue(pad.valid(), pad.reason());
        assertEquals(64 + 8, pad.targetHeight());
        for (int x = 19; x <= 29; x++) {
            for (int z = 19; z <= 29; z++) {
                assertEquals(pad.targetHeight(), pad.finalHeight(x, z));
            }
        }
        long distinct = pad.changes().stream().map(c -> c.x() + "," + c.y() + "," + c.z()).distinct().count();
        assertEquals(pad.changes().size(), distinct, "no block changed twice");
    }

    @Test
    void aLotMostlyUnderWaterIsNotTerracedIntoALake() {
        HeightSource lake = HeightSource.of((x, z) -> 64 + 2 * (x - 20), (x, z) -> x < 25);
        assertFalse(TerrainPad.terrace(LOT, lake, null).valid());
    }
}
