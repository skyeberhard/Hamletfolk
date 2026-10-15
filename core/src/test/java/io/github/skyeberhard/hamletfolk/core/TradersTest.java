package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R3.18: what the stores cannot hold is sold to a passing trader once a week, not just thrown away. */
class TradersTest {
    private final SettlementSimulator simulator = new SettlementSimulator(false, o -> true);
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(95, next++), "T", "P" + next, Gender.FEMALE, new Traits(50, 50, 50, 80), job, true,
                -30, null, null, Needs.initial());
    }

    /** Four idlers with plenty to eat and the wood store 120 over its limit. */
    private Settlement overflowing() {
        Settlement s = new Settlement(new UUID(96, 96), "Fullham", "world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(person(Occupation.NITWIT));
        }
        for (ResourceType type : ResourceType.values()) {
            s.ledger().add(type, Math.min(SettlementSimulator.capacity(s, type), 60)); // enough of everything: no requests, nothing over
        }
        s.ledger().take(ResourceType.WOOD, s.ledger().get(ResourceType.WOOD));
        s.ledger().add(ResourceType.WOOD, SettlementSimulator.capacity(s, ResourceType.WOOD) + 120);
        return s;
    }

    private void run(Settlement s, long from, long to) {
        for (long day = from; day <= to; day++) {
            simulator.simulateDay(s, day);
        }
    }

    @Test
    void aTraderBuysTheOverflowOnceAWeekAtHalfPrice() {
        Settlement s = overflowing();
        simulator.simulateDay(s, 1);
        assertEquals(0, s.ledger().treasury(), "the week starts");
        run(s, 2, 7);
        assertEquals(0, s.ledger().treasury(), "and has not ended");
        simulator.simulateDay(s, 8);
        assertEquals(120 / (2 * SettlementSimulator.unitsPerEmerald(ResourceType.WOOD)), s.ledger().treasury(),
                "120 wood at half the merchant's price");
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("passing trader") && e.text().contains("wood")));
        assertTrue(s.conditions().keySet().stream().noneMatch(k -> k.equals("forSale:WOOD")), "what was kept aside is spent");
    }

    @Test
    void aShopAndAMerchantGetABetterPrice() {
        Settlement plain = overflowing();
        Settlement market = overflowing();
        market.addResident(person(Occupation.MERCHANT));
        market.registerBuilding(new Building(BuildingType.SHOP, 3, 64, 3, 0, "a player"));
        simulator.simulateDay(plain, 1);
        simulator.simulateDay(market, 1);
        run(plain, 2, 8);
        run(market, 2, 8);
        // (the merchant also sells some surplus itself, so this can only be said to be more)
        assertTrue(market.ledger().treasury() > plain.ledger().treasury(), market.ledger().treasury() + " against " + plain.ledger().treasury());
    }

    @Test
    void nothingIsPaidBeyondTheTreasuryLimit() {
        Settlement s = overflowing();
        s.ledger().addTreasury(simulator.treasuryRoom(s));
        int full = s.ledger().treasury();
        simulator.simulateDay(s, 1);
        run(s, 2, 8);
        assertEquals(full, s.ledger().treasury(), "a full treasury takes no more");
        assertTrue(s.conditions().keySet().stream().noneMatch(k -> k.startsWith("forSale:")), "and the overflow is lost as before");
    }

    @Test
    void aVillageWithRoomInItsStoresSellsNothing() {
        Settlement s = overflowing();
        s.ledger().take(ResourceType.WOOD, s.ledger().get(ResourceType.WOOD));
        simulator.simulateDay(s, 1);
        run(s, 2, 8);
        assertEquals(0, s.ledger().treasury());
    }
}
