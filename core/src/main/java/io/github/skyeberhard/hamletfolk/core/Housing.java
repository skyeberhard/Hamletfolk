package io.github.skyeberhard.hamletfolk.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * R2.2: how many beds a settlement has, which is how many people it can house. Beds are counted per
 * chunk, and a chunk is only recounted while it is loaded, so walking away from a village does not
 * make its housing disappear: an unloaded chunk keeps its last known count.
 */
public final class Housing {
    private final Map<String, Integer> bedsByChunk = new LinkedHashMap<>();
    /** Whether beds have been counted at all (R8.1): no count is not the same as no beds. Not saved; a count refills it. */
    private boolean counted;

    /** True once some chunk has been counted, even if it held no beds. */
    public boolean counted() {
        return counted;
    }

    public static String key(int chunkX, int chunkZ) {
        return chunkX + "," + chunkZ;
    }

    /** Records how many beds a chunk has now. Zero forgets the chunk. */
    public void setChunk(int chunkX, int chunkZ, int beds) {
        counted = true;
        if (beds <= 0) {
            bedsByChunk.remove(key(chunkX, chunkZ));
        } else {
            bedsByChunk.put(key(chunkX, chunkZ), beds);
        }
    }

    /** The beds last counted in a chunk. */
    public int bedsIn(int chunkX, int chunkZ) {
        return bedsByChunk.getOrDefault(key(chunkX, chunkZ), 0);
    }

    /** Total beds: the settlement's housing capacity. */
    public int capacity() {
        int total = 0;
        for (int beds : bedsByChunk.values()) {
            total += beds;
        }
        return total;
    }

    /** How many chunks have any beds. */
    public int chunkCount() {
        return bedsByChunk.size();
    }

    /**
     * Forgets every chunk outside the given inclusive range, so lowering the settlement radius cannot leave
     * counts for chunks that are no longer part of the settlement.
     */
    public void retainWithin(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        bedsByChunk.keySet().removeIf(key -> {
            String[] parts = key.split(",");
            int cx = Integer.parseInt(parts[0]);
            int cz = Integer.parseInt(parts[1]);
            return cx < minChunkX || cx > maxChunkX || cz < minChunkZ || cz > maxChunkZ;
        });
    }

    /** The saved form: chunk key to bed count. */
    Map<String, Integer> asMap() {
        return Collections.unmodifiableMap(bedsByChunk);
    }

    /** Loads a saved count, ignoring a nonsense one (a damaged save must not invent or remove housing). */
    void load(String key, int beds) {
        if (beds <= 0 || beds > 4096 || key == null || !key.matches("-?\\d+,-?\\d+")) {
            return;
        }
        // Rebuilt from the numbers, so "01,2" or "-0,0" cannot become a key setChunk would never write.
        String[] parts = key.split(",");
        try {
            bedsByChunk.put(key(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])), beds);
            counted = true;
        } catch (NumberFormatException e) {
            // out of range: not a real chunk
        }
    }
}
