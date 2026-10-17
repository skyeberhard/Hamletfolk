package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * R8.23: what an admin may tell a village's planner and builders: keep a lot empty, change what a lot is for, never build a kind of
 * building, or build one next. Each is kept in the village's conditions (nothing new in the save), and read by the planner
 * ({@link Planner#directives}) and the builders ({@link Construction#propose}), which otherwise decide as before. Nothing here moves a
 * building that stands: a lot that is built on is out of reach, and forbidding a kind only stops the village raising more of it.
 */
public final class PlanOverrides {
    /** Condition prefix for a lot kept empty: {@code lotLocked:<lot id>}. */
    static final String LOCKED = "lotLocked:";
    /** Condition prefix for a kind the village may not build: {@code forbid:<TYPE>}. */
    static final String FORBIDDEN = "forbid:";
    /** Condition prefix for a kind to be built before anything else: {@code buildNext:<TYPE>}, the day it was asked for. */
    static final String BUILD_NEXT = "buildNext:";
    /** Condition prefix for a project queued on an admin's order: {@code ordered:<project id>}, the day. It is kept while it waits for a builder. */
    static final String ORDERED = "ordered:";
    /** How long an order to build something next stands (days) before the village goes back to its own list. */
    public static final int BUILD_NEXT_DAYS = 40;

    /** The reason a directive carries when it comes from an admin's order, so the builders know not to put the meeting place first. */
    static final String ORDER_REASON = "an admin asked for it to be built next";

    /** What an override did, for the admin and the history. */
    public record Result(boolean ok, String message) {
    }

    private PlanOverrides() {
    }

    // ----- reading -----

    public static boolean isLocked(Settlement settlement, VillagePlan.Lot lot) {
        return settlement.hasCondition(LOCKED + lot.id());
    }

    public static boolean isForbidden(Settlement settlement, BuildingType type) {
        return settlement.hasCondition(FORBIDDEN + type.name());
    }

    /** The kinds the village may not build, in the order of {@link BuildingType}. */
    public static List<BuildingType> forbidden(Settlement settlement) {
        List<BuildingType> out = new ArrayList<>();
        for (BuildingType type : BuildingType.values()) {
            if (isForbidden(settlement, type)) {
                out.add(type);
            }
        }
        return out;
    }

    /** The kind an admin asked to be built next, if the order is still standing on {@code day}. */
    public static Optional<BuildingType> buildNext(Settlement settlement, long day) {
        for (BuildingType type : BuildingType.values()) {
            Long asked = settlement.conditions().get(BUILD_NEXT + type.name());
            if (asked != null && day - asked <= BUILD_NEXT_DAYS) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** The ids of the lots kept empty, lowest first. */
    public static List<Integer> lockedLots(Settlement settlement) {
        List<Integer> out = new ArrayList<>();
        if (settlement.plan() != null) {
            for (VillagePlan.Lot lot : settlement.plan().lots()) {
                if (isLocked(settlement, lot)) {
                    out.add(lot.id());
                }
            }
        }
        return out;
    }

    // ----- what the planner and builders do with them -----

    /**
     * Called when a project is queued. A new building of the kind an admin ordered ends the order, and the project is marked as ordered,
     * so the builders do not drop it the next day as "not needed" (the planner no longer asks for what is already in hand). An upgrade
     * is not the building that was ordered.
     */
    static void noteQueued(Settlement settlement, ConstructionProject project) {
        if (project.isUpgrade()) {
            return;
        }
        if (settlement.conditions().remove(BUILD_NEXT + project.type().name()) != null) {
            settlement.conditions().put(ORDERED + project.id(), settlement.lastSimulatedDay());
        }
    }

    /** Whether a queued project was begun on an admin's order, and the order has not run out. */
    static boolean isOrdered(Settlement settlement, int projectId, long day) {
        Long asked = settlement.conditions().get(ORDERED + projectId);
        return asked != null && day - asked <= BUILD_NEXT_DAYS;
    }

    /** Forgets the lots kept empty: their ids belong to a plan that has been replaced. */
    public static void forgetLots(Settlement settlement) {
        settlement.conditions().keySet().removeIf(k -> k.startsWith(LOCKED));
    }

    /** Whether a project that is not given up (a new building) stands on the lot, though the lot is not built on until it finishes. */
    private static boolean projectOn(Settlement settlement, int lotId) {
        return settlement.projects().stream().anyMatch(p -> p.lotId() == lotId && !p.isUpgrade()
                && p.status() != ConstructionProject.Status.CANCELLED && p.status() != ConstructionProject.Status.DONE);
    }

    // ----- changing them (the dashboard's actions) -----

    /** Whether a kind may be named in an override at all: a part of a rampart is only ever built by the rampart. */
    public static boolean nameable(BuildingType type) {
        return !type.isPart();
    }

    /** Whether a lot may be made for this kind: anything with a place on a lot (not the works, laid on the streets, nor the square, built on its own). */
    public static boolean lotKind(BuildingType type) {
        return !type.isPart() && !type.isWorks() && type != BuildingType.SQUARE;
    }

    /** Whether a kind may be ordered built next: the square is built on its own ground, when the village is ready for it. */
    public static boolean orderable(BuildingType type) {
        return nameable(type) && type != BuildingType.SQUARE;
    }

    public static Result lock(Settlement settlement, int lotId, boolean locked, long day, String actor) {
        Optional<VillagePlan.Lot> lot = lot(settlement, lotId);
        if (!locked) { // always possible, whatever became of the lot
            boolean had = settlement.conditions().remove(LOCKED + lotId) != null;
            if (!had) {
                return new Result(true, "Lot " + lotId + " is already open to building.");
            }
            settlement.record(day, HistoryEvent.Kind.BUILDING, "Lot " + lotId + " was opened to building by " + actor + ".");
            return new Result(true, "Lot " + lotId + " in " + settlement.name() + " is open to building again.");
        }
        if (lot.isEmpty()) {
            return new Result(false, settlement.name() + " has no lot " + lotId + ".");
        }
        if (lot.get().status() == VillagePlan.LotStatus.FILLED) {
            return new Result(false, "Lot " + lotId + " is already built on.");
        }
        if (projectOn(settlement, lotId)) {
            return new Result(false, "A building is already being raised on lot " + lotId + ": give the project up first.");
        }
        if (isLocked(settlement, lot.get())) {
            return new Result(true, "Lot " + lotId + " is already kept empty.");
        }
        settlement.conditions().put(LOCKED + lotId, day);
        String what = lot.get().type().label().toLowerCase(Locale.ROOT) + " lot " + lotId;
        settlement.record(day, HistoryEvent.Kind.BUILDING, "The " + what + " was kept empty by " + actor + ".");
        return new Result(true, "The " + what + " in " + settlement.name() + " is kept empty. If it is the last lot of its kind the plan may grow, "
                + "or the village find ground elsewhere, for the building it wants.");
    }

    public static Result retype(Settlement settlement, int lotId, BuildingType type, long day, String actor) {
        Optional<VillagePlan.Lot> lot = lot(settlement, lotId);
        if (lot.isEmpty()) {
            return new Result(false, settlement.name() + " has no lot " + lotId + ".");
        }
        if (!lotKind(type)) {
            return new Result(false, "A lot cannot be made for " + type.label().toLowerCase(Locale.ROOT) + ".");
        }
        if (lot.get().status() == VillagePlan.LotStatus.FILLED) {
            return new Result(false, "Lot " + lotId + " is already built on.");
        }
        if (projectOn(settlement, lotId)) {
            return new Result(false, "A building is already being raised on lot " + lotId + ": give the project up first.");
        }
        if (lot.get().type() == type) {
            return new Result(true, "Lot " + lotId + " is already for a " + type.label().toLowerCase(Locale.ROOT) + ".");
        }
        BuildingType was = lot.get().type();
        settlement.plan().retype(lotId, type);
        settlement.record(day, HistoryEvent.Kind.BUILDING, "Lot " + lotId + " was set aside for a " + type.label().toLowerCase(Locale.ROOT)
                + " instead of a " + was.label().toLowerCase(Locale.ROOT) + " by " + actor + ".");
        return new Result(true, "Lot " + lotId + " in " + settlement.name() + " is now for a " + type.label().toLowerCase(Locale.ROOT)
                + " (was a " + was.label().toLowerCase(Locale.ROOT) + "). The builders still need the template to fit it.");
    }

    public static Result forbid(Settlement settlement, BuildingType type, boolean forbidden, long day, String actor) {
        if (!nameable(type)) {
            return new Result(false, "That cannot be named.");
        }
        if (forbidden == isForbidden(settlement, type)) {
            return new Result(true, settlement.name() + (forbidden ? " already may not build a " : " already may build a ")
                    + type.label().toLowerCase(Locale.ROOT) + ".");
        }
        if (forbidden) {
            settlement.conditions().put(FORBIDDEN + type.name(), day);
            settlement.conditions().remove(BUILD_NEXT + type.name());
        } else {
            settlement.conditions().remove(FORBIDDEN + type.name());
        }
        settlement.record(day, HistoryEvent.Kind.BUILDING, settlement.name() + (forbidden ? " was told not to build a " : " was allowed to build a ")
                + type.label().toLowerCase(Locale.ROOT) + " by " + actor + ".");
        return new Result(true, settlement.name() + (forbidden ? " will not raise a " : " may raise a ") + type.label().toLowerCase(Locale.ROOT)
                + (forbidden ? ". Any that stand are left alone." : " again."));
    }

    /** Orders a kind built before anything else (for {@link #BUILD_NEXT_DAYS} days, or until a project for it is queued). */
    public static Result buildNext(Settlement settlement, BuildingType type, boolean ordered, long day, String actor) {
        if (!nameable(type)) {
            return new Result(false, "That cannot be named.");
        }
        if (ordered && !orderable(type)) {
            return new Result(false, "The " + type.label().toLowerCase(Locale.ROOT) + " is built on its own, when the village is ready for it.");
        }
        if (!ordered) {
            boolean had = settlement.conditions().remove(BUILD_NEXT + type.name()) != null;
            return new Result(true, had ? "The order to build a " + type.label().toLowerCase(Locale.ROOT) + " next is withdrawn."
                    : "No order to build a " + type.label().toLowerCase(Locale.ROOT) + " next was standing.");
        }
        if (isForbidden(settlement, type)) {
            return new Result(false, settlement.name() + " is forbidden to build a " + type.label().toLowerCase(Locale.ROOT) + ": allow it first.");
        }
        for (BuildingType other : BuildingType.values()) {
            settlement.conditions().remove(BUILD_NEXT + other.name()); // one order at a time
        }
        settlement.conditions().put(BUILD_NEXT + type.name(), day);
        settlement.record(day, HistoryEvent.Kind.BUILDING, settlement.name() + " was told to build a " + type.label().toLowerCase(Locale.ROOT)
                + " next by " + actor + ".");
        return new Result(true, settlement.name() + " will build a " + type.label().toLowerCase(Locale.ROOT) + " next, if it has the people, a lot and the materials (the order stands "
                + BUILD_NEXT_DAYS + " days).");
    }

    private static Optional<VillagePlan.Lot> lot(Settlement settlement, int id) {
        if (settlement.plan() == null) {
            return Optional.empty();
        }
        return settlement.plan().lots().stream().filter(l -> l.id() == id).findFirst();
    }
}
