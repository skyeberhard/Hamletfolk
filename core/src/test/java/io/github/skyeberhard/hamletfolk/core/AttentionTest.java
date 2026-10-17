package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R1.48: the list of what needs a human. */
class AttentionTest {
    private static int n;

    private Settlement village(String name, int people) {
        Settlement s = new Settlement(new UUID(990, n++), name, "world", 0, 64, 0);
        s.setLastSimulatedDay(40);
        for (int i = 0; i < people; i++) {
            s.addResident(new Resident(new UUID(991, n++), "T", name + i, i % 2 == 0 ? Gender.FEMALE : Gender.MALE, new Traits(50, 50, 50, 50), Occupation.FARMER, true, 5, null, null,
                    Needs.initial()));
        }
        s.housing().setChunk(0, 0, people + 3);
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.ledger().add(ResourceType.TOOLS, 20);
        s.ledger().add(ResourceType.FOOD, 500);
        return s;
    }

    private static boolean has(List<Attention.Item> items, String kind) {
        return items.stream().anyMatch(i -> i.kind().equals(kind));
    }

    @Test
    void aHealthyVillageNeedsNothing() {
        assertTrue(Attention.of(village("Fine", 6), 40, 200).isEmpty(), Attention.of(village("Fine", 6), 40, 200).toString());
    }

    @Test
    void anEmptyVillageOffersToSendTravellers() {
        Settlement s = village("Gone", 0);
        List<Attention.Item> items = Attention.of(s, 40, 200);
        assertEquals(1, items.size());
        assertEquals("resettle", items.get(0).action());
        assertEquals(2, items.get(0).severity());
        assertEquals(3, items.get(0).params().get("travellers"));
    }

    @Test
    void famineAndDangerAreSeriousAndFamineSaysHowLong() {
        Settlement s = village("Hungry", 6);
        s.conditions().put("famine", 1L);
        s.setLastSimulatedDay(30);
        s.record(30, HistoryEvent.Kind.FAMINE, "The food ran out.");
        s.setLastSimulatedDay(40);
        s.raiseThreat(100);
        List<Attention.Item> items = Attention.of(s, 40, 200);
        assertTrue(has(items, "famine") && has(items, "danger"));
        assertTrue(items.stream().filter(i -> i.kind().equals("famine")).findFirst().orElseThrow().text().contains("for 10 days"));
        assertTrue(items.stream().allMatch(i -> i.severity() == 2 || i.severity() == 1));
        assertEquals(2, items.get(0).severity(), "the most serious first");
    }

    @Test
    void aFullVillageSuggestsBuildingAHouseUnlessAnAdminForbadeIt() {
        Settlement s = village("Full", 6);
        s.housing().setChunk(0, 0, 6); // 6 people, 6 beds
        Attention.Item item = Attention.of(s, 40, 200).stream().filter(i -> i.kind().equals("nobed")).findFirst().orElseThrow();
        assertEquals("build-next", item.action());
        assertEquals("HOUSE", item.params().get("building"));
    }

    @Test
    void idleAdultsAreWorthALookOnlyInNumbers() {
        Settlement few = village("Few", 6);
        few.residents().stream().limit(2).forEach(r -> r.setOccupation(Occupation.UNEMPLOYED));
        assertFalse(has(Attention.of(few, 40, 200), "idle"));
        Settlement many = village("Many", 6);
        many.residents().stream().limit(3).forEach(r -> r.setOccupation(Occupation.UNEMPLOYED));
        assertTrue(has(Attention.of(many, 40, 200), "idle"));
    }

    @Test
    void everyVillageIsListedWorstFirstThenByName() {
        Settlement bad = village("Zed", 6);
        bad.conditions().put("famine", 1L);
        Settlement idle = village("Alpha", 6);
        idle.residents().stream().limit(3).forEach(r -> r.setOccupation(Occupation.UNEMPLOYED));
        SettlementRegistry registry = new SettlementRegistry();
        registry.add(idle);
        registry.add(bad);
        List<Attention.Item> all = Attention.of(registry.settlements(), s -> 200);
        assertEquals("Zed", all.get(0).village(), "famine outranks idle hands, whatever the name");
        List<Map<String, Object>> data = Attention.asData(all);
        assertEquals(all.size(), data.size());
        assertEquals("Zed", data.get(0).get("name"));
    }
}
