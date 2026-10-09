package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R8.12: the building a village's leaning calls for, and what each one does. */
class SignatureBuildingTest {
    private static final UUID SAME = new UUID(96, 96);
    private final SettlementSimulator simulator = SettlementSimulator.withOldAgeDeaths(false);
    private final TemplateCatalog catalog = new TemplateCatalog();
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(97, next++), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50), job, true, 10_000, null, null,
                Needs.initial());
    }

    private static final String[][] LANDS = {{"forest", "SAWMILL"}, {"windswept_hills", "FORGE"}, {"plains", "GRANARY"},
            {"savanna", "GRANARY"}};

    /** A settled village of {@code people} on the given land: fed, housed, with a mine so only wants remain. */
    private Settlement settled(String biome, int people) {
        Settlement s = new Settlement(UUID.randomUUID(), "Charactham", "world", 0, 0, 0);
        for (int i = 0; i < people; i++) {
            s.addResident(person(Occupation.NITWIT));
        }
        s.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> biome)));
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.ledger().add(Commodity.BREAD, 5000);
        s.ledger().add(Commodity.PLANKS, 500);
        s.ledger().add(Commodity.COBBLESTONE, 500);
        s.housing().setChunk(0, 0, people + 2);
        s.registerBuilding(new Building(BuildingType.MINE, 2000, 64, 2000, 0, "a player"));
        s.registerBuilding(new Building(BuildingType.SHOP, 2010, 64, 2000, 0, "a player")); // so the shop is not what is wanted
        s.registerBuilding(new Building(BuildingType.TREASURY, 2020, 64, 2000, 0, "a player"));
        return s;
    }

    @Test
    void eachLeaningCallsForItsOwnBuilding() {
        assertEquals(BuildingType.SAWMILL, Leaning.TIMBER.signature());
        assertEquals(BuildingType.FORGE, Leaning.MINING.signature());
        assertEquals(BuildingType.GRANARY, Leaning.FARMING.signature());
        assertEquals(BuildingType.GRANARY, Leaning.PASTORAL.signature());
        assertNull(Leaning.FISHING.signature(), "a harbour comes with R8.13");
        assertNull(Leaning.ALL_ROUND.signature());
        assertTrue(Construction.Direction.FORESTRY.serves(BuildingType.SAWMILL));
        assertTrue(Construction.Direction.MINING.serves(BuildingType.FORGE));
        assertTrue(Construction.Direction.FARMING.serves(BuildingType.GRANARY));
    }

    @Test
    void itIsWantedOnceEveryNeedIsMetAndTheVillageIsBigEnoughAndOnlyOnce() {
        for (String[] land : LANDS) {
            BuildingType wanted = BuildingType.valueOf(land[1]);
            Settlement s = settled(land[0], 10);
            assertTrue(Planner.directives(s, 5, 200).stream().anyMatch(d -> d.target().equalsIgnoreCase(wanted.name())),
                    land[0] + " wants a " + wanted);

            Settlement hamlet = settled(land[0], 7);
            assertTrue(Planner.directives(hamlet, 5, 200).stream().noneMatch(d -> d.target().equalsIgnoreCase(wanted.name())),
                    "seven is a hamlet: too small");

            Settlement built = settled(land[0], 10);
            built.registerBuilding(new Building(wanted, 3000, 64, 3000, 0, "the builders"));
            assertTrue(Planner.directives(built, 5, 200).stream().noneMatch(d -> d.target().equalsIgnoreCase(wanted.name())),
                    "one of each");

            // Still asked for while one is queued, or the planner would cancel the queued project as unwanted.
            Settlement queued = settled(land[0], 10);
            ConstructionProject project = Construction.propose(queued, 5, "plains", 200, catalog, t -> catalog.blueprint(t), (x, z) -> 64)
                    .orElseThrow();
            assertEquals(wanted, project.type());
            for (long day = 6; day <= 9; day++) {
                Construction.propose(queued, day, "plains", 200, catalog, t -> catalog.blueprint(t), (x, z) -> 64);
            }
            assertEquals(ConstructionProject.Status.QUEUED, project.status(), "no builder yet, and still not cancelled after four days");
            assertTrue(queued.history().stream().noneMatch(e -> e.text().contains("not needed any more")));
        }
        Settlement hungry = settled("forest", 10);
        hungry.ledger().take(ResourceType.FOOD, 10_000);
        assertTrue(Planner.directives(hungry, 5, 200).stream().noneMatch(d -> d.target().equals("sawmill")), "needs first");
        assertTrue(Planner.directives(settled("desert", 10), 5, 200).stream().noneMatch(d ->
                d.target().equals("sawmill") || d.target().equals("forge") || d.target().equals("granary")), "all-round: none");
        assertTrue(Planner.directives(settled("forest", 10), 5, 200).stream().filter(d -> d.target().equals("sawmill"))
                .allMatch(d -> d.tier() == Planner.Tier.GROWTH && d.reason().contains("timber village")));
    }

    @Test
    void theSignatureBuildingDoesNotHideTheNeedForAHouseWhenEveryBedIsTaken() {
        Settlement s = settled("forest", 10);
        s.housing().setChunk(0, 0, 10); // exactly as many beds as people
        List<Planner.Directive> directives = Planner.directives(s, 5, 200);
        assertTrue(directives.stream().anyMatch(d -> d.target().equals("house") && d.reason().contains("every bed is taken")), directives.toString());
        assertTrue(directives.stream().anyMatch(d -> d.target().equals("sawmill")), directives.toString());
    }

    @Test
    void theVillageBuildsIt() {
        Settlement s = settled("forest", 10);
        Optional<ConstructionProject> project = Construction.propose(s, 5, "plains", 200, catalog,
                t -> catalog.blueprint(t), (x, z) -> 64);
        assertTrue(project.isPresent(), s.history().toString());
        assertEquals(BuildingType.SAWMILL, project.get().type());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("sawmill")));
    }

    @Test
    void aSawmillMakesLumberjacksWoodGoAQuarterFurther() {
        int plain = woodMade(false);
        int sawmill = woodMade(true);
        assertTrue(sawmill > plain * 1.15, "sawmill " + sawmill + " against " + plain);
    }

    /** Wood made by four lumberjacks in a month (the same village id, so the same dice). */
    private int woodMade(boolean withSawmill) {
        Settlement s = new Settlement(SAME, "Sawham", "world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(new Resident(new UUID(98, i), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50), Occupation.LUMBERJACK, true,
                    10_000, null, null, Needs.initial()));
        }
        s.ledger().add(Commodity.PRODUCE, 5000);
        if (withSawmill) {
            s.registerBuilding(new Building(BuildingType.SAWMILL, 0, 64, 0, 0, "test"));
        }
        int made = 0;
        for (long day = 1; day <= 30; day++) {
            s.ledger().take(ResourceType.WOOD, 10_000);
            simulator.simulateDay(s, day);
            made += s.ledger().get(ResourceType.WOOD);
        }
        return made;
    }

    @Test
    void aForgeLetsEachSmithSmeltFourMoreOreADay() {
        for (boolean forge : new boolean[] {false, true}) {
            Settlement s = new Settlement(SAME, "Forgeham", "world", 0, 0, 0);
            s.addResident(person(Occupation.TOOLSMITH));
            s.ledger().add(Commodity.RAW_IRON, 50);
            s.ledger().add(Commodity.COAL, 10);
            if (forge) {
                s.registerBuilding(new Building(BuildingType.FORGE, 0, 64, 0, 0, "test"));
            }
            SettlementSimulator.process(s, 1);
            assertEquals(forge ? 12 : 8, s.ledger().get(Commodity.IRON), forge ? "with a forge: eight and four more" : "without: eight");
        }
    }

    @Test
    void aGranaryHalvesHowFastFoodSpoils() {
        int[] left = new int[2];
        for (int i = 0; i < 2; i++) {
            Settlement s = new Settlement(SAME, "Grainham", "world", 0, 0, 0);
            for (int p = 0; p < 20; p++) { // enough people that the storage limit (900) is not what is lost
                s.addResident(new Resident(new UUID(99, p), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50), Occupation.NITWIT, true,
                        10_000, null, null, Needs.initial()));
            }
            s.ledger().add(Commodity.BREAD, 700);
            if (i == 1) {
                s.registerBuilding(new Building(BuildingType.GRANARY, 0, 64, 0, 0, "test"));
            }
            simulator.simulateDay(s, 1);
            left[i] = s.ledger().get(ResourceType.FOOD);
        }
        int spoiledWithout = 700 - 40 - left[0]; // (twenty adults eat two each)
        int spoiledWith = 700 - 40 - left[1];
        assertEquals(13, spoiledWithout, "2% of 660");
        assertEquals(6, spoiledWith, "1% of 660");
    }

    @Test
    void theGeneratedDesignsExistInTwoTiersWithASignAndFitTheLot() {
        for (BuildingType type : new BuildingType[] {BuildingType.SAWMILL, BuildingType.FORGE, BuildingType.GRANARY}) {
            assertTrue(BuildingGenerator.generates(type));
            for (int tier = 1; tier <= BuildingGenerator.TIERS; tier++) {
                Blueprint b = BuildingGenerator.generate(type, tier).orElseThrow();
                assertTrue(b.sign().isPresent(), type + " tier " + tier + " has a sign spot");
                // They take a free lot (a spare house lot, 9 by 9, if none is reserved for them).
                assertTrue(b.width() <= 9 && b.depth() <= 9, type + " tier " + tier + " fits the smallest lot");
                assertFalse(b.blocks().isEmpty());
            }
            List<TemplateCatalog.Template> ladder = catalog.ladder(type, "plains");
            assertEquals(BuildingGenerator.TIERS, ladder.size());
            assertTrue(catalog.blueprint(ladder.get(0)).isPresent());
            assertTrue(BuildingType.fromSign(type.signText()).isPresent(), "a player can register one with a sign");
        }
        Blueprint granary = BuildingGenerator.generate(BuildingType.GRANARY, 1).orElseThrow();
        assertTrue(granary.materialCounts().containsKey("HAY_BLOCK"));
        Blueprint forge = BuildingGenerator.generate(BuildingType.FORGE, 1).orElseThrow();
        assertTrue(forge.materialCounts().containsKey("STONE_BRICKS"), "a forge is built of stone");
    }

    @Test
    void aFormatTwentyFourSaveStillLoads() {
        Settlement s = settled("forest", 10);
        java.util.Map<String, Object> old = new java.util.LinkedHashMap<>(SettlementCodec.encode(s));
        old.put("format", 24);
        assertEquals(Leaning.TIMBER, SettlementCodec.decode(old).leaning());
        assertEquals(0, SettlementCodec.decode(old).buildingCount(BuildingType.SAWMILL));
    }

    @Test
    void theNewBuildingsSurviveASave() {
        Settlement s = settled("forest", 10);
        s.registerBuilding(new Building(BuildingType.SAWMILL, 3000, 64, 3000, 0, "the builders"));
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(1, loaded.buildingCount(BuildingType.SAWMILL));
    }
}
