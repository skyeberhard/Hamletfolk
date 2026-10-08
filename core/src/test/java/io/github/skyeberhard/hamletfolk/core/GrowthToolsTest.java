package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R1.31 pins and the pit rule, R4.26 beds, R4.27 fast-forward, R4.28 births and R5.8 staged works. */
class GrowthToolsTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = SettlementSimulator.withOldAgeDeaths(false);
    private int next = 1;

    private Resident person(Occupation job, Gender gender) {
        return new Resident(new UUID(50, next++), "T", "P" + next, gender, new Traits(50, 50, 50, 80), job, true, 10_000,
                null, null, Needs.initial());
    }

    private Resident person(Occupation job) {
        return person(job, next % 2 == 0 ? Gender.FEMALE : Gender.MALE);
    }

    // ----- R1.31 -----

    @Test
    void aPinnedResidentIsNeverDraftedCalledUpReleasedOrReassigned() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident nitwit = person(Occupation.NITWIT);
        s.addResident(nitwit);
        for (int i = 0; i < 3; i++) {
            s.addResident(person(Occupation.FARMER));
        }
        s.ledger().add(ResourceType.FOOD, 1000);
        assertEquals(nitwit, SettlementSimulator.spareWorker(s, 1), "an idler is the first spared");
        s.setPinned(nitwit.id(), true, 1);
        assertTrue(SettlementSimulator.spareWorker(s, 1) != nitwit, "pinned: not spared");

        Resident jobless = person(Occupation.UNEMPLOYED);
        s.addResident(jobless);
        s.setPinned(jobless.id(), true, 1);
        simulator.simulateDay(s, 2);
        assertEquals(Occupation.UNEMPLOYED, jobless.occupation(), "a pinned jobless resident is not given a job");

        Resident guard = person(Occupation.GUARD);
        s.addResident(guard);
        s.setPinned(guard.id(), true, 2);
        for (long day = 3; day < 30; day++) {
            simulator.simulateDay(s, day);
        }
        assertEquals(Occupation.GUARD, guard.occupation(), "a pinned guard never stands down");

        Settlement copy = SettlementCodec.decode(SettlementCodec.encode(s));
        assertTrue(copy.isPinned(guard.id()), "the pin is saved");
        assertTrue(copy.residents().stream().filter(r -> r.occupation() == Occupation.LUMBERJACK).count() <= 1,
                "a pinned jobless resident does not stop the suppliers being staffed by the others");
        s.setPinned(guard.id(), false, 30);
        assertFalse(s.isPinned(guard.id()));
    }

    @Test
    void aPinnedBraveResidentIsNotCalledUpAndAPinnedMerchantIsNotReleased() {
        Settlement s = registry.found("world", 0, 0, 0);
        Resident brave = person(Occupation.NITWIT);
        s.addResident(brave);
        Resident merchant = person(Occupation.MERCHANT);
        s.addResident(merchant);
        for (int i = 0; i < 4; i++) {
            s.addResident(person(Occupation.FARMER));
        }
        s.setPinned(brave.id(), true, 1);
        s.setPinned(merchant.id(), true, 1);
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.TOOLS, 50);
        s.recordIncident(1);
        s.recordIncident(1);
        s.recordIncident(1); // alarmed: guards are called up
        simulator.simulateDay(s, 1);
        assertEquals(Occupation.NITWIT, brave.occupation(), "pinned: not called up although brave and idle");
        assertEquals(Occupation.MERCHANT, merchant.occupation(), "pinned: kept although there is no shop");
    }

    @Test
    void aVillagerInAPitIsWalledInAndOneThatCanStepOutIsNot() {
        // A pit two deep: every side is solid at the feet and at head height.
        assertTrue(Stuck.walledIn((dx, dy, dz) -> (dx != 0 || dz != 0) && dy <= 1));
        // One side open at feet and head: it can walk out.
        assertFalse(Stuck.walledIn((dx, dy, dz) -> (dx != 0 || dz != 0) && dy <= 1 && dx != 1));
        // A one-block step on every side: it can step up.
        assertFalse(Stuck.walledIn((dx, dy, dz) -> (dx != 0 || dz != 0) && dy == 0));
        // A one-block step, but a ceiling over its head: it cannot jump.
        assertTrue(Stuck.walledIn((dx, dy, dz) -> ((dx != 0 || dz != 0) && dy == 0) || (dx == 0 && dz == 0 && dy == 2)));
        // Open ground: free.
        assertFalse(Stuck.walledIn((dx, dy, dz) -> false));
        // A pit is walled in with sky above; the same cell under a roof is a room or a trading-hall cell, left alone.
        Stuck.Blocks cell = (dx, dy, dz) -> (dx != 0 || dz != 0) && dy <= 1;
        assertTrue(Stuck.inPit(cell, true));
        assertFalse(Stuck.inPit(cell, false));
    }

    // ----- R4.26 -----

    private static Blueprint withBeds(int beds, int planks, boolean partNamed) {
        List<Blueprint.Block> blocks = new ArrayList<>();
        for (int i = 0; i < beds; i++) {
            blocks.add(new Blueprint.Block(i, 0, 0, partNamed ? "RED_BED[part=head,facing=north]" : "RED_BED"));
            blocks.add(new Blueprint.Block(i, 0, 1, partNamed ? "RED_BED[part=foot,facing=north]" : "RED_BED"));
        }
        for (int i = 0; i < planks; i++) {
            blocks.add(new Blueprint.Block(i % 10, 1 + i / 100, (i / 10) % 10, "OAK_PLANKS"));
        }
        return new Blueprint("t", 10, 10, 10, blocks);
    }

    @Test
    void bedsAreCountedOncePerBed() {
        assertEquals(3, withBeds(3, 0, true).beds());
        assertEquals(2, withBeds(2, 0, false).beds());
        assertEquals(0, withBeds(0, 50, true).beds());
    }

    @Test
    void aHouseIsChosenForTheMostBedsForItsCostAndAnUpgradeAddsBeds() {
        TemplateCatalog.Template small = new TemplateCatalog.Template(BuildingType.HOUSE, "plains", 1, "small", TemplateCatalog.Source.VANILLA);
        TemplateCatalog.Template medium = new TemplateCatalog.Template(BuildingType.HOUSE, "plains", 2, "medium", TemplateCatalog.Source.VANILLA);
        Map<String, Blueprint> designs = Map.of("small", withBeds(1, 40, true), "medium", withBeds(3, 70, true));
        List<TemplateCatalog.Template> ladder = List.of(small, medium);
        Map<ResourceType, Integer> rich = Map.of(ResourceType.WOOD, 1000);
        Map<ResourceType, Integer> poor = Map.of(ResourceType.WOOD, 50);
        java.util.function.Function<TemplateCatalog.Template, Map<ResourceType, Integer>> cost = t -> designs.get(t.key()).cost();
        java.util.function.Function<TemplateCatalog.Template, Integer> beds = t -> designs.get(t.key()).beds();
        assertEquals("medium", TemplateCatalog.mostBedsAffordable(ladder, 0, cost, beds, rich, 0).orElseThrow().key(),
                "three beds for 70 beats one for 40");
        assertEquals("small", TemplateCatalog.mostBedsAffordable(ladder, 0, cost, beds, poor, 0).orElseThrow().key(),
                "but only what it can pay for");
        assertTrue(TemplateCatalog.mostBedsAffordable(ladder, 1, cost, beds, rich, 3).isEmpty(), "an upgrade must add beds");
    }

    // ----- R4.27 -----

    @Test
    void fastForwardOwesTheExtraDaysAndCarriesTheRest() {
        // At 100x, a second (20 ticks) owes 99 * 20 / 24000 of a day: a day every 12 seconds or so.
        double carry = 0;
        int days = 0;
        for (int second = 0; second < 1200; second++) {
            FastForward.Step step = FastForward.advance(carry, 100, 20, 3);
            carry = step.carry();
            days += step.days();
        }
        assertEquals(99, days, "1200 seconds is one world day, plus 99 more at 100x");
        assertTrue(carry < 1);

        FastForward.Step slow = FastForward.advance(0, 2, 20, 3);
        assertEquals(0, slow.days());
        assertEquals(20 / 24000.0, slow.carry(), 1e-12);

        FastForward.Step capped = FastForward.advance(10, 100, 20, 3);
        assertEquals(3, capped.days(), "never more than the cap in one pass");
        assertEquals(3, capped.carry(), 1e-9, "and the backlog is capped too");

        assertEquals(FastForward.advance(0, 100, 20, 3), FastForward.advance(0, 1000, 20, 3), "speed is capped at 100");
    }

    // ----- R4.28 -----

    private Settlement familyVillage(int food, int beds) {
        Settlement s = registry.found("world", 0, 0, 0);
        s.addResident(person(Occupation.FARMER, Gender.FEMALE));
        s.addResident(person(Occupation.FARMER, Gender.MALE));
        s.addResident(person(Occupation.LUMBERJACK, Gender.MALE));
        s.ledger().add(ResourceType.FOOD, food);
        s.housing().setChunk(0, 0, beds);
        return s;
    }

    @Test
    void aChildWithNoVillagerDoesNotMigrateAndAPinnedResidentWhoSettlesElsewhereKeepsTheirTrade() {
        SettlementRegistry own = new SettlementRegistry();
        Settlement from = own.found("world", 0, 0, 0);
        Settlement to = own.found("world", 100, 0, 0);
        Resident pinned = person(Occupation.MINER);
        from.addResident(pinned);
        from.setPinned(pinned.id(), true, 1);
        own.transfer(pinned, from, to, 5, "left", "came", false); // their villager settled there (R1.8)
        assertEquals(Occupation.MINER, pinned.occupation());
        assertTrue(to.isPinned(pinned.id()));
        Resident moved = person(Occupation.MINER);
        to.addResident(moved);
        to.setPinned(moved.id(), true, 5);
        own.transfer(moved, to, from, 6, "left", "came", true); // a migration
        assertEquals(Occupation.UNEMPLOYED, moved.occupation());
        assertFalse(from.isPinned(moved.id()));
    }

    @Test
    void aWellFedVillageWithAFreeBedHasAChild() {
        Settlement s = familyVillage(500, 6);
        Optional<Resident> child = Births.run(registry, s, 10);
        assertTrue(child.isPresent());
        assertEquals(4, s.population());
        assertFalse(child.get().adult());
        assertEquals(500 - Births.FOOD_COST, s.ledger().get(ResourceType.FOOD));
        Resident mother = s.resident(child.get().parentA()).orElseThrow();
        Resident father = s.resident(child.get().parentB()).orElseThrow();
        assertEquals(Gender.FEMALE, mother.gender());
        assertEquals(Gender.MALE, father.gender());
        assertTrue(s.history().stream().anyMatch(e -> e.kind() == HistoryEvent.Kind.BIRTH && e.text().contains(mother.fullName())));
        assertEquals(List.of(child.get()), Births.awaiting(s), "no villager yet");

        assertTrue(Births.run(registry, s, 11).isEmpty(), "not again within three days");
        assertTrue(Births.run(registry, s, 13).isPresent());
    }

    @Test
    void noChildWithoutFoodABedPeaceOrACouple() {
        assertTrue(Births.run(registry, familyVillage(50, 6), 10).isEmpty(), "too little food");
        assertTrue(Births.run(registry, familyVillage(500, 3), 10).isEmpty(), "no free bed");
        Settlement hungry = familyVillage(500, 6);
        hungry.conditions().put("famine", 9L);
        assertTrue(Births.run(registry, hungry, 10).isEmpty(), "famine");
        Settlement attacked = familyVillage(500, 6);
        attacked.recordIncident(9);
        assertTrue(Births.run(registry, attacked, 10).isEmpty(), "in danger");
        Settlement men = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 3; i++) {
            men.addResident(person(Occupation.FARMER, Gender.MALE));
        }
        men.ledger().add(ResourceType.FOOD, 500);
        men.housing().setChunk(0, 0, 6);
        assertTrue(Births.run(registry, men, 10).isEmpty(), "no couple");
    }

    @Test
    void eldersAndChildrenWithNoVillagerYetHaveNoChildren() {
        Settlement elders = registry.found("world", 0, 0, 0);
        elders.addResident(new Resident(new UUID(52, 1), "A", "B", Gender.FEMALE, new Traits(50, 50, 50, 50), Occupation.FARMER, true,
                -1000, null, null, Needs.initial()));
        elders.addResident(new Resident(new UUID(52, 2), "C", "D", Gender.MALE, new Traits(50, 50, 50, 50), Occupation.FARMER, true,
                -1000, null, null, Needs.initial()));
        elders.ledger().add(ResourceType.FOOD, 500);
        elders.housing().setChunk(0, 0, 6);
        assertTrue(Births.run(registry, elders, 10).isEmpty(), "elders");

        Settlement s = familyVillage(5000, 20);
        Resident daughter = Births.run(registry, s, 10).orElseThrow();
        daughter.setAdult(true); // grown, but still with no villager
        s.removeResident(s.residents().stream().filter(r -> r.gender() == Gender.FEMALE && r != daughter).findFirst().orElseThrow().id());
        // The only woman left is the daughter, who has no villager yet: no couple.
        assertTrue(Births.run(registry, s, 20).isEmpty(), "one born here with no villager yet is not chosen as a parent");
    }

    @Test
    void brotherAndSisterAreNotACouple() {
        Settlement s = registry.found("world", 0, 0, 0);
        UUID mum = new UUID(51, 1);
        Resident sister = new Resident(new UUID(51, 2), "A", "B", Gender.FEMALE, new Traits(50, 50, 50, 50), Occupation.FARMER, true,
                10_000, mum, null, Needs.initial());
        Resident brother = new Resident(new UUID(51, 3), "C", "B", Gender.MALE, new Traits(50, 50, 50, 50), Occupation.FARMER, true,
                10_000, mum, null, Needs.initial());
        s.addResident(sister);
        s.addResident(brother);
        s.ledger().add(ResourceType.FOOD, 500);
        s.housing().setChunk(0, 0, 6);
        assertTrue(Births.run(registry, s, 10).isEmpty());
    }

    @Test
    void aChildBornWithNoVillagerGrowsUpAndIsBroughtToLifeOnItsVillagersId() {
        Settlement s = familyVillage(500, 6);
        Resident child = Births.run(registry, s, 10).orElseThrow();
        UUID standIn = child.id();
        s.setLastSimulatedDay(10);
        simulator.simulateDay(s, 10 + Births.GROW_UP_DAYS);
        assertTrue(s.resident(standIn).orElseThrow().adult(), "grown up on the village's clock");

        UUID villager = new UUID(99, 99);
        Resident living = registry.bringToLife(standIn, villager).orElseThrow();
        assertEquals(villager, living.id());
        assertEquals(child.fullName(), living.fullName());
        assertEquals(child.parentA(), living.parentA());
        assertTrue(s.resident(standIn).isEmpty());
        assertTrue(Births.awaiting(s).isEmpty());
        assertTrue(registry.bringToLife(standIn, new UUID(99, 100)).isEmpty(), "only once");
    }

    // ----- R5.8 -----

    @Test
    void theRingOfEachStageGoesRoundWhatThatStageLaidOutAndTheOldRingIsWhatIsLeftOver() {
        VillagePlan plan = PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64));
        List<Works.Spot> first = Works.palisade(plan, 1);
        assertTrue(PlanGenerator.extend(plan, HeightSource.flat(64)));
        assertEquals(2, plan.stage());
        assertEquals(first, Works.palisade(plan, 1), "stage 1's ring is unchanged by growth");
        List<Works.Spot> second = Works.palisade(plan, 2);
        Rect inner = Works.extent(plan, 1).orElseThrow().inflated(Works.FENCE_MARGIN);
        Rect outer = Works.extent(plan, 2).orElseThrow().inflated(Works.FENCE_MARGIN);
        assertTrue(outer.width() * outer.depth() > inner.width() * inner.depth(), "the ring moves out");
        Set<Works.Spot> kept = new HashSet<>(second);
        List<Works.Spot> old = Works.oldRing(plan, 2);
        assertFalse(old.isEmpty());
        assertTrue(old.stream().noneMatch(kept::contains), "nothing on the new ring is taken down");
        assertTrue(Works.lights(plan, 2).containsAll(Works.lights(plan, 1)), "the lights of the old streets stay");
        assertTrue(Works.lights(plan, 2).size() > Works.lights(plan, 1).size(), "and the new streets get theirs");
        assertTrue(Works.oldRing(plan, 1).isEmpty());
        assertTrue(Works.oldRing(plan, 2, 2).isEmpty(), "a ring recorded for the same stage replaces nothing");
        assertTrue(Works.oldRing(plan, 1, 2).stream().noneMatch(s -> Works.lights(plan, 2).contains(s)), "a light is never taken down");
        assertTrue(Works.refund(10) > 0);
        int whole = Works.price(BuildingType.STREET_LIGHTS, plan).get(ResourceType.WOOD);
        int added = Works.price(BuildingType.STREET_LIGHTS, plan, 1, 2).get(ResourceType.WOOD);
        assertTrue(added < whole, "lights for the new streets cost less than lighting the whole village again");
    }

    @Test
    void whenThePlanGrowsTheVillageWantsLightsAndThenARingForTheNewStage() {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 10; i++) {
            s.addResident(person(Occupation.NITWIT));
        }
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.ledger().add(ResourceType.FOOD, 300);
        s.housing().setChunk(0, 0, 12);
        s.registerBuilding(new Building(BuildingType.MINE, 0, 64, 0, 0, "test"));
        s.recordIncident(1);
        for (BuildingType type : new BuildingType[] {BuildingType.STREET_LIGHTS, BuildingType.PALISADE}) {
            ConstructionProject done = new ConstructionProject(s.nextProjectId(), type, 1, 0, "plains", 0, 0, 0, -1, 2);
            s.addProject(done);
            Construction.finish(s, done, 3);
        }
        assertTrue(Planner.directives(s, 4, 200).stream().noneMatch(d -> d.target().equals("street_lights") || d.target().equals("palisade")));
        PlanGenerator.extend(s.plan(), HeightSource.flat(64));
        List<Planner.Directive> grown = Planner.directives(s, 5, 200);
        assertEquals("street_lights", grown.get(0).target(), grown.toString());
        assertTrue(grown.get(0).reason().contains("grown"), grown.get(0).reason());
        ConstructionProject lights = new ConstructionProject(s.nextProjectId(), BuildingType.STREET_LIGHTS, 2, 1, "plains", 0, 0, 0, -1, 5);
        s.addProject(lights);
        Construction.finish(s, lights, 6);
        assertEquals("palisade", Planner.directives(s, 7, 200).get(0).target());
    }
}
