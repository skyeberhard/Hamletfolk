package io.github.skyeberhard.hamletfolk.core;

import java.util.List;
import java.util.Optional;

/**
 * R4.64: a master craftsman sells the village's finished goods. A resident who makes tools or goods and has reached expert (level 4) or master offers, to
 * a player opening their trade window, the best finished thing the village has to spare of their category for emeralds: dearer the better the
 * hand (three times a unit's worth at level 4, four and a half at 5), and cheaper or dearer by the player's standing like the village's other prices
 * ({@link Reputation#priceFactor}). The stock is the village's own: a sale takes the units out of the ledger and the emeralds go to the treasury as far as it
 * has room, so nothing is made from nothing. A stall sells nothing the village cannot spare, and at most {@link #MAX_USES} at a time. Pure rules; the
 * Paper layer turns an {@link Offer} into a trade recipe and checks it again when the player buys.
 */
public final class Stall {
    public static final int MIN_LEVEL = TradeLevel.EXPERT;
    /** The most a village sells at its stalls in a day, however often the window is opened. */
    public static final int MAX_USES = 4;
    static final String SOLD_DAY = "stallDay";
    static final String SOLD_COUNT = "stallCount";

    private Stall() {
    }

    /**
     * What a stall offers.
     *
     * @param material the item (a Minecraft item name) and {@code count} of it
     * @param units    what the village's stores lose for one sale
     * @param emeralds the price for this player
     * @param maxUses  how many sales the stores can cover today
     * @param name     the item's name for its quality (e.g. "Fine Iron Pickaxe") and {@code lore} its maker line
     */
    public record Offer(String material, int count, int units, Commodity commodity, ResourceType category, int emeralds, int maxUses, String name, String lore) {
    }

    /** True if this resident can keep a stall at all. */
    public static boolean keeps(Resident r) {
        ResourceType made = r.occupation().produces();
        return r.adult() && r.level() >= MIN_LEVEL && (made == ResourceType.TOOLS || made == ResourceType.GOODS);
    }

    private static int soldToday(Settlement s, long day) {
        Long was = s.conditions().get(SOLD_DAY);
        return was != null && was == day ? (int) (long) s.conditions().getOrDefault(SOLD_COUNT, 0L) : 0;
    }

    /**
     * The offer this stall makes to a player with this standing on this village day, or empty if there is nothing to offer: nothing the village can
     * spare, the day's sales used up, or a treasury with no room for the payment ({@code treasuryRoom} emeralds).
     */
    public static Optional<Offer> offer(Settlement s, Resident keeper, int standing, long day, int treasuryRoom) {
        if (!keeps(keeper) || soldToday(s, day) >= MAX_USES || treasuryRoom <= 0) {
            return Optional.empty();
        }
        ResourceType category = keeper.occupation().produces();
        List<Commodity> kinds = Commodity.of(category);
        for (int i = kinds.size() - 1; i >= 0; i--) { // the most finished first
            Commodity kind = kinds.get(i);
            Optional<String> material = StoreDisplay.material(kind);
            if (material.isEmpty()) {
                continue;
            }
            int count = Gifts.itemsFor(category);
            int units = StoreDisplay.unitsFor(material.get(), count);
            if (units <= 0) {
                continue;
            }
            int uses = Math.min(MAX_USES - soldToday(s, day), Math.min(s.ledger().get(kind), SettlementSimulator.surplus(s, category)) / units);
            if (uses <= 0) {
                continue;
            }
            double worth = (double) units / SettlementSimulator.unitsPerEmerald(category);
            double multiplier = 1.5 * (keeper.level() - 2); // 3 at expert, 4.5 at master
            int price = Math.max(1, (int) Math.ceil(worth * multiplier * Reputation.priceFactor(standing)));
            String quality = Quality.label(keeper.level());
            String item = titleCase(material.get().replace('_', ' '));
            uses = Math.min(uses, Math.max(0, treasuryRoom / price)); // never a sale whose payment the treasury could not bank
            if (uses <= 0) {
                return Optional.empty();
            }
            return Optional.of(new Offer(material.get(), count, units, kind, category, price, uses, quality + " " + item,
                    "Made by " + keeper.fullName() + ", " + TradeLevel.title(keeper)));
        }
        return Optional.empty();
    }

    /** Whether the stores still cover a sale of this offer right now (checked again when the player buys). */
    public static boolean covers(Settlement s, Resident keeper, String material, int count) {
        if (!keeps(keeper)) {
            return false;
        }
        int units = StoreDisplay.unitsFor(material, count);
        Optional<Commodity> kind = ResourceMapper.commodity(material);
        ResourceType category = keeper.occupation().produces();
        return units > 0 && kind.isPresent() && kind.get().category() == category
                && s.ledger().get(kind.get()) >= units && SettlementSimulator.surplus(s, category) >= units;
    }

    /**
     * A sale made: the units leave the stores and the emeralds paid go to the treasury as far as {@code treasuryRoom} allows. Returns the emeralds
     * banked, or -1 if the stores could not cover it (the Paper layer refuses the trade before this, so that is only a race).
     */
    public static int sold(Settlement s, Resident keeper, String material, int count, int emeraldsPaid, int treasuryRoom, long day) {
        if (!covers(s, keeper, material, count)) {
            return -1;
        }
        int units = StoreDisplay.unitsFor(material, count);
        Commodity kind = ResourceMapper.commodity(material).orElseThrow();
        if (s.ledger().take(kind, units) < units) {
            return -1;
        }
        int banked = Math.max(0, Math.min(emeraldsPaid, treasuryRoom));
        s.ledger().addTreasury(banked);
        s.conditions().put(SOLD_COUNT, (long) soldToday(s, day) + 1);
        s.conditions().put(SOLD_DAY, day);
        return banked;
    }

    private static String titleCase(String text) {
        StringBuilder out = new StringBuilder();
        for (String word : text.split(" ")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(word.isEmpty() ? word : word.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + word.substring(1));
        }
        return out.toString();
    }
}
