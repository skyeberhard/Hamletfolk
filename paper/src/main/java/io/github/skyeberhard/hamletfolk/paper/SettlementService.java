package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.HistoryEvent;
import io.github.skyeberhard.hamletfolk.core.Occupation;
import io.github.skyeberhard.hamletfolk.core.Resident;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.SettlementRegistry;
import io.github.skyeberhard.hamletfolk.core.SettlementSimulator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Villager;

/** Connects the simulation core to live villager entities. Main thread only. */
final class SettlementService {
    private static final long TICKS_PER_DAY = 24_000L;

    private final HamletfolkPlugin plugin;
    private final SettlementRegistry registry;
    private final HamletfolkConfig config;
    private final SettlementSimulator simulator = new SettlementSimulator();
    /** Parents of villagers that were just bred but haven't been added to the world yet. */
    private final Map<UUID, UUID[]> pendingParents = new HashMap<>();

    SettlementService(HamletfolkPlugin plugin, SettlementRegistry registry, HamletfolkConfig config) {
        this.plugin = plugin;
        this.registry = registry;
        this.config = config;
    }

    SettlementRegistry registry() {
        return registry;
    }

    HamletfolkPlugin plugin() {
        return plugin;
    }

    static long day(World world) {
        return world.getFullTime() / TICKS_PER_DAY;
    }

    /** R1.10: false for a world the config's allow/deny lists exclude; nothing there is tracked or simulated. */
    boolean inScope(World world) {
        return config.worlds().accepts(world.getName());
    }

    Optional<Settlement> settlementAt(Location location) {
        if (!inScope(location.getWorld())) {
            return Optional.empty();
        }
        return registry.nearest(location.getWorld().getName(), location.getBlockX(), location.getBlockZ(),
                config.settlementRadius());
    }

    /** Brings a settlement's simulation up to the current day of its world. */
    void simulate(Settlement settlement) {
        World world = Bukkit.getWorld(settlement.world());
        if (world != null && inScope(world)) {
            simulator.simulateTo(settlement, day(world), config.maxCatchUpDays());
        }
    }

    void simulateAll() {
        for (Settlement settlement : registry.settlements()) {
            simulate(settlement);
        }
    }

    void trackLoadedVillagers() {
        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                track(villager);
            }
        }
    }

    void expectBirth(UUID child, UUID parentA, UUID parentB) {
        pendingParents.put(child, new UUID[] {parentA, parentB});
    }

    /**
     * Returns the villager's resident record, enrolling them in a settlement if they're new,
     * or null if their world is excluded by the config (R1.10).
     */
    Resident track(Villager villager) {
        if (!inScope(villager.getWorld())) {
            return null;
        }
        Resident resident = registry.resident(villager.getUniqueId()).orElse(null);
        if (resident == null) {
            resident = enroll(villager);
        }
        resident.setOccupation(occupationOf(villager));
        resident.setAdult(villager.isAdult());
        if (config.showNames() && villager.customName() == null) {
            villager.customName(Component.text(resident.fullName()));
            villager.setCustomNameVisible(false);
        }
        return resident;
    }

    private Resident enroll(Villager villager) {
        Location location = villager.getLocation();
        long today = day(villager.getWorld());
        Settlement settlement = settlementAt(location).orElseGet(() -> registry.found(
                villager.getWorld().getName(), location.getBlockX(), location.getBlockZ(), today));
        // A settlement that is behind would otherwise simulate the newcomer through days they weren't there.
        simulate(settlement);
        // R1.23: if the world clock went backwards, go by the settlement's own day.
        long day = settlement.effectiveDay(today);

        UUID[] parents = pendingParents.remove(villager.getUniqueId());
        Resident resident = registry.enroll(settlement, villager.getUniqueId(), occupationOf(villager),
                villager.isAdult(), day, parents == null ? null : parents[0], parents == null ? null : parents[1]);

        if (parents != null) {
            settlement.record(day, HistoryEvent.Kind.BIRTH, resident.fullName() + " was born to "
                    + nameOf(parents[0]) + " and " + nameOf(parents[1]) + ".");
        } else if (day > settlement.foundedDay()) {
            // Villagers present when a settlement is first found are its founders, not arrivals.
            settlement.record(day, HistoryEvent.Kind.ARRIVAL,
                    resident.fullName() + " settled in " + settlement.name() + ".");
        }
        return resident;
    }

    private String nameOf(UUID residentId) {
        return registry.resident(residentId).map(Resident::fullName).orElse("a traveller");
    }

    static Occupation occupationOf(Villager villager) {
        return Occupation.fromVanillaKey(villager.getProfession().getKey().getKey());
    }
}
