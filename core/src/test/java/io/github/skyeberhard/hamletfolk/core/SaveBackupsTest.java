package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R1.3: the previous save is copied to backups/ on startup, keeping the last 5. */
class SaveBackupsTest {
    @TempDir
    Path dataFolder;

    @Test
    void noSaveYetMeansNoBackup() throws IOException {
        Path save = dataFolder.resolve("settlements.json");
        assertTrue(SaveBackups.backup(save, dataFolder.resolve("backups"), 5, Instant.now()).isEmpty());
        assertTrue(Files.notExists(dataFolder.resolve("backups")));
    }

    @Test
    void keepsTheNewestFiveWithTheirContents() throws IOException {
        Path save = dataFolder.resolve("settlements.json");
        Path backups = dataFolder.resolve("backups");
        Instant start = Instant.parse("2026-09-26T20:00:00Z");

        for (int i = 0; i < 7; i++) {
            Files.writeString(save, "save " + i);
            SaveBackups.backup(save, backups, 5, start.plusSeconds(60L * i));
        }

        List<Path> kept = SaveBackups.list(save, backups);
        assertEquals(5, kept.size());
        assertEquals("settlements-20260926-200600.json", kept.get(0).getFileName().toString());
        assertEquals("save 6", Files.readString(kept.get(0)));
        assertEquals("save 2", Files.readString(kept.get(4)));
    }

    @Test
    void leavesUnrelatedFilesAlone() throws IOException {
        Path save = dataFolder.resolve("settlements.json");
        Path backups = dataFolder.resolve("backups");
        Files.createDirectories(backups);
        Path mine = Files.writeString(backups.resolve("settlements-keep-me.json"), "hand-made");
        Files.writeString(save, "data");

        for (int i = 0; i < 3; i++) {
            SaveBackups.backup(save, backups, 1, Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i));
        }
        assertTrue(Files.exists(mine));
        assertEquals(1, SaveBackups.list(save, backups).size());
    }
}
