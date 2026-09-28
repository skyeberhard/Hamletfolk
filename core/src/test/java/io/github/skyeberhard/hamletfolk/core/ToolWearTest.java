package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R3.6: gatherers wear out tools and produce less while the village has none. */
class ToolWearTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();

    private Settlement village(Occupation job, int workers) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < workers; i++) {
            registry.enroll(s, UUID.randomUUID(), job, true, 0, null, null);
        }
        s.ledger().add(ResourceType.FOOD, 100_000); // keep famine out of the way
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
        s.ledger().add(ResourceType.TOOLS, 10_000);
        simulator.simulateTo(s, 7, 100); // within the 7-day flow window, so flow accounts for every worn tool
        int worn = 10_000 - s.ledger().get(ResourceType.TOOLS);
        // 10 masons x 7 days x 15% is about 10 tools; allow generous slack for the dice.
        assertTrue(worn > 0 && worn < 40, "worn tools: " + worn);
        assertEquals(worn, s.flow().consumed(ResourceType.TOOLS, s.lastSimulatedDay()));
    }

    @Test
    void toollessGatherersProduceLess() {
        Settlement equipped = village(Occupation.MASON, 10);
        equipped.ledger().add(ResourceType.TOOLS, 1_000_000);
        Settlement bare = village(Occupation.MASON, 10);
        simulator.simulateTo(equipped, 40, 100);
        simulator.simulateTo(bare, 40, 100);
        long dayNow = equipped.lastSimulatedDay();
        int withTools = equipped.flow().produced(ResourceType.STONE, dayNow);
        int without = bare.flow().produced(ResourceType.STONE, dayNow);
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
    void smithsDoNotWearOutTools() {
        Settlement s = village(Occupation.TOOLSMITH, 3);
        s.ledger().add(ResourceType.METAL, 100);
        s.ledger().add(ResourceType.TOOLS, 5);
        simulator.simulateTo(s, 5, 100);
        assertFalse(s.hasCondition("shortage:tools"));
        assertTrue(s.ledger().get(ResourceType.TOOLS) >= 5);
    }
}
