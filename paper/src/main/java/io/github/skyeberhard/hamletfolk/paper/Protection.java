package io.github.skyeberhard.hamletfolk.paper;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

/**
 * R1.52: how the village's work sits with the server's protection plugins. Both are optional and found by name at run time (no class of theirs is
 * linked), so nothing changes where they are not installed.
 *
 * <ul>
 *   <li><b>CoreProtect</b>: every block the plugin places or removes goes through {@link #setType} or {@link #setBlockData}, which log it under the
 *       user {@code #Hamletfolk}, so an admin can look it up and roll it back like anyone's.</li>
 *   <li><b>WorldGuard</b>: {@link #denies} says whether a place is one where the village may not change blocks, asked the way WorldGuard asks
 *       about a stranger building there (a region with owners or members, or with {@code build: deny}). How WorldGuard treats a region with no
 *       owners and no flags is WorldGuard's own rule and has not been checked here: look at one on the dev server. Builders do not start a project
 *       there, lumberjacks do not fell there, miners do not dig there, streets stop there and notice boards and barrels are not put there.</li>
 * </ul>
 *
 * A failure to reach either plugin is logged once and the plugin carries on without it.
 */
final class Protection {
    private static volatile Protection instance;

    private final Plugin plugin;
    private final boolean logToCoreProtect;
    private final boolean honourWorldGuard;
    private final String user;
    private Object coreProtect; // its API object, or null
    private Method logPlacement;
    private Method logRemoval;
    private Object regionQuery;
    private Method testBuild;
    private Method adapt;
    private Class<?> flagArray;
    private boolean warned;
    /** When to look for each plugin again after it was missing or failed (milliseconds), so a server without it pays nothing per block. */
    private long coreProtectRetryAt;
    private long worldGuardRetryAt;
    private static final long RETRY_MS = 60_000;

    Protection(Plugin plugin) {
        this.plugin = plugin;
        this.logToCoreProtect = plugin.getConfig().getBoolean("protection.coreprotect", true);
        this.honourWorldGuard = plugin.getConfig().getBoolean("protection.worldguard", true);
        this.user = plugin.getConfig().getString("protection.user", "#Hamletfolk");
    }

    /** Makes this the instance the static methods use. */
    void install() {
        instance = this;
    }

    static void uninstall() {
        instance = null;
    }

    // ----- block changes -----

    static void setType(Block block, Material type, boolean physics) {
        Protection p = instance;
        if (p == null) {
            block.setType(type, physics);
            return;
        }
        boolean logging = p.coreProtectUsable();
        BlockData before = logging ? block.getBlockData() : null;
        block.setType(type, physics);
        p.log(block, before, logging ? block.getBlockData() : null);
    }

    static void setBlockData(Block block, BlockData data, boolean physics) {
        Protection p = instance;
        if (p == null) {
            block.setBlockData(data, physics);
            return;
        }
        boolean logging = p.coreProtectUsable();
        BlockData before = logging ? block.getBlockData() : null;
        block.setBlockData(data, physics);
        p.log(block, before, logging ? block.getBlockData() : null);
    }

    /** True if CoreProtect is to be logged to and is there: looked for again only once a minute while it is not. */
    private boolean coreProtectUsable() {
        if (!logToCoreProtect) {
            return false;
        }
        if (coreProtect != null) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now < coreProtectRetryAt) {
            return false;
        }
        try {
            if (reachCoreProtect()) {
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("CoreProtect", e);
        }
        coreProtectRetryAt = now + RETRY_MS;
        return false;
    }

    private void log(Block block, BlockData before, BlockData after) {
        if (before == null || after == null || coreProtect == null || before.equals(after)) {
            return;
        }
        try {
            Location at = block.getLocation();
            if (!before.getMaterial().isAir()) {
                logRemoval.invoke(coreProtect, user, at, before.getMaterial(), before);
            }
            if (!after.getMaterial().isAir()) {
                logPlacement.invoke(coreProtect, user, at, after.getMaterial(), after);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("CoreProtect", e);
        }
    }

    private boolean reachCoreProtect() throws ReflectiveOperationException {
        Plugin cp = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (cp == null || !cp.isEnabled()) {
            return false;
        }
        Object api = cp.getClass().getMethod("getAPI").invoke(cp);
        Class<?> type = api.getClass();
        logPlacement = type.getMethod("logPlacement", String.class, Location.class, Material.class, BlockData.class);
        logRemoval = type.getMethod("logRemoval", String.class, Location.class, Material.class, BlockData.class);
        coreProtect = api;
        plugin.getLogger().info("Logging the village's building to CoreProtect as " + user + ".");
        return true;
    }

    // ----- places the village may not change -----

    /** True if WorldGuard says a stranger may not build at this place. False if it has no say, is not installed, or could not be asked. */
    static boolean denies(Location at) {
        Protection p = instance;
        return p != null && p.honourWorldGuard && at != null && at.getWorld() != null && p.askWorldGuard(at);
    }

    static boolean denies(org.bukkit.World world, int x, int y, int z) {
        return denies(new Location(world, x, y, z));
    }

    private boolean askWorldGuard(Location at) {
        if (regionQuery == null) {
            long now = System.currentTimeMillis();
            if (now < worldGuardRetryAt) {
                return false;
            }
            try {
                if (!reachWorldGuard()) {
                    worldGuardRetryAt = now + RETRY_MS;
                    return false;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                fail("WorldGuard", e);
                return false;
            }
        }
        try {
            Object where = adapt.invoke(null, at);
            return !(Boolean) testBuild.invoke(regionQuery, where, null, Array.newInstance(flagArray.getComponentType(), 0));
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("WorldGuard", e);
            return false;
        }
    }

    private boolean reachWorldGuard() throws ReflectiveOperationException {
        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled()) {
            return false;
        }
        Class<?> guard = Class.forName("com.sk89q.worldguard.WorldGuard", true, wg.getClass().getClassLoader());
        Object inst = guard.getMethod("getInstance").invoke(null);
        Object platform = inst.getClass().getMethod("getPlatform").invoke(inst);
        Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
        Object query = container.getClass().getMethod("createQuery").invoke(container);
        Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, wg.getClass().getClassLoader());
        Class<?> weLocation = Class.forName("com.sk89q.worldedit.util.Location", true, wg.getClass().getClassLoader());
        Class<?> associable = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable", true, wg.getClass().getClassLoader());
        Class<?> flag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag", true, wg.getClass().getClassLoader());
        flagArray = Array.newInstance(flag, 0).getClass();
        adapt = adapter.getMethod("adapt", Location.class);
        testBuild = query.getClass().getMethod("testBuild", weLocation, associable, flagArray);
        regionQuery = query;
        plugin.getLogger().info("Keeping the village out of places WorldGuard protects.");
        return true;
    }

    private void fail(String what, Exception e) {
        if (!warned) {
            warned = true;
            plugin.getLogger().log(Level.WARNING, "Could not use " + what + " (" + e + "); carrying on without it. This is logged once.");
        }
        if (what.equals("CoreProtect")) {
            coreProtect = null;
            coreProtectRetryAt = System.currentTimeMillis() + 10 * RETRY_MS;
        } else {
            regionQuery = null;
            worldGuardRetryAt = System.currentTimeMillis() + 10 * RETRY_MS;
        }
    }
}
