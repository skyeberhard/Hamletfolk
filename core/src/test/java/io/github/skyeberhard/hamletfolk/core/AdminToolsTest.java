package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R1.4: the logic behind /settlement admin inspect, rename and lookup. */
class AdminToolsTest {
    private final SettlementRegistry registry = new SettlementRegistry();

    @Test
    void findsBySettlementNameInAnyCase() {
        Settlement s = registry.found("world", 0, 0, 0);
        assertEquals(s, registry.find(s.name().toUpperCase()).orElseThrow());
        assertEquals(s, registry.find("  " + s.name().toLowerCase() + " ").orElseThrow());
        assertTrue(registry.find("nowhere at all").isEmpty());
        assertTrue(registry.find("").isEmpty());
    }

    @Test
    void findsByIdPrefixButNotByAnAmbiguousOrTinyOne() {
        Settlement s = registry.found("world", 0, 0, 0);
        assertEquals(s, registry.find(s.id().toString().substring(0, 8)).orElseThrow());
        assertTrue(registry.find(s.id().toString().substring(0, 2)).isEmpty());
    }

    @Test
    void duplicateGeneratedNamesAreReportedNotSilentlyPicked() {
        // Generated names can repeat; a lookup must not quietly pick the first.
        Settlement a = new Settlement(java.util.UUID.randomUUID(), "Ashford", "world", 0, 0, 0);
        Settlement b = new Settlement(java.util.UUID.randomUUID(), "Ashford", "world", 900, 0, 0);
        registry.add(a);
        registry.add(b);
        assertEquals(List.of(a, b), registry.findAll("ashford"));
        assertTrue(registry.find("Ashford").isEmpty());
        assertEquals(b, registry.find(b.id().toString().substring(0, 8)).orElseThrow());
    }

    @Test
    void namesWithSpacesAreFoundWhole() {
        Settlement s = registry.found("world", 0, 0, 0);
        registry.rename(s, "New Haven", 1);
        assertEquals(s, registry.find("new haven").orElseThrow());
        assertTrue(registry.findAll("New").isEmpty());
    }

    @Test
    void renameChangesTheNameAndNotesItInHistory() {
        Settlement s = registry.found("world", 0, 0, 3);
        String old = s.name();
        registry.rename(s, "New Haven", 5);
        assertEquals("New Haven", s.name());
        HistoryEvent last = s.history().get(s.history().size() - 1);
        assertEquals(old + " was renamed New Haven.", last.text());
        assertEquals(5, last.day());
        assertEquals("New Haven", SettlementCodec.decode(SettlementCodec.encode(s)).name());
    }

    @Test
    void renameRefusesBlankLongAndDuplicateNames() {
        Settlement a = registry.found("world", 0, 0, 0);
        Settlement b = registry.found("world", 500, 500, 0);
        assertThrows(IllegalArgumentException.class, () -> registry.rename(a, "   ", 1));
        assertThrows(IllegalArgumentException.class, () -> registry.rename(a, "x".repeat(33), 1));
        assertThrows(IllegalArgumentException.class, () -> registry.rename(a, b.name().toUpperCase(), 1));
        // Renaming to its own name (or its own name in another case) is harmless.
        registry.rename(a, a.name(), 1);
    }

    @Test
    void inspectReportsTheStateAnAdminNeeds() {
        Settlement s = registry.found("world", 12, -34, 0);
        registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
        registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, false, 0, null, null);
        s.ledger().add(ResourceType.FOOD, 50);
        s.ledger().addTreasury(7);
        new SettlementSimulator().simulateTo(s, 3, 100);

        String report = String.join("\n", SettlementInspector.report(s));
        assertTrue(report.contains(s.name()), report);
        assertTrue(report.contains(s.id().toString()), report);
        assertTrue(report.contains("world at 12, -34"), report);
        assertTrue(report.contains("Population: 2 (1 children"), report);
        // The village short of wood and stone has spent some of its 7 emeralds on requests (R3.3).
        assertTrue(report.contains("treasury " + s.ledger().treasury()), report);
        assertTrue(report.contains("Requests: ") && !report.contains("Requests: none"), report);
        assertTrue(report.contains("simulated through 3"), report);
        assertTrue(report.contains("Flow, last 7 days: food"), report);
        assertTrue(report.contains("FOUNDED"), report);
    }

    @Test
    void inspectShowsOnlyTheMostRecentEvents() {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 30; i++) {
            s.record(i, HistoryEvent.Kind.BIRTH, "Birth number " + i);
        }
        List<String> lines = SettlementInspector.report(s);
        String report = String.join("\n", lines);
        assertTrue(report.contains("Birth number 29"));
        assertTrue(!report.contains("Birth number 5\n") && !report.contains("Birth number 5"));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("History: 31 events")));
    }
}
