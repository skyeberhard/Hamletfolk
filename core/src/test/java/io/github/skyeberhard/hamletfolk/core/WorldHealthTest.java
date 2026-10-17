package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** R1.44: a world's light and the row the dashboard shows. */
class WorldHealthTest {
    private static Map<String, Object> village(int health, int people, boolean abandoned) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("health", health);
        m.put("population", people);
        m.put("abandoned", abandoned);
        return m;
    }

    private static int light(List<Map<String, Object>> villages) {
        return WorldHealth.light(true, villages).colour();
    }

    @Test
    void aWorldThatIsNotSimulatedOrHasNoVillagesIsGrey() {
        assertEquals(WorldHealth.GREY, WorldHealth.light(false, List.of(village(0, 10, false))).colour());
        assertEquals(WorldHealth.GREY, light(List.of()));
    }

    @Test
    void healthyVillagesMakeAGreenWorld() {
        assertEquals(WorldHealth.GREEN, light(List.of(village(0, 10, false), village(0, 5, false), village(1, 3, false))));
    }

    @Test
    void oneVillageInTroubleInABigWorldIsAmberAtWorst() {
        List<Map<String, Object>> villages = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            villages.add(village(0, 20, false));
        }
        villages.add(village(2, 20, false));
        assertEquals(WorldHealth.AMBER, light(villages));
    }

    @Test
    void halfOfThePeopleHeldBackIsAmber() {
        assertEquals(WorldHealth.AMBER, light(List.of(village(1, 10, false), village(0, 10, false))));
        assertEquals(WorldHealth.GREEN, light(List.of(village(1, 9, false), village(0, 10, false))));
    }

    @Test
    void halfOfThePeopleInTroubleIsRed() {
        assertEquals(WorldHealth.RED, light(List.of(village(2, 10, false), village(0, 10, false))));
        assertEquals(WorldHealth.RED, light(List.of(village(2, 0, true), village(2, 0, true))));
    }

    @Test
    void aStoppedClockMakesAHealthyWorldAmberAndChangesNothingElse() {
        List<Map<String, Object>> healthy = List.of(village(0, 10, false));
        assertEquals(WorldHealth.AMBER, WorldHealth.light(true, healthy, false).colour());
        assertTrue(WorldHealth.light(true, healthy, false).why().contains("clock is stopped"));
        assertEquals(WorldHealth.GREEN, WorldHealth.light(true, healthy, true).colour());
        assertEquals(WorldHealth.RED, WorldHealth.light(true, List.of(village(2, 10, false)), false).colour(), "a worse light is left as it is");
        assertEquals(WorldHealth.GREY, WorldHealth.light(true, List.of(), false).colour(), "no villages, nothing to freeze");
    }

    @Test
    void theClockStartsAtSixAndThePeriodsFollowTheSky() {
        assertEquals("6:00", WorldHealth.clock(0));
        assertEquals("12:00", WorldHealth.clock(6000));
        assertEquals("18:00", WorldHealth.clock(12000));
        assertEquals("0:00", WorldHealth.clock(18000));
        assertEquals("6:00", WorldHealth.clock(24000));
        assertEquals("day", WorldHealth.period(6000));
        assertEquals("dusk", WorldHealth.period(12500));
        assertEquals("night", WorldHealth.period(18000));
        assertEquals("dawn", WorldHealth.period(23500));
    }

    @Test
    void theRowCountsVillagesByColourAndNamesWhoIsWhere() {
        List<Map<String, Object>> villages = List.of(village(0, 10, false), village(1, 5, false), village(2, 2, false));
        Map<String, Object> row = WorldHealth.row(new WorldHealth.Facts("world", "NORMAL", 12, 6000, "clear", "NORMAL", true,
                List.of(new WorldHealth.PlayerAt("Skye", "abc", "Foxhaven"), new WorldHealth.PlayerAt("Owen", "", ""))), villages);
        assertEquals(3, row.get("villages"));
        assertEquals(1, row.get("green"));
        assertEquals(1, row.get("amber"));
        assertEquals(1, row.get("red"));
        assertEquals(17L, row.get("people"));
        assertEquals(WorldHealth.AMBER, row.get("light"));
        assertEquals("12:00", row.get("clock"));
        assertEquals(2, ((List<?>) row.get("players")).size());
        assertTrue(row.get("why").toString().contains("trouble"));
    }
}
