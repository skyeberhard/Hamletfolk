package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.63: a friend of the village is now and then given something its trades make, out of its own surplus. */
class GiftsTest {
    private static int n;
    private final UUID player = new UUID(5, 5);

    private Resident person(Occupation job, int days) {
        Resident r = new Resident(new UUID(60, n++), "Test", "Giver" + n, Gender.MALE, new Traits(50, 50, 50, 50), job, true, 10_000, null, null, Needs.initial());
        if (days > 0) {
            r.restoreXp(job, days);
        }
        return r;
    }

    private Settlement village(Resident giver, int standing) {
        Settlement s = new Settlement(new UUID(61, n++), "Giftford", "world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(person(Occupation.UNEMPLOYED, 0));
        }
        s.addResident(giver);
        s.housing().setChunk(0, 0, 10);
        s.conditions().put(SettlementSimulator.TREASURY_LEGACY, 100_000L);
        s.ledger().add(Commodity.BREAD, 500);
        s.ledger().add(Commodity.IRON_TOOLS, 500);
        s.adjustReputation(player, standing);
        return s;
    }

    /** The first offer in a long run of days, so tests do not depend on the roll. */
    private Optional<Gifts.Gift> firstOffer(Settlement s, Resident giver, int from) {
        for (int day = from; day < from + 60; day++) {
            Optional<Gifts.Gift> gift = Gifts.offer(s, giver, player, day);
            if (gift.isPresent()) {
                return gift;
            }
        }
        return Optional.empty();
    }

    @Test
    void aFriendIsGivenBreadOutOfTheStoresAndTheLedgerPays() {
        Resident farmer = person(Occupation.FARMER, 0);
        Settlement s = village(farmer, 30);
        int before = s.ledger().get(Commodity.BREAD);
        Gifts.Gift gift = firstOffer(s, farmer, 100).orElseThrow();
        assertEquals("bread", gift.material());
        assertTrue(gift.count() >= 1);
        assertNull(gift.name(), "a novice's gift is plain");
        assertTrue(s.ledger().get(Commodity.BREAD) < before, "the stores paid for it");
    }

    @Test
    void aMastersGiftIsNamedForItsQualityAndMaker() {
        Resident smith = person(Occupation.TOOLSMITH, 100);
        Settlement s = village(smith, 30);
        Gifts.Gift gift = firstOffer(s, smith, 100).orElseThrow();
        assertEquals("iron_pickaxe", gift.material());
        assertEquals("Masterwork Iron Pickaxe", gift.name());
        assertNotNull(gift.lore());
        assertTrue(gift.lore().contains(smith.fullName()));
    }

    @Test
    void strangersThievesAndTheOutOfStockAreGivenNothing() {
        Resident farmer = person(Occupation.FARMER, 0);
        assertTrue(firstOffer(village(farmer, 5), farmer, 100).isEmpty(), "a stranger");
        Resident f2 = person(Occupation.FARMER, 0);
        Settlement thief = village(f2, 30);
        for (int day = 100; day < 160; day++) {
            StoreDisplay.noteTheft(thief, player, day);
            assertTrue(Gifts.offer(thief, f2, player, day).isEmpty(), "a recent thief");
        }
        Resident f3 = person(Occupation.FARMER, 0);
        Settlement empty = village(f3, 30);
        empty.ledger().take(ResourceType.FOOD, 10_000);
        assertTrue(firstOffer(empty, f3, 100).isEmpty(), "nothing to spare");
        Resident idle = person(Occupation.NITWIT, 0);
        assertTrue(firstOffer(village(idle, 30), idle, 100).isEmpty(), "no trade");
    }

    @Test
    void aGiftWaitsOutItsCooldownAndAVillageGivesAFewADay() {
        Resident farmer = person(Occupation.FARMER, 0);
        Settlement s = village(farmer, 30);
        long day = 0;
        Gifts.Gift gift = null;
        for (long d = 100; d < 400 && gift == null; d++) {
            gift = Gifts.offer(s, farmer, player, d).orElse(null);
            day = d;
        }
        assertNotNull(gift);
        for (long d = day + 1; d < day + Gifts.COOLDOWN_DAYS; d++) {
            assertFalse(Gifts.offer(s, farmer, player, d).isPresent(), "inside the cooldown, day " + d);
        }
        Resident giver = person(Occupation.FARMER, 0);
        Settlement busy = village(giver, 30);
        int given = 0;
        for (int i = 0; i < 40; i++) {
            UUID other = new UUID(7, i);
            busy.adjustReputation(other, 30);
            given += Gifts.offer(busy, giver, other, 500).isPresent() ? 1 : 0;
        }
        assertEquals(Gifts.DAILY_CAP, given, "the village stops at its daily cap");
    }
}
