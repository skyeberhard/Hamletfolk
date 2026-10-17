package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * R1.35: what stands in a village's way, in plain words, for the dashboard and the console: no free bed, food under what a newcomer needs,
 * a material a building is waiting on, idle adults and no workplace, a project nobody is building. Reads the village; changes nothing.
 */
public final class VillageBlockers {
    private VillageBlockers() {
    }

    /** The blockers, most important first; empty for a village with nothing in its way. */
    public static List<String> of(Settlement s, long day, int treasuryLimit) {
        List<String> out = new ArrayList<>();
        int population = s.population();
        if (population == 0) {
            out.add(s.isAbandoned() ? "abandoned" : "nobody lives here");
            return out;
        }
        long food = s.ledger().get(ResourceType.FOOD);
        if (s.hasCondition("famine")) {
            out.add("famine: the food ran out (" + food + " in store)");
        } else if (SettlementSimulator.foodBelowAim(s)) {
            out.add("food " + food + " of the " + (long) population * SettlementSimulator.foodAim(s) + " it aims for"
                    + (s.freeBeds() > 0 ? " (a bed is free for a newcomer)" : ""));
        }
        if (s.housing().counted() && s.freeBeds() == 0) {
            out.add("no free bed: " + population + " people, " + s.housingCapacity() + " beds, so no newcomers or births");
        }
        if (SettlementSimulator.inDanger(s, day)) {
            out.add("in danger: no newcomers come");
        }
        for (ResourceType type : new ResourceType[] {ResourceType.WOOD, ResourceType.STONE, ResourceType.METAL, ResourceType.TOOLS}) {
            long have = s.ledger().get(type);
            long wanted = (long) population * SettlementSimulator.STOCK_WANTED_PER_HEAD;
            if (have < wanted) {
                out.add(type.name().toLowerCase(Locale.ROOT) + " " + have + " of the " + wanted + " wanted");
            }
        }
        long idle = s.residents().stream().filter(r -> r.adult() && r.occupation() == Occupation.UNEMPLOYED).count();
        if (idle > 0) {
            out.add(idle + " idle " + (idle == 1 ? "adult" : "adults") + " (nothing short, or no free workplace)");
        }
        s.openProject().ifPresent(p -> {
            String what = p.type().label().toLowerCase(Locale.ROOT);
            if (p.status() == ConstructionProject.Status.QUEUED) {
                out.add("the " + what + " has waited " + (day - p.queuedDay()) + " days for a builder");
            } else if (p.waitingFor() != null) {
                out.add("the " + what + " is waiting for " + p.waitingFor().name().toLowerCase(Locale.ROOT));
            }
        });
        if (s.openProject().isEmpty() && s.plan() != null) {
            for (Planner.Directive d : Planner.directives(s, day, treasuryLimit)) {
                if (d.kind() != Planner.Kind.BUILD) {
                    continue;
                }
                BuildingType.fromTarget(d.target()).ifPresent(type -> {
                    if (!type.isWorks() && !type.isPart() && type != BuildingType.SQUARE && s.plan().nextLot(type, l -> !PlanOverrides.isLocked(s, l)).isEmpty()) {
                        out.add("wants a " + type.label().toLowerCase(Locale.ROOT) + " but has no lot for one");
                    }
                });
            }
        }
        if (s.plan() != null) { // R8.23: what an admin's rules are holding back
            for (Planner.Directive d : Planner.ownDirectives(s, day, treasuryLimit)) {
                BuildingType.fromTarget(d.target()).filter(t -> d.kind() == Planner.Kind.BUILD && PlanOverrides.isForbidden(s, t)).ifPresent(
                        t -> out.add("wants a " + t.label().toLowerCase(Locale.ROOT) + " but an admin forbade it"));
            }
            PlanOverrides.buildNext(s, day).ifPresent(t -> {
                if (s.openProject().isEmpty()) {
                    out.add("an admin ordered a " + t.label().toLowerCase(Locale.ROOT) + " next: waiting for people, a lot or materials");
                }
            });
        }
        for (ResourceType type : Construction.lacking(s, day)) {
            out.add("a building it wants is waiting on " + type.name().toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
