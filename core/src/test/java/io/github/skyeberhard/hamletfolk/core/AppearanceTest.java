package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.16: the data and villager types a resource pack can match on. */
class AppearanceTest {
    private static Resident person(Gender gender, Occupation job, boolean adult, long bornDay) {
        return new Resident(UUID.randomUUID(), "Test", "Person", gender, new Traits(50, 50, 50, 50),
                job, adult, bornDay, null, null, Needs.initial());
    }

    @Test
    void everyGenderAndStageMapsToARealVanillaType() {
        for (Gender gender : Gender.values()) {
            for (LifeStage stage : LifeStage.values()) {
                String type = Appearance.villagerType(gender, stage);
                assertTrue(Appearance.VANILLA_TYPES.contains(type), gender + "/" + stage + " -> " + type);
                assertEquals(type, Appearance.villagerType(gender, stage), "deterministic");
            }
        }
    }

    @Test
    void childrenShareTheAdultsTypeAndEldersHaveTheirOwn() {
        for (Gender gender : Gender.values()) {
            assertEquals(Appearance.villagerType(gender, LifeStage.ADULT), Appearance.villagerType(gender, LifeStage.CHILD));
            assertNotEquals(Appearance.villagerType(gender, LifeStage.ADULT), Appearance.villagerType(gender, LifeStage.ELDER));
        }
    }

    @Test
    void noTwoGenderStageLooksShareAType() {
        Set<String> seen = new HashSet<>();
        for (Gender gender : Gender.values()) {
            for (LifeStage stage : List.of(LifeStage.ADULT, LifeStage.ELDER)) {
                assertTrue(seen.add(Appearance.villagerType(gender, stage)), "type reused: " + gender + "/" + stage);
            }
        }
        assertEquals(6, seen.size());
        assertFalse(seen.contains(Appearance.UNUSED_TYPE));
    }

    @Test
    void tagsNameGenderOccupationAndStageInLowerCase() {
        Resident elder = person(Gender.NONBINARY, Occupation.LUMBERJACK, true, 0);
        assertEquals(Map.of("gender", "nonbinary", "occupation", "lumberjack", "life_stage", "elder"),
                Appearance.tags(elder, 100));
        Resident child = person(Gender.FEMALE, Occupation.UNEMPLOYED, false, 90);
        assertEquals("child", Appearance.tags(child, 100).get(Appearance.LIFE_STAGE));
        assertEquals("female", Appearance.tags(child, 100).get(Appearance.GENDER));
        assertEquals("adult", Appearance.tags(person(Gender.MALE, Occupation.FARMER, true, 90), 100).get("life_stage"));
    }
}
