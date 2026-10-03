package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.Appearance;
import io.github.skyeberhard.hamletfolk.core.Building;
import io.github.skyeberhard.hamletfolk.core.BuildingType;
import io.github.skyeberhard.hamletfolk.core.HistoryEvent;
import io.github.skyeberhard.hamletfolk.core.Occupation;
import io.github.skyeberhard.hamletfolk.core.PriceModel;
import io.github.skyeberhard.hamletfolk.core.Resident;
import io.github.skyeberhard.hamletfolk.core.ResourceType;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.SettlementRegistry;
import io.github.skyeberhard.hamletfolk.core.SettlementSimulator;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Connects the simulation core to live villager entities. Main thread only. */
final class SettlementService {
    private static final long TICKS_PER_DAY = 24_000L;

    private final HamletfolkPlugin plugin;
    private final SettlementRegistry registry;
    private final HamletfolkConfig config;
    private final SettlementSimulator simulator;
    /** Parents of villagers that were just bred but haven't been added to the world yet. */
    private final Map<UUID, UUID[]> pendingParents = new HashMap<>();

    SettlementService(HamletfolkPlugin plugin, SettlementRegistry registry, HamletfolkConfig config) {
        this.plugin = plugin;
        this.registry = registry;
        this.config = config;
        this.simulator = SettlementSimulator.configured(config.oldAgeDeaths(), config.toolPenalty());
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

    /**
     * R3.1: as a trade window opens, adds to each trade's price a change from the settlement's stores
     * (see {@code PriceModel}). The game has already added its reputation and Hero of the Village
     * discounts, and clears the lot when the window closes, so this never compounds. Does nothing
     * for a villager outside any settlement.
     */
    void applyTradePrices(Villager villager, java.util.UUID player) {
        if (!config.pricesFollowSupply()) {
            return;
        }
        Resident resident = track(villager);
        if (resident == null) {
            return;
        }
        Settlement settlement = registry.settlementOf(resident.id()).orElse(null);
        if (settlement == null) {
            return;
        }
        simulate(settlement);
        if (settlement.resident(resident.id()).isEmpty()) {
            return; // they died of old age just now (R4.15)
        }
        // getRecipes() wraps the villager's live offers, so changing a recipe changes the trade the player sees.
        for (MerchantRecipe recipe : villager.getRecipes()) {
            List<ItemStack> ingredients = recipe.getIngredients();
            if (ingredients.isEmpty()) {
                continue;
            }
            ItemStack cost = ingredients.get(0);
            int delta = PriceModel.specialPriceDelta(settlement, recipe.getResult().getType().getKey().getKey(),
                    cost.getType().getKey().getKey(), cost.getAmount(), settlement.reputationOf(player));
            if (delta != 0) {
                recipe.setSpecialPrice(recipe.getSpecialPrice() + delta);
            }
        }
    }

    /** R2.1: registers the building a sign marks. */
    Settlement.Registration registerBuilding(Settlement settlement, BuildingType type, Location sign, String by) {
        long day = settlement.effectiveDay(day(sign.getWorld()));
        // A sign belongs to one settlement: if another one (an older one, say) has it registered, that is dropped.
        registry.settlementWithBuildingAt(sign.getWorld().getName(), sign.getBlockX(), sign.getBlockY(), sign.getBlockZ())
                .filter(other -> other != settlement)
                .ifPresent(other -> other.removeBuilding(sign.getBlockX(), sign.getBlockY(), sign.getBlockZ(), day));
        return settlement.registerBuilding(new Building(type, sign.getBlockX(), sign.getBlockY(), sign.getBlockZ(), day, by));
    }

    /** R2.1: removes whatever building is registered at a sign position, from whichever settlement has it. */
    Optional<Building> removeBuildingAt(World world, int x, int y, int z) {
        return registry.settlementWithBuildingAt(world.getName(), x, y, z)
                .flatMap(s -> s.removeBuilding(x, y, z, s.effectiveDay(day(world))));
    }

    /**
     * R2.1: drops registered buildings whose sign is gone (destroyed by physics, an explosion, a piston or an
     * editing tool, none of which fire a break event). Only looks in chunks that are loaded.
     */
    private void pruneBuildings(Settlement settlement, World world) {
        boolean pruned = false;
        for (Building building : new ArrayList<>(settlement.buildings())) {
            if (!world.isChunkLoaded(building.x() >> 4, building.z() >> 4)) {
                continue;
            }
            if (!Tag.ALL_SIGNS.isTagged(world.getBlockAt(building.x(), building.y(), building.z()).getType())) {
                settlement.removeBuilding(building.x(), building.y(), building.z(), settlement.effectiveDay(day(world)));
                pruned = true;
            }
        }
        if (pruned) {
            plugin.requestSave();
        }
    }

    /** R3.3: counts donated units toward the settlement's open request and returns the emeralds owed. */
    int fulfilRequest(Settlement settlement, ResourceType type, int units, long day, String donor, int maxPayout) {
        return simulator.fulfil(settlement, type, units, day, donor, maxPayout);
    }

    /** Brings a settlement's simulation up to the current day of its world. */
    void simulate(Settlement settlement) {
        World world = Bukkit.getWorld(settlement.world());
        if (world != null && inScope(world)) {
            simulator.simulateTo(settlement, day(world), config.maxCatchUpDays());
            // R4.15: residents who died of old age leave the record; remove their villagers if loaded.
            // Any that are unloaded are removed by track() when they load.
            for (UUID id : registry.reapDeparted(settlement)) {
                if (Bukkit.getEntity(id) instanceof Villager departed) {
                    departed.remove();
                }
            }
        }
    }

    void simulateAll() {
        for (Settlement settlement : registry.settlements()) {
            simulate(settlement);
            World bedWorld = Bukkit.getWorld(settlement.world());
            if (bedWorld != null && inScope(bedWorld)) {
                scanBedsIfDue(settlement, bedWorld);
            }
            considerNewcomer(settlement);
            World world = Bukkit.getWorld(settlement.world());
            if (world != null && inScope(world)) {
                refreshAppearance(settlement, world);
                pruneBuildings(settlement, world);
            }
        }
    }

    /**
     * R4.1: with a food surplus and a free bed, a new villager turns up. Free beds come from the saved
     * housing count (R2.2), which is only refreshed from loaded chunks, so it can be slightly out of date
     * (a bed broken just before its chunk unloaded) and, where two settlements' areas overlap, counts a
     * shared bed for both. The arrival is recorded in history when the new villager is enrolled.
     */
    private void considerNewcomer(Settlement settlement) {
        World world = Bukkit.getWorld(settlement.world());
        if (world == null || !inScope(world) || settlement.isAbandoned()) {
            return;
        }
        if (!simulator.newcomerDue(settlement, settlement.freeBeds())) {
            return;
        }
        int x = settlement.centerX();
        int z = settlement.centerZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return;
        }
        // Leaves are skipped so a settlement centre under a tree doesn't put the newcomer in the canopy.
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        Villager newcomer = world.spawn(new Location(world, x + 0.5, y, z + 0.5), Villager.class);
        simulator.newcomerArrived(settlement);
        // Enroll now rather than wait for the add-to-world event, so the arrival is recorded and
        // counted in the population before the next check can spawn another.
        if (newcomer.isValid()) {
            track(newcomer);
        }
    }

    /** How often a settlement's beds are recounted while the server runs: beds change rarely and the scan is not free. */
    private static final long BED_SCAN_INTERVAL_MS = 60_000;
    private final Map<UUID, Long> lastBedScan = new HashMap<>();

    /** R2.2: recounts beds if it has been a minute. */
    private void scanBedsIfDue(Settlement settlement, World world) {
        long now = System.currentTimeMillis();
        Long last = lastBedScan.get(settlement.id());
        if (last == null || now - last >= BED_SCAN_INTERVAL_MS) {
            lastBedScan.put(settlement.id(), now);
            scanBeds(settlement, world);
        }
    }

    /** R2.2: recounts beds now (for a player asking), in whichever of the settlement's chunks are loaded. */
    void refreshHousing(Settlement settlement) {
        World world = Bukkit.getWorld(settlement.world());
        long now = System.currentTimeMillis();
        Long last = lastBedScan.get(settlement.id());
        if (world != null && inScope(world) && (last == null || now - last >= 2_000)) { // a player spamming the command costs one scan per 2 s
            lastBedScan.put(settlement.id(), now);
            scanBeds(settlement, world);
        }
    }

    /**
     * R2.2: counts beds chunk by chunk in the loaded chunks within the settlement radius. A chunk that
     * is not loaded keeps its last known count, so housing does not vanish when nobody is nearby.
     */
    private void scanBeds(Settlement settlement, World world) {
        int radius = config.settlementRadius();
        long radiusSquared = (long) radius * radius;
        for (int cx = (settlement.centerX() - radius) >> 4; cx <= (settlement.centerX() + radius) >> 4; cx++) {
            for (int cz = (settlement.centerZ() - radius) >> 4; cz <= (settlement.centerZ() + radius) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                int beds = 0;
                // Each bed is two blocks; count only the head so a bed counts once.
                for (BlockState state : world.getChunkAt(cx, cz).getTileEntities(
                        block -> block.getBlockData() instanceof Bed bed && bed.getPart() == Bed.Part.HEAD, false)) {
                    if (settlement.distanceSquared(state.getX(), state.getZ()) <= radiusSquared) {
                        beds++;
                    }
                }
                settlement.housing().setChunk(cx, cz, beds);
            }
        }
        // A smaller radius than before must not leave counts for chunks that are no longer in range.
        settlement.housing().retainWithin((settlement.centerX() - radius) >> 4, (settlement.centerX() + radius) >> 4,
                (settlement.centerZ() - radius) >> 4, (settlement.centerZ() + radius) >> 4);
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
        if (registry.isDeparted(villager.getUniqueId())) {
            villager.remove(); // R4.15: died of old age while unloaded; don't enroll them again as a stranger
            return null;
        }
        Resident resident = registry.resident(villager.getUniqueId()).orElse(null);
        if (resident == null) {
            resident = enroll(villager);
        }
        // R4.3: the simulation owns the occupation; the vanilla profession only fills in a missing one.
        resident.seedOccupation(occupationOf(villager));
        resident.setAdult(villager.isAdult());
        if (config.showNames() && villager.customName() == null) {
            villager.customName(Component.text(resident.fullName()));
            villager.setCustomNameVisible(false);
        }
        long today = registry.settlementOf(resident.id()).map(s -> s.effectiveDay(day(villager.getWorld())))
                .orElse(day(villager.getWorld()));
        applyAppearance(villager, resident, today);
        return resident;
    }

    /**
     * R4.16: stores the resident's gender, occupation and life stage on the villager for resource
     * packs and client mods, and, if configured, sets the vanilla villager type that stands for
     * gender and life stage. Only writes what changed.
     */
    void applyAppearance(Villager villager, Resident resident, long day) {
        PersistentDataContainer data = villager.getPersistentDataContainer();
        Appearance.tags(resident, day).forEach((name, value) -> {
            NamespacedKey key = new NamespacedKey(plugin, name);
            if (!value.equals(data.get(key, PersistentDataType.STRING))) {
                data.set(key, PersistentDataType.STRING, value);
            }
        });
        NamespacedKey originalKey = new NamespacedKey(plugin, "original_type");
        String original = data.get(originalKey, PersistentDataType.STRING);
        if (config.appearanceTypes()) {
            if (original == null) {
                // Remember the biome look once, so turning the setting off can put it back.
                data.set(originalKey, PersistentDataType.STRING, villager.getVillagerType().getKey().getKey());
            }
            Villager.Type type = villagerType(Appearance.villagerType(resident.gender(), resident.stage(day)));
            if (!type.equals(villager.getVillagerType())) {
                villager.setVillagerType(type);
            }
        } else if (original != null) {
            villager.setVillagerType(villagerType(original));
            data.remove(originalKey);
        }
    }

    private static Villager.Type villagerType(String key) {
        return switch (key) {
            case "desert" -> Villager.Type.DESERT;
            case "jungle" -> Villager.Type.JUNGLE;
            case "snow" -> Villager.Type.SNOW;
            case "swamp" -> Villager.Type.SWAMP;
            case "taiga" -> Villager.Type.TAIGA;
            case "savanna" -> Villager.Type.SAVANNA;
            default -> Villager.Type.PLAINS;
        };
    }

    /** Brings loaded residents' appearance up to date: a resident may have become an elder since last tracked. */
    private void refreshAppearance(Settlement settlement, World world) {
        long day = settlement.effectiveDay(day(world));
        for (Resident resident : settlement.residents()) {
            if (Bukkit.getEntity(resident.id()) instanceof Villager villager) {
                resident.setAdult(villager.isAdult()); // a child may have grown up since last tracked
                applyAppearance(villager, resident, day);
            }
        }
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
