package io.github.skyeberhard.hamletfolk.core;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * R4.56: skill gates what a village can improve. A building above its first tier is raised only if someone in the village has the skill for that
 * tier: tier 2 needs an apprentice (level 2) of the trade the building is for, tier 3 a journeyman, tier 4 an expert, tier 5 a master. The trade is the
 * building's own (a smithy's smiths, a shop's merchants, a farm's farmers); a house's is building (anyone who has finished buildings, by their rank:
 * a builder is level 2 at 3 buildings, 3 at 6, 4 at 10) and a guard post's the guards'. A building with no trade of its own (the square, the works) is not
 * gated. A first tier is never gated, so a village always gets something built; this only paces improvement, and gives experience a reason to
 * matter.
 */
public final class Guild {
    /**
     * Whether skill gates improvement (the server's {@code construction.guild}). Off by default so the simulation tests that are about something else do
     * not need a master of every trade; the Paper layer switches it on from the config. A static, like the lifespan scale: a test that changes it must put
     * it back.
     */
    private static volatile boolean enforced;

    public static void setEnforced(boolean on) {
        enforced = on;
    }

    public static boolean enforced() {
        return enforced;
    }

    /** Buildings a builder has finished to count as a master (tier 5): their own ranks stop at the fourth, "master builder", at 10. */
    static final int VETERAN = 20;

    private Guild() {
    }

    /** The occupations whose skill counts for a building, empty if its tiers are not gated; a house is the builders' (see {@link #skill}). */
    static List<Occupation> trades(BuildingType type) {
        List<Occupation> own = type.jobs();
        if (!own.isEmpty()) {
            return own;
        }
        return switch (type) {
            case GUARD_POST -> List.of(Occupation.GUARD);
            case TREASURY, TRADING_POST -> List.of(Occupation.MERCHANT);
            case SAWMILL -> List.of(Occupation.LUMBERJACK);
            case GRANARY -> List.of(Occupation.FARMER);
            case FORGE -> List.of(Occupation.TOOLSMITH, Occupation.ARMORER, Occupation.WEAPONSMITH);
            default -> List.of();
        };
    }

    /** True if the tiers of this kind of building are gated by skill. */
    public static boolean gated(BuildingType type) {
        return type == BuildingType.HOUSE || !trades(type).isEmpty();
    }

    /**
     * The best skill (level 1 to 5) anyone in the village has for this kind of building, or 0 if nobody does. A house counts the builders' rank among
     * all adults (a builder who has moved on to another trade keeps it).
     */
    public static int skill(Settlement s, BuildingType type) {
        int best = 0;
        List<Occupation> trades = trades(type);
        for (Resident r : s.residents()) {
            if (!r.adult()) {
                continue;
            }
            if (type == BuildingType.HOUSE) {
                if (r.built() > 0 || r.occupation() == Occupation.BUILDER) {
                    // a builder's rank is 1 to 4 (3, 6 and 10 buildings finished); a veteran with {@link #VETERAN} buildings counts as a master of the building trade, tier 5
                    best = Math.max(best, r.built() >= VETERAN ? TradeLevel.MASTER : Construction.builderLevel(r.built()) + 1);
                }
            } else if (trades.contains(r.occupation())) {
                best = Math.max(best, r.level());
            }
        }
        return best;
    }

    /** The highest tier the village's skill allows for this kind of building (at least 1; unlimited if it is not gated). */
    public static int tierCap(Settlement s, BuildingType type) {
        return enforced && gated(type) ? Math.max(1, skill(s, type)) : Integer.MAX_VALUE;
    }

    /** e.g. "a journeyman toolsmith": who a tier needs. */
    public static String need(BuildingType type, int tier) {
        String level = TradeLevel.name(tier);
        String trade = type == BuildingType.HOUSE ? "builder" : trades(type).isEmpty() ? "worker"
                : trades(type).get(0).title().toLowerCase(Locale.ROOT);
        String article = level.startsWith("a") || level.startsWith("e") ? "an " : "a ";
        return article + level + " " + trade;
    }

    /** Why a building cannot be improved to {@code tier} for lack of skill, or empty if it can (or is not gated). */
    public static Optional<String> refusal(Settlement s, BuildingType type, int tier) {
        int have = skill(s, type);
        if (!enforced || !gated(type) || tier <= 1 || tier <= have) {
            return Optional.empty();
        }
        return Optional.of("tier " + tier + " needs " + need(type, tier) + " (the best here is "
                + (have == 0 ? "nobody in the trade" : TradeLevel.name(have)) + ")");
    }
}
