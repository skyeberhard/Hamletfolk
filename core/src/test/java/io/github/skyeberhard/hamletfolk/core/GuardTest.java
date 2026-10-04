package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R5.1: sustained high threat turns a resident into a guard, who consumes tools and food. */
class GuardTest {
    private static final UUID VILLAGE = new UUID(11, 11);

    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();
    private int next = 1;

    private Resident person(Occupation job, int bravery) {
        return new Resident(new UUID(10, next++), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, bravery),
                job, true, 10_000, null, null, Needs.initial());
    }

    /** Ten residents of the given jobs with food and tools to spare, on a fixed id so every run draws the same numbers. */
    private Settlement village(Occupation... jobs) {
        Settlement s = new Settlement(VILLAGE, "Watchford", "world", 0, 0, 0);
        registry.add(s);
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

    private static long guards(Settlement s) {
        return s.residents().stream().filter(r -> r.occupation() == Occupation.GUARD).count();
    }

    private void days(Settlement s, int n) {
        simulator.simulateTo(s, s.lastSimulatedDay() + n, n);
    }

    /** Days with danger that does not let up, so guards stay on watch instead of standing down. */
    private void dangerousDays(Settlement s, int n) {
        for (int i = 0; i < n; i++) {
            s.raiseThreat(60);
            days(s, 1);
        }
    }

    @Test
    void aRaidTheVillageWinsIsNotSustained() {
        // What the Paper layer adds: 30 when a raid starts, and 15 more for a villager killed by a raider.
        for (int added : new int[] {30, 45}) {
            Settlement s = village(Occupation.NITWIT, Occupation.NITWIT, Occupation.NITWIT, Occupation.NITWIT);
            s.raiseThreat(added);
            days(s, 15);
            assertEquals(0, guards(s), "threat " + added + " fades before it is sustained");
        }
    }

    @Test
    void aRaidTheVillageLosesRaisesAGuardAfterThreeDays() {
        Settlement s = village(Occupation.NITWIT, Occupation.NITWIT, Occupation.NITWIT, Occupation.NITWIT);
        s.raiseThreat(70); // 30 when the raid starts and 40 more when it is lost
        days(s, 3);
        assertEquals(0, guards(s));
        days(s, 1);
        assertEquals(1, guards(s));
    }

    @Test
    void sustainedHighThreatTurnsAResidentIntoAGuard() {
        Settlement s = village(Occupation.NITWIT, Occupation.NITWIT, Occupation.NITWIT, Occupation.NITWIT);
        s.raiseThreat(95); // a raid and more
        days(s, 2);
        assertEquals(0, guards(s), "not yet: only two days");
        days(s, 3);
        assertEquals(1, guards(s));
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("took up arms")));
    }

    @Test
    void onlyOneGuardPerTenResidentsAndOneADay() {
        Settlement few = village(Occupation.NITWIT, Occupation.NITWIT, Occupation.NITWIT, Occupation.NITWIT,
                Occupation.NITWIT, Occupation.NITWIT);
        for (int day = 0; day < 12; day++) {
            few.raiseThreat(100); // danger that never lets up
            days(few, 1);
        }
        assertEquals(1, guards(few), "six residents want one guard");

        Occupation[] twentyFive = new Occupation[25];
        java.util.Arrays.fill(twentyFive, Occupation.NITWIT);
        Settlement many = village(twentyFive);
        for (int day = 0; day < 4; day++) {
            many.raiseThreat(100);
            days(many, 1);
        }
        long afterFirstDays = guards(many);
        assertTrue(afterFirstDays <= 1, "one a day at most, and the first only on the fourth day: " + afterFirstDays);
        for (int day = 0; day < 20; day++) {
            many.raiseThreat(100);
            many.ledger().add(ResourceType.FOOD, 80); // enough to eat: nobody is called up while food is short
            days(many, 1);
        }
        assertEquals(3, guards(many), "twenty-five residents want three");
    }

    @Test
    void theJoblessAreCalledUpFirstAndNobodyTimidOrFeedingTheVillage() {
        Settlement s = village(Occupation.FARMER, Occupation.FARMER, Occupation.MASON);
        Resident timid = person(Occupation.UNEMPLOYED, 10);
        Resident brave = person(Occupation.UNEMPLOYED, 90);
        s.addResident(timid);
        s.addResident(brave);
        registry.add(s);
        for (int day = 0; day < 6; day++) {
            s.raiseThreat(100);
            days(s, 1);
        }
        assertEquals(Occupation.GUARD, brave.occupation());
        assertTrue(timid.occupation() != Occupation.GUARD, "too timid");
        assertEquals(2, s.residents().stream().filter(r -> r.occupation() == Occupation.FARMER).count(),
                "farmers keep the village fed");
        assertEquals(1, guards(s));
    }

    @Test
    void whenNoneAreJoblessSomeoneWhoIsNotFeedingTheVillageGoes() {
        Settlement s = village(Occupation.FARMER, Occupation.MASON);
        for (int day = 0; day < 6; day++) {
            s.raiseThreat(100);
            days(s, 1);
        }
        assertEquals(1, s.residents().stream().filter(r -> r.occupation() == Occupation.GUARD).count());
        assertEquals(Occupation.FARMER, s.residents().stream().filter(r -> r.occupation() != Occupation.GUARD)
                .findFirst().orElseThrow().occupation());
    }

    @Test
    void nobodyIsCalledUpInAFamineOrWhileFoodIsShort() {
        Settlement s = village(Occupation.NITWIT, Occupation.NITWIT, Occupation.NITWIT, Occupation.NITWIT);
        s.ledger().take(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.FOOD, 12); // well under what four residents should hold
        for (int day = 0; day < 6; day++) {
            s.raiseThreat(100);
            s.ledger().add(ResourceType.FOOD, 12 - Math.min(12, s.ledger().get(ResourceType.FOOD)));
            days(s, 1);
        }
        assertEquals(0, guards(s));
    }

    @Test
    void idlersAreCalledUpBeforeWorkersWhoProduceSomething() {
        Settlement s = village(Occupation.MASON, Occupation.MASON);
        Resident idler = person(Occupation.NITWIT, 60);
        s.addResident(idler);
        registry.add(s);
        for (int day = 0; day < 6; day++) {
            s.raiseThreat(100);
            days(s, 1);
        }
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
        for (int day = 0; day < 8; day++) {
            s.raiseThreat(100);
            days(s, 1);
        }
        assertEquals(0, guards(s));
    }

    @Test
    void aGuardEatsAnExtraRationAndWearsOutTools() {
        Settlement s = village();
        Resident guard = person(Occupation.GUARD, 80);
        s.addResident(guard);
        registry.add(s);
        s.ledger().take(ResourceType.TOOLS, 50);
        s.ledger().add(ResourceType.TOOLS, 40);
        // With too little for spoilage to matter, and a twin whose resident is an idler
        // instead of a guard: the difference between them is exactly the guard's extra ration.
        s.ledger().take(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.FOOD, 48); // under 50, where 2% spoilage rounds to nothing, and enough for ten days
        Settlement idler = SettlementCodec.decode(SettlementCodec.encode(s));
        idler.residents().forEach(r -> r.setOccupation(Occupation.NITWIT));
        int toolsBefore = s.ledger().get(ResourceType.TOOLS);
        StringBuilder trace = new StringBuilder();
        // Three days: on the fourth the idler's village has had danger long enough to call up a guard of its own.
        for (int i = 0; i < 3; i++) {
            dangerousDays(s, 1);
            dangerousDays(idler, 1);
            trace.append(String.format("d%d guard food=%d idler food=%d guards=%d; ", i + 1, s.ledger().get(ResourceType.FOOD),
                    idler.ledger().get(ResourceType.FOOD), guards(s)));
        }
        assertEquals(3, idler.ledger().get(ResourceType.FOOD) - s.ledger().get(ResourceType.FOOD),
                "one extra food a day for three days: " + trace);
        assertTrue(s.ledger().get(ResourceType.TOOLS) <= toolsBefore);
        assertEquals(toolsBefore, idler.ledger().get(ResourceType.TOOLS), "an idler wears nothing out");
    }

    @Test
    void aGuardWithoutFoodCannotStandWatch() {
        Settlement s = village();
        s.ledger().take(ResourceType.FOOD, 400);
        Resident guard = person(Occupation.GUARD, 80);
        s.addResident(guard);
        registry.add(s);
        dangerousDays(s, 2);
        assertTrue(guard.lastBlockedDay() >= 0, "blocked for want of food");
    }

    @Test
    void guardsWithToolsCalmTheVillageFasterAndWithoutToolsDoNot() {
        Settlement base = village(Occupation.NITWIT, Occupation.NITWIT);
        base.addResident(person(Occupation.GUARD, 80));
        base.addResident(person(Occupation.GUARD, 80));
        registry.add(base);
        base.raiseThreat(80);
        Map<String, Object> saved = SettlementCodec.encode(base);

        Settlement armed = SettlementCodec.decode(saved);
        Settlement bare = SettlementCodec.decode(saved);
        bare.ledger().take(ResourceType.TOOLS, 1000);
        Settlement noGuards = SettlementCodec.decode(saved);
        noGuards.residents().forEach(r -> {
            if (r.occupation() == Occupation.GUARD) {
                r.setOccupation(Occupation.NITWIT);
            }
        });
        days(armed, 3);
        StringBuilder trace = new StringBuilder();
        // Three days: on the fourth the unguarded village has had danger long enough to call up a guard of its own.
        for (int i = 0; i < 3; i++) {
            days(bare, 1);
            trace.append(String.format("d%d threat=%.3f tools=%d guards=%d; ", i + 1, bare.threat(), bare.ledger().get(ResourceType.TOOLS), guards(bare)));
        }
        days(noGuards, 3);
        assertTrue(armed.threat() < noGuards.threat() - 3, armed.threat() + " vs " + noGuards.threat());
        assertEquals(noGuards.threat(), bare.threat(), 1e-9, "no tools, no difference: " + trace);
    }

    @Test
    void guardsStandDownOnceTheDangerHasPassedOneADay() {
        Settlement s = village(Occupation.NITWIT, Occupation.NITWIT);
        s.addResident(person(Occupation.GUARD, 80));
        s.addResident(person(Occupation.GUARD, 80));
        registry.add(s);
        // Threat is 0: calm.
        days(s, 1);
        assertEquals(1, guards(s), "one a day");
        days(s, 1);
        assertEquals(0, guards(s));
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("stood down")));
    }

    @Test
    void aGuardAndTheWatchSurviveASave() {
        Settlement s = village(Occupation.NITWIT, Occupation.NITWIT);
        s.addResident(person(Occupation.GUARD, 80));
        registry.add(s);
        s.raiseThreat(90);
        days(s, 1);
        assertTrue(s.hasCondition(SettlementSimulator.THREAT_HIGH_SINCE));
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(1, guards(loaded));
        assertTrue(loaded.hasCondition(SettlementSimulator.THREAT_HIGH_SINCE));
        assertTrue(Occupation.GUARD.simOwned());
    }
}
