package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.64: a master craftsman sells the village's finished goods out of its own surplus, for the treasury. */
class StallTest {
    private static int n;

    private Resident person(Occupation job, int days) {
        Resident r = new Resident(new UUID(50, n++), "Test", "Keeper" + n, Gender.MALE, new Traits(50, 50, 50, 50), job, true, 10_000, null, null, Needs.initial());
        if (days > 0) {
            r.restoreXp(job, days);
        }
        return r;
    }

    private Settlement village(Resident keeper, int tools) {
        Settlement s = new Settlement(new UUID(51, n++), "Stallton", "world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(person(Occupation.UNEMPLOYED, 0));
        }
        s.addResident(keeper);
        s.housing().setChunk(0, 0, 10);
        s.conditions().put(SettlementSimulator.TREASURY_LEGACY, 100_000L);
        s.ledger().add(Commodity.IRON_TOOLS, tools);
        return s;
    }

    @Test
    void onlyAnExpertOrBetterWhoMakesToolsOrGoodsKeepsAStall() {
        assertTrue(Stall.keeps(person(Occupation.TOOLSMITH, 60)));
        assertTrue(Stall.keeps(person(Occupation.LIBRARIAN, 100)));
        assertFalse(Stall.keeps(person(Occupation.TOOLSMITH, 30)), "a journeyman");
        assertFalse(Stall.keeps(person(Occupation.FARMER, 100)), "food is not sold at a stall");
        assertFalse(Stall.keeps(person(Occupation.MERCHANT, 100)));
    }

    @Test
    void theOfferIsTheBestFinishedToolDearerForABetterHandAndByStanding() {
        Resident expert = person(Occupation.TOOLSMITH, 60);
        Settlement s = village(expert, 500);
        Stall.Offer offer = Stall.offer(s, expert, 0, 10, 1000).orElseThrow();
        assertEquals("iron_pickaxe", offer.material());
        assertEquals(1, offer.count());
        assertEquals("Fine Iron Pickaxe", offer.name());
        assertTrue(offer.lore().contains(expert.fullName()));
        assertTrue(offer.maxUses() >= 1 && offer.maxUses() <= Stall.MAX_USES);
        Resident master = person(Occupation.TOOLSMITH, 100);
        Settlement t = village(master, 500);
        assertTrue(Stall.offer(t, master, 0, 10, 1000).orElseThrow().emeralds() > offer.emeralds(), "a master charges more");
        assertTrue(Stall.offer(s, expert, 100, 10, 1000).orElseThrow().emeralds() <= offer.emeralds(), "a friend pays no more");
        int base = Stall.offer(t, master, 0, 10, 1000).orElseThrow().emeralds();
        assertTrue(Stall.offer(t, master, 100, 10, 1000).orElseThrow().emeralds() < base, "a friend pays less");
        assertTrue(Stall.offer(t, master, -100, 10, 1000).orElseThrow().emeralds() > base, "an enemy pays more");
    }

    @Test
    void nothingIsOfferedThatTheVillageCannotSpare() {
        Resident expert = person(Occupation.TOOLSMITH, 60);
        assertTrue(Stall.offer(village(expert, 0), expert, 0, 10, 1000).isEmpty());
        Resident e2 = person(Occupation.TOOLSMITH, 60);
        assertTrue(Stall.offer(village(e2, 1), e2, 0, 10, 1000).isEmpty(), "within what it keeps for itself");
    }

    @Test
    void aSaleTakesTheUnitsAndBanksTheEmeraldsWithinTheRoom() {
        Resident expert = person(Occupation.TOOLSMITH, 60);
        Settlement s = village(expert, 500);
        int before = s.ledger().get(Commodity.IRON_TOOLS);
        int banked = Stall.sold(s, expert, "iron_pickaxe", 1, 3, 100, 10);
        assertEquals(3, banked);
        assertEquals(before - StoreDisplay.unitsFor("iron_pickaxe", 1), s.ledger().get(Commodity.IRON_TOOLS));
        assertEquals(3, s.ledger().treasury());
        assertEquals(2, Stall.sold(s, expert, "iron_pickaxe", 1, 3, 2, 10), "only what the treasury has room for");
        Resident e2 = person(Occupation.TOOLSMITH, 60);
        Settlement poor = village(e2, 1);
        assertEquals(-1, Stall.sold(poor, e2, "iron_pickaxe", 1, 3, 100, 10), "no stock to spare: refused");
        assertEquals(1, poor.ledger().get(Commodity.IRON_TOOLS));
        assertEquals(-1, Stall.sold(s, expert, "bread", 1, 3, 100, 10), "not this keeper's category");
    }

    @Test
    void theDaysSalesAreCappedAndATreasuryWithNoRoomMeansNoStall() {
        Resident expert = person(Occupation.TOOLSMITH, 60);
        Settlement s = village(expert, 2000);
        assertTrue(Stall.offer(s, expert, 0, 10, 0).isEmpty(), "no room for the payment");
        Stall.Offer offer = Stall.offer(s, expert, 0, 10, 1000).orElseThrow();
        assertEquals(1, Stall.offer(s, expert, 0, 10, offer.emeralds()).orElseThrow().maxUses(), "the room caps the sales");
        for (int i = 0; i < Stall.MAX_USES; i++) {
            assertTrue(Stall.sold(s, expert, "iron_pickaxe", 1, offer.emeralds(), 1000, 10) >= 0);
        }
        assertTrue(Stall.offer(s, expert, 0, 10, 1000).isEmpty(), "the day is sold out however often the window is opened");
        assertTrue(Stall.offer(s, expert, 0, 11, 1000).isPresent(), "and the next day it sells again");
    }

    @Test
    void aGoodsMakerSellsTheirKind() {
        Resident librarian = person(Occupation.LIBRARIAN, 100);
        Settlement s = village(librarian, 0);
        s.ledger().add(Commodity.BOOKS, 400);
        Stall.Offer offer = Stall.offer(s, librarian, 0, 10, 1000).orElseThrow();
        assertEquals("book", offer.material());
        assertEquals(ResourceType.GOODS, offer.category());
        assertEquals("Masterwork Book", offer.name());
    }
}
