package io.github.skyeberhard.hamletfolk.core;

import java.util.Locale;

/** R4.30: the things a villager can have something to say about. Each has a file of lines, one set per tone. */
public enum Situation {
    GREETING_STRANGER, GREETING_KNOWN, GREETING_FRIEND, GREETING_HOSTILE, GREETING_WARY, GREETING_FRIENDLY, GREETING_HONOURED,
    FAREWELL, FAMINE, BLOCKED, TOOLS, LOSS, DANGER, THIN, PLENTY, REQUEST, CHILD, ELDER, MEMORY_RAID, MEMORY_GIFT, MEMORY,
    NEIGHBOUR, TALK,
    /** R4.31: any other remark, wrapped in the speaker's tone ({statement} is the sentence). */
    REMARK,
    /** R4.31: talk about their own trade, in the trade's words (see vocab_trade.txt); and for an expert or master. */
    WORK, WORK_EXPERT,
    /** R4.31: a remark on the weather and the time of day. */
    WEATHER;

    /** The file the lines are in, e.g. {@code greeting_stranger.txt}. */
    public String fileName() {
        return name().toLowerCase(Locale.ROOT) + ".txt";
    }
}
