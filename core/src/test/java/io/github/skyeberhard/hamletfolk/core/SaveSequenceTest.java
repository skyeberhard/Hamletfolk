package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R1.17: a stale background save can't overwrite a newer one. */
class SaveSequenceTest {

    @Test
    void olderSnapshotFinishingLastIsSkipped() throws Exception {
        SaveSequence sequence = new SaveSequence();
        List<String> file = new ArrayList<>();
        long autosave = sequence.next();   // background autosave snapshotted first...
        long shutdown = sequence.next();   // ...then the shutdown save

        assertTrue(sequence.writeIfNewer(shutdown, () -> file.add("shutdown")));
        assertFalse(sequence.writeIfNewer(autosave, () -> file.add("autosave")));
        assertEquals(List.of("shutdown"), file);
    }

    @Test
    void aWriteWaitsForAnExclusiveActionToFinish() throws Exception {
        // R1.19: a backup copy and a save must never touch the file at the same time.
        SaveSequence sequence = new SaveSequence();
        List<String> order = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.CountDownLatch backupStarted = new java.util.concurrent.CountDownLatch(1);
        Thread backup = new Thread(() -> {
            try {
                sequence.exclusive(() -> {
                    backupStarted.countDown();
                    Thread.sleep(200);
                    order.add("backup done");
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        backup.start();
        backupStarted.await();
        sequence.writeIfNewer(sequence.next(), () -> order.add("save"));
        backup.join();
        assertEquals(List.of("backup done", "save"), order);
    }

    @Test
    void anExclusiveActionDoesNotCountAsAWrite() throws Exception {
        SaveSequence sequence = new SaveSequence();
        long first = sequence.next();
        sequence.exclusive(() -> { });
        assertTrue(sequence.writeIfNewer(first, () -> { }));
    }

    @Test
    void inOrderWritesAllHappen() throws Exception {
        SaveSequence sequence = new SaveSequence();
        List<Long> file = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long seq = sequence.next();
            assertTrue(sequence.writeIfNewer(seq, () -> file.add(seq)));
        }
        assertEquals(List.of(1L, 2L, 3L), file);
    }

    @Test
    void failedWriteDoesNotBlockARetry() throws Exception {
        SaveSequence sequence = new SaveSequence();
        long seq = sequence.next();
        assertThrows(IOException.class, () -> sequence.writeIfNewer(seq, () -> {
            throw new IOException("disk full");
        }));
        assertTrue(sequence.writeIfNewer(seq, () -> { }));
    }
}
