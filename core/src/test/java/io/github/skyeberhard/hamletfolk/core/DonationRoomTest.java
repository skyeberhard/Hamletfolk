package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R3.11: a donation is only taken as far as it fits, and the rest stays with the donor. */
class DonationRoomTest {
    private final SettlementRegistry registry = new SettlementRegistry();

    /** Four residents: a stone limit of 140 and a food limit of 260 (R3.10). */
    private Settlement village() {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(new Resident(new UUID(0, i + 1), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                    Occupation.NITWIT, true, 10_000, null, null, Needs.initial()));
        }
        return s;
    }

    private static ResourceMapper.Value value(String material) {
        return ResourceMapper.value(material).orElseThrow();
    }

    @Test
    void roomIsWhatIsLeftBeforeTheLimitAndNeverNegative() {
        Settlement s = village();
        assertEquals(140, SettlementSimulator.room(s, ResourceType.STONE));
        assertEquals(260, SettlementSimulator.room(s, ResourceType.FOOD));
        s.ledger().add(ResourceType.STONE, 100);
        assertEquals(40, SettlementSimulator.room(s, ResourceType.STONE));
        s.ledger().add(ResourceType.STONE, 500); // already over the limit
        assertEquals(0, SettlementSimulator.room(s, ResourceType.STONE));
    }

    @Test
    void itemsForAndItemsThatFitRoundTheOppositeWays() {
        ResourceMapper.Value log = value("OAK_LOG");   // 4 units each
        assertEquals(3, log.itemsFor(10));     // 3 logs are needed to earn 10 (they are worth 12)
        assertEquals(2, log.itemsThatFit(10)); // but only 2 fit in room for 10 (they are worth 8)
        ResourceMapper.Value stick = value("STICK");   // half a unit each
        assertEquals(62, stick.itemsFor(31));
        assertEquals(62, stick.itemsThatFit(31));
        assertEquals(0, log.itemsFor(0));
        assertEquals(0, log.itemsThatFit(-5));
        assertEquals(1, value("COBBLESTONE").itemsFor(1));
    }

    @Test
    void everythingFitsWhenThereIsRoom() {
        Donation.Plan plan = Donation.plan(village(), value("OAK_LOG"), 10);
        assertEquals(10, plan.items());
        assertEquals(40, plan.units());
        assertFalse(plan.limitedByRoom());
        // An odd stick stays with the player, as before (R3.12).
        Donation.Plan sticks = Donation.plan(village(), value("STICK"), 63);
        assertEquals(62, sticks.items());
        assertEquals(31, sticks.units());
        assertFalse(sticks.limitedByRoom());
    }

    @Test
    void aDonationIsCutToTheRoomLeftAndTheRestStaysWithTheDonor() {
        Settlement s = village();
        s.ledger().add(ResourceType.STONE, 100); // room for 40
        Donation.Plan stone = Donation.plan(s, value("COBBLESTONE"), 64);
        assertEquals(40, stone.items());
        assertEquals(40, stone.units());
        assertTrue(stone.limitedByRoom());

        Settlement t = village();
        t.ledger().add(ResourceType.WOOD, 130); // room for 10
        Donation.Plan logs = Donation.plan(t, value("OAK_LOG"), 16); // 64 units offered
        assertEquals(2, logs.items());   // 8 units: a third log would not fit
        assertEquals(8, logs.units());
        assertTrue(logs.limitedByRoom());
    }

    @Test
    void aFullStoreTakesNothing() {
        Settlement s = village();
        s.ledger().add(ResourceType.WOOD, 140);
        Donation.Plan plan = Donation.plan(s, value("OAK_LOG"), 10);
        assertEquals(0, plan.items());
        assertEquals(0, plan.units());
        assertEquals(0, plan.room());
        assertTrue(plan.limitedByRoom());
    }

    @Test
    void roomLessThanOneItemsWorthTakesNothingAndIsBlamedOnRoomNotWorth() {
        Settlement s = village();
        s.ledger().add(ResourceType.WOOD, 137); // room for 3 units; a log is worth 4
        Donation.Plan plan = Donation.plan(s, value("OAK_LOG"), 10);
        assertEquals(0, plan.items());
        assertEquals(0, plan.units());
        assertEquals(3, plan.room());
        assertTrue(plan.limitedByRoom());
        // Planks (1 each) still fit in the same room.
        Donation.Plan planks = Donation.plan(s, value("OAK_PLANKS"), 10);
        assertEquals(3, planks.items());
        assertTrue(planks.limitedByRoom());
    }

    @Test
    void somethingWorthLessThanAUnitIsNotTakenAndIsNotBlamedOnRoom() {
        Donation.Plan plan = Donation.plan(village(), value("STICK"), 1);
        assertEquals(0, plan.items());
        assertEquals(0, plan.units());
        assertFalse(plan.limitedByRoom());
    }

    @Test
    void aDonationNeverTakesTheStoresPastTheirLimitWhateverIsOffered() {
        java.util.List<ResourceMapper.Value> values = new java.util.ArrayList<>();
        for (String material : new String[] {"OAK_LOG", "OAK_PLANKS", "STICK", "COBBLESTONE", "IRON_INGOT", "WHEAT",
                "HAY_BLOCK", "BAMBOO"}) {
            values.add(value(material));
        }
        for (int tier = 0; tier <= 6; tier++) { // tools, from worn to nothing up to netherite
            values.add(new ResourceMapper.Value(ResourceType.TOOLS, tier, 1));
        }
        for (ResourceMapper.Value value : values) {
            for (int stock = 0; stock <= 300; stock += 7) {
                for (int offered = 1; offered <= 64; offered += 3) {
                    Settlement s = village();
                    s.ledger().add(value.type(), stock);
                    Donation.Plan plan = Donation.plan(s, value, offered);
                    String where = value + " at stock " + stock + " offered " + offered;
                    assertTrue(plan.items() >= 0 && plan.items() <= offered, where + " took " + plan.items());
                    assertEquals(value.unitsFor(plan.items()), plan.units(), where + ": units must match the items taken");
                    int limit = SettlementSimulator.capacity(s, value.type());
                    assertTrue(stock + plan.units() <= Math.max(stock, limit), where + " would hold " + (stock + plan.units())
                            + " over the limit " + limit);
                    // The player never pays for nothing: the last item taken earned something.
                    assertTrue(plan.items() == 0 || value.unitsFor(plan.items() - 1) < plan.units(), where + ": paid for nothing");
                    // And when the room cut the gift, one more item would either not have fitted or earned nothing more.
                    if (plan.limitedByRoom()) {
                        int more = value.unitsFor(plan.items() + 1);
                        assertTrue(more > plan.room() || more == plan.units(), where + ": room was left for another item");
                        assertTrue(plan.items() < offered, where + ": limited by room but took everything");
                    }
                }
            }
        }
    }
}
