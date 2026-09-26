package io.github.skyeberhard.societies.core;

import java.util.List;
import java.util.Random;

/** Deterministic names for residents and settlements. */
public final class NameGenerator {
    private static final List<String> GIVEN = List.of(
            "Ada", "Alder", "Amos", "Anya", "Bram", "Brenna", "Cal", "Clara", "Corin", "Dara",
            "Edda", "Elias", "Elin", "Esme", "Fen", "Finn", "Greta", "Gideon", "Hale", "Harold",
            "Ines", "Ivo", "Jonas", "Juno", "Kaia", "Kestrel", "Lars", "Leda", "Linnea", "Mabel",
            "Marek", "Mira", "Nell", "Nico", "Oda", "Orrin", "Pim", "Priya", "Quill", "Rhea",
            "Rowan", "Sable", "Silas", "Sunniva", "Tam", "Tove", "Ulla", "Vesna", "Wren", "Yara");
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

    public static String givenName(Random random) {
        return pick(GIVEN, random);
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
