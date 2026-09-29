package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementSimulatorTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();

    private Settlement village(int farmers, int others, Occupation otherJob) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < farmers; i++) {
            registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
        }
        for (int i = 0; i < others; i++) {
            registry.enroll(s, UUID.randomUUID(), otherJob, true, 0, null, null);
        }
        return s;
    }

    @Test
    void villageWithoutFoodProducersFallsIntoFamineAndRecovers() {
        Settlement s = village(0, 4, Occupation.LIBRARIAN);
        simulator.simulateTo(s, 3, 100);
        assertTrue(s.hasCondition("famine"));
        assertTrue(s.latest(HistoryEvent.Kind.FAMINE, 0).isPresent());
        assertTrue(s.residents().iterator().next().needs().food() < Needs.initial().food());

        s.ledger().add(ResourceType.FOOD, 1000);
        simulator.simulateTo(s, 4, 100);
        assertFalse(s.hasCondition("famine"));
        assertTrue(s.latest(HistoryEvent.Kind.RECOVERY, 4).isPresent());
    }

    @Test
    void farmersFeedTheVillage() {
        Settlement s = village(5, 2, Occupation.LIBRARIAN);
        s.ledger().add(ResourceType.TOOLS, 1000); // tool wear (R3.6) is tested separately
        simulator.simulateTo(s, 30, 100);
        assertFalse(s.hasCondition("famine"));
        assertTrue(s.ledger().get(ResourceType.FOOD) > 0);
    }

    @Test
    void smithsIdleWithoutMetalAndResumeWhenSupplied() {
        Settlement s = village(4, 1, Occupation.TOOLSMITH);
        simulator.simulateTo(s, 1, 100);
        assertTrue(s.hasCondition("shortage:metal"));
        assertEquals(0, s.ledger().get(ResourceType.TOOLS));

        // Output on any single day can legitimately be zero (low work ethic, an unlucky roll),
        // so give the smith several days to work rather than asserting on day one alone.
        s.ledger().add(ResourceType.METAL, 100);
        simulator.simulateTo(s, 11, 100);
        assertFalse(s.hasCondition("shortage:metal"));
        // Count what the smith made, not what is left: the farmers wear tools out too (R3.6).
        assertTrue(s.flow().produced(ResourceType.TOOLS, s.lastSimulatedDay()) > 0);
    }

    @Test
    void lumberjacksFeedTheVillageWithWood() {
        Settlement s = village(0, 5, Occupation.LUMBERJACK);
        simulator.simulateTo(s, 30, 100);
        assertTrue(s.ledger().get(ResourceType.WOOD) > 0);
    }

    @Test
    void fletchersIdleWithoutWoodAndResumeWhenSupplied() {
        Settlement s = village(4, 1, Occupation.FLETCHER);
        simulator.simulateTo(s, 1, 100);
        assertTrue(s.hasCondition("shortage:wood"));
        assertEquals(0, s.ledger().get(ResourceType.GOODS));

        s.ledger().add(ResourceType.WOOD, 100);
        simulator.simulateTo(s, 11, 100);
        assertFalse(s.hasCondition("shortage:wood"));
        assertTrue(s.ledger().get(ResourceType.GOODS) > 0);
    }

    @Test
    void shortageIsRecordedOnceNotEveryDay() {
        Settlement s = village(4, 1, Occupation.ARMORER);
        s.ledger().add(ResourceType.TOOLS, 1000); // tool wear (R3.6) is tested separately
        simulator.simulateTo(s, 20, 100);
        long shortages = s.history().stream().filter(e -> e.kind() == HistoryEvent.Kind.SHORTAGE).count();
        assertEquals(1, shortages);
    }

    @Test
    void aFlickeringShortageIsRecordedOnceNotOnEveryFlip() {
        // R1.21: metal arriving every other day turns the shortage on and off, but the
        // history shouldn't fill with it.
        Settlement s = village(4, 1, Occupation.TOOLSMITH);
        s.ledger().add(ResourceType.TOOLS, 1000);
        int flips = 0;
        boolean wasShort = false;
        for (long day = 1; day <= 40; day++) {
            if (day % 2 == 0) {
                s.ledger().add(ResourceType.METAL, 1);
            }
            simulator.simulateTo(s, day, 100);
            boolean isShort = s.hasCondition("shortage:metal");
            if (isShort != wasShort) {
                flips++;
            }
            wasShort = isShort;
        }
        assertTrue(flips > 10, "the shortage should really be flickering: " + flips);
        long lines = s.history().stream()
                .filter(e -> e.text().contains("metal")).count();
        assertTrue(lines <= 2, "history lines about metal: " + lines);
    }

    @Test
    void aShortageThatReturnsAndLastsIsRecordedAgain() {
        Settlement s = village(4, 1, Occupation.TOOLSMITH);
        s.ledger().add(ResourceType.TOOLS, 1000);
        simulator.simulateTo(s, 1, 100);                 // short: recorded
        s.ledger().add(ResourceType.METAL, 1);
        simulator.simulateTo(s, 2, 100);                 // restored: recorded
        simulator.simulateTo(s, 3 + SettlementSimulator.SHORTAGE_QUIET_DAYS, 100); // back, and it lasts
        long shortages = s.history().stream().filter(e -> e.kind() == HistoryEvent.Kind.SHORTAGE).count();
        assertEquals(2, shortages);
    }

    @Test
    void populationMilestoneRecorded() {
        Settlement s = village(10, 0, Occupation.FARMER);
        simulator.simulateTo(s, 1, 100);
        assertTrue(s.hasCondition("population:10"));
        assertFalse(s.hasCondition("population:25"));
    }

    @Test
    void threatFadesOverTime() {
        Settlement s = village(3, 0, Occupation.FARMER);
        s.raiseThreat(80);
        simulator.simulateTo(s, 10, 100);
        assertTrue(s.threat() < 30);
    }

    @Test
    void catchUpIsCappedAndDeterministic() {
        Settlement a = village(3, 1, Occupation.MASON);
        assertEquals(10, simulator.simulateTo(a, 1_000, 10));
        assertEquals(1_000, a.lastSimulatedDay());

        Settlement b = SettlementCodec.decode(SettlementCodec.encode(a));
        new SettlementSimulator().simulateTo(a, 1_005, 10);
        new SettlementSimulator().simulateTo(b, 1_005, 10);
        assertEquals(SettlementCodec.encode(a), SettlementCodec.encode(b));
    }

    @Test
    void skippedCatchUpDaysAreRecordedInHistory() {
        // R1.22: 1,000 days pending with a 10-day cap skips 990, and the history says so.
        Settlement s = village(3, 1, Occupation.MASON);
        simulator.simulateTo(s, 1_000, 10);
        assertTrue(s.history().stream().anyMatch(e -> e.text().startsWith("990 days passed that no one in")));

        // Nothing skipped, nothing recorded.
        Settlement quiet = village(3, 1, Occupation.MASON);
        simulator.simulateTo(quiet, 8, 10);
        assertTrue(quiet.history().stream().noneMatch(e -> e.text().contains("wrote down")));

        // An abandoned settlement has nothing to miss, so nothing is written.
        Settlement empty = registry.found("world", 0, 0, 0);
        simulator.simulateTo(empty, 20, 100);
        assertTrue(empty.isAbandoned());
        simulator.simulateTo(empty, 2_000, 10);
        assertTrue(empty.history().stream().noneMatch(e -> e.text().contains("wrote down")));
    }

    @Test
    void scarceInputsRotateBetweenWorkers() {
        // R1.20: two smiths, one ingot a day. Each should go without on some days, not the same one forever.
        Settlement s = village(4, 0, Occupation.FARMER);
        Resident first = registry.enroll(s, UUID.randomUUID(), Occupation.TOOLSMITH, true, 0, null, null);
        Resident second = registry.enroll(s, UUID.randomUUID(), Occupation.TOOLSMITH, true, 0, null, null);
        int firstIdle = 0;
        int secondIdle = 0;
        for (long day = 1; day <= 20; day++) {
            s.ledger().add(ResourceType.METAL, 1);
            simulator.simulateTo(s, day, 100);
            if (first.lastBlockedDay() == day) {
                firstIdle++;
            }
            if (second.lastBlockedDay() == day) {
                secondIdle++;
            }
        }
        assertEquals(20, firstIdle + secondIdle);
        assertEquals(10, firstIdle, "first smith idle days");
        assertEquals(10, secondIdle, "second smith idle days");
    }
}
