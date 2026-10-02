package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R1.24: unemployed adults forage about 1 food a day, on purpose. */
class ForagingTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final SettlementSimulator simulator = new SettlementSimulator();

    private Settlement village(int residents, Occupation job) {
        Settlement s = registry.found("world", 0, 0, 0);
        for (int i = 0; i < residents; i++) {
            // Far-future birth day so aging (R4.15) never affects the run.
            s.addResident(new Resident(new UUID(0, i + 1), "T", "P", Gender.MALE, new Traits(50, 50, 50, 50),
                    job, true, 10_000, null, null, Needs.initial()));
        }
        return s;
    }

    @Test
    void anUnemployedAdultProducesAboutOneFoodADay() {
        assertEquals(ResourceType.FOOD, Occupation.UNEMPLOYED.produces());
        assertEquals(1, Occupation.UNEMPLOYED.baseOutput());

        // A week of ten foragers, summed over residents and days (never a single day's output, which can be 0).
        // Wood and stone are stocked so nobody is sent to a job, and food is plentiful so nobody is hungry.
        Settlement s = village(10, Occupation.UNEMPLOYED);
        s.ledger().add(ResourceType.WOOD, 100);
        s.ledger().add(ResourceType.STONE, 100);
        s.ledger().add(ResourceType.FOOD, 100);
        simulator.simulateTo(s, 7, 100);
        int week = s.flow().produced(ResourceType.FOOD, 7);
        assertTrue(week >= 60 && week <= 70, "ten foragers made " + week + " food in a week, expected about 70");
    }

    @Test
    void foragingKeepsAVillageWithNoFarmersFedLongerThanIdlenessWould() {
        // Four foragers against four idlers who make nothing, from the same small store.
        Settlement foragers = village(4, Occupation.UNEMPLOYED);
        Settlement idlers = village(4, Occupation.NITWIT);
        for (Settlement s : new Settlement[] {foragers, idlers}) {
            s.ledger().add(ResourceType.FOOD, 40);
            s.ledger().add(ResourceType.WOOD, 100);
            s.ledger().add(ResourceType.STONE, 100);
            simulator.simulateTo(s, 5, 100);
        }
        assertTrue(foragers.ledger().get(ResourceType.FOOD) > idlers.ledger().get(ResourceType.FOOD),
                "foragers " + foragers.ledger().get(ResourceType.FOOD) + " vs idlers " + idlers.ledger().get(ResourceType.FOOD));
    }

    @Test
    void anUnemployedResidentSaysSoWhenYouTalkToThem() {
        Settlement s = village(1, Occupation.UNEMPLOYED);
        Resident jobless = s.residents().iterator().next();
        Random random = new Random(1);
        boolean said = false;
        for (int i = 0; i < 400 && !said; i++) {
            said = Dialogue.smallTalk(jobless, s, 5, random).contains("forage");
        }
        assertTrue(said, "unemployed residents should mention foraging");
    }
}
