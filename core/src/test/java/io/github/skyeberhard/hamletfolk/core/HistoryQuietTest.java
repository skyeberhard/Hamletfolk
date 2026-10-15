package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.44: a village that is waiting for something says so once, not every few days. */
class HistoryQuietTest {
    private Settlement village() {
        Settlement s = new Settlement(new UUID(80, 80), "Quietham", "world", 0, 0, 0);
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        return s;
    }

    @Test
    void theSameLineIsNotWrittenAgainInsideTheQuietPeriodButChangedWordsAre() {
        Settlement s = village();
        s.recordQuiet(10, HistoryEvent.Kind.BUILDING, "It needs 2 stone (it has 0).", 30);
        s.recordQuiet(15, HistoryEvent.Kind.BUILDING, "It needs 2 stone (it has 0).", 30);
        s.recordQuiet(20, HistoryEvent.Kind.BUILDING, "It needs 2 stone (it has 1).", 30);
        s.recordQuiet(39, HistoryEvent.Kind.BUILDING, "It needs 2 stone (it has 0).", 30);
        s.recordQuiet(41, HistoryEvent.Kind.BUILDING, "It needs 2 stone (it has 0).", 30);
        long needs = s.history().stream().filter(e -> e.text().startsWith("It needs")).count();
        assertEquals(3, needs, "day 10, the changed numbers on day 20, and again once 30 days had passed (day 41)");
    }

    @Test
    void aVillageThatCannotAffordWhatItWantsSaysSoOnlyNowAndThen() {
        Settlement s = village();
        s.ledger().add(ResourceType.FOOD, 1000);
        s.registerBuilding(new Building(BuildingType.FARM, 500, 64, 500, 0, "a player"));
        s.registerBuilding(new Building(BuildingType.MINE, 501, 64, 501, 0, "a player"));
        for (int i = 0; i < 6; i++) {
            s.addResident(new Resident(new UUID(81, i), "T", "P" + i, Gender.FEMALE, new Traits(50, 50, 50, 80), Occupation.FARMER,
                    true, 0, null, null, Needs.initial()));
        }
        s.housing().setChunk(0, 0, 6);
        s.ledger().add(ResourceType.TOOLS, 50);
        TemplateCatalog catalog = new TemplateCatalog();
        for (long day = 1; day <= 120; day++) {
            Construction.propose(s, day, "plains", 200, catalog, t -> Optional.empty(), (x, z) -> 64);
        }
        long lines = s.history().stream().filter(e -> e.text().contains("wants")).count();
        assertTrue(lines <= 6, "was a line every five days for each building it wants; now: " + lines);
    }

    @Test
    void theDecisionLogHoldsEachDecisionOnceWithItsLastDay() {
        Settlement s = village();
        s.recordDecision(10, Planner.Tier.GROWTH, "GROWTH|BUILD|shop", "Build a shop (first).", 12);
        s.recordDecision(50, Planner.Tier.GROWTH, "GROWTH|BUILD|house", "Build a house.", 12);
        s.recordDecision(60, Planner.Tier.GROWTH, "GROWTH|BUILD|shop", "Build a shop (again).", 12);
        assertEquals(2, s.decisions().size());
        assertEquals("GROWTH|BUILD|house", s.decisions().get(0).key());
        Planner.Decision shop = s.decisions().get(1);
        assertEquals(60, shop.day());
        assertEquals("Build a shop (again).", shop.text());
    }
}
