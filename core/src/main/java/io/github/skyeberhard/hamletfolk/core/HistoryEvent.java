package io.github.skyeberhard.hamletfolk.core;

/**
 * One line in a settlement's permanent record. {@code count} is how many like events were
 * merged into it (R1.21) and {@code actor} names who they were about, or is null.
 */
public record HistoryEvent(long day, Kind kind, String text, int count, String actor) {

    public HistoryEvent(long day, Kind kind, String text) {
        this(day, kind, text, 1, null);
    }

    public enum Kind {
        FOUNDED(true),
        ARRIVAL(false),
        DEPARTURE(false),
        BIRTH(false),
        DEATH(true),
        CURE(true),
        RAID(true),
        FAMINE(true),
        RECOVERY(false),
        SHORTAGE(false),
        MILESTONE(false),
        DONATION(false),
        BUILDING(false),
        ABANDONED(true);

        private final boolean major;

        Kind(boolean major) {
            this.major = major;
        }

        /** Major events are kept over minor ones when the history is full. */
        public boolean isMajor() {
            return major;
        }
    }
}
