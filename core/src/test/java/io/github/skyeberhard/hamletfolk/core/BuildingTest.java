package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** R2.1: a bracketed sign registers a building, breaking it removes it, and the list is saved. */
class BuildingTest {
    private final SettlementRegistry registry = new SettlementRegistry();

    private static Building farm(int x, int y, int z) {
        return new Building(BuildingType.FARM, x, y, z, 5, "Skye");
    }

    @Test
    void signTextIsRecognisedInItsUsualSpellingsAndOnlyWhenBracketed() {
        assertEquals(Optional.of(BuildingType.FARM), BuildingType.fromSign("[Farm]"));
        assertEquals(Optional.of(BuildingType.FARM), BuildingType.fromSign("[farm]"));
        assertEquals(Optional.of(BuildingType.FARM), BuildingType.fromSign("  [ FARM ]  "));
        assertEquals(Optional.of(BuildingType.SMITHY), BuildingType.fromSign("[Smithy]"));
        assertEquals(Optional.of(BuildingType.MINE), BuildingType.fromSign("[Mine]"));
        assertEquals(Optional.of(BuildingType.HOUSE), BuildingType.fromSign("[House]"));
        assertEquals(Optional.of(BuildingType.GUARD_POST), BuildingType.fromSign("[Guard Post]"));
        assertEquals(Optional.of(BuildingType.GUARD_POST), BuildingType.fromSign("[guardpost]"));
        assertEquals(Optional.of(BuildingType.GUARD_POST), BuildingType.fromSign("[Guard_Post]"));
        assertEquals(Optional.of(BuildingType.GUARD_POST), BuildingType.fromSign("[guard-post]"));

        assertEquals(Optional.empty(), BuildingType.fromSign("Farm"));       // a sign that just says so is left alone
        assertEquals(Optional.empty(), BuildingType.fromSign("[Barn]"));
        assertEquals(Optional.empty(), BuildingType.fromSign("[]"));
        assertEquals(Optional.empty(), BuildingType.fromSign("["));
        assertEquals(Optional.empty(), BuildingType.fromSign(""));
        assertEquals(Optional.empty(), BuildingType.fromSign(null));
        assertEquals(Optional.empty(), BuildingType.fromSign("[Farm] and more"));
        for (BuildingType type : BuildingType.values()) {
            if (type.isWorks()) {
                // R5.6: a work on the plan is built by the village and cannot be registered by a sign
                assertEquals(Optional.empty(), BuildingType.fromSign(type.signText()), type + " has no sign");
                assertEquals(Optional.of(type), BuildingType.fromTarget(type.name().toLowerCase()), type + " is a planner target");
            } else {
                assertEquals(Optional.of(type), BuildingType.fromSign(type.signText()), "every type reads its own sign text");
            }
        }
    }

    @Test
    void registeringABuildingListsItAndNotesItInTheHistory() {
        Settlement s = registry.found("world", 0, 0, 0);
        assertEquals(Settlement.Registration.REGISTERED, s.registerBuilding(farm(10, 64, 20)));
        assertEquals(1, s.buildings().size());
        assertEquals(1, s.buildingCount(BuildingType.FARM));
        assertEquals(0, s.buildingCount(BuildingType.MINE));
        assertTrue(s.hasBuildingAt(10, 64, 20));
        assertFalse(s.hasBuildingAt(10, 65, 20));
        assertTrue(s.history().stream().anyMatch(e -> e.text().equals("Skye registered a farm in " + s.name() + ".")));
    }

    @Test
    void aPlayerTogglingSignsCannotFloodTheHistory() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.registerBuilding(farm(1, 2, 3));
        int before = s.history().size();
        for (int i = 0; i < 300; i++) {
            s.registerBuilding(new Building(i % 2 == 0 ? BuildingType.MINE : BuildingType.FARM, 1, 2, 3, 5, "Skye"));
            s.removeBuilding(1, 2, 3, 5);
            s.registerBuilding(farm(1, 2, 3));
        }
        assertEquals(before, s.history().size(), "all of Skye's changes this week are one line");
        HistoryEvent line = s.history().get(s.history().size() - 1);
        assertEquals(HistoryEvent.Kind.BUILDING, line.kind());
        assertTrue(line.text().startsWith("Skye changed ") && line.text().endsWith(" buildings this week."), line.text());
        assertTrue(s.history().stream().anyMatch(e -> e.kind() == HistoryEvent.Kind.FOUNDED), "the founding is still there");

        // Another player's change, or one much later, is a line of its own.
        s.registerBuilding(new Building(BuildingType.HOUSE, 9, 9, 9, 5, "Alex"));
        assertEquals(before + 1, s.history().size());
        s.registerBuilding(new Building(BuildingType.HOUSE, 8, 8, 8, 30, "Skye"));
        assertEquals(before + 2, s.history().size());
    }

    @Test
    void theSameSignIsNotRegisteredTwiceButChangingItsKindReplacesTheBuilding() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.registerBuilding(farm(1, 2, 3));
        int events = s.history().size();
        assertEquals(Settlement.Registration.ALREADY_REGISTERED, s.registerBuilding(farm(1, 2, 3)));
        assertEquals(1, s.buildings().size());
        assertEquals(events, s.history().size(), "a repeat is not written down again");

        assertEquals(Settlement.Registration.REGISTERED,
                s.registerBuilding(new Building(BuildingType.MINE, 1, 2, 3, 6, "Skye")));
        assertEquals(1, s.buildings().size());
        assertEquals(0, s.buildingCount(BuildingType.FARM));
        assertEquals(1, s.buildingCount(BuildingType.MINE));
    }

    @Test
    void breakingTheSignRemovesTheBuilding() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.registerBuilding(farm(1, 2, 3));
        s.registerBuilding(new Building(BuildingType.HOUSE, 4, 5, 6, 5, "Skye"));

        Optional<Building> gone = s.removeBuilding(1, 2, 3, 9);
        assertEquals(BuildingType.FARM, gone.orElseThrow().type());
        assertEquals(1, s.buildings().size());
        assertFalse(s.hasBuildingAt(1, 2, 3));
        // (Skye's changes this week merge into one line, so look at the whole record.)
        assertTrue(s.history().stream().anyMatch(e -> e.kind() == HistoryEvent.Kind.BUILDING && e.actor().equals("Skye")));
        assertEquals(Optional.empty(), s.removeBuilding(1, 2, 3, 9), "removing it again does nothing");
        assertEquals(Optional.empty(), s.removeBuilding(99, 99, 99, 9));
    }

    @Test
    void aSettlementHasALimitSoSignSpamCannotBloatTheSave() {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < Settlement.MAX_BUILDINGS; i++) {
            assertEquals(Settlement.Registration.REGISTERED, s.registerBuilding(farm(i, 0, 0)));
        }
        assertEquals(Settlement.Registration.TOO_MANY, s.registerBuilding(farm(9999, 0, 0)));
        assertEquals(Settlement.MAX_BUILDINGS, s.buildings().size());
        // Changing the kind of an existing one is still allowed at the limit.
        assertEquals(Settlement.Registration.REGISTERED, s.registerBuilding(new Building(BuildingType.MINE, 0, 0, 0, 7, "Skye")));
    }

    @Test
    void theRegistryFindsWhichSettlementOwnsASignInTheRightWorld() {
        Settlement a = registry.found("world", 0, 0, 0);
        Settlement b = registry.found("other", 0, 0, 0);
        a.registerBuilding(farm(1, 2, 3));
        assertEquals(Optional.of(a), registry.settlementWithBuildingAt("world", 1, 2, 3));
        assertEquals(Optional.empty(), registry.settlementWithBuildingAt("other", 1, 2, 3));
        assertEquals(Optional.empty(), registry.settlementWithBuildingAt("world", 1, 2, 4));
        assertEquals(Optional.empty(), b.buildings().stream().findFirst());
    }

    @Test
    void buildingsAreSavedAndAnOlderSaveWithoutAnyLoads() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.registerBuilding(farm(10, 64, 20));
        s.registerBuilding(new Building(BuildingType.GUARD_POST, -5, 70, 8, 6, "Alex"));

        Map<String, Object> encoded = SettlementCodec.encode(s);
        Settlement loaded = SettlementCodec.decode(encoded);
        assertEquals(2, loaded.buildings().size());
        assertTrue(loaded.hasBuildingAt(-5, 70, 8));
        assertEquals(BuildingType.GUARD_POST, loaded.buildings().stream().skip(1).findFirst().orElseThrow().type());
        assertEquals("Alex", loaded.buildings().stream().skip(1).findFirst().orElseThrow().registeredBy());
        assertEquals(encoded, SettlementCodec.encode(loaded));
        assertEquals(SettlementCodec.FORMAT_VERSION, encoded.get("format"));

        // A format-8 save has no "buildings" key at all.
        Map<String, Object> old = new LinkedHashMap<>(encoded);
        old.remove("buildings");
        old.put("format", 8);
        assertTrue(SettlementCodec.decode(old).buildings().isEmpty());
    }

    @Test
    void theAdminReportListsTheBuildings() {
        Settlement s = registry.found("world", 0, 0, 0);
        assertTrue(String.join("\n", SettlementInspector.report(s)).contains("Buildings: none"));
        s.registerBuilding(farm(1, 2, 3));
        s.registerBuilding(farm(4, 5, 6));
        s.registerBuilding(new Building(BuildingType.MINE, 7, 8, 9, 5, "Skye"));
        assertTrue(String.join("\n", SettlementInspector.report(s)).contains("Buildings: 2 farm, 1 mine"));
    }
}
