package io.github.skyeberhard.hamletfolk.paper;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.skyeberhard.hamletfolk.core.AdminAction;
import io.github.skyeberhard.hamletfolk.core.Dashboard;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import org.bukkit.scheduler.BukkitTask;

/**
 * R1.35: serves the village dashboard (a page and two JSON documents) from the machine the server runs on. Read only. The data is
 * gathered on the main thread every few seconds into text; the HTTP threads only ever hand out that text, never touch a village.
 * Bound to the loopback address unless the config says otherwise, and a request whose Host header is not one of the server's own names
 * is refused (so a web page elsewhere cannot read it through the browser).
 *
 * <p>R1.36: with {@code dashboard.allow-actions} on, {@code POST /api/action} runs one of the actions {@link AdminAction} lists. Every
 * request must carry the token this server put in the page it served (a fresh random one each start, compared in constant time), as a
 * header a page on another site cannot send without the browser asking first (and this server never says yes), with a JSON body of at
 * most {@link #MAX_BODY} bytes; one action at a time and at most one every {@link #ACTION_GAP_MS}. The action is checked in core, run on
 * the main thread, and written to the audit log.
 */
final class DashboardServer {
    private final HamletfolkPlugin plugin;
    private final SettlementService service;
    private final Gson gson = new Gson();

    private volatile String overview = "[]";
    private volatile Map<String, String> pages = Map.of();
    private volatile byte[] index = new byte[0];
    /** When (milliseconds) a page was last asked for: nothing is built while nobody is looking. */
    private volatile long lastRequest;
    private int passes;

    private HttpServer server;
    private ExecutorService threads;
    private BukkitTask refresher;
    private Set<String> hosts = Set.of();

    /** R1.36: the most a request body may hold. */
    static final int MAX_BODY = 4096;
    /** R1.36: the least time between two actions, so a stuck button cannot fire a hundred replans. */
    static final long ACTION_GAP_MS = 750;
    private final String token = newToken();
    private boolean allowActions;
    private DashboardActions actions;
    private final java.util.concurrent.locks.ReentrantLock actionLock = new java.util.concurrent.locks.ReentrantLock();
    /** A fresh nonce each start for the page's one script, so the page needs no 'unsafe-inline' for scripts. */
    private final String nonce = newToken();
    private long lastAction;

    DashboardServer(HamletfolkPlugin plugin, SettlementService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /** Starts serving; false (and a line in the log) if the port cannot be bound. */
    boolean start(String bind, int port, int refreshSeconds, boolean allowActions) {
        this.allowActions = allowActions && loopback(bind);
        if (allowActions && !this.allowActions) {
            // R1.36: the page token keeps other websites out, not other computers: anyone who can reach the port can read the page
            plugin.getLogger().warning("Dashboard actions are off: dashboard.bind is not the loopback address (127.0.0.1), and anyone who"
                    + " can reach the port could read the page and act. Keep bind at 127.0.0.1 and reach it through a tunnel (docs/DASHBOARD.md).");
        }
        this.actions = new DashboardActions(plugin, service);
        try (InputStream in = plugin.getResource("dashboard.html")) {
            index = in == null ? "dashboard.html is missing from the plugin".getBytes(StandardCharsets.UTF_8)
                    : new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("__HAMLETFOLK_TOKEN__", token)
                            .replace("__HAMLETFOLK_ACTIONS__", String.valueOf(this.allowActions))
                            .replace("<script>", "<script nonce=\"" + nonce + "\">").getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the dashboard page", e);
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(bind);
            server = HttpServer.create(new InetSocketAddress(address, port), 0);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "The dashboard could not start on " + bind + ":" + port + ": " + e.getMessage());
            return false;
        }
        hosts = new java.util.HashSet<>(List.of("localhost:" + port, "127.0.0.1:" + port, "[::1]:" + port, bind.toLowerCase(Locale.ROOT) + ":" + port));
        threads = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Hamletfolk dashboard");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(threads);
        server.createContext("/", this::handle);
        server.start();
        lastRequest = System.currentTimeMillis();
        refresh(); // so the first request has something to show
        long ticks = Math.max(1, refreshSeconds) * 20L;
        refresher = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> refresh(false), ticks, ticks);
        plugin.getLogger().info("The village dashboard is at http://" + (bind.equals("0.0.0.0") ? "localhost" : bind) + ":" + port + "/"
                + (this.allowActions ? " (actions on: every one is written to plugins/Hamletfolk/audit.log)" : " (read only)"));
        return true;
    }

    void stop() {
        if (refresher != null) {
            refresher.cancel();
        }
        if (server != null) {
            server.stop(0);
        }
        if (threads != null) {
            threads.shutdownNow();
        }
    }

    /** On the main thread: reads every village into text for the HTTP threads to hand out. */
    private void refresh() {
        refresh(false);
    }

    /** As above; {@code now} rebuilds everything at once (after an action, so the page shows what it did). */
    private void refresh(boolean now) {
        // nothing is built for a page nobody has asked for in the last two minutes (the page asks every five seconds while it is open)
        boolean first = passes == 0;
        if (!now && !first && System.currentTimeMillis() - lastRequest > 120_000) {
            return;
        }
        // the village pages are rebuilt every third pass (fifteen seconds); the overview every pass
        boolean pages3 = passes++ % 3 == 0 || now;
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, String> detail = pages3 ? new HashMap<>() : null;
            for (Settlement s : service.registry().settlements()) {
                int limit = service.treasuryLimit(s);
                Map<String, Object> summary = Dashboard.summary(s, limit);
                rows.add(summary);
                if (detail != null) {
                    detail.put(s.id().toString(), gson.toJson(Dashboard.detail(s, limit, summary)));
                }
            }
            // the villages in most trouble first, then by name
            rows.sort(Comparator.comparingInt((Map<String, Object> r) -> -((Number) r.get("health")).intValue())
                    .thenComparing(r -> String.valueOf(r.get("name"))));
            overview = gson.toJson(rows);
            if (detail != null) {
                pages = detail;
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not refresh the dashboard", e);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            exchange.setAttribute("hamletfolk.nonce", nonce);
            String host = exchange.getRequestHeaders().getFirst("Host");
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if (host == null || !hosts.contains(host.toLowerCase(Locale.ROOT))) {
                send(exchange, 403, "text/plain; charset=utf-8", "Forbidden".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if ("POST".equals(method) && path.equals("/api/action")) {
                action(exchange);
                return;
            }
            if (!"GET".equals(method)) {
                send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }
            lastRequest = System.currentTimeMillis();
            if (path.equals("/api/actions")) {
                Map<String, Object> catalogue = new java.util.LinkedHashMap<>();
                catalogue.put("enabled", allowActions);
                catalogue.put("actions", AdminAction.catalogue());
                catalogue.put("occupations", java.util.Arrays.stream(io.github.skyeberhard.hamletfolk.core.Occupation.values())
                        .map(o -> o.name().toLowerCase(Locale.ROOT)).toList());
                send(exchange, 200, "application/json; charset=utf-8", gson.toJson(catalogue).getBytes(StandardCharsets.UTF_8));
            } else if (path.equals("/") || path.equals("/index.html")) {
                send(exchange, 200, "text/html; charset=utf-8", index);
            } else if (path.equals("/api/villages")) {
                send(exchange, 200, "application/json; charset=utf-8", overview.getBytes(StandardCharsets.UTF_8));
            } else if (path.startsWith("/api/village/")) {
                String page = pages.get(path.substring("/api/village/".length()));
                if (page == null) {
                    send(exchange, 404, "text/plain; charset=utf-8", "No such village".getBytes(StandardCharsets.UTF_8));
                } else {
                    send(exchange, 200, "application/json; charset=utf-8", page.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                send(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            exchange.close();
        }
    }

    /** R1.36: one action, checked every way it can be before it is run on the main thread. */
    private void action(HttpExchange exchange) throws IOException {
        if (!allowActions) {
            actions.refused(actor(exchange), from(exchange), "actions are off");
            json(exchange, 403, false, "Actions are off: set dashboard.allow-actions: true in the config.");
            return;
        }
        String sent = exchange.getRequestHeaders().getFirst("X-Hamletfolk-Token");
        if (sent == null || !java.security.MessageDigest.isEqual(sent.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            actions.refused(actor(exchange), from(exchange), "no or wrong page token");
            json(exchange, 403, false, "The page is out of date (the server restarted): reload it.");
            return;
        }
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            actions.refused(actor(exchange), from(exchange), "not JSON");
            json(exchange, 415, false, "Send JSON.");
            return;
        }
        byte[] raw;
        try (InputStream in = exchange.getRequestBody()) {
            raw = in.readNBytes(MAX_BODY + 1);
        }
        if (raw.length > MAX_BODY) {
            actions.refused(actor(exchange), from(exchange), "body too large");
            json(exchange, 413, false, "Too large.");
            return;
        }
        Map<?, ?> body;
        try {
            body = gson.fromJson(new String(raw, StandardCharsets.UTF_8), Map.class);
        } catch (RuntimeException e) {
            actions.refused(actor(exchange), from(exchange), "not JSON");
            json(exchange, 400, false, "That is not JSON.");
            return;
        }
        // one at a time (a second request while one runs is turned away at once, not queued), and not too fast
        if (!actionLock.tryLock()) {
            actions.refused(actor(exchange), from(exchange), "another action is running");
            json(exchange, 429, false, "Another action is running: wait a moment.");
            return;
        }
        try {
            long now = System.currentTimeMillis();
            if (now - lastAction < ACTION_GAP_MS) {
                actions.refused(actor(exchange), from(exchange), "too fast");
                json(exchange, 429, false, "Too fast: wait a moment.");
                return;
            }
            lastAction = now;
            String actor = actor(exchange);
            String from = from(exchange);
            DashboardActions.Outcome outcome;
            try {
                outcome = plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                    AdminAction.Checked checked = AdminAction.check(body, service.registry());
                    if (!checked.ok()) {
                        actions.refused(actor, from, checked.error());
                        return new DashboardActions.Outcome(false, checked.error());
                    }
                    DashboardActions.Outcome done = actions.run(checked.request(), actor);
                    refresh(true);
                    return done;
                }).get(15, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                json(exchange, 504, false, "The server is busy; the action may still run. Check the audit log.");
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                json(exchange, 503, false, "Stopping.");
                return;
            } catch (java.util.concurrent.ExecutionException e) {
                plugin.getLogger().log(Level.WARNING, "A dashboard action failed", e.getCause());
                json(exchange, 500, false, "It failed. See the server log.");
                return;
            } catch (RuntimeException e) { // cancelled, or the plugin is being disabled
                json(exchange, 503, false, "The server is stopping.");
                return;
            }
            json(exchange, outcome.ok() ? 200 : 422, outcome.ok(), outcome.message());
        } finally {
            actionLock.unlock();
        }
    }

    /** Whether an address is this machine's own (127.0.0.1, ::1, localhost). */
    private static boolean loopback(String bind) {
        try {
            return InetAddress.getByName(bind).isLoopbackAddress();
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Who made a request, for the audit log. On the machine itself it is "dashboard" (R1.37 will take the signed-in user's address from
     * the login in front of it; nothing a browser sends is trusted for this until then).
     */
    private static String actor(HttpExchange exchange) {
        return "dashboard";
    }

    /** Where a request came from, for the audit log: the address that connected (the tunnel's, behind one). */
    private static String from(HttpExchange exchange) {
        return exchange.getRemoteAddress() == null ? "?" : exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private void json(HttpExchange exchange, int status, boolean ok, String message) throws IOException {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("ok", ok);
        out.put("message", message);
        send(exchange, status, "application/json; charset=utf-8", gson.toJson(out).getBytes(StandardCharsets.UTF_8));
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    /** The script nonce, which {@link #send} reads from the exchange (set by {@link #handle} before any reply). */
    private static String nonceFor(HttpExchange exchange) {
        Object n = exchange.getAttribute("hamletfolk.nonce");
        return n == null ? "none" : n.toString();
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; script-src 'nonce-" + nonceFor(exchange) + "'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                        + "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
