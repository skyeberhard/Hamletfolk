package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class HistoryFeedTest {
    private static HistoryEvent event(long day, String text) {
        return new HistoryEvent(day, HistoryEvent.Kind.MILESTONE, text);
    }

    @Test
    void theEventsAfterTheLastOneReadAreNew() {
        List<HistoryEvent> history = List.of(event(1, "a"), event(2, "b"), event(2, "c"), event(3, "d"));
        assertEquals(List.of(event(2, "c"), event(3, "d")), HistoryFeed.newSince(history, event(2, "b")));
        assertEquals(List.of(), HistoryFeed.newSince(history, event(3, "d")));
    }

    @Test
    void whenTheLastLineIsGoneTheEventsOfALaterDayAreNew() {
        List<HistoryEvent> history = List.of(event(5, "x"), event(6, "y"), event(7, "z"));
        assertEquals(List.of(event(6, "y"), event(7, "z")), HistoryFeed.newSince(history, event(5, "merged away")));
    }
}
