package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.14: residents have a gender, names agree with it, and old saves gain one without renaming anyone. */
class GenderTest {
    private final SettlementRegistry registry = new SettlementRegistry();

    @Test
    void aResidentsNameComesFromTheirGendersList() {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 300; i++) {
            Resident r = registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
            assertEquals(r.gender(), NameGenerator.genderOf(r.givenName()), r.givenName());
        }
    }

    @Test
    void gendersAreMixedAndMostlyFemaleOrMale() {
        Settlement s = registry.found("world", 0, 0, 0);
        int[] counts = new int[Gender.values().length];
        for (int i = 0; i < 600; i++) {
            counts[registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null).gender().ordinal()]++;
        }
        assertTrue(counts[Gender.FEMALE.ordinal()] > 200, "female " + counts[0]);
        assertTrue(counts[Gender.MALE.ordinal()] > 200, "male " + counts[1]);
        assertTrue(counts[Gender.NONBINARY.ordinal()] > 0, "nonbinary " + counts[2]);
    }

    @Test
    void theSameIdAlwaysGivesTheSameResident() {
        UUID id = UUID.randomUUID();
        Resident a = new SettlementRegistry().enroll(registry.found("w", 0, 0, 0), id, Occupation.FARMER, true, 0, null, null);
        SettlementRegistry other = new SettlementRegistry();
        Resident b = other.enroll(other.found("w", 0, 0, 0), id, Occupation.FARMER, true, 0, null, null);
        assertEquals(a.gender(), b.gender());
        assertEquals(a.givenName(), b.givenName());
    }

    @Test
    void childrenDrawTheirOwnGenderAndGenderSurvivesSavingAndCure() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident mother = registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
        Resident child = registry.enroll(s, UUID.randomUUID(), Occupation.UNEMPLOYED, false, 1, mother.id(), null);
        assertEquals(child.gender(), NameGenerator.genderOf(child.givenName()));

        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(child.gender(), loaded.resident(child.id()).orElseThrow().gender());

        UUID zombie = UUID.randomUUID();
        UUID cured = UUID.randomUUID();
        registry.turn(child.id(), zombie);
        assertEquals(child.gender(), registry.cure(zombie, cured).orElseThrow().gender());
    }

    private static Map<String, Object> oldResident(String given, UUID id) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id.toString());
        r.put("givenName", given);
        r.put("familyName", "Hand");
        r.put("occupation", "FARMER");
        r.put("adult", true);
        r.put("bornDay", 0);
        r.put("lastBlockedDay", -1);
        r.put("traits", List.of(50, 50, 50, 50));
        r.put("needs", List.of(80, 80, 60));
        r.put("familiarity", new LinkedHashMap<>());
        return r;
    }

    /** A hand-built format-4 save, written before residents had a gender. */
    private static Map<String, Object> formatFourSave(List<Object> residents, Map<String, Object> turned) {
        Map<String, Object> save = new LinkedHashMap<>();
        save.put("format", 4);
        save.put("id", UUID.randomUUID().toString());
        save.put("name", "Oldford");
        save.put("world", "world");
        save.put("centerX", 0);
        save.put("centerZ", 0);
        save.put("foundedDay", 0);
        save.put("lastSimulatedDay", 0);
        save.put("threat", 0.0);
        save.put("stock", new LinkedHashMap<>());
        save.put("treasury", 0);
        save.put("conditions", new LinkedHashMap<>());
        save.put("residents", residents);
        save.put("turned", turned);
        save.put("history", new ArrayList<>());
        return save;
    }

    @Test
    void aFormatFourSaveGainsGendersFromNamesAndKeepsEveryName() {
        UUID neutral = UUID.randomUUID();
        List<Object> residents = new ArrayList<>(List.of(
                oldResident("Anya", UUID.randomUUID()),
                oldResident("Harold", UUID.randomUUID()),
                oldResident("Wren", neutral),
                oldResident("Renamed By An Admin", UUID.randomUUID())));
        Map<String, Object> turned = new LinkedHashMap<>();
        turned.put(UUID.randomUUID().toString(), oldResident("Greta", UUID.randomUUID()));

        Settlement loaded = SettlementCodec.decode(formatFourSave(residents, turned));

        Map<String, Gender> byName = new LinkedHashMap<>();
        loaded.residents().forEach(r -> byName.put(r.givenName(), r.gender()));
        assertEquals(Gender.FEMALE, byName.get("Anya"));
        assertEquals(Gender.MALE, byName.get("Harold"));
        assertNotNull(byName.get("Wren"));
        assertNotNull(byName.get("Renamed By An Admin"));
        assertEquals(4, byName.size());
        assertEquals(Gender.FEMALE, loaded.turned().values().iterator().next().gender());

        // Deterministic: loading the same old save again gives the neutral resident the same gender.
        Settlement again = SettlementCodec.decode(formatFourSave(residents, turned));
        assertEquals(loaded.resident(neutral).orElseThrow().gender(), again.resident(neutral).orElseThrow().gender());
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(loaded).get("format"));
    }

    private Resident parentOf(Settlement s, Gender wanted) {
        for (int i = 0; i < 500; i++) {
            Resident r = registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
            if (r.gender() == wanted) {
                return r;
            }
        }
        throw new AssertionError("no " + wanted + " resident drawn");
    }

    @Test
    void dialogueAboutAParentUsesTheirGender() {
        Settlement s = registry.found("world", 0, 0, 0);
        String[][] expected = {{"FEMALE", "mother", "her."}, {"MALE", "father", "him."}, {"NONBINARY", "parent", "them."}};
        for (String[] row : expected) {
            Resident parent = parentOf(s, Gender.valueOf(row[0]));
            Resident child = registry.enroll(s, UUID.randomUUID(), Occupation.UNEMPLOYED, false, 1, parent.id(), null);
            String line = null;
            for (long seed = 0; seed < 500 && line == null; seed++) {
                String said = Dialogue.smallTalk(child, s, 10, new java.util.Random(seed));
                if (said.startsWith("My ")) {
                    line = said;
                }
            }
            assertNotNull(line, "a child with a parent should sometimes mention them");
            assertTrue(line.startsWith("My " + row[1] + ", " + parent.givenName()), line);
            assertTrue(line.endsWith("from " + row[2]), line);
        }
    }
}
