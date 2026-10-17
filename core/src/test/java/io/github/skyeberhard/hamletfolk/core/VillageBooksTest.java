package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R1.45: what the Treasury, Stores and Quests tabs show. */
class VillageBooksTest {
    private Settlement village() {
        Settlement s = new Settlement(new UUID(700, 1), "Ledgerton", "world", 0, 0, 0);
        s.setLastSimulatedDay(30);
        return s;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object o) {
        return (List<Map<String, Object>>) o;
    }

    @Test
    void theTreasuryTabHasTheBalanceTheRoomAndWhatIsHeldForRequests() {
        Settlement s = village();
        s.ledger().addTreasury(120);
        s.requestMap().put(ResourceType.WOOD, new Request(ResourceType.WOOD, 40, 10, 8, 3, 20));
        Map<String, Object> t = VillageBooks.treasury(s, 200);
        assertEquals(120, t.get("balance"));
        assertEquals(80, t.get("room"));
        assertEquals(5, t.get("heldForRequests"));
        assertEquals(false, t.get("hasCounter"));
    }

    @Test
    void theTreasuryLinesAreTheOnesThatMovedMoney() {
        Settlement s = village();
        s.recordDonation(30, "Skye", 5, "oak log");
        s.record(30, HistoryEvent.Kind.BIRTH, "Someone was born.");
        s.record(31, HistoryEvent.Kind.MILESTONE, "A passing trader bought what Ledgerton could spare, for 6 emeralds.");
        List<Map<String, Object>> lines = list(VillageBooks.treasury(s, 200).get("lines"));
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).get("text").toString().contains("trader"), "newest first");
    }

    @Test
    void theStoresTabListsCommoditiesWithWhatTheBarrelShowsAndWhereItIsDrawn() {
        Settlement s = village();
        s.ledger().add(Commodity.BREAD, 40);
        s.registerBuilding(new Building(BuildingType.GRANARY, 5, 64, 5, 1, "p"));
        Map<String, Object> stores = VillageBooks.stores(s);
        Map<String, Object> food = list(stores.get("categories")).stream().filter(c -> c.get("type").equals("food")).findFirst().orElseThrow();
        assertEquals("Granary", food.get("shownAt"));
        List<Map<String, Object>> held = list(food.get("commodities"));
        assertEquals(1, held.size());
        assertEquals("bread", held.get(0).get("commodity"));
        assertEquals("bread", held.get(0).get("item"));
        assertTrue(((Number) held.get(0).get("shown")).intValue() > 0);
        Map<String, Object> wood = list(stores.get("categories")).stream().filter(c -> c.get("type").equals("wood")).findFirst().orElseThrow();
        assertEquals("", wood.get("shownAt"), "no sawmill, no barrel");
    }

    @Test
    void thievesAreListedOnTheStoresTab() {
        Settlement s = village();
        s.record(30, HistoryEvent.Kind.THEFT, "Skye took from the food stores.");
        assertEquals(1, list(VillageBooks.stores(s).get("lines")).size());
    }

    @Test
    void theQuestsTabHasTheOpenRequestsAndTheirAge() {
        Settlement s = village();
        s.requestMap().put(ResourceType.STONE, new Request(ResourceType.STONE, 20, 5, 4, 1, 25));
        s.record(28, HistoryEvent.Kind.MILESTONE, "The request for 20 wood lapsed.");
        Map<String, Object> q = VillageBooks.quests(s, 30);
        List<Map<String, Object>> open = list(q.get("open"));
        assertEquals(1, open.size());
        assertEquals(15, open.get(0).get("remaining"));
        assertEquals(5L, open.get(0).get("daysOpen"));
        assertEquals(1, list(q.get("lines")).size());
    }

    @Test
    void anEmptyVillageStillGivesEveryTab() {
        Map<String, Object> all = VillageBooks.asData(village(), 200, 30);
        assertTrue(all.containsKey("treasuryTab") && all.containsKey("storesTab") && all.containsKey("questsTab"));
    }
}
