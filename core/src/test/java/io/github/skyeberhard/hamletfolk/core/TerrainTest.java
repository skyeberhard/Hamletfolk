package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** R8.27: the slope layer's data. */
class TerrainTest {
    @Test
    void flatGroundIsFlatAndAHillIsNot() {
        Terrain.Grid flat = Terrain.sample(HeightSource.flat(64), new Rect(0, 0, 40, 40), 4);
        assertEquals(10, flat.cols());
        assertTrue(Terrain.slopeRows(flat).stream().allMatch(row -> row.chars().allMatch(c -> c == '0')));
        // a ramp that rises one block a block: 4 a cell, which is gentle
        Terrain.Grid ramp = Terrain.sample((x, z) -> 64 + x, new Rect(0, 0, 40, 40), 4);
        assertEquals('1', Terrain.slopeRows(ramp).get(5).charAt(5));
        // a cliff: a drop of 20 between neighbouring samples
        Terrain.Grid cliff = Terrain.sample((x, z) -> x < 20 ? 90 : 70, new Rect(0, 0, 40, 40), 4);
        List<String> rows = Terrain.slopeRows(cliff);
        assertEquals('3', rows.get(5).charAt(4));
        assertEquals('3', rows.get(5).charAt(5));
        assertEquals('0', rows.get(5).charAt(0));
    }

    @Test
    void classesFollowTheChangeBetweenNeighbours() {
        assertEquals('0', Terrain.slopeClass(0));
        assertEquals('0', Terrain.slopeClass(2));
        assertEquals('1', Terrain.slopeClass(3));
        assertEquals('1', Terrain.slopeClass(4));
        assertEquals('2', Terrain.slopeClass(8));
        assertEquals('3', Terrain.slopeClass(9));
    }

    @Test
    void waterAndUnknownGroundAreMarkedAndDoNotMakeTheirNeighboursSteep() {
        HeightSource source = new HeightSource() {
            @Override
            public int height(int x, int z) {
                return x >= 20 ? UNKNOWN : 64;
            }

            @Override
            public boolean water(int x, int z) {
                return z < 8;
            }
        };
        List<String> rows = Terrain.slopeRows(Terrain.sample(source, new Rect(0, 0, 40, 16), 4));
        assertEquals('~', rows.get(0).charAt(0));
        assertEquals('.', rows.get(2).charAt(8));
        assertEquals('0', rows.get(2).charAt(0), "flat beside water and unmeasured ground");
    }

    @Test
    void anOlderSampleFillsWhatANewerOneCouldNotMeasure() {
        Terrain.Grid old = Terrain.sample(HeightSource.flat(70), new Rect(0, 0, 40, 40), 4);
        Terrain.Grid fresh = Terrain.sample((x, z) -> x < 20 ? 64 : HeightSource.UNKNOWN, new Rect(0, 0, 40, 40), 4);
        Terrain.Grid merged = Terrain.merge(old, fresh);
        assertEquals(64, merged.height(0, 0));
        assertEquals(70, merged.height(9, 0), "kept from the older sample");
        assertEquals(fresh, Terrain.merge(null, fresh));
        assertEquals(fresh, Terrain.merge(Terrain.sample(HeightSource.flat(70), new Rect(0, 0, 80, 40), 4), fresh), "a different grid is replaced, not mixed");
    }

    @Test
    void aBigAreaIsCappedAndCentred() {
        Terrain.Grid big = Terrain.sample(HeightSource.flat(64), new Rect(0, 0, 1000, 1000), 4);
        assertEquals(Terrain.MAX_CELLS, big.cols());
        assertEquals(Terrain.MAX_CELLS, big.rows());
        assertTrue(big.x0() > 0, "centred on the area");
    }

    @Test
    void theDataHasTheGridAndItsRange() {
        Map<String, Object> data = Terrain.asData(Terrain.sample((x, z) -> 60 + x / 4, new Rect(0, 0, 40, 40), 4));
        assertEquals(10, data.get("cols"));
        assertEquals(10, ((List<?>) data.get("slope")).size());
        assertEquals(60, data.get("low"));
        assertEquals(69, data.get("high"));
    }

    @Test
    void anUnmeasuredGridHasNoRange() {
        Map<String, Object> data = Terrain.asData(Terrain.sample((x, z) -> HeightSource.UNKNOWN, new Rect(0, 0, 40, 40), 4));
        assertNull(data.get("low"));
        assertNotNull(data.get("slope"));
    }

    @Test
    void theAreaOfAPlanCoversItsRoadsAndLotsWithAMargin() {
        Settlement s = new Settlement(new java.util.UUID(1, 1), "Planned", "world", 0, 64, 0);
        assertNull(Terrain.areaOf(s, 8), "no plan, no area");
    }

    @Test
    void aGridSurvivesTheFileAndADamagedOneIsRefused() {
        Terrain.Grid g = Terrain.sample((x, z) -> x < 20 ? 64 : HeightSource.UNKNOWN, new Rect(0, 0, 40, 40), 4);
        Terrain.Grid back = Terrain.fromFile(Terrain.toFile(g));
        assertEquals(Terrain.slopeRows(g), Terrain.slopeRows(back));
        assertEquals(0.5, Terrain.unknownShare(g), 0.01);
        assertNull(Terrain.fromFile(Map.of("cols", 3, "rows", 3, "heights", List.of(1, 2), "wet", "000")));
        assertNull(Terrain.fromFile(Map.of()));
    }
}
