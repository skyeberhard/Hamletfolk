package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * R1.39: what the dashboard shows as it happens. The feed: every new line of every village's history, stamped with the real time it was
 * seen (a village's own day cannot be compared across villages: a warped one runs ahead), kept to the last {@link #MAX_EVENTS}. The
 * trends: a sample of each village's numbers now and then, kept to the last {@link #MAX_SAMPLES}, for the small graphs. In memory only;
 * a restart starts both again. Not thread safe: fed and read on the server's main thread.
 */
public final class DashboardFeed {
    public static final int MAX_EVENTS = 400;
    public static final int MAX_SAMPLES = 240;
    /** Lines of each village's history shown as "earlier" when the feed starts. */
    static final int SEED_PER_VILLAGE = 3;

    /** One line in the feed. {@code seq} grows by one each line, so a page asks only for what it has not seen. */
    public record Event(long seq, long at, UUID village, String villageName, long day, String kind, String text, boolean earlier) {
    }

    /** One sample of a village's numbers. */
    public record Sample(long at, int population, long food, long wood, long stone, long tools, long treasury, int beds) {
    }

    private final Deque<Event> events = new ArrayDeque<>();
    /** For each village, {@link Settlement#historyWritten()} when it was last read. */
    private final Map<UUID, Long> seen = new HashMap<>();
    private boolean started;
    private final Map<UUID, Deque<Sample>> samples = new HashMap<>();
    private long seq;

    /**
     * Reads each village's history for lines added since the last look, stamped {@code now}. The very first look shows the last few of
     * each village as "earlier"; a village first seen later (a new one) has all its lines shown as new. A line merged into an earlier one
     * (donations, the builders' weekly line) is not shown again.
     */
    public void take(Iterable<Settlement> settlements, long now) {
        for (Settlement s : settlements) {
            List<HistoryEvent> history = s.history();
            long written = s.historyWritten();
            Long before = seen.put(s.id(), written);
            if (before == null && !started) {
                for (HistoryEvent e : history.subList(Math.max(0, history.size() - SEED_PER_VILLAGE), history.size())) {
                    add(s, e, now, true);
                }
                continue;
            }
            long fresh = Math.min(written - (before == null ? 0 : before), history.size());
            for (HistoryEvent e : history.subList(history.size() - (int) Math.max(0, fresh), history.size())) {
                add(s, e, now, false);
            }
        }
        started = true;
    }

    private void add(Settlement s, HistoryEvent e, long now, boolean earlier) {
        events.addLast(new Event(++seq, now, s.id(), s.name(), e.day(), e.kind().name().toLowerCase(java.util.Locale.ROOT), e.text(), earlier));
        while (events.size() > MAX_EVENTS) {
            events.removeFirst();
        }
    }

    /** The lines after {@code since} (0 for all kept), oldest first. */
    public List<Event> since(long since) {
        List<Event> out = new ArrayList<>();
        for (Event e : events) {
            if (e.seq() > since) {
                out.add(e);
            }
        }
        return out;
    }

    /** The newest sequence number handed out (0 before any). */
    public long latest() {
        return seq;
    }

    /** Takes a sample of every village's numbers at {@code now}; forgets villages that are gone. */
    public void sample(java.util.Collection<Settlement> settlements, long now) {
        java.util.Set<UUID> present = new java.util.HashSet<>();
        for (Settlement s : settlements) {
            present.add(s.id());
            Deque<Sample> row = samples.computeIfAbsent(s.id(), k -> new ArrayDeque<>());
            row.addLast(new Sample(now, s.population(), s.ledger().get(ResourceType.FOOD), s.ledger().get(ResourceType.WOOD),
                    s.ledger().get(ResourceType.STONE), s.ledger().get(ResourceType.TOOLS), s.ledger().treasury(), s.housingCapacity()));
            while (row.size() > MAX_SAMPLES) {
                row.removeFirst();
            }
        }
        samples.keySet().retainAll(present);
        seen.keySet().retainAll(present);
    }

    /** The samples kept for one village, oldest first. */
    public List<Sample> samples(UUID village) {
        Deque<Sample> row = samples.get(village);
        return row == null ? List.of() : new ArrayList<>(row);
    }

    /** Every village's samples as plain data, keyed by village id, for the page: people, food, wood, stone, tools, treasury, beds (one a minute, oldest first). */
    public Map<String, Object> trends() {
        Map<String, Object> out = new LinkedHashMap<>();
        samples.forEach((id, row) -> {
            List<List<Long>> points = new ArrayList<>();
            for (Sample p : row) {
                points.add(List.of((long) p.population(), p.food(), p.wood(), p.stone(), p.tools(), p.treasury(), (long) p.beds()));
            }
            out.put(id.toString(), points);
        });
        return out;
    }

    /** The feed as plain data for the page. */
    public static List<Map<String, Object>> asData(List<Event> events) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Event e : events) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seq", e.seq());
            row.put("at", e.at());
            row.put("village", e.village().toString());
            row.put("name", e.villageName());
            row.put("day", e.day());
            row.put("kind", e.kind());
            row.put("text", e.text());
            row.put("earlier", e.earlier());
            out.add(row);
        }
        return out;
    }
}
