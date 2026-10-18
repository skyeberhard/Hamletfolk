package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * R4.60: what a master is remembered for. The first time a resident becomes a master the history names a masterpiece, a piece of flavour chosen from
 * their trade's list by their id (so it is always the same one, and nothing is saved); the notice board lists the village's living masters with theirs,
 * and when a master dies of old age the history says how long they worked and whom they taught.
 */
public final class Masterpieces {
    private Masterpieces() {
    }

    private static List<String> pieces(Occupation trade) {
        return switch (trade) {
            case FARMER -> List.of("a harvest loaf the whole village shared", "a field that never lay fallow", "a barley that outlasted a bad winter");
            case FISHERMAN -> List.of("a catch that fed the village for three days", "nets that were mended by every child", "a cold-water trout stew");
            case BUTCHER -> List.of("a feast-day roast", "a smokehouse ham aged to a legend");
            case SHEPHERD -> List.of("a flock that grew from two ewes", "a coloured wool cloak");
            case LEATHERWORKER -> List.of("a saddle that outlived its horse", "boots that never leaked");
            case FLETCHER -> List.of("an arrow that flew true across the river", "a bow that nobody else can string");
            case MASON, MINER -> List.of("a wall that has never needed mending", "a shaft cut straight as a plumb line", "a keystone nobody could shift");
            case LUMBERJACK -> List.of("an oak felled where it was meant to fall", "a beam as long as the hall");
            case ARMORER, WEAPONSMITH, TOOLSMITH -> List.of("a blade that sang when struck", "a pick that never dulled", "an anvil-bright helm");
            case CARTOGRAPHER -> List.of("a map of the whole valley", "a chart that found the lost well");
            case CLERIC -> List.of("a blessing the village still repeats", "a brew that cured a fever");
            case LIBRARIAN -> List.of("a catalogue of every book in the village", "a tale told so well it is told still");
            case BEEKEEPER -> List.of("a honey that tasted of the whole summer", "a hive that swarmed and stayed");
            case HORSE_TRAINER -> List.of("a foal that would not be broken and then was", "a mare that always came home");
            case GLASSBLOWER -> List.of("a window the whole village stopped to look at", "a flask as thin as a leaf");
            case GUARD -> List.of("a night the walls held alone", "a watch that was never once caught sleeping");
            case MERCHANT -> List.of("a bargain both sides called fair", "a ledger without a single error");
            default -> List.of("a lifetime of steady work");
        };
    }

    /** The masterpiece of a resident, the same each time. */
    public static String of(Resident r) {
        List<String> list = pieces(r.occupation());
        return list.get(Math.floorMod(r.id().hashCode(), list.size()));
    }

    /** The first time someone is a master: the line for the history. */
    static String promotion(Resident r) {
        return r.fullName() + " is now a " + TradeLevel.title(r) + ", and made " + of(r) + ".";
    }

    /** The living masters, with their masterpieces, for the notice board. */
    public static List<String> living(Settlement s) {
        List<String> out = new ArrayList<>();
        for (Resident r : s.residents()) {
            if (r.adult() && r.level() >= TradeLevel.MASTER && (r.occupation().produces() != null || r.occupation() == Occupation.GUARD)) {
                out.add(r.fullName() + ", " + TradeLevel.title(r) + ": " + of(r));
            }
        }
        return out;
    }

    /** What the history says when a master dies, or null if they were not one. */
    static String legacy(Settlement s, Resident master, long day) {
        if (master.level() < TradeLevel.MASTER) {
            return null;
        }
        List<String> pupils = new ArrayList<>();
        for (Resident r : s.residents()) {
            if (r.adult() && r.occupation() == master.occupation() && r.id() != master.id()
                    && (master.id().equals(r.parentA()) || master.id().equals(r.parentB()) || s.hasCondition(Apprenticeships.MARKER + r.id()))) {
                pupils.add(r.fullName());
            }
        }
        String taught = pupils.isEmpty() ? " and left no pupil behind"
                : ", and trained " + String.join(" and ", pupils.subList(0, Math.min(2, pupils.size())));
        return "The " + master.occupation().title().toLowerCase(Locale.ROOT) + "'s trade in " + s.name() + " mourns " + master.fullName()
                + ", a " + TradeLevel.title(master) + " remembered for " + of(master) + taught + ".";
    }

    // ----- R4.61: heirlooms, the masterpiece as an item the village keeps on show -----

    static final String HEIRLOOM = "heirloom:";
    /** The most heirlooms a village keeps on show; the oldest make way. */
    public static final int MAX_HEIRLOOMS = 6;

    /** One masterpiece kept by the village. */
    public record Heirloom(String material, String title, String maker, String trade, long day) {
    }

    /** The item that stands for a trade's masterpiece (a Minecraft item name). */
    static String materialFor(Occupation trade) {
        return switch (trade) {
            case FARMER -> "golden_carrot";
            case FISHERMAN -> "tropical_fish";
            case BUTCHER -> "cooked_porkchop";
            case SHEPHERD -> "magenta_wool";
            case LEATHERWORKER -> "saddle";
            case FLETCHER -> "bow";
            case MASON, MINER -> "diamond_pickaxe";
            case LUMBERJACK -> "diamond_axe";
            case ARMORER -> "iron_chestplate";
            case WEAPONSMITH -> "iron_sword";
            case TOOLSMITH -> "diamond_shovel";
            case CARTOGRAPHER -> "filled_map";
            case CLERIC -> "potion";
            case LIBRARIAN -> "enchanted_book";
            case BEEKEEPER -> "honey_bottle";
            case HORSE_TRAINER -> "golden_horse_armor";
            case GLASSBLOWER -> "glass_bottle";
            case GUARD -> "shield";
            case MERCHANT -> "emerald";
            default -> "paper";
        };
    }

    /** Keeps a new master's masterpiece: the key carries the trade, the maker's id and name, and the value is the day. */
    static void keep(Settlement s, Resident r, long day) {
        s.conditions().put(HEIRLOOM + r.occupation().name() + ":" + r.id() + ":" + r.fullName(), day);
        List<String> keys = new ArrayList<>();
        for (String key : s.conditions().keySet()) {
            if (key.startsWith(HEIRLOOM)) {
                keys.add(key);
            }
        }
        keys.sort(java.util.Comparator.comparingLong((String k) -> s.conditions().get(k)).thenComparing(k -> k));
        while (keys.size() > MAX_HEIRLOOMS) {
            s.conditions().remove(keys.remove(0));
        }
    }

    /** The masterpieces the village keeps, newest first. */
    public static List<Heirloom> heirlooms(Settlement s) {
        List<Heirloom> out = new ArrayList<>();
        for (var entry : s.conditions().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(HEIRLOOM)) {
                continue;
            }
            String[] parts = key.substring(HEIRLOOM.length()).split(":", 3);
            if (parts.length < 3) {
                continue;
            }
            try {
                Occupation trade = Occupation.valueOf(parts[0]);
                UUID id = UUID.fromString(parts[1]);
                List<String> list = pieces(trade);
                String piece = list.get(Math.floorMod(id.hashCode(), list.size()));
                out.add(new Heirloom(materialFor(trade), piece.substring(0, 1).toUpperCase(Locale.ROOT) + piece.substring(1), parts[2],
                        trade.title().toLowerCase(Locale.ROOT), entry.getValue()));
            } catch (IllegalArgumentException e) {
                // a damaged key: ignore it
            }
        }
        out.sort(java.util.Comparator.comparingLong(Heirloom::day).reversed().thenComparing(Heirloom::maker));
        return out;
    }
}
