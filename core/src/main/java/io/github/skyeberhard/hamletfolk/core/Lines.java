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
    private static final Pattern SLOT = Pattern.compile("\\{[a-z]+}");

    private final Map<Situation, Map<Tone, List<String>>> lines = new EnumMap<>(Situation.class);
    private final List<String> problems = new ArrayList<>();

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
        return out;
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
            out = out.replace("{" + slot.getKey() + "}", slot.getValue());
        }
        return out;
    }
}
