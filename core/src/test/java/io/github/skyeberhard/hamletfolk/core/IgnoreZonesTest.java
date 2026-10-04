package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** R1.30: a sign exempts the villagers around it, with limits so it cannot switch a village off. */
class IgnoreZonesTest {
    @Test
    void theSignTextIsRecognisedInItsUsualSpellings() {
        assertTrue(IgnoreZones.isSign("[Exempt]"));
        assertTrue(IgnoreZones.isSign("[exempt]"));
        assertTrue(IgnoreZones.isSign("[ Ignore ]"));
        assertTrue(IgnoreZones.isSign(" [IGNORE] "));
        assertFalse(IgnoreZones.isSign("Exempt"), "brackets are needed, so a sign that just says so is left alone");
        assertFalse(IgnoreZones.isSign("[Farm]"));
        assertFalse(IgnoreZones.isSign("[]"));
        assertFalse(IgnoreZones.isSign(null));
    }

    @Test
    void theRadiusComesFromTheNextLineWithinLimits() {
        assertEquals(IgnoreZones.DEFAULT_RADIUS, IgnoreZones.radius(""));
        assertEquals(IgnoreZones.DEFAULT_RADIUS, IgnoreZones.radius(null));
        assertEquals(IgnoreZones.DEFAULT_RADIUS, IgnoreZones.radius("big"));
        assertEquals(IgnoreZones.DEFAULT_RADIUS, IgnoreZones.radius("12 blocks"));
        assertEquals(16, IgnoreZones.radius("16"));
        assertEquals(16, IgnoreZones.radius(" 16 "));
        assertEquals(IgnoreZones.MIN_RADIUS, IgnoreZones.radius("1"));
        assertEquals(IgnoreZones.MAX_RADIUS, IgnoreZones.radius("500"));
        assertEquals(IgnoreZones.DEFAULT_RADIUS, IgnoreZones.radius("99999"), "too many digits is not a number we trust");
    }

    @Test
    void aZoneCoversWhatIsWithinItsRadiusAndNothingBeyond() {
        IgnoreZones zones = new IgnoreZones();
        zones.add(new IgnoreZones.Zone(100, 64, 100, 10, "skye", false));
        assertTrue(zones.covers(100, 64, 100));
        assertTrue(zones.covers(110, 64, 100), "on the edge");
        assertFalse(zones.covers(111, 64, 100));
        assertTrue(zones.covers(107, 64, 107), "a diagonal inside the circle");
        assertFalse(zones.covers(108, 64, 108), "a diagonal outside it");
        assertTrue(zones.covers(100, 74, 100), "as high as the radius");
        assertFalse(zones.covers(100, 75, 100));
        assertFalse(new IgnoreZones().covers(0, 0, 0), "no zones, nothing exempt");
    }

    @Test
    void aSignEditedInPlaceReplacesItsZoneAndBreakingItRemovesIt() {
        IgnoreZones zones = new IgnoreZones();
        assertEquals(IgnoreZones.Result.ADDED, zones.add(new IgnoreZones.Zone(0, 64, 0, 10, "skye", false)));
        assertEquals(IgnoreZones.Result.REPLACED, zones.add(new IgnoreZones.Zone(0, 64, 0, 30, "skye", false)));
        assertEquals(1, zones.zones().size());
        assertTrue(zones.covers(25, 64, 0), "the new radius");
        assertTrue(zones.removeAt(0, 64, 0));
        assertFalse(zones.removeAt(0, 64, 0), "already gone");
        assertTrue(zones.isEmpty());
    }

    @Test
    void aPlayerHasAtMostFiveZonesAndAWorldAHundred() {
        IgnoreZones zones = new IgnoreZones();
        for (int i = 0; i < IgnoreZones.MAX_PER_PLAYER; i++) {
            assertEquals(IgnoreZones.Result.ADDED, zones.add(new IgnoreZones.Zone(i * 200, 64, 0, 10, "Skye", false)));
        }
        assertEquals(IgnoreZones.Result.TOO_MANY_FOR_PLAYER, zones.add(new IgnoreZones.Zone(5000, 64, 0, 10, "skye", false)),
                "the limit is per player, whatever their capitals");
        assertEquals(IgnoreZones.Result.ADDED, zones.add(new IgnoreZones.Zone(5000, 64, 0, 10, "someone else", false)));
        // Moving a sign's zone does not count against the limit: it is the same zone.
        assertEquals(IgnoreZones.Result.REPLACED, zones.add(new IgnoreZones.Zone(0, 64, 0, 20, "skye", false)));

        IgnoreZones crowded = new IgnoreZones();
        for (int i = 0; i < IgnoreZones.MAX_TOTAL; i++) {
            assertEquals(IgnoreZones.Result.ADDED, crowded.add(new IgnoreZones.Zone(i * 100, 64, 0, 10, "player" + i, false)));
        }
        assertEquals(IgnoreZones.Result.FULL, crowded.add(new IgnoreZones.Zone(99999, 64, 0, 10, "newcomer", false)));
    }

    @Test
    void takingOverSomeoneElsesSignCountsAsOneOfYoursAndAnAdminZoneWinsWhereZonesOverlap() {
        IgnoreZones zones = new IgnoreZones();
        for (int i = 0; i < IgnoreZones.MAX_PER_PLAYER; i++) {
            assertEquals(IgnoreZones.Result.ADDED, zones.add(new IgnoreZones.Zone(i * 200, 64, 0, 10, "bob", false)));
        }
        zones.add(new IgnoreZones.Zone(5000, 64, 0, 10, "alice", false));
        // Bob already has five, and cannot take Alice's sign over by editing it.
        assertEquals(IgnoreZones.Result.TOO_MANY_FOR_PLAYER, zones.add(new IgnoreZones.Zone(5000, 64, 0, 10, "bob", false)));
        assertEquals("alice", zones.coveringZone(5000, 64, 0).orElseThrow().owner());
        // Overlapping zones: the one that overrides villages is the one reported.
        zones.add(new IgnoreZones.Zone(5005, 64, 0, 10, "admin", true));
        assertTrue(zones.coveringZone(5000, 64, 0).orElseThrow().overridesVillages());
        assertTrue(zones.coveringZone(0, 64, 0).isPresent() && !zones.coveringZone(0, 64, 0).orElseThrow().overridesVillages());
        assertTrue(zones.coveringZone(900, 64, 0).isEmpty());
    }

    @Test
    void zonesSurviveStoringAndDamagedLinesAreSkipped() {
        IgnoreZones zones = new IgnoreZones();
        zones.add(new IgnoreZones.Zone(-5, 70, 12, 24, "Skye", false));
        zones.add(new IgnoreZones.Zone(300, 20, -300, 8, "Owen, the builder", false));
        IgnoreZones loaded = IgnoreZones.decode(zones.encode());
        assertEquals(zones.zones(), loaded.zones());

        IgnoreZones damaged = IgnoreZones.decode(List.of("1,2,3,10,false,skye", "nonsense", "1,2,x,10,false,skye", "4,5,6,7", "7,8,9,1000,true,big"));
        assertEquals(2, damaged.zones().size(), "the good line and the oversized radius, which is clamped");
        assertTrue(damaged.zones().get(1).overridesVillages());
        assertEquals(IgnoreZones.MAX_RADIUS, damaged.zones().get(1).radius());
    }
}
