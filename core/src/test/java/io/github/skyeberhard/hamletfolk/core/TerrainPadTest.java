package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** R8.4: grading a lot is heights in, a block diff out: target, cut, fill, foundation, blended edge, and stepped roads. */
class TerrainPadTest {
    private static final Rect LOT = new Rect(0, 0, 9, 9);
    private static final int BUFFER = 3;

    private static List<TerrainPad.Change> column(TerrainPad.Pad pad, int x, int z) {
        return pad.changes().stream().filter(c -> c.x() == x && c.z() == z).toList();
    }

    @Test
    void flatGroundNeedsNothing() {
        TerrainPad.Pad pad = TerrainPad.compute(LOT, BUFFER, HeightSource.flat(64));
        assertTrue(pad.valid());
        assertEquals(64, pad.targetHeight());
        assertTrue(pad.changes().isEmpty());
        assertEquals(64, pad.finalHeight(4, 4));
    }

    @Test
    void aSlopeIsCutDownToTheMedianHeight() {
        // Five columns at 64 and four at 66: the median is 64, so the high side is cut by two.
        HeightSource slope = HeightSource.of((x, z) -> x >= 5 ? 66 : 64);
        TerrainPad.Pad pad = TerrainPad.compute(LOT, BUFFER, slope);
        assertTrue(pad.valid());
        assertEquals(64, pad.targetHeight());
        assertTrue(pad.cut() >= 4 * 9 * 2, "two blocks off each of the 36 high columns (and the blended edge)");
        for (int x = 0; x < 9; x++) {
            assertEquals(64, pad.finalHeight(x, 4), "the pad is level");
        }
        List<TerrainPad.Change> high = column(pad, 6, 4);
        assertEquals(TerrainPad.Role.AIR, high.get(0).role());
        assertEquals(TerrainPad.Role.SURFACE, high.get(high.size() - 1).role());
        assertEquals(64, high.get(high.size() - 1).y());
    }

    @Test
    void aHollowIsFilledWithSoilOnTopAndFoundationBelowReachingTheGround() {
        // Nine columns six blocks down in the middle of ground at 64: filled from the bottom of the hole to the top.
        HeightSource hollow = HeightSource.of((x, z) -> x >= 3 && x <= 5 && z >= 3 && z <= 5 ? 58 : 64);
        TerrainPad.Pad pad = TerrainPad.compute(LOT, BUFFER, hollow);
        assertTrue(pad.valid());
        List<TerrainPad.Change> col = column(pad, 4, 4);
        assertEquals(6, col.size());
        assertEquals(59, col.get(0).y(), "starts on the ground at the bottom of the hole: nothing floats");
        for (int i = 1; i < col.size(); i++) {
            assertEquals(col.get(i - 1).y() + 1, col.get(i).y(), "no gap in the fill");
        }
        assertEquals(TerrainPad.Role.FOUNDATION, col.get(0).role());
        assertEquals(TerrainPad.Role.FOUNDATION, col.get(1).role());
        assertEquals(TerrainPad.Role.FILL, col.get(2).role());
        assertEquals(TerrainPad.Role.FILL, col.get(4).role());
        assertEquals(TerrainPad.Role.SURFACE, col.get(5).role());
        assertEquals(64, col.get(5).y());
    }

    @Test
    void aLotOnShallowWaterIsFilledFromTheBedAndOnDeepWaterIsRefused() {
        HeightSource shallow = HeightSource.withFloor((x, z) -> 62, (x, z) -> true, (x, z) -> 60);
        TerrainPad.Pad pad = TerrainPad.compute(LOT, BUFFER, shallow);
        assertTrue(pad.valid());
        List<TerrainPad.Change> col = column(pad, 4, 4);
        assertEquals(2, col.size());
        assertEquals(61, col.get(0).y(), "from the bed, through the water");
        assertEquals(TerrainPad.Role.SURFACE, col.get(1).role());
        assertEquals(62, col.get(1).y());

        HeightSource deep = HeightSource.withFloor((x, z) -> 62, (x, z) -> true, (x, z) -> 55);
        TerrainPad.Pad refused = TerrainPad.compute(LOT, BUFFER, deep);
        assertFalse(refused.valid());
        assertTrue(refused.reason().contains("deep"), refused.reason());
        assertTrue(refused.changes().isEmpty());
    }

    @Test
    void unmeasuredGroundIsRefused() {
        TerrainPad.Pad pad = TerrainPad.compute(LOT, BUFFER, (x, z) -> x == 4 && z == 4 ? HeightSource.UNKNOWN : 64);
        assertFalse(pad.valid());
        assertTrue(pad.reason().contains("not been measured"), pad.reason());
    }

    @Test
    void aCliffTooBigToGradeIsRefusedAndOneThatFitsIsCutWithABlendedEdge() {
        HeightSource tooHigh = HeightSource.of((x, z) -> x >= 5 ? 75 : 64);
        assertFalse(TerrainPad.compute(LOT, BUFFER, tooHigh).valid(), "eleven blocks of cut");

        HeightSource cliff = HeightSource.of((x, z) -> x >= 5 ? 70 : 64);
        TerrainPad.Pad pad = TerrainPad.compute(LOT, BUFFER, cliff);
        assertTrue(pad.valid());
        // Along a row out through the east edge of the lot the surface climbs by one block per block, then the cliff is as it was.
        int previous = pad.finalHeight(8, 4);
        assertEquals(64, previous);
        for (int x = 9; x <= 11; x++) {
            int h = pad.finalHeight(x, 4);
            assertEquals(previous + 1, h, "one block per block out to the end of the buffer");
            previous = h;
        }
    }

    @Test
    void onSmoothGroundTheGradedSurfaceNeverChangesByMoreThanOneBlockBetweenNeighbours() {
        for (int seed = 0; seed < 40; seed++) {
            int a = seed % 3 - 1; // slopes of -1, 0 or 1 block per block
            int b = (seed / 3) % 3 - 1;
            HeightSource smooth = HeightSource.of((x, z) -> 64 + a * x + b * z);
            TerrainPad.Pad pad = TerrainPad.compute(LOT, BUFFER, smooth);
            if (!pad.valid()) {
                continue;
            }
            for (int x = -BUFFER; x < 9 + BUFFER - 1; x++) {
                for (int z = -BUFFER; z < 9 + BUFFER - 1; z++) {
                    Integer h = pad.finalHeight(x, z);
                    Integer east = pad.finalHeight(x + 1, z);
                    Integer south = pad.finalHeight(x, z + 1);
                    if (h != null && east != null) {
                        assertTrue(Math.abs(h - east) <= 1, "seed " + seed + " at " + x + "," + z + " east");
                    }
                    if (h != null && south != null) {
                        assertTrue(Math.abs(h - south) <= 1, "seed " + seed + " at " + x + "," + z + " south");
                    }
                }
            }
        }
    }

    @Test
    void theSameGroundAlwaysGivesTheSamePad() {
        HeightSource bumpy = HeightSource.of((x, z) -> 64 + Math.floorMod(x * 7 + z * 3, 4));
        assertEquals(TerrainPad.compute(LOT, BUFFER, bumpy), TerrainPad.compute(LOT, BUFFER, bumpy));
    }

    @Test
    void roadsRiseAndFallAtMostOneBlockAStepWithStairsAndSlabs() {
        int[] natural = {64, 64, 65, 66, 67, 67, 66, 64, 60, 60, 61};
        List<TerrainPad.Step> steps = TerrainPad.roadProfile(natural);
        assertEquals(natural.length, steps.size());
        assertEquals(64, steps.get(0).height());
        for (int i = 1; i < steps.size(); i++) {
            assertTrue(Math.abs(steps.get(i).height() - steps.get(i - 1).height()) <= 1, "step " + i);
        }
        assertTrue(steps.stream().anyMatch(s -> s.kind() == TerrainPad.StepKind.STAIR_UP));
        assertTrue(steps.stream().anyMatch(s -> s.kind() == TerrainPad.StepKind.SLAB_UP), "a longer climb has slabs in it");
        assertTrue(steps.stream().anyMatch(s -> s.kind() == TerrainPad.StepKind.STAIR_DOWN
                || s.kind() == TerrainPad.StepKind.SLAB_DOWN));

        List<TerrainPad.Step> flat = TerrainPad.roadProfile(new int[] {70, 70, 70});
        assertTrue(flat.stream().allMatch(s -> s.kind() == TerrainPad.StepKind.PATH));
        List<TerrainPad.Step> single = TerrainPad.roadProfile(new int[] {70, 70, 71, 71, 71});
        assertEquals(TerrainPad.StepKind.STAIR_UP, single.get(2).kind(), "one isolated step is a stair");
        assertTrue(TerrainPad.roadProfile(new int[0]).isEmpty());
    }
}
