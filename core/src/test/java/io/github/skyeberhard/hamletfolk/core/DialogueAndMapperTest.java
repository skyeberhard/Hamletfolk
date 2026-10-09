package io.github.skyeberhard.hamletfolk.core;

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
        s.ledger().add(ResourceType.TOOLS, 1000); // tool wear (R3.6) is tested separately
        // Food to last: the villagers are random, and with too little a famine starts and its line is spoken before the metal one.
        s.ledger().add(ResourceType.FOOD, 200);
        new SettlementSimulator().simulateTo(s, 2, 100);

        String line = Dialogue.speak(smith, s, 2, new Random(1));
        assertTrue(line.contains("metal"), line); // (every tone names what is missing)
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
        r.recordConversation(player); // (six now: a friend)
        assertTrue(Dialogue.greeting(r, player, "Skye").contains("Skye"), "a friend is greeted by name");
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

    @Test
    void lumberjackAndFletcherFormARealDependencyChain() {
        // R4.4/R4.5: fletcher no longer stands in as a de facto lumber source.
        assertEquals(ResourceType.WOOD, Occupation.LUMBERJACK.produces());
        assertEquals(null, Occupation.LUMBERJACK.consumes());
        assertEquals(ResourceType.GOODS, Occupation.FLETCHER.produces());
        assertEquals(ResourceType.WOOD, Occupation.FLETCHER.consumes());

        // Bukkit's real Villager.Profession enum has no "lumberjack" entry, so no actual
        // villager ever reports this key; the occupation is only ever reached by the
        // simulation assigning it directly (R4.3). This just checks the round-trip is sane.
        assertEquals(Occupation.LUMBERJACK, Occupation.fromVanillaKey("lumberjack"));
    }
}
