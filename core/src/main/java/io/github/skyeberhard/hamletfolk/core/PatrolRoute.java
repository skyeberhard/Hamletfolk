package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * R9.6: where a guard on duty walks when nothing needs fighting, for the brain module's patrol behaviour. The route is a loop
 * through the village's plan (R8.3): the middle of the square, the middle and both ends of every street, taken in order round
 * the village centre, so a guard walking it passes everywhere people live. A village with no plan gets a ring round its
 * centre. Each guard starts at a point of its own (so several do not walk in a bunch), stays a while at each (long by day,
 * hardly at all at night, when the monsters come) and walks on. Pure, so it is tested here. The height of the ground at a
 * spot is the plugin's to give ({@link Spot#withGround}); without it the behaviour uses the guard's own.
 */
public final class PatrolRoute {
    private PatrolRoute() {
    }

    /** "No ground height known": the behaviour uses the height the guard stands at. */
    public static final int NO_GROUND = Integer.MIN_VALUE;

    /** A place on the ground to walk to. */
    public record Spot(int x, int y, int z) {
        public static Spot at(int x, int z) {
            return new Spot(x, NO_GROUND, z);
        }

        public Spot withGround(int ground) {
            return new Spot(x, ground, z);
        }

        double distance(Spot other) {
            return Math.hypot(x - other.x, z - other.z);
        }

        /** (The form {@link BrainReport#redact} takes out of a report; the height is left out.) */
        @Override
        public String toString() {
            return "x=" + x + " z=" + z;
        }
    }

    /** The most spots a route has at plan stage 1; each later stage allows three more (its streets are longer and more). */
    public static final int BASE_SPOTS = 11;
    /** Spots closer than this to one already on the route are left out. */
    public static final int MIN_GAP = 6;
    /** Radius of the ring a village with no plan is walked round. */
    public static final int RING = 14;
    /**
     * Ticks a guard stays at a spot: by day and at night. At night it hardly stops: a villager with no walk target heads for
     * its bed, so a guard on the night watch keeps walking.
     */
    public static final int DAY_DWELL_MIN = 400;
    public static final int DAY_DWELL_MAX = 900;
    public static final int NIGHT_DWELL_MIN = 0;
    public static final int NIGHT_DWELL_MAX = 20;

    /** How many spots a route through this plan may have. */
    public static int maxSpots(VillagePlan plan) {
        return BASE_SPOTS + 3 * ((plan == null ? 1 : Math.max(1, plan.stage())) - 1);
    }

    /** The loop for a village: its plan's square and streets, else a ring round its centre. */
    public static List<Spot> route(VillagePlan plan, int centerX, int centerZ) {
        List<Spot> raw = new ArrayList<>();
        if (plan != null) {
            if (plan.square() != null) {
                raw.add(Spot.at(plan.square().centerX(), plan.square().centerZ()));
            }
            for (VillagePlan.Road road : plan.roads()) {
                Rect r = road.rect();
                boolean alongX = plan.runsAlongX(road);
                raw.add(Spot.at(r.centerX(), r.centerZ()));
                raw.add(alongX ? Spot.at(r.x() + 1, r.centerZ()) : Spot.at(r.centerX(), r.z() + 1));
                raw.add(alongX ? Spot.at(r.maxX() - 1, r.centerZ()) : Spot.at(r.centerX(), r.maxZ() - 1));
            }
        }
        if (raw.isEmpty()) {
            for (int i = 0; i < 6; i++) {
                double angle = i * Math.PI / 3;
                raw.add(Spot.at(centerX + (int) Math.round(Math.cos(angle) * RING), centerZ + (int) Math.round(Math.sin(angle) * RING)));
            }
        }
        List<Spot> kept = new ArrayList<>();
        for (Spot s : raw) {
            boolean crowded = false;
            for (Spot k : kept) {
                if (k.distance(s) < MIN_GAP) {
                    crowded = true;
                    break;
                }
            }
            if (!crowded) {
                kept.add(s);
            }
        }
        // thin to the most spread-out spots: drop the one closest to its nearest neighbour, until few enough
        int most = maxSpots(plan);
        while (kept.size() > most) {
            int crowdedAt = 0;
            double least = Double.MAX_VALUE;
            for (int i = 0; i < kept.size(); i++) {
                double nearest = Double.MAX_VALUE;
                for (int j = 0; j < kept.size(); j++) {
                    if (j != i) {
                        nearest = Math.min(nearest, kept.get(i).distance(kept.get(j)));
                    }
                }
                if (nearest < least) {
                    least = nearest;
                    crowdedAt = i;
                }
            }
            kept.remove(crowdedAt);
        }
        double cx = centerX;
        double cz = centerZ;
        kept.sort(Comparator.comparingDouble((Spot s) -> Math.atan2(s.z() - cz, s.x() - cx)).thenComparingInt(Spot::x).thenComparingInt(Spot::z));
        return List.copyOf(kept);
    }

    /**
     * Whether this guard keeps the night watch: the guards on duty in a village take turns, every other one (in the order of
     * their ids) walks the night while the rest sleep, and a lone guard keeps it. Everyone patrols by day.
     */
    public static boolean watchesNight(java.util.Collection<UUID> onDuty, UUID guard) {
        List<UUID> sorted = new ArrayList<>(onDuty);
        sorted.sort(Comparator.comparing(UUID::toString));
        int at = sorted.indexOf(guard);
        return at < 0 || at % 2 == 0;
    }

    /** The spot a guard starts at: its own place round the loop, the same every time. */
    public static int startIndex(UUID guard, int spots) {
        return spots <= 0 ? 0 : Math.floorMod(guard.hashCode(), spots);
    }

    /** The spot after this one (round the loop). */
    public static int next(int index, int spots) {
        return spots <= 0 ? 0 : (index + 1) % spots;
    }

    /** How many ticks to stay at a spot: by night (the villager's bedtime) or by day. */
    public static int dwell(boolean night, Random random) {
        int min = night ? NIGHT_DWELL_MIN : DAY_DWELL_MIN;
        int max = night ? NIGHT_DWELL_MAX : DAY_DWELL_MAX;
        return min + random.nextInt(max - min + 1);
    }
}
