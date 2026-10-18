package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.65, R4.66, R4.67: a mine does not rest because its bin is full of metals nobody uses; a farm is looked for farther out than other buildings. */
class StuckVillageTest {
    private static int n;

    private Resident person(Occupation job) {
        return new Resident(new UUID(20, n++), "Test", "Folk" + n, Gender.MALE, new Traits(50, 50, 50, 50), job, true, 10_000, null, null, Needs.initial());
    }

    @Test
    void aMetalBinFullOfCopperAndGoldIsNotFullOfWhatTheSmithsNeed() {
        Settlement s = new Settlement(new UUID(21, n++), "Stuckton", "world", 0, 0, 0);
        for (int i = 0; i < 10; i++) {
            s.addResident(person(Occupation.FARMER));
        }
        s.addResident(person(Occupation.MINER));
        int cap = SettlementSimulator.capacity(s, ResourceType.METAL);
        s.ledger().add(Commodity.COPPER, cap / 2);
        s.ledger().add(Commodity.GOLD, cap / 2);
        s.ledger().add(Commodity.STONE_BLOCKS, SettlementSimulator.capacity(s, ResourceType.STONE));
        assertEquals(cap, s.ledger().get(ResourceType.METAL));
        assertEquals(0, SettlementSimulator.usefulStock(s, ResourceType.METAL), "none of it is of use");
        assertTrue(Commodity.COPPER.inert() && Commodity.GOLD.inert() && !Commodity.IRON.inert() && !Commodity.DIAMOND.inert());
        java.util.Set<Occupation> resting = SettlementSimulator.updateResting(s);
        assertFalse(resting.contains(Occupation.MINER), "the mine keeps working, so iron can come in and push the copper out");
        s.ledger().add(Commodity.IRON, cap); // now it really is full of iron
        assertTrue(SettlementSimulator.updateResting(s).contains(Occupation.MINER));
    }

    @Test
    void aFarmIsLookedForFartherOutThanAHouse() {
        assertEquals(64, PlanGenerator.replacementRadius(BuildingType.HOUSE));
        assertEquals(64, PlanGenerator.replacementRadius(BuildingType.SMITHY));
        assertEquals(PlanGenerator.FARM_REPLACEMENT_RADIUS, PlanGenerator.replacementRadius(BuildingType.FARM));
        assertTrue(PlanGenerator.FARM_REPLACEMENT_RADIUS > PlanGenerator.REPLACEMENT_RADIUS);
        // flat dry ground only 90 blocks from the square: a farm finds it, a smithy does not
        Settlement s = new Settlement(new UUID(22, n++), "Farmless", "world", 0, 0, 0);
        s.setPlan(PlanGenerator.generate(0, 0, 11L, "plains", HeightSource.flat(64)));
        HeightSource farAway = new HeightSource() {
            public int height(int x, int z) {
                return x > 80 && z > 20 && z < 60 ? 64 : HeightSource.UNKNOWN;
            }
        };
        assertTrue(PlanGenerator.addReplacementLot(s.plan(), BuildingType.SMITHY, farAway, java.util.List.of(), false).isEmpty());
        assertTrue(PlanGenerator.addReplacementLot(s.plan(), BuildingType.FARM, farAway, java.util.List.of(), false).isPresent());
    }

    @Test
    void aFarmTheBuildersRaisedButLostTheRecordOfCanBeAdoptedAndThenCountsAsATierOneFarm() {
        Settlement s = new Settlement(new UUID(23, n++), "Recordless", "world", 0, 0, 0);
        s.setPlan(PlanGenerator.generate(0, 0, 5L, "plains", HeightSource.flat(64)));
        VillagePlan.Lot lot = s.plan().lots().stream().filter(l -> l.type() == BuildingType.FARM).findFirst().orElseThrow();
        Building farm = new Building(BuildingType.FARM, lot.rect().x() + 2, 65, lot.rect().z() + 2, 10, Settlement.BUILT_BY_BUILDERS);
        s.registerBuilding(farm);
        s.registerBuilding(new Building(BuildingType.HOUSE, 500, 65, 500, 10, "a player"));
        assertEquals(java.util.List.of(farm), s.withoutRecord(), "only the builders' own building, and only without a record");
        ConstructionProject record = s.adopt(farm, 1, "plains", lot.rect().x(), 64, lot.rect().z(), 2, lot.id(), null, 300);
        assertEquals(ConstructionProject.Status.DONE, record.status());
        assertEquals(1, s.tierOf(farm));
        assertTrue(s.withoutRecord().isEmpty());
        assertTrue(Construction.standing(s, record));
        assertEquals(VillagePlan.LotStatus.FILLED, s.plan().lots().stream().filter(l -> l.id() == lot.id()).findFirst().orElseThrow().status());
        assertTrue(BuildingType.FARM.placesPerTier() > 0, "a better farm gives more places (R4.19)");
        // a building the plan no longer has a lot for gets one set down round it
        Settlement moved = new Settlement(new UUID(24, n++), "Replanned", "world", 0, 0, 0);
        moved.setPlan(PlanGenerator.generate(0, 0, 6L, "plains", HeightSource.flat(64)));
        Building old = new Building(BuildingType.FARM, 900, 65, 900, 10, Settlement.BUILT_BY_BUILDERS);
        moved.registerBuilding(old);
        int lots = moved.plan().lots().size();
        ConstructionProject made = moved.adopt(old, 1, "plains", 895, 64, 895, 0, -1, new Rect(890, 890, 17, 17), 300);
        assertEquals(lots + 1, moved.plan().lots().size());
        assertTrue(made.lotId() >= 0 && moved.plan().lots().stream().anyMatch(l -> l.id() == made.lotId() && l.status() == VillagePlan.LotStatus.FILLED));
    }
}
