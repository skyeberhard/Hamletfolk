package io.github.skyeberhard.hamletfolk.core;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * R3.2: what a trade between a player and a villager does to the settlement's stores. Emeralds are
 * minted by the game, not paid from the treasury, so only the goods move: what the player hands over for
 * emeralds is added to the stores (as far as they have room, R3.10), and what the player buys with
 * emeralds is taken out of them, but never below what the village wants to hold, so buying cannot starve
 * a village or make the shortage whose request pays more than the goods cost. Selling to a short village
 * makes the next sale pay better (prices follow the stores, R3.1); a 1-emerald purchase never changes
 * price, which is why buying is held back by that floor rather than by price.
 */
public final class Trading {
    private Trading() {
    }

    /** Food a founding resident brings, double what the village wants of it (R3.14). */
    static final int FOOD_SEED_PER_HEAD = 2 * SettlementSimulator.FOOD_WANTED_PER_HEAD;

    /**
     * R3.14: food for a newly founded village, added once for each founding resident, so its first
     * traders have something to sell. Food is the one resource vanilla villagers really sell. Nothing else
     * is seeded: a village with no wood, stone, metal or tools should feel it.
     */
    public static void seedFounder(Settlement settlement) {
        settlement.ledger().add(ResourceType.FOOD, FOOD_SEED_PER_HEAD);
    }

    /**
     * R3.14: how many more times a villager may make a trade that sells the player {@code resultAmount} of
     * {@code resultMaterial} for emeralds, given what the village can spare (stock above what it wants to
     * hold). Empty when the trade is not one the stores govern (not emeralds for food, wood, stone or
     * metal; tools and goods such as wool and glass are left as in vanilla, like their prices; or worth less
     * than a unit), meaning the game's own limit stands.
     */
    public static OptionalInt tradesAllowed(Settlement settlement, String costMaterial, String resultMaterial,
            int resultAmount) {
        if (!ResourceMapper.isCurrency(costMaterial)) {
            return OptionalInt.empty();
        }
        Optional<ResourceMapper.Value> value = ResourceMapper.value(resultMaterial)
                .filter(v -> v.type() != ResourceType.TOOLS && v.type() != ResourceType.GOODS);
        if (value.isEmpty() || value.get().unitsFor(resultAmount) <= 0) {
            return OptionalInt.empty();
        }
        int spare = Math.max(0, settlement.ledger().get(value.get().type())
                - PriceModel.wanted(settlement, value.get().type()));
        return OptionalInt.of(spare / value.get().unitsFor(resultAmount));
    }

    /** The change a trade made: {@code units} of {@code type}, added when {@code gained}, else taken out. */
    public record Effect(ResourceType type, int units, boolean gained) {
    }

    /**
     * Applies a trade in which the player gives {@code givenAmount} of {@code givenMaterial} and receives
     * {@code receivedAmount} of {@code receivedMaterial}. Does nothing unless exactly one side is emeralds
     * and the other is something the village can use. Returns what changed, if anything did.
     */
    public static Optional<Effect> apply(Settlement settlement, String givenMaterial, int givenAmount,
            String receivedMaterial, int receivedAmount) {
        boolean paysEmeralds = ResourceMapper.isCurrency(givenMaterial);
        boolean getsEmeralds = ResourceMapper.isCurrency(receivedMaterial);
        if (paysEmeralds == getsEmeralds) {
            return Optional.empty();
        }
        Ledger ledger = settlement.ledger();
        if (getsEmeralds) {
            return ResourceMapper.value(givenMaterial)
                    .filter(value -> value.type() != ResourceType.TOOLS) // a worn tool's worth depends on wear; not known here
                    .flatMap(value -> {
                        int units = Math.min(value.unitsFor(givenAmount), SettlementSimulator.room(settlement, value.type()));
                        if (units <= 0) {
                            return Optional.empty();
                        }
                        ledger.add(value.type(), units);
                        return Optional.of(new Effect(value.type(), units, true));
                    });
        }
        return ResourceMapper.value(receivedMaterial)
                .filter(value -> value.type() != ResourceType.TOOLS)
                .flatMap(value -> {
                    // The game has already decided the trade; only what the village can spare comes out of the stores.
                    int spare = Math.max(0, ledger.get(value.type()) - PriceModel.wanted(settlement, value.type()));
                    int units = ledger.take(value.type(), Math.min(spare, value.unitsFor(receivedAmount)));
                    return units <= 0 ? Optional.empty() : Optional.of(new Effect(value.type(), units, false));
                });
    }
}
