package io.github.skyeberhard.hamletfolk.core;

import java.util.List;
import java.util.Optional;

/**
 * R9.1: the master switch of the brain module (the code that adds behaviours to villagers through the server's internals).
 * Pure: it only decides what the module should do next; the Paper layer does it. Four states:
 * <ul>
 * <li>OFF: nothing added; can be switched on.</li>
 * <li>ON: behaviours added to the village's villagers.</li>
 * <li>FAILED: an added behaviour threw; everything was taken off and the fault logged once. Can be switched on again.</li>
 * <li>UNAVAILABLE: the self-check found something missing in the running server, so it stays off for good (until restart).</li>
 * </ul>
 */
public final class BrainSwitch {
    public enum State {
        OFF, ON, FAILED, UNAVAILABLE
    }

    /** What the Paper layer must do after a change. */
    public enum Action {
        NONE, ATTACH_ALL, DETACH_ALL
    }

    private State state = State.OFF;
    private String reason = "";

    public State state() {
        return state;
    }

    /** Why it is failed or unavailable (empty otherwise). */
    public String reason() {
        return reason;
    }

    /** True only while behaviours may be added and may act. */
    public boolean on() {
        return state == State.ON;
    }

    /** Switch on (the config at startup, or {@code /settlement brain on}). A failed module may be tried again; an unavailable one may not. */
    public Action requestOn() {
        if (state == State.UNAVAILABLE || state == State.ON) {
            return Action.NONE;
        }
        state = State.ON;
        reason = "";
        return Action.ATTACH_ALL;
    }

    /** Switch off ({@code /settlement brain off}, or the plugin stopping): everything added is taken off. */
    public Action requestOff() {
        if (state == State.ON) {
            state = State.OFF;
            return Action.DETACH_ALL;
        }
        if (state == State.FAILED) {
            state = State.OFF; // (already taken off when it failed)
            reason = "";
        }
        return Action.NONE;
    }

    /**
     * The self-check found these missing (a class, field or method the module relies on). The module stays off until the
     * server restarts; if it was on, everything is taken off.
     */
    public Action selfCheckFailed(List<String> missing) {
        boolean wasOn = state == State.ON;
        state = State.UNAVAILABLE;
        reason = "the running server lacks " + String.join(", ", missing);
        return wasOn ? Action.DETACH_ALL : Action.NONE;
    }

    /**
     * An added behaviour threw. The first fault while on turns the module off (the caller takes everything off) and returns the
     * one line to log, naming the villager; any fault after that, until it is switched on again, returns nothing, so a broken
     * behaviour on many villagers is logged once.
     */
    public Optional<String> fault(String villager, String error) {
        if (state != State.ON) {
            return Optional.empty();
        }
        state = State.FAILED;
        reason = "a behaviour failed on " + villager + ": " + error;
        return Optional.of("Brain module switched off: " + reason + ". Villagers are back to vanilla. /settlement brain on tries again.");
    }
}
