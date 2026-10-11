package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** R8.18: the chunks a village's planning needs loaded. */
class ChunkRegionTest {
    @Test
    void chunksPackAndUnpackIncludingNegativeOnes() {
        for (int x : new int[] {0, 1, -1, 62, -63, 100_000, -100_000}) {
            for (int z : new int[] {0, 1, -1, 17, -64, 99_999, -99_999}) {
                long packed = ChunkRegion.pack(x, z);
                assertEquals(x, ChunkRegion.chunkX(packed));
                assertEquals(z, ChunkRegion.chunkZ(packed));
            }
        }
    }

    @Test
    void theRegionCoversEveryBlockWithinReachAndNothingBeyondAChunk() {
        Set<Long> chunks = ChunkRegion.covering(-76, -55, 112, 112);
        // blocks -188..36 east-west and -167..57 north-south: chunks -12..2 and -11..3
        assertEquals(15 * 15, chunks.size());
        assertTrue(chunks.contains(ChunkRegion.pack(-12, -11)));
        assertTrue(chunks.contains(ChunkRegion.pack(2, 3)));
        assertTrue(chunks.contains(ChunkRegion.pack(-76 >> 4, -55 >> 4)), "the centre's own chunk");
        assertTrue(!chunks.contains(ChunkRegion.pack(3, 3)) && !chunks.contains(ChunkRegion.pack(-13, 0)));
    }

    @Test
    void aStripIsLongerOneWayThanTheOther() {
        Set<Long> strip = ChunkRegion.covering(0, 0, 96, 40);
        assertEquals(13 * 6, strip.size(), "chunks -6..6 east-west, -3..2 north-south");
    }

    @Test
    void aPointIsInItsOwnChunkAndReachCrossesChunkEdges() {
        Set<Long> chunks = ChunkRegion.covering(16, 16, 0, 0);
        assertEquals(Set.of(ChunkRegion.pack(1, 1)), chunks);
        assertEquals(Set.of(ChunkRegion.pack(0, 0), ChunkRegion.pack(1, 0)), ChunkRegion.covering(15, 0, 1, 0));
    }
}
