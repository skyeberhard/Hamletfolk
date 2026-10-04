package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;

/** R8.2: the natural resources a village site is scored on. */
public enum SiteResource {
    WATER, LUMBER, FARMLAND, LIVESTOCK, STONE, ORE;

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
