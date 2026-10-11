package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** R9.5: the shared pathing service's fallback sequence (path, short hops, teleport or give up), with a fake mover, and road routes. */
class JourneyTest {
    /** A villager on flat ground that walks a quarter of a block a tick toward its walk target, with a brain that may interfere. */
    private static final class FakeMover implements Journey.Mover {
        double x;
        double z;
        final int y = 64;
        Journey.Point target;
        boolean walking;
        double speed = 0.25;
        /** The brain drops any walk target further than this from the villager (it "keeps cancelling the path"). */
        double dropBeyond = Double.MAX_VALUE;
        /** Whether a drop is something else taking over the walk target (true) or the brain finding no route (false). */
        boolean steerOnDrop;
        boolean steered;
        /** Ticks at which the brain drops whatever it is walking to. */
        List<Integer> dropAt = new ArrayList<>();
        /** Blocks the villager cannot stand in. */
        Predicate<Journey.Point> water = p -> false;
        boolean teleportWorks = true;
        /** Where a teleport really puts it (another plugin may redirect it); null: where it was asked. */
        Journey.Point teleportLands;
        boolean teleported;
        int tick;
        final List<Journey.Point> targets = new ArrayList<>();

        FakeMover(double x, double z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public Journey.Point position() {
            return new Journey.Point((int) Math.floor(x), y, (int) Math.floor(z));
        }

        @Override
        public void walkTo(Journey.Point t, int closeEnough) {
            target = t;
            walking = true;
            targets.add(t);
        }

        @Override
        public boolean steered() {
            return steered;
        }

        @Override
        public boolean walking() {
            return walking;
        }

        @Override
        public Optional<Journey.Point> standingNear(Journey.Point p) {
            Journey.Point ground = new Journey.Point(p.x(), y, p.z());
            return water.test(ground) ? Optional.empty() : Optional.of(ground);
        }

        @Override
        public boolean teleport(Journey.Point p) {
            if (!teleportWorks) {
                return false;
            }
            Journey.Point to = teleportLands == null ? p : teleportLands;
            x = to.x() + 0.5;
            z = to.z() + 0.5;
            teleported = true;
            walking = false;
            return true;
        }

        /** One game tick: the brain may interfere, then the villager moves. */
        void step() {
            tick++;
            if (walking && (dropAt.contains(tick) || Math.hypot(target.x() + 0.5 - x, target.z() + 0.5 - z) > dropBeyond)) {
                walking = false;
                steered = steerOnDrop;
            }
            if (!walking) {
                return;
            }
            double dx = target.x() + 0.5 - x;
            double dz = target.z() + 0.5 - z;
            double d = Math.hypot(dx, dz);
            if (d <= 1) {
                walking = false; // reached: the brain clears the walk target
                return;
            }
            double s = Math.min(speed, d);
            x += dx / d * s;
            z += dz / d * s;
        }
    }

    private final List<String> log = new ArrayList<>();

    private Journey.Result run(Journey journey, FakeMover mover) {
        for (int i = 0; i < 20_000; i++) {
            Optional<Journey.Result> r = journey.tick(mover);
            if (r.isPresent()) {
                return r.get();
            }
            mover.step();
        }
        throw new AssertionError("never ended: " + log);
    }

    private Journey journey(Journey.Point goal, boolean mayTeleport) {
        return new Journey(goal, List.of(), mayTeleport, 1, log::add);
    }

    private static Journey.Point at(int x, int z) {
        return new Journey.Point(x, 64, z);
    }

    @Test
    void anUntroubledVillagerWalksThere() {
        FakeMover m = new FakeMover(0.5, 0.5);
        Journey.Result r = run(journey(at(40, 0), true), m);
        assertEquals(Journey.Outcome.WALKED, r.outcome(), log.toString());
        assertTrue(r.ticks() > 100 && r.ticks() < 200, "about 40 blocks at a quarter a tick: " + r.ticks());
        assertFalse(m.teleported);
        assertEquals(1, m.targets.size(), "one path, set once");
        assertTrue(log.get(0).startsWith("setting off for x=40 y=64 z=0"), log.toString());
        assertTrue(r.describe().startsWith("walked there"), r.describe());
        assertTrue(r.outcome().arrived());
    }

    @Test
    void aPathTheBrainDropsNowAndThenIsSetAgain() {
        FakeMover m = new FakeMover(0.5, 0.5);
        m.dropAt = List.of(20, 60);
        Journey.Result r = run(journey(at(40, 0), true), m);
        assertEquals(Journey.Outcome.WALKED, r.outcome(), log.toString());
        assertTrue(log.stream().anyMatch(l -> l.startsWith("the brain dropped the path (1 of 3): trying the path again")), log.toString());
        assertTrue(log.stream().anyMatch(l -> l.startsWith("the brain dropped the path (2 of 3)")), log.toString());
        assertEquals(3, m.targets.size());
    }

    @Test
    void aPathReplacedAtOnceIsRetriedAfterAWaitNotThreeTimesInASecond() {
        FakeMover m = new FakeMover(0.5, 0.5);
        m.dropAt = List.of(2, 3, 4); // something else steers the villager for a moment, and the brain drops the path
        m.steerOnDrop = true;
        Journey.Result r = run(journey(at(40, 0), false), m);
        assertEquals(Journey.Outcome.WALKED, r.outcome(), log.toString());
        assertTrue(log.stream().anyMatch(l -> l.contains("at once (1 of 3): something else is steering it, waiting 2 seconds")), log.toString());
        assertFalse(log.stream().anyMatch(l -> l.startsWith("falling back to short hops")), "no hops: the wait was enough: " + log);
        assertEquals(2, m.targets.size(), "one path, one more after the wait");
    }

    @Test
    void aPathWithNoRouteIsRetriedAtOnceNotAfterAWait() {
        FakeMover m = new FakeMover(0.5, 0.5);
        m.dropAt = List.of(2, 4); // no route: the brain clears the target and nothing else sets one
        Journey.Result r = run(journey(at(40, 0), false), m);
        assertEquals(Journey.Outcome.WALKED, r.outcome(), log.toString());
        assertFalse(log.stream().anyMatch(l -> l.contains("something else is steering it")), log.toString());
        assertTrue(log.stream().anyMatch(l -> l.contains("at once") || l.contains("trying the path again")), log.toString());
    }

    @Test
    void whenTheBrainKeepsCancellingThePathItGoesInShortHops() {
        FakeMover m = new FakeMover(0.5, 0.5);
        m.dropBeyond = 8; // a long path is always dropped; a hop of six is not
        Journey.Result r = run(journey(at(40, 0), true), m);
        assertEquals(Journey.Outcome.HOPPED, r.outcome(), log.toString());
        assertFalse(m.teleported);
        assertTrue(log.contains("falling back to short hops: the brain dropped the path (3 times)"), log.toString());
        assertTrue(log.stream().anyMatch(l -> l.startsWith("hop 1 to ")), log.toString());
        for (Journey.Point t : m.targets.subList(3, m.targets.size())) {
            assertTrue(t.x() <= 41, "hops go toward the goal: " + t);
        }
        assertTrue(r.describe().startsWith("got there in short hops"), r.describe());
    }

    @Test
    void aHopWithNowhereToStandTurnsAside() {
        FakeMover m = new FakeMover(0.5, 0.5);
        m.dropBeyond = 8;
        m.water = p -> p.x() >= 4 && p.x() <= 20 && Math.abs(p.z()) <= 1; // a pond straight ahead
        Journey.Result r = run(journey(at(40, 0), true), m);
        assertEquals(Journey.Outcome.HOPPED, r.outcome(), log.toString());
        assertTrue(log.stream().anyMatch(l -> l.startsWith("nowhere to stand at x=6 y=64 z=0 (1 of 3): turning aside")), log.toString());
        for (Journey.Point t : m.targets) {
            assertFalse(m.water.test(t), "never sent into the pond: " + t);
        }
    }

    @Test
    void aVillagerThatCannotMoveIsTeleportedAndTheLogSaysWhy() {
        FakeMover m = new FakeMover(0.5, 0.5);
        m.speed = 0; // walled in
        Journey.Result r = run(journey(at(40, 0), true), m);
        assertEquals(Journey.Outcome.TELEPORTED, r.outcome(), log.toString());
        assertTrue(m.teleported);
        assertEquals(at(40, 0), m.position());
        int stalled = indexStarting("it got no nearer in 5 seconds (1 of 3)");
        int hops = indexStarting("falling back to short hops");
        int teleport = indexStarting("teleporting: the hop was taking too long (3 hops failed in a row)");
        assertTrue(stalled >= 0 && stalled < hops && hops < teleport, "path, then hops, then teleport: " + log);
        assertTrue(r.ticks() < 3 * Journey.STALL_TICKS + 4 * Journey.HOP_TICKS, "it does not wait for ever: " + r.ticks());
    }

    @Test
    void withoutLeaveToTeleportItGivesUpAndSaysSo() {
        FakeMover m = new FakeMover(0.5, 0.5);
        m.speed = 0;
        Journey.Result r = run(journey(at(40, 0), false), m);
        assertEquals(Journey.Outcome.GAVE_UP, r.outcome());
        assertFalse(m.teleported);
        assertTrue(r.detail().endsWith("teleporting is not allowed"), r.detail());
        assertFalse(r.outcome().arrived());
    }

    @Test
    void withNowhereSafeToStandAtTheGoalItGivesUp() {
        FakeMover m = new FakeMover(0.5, 0.5);
        m.speed = 0;
        m.teleportWorks = false;
        Journey.Result r = run(journey(at(40, 0), true), m);
        assertEquals(Journey.Outcome.GAVE_UP, r.outcome());
        assertTrue(r.detail().endsWith("no safe place to stand at the goal"), r.detail());
        assertTrue(log.contains("giving up: no safe place to stand at x=40 y=64 z=0"), log.toString());
    }

    @Test
    void itFollowsTheRoadWaypointsInOrder() {
        FakeMover m = new FakeMover(0.5, 0.5);
        Journey j = new Journey(at(30, 30), List.of(at(30, 0), at(30, 30)), true, 1, log::add);
        assertEquals(1, j.waypoints());
        Journey.Result r = run(j, m);
        assertEquals(Journey.Outcome.WALKED, r.outcome(), log.toString());
        assertEquals(List.of(at(30, 0), at(30, 30)), m.targets);
        assertTrue(log.contains("reached waypoint 1 of 1"), log.toString());
        assertTrue(log.get(0).contains("by road (1 waypoints)"), log.get(0));
    }

    @Test
    void aStoppedJourneyStaysStopped() {
        FakeMover m = new FakeMover(0.5, 0.5);
        Journey j = journey(at(40, 0), true);
        j.tick(m);
        Journey.Result r = j.stop("the brain module was switched off");
        assertEquals(Journey.Outcome.STOPPED, r.outcome());
        assertEquals(r, j.tick(m).orElseThrow());
        assertEquals(r, j.stop("again"), "the first reason stands");
        assertTrue(r.describe().contains("the brain module was switched off"), r.describe());
        assertEquals("setting off for x=? y=? z=?, 40 blocks away", BrainReport.redact(log.get(0)), "positions leave a report");
    }

    @Test
    void aGoalInTheAirIsReachedOnTheGroundBelowIt() {
        FakeMover m = new FakeMover(0.5, 0.5);
        Journey.Result r = run(new Journey(new Journey.Point(10, 68, 0), List.of(), true, 1, log::add), m);
        assertEquals(Journey.Outcome.WALKED, r.outcome(), log.toString());
        assertEquals(List.of(at(10, 0)), m.targets, "it walks to the ground under the goal");
        assertFalse(m.teleported);
        assertTrue(log.size() < 10, "no hopping on the spot: " + log);
    }

    @Test
    void aTeleportThatLandsSomewhereElseIsNotAnArrival() {
        FakeMover m = new FakeMover(0.5, 0.5);
        m.speed = 0;
        m.teleportLands = at(0, 30);
        Journey.Result r = run(journey(at(40, 0), true), m);
        assertEquals(Journey.Outcome.GAVE_UP, r.outcome(), log.toString());
        assertFalse(r.outcome().arrived());
    }

    @Test
    void aVillagerAlreadyThereHasArrived() {
        FakeMover m = new FakeMover(40.5, 0.5);
        Journey.Result r = run(journey(at(40, 0), true), m);
        assertEquals(Journey.Outcome.WALKED, r.outcome());
        assertEquals(1, r.ticks());
        assertTrue(m.targets.isEmpty());
    }

    private int indexStarting(String prefix) {
        for (int i = 0; i < log.size(); i++) {
            if (log.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    // ----- road routes -----

    @Test
    void aShortTripOrOneFarFromTheStreetsGoesStraight() {
        List<Rect> street = List.of(new Rect(0, 0, 61, 3));
        assertEquals(List.of(at(10, 5)), RoadRoute.route(street, at(0, 5), at(10, 5)));
        assertEquals(List.of(at(60, 100)), RoadRoute.route(street, at(0, 100), at(60, 100)), "both far from the street");
        assertEquals(List.of(at(60, 5)), RoadRoute.route((VillagePlan) null, at(0, 5), at(60, 5)), "no plan");
    }

    @Test
    void aTripAcrossTheVillageFollowsTheStreetsRoundTheCorner() {
        List<Rect> streets = List.of(new Rect(0, 0, 61, 3), new Rect(58, 0, 3, 61)); // an L
        Journey.Point from = at(0, 10);
        Journey.Point to = at(70, 60);
        List<Journey.Point> route = RoadRoute.route(streets, from, to);
        assertEquals(to, route.get(route.size() - 1));
        assertTrue(route.size() >= 4, route.toString());
        assertTrue(route.stream().anyMatch(p -> p.x() >= 57 && p.z() <= 3), "turns at the corner: " + route);
        java.util.function.Predicate<Journey.Point> onStreet = p -> streets.stream().anyMatch(r -> r.contains(p.x(), p.z()));
        for (Journey.Point p : route.subList(0, route.size() - 1)) {
            assertTrue(onStreet.test(p), "a waypoint is on the street: " + p);
        }
        for (int i = 1; i < route.size() - 1; i++) {
            assertTrue(route.get(i).horizontalDistance(route.get(i - 1)) <= RoadRoute.MAX_LEG + 1, "legs are short: " + route);
        }
        assertEquals(route, RoadRoute.route(streets, from, to), "the same every time");
    }

    @Test
    void streetsThatGoTheLongWayRoundAreNotFollowed() {
        List<Rect> u = List.of(new Rect(-5, 0, 3, 101), new Rect(-5, 98, 51, 3), new Rect(43, 0, 3, 101));
        assertEquals(List.of(at(40, 0)), RoadRoute.route(u, at(0, 0), at(40, 0)));
    }

    @Test
    void streetsThatDoNotJoinAreNotFollowed() {
        List<Rect> apart = List.of(new Rect(0, 0, 20, 3), new Rect(40, 0, 20, 3));
        assertEquals(List.of(at(58, 5)), RoadRoute.route(apart, at(1, 5), at(58, 5)));
    }
}
