package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** R3.8: donations are credited by what they're worth, not by item count. */
class DonationValueTest {

    private static ResourceMapper.Value value(String material) {
        return ResourceMapper.value(material).orElseThrow();
    }

    @Test
    void storageBlocksCountAsTheirContents() {
        assertEquals(new ResourceMapper.Value(ResourceType.METAL, 9), value("IRON_BLOCK"));
        assertEquals(new ResourceMapper.Value(ResourceType.METAL, 9), value("gold_block"));
        assertEquals(new ResourceMapper.Value(ResourceType.METAL, 9), value("minecraft:raw_copper_block"));
        assertEquals(new ResourceMapper.Value(ResourceType.FOOD, 9), value("HAY_BLOCK"));
        assertEquals(new ResourceMapper.Value(ResourceType.FOOD, 9), value("MELON"));
        assertEquals(new ResourceMapper.Value(ResourceType.FOOD, 1), value("MELON_SLICE"));
    }

    @Test
    void aBlockIsWorthExactlyItsIngots() {
        // The point of R3.8: a block can't be worth more or less than the nine items inside it.
        assertEquals(9 * value("IRON_INGOT").unitsPerItem(), value("IRON_BLOCK").unitsPerItem());
        assertEquals(9 * value("WHEAT").unitsPerItem(), value("HAY_BLOCK").unitsPerItem());
    }

    @Test
    void toolsAreWorthMoreTheBetterTheirMaterial() {
        int wooden = value("WOODEN_PICKAXE").unitsPerItem();
        int stone = value("STONE_AXE").unitsPerItem();
        int iron = value("IRON_HOE").unitsPerItem();
        int diamond = value("DIAMOND_SHOVEL").unitsPerItem();
        int netherite = value("NETHERITE_SWORD").unitsPerItem();
        assertTrue(wooden < stone && stone < iron && iron < diamond && diamond < netherite,
                wooden + " " + stone + " " + iron + " " + diamond + " " + netherite);
        assertEquals(ResourceType.TOOLS, value("IRON_PICKAXE").type());
    }

    @Test
    void ordinaryItemsAreWorthOneEach() {
        assertEquals(new ResourceMapper.Value(ResourceType.STONE, 1), value("COBBLESTONE"));
        assertEquals(new ResourceMapper.Value(ResourceType.WOOD, 4), value("oak_log")); // four planks (R3.12)
        assertEquals(new ResourceMapper.Value(ResourceType.METAL, 1), value("IRON_INGOT"));
    }

    @Test
    void itemsTheVillageCannotUseHaveNoValue() {
        assertEquals(Optional.empty(), ResourceMapper.value("DIAMOND"));
        assertEquals(Optional.empty(), ResourceMapper.value("DIRT"));
        assertEquals(Optional.empty(), ResourceMapper.value("EMERALD")); // currency, not a resource
    }

    @Test
    void emeraldsGoToTheTreasuryByValue() {
        assertEquals(1, ResourceMapper.currencyValue("EMERALD"));
        assertEquals(9, ResourceMapper.currencyValue("minecraft:emerald_block"));
        assertEquals(0, ResourceMapper.currencyValue("IRON_INGOT"));
        assertTrue(ResourceMapper.isCurrency("EMERALD_BLOCK"));
        assertFalse(ResourceMapper.isCurrency("DIAMOND"));
    }

    @Test
    void quantityMustBeAllOrAWholeNumberWithinWhatIsHeld() {
        assertEquals(OptionalInt.of(64), ResourceMapper.parseQuantity("all", 64));
        assertEquals(OptionalInt.of(64), ResourceMapper.parseQuantity("ALL", 64));
        assertEquals(OptionalInt.of(5), ResourceMapper.parseQuantity("5", 64));
        assertEquals(OptionalInt.of(64), ResourceMapper.parseQuantity("64", 64));
        assertEquals(OptionalInt.empty(), ResourceMapper.parseQuantity("65", 64));
        assertEquals(OptionalInt.empty(), ResourceMapper.parseQuantity("0", 64));
        assertEquals(OptionalInt.empty(), ResourceMapper.parseQuantity("-3", 64));
        assertEquals(OptionalInt.empty(), ResourceMapper.parseQuantity("lots", 64));
        assertEquals(OptionalInt.empty(), ResourceMapper.parseQuantity("2.5", 64));
        assertEquals(OptionalInt.empty(), ResourceMapper.parseQuantity(null, 64));
        assertEquals(OptionalInt.empty(), ResourceMapper.parseQuantity("all", 0));
    }

    @Test
    void woodIsWorthWhatItIsMadeOfWhateverItsForm() {
        // One log is four planks is eight sticks, so the same timber is worth the same in any form.
        int log = value("OAK_LOG").unitsFor(1);
        assertEquals(4, log);
        assertEquals(log, value("OAK_PLANKS").unitsFor(4));
        assertEquals(log, value("STICK").unitsFor(8));
        assertEquals(log, value("BAMBOO").unitsFor(16));
        assertEquals(4, value("CRIMSON_STEM").unitsFor(1));
        assertEquals(4, value("OAK_WOOD").unitsFor(1));
    }

    @Test
    void aStackIsValuedWholeAndRoundedDown() {
        assertEquals(0, value("STICK").unitsFor(1), "a lone stick is worth less than a unit");
        assertEquals(32, value("STICK").unitsFor(64));
        // Splitting a donation into single items can never gain anything.
        int split = 0;
        for (int i = 0; i < 64; i++) {
            split += value("STICK").unitsFor(1);
        }
        assertTrue(split <= value("STICK").unitsFor(64));
        assertEquals(0, value("STICK").unitsPerItem());
    }

    @Test
    void aToolIsWorthLessAsItWearsOut() {
        int tier = ResourceMapper.value("IRON_PICKAXE", 1.0).orElseThrow().unitsFor(1);
        assertEquals(3, tier);
        assertEquals(1, ResourceMapper.value("IRON_PICKAXE", 0.5).orElseThrow().unitsFor(1));
        assertEquals(0, ResourceMapper.value("IRON_PICKAXE", 0.0).orElseThrow().unitsFor(1));
        assertEquals(0, ResourceMapper.value("IRON_PICKAXE", 0.2).orElseThrow().unitsFor(1));
        assertTrue(ResourceMapper.value("DIAMOND_PICKAXE", 0.5).orElseThrow().unitsFor(1)
                < ResourceMapper.value("DIAMOND_PICKAXE", 1.0).orElseThrow().unitsFor(1));
        // Out-of-range conditions are clamped, and things that do not wear ignore it.
        assertEquals(tier, ResourceMapper.value("IRON_PICKAXE", 7.0).orElseThrow().unitsFor(1));
        assertEquals(1, ResourceMapper.value("IRON_INGOT", 0.1).orElseThrow().unitsFor(1));
    }

    @Test
    void aMushroomStemIsNotTimber() {
        assertEquals(Optional.empty(), ResourceMapper.classify("MUSHROOM_STEM"));
        assertEquals(ResourceType.WOOD, ResourceMapper.classify("WARPED_STEM").orElseThrow());
        assertEquals(ResourceType.WOOD, ResourceMapper.classify("STRIPPED_CRIMSON_STEM").orElseThrow());
    }

    @Test
    void aToolsRequestPaysLessForAWornToolThanANewOne() {
        // Only metal and tools are short here, so both are asked for and the treasury can pay.
        int[] paid = new int[2];
        double[] condition = {1.0, 0.5};
        for (int i = 0; i < 2; i++) {
            SettlementRegistry registry = new SettlementRegistry();
            SettlementSimulator simulator = new SettlementSimulator();
            Settlement s = registry.found("world", 0, 0, 0);
            for (int j = 0; j < 4; j++) {
                s.addResident(new Resident(new UUID(0, j + 1), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                        Occupation.NITWIT, true, 10_000, null, null, Needs.initial()));
            }
            for (ResourceType type : List.of(ResourceType.FOOD, ResourceType.WOOD, ResourceType.STONE)) {
                s.ledger().add(type, 100);
            }
            s.ledger().addTreasury(200);
            simulator.simulateTo(s, 1, 100);
            int units = ResourceMapper.value("IRON_PICKAXE", condition[i]).orElseThrow().unitsFor(1);
            paid[i] = simulator.fulfil(s, ResourceType.TOOLS, units, 1, "Skye");
        }
        assertTrue(paid[1] > 0, "a half-worn tool still pays something");
        assertTrue(paid[1] < paid[0], "worn " + paid[1] + " should be below new " + paid[0]);
    }

    @Test
    void aRequestPaysTheSameForTheSameMaterialInAnyForm() {
        // A village short of wood: handing in a log, four planks or eight sticks pays the same.
        int[] paid = new int[3];
        String[] forms = {"OAK_LOG", "OAK_PLANKS", "STICK"};
        int[] counts = {1, 4, 8};
        for (int i = 0; i < forms.length; i++) {
            SettlementRegistry registry = new SettlementRegistry();
            SettlementSimulator simulator = new SettlementSimulator();
            Settlement s = registry.found("world", 0, 0, 0);
            for (int j = 0; j < 4; j++) {
                s.addResident(new Resident(UUID.randomUUID(), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                        Occupation.NITWIT, true, 10_000, null, null, Needs.initial()));
            }
            s.ledger().addTreasury(100);
            simulator.simulateTo(s, 1, 100);
            paid[i] = simulator.fulfil(s, ResourceType.WOOD, value(forms[i]).unitsFor(counts[i]), 1, "Skye");
        }
        assertTrue(paid[0] > 0);
        assertEquals(paid[0], paid[1]);
        assertEquals(paid[0], paid[2]);
    }
}
