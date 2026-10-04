package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R5.1, R5.5: a village that is attacked arms itself ahead of the worst; guards need tools, not food. */
class GuardTest {
    private static final UUID VILLAGE = new UUID(11, 11);

    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();
    private int next = 1;

    private Resident person(Occupation job, int bravery) {
        return new Resident(new UUID(10, next++), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, bravery),
                job, true, 10_000, null, null, Needs.initial());
    }

    /** A village of the given jobs with food and tools to spare, on a fixed id so every run draws the same numbers. */
    private Settlement village(Occupation... jobs) {
        Settlement s = new Settlement(VILLAGE, "Watchford", "world", 0, 0, 0);
        for (Occupation job : jobs) {
            s.addResident(person(job, 80));
        }
        registry.add(s);
        s.ledger().add(ResourceType.FOOD, 400);
        s.ledger().add(ResourceType.TOOLS, 50);
        s.ledger().add(ResourceType.WOOD, 100);
        s.ledger().add(ResourceType.STONE, 100);
        s.ledger().add(ResourceType.METAL, 50);
        s.ledger().add(ResourceType.GOODS, 30);
        return s;
    }

    private Settlement idlers(int count) {
        Occupation[] jobs = new Occupation[count];
        java.util.Arrays.fill(jobs, Occupation.NITWIT);
        return village(jobs);
    }

    private static long guards(Settlement s) {
        return s.residents().stream().filter(r -> r.occupation() == Occupation.GUARD).count();
    }

    private void days(Settlement s, int n) {
        simulator.simulateTo(s, s.lastSimulatedDay() + n, n);
    }

    /** Lets the next day simulated be {@code day}, as if the village had been quiet until then. */
    private static void startAt(Settlement s, long day) {
        s.setLastSimulatedDay(day - 1);
    }

    @Test
    void oneAttackIsNotEnough() {
        Settlement s = idlers(6);
        s.recordIncident(1);
        days(s, 12);
        assertEquals(0, guards(s));
    }

    @Test
    void twoAttacksInAWeekMakeTheVillageWaryAndAGuardIsCalledUpAtOnce() {
        Settlement s = idlers(6);
        days(s, 1);
        s.recordIncident(1);
        days(s, 1);
        assertEquals(0, guards(s), "one attack so far");
        s.recordIncident(2);
        assertEquals(SettlementSimulator.Alert.WARY, SettlementSimulator.alertLevel(s, 3));
        days(s, 1); // day 3: two attacks in the week
        assertEquals(1, guards(s));
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("took up arms")));
    }

    @Test
    void attacksOlderThanAWeekDoNotCount() {
        Settlement s = idlers(6);
        s.recordIncident(1);
        s.recordIncident(2);
        startAt(s, 20);
        days(s, 3);
        assertEquals(0, guards(s));
        assertEquals(SettlementSimulator.Alert.CALM, SettlementSimulator.alertLevel(s, 20));
    }

    @Test
    void theAlertLevelsFollowAttacksAndThreat() {
        Settlement s = idlers(4);
        assertEquals(SettlementSimulator.Alert.CALM, SettlementSimulator.alertLevel(s, 10));
        s.recordIncident(10);
        assertEquals(SettlementSimulator.Alert.CALM, SettlementSimulator.alertLevel(s, 10));
        s.recordIncident(9);
        assertEquals(SettlementSimulator.Alert.WARY, SettlementSimulator.alertLevel(s, 10));
        s.recordIncident(8);
        assertEquals(SettlementSimulator.Alert.ALARMED, SettlementSimulator.alertLevel(s, 10));
        assertEquals(SettlementSimulator.Alert.WARY, SettlementSimulator.alertLevel(s, 15), "days 9 to 15 hold two attacks");
        assertEquals(SettlementSimulator.Alert.CALM, SettlementSimulator.alertLevel(s, 16), "the attack on day 9 has left the week");
        Settlement high = idlers(4);
        high.setThreat(50);
        assertEquals(SettlementSimulator.Alert.ALARMED, SettlementSimulator.alertLevel(high, 10));
        high.setThreat(80);
        assertEquals(SettlementSimulator.Alert.SIEGE, SettlementSimulator.alertLevel(high, 10));
    }

    @Test
    void anAlarmedVillageWantsOneGuardPerTenResidentsAndCallsThemUpOneADay() {
        Settlement s = idlers(25);
        s.recordIncident(1);
        s.recordIncident(1);
        s.recordIncident(1);
        startAt(s, 2);
        for (int day = 0; day < 5; day++) {
            s.ledger().add(ResourceType.FOOD, 60); // 25 residents eat 50 a day; nobody is called up while food is short
            days(s, 1);
            if (day == 0) {
                assertEquals(1, guards(s), "the first on the first day");
            }
            if (day == 1) {
                assertEquals(2, guards(s), "then one a day");
            }
        }
        assertEquals(3, guards(s), "twenty-five residents want three, and no more");
    }

    @Test
    void aVillageUnderSiegeCallsEveryGuardItWantsUpAtOnceAndEvenInAFamine() {
        Settlement s = idlers(12);
        s.ledger().take(ResourceType.FOOD, 1000); // famine
        s.setThreat(95);
        days(s, 1);
        assertEquals(2, guards(s), "twelve residents under siege want two, called up on the same day");

        Settlement alarmed = idlers(12);
        alarmed.ledger().take(ResourceType.FOOD, 1000);
        alarmed.recordIncident(1);
        alarmed.recordIncident(1);
        alarmed.recordIncident(1);
        startAt(alarmed, 2);
        days(alarmed, 3);
        assertEquals(0, guards(alarmed), "alarmed but starving: every hand is needed for food");
    }

    @Test
    void mossmoorsHistoryGetsAGuardByDay27() {
        // Monster deaths on days 25, 26 and 27, as in the first playtest, with danger never above 28.
        Settlement s = idlers(11);
        startAt(s, 25);
        for (int day = 25; day <= 27; day++) {
            days(s, 1);
            if (day == 26) {
                assertEquals(0, guards(s), "only one attack so far");
            }
            s.recordIncident(day);
        }
        assertEquals(1, guards(s), "wary on day 27, after the attacks on days 25 and 26");
    }

    @Test
    void guardsNeedToolsToBeArmedButNotFood() {
        Settlement base = idlers(4);
        base.addResident(person(Occupation.GUARD, 80));
        base.addResident(person(Occupation.GUARD, 80));
        registry.add(base);
        base.setThreat(45); // not alarmed (that is 50), but not calm either: the guards stay on watch
        Map<String, Object> saved = SettlementCodec.encode(base);

        Settlement armed = SettlementCodec.decode(saved);
        Settlement unarmed = SettlementCodec.decode(saved);
        unarmed.ledger().take(ResourceType.TOOLS, 1000);
        Settlement hungry = SettlementCodec.decode(saved);
        hungry.ledger().take(ResourceType.FOOD, 1000);
        Settlement noGuards = SettlementCodec.decode(saved);
        noGuards.residents().forEach(r -> {
            if (r.occupation() == Occupation.GUARD) {
                r.setOccupation(Occupation.NITWIT);
            }
        });
        for (Settlement s : new Settlement[] {armed, unarmed, hungry, noGuards}) {
            days(s, 3);
        }
        assertTrue(armed.threat() < noGuards.threat() - 3, armed.threat() + " vs " + noGuards.threat());
        assertEquals(noGuards.threat(), unarmed.threat(), 1e-9, "no tools, no difference");
        assertEquals(armed.threat(), hungry.threat(), 1e-9, "an empty stomach does not stop a guard");
    }

    @Test
    void aGuardEatsAnExtraRationWhenThereIsOneAndWearsToolsOut() {
        Settlement s = village();
        s.addResident(person(Occupation.GUARD, 80));
        registry.add(s);
        s.ledger().take(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.FOOD, 48); // under 50, where 2% spoilage rounds to nothing, and enough for the run
        s.ledger().take(ResourceType.TOOLS, 50);
        s.ledger().add(ResourceType.TOOLS, 40);
        Settlement idler = SettlementCodec.decode(SettlementCodec.encode(s));
        idler.residents().forEach(r -> r.setOccupation(Occupation.NITWIT));
        for (int day = 0; day < 3; day++) {
            s.setThreat(45);
            idler.setThreat(45);
            days(s, 1);
            days(idler, 1);
        }
        assertEquals(3, idler.ledger().get(ResourceType.FOOD) - s.ledger().get(ResourceType.FOOD),
                "one extra food a day for three days");
        for (int day = 0; day < 30; day++) {
            s.setThreat(45);
            days(s, 1);
        }
        assertTrue(s.ledger().get(ResourceType.TOOLS) < 40, "tools wear on watch");
    }

    @Test
    void guardsStandDownOnlyAfterTenQuietDaysOneADay() {
        Settlement s = idlers(4);
        s.addResident(person(Occupation.GUARD, 80));
        s.addResident(person(Occupation.GUARD, 80));
        registry.add(s);
        s.recordIncident(8); // a single attack: not wary, but not quiet either
        startAt(s, 10);
        days(s, 8); // days 10 to 17
        assertEquals(2, guards(s), "the attack on day 8 is still within ten days");
        days(s, 1); // day 18
        assertEquals(1, guards(s), "one a day");
        days(s, 1);
        assertEquals(0, guards(s));
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("stood down")));
    }

    @Test
    void theJoblessAreCalledUpFirstAndNobodyTimidOrFeedingTheVillage() {
        Settlement s = village(Occupation.FARMER, Occupation.FARMER, Occupation.MASON);
        Resident timid = person(Occupation.UNEMPLOYED, 10);
        Resident brave = person(Occupation.UNEMPLOYED, 90);
        s.addResident(timid);
        s.addResident(brave);
        registry.add(s);
        s.recordIncident(1);
        s.recordIncident(1);
        startAt(s, 2);
        days(s, 4);
        assertEquals(Occupation.GUARD, brave.occupation());
        assertTrue(timid.occupation() != Occupation.GUARD, "too timid");
        assertEquals(2, s.residents().stream().filter(r -> r.occupation() == Occupation.FARMER).count(),
                "farmers keep the village fed");
        assertEquals(1, guards(s));
    }

    @Test
    void theOnlyToolmakerOrMinerIsNotCalledUpButOneOfSeveralIs() {
        Settlement s = village(Occupation.TOOLSMITH, Occupation.MINER, Occupation.FARMER, Occupation.FARMER);
        s.recordIncident(1);
        s.recordIncident(1);
        startAt(s, 2);
        days(s, 4);
        assertEquals(0, guards(s), "the only smith and the only miner arm the guards, and nobody else is free to go");

        Settlement two = village(Occupation.TOOLSMITH, Occupation.TOOLSMITH, Occupation.FARMER, Occupation.FARMER);
        two.recordIncident(1);
        two.recordIncident(1);
        startAt(two, 2);
        days(two, 3);
        assertEquals(1, guards(two), "with a second smith one can be spared");
    }

    @Test
    void idlersAreCalledUpBeforeWorkersWhoProduceSomething() {
        Settlement s = village(Occupation.MASON, Occupation.MASON);
        Resident idler = person(Occupation.NITWIT, 60);
        s.addResident(idler);
        registry.add(s);
        s.recordIncident(1);
        s.recordIncident(1);
        startAt(s, 2);
        days(s, 2);
        assertEquals(Occupation.GUARD, idler.occupation(), "less brave, but makes nothing, so goes before the masons");
    }

    @Test
    void childrenAndEldersNeverTakeUpArms() {
        Settlement s = village();
        s.addResident(new Resident(new UUID(10, 900), "Kid", "Person", Gender.MALE, new Traits(50, 50, 50, 99),
                Occupation.UNEMPLOYED, false, 10_000, null, null, Needs.initial()));
        s.addResident(new Resident(new UUID(10, 901), "Old", "Person", Gender.MALE, new Traits(50, 50, 50, 99),
                Occupation.UNEMPLOYED, true, -Resident.elderAge() - 5, null, null, Needs.initial()));
        registry.add(s);
        s.setThreat(95);
        days(s, 4);
        assertEquals(0, guards(s));
    }

    @Test
    void attacksAreSavedAndAnOldSaveHasNone() {
        Settlement s = idlers(3);
        s.recordIncident(4);
        s.recordIncident(6);
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(2, loaded.incidentsSince(0));
        assertEquals(1, loaded.incidentsSince(5));

        // A format-15 save, written before attacks were remembered: no "incidents" key at all.
        Map<String, Object> old = new LinkedHashMap<>(SettlementCodec.encode(idlers(3)));
        old.put("format", 15);
        old.remove("incidents");
        Settlement migrated = SettlementCodec.decode(old);
        assertEquals(0, migrated.incidentsSince(0));
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(migrated).get("format"));
    }

    @Test
    void theRecordOfAttacksIsBounded() {
        Settlement s = idlers(3);
        for (int i = 0; i < Settlement.MAX_INCIDENTS + 40; i++) {
            s.recordIncident(i);
        }
        assertEquals(Settlement.MAX_INCIDENTS, s.incidentsSince(0));
        assertFalse(s.incidentsSince(0) > Settlement.MAX_INCIDENTS);
    }
}
