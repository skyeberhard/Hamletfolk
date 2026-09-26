package io.github.skyeberhard.societies.core;

/** A resident's current wellbeing, each 0-100 where higher is better. */
public final class Needs {
    private int food;
    private int safety;
    private int purpose;

    public Needs(int food, int safety, int purpose) {
        this.food = clamp(food);
        this.safety = clamp(safety);
        this.purpose = clamp(purpose);
    }

    public static Needs initial() {
        return new Needs(80, 80, 60);
    }

    static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public int food() {
        return food;
    }

    public int safety() {
        return safety;
    }

    public int purpose() {
        return purpose;
    }

    void adjustFood(int delta) {
        food = clamp(food + delta);
    }

    void setSafety(int value) {
        safety = clamp(value);
    }

    void adjustPurpose(int delta) {
        purpose = clamp(purpose + delta);
    }

    /** Overall mood: the average of all needs. */
    public int mood() {
        return (food + safety + purpose) / 3;
    }
}
