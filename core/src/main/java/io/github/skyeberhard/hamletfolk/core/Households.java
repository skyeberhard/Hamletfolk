package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * R6.1: who lives together, worked out from the residents each time and never saved. A married couple and their children who are not yet
 * grown make a household, named for the wife's family (her children carry it); everyone else is a household of one (a child with no
 * parent left in the village is a household of one, to be looked after by whoever is told of it). Cheap enough to call for a report.
 */
public final class Households {
    private Households() {
    }

    /**
     * One household.
     *
     * @param name     e.g. "Mercer"
     * @param head     the older of the couple, or the one resident
     * @param spouse   the other of the couple, if any
     * @param children their children who are not grown
     */
    public record Household(String name, Resident head, Optional<Resident> spouse, List<Resident> children) {
        /** Everyone in it. */
        public List<Resident> members() {
            List<Resident> out = new ArrayList<>();
            out.add(head);
            spouse.ifPresent(out::add);
            out.addAll(children);
            return out;
        }

        /** e.g. "the Mercer household: Anna and Tom Mercer, with Lily and Sam". */
        public String describe() {
            StringBuilder text = new StringBuilder("the ").append(name).append(" household: ");
            if (spouse.isPresent()) {
                text.append(head.givenName()).append(" and ").append(spouse.get().givenName());
            } else {
                text.append(head.givenName());
            }
            if (!children.isEmpty()) {
                text.append(", with ");
                for (int i = 0; i < children.size(); i++) {
                    text.append(i > 0 ? (i == children.size() - 1 ? " and " : ", ") : "").append(children.get(i).givenName());
                }
            }
            return text.toString();
        }
    }

    /** All the households of a village, the couples and their children first, then the people on their own. */
    public static List<Household> of(Settlement settlement) {
        List<Resident> all = new ArrayList<>(settlement.residents());
        all.sort(Comparator.comparing(Resident::id)); // repeatable
        Set<UUID> placed = new HashSet<>();
        List<Household> out = new ArrayList<>();
        for (Resident r : all) {
            if (placed.contains(r.id()) || r.spouse() == null) {
                continue;
            }
            Optional<Resident> other = settlement.resident(r.spouse());
            if (other.isEmpty() || placed.contains(other.get().id())) {
                continue;
            }
            Resident head = r.bornDay() <= other.get().bornDay() ? r : other.get();
            Resident second = head == r ? other.get() : r;
            Resident wife = r.gender() == Gender.FEMALE ? r : other.get();
            List<Resident> children = new ArrayList<>();
            for (Resident child : all) {
                if (!child.adult() && !placed.contains(child.id()) && (head.id().equals(child.parentA()) || head.id().equals(child.parentB())
                        || second.id().equals(child.parentA()) || second.id().equals(child.parentB()))) {
                    children.add(child);
                }
            }
            placed.add(head.id());
            placed.add(second.id());
            children.forEach(c -> placed.add(c.id()));
            out.add(new Household(wife.familyName(), head, Optional.of(second), children));
        }
        List<Resident> adultsFirst = new ArrayList<>(all);
        adultsFirst.sort(Comparator.comparing((Resident x) -> !x.adult()).thenComparing(Resident::id));
        for (Resident r : adultsFirst) {
            if (placed.contains(r.id())) {
                continue;
            }
            List<Resident> children = new ArrayList<>();
            if (r.adult()) { // a widow or a single parent keeps their young children
                for (Resident child : all) {
                    if (!child.adult() && !placed.contains(child.id()) && (r.id().equals(child.parentA()) || r.id().equals(child.parentB()))) {
                        children.add(child);
                    }
                }
            }
            placed.add(r.id());
            children.forEach(c -> placed.add(c.id()));
            out.add(new Household(r.familyName(), r, Optional.empty(), children));
        }
        return out;
    }

    /** The household a resident belongs to, if they are in the village. */
    public static Optional<Household> of(Settlement settlement, UUID resident) {
        return of(settlement).stream().filter(h -> h.members().stream().anyMatch(m -> m.id().equals(resident))).findFirst();
    }
}
