package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * R4.6, R4.18: which template builds what. For a building type and a village style there is an ordered ladder of
 * tiers (a small house, then a medium one, then a big one), each tier one template key. Vanilla keys name pieces the
 * server jar ships (the Paper layer checks at startup that each exists and reads the blocks); generated keys
 * ({@link BuildingGenerator}) and captured ones (an admin's own build, which replaces the generated tier) carry a
 * {@link Blueprint} here. A style with a shorter ladder (no big house in the desert) is a documented gap, not an error.
 */
public final class TemplateCatalog {
    public enum Source {
        VANILLA, GENERATED, CAPTURED
    }

    /** One rung: tier 1 is the first thing built. */
    public record Template(BuildingType type, String biomeSet, int tier, String key, Source source) {
    }

    private static final Map<String, String> FARM = Map.of(BiomeSet.PLAINS, "plains_small_farm_1",
            BiomeSet.DESERT, "desert_farm_1", BiomeSet.SAVANNA, "savanna_small_farm", BiomeSet.SNOWY, "snowy_farm_1",
            BiomeSet.TAIGA, "taiga_small_farm_1");
    /** Which styles ship a large farm (snowy does not). */
    private static final List<String> LARGE_FARM = List.of(BiomeSet.PLAINS, BiomeSet.DESERT, BiomeSet.SAVANNA,
            BiomeSet.TAIGA);

    private final Map<String, Blueprint> captured = new HashMap<>();

    private static String id(BuildingType type, String biomeSet, int tier) {
        return type.name() + "/" + BiomeSet.normalize(biomeSet) + "/" + tier;
    }

    private static String vanilla(String biome, String folder, String piece) {
        return "minecraft:village/" + biome + "/" + folder + "/" + piece;
    }

    /**
     * R4.18: use an admin's build for this type, style and tier from now on. The blueprint is stored in the style's
     * own materials, so it is not substituted again for that style.
     */
    public void capture(BuildingType type, String biomeSet, int tier, Blueprint blueprint) {
        if (tier < 1) {
            throw new IllegalArgumentException("tier must be at least 1");
        }
        captured.put(id(type, biomeSet, tier), blueprint);
    }

    /** Forget a captured template, so the generated or vanilla one is used again. */
    public boolean uncapture(BuildingType type, String biomeSet, int tier) {
        return captured.remove(id(type, biomeSet, tier)) != null;
    }

    /** The ladder for a type in a style, tier 1 first. Empty for a type with no template at all. */
    public List<Template> ladder(BuildingType type, String biomeSet) {
        String biome = BiomeSet.normalize(biomeSet);
        List<Template> rungs = new ArrayList<>();
        switch (type) {
            case HOUSE -> {
                rungs.add(new Template(type, biome, 1, vanilla(biome, "houses", biome + "_small_house_1"), Source.VANILLA));
                rungs.add(new Template(type, biome, 2, vanilla(biome, "houses", biome + "_medium_house_1"), Source.VANILLA));
                if (BiomeSet.PLAINS.equals(biome)) {
                    rungs.add(new Template(type, biome, 3, vanilla(biome, "houses", "plains_big_house_1"), Source.VANILLA));
                }
            }
            case FARM -> {
                rungs.add(new Template(type, biome, 1, vanilla(biome, "houses", FARM.get(biome)), Source.VANILLA));
                if (LARGE_FARM.contains(biome)) {
                    rungs.add(new Template(type, biome, 2, vanilla(biome, "houses", biome + "_large_farm_1"), Source.VANILLA));
                }
            }
            case SQUARE -> {
                // The game's own town centres, plainest first. Every one fits the plan's 15 by 15 square.
                String[] pieces = switch (biome) {
                    case BiomeSet.DESERT -> new String[] {"desert_meeting_point_2", "desert_meeting_point_3"};
                    case BiomeSet.SAVANNA -> new String[] {"savanna_meeting_point_4", "savanna_meeting_point_2"};
                    case BiomeSet.SNOWY -> new String[] {"snowy_meeting_point_3", "snowy_meeting_point_2"};
                    case BiomeSet.TAIGA -> new String[] {"taiga_meeting_point_2"};
                    default -> new String[] {"plains_fountain_01", "plains_meeting_point_3"};
                };
                for (int i = 0; i < pieces.length; i++) {
                    rungs.add(new Template(type, biome, i + 1, vanilla(biome, "town_centers", pieces[i]), Source.VANILLA));
                }
            }
            case STREET_LIGHTS, PALISADE -> {
                // works on the plan: no template, so no rungs
            }
            case SMITHY -> rungs.add(new Template(type, biome, 1, vanilla(biome, "houses", biome + "_tool_smith_1"),
                    Source.VANILLA));
            default -> {
                for (int tier = 1; tier <= BuildingGenerator.TIERS; tier++) {
                    rungs.add(new Template(type, biome, tier,
                            "generated:" + type.name().toLowerCase(Locale.ROOT) + "/" + biome + "/tier" + tier,
                            Source.GENERATED));
                }
            }
        }
        // A captured build replaces what the rung would have been, and can add rungs past the end.
        int top = rungs.size();
        for (int tier = 1; tier <= Math.max(top, highestCaptured(type, biome)); tier++) {
            if (captured.containsKey(id(type, biome, tier))) {
                Template captive = new Template(type, biome, tier, "captured:" + id(type, biome, tier).toLowerCase(Locale.ROOT),
                        Source.CAPTURED);
                if (tier <= rungs.size()) {
                    rungs.set(tier - 1, captive);
                } else if (tier == rungs.size() + 1) {
                    rungs.add(captive);
                }
            }
        }
        return rungs;
    }

    private int highestCaptured(BuildingType type, String biome) {
        int highest = 0;
        for (int tier = 1; tier < 20; tier++) {
            if (captured.containsKey(id(type, biome, tier))) {
                highest = tier;
            }
        }
        return highest;
    }

    /**
     * The blocks of a generated or captured rung, in the style's materials. Empty for a vanilla rung: the Paper
     * layer reads those from the server's structure files.
     */
    public Optional<Blueprint> blueprint(Template template) {
        Blueprint own = captured.get(id(template.type(), template.biomeSet(), template.tier()));
        if (own != null) {
            return Optional.of(own);
        }
        if (template.source() != Source.GENERATED) {
            return Optional.empty();
        }
        return BuildingGenerator.generate(template.type(), template.tier()).map(b -> b.inBiome(template.biomeSet()));
    }

    /** The plainest rung above {@code currentTier} the village can pay for in full now: what a village builds first. */
    public static Optional<Template> plainestAffordable(List<Template> ladder, int currentTier,
            Function<Template, Map<ResourceType, Integer>> costOf, Map<ResourceType, Integer> stock) {
        for (Template rung : ladder) {
            if (rung.tier() <= currentTier) {
                continue;
            }
            boolean affordable = true;
            for (Map.Entry<ResourceType, Integer> need : costOf.apply(rung).entrySet()) {
                if (stock.getOrDefault(need.getKey(), 0) < need.getValue()) {
                    affordable = false;
                    break;
                }
            }
            if (affordable) {
                return Optional.of(rung);
            }
        }
        return Optional.empty();
    }

    /**
     * R4.26: of the rungs above {@code currentTier} the stores can pay for, the one with the most beds for its cost (beds
     * per unit of everything it costs), the plainest of equals first. Only a rung with more beds than {@code currentBeds}
     * is considered, so an upgrade always adds room. Empty if none.
     */
    public static Optional<Template> mostBedsAffordable(List<Template> ladder, int currentTier,
            Function<Template, Map<ResourceType, Integer>> costOf, Function<Template, Integer> bedsOf,
            Map<ResourceType, Integer> stock, int currentBeds) {
        Template best = null;
        double bestValue = -1;
        for (Template rung : ladder) {
            if (rung.tier() <= currentTier) {
                continue;
            }
            Map<ResourceType, Integer> cost = costOf.apply(rung);
            boolean affordable = true;
            long units = 0;
            for (Map.Entry<ResourceType, Integer> need : cost.entrySet()) {
                if (stock.getOrDefault(need.getKey(), 0) < need.getValue()) {
                    affordable = false;
                    break;
                }
                units += need.getValue();
            }
            int beds = bedsOf.apply(rung);
            if (!affordable || beds <= currentBeds) {
                continue;
            }
            double value = (double) beds / Math.max(1, units);
            if (value > bestValue) {
                best = rung;
                bestValue = value;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The best rung above {@code currentTier} (0 for nothing built) the village can pay for in full now, if any: the
     * highest tier whose cost fits the stock. {@code costOf} says what a rung costs, normally from its blueprint (or
     * from the blocks the Paper layer read for a vanilla piece) minus what is already built.
     */
    public static Optional<Template> bestAffordable(List<Template> ladder, int currentTier,
            Function<Template, Map<ResourceType, Integer>> costOf, Map<ResourceType, Integer> stock) {
        Template best = null;
        for (Template rung : ladder) {
            if (rung.tier() <= currentTier) {
                continue;
            }
            boolean affordable = true;
            for (Map.Entry<ResourceType, Integer> need : costOf.apply(rung).entrySet()) {
                if (stock.getOrDefault(need.getKey(), 0) < need.getValue()) {
                    affordable = false;
                    break;
                }
            }
            if (affordable) {
                best = rung;
            }
        }
        return Optional.ofNullable(best);
    }
}
