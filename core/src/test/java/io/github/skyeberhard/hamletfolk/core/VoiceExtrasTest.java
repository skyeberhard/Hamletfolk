package io.github.skyeberhard.hamletfolk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R4.31: voices for the rest of what villagers say. */
class VoiceExtrasTest {
    private static final String FORAGE = "No trade of my own yet, so I forage what I can along the edges of the fields. It keeps "
            + "the larder from running bare while I wait for work.";
    private final SettlementRegistry registry = new SettlementRegistry();

    private Settlement village() {
        Settlement s = registry.found("world", 0, 0, 0);
        s.ledger().add(ResourceType.FOOD, 500);
        return s;
    }

    private Resident adult(Settlement s, Occupation job) {
        return registry.enroll(s, UUID.randomUUID(), job, true, 0, null, null);
    }

    /** One resident of each tone. */
    private Map<Tone, Resident> oneOfEachTone(Settlement s, Occupation job) {
        Map<Tone, Resident> found = new EnumMap<>(Tone.class);
        for (int i = 0; i < 800 && found.size() < 6; i++) {
            Resident r = adult(s, job);
            found.putIfAbsent(Voice.of(r, s, 10).tone(), r);
        }
        assertEquals(6, found.size());
        return found;
    }

    @Test
    void aPlainRemarkIsSaidInTheSpeakersToneWithItsFactUnchanged() {
        Settlement s = village();
        Map<Tone, Resident> byTone = oneOfEachTone(s, Occupation.UNEMPLOYED);
        for (Map.Entry<Tone, Resident> entry : byTone.entrySet()) {
            Random random = new Random(4);
            String line = null;
            for (int i = 0; i < 600 && line == null; i++) {
                String said = Dialogue.smallTalk(entry.getValue(), s, 10, random);
                if (said.contains("forage")) {
                    line = said;
                }
            }
            String said = line;
            assertTrue(said != null, entry.getKey() + " mentions foraging");
            assertTrue(said.contains(FORAGE), "the fact is unchanged: " + said);
            assertTrue(Dialogue.library().lines(Situation.REMARK, entry.getKey()).stream()
                    .anyMatch(l -> l.replace("{statement}", FORAGE).equals(said)), entry.getKey() + " wraps it in its own tone: " + said);
        }
    }

    @Test
    void differentTonesWrapTheSameFactDifferently() {
        Settlement s = village();
        Map<Tone, Resident> byTone = oneOfEachTone(s, Occupation.UNEMPLOYED);
        assertEquals(6, byTone.size());
        java.util.Set<String> wrapped = new java.util.HashSet<>();
        for (Tone tone : Tone.values()) {
            Map<String, String> slots = Map.of("statement", "Rain is good for the crops.");
            for (long seed = 0; seed < 30; seed++) {
                String line = Dialogue.library().pick(Situation.REMARK, tone, slots, new Random(seed)).orElseThrow();
                assertTrue(line.contains("Rain is good for the crops."), "the fact stays: " + line);
                wrapped.add(tone + ":" + line);
            }
        }
        assertTrue(wrapped.size() >= 18, "three or more wrappings in each of six tones: " + wrapped.size());
    }

    @Test
    void everyTradeHasWordsAndTheirLinesFillCompletely() {
        Lines lines = Lines.builtIn();
        assertEquals(List.of(), lines.problems());
        for (Occupation job : Occupation.values()) {
            if (job == Occupation.UNEMPLOYED || job == Occupation.NITWIT) {
                continue;
            }
            Map<String, String> words = lines.vocab("trade", job.name());
            assertFalse(words.isEmpty(), job + " has an entry in vocab_trade.txt");
            Map<String, String> slots = new java.util.HashMap<>(words);
            slots.put("days", "75");
            slots.put("village", "Oakvale");
            for (Situation situation : List.of(Situation.WORK, Situation.WORK_EXPERT)) {
                for (Tone tone : Tone.values()) {
                    for (String line : lines.lines(situation, tone)) {
                        String filled = Lines.fill(line, slots);
                        assertFalse(filled.contains("{"), job + "/" + situation + "/" + tone + ": " + filled);
                    }
                }
            }
        }
    }

    @Test
    void aVillagerTalksAboutTheirTradeInTheTradesWords() {
        Settlement s = village();
        Map<Tone, Resident> byTone = oneOfEachTone(s, Occupation.FARMER);
        for (Map.Entry<Tone, Resident> entry : byTone.entrySet()) {
            Random random = new Random(9);
            boolean said = false;
            for (int i = 0; i < 600 && !said; i++) {
                String line = Dialogue.smallTalk(entry.getValue(), s, 10, random);
                said = line.contains("wheat and carrots") || line.contains("sowing and reaping") || line.contains("farming");
            }
            assertTrue(said, entry.getKey() + " farmer talks about farming");
        }
        Resident miner = adult(s, Occupation.MINER);
        Random random = new Random(2);
        boolean seam = false;
        for (int i = 0; i < 600 && !seam; i++) {
            String line = Dialogue.smallTalk(miner, s, 10, random);
            seam = line.contains("working the seam") || line.contains("ore and stone") || line.contains("mining");
        }
        assertTrue(seam, "and a miner about the seam");
    }

    @Test
    void anExpertSaysMoreAboutTheirTradeThanANovice() {
        Settlement s = village();
        Resident novice = adult(s, Occupation.FARMER);
        Resident master = adult(s, Occupation.FARMER);
        master.setXp(100);
        assertTrue(master.level() >= 4);
        Tone masterTone = Voice.of(master, s, 10).tone();
        Random random = new Random(5);
        boolean expertLine = false;
        for (int i = 0; i < 800 && !expertLine; i++) {
            String line = Dialogue.smallTalk(master, s, 10, random);
            expertLine = Dialogue.library().lines(Situation.WORK_EXPERT, masterTone).stream()
                    .anyMatch(l -> Lines.fill(l, Map.of("craft", "farming", "days", String.valueOf(master.xp()),
                            "product", "wheat and carrots", "village", s.name())).equals(line));
        }
        assertTrue(expertLine, "a master speaks of their " + master.xp() + " days");
        Tone noviceTone = Voice.of(novice, s, 10).tone();
        Random again = new Random(5);
        for (int i = 0; i < 800; i++) {
            String line = Dialogue.smallTalk(novice, s, 10, again);
            assertFalse(Dialogue.library().lines(Situation.WORK_EXPERT, noviceTone).stream()
                    .anyMatch(l -> Lines.fill(l, Map.of("craft", "farming", "days", "0", "product", "wheat and carrots",
                            "village", s.name())).equals(line)), "a novice does not talk like a master: " + line);
        }
    }

    @Test
    void theWeatherAndTheTimeOfDayComeUpOnlyWhenTheyAreKnown() {
        Settlement s = village();
        Resident r = adult(s, Occupation.UNEMPLOYED);
        Tone tone = Voice.of(r, s, 10).tone();
        Dialogue.Ambient ambient = new Dialogue.Ambient("rain coming down", "evening");
        Map<String, String> slots = Map.of("sky", "rain coming down", "time", "evening", "village", s.name());
        Random random = new Random(3);
        boolean said = false;
        for (int i = 0; i < 800 && !said; i++) {
            String line = Dialogue.smallTalk(r, s, 10, random, ambient);
            said = Dialogue.library().lines(Situation.WEATHER, tone).stream().anyMatch(l -> Lines.fill(l, slots).equals(line));
        }
        assertTrue(said, "they remark on the evening rain");
        Random none = new Random(3);
        for (int i = 0; i < 800; i++) {
            String line = Dialogue.smallTalk(r, s, 10, none);
            assertFalse(line.toLowerCase().contains("rain coming down"), "no weather without an ambient: " + line);
        }
        assertFalse(Dialogue.speak(r, s, 10, new Random(1), ambient).isBlank());
    }

    @Test
    void anExpertKeepsTheirOwnToneAndOnlyAMasterIsMadeProud() {
        Settlement s = village();
        java.util.Set<Tone> expertTones = new java.util.HashSet<>();
        for (int i = 0; i < 800; i++) {
            Resident r = adult(s, Occupation.FARMER);
            r.setXp(70); // an expert (level 4), not yet a master
            assertEquals(4, r.level());
            expertTones.add(Voice.of(r, s, 10).tone());
        }
        assertEquals(6, expertTones.size(), "experts come in every tone, so every WORK_EXPERT line can be said: " + expertTones);
        for (int i = 0; i < 200; i++) {
            Resident master = adult(s, Occupation.FARMER);
            master.setXp(200);
            assertEquals(5, master.level());
            Tone tone = Voice.of(master, s, 10).tone();
            assertTrue(tone == Tone.PROUD || tone == Tone.ANXIOUS || tone == Tone.GRUFF, "a master is proud (or frightened, or shaken): " + tone);
        }
    }

    @Test
    void noLineStartsASentenceWithALowercaseSlotOrPutsAnArticleBeforeATime() {
        Lines lines = Lines.builtIn();
        for (Situation situation : List.of(Situation.WORK, Situation.WORK_EXPERT, Situation.WEATHER)) {
            for (Tone tone : Tone.values()) {
                for (String line : lines.lines(situation, tone)) {
                    for (String slot : List.of("craft", "work", "time", "sky", "product")) {
                        assertFalse(line.startsWith("{" + slot + "}"), situation + "/" + tone + " starts with {" + slot + "}: " + line);
                        assertFalse(line.contains(". {" + slot + "}"), situation + "/" + tone + " starts a sentence with {" + slot + "}: " + line);
                    }
                    assertFalse(line.contains("a {time}") || line.contains("an {time}"), "no article before the time: " + line);
                }
            }
        }
    }

    @Test
    void aSlotCanBeWrittenWithACapitalForTheStartOfASentence() {
        assertEquals("Rain coming down. It is rain coming down.", Lines.fill("{Sky}. It is {sky}.", Map.of("sky", "rain coming down")));
        assertEquals("{Other}", Lines.fill("{Other}", Map.of("sky", "x")));
    }

    @Test
    void aServerFileChangesATradesWordsAndABadLineIsNoted(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("vocab_trade.txt"),
                "farmer: craft=gardening; work=weeding; product=beans\nthis is not an entry\nminer: craft=\n");
        Lines lines = Lines.withOverrides(dir);
        assertEquals("gardening", lines.vocab("trade", "farmer").get("craft"));
        assertEquals("beans", lines.vocab("trade", "FARMER").get("product"), "the key is case-blind");
        assertEquals("fish", lines.vocab("trade", "fisherman").get("product"), "other trades keep the built-in words");
        assertEquals("mining", lines.vocab("trade", "miner").get("craft"), "an empty entry does not wipe the built-in one");
        assertEquals(2, lines.problems().size(), lines.problems().toString());
        assertTrue(lines.problems().get(0).contains("vocab_trade.txt line 2"), lines.problems().get(0));
        assertTrue(lines.vocab("trade", "no-such-trade").isEmpty());
        assertTrue(lines.vocab("nowhere", "farmer").isEmpty());
    }

    @Test
    void remarkLinesAreInTheLibraryInEveryToneAndCapitaliseAgainstNothing() {
        for (Tone tone : Tone.values()) {
            assertNotEquals(0, Dialogue.library().lines(Situation.REMARK, tone).size());
            assertTrue(Dialogue.library().lines(Situation.REMARK, tone).stream().allMatch(l -> l.contains("{statement}")),
                    tone + ": every wrapper carries the fact");
        }
    }
}
