package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.SaveBackups;
import io.github.skyeberhard.hamletfolk.core.Resident;
import io.github.skyeberhard.hamletfolk.core.SettlementRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class HamletfolkPlugin extends JavaPlugin {
    private static final long SIMULATION_PERIOD_TICKS = 100;
    private static final long SAVE_REQUEST_DELAY_TICKS = 40;
    private static final long TICKS_PER_HOUR = 60L * 60 * 20;

    private SettlementStore store;
    private SettlementService service;
    private TemplateLibrary templates;
    private ConstructionService construction;
    private boolean saveRequested;
    private Path saveFile;
    private Path backupDirectory;
    private int backupsKept;

    /** R4.30: lays the server's dialogue/ folder over the built-in lines; returns what was wrong with it (also logged). */
    java.util.List<String> loadDialogue() {
        io.github.skyeberhard.hamletfolk.core.Lines lines = io.github.skyeberhard.hamletfolk.core.Lines.withOverrides(
                getDataFolder().toPath().resolve("dialogue"));
        io.github.skyeberhard.hamletfolk.core.Dialogue.setLibrary(lines);
        lines.problems().forEach(problem -> getLogger().warning("dialogue: " + problem));
        return lines.problems();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Before any save is loaded: ages only mean something against the lifespan in force (R4.15).
        Resident.setLifespanScale(getConfig().getDouble("aging.lifespan-scale", HamletfolkConfig.DEFAULT_LIFESPAN_SCALE));
        saveFile = getDataFolder().toPath().resolve("settlements.json");
        backupDirectory = getDataFolder().toPath().resolve("backups");
        backupsKept = Math.max(1, getConfig().getInt("backups.keep", 5));
        store = new SettlementStore(saveFile);
        try {
            SaveBackups.backup(saveFile, backupDirectory, backupsKept, Instant.now())
                    .ifPresent(backup -> getLogger().info("Backed up settlements to " + backup.getFileName()));
        } catch (IOException e) {
            // A failed backup shouldn't stop the plugin, but it should be loud.
            getLogger().log(Level.WARNING, "Could not back up settlements.json", e);
        }

        SettlementRegistry registry;
        try {
            registry = store.load();
        } catch (IOException | RuntimeException e) {
            // Refuse to start rather than overwrite a save we couldn't read.
            getLogger().log(Level.SEVERE, "Could not read settlements.json; disabling to protect the data. "
                    + "Recent copies are in the backups/ folder next to it.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        io.github.skyeberhard.hamletfolk.core.Construction.setCostPercent(getConfig().getInt("construction.cost-percent", 35));
        templates = new TemplateLibrary(this);
        templates.load();
        loadDialogue(); // R4.30
        service = new SettlementService(this, registry, HamletfolkConfig.from(getConfig()));
        getServer().getPluginManager().registerEvents(new VillagerListener(this, service), this);
        getServer().getPluginManager().registerEvents(new RaidListener(service), this);
        getServer().getPluginManager().registerEvents(new BuildingListener(service), this);
        getServer().getPluginManager().registerEvents(new IgnoreListener(service), this);

        SettlementCommand command = new SettlementCommand(service);
        PluginCommand settlement = Objects.requireNonNull(getCommand("settlement"));
        settlement.setExecutor(command);
        settlement.setTabCompleter(command);

        // Structures are only loadable once the server has finished starting.
        getServer().getScheduler().runTask(this, templates::verifyVanilla);
        construction = new ConstructionService(this, service);
        construction.start();
        new WorldMarksService(this, service, construction).start(); // R4.25
        service.trackLoadedVillagers();
        getServer().getScheduler().runTaskTimer(this, service::simulateAll, SIMULATION_PERIOD_TICKS, SIMULATION_PERIOD_TICKS);
        service.startFastForward(); // R4.27

        long autosaveTicks = Math.max(1, getConfig().getLong("autosave-minutes", 5)) * 60 * 20;
        getServer().getScheduler().runTaskTimer(this, this::saveAsync, autosaveTicks, autosaveTicks);

        // R1.19: a server that runs for weeks between restarts still gets fresh backups.
        double backupHours = getConfig().getDouble("backups.interval-hours", 24);
        if (backupHours > 0) {
            long backupTicks = Math.max(1, Math.round(backupHours * TICKS_PER_HOUR));
            getServer().getScheduler().runTaskTimer(this, this::backupAsync, backupTicks, backupTicks);
        }

        getLogger().info("Tracking " + registry.settlements().size() + " settlements.");
    }

    TemplateLibrary templates() {
        return templates;
    }

    @Override
    public void onDisable() {
        if (construction != null) {
            construction.releaseAll(); // R4.22: chunks held for unattended building
        }
        if (service == null) {
            return;
        }
        try {
            store.write(store.snapshot(service.registry()));
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to save settlements", e);
        }
    }

    /**
     * R1.18: save within a couple of seconds, for changes that shouldn't wait for the next
     * autosave (e.g. a donation, where the items have already left the player). Several
     * requests in quick succession cause one save. Main thread only.
     */
    void requestSave() {
        if (saveRequested) {
            return;
        }
        saveRequested = true;
        getServer().getScheduler().runTaskLater(this, () -> {
            saveRequested = false;
            saveAsync();
        }, SAVE_REQUEST_DELAY_TICKS);
    }

    /** Saves on the calling (main) thread and reports whether it worked, for `/settlement admin save`. */
    boolean saveNow() {
        try {
            store.write(store.snapshot(service.registry()));
            return true;
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to save settlements", e);
            return false;
        }
    }

    /** Saves the current state, then copies the save file into backups/ with the usual rotation. */
    private void backupAsync() {
        SettlementStore.Snapshot snapshot = store.snapshot(service.registry());
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                store.write(snapshot); // Skipped if a newer save already landed; either way the file is current.
                store.backup(backupDirectory, backupsKept)
                        .ifPresent(backup -> getLogger().info("Backed up settlements to " + backup.getFileName()));
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Could not back up settlements.json", e);
            }
        });
    }

    private void saveAsync() {
        // Snapshot on the main thread, where the data is safe to read; write the file off it.
        SettlementStore.Snapshot snapshot = store.snapshot(service.registry());
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                store.write(snapshot);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to save settlements", e);
            }
        });
    }
}
