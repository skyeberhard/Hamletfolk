package io.github.skyeberhard.hamletfolk.paper;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.entity.Villager;

/**
 * R9.1: what the plugin asks of the brain module. The module (in {@code brain/}, the only code that touches the server's
 * internals) implements this and is loaded by name only after the self-check passes, so nothing here may mention an
 * internal type. Every call is made on the main thread, between ticks, never from inside a villager's own brain tick.
 * R9.4: behaviours are named (the names of {@link io.github.skyeberhard.hamletfolk.core.BrainBehaviours#ALL}); the plugin
 * decides which a villager has, the module puts exactly those on it.
 */
public interface BrainModule {
    /** The class the plugin loads, by name. */
    String IMPLEMENTATION = "io.github.skyeberhard.hamletfolk.brain.VillagerBrains";

    /** The behaviours the module can make, by name. */
    List<String> known();

    /**
     * Gives a villager exactly these behaviours: adds the missing ones, takes off the others (stopping any that is running),
     * and re-adds them all if its brain has been rebuilt since. An empty set leaves its brain exactly as vanilla built it.
     * Names the module does not know are skipped.
     */
    void attach(Villager villager, Set<String> behaviours);

    /** Takes all of them off a villager. */
    void detach(Villager villager);

    /** Forgets a villager that has left the world (its brain goes with it). */
    void forget(UUID villager);

    /** Takes them off every villager they are on. */
    void detachAll();

    /** R9.4: takes one behaviour off every villager it is on (it, or its family, was switched off, or it went over its budget). */
    void detachEverywhere(String behaviour);

    /** Whether the added behaviours may act. Off makes them stand aside at once, before they are taken off. */
    void setActive(boolean active);

    /**
     * R9.4: whether one behaviour may act. A behaviour that goes over its budget stands aside by itself; true lets it act again
     * and starts its figures afresh.
     */
    void setRunning(String behaviour, boolean running);

    /** R9.4: the most all of a behaviour's calls in one tick may take, in microseconds (0: no budget). */
    void setBudget(String behaviour, int micros);

    /**
     * R9.4: called (on the main thread, from inside a brain tick, so it must not change any brain) with the behaviour's name
     * and the figures when a behaviour goes over its budget. It has already stood aside.
     */
    void onOverBudget(BiConsumer<String, String> handler);

    /** Called (on the main thread, from inside a brain tick) with the villager and the error when a behaviour throws. */
    void onFault(BiConsumer<UUID, Throwable> handler);

    /** How many villagers have any of the behaviours. */
    int attached();

    /** R9.4: how many villagers have this behaviour. */
    int attached(String behaviour);

    /** R9.4: the villagers that have any of them. */
    Set<UUID> villagers();

    /** R9.4: whether this villager has any of them. */
    boolean has(UUID villager);

    /** What the behaviours have cost so far (R9.2): per call and per tick, all of them together. */
    io.github.skyeberhard.hamletfolk.core.BrainStats stats();

    /** R9.4: what one behaviour has cost (empty figures for a name the module does not know). */
    io.github.skyeberhard.hamletfolk.core.BrainStats stats(String behaviour);

    /** R9.2: a villager's brain as plain text. Never throws: a brain that cannot be read says so in {@code activity}. */
    Snapshot inspect(Villager villager);

    /** R9.2: whether the behaviours report each decision to the handler given to {@link #onDecision}. Off by default. */
    void setDebug(boolean on);

    /**
     * R9.2: called (inside a brain tick, so it must only log) with the villager and a line about what a behaviour decided; the
     * line starts with the behaviour's debug name (R9.4).
     */
    void onDecision(BiConsumer<UUID, String> handler);

    /** A villager's brain as plain text: its activity, what it remembers, what is running, and what the plugin added. */
    record Snapshot(String activity, java.util.List<String> memories, java.util.List<String> running, java.util.List<String> added) {
    }
}
