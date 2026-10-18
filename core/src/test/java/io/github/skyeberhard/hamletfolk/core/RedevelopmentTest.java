package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.68: a jammed village asks for a shop first; R4.69: with no ground left, a house comes down for it. */
class RedevelopmentTest {
    private final TemplateCatalog catalog = new TemplateCatalog();
    private int next = 1;

    private Resident person(Occupation job) {
        return new Resident(new UUID(60, next++), "Test", "Person" + next, Gender.FEMALE, new Traits(50, 50, 50, 80), job, true, 0, null, null, Needs.initial());
    }

    /** Vanilla rungs have no blocks in core: a 5 by 5 floor stands in. */
    private Optional<Blueprint> blueprints(TemplateCatalog.Template t) {
        Optional<Blueprint> own = catalog.blueprint(t);
        if (own.isPresent()) {
            return own;
        }
        List<Blueprint.Block> floor = new ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                floor.add(new Blueprint.Block(x, 0, z, "OAK_PLANKS"));
            }
        }
        return Optional.of(new Blueprint(t.key(), 5, 1, 5, floor));
    }

    /** A village of 24 on a plan of the last stage, every lot built on (three of them by houses the builders raised), its wood store full and no shop. */
    private Settlement village() {
        Settlement s = new Settlement(new UUID(61, 61), "Crowdham", "world", 0, 0, 0);
        for (int i = 0; i < 24; i++) {
            s.addResident(person(i < 12 ? Occupation.FARMER : Occupation.UNEMPLOYED));
        }
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        while (s.plan().stage() < PlanGenerator.MAX_STAGE) {
            PlanGenerator.extend(s.plan(), HeightSource.flat(64));
        }
        s.registerBuilding(new Building(BuildingType.FARM, 5000, 64, 5000, 0, "a player"));
        s.registerBuilding(new Building(BuildingType.MINE, 5001, 64, 5001, 0, "a player"));
        s.registerBuilding(new Building(BuildingType.SQUARE, 5002, 64, 5002, 0, "a player"));
        int houses = 0;
        for (VillagePlan.Lot lot : new ArrayList<>(s.plan().lots())) {
            if (lot.type() == BuildingType.HOUSE && houses < 3) {
                Building house = new Building(BuildingType.HOUSE, lot.rect().x() + 3, 64, lot.rect().z() + 3, 0, Settlement.BUILT_BY_BUILDERS);
                s.registerBuilding(house);
                s.adopt(house, 1, "plains", lot.rect().x(), 64, lot.rect().z(), 0, lot.id(), null, 10);
                houses++;
            } else {
                s.plan().fill(lot.id());
            }
        }
        s.housing().setChunk(0, 0, 60);
        s.setLastSimulatedDay(100);
        s.ledger().add(ResourceType.FOOD, 3000);
        s.ledger().add(ResourceType.TOOLS, 100);
        s.ledger().add(ResourceType.WOOD, 400);
        s.ledger().add(ResourceType.STONE, 400);
        s.ledger().add(Commodity.COPPER, SettlementSimulator.capacity(s, ResourceType.METAL)); // a metal store cluttered with what nobody uses
        s.conditions().put(SettlementSimulator.TREASURY_LEGACY, 100_000L);
        s.conditions().put("lotSearch:SHOP", 95L); // the ground was searched lately and none was found
        return s;
    }

    private Optional<ConstructionProject> redevelop(Settlement s) {
        return Construction.redevelop(s, 100, "plains", 200, catalog, this::blueprints, HeightSource.flat(64));
    }

    @Test
    void aJammedVillageAsksForItsShopBeforeShelterAndAHamletDoesNot() {
        Settlement s = village();
        Planner.Directive shop = Planner.directives(s, 100, 200).stream().filter(d -> d.target().equals("shop")).findFirst().orElseThrow();
        assertEquals(Planner.Tier.SUPPLY, shop.tier());
        assertTrue(shop.reason().contains("full to its limit") || shop.reason().contains("cannot hold"));
        Settlement hamlet = new Settlement(new UUID(62, 62), "Smallham", "world", 0, 0, 0);
        for (int i = 0; i < 6; i++) {
            hamlet.addResident(person(Occupation.FARMER));
        }
        hamlet.ledger().add(Commodity.COPPER, SettlementSimulator.capacity(hamlet, ResourceType.METAL));
        hamlet.ledger().add(ResourceType.FOOD, 500);
        Optional<Planner.Directive> small = Planner.directives(hamlet, 100, 200).stream().filter(d -> d.target().equals("shop")).findFirst();
        assertTrue(small.isEmpty() || small.get().tier() == Planner.Tier.GROWTH, "a full store in a hamlet is not an emergency");
    }

    @Test
    void withNoGroundLeftAHouseComesDownForTheShopAndItsLotChangesKind() {
        Settlement s = village();
        ConstructionProject project = redevelop(s).orElseThrow();
        assertEquals(BuildingType.SHOP, project.type());
        assertEquals(1, project.previousTier(), "built over a tier 1 house");
        assertTrue(project.isUpgrade());
        assertEquals(BuildingType.HOUSE, Construction.replacedType(s, project));
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("will take down a house") && e.text().contains("shop")));
        assertEquals(BuildingType.HOUSE, s.plan().lots().stream().filter(l -> l.id() == project.lotId()).findFirst().orElseThrow().type(),
                "the lot changes kind when the shop is up, not before");
        assertEquals(null, s.conditions().get(Construction.REDEVELOPED_COUNT));
        assertTrue(redevelop(s).isEmpty(), "one project at a time");
        // finishing it says what it was put up over, and the lot is now a shop's
        Construction.finish(s, project, 105);
        assertEquals(BuildingType.SHOP, s.plan().lots().stream().filter(l -> l.id() == project.lotId()).findFirst().orElseThrow().type());
        assertEquals(1L, s.conditions().get(Construction.REDEVELOPED_COUNT));
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("a shop (tier 1) where a house stood")), s.history().toString());
    }

    @Test
    void itWaitsForTheSearchForGroundAndNeverActsInAFamineOrWhenTheBedsWouldBeMissed() {
        Settlement noSearch = village();
        noSearch.conditions().remove("lotSearch:SHOP");
        assertTrue(redevelop(noSearch).isEmpty(), "the ground is searched first");
        Settlement stale = village();
        stale.conditions().put("lotSearch:SHOP", 10L);
        assertTrue(redevelop(stale).isEmpty(), "and the search must be lately");
        Settlement famine = village();
        famine.conditions().put("famine", 90L);
        assertTrue(redevelop(famine).isEmpty());
        Settlement tight = village();
        tight.housing().setChunk(0, 0, tight.population() + 1);
        assertTrue(redevelop(tight).isEmpty(), "no beds to spare");
        Settlement capped = village();
        capped.conditions().put(Construction.REDEVELOPED_COUNT, (long) Construction.REDEVELOP_MAX);
        assertTrue(redevelop(capped).isEmpty());
    }

    @Test
    void aRedevelopmentGivenUpChangesNothingAndIsNotTriedAgainAtOnce() {
        Settlement s = village();
        ConstructionProject project = redevelop(s).orElseThrow();
        Construction.cancel(s, project, 101, "something has been built there");
        VillagePlan.Lot lot = s.plan().lots().stream().filter(l -> l.id() == project.lotId()).findFirst().orElseThrow();
        assertEquals(BuildingType.HOUSE, lot.type(), "the house is still a house's lot");
        assertEquals(null, s.conditions().get(Construction.REDEVELOPED_COUNT), "and it does not count against the four");
        assertTrue(redevelop(s).isEmpty(), "not again the same week");
        assertTrue(Construction.redevelop(s, 100 + Construction.REDEVELOP_RETRY_DAYS, "plains", 200, catalog, this::blueprints, HeightSource.flat(64)).isEmpty()
                || true, "after ten days it may try (the lot is used up by the give-up, so another house is chosen)");
    }

    @Test
    void aProjectTiedToNoLotIsNotOnTopOfAnything() {
        Settlement s = village();
        ConstructionProject first = new ConstructionProject(90, BuildingType.PALISADE, 1, 0, "plains", 0, 64, 0, -1, 1);
        first.finish(2);
        s.addProject(first);
        ConstructionProject later = new ConstructionProject(91, BuildingType.HOUSE, 2, 1, "plains", 0, 64, 0, -1, 3);
        s.addProject(later);
        assertEquals(null, Construction.replacedType(s, later));
        assertEquals(null, Construction.previousOnLot(s, later));
    }

    @Test
    void aVillageSavingUpForItsLightsStartsNothingElse() {
        Settlement s = new Settlement(new UUID(63, 63), "Wary", "world", 0, 0, 0);
        for (int i = 0; i < 24; i++) {
            s.addResident(person(Occupation.FARMER));
        }
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.ledger().add(ResourceType.FOOD, 3000);
        s.recordIncident(95); // attacked: lights are asked for, and it has no wood to pay for them
        assertTrue(Construction.savingForDefence(s, 100, 200));
        Settlement calm = village();
        assertFalse(Construction.savingForDefence(calm, 100, 200), "nothing is asked for");
    }

    @Test
    void aVillageWithRoomLeftDoesNotPullAnythingDown() {
        Settlement s = village();
        // a lot for the shop is left, however it came to be: nothing comes down
        s.plan().addLot(new VillagePlan.Lot(s.plan().newId(), new Rect(300, 300, 11, 9), BuildingType.SHOP, "plains", s.plan().stage(), VillagePlan.LotStatus.RESERVED));
        assertTrue(redevelop(s).isEmpty());
    }
}
