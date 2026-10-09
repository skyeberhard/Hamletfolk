package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R8.13: the scholarly, craft and trading leanings, read from what the survey counts. */
class LeaningCountsTest {
    private int next = 1;
    private int villages = 1;

    private Settlement village(String biome, int people) {
        Settlement s = new Settlement(new UUID(77, villages++), "Countham", "world", 0, 0, 0);
        for (int i = 0; i < people; i++) {
            s.addResident(new Resident(new UUID(78, next++), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50), Occupation.NITWIT, true,
                    10_000, null, null, Needs.initial()));
        }
        s.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> biome)));
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.ledger().add(Commodity.BREAD, 5000);
        s.ledger().add(Commodity.PLANKS, 800);
        s.ledger().add(Commodity.COBBLESTONE, 800);
        s.housing().setChunk(0, 0, people + 2);
        s.registerBuilding(new Building(BuildingType.MINE, 2000, 64, 2000, 0, "a player"));
        s.registerBuilding(new Building(BuildingType.SHOP, 2010, 64, 2000, 0, "a player"));
        s.registerBuilding(new Building(BuildingType.TREASURY, 2020, 64, 2000, 0, "a player"));
        return s;
    }

    private static void counted(Settlement s, Map<LandCounts.Feature, Integer> counts) {
        LandCounts.record(s, counts, 1);
    }

    private static boolean asks(Settlement s, BuildingType type) {
        return Planner.directives(s, 5, 200).stream().anyMatch(d -> d.target().equals(type.name().toLowerCase()));
    }

    @Test
    void theNewLeaningsComeAfterAllRoundSoSavedOnesKeepTheirNumbers() {
        assertEquals(5, Leaning.ALL_ROUND.ordinal());
        assertEquals(6, Leaning.SCHOLARLY.ordinal());
        assertEquals(Leaning.TIMBER, Leaning.fromSave(1));
        assertEquals(Leaning.ALL_ROUND, Leaning.fromSave(6));
        assertEquals(Leaning.TRADING, Leaning.fromSave(9));
    }

    @Test
    void sugarCaneAndCattleMakeAVillageScholarlySandMakesItCraftAndManyKindsMakeItTrading() {
        Settlement scholar = village("desert", 10);
        counted(scholar, Map.of(LandCounts.Feature.SUGAR_CANE, 16, LandCounts.Feature.CATTLE, 4));
        assertEquals(Leaning.SCHOLARLY, scholar.leaning());

        Settlement paperOnly = village("desert", 10);
        counted(paperOnly, Map.of(LandCounts.Feature.SUGAR_CANE, 30, LandCounts.Feature.CATTLE, 3));
        assertEquals(Leaning.ALL_ROUND, paperOnly.leaning(), "cane without enough cattle");

        Settlement glass = village("desert", 10);
        counted(glass, Map.of(LandCounts.Feature.SAND, 60));
        assertEquals(Leaning.CRAFT, glass.leaning());
        Settlement fewer = village("desert", 10);
        counted(fewer, Map.of(LandCounts.Feature.SAND, 59));
        assertEquals(Leaning.ALL_ROUND, fewer.leaning());

        Settlement crossroads = village("desert", 10);
        counted(crossroads, Map.of(LandCounts.Feature.WATER, 50, LandCounts.Feature.SHEEP, 5, LandCounts.Feature.HORSES, 3,
                LandCounts.Feature.BEES, 2));
        assertEquals(Leaning.TRADING, crossroads.leaning());
        Settlement three = village("desert", 10);
        counted(three, Map.of(LandCounts.Feature.WATER, 50, LandCounts.Feature.SHEEP, 5, LandCounts.Feature.HORSES, 3));
        assertEquals(Leaning.ALL_ROUND, three.leaning(), "three kinds are not a crossroads");

        Settlement both = village("desert", 10);
        counted(both, Map.of(LandCounts.Feature.SUGAR_CANE, 40, LandCounts.Feature.CATTLE, 8, LandCounts.Feature.SAND, 200));
        assertEquals(Leaning.SCHOLARLY, both.leaning(), "scholarly comes before craft");
    }

    @Test
    void theBiomesReadingAlwaysComesFirstAndAnUnsurveyedVillageIsAllRound() {
        Settlement forest = village("forest", 10);
        counted(forest, Map.of(LandCounts.Feature.SAND, 400));
        assertEquals(Leaning.TIMBER, forest.leaning(), "a forest village stays a timber one");
        Settlement unread = new Settlement(UUID.randomUUID(), "Blank", "world", 0, 0, 0);
        counted(unread, Map.of(LandCounts.Feature.SAND, 400));
        assertEquals(Leaning.ALL_ROUND, unread.leaning(), "not read yet: no leaning at all");
        assertFalse(unread.hasLeaning());
    }

    @Test
    void eachCallsForItsOwnBuilding() {
        assertEquals(BuildingType.MAP_ROOM, Leaning.SCHOLARLY.signature());
        assertEquals(BuildingType.GLASSWORKS, Leaning.CRAFT.signature());
        assertEquals(BuildingType.TRADING_POST, Leaning.TRADING.signature());
        assertTrue(Leaning.SCHOLARLY.favours(Occupation.LIBRARIAN) && Leaning.SCHOLARLY.favours(Occupation.CARTOGRAPHER));
        assertTrue(Leaning.CRAFT.favours(Occupation.GLASSBLOWER));
        assertEquals(Construction.Direction.SCHOLARLY, Construction.direction(scholarVillage(), 5));
        assertTrue(Construction.Direction.SCHOLARLY.serves(BuildingType.MAP_ROOM) && Construction.Direction.SCHOLARLY.serves(BuildingType.LIBRARY));
        assertTrue(Construction.Direction.CRAFT.serves(BuildingType.GLASSWORKS));
        assertTrue(Construction.Direction.TRADE.serves(BuildingType.TRADING_POST));
    }

    private Settlement scholarVillage() {
        Settlement s = village("desert", 10);
        counted(s, Map.of(LandCounts.Feature.SUGAR_CANE, 20, LandCounts.Feature.CATTLE, 6));
        return s;
    }

    @Test
    void aScholarlyVillageAsksForMapsThenALibraryOnceItIsATown() {
        Settlement s = scholarVillage();
        assertTrue(asks(s, BuildingType.MAP_ROOM), "cane opens the cartographer");
        assertFalse(asks(s, BuildingType.LIBRARY), "a library needs a town of 20");
        s.registerBuilding(new Building(BuildingType.MAP_ROOM, 3000, 64, 3000, 0, "the builders"));
        assertFalse(asks(s, BuildingType.MAP_ROOM));
        Settlement town = village("desert", 20);
        counted(town, Map.of(LandCounts.Feature.SUGAR_CANE, 20, LandCounts.Feature.CATTLE, 6));
        town.registerBuilding(new Building(BuildingType.MAP_ROOM, 3000, 64, 3000, 0, "the builders"));
        assertTrue(asks(town, BuildingType.LIBRARY));
    }

    @Test
    void aCraftVillageAsksForAGlassworksAndATradingOneForAPostWithoutMerchants() {
        Settlement craft = village("desert", 10);
        counted(craft, Map.of(LandCounts.Feature.SAND, 90));
        assertTrue(asks(craft, BuildingType.GLASSWORKS));
        long asked = Planner.directives(craft, 5, 200).stream().filter(d -> d.target().equals("glassworks")).count();
        assertEquals(1, asked, "asked for once");

        Settlement trading = village("desert", 10);
        counted(trading, Map.of(LandCounts.Feature.WATER, 50, LandCounts.Feature.SHEEP, 5, LandCounts.Feature.HORSES, 3,
                LandCounts.Feature.BEES, 2));
        assertTrue(asks(trading, BuildingType.TRADING_POST), "a crossroads wants its market even before it has a merchant");
        long posts = Planner.directives(trading, 5, 200).stream().filter(d -> d.target().equals("trading_post")).count();
        assertEquals(1, posts, "and only once");
        Settlement market = village("desert", 12); // with a merchant and a shop the other reason for a post applies too: still one ask
        counted(market, Map.of(LandCounts.Feature.WATER, 50, LandCounts.Feature.SHEEP, 5, LandCounts.Feature.HORSES, 3,
                LandCounts.Feature.BEES, 2));
        market.residents().iterator().next().setOccupation(Occupation.MERCHANT);
        int at = 3000;
        for (BuildingType type : new BuildingType[] {BuildingType.HARBOUR, BuildingType.PENS, BuildingType.STABLE, BuildingType.APIARY}) {
            market.registerBuilding(new Building(type, at += 10, 64, 3000, 0, "the builders")); // every open trade has its building
        }
        assertTrue(Trades.wanted(market).isEmpty());
        assertEquals(1, Planner.directives(market, 5, 200).stream().filter(d -> d.target().equals("trading_post")).count());
        Settlement hamlet = village("desert", 7);
        counted(hamlet, Map.of(LandCounts.Feature.WATER, 50, LandCounts.Feature.SHEEP, 5, LandCounts.Feature.HORSES, 3,
                LandCounts.Feature.BEES, 2));
        assertFalse(asks(hamlet, BuildingType.TRADING_POST), "seven is too few");
    }

    @Test
    void aLeaningTheCountsGiveIsWrittenUpOnce() {
        Settlement s = scholarVillage();
        Trades.noteOpened(s, 4);
        long notes = s.history().stream().filter(e -> e.text().contains("it is a scholarly village")).count();
        assertEquals(1, notes);
        Trades.noteOpened(s, 5);
        assertEquals(1, s.history().stream().filter(e -> e.text().contains("it is a scholarly village")).count());
        Settlement plain = village("desert", 10);
        Trades.noteOpened(plain, 4);
        assertTrue(plain.history().stream().noneMatch(e -> e.text().contains("village.")), "all-round says nothing");
    }

    @Test
    void theirTradesAreStaffedSooner() {
        assertEquals(SettlementSimulator.LEANING_BIAS, SettlementSimulator.leaningBias(scholarVillage(), Occupation.LIBRARIAN));
        assertEquals(1.0, SettlementSimulator.leaningBias(scholarVillage(), Occupation.GLASSBLOWER));
    }

    @Test
    void theLeaningFollowsASaveAndTheCharacterLinesMentionIt() {
        Settlement s = scholarVillage();
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(Leaning.SCHOLARLY, loaded.leaning());
        assertTrue(Dialogue.characterLines(loaded).stream().anyMatch(l -> l.contains("maps and books")));
    }
}
