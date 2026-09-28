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
