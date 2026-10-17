package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * R6.1: which house each household lives in. Worked out from the registered houses and the households ({@link Households}), never saved by
 * the simulation: the Paper layer keeps who lives where on the house's own sign, and hands it back as {@code previous} so a household keeps
 * its house as the village changes. Only households with an adult in them have a house; a house holds one household, so a village with more
 * households than houses leaves the rest in the village's other housing (the beds the game counts, which are not tied to a registered house).
 */
public final class Homes {
    private Homes() {
    }

    /** A household and the house it lives in. */
    public record Home(Building house, Households.Household household) {
        /** The resident the house remembers the household by (the head). */
        public UUID head() {
            return household.head().id();
        }
    }

    /**
     * The homes of a village. {@code previous} maps a house's key to a resident who lived there (the head, last time); a household that still
     * has that resident keeps the house. The rest take the free houses in order: the larger households first, then by their head's id.
     */
    public static List<Home> assign(Settlement settlement, Map<String, UUID> previous) {
        List<Building> houses = new ArrayList<>();
        for (Building b : settlement.buildings()) {
            if (b.type() == BuildingType.HOUSE) {
                houses.add(b);
            }
        }
        houses.sort(Comparator.comparing(Building::key));
        List<Households.Household> households = new ArrayList<>();
        for (Households.Household h : Households.of(settlement)) {
            if (h.members().stream().anyMatch(Resident::adult)) {
                households.add(h);
            }
        }
        List<Home> out = new ArrayList<>();
        Set<UUID> taken = new HashSet<>(); // heads of the households already housed
        Set<String> used = new HashSet<>();
        for (Building house : houses) { // those who stay where they were
            UUID lived = previous.get(house.key());
            if (lived == null) {
                continue;
            }
            for (Households.Household h : households) {
                if (!taken.contains(h.head().id()) && h.members().stream().anyMatch(m -> m.id().equals(lived))) {
                    out.add(new Home(house, h));
                    taken.add(h.head().id());
                    used.add(house.key());
                    break;
                }
            }
        }
        List<Households.Household> waiting = new ArrayList<>();
        for (Households.Household h : households) {
            if (!taken.contains(h.head().id())) {
                waiting.add(h);
            }
        }
        waiting.sort(Comparator.comparingInt((Households.Household h) -> -h.members().size()).thenComparing(h -> h.head().id()));
        int next = 0;
        for (Households.Household h : waiting) {
            while (next < houses.size() && used.contains(houses.get(next).key())) {
                next++;
            }
            if (next >= houses.size()) {
                break;
            }
            out.add(new Home(houses.get(next), h));
            used.add(houses.get(next).key());
        }
        out.sort(Comparator.comparing((Home h) -> h.house().key()));
        return out;
    }

    /** The house a resident lives in, if their household has one. */
    public static Optional<Building> houseOf(List<Home> homes, UUID resident) {
        return homes.stream().filter(h -> h.household().members().stream().anyMatch(m -> m.id().equals(resident))).map(Home::house).findFirst();
    }

    /** The lines for a house's sign: the household's name and who lives there, each short enough for a sign line. */
    public static List<String> signLines(Households.Household household, int width) {
        List<String> lines = new ArrayList<>();
        lines.add(clip(household.name() + " house", width));
        int people = household.members().size();
        lines.add(clip(people == 1 ? household.head().givenName() : household.head().givenName() + " and " + (people - 1 == 1
                ? household.spouse().map(Resident::givenName).orElse(household.children().isEmpty() ? "" : household.children().get(0).givenName())
                : (people - 1) + " more"), width));
        return lines;
    }

    private static String clip(String s, int width) {
        return s.length() <= width ? s : s.substring(0, width - 1) + "…";
    }
}
