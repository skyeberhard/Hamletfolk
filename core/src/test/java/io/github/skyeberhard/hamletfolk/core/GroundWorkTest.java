package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R8.20: levelling ground yields stone and wood. */
class GroundWorkTest {
    @Test
    void rockIsRockAndSoilIsNot() {
        assertTrue(GroundWork.isRock("STONE"));
        assertTrue(GroundWork.isRock("minecraft:deepslate"));
        assertTrue(GroundWork.isRock("SANDSTONE"));
        assertFalse(GroundWork.isRock("DIRT"));
        assertFalse(GroundWork.isRock("GRASS_BLOCK"));
        assertFalse(GroundWork.isRock("SAND"));
        assertFalse(GroundWork.isRock("OAK_LOG"));
    }

    @Test
    void rockCutAndLogsFelledGoIntoTheStoresAndTheFlow() {
        Settlement s = new Settlement(new UUID(130, 1), "Cutham", "world", 0, 0, 0);
        GroundWork.Yield yield = GroundWork.credit(s, 7, 30, 3);
        assertEquals(30, yield.stone());
        assertEquals(3 * WorldMarks.UNITS_PER_LOG, yield.wood());
        assertEquals(30, s.ledger().get(ResourceType.STONE));
        assertEquals(3 * WorldMarks.UNITS_PER_LOG, s.ledger().get(ResourceType.WOOD));
        assertEquals(30, s.flow().produced(ResourceType.STONE, 7));
        assertFalse(GroundWork.credit(s, 8, 0, 0).any());
        assertFalse(GroundWork.credit(s, 8, -4, -1).any(), "never a negative yield");
    }
}
