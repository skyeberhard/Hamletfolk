package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** R1.2: a villager infected and then cured is the same person, and history records the cure. */
class InfectionAndCureTest {
    private SettlementRegistry registry;
    private Settlement settlement;
    private Resident harold;
    private final UUID player = UUID.randomUUID();
    private final UUID zombie = UUID.randomUUID();
    private final UUID curedVillager = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registry = new SettlementRegistry();
        settlement = registry.found("world", 0, 0, 0);
        harold = registry.enroll(settlement, UUID.randomUUID(), Occupation.TOOLSMITH, true, 0, null, null);
        harold.recordConversation(player);
        harold.recordConversation(player);
    }

    @Test
    void curedVillagerKeepsWhatTheyHadPutBy() {
        harold.addWealth(777); // R3.5
        registry.turn(harold.id(), zombie);
        Settlement reloaded = SettlementCodec.decode(SettlementCodec.encode(settlement)); // a zombie is saved with its wealth
        assertEquals(777, reloaded.turned().get(zombie).wealth());
        assertEquals(777, registry.cure(zombie, curedVillager).orElseThrow().wealth());
    }

    @Test
    void curedVillagerKeepsTheirIdentity() {
        registry.turn(harold.id(), zombie);
        Resident cured = registry.cure(zombie, curedVillager).orElseThrow();

        assertEquals(curedVillager, cured.id());
        assertEquals(harold.fullName(), cured.fullName());
        assertEquals(harold.traits(), cured.traits());
        assertEquals(harold.occupation(), cured.occupation());
        assertEquals(harold.bornDay(), cured.bornDay());
        assertEquals(2, cured.familiarityWith(player));
        assertSame(settlement, registry.settlementOf(curedVillager).orElseThrow());
        assertTrue(registry.resident(harold.id()).isEmpty());
    }

    @Test
    void turnedResidentsLeaveThePopulationUntilCured() {
        registry.turn(harold.id(), zombie);
        assertEquals(0, settlement.population());
        assertEquals(1, settlement.turnedCount());
        assertEquals(harold.fullName(), registry.turnedResident(zombie).orElseThrow().fullName());

        registry.cure(zombie, curedVillager);
        assertEquals(1, settlement.population());
        assertEquals(0, settlement.turnedCount());
    }

    @Test
    void killedZombieCannotBeCuredLater() {
        registry.turn(harold.id(), zombie);
        assertEquals(harold.fullName(), registry.forgetTurned(zombie).orElseThrow().fullName());
        assertTrue(registry.cure(zombie, curedVillager).isEmpty());
        assertEquals(0, settlement.turnedCount());
    }

    @Test
    void curingAStrangersZombieIsNotACure() {
        assertTrue(registry.cure(UUID.randomUUID(), curedVillager).isEmpty());
    }

    @Test
    void turnedResidentsSurviveARestart() {
        registry.turn(harold.id(), zombie);
        SettlementRegistry reloaded = new SettlementRegistry();
        reloaded.add(SettlementCodec.decode(SettlementCodec.encode(settlement)));

        Resident cured = reloaded.cure(zombie, curedVillager).orElseThrow();
        assertEquals(harold.fullName(), cured.fullName());
        assertEquals(harold.traits(), cured.traits());
    }
}
