package io.github.skyeberhard.hamletfolk.core;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * R1.10: which worlds settlements are tracked in. An empty allow-list means every world;
 * otherwise a world must match it. A world matching the deny-list is always out. Patterns
 * match the whole world name, ignoring case, and {@code *} stands for any run of characters.
 */
public final class WorldFilter {
    private final List<Pattern> allow;
    private final List<Pattern> deny;

    public WorldFilter(List<String> allow, List<String> deny) {
        this.allow = compile(allow);
        this.deny = compile(deny);
    }

    public static WorldFilter everything() {
        return new WorldFilter(List.of(), List.of());
    }

    public boolean accepts(String world) {
        if (world == null) {
            return false;
        }
        if (matchesAny(deny, world)) {
            return false;
        }
        return allow.isEmpty() || matchesAny(allow, world);
    }

    private static boolean matchesAny(List<Pattern> patterns, String world) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(world).matches()) {
                return true;
            }
        }
        return false;
    }

    private static List<Pattern> compile(List<String> globs) {
        return globs.stream()
                .map(String::strip)
                .filter(glob -> !glob.isEmpty())
                .map(WorldFilter::compileGlob)
                .toList();
    }

    private static Pattern compileGlob(String glob) {
        StringBuilder regex = new StringBuilder();
        for (String part : glob.split(Pattern.quote("*"), -1)) {
            if (regex.length() > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part.toLowerCase(Locale.ROOT)));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }
}
