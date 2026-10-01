package io.github.skyeberhard.hamletfolk.core;

import java.util.List;
import java.util.Random;

/** Deterministic names for residents and settlements. */
public final class NameGenerator {
    private static final List<String> FEMININE = List.of(
            "Ada", "Anya", "Brenna", "Clara", "Dara", "Edda", "Elin", "Esme", "Greta", "Ines",
            "Juno", "Kaia", "Leda", "Linnea", "Mabel", "Mira", "Nell", "Oda", "Priya", "Rhea",
            "Sunniva", "Tove", "Ulla", "Vesna", "Yara");
    private static final List<String> MASCULINE = List.of(
            "Amos", "Bram", "Brant", "Cal", "Cormac", "Corin", "Dunstan", "Elias", "Ewan", "Finn",
            "Gideon", "Hale", "Harold", "Hugo", "Ivo", "Jonas", "Lars", "Marek", "Nico", "Orrin",
            "Osric", "Perrin", "Silas", "Tobias", "Wilf");
    private static final List<String> NEUTRAL = List.of(
            "Alder", "Fen", "Kestrel", "Pim", "Quill", "Rowan", "Sable", "Tam", "Wren");
    /** R4.14: share of residents, out of 100, who are female and male; the rest are nonbinary. */
    private static final int FEMALE_PERCENT = 47;
    private static final int MALE_PERCENT = 47;
    private static final List<String> FAMILY = List.of(
            "Ashdown", "Barrow", "Birch", "Brightwater", "Carter", "Cobb", "Cooper", "Dunmore",
            "Fairweather", "Fletcher", "Flint", "Greenfield", "Hayward", "Hollis", "Ironside",
            "Kettle", "Larkin", "Marsh", "Mercer", "Millbrook", "Oakes", "Pike", "Quarry",
            "Reed", "Rook", "Sawyer", "Stone", "Thatcher", "Underhill", "Wick");
    private static final List<String> PLACE_START = List.of(
            "Oak", "Ash", "Stone", "River", "Mill", "Elder", "Thorn", "Wheat", "Iron", "Fox",
            "Willow", "Clay", "Amber", "Red", "Frost", "Moss", "Hollow", "Bright");
    private static final List<String> PLACE_END = List.of(
            "ridge", "ford", "brook", "field", "vale", "stead", "wick", "hollow", "haven", "mere",
            "crest", "wood", "bury", "gate", "moor");

    private NameGenerator() {
    }

    /** Draws a gender for a new resident. */
    public static Gender gender(Random random) {
        int roll = random.nextInt(100);
        return roll < FEMALE_PERCENT ? Gender.FEMALE
                : roll < FEMALE_PERCENT + MALE_PERCENT ? Gender.MALE : Gender.NONBINARY;
    }

    /** A given name that agrees with the gender. */
    public static String givenName(Gender gender, Random random) {
        return pick(listFor(gender), random);
    }

    /** The gender a given name belongs to, or null for a name that isn't on any list (e.g. an admin's rename). */
    public static Gender genderOf(String givenName) {
        for (Gender gender : Gender.values()) {
            if (listFor(gender).contains(givenName)) {
                return gender;
            }
        }
        return null;
    }

    private static List<String> listFor(Gender gender) {
        return switch (gender) {
            case FEMALE -> FEMININE;
            case MALE -> MASCULINE;
            case NONBINARY -> NEUTRAL;
        };
    }

    public static String familyName(Random random) {
        return pick(FAMILY, random);
    }

    public static String settlementName(Random random) {
        return pick(PLACE_START, random) + pick(PLACE_END, random);
    }

    private static String pick(List<String> options, Random random) {
        return options.get(random.nextInt(options.size()));
    }
}
