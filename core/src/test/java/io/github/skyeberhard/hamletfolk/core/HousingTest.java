package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R2.2: beds set a housing capacity, counted per chunk. */
class HousingTest {
    private final SettlementRegistry registry = new SettlementRegistry();

    private static Resident person(int i) {
        return new Resident(new UUID(0, i + 1), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                Occupation.NITWIT, true, 10_000, null, null, Needs.initial());
    }

    @Test
    void capacityIsTheSumOfTheBedsInEachChunk() {
        Housing housing = new Housing();
        assertEquals(0, housing.capacity());
        housing.setChunk(0, 0, 3);
        housing.setChunk(-1, 4, 5);
        assertEquals(8, housing.capacity());
        assertEquals(3, housing.bedsIn(0, 0));
        assertEquals(0, housing.bedsIn(9, 9));
        assertEquals(2, housing.chunkCount());
    }

    @Test
    void recountingAChunkReplacesItsOldCountAndZeroForgetsIt() {
        Housing housing = new Housing();
        housing.setChunk(2, 2, 6);
        housing.setChunk(2, 2, 4); // two beds were removed
        assertEquals(4, housing.capacity());
        housing.setChunk(2, 2, 0); // all gone
        assertEquals(0, housing.capacity());
        assertEquals(0, housing.chunkCount());
        housing.setChunk(2, 2, -3);
        assertEquals(0, housing.capacity(), "a negative count is not a bed");
    }

    @Test
    void anUnrecountedChunkKeepsItsHousing() {
        // Walking away unloads a chunk, and the scan skips it, so its beds stay on record.
        Housing housing = new Housing();
        housing.setChunk(0, 0, 4);
        housing.setChunk(1, 0, 4);
        housing.setChunk(0, 0, 5); // only chunk (0,0) was loaded this time
        assertEquals(9, housing.capacity());
    }

    @Test
    void lowerRadiusForgetsChunksOutsideIt() {
        Housing housing = new Housing();
        housing.setChunk(0, 0, 2);
        housing.setChunk(5, 0, 3);   // inside a radius of 6 chunks, outside one of 2
        housing.setChunk(-5, -5, 4);
        housing.retainWithin(-2, 2, -2, 2);
        assertEquals(2, housing.capacity());
        assertEquals(1, housing.chunkCount());
    }

    @Test
    void oddKeysAndValuesInASaveAreIgnoredOrNormalised() {
        Settlement s = registry.found("world", 0, 0, 0);
        Map<String, Object> encoded = SettlementCodec.encode(s);
        Map<String, Object> beds = new LinkedHashMap<>();
        beds.put("01,2", 4);          // normalised to "1,2"
        beds.put("1,2", 3);           // the same chunk again: the later one wins, not both
        beds.put("5,5", "three");     // not a number: ignored, and the save still loads
        encoded.put("beds", beds);
        Settlement loaded = SettlementCodec.decode(encoded);
        assertEquals(3, loaded.housingCapacity());
        assertEquals(3, loaded.housing().bedsIn(1, 2));
    }

    @Test
    void freeBedsAreCapacityLessThePopulationAndNeverNegative() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.housing().setChunk(0, 0, 5);
        assertEquals(5, s.housingCapacity());
        assertEquals(5, s.freeBeds());
        for (int i = 0; i < 3; i++) {
            s.addResident(person(i));
        }
        assertEquals(2, s.freeBeds());
        for (int i = 3; i < 9; i++) {
            s.addResident(person(i));
        }
        assertEquals(5, s.housingCapacity());
        assertEquals(0, s.freeBeds(), "crowded, not negative");
    }

    @Test
    void newcomersNeedAFreeBedFromTheSavedHousing() {
        SettlementSimulator simulator = new SettlementSimulator();
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            s.addResident(person(i));
        }
        s.ledger().add(ResourceType.FOOD, 200);
        simulator.simulateTo(s, 2, 100); // not the founding day, and plenty of food
        assertFalse(simulator.newcomerDue(s, s.freeBeds()), "no beds recorded, so no room");
        s.housing().setChunk(0, 0, 4);
        assertFalse(simulator.newcomerDue(s, s.freeBeds()), "four beds for four residents");
        s.housing().setChunk(0, 0, 5);
        assertTrue(simulator.newcomerDue(s, s.freeBeds()), "a fifth bed makes room");
    }

    @Test
    void housingIsSavedAndAnOlderSaveWithoutAnyLoads() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.housing().setChunk(0, 0, 4);
        s.housing().setChunk(-3, 7, 2);

        Map<String, Object> encoded = SettlementCodec.encode(s);
        Settlement loaded = SettlementCodec.decode(encoded);
        assertEquals(6, loaded.housingCapacity());
        assertEquals(2, loaded.housing().bedsIn(-3, 7));
        assertEquals(encoded, SettlementCodec.encode(loaded));
        assertEquals(SettlementCodec.FORMAT_VERSION, encoded.get("format"));

        // A format-9 save has no "beds" key at all.
        Map<String, Object> old = new LinkedHashMap<>(encoded);
        old.remove("beds");
        old.put("format", 9);
        assertEquals(0, SettlementCodec.decode(old).housingCapacity());
    }

    @Test
    void aDamagedSaveCannotInventHousing() {
        Settlement s = registry.found("world", 0, 0, 0);
        Map<String, Object> encoded = SettlementCodec.encode(s);
        Map<String, Object> beds = new LinkedHashMap<>();
        beds.put("0,0", 3);
        beds.put("not a chunk", 50);
        beds.put("1,1", -9);
        beds.put("2,2", 1_000_000);
        encoded.put("beds", beds);
        assertEquals(3, SettlementCodec.decode(encoded).housingCapacity());
    }

    @Test
    void theAdminReportShowsHousing() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.housing().setChunk(0, 0, 3);
        s.addResident(person(0));
        assertTrue(String.join("\n", SettlementInspector.report(s)).contains("Housing: 3 beds, population 1 (2 free; beds counted in 1 chunks)"));
    }
}
