package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R8.13: the trades the land opens, the buildings that hold them, and what each does. */
class LandTradesTest {
    private final TemplateCatalog catalog = new TemplateCatalog();
    private int next = 1;
    private int villages = 1; // (fixed ids, so the simulator's draws are the same every run)

    private Resident person(Occupation job) {
        return new Resident(new UUID(98, next++), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50), job, true, 10_000, null, null,
                Needs.initial());
    }

    /** A fed, housed village of {@code people} on desert land (so no leaning building competes), with a mine, shop and treasury. */
    private Settlement village(int people, Occupation job) {
        Settlement s = new Settlement(new UUID(99, villages++), "Landham", "world", 0, 0, 0);
        for (int i = 0; i < people; i++) {
            s.addResident(person(job));
        }
        s.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "desert")));
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

    private static void survey(Settlement s, LandCounts.Feature feature, int n) {
        LandCounts.record(s, Map.of(feature, n), s.lastSimulatedDay());
    }

    // ----- the counts -----

    @Test
    void theSurveyCountsAreKeptAndOnlyEverRaised() {
        Settlement s = village(10, Occupation.NITWIT);
        assertFalse(LandCounts.surveyed(s));
        assertEquals(0, LandCounts.get(s, LandCounts.Feature.SHEEP));
        Map<LandCounts.Feature, Integer> found = new EnumMap<>(LandCounts.Feature.class);
        found.put(LandCounts.Feature.SHEEP, 5);
        found.put(LandCounts.Feature.WATER, 60);
        LandCounts.record(s, found, 12);
        assertTrue(LandCounts.surveyed(s));
        assertEquals(12, LandCounts.surveyedDay(s));
        assertEquals(5, LandCounts.get(s, LandCounts.Feature.SHEEP));
        found.put(LandCounts.Feature.SHEEP, 2); // a survey with some chunks unloaded
        found.put(LandCounts.Feature.WATER, 0);
        LandCounts.record(s, found, 30);
        assertEquals(5, LandCounts.get(s, LandCounts.Feature.SHEEP), "a lower count does not close a trade that was open");
        assertEquals(60, LandCounts.get(s, LandCounts.Feature.WATER));
        assertEquals(30, LandCounts.surveyedDay(s));
        assertEquals("5 sheep, 60 water", LandCounts.describe(s));
        found.put(LandCounts.Feature.SHEEP, 9);
        LandCounts.record(s, found, 31);
        assertEquals(9, LandCounts.get(s, LandCounts.Feature.SHEEP));
    }

    @Test
    void theCountsSurviveASave() {
        Settlement s = village(10, Occupation.NITWIT);
        survey(s, LandCounts.Feature.HORSES, 4);
        s.registerBuilding(new Building(BuildingType.STABLE, 3000, 64, 3000, 0, "the builders"));
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(4, LandCounts.get(loaded, LandCounts.Feature.HORSES));
        assertEquals(1, loaded.buildingCount(BuildingType.STABLE));
    }

    @Test
    void aFormatTwentySixSaveStillLoads() {
        Settlement s = village(10, Occupation.NITWIT);
        Map<String, Object> old = new LinkedHashMap<>(SettlementCodec.encode(s));
        old.put("format", 26);
        Settlement loaded = SettlementCodec.decode(old);
        assertEquals(10, loaded.population());
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(loaded).get("format"));
        assertEquals(0, LandCounts.get(loaded, LandCounts.Feature.SHEEP), "an old village is simply unsurveyed");
    }

    // ----- what opens a trade -----

    @Test
    void eachTradeOpensAtItsOwnCountAndSize() {
        Object[][] cases = {
                {Occupation.FISHERMAN, new LandCounts.Feature[] {LandCounts.Feature.WATER}, 40, 8},
                {Occupation.SHEPHERD, new LandCounts.Feature[] {LandCounts.Feature.SHEEP}, 4, 8},
                {Occupation.BUTCHER, new LandCounts.Feature[] {LandCounts.Feature.PIGS}, 6, 8},
                {Occupation.LEATHERWORKER, new LandCounts.Feature[] {LandCounts.Feature.CATTLE}, 4, 8},
                {Occupation.HORSE_TRAINER, new LandCounts.Feature[] {LandCounts.Feature.HORSES}, 3, 8},
                {Occupation.BEEKEEPER, new LandCounts.Feature[] {LandCounts.Feature.BEES}, 2, 8},
                {Occupation.CARTOGRAPHER, new LandCounts.Feature[] {LandCounts.Feature.SUGAR_CANE}, 16, 8},
                {Occupation.GLASSBLOWER, new LandCounts.Feature[] {LandCounts.Feature.SAND}, 30, 8}};
        for (Object[] c : cases) {
            Occupation trade = (Occupation) c[0];
            LandCounts.Feature feature = ((LandCounts.Feature[]) c[1])[0];
            int needed = (Integer) c[2];
            int size = (Integer) c[3];
            Settlement enough = village(size, Occupation.NITWIT);
            survey(enough, feature, needed);
            assertTrue(Trades.opens(enough, trade), trade + " opens at " + needed + " " + feature);
            Settlement few = village(size, Occupation.NITWIT);
            survey(few, feature, needed - 1);
            assertFalse(Trades.opens(few, trade), trade + " does not open at " + (needed - 1));
            Settlement small = village(size - 1, Occupation.NITWIT);
            survey(small, feature, needed * 3);
            assertFalse(Trades.opens(small, trade), trade + " waits for a village of " + size);
        }
    }

    @Test
    void aButcherCountsCattlePigsAndChickensTogether() {
        Settlement s = village(10, Occupation.NITWIT);
        LandCounts.record(s, Map.of(LandCounts.Feature.CATTLE, 2, LandCounts.Feature.PIGS, 2, LandCounts.Feature.CHICKENS, 2), 1);
        assertTrue(Trades.opens(s, Occupation.BUTCHER));
        assertFalse(Trades.opens(s, Occupation.LEATHERWORKER), "four cattle are wanted for leather, not two");
    }

    @Test
    void aLibrarianNeedsATownPaperAndCattle() {
        Settlement town = village(20, Occupation.NITWIT);
        LandCounts.record(town, Map.of(LandCounts.Feature.SUGAR_CANE, 8, LandCounts.Feature.CATTLE, 2), 1);
        assertTrue(Trades.opens(town, Occupation.LIBRARIAN));
        Settlement village = village(19, Occupation.NITWIT);
        LandCounts.record(village, Map.of(LandCounts.Feature.SUGAR_CANE, 40, LandCounts.Feature.CATTLE, 9), 1);
        assertFalse(Trades.opens(village, Occupation.LIBRARIAN), "a village is too small for a library");
        Settlement noCattle = village(20, Occupation.NITWIT);
        LandCounts.record(noCattle, Map.of(LandCounts.Feature.SUGAR_CANE, 40), 1);
        assertFalse(Trades.opens(noCattle, Occupation.LIBRARIAN));
        assertFalse(Trades.opens(town, Occupation.FARMER), "only the land trades");
    }

    @Test
    void everyLandTradeHasABuildingThatEmploysIt() {
        for (Occupation trade : Trades.landTrades()) {
            BuildingType building = Trades.buildingFor(trade).orElseThrow();
            assertEquals(Optional.of(trade), building.job(), building + " employs the " + trade);
            assertEquals(Optional.of(building), BuildingType.fromSign(building.signText()), "its sign registers it");
        }
        assertEquals(9, Trades.landTrades().size());
        assertTrue(Occupation.BEEKEEPER.simOwned() && Occupation.HORSE_TRAINER.simOwned() && Occupation.GLASSBLOWER.simOwned());
    }

    // ----- asking for the building -----

    private static boolean asks(Settlement s, BuildingType type) {
        return Planner.directives(s, 5, 200).stream().anyMatch(d -> d.target().equals(type.name().toLowerCase()));
    }

    @Test
    void anOpenTradeWithNoBuildingIsAskedForAndOnlyUntilItStands() {
        Settlement s = village(10, Occupation.NITWIT);
        assertFalse(asks(s, BuildingType.PENS), "nothing to shear yet");
        survey(s, LandCounts.Feature.SHEEP, 6);
        assertTrue(asks(s, BuildingType.PENS));
        String reason = Planner.directives(s, 5, 200).stream().filter(d -> d.target().equals("pens")).findFirst().orElseThrow().reason();
        assertTrue(reason.contains("sheep") && reason.contains("shepherd"), reason);
        s.registerBuilding(new Building(BuildingType.PENS, 3000, 64, 3000, 0, "the builders"));
        assertFalse(asks(s, BuildingType.PENS), "one is enough");

        Settlement hamlet = village(7, Occupation.NITWIT);
        survey(hamlet, LandCounts.Feature.SHEEP, 20);
        assertFalse(asks(hamlet, BuildingType.PENS), "seven is too few");

        Settlement hungry = village(10, Occupation.NITWIT);
        survey(hungry, LandCounts.Feature.SHEEP, 20);
        hungry.ledger().take(ResourceType.FOOD, 100_000);
        assertFalse(asks(hungry, BuildingType.PENS), "food comes first");
    }

    @Test
    void severalOpenTradesAreAllAskedForSoOneThatCannotBePaidForHidesNoOther() {
        Settlement s = village(12, Occupation.NITWIT);
        LandCounts.record(s, Map.of(LandCounts.Feature.SHEEP, 6, LandCounts.Feature.HORSES, 5, LandCounts.Feature.SAND, 90), 1);
        List<BuildingType> wanted = Trades.wanted(s);
        assertEquals(List.of(BuildingType.PENS, BuildingType.STABLE, BuildingType.GLASSWORKS), wanted);
        List<String> asked = Planner.directives(s, 5, 200).stream().map(Planner.Directive::target)
                .filter(target -> wanted.stream().anyMatch(b -> target.equals(b.name().toLowerCase()))).toList();
        assertEquals(List.of("pens", "stable", "glassworks"), asked, "all three, in order");
    }

    @Test
    void theVillageBuildsItAndItIsPaidForInWorkstationMaterials() {
        Settlement s = village(10, Occupation.NITWIT);
        survey(s, LandCounts.Feature.CATTLE, 8);
        Optional<ConstructionProject> project = Construction.propose(s, 5, "plains", 200, catalog, t -> catalog.blueprint(t), (x, z) -> 64);
        assertTrue(project.isPresent(), s.history().toString());
        assertEquals(BuildingType.SMOKEHOUSE, project.get().type(), "cattle open the butcher's first");
        Blueprint b = catalog.blueprint(catalog.ladder(BuildingType.SMOKEHOUSE, "plains").get(0)).orElseThrow();
        assertTrue(b.materialCounts().keySet().stream().anyMatch(m -> Blueprint.name(m).equals("SMOKER")), "it holds the smoker");
        assertTrue(b.cost().getOrDefault(ResourceType.STONE, 0) >= 8, "and the smoker is in the price: " + b.cost());
    }

    @Test
    void noTradeBuildingNeedsGoodsTheTradeItselfMakes() {
        for (Occupation trade : Trades.landTrades()) {
            BuildingType type = Trades.buildingFor(trade).orElseThrow();
            for (int tier = 1; tier <= BuildingGenerator.TIERS; tier++) {
                Blueprint b = BuildingGenerator.generate(type, tier).orElseThrow();
                assertFalse(b.cost().containsKey(ResourceType.GOODS), type + " tier " + tier + " would wait on goods: " + b.cost());
                assertTrue(b.sign().isPresent(), type + " has its sign");
                assertFalse(b.cost().isEmpty());
            }
        }
        assertTrue(BuildingGenerator.generate(BuildingType.TRADING_POST, 1).isPresent());
    }

    // ----- staffing -----

    private Settlement idleVillageWithPens(int sheep) {
        return idleVillage(sheep, true);
    }

    private Settlement idleVillage(int sheep, boolean pens) {
        Settlement s = new Settlement(new UUID(99, 1000 + villages++), "Woolham", "world", 0, 0, 0);
        for (int i = 0; i < 10; i++) {
            s.addResident(person(Occupation.UNEMPLOYED));
        }
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.ledger().add(Commodity.BREAD, 5000);
        s.ledger().add(Commodity.PLANKS, 5000);
        s.ledger().add(Commodity.COBBLESTONE, 5000);
        s.ledger().add(Commodity.IRON, 2000);
        s.ledger().add(ResourceType.TOOLS, 2000);
        if (pens) {
            s.registerBuilding(new Building(BuildingType.PENS, 3000, 64, 3000, 0, "the builders"));
        }
        survey(s, LandCounts.Feature.SHEEP, sheep);
        return s;
    }

    private static long shepherds(Settlement s) {
        return s.residents().stream().filter(r -> r.occupation() == Occupation.SHEPHERD).count();
    }

    @Test
    void anIdleResidentTakesUpAnOpenTradeThatHasItsBuildingAndNotOtherwise() {
        SettlementSimulator simulator = SettlementSimulator.withOldAgeDeaths(false);
        Settlement open = idleVillageWithPens(6);
        simulator.simulateTo(open, 10, 100);
        assertTrue(shepherds(open) >= 1, "someone takes up shearing");
        assertTrue(shepherds(open) <= BuildingType.PENS.places(), "no more than the pens hold");

        Settlement fewSheep = idleVillageWithPens(3);
        simulator.simulateTo(fewSheep, 10, 100);
        assertEquals(0, shepherds(fewSheep), "three sheep are not a trade");

        Settlement noPens = idleVillage(6, false);
        simulator.simulateTo(noPens, 10, 100);
        assertEquals(0, shepherds(noPens), "nowhere to work");
    }

    private long goodsMadeBy(Settlement s, int days) {
        SettlementSimulator simulator = SettlementSimulator.withOldAgeDeaths(false);
        long before = s.ledger().get(ResourceType.GOODS);
        simulator.simulateTo(s, days, 400);
        return s.ledger().get(ResourceType.GOODS) - before;
    }

    @Test
    void aTradeWorksAQuarterBetterInItsOwnBuilding() {
        Settlement with = village(10, Occupation.SHEPHERD);
        with.registerBuilding(new Building(BuildingType.PENS, 3000, 64, 3000, 0, "the builders"));
        Settlement without = village(10, Occupation.SHEPHERD);
        for (Settlement s : List.of(with, without)) {
            s.ledger().add(ResourceType.TOOLS, 5000);
        }
        long a = goodsMadeBy(with, 12); // (short of the storage limit, where the trade would rest)
        long b = goodsMadeBy(without, 12);
        assertTrue(a > b * 1.10, "with pens " + a + " against " + b);
        assertTrue(a < b * 1.45, "about a quarter more, not double: " + a + " against " + b);
    }

    @Test
    void aTradingPostGivesEachMerchantOneMoreSaleADay() {
        Settlement s = village(4, Occupation.MERCHANT);
        Resident merchant = s.residents().iterator().next();
        int without = SettlementSimulator.batchesFor(s, merchant, 1);
        assertTrue(without >= 2, "a merchant sells several batches a day: " + without);
        s.registerBuilding(new Building(BuildingType.TRADING_POST, 3000, 64, 3000, 0, "the builders"));
        assertEquals(without + 1, SettlementSimulator.batchesFor(s, merchant, 1));
        Settlement other = village(4, Occupation.MERCHANT);
        assertEquals(without, SettlementSimulator.batchesFor(other, other.residents().iterator().next(), 1), "only where it stands");
    }

    @Test
    void aTradingPostIsAskedForOnceThereAreMerchantsAndAShop() {
        Settlement s = village(12, Occupation.NITWIT);
        assertFalse(asks(s, BuildingType.TRADING_POST), "no merchant yet");
        s.residents().iterator().next().setOccupation(Occupation.MERCHANT);
        assertTrue(asks(s, BuildingType.TRADING_POST));
        s.registerBuilding(new Building(BuildingType.TRADING_POST, 3000, 64, 3000, 0, "the builders"));
        assertFalse(asks(s, BuildingType.TRADING_POST));
        assertNotEquals(Optional.empty(), BuildingType.fromSign("[Trading Post]"));
    }

    @Test
    void aTradeThatOpensIsWrittenUpOnceAndOnlyWhenItOpens() {
        Settlement s = village(10, Occupation.NITWIT);
        assertEquals(0, Trades.noteOpened(s, 3));
        LandCounts.record(s, Map.of(LandCounts.Feature.BEES, 3, LandCounts.Feature.SAND, 50), 4);
        assertEquals(2, Trades.noteOpened(s, 4));
        assertEquals(0, Trades.noteOpened(s, 5), "once each");
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("could now support a beekeeper")
                && e.text().contains("apiary")), s.history().toString());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("glassblower") && e.text().contains("glassworks")));
        LandCounts.record(s, Map.of(LandCounts.Feature.HORSES, 3), 6);
        assertEquals(1, Trades.noteOpened(s, 6));
    }

    @Test
    void aFishingVillageCallsForAHarbourAndAPastoralOneForPensOnceTheirTradeIsOpen() {
        assertEquals(BuildingType.HARBOUR, Leaning.FISHING.signature());
        assertEquals(BuildingType.PENS, Leaning.PASTORAL.signature());
        assertTrue(Trades.isTradeBuilding(BuildingType.HARBOUR) && Trades.isTradeBuilding(BuildingType.PENS));
        assertFalse(Trades.isTradeBuilding(BuildingType.SAWMILL));
        Settlement pastoral = village(10, Occupation.NITWIT);
        pastoral.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "savanna")));
        assertEquals(Leaning.PASTORAL, pastoral.leaning());
        assertFalse(asks(pastoral, BuildingType.PENS), "no sheep, no shepherd: pens would stand empty");
        survey(pastoral, LandCounts.Feature.SHEEP, 5);
        assertTrue(asks(pastoral, BuildingType.PENS));
        assertTrue(Construction.Direction.FISHING.serves(BuildingType.HARBOUR));
        assertTrue(Construction.Direction.PASTORAL.serves(BuildingType.PENS));
    }
}
