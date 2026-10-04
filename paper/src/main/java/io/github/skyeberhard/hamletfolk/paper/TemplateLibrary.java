package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.BiomeSet;
import io.github.skyeberhard.hamletfolk.core.Blueprint;
import io.github.skyeberhard.hamletfolk.core.BuildingType;
import io.github.skyeberhard.hamletfolk.core.TemplateCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.structure.Palette;
import org.bukkit.structure.Structure;

/**
 * R4.6, R4.18: the Paper side of the building templates. It owns the core {@link TemplateCatalog}, loads captured
 * buildings from {@code plugins/Hamletfolk/templates/}, checks at startup that every vanilla piece the catalog names
 * exists on the server, reads vanilla pieces and world areas into {@link Blueprint}s, and (for now, as an admin
 * tool; construction over time is R4.8) places one.
 */
final class TemplateLibrary {
    /** The biggest area a capture may cover, along any side and in total. */
    static final int MAX_SIDE = 32;
    static final int MAX_VOLUME = 16_000;

    private final HamletfolkPlugin plugin;
    private final TemplateCatalog catalog = new TemplateCatalog();
    private final Path directory;

    TemplateLibrary(HamletfolkPlugin plugin) {
        this.plugin = plugin;
        this.directory = plugin.getDataFolder().toPath().resolve("templates");
    }

    TemplateCatalog catalog() {
        return catalog;
    }

    /** Reads every captured template file; a bad file is logged and skipped, never fatal. */
    void load() {
        if (!Files.isDirectory(directory)) {
            return;
        }
        int loaded = 0;
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".txt")).toList()) {
                String name = file.getFileName().toString();
                String[] part = name.substring(0, name.length() - 4).split("_");
                try {
                    // type_style_tier, where a type may itself contain an underscore (GUARD_POST).
                    int n = part.length;
                    BuildingType type = BuildingType.valueOf(String.join("_", java.util.Arrays.copyOfRange(part, 0, n - 2))
                            .toUpperCase(Locale.ROOT));
                    if (!BiomeSet.ALL.contains(part[n - 2])) {
                        throw new IllegalArgumentException("unknown style " + part[n - 2]);
                    }
                    catalog.capture(type, part[n - 2], Integer.parseInt(part[n - 1]),
                            Blueprint.fromText(Files.readString(file, StandardCharsets.UTF_8)));
                    loaded++;
                } catch (IOException | RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Skipping template file " + name + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the templates folder", e);
        }
        if (loaded > 0) {
            plugin.getLogger().info("Loaded " + loaded + " captured building templates.");
        }
    }

    /** Saves a capture to disk and makes it the template from now on. */
    void capture(BuildingType type, String style, int tier, Blueprint blueprint) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(file(type, style, tier), blueprint.toText(), StandardCharsets.UTF_8);
        catalog.capture(type, style, tier, blueprint);
    }

    /** Deletes a capture, so the generated or vanilla template is used again. */
    boolean remove(BuildingType type, String style, int tier) throws IOException {
        boolean had = catalog.uncapture(type, style, tier);
        return Files.deleteIfExists(file(type, style, tier)) || had;
    }

    private Path file(BuildingType type, String style, int tier) {
        return directory.resolve(type.name().toLowerCase(Locale.ROOT) + "_" + BiomeSet.normalize(style) + "_" + tier + ".txt");
    }

    /** Logs every vanilla template key the catalog names that this server cannot load (R4.6's startup check). */
    void verifyVanilla() {
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (BuildingType type : BuildingType.values()) {
            for (String style : BiomeSet.ALL) {
                for (TemplateCatalog.Template rung : catalog.ladder(type, style)) {
                    if (rung.source() != TemplateCatalog.Source.VANILLA) {
                        continue;
                    }
                    checked++;
                    if (loadStructure(rung.key()) == null) {
                        missing.add(rung.key());
                    }
                }
            }
        }
        if (missing.isEmpty()) {
            plugin.getLogger().info("Template check: all " + checked + " vanilla building pieces found.");
        } else {
            plugin.getLogger().warning("Template check: " + missing.size() + " of " + checked
                    + " vanilla building pieces are missing: " + String.join(", ", missing));
        }
    }

    private static Structure loadStructure(String key) {
        NamespacedKey id = NamespacedKey.fromString(key);
        return id == null ? null : Bukkit.getStructureManager().loadStructure(id);
    }

    /** The blocks of a rung: generated and captured ones from the catalog, vanilla ones read from the server. */
    Optional<Blueprint> blueprint(TemplateCatalog.Template template) {
        Optional<Blueprint> own = catalog.blueprint(template);
        if (own.isPresent() || template.source() != TemplateCatalog.Source.VANILLA) {
            return own;
        }
        Structure structure = loadStructure(template.key());
        if (structure == null || structure.getPalettes().isEmpty()) {
            return Optional.empty();
        }
        Palette palette = structure.getPalettes().get(0);
        List<Blueprint.Block> blocks = new ArrayList<>();
        for (BlockState state : palette.getBlocks()) {
            Material type = state.getType();
            if (type.isAir() || type == Material.JIGSAW || type == Material.STRUCTURE_VOID
                    || type == Material.STRUCTURE_BLOCK) {
                continue; // a jigsaw is where the game joins pieces, not part of the building
            }
            blocks.add(new Blueprint.Block(state.getX(), state.getY(), state.getZ(), materialOf(state.getBlockData())));
        }
        return Optional.of(new Blueprint(template.key(), Math.max(1, structure.getSize().getBlockX()),
                Math.max(1, structure.getSize().getBlockY()), Math.max(1, structure.getSize().getBlockZ()), blocks));
    }

    /** "minecraft:ladder[facing=south]" as the core's "LADDER[facing=south]". */
    static String materialOf(BlockData data) {
        String text = data.getAsString();
        if (text.startsWith("minecraft:")) {
            text = text.substring("minecraft:".length());
        }
        int bracket = text.indexOf('[');
        return bracket < 0 ? text.toUpperCase(Locale.ROOT)
                : text.substring(0, bracket).toUpperCase(Locale.ROOT) + text.substring(bracket);
    }

    static BlockData dataOf(String material) {
        return Bukkit.createBlockData(material.toLowerCase(Locale.ROOT));
    }

    /**
     * Reads a box of the world into a blueprint. The floor is {@code floorY}: blocks below it get negative heights
     * (a shaft). Air is left out, so putting the building down never clears anything.
     */
    static Blueprint read(World world, int x1, int y1, int z1, int x2, int y2, int z2, int floorY, String key) {
        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        int maxZ = Math.max(z1, z2);
        List<Blueprint.Block> blocks = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) {
                        blocks.add(new Blueprint.Block(x - minX, y - floorY, z - minZ, materialOf(block.getBlockData())));
                    }
                }
            }
        }
        return new Blueprint(key, maxX - minX + 1, maxY - Math.min(minY, floorY) + 1, maxZ - minZ + 1, blocks);
    }

    /** Puts a blueprint in the world with its floor-level corner of smallest x and z at {@code origin}. Main thread. */
    static int place(Location origin, Blueprint blueprint) {
        World world = origin.getWorld();
        int placed = 0;
        for (Blueprint.Block block : blueprint.blocks()) {
            try {
                world.getBlockAt(origin.getBlockX() + block.x(), origin.getBlockY() + block.y(),
                        origin.getBlockZ() + block.z()).setBlockData(dataOf(block.material()), false);
                placed++;
            } catch (IllegalArgumentException e) {
                // A material this server does not know: leave a gap rather than stop half way.
            }
        }
        return placed;
    }
}
