package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
