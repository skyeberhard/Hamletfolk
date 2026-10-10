package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * R9.4: every behaviour the brain module can add, with its family, its debug name and its time budget, and the switches that
 * decide whether it runs: the module's master switch (R9.1, kept by {@link BrainSwitch}), then its family, then itself, and
 * not if it has gone over its budget. Pure: the Paper layer reads the config into it and asks it which behaviours a villager
 * near a player should have. Also holds the rule for "near a player" ({@link #keepWatching}).
 */
public final class BrainBehaviours {
    /** One behaviour: a short name (unique), its family, and how many microseconds all its calls in one tick may take. */
    public record Spec(String name, BrainFamily family, int budgetMicros) {
        /** e.g. "hamletfolk:stewards/attention", what inspect and the debug log call it. */
        public String debugName() {
            return "hamletfolk:" + family.key() + "/" + name;
        }
    }

    /** Every behaviour the module has. Keep in step with the module's makers ({@code VillagerBrains.MAKERS}). */
    public static final List<Spec> ALL = List.of(
            // the test behaviour (R9.1): a villager stops and looks at a player within three blocks. About 2.5 microseconds a call
            // on the test server, so 500 (1% of a tick) is some two hundred villagers near players at once.
            new Spec("attention", BrainFamily.STEWARDS, 500));

    /** Default distance (blocks) from a player within which villagers get their behaviours. */
    public static final int DEFAULT_WATCH = 48;
    /** How much further a villager that has them may go before they are taken off again, so a player at the edge does not flicker them on and off. */
    public static final int WATCH_MARGIN = 16;

    private final Map<String, Spec> specs = new LinkedHashMap<>();
    private final Map<BrainFamily, Boolean> families = new EnumMap<>(BrainFamily.class);
    private final Map<String, Boolean> switched = new LinkedHashMap<>();
    private final Map<String, Integer> budgets = new LinkedHashMap<>();
    private final Map<String, String> overBudget = new LinkedHashMap<>();
    private int watch = DEFAULT_WATCH;

    public BrainBehaviours(List<Spec> all) {
        for (Spec s : all) {
            if (specs.put(s.name(), s) != null) {
                throw new IllegalArgumentException("two behaviours called " + s.name());
            }
            switched.put(s.name(), true);
            budgets.put(s.name(), s.budgetMicros());
        }
        for (BrainFamily f : BrainFamily.values()) {
            families.put(f, true);
        }
    }

    public List<Spec> specs() {
        return List.copyOf(specs.values());
    }

    public Optional<Spec> spec(String name) {
        return Optional.ofNullable(specs.get(name));
    }

    /** The behaviours of a family, in the order they were listed. */
    public List<Spec> members(BrainFamily family) {
        return specs.values().stream().filter(s -> s.family() == family).toList();
    }

    // ----- switches -----

    public boolean familyOn(BrainFamily family) {
        return families.get(family);
    }

    /** Switches a family; switching it on also lets its members that went over budget try again. Returns its members. */
    public List<Spec> setFamily(BrainFamily family, boolean on) {
        families.put(family, on);
        List<Spec> members = members(family);
        if (on) {
            members.forEach(s -> overBudget.remove(s.name()));
        }
        return members;
    }

    public boolean switchedOn(String name) {
        return switched.getOrDefault(name, false);
    }

    /** Switches one behaviour; switching it on also lets it try again if it went over budget. False if there is no such behaviour. */
    public boolean setBehaviour(String name, boolean on) {
        if (!specs.containsKey(name)) {
            return false;
        }
        switched.put(name, on);
        if (on) {
            overBudget.remove(name);
        }
        return true;
    }

    /** True if the behaviour runs (given the master switch is on): its family and itself on, and not over its budget. */
    public boolean runs(String name) {
        Spec s = specs.get(name);
        return s != null && familyOn(s.family()) && switchedOn(name) && !overBudget.containsKey(name);
    }

    /** The behaviours that run, in order: what a villager near a player gets. */
    public Set<String> running() {
        Set<String> out = new LinkedHashSet<>();
        for (Spec s : specs.values()) {
            if (runs(s.name())) {
                out.add(s.name());
            }
        }
        return out;
    }

    /** Why a behaviour does not run ("its family is off", "switched off", "over its budget: ..."), or empty if it runs. */
    public String whyNot(String name) {
        Spec s = specs.get(name);
        if (s == null) {
            return "there is no such behaviour";
        }
        if (!familyOn(s.family())) {
            return "the " + s.family().key() + " family is off";
        }
        if (!switchedOn(name)) {
            return "switched off";
        }
        return overBudget.containsKey(name) ? "over its budget: " + overBudget.get(name) : "";
    }

    // ----- budgets -----

    public int budgetMicros(String name) {
        return budgets.getOrDefault(name, 0);
    }

    public void setBudgetMicros(String name, int micros) {
        if (specs.containsKey(name)) {
            budgets.put(name, Math.max(0, micros));
        }
    }

    /**
     * A behaviour went over its budget. The first time (until it is switched on again) returns the line to log; it no longer
     * runs, so the caller takes it off every villager. Any later report returns nothing.
     */
    public Optional<String> overBudget(String name, String figures) {
        Spec s = specs.get(name);
        if (s == null || overBudget.containsKey(name)) {
            return Optional.empty();
        }
        overBudget.put(name, figures);
        return Optional.of("Brain behaviour " + s.debugName() + " switched off: over its budget of " + budgetMicros(name)
                + " microseconds a tick (" + figures + "). The other behaviours carry on. /settlement brain behaviour " + name
                + " on tries it again.");
    }

    // ----- near a player -----

    public int watchDistance() {
        return watch;
    }

    public void setWatchDistance(int blocks) {
        watch = Math.max(1, blocks);
    }

    /**
     * Whether a villager should have its behaviours: one without them gets them within {@link #watchDistance()} blocks of a
     * player, one with them keeps them until it is {@link #WATCH_MARGIN} further than that. A villager no player is near (a
     * distance of {@code Double.POSITIVE_INFINITY}, or another world) stays simulation-only.
     */
    public boolean keepWatching(boolean hasThem, double distanceToNearestPlayer) {
        double limit = hasThem ? watch + WATCH_MARGIN : watch;
        return distanceToNearestPlayer <= limit;
    }

    /** One line per behaviour, for status and the report: name, family, state and why, budget. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Spec s : specs.values()) {
            String why = whyNot(s.name());
            out.add(s.debugName() + ": " + (why.isEmpty() ? "on" : "off (" + why + ")") + ", budget " + budgetMicros(s.name())
                    + " microseconds a tick");
        }
        return out;
    }
}
