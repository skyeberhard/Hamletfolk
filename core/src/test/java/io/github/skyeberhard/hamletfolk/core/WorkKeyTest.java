package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.7/R4.8: two villages building at once each have a project 1, and their working state must not be shared. */
class WorkKeyTest {
    @Test
    void theSameProjectNumberInTwoVillagesIsTwoKeys() {
        UUID a = new UUID(1, 1);
        UUID b = new UUID(1, 2);
        assertNotEquals(new WorkKey(a, 1), new WorkKey(b, 1));
        assertEquals(new WorkKey(a, 1), new WorkKey(a, 1));
        Map<WorkKey, Integer> passes = new HashMap<>();
        passes.merge(new WorkKey(a, 1), 1, Integer::sum);
        passes.merge(new WorkKey(a, 1), 1, Integer::sum);
        passes.merge(new WorkKey(b, 1), 1, Integer::sum);
        assertEquals(2, passes.get(new WorkKey(a, 1)));
        assertEquals(1, passes.get(new WorkKey(b, 1)));
        passes.remove(new WorkKey(a, 1));
        assertEquals(1, passes.get(new WorkKey(b, 1)), "finishing one village's project leaves the other's");
    }
}
