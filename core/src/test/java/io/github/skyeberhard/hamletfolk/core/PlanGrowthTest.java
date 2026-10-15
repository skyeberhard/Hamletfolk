package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.38: a village that has filled its plan grows it; R4.39: a village with no stone can still build its first mine. */
class PlanGrowthTest {
    private final TemplateCatalog catalog = new TemplateCatalog();
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(40, next++), "Test", "Person", Gender.FEMALE, new Traits(50, 50, 50, 80), job, true,
                0, null, null, Needs.initial());
    }

    /** Six fed adults with a farm, a mine and tools on a flat plan, every bed taken. */
    private Settlement village() {
        Settlement s = new Settlement(new UUID(41, 41), "Roomham", "world", 0, 0, 0);
        for (int i = 0; i < 6; i++) {
            s.addResident(person(Occupation.FARMER));
        }
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.registerBuilding(new Building(BuildingType.FARM, 500, 64, 500, 0, "a player"));
        s.registerBuilding(new Building(BuildingType.MINE, 501, 64, 501, 0, "a player"));
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.WOOD, 200);
        s.ledger().add(ResourceType.STONE, 200);
        s.ledger().add(ResourceType.TOOLS, 50); // (so it wants no smithy: only wants are left, a shop and a house)
        s.housing().setChunk(0, 0, 6);
        return s;
    }

    /** Vanilla rungs have no blocks in core: a 5 by 5 floor stands in, planks at tier 1 and cobblestone above. */
    private Optional<Blueprint> blueprints(TemplateCatalog.Template t) {
        Optional<Blueprint> own = catalog.blueprint(t);
        if (own.isPresent()) {
            return own;
        }
        List<Blueprint.Block> floor = new ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                floor.add(new Blueprint.Block(x, 0, z, t.tier() == 1 ? "OAK_PLANKS" : "COBBLESTONE"));
            }
        }
        return Optional.of(new Blueprint(t.key(), 5, 1, 5, floor));
    }

    private Optional<ConstructionProject> propose(Settlement s, long day) {
        return Construction.propose(s, day, "plains", 200, catalog, this::blueprints, (x, z) -> 64);
    }

    private static void fillEveryLot(Settlement s) {
        for (VillagePlan.Lot lot : new ArrayList<>(s.plan().lots())) {
            s.plan().fill(lot.id());
        }
    }

    /** Builds the square the planner asks for first, so the next decision is about the buildings. */
    private void squareDone(Settlement s, long day) {
        ConstructionProject square = propose(s, day).orElseThrow();
        assertEquals(BuildingType.SQUARE, square.type());
        Construction.finish(s, square, day);
    }

    @Test
    void aVillageThatHasFilledItsPlanAsksForTheNextStageAndBuildsOnIt() {
        Settlement s = village();
        fillEveryLot(s);
        squareDone(s, 5);
        assertEquals(1, Construction.wantedStage(s, 6), "six people are a stage-one village");
        assertTrue(propose(s, 6).isEmpty(), "nowhere to build");
        assertEquals(2, Construction.wantedStage(s, 6), "so it asks for room");
        assertEquals(1, s.plan().stage(), "(the Paper layer lays the new stage out once the ground has loaded)");

        Set<Integer> before = new HashSet<>();
        s.plan().lots().forEach(l -> before.add(l.id()));
        assertTrue(PlanGenerator.extend(s.plan(), HeightSource.flat(64)));
        Construction.planGrew(s);
        assertEquals(2, s.plan().stage());
        assertEquals(1, Construction.wantedStage(s, 7), "answered: no further stage is asked for");

        ConstructionProject project = propose(s, 7).orElseThrow();
        VillagePlan.Lot lot = s.plan().lots().stream().filter(l -> l.id() == project.lotId()).findFirst().orElseThrow();
        assertFalse(before.contains(lot.id()), "on a lot of the new stage");
        assertEquals(2, lot.stage());
    }

    @Test
    void theCallForRoomLapsesAndNeverPassesTheLastStage() {
        Settlement s = village();
        s.conditions().put(Construction.OUTGROWN, 10L);
        assertEquals(2, Construction.wantedStage(s, 10 + Construction.OUTGROWN_DAYS));
        assertEquals(1, Construction.wantedStage(s, 11 + Construction.OUTGROWN_DAYS), "a call ten days old is forgotten");
        while (PlanGenerator.extend(s.plan(), HeightSource.flat(64))) {
            // grow to the last stage
        }
        s.conditions().put(Construction.OUTGROWN, 20L);
        assertEquals(PlanGenerator.MAX_STAGE, Construction.wantedStage(s, 20));
    }

    @Test
    void aVillageWithLotsLeftDoesNotAskForRoom() {
        Settlement s = village();
        squareDone(s, 5);
        propose(s, 6);
        assertEquals(1, Construction.wantedStage(s, 6));
    }

    @Test
    void atTheLastStageAWantWithNowhereToGoGetsGroundSetAside() {
        Settlement s = village();
        while (PlanGenerator.extend(s.plan(), HeightSource.flat(64))) {
            // grow to the last stage
        }
        fillEveryLot(s);
        squareDone(s, 5);
        assertTrue(Construction.needsWiderGround(s, 6, 200), "the Paper layer is told to load the search area");
        int lots = s.plan().lots().size();
        propose(s, 6);
        assertTrue(s.plan().lots().size() > lots, "ground is set aside for the house");
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("set aside ground")));
    }

    @Test
    void wantsDoNotSearchForGroundWhileThePlanCanStillGrow() {
        Settlement s = village();
        fillEveryLot(s);
        squareDone(s, 5);
        assertFalse(Construction.needsWiderGround(s, 6, 200));
        int lots = s.plan().lots().size();
        propose(s, 6);
        assertEquals(lots, s.plan().lots().size(), "the next stage is the room it gets");
    }

    @Test
    void lotsKeptForBuildingsTheVillageLacksDoNotCountAsRoom() {
        Settlement s = village();
        for (VillagePlan.Lot lot : new ArrayList<>(s.plan().lots())) {
            if (lot.type() != BuildingType.SMITHY) {
                s.plan().fill(lot.id()); // only the smithy's lot is left, and the village has no smithy
            }
        }
        assertTrue(s.plan().nextLot(BuildingType.SMITHY).isPresent());
        squareDone(s, 5);
        Optional<ConstructionProject> project = propose(s, 6);
        assertTrue(project.isEmpty(), "a house or shop does not take the smithy's lot");
        assertEquals(2, Construction.wantedStage(s, 6), "so the village asks for room");
    }

    @Test
    void theDefencesOfAStageGrownForRoomDoNotHoldUpItsHouses() {
        Settlement s = village();
        s.recordIncident(3);
        ConstructionProject lights = new ConstructionProject(1, BuildingType.STREET_LIGHTS, 1, 0, "plains", 0, 0, 0, -1, 2);
        lights.finish(3);
        s.addProject(lights);
        ConstructionProject fence = new ConstructionProject(2, BuildingType.PALISADE, 1, 0, "plains", 0, 0, 0, -1, 3);
        fence.finish(4);
        s.addProject(fence);
        squareDone(s, 5);
        assertTrue(PlanGenerator.extend(s.plan(), HeightSource.flat(64)), "grown for room, with six people");
        s.ledger().take(ResourceType.WOOD, s.ledger().get(ResourceType.WOOD) - 30); // not enough for the new stage's lights (32)
        propose(s, 6);
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("wants the street lights")), "the lights are saved for");
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("wants a shop")),
                "but the village's wants are still looked at, not held back behind them");
    }

    // ----- R4.39 -----

    /** Fed, with wood and a mine lot but not a block of stone, and no mine. */
    private Settlement stoneless() {
        Settlement s = new Settlement(new UUID(42, 42), "Bareham", "world", 0, 0, 0);
        for (int i = 0; i < 6; i++) {
            s.addResident(person(Occupation.FARMER));
        }
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.registerBuilding(new Building(BuildingType.FARM, 500, 64, 500, 0, "a player"));
        s.registerBuilding(new Building(BuildingType.SQUARE, 0, 64, 0, 0, "a player"));
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.WOOD, 300);
        s.housing().setChunk(0, 0, 20);
        return s;
    }

    private Optional<ConstructionProject> proposeStoneMine(Settlement s, long day) {
        // a mine design that is all cobblestone, so only stone could pay for it
        return Construction.propose(s, day, "plains", 200, catalog, t -> {
            if (t.type() != BuildingType.MINE) {
                return blueprints(t);
            }
            List<Blueprint.Block> floor = new ArrayList<>();
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    floor.add(new Blueprint.Block(x, 0, z, "COBBLESTONE"));
                }
            }
            return Optional.of(new Blueprint(t.key(), 5, 1, 5, floor));
        }, (x, z) -> 64);
    }

    @Test
    void aVillageWithNoStoneBuildsItsFirstMineAndPaysNoStoneForIt() {
        Settlement s = stoneless();
        assertEquals(0, s.ledger().get(ResourceType.STONE));
        ConstructionProject mine = proposeStoneMine(s, 6).orElseThrow();
        assertEquals(BuildingType.MINE, mine.type());
        assertTrue(Construction.charge(s, mine, "COBBLESTONE", 7), "the stone comes out of the ground as it is dug");
        assertEquals(0, s.ledger().get(ResourceType.STONE));
        int wood = s.ledger().get(ResourceType.WOOD);
        assertTrue(Construction.charge(s, mine, "OAK_PLANKS", 7));
        assertTrue(s.ledger().get(ResourceType.WOOD) < wood, "its timber is still paid for");
        assertTrue(s.history().stream().noneMatch(e -> e.text().contains("cannot afford")));
    }

    @Test
    void onlyTheFirstMineIsFree() {
        Settlement s = stoneless();
        s.registerBuilding(new Building(BuildingType.MINE, 501, 64, 501, 0, "a player"));
        ConstructionProject second = new ConstructionProject(1, BuildingType.MINE, 1, 0, "plains", 0, 64, 0, 1, 0);
        assertFalse(Construction.charge(s, second, "COBBLESTONE", 7), "a second mine pays for its stone");
        assertEquals(ResourceType.STONE, second.waitingFor());
        Settlement fresh = stoneless();
        ConstructionProject upgrade = new ConstructionProject(1, BuildingType.MINE, 2, 1, "plains", 0, 64, 0, 1, 0);
        assertFalse(Construction.charge(fresh, upgrade, "COBBLESTONE", 7), "and so does an upgrade");
        Settlement lost = stoneless();
        ConstructionProject built = new ConstructionProject(3, BuildingType.MINE, 1, 0, "plains", 0, 64, 0, 1, 0);
        built.finish(4);
        lost.addProject(built); // its mine stood once; its sign has since been broken
        ConstructionProject again = new ConstructionProject(4, BuildingType.MINE, 1, 0, "plains", 0, 64, 0, 2, 5);
        assertFalse(Construction.charge(lost, again, "COBBLESTONE", 7), "the next mine after the first pays for its stone");
        ConstructionProject house = new ConstructionProject(2, BuildingType.HOUSE, 1, 0, "plains", 0, 64, 0, 1, 0);
        assertFalse(Construction.charge(fresh, house, "COBBLESTONE", 7), "and every other building");
    }
}
