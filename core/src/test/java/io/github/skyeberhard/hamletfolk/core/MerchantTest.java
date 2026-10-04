package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R3.9: a merchant sells surplus for emeralds into the treasury. */
class MerchantTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();

    private static Resident person(Occupation job) {
        // Born far in the future of these short runs, so nobody ages into an elder.
        return new Resident(UUID.randomUUID(), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, 50),
                job, true, 10_000, null, null, Needs.initial());
    }

    /** R2.5: a merchant needs a registered storefront each. */
    private static void shop(Settlement s, int count) {
        for (int i = 0; i < count; i++) {
            s.registerBuilding(new Building(BuildingType.SHOP, i, 64, 0, 0, "test"));
        }
    }

    /** Emeralds the merchant has earned: the treasury plus what the village has put aside for requests (R3.3). */
    private static int earned(Settlement s) {
        return s.ledger().treasury() + s.requests().stream().mapToInt(Request::reward).sum();
    }

    /** Four residents (a storage limit of 140 for stone, 24 of it kept) with one merchant. */
    private Settlement village() {
        Settlement s = registry.found("world", 0, 0, 0);
        shop(s, 1);
        s.addResident(person(Occupation.MERCHANT));
        for (int i = 0; i < 3; i++) {
            s.addResident(person(Occupation.NITWIT)); // makes nothing, so only the test adds to the stores
        }
        s.ledger().add(ResourceType.FOOD, 100); // they eat for the run; with none the merchant would be released to forage
        return s;
    }

    @Test
    void aMerchantSellsWholeBatchesOfSurplusForEmeralds() {
        Settlement s = village();
        s.ledger().add(ResourceType.STONE, 120);
        int kept = 4 * SettlementSimulator.STOCK_KEPT_PER_HEAD;
        simulator.simulateTo(s, 1, 100);

        int sold = 120 - s.ledger().get(ResourceType.STONE);
        assertTrue(sold > 0, "something should have sold");
        assertEquals(0, sold % SettlementSimulator.unitsPerEmerald(ResourceType.STONE), "whole batches only");
        assertEquals(sold / SettlementSimulator.unitsPerEmerald(ResourceType.STONE), earned(s));
        assertEquals(sold, s.flow().consumed(ResourceType.STONE, 1));
        assertTrue(s.ledger().get(ResourceType.STONE) >= kept);
    }

    @Test
    void aMerchantNeverSellsBelowWhatTheVillageKeeps() {
        Settlement s = village();
        s.ledger().add(ResourceType.STONE, 120);
        simulator.simulateTo(s, 60, 100);
        int kept = 4 * SettlementSimulator.STOCK_KEPT_PER_HEAD;
        int each = SettlementSimulator.unitsPerEmerald(ResourceType.STONE);
        assertTrue(s.ledger().get(ResourceType.STONE) >= kept, "sold into the reserve");
        assertTrue(s.ledger().get(ResourceType.STONE) < kept + each, "left a whole batch of surplus unsold");
        // Earnings may have gone to requests for what the village lacks (R3.3), so count both.
        assertEquals((120 - s.ledger().get(ResourceType.STONE)) / each, earned(s));
    }

    @Test
    void aMerchantNeverStopsNewcomersFromArriving() {
        // Six farmers make 24 food a day, ten residents eat 20: just self-sufficient. The merchant is
        // enrolled last, so it sells after the farmers, which is the order that used to drain the
        // stores to just under the 20 a head newcomers need.
        Settlement s = registry.found("world", 0, 0, 0);
        shop(s, 1);
        for (int i = 0; i < 6; i++) {
            s.addResident(person(Occupation.FARMER));
        }
        for (int i = 0; i < 3; i++) {
            s.addResident(person(Occupation.NITWIT));
        }
        s.addResident(person(Occupation.MERCHANT));
        s.ledger().add(ResourceType.FOOD, 300);
        for (int day = 1; day <= 40; day++) {
            simulator.simulateTo(s, day, 100);
            assertTrue(simulator.newcomerDue(s, 5), "no newcomer due on day " + day + ", food " + s.ledger().get(ResourceType.FOOD));
        }
        assertTrue(earned(s) > 0, "the merchant should still have sold the surplus");
    }

    @Test
    void toolsAndMetalAreKeptInLargerReserves() {
        Settlement s = village();
        s.ledger().add(ResourceType.TOOLS, 60); // 15 a head: well above need, but below what a village keeps
        s.ledger().add(ResourceType.METAL, 60);
        // Enough wood that nothing is short, but not a surplus to sell: otherwise the idle merchant is released
        // to cut wood, and a lumberjack's tools wear out by a dice roll.
        s.ledger().add(ResourceType.WOOD, 20);
        simulator.simulateTo(s, 5, 100);
        assertEquals(60, s.ledger().get(ResourceType.TOOLS));
        assertEquals(60, s.ledger().get(ResourceType.METAL));
        assertEquals(0, s.ledger().treasury());
    }

    @Test
    void aMerchantWithNothingToSellIsReleasedWhenSomethingIsShort() {
        Settlement s = registry.found("world", 0, 0, 0);
        shop(s, 1);
        Resident merchant = person(Occupation.MERCHANT);
        s.addResident(merchant);
        s.ledger().add(ResourceType.FOOD, 5); // short, and nothing at all to sell
        simulator.simulateTo(s, 1, 100);
        assertTrue(merchant.occupation() != Occupation.MERCHANT, "should have been released: " + merchant.occupation());
    }

    @Test
    void aMerchantsChildFollowsTheTradeOnlyWhenNothingIsShort() {
        Settlement s = registry.found("world", 0, 0, 0);
        shop(s, 1);
        SettlementSimulator anyJob = new SettlementSimulator(false, o -> true);
        Resident parent = person(Occupation.MERCHANT);
        s.addResident(parent);
        for (int i = 0; i < 15; i++) {
            s.addResident(person(Occupation.NITWIT)); // 16 residents: room for a second merchant
        }
        Resident child = new Resident(UUID.randomUUID(), "Kid", "Person", Gender.FEMALE, new Traits(50, 50, 50, 50),
                Occupation.UNEMPLOYED, true, 10_000, parent.id(), null, Needs.initial());
        s.addResident(child);
        s.ledger().add(ResourceType.FOOD, 400);
        s.ledger().add(ResourceType.STONE, 120);
        // wood is short (0), so the shortest job wins over following the parent
        anyJob.simulateTo(s, 1, 100);
        assertEquals(Occupation.LUMBERJACK, child.occupation());
    }

    @Test
    void anElderMerchantSellsLessThanAnAdult() {
        Settlement s = registry.found("world", 50, 50, 0);
        shop(s, 1);
        Resident elder = new Resident(UUID.randomUUID(), "Old", "Person", Gender.MALE, new Traits(50, 50, 50, 50),
                Occupation.MERCHANT, true, -70, null, null, Needs.initial()); // 71 days: an elder, well short of any maximum age
        s.addResident(elder);
        s.ledger().add(ResourceType.STONE, 140);
        simulator.simulateTo(s, 1, 100);
        // one resident keeps 6; an elder (0.6 pace) manages 2 of the 4 daily batches
        assertEquals(2, s.ledger().treasury());
    }

    @Test
    void nothingSellsWithoutASurplusAndAMerchantEarnsNothingThen() {
        Settlement s = village();
        s.ledger().add(ResourceType.STONE, 20); // under the 24 kept
        simulator.simulateTo(s, 5, 100);
        assertEquals(0, s.ledger().treasury());
    }

    @Test
    void theMostPlentifulSurplusGoesFirst() {
        Settlement s = village();
        s.ledger().add(ResourceType.STONE, 40);
        s.ledger().add(ResourceType.TOOLS, 100); // each tool is a whole emerald, so this is worth far more
        simulator.simulateTo(s, 1, 100);
        assertTrue(s.ledger().get(ResourceType.TOOLS) < 100, "tools should have sold first");
        assertEquals(40, s.ledger().get(ResourceType.STONE));
    }

    @Test
    void anUnemployedResidentBecomesTheMerchantOnlyWhenThereIsSomethingToSell() {
        Settlement bare = registry.found("world", 0, 0, 0);
        shop(bare, 1);
        Resident jobless = person(Occupation.UNEMPLOYED);
        bare.addResident(jobless);
        bare.ledger().add(ResourceType.FOOD, 20); // exactly what one resident keeps
        bare.ledger().add(ResourceType.WOOD, 6);
        bare.ledger().add(ResourceType.STONE, 6);
        simulator.simulateTo(bare, 3, 100);
        assertEquals(Occupation.UNEMPLOYED, jobless.occupation()); // nothing to spare, and nothing short

        Settlement rich = registry.found("world", 100, 100, 0);

        shop(rich, 1);
        Resident other = person(Occupation.UNEMPLOYED);
        rich.addResident(other);
        rich.ledger().add(ResourceType.FOOD, 100);
        rich.ledger().add(ResourceType.WOOD, 100);
        rich.ledger().add(ResourceType.STONE, 100);
        simulator.simulateTo(rich, 3, 100);
        assertEquals(Occupation.MERCHANT, other.occupation());
    }

    @Test
    void oneMerchantPerFifteenResidentsNotAMerchantEach() {
        Settlement s = registry.found("world", 0, 0, 0);
        shop(s, 5);
        for (int i = 0; i < 6; i++) {
            s.addResident(person(Occupation.UNEMPLOYED));
        }
        s.ledger().add(ResourceType.FOOD, 140);
        s.ledger().add(ResourceType.WOOD, 100);
        s.ledger().add(ResourceType.STONE, 100);
        s.ledger().add(ResourceType.GOODS, 100);
        simulator.simulateTo(s, 10, 100);
        long merchants = s.residents().stream().filter(r -> r.occupation() == Occupation.MERCHANT).count();
        assertEquals(1, merchants);
    }

    @Test
    void merchantsRoundTripThroughASave() {
        assertTrue(Occupation.MERCHANT.simOwned());
        assertEquals(null, Occupation.MERCHANT.produces());
        Settlement s = village();
        s.ledger().addTreasury(7);
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(1, loaded.residents().stream().filter(r -> r.occupation() == Occupation.MERCHANT).count());
        assertEquals(7, loaded.ledger().treasury());
    }
}
