package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;
import java.util.Set;

/**
 * R8.20: what levelling a building's ground yields. The builders' grading used to cut hills and clear trees for nothing; the rock
 * they cut now goes into the village's stores as stone, one unit a block, and the trees they fell as wood, so a village on a hillside
 * has stone before it has a mine. Pure counting; the Paper layer does the cutting.
 */
public final class GroundWork {
    private static final Set<String> ROCK = Set.of("STONE", "DEEPSLATE", "ANDESITE", "DIORITE", "GRANITE", "TUFF", "CALCITE",
            "SANDSTONE", "RED_SANDSTONE", "SMOOTH_SANDSTONE");

    private GroundWork() {
    }

    /** True for a block that is rock when cut (soil, sand and gravel are not worth a unit of stone). */
    public static boolean isRock(String block) {
        String name = block.toUpperCase(Locale.ROOT);
        int colon = name.indexOf(':');
        name = colon >= 0 ? name.substring(colon + 1) : name;
        return ROCK.contains(name);
    }

    /** What a day's ground work earned, to put in the history. */
    public record Yield(int stone, int wood) {
        public boolean any() {
            return stone > 0 || wood > 0;
        }
    }

    /** Puts the rock cut and the logs felled into the stores and the flow: a unit of stone a block, {@link WorldMarks#UNITS_PER_LOG} wood a log. */
    public static Yield credit(Settlement settlement, long day, int rockBlocks, int logs) {
        int stone = Math.max(0, rockBlocks);
        int wood = Math.max(0, logs) * WorldMarks.UNITS_PER_LOG;
        if (stone > 0) {
            settlement.ledger().add(ResourceType.STONE, stone);
            settlement.flow().recordProduced(ResourceType.STONE, day, stone);
        }
        if (wood > 0) {
            settlement.ledger().add(ResourceType.WOOD, wood);
            settlement.flow().recordProduced(ResourceType.WOOD, day, wood);
        }
        return new Yield(stone, wood);
    }
}
