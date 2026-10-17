package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/** R6.5: caravans between villages. */
class ExchangeTest {
    private static int n;
    private static final ToIntFunction<Settlement> ROOM = s -> 1000;
    private static final int RANGE = Exchange.DEFAULT_RANGE;
    private static final java.util.function.Predicate<Settlement> ALL = x -> true;

    private Settlement village(String name, int x, int z, int people, boolean merchant) {
        Settlement s = new Settlement(new UUID(900, n++), name, "world", x, 64, z);
        s.setLastSimulatedDay(40);
        for (int i = 0; i < people; i++) {
            s.addResident(new Resident(new UUID(901, n++), "T", name + i, i % 2 == 0 ? Gender.FEMALE : Gender.MALE, new Traits(50, 50, 50, 50),
                    merchant && i == 0 ? Occupation.MERCHANT : Occupation.FARMER, true, 5, null, null, Needs.initial()));
        }
        s.housing().setChunk(0, 0, people + 2);
        return s;
    }

    private SettlementRegistry pair(Settlement a, Settlement b) {
        SettlementRegistry registry = new SettlementRegistry();
        registry.add(a);
        registry.add(b);
        return registry;
    }

    @Test
    void aVillageWithSurplusSellsToANeighbourThatIsShort() {
        Settlement rich = village("Richford", 0, 0, 10, true);
        Settlement poor = village("Poorton", 200, 0, 10, false);
        rich.ledger().add(Commodity.BREAD, 400);
        poor.ledger().addTreasury(50);
        int bread = rich.ledger().get(Commodity.BREAD);
        List<Exchange.Plan> made = Exchange.run(pair(rich, poor), rich, 40, ROOM, RANGE, ALL);
        assertEquals(1, made.size());
        Exchange.Shipment load = made.get(0).loads().get(0);
        assertEquals(rich, load.from());
        assertEquals(ResourceType.FOOD, load.type());
        assertEquals(bread - load.units(), rich.ledger().get(Commodity.BREAD), "it left the sender's stores");
        assertEquals(load.received(), poor.ledger().get(Commodity.BREAD), "and arrived in the neighbour's, as the same commodity");
        assertEquals(load.units(), load.received(), "a merchant keeps the load whole");
        assertEquals(made.get(0).cash(), rich.ledger().treasury());
        assertEquals(50 - made.get(0).cash(), poor.ledger().treasury());
        assertTrue(made.get(0).cash() > 0);
        assertTrue(rich.history().stream().anyMatch(e -> e.text().contains("A caravan went between Richford and Poorton") && e.text().contains("sent")));
        assertTrue(poor.history().stream().anyMatch(e -> e.text().contains("A caravan went between Poorton and Richford") && e.text().contains("brought")));
        assertEquals(1, rich.history().stream().filter(e -> e.text().startsWith("A caravan")).count(), "one line a visit");
    }

    @Test
    void withNoMerchantTheCarriersKeepAFifth() {
        Settlement rich = village("Richford", 0, 0, 10, false);
        Settlement poor = village("Poorton", 200, 0, 10, false);
        rich.ledger().add(Commodity.BREAD, 400);
        poor.ledger().addTreasury(50);
        Exchange.Shipment load = Exchange.run(pair(rich, poor), rich, 40, ROOM, RANGE, ALL).get(0).loads().get(0);
        assertTrue(load.received() < load.units());
        assertEquals(load.units() * 80 / 100, load.received());
        assertEquals(load.received() / 10, load.worth(), "it is paid for what arrives");
    }

    @Test
    void withAMerchantNothingIsCreatedOrLost() {
        Settlement rich = village("Richford", 0, 0, 10, true);
        Settlement poor = village("Poorton", 100, 100, 10, false);
        rich.ledger().add(Commodity.BREAD, 400);
        poor.ledger().add(Commodity.LOGS, 200);
        poor.ledger().addTreasury(40);
        rich.ledger().addTreasury(40);
        int foodBefore = rich.ledger().get(ResourceType.FOOD) + poor.ledger().get(ResourceType.FOOD);
        int woodBefore = rich.ledger().get(ResourceType.WOOD) + poor.ledger().get(ResourceType.WOOD);
        int moneyBefore = rich.ledger().treasury() + poor.ledger().treasury();
        assertFalse(Exchange.run(pair(rich, poor), rich, 40, ROOM, RANGE, ALL).isEmpty());
        assertEquals(foodBefore, rich.ledger().get(ResourceType.FOOD) + poor.ledger().get(ResourceType.FOOD));
        assertEquals(woodBefore, rich.ledger().get(ResourceType.WOOD) + poor.ledger().get(ResourceType.WOOD));
        assertEquals(moneyBefore, rich.ledger().treasury() + poor.ledger().treasury());
    }

    @Test
    void goodsForGoodsOnlyTheDifferenceIsPaid() {
        Settlement a = village("Aford", 0, 0, 10, true);
        Settlement b = village("Bton", 100, 0, 10, true);
        a.ledger().add(Commodity.BREAD, 400);
        b.ledger().add(Commodity.LOGS, 300);
        a.ledger().addTreasury(30);
        b.ledger().addTreasury(30);
        Exchange.Plan plan = Exchange.run(pair(a, b), a, 40, ROOM, RANGE, ALL).get(0);
        assertEquals(2, plan.loads().size());
        assertTrue(plan.loads().stream().anyMatch(s -> s.from() == a && s.type() == ResourceType.FOOD));
        assertTrue(plan.loads().stream().anyMatch(s -> s.from() == b && s.type() == ResourceType.WOOD));
        int a2b = plan.loads().stream().filter(s -> s.from() == a).mapToInt(Exchange.Shipment::worth).sum();
        int b2a = plan.loads().stream().filter(s -> s.from() == b).mapToInt(Exchange.Shipment::worth).sum();
        assertEquals(Math.abs(a2b - b2a), plan.cash(), "only the difference changes hands");
        assertEquals(60, a.ledger().treasury() + b.ledger().treasury());
    }

    @Test
    void farOrOtherWorldNeighboursAreNotReached() {
        Settlement a = village("Aford", 0, 0, 10, true);
        assertFalse(Exchange.linked(a, village("Far", RANGE + 50, 0, 10, true), 40, RANGE));
        assertTrue(Exchange.linked(a, village("Near", RANGE - 50, 0, 10, true), 40, RANGE));
        assertTrue(Exchange.linked(a, village("Far", RANGE + 50, 0, 10, true), 40, RANGE + 100), "the range is the server's to set");
        Settlement other = new Settlement(new UUID(902, 1), "Nether", "world_nether", 10, 64, 10);
        other.addResident(new Resident(new UUID(902, 2), "T", "X", Gender.MALE, new Traits(50, 50, 50, 50), Occupation.MERCHANT, true, 5, null, null, Needs.initial()));
        assertFalse(Exchange.linked(a, other, 40, RANGE));
    }

    @Test
    void thePairRestsFiveDays() {
        Settlement rich = village("Richford", 0, 0, 10, true);
        Settlement poor = village("Poorton", 100, 0, 10, false);
        rich.ledger().add(Commodity.BREAD, 800);
        poor.ledger().addTreasury(200);
        SettlementRegistry registry = pair(rich, poor);
        assertFalse(Exchange.run(registry, rich, 40, ROOM, RANGE, ALL).isEmpty());
        assertTrue(Exchange.run(registry, rich, 42, ROOM, RANGE, ALL).isEmpty(), "too soon");
        assertTrue(Exchange.run(registry, poor, 43, ROOM, RANGE, ALL).isEmpty(), "the other side is bound by the same rest");
        assertFalse(Exchange.run(registry, rich, 45, ROOM, RANGE, ALL).isEmpty());
    }

    @Test
    void aNeighbourOnlyPaysWhatItHasAndWithNothingThereIsNoSale() {
        Settlement rich = village("Richford", 0, 0, 10, true);
        Settlement poor = village("Poorton", 100, 0, 10, false);
        rich.ledger().add(Commodity.BREAD, 800);
        assertTrue(Exchange.run(pair(rich, poor), rich, 40, ROOM, RANGE, ALL).isEmpty(), "no money, no sale");
        poor.ledger().addTreasury(2);
        List<Exchange.Plan> made = Exchange.run(pair(rich, poor), rich, 40, ROOM, RANGE, ALL);
        assertEquals(1, made.size());
        assertEquals(2, made.get(0).cash());
        assertEquals(20, made.get(0).loads().get(0).units(), "two emeralds buy twenty food");
        assertEquals(0, poor.ledger().treasury());
    }

    @Test
    void aStarvingNeighbourIsSentFoodWhetherOrNotItCanPay() {
        Settlement rich = village("Richford", 0, 0, 10, true);
        Settlement poor = village("Poorton", 100, 0, 10, false);
        rich.ledger().add(Commodity.BREAD, 800);
        poor.conditions().put("famine", 1L);
        List<Exchange.Plan> made = Exchange.run(pair(rich, poor), rich, 40, ROOM, RANGE, ALL);
        assertEquals(1, made.size());
        assertTrue(made.get(0).loads().get(0).relief());
        assertEquals(0, made.get(0).cash());
        assertTrue(poor.ledger().get(Commodity.BREAD) > 0);
        assertTrue(poor.history().stream().anyMatch(e -> e.text().contains("as relief")));
    }

    @Test
    void theSellersTreasuryMustHaveRoomForThePayment() {
        Settlement rich = village("Richford", 0, 0, 10, true);
        Settlement poor = village("Poorton", 100, 0, 10, false);
        rich.ledger().add(Commodity.BREAD, 800);
        poor.ledger().addTreasury(100);
        assertTrue(Exchange.run(pair(rich, poor), rich, 40, s -> 0, RANGE, ALL).isEmpty(), "a full treasury cannot bank the price, so no sale");
    }

    @Test
    void aVillageNeverSellsWhatItNeedsItself() {
        Settlement a = village("Aford", 0, 0, 10, true);
        Settlement b = village("Bton", 100, 0, 10, false);
        a.ledger().add(Commodity.BREAD, 10 * SettlementSimulator.FOOD_KEPT_PER_HEAD); // exactly what it keeps
        b.ledger().addTreasury(100);
        assertTrue(Exchange.run(pair(a, b), a, 40, ROOM, RANGE, ALL).isEmpty());
    }

    @Test
    void theExplanationSaysWhyNothingTrades() {
        Settlement a = village("Aford", 0, 0, 10, false);
        Settlement b = village("Bton", 1000, 0, 10, false);
        String why = Exchange.explain(a, b, RANGE);
        assertTrue(why.contains("1000 blocks apart") && why.contains("out of reach") && why.contains("no merchant"), why);
    }

    @Test
    void theRestIsKeptOnTheWorldsClockWhateverEachVillagesOwnIs() {
        Settlement rich = village("Richford", 0, 0, 10, true);
        Settlement poor = village("Poorton", 100, 0, 10, false);
        rich.setLastSimulatedDay(100);
        poor.setLastSimulatedDay(400); // a village that was warped far ahead
        rich.ledger().add(Commodity.BREAD, 800);
        poor.ledger().addTreasury(200);
        SettlementRegistry registry = pair(rich, poor);
        long world = 100;
        assertFalse(Exchange.run(registry, rich, world, ROOM, RANGE, ALL).isEmpty());
        assertTrue(Exchange.run(registry, poor, world + 1, ROOM, RANGE, ALL).isEmpty(), "the other village cannot trade again at once");
        assertTrue(Exchange.run(registry, rich, world + 2, ROOM, RANGE, ALL).isEmpty());
        assertFalse(Exchange.run(registry, rich, world + 6, ROOM, RANGE, ALL).isEmpty());
    }

    @Test
    void eachVillagesHistoryIsFiledUnderItsOwnDay() {
        Settlement rich = village("Richford", 0, 0, 10, true);
        Settlement poor = village("Poorton", 100, 0, 10, false);
        poor.setLastSimulatedDay(30);
        rich.setLastSimulatedDay(900);
        rich.ledger().add(Commodity.BREAD, 800);
        poor.ledger().addTreasury(50);
        Exchange.run(pair(rich, poor), rich, 20, ROOM, RANGE, ALL);
        long poorDay = poor.history().stream().filter(e -> e.text().startsWith("A caravan")).findFirst().orElseThrow().day();
        assertTrue(poorDay < 100, "not dated from the other village's clock: " + poorDay);
    }

    @Test
    void aVillageThatIsNotBeingSimulatedIsLeftAlone() {
        Settlement rich = village("Richford", 0, 0, 10, true);
        Settlement poor = village("Poorton", 100, 0, 10, false);
        rich.ledger().add(Commodity.BREAD, 800);
        poor.ledger().addTreasury(50);
        assertTrue(Exchange.run(pair(rich, poor), rich, 40, ROOM, RANGE, s -> s != poor).isEmpty(), "a neighbour out of scope is not traded with");
        assertTrue(Exchange.run(pair(rich, poor), rich, 40, ROOM, RANGE, s -> s != rich).isEmpty(), "nor does it trade itself");
        assertEquals(0, poor.ledger().get(Commodity.BREAD));
    }

    @Test
    void aLoadIsCutBackUntilTheDifferenceCanBePaid() {
        Settlement a = village("Aford", 0, 0, 10, true);
        Settlement b = village("Bton", 100, 0, 10, true);
        a.ledger().add(Commodity.BREAD, 400); // b is short of food: b owes a
        b.ledger().add(Commodity.LOGS, 40);   // a has no use for much wood
        b.ledger().addTreasury(2);             // but b can pay only two emeralds
        Exchange.Plan plan = Exchange.run(pair(a, b), a, 40, ROOM, RANGE, ALL).get(0);
        int a2b = plan.loads().stream().filter(s -> s.from() == a).mapToInt(Exchange.Shipment::worth).sum();
        int b2a = plan.loads().stream().filter(s -> s.from() == b).mapToInt(Exchange.Shipment::worth).sum();
        assertEquals(a2b - b2a, plan.cash(), "the difference is exactly what is paid");
        assertTrue(plan.cash() <= 2, "no more than b held");
        assertEquals(0, b.ledger().treasury() - (2 - plan.cash()));
    }

    @Test
    void reliefAndAPaidLoadBackSettleOnlyTheOneThatIsPaidFor() {
        Settlement rich = village("Richford", 0, 0, 10, true);
        Settlement poor = village("Poorton", 100, 0, 10, true);
        rich.ledger().add(Commodity.BREAD, 800);
        poor.conditions().put("famine", 1L);
        poor.ledger().add(Commodity.LOGS, 300);
        poor.ledger().addTreasury(20);
        Exchange.Plan plan = Exchange.run(pair(rich, poor), rich, 40, ROOM, RANGE, ALL).get(0);
        assertTrue(plan.loads().stream().anyMatch(Exchange.Shipment::relief));
        Exchange.Shipment wood = plan.loads().stream().filter(s -> !s.relief()).findFirst().orElse(null);
        if (wood != null) {
            assertEquals(rich, plan.payer(), "the rich village pays for the wood it takes");
            assertEquals(wood.worth(), plan.cash());
        }
    }

    @Test
    void aTradeLeavesNoOneInFamineAndResetForgetsTheRest() {
        Settlement rich = village("Richford", 0, 0, 10, true);
        Settlement poor = village("Poorton", 100, 0, 10, true);
        rich.ledger().add(Commodity.BREAD, 400);
        poor.ledger().addTreasury(100);
        SettlementRegistry registry = pair(rich, poor);
        Exchange.run(registry, rich, 40, ROOM, RANGE, ALL);
        assertTrue(rich.ledger().get(ResourceType.FOOD) >= 10 * SettlementSimulator.FOOD_KEPT_PER_HEAD, "the seller keeps what it keeps");
        Exchange.reset(rich, registry);
        assertTrue(Exchange.linked(rich, poor, 41, RANGE), "after a reset the pair may trade again at once");
    }
}
