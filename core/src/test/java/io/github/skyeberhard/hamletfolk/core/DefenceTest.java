package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R8.28: the defence and lights layer's data. */
class DefenceTest {
    private Settlement planned() {
        Settlement s = new Settlement(new UUID(950, 1), "Wallham", "world", 0, 64, 0);
        s.setLastSimulatedDay(40);
        s.setPlan(PlanGenerator.generate(0, 0, 7L, "plains", HeightSource.flat(64)));
        return s;
    }

    @Test
    void aVillageWithoutAPlanHasNothingToDraw() {
        assertTrue(Defence.asData(new Settlement(new UUID(950, 2), "Bare", "world", 0, 64, 0), 40).isEmpty());
    }

    @Test
    void theLightsAndThePlannedWallComeFromThePlan() {
        Settlement s = planned();
        Map<String, Object> data = Defence.asData(s, 40);
        assertEquals(Works.lights(s.plan(), s.plan().stage()).size(), ((List<?>) data.get("lights")).size());
        assertEquals(0, data.get("tier"), "nothing built yet");
        assertFalse(((List<?>) data.get("wall")).isEmpty(), "the planned palisade is drawn");
        assertEquals(Works.palisade(s.plan(), s.plan().stage()).size(), ((List<?>) data.get("wall")).size());
        assertFalse(((List<?>) data.get("patrol")).isEmpty());
        assertEquals(false, data.get("danger"));
        assertEquals(0L, data.get("guards"));
    }

    @Test
    void aVillageUnderThreatSaysSoAndGuardsAreCounted() {
        Settlement s = planned();
        s.raiseThreat(60);
        s.addResident(new Resident(new UUID(950, 3), "G", "Guard", Gender.MALE, new Traits(50, 50, 50, 50), Occupation.GUARD, true, 5, null, null, Needs.initial()));
        Map<String, Object> data = Defence.asData(s, 40);
        assertEquals(true, data.get("danger"));
        assertEquals(60L, data.get("threat"));
        assertEquals(1L, data.get("guards"));
    }
}
