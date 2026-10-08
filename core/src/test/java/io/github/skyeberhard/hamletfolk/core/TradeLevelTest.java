package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.29: every trade has levels. */
class TradeLevelTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = SettlementSimulator.withOldAgeDeaths(false);
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(70, next++), "T", "P", Gender.MALE, new Traits(50, 50, 50, 80), job, true, 10_000, null, null,
                Needs.initial());
    }

    @Test
    void levelsFollowTheDaysWorked() {
        assertEquals(1, TradeLevel.of(0));
        assertEquals(1, TradeLevel.of(9));
        assertEquals(2, TradeLevel.of(10));
        assertEquals(3, TradeLevel.of(30));
        assertEquals(4, TradeLevel.of(60));
        assertEquals(5, TradeLevel.of(100));
        assertEquals(5, TradeLevel.of(10_000));
        assertEquals("master", TradeLevel.name(5));
        assertEquals("novice", TradeLevel.name(1));
        assertEquals(1.4, TradeLevel.outputFactor(5), 1e-9);
        assertEquals(SettlementSimulator.SMELT_PER_SMITH + 8, TradeLevel.smelts(5));
        assertEquals(2, TradeLevel.extraBatches(5));
        assertEquals(3, TradeLevel.guardWeight(5));
    }

    @Test
    void aDayAtWorkIsADayOfExperienceAndANewTradeStartsAgain() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident lumberjack = person(Occupation.LUMBERJACK);
        s.addResident(lumberjack);
        s.ledger().add(Commodity.PRODUCE, 1000);
        for (long day = 1; day <= 12; day++) {
            s.ledger().take(ResourceType.WOOD, 10_000); // never at the limit, so never resting
            simulator.simulateDay(s, day);
        }
        assertEquals(12, lumberjack.xp());
        assertEquals(2, lumberjack.level());
        lumberjack.setOccupation(Occupation.FARMER);
        assertEquals(0, lumberjack.xp(), "a new trade is learned from the start");
        lumberjack.setOccupation(Occupation.BUILDER); // a spell at something else
        lumberjack.setOccupation(Occupation.LUMBERJACK);
        assertEquals(12, lumberjack.xp(), "but a spell elsewhere does not lose what they knew");
        lumberjack.setOccupation(Occupation.FARMER);
        lumberjack.addXp(1);
        assertEquals(1, lumberjack.xp(), "working at the new trade starts it from nothing");
        lumberjack.setOccupation(Occupation.LUMBERJACK);
        assertEquals(0, lumberjack.xp(), "and the old one is gone");

        Resident idle = person(Occupation.NITWIT);
        s.addResident(idle);
        simulator.simulateDay(s, 13);
        assertEquals(0, idle.xp(), "no trade, no experience");
    }

    @Test
    void aMasterMakesMoreThanANovice() {
        int novice = woodOver(0);
        int master = woodOver(100);
        assertTrue(master > novice * 1.25, "master " + master + " against novice " + novice);
    }

    /** Wood made by four lumberjacks with this much experience over 30 days (different dice each time: the margin is wide). */
    private int woodOver(int xp) {
        SettlementRegistry own = new SettlementRegistry();
        Settlement s = own.found("world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            Resident r = new Resident(new UUID(71, i), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50), Occupation.LUMBERJACK, true,
                    10_000, null, null, Needs.initial());
            r.setXp(xp);
            s.addResident(r);
        }
        s.ledger().add(Commodity.PRODUCE, 5000);
        int made = 0;
        for (long day = 1; day <= 30; day++) {
            s.ledger().take(ResourceType.WOOD, 10_000); // emptied each morning, so what is there at night is the day's work
            simulator.simulateDay(s, day);
            made += s.ledger().get(ResourceType.WOOD);
        }
        return made;
    }

    @Test
    void reachingMasterIsRecordedAndExperienceIsSaved() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident farmer = person(Occupation.FARMER);
        s.addResident(farmer);
        farmer.setXp(99);
        s.ledger().add(Commodity.PRODUCE, 1000);
        simulator.simulateDay(s, 1);
        assertEquals(5, farmer.level());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("is now a master farmer")));

        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(farmer.xp(), loaded.resident(farmer.id()).orElseThrow().xp());
        Map<String, Object> old = new LinkedHashMap<>(SettlementCodec.encode(s));
        old.put("format", 23);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> residents = (java.util.List<Map<String, Object>>) old.get("residents");
        residents.forEach(r -> r.remove("xp"));
        assertEquals(0, SettlementCodec.decode(old).resident(farmer.id()).orElseThrow().xp(), "a format-23 save starts at novice");
    }

    @Test
    void aDraftedMasterKeepsTheirTradeThroughASaveAndTheirLevelWhenTheyReturn() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident farmer = person(Occupation.FARMER);
        farmer.setXp(100);
        s.addResident(farmer);
        farmer.setOccupation(Occupation.BUILDER);
        assertEquals(1, farmer.level(), "a builder is judged by the buildings they finish");
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        Resident again = loaded.resident(farmer.id()).orElseThrow();
        assertEquals(Occupation.BUILDER, again.occupation());
        again.setOccupation(Occupation.FARMER);
        assertEquals(5, again.level(), "back in the fields, still a master");
    }

    @Test
    void aSkilledSmithSmeltsMore() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident smith = person(Occupation.TOOLSMITH);
        smith.setXp(100);
        s.addResident(smith);
        s.ledger().add(Commodity.RAW_IRON, 50);
        s.ledger().add(Commodity.COAL, 10);
        SettlementSimulator.process(s, 1);
        assertEquals(TradeLevel.smelts(5), s.ledger().get(Commodity.IRON));
    }
}
