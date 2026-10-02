package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R3.3: a short village posts a request, and fulfilling it pays emeralds from the treasury. */
class RequestTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();

    private static Resident person() {
        // Makes nothing and is born far in the future of these runs, so only the test changes the stores.
        return new Resident(UUID.randomUUID(), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, 50),
                Occupation.NITWIT, true, 10_000, null, null, Needs.initial());
    }

    /** Four residents who make nothing, so food, wood, stone, metal and tools are all short. */
    private Settlement village(int treasury) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(person());
        }
        s.ledger().addTreasury(treasury);
        return s;
    }

    private static Request request(Settlement s, ResourceType type) {
        return s.requests().stream().filter(r -> r.type() == type).findFirst().orElse(null);
    }

    @Test
    void aShortVillagePostsRequestsItCanAffordAndHoldsTheRewardAside() {
        Settlement s = village(100);
        simulator.simulateTo(s, 1, 100);

        // Three at most, in order: food (80 units, 16 emeralds), wood (24, 8), stone (24, 10).
        assertEquals(3, s.requests().size());
        assertEquals(80, request(s, ResourceType.FOOD).wanted());
        assertEquals(16, request(s, ResourceType.FOOD).reward());
        assertEquals(24, request(s, ResourceType.WOOD).wanted());
        assertEquals(8, request(s, ResourceType.WOOD).reward());
        assertEquals(10, request(s, ResourceType.STONE).reward());
        assertEquals(100 - 16 - 8 - 10, s.ledger().treasury());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("is asking for 80 food and will pay 16 emeralds")));
    }

    @Test
    void aVillageThatCannotPayDoesNotAsk() {
        Settlement s = village(3);
        simulator.simulateTo(s, 5, 100);
        assertTrue(s.requests().isEmpty());
        assertEquals(3, s.ledger().treasury());
    }

    @Test
    void aVillageThatIsNotShortDoesNotAsk() {
        Settlement s = village(100);
        for (ResourceType type : ResourceType.values()) {
            s.ledger().add(type, 60); // above the 12 of anything else four residents want
        }
        s.ledger().add(ResourceType.FOOD, 140); // 200: well above the 40 wanted, and four residents eat 8 a day
        simulator.simulateTo(s, 3, 100);
        assertTrue(s.requests().isEmpty());
    }

    @Test
    void fulfillingPaysTheRewardInProportionAndFinishesTheRequest() {
        Settlement s = village(100);
        simulator.simulateTo(s, 1, 100);
        Request wood = request(s, ResourceType.WOOD); // 24 units for 8 emeralds
        int treasuryBefore = s.ledger().treasury();

        int first = simulator.fulfil(s, ResourceType.WOOD, 12, 2, "Skye");
        assertEquals(4, first);
        assertEquals(12, wood.remaining());
        assertNotNull(request(s, ResourceType.WOOD));

        int second = simulator.fulfil(s, ResourceType.WOOD, 100, 2, "Skye"); // more than is needed
        assertEquals(4, second);
        assertEquals(8, first + second); // exactly the reward, no more
        assertEquals(null, request(s, ResourceType.WOOD));
        assertEquals(treasuryBefore, s.ledger().treasury(), "paid from the held reward, not the treasury");
        assertEquals(0, simulator.fulfil(s, ResourceType.WOOD, 10, 2, "Skye"), "nothing is asked for now");
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("Skye filled")
                && e.text().contains("request for 24 wood")));
    }

    @Test
    void thePayoutRoundsDownAndTheLastDeliveryTakesTheRemainder() {
        Request request = new Request(ResourceType.METAL, 24, 0, 8, 0, 1);
        int paid = 0;
        for (int units : List.of(5, 5, 14)) {
            request.fill(units);
            paid += request.settle();
        }
        assertEquals(8, paid);
        assertEquals(0, request.remaining());
        assertEquals(0, request.unpaid());
    }

    @Test
    void deliveringSomethingNotAskedForPaysNothing() {
        Settlement s = village(100);
        simulator.simulateTo(s, 1, 100);
        assertEquals(0, simulator.fulfil(s, ResourceType.GOODS, 50, 2, "Skye"));
    }

    @Test
    void aRequestIsWithdrawnWhenTheStoresRecoverAndTheUnpaidRewardReturns() {
        Settlement s = village(100);
        simulator.simulateTo(s, 1, 100);
        simulator.fulfil(s, ResourceType.WOOD, 12, 1, "Skye"); // paid 4 of 8
        s.ledger().add(ResourceType.WOOD, 50); // the stores recover on their own

        simulator.simulateTo(s, 2, 100);
        assertEquals(null, request(s, ResourceType.WOOD));
        // (The freed slot may be taken at once by a request for something else, so check the record.)
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("request for 24 wood was withdrawn")
                && e.text().contains("4 emeralds went back to the treasury")), "history: " + s.history());
    }

    @Test
    void aRequestLapsesAfterThirtyDaysAndTheVillageWaitsBeforeAskingAgain() {
        Settlement s = village(200);
        simulator.simulateTo(s, 1, 100);
        assertNotNull(request(s, ResourceType.FOOD)); // food is tried first

        simulator.simulateTo(s, 1 + SettlementSimulator.REQUEST_EXPIRY_DAYS, 100);
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("request for 80 food lapsed")
                && e.text().contains("emeralds went back to the treasury")), "history: " + s.history());
        // Still short, but it has just closed: it waits out the cooldown before asking again.
        assertEquals(null, request(s, ResourceType.FOOD));

        simulator.simulateTo(s, 1 + SettlementSimulator.REQUEST_EXPIRY_DAYS + SettlementSimulator.REQUEST_COOLDOWN_DAYS, 100);
        assertNotNull(request(s, ResourceType.FOOD), "asks again once the cooldown has passed");
    }

    @Test
    void anEmptiedVillageGivesItsHeldRewardsBack() {
        Settlement s = village(100);
        simulator.simulateTo(s, 1, 100);
        assertEquals(66, s.ledger().treasury()); // 34 is held aside in three requests
        new java.util.ArrayList<>(s.residents()).forEach(r -> s.removeResident(r.id()));

        simulator.simulateTo(s, 2, 100);
        assertEquals(100, s.ledger().treasury(), "nothing is stuck in requests nobody can fill");
        assertTrue(s.requests().isEmpty());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("no one is left")), "history: " + s.history());
    }

    @Test
    void aDamagedSaveCannotMintOrDestroyEmeralds() {
        Settlement s = village(100);
        simulator.simulateTo(s, 1, 100);
        Map<String, Object> encoded = SettlementCodec.encode(s);
        @SuppressWarnings("unchecked")
        List<Object> requests = (List<Object>) encoded.get("requests");
        @SuppressWarnings("unchecked")
        Map<String, Object> food = (Map<String, Object>) requests.get(0);
        food.put("wanted", 0); // would have paid the whole reward for one unit
        food.put("filled", 500);
        food.put("paid", 9999);
        requests.add(new java.util.LinkedHashMap<>(food)); // a duplicate entry for the same resource

        Settlement loaded = SettlementCodec.decode(encoded);
        for (Request request : loaded.requests()) {
            assertTrue(request.wanted() >= 1);
            assertTrue(request.filled() >= 0 && request.filled() <= request.wanted());
            assertTrue(request.paid() >= 0 && request.paid() <= request.reward());
            assertTrue(request.unpaid() >= 0);
        }
        assertTrue(loaded.ledger().treasury() >= 0);
        assertEquals(3, loaded.requests().size(), "one request per resource");
    }

    @Test
    void openRequestsAreSavedAndAnOlderSaveWithoutAnyLoads() {
        Settlement s = village(100);
        simulator.simulateTo(s, 1, 100);
        simulator.fulfil(s, ResourceType.WOOD, 12, 1, "Skye");

        Map<String, Object> encoded = SettlementCodec.encode(s);
        Settlement loaded = SettlementCodec.decode(encoded);
        Request wood = request(loaded, ResourceType.WOOD);
        assertEquals(24, wood.wanted());
        assertEquals(12, wood.filled());
        assertEquals(4, wood.paid());
        assertEquals(3, loaded.requests().size());
        assertEquals(encoded, SettlementCodec.encode(loaded));

        // A format-7 save has no "requests" key at all.
        Map<String, Object> old = new java.util.LinkedHashMap<>(encoded);
        old.remove("requests");
        old.put("format", 7);
        assertTrue(SettlementCodec.decode(old).requests().isEmpty());
    }

    @Test
    void aResidentMentionsWhatTheVillageWillPayFor() {
        Settlement s = village(100);
        simulator.simulateTo(s, 1, 100);
        Resident talker = s.residents().iterator().next();
        Random random = new Random(1);
        boolean said = false;
        for (int i = 0; i < 300 && !said; i++) {
            String line = Dialogue.smallTalk(talker, s, 1, random);
            said = line.contains("will pay") && line.contains("emeralds for");
        }
        assertTrue(said);
        assertFalse(s.requests().isEmpty());
    }
}
