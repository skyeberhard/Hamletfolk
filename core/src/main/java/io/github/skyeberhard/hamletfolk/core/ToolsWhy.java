package io.github.skyeberhard.hamletfolk.core;

import java.util.Optional;

/**
 * R4.52: why a village is short of tools or metal, in a few words, for the status line ("no tools" alone does not say what to do). Tools
 * are made by a toolsmith in a smithy from metal (or, failing that, stone), and metal comes from a mine that someone works; so the reason is
 * the first of those that is missing. Read-only.
 */
public final class ToolsWhy {
    private ToolsWhy() {
    }

    private static boolean anyAdult(Settlement s, Occupation job) {
        return s.residents().stream().anyMatch(r -> r.adult() && r.occupation() == job);
    }

    /** Why tools are short, or empty if they are being made (a smithy, a toolsmith and something to make them from). */
    public static Optional<String> tools(Settlement s) {
        if (s.buildingCount(BuildingType.SMITHY) == 0) {
            boolean noGround = s.plan() != null && s.plan().nextLot(BuildingType.SMITHY, l -> !PlanOverrides.isLocked(s, l)).isEmpty()
                    && s.conditions().containsKey("lotSearch:" + BuildingType.SMITHY.name());
            return Optional.of(noGround ? "no smithy, and no ground found for one" : "no smithy yet");
        }
        if (!anyAdult(s, Occupation.TOOLSMITH)) {
            return Optional.of("a smithy, but nobody working in it");
        }
        if (s.ledger().get(ResourceType.METAL) == 0 && s.ledger().get(ResourceType.STONE) == 0) {
            return Optional.of(metal(s).map(why -> "nothing to make them from: " + why).orElse("nothing to make them from"));
        }
        return Optional.empty();
    }

    /** Why metal is short, or empty if a mine is being worked. */
    public static Optional<String> metal(Settlement s) {
        if (s.buildingCount(BuildingType.MINE) == 0) {
            return Optional.of("no mine");
        }
        if (!anyAdult(s, Occupation.MINER)) {
            return Optional.of("nobody mining");
        }
        return Optional.empty();
    }
}
