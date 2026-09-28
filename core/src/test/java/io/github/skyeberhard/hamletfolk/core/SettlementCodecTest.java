package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R1.13: settlements.json carries a schema version, and loading an older one migrates cleanly. */
class SettlementCodecTest {

    @Test
    void roundTripPreservesEverything() {
        SettlementRegistry registry = new SettlementRegistry();
        Settlement s = registry.found("world", 10, -20, 3);
        Resident mother = registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 3, null, null);
        Resident child = registry.enroll(s, UUID.randomUUID(), Occupation.UNEMPLOYED, false, 9, mother.id(), null);
        child.recordConversation(UUID.randomUUID());
        s.ledger().add(ResourceType.METAL, 7);
        s.ledger().addTreasury(12);
        s.raiseThreat(33.5);
        new SettlementSimulator().simulateTo(s, 15, 100);

        Map<String, Object> encoded = SettlementCodec.encode(s);
        assertEquals(encoded, SettlementCodec.encode(SettlementCodec.decode(encoded)));
    }

    @Test
    void decodesNumbersThatCameBackAsDoubles() {
        // Gson reads every JSON number as a double; the codec must cope.
        SettlementRegistry registry = new SettlementRegistry();
        Settlement s = registry.found("world", 1, 2, 3);
        registry.enroll(s, UUID.randomUUID(), Occupation.MASON, true, 3, null, null);
        Map<String, Object> encoded = SettlementCodec.encode(s);

        Object widened = widen(encoded);
        Settlement decoded = SettlementCodec.decode((Map<?, ?>) widened);
        assertEquals(encoded, SettlementCodec.encode(decoded));
    }

    @Test
    void currentSavesDeclareTheCurrentFormat() {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 0);
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(s).get("format"));
    }

    @Test
    void migratesAFormatOneSaveWithoutTurned() {
        // What a genuine pre-R1.2 save looked like: format 1, no "turned" key at all.
        SettlementRegistry registry = new SettlementRegistry();
        Settlement original = registry.found("world", 4, 5, 2);
        registry.enroll(original, UUID.randomUUID(), Occupation.FARMER, true, 2, null, null);
        original.record(6, HistoryEvent.Kind.RAID, "Raiders were driven off.");

        Map<String, Object> legacy = new LinkedHashMap<>(SettlementCodec.encode(original));
        legacy.put("format", 1);
        legacy.remove("turned");

        Settlement migrated = SettlementCodec.decode(legacy);
        assertEquals(0, migrated.turnedCount());
        assertEquals(original.name(), migrated.name());
        assertEquals(original.population(), migrated.population());
        assertEquals(original.history().size(), migrated.history().size());
        // The migrated copy now declares the current format, not the one it was loaded from.
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(migrated).get("format"));
    }

    @Test
    void missingFormatFieldIsTreatedAsVersionOne() {
        SettlementRegistry registry = new SettlementRegistry();
        Settlement original = registry.found("world", 0, 0, 0);
        Map<String, Object> legacy = new LinkedHashMap<>(SettlementCodec.encode(original));
        legacy.remove("format");
        legacy.remove("turned");

        Settlement migrated = SettlementCodec.decode(legacy);
        assertEquals(0, migrated.turnedCount());
        assertEquals(original.id(), migrated.id());
    }

    @Test
    void refusesASaveFromANewerPluginVersion() {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 0);
        Map<String, Object> fromTheFuture = new LinkedHashMap<>(SettlementCodec.encode(s));
        fromTheFuture.put("format", SettlementCodec.FORMAT_VERSION + 1);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SettlementCodec.decode(fromTheFuture));
        assertTrue(e.getMessage().contains("newer version"));
    }

    private static Object widen(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(k, widen(v)));
            return copy;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(SettlementCodecTest::widen).toList();
        }
        return value;
    }
}
