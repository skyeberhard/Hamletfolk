package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R5.10: the rampart: walls, gates and towers on the palisade's ring, tier by tier. */
class RampartTest {
    private final TemplateCatalog catalog = new TemplateCatalog();
    private final VillagePlan plan = PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64));
    private int next = 1;

    private static boolean has(Rampart.Piece piece, int dy, String material) {
        return piece.blocks().stream().anyMatch(b -> b.dy() == dy && b.material().equals(material) && !b.ifEmpty());
    }

    // ----- geometry -----

    @Test
    void thereIsNothingToBuildForTheFenceOrWithoutARing() {
        assertTrue(Rampart.pieces(plan, 1, 1).isEmpty(), "tier 1 is the fence");
        assertTrue(Rampart.pieces(null, 1, 2).isEmpty());
        assertFalse(Rampart.pieces(plan, 1, 2).isEmpty());
    }

    @Test
    void everyPieceStandsOnTheRingAndNoWallStandsInAGate() {
        Works.Ring ring = Works.ring(plan, 1).orElseThrow();
        Set<Works.Spot> onRing = new HashSet<>(ring.cells());
        Set<Works.Spot> gaps = new HashSet<>();
        for (int i = 0; i < ring.cells().size(); i++) {
            if (ring.gap()[i]) {
                gaps.add(ring.cells().get(i));
            }
        }
        assertFalse(gaps.isEmpty(), "streets leave the ring");
        for (int tier = Rampart.FIRST_TIER; tier <= Rampart.LAST_TIER; tier++) {
            List<Rampart.Piece> pieces = Rampart.pieces(plan, 1, tier);
            Set<Works.Spot> anchors = new HashSet<>();
            for (Rampart.Piece piece : pieces) {
                Works.Spot at = new Works.Spot(piece.x(), piece.z());
                assertTrue(onRing.contains(at), "tier " + tier + ": " + at + " is on the ring");
                assertTrue(!gaps.contains(at), "nothing is anchored in a gate");
                assertTrue(anchors.add(at), "no column built twice");
            }
            for (Works.Spot gap : gaps) {
                // the lintel over each cell of the gap rides on a pillar: one block at height four, three of clearance beneath
                long lintels = pieces.stream().flatMap(p -> p.blocks().stream().filter(b -> p.x() + b.dx() == gap.x()
                        && p.z() + b.dz() == gap.z() && !b.ifEmpty())).count();
                assertEquals(1, lintels, "tier " + tier + ": one lintel block over " + gap);
                assertTrue(pieces.stream().flatMap(p -> p.blocks().stream().filter(b -> p.x() + b.dx() == gap.x()
                        && p.z() + b.dz() == gap.z())).allMatch(b -> b.dy() == 4), "at height four");
            }
        }
    }

    @Test
    void aWallColumnIsPlanksUnderARailInTierTwoAndStoneUnderAWallInTierThree() {
        Rampart.Piece wall2 = Rampart.pieces(plan, 1, 2).stream().filter(p -> p.blocks().size() == 3).findFirst().orElseThrow();
        assertTrue(has(wall2, 1, "OAK_PLANKS") && has(wall2, 2, "OAK_PLANKS") && has(wall2, 3, "OAK_FENCE"));
        Rampart.Piece wall3 = Rampart.pieces(plan, 1, 3).stream().filter(p -> p.blocks().size() == 4).findFirst().orElseThrow();
        assertTrue(has(wall3, 1, "STONE_BRICKS") && has(wall3, 3, "STONE_BRICKS") && has(wall3, 4, "STONE_BRICK_WALL"));
    }

    @Test
    void eachGateHasAPillarEitherSideAndTheyAreTallerThanTheLintel() {
        for (int tier = Rampart.FIRST_TIER; tier <= Rampart.LAST_TIER; tier++) {
            List<Rampart.Piece> pillars = Rampart.pieces(plan, 1, tier).stream()
                    .filter(p -> p.blocks().stream().anyMatch(b -> b.material().equals("TORCH") && b.dy() >= 6)
                            && p.blocks().stream().noneMatch(b -> b.material().startsWith("LADDER"))).toList();
            Works.Ring ring = Works.ring(plan, 1).orElseThrow();
            int gates = 0;
            int n = ring.cells().size();
            for (int i = 0; i < n; i++) {
                if (ring.gap()[i] && !ring.gap()[(i - 1 + n) % n]) {
                    gates++;
                }
            }
            assertTrue(gates > 0);
            assertTrue(pillars.size() >= gates, "tier " + tier + ": a pillar on at least one side of each of the " + gates + " gates: " + pillars.size());
            assertTrue(pillars.stream().allMatch(p -> p.blocks().stream().filter(b -> !b.ifEmpty() && !b.material().equals("TORCH"))
                    .mapToInt(Rampart.Block::dy).max().orElse(0) >= 5), "pillars rise above the lintel at 4");
        }
    }

    @Test
    void aTowerStandsAtEachCornerWithALadderAPlatformAParapetAndATorch() {
        for (int tier = Rampart.FIRST_TIER; tier <= Rampart.LAST_TIER; tier++) {
            List<Rampart.Piece> towers = Rampart.pieces(plan, 1, tier).stream()
                    .filter(p -> p.blocks().stream().anyMatch(b -> b.material().startsWith("LADDER"))).toList();
            assertEquals(4, towers.size(), "one at each corner");
            int height = tier == 3 ? 6 : 4;
            for (Rampart.Piece tower : towers) {
                long platform = tower.blocks().stream().filter(b -> b.dy() == height + 1 && !b.material().startsWith("LADDER")).count();
                assertEquals(8, platform, "the platform round the hatch");
                long parapet = tower.blocks().stream().filter(b -> b.dy() == height + 2).count();
                assertEquals(5, parapet, "along the two outer edges");
                assertEquals(1, tower.blocks().stream().filter(b -> b.material().equals("TORCH")).count());
                assertEquals(height + 1, tower.blocks().stream().filter(b -> b.material().startsWith("LADDER")).count());
                long doorway = tower.blocks().stream().filter(b -> b.dy() <= 2 && b.dy() >= 1 && !b.ifEmpty()
                        && !b.material().startsWith("LADDER")).count();
                assertEquals(8 * 2 - 2, doorway, "a doorway two high in the side facing inward");
                assertTrue(tower.blocks().stream().anyMatch(b -> b.ifEmpty() && b.dy() == -2), "footed where the ground falls away");
            }
            assertTrue(towers.stream().map(p -> p.x() + "," + p.z()).distinct().count() == 4);
        }
        // the ladder leans on the outer wall: facing south from a north corner
        Works.Ring ring = Works.ring(plan, 1).orElseThrow();
        Rampart.Piece northWest = Rampart.pieces(plan, 1, 2).stream()
                .filter(p -> p.x() == ring.rect().x() && p.z() == ring.rect().z()).findFirst().orElseThrow();
        assertTrue(northWest.blocks().stream().anyMatch(b -> b.material().equals("LADDER[facing=south]")));
        Rampart.Piece southEast = Rampart.pieces(plan, 1, 2).stream()
                .filter(p -> p.x() == ring.rect().maxX() && p.z() == ring.rect().maxZ()).findFirst().orElseThrow();
        assertTrue(southEast.blocks().stream().anyMatch(b -> b.material().equals("LADDER[facing=north]")));
    }

    @Test
    void aTierReplacesExactlyWhatTheTierBelowPutThere() {
        Works.Ring ring = Works.ring(plan, 1).orElseThrow();
        java.util.Map<Rampart.Pos, String> two = Rampart.replaced(plan, 1, 2);
        assertEquals(Works.palisade(plan, 1).size(), two.size(), "only the fence posts of the ring");
        assertTrue(two.values().stream().allMatch("OAK_FENCE"::equals));
        assertTrue(two.keySet().stream().allMatch(p -> p.dy() == 1));
        java.util.Map<Rampart.Pos, String> three = Rampart.replaced(plan, 1, 3);
        List<Rampart.Piece> tier2 = Rampart.pieces(plan, 1, 2);
        // a plain wall column that no tower or pillar reaches over
        Rampart.Piece wall = tier2.stream().filter(p -> p.blocks().size() == 3)
                .filter(p -> tier2.stream().filter(o -> o != p).noneMatch(o -> o.blocks().stream()
                        .anyMatch(b -> o.x() + b.dx() == p.x() && o.z() + b.dz() == p.z())))
                .findFirst().orElseThrow();
        assertEquals("OAK_PLANKS", three.get(new Rampart.Pos(wall.x(), 1, wall.z())));
        assertEquals("OAK_FENCE", three.get(new Rampart.Pos(wall.x(), 3, wall.z())));
        assertEquals(null, three.get(new Rampart.Pos(wall.x(), 4, wall.z())), "nothing was above the rail");
        assertTrue(three.values().stream().noneMatch(m -> m.contains("[")), "bare names");
        assertTrue(three.containsValue("LADDER") && three.containsValue("TORCH"));
        assertTrue(three.size() > two.size());
        // a lintel of the tier below is replaced where it hung over the gap
        Works.Spot gap = java.util.stream.IntStream.range(0, ring.cells().size()).filter(i -> ring.gap()[i]).mapToObj(i -> ring.cells().get(i))
                .findFirst().orElseThrow();
        assertEquals("OAK_PLANKS", three.get(new Rampart.Pos(gap.x(), 4, gap.z())));
    }

    @Test
    void aStrongerTierCostsMoreAndTheFoundationsAreFree() {
        int fence = Works.price(BuildingType.PALISADE, plan).get(ResourceType.WOOD);
        int planks = Rampart.price(plan, 1, 2).get(ResourceType.WOOD);
        int stone = Rampart.price(plan, 1, 3).get(ResourceType.STONE);
        assertTrue(planks > fence, "planks two high cost more than posts: " + planks + " against " + fence);
        assertTrue(stone > 0 && Rampart.price(plan, 1, 3).getOrDefault(ResourceType.WOOD, 0) < planks, "stone, not wood");
        int before = Construction.costPercent();
        try {
            Construction.setCostPercent(35);
            assertTrue(Rampart.price(plan, 1, 2).get(ResourceType.WOOD) < planks);
        } finally {
            Construction.setCostPercent(before);
        }
    }

    @Test
    void aLaterStageGetsALargerRingAndTheOldOneRemainsAsAWall() {
        VillagePlan grown = PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64));
        assertTrue(PlanGenerator.extend(grown, HeightSource.flat(64)));
        Works.Spot innerCorner = new Works.Spot(Works.ring(grown, 1).orElseThrow().rect().x(), Works.ring(grown, 1).orElseThrow().rect().z());
        Set<Works.Spot> second = new HashSet<>();
        Rampart.pieces(grown, 2, 2).forEach(p -> second.add(new Works.Spot(p.x(), p.z())));
        assertFalse(second.contains(innerCorner), "the new ring is outside the old one");
        assertTrue(Rampart.pieces(grown, 2, 2).size() > Rampart.pieces(grown, 1, 2).size());
    }

    // ----- when it is wanted -----

    private Settlement town(int people, int attackedDay) {
        Settlement s = new Settlement(UUID.randomUUID(), "Wallham", "world", 0, 0, 0);
        for (int i = 0; i < people; i++) {
            s.addResident(new Resident(new UUID(110, next++), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50), Occupation.NITWIT, true,
                    10_000, null, null, Needs.initial()));
        }
        s.setPlan(plan);
        s.ledger().add(Commodity.BREAD, 5000);
        s.ledger().add(Commodity.PLANKS, 5000);
        s.ledger().add(Commodity.COBBLESTONE, 5000);
        s.ledger().add(Commodity.STONE_BLOCKS, 5000);
        s.housing().setChunk(0, 0, people + 3);
        s.registerBuilding(new Building(BuildingType.MINE, 2000, 64, 2000, 0, "a player"));
        s.registerBuilding(new Building(BuildingType.SHOP, 2010, 64, 2000, 0, "a player"));
        s.registerBuilding(new Building(BuildingType.TREASURY, 2020, 64, 2000, 0, "a player"));
        s.setLastSimulatedDay(attackedDay + 5);
        s.recordIncident(attackedDay);
        for (BuildingType type : new BuildingType[] {BuildingType.STREET_LIGHTS, BuildingType.PALISADE}) {
            ConstructionProject done = new ConstructionProject(s.nextProjectId(), type, 1, 0, "plains", 0, 0, 0, -1, attackedDay);
            s.addProject(done);
            Construction.finish(s, done, attackedDay + 1);
        }
        return s;
    }

    private static boolean wantsRampart(Settlement s) {
        return Planner.directives(s, s.lastSimulatedDay(), 200).stream().anyMatch(d -> d.target().equals("rampart"));
    }

    @Test
    void aTownThatHasBeenThreatenedWantsPlanksAndACityWantsStoneAfterwards() {
        Settlement village = town(10, 20);
        assertFalse(wantsRampart(village), "a village is content with its fence");
        Settlement town = town(25, 20);
        assertTrue(wantsRampart(town));
        assertEquals(2, Planner.wallTierWanted(town, 25));
        assertEquals(1, Construction.wallTier(town, 1), "the fence");

        Settlement quiet = town(25, 20);
        quiet.setLastSimulatedDay(400); // attacked long ago, and nothing since
        assertFalse(wantsRampart(quiet), "not threatened any more");

        Settlement city = town(55, 20);
        assertEquals(Rampart.LAST_TIER, Planner.wallTierWanted(city, 25));
        ConstructionProject planks = new ConstructionProject(city.nextProjectId(), BuildingType.RAMPART, 2, 1, "plains", 0, 0, 0, -1, 22);
        planks.setStage(1);
        city.addProject(planks);
        assertFalse(wantsRampart(city), "not while one is being built");
        Construction.finish(city, planks, 23);
        assertEquals(2, Construction.wallTier(city, 1));
        assertTrue(wantsRampart(city), "now the stone");
        ConstructionProject stone = new ConstructionProject(city.nextProjectId(), BuildingType.RAMPART, 3, 2, "plains", 0, 0, 0, -1, 30);
        stone.setStage(1);
        city.addProject(stone);
        Construction.finish(city, stone, 31);
        assertEquals(3, Construction.wallTier(city, 1));
        assertFalse(wantsRampart(city), "as strong as it gets");
    }

    @Test
    void aGrownPlanNeedsItsOwnFenceBeforeARampartAndTheOldRampartIsLeftAsAnInnerWall() {
        Settlement city = town(55, 20);
        ConstructionProject planks = new ConstructionProject(city.nextProjectId(), BuildingType.RAMPART, 2, 1, "plains", 0, 0, 0, -1, 22);
        planks.setStage(1);
        city.addProject(planks);
        Construction.finish(city, planks, 23);
        assertTrue(PlanGenerator.extend(city.plan(), HeightSource.flat(64)));
        assertEquals(0, Construction.wallTier(city, 2), "nothing yet round the larger ring");
        assertFalse(wantsRampart(city), "the fence for the new ring comes first");
        assertTrue(Planner.directives(city, city.lastSimulatedDay(), 200).stream().anyMatch(d -> d.target().equals("street_lights")
                || d.target().equals("palisade")));
        assertTrue(Rampart.pieces(city.plan(), 1, 2).size() > 0, "the old rampart's pieces are still described, and not taken down");
    }

    // ----- building it -----

    @Test
    void aTownBuildsItWhenItCanPayAndTheRestOfItsBuildingIsNotHeldUp() {
        Settlement s = town(25, 20);
        // Its shop and treasury exist; a house is wanted, because every bed is taken: the rampart must not stop that.
        s.housing().setChunk(0, 0, 25);
        Optional<ConstructionProject> project = Construction.propose(s, 26, "plains", 200, catalog, t -> catalog.blueprint(t), (x, z) -> 64);
        assertTrue(project.isPresent());
        // lights and the fence are done, so the first thing asked for is the rampart (ahead of a house)
        assertEquals(BuildingType.RAMPART, project.get().type(), s.history().toString());
        assertEquals(Rampart.FIRST_TIER, project.get().tier());
        assertEquals(1, project.get().stage());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("rampart of planks")));
    }

    @Test
    void ATownThatCannotYetPayNotesWhatItLacksAndGoesOnBuildingOtherThings() {
        Settlement s = town(25, 20);
        s.ledger().take(ResourceType.WOOD, 100_000);
        s.ledger().take(ResourceType.STONE, 100_000);
        s.ledger().add(Commodity.PLANKS, 150); // enough for a house, under the 245 (70% of the 350 storage) a rampart needs to start
        s.ledger().add(Commodity.COBBLESTONE, 300);
        s.housing().setChunk(0, 0, 25); // every bed is taken: a house is a growth want
        // (the game's own house pieces have no blocks in core, so stand in a plain floor of planks, as ConstructionTest does)
        Optional<ConstructionProject> project = Construction.propose(s, 26, "plains", 200, catalog, t -> catalog.blueprint(t).or(() ->
                Optional.of(new Blueprint(t.key(), 5, 1, 5, java.util.stream.IntStream.range(0, 25)
                        .mapToObj(i -> new Blueprint.Block(i % 5, 0, i / 5, "OAK_PLANKS")).toList()))), (x, z) -> 64);
        assertTrue(Construction.lacking(s, 26).contains(ResourceType.WOOD), "wood is asked for, so a lumberjack is taken on");
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("rampart") && e.text().contains("cannot start yet")));
        assertTrue(project.isPresent(), "the rampart does not hold the village's other building up: " + s.history());
        assertNotEquals(BuildingType.RAMPART, project.get().type(), "it builds something else (here the town square) meanwhile");
    }

    @Test
    void aVillageThatHasNotBeenAttackedLatelyWantsNoRampartHoweverBigOrWary() {
        Settlement city = town(55, 20);
        city.setLastSimulatedDay(20 + Planner.THREAT_MEMORY_DAYS + 1);
        assertEquals(1, Planner.wallTierWanted(city, city.lastSimulatedDay()), "the attack is more than three months back");
        city.setLastSimulatedDay(20 + Planner.THREAT_MEMORY_DAYS - 1);
        assertEquals(Rampart.LAST_TIER, Planner.wallTierWanted(city, city.lastSimulatedDay()));
    }

    @Test
    void aFormatTwentyFiveSaveStillLoadsWithEveryProjectAtStageOne() {
        Settlement s = town(25, 20);
        java.util.Map<String, Object> old = new java.util.LinkedHashMap<>(SettlementCodec.encode(s));
        old.put("format", 25);
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> projects = (List<java.util.Map<String, Object>>) old.get("projects");
        projects.forEach(p -> p.remove("stage"));
        Settlement loaded = SettlementCodec.decode(old);
        assertEquals(s.projects().size(), loaded.projects().size());
        assertTrue(loaded.projects().stream().allMatch(p -> p.stage() == 1));
        assertEquals(1, Construction.wallTier(loaded, 1));
    }

    @Test
    void theStageOfARampartIsSaved() {
        Settlement s = town(25, 20);
        ConstructionProject p = new ConstructionProject(s.nextProjectId(), BuildingType.RAMPART, 3, 2, "plains", 0, 0, 0, -1, 22);
        p.setStage(2);
        s.addProject(p);
        Construction.finish(s, p, 23);
        Settlement loaded = SettlementCodec.decode(SettlementCodec.encode(s));
        ConstructionProject back = loaded.projects().stream().filter(q -> q.type() == BuildingType.RAMPART).findFirst().orElseThrow();
        assertEquals(2, back.stage());
        assertEquals(3, back.tier());
        assertEquals(3, Construction.wallTier(loaded, 2));
        assertNotEquals(3, Construction.wallTier(loaded, 3), "not for a larger plan");
        assertTrue(BuildingType.RAMPART.isWorks() && BuildingType.fromSign("[Rampart]").isEmpty(), "never registered by a sign");
    }
}
