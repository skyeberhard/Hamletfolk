package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** R4.7 and R4.8: what the village decides to build, the builder who takes the job, and the work block by block. */
class ConstructionTest {
    private static final UUID VILLAGE = new UUID(31, 31);
    private final TemplateCatalog catalog = new TemplateCatalog();
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(30, next++), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, 80), job, true,
                10_000, null, null, Needs.initial());
    }

    /** Six jobless adults on a flat plan; wanted levels are 60 food and 18 of the rest. */
    private Settlement village() {
        Settlement s = new Settlement(VILLAGE, "Buildham", "world", 0, 0, 0);
        for (int i = 0; i < 6; i++) {
            s.addResident(person(Occupation.UNEMPLOYED));
        }
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.ledger().add(ResourceType.WOOD, 200);
        s.ledger().add(ResourceType.STONE, 200);
        return s;
    }

    /** Vanilla rungs have no blocks in core: stand in a 5 by 5 floor for each: planks (25 wood) at tier 1, cobblestone (25 stone) above. */
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

    @Test
    void matchesIgnoresNamespaceCaseAndPropertiesTheBlueprintDoesNotName() {
        assertTrue(Construction.matches("minecraft:oak_planks", "OAK_PLANKS"));
        assertTrue(Construction.matches("minecraft:ladder[facing=south,waterlogged=false]", "LADDER[facing=south]"));
        assertFalse(Construction.matches("minecraft:ladder[facing=north,waterlogged=false]", "LADDER[facing=south]"));
        assertFalse(Construction.matches("minecraft:oak_planks", "SPRUCE_PLANKS"));
        assertTrue(Construction.matches(null, "AIR"));
        assertTrue(Construction.matches("minecraft:cave_air", "AIR"));
        assertFalse(Construction.matches(null, "OAK_PLANKS"));
    }

    @Test
    void aFinishedGeneratedBuildingNoLongerDiffsAgainstTheFullStateOfItsBlocks() {
        Blueprint b = BuildingGenerator.generate(BuildingType.MINE, 1).orElseThrow();
        // The world reports every block with all its properties, as Bukkit does.
        assertTrue(b.diff(block -> block.material().contains("[") ? block.material().toLowerCase().replace("]", ",waterlogged=false]")
                : "minecraft:" + block.material().toLowerCase()).isEmpty());
    }

    @Test
    void theWorklistKnocksOutOnlyWhatTheNewBuildingDoesNotReplace() {
        Blueprint old = new Blueprint("old", 2, 1, 1, List.of(new Blueprint.Block(0, 0, 0, "OAK_PLANKS"),
                new Blueprint.Block(1, 0, 0, "OAK_PLANKS")));
        Blueprint target = new Blueprint("new", 1, 1, 1, List.of(new Blueprint.Block(0, 0, 0, "COBBLESTONE")));
        Map<String, String> world = new HashMap<>(Map.of("0,0,0", "OAK_PLANKS", "1,0,0", "OAK_PLANKS"));
        Function<Blueprint.Block, String> at = b -> world.get(b.x() + "," + b.y() + "," + b.z());
        List<Construction.Step> steps = Construction.worklist(target, old, at);
        assertEquals(2, steps.size());
        assertTrue(steps.get(0).demolish());
        assertEquals(1, steps.get(0).block().x());
        assertFalse(steps.get(1).demolish());
        assertEquals("COBBLESTONE", steps.get(1).block().material());
        // A block the player has since changed is not ours to knock out.
        world.put("1,0,0", "GLASS");
        assertEquals(1, Construction.worklist(target, old, at).size());
        // Nothing left to do when it stands.
        world.put("0,0,0", "COBBLESTONE");
        assertTrue(Construction.worklist(target, null, at).isEmpty());
    }

    @Test
    void chargingTakesWholeUnitsAndKeepsTheHalfOfASlab() {
        Settlement s = village();
        ConstructionProject p = new ConstructionProject(1, BuildingType.MINE, 1, 0, "plains", 0, 64, 0, 1, 0);
        int before = s.ledger().get(ResourceType.WOOD);
        assertTrue(Construction.charge(s, p, "OAK_SLAB", 1));
        assertEquals(before - 1, s.ledger().get(ResourceType.WOOD));
        assertTrue(Construction.charge(s, p, "OAK_SLAB", 1)); // paid by the credit
        assertEquals(before - 1, s.ledger().get(ResourceType.WOOD));
        assertTrue(Construction.charge(s, p, "OAK_LOG", 1)); // 4 planks
        assertEquals(before - 5, s.ledger().get(ResourceType.WOOD));
        assertTrue(Construction.charge(s, p, "LANTERN", 1), "fittings are free");
        assertTrue(Construction.charge(s, p, "AIR", 1));
        assertEquals(before - 5, s.ledger().get(ResourceType.WOOD));
    }

    @Test
    void aBuilderWaitsWhenTheStoresRunOut() {
        Settlement s = new Settlement(VILLAGE, "Poorham", "world", 0, 0, 0);
        s.ledger().add(ResourceType.STONE, 1);
        ConstructionProject p = new ConstructionProject(1, BuildingType.MINE, 1, 0, "plains", 0, 64, 0, 1, 0);
        assertTrue(Construction.charge(s, p, "COBBLESTONE", 1));
        assertFalse(Construction.charge(s, p, "COBBLESTONE", 1));
        assertEquals(ResourceType.STONE, p.waitingFor());
        s.ledger().add(ResourceType.STONE, 5);
        assertTrue(Construction.charge(s, p, "COBBLESTONE", 1));
        assertNull(p.waitingFor());
    }

    @Test
    void aVillageWithoutAFarmQueuesOneOnAReservedLot() {
        Settlement s = village();
        Optional<ConstructionProject> project = propose(s, 5);
        assertTrue(project.isPresent());
        assertEquals(BuildingType.FARM, project.get().type());
        assertEquals(1, project.get().tier(), "the plainest design, even though the stores could pay for more");
        assertEquals(ConstructionProject.Status.QUEUED, project.get().status());
        VillagePlan.Lot lot = s.plan().lots().stream().filter(l -> l.id() == project.get().lotId()).findFirst().orElseThrow();
        assertTrue(lot.rect().contains(project.get().x(), project.get().z()));
        assertEquals(64, project.get().y());
        assertTrue(s.history().stream().anyMatch(e -> e.kind() == HistoryEvent.Kind.BUILDING && e.text().contains("farm")));
        // One project at a time, and one decision a day.
        assertTrue(propose(s, 6).isEmpty());
        assertTrue(propose(s, 5).isEmpty());
    }

    @Test
    void nothingIsQueuedWithoutAPlanPeopleOrMaterials() {
        Settlement noPlan = new Settlement(VILLAGE, "Planless", "world", 0, 0, 0);
        for (int i = 0; i < 6; i++) {
            noPlan.addResident(person(Occupation.UNEMPLOYED));
        }
        assertTrue(propose(noPlan, 5).isEmpty());

        Settlement poor = village();
        poor.ledger().take(ResourceType.WOOD, 200);
        poor.ledger().take(ResourceType.STONE, 200);
        assertTrue(propose(poor, 5).isEmpty(), "cannot afford even the crude tier");

        Settlement tiny = new Settlement(VILLAGE, "Tiny", "world", 0, 0, 0);
        tiny.addResident(person(Occupation.UNEMPLOYED));
        tiny.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        assertTrue(propose(tiny, 5).isEmpty());

        assertTrue(Construction.propose(village(), 5, "plains", 200, catalog, this::blueprints,
                (x, z) -> Construction.UNKNOWN_GROUND).isEmpty(), "ground not measured");
    }

    @Test
    void aTemplateThatDoesNotFitTheLotIsNotChosen() {
        Settlement s = village();
        Optional<ConstructionProject> project = Construction.propose(s, 5, "plains", 200, catalog,
                t -> Optional.of(new Blueprint(t.key(), 40, 1, 40, List.of(new Blueprint.Block(0, 0, 0, "OAK_PLANKS")))),
                (x, z) -> 64);
        assertTrue(project.isEmpty());
    }

    @Test
    void aJoblessAdultTakesTheProjectAndStaysABuilderForTheNextOne() {
        Settlement s = village();
        SettlementSimulator sim = SettlementSimulator.withOldAgeDeaths(false);
        ConstructionProject project = propose(s, 5).orElseThrow();
        s.ledger().add(ResourceType.FOOD, 500);
        sim.simulateDay(s, 5);
        assertEquals(ConstructionProject.Status.ACTIVE, project.status());
        assertNotNull(project.builder());
        assertEquals(1, s.residents().stream().filter(r -> r.occupation() == Occupation.BUILDER).count());
        assertEquals(Occupation.BUILDER, s.resident(project.builder()).orElseThrow().occupation());
        assertTrue(s.openProject().isPresent());

        Resident builder = s.resident(project.builder()).orElseThrow();
        Construction.finish(s, project, 6);
        assertEquals(ConstructionProject.Status.DONE, project.status());
        assertTrue(s.openProject().isEmpty());
        assertEquals(VillagePlan.LotStatus.FILLED, s.plan().lots().stream().filter(l -> l.id() == project.lotId())
                .findFirst().orElseThrow().status());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("finished building a farm")));
        assertEquals(1, builder.built());
        // R4.21: they stay a builder between buildings and take the next one before anyone else is asked.
        sim.simulateDay(s, 6);
        assertEquals(Occupation.BUILDER, builder.occupation());
        ConstructionProject next = new ConstructionProject(s.nextProjectId(), BuildingType.HOUSE, 1, 0, "plains", 0, 64, 0, -1, 7);
        s.addProject(next);
        sim.simulateDay(s, 7);
        assertEquals(builder.id(), next.builder());
        assertEquals(1, s.residents().stream().filter(r -> r.occupation() == Occupation.BUILDER).count());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("began work on the house")));
    }

    @Test
    void aBuilderWithNothingToBuildForAWeekGoesBackToOtherWork() {
        Settlement s = village();
        SettlementSimulator sim = SettlementSimulator.withOldAgeDeaths(false);
        ConstructionProject project = propose(s, 5).orElseThrow();
        s.ledger().add(ResourceType.FOOD, 1000);
        sim.simulateDay(s, 5);
        Resident builder = s.resident(project.builder()).orElseThrow();
        Construction.finish(s, project, 6);
        for (long day = 6; day < 6 + Construction.BUILDER_IDLE_DAYS; day++) {
            sim.simulateDay(s, day);
            assertEquals(Occupation.BUILDER, builder.occupation(), "still a builder on day " + day);
        }
        sim.simulateDay(s, 6 + Construction.BUILDER_IDLE_DAYS);
        assertFalse(builder.occupation() == Occupation.BUILDER, "a week with nothing to build");
    }

    @Test
    void buildersGetFasterWithEveryFewBuildingsAndTheHistorySaysSo() {
        assertEquals(4, Construction.blocksPerPass(0));
        assertEquals(4, Construction.blocksPerPass(2));
        assertEquals(6, Construction.blocksPerPass(3));
        assertEquals(8, Construction.blocksPerPass(6));
        assertEquals(10, Construction.blocksPerPass(10));
        assertEquals(10, Construction.blocksPerPass(50));
        assertEquals("apprentice builder", Construction.builderTitle(0));
        assertEquals("master builder", Construction.builderTitle(10));

        Settlement s = village();
        Resident builder = s.residents().iterator().next();
        builder.setOccupation(Occupation.BUILDER);
        for (int i = 1; i <= 3; i++) {
            ConstructionProject p = new ConstructionProject(s.nextProjectId(), BuildingType.HOUSE, 1, 0, "plains", 0, 64, 0, -1, i);
            s.addProject(p);
            p.claim(builder.id());
            Construction.finish(s, p, i);
        }
        assertEquals(3, builder.built());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("has finished 3 buildings and is now a builder")));

        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(3, loaded.resident(builder.id()).orElseThrow().built());
        Map<String, Object> old = SettlementCodec.encode(s);
        old.put("format", 20);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> residents = (List<Map<String, Object>>) old.get("residents");
        residents.forEach(r -> r.remove("built"));
        assertEquals(0, SettlementCodec.decode(old).resident(builder.id()).orElseThrow().built(), "a format-20 save starts at 0");
    }

    @Test
    void aFamineKeepsEveryoneOnFoodExceptTheFarmThatEndsIt() {
        Settlement s = village();
        assertEquals(BuildingType.FARM, propose(s, 5).orElseThrow().type());
        s.conditions().put("famine", 4L);
        SettlementSimulator.withOldAgeDeaths(false).simulateDay(s, 5);
        assertEquals(1, s.residents().stream().filter(r -> r.occupation() == Occupation.BUILDER).count(), "the farm is built");

        Settlement h = village();
        h.addProject(new ConstructionProject(1, BuildingType.HOUSE, 1, 0, "plains", 0, 64, 0, -1, 5));
        h.conditions().put("famine", 4L);
        SettlementSimulator.withOldAgeDeaths(false).simulateDay(h, 5);
        assertEquals(0, h.residents().stream().filter(r -> r.occupation() == Occupation.BUILDER).count(), "a house waits");
    }

    @Test
    void aProjectWhoseBuilderIsGoneGoesBackInTheQueue() {
        Settlement s = village();
        SettlementSimulator sim = SettlementSimulator.withOldAgeDeaths(false);
        ConstructionProject project = propose(s, 5).orElseThrow();
        s.ledger().add(ResourceType.FOOD, 500);
        sim.simulateDay(s, 5);
        s.resident(project.builder()).orElseThrow().setOccupation(Occupation.GUARD); // called up
        sim.simulateDay(s, 6);
        assertEquals(ConstructionProject.Status.ACTIVE, project.status(), "a new builder took it");
        assertFalse(s.resident(project.builder()).orElseThrow().occupation() == Occupation.GUARD);
    }

    @Test
    void aCancelledProjectLeavesTheBuilderFreeForTheNextAndMovesThePlanOn() {
        Settlement s = village();
        SettlementSimulator sim = SettlementSimulator.withOldAgeDeaths(false);
        ConstructionProject project = propose(s, 5).orElseThrow();
        s.ledger().add(ResourceType.FOOD, 500);
        sim.simulateDay(s, 5);
        Construction.cancel(s, project, 6, "something was built there");
        sim.simulateDay(s, 6);
        assertEquals(1, s.residents().stream().filter(r -> r.occupation() == Occupation.BUILDER).count(), "kept for the next");
        assertTrue(s.projects().stream().noneMatch(p -> p.status() == ConstructionProject.Status.ACTIVE));
        assertEquals(ConstructionProject.Status.CANCELLED, project.status());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("given up")));
    }

    @Test
    void aVillageUpgradesWhatItBuiltItselfButNeverAPlayersBuilding() {
        Settlement s = village();
        // Give it a farm of its own on a lot, with its sign registered, and every other need met. It could only afford tier 1.
        s.ledger().take(ResourceType.WOOD, 170);
        s.ledger().take(ResourceType.STONE, 200);
        ConstructionProject farm = propose(s, 5).orElseThrow();
        farm.setSign(farm.x(), 65, farm.z() - 1);
        s.registerBuilding(new Building(BuildingType.FARM, farm.x(), 65, farm.z() - 1, 5, "village"));
        Construction.finish(s, farm, 6);
        for (BuildingType type : BuildingType.values()) {
            if (s.buildingCount(type) == 0) {
                s.registerBuilding(new Building(type, 1000 + type.ordinal(), 64, 1000, 0, "a player")); // never replaced
            }
        }
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.STONE, 100);
        s.housing().setChunk(0, 0, 20);
        Optional<ConstructionProject> upgrade = propose(s, 15);
        assertTrue(upgrade.isPresent(), "a large farm");
        assertTrue(upgrade.get().isUpgrade());
        assertEquals(2, upgrade.get().tier());
        assertEquals(farm.lotId(), upgrade.get().lotId());
        assertEquals(1, upgrade.get().previousTier());
        // Without its sign the building is no longer ours to touch.
        Settlement other = village();
        ConstructionProject lost = propose(other, 5).orElseThrow();
        other.ledger().add(ResourceType.FOOD, 1500);
        lost.setSign(lost.x(), 65, lost.z() - 1); // sign never registered (broken since)
        Construction.finish(other, lost, 6);
        for (BuildingType type : BuildingType.values()) {
            other.registerBuilding(new Building(type, 1000 + type.ordinal(), 64, 1000, 0, "a player"));
        }
        other.housing().setChunk(0, 0, 20);
        assertTrue(propose(other, 15).isEmpty());
    }

    @Test
    void projectsSurviveASaveAndAnOldSaveHasNone() {
        Settlement s = village();
        ConstructionProject project = propose(s, 5).orElseThrow();
        s.ledger().add(ResourceType.FOOD, 500);
        SettlementSimulator.withOldAgeDeaths(false).simulateDay(s, 5);
        project.credit().put(ResourceType.WOOD, 1);
        project.setSign(3, 65, 4);
        project.setSiteChecked(true);

        Map<String, Object> saved = SettlementCodec.encode(s);
        Settlement loaded = SettlementCodec.decode(saved);
        ConstructionProject back = loaded.openProject().orElseThrow();
        assertEquals(project.id(), back.id());
        assertEquals(BuildingType.FARM, back.type());
        assertEquals(project.builder(), back.builder());
        assertEquals(ConstructionProject.Status.ACTIVE, back.status());
        assertEquals(project.x(), back.x());
        assertEquals(1, back.credit().get(ResourceType.WOOD));
        assertEquals(65, back.signY());
        assertTrue(back.siteChecked());

        Map<String, Object> old = new java.util.LinkedHashMap<>(saved);
        old.remove("projects");
        old.put("format", 18);
        assertTrue(SettlementCodec.decode(old).projects().isEmpty());
        assertEquals(21, SettlementCodec.FORMAT_VERSION);
    }

    @Test
    void anUpgradeStaysOnTheOldFloorAndIsAlignedOverTheOldBuilding() {
        Settlement s = village();
        s.ledger().take(ResourceType.WOOD, 170);
        s.ledger().take(ResourceType.STONE, 200);
        ConstructionProject farm = propose(s, 5).orElseThrow();
        farm.setSign(farm.x(), 65, farm.z() - 1);
        s.registerBuilding(new Building(BuildingType.FARM, farm.x(), 65, farm.z() - 1, 5, "village"));
        Construction.finish(s, farm, 6);
        for (BuildingType type : BuildingType.values()) {
            if (s.buildingCount(type) == 0) {
                s.registerBuilding(new Building(type, 1000 + type.ordinal(), 64, 1000, 0, "a player"));
            }
        }
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.STONE, 100);
        s.housing().setChunk(0, 0, 20);
        // The ground now reads as the old roof, four blocks up: the upgrade must not build on it.
        ConstructionProject upgrade = Construction.propose(s, 15, "plains", 200, catalog, this::blueprints, (x, z) -> 68).orElseThrow();
        assertEquals(64, upgrade.y());
        assertEquals(farm.x(), upgrade.x() + upgrade.shiftX());
        assertEquals(farm.z(), upgrade.z() + upgrade.shiftZ());
        // Given up, it is not proposed again on that lot.
        Construction.cancel(s, upgrade, 16, "test");
        assertTrue(Construction.propose(s, 30, "plains", 200, catalog, this::blueprints, (x, z) -> 68).isEmpty());
    }

    @Test
    void aFamineReleasesTheBuilderAndFarmsComeBeforeBuilding() {
        Settlement s = village();
        ConstructionProject project = new ConstructionProject(1, BuildingType.HOUSE, 1, 0, "plains", 0, 64, 0, -1, 5);
        s.addProject(project);
        SettlementSimulator sim = SettlementSimulator.withOldAgeDeaths(false);
        s.ledger().add(ResourceType.FOOD, 500);
        sim.simulateDay(s, 5);
        assertEquals(ConstructionProject.Status.ACTIVE, project.status());
        s.conditions().put("famine", 5L);
        sim.simulateDay(s, 6);
        assertEquals(ConstructionProject.Status.QUEUED, project.status());
        assertEquals(0, s.residents().stream().filter(r -> r.occupation() == Occupation.BUILDER).count());

        // A farm is what ends a famine: its builder stays on it.
        Settlement f = village();
        ConstructionProject farm = propose(f, 5).orElseThrow();
        f.ledger().add(ResourceType.FOOD, 500);
        sim.simulateDay(f, 5);
        f.conditions().put("famine", 5L);
        sim.simulateDay(f, 6);
        assertEquals(ConstructionProject.Status.ACTIVE, farm.status());

        // Farm places free and food short: the jobless go to the farm first.
        Settlement fed = village();
        fed.registerBuilding(new Building(BuildingType.FARM, 500, 64, 500, 0, "a player"));
        ConstructionProject other = new ConstructionProject(1, BuildingType.HOUSE, 1, 0, "plains", 0, 64, 0, -1, 0);
        fed.addProject(other);
        SettlementSimulator.withOldAgeDeaths(false).simulateDay(fed, 5);
        assertEquals(ConstructionProject.Status.QUEUED, other.status());
    }

    @Test
    void aDamagedProjectIsDroppedAndASecondOpenOneIsCancelled() {
        Settlement s = village();
        propose(s, 5).orElseThrow();
        Map<String, Object> saved = SettlementCodec.encode(s);
        @SuppressWarnings("unchecked")
        List<Object> projects = (List<Object>) saved.get("projects");
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) projects.get(0);
        Map<String, Object> broken = new java.util.LinkedHashMap<>(first);
        broken.remove("x");
        Map<String, Object> second = new java.util.LinkedHashMap<>(first);
        second.put("id", 9);
        projects.add(broken);
        projects.add(second);
        Settlement loaded = SettlementCodec.decode(saved);
        assertEquals(2, loaded.projects().size(), "the one without a place was dropped");
        assertEquals(1, loaded.projects().stream().filter(ConstructionProject::isOpen).count());
    }

    @Test
    void aCropGrowingOrADoorOpeningDoesNotCountAsADifferentBlock() {
        assertTrue(Construction.matches("minecraft:wheat[age=7]", "WHEAT[age=0]"));
        assertTrue(Construction.matches("minecraft:oak_door[open=true,half=lower,facing=east]", "OAK_DOOR[open=false,half=lower,facing=east]"));
        assertFalse(Construction.matches("minecraft:oak_door[open=true,half=upper,facing=east]", "OAK_DOOR[half=lower,facing=east]"));
    }

    @Test
    void aVillageThatCannotAffordWhatItWantsSaysSoNowAndThenNotEveryDay() {
        Settlement poor = village();
        poor.ledger().take(ResourceType.WOOD, 200);
        poor.ledger().take(ResourceType.STONE, 200);
        assertTrue(propose(poor, 5).isEmpty());
        long notes = poor.history().stream().filter(e -> e.text().contains("cannot afford")).count();
        assertEquals(1, notes);
        assertTrue(poor.history().stream().anyMatch(e -> e.text().contains("needs 25 wood (it has 0)")), "says what is missing");
        assertTrue(propose(poor, 6).isEmpty());
        assertTrue(propose(poor, 8).isEmpty());
        assertEquals(1, poor.history().stream().filter(e -> e.text().contains("cannot afford")).count(), "not every day");
        assertTrue(propose(poor, 11).isEmpty());
        assertEquals(2, poor.history().stream().filter(e -> e.text().contains("cannot afford")).count(), "a reminder after five days");
    }

    @Test
    void villagersPayAFractionOfTheCraftingCostAndItAddsUpBlockByBlock() {
        int before = Construction.costPercent();
        try {
            Construction.setCostPercent(35);
            Blueprint wall = new Blueprint("w", 10, 10, 1, java.util.stream.IntStream.range(0, 100)
                    .mapToObj(i -> new Blueprint.Block(i % 10, i / 10, 0, "OAK_PLANKS")).toList());
            assertEquals(35, Construction.priceOf(wall).get(ResourceType.WOOD)); // 100 planks at 35%
            Settlement s = village();
            ConstructionProject p = new ConstructionProject(1, BuildingType.MINE, 1, 0, "plains", 0, 64, 0, 1, 0);
            int start = s.ledger().get(ResourceType.WOOD);
            for (Blueprint.Block block : wall.blocks()) {
                assertTrue(Construction.charge(s, p, block.material(), 1));
            }
            assertEquals(35, start - s.ledger().get(ResourceType.WOOD), "100 planks cost 35 wood in all, not 100");
            // At 35% a vanilla smithy (about 278 wood) is within what a small village can store (150).
            Settlement tiny = village();
            tiny.ledger().take(ResourceType.WOOD, 200);
            tiny.ledger().add(ResourceType.WOOD, 100);
            Blueprint smithy = new Blueprint("s", 10, 28, 1, java.util.stream.IntStream.range(0, 280)
                    .mapToObj(i -> new Blueprint.Block(i % 10, i / 10, 0, "OAK_PLANKS")).toList());
            assertEquals(98, Construction.priceOf(smithy).get(ResourceType.WOOD));
        } finally {
            Construction.setCostPercent(before);
        }
    }

    @Test
    void aVillageWithNoLotMadeForWhatItNeedsUsesAnotherFreeOne() {
        Settlement s = village();
        for (VillagePlan.Lot lot : new ArrayList<>(s.plan().lots())) {
            if (lot.type() == BuildingType.FARM) {
                s.plan().fill(lot.id()); // the plan had no farm lot left (dropped as steep or wet, or already used)
            }
        }
        assertTrue(s.plan().nextLot(BuildingType.FARM).isEmpty());
        ConstructionProject project = propose(s, 5).orElseThrow();
        assertEquals(BuildingType.FARM, project.type());
        VillagePlan.Lot lot = s.plan().lots().stream().filter(l -> l.id() == project.lotId()).findFirst().orElseThrow();
        assertEquals(BuildingType.HOUSE, lot.type(), "house lots are used first");
    }

    @Test
    void aFullyEmployedVillageDraftsAWorkerAfterAFewDaysButNeverAFarmer() {
        Settlement s = new Settlement(VILLAGE, "Busyham", "world", 0, 0, 0);
        s.addResident(person(Occupation.FARMER));
        s.addResident(person(Occupation.FARMER));
        Resident mason = person(Occupation.MASON);
        s.addResident(mason);
        s.addResident(person(Occupation.TOOLSMITH)); // the only smith: not to be taken
        s.addResident(person(Occupation.LUMBERJACK)); // R4.20: nor the only lumberjack
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        ConstructionProject project = new ConstructionProject(1, BuildingType.HOUSE, 1, 0, "plains", 0, 64, 0, -1, 5);
        s.addProject(project);
        Construction.staffBuilders(s, 6, false);
        assertEquals(ConstructionProject.Status.QUEUED, project.status(), "not yet: someone may still be out of work");
        Construction.staffBuilders(s, 8, false);
        assertEquals(ConstructionProject.Status.QUEUED, project.status(), "R4.20: the only mason is not taken either");
        s.addResident(person(Occupation.MASON));
        Construction.staffBuilders(s, 8, false);
        assertEquals(ConstructionProject.Status.ACTIVE, project.status());
        assertEquals(mason.id(), project.builder());
        assertEquals(Occupation.BUILDER, mason.occupation());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("put down the work of a mason")));
    }

    @Test
    void theGeneratedBuildingsHaveNoJobSitesAVillagerCouldClaim() {
        java.util.Set<String> jobSites = java.util.Set.of("BARREL", "LECTERN", "LOOM", "COMPOSTER", "SMOKER", "BLAST_FURNACE",
                "FLETCHING_TABLE", "CARTOGRAPHY_TABLE", "BREWING_STAND", "GRINDSTONE", "STONECUTTER", "SMITHING_TABLE", "CAULDRON");
        for (BuildingType type : BuildingType.values()) {
            for (int tier = 1; tier <= BuildingGenerator.TIERS; tier++) {
                for (String material : BuildingGenerator.generate(type, tier).map(Blueprint::materialCounts).orElse(Map.of()).keySet()) {
                    assertFalse(jobSites.contains(Blueprint.name(material)), type + " tier " + tier + " has a " + material);
                }
            }
        }
    }

    @Test
    void aVillageOfFoodWorkersDraftsOneWhenTheLarderIsFullButNotBefore() {
        Settlement s = new Settlement(VILLAGE, "Fishford", "world", 0, 0, 0);
        for (int i = 0; i < 3; i++) {
            s.addResident(person(Occupation.FARMER));
        }
        s.addResident(person(Occupation.FISHERMAN));
        ConstructionProject project = new ConstructionProject(1, BuildingType.HOUSE, 1, 0, "plains", 0, 64, 0, -1, 1);
        s.addProject(project);
        s.ledger().add(ResourceType.FOOD, 30);
        Construction.staffBuilders(s, 9, false);
        assertEquals(ConstructionProject.Status.QUEUED, project.status(), "food is not plentiful: they stay on food");
        s.ledger().add(ResourceType.FOOD, 150);
        Construction.staffBuilders(s, 9, false);
        assertEquals(ConstructionProject.Status.ACTIVE, project.status());
        assertEquals(3, s.residents().stream().filter(r -> r.occupation().produces() == ResourceType.FOOD).count());
    }

    @Test
    void aQueuedBuildingThatIsNoLongerNeededIsDroppedBeforeAnyoneStartsIt() {
        Settlement s = village();
        ConstructionProject farm = propose(s, 5).orElseThrow();
        assertEquals(BuildingType.FARM, farm.type());
        s.ledger().add(ResourceType.FOOD, 500); // the larders have filled: no farm is wanted now
        Optional<ConstructionProject> next = propose(s, 6);
        assertEquals(ConstructionProject.Status.CANCELLED, farm.status());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("not needed any more")));
        // The lot was not used up.
        assertEquals(VillagePlan.LotStatus.RESERVED, s.plan().lots().stream().filter(l -> l.id() == farm.lotId())
                .findFirst().orElseThrow().status());
        assertTrue(next.isEmpty() || next.get().type() != BuildingType.FARM);
    }

    @Test
    void rotatingFourQuarterTurnsGivesTheBuildingBackAndTurnsWhatFaces() {
        Blueprint mine = BuildingGenerator.generate(BuildingType.MINE, 2).orElseThrow();
        assertEquals(mine, mine.rotated(4));
        assertEquals(mine, mine.rotated(0));
        Blueprint turned = mine.rotated(1);
        assertEquals(mine.depth(), turned.width());
        assertEquals(mine.width(), turned.depth());
        assertEquals(mine.blocks().size(), turned.blocks().size());
        assertEquals(2, mine.front().orElseThrow(), "the generated entrance faces south");
        assertEquals(3, turned.front().orElseThrow(), "one clockwise turn: south becomes west");
        assertEquals(0, mine.rotated(2).front().orElseThrow());
        assertEquals("LADDER[facing=west]", mine.rotated(1).blocks().stream()
                .map(Blueprint.Block::material).filter(m -> m.startsWith("LADDER")).findFirst().orElseThrow());
        assertEquals("OAK_FENCE[east=true,north=false]", Blueprint.turnState("OAK_FENCE[north=true,west=false]"));
        assertEquals("OAK_LOG[axis=z]", Blueprint.turnState("OAK_LOG[axis=x]"));
        assertEquals("OAK_SIGN[rotation=4]", Blueprint.turnState("OAK_SIGN[rotation=0]"));
    }

    @Test
    void aDoorWithNoSignSaysWhichSideTheBuildingFaces() {
        Blueprint house = new Blueprint("h", 7, 3, 7, List.of(new Blueprint.Block(6, 1, 3, "OAK_DOOR[half=lower,facing=east]"),
                new Blueprint.Block(6, 2, 3, "OAK_DOOR[half=upper,facing=east]"), new Blueprint.Block(0, 0, 0, "COBBLESTONE")));
        assertEquals(1, house.front().orElseThrow(), "the door is on the east wall");
        assertTrue(new Blueprint("f", 2, 1, 2, List.of(new Blueprint.Block(0, 0, 0, "FARMLAND"))).front().isEmpty());
    }

    @Test
    void everyGeneratedBuildingCanBeTurnedToFaceEveryLotOfThePlan() {
        Settlement s = village();
        for (VillagePlan.Lot lot : s.plan().lots()) {
            int toStreet = s.plan().facingFor(lot.rect());
            for (BuildingType type : BuildingType.values()) {
                Blueprint b = BuildingGenerator.generate(type, 1).orElse(null);
                if (b != null) {
                    assertEquals(toStreet, b.rotated(Construction.turnsFor(b, toStreet)).front().orElseThrow());
                }
            }
        }
    }

    @Test
    void aProjectRecordsTheTurnThatPutsItsEntranceOnTheStreetAndItSurvivesASave() {
        Settlement s = village();
        // Every design has its door in the east wall.
        Blueprint hall = new Blueprint("hall", 7, 3, 7, List.of(new Blueprint.Block(6, 1, 3, "OAK_DOOR[half=lower,facing=east]"),
                new Blueprint.Block(0, 0, 0, "OAK_PLANKS")));
        ConstructionProject made = Construction.propose(s, 5, "plains", 200, catalog, t -> Optional.of(hall), (x, z) -> 64).orElseThrow();
        VillagePlan.Lot lot = s.plan().lots().stream().filter(l -> l.id() == made.lotId()).findFirst().orElseThrow();
        assertEquals(s.plan().facingFor(lot.rect()), hall.rotated(made.turns()).front().orElseThrow());
        assertEquals(made.turns(), SettlementCodec.decode(SettlementCodec.encode(s)).projects().get(0).turns());
    }

    @Test
    void aTemplateThatFitsOnlyTurnedIsTurnedRatherThanDropped() {
        Settlement s = new Settlement(VILLAGE, "Tightford", "world", 0, 0, 0);
        for (int i = 0; i < 6; i++) {
            s.addResident(person(Occupation.UNEMPLOYED));
        }
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.ledger().add(ResourceType.WOOD, 100);
        // Longer than it is wide, with its door at one end: whichever way the street lies, it has to fit the lot somehow.
        Optional<ConstructionProject> project = Construction.propose(s, 5, "plains", 200, catalog,
                t -> Optional.of(new Blueprint(t.key(), 5, 1, 17, List.of(new Blueprint.Block(2, 0, 16, "OAK_PLANKS"),
                        new Blueprint.Block(2, 1, 16, "OAK_DOOR[half=lower,facing=south]")))), (x, z) -> 64);
        assertTrue(project.isPresent());
    }

    @Test
    void aSlopedLotIsSetAtTheMedianHeightAndAnImpossibleOneIsPassedOver() {
        Settlement s = village();
        Optional<ConstructionProject> project = Construction.propose(s, 5, "plains", 200, catalog, this::blueprints,
                (x, z) -> 60 + Math.floorMod(x, 3)); // a gentle ripple: 60, 61, 62
        assertEquals(61, project.orElseThrow().y(), "the median of the ground under it");
        Settlement cliff = village();
        assertTrue(Construction.propose(cliff, 5, "plains", 200, catalog, this::blueprints,
                (x, z) -> x % 2 == 0 ? 40 : 90).isEmpty(), "cut or fill beyond the limit: not built here");
    }

    @Test
    void onceThereIsFoodTheVillageBuildsItsTownSquareOnceAndOnlyOnce() {
        Settlement s = village();
        s.ledger().add(ResourceType.FOOD, 500); // nothing lacking: the meeting place is next
        ConstructionProject square = propose(s, 5).orElseThrow();
        assertEquals(BuildingType.SQUARE, square.type());
        assertEquals(1, square.tier());
        assertTrue(s.plan().square().contains(square.x(), square.z()), "on the plan's main square");
        Construction.finish(s, square, 6);
        assertTrue(propose(s, 20).map(p -> p.type() != BuildingType.SQUARE).orElse(true), "never a second square");
        // A given-up attempt waits ten days before the next.
        Settlement other = village();
        other.ledger().add(ResourceType.FOOD, 500);
        Construction.cancel(other, propose(other, 5).orElseThrow(), 6, "test");
        ConstructionProject early = propose(other, 9).orElse(null); // something else may be wanted: not the square
        assertTrue(early == null || early.type() != BuildingType.SQUARE);
        if (early != null) {
            Construction.cancel(other, early, 10, "test", false);
        }
        assertEquals(BuildingType.SQUARE, propose(other, 25).orElseThrow().type());
    }

    @Test
    void foodComesBeforeTheTownSquare() {
        Settlement s = village(); // no food: the planner wants a farm first
        assertEquals(BuildingType.FARM, propose(s, 5).orElseThrow().type());
    }

    @Test
    void everyStyleHasATownSquareLadderOfTheGamesOwnPieces() {
        for (String style : BiomeSet.ALL) {
            List<TemplateCatalog.Template> ladder = catalog.ladder(BuildingType.SQUARE, style);
            assertFalse(ladder.isEmpty(), style);
            assertTrue(ladder.get(0).key().contains("/" + style + "/town_centers/"), ladder.get(0).key());
        }
    }

    @Test
    void aHungryVillageThatCannotYetBuildItsFarmDoesNotSpendOnTheSquare() {
        Settlement poor = village();
        poor.ledger().take(ResourceType.WOOD, 200);
        poor.ledger().add(ResourceType.WOOD, 30); // the square costs 25, the farm more
        Optional<ConstructionProject> project = Construction.propose(poor, 5, "plains", 200, catalog,
                t -> Optional.of(new Blueprint(t.key(), 5, 1, 5, java.util.stream.IntStream.range(0, 25)
                        .mapToObj(i -> new Blueprint.Block(i % 5, 0, i / 5, "OAK_PLANKS")).toList()
                        .subList(0, t.type() == BuildingType.FARM ? 25 : 10))), (x, z) -> 64);
        // Whatever it may have queued, it is not the square while food is what the village lacks.
        assertTrue(project.isEmpty() || project.get().type() != BuildingType.SQUARE);
        assertTrue(poor.history().stream().noneMatch(e -> e.text().contains("town square")));
    }

    @Test
    void aQueuedSquareIsNotDroppedAsNotNeededAndTheVillageStopsAfterTwoFailures() {
        Settlement s = village();
        s.ledger().add(ResourceType.FOOD, 500);
        ConstructionProject square = propose(s, 5).orElseThrow();
        assertEquals(BuildingType.SQUARE, square.type());
        propose(s, 6);
        assertEquals(ConstructionProject.Status.QUEUED, square.status(), "still waiting for a builder");
        Construction.cancel(s, square, 7, "test");
        ConstructionProject second = propose(s, 20).orElseThrow();
        assertEquals(BuildingType.SQUARE, second.type());
        Construction.cancel(s, second, 21, "test");
        for (long day = 40; day < 60; day++) {
            Optional<ConstructionProject> p = propose(s, day);
            assertTrue(p.isEmpty() || p.get().type() != BuildingType.SQUARE, "never a third attempt");
            p.ifPresent(x -> Construction.cancel(s, x, 0, "test", false));
        }
    }

    /** A building the village itself raised on a lot: finished, its sign registered, so it counts and can be improved. */
    private ConstructionProject raised(Settlement s, BuildingType type, int tier, int signX) {
        VillagePlan.Lot lot = s.plan().lots().stream().filter(l -> l.status() == VillagePlan.LotStatus.RESERVED).findFirst().orElseThrow();
        ConstructionProject p = new ConstructionProject(s.nextProjectId(), type, tier, 0, "plains", lot.rect().x(), 64,
                lot.rect().z(), lot.id(), 0);
        p.setSign(signX, 65, 5000);
        s.addProject(p);
        s.registerBuilding(new Building(type, signX, 65, 5000, 0, "the builders"));
        Construction.finish(s, p, 1);
        return p;
    }

    @Test
    void eachTierAddsPlacesToAWorkBuildingButOnlyForTheVillagesOwnProjects() {
        Settlement s = village();
        raised(s, BuildingType.FARM, 2, 4000);
        assertEquals(6, SettlementSimulator.placesFor(s, Occupation.FARMER), "4 places and 2 for the second tier");
        s.registerBuilding(new Building(BuildingType.MINE, 4100, 65, 5000, 0, "a player"));
        assertEquals(4, SettlementSimulator.placesFor(s, Occupation.MINER), "a player's building is tier 1");
        raised(s, BuildingType.SHOP, 3, 4200);
        assertEquals(3, SettlementSimulator.placesFor(s, Occupation.MERCHANT), "a shop adds one a tier");
    }

    @Test
    void aNeedThatCannotBeMetWithANewBuildingIsMetByImprovingOne() {
        Settlement s = new Settlement(VILLAGE, "Tightham", "world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(person(Occupation.FARMER)); // every place at the one farm is taken, and food is short
        }
        s.addResident(person(Occupation.UNEMPLOYED));
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.ledger().add(ResourceType.WOOD, 100);
        s.ledger().add(ResourceType.STONE, 100);
        ConstructionProject farm = raised(s, BuildingType.FARM, 1, 4000);
        for (VillagePlan.Lot lot : new ArrayList<>(s.plan().lots())) {
            s.plan().fill(lot.id()); // nowhere for another farm
        }
        ConstructionProject upgrade = propose(s, 2).orElseThrow();
        assertTrue(upgrade.isUpgrade());
        assertEquals(BuildingType.FARM, upgrade.type());
        assertEquals(2, upgrade.tier());
        assertEquals(farm.lotId(), upgrade.lotId());
    }

    @Test
    void aVillagesDirectionFollowsWhatItMakesAndTrades() {
        Settlement s = village();
        assertEquals(Construction.Direction.UNDECIDED, Construction.direction(s, 10));
        s.flow().recordProduced(ResourceType.FOOD, 10, 400);
        assertEquals(Construction.Direction.FARMING, Construction.direction(s, 10));
        s.flow().recordProduced(ResourceType.STONE, 10, 300);
        assertEquals(Construction.Direction.MINING, Construction.direction(s, 10));
        s.addResident(person(Occupation.MERCHANT));
        s.ledger().addTreasury(150);
        assertEquals(Construction.Direction.TRADE, Construction.direction(s, 10));
    }

    @Test
    void withEveryNeedMetTheVillageImprovesWhatItsDirectionServesFirst() {
        Settlement s = village();
        s.ledger().add(ResourceType.FOOD, 500);
        raised(s, BuildingType.MINE, 1, 4100); // older
        raised(s, BuildingType.FARM, 1, 4000); // newer: without a direction the newest would come first
        for (BuildingType type : BuildingType.values()) {
            if (s.buildingCount(type) == 0) {
                s.registerBuilding(new Building(type, 6000 + type.ordinal(), 64, 6000, 0, "a player"));
            }
        }
        s.ledger().add(ResourceType.WOOD, 100);
        s.ledger().add(ResourceType.STONE, 100);
        s.housing().setChunk(0, 0, 20);
        s.flow().recordProduced(ResourceType.STONE, 20, 300); // a mining village
        // A cheap second-tier mine, so what it costs does not decide the test.
        catalog.capture(BuildingType.MINE, "plains", 2, new Blueprint("m2", 3, 1, 3, List.of(new Blueprint.Block(0, 0, 0, "COBBLESTONE"))));
        ConstructionProject upgrade = propose(s, 20).orElseThrow();
        assertTrue(upgrade.isUpgrade());
        assertEquals(BuildingType.MINE, upgrade.type(), "the mine, not the older farm");
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("mining village")));
        assertTrue(s.conditions().containsKey(Construction.UPGRADED), "a want is paced");
    }

    @Test
    void aBuildingKeepsItsTierOnlyWhileItIsTheVillagesOwnAndTheNewestOnItsLot() {
        Settlement s = village();
        raised(s, BuildingType.FARM, 2, 4000);
        assertEquals(2, s.tierOf(new Building(BuildingType.FARM, 4000, 65, 5000, 0, "the builders")));
        assertEquals(1, s.tierOf(new Building(BuildingType.MINE, 4000, 65, 5000, 0, "a player")), "another kind at the same sign");
        assertEquals(1, s.tierOf(new Building(BuildingType.FARM, 4001, 65, 5000, 0, "a player")), "another sign");
    }

    @Test
    void trimmingOldProjectsNeverForgetsABuildingThatStillStands() {
        Settlement s = village();
        raised(s, BuildingType.FARM, 2, 4000); // the oldest record, and still standing
        for (int i = 0; i < Settlement.MAX_CLOSED_PROJECTS + 10; i++) {
            ConstructionProject p = new ConstructionProject(s.nextProjectId(), BuildingType.HOUSE, 1, 0, "plains", 0, 64, 0, -1, i);
            s.addProject(p);
            p.cancel(i);
        }
        assertEquals(2, s.tierOf(new Building(BuildingType.FARM, 4000, 65, 5000, 0, "the builders")), "its tier survives the trimming");
        assertTrue(s.projects().size() <= Settlement.MAX_CLOSED_PROJECTS + 2);
    }

    @Test
    void aBuildingThatLacksWoodMakesAJoblessResidentALumberjackWhateverTheStockPerHead() {
        SettlementSimulator sim = SettlementSimulator.withOldAgeDeaths(false);
        Settlement control = village();
        control.ledger().take(ResourceType.WOOD, 180); // 20 wood: above the 18 (3 a head) that counts as short
        control.ledger().add(ResourceType.FOOD, 500);
        sim.simulateDay(control, 5);
        assertEquals(0, control.residents().stream().filter(r -> r.occupation() == Occupation.LUMBERJACK).count());

        Settlement s = village();
        s.ledger().take(ResourceType.WOOD, 180);
        s.ledger().take(ResourceType.STONE, 200);
        s.ledger().add(ResourceType.FOOD, 500);
        assertTrue(propose(s, 5).isEmpty(), "every plainest design costs 25 and it has 20");
        assertTrue(Construction.lacking(s, 5).contains(ResourceType.WOOD));
        Settlement late = SettlementCodec.decode(SettlementCodec.encode(s)); // the same, but checked long after
        s.setLastSimulatedDay(4);
        sim.simulateDay(s, 5);
        assertEquals(1, s.residents().stream().filter(r -> r.occupation() == Occupation.LUMBERJACK).count());
        assertFalse(Construction.lacking(s, 5 + Construction.LACK_MEMORY_DAYS + 1).contains(ResourceType.WOOD), "forgotten in time");
        late.setLastSimulatedDay(5 + Construction.LACK_MEMORY_DAYS);
        sim.simulateDay(late, 6 + Construction.LACK_MEMORY_DAYS);
        assertEquals(0, late.residents().stream().filter(r -> r.occupation() == Occupation.LUMBERJACK).count(), "a stale lack hires nobody");
    }

    @Test
    void withNobodyJoblessAFoodWorkerIsMovedToTheTradeABuildingLacksButOnlyWhenTheLarderIsFull() {
        SettlementSimulator sim = SettlementSimulator.withOldAgeDeaths(false);
        for (int food : new int[] {30, 500}) {
            Settlement s = new Settlement(VILLAGE, "Farmham", "world", 0, 0, 0);
            for (int i = 0; i < 4; i++) {
                s.addResident(person(Occupation.FARMER));
            }
            s.registerBuilding(new Building(BuildingType.FARM, 0, 64, 0, 0, "test"));
            s.ledger().add(ResourceType.FOOD, food);
            s.ledger().add(ResourceType.WOOD, 20);
            s.conditions().put(Construction.LACKS + "WOOD", 5L);
            s.setLastSimulatedDay(4);
            sim.simulateDay(s, 5);
            long lumberjacks = s.residents().stream().filter(r -> r.occupation() == Occupation.LUMBERJACK).count();
            assertEquals(food >= 80 ? 1 : 0, lumberjacks, "food " + food);
            if (lumberjacks == 1) {
                assertTrue(s.history().stream().anyMatch(e -> e.text().contains("to work as a lumberjack")));
                sim.simulateDay(s, 6);
                assertEquals(1, s.residents().stream().filter(r -> r.occupation() == Occupation.LUMBERJACK).count(), "one is enough");
            }
        }
    }

    @Test
    void aNeedTheVillageCannotPayForIsSavedUpForAndAWantKeepsOnlyWhatStopsItBeingShort() {
        Settlement s = village();
        s.ledger().take(ResourceType.WOOD, 170);
        s.ledger().take(ResourceType.STONE, 200);
        ConstructionProject farm = propose(s, 5).orElseThrow();
        farm.setSign(farm.x(), 65, farm.z() - 1);
        s.registerBuilding(new Building(BuildingType.FARM, farm.x(), 65, farm.z() - 1, 5, "village"));
        Construction.finish(s, farm, 6);
        for (BuildingType type : BuildingType.values()) {
            if (s.buildingCount(type) == 0) {
                s.registerBuilding(new Building(type, 1000 + type.ordinal(), 64, 1000, 0, "a player"));
            }
        }
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.STONE, 45); // under twice the 25 an upgrade costs, but 27 over the 18 that is short
        s.housing().setChunk(0, 0, 2); // six people, two beds: a house is needed, and no wood to pay for one
        s.ledger().take(ResourceType.WOOD, 30); // spent on the farm (core only charges as blocks go down)
        // Every house design costs 50 wood, which it does not have; the farm's tier 2 costs 25 stone.
        Function<TemplateCatalog.Template, Optional<Blueprint>> designs = t -> t.type() != BuildingType.HOUSE ? blueprints(t)
                : Optional.of(new Blueprint(t.key(), 5, 2, 5, java.util.stream.IntStream.range(0, 50)
                        .mapToObj(i -> new Blueprint.Block(i % 5, i / 25, (i / 5) % 5, "OAK_PLANKS")).toList()));
        assertTrue(Construction.propose(s, 15, "plains", 200, catalog, designs, (x, z) -> 64).isEmpty(),
                "no farm upgrade while the house it needs waits");
        assertTrue(Construction.lacking(s, 15).contains(ResourceType.WOOD));

        s.housing().setChunk(0, 0, 20);
        Optional<ConstructionProject> upgrade = Construction.propose(s, 16, "plains", 200, catalog, designs, (x, z) -> 64);
        assertTrue(upgrade.isPresent() && upgrade.get().isUpgrade(), "housed, it can improve the farm");
    }

    @Test
    void aMineItHasNoWayToPayForDoesNotStopAVillageImprovingWhatItHas() {
        Settlement s = village();
        s.ledger().take(ResourceType.WOOD, 170);
        s.ledger().take(ResourceType.STONE, 200);
        ConstructionProject farm = propose(s, 5).orElseThrow();
        farm.setSign(farm.x(), 65, farm.z() - 1);
        s.registerBuilding(new Building(BuildingType.FARM, farm.x(), 65, farm.z() - 1, 5, "village"));
        Construction.finish(s, farm, 6);
        for (BuildingType type : BuildingType.values()) {
            if (s.buildingCount(type) == 0 && type != BuildingType.MINE) {
                s.registerBuilding(new Building(type, 1000 + type.ordinal(), 64, 1000, 0, "a player"));
            }
        }
        s.ledger().add(ResourceType.FOOD, 1000);
        s.housing().setChunk(0, 0, 20);
        // Mines cost stone (generated, tier 1 needs 4), which it has none of and no way to make; the farm's tier 2 costs wood here.
        Function<TemplateCatalog.Template, Optional<Blueprint>> designs = t -> t.type() == BuildingType.MINE ? blueprints(t)
                : Optional.of(new Blueprint(t.key(), 5, 1, 5, java.util.stream.IntStream.range(0, 25)
                        .mapToObj(i -> new Blueprint.Block(i % 5, 0, i / 5, "OAK_PLANKS")).toList()));
        s.ledger().add(ResourceType.WOOD, 100);
        Optional<ConstructionProject> upgrade = Construction.propose(s, 15, "plains", 200, catalog, designs, (x, z) -> 64);
        assertTrue(upgrade.isPresent() && upgrade.get().isUpgrade(), "no mine, but the farm is improved: " + upgrade);
    }

    @Test
    void cropsAreSownNotPaidFor() {
        assertTrue(Blueprint.halvesOf("WHEAT[age=0]").isEmpty());
        assertTrue(Blueprint.halvesOf("CARROTS").isEmpty());
        assertTrue(Blueprint.halvesOf("HAY_BLOCK").isPresent(), "a bale is still made of wheat");
    }
}
