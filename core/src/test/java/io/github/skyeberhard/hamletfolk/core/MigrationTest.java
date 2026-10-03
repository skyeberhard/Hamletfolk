package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.2: unemployed or unhappy residents leave for a better-off settlement nearby, and both histories record it. */
class MigrationTest {
    private static final long DAY = 10_010;

    private final SettlementRegistry registry = new SettlementRegistry();
    private int next = 1;

    private Resident person(Occupation occupation, boolean adult, Needs needs) {
        return new Resident(new UUID(7, next++), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50), occupation, adult,
                10_000, null, null, needs);
    }

    /** A settlement at (x, 0) with {@code people} content farmers, {@code food} in store and {@code beds} beds. */
    private Settlement village(String world, int x, int people, int food, int beds) {
        // A fixed id (not registry.found's random one) so the day-by-day draws are the same on every run.
        Settlement s = new Settlement(new UUID(9, next), "Town" + next++, world, x, 0, 10_000);
        for (int i = 0; i < people; i++) {
            s.addResident(person(Occupation.FARMER, true, Needs.initial()));
        }
        registry.add(s); // index the residents
        s.ledger().add(ResourceType.FOOD, food);
        s.housing().setChunk(0, 0, beds);
        return s;
    }

    private Resident addJobless(Settlement s) {
        Resident r = person(Occupation.UNEMPLOYED, true, Needs.initial());
        s.addResident(r);
        registry.add(s);
        return r;
    }

    /** Runs the check for many days and returns the first move. */
    private Optional<Migration.Move> untilSomeoneGoes(Settlement from) {
        for (long day = DAY; day < DAY + 200; day++) {
            Optional<Migration.Move> move = Migration.run(registry, from, day);
            if (move.isPresent()) {
                return move;
            }
        }
        return Optional.empty();
    }

    @Test
    void aJoblessResidentLeavesForABetterOffNeighbour() {
        Settlement poor = village("world", 0, 3, 0, 10);
        Settlement rich = village("world", 200, 4, 400, 10);
        Resident jobless = addJobless(poor);

        Migration.Move move = untilSomeoneGoes(poor).orElseThrow();

        assertEquals(jobless.id(), move.resident().id());
        assertEquals(rich, move.to());
        assertTrue(rich.resident(jobless.id()).isPresent());
        assertTrue(poor.resident(jobless.id()).isEmpty());
        assertEquals(rich, registry.settlementOf(jobless.id()).orElseThrow(), "the registry follows them");
        assertEquals(Occupation.UNEMPLOYED, jobless.occupation());
        assertTrue(rich.hasCondition(Migration.MOVING + jobless.id()), "marked for the Minecraft layer to carry out");
        assertTrue(poor.history().stream().anyMatch(e -> e.kind() == HistoryEvent.Kind.DEPARTURE
                && e.text().contains(rich.name())));
        assertTrue(rich.history().stream().anyMatch(e -> e.kind() == HistoryEvent.Kind.ARRIVAL
                && e.text().contains(poor.name())));
    }

    @Test
    void anUnhappyWorkerLeavesAndLosesTheirJob() {
        Settlement poor = village("world", 0, 3, 0, 10);
        village("world", 200, 4, 400, 10);
        Resident miserable = person(Occupation.FARMER, true, new Needs(10, 20, 10));
        poor.addResident(miserable);
        registry.add(poor);
        Migration.Move move = untilSomeoneGoes(poor).orElseThrow();
        assertEquals(miserable.id(), move.resident().id());
        assertEquals(Occupation.UNEMPLOYED, miserable.occupation(), "there is no farm for them where they are going");
    }

    @Test
    void contentWorkersChildrenAndEldersStay() {
        Settlement poor = village("world", 0, 3, 0, 10); // three content farmers
        village("world", 200, 4, 400, 10);
        poor.addResident(person(Occupation.UNEMPLOYED, false, Needs.initial())); // a child
        // An elder: born long ago.
        poor.addResident(new Resident(new UUID(8, 1), "E", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                Occupation.UNEMPLOYED, true, DAY - Resident.elderAge() - 5, null, null, Needs.initial()));
        registry.add(poor);
        assertTrue(untilSomeoneGoes(poor).isEmpty());
    }

    @Test
    void onlyOneLeavesADayAndTheCheckIsOncePerDay() {
        Settlement poor = village("world", 0, 3, 0, 10);
        village("world", 200, 4, 400, 10);
        for (int i = 0; i < 6; i++) {
            addJobless(poor);
        }
        int before = poor.population();
        Migration.run(registry, poor, DAY);
        assertTrue(before - poor.population() <= 1);
        int after = poor.population();
        assertTrue(Migration.run(registry, poor, DAY).isEmpty(), "already checked today");
        assertEquals(after, poor.population());
    }

    @Test
    void nobodyLeavesForAPlaceThatIsNotBetterOpenAndNear() {
        // Not better off by enough.
        Settlement a = village("world", 0, 3, 100, 10);
        village("world", 200, 4, 110, 10);
        addJobless(a);
        assertTrue(untilSomeoneGoes(a).isEmpty(), "similar food: not worth the move");

        // Too far.
        Settlement b = village("far", 0, 3, 0, 10);
        village("far", Migration.MAX_DISTANCE + 50, 4, 400, 10);
        addJobless(b);
        assertTrue(untilSomeoneGoes(b).isEmpty());

        // Another world.
        Settlement c = village("here", 0, 3, 0, 10);
        village("there", 100, 4, 400, 10);
        addJobless(c);
        assertTrue(untilSomeoneGoes(c).isEmpty());

        // No free bed.
        Settlement d = village("full", 0, 3, 0, 10);
        village("full", 100, 4, 400, 4);
        addJobless(d);
        assertTrue(untilSomeoneGoes(d).isEmpty());

        // In famine.
        Settlement e = village("hungry", 0, 3, 0, 10);
        Settlement famine = village("hungry", 100, 4, 400, 10);
        famine.conditions().put("famine", DAY);
        addJobless(e);
        assertTrue(untilSomeoneGoes(e).isEmpty());
    }

    @Test
    void aSmallVillageIsNotEmptied() {
        Settlement poor = village("world", 0, 0, 0, 10);
        village("world", 200, 4, 400, 10);
        addJobless(poor);
        addJobless(poor); // two people, both jobless: not enough to leave anyone behind
        assertTrue(untilSomeoneGoes(poor).isEmpty());
        assertEquals(2, poor.population());
    }

    @Test
    void theNearestBetterOffNeighbourWins() {
        Settlement poor = village("world", 0, 3, 0, 10);
        Settlement far = village("world", 500, 4, 400, 10);
        Settlement near = village("world", 100, 4, 300, 10);
        addJobless(poor);
        Migration.Move move = untilSomeoneGoes(poor).orElseThrow();
        assertEquals(near, move.to());
        assertFalse(far.history().stream().anyMatch(e -> e.kind() == HistoryEvent.Kind.ARRIVAL));
    }

    @Test
    void theMoveSurvivesASave() {
        Settlement poor = village("world", 0, 3, 0, 10);
        Settlement rich = village("world", 200, 4, 400, 10);
        Resident jobless = addJobless(poor);
        untilSomeoneGoes(poor).orElseThrow();
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(rich));
        assertTrue(loaded.resident(jobless.id()).isPresent());
        assertTrue(loaded.hasCondition(Migration.MOVING + jobless.id()), "the pending move is saved with the settlement");
        assertFalse(SettlementCodec.decode(SettlementCodec.encode(poor)).resident(jobless.id()).isPresent());
        assertEquals(java.util.List.of(jobless.id()), loaded.pendingMoves());
        loaded.removeCondition(Migration.MOVING + jobless.id());
        assertTrue(loaded.pendingMoves().isEmpty());
    }

    @Test
    void aFormatTwelveSaveStillLoads() {
        Settlement s = village("world", 0, 3, 50, 4);
        java.util.Map<String, Object> old = new java.util.LinkedHashMap<>(SettlementCodec.encode(s));
        old.put("format", 12); // before the DEPARTURE history kind existed
        Settlement loaded = SettlementCodec.decode(old);
        assertEquals(3, loaded.population());
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(loaded).get("format"));
    }
}
