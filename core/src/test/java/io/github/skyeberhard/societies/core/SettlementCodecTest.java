package io.github.skyeberhard.societies.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
