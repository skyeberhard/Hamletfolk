package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.62: the quality of a village's stores follows its best maker. */
class QualityTest {
    private static int n;

    private Resident person(Occupation job, int days) {
        Resident r = new Resident(new UUID(70, n++), "Test", "Maker" + n, Gender.MALE, new Traits(50, 50, 50, 50), job, true, 10_000, null, null, Needs.initial());
        r.restoreXp(job, days);
        return r;
    }

    @Test
    void theLabelFollowsTheLevel() {
        assertEquals("", Quality.label(1));
        assertEquals("", Quality.label(2));
        assertEquals("Good", Quality.label(3));
        assertEquals("Fine", Quality.label(4));
        assertEquals("Masterwork", Quality.label(5));
    }

    @Test
    void aCategoryTakesTheGradeOfItsBestMaker() {
        Settlement s = new Settlement(new UUID(71, 1), "Qualitown", "world", 0, 0, 0);
        Resident fine = person(Occupation.TOOLSMITH, 60);
        s.addResident(fine);
        s.addResident(person(Occupation.ARMORER, 10));
        s.addResident(person(Occupation.FARMER, 100));
        Quality.Grade tools = Quality.of(s, ResourceType.TOOLS);
        assertEquals("Fine", tools.label());
        assertEquals(fine.fullName(), tools.maker());
        assertTrue(Quality.of(s, ResourceType.FOOD).plain(), "food is not graded");
        s.addResident(person(Occupation.SHEPHERD, 100));
        assertEquals("Masterwork", Quality.of(s, ResourceType.GOODS).label());
        Quality.Grade wood = Quality.of(s, ResourceType.WOOD);
        assertTrue(wood.plain());
        assertNull(wood.maker());
    }
}
