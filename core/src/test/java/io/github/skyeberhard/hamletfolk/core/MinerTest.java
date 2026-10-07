package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R2.3: a registered mine lets an unemployed resident become a miner, who makes the stone and metal smiths need. */
class MinerTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();

    private static Resident person(int i, Occupation job) {
        // Far-future birth day so aging (R4.15) never affects a long run.
        return new Resident(new UUID(0, i + 1), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                job, true, 10_000, null, null, Needs.initial());
    }

    /** {@code unemployed} jobless residents, with food and wood plentiful so only stone and metal are short. */
    private Settlement village(int unemployed) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < unemployed; i++) {
            s.addResident(person(i, Occupation.UNEMPLOYED));
        }
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.WOOD, 1000);
        return s;
    }

    private static long count(Settlement s, Occupation job) {
        return s.residents().stream().filter(r -> r.occupation() == job).count();
    }

    private static void register(Settlement s, BuildingType type, int x) {
        s.registerBuilding(new Building(type, x, 64, 0, 1, "Skye"));
    }

    @Test
    void withoutAMineNobodyBecomesAMinerNoMatterHowShortMetalIs() {
        Settlement s = village(4);
        simulator.simulateTo(s, 10, 100);
        assertEquals(0, count(s, Occupation.MINER));
        assertEquals(0, s.ledger().get(ResourceType.METAL));
    }

    @Test
    void aRegisteredMineLetsUnemployedResidentsBecomeMinersFourPlacesPerMine() {
        Settlement s = village(9);
        register(s, BuildingType.MINE, 0);
        for (int day = 1; day <= 12; day++) {
            s.ledger().take(ResourceType.STONE, 10_000); // keep stone and metal short so more miners stay wanted
            s.ledger().take(ResourceType.METAL, 10_000);
            simulator.simulateTo(s, day, 100);
        }
        assertEquals(4, count(s, Occupation.MINER), "one mine has four places");

        register(s, BuildingType.MINE, 10);
        for (int day = 13; day <= 30; day++) {
            s.ledger().take(ResourceType.STONE, 10_000);
            s.ledger().take(ResourceType.METAL, 10_000);
            simulator.simulateTo(s, day, 100);
        }
        assertEquals(8, count(s, Occupation.MINER), "a second mine has four more");
    }

    @Test
    void aMinerMakesStoneAndMetal() {
        Settlement s = village(3);
        register(s, BuildingType.MINE, 0);
        simulator.simulateTo(s, 14, 100);
        assertTrue(count(s, Occupation.MINER) > 0);
        // Summed over days and workers, never a single day's output.
        assertTrue(s.flow().produced(ResourceType.STONE, 14) > 0, "stone");
        assertTrue(s.flow().produced(ResourceType.METAL, 14) > 0, "metal");
        assertTrue(s.flow().produced(ResourceType.STONE, 14) > s.flow().produced(ResourceType.METAL, 14),
                "mostly stone, and some metal");
    }

    @Test
    void aMineLetsSmithsWorkWithoutDonations() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.addResident(person(0, Occupation.TOOLSMITH));
        for (int i = 1; i <= 5; i++) {
            s.addResident(person(i, Occupation.UNEMPLOYED));
        }
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.WOOD, 1000);
        register(s, BuildingType.MINE, 0);

        simulator.simulateTo(s, 1, 100);
        assertTrue(s.hasCondition("shortage:metal"), "no metal yet: the smith sits idle");

        simulator.simulateTo(s, 25, 100);
        assertFalse(s.hasCondition("shortage:metal"), "miners have supplied metal");
        // What the smith made, not what is left: gatherers wear tools out too.
        assertTrue(s.flow().produced(ResourceType.TOOLS, 25) > 0, "the smith made tools from mined metal");
    }

    @Test
    void foodComesFirstSoAStarvingVillageKeepsItsForagersEvenWithAMine() {
        Settlement s = village(4);
        s.ledger().take(ResourceType.FOOD, 1000); // famine
        register(s, BuildingType.MINE, 0);
        simulator.simulateTo(s, 5, 100);
        assertEquals(0, count(s, Occupation.MINER));
        assertEquals(0, count(s, Occupation.LUMBERJACK));
    }

    @Test
    void aFarmGivesFarmersToAHungryVillage() {
        Settlement s = village(6);
        s.ledger().take(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.FOOD, 5);
        register(s, BuildingType.FARM, 0);
        simulator.simulateTo(s, 8, 100);
        assertTrue(count(s, Occupation.FARMER) >= 1, "someone took up farming");
        assertTrue(count(s, Occupation.FARMER) <= 4, "a farm has four places");
    }

    @Test
    void theLumberjackNeedsNoBuildingAndAMissingMineDoesNotBlockHim() {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 3; i++) {
            s.addResident(person(i, Occupation.UNEMPLOYED));
        }
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.STONE, 1000); // wood and metal are both short, but only wood has somewhere to work
        simulator.simulateTo(s, 4, 100);
        assertTrue(count(s, Occupation.LUMBERJACK) >= 1);
        assertEquals(0, count(s, Occupation.MINER));
    }

    @Test
    void aVillageWithoutAMineDoesNotAlwaysLookShortOfMetalSoItKeepsItsMerchant() {
        // Metal is permanently 0 without a mine; that must not count as "something short" and release the merchant.
        Settlement s = registry.found("world", 0, 0, 0);
        s.addResident(person(0, Occupation.MERCHANT));
        for (int i = 1; i < 4; i++) {
            s.addResident(person(i, Occupation.NITWIT));
        }
        s.registerBuilding(new Building(BuildingType.SHOP, 0, 64, 0, 0, "test"));
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.WOOD, 100);
        s.ledger().add(ResourceType.STONE, 100);
        for (int day = 1; day <= 30; day++) {
            s.ledger().add(ResourceType.FOOD, 8); // enough to eat, so this is not the food case
            simulator.simulateTo(s, day, 100);
        }
        assertEquals(1, count(s, Occupation.MERCHANT));
    }

    @Test
    void aMerchantIsReleasedInAFamineSoTheyCanForage() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.registerBuilding(new Building(BuildingType.SHOP, 0, 64, 0, 0, "test")); // so it is the famine, not a missing shop, that releases them
        s.addResident(person(0, Occupation.MERCHANT));
        for (int i = 1; i < 4; i++) {
            s.addResident(person(i, Occupation.NITWIT));
        }
        simulator.simulateTo(s, 3, 100); // nothing to sell and no food at all
        assertEquals(0, count(s, Occupation.MERCHANT));
    }

    @Test
    void aVillageThatMerelyHoldsLittleFoodStillGetsLumberjacksAndMiners() {
        // Food stock under 10 a head but no famine: this used to freeze every non-food job.
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(person(i, Occupation.FARMER));
        }
        for (int i = 4; i < 7; i++) {
            s.addResident(person(i, Occupation.UNEMPLOYED));
        }
        s.ledger().add(ResourceType.FOOD, 40); // 5.7 a head: short, but the four farmers keep it fed
        register(s, BuildingType.MINE, 0);
        for (int day = 1; day <= 6; day++) {
            s.ledger().add(ResourceType.FOOD, 5);
            simulator.simulateTo(s, day, 100);
        }
        assertFalse(s.hasCondition("famine"));
        assertTrue(count(s, Occupation.LUMBERJACK) + count(s, Occupation.MINER) >= 1, "someone took up work");
    }

    @Test
    void aFarmPlaceGoesToHungryUnemployedBeforeAnythingElseWhenAFoodJobIsFree() {
        Settlement s = village(4);
        s.ledger().take(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.FOOD, 10); // short but not yet famine
        register(s, BuildingType.FARM, 0);
        register(s, BuildingType.MINE, 5);
        simulator.simulateTo(s, 1, 100);
        assertEquals(1, count(s, Occupation.FARMER));
        assertEquals(0, count(s, Occupation.MINER));
    }

    @Test
    void aSmithyGivesSmithPlacesWhenToolsAreShort() {
        Settlement s = village(3);
        s.ledger().add(ResourceType.STONE, 1000);
        s.ledger().add(ResourceType.METAL, 1000);
        register(s, BuildingType.SMITHY, 0);
        simulator.simulateTo(s, 3, 100);
        assertTrue(count(s, Occupation.TOOLSMITH) >= 1, "tools are short and a smithy has places");
    }

    @Test
    void theMinerHasNoVanillaProfessionAndWearsToolsOut() {
        assertTrue(Occupation.MINER.simOwned());
        assertTrue(Occupation.MINER.usesTools());
        assertEquals(ResourceType.STONE, Occupation.MINER.produces());
        assertEquals(ResourceType.METAL, Occupation.MINER.secondaryProduces());
        assertEquals(java.util.Optional.of(Occupation.MINER), BuildingType.MINE.job());
        assertEquals(java.util.Optional.empty(), BuildingType.HOUSE.job());
    }

    @Test
    void theToollessPenaltySlowsGatherersOnceAMineGivesTheVillageAWayToGetMetal() {
        SettlementSimulator strict = SettlementSimulator.configured(false, true);
        Settlement bare = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 10; i++) {
            bare.addResident(person(i, Occupation.MASON));
        }
        bare.ledger().add(ResourceType.FOOD, 1000);
        bare.registerBuilding(new Building(BuildingType.MINE, 0, 64, 0, 1, "Skye"));
        Settlement equipped = SettlementCodec.decode(SettlementCodec.encode(bare)); // the same people and dice
        equipped.ledger().add(ResourceType.TOOLS, 100);
        int withoutTools = 0;
        int withTools = 0;
        for (int day = 1; day <= 35; day++) {
            bare.ledger().take(ResourceType.STONE, 10_000); // spent each day, so the masons never rest at the limit (R4.24)
            equipped.ledger().take(ResourceType.STONE, 10_000);
            strict.simulateTo(bare, day, 100);
            strict.simulateTo(equipped, day, 100);
            if (day % 7 == 0) {
                withoutTools += bare.flow().produced(ResourceType.STONE, day);
                withTools += equipped.flow().produced(ResourceType.STONE, day);
            }
        }
        assertTrue(bare.history().stream().anyMatch(e -> e.text().contains("lack of tools")), "the shortage is recorded");
        assertTrue(withoutTools < withTools, "slowed: " + withoutTools + " against " + withTools);
    }

    @Test
    void aVillageWithNoMineIsNotPunishedForHavingNoTools() {
        SettlementSimulator strict = SettlementSimulator.configured(false, true);
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 10; i++) {
            s.addResident(person(i, Occupation.MASON));
        }
        s.ledger().add(ResourceType.FOOD, 1000);
        strict.simulateTo(s, 14, 100);
        assertFalse(s.history().stream().anyMatch(e -> e.text().contains("lack of tools")), "no way to get metal, so no penalty");
    }
}
