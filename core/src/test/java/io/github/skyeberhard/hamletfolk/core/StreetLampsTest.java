package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StreetLampsTest {
    @Test
    void litFromDuskToDawn() {
        assertFalse(StreetLamps.lit(1000, false));
        assertFalse(StreetLamps.lit(11999, false));
        assertTrue(StreetLamps.lit(12000, false));
        assertTrue(StreetLamps.lit(18000, false));
        assertTrue(StreetLamps.lit(22999, false));
        assertFalse(StreetLamps.lit(23000, false));
    }

    @Test
    void aStormLightsThemAllDay() {
        assertTrue(StreetLamps.lit(6000, true));
    }

    @Test
    void theClockMayRunPastADay() {
        assertTrue(StreetLamps.lit(24000L * 5 + 15000, false));
        assertFalse(StreetLamps.lit(24000L * 5 + 3000, false));
    }
}
