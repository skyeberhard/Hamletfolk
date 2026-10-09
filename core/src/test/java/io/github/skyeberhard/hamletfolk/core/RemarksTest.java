package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R4.32: what villagers say about the land, the village's mood and size, their money and a parent, in lines of their own. */
class RemarksTest {
    private final SettlementRegistry registry = new SettlementRegistry();
    private final Lines lines = Lines.builtIn();

    private Settlement village(String biome, int people) {
        Settlement s = registry.found("world", 0, 0, 0);
        home = s;
        s.setLand(SiteSurvey.score(SiteSurvey.grid(96, 24, (dx, dz) -> biome)));
        s.ledger().add(ResourceType.FOOD, 500);
        for (int i = 0; i < people; i++) {
            registry.enroll(s, UUID.randomUUID(), Occupation.UNEMPLOYED, true, 0, null, null);
        }
        return s;
    }

    /** One adult of each tone in the village. */
    private Map<Tone, Resident> oneOfEachTone(Settlement s) {
        Map<Tone, Resident> found = new EnumMap<>(Tone.class);
        for (int i = 0; i < 800 && found.size() < 6; i++) {
            Resident r = registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
            found.putIfAbsent(Voice.of(r, s, 10).tone(), r);
        }
        assertEquals(6, found.size());
        return found;
    }

    private Settlement home;

    private boolean saysAnyOf(String line, Map<Tone, Resident> byTone, Resident speaker, Situation situation, Map<String, String> slots,
            Lines lines) {
        Tone tone = Voice.of(speaker, home, 10).tone(); // (the tone they really speak in, village temperament and all)
        return lines.lines(situation, tone).stream().anyMatch(l -> Lines.fill(l, slots).equals(line));
    }

    // ----- the files -----

    @Test
    void everyVocabularyEntryFillsEveryLineOfItsSituation() {
        Map<String, String> village = Map.of("village", "Oakvale");
        for (Leaning leaning : Leaning.values()) {
            if (leaning == Leaning.ALL_ROUND) {
                continue;
            }
            fillsCompletely(Situation.LAND, new HashMap<>(lines.vocab("land", leaning.name())), village, "land/" + leaning);
        }
        for (VillageCharacter.Temperament t : VillageCharacter.Temperament.values()) {
            if (t != VillageCharacter.Temperament.STEADY) {
                fillsCompletely(Situation.MOOD, new HashMap<>(lines.vocab("mood", t.name())), village, "mood/" + t);
            }
        }
        for (VillageCharacter.Stage stage : VillageCharacter.Stage.values()) {
            if (stage != VillageCharacter.Stage.VILLAGE) {
                Map<String, String> slots = new HashMap<>(lines.vocab("size", stage.name()));
                slots.put("count", "31");
                fillsCompletely(Situation.SIZE, slots, village, "size/" + stage);
            }
        }
        for (String key : List.of("broke", "idle_broke", "modest", "comfortable", "wealthy")) {
            Map<String, String> slots = new HashMap<>(lines.vocab("wealth", key));
            slots.computeIfPresent("standing", (k, v) -> v.replace("{amount}", "12"));
            fillsCompletely(Situation.WEALTH, slots, village, "wealth/" + key);
        }
        for (Gender gender : Gender.values()) {
            fillsCompletely(Situation.PARENT, new HashMap<>(Map.of("kin", gender == Gender.FEMALE ? "mother" : "father", "parent", "Wren",
                    "obj", gender.object(), "poss", gender.possessive())), village, "parent/" + gender);
        }
    }

    private void fillsCompletely(Situation situation, Map<String, String> words, Map<String, String> village, String what) {
        assertFalse(words.isEmpty(), what + " has a vocabulary entry");
        Map<String, String> slots = new HashMap<>(words);
        slots.putAll(village);
        for (Tone tone : Tone.values()) {
            assertTrue(lines.lines(situation, tone).size() >= 3, situation + "/" + tone);
            for (String line : lines.lines(situation, tone)) {
                String filled = Lines.fill(line, slots);
                assertFalse(filled.contains("{"), what + "/" + tone + ": " + filled);
            }
        }
    }

    @Test
    void everyLineCarriesTheFactItIsAbout() {
        // LAND: place, living or sight; MOOD: the sense; SIZE: the size, the count or the feel; WEALTH: the standing; PARENT: kin and name
        for (Tone tone : Tone.values()) {
            for (String l : lines.lines(Situation.LAND, tone)) {
                assertTrue(l.toLowerCase(Locale.ROOT).contains("{place}") || l.toLowerCase(Locale.ROOT).contains("{living}")
                        || l.toLowerCase(Locale.ROOT).contains("{sight}"), "LAND/" + tone + ": " + l);
            }
            for (String l : lines.lines(Situation.MOOD, tone)) {
                assertTrue(l.toLowerCase(Locale.ROOT).contains("{sense}"), "MOOD/" + tone + ": " + l);
            }
            for (String l : lines.lines(Situation.SIZE, tone)) {
                String low = l.toLowerCase(Locale.ROOT);
                assertTrue(low.contains("{size}") || low.contains("{count}") || low.contains("{feel}"), "SIZE/" + tone + ": " + l);
            }
            for (String l : lines.lines(Situation.WEALTH, tone)) {
                assertTrue(l.toLowerCase(Locale.ROOT).contains("{standing}"), "WEALTH/" + tone + ": " + l);
            }
            for (String l : lines.lines(Situation.PARENT, tone)) {
                assertTrue(l.contains("{parent}") && l.contains("{kin}"), "PARENT/" + tone + ": " + l);
            }
        }
    }

    @Test
    void theSameFactIsSaidDifferentlyByEachTone() {
        Map<String, String> slots = new HashMap<>(lines.vocab("land", "MINING"));
        slots.put("village", "Oakvale");
        Set<String> said = new HashSet<>();
        for (Tone tone : Tone.values()) {
            for (long seed = 0; seed < 10; seed++) {
                said.add(tone + ": " + lines.pick(Situation.LAND, tone, slots, new Random(seed)).orElseThrow());
            }
        }
        for (Tone tone : Tone.values()) {
            assertTrue(said.stream().filter(x -> x.startsWith(tone + ": ")).count() >= 3, "three or more ways in " + tone);
        }
    }

    // ----- in talk -----

    @Test
    void aVillagerSpeaksOfTheLandInLinesOfTheirTonesOwn() {
        Settlement s = village("windswept_hills", 10);
        assertEquals(Leaning.MINING, s.leaning());
        Map<String, String> slots = new HashMap<>(lines.vocab("land", "MINING"));
        slots.put("village", s.name());
        for (Map.Entry<Tone, Resident> entry : oneOfEachTone(s).entrySet()) {
            Random random = new Random(6);
            boolean said = false;
            for (int i = 0; i < 1500 && !said; i++) {
                String line = Dialogue.smallTalk(entry.getValue(), s, 10, random);
                said = saysAnyOf(line, null, entry.getValue(), Situation.LAND, slots, lines);
            }
            assertTrue(said, entry.getKey() + " speaks of the hills");
        }
    }

    @Test
    void aVillagerSpeaksOfTheVillagesMoodAndSize() {
        Settlement s = village("desert", 25);
        s.ledger().addTreasury(300);
        s.ledger().add(Commodity.BREAD, 3000);
        assertEquals(VillageCharacter.Temperament.PROSPEROUS, VillageCharacter.temperament(s));
        Map<String, String> mood = new HashMap<>(lines.vocab("mood", "PROSPEROUS"));
        mood.put("village", s.name());
        Map<String, String> size = new HashMap<>(lines.vocab("size", "TOWN"));
        size.put("count", String.valueOf(s.population()));
        size.put("village", s.name());
        Resident r = s.residents().iterator().next();
        Random random = new Random(3);
        boolean moodSaid = false;
        boolean sizeSaid = false;
        for (int i = 0; i < 3000 && !(moodSaid && sizeSaid); i++) {
            String line = Dialogue.smallTalk(r, s, 10, random);
            moodSaid |= saysAnyOf(line, null, r, Situation.MOOD, mood, lines);
            sizeSaid |= saysAnyOf(line, null, r, Situation.SIZE, size, lines);
        }
        assertTrue(moodSaid, "the stores are full and the treasury heavy");
        assertTrue(sizeSaid, "a town");
    }

    @Test
    void aVillagerSpeaksOfTheirMoneyAndOfTheirParent() {
        Settlement s = village("desert", 3);
        Resident farmer = registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
        Map<String, String> broke = new HashMap<>(lines.vocab("wealth", "BROKE"));
        Random random = new Random(1);
        boolean money = false;
        for (int i = 0; i < 2000 && !money; i++) {
            money = saysAnyOf(Dialogue.smallTalk(farmer, s, 10_005, random), null, farmer, Situation.WEALTH, broke, lines);
        }
        assertTrue(money, "a broke farmer says there is nothing put by");

        // and someone out of work says it their own way
        Resident idle = s.residents().stream().filter(r -> r.occupation() == Occupation.UNEMPLOYED).findFirst().orElseThrow();
        Map<String, String> idleBroke = new HashMap<>(lines.vocab("wealth", "idle_broke"));
        Random again = new Random(8);
        boolean idleSaid = false;
        for (int i = 0; i < 2000; i++) {
            String line = Dialogue.smallTalk(idle, s, 10_005, again);
            idleSaid |= saysAnyOf(line, null, idle, Situation.WEALTH, idleBroke, lines);
            assertFalse(saysAnyOf(line, null, idle, Situation.WEALTH, broke, lines), "not the working farmer's words: " + line);
        }
        assertTrue(idleSaid, "someone out of work has not a coin to their name");

        Resident parent = registry.enroll(s, UUID.randomUUID(), Occupation.FARMER, true, 0, null, null);
        Resident child = registry.enroll(s, UUID.randomUUID(), Occupation.UNEMPLOYED, false, 1, parent.id(), null);
        Map<String, String> kin = Map.of("kin", parent.gender() == Gender.FEMALE ? "mother" : "father", "parent", parent.givenName(),
                "obj", parent.gender().object(), "poss", parent.gender().possessive());
        boolean said = false;
        for (int i = 0; i < 2000 && !said; i++) {
            String line = Dialogue.smallTalk(child, s, 10, random);
            said = saysAnyOf(line, null, child, Situation.PARENT, kin, lines);
            if (said) {
                java.util.regex.Pattern wrong = parent.gender() == Gender.FEMALE ? java.util.regex.Pattern.compile("\\b(him|his)\\b")
                        : java.util.regex.Pattern.compile("\\b(her|hers)\\b");
                assertFalse(wrong.matcher(line).find(), "the right pronouns: " + line);
            }
        }
        assertTrue(said, "a child speaks of their " + kin.get("kin"));
    }

    // ----- servers and fallbacks -----

    @Test
    void aServerFileChangesWhatTheLandIsCalledAndIsHeardInTalk(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("vocab_land.txt"), "mining: place=the deep mountains; living=iron; sight=smoke over the peaks\n");
        Files.writeString(dir.resolve("vocab_mood.txt"), "martial: nonsense\nwary: sense=we keep the gates barred\n");
        Lines custom = Lines.withOverrides(dir);
        assertEquals("the deep mountains", custom.vocab("land", "mining").get("place"));
        assertEquals("the forest", custom.vocab("land", "timber").get("place"), "other leanings keep the built-in words");
        assertEquals("we keep the gates barred", custom.vocab("mood", "wary").get("sense"));
        assertEquals(1, custom.problems().size(), custom.problems().toString());
        assertTrue(custom.problems().get(0).contains("vocab_mood.txt line 1"), custom.problems().get(0));

        Settlement s = village("windswept_hills", 6);
        Resident r = s.residents().iterator().next();
        Map<String, String> slots = new HashMap<>(custom.vocab("land", "MINING"));
        slots.put("village", s.name());
        Lines before = Dialogue.library();
        try {
            Dialogue.setLibrary(custom);
            Random random = new Random(2);
            boolean said = false;
            for (int i = 0; i < 2000 && !said; i++) {
                said = saysAnyOf(Dialogue.smallTalk(r, s, 10, random), null, r, Situation.LAND, slots, custom);
            }
            assertTrue(said, "villagers talk about the deep mountains now");
        } finally {
            Dialogue.setLibrary(before);
        }
    }

    @Test
    void withoutAVocabularyEntryTheOldSentenceIsSaidInstead() {
        Settlement s = village("windswept_hills", 6);
        Resident r = s.residents().iterator().next();
        Lines noWords = Lines.empty(); // lines for the land, but no vocabulary to fill them from
        for (Tone tone : Tone.values()) {
            noWords.parse(Situation.LAND, tone.key() + ": {Place} is {living}.", "test", false);
        }
        Lines before = Dialogue.library();
        try {
            Dialogue.setLibrary(noWords);
            Random random = new Random(5);
            boolean said = false;
            for (int i = 0; i < 1500 && !said; i++) {
                said = Dialogue.smallTalk(r, s, 10, random).contains("mining place at heart");
            }
            assertTrue(said, "the plain sentence about the land is not lost");
        } finally {
            Dialogue.setLibrary(before);
        }
    }

    @Test
    void aLoneResidentDoesNotTalkAboutHowManyOfUsThereAre() {
        Settlement s = village("desert", 1);
        Resident only = s.residents().iterator().next();
        Random random = new Random(7);
        for (int i = 0; i < 1000; i++) {
            String line = Dialogue.smallTalk(only, s, 10, random);
            assertFalse(line.contains("1 of us") || line.contains("There are 1"), line);
        }
    }

    @Test
    void withNoLinesAtAllTheyStillSaySomething() {
        Settlement s = village("windswept_hills", 6);
        Resident r = s.residents().iterator().next();
        Lines before = Dialogue.library();
        try {
            Dialogue.setLibrary(Lines.empty());
            Random random = new Random(4);
            for (int i = 0; i < 200; i++) {
                assertFalse(Dialogue.smallTalk(r, s, 10, random).isBlank());
            }
        } finally {
            Dialogue.setLibrary(before);
        }
    }
}
