package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;

/** Finds what is new in a village's history since a line that was already read (for the console feed). */
public final class HistoryFeed {
    private HistoryFeed() {
    }

    /**
     * The events after {@code last}. If that line is no longer there (a donation line merged into the next, or a full history pushed it
     * out) the events of a later day than it are returned instead.
     */
    public static List<HistoryEvent> newSince(List<HistoryEvent> history, HistoryEvent last) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).equals(last)) {
                return new ArrayList<>(history.subList(i + 1, history.size()));
            }
        }
        List<HistoryEvent> out = new ArrayList<>();
        for (HistoryEvent event : history) {
            if (event.day() > last.day()) {
                out.add(event);
            }
        }
        return out;
    }
}
