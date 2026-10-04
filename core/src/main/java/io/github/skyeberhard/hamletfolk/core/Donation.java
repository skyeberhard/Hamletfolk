package io.github.skyeberhard.hamletfolk.core;

/**
 * R3.11: what a donation of items actually does to a settlement's stores. A store has a limit
 * (R3.10), so a gift is only taken as far as it fits: whatever would be wasted stays with the donor.
 */
public final class Donation {
    private Donation() {
    }

    /**
     * @param items          how many of the offered items are taken
     * @param units          what they are worth to the stores, in ledger units
     * @param room           how many units of this resource the stores could still hold before the gift
     * @param limitedByRoom  true if fewer items were taken than were offered because the store is (nearly) full
     */
    public record Plan(int items, int units, int room, boolean limitedByRoom) {
    }

    /**
     * R2.6: how many emerald items (or blocks) the treasury takes: as many as fit in its room, each worth
     * {@code perItem} emeralds. The room is how many more emeralds it can hold (the limit less what it has).
     */
    public static Plan planEmeralds(int room, int perItem, int offered) {
        int items = Math.min(Math.max(0, offered), Math.max(0, room) / Math.max(1, perItem));
        return new Plan(items, items * perItem, Math.max(0, room), items < offered);
    }

    /**
     * Works out how much of {@code offered} items, valued as {@code value}, the settlement takes: as
     * many as are worth something and fit in the room left, and no more than their worth needs. A stack
     * is valued whole and rounded down (R3.12), so the player never pays for an item that earns nothing.
     */
    public static Plan plan(Settlement settlement, ResourceMapper.Value value, int offered) {
        int room = SettlementSimulator.room(settlement, value.type());
        int worth = value.unitsFor(offered);
        if (worth <= room) {
            // Everything fits: take only the items the credit pays for (an odd stick or bamboo stalk stays).
            int items = worth == 0 ? 0 : Math.min(offered, value.itemsFor(worth));
            return new Plan(items, value.unitsFor(items), room, false);
        }
        int items = Math.min(offered, value.itemsThatFit(room));
        return new Plan(items, value.unitsFor(items), room, true);
    }
}
