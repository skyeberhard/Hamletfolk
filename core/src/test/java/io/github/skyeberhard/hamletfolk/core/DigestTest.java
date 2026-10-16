package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The one-line digest the console prints for a village. */
class DigestTest {
    private Settlement village() {
        Settlement s = new Settlement(new UUID(120, 1), "Digestham", "world", 0, 0, 0);
        for (int i = 0; i < 3; i++) {
            s.addResident(new Resident(new UUID(121, i), "T", "P" + i, Gender.FEMALE, new Traits(50, 50, 50, 80), Occupation.FARMER,
                    true, 0, null, null, Needs.initial()));
        }
        s.ledger().add(ResourceType.FOOD, 40);
        return s;
    }

    @Test
    void aVillageIsOneLineWithItsNumbers() {
        String line = SettlementInspector.digest(village());
        assertEquals(-1, line.indexOf('\n'), "one line");
        assertTrue(line.startsWith("Digestham day 0: 3 people, food 40"), line);
        assertTrue(line.contains("3 farmer"), line);
    }

    @Test
    void troublesAreNamed() {
        Settlement s = village();
        s.conditions().put("famine", 1L);
        s.conditions().put("shortage:tools", 1L);
        String line = SettlementInspector.digest(s);
        assertTrue(line.contains("famine") && line.contains("no tools"), line);
    }

    @Test
    void anEmptyOrAbandonedVillageIsShortAndSaysSo() {
        Settlement s = new Settlement(new UUID(120, 2), "Ghostham", "world", 0, 0, 0);
        assertEquals("Ghostham day 0: empty", SettlementInspector.digest(s));
    }
}
