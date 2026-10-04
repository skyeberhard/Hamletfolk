package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R1.8: a villager that stays in another settlement's area for 3 days moves there, and both histories record it. */
class MembershipTest {
    private static final int RADIUS = 96;
    private static final long DAY = 10_000;

    private final SettlementRegistry registry = new SettlementRegistry();
    private int next = 1;

    /** A settlement with a fixed id, centred at (x, 0) in the overworld. */
    private Settlement village(int x, String name) {
        Settlement s = new Settlement(new UUID(3, next++), name, "world", x, 0, DAY);
        registry.add(s);
        return s;
    }

    private Resident settle(Settlement s, Occupation occupation) {
        Resident r = new Resident(new UUID(4, next++), "Ada", "Reed", Gender.FEMALE, new Traits(50, 50, 50, 50),
                occupation, true, DAY, null, null, Needs.initial());
        s.addResident(r);
        registry.add(s);
        return r;
    }

    private java.util.Optional<Settlement> at(Resident r, int x, long day) {
        return Membership.observe(registry, r, "world", x, 0, RADIUS, day);
    }

    @Test
    void stayingThreeDaysInAnotherAreaMovesYouThere() {
        Settlement home = village(0, "Homeside");
        Settlement away = village(1000, "Awayford");
        settle(home, Occupation.NITWIT);
        settle(home, Occupation.NITWIT); // someone is left behind
        Resident mover = settle(home, Occupation.FARMER);

        assertTrue(at(mover, 1010, DAY + 1).isEmpty(), "first seen there: the count starts");
        assertTrue(at(mover, 990, DAY + 3).isEmpty(), "two days in");
        assertEquals(away, at(mover, 1005, DAY + 4).orElseThrow(), "three days in: they live there now");

        assertTrue(away.resident(mover.id()).isPresent());
        assertTrue(home.resident(mover.id()).isEmpty());
        assertEquals(away, registry.settlementOf(mover.id()).orElseThrow());
        assertEquals(Occupation.UNEMPLOYED, mover.occupation(), "their old job did not come with them");
        assertFalse(away.hasCondition(Migration.MOVING + mover.id()), "the villager is already there: nothing to bring over");
        assertTrue(home.history().stream().anyMatch(e -> e.kind() == HistoryEvent.Kind.DEPARTURE
                && e.text().contains("Awayford") && e.text().contains("Ada Reed")));
        assertTrue(away.history().stream().anyMatch(e -> e.kind() == HistoryEvent.Kind.ARRIVAL
                && e.text().contains("Homeside")));
    }

    @Test
    void goingHomeStartsTheCountOver() {
        Settlement home = village(0, "Homeside");
        Settlement away = village(1000, "Awayford");
        Resident r = settle(home, Occupation.NITWIT);

        at(r, 1000, DAY + 1);
        at(r, 1000, DAY + 3); // two days there
        assertTrue(at(r, 10, DAY + 4).isEmpty(), "back in the home area");
        assertTrue(home.conditions().keySet().stream().noneMatch(k -> k.startsWith(Membership.STRAYING)));
        at(r, 1000, DAY + 5); // the count starts again
        assertTrue(at(r, 1000, DAY + 7).isEmpty());
        assertEquals(away, at(r, 1000, DAY + 8).orElseThrow());
    }

    @Test
    void movingBetweenOtherAreasStartsANewCount() {
        Settlement home = village(0, "Homeside");
        village(1000, "Awayford");
        Settlement third = village(2000, "Thirdwick");
        Resident r = settle(home, Occupation.NITWIT);

        at(r, 1000, DAY + 1);
        at(r, 2000, DAY + 3); // different village: the count restarts
        assertTrue(at(r, 2000, DAY + 5).isEmpty());
        assertEquals(third, at(r, 2000, DAY + 6).orElseThrow());
    }

    @Test
    void insideYourOwnRadiusNeverCountsEvenIfAnotherVillageIsNearer() {
        Settlement home = village(0, "Homeside");
        village(120, "Closeby"); // centres 120 apart: their areas overlap
        Resident r = settle(home, Occupation.NITWIT);
        // 80 blocks from home (inside its radius) and 40 from Closeby (nearer): still home.
        for (long day = DAY; day < DAY + 20; day++) {
            assertTrue(at(r, 80, day).isEmpty());
        }
        assertEquals(home, registry.settlementOf(r.id()).orElseThrow());
    }

    @Test
    void outsideEveryAreaOrInAnotherWorldNothingHappens() {
        Settlement home = village(0, "Homeside");
        village(1000, "Awayford");
        Resident r = settle(home, Occupation.NITWIT);
        for (long day = DAY; day < DAY + 20; day++) {
            assertTrue(at(r, 500, day).isEmpty(), "in the wilderness");
            assertTrue(Membership.observe(registry, r, "nether", 1000, 0, RADIUS, day).isEmpty(), "another world");
        }
        assertEquals(home, registry.settlementOf(r.id()).orElseThrow());
    }

    @Test
    void someoneWhoseMoveIsStillBeingCarriedOutIsLeftAlone() {
        Settlement home = village(0, "Homeside");
        Settlement away = village(1000, "Awayford");
        Resident r = settle(home, Occupation.NITWIT);
        registry.migrate(r, home, away, DAY); // R4.2: moved on paper, villager still back in Homeside's area
        for (long day = DAY; day < DAY + 20; day++) {
            assertTrue(at(r, 0, day).isEmpty());
        }
        assertEquals(away, registry.settlementOf(r.id()).orElseThrow(), "not dragged back home");
    }

    @Test
    void anAbandonedSettlementCanBeResettledThisWay() {
        Settlement home = village(0, "Homeside");
        Settlement ghost = village(1000, "Ghostford");
        ghost.conditions().put("abandoned", DAY);
        Resident r = settle(home, Occupation.NITWIT);
        at(r, 1000, DAY + 1);
        assertEquals(ghost, at(r, 1000, DAY + 4).orElseThrow());
        assertEquals(1, ghost.population());
    }

    @Test
    void aJustArrivedMigrantIsNotCountedAsStrayingBackAndTheGraceEnds() {
        Settlement home = village(0, "Homeside");
        Settlement away = village(1000, "Awayford");
        Resident r = settle(home, Occupation.NITWIT);
        registry.migrate(r, home, away, DAY);
        away.completeMove(r.id(), DAY + 1); // the villager has been brought over
        assertFalse(away.hasCondition(Migration.MOVING + r.id()));
        // They wander back to Homeside's area and stay there.
        for (long day = DAY + 1; day < DAY + 1 + Migration.ARRIVAL_GRACE_DAYS; day++) {
            assertTrue(at(r, 0, day).isEmpty(), "within the grace period");
        }
        assertEquals(away, registry.settlementOf(r.id()).orElseThrow());
        // After the grace they count like anyone else.
        at(r, 0, DAY + 1 + Migration.ARRIVAL_GRACE_DAYS);
        assertEquals(home, at(r, 0, DAY + 1 + Migration.ARRIVAL_GRACE_DAYS + Membership.STAY_DAYS).orElseThrow());
        assertTrue(away.conditions().keySet().stream().noneMatch(k -> k.startsWith(Migration.ARRIVED)));
    }

    @Test
    void theCountSurvivesASaveAndGoesWithAResidentWhoLeaves() {
        Settlement home = village(0, "Homeside");
        village(1000, "Awayford");
        Resident r = settle(home, Occupation.NITWIT);
        at(r, 1000, DAY + 1);
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(home));
        assertTrue(loaded.conditions().keySet().stream().anyMatch(k -> k.startsWith(Membership.STRAYING + r.id())));

        // Once they are gone (they died, or moved some other way) their count goes with them.
        registry.remove(r.id());
        assertTrue(home.conditions().keySet().stream().noneMatch(k -> k.startsWith(Membership.STRAYING)));
    }
}
