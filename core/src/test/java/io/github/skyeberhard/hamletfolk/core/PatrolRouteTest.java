package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R9.6: the loop a guard walks, where it starts, and how long it stays at each spot. */
class PatrolRouteTest {
    private static VillagePlan plan(long seed) {
        return PlanGenerator.generate(0, 0, seed, "plains", HeightSource.flat(64));
    }

    @Test
    void aVillageWithAPlanIsWalkedThroughItsSquareAndStreets() {
        VillagePlan plan = plan(7L);
        List<PatrolRoute.Spot> route = PatrolRoute.route(plan, 0, 0);
        assertTrue(route.size() >= 3 && route.size() <= PatrolRoute.maxSpots(plan), route.toString());
        Rect square = plan.square();
        assertTrue(route.stream().anyMatch(s -> square.contains(s.x(), s.z())), "it passes through the square: " + route);
        for (VillagePlan.Road road : plan.roads()) {
            assertTrue(route.stream().anyMatch(s -> road.rect().inflated(1).contains(s.x(), s.z())) || route.size() == PatrolRoute.maxSpots(plan),
                    "every street is walked, unless thinned: " + road);
        }
    }

    @Test
    void noSpotIsCloserThanTheGapToAnotherAndThereAreNoRepeats() {
        for (long seed = 1; seed <= 12; seed++) {
            List<PatrolRoute.Spot> route = PatrolRoute.route(plan(seed), 0, 0);
            assertEquals(route.size(), new HashSet<>(route).size());
            for (int i = 0; i < route.size(); i++) {
                for (int j = i + 1; j < route.size(); j++) {
                    PatrolRoute.Spot a = route.get(i);
                    PatrolRoute.Spot b = route.get(j);
                    assertTrue(Math.hypot(a.x() - b.x(), a.z() - b.z()) >= PatrolRoute.MIN_GAP, seed + ": " + a + " " + b);
                }
            }
        }
    }

    @Test
    void theSameVillageGivesTheSameRouteEveryTime() {
        assertEquals(PatrolRoute.route(plan(3L), 0, 0), PatrolRoute.route(plan(3L), 0, 0));
    }

    @Test
    void aVillageWithNoPlanIsWalkedRoundItsCentre() {
        List<PatrolRoute.Spot> route = PatrolRoute.route(null, 100, -40);
        assertEquals(6, route.size());
        for (PatrolRoute.Spot s : route) {
            assertEquals(PatrolRoute.RING, Math.hypot(s.x() - 100, s.z() - -40), 1.0, s.toString());
        }
    }

    @Test
    void eachGuardStartsAtAPlaceOfItsOwnAndTheLoopCloses() {
        Set<Integer> starts = new HashSet<>();
        Random ids = new Random(5);
        for (int i = 0; i < 40; i++) {
            int start = PatrolRoute.startIndex(new UUID(ids.nextLong(), ids.nextLong()), 8);
            assertTrue(start >= 0 && start < 8);
            starts.add(start);
        }
        assertTrue(starts.size() >= 5, "guards spread round the loop: " + starts);
        UUID id = new UUID(7, 11);
        assertEquals(PatrolRoute.startIndex(id, 8), PatrolRoute.startIndex(id, 8));
        assertEquals(0, PatrolRoute.startIndex(id, 0));
        assertEquals(0, PatrolRoute.next(7, 8), "after the last, the first");
        assertEquals(3, PatrolRoute.next(2, 8));
    }

    @Test
    void guardsTakeTurnsAtTheNightWatchAndALoneGuardKeepsIt() {
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        UUID c = new UUID(0, 3);
        assertTrue(PatrolRoute.watchesNight(List.of(a), a), "a lone guard keeps it");
        assertTrue(PatrolRoute.watchesNight(List.of(a, b), a));
        assertFalse(PatrolRoute.watchesNight(List.of(b, a), b), "the order they are listed in does not matter");
        assertTrue(PatrolRoute.watchesNight(List.of(c, b, a), c));
        assertFalse(PatrolRoute.watchesNight(List.of(c, b, a), b));
        assertTrue(PatrolRoute.watchesNight(List.of(), a), "not in the list: keeps it, rather than nobody");
    }

    @Test
    void biggerVillagesGetLongerRoutesThatStillReachMostStreets() {
        VillagePlan plan = plan(3L);
        int previous = 0;
        while (true) {
            List<PatrolRoute.Spot> route = PatrolRoute.route(plan, 0, 0);
            assertTrue(route.size() <= PatrolRoute.maxSpots(plan), "stage " + plan.stage() + ": " + route.size());
            assertTrue(route.size() >= previous, "a bigger plan never has a shorter route");
            previous = route.size();
            long reached = plan.roads().stream()
                    .filter(road -> route.stream().anyMatch(s -> road.rect().inflated(2).contains(s.x(), s.z()))).count();
            assertTrue(reached * 4 >= plan.roads().size() * 3, "stage " + plan.stage() + ": " + reached + " of " + plan.roads().size()
                    + " streets have a spot on them");
            if (!PlanGenerator.extend(plan, HeightSource.flat(64))) {
                break;
            }
        }
        assertTrue(plan.stage() > 1, "the plan did grow");
    }

    @Test
    void aSpotKnowsItsGroundOnlyWhenTheyAreTold() {
        PatrolRoute.Spot spot = PatrolRoute.Spot.at(10, -4);
        assertEquals(PatrolRoute.NO_GROUND, spot.y());
        assertEquals(70, spot.withGround(70).y());
        assertEquals("x=10 z=-4", spot.withGround(70).toString(), "no height in a report");
    }

    @Test
    void theyStayLongByDayAndBrieflyAtNight() {
        Random random = new Random(1);
        for (int i = 0; i < 200; i++) {
            int day = PatrolRoute.dwell(false, random);
            int night = PatrolRoute.dwell(true, random);
            assertTrue(day >= PatrolRoute.DAY_DWELL_MIN && day <= PatrolRoute.DAY_DWELL_MAX, "" + day);
            assertTrue(night >= PatrolRoute.NIGHT_DWELL_MIN && night <= PatrolRoute.NIGHT_DWELL_MAX, "" + night);
        }
    }
}
