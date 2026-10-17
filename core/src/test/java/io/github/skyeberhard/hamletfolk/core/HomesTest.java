package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R6.1: which house a household lives in. */
class HomesTest {
    private static int n;

    private Resident person(Settlement s, String given, String family, Gender gender, long born, UUID a, UUID b, boolean adult) {
        Resident r = new Resident(new UUID(800, n++), given, family, gender, new Traits(50, 50, 50, 50), Occupation.FARMER, adult, born, a, b, Needs.initial());
        s.addResident(r);
        return r;
    }

    private Settlement village(int houses) {
        Settlement s = new Settlement(new UUID(801, 1), "Homeford", "world", 0, 0, 0);
        s.setLastSimulatedDay(40);
        for (int i = 0; i < houses; i++) {
            s.registerBuilding(new Building(BuildingType.HOUSE, 10 * i, 64, 0, 1, "p"));
        }
        return s;
    }

    @Test
    void eachHouseholdTakesAHouseAndTheLargestComeFirst() {
        Settlement s = village(2);
        Resident wife = person(s, "Anna", "Mercer", Gender.FEMALE, 5, null, null, true);
        Resident husband = person(s, "Tom", "Hale", Gender.MALE, 8, null, null, true);
        Marriages.marry(s, wife, husband, 30);
        person(s, "Lily", "Mercer", Gender.FEMALE, 38, wife.id(), husband.id(), false);
        Resident single = person(s, "Old", "Wren", Gender.MALE, 0, null, null, true);
        person(s, "Third", "Fenn", Gender.FEMALE, 0, null, null, true); // no house left for this one
        List<Homes.Home> homes = Homes.assign(s, Map.of());
        assertEquals(2, homes.size());
        assertEquals("Mercer", homes.get(0).household().name(), "the family of three has the first house");
        assertTrue(Homes.houseOf(homes, wife.id()).isPresent());
        assertEquals(single.id(), homes.get(1).head(), "of the two on their own, the one with the lower id has the second house");
    }

    @Test
    void aHouseholdKeepsItsHouseWhenTheVillageChanges() {
        Settlement s = village(2);
        Resident a = person(s, "Anna", "Mercer", Gender.FEMALE, 5, null, null, true);
        Resident b = person(s, "Bram", "Wren", Gender.MALE, 6, null, null, true);
        List<Homes.Home> first = Homes.assign(s, Map.of());
        Map<String, UUID> previous = new HashMap<>();
        first.forEach(h -> previous.put(h.house().key(), h.head()));
        Building annaHouse = Homes.houseOf(first, a.id()).orElseThrow();
        // a larger household arrives and would take the first house, but the others keep theirs
        Resident c = person(s, "Cora", "Fenn", Gender.FEMALE, 1, null, null, true);
        Resident d = person(s, "Dale", "Fenn", Gender.MALE, 1, null, null, true);
        Marriages.marry(s, c, d, 40);
        List<Homes.Home> later = Homes.assign(s, previous);
        assertEquals(annaHouse, Homes.houseOf(later, a.id()).orElseThrow(), "Anna still lives where she did");
        assertEquals(Homes.houseOf(first, b.id()).orElseThrow(), Homes.houseOf(later, b.id()).orElseThrow());
        assertEquals(2, later.size(), "two houses, two households: the newcomers wait");
    }

    @Test
    void aHouseIsFreedWhenItsHouseholdIsGone() {
        Settlement s = village(1);
        Resident a = person(s, "Anna", "Mercer", Gender.FEMALE, 5, null, null, true);
        Resident b = person(s, "Bram", "Wren", Gender.MALE, 6, null, null, true);
        List<Homes.Home> first = Homes.assign(s, Map.of());
        Map<String, UUID> previous = new HashMap<>();
        first.forEach(h -> previous.put(h.house().key(), h.head()));
        Resident living = Homes.houseOf(first, a.id()).isPresent() ? a : b;
        Resident other = living == a ? b : a;
        s.removeResident(living.id());
        List<Homes.Home> later = Homes.assign(s, previous);
        assertEquals(1, later.size());
        assertTrue(Homes.houseOf(later, other.id()).isPresent(), "the other takes the free house");
    }

    @Test
    void childrenWithNoAdultHaveNoHouseAndFoundHousesAreCounted() {
        Settlement s = village(1);
        person(s, "Tiny", "Orphan", Gender.FEMALE, 38, null, null, false);
        assertTrue(Homes.assign(s, Map.of()).isEmpty());
    }

    @Test
    void signLinesAreShort() {
        Settlement s = village(1);
        Resident wife = person(s, "Anna", "Mercer", Gender.FEMALE, 5, null, null, true);
        Resident husband = person(s, "Tom", "Hale", Gender.MALE, 8, null, null, true);
        Marriages.marry(s, wife, husband, 30);
        person(s, "Lily", "Mercer", Gender.FEMALE, 38, wife.id(), husband.id(), false);
        var home = Households.of(s, wife.id()).orElseThrow();
        List<String> lines = Homes.signLines(home, 14);
        assertEquals("Mercer house", lines.get(0));
        assertTrue(lines.get(1).contains("Anna"), lines.get(1));
        assertFalse(lines.stream().anyMatch(l -> l.length() > 14));
    }
}
