package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** R4.56: skill gates improvement; R4.57: what a skilled hand makes. */
class GuildTest {
    private final TemplateCatalog catalog = new TemplateCatalog();
    private int next = 1;
    private boolean before;

    @BeforeEach
    void on() {
        before = Guild.enforced();
        Guild.setEnforced(true);
    }

    @AfterEach
    void restore() {
        Guild.setEnforced(before);
    }

    private Resident person(Occupation job, int days) {
        Resident r = new Resident(new UUID(80, next++), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, 50), job, true, 10_000, null, null, Needs.initial());
        if (days > 0) {
            r.restoreXp(job, days);
        }
        return r;
    }

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

    /** A village with a farm its builders raised, and the stores to improve it, but only the people the test adds. */
    private Settlement withFarm(Resident... extra) {
        Settlement s = new Settlement(new UUID(81, 1), "Guildford", "world", 0, 0, 0);
        for (int i = 0; i < 6; i++) {
            s.addResident(person(Occupation.UNEMPLOYED, 0));
        }
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.ledger().add(ResourceType.WOOD, 200);
        s.ledger().add(ResourceType.STONE, 200);
        s.ledger().take(ResourceType.WOOD, 170);
        s.ledger().take(ResourceType.STONE, 200);
        Guild.setEnforced(false); // (the first tier is never gated, but the planner's other decisions are not what this is about)
        ConstructionProject farm = Construction.propose(s, 5, "plains", 200, catalog, this::blueprints, (x, z) -> 64).orElseThrow();
        Guild.setEnforced(true);
        farm.setSign(farm.x(), 65, farm.z() - 1);
        s.registerBuilding(new Building(BuildingType.FARM, farm.x(), 65, farm.z() - 1, 5, "village"));
        Construction.finish(s, farm, 6);
        for (BuildingType type : BuildingType.values()) {
            if (s.buildingCount(type) == 0) {
                s.registerBuilding(new Building(type, 1000 + type.ordinal(), 64, 1000, 0, "a player"));
            }
        }
        for (Resident r : extra) {
            s.addResident(r);
        }
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.STONE, 100);
        s.housing().setChunk(0, 0, 20);
        s.setLastSimulatedDay(20);
        return s;
    }

    @Test
    void tierTwoNeedsAnApprenticeOfTheTradeAndTierThreeAJourneyman() {
        Settlement none = withFarm();
        assertEquals(0, Guild.skill(none, BuildingType.FARM));
        assertEquals(1, Guild.tierCap(none, BuildingType.FARM), "a first tier is never held back");
        assertTrue(Guild.refusal(none, BuildingType.FARM, 2).orElseThrow().contains("apprentice farmer"));
        Settlement novice = withFarm(person(Occupation.FARMER, 3));
        assertEquals(1, Guild.skill(novice, BuildingType.FARM));
        assertTrue(Guild.refusal(novice, BuildingType.FARM, 2).isPresent());
        Settlement apprentice = withFarm(person(Occupation.FARMER, 10));
        assertEquals(2, Guild.skill(apprentice, BuildingType.FARM));
        assertTrue(Guild.refusal(apprentice, BuildingType.FARM, 2).isEmpty());
        assertTrue(Guild.refusal(apprentice, BuildingType.FARM, 3).orElseThrow().contains("journeyman farmer"));
    }

    @Test
    void anUnskilledVillageDoesNotUpgradeButASkilledOneDoes() {
        Settlement s = withFarm(person(Occupation.UNEMPLOYED, 0));
        assertTrue(Construction.propose(s, 30, "plains", 200, catalog, this::blueprints, (x, z) -> 68).isEmpty(), "nobody skilled: no upgrade");
        Settlement skilled = withFarm(person(Occupation.FARMER, 30));
        Optional<ConstructionProject> project = Construction.propose(skilled, 30, "plains", 200, catalog, this::blueprints, (x, z) -> 68);
        assertTrue(project.isPresent(), "a journeyman farmer: the farm is improved");
        assertTrue(project.get().isUpgrade());
    }

    @Test
    void anOrderFromTheDashboardIsRefusedWithTheReason() {
        Settlement s = withFarm();
        String key = s.buildings().stream().filter(b -> b.registeredBy().equals("village")).findFirst().orElseThrow().key();
        BuildingTiers.Order order = BuildingTiers.order(s, key, 20, catalog, this::blueprints, (x, z) -> 64);
        assertFalse(order.ok());
        assertTrue(order.message().contains("apprentice farmer"), order.message());
        assertFalse(BuildingTiers.of(s, catalog, this::blueprints).get(key).upgradable());
    }

    @Test
    void aHouseNeedsABuilderOfRankAndAGuardPostAGuard() {
        Settlement s = withFarm();
        Resident builder = person(Occupation.BUILDER, 0);
        builder.setBuilt(6); // builder level 2 (rank 3): journeyman
        s.addResident(builder);
        assertEquals(3, Guild.skill(s, BuildingType.HOUSE));
        assertTrue(Guild.refusal(s, BuildingType.HOUSE, 3).isEmpty());
        assertTrue(Guild.refusal(s, BuildingType.HOUSE, 4).isPresent());
        s.addResident(person(Occupation.GUARD, 0));
        assertEquals(1, Guild.skill(s, BuildingType.GUARD_POST));
        Resident veteran = person(Occupation.UNEMPLOYED, 0);
        veteran.setBuilt(Guild.VETERAN);
        s.addResident(veteran);
        assertEquals(TradeLevel.MASTER, Guild.skill(s, BuildingType.HOUSE), "a builder with twenty buildings can raise a tier 5 house");
    }

    @Test
    void theSquareAndTheWorksAreNotGatedAndNeitherIsAnythingWhenTheRuleIsOff() {
        Settlement s = withFarm();
        assertFalse(Guild.gated(BuildingType.SQUARE));
        assertEquals(Integer.MAX_VALUE, Guild.tierCap(s, BuildingType.SQUARE));
        Guild.setEnforced(false);
        assertEquals(Integer.MAX_VALUE, Guild.tierCap(s, BuildingType.FARM));
        assertTrue(Guild.refusal(s, BuildingType.FARM, 3).isEmpty());
    }

    @Test
    void aLevelIsNamedInTheReason() {
        assertEquals("a journeyman toolsmith", Guild.need(BuildingType.SMITHY, 3));
        assertEquals("an expert merchant", Guild.need(BuildingType.SHOP, 4));
        assertEquals("a master builder", Guild.need(BuildingType.HOUSE, 5));
    }
}
