package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R8.23: what an admin tells a village's planner and builders (lots kept empty, kinds forbidden or ordered, lots set aside). */
class PlanOverridesTest {
    private final TemplateCatalog catalog = new TemplateCatalog();
    private int next = 1;

    private Settlement village() {
        Settlement s = new Settlement(new UUID(32, 32), "Overham", "world", 0, 0, 0);
        for (int i = 0; i < 6; i++) {
            s.addResident(new Resident(new UUID(33, next++), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, 80), Occupation.UNEMPLOYED,
                    true, 10_000, null, null, Needs.initial()));
        }
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.ledger().add(ResourceType.WOOD, 200);
        s.ledger().add(ResourceType.STONE, 200);
        return s;
    }

    /** As in ConstructionTest: a 5 by 5 floor stands in for each vanilla rung. */
    private Optional<Blueprint> blueprints(TemplateCatalog.Template t) {
        Optional<Blueprint> own = catalog.blueprint(t);
        if (own.isPresent()) {
            return own;
        }
        List<Blueprint.Block> floor = new ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                floor.add(new Blueprint.Block(x, 0, z, t.tier() == 1 ? "OAK_PLANKS" : "COBBLESTONE"));
            }
        }
        return Optional.of(new Blueprint(t.key(), 5, 1, 5, floor));
    }

    private Optional<ConstructionProject> propose(Settlement s, long day) {
        return Construction.propose(s, day, "plains", 200, catalog, this::blueprints, (x, z) -> 64);
    }

    private static Map<String, Object> body(Object... pairs) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], pairs[i + 1]);
        }
        return m;
    }

    @Test
    void aVillageWithNoOverridesDecidesAsBefore() {
        Settlement s = village();
        assertEquals(BuildingType.FARM, propose(s, 5).orElseThrow().type());
    }

    @Test
    void aLotKeptEmptyIsNotBuiltOnAndOpensAgain() {
        Settlement s = village();
        List<VillagePlan.Lot> farms = s.plan().lots().stream().filter(l -> l.type() == BuildingType.FARM).toList();
        assertFalse(farms.isEmpty());
        for (VillagePlan.Lot lot : farms) {
            assertTrue(PlanOverrides.lock(s, lot.id(), true, 4, "an admin").ok());
        }
        Optional<ConstructionProject> project = propose(s, 5);
        assertTrue(project.isEmpty() || project.get().type() != BuildingType.FARM, "no farm goes on a lot kept empty");
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("kept empty by an admin")));
        assertEquals(farms.size(), PlanOverrides.lockedLots(s).size());
        for (VillagePlan.Lot lot : farms) {
            PlanOverrides.lock(s, lot.id(), false, 4, "an admin");
        }
        assertTrue(PlanOverrides.lockedLots(s).isEmpty());
        Settlement open = village();
        VillagePlan.Lot lot = open.plan().nextLot(BuildingType.FARM).orElseThrow();
        PlanOverrides.lock(open, lot.id(), true, 4, "an admin");
        PlanOverrides.lock(open, lot.id(), false, 5, "an admin");
        assertEquals(BuildingType.FARM, propose(open, 6).orElseThrow().type(), "opened again, the village builds its farm");
        assertEquals(lot.id(), propose(village(), 6).orElseThrow().lotId(), "the same best lot as before it was ever locked");
    }

    @Test
    void oneLockedLotIsSkippedForTheNextBestOne() {
        Settlement s = village();
        VillagePlan.Lot best = s.plan().nextLot(BuildingType.FARM).orElseThrow();
        PlanOverrides.lock(s, best.id(), true, 4, "an admin");
        ConstructionProject project = propose(s, 5).orElseThrow();
        assertEquals(BuildingType.FARM, project.type());
        assertNotEquals(best.id(), project.lotId());
    }

    @Test
    void aKindForbiddenIsNeitherAskedForNorBuilt() {
        Settlement s = village();
        assertTrue(Planner.directives(s, 5, 200).stream().anyMatch(d -> d.kind() == Planner.Kind.BUILD && d.target().equals("farm")));
        assertTrue(PlanOverrides.forbid(s, BuildingType.FARM, true, 4, "an admin").ok());
        assertTrue(PlanOverrides.isForbidden(s, BuildingType.FARM));
        assertTrue(Planner.directives(s, 5, 200).stream().noneMatch(d -> d.kind() == Planner.Kind.BUILD && d.target().equals("farm")));
        Optional<ConstructionProject> project = propose(s, 5);
        assertTrue(project.isEmpty() || project.get().type() != BuildingType.FARM);
        PlanOverrides.forbid(s, BuildingType.FARM, false, 6, "an admin");
        assertEquals(List.of(), PlanOverrides.forbidden(s));
        Settlement allowed = village();
        PlanOverrides.forbid(allowed, BuildingType.FARM, true, 4, "an admin");
        PlanOverrides.forbid(allowed, BuildingType.FARM, false, 5, "an admin");
        assertEquals(BuildingType.FARM, propose(allowed, 6).orElseThrow().type());
    }

    @Test
    void aKindOrderedNextComesFirstAndTheOrderEndsWhenItIsQueued() {
        Settlement s = village();
        assertTrue(PlanOverrides.buildNext(s, BuildingType.HOUSE, true, 4, "an admin").ok());
        assertEquals(Optional.of(BuildingType.HOUSE), PlanOverrides.buildNext(s, 5));
        assertEquals("house", Planner.directives(s, 5, 200).get(0).target());
        ConstructionProject project = propose(s, 5).orElseThrow();
        assertEquals(BuildingType.HOUSE, project.type(), "ahead of the farm the village wanted first");
        assertEquals(Optional.empty(), PlanOverrides.buildNext(s, 5), "the order is spent once a project is queued");
    }

    @Test
    void aVillageThatWantsNothingStillBuildsWhatItWasToldAndAnEmptyVillageIsAskedNothing() {
        Settlement s = village();
        PlanOverrides.buildNext(s, BuildingType.SHOP, true, 4, "an admin");
        List<Planner.Directive> out = Planner.withOverrides(s, List.of(), 5);
        assertEquals(1, out.size());
        assertEquals("shop", out.get(0).target());
        assertEquals(Planner.Kind.BUILD, out.get(0).kind());
        assertEquals(List.of(), Planner.withOverrides(new Settlement(new UUID(32, 40), "Empty", "world", 0, 0, 0), List.of(), 5));
    }

    @Test
    void anOrderedKindIsNotListedTwiceWhenTheVillageWantedItToo() {
        Settlement s = village();
        List<Planner.Directive> own = List.of(new Planner.Directive(Planner.Tier.FOOD, Planner.Kind.BUILD, "farm", "hungry"),
                new Planner.Directive(Planner.Tier.GROWTH, Planner.Kind.BUILD, "house", "room"));
        PlanOverrides.buildNext(s, BuildingType.HOUSE, true, 4, "an admin");
        List<Planner.Directive> out = Planner.withOverrides(s, own, 5);
        assertEquals(List.of("house", "farm"), out.stream().map(Planner.Directive::target).toList());
    }

    @Test
    void anOrderLapsesAfterItsDaysAndOnlyOneStandsAtATime() {
        Settlement s = village();
        PlanOverrides.buildNext(s, BuildingType.HOUSE, true, 4, "an admin");
        PlanOverrides.buildNext(s, BuildingType.SHOP, true, 4, "an admin");
        assertEquals(Optional.of(BuildingType.SHOP), PlanOverrides.buildNext(s, 5));
        assertEquals(Optional.empty(), PlanOverrides.buildNext(s, 4 + PlanOverrides.BUILD_NEXT_DAYS + 1));
        assertTrue(PlanOverrides.buildNext(s, BuildingType.SHOP, false, 5, "an admin").ok());
        assertEquals(Optional.empty(), PlanOverrides.buildNext(s, 5));
    }

    @Test
    void aForbiddenKindCannotBeOrderedAndForbiddingWithdrawsAnOrder() {
        Settlement s = village();
        PlanOverrides.buildNext(s, BuildingType.HOUSE, true, 4, "an admin");
        PlanOverrides.forbid(s, BuildingType.HOUSE, true, 5, "an admin");
        assertEquals(Optional.empty(), PlanOverrides.buildNext(s, 5));
        assertFalse(PlanOverrides.buildNext(s, BuildingType.HOUSE, true, 5, "an admin").ok());
    }

    @Test
    void anUnbuiltLotCanBeSetAsideForAnotherKindButNotABuiltOneOrAWork() {
        Settlement s = village();
        VillagePlan.Lot house = s.plan().lots().stream().filter(l -> l.type() == BuildingType.HOUSE).findFirst().orElseThrow();
        assertTrue(PlanOverrides.retype(s, house.id(), BuildingType.SHOP, 4, "an admin").ok());
        assertEquals(BuildingType.SHOP, s.plan().lots().stream().filter(l -> l.id() == house.id()).findFirst().orElseThrow().type());
        assertEquals(house.rect(), s.plan().lots().stream().filter(l -> l.id() == house.id()).findFirst().orElseThrow().rect());
        assertFalse(PlanOverrides.retype(s, house.id(), BuildingType.PALISADE, 4, "an admin").ok(), "a palisade has no lot");
        assertFalse(PlanOverrides.retype(s, 99_999, BuildingType.SHOP, 4, "an admin").ok());
        s.plan().fill(house.id());
        assertFalse(PlanOverrides.retype(s, house.id(), BuildingType.HOUSE, 4, "an admin").ok(), "built on");
        assertFalse(PlanOverrides.lock(s, house.id(), true, 4, "an admin").ok(), "built on");
    }

    @Test
    void anOrderedProjectIsNotDroppedTheNextDayAsNotNeeded() {
        Settlement s = village();
        PlanOverrides.buildNext(s, BuildingType.SHOP, true, 4, "an admin");
        ConstructionProject shop = propose(s, 5).orElseThrow();
        assertEquals(BuildingType.SHOP, shop.type());
        assertEquals(Optional.empty(), PlanOverrides.buildNext(s, 5), "the order is spent");
        assertTrue(propose(s, 6).isEmpty(), "one project at a time");
        ConstructionProject still = s.openProject().orElseThrow();
        assertEquals(shop.id(), still.id(), "the village did not give up the shop it was ordered to build");
        assertEquals(ConstructionProject.Status.QUEUED, still.status());
    }

    @Test
    void theTownSquareCannotBeOrderedOrMadeALotKindButMayBeForbidden() {
        Settlement s = village();
        assertFalse(PlanOverrides.buildNext(s, BuildingType.SQUARE, true, 4, "an admin").ok());
        assertEquals(Optional.empty(), PlanOverrides.buildNext(s, 5));
        assertFalse(PlanOverrides.retype(s, s.plan().lots().get(0).id(), BuildingType.SQUARE, 4, "an admin").ok());
        assertTrue(PlanOverrides.forbid(s, BuildingType.SQUARE, true, 4, "an admin").ok());
        SettlementRegistry registry = new SettlementRegistry();
        registry.add(s);
        assertFalse(AdminAction.check(body("action", "build-next", "village", s.id().toString(), "building", "square"), registry).ok());
        assertTrue(AdminAction.check(body("action", "forbid", "village", s.id().toString(), "building", "square"), registry).ok());
        assertTrue(AdminAction.buildingKinds().stream().anyMatch(k -> "SQUARE".equals(k.get("type")) && Boolean.FALSE.equals(k.get("next"))));
    }

    @Test
    void aLotWithAProjectOnItCannotBeLockedOrSetAsideButCanAlwaysBeOpened() {
        Settlement s = village();
        ConstructionProject farm = propose(s, 5).orElseThrow();
        assertEquals(BuildingType.FARM, farm.type());
        assertFalse(PlanOverrides.lock(s, farm.lotId(), true, 6, "an admin").ok(), "a farm is already being raised on it");
        assertFalse(PlanOverrides.retype(s, farm.lotId(), BuildingType.SHOP, 6, "an admin").ok());
        assertTrue(PlanOverrides.lockedLots(s).isEmpty());
        // a lock left on a lot that has since been built on can still be lifted
        s.conditions().put(PlanOverrides.LOCKED + farm.lotId(), 1L);
        s.plan().fill(farm.lotId());
        assertTrue(PlanOverrides.lock(s, farm.lotId(), false, 7, "an admin").ok());
        assertTrue(PlanOverrides.lockedLots(s).isEmpty());
    }

    @Test
    void aReplanForgetsTheLotsKeptEmpty() {
        Settlement s = village();
        PlanOverrides.lock(s, s.plan().lots().get(0).id(), true, 4, "an admin");
        PlanOverrides.forbid(s, BuildingType.SMITHY, true, 4, "an admin");
        PlanOverrides.forgetLots(s);
        assertTrue(PlanOverrides.lockedLots(s).isEmpty());
        assertTrue(s.conditions().keySet().stream().noneMatch(k -> k.startsWith(PlanOverrides.LOCKED)));
        assertTrue(PlanOverrides.isForbidden(s, BuildingType.SMITHY), "a rule about a kind is not about the old plan");
    }

    @Test
    void anOrderForWhatTheVillageAlreadyWantsKeepsItsTier() {
        Settlement s = village();
        Planner.Directive own = new Planner.Directive(Planner.Tier.FOOD, Planner.Kind.BUILD, "farm", "hungry");
        PlanOverrides.buildNext(s, BuildingType.FARM, true, 4, "an admin");
        Planner.Directive ordered = Planner.withOverrides(s, List.of(own), 5).get(0);
        assertEquals(Planner.Tier.FOOD, ordered.tier());
        assertTrue(ordered.reason().startsWith(PlanOverrides.ORDER_REASON) && ordered.reason().contains("hungry"), ordered.reason());
        assertEquals(1, Planner.withOverrides(s, List.of(own), 5).size());
    }

    @Test
    void whatAnAdminHoldsBackIsSaidInTheBlockers() {
        Settlement s = village();
        assertTrue(VillageBlockers.of(s, 5, 200).stream().noneMatch(b -> b.contains("admin")));
        PlanOverrides.forbid(s, BuildingType.FARM, true, 4, "an admin");
        assertTrue(VillageBlockers.of(s, 5, 200).stream().anyMatch(b -> b.equals("wants a farm but an admin forbade it")),
                VillageBlockers.of(s, 5, 200).toString());
        PlanOverrides.buildNext(s, BuildingType.SHOP, true, 4, "an admin");
        assertTrue(VillageBlockers.of(s, 5, 200).stream().anyMatch(b -> b.startsWith("an admin ordered a shop next")));
    }

    @Test
    void lockingEveryLotOfAKindMakesTheVillageLookForGroundElsewhere() {
        Settlement s = village();
        for (VillagePlan.Lot lot : s.plan().lots()) {
            if (lot.type() == BuildingType.FARM) {
                PlanOverrides.lock(s, lot.id(), true, 4, "an admin");
            }
        }
        Optional<ConstructionProject> project = propose(s, 5);
        assertTrue(project.isEmpty() || project.get().type() != BuildingType.FARM);
        assertTrue(s.conditions().containsKey(Construction.OUTGROWN), "the village has outgrown the lots it may use: the plan grows");
    }

    @Test
    void theOverridesSurviveASaveBecauseTheyLiveInTheConditions() {
        Settlement s = village();
        PlanOverrides.forbid(s, BuildingType.SMITHY, true, 4, "an admin");
        PlanOverrides.lock(s, s.plan().lots().get(0).id(), true, 4, "an admin");
        PlanOverrides.buildNext(s, BuildingType.SHOP, true, 4, "an admin");
        Settlement back = SettlementCodec.decode(SettlementCodec.encode(s));
        assertTrue(PlanOverrides.isForbidden(back, BuildingType.SMITHY));
        assertEquals(1, PlanOverrides.lockedLots(back).size());
        assertEquals(Optional.of(BuildingType.SHOP), PlanOverrides.buildNext(back, 5));
    }

    @Test
    void theRequestsAreCheckedBeforeAnythingRuns() {
        Settlement s = village();
        SettlementRegistry registry = new SettlementRegistry();
        registry.add(s);
        String id = s.id().toString();
        int lot = s.plan().lots().get(0).id();
        assertTrue(AdminAction.check(body("action", "lock-lot", "village", id, "lot", lot), registry).ok());
        assertTrue(AdminAction.check(body("action", "retype-lot", "village", id, "lot", lot, "building", "shop"), registry).ok());
        assertFalse(AdminAction.check(body("action", "lock-lot", "village", id, "lot", 99_999), registry).ok());
        assertFalse(AdminAction.check(body("action", "lock-lot", "village", id), registry).ok());
        assertFalse(AdminAction.check(body("action", "lock-lot", "village", id, "lot", "1; drop"), registry).ok());
        assertFalse(AdminAction.check(body("action", "retype-lot", "village", id, "lot", lot, "building", "palisade"), registry).ok());
        assertFalse(AdminAction.check(body("action", "retype-lot", "village", id, "lot", lot, "building", "gatehouse"), registry).ok());
        assertTrue(AdminAction.check(body("action", "forbid", "village", id, "building", "palisade"), registry).ok(), "a palisade may be forbidden");
        assertTrue(AdminAction.check(body("action", "build-next", "village", id, "building", "guard-post"), registry).ok());
        assertFalse(AdminAction.check(body("action", "forbid", "village", id, "building", "gatehouse"), registry).ok(), "a part is not named");
        assertFalse(AdminAction.check(body("action", "forbid", "village", id, "building", "wizard tower"), registry).ok());
        assertFalse(AdminAction.check(body("action", "forbid", "village", id, "building", "x".repeat(40)), registry).ok());
        Settlement bare = new Settlement(new UUID(32, 33), "Planless", "world", 0, 0, 0);
        registry.add(bare);
        assertFalse(AdminAction.check(body("action", "lock-lot", "village", bare.id().toString(), "lot", 1), registry).ok(), "no plan yet");
    }

    @Test
    void anOverrideRunsThroughTheSharedFunctionAndIsAuditedWithItsKind() {
        Settlement s = village();
        SettlementRegistry registry = new SettlementRegistry();
        registry.add(s);
        AdminAction.Request request = AdminAction.check(body("action", "forbid", "village", s.id().toString(), "building", "shop"), registry).request();
        PlanOverrides.Result result = AdminAction.applyOverride(s, request, 5, "an admin (test)");
        assertTrue(result.ok());
        assertTrue(PlanOverrides.isForbidden(s, BuildingType.SHOP));
        String line = AdminAction.auditLine(java.time.Instant.parse("2026-10-16T12:00:00Z"), "test", request, s.name(), true, result.message());
        assertTrue(line.contains("| forbid | Overham | shop | ok |"), line);
        assertTrue(AdminAction.buildingKinds().stream().noneMatch(k -> "GATEHOUSE".equals(k.get("type"))));
        assertTrue(AdminAction.buildingKinds().stream().anyMatch(k -> "PALISADE".equals(k.get("type")) && Boolean.FALSE.equals(k.get("lot"))));
    }
}
