package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.BiomeSet;
import io.github.skyeberhard.hamletfolk.core.Blueprint;
import io.github.skyeberhard.hamletfolk.core.BuildingType;
import io.github.skyeberhard.hamletfolk.core.Construction;
import io.github.skyeberhard.hamletfolk.core.ConstructionProject;
import io.github.skyeberhard.hamletfolk.core.HeightSource;
import io.github.skyeberhard.hamletfolk.core.Rect;
import io.github.skyeberhard.hamletfolk.core.Resident;
import io.github.skyeberhard.hamletfolk.core.TerrainPad;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.TemplateCatalog;
import io.github.skyeberhard.hamletfolk.core.VillagePlan;
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
    private static final int GROUND_PER_PASS = 48;
    /** The most a block is placed again if it never ends up matching (a torch that pops off, say), before it is left. */
    private static final int MAX_ATTEMPTS = 3;
    private static final int PLAYER_RANGE = 128;
    private static final int EXEMPT_RANGE = 24;
    private static final int SIGN_TRIES = 5;

    private final HamletfolkPlugin plugin;
    private final SettlementService service;
    /** Not saved: blocks given up on, and how often each was placed, per project. Start again after a restart. */
    private final Map<Integer, Map<Long, Integer>> attempts = new HashMap<>();
    private final Map<Integer, Set<Long>> skipped = new HashMap<>();
    private final Map<Integer, Integer> signTries = new HashMap<>();
    /** Not saved: how many passes of levelling a project has had, so water or sand flowing back cannot hold it up for ever. */
    private final Map<Integer, Integer> gradingPasses = new HashMap<>();
    private static final int MAX_GRADING_PASSES = 60;
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
                groundOf(world));
        if (queued.isPresent()) {
            plugin.requestSave();
        }
    }

    /** The ground as the grading plan sees it: the surface under any trees and plants, and where it is wet, the floor. */
    private static HeightSource groundOf(World world) {
        return new HeightSource() {
            @Override
            public int height(int x, int z) {
                return surfaceY(world, x, z);
            }

            @Override
            public boolean water(int x, int z) {
                return world.isChunkLoaded(x >> 4, z >> 4)
                        && world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES).isLiquid();
            }

            @Override
            public int floor(int x, int z) {
                int y = height(x, z);
                while (y != HeightSource.UNKNOWN && y > world.getMinHeight() && world.getBlockAt(x, y, z).isLiquid()) {
                    y--;
                }
                return y;
            }
        };
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
        if (m == Material.WATER || m == Material.LAVA) {
            return false; // a lake is a surface to measure, not a plant to look through
        }
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
                        .map(b -> b.rotated(project.oldTurns()).shifted(project.shiftX(), 0, project.shiftZ())).orElse(null) : null;
        Blueprint bp = target.get().rotated(project.turns());
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
        // Level the ground once, for a new building only: an upgrade is built over the old one, and a building already
        // standing (after a restart, say) must not be graded again over its own walls.
        if (!project.graded() && !project.isUpgrade() && project.blocksLeft() < 0) {
            int passes = gradingPasses.merge(project.id(), 1, Integer::sum);
            int graded = passes > MAX_GRADING_PASSES ? 0 : prepareGround(world, bp, ox, oy, oz, project.biomeSet());
            if (graded < 0) {
                giveUp(settlement, project, "the ground there cannot be levelled", true);
                return;
            }
            if (graded > 0) {
                return; // clearing and levelling first, a pass at a time
            }
        }
        if (!project.graded()) {
            project.setGraded(true);
            gradingPasses.remove(project.id());
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
        gradingPasses.remove(project.id());
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
                    if (clearable(block, y <= 0 || columnNatural(world, ox + x, oz + z))) {
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
    /** The ground itself, listed rather than taken from a tag (the game's dirt tag does not hold every grass block). */
    private static final java.util.Set<Material> TERRAIN = java.util.EnumSet.of(Material.GRASS_BLOCK, Material.DIRT,
            Material.COARSE_DIRT, Material.PODZOL, Material.ROOTED_DIRT, Material.MYCELIUM, Material.MUD, Material.DIRT_PATH,
            Material.MOSS_BLOCK, Material.GRAVEL, Material.CLAY, Material.SAND, Material.RED_SAND, Material.SNOW_BLOCK,
            Material.POWDER_SNOW, Material.WATER, Material.ICE, Material.PACKED_ICE);

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
        if (TERRAIN.contains(m) || Tag.SAND.isTagged(m) || Tag.SNOW.isTagged(m)) {
            return true;
        }
        return groundLevel && (m == Material.STONE || m == Material.DEEPSLATE || m == Material.SANDSTONE
                || m == Material.RED_SANDSTONE || m == Material.TUFF || Tag.BASE_STONE_OVERWORLD.isTagged(m));
    }

    /**
     * Free ground work, a few dozen blocks a pass: levels the ground to the height the building was set at, cutting hills
     * and filling hollows with the biome's blocks (the grading plan, with a blended edge), and clears trees and plants
     * out of the building's space. Only terrain and growth are ever changed. Returns how many blocks it changed, or -1
     * if the ground cannot be levelled at all.
     */
    private int prepareGround(World world, Blueprint bp, int ox, int oy, int oz, String style) {
        TerrainPad.Pad pad = TerrainPad.compute(new Rect(ox, oz, bp.width(), bp.depth()), Construction.PAD_BUFFER,
                groundOf(world), oy);
        if (!pad.valid()) {
            return -1;
        }
        Material[] soil = soilFor(style); // surface, fill, foundation
        Map<Long, Boolean> natural = new HashMap<>();
        int done = 0;
        for (TerrainPad.Change change : pad.changes()) {
            if (done >= GROUND_PER_PASS) {
                return done;
            }
            if (!natural.computeIfAbsent(((long) change.x() << 32) ^ (change.z() & 0xffffffffL), k -> columnNatural(world, change.x(), change.z()))) {
                continue; // something built stands on this column (a neighbour's wall, say): its ground is not ours to cut
            }
            Block block = world.getBlockAt(change.x(), change.y(), change.z());
            Material want = switch (change.role()) {
                case AIR -> Material.AIR;
                case SURFACE -> soil[0];
                case FILL -> soil[1];
                case FOUNDATION -> soil[2];
            };
            if (block.getType() == want || !clearable(block, true)) {
                continue; // already right, or not ours to change
            }
            block.setType(want, false);
            done++;
        }
        Set<Long> cells = new HashSet<>();
        for (Blueprint.Block b : bp.blocks()) {
            cells.add(key(b));
        }
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
            }
        }
        return done;
    }

    /**
     * True if the top of a column is plain terrain (grass, dirt, sand, rock, or a lake) and not part of a building: then
     * the stone and soil under it can be cut like the ground it is. A column with a roof or a wall on top is left alone.
     */
    private static boolean columnNatural(World world, int x, int z) {
        int y = surfaceY(world, x, z);
        return y != Construction.UNKNOWN_GROUND && clearable(world.getBlockAt(x, y, z), true);
    }

    /** The surface, soil and foundation blocks of a village style. */
    private static Material[] soilFor(String style) {
        return BiomeSet.DESERT.equals(style)
                ? new Material[] {Material.SAND, Material.SAND, Material.SANDSTONE}
                : new Material[] {Material.GRASS_BLOCK, Material.DIRT, Material.STONE};
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
        pave(world, settlement, project, bp);
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
        gradingPasses.remove(project.id());
        plugin.requestSave();
    }

    /** How far along the planned streets a finished building lays path (the street grows outward as the village does). */
    private static final int PAVE_RANGE = 20;
    private static final int WALKWAY_MAX = 14;
    private static final java.util.Set<Material> PATHABLE = java.util.EnumSet.of(Material.GRASS_BLOCK, Material.DIRT,
            Material.COARSE_DIRT, Material.PODZOL, Material.ROOTED_DIRT, Material.MYCELIUM);

    /**
     * Gives a finished building its street: the planned streets and the square within reach of it are laid with path
     * blocks, and a walkway of path leads from its entrance out to the street. Only plain ground is changed (never
     * anything built, and sand stays sand); streets follow the land and are not graded.
     */
    private void pave(World world, Settlement settlement, ConstructionProject project, Blueprint bp) {
        VillagePlan plan = settlement.plan();
        if (plan == null) {
            return;
        }
        List<Rect> streets = new ArrayList<>();
        plan.roads().forEach(r -> streets.add(r.rect()));
        if (plan.square() != null) {
            streets.add(plan.square());
        }
        Rect near = new Rect(project.x(), project.z(), bp.width(), bp.depth()).inflated(PAVE_RANGE);
        for (Rect street : streets) {
            for (int x = Math.max(street.x(), near.x()); x <= Math.min(street.maxX(), near.maxX()); x++) {
                for (int z = Math.max(street.z(), near.z()); z <= Math.min(street.maxZ(), near.maxZ()); z++) {
                    layPath(world, x, z);
                }
            }
        }
        java.util.OptionalInt front = bp.front();
        if (front.isEmpty()) {
            return;
        }
        int dx = front.getAsInt() == 1 ? 1 : front.getAsInt() == 3 ? -1 : 0;
        int dz = front.getAsInt() == 2 ? 1 : front.getAsInt() == 0 ? -1 : 0;
        int x = dx == 0 ? project.x() + bp.width() / 2 : dx > 0 ? project.x() + bp.width() : project.x() - 1;
        int z = dz == 0 ? project.z() + bp.depth() / 2 : dz > 0 ? project.z() + bp.depth() : project.z() - 1;
        for (int step = 0; step < WALKWAY_MAX; step++, x += dx, z += dz) {
            final int px = x;
            final int pz = z;
            if (streets.stream().anyMatch(r -> r.contains(px, pz))) {
                break;
            }
            layPath(world, x, z);
        }
    }

    /** Turns the plain ground at a column into path, if that is what is there. */
    private static void layPath(World world, int x, int z) {
        int y = surfaceY(world, x, z);
        if (y == Construction.UNKNOWN_GROUND) {
            return;
        }
        Block ground = world.getBlockAt(x, y, z);
        if (!PATHABLE.contains(ground.getType())) {
            return;
        }
        Block above = ground.getRelative(0, 1, 0);
        if (!above.getType().isAir()) {
            if (!(Tag.FLOWERS.isTagged(above.getType()) || Tag.REPLACEABLE.isTagged(above.getType()))
                    || above.isLiquid()) {
                return; // something stands there
            }
            above.setType(Material.AIR, false);
        }
        ground.setType(Material.DIRT_PATH, false);
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
