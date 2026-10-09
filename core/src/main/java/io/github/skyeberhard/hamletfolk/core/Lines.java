package io.github.skyeberhard.hamletfolk.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * R4.30: the lines villagers say, kept in plain text files (one per {@link Situation}, one {@code tone: text} line each) with
 * slots in braces for the village, trade, numbers and people. The built-in set ships in the plugin; a file of the same name in
 * a server's {@code dialogue/} folder replaces the lines of each tone it mentions, and a line that starts with {@code +}
 * adds to the built-in ones instead. A bad file or line is skipped and noted in {@link #problems()}.
 */
public final class Lines {
    private static final Pattern SLOT = Pattern.compile("\\{[A-Za-z]+}");

    /** The vocabulary files: vocab_trade.txt, vocab_land.txt, vocab_mood.txt, vocab_size.txt and vocab_wealth.txt. */
    static final List<String> VOCAB_GROUPS = List.of("trade", "land", "mood", "size", "wealth");

    private final Map<Situation, Map<Tone, List<String>>> lines = new EnumMap<>(Situation.class);
    private final List<String> problems = new ArrayList<>();
    /** R4.31: vocabulary by group (e.g. "trade") and then key (e.g. "farmer"): the slots that key fills in. */
    private final Map<String, Map<String, Map<String, String>>> vocab = new HashMap<>();

    private Lines() {
    }

    /** An empty library: nothing to say, so callers fall back on plain wording. */
    public static Lines empty() {
        return new Lines();
    }

    /** The lines that ship with the plugin. */
    public static Lines builtIn() {
        Lines out = new Lines();
        for (Situation situation : Situation.values()) {
            String text = readResource("/dialogue/" + situation.fileName());
            if (text == null) {
                out.problems.add("built-in " + situation.fileName() + " is missing");
            } else {
                out.parse(situation, text, "built-in " + situation.fileName(), false);
            }
        }
        for (String group : VOCAB_GROUPS) {
            String text = readResource("/dialogue/vocab_" + group + ".txt");
            if (text == null) {
                out.problems.add("built-in vocab_" + group + ".txt is missing");
            } else {
                out.parseVocab(group, text, "built-in vocab_" + group + ".txt");
            }
        }
        return out;
    }

    /** The built-in lines with a server's folder laid over them. A missing folder is fine. */
    public static Lines withOverrides(Path directory) {
        Lines out = builtIn();
        if (directory == null || !Files.isDirectory(directory)) {
            return out;
        }
        for (Situation situation : Situation.values()) {
            Path file = directory.resolve(situation.fileName());
            if (Files.isRegularFile(file)) {
                try {
                    out.parse(situation, Files.readString(file, StandardCharsets.UTF_8), file.getFileName().toString(), true);
                } catch (IOException | RuntimeException e) {
                    out.problems.add(file.getFileName() + " could not be read and was skipped: " + e.getMessage());
                }
            }
        }
        for (String group : VOCAB_GROUPS) {
            Path vocabFile = directory.resolve("vocab_" + group + ".txt");
            if (Files.isRegularFile(vocabFile)) {
                try {
                    out.parseVocab(group, Files.readString(vocabFile, StandardCharsets.UTF_8), vocabFile.getFileName().toString());
                } catch (IOException | RuntimeException e) {
                    out.problems.add(vocabFile.getFileName() + " could not be read and was skipped: " + e.getMessage());
                }
            }
        }
        return out;
    }

    /**
     * R4.31: reads vocabulary lines, {@code key: slot=value; slot=value}. A key that appears again (a server file over the
     * built-in one) replaces the earlier entry whole.
     */
    void parseVocab(String group, String text, String source) {
        Map<String, Map<String, String>> entries = vocab.computeIfAbsent(group, k -> new HashMap<>());
        int number = 0;
        for (String raw : text.split("\\R")) {
            number++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            Map<String, String> slots = new HashMap<>();
            if (colon > 0) {
                for (String pair : line.substring(colon + 1).split(";")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0 && !pair.substring(eq + 1).isBlank()) {
                        slots.put(pair.substring(0, eq).strip(), pair.substring(eq + 1).strip());
                    }
                }
            }
            if (colon <= 0 || slots.isEmpty()) {
                problems.add(source + " line " + number + " skipped: expected 'name: slot=value; slot=value'");
                continue;
            }
            entries.put(line.substring(0, colon).strip().toLowerCase(java.util.Locale.ROOT), slots);
        }
    }

    /** The slots a vocabulary entry fills in, or none if there is no such entry. */
    public Map<String, String> vocab(String group, String key) {
        return vocab.getOrDefault(group, Map.of()).getOrDefault(key.toLowerCase(java.util.Locale.ROOT), Map.of());
    }

    private static String readResource(String path) {
        try (InputStream in = Lines.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Reads one file's text into the library. With {@code override}, a tone the file mentions (without a leading '+') is
     * replaced whole, and '+' lines are added after; without it, every line is added (the built-in files).
     */
    void parse(Situation situation, String text, String source, boolean override) {
        Map<Tone, List<String>> mine = lines.computeIfAbsent(situation, k -> new EnumMap<>(Tone.class));
        Map<Tone, List<String>> replacing = new EnumMap<>(Tone.class);
        Map<Tone, List<String>> adding = new EnumMap<>(Tone.class);
        int number = 0;
        for (String raw : text.split("\\R")) {
            number++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            boolean plus = line.startsWith("+");
            if (plus) {
                line = line.substring(1).stripLeading();
            }
            int colon = line.indexOf(':');
            Optional<Tone> tone = colon < 0 ? Optional.empty() : Tone.fromKey(line.substring(0, colon));
            String body = colon < 0 ? "" : line.substring(colon + 1).strip();
            if (tone.isEmpty() || body.isEmpty()) {
                problems.add(source + " line " + number + " skipped: expected 'tone: text' with one of the six tones");
                continue;
            }
            (override && !plus ? replacing : adding).computeIfAbsent(tone.get(), k -> new ArrayList<>()).add(body);
        }
        replacing.forEach((tone, list) -> mine.put(tone, new ArrayList<>(list)));
        adding.forEach((tone, list) -> mine.computeIfAbsent(tone, k -> new ArrayList<>()).addAll(list));
    }

    /** What went wrong while loading, for the log. */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    /** True if the situation has at least one line in the tone. */
    public boolean has(Situation situation, Tone tone) {
        return !lines(situation, tone).isEmpty();
    }

    /** The raw lines (slots unfilled) for a situation in a tone. */
    public List<String> lines(Situation situation, Tone tone) {
        return lines.getOrDefault(situation, Map.of()).getOrDefault(tone, List.of());
    }

    /**
     * One line for the situation in the tone with the slots filled, drawn with {@code random}. Lines with a slot the caller did
     * not supply are skipped. If the tone has nothing usable the warm lines are tried, then nothing.
     */
    public Optional<String> pick(Situation situation, Tone tone, Map<String, String> slots, Random random) {
        for (Tone t : new Tone[] {tone, Tone.WARM}) {
            List<String> usable = new ArrayList<>();
            for (String line : lines(situation, t)) {
                String filled = fill(line, slots);
                if (!SLOT.matcher(filled).find()) {
                    usable.add(filled);
                }
            }
            if (!usable.isEmpty()) {
                return Optional.of(usable.get(random.nextInt(usable.size())));
            }
        }
        return Optional.empty();
    }

    static String fill(String line, Map<String, String> slots) {
        String out = line;
        for (Map.Entry<String, String> slot : new HashMap<>(slots).entrySet()) {
            String value = slot.getValue();
            out = out.replace("{" + slot.getKey() + "}", value);
            String key = slot.getKey();
            if (!key.isEmpty() && !value.isEmpty()) { // {Name}: the same slot with a capital letter, for the start of a sentence
                out = out.replace("{" + Character.toUpperCase(key.charAt(0)) + key.substring(1) + "}",
                        Character.toUpperCase(value.charAt(0)) + value.substring(1));
            }
        }
        return out;
    }
}
