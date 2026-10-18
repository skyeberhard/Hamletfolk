package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R10.3: choosing where a daughter village goes. */
class ColonySitesTest {
    private final Colonies.Rules rules = Colonies.defaults();

    private Settlement parent() {
        Settlement s = new Settlement(new UUID(30, 1), "Motherton", "world", 0, 0, 0);
        return s;
    }

    /** A site profile from a biome sampled everywhere round it. */
    private SiteSurvey.Profile land(String biome) {
        List<SiteSurvey.Cell> cells = new ArrayList<>();
        for (int dx = -96; dx <= 96; dx += 24) {
            for (int dz = -96; dz <= 96; dz += 24) {
                cells.add(new SiteSurvey.Cell(dx, dz, biome));
            }
        }
        return SiteSurvey.score(cells);
    }

    /** Half one biome, half another, in a chequer. */
    private SiteSurvey.Profile mixed(String a, String b) {
        List<SiteSurvey.Cell> cells = new ArrayList<>();
        int i = 0;
        for (int dx = -96; dx <= 96; dx += 24) {
            for (int dz = -96; dz <= 96; dz += 24) {
                cells.add(new SiteSurvey.Cell(dx, dz, i++ % 2 == 0 ? a : b));
            }
        }
        return SiteSurvey.score(cells);
    }

    private ColonySites.Candidate at(int x, int z, String biome) {
        return new ColonySites.Candidate(x, z, 64, land(biome), null);
    }

    @Test
    void theCompassUsesNorthAsNegativeZ() {
        assertEquals("north", ColonySites.direction(0, -100));
        assertEquals("east", ColonySites.direction(100, 0));
        assertEquals("south", ColonySites.direction(0, 100));
        assertEquals("west", ColonySites.direction(-100, 0));
        assertEquals("north-east", ColonySites.direction(100, -100));
        assertEquals("south-west", ColonySites.direction(-100, 100));
    }

    @Test
    void aPlaceTooCloseTooFarOrRuledOutByTheGroundIsNotChosen() {
        Settlement mother = parent();
        List<ColonySites.Candidate> candidates = List.of(
                at(400, 0, "plains"),
                at(100, 0, "plains"),   // inside the spacing of the parent... and of the other village below
                at(900, 0, "plains"),   // too far
                new ColonySites.Candidate(0, 400, 64, land("plains"), "on water"),
                at(-400, 0, "plains"));
        ColonySites.Result result = ColonySites.choose(mother, candidates, List.of(new ColonySites.Point(-400, 0)), rules);
        assertTrue(result.best().isPresent());
        assertEquals(400, result.best().get().site().x(), "the only free place in range");
        assertEquals(1, result.ruledOut().get("too far from Motherton"));
        assertEquals(1, result.ruledOut().get("on water"));
        assertEquals(2, result.ruledOut().get("too close to another village"), "the one beside the other village and the one that is too close to it");
        assertEquals(5, result.considered());
    }

    @Test
    void anOceanOrADesertWithoutWaterIsRefusedAndNothingIsChosenIfNothingSuits() {
        Settlement mother = parent();
        ColonySites.Result sea = ColonySites.choose(mother, List.of(at(400, 0, "deep_ocean")), List.of(), rules);
        assertTrue(sea.best().isEmpty());
        assertFalse(sea.ruledOut().isEmpty());
        ColonySites.Result none = ColonySites.choose(mother, List.of(), List.of(), rules);
        assertTrue(none.best().isEmpty());
        assertEquals(0, none.considered());
    }

    @Test
    void aVillageWithoutStoneIsSentWhereThereIsStone() {
        Settlement mother = parent();
        mother.setLand(land("plains")); // reads the land: plains, so little stone and ore stands out
        // a hilly place against another plain one: stone should win when the parent is short of it
        ColonySites.Candidate hills = new ColonySites.Candidate(0, -400, 64, mixed("plains", "windswept_hills"), null);
        ColonySites.Candidate plain = at(0, 400, "plains");
        ColonySites.Result result = ColonySites.choose(mother, List.of(plain, hills), List.of(), rules);
        assertTrue(result.best().isPresent());
        ColonySites.Choice best = result.best().get();
        assertTrue(mother.landScore(SiteResource.STONE) < SiteSurvey.SCARCE, "a plains village is short of stone: " + mother.landScore(SiteResource.STONE));
        assertEquals(-400, best.site().z(), "the hills win");
        assertTrue(best.reasons().stream().anyMatch(r -> r.contains("stone, which Motherton is short of")), best.reasons().toString());
    }

    @Test
    void theSameCandidatesAlwaysGiveTheSameChoice() {
        Settlement mother = parent();
        List<ColonySites.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            candidates.add(at(350 + 20 * i, 10 * i, i % 2 == 0 ? "plains" : "forest"));
        }
        ColonySites.Choice a = ColonySites.choose(mother, candidates, List.of(), rules).best().orElseThrow();
        ColonySites.Choice b = ColonySites.choose(mother, new ArrayList<>(candidates.reversed()), List.of(), rules).best().orElseThrow();
        assertEquals(a.site(), b.site());
        assertEquals(Map.of(), ColonySites.choose(mother, List.of(), List.of(), rules).ruledOut());
        assertTrue(ColonySites.describe(a, mother).contains("blocks"));
    }
}
