package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * R1.45: what a village's Treasury, Stores and Quests tabs show, read from its ledger, requests and history. Read-only and derived:
 * nothing here is saved. The treasury keeps no list of what came in and went out, so its tab shows the balance, the room and what is
 * held aside for requests, with the history lines that moved money (gifts, a passing trader, requests filled or lapsed).
 */
public final class VillageBooks {
    /** The most history lines each tab quotes. */
    static final int LINES = 12;

    private VillageBooks() {
    }

    /** The three tabs' data, keyed {@code treasuryTab}, {@code storesTab} and {@code questsTab}. */
    public static Map<String, Object> asData(Settlement s, int treasuryLimit, long day) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("treasuryTab", treasury(s, treasuryLimit));
        out.put("storesTab", stores(s));
        out.put("questsTab", quests(s, day));
        return out;
    }

    static Map<String, Object> treasury(Settlement s, int limit) {
        Map<String, Object> out = new LinkedHashMap<>();
        int held = 0;
        for (Request r : s.requests()) {
            held += r.unpaid();
        }
        out.put("balance", s.ledger().treasury());
        out.put("limit", limit);
        out.put("room", Math.max(0, limit - s.ledger().treasury()));
        out.put("heldForRequests", held);
        out.put("hasCounter", s.buildingCount(BuildingType.TREASURY) > 0);
        out.put("merchants", s.residents().stream().filter(r -> r.occupation() == Occupation.MERCHANT).count());
        out.put("lines", lines(s, e -> e.kind() == HistoryEvent.Kind.DONATION
                || (e.kind() == HistoryEvent.Kind.MILESTONE && (e.text().contains("trader") || e.text().contains("emeralds") || e.text().startsWith("The request")))));
        return out;
    }

    static Map<String, Object> stores(Settlement s) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> categories = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", type.name().toLowerCase(Locale.ROOT));
            row.put("units", s.ledger().get(type));
            row.put("room", SettlementSimulator.room(s, type));
            row.put("shownAt", StoreDisplay.site(s, type).map(b -> b.type().label()).orElse(""));
            List<Map<String, Object>> held = new ArrayList<>();
            for (Commodity c : Commodity.of(type)) {
                int units = s.ledger().get(c);
                if (units <= 0) {
                    continue;
                }
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("commodity", c.label());
                line.put("units", units);
                line.put("shown", StoreDisplay.itemsShown(c, units));
                line.put("item", StoreDisplay.material(c).orElse(""));
                held.add(line);
            }
            row.put("commodities", held);
            categories.add(row);
        }
        out.put("categories", categories);
        out.put("lines", lines(s, e -> e.kind() == HistoryEvent.Kind.THEFT));
        return out;
    }

    static Map<String, Object> quests(Settlement s, long day) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> open = new ArrayList<>();
        for (Request r : s.requests()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item", r.type().name().toLowerCase(Locale.ROOT));
            row.put("wanted", r.wanted());
            row.put("filled", r.filled());
            row.put("remaining", r.remaining());
            row.put("reward", r.reward());
            row.put("paid", r.paid());
            row.put("unpaid", r.unpaid());
            row.put("thanksOnly", r.thanksOnly());
            row.put("daysOpen", Math.max(0, day - r.postedDay()));
            open.add(row);
        }
        out.put("open", open);
        // who has been giving: the request keeps no list of donors, so the gifts and what became of past requests come from the history
        out.put("lines", lines(s, e -> e.kind() == HistoryEvent.Kind.DONATION
                || (e.kind() == HistoryEvent.Kind.MILESTONE && e.text().startsWith("The request"))));
        return out;
    }

    private static List<Map<String, Object>> lines(Settlement s, java.util.function.Predicate<HistoryEvent> wanted) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<HistoryEvent> events = s.history();
        for (int i = events.size() - 1; i >= 0 && out.size() < LINES; i--) {
            HistoryEvent e = events.get(i);
            if (wanted.test(e)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("day", e.day());
                row.put("kind", e.kind().name().toLowerCase(Locale.ROOT));
                row.put("text", e.text());
                out.add(row);
            }
        }
        return out;
    }
}
