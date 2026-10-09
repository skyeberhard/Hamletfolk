package io.github.skyeberhard.hamletfolk.paper;

import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.entity.Villager;

/**
 * R9.1: what the plugin asks of the brain module. The module (in {@code brain/}, the only code that touches the server's
 * internals) implements this and is loaded by name only after the self-check passes, so nothing here may mention an
 * internal type. Every call is made on the main thread, between ticks, never from inside a villager's own brain tick.
 */
public interface BrainModule {
    /** The class the plugin loads, by name. */
    String IMPLEMENTATION = "io.github.skyeberhard.hamletfolk.brain.VillagerBrains";

    /** Adds the module's behaviours to a villager, or re-adds them if its brain has been rebuilt since. Does nothing if already there. */
    void attach(Villager villager);

    /** Takes them off a villager (stopping any that is running), leaving its brain exactly as vanilla built it. */
    void detach(Villager villager);

    /** Forgets a villager that has left the world (its brain goes with it). */
    void forget(UUID villager);

    /** Takes them off every villager they are on. */
    void detachAll();

    /** Whether the added behaviours may act. Off makes them stand aside at once, before they are taken off. */
    void setActive(boolean active);

    /** Called (on the main thread, from inside a brain tick) with the villager and the error when a behaviour throws. */
    void onFault(BiConsumer<UUID, Throwable> handler);

    /** How many villagers have the behaviours. */
    int attached();

    /** What the behaviours have cost so far, for {@code /settlement brain status}. */
    String cost();
}
