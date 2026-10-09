package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R8.11: what the land and its history make a village, and what that changes. */
class VillageCharacterTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = SettlementSimulator.withOldAgeDeaths(false);
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(95, next++), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50), job, true, 10_000, null, null,
                Needs.initial());
    }

    private Settlement village(int people) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < people; i++) {
            s.addResident(person(Occupation.UNEMPLOYED));
        }
        return s;
    }

    @Test
    void anOceanOrCoastGivesAFishingLeaning() {
        assertEquals(Leaning.FISHING, leaningOf("ocean"));
        assertEquals(Leaning.FISHING, leaningOf("mangrove_swamp"));
    }

    private static Leaning leaningOf(String biome) {
        return Leaning.of(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> biome)));
    }

    // ----- the land -----

    @Test
    void theLandGivesALeaning() {
        assertEquals(Leaning.TIMBER, leaningOf("forest"));
        assertEquals(Leaning.TIMBER, leaningOf("taiga"));
        assertEquals(Leaning.FARMING, leaningOf("plains"));
        assertEquals(Leaning.MINING, leaningOf("windswept_hills"));
        assertEquals(Leaning.MINING, leaningOf("badlands"));
        assertEquals(Leaning.FISHING, leaningOf("river"));
        assertEquals(Leaning.FISHING, leaningOf("swamp"));
        assertEquals(Leaning.PASTORAL, leaningOf("savanna"));
        assertEquals(Leaning.ALL_ROUND, leaningOf("desert"), "nothing stands out");
        assertEquals(Leaning.ALL_ROUND, leaningOf("snowy_plains"));
    }

    @Test
    void aLeaningNeedsAStrongScoreAndALeadOverTheNext() {
        java.util.Map<SiteResource, Double> s = new java.util.EnumMap<>(SiteResource.class);
        s.put(SiteResource.LUMBER, 60.0);
        s.put(SiteResource.FARMLAND, 55.0);
        assertEquals(Leaning.ALL_ROUND, Leaning.of(s), "a lead of five is not standing out");
        s.put(SiteResource.FARMLAND, 45.0);
        assertEquals(Leaning.TIMBER, Leaning.of(s), "a lead of fifteen is");
        s.clear();
        s.put(SiteResource.LUMBER, 30.0);
        assertEquals(Leaning.ALL_ROUND, Leaning.of(s), "too little of anything");
        s.clear();
        s.put(SiteResource.STONE, 60.0);
        s.put(SiteResource.ORE, 30.0);
        assertEquals(Leaning.MINING, Leaning.of(s), "stone and ore are averaged");
    }

    @Test
    void theLandIsKeptWithTheVillageAndASavedVillageRemembersIt() {
        Settlement s = village(3);
        assertFalse(s.hasLeaning());
        assertEquals(Leaning.ALL_ROUND, s.leaning());
        s.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "forest")));
        assertTrue(s.hasLeaning());
        assertEquals(Leaning.TIMBER, s.leaning());
        assertEquals(100, s.landScore(SiteResource.LUMBER));
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(Leaning.TIMBER, loaded.leaning());
        assertEquals(100, loaded.landScore(SiteResource.LUMBER));
        s.clearLand();
        assertFalse(s.hasLeaning());
    }

    // ----- size -----

    @Test
    void aVillagesStageFollowsItsSize() {
        assertEquals(VillageCharacter.Stage.HAMLET, VillageCharacter.Stage.of(0));
        assertEquals(VillageCharacter.Stage.HAMLET, VillageCharacter.Stage.of(7));
        assertEquals(VillageCharacter.Stage.VILLAGE, VillageCharacter.Stage.of(8));
        assertEquals(VillageCharacter.Stage.VILLAGE, VillageCharacter.Stage.of(19));
        assertEquals(VillageCharacter.Stage.TOWN, VillageCharacter.Stage.of(20));
        assertEquals(VillageCharacter.Stage.CITY, VillageCharacter.Stage.of(50));
        assertEquals(VillageCharacter.Stage.CITY, VillageCharacter.Stage.of(500));
    }

    // ----- history -----

    @Test
    void historyMakesATemperamentAndTheFirstThatFitsWins() {
        Settlement plain = village(10);
        assertEquals(VillageCharacter.Temperament.STEADY, VillageCharacter.temperament(plain));

        Settlement welcoming = village(10);
        for (int i = 0; i < 3; i++) {
            welcoming.record(i, HistoryEvent.Kind.ARRIVAL, "Someone came.");
        }
        assertEquals(VillageCharacter.Temperament.WELCOMING, VillageCharacter.temperament(welcoming));

        Settlement rich = village(10);
        rich.ledger().addTreasury(150);
        rich.ledger().add(Commodity.BREAD, 400);
        assertEquals(VillageCharacter.Temperament.PROSPEROUS, VillageCharacter.temperament(rich));
        rich.ledger().take(ResourceType.FOOD, 399); // rich but hungry is not prosperous
        assertEquals(VillageCharacter.Temperament.STEADY, VillageCharacter.temperament(rich));

        Settlement wary = village(10);
        wary.record(5, HistoryEvent.Kind.RAID, "Raiders overran the village.");
        assertEquals(VillageCharacter.Temperament.WARY, VillageCharacter.temperament(wary));
        wary.record(6, HistoryEvent.Kind.RAID, "Raiders attacked and were driven off.");
        assertEquals(VillageCharacter.Temperament.WARY, VillageCharacter.temperament(wary), "a raid driven off does not count");

        Settlement martial = village(10);
        for (int i = 0; i < 3; i++) {
            martial.recordIncident(i);
        }
        martial.record(1, HistoryEvent.Kind.RAID, "Raiders overran the village.");
        assertEquals(VillageCharacter.Temperament.MARTIAL, VillageCharacter.temperament(martial), "martial before wary");

        Settlement hungry = village(10);
        hungry.conditions().put("famine", 3L);
        for (int i = 0; i < 3; i++) {
            hungry.recordIncident(i);
        }
        assertEquals(VillageCharacter.Temperament.HARD_PRESSED, VillageCharacter.temperament(hungry), "hardship first");

        Settlement scarred = village(10);
        scarred.record(1, HistoryEvent.Kind.FAMINE, "The food stores ran empty.");
        assertEquals(VillageCharacter.Temperament.STEADY, VillageCharacter.temperament(scarred), "one famine");
        scarred.record(40, HistoryEvent.Kind.FAMINE, "The food stores ran empty.");
        scarred.setLastSimulatedDay(50);
        assertEquals(VillageCharacter.Temperament.HARD_PRESSED, VillageCharacter.temperament(scarred), "two within two months");
        scarred.setLastSimulatedDay(105);
        assertEquals(VillageCharacter.Temperament.STEADY, VillageCharacter.temperament(scarred),
                "the first is now more than two months back: it has come through them");
        scarred.setLastSimulatedDay(300);
        assertEquals(VillageCharacter.Temperament.STEADY, VillageCharacter.temperament(scarred), "and so is the second");
    }

    @Test
    void aBadNightIsOneAttackNotManyAndOldAttacksFade() {
        Settlement night = village(10);
        for (int i = 0; i < 12; i++) {
            night.recordIncident(5); // twelve villagers lost on one night
        }
        assertEquals(VillageCharacter.Temperament.STEADY, VillageCharacter.temperament(night));
        night.recordIncident(6);
        night.recordIncident(7);
        night.setLastSimulatedDay(8);
        assertEquals(VillageCharacter.Temperament.MARTIAL, VillageCharacter.temperament(night), "three different days");
        night.setLastSimulatedDay(200);
        assertEquals(VillageCharacter.Temperament.STEADY, VillageCharacter.temperament(night), "long ago");

        Settlement raided = village(10);
        raided.record(5, HistoryEvent.Kind.RAID, "Raiders overran the village.");
        raided.setLastSimulatedDay(200);
        assertEquals(VillageCharacter.Temperament.STEADY, VillageCharacter.temperament(raided), "a lost raid is forgotten in time");
        Settlement arrivals = village(10);
        for (int i = 0; i < 3; i++) {
            arrivals.record(i, HistoryEvent.Kind.ARRIVAL, "Someone came.");
        }
        arrivals.setLastSimulatedDay(200);
        assertEquals(VillageCharacter.Temperament.STEADY, VillageCharacter.temperament(arrivals));
    }

    @Test
    void sellingSurplusDoesNotFlipAVillageInAndOutOfProsperity() {
        Settlement s = village(10);
        s.ledger().addTreasury(150);
        s.ledger().add(Commodity.BREAD, SettlementSimulator.FOOD_KEPT_PER_HEAD * 10); // what a merchant leaves
        assertEquals(VillageCharacter.Temperament.PROSPEROUS, VillageCharacter.temperament(s));
        s.ledger().take(ResourceType.FOOD, 3 * 10); // three a head eaten or spoiled: still above the bar
        assertEquals(VillageCharacter.Temperament.PROSPEROUS, VillageCharacter.temperament(s));
    }

    @Test
    void theVillageIsDescribedInAPhrase() {
        Settlement s = village(25);
        s.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "forest")));
        assertEquals("a steady timber town", VillageCharacter.describe(s));
        Settlement plain = village(3);
        assertEquals("a steady hamlet", VillageCharacter.describe(plain));
        plain.ledger().addTreasury(500);
        plain.ledger().add(Commodity.BREAD, 500);
        assertEquals("a prosperous hamlet", VillageCharacter.describe(plain));
        Settlement hard = village(10);
        hard.conditions().put("famine", 1L);
        assertEquals("a hard-pressed village", VillageCharacter.describe(hard));
    }

    // ----- what it changes -----

    @Test
    void temperamentChangesTheUpgradePaceNewcomersAndGolems() {
        assertEquals(4, VillageCharacter.Temperament.PROSPEROUS.upgradeEveryDays());
        assertEquals(14, VillageCharacter.Temperament.HARD_PRESSED.upgradeEveryDays());
        assertEquals(Construction.UPGRADE_EVERY_DAYS, VillageCharacter.Temperament.STEADY.upgradeEveryDays());
        assertEquals(2, VillageCharacter.Temperament.WELCOMING.newcomerCooldownDays());
        assertEquals(6, VillageCharacter.Temperament.WARY.newcomerCooldownDays());
        assertEquals(SettlementSimulator.NEWCOMER_COOLDOWN_DAYS, VillageCharacter.Temperament.STEADY.newcomerCooldownDays());
        assertEquals(1, VillageCharacter.Temperament.MARTIAL.extraGolems());
        assertEquals(0, VillageCharacter.Temperament.STEADY.extraGolems());

        Settlement martial = village(12);
        for (int i = 0; i < 3; i++) {
            martial.recordIncident(i);
        }
        assertEquals(1 + 1 + 0, Golems.wanted(martial), "one for ten residents, and one more for a martial village");
    }

    @Test
    void aWelcomingVillageTakesNewcomersSoonerThanAWaryOne() {
        for (VillageCharacter.Temperament t : new VillageCharacter.Temperament[] {VillageCharacter.Temperament.WELCOMING,
                VillageCharacter.Temperament.WARY}) {
            Settlement s = village(4);
            s.ledger().add(Commodity.BREAD, 500);
            if (t == VillageCharacter.Temperament.WELCOMING) {
                for (int i = 0; i < 3; i++) {
                    s.record(i, HistoryEvent.Kind.ARRIVAL, "Someone came.");
                }
            } else {
                s.record(1, HistoryEvent.Kind.RAID, "Raiders overran the village.");
            }
            s.setLastSimulatedDay(50);
            simulator.newcomerArrived(s);
            s.setLastSimulatedDay(52);
            assertEquals(t == VillageCharacter.Temperament.WELCOMING, simulator.newcomerDue(s, 3), t + " two days after the last");
            s.setLastSimulatedDay(56);
            assertTrue(simulator.newcomerDue(s, 3), t + " six days after the last");
        }
    }

    @Test
    void theTradesOfTheLeaningAreStaffedWhileTheStoresAreStillAQuarterAboveWhatIsWanted() {
        // Seven residents, so the second permanent trade (eight) cannot be what hires them.
        for (String[] case_ : new String[][] {{"forest", "LUMBERJACK", "wood"}, {"windswept_hills", "MINER", "stone"}}) {
            Occupation trade = Occupation.valueOf(case_[1]);
            Settlement land = village(7);
            land.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> case_[0])));
            Settlement plain = village(7);
            for (Settlement s : List.of(land, plain)) {
                s.registerBuilding(new Building(BuildingType.MINE, 0, 64, 0, 0, "test"));
            }
            for (long day = 1; day <= 2; day++) {
                for (Settlement s : List.of(land, plain)) {
                    s.ledger().add(Commodity.PRODUCE, 5000);
                    // 21 of each is wanted for seven: 25 is not short, unless the leaning reads it a fifth lower
                    s.ledger().take(ResourceType.WOOD, 10_000);
                    s.ledger().take(ResourceType.STONE, 10_000);
                    s.ledger().take(ResourceType.METAL, 10_000);
                    s.ledger().add(Commodity.PLANKS, 25);
                    s.ledger().add(Commodity.COBBLESTONE, 25);
                    s.ledger().add(Commodity.IRON, 25);
                    simulator.simulateDay(s, day);
                }
            }
            assertEquals(2, count(land, trade), case_[0] + ": one a day while " + case_[2] + " is under a quarter over what is wanted");
            assertEquals(1, count(plain, trade), "only the permanent one without the land");
        }
        assertTrue(Leaning.TIMBER.favours(Occupation.LUMBERJACK) && !Leaning.TIMBER.favours(Occupation.MINER));
        assertTrue(Leaning.MINING.favours(Occupation.MINER) && Leaning.MINING.favours(Occupation.MASON));
    }

    @Test
    void aLeaningNeverBeatsARealShortage() {
        Settlement timber = village(7);
        timber.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "forest")));
        timber.registerBuilding(new Building(BuildingType.MINE, 0, 64, 0, 0, "test"));
        timber.ledger().add(Commodity.PRODUCE, 5000);
        timber.ledger().add(Commodity.PLANKS, 25); // not short (21 wanted), but favoured
        // stone and metal: truly short
        simulator.simulateDay(timber, 1);
        assertEquals(1, count(timber, Occupation.MINER), "the first jobless adult goes where there is a real shortage");
    }

    @Test
    void aTimberOrMiningVillageOfEightKeepsASecondPermanentLumberjackOrMiner() {
        Settlement timber = village(10);
        timber.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "forest")));
        Settlement small = village(7);
        small.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "forest")));
        Settlement plain = village(10);
        Settlement mining = village(10);
        mining.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "windswept_hills")));
        mining.registerBuilding(new Building(BuildingType.MINE, 0, 64, 0, 0, "test"));
        for (long day = 1; day <= 4; day++) {
            for (Settlement s : List.of(timber, small, plain, mining)) {
                s.ledger().add(Commodity.PRODUCE, 5000);
                s.ledger().add(Commodity.PLANKS, 5000); // plenty of everything: only the permanent trades are hired
                s.ledger().add(Commodity.COBBLESTONE, 5000);
                s.ledger().add(Commodity.IRON, 5000);
                simulator.simulateDay(s, day);
            }
        }
        assertEquals(2, count(timber, Occupation.LUMBERJACK));
        assertEquals(1, count(small, Occupation.LUMBERJACK), "seven is too few");
        assertEquals(1, count(plain, Occupation.LUMBERJACK));
        assertEquals(2, count(mining, Occupation.MINER));
        assertEquals(1, count(mining, Occupation.LUMBERJACK), "its leaning is not timber");
    }

    private static long count(Settlement s, Occupation job) {
        return s.residents().stream().filter(r -> r.occupation() == job).count();
    }

    @Test
    void theLandSetsTheDirectionAheadOfLastWeeksOutput() {
        Settlement s = village(6);
        assertEquals(Construction.Direction.UNDECIDED, Construction.direction(s, 10), "no land read, nothing made");
        s.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "windswept_hills")));
        assertEquals(Construction.Direction.MINING, Construction.direction(s, 10));
        s.flow().recordProduced(ResourceType.WOOD, 10, 500); // it made a lot of wood: the land still decides
        assertEquals(Construction.Direction.MINING, Construction.direction(s, 10));
        assertTrue(Construction.Direction.MINING.serves(BuildingType.MINE));
        s.clearLand();
        assertEquals(Construction.Direction.FORESTRY, Construction.direction(s, 10), "an all-round village goes by what it makes");
        Settlement river = village(6);
        river.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "river")));
        assertEquals(Construction.Direction.FISHING, Construction.direction(river, 10));
        Settlement savanna = village(6);
        savanna.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "savanna")));
        assertEquals(Construction.Direction.PASTORAL, Construction.direction(savanna, 10));
    }

    @Test
    void villagersSpeakOfTheirVillage() {
        Settlement s = village(25);
        s.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "windswept_hills")));
        s.ledger().addTreasury(300);
        s.ledger().add(Commodity.BREAD, 3000);
        List<String> lines = Dialogue.characterLines(s);
        assertTrue(lines.stream().anyMatch(l -> l.contains("mining place")), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.contains("never done better")), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.contains("is a town")), lines.toString());
        Settlement small = village(3);
        assertTrue(Dialogue.characterLines(small).stream().anyMatch(l -> l.contains("small place")));
        // and it reaches what a resident can say
        boolean said = false;
        for (int seed = 0; seed < 200 && !said; seed++) {
            said = Dialogue.smallTalk(s.residents().iterator().next(), s, 10, new Random(seed)).contains("mining place");
        }
        assertTrue(said, "a resident sometimes says it");
    }
}
