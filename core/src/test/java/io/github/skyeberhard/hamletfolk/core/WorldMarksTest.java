package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.25: what the village's work owes the land, and what the quarry may take and finds. */
class WorldMarksTest {
    private final SettlementRegistry registry = new SettlementRegistry();

    @Test
    void lumberjacksAndMinersOweTheirWorkToTheWorldUpToACap() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.addResident(new Resident(new UUID(90, 1), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50), Occupation.LUMBERJACK, true,
                10_000, null, null, Needs.initial()));
        s.addResident(new Resident(new UUID(90, 2), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50), Occupation.MINER, true,
                10_000, null, null, Needs.initial()));
        s.registerBuilding(new Building(BuildingType.MINE, 0, 64, 0, 0, "test"));
        s.ledger().add(Commodity.PRODUCE, 5000);
        SettlementSimulator sim = SettlementSimulator.withOldAgeDeaths(false);
        for (long day = 1; day <= 5; day++) {
            s.ledger().take(ResourceType.WOOD, 10_000);
            s.ledger().take(ResourceType.STONE, 10_000);
            sim.simulateDay(s, day);
        }
        assertTrue(WorldMarks.owed(s, ResourceType.WOOD) > 0);
        assertTrue(WorldMarks.owed(s, ResourceType.STONE) > 0);
        int wood = WorldMarks.owed(s, ResourceType.WOOD);
        WorldMarks.pay(s, ResourceType.WOOD, 4);
        assertEquals(Math.max(0, wood - 4), WorldMarks.owed(s, ResourceType.WOOD));
        WorldMarks.pay(s, ResourceType.WOOD, 10_000);
        assertEquals(0, WorldMarks.owed(s, ResourceType.WOOD), "never below nothing");

        WorldMarks.owe(s, ResourceType.STONE, 10_000);
        assertEquals(WorldMarks.MAX_OWED, WorldMarks.owed(s, ResourceType.STONE), "capped");
        WorldMarks.owe(s, ResourceType.FOOD, 50);
        assertEquals(WorldMarks.MAX_OWED, WorldMarks.owed(s, ResourceType.STONE));
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        assertEquals(WorldMarks.MAX_OWED, WorldMarks.owed(loaded, ResourceType.STONE), "saved");
    }

    @Test
    void keptOreGoesIntoTheStoresAndTheFlow() {
        Settlement s = registry.found("world", 0, 0, 0);
        WorldMarks.keep(s, WorldMarks.drop("LAPIS_ORE").orElseThrow(), 3);
        assertEquals(6, s.ledger().get(Commodity.LAPIS));
        assertEquals(6, s.flow().produced(ResourceType.METAL, 3));
    }

    @Test
    void aQuarryIsDugALayerAtATimeToItsBottom() {
        Settlement s = registry.found("world", 0, 0, 0);
        assertTrue(WorldMarks.quarry(s).isEmpty());
        WorldMarks.openQuarry(s, 10, 20, 64, 62, 1);
        assertEquals(new WorldMarks.Quarry(10, 20, 64, 62), WorldMarks.quarry(s).orElseThrow());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("opened a quarry")));
        WorldMarks.nextLayer(s, 2);
        WorldMarks.nextLayer(s, 2);
        assertFalse(WorldMarks.quarry(s).orElseThrow().exhausted());
        WorldMarks.nextLayer(s, 3);
        assertTrue(WorldMarks.quarry(s).orElseThrow().exhausted());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("as deep as it goes")));
        WorldMarks.nextLayer(s, 4);
        assertEquals(61, WorldMarks.quarry(s).orElseThrow().layer(), "no further once exhausted");
        assertTrue(WorldMarks.quarry(SettlementCodec.decode(SettlementCodec.encode(s))).isPresent(), "saved");
    }

    @Test
    void anOreBlockPaysWhatItDropsAndTheQuarryTakesOnlyTheGround() {
        assertEquals(Optional.of(new WorldMarks.Drop(Commodity.COAL, 1)), WorldMarks.drop("minecraft:coal_ore"));
        assertEquals(Optional.of(new WorldMarks.Drop(Commodity.RAW_IRON, 1)), WorldMarks.drop("DEEPSLATE_IRON_ORE"));
        assertEquals(Commodity.DIAMOND, WorldMarks.drop("DIAMOND_ORE").orElseThrow().commodity());
        assertEquals(6, WorldMarks.drop("LAPIS_ORE").orElseThrow().count());
        assertTrue(WorldMarks.drop("STONE").isEmpty());
        assertTrue(WorldMarks.quarryable("STONE"));
        assertTrue(WorldMarks.quarryable("minecraft:deepslate"));
        assertTrue(WorldMarks.quarryable("GOLD_ORE"));
        assertTrue(WorldMarks.quarryable("GRASS_BLOCK"));
        for (String never : new String[] {"BEDROCK", "WATER", "LAVA", "COBBLESTONE", "STONE_BRICKS", "OAK_PLANKS", "CHEST", "GRAVEL",
                "SAND", "TORCH", "AIR"}) {
            assertFalse(WorldMarks.quarryable(never), never + " is never dug: built, liquid, falling or air");
        }
    }
}
