package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** R4.48: a house lot holds the biggest house of its style, and a village builds the most beds it can pay for. */
class HouseLotTest {
    private static boolean fits(int[] lot, int width, int depth) {
        return (width <= lot[0] && depth <= lot[1]) || (depth <= lot[0] && width <= lot[1]);
    }

    /** The sizes (x by z) of the game's 26.2 houses, from the server jar (tools/piece_sizes.py). */
    @Test
    void theMediumHouseOfEveryStyleFitsItsLotAndInPlainsSoDoesTheBigOne() {
        assertTrue(fits(PlanGenerator.size(BuildingType.HOUSE, "plains"), 13, 11), "plains medium, 2 beds");
        assertTrue(fits(PlanGenerator.size(BuildingType.HOUSE, "plains"), 7, 11), "plains big, 4 beds");
        assertTrue(fits(PlanGenerator.size(BuildingType.HOUSE, "savanna"), 8, 15), "savanna medium");
        assertTrue(fits(PlanGenerator.size(BuildingType.HOUSE, "desert"), 6, 7), "desert medium");
        assertTrue(fits(PlanGenerator.size(BuildingType.HOUSE, "snowy"), 7, 8), "snowy medium");
        assertTrue(fits(PlanGenerator.size(BuildingType.HOUSE, "taiga"), 8, 7), "taiga medium");
        for (String style : List.of("plains", "desert", "savanna", "snowy", "taiga")) {
            assertTrue(fits(PlanGenerator.size(BuildingType.HOUSE, style), 7, 7), style + " small, 1 bed");
        }
    }

    @Test
    void aPlansHouseLotsAreAsBigAsItsStyleNeeds() {
        VillagePlan plains = PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64));
        List<VillagePlan.Lot> houses = plains.lots().stream().filter(l -> l.type() == BuildingType.HOUSE).toList();
        assertTrue(houses.size() >= 5, "stage one still has houses: " + houses.size());
        assertTrue(houses.stream().allMatch(l -> fits(new int[] {l.rect().width(), l.rect().depth()}, 13, 11)));
        VillagePlan desert = PlanGenerator.generate(0, 0, 7L, "desert", HeightSource.flat(64));
        assertTrue(desert.lots().stream().filter(l -> l.type() == BuildingType.HOUSE)
                .allMatch(l -> l.rect().width() * l.rect().depth() == 81), "9 by 9 where that already holds the medium house");
    }

    @Test
    void aPlanMadeAsPlainsTakesTheVillagesRealStyle() {
        VillagePlan plan = PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64));
        plan.setBiomeSet("desert");
        assertEquals("desert", plan.biomeSet());
        int lots = plan.lots().size();
        assertTrue(PlanGenerator.extend(plan, HeightSource.flat(64)));
        assertTrue(plan.lots().subList(lots, plan.lots().size()).stream().filter(l -> l.type() == BuildingType.HOUSE)
                .allMatch(l -> l.rect().width() * l.rect().depth() == 81), "what is laid out next suits a desert house");
    }

    @Test
    void aVillageBuildsTheHouseWithTheMostBedsItCanPayForTwiceOver() {
        TemplateCatalog.Template small = new TemplateCatalog.Template(BuildingType.HOUSE, "plains", 1, "small", TemplateCatalog.Source.VANILLA);
        TemplateCatalog.Template medium = new TemplateCatalog.Template(BuildingType.HOUSE, "plains", 2, "medium", TemplateCatalog.Source.VANILLA);
        TemplateCatalog.Template big = new TemplateCatalog.Template(BuildingType.HOUSE, "plains", 3, "big", TemplateCatalog.Source.VANILLA);
        List<TemplateCatalog.Template> ladder = List.of(small, medium, big);
        Function<TemplateCatalog.Template, Map<ResourceType, Integer>> cost = t -> Map.of(ResourceType.WOOD,
                t == small ? 10 : t == medium ? 30 : 70);
        Function<TemplateCatalog.Template, Integer> beds = t -> t == small ? 1 : t == medium ? 2 : 4;
        assertEquals(big, TemplateCatalog.mostBedsAffordable(ladder, 0, cost, beds, Map.of(ResourceType.WOOD, 150), 0).orElseThrow(),
                "wood for the big house twice over");
        assertEquals(medium, TemplateCatalog.mostBedsAffordable(ladder, 0, cost, beds, Map.of(ResourceType.WOOD, 100), 0).orElseThrow(),
                "the big one would take most of it: the medium one twice over");
        assertEquals(small, TemplateCatalog.mostBedsAffordable(ladder, 0, cost, beds, Map.of(ResourceType.WOOD, 35), 0).orElseThrow(),
                "nothing bigger twice over: the most beds for the cost, as before");
        assertEquals(small, TemplateCatalog.mostBedsAffordable(ladder, 0, cost, beds, Map.of(ResourceType.WOOD, 12), 0).orElseThrow());
        assertTrue(TemplateCatalog.mostBedsAffordable(ladder, 0, cost, beds, Map.of(ResourceType.WOOD, 5), 0).isEmpty());
        assertEquals(big, TemplateCatalog.mostBedsAffordable(ladder, 1, cost, beds, Map.of(ResourceType.WOOD, 150), 1).orElseThrow(), "an upgrade adds beds");
    }
}
