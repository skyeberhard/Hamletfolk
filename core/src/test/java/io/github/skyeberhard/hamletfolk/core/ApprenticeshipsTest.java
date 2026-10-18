package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4.58: a master trains a pupil, and a master's child starts ahead. */
class ApprenticeshipsTest {
    private static int n;

    private Resident person(Occupation job, int days, UUID parent) {
        Resident r = new Resident(new UUID(90, n++), "Test", "Pupil" + n, Gender.MALE, new Traits(50, 50, 50, 50), job, true, 10_000, parent, null, Needs.initial());
        if (days > 0) {
            r.restoreXp(job, days);
        }
        return r;
    }

    private Settlement village() {
        return new Settlement(new UUID(91, n++), "Learnford", "world", 0, 0, 0);
    }

    @Test
    void aMastersChildLearnsHalfAgainAsFast() {
        Settlement s = village();
        Resident master = person(Occupation.FARMER, 60, null);
        Resident child = person(Occupation.FARMER, 0, master.id());
        Resident other = person(Occupation.FARMER, 0, null);
        s.addResident(master);
        s.addResident(child);
        s.addResident(other);
        for (long day = 1; day <= 20; day++) {
            Apprenticeships.run(s, day);
        }
        assertEquals(10, child.xp(), "ten extra days over twenty");
        assertEquals(0, other.xp(), "the master already teaches their own child");
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("is learning the farmer's trade from")));
    }

    @Test
    void aMasterTeachesOnePupilAndNeedsToBeAMaster() {
        Settlement s = village();
        s.addResident(person(Occupation.FARMER, 59, null)); // level 3: not yet
        Resident a = person(Occupation.FARMER, 0, null);
        s.addResident(a);
        for (long day = 1; day <= 10; day++) {
            Apprenticeships.run(s, day);
        }
        assertEquals(0, a.xp());
        Settlement t = village();
        t.addResident(person(Occupation.FARMER, 60, null));
        Resident p1 = person(Occupation.FARMER, 0, null);
        Resident p2 = person(Occupation.FARMER, 5, null);
        t.addResident(p1);
        t.addResident(p2);
        for (long day = 1; day <= 10; day++) {
            Apprenticeships.run(t, day);
        }
        assertEquals(5, p1.xp(), "the least experienced is taken on");
        assertEquals(5, p2.xp(), "and the other is left alone");
    }

    @Test
    void aTradeTakenUpFromAMasterParentStartsWithAHeadStart() {
        Settlement s = village();
        Resident master = person(Occupation.TOOLSMITH, 60, null);
        Resident child = person(Occupation.UNEMPLOYED, 0, master.id());
        s.addResident(master);
        s.addResident(child);
        assertEquals(Apprenticeships.HEAD_START, Apprenticeships.headStart(s, child, Occupation.TOOLSMITH));
        assertEquals(0, Apprenticeships.headStart(s, child, Occupation.FARMER));
    }

    @Test
    void aPupilStopsBeingBoostedAtExpert() {
        Settlement s = village();
        s.addResident(person(Occupation.MINER, 100, null));
        Resident p = person(Occupation.MINER, 58, null);
        s.addResident(p);
        for (long day = 2; day <= 10; day += 2) {
            Apprenticeships.run(s, day);
        }
        assertEquals(60, p.xp(), "two boosts, then an expert needs none");
    }

    @Test
    void onlyAPupilWhoWorkedTodayIsTaughtAndAnOldHandKeepsTheirDays() {
        Settlement s = village();
        s.addResident(person(Occupation.FARMER, 60, null));
        Resident idle = person(Occupation.FARMER, 5, null);
        s.addResident(idle);
        var before = Apprenticeships.snapshot(s);
        Apprenticeships.run(s, 2, before); // did not work: xp unchanged
        assertEquals(5, idle.xp());
        idle.addXp(1); // worked
        Apprenticeships.run(s, 2, before);
        assertEquals(7, idle.xp());
        Resident back = person(Occupation.UNEMPLOYED, 0, s.residents().iterator().next().id());
        back.restoreXp(Occupation.FARMER, 40);
        assertEquals(0, Apprenticeships.headStart(s, back, Occupation.FARMER), "no head start over days already earned");
    }
}
