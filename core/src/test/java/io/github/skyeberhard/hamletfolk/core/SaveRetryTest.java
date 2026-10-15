package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R1.33: a save that finds its file locked for a moment tries again. */
class SaveRetryTest {
    private final List<Long> pauses = new ArrayList<>();

    private int runFailing(int failures, IOException failure) throws IOException {
        int[] calls = {0};
        return SaveRetry.run(() -> {
            if (calls[0]++ < failures) {
                throw failure;
            }
        }, SaveRetry.ATTEMPTS, SaveRetry.FIRST_PAUSE_MILLIS, pauses::add);
    }

    @Test
    void aSaveThatWorksFirstTimeWaitsForNothing() throws IOException {
        assertEquals(1, runFailing(0, new AccessDeniedException("x")));
        assertTrue(pauses.isEmpty());
    }

    @Test
    void aLockedFileIsTriedAgainWithGrowingPauses() throws IOException {
        assertEquals(3, runFailing(2, new AccessDeniedException("settlements.json.tmp -> settlements.json")));
        assertEquals(List.of(100L, 200L), pauses);
    }

    @Test
    void aFileThatStaysLockedFailsWithTheLastErrorAfterTheLastTry() {
        AccessDeniedException locked = new AccessDeniedException("still locked");
        AccessDeniedException thrown = assertThrows(AccessDeniedException.class, () -> runFailing(99, locked));
        assertEquals(locked, thrown);
        assertEquals(SaveRetry.ATTEMPTS - 1, pauses.size());
    }

    @Test
    void aFailureThatIsNotALockedFileIsNotRetried() {
        assertThrows(IOException.class, () -> runFailing(1, new IOException("disk full")));
        assertTrue(pauses.isEmpty());
    }

    @Test
    void anInterruptedWaitGivesUpAndKeepsTheInterrupt() {
        IOException thrown = assertThrows(IOException.class, () -> SaveRetry.run(() -> {
            throw new AccessDeniedException("x");
        }, 3, 10, millis -> {
            throw new InterruptedException();
        }));
        assertTrue(thrown.getMessage().contains("interrupted"));
        assertTrue(Thread.interrupted(), "and the interrupt is passed on (this clears it for the next test)");
    }
}
