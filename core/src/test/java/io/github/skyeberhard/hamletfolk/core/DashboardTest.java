package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R1.35: what the dashboard shows, and what it says is in a village's way. */
class DashboardTest {
    private Settlement village() {
        Settlement s = new Settlement(new UUID(150, 1), "Dashham", "world", 10, 20, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(new Resident(new UUID(151, i), "T", "P" + i, Gender.FEMALE, new Traits(50, 50, 50, 80),
                    i == 0 ? Occupation.UNEMPLOYED : Occupation.FARMER, true, 0, null, null, Needs.initial()));
        }
        s.setPlan(PlanGenerator.generate(10, 20, 7L, "plains", HeightSource.flat(64)));
        s.ledger().add(ResourceType.FOOD, 500);
        return s;
    }

    /** Only what Gson turns into JSON: strings, numbers, booleans, lists, maps and null. */
    private static void assertPlainJsonData(Object value, String path) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                assertPlainJsonData(list.get(i), path + "[" + i + "]");
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> assertPlainJsonData(v, path + "." + k));
            return;
        }
        fail(path + " is a " + value.getClass());
    }

    @Test
    void aSummaryHasTheNumbersOfTheOverviewRow() {
        Map<String, Object> row = Dashboard.summary(village(), 200);
        assertEquals("Dashham", row.get("name"));
        assertEquals(4, row.get("population"));
        assertEquals(1, row.get("idle"));
        assertEquals(500, ((Number) ((Map<?, ?>) row.get("stores")).get("food")).intValue());
        assertEquals(200, row.get("treasuryLimit"));
        assertPlainJsonData(row, "summary");
    }

    @Test
    void aDetailCarriesEveryListAPageShowsAndNothingButPlainData() {
        Settlement s = village();
        s.registerBuilding(new Building(BuildingType.FARM, 12, 64, 22, 0, "a player"));
        Map<String, Object> page = Dashboard.detail(s, 200);
        for (String key : List.of("residents", "buildings", "projects", "requestList", "history", "decisions", "wants", "plan", "blockers")) {
            assertTrue(page.containsKey(key), key);
        }
        assertEquals(4, ((List<?>) page.get("residents")).size());
        assertEquals(1, ((List<?>) page.get("buildings")).size());
        assertTrue(((List<?>) ((Map<?, ?>) page.get("plan")).get("lots")).size() > 0);
        assertPlainJsonData(page, "detail");
    }

    @Test
    void aVillageWithPeopleButNoPlanYetStillMakesAPage() {
        Settlement s = village();
        s.setPlan(null);
        Map<String, Object> page = Dashboard.detail(s, 200);
        assertEquals(4, page.get("population"));
        assertTrue(!page.containsKey("plan"));
        assertPlainJsonData(page, "unplanned");
    }

    @Test
    void aVillageWithoutAPlanOrPeopleStillMakesAPage() {
        Settlement s = new Settlement(new UUID(150, 2), "Emptyham", "world", 0, 0, 0);
        Map<String, Object> page = Dashboard.detail(s, 200);
        assertEquals(2, page.get("health"), "an empty village is in trouble");
        assertTrue(!page.containsKey("plan"));
        assertPlainJsonData(page, "empty");
    }

    @Test
    void whatIsInTheWayIsSaidInPlainWords() {
        Settlement s = village();
        s.housing().setChunk(0, 0, 4); // every bed taken
        s.ledger().take(ResourceType.FOOD, 450); // 50 of the 80 a newcomer needs... but no bed free, so the aim is 10 a head: fine
        List<String> blockers = VillageBlockers.of(s, 5, 200);
        assertTrue(blockers.stream().anyMatch(b -> b.startsWith("no free bed")), blockers.toString());
        assertTrue(blockers.stream().anyMatch(b -> b.contains("idle")), blockers.toString());
        assertTrue(blockers.stream().anyMatch(b -> b.startsWith("wood ")), "no wood in store: " + blockers);

        Settlement famished = village();
        famished.conditions().put("famine", 1L);
        assertTrue(VillageBlockers.of(famished, 5, 200).get(0).startsWith("famine"), "the worst first");
        assertEquals(2, Dashboard.summary(famished, 200).get("health"));
    }

    @Test
    void foodUnderTheAimIsNamedWhileABedIsFree() {
        Settlement s = village();
        s.housing().setChunk(0, 0, 8);
        s.ledger().take(ResourceType.FOOD, 440); // 60 of the 80 a newcomer needs
        assertTrue(VillageBlockers.of(s, 5, 200).stream().anyMatch(b -> b.startsWith("food 60 of the 80")));
    }

    /** R1.41: the genders, the biome and what stands out in the land. */
    @Test
    void aSummaryCountsTheGendersAndNamesTheBiomeAndTheLand() {
        Settlement s = village();
        s.addResident(new Resident(new UUID(151, 9), "T", "P9", Gender.MALE, new Traits(50, 50, 50, 80), Occupation.FARMER, true, 0, null, null, Needs.initial()));
        s.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> "plains")));
        Map<String, Object> row = Dashboard.summary(s, 200);
        Map<?, ?> gender = (Map<?, ?>) row.get("gender");
        assertEquals(4L, gender.get("female"));
        assertEquals(1L, gender.get("male"));
        assertEquals("plains", row.get("biome"));
        Map<?, ?> land = (Map<?, ?>) row.get("land");
        assertEquals(6, land.size());
        assertTrue(((Number) land.get("farmland")).intValue() > 90, land.toString());
        assertPlainJsonData(row, "summary");
        Settlement unplanned = new Settlement(new UUID(150, 2), "Planless", "world", 0, 0, 0);
        assertEquals("", Dashboard.summary(unplanned, 200).get("biome"), "no plan, no biome yet");
    }
}
