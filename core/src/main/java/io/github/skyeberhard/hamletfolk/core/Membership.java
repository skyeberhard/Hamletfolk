package io.github.skyeberhard.hamletfolk.core;

import java.util.Optional;
import java.util.UUID;

/**
 * R1.8: membership follows where a resident actually lives. A villager that stays in another
 * settlement's area, outside its own, for {@link #STAY_DAYS} days becomes a resident of that settlement.
 * The Minecraft layer reports where a villager is now; the rule and the count of days are here. The count
 * is kept as a condition on the settlement they belong to (so it is saved and needs no format change):
 * {@code straying:<resident>:<settlement they are in>} holding the day they were first seen there.
 */
public final class Membership {
    /** How many days someone must stay in another settlement's area before they move there. */
    public static final int STAY_DAYS = 3;
    static final String STRAYING = "straying:";

    private Membership() {
    }

    /** The condition key marking a resident as having been seen in {@code target}'s area. */
    static String key(UUID resident, UUID target) {
        return STRAYING + resident + ":" + target;
    }

    /**
     * Notes where a resident is on {@code day}, and moves them to the settlement whose area they have
     * stayed in for {@link #STAY_DAYS} days (an abandoned settlement can be resettled this way, as with a
     * newcomer). Being anywhere inside their own settlement's radius, or in no
     * settlement's area, ends the count; so does moving to a different settlement's area (which starts a
     * new one). A resident whose move on paper is still being carried out (R4.2) is left alone, since their
     * villager has not arrived yet. Returns the settlement they moved to, if they moved.
     */
    public static Optional<Settlement> observe(SettlementRegistry registry, Resident resident, String world, int x, int z,
            int radius, long day) {
        Settlement home = registry.settlementOf(resident.id()).orElse(null);
        if (home == null || home.hasCondition(Migration.MOVING + resident.id())) {
            return Optional.empty();
        }
        Long arrived = home.conditions().get(Migration.ARRIVED + resident.id());
        if (arrived != null) {
            if (day - arrived < Migration.ARRIVAL_GRACE_DAYS) {
                return Optional.empty(); // just brought here by R4.2: give them time to settle
            }
            home.conditions().remove(Migration.ARRIVED + resident.id());
        }
        long radiusSquared = (long) radius * radius;
        boolean inOwnArea = home.world().equals(world) && home.distanceSquared(x, z) <= radiusSquared;
        Settlement area = inOwnArea ? null : registry.nearest(world, x, z, radius).orElse(null);
        if (area == null || area == home) {
            clear(home, resident.id(), null);
            return Optional.empty();
        }
        String key = key(resident.id(), area.id());
        clear(home, resident.id(), key); // being somewhere else now ends any other count
        Long since = home.conditions().get(key);
        if (since == null) {
            home.conditions().put(key, day);
            return Optional.empty();
        }
        if (day - since < STAY_DAYS) {
            return Optional.empty();
        }
        registry.transfer(resident, home, area, day,
                resident.fullName() + " settled in " + area.name() + " and is no longer counted in " + home.name() + ".",
                resident.fullName() + " came to live in " + area.name() + " from " + home.name() + ".", false);
        return Optional.of(area);
    }

    /** Ends the count for a resident, except the one under {@code keep} (may be null). */
    private static void clear(Settlement home, UUID resident, String keep) {
        String prefix = STRAYING + resident + ":";
        home.conditions().keySet().removeIf(k -> k.startsWith(prefix) && !k.equals(keep));
    }
}
