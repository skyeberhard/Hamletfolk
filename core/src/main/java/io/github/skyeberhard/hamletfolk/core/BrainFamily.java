package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;
import java.util.Optional;

/** R9.4: the families the brain module's behaviours belong to; each can be switched off as a whole. */
public enum BrainFamily {
    /** Guards that fight (R9.3). */
    GUARDS,
    /** Villagers walking to their workplace and working it (R4.17). */
    WORK,
    /** The shared pathing service (R9.5). */
    PATHING,
    /** Builders walking to a site and building it (R4.7, R4.8). */
    BUILDERS,
    /** Elders and stewards meeting people and walking the village (R6.4, R6.6); and the test behaviour, a villager noticing a player. */
    STEWARDS;

    /** e.g. "guards", as the config and commands call it. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<BrainFamily> fromKey(String key) {
        for (BrainFamily f : values()) {
            if (f.key().equalsIgnoreCase(key.trim())) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
