package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** R9.4: the brain module's behaviour registry: families, switches, budgets and the near-a-player rule. */
class BrainBehavioursTest {
    private static final List<BrainBehaviours.Spec> SPECS = List.of(
            new BrainBehaviours.Spec("fight", BrainFamily.GUARDS, 800),
            new BrainBehaviours.Spec("retreat", BrainFamily.GUARDS, 200),
            new BrainBehaviours.Spec("tend", BrainFamily.WORK, 300),
            new BrainBehaviours.Spec("attention", BrainFamily.STEWARDS, 500));

    // ----- the registry -----

    @Test
    void everyBehaviourHasAFamilyADebugNameAndABudget() {
        BrainBehaviours b = new BrainBehaviours(SPECS);
        assertEquals("hamletfolk:guards/fight", b.spec("fight").orElseThrow().debugName());
        assertEquals(BrainFamily.GUARDS, b.spec("retreat").orElseThrow().family());
        assertEquals(300, b.budgetMicros("tend"));
        assertEquals(List.of("fight", "retreat"), b.members(BrainFamily.GUARDS).stream().map(BrainBehaviours.Spec::name).toList());
        assertEquals(Optional.empty(), b.spec("dance"));
        assertThrows(IllegalArgumentException.class, () -> new BrainBehaviours(List.of(
                new BrainBehaviours.Spec("x", BrainFamily.WORK, 1), new BrainBehaviours.Spec("x", BrainFamily.GUARDS, 1))));
    }

    @Test
    void theShippedListIsWellFormed() {
        BrainBehaviours shipped = new BrainBehaviours(BrainBehaviours.ALL);
        Set<String> debugNames = new HashSet<>();
        for (BrainBehaviours.Spec s : BrainBehaviours.ALL) {
            assertTrue(s.name().matches("[a-z][a-z_]*"), "a config-friendly name: " + s.name());
            assertTrue(s.budgetMicros() > 0 && s.budgetMicros() <= 5_000, "a budget under a tenth of a tick: " + s.name());
            assertTrue(debugNames.add(s.debugName()));
        }
        assertTrue(shipped.runs("attention"), "the test behaviour (stewards) is on by default");
    }

    @Test
    void allAreOnByDefaultAndRunInOrder() {
        BrainBehaviours b = new BrainBehaviours(SPECS);
        for (BrainFamily f : BrainFamily.values()) {
            assertTrue(b.familyOn(f));
        }
        assertEquals(List.of("fight", "retreat", "tend", "attention"), List.copyOf(b.running()));
        assertEquals("", b.whyNot("fight"));
    }

    @Test
    void aBehaviourCanBeSwitchedOffAloneAndOnAgain() {
        BrainBehaviours b = new BrainBehaviours(SPECS);
        assertTrue(b.setBehaviour("fight", false));
        assertFalse(b.runs("fight"));
        assertTrue(b.runs("retreat"), "its family carries on");
        assertEquals("switched off", b.whyNot("fight"));
        assertEquals(List.of("retreat", "tend", "attention"), List.copyOf(b.running()));
        assertTrue(b.setBehaviour("fight", true));
        assertTrue(b.runs("fight"));
        assertFalse(b.setBehaviour("dance", false), "no such behaviour");
    }

    @Test
    void aWholeFamilyCanBeSwitchedOffWhileTheOthersRun() {
        BrainBehaviours b = new BrainBehaviours(SPECS);
        List<BrainBehaviours.Spec> affected = b.setFamily(BrainFamily.GUARDS, false);
        assertEquals(List.of("fight", "retreat"), affected.stream().map(BrainBehaviours.Spec::name).toList());
        assertEquals(List.of("tend", "attention"), List.copyOf(b.running()));
        assertEquals("the guards family is off", b.whyNot("fight"));
        b.setFamily(BrainFamily.GUARDS, true);
        assertTrue(b.runs("fight") && b.runs("retreat"));
        // a behaviour switched off alone stays off when its family comes back on
        b.setBehaviour("retreat", false);
        b.setFamily(BrainFamily.GUARDS, false);
        b.setFamily(BrainFamily.GUARDS, true);
        assertTrue(b.runs("fight"));
        assertFalse(b.runs("retreat"));
    }

    @Test
    void aBehaviourOverBudgetStopsAloneIsLoggedOnceAndCanBeTriedAgain() {
        BrainBehaviours b = new BrainBehaviours(SPECS);
        Optional<String> first = b.overBudget("tend", "12 of the last 100 ticks over");
        assertTrue(first.isPresent());
        assertTrue(first.get().contains("hamletfolk:work/tend") && first.get().contains("300 microseconds") && first.get().contains("12 of the last 100"),
                first.get());
        assertFalse(b.runs("tend"));
        assertTrue(b.runs("fight") && b.runs("attention"), "the others carry on");
        assertTrue(b.whyNot("tend").startsWith("over its budget"), b.whyNot("tend"));
        assertEquals(Optional.empty(), b.overBudget("tend", "again"), "logged once");
        b.setBehaviour("tend", true);
        assertTrue(b.runs("tend"), "switching it on tries it again");
        assertTrue(b.overBudget("tend", "and again").isPresent(), "and a new trip is logged");
        b.setFamily(BrainFamily.WORK, true);
        assertTrue(b.runs("tend"), "switching its family on tries it again too");
        assertEquals(Optional.empty(), b.overBudget("dance", "x"));
    }

    @Test
    void budgetsCanBeSetFromTheConfig() {
        BrainBehaviours b = new BrainBehaviours(SPECS);
        b.setBudgetMicros("fight", 1200);
        assertEquals(1200, b.budgetMicros("fight"));
        b.setBudgetMicros("fight", -5);
        assertEquals(0, b.budgetMicros("fight"), "no budget, not a negative one");
        b.setBudgetMicros("dance", 10);
        assertEquals(0, b.budgetMicros("dance"));
        assertTrue(b.describe().get(0).startsWith("hamletfolk:guards/fight: on, budget 0 microseconds"), b.describe().get(0));
    }

    // ----- near a player -----

    @Test
    void villagersGetTheirBehavioursNearAPlayerAndKeepThemALittleFurther() {
        BrainBehaviours b = new BrainBehaviours(SPECS);
        assertEquals(BrainBehaviours.DEFAULT_WATCH, b.watchDistance());
        assertTrue(b.keepWatching(false, 48), "within the distance");
        assertFalse(b.keepWatching(false, 48.5), "a villager without them gets them only within it");
        assertTrue(b.keepWatching(true, 60), "one with them keeps them a little further (no flicker at the edge)");
        assertTrue(b.keepWatching(true, 48 + BrainBehaviours.WATCH_MARGIN));
        assertFalse(b.keepWatching(true, 48 + BrainBehaviours.WATCH_MARGIN + 0.1));
        assertFalse(b.keepWatching(true, Double.POSITIVE_INFINITY), "no player at all: simulation only");
        b.setWatchDistance(20);
        assertFalse(b.keepWatching(false, 30));
        b.setWatchDistance(0);
        assertEquals(1, b.watchDistance(), "never less than a block");
    }

    // ----- the budget guard -----

    @Test
    void oneSlowTickDoesNotTripTheBudgetButAPatternDoes() {
        BudgetGuard g = new BudgetGuard();
        long budget = 1_000;
        for (int i = 0; i < 500; i++) {
            assertFalse(g.closeTick(i % 50 == 0 ? 5_000 : 500, budget), "the odd slow tick, " + i);
        }
        assertFalse(g.tripped());
        BudgetGuard h = new BudgetGuard();
        int trippedAt = -1;
        for (int i = 0; i < BudgetGuard.OVER; i++) {
            if (h.closeTick(2_000, budget)) {
                trippedAt = i;
            }
        }
        assertEquals(BudgetGuard.OVER - 1, trippedAt, "the tenth over the budget trips it");
        assertTrue(h.tripped());
        assertFalse(h.closeTick(2_000, budget), "it says so once");
        h.reset();
        assertFalse(h.tripped());
        assertEquals(0, h.overCount());
    }

    @Test
    void slowTicksThatHaveLeftTheWindowNoLongerCount() {
        BudgetGuard g = new BudgetGuard();
        for (int i = 0; i < BudgetGuard.OVER - 1; i++) {
            g.closeTick(2_000, 1_000); // nine over
        }
        assertEquals(BudgetGuard.OVER - 1, g.overCount());
        for (int i = 0; i < BudgetGuard.WINDOW; i++) {
            assertFalse(g.closeTick(100, 1_000)); // a hundred under push them out
        }
        assertEquals(0, g.overCount());
        assertFalse(g.closeTick(2_000, 1_000), "one more over is one, not ten");
        assertFalse(g.closeTick(2_000, 0), "no budget: never trips");
    }

    @Test
    void theMeterJudgesWholeTicksSoManyQuickCallsAddUp() {
        BehaviourMeter m = new BehaviourMeter(100); // 100 microseconds a tick
        boolean tripped = false;
        for (long tick = 1; tick <= BehaviourMeter.WARM_UP + 20 && !tripped; tick++) {
            for (int villager = 0; villager < 40; villager++) {
                tripped |= m.record(tick, 5_000); // 5 microseconds a call, 40 villagers: 200 a tick
            }
        }
        assertTrue(tripped, "each call is cheap, but the tick is twice the budget");
        assertTrue(m.tripped());
        assertEquals(100, m.budgetMicros());
        assertTrue(m.stats().summary().calls() > 0);
        m.reset();
        assertFalse(m.tripped());
        assertEquals(0, m.stats().summary().calls());

        BehaviourMeter fresh = new BehaviourMeter(100);
        for (long tick = 1; tick <= BehaviourMeter.WARM_UP; tick++) {
            for (int villager = 0; villager < 40; villager++) {
                assertFalse(fresh.record(tick, 50_000), "slow while warming up: not judged");
            }
        }
        boolean later = false;
        for (long tick = BehaviourMeter.WARM_UP + 1; tick <= BehaviourMeter.WARM_UP + 20; tick++) {
            later |= fresh.record(tick, 500_000);
        }
        assertTrue(later, "judged once warmed up");
        m.reset();
        for (long tick = 1; tick <= BehaviourMeter.WARM_UP; tick++) {
            assertFalse(m.record(tick, 500_000), "switched on again: warms up again");
        }

        BehaviourMeter cheap = new BehaviourMeter(100);
        for (long tick = 1; tick <= 300; tick++) {
            for (int villager = 0; villager < 10; villager++) {
                assertFalse(cheap.record(tick, 5_000), "50 microseconds a tick is within 100");
            }
        }
    }

    @Test
    void statsReportTheTickACallClosed() {
        BrainStats s = new BrainStats();
        assertEquals(-1, s.record(1, 100), "the first call closes nothing");
        assertEquals(-1, s.record(1, 200));
        assertEquals(300, s.record(2, 50), "the first call of tick 2 closes tick 1, 300 in all");
        assertEquals(50, s.record(5, 10));
    }
}
