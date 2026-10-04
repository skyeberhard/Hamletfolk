package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R3.5: residents earn from their work and spend on food, and their wealth shows in dialogue. */
class WealthTest {
    private final SettlementSimulator simulator = SettlementSimulator.withOldAgeDeaths(false);

    private static Resident resident(int id, Occupation occupation, boolean adult) {
        return new Resident(new UUID(5, id), "T", "P", Gender.MALE, new Traits(60, 50, 50, 50), occupation, adult,
                10_000, null, null, Needs.initial());
    }

    private Settlement village(Resident... people) {
        Settlement s = new SettlementRegistry().found("world", 0, 0, 10_000);
        for (Resident r : people) {
            s.addResident(r);
        }
        s.ledger().add(ResourceType.FOOD, 400);
        return s;
    }

    private void days(Settlement s, int n) {
        simulator.simulateTo(s, s.lastSimulatedDay() + n, n);
    }

    @Test
    void aWorkerEarnsTheWorthOfWhatTheyMakeAndAnIdlerEarnsNothing() {
        Resident farmer = resident(1, Occupation.FARMER, true);
        Resident idle = resident(2, Occupation.NITWIT, true); // nitwits never work (and are not given a job)
        Settlement s = village(farmer, idle);
        days(s, 20);
        assertTrue(farmer.wealth() > 0, "a farmer who sells 4 food a day for 20 days has something put by");
        assertEquals(0, idle.wealth());
    }

    @Test
    void whatTheyEatIsPaidForOutOfTheirWealth() {
        Resident rich = resident(1, Occupation.NITWIT, true);
        rich.addWealth(1000);
        Resident poor = resident(2, Occupation.NITWIT, true);
        Settlement s = village(rich, poor);
        days(s, 1);
        assertEquals(1000 - Wealth.mealCost(1.0), rich.wealth());
        assertEquals(0, poor.wealth(), "nothing to pay with: they are still fed, and stay broke");
    }

    @Test
    void childrenNeitherEarnNorPay() {
        Resident child = resident(3, Occupation.NITWIT, false);
        child.addWealth(300);
        Settlement s = village(child);
        days(s, 3);
        assertEquals(300, child.wealth());
    }

    @Test
    void wealthIsClampedAndCannotGoBelowNothing() {
        Resident r = resident(1, Occupation.UNEMPLOYED, true);
        r.addWealth(Integer.MAX_VALUE);
        assertEquals(Wealth.MAX, r.wealth());
        assertEquals(Wealth.MAX, r.spendWealth(Integer.MAX_VALUE));
        assertEquals(0, r.wealth());
        r.addWealth(-50);
        assertEquals(0, r.wealth());
    }

    @Test
    void wealthDoesNotTouchTheTreasuryOrTheStores() {
        // The same village twice, from one save so both run on the same seed: one resident is rich, one is not.
        Settlement original = village(resident(1, Occupation.FARMER, true), resident(2, Occupation.MERCHANT, true));
        original.ledger().add(ResourceType.WOOD, 300);
        Map<String, Object> saved = SettlementCodec.encode(original);
        Settlement plain = SettlementCodec.decode(saved);
        Settlement rich = SettlementCodec.decode(saved);
        rich.residents().forEach(r -> r.addWealth(5000));
        days(plain, 8);
        days(rich, 8);
        for (ResourceType type : ResourceType.values()) {
            assertEquals(plain.ledger().get(type), rich.ledger().get(type), type + " stores");
        }
        assertEquals(plain.ledger().treasury(), rich.ledger().treasury());
    }

    @Test
    void aMerchantKeepsACommissionOnTheirSales() {
        Resident merchant = resident(1, Occupation.MERCHANT, true);
        Settlement s = village(merchant);
        s.registerBuilding(new Building(BuildingType.SHOP, 0, 64, 0, 0, "test"));
        s.ledger().add(ResourceType.WOOD, 500); // plenty to sell
        s.ledger().add(ResourceType.STONE, 500);
        days(s, 5);
        assertTrue(s.ledger().treasury() > 0);
        assertTrue(merchant.wealth() > 0);
    }

    @Test
    void tiersFollowWhatTheyHavePutBy() {
        assertEquals(Wealth.Tier.BROKE, Wealth.tier(0));
        assertEquals(Wealth.Tier.BROKE, Wealth.tier(Wealth.mealCost(1.0) - 1));
        assertEquals(Wealth.Tier.MODEST, Wealth.tier(Wealth.mealCost(1.0)));
        assertEquals(Wealth.Tier.COMFORTABLE, Wealth.tier(5 * Wealth.PER_EMERALD));
        assertEquals(Wealth.Tier.WEALTHY, Wealth.tier(20 * Wealth.PER_EMERALD));
    }

    @Test
    void wealthShowsInDialogue() {
        Resident broke = resident(1, Occupation.FARMER, true);
        Resident rich = resident(2, Occupation.FARMER, true);
        rich.addWealth(30 * Wealth.PER_EMERALD);
        assertTrue(Dialogue.wealthLine(broke, Occupation.FARMER).orElseThrow().contains("nothing put by"));
        assertTrue(Dialogue.wealthLine(rich, Occupation.FARMER).orElseThrow().contains("Business has been good"));
        // And it can be picked in ordinary talk: across many draws the broke farmer says it at least once.
        Settlement s = village(broke);
        boolean said = false;
        for (int seed = 0; seed < 200 && !said; seed++) {
            said = Dialogue.smallTalk(broke, s, 10_005, new Random(seed)).contains("nothing put by");
        }
        assertTrue(said);
        Resident child = resident(3, Occupation.UNEMPLOYED, false);
        for (int seed = 0; seed < 100; seed++) {
            assertFalse(Dialogue.smallTalk(child, s, 10_005, new Random(seed)).contains("put by"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void wealthSurvivesASaveAndAnOldSaveHasNone() {
        Resident farmer = resident(1, Occupation.FARMER, true);
        farmer.addWealth(1234);
        Settlement s = village(farmer);
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(1234, loaded.resident(farmer.id()).orElseThrow().wealth());

        // A format-11 save, written before wealth existed: the resident has no "wealth" key at all.
        Map<String, Object> old = new LinkedHashMap<>(SettlementCodec.encode(s));
        old.put("format", 11);
        Map<String, Object> residentMap = new LinkedHashMap<>((Map<String, Object>) ((java.util.List<?>) old.get("residents")).get(0));
        assertTrue(residentMap.remove("wealth") != null, "the current format wrote the wealth");
        old.put("residents", java.util.List.of(residentMap));
        Settlement migrated = SettlementCodec.decode(old);
        assertEquals(0, migrated.residents().iterator().next().wealth());
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(migrated).get("format"));

        // A damaged file cannot give a negative or enormous fortune.
        residentMap.put("wealth", -5);
        old.put("residents", java.util.List.of(residentMap));
        assertEquals(0, SettlementCodec.decode(old).residents().iterator().next().wealth());
        residentMap.put("wealth", Integer.MAX_VALUE);
        old.put("residents", java.util.List.of(residentMap));
        assertEquals(Wealth.MAX, SettlementCodec.decode(old).residents().iterator().next().wealth());
    }
}
