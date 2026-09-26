package io.github.skyeberhard.hamletfolk.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Keeps timestamped copies of a save file, e.g. {@code settlements-20260926-204512.json},
 * and deletes all but the newest few. Timestamps are UTC and sort by name.
 */
public final class SaveBackups {
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private SaveBackups() {
    }

    /**
     * Copies {@code file} into {@code directory} and prunes older copies down to {@code keep}.
     *
     * @return the backup written, or empty if {@code file} doesn't exist yet
     */
    public static Optional<Path> backup(Path file, Path directory, int keep, Instant now) throws IOException {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        Files.createDirectories(directory);
        String[] parts = splitExtension(file.getFileName().toString());
        Path target = directory.resolve(parts[0] + "-" + STAMP.format(now) + parts[1]);
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        prune(directory, parts[0], parts[1], keep);
        return Optional.of(target);
    }

    /** Backups of {@code file} in {@code directory}, newest first. */
    public static List<Path> list(Path file, Path directory) throws IOException {
        String[] parts = splitExtension(file.getFileName().toString());
        return matching(directory, parts[0], parts[1]);
    }

    private static void prune(Path directory, String base, String extension, int keep) throws IOException {
        List<Path> backups = matching(directory, base, extension);
        for (Path old : backups.subList(Math.min(Math.max(keep, 1), backups.size()), backups.size())) {
            Files.deleteIfExists(old);
        }
    }

    private static List<Path> matching(Path directory, String base, String extension) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        String prefix = base + "-";
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> result = new ArrayList<>(files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(extension)
                                && name.length() == prefix.length() + 15 + extension.length();
                    })
                    .toList());
            result.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
            return result;
        }
    }

    private static String[] splitExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? new String[] {name, ""} : new String[] {name.substring(0, dot), name.substring(dot)};
    }
}
