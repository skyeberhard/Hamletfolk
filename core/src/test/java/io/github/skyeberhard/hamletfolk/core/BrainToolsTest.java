package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R9.2: the brain module's tools: the timing counters, the decision log and the text of the inspect output and the report. */
class BrainToolsTest {
    // ----- timing -----

    @Test
    void nothingRecordedSaysSo() {
        BrainStats stats = new BrainStats();
        assertEquals(0, stats.summary().calls());
        assertEquals(0, stats.summary().ticks());
        assertTrue(stats.summary().describe().contains("not run yet"));
    }

    @Test
    void callsAreAddedUpPerTickAndPerCall() {
        BrainStats stats = new BrainStats();
        // tick 100: three calls of 2000, 4000 and 6000 ns; tick 101: one of 10000; tick 105: one of 1000
        stats.record(100, 2_000);
        stats.record(100, 4_000);
        stats.record(100, 6_000);
        stats.record(101, 10_000);
        stats.record(105, 1_000);
        BrainStats.Summary s = stats.summary();
        assertEquals(5, s.calls());
        assertEquals(3, s.ticks(), "three different ticks had a call");
        assertEquals(23_000 / 1000.0 / 5, s.averageCallMicros(), 1e-9);
        assertEquals(10.0, s.worstCallMicros(), 1e-9);
        assertEquals(23.0 / 3, s.averageTickMicros(), 1e-9, "per tick: all the calls of the tick added up");
        assertEquals(12.0, s.worstTickMicros(), 1e-9, "tick 100 took 12 microseconds in all, tick 101 ten");
        assertEquals(0.023, s.totalMillis(), 1e-9);
        assertTrue(s.describe().startsWith("5 calls in 3 ticks: 4.6 microseconds a call (worst 10.0), 7.7 a tick (worst 12.0)"), s.describe());
    }

    @Test
    void theTickStillOpenCountsAndAWorstTickCanBeTheLastOne() {
        BrainStats stats = new BrainStats();
        stats.record(1, 1_000);
        stats.record(2, 5_000);
        stats.record(2, 5_000);
        assertEquals(10.0, stats.summary().worstTickMicros(), 1e-9);
        assertEquals(2, stats.summary().ticks());
        assertEquals(10.0, stats.summary().worstTickMicros(), 1e-9, "asking twice changes nothing");
    }

    @Test
    void aNegativeTimeIsTakenAsNothingAndResetStartsAgain() {
        BrainStats stats = new BrainStats();
        stats.record(1, -50);
        assertEquals(1, stats.summary().calls());
        assertEquals(0, stats.summary().totalMillis(), 0);
        stats.record(2, 1_000);
        stats.reset();
        assertEquals(0, stats.summary().calls());
        assertEquals(0, stats.summary().ticks());
        stats.record(3, 2_000);
        assertEquals(1, stats.summary().ticks());
        assertEquals(2.0, stats.summary().worstTickMicros(), 1e-9);
    }

    // ----- decisions -----

    @Test
    void theLogKeepsTheLastDecisionsInOrderAndCountsAllOfThem() {
        DecisionLog log = new DecisionLog(3);
        for (int i = 1; i <= 5; i++) {
            log.add(i * 20L, "Mira Oakes", "decision " + i);
        }
        assertEquals(3, log.size());
        assertEquals(5, log.total());
        assertEquals(List.of("[tick 60] Mira Oakes: decision 3", "[tick 80] Mira Oakes: decision 4", "[tick 100] Mira Oakes: decision 5"),
                log.last(10));
        assertEquals(List.of("[tick 100] Mira Oakes: decision 5"), log.last(1));
        assertEquals(List.of(), log.last(0));
        log.clear();
        assertEquals(0, log.size());
        assertEquals(List.of(), log.last(5));
        assertEquals(1, new DecisionLog(0).size() + 1, "a capacity under one is made one");
    }

    // ----- text -----

    private static BrainReport.VillagerView view(int memories) {
        List<String> remembered = new ArrayList<>();
        for (int i = 0; i < memories; i++) {
            remembered.add("minecraft:memory_" + i + " = value " + i);
        }
        return new BrainReport.VillagerView("Mira Oakes (farmer of Oakvale)", "activity: work (also core, meet)", remembered,
                List.of("hamletfolk:attention"), List.of("hamletfolk:attention"));
    }

    @Test
    void inspectShowsActivityAddedBehavioursWhatIsRunningAndMemories() {
        List<String> lines = BrainReport.inspect(view(2));
        assertEquals("Mira Oakes (farmer of Oakvale): activity: work (also core, meet)", lines.get(0));
        assertEquals("Added by Hamletfolk: hamletfolk:attention", lines.get(1));
        assertEquals("Running now: hamletfolk:attention", lines.get(2));
        assertEquals("Memories (2):", lines.get(3));
        assertEquals("  minecraft:memory_0 = value 0", lines.get(4));
        assertEquals(6, lines.size());

        BrainReport.VillagerView bare = new BrainReport.VillagerView("Tom Ash", "activity: core only", List.of(), List.of(), List.of());
        List<String> none = BrainReport.inspect(bare);
        assertEquals("Added by Hamletfolk: nothing", none.get(1));
        assertEquals("Running now: nothing", none.get(2));
        assertEquals("Memories (0):", none.get(3));
    }

    @Test
    void aLongBrainIsCutOffAndSaysSo() {
        List<String> lines = BrainReport.inspect(view(BrainReport.MAX_LINES + 15));
        assertEquals(4 + BrainReport.MAX_LINES + 1, lines.size());
        assertEquals("  ... and 15 more", lines.get(lines.size() - 1));
        assertEquals("Memories (" + (BrainReport.MAX_LINES + 15) + "):", lines.get(3));
    }

    @Test
    void shortenCutsAValueToOneLine() {
        assertEquals("abc", BrainReport.shorten("abc", 10));
        assertEquals("abcdefg...", BrainReport.shorten("abcdefghijklmnop", 10));
        assertEquals("a b", BrainReport.shorten("a\nb", 10));
        assertEquals("null", BrainReport.shorten(null, 10));
        assertEquals("WalkTarget", BrainReport.shorten("net.minecraft.world.entity.ai.memory.WalkTarget@debb33a", 90),
                "a default object string is just its class");
        assertEquals("Inner$Part", BrainReport.shorten("a.b.Inner$Part@1f", 90));
        assertEquals("Villager['Mira'/4, uuid='x']", BrainReport.shorten("Villager['Mira'/4, uuid='x']", 90), "a real description is kept");
        assertEquals("name@home", BrainReport.shorten("name@home", 90), "and so is anything else with an at sign");
    }

    private static BrainReport.Input input(BrainSwitch.State state, String reason, List<String> missing) {
        BrainStats stats = new BrainStats();
        stats.record(10, 3_000);
        return new BrainReport.Input("0.1.0-SNAPSHOT", "Paper 26.2 build 130", "2026-10-09 17:30", state, reason, false, true, 8,
                stats.summary(), missing, List.of(view(1)), List.of("[tick 10] Mira Oakes: noticed Skye"), 41);
    }

    @Test
    void theReportHasEverythingASupportRequestNeeds() {
        String text = BrainReport.report(input(BrainSwitch.State.ON, "", List.of()));
        for (String wanted : new String[] {"Hamletfolk brain module report", "Written: 2026-10-09 17:30", "Plugin: 0.1.0-SNAPSHOT",
                "Server: Paper 26.2 build 130", "State: on", "brain.enabled in the config: false", "debug logging: on",
                "Villagers with the added behaviours: 8", "Cost: 1 calls in 1 ticks", "Self-check: everything the module needs is in this server.",
                "Villagers (1 shown):", "  Mira Oakes (farmer of Oakvale)", "Last decisions (1 of 41", "  [tick 10] Mira Oakes: noticed Skye"}) {
            assertTrue(text.contains(wanted), "has '" + wanted + "' in:\n" + text);
        }
        assertFalse(text.contains("MISSING"));
        assertTrue(text.endsWith(System.lineSeparator()));
    }

    @Test
    void theReportKeepsNamesButTakesOutIdsAndPositions() {
        String id = "2e1848b1-8928-497c-aedc-b1659703e644";
        BrainReport.VillagerView v = new BrainReport.VillagerView("Mira Oakes (farmer of Oakvale, " + id + ")", "activity: idle",
                List.of("minecraft:look_target = EntityTracker for Player['Skye'/7, uuid='" + id + "', l='ServerLevel[world]', x=-12.50, y=64.00, z=301.25]",
                        "minecraft:home = GlobalPos[dimension=minecraft:overworld, pos=BlockPos{x=10, y=64, z=-8}]",
                        "minecraft:walk_target = walk to x=10.5 y=64.0 z=-7.5 (speed 0.50, close enough at 1)"),
                List.of(), List.of());
        BrainStats stats = new BrainStats();
        String text = BrainReport.report(new BrainReport.Input("v", "s", "now", BrainSwitch.State.ON, "", false, false, 1, stats.summary(),
                List.of(), List.of(v), List.of("[tick 5] Mira Oakes: noticed Skye 2.0 blocks away: will look at them"), 1));
        assertFalse(text.contains(id), text);
        assertTrue(text.contains("Mira Oakes (farmer of Oakvale, <id>)"), "the name stays: " + text);
        assertTrue(text.contains("Player['Skye'/7, uuid='<id>'"), text);
        assertTrue(text.contains("x=?, y=?, z=?") && text.contains("BlockPos{x=?, y=?, z=?}") && text.contains("walk to x=? y=? z=? (speed 0.50"), text);
        assertFalse(text.contains("301.25") || text.contains("-12.50"), text);
        assertTrue(text.contains("noticed Skye 2.0 blocks away"), "a distance is not a position: " + text);
        assertEquals("a x=? b", BrainReport.redact("a x=5 b"));
        assertEquals("xx=1 and max=3", BrainReport.redact("xx=1 and max=3"), "only whole x, y and z");
    }

    @Test
    void aFailedSelfCheckListsWhatIsMissingAndTheReason() {
        String text = BrainReport.report(input(BrainSwitch.State.UNAVAILABLE, "the running server lacks Brain#x",
                List.of("net.minecraft.world.entity.ai.Brain#x : Map")));
        assertTrue(text.contains("State: unavailable (the running server lacks Brain#x)"), text);
        assertTrue(text.contains("Self-check: MISSING from this server:"), text);
        assertTrue(text.contains("  net.minecraft.world.entity.ai.Brain#x : Map"), text);
    }
}
