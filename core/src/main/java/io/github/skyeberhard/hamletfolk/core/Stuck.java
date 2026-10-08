package io.github.skyeberhard.hamletfolk.core;

/**
 * R1.31: whether a villager is stuck in a pit: walled in where it stands, with open sky above it. A villager can walk to
 * a neighbouring block if that block and the one above it are open, or step up onto it if it is solid with two open
 * blocks above, and it needs headroom to step up. Walled in means it can do neither in any of the four directions. The
 * open sky is what tells a hole it pathed into from a room, a trading-hall cell or a breeder a player built round it,
 * which all have a roof. The Paper layer says which blocks around the villager's feet are solid; this decides.
 */
public final class Stuck {
    /** Minutes a villager must stand still, walled in, before it is moved out. */
    public static final int MINUTES = 10;

    private Stuck() {
    }

    /** What is at an offset from the block the villager's feet are in. */
    public interface Blocks {
        boolean solid(int dx, int dy, int dz);
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** True if the villager is in a pit: walled in, and nothing over it (a roofed cell is someone's building, not a pit). */
    public static boolean inPit(Blocks blocks, boolean openSky) {
        return openSky && walledIn(blocks);
    }

    /** True if the villager cannot walk or step out to any side. */
    public static boolean walledIn(Blocks blocks) {
        boolean headroom = !blocks.solid(0, 2, 0);
        for (int[] side : SIDES) {
            int dx = side[0];
            int dz = side[1];
            boolean walk = !blocks.solid(dx, 0, dz) && !blocks.solid(dx, 1, dz);
            boolean step = headroom && blocks.solid(dx, 0, dz) && !blocks.solid(dx, 1, dz) && !blocks.solid(dx, 2, dz);
            if (walk || step) {
                return false;
            }
        }
        return true;
    }
}
