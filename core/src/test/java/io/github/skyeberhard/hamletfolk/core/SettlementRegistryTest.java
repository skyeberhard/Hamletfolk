package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementRegistryTest {
    private final SettlementRegistry registry = new SettlementRegistry();

    @Test
    void nearestRespectsWorldAndRadius() {
        Settlement near = registry.found("world", 0, 0, 0);
        registry.found("world", 500, 500, 0);
        registry.found("world_nether", 0, 0, 0);

        assertSame(near, registry.nearest("world", 30, 40, 96).orElseThrow());
        assertTrue(registry.nearest("world", 250, 250, 96).isEmpty());
    }

    @Test
    void foundingRecordsHistory() {
        Settlement s = registry.found("world", 0, 0, 12);
        assertEquals(HistoryEvent.Kind.FOUNDED, s.history().get(0).kind());
        assertEquals(12, s.foundedDay());
    }

    @Test
    void childrenInheritFamilyName() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident mother = registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
        Resident father = registry.enroll(s, UUID.randomUUID(), Occupation.MASON, true, 0, null, null);
        Resident child = registry.enroll(s, UUID.randomUUID(), Occupation.UNEMPLOYED, false, 5, mother.id(), father.id());

        assertEquals(mother.familyName(), child.familyName());
        assertEquals(mother.id(), child.parentA());
        assertFalse(child.adult());
    }

    @Test
    void identityIsStableForTheSameEntity() {
        UUID id = UUID.randomUUID();
        Resident a = registry.enroll(registry.found("world", 0, 0, 0), id, Occupation.FARMER, true, 0, null, null);
        SettlementRegistry other = new SettlementRegistry();
        Resident b = other.enroll(other.found("world", 0, 0, 0), id, Occupation.FARMER, true, 0, null, null);
        assertEquals(a.fullName(), b.fullName());
        assertEquals(a.traits(), b.traits());
    }

    @Test
    void removeUpdatesIndex() {
        Settlement s = registry.found("world", 0, 0, 0);
        UUID id = UUID.randomUUID();
        registry.enroll(s, id, Occupation.FARMER, true, 0, null, null);
        assertTrue(registry.remove(id).isPresent());
        assertTrue(registry.resident(id).isEmpty());
        assertEquals(0, s.population());
    }
}
