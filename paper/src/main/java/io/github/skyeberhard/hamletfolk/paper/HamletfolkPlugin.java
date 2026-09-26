package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.SettlementRegistry;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class HamletfolkPlugin extends JavaPlugin {
    private static final long SIMULATION_PERIOD_TICKS = 100;

    private SettlementStore store;
    private SettlementService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new SettlementStore(getDataFolder().toPath().resolve("settlements.json"));

        SettlementRegistry registry;
        try {
            registry = store.load();
        } catch (IOException | RuntimeException e) {
            // Refuse to start rather than overwrite a save we couldn't read.
            getLogger().log(Level.SEVERE, "Could not read settlements.json; disabling to protect the data", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        service = new SettlementService(this, registry, HamletfolkConfig.from(getConfig()));
        getServer().getPluginManager().registerEvents(new VillagerListener(this, service), this);
        getServer().getPluginManager().registerEvents(new RaidListener(service), this);

        SettlementCommand command = new SettlementCommand(service);
        PluginCommand settlement = Objects.requireNonNull(getCommand("settlement"));
        settlement.setExecutor(command);
        settlement.setTabCompleter(command);

        service.trackLoadedVillagers();
        getServer().getScheduler().runTaskTimer(this, service::simulateAll, SIMULATION_PERIOD_TICKS, SIMULATION_PERIOD_TICKS);

        long autosaveTicks = Math.max(1, getConfig().getLong("autosave-minutes", 5)) * 60 * 20;
        getServer().getScheduler().runTaskTimer(this, this::saveAsync, autosaveTicks, autosaveTicks);

        getLogger().info("Tracking " + registry.settlements().size() + " settlements.");
    }

    @Override
    public void onDisable() {
        if (service == null) {
            return;
        }
        try {
            store.write(store.serialize(service.registry()));
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to save settlements", e);
        }
    }

    private void saveAsync() {
        // Snapshot on the main thread, where the data is safe to read; write the file off it.
        String json = store.serialize(service.registry());
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                store.write(json);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to save settlements", e);
            }
        });
    }
}
