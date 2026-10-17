package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.55: how the stores are pictured, and what taking from the picture costs. */
class StoreDisplayTest {
    private Settlement village() {
        return new Settlement(new UUID(300, 1), "Barrelton", "world", 0, 0, 0);
    }

    @Test
    void everyItemStandsForItsOwnCommodityInItsOwnCategory() {
        for (Commodity c : Commodity.values()) {
            var material = StoreDisplay.material(c);
            if (material.isEmpty()) {
                assertEquals(Commodity.WARES, c, "only the wares bucket has no item");
                continue;
            }
            if (ResourceMapper.value(material.get()).isEmpty()) {
                assertEquals(0, StoreDisplay.itemsShown(c, 1000), c + " cannot be valued, so it is not shown"); // (mined, never donated: not shown)
                continue;
            }
            assertEquals(c, StoreDisplay.commodityOf(material.get()).orElse(null), c + " is shown as " + material.get());
            assertEquals(c.category(), ResourceMapper.value(material.get()).orElseThrow().type(), c + " is valued in its own category");
        }
    }

    @Test
    void theCountFollowsTheLedgerAndIsCapped() {
        Settlement s = village();
        s.ledger().add(Commodity.BREAD, 10);
        assertEquals(10, StoreDisplay.contents(s.ledger(), ResourceType.FOOD).get(0).count());
        s.ledger().add(Commodity.COBBLESTONE, 5000);
        int shown = StoreDisplay.contents(s.ledger(), ResourceType.STONE).stream().mapToInt(StoreDisplay.Shown::count).sum();
        assertEquals(StoreDisplay.CAP, shown);
        assertTrue(StoreDisplay.contents(s.ledger(), ResourceType.METAL).isEmpty(), "nothing in the ledger, nothing on show");
    }

    @Test
    void stacksAreAtMostSixtyFour() {
        Settlement s = village();
        s.ledger().add(Commodity.COBBLESTONE, 5000);
        List<StoreDisplay.Shown> stacks = StoreDisplay.contents(s.ledger(), ResourceType.STONE);
        assertEquals(2, stacks.size());
        assertTrue(stacks.stream().allMatch(x -> x.count() <= 64));
    }

    @Test
    void aBuildingOfTheRightKindDrawsTheCategory() {
        Settlement s = village();
        assertTrue(StoreDisplay.site(s, ResourceType.FOOD).isEmpty(), "no building, no barrel");
        s.registerBuilding(new Building(BuildingType.FARM, 10, 64, 10, 1, "p"));
        assertEquals(BuildingType.FARM, StoreDisplay.site(s, ResourceType.FOOD).orElseThrow().type());
        s.registerBuilding(new Building(BuildingType.GRANARY, 20, 64, 20, 1, "p"));
        assertEquals(BuildingType.GRANARY, StoreDisplay.site(s, ResourceType.FOOD).orElseThrow().type(), "the granary is the better home");
        assertTrue(StoreDisplay.site(s, ResourceType.TOOLS).isEmpty());
    }

    @Test
    void takingCostsTheLedgerAtLeastTheWorthOfWhatIsTaken() {
        Settlement s = village();
        s.ledger().add(Commodity.IRON, 30);
        int before = s.ledger().get(Commodity.IRON);
        int units = StoreDisplay.take(s, "iron_ingot", 10);
        assertTrue(units > 0);
        assertEquals(before - units, s.ledger().get(Commodity.IRON));
        // taking one at a time never costs less than taking them together
        assertTrue(StoreDisplay.unitsFor("oak_log", 1) * 3 >= StoreDisplay.unitsFor("oak_log", 3));
    }

    @Test
    void youCannotTakeWhatTheLedgerNoLongerHolds() {
        Settlement s = village();
        s.ledger().add(Commodity.BREAD, 3);
        assertEquals(0, StoreDisplay.take(s, "bread", 50), "the picture was stale");
        assertEquals(3, s.ledger().get(Commodity.BREAD));
        assertEquals(0, StoreDisplay.take(s, "dirt", 1), "dirt is not on show");
    }

    @Test
    void aThiefIsNotTakenForAGiverForTenDays() {
        Settlement s = village();
        UUID thief = new UUID(9, 9);
        assertFalse(StoreDisplay.recentThief(s, thief, 5));
        StoreDisplay.noteTheft(s, thief, 5);
        assertTrue(StoreDisplay.recentThief(s, thief, 14));
        assertFalse(StoreDisplay.recentThief(s, thief, 15));
        assertFalse(StoreDisplay.recentThief(s, new UUID(9, 8), 6));
    }

    @Test
    void everythingShownCanBeTakenAndNeverForLessThanItsWorth() {
        for (Commodity c : Commodity.values()) {
            String material = StoreDisplay.material(c).orElse(null);
            if (material == null) {
                continue;
            }
            for (int units : new int[] {1, 3, 7, 64, 100, 257, 5000}) {
                Settlement s = village();
                s.ledger().add(c, units);
                for (StoreDisplay.Shown stack : StoreDisplay.contents(s.ledger(), c.category())) {
                    if (stack.commodity() != c) {
                        continue;
                    }
                    int cost = StoreDisplay.unitsFor(material, stack.count());
                    assertTrue(cost <= units, c + " " + units + ": a stack of " + stack.count() + " costs " + cost);
                    assertEquals(cost, StoreDisplay.take(s, material, stack.count()), c + " " + units);
                    break; // (the first stack; the ledger has changed)
                }
            }
        }
    }

    @Test
    void sixtyFiveIsASixtyFourAndAOne() {
        Settlement s = village();
        s.ledger().add(Commodity.GRAVEL, ResourceMapper.value("gravel").orElseThrow().unitsFor(65));
        List<StoreDisplay.Shown> stacks = StoreDisplay.contents(s.ledger(), ResourceType.STONE);
        assertEquals(List.of(64, 1), stacks.stream().map(StoreDisplay.Shown::count).toList(), stacks.toString());
    }

    @Test
    void aTheftCostsReputationByWorthWithinBounds() {
        assertEquals(1, StoreDisplay.theftLoss(ResourceType.FOOD, 1));
        assertEquals(StoreDisplay.MAX_THEFT_LOSS, StoreDisplay.theftLoss(ResourceType.METAL, 100000));
        assertTrue(StoreDisplay.theftLoss(ResourceType.FOOD, 400) >= StoreDisplay.theftLoss(ResourceType.FOOD, 40));
        assertFalse(StoreDisplay.theftLoss(ResourceType.WOOD, 0) < 1);
    }
}
