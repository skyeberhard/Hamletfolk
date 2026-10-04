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
 */
public final class SiteSurvey {
    /** The farthest a sample counts, in blocks from the centre. */
    public static final int MAX_RADIUS = 96;
    /** A resource scoring below this (out of 100) is scarce: the village will have to bring it in. */
    public static final double SCARCE = 20.0;
    /** Water below this limits how far a village can grow. */
    static final double DRY = 15.0;

    private SiteSurvey() {
    }

    /** One sampled point: its offset from the centre in blocks, and the biome there. */
    public record Cell(int dx, int dz, String biome) {
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
     * @param scores       per resource, 0 to 100, nearer samples counting for more
     * @param overall      the weighted mean of the scores
     * @param buildable    the share (0 to 1) of the sample that is land a village could stand on
     * @param growth       how fast the village is likely to grow
     * @param foodBalance  from -1 (it cannot feed itself) to 1 (a surplus of food), from farmland and livestock
     * @param imports      what the village will have to bring in because the site is scarce in it
     * @param notes        plain sentences saying what is good and bad about the site
     */
    public record Profile(Map<SiteResource, Double> scores, double overall, double buildable, Growth growth,
            double foodBalance, List<ResourceType> imports, List<String> notes) {
        public double score(SiteResource resource) {
            return scores.getOrDefault(resource, 0.0);
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
            for (int i = 0; i < resources.length; i++) {
                sums[i] += weight * values[i];
            }
            totalWeight += weight;
            counted++;
            if (BiomeTable.buildable(cell.biome())) {
                land++;
            }
        }
        Map<SiteResource, Double> scores = new EnumMap<>(SiteResource.class);
        for (int i = 0; i < resources.length; i++) {
            scores.put(resources[i], totalWeight == 0 ? 0.0 : 100.0 * sums[i] / totalWeight);
        }
        double weighted = 0;
        double weightSum = 0;
        for (SiteResource resource : resources) {
            double w = weights.getOrDefault(resource, 1.0);
            weighted += w * scores.get(resource);
            weightSum += w;
        }
        double overall = weightSum == 0 ? 0 : weighted / weightSum;
        double buildable = counted == 0 ? 0 : (double) land / counted;

        double food = 0.7 * scores.get(SiteResource.FARMLAND) + 0.3 * scores.get(SiteResource.LIVESTOCK);
        double foodBalance = Math.max(-1.0, Math.min(1.0, (food - 40.0) / 60.0));

        List<ResourceType> imports = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (food < SCARCE) {
            imports.add(ResourceType.FOOD);
            notes.add("Little farmland or grazing: the village will have to buy its food.");
        }
        if (scores.get(SiteResource.LUMBER) < SCARCE) {
            imports.add(ResourceType.WOOD);
            notes.add("Few trees: wood will have to be brought in.");
        }
        if (scores.get(SiteResource.STONE) < SCARCE) {
            imports.add(ResourceType.STONE);
            notes.add("Little stone nearby: stone will have to be brought in.");
        }
        if (scores.get(SiteResource.ORE) < SCARCE) {
            imports.add(ResourceType.METAL);
            notes.add("No ore to speak of: metal, and so tools, will have to be brought in.");
        }
        boolean dry = scores.get(SiteResource.WATER) < DRY;
        if (dry) {
            notes.add("Hardly any water: the village will not grow far.");
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
        return new Profile(scores, overall, buildable, growth, foodBalance, List.copyOf(imports), List.copyOf(notes));
    }

    /**
     * A sample grid centred on a point, for the Paper layer and for tests: every {@code step} blocks out to
     * {@code radius}, with the biome looked up by a function of the block offset.
     */
    public static List<Cell> grid(int radius, int step, java.util.function.BiFunction<Integer, Integer, String> biomeAt) {
        List<Cell> cells = new ArrayList<>();
        int limit = Math.min(radius, MAX_RADIUS);
        for (int dx = -limit; dx <= limit; dx += step) {
            for (int dz = -limit; dz <= limit; dz += step) {
                cells.add(new Cell(dx, dz, biomeAt.apply(dx, dz)));
            }
        }
        return cells;
    }
}
