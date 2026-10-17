package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R1.42: what a village's numbers mean over the days to come, and R4.52: why it is short of tools. */
class OutlookTest {
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(180, next++), "T", "P", Gender.MALE, new Traits(50, 50, 50, 80), job, true, 0, null, null, Needs.initial());
    }

    /** A village that has run for ten days with the given daily making and using of food, and room for newcomers. */
    private Settlement village(int stock, int madePerDay, int usedPerDay) {
        Settlement s = new Settlement(new UUID(181, 1), "Outham", "world", 0, 0, 0);
        for (int i = 0; i < 6; i++) {
            s.addResident(person(Occupation.FARMER));
        }
        s.setLastSimulatedDay(10);
        s.ledger().add(ResourceType.FOOD, stock);
        for (long d = 4; d <= 10; d++) {
            s.flow().recordProduced(ResourceType.FOOD, d, madePerDay);
            s.flow().recordConsumed(ResourceType.FOOD, d, usedPerDay);
        }
        s.housing().setChunk(0, 0, 12); // beds
        return s;
    }

    private static Outlook.Line food(Settlement s) {
        return Outlook.lines(s, s.lastSimulatedDay()).get(0);
    }

    @Test
    void aFallingStockSaysHowManyDaysItHasLeft() {
        Outlook.Line food = food(village(60, 10, 16)); // loses 6 a day
        assertEquals(-6.0, food.perDay(), 1e-9);
        assertEquals(10, food.daysLeft(), "60 / 6");
        assertEquals("runs out in about 10 days", food.verdict());
        assertEquals(10.0, food.produced(), 1e-9);
        assertEquals(16.0, food.consumed(), 1e-9);
    }

    @Test
    void aSlowFallIsNotAWarningAndASteadyOneIsSteady() {
        Outlook.Line slow = food(village(900, 10, 11)); // 900 days
        assertEquals("falling slowly", slow.verdict());
        assertTrue(slow.daysLeft() > Outlook.WARN_DAYS);
        Settlement steadyVillage = village(200, 12, 12);
        assertEquals("steady", food(steadyVillage).verdict());
        assertEquals(-1, food(steadyVillage).daysLeft());
    }

    @Test
    void aStockBelowItsAimThatIsRisingSaysWhenItWillGetThere() {
        Settlement s = village(30, 20, 14); // +6 a day; aims for 6 x (at least 10) = 60 or more
        Outlook.Line food = food(s);
        assertTrue(food.wanted() > food.stock(), food.wanted() + " aim");
        assertEquals(Math.ceil((food.wanted() - 30) / 6.0), food.daysToAim(), 1e-9);
        assertTrue(food.verdict().startsWith("recovering: back to its aim in about"), food.verdict());
        Settlement grown = village(900, 20, 14);
        assertEquals("growing", food(grown).verdict());
    }

    @Test
    void nothingInAndNothingOutIsSaidPlainly() {
        Settlement s = village(0, 0, 0);
        assertEquals("none, and none coming in", food(s).verdict());
        Settlement used = village(0, 0, 5);
        assertEquals("none left and still being used", food(used).verdict());
        assertEquals(0, food(used).daysLeft());
    }

    @Test
    void aYoungVillageUsesTheDaysItHasNotTheWholeWindow() {
        Settlement s = new Settlement(new UUID(181, 2), "Newham", "world", 0, 0, 5);
        s.addResident(person(Occupation.FARMER));
        s.setLastSimulatedDay(7); // two days of accounts
        s.flow().recordConsumed(ResourceType.FOOD, 6, 4);
        s.flow().recordConsumed(ResourceType.FOOD, 7, 4);
        assertEquals(2, s.flowDays());
        assertEquals(4.0, Outlook.lines(s, 7).get(0).consumed(), 1e-9, "8 over two days, not over seven");
    }

    @Test
    void thePeopleCanGrowAreHeldBackStalledOrHeadingForFamine() {
        Settlement good = village(400, 20, 14);
        Outlook.People grows = Outlook.people(good, 10, Outlook.lines(good, 10));
        assertEquals("can grow", grown(grows));
        Settlement hungry = village(30, 10, 16); // 5 days of food
        assertEquals("heading for famine", Outlook.people(hungry, 10, Outlook.lines(hungry, 10)).verdict());
        Settlement noBeds = village(400, 20, 14);
        noBeds.housing().setChunk(0, 0, 0);
        assertEquals("stalled", Outlook.people(noBeds, 10, Outlook.lines(noBeds, 10)).verdict());
        Settlement held = village(20, 16, 15); // not falling, but below what a newcomer needs
        assertEquals("held back", Outlook.people(held, 10, Outlook.lines(held, 10)).verdict());
        Settlement famine = village(0, 0, 10);
        famine.conditions().put("famine", 9L);
        assertEquals("in famine", Outlook.people(famine, 10, Outlook.lines(famine, 10)).verdict());
        assertEquals("empty", Outlook.people(new Settlement(new UUID(181, 3), "Void", "world", 0, 0, 0), 1,
                Outlook.lines(new Settlement(new UUID(181, 3), "Void", "world", 0, 0, 0), 1)).verdict());
    }

    private static String grown(Outlook.People people) {
        return people.verdict();
    }

    @Test
    void theOutlookIsPlainDataForThePage() {
        Settlement s = village(60, 10, 16);
        var data = Outlook.asData(s, 10);
        assertEquals(7, data.get("windowDays"));
        assertEquals(6, ((List<?>) data.get("lines")).size());
        assertTrue(((java.util.Map<?, ?>) data.get("people")).containsKey("verdict"));
        assertEquals(data, Outlook.asData(s, 10), "the same every time");
    }

    // ----- R4.52 -----

    @Test
    void noSmithyIsSaidAndSoIsNoGroundForOne() {
        Settlement s = village(60, 10, 10);
        assertEquals(Optional.of("no smithy yet"), ToolsWhy.tools(s));
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.plan().lots().stream().filter(l -> l.type() == BuildingType.SMITHY).forEach(l -> s.plan().fill(l.id()));
        s.conditions().put("lotSearch:SMITHY", 9L);
        assertEquals(Optional.of("no smithy, and no ground found for one"), ToolsWhy.tools(s));
    }

    @Test
    void theNextMissingThingIsTheReason() {
        Settlement s = village(60, 10, 10);
        s.registerBuilding(new Building(BuildingType.SMITHY, 5, 64, 5, 1, "test"));
        assertEquals(Optional.of("a smithy, but nobody working in it"), ToolsWhy.tools(s));
        s.addResident(person(Occupation.TOOLSMITH));
        assertEquals(Optional.of("nothing to make them from: no mine"), ToolsWhy.tools(s));
        s.registerBuilding(new Building(BuildingType.MINE, 9, 64, 9, 1, "test"));
        assertEquals(Optional.of("nothing to make them from: nobody mining"), ToolsWhy.tools(s));
        s.addResident(person(Occupation.MINER));
        assertEquals(Optional.of("nothing to make them from"), ToolsWhy.tools(s), "a mine and a miner, but nothing yet");
        s.ledger().add(ResourceType.METAL, 5);
        assertFalse(ToolsWhy.tools(s).isPresent(), "they are being made");
        assertEquals(Optional.empty(), ToolsWhy.metal(s));
    }

    @Test
    void theStatusLineSaysWhy() {
        Settlement s = village(60, 10, 10);
        s.conditions().put("shortage:tools", 9L);
        s.conditions().put("shortage:metal", 9L);
        String line = SettlementInspector.digest(s);
        assertTrue(line.contains("no tools (no smithy yet)"), line);
        assertTrue(line.contains("no metal (no mine)"), line);
    }
}
