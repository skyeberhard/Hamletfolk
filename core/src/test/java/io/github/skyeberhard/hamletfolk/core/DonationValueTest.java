package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
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
        assertEquals(new ResourceMapper.Value(ResourceType.WOOD, 1), value("oak_log"));
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
}
