package io.github.skyeberhard.hamletfolk.paper;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
 */
final class DashboardServer {
    private final HamletfolkPlugin plugin;
    private final SettlementService service;
    private final Gson gson = new Gson();

    private volatile String overview = "[]";
    private volatile Map<String, String> pages = Map.of();
    private volatile byte[] index = new byte[0];

    private HttpServer server;
    private ExecutorService threads;
    private BukkitTask refresher;
    private Set<String> hosts = Set.of();

    DashboardServer(HamletfolkPlugin plugin, SettlementService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /** Starts serving; false (and a line in the log) if the port cannot be bound. */
    boolean start(String bind, int port, int refreshSeconds) {
        try (InputStream in = plugin.getResource("dashboard.html")) {
            index = in == null ? "dashboard.html is missing from the plugin".getBytes(StandardCharsets.UTF_8) : in.readAllBytes();
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
        refresh(); // so the first request has something to show
        long ticks = Math.max(1, refreshSeconds) * 20L;
        refresher = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, ticks, ticks);
        plugin.getLogger().info("The village dashboard is at http://" + (bind.equals("0.0.0.0") ? "localhost" : bind) + ":" + port + "/");
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
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, String> detail = new HashMap<>();
            for (Settlement s : service.registry().settlements()) {
                int limit = service.treasuryLimit(s);
                rows.add(Dashboard.summary(s, limit));
                detail.put(s.id().toString(), gson.toJson(Dashboard.detail(s, limit)));
            }
            // the villages in most trouble first, then by name
            rows.sort(Comparator.comparingInt((Map<String, Object> r) -> -((Number) r.get("health")).intValue())
                    .thenComparing(r -> String.valueOf(r.get("name"))));
            overview = gson.toJson(rows);
            pages = detail;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not refresh the dashboard", e);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (!"GET".equals(exchange.getRequestMethod()) || host == null || !hosts.contains(host.toLowerCase(Locale.ROOT))) {
                send(exchange, 403, "text/plain; charset=utf-8", "Forbidden".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/index.html")) {
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

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
