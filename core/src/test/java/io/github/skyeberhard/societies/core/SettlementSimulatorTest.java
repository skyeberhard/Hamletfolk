package io.github.skyeberhard.societies.core;

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

        s.ledger().add(ResourceType.METAL, 10);
        simulator.simulateTo(s, 2, 100);
        assertFalse(s.hasCondition("shortage:metal"));
        assertTrue(s.ledger().get(ResourceType.TOOLS) > 0);
    }

    @Test
    void shortageIsRecordedOnceNotEveryDay() {
        Settlement s = village(4, 1, Occupation.ARMORER);
        simulator.simulateTo(s, 20, 100);
        long shortages = s.history().stream().filter(e -> e.kind() == HistoryEvent.Kind.SHORTAGE).count();
        assertEquals(1, shortages);
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
}
