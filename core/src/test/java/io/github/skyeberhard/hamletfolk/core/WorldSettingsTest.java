package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** R1.46: the per-world settings and their file. */
class WorldSettingsTest {
    @Test
    void aWorldWithNothingSavedRunsAtNormalSpeed() {
        WorldSettings s = new WorldSettings();
        assertEquals(1, s.speed("world"));
        assertEquals(WorldSettings.DEFAULT_WARP_DAYS, s.of("world").warpDays());
        assertTrue(s.toMap().isEmpty());
    }

    @Test
    void speedAndWarpDaysAreKeptWithinTheirLimits() {
        WorldSettings s = new WorldSettings();
        s.setSpeed("world_nether", 500);
        s.setWarpDays("world_nether", 99);
        assertEquals(FastForward.MAX_SPEED, s.speed("world_nether"));
        assertEquals(WorldSettings.MAX_WARP_DAYS, s.of("world_nether").warpDays());
        s.setSpeed("world_nether", -4);
        assertEquals(1, s.speed("world_nether"));
    }

    @Test
    void settingsSurviveTheFileAndOnlyTheOddOnesAreKept() {
        WorldSettings s = new WorldSettings();
        s.setSpeed("world", 3);
        s.setWarpDays("world", 7);
        s.setSpeed("other", 10);
        WorldSettings back = WorldSettings.fromMap(s.toMap());
        assertEquals(3, back.speed("world"));
        assertEquals(7, back.of("world").warpDays());
        assertEquals(10, back.speed("other"));
        assertEquals(1, back.speed("unknown"));
    }

    @Test
    void backToTheDefaultsKeepsNothing() {
        WorldSettings s = new WorldSettings();
        s.setSpeed("world", 4);
        s.setSpeed("world", 1);
        assertTrue(s.toMap().isEmpty(), "a world at its defaults is not written down");
    }

    @Test
    void aHandEditedFileCannotBreakIt() {
        WorldSettings s = WorldSettings.fromMap(Map.of("world", Map.of("speed", "fast"), "x", "not a map", "y", Map.of("speed", 9999, "warpDays", -1)));
        assertEquals(1, s.speed("world"));
        assertEquals(FastForward.MAX_SPEED, s.speed("y"));
        assertEquals(1, s.of("y").warpDays());
        assertEquals(1, WorldSettings.fromMap(null).speed("world"));
    }
}
