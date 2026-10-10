package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;
import java.util.Map;

/**
 * R8.13: what the land actually offers within reach of a village, counted when the village is surveyed: the animals
 * that are there and the sugar cane, sand and water. Kept in the settlement's conditions (so it needs no new save
 * format) and only ever raised, so a count taken while some chunks were unloaded cannot close a trade that was open.
 */
public final class LandCounts {
    /** What is counted. */
    public enum Feature {
        SHEEP, CATTLE, PIGS, CHICKENS, HORSES, BEES, SUGAR_CANE, SAND, WATER,
        /** R8.15: exposed gravel (flint) and exposed stone, counted as surface blocks. */
        GRAVEL, STONE;

        /** The condition this count is kept under (built once: these are read in the simulation's hot paths). */
        private final String key = "landCount:" + name();

        public String label() {
            return name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }

    private static final String SURVEYED = "landSurveyed";

    private LandCounts() {
    }

    /** How many of a feature the survey found (0 if never surveyed). */
    public static int get(Settlement settlement, Feature feature) {
        return (int) Math.min(Integer.MAX_VALUE, settlement.conditions().getOrDefault(feature.key, 0L));
    }

    /** True once a survey has been recorded. */
    public static boolean surveyed(Settlement settlement) {
        return settlement.conditions().containsKey(SURVEYED);
    }

    /** The day the land was last surveyed, or -1. */
    public static long surveyedDay(Settlement settlement) {
        return settlement.conditions().getOrDefault(SURVEYED, -1L);
    }

    /** Records a survey: each count is kept if it is higher than what was found before. */
    public static void record(Settlement settlement, Map<Feature, Integer> found, long day) {
        for (Feature feature : Feature.values()) {
            long now = Math.max(0, found.getOrDefault(feature, 0));
            if (now > settlement.conditions().getOrDefault(feature.key, 0L)) {
                settlement.conditions().put(feature.key, now);
            }
        }
        settlement.conditions().put(SURVEYED, day);
    }

    /** e.g. "4 sheep, 7 cattle and 52 water". Empty text if nothing was found. */
    public static String describe(Settlement settlement) {
        StringBuilder out = new StringBuilder();
        for (Feature feature : Feature.values()) {
            int n = get(settlement, feature);
            if (n > 0) {
                out.append(out.length() == 0 ? "" : ", ").append(n).append(' ').append(feature.label());
            }
        }
        return out.toString();
    }
}
