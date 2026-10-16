package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * R1.35: the data the village dashboard shows, as plain maps and lists (the Paper layer turns them into JSON), so what a page shows can be
 * unit tested and read from the same code as the console. Reads a village; changes nothing.
 */
public final class Dashboard {
    /** History lines, planner decisions and projects a village page carries. */
    static final int HISTORY_LINES = 60;
    static final int DECISIONS = 12;
    static final int PROJECTS = 10;

    private Dashboard() {
    }

    /** How a village is doing at a glance: 0 well, 1 something in the way, 2 trouble (famine, danger, empty, no tools with a smithy). */
    static int health(Settlement s, List<String> blockers, long day) {
        if (s.population() == 0 || s.isAbandoned() || s.hasCondition("famine") || SettlementSimulator.inDanger(s, day)) {
            return 2;
        }
        return blockers.isEmpty() ? 0 : 1;
    }

    /** One village in a row: what the overview table shows. */
    public static Map<String, Object> summary(Settlement s, int treasuryLimit) {
        long day = s.lastSimulatedDay();
        List<String> blockers = VillageBlockers.of(s, day, treasuryLimit);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.id().toString());
        out.put("name", s.name());
        out.put("world", s.world());
        out.put("x", s.centerX());
        out.put("z", s.centerZ());
        out.put("day", day);
        out.put("population", s.population());
        out.put("children", s.residents().stream().filter(r -> !r.adult()).count());
        out.put("character", s.population() == 0 ? "" : VillageCharacter.describe(s));
        out.put("health", health(s, blockers, day));
        out.put("abandoned", s.isAbandoned());
        out.put("famine", s.hasCondition("famine"));
        out.put("threat", Math.round(s.threat()));
        Map<String, Object> stores = new LinkedHashMap<>();
        for (ResourceType type : ResourceType.values()) {
            stores.put(type.name().toLowerCase(Locale.ROOT), s.ledger().get(type));
        }
        out.put("stores", stores);
        out.put("treasury", s.ledger().treasury());
        out.put("treasuryLimit", treasuryLimit);
        out.put("beds", s.housingCapacity());
        out.put("freeBeds", s.freeBeds());
        EnumMap<Occupation, Integer> jobs = new EnumMap<>(Occupation.class);
        for (Resident r : s.residents()) {
            if (r.adult()) {
                jobs.merge(r.occupation(), 1, Integer::sum);
            }
        }
        Map<String, Object> work = new LinkedHashMap<>();
        jobs.forEach((job, n) -> work.put(job.title(), n));
        out.put("jobs", work);
        out.put("idle", jobs.getOrDefault(Occupation.UNEMPLOYED, 0));
        out.put("building", s.openProject().map(p -> p.type().label() + " (" + p.status().name().toLowerCase(Locale.ROOT) + ")").orElse(""));
        out.put("requests", s.requests().size());
        out.put("blockers", blockers);
        out.put("digest", SettlementInspector.digest(s));
        return out;
    }

    /** Everything a village's own page shows. */
    public static Map<String, Object> detail(Settlement s, int treasuryLimit) {
        return detail(s, treasuryLimit, summary(s, treasuryLimit));
    }

    /** As above, reusing a summary already made. */
    public static Map<String, Object> detail(Settlement s, int treasuryLimit, Map<String, Object> summary) {
        long day = s.lastSimulatedDay();
        Map<String, Object> out = new LinkedHashMap<>(summary);

        List<Map<String, Object>> residents = new ArrayList<>();
        for (Resident r : s.residents()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", r.fullName());
            row.put("gender", r.gender().name().toLowerCase(Locale.ROOT));
            row.put("adult", r.adult());
            row.put("age", r.age(day));
            row.put("stage", r.stage(day).name().toLowerCase(Locale.ROOT));
            row.put("job", r.occupation().title());
            row.put("level", r.adult() && r.occupation() != Occupation.UNEMPLOYED ? TradeLevel.name(r.level()) : "");
            row.put("resting", SettlementSimulator.isResting(s, r));
            row.put("pinned", s.isPinned(r.id()));
            row.put("wealth", Wealth.tier(r.wealth()).name().toLowerCase(Locale.ROOT));
            row.put("food", r.needs().food());
            row.put("safety", r.needs().safety());
            row.put("purpose", r.needs().purpose());
            residents.add(row);
        }
        out.put("residents", residents);

        List<Map<String, Object>> buildings = new ArrayList<>();
        for (Building b : s.buildings()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", b.type().label());
            row.put("x", b.x());
            row.put("y", b.y());
            row.put("z", b.z());
            row.put("by", b.registeredBy());
            row.put("tier", s.tierOf(b));
            buildings.add(row);
        }
        out.put("buildings", buildings);

        List<Map<String, Object>> projects = new ArrayList<>();
        List<ConstructionProject> all = s.projects();
        for (int i = all.size() - 1; i >= 0 && projects.size() < PROJECTS; i--) {
            ConstructionProject p = all.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", p.type().label());
            row.put("tier", p.tier());
            row.put("status", p.status().name().toLowerCase(Locale.ROOT));
            row.put("queued", p.queuedDay());
            row.put("finished", p.finishedDay());
            row.put("x", p.x());
            row.put("z", p.z());
            projects.add(row);
        }
        out.put("projects", projects);

        List<Map<String, Object>> requests = new ArrayList<>();
        for (Request r : s.requests()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item", r.type().name().toLowerCase(Locale.ROOT));
            row.put("wanted", r.wanted());
            row.put("filled", r.filled());
            row.put("reward", r.reward());
            row.put("thanksOnly", r.thanksOnly());
            requests.add(row);
        }
        out.put("requestList", requests);

        List<Map<String, Object>> history = new ArrayList<>();
        List<HistoryEvent> events = s.history();
        for (int i = Math.max(0, events.size() - HISTORY_LINES); i < events.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", events.get(i).day());
            row.put("kind", events.get(i).kind().name().toLowerCase(Locale.ROOT));
            row.put("text", events.get(i).text());
            history.add(row);
        }
        out.put("history", history);

        List<Map<String, Object>> decisions = new ArrayList<>();
        List<Planner.Decision> made = s.decisions();
        for (int i = Math.max(0, made.size() - DECISIONS); i < made.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", made.get(i).day());
            row.put("tier", made.get(i).tier().label());
            row.put("text", made.get(i).text());
            decisions.add(row);
        }
        out.put("decisions", decisions);

        List<Map<String, Object>> wants = new ArrayList<>();
        if (s.population() > 0) {
            for (Planner.Directive d : Planner.directives(s, day, treasuryLimit)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("tier", d.tier().label());
                row.put("kind", d.kind().name().toLowerCase(Locale.ROOT));
                row.put("target", d.target());
                row.put("reason", d.reason());
                wants.add(row);
            }
        }
        out.put("wants", wants);

        VillagePlan plan = s.plan();
        if (plan != null) {
            Map<String, Object> geometry = new LinkedHashMap<>();
            geometry.put("stage", plan.stage());
            geometry.put("dropped", plan.dropped());
            geometry.put("square", plan.square() == null ? null : rect(plan.square()));
            List<Map<String, Object>> roads = new ArrayList<>();
            for (VillagePlan.Road road : plan.roads()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("rect", rect(road.rect()));
                row.put("stage", road.stage());
                roads.add(row);
            }
            geometry.put("roads", roads);
            List<Map<String, Object>> lots = new ArrayList<>();
            for (VillagePlan.Lot lot : plan.lots()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("rect", rect(lot.rect()));
                row.put("type", lot.type().label());
                row.put("status", lot.status().name().toLowerCase(Locale.ROOT));
                lots.add(row);
            }
            geometry.put("lots", lots);
            out.put("plan", geometry);
        }
        return out;
    }

    private static List<Integer> rect(Rect r) {
        return List.of(r.x(), r.z(), r.width(), r.depth());
    }
}
