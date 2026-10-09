package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.15: age, life stages, elders and death of old age. */
class AgingTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();

    private static Resident person(Occupation job, boolean adult, long bornDay) {
        return new Resident(UUID.randomUUID(), "Test", "Person", Gender.FEMALE, new Traits(50, 50, 50, 50),
                job, adult, bornDay, null, null, Needs.initial());
    }

    @Test
    void stagesFollowAge() {
        Resident r = person(Occupation.FARMER, true, 100);
        assertEquals(0, r.age(90)); // a clock that went backwards does not make anyone negative
        assertEquals(LifeStage.ADULT, r.stage(100 + Resident.ELDER_AGE - 1));
        assertEquals(LifeStage.ELDER, r.stage(100 + Resident.ELDER_AGE));
        assertEquals(LifeStage.CHILD, person(Occupation.UNEMPLOYED, false, 100).stage(100 + 500));
        assertTrue(r.maxAge() >= Resident.MAX_AGE_MIN && r.maxAge() <= Resident.MAX_AGE_MAX);
        assertEquals(r.maxAge(), r.maxAge());
    }

    @Test
    void foundersGetVariedDeterministicAgesAndNeverArriveAsElders() {
        Settlement s = registry.found("world", 0, 0, 100);
        Set<Long> ages = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            Resident r = registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 100, null, null);
            assertEquals(LifeStage.ADULT, r.stage(100));
            ages.add(r.age(100));
        }
        assertTrue(ages.size() > 10, "ages should vary, got " + ages.size());

        UUID id = UUID.randomUUID();
        SettlementRegistry a = new SettlementRegistry();
        SettlementRegistry b = new SettlementRegistry();
        assertEquals(a.enroll(a.found("w", 0, 0, 100), id, Occupation.FARMER, true, 100, null, null).bornDay(),
                b.enroll(b.found("w", 0, 0, 100), id, Occupation.FARMER, true, 100, null, null).bornDay());
        // A child's age starts today.
        assertEquals(100, registry.enroll(s, UUID.randomUUID(), Occupation.UNEMPLOYED, false, 100, null, null).bornDay());
    }

    @Test
    void eldersProduceLessThanAdults() {
        int adults = foodMadeByTwentyFarmersBornOn(100); // age 0 and rising: adults throughout
        int elders = foodMadeByTwentyFarmersBornOn(-65); // elders, and not yet at their maximum age
        assertTrue(elders < adults * 0.8, adults + " adults vs " + elders + " elders");
        assertTrue(elders > 0);
    }

    private int foodMadeByTwentyFarmersBornOn(long bornDay) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 20; i++) {
            s.addResident(person(Occupation.FARMER, true, bornDay));
        }
        int total = 0;
        for (int day = 1; day <= 10; day++) {
            simulator.simulateTo(s, day, 100);
            total += s.flow().produced(ResourceType.FOOD, day);
        }
        assertEquals(20, s.population(), "nobody should die of old age during this run");
        return total;
    }

    @Test
    void aResidentPastTheirMaximumAgeDiesAndIsRecorded() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident old = person(Occupation.FARMER, true, 0);
        Resident young = person(Occupation.FARMER, true, 1000);
        s.addResident(old);
        s.addResident(young);
        s.ledger().add(ResourceType.FOOD, 100);

        simulator.simulateTo(s, old.maxAge() - 1, 200);
        assertEquals(2, s.population());
        simulator.simulateTo(s, old.maxAge(), 200);

        assertEquals(1, s.population());
        assertTrue(s.resident(old.id()).isEmpty());
        assertTrue(s.hasDeparted(old.id()));
        assertTrue(s.history().stream().anyMatch(e -> e.kind() == HistoryEvent.Kind.DEATH
                && e.text().contains("died of old age") && e.text().contains(old.fullName())));
        assertEquals(List.of(old.id()), s.drainNewlyDeparted());
        assertTrue(s.drainNewlyDeparted().isEmpty());
    }

    @Test
    void childrenDoNotDieOfOldAgeAndTheRegistryRemembersTheDeparted() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident child = person(Occupation.UNEMPLOYED, false, 0);
        Resident old = person(Occupation.FARMER, true, 0);
        s.addResident(child);
        s.addResident(old);
        registry.add(s);
        simulator.simulateTo(s, 150, 200);
        assertTrue(s.resident(child.id()).isPresent());
        assertTrue(registry.isDeparted(old.id()));
        assertFalse(registry.isDeparted(child.id()));
        assertEquals(List.of(old.id()), registry.reapDeparted(s));
        assertTrue(registry.resident(old.id()).isEmpty());
    }

    @Test
    void departedIdsSurviveSavingAndAreNeverForgotten() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident old = person(Occupation.FARMER, true, 0);
        s.addResident(old);
        s.addResident(person(Occupation.FARMER, true, 10_000)); // someone lives here, so it is not abandoned
        simulator.simulateTo(s, 150, 200);
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertTrue(loaded.hasDeparted(old.id()));

        // Still remembered a long time on: a villager that stayed unloaded must not return as a stranger.
        simulator.simulateTo(loaded, 2_000, 2_000);
        assertTrue(loaded.hasDeparted(old.id()));
    }

    @Test
    void oldAgeDeathsCanBeSwitchedOffAndEldersStillSlowDown() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident old = person(Occupation.FARMER, true, 0);
        s.addResident(old);
        SettlementSimulator gentle = SettlementSimulator.withOldAgeDeaths(false);
        gentle.simulateTo(s, 300, 400);
        assertEquals(1, s.population());
        assertFalse(s.hasDeparted(old.id()));
        assertEquals(LifeStage.ELDER, old.stage(300));
    }

    @Test
    void lifespanIsStableAcrossACureAndACuredResidentIsNotDueToDie() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident turned = person(Occupation.FARMER, true, 0);
        s.addResident(turned);
        registry.add(s);
        UUID zombie = UUID.randomUUID();
        UUID cured = UUID.randomUUID();
        registry.turn(turned.id(), zombie);
        simulator.simulateTo(s, 500, 600); // long a zombie: far past any lifespan
        Resident back = registry.cure(zombie, cured).orElseThrow();
        assertEquals(turned.maxAge(), back.maxAge());
        assertTrue(back.maxAge() - back.age(500) >= Resident.CURE_GRACE_DAYS);
        simulator.simulateTo(s, 501, 600);
        assertTrue(s.resident(cured).isPresent(), "a freshly cured villager should not die of old age at once");
    }

    private static Map<String, Object> resident(UUID id, boolean adult, long bornDay) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id.toString());
        r.put("givenName", "Anya");
        r.put("familyName", "Hand");
        r.put("gender", "FEMALE");
        r.put("occupation", "FARMER");
        r.put("adult", adult);
        r.put("bornDay", bornDay);
        r.put("lastBlockedDay", -1);
        r.put("traits", List.of(50, 50, 50, 50));
        r.put("needs", List.of(80, 80, 60));
        r.put("familiarity", new LinkedHashMap<>());
        return r;
    }

    @Test
    void aFormatFiveSaveKeepsChildrenAndYoungAdultsAndRebasesAncientOnes() {
        UUID ancient = UUID.randomUUID();
        UUID recent = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        Map<String, Object> save = new LinkedHashMap<>();
        save.put("format", 5);
        save.put("id", UUID.randomUUID().toString());
        save.put("name", "Oldford");
        save.put("world", "world");
        save.put("centerX", 0);
        save.put("centerZ", 0);
        save.put("foundedDay", 0);
        save.put("lastSimulatedDay", 500);
        save.put("threat", 0.0);
        save.put("stock", new LinkedHashMap<>());
        save.put("treasury", 0);
        save.put("conditions", new LinkedHashMap<>());
        List<Object> residents = new ArrayList<>();
        residents.add(resident(ancient, true, 0));
        residents.add(resident(recent, true, 480));
        residents.add(resident(child, false, 0)); // flagged child, but 500 days old: it has grown up
        save.put("residents", residents);
        save.put("turned", new LinkedHashMap<>());
        save.put("history", new ArrayList<>());

        Settlement loaded = SettlementCodec.decode(save);
        Resident a = loaded.resident(ancient).orElseThrow();
        assertTrue(a.age(500) <= Resident.ADULT_AGE_MAX && a.age(500) >= Resident.ADULT_AGE_MIN, "age " + a.age(500));
        assertEquals(480, loaded.resident(recent).orElseThrow().bornDay()); // nobody is made older
        Resident grownUp = loaded.resident(child).orElseThrow();
        assertTrue(grownUp.age(500) <= Resident.ADULT_AGE_MAX, "stale child flag age " + grownUp.age(500));
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(loaded).get("format"));

        // Nobody dies of old age the moment an old save loads, even once the child flag catches up.
        grownUp.setAdult(true);
        new SettlementSimulator().simulateTo(loaded, 501, 100);
        assertEquals(3, loaded.population());
        assertNotEquals(0, a.bornDay());

        // Deterministic: the same old save rebased twice gives the same ages.
        assertEquals(a.bornDay(), SettlementCodec.decode(save).resident(ancient).orElseThrow().bornDay());
    }

    @Test
    void anElderMayTalkAboutTheirYears() {
        Settlement s = registry.found("world", 0, 0, 100);
        Resident elder = person(Occupation.FARMER, true, 30);
        s.addResident(elder);
        boolean said = false;
        Random random = new Random(1); // one generator: sequential seeds start with near-identical draws
        for (int i = 0; i < 300 && !said; i++) {
            said = Dialogue.smallTalk(elder, s, 100, random).contains(elder.age(100) + " days");
        }
        assertTrue(said);
    }

    @Test
    void lifespanScaleStretchesEveryAgeTogether() {
        Resident.setLifespanScale(20);
        try {
            assertEquals(1200, Resident.elderAge());
            Resident r = person(Occupation.FARMER, true, 0);
            assertTrue(r.maxAge() >= 1800 && r.maxAge() <= 2200, "max age " + r.maxAge());
            assertEquals(LifeStage.ADULT, r.stage(1199));
            assertEquals(LifeStage.ELDER, r.stage(1200));

            // First residents start with a scaled age, so they are not babies next to a long life.
            Settlement s = registry.found("world", 0, 0, 5000);
            for (int i = 0; i < 50; i++) {
                Resident founder = registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 5000, null, null);
                assertTrue(founder.age(5000) >= 240 && founder.age(5000) <= 1020, "age " + founder.age(5000));
            }

            // Nobody dies at the old, unscaled age.
            Settlement old = registry.found("world", 100, 100, 0);
            old.addResident(person(Occupation.FARMER, true, 0));
            simulator.simulateTo(old, 150, 200);
            assertEquals(1, old.population());
        } finally {
            Resident.setLifespanScale(1.0);
        }
        assertEquals(60, Resident.elderAge());
    }
}
