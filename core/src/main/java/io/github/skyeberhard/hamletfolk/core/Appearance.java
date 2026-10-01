package io.github.skyeberhard.hamletfolk.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a resource pack or client mod can see of a resident (R4.16): a few plain-text tags the
 * Minecraft layer stores on the villager, and an optional vanilla villager type that encodes
 * gender and life stage so a plain resource pack can reskin villagers without a client mod.
 * The values are documented in docs/APPEARANCE.md and are a stable contract: changing one
 * breaks packs built against it.
 */
public final class Appearance {
    public static final String GENDER = "gender";
    public static final String OCCUPATION = "occupation";
    public static final String LIFE_STAGE = "life_stage";

    /** Every vanilla villager type, by its lowercase key. */
    public static final List<String> VANILLA_TYPES =
            List.of("desert", "jungle", "plains", "savanna", "snow", "swamp", "taiga");

    /** The types the mapping never uses, which a pack can leave as they are. */
    public static final List<String> UNUSED_TYPES = List.of("jungle", "savanna", "swamp");

    private Appearance() {
    }

    /** The tags to store on a resident's villager: lowercase gender, occupation and life stage names. */
    public static Map<String, String> tags(Resident resident, long day) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put(GENDER, resident.gender().name().toLowerCase(Locale.ROOT));
        tags.put(OCCUPATION, resident.occupation().name().toLowerCase(Locale.ROOT));
        tags.put(LIFE_STAGE, resident.stage(day).name().toLowerCase(Locale.ROOT));
        return tags;
    }

    /**
     * The vanilla villager type that stands for a gender and life stage. Children share the
     * adult's type, because the game already draws a child with the baby model; elders get their own.
     * <pre>
     *            adult / child   elder
     * female     plains          snow
     * male       desert          taiga
     * </pre>
     */
    public static String villagerType(Gender gender, LifeStage stage) {
        boolean elder = stage == LifeStage.ELDER;
        return switch (gender) {
            case FEMALE -> elder ? "snow" : "plains";
            case MALE -> elder ? "taiga" : "desert";
        };
    }
}
