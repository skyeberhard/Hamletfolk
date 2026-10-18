package io.github.skyeberhard.hamletfolk.core;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * R4.63: a villager who thinks well of a player now and then gives them something their trade makes. Only a friendly player (standing 15 or more) who
 * has not recently taken from the stores is offered anything, at most once in {@link #COOLDOWN_DAYS} village days per player ({@link #HONOURED_COOLDOWN_DAYS}
 * if honoured), on a roll that is a third (a half if honoured) and fixed by the player, the villager and the day. A gift comes out of the village's own
 * surplus (the ledger pays for it, so nothing appears from nowhere), is a few items of the best finished thing the villager's category has to spare, and from a
 * master (level 4 or more) is named for its quality with the master's name in the lore. A village gives at most {@link #DAILY_CAP} gifts a day.
 * The Paper layer marks the item as taken from the stores, so it cannot be handed back for a reward.
 */
public final class Gifts {
    public static final int FRIENDLY = 15;
    public static final int COOLDOWN_DAYS = 8;
    public static final int HONOURED_COOLDOWN_DAYS = 4;
    public static final int DAILY_CAP = 3;

    private Gifts() {
    }

    /** What a villager gave: items of {@code material} (a Minecraft item name) and what to say. {@code name} and {@code lore} are null for a plain item. */
    public record Gift(String material, int count, ResourceType category, String name, String lore, String message) {
    }

    static int itemsFor(ResourceType category) {
        return switch (category) {
            case FOOD, FUEL -> 4;
            case WOOD, STONE -> 8;
            case METAL, GOODS -> 2;
            case TOOLS -> 1;
        };
    }

    private static int mix(UUID player, UUID villager, long day) {
        long h = player.getMostSignificantBits() * 31 + player.getLeastSignificantBits() * 13 + villager.getLeastSignificantBits() * 17 + day * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (int) Math.floorMod(h, 6L);
    }

    /**
     * The gift this villager gives this player today, taken from the village's stores and recorded, or empty if none. Changes the ledger and the
     * cooldown only when it returns a gift.
     */
    public static Optional<Gift> offer(Settlement s, Resident giver, UUID player, long day) {
        if (!giver.adult() || giver.occupation().produces() == null) {
            return Optional.empty();
        }
        int standing = s.reputationOf(player);
        if (standing < FRIENDLY || StoreDisplay.recentThief(s, player, day)) {
            return Optional.empty();
        }
        boolean honoured = Reputation.standing(standing) == Reputation.Standing.HONOURED;
        Long last = s.conditions().get("giftAt:" + player);
        if (last != null && day >= last && day - last < (honoured ? HONOURED_COOLDOWN_DAYS : COOLDOWN_DAYS)) { // (a day that went backwards is over)
            return Optional.empty();
        }
        if (mix(player, giver.id(), day) >= (honoured ? 3 : 2)) {
            return Optional.empty();
        }
        Long countDay = s.conditions().get("giftCountDay");
        long today = countDay != null && countDay == day ? s.conditions().getOrDefault("giftCount", 0L) : 0;
        if (today >= DAILY_CAP) {
            return Optional.empty();
        }
        ResourceType category = giver.occupation().produces();
        List<Commodity> kinds = Commodity.of(category);
        for (int i = kinds.size() - 1; i >= 0; i--) { // the most finished first
            Commodity kind = kinds.get(i);
            Optional<String> material = StoreDisplay.material(kind);
            if (material.isEmpty()) {
                continue;
            }
            int count = itemsFor(category);
            int units = StoreDisplay.unitsFor(material.get(), count);
            if (units <= 0 || s.ledger().get(kind) < units || SettlementSimulator.surplus(s, category) < units) {
                continue;
            }
            if (s.ledger().take(kind, units) < units) {
                continue;
            }
            s.conditions().put("giftAt:" + player, day);
            s.conditions().put("giftCountDay", day);
            s.conditions().put("giftCount", today + 1);
            String quality = Quality.label(giver.level());
            String item = material.get().replace('_', ' ');
            String message = giver.givenName() + " presses " + count + " x " + item + " into your hands. \"For a friend of "
                    + s.name() + ".\"";
            if (!quality.isEmpty()) {
                return Optional.of(new Gift(material.get(), count, category, quality + " " + titleCase(item), "Made by " + giver.fullName() + ", "
                        + TradeLevel.title(giver), message));
            }
            return Optional.of(new Gift(material.get(), count, category, null, null, message));
        }
        return Optional.empty();
    }

    private static String titleCase(String s) {
        StringBuilder out = new StringBuilder();
        for (String word : s.split(" ")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(word.isEmpty() ? word : word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1));
        }
        return out.toString();
    }
}
