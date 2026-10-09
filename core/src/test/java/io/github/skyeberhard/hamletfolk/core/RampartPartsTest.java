package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** R5.11: an admin's own gatehouse and tower in place of the generated ones. */
class RampartPartsTest {
    private final VillagePlan plan = PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64));

    /** A gatehouse 3 wide and 5 deep, passage north to south, a stone marker block at its south (outside) end. */
    private static Blueprint gatehouse() {
        List<Blueprint.Block> blocks = new ArrayList<>();
        for (int z = 0; z < 5; z++) {
            blocks.add(new Blueprint.Block(0, 0, z, "STONE_BRICKS"));
            blocks.add(new Blueprint.Block(2, 0, z, "STONE_BRICKS"));
        }
        blocks.add(new Blueprint.Block(1, 1, 4, "GOLD_BLOCK")); // the marker, outside
        return new Blueprint("captured:gatehouse", 3, 2, 5, blocks);
    }

    /** A tower 3 by 3, a marker at its outer (north-west) corner, block 0, 0. */
    private static Blueprint tower() {
        List<Blueprint.Block> blocks = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                blocks.add(new Blueprint.Block(x, 0, z, "STONE_BRICKS"));
            }
        }
        blocks.add(new Blueprint.Block(0, 1, 0, "GOLD_BLOCK"));
        return new Blueprint("captured:tower", 3, 2, 3, blocks);
    }

    private static Blueprint.Block marker(Blueprint blueprint) {
        return blueprint.blocks().stream().filter(b -> b.material().equals("GOLD_BLOCK")).findFirst().orElseThrow();
    }

    private int gateRuns() {
        Works.Ring ring = Works.ring(plan, 1).orElseThrow();
        int n = ring.cells().size();
        int runs = 0;
        for (int i = 0; i < n; i++) {
            if (ring.gap()[i] && !ring.gap()[(i - 1 + n) % n]) {
                runs++;
            }
        }
        return runs;
    }

    @Test
    void thereIsOneGatehouseAtEveryGateTurnedSoItsOutsideFacesAwayFromTheVillage() {
        Rect rect = Works.ring(plan, 1).orElseThrow().rect();
        List<RampartParts.Placement> gates = RampartParts.gates(plan, 1, gatehouse());
        assertEquals(gateRuns(), gates.size());
        assertTrue(gates.size() >= 2);
        Set<String> sides = new java.util.HashSet<>();
        for (RampartParts.Placement gate : gates) {
            Blueprint b = gate.blueprint();
            Blueprint.Block m = marker(b);
            int mx = gate.originX() + m.x();
            int mz = gate.originZ() + m.z();
            if (gate.anchorZ() == rect.z()) {
                sides.add("north");
                assertEquals(rect.z() - (b.depth() - 1) / 2, mz, "north gate: the marker is at the far (north) edge");
                assertEquals(gate.originZ(), mz);
                assertEquals(2, gate.turns());
            } else if (gate.anchorZ() == rect.maxZ()) {
                sides.add("south");
                assertEquals(gate.originZ() + b.depth() - 1, mz, "south gate: the marker is at the south edge");
                assertEquals(0, gate.turns());
            } else if (gate.anchorX() == rect.x()) {
                sides.add("west");
                assertEquals(gate.originX(), mx, "west gate: the marker is at the west edge");
                assertEquals(1, gate.turns());
                assertEquals(5, b.width(), "turned a quarter: five wide now");
            } else {
                assertEquals(rect.maxX(), gate.anchorX());
                sides.add("east");
                assertEquals(gate.originX() + b.width() - 1, mx, "east gate: the marker is at the east edge");
                assertEquals(3, gate.turns());
            }
            assertTrue(gate.covers(gate.anchorX(), gate.anchorZ()), "centred on the gate");
            assertEquals(gate.anchorX() - gate.originX(), (b.width() - 1) / 2, "centred along the east-west axis");
            assertEquals(gate.anchorZ() - gate.originZ(), (b.depth() - 1) / 2, "centred along the north-south axis");
        }
        assertTrue(sides.size() >= 2, "gates on more than one side were checked: " + sides);
    }

    @Test
    void theTurnedPassageStillRunsAcrossTheWall() {
        Rect rect = Works.ring(plan, 1).orElseThrow().rect();
        for (RampartParts.Placement gate : RampartParts.gates(plan, 1, gatehouse())) {
            boolean northSouthWall = gate.anchorZ() == rect.z() || gate.anchorZ() == rect.maxZ();
            // the two stone sides are three apart as built; a quarter turn makes them five apart along x
            boolean alongX = gate.blueprint().width() == 3; // the two stone sides are 3 apart across x as built
            assertEquals(northSouthWall, alongX, "a north or south gate keeps the passage north to south; an east or west one turns it");
        }
    }

    @Test
    void aTowerSitsAtEachCornerWithItsOuterCornerOnTheRingsCorner() {
        Rect rect = Works.ring(plan, 1).orElseThrow().rect();
        List<RampartParts.Placement> towers = RampartParts.towers(plan, 1, tower());
        assertEquals(4, towers.size());
        Set<String> corners = new java.util.HashSet<>();
        for (RampartParts.Placement t : towers) {
            Blueprint.Block m = marker(t.blueprint());
            assertEquals(t.anchorX(), t.originX() + m.x(), "the marked corner is the ring's corner");
            assertEquals(t.anchorZ(), t.originZ() + m.z());
            assertTrue(t.originX() >= rect.x() && t.originX() + t.blueprint().width() - 1 <= rect.maxX(), "reaches inward, not out");
            assertTrue(t.originZ() >= rect.z() && t.originZ() + t.blueprint().depth() - 1 <= rect.maxZ());
            corners.add((t.anchorX() == rect.x() ? "w" : "e") + (t.anchorZ() == rect.z() ? "n" : "s"));
        }
        assertEquals(Set.of("wn", "en", "es", "ws"), corners);
    }

    @Test
    void nothingIsPlacedWithoutACaptureOrARing() {
        assertTrue(RampartParts.gates(plan, 1, null).isEmpty());
        assertTrue(RampartParts.towers(plan, 1, null).isEmpty());
        assertTrue(RampartParts.gates(null, 1, gatehouse()).isEmpty());
        assertTrue(Rampart.parts(plan, 1, null, null).isEmpty());
        assertEquals(Rampart.pieces(plan, 1, 2).size(), Rampart.pieces(plan, 1, 2, null, null).size(), "no captures, no change");
    }

    @Test
    void aCapturedGatehouseReplacesTheGeneratedPillarsAndLintelsAndCoveredWallColumns() {
        List<Rampart.Piece> generated = Rampart.pieces(plan, 1, 2);
        List<Rampart.Piece> own = Rampart.pieces(plan, 1, 2, gatehouse(), null);
        boolean generatedHasPillars = generated.stream().anyMatch(p -> p.blocks().stream().anyMatch(b -> b.material().equals("OAK_LOG")));
        assertTrue(generatedHasPillars);
        assertTrue(own.stream().noneMatch(p -> p.blocks().stream().anyMatch(b -> b.material().equals("OAK_LOG"))), "no generated pillars");
        assertTrue(own.stream().noneMatch(p -> p.blocks().stream().anyMatch(b -> b.dy() == 6 && b.material().equals("TORCH"))),
                "no generated pillar (the torch on top of each)");
        Set<Long> covered = RampartParts.covered(RampartParts.gates(plan, 1, gatehouse()));
        assertTrue(own.stream().noneMatch(p -> covered.contains(RampartParts.cell(p.x(), p.z()))), "no wall under the gatehouse");
        assertTrue(own.stream().anyMatch(p -> p.blocks().stream().anyMatch(b -> b.material().equals("LADDER[facing=south]")
                || b.material().equals("LADDER[facing=north]"))), "the generated towers are still there");
        assertTrue(own.stream().anyMatch(p -> p.blocks().size() == 3), "plain wall columns remain either side of a gatehouse");
    }

    @Test
    void aCapturedTowerReplacesTheGeneratedTowersAndTheWallColumnsItCovers() {
        List<Rampart.Piece> own = Rampart.pieces(plan, 1, 2, null, tower());
        assertTrue(own.stream().noneMatch(p -> p.blocks().stream().anyMatch(b -> b.material().startsWith("LADDER"))), "no generated tower");
        assertTrue(own.stream().anyMatch(p -> p.blocks().stream().anyMatch(b -> b.material().equals("OAK_LOG"))), "the pillars stay");
        Set<Long> covered = RampartParts.covered(RampartParts.towers(plan, 1, tower()));
        assertEquals(36, covered.size(), "four three-by-three footprints");
        assertTrue(own.stream().noneMatch(p -> covered.contains(RampartParts.cell(p.x(), p.z()))));
    }

    @Test
    void theCapturedPartsArePaidForAndTheGeneratedOnesTheyReplaceAreNot() {
        Map<ResourceType, Integer> generated = Rampart.price(plan, 1, 2);
        Map<ResourceType, Integer> both = Rampart.price(plan, 1, 2, gatehouse(), tower());
        assertNotEquals(generated, both);
        // worked out independently: the pieces left plus every block of every placement
        Map<ResourceType, Integer> halves = new EnumMap<>(ResourceType.class);
        for (Rampart.Piece piece : Rampart.pieces(plan, 1, 2, gatehouse(), tower())) {
            for (Rampart.Block block : piece.blocks()) {
                if (!block.ifEmpty()) {
                    Blueprint.halvesOf(block.material()).ifPresent(h -> halves.merge(h.type(), h.halves(), Integer::sum));
                }
            }
        }
        for (RampartParts.Placement part : Rampart.parts(plan, 1, gatehouse(), tower())) {
            part.blueprint().blocks().forEach(b -> Blueprint.halvesOf(b.material()).ifPresent(h -> halves.merge(h.type(), h.halves(), Integer::sum)));
        }
        Map<ResourceType, Integer> expected = new EnumMap<>(ResourceType.class);
        halves.forEach((type, h) -> expected.put(type, Math.max(1, (int) Math.ceil((long) h * Construction.costPercent() / 200.0))));
        assertEquals(expected, both);
        assertTrue(both.getOrDefault(ResourceType.STONE, 0) > 0, "the stone bricks of the parts are in the price");
    }

    @Test
    void theCatalogKeepsACapturedPartForItsExactTierAndStyleOnly() {
        TemplateCatalog catalog = new TemplateCatalog();
        assertTrue(catalog.part(BuildingType.GATEHOUSE, "plains", 2).isEmpty());
        catalog.capture(BuildingType.GATEHOUSE, "plains", 2, gatehouse());
        assertTrue(catalog.part(BuildingType.GATEHOUSE, "plains", 2).isPresent());
        assertTrue(catalog.part(BuildingType.GATEHOUSE, "plains", 3).isEmpty(), "tier 3 is generated unless captured too");
        assertTrue(catalog.part(BuildingType.GATEHOUSE, "desert", 2).isEmpty());
        assertTrue(catalog.part(BuildingType.TOWER, "plains", 2).isEmpty());
        assertTrue(catalog.part(BuildingType.HOUSE, "plains", 1).isEmpty(), "only parts");
        assertTrue(catalog.ladder(BuildingType.GATEHOUSE, "plains").isEmpty(), "a part has no ladder of its own");
        assertTrue(catalog.uncapture(BuildingType.GATEHOUSE, "plains", 2));
        assertTrue(catalog.part(BuildingType.GATEHOUSE, "plains", 2).isEmpty());
    }

    @Test
    void partsAreNamedForCaptureButNeverRegisteredByASign() {
        assertEquals(Optional.of(BuildingType.GATEHOUSE), BuildingType.fromCapture("gatehouse"));
        assertEquals(Optional.of(BuildingType.TOWER), BuildingType.fromCapture("Tower"));
        assertEquals(Optional.of(BuildingType.FARM), BuildingType.fromCapture("farm"));
        assertTrue(BuildingType.fromCapture("rampart").isEmpty());
        assertTrue(BuildingType.fromSign("[Gatehouse]").isEmpty(), "a sign cannot register a part");
        assertTrue(BuildingType.fromSign("[Tower]").isEmpty());
        assertTrue(BuildingType.GATEHOUSE.isPart() && BuildingType.TOWER.isPart());
        assertFalse(BuildingType.RAMPART.isPart());
        assertFalse(BuildingType.GATEHOUSE.isWorks());
    }
}
