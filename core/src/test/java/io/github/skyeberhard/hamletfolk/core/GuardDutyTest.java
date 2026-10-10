package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R9.3: who is on guard duty, when a guard retreats, and which monster it goes for. */
class GuardDutyTest {
    private static GuardDuty.Foe foe(int id, double x, double z) {
        return new GuardDuty.Foe(id, x, 64, z);
    }

    private static Resident resident(Occupation job) {
        return new Resident(UUID.randomUUID(), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, 50),
                job, true, 0, null, null, Needs.initial());
    }

    private static Optional<GuardDuty.Foe> choose(List<GuardDuty.Foe> foes, GuardDuty.Foe current) {
        return GuardDuty.choose(foes, 0, 64, 0, 0, 0, current);
    }

    @Test
    void aGuardIsOnDutyOnlyWhileTheStoresHoldTools() {
        Settlement village = new Settlement(UUID.randomUUID(), "Testford", "world", 0, 0, 0);
        Resident guard = resident(Occupation.GUARD);
        Resident farmer = resident(Occupation.FARMER);
        assertFalse(GuardDuty.onDuty(village, guard), "no tools: no fighting");
        village.ledger().add(ResourceType.TOOLS, 1);
        assertTrue(GuardDuty.onDuty(village, guard));
        assertFalse(GuardDuty.onDuty(village, farmer), "only guards fight");
        village.ledger().take(ResourceType.TOOLS, 1);
        assertFalse(GuardDuty.onDuty(village, guard), "the last tool gone: it stops");
        village.ledger().add(ResourceType.TOOLS, 1);
        Resident child = new Resident(UUID.randomUUID(), "Test", "Child", Gender.MALE, new Traits(50, 50, 50, 50),
                Occupation.GUARD, false, 0, null, null, Needs.initial());
        assertFalse(GuardDuty.onDuty(village, child), "a child never fights");
    }

    @Test
    void aMonsterUnderTheFloorOrOnARoofIsNotItsBusiness() {
        assertEquals(Optional.empty(), choose(List.of(new GuardDuty.Foe(1, 3, 58, 0)), null));
        assertEquals(1, choose(List.of(new GuardDuty.Foe(1, 3, 61, 0)), null).orElseThrow().id());
    }

    @Test
    void itHoldsItsPostUntilItIsBackOrAWhileHasPassed() {
        assertTrue(GuardDuty.keepsPost(20, 100), "it chased a long way a moment ago: the post holds");
        assertFalse(GuardDuty.keepsPost(2, 100), "back at it");
        assertFalse(GuardDuty.keepsPost(20, GuardDuty.POST_TICKS), "long enough: a new post where it stands");
    }

    @Test
    void aGuardHealsSlowlyOnlyWhenNotFighting() {
        assertFalse(GuardDuty.heals(true, 10_000));
        assertFalse(GuardDuty.heals(false, GuardDuty.HEAL_TICKS - 1));
        assertTrue(GuardDuty.heals(false, GuardDuty.HEAL_TICKS));
        assertTrue(20 / GuardDuty.HEAL * GuardDuty.HEAL_TICKS <= 20 * 60 * 3, "a badly hurt guard is back on duty within minutes");
    }

    @Test
    void aBadlyHurtGuardRetreatsUntilItHasSomeHealthBack() {
        assertFalse(GuardDuty.retreats(1.0, false));
        assertFalse(GuardDuty.retreats(0.4, false), "hurt, but fighting on");
        assertTrue(GuardDuty.retreats(0.3, false), "badly hurt: retreat");
        assertTrue(GuardDuty.retreats(0.5, true), "not back in the fight at half health");
        assertFalse(GuardDuty.retreats(0.6, true), "back at 60%");
    }

    @Test
    void itGoesForTheNearestMonsterItCanSeeAndNotOneFarFromItsPost() {
        assertEquals(Optional.empty(), choose(List.of(), null));
        assertEquals(2, choose(List.of(foe(1, 10, 0), foe(2, 5, 0)), null).orElseThrow().id());
        assertEquals(Optional.empty(), choose(List.of(foe(1, 20, 0)), null), "beyond what it notices");
        assertEquals(Optional.empty(), GuardDuty.choose(List.of(foe(1, 30, 0)), 20, 64, 0, 0, 0, null),
                "near the guard but past the leash from its post");
    }

    @Test
    void itKeepsToItsFoeUnlessAnotherIsMuchNearer() {
        GuardDuty.Foe current = foe(1, 8, 0);
        assertEquals(1, choose(List.of(foe(1, 8, 0), foe(2, 6, 0)), current).orElseThrow().id(), "no dithering");
        assertEquals(2, choose(List.of(foe(1, 8, 0), foe(2, 3, 0)), current).orElseThrow().id(), "one at its elbow wins");
        assertEquals(2, choose(List.of(foe(2, 9, 0)), current).orElseThrow().id(), "its foe is gone: the next");
    }

    @Test
    void onlyAGuardOnDutyGetsTheGuardBehaviour() {
        java.util.Set<String> running = java.util.Set.of("attention", "journey", GuardDuty.BEHAVIOUR);
        assertEquals(running, GuardDuty.fit(running, true));
        assertEquals(java.util.Set.of("attention", "journey"), GuardDuty.fit(running, false));
        assertTrue(BrainBehaviours.ALL.stream().anyMatch(s -> s.name().equals(GuardDuty.BEHAVIOUR) && s.family() == BrainFamily.GUARDS));
    }

    @Test
    void itStrikesOnlyWithinReach() {
        assertTrue(GuardDuty.inReach(2.0));
        assertFalse(GuardDuty.inReach(3.0));
    }
}
