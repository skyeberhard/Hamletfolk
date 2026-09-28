package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** R1.21: history can't grow without bound; donations merge and major events outlast minor ones. */
class HistoryBoundsTest {

    private static long count(Settlement s, HistoryEvent.Kind kind) {
        return s.history().stream().filter(e -> e.kind() == kind).count();
    }

    @Test
    void repeatedDonationsFromOnePlayerMergeIntoOneLine() {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 0);
        for (int i = 0; i < 14; i++) {
            s.recordDonation(1 + i % 3, "Skye", 5, "wheat");
        }
        assertEquals(1, count(s, HistoryEvent.Kind.DONATION));
        HistoryEvent merged = s.history().get(s.history().size() - 1);
        assertEquals(14, merged.count());
        assertEquals("Skye made 14 donations this week.", merged.text());
    }

    @Test
    void differentDonorsAndDonationsAWeekApartStaySeparate() {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 0);
        s.recordDonation(1, "Skye", 5, "wheat");
        s.recordDonation(1, "Alex", 3, "iron ingot");
        s.recordDonation(1 + Settlement.DONATION_MERGE_DAYS, "Skye", 2, "wheat");
        assertEquals(3, count(s, HistoryEvent.Kind.DONATION));
    }

    @Test
    void aSingleDonationKeepsItsDetail() {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 0);
        s.recordDonation(1, "Skye", 5, "wheat");
        assertEquals("Skye gave 5 wheat to the village.", s.history().get(s.history().size() - 1).text());
    }

    @Test
    void historyIsCappedAndKeepsMajorEventsOverMinorOnes() {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 0);
        s.record(1, HistoryEvent.Kind.RAID, "Raiders came.");
        s.record(2, HistoryEvent.Kind.DEATH, "Someone died.");
        s.record(3, HistoryEvent.Kind.FAMINE, "Famine.");
        for (int i = 0; i < Settlement.MAX_HISTORY * 2; i++) {
            s.record(4 + i, HistoryEvent.Kind.SHORTAGE, "Work stopped " + i);
        }
        assertEquals(Settlement.MAX_HISTORY, s.history().size());
        assertEquals(HistoryEvent.Kind.FOUNDED, s.history().get(0).kind());
        assertEquals(1, count(s, HistoryEvent.Kind.RAID));
        assertEquals(1, count(s, HistoryEvent.Kind.DEATH));
        assertEquals(1, count(s, HistoryEvent.Kind.FAMINE));
        // The newest minor event survives; the oldest were dropped.
        assertEquals("Work stopped " + (Settlement.MAX_HISTORY * 2 - 1),
                s.history().get(s.history().size() - 1).text());
    }

    @Test
    void whenEverythingIsMajorTheOldestGoesButFoundingStays() {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 0);
        for (int i = 0; i < Settlement.MAX_HISTORY + 10; i++) {
            s.record(i, HistoryEvent.Kind.DEATH, "Death " + i);
        }
        assertEquals(Settlement.MAX_HISTORY, s.history().size());
        assertEquals(HistoryEvent.Kind.FOUNDED, s.history().get(0).kind());
        assertEquals("Death " + (Settlement.MAX_HISTORY + 9), s.history().get(s.history().size() - 1).text());
    }

    @Test
    void mergedDonationsSurviveSaveAndLoadAndKeepMerging() {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 0);
        s.recordDonation(1, "Skye", 5, "wheat");
        s.recordDonation(2, "Skye", 5, "wheat");
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        loaded.recordDonation(3, "Skye", 5, "wheat");
        assertEquals(1, count(loaded, HistoryEvent.Kind.DONATION));
        assertEquals(3, loaded.history().get(loaded.history().size() - 1).count());
    }

    @Test
    void migratesAFormatTwoSaveWhoseEventsHaveNoCountOrActor() {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 0);
        s.record(2, HistoryEvent.Kind.DONATION, "Skye gave 5 wheat to the village.");
        Map<String, Object> legacy = new java.util.LinkedHashMap<>(SettlementCodec.encode(s));
        legacy.put("format", 2);
        Settlement migrated = SettlementCodec.decode(legacy);
        HistoryEvent event = migrated.history().get(migrated.history().size() - 1);
        assertEquals(1, event.count());
        assertTrue(event.actor() == null);
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(migrated).get("format"));
    }
}
