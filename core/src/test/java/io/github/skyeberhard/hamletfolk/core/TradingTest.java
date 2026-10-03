package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R3.2: trades with villagers move goods into and out of the settlement's stores. */
class TradingTest {
    private Settlement village() {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 0);
        for (int i = 0; i < 10; i++) {
            s.addResident(new Resident(new UUID(0, i + 1), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                    Occupation.NITWIT, true, 10_000, null, null, Needs.initial()));
        }
        return s;
    }

    @Test
    void sellingFoodToAFarmerAddsItToTheStores() {
        Settlement s = village();
        Trading.Effect effect = Trading.apply(s, "wheat", 20, "emerald", 1).orElseThrow();
        assertEquals(ResourceType.FOOD, effect.type());
        assertTrue(effect.gained());
        assertEquals(20, s.ledger().get(ResourceType.FOOD));
        assertEquals(0, s.ledger().treasury(), "emeralds come from the game, not the treasury");
    }

    @Test
    void buyingFromAVillagerTakesOnlyWhatTheVillageCanSpare() {
        Settlement s = village(); // ten residents want 100 food
        s.ledger().add(ResourceType.FOOD, 103);
        Trading.Effect effect = Trading.apply(s, "emerald", 1, "bread", 6).orElseThrow();
        assertFalse(effect.gained());
        assertEquals(3, effect.units());
        assertEquals(100, s.ledger().get(ResourceType.FOOD));
        assertTrue(Trading.apply(s, "emerald", 1, "bread", 6).isEmpty(), "nothing spare: the village keeps what it wants");
    }

    @Test
    void aVillagerSellsOnlyWhatTheVillageCanSpare() {
        Settlement s = village(); // wants 100 food
        s.ledger().add(ResourceType.FOOD, 112);
        // Six bread per trade is six units: 12 spare is two trades.
        assertEquals(2, Trading.tradesAllowed(s, "emerald", "bread", 6).orElseThrow());
        s.ledger().take(ResourceType.FOOD, 20); // now short
        assertEquals(0, Trading.tradesAllowed(s, "emerald", "bread", 6).orElseThrow());
    }

    @Test
    void tradesTheStoresDoNotGovernAreUncapped() {
        Settlement s = village();
        assertTrue(Trading.tradesAllowed(s, "emerald", "enchanted_book", 1).isEmpty());
        assertTrue(Trading.tradesAllowed(s, "emerald", "iron_pickaxe", 1).isEmpty(), "tools stay vanilla");
        assertTrue(Trading.tradesAllowed(s, "wheat", "emerald", 1).isEmpty(), "the village buying is not capped");
        assertTrue(Trading.tradesAllowed(s, "emerald", "stick", 1).isEmpty(), "worth less than a unit");
        assertTrue(Trading.tradesAllowed(s, "emerald", "glass", 4).isEmpty(), "goods have no wanted level, like their prices");
        assertTrue(Trading.tradesAllowed(s, "emerald", "white_wool", 1).isEmpty());
    }

    @Test
    void foundersBringStartingStoresSoTheFirstTradersHaveStock() {
        Settlement s = village();
        for (int i = 0; i < 3; i++) {
            Trading.seedFounder(s);
        }
        assertEquals(60, s.ledger().get(ResourceType.FOOD));
        for (ResourceType type : ResourceType.values()) {
            if (type != ResourceType.FOOD) {
                assertEquals(0, s.ledger().get(type), type + " is not seeded");
            }
        }
        // A village of three wants 30 food, so the seed leaves something spare.
        Settlement three = new SettlementRegistry().found("world", 0, 0, 0);
        for (int i = 0; i < 3; i++) {
            three.addResident(new Resident(new UUID(2, i), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                    Occupation.NITWIT, true, 10_000, null, null, Needs.initial()));
            Trading.seedFounder(three);
        }
        assertTrue(Trading.tradesAllowed(three, "emerald", "bread", 6).orElseThrow() > 0);
    }

    @Test
    void buyingCannotMakeAShortage() {
        Settlement s = village();
        s.ledger().add(ResourceType.FOOD, 40); // already short
        assertTrue(Trading.apply(s, "emerald", 1, "bread", 6).isEmpty());
        assertEquals(40, s.ledger().get(ResourceType.FOOD));
    }

    @Test
    void whatCannotBeStoredIsLost() {
        Settlement s = village();
        int room = SettlementSimulator.room(s, ResourceType.WOOD);
        s.ledger().add(ResourceType.WOOD, room - 2);
        Trading.Effect effect = Trading.apply(s, "oak_planks", 10, "emerald", 1).orElseThrow();
        assertEquals(2, effect.units());
        assertEquals(0, SettlementSimulator.room(s, ResourceType.WOOD));
        assertTrue(Trading.apply(s, "oak_planks", 10, "emerald", 1).isEmpty());
    }

    @Test
    void tradesThatAreNotGoodsForEmeraldsChangeNothing() {
        Settlement s = village();
        assertTrue(Trading.apply(s, "wheat", 5, "bread", 1).isEmpty());          // goods for goods
        assertTrue(Trading.apply(s, "emerald", 3, "emerald_block", 1).isEmpty()); // currency both ways
        assertTrue(Trading.apply(s, "rotten_flesh", 24, "emerald", 1).isEmpty());        // unusable item
        assertTrue(Trading.apply(s, "emerald", 5, "enchanted_book", 1).isEmpty());
        assertTrue(Trading.apply(s, "iron_pickaxe", 1, "emerald", 1).isEmpty(), "tools are left alone");
        for (ResourceType type : ResourceType.values()) {
            assertEquals(0, s.ledger().get(type));
        }
    }

    @Test
    void sellingToAShortVillagerEasesItsPriceBack() {
        Settlement s = village();
        int before = PriceModel.specialPriceDelta(s, "emerald", "wheat", 20);
        Trading.apply(s, "wheat", 20, "emerald", 1);
        Trading.apply(s, "wheat", 20, "emerald", 1);
        Trading.apply(s, "wheat", 20, "emerald", 1);
        int after = PriceModel.specialPriceDelta(s, "emerald", "wheat", 20);
        assertTrue(after > before, "the village asks for more wheat per emerald as its larder fills: " + before + " -> " + after);
    }
}
