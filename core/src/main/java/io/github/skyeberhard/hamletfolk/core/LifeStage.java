package io.github.skyeberhard.hamletfolk.core;

/** Where a resident is in life (R4.15). */
public enum LifeStage {
    CHILD("child"),
    ADULT("adult"),
    ELDER("elder");

    private final String label;

    LifeStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
