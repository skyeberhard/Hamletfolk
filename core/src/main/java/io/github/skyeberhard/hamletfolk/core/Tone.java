package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;
import java.util.Optional;

/** R4.30: the six ways a villager can sound. The name in lower case is what a dialogue file calls it. */
public enum Tone {
    WARM, GRUFF, ANXIOUS, PROUD, DRY, GENTLE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Tone> fromKey(String key) {
        for (Tone tone : values()) {
            if (tone.key().equals(key.trim().toLowerCase(Locale.ROOT))) {
                return Optional.of(tone);
            }
        }
        return Optional.empty();
    }
}
