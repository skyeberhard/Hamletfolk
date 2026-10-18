package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.Colonies;
import io.github.skyeberhard.hamletfolk.core.ColonySites;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.SiteSurvey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * R10.3: proposes places for a daughter village to {@link ColonySites} and says what is wrong with the ones that will not do. A ring of
 * places round the parent is looked at: only chunks that already exist are read (they are loaded from disk, never generated, and held with a
 * looked at straight away), nine columns of ground at each place are checked (water, lava, things built on it, a steep
 * slope), and so are the world border, exempt zones and WorldGuard. What passes is scored by the site survey. Nothing is changed in the world.
 */
final class ColonySiteFinder {
    /** Places round the ring, and the offsets (blocks) of the nine columns looked at at each. */
    private static final int DIRECTIONS = 16;
    private static final int[] LOOK = {-8, 0, 8};
    /** The most the ground may rise or fall across a place, in blocks. */
    private static final int MAX_SLOPE = 10;

    private final HamletfolkPlugin plugin;
    private final SettlementService service;

    ColonySiteFinder(HamletfolkPlugin plugin, SettlementService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /** What came of the search: a result, or the reason it could not be made. */
    record Outcome(ColonySites.Result result, String error) {
    }

    /** Looks for a place for a daughter of {@code parent}; {@code done} is called on the main thread. */
    void find(Settlement parent, Colonies.Rules rules, Consumer<Outcome> done) {
        World world = Bukkit.getWorld(parent.world());
        if (world == null || !service.inScope(world)) {
            done.accept(new Outcome(null, parent.name() + " is in a world that is not loaded or not simulated."));
            return;
        }
        long seed = parent.id().getMostSignificantBits() ^ parent.id().getLeastSignificantBits();
        double turn = (Math.floorMod(seed, 100) / 100.0) * (2 * Math.PI / DIRECTIONS);
        int near = Math.min(ColonySites.MAX_DISTANCE - 20, Math.max(100, rules.minSpacing()) + 8); // (a few blocks past the spacing: rounding must not put a place inside it)
        int[] radii = {near, (near + ColonySites.MAX_DISTANCE) / 2, ColonySites.MAX_DISTANCE};
        List<int[]> points = new ArrayList<>();
        int outside = 0;
        for (int d = 0; d < DIRECTIONS; d++) {
            double angle = turn + 2 * Math.PI * d / DIRECTIONS;
            for (int r : radii) {
                int x = parent.centerX() + (int) Math.round(r * Math.sin(angle));
                int z = parent.centerZ() - (int) Math.round(r * Math.cos(angle));
                if (world.getWorldBorder().isInside(new Location(world, x, 64, z))) {
                    points.add(new int[] {x, z});
                } else {
                    outside++;
                }
            }
        }
        int outsideBorder = outside;
        java.util.concurrent.Executor main = task -> {
            if (Bukkit.isPrimaryThread()) {
                task.run(); // (the tick the chunks arrived in: they cannot have been let go yet)
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        };
        List<CompletableFuture<ColonySites.Candidate>> looked = new ArrayList<>();
        for (int[] p : points) {
            // the chunks of this place's nine columns; false: never generate a chunk for a search (and no ticket: a ticket loads synchronously)
            Set<Long> chunks = new HashSet<>();
            for (int dx : LOOK) {
                for (int dz : LOOK) {
                    chunks.add(key((p[0] + dx) >> 4, (p[1] + dz) >> 4));
                }
            }
            List<CompletableFuture<?>> loading = new ArrayList<>();
            for (long k : chunks) {
                loading.add(world.getChunkAtAsync((int) (k >> 32), (int) k, false));
            }
            looked.add(CompletableFuture.allOf(loading.toArray(new CompletableFuture<?>[0]))
                    .handleAsync((ok, failure) -> failure != null ? rejected(p[0], p[1], "could not be read") : inspect(world, p[0], p[1]), main));
        }
        CompletableFuture.allOf(looked.toArray(new CompletableFuture<?>[0])).whenCompleteAsync((ok, failure) -> {
            try {
                if (failure != null) {
                    done.accept(new Outcome(null, "Could not read the land: " + failure.getMessage()));
                    return;
                }
                List<ColonySites.Candidate> candidates = new ArrayList<>();
                for (CompletableFuture<ColonySites.Candidate> f : looked) {
                    candidates.add(f.join());
                }
                done.accept(new Outcome(choose(parent, candidates, outsideBorder, rules), null));
            } catch (RuntimeException e) {
                done.accept(new Outcome(null, "The search failed: " + e));
            }
        }, main);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    private ColonySites.Result choose(Settlement parent, List<ColonySites.Candidate> candidates, int outsideBorder, Colonies.Rules rules) {
        List<ColonySites.Point> others = new ArrayList<>();
        for (Settlement other : service.registry().settlements()) {
            if (other != parent && other.world().equals(parent.world())) {
                others.add(new ColonySites.Point(other.centerX(), other.centerZ()));
            }
        }
        ColonySites.Result result = ColonySites.choose(parent, candidates, others, rules);
        if (outsideBorder == 0) {
            return result;
        }
        java.util.Map<String, Integer> ruledOut = new java.util.LinkedHashMap<>(result.ruledOut());
        ruledOut.merge("outside the world border", outsideBorder, Integer::sum);
        return new ColonySites.Result(result.best(), result.others(), ruledOut, result.considered() + outsideBorder);
    }

    /** One place: the ground at nine columns, then the survey of the land round it. */
    private ColonySites.Candidate inspect(World world, int x, int z) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        int centreY = world.getMinHeight();
        for (int dx : LOOK) {
            for (int dz : LOOK) {
                int px = x + dx;
                int pz = z + dz;
                if (!world.isChunkLoaded(px >> 4, pz >> 4)) {
                    return rejected(x, z, "not generated yet");
                }
                int y = world.getHighestBlockYAt(px, pz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                Block top = world.getBlockAt(px, y, pz);
                Material type = top.getType();
                if (top.isLiquid() || type == Material.ICE || type == Material.BLUE_ICE || type == Material.PACKED_ICE) {
                    return rejected(x, z, "on water, lava or ice");
                }
                if (!natural(type)) {
                    return rejected(x, z, "something has been built there (" + type.name().toLowerCase(java.util.Locale.ROOT) + ")");
                }
                low = Math.min(low, y);
                high = Math.max(high, y);
                if (dx == 0 && dz == 0) {
                    centreY = y;
                }
            }
        }
        if (high - low > MAX_SLOPE) {
            return rejected(x, z, "too steep");
        }
        for (int dx : new int[] {0, -16, 16}) {
            for (int dz : new int[] {0, -16, 16}) {
                if (dx != 0 && dz == 0 || dx == 0 && dz != 0) {
                    continue; // (the centre and the four corners)
                }
                Location at = new Location(world, x + dx, centreY, z + dz);
                if (!world.getWorldBorder().isInside(at)) {
                    return rejected(x, z, "outside the world border");
                }
                if (service.zonesOf(world).covers(x + dx, centreY, z + dz)) {
                    return rejected(x, z, "in an exempt zone");
                }
                if (Protection.denies(at)) {
                    return rejected(x, z, "protected by WorldGuard");
                }
            }
        }
        SiteSurvey.Profile profile = SiteSurvey.score(service.siteGrid(world, x, z, centreY, true));
        return new ColonySites.Candidate(x, z, centreY, profile, null);
    }

    private static ColonySites.Candidate rejected(int x, int z, String why) {
        return new ColonySites.Candidate(x, z, 0, SiteSurvey.score(List.of()), why);
    }

    /** Whether a surface block is what the land gives: soil, sand, stone, snow, clay, mud, terracotta, or a tree. */
    static boolean natural(Material type) {
        return type == Material.GRASS_BLOCK || type == Material.DIRT || Tag.DIRT.isTagged(type) || Tag.SAND.isTagged(type) || Tag.BASE_STONE_OVERWORLD.isTagged(type) || Tag.TERRACOTTA.isTagged(type)
                || Tag.LOGS.isTagged(type) || Tag.LEAVES.isTagged(type) || Tag.SNOW.isTagged(type) || Tag.FLOWERS.isTagged(type)
                || type == Material.GRAVEL || type == Material.CLAY || type == Material.SNOW_BLOCK || type == Material.POWDER_SNOW
                || type == Material.MOSS_BLOCK || type == Material.SANDSTONE || type == Material.RED_SANDSTONE || type == Material.MUD
                || type == Material.MYCELIUM || type == Material.PODZOL || type == Material.COARSE_DIRT || type == Material.TUFF
                || type == Material.CALCITE || type == Material.DRIPSTONE_BLOCK || type == Material.COPPER_ORE || type == Material.CACTUS
                || type == Material.BAMBOO || type == Material.SUGAR_CANE || type == Material.PUMPKIN || type == Material.MELON
                || type == Material.SWEET_BERRY_BUSH || type == Material.MANGROVE_ROOTS || type == Material.MUDDY_MANGROVE_ROOTS
                || type == Material.BROWN_MUSHROOM_BLOCK || type == Material.RED_MUSHROOM_BLOCK || type == Material.MUSHROOM_STEM
                || type == Material.PALE_MOSS_BLOCK || type == Material.POINTED_DRIPSTONE;
    }
}
