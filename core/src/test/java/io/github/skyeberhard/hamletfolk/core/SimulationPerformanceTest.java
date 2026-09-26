package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * R1.6: 50 settlements of 50 residents simulate one day in under 5 ms.
 *
 * <p>Uses the median of many days after a warm-up, so a single slow day on a busy CI
 * runner doesn't fail the build. The budget is roughly 10% of one server tick.
 */
@Tag("performance")
class SimulationPerformanceTest {
    private static final int SETTLEMENTS = 50;
    private static final int RESIDENTS = 50;
    private static final long BUDGET_NANOS = 5_000_000;
    private static final Occupation[] JOBS = {
            Occupation.FARMER, Occupation.FARMER, Occupation.FISHERMAN, Occupation.MASON,
            Occupation.TOOLSMITH, Occupation.LIBRARIAN, Occupation.UNEMPLOYED};

    @Test
    void oneDayForFiftyVillagesOfFiftyFitsTheBudget() {
        SettlementRegistry registry = new SettlementRegistry();
        List<Settlement> settlements = new ArrayList<>();
        for (int s = 0; s < SETTLEMENTS; s++) {
            Settlement settlement = registry.found("world", s * 500, 0, 0);
            for (int r = 0; r < RESIDENTS; r++) {
                registry.enroll(settlement, UUID.randomUUID(), JOBS[r % JOBS.length], r % 10 != 0, 0, null, null);
            }
            settlements.add(settlement);
        }
        SettlementSimulator simulator = new SettlementSimulator();

        long day = 0;
        for (int warmup = 0; warmup < 200; warmup++) {
            day++;
            for (Settlement settlement : settlements) {
                simulator.simulateTo(settlement, day, 1);
            }
        }

        long[] samples = new long[101];
        for (int i = 0; i < samples.length; i++) {
            day++;
            long start = System.nanoTime();
            for (Settlement settlement : settlements) {
                simulator.simulateTo(settlement, day, 1);
            }
            samples[i] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        long median = samples[samples.length / 2];

        System.out.printf("R1.6: %d settlements x %d residents, one day: median %.3f ms, worst %.3f ms%n",
                SETTLEMENTS, RESIDENTS, median / 1e6, samples[samples.length - 1] / 1e6);
        assertTrue(median < BUDGET_NANOS, "median day took " + median / 1e6 + " ms");
    }
}
