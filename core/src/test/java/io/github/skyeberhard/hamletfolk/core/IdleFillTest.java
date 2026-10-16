package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** R4.50: nobody idles while a workplace stands empty. */
class IdleFillTest {
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(140, next++), "T", "P" + next, Gender.FEMALE, new Traits(50, 50, 50, 80), job, true,
                -30, null, null, Needs.initial());
    }

    /** Four jobless adults, food and everything else well stocked: nothing is short. */
    private Settlement wellStocked() {
        Settlement s = new Settlement(new UUID(141, 141), "Idleham", "world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(person(Occupation.UNEMPLOYED));
        }
        for (ResourceType type : ResourceType.values()) {
            s.ledger().add(type, Math.min(SettlementSimulator.capacity(s, type), 60));
        }
        return s;
    }

    private long count(Settlement s, Occupation job) {
        return s.residents().stream().filter(r -> r.occupation() == job).count();
    }

    @Test
    void anIdleHandTakesAFreePlaceWhenNothingIsShort() {
        Settlement s = wellStocked();
        SettlementSimulator onlyFarms = new SettlementSimulator(false, (Predicate<Occupation>) o -> o == Occupation.FARMER);
        for (long day = 1; day <= 6; day++) {
            onlyFarms.simulateDay(s, day);
        }
        assertEquals(4, count(s, Occupation.FARMER), "the farm is the only workplace, and nobody stays idle beside it");
        assertEquals(0, count(s, Occupation.UNEMPLOYED));
    }

    @Test
    void theTradeWithTheLeastInStoreIsTakenFirst() {
        Settlement s = wellStocked();
        s.ledger().take(ResourceType.GOODS, s.ledger().get(ResourceType.GOODS) - 13); // 13 of 12 wanted: not short, but least
        SettlementSimulator two = new SettlementSimulator(false, (Predicate<Occupation>) o -> o == Occupation.FARMER || o == Occupation.LEATHERWORKER);
        // (the leatherworker is only a candidate where the land opens it; here that is not so, so it stays with the farm)
        two.simulateDay(s, 1);
        assertEquals(1, count(s, Occupation.FARMER) + count(s, Occupation.LUMBERJACK) + count(s, Occupation.MINER)
                + count(s, Occupation.MASON) + count(s, Occupation.FISHERMAN) + count(s, Occupation.TOOLSMITH),
                "one a day takes a place");
    }

    @Test
    void nobodyTakesAPlaceWhoseProductTheStoresCannotHold() {
        Settlement s = wellStocked();
        s.removeResident(s.residents().iterator().next().id()); // under four, so no permanent lumberjack or miner (R4.24)
        for (ResourceType type : ResourceType.values()) {
            s.ledger().add(type, SettlementSimulator.capacity(s, type)); // every store full
        }
        SettlementSimulator all = new SettlementSimulator(false, o -> true);
        for (long day = 1; day <= 4; day++) {
            all.simulateDay(s, day);
        }
        // (food spoils a little every day, so there is always room for more of that; wood, stone and tools are full)
        assertEquals(0, count(s, Occupation.LUMBERJACK) + count(s, Occupation.MASON) + count(s, Occupation.MINER)
                + count(s, Occupation.TOOLSMITH), "nobody takes a place whose product the stores cannot hold");
    }

    @Test
    void aFamineLeavesTheIdleToForage() {
        Settlement s = wellStocked();
        s.conditions().put("famine", 0L);
        SettlementSimulator all = new SettlementSimulator(false, o -> o != Occupation.FARMER);
        all.simulateDay(s, 1);
        assertEquals(4, count(s, Occupation.UNEMPLOYED), "only food work will do in a famine, and there is no farm place");
    }
}
