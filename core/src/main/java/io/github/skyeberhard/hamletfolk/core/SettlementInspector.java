package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** R1.4: a plain-text dump of a settlement's state, for admins and the server console. */
public final class SettlementInspector {
    private static final int RECENT_EVENTS = 8;

    private SettlementInspector() {
    }

    public static List<String> report(Settlement s) {
        List<String> lines = new ArrayList<>();
        long today = s.lastSimulatedDay();
        lines.add(s.name() + " [" + s.id() + "]" + (s.isAbandoned() ? " (abandoned)" : ""));
        lines.add("Where: " + s.world() + " at " + s.centerX() + ", " + s.centerZ());
        lines.add("Days: founded " + s.foundedDay() + ", simulated through " + today);

        long children = s.residents().stream().filter(r -> !r.adult()).count();
        Map<Occupation, Integer> jobs = new EnumMap<>(Occupation.class);
        for (Resident r : s.residents()) {
            if (r.adult()) {
                jobs.merge(r.occupation(), 1, Integer::sum);
            }
        }
        StringBuilder jobText = new StringBuilder();
        jobs.forEach((job, n) -> jobText.append(jobText.isEmpty() ? "" : ", ").append(n).append(' ')
                .append(job.title()));
        lines.add("Population: " + s.population() + " (" + children + " children, " + s.turnedCount()
                + " turned) " + jobText);

        StringBuilder stock = new StringBuilder();
        for (ResourceType type : ResourceType.values()) {
            stock.append(stock.isEmpty() ? "" : ", ").append(s.ledger().get(type)).append(' ')
                    .append(type.name().toLowerCase(Locale.ROOT));
        }
        lines.add("Stores: " + stock + "; treasury " + s.ledger().treasury());

        StringBuilder flow = new StringBuilder();
        for (ResourceType type : ResourceType.values()) {
            int made = s.flow().produced(type, today);
            int used = s.flow().consumed(type, today);
            if (made != 0 || used != 0) {
                flow.append(flow.isEmpty() ? "" : ", ").append(type.name().toLowerCase(Locale.ROOT))
                        .append(" +").append(made).append(" -").append(used);
            }
        }
        lines.add("Flow, last " + ResourceFlow.WINDOW_DAYS + " days: " + (flow.isEmpty() ? "none" : flow));

        StringBuilder requests = new StringBuilder();
        for (Request r : s.requests()) {
            requests.append(requests.isEmpty() ? "" : ", ").append(r.filled()).append('/').append(r.describe())
                    .append(" for ").append(r.reward()).append(" emeralds");
        }
        lines.add("Requests: " + (requests.isEmpty() ? "none" : requests));

        StringBuilder buildings = new StringBuilder();
        for (BuildingType type : BuildingType.values()) {
            int count = s.buildingCount(type);
            if (count > 0) {
                buildings.append(buildings.isEmpty() ? "" : ", ").append(count).append(' ').append(type.label().toLowerCase(Locale.ROOT));
            }
        }
        lines.add("Buildings: " + (buildings.isEmpty() ? "none" : buildings));
        lines.add("Housing: " + s.housingCapacity() + " beds, population " + s.population() + " (" + s.freeBeds()
                + " free; beds counted in " + s.housing().chunkCount() + " chunks)");

        lines.add("Threat: " + Math.round(s.threat()));
        Map<String, Long> conditions = new TreeMap<>(s.conditions());
        lines.add("Conditions: " + (conditions.isEmpty() ? "none" : conditions.toString()));

        List<HistoryEvent> history = s.history();
        lines.add("History: " + history.size() + " events, most recent last");
        for (HistoryEvent e : history.subList(Math.max(0, history.size() - RECENT_EVENTS), history.size())) {
            lines.add("  day " + e.day() + " " + e.kind() + ": " + e.text());
        }
        return lines;
    }
}
