package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.StreetLamps;
import io.github.skyeberhard.hamletfolk.core.VillagePlan;
import io.github.skyeberhard.hamletfolk.core.Works;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;

/**
 * R4.54: lights the village's street lamps (the copper bulbs on its light posts) at dusk and puts them out at dawn, in loaded ground
 * near a player. Nothing is loaded; a lamp nobody is near stays as it was and is set right when someone comes by. Main thread.
 */
final class StreetLampService {
    private static final double NEAR = 96;
    private static final long EVERY_TICKS = 100;

    private final HamletfolkPlugin plugin;
    private final SettlementService service;

    StreetLampService(HamletfolkPlugin plugin, SettlementService service) {
        this.plugin = plugin;
        this.service = service;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 200L, EVERY_TICKS);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("lights.lamps", true)) {
            return;
        }
        try {
            for (Settlement settlement : new java.util.ArrayList<>(service.registry().settlements())) {
                visit(settlement);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Street lamp pass failed", e);
        }
    }

    private void visit(Settlement settlement) {
        VillagePlan plan = settlement.plan();
        if (plan == null || settlement.population() == 0) {
            return;
        }
        World world = Bukkit.getWorld(settlement.world());
        if (world == null || !service.inScope(world) || !near(world, plan)) {
            return;
        }
        boolean lit = StreetLamps.lit(world.getTime(), world.hasStorm());
        for (Works.Spot spot : Works.lights(plan)) {
            if (!world.isChunkLoaded(spot.x() >> 4, spot.z() >> 4)) {
                continue;
            }
            int top = world.getHighestBlockYAt(spot.x(), spot.z());
            for (int y = top; y >= top - 1; y--) {
                Block block = world.getBlockAt(spot.x(), y, spot.z());
                if (block.getType() == Material.WAXED_COPPER_BULB && block.getBlockData() instanceof Lightable data && data.isLit() != lit) {
                    data.setLit(lit);
                    Protection.setBlockData(block, data, false);
                    break;
                }
            }
        }
    }

    private static boolean near(World world, VillagePlan plan) {
        for (Player player : world.getPlayers()) {
            if (Math.hypot(player.getLocation().getX() - plan.centerX(), player.getLocation().getZ() - plan.centerZ()) <= NEAR) {
                return true;
            }
        }
        return false;
    }
}
