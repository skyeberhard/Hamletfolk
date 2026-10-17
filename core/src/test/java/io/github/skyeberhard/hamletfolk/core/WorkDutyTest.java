package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.17: who walks where to be seen working, how many at once, and how long. */
class WorkDutyTest {
    private int next = 1;

    private Settlement village() {
        return new Settlement(new UUID(170, 1), "Workham", "world", 0, 0, 0);
    }

    private Resident person(Occupation job, boolean adult) {
        return new Resident(new UUID(171, next++), "Test", "Person", Gender.MALE, new Traits(50, 50, 50, 80), job, adult, 0, null, null, Needs.initial());
    }

    private static void register(Settlement s, BuildingType type, int x, int z) {
        s.registerBuilding(new Building(type, x, 64, z, 1, "test"));
    }

    @Test
    void aTradesWorkplaceIsTheKindOfBuildingItEmploysIn() {
        assertEquals(java.util.List.of(BuildingType.FARM), WorkDuty.placesOf(Occupation.FARMER));
        assertEquals(java.util.List.of(BuildingType.MINE), WorkDuty.placesOf(Occupation.MINER));
        assertEquals(java.util.List.of(BuildingType.SMITHY), WorkDuty.placesOf(Occupation.TOOLSMITH));
        assertEquals(java.util.List.of(BuildingType.SHOP), WorkDuty.placesOf(Occupation.MERCHANT));
        assertTrue(WorkDuty.placesOf(Occupation.ARMORER).contains(BuildingType.ARMOURY));
        assertTrue(WorkDuty.placesOf(Occupation.WEAPONSMITH).contains(BuildingType.ARMOURY));
        for (Occupation none : new Occupation[] {Occupation.LUMBERJACK, Occupation.BUILDER, Occupation.GUARD, Occupation.UNEMPLOYED}) {
            assertTrue(WorkDuty.placesOf(none).isEmpty(), none.name());
        }
    }

    @Test
    void aFarmerGoesToAFarmAndNobodyGoesWithoutOne() {
        Settlement s = village();
        Resident farmer = person(Occupation.FARMER, true);
        s.addResident(farmer);
        assertEquals(Optional.empty(), WorkDuty.workplace(s, farmer), "no farm registered");
        register(s, BuildingType.SMITHY, 5, 5);
        assertEquals(Optional.empty(), WorkDuty.workplace(s, farmer), "a smithy is not a farm");
        register(s, BuildingType.FARM, 20, -3);
        WorkDuty.Place place = WorkDuty.workplace(s, farmer).orElseThrow();
        assertEquals(BuildingType.FARM, place.type());
        assertEquals(20, place.x());
        assertEquals(-3, place.z());
    }

    @Test
    void aChildAGuardAndTheJoblessHaveNoWorkplaceAndNeitherDoesALumberjack() {
        Settlement s = village();
        for (BuildingType type : BuildingType.values()) {
            if (!type.isWorks() && !type.isPart()) {
                register(s, type, 10 + type.ordinal() * 15, 0);
            }
        }
        assertEquals(Optional.empty(), WorkDuty.workplace(s, person(Occupation.FARMER, false)), "a child");
        assertEquals(Optional.empty(), WorkDuty.workplace(s, person(Occupation.GUARD, true)), "a guard patrols instead");
        assertEquals(Optional.empty(), WorkDuty.workplace(s, person(Occupation.UNEMPLOYED, true)));
        assertEquals(Optional.empty(), WorkDuty.workplace(s, person(Occupation.LUMBERJACK, true)));
        assertTrue(WorkDuty.workplace(s, person(Occupation.MINER, true)).isPresent());
    }

    @Test
    void severalFarmersShareTheFarmsAndEachAlwaysGoesToTheSame() {
        Settlement s = village();
        register(s, BuildingType.FARM, 0, 0);
        register(s, BuildingType.FARM, 30, 0);
        register(s, BuildingType.FARM, 60, 0);
        Set<Integer> farmsUsed = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            Resident farmer = person(Occupation.FARMER, true);
            WorkDuty.Place first = WorkDuty.workplace(s, farmer).orElseThrow();
            assertEquals(first, WorkDuty.workplace(s, farmer).orElseThrow(), "the same farm every time");
            farmsUsed.add(first.x());
        }
        assertTrue(farmsUsed.size() >= 2, "not all of them at one farm: " + farmsUsed);
    }

    @Test
    void theChoiceDoesNotDependOnTheOrderBuildingsWereRegistered() {
        Settlement a = village();
        register(a, BuildingType.FARM, 0, 0);
        register(a, BuildingType.FARM, 30, 0);
        Settlement b = village();
        register(b, BuildingType.FARM, 30, 0);
        register(b, BuildingType.FARM, 0, 0);
        Resident farmer = person(Occupation.FARMER, true);
        assertEquals(WorkDuty.workplace(a, farmer), WorkDuty.workplace(b, farmer));
    }

    @Test
    void theWorkBehaviourIsOnlyGivenToAResidentWithAWorkplace() {
        Set<String> running = Set.of("work", "journey", "patrol");
        assertEquals(running, WorkDuty.fit(running, true));
        assertEquals(Set.of("journey", "patrol"), WorkDuty.fit(running, false));
        assertEquals(Set.of("journey"), WorkDuty.fit(Set.of("journey"), false));
    }

    @Test
    void onlyAFewWalkAtOnceAndAnyoneStrayingFarHasStoppedWorking() {
        assertTrue(WorkDuty.maySetOut(0));
        assertTrue(WorkDuty.maySetOut(WorkDuty.MAX_WALKING - 1));
        assertFalse(WorkDuty.maySetOut(WorkDuty.MAX_WALKING));
        assertTrue(WorkDuty.atWork(WorkDuty.STRAY));
        assertFalse(WorkDuty.atWork(WorkDuty.STRAY + 1));
    }

    @Test
    void theTimesAreWithinTheirLimits() {
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            int dwell = WorkDuty.dwell(random);
            int rest = WorkDuty.rest(random);
            assertTrue(dwell >= WorkDuty.DWELL_MIN && dwell <= WorkDuty.DWELL_MAX);
            assertTrue(rest >= WorkDuty.REST_MIN && rest <= WorkDuty.REST_MAX);
        }
    }

    @Test
    void theWorkingDayIsFromTwoThousandToNineThousandTicksIntoTheDay() {
        assertFalse(WorkDuty.workHours(0));
        assertFalse(WorkDuty.workHours(1999));
        assertTrue(WorkDuty.workHours(2000));
        assertTrue(WorkDuty.workHours(8999));
        assertFalse(WorkDuty.workHours(9000));
        assertFalse(WorkDuty.workHours(13000));
        assertTrue(WorkDuty.workHours(24000L * 3000 + 5000), "any day");
        assertTrue(WorkDuty.workHours(-24000L + 5000), "and a world time that has gone negative");
    }
}
