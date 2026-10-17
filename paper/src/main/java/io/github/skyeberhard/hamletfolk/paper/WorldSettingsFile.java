package io.github.skyeberhard.hamletfolk.paper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.skyeberhard.hamletfolk.core.WorldSettings;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/** R1.46: reads and writes the per-world settings ({@link WorldSettings}) in a small file of their own, apart from the villages' save. */
final class WorldSettingsFile {
    private static final Type MAP = new TypeToken<Map<String, Object>>() { }.getType();

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    WorldSettingsFile(Path file) {
        this.file = file;
    }

    /** What was saved, or the defaults if there is no file or it cannot be read (the file is left alone, so nothing is lost to a typo). */
    WorldSettings load() throws IOException {
        if (!Files.exists(file)) {
            return new WorldSettings();
        }
        Map<String, Object> raw = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP);
        return WorldSettings.fromMap(raw);
    }

    /** Writes the settings through a temporary file, so a crash never leaves half a file. */
    void save(WorldSettings settings) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, gson.toJson(settings.toMap()), StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
