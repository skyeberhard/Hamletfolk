package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R3.7: each settlement keeps a rolling 7-day total of what it produced and consumed per resource. */
class ResourceFlowTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();

    private Settlement farm(int farmers) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < farmers; i++) {
            registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
        }
        return s;
    }

    @Test
    void totalsCoverOnlyTheLastSevenDays() {
        ResourceFlow flow = new ResourceFlow();
        flow.recordProduced(ResourceType.FOOD, 1, 100);
        flow.recordProduced(ResourceType.FOOD, 5, 4);
        flow.recordConsumed(ResourceType.FOOD, 7, 6);
        assertEquals(104, flow.produced(ResourceType.FOOD, 7));
        assertEquals(6, flow.consumed(ResourceType.FOOD, 7));
        // Day 1 has aged out of the window ending on day 8.
        assertEquals(4, flow.produced(ResourceType.FOOD, 8));
        assertEquals(0, flow.produced(ResourceType.WOOD, 8));
    }

    @Test
    void theSimulatorRecordsWhatWasMadeAndEaten() {
        Settlement s = farm(4);
        s.ledger().add(ResourceType.FOOD, 1000);
        int before = s.ledger().get(ResourceType.FOOD);
        simulator.simulateTo(s, 5, 100);
        long today = s.lastSimulatedDay();
        int made = s.flow().produced(ResourceType.FOOD, today);
        int eaten = s.flow().consumed(ResourceType.FOOD, today);
        assertTrue(made > 0);
        assertEquals(5 * 4 * SettlementSimulator.ADULT_FOOD_PER_DAY, eaten);
        // The flow explains the change in stock exactly.
        assertEquals(before + made - eaten, s.ledger().get(ResourceType.FOOD));
    }

    @Test
    void donationsAreNotProduction() {
        Settlement s = farm(1);
        s.ledger().add(ResourceType.FOOD, 500);
        assertEquals(0, s.flow().produced(ResourceType.FOOD, s.lastSimulatedDay()));
    }

    @Test
    void inputsAreCountedAsConsumed() {
        Settlement s = farm(4);
        registry.enroll(s, UUID.randomUUID(), Occupation.TOOLSMITH, true, 0, null, null);
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.METAL, 3);
        simulator.simulateTo(s, 5, 100);
        assertEquals(3, s.flow().consumed(ResourceType.METAL, s.lastSimulatedDay()));
    }

    @Test
    void flowSurvivesSaveAndLoad() {
        Settlement s = farm(3);
        s.ledger().add(ResourceType.FOOD, 500);
        simulator.simulateTo(s, 6, 100);
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        long today = s.lastSimulatedDay();
        assertEquals(s.flow().produced(ResourceType.FOOD, today), loaded.flow().produced(ResourceType.FOOD, today));
        assertEquals(s.flow().consumed(ResourceType.FOOD, today), loaded.flow().consumed(ResourceType.FOOD, today));
        assertEquals(SettlementCodec.encode(s), SettlementCodec.encode(loaded));
    }

    @Test
    void migratesAFormatThreeSaveWithoutFlow() {
        Settlement s = farm(2);
        Map<String, Object> legacy = new LinkedHashMap<>(SettlementCodec.encode(s));
        legacy.put("format", 3);
        legacy.remove("flow");
        Settlement migrated = SettlementCodec.decode(legacy);
        assertEquals(0, migrated.flow().produced(ResourceType.FOOD, 10));
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(migrated).get("format"));
    }

    @Test
    void flowDaysGrowWithAgeUpToTheWindow() {
        Settlement s = farm(1);
        assertEquals(1, s.flowDays());
        simulator.simulateTo(s, 3, 100);
        assertEquals(3, s.flowDays());
        simulator.simulateTo(s, 30, 100);
        assertEquals(ResourceFlow.WINDOW_DAYS, s.flowDays());
    }
}
