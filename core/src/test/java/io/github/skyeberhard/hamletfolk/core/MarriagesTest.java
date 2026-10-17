package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R6.1: marriages, households, and their save. */
class MarriagesTest {
    private static int n;

    private Resident person(Settlement s, String given, String family, Gender gender, long born, UUID a, UUID b, boolean adult) {
        Resident r = new Resident(new UUID(500, n++), given, family, gender, new Traits(50, 50, 50, 50), Occupation.FARMER, adult, born, a, b,
                Needs.initial());
        s.addResident(r);
        return r;
    }

    private Settlement village() {
        Settlement s = new Settlement(new UUID(501, 1), "Weddington", "world", 0, 0, 0);
        s.setLastSimulatedDay(40);
        s.housing().setChunk(0, 0, 10);
        return s;
    }

    @Test
    void anUnmarriedManAndWomanEventuallyMarryAndTheHistoryRecordsIt() {
        Settlement s = village();
        Resident woman = person(s, "Anna", "Mercer", Gender.FEMALE, 10, null, null, true);
        Resident man = person(s, "Tom", "Hale", Gender.MALE, 10, null, null, true);
        long day = 40;
        for (; day < 90 && woman.spouse() == null; day++) {
            Marriages.run(s, day);
        }
        assertEquals(man.id(), woman.spouse());
        assertEquals(woman.id(), man.spouse());
        assertTrue(s.history().stream().anyMatch(e -> e.kind() == HistoryEvent.Kind.MARRIAGE && e.text().contains("Anna Mercer and Tom Hale were married")));
        assertEquals(Optional.empty(), Marriages.run(s, day + 1), "nobody left to marry");
    }

    @Test
    void kinDoNotMarryAndTwoOfTheSameSexDoNot() {
        Settlement s = village();
        Resident mother = person(s, "Mary", "Hale", Gender.FEMALE, 0, null, null, true);
        person(s, "Son", "Hale", Gender.MALE, 10, mother.id(), null, true);
        person(s, "Ann", "Hale", Gender.FEMALE, 10, null, null, true);
        // the son is Mary's kin, so she cannot marry him; he may marry Ann
        for (long day = 40; day < 600; day++) {
            Marriages.run(s, day);
        }
        assertNull(mother.spouse());
    }

    @Test
    void theSameVillageOnTheSameDayMakesTheSameChoice() {
        Settlement a = village();
        Settlement b = village();
        for (int i = 0; i < 4; i++) {
            person(a, "W" + i, "F" + i, Gender.FEMALE, 10, null, null, true);
            person(a, "M" + i, "G" + i, Gender.MALE, 10, null, null, true);
        }
        for (Resident r : a.residents()) {
            b.addResident(new Resident(r.id(), r.givenName(), r.familyName(), r.gender(), r.traits(), r.occupation(), true, 10, null, null, Needs.initial()));
        }
        for (long day = 40; day < 100; day++) {
            Optional<Resident[]> x = Marriages.run(a, day);
            Optional<Resident[]> y = Marriages.run(b, day);
            assertEquals(x.isPresent(), y.isPresent());
            x.ifPresent(pair -> assertEquals(pair[0].id(), y.get()[0].id()));
        }
    }

    @Test
    void aHouseholdIsACoupleAndTheirYoungChildrenNamedForTheWife() {
        Settlement s = village();
        Resident wife = person(s, "Anna", "Mercer", Gender.FEMALE, 5, null, null, true);
        Resident husband = person(s, "Tom", "Hale", Gender.MALE, 8, null, null, true);
        Marriages.marry(s, wife, husband, 30);
        Resident child = person(s, "Lily", "Mercer", Gender.FEMALE, 38, wife.id(), husband.id(), false);
        Resident grown = person(s, "Sam", "Mercer", Gender.MALE, 0, wife.id(), husband.id(), true);
        Resident alone = person(s, "Old", "Wren", Gender.MALE, 0, null, null, true);

        var households = Households.of(s);
        var home = Households.of(s, child.id()).orElseThrow();
        assertEquals("Mercer", home.name());
        assertEquals(wife.id(), home.head().id(), "the older of the couple");
        assertEquals(1, home.children().size());
        assertTrue(home.describe().contains("Anna and Tom") && home.describe().contains("with Lily"), home.describe());
        assertEquals(home, Households.of(s, husband.id()).orElseThrow());
        assertFalse(home.children().contains(grown), "a grown child has a home of their own");
        assertEquals(1, Households.of(s, alone.id()).orElseThrow().members().size());
        assertEquals(households.stream().mapToInt(h -> h.members().size()).sum(), s.population(), "everyone is in exactly one household");
    }

    @Test
    void aWidowKeepsTheYoungChildrenInAHouseholdOfTheirOwn() {
        Settlement s = village();
        Resident wife = person(s, "Anna", "Mercer", Gender.FEMALE, 5, null, null, true);
        Resident husband = person(s, "Tom", "Hale", Gender.MALE, 8, null, null, true);
        Marriages.marry(s, wife, husband, 30);
        Resident child = person(s, "Lily", "Mercer", Gender.FEMALE, 38, wife.id(), husband.id(), false);
        s.removeResident(husband.id());
        assertNull(wife.spouse());
        assertTrue(s.history().stream().anyMatch(e -> e.kind() == HistoryEvent.Kind.MARRIAGE && e.text().contains("has no spouse now")));
        var home = Households.of(s, child.id()).orElseThrow();
        assertEquals(wife.id(), home.head().id());
        assertEquals(1, home.children().size());
    }

    @Test
    void aChildBornToAnUnmarriedPairMarriesThem() {
        Settlement s = village();
        Resident woman = person(s, "Anna", "Mercer", Gender.FEMALE, 10, null, null, true);
        Resident man = person(s, "Tom", "Hale", Gender.MALE, 10, null, null, true);
        s.ledger().add(Commodity.BREAD, 400);
        s.ledger().add(Commodity.PRODUCE, 400);
        SettlementRegistry registry = new SettlementRegistry();
        registry.add(s);
        Resident child = Births.run(registry, s, 40).orElseThrow();
        assertEquals(man.id(), woman.spouse());
        assertEquals(woman.id(), child.parentA());
    }

    @Test
    void aResidentWhoMovesToAnotherVillageArrivesUnmarried() {
        Settlement a = village();
        Settlement b = new Settlement(new UUID(501, 2), "Elsewhere", "world", 500, 0, 500);
        Resident wife = person(a, "Anna", "Mercer", Gender.FEMALE, 5, null, null, true);
        Resident husband = person(a, "Tom", "Hale", Gender.MALE, 8, null, null, true);
        Marriages.marry(a, wife, husband, 30);
        Resident moved = a.removeResident(wife.id());
        b.addResident(moved);
        assertNull(moved.spouse(), "the migrant is free to marry again");
        assertNull(husband.spouse());
        assertEquals(0, moved.marriedDay());
    }

    @Test
    void marriagesSurviveASaveAndAnOldSaveHasNone() {
        Settlement s = village();
        Resident wife = person(s, "Anna", "Mercer", Gender.FEMALE, 5, null, null, true);
        Resident husband = person(s, "Tom", "Hale", Gender.MALE, 8, null, null, true);
        Marriages.marry(s, wife, husband, 33);
        Map<String, Object> saved = SettlementCodec.encode(s);
        Settlement loaded = SettlementCodec.decode(saved);
        assertEquals(husband.id(), loaded.resident(wife.id()).orElseThrow().spouse());
        assertEquals(33, loaded.resident(wife.id()).orElseThrow().marriedDay());

        // a format 29 save: no spouse fields, an older format number
        @SuppressWarnings("unchecked")
        Map<String, Object> old = new java.util.LinkedHashMap<>(saved);
        old.put("format", 29);
        java.util.List<Object> residents = new java.util.ArrayList<>();
        for (Object o : (java.util.List<?>) old.get("residents")) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>((Map<String, Object>) o);
            copy.remove("spouse");
            copy.remove("marriedDay");
            residents.add(copy);
        }
        old.put("residents", residents);
        Settlement migrated = SettlementCodec.decode(old);
        assertEquals(2, migrated.population());
        assertTrue(migrated.residents().stream().allMatch(r -> r.spouse() == null));
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(migrated).get("format"));
    }
}
