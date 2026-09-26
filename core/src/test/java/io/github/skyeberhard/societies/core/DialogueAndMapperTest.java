package io.github.skyeberhard.societies.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DialogueAndMapperTest {

    @Test
    void idleSmithExplainsTheShortage() {
        SettlementRegistry registry = new SettlementRegistry();
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 3; i++) {
            registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
        }
        Resident smith = registry.enroll(s, UUID.randomUUID(), Occupation.TOOLSMITH, true, 0, null, null);
        new SettlementSimulator().simulateTo(s, 2, 100);

        String line = Dialogue.speak(smith, s, 2, new Random(1));
        assertTrue(line.contains("no metal"), line);
    }

    @Test
    void recentDeathIsMentioned() {
        SettlementRegistry registry = new SettlementRegistry();
        Settlement s = registry.found("world", 0, 0, 0);
        Resident r = registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
        s.ledger().add(ResourceType.FOOD, 100);
        s.record(5, HistoryEvent.Kind.DEATH, "Mira Oakes was killed by a zombie.");

        Optional<String> concern = Dialogue.urgentConcern(r, s, 6);
        assertTrue(concern.orElseThrow().contains("Mira Oakes"));
        assertTrue(Dialogue.urgentConcern(r, s, 20).isEmpty());
    }

    @Test
    void greetingWarmsUpWithFamiliarity() {
        Resident r = new SettlementRegistry().enroll(
                new SettlementRegistry().found("world", 0, 0, 0), UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
        UUID player = UUID.randomUUID();
        String first = Dialogue.greeting(r, player, "Skye");
        for (int i = 0; i < 5; i++) {
            r.recordConversation(player);
        }
        assertFalse(first.contains("Skye"));
        assertTrue(Dialogue.greeting(r, player, "Skye").contains("Good to see you"));
    }

    @Test
    void mapsCommonItems() {
        assertEquals(Optional.of(ResourceType.FOOD), ResourceMapper.classify("WHEAT"));
        assertEquals(Optional.of(ResourceType.FOOD), ResourceMapper.classify("cooked_beef"));
        assertEquals(Optional.of(ResourceType.WOOD), ResourceMapper.classify("minecraft:oak_log"));
        assertEquals(Optional.of(ResourceType.METAL), ResourceMapper.classify("IRON_INGOT"));
        assertEquals(Optional.of(ResourceType.TOOLS), ResourceMapper.classify("IRON_PICKAXE"));
        assertEquals(Optional.of(ResourceType.GOODS), ResourceMapper.classify("RED_WOOL"));
        assertTrue(ResourceMapper.classify("DIAMOND").isEmpty());
        assertTrue(ResourceMapper.isCurrency("EMERALD"));
    }

    @Test
    void mapsVanillaProfessionKeys() {
        assertEquals(Occupation.FARMER, Occupation.fromVanillaKey("minecraft:farmer"));
        assertEquals(Occupation.UNEMPLOYED, Occupation.fromVanillaKey("none"));
        assertEquals(Occupation.UNEMPLOYED, Occupation.fromVanillaKey("modded:alchemist"));
    }
}
