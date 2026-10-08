package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.Building;
import io.github.skyeberhard.hamletfolk.core.BuildingType;
import io.github.skyeberhard.hamletfolk.core.HistoryEvent;
import io.github.skyeberhard.hamletfolk.core.Rect;
import io.github.skyeberhard.hamletfolk.core.ResourceType;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.VillagePlan;
import io.github.skyeberhard.hamletfolk.core.WorldMarks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;

/**
 * R4.25: pays the village's debt to the land (see core's {@link WorldMarks}) where the ground is loaded. Lumberjacks fell
 * whole trees near the village, outside its plan, and plant a sapling where each stood; miners dig a quarry five blocks
 * square beside the village's mine, a layer at a time, and what it turns up is kept. Only trees the game grew and natural
 * ground and ore are taken; never anything a player built, nothing inside the plan, nothing next to water or lava, and
 * nothing in an exempt zone. A quarry is fenced round when it is opened, so villagers do not walk into it.
 */
final class WorldMarksService {
    private static final long PERIOD_TICKS = 40;
    /** Blocks a quarry is dug by in one pass, and at most one tree is felled a pass. */
    private static final int DIG_PER_PASS = 12;
    private static final int QUARRY_SIZE = 5;
    /** How deep a quarry goes below its top, and how close to the bottom of the world. */
    private static final int QUARRY_DEPTH = 40;
    private static final int QUARRY_FLOOR_MARGIN = 6;
    /** How far from its mine's sign a quarry may be opened. */
    private static final int QUARRY_REACH = 20;
    /** How far beyond the plan's edge the lumberjacks look for trees. */
    private static final int WOODS_REACH = 40;
    private static final int MAX_LOGS_PER_TREE = 64;

    private final HamletfolkPlugin plugin;
    private final SettlementService service;
    private final ConstructionService construction;

    WorldMarksService(HamletfolkPlugin plugin, SettlementService service, ConstructionService construction) {
        this.plugin = plugin;
        this.service = service;
        this.construction = construction;
    }

    void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::pass, PERIOD_TICKS * 3, PERIOD_TICKS);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("world-marks.enabled", true);
    }

    /** Not saved: when (real time) a village last found no tree to fell, so it does not search every pass for nothing. */
    private final java.util.Map<java.util.UUID, Long> noTrees = new java.util.HashMap<>();
    private static final long NO_TREES_MS = 120_000;

    private void pass() {
        Set<java.util.UUID> known = new HashSet<>();
        for (Settlement settlement : service.registry().settlements()) {
            known.add(settlement.id());
        }
        noTrees.keySet().retainAll(known);
        construction.releaseKeysWhere(key -> key.startsWith("mq")
                && !known.contains(java.util.UUID.fromString(key.substring(2)))); // a village that is gone holds nothing
        for (Settlement settlement : new ArrayList<>(service.registry().settlements())) {
            World world = Bukkit.getWorld(settlement.world());
            if (!enabled() || world == null || !service.inScope(world) || settlement.isAbandoned() || settlement.plan() == null) {
                construction.releaseKey("mq" + settlement.id());
                continue;
            }
            try {
                quarry(settlement, world);
                woods(settlement, world);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not leave the marks of work in " + settlement.name(), e);
            }
        }
    }

    // ----- the quarry -----

    private void quarry(Settlement settlement, World world) {
        String holdKey = "mq" + settlement.id();
        if (WorldMarks.owed(settlement, ResourceType.STONE) == 0) {
            construction.releaseKey(holdKey);
            return;
        }
        java.util.Optional<WorldMarks.Quarry> quarry = WorldMarks.quarry(settlement);
        if (quarry.isEmpty()) {
            openQuarry(settlement, world);
            return;
        }
        if (quarry.get().exhausted()) {
            construction.releaseKey(holdKey);
            return; // dug as deep as it goes
        }
        int x0 = quarry.get().x();
        int z0 = quarry.get().z();
        int y = quarry.get().layer();
        int span = WorldMarks.QUARRY_SIZE + 2;
        if (inPlan(settlement.plan(), new Rect(x0 - 1, z0 - 1, span, span)) || zoneCovers(world, x0 - 1, z0 - 1, span, y)) {
            construction.releaseKey(holdKey);
            return; // the village has grown over it, or an exempt zone has been put round it: no more digging
        }
        if (construction.unattended()) {
            construction.holdKey(world, holdKey, chunksOf(x0 - 1, z0 - 1, QUARRY_SIZE + 2));
        }
        if (!loaded(world, x0 - 1, z0 - 1, QUARRY_SIZE + 2)) {
            return;
        }
        int dug = 0;
        boolean layerDone = true;
        for (int dx = 0; dx < QUARRY_SIZE && dug < DIG_PER_PASS && WorldMarks.owed(settlement, ResourceType.STONE) > 0; dx++) {
            for (int dz = 0; dz < QUARRY_SIZE && dug < DIG_PER_PASS && WorldMarks.owed(settlement, ResourceType.STONE) > 0; dz++) {
                Block block = world.getBlockAt(x0 + dx, y, z0 + dz);
                if (block.getType().isAir() || !WorldMarks.quarryable(block.getType().getKey().getKey()) || touchesLiquid(block)) {
                    continue; // nothing there, not ours to take, or it would let water or lava in
                }
                WorldMarks.drop(block.getType().getKey().getKey()).ifPresent(d -> WorldMarks.keep(settlement, d, settlement.lastSimulatedDay()));
                block.setType(Material.AIR, false);
                WorldMarks.pay(settlement, ResourceType.STONE, 1);
                dug++;
                layerDone = false;
                if (WorldMarks.owed(settlement, ResourceType.STONE) == 0) {
                    break; // a block against each unit owed, and no more
                }
            }
        }
        if (layerDone) {
            WorldMarks.nextLayer(settlement, settlement.lastSimulatedDay());
        }
        if (dug > 0) {
            plugin.requestSave();
        }
    }

    /** Finds a spot for the quarry beside the mine: five by five of natural ground, outside the plan, all loaded; fences it. */
    private void openQuarry(Settlement settlement, World world) {
        Building mine = null;
        for (Building b : settlement.buildings()) {
            if (b.type() == BuildingType.MINE) {
                mine = b;
                break;
            }
        }
        if (mine == null) {
            return;
        }
        for (int reach = 6; reach <= QUARRY_REACH; reach += 2) {
            for (int[] dir : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
                int x0 = mine.x() + dir[0] * reach - QUARRY_SIZE / 2;
                int z0 = mine.z() + dir[1] * reach - QUARRY_SIZE / 2;
                if (!loaded(world, x0 - 1, z0 - 1, QUARRY_SIZE + 2) || inPlan(settlement.plan(), new Rect(x0 - 1, z0 - 1, QUARRY_SIZE + 2, QUARRY_SIZE + 2))
                        || zoneCovers(world, x0 - 1, z0 - 1, QUARRY_SIZE + 2, world.getHighestBlockYAt(x0, z0))) {
                    continue;
                }
                int top = Integer.MIN_VALUE;
                boolean natural = true;
                for (int dx = -1; dx <= QUARRY_SIZE && natural; dx++) {
                    for (int dz = -1; dz <= QUARRY_SIZE && natural; dz++) {
                        int y = world.getHighestBlockYAt(x0 + dx, z0 + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                        Block surface = world.getBlockAt(x0 + dx, y, z0 + dz);
                        natural = WorldMarks.quarryable(surface.getType().getKey().getKey()) && !surface.isLiquid();
                        top = Math.max(top, y);
                    }
                }
                if (!natural) {
                    continue;
                }
                fence(world, x0, z0);
                WorldMarks.openQuarry(settlement, x0, z0, top, Math.max(world.getMinHeight() + QUARRY_FLOOR_MARGIN, top - QUARRY_DEPTH),
                        settlement.lastSimulatedDay());
                plugin.requestSave();
                return;
            }
        }
    }

    /** A fence round the quarry's edge, on the ground, wherever there is room for one. */
    private static void fence(World world, int x0, int z0) {
        for (int dx = -1; dx <= QUARRY_SIZE; dx++) {
            for (int dz = -1; dz <= QUARRY_SIZE; dz++) {
                if (dx >= 0 && dx < QUARRY_SIZE && dz >= 0 && dz < QUARRY_SIZE) {
                    continue;
                }
                int y = world.getHighestBlockYAt(x0 + dx, z0 + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                Block post = world.getBlockAt(x0 + dx, y + 1, z0 + dz);
                if (post.getType().isAir() || Tag.REPLACEABLE.isTagged(post.getType())) {
                    post.setType(Material.OAK_FENCE, true);
                }
            }
        }
    }

    /** True if an exempt zone covers any corner or the middle of a square area, at a height. */
    private boolean zoneCovers(World world, int x, int z, int size, int y) {
        io.github.skyeberhard.hamletfolk.core.IgnoreZones zones = service.zonesOf(world);
        for (int[] at : new int[][] {{0, 0}, {size, 0}, {0, size}, {size, size}, {size / 2, size / 2}}) {
            for (int dy : new int[] {-30, 0, 30}) {
                if (zones.covers(x + at[0], y + dy, z + at[1])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean touchesLiquid(Block block) {
        for (BlockFace face : new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (block.getRelative(face).isLiquid()) {
                return true;
            }
        }
        return false;
    }

    // ----- the woods -----

    /** Fells one tree a pass while wood is owed: the nearest the game grew, outside the plan, and plants a sapling. */
    private void woods(Settlement settlement, World world) {
        if (WorldMarks.owed(settlement, ResourceType.WOOD) < WorldMarks.UNITS_PER_LOG) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - noTrees.getOrDefault(settlement.id(), 0L) < NO_TREES_MS) {
            return; // looked a moment ago and found nothing to fell
        }
        VillagePlan plan = settlement.plan();
        Rect edge = extent(plan);
        if (edge == null) {
            return;
        }
        for (int ring = 4; ring <= WOODS_REACH; ring += 3) {
            Rect around = edge.inflated(ring);
            for (int x = around.x(); x <= around.maxX(); x += 3) {
                for (int z : new int[] {around.z(), around.maxZ()}) {
                    if (fellAt(settlement, world, plan, x, z)) {
                        return;
                    }
                }
            }
            for (int z = around.z(); z <= around.maxZ(); z += 3) {
                for (int x : new int[] {around.x(), around.maxX()}) {
                    if (fellAt(settlement, world, plan, x, z)) {
                        return;
                    }
                }
            }
        }
        noTrees.put(settlement.id(), now);
    }

    private boolean fellAt(Settlement settlement, World world, VillagePlan plan, int x, int z) {
        // Every chunk within reach of the tree must be loaded before any block is read, or reading would load it.
        if (!loaded(world, x - TREE_REACH, z - TREE_REACH, 2 * TREE_REACH) || inPlan(plan, new Rect(x - TREE_REACH, z - TREE_REACH,
                2 * TREE_REACH + 1, 2 * TREE_REACH + 1)) || zoneCovers(world, x - TREE_REACH, z - TREE_REACH, 2 * TREE_REACH,
                world.getHighestBlockYAt(x, z))) {
            return false;
        }
        int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        Block log = null;
        for (int y = top; y > top - 16 && y > world.getMinHeight(); y--) {
            Block b = world.getBlockAt(x, y, z);
            if (Tag.LOGS.isTagged(b.getType())) {
                log = b;
                break;
            }
        }
        if (log == null) {
            return false;
        }
        Block base = log;
        while (base.getRelative(BlockFace.DOWN).getType() == base.getType()) {
            base = base.getRelative(BlockFace.DOWN);
        }
        Material ground = base.getRelative(BlockFace.DOWN).getType();
        if (!(ground == Material.GRASS_BLOCK || ground == Material.DIRT || ground == Material.PODZOL || ground == Material.COARSE_DIRT
                || ground == Material.ROOTED_DIRT || ground == Material.MUD)) {
            return false; // a log on a block someone placed is not a tree
        }
        Material wood = base.getType();
        if (wood.name().startsWith("STRIPPED_") || wood.name().endsWith("_WOOD") || wood.name().endsWith("_HYPHAE")) {
            return false; // a log someone stripped, or made into wood, was never a tree
        }
        List<Block> logs = trunk(base, wood);
        // Don't take more than is owed (a little over is fine, so a tree is never left half-felled).
        if (logs.isEmpty() || logs.size() > WorldMarks.owed(settlement, ResourceType.WOOD) / WorldMarks.UNITS_PER_LOG + 2
                || !grown(logs) || builtOn(logs)) {
            return false;
        }
        for (int i = logs.size() - 1; i >= 0; i--) {
            logs.get(i).setType(Material.AIR, true); // with physics: the leaves decay on their own, as when a player fells a tree
        }
        Material sapling = Material.matchMaterial(wood.name().replace("STRIPPED_", "").replace("_LOG", "_SAPLING")
                .replace("_WOOD", "_SAPLING").replace("MANGROVE_SAPLING", "MANGROVE_PROPAGULE"));
        if (sapling != null && base.getType().isAir()) {
            base.setType(sapling, true);
        }
        WorldMarks.pay(settlement, ResourceType.WOOD, logs.size() * WorldMarks.UNITS_PER_LOG);
        plugin.requestSave();
        return true;
    }

    /** The logs of one tree, from its base up: logs of the same wood joined to it, near its trunk, up to a limit. */
    private static List<Block> trunk(Block base, Material wood) {
        List<Block> out = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(base);
        seen.add(base);
        while (!queue.isEmpty() && out.size() < MAX_LOGS_PER_TREE) {
            Block b = queue.poll();
            out.add(b);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = b.getRelative(dx, dy, dz);
                        if (n.getType() == wood && Math.abs(n.getX() - base.getX()) <= 6 && Math.abs(n.getZ() - base.getZ()) <= 6
                                && seen.add(n)) {
                            queue.add(n);
                        }
                    }
                }
            }
        }
        return out;
    }

    /** How far from a column's tree the search reads, in blocks: the trunk search and the crown check. */
    private static final int TREE_REACH = 8;

    /** A tree the game grew has leaves that are not a player's (persistent) next to its highest log. */
    private static boolean grown(List<Block> logs) {
        Block crown = logs.get(0);
        for (Block b : logs) {
            if (b.getY() > crown.getY()) {
                crown = b;
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block near = crown.getRelative(dx, dy, dz);
                    if (Tag.LEAVES.isTagged(near.getType()) && near.getBlockData() instanceof Leaves leaves && !leaves.isPersistent()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * True if something is built against the logs: any solid block beside or above them that is not another log of the
     * tree, leaves, or the ground under its base. A cabin wall, a roof, a platform or a ladder on a trunk is a player's.
     */
    private static boolean builtOn(List<Block> logs) {
        Set<Block> own = new HashSet<>(logs);
        Block base = logs.get(0);
        for (Block log : logs) {
            for (BlockFace face : new BlockFace[] {BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block next = log.getRelative(face);
                Material m = next.getType();
                if (own.contains(next) || m.isAir() || Tag.LEAVES.isTagged(m) || Tag.REPLACEABLE.isTagged(m) || next.isLiquid()
                        || Tag.LOGS.isTagged(m) && next.getType() == log.getType() || Tag.FLOWERS.isTagged(m)) {
                    continue;
                }
                if (m == Material.VINE || m == Material.COCOA || m == Material.BEE_NEST || m == Material.MOSS_CARPET
                        || m == Material.SNOW) {
                    continue; // things a tree grows or carries
                }
                return true;
            }
        }
        return base.getRelative(BlockFace.DOWN).getType().isAir();
    }

    // ----- shared -----

    private static Rect extent(VillagePlan plan) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        List<Rect> rects = new ArrayList<>();
        plan.lots().forEach(l -> rects.add(l.rect()));
        plan.roads().forEach(r -> rects.add(r.rect()));
        if (plan.square() != null) {
            rects.add(plan.square());
        }
        for (Rect r : rects) {
            minX = Math.min(minX, r.x());
            minZ = Math.min(minZ, r.z());
            maxX = Math.max(maxX, r.maxX());
            maxZ = Math.max(maxZ, r.maxZ());
        }
        return rects.isEmpty() ? null : new Rect(minX, minZ, maxX - minX + 1, maxZ - minZ + 1);
    }

    private static boolean inPlan(VillagePlan plan, Rect area) {
        if (plan.square() != null && plan.square().overlaps(area)) {
            return true;
        }
        for (VillagePlan.Lot lot : plan.lots()) {
            if (lot.rect().inflated(1).overlaps(area)) {
                return true;
            }
        }
        for (VillagePlan.Road road : plan.roads()) {
            if (road.rect().inflated(1).overlaps(area)) {
                return true;
            }
        }
        return false;
    }

    private static boolean loaded(World world, int x, int z, int size) {
        for (int cx = x >> 4; cx <= (x + size) >> 4; cx++) {
            for (int cz = z >> 4; cz <= (z + size) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Set<Long> chunksOf(int x, int z, int size) {
        Set<Long> out = new HashSet<>();
        for (int cx = x >> 4; cx <= (x + size) >> 4; cx++) {
            for (int cz = z >> 4; cz <= (z + size) >> 4; cz++) {
                out.add(((long) cx << 32) ^ (cz & 0xffffffffL));
            }
        }
        return out;
    }
}
