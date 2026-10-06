package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConstructionWorkTest {
    @Test
    void concurrentVillagesDoNotShareBlockOrGroundRetries() {
        ConstructionWork work = new ConstructionWork();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var a = work.progress(first, 1);
        a.attempts().put(42L, 3);
        a.skipped().add(42L);
        for (int i = 0; i < 60; i++) a.nextGradingPass();
        for (int i = 0; i < 5; i++) a.nextSignTry();

        var b = work.progress(second, 1);
        assertEquals(0, b.attempts().getOrDefault(42L, 0));
        assertFalse(b.skipped().contains(42L));
        assertEquals(1, b.nextGradingPass());
        assertEquals(1, b.nextSignTry());
        assertSame(a, work.progress(first, 1));
        assertEquals(61, a.nextGradingPass());
        assertEquals(6, a.nextSignTry());
    }

    @Test
    void finishingOrCancellingOneVillageDoesNotResetAnother() {
        ConstructionWork work = new ConstructionWork();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var a = work.progress(first, 1);
        var b = work.progress(second, 1);
        b.attempts().put(42L, 2);
        b.skipped().add(99L);
        b.nextGradingPass();
        b.nextSignTry();

        work.forget(first, 1);
        assertNotSame(a, work.progress(first, 1));
        assertSame(b, work.progress(second, 1));
        assertEquals(2, b.attempts().get(42L));
        assertTrue(b.skipped().contains(99L));
        assertEquals(2, b.nextGradingPass());
        assertEquals(2, b.nextSignTry());
    }

    @Test
    void projectsWithinOneVillageStaySeparateAndGradingResetKeepsOtherRetries() {
        ConstructionWork work = new ConstructionWork();
        UUID village = UUID.randomUUID();
        var first = work.progress(village, 1);
        first.attempts().put(42L, 2);
        first.skipped().add(99L);
        first.nextSignTry();
        first.nextGradingPass();
        first.finishGrading();
        assertEquals(1, first.nextGradingPass());
        assertEquals(2, first.nextSignTry());
        assertEquals(2, first.attempts().get(42L));
        assertTrue(first.skipped().contains(99L));
        assertTrue(work.progress(village, 2).attempts().isEmpty());
        work.forget(village, 2);
        assertSame(first, work.progress(village, 1));
    }
}
