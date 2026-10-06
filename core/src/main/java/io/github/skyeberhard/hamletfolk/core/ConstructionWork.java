package io.github.skyeberhard.hamletfolk.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** In-memory placement retries; project numbers are unique only within their settlement. */
public final class ConstructionWork {
    private record Key(UUID settlement, int project) { }

    private final Map<Key, Progress> projects = new HashMap<>();

    public Progress progress(UUID settlement, int project) {
        return projects.computeIfAbsent(new Key(settlement, project), key -> new Progress());
    }

    /** Completing or abandoning one village's project must leave every other project alone. */
    public void forget(UUID settlement, int project) {
        projects.remove(new Key(settlement, project));
    }

    /** Not saved: recomputed from the world after a restart, just like the remaining block diff. */
    public static final class Progress {
        private final Map<Long, Integer> attempts = new HashMap<>();
        private final Set<Long> skipped = new HashSet<>();
        private int signTries;
        private int gradingPasses;

        public Map<Long, Integer> attempts() { return attempts; }
        public Set<Long> skipped() { return skipped; }
        public int nextSignTry() { return ++signTries; }
        public int nextGradingPass() { return ++gradingPasses; }
        public void finishGrading() { gradingPasses = 0; }
    }
}
