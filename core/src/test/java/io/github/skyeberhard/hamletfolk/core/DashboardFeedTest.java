package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R1.39: the dashboard's live feed and trends. */
class DashboardFeedTest {
    private Settlement village(int n) {
        Settlement s = new Settlement(new UUID(170, n), "Feedham" + n, "world", 0, 0, 0);
        for (int i = 0; i < 5; i++) {
            s.record(i, HistoryEvent.Kind.MILESTONE, "old " + i);
        }
        return s;
    }

    @Test
    void theFirstLookSeedsAFewEarlierLinesThenOnlyNewOnesComeIn() {
        DashboardFeed feed = new DashboardFeed();
        Settlement s = village(1);
        feed.take(List.of(s), 1000);
        List<DashboardFeed.Event> first = feed.since(0);
        assertEquals(DashboardFeed.SEED_PER_VILLAGE, first.size());
        assertTrue(first.stream().allMatch(DashboardFeed.Event::earlier));
        long mark = feed.latest();
        feed.take(List.of(s), 2000);
        assertTrue(feed.since(mark).isEmpty(), "nothing new");
        s.record(9, HistoryEvent.Kind.BIRTH, "A child was born.");
        s.record(9, HistoryEvent.Kind.ARRIVAL, "Someone settled.");
        feed.take(List.of(s), 3000);
        List<DashboardFeed.Event> fresh = feed.since(mark);
        assertEquals(2, fresh.size());
        assertEquals("A child was born.", fresh.get(0).text());
        assertEquals(3000, fresh.get(0).at(), "stamped with when it was seen");
        assertTrue(fresh.stream().noneMatch(DashboardFeed.Event::earlier));
    }

    @Test
    void aLineEqualToTheLastIsStillShownAndAVillageFoundedLaterIsNew() {
        DashboardFeed feed = new DashboardFeed();
        Settlement s = village(6);
        feed.take(List.of(s), 0);
        long mark = feed.latest();
        s.record(9, HistoryEvent.Kind.MILESTONE, "The granaries were full to bursting.");
        feed.take(List.of(s), 1);
        s.record(9, HistoryEvent.Kind.MILESTONE, "The granaries were full to bursting.");
        feed.take(List.of(s), 2);
        assertEquals(2, feed.since(mark).size(), "the same words twice are two lines");
        Settlement later = village(7);
        feed.take(List.of(s, later), 3);
        List<DashboardFeed.Event> fresh = feed.since(mark + 2);
        assertEquals(5, fresh.size(), "every line of a village seen after the start is new");
        assertTrue(fresh.stream().noneMatch(DashboardFeed.Event::earlier));
    }

    @Test
    void theFeedKeepsOnlyTheLatestLines() {
        DashboardFeed feed = new DashboardFeed();
        Settlement s = village(2);
        feed.take(List.of(s), 0);
        for (int i = 0; i < DashboardFeed.MAX_EVENTS + 50; i++) {
            s.record(10 + i, HistoryEvent.Kind.MILESTONE, "line " + i);
            feed.take(List.of(s), i);
        }
        assertEquals(DashboardFeed.MAX_EVENTS, feed.since(0).size());
        assertEquals("line " + (DashboardFeed.MAX_EVENTS + 49), feed.since(0).get(DashboardFeed.MAX_EVENTS - 1).text());
    }

    @Test
    void samplesAreKeptPerVillageAndForgottenWhenTheVillageIsGone() {
        DashboardFeed feed = new DashboardFeed();
        Settlement a = village(3);
        Settlement b = village(4);
        a.ledger().add(ResourceType.FOOD, 50);
        for (int i = 0; i < DashboardFeed.MAX_SAMPLES + 10; i++) {
            feed.sample(List.of(a, b), i);
        }
        assertEquals(DashboardFeed.MAX_SAMPLES, feed.samples(a.id()).size());
        assertEquals(50, feed.samples(a.id()).get(0).food());
        feed.sample(List.of(a), 999);
        assertTrue(feed.samples(b.id()).isEmpty());
        assertEquals(1, feed.trends().size());
    }

    @Test
    void theDataForThePageIsPlain() {
        DashboardFeed feed = new DashboardFeed();
        feed.take(List.of(village(5)), 7);
        var rows = DashboardFeed.asData(feed.since(0));
        assertEquals("Feedham5", rows.get(0).get("name"));
        assertEquals(7L, rows.get(0).get("at"));
    }
}
