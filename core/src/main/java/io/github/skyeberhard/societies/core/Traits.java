package io.github.skyeberhard.societies.core;

import java.util.Random;

/** Fixed personality values, each 0-100. They shape output and dialogue. */
public record Traits(int workEthic, int sociability, int ambition, int bravery) {

    public Traits {
        workEthic = Needs.clamp(workEthic);
        sociability = Needs.clamp(sociability);
        ambition = Needs.clamp(ambition);
        bravery = Needs.clamp(bravery);
    }

    /** Rolls traits clustered around the middle, so extremes are rare. */
    public static Traits roll(Random random) {
        return new Traits(bell(random), bell(random), bell(random), bell(random));
    }

    /** Blends two parents' traits with some variation, for children. */
    public static Traits inherit(Traits a, Traits b, Random random) {
        return new Traits(
                mix(a.workEthic, b.workEthic, random),
                mix(a.sociability, b.sociability, random),
                mix(a.ambition, b.ambition, random),
                mix(a.bravery, b.bravery, random));
    }

    private static int bell(Random random) {
        return (random.nextInt(51) + random.nextInt(51));
    }

    private static int mix(int a, int b, Random random) {
        return (a + b) / 2 + random.nextInt(21) - 10;
    }
}
