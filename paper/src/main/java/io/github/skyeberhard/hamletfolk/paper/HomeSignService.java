package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.Building;
import io.github.skyeberhard.hamletfolk.core.BuildingFinder;
import io.github.skyeberhard.hamletfolk.core.Homes;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * R6.1: puts the household's name on its house's sign (lines 3 and 4, front and back) while a player is near, and remembers who lives where
 * on the sign itself (a persistent-data mark with the head's id), so a household keeps its house over restarts and as the village changes.
 * A sign a player has written on is never touched: only lines that are empty, or that we wrote (carry our mark). Loaded ground only. Main thread.
 */
final class HomeSignService {
    private static final double NEAR = 64;
    private static final long EVERY_TICKS = 400;
    private static final int WIDTH = 14;

    private final HamletfolkPlugin plugin;
    private final SettlementService service;
    private final NamespacedKey markKey;
    /** The last homes worked out, per village, for the commands and the dashboard. */
    private final Map<UUID, List<Homes.Home>> latest = new HashMap<>();
    /** Who lived in which house (house key to head), as last read from the signs, so a house is remembered while its chunk is unloaded. */
    private final Map<UUID, Map<String, UUID>> remembered = new HashMap<>();

    HomeSignService(HamletfolkPlugin plugin, SettlementService service) {
        this.plugin = plugin;
        this.service = service;
        this.markKey = new NamespacedKey(plugin, "home");
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 260L, EVERY_TICKS);
    }

    /** The homes for a village's map: those last worked out, else worked out now from what the signs last said (nothing is written). */
    List<Homes.Home> homesFor(Settlement settlement) {
        List<Homes.Home> known = latest.get(settlement.id());
        return known != null ? known : Homes.assign(settlement, remembered.getOrDefault(settlement.id(), Map.of()));
    }

    /** The homes last worked out for a village (empty if none yet). */
    List<Homes.Home> homesOf(Settlement settlement) {
        return latest.getOrDefault(settlement.id(), List.of());
    }

    /** An admin's {@code homes}: loads each house's ground (held for a minute), writes the signs now whether or not a player is near, and lists the homes. Main thread. */
    List<String> writeNow(Settlement settlement) {
        World world = Bukkit.getWorld(settlement.world());
        if (world == null || !service.inScope(world)) {
            return List.of(settlement.name() + " is in a world that is not simulated.");
        }
        List<int[]> chunks = new ArrayList<>();
        for (Building b : settlement.buildings()) {
            if (b.type() == io.github.skyeberhard.hamletfolk.core.BuildingType.HOUSE && !BuildingFinder.FOUND_BY.equals(b.registeredBy())) {
                world.getChunkAt(b.x() >> 4, b.z() >> 4);
                world.addPluginChunkTicket(b.x() >> 4, b.z() >> 4, plugin);
                chunks.add(new int[] {b.x() >> 4, b.z() >> 4});
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> chunks.forEach(c -> world.removePluginChunkTicket(c[0], c[1], plugin)), 1200L);
        signsWritten = 0;
        visit(settlement, true);
        List<String> out = new ArrayList<>();
        out.add(chunks.size() + " houses have a sign of their own; " + signsWritten + " signs written" + (chunks.isEmpty() ? " (the others were found by their beds and have no sign)" : "") + ".");
        for (Homes.Home h : homesOf(settlement)) {
            out.add(h.household().describe() + ", house at " + h.house().x() + " " + h.house().y() + " " + h.house().z());
        }
        if (out.isEmpty()) {
            out.add(settlement.name() + " has no registered house with a household to put in it.");
        }
        return out;
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("homes.signs", true)) {
            return;
        }
        for (Settlement settlement : new ArrayList<>(service.registry().settlements())) {
            try {
                visit(settlement);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Home sign pass failed for " + settlement.name(), e);
            }
        }
    }

    private void visit(Settlement settlement) {
        visit(settlement, false);
    }

    private void visit(Settlement settlement, boolean anyway) {
        if (settlement.population() == 0 || settlement.isAbandoned()) {
            return;
        }
        World world = Bukkit.getWorld(settlement.world());
        if (world == null || !service.inScope(world) || (!anyway && !playerNear(world, settlement))) {
            return;
        }
        Map<String, UUID> previous = remembered.computeIfAbsent(settlement.id(), k -> new HashMap<>());
        // what the signs say now (only for signs in loaded ground)
        for (Building house : settlement.buildings()) {
            Block block = signBlock(world, house);
            if (block != null && block.getState(false) instanceof Sign sign) {
                String mark = sign.getPersistentDataContainer().get(markKey, PersistentDataType.STRING);
                if (mark != null) {
                    try {
                        previous.put(house.key(), UUID.fromString(mark));
                    } catch (IllegalArgumentException e) {
                        // a corrupt mark: ignored, and written over below
                    }
                }
            }
        }
        List<Homes.Home> homes = Homes.assign(settlement, previous);
        latest.put(settlement.id(), homes);
        Map<String, Homes.Home> byHouse = new HashMap<>();
        homes.forEach(h -> byHouse.put(h.house().key(), h));
        previous.clear();
        homes.forEach(h -> previous.put(h.house().key(), h.head()));
        for (Building house : settlement.buildings()) {
            if (house.type() != io.github.skyeberhard.hamletfolk.core.BuildingType.HOUSE) {
                continue;
            }
            Block block = signBlock(world, house);
            if (block != null) {
                write(block, byHouse.get(house.key()));
            }
        }
    }

    /** The sign block of a house that has a sign of its own (not one found by the game's beds) in loaded ground, else null. */
    private static Block signBlock(World world, Building house) {
        if (BuildingFinder.FOUND_BY.equals(house.registeredBy()) || !world.isChunkLoaded(house.x() >> 4, house.z() >> 4)) {
            return null;
        }
        Block block = world.getBlockAt(house.x(), house.y(), house.z());
        return org.bukkit.Tag.ALL_SIGNS.isTagged(block.getType()) ? block : null;
    }

    private int signsWritten;

    private void write(Block block, Homes.Home home) {
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }
        boolean ours = sign.getPersistentDataContainer().has(markKey, PersistentDataType.STRING);
        boolean blank = blank(sign.getSide(Side.FRONT), 2) && blank(sign.getSide(Side.FRONT), 3);
        if (!ours && !blank) {
            return; // someone's own writing: leave it
        }
        if (home == null) {
            if (ours) { // the household is gone: the sign is plain again
                for (Side side : Side.values()) {
                    sign.getSide(side).line(2, Component.empty());
                    sign.getSide(side).line(3, Component.empty());
                }
                sign.getPersistentDataContainer().remove(markKey);
                sign.update(true, false);
            }
            return;
        }
        List<String> lines = Homes.signLines(home.household(), WIDTH);
        for (Side side : Side.values()) {
            for (int i = 0; i < 2; i++) {
                sign.getSide(side).line(2 + i, Component.text(i < lines.size() ? lines.get(i) : ""));
            }
        }
        sign.getPersistentDataContainer().set(markKey, PersistentDataType.STRING, home.head().toString());
        sign.update(true, false);
        signsWritten++;
    }

    private static boolean blank(org.bukkit.block.sign.SignSide side, int line) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(side.line(line)).isBlank();
    }

    private static boolean playerNear(World world, Settlement settlement) {
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (Math.hypot(at.getX() - settlement.centerX(), at.getZ() - settlement.centerZ()) <= NEAR + 32) {
                return true;
            }
        }
        return false;
    }
}
