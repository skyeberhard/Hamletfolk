package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R3.1: trade prices rise when the village is short of a resource and fall when it has plenty. */
class PriceModelTest {
    private final SettlementRegistry registry = new SettlementRegistry();

    /** Ten residents: the village wants 100 food and 30 of anything else. */
    private Settlement village(int residents) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < residents; i++) {
            s.addResident(new Resident(new UUID(0, i + 1), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                    Occupation.NITWIT, true, 10_000, null, null, Needs.initial()));
        }
        return s;
    }

    private static Settlement withStock(Settlement s, ResourceType type, int units) {
        s.ledger().add(type, units);
        return s;
    }

    @Test
    void theMultiplierFollowsTheStoresInSteps() {
        Settlement empty = village(10);
        assertEquals(1.5, PriceModel.multiplier(empty, ResourceType.WOOD), 1e-9);

        assertEquals(1.25, PriceModel.multiplier(withStock(village(10), ResourceType.WOOD, 15), ResourceType.WOOD), 1e-9); // half of 30
        assertEquals(1.0, PriceModel.multiplier(withStock(village(10), ResourceType.WOOD, 30), ResourceType.WOOD), 1e-9);  // as wanted
        assertEquals(1.0, PriceModel.multiplier(withStock(village(10), ResourceType.WOOD, 60), ResourceType.WOOD), 1e-9);  // twice
        assertEquals(0.875, PriceModel.multiplier(withStock(village(10), ResourceType.WOOD, 90), ResourceType.WOOD), 1e-9); // three times
        assertEquals(0.75, PriceModel.multiplier(withStock(village(10), ResourceType.WOOD, 120), ResourceType.WOOD), 1e-9);
        assertEquals(0.75, PriceModel.multiplier(withStock(village(10), ResourceType.WOOD, 5000), ResourceType.WOOD), 1e-9);
    }

    @Test
    void foodIsWeighedAgainstWhatItTakesToFeedEveryone() {
        // 100 food is what ten people want; 100 wood would be more than three times what they want.
        Settlement s = withStock(withStock(village(10), ResourceType.FOOD, 100), ResourceType.WOOD, 100);
        assertEquals(1.0, PriceModel.multiplier(s, ResourceType.FOOD), 1e-9);
        assertTrue(PriceModel.multiplier(s, ResourceType.WOOD) < 0.9);
    }

    @Test
    void anEmptyVillageChangesNothing() {
        assertEquals(1.0, PriceModel.multiplier(registry.found("world", 5, 5, 0), ResourceType.FOOD), 1e-9);
    }

    @Test
    void aVillagerSellingAShortResourceCostsMoreAndAPlentifulOneCostsLess() {
        Settlement shortOfFood = village(10); // 0 of 100 wanted
        Settlement plentyOfFood = withStock(village(10), ResourceType.FOOD, 500);
        Settlement balanced = withStock(village(10), ResourceType.FOOD, 100);

        // Bread for 4 emeralds.
        assertEquals(2, PriceModel.specialPriceDelta(shortOfFood, "bread", "emerald", 4));   // 6 emeralds
        assertEquals(-1, PriceModel.specialPriceDelta(plentyOfFood, "bread", "emerald", 4)); // 3 emeralds
        assertEquals(0, PriceModel.specialPriceDelta(balanced, "bread", "emerald", 4));
        // Tools are a resource too.
        assertEquals(3, PriceModel.specialPriceDelta(village(10), "iron_pickaxe", "emerald", 6)); // 9
    }

    @Test
    void aVillagerBuyingAShortResourceAsksForLessOfIt() {
        // A farmer buys 20 wheat for an emerald.
        assertEquals(-6, PriceModel.specialPriceDelta(village(10), "emerald", "wheat", 20));                             // 14 wheat
        assertEquals(6, PriceModel.specialPriceDelta(withStock(village(10), ResourceType.FOOD, 500), "emerald", "wheat", 20)); // 26 wheat
        assertEquals(0, PriceModel.specialPriceDelta(withStock(village(10), ResourceType.FOOD, 100), "emerald", "wheat", 20));
    }

    @Test
    void tradesThatAreNotAboutAResourceAreLeftAlone() {
        Settlement s = village(10);
        assertEquals(0, PriceModel.specialPriceDelta(s, "diamond", "emerald", 12));  // the village has no use for diamonds
        assertEquals(0, PriceModel.specialPriceDelta(s, "bread", "wheat", 3));       // item for item, no emeralds
        assertEquals(0, PriceModel.specialPriceDelta(s, "bread", "emerald", 0));
        assertEquals(0, PriceModel.specialPriceDelta(s, "emerald", "diamond", 1));
    }

    @Test
    void aCheapItemNeverMovesMoreThanTheStatedRangeAndOneEmeraldNeverMoves() {
        // Real trades cost 1 to 3 emeralds, and the first rule is that a price stays within +50% / -25%.
        for (int base = 1; base <= 64; base++) {
            for (int stock = 0; stock <= 500; stock += 25) {
                Settlement s = withStock(village(10), ResourceType.FOOD, stock);
                double multiplier = PriceModel.multiplier(s, ResourceType.FOOD);
                int cost = base + PriceModel.specialPriceDelta(s, "bread", "emerald", base);
                assertTrue(cost >= 1, "cost fell below 1");
                assertTrue(cost <= base * 1.5 + 1e-9, "cost " + cost + " is over +50% of " + base);
                assertTrue(cost >= Math.min(base, base * 0.75) - 1e-9 || cost == 1, "cost " + cost + " is under -25% of " + base);
                if (base == 1) {
                    assertEquals(1, cost, "a 1 emerald item never changes price");
                }
                int amount = base + PriceModel.specialPriceDelta(s, "emerald", "wheat", base);
                assertTrue(amount >= 1, "amount fell below 1");
                assertTrue(amount >= base / 1.5 - 1e-9 && amount <= base / 0.75 + 1e-9,
                        "amount " + amount + " for " + base + " at multiplier " + multiplier);
            }
        }
    }

    @Test
    void realCheapTradesDoMoveWhereTheyCan() {
        // Cookie, cake, golden carrot and iron pickaxe cost 3: 4 when short (+33%), 3 when plentiful (-25% rounds to 3).
        assertEquals(1, PriceModel.specialPriceDelta(village(10), "cookie", "emerald", 3));
        assertEquals(0, PriceModel.specialPriceDelta(withStock(village(10), ResourceType.FOOD, 500), "cookie", "emerald", 3));
        // 2 emeralds: 3 when short, unchanged when plentiful.
        assertEquals(1, PriceModel.specialPriceDelta(village(10), "iron_sword", "emerald", 2));
        // 1 emerald (bread, apples): never moves.
        assertEquals(0, PriceModel.specialPriceDelta(village(10), "bread", "emerald", 1));
        assertEquals(0, PriceModel.specialPriceDelta(withStock(village(10), ResourceType.FOOD, 500), "bread", "emerald", 1));
    }

    @Test
    void goodsAreLeftAlone() {
        assertEquals(0, PriceModel.specialPriceDelta(village(10), "white_wool", "emerald", 6));
        assertEquals(0, PriceModel.specialPriceDelta(village(10), "emerald", "white_wool", 18));
        assertEquals(0, PriceModel.specialPriceDelta(village(10), "emerald", "paper", 24));
        assertTrue(PriceModel.specialPriceDelta(village(10), "oak_log", "emerald", 4) > 0, "wood is still priced");
    }

    @Test
    void theDearerTheScarcerNeverTheOtherWay() {
        int previousSelling = Integer.MAX_VALUE;
        int previousBuying = Integer.MIN_VALUE;
        for (int stock = 0; stock <= 500; stock += 10) {
            Settlement s = withStock(village(10), ResourceType.FOOD, stock);
            int selling = PriceModel.specialPriceDelta(s, "bread", "emerald", 12);
            int buying = PriceModel.specialPriceDelta(s, "emerald", "wheat", 20);
            assertTrue(selling <= previousSelling, "selling price rose at stock " + stock);
            assertTrue(buying >= previousBuying, "buying amount fell at stock " + stock);
            previousSelling = selling;
            previousBuying = buying;
        }
    }
}
