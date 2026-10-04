package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R3.13: a crafted tool never pays more on a request than the raw materials in it would. */
class ToolRequestTest {
    private final SettlementSimulator simulator = new SettlementSimulator();

    /** Four residents who make nothing, plenty of food, wood and stone, and a rich treasury: only metal and tools are asked for. */
    private Settlement village() {
        SettlementRegistry registry = new SettlementRegistry();
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(new Resident(new UUID(0, i + 1), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                    Occupation.NITWIT, true, 10_000, null, null, Needs.initial()));
        }
        for (ResourceType type : List.of(ResourceType.FOOD, ResourceType.WOOD, ResourceType.STONE)) {
            s.ledger().add(type, 100);
        }
        s.ledger().addTreasury(500);
        s.conditions().put(SettlementSimulator.TREASURY_LEGACY, 100_000L); // R2.6: room for what the test banks
        simulator.simulateTo(s, 1, 100);
        return s;
    }

    @Test
    void aToolIsCappedAtWhatItsMaterialsWouldPay() {
        assertEquals(0, SettlementSimulator.maxPayout("WOODEN_SHOVEL", 1.0));  // 2 wood units: 0.67 emeralds
        assertEquals(1, SettlementSimulator.maxPayout("WOODEN_PICKAXE", 1.0)); // 4 wood units: 1.33
        assertEquals(1, SettlementSimulator.maxPayout("STONE_PICKAXE", 1.0));  // 3 stone + a plank: 1.53
        assertEquals(3, SettlementSimulator.maxPayout("IRON_PICKAXE", 1.0));   // 3 iron + a plank: 3.33
        assertEquals(3, SettlementSimulator.maxPayout("golden_axe", 1.0));
        assertEquals(2, SettlementSimulator.maxPayout("minecraft:iron_sword", 1.0)); // 2 iron + half a plank
        assertEquals(1, SettlementSimulator.maxPayout("IRON_PICKAXE", 0.5));   // half worn, half the materials
        assertEquals(0, SettlementSimulator.maxPayout("DIAMOND_PICKAXE", 1.0)); // diamonds are not a raw material
        assertEquals(Integer.MAX_VALUE, SettlementSimulator.maxPayout("IRON_INGOT", 1.0)); // not a tool
    }

    @Test
    void whenTheCapBindsTheDeliveryOnlyCountsForWhatItCanPay() {
        Settlement s = village();
        Request tools = s.requests().stream().filter(r -> r.type() == ResourceType.TOOLS).findFirst().orElseThrow();
        assertEquals(24, tools.wanted());
        assertEquals(48, tools.reward());

        int units = ResourceMapper.value("IRON_PICKAXE", 1.0).orElseThrow().unitsFor(1); // 3 tool units
        int uncapped = new SettlementSimulator().fulfil(village(), ResourceType.TOOLS, units, 1, "Skye");
        assertEquals(6, uncapped, "without the cap three tool units would pay 6");

        int paid = simulator.fulfil(s, ResourceType.TOOLS, units, 1, "Skye", SettlementSimulator.maxPayout("IRON_PICKAXE", 1.0));
        assertTrue(paid <= 3, "paid " + paid);
        assertEquals(2, paid);
        assertEquals(1, tools.filled(), "only the part it could pay for counted");
        assertEquals(tools.reward() - paid, tools.unpaid(), "nothing is lost or carried over");

        // The next delivery is paid in the same way, not for any shortfall of the last.
        int again = simulator.fulfil(s, ResourceType.TOOLS, units, 2, "Skye", 3);
        assertEquals(2, again);
    }

    @Test
    void aToolNeverPaysMoreThanItsMaterialsDoOnTheirOwnRequests() {
        // Raw: three iron ingots on a metal request and a plank's worth of wood on a wood request.
        Settlement metalRequest = village();
        int rawMetal = simulator.fulfil(metalRequest, ResourceType.METAL, 3, 1, "Skye");
        // (wood is plentiful in these villages, so a wood request is posted in a village short of it)
        SettlementRegistry registry = new SettlementRegistry();
        Settlement woodVillage = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            woodVillage.addResident(new Resident(new UUID(0, i + 1), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                    Occupation.NITWIT, true, 10_000, null, null, Needs.initial()));
        }
        woodVillage.ledger().add(ResourceType.FOOD, 100);
        woodVillage.ledger().add(ResourceType.STONE, 100);
        woodVillage.ledger().addTreasury(500);
        woodVillage.conditions().put(SettlementSimulator.TREASURY_LEGACY, 100_000L); // R2.6: room for what the test banks
        simulator.simulateTo(woodVillage, 1, 100);
        int rawWood = simulator.fulfil(woodVillage, ResourceType.WOOD, 1, 1, "Skye");

        Settlement toolVillage = village();
        int tool = simulator.fulfil(toolVillage, ResourceType.TOOLS,
                ResourceMapper.value("IRON_PICKAXE", 1.0).orElseThrow().unitsFor(1), 1, "Skye",
                SettlementSimulator.maxPayout("IRON_PICKAXE", 1.0));
        assertTrue(tool <= rawMetal + rawWood, "tool paid " + tool + ", its materials " + (rawMetal + rawWood));
    }

    /** What {@code units} of a raw resource pay on a request for it, in a village short of only that resource. */
    private int rawPayout(ResourceType type, int units) {
        SettlementRegistry registry = new SettlementRegistry();
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(new Resident(new UUID(0, i + 1), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                    Occupation.NITWIT, true, 10_000, null, null, Needs.initial()));
        }
        for (ResourceType other : List.of(ResourceType.FOOD, ResourceType.WOOD, ResourceType.STONE, ResourceType.METAL)) {
            if (other != type) {
                s.ledger().add(other, 100);
            }
        }
        s.ledger().addTreasury(500);
        s.conditions().put(SettlementSimulator.TREASURY_LEGACY, 100_000L); // R2.6: room for what the test banks
        simulator.simulateTo(s, 1, 100);
        return simulator.fulfil(s, type, units, 1, "Skye");
    }

    @Test
    void aWoodenAStoneAndAnIronToolNeverPayMoreThanTheirMaterialsOnRequestsOfTheirOwn() {
        // wooden shovel: a plank and two sticks (a plank's worth of wood in all)
        int woodenRaw = rawPayout(ResourceType.WOOD, 2);
        int wooden = simulator.fulfil(village(), ResourceType.TOOLS,
                ResourceMapper.value("WOODEN_SHOVEL", 1.0).orElseThrow().unitsFor(1), 1, "Skye",
                SettlementSimulator.maxPayout("WOODEN_SHOVEL", 1.0));
        assertTrue(wooden <= woodenRaw, "wooden tool paid " + wooden + ", its planks " + woodenRaw);

        // stone pickaxe: three cobblestone and two sticks
        int stoneRaw = rawPayout(ResourceType.STONE, 3) + rawPayout(ResourceType.WOOD, 1);
        int stone = simulator.fulfil(village(), ResourceType.TOOLS,
                ResourceMapper.value("STONE_PICKAXE", 1.0).orElseThrow().unitsFor(1), 1, "Skye",
                SettlementSimulator.maxPayout("STONE_PICKAXE", 1.0));
        assertTrue(stone <= stoneRaw, "stone tool paid " + stone + ", its materials " + stoneRaw);

        // iron pickaxe: three ingots and two sticks
        int ironRaw = rawPayout(ResourceType.METAL, 3) + rawPayout(ResourceType.WOOD, 1);
        int iron = simulator.fulfil(village(), ResourceType.TOOLS,
                ResourceMapper.value("IRON_PICKAXE", 1.0).orElseThrow().unitsFor(1), 1, "Skye",
                SettlementSimulator.maxPayout("IRON_PICKAXE", 1.0));
        assertTrue(iron <= ironRaw, "iron tool paid " + iron + ", its materials " + ironRaw);
        assertTrue(ironRaw >= 3, "sanity: three ingots pay at least 3, got " + ironRaw);
    }

    @Test
    void aCopperToolIsValuedByItsCopperLikeAnyOtherMetal() {
        assertEquals(SettlementSimulator.maxPayout("IRON_PICKAXE", 1.0), SettlementSimulator.maxPayout("COPPER_PICKAXE", 1.0));
    }

    @Test
    void noToolInAnyConditionEverBeatsItsCapOrBreaksTheBooks() {
        List<String> tools = List.of("WOODEN_SHOVEL", "WOODEN_AXE", "STONE_SWORD", "STONE_HOE", "IRON_PICKAXE",
                "IRON_SHOVEL", "GOLDEN_SWORD", "DIAMOND_AXE", "NETHERITE_PICKAXE");
        for (String tool : tools) {
            for (double condition : new double[] {1.0, 0.75, 0.5, 0.1, 0.0}) {
                Settlement s = village();
                Request request = s.requests().stream().filter(r -> r.type() == ResourceType.TOOLS).findFirst().orElseThrow();
                int units = ResourceMapper.value(tool, condition).orElseThrow().unitsFor(1);
                int cap = SettlementSimulator.maxPayout(tool, condition);
                int paid = simulator.fulfil(s, ResourceType.TOOLS, units, 1, "Skye", cap);
                assertTrue(paid <= cap, tool + " at " + condition + " paid " + paid + " over a cap of " + cap);
                assertEquals(paid, request.paid());
                assertEquals(request.reward() - paid, request.unpaid(), tool + ": emeralds must be conserved");
                assertTrue(request.filled() <= units);
            }
        }
    }

    @Test
    void aDeliveryThatCannotBePaidForStillCountsAsADonationOfNothingToTheRequest() {
        Settlement s = village();
        Request tools = s.requests().stream().filter(r -> r.type() == ResourceType.TOOLS).findFirst().orElseThrow();
        int units = ResourceMapper.value("DIAMOND_PICKAXE", 1.0).orElseThrow().unitsFor(1);
        int paid = simulator.fulfil(s, ResourceType.TOOLS, units, 1, "Skye", SettlementSimulator.maxPayout("DIAMOND_PICKAXE", 1.0));
        assertEquals(0, paid);
        assertEquals(0, tools.filled(), "the request is still open for the units it was after");
    }
}
