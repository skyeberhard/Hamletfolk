package io.github.skyeberhard.hamletfolk.core;

/**
 * A building a player registered with a sign (R2.1): what it is, where its sign is, and who put it
 * there and when. Its place is the sign's block position; a sign registers one building.
 */
public record Building(BuildingType type, int x, int y, int z, long registeredDay, String registeredBy) {

    /** The block position as a stable key, e.g. "12,64,-30". */
    public String key() {
        return key(x, y, z);
    }

    public static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
