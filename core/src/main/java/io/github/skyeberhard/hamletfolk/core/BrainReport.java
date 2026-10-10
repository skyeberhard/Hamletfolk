package io.github.skyeberhard.hamletfolk.core;

import java.util.ArrayList;
import java.util.List;

/**
 * R9.2: the text of the brain module's tools: what {@code /settlement brain inspect} shows for one villager, and the summary
 * written to a file for a bug report. Pure: everything it prints is handed to it, already turned into plain text, so it
 * has no Minecraft types and can be tested.
 */
public final class BrainReport {
    private BrainReport() {
    }

    /** How much of a villager's brain a report shows, so one villager cannot fill a file. */
    static final int MAX_LINES = 60;

    /** One villager's brain as plain text: where it is, what it remembers, what is running, and what the plugin added. */
    public record VillagerView(String name, String activity, List<String> memories, List<String> running, List<String> added) {
    }

    /** Everything a report is made of. */
    public record Input(String pluginVersion, String serverVersion, String generated, BrainSwitch.State state, String reason,
            boolean configEnabled, boolean debug, int attached, BrainStats.Summary stats, List<String> missing,
            List<VillagerView> villagers, List<String> decisions, long decisionsTotal) {
    }

    /** The lines {@code /settlement brain inspect} prints for a villager. */
    public static List<String> inspect(VillagerView v) {
        List<String> out = new ArrayList<>();
        out.add(v.name() + ": " + v.activity());
        out.add("Added by Hamletfolk: " + (v.added().isEmpty() ? "nothing" : String.join(", ", v.added())));
        out.add("Running now: " + (v.running().isEmpty() ? "nothing" : String.join(", ", v.running())));
        out.add("Memories (" + v.memories().size() + "):");
        capped(v.memories(), "  ", out);
        return out;
    }

    private static void capped(List<String> items, String indent, List<String> out) {
        for (int i = 0; i < items.size() && i < MAX_LINES; i++) {
            out.add(indent + items.get(i));
        }
        if (items.size() > MAX_LINES) {
            out.add(indent + "... and " + (items.size() - MAX_LINES) + " more");
        }
    }

    /** The bug-report file: versions, the switch and why, the cost, anything the self-check finds missing, villagers, decisions. */
    public static String report(Input in) {
        List<String> out = new ArrayList<>();
        out.add("Hamletfolk brain module report");
        out.add("Written: " + in.generated());
        out.add("Plugin: " + in.pluginVersion() + "    Server: " + in.serverVersion());
        out.add("");
        out.add("State: " + in.state().name().toLowerCase(java.util.Locale.ROOT) + (in.reason().isEmpty() ? "" : " (" + in.reason() + ")"));
        out.add("brain.enabled in the config: " + in.configEnabled() + "    debug logging: " + (in.debug() ? "on" : "off"));
        out.add("Villagers with the added behaviours: " + in.attached());
        out.add("Cost: " + in.stats().describe());
        out.add("");
        out.add(in.missing().isEmpty() ? "Self-check: everything the module needs is in this server."
                : "Self-check: MISSING from this server:");
        in.missing().forEach(m -> out.add("  " + m));
        out.add("");
        out.add("Villagers (" + in.villagers().size() + " shown):");
        for (VillagerView v : in.villagers()) {
            inspect(v).forEach(line -> out.add("  " + line));
            out.add("");
        }
        out.add("Last decisions (" + in.decisions().size() + " of " + in.decisionsTotal() + "; turn on /settlement brain debug on to record them):");
        in.decisions().forEach(d -> out.add("  " + d));
        return redact(String.join(System.lineSeparator(), out)) + System.lineSeparator();
    }

    private static final java.util.regex.Pattern UUID_TEXT = java.util.regex.Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final java.util.regex.Pattern POSITION = java.util.regex.Pattern.compile("\\b([xyz])=-?\\d+(?:\\.\\d+)?");

    /**
     * Takes unique ids and positions out of text that is going to be shared: names stay (a bug report needs to say which
     * villager), but a player's id and where anything stands do not leave the server in a file.
     */
    public static String redact(String text) {
        String noIds = UUID_TEXT.matcher(text).replaceAll("<id>");
        return POSITION.matcher(noIds).replaceAll("$1=?");
    }

    private static final java.util.regex.Pattern PLAIN_OBJECT = java.util.regex.Pattern.compile("(?:[\\w$]+\\.)*([\\w$]+)@[0-9a-f]+");

    /**
     * Shortens a memory's value for one line. An object that prints as the default {@code ClassName@1a2b3c} (the hash tells
     * nobody anything) is shown as just its class's simple name.
     */
    public static String shorten(Object value, int max) {
        String text = String.valueOf(value).replace('\n', ' ');
        java.util.regex.Matcher plain = PLAIN_OBJECT.matcher(text);
        if (plain.matches()) {
            text = plain.group(1);
        }
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)) + "...";
    }
}
