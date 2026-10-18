package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** R8.32: generated buildings now have four tiers. */
class GeneratedTiersTest {
    private static int total(Blueprint b) {
        return b.cost().values().stream().mapToInt(Integer::intValue).sum();
    }

    @Test
    void everyGeneratedTypeHasFourTiersThatCostMoreAndFitASpareLot() {
        assertEquals(4, BuildingGenerator.TIERS);
        for (BuildingType type : BuildingType.values()) {
            if (!BuildingGenerator.generates(type)) {
                continue;
            }
            int before = 0;
            for (int tier = 1; tier <= BuildingGenerator.TIERS; tier++) {
                Blueprint b = BuildingGenerator.generate(type, tier).orElseThrow();
                assertTrue(b.width() <= 9 && b.depth() <= 9 || tier <= 2, type + " tier " + tier + " is " + b.width() + "x" + b.depth());
                assertTrue(b.sign().isPresent(), type + " tier " + tier + " keeps its sign");
                for (Blueprint.Block k : b.blocks()) {
                    assertTrue(k.x() >= 0 && k.x() < b.width() && k.z() >= 0 && k.z() < b.depth() && k.y() < b.height(), "in the box: " + type + " " + tier + " " + k);
                }
                int cost = total(b);
                assertTrue(cost > before, type + " tier " + tier + " costs " + cost + ", more than " + before);
                before = cost;
            }
        }
    }

    @Test
    void theFourthTierIsTallerWithALoftAndALadderUp() {
        Blueprint hall = BuildingGenerator.generate(BuildingType.SHOP, 3).orElseThrow();
        Blueprint loft = BuildingGenerator.generate(BuildingType.SHOP, 4).orElseThrow();
        assertEquals(hall.width(), loft.width());
        assertTrue(loft.height() > hall.height());
        assertTrue(loft.blocks().stream().anyMatch(k -> k.material().startsWith("LADDER") && k.y() == 4), "a ladder reaches the loft");
        assertTrue(loft.blocks().stream().anyMatch(k -> k.material().equals("OAK_PLANKS") && k.y() == 4), "a loft floor");
        assertTrue(hall.blocks().stream().noneMatch(k -> k.y() == 4 && k.material().equals("OAK_PLANKS")), "no loft in the hall");
    }

    @Test
    void aMinesShaftGoesDeeperEachTier() {
        for (int tier = 1; tier <= 4; tier++) {
            int low = BuildingGenerator.generate(BuildingType.MINE, tier).orElseThrow().blocks().stream().mapToInt(Blueprint.Block::y).min().orElseThrow();
            assertEquals(-(3 + 2 * (tier - 1)) - 1, low);
        }
    }
}
