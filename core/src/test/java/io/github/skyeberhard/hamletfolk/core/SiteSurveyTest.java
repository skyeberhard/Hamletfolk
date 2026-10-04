package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** R8.2: a site is scored from a coarse sample, with scarce resources becoming imports instead of a refusal. */
class SiteSurveyTest {
    private static List<SiteSurvey.Cell> land(java.util.function.BiFunction<Integer, Integer, String> biomeAt) {
        return SiteSurvey.grid(96, 24, biomeAt);
    }

    private static double distance(int dx, int dz) {
        return Math.sqrt((double) dx * dx + (double) dz * dz);
    }

    @Test
    void anOasisInADesertHasWaterButLittleElseAndMustImportWoodAndMetal() {
        SiteSurvey.Profile oasis = SiteSurvey.score(land((dx, dz) -> {
            double d = distance(dx, dz);
            return d <= 24 ? "river" : d <= 48 ? "plains" : "desert";
        }));
        assertTrue(oasis.score(SiteResource.WATER) > SiteSurvey.score(land((dx, dz) -> "desert")).score(SiteResource.WATER),
                "the spring is the whole reason for the village");
        assertTrue(oasis.imports().contains(ResourceType.WOOD), oasis.imports().toString());
        assertTrue(oasis.imports().contains(ResourceType.METAL), oasis.imports().toString());
        assertFalse(oasis.imports().contains(ResourceType.FOOD), "the ring of plains feeds it");
        assertFalse(oasis.notes().isEmpty());
    }

    @Test
    void aSwampIsAllWaterAndTimberAndNeedsStoneAndMetal() {
        SiteSurvey.Profile swamp = SiteSurvey.score(land((dx, dz) -> "swamp"));
        assertTrue(swamp.score(SiteResource.WATER) > 85, "" + swamp.score(SiteResource.WATER));
        assertTrue(swamp.imports().contains(ResourceType.STONE));
        assertTrue(swamp.imports().contains(ResourceType.METAL));
        assertFalse(swamp.imports().contains(ResourceType.WOOD));
        assertEquals(SiteSurvey.Growth.SLOW, swamp.growth(), "stone and metal both to bring in: it grows slowly");
    }

    @Test
    void aMountainWithSpringsHasStoneAndOreAndMustFeedItself() {
        SiteSurvey.Profile mountain = SiteSurvey.score(land((dx, dz) -> dx < -48 ? "river" : "windswept_hills"));
        assertTrue(mountain.score(SiteResource.STONE) > 80, "" + mountain.score(SiteResource.STONE));
        assertTrue(mountain.score(SiteResource.ORE) > 50, "" + mountain.score(SiteResource.ORE));
        assertTrue(mountain.imports().contains(ResourceType.FOOD));
        assertFalse(mountain.imports().contains(ResourceType.STONE));
        assertFalse(mountain.imports().contains(ResourceType.METAL));
        assertEquals(SiteSurvey.Growth.NORMAL, mountain.growth(), "one thing to bring in: food");
        assertTrue(mountain.foodBalance() < 0, "it cannot feed itself: " + mountain.foodBalance());
    }

    @Test
    void aBarePlainFeedsItselfWellButIsDryAndHasNothingElse() {
        SiteSurvey.Profile plain = SiteSurvey.score(land((dx, dz) -> "plains"));
        assertTrue(plain.foodBalance() > 0.5, "" + plain.foodBalance());
        assertTrue(plain.imports().containsAll(List.of(ResourceType.WOOD, ResourceType.STONE, ResourceType.METAL)));
        assertFalse(plain.imports().contains(ResourceType.FOOD));
        assertEquals(SiteSurvey.Growth.SLOW, plain.growth(), "hardly any water caps the growth");
        assertTrue(plain.notes().stream().anyMatch(n -> n.contains("water")), plain.notes().toString());
    }

    @Test
    void aSiteWithEverythingNearbyIsWellRoundedAndGrowsFast() {
        SiteSurvey.Profile rich = SiteSurvey.score(land((dx, dz) -> {
            int ring = Math.floorMod(dx / 24 + dz / 24, 6);
            return switch (ring) {
                case 0 -> "river";
                case 1 -> "forest";
                case 2 -> "plains";
                case 3 -> "windswept_hills";
                case 4 -> "badlands";
                default -> "meadow";
            };
        }));
        assertTrue(rich.imports().isEmpty(), "nothing is scarce: " + rich.imports());
        assertEquals(SiteSurvey.Growth.FAST, rich.growth(), "overall " + rich.overall());
        assertTrue(rich.notes().get(0).contains("well-rounded"));
    }

    @Test
    void nearerLandCountsForMoreThanLandAtTheEdge() {
        // The same single patch of river, at the centre and at the edge of a desert.
        SiteSurvey.Profile near = SiteSurvey.score(land((dx, dz) -> dx == 0 && dz == 0 ? "river" : "desert"));
        SiteSurvey.Profile far = SiteSurvey.score(land((dx, dz) -> dx == 96 && dz == 96 ? "river" : "desert"));
        assertTrue(near.score(SiteResource.WATER) > far.score(SiteResource.WATER));
    }

    @Test
    void samplesBeyondTheLimitAreIgnoredAndAnEmptySampleScoresNothing() {
        SiteSurvey.Profile onlyFar = SiteSurvey.score(List.of(new SiteSurvey.Cell(200, 0, "river")));
        assertEquals(0.0, onlyFar.score(SiteResource.WATER), 1e-9);
        SiteSurvey.Profile empty = SiteSurvey.score(List.of());
        assertEquals(0.0, empty.overall(), 1e-9);
        assertEquals(SiteSurvey.Growth.SLOW, empty.growth());
        assertFalse(empty.notes().isEmpty());
    }

    @Test
    void weightsChangeWhichSiteWinsAndAreNotAGate() {
        List<SiteSurvey.Cell> plains = land((dx, dz) -> "plains");
        Map<SiteResource, Double> farmersOnly = new EnumMap<>(SiteResource.class);
        for (SiteResource resource : SiteResource.values()) {
            farmersOnly.put(resource, resource == SiteResource.FARMLAND ? 10.0 : 0.1);
        }
        assertTrue(SiteSurvey.score(plains, farmersOnly).overall() > SiteSurvey.score(plains).overall(),
                "valuing farmland above all makes a plain look better");
        // A poor site is still scored, not refused: the score is advice and the starting conditions.
        assertTrue(SiteSurvey.score(land((dx, dz) -> "desert")).overall() >= 0);
    }

    @Test
    void openWaterIsNotBuildableAndSlowsGrowth() {
        SiteSurvey.Profile sea = SiteSurvey.score(land((dx, dz) -> dx > -24 ? "ocean" : "beach"));
        assertTrue(sea.buildable() < 0.5, "" + sea.buildable());
        assertEquals(SiteSurvey.Growth.SLOW, sea.growth());
        assertTrue(sea.notes().stream().anyMatch(n -> n.contains("open water")));
    }

    @Test
    void theSameSampleAlwaysGivesTheSameProfile() {
        java.util.function.BiFunction<Integer, Integer, String> mixed = (dx, dz) ->
                (dx * 31 + dz * 17) % 3 == 0 ? "forest" : (dx - dz) % 5 == 0 ? "river" : "plains";
        assertEquals(SiteSurvey.score(land(mixed)), SiteSurvey.score(land(mixed)));
    }

    @Test
    void theGridIsClampedToTheLimitAndStepsEvenly() {
        assertEquals(49, SiteSurvey.grid(96, 32, (dx, dz) -> "plains").size()); // -96..96 every 32: 7 by 7
        assertEquals(49, SiteSurvey.grid(500, 32, (dx, dz) -> "plains").size(), "never past 96 blocks");
    }

    @Test
    void biomeKeysAreReadWithOrWithoutTheirNamespace() {
        assertEquals(BiomeTable.resources("plains")[SiteResource.FARMLAND.ordinal()],
                BiomeTable.resources("minecraft:Plains")[SiteResource.FARMLAND.ordinal()], 1e-9);
        assertTrue(BiomeTable.known("minecraft:swamp"));
        assertFalse(BiomeTable.known("somemod:glowing_marsh"));
        assertEquals(0.2, BiomeTable.resources("somemod:glowing_marsh")[0], 1e-9, "an unknown biome is mildly useful");
        assertTrue(BiomeTable.buildable("plains"));
        assertFalse(BiomeTable.buildable("minecraft:deep_ocean"));
        // Every resource of every known biome is within 0 to 1.
        for (String biome : List.of("plains", "forest", "desert", "swamp", "river", "windswept_hills", "badlands", "grove")) {
            for (double value : BiomeTable.resources(biome)) {
                assertTrue(value >= 0 && value <= 1, biome);
            }
        }
    }
}
