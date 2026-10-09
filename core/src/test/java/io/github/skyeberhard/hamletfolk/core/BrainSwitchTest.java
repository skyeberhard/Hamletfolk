package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** R9.1: the brain module's on/off state machine and its self-check. */
class BrainSwitchTest {
    // ----- the switch -----

    @Test
    void itStartsOffAndSwitchesOnAndOff() {
        BrainSwitch s = new BrainSwitch();
        assertEquals(BrainSwitch.State.OFF, s.state());
        assertFalse(s.on());
        assertEquals(BrainSwitch.Action.ATTACH_ALL, s.requestOn());
        assertTrue(s.on());
        assertEquals(BrainSwitch.Action.NONE, s.requestOn(), "already on: nothing to add again");
        assertEquals(BrainSwitch.Action.DETACH_ALL, s.requestOff(), "off takes everything off");
        assertEquals(BrainSwitch.State.OFF, s.state());
        assertEquals(BrainSwitch.Action.NONE, s.requestOff(), "already off");
    }

    @Test
    void aFaultSwitchesItOffAndIsLoggedOnceNamingTheVillager() {
        BrainSwitch s = new BrainSwitch();
        s.requestOn();
        Optional<String> first = s.fault("Mira Oakes (farmer of Oakvale)", "IllegalStateException: no LOOK_TARGET");
        assertTrue(first.isPresent());
        assertTrue(first.get().contains("Mira Oakes") && first.get().contains("IllegalStateException"), first.get());
        assertEquals(BrainSwitch.State.FAILED, s.state());
        assertFalse(s.on(), "the behaviours stop acting at once");
        assertEquals(Optional.empty(), s.fault("Tom Ash", "the same fault on another villager"), "logged once");
        assertTrue(s.reason().contains("Mira Oakes"));
        // it can be tried again, and a new fault is logged again
        assertEquals(BrainSwitch.Action.ATTACH_ALL, s.requestOn());
        assertEquals("", s.reason());
        assertTrue(s.fault("Tom Ash", "again").isPresent());
    }

    @Test
    void offAfterAFaultHasNothingLeftToTakeOff() {
        BrainSwitch s = new BrainSwitch();
        s.requestOn();
        s.fault("Mira", "boom");
        assertEquals(BrainSwitch.Action.NONE, s.requestOff(), "the fault already took everything off");
        assertEquals(BrainSwitch.State.OFF, s.state());
        assertEquals("", s.reason());
    }

    @Test
    void aFaultWhileOffIsIgnored() {
        BrainSwitch s = new BrainSwitch();
        assertEquals(Optional.empty(), s.fault("Mira", "late callback from a behaviour being taken off"));
        assertEquals(BrainSwitch.State.OFF, s.state());
    }

    @Test
    void aFailedSelfCheckKeepsItOffForGood() {
        BrainSwitch s = new BrainSwitch();
        assertEquals(BrainSwitch.Action.NONE, s.selfCheckFailed(List.of("net.minecraft.world.entity.ai.Brain#availableBehaviorsByPriority")));
        assertEquals(BrainSwitch.State.UNAVAILABLE, s.state());
        assertTrue(s.reason().contains("availableBehaviorsByPriority"), s.reason());
        assertEquals(BrainSwitch.Action.NONE, s.requestOn(), "cannot be switched on");
        assertFalse(s.on());
        assertEquals(BrainSwitch.Action.NONE, s.requestOff());
        assertEquals(BrainSwitch.State.UNAVAILABLE, s.state(), "and stays unavailable");
        assertEquals(Optional.empty(), s.fault("Mira", "x"));

        BrainSwitch running = new BrainSwitch();
        running.requestOn();
        assertEquals(BrainSwitch.Action.DETACH_ALL, running.selfCheckFailed(List.of("x")), "a check failing while on takes everything off");
    }

    // ----- the self-check -----

    /** A server that has everything, with what is listed gone or changed (keys as {@link #key}). */
    private static BrainSelfCheck.Probe serverWithout(Set<String> gone) {
        return new BrainSelfCheck.Probe() {
            @Override
            public boolean hasClass(String name) {
                return !gone.contains(name);
            }

            @Override
            public boolean hasField(String owner, String name, String type) {
                return !gone.contains(owner + "#" + name);
            }

            @Override
            public boolean hasConstructor(String owner, List<String> parameters) {
                return !gone.contains(owner + "#<init>" + parameters);
            }

            @Override
            public boolean hasMethod(String owner, String name, List<String> parameters, String returns) {
                return !gone.contains(owner + "#" + name + parameters + returns);
            }
        };
    }

    @Test
    void everythingPresentMeansNothingMissing() {
        assertEquals(List.of(), BrainSelfCheck.missing(BrainSelfCheck.REQUIRED, serverWithout(Set.of())));
    }

    @Test
    void aMissingOrChangedFieldMethodConstructorOrClassIsNamedWithItsSignature() {
        List<String> missing = BrainSelfCheck.missing(BrainSelfCheck.REQUIRED, serverWithout(Set.of(
                "net.minecraft.world.entity.ai.Brain#availableBehaviorsByPriority",
                "net.minecraft.world.entity.ai.behavior.Behavior#canStillUse"
                        + "[net.minecraft.server.level.ServerLevel, net.minecraft.world.entity.LivingEntity, long]boolean",
                "net.minecraft.world.entity.ai.behavior.EntityTracker#<init>[net.minecraft.world.entity.Entity, boolean]")));
        assertEquals(List.of("net.minecraft.world.entity.ai.Brain#availableBehaviorsByPriority : Map",
                "net.minecraft.world.entity.ai.behavior.Behavior#canStillUse(ServerLevel, LivingEntity, long) -> boolean",
                "net.minecraft.world.entity.ai.behavior.EntityTracker(Entity, boolean)"), missing);

        List<String> moved = BrainSelfCheck.missing(BrainSelfCheck.REQUIRED,
                serverWithout(Set.of("net.minecraft.world.entity.npc.villager.Villager")));
        assertTrue(moved.contains("net.minecraft.world.entity.npc.villager.Villager"));
        assertTrue(moved.stream().anyMatch(m -> m.startsWith("net.minecraft.world.entity.npc.villager.Villager#getBrain")),
                "a member of a missing class is missing too: " + moved);
    }

    @Test
    void theRequirementsNameTypesAndReturnsSoAChangedSignatureIsCaught() {
        BrainSelfCheck.Requirement handle = BrainSelfCheck.REQUIRED.stream()
                .filter(r -> r.name().equals("getHandle")).findFirst().orElseThrow();
        assertEquals("net.minecraft.world.entity.npc.villager.Villager", handle.returns(),
                "CraftVillager has eight getHandle()s (bridges): the return type says which");
        BrainSelfCheck.Requirement behaviour = BrainSelfCheck.REQUIRED.stream()
                .filter(r -> r.kind() == BrainSelfCheck.Kind.CONSTRUCTOR && r.owner().endsWith(".Behavior")).findFirst().orElseThrow();
        assertEquals(List.of("java.util.Map", "int", "int"), behaviour.parameters());
        for (BrainSelfCheck.Requirement r : BrainSelfCheck.REQUIRED) {
            if (r.kind() == BrainSelfCheck.Kind.METHOD || r.kind() == BrainSelfCheck.Kind.FIELD) {
                assertFalse(r.returns().isBlank(), "a type for " + r.describe());
            }
            for (String p : r.parameters()) {
                assertFalse(p.contains("<"), "erased types only: " + r.describe());
            }
        }
    }

    @Test
    void aProbeThatThrowsCountsAsMissingAndNeverEscapes() {
        BrainSelfCheck.Probe broken = new BrainSelfCheck.Probe() {
            @Override
            public boolean hasClass(String name) {
                throw new NoClassDefFoundError(name);
            }

            @Override
            public boolean hasField(String owner, String name, String type) {
                return true;
            }

            @Override
            public boolean hasConstructor(String owner, List<String> parameters) {
                return true;
            }

            @Override
            public boolean hasMethod(String owner, String name, List<String> parameters, String returns) {
                return true;
            }
        };
        assertEquals(BrainSelfCheck.REQUIRED.size(), BrainSelfCheck.missing(BrainSelfCheck.REQUIRED, broken).size());
    }

    @Test
    void theListIsWellFormedWithNoRepeats() {
        Set<String> seen = new HashSet<>();
        for (BrainSelfCheck.Requirement r : BrainSelfCheck.REQUIRED) {
            assertTrue(seen.add(r.describe() + r.parameters()), "listed twice: " + r.describe());
            assertTrue(r.owner().startsWith("net.minecraft.") || r.owner().startsWith("org.bukkit.craftbukkit."), r.owner());
            if (r.kind() != BrainSelfCheck.Kind.CLASS) {
                assertTrue(BrainSelfCheck.REQUIRED.stream().anyMatch(c -> c.kind() == BrainSelfCheck.Kind.CLASS
                        && c.owner().equals(r.owner())), "its class is listed too: " + r.describe());
            }
        }
    }
}
