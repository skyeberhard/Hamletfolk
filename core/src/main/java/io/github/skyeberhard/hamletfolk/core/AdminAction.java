package io.github.skyeberhard.hamletfolk.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * R1.36: the actions an admin may take from outside the game (the dashboard), as a closed list. Each is checked here, in core, before
 * anything runs: its name, the village it names, and every parameter, with the same limits as the {@code /settlement admin} command.
 * Nothing that is not on this list can be run, and no text is ever passed to the server console.
 */
public final class AdminAction {
    /** What an action does, what it needs and whether it must be confirmed (it cannot be undone, or changes a lot). */
    public enum Kind {
        SAVE("Save every village now", false, false),
        REPLAN("Forget the village's plan and lay it out again", true, true),
        WARP("Run the village ahead a number of days", true, true),
        FASTFORWARD("Run the village fast (2 to 100 times), or back to normal (1)", true, false),
        RESETTLE("Send travellers to a village that has lost its people", true, true),
        CANCEL_PROJECT("Give up the village's open building project", true, true),
        UNSTICK("Free walled-in villagers to the village centre", true, false),
        REGRADE("Have the builder mend the palisade", true, true),
        RENAME("Rename the village", true, true),
        JOB("Set a resident's work and pin them to it", true, false),
        UNPIN("Hand a pinned resident back to the village", true, false),
        // R8.23: plan overrides. All can be undone with the opposite action, so none asks first.
        LOCK_LOT("Keep a lot empty: no building is put on it", true, false),
        UNLOCK_LOT("Open a kept-empty lot to building again", true, false),
        RETYPE_LOT("Set an unbuilt lot aside for another kind of building", true, false),
        FORBID("Stop the village building a kind of building", true, false),
        ALLOW("Let the village build a kind of building again", true, false),
        BUILD_NEXT("Have the village build a kind of building before anything else", true, false),
        CLEAR_BUILD_NEXT("Withdraw the order to build something next", true, false);

        private final String label;
        private final boolean needsVillage;
        private final boolean confirm;

        Kind(String label, boolean needsVillage, boolean confirm) {
            this.label = label;
            this.needsVillage = needsVillage;
            this.confirm = confirm;
        }

        public String label() {
            return label;
        }

        /** True for the plan overrides (R8.23), which write their own lines in the village's history. */
        public boolean isPlanOverride() {
            return this == LOCK_LOT || this == UNLOCK_LOT || this == RETYPE_LOT || this == FORBID || this == ALLOW
                    || this == BUILD_NEXT || this == CLEAR_BUILD_NEXT;
        }

        public boolean needsVillage() {
            return needsVillage;
        }

        /** True if the page must ask before running it, and the request must say it was asked. */
        public boolean confirm() {
            return confirm;
        }

        /** The name used on the wire: lower case with dashes ({@code cancel-project}). */
        public String wireName() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        public static Optional<Kind> fromWire(String name) {
            for (Kind kind : values()) {
                if (kind.wireName().equals(name)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    /** Longest name a village may be renamed to. */
    public static final int MAX_NAME = 32;
    /** Fastest a village may run without being confirmed: faster costs the server a lot more. */
    public static final int CONFIRM_SPEED_ABOVE = 20;
    /** Most days a warp may run (the same as the command, which reads this). */
    public static final int MAX_WARP_DAYS = 60;

    /** A checked request: the kind, the village (null for SAVE), and the parameters that kind uses. */
    public record Request(Kind kind, UUID village, Integer number, String text, UUID resident, Occupation occupation,
            BuildingType building) {
    }

    /** Either a request that may run, or why not. */
    public record Checked(Request request, String error) {
        public boolean ok() {
            return request != null;
        }

        static Checked fail(String error) {
            return new Checked(null, error);
        }
    }

    private AdminAction() {
    }

    /**
     * Checks a request as it arrived (a JSON object read into a map): {@code action}, {@code village} (its id), {@code confirm}, and the
     * kind's own: {@code days}, {@code speed}, {@code travellers}, {@code name}, {@code resident}, {@code occupation}.
     */
    public static Checked check(Map<?, ?> body, SettlementRegistry registry) {
        if (body == null) {
            return Checked.fail("no request");
        }
        Optional<Kind> kind = Kind.fromWire(String.valueOf(body.get("action")));
        if (kind.isEmpty()) {
            return Checked.fail("unknown action");
        }
        if (kind.get().confirm() && !Boolean.TRUE.equals(body.get("confirm"))) {
            return Checked.fail(kind.get().label() + " must be confirmed");
        }
        UUID village = null;
        Settlement settlement = null;
        if (kind.get().needsVillage()) {
            village = uuid(body.get("village"));
            if (village == null) {
                return Checked.fail("no village given");
            }
            settlement = registry.get(village).orElse(null);
            if (settlement == null) {
                return Checked.fail("no such village");
            }
        }
        final UUID id = village;
        final Settlement home = settlement;
        return switch (kind.get()) {
            case SAVE, REPLAN, CANCEL_PROJECT, UNSTICK, REGRADE -> ok(kind.get(), id, null, null, null, null);
            case LOCK_LOT, UNLOCK_LOT, RETYPE_LOT -> {
                if (home.plan() == null) {
                    yield Checked.fail(home.name() + " has no plan yet");
                }
                Optional<Integer> lotId = number(body.get("lot"), 1, 100_000);
                if (lotId.isEmpty() || home.plan().lots().stream().noneMatch(l -> l.id() == lotId.get())) {
                    yield Checked.fail("no such lot in " + home.name());
                }
                if (kind.get() != Kind.RETYPE_LOT) {
                    yield new Checked(new Request(kind.get(), id, lotId.get(), null, null, null, null), null);
                }
                Optional<BuildingType> building = buildingKind(body.get("building"), PlanOverrides::lotKind);
                yield building.isEmpty() ? Checked.fail("no such kind of building for a lot")
                        : new Checked(new Request(kind.get(), id, lotId.get(), null, null, null, building.get()), null);
            }
            case FORBID, ALLOW, BUILD_NEXT, CLEAR_BUILD_NEXT -> {
                Optional<BuildingType> building = buildingKind(body.get("building"),
                        kind.get() == Kind.BUILD_NEXT ? PlanOverrides::orderable : PlanOverrides::nameable);
                yield building.isEmpty() ? Checked.fail("no such kind of building")
                        : new Checked(new Request(kind.get(), id, null, null, null, null, building.get()), null);
            }
            case WARP -> number(body.get("days"), 1, MAX_WARP_DAYS)
                    .map(n -> ok(kind.get(), id, n, null, null, null))
                    .orElse(Checked.fail("days must be a whole number from 1 to " + MAX_WARP_DAYS));
            case FASTFORWARD -> number(body.get("speed"), 1, FastForward.MAX_SPEED)
                    .map(n -> n > CONFIRM_SPEED_ABOVE && !Boolean.TRUE.equals(body.get("confirm"))
                            ? Checked.fail("a speed above " + CONFIRM_SPEED_ABOVE + " must be confirmed")
                            : ok(kind.get(), id, n, null, null, null))
                    .orElse(Checked.fail("speed must be 1 (normal) or 2 to " + FastForward.MAX_SPEED));
            case RESETTLE -> number(body.get("travellers"), 1, Resettlement.MAX_SETTLERS)
                    .map(n -> ok(kind.get(), id, n, null, null, null))
                    .orElse(Checked.fail("travellers must be 1 to " + Resettlement.MAX_SETTLERS));
            case RENAME -> {
                String name = body.get("name") instanceof String s ? s.strip() : "";
                yield name.isEmpty() || name.length() > MAX_NAME || !name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == ' '
                        || c == '-' || c == '\'') ? Checked.fail("a name is 1 to " + MAX_NAME + " letters, digits, spaces, dashes or apostrophes")
                        : ok(kind.get(), id, null, name, null, null);
            }
            case JOB, UNPIN -> {
                UUID resident = uuid(body.get("resident"));
                if (resident == null || home.resident(resident).isEmpty()) {
                    yield Checked.fail("no such resident in " + home.name());
                }
                if (kind.get() == Kind.UNPIN) {
                    yield ok(kind.get(), id, null, null, resident, null);
                }
                if (!home.resident(resident).orElseThrow().adult()) {
                    yield Checked.fail("a child has no trade");
                }
                Occupation job;
                try {
                    job = Occupation.valueOf(String.valueOf(body.get("occupation")).toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    yield Checked.fail("no such occupation");
                }
                yield ok(kind.get(), id, null, null, resident, job);
            }
        };
    }

    private static Checked ok(Kind kind, UUID id, Integer number, String text, UUID resident, Occupation occupation) {
        return new Checked(new Request(kind, id, number, text, resident, occupation, null), null);
    }

    /** A kind of building named by its enum name (any case, dashes or underscores), if the rule accepts it. */
    private static Optional<BuildingType> buildingKind(Object value, java.util.function.Predicate<BuildingType> accepts) {
        if (!(value instanceof String s) || s.isEmpty() || s.length() > 24) {
            return Optional.empty();
        }
        try {
            BuildingType type = BuildingType.valueOf(s.toUpperCase(Locale.ROOT).replace('-', '_'));
            return accepts.test(type) ? Optional.of(type) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** R8.23: what the page offers to name in an override: every kind with its label and whether a lot may be made for it. */
    public static List<Map<String, Object>> buildingKinds() {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (BuildingType type : BuildingType.values()) {
            if (PlanOverrides.nameable(type)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", type.name());
                row.put("label", type.label());
                row.put("lot", PlanOverrides.lotKind(type));
                row.put("next", PlanOverrides.orderable(type));
                out.add(row);
            }
        }
        return out;
    }

    /** R8.23: runs a plan override that has been checked; returns what to tell the admin. */
    public static PlanOverrides.Result applyOverride(Settlement settlement, Request request, long day, String actor) {
        return switch (request.kind()) {
            case LOCK_LOT -> PlanOverrides.lock(settlement, request.number(), true, day, actor);
            case UNLOCK_LOT -> PlanOverrides.lock(settlement, request.number(), false, day, actor);
            case RETYPE_LOT -> PlanOverrides.retype(settlement, request.number(), request.building(), day, actor);
            case FORBID -> PlanOverrides.forbid(settlement, request.building(), true, day, actor);
            case ALLOW -> PlanOverrides.forbid(settlement, request.building(), false, day, actor);
            case BUILD_NEXT -> PlanOverrides.buildNext(settlement, request.building(), true, day, actor);
            case CLEAR_BUILD_NEXT -> PlanOverrides.buildNext(settlement, request.building(), false, day, actor);
            default -> new PlanOverrides.Result(false, "not a plan override");
        };
    }

    private static UUID uuid(Object value) {
        if (!(value instanceof String s)) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A whole number in range, whether it came as a JSON number or a string. */
    private static Optional<Integer> number(Object value, int min, int max) {
        long n;
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (d != Math.floor(d) || Double.isInfinite(d)) {
                return Optional.empty();
            }
            n = (long) d;
        } else if (value instanceof String s && !s.isEmpty() && s.length() <= 6 && s.chars().allMatch(Character::isDigit)) {
            n = Long.parseLong(s);
        } else {
            return Optional.empty();
        }
        return n < min || n > max ? Optional.empty() : Optional.of((int) n);
    }

    // ----- the work the command and the dashboard share -----

    /** R1.31: sets a resident's work and pins it, with a line in the history. Returns what to tell the admin. */
    public static String setJob(Settlement settlement, Resident resident, Occupation job, long day, String actor) {
        String was = resident.occupation().title();
        resident.setOccupation(job);
        settlement.setPinned(resident.id(), true, day);
        settlement.record(day, HistoryEvent.Kind.MILESTONE, resident.fullName() + " was set to work as a " + job.title() + " by " + actor + ".");
        return resident.fullName() + " of " + settlement.name() + " is now a " + job.title() + " (was a " + was + "), pinned until unpinned.";
    }

    /** R1.31: hands a resident back to the village. */
    public static String unpin(Settlement settlement, Resident resident, long day) {
        settlement.setPinned(resident.id(), false, day);
        return resident.fullName() + " is no longer pinned: the village decides their work again.";
    }

    // ----- what is offered -----

    /** The catalogue the page reads: every action, what it needs, and whether it asks first. */
    public static List<Map<String, Object>> catalogue() {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Kind kind : Kind.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("action", kind.wireName());
            row.put("label", kind.label());
            row.put("village", kind.needsVillage());
            row.put("confirm", kind.confirm());
            out.add(row);
        }
        return out;
    }

    /** One line for the audit log: when, who, what, on which village, and how it ended. Plain text, one line, no newlines from input. */
    public static String auditLine(java.time.Instant when, String actor, Request request, String villageName, boolean ok, String result) {
        StringBuilder line = new StringBuilder();
        line.append(when).append(" | ").append(clean(actor)).append(" | ").append(request.kind().wireName());
        if (villageName != null) {
            line.append(" | ").append(clean(villageName));
        }
        if (request.number() != null) {
            line.append(" | ").append(request.number());
        }
        if (request.text() != null) {
            line.append(" | \"").append(clean(request.text())).append('"');
        }
        if (request.resident() != null) {
            line.append(" | resident ").append(request.resident());
        }
        if (request.occupation() != null) {
            line.append(" | ").append(request.occupation().name().toLowerCase(Locale.ROOT));
        }
        if (request.building() != null) {
            line.append(" | ").append(request.building().name().toLowerCase(Locale.ROOT));
        }
        line.append(" | ").append(ok ? "ok" : "failed").append(" | ").append(clean(result));
        return line.toString();
    }

    /** One line for a request that was refused before it could run (a bad token, an unknown action, a bad parameter). */
    public static String rejectedLine(java.time.Instant when, String actor, String from, String reason) {
        return when + " | " + clean(actor) + " | refused | " + clean(from) + " | " + clean(reason);
    }

    private static String clean(String s) {
        // control characters (newlines, tabs), the Unicode line and paragraph separators, and the field separator
        return s == null ? "" : s.replaceAll("[\\p{Cntrl}\\u0085\\u2028\\u2029|]", " ");
    }
}
