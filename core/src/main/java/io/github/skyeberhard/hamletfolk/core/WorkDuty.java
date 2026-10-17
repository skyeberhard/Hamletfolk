package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * R4.17: the rules for a resident who visibly works. A resident whose trade has a registered workplace (a farmer at a farm, a miner at
 * a mine, a smith at a smithy, a trade at its building) walks there while it is the village's working hours, stays a while, swinging its
 * arm, and wanders back to what a villager does: the brain module's work behaviour acts it out through the shared pathing service (R9.5).
 * It is presentation only: nothing here reads or changes the ledger, a trade or a need, and a village nobody is near does none of it.
 * Pure, so it is tested here; the module decides the time of day (the villager's own schedule says when it is at work) and the world.
 */
public final class WorkDuty {
    /** The behaviour's name in {@link BrainBehaviours#ALL} and the config. */
    public static final String BEHAVIOUR = "work";

    /** The most residents walking to a workplace at once, in the whole server: it is a show, not a crowd. */
    public static final int MAX_WALKING = 8;
    /** Ticks a resident stays at its workplace, working, at least and at most. */
    public static final int DWELL_MIN = 300;
    public static final int DWELL_MAX = 700;
    /** Ticks it goes about its day before the next visit, at least and at most. */
    public static final int REST_MIN = 1800;
    public static final int REST_MAX = 4200;
    /** How near (blocks) counts as having arrived. */
    public static final int CLOSE_ENOUGH = 3;
    /** Farther than this (blocks) from its workplace, a resident that wandered off has stopped working. */
    public static final int STRAY = 8;

    private WorkDuty() {
    }

    /** The registered place a resident works at: where, and what it is. */
    public record Place(BuildingType type, int x, int y, int z) {
    }

    /** The kinds of building a trade works at (none for the lumberjack, a builder, a guard or the jobless). */
    public static List<BuildingType> placesOf(Occupation job) {
        List<BuildingType> out = new ArrayList<>();
        for (BuildingType type : BuildingType.values()) {
            if (!type.isWorks() && !type.isPart() && type.jobs().contains(job)) {
                out.add(type);
            }
        }
        return out;
    }

    /**
     * Where this resident works, if its trade has a registered workplace in its village: of the buildings of its kind the one its id picks,
     * so several farmers share the village's farms rather than all going to the nearest. Never a child, and never a guard (who patrols).
     */
    public static Optional<Place> workplace(Settlement settlement, Resident resident) {
        if (!resident.adult() || resident.occupation() == Occupation.GUARD) {
            return Optional.empty();
        }
        List<BuildingType> kinds = placesOf(resident.occupation());
        if (kinds.isEmpty()) {
            return Optional.empty();
        }
        List<Building> there = new ArrayList<>();
        for (Building building : settlement.buildings()) {
            if (kinds.contains(building.type())) {
                there.add(building);
            }
        }
        if (there.isEmpty()) {
            return Optional.empty();
        }
        there.sort(java.util.Comparator.comparing(Building::key)); // (the same order every time, whatever the order they were registered in)
        Building chosen = there.get(Math.floorMod(resident.id().hashCode(), there.size()));
        return Optional.of(new Place(chosen.type(), chosen.x(), chosen.y(), chosen.z()));
    }

    /** The behaviours a resident near a player gets: those that run, less the work behaviour unless it has a workplace to go to. */
    public static Set<String> fit(Set<String> running, boolean hasWorkplace) {
        if (hasWorkplace || !running.contains(BEHAVIOUR)) {
            return running;
        }
        Set<String> out = new HashSet<>(running);
        out.remove(BEHAVIOUR);
        return out;
    }

    /** Whether another resident may set out for its workplace, given how many are walking to theirs now. */
    public static boolean maySetOut(int walkingNow) {
        return walkingNow < MAX_WALKING;
    }

    /** The village's working day, as the world's time of day (ticks): from two hours after dawn to the afternoon, as a vanilla villager's. */
    public static final int WORK_FROM = 2000;
    public static final int WORK_UNTIL = 9000;

    /** Whether it is the working day at this time of day (ticks; any multiple of a day is fine). */
    public static boolean workHours(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return t >= WORK_FROM && t < WORK_UNTIL;
    }

    /** Whether a resident at this distance (blocks) from its workplace is still working it. */
    public static boolean atWork(double distance) {
        return distance <= STRAY;
    }

    /** How many ticks to stay at the workplace. */
    public static int dwell(Random random) {
        return DWELL_MIN + random.nextInt(DWELL_MAX - DWELL_MIN + 1);
    }

    /** How many ticks to go about the day before the next visit. */
    public static int rest(Random random) {
        return REST_MIN + random.nextInt(REST_MAX - REST_MIN + 1);
    }
}
