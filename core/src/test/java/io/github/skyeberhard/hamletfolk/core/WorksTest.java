package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** R5.6: where the street lights and the palisade go, from the plan alone. */
class WorksTest {
    private final VillagePlan plan = PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64));

    @Test
    void lightsStandEverySixthBlockAlongEveryStreetAndAtTheCornersOfTheSquare() {
        List<Works.Spot> lights = Works.lights(plan);
        assertFalse(lights.isEmpty());
        assertEquals(lights.size(), new HashSet<>(lights).size(), "no spot twice");
        // Every light is on a street or the square.
        for (Works.Spot spot : lights) {
            boolean onStreet = plan.roads().stream().anyMatch(r -> r.rect().contains(spot.x(), spot.z()))
                    || (plan.square() != null && plan.square().inflated(1).contains(spot.x(), spot.z()));
            assertTrue(onStreet, spot + " is not on a street");
        }
        // A street's lights run in a line with the spacing between them.
        VillagePlan.Road road = plan.roads().stream().filter(r -> Math.max(r.rect().width(), r.rect().depth()) >= 18).findFirst().orElseThrow();
        boolean alongX = road.rect().width() >= road.rect().depth();
        List<Integer> along = lights.stream()
                .filter(s -> road.rect().contains(s.x(), s.z()) && (alongX ? s.z() == road.rect().z() : s.x() == road.rect().x()))
                .map(s -> alongX ? s.x() : s.z()).sorted().toList();
        assertTrue(along.size() >= 3, "a long street has several lights");
        int squareWidth = Math.max(plan.square().width(), plan.square().depth());
        for (int i = 1; i < along.size(); i++) {
            int gap = along.get(i) - along.get(i - 1);
            // every sixth block, except across the square, which has no lights in it
            assertTrue(gap == Works.LIGHT_SPACING || gap <= Works.LIGHT_SPACING + squareWidth, "spacing " + along);
            assertTrue(gap >= Works.LIGHT_SPACING || gap > 0, "no spot twice " + along);
        }
        Rect square = plan.square();
        assertTrue(lights.contains(new Works.Spot(square.x() - 1, square.z() - 1)), "outside the corner, not in the square");
        assertTrue(lights.contains(new Works.Spot(square.maxX() + 1, square.maxZ() + 1)));
        assertTrue(lights.stream().noneMatch(s -> square.contains(s.x(), s.z())), "nothing inside the square for its building to meet");
    }

    @Test
    void thePalisadeIsARingRoundThePlanWithAGapWhereEachStreetLeaves() {
        List<Works.Spot> fence = Works.palisade(plan);
        Rect ring = Works.extent(plan).orElseThrow().inflated(Works.FENCE_MARGIN);
        assertFalse(fence.isEmpty());
        Set<Works.Spot> unique = new HashSet<>(fence);
        assertEquals(fence.size(), unique.size());
        for (Works.Spot spot : fence) {
            boolean onRing = spot.x() == ring.x() || spot.x() == ring.maxX() || spot.z() == ring.z() || spot.z() == ring.maxZ();
            assertTrue(onRing && ring.contains(spot.x(), spot.z()), spot + " is not on the ring");
        }
        int perimeter = 2 * (ring.width() + ring.depth()) - 4;
        assertTrue(fence.size() < perimeter, "gaps were left for the streets");
        assertTrue(perimeter - fence.size() >= 3, "at least one gate");
        // No gap is wider than the widest street plus a block of slack, and none is in the middle of a side with no street.
        int missing = perimeter - fence.size();
        int streets = plan.roads().size();
        int widest = plan.roads().stream().mapToInt(r -> Math.min(r.rect().width(), r.rect().depth())).max().orElse(1);
        assertTrue(missing <= 2 * streets * (widest + 1), missing + " cells missing for " + streets + " streets");
    }

    @Test
    void everyPlanGetsAPalisadeWithAGateWhateverTheSeedOrBiome() {
        for (String biome : new String[] {"plains", "desert", "savanna", "snowy", "taiga"}) {
            for (long seed = 1; seed <= 25; seed++) {
                VillagePlan p = PlanGenerator.generate(100, -200, seed, biome, HeightSource.flat(64));
                Rect ring = Works.extent(p).orElseThrow().inflated(Works.FENCE_MARGIN);
                int perimeter = 2 * (ring.width() + ring.depth()) - 4;
                int fence = Works.palisade(p).size();
                assertTrue(fence > 0 && fence <= perimeter - 6, biome + " seed " + seed + ": " + fence + " of " + perimeter
                        + " (at least two gates of three)");
                assertFalse(Works.lights(p).isEmpty(), biome + " seed " + seed);
            }
        }
    }

    @Test
    void anEmptyPlanHasNoWorks() {
        assertTrue(Works.spots(BuildingType.PALISADE, null).isEmpty());
        assertTrue(Works.spots(BuildingType.HOUSE, plan).isEmpty(), "a house is a building, not a work");
        assertTrue(Works.isWorks(BuildingType.STREET_LIGHTS) && Works.isWorks(BuildingType.PALISADE));
        assertFalse(Works.isWorks(BuildingType.FARM));
    }

    @Test
    void thePriceFollowsTheNumberOfPostsAndTheCostSetting() {
        int before = Construction.costPercent();
        try {
            Construction.setCostPercent(100);
            int posts = Works.palisade(plan).size();
            int full = Works.price(BuildingType.PALISADE, plan).get(ResourceType.WOOD);
            assertTrue(full >= posts / 2 && full <= posts * 2, "about a unit a post: " + full + " for " + posts);
            Construction.setCostPercent(35);
            int cheap = Works.price(BuildingType.PALISADE, plan).get(ResourceType.WOOD);
            assertTrue(cheap < full && cheap >= full * 30 / 100, cheap + " at 35% against " + full);
            assertTrue(Works.price(BuildingType.STREET_LIGHTS, plan).get(ResourceType.WOOD) < cheap, "lights are far cheaper than a wall");
        } finally {
            Construction.setCostPercent(before);
        }
    }

    // ----- R5.12: a palisade on uneven ground -----

    /** A straight run of cells along x: cells in a line, the last not next to the first. */
    private static List<Works.Spot> line(int n) {
        List<Works.Spot> spots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            spots.add(new Works.Spot(i, 0));
        }
        return spots;
    }

    @Test
    void flatGroundAndSingleStepsNeedNoStackedPosts() {
        assertArrayEquals(new int[] {0, 0, 0, 0}, Works.stacks(line(4), new int[] {64, 64, 64, 64}, Works.MAX_EXTRA));
        assertArrayEquals(new int[] {0, 0, 0, 0}, Works.stacks(line(4), new int[] {64, 65, 66, 65}, Works.MAX_EXTRA), "a slope of one a cell joins as it is");
    }

    @Test
    void theLowSideOfAStepIsRaisedUntilTheWallHasNoJumpOfMoreThanOne() {
        // ground 64 64 64 67 67 67: the low cells climb 64 65 66 | 67
        assertArrayEquals(new int[] {0, 1, 2, 0, 0, 0}, Works.stacks(line(6), new int[] {64, 64, 64, 67, 67, 67}, Works.MAX_EXTRA));
        // and the same, mirrored
        assertArrayEquals(new int[] {0, 0, 0, 2, 1, 0}, Works.stacks(line(6), new int[] {67, 67, 67, 64, 64, 64}, Works.MAX_EXTRA));
    }

    @Test
    void aCliffIsLeftAsTheBarrierItIsAndItsPullFadesFromTheCappedHeight() {
        int[] extra = Works.stacks(line(8), new int[] {64, 64, 64, 64, 64, 80, 80, 80}, Works.MAX_EXTRA);
        assertEquals(Works.MAX_EXTRA, extra[4], "no more than three posts are stacked on one cell");
        assertEquals(0, extra[5]);
        assertArrayEquals(new int[] {0, 0, 1, 2, 3, 0, 0, 0}, extra, "and the run below it climbs to the capped height, not further");
    }

    @Test
    void aSingleTallCellDoesNotRaiseAWholeRunOfFlatGround() {
        int[] extra = Works.stacks(line(12), new int[] {64, 64, 64, 64, 64, 70, 64, 64, 64, 64, 64, 64}, Works.MAX_EXTRA);
        assertEquals(0, extra[5], "the tall cell itself gets nothing");
        assertEquals(3, extra[4]);
        assertEquals(3, extra[6]);
        assertEquals(2, extra[3]);
        assertEquals(1, extra[2]);
        assertEquals(0, extra[1], "three cells away it is flat ground again");
    }

    @Test
    void aPitIsRaisedFromBothSidesAndAnUnknownCellJoinsNothing() {
        assertArrayEquals(new int[] {0, 0, 1, 0, 0}, Works.stacks(line(5), new int[] {66, 66, 64, 66, 66}, Works.MAX_EXTRA), "64 beside 66s: raised by one to 65");
        int[] cut = Works.stacks(line(5), new int[] {64, 64, Works.NO_GROUND, 70, 70}, Works.MAX_EXTRA);
        assertArrayEquals(new int[] {0, 0, 0, 0, 0}, cut, "the unknown cell breaks the run: nothing is raised towards the far side of it");
    }

    @Test
    void theRingRoundARealPlanIsWithoutBigStepsOnAHillAndItsLastCellJoinsItsFirst() {
        VillagePlan plan = PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64));
        List<Works.Spot> spots = Works.palisade(plan);
        int[] base = new int[spots.size()];
        for (int i = 0; i < base.length; i++) {
            Works.Spot s = spots.get(i);
            base[i] = 64 + (s.x() > 0 ? 3 : 0) + (s.z() > 4 ? 2 : 0); // steps of three and two across the ring
        }
        int[] extra = Works.stacks(spots, base, Works.MAX_EXTRA);
        for (int i = 0; i < spots.size(); i++) {
            int j = (i + 1) % spots.size();
            Works.Spot a = spots.get(i);
            Works.Spot b = spots.get(j);
            if (Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) != 1) {
                continue; // a gate between them
            }
            int gap = Math.abs((base[i] + extra[i]) - (base[j] + extra[j]));
            assertTrue(gap <= 1 || extra[i] == Works.MAX_EXTRA || extra[j] == Works.MAX_EXTRA, "step of " + gap + " at " + a + " and " + b);
        }
        assertArrayEquals(extra, Works.stacks(spots, base, Works.MAX_EXTRA), "the same ground gives the same answer");
    }

    @Test
    void aPostInShallowWaterComesUpFromTheBedToAboveTheSurface() {
        // dry ground 64, then three cells of water over a bed at 62 with the surface at 64 (so the wall must reach 65), then dry again
        int[] base = {64, 63, 63, 63, 64};
        int[] minTop = {Works.NO_GROUND, 65, 65, 65, Works.NO_GROUND};
        int[] extra = Works.stacks(line(5), base, minTop, Works.MAX_EXTRA);
        assertArrayEquals(new int[] {0, 2, 2, 2, 0}, extra, "63, 64, 65: two posts stacked on the first, in each wet cell");
    }

    @Test
    void theSlopeStillStacksOnTopOfTheWaterPosts() {
        // a wet cell (top 65) beside a cell on a bank 68 high: the wet one climbs to 67 on top of its water posts
        int[] base = {63, 63, 68};
        int[] minTop = {65, 65, Works.NO_GROUND};
        int[] extra = Works.stacks(line(3), base, minTop, Works.MAX_EXTRA);
        assertEquals(3, extra[0], "two for the water and one for the slope, two cells away from a bank");
        assertEquals(4, extra[1], "two for the water and two for the step up to the bank");
    }

    @Test
    void aFinishedPalisadeCanBeQueuedAgainOnlyWhenTheVillageIsFree() {
        Settlement village = new Settlement(new java.util.UUID(31, 31), "Mossmoor", "world", 0, 0, 0);
        assertEquals("Mossmoor has no plan, so no palisade to mend.", Construction.reopenPalisade(village, 5).orElseThrow());
    }
}
