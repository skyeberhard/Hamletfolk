package io.github.skyeberhard.hamletfolk.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * R1.17: orders saves that are snapshotted on one thread and written on another. Each
 * snapshot takes a number when it's made; a write only happens if its snapshot is newer
 * than the last one successfully written, so a slow background save can never overwrite
 * a newer one (e.g. the save made at shutdown).
 */
public final class SaveSequence {
    private final AtomicLong issued = new AtomicLong();
    private long written;

    /** Call when taking a snapshot, on the thread that owns the data. */
    public long next() {
        return issued.incrementAndGet();
    }

    /**
     * Runs {@code write} only if {@code sequence} is newer than the last successful write.
     * A write that throws doesn't count, so a later retry of the same or an older snapshot
     * isn't wrongly skipped.
     *
     * @return whether the write ran
     */
    public synchronized <E extends Exception> boolean writeIfNewer(long sequence, Write<E> write) throws E {
        if (sequence <= written) {
            return false;
        }
        write.run();
        written = sequence;
        return true;
    }

    @FunctionalInterface
    public interface Write<E extends Exception> {
        void run() throws E;
    }
}
