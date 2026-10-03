package io.github.skyeberhard.hamletfolk.core;

import java.util.Optional;

/**
 * R3.1: how villager trade prices follow a settlement's stores. A resource the village is short of
 * costs more to buy from a villager and earns more when sold to one; a resource it has plenty of
 * costs less and earns less. Pure arithmetic on the ledger, so it is tested here; the Minecraft
 * layer only applies the result to a villager's trades.
 */
public final class PriceModel {
    /** The dearest a short resource gets: half as much again at an empty store. */
    static final double SHORT_MULTIPLIER = 1.5;
    /** The cheapest a plentiful resource gets: three quarters, from four times what the village wants. */
    static final double PLENTY_MULTIPLIER = 0.75;
    /** Cover (stock per wanted level) from which a surplus starts to cut prices, and where the cut stops. */
    static final double PLENTY_FROM = 2.0;
    static final double PLENTY_TO = 4.0;

    private PriceModel() {
    }

    /**
     * The price multiplier for a resource: {@link #SHORT_MULTIPLIER} at an empty store, falling
     * steadily to 1 when the stores reach what the village wants, staying at 1 up to twice that, then
     * falling steadily to {@link #PLENTY_MULTIPLIER} at four times it.
     */
    public static double multiplier(Settlement settlement, ResourceType type) {
        int population = settlement.population();
        if (population == 0) {
            return 1.0;
        }
        int wanted = population * (type == ResourceType.FOOD
                ? SettlementSimulator.FOOD_WANTED_PER_HEAD : SettlementSimulator.STOCK_WANTED_PER_HEAD);
        double cover = (double) settlement.ledger().get(type) / wanted;
        if (cover < 1.0) {
            return SHORT_MULTIPLIER - (SHORT_MULTIPLIER - 1.0) * Math.max(0.0, cover);
        }
        if (cover <= PLENTY_FROM) {
            return 1.0;
        }
        double over = Math.min(1.0, (cover - PLENTY_FROM) / (PLENTY_TO - PLENTY_FROM));
        return 1.0 - (1.0 - PLENTY_MULTIPLIER) * over;
    }

    /**
     * What the player pays for something the village sells: the base price times the multiplier, at
     * least 1. Rounded towards the base price, so a price never moves by more than the multiplier
     * says: a cheap item moves in whole emeralds or not at all (1 emerald never changes).
     */
    static int adjustedCost(int base, double multiplier) {
        double exact = base * multiplier;
        int rounded = multiplier >= 1.0 ? (int) Math.floor(exact + 1e-9) : (int) Math.ceil(exact - 1e-9);
        return Math.max(1, rounded);
    }

    /**
     * How much of something the player must hand over to be paid: fewer when the village is short of
     * it, more when it is plentiful. Rounded towards the base amount, like {@link #adjustedCost}.
     */
    static int adjustedAmount(int base, double multiplier) {
        double exact = base / multiplier;
        int rounded = multiplier >= 1.0 ? (int) Math.ceil(exact - 1e-9) : (int) Math.floor(exact + 1e-9);
        return Math.max(1, rounded);
    }

    /** Goods have no wanted level or demand to follow (R3.9 only just gave them a sink), so their prices are left alone. */
    private static Optional<ResourceType> priced(String material) {
        return ResourceMapper.classify(material).filter(type -> type != ResourceType.GOODS);
    }

    /**
     * How much to change a trade's price by, for the first item the player gives (the "special
     * price" a villager adds to or takes off it), or 0 if the trade is not about a resource.
     * <ul>
     * <li>A villager <b>selling</b> a resource (the player pays emeralds, gets the resource): the
     *     emerald cost is scaled by that resource's multiplier.</li>
     * <li>A villager <b>buying</b> a resource (the player gives it, gets emeralds): the amount asked for
     *     is scaled the other way, so a short resource needs fewer of them to pay the same.</li>
     * </ul>
     *
     * @param resultMaterial   what the player receives, e.g. {@code "bread"}
     * @param costMaterial     the first thing the player gives
     * @param costAmount       how many of it the trade asks for at the base price
     */
    public static int specialPriceDelta(Settlement settlement, String resultMaterial, String costMaterial, int costAmount) {
        return specialPriceDelta(settlement, resultMaterial, costMaterial, costAmount, 0);
    }

    /**
     * As above, for a player the settlement regards at {@code reputation} (R3.4): up to a tenth off
     * what they pay and a tenth better when they sell, up to a tenth more and worse at the other end.
     * Rounded towards the base price like the rest, so a cheap trade may not move at all.
     */
    public static int specialPriceDelta(Settlement settlement, String resultMaterial, String costMaterial, int costAmount,
            int reputation) {
        double factor = Reputation.priceFactor(reputation);
        if (costAmount < 1) {
            return 0;
        }
        if (ResourceMapper.currencyValue(costMaterial) > 0) {
            Optional<ResourceType> sold = priced(resultMaterial);
            if (sold.isPresent()) {
                return adjustedCost(costAmount, multiplier(settlement, sold.get()) * factor) - costAmount;
            }
        } else if (ResourceMapper.currencyValue(resultMaterial) > 0) {
            Optional<ResourceType> bought = priced(costMaterial);
            if (bought.isPresent()) {
                return adjustedAmount(costAmount, multiplier(settlement, bought.get()) / factor) - costAmount;
            }
        }
        return 0;
    }
}
