package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R8.14: a plan that grows to four stages, and the districts they make. */
class DistrictsTest {
    private static VillagePlan grown(long seed, int stages) {
        VillagePlan plan = PlanGenerator.generate(0, 0, seed, "plains", HeightSource.flat(64));
        for (int stage = 2; stage <= stages; stage++) {
            assertTrue(PlanGenerator.extend(plan, HeightSource.flat(64)), "stage " + stage);
        }
        return plan;
    }

    // ----- growth -----

    @Test
    void aVillageHasTheStageItsSizeCallsFor() {
        assertEquals(1, PlanGenerator.stageFor(0));
        assertEquals(1, PlanGenerator.stageFor(24));
        assertEquals(2, PlanGenerator.stageFor(25));
        assertEquals(2, PlanGenerator.stageFor(49));
        assertEquals(3, PlanGenerator.stageFor(50));
        assertEquals(3, PlanGenerator.stageFor(99));
        assertEquals(4, PlanGenerator.stageFor(100));
        assertEquals(4, PlanGenerator.stageFor(5000));
        assertEquals(112, PlanGenerator.readRadius(2), "what stage two always needed");
        assertTrue(PlanGenerator.readRadius(1) >= 96, "the axis choice samples 96 blocks each way");
        assertTrue(PlanGenerator.readRadius(3) > PlanGenerator.readRadius(2) && PlanGenerator.readRadius(4) > PlanGenerator.readRadius(3));
    }

    @Test
    void thePlanGrowsToFourStagesAndNoFurther() {
        VillagePlan plan = grown(7, 4);
        assertEquals(4, plan.stage());
        assertFalse(PlanGenerator.extend(plan, HeightSource.flat(64)), "four is the last");
        assertEquals(4, plan.stage());
        for (int stage = 1; stage <= 4; stage++) {
            final int s = stage;
            assertTrue(plan.roads().stream().anyMatch(r -> r.stage() == s), "stage " + s + " added streets");
            assertTrue(plan.lots().stream().anyMatch(l -> l.stage() == s), "stage " + s + " added lots");
        }
    }

    @Test
    void eachStageReachesFurtherOutAndLeavesTheEarlierOnesAsTheyWere() {
        VillagePlan plan = PlanGenerator.generate(0, 0, 7, "plains", HeightSource.flat(64));
        plan.fill(plan.lots().get(0).id());
        List<VillagePlan.Lot> lots = List.copyOf(plan.lots());
        Rect reach = Works.extent(plan, 1).orElseThrow();
        for (int stage = 2; stage <= 4; stage++) {
            assertTrue(PlanGenerator.extend(plan, HeightSource.flat(64)));
            Rect wider = Works.extent(plan, stage).orElseThrow();
            assertTrue(wider.width() * wider.depth() > reach.width() * reach.depth(), "stage " + stage + " is larger");
            assertTrue(wider.contains(reach.x(), reach.z()) && wider.contains(reach.maxX(), reach.maxZ()), "and holds the one inside it");
            assertEquals(lots, plan.lots().subList(0, lots.size()), "earlier lots are exactly as they were");
            reach = wider;
            lots = List.copyOf(plan.lots());
        }
        assertEquals(reach, Works.extent(plan, 4).orElseThrow());
    }

    @Test
    void noLotOverlapsAStreetTheSquareOrAnotherLotAtStageFour() {
        for (long seed = 0; seed < 30; seed++) {
            VillagePlan plan = grown(seed, 4);
            for (int i = 0; i < plan.lots().size(); i++) {
                Rect a = plan.lots().get(i).rect();
                assertFalse(a.overlaps(plan.square()), "seed " + seed + " lot on the square");
                for (VillagePlan.Road road : plan.roads()) {
                    assertFalse(a.overlaps(road.rect()), "seed " + seed + " lot " + plan.lots().get(i).id() + " on road " + road.id());
                }
                for (int j = i + 1; j < plan.lots().size(); j++) {
                    assertFalse(a.overlaps(plan.lots().get(j).rect()), "seed " + seed + " lots " + i + " and " + j);
                }
            }
        }
    }

    @Test
    void theSpineIsOneUnbrokenStreetAndEveryBranchMeetsIt() {
        VillagePlan plan = grown(11, 4);
        List<VillagePlan.Road> spine = plan.roads().stream().filter(r -> r.kind() == VillagePlan.RoadKind.SPINE).toList();
        assertTrue(spine.size() >= 3, "the stage-one street and its extensions");
        for (VillagePlan.Road road : plan.roads()) {
            if (road.kind() == VillagePlan.RoadKind.BRANCH) {
                assertTrue(spine.stream().anyMatch(s -> s.rect().overlaps(road.rect())), "branch " + road.id() + " is cut off");
            }
        }
        // the extensions meet end to end, each further out than the last, none laid over another
        List<Rect> pieces = spine.stream().map(VillagePlan.Road::rect).toList();
        for (int i = 0; i < pieces.size(); i++) {
            for (int j = i + 1; j < pieces.size(); j++) {
                assertFalse(pieces.get(i).overlaps(pieces.get(j)), "spine pieces " + pieces.get(i) + " and " + pieces.get(j) + " overlap");
            }
        }
        for (Rect piece : pieces) {
            assertTrue(pieces.stream().anyMatch(other -> other != piece && (other.overlaps(piece) || touches(other, piece))),
                    "spine piece " + piece + " meets another");
        }
    }

    private static boolean touches(Rect a, Rect b) {
        return new Rect(a.x() - 1, a.z() - 1, a.width() + 2, a.depth() + 2).overlaps(b);
    }

    @Test
    void sameGroundAndSeedGiveTheSamePlanAtEveryStage() {
        assertEquals(grown(5, 4).lots(), grown(5, 4).lots());
        assertEquals(grown(5, 4).roads(), grown(5, 4).roads());
    }

    @Test
    void lakesAndRiversStopTheLaterStagesToo() {
        for (long seed = 0; seed < 40; seed++) {
            java.util.Random random = new java.util.Random(seed);
            int cx = 40 + random.nextInt(120);
            int cz = -200 + random.nextInt(400);
            int r = 8 + random.nextInt(25);
            HeightSource ground = HeightSource.of((x, z) -> 64 + (Math.abs(x) + Math.abs(z)) % 3,
                    (x, z) -> (x - cx) * (x - cx) + (z - cz) * (z - cz) < r * r || (x >= 90 && x <= 96));
            VillagePlan plan = PlanGenerator.generate(0, 0, seed, "plains", ground);
            while (PlanGenerator.extend(plan, ground)) {
                // grow all the way
            }
            assertEquals(4, plan.stage());
            for (VillagePlan.Lot lot : plan.lots()) {
                for (int x = lot.rect().x(); x <= lot.rect().maxX(); x++) {
                    for (int z = lot.rect().z(); z <= lot.rect().maxZ(); z++) {
                        assertFalse(ground.water(x, z), "seed " + seed + " lot " + lot.id() + " is wet at " + x + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void aPlanAtStageFourIsSavedWithTheSettlement() {
        Settlement s = new Settlement(UUID.randomUUID(), "Bigtown", "world", 0, 0, 0);
        s.setPlan(grown(3, 4));
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(4, loaded.plan().stage());
        assertEquals(s.plan().lots(), loaded.plan().lots());
        assertEquals(Districts.of(s.plan()), Districts.of(loaded.plan()));
    }

    @Test
    void aPlanThatHasGrownIsNotMistakenForADamagedOneWhenItIsLoaded() {
        for (int stages = 2; stages <= 4; stages++) {
            VillagePlan plan = grown(7, stages);
            VillagePlan loaded = VillagePlan.fromMap(plan.toMap());
            assertEquals(stages, loaded.stage());
            assertEquals(plan.lots(), loaded.lots());
            assertEquals(plan.roads(), loaded.roads());
        }
        VillagePlan none = new VillagePlan(1, "plains", 0, 0, true, 1);
        none.setSquare(new Rect(-7, -7, 15, 15));
        try {
            VillagePlan.fromMap(none.toMap());
            org.junit.jupiter.api.Assertions.fail("a plan with no main street is damaged");
        } catch (IllegalArgumentException expected) {
            // dropped by the caller
        }
    }

    @Test
    void aShortExtensionOfTheMainStreetStillRunsTheSameWay() {
        VillagePlan plan = new VillagePlan(1, "plains", 0, 0, true, 2);
        plan.setSquare(new Rect(-7, -7, 15, 15));
        VillagePlan.Road long1 = new VillagePlan.Road(plan.newId(), VillagePlan.RoadKind.SPINE, new Rect(-40, -2, 81, 5), 1);
        VillagePlan.Road stub = new VillagePlan.Road(plan.newId(), VillagePlan.RoadKind.SPINE, new Rect(41, -2, 3, 5), 2); // taller than long
        VillagePlan.Road branch = new VillagePlan.Road(plan.newId(), VillagePlan.RoadKind.BRANCH, new Rect(27, -4, 3, 9), 1);
        plan.addRoad(long1);
        plan.addRoad(stub);
        plan.addRoad(branch);
        assertTrue(plan.runsAlongX(long1) && plan.runsAlongX(stub), "the street, whatever the piece's shape");
        assertFalse(plan.runsAlongX(branch));
        VillagePlan across = new VillagePlan(1, "plains", 0, 0, false, 1);
        VillagePlan.Road north = new VillagePlan.Road(1, VillagePlan.RoadKind.SPINE, new Rect(-2, 41, 5, 3), 2);
        assertFalse(across.runsAlongX(north));
        // the lights along the stub are on the street's line, not along the stub's short side
        assertTrue(Works.lights(plan, 2).stream().noneMatch(spot -> spot.x() >= 41 && spot.x() <= 43), "the stub is three long: no light on it");
    }

    @Test
    void aWallRingGrowsWithEachStage() {
        VillagePlan plan = grown(7, 4);
        Rect previous = Works.ring(plan, 1).orElseThrow().rect();
        for (int stage = 2; stage <= 4; stage++) {
            Rect ring = Works.ring(plan, stage).orElseThrow().rect();
            assertTrue(ring.width() * ring.depth() > previous.width() * previous.depth(), "the stage " + stage + " wall is further out");
            previous = ring;
        }
    }

    // ----- names -----

    @Test
    void theFirstTwoDistrictsAreTheOldTownAndTheNewQuarter() {
        List<Districts.District> districts = Districts.of(grown(7, 4));
        assertEquals(4, districts.size());
        assertEquals("The old town", districts.get(0).name());
        assertEquals("The new quarter", districts.get(1).name());
        assertEquals(List.of(1, 2, 3, 4), districts.stream().map(Districts.District::stage).toList());
        assertEquals(grown(7, 4).lots().size(), districts.stream().mapToInt(Districts.District::lots).sum());
    }

    @Test
    void laterDistrictsAreNamedForWhatTheyHold() {
        Map<BuildingType, Integer> held = new EnumMap<>(BuildingType.class);
        held.put(BuildingType.HOUSE, 9);
        held.put(BuildingType.FARM, 3);
        held.put(BuildingType.SHOP, 1);
        assertEquals("The farm quarter", Districts.nameFor(3, held), "houses do not name a district while something else is there");
        held.put(BuildingType.SHOP, 3);
        assertEquals("The farm quarter", Districts.nameFor(3, held), "a tie goes to the earlier kind");
        held.put(BuildingType.SHOP, 4);
        assertEquals("The market quarter", Districts.nameFor(4, held));
        Map<BuildingType, Integer> mines = new EnumMap<>(BuildingType.class);
        mines.put(BuildingType.MINE, 2);
        assertEquals("The mining quarter", Districts.nameFor(3, mines));
        Map<BuildingType, Integer> smiths = new EnumMap<>(BuildingType.class);
        smiths.put(BuildingType.SMITHY, 1);
        assertEquals("The smiths' quarter", Districts.nameFor(3, smiths));
        Map<BuildingType, Integer> garrison = new EnumMap<>(BuildingType.class);
        garrison.put(BuildingType.GUARD_POST, 1);
        assertEquals("The garrison quarter", Districts.nameFor(4, garrison));
        Map<BuildingType, Integer> houses = new EnumMap<>(BuildingType.class);
        houses.put(BuildingType.HOUSE, 7);
        assertEquals("The housing quarter", Districts.nameFor(3, houses));
        assertEquals("The housing quarter", Districts.nameFor(3, Map.of()));
        assertEquals("The old town", Districts.nameFor(1, held), "the first two ignore what they hold");
        assertEquals("The new quarter", Districts.nameFor(2, held));
    }

    @Test
    void theLaterDistrictsOfARealPlanAreNamedDifferently() {
        for (long seed = 0; seed < 30; seed++) {
            List<Districts.District> districts = Districts.of(grown(seed, 4));
            assertEquals(4, districts.size());
            assertNotEquals(districts.get(2).name(), districts.get(3).name(), "seed " + seed);
            assertFalse(districts.get(3).name().contains("outer"), "seed " + seed + ": " + districts.get(3).name());
        }
    }

    @Test
    void aFarmAndAGranaryAreOneKindOfDistrict() {
        Map<BuildingType, Integer> held = new EnumMap<>(BuildingType.class);
        held.put(BuildingType.FARM, 1);
        held.put(BuildingType.GRANARY, 1);
        held.put(BuildingType.SHOP, 1);
        assertEquals("The farm quarter", Districts.nameFor(3, held), "two farm-kind lots beat one shop");
        Map<BuildingType, Integer> tie = new EnumMap<>(BuildingType.class);
        tie.put(BuildingType.MINE, 1);
        tie.put(BuildingType.SHOP, 1);
        assertEquals("The mining quarter", Districts.nameFor(3, tie), "a tie goes to the earlier kind");
        assertEquals("The market quarter", Districts.nameFor(4, tie, Set.of("The mining quarter")), "unless that name is taken");
    }

    @Test
    void twoDistrictsNeverShareAName() {
        VillagePlan plan = new VillagePlan(1, "plains", 0, 0, true, 4);
        plan.setSquare(new Rect(-7, -7, 15, 15));
        for (int stage = 1; stage <= 4; stage++) {
            plan.addRoad(new VillagePlan.Road(plan.newId(), VillagePlan.RoadKind.SPINE, new Rect(stage * 20, -2, 10, 5), stage));
            plan.addLot(new VillagePlan.Lot(plan.newId(), new Rect(stage * 20, 4, 9, 9), BuildingType.FARM, "plains", stage,
                    VillagePlan.LotStatus.RESERVED));
        }
        List<String> names = Districts.of(plan).stream().map(Districts.District::name).toList();
        assertEquals(List.of("The old town", "The new quarter", "The farm quarter", "The outer farm quarter"), names);
        assertEquals(4, new HashSet<>(names).size());
    }

    @Test
    void aStageThatAddedNothingMakesNoDistrict() {
        VillagePlan plan = new VillagePlan(1, "plains", 0, 0, true, 3);
        plan.setSquare(new Rect(-7, -7, 15, 15));
        plan.addRoad(new VillagePlan.Road(plan.newId(), VillagePlan.RoadKind.SPINE, new Rect(8, -2, 40, 5), 1));
        plan.addLot(new VillagePlan.Lot(plan.newId(), new Rect(20, 4, 9, 9), BuildingType.HOUSE, "plains", 3, VillagePlan.LotStatus.FILLED));
        List<Districts.District> districts = Districts.of(plan);
        assertEquals(List.of(1, 3), districts.stream().map(Districts.District::stage).toList());
        assertEquals(1, districts.get(1).built());
        assertTrue(Districts.of(null).isEmpty());
    }

    // ----- where you are, and the walls -----

    @Test
    void aSpotIsInTheInnermostDistrictThatTakesItIn() {
        VillagePlan plan = grown(7, 4);
        assertEquals("The old town", Districts.at(plan, 0, 0).orElseThrow().name());
        VillagePlan.Lot far = plan.lots().stream().filter(l -> l.stage() == 4).findFirst().orElseThrow();
        Districts.District there = Districts.at(plan, far.rect().centerX(), far.rect().centerZ()).orElseThrow();
        assertEquals(4, there.stage());
        VillagePlan.Lot second = plan.lots().stream().filter(l -> l.stage() == 2).findFirst().orElseThrow();
        assertEquals(2, Districts.at(plan, second.rect().centerX(), second.rect().centerZ()).orElseThrow().stage());
        assertEquals(Optional.empty(), Districts.at(plan, 5000, 5000));
        assertEquals(Optional.empty(), Districts.at(null, 0, 0));
    }

    @Test
    void aFenceForALaterStageStandsUntilTheNextRingGoesUp() {
        Settlement s = new Settlement(UUID.randomUUID(), "Fenceton", "world", 0, 0, 0);
        s.setPlan(grown(7, 3));
        ConstructionProject outer = new ConstructionProject(s.nextProjectId(), BuildingType.PALISADE, 2, 1, "plains", 0, 0, 0, -1, 2);
        s.addProject(outer);
        assertEquals(0, Districts.wall(s, 2), "not until finished");
        Construction.finish(s, outer, 3);
        assertEquals(1, Districts.wall(s, 2), "the stage two fence stands");
    }

    @Test
    void theWallRoundEachDistrictIsWhatWasBuiltForThatStage() {
        Settlement s = new Settlement(UUID.randomUUID(), "Wallton", "world", 0, 0, 0);
        s.setPlan(grown(7, 3));
        assertEquals(0, Districts.wall(s, 1));
        ConstructionProject fence = new ConstructionProject(s.nextProjectId(), BuildingType.PALISADE, 1, 0, "plains", 0, 0, 0, -1, 5);
        s.addProject(fence);
        assertEquals(0, Districts.wall(s, 1), "not until it is finished");
        Construction.finish(s, fence, 6);
        assertEquals(1, Districts.wall(s, 1));
        assertEquals(0, Districts.wall(s, 2), "the fence of stage one is not stage two's");
        ConstructionProject second = new ConstructionProject(s.nextProjectId(), BuildingType.PALISADE, 2, 1, "plains", 0, 0, 0, -1, 7);
        s.addProject(second);
        Construction.finish(s, second, 8);
        assertEquals(0, Districts.wall(s, 1), "the fence round the old town was taken down when the next one went up outside it");
        assertEquals(1, Districts.wall(s, 2));
        ConstructionProject stone = new ConstructionProject(s.nextProjectId(), BuildingType.RAMPART, 3, 2, "plains", 0, 0, 0, -1, 9);
        stone.setStage(1);
        s.addProject(stone);
        Construction.finish(s, stone, 10);
        assertEquals(3, Districts.wall(s, 1), "a stone rampart round the old town now, and it stays");
        assertEquals(0, Districts.wall(s, 3));
        List<String> lines = Districts.describe(s);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).startsWith("The old town (stage 1)") && lines.get(0).contains("a stone rampart round it"), lines.get(0));
        assertTrue(lines.get(1).contains("a fence round it"), lines.get(1)); // (the stage two fence built above)
        assertTrue(lines.get(2).contains("no wall round it"), lines.get(2));
        assertNotEquals(lines.get(0), lines.get(1));
        Set<String> distinct = new HashSet<>(lines);
        assertEquals(3, distinct.size());
    }
}
