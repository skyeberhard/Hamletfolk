package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.AdminAction;
import io.github.skyeberhard.hamletfolk.core.Construction;
import io.github.skyeberhard.hamletfolk.core.HistoryEvent;
import io.github.skyeberhard.hamletfolk.core.Resident;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;

/**
 * R1.36: runs a dashboard action that {@link AdminAction#check} has already allowed. Always on the main thread (the server calls it through
 * the scheduler). Each runs the same code as its {@code /settlement admin} command, and every one, done or not, is written to
 * {@code plugins/Hamletfolk/audit.log}, to the console and, where it touches a village, to that village's history.
 */
final class DashboardActions {
    private final HamletfolkPlugin plugin;
    private final SettlementService service;
    private final Path auditFile;

    DashboardActions(HamletfolkPlugin plugin, SettlementService service) {
        this.plugin = plugin;
        this.service = service;
        this.auditFile = plugin.getDataFolder().toPath().resolve("audit.log");
    }

    /** What happened: whether it worked and what to tell the admin. */
    record Outcome(boolean ok, String message) {
    }

    /** Runs a checked request for {@code actor} (who it is, as the page knows them). Main thread only. */
    Outcome run(AdminAction.Request request, String actor) {
        Optional<Settlement> village = request.village() == null ? Optional.empty() : service.registry().get(request.village());
        Outcome outcome;
        try {
            outcome = village.isEmpty() && request.kind().needsVillage() ? new Outcome(false, "That village is gone.")
                    : perform(request, village.orElse(null), actor);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "A dashboard action failed: " + request.kind().wireName(), e);
            outcome = new Outcome(false, "It failed: " + e.getClass().getSimpleName() + ". See the server log.");
        }
        audit(request, village.map(Settlement::name).orElse(null), actor, outcome);
        if (outcome.ok() && village.isPresent() && request.kind() != AdminAction.Kind.JOB) { // (a job writes its own line)
            Settlement s = village.get();
            s.record(s.lastSimulatedDay(), HistoryEvent.Kind.MILESTONE, "An admin (" + actor + ") chose to "
                    + request.kind().label().toLowerCase(Locale.ROOT) + ".");
        }
        if (outcome.ok()) {
            plugin.requestSave();
        }
        return outcome;
    }

    private Outcome perform(AdminAction.Request request, Settlement s, String actor) {
        return switch (request.kind()) {
            case SAVE -> plugin.saveNow() ? new Outcome(true, "Saved " + service.registry().settlements().size() + " villages.")
                    : new Outcome(false, "The save failed. See the server log.");
            case REPLAN -> s.isAbandoned() || s.population() == 0
                    ? new Outcome(false, s.name() + " has nobody living in it, so there is nothing to plan for.")
                    : new Outcome(true, service.replan(s));
            case WARP -> {
                long before = s.lastSimulatedDay();
                int ran = service.warp(s, request.number());
                yield ran == 0 ? new Outcome(false, s.name() + " is abandoned or in a world that is not simulated.")
                        : new Outcome(true, s.name() + " ran " + ran + (ran == 1 ? " day" : " days") + " ahead (day " + before + " to "
                                + s.lastSimulatedDay() + ").");
            }
            case FASTFORWARD -> {
                service.setFastForward(s, request.number());
                yield new Outcome(true, request.number() <= 1 ? s.name() + " is back to normal speed."
                        : s.name() + " now runs at " + request.number() + "x.");
            }
            case RESETTLE -> {
                Optional<String> refusal = io.github.skyeberhard.hamletfolk.core.Resettlement.refusal(s);
                if (refusal.isPresent()) {
                    yield new Outcome(false, refusal.get());
                }
                String said = service.resettle(s, request.number(), line -> plugin.getLogger().info("[dashboard] " + line));
                yield new Outcome(said.contains("on the way to"), said); // (the service's other answers are refusals)
            }
            case CANCEL_PROJECT -> {
                var open = s.openProject();
                if (open.isEmpty()) {
                    yield new Outcome(false, s.name() + " has no open building project.");
                }
                Construction.cancel(s, open.get(), s.lastSimulatedDay(), "an admin stopped it");
                yield new Outcome(true, "Gave up the " + open.get().type().label().toLowerCase(Locale.ROOT) + " in " + s.name() + ".");
            }
            case UNSTICK -> {
                int freed = service.unstick(s);
                yield new Outcome(true, freed == 0 ? "Nobody in " + s.name() + " is walled in (only loaded villagers are checked)."
                        : "Freed " + freed + (freed == 1 ? " villager" : " villagers") + " of " + s.name() + ".");
            }
            case REGRADE -> {
                Optional<String> why = Construction.reopenPalisade(s, s.lastSimulatedDay());
                yield why.map(w -> new Outcome(false, w)).orElse(new Outcome(true, s.name() + " will mend its palisade."));
            }
            case RENAME -> {
                String old = s.name();
                try {
                    service.registry().rename(s, request.text(), s.lastSimulatedDay());
                } catch (IllegalArgumentException e) {
                    yield new Outcome(false, e.getMessage());
                }
                yield new Outcome(true, old + " is now " + s.name() + ".");
            }
            case JOB -> {
                Resident r = s.resident(request.resident()).orElseThrow();
                yield new Outcome(true, AdminAction.setJob(s, r, request.occupation(), s.lastSimulatedDay(), "an admin (" + actor + ")"));
            }
            case UNPIN -> {
                Resident r = s.resident(request.resident()).orElseThrow();
                yield new Outcome(true, AdminAction.unpin(s, r, s.lastSimulatedDay()));
            }
        };
    }

    /** Refusals written in a minute before the rest are only counted (a page can send them as fast as it likes). */
    static final int REFUSALS_PER_MINUTE = 20;
    /** Largest the audit file grows before it is moved to audit.log.1 (the one before that is dropped). */
    static final long MAX_AUDIT_BYTES = 5L * 1024 * 1024;
    private long refusalMinute;
    private int refusalsThisMinute;
    private int refusalsUnwritten;

    /**
     * A request refused before it ran: written down too, so an attempt from somewhere it should not come from is seen. At most
     * {@link #REFUSALS_PER_MINUTE} a minute are written; the rest are counted and the count written with the next one that is.
     */
    synchronized void refused(String actor, String from, String reason) {
        long minute = System.currentTimeMillis() / 60_000;
        if (minute != refusalMinute) {
            refusalMinute = minute;
            refusalsThisMinute = 0;
        }
        if (++refusalsThisMinute > REFUSALS_PER_MINUTE) {
            refusalsUnwritten++;
            return;
        }
        String more = refusalsUnwritten == 0 ? "" : " (and " + refusalsUnwritten + " more refused before this, not written)";
        refusalsUnwritten = 0;
        String line = AdminAction.rejectedLine(Instant.now(), actor, from, reason + more);
        plugin.getLogger().warning("[dashboard] " + line);
        write(line);
    }

    /** One line in the audit file and the console. A failure to write the file is logged, never thrown. */
    void audit(AdminAction.Request request, String village, String actor, Outcome outcome) {
        String line = AdminAction.auditLine(Instant.now(), actor, request, village, outcome.ok(), outcome.message());
        plugin.getLogger().info("[dashboard] " + line);
        write(line);
    }

    private synchronized void write(String line) {
        try {
            Files.createDirectories(auditFile.getParent());
            if (Files.exists(auditFile) && Files.size(auditFile) > MAX_AUDIT_BYTES) {
                Files.move(auditFile, auditFile.resolveSibling("audit.log.1"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(auditFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write the dashboard audit log", e);
        }
    }
}
