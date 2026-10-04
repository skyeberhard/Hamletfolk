package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** R4.6 and R4.18: the catalog, the generated buildings, biome substitution, cost and the diff. */
class TemplateCatalogTest {

    @Test
    void everyGeneratedTypeHasTwoTiersWithADoorwayAndASign() {
        for (BuildingType type : BuildingType.values()) {
            if (!BuildingGenerator.generates(type)) {
                assertTrue(BuildingGenerator.generate(type, 1).isEmpty(), type + " comes from vanilla");
                continue;
            }
            for (int tier = 1; tier <= BuildingGenerator.TIERS; tier++) {
                Blueprint b = BuildingGenerator.generate(type, tier).orElseThrow();
                assertTrue(b.sign().isPresent(), type + " tier " + tier + " has a sign");
                int door = b.width() / 2;
                String below = b.blocks().stream().filter(k -> k.x() == door && k.y() == 1 && k.z() == b.depth() - 2)
                        .map(Blueprint.Block::material).findFirst().orElse("?");
                assertEquals("AIR", below, "doorway");
                for (Blueprint.Block k : b.blocks()) {
                    assertTrue(k.x() >= 0 && k.x() < b.width() && k.z() >= 0 && k.z() < b.depth(), "in footprint: " + k);
                }
            }
        }
    }

    @Test
    void theSolidTierIsBiggerAndCostsMore() {
        for (BuildingType type : List.of(BuildingType.MINE, BuildingType.GUARD_POST, BuildingType.SHOP,
                BuildingType.TREASURY)) {
            Blueprint crude = BuildingGenerator.generate(type, 1).orElseThrow();
            Blueprint solid = BuildingGenerator.generate(type, 2).orElseThrow();
            assertTrue(solid.width() > crude.width());
            int crudeTotal = crude.cost().values().stream().mapToInt(Integer::intValue).sum();
            int solidTotal = solid.cost().values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(solidTotal > crudeTotal, type + " " + solidTotal + " > " + crudeTotal);
        }
    }

    @Test
    void aCrudeMineNeedsWoodAndStoneButNoMetalOrFood() {
        Map<ResourceType, Integer> cost = BuildingGenerator.generate(BuildingType.MINE, 1).orElseThrow().cost();
        assertTrue(cost.get(ResourceType.WOOD) > 0);
        assertTrue(cost.get(ResourceType.STONE) > 0);
        assertFalse(cost.containsKey(ResourceType.METAL));
        assertFalse(cost.containsKey(ResourceType.FOOD));
    }

    @Test
    void slabsCostHalfABlockAndFittingsAreFree() {
        Blueprint b = new Blueprint("t", 2, 2, 1, List.of(
                new Blueprint.Block(0, 0, 0, "OAK_SLAB"), new Blueprint.Block(1, 0, 0, "OAK_SLAB"),
                new Blueprint.Block(0, 1, 0, "LANTERN"), new Blueprint.Block(1, 1, 0, "AIR")));
        assertEquals(Map.of(ResourceType.WOOD, 1), b.cost());
    }

    @Test
    void biomeSubstitutionSwapsMaterialsAndKeepsBlockStates() {
        Blueprint plains = BuildingGenerator.generate(BuildingType.MINE, 1).orElseThrow();
        assertTrue(plains.materialCounts().containsKey("OAK_PLANKS"));
        Blueprint desert = plains.inBiome(BiomeSet.DESERT);
        assertFalse(desert.materialCounts().containsKey("OAK_PLANKS"));
        assertTrue(desert.materialCounts().containsKey("SANDSTONE"));
        assertTrue(desert.materialCounts().containsKey("LADDER[facing=south]"));
        assertTrue(desert.sign().orElseThrow().material().startsWith("ACACIA_WALL_SIGN"));
        assertTrue(plains.inBiome(BiomeSet.TAIGA).materialCounts().containsKey("SPRUCE_PLANKS"));
        assertTrue(plains.inBiome(BiomeSet.SAVANNA).materialCounts().containsKey("ACACIA_PLANKS"));
        assertTrue(desert.cost().get(ResourceType.STONE) > plains.cost().get(ResourceType.STONE),
                "a desert building is made of sandstone");
    }

    @Test
    void unknownBiomesAreTreatedAsPlains() {
        assertEquals("plains", BiomeSet.normalize(null));
        assertEquals("plains", BiomeSet.normalize("jungle"));
        assertEquals("snowy", BiomeSet.forBiome("minecraft:snowy_taiga"));
        assertEquals("taiga", BiomeSet.forBiome("old_growth_pine_taiga"));
        assertEquals("desert", BiomeSet.forBiome("minecraft:badlands"));
        assertEquals("plains", BiomeSet.forBiome("minecraft:swamp"));
    }

    @Test
    void laddersFollowWhatVanillaShips() {
        TemplateCatalog catalog = new TemplateCatalog();
        assertEquals(3, catalog.ladder(BuildingType.HOUSE, "plains").size());
        assertEquals(2, catalog.ladder(BuildingType.HOUSE, "desert").size(), "no big desert house");
        assertEquals(1, catalog.ladder(BuildingType.FARM, "snowy").size(), "no large snowy farm");
        assertEquals(2, catalog.ladder(BuildingType.FARM, "taiga").size());
        assertEquals("minecraft:village/plains/houses/plains_small_house_1",
                catalog.ladder(BuildingType.HOUSE, "plains").get(0).key());
        assertEquals(TemplateCatalog.Source.GENERATED, catalog.ladder(BuildingType.SHOP, "savanna").get(0).source());
        assertEquals(2, catalog.ladder(BuildingType.TREASURY, null).size());
    }

    @Test
    void aCapturedBuildingReplacesTheGeneratedOne() {
        TemplateCatalog catalog = new TemplateCatalog();
        Blueprint mine = new Blueprint("mine", 3, 3, 3, List.of(new Blueprint.Block(0, 0, 0, "COBBLESTONE")));
        catalog.capture(BuildingType.MINE, "plains", 1, mine);
        TemplateCatalog.Template rung = catalog.ladder(BuildingType.MINE, "plains").get(0);
        assertEquals(TemplateCatalog.Source.CAPTURED, rung.source());
        assertEquals(mine, catalog.blueprint(rung).orElseThrow());
        assertEquals(TemplateCatalog.Source.GENERATED, catalog.ladder(BuildingType.MINE, "plains").get(1).source());
        assertEquals(TemplateCatalog.Source.GENERATED, catalog.ladder(BuildingType.MINE, "desert").get(0).source());
        assertTrue(catalog.uncapture(BuildingType.MINE, "plains", 1));
        assertEquals(TemplateCatalog.Source.GENERATED, catalog.ladder(BuildingType.MINE, "plains").get(0).source());
    }

    @Test
    void generatedRungsCarryTheirStyleBlueprint() {
        TemplateCatalog catalog = new TemplateCatalog();
        TemplateCatalog.Template rung = catalog.ladder(BuildingType.GUARD_POST, "desert").get(0);
        Blueprint b = catalog.blueprint(rung).orElseThrow();
        assertNotNull(b);
        assertTrue(b.materialCounts().containsKey("SANDSTONE"));
        assertTrue(catalog.blueprint(catalog.ladder(BuildingType.HOUSE, "plains").get(0)).isEmpty());
    }

    @Test
    void bestAffordableTakesTheHighestRungTheStockPaysFor() {
        TemplateCatalog catalog = new TemplateCatalog();
        List<TemplateCatalog.Template> ladder = catalog.ladder(BuildingType.MINE, "plains");
        java.util.function.Function<TemplateCatalog.Template, Map<ResourceType, Integer>> cost =
                t -> catalog.blueprint(t).orElseThrow().cost();
        Map<ResourceType, Integer> crude = cost.apply(ladder.get(0));
        Map<ResourceType, Integer> solid = cost.apply(ladder.get(1));

        assertTrue(TemplateCatalog.bestAffordable(ladder, 0, cost, Map.of()).isEmpty());
        assertEquals(1, TemplateCatalog.bestAffordable(ladder, 0, cost, crude).orElseThrow().tier());
        assertEquals(2, TemplateCatalog.bestAffordable(ladder, 0, cost, solid).orElseThrow().tier());
        Optional<TemplateCatalog.Template> already = TemplateCatalog.bestAffordable(ladder, 2, cost, solid);
        assertTrue(already.isEmpty(), "nothing above the top rung");
    }

    @Test
    void theDiffLeavesOutWhatIsAlreadyThereAndPricesTheRest() {
        Blueprint b = BuildingGenerator.generate(BuildingType.GUARD_POST, 1).orElseThrow();
        List<Blueprint.Block> all = b.diff(block -> "AIR");
        assertTrue(all.stream().noneMatch(k -> k.material().equals("AIR")), "air over air is not work");
        assertEquals(b.cost(), Blueprint.costOf(all));
        // The floor and walls already stand: only the rest is left to buy.
        List<Blueprint.Block> rest = b.diff(block -> block.y() <= 3 ? block.material() : "AIR");
        assertTrue(rest.size() < all.size());
        assertTrue(rest.stream().allMatch(k -> k.y() >= 4 || k.material().equals("AIR")));
        assertTrue(b.diff(Blueprint.Block::material).isEmpty(), "finished building needs nothing");
    }
}
