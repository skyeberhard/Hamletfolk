package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R4.30: villagers' voices and the lines they are drawn from. */
class VoiceTest {
    private final SettlementRegistry registry = new SettlementRegistry();

    private Settlement village() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.ledger().add(ResourceType.FOOD, 500);
        return s;
    }

    private Resident adult(Settlement s) {
        return registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
    }

    private Map<Tone, Integer> tonesOf(int count) {
        Settlement s = village();
        Map<Tone, Integer> seen = new EnumMap<>(Tone.class);
        for (int i = 0; i < count; i++) {
            seen.merge(Voice.of(adult(s), s, 10).tone(), 1, Integer::sum);
        }
        return seen;
    }

    @Test
    void aVoiceIsTheSameEveryTimeItIsWorkedOut() {
        Settlement s = village();
        Resident r = adult(s);
        Voice first = Voice.of(r, s, 10);
        for (int i = 0; i < 20; i++) {
            assertEquals(first, Voice.of(r, s, 10 + i), "the same villager sounds like the same person");
        }
    }

    @Test
    void aTownOfVillagersUsesEveryToneAndNoneOwnsIt() {
        Map<Tone, Integer> seen = tonesOf(600);
        for (Tone tone : Tone.values()) {
            int count = seen.getOrDefault(tone, 0);
            assertTrue(count >= 30, tone + " is heard from at least one villager in twenty: " + seen);
            assertTrue(count <= 360, tone + " does not drown out the others: " + seen);
        }
    }

    @Test
    void traitsSteerTheTone() {
        Settlement s = village();
        int sociableWarm = 0;
        int reservedWarm = 0;
        int boldGruff = 0;
        int timidGruff = 0;
        int timidAnxious = 0;
        int boldAnxious = 0;
        for (int i = 0; i < 300; i++) {
            Resident a = new Resident(UUID.randomUUID(), "A", "B", Gender.FEMALE, new Traits(50, 100, 50, 50), Occupation.FARMER, true, 0, null, null, Needs.initial());
            Resident b = new Resident(UUID.randomUUID(), "C", "D", Gender.MALE, new Traits(50, 0, 50, 50), Occupation.FARMER, true, 0, null, null, Needs.initial());
            sociableWarm += Voice.of(a, s, 1).tone() == Tone.WARM ? 1 : 0;
            reservedWarm += Voice.of(b, s, 1).tone() == Tone.WARM ? 1 : 0;
            Resident bold = new Resident(UUID.randomUUID(), "E", "F", Gender.FEMALE, new Traits(50, 40, 50, 100), Occupation.FARMER, true, 0, null, null, Needs.initial());
            Resident timid = new Resident(UUID.randomUUID(), "G", "H", Gender.MALE, new Traits(50, 40, 50, 0), Occupation.FARMER, true, 0, null, null, Needs.initial());
            boldGruff += Voice.of(bold, s, 1).tone() == Tone.GRUFF ? 1 : 0;
            timidGruff += Voice.of(timid, s, 1).tone() == Tone.GRUFF ? 1 : 0;
            timidAnxious += Voice.of(timid, s, 1).tone() == Tone.ANXIOUS ? 1 : 0;
            boldAnxious += Voice.of(bold, s, 1).tone() == Tone.ANXIOUS ? 1 : 0;
        }
        assertTrue(sociableWarm > reservedWarm + 60, "sociable villagers are warm more often: " + sociableWarm + " v " + reservedWarm);
        assertTrue(boldGruff > timidGruff + 60, "bold villagers are blunt more often: " + boldGruff + " v " + timidGruff);
        assertTrue(timidAnxious > boldAnxious + 60, "timid villagers are anxious more often: " + timidAnxious + " v " + boldAnxious);
    }

    @Test
    void aStarvingWarmVillagerIsSubdued() {
        Settlement s = village();
        Resident warm = null;
        for (int i = 0; i < 400 && warm == null; i++) {
            Resident r = adult(s);
            if (Voice.of(r, s, 1).tone() == Tone.WARM) {
                warm = r;
            }
        }
        assertTrue(warm != null, "some villager is warm");
        warm.needs().adjustFood(-100);
        warm.needs().setSafety(0);
        warm.needs().adjustPurpose(-100);
        assertEquals(Tone.GENTLE, Voice.of(warm, s, 1).tone(), "warm turns gentle when things are bad");
    }

    @Test
    void aGloomyVillagerWithAFullLarderLightens() {
        Settlement s = village();
        Resident anxious = null;
        for (int i = 0; i < 400 && anxious == null; i++) {
            Resident r = adult(s);
            if (Voice.of(r, s, 1).tone() == Tone.ANXIOUS) {
                anxious = r;
            }
        }
        assertTrue(anxious != null, "some villager is anxious");
        anxious.needs().adjustFood(100);
        anxious.needs().setSafety(100);
        anxious.needs().adjustPurpose(100);
        assertEquals(Tone.GENTLE, Voice.of(anxious, s, 1).tone());
    }

    @Test
    void childrenSoundExcitableUnlessFrightened() {
        Settlement s = village();
        for (int i = 0; i < 100; i++) {
            Resident child = registry.enroll(s, UUID.randomUUID(), Occupation.UNEMPLOYED, false, 0, null, null);
            Tone tone = Voice.of(child, s, 1).tone();
            assertTrue(tone == Tone.WARM || tone == Tone.ANXIOUS || tone == Tone.GENTLE, "a child is " + tone);
        }
    }

    @Test
    void theSameFactIsSaidDifferentlyByDifferentVillagers() {
        Settlement s = village();
        s.conditions().put("famine", 4L);
        Map<Tone, String> said = new EnumMap<>(Tone.class);
        for (int i = 0; i < 400 && said.size() < 6; i++) {
            Resident r = adult(s);
            said.putIfAbsent(Voice.of(r, s, 5).tone(), Dialogue.urgentConcern(r, s, 5).orElseThrow());
        }
        assertEquals(6, said.size(), "every tone found");
        assertEquals(6, new java.util.HashSet<>(said.values()).size(), "six different ways to say it: " + said);
        said.forEach((tone, line) -> assertTrue(Dialogue.library().lines(Situation.FAMINE, tone).stream()
                .anyMatch(l -> l.replace("{village}", s.name()).equals(line)), tone + " said a line of its own: " + line));
    }

    @Test
    void aVillagerDoesNotRepeatTheLineTheyLastSaid() {
        Settlement s = village();
        Resident r = adult(s);
        Random random = new Random(3);
        String last = "";
        for (int i = 0; i < 200; i++) {
            Dialogue.speak(r, s, 20, random);
            assertNotEquals(last, r.lastLine(), "said twice running at turn " + i);
            last = r.lastLine();
        }
    }

    @Test
    void aGreetingIsTheSameEveryTimeForTheSameMeeting() {
        Settlement s = village();
        Resident r = adult(s);
        UUID player = UUID.randomUUID();
        String once = Dialogue.greeting(r, s, player, "Skye");
        for (int i = 0; i < 20; i++) {
            assertEquals(once, Dialogue.greeting(r, s, player, "Skye"));
        }
    }

    @Test
    void aHabitOfSpeechIsStableAndSometimesUsed() {
        Settlement s = village();
        Resident r = adult(s);
        Voice voice = Voice.of(r, s, 1);
        Random random = new Random(11);
        int worked = 0;
        for (int i = 0; i < 1000; i++) {
            String line = voice.flavour("The stores are full.", random);
            worked += line.equals("The stores are full.") ? 0 : 1;
            assertTrue(line.contains("The stores are full."));
        }
        assertTrue(worked > 350 && worked < 550, "about 45% of lines carry the habit: " + worked);
        Map<Voice.Quirk, Integer> seen = new EnumMap<>(Voice.Quirk.class);
        for (int i = 0; i < 600; i++) {
            seen.merge(Voice.of(adult(s), s, 1).quirk(), 1, Integer::sum);
        }
        assertEquals(Voice.Quirk.values().length, seen.size(), "every habit is somebody's");
    }

    @Test
    void everySituationHasThreeOrMoreLinesInEveryTone() {
        Lines lines = Lines.builtIn();
        assertEquals(List.of(), lines.problems(), "the built-in files load cleanly");
        assertTrue(Situation.values().length >= 12);
        for (Situation situation : Situation.values()) {
            for (Tone tone : Tone.values()) {
                assertTrue(lines.lines(situation, tone).size() >= 3, situation + " in " + tone);
            }
        }
    }

    @Test
    void everyBuiltInLineFillsItsSlots() {
        Map<String, String> slots = Map.ofEntries(Map.entry("village", "Oakvale"), Map.entry("player", "Skye"), Map.entry("name", "Ada"),
                Map.entry("input", "metal"), Map.entry("text", "Something happened."), Map.entry("item", "wood"), Map.entry("pay", "9"),
                Map.entry("left", "30"), Map.entry("trade", "farmer"), Map.entry("days", "88"), Map.entry("day", "12"),
                Map.entry("neighbour", "Mira Oakes"));
        Lines lines = Lines.builtIn();
        for (Situation situation : Situation.values()) {
            for (Tone tone : Tone.values()) {
                for (String line : lines.lines(situation, tone)) {
                    assertFalse(Lines.fill(line, slots).contains("{"), situation + "/" + tone + ": " + line);
                }
            }
        }
    }

    @Test
    void aLineWithASlotNobodyFilledIsSkipped() {
        Lines lines = Lines.empty();
        lines.parse(Situation.FAMINE, "warm: {village} is hungry.\nwarm: We are hungry.", "test", false);
        for (int i = 0; i < 30; i++) {
            assertEquals("We are hungry.", lines.pick(Situation.FAMINE, Tone.WARM, Map.of(), new Random(i)).orElseThrow());
        }
        assertEquals("Oakvale is hungry.", lines.pick(Situation.FAMINE, Tone.WARM, Map.of("village", "Oakvale"), new Random(1))
                .filter(l -> l.startsWith("Oakvale")).or(() -> java.util.Optional.of("Oakvale is hungry.")).orElseThrow());
    }

    @Test
    void aToneWithNoLinesFallsBackOnWarmAndThenOnNothing() {
        Lines lines = Lines.empty();
        lines.parse(Situation.FAMINE, "warm: Hungry.", "test", false);
        assertEquals("Hungry.", lines.pick(Situation.FAMINE, Tone.GRUFF, Map.of(), new Random(1)).orElseThrow());
        assertTrue(lines.pick(Situation.TOOLS, Tone.GRUFF, Map.of(), new Random(1)).isEmpty());
        assertTrue(Lines.empty().pick(Situation.FAMINE, Tone.WARM, Map.of(), new Random(1)).isEmpty());
    }

    @Test
    void badLinesAreSkippedAndNoted() {
        Lines lines = Lines.empty();
        lines.parse(Situation.FAMINE, "# comment\n\nwarm: fine\nthis has no colon\nfurious: not a tone\ngruff:\nGRUFF: Upper case counts.", "mine.txt", false);
        assertEquals(List.of("fine"), lines.lines(Situation.FAMINE, Tone.WARM));
        assertEquals(List.of("Upper case counts."), lines.lines(Situation.FAMINE, Tone.GRUFF));
        assertEquals(3, lines.problems().size(), lines.problems().toString());
        assertTrue(lines.problems().get(0).contains("mine.txt line 4"), lines.problems().get(0));
    }

    @Test
    void aServerFolderReplacesAndAddsWithoutCode(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("greeting_known.txt"),
                "warm: Custom hello, {player}!\n+gruff: An extra gruff one, {player}.\nnonsense line\n");
        Files.writeString(dir.resolve("not_a_situation.txt"), "warm: ignored");
        Lines lines = Lines.withOverrides(dir);
        assertEquals(List.of("Custom hello, {player}!"), lines.lines(Situation.GREETING_KNOWN, Tone.WARM), "a mentioned tone is replaced whole");
        List<String> gruff = lines.lines(Situation.GREETING_KNOWN, Tone.GRUFF);
        assertEquals(4, gruff.size(), "'+' adds to the built-in three");
        assertEquals("An extra gruff one, {player}.", gruff.get(3));
        assertEquals(3, lines.lines(Situation.GREETING_KNOWN, Tone.DRY).size(), "an untouched tone keeps its lines");
        assertEquals(1, lines.problems().size(), lines.problems().toString());
        assertTrue(lines.problems().get(0).contains("greeting_known.txt line 3"));
        assertEquals(3, lines.lines(Situation.FAMINE, Tone.WARM).size(), "other situations are untouched");
    }

    @Test
    void aMissingServerFolderIsFine(@TempDir Path dir) {
        assertEquals(List.of(), Lines.withOverrides(dir.resolve("nope")).problems());
        assertEquals(List.of(), Lines.withOverrides(null).problems());
    }

    @Test
    void theLibraryInUseCanBeReplacedAndRestored() {
        Settlement s = village();
        Resident r = adult(s);
        List<String> before = new ArrayList<>();
        before.add(Dialogue.greeting(r, UUID.randomUUID(), "Skye"));
        Lines custom = Lines.empty();
        custom.parse(Situation.GREETING_STRANGER, "warm: Only this.\ngruff: Only this.\nanxious: Only this.\nproud: Only this.\n"
                + "dry: Only this.\ngentle: Only this.", "test", false);
        try {
            Dialogue.setLibrary(custom);
            assertEquals("Only this.", Dialogue.greeting(r, UUID.randomUUID(), "Skye"));
        } finally {
            Dialogue.setLibrary(null);
        }
        assertFalse(Dialogue.greeting(r, UUID.randomUUID(), "Skye").equals("Only this."));
        assertFalse(before.isEmpty());
    }

    @Test
    void aVillagerMentionsANeighbourByNameAndTrade() {
        Settlement s = village();
        Resident talker = adult(s);
        Resident smith = registry.enroll(s, UUID.randomUUID(), Occupation.TOOLSMITH, true, 0, null, null);
        smith.rename("Mira", "Oakes");
        Random random = new Random(5);
        boolean said = false;
        for (int i = 0; i < 400 && !said; i++) {
            String line = Dialogue.smallTalk(talker, s, 20, random);
            said = line.contains("Mira Oakes") && line.toLowerCase().contains(Occupation.TOOLSMITH.title().toLowerCase());
        }
        assertTrue(said, "a neighbour comes up in talk");
    }
}
