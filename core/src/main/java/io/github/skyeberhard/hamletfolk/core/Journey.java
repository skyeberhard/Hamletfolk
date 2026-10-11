package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * R9.5: one villager's journey to a place, for every brain behaviour that needs to move a villager. The villager's brain
 * re-decides its movement every few ticks, so a path it is given may be dropped (another behaviour sets its own walk target,
 * or the brain gives up on the path) or may never get nearer. The journey copes in three steps and says which one got there:
 *
 * <ol>
 *   <li><b>A path</b> to the goal (through the road waypoints when there are any, R8.3): set again when the brain drops it, up to
 *       {@link #MAX_SETBACKS} times (a drop, or {@link #STALL_TICKS} without getting a block nearer);</li>
 *   <li><b>short hops</b> of {@link #HOP} blocks toward the next waypoint, each to a place a villager can stand, turning aside when
 *       one fails, until {@link #MAX_HOP_FAILS} fail in a row;</li>
 *   <li><b>a teleport</b> to the goal if the caller allows it, otherwise it gives up; it also gives up if there is no safe place
 *       to stand there.</li>
 * </ol>
 *
 * Each step and each fallback is written to the log it is given (the debug log, R9.2). Pure: the brain module supplies a
 * {@link Mover} for the villager and calls {@link #tick} once a tick, so the whole sequence is tested with a fake one.
 */
public final class Journey {
    /** A block position. */
    public record Point(int x, int y, int z) {
        public double horizontalDistance(Point other) {
            return Math.hypot(x - other.x, z - other.z);
        }

        /** "x=40 y=64 z=0": the form {@link BrainReport#redact} takes out of a report. */
        @Override
        public String toString() {
            return "x=" + x + " y=" + y + " z=" + z;
        }
    }

    /** What the journey did in the end. */
    public enum Outcome {
        /** Walked all the way on a path. */
        WALKED,
        /** Got there, at least partly by short hops after the path failed. */
        HOPPED,
        /** Could not walk there and was teleported. */
        TELEPORTED,
        /** Could not walk there and was not teleported (not allowed, or nowhere safe to stand). */
        GAVE_UP,
        /** Ended before it was finished: the module or the behaviour was switched off, the villager fell asleep, a new journey replaced it. */
        STOPPED;

        public boolean arrived() {
            return this == WALKED || this == HOPPED || this == TELEPORTED;
        }
    }

    /** How a journey ended, why, and how many ticks it took. */
    public record Result(Outcome outcome, String detail, long ticks) {
        public String describe() {
            String time = String.format(Locale.ROOT, "%.1f s", ticks / 20.0);
            return switch (outcome) {
                case WALKED -> "walked there (" + time + ")";
                case HOPPED -> "got there in short hops after the path failed (" + time + "; " + detail + ")";
                case TELEPORTED -> "could not walk there and was teleported (after " + time + "; " + detail + ")";
                case GAVE_UP -> "could not get there and gave up (after " + time + "; " + detail + ")";
                case STOPPED -> "stopped before getting there (after " + time + "; " + detail + ")";
            };
        }
    }

    /** The villager, as the journey sees it. Implemented in the brain module; a fake in tests. */
    public interface Mover {
        /** Where the villager stands now. */
        Point position();

        /** Sets the villager's walk target to this block (it is then "ours" until something replaces it). */
        void walkTo(Point target, int closeEnough);

        /**
         * Whether something else has put a walk target of its own in place of ours (the game's idle chat does), as opposed to the
         * brain having found no route and cleared it. Only false when there is none at all.
         */
        boolean steered();

        /** Whether the villager is still walking to the target last given to {@link #walkTo} (false once anything dropped or replaced it). */
        boolean walking();

        /** A block near this one (same column, a few blocks up or down) a villager can stand in, or empty if there is none. */
        Optional<Point> standingNear(Point point);

        /** Moves the villager to stand at this block; false if there is no safe place there (and it did not move). */
        boolean teleport(Point point);
    }

    /** Times the path may be dropped or stall before the journey falls back to hops. */
    public static final int MAX_SETBACKS = 3;
    /** Ticks without getting a block nearer that count as a stall. */
    public static final int STALL_TICKS = 100;
    /** Length of a hop, in blocks. */
    public static final int HOP = 6;
    /** Ticks a single hop may take. */
    public static final int HOP_TICKS = 80;
    /** Hops that may fail in a row before the journey stops walking. */
    public static final int MAX_HOP_FAILS = 3;
    /** How near a road waypoint (horizontally) counts as reaching it. */
    public static final int WAYPOINT_NEAR = 3;
    /**
     * A path the brain replaces within this many ticks of our setting it was replaced by something else steering the villager
     * (the game's idle chat with a neighbour walks two villagers to each other and sets the target again every tick), not
     * dropped for want of a route. Trying again at once only loses the same race, three times in one second; the journey waits
     * {@link #BACKOFF_TICKS} for the chat to end before it tries again.
     */
    public static final int QUICK_TICKS = 10;
    public static final int BACKOFF_TICKS = 40;
    /** Each way of walking may take this many ticks plus {@link #TICKS_PER_BLOCK} for each block of the way that is left. */
    public static final int BASE_TICKS = 200;
    public static final int TICKS_PER_BLOCK = 20;

    private enum Stage {
        PATH, HOPS
    }

    private final Point goal;
    private final List<Point> route;
    private final boolean mayTeleport;
    private final int closeEnough;
    private final Consumer<String> log;

    /** Where it stands at the goal: the goal brought down (or up) to a block a villager can stand in, found on the first tick. */
    private Point end;
    private Stage stage = Stage.PATH;
    private int next;
    private long ticks;
    private long stageStart;
    private long stageLimit = -1;
    private boolean issued;
    private double best;
    private long lastProgress;
    private int setbacks;
    private long issuedAt;
    private long retryAt;
    private boolean reissue;
    private Point hop;
    private long hopStart;
    private int hopFails;
    private int hopsMade;
    private Result result;

    /**
     * A journey to {@code goal}, through {@code waypoints} first (the road route, which may be empty and may end with the goal).
     * It has arrived within {@code closeEnough} blocks of the goal (horizontally, and within two blocks up or down).
     */
    public Journey(Point goal, List<Point> waypoints, boolean mayTeleport, int closeEnough, Consumer<String> log) {
        this.goal = goal;
        this.route = new ArrayList<>(waypoints);
        if (route.isEmpty() || !route.get(route.size() - 1).equals(goal)) {
            route.add(goal);
        }
        this.mayTeleport = mayTeleport;
        this.closeEnough = Math.max(0, closeEnough);
        this.log = log;
    }

    public Point goal() {
        return goal;
    }

    /** How many waypoints come before the goal (the road route). */
    public int waypoints() {
        return route.size() - 1;
    }

    /** The result once it has ended, else empty. */
    public Optional<Result> result() {
        return Optional.ofNullable(result);
    }

    /** One tick of the journey. Returns the result on the tick it ends (and on every call after). */
    public Optional<Result> tick(Mover mover) {
        if (result != null) {
            return Optional.of(result);
        }
        ticks++;
        if (end == null) {
            // a goal in the air or in the ground would never count as reached: aim at the place a villager can stand there
            end = mover.standingNear(goal).orElse(goal);
            route.set(route.size() - 1, end);
        }
        Point at = mover.position();
        if (arrived(at, end, closeEnough)) {
            return finish(stage == Stage.PATH ? Outcome.WALKED : Outcome.HOPPED,
                    stage == Stage.PATH ? "arrived" : hopsMade + " hops");
        }
        if (stageLimit < 0) {
            stageLimit = limit(at);
            log("setting off for " + goal + (waypoints() > 0 ? " by road (" + waypoints() + " waypoints)" : "") + ", "
                    + Math.round(at.horizontalDistance(goal)) + " blocks away");
        }
        if (stage == Stage.PATH) {
            path(mover, at);
        } else {
            hops(mover, at);
        }
        return Optional.ofNullable(result);
    }

    /** Ends it now (if it has not ended) with {@link Outcome#STOPPED}. */
    public Result stop(String why) {
        if (result == null) {
            log("stopped: " + why);
            result = new Result(Outcome.STOPPED, why, ticks);
        }
        return result;
    }

    // ----- the path -----

    private void path(Mover mover, Point at) {
        Point target = advance(at);
        if (reissue) {
            if (ticks - stageStart > stageLimit) {
                toHops(mover, at, "the path was taking too long");
            } else if (ticks >= retryAt) {
                reissue = false;
                walk(mover, target, at, "walking again");
            }
            return;
        }
        if (!issued) {
            walk(mover, target, at, "walking to " + (target.equals(end) ? "the goal" : "waypoint " + (next + 1) + " of " + waypoints()));
            issued = true;
            return;
        }
        double d = at.horizontalDistance(target);
        if (d < best - 1) {
            best = d;
            lastProgress = ticks;
        }
        if (ticks - stageStart > stageLimit) {
            toHops(mover, at, "the path was taking too long");
        } else if (!mover.walking()) {
            setback(mover, at, target, "the brain dropped the path");
        } else if (ticks - lastProgress > STALL_TICKS) {
            setback(mover, at, target, "it got no nearer in " + STALL_TICKS / 20 + " seconds");
        }
    }

    /** Moves on past every waypoint already reached; returns the one to walk to now. */
    private Point advance(Point at) {
        while (next < route.size() - 1 && at.horizontalDistance(route.get(next)) <= WAYPOINT_NEAR) {
            next++;
            issued = false;
            setbacks = 0; // a few ordinary interruptions over a long road trip are not a path that keeps failing
            log("reached waypoint " + next + " of " + waypoints());
        }
        return route.get(next);
    }

    private void walk(Mover mover, Point target, Point at, String what) {
        // a road waypoint's height is only a guess between start and goal: walk to the ground there, or the brain never reaches it
        Point dest = target.equals(end) ? end : mover.standingNear(target).orElse(target);
        mover.walkTo(dest, target.equals(end) ? closeEnough : 1);
        best = at.horizontalDistance(target);
        lastProgress = ticks;
        issuedAt = ticks;
        log(what + " (" + dest + ")");
    }

    private void setback(Mover mover, Point at, Point target, String why) {
        setbacks++;
        if (setbacks >= MAX_SETBACKS) {
            toHops(mover, at, why + " (" + setbacks + " times)");
            return;
        }
        if (ticks - issuedAt < QUICK_TICKS && mover.steered()) {
            retryAt = ticks + BACKOFF_TICKS;
            reissue = true;
            log(why + " at once (" + setbacks + " of " + MAX_SETBACKS + "): something else is steering it, waiting " + BACKOFF_TICKS / 20 + " seconds");
            return;
        }
        log(why + " (" + setbacks + " of " + MAX_SETBACKS + "): trying the path again");
        walk(mover, target, at, "walking again");
    }

    // ----- short hops -----

    private void toHops(Mover mover, Point at, String why) {
        log("falling back to short hops: " + why);
        stage = Stage.HOPS;
        reissue = false;
        stageStart = ticks;
        stageLimit = limit(at);
        hop = null;
        hopFails = 0;
        nextHop(mover, at);
    }

    private void hops(Mover mover, Point at) {
        if (ticks - stageStart > stageLimit) {
            stopWalking(mover, "the hops were taking too long");
            return;
        }
        if (hop == null) {
            if (ticks >= retryAt) {
                nextHop(mover, at);
            }
            return;
        }
        if (arrived(at, hop, 1)) {
            hopFails = 0;
            hopsMade++;
            nextHop(mover, at);
        } else if (!mover.walking()) {
            if (ticks - hopStart < QUICK_TICKS && mover.steered()) {
                retryAt = ticks + BACKOFF_TICKS; // something else is steering it: wait, then the next hop
                hop = null;
                hopFails++;
                if (hopFails >= MAX_HOP_FAILS) {
                    stopWalking(mover, "the brain kept replacing the hop (" + hopFails + " hops failed in a row)");
                } else {
                    log("the brain replaced the hop at once (" + hopFails + " of " + MAX_HOP_FAILS + "): waiting " + BACKOFF_TICKS / 20 + " seconds");
                }
                return;
            }
            hopFailed(mover, at, "the brain dropped the hop");
        } else if (ticks - hopStart > HOP_TICKS) {
            hopFailed(mover, at, "the hop was taking too long");
        }
    }

    /** The next hop: up to {@link #HOP} blocks toward the next waypoint, turned 45 degrees aside after a failed one. */
    private void nextHop(Mover mover, Point at) {
        Point target = advance(at);
        if (result != null) {
            return;
        }
        double dx = target.x() - at.x();
        double dz = target.z() - at.z();
        double length = Math.hypot(dx, dz);
        Point aim;
        if (length <= HOP && hopFails == 0) {
            aim = target;
        } else {
            double turn = switch (hopFails) {
                case 0 -> 0;
                case 1 -> Math.PI / 4;
                default -> -Math.PI / 4;
            };
            double angle = Math.atan2(dz, dx) + turn;
            double step = Math.min(HOP, Math.max(length, 2));
            aim = new Point(at.x() + (int) Math.round(Math.cos(angle) * step), target.equals(end) && length <= HOP ? end.y() : at.y(),
                    at.z() + (int) Math.round(Math.sin(angle) * step));
        }
        Optional<Point> stand = mover.standingNear(aim);
        if (stand.isEmpty()) {
            hop = null;
            hopFailed(mover, at, "nowhere to stand at " + aim);
            return;
        }
        if (stand.get().equals(at)) {
            hop = null;
            hopFailed(mover, at, "the only place to stand is where it is"); // never a hop "made" on the spot, tick after tick
            return;
        }
        hop = stand.get();
        hopStart = ticks;
        mover.walkTo(hop, hop.equals(end) ? closeEnough : 1);
        log("hop " + (hopsMade + 1) + " to " + hop);
    }

    private void hopFailed(Mover mover, Point at, String why) {
        hopFails++;
        if (hopFails >= MAX_HOP_FAILS) {
            stopWalking(mover, why + " (" + hopFails + " hops failed in a row)");
            return;
        }
        log(why + " (" + hopFails + " of " + MAX_HOP_FAILS + "): turning aside");
        nextHop(mover, at);
    }

    // ----- the end -----

    private void stopWalking(Mover mover, String why) {
        if (!mayTeleport) {
            log("giving up: " + why + "; teleporting is not allowed");
            finish(Outcome.GAVE_UP, why + "; teleporting is not allowed");
            return;
        }
        log("teleporting: " + why);
        if (mover.teleport(end) && arrived(mover.position(), end, closeEnough)) {
            finish(Outcome.TELEPORTED, why);
        } else {
            log("giving up: no safe place to stand at " + end);
            finish(Outcome.GAVE_UP, why + "; no safe place to stand at the goal");
        }
    }

    private Optional<Result> finish(Outcome outcome, String detail) {
        result = new Result(outcome, detail, ticks);
        log(result.describe());
        return Optional.of(result);
    }

    private long limit(Point at) {
        double left = 0;
        Point from = at;
        for (int i = next; i < route.size(); i++) {
            left += from.horizontalDistance(route.get(i));
            from = route.get(i);
        }
        return BASE_TICKS + (long) Math.ceil(left) * TICKS_PER_BLOCK;
    }

    static boolean arrived(Point at, Point target, int near) {
        return Math.abs(at.x() - target.x()) <= near + 1 && Math.abs(at.z() - target.z()) <= near + 1 && Math.abs(at.y() - target.y()) <= 2;
    }

    private void log(String line) {
        try {
            log.accept(line);
        } catch (RuntimeException ignored) {
            // a log line must never end a journey
        }
    }
}
