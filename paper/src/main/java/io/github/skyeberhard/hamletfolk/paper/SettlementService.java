package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.Appearance;
import io.github.skyeberhard.hamletfolk.core.Building;
import io.github.skyeberhard.hamletfolk.core.BuildingType;
import io.github.skyeberhard.hamletfolk.core.HistoryEvent;
import io.github.skyeberhard.hamletfolk.core.Occupation;
import io.github.skyeberhard.hamletfolk.core.PriceModel;
import io.github.skyeberhard.hamletfolk.core.Resident;
import io.github.skyeberhard.hamletfolk.core.ResourceType;
import io.github.skyeberhard.hamletfolk.core.HeightSource;
import io.github.skyeberhard.hamletfolk.core.IgnoreZones;
import io.github.skyeberhard.hamletfolk.core.Membership;
import io.github.skyeberhard.hamletfolk.core.Migration;
import io.github.skyeberhard.hamletfolk.core.PlanGenerator;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.Trading;
import io.github.skyeberhard.hamletfolk.core.VillagePlan;
import io.github.skyeberhard.hamletfolk.core.SettlementRegistry;
import io.github.skyeberhard.hamletfolk.core.SettlementSimulator;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import io.papermc.paper.entity.poi.PoiSearchResult;
import io.papermc.paper.entity.poi.PoiType;
import io.papermc.paper.entity.poi.PoiTypes;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
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
    /** R4.2: residents whose villager is being teleported right now, so it is not started twice. */
    private final java.util.Set<UUID> movesInFlight = new java.util.HashSet<>();

    SettlementService(HamletfolkPlugin plugin, SettlementRegistry registry, HamletfolkConfig config) {
        this.plugin = plugin;
        this.registry = registry;
        this.config = config;
        this.simulator = SettlementSimulator.configured(config.oldAgeDeaths(), config.toolPenalty(), config.treasuryBase());
    }

    /** The radius of a settlement's area, in blocks, from the config. */
    int settlementRadius() {
        return config.settlementRadius();
    }

    /** R2.6: the most emeralds this settlement can bank. */
    int treasuryLimit(Settlement settlement) {
        return simulator.treasuryLimit(settlement);
    }

    /** R2.6: how many more emeralds this settlement can bank. */
    int treasuryRoom(Settlement settlement) {
        return simulator.treasuryRoom(settlement);
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
     * As a trade window opens: R3.1 adds to each trade's price a change from the settlement's stores and
     * R3.4 the player's standing (see {@code PriceModel}).
     * The game has already added its reputation and Hero of the Village discounts, and clears the lot when
     * the window closes, so the price change never compounds. Does nothing for a villager outside any settlement.
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

    /** The ground of a world as the plan sees it: surface height and water, only where chunks are loaded. */
    private static HeightSource terrainOf(World world) {
        return new HeightSource() {
            @Override
            public int height(int x, int z) {
                // Leaves are skipped, so a tree or a roof is not mistaken for the ground.
                return world.isChunkLoaded(x >> 4, z >> 4)
                        ? world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) : HeightSource.UNKNOWN;
            }

            @Override
            public boolean water(int x, int z) {
                // Water and lava are both not ground to build on; ice and lily pads are read as what is under them.
                return world.isChunkLoaded(x >> 4, z >> 4)
                        && world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES).isLiquid();
            }
        };
    }

    /** True if every chunk within {@code radius} blocks of a point is loaded, so the ground there can be measured. */
    private static boolean groundLoaded(World world, int cx, int cz, int radius) {
        for (int x = (cx - radius) >> 4; x <= (cx + radius) >> 4; x++) {
            for (int z = (cz - radius) >> 4; z <= (cz + radius) >> 4; z++) {
                if (!world.isChunkLoaded(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * R8.3: gives a village its plan of streets and lots once the ground around it is loaded, and lengthens it when the
     * village reaches {@link #STAGE_TWO_POPULATION}. Lots over bad ground are dropped for good, so this waits until the
     * whole area can be measured instead of planning from part of it.
     */
    private void ensurePlan(Settlement settlement) {
        World world = Bukkit.getWorld(settlement.world());
        if (world == null || !inScope(world) || settlement.isAbandoned() || settlement.population() == 0) {
            return;
        }
        VillagePlan plan = settlement.plan();
        if (plan != null && (plan.stage() >= 2 || settlement.population() < STAGE_TWO_POPULATION)) {
            return;
        }
        if (!groundLoaded(world, settlement.centerX(), settlement.centerZ(), 112)) {
            return;
        }
        if (plan == null && !PlanGenerator.siteUsable(terrainOf(world), settlement.centerX(), settlement.centerZ())) {
            return; // the centre is water or unmeasured: nothing to plan on
        }
        if (plan == null) {
            long seed = settlement.id().getMostSignificantBits() ^ settlement.id().getLeastSignificantBits();
            settlement.setPlan(PlanGenerator.generate(settlement.centerX(), settlement.centerZ(), seed, "plains", terrainOf(world)));
            plugin.requestSave();
        } else if (PlanGenerator.extend(plan, terrainOf(world))) {
            plugin.requestSave();
        }
    }

    /** The population at which a village's plan grows to its second stage. */
    static final int STAGE_TWO_POPULATION = 25;

    /**
     * R1.8: reports where each loaded villager of a settlement is, so one that has settled in another
     * settlement's area becomes a resident of it (the rule and the count of days are in core).
     */
    private void considerMembership(Settlement settlement) {
        if (!config.membershipFollows()) {
            return;
        }
        for (Resident resident : new ArrayList<>(settlement.residents())) {
            if (!(Bukkit.getEntity(resident.id()) instanceof Villager villager) || !villager.isValid()
                    || !inScope(villager.getWorld())) {
                continue;
            }
            Location at = villager.getLocation();
            long day = settlement.lastSimulatedDay(); // the settlement's own day, whichever world the villager is in
            if (Membership.observe(registry, resident, villager.getWorld().getName(), at.getBlockX(), at.getBlockZ(),
                    config.settlementRadius(), day).isPresent()) {
                plugin.requestSave();
            }
        }
    }

    /**
     * R4.2: lets an unemployed or unhappy resident leave for a better-off neighbour (the rule is in core).
     * The record moves at once; the villager is brought over now if it is loaded, else when it next loads.
     */
    private void considerMigration(Settlement settlement) {
        World world = Bukkit.getWorld(settlement.world());
        if (world == null || !inScope(world)) {
            return;
        }
        // Moves that could not be carried out yet (villager unloaded or busy) are tried again every round.
        for (UUID id : settlement.pendingMoves()) {
            if (Bukkit.getEntity(id) instanceof Villager villager) {
                registry.resident(id).ifPresent(resident -> completeMove(villager, resident));
            }
        }
        if (!config.migration()) {
            return;
        }
        Migration.run(registry, settlement, settlement.lastSimulatedDay()).ifPresent(move -> {
            plugin.requestSave();
            if (Bukkit.getEntity(move.resident().id()) instanceof Villager villager) {
                completeMove(villager, move.resident());
            }
        });
    }

    /**
     * R4.2: if this resident has moved on paper to another settlement, take their villager there. The
     * marker stays until the teleport has worked, so a villager that is busy (trading, leashed, riding)
     * or a teleport that fails is simply tried again later.
     */
    private void completeMove(Villager villager, Resident resident) {
        Settlement home = registry.settlementOf(resident.id()).orElse(null);
        String marker = Migration.MOVING + resident.id();
        if (home == null || !home.hasCondition(marker) || !villager.isValid() || isIgnored(villager)
                || villager.isTrading() || villager.isLeashed() || villager.isInsideVehicle()
                || !movesInFlight.add(resident.id())) {
            return;
        }
        World world = Bukkit.getWorld(home.world());
        if (world == null) {
            movesInFlight.remove(resident.id());
            return;
        }
        int x = home.centerX();
        int z = home.centerZ();
        if (zonesOf(world).coveringZone(x, world.getHighestBlockYAt(x, z), z).filter(IgnoreZones.Zone::overridesVillages).isPresent()) {
            movesInFlight.remove(resident.id());
            return; // the destination is inside an admin's exempt zone: nobody is moved into it
        }
        // Load the destination first (it is often far from anyone), then pick the spot: leaves are skipped so
        // a village centre under a tree does not put the villager in the canopy (as for newcomers).
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            if (!villager.isValid()) {
                movesInFlight.remove(resident.id());
                return;
            }
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            villager.teleportAsync(new Location(world, x + 0.5, y, z + 0.5)).thenAccept(moved -> {
                movesInFlight.remove(resident.id());
                if (moved) {
                    home.completeMove(resident.id(), home.lastSimulatedDay());
                    // Forget the old bed, workplace and meeting place so it does not walk back to them (R1.8 would
                    // then count it as straying home).
                    villager.setMemory(MemoryKey.HOME, null);
                    villager.setMemory(MemoryKey.JOB_SITE, null);
                    villager.setMemory(MemoryKey.POTENTIAL_JOB_SITE, null);
                    villager.setMemory(MemoryKey.MEETING_POINT, null);
                    plugin.requestSave();
                }
            }).exceptionally(error -> {
                movesInFlight.remove(resident.id());
                return null;
            });
        }).exceptionally(error -> {
            movesInFlight.remove(resident.id());
            return null;
        });
    }

    // ----- R1.30: villagers that are left alone -----

    private final Map<UUID, IgnoreZones> ignoreZones = new HashMap<>();

    private org.bukkit.NamespacedKey zonesKey() {
        return new org.bukkit.NamespacedKey(plugin, "ignore_zones");
    }

    private org.bukkit.NamespacedKey ignoreTagKey() {
        return new org.bukkit.NamespacedKey(plugin, "ignored");
    }

    /** The exempt zones of a world, read from the world's own data the first time. */
    IgnoreZones zonesOf(World world) {
        return ignoreZones.computeIfAbsent(world.getUID(), id -> {
            String stored = world.getPersistentDataContainer().get(zonesKey(), org.bukkit.persistence.PersistentDataType.STRING);
            return stored == null || stored.isBlank() ? new IgnoreZones() : IgnoreZones.decode(List.of(stored.split("\n")));
        });
    }

    /** Writes a world's exempt zones back into its data. */
    void saveZones(World world) {
        IgnoreZones zones = zonesOf(world);
        if (zones.isEmpty()) {
            world.getPersistentDataContainer().remove(zonesKey());
        } else {
            world.getPersistentDataContainer().set(zonesKey(), org.bukkit.persistence.PersistentDataType.STRING,
                    String.join("\n", zones.encode()));
        }
    }

    /** True if this villager is to be left alone: tagged, or inside an exempt zone. */
    boolean isIgnored(Villager villager) {
        if (villager.getPersistentDataContainer().has(ignoreTagKey(), org.bukkit.persistence.PersistentDataType.BYTE)) {
            return true;
        }
        Location at = villager.getLocation();
        IgnoreZones.Zone zone = zonesOf(at.getWorld()).coveringZone(at.getBlockX(), at.getBlockY(), at.getBlockZ()).orElse(null);
        // An ordinary player's zone leaves out anyone who already belongs to a village.
        return zone != null && (zone.overridesVillages() || registry.resident(villager.getUniqueId()).isEmpty());
    }

    /** Toggles the tag on one villager; returns true if it is now left alone. A tagged villager is released at once. */
    boolean toggleIgnoreTag(Villager villager) {
        var data = villager.getPersistentDataContainer();
        if (data.has(ignoreTagKey(), org.bukkit.persistence.PersistentDataType.BYTE)) {
            data.remove(ignoreTagKey());
            return false;
        }
        data.set(ignoreTagKey(), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        release(villager);
        return true;
    }

    /**
     * Takes a villager out of its settlement, if it was in one, and removes the name this plugin gave it (a name an
     * owner chose is kept). Nothing else about the villager is touched.
     */
    private void release(Villager villager) {
        Resident resident = registry.remove(villager.getUniqueId()).orElse(null);
        if (resident == null) {
            return;
        }
        clearAppearance(villager, resident);
        if (villager.customName() != null && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(villager.customName()).equals(resident.fullName())) {
            villager.customName(null);
        }
        plugin.requestSave();
    }

    /** Takes the plugin's appearance data off a villager, and puts back the look it had if the plugin changed its type. */
    private void clearAppearance(Villager villager, Resident resident) {
        var data = villager.getPersistentDataContainer();
        long day = day(villager.getWorld());
        Appearance.tags(resident, day).keySet().forEach(name -> data.remove(new NamespacedKey(plugin, name)));
        NamespacedKey originalKey = new NamespacedKey(plugin, "original_type");
        String original = data.get(originalKey, PersistentDataType.STRING);
        if (original != null) {
            villager.setVillagerType(villagerType(original));
            data.remove(originalKey);
        }
    }

    /** How many loaded villagers inside a zone belong to a village (and so are not exempted by an ordinary player's sign). */
    int registeredWithin(World world, IgnoreZones.Zone zone) {
        int count = 0;
        Location centre = new Location(world, zone.x() + 0.5, zone.y() + 0.5, zone.z() + 0.5);
        for (Villager villager : world.getNearbyEntitiesByType(Villager.class, centre, zone.radius(), zone.radius(), zone.radius())) {
            Location at = villager.getLocation();
            long dx = at.getBlockX() - zone.x();
            long dz = at.getBlockZ() - zone.z();
            if (dx * dx + dz * dz <= (long) zone.radius() * zone.radius() && registry.resident(villager.getUniqueId()).isPresent()) {
                count++;
            }
        }
        return count;
    }

    /** Releases residents whose loaded villager has become exempt (a tag, or an admin's sign it walked into). */
    private void releaseExemptResidents(Settlement settlement) {
        for (Resident resident : new ArrayList<>(settlement.residents())) {
            if (Bukkit.getEntity(resident.id()) instanceof Villager villager && isIgnored(villager)) {
                release(villager);
            }
        }
    }

    /** Releases every loaded villager now inside a zone; returns how many were in a village. */
    int releaseWithin(World world, IgnoreZones.Zone zone) {
        int released = 0;
        Location centre = new Location(world, zone.x() + 0.5, zone.y() + 0.5, zone.z() + 0.5);
        for (Villager villager : world.getNearbyEntitiesByType(Villager.class, centre, zone.radius(), zone.radius(), zone.radius())) {
            if (isIgnored(villager) && registry.resident(villager.getUniqueId()).isPresent()) {
                release(villager);
                released++;
            }
        }
        return released;
    }

    /** Drops exempt zones whose sign is gone (destroyed by physics, an explosion or an editing tool), in loaded chunks. */
    private void pruneIgnoreZones(World world) {
        IgnoreZones zones = zonesOf(world);
        boolean pruned = false;
        for (IgnoreZones.Zone zone : zones.zones()) {
            if (world.isChunkLoaded(zone.x() >> 4, zone.z() >> 4)
                    && !Tag.ALL_SIGNS.isTagged(world.getBlockAt(zone.x(), zone.y(), zone.z()).getType())) {
                zones.removeAt(zone.x(), zone.y(), zone.z());
                pruned = true;
            }
        }
        if (pruned) {
            saveZones(world);
        }
    }

    /**
     * R3.14: false when this trade sells the player something the stores can no longer spare. Checked as the
     * trade happens, because several offers can draw on the same stock.
     */
    boolean storesCover(Villager villager, String costMaterial, String resultMaterial, int resultAmount) {
        if (!config.tradesNeedStock() || isIgnored(villager)) {
            return true;
        }
        Resident resident = registry.resident(villager.getUniqueId()).orElse(null);
        Settlement settlement = resident == null ? null : registry.settlementOf(resident.id()).orElse(null);
        return settlement == null
                || Trading.tradesAllowed(settlement, costMaterial, resultMaterial, resultAmount).orElse(1) > 0;
    }

    /** R3.2: feeds a completed trade into the stores of the settlement the villager belongs to, if any. */
    void applyTrade(Villager villager, String given, int givenAmount, String received, int receivedAmount) {
        Resident resident = track(villager);
        if (resident == null) {
            return;
        }
        registry.settlementOf(resident.id()).ifPresent(settlement -> {
            if (Trading.apply(settlement, given, givenAmount, received, receivedAmount).isPresent()) {
                plugin.requestSave();
            }
        });
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
        for (World world : Bukkit.getWorlds()) {
            if (inScope(world)) {
                pruneIgnoreZones(world); // R1.30
            }
        }
        for (Settlement settlement : registry.settlements()) {
            simulate(settlement);
            World bedWorld = Bukkit.getWorld(settlement.world());
            if (bedWorld != null && inScope(bedWorld)) {
                scanBedsIfDue(settlement, bedWorld);
            }
            considerNewcomer(settlement);
            releaseExemptResidents(settlement); // R1.30
            considerMigration(settlement);
            considerMembership(settlement);
            try {
                ensurePlan(settlement);
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not plan the layout of " + settlement.name(), e);
            }
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
        if (zonesOf(world).covers(x, y, z)) {
            return; // R1.30: nobody is spawned into an exempt zone, where they would be left alone and never counted
        }
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
     * R2.2: counts the beds in a settlement's area through the game's own points of interest (the same registry
     * villagers use to claim a bed), because beds are no longer block entities and cannot be found that way. Only
     * chunks that are loaded are updated, so a chunk nobody is near keeps its last known count.
     */
    private void scanBeds(Settlement settlement, World world) {
        // Searching points of interest can read unloaded chunks from disk on the main thread, so only do it while the
        // village is in use: with its centre loaded. Otherwise every count stays as last known.
        if (!world.isChunkLoaded(settlement.centerX() >> 4, settlement.centerZ() >> 4)) {
            return;
        }
        int radius = config.settlementRadius();
        long radiusSquared = (long) radius * radius;
        Map<Long, Integer> perChunk = new HashMap<>();
        for (Location bed : bedsAround(settlement, world, null, 0)) {
            if (settlement.distanceSquared(bed.getBlockX(), bed.getBlockZ()) <= radiusSquared) {
                perChunk.merge(chunkKey(bed.getBlockX() >> 4, bed.getBlockZ() >> 4), 1, Integer::sum);
            }
        }
        for (int cx = (settlement.centerX() - radius) >> 4; cx <= (settlement.centerX() + radius) >> 4; cx++) {
            for (int cz = (settlement.centerZ() - radius) >> 4; cz <= (settlement.centerZ() + radius) >> 4; cz++) {
                if (world.isChunkLoaded(cx, cz)) {
                    settlement.housing().setChunk(cx, cz, perChunk.getOrDefault(chunkKey(cx, cz), 0));
                }
            }
        }
        // A smaller radius than before must not leave counts for chunks that are no longer in range.
        settlement.housing().retainWithin((settlement.centerX() - radius) >> 4, (settlement.centerX() + radius) >> 4,
                (settlement.centerZ() - radius) >> 4, (settlement.centerZ() + radius) >> 4);
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    /** How far above or below the village's surface a bed is still looked for. */
    private static final int BED_HEIGHT = 48;

    /**
     * R2.2: the beds around a settlement's centre: every one, or only the free or only the taken ones. A bed is
     * a point of interest of the {@code home} type, one for each bed. The game searches a sphere, so it is made
     * large enough to cover the whole circle of the settlement radius for {@link #BED_HEIGHT} blocks above and
     * below the surface at the centre, and the caller keeps what lies inside the radius. Beds further above or
     * below that are missed. {@code fallbackY} stands in for the surface height when the centre chunk is not
     * loaded (a loaded chunk's height costs nothing; an unloaded one would be loaded to find it).
     */
    List<Location> bedsAround(Settlement settlement, World world, PoiType.Occupancy occupancy, int fallbackY) {
        int x = settlement.centerX();
        int z = settlement.centerZ();
        int y = world.isChunkLoaded(x >> 4, z >> 4)
                ? world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) : fallbackY;
        int radius = config.settlementRadius();
        int search = (int) Math.ceil(Math.sqrt((double) radius * radius + (double) BED_HEIGHT * BED_HEIGHT));
        List<Location> beds = new ArrayList<>();
        for (PoiSearchResult found : world.locateAllPoiInRange(new Location(world, x + 0.5, y, z + 0.5),
                type -> type == PoiTypes.HOME, search, occupancy == null ? PoiType.Occupancy.ANY : occupancy)) {
            Location bed = found.location();
            if (Math.abs(bed.getBlockY() - y) <= BED_HEIGHT) {
                beds.add(bed);
            }
        }
        return beds;
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
        if (isIgnored(villager)) {
            release(villager); // R1.30: tagged or inside an exempt zone: left completely alone
            pendingParents.remove(villager.getUniqueId());
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
        completeMove(villager, resident); // R4.2: someone who moved settlement while unloaded arrives now
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
        if (config.tradesNeedStock() && parents == null && day == settlement.foundedDay()) {
            Trading.seedFounder(settlement); // R3.14: the first villagers bring the village's starting stores
        }
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
