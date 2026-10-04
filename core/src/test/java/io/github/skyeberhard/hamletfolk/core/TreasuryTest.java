package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R2.6: without a treasury building a settlement banks only a base amount; each building raises the limit. */
class TreasuryTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(8, next++), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, 50),
                job, true, 10_000, null, null, Needs.initial());
    }

    /** Four residents who make nothing, with plenty of everything, so only the test moves the treasury. */
    private Settlement village() {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(person(Occupation.NITWIT));
        }
        for (ResourceType type : ResourceType.values()) {
            s.ledger().add(type, 60); // nothing short, so the village posts no request and spends nothing from the treasury
        }
        s.ledger().add(ResourceType.FOOD, 100);
        return s;
    }

    private static void treasuryBuilding(Settlement s, int at) {
        s.registerBuilding(new Building(BuildingType.TREASURY, at, 64, 0, 0, "test"));
    }

    @Test
    void aTreasurySignIsATreasuryBuilding() {
        assertEquals(Optional.of(BuildingType.TREASURY), BuildingType.fromSign("[Treasury]"));
        assertEquals(Optional.of(BuildingType.TREASURY), BuildingType.fromSign("[ treasury ]"));
        assertTrue(BuildingType.TREASURY.job().isEmpty());
    }

    @Test
    void theLimitIsTheBasePlusAFixedAmountForEachBuilding() {
        Settlement s = village();
        assertEquals(SettlementSimulator.DEFAULT_TREASURY_BASE, simulator.treasuryLimit(s));
        treasuryBuilding(s, 0);
        assertEquals(SettlementSimulator.DEFAULT_TREASURY_BASE + SettlementSimulator.TREASURY_PER_BUILDING,
                simulator.treasuryLimit(s));
        treasuryBuilding(s, 1);
        assertEquals(SettlementSimulator.DEFAULT_TREASURY_BASE + 2 * SettlementSimulator.TREASURY_PER_BUILDING,
                simulator.treasuryLimit(s));
        assertEquals(350, SettlementSimulator.configured(false, false, 350).treasuryLimit(village()), "the base is a setting");
        assertEquals(0, SettlementSimulator.configured(false, false, 0).treasuryLimit(village()));
    }

    @Test
    void incomeBeyondTheLimitIsWasted() {
        Settlement s = village();
        s.ledger().addTreasury(simulator.treasuryLimit(s) + 30);
        simulator.simulateTo(s, 1, 100);
        assertEquals(simulator.treasuryLimit(s), s.ledger().treasury());
        assertEquals(0, simulator.treasuryRoom(s));
    }

    @Test
    void breakingTheSignLowersTheLimitAndTrimsTheTreasury() {
        Settlement s = village();
        treasuryBuilding(s, 0);
        s.ledger().addTreasury(400);
        simulator.simulateTo(s, 1, 100);
        assertEquals(400, s.ledger().treasury(), "within the raised limit");

        s.removeBuilding(0, 64, 0, 1);
        simulator.simulateTo(s, 2, 100);
        assertEquals(SettlementSimulator.DEFAULT_TREASURY_BASE, s.ledger().treasury());
    }

    @Test
    void aMerchantStopsSellingWhenTheTreasuryIsFull() {
        Settlement s = village();
        s.registerBuilding(new Building(BuildingType.SHOP, 0, 64, 0, 0, "test"));
        s.addResident(person(Occupation.MERCHANT));
        // the village already holds 60 of everything: more than it keeps, so there is surplus to sell
        s.ledger().addTreasury(simulator.treasuryLimit(s)); // full
        int stoneBefore = s.ledger().get(ResourceType.STONE);
        simulator.simulateTo(s, 3, 100);
        assertEquals(simulator.treasuryLimit(s), s.ledger().treasury());
        assertEquals(stoneBefore, s.ledger().get(ResourceType.STONE), "nothing is sold, so nothing is thrown away");

        // With room for two emeralds, a day's selling stops at two.
        Settlement almost = village();
        almost.registerBuilding(new Building(BuildingType.SHOP, 0, 64, 0, 0, "test"));
        almost.addResident(person(Occupation.MERCHANT));
        almost.ledger().addTreasury(simulator.treasuryLimit(almost) - 2);
        Map<ResourceType, Integer> before = new java.util.EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            before.put(type, almost.ledger().get(type));
        }
        simulator.simulateTo(almost, 1, 100);
        assertEquals(simulator.treasuryLimit(almost), almost.ledger().treasury());
        int soldWorth = 0; // emeralds' worth of goods that left the stores (food is also eaten, so it is left out)
        for (ResourceType type : ResourceType.values()) {
            if (type != ResourceType.FOOD) {
                soldWorth += (before.get(type) - almost.ledger().get(type)) / SettlementSimulator.unitsPerEmerald(type);
            }
        }
        assertEquals(2, soldWorth, "it sold two emeralds' worth and stopped, rather than selling more to be thrown away");
    }

    @Test
    void rewardsHeldForOpenRequestsCountAgainstTheLimitAndAreNeverTrimmed() {
        Settlement s = village();
        s.ledger().addTreasury(80);
        s.requestMap().put(ResourceType.WOOD, new Request(ResourceType.WOOD, 40, 0, 60, 0, 0));
        // 80 in the treasury and 60 held aside: 140 banked against a limit of 200.
        assertEquals(SettlementSimulator.DEFAULT_TREASURY_BASE - 140, simulator.treasuryRoom(s));

        // Lower the limit below what is banked: only the treasury is trimmed, the promised reward stays.
        SettlementSimulator tight = SettlementSimulator.configured(false, false, 100);
        tight.simulateTo(s, 1, 100);
        // The 40 over the limit came out of the treasury (80 to 40); then the request, which the village's stock
        // had already covered, closed and its 60 came back whole: 40 + 60 = the limit, and nothing promised was lost.
        assertTrue(s.requests().isEmpty());
        assertEquals(100, s.ledger().treasury());
    }

    @Test
    void aRefundFromALapsedRequestAlwaysFitsBecauseItWasCountedAllAlong() {
        Settlement s = village();
        s.ledger().addTreasury(130);
        s.requestMap().put(ResourceType.WOOD, new Request(ResourceType.WOOD, 40, 0, 60, 0, 0));
        s.ledger().addTreasury(10); // banked is now 200: exactly the limit, with 60 of it held
        assertEquals(0, simulator.treasuryRoom(s), "nothing more can come in while the reward is held");
        // The request lapses: its reward goes back to the treasury, and the total is still within the limit.
        int before = SettlementSimulator.banked(s);
        s.ledger().addTreasury(s.requests().iterator().next().unpaid());
        s.requestMap().clear();
        assertEquals(before, SettlementSimulator.banked(s));
        simulator.simulateTo(s, 1, 100);
        assertEquals(before, s.ledger().treasury(), "nothing was destroyed");
    }

    @Test
    void anEmeraldDonationOnlyTakesWhatFits() {
        Donation.Plan some = Donation.planEmeralds(10, 1, 64);
        assertEquals(10, some.items());
        assertEquals(10, some.units());
        assertTrue(some.limitedByRoom());

        Donation.Plan blocks = Donation.planEmeralds(20, 9, 5); // an emerald block is nine
        assertEquals(2, blocks.items());
        assertEquals(18, blocks.units());

        Donation.Plan none = Donation.planEmeralds(8, 9, 1); // room for 8, a block is worth 9
        assertEquals(0, none.items());
        assertEquals(8, none.room());

        Donation.Plan all = Donation.planEmeralds(100, 1, 30);
        assertEquals(30, all.items());
        assertFalse(all.limitedByRoom());

        Donation.Plan full = Donation.planEmeralds(0, 1, 30);
        assertEquals(0, full.items());
    }

    @Test
    void emeraldsHeldAsideForOpenRequestsAreKeptByTheMigrationToo() {
        Settlement old = village();
        Map<String, Object> saved = new LinkedHashMap<>(SettlementCodec.encode(old));
        saved.put("format", 13);
        saved.put("treasury", 300.0); // a JSON number can come back as a double
        saved.remove("conditions");   // an old save may not have the key at all
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", "WOOD");
        request.put("wanted", 40);
        request.put("filled", 10);
        request.put("reward", 120);
        request.put("paid", 30);
        request.put("postedDay", 0);
        saved.put("requests", java.util.List.of(request));

        Settlement loaded = SettlementCodec.decode(saved);
        assertEquals(300 + 90, simulator.treasuryLimit(loaded), "the treasury and the 90 still held for the request");
        assertEquals(300, loaded.ledger().treasury());
    }

    @Test
    void aVillageWithMoreThanTheBaseWhenThisArrivedKeepsIt() {
        // A format-13 save from before the limit existed: 300 emeralds in the treasury and no treasury building.
        Settlement old = village();
        Map<String, Object> saved = new LinkedHashMap<>(SettlementCodec.encode(old));
        saved.put("format", 13);
        saved.put("treasury", 300);
        Map<String, Object> noLegacy = new LinkedHashMap<>(saved);
        noLegacy.remove("conditions"); // as an old save has it: no such key

        Settlement loaded = SettlementCodec.decode(noLegacy);
        assertEquals(300, loaded.ledger().treasury());
        assertEquals(300, simulator.treasuryLimit(loaded), "room for what it holds");
        simulator.simulateTo(loaded, loaded.lastSimulatedDay() + 5, 100);
        assertEquals(300, loaded.ledger().treasury(), "nothing is taken away");
        loaded.ledger().addTreasury(10);
        simulator.simulateTo(loaded, loaded.lastSimulatedDay() + 1, 100);
        assertEquals(300, loaded.ledger().treasury(), "but it cannot grow past it without a building");

        // A village with a small or empty treasury just gets the base.
        Map<String, Object> smallSave = new LinkedHashMap<>(noLegacy);
        smallSave.put("treasury", 20);
        Settlement poor = SettlementCodec.decode(smallSave);
        assertEquals(20, poor.ledger().treasury());
        assertEquals(SettlementSimulator.DEFAULT_TREASURY_BASE, simulator.treasuryLimit(poor));

        // And the allowance is saved with the settlement.
        Settlement again = SettlementCodec.decode(SettlementCodec.encode(loaded));
        assertEquals(300, simulator.treasuryLimit(again));
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(again).get("format"));
    }
}
