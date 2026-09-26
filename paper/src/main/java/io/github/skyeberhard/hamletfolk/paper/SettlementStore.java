package io.github.skyeberhard.hamletfolk.paper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.SettlementCodec;
import io.github.skyeberhard.hamletfolk.core.SettlementRegistry;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads and writes all settlements as one JSON file in the plugin's data folder. */
final class SettlementStore {
    private static final Type LIST_OF_MAPS = new TypeToken<List<Map<String, Object>>>() { }.getType();

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    SettlementStore(Path file) {
        this.file = file;
    }

    SettlementRegistry load() throws IOException {
        SettlementRegistry registry = new SettlementRegistry();
        if (!Files.exists(file)) {
            return registry;
        }
        List<Map<String, Object>> entries = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), LIST_OF_MAPS);
        if (entries != null) {
            for (Map<String, Object> entry : entries) {
                registry.add(SettlementCodec.decode(entry));
            }
        }
        return registry;
    }

    /** Must run on the main thread: it reads live settlement data. */
    String serialize(SettlementRegistry registry) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Settlement settlement : registry.settlements()) {
            entries.add(SettlementCodec.encode(settlement));
        }
        return gson.toJson(entries);
    }

    /** Safe to call off the main thread. Writes to a temp file first so a crash can't truncate the save. */
    synchronized void write(String json) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
