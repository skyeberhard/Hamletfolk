package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** R1.10: settlements are only tracked in worlds the config's allow/deny lists let through. */
class WorldFilterTest {

    @Test
    void emptyListsAcceptEveryWorld() {
        WorldFilter filter = WorldFilter.everything();
        assertTrue(filter.accepts("world"));
        assertTrue(filter.accepts("world_nether"));
    }

    @Test
    void anAllowListLimitsTrackingToMatchingWorlds() {
        WorldFilter filter = new WorldFilter(List.of("world", "world_nether"), List.of());
        assertTrue(filter.accepts("world"));
        assertTrue(filter.accepts("WORLD_NETHER"));
        assertFalse(filter.accepts("creative_plots"));
    }

    @Test
    void aDenyListExcludesMatchingWorldsAndWinsOverTheAllowList() {
        WorldFilter denyOnly = new WorldFilter(List.of(), List.of("void_*", "plots"));
        assertTrue(denyOnly.accepts("world"));
        assertFalse(denyOnly.accepts("void_spawn"));
        assertFalse(denyOnly.accepts("plots"));

        WorldFilter both = new WorldFilter(List.of("world*"), List.of("world_the_end"));
        assertTrue(both.accepts("world"));
        assertFalse(both.accepts("world_the_end"));
    }

    @Test
    void patternsMatchTheWholeNameAndTreatOtherCharactersLiterally() {
        WorldFilter filter = new WorldFilter(List.of("world"), List.of());
        assertFalse(filter.accepts("world2"));
        assertFalse(filter.accepts("myworld"));
        WorldFilter dotted = new WorldFilter(List.of("a.b"), List.of());
        assertTrue(dotted.accepts("a.b"));
        assertFalse(dotted.accepts("axb"));
    }

    @Test
    void blankEntriesAreIgnoredAndNullWorldsAreRefused() {
        WorldFilter filter = new WorldFilter(List.of("", "  "), List.of(""));
        assertTrue(filter.accepts("world"));
        assertFalse(filter.accepts(null));
    }
}
