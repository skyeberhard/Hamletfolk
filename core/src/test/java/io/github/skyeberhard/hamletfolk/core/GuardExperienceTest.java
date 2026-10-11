package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R9.7: a guard's experience is kept for good and the village calls up its veterans first. */
class GuardExperienceTest {
    private static final UUID VILLAGE = new UUID(12, 12);

    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();
    private int next = 1;

    private Resident person(Occupation job, int bravery) {
        return new Resident(new UUID(13, next++), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, bravery),
                job, true, 10_000, null, null, Needs.initial());
    }

    @Test
    void guardDaysSurviveWorkingAnotherTradeAndBackAgain() {
        Resident r = person(Occupation.GUARD, 80);
        for (int i = 0; i < 40; i++) {
            r.addXp(1);
        }
        assertEquals(40, r.xp());
        r.setOccupation(Occupation.UNEMPLOYED);
        r.setOccupation(Occupation.FARMER);
        for (int i = 0; i < 5; i++) {
            r.addXp(1);
        }
        assertEquals(5, r.xp(), "a novice farmer");
        r.setOccupation(Occupation.GUARD);
        assertEquals(40, r.xp(), "the veteran guard they were");
        assertEquals(3, r.level(), "40 days is level 3");
    }

    @Test
    void aFarmersDaysSurviveASpellAsAGuard() {
        Resident r = person(Occupation.FARMER, 80);
        r.setXp(70);
        r.setOccupation(Occupation.GUARD);
        for (int i = 0; i < 3; i++) {
            r.addXp(1);
        }
        assertEquals(3, r.xp());
        r.setOccupation(Occupation.FARMER);
        assertEquals(70, r.xp(), "a master farmer's days are not given up by guarding");
        assertEquals(4, r.level());
    }

    @Test
    void guardDaysAreSavedAndAnOldSavesGuardDaysAreKept() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident guard = person(Occupation.GUARD, 80);
        s.addResident(guard);
        guard.setXp(25);
        Resident farmer = person(Occupation.FARMER, 80);
        s.addResident(farmer);
        farmer.setXp(12);
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(25, loaded.resident(guard.id()).orElseThrow().xp());
        assertEquals(12, loaded.resident(farmer.id()).orElseThrow().xp());

        // a format-28 save kept guard days like any trade's, in "xp" and "xpTrade": they are the guard's days now
        Map<String, Object> old = new LinkedHashMap<>(SettlementCodec.encode(s));
        old.put("format", 28);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> residents = (List<Map<String, Object>>) old.get("residents");
        for (Map<String, Object> r : residents) {
            r.remove("guardXp");
            if (r.get("id").toString().equals(guard.id().toString())) {
                r.put("xp", 25);
                r.put("xpTrade", "GUARD");
            }
        }
        Resident migrated = SettlementCodec.decode(old).resident(guard.id()).orElseThrow();
        assertEquals(25, migrated.xp());
        migrated.setOccupation(Occupation.FARMER);
        assertEquals(0, migrated.xp());
        migrated.setOccupation(Occupation.GUARD);
        assertEquals(25, migrated.xp(), "and kept for good");
        assertEquals(25, SettlementCodec.decode(SettlementCodec.encode(SettlementCodec.decode(old))).resident(guard.id()).orElseThrow().xp());
    }

    @Test
    void aVeteranIsCalledUpBeforeABraverNovice() {
        Settlement s = new Settlement(VILLAGE, "Watchford", "world", 0, 0, 0);
        for (int i = 0; i < 3; i++) {
            s.addResident(person(Occupation.FARMER, 80));
        }
        Resident novice = person(Occupation.UNEMPLOYED, 95);
        Resident veteran = person(Occupation.GUARD, 60);
        veteran.setXp(30);
        veteran.setOccupation(Occupation.UNEMPLOYED); // stood down after the last scare
        s.addResident(novice);
        s.addResident(veteran);
        registry.add(s);
        s.ledger().add(ResourceType.FOOD, 400);
        s.ledger().add(ResourceType.TOOLS, 50);
        s.recordIncident(1);
        s.recordIncident(1);
        s.setLastSimulatedDay(1);
        simulator.simulateTo(s, 4, 3);
        assertEquals(Occupation.GUARD, veteran.occupation(), "the one who has done it before");
        assertTrue(novice.occupation() != Occupation.GUARD, "the braver novice waits");
        assertTrue(veteran.xp() >= 30, "and the days are still his");
    }

    @Test
    void aFewDaysServedAreNotYetAVeteran() {
        Settlement s = new Settlement(VILLAGE, "Watchford", "world", 0, 0, 0);
        for (int i = 0; i < 3; i++) {
            s.addResident(person(Occupation.FARMER, 80));
        }
        Resident bravest = person(Occupation.UNEMPLOYED, 95);
        Resident fewDays = person(Occupation.GUARD, 60);
        fewDays.setXp(3);
        fewDays.setOccupation(Occupation.UNEMPLOYED);
        s.addResident(bravest);
        s.addResident(fewDays);
        registry.add(s);
        s.ledger().add(ResourceType.FOOD, 400);
        s.ledger().add(ResourceType.TOOLS, 50);
        s.recordIncident(1);
        s.recordIncident(1);
        s.setLastSimulatedDay(1);
        simulator.simulateTo(s, 4, 3);
        assertEquals(Occupation.GUARD, bravest.occupation(), "three days is still level 1: the braver wins");
        assertTrue(fewDays.occupation() != Occupation.GUARD);
    }

    @Test
    void theLeastExperiencedGuardStandsDownFirst() {
        Settlement s = new Settlement(VILLAGE, "Watchford", "world", 0, 0, 0);
        s.addResident(person(Occupation.FARMER, 80));
        Resident veteran = person(Occupation.GUARD, 80);
        veteran.setXp(60);
        Resident novice = person(Occupation.GUARD, 80);
        s.addResident(veteran);
        s.addResident(novice);
        registry.add(s);
        s.ledger().add(ResourceType.FOOD, 400);
        s.ledger().add(ResourceType.TOOLS, 50);
        s.setLastSimulatedDay(40); // quiet for a long time: the guards stand down, one a day
        simulator.simulateTo(s, 41, 1);
        assertEquals(Occupation.GUARD, veteran.occupation(), "the veteran is the last to go home");
        assertTrue(novice.occupation() != Occupation.GUARD);
    }

    @Test
    void guardDaysSurviveBecomingAZombieAndBeingCured() {
        Resident guard = person(Occupation.GUARD, 80);
        guard.setXp(45);
        guard.setOccupation(Occupation.FARMER);
        guard.setXp(7);
        Resident copy = guard.withId(new UUID(99, 99), 0);
        assertEquals(7, copy.xp());
        copy.setOccupation(Occupation.GUARD);
        assertEquals(45, copy.xp(), "both kinds of days came through");
    }

    @Test
    void serviceAddsDaysToTheGuardRecordAndTheLevelShowsOnTheTitle() {
        Settlement s = new Settlement(VILLAGE, "Watchford", "world", 0, 0, 0);
        Resident guard = person(Occupation.GUARD, 80);
        s.addResident(guard);
        s.ledger().add(ResourceType.FOOD, 100);
        s.ledger().add(ResourceType.TOOLS, 20);
        guard.setXp(9);
        s.setPinned(guard.id(), true, 0); // (a village at peace would stand its guard down)
        s.setLastSimulatedDay(0);
        simulator.simulateTo(s, 3, 3);
        assertEquals(12, guard.xp(), "a day in the stores' armoury is a day served");
        assertEquals(2, guard.level(), "10 days: level 2");
    }
}
