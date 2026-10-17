package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.HistoryEvent;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.SettlementInspector;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What happens in the villages, written to the server console as it happens: each new line of a village's history, and every few
 * minutes a one-line summary of every village. For a server run from a command prompt, where nobody is in game to read the history
 * book. {@code console.events} in the config is {@code all}, {@code important} (births, deaths, arrivals, famines, raids,
 * shortages) or {@code off}; {@code console.digest-minutes} is how often the summaries come (0 for never).
 */
final class ConsoleFeed {
    /** Most lines one village writes in one pass (a fast-forwarded village makes many), then a count of the rest. */
    private static final int MAX_LINES_PER_PASS = 12;

    private final HamletfolkPlugin plugin;
    private final SettlementService service;
    private final Mode mode;
    /** The newest history line already written for each village. Not saved: a restart writes nothing old. */
    private final Map<UUID, HistoryEvent> seen = new HashMap<>();

    enum Mode {
        ALL, IMPORTANT, OFF;

        static Mode of(String text) {
            return switch (text == null ? "all" : text.toLowerCase(Locale.ROOT)) {
                case "off", "false", "none" -> OFF;
                case "important", "major" -> IMPORTANT;
                default -> ALL;
            };
        }
    }

    ConsoleFeed(HamletfolkPlugin plugin, SettlementService service, Mode mode) {
        this.plugin = plugin;
        this.service = service;
        this.mode = mode;
    }

    /** Writes the history lines that are new since the last pass. */
    void events() {
        if (mode == Mode.OFF) {
            return;
        }
        for (Settlement settlement : service.registry().settlements()) {
            List<HistoryEvent> history = settlement.history();
            if (history.isEmpty()) {
                continue;
            }
            HistoryEvent latest = history.get(history.size() - 1);
            HistoryEvent before = seen.put(settlement.id(), latest);
            if (before == null || before.equals(latest)) {
                continue; // the first look only notes where the history stands
            }
            List<HistoryEvent> fresh = io.github.skyeberhard.hamletfolk.core.HistoryFeed.newSince(history, before);
            int written = 0;
            int more = 0;
            for (HistoryEvent event : fresh) {
                if (!wanted(event)) {
                    continue;
                }
                if (written++ < MAX_LINES_PER_PASS) {
                    plugin.getLogger().info(settlement.name() + " (day " + event.day() + "): " + event.text());
                } else {
                    more++;
                }
            }
            if (more > 0) {
                plugin.getLogger().info(settlement.name() + ": and " + more + " more.");
            }
        }
    }

    private boolean wanted(HistoryEvent event) {
        if (event.kind() == HistoryEvent.Kind.DONATION) {
            return false; // merged into "N donations this week": a player is there to see it
        }
        if (mode == Mode.ALL) {
            return true;
        }
        return switch (event.kind()) {
            case FOUNDED, ARRIVAL, DEPARTURE, BIRTH, DEATH, FAMINE, RECOVERY, RAID, SHORTAGE, ABANDONED, CURE, MARRIAGE -> true;
            default -> false;
        };
    }

    /** One line for every village. */
    void digest() {
        for (Settlement settlement : service.registry().settlements()) {
            plugin.getLogger().info(SettlementInspector.digest(settlement));
        }
    }
}
