package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * R4.53: what the village's notice board says: a book on a lectern by the bell, in plain words a visitor can use. A cover (the day and who
 * lives there), what the village is asking for and what it pays, what its builders are doing and what it would build next, and the latest
 * news from its history. Pure and read-only, worked out from the village's saved state each time; the Paper layer puts it in the book.
 * Pages are kept short (a book page holds about fourteen lines of nineteen letters).
 */
public final class NoticeBoard {
    /** The most characters on a page (the game's page shows about 14 lines of 19). */
    public static final int PAGE_CHARS = 250;
    /** The most news items shown. */
    public static final int NEWS = 7;
    /** The most requests listed (the rest are counted). */
    static final int MAX_REQUESTS = 3;

    private NoticeBoard() {
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private static String page(String text) {
        return text.length() <= PAGE_CHARS ? text : text.substring(0, PAGE_CHARS - 1) + "…";
    }

    /** The book's pages for a village, at its latest day. */
    public static List<String> pages(Settlement s) {
        long day = s.lastSimulatedDay();
        List<String> pages = new ArrayList<>();

        StringBuilder cover = new StringBuilder(s.name()).append("\nNotice Board\n\nDay ").append(day).append('\n')
                .append(s.population()).append(s.population() == 1 ? " person, " : " people, ").append(s.housingCapacity()).append(" beds (").append(s.freeBeds()).append(" free)");
        if (s.population() > 0) {
            cover.append("\n\n").append(cap(VillageCharacter.describe(s)));
        }
        pages.add(page(cover.toString()));

        // what it asks for
        StringBuilder wanted = new StringBuilder("WANTED\n");
        List<Request> open = s.requests().stream().filter(r -> r.remaining() > 0).toList();
        if (open.isEmpty()) {
            wanted.append("\nNothing, for now. The stores will do.");
        }
        if (!open.isEmpty()) {
            wanted.append("\nDrop it at the bell: /settlement donate\n");
        }
        for (Request r : open.stream().limit(MAX_REQUESTS).toList()) {
            wanted.append('\n').append(r.remaining()).append(' ').append(r.type().name().toLowerCase(Locale.ROOT));
            if (r.filled() > 0) {
                wanted.append(" (").append(r.filled()).append(" of ").append(r.wanted()).append(" given)");
            }
            wanted.append(r.thanksOnly() ? "\n  thanks only" : "\n  " + r.reward() + " emeralds");
        }
        if (open.size() > MAX_REQUESTS) {
            wanted.append("\n\n...and ").append(open.size() - MAX_REQUESTS).append(" more");
        }
        pages.add(page(wanted.toString()));

        // what it builds
        StringBuilder work = new StringBuilder("BUILDING\n");
        s.openProject().ifPresentOrElse(p -> work.append('\n').append("The builders are ").append(p.status() == ConstructionProject.Status.QUEUED ? "about to raise" : "raising")
                .append(" a ").append(p.type().label().toLowerCase(Locale.ROOT)).append('.'), () -> work.append("\nNothing under way."));
        List<Planner.Directive> wants = Planner.directives(s, day, SettlementSimulator.DEFAULT_TREASURY_BASE).stream()
                .filter(d -> d.kind() == Planner.Kind.BUILD).limit(2).toList();
        String underWay = s.openProject().map(p -> p.type().name().toLowerCase(Locale.ROOT)).orElse("");
        for (Planner.Directive d : wants) {
            if (!d.target().equalsIgnoreCase(underWay)) { // (not the one the builders are already raising)
                work.append("\n\nNext: a ").append(d.target().replace('_', ' ')).append(".");
            }
        }
        pages.add(page(work.toString()));

        // people of note (R4.60)
        List<String> masters = Masterpieces.living(s);
        if (!masters.isEmpty()) {
            StringBuilder note = new StringBuilder("PEOPLE OF NOTE\n");
            for (String m : masters.stream().limit(3).toList()) {
                note.append('\n').append(shorten(m, 70)).append('\n');
            }
            pages.add(page(note.toString()));
        }

        // the news
        StringBuilder news = new StringBuilder("NEWS\n");
        List<HistoryEvent> events = s.history();
        int shown = 0;
        for (int i = events.size() - 1; i >= 0 && shown < NEWS; i--) {
            HistoryEvent e = events.get(i);
            if (e.kind() == HistoryEvent.Kind.DONATION || (e.kind() == HistoryEvent.Kind.SHORTAGE && shown > 2)) {
                continue;
            }
            String item = "\nDay " + e.day() + ": " + shorten(e.text(), 48);
            if (news.length() + item.length() > PAGE_CHARS) {
                break; // whole items only: one that does not fit is left for the next look
            }
            news.append(item);
            shown++;
        }
        if (shown == 0) {
            news.append("\nQuiet times.");
        }
        pages.add(page(news.toString()));
        return pages;
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
