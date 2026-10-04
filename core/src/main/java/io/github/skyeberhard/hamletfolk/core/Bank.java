package io.github.skyeberhard.hamletfolk.core;

import java.util.Optional;

/**
 * R2.7: where a settlement takes donations and pays out for requests. Once a settlement has a registered
 * treasury building (R2.6) that is the only place to do it: standing at one of its signs. A settlement with
 * no treasury building keeps the older rule, anywhere in the settlement, so existing villages still work.
 * The goods themselves are still consumed into the ledger; the building is where the exchange happens.
 */
public final class Bank {
    /** How far from a treasury sign, level, a player may stand and still be at the counter. */
    public static final int RANGE = 12;
    /** How far above or below the sign, so a counter on another floor does not count. */
    public static final int HEIGHT = 8;

    private Bank() {
    }

    /**
     * Whether the exchange may happen here, and if not, which treasury is nearest.
     *
     * @param allowed   true at a counter, or anywhere when the settlement has no treasury building
     * @param nearest   the treasury building to go to; present only when {@code allowed} is false
     * @param distance  blocks in a straight line to that building (rounded up); 0 when {@code nearest} is empty
     */
    public record Access(boolean allowed, Optional<Building> nearest, int distance) {
    }

    /** Checks a player standing at a block position in the settlement. */
    public static Access check(Settlement settlement, int x, int y, int z) {
        Building nearest = null;
        double best = Double.MAX_VALUE;
        boolean any = false;
        for (Building building : settlement.buildings()) {
            if (building.type() != BuildingType.TREASURY) {
                continue;
            }
            any = true;
            double dx = building.x() - x;
            double dz = building.z() - z;
            double dy = building.y() - y;
            double level = Math.sqrt(dx * dx + dz * dz);
            if (level <= RANGE && Math.abs(dy) <= HEIGHT) {
                return new Access(true, Optional.empty(), 0);
            }
            // Straight-line distance, so being on the wrong floor does not read as "0 blocks away".
            double straight = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (straight < best) {
                best = straight;
                nearest = building;
            }
        }
        if (!any) {
            return new Access(true, Optional.empty(), 0);
        }
        return new Access(false, Optional.ofNullable(nearest), (int) Math.ceil(best));
    }
}
