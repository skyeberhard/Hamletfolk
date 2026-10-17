package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * R1.48: what needs a human, across every village, as one list for the dashboard's Attention page: a village in famine, abandoned, under attack,
 * a project nobody is building, idle adults, no free bed, a building it wants and has no lot for. Each item says how serious it is and, where the
 * dashboard has an action that helps, which one (the page runs it through the same checked, confirmed, audited path as any other). Read-only.
 */
public final class Attention {
    /** Days a project may wait for a builder before it is worth a look. */
    static final int STUCK_DAYS = 6;
    /** Idle adults before they are worth a look. */
    static final int IDLE_ADULTS = 3;

    private Attention() {
    }

    /** One thing that needs a look. {@code severity} is 2 for something wrong, 1 for something worth a look. */
    public record Item(String villageId, String village, String world, int severity, String kind, String text, String action, Map<String, Object> params) {
    }

    /** The items for one village, most serious first. */
    public static List<Item> of(Settlement s, long day, int treasuryLimit) {
        List<Item> out = new ArrayList<>();
        String id = s.id().toString();
        if (s.population() == 0) {
            out.add(new Item(id, s.name(), s.world(), 2, "empty", s.isAbandoned() ? "Abandoned: nobody lives here." : "Nobody lives here.", "resettle", Map.of("travellers", 3)));
            return out;
        }
        if (s.hasCondition("famine")) {
            out.add(new Item(id, s.name(), s.world(), 2, "famine", "In famine: " + s.ledger().get(ResourceType.FOOD) + " food in store"
                    + famineDays(s, day) + ".", null, Map.of()));
        }
        if (SettlementSimulator.inDanger(s, day)) {
            long attacks = SettlementSimulator.recentIncidents(s, day);
            out.add(new Item(id, s.name(), s.world(), 2, "danger", "In danger: threat " + Math.round(s.threat())
                    + (attacks > 0 ? ", " + attacks + (attacks == 1 ? " attack" : " attacks") + " lately" : "") + "; no newcomers come.", null, Map.of()));
        }
        s.openProject().ifPresent(p -> {
            String what = p.type().label().toLowerCase(Locale.ROOT);
            if (p.status() == ConstructionProject.Status.QUEUED && day - p.queuedDay() >= STUCK_DAYS) {
                out.add(new Item(id, s.name(), s.world(), 1, "stuck", "The " + what + " has waited " + (day - p.queuedDay()) + " days for a builder.", "cancel-project", Map.of()));
            } else if (p.waitingFor() != null) {
                out.add(new Item(id, s.name(), s.world(), 1, "waiting", "The " + what + " is waiting for " + p.waitingFor().name().toLowerCase(Locale.ROOT) + ".", null, Map.of()));
            }
        });
        if (s.plan() == null) {
            out.add(new Item(id, s.name(), s.world(), 1, "noplan", "No plan yet: it is made once the ground round it has been loaded.", "replan", Map.of()));
        } else if (s.openProject().isEmpty()) {
            for (Planner.Directive d : Planner.directives(s, day, treasuryLimit)) {
                if (d.kind() != Planner.Kind.BUILD) {
                    continue;
                }
                var type = BuildingType.fromTarget(d.target());
                if (type.isPresent() && !type.get().isWorks() && !type.get().isPart() && type.get() != BuildingType.SQUARE
                        && s.plan().nextLot(type.get(), l -> !PlanOverrides.isLocked(s, l)).isEmpty()) {
                    out.add(new Item(id, s.name(), s.world(), 1, "nolot", "Wants a " + type.get().label().toLowerCase(Locale.ROOT) + " but has no lot for one.", "replan", Map.of()));
                    break;
                }
            }
        }
        if (s.housing().counted() && s.freeBeds() == 0 && !s.hasCondition("famine")) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("building", BuildingType.HOUSE.name());
            out.add(new Item(id, s.name(), s.world(), 1, "nobed", "No free bed: " + s.population() + " people, " + s.housingCapacity() + " beds, so no newcomers or births.",
                    PlanOverrides.isForbidden(s, BuildingType.HOUSE) || s.plan() == null || s.plan().nextLot(BuildingType.HOUSE, l -> !PlanOverrides.isLocked(s, l)).isEmpty()
                            ? null : "build-next", params));
        }
        long idle = s.residents().stream().filter(r -> r.adult() && r.occupation() == Occupation.UNEMPLOYED).count();
        if (idle >= IDLE_ADULTS) {
            out.add(new Item(id, s.name(), s.world(), 1, "idle", idle + " idle adults (nothing short, or no free workplace).", null, Map.of()));
        }
        if (s.ledger().get(ResourceType.TOOLS) == 0 && s.population() >= 4) {
            out.add(new Item(id, s.name(), s.world(), 1, "notools", "No tools" + ToolsWhy.tools(s).map(why -> ": " + why).orElse("") + ".", null, Map.of()));
        }
        out.sort(Comparator.comparingInt((Item i) -> -i.severity()));
        return out;
    }

    /** The items for every village, the most serious first, then by village name. */
    public static List<Item> of(Iterable<Settlement> villages, java.util.function.ToIntFunction<Settlement> treasuryLimit) {
        List<Item> all = new ArrayList<>();
        for (Settlement s : villages) {
            all.addAll(of(s, s.lastSimulatedDay(), treasuryLimit.applyAsInt(s)));
        }
        all.sort(Comparator.comparingInt((Item i) -> -i.severity()).thenComparing(Item::village).thenComparing(Item::kind));
        return all;
    }

    /** ", for N days" if the history says when the famine began. */
    private static String famineDays(Settlement s, long day) {
        List<HistoryEvent> events = s.history();
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i).kind() == HistoryEvent.Kind.FAMINE) {
                return ", for " + Math.max(0, day - events.get(i).day()) + " days";
            }
            if (events.get(i).kind() == HistoryEvent.Kind.RECOVERY) {
                return "";
            }
        }
        return "";
    }

    /** The items as plain data. */
    public static List<Map<String, Object>> asData(List<Item> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Item i : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("village", i.villageId());
            row.put("name", i.village());
            row.put("world", i.world());
            row.put("severity", i.severity());
            row.put("kind", i.kind());
            row.put("text", i.text());
            if (i.action() != null) {
                row.put("action", i.action());
                row.put("params", i.params());
            }
            out.add(row);
        }
        return out;
    }
}
