package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * R8.2: scores a candidate site for a village from a coarse sample of the land around it. Resources decide where a
 * village goes and the biome decides how it looks; scarcity feeds trade, so a missing resource is not a refusal but an
 * import the village will have to make. A pure function: the Paper layer samples the biome every 16 to 32 blocks within
 * 64 to 96 blocks of the centre (with the game's computed biome lookup, so no chunks are generated just to be rejected)
 * and this scores the sample. The same sample always gives the same profile.
 *
 * <p>R8.24: two measures come out of one sample. The {@link Profile#scores} say how much each resource <i>stands out</i> (a biome is
 * a specialist: plains are farmland and little else), which is what gives a village its character ({@link Leaning}). The
 * {@link Profile#reach} say whether a village could <i>get</i> each resource from here: some of each nearby is enough (a village
 * does not need the whole country to be forest to have timber), stone and ore are reachable underground wherever there is land, and where
 * the chunks are loaded the Paper layer adds what is really there (a lake, a stand of trees, bare stone: {@link Observed}), which a
 * biome lookup cannot see. The overall score, the imports, the notes and the growth follow the reach. What is seen on the ground never
 * changes {@link Profile#scores}: a village's character is read from the biome alone, so it does not depend on which chunks happen to be loaded
 * (or on what players have built or felled) when it is read.
 */
public final class SiteSurvey {
    /** The farthest a sample counts, in blocks from the centre. */
    public static final int MAX_RADIUS = 96;
    /** A resource within reach scoring below this (out of 100) is scarce: the village will have to bring it in. */
    public static final double SCARCE = 25.0;
    /** Water within reach below this limits how far a village can grow. */
    static final double DRY = 15.0;
    /** Water within reach below this is worth a word, though it does not stop a village growing. */
    static final double DAMP = 30.0;
    /** The share of the nearby land that is a resource for it to be fully within reach: half is plenty, a tenth is thin. */
    public static final double SATURATION = 0.5;
    /** What land of any biome gives underground, as a share (0 to 1) of a cell: stone to quarry, ore to mine. A mine reaches both. */
    static final double UNDERGROUND_STONE = 0.5;
    static final double UNDERGROUND_ORE = 0.25;
    /** What a sampled column that is water, a tree or bare stone is worth to the cell it stands for: a few of them make a cell rich in it. */
    static final double WATER_PER_SHARE = 6.0;
    static final double TREES_PER_SHARE = 3.0;
    static final double STONE_PER_SHARE = 3.0;

    private SiteSurvey() {
    }

    /**
     * What the Paper layer saw on the ground in a cell, from the columns it looked at there (only where the chunk was loaded): the
     * share (0 to 1) of them whose top block was water, leaves or logs, and bare stone.
     */
    public record Observed(double water, double trees, double stone) {
    }

    /** One sampled point: its offset from the centre in blocks, the biome there, and what was seen on the ground there if it was looked at. */
    public record Cell(int dx, int dz, String biome, Observed seen) {
        public Cell(int dx, int dz, String biome) {
            this(dx, dz, biome, null);
        }
    }

    /** How fast a village at the site can be expected to grow. */
    public enum Growth {
        SLOW, NORMAL, FAST;

        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The site profile: the score of each resource (0 to 100), an overall score, and the starting conditions they imply.
     *
     * @param scores       per resource, 0 to 100, how much it stands out in the land (the village's character), nearer samples counting for more
     * @param overall      the weighted mean of the reach, 0 to 100
     * @param buildable    the share (0 to 1) of the sample that is land a village could stand on
     * @param growth       how fast the village is likely to grow
     * @param foodBalance  from -1 (it cannot feed itself) to 1 (a surplus of food), from farmland and livestock
     * @param imports      what the village will have to bring in because the site is scarce in it
     * @param notes        plain sentences saying what is good and bad about the site
     * @param reach        per resource, 0 to 100, how readily a village here could get it (R8.24)
     */
    public record Profile(Map<SiteResource, Double> scores, double overall, double buildable, Growth growth,
            double foodBalance, List<ResourceType> imports, List<String> notes, Map<SiteResource, Double> reach) {
        public double score(SiteResource resource) {
            return scores.getOrDefault(resource, 0.0);
        }

        /** How readily a village here could get the resource, 0 to 100. */
        public double reach(SiteResource resource) {
            return reach.getOrDefault(resource, 0.0);
        }
    }

    /** How much each resource counts toward the overall score. */
    public static Map<SiteResource, Double> defaultWeights() {
        Map<SiteResource, Double> weights = new EnumMap<>(SiteResource.class);
        weights.put(SiteResource.WATER, 1.0);
        weights.put(SiteResource.LUMBER, 1.0);
        weights.put(SiteResource.FARMLAND, 1.5); // a village lives on its food
        weights.put(SiteResource.LIVESTOCK, 0.7);
        weights.put(SiteResource.STONE, 0.8);
        weights.put(SiteResource.ORE, 0.8);
        return weights;
    }

    public static Profile score(List<Cell> cells) {
        return score(cells, defaultWeights());
    }

    /** Scores the sample with the given weights. Cells beyond {@link #MAX_RADIUS} are ignored. */
    public static Profile score(List<Cell> cells, Map<SiteResource, Double> weights) {
        SiteResource[] resources = SiteResource.values();
        double[] sums = new double[resources.length];
        double[] reachSums = new double[resources.length];
        double totalWeight = 0;
        int counted = 0;
        int land = 0;
        for (Cell cell : cells) {
            double distance = Math.sqrt((double) cell.dx() * cell.dx() + (double) cell.dz() * cell.dz());
            if (distance > MAX_RADIUS) {
                continue;
            }
            // Nearer land matters more than land at the edge of the sample.
            double weight = 1.0 / (1.0 + distance / 32.0);
            double[] values = BiomeTable.resources(cell.biome());
            double[] reachable = values.clone();
            Observed seen = cell.seen();
            if (seen != null) { // what is really there beats what the biome says it should be, where it is more: for what is within reach only
                reachable[SiteResource.WATER.ordinal()] = Math.max(reachable[SiteResource.WATER.ordinal()], Math.min(1.0, seen.water() * WATER_PER_SHARE));
                reachable[SiteResource.LUMBER.ordinal()] = Math.max(reachable[SiteResource.LUMBER.ordinal()], Math.min(1.0, seen.trees() * TREES_PER_SHARE));
                reachable[SiteResource.STONE.ordinal()] = Math.max(reachable[SiteResource.STONE.ordinal()], Math.min(1.0, seen.stone() * STONE_PER_SHARE));
            }
            boolean ground = BiomeTable.buildable(cell.biome());
            for (int i = 0; i < resources.length; i++) {
                sums[i] += weight * values[i];
                double reached = reachable[i];
                if (ground && resources[i] == SiteResource.STONE) {
                    reached = Math.max(reached, UNDERGROUND_STONE); // under any land
                } else if (ground && resources[i] == SiteResource.ORE) {
                    reached = Math.max(reached, UNDERGROUND_ORE);
                }
                reachSums[i] += weight * reached;
            }
            totalWeight += weight;
            counted++;
            if (ground) {
                land++;
            }
        }
        Map<SiteResource, Double> scores = new EnumMap<>(SiteResource.class);
        Map<SiteResource, Double> reach = new EnumMap<>(SiteResource.class);
        for (int i = 0; i < resources.length; i++) {
            scores.put(resources[i], totalWeight == 0 ? 0.0 : 100.0 * sums[i] / totalWeight);
            reach.put(resources[i], totalWeight == 0 ? 0.0 : Math.min(100.0, 100.0 * (reachSums[i] / totalWeight) / SATURATION));
        }
        double weighted = 0;
        double weightSum = 0;
        for (SiteResource resource : resources) {
            double w = weights.getOrDefault(resource, 1.0);
            weighted += w * reach.get(resource);
            weightSum += w;
        }
        double overall = weightSum == 0 ? 0 : weighted / weightSum;
        double buildable = counted == 0 ? 0 : (double) land / counted;

        double food = 0.7 * scores.get(SiteResource.FARMLAND) + 0.3 * scores.get(SiteResource.LIVESTOCK);
        double foodBalance = Math.max(-1.0, Math.min(1.0, (food - 40.0) / 60.0));
        double foodReach = 0.7 * reach.get(SiteResource.FARMLAND) + 0.3 * reach.get(SiteResource.LIVESTOCK);

        List<ResourceType> imports = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (foodReach < SCARCE) {
            imports.add(ResourceType.FOOD);
            notes.add("Little farmland or grazing: the village will have to buy its food.");
        }
        if (reach.get(SiteResource.LUMBER) < SCARCE) {
            imports.add(ResourceType.WOOD);
            notes.add("Few trees: wood will have to be brought in.");
        }
        if (reach.get(SiteResource.STONE) < SCARCE) {
            imports.add(ResourceType.STONE);
            notes.add("No ground to quarry: stone will have to be brought in.");
        }
        if (reach.get(SiteResource.ORE) < SCARCE) {
            imports.add(ResourceType.METAL);
            notes.add("No ground to mine: metal, and so tools, will have to be brought in.");
        }
        double water = reach.get(SiteResource.WATER);
        boolean dry = water < DRY;
        if (dry) {
            notes.add("Hardly any water: the village will not grow far.");
        } else if (water < DAMP) {
            notes.add("Not much water about: a river or a lake would help.");
        }
        if (buildable < 0.5 && counted > 0) {
            notes.add("More than half of the surroundings are open water.");
        }
        if (notes.isEmpty()) {
            notes.add("A well-rounded site: every resource is within reach.");
        }
        // Growth follows how much the village has to bring in: nothing scarce is fast, one thing is normal, more is slow.
        Growth growth = imports.isEmpty() && overall >= 30 ? Growth.FAST : imports.size() <= 1 ? Growth.NORMAL : Growth.SLOW;
        if (dry || buildable < 0.5) {
            growth = Growth.SLOW;
        }
        return new Profile(scores, overall, buildable, growth, foodBalance, List.copyOf(imports), List.copyOf(notes), reach);
    }

    /**
     * A sample grid centred on a point, for the Paper layer and for tests: every {@code step} blocks out to
     * {@code radius}, with the biome looked up by a function of the block offset.
     */
    public static List<Cell> grid(int radius, int step, java.util.function.BiFunction<Integer, Integer, String> biomeAt) {
        return grid(radius, step, biomeAt, (dx, dz) -> null);
    }

    /** As above, with what was seen on the ground at each cell (null where it was not looked at: its chunk was not loaded). */
    public static List<Cell> grid(int radius, int step, java.util.function.BiFunction<Integer, Integer, String> biomeAt,
            java.util.function.BiFunction<Integer, Integer, Observed> seenAt) {
        List<Cell> cells = new ArrayList<>();
        int limit = Math.min(radius, MAX_RADIUS);
        for (int dx = -limit; dx <= limit; dx += step) {
            for (int dz = -limit; dz <= limit; dz += step) {
                cells.add(new Cell(dx, dz, biomeAt.apply(dx, dz), seenAt.apply(dx, dz)));
            }
        }
        return cells;
    }
}
