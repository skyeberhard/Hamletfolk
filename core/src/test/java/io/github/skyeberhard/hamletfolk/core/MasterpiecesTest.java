package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.60: masterpieces and legacy; R4.59: a village pays less for what its masters make. */
class MasterpiecesTest {
    private static int n;

    private Resident person(Occupation job, int days, UUID parent) {
        Resident r = new Resident(new UUID(98, n++), "Test", "Master" + n, Gender.MALE, new Traits(50, 50, 50, 50), job, true, 10_000, parent, null, Needs.initial());
        if (days > 0) {
            r.restoreXp(job, days);
        }
        return r;
    }

    private Settlement village() {
        return new Settlement(new UUID(99, n++), "Piecefold", "world", 0, 0, 0);
    }

    @Test
    void aMasterpieceIsTheSameEachTimeAndComesFromTheTrade() {
        Resident r = person(Occupation.FARMER, 100, null);
        assertEquals(Masterpieces.of(r), Masterpieces.of(r));
        assertFalse(Masterpieces.of(r).isEmpty());
        assertTrue(Masterpieces.promotion(r).contains(Masterpieces.of(r)));
    }

    @Test
    void theNoticeBoardListsLivingMastersOnly() {
        Settlement s = village();
        s.addResident(person(Occupation.FARMER, 100, null));
        s.addResident(person(Occupation.FARMER, 59, null));
        assertEquals(1, Masterpieces.living(s).size());
        assertTrue(NoticeBoard.pages(s).stream().anyMatch(p -> p.startsWith("PEOPLE OF NOTE")));
        Settlement none = village();
        none.addResident(person(Occupation.FARMER, 5, null));
        assertTrue(NoticeBoard.pages(none).stream().noneMatch(p -> p.startsWith("PEOPLE OF NOTE")));
    }

    @Test
    void aMastersDeathNamesWhomTheyTaught() {
        Settlement s = village();
        Resident master = person(Occupation.FARMER, 100, null);
        Resident child = person(Occupation.FARMER, 5, master.id());
        s.addResident(master);
        s.addResident(child);
        String legacy = Masterpieces.legacy(s, master, 50);
        assertTrue(legacy.contains("trained " + child.fullName()), legacy);
        assertTrue(legacy.contains(master.fullName()));
        Settlement lone = village();
        Resident m = person(Occupation.FARMER, 100, null);
        lone.addResident(m);
        assertTrue(Masterpieces.legacy(lone, m, 50).contains("left no pupil behind"));
        assertNull(Masterpieces.legacy(lone, person(Occupation.FARMER, 20, null), 50), "only a master is remembered so");
    }

    @Test
    void aVillageWithAMasterMakerPaysLessForItsGoods() {
        Settlement novices = village();
        novices.addResident(person(Occupation.LUMBERJACK, 0, null));
        Settlement masters = village();
        masters.addResident(person(Occupation.LUMBERJACK, 100, null));
        int full = TradeLevel.paid(novices, ResourceType.WOOD, 50);
        int less = TradeLevel.paid(masters, ResourceType.WOOD, 50);
        assertEquals(50, full);
        assertEquals(42, less); // 84% of 50
        assertEquals(50, TradeLevel.paid(masters, ResourceType.STONE, 50), "no mason or miner here");
    }

    @Test
    void aNewMastersMasterpieceIsKeptAsAnHeirloomAndOnlySixAreShown() {
        Settlement s = village();
        Resident r = person(Occupation.FARMER, 99, null);
        s.addResident(r);
        TradeLevel.worked(s, r, 7);
        var kept = Masterpieces.heirlooms(s);
        assertEquals(1, kept.size());
        assertEquals("golden_carrot", kept.get(0).material());
        assertEquals(r.fullName(), kept.get(0).maker());
        assertEquals(7, kept.get(0).day());
        assertTrue(kept.get(0).title().equalsIgnoreCase(Masterpieces.of(r)));
        for (int i = 0; i < 10; i++) {
            Resident other = person(Occupation.MINER, 99, null);
            s.addResident(other);
            TradeLevel.worked(s, other, 10 + i);
        }
        var all = Masterpieces.heirlooms(s);
        assertEquals(Masterpieces.MAX_HEIRLOOMS, all.size());
        assertEquals(19, all.get(0).day(), "newest first");
        assertTrue(all.stream().noneMatch(h -> h.maker().equals(r.fullName())), "the oldest made way");
    }
}
