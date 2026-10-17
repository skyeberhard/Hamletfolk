package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * R4.55: how the stores are pictured. The ledger is the truth; a barrel in the village's building shows one category of it as items.
 * Pure rules: which building shows which category, which item stands for which commodity, how many are shown, and what taking
 * some costs the ledger. The Paper layer places the barrel and fills it.
 */
public final class StoreDisplay {
    /** The most items of one commodity shown (two stacks). */
    public static final int CAP = 128;
    /** The most reputation a theft costs. */
    public static final int MAX_THEFT_LOSS = 30;
    /** What breaking a store's barrel costs. */
    public static final int VANDALISM_LOSS = 5;

    private static final Map<Commodity, String> ITEM = new EnumMap<>(Commodity.class);
    private static final Map<ResourceType, List<BuildingType>> HOMES = new EnumMap<>(ResourceType.class);

    static {
        ITEM.put(Commodity.PRODUCE, "carrot");
        ITEM.put(Commodity.FISH, "cod");
        ITEM.put(Commodity.MEAT, "beef");
        ITEM.put(Commodity.COOKED, "cooked_beef");
        ITEM.put(Commodity.GRAIN, "wheat");
        ITEM.put(Commodity.BREAD, "bread");
        ITEM.put(Commodity.PLANKS, "oak_planks");
        ITEM.put(Commodity.LOGS, "oak_log");
        ITEM.put(Commodity.COBBLESTONE, "cobblestone");
        ITEM.put(Commodity.GRAVEL, "gravel");
        ITEM.put(Commodity.SAND, "sand");
        ITEM.put(Commodity.CLAY, "clay_ball");
        ITEM.put(Commodity.STONE_BLOCKS, "stone");
        ITEM.put(Commodity.BRICKS, "bricks");
        ITEM.put(Commodity.RAW_COPPER, "raw_copper");
        ITEM.put(Commodity.COPPER, "copper_ingot");
        ITEM.put(Commodity.RAW_GOLD, "raw_gold");
        ITEM.put(Commodity.GOLD, "gold_ingot");
        ITEM.put(Commodity.REDSTONE, "redstone");
        ITEM.put(Commodity.LAPIS, "lapis_lazuli");
        ITEM.put(Commodity.RAW_IRON, "raw_iron");
        ITEM.put(Commodity.IRON, "iron_ingot");
        ITEM.put(Commodity.DIAMOND, "diamond");
        ITEM.put(Commodity.CHARCOAL, "charcoal");
        ITEM.put(Commodity.COAL, "coal");
        ITEM.put(Commodity.STONE_TOOLS, "stone_pickaxe");
        ITEM.put(Commodity.IRON_TOOLS, "iron_pickaxe");
        ITEM.put(Commodity.DIAMOND_TOOLS, "diamond_pickaxe");
        ITEM.put(Commodity.WOOL, "white_wool");
        ITEM.put(Commodity.STRING, "string");
        ITEM.put(Commodity.LEATHER, "leather");
        ITEM.put(Commodity.PAPER, "paper");
        ITEM.put(Commodity.GLASS, "glass");
        ITEM.put(Commodity.BOOKS, "book");
        // (WARES is a bucket of anything: nothing stands for it)

        HOMES.put(ResourceType.FOOD, List.of(BuildingType.GRANARY, BuildingType.FARM, BuildingType.SMOKEHOUSE));
        HOMES.put(ResourceType.WOOD, List.of(BuildingType.SAWMILL));
        HOMES.put(ResourceType.STONE, List.of(BuildingType.MASONS_YARD, BuildingType.MINE));
        HOMES.put(ResourceType.METAL, List.of(BuildingType.MINE, BuildingType.FORGE));
        HOMES.put(ResourceType.FUEL, List.of(BuildingType.SMITHY, BuildingType.FORGE));
        HOMES.put(ResourceType.TOOLS, List.of(BuildingType.SMITHY));
        HOMES.put(ResourceType.GOODS, List.of(BuildingType.SHOP, BuildingType.TRADING_POST));
    }

    /** How many village days a thief is not taken as a giver: stock taken from the stores cannot be handed back, changed, for a reward. */
    public static final int THIEF_DAYS = 10;

    /** Remembers that a player took from the stores on this village day. */
    public static void noteTheft(Settlement settlement, java.util.UUID player, long day) {
        settlement.conditions().put("thief:" + player, day);
    }

    /** True if the player took from the village's stores in the last {@link #THIEF_DAYS} days. */
    public static boolean recentThief(Settlement settlement, java.util.UUID player, long day) {
        Long was = settlement.conditions().get("thief:" + player);
        return was != null && day - was < THIEF_DAYS;
    }

    private StoreDisplay() {
    }

    /** One stack on show. */
    public record Shown(Commodity commodity, String material, int count) {
    }

    /** The item that stands for a commodity, if it has one. */
    public static Optional<String> material(Commodity commodity) {
        return Optional.ofNullable(ITEM.get(commodity));
    }

    /** The commodity an item shown in a store is (the same as what a donation of it would be), if it is one. */
    public static Optional<Commodity> commodityOf(String material) {
        return ResourceMapper.commodity(material).filter(c -> material(c).isPresent());
    }

    /** The kinds of building a category may be drawn in, best first. */
    public static List<BuildingType> homes(ResourceType category) {
        return HOMES.getOrDefault(category, List.of());
    }

    /** The building the category is drawn at: the first registered one of its best kind. */
    public static Optional<Building> site(Settlement settlement, ResourceType category) {
        for (BuildingType type : homes(category)) {
            Optional<Building> found = settlement.buildings().stream().filter(b -> b.type() == type).findFirst();
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /** How many items of a commodity the ledger's units are shown as (never more than {@link #CAP}). */
    public static int itemsShown(Commodity commodity, int units) {
        String material = ITEM.get(commodity);
        if (material == null || units <= 0) {
            return 0;
        }
        return ResourceMapper.value(material).map(v -> Math.min(CAP, v.itemsThatFit(units))).orElse(0);
    }

    /** What the barrel for a category shows, from the ledger: stacks in commodity order. */
    public static List<Shown> contents(Ledger ledger, ResourceType category) {
        List<Shown> out = new ArrayList<>();
        for (Commodity c : Commodity.of(category)) {
            String material = ITEM.get(c);
            int count = itemsShown(c, ledger.get(c));
            for (; count > 0 && material != null; count -= 64) {
                out.add(new Shown(c, material, Math.min(64, count)));
            }
        }
        return out;
    }

    /** What taking {@code count} of an item costs the ledger, in units: rounded up, so splitting a theft gains nothing. */
    public static int unitsFor(String material, int count) {
        return ResourceMapper.value(material).map(v -> (int) Math.min(Integer.MAX_VALUE,
                ((long) Math.max(0, count) * v.numerator() + v.denominator() - 1) / v.denominator())).orElse(0);
    }

    /** What a theft of {@code units} of a category costs the thief's reputation: a point per emerald's worth, at least 1, at most 30. */
    public static int theftLoss(ResourceType category, int units) {
        return Math.max(1, Math.min(MAX_THEFT_LOSS, units / SettlementSimulator.unitsPerEmerald(category)));
    }

    /**
     * A player takes {@code count} of {@code material} from a store. Takes the units from the ledger if it still holds them and returns
     * them; 0 if the item is not on show or the ledger holds less than that now (the picture is a few seconds old).
     */
    public static int take(Settlement settlement, String material, int count) {
        Optional<Commodity> commodity = commodityOf(material);
        if (commodity.isEmpty() || count <= 0) {
            return 0;
        }
        int units = unitsFor(material, count);
        if (units == 0 || settlement.ledger().get(commodity.get()) < units) {
            return 0;
        }
        return settlement.ledger().take(commodity.get(), units);
    }
}
