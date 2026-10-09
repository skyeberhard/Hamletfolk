package io.github.skyeberhard.hamletfolk.core;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * R8.11: what the land makes a village. The site survey (R8.2) scores the water, timber, farmland, grazing, stone and ore
 * round a site; the strongest of them, if it stands out, is the village's leaning. It is a bias, not a cage: needs still
 * come first, and a mining village still farms. Pure: a profile in, a leaning out.
 */
public enum Leaning {
    TIMBER("timber", "forest all round it", List.of(Occupation.LUMBERJACK)),
    MINING("mining", "hills of stone and ore", List.of(Occupation.MINER, Occupation.MASON)),
    FARMING("farming", "open farmland", List.of(Occupation.FARMER)),
    FISHING("fishing", "water all round it", List.of(Occupation.FISHERMAN)),
    PASTORAL("pastoral", "grazing land", List.of(Occupation.SHEPHERD, Occupation.BUTCHER, Occupation.LEATHERWORKER)),
    ALL_ROUND("all-round", "nothing in the land that stands out", List.of()),
    // R8.13: read from what the survey counts rather than from the biomes, and only where the biomes gave nothing that stands out.
    // They come after ALL_ROUND so that the leanings already saved keep their numbers.
    SCHOLARLY("scholarly", "sugar cane and cattle for paper and books", List.of(Occupation.CARTOGRAPHER, Occupation.LIBRARIAN)),
    CRAFT("craft", "sand enough for glass", List.of(Occupation.GLASSBLOWER)),
    TRADING("trading", "a little of everything to trade", List.of());

    /** The score (out of 100) a resource needs to be a leaning at all. */
    static final double MIN_SCORE = 40;
    /** How far the best must lead the second to stand out. */
    static final double LEAD = 10;

    private final String label;
    private final String reason;
    private final List<Occupation> trades;

    Leaning(String label, String reason, List<Occupation> trades) {
        this.label = label;
        this.reason = reason;
        this.trades = trades;
    }

    /** e.g. "timber". */
    public String label() {
        return label;
    }

    /** Why the land gives this leaning, e.g. "forest all round it". */
    public String reason() {
        return reason;
    }

    /** The building this leaning calls for once every need is met (R8.12), or null if it has none yet. */
    public BuildingType signature() {
        return switch (this) {
            case TIMBER -> BuildingType.SAWMILL;
            case MINING -> BuildingType.FORGE;
            case FARMING -> BuildingType.GRANARY;
            case PASTORAL -> BuildingType.PENS; // R8.13
            case FISHING -> BuildingType.HARBOUR; // R8.13
            case SCHOLARLY -> BuildingType.MAP_ROOM; // (and a library once it is a town: see Trades)
            case CRAFT -> BuildingType.GLASSWORKS;
            case TRADING -> BuildingType.TRADING_POST;
            case ALL_ROUND -> null;
        };
    }

    /** True for a trade of this leaning, which is staffed sooner. */
    public boolean favours(Occupation trade) {
        return trades.contains(trade);
    }

    /** The leaning the land gives: its strongest resource, if that is at least {@link #MIN_SCORE} and leads the next. */
    public static Leaning of(SiteSurvey.Profile profile) {
        return of(profile.scores());
    }

    static Leaning of(Map<SiteResource, Double> scores) {
        double timber = scores.getOrDefault(SiteResource.LUMBER, 0.0);
        double mining = (scores.getOrDefault(SiteResource.STONE, 0.0) + scores.getOrDefault(SiteResource.ORE, 0.0)) / 2;
        double farming = scores.getOrDefault(SiteResource.FARMLAND, 0.0);
        double fishing = scores.getOrDefault(SiteResource.WATER, 0.0);
        double pastoral = scores.getOrDefault(SiteResource.LIVESTOCK, 0.0);
        double[] values = {timber, mining, farming, fishing, pastoral};
        Leaning[] kinds = {TIMBER, MINING, FARMING, FISHING, PASTORAL};
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        double second = 0;
        for (int i = 0; i < values.length; i++) {
            if (i != best) {
                second = Math.max(second, values[i]);
            }
        }
        return values[best] >= MIN_SCORE && values[best] - second >= LEAD ? kinds[best] : ALL_ROUND;
    }

    /** Sugar cane and cattle a village needs within reach to lean scholarly; sand to lean craft; how many kinds of thing for trading. */
    static final int SCHOLARLY_CANE = 16;
    static final int SCHOLARLY_CATTLE = 4;
    static final int CRAFT_SAND = 60;
    static final int TRADING_KINDS = 4;

    /**
     * R8.13: the leaning the survey's counts give a village whose biomes gave none: scholarly (paper and leather for books and
     * maps), craft (sand for glass), or trading (many kinds of thing within reach, a crossroads); otherwise all-round.
     */
    static Leaning fromCounts(Settlement settlement) {
        int cane = LandCounts.get(settlement, LandCounts.Feature.SUGAR_CANE);
        int cattle = LandCounts.get(settlement, LandCounts.Feature.CATTLE);
        int sand = LandCounts.get(settlement, LandCounts.Feature.SAND);
        if (cane >= SCHOLARLY_CANE && cattle >= SCHOLARLY_CATTLE) {
            return SCHOLARLY;
        }
        if (sand >= CRAFT_SAND) {
            return CRAFT;
        }
        int kinds = 0;
        kinds += LandCounts.get(settlement, LandCounts.Feature.WATER) >= 40 ? 1 : 0;
        kinds += LandCounts.get(settlement, LandCounts.Feature.SHEEP) >= 4 ? 1 : 0;
        kinds += cattle >= 4 ? 1 : 0;
        kinds += LandCounts.get(settlement, LandCounts.Feature.HORSES) >= 3 ? 1 : 0;
        kinds += cane >= SCHOLARLY_CANE ? 1 : 0;
        kinds += sand >= 30 ? 1 : 0;
        kinds += LandCounts.get(settlement, LandCounts.Feature.BEES) >= 2 ? 1 : 0;
        return kinds >= TRADING_KINDS ? TRADING : ALL_ROUND;
    }

    static Leaning fromSave(long ordinalPlusOne) {
        Leaning[] all = values();
        return ordinalPlusOne >= 1 && ordinalPlusOne <= all.length ? all[(int) ordinalPlusOne - 1] : ALL_ROUND;
    }

    @Override
    public String toString() {
        return label.toUpperCase(Locale.ROOT);
    }
}
