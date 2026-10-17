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
    void anOasisInADesertHasWaterButLittleElseAndMustImportWood() {
        SiteSurvey.Profile oasis = SiteSurvey.score(land((dx, dz) -> {
            double d = distance(dx, dz);
            return d <= 24 ? "river" : d <= 48 ? "plains" : "desert";
        }));
        assertTrue(oasis.score(SiteResource.WATER) > SiteSurvey.score(land((dx, dz) -> "desert")).score(SiteResource.WATER),
                "the spring is the whole reason for the village");
        assertTrue(oasis.imports().contains(ResourceType.WOOD), oasis.imports().toString());
        assertFalse(oasis.imports().contains(ResourceType.METAL), "metal is under any land: a mine reaches it (R8.24)");
        assertFalse(oasis.imports().contains(ResourceType.FOOD), "the ring of plains feeds it");
        assertFalse(oasis.notes().isEmpty());
    }

    @Test
    void aSwampIsAllWaterAndTimberAndLacksOnlyWhatItMustDigFor() {
        SiteSurvey.Profile swamp = SiteSurvey.score(land((dx, dz) -> "swamp"));
        assertTrue(swamp.score(SiteResource.WATER) > 85, "" + swamp.score(SiteResource.WATER));
        assertFalse(swamp.imports().contains(ResourceType.STONE), "stone is under the swamp too");
        assertFalse(swamp.imports().contains(ResourceType.METAL));
        assertFalse(swamp.imports().contains(ResourceType.WOOD));
        assertEquals(SiteSurvey.Growth.FAST, swamp.growth(), "nothing to bring in: " + swamp.overall());
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
    void aBarePlainFeedsItselfWellButLacksTimberAndWater() {
        SiteSurvey.Profile plain = SiteSurvey.score(land((dx, dz) -> "plains"));
        assertTrue(plain.foodBalance() > 0.5, "" + plain.foodBalance());
        assertTrue(plain.imports().contains(ResourceType.WOOD));
        assertFalse(plain.imports().contains(ResourceType.STONE), "a mine reaches stone and metal under any land");
        assertFalse(plain.imports().contains(ResourceType.METAL));
        assertFalse(plain.imports().contains(ResourceType.FOOD));
        assertEquals(SiteSurvey.Growth.NORMAL, plain.growth(), "one thing to bring in: wood");
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

    // ----- R8.24: the survey reads what is there -----

    @Test
    void theOverallScoreSpreadsFromOceanAndDesertToAWellRoundedSite() {
        double ocean = SiteSurvey.score(land((dx, dz) -> "deep_ocean")).overall();
        double desert = SiteSurvey.score(land((dx, dz) -> "desert")).overall();
        double badlands = SiteSurvey.score(land((dx, dz) -> "badlands")).overall();
        double plains = SiteSurvey.score(land((dx, dz) -> "plains")).overall();
        double forest = SiteSurvey.score(land((dx, dz) -> "forest")).overall();
        double mixed = SiteSurvey.score(land((dx, dz) -> switch (Math.floorMod(dx / 24 + dz / 24, 6)) {
            case 0 -> "river";
            case 1 -> "forest";
            case 2 -> "plains";
            case 3 -> "windswept_hills";
            case 4 -> "badlands";
            default -> "meadow";
        })).overall();
        assertTrue(ocean < 25, "ocean " + ocean);
        assertTrue(desert < 30, "desert " + desert);
        assertTrue(badlands > desert && badlands < 50, "badlands " + badlands);
        assertTrue(plains >= 55 && plains <= 80, "plains " + plains + ": it was 40, the most any biome could reach");
        assertTrue(forest >= 55 && forest <= 80, "forest " + forest);
        assertTrue(mixed >= 70, "mixed " + mixed);
        assertTrue(mixed > plains && plains > badlands && badlands > desert && desert > ocean);
    }

    @Test
    void stoneAndOreAreUnderAnyLandButNotUnderTheSea() {
        SiteSurvey.Profile plains = SiteSurvey.score(land((dx, dz) -> "plains"));
        assertEquals(100.0, plains.reach(SiteResource.STONE), 1e-9, "stone to quarry under the plain");
        assertTrue(plains.reach(SiteResource.ORE) > 40, "" + plains.reach(SiteResource.ORE));
        assertTrue(plains.score(SiteResource.STONE) < 20, "but the plain does not stand out for it: " + plains.score(SiteResource.STONE));
        SiteSurvey.Profile sea = SiteSurvey.score(land((dx, dz) -> "ocean"));
        assertEquals(0.0, sea.reach(SiteResource.STONE), 1e-9);
        assertTrue(sea.imports().contains(ResourceType.STONE));
    }

    @Test
    void whatIsSeenOnTheGroundCountsForWhatIsWithinReachWhereTheBiomeDoesNotKnowIt() {
        // desert with a lake and a grove, seen
        SiteSurvey.Observed oasis = new SiteSurvey.Observed(0.2, 0.2, 0.0);
        SiteSurvey.Profile seen = SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "desert", (dx, dz) -> oasis));
        SiteSurvey.Profile unseen = SiteSurvey.score(land((dx, dz) -> "desert"));
        assertTrue(seen.reach(SiteResource.WATER) > 95, "" + seen.reach(SiteResource.WATER));
        assertTrue(seen.reach(SiteResource.LUMBER) > 95);
        assertFalse(seen.imports().contains(ResourceType.WOOD));
        assertTrue(unseen.imports().contains(ResourceType.WOOD));
        assertTrue(seen.overall() > unseen.overall() + 20, seen.overall() + " against " + unseen.overall());
    }

    /** A village's character must not depend on which chunks are loaded when it is read, or on what players have built. */
    @Test
    void whatIsSeenNeverChangesTheScoresThatMakeAVillagesCharacter() {
        SiteSurvey.Observed lake = new SiteSurvey.Observed(0.5, 0.5, 0.5);
        for (String biome : List.of("plains", "forest", "windswept_hills", "desert", "swamp")) {
            SiteSurvey.Profile bare = SiteSurvey.score(land((dx, dz) -> biome));
            SiteSurvey.Profile seen = SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> biome, (dx, dz) -> lake));
            for (SiteResource r : SiteResource.values()) {
                assertEquals(bare.score(r), seen.score(r), 1e-9, biome + " " + r);
            }
            assertEquals(Leaning.of(bare), Leaning.of(seen), biome);
        }
        // and a partly seen sample is the same
        assertEquals(SiteSurvey.score(land((dx, dz) -> "plains")).score(SiteResource.WATER),
                SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "plains", (dx, dz) -> dx > 0 ? lake : null)).score(SiteResource.WATER), 1e-9);
    }

    @Test
    void aCellNeverSeenScoresLikeOneWithNoObservationAndSharesAreClamped() {
        List<SiteSurvey.Cell> plain = land((dx, dz) -> "forest");
        List<SiteSurvey.Cell> asNull = plain.stream().map(c -> new SiteSurvey.Cell(c.dx(), c.dz(), c.biome(), null)).toList();
        assertEquals(SiteSurvey.score(plain).overall(), SiteSurvey.score(asNull).overall(), 1e-9);
        SiteSurvey.Observed wild = new SiteSurvey.Observed(7.0, -3.0, 40.0);
        SiteSurvey.Profile p = SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "desert", (dx, dz) -> wild));
        assertTrue(p.overall() >= 0 && p.overall() <= 100, "" + p.overall());
        for (SiteResource r : SiteResource.values()) {
            assertTrue(p.reach(r) >= 0 && p.reach(r) <= 100, r + " " + p.reach(r));
        }
        assertEquals(0.0, p.reach(SiteResource.FARMLAND), 1e-9, "nothing seen of it, and a desert has none");
    }

    @Test
    void thinWaterIsWorthAWordButScarceLandIsListedOnlyBelowTheLine() {
        // a little river at the centre of a desert: some water within reach, not a lot
        SiteSurvey.Profile damp = SiteSurvey.score(land((dx, dz) -> dz == 0 && Math.abs(dx) <= 24 ? "river" : "desert"));
        double w = damp.reach(SiteResource.WATER);
        assertTrue(w >= 15 && w < 30, "water " + w);
        assertTrue(damp.notes().stream().anyMatch(n -> n.contains("Not much water about")), damp.notes().toString());
        assertTrue(SiteSurvey.Growth.SLOW != SiteSurvey.score(land((dx, dz) -> "plains")).growth());
        SiteSurvey.Profile dry = SiteSurvey.score(land((dx, dz) -> "desert"));
        assertTrue(dry.notes().stream().anyMatch(n -> n.contains("Hardly any water")), dry.notes().toString());
        // lumber exactly on the line: a tenth of the land in plains is 20, so wood is listed as scarce; a quarter forest is not
        SiteSurvey.Profile plains = SiteSurvey.score(land((dx, dz) -> "plains"));
        assertTrue(plains.reach(SiteResource.LUMBER) < SiteSurvey.SCARCE && plains.imports().contains(ResourceType.WOOD));
        SiteSurvey.Profile grove = SiteSurvey.score(land((dx, dz) -> Math.floorMod(dx / 24 + dz / 24, 2) == 0 ? "forest" : "plains"));
        assertTrue(grove.reach(SiteResource.LUMBER) >= SiteSurvey.SCARCE && !grove.imports().contains(ResourceType.WOOD), "" + grove.reach(SiteResource.LUMBER));
    }

    @Test
    void theLeaningStillFollowsWhatStandsOutNotWhatIsReachable() {
        // every land has stone and ore underground, but only a mountain stands out for them
        assertEquals(Leaning.FARMING, Leaning.of(SiteSurvey.score(land((dx, dz) -> "plains"))));
        assertEquals(Leaning.TIMBER, Leaning.of(SiteSurvey.score(land((dx, dz) -> "forest"))));
        assertEquals(Leaning.MINING, Leaning.of(SiteSurvey.score(land((dx, dz) -> "windswept_hills"))));
    }
}
