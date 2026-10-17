package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * R6.1: marriages. On most days nothing happens; now and then two unmarried adults of the village, a man and a woman who are not kin,
 * marry, and the history says so. The pair is picked from the village's own people on the village's own clock (repeatable: the same
 * village on the same day makes the same choice). A couple who have a child and were not married are married then (Births). A marriage
 * ends when either of them leaves the village or dies ({@link Settlement#removeResident}). Names are not changed: the household is
 * named for the wife's family, as her children are.
 */
public final class Marriages {
    /** The chance on a day with a possible pair that they marry. */
    static final double CHANCE = 0.12;

    private Marriages() {
    }

    /** True for an adult in the prime of life with no spouse, who is not a child still waiting for a villager. */
    static boolean eligible(Settlement settlement, Resident r, long day) {
        return r.adult() && r.stage(day) == LifeStage.ADULT && r.spouse() == null && !settlement.hasCondition(Births.AWAITING + r.id());
    }

    /** Marries a pair today if one is due; returns the woman then the man. Call once a day per village. */
    public static Optional<Resident[]> run(Settlement settlement, long day) {
        if (settlement.isAbandoned() || SettlementSimulator.inDanger(settlement, day) || settlement.hasCondition("famine")) {
            return Optional.empty();
        }
        Random random = new Random(mix(settlement.id().getLeastSignificantBits() * 31 + day));
        if (random.nextDouble() >= CHANCE) {
            return Optional.empty(); // (rolled first: most days nothing happens, and then nobody need be looked at)
        }
        List<Resident> women = new ArrayList<>();
        List<Resident> men = new ArrayList<>();
        for (Resident r : settlement.residents()) {
            if (eligible(settlement, r, day)) {
                (r.gender() == Gender.FEMALE ? women : men).add(r);
            }
        }
        if (women.isEmpty() || men.isEmpty()) {
            return Optional.empty();
        }
        women.sort(Comparator.comparing(Resident::id)); // repeatable
        men.sort(Comparator.comparing(Resident::id));
        Resident woman = women.get(random.nextInt(women.size()));
        Resident man = null;
        for (Resident candidate : men) { // the likeliest match: the most alike in sociability, then ambition
            if (Births.kin(woman, candidate)) {
                continue;
            }
            if (man == null || difference(woman, candidate) < difference(woman, man)) {
                man = candidate;
            }
        }
        if (man == null) {
            return Optional.empty();
        }
        marry(settlement, woman, man, day);
        return Optional.of(new Resident[] {woman, man});
    }

    /** Spreads neighbouring days apart: java.util.Random's first draws from seeds one apart are nearly the same. */
    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static int difference(Resident a, Resident b) {
        return Math.abs(a.traits().sociability() - b.traits().sociability()) * 2 + Math.abs(a.traits().ambition() - b.traits().ambition());
    }

    /** Marries two residents of the village today and records it. Both must be unmarried. */
    public static void marry(Settlement settlement, Resident woman, Resident man, long day) {
        if (woman.spouse() != null || man.spouse() != null) {
            return;
        }
        woman.marry(man.id(), day);
        man.marry(woman.id(), day);
        settlement.record(day, HistoryEvent.Kind.MARRIAGE, woman.fullName() + " and " + man.fullName() + " were married.");
    }
}
