package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R8.29: tiers, beds and upgrade orders from the plan screen. */
class BuildingTiersTest {
    private final TemplateCatalog catalog = new TemplateCatalog();
    private int next = 1;

    private Resident person() {
        return new Resident(new UUID(60, next++), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, 80), Occupation.UNEMPLOYED, true, 10_000, null, null, Needs.initial());
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

    /** A village with a farm its builders raised, standing, and the stores to improve it (set up as ConstructionTest does for an upgrade). */
    private Settlement withFarm() {
        Settlement s = new Settlement(new UUID(61, 1), "Tierford", "world", 0, 0, 0);
        for (int i = 0; i < 6; i++) {
            s.addResident(person());
        }
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        s.ledger().add(ResourceType.WOOD, 200);
        s.ledger().add(ResourceType.STONE, 200);
        s.ledger().take(ResourceType.WOOD, 170);
        s.ledger().take(ResourceType.STONE, 200);
        ConstructionProject farm = Construction.propose(s, 5, "plains", 200, catalog, this::blueprints, (x, z) -> 64).orElseThrow();
        farm.setSign(farm.x(), 65, farm.z() - 1);
        s.registerBuilding(new Building(BuildingType.FARM, farm.x(), 65, farm.z() - 1, 5, "village"));
        Construction.finish(s, farm, 6);
        for (BuildingType type : BuildingType.values()) {
            if (s.buildingCount(type) == 0) {
                s.registerBuilding(new Building(type, 1000 + type.ordinal(), 64, 1000, 0, "a player"));
            }
        }
        s.ledger().add(ResourceType.FOOD, 1000);
        s.ledger().add(ResourceType.STONE, 100);
        s.housing().setChunk(0, 0, 20);
        s.setLastSimulatedDay(20);
        return s;
    }

    private String farmKey(Settlement s) {
        return s.buildings().stream().filter(b -> b.registeredBy().equals("village")).findFirst().orElseThrow().key();
    }

    @Test
    void aBuildingTheVillageRaisedShowsItsTierAndCanBeUpgraded() {
        Settlement s = withFarm();
        Map<String, BuildingTiers.Info> info = BuildingTiers.of(s, catalog, this::blueprints);
        BuildingTiers.Info farm = info.get(farmKey(s));
        assertEquals(1, farm.tier());
        assertTrue(farm.maxTier() > 1, "the catalog has a better farm");
        assertTrue(farm.upgradable(), farm.why());
    }

    @Test
    void aPlayersBuildingIsNeverUpgradedAndABestTierOneIsRefused() {
        Settlement s = withFarm();
        BuildingTiers.Info shop = BuildingTiers.of(s, catalog, this::blueprints).get((1000 + BuildingType.SHOP.ordinal()) + ",64,1000");
        assertFalse(shop.upgradable());
        assertTrue(shop.why().contains("player"), shop.why());
        BuildingTiers.Order refused = BuildingTiers.order(s, (1000 + BuildingType.SHOP.ordinal()) + ",64,1000", 20, catalog, this::blueprints, (x, z) -> 64);
        assertFalse(refused.ok());
        assertTrue(s.projects().stream().noneMatch(p -> p.isUpgrade()));
        assertFalse(BuildingTiers.order(s, "9,9,9", 20, catalog, this::blueprints, (x, z) -> 64).ok(), "no such building");
    }

    @Test
    void anOrderQueuesTheUpgradeOnceAndTheBuildersAreThenBusy() {
        Settlement s = withFarm();
        String key = farmKey(s);
        BuildingTiers.Order order = BuildingTiers.order(s, key, 20, catalog, this::blueprints, (x, z) -> 64);
        assertTrue(order.ok(), order.message());
        ConstructionProject queued = s.openProject().orElseThrow();
        assertTrue(queued.isUpgrade());
        assertEquals(2, queued.tier());
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("An admin asked for the farm to be improved")));
        BuildingTiers.Order again = BuildingTiers.order(s, key, 20, catalog, this::blueprints, (x, z) -> 64);
        assertFalse(again.ok());
        assertTrue(again.message().contains("busy"), again.message());
    }

    @Test
    void withNothingInTheStoresTheOrderSaysSo() {
        Settlement s = withFarm();
        s.ledger().take(ResourceType.STONE, 1000);
        s.ledger().take(ResourceType.WOOD, 1000);
        String key = farmKey(s);
        BuildingTiers.Order order = BuildingTiers.order(s, key, 20, catalog, this::blueprints, (x, z) -> 64);
        assertFalse(order.ok());
        assertTrue(order.message().contains("cannot pay"), order.message());
        assertTrue(s.openProject().isEmpty());
    }
}
