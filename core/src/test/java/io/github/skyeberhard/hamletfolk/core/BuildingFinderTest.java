package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R2.8: the buildings a village already stands on are found without signs. */
class BuildingFinderTest {
    private static BuildingFinder.Site site(String kind, int x, int z) {
        return new BuildingFinder.Site(kind, x, 64, z);
    }

    private static List<BuildingFinder.Cell> field(int x, int z, int width, int depth) {
        List<BuildingFinder.Cell> cells = new ArrayList<>();
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                if (dx == width / 2 && dz == depth / 2) {
                    continue; // (the water in the middle of a field)
                }
                cells.add(new BuildingFinder.Cell(x + dx, 64, z + dz));
            }
        }
        return cells;
    }

    private static long count(List<BuildingFinder.Found> found, BuildingType type) {
        return found.stream().filter(f -> f.type() == type).count();
    }

    private Settlement village() {
        Settlement s = new Settlement(new UUID(70, 70), "Oldham", "world", 0, 0, 0);
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        return s;
    }

    @Test
    void bedsCloseTogetherAreOneHouseAndFarOnesAreAnother() {
        List<BuildingFinder.Found> found = BuildingFinder.find(List.of(site("home", 10, 10), site("home", 13, 12), site("home", 22, 11),
                site("home", 60, 60)), List.of());
        assertEquals(3, count(found, BuildingType.HOUSE), "two beds three blocks apart are one house; a bed twelve away and a far one are others");
        BuildingFinder.Found first = found.get(0);
        assertEquals(10, first.x(), "found at its first bed");
        assertTrue(first.footprint().contains(13, 12) && first.footprint().contains(10, 10));
    }

    /** The reviewer's case: a street of houses eight blocks apart must not be chained into one house that fills every lot. */
    @Test
    void aGridOfHousesIsManyHousesNotOne() {
        List<BuildingFinder.Site> beds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                beds.add(site("home", i * 8, j * 8));
            }
        }
        List<BuildingFinder.Found> found = BuildingFinder.find(beds, List.of());
        assertEquals(25, count(found, BuildingType.HOUSE));
        assertTrue(found.stream().allMatch(f -> f.footprint().width() <= 2 * BuildingFinder.FOOTPRINT_RADIUS + 2),
                "no footprint spans a street");
    }

    @Test
    void aVeryLargeFieldIsSeveralFarmsOfBoundedSize() {
        List<BuildingFinder.Found> found = BuildingFinder.find(List.of(), field(0, 0, 40, 40));
        assertTrue(count(found, BuildingType.FARM) >= 4, "a field forty blocks across is not one farm");
        assertTrue(found.stream().allMatch(f -> f.footprint().width() <= 2 * BuildingFinder.FIELD_SPAN + 3));
    }

    @Test
    void aBedOnAnotherFloorIsNotTheSameHouse() {
        List<BuildingFinder.Found> found = BuildingFinder.find(List.of(new BuildingFinder.Site("home", 10, 64, 10),
                new BuildingFinder.Site("home", 11, 90, 10)), List.of());
        assertEquals(2, count(found, BuildingType.HOUSE));
    }

    @Test
    void jobSitesBecomeTheirWorkBuildings() {
        List<BuildingFinder.Found> found = BuildingFinder.find(List.of(site("toolsmith", 0, 0), site("armorer", 40, 0),
                site("weaponsmith", 44, 3), site("librarian", 0, 40), site("farmer", 80, 80), site("fisherman", 90, 90)), List.of());
        assertEquals(1, count(found, BuildingType.SMITHY));
        assertEquals(1, count(found, BuildingType.ARMOURY), "an armorer and a weaponsmith next door share one armoury");
        assertEquals(1, count(found, BuildingType.LIBRARY));
        assertEquals(3, found.size(), "a farmer's composter and a fisherman's barrel are not read: fields are, and a barrel is storage");
    }

    @Test
    void aFieldOfFarmlandIsOneFarmAndAPatchIsNone() {
        List<BuildingFinder.Cell> cells = new ArrayList<>(field(0, 0, 9, 9));
        cells.addAll(field(40, 40, 5, 5));
        cells.add(new BuildingFinder.Cell(100, 64, 100));
        cells.add(new BuildingFinder.Cell(101, 64, 100));
        List<BuildingFinder.Found> found = BuildingFinder.find(List.of(), cells);
        assertEquals(2, count(found, BuildingType.FARM), "the water in the middle of a field does not split it; two cells are not a farm");
        BuildingFinder.Found big = found.get(0);
        assertEquals(4, big.x());
        assertEquals(4, big.z());
    }

    @Test
    void theSameReadingGivesTheSameBuildingsInTheSameOrder() {
        List<BuildingFinder.Site> sites = List.of(site("home", 30, 5), site("toolsmith", 0, 0), site("home", 3, 3));
        assertEquals(BuildingFinder.find(sites, List.of()), BuildingFinder.find(new ArrayList<>(sites.reversed()), List.of()));
    }

    // ----- registering what is found -----

    @Test
    void foundBuildingsAreRegisteredOnceAndGiveWorkAtOnce() {
        Settlement s = village();
        List<BuildingFinder.Found> found = BuildingFinder.find(List.of(site("toolsmith", 5, 5), site("home", 20, 20)), field(40, 40, 9, 9));
        s.syncFound(found, b -> true, 10);
        assertEquals(1, s.buildingCount(BuildingType.SMITHY));
        assertEquals(1, s.buildingCount(BuildingType.HOUSE));
        assertEquals(1, s.buildingCount(BuildingType.FARM));
        assertTrue(SettlementSimulator.placesFor(s, Occupation.FARMER) > 0, "a vanilla farm has farm places");
        assertTrue(SettlementSimulator.placesFor(s, Occupation.TOOLSMITH) > 0);
        assertTrue(s.buildings().stream().allMatch(b -> b.registeredBy().equals(BuildingFinder.FOUND_BY)));
        long lines = s.history().stream().filter(e -> e.text().contains("took stock")).count();
        assertEquals(1, lines);
        s.syncFound(found, b -> true, 11);
        assertEquals(3, s.buildings().size(), "reading it again changes nothing");
        assertEquals(lines, s.history().stream().filter(e -> e.text().contains("took stock")).count(), "and says nothing");
    }

    @Test
    void aBuildingAPlayerSignedIsNotFoundAgain() {
        Settlement s = village();
        s.registerBuilding(new Building(BuildingType.SMITHY, 8, 64, 8, 1, "Skye"));
        s.syncFound(BuildingFinder.find(List.of(site("toolsmith", 5, 5)), List.of()), b -> true, 10);
        assertEquals(1, s.buildingCount(BuildingType.SMITHY), "the sign's building stands for it");
        assertEquals("Skye", s.buildings().iterator().next().registeredBy());
    }

    @Test
    void aFoundBuildingThatIsGoneIsDroppedOnlyWhereItWasLookedFor() {
        Settlement s = village();
        s.syncFound(BuildingFinder.find(List.of(site("toolsmith", 5, 5)), List.of()), b -> true, 10);
        s.syncFound(List.of(), b -> false, 11); // its chunk is not loaded: nothing was read
        assertEquals(1, s.buildingCount(BuildingType.SMITHY));
        s.syncFound(List.of(), b -> true, 12);
        assertEquals(0, s.buildingCount(BuildingType.SMITHY));
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("no longer there")));
    }

    @Test
    void aSignedBuildingIsNeverDroppedBySyncing() {
        Settlement s = village();
        s.registerBuilding(new Building(BuildingType.SMITHY, 8, 64, 8, 1, "Skye"));
        s.syncFound(List.of(), b -> true, 10);
        assertEquals(1, s.buildingCount(BuildingType.SMITHY));
    }

    @Test
    void lotsOverFoundBuildingsAreFilledWhateverTheirKind() {
        Settlement s = village();
        VillagePlan.Lot lot = s.plan().lots().stream().filter(l -> l.type() == BuildingType.FARM).findFirst().orElseThrow();
        int x = lot.rect().centerX();
        int z = lot.rect().centerZ();
        s.syncFound(BuildingFinder.find(List.of(site("home", x, z)), List.of()), b -> true, 10);
        VillagePlan.Lot after = s.plan().lots().stream().filter(l -> l.id() == lot.id()).findFirst().orElseThrow();
        assertEquals(VillagePlan.LotStatus.FILLED, after.status(), "a farm lot with a house on it is not built on");
        assertFalse(s.plan().nextLot(BuildingType.FARM).map(l -> l.id() == lot.id()).orElse(false));
    }

    @Test
    void aPlanThatGrowsLaterKeepsOffTheFoundBuildings() {
        Settlement s = village();
        s.syncFound(BuildingFinder.find(List.of(site("home", 62, 4)), List.of()), b -> true, 10);
        assertTrue(PlanGenerator.extend(s.plan(), HeightSource.flat(64)));
        s.fillFoundLots();
        int r = BuildingFinder.FOOTPRINT_RADIUS;
        Rect over = new Rect(62 - r, 4 - r, 2 * r + 1, 2 * r + 1);
        assertTrue(s.plan().lots().stream().filter(l -> l.status() == VillagePlan.LotStatus.RESERVED)
                .noneMatch(l -> l.rect().overlaps(over)), "no reserved lot is left over the old house");
    }

    @Test
    void aBuildingSignedBesideAFoundOneTakesItsPlace() {
        Settlement s = village();
        s.syncFound(BuildingFinder.find(List.of(site("toolsmith", 5, 5)), List.of()), b -> true, 10);
        assertEquals(1, s.buildingCount(BuildingType.SMITHY));
        s.registerBuilding(new Building(BuildingType.SMITHY, 9, 64, 9, 11, "Skye"));
        assertEquals(1, s.buildingCount(BuildingType.SMITHY), "not counted twice");
        assertEquals("Skye", s.buildings().iterator().next().registeredBy());
        s.syncFound(BuildingFinder.find(List.of(site("toolsmith", 5, 5)), List.of()), b -> true, 20);
        assertEquals(1, s.buildingCount(BuildingType.SMITHY), "and finding it again does not bring the old one back");
    }

    @Test
    void manyHousesLeaveRoomInTheRegistryForOtherBuildings() {
        Settlement s = village();
        List<BuildingFinder.Site> sites = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            sites.add(site("home", i * 20, 0));
        }
        sites.add(site("toolsmith", 7, 500));
        s.syncFound(BuildingFinder.find(sites, List.of()), b -> true, 10);
        assertEquals(1, s.buildingCount(BuildingType.SMITHY), "the smithy is registered ahead of the houses");
        assertTrue(s.buildings().size() <= Settlement.MAX_BUILDINGS - Settlement.FOUND_RESERVE);
        assertEquals(Settlement.Registration.REGISTERED, s.registerBuilding(new Building(BuildingType.MINE, 1, 64, 1, 1, "Skye")));
    }

    @Test
    void aBellIsTheTownSquareTheVillageAlreadyHas() {
        Settlement s = village();
        s.syncFound(BuildingFinder.find(List.of(site("meeting", 2, 2)), List.of()), b -> true, 10);
        assertEquals(1, s.buildingCount(BuildingType.SQUARE));
        for (int i = 0; i < 6; i++) {
            s.addResident(new Resident(new UUID(75, i), "T", "P" + i, Gender.FEMALE, new Traits(50, 50, 50, 80), Occupation.FARMER, true,
                    0, null, null, Needs.initial()));
        }
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.WOOD, 300);
        s.ledger().add(ResourceType.STONE, 300);
        java.util.Optional<ConstructionProject> project = Construction.propose(s, 5, "plains", 200, new TemplateCatalog(),
                t -> java.util.Optional.empty(), (x, z) -> 64);
        assertTrue(project.isEmpty() || project.get().type() != BuildingType.SQUARE, "no square is built over the bell");
    }
}
