package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R3.16 commodities under the categories, and R3.17 processing. */
class CommodityTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = SettlementSimulator.withOldAgeDeaths(false);
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(60, next++), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50), job, true, 10_000, null, null,
                Needs.initial());
    }

    private Settlement village(Occupation... jobs) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (Occupation job : jobs) {
            s.addResident(person(job));
        }
        s.ledger().add(Commodity.PRODUCE, 1000); // fed, but no grain or bread to blur what is tested
        return s;
    }

    // ----- R3.16 -----

    @Test
    void aCategoryIsTheSumOfItsCommoditiesAndEveryCategoryHasAPlainOne() {
        Ledger ledger = new Ledger();
        ledger.add(Commodity.RAW_IRON, 5);
        ledger.add(Commodity.GOLD, 2);
        ledger.add(ResourceType.METAL, 3); // to the plain commodity
        assertEquals(10, ledger.get(ResourceType.METAL));
        assertEquals(3, ledger.get(Commodity.IRON));
        for (ResourceType type : ResourceType.values()) {
            assertEquals(type, Commodity.plainOf(type).category());
            assertFalse(Commodity.of(type).isEmpty());
        }
    }

    @Test
    void takingACategoryTakesTheFreshAndTheCheapFirstAndKeepsBreadForLast() {
        Ledger ledger = new Ledger();
        ledger.add(Commodity.BREAD, 10);
        ledger.add(Commodity.GRAIN, 10);
        ledger.add(Commodity.PRODUCE, 5);
        assertEquals(12, ledger.take(ResourceType.FOOD, 12));
        assertEquals(0, ledger.get(Commodity.PRODUCE));
        assertEquals(3, ledger.get(Commodity.GRAIN));
        assertEquals(10, ledger.get(Commodity.BREAD), "bread is eaten last");

        ledger.add(Commodity.IRON_TOOLS, 2);
        ledger.add(Commodity.STONE_TOOLS, 2);
        ledger.take(ResourceType.TOOLS, 1);
        assertEquals(1, ledger.get(Commodity.STONE_TOOLS), "the cheapest tool wears out first");

        assertEquals(5, ledger.takePreferring(Commodity.BREAD, 5));
        assertEquals(5, ledger.get(Commodity.BREAD), "a sale of bread takes bread");
        assertEquals(8, ledger.take(ResourceType.FOOD, 99), "never more than there is (3 grain and 5 bread)");
    }

    @Test
    void itemsAreTheirCommodity() {
        Map<String, Commodity> expected = new LinkedHashMap<>();
        expected.put("WHEAT", Commodity.GRAIN);
        expected.put("HAY_BLOCK", Commodity.GRAIN);
        expected.put("BREAD", Commodity.BREAD);
        expected.put("CARROT", Commodity.PRODUCE);
        expected.put("BEEF", Commodity.MEAT);
        expected.put("COOKED_BEEF", Commodity.COOKED);
        expected.put("SALMON", Commodity.FISH);
        expected.put("OAK_LOG", Commodity.LOGS);
        expected.put("SPRUCE_PLANKS", Commodity.PLANKS);
        expected.put("STICK", Commodity.PLANKS);
        expected.put("COBBLESTONE", Commodity.COBBLESTONE);
        expected.put("STONE_BRICKS", Commodity.STONE_BLOCKS);
        expected.put("SAND", Commodity.SAND);
        expected.put("GRAVEL", Commodity.GRAVEL);
        expected.put("CLAY_BALL", Commodity.CLAY);
        expected.put("RAW_IRON", Commodity.RAW_IRON);
        expected.put("IRON_INGOT", Commodity.IRON);
        expected.put("RAW_GOLD_BLOCK", Commodity.RAW_GOLD);
        expected.put("COPPER_INGOT", Commodity.COPPER);
        expected.put("COAL", Commodity.COAL);
        expected.put("CHARCOAL", Commodity.CHARCOAL);
        expected.put("STONE_PICKAXE", Commodity.STONE_TOOLS);
        expected.put("IRON_AXE", Commodity.IRON_TOOLS);
        expected.put("DIAMOND_SWORD", Commodity.DIAMOND_TOOLS);
        expected.put("RED_WOOL", Commodity.WOOL);
        expected.put("BOOK", Commodity.BOOKS);
        expected.put("FEATHER", Commodity.WARES);
        expected.forEach((item, commodity) -> {
            assertEquals(Optional.of(commodity), ResourceMapper.commodity(item), item);
            assertEquals(commodity.category(), ResourceMapper.value(item).orElseThrow().type(), item + " is valued in its category");
        });
        assertEquals(9, ResourceMapper.value("COAL_BLOCK").orElseThrow().unitsPerItem());
        assertEquals(4, ResourceMapper.value("CLAY").orElseThrow().unitsPerItem());
        assertTrue(ResourceMapper.commodity("DIRT").isEmpty());
    }

    @Test
    void atTheStorageLimitIronIsTheLastMetalThrownAway() {
        Settlement s = village(Occupation.NITWIT);
        int limit = SettlementSimulator.capacity(s, ResourceType.METAL);
        s.ledger().add(Commodity.IRON, limit);
        s.ledger().add(Commodity.GOLD, 20);
        s.ledger().add(Commodity.REDSTONE, 20);
        simulator.simulateDay(s, 1);
        assertEquals(limit, s.ledger().get(ResourceType.METAL));
        assertEquals(limit, s.ledger().get(Commodity.IRON), "the gold and redstone went, not the iron");
    }

    @Test
    void spoilageTakesFreshFoodFirst() {
        Settlement s = village(Occupation.NITWIT);
        s.ledger().take(ResourceType.FOOD, 10_000);
        s.ledger().add(Commodity.BREAD, 100);
        s.ledger().add(Commodity.FISH, 100);
        s.setLastSimulatedDay(0);
        simulator.simulateDay(s, 1);
        assertEquals(100, s.ledger().get(Commodity.BREAD), "eating and spoilage both took fish");
        assertTrue(s.ledger().get(Commodity.FISH) < 100);
    }

    @Test
    void aFormatTwentyTwoSaveBecomesThePlainestCommodities() {
        Settlement s = village(Occupation.FARMER);
        Map<String, Object> saved = SettlementCodec.encode(s);
        Map<String, Object> old = new LinkedHashMap<>(saved);
        old.put("format", 22);
        Map<String, Object> stock = new LinkedHashMap<>();
        stock.put("FOOD", 40);
        stock.put("WOOD", 30);
        stock.put("METAL", 7);
        stock.put("TOOLS", 3);
        stock.put("GOODS", 5);
        old.put("stock", stock);
        Settlement loaded = SettlementCodec.decode(old);
        assertEquals(40, loaded.ledger().get(Commodity.BREAD));
        assertEquals(30, loaded.ledger().get(Commodity.PLANKS));
        assertEquals(7, loaded.ledger().get(Commodity.IRON));
        assertEquals(3, loaded.ledger().get(Commodity.IRON_TOOLS));
        assertEquals(5, loaded.ledger().get(Commodity.WOOL));
        assertEquals(0, loaded.ledger().get(ResourceType.FUEL));

        loaded.ledger().add(Commodity.RAW_COPPER, 4);
        Settlement again = SettlementCodec.decode(SettlementCodec.encode(loaded));
        assertEquals(4, again.ledger().get(Commodity.RAW_COPPER), "commodities survive a save");
        assertEquals(26, SettlementCodec.FORMAT_VERSION);
    }

    // ----- R3.17 -----

    @Test
    void aMinersOreSpreadsOverTheTable() {
        Map<Commodity, Integer> found = new EnumMap<>(Commodity.class);
        Random random = new Random(7);
        for (int i = 0; i < 10_000; i++) {
            found.merge(SettlementSimulator.ore(random), 1, Integer::sum);
        }
        assertTrue(found.get(Commodity.COAL) > 3500 && found.get(Commodity.COAL) < 4500, found.toString());
        assertTrue(found.get(Commodity.RAW_IRON) > 2500, found.toString());
        assertTrue(found.getOrDefault(Commodity.DIAMOND, 0) > 30 && found.getOrDefault(Commodity.DIAMOND, 0) < 200, found.toString());
        assertTrue(found.keySet().stream().allMatch(c -> c.category() == ResourceType.METAL || c.category() == ResourceType.FUEL));
    }

    @Test
    void minersBringUpCoalAndRawOreThatASmithSmeltsWithFuel() {
        Settlement s = village(Occupation.MINER, Occupation.MINER, Occupation.MINER, Occupation.MINER);
        s.registerBuilding(new Building(BuildingType.MINE, 0, 64, 0, 0, "test"));
        for (long day = 1; day <= 20; day++) {
            s.ledger().take(ResourceType.STONE, 10_000); // never at the limit, so nobody rests
            simulator.simulateDay(s, day);
        }
        assertTrue(s.ledger().get(Commodity.COAL) > 0, "coal");
        assertTrue(s.ledger().get(Commodity.RAW_IRON) > 0, "raw iron, not iron: there is no smith");
        assertEquals(0, s.ledger().get(Commodity.IRON));
    }

    @Test
    void smeltingNeedsASmithAndPaysOneFuelForEveryEight() {
        Settlement s = village(Occupation.TOOLSMITH);
        s.ledger().add(Commodity.RAW_IRON, 20);
        SettlementSimulator.process(s, 1);
        assertEquals(20, s.ledger().get(Commodity.RAW_IRON), "no fuel, no smelting");

        s.ledger().add(Commodity.COAL, 2);
        SettlementSimulator.process(s, 2);
        assertEquals(8, s.ledger().get(Commodity.IRON), "a smith smelts eight a day");
        assertEquals(12, s.ledger().get(Commodity.RAW_IRON));
        assertEquals(1, s.ledger().get(Commodity.COAL), "one coal for the eight");

        SettlementSimulator.process(s, 3);
        assertEquals(16, s.ledger().get(Commodity.IRON));
        assertEquals(0, s.ledger().get(Commodity.COAL));
        s.ledger().add(Commodity.RAW_GOLD, 3);
        s.ledger().add(Commodity.CHARCOAL, 1);
        SettlementSimulator.process(s, 4);
        assertEquals(20, s.ledger().get(Commodity.IRON), "iron first");
        assertEquals(3, s.ledger().get(Commodity.GOLD), "then gold, on the same charcoal");
        assertTrue(s.hasCondition(SettlementSimulator.SMELT_CREDIT), "one smelt left on the charcoal, kept for tomorrow");

        Settlement noSmith = village(Occupation.MINER);
        noSmith.ledger().add(Commodity.RAW_IRON, 20);
        noSmith.ledger().add(Commodity.COAL, 20);
        SettlementSimulator.process(noSmith, 1);
        assertEquals(0, noSmith.ledger().get(Commodity.IRON));
    }

    @Test
    void aSmithMakesIronToolsFromIronAndStoneToolsWithoutIt() {
        Settlement s = village(Occupation.TOOLSMITH);
        s.ledger().add(Commodity.IRON, 50);
        for (long day = 1; day <= 10; day++) {
            simulator.simulateDay(s, day);
        }
        assertTrue(s.ledger().get(Commodity.IRON_TOOLS) > 0);
        assertEquals(0, s.ledger().get(Commodity.STONE_TOOLS));

        Settlement poor = village(Occupation.TOOLSMITH);
        poor.ledger().add(Commodity.COBBLESTONE, 50);
        for (long day = 1; day <= 10; day++) {
            simulator.simulateDay(poor, day);
        }
        assertTrue(poor.ledger().get(Commodity.STONE_TOOLS) > 0, "stone tools when there is no iron");
        assertTrue(poor.ledger().get(Commodity.COBBLESTONE) < 50);
    }

    @Test
    void charcoalIsBurnedOnlyWhenFuelIsShortAndThereIsASmithAndALumberjack() {
        Settlement s = village(Occupation.TOOLSMITH, Occupation.LUMBERJACK);
        s.ledger().add(Commodity.LOGS, 40);
        s.ledger().add(Commodity.RAW_IRON, 100); // ore waiting, no fuel
        SettlementSimulator.process(s, 1);
        assertEquals(SettlementSimulator.CHARCOAL_LOGS_PER_DAY - SettlementSimulator.CHARCOAL_LOGS_PER_DAY / SettlementSimulator.SMELTS_PER_FUEL
                - 1, s.ledger().get(Commodity.CHARCOAL), "burned, then one used to smelt the day's ore");
        assertEquals(40 - SettlementSimulator.CHARCOAL_LOGS_PER_DAY * SettlementSimulator.UNITS_PER_LOG, s.ledger().get(Commodity.LOGS));

        Settlement stocked = village(Occupation.TOOLSMITH, Occupation.LUMBERJACK);
        stocked.ledger().add(Commodity.LOGS, 40);
        stocked.ledger().add(Commodity.RAW_IRON, 100);
        stocked.ledger().add(Commodity.COAL, 100);
        SettlementSimulator.process(stocked, 1);
        assertEquals(0, stocked.ledger().get(Commodity.CHARCOAL), "fuel is not short");

        Settlement noOre = village(Occupation.TOOLSMITH, Occupation.LUMBERJACK);
        noOre.ledger().add(Commodity.LOGS, 40);
        noOre.ledger().take(Commodity.RAW_IRON, 1000);
        SettlementSimulator.process(noOre, 1);
        assertEquals(0, noOre.ledger().get(Commodity.CHARCOAL), "nothing to smelt: the logs are kept");

        Settlement noLumberjack = village(Occupation.TOOLSMITH);
        noLumberjack.ledger().add(Commodity.LOGS, 40);
        SettlementSimulator.process(noLumberjack, 1);
        assertEquals(0, noLumberjack.ledger().get(Commodity.CHARCOAL));
    }

    @Test
    void farmersBakeGrainIntoBreadAndABirthNeedsBread() {
        Settlement s = village(Occupation.FARMER, Occupation.FARMER);
        s.ledger().add(Commodity.GRAIN, 10);
        SettlementSimulator.process(s, 1);
        assertEquals(2 * SettlementSimulator.BAKE_PER_FARMER, s.ledger().get(Commodity.BREAD));
        assertEquals(10 - 2 * SettlementSimulator.BAKE_PER_FARMER, s.ledger().get(Commodity.GRAIN));

        Settlement grainOnly = registry.found("world", 0, 0, 0);
        grainOnly.addResident(new Resident(new UUID(61, 1), "A", "B", Gender.FEMALE, new Traits(50, 50, 50, 50), Occupation.NITWIT, true,
                10_000, null, null, Needs.initial()));
        grainOnly.addResident(new Resident(new UUID(61, 2), "C", "D", Gender.MALE, new Traits(50, 50, 50, 50), Occupation.NITWIT, true,
                10_000, null, null, Needs.initial()));
        grainOnly.ledger().add(Commodity.GRAIN, 500);
        grainOnly.housing().setChunk(0, 0, 6);
        assertTrue(Births.run(registry, grainOnly, 10).isEmpty(), "grain but no bread: no child");
        grainOnly.ledger().add(Commodity.BREAD, Births.FOOD_COST);
        assertTrue(Births.run(registry, grainOnly, 10).isPresent());
        assertEquals(0, grainOnly.ledger().get(Commodity.BREAD), "the birth ate the bread");
    }
}
