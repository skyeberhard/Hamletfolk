package io.github.skyeberhard.hamletfolk.core;

import java.util.List;
import java.util.Random;

/** Deterministic names for residents and settlements. */
public final class NameGenerator {
    private static final List<String> FEMININE = List.of(
            "Ada", "Anya", "Brenna", "Clara", "Dara", "Edda", "Elin", "Esme", "Greta", "Ines",
            "Juno", "Kaia", "Leda", "Linnea", "Mabel", "Mira", "Nell", "Oda", "Priya", "Rhea",
            "Rowan", "Sable", "Sunniva", "Tove", "Ulla", "Vesna", "Wren", "Yara");
    private static final List<String> MASCULINE = List.of(
            "Alder", "Amos", "Bram", "Brant", "Cal", "Cormac", "Corin", "Dunstan", "Elias", "Ewan",
            "Fen", "Finn", "Gideon", "Hale", "Harold", "Hugo", "Ivo", "Jonas", "Kestrel", "Lars",
            "Marek", "Nico", "Orrin", "Osric", "Perrin", "Pim", "Quill", "Silas", "Tam", "Tobias", "Wilf");
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
        return random.nextBoolean() ? Gender.FEMALE : Gender.MALE;
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
