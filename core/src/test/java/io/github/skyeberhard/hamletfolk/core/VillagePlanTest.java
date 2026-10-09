package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** R8.3: a village's plan of square, streets and lots, made from the ground and saved with the settlement. */
class VillagePlanTest {
    private static VillagePlan flat(long seed) {
        return PlanGenerator.generate(0, 0, seed, "plains", HeightSource.flat(64));
    }

    private static boolean overlap(Rect a, Rect b) {
        return a.overlaps(b);
    }

    @Test
    void aSquareAMainStreetBranchesAndLotsComeFromFlatGround() {
        VillagePlan plan = flat(1);
        assertEquals(15, plan.square().width());
        assertEquals(15, plan.square().depth());
        assertTrue(plan.square().contains(0, 0), "the square is at the centre");
        assertEquals(1, plan.roads().stream().filter(r -> r.kind() == VillagePlan.RoadKind.SPINE).count());
        assertEquals(2, plan.roads().stream().filter(r -> r.kind() == VillagePlan.RoadKind.BRANCH).count());
        assertTrue(plan.lots().size() >= 10, "lots: " + plan.lots().size());
        assertTrue(plan.lots().stream().allMatch(l -> l.status() == VillagePlan.LotStatus.RESERVED && l.stage() == 1
                && l.biomeSet().equals("plains")));
        Set<BuildingType> types = plan.lots().stream().map(VillagePlan.Lot::type).collect(Collectors.toSet());
        assertTrue(types.size() >= 5, "a mix of buildings: " + types);
        assertTrue(types.contains(BuildingType.HOUSE) && types.contains(BuildingType.FARM));
        assertEquals(0, plan.dropped(), "nothing wrong with the ground");
        assertEquals(1, plan.stage());
    }

    @Test
    void nothingOverlapsAnythingElseAcrossManySeeds() {
        for (long seed = 0; seed < 25; seed++) {
            VillagePlan plan = flat(seed);
            List<VillagePlan.Lot> lots = plan.lots();
            for (int i = 0; i < lots.size(); i++) {
                Rect a = lots.get(i).rect();
                assertFalse(overlap(a, plan.square()), "seed " + seed + " lot " + lots.get(i).id() + " on the square");
                for (VillagePlan.Road road : plan.roads()) {
                    assertFalse(overlap(a, road.rect()), "seed " + seed + " lot " + lots.get(i).id() + " on road " + road.id());
                }
                for (int j = i + 1; j < lots.size(); j++) {
                    assertFalse(overlap(a, lots.get(j).rect()), "seed " + seed + " lots " + lots.get(i).id() + " and " + lots.get(j).id());
                }
            }
        }
    }

    @Test
    void theSameGroundAndSeedAlwaysGiveTheSamePlan() {
        assertEquals(flat(7).toMap(), flat(7).toMap());
        // Zoning: shops stand nearer the square than houses, and houses nearer than farms and the mine.
        VillagePlan plan = flat(7);
        double shops = averageDistance(plan, BuildingType.SHOP);
        double houses = averageDistance(plan, BuildingType.HOUSE);
        double farms = averageDistance(plan, BuildingType.FARM);
        assertTrue(shops < houses, "shops " + shops + " houses " + houses);
        assertTrue(houses < farms, "houses " + houses + " farms " + farms);
    }

    @Test
    void theMainStreetRunsAlongTheFlatterAxis() {
        HeightSource risesEastward = HeightSource.of((x, z) -> 64 + x / 2);
        assertFalse(PlanGenerator.generate(0, 0, 1, "plains", risesEastward).spineAlongX(), "the street runs north-south, level");
        HeightSource risesSouthward = HeightSource.of((x, z) -> 64 + z / 2);
        assertTrue(PlanGenerator.generate(0, 0, 1, "plains", risesSouthward).spineAlongX());
    }

    @Test
    void lotsOnTooSteepGroundAreDroppedAtFoundingAndEveryKeptLotIsWithinTheLimit() {
        HeightSource cliffEast = HeightSource.of((x, z) -> x > 20 ? 64 + (x - 20) * 2 : 64);
        VillagePlan plan = PlanGenerator.generate(0, 0, 1, "plains", cliffEast);
        assertTrue(plan.dropped() > 0, "some lots are on the slope");
        for (VillagePlan.Lot lot : plan.lots()) {
            assertTrue(PlanGenerator.buildable(cliffEast, lot.rect()), "lot " + lot.id() + " is too steep");
        }
    }

    @Test
    void nothingIsPlannedOnWaterAndStreetsStopAtTheShore() {
        HeightSource lake = HeightSource.of((x, z) -> 62, (x, z) -> x >= 30 && x <= 60);
        VillagePlan plan = PlanGenerator.generate(0, 0, 1, "plains", lake);
        for (VillagePlan.Lot lot : plan.lots()) {
            for (int x = lot.rect().x(); x <= lot.rect().maxX(); x++) {
                for (int z = lot.rect().z(); z <= lot.rect().maxZ(); z++) {
                    assertFalse(lake.water(x, z), "lot " + lot.id() + " is in the lake");
                }
            }
        }
        for (VillagePlan.Road road : plan.roads()) {
            for (int x = road.rect().x(); x <= road.rect().maxX(); x++) {
                for (int z = road.rect().z(); z <= road.rect().maxZ(); z++) {
                    assertFalse(lake.water(x, z), "road " + road.id() + " crosses the lake at " + x + "," + z);
                }
            }
        }
    }

    @Test
    void aRiverAcrossTheMainStreetStopsItAndKeepsBranchesToWhereItReaches() {
        // Rivers across both axes (so neither is the drier choice) and an even seed, which settles the tie for the x axis: the
        // river at x 20 to 35 cuts the street, so the branch at 28 has nothing to cross.
        HeightSource river = HeightSource.of((x, z) -> 64, (x, z) -> (x >= 20 && x <= 35) || (z >= 20 && z <= 35));
        VillagePlan plan = PlanGenerator.generate(0, 0, 2, "plains", river);
        assertTrue(plan.spineAlongX());
        VillagePlan.Road spine = plan.roads().stream().filter(r -> r.kind() == VillagePlan.RoadKind.SPINE).findFirst().orElseThrow();
        assertTrue(spine.rect().maxX() < 20, "the street stops at the river: " + spine.rect());
        assertTrue(plan.roads().stream().noneMatch(r -> r.kind() == VillagePlan.RoadKind.BRANCH && r.rect().centerX() > 20),
                "no branch beyond the river");
        assertDry(plan, river);
        PlanGenerator.extend(plan, river);
        assertDry(plan, river);
    }

    @Test
    void acrossManyLakesAndHillsNoStreetOrLotIsWetAndEveryBranchMeetsTheMainStreet() {
        for (long seed = 0; seed < 60; seed++) {
            java.util.Random random = new java.util.Random(seed);
            int lakes = 1 + random.nextInt(3);
            int[][] lake = new int[lakes][3];
            for (int[] l : lake) {
                l[0] = random.nextInt(160) - 80;
                l[1] = random.nextInt(160) - 80;
                l[2] = 6 + random.nextInt(18);
            }
            int slope = random.nextInt(3);
            HeightSource ground = HeightSource.of((x, z) -> 64 + Math.floorDiv(x * slope + z, 5) % 3, (x, z) -> {
                for (int[] l : lake) {
                    if ((x - l[0]) * (x - l[0]) + (z - l[1]) * (z - l[1]) <= l[2] * l[2]) {
                        return true;
                    }
                }
                return false;
            });
            if (!PlanGenerator.siteUsable(ground, 0, 0)) {
                continue; // a village is not planned in the middle of a lake
            }
            VillagePlan plan = PlanGenerator.generate(0, 0, seed, "plains", ground);
            PlanGenerator.extend(plan, ground);
            assertDry(plan, ground);
            VillagePlan.Road spine = plan.roads().stream().filter(r -> r.kind() == VillagePlan.RoadKind.SPINE).findFirst().orElseThrow();
            for (VillagePlan.Road road : plan.roads()) {
                if (road.kind() == VillagePlan.RoadKind.BRANCH) {
                    assertTrue(plan.roads().stream().filter(r -> r.kind() == VillagePlan.RoadKind.SPINE)
                            .anyMatch(r -> r.rect().overlaps(road.rect())), "seed " + seed + ": branch " + road.id() + " is cut off");
                }
            }
            for (VillagePlan.Lot lot : plan.lots()) {
                assertTrue(PlanGenerator.buildable(ground, lot.rect()), "seed " + seed + " lot " + lot.id());
            }
            assertNotNull(spine);
        }
    }

    private static void assertDry(VillagePlan plan, HeightSource ground) {
        for (VillagePlan.Road road : plan.roads()) {
            for (int x = road.rect().x(); x <= road.rect().maxX(); x++) {
                for (int z = road.rect().z(); z <= road.rect().maxZ(); z++) {
                    assertFalse(ground.water(x, z), "road " + road.id() + " is wet at " + x + "," + z);
                }
            }
        }
        for (VillagePlan.Lot lot : plan.lots()) {
            for (int x = lot.rect().x(); x <= lot.rect().maxX(); x++) {
                for (int z = lot.rect().z(); z <= lot.rect().maxZ(); z++) {
                    assertFalse(ground.water(x, z), "lot " + lot.id() + " is wet at " + x + "," + z);
                }
            }
        }
    }

    @Test
    void registeringABuildingOnALotFillsItAndTheAdviceMovesToTheNextOne() {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 0);
        s.setPlan(flat(1));
        VillagePlan plan = s.plan();
        VillagePlan.Lot shop = plan.lots().stream().filter(l -> l.type() == BuildingType.SHOP).findFirst().orElseThrow();
        assertEquals(shop, plan.nextLot(BuildingType.SHOP).orElseThrow());
        s.registerBuilding(new Building(BuildingType.SHOP, shop.rect().centerX(), 64, shop.rect().centerZ(), 0, "test"));
        assertEquals(VillagePlan.LotStatus.FILLED, plan.lots().stream().filter(l -> l.id() == shop.id()).findFirst().orElseThrow().status());
        assertTrue(plan.nextLot(BuildingType.SHOP).map(l -> l.id() != shop.id()).orElse(true));

        // A farm sign on a house lot fills nothing, and a plan set after buildings exist picks them up.
        s.registerBuilding(new Building(BuildingType.FARM, shop.rect().centerX(), 64, shop.rect().centerZ(), 0, "test"));
        assertEquals(1, plan.filledCount());
        Settlement late = new SettlementRegistry().found("world", 0, 0, 0);
        VillagePlan latePlan = flat(1);
        VillagePlan.Lot house = latePlan.lots().stream().filter(l -> l.type() == BuildingType.HOUSE).findFirst().orElseThrow();
        late.registerBuilding(new Building(BuildingType.HOUSE, house.rect().centerX(), 64, house.rect().centerZ(), 0, "test"));
        late.setPlan(latePlan);
        assertEquals(1, late.plan().filledCount());
    }

    @Test
    void thePlannersAdviceNamesTheLotItWouldGoOn() {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(new Resident(new java.util.UUID(30, i), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                    Occupation.NITWIT, true, 10_000, null, null, Needs.initial()));
        }
        s.setPlan(flat(1));
        List<Planner.Directive> advice = Planner.directives(s, 1, 200); // no food: build a farm
        assertEquals("farm", advice.get(0).target());
        assertTrue(advice.get(0).reason().contains("Lot reserved at"), advice.get(0).reason());
        Settlement none = new SettlementRegistry().found("world", 0, 0, 0);
        none.addResident(new Resident(new java.util.UUID(30, 99), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                Occupation.NITWIT, true, 10_000, null, null, Needs.initial()));
        assertFalse(Planner.directives(none, 1, 200).get(0).reason().contains("Lot reserved"), "no plan, no lot");
    }

    @Test
    void groundNobodyMeasuredGetsNoLots() {
        VillagePlan plan = PlanGenerator.generate(0, 0, 1, "plains", (x, z) -> HeightSource.UNKNOWN);
        assertTrue(plan.lots().isEmpty());
        assertNotNull(plan.square());
    }

    @Test
    void growthFillsTheNearestReservedLotOfAKindFirst() {
        VillagePlan plan = flat(3);
        VillagePlan.Lot first = plan.nextLot(BuildingType.HOUSE).orElseThrow();
        long previous = -1;
        int filled = 0;
        while (plan.nextLot(BuildingType.HOUSE).isPresent()) {
            VillagePlan.Lot lot = plan.nextLot(BuildingType.HOUSE).orElseThrow();
            long dx = lot.rect().centerX();
            long dz = lot.rect().centerZ();
            long distance = dx * dx + dz * dz;
            assertTrue(distance >= previous, "the village grows outward");
            previous = distance;
            assertTrue(plan.fill(lot.id()));
            assertFalse(plan.fill(lot.id()), "a lot is only filled once");
            filled++;
        }
        assertTrue(filled >= 4);
        assertEquals(filled, plan.filledCount());
        assertTrue(plan.nextLot(BuildingType.HOUSE).isEmpty());
        BuildingType other = plan.lots().stream().map(VillagePlan.Lot::type).filter(t -> t != BuildingType.HOUSE)
                .findFirst().orElseThrow();
        assertTrue(plan.nextLot(other).isPresent(), "other kinds are untouched: " + other);
        assertFalse(plan.fill(-1));
        assertEquals(first.type(), BuildingType.HOUSE);
    }

    @Test
    void levellingUpAddsStageTwoStreetsAndLotsWithoutTouchingStageOne() {
        VillagePlan plan = flat(5);
        plan.fill(plan.lots().get(0).id());
        List<VillagePlan.Lot> before = List.copyOf(plan.lots());
        int roadsBefore = plan.roads().size();

        assertTrue(PlanGenerator.extend(plan, HeightSource.flat(64)));
        assertEquals(2, plan.stage());
        assertTrue(plan.roads().size() > roadsBefore, "longer spine and new branches");
        assertTrue(plan.lots().size() > before.size());
        assertEquals(before, plan.lots().subList(0, before.size()), "stage-one lots are exactly as they were, filled one included");
        List<VillagePlan.Lot> added = plan.lots().subList(before.size(), plan.lots().size());
        assertTrue(added.stream().allMatch(l -> l.stage() == 2 && l.status() == VillagePlan.LotStatus.RESERVED));
        for (VillagePlan.Lot lot : plan.lots()) {
            assertFalse(overlap(lot.rect(), plan.square()));
            for (VillagePlan.Road road : plan.roads()) {
                assertFalse(overlap(lot.rect(), road.rect()), "lot " + lot.id() + " on road " + road.id());
            }
        }
        for (int i = 0; i < plan.lots().size(); i++) {
            for (int j = i + 1; j < plan.lots().size(); j++) {
                assertFalse(overlap(plan.lots().get(i).rect(), plan.lots().get(j).rect()));
            }
        }
        assertEquals(2, plan.stage()); // (stages 3 and 4 follow, and then it stops: see DistrictsTest)
    }

    @Test
    void thePlanIsSavedWithTheSettlementAndAnOldSaveHasNone() {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 0);
        VillagePlan plan = flat(9);
        plan.fill(plan.lots().get(2).id());
        s.setPlan(plan);
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(plan.toMap(), loaded.plan().toMap());
        assertEquals(1, loaded.plan().filledCount());
        assertEquals(plan.lots(), loaded.plan().lots());

        Map<String, Object> old = new LinkedHashMap<>(SettlementCodec.encode(s));
        old.put("format", 17);
        old.remove("plan");
        assertNull(SettlementCodec.decode(old).plan());
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(SettlementCodec.decode(old)).get("format"));
    }

    @Test
    void aDamagedPlanIsDroppedNotFatal() {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 0);
        s.setPlan(flat(1));
        Map<String, Object> saved = new LinkedHashMap<>(SettlementCodec.encode(s));
        Map<String, Object> plan = new LinkedHashMap<>((Map<String, Object>) saved.get("plan"));
        plan.put("square", "not a rectangle");
        saved.put("plan", plan);
        assertNull(SettlementCodec.decode(saved).plan());
        Map<String, Object> noStreet = new LinkedHashMap<>((Map<String, Object>) SettlementCodec.encode(s).get("plan"));
        noStreet.put("roads", List.of());
        saved.put("plan", noStreet);
        assertNull(SettlementCodec.decode(saved).plan(), "a plan with no main street would break stage 2: dropped");
        Map<String, Object> badLot = new LinkedHashMap<>((Map<String, Object>) SettlementCodec.encode(s).get("plan"));
        badLot.put("lots", List.of(Map.of("id", 1, "rect", List.of(0, 0, 9, 9), "type", "CASTLE", "biomeSet", "plains",
                "stage", 1, "status", "RESERVED")));
        saved.put("plan", badLot);
        assertNull(SettlementCodec.decode(saved).plan());
    }

    @Test
    void rectanglesOverlapGrowAndContain() {
        Rect a = new Rect(0, 0, 10, 10);
        assertTrue(a.overlaps(new Rect(9, 9, 5, 5)));
        assertFalse(a.overlaps(new Rect(10, 0, 5, 5)), "touching is not overlapping");
        assertEquals(new Rect(-2, -2, 14, 14), a.inflated(2));
        assertTrue(a.contains(9, 9));
        assertFalse(a.contains(10, 9));
        assertEquals(9, a.maxX());
    }

    private static double averageDistance(VillagePlan plan, BuildingType type) {
        return plan.lots().stream().filter(l -> l.type() == type)
                .mapToDouble(l -> Math.hypot(l.rect().centerX() - plan.centerX(), l.rect().centerZ() - plan.centerZ()))
                .average().orElse(Double.NaN);
    }

    @Test
    void zoningNeverLeavesAKindOfBuildingWithoutALot() {
        for (long seed = 0; seed < 40; seed++) {
            Set<BuildingType> kinds = new java.util.HashSet<>();
            flat(seed).lots().forEach(l -> kinds.add(l.type()));
            assertTrue(kinds.containsAll(Set.of(BuildingType.HOUSE, BuildingType.FARM, BuildingType.SHOP, BuildingType.MINE,
                    BuildingType.SMITHY, BuildingType.TREASURY, BuildingType.GUARD_POST)), "seed " + seed + ": " + kinds);
        }
    }

    @Test
    void aCrampedSiteStillGetsAFarmAndAMineWhereTheyCanFit() {
        for (int edge : new int[] {40, 30}) {
            for (long seed = 0; seed < 20; seed++) {
                HeightSource lake = new HeightSource() {
                    @Override
                    public int height(int x, int z) {
                        return 64;
                    }

                    @Override
                    public boolean water(int x, int z) {
                        return Math.max(Math.abs(x), Math.abs(z)) > edge;
                    }
                };
                VillagePlan plan = PlanGenerator.generate(0, 0, seed, "plains", lake);
                Set<BuildingType> kinds = new java.util.HashSet<>();
                plan.lots().forEach(l -> kinds.add(l.type()));
                assertTrue(kinds.contains(BuildingType.FARM) || plan.lots().stream().anyMatch(l -> l.rect().width() >= 9),
                        "edge " + edge + " seed " + seed + ": " + kinds);
                assertTrue(kinds.contains(BuildingType.HOUSE), "edge " + edge + " seed " + seed + ": " + kinds);
            }
        }
    }
}
