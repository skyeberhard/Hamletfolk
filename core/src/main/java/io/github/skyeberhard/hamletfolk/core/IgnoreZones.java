package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * R1.30: places where villagers are left alone: a trading hall, a shop rig, anything that is not a village. A sign reading
 * {@code [Exempt]} (or {@code [Ignore]}) marks a zone around itself, with its radius on the sign's next line (24 blocks if
 * it gives none, between {@value #MIN_RADIUS} and {@value #MAX_RADIUS}). A villager inside a zone is never enrolled in a
 * settlement, named, re-priced, refused a sale, migrated or counted. A zone placed by an ordinary player only exempts
 * villagers that are not already part of a village, so a sign cannot be used to take a village's people out of it; one
 * placed by an admin ({@code overridesVillages}) exempts everyone inside. The rules are here, so they are tested without
 * a server; the Paper layer keeps the zones in the world's data and reads the signs.
 */
public final class IgnoreZones {
    public static final int DEFAULT_RADIUS = 24;
    public static final int MIN_RADIUS = 4;
    public static final int MAX_RADIUS = 64;
    /** Most zones one player may have, and most in a world, so the signs cannot be used to switch a village off. */
    public static final int MAX_PER_PLAYER = 5;
    public static final int MAX_TOTAL = 100;

    /**
     * One exempt place: the sign's block, the radius around it, who placed it (an id, so a rename does not escape the limit),
     * and whether it may also exempt villagers that already belong to a village.
     */
    public record Zone(int x, int y, int z, int radius, String owner, boolean overridesVillages) {
    }

    /** What adding a zone did. */
    public enum Result {
        ADDED, REPLACED, TOO_MANY_FOR_PLAYER, FULL
    }

    private final List<Zone> zones = new ArrayList<>();

    /** True for a sign line naming an exempt zone: "[Exempt]", "[ignore]", "[ Exempt ]". */
    public static boolean isSign(String line) {
        if (line == null) {
            return false;
        }
        String text = line.strip().toLowerCase(Locale.ROOT);
        if (text.length() < 3 || text.charAt(0) != '[' || text.charAt(text.length() - 1) != ']') {
            return false;
        }
        String name = text.substring(1, text.length() - 1).strip();
        return name.equals("exempt") || name.equals("ignore");
    }

    /** The radius a sign asks for: the number on the line (1 to 3 digits), kept within limits, else the default. */
    public static int radius(String line) {
        if (line == null) {
            return DEFAULT_RADIUS;
        }
        String digits = line.strip();
        if (digits.isEmpty() || digits.length() > 3 || !digits.chars().allMatch(Character::isDigit)) {
            return DEFAULT_RADIUS;
        }
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, Integer.parseInt(digits)));
    }

    /**
     * Adds a zone at a sign. A zone already at that block is replaced (the sign was edited). A player is limited to
     * {@link #MAX_PER_PLAYER} zones, and a world to {@link #MAX_TOTAL}.
     */
    public Result add(Zone zone) {
        for (int i = 0; i < zones.size(); i++) {
            Zone existing = zones.get(i);
            if (existing.x() == zone.x() && existing.y() == zone.y() && existing.z() == zone.z()) {
                // Editing your own sign changes it; editing someone else's takes it over, and counts as one of yours.
                if (!existing.owner().equalsIgnoreCase(zone.owner())) {
                    long theirs = zones.stream().filter(z -> z.owner().equalsIgnoreCase(zone.owner())).count();
                    if (theirs >= MAX_PER_PLAYER) {
                        return Result.TOO_MANY_FOR_PLAYER;
                    }
                }
                zones.set(i, zone);
                return Result.REPLACED;
            }
        }
        if (zones.size() >= MAX_TOTAL) {
            return Result.FULL;
        }
        long theirs = zones.stream().filter(z -> z.owner().equalsIgnoreCase(zone.owner())).count();
        if (theirs >= MAX_PER_PLAYER) {
            return Result.TOO_MANY_FOR_PLAYER;
        }
        zones.add(zone);
        return Result.ADDED;
    }

    /** Removes the zone whose sign is at a block. Returns whether there was one. */
    public boolean removeAt(int x, int y, int z) {
        return zones.removeIf(zone -> zone.x() == x && zone.y() == y && zone.z() == z);
    }

    /** True if a point is within any zone: within the radius horizontally and vertically of its sign. */
    public boolean covers(int x, int y, int z) {
        return coveringZone(x, y, z).isPresent();
    }

    /** The zone covering a point (the first, preferring one that overrides villages), if any. */
    public java.util.Optional<Zone> coveringZone(int x, int y, int z) {
        Zone found = null;
        for (Zone zone : zones) {
            long dx = x - zone.x();
            long dz = z - zone.z();
            if (dx * dx + dz * dz <= (long) zone.radius() * zone.radius() && Math.abs(y - zone.y()) <= zone.radius()) {
                if (zone.overridesVillages()) {
                    return java.util.Optional.of(zone);
                }
                if (found == null) {
                    found = zone;
                }
            }
        }
        return java.util.Optional.ofNullable(found);
    }

    public List<Zone> zones() {
        return List.copyOf(zones);
    }

    public boolean isEmpty() {
        return zones.isEmpty();
    }

    /** One line per zone, "x,y,z,radius,overrides,owner", for storing. */
    public List<String> encode() {
        List<String> lines = new ArrayList<>();
        for (Zone zone : zones) {
            lines.add(zone.x() + "," + zone.y() + "," + zone.z() + "," + zone.radius() + "," + zone.overridesVillages()
                    + "," + zone.owner());
        }
        return lines;
    }

    /** Reads stored lines back, skipping any that are damaged. */
    public static IgnoreZones decode(List<String> lines) {
        IgnoreZones result = new IgnoreZones();
        for (String line : lines) {
            String[] parts = line.split(",", 6);
            if (parts.length < 5) {
                continue;
            }
            try {
                int radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, Integer.parseInt(parts[3].strip())));
                result.zones.add(new Zone(Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip()),
                        Integer.parseInt(parts[2].strip()), radius, parts.length > 5 ? parts[5] : "",
                        Boolean.parseBoolean(parts[4].strip())));
            } catch (NumberFormatException e) {
                // a damaged line: skip it
            }
        }
        return result;
    }
}
