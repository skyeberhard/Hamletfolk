package io.github.skyeberhard.hamletfolk.core;

import java.io.IOException;
import java.nio.file.FileSystemException;

/**
 * R1.33: tries a file operation again a few times when another program holds the file for a moment (a sync client such as
 * OneDrive, a virus scanner, a backup tool). Windows then refuses to replace the save file with an "access denied" that goes
 * away on its own. Any other failure is not retried.
 */
public final class SaveRetry {
    /** Total tries. */
    public static final int ATTEMPTS = 5;
    /** Milliseconds between tries, doubling each time (100, 200, 400, 800: a second and a half in all). */
    public static final long FIRST_PAUSE_MILLIS = 100;

    private SaveRetry() {
    }

    @FunctionalInterface
    public interface Action {
        void run() throws IOException;
    }

    @FunctionalInterface
    public interface Pause {
        void pause(long millis) throws InterruptedException;
    }

    /**
     * Runs the action, trying again after a pause when the file is locked.
     *
     * @return how many tries it took (1 when the first worked)
     * @throws IOException the last failure, when every try failed or the failure was not a locked file
     */
    public static int run(Action action, int attempts, long firstPauseMillis, Pause pause) throws IOException {
        long wait = firstPauseMillis;
        for (int attempt = 1; ; attempt++) {
            try {
                action.run();
                return attempt;
            } catch (FileSystemException e) {
                if (attempt >= attempts || !lockedFile(e)) {
                    throw e;
                }
            }
            try {
                pause.pause(wait);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting to save again", interrupted);
            }
            wait *= 2;
        }
    }

    /**
     * Whether a file-system failure may be a file another program holds for a moment: access denied, or a sharing violation
     * (which Windows reports as a plain file-system error). A file that is missing, a directory in the way, or a move the system
     * cannot do atomically will not get better by waiting.
     */
    static boolean lockedFile(FileSystemException e) {
        return !(e instanceof java.nio.file.NoSuchFileException || e instanceof java.nio.file.AtomicMoveNotSupportedException
                || e instanceof java.nio.file.NotDirectoryException || e instanceof java.nio.file.FileAlreadyExistsException
                || e instanceof java.nio.file.DirectoryNotEmptyException || e instanceof java.nio.file.FileSystemLoopException
                || e instanceof java.nio.file.NotLinkException);
    }

    /** As above with the usual number of tries and pauses, really waiting. */
    public static int run(Action action) throws IOException {
        return run(action, ATTEMPTS, FIRST_PAUSE_MILLIS, Thread::sleep);
    }
}
