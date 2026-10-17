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

    /**
     * A village in one line, for the server console: its day, people, stores, treasury, jobs, beds, what it is building and what
     * is wrong ("famine", "no tools", "under attack"). Meant to be read at a glance every few minutes.
     */
    public static String digest(Settlement s) {
        StringBuilder out = new StringBuilder(s.name()).append(" day ").append(s.lastSimulatedDay());
        if (s.isAbandoned() || s.population() == 0) {
            return out.append(s.isAbandoned() ? ": abandoned" : ": empty").toString();
        }
        long children = s.residents().stream().filter(r -> !r.adult()).count();
        out.append(": ").append(s.population()).append(" people");
        if (children > 0) {
            out.append(" (").append(children).append(" children)");
        }
        out.append(", food ").append(s.ledger().get(ResourceType.FOOD)).append(", wood ").append(s.ledger().get(ResourceType.WOOD))
                .append(", stone ").append(s.ledger().get(ResourceType.STONE)).append(", tools ").append(s.ledger().get(ResourceType.TOOLS))
                .append(", treasury ").append(s.ledger().treasury());
        Map<Occupation, Integer> jobs = new EnumMap<>(Occupation.class);
        for (Resident r : s.residents()) {
            if (r.adult()) {
                jobs.merge(r.occupation(), 1, Integer::sum);
            }
        }
        StringBuilder work = new StringBuilder();
        jobs.forEach((job, n) -> work.append(work.isEmpty() ? "" : ", ").append(n).append(' ').append(job.title()));
        out.append("; ").append(work).append("; beds ").append(s.housingCapacity());
        s.openProject().ifPresent(p -> out.append("; building a ").append(p.type().label().toLowerCase(Locale.ROOT)).append(" (")
                .append(p.status().name().toLowerCase(Locale.ROOT)).append(')'));
        List<String> troubles = new ArrayList<>();
        if (s.hasCondition("famine")) {
            troubles.add("famine");
        }
        if (s.hasCondition("shortage:tools")) {
            troubles.add("no tools" + ToolsWhy.tools(s).map(why -> " (" + why + ")").orElse("")); // R4.52: and why
        }
        if (s.hasCondition("shortage:metal")) {
            troubles.add("no metal" + ToolsWhy.metal(s).map(why -> " (" + why + ")").orElse(""));
        }
        if (s.threat() >= 35) {
            troubles.add("under threat");
        }
        long thanks = s.requests().stream().filter(Request::thanksOnly).count();
        if (!s.requests().isEmpty()) {
            troubles.add(s.requests().size() + " request" + (s.requests().size() == 1 ? "" : "s") + (thanks > 0 ? " (" + thanks + " unpaid)" : ""));
        }
        if (!troubles.isEmpty()) {
            out.append("; ").append(String.join(", ", troubles));
        }
        return out.toString();
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
        StringBuilder held = new StringBuilder(); // R3.16
        s.ledger().stock().forEach((c, n) -> held.append(held.isEmpty() ? "" : ", ").append(n).append(' ').append(c.label()));
        lines.add("Holding: " + (held.isEmpty() ? "nothing" : held));

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
                    .append(r.thanksOnly() ? " for thanks" : " for " + r.reward() + " emeralds");
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
