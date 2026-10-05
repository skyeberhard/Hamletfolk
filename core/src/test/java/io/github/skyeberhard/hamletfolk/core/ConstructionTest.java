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
        assertEquals(2, project.get().tier(), "the best tier the stores can pay for");
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
    void aJoblessAdultTakesTheProjectAndGivesItBackWhenItIsDone() {
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

        Construction.finish(s, project, 6);
        assertEquals(ConstructionProject.Status.DONE, project.status());
        assertTrue(s.openProject().isEmpty());
        assertEquals(VillagePlan.LotStatus.FILLED, s.plan().lots().stream().filter(l -> l.id() == project.lotId())
                .findFirst().orElseThrow().status());
        sim.simulateDay(s, 6);
        assertEquals(0, s.residents().stream().filter(r -> r.occupation() == Occupation.BUILDER).count());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("finished building a farm")));
    }

    @Test
    void aFamineKeepsEveryoneOnFood() {
        Settlement s = village();
        propose(s, 5).orElseThrow();
        s.conditions().put("famine", 4L);
        SettlementSimulator.withOldAgeDeaths(false).simulateDay(s, 5);
        assertEquals(0, s.residents().stream().filter(r -> r.occupation() == Occupation.BUILDER).count());
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
    void aCancelledProjectFreesTheBuilderAndMovesThePlanOn() {
        Settlement s = village();
        SettlementSimulator sim = SettlementSimulator.withOldAgeDeaths(false);
        ConstructionProject project = propose(s, 5).orElseThrow();
        s.ledger().add(ResourceType.FOOD, 500);
        sim.simulateDay(s, 5);
        Construction.cancel(s, project, 6, "something was built there");
        sim.simulateDay(s, 6);
        assertEquals(0, s.residents().stream().filter(r -> r.occupation() == Occupation.BUILDER).count());
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
        Optional<ConstructionProject> upgrade = propose(s, 7);
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
        assertTrue(propose(other, 7).isEmpty());
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
        assertEquals(19, SettlementCodec.FORMAT_VERSION);
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
        ConstructionProject upgrade = Construction.propose(s, 7, "plains", 200, catalog, this::blueprints, (x, z) -> 68).orElseThrow();
        assertEquals(64, upgrade.y());
        assertEquals(farm.x(), upgrade.x() + upgrade.shiftX());
        assertEquals(farm.z(), upgrade.z() + upgrade.shiftZ());
        // Given up, it is not proposed again on that lot.
        Construction.cancel(s, upgrade, 8, "test");
        assertTrue(Construction.propose(s, 9, "plains", 200, catalog, this::blueprints, (x, z) -> 68).isEmpty());
    }

    @Test
    void aFamineReleasesTheBuilderAndFarmsComeBeforeBuilding() {
        Settlement s = village();
        ConstructionProject project = propose(s, 5).orElseThrow();
        SettlementSimulator sim = SettlementSimulator.withOldAgeDeaths(false);
        s.ledger().add(ResourceType.FOOD, 500);
        sim.simulateDay(s, 5);
        assertEquals(ConstructionProject.Status.ACTIVE, project.status());
        s.conditions().put("famine", 5L);
        sim.simulateDay(s, 6);
        assertEquals(ConstructionProject.Status.QUEUED, project.status());
        assertEquals(0, s.residents().stream().filter(r -> r.occupation() == Occupation.BUILDER).count());

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
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        ConstructionProject project = new ConstructionProject(1, BuildingType.HOUSE, 1, 0, "plains", 0, 64, 0, -1, 5);
        s.addProject(project);
        Construction.staffBuilders(s, 6, false);
        assertEquals(ConstructionProject.Status.QUEUED, project.status(), "not yet: someone may still be out of work");
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
}
