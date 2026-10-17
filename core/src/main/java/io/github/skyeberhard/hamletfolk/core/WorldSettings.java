package io.github.skyeberhard.hamletfolk.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * R1.46: what an admin has set for each world, kept over restarts (in a file of its own, never in a village's save). A world has a speed,
 * the fast-forward its villages run at (1 is normal; {@link FastForward#MAX_SPEED} at most), and the number of days its "warp now" starts
 * at. A village's own fast-forward, if set, wins over its world's. Pure: the Paper layer reads and writes the file.
 */
public final class WorldSettings {
    /** Most days the saved warp default may be (the same limit as warping every village). */
    public static final int MAX_WARP_DAYS = AdminAction.MAX_WARP_ALL_DAYS;
    /** What a world is set to when nothing was saved. */
    public static final int DEFAULT_WARP_DAYS = 5;

    /** One world's settings. */
    public record Entry(int speed, int warpDays) {
        public Entry {
            speed = Math.max(1, Math.min(FastForward.MAX_SPEED, speed));
            warpDays = Math.max(1, Math.min(MAX_WARP_DAYS, warpDays));
        }
    }

    private static final Entry DEFAULT = new Entry(1, DEFAULT_WARP_DAYS);

    private final Map<String, Entry> worlds = new TreeMap<>();

    /** The settings of a world (the defaults if none were saved). */
    public Entry of(String world) {
        return worlds.getOrDefault(world, DEFAULT);
    }

    /** The speed the world's villages run at unless a village has its own. */
    public int speed(String world) {
        return of(world).speed();
    }

    public void setSpeed(String world, int speed) {
        put(world, new Entry(speed, of(world).warpDays()));
    }

    public void setWarpDays(String world, int days) {
        put(world, new Entry(of(world).speed(), days));
    }

    private void put(String world, Entry entry) {
        if (entry.equals(DEFAULT)) {
            worlds.remove(world); // (nothing to keep)
        } else {
            worlds.put(world, entry);
        }
    }

    /** The settings as plain data for the file: {@code {world: {speed, warpDays}}}. */
    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        worlds.forEach((name, e) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("speed", e.speed());
            row.put("warpDays", e.warpDays());
            out.put(name, row);
        });
        return out;
    }

    /** Reads what {@link #toMap} wrote; anything unreadable is left out, so a hand-edited file cannot stop the plugin. */
    public static WorldSettings fromMap(Map<?, ?> map) {
        WorldSettings out = new WorldSettings();
        if (map == null) {
            return out;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> row && e.getKey() != null && !e.getKey().toString().isBlank()) {
                int speed = row.get("speed") instanceof Number n ? n.intValue() : 1;
                int days = row.get("warpDays") instanceof Number n ? n.intValue() : DEFAULT_WARP_DAYS;
                out.put(e.getKey().toString(), new Entry(speed, days));
            }
        }
        return out;
    }
}
