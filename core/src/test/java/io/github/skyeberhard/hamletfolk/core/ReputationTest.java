package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R3.4: each settlement keeps a reputation for each player that dialogue and prices reflect. */
class ReputationTest {
    private static final UUID SKYE = new UUID(1, 1);
    private static final UUID OTHER = new UUID(1, 2);

    private final SettlementRegistry registry = new SettlementRegistry();

    private Settlement village() {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 10; i++) {
            s.addResident(new Resident(new UUID(0, i + 1), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                    Occupation.NITWIT, true, 10_000, null, null, Needs.initial()));
        }
        s.ledger().add(ResourceType.WOOD, 30); // exactly what ten residents want: multiplier 1
        s.ledger().add(ResourceType.METAL, 30);
        return s;
    }

    @Test
    void strangersStartAtZeroAndEachPlayerIsTrackedSeparately() {
        Settlement s = village();
        assertEquals(0, s.reputationOf(SKYE));
        s.adjustReputation(SKYE, 12);
        assertEquals(12, s.reputationOf(SKYE));
        assertEquals(0, s.reputationOf(OTHER));
    }

    @Test
    void reputationIsClampedBothWays() {
        Settlement s = village();
        s.adjustReputation(SKYE, 500);
        assertEquals(Reputation.MAX, s.reputationOf(SKYE));
        s.adjustReputation(SKYE, -1000);
        assertEquals(Reputation.MIN, s.reputationOf(SKYE));
    }

    @Test
    void standingsHaveThresholds() {
        assertEquals(Reputation.Standing.STRANGER, Reputation.standing(0));
        assertEquals(Reputation.Standing.STRANGER, Reputation.standing(14));
        assertEquals(Reputation.Standing.FRIENDLY, Reputation.standing(15));
        assertEquals(Reputation.Standing.HONOURED, Reputation.standing(60));
        assertEquals(Reputation.Standing.STRANGER, Reputation.standing(-9));
        assertEquals(Reputation.Standing.WARY, Reputation.standing(-10));
        assertEquals(Reputation.Standing.HOSTILE, Reputation.standing(-50));
    }

    @Test
    void giftsAndFilledRequestsEarnAndKillingCostsMore() {
        // 20 wood is 3 emeralds' worth (6 units each); sticks and splitting earn nothing.
        assertEquals(3, Reputation.donationGain(ResourceType.WOOD, 20, false));
        assertEquals(0, Reputation.donationGain(ResourceType.WOOD, 5, false));
        assertEquals(7, Reputation.donationGain(ResourceType.GOODS, 7, true));
        assertEquals(4, Reputation.requestGain(4));
        assertEquals(10, Reputation.requestGain(500));
        assertEquals(0, Reputation.requestGain(-3));
        assertTrue(Reputation.KILLING > Reputation.requestGain(Integer.MAX_VALUE));
    }

    @Test
    void goodStandingMakesPricesBetterAndBadStandingWorse() {
        Settlement s = village();
        // Buying 60 metal-ish goods: a villager sells an iron ingot for 100 emeralds' worth of cost (a base of 40).
        int base = PriceModel.specialPriceDelta(s, "iron_ingot", "emerald", 40, 0);
        assertEquals(0, base);
        assertEquals(-4, PriceModel.specialPriceDelta(s, "iron_ingot", "emerald", 40, 100));
        assertEquals(4, PriceModel.specialPriceDelta(s, "iron_ingot", "emerald", 40, -100));
        // Selling: the village asks fewer logs for 1 emerald when it likes you, more when it does not.
        int liked = PriceModel.specialPriceDelta(s, "emerald", "oak_log", 40, 100);
        int disliked = PriceModel.specialPriceDelta(s, "emerald", "oak_log", 40, -100);
        assertTrue(liked < 0, "liked: " + liked);
        assertTrue(disliked > 0, "disliked: " + disliked);
        // The old signature is a stranger.
        assertEquals(base, PriceModel.specialPriceDelta(s, "iron_ingot", "emerald", 40));
    }

    @Test
    void greetingsReflectStanding() {
        Settlement s = village();
        Resident resident = s.residents().iterator().next();
        String stranger = Dialogue.greeting(resident, s, SKYE, "Skye");
        assertEquals(Dialogue.greeting(resident, SKYE, "Skye"), stranger);
        s.adjustReputation(SKYE, 80);
        String honoured = Dialogue.greeting(resident, s, SKYE, "Skye");
        assertTrue(honoured.contains("Skye"), honoured);
        assertNotEquals(stranger, honoured);
        assertTrue(Dialogue.library().lines(Situation.GREETING_HONOURED, Voice.of(resident, s, 0).tone()).stream()
                .anyMatch(l -> l.replace("{player}", "Skye").replace("{village}", s.name()).equals(honoured)), "from the honoured lines: " + honoured);
        s.adjustReputation(SKYE, -200);
        String hostile = Dialogue.greeting(resident, s, SKYE, "Skye");
        assertTrue(hostile.contains("Skye"), hostile);
        assertNotEquals(honoured, hostile);
        assertTrue(Dialogue.library().lines(Situation.GREETING_HOSTILE, Voice.of(resident, s, 0).tone()).stream()
                .anyMatch(l -> l.replace("{player}", "Skye").replace("{village}", s.name()).equals(hostile)), "from the hostile lines: " + hostile);
        assertEquals(stranger, Dialogue.greeting(resident, s, OTHER, "Other"));
    }

    @Test
    void reputationSurvivesASaveAndAnOldSaveHasNone() {
        Settlement s = village();
        s.adjustReputation(SKYE, 33);
        s.adjustReputation(OTHER, -40);
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(33, loaded.reputationOf(SKYE));
        assertEquals(-40, loaded.reputationOf(OTHER));

        // A format-10 save, written before reputation existed.
        Map<String, Object> old = new LinkedHashMap<>(SettlementCodec.encode(s));
        old.put("format", 10);
        old.remove("reputation");
        Settlement migrated = SettlementCodec.decode(old);
        assertEquals(0, migrated.reputationOf(SKYE));
        assertEquals(SettlementCodec.FORMAT_VERSION, SettlementCodec.encode(migrated).get("format"));
    }
}
