package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R8.19: ground the builders can level is kept as rough lots and used when no ready lot of the kind is left. */
class RoughLotTest {
    /** A hillside everywhere: 7 blocks of fall across any 14 columns, too steep to found on but within what the pad can level. */
    private static final HeightSource HILL = (x, z) -> 64 + Math.floorMod(x, 14) / 2;

    @Test
    void aHillsideVillageKeepsItsLotsAsRoughInsteadOfDroppingThem() {
        VillagePlan plan = PlanGenerator.generate(0, 0, 7L, "plains", HILL);
        assertTrue(plan.roughCount() > 0, "rough lots are kept");
        for (VillagePlan.Lot lot : plan.lots()) {
            assertTrue(lot.status() == VillagePlan.LotStatus.ROUGH ? PlanGenerator.gradable(HILL, lot.rect())
                    && !PlanGenerator.buildable(HILL, lot.rect()) : PlanGenerator.buildable(HILL, lot.rect()));
        }
        assertEquals(plan.reservedCount(), plan.lots().size() - plan.filledCount(), "rough lots count as lots left");
    }

    @Test
    void aReadyLotOfTheKindComesBeforeARoughOneEvenIfTheRoughOneIsNearer() {
        VillagePlan plan = PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64));
        VillagePlan.Lot ready = plan.nextLot(BuildingType.HOUSE).orElseThrow();
        VillagePlan.Lot rough = new VillagePlan.Lot(plan.newId(), new Rect(plan.centerX() + 1, plan.centerZ() + 1, 9, 9),
                BuildingType.HOUSE, "plains", 1, VillagePlan.LotStatus.ROUGH);
        plan.addLot(rough);
        assertEquals(ready.id(), plan.nextLot(BuildingType.HOUSE).orElseThrow().id());
        List<VillagePlan.Lot> order = plan.candidatesFor(BuildingType.HOUSE);
        assertEquals(VillagePlan.LotStatus.RESERVED, order.get(0).status());
        assertEquals(rough.id(), order.get(order.size() - 1).id(), "the rough lot is the last resort");
    }

    @Test
    void whenNoReadyLotOfTheKindIsLeftTheRoughOneIsOfferedBeforeAnotherKindsLot() {
        VillagePlan plan = PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64));
        for (VillagePlan.Lot lot : new ArrayList<>(plan.lots())) {
            if (lot.type() == BuildingType.SMITHY) {
                plan.fill(lot.id());
            }
        }
        VillagePlan.Lot rough = new VillagePlan.Lot(plan.newId(), new Rect(80, 80, 13, 13), BuildingType.SMITHY, "plains", 1,
                VillagePlan.LotStatus.ROUGH);
        plan.addLot(rough);
        assertEquals(rough.id(), plan.candidatesFor(BuildingType.SMITHY).get(0).id());
        assertEquals(rough.id(), plan.nextLot(BuildingType.SMITHY).orElseThrow().id());
    }

    @Test
    void aRoughLotIsFilledLikeAnyOtherAndSurvivesTheSave() {
        VillagePlan plan = PlanGenerator.generate(0, 0, 7L, "plains", HILL);
        VillagePlan.Lot rough = plan.lots().stream().filter(l -> l.status() == VillagePlan.LotStatus.ROUGH).findFirst().orElseThrow();
        VillagePlan copy = VillagePlan.fromMap(plan.toMap());
        assertEquals(plan.lots(), copy.lots(), "rough lots come back as rough");
        assertTrue(copy.fill(rough.id()));
        assertEquals(VillagePlan.LotStatus.FILLED, copy.lots().stream().filter(l -> l.id() == rough.id()).findFirst().orElseThrow().status());
        assertTrue(copy.fillOverlapping(new Rect(rough.rect().x(), rough.rect().z(), 1, 1)) >= 0);
    }

    @Test
    void aVillageOnAHillsideCanBuildOnItsRoughLots() {
        Settlement s = new Settlement(new UUID(97, 97), "Hillham", "world", 0, 0, 0);
        for (int i = 0; i < 6; i++) {
            s.addResident(new Resident(new UUID(98, i), "T", "P" + i, Gender.FEMALE, new Traits(50, 50, 50, 80), Occupation.UNEMPLOYED, true,
                    0, null, null, Needs.initial()));
        }
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HILL));
        s.ledger().add(ResourceType.WOOD, 300);
        s.ledger().add(ResourceType.STONE, 300);
        TemplateCatalog catalog = new TemplateCatalog();
        Optional<ConstructionProject> project = Construction.propose(s, 5, "plains", 200, catalog, t -> {
            List<Blueprint.Block> floor = new ArrayList<>();
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    floor.add(new Blueprint.Block(x, 0, z, "OAK_PLANKS"));
                }
            }
            return Optional.of(new Blueprint(t.key(), 5, 1, 5, floor));
        }, HILL);
        assertTrue(project.isPresent(), "a farm or a square is started: " + s.history());
        if (project.get().lotId() >= 0) {
            assertTrue(s.plan().lots().stream().anyMatch(l -> l.id() == project.get().lotId() && l.status() == VillagePlan.LotStatus.ROUGH));
        }
    }
}
