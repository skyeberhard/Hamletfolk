package io.github.skyeberhard.hamletfolk.core;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * R8.18: the chunks round a point, for the Paper layer to hold loaded while a village is planned. A chunk is packed into a long
 * the way the construction service packs them (x in the high 32 bits, z in the low 32). Pure, so the arithmetic is tested.
 */
public final class ChunkRegion {
    private ChunkRegion() {
    }

    /** The chunks that cover {@code reachX} blocks east and west of the block (cx, cz) and {@code reachZ} north and south of it. */
    public static Set<Long> covering(int cx, int cz, int reachX, int reachZ) {
        Set<Long> out = new LinkedHashSet<>();
        for (int x = (cx - reachX) >> 4; x <= (cx + reachX) >> 4; x++) {
            for (int z = (cz - reachZ) >> 4; z <= (cz + reachZ) >> 4; z++) {
                out.add(pack(x, z));
            }
        }
        return out;
    }

    public static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    public static int chunkX(long packed) {
        return (int) (packed >> 32);
    }

    public static int chunkZ(long packed) {
        return (int) packed;
    }
}
