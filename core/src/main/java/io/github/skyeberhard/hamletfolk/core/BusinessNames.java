package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * R2.10: a name for each business, so a hanging sign reads "[Shop]" and under it "Mercer's Wares". The name is worked out from the village and
 * the building's place, so the same building always has the same name and nothing needs saving; a village's two smithies are not given the
 * same name unless the dice fall that way. A house has none (it is known by its family), nor does the square or a work on the streets.
 */
public final class BusinessNames {
    private BusinessNames() {
    }

    /** "{F}" becomes a family name, "{V}" the village's name. Kept short: a sign line is a dozen letters or so. */
    private static List<String> templates(BuildingType type) {
        return switch (type) {
            case SHOP -> List.of("{F}'s Wares", "{F} & Daughter", "The Open Door", "{F}'s Goods", "The Fair Trade", "{F} Provisions");
            case SMITHY -> List.of("{F}'s Forge", "The Anvil", "{F} Ironworks", "Hammer & Tongs", "The Hot Iron");
            case MINE -> List.of("{F}'s Delve", "The {F} Seam", "Deep Pick", "{F} Quarry", "Lantern Shaft");
            case FARM -> List.of("{F} Farm", "{F}'s Acres", "Goldfield", "Green Furrow", "{F} Holding");
            case TREASURY -> List.of("Counting House", "The Strongbox", "{V} Treasury", "The Coffer");
            case GUARD_POST -> List.of("The Watch", "Guard House", "The Lookout", "Shield Post");
            case GRANARY -> List.of("{V} Granary", "The Full Barn", "Grain Hall", "The Storehouse");
            case SAWMILL -> List.of("{F}'s Mill", "Sawdust Row", "The Whetstone Mill", "{F} Timber");
            case FORGE -> List.of("The Deep Forge", "{F} Foundry", "Ember Works", "The Great Forge");
            case LIBRARY -> List.of("The Open Book", "Quiet Pages", "The Reading Room", "Inkwell Hall");
            case HARBOUR -> List.of("{F}'s Landing", "Net & Hook", "The Wharf", "{F} Catch");
            case PENS -> List.of("{F} Fold", "Woolgate", "The Shearing Pen", "{F}'s Flock");
            case SMOKEHOUSE -> List.of("The Smokehouse", "{F} Cured Meats", "Hickory Hut", "The Hanging Hams");
            case TANNERY -> List.of("{F} Tannery", "The Hide Yard", "Oak & Leather", "{F}'s Leather");
            case STABLE -> List.of("{F} Stables", "The Hitching Post", "Hoofbeat Yard", "{F}'s Horses");
            case APIARY -> List.of("Honeycomb", "The Busy Bee", "{F} Apiary", "Sweetwax");
            case MAP_ROOM -> List.of("The Wayfarer", "Compass & Chart", "The Map Room", "{F}'s Charts");
            case GLASSWORKS -> List.of("Clearglass", "{F} Glassworks", "The Bright Pane", "Sandfire");
            case TRADING_POST -> List.of("The Market Cross", "{V} Exchange", "Trader's Rest", "The Barter Hall");
            case BOWYER -> List.of("Straight Arrow", "{F}'s Bows", "The Fletcher's", "Feather & String");
            case MASONS_YARD -> List.of("{F} Masonry", "Stone & Mortar", "The Mason's Yard", "{F}'s Stoneworks");
            case ARMOURY -> List.of("The Armoury", "{F} Arms", "Shield & Spear", "The Iron Wall");
            case CHAPEL -> List.of("The Quiet Chapel", "Hearthlight Chapel", "{V} Chapel", "The Stillness");
            default -> List.of();
        };
    }

    /** The business's name, or empty for a building that has none (a house, the square, a work on the streets, a part of a wall). */
    public static Optional<String> of(Settlement settlement, Building building) {
        List<String> options = templates(building.type());
        if (options.isEmpty()) {
            return Optional.empty();
        }
        long seed = settlement.id().getMostSignificantBits() * 31 + settlement.id().getLeastSignificantBits() * 17
                + building.x() * 73_856_093L + building.z() * 19_349_663L + building.type().ordinal();
        Random random = new Random(seed);
        String template = options.get(random.nextInt(options.size()));
        String name = template.replace("{F}", NameGenerator.familyName(random)).replace("{V}", settlement.name());
        return Optional.of(name);
    }

    /**
     * A name split over at most {@code maxLines} sign lines of at most {@code width} letters, at spaces; a word longer than a line is cut to
     * fit, and what does not fit in the lines is left off.
     */
    public static List<String> lines(String name, int width, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : name.split(" ")) {
            String w = word.length() > width ? word.substring(0, width) : word;
            if (line.length() > 0 && line.length() + 1 + w.length() > width) {
                out.add(line.toString());
                line = new StringBuilder();
            }
            line.append(line.length() == 0 ? "" : " ").append(w);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out.size() > maxLines ? new ArrayList<>(out.subList(0, maxLines)) : out;
    }
}
