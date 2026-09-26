package io.github.skyeberhard.hamletfolk.core;

/** One line in a settlement's permanent record. */
public record HistoryEvent(long day, Kind kind, String text) {

    public enum Kind {
        FOUNDED,
        ARRIVAL,
        BIRTH,
        DEATH,
        RAID,
        FAMINE,
        RECOVERY,
        SHORTAGE,
        MILESTONE,
        DONATION
    }
}
