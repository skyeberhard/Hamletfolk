package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;

/**
 * R3.3: something the village is short of and will pay emeralds for. The reward is taken out of the
 * treasury when the request is posted and held here, so the village can always honour it; what is
 * left when the request closes goes back to the treasury. At most one is open per resource.
 */
public final class Request {
    private final ResourceType type;
    private final int wanted;
    private int filled;
    private final int reward;
    private int paid;
    private final long postedDay;

    Request(ResourceType type, int wanted, int filled, int reward, int paid, long postedDay) {
        this.type = type;
        this.wanted = wanted;
        this.filled = filled;
        this.reward = reward;
        this.paid = paid;
        this.postedDay = postedDay;
    }

    public ResourceType type() {
        return type;
    }

    /** Units asked for in total. */
    public int wanted() {
        return wanted;
    }

    public int filled() {
        return filled;
    }

    public int remaining() {
        return wanted - filled;
    }

    /** Emeralds held for this request in total, paid out as it is filled. */
    public int reward() {
        return reward;
    }

    public int paid() {
        return paid;
    }

    /** Emeralds still held, not yet paid to anyone. */
    public int unpaid() {
        return reward - paid;
    }

    public long postedDay() {
        return postedDay;
    }

    /** e.g. "32 metal". */
    public String describe() {
        return wanted + " " + type.name().toLowerCase(Locale.ROOT);
    }

    /** Takes up to {@code units} toward the request and returns how many it accepted. */
    int fill(int units) {
        int taken = Math.min(Math.max(0, units), remaining());
        filled += taken;
        return taken;
    }

    /** The emeralds that would be owed if {@code extraUnits} more were delivered now, without delivering them. */
    int owedWith(int extraUnits) {
        int total = filled + Math.min(Math.max(0, extraUnits), remaining());
        int due = total == wanted ? reward : (int) ((long) reward * total / wanted);
        return due - paid;
    }

    /**
     * The emeralds now owed for what has been filled, marked as paid: the share of the reward
     * matching the share delivered, rounded down, with the last delivery taking the remainder.
     */
    int settle() {
        int due = filled == wanted ? reward : (int) ((long) reward * filled / wanted);
        int owed = due - paid;
        paid += owed;
        return owed;
    }
}
