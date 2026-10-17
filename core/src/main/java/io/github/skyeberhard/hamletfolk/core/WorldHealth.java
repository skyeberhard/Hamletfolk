package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R1.44: how a world is doing, for the dashboard's side tray and world page. A world's light comes from its villages' lights
 * ({@link Dashboard#summary}'s {@code health}: 0 green, 1 amber, 2 red) weighted by the people who live in them, so one struggling
 * village in a big world is amber at worst. Pure: the Paper layer says what it knows of the world ({@link Facts}).
 */
public final class WorldHealth {
    public static final int GREEN = 0;
    public static final int AMBER = 1;
    public static final int RED = 2;
    /** A world that is not simulated, or has no villages to judge. */
    public static final int GREY = 3;

    private WorldHealth() {
    }

    /** One player in a world and the village they stand inside the boundary of (empty strings if none). */
    public record PlayerAt(String name, String villageId, String villageName) {
    }

    /** What the server knows of a world. */
    public record Facts(String name, String environment, long day, long timeOfDay, String weather, String difficulty, boolean simulated,
            List<PlayerAt> players) {
    }

    /** What a light is, and why. */
    public record Light(int colour, String why) {
    }

    /** The light of a world from its villages' summaries (each with {@code health}, {@code population} and {@code abandoned}). */
    public static Light light(boolean simulated, List<Map<String, Object>> villages) {
        if (!simulated) {
            return new Light(GREY, "this world is not simulated");
        }
        if (villages.isEmpty()) {
            return new Light(GREY, "no villages here");
        }
        long people = 0;
        long inRed = 0;
        long inAmber = 0;
        int red = 0;
        int alive = 0;
        for (Map<String, Object> v : villages) {
            long pop = ((Number) v.get("population")).longValue();
            int health = ((Number) v.get("health")).intValue();
            if (!Boolean.TRUE.equals(v.get("abandoned"))) {
                alive++;
            }
            people += pop;
            if (health == 2) {
                red++;
                inRed += pop;
            } else if (health == 1) {
                inAmber += pop;
            }
        }
        if (alive == 0) {
            return new Light(RED, "every village here is abandoned");
        }
        if (people == 0) {
            return new Light(RED, "nobody lives here");
        }
        if (inRed * 2 >= people) {
            return new Light(RED, "half or more of its people live in villages in trouble");
        }
        if (red > 0) {
            return new Light(AMBER, red + (red == 1 ? " village is in trouble" : " villages are in trouble"));
        }
        if (inAmber * 2 >= people) {
            return new Light(AMBER, "half or more of its people live in villages that are held back");
        }
        return new Light(GREEN, "its villages are doing well");
    }

    /** The time of day as a clock reading, with Minecraft's tick 0 being 6:00. */
    public static String clock(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000L);
        long minutes = (t * 60 / 1000 + 6 * 60) % (24 * 60);
        return String.format("%d:%02d", minutes / 60, minutes % 60);
    }

    /** The part of the day: dawn, day, dusk or night. */
    public static String period(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return t < 1000 || t >= 23000 ? "dawn" : t < 12000 ? "day" : t < 13000 ? "dusk" : "night";
    }

    /** A world as the dashboard shows it: its facts, its light and why, its numbers and its players. */
    public static Map<String, Object> row(Facts facts, List<Map<String, Object>> villages) {
        Light light = light(facts.simulated(), villages);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", facts.name());
        out.put("environment", facts.environment());
        out.put("day", facts.day());
        out.put("clock", clock(facts.timeOfDay()));
        out.put("period", period(facts.timeOfDay()));
        out.put("weather", facts.weather());
        out.put("difficulty", facts.difficulty());
        out.put("simulated", facts.simulated());
        out.put("light", light.colour());
        out.put("why", light.why());
        int green = 0;
        int amber = 0;
        int red = 0;
        long people = 0;
        for (Map<String, Object> v : villages) {
            switch (((Number) v.get("health")).intValue()) {
                case 2 -> red++;
                case 1 -> amber++;
                default -> green++;
            }
            people += ((Number) v.get("population")).longValue();
        }
        out.put("villages", villages.size());
        out.put("green", green);
        out.put("amber", amber);
        out.put("red", red);
        out.put("people", people);
        List<Map<String, Object>> players = new ArrayList<>();
        for (PlayerAt p : facts.players()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", p.name());
            row.put("village", p.villageId());
            row.put("villageName", p.villageName());
            players.add(row);
        }
        out.put("players", players);
        return out;
    }
}
