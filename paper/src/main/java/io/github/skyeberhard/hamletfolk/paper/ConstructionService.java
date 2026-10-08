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
import io.github.skyeberhard.hamletfolk.core.StreetGrade;
import io.github.skyeberhard.hamletfolk.core.TemplateCatalog;
import io.github.skyeberhard.hamletfolk.core.VillagePlan;
import io.github.skyeberhard.hamletfolk.core.Works;
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
    /** Not saved: the spots of a work found standing (so a post in a chunk that has since unloaded still counts), per project. */
    private final Map<Integer, Set<Long>> standingSpots = new HashMap<>();
    /** Not saved: the village day on which a work last made progress (placed a post, or waited for wood). */
    private final Map<Integer, Long> workProgress = new HashMap<>();
    /** Village days a work may go with nothing placed and nothing awaited before it is finished with gaps where it could not be built. */
    private static final int WORKS_STALL_DAYS = 6;
    private static final int MAX_GRADING_PASSES = 60;
    private final Map<java.util.UUID, Long> lastLook = new HashMap<>();
    /** Not saved: the village day on which each village last had its plan loaded to decide with nobody near (R4.22). */
    private final Map<java.util.UUID, Long> lastHoldDay = new HashMap<>();
    /** Not saved: when (real time) each village last had its plan loaded to decide, so a fast-forward does not churn chunks. */
    private final Map<java.util.UUID, Long> lastHoldMs = new HashMap<>();
    private static final long HOLD_EVERY_MS = 60_000;
    private static final long LOOK_EVERY_MS = 10_000;

    /** R4.22: chunks held loaded for one purpose ("p<id>" for an open project, "d<id>" for a village deciding), and since when. */
    private record Held(String world, Set<Long> chunks, long since) {
    }

    private final Map<String, Held> held = new HashMap<>();
    /** How many purposes hold each chunk (world|chunk), so the plugin's one ticket on it is only let go when none does. */
    private final Map<String, Integer> tickets = new HashMap<>();
    /** How long a village waits for the chunks of its plan to load before it decides with what is loaded. */
    private static final long LOAD_WAIT_MS = 20_000;
    private static final long STALE_DECISION_MS = 60_000;

    ConstructionService(HamletfolkPlugin plugin, SettlementService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /** R4.22: whether villages build with no player near, loading just the chunks they need (off unless configured). */
    private boolean unattended() {
        return plugin.getConfig().getBoolean("construction.unattended", false);
    }

    /** R4.22: a multiplier on how fast builders work (1 is 4 blocks a second for a new builder). */
    private double speed() {
        return Math.max(0.25, Math.min(20.0, plugin.getConfig().getDouble("construction.speed", 1.0)));
    }

    /** R4.27: the most blocks any builder places in a pass, however fast the village runs. */
    private static final int MAX_BLOCKS_PER_PASS = 200;

    /** How fast a village's builders work: the setting, times its fast-forward speed (R4.27). */
    private double speed(Settlement settlement) {
        return speed() * service.fastForwardSpeed(settlement);
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    /** The chunks covering a block rectangle, widened by {@code margin} blocks. */
    private static Set<Long> chunksOf(int x, int z, int width, int depth, int margin) {
        Set<Long> out = new HashSet<>();
        for (int cx = (x - margin) >> 4; cx <= (x + width + margin) >> 4; cx++) {
            for (int cz = (z - margin) >> 4; cz <= (z + depth + margin) >> 4; cz++) {
                out.add(chunkKey(cx, cz));
            }
        }
        return out;
    }

    /** Holds the chunks loaded for a purpose (idempotent), asking for the ones not loaded yet. */
    private Held hold(World world, String key, Set<Long> chunks) {
        Held existing = held.get(key);
        if (existing != null) {
            return existing;
        }
        Held fresh = new Held(world.getName(), new HashSet<>(chunks), System.currentTimeMillis());
        held.put(key, fresh);
        for (long c : fresh.chunks()) {
            int cx = (int) (c >> 32);
            int cz = (int) c;
            if (tickets.merge(world.getName() + "|" + c, 1, Integer::sum) == 1) {
                world.addPluginChunkTicket(cx, cz, plugin);
            }
            if (!world.isChunkLoaded(cx, cz)) {
                world.getChunkAtAsync(cx, cz, false); // ask for it now rather than wait for the ticket to take effect
            }
        }
        return fresh;
    }

    private void release(String key) {
        Held gone = held.remove(key);
        World world = gone == null ? null : Bukkit.getWorld(gone.world());
        if (gone == null) {
            return;
        }
        for (long c : gone.chunks()) {
            String ticket = gone.world() + "|" + c;
            Integer left = tickets.computeIfPresent(ticket, (k, n) -> n > 1 ? n - 1 : null);
            if (left == null && world != null) {
                world.removePluginChunkTicket((int) (c >> 32), (int) c, plugin);
            }
        }
    }

    /** Lets every chunk go (the plugin is stopping, or unattended building was switched off). */
    void releaseAll() {
        for (String key : new ArrayList<>(held.keySet())) {
            release(key);
        }
    }

    private static boolean allLoaded(World world, Set<Long> chunks) {
        for (long c : chunks) {
            if (!world.isChunkLoaded((int) (c >> 32), (int) c)) {
                return false;
            }
        }
        return true;
    }

    /** Lets go of chunks held for a project that is no longer being built, or a decision that was never finished. */
    private void releaseFinished() {
        java.util.Set<String> active = new HashSet<>();
        for (Settlement settlement : service.registry().settlements()) {
            World world = Bukkit.getWorld(settlement.world());
            if (world == null || !service.inScope(world) || settlement.isAbandoned() || settlement.population() == 0) {
                continue; // tick() does not build here, so nothing is held for it
            }
            settlement.openProject().filter(p -> p.status() == ConstructionProject.Status.ACTIVE)
                    .ifPresent(p -> active.add("p" + p.id() + "@" + settlement.id()));
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Held> entry : new ArrayList<>(held.entrySet())) {
            String key = entry.getKey();
            boolean stale = key.startsWith("d") ? now - entry.getValue().since() > STALE_DECISION_MS : !active.contains(key);
            if (stale || !unattended()) {
                release(key);
            }
        }
    }

    void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS * 5, PERIOD_TICKS);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("construction.enabled", true);
    }

    private void tick() {
        if (!enabled()) {
            releaseAll();
            return;
        }
        releaseFinished();
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
        // A queued project is looked at too (it is dropped if the need has gone); only work in hand is left alone.
        if (settlement.openProject().filter(p -> p.status() == ConstructionProject.Status.ACTIVE).isPresent() || settlement.plan() == null
                || Construction.decidedToday(settlement, settlement.lastSimulatedDay())
                || (!unattended() && !world.isChunkLoaded(settlement.centerX() >> 4, settlement.centerZ() >> 4))) {
            return;
        }
        // Thinking about it costs a little, so a village that cannot build yet looks again every few seconds, not every tick.
        long now = System.currentTimeMillis();
        if (now - lastLook.getOrDefault(settlement.id(), 0L) < LOOK_EVERY_MS) {
            return;
        }
        lastLook.put(settlement.id(), now);
        // R4.22: with nobody near, the chunks of the plan are loaded for the decision, at most once a village day, and let go
        // once it is made. A village with a project open or too few people has nothing to decide that needs the ground.
        String holdKey = "d" + settlement.id();
        boolean needsGround = settlement.openProject().isEmpty() && settlement.population() >= Construction.MIN_POPULATION;
        if (unattended() && needsGround) {
            long today = settlement.lastSimulatedDay();
            if (!held.containsKey(holdKey)) {
                if (lastHoldDay.getOrDefault(settlement.id(), Long.MIN_VALUE) >= today
                        || now - lastHoldMs.getOrDefault(settlement.id(), 0L) < HOLD_EVERY_MS) {
                    return; // looked today already, or a minute ago (a fast-forwarded village's day is short)
                }
                lastHoldDay.put(settlement.id(), today);
                lastHoldMs.put(settlement.id(), now);
            }
            Held loading = hold(world, holdKey, planChunks(settlement));
            if (!allLoaded(world, loading.chunks()) && now - loading.since() < LOAD_WAIT_MS) {
                return; // look again in a few seconds; after a while it decides with what has loaded
            }
        }
        String style = BiomeSet.forBiome(world.getComputedBiome(settlement.centerX(), 64, settlement.centerZ()).getKey().getKey());
        Optional<ConstructionProject> queued = Construction.propose(settlement, settlement.lastSimulatedDay(), style,
                service.treasuryLimit(settlement), plugin.templates().catalog(), plugin.templates()::blueprint,
                groundOf(world));
        release(holdKey);
        if (queued.isPresent()) {
            plugin.requestSave();
        }
    }

    /** The chunks the village's plan covers: its lots, streets and square, which is where a decision needs the ground. */
    private static Set<Long> planChunks(Settlement settlement) {
        Set<Long> out = new HashSet<>();
        VillagePlan plan = settlement.plan();
        List<Rect> rects = new ArrayList<>();
        plan.lots().forEach(l -> rects.add(l.rect()));
        plan.roads().forEach(r -> rects.add(r.rect()));
        if (plan.square() != null) {
            rects.add(plan.square());
        }
        for (Rect r : rects) {
            out.addAll(chunksOf(r.x(), r.z(), r.width(), r.depth(), 0));
        }
        return out;
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
        // R4.21: 4 a second, up to 10 for a master; R4.22: times the speed setting.
        int perPass = Math.max(1, Math.min(MAX_BLOCKS_PER_PASS,
                (int) Math.round(Construction.blocksPerPass(builder.get().built()) * speed(settlement))));
        if (project.type().isWorks()) {
            workWorks(settlement, world, project, perPass);
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
        boolean unattended = unattended();
        if (unattended && !allLoaded(world, hold(world, "p" + project.id() + "@" + settlement.id(),
                chunksOf(ox, oz, bp.width(), bp.depth(), Construction.PAD_BUFFER + 1)).chunks())) {
            return; // the site is being loaded
        }
        if (!world.isChunkLoaded(ox >> 4, oz >> 4) || !world.isChunkLoaded((ox + bp.width()) >> 4, (oz + bp.depth()) >> 4)
                || !world.isChunkLoaded(ox >> 4, (oz + bp.depth()) >> 4) || !world.isChunkLoaded((ox + bp.width()) >> 4, oz >> 4)) {
            return;
        }
        Location centre = new Location(world, ox + bp.width() / 2.0, oy, oz + bp.depth() / 2.0);
        if ((!unattended && !playerNear(centre)) || exemptNear(centre)) {
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
            // (a slow builder does less each pass, so it is given more passes before the ground is left as it is)
            int graded = passes > (int) (MAX_GRADING_PASSES / Math.min(1.0, speed())) ? 0
                    : prepareGround(world, bp, ox, oy, oz, project.biomeSet(), Math.min(speed(settlement), MAX_BLOCKS_PER_PASS / 48.0));
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
            if (placed >= perPass) {
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

    /**
     * R5.6: puts up street lights (a fence post with a torch on it) or the palisade (a fence post), a few a pass, each paid
     * for from the ledger as it goes down. What stands is worked out from the world each pass, so a restart or a post a
     * player took away just shows as work still to do. A spot with something built on it, water or a tree is left as a gap.
     */
    private void workWorks(Settlement settlement, World world, ConstructionProject project, int perPass) {
        VillagePlan plan = settlement.plan();
        List<Works.Spot> spots = Works.spots(project.type(), plan, project.tier()); // R5.8: the stage it was laid out for
        List<Works.Spot> oldRing = project.type() == BuildingType.PALISADE && project.isUpgrade()
                ? Works.oldRing(plan, project.previousTier(), project.tier()) : List.of();
        if (spots.isEmpty()) {
            giveUp(settlement, project, "the village has no plan to put it on", false);
            return;
        }
        boolean lights = project.type() == BuildingType.STREET_LIGHTS;
        boolean unattended = unattended();
        if (unattended) {
            Set<Long> chunks = new HashSet<>();
            spots.forEach(s -> chunks.add(chunkKey(s.x() >> 4, s.z() >> 4)));
            oldRing.forEach(s -> chunks.add(chunkKey(s.x() >> 4, s.z() >> 4)));
            hold(world, "p" + project.id() + "@" + settlement.id(), chunks);
        }
        if (!unattended && !playerNear(new Location(world, plan.centerX(), 64, plan.centerZ()))) {
            return;
        }
        Set<Long> given = skipped.computeIfAbsent(project.id(), k -> new HashSet<>());
        Set<Long> standing = standingSpots.computeIfAbsent(project.id(), k -> new HashSet<>());
        long day = settlement.lastSimulatedDay();
        int placed = 0;
        int left = 0;
        boolean dry = false;
        for (Works.Spot spot : spots) {
            long key = ((long) spot.x() << 32) ^ (spot.z() & 0xffffffffL);
            if (given.contains(key) || standing.contains(key)) {
                continue;
            }
            if (!world.isChunkLoaded(spot.x() >> 4, spot.z() >> 4)) {
                left++; // not loaded yet: it waits for a player to come near, or for the chunks to be held
                continue;
            }
            // A fence blocks movement, so once a post is up it is the surface block itself.
            Block at = world.getBlockAt(spot.x(), surfaceY(world, spot.x(), spot.z()), spot.z());
            if (Tag.FENCES.isTagged(at.getType())) {
                Block torch = at.getRelative(0, 1, 0);
                if (lights && torch.getType() != Material.TORCH && growthOnly(torch)) {
                    torch.setType(Material.TORCH, false); // lit again if it was knocked off
                }
                standing.add(key);
                continue;
            }
            Block post = at.getRelative(0, 1, 0);
            Block top = at.getRelative(0, 2, 0);
            boolean fits = at.getType().isSolid() && !at.isLiquid() && clearable(at, true)
                    && growthOnly(post) && (!lights || growthOnly(top));
            if (!fits || exemptNear(post.getLocation())) {
                given.add(key); // something is in the way (or an exempt villager is near): a gap, not a wait
                continue;
            }
            if (dry || placed >= perPass) {
                left++;
                continue;
            }
            if (!Construction.charge(settlement, project, "OAK_FENCE", day)) {
                dry = true; // the stores ran dry: wait for wood
                left++;
                continue;
            }
            post.setType(Material.OAK_FENCE, true); // with physics, so it joins the posts beside it
            if (lights) {
                top.setType(Material.TORCH, false);
            }
            standing.add(key);
            placed++;
        }
        project.setBlocksLeft(left);
        Long progressed = workProgress.get(project.id());
        if (progressed == null || placed > 0 || dry) {
            workProgress.put(project.id(), day); // waiting for wood is not a stall
        }
        boolean stalled = left > 0 && !dry && placed == 0 && progressed != null && day - progressed >= WORKS_STALL_DAYS;
        if (left == 0 || stalled) {
            if (!oldRing.isEmpty()) {
                takeDownOldRing(settlement, world, oldRing, day);
            }
            if (stalled) {
                settlement.record(day, io.github.skyeberhard.hamletfolk.core.HistoryEvent.Kind.BUILDING, "The "
                        + project.type().label().toLowerCase(Locale.ROOT) + " was left with gaps where " + left
                        + " posts could not be reached.");
            }
            Construction.finish(settlement, project, day);
            forget(project.id());
            plugin.requestSave();
        } else if (placed > 0) {
            plugin.requestSave();
        }
    }

    /**
     * R5.8: the ring has moved out: the posts of the old one that the new one does not use are taken down (only an oak
     * fence post standing on the old ring's line, which the village put there) and half their wood goes back to the stores.
     * A post in a chunk that is not loaded is left standing.
     */
    private void takeDownOldRing(Settlement settlement, World world, List<Works.Spot> oldRing, long day) {
        int taken = 0;
        for (Works.Spot spot : oldRing) {
            if (!world.isChunkLoaded(spot.x() >> 4, spot.z() >> 4)) {
                continue;
            }
            Block at = world.getBlockAt(spot.x(), surfaceY(world, spot.x(), spot.z()), spot.z());
            // Only a post of the old line: oak fence with a post of the line beside it, and no torch on it (a light).
            boolean onLine = false;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                onLine |= at.getRelative(d[0], 0, d[1]).getType() == Material.OAK_FENCE;
            }
            if (at.getType() == Material.OAK_FENCE && onLine && at.getRelative(0, 1, 0).getType() != Material.TORCH) {
                at.setType(Material.AIR, true);
                taken++;
            }
        }
        int wood = Works.refund(taken);
        if (wood > 0) {
            settlement.ledger().add(io.github.skyeberhard.hamletfolk.core.ResourceType.WOOD, wood);
        }
        if (taken > 0) {
            settlement.record(day, io.github.skyeberhard.hamletfolk.core.HistoryEvent.Kind.BUILDING, "The old palisade was taken down: "
                    + taken + " posts, and " + wood + " wood went back to the stores.");
        }
    }

    private void forget(int projectId) {
        attempts.remove(projectId);
        skipped.remove(projectId);
        signTries.remove(projectId);
        gradingPasses.remove(projectId);
        standingSpots.remove(projectId);
        workProgress.remove(projectId);
    }

    private void giveUp(Settlement settlement, ConstructionProject project, String reason, boolean abandonLot) {
        Construction.cancel(settlement, project, settlement.lastSimulatedDay(), reason, abandonLot);
        forget(project.id());
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
    private int prepareGround(World world, Blueprint bp, int ox, int oy, int oz, String style, double speed) {
        int groundPerPass = (int) Math.round(GROUND_PER_PASS * speed);
        TerrainPad.Pad pad = TerrainPad.compute(new Rect(ox, oz, bp.width(), bp.depth()), Construction.PAD_BUFFER,
                groundOf(world), oy);
        if (!pad.valid()) {
            return -1;
        }
        Material[] soil = soilFor(style); // surface, fill, foundation
        Map<Long, Boolean> natural = new HashMap<>();
        int done = 0;
        for (TerrainPad.Change change : pad.changes()) {
            if (done >= groundPerPass) {
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
        for (int z = 0; z < bp.depth() && done < groundPerPass; z++) {
            for (int x = 0; x < bp.width() && done < groundPerPass; x++) {
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

    /** How far along the planned streets a finished building grades and paves (the street grows outward as the village does). */
    private static final int PAVE_RANGE = 20;
    private static final int WALKWAY_MAX = 14;
    private static final java.util.Set<Material> PATHABLE = java.util.EnumSet.of(Material.GRASS_BLOCK, Material.DIRT,
            Material.COARSE_DIRT, Material.PODZOL, Material.ROOTED_DIRT, Material.MYCELIUM);

    /**
     * Gives a finished building its street (R4.23): the planned streets within reach of it are graded and paved (a hole
     * is filled, a bump cut down, no step above one block, a narrow stream bridged), the square within reach is paved,
     * and a walkway leads from its entrance out to the street. Only plain ground is changed (never anything built, and
     * sand stays sand).
     */
    private void pave(World world, Settlement settlement, ConstructionProject project, Blueprint bp) {
        VillagePlan plan = settlement.plan();
        if (plan == null) {
            return;
        }
        List<Rect> streets = new ArrayList<>();
        plan.roads().forEach(r -> streets.add(r.rect()));
        Rect near = new Rect(project.x(), project.z(), bp.width(), bp.depth()).inflated(PAVE_RANGE);
        List<Rect> toGrade = new ArrayList<>();
        for (Rect street : streets) {
            overlap(street, near).ifPresent(toGrade::add);
        }
        List<Rect> everyStreet = new ArrayList<>(streets);
        if (plan.square() != null) {
            everyStreet.add(plan.square());
            if (settlement.buildingCount(BuildingType.SQUARE) == 0) { // until its building stands, which has a floor of its own
                overlap(plan.square(), near).ifPresent(toGrade::add); // the square is levelled like a street (R4.23)
            }
        }
        java.util.OptionalInt front = bp.front();
        if (front.isPresent()) {
            int dx = front.getAsInt() == 1 ? 1 : front.getAsInt() == 3 ? -1 : 0;
            int dz = front.getAsInt() == 2 ? 1 : front.getAsInt() == 0 ? -1 : 0;
            int startX = dx == 0 ? project.x() + bp.width() / 2 : dx > 0 ? project.x() + bp.width() : project.x() - 1;
            int startZ = dz == 0 ? project.z() + bp.depth() / 2 : dz > 0 ? project.z() + bp.depth() : project.z() - 1;
            int steps = 0;
            boolean meets = false;
            while (steps < WALKWAY_MAX) { // out from the door until it meets a street or the square
                final int px = startX + dx * steps;
                final int pz = startZ + dz * steps;
                if (everyStreet.stream().anyMatch(r -> r.contains(px, pz))) {
                    meets = true;
                    break;
                }
                steps++;
            }
            if (steps > 0) { // one block wide and straight, from the door to the street
                int cells = steps + (meets ? 1 : 0); // the street's first cell is in the slice, so the walkway meets its height
                int minX = dx >= 0 ? startX : startX - (cells - 1);
                int minZ = dz >= 0 ? startZ : startZ - (cells - 1);
                toGrade.add(new Rect(minX, minZ, dx == 0 ? 1 : cells, dz == 0 ? 1 : cells));
            }
        }
        applyGrade(world, StreetGrade.compute(toGrade, streetGround(world)), project.biomeSet());
    }

    private static java.util.Optional<Rect> overlap(Rect a, Rect b) {
        int x = Math.max(a.x(), b.x());
        int z = Math.max(a.z(), b.z());
        int maxX = Math.min(a.maxX(), b.maxX());
        int maxZ = Math.min(a.maxZ(), b.maxZ());
        return maxX < x || maxZ < z ? java.util.Optional.empty() : java.util.Optional.of(new Rect(x, z, maxX - x + 1, maxZ - z + 1));
    }

    /** As {@link #groundOf}, but a column with something built on it is unmeasured, so a street goes round it and never through. */
    private static HeightSource streetGround(World world) {
        HeightSource ground = groundOf(world);
        return new HeightSource() {
            @Override
            public int height(int x, int z) {
                if (!world.isChunkLoaded(x >> 4, z >> 4) || !columnNatural(world, x, z)) {
                    return UNKNOWN;
                }
                int h = ground.height(x, z);
                // A torch, a rail, a sign or a tree trunk on the ground is something to go round, not to dig out from under.
                return h == UNKNOWN || (!ground.water(x, z) && !growthOnly(world.getBlockAt(x, h + 1, z))) ? UNKNOWN : h;
            }

            @Override
            public boolean water(int x, int z) {
                return ground.water(x, z);
            }

            @Override
            public int floor(int x, int z) {
                return ground.floor(x, z);
            }
        };
    }

    /** Carries out a street grading: soil into hollows, rock and growth out of the way, path on top, planks over water. */
    private static void applyGrade(World world, List<StreetGrade.Change> changes, String style) {
        Material[] soil = soilFor(style);
        boolean desert = BiomeSet.DESERT.equals(style);
        Set<Long> blocked = new HashSet<>(); // columns with something in the way of the walking surface
        for (StreetGrade.Change change : changes) {
            Block block = world.getBlockAt(change.x(), change.y(), change.z());
            long column = ((long) change.x() << 32) ^ (change.z() & 0xffffffffL);
            switch (change.role()) {
                case FILL -> {
                    if (blocked.contains(column)) {
                        continue;
                    }
                    if (clearable(block, true)) {
                        block.setType(soil[1], false);
                    } else {
                        blocked.add(column);
                    }
                }
                case AIR -> {
                    if (!block.getType().isAir() && clearable(block, true)) {
                        block.setType(Material.AIR, false);
                    }
                    Block above = block.getRelative(0, 1, 0); // nothing is left hanging over what was cut
                    if (!above.getType().isAir() && growthOnly(above)) {
                        above.setType(Material.AIR, false);
                    }
                }
                case SURFACE -> {
                    if (blocked.contains(column) || !clearHeadroom(block)) {
                        continue;
                    }
                    Material here = block.getType();
                    if (here.isAir() || Tag.REPLACEABLE.isTagged(here)) {
                        block.setType(desert ? Material.SAND : Material.DIRT_PATH, false);
                    } else if (!desert && PATHABLE.contains(here)) {
                        block.setType(Material.DIRT_PATH, false);
                    }
                }
                case BRIDGE -> {
                    if (block.isLiquid() && clearHeadroom(block)) {
                        block.setType(Material.OAK_PLANKS, false);
                    }
                }
            }
        }
    }

    /** True for air, plants and leaves the game grew: what may stand over a street. Anything else is something built or a trunk. */
    private static boolean growthOnly(Block block) {
        Material m = block.getType();
        if (m.isAir()) {
            return true;
        }
        if (block.isLiquid()) {
            return false;
        }
        return Tag.FLOWERS.isTagged(m) || Tag.SAPLINGS.isTagged(m) || Tag.REPLACEABLE.isTagged(m)
                || (Tag.LEAVES.isTagged(m) && block.getBlockData() instanceof Leaves l && !l.isPersistent());
    }

    /** Makes the two blocks over a walking surface free of plants and growing leaves; false if something else stands there. */
    private static boolean clearHeadroom(Block surface) {
        for (int up = 1; up <= 2; up++) {
            Block above = surface.getRelative(0, up, 0);
            if (above.getType().isAir()) {
                continue;
            }
            if (!growthOnly(above)) {
                return false;
            }
            above.setType(Material.AIR, false);
        }
        return true;
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
