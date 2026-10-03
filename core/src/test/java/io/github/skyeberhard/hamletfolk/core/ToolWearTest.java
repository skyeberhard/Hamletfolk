package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * R3.6: gatherers wear out tools. The toolless penalty is off by default until a mine (R2.3) gives
 * METAL a source, so the penalty tests use a simulator with it switched on.
 */
class ToolWearTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator(true);

    private Settlement village(Occupation job, int workers) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < workers; i++) {
            registry.enroll(s, UUID.randomUUID(), job, true, 0, null, null);
        }
        s.ledger().add(ResourceType.FOOD, 100_000); // keep famine out of the way
        // The tool penalty only applies where a mine gives the village a way to get metal (R2.3).
        s.registerBuilding(new Building(BuildingType.MINE, 0, 64, 0, 0, "Test"));
        return s;
    }

    @Test
    void onlyGatheringOccupationsUseTools() {
        assertTrue(Occupation.FARMER.usesTools());
        assertTrue(Occupation.FISHERMAN.usesTools());
        assertTrue(Occupation.LUMBERJACK.usesTools());
        assertTrue(Occupation.MASON.usesTools());
        assertFalse(Occupation.TOOLSMITH.usesTools());
        assertFalse(Occupation.LIBRARIAN.usesTools());
        assertFalse(Occupation.UNEMPLOYED.usesTools());
    }

    @Test
    void gatherersConsumeToolsOverTime() {
        Settlement s = village(Occupation.MASON, 10);
        int stocked = SettlementSimulator.capacity(s, ResourceType.TOOLS); // fill the storage limit (R3.10)
        s.ledger().add(ResourceType.TOOLS, stocked);
        simulator.simulateTo(s, 7, 100); // within the 7-day flow window, so flow accounts for every worn tool
        int worn = stocked - s.ledger().get(ResourceType.TOOLS);
        // 10 masons x 7 days x 15% is about 10 tools; allow generous slack for the dice.
        assertTrue(worn > 0 && worn < 40, "worn tools: " + worn);
        assertEquals(worn, s.flow().consumed(ResourceType.TOOLS, s.lastSimulatedDay()));
    }

    @Test
    void toollessGatherersProduceLess() {
        // The same ten people and the same dice in both villages, so only the tools differ. Two independently
        // random villages differ in average work ethic by about 9%, which hid a 25% penalty about 1 run in 300.
        Settlement bare = village(Occupation.MASON, 10);
        Settlement equipped = SettlementCodec.decode(SettlementCodec.encode(bare));
        equipped.ledger().add(ResourceType.TOOLS, 1_000_000);
        // Each flow figure covers a 7-day window, so add up five separate windows: one window
        // of ten workers is small enough for the dice to hide a 25% penalty.
        int withTools = 0;
        int without = 0;
        for (int day = 7; day <= 35; day += 7) {
            simulator.simulateTo(equipped, day, 100);
            simulator.simulateTo(bare, day, 100);
            withTools += equipped.flow().produced(ResourceType.STONE, day);
            without += bare.flow().produced(ResourceType.STONE, day);
        }
        assertTrue(without < withTools, without + " should be below " + withTools);
    }

    @Test
    void aToolShortageIsRecordedOnceAndMentionedInDialogue() {
        Settlement s = village(Occupation.FARMER, 3);
        Resident farmer = s.residents().iterator().next();
        simulator.simulateTo(s, 10, 100);
        assertTrue(s.hasCondition("shortage:tools"));
        assertEquals(1, s.history().stream()
                .filter(e -> e.kind() == HistoryEvent.Kind.SHORTAGE && e.text().contains("tools")).count());
        assertTrue(Dialogue.speak(farmer, s, 10, new Random(1)).contains("tools"));

        // A smith's output (or a donation) ends the shortage.
        s.ledger().add(ResourceType.TOOLS, 1000);
        simulator.simulateTo(s, 11, 100);
        assertFalse(s.hasCondition("shortage:tools"));
    }

    @Test
    void byDefaultAToollessVillageIsNotPenalised() {
        // No job makes METAL yet, so smiths can't make tools; a penalty would starve every village.
        SettlementSimulator standard = new SettlementSimulator();
        Settlement bare = village(Occupation.MASON, 10);
        Settlement equipped = SettlementCodec.decode(SettlementCodec.encode(bare)); // same people, same dice
        equipped.ledger().add(ResourceType.TOOLS, 1_000_000);
        standard.simulateTo(equipped, 40, 100);
        standard.simulateTo(bare, 40, 100);
        long dayNow = equipped.lastSimulatedDay();
        assertEquals(equipped.flow().produced(ResourceType.STONE, dayNow), bare.flow().produced(ResourceType.STONE, dayNow));
        assertFalse(bare.hasCondition("shortage:tools"));
        assertTrue(bare.history().stream().noneMatch(e -> e.text().contains("tools")));
    }

    @Test
    void smithsDoNotWearOutTools() {
        Settlement s = village(Occupation.TOOLSMITH, 3);
        s.ledger().add(ResourceType.METAL, 100);
        s.ledger().add(ResourceType.TOOLS, 5);
        simulator.simulateTo(s, 5, 100);
        assertFalse(s.hasCondition("shortage:tools"));
        assertTrue(s.ledger().get(ResourceType.TOOLS) >= 5);
    }
}
