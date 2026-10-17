package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R1.36: only the listed actions run, with checked parameters, and every one is written down. */
class AdminActionTest {
    private final SettlementRegistry registry = new SettlementRegistry();

    private Settlement village() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.addResident(new Resident(new UUID(160, 1), "Dara", "Mercer", Gender.FEMALE, new Traits(50, 50, 50, 80), Occupation.FARMER,
                true, 0, null, null, Needs.initial()));
        return s;
    }

    private static Map<String, Object> body(Object... pairs) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], pairs[i + 1]);
        }
        return m;
    }

    @Test
    void anActionNotOnTheListIsRefused() {
        assertFalse(AdminAction.check(body("action", "op"), registry).ok());
        assertFalse(AdminAction.check(body("action", "say hello"), registry).ok());
        assertFalse(AdminAction.check(null, registry).ok());
    }

    @Test
    void anActionThatCannotBeUndoneMustBeConfirmed() {
        Settlement s = village();
        String id = s.id().toString();
        assertFalse(AdminAction.check(body("action", "replan", "village", id), registry).ok());
        assertTrue(AdminAction.check(body("action", "replan", "village", id, "confirm", true), registry).ok());
        assertTrue(AdminAction.check(body("action", "unstick", "village", id), registry).ok(), "freeing villagers is safe to do without asking");
    }

    @Test
    void theVillageMustExist() {
        village();
        assertFalse(AdminAction.check(body("action", "unstick", "village", UUID.randomUUID().toString()), registry).ok());
        assertFalse(AdminAction.check(body("action", "unstick", "village", "not an id"), registry).ok());
        assertFalse(AdminAction.check(body("action", "unstick"), registry).ok());
        assertTrue(AdminAction.check(body("action", "save"), registry).ok(), "saving names no village");
    }

    @Test
    void numbersAreWholeAndInRange() {
        String id = village().id().toString();
        assertTrue(AdminAction.check(body("action", "warp", "village", id, "confirm", true, "days", 10), registry).ok());
        assertTrue(AdminAction.check(body("action", "warp", "village", id, "confirm", true, "days", "10"), registry).ok());
        assertFalse(AdminAction.check(body("action", "warp", "village", id, "confirm", true, "days", 0), registry).ok());
        assertFalse(AdminAction.check(body("action", "warp", "village", id, "confirm", true, "days", 61), registry).ok());
        assertFalse(AdminAction.check(body("action", "warp", "village", id, "confirm", true, "days", 2.5), registry).ok());
        assertFalse(AdminAction.check(body("action", "warp", "village", id, "confirm", true, "days", "1e9"), registry).ok());
        assertEquals(1, AdminAction.check(body("action", "fastforward", "village", id, "speed", 1), registry).request().number(), "1 is normal speed");
        assertFalse(AdminAction.check(body("action", "fastforward", "village", id, "speed", 101), registry).ok());
        assertFalse(AdminAction.check(body("action", "resettle", "village", id, "confirm", true, "travellers", 7), registry).ok());
    }

    @Test
    void aNameIsShortAndPlain() {
        String id = village().id().toString();
        assertTrue(AdminAction.check(body("action", "rename", "village", id, "confirm", true, "name", "Old Mill"), registry).ok());
        assertFalse(AdminAction.check(body("action", "rename", "village", id, "confirm", true, "name", "<script>"), registry).ok());
        assertFalse(AdminAction.check(body("action", "rename", "village", id, "confirm", true, "name", "x".repeat(33)), registry).ok());
        assertFalse(AdminAction.check(body("action", "rename", "village", id, "confirm", true, "name", "   "), registry).ok());
    }

    @Test
    void aJobNamesAResidentOfThatVillageAndARealTrade() {
        Settlement s = village();
        String id = s.id().toString();
        String dara = new UUID(160, 1).toString();
        assertTrue(AdminAction.check(body("action", "job", "village", id, "resident", dara, "occupation", "builder"), registry).ok());
        assertFalse(AdminAction.check(body("action", "job", "village", id, "resident", dara, "occupation", "king"), registry).ok());
        assertFalse(AdminAction.check(body("action", "job", "village", id, "resident", UUID.randomUUID().toString(), "occupation", "farmer"), registry).ok());
        Settlement other = registry.found("world", 500, 500, 0);
        assertFalse(AdminAction.check(body("action", "job", "village", other.id().toString(), "resident", dara, "occupation", "farmer"), registry).ok(),
                "a resident of another village");
    }

    @Test
    void settingAJobPinsItAndUnpinningHandsItBack() {
        Settlement s = village();
        Resident dara = s.resident(new UUID(160, 1)).orElseThrow();
        AdminAction.setJob(s, dara, Occupation.BUILDER, 5, "an admin (dashboard)");
        assertEquals(Occupation.BUILDER, dara.occupation());
        assertTrue(s.isPinned(dara.id()));
        assertTrue(s.history().stream().anyMatch(e -> e.text().contains("by an admin (dashboard)")));
        AdminAction.unpin(s, dara, 6);
        assertFalse(s.isPinned(dara.id()));
    }

    @Test
    void anAuditLineIsOneLineWhateverItWasGiven() {
        Settlement s = village();
        AdminAction.Request rename = AdminAction.check(body("action", "rename", "village", s.id().toString(), "confirm", true, "name", "Old Mill"),
                registry).request();
        String line = AdminAction.auditLine(Instant.parse("2026-10-16T12:00:00Z"), "owen@example.com\nforged", rename, "Hamlet|x", true, "done\r\n");
        assertFalse(line.contains("\n") || line.contains("\r"));
        assertTrue(line.startsWith("2026-10-16T12:00:00Z | owen@example.com forged | rename | Hamlet x | \"Old Mill\" | ok"), line);
    }

    @Test
    void theCatalogueListsEveryAction() {
        assertEquals(AdminAction.Kind.values().length, AdminAction.catalogue().size());
        assertTrue(AdminAction.catalogue().stream().anyMatch(a -> a.get("action").equals("cancel-project") && Boolean.TRUE.equals(a.get("confirm"))));
    }

    @Test
    void aRefusalIsOneLineToo() {
        String line = AdminAction.rejectedLine(Instant.parse("2026-10-16T12:00:00Z"), "dashboard", "127.0.0.1", "bad" + (char) 10 + "token|x");
        assertEquals("2026-10-16T12:00:00Z | dashboard | refused | 127.0.0.1 | bad token x", line);
    }

    @Test
    void aChildCannotBeGivenATradeAndAFastSpeedMustBeConfirmed() {
        Settlement s = village();
        Resident child = new Resident(new UUID(160, 2), "Pip", "Mercer", Gender.MALE, new Traits(50, 50, 50, 80), Occupation.UNEMPLOYED,
                false, 0, null, null, Needs.initial());
        s.addResident(child);
        String id = s.id().toString();
        assertFalse(AdminAction.check(body("action", "job", "village", id, "resident", child.id().toString(), "occupation", "farmer"), registry).ok());
        assertFalse(AdminAction.check(body("action", "fastforward", "village", id, "speed", 50), registry).ok(), "50x is heavy: ask first");
        assertTrue(AdminAction.check(body("action", "fastforward", "village", id, "speed", 50, "confirm", true), registry).ok());
        assertTrue(AdminAction.check(body("action", "fastforward", "village", id, "speed", 20), registry).ok());
        assertFalse(AdminAction.check(body("action", "regrade", "village", id), registry).ok(), "mending spends materials: ask first");
    }

    @Test
    void anAuditLineDropsEveryKindOfLineBreak() {
        String line = AdminAction.rejectedLine(Instant.parse("2026-10-16T12:00:00Z"), "a" + (char) 0x2028 + "b" + (char) 0x85 + "c",
                "127.0.0.1", "x" + (char) 0x2029 + "y" + (char) 11 + "z");
        assertEquals("2026-10-16T12:00:00Z | a b c | refused | 127.0.0.1 | x y z", line);
    }

    @Test
    void aWorldCanBeWarpedOrFastForwardedOnItsOwn() {
        village();
        AdminAction.Checked warp = AdminAction.check(body("action", "warp-all", "days", 3, "world", "world_nether", "confirm", true), registry);
        assertTrue(warp.ok(), "" + warp.error());
        assertEquals("world_nether", warp.request().text());
        assertEquals(null, AdminAction.check(body("action", "warp-all", "days", 3, "confirm", true), registry).request().text(), "no world: every village");
        assertFalse(AdminAction.check(body("action", "warp-all", "days", 3, "world", "a b; rm", "confirm", true), registry).ok(), "not a world name");
        AdminAction.Checked fast = AdminAction.check(body("action", "fastforward-all", "speed", 10, "world", "world"), registry);
        assertTrue(fast.ok(), "" + fast.error());
        assertEquals("world", fast.request().text());
        assertEquals(null, fast.request().village());
        assertFalse(AdminAction.check(body("action", "fastforward-all", "speed", 50, "world", "world"), registry).ok(), "above 20 must be confirmed");
        assertTrue(AdminAction.check(body("action", "fastforward-all", "speed", 50, "confirm", true), registry).ok());
        assertFalse(AdminAction.check(body("action", "fastforward-all", "speed", 101), registry).ok());
    }

    @Test
    void aWorldsSpeedAndWarpDefaultCanBeSaved() {
        village();
        AdminAction.Checked speed = AdminAction.check(body("action", "set-world-speed", "world", "world", "speed", 5), registry);
        assertTrue(speed.ok(), "" + speed.error());
        assertEquals("world", speed.request().text());
        assertEquals(5, speed.request().number());
        assertFalse(AdminAction.check(body("action", "set-world-speed", "speed", 5), registry).ok(), "it must name a world");
        assertFalse(AdminAction.check(body("action", "set-world-speed", "world", "world", "speed", 40), registry).ok(), "above 20 must be confirmed");
        assertTrue(AdminAction.check(body("action", "set-world-speed", "world", "world", "speed", 40, "confirm", true), registry).ok());
        assertFalse(AdminAction.check(body("action", "set-world-speed", "world", "world", "speed", 0), registry).ok());
        assertTrue(AdminAction.check(body("action", "set-world-warp", "world", "world", "days", 7), registry).ok());
        assertFalse(AdminAction.check(body("action", "set-world-warp", "world", "world", "days", 31), registry).ok());
    }

    @Test
    void everyVillageCanBeWarpedUpToThirtyDaysAfterAConfirmation() {
        village();
        assertFalse(AdminAction.check(body("action", "warp-all", "days", 5), registry).ok(), "it must be confirmed");
        AdminAction.Checked ok = AdminAction.check(body("action", "warp-all", "days", 5, "confirm", true), registry);
        assertTrue(ok.ok(), "" + ok.error());
        assertEquals(5, ok.request().number());
        assertEquals(null, ok.request().village(), "it names no village");
        assertTrue(AdminAction.check(body("action", "warp-all", "days", 30, "confirm", true), registry).ok());
        assertFalse(AdminAction.check(body("action", "warp-all", "days", 31, "confirm", true), registry).ok(), "more than the limit for all at once");
        assertFalse(AdminAction.check(body("action", "warp-all", "days", 0, "confirm", true), registry).ok());
        assertFalse(AdminAction.check(body("action", "warp-all", "confirm", true), registry).ok());
        assertTrue(AdminAction.catalogue().stream().anyMatch(a -> a.get("action").equals("warp-all") && Boolean.TRUE.equals(a.get("confirm"))
                && Boolean.FALSE.equals(a.get("village"))));
    }

    @Test
    void aStreetCheckNamesAVillageAndMustBeConfirmed() {
        String id = village().id().toString();
        assertFalse(AdminAction.check(body("action", "grade-streets", "village", id), registry).ok());
        assertTrue(AdminAction.check(body("action", "grade-streets", "village", id, "confirm", true), registry).ok());
        assertFalse(AdminAction.check(body("action", "grade-streets", "confirm", true), registry).ok(), "no village");
        assertFalse(AdminAction.check(body("action", "grade-streets", "village", UUID.randomUUID().toString(), "confirm", true), registry).ok());
    }
}
