package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.BiomeSet;
import io.github.skyeberhard.hamletfolk.core.Blueprint;
import io.github.skyeberhard.hamletfolk.core.BuildingType;
import io.github.skyeberhard.hamletfolk.core.Construction;
import io.github.skyeberhard.hamletfolk.core.ConstructionProject;
import io.github.skyeberhard.hamletfolk.core.Resident;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.TemplateCatalog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

/**
 * R4.7, R4.8: the Paper side of construction. Once a second it lets each village decide what to build (the rules are in
 * core's {@link Construction}), and carries out the open project a little at a time: it clears and levels the ground,
 * pays for each block from the ledger and places it, and when nothing is left to do it writes the sign, registers the
 * building and tells core the project is done. The remaining work is never stored: it is worked out from the world each
 * pass, so a restart, a half-built wall or a block a player removed all just show up as work still to do.
 *
 * <p>Safety: a site is checked once before the first block, and the project is given up if anything a player made is
 * on it (only terrain, trees and plants may be cleared). Nothing is built while an exempt villager is near, while no
 * player is within range, or while the chunks are unloaded.
 */
final class ConstructionService {
    private static final long PERIOD_TICKS = 20;
    /** Blocks paid for and placed per pass: about this many a second. */
    private static final int BLOCKS_PER_PASS = 4;
    /** Free ground work (clearing trees, filling hollows) per pass. */
    private static final int GROUND_PER_PASS = 24;
    /** The most a block is placed again if it never ends up matching (a torch that pops off, say), before it is left. */
    private static final int MAX_ATTEMPTS = 3;
    private static final int PLAYER_RANGE = 128;
    private static final int EXEMPT_RANGE = 24;
    private static final int FILL_DEPTH = 4;
    private static final int SIGN_TRIES = 5;

    private final HamletfolkPlugin plugin;
    private final SettlementService service;
    /** Not saved: blocks given up on, and how often each was placed, per project. Start again after a restart. */
    private final Map<Integer, Map<Long, Integer>> attempts = new HashMap<>();
    private final Map<Integer, Set<Long>> skipped = new HashMap<>();
    private final Map<Integer, Integer> signTries = new HashMap<>();
    private final Map<java.util.UUID, Long> lastLook = new HashMap<>();
    private static final long LOOK_EVERY_MS = 10_000;

    ConstructionService(HamletfolkPlugin plugin, SettlementService service) {
        this.plugin = plugin;
        this.service = service;
    }

    void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS * 5, PERIOD_TICKS);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("construction.enabled", true);
    }

    private void tick() {
        if (!enabled()) {
            return;
        }
        for (Settlement settlement : new ArrayList<>(service.registry().settlements())) {
            World world = Bukkit.getWorld(settlement.world());
            if (world == null || !service.inScope(world) || settlement.isAbandoned() || settlement.population() == 0) {
                continue;
            }
            try {
                decide(settlement, world);
                settlement.openProject().filter(p -> p.status() == ConstructionProject.Status.ACTIVE)
                        .ifPresent(p -> work(settlement, world, p));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Construction failed in " + settlement.name(), e);
            }
        }
    }

    // ----- deciding -----

    private void decide(Settlement settlement, World world) {
        if (settlement.openProject().isPresent() || settlement.plan() == null
                || Construction.decidedToday(settlement, settlement.lastSimulatedDay())
                || !world.isChunkLoaded(settlement.centerX() >> 4, settlement.centerZ() >> 4)) {
            return;
        }
        // Thinking about it costs a little, so a village that cannot build yet looks again every few seconds, not every tick.
        long now = System.currentTimeMillis();
        if (now - lastLook.getOrDefault(settlement.id(), 0L) < LOOK_EVERY_MS) {
            return;
        }
        lastLook.put(settlement.id(), now);
        String style = BiomeSet.forBiome(world.getComputedBiome(settlement.centerX(), 64, settlement.centerZ()).getKey().getKey());
        Optional<ConstructionProject> queued = Construction.propose(settlement, settlement.lastSimulatedDay(), style,
                service.treasuryLimit(settlement), plugin.templates().catalog(), plugin.templates()::blueprint,
                (x, z) -> surfaceY(world, x, z));
        if (queued.isPresent()) {
            plugin.requestSave();
        }
    }

    /** The y of the ground at a column (trees and plants do not count), or {@link Construction#UNKNOWN_GROUND} if unloaded. */
    private static int surfaceY(World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return Construction.UNKNOWN_GROUND;
        }
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        while (y > world.getMinHeight() && isGrowth(world.getBlockAt(x, y, z).getType())) {
            y--;
        }
        return y;
    }

    private static boolean isGrowth(Material m) {
        return Tag.LEAVES.isTagged(m) || Tag.LOGS.isTagged(m) || Tag.FLOWERS.isTagged(m) || Tag.SAPLINGS.isTagged(m)
                || Tag.REPLACEABLE.isTagged(m) || m.isAir();
    }

    // ----- working -----

    private void work(Settlement settlement, World world, ConstructionProject project) {
        Optional<Resident> builder = project.builder() == null ? Optional.empty() : settlement.resident(project.builder());
        if (builder.isEmpty()) {
            return;
        }
        List<TemplateCatalog.Template> ladder = plugin.templates().catalog().ladder(project.type(), project.biomeSet());
        if (project.tier() > ladder.size()) {
            giveUp(settlement, project, "there is no such design any more", false);
            return;
        }
        Optional<Blueprint> target = plugin.templates().blueprint(ladder.get(project.tier() - 1));
        if (target.isEmpty()) {
            giveUp(settlement, project, "the design could not be found", false);
            return;
        }
        Blueprint old = project.isUpgrade() && project.previousTier() <= ladder.size()
                ? plugin.templates().blueprint(ladder.get(project.previousTier() - 1))
                        .map(b -> b.shifted(project.shiftX(), 0, project.shiftZ())).orElse(null) : null;
        Blueprint bp = target.get();
        int ox = project.x();
        int oy = project.y();
        int oz = project.z();
        if (!world.isChunkLoaded(ox >> 4, oz >> 4) || !world.isChunkLoaded((ox + bp.width()) >> 4, (oz + bp.depth()) >> 4)
                || !world.isChunkLoaded(ox >> 4, (oz + bp.depth()) >> 4) || !world.isChunkLoaded((ox + bp.width()) >> 4, oz >> 4)) {
            return;
        }
        Location centre = new Location(world, ox + bp.width() / 2.0, oy, oz + bp.depth() / 2.0);
        if (!playerNear(centre) || exemptNear(centre)) {
            return;
        }
        if (!project.siteChecked()) {
            String problem = checkSite(world, bp, old, ox, oy, oz);
            if (problem != null) {
                giveUp(settlement, project, problem, true);
                return;
            }
            project.setSiteChecked(true);
        }
        if (prepareGround(world, bp, ox, oy, oz)) {
            return; // clearing and levelling first, a pass at a time
        }
        Set<Long> given = skipped.computeIfAbsent(project.id(), k -> new HashSet<>());
        List<Construction.Step> steps = new ArrayList<>(Construction.worklist(bp, old, b -> materialAt(world, ox, oy, oz, b)));
        steps.removeIf(step -> given.contains(key(step.block())));
        project.setBlocksLeft(steps.size());
        if (steps.isEmpty()) {
            complete(settlement, world, project, bp);
            return;
        }
        Map<Long, Integer> tries = attempts.computeIfAbsent(project.id(), k -> new HashMap<>());
        Map<Long, String> oldBlocks = new HashMap<>();
        if (old != null) {
            for (Blueprint.Block b : old.blocks()) {
                oldBlocks.put(key(b), b.material());
            }
        }
        int placed = 0;
        long day = settlement.lastSimulatedDay();
        for (Construction.Step step : steps) {
            if (placed >= BLOCKS_PER_PASS) {
                break;
            }
            Blueprint.Block block = step.block();
            long k = key(block);
            Block at = world.getBlockAt(ox + block.x(), oy + block.y(), oz + block.z());
            if (!step.demolish()) {
                // Something a player put here since the site was checked: not ours to build over.
                String was = oldBlocks.get(k);
                if (!clearable(at, block.y() <= 0)
                        && !(was != null && Construction.matches(TemplateLibrary.materialOf(at.getBlockData()), was))) {
                    giveUp(settlement, project, "something has been built there ("
                            + at.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ")", true);
                    return;
                }
                BlockData data;
                try {
                    data = TemplateLibrary.dataOf(block.material());
                } catch (IllegalArgumentException e) {
                    given.add(k); // a block this server does not know: leave a gap rather than wait for ever
                    continue;
                }
                if (tries.getOrDefault(k, 0) >= MAX_ATTEMPTS) {
                    given.add(k); // placed and still not right (it pops off, say): stop paying for it
                    continue;
                }
                if (!Construction.charge(settlement, project, block.material(), day)) {
                    break; // the stores ran dry: wait for more, however long it takes
                }
                tries.merge(k, 1, Integer::sum);
                at.setBlockData(data, false);
            } else {
                at.setType(Material.AIR, false);
            }
            placed++;
        }
        if (placed > 0) {
            plugin.requestSave(); // the payments in flight are saved with the project
        }
    }

    private void giveUp(Settlement settlement, ConstructionProject project, String reason, boolean abandonLot) {
        Construction.cancel(settlement, project, settlement.lastSimulatedDay(), reason, abandonLot);
        attempts.remove(project.id());
        skipped.remove(project.id());
        signTries.remove(project.id());
        plugin.requestSave();
    }

    private static long key(Blueprint.Block b) {
        return ((long) (b.y() + 512) << 40) | ((long) (b.z() + 512) << 20) | (b.x() + 512);
    }

    private static String materialAt(World world, int ox, int oy, int oz, Blueprint.Block b) {
        return TemplateLibrary.materialOf(world.getBlockAt(ox + b.x(), oy + b.y(), oz + b.z()).getBlockData());
    }

    private boolean playerNear(Location at) {
        for (Player player : at.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= (double) PLAYER_RANGE * PLAYER_RANGE) {
                return true;
            }
        }
        return false;
    }

    private boolean exemptNear(Location at) {
        for (org.bukkit.entity.Entity entity : at.getWorld().getNearbyEntities(at, EXEMPT_RANGE, EXEMPT_RANGE, EXEMPT_RANGE)) {
            if (entity instanceof Villager villager && service.isIgnored(villager)) {
                return true;
            }
        }
        return false;
    }

    // ----- the site -----

    /**
     * Whether everything inside the footprint is something it is fine to build over: air, terrain, trees and plants,
     * or (for an upgrade) a block of the building being replaced. Anything else was put there by a player.
     */
    private static String checkSite(World world, Blueprint bp, Blueprint old, int ox, int oy, int oz) {
        Map<Long, String> oldBlocks = new HashMap<>();
        if (old != null) {
            for (Blueprint.Block b : old.blocks()) {
                oldBlocks.put(key(b), b.material());
            }
        }
        for (int y = 0; y < bp.height(); y++) { // below the floor is the ground: never ours to judge
            for (int z = 0; z < bp.depth(); z++) {
                for (int x = 0; x < bp.width(); x++) {
                    Block block = world.getBlockAt(ox + x, oy + y, oz + z);
                    if (clearable(block, y <= 0)) {
                        continue;
                    }
                    String was = oldBlocks.get(key(new Blueprint.Block(x, y, z, "")));
                    if (was != null && Construction.matches(TemplateLibrary.materialOf(block.getBlockData()), was)) {
                        continue;
                    }
                    return "something has been built there (" + block.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ")";
                }
            }
        }
        return null;
    }

    /**
     * Whether a block is terrain, a plant or a tree and so fine to build over or clear. Anything else is taken to be
     * something a player made. Leaves count only if the game grew them (a hedge a player placed is persistent), logs only
     * if they stand among such leaves, and at floor level or below, plain stone and sandstone are just the ground.
     */
    static boolean clearable(Block block, boolean groundLevel) {
        Material m = block.getType();
        if (m.isAir() || Tag.FLOWERS.isTagged(m) || Tag.SAPLINGS.isTagged(m) || Tag.REPLACEABLE.isTagged(m)) {
            return true;
        }
        if (Tag.LEAVES.isTagged(m)) {
            return block.getBlockData() instanceof Leaves leaves && !leaves.isPersistent();
        }
        if (Tag.LOGS.isTagged(m)) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -1; dy <= 6; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        Block near = block.getRelative(dx, dy, dz);
                        if (Tag.LEAVES.isTagged(near.getType()) && near.getBlockData() instanceof Leaves l && !l.isPersistent()) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        if (Tag.DIRT.isTagged(m) || Tag.SAND.isTagged(m) || Tag.SNOW.isTagged(m) || m == Material.GRAVEL
                || m == Material.CLAY || m == Material.SNOW_BLOCK || m == Material.WATER) {
            return true;
        }
        return groundLevel && (m == Material.STONE || m == Material.DEEPSLATE || m == Material.SANDSTONE
                || m == Material.RED_SANDSTONE || m == Material.TUFF || Tag.BASE_STONE_OVERWORLD.isTagged(m));
    }

    /**
     * Free ground work: clears trees, plants and hills out of the building's space, and fills hollows under its floor
     * with dirt. Returns true if it did any (the building waits a pass), so it settles before the first block is paid for.
     */
    private boolean prepareGround(World world, Blueprint bp, int ox, int oy, int oz) {
        Set<Long> cells = new HashSet<>();
        Set<Long> columnsBelowFloor = new HashSet<>();
        for (Blueprint.Block b : bp.blocks()) {
            cells.add(key(b));
            if (b.y() < 0) {
                columnsBelowFloor.add(((long) b.z() << 20) | b.x());
            }
        }
        int done = 0;
        for (int z = 0; z < bp.depth() && done < GROUND_PER_PASS; z++) {
            for (int x = 0; x < bp.width() && done < GROUND_PER_PASS; x++) {
                for (int y = 1; y < bp.height(); y++) { // the floor level itself is left as the ground is
                    Block block = world.getBlockAt(ox + x, oy + y, oz + z);
                    if (!block.getType().isAir() && clearable(block, false)
                            && !cells.contains(key(new Blueprint.Block(x, y, z, "")))) {
                        block.setType(Material.AIR, false);
                        done++;
                    }
                }
                if (!columnsBelowFloor.contains(((long) z << 20) | x)) {
                    for (int y = -1; y >= -FILL_DEPTH; y--) {
                        Block below = world.getBlockAt(ox + x, oy + y, oz + z);
                        if (!below.getType().isAir() && !below.isLiquid() && !Tag.REPLACEABLE.isTagged(below.getType())) {
                            break;
                        }
                        below.setType(Material.DIRT, false);
                        done++;
                    }
                }
            }
        }
        return done > 0;
    }

    // ----- finishing -----

    private void complete(Settlement settlement, World world, ConstructionProject project, Blueprint bp) {
        BuildingType type = project.type();
        Optional<Blueprint.Block> own = bp.sign();
        Block signBlock;
        if (own.isPresent()) {
            signBlock = world.getBlockAt(project.x() + own.get().x(), project.y() + own.get().y(), project.z() + own.get().z());
        } else {
            signBlock = standingSignSpot(world, project, bp);
            if (signBlock != null) {
                signBlock.setType(Material.OAK_SIGN, false);
            }
        }
        boolean registered = false;
        if (signBlock != null && signBlock.getState() instanceof Sign sign) {
            sign.getSide(Side.FRONT).line(1, Component.text(type.signText()));
            sign.update(true, false);
            replacePreviousBuilding(settlement, world, project);
            Location at = signBlock.getLocation();
            registered = service.registerBuilding(settlement, type, at, "the builders") == Settlement.Registration.REGISTERED;
            if (registered) {
                project.setSign(at.getBlockX(), at.getBlockY(), at.getBlockZ());
            }
        }
        // Without a registered sign the planner would still think the building missing and build another: try a few
        // more times (a corner may be blocked for now) before giving up on the sign.
        if (!registered && signTries.merge(project.id(), 1, Integer::sum) < SIGN_TRIES) {
            return;
        }
        if (!registered) {
            settlement.record(settlement.lastSimulatedDay(), io.github.skyeberhard.hamletfolk.core.HistoryEvent.Kind.BUILDING,
                    "The new " + type.label().toLowerCase(Locale.ROOT) + " has no sign: place a " + type.signText()
                            + " sign on it to put it to use.");
        }
        Construction.finish(settlement, project, settlement.lastSimulatedDay());
        attempts.remove(project.id());
        skipped.remove(project.id());
        signTries.remove(project.id());
        plugin.requestSave();
    }

    /** An upgrade replaces the building it was built over: its sign goes and its registration with it. */
    private void replacePreviousBuilding(Settlement settlement, World world, ConstructionProject project) {
        if (!project.isUpgrade()) {
            return;
        }
        for (ConstructionProject earlier : settlement.projects()) {
            if (earlier.lotId() == project.lotId() && earlier.id() < project.id()
                    && earlier.status() == ConstructionProject.Status.DONE && earlier.signY() != ConstructionProject.NO_SIGN) {
                service.removeBuildingAt(world, earlier.signX(), earlier.signY(), earlier.signZ());
                Block old = world.getBlockAt(earlier.signX(), earlier.signY(), earlier.signZ());
                if (Tag.ALL_SIGNS.isTagged(old.getType())) {
                    old.setType(Material.AIR, false);
                }
            }
        }
    }

    /** A free spot for a sign beside a vanilla building, which has none of its own: just outside a corner, on the ground. */
    private static Block standingSignSpot(World world, ConstructionProject project, Blueprint bp) {
        int[][] candidates = {{-1, -1}, {bp.width(), -1}, {-1, bp.depth()}, {bp.width(), bp.depth()}};
        for (int[] c : candidates) {
            int x = project.x() + c[0];
            int z = project.z() + c[1];
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Block ground = world.getBlockAt(x, surfaceY(world, x, z), z);
            Block spot = ground.getRelative(0, 1, 0);
            if (ground.getType().isSolid() && (spot.getType().isAir() || Tag.REPLACEABLE.isTagged(spot.getType()))) {
                return spot;
            }
        }
        return null;
    }
}
