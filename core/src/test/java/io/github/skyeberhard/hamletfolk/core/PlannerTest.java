package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R8.1: the planner works through food, shelter, safety and growth, finds the root of a shortage and says why. */
class PlannerTest {
    private static final int LIMIT = 200;
    private static final UUID VILLAGE = new UUID(21, 21);

    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(20, next++), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, 80), job, true,
                10_000, null, null, Needs.initial());
    }

    /** Ten residents who make nothing, so only the test decides the stores. Wanted levels: 100 food, 30 of the rest. */
    private Settlement village() {
        Settlement s = new Settlement(VILLAGE, "Planford", "world", 0, 0, 0);
        for (int i = 0; i < 10; i++) {
            s.addResident(person(Occupation.NITWIT));
        }
        return s;
    }

    private static void building(Settlement s, BuildingType type, int at) {
        s.registerBuilding(new Building(type, at, 64, 0, 0, "test"));
    }

    private static List<Planner.Directive> run(Settlement s, long day) {
        return Planner.run(s, day, LIMIT);
    }

    /** The directives apart from the mine a village with none always asks for first (R4.20). */
    private static List<Planner.Directive> needs(List<Planner.Directive> directives) {
        return directives.stream().filter(d -> d.tier() != Planner.Tier.SUPPLY).toList();
    }

    private static boolean met(Settlement s, Planner.Tier tier) {
        return s.hasCondition(Planner.MET_PREFIX + tier.name());
    }

    @Test
    void tiersAreWorkedThroughInOrder() {
        Settlement s = village(); // no food, no beds, nothing
        s.housing().setChunk(0, 0, 0); // the beds have been counted, and there are none
        List<Planner.Directive> first = run(s, 1);
        assertFalse(first.isEmpty());
        assertTrue(first.stream().allMatch(d -> d.tier() == Planner.Tier.FOOD), "food comes before shelter: " + first);
        assertEquals(Planner.Kind.BUILD, first.get(0).kind());
        assertEquals("farm", first.get(0).target());

        s.ledger().add(ResourceType.FOOD, 200); // food is met
        List<Planner.Directive> withMine = run(s, 2);
        assertEquals("mine", withMine.get(0).target(), "then a mine, ahead of the rest (R4.20): " + withMine);
        List<Planner.Directive> second = needs(withMine);
        assertTrue(met(s, Planner.Tier.FOOD));
        assertTrue(second.stream().allMatch(d -> d.tier() == Planner.Tier.SHELTER), "now shelter: " + second);
        assertEquals("house", second.get(0).target());
        assertTrue(second.get(0).reason().contains("10 residents but only 0 beds"), second.get(0).reason());
        assertTrue(second.get(0).text().endsWith("."), "a sentence a resident can say: " + second.get(0).text());
    }

    @Test
    void aTierMetStaysMetUntilItSlipsAndNeedsFullCoverToReturn() {
        Settlement s = village();
        s.ledger().add(ResourceType.FOOD, 100); // exactly what ten residents want: ratio 1.0
        run(s, 1);
        assertTrue(met(s, Planner.Tier.FOOD));

        s.ledger().take(ResourceType.FOOD, 20); // 0.8: dipped, not slipped
        run(s, 2);
        assertTrue(met(s, Planner.Tier.FOOD), "a bad day does not flip it");

        s.ledger().take(ResourceType.FOOD, 20); // 0.6: slipped
        run(s, 3);
        assertFalse(met(s, Planner.Tier.FOOD));

        s.ledger().add(ResourceType.FOOD, 30); // 0.9: better, but not met again until it is enough
        run(s, 4);
        assertFalse(met(s, Planner.Tier.FOOD));

        s.ledger().add(ResourceType.FOOD, 10); // 1.0
        run(s, 5);
        assertTrue(met(s, Planner.Tier.FOOD));
    }

    @Test
    void aFamineWithAFarmWhosePlacesAreFullSaysBuildAnotherAndWithFreePlacesSaysTakeOnFarmers() {
        Settlement s = village();
        building(s, BuildingType.FARM, 0);
        List<Planner.Directive> open = run(s, 1);
        assertEquals(Planner.Kind.OPEN_JOB, open.get(0).kind());
        assertEquals("farmer", open.get(0).target());

        Settlement full = new Settlement(new UUID(21, 22), "Fullford", "world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            full.addResident(person(Occupation.FARMER));
        }
        building(full, BuildingType.FARM, 0); // four places, four farmers
        List<Planner.Directive> more = run(full, 1);
        assertEquals(Planner.Kind.BUILD, more.get(0).kind());
        assertTrue(more.get(0).reason().contains("every place at the farm is taken"), more.get(0).reason());
    }

    @Test
    void shortOfToolsTheVillageWalksBackToTheRootOfTheProblem() {
        Settlement s = village();
        s.ledger().add(ResourceType.FOOD, 200);
        s.housing().setChunk(0, 0, 12); // fed and housed: safety is next
        s.setThreat(60); // alarmed
        run(s, 1);

        // Alarmed, no tools, no smithy: tools come from a smithy.
        List<Planner.Directive> a = Planner.directives(s, 2, LIMIT);
        assertTrue(a.stream().anyMatch(d -> d.kind() == Planner.Kind.BUILD && d.target().equals("smithy")), a.toString());
        assertTrue(a.stream().anyMatch(d -> d.target().equals("guard_post")), "somewhere to gather the guards");

        // A smithy, but no metal and no mine: metal comes from a mine.
        building(s, BuildingType.SMITHY, 0);
        List<Planner.Directive> b = Planner.directives(s, 2, LIMIT);
        assertTrue(b.stream().anyMatch(d -> d.target().equals("mine")), b.toString());
        assertTrue(needs(b).get(0).reason().contains("tools"), "the reason names where the chain started: " + b);

        // A mine with free places: take on miners.
        building(s, BuildingType.MINE, 1);
        List<Planner.Directive> c = Planner.directives(s, 2, LIMIT);
        assertTrue(c.stream().anyMatch(d -> d.kind() == Planner.Kind.OPEN_JOB && d.target().equals("miner")), c.toString());

        // Metal in the stores: now it is the smith's job.
        s.ledger().add(ResourceType.METAL, 10);
        List<Planner.Directive> d = Planner.directives(s, 2, LIMIT);
        assertTrue(d.stream().anyMatch(x -> x.kind() == Planner.Kind.OPEN_JOB && x.target().equals("toolsmith")), d.toString());
    }

    @Test
    void shelterCountsHowManyHousesTheRestNeed() {
        Settlement s = new Settlement(new UUID(21, 23), "Roofless", "world", 0, 0, 0);
        for (int i = 0; i < 7; i++) {
            s.addResident(person(Occupation.NITWIT));
        }
        s.ledger().add(ResourceType.FOOD, 500);
        s.housing().setChunk(0, 0, 1);
        List<Planner.Directive> d = needs(run(s, 1));
        assertEquals("house", d.get(0).target());
        assertTrue(d.get(0).reason().contains("2 houses"), d.get(0).reason()); // six without a bed, three to a house
    }

    @Test
    void whenEverythingIsMetItLooksForTradeBankingAndRoomToGrow() {
        Settlement s = village();
        s.ledger().add(ResourceType.FOOD, 300);
        s.ledger().add(ResourceType.WOOD, 100);
        s.ledger().add(ResourceType.STONE, 100);
        s.housing().setChunk(0, 0, 10); // exactly ten beds for ten residents: shelter met, no free bed
        run(s, 1);
        assertTrue(met(s, Planner.Tier.FOOD) && met(s, Planner.Tier.SHELTER) && met(s, Planner.Tier.SAFETY));
        List<Planner.Directive> growth = needs(Planner.directives(s, 1, LIMIT));
        assertTrue(growth.stream().allMatch(d -> d.tier() == Planner.Tier.GROWTH), growth.toString());
        assertTrue(growth.stream().anyMatch(d -> d.target().equals("shop")), "surplus and no shop: " + growth);

        building(s, BuildingType.SHOP, 0);
        s.ledger().addTreasury(130); // more than half of the 200 limit
        List<Planner.Directive> bank = Planner.directives(s, 1, LIMIT);
        assertTrue(bank.stream().anyMatch(d -> d.target().equals("treasury")), bank.toString());

        building(s, BuildingType.TREASURY, 1);
        List<Planner.Directive> room = Planner.directives(s, 1, LIMIT);
        assertTrue(room.stream().anyMatch(d -> d.target().equals("house") && d.reason().contains("every bed is taken")),
                room.toString());
    }

    @Test
    void theSameVillageAndDayAlwaysGiveTheSameDecisions() {
        Settlement a = village();
        a.setThreat(70);
        Settlement b = SettlementCodec.decode(SettlementCodec.encode(a));
        for (long day = 1; day <= 8; day++) {
            run(a, day);
            run(b, day);
        }
        assertEquals(a.decisions(), b.decisions());
        assertFalse(a.decisions().isEmpty());
    }

    @Test
    void theDecisionLogIsBoundedSeparateFromHistoryAndDoesNotRepeatItself() {
        Settlement s = village();
        int historyBefore = s.history().size();
        s.recordDecision(1, Planner.Tier.FOOD, "FOOD|BUILD|farm", "Build a farm: food is short.", 10);
        s.recordDecision(5, Planner.Tier.FOOD, "FOOD|BUILD|farm", "Build a farm: food is short.", 10); // same, within ten days
        assertEquals(1, s.decisions().size());
        s.recordDecision(6, Planner.Tier.GROWTH, "GROWTH|BUILD|treasury", "Build a treasury: 130 of 200.", 10);
        s.recordDecision(7, Planner.Tier.GROWTH, "GROWTH|BUILD|treasury", "Build a treasury: 134 of 200.", 10); // new numbers, same decision
        assertEquals(2, s.decisions().size(), "numbers in the reason do not make it a new decision");
        s.recordDecision(12, Planner.Tier.FOOD, "FOOD|BUILD|farm", "Build a farm: food is short.", 10); // a reminder after ten days
        assertEquals(3, s.decisions().size());
        for (int i = 0; i < Settlement.MAX_DECISIONS + 30; i++) {
            s.recordDecision(100 + i, Planner.Tier.GROWTH, "k" + i, "Decision " + i, 10);
        }
        assertEquals(Settlement.MAX_DECISIONS, s.decisions().size());
        assertEquals(historyBefore, s.history().size(), "the history is not touched");
    }

    @Test
    void aVillageNobodyVisitsPlansAtTheSlowPaceAndOnlyOncePerDay() {
        Settlement visited = village();
        Planner.visited(visited, 1);
        assertFalse(run(visited, 5).isEmpty(), "recently visited: every day");
        assertTrue(run(visited, 5).isEmpty(), "but only once a day");
        assertFalse(run(visited, 6).isEmpty());

        Settlement away = village();
        Planner.visited(away, 1);
        assertTrue(run(away, 20).isEmpty(), "day 20: a week and more since a visit, and 20 is not a multiple of 3");
        assertFalse(run(away, 21).isEmpty(), "day 21 is");

        Settlement never = village(); // adopted villages nobody has opened a command in still plan every day
        assertFalse(run(never, 20).isEmpty());
    }

    @Test
    void beforeThePlannerHasRunTheAdviceUsesTheNumbersAsTheyAre() {
        Settlement fed = village();
        fed.ledger().add(ResourceType.FOOD, 500);
        fed.housing().setChunk(0, 0, 12);
        List<Planner.Directive> advice = Planner.directives(fed, 1, LIMIT); // run() has never been called
        assertTrue(advice.stream().noneMatch(d -> d.tier() == Planner.Tier.FOOD), "plenty of food: not 'food is short': " + advice);

        Settlement hungry = village();
        List<Planner.Directive> hungryAdvice = Planner.directives(hungry, 1, LIMIT);
        assertEquals(Planner.Tier.FOOD, hungryAdvice.get(0).tier());
    }

    @Test
    void bedsNobodyHasCountedYetAreNotTreatedAsNoBeds() {
        Settlement s = village();
        s.ledger().add(ResourceType.FOOD, 500);
        assertFalse(s.housing().counted());
        run(s, 1);
        assertTrue(met(s, Planner.Tier.SHELTER), "no count yet: no shelter directive");
        s.housing().setChunk(0, 0, 0); // counted: none
        run(s, 2);
        assertFalse(met(s, Planner.Tier.SHELTER));
    }

    @Test
    void anUnmetSafetyTierNeverLetsGrowthAdviceThrough() {
        // Wary, with tools, but nobody brave enough to call up: safety is open, so the plan must say so.
        Settlement s = new Settlement(new UUID(21, 30), "Timidton", "world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(new Resident(new UUID(20, 900 + i), "T", "P", Gender.MALE, new Traits(50, 50, 50, 10),
                    Occupation.NITWIT, true, 10_000, null, null, Needs.initial()));
        }
        s.ledger().add(ResourceType.FOOD, 300);
        s.ledger().add(ResourceType.TOOLS, 10);
        s.ledger().add(ResourceType.WOOD, 100);
        s.ledger().add(ResourceType.STONE, 100);
        s.housing().setChunk(0, 0, 8);
        s.recordIncident(1);
        s.recordIncident(1);
        run(s, 2); // wary, nobody fit to guard
        List<Planner.Directive> plan = needs(Planner.directives(s, 2, LIMIT));
        assertTrue(plan.stream().allMatch(d -> d.tier() == Planner.Tier.SAFETY), plan.toString());
        assertTrue(plan.stream().anyMatch(d -> d.target().equals("guard")), plan.toString());
    }

    @Test
    void whenTheRootIsMissingTheVillageAlsoFlagsWhatToBringInMeanwhile() {
        Settlement famine = village();
        famine.conditions().put("famine", 1L);
        List<Planner.Directive> food = run(famine, 2);
        assertTrue(food.stream().anyMatch(d -> d.kind() == Planner.Kind.IMPORT && d.target().equals("food")), food.toString());

        Settlement s = village();
        s.ledger().add(ResourceType.FOOD, 200);
        s.housing().setChunk(0, 0, 12);
        s.setThreat(60);
        run(s, 1);
        List<Planner.Directive> safety = Planner.directives(s, 2, LIMIT);
        assertTrue(safety.stream().anyMatch(d -> d.kind() == Planner.Kind.IMPORT && d.target().equals("tools")), safety.toString());
    }

    @Test
    void onceFedAVillageWithNoMineAsksForOneFirstWithoutHoldingTheRestUp() {
        Settlement hungry = village(); // no food: a farm, and nothing about a mine yet
        assertTrue(run(hungry, 1).stream().noneMatch(d -> d.target().equals("mine")));

        Settlement s = village();
        s.ledger().add(ResourceType.FOOD, 200);
        s.housing().setChunk(0, 0, 0); // no beds: shelter is open too
        List<Planner.Directive> plan = run(s, 1);
        assertEquals(Planner.Tier.SUPPLY, plan.get(0).tier());
        assertEquals("mine", plan.get(0).target());
        assertTrue(plan.stream().anyMatch(d -> d.target().equals("house")), "the houses are still asked for: " + plan);

        building(s, BuildingType.MINE, 0);
        assertTrue(run(s, 2).stream().noneMatch(d -> d.tier() == Planner.Tier.SUPPLY), "a mine, so no more asking");
    }

    @Test
    void anEmptyVillagePlansNothing() {
        Settlement s = new Settlement(new UUID(21, 24), "Emptyford", "world", 0, 0, 0);
        assertTrue(run(s, 1).isEmpty());
        assertTrue(s.decisions().isEmpty());
    }

    @Test
    void theSimulationRunsThePlannerEveryDayAndTheLogSurvivesASave() {
        Settlement s = village();
        new SettlementSimulator().simulateTo(s, 3, 10);
        assertFalse(s.decisions().isEmpty(), "three simulated days of a village with nothing: it wants a farm");
        assertTrue(s.decisions().stream().anyMatch(d -> d.text().startsWith("Build a farm")), s.decisions().toString());
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(s.decisions(), loaded.decisions());
    }

    @Test
    void aFormatSixteenSaveStillLoadsAndTheLogStartsEmpty() {
        Settlement s = village();
        Map<String, Object> old = new LinkedHashMap<>(SettlementCodec.encode(s));
        old.put("format", 16);
        old.remove("decisions");
        Settlement loaded = SettlementCodec.decode(old);
        assertTrue(loaded.decisions().isEmpty());
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(loaded).get("format"));
    }

    @Test
    void residentsMentionTheLatestDecisionOnlyWhileItIsFresh() {
        Settlement s = village();
        s.recordDecision(10, Planner.Tier.FOOD, "FOOD|BUILD|farm", "Build a farm: food is short.", 10);
        Resident adult = s.residents().iterator().next();
        Random random = new Random(7); // one sequence: consecutive small seeds give near-identical first draws
        boolean said = false;
        for (int draw = 0; draw < 200 && !said; draw++) {
            said = Dialogue.smallTalk(adult, s, 12, random).contains("There's talk in Planford. Build a farm");
        }
        assertTrue(said, "a recent decision comes up in talk");
        for (int draw = 0; draw < 100; draw++) {
            assertFalse(Dialogue.smallTalk(adult, s, 40, random).contains("There's talk"), "an old one does not");
        }
    }
}
