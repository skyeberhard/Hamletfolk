package io.github.skyeberhard.hamletfolk.core;

/**
 * R4.54: when the street lamps are lit. A village's lamps come on at dusk and go out at dawn (and burn all day in a storm), so the
 * streets look lived in. Pure: the Paper layer asks it for the world's clock.
 */
public final class StreetLamps {
    /** The tick of the day the sun sets (the sky starts to darken). */
    public static final long DUSK = 12000;
    /** The tick of the day the sun is up again (monsters that burn in daylight stop burning from about here). */
    public static final long DAWN = 23000;

    private StreetLamps() {
    }

    /** True if the lamps should be lit at this time of day (0 to 24000 ticks, the world's clock) or in a storm. */
    public static boolean lit(long timeOfDay, boolean storm) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return storm || (t >= DUSK && t < DAWN);
    }
}
