package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.BrainSelfCheck;
import io.github.skyeberhard.hamletfolk.core.BrainSwitch;
import io.github.skyeberhard.hamletfolk.core.Resident;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * R9.1: the brain module's switch, self-check and lifecycle, in the plugin. It never touches an internal class itself: it
 * checks by name that everything the module needs is in the running server ({@link BrainSelfCheck}), then loads the module by
 * name and talks to it through {@link BrainModule}. Off (or a failed check, or a missing module) leaves the plugin exactly as
 * it is without the module. The behaviours go only on villagers that are residents of a village.
 */
final class BrainService implements Listener {
    static final String PERMISSION = AdminCommand.PERMISSION;

    private final HamletfolkPlugin plugin;
    private final SettlementService settlements;
    private final BrainSwitch brainSwitch = new BrainSwitch();
    private final io.github.skyeberhard.hamletfolk.core.DecisionLog decisions = new io.github.skyeberhard.hamletfolk.core.DecisionLog(200);
    private boolean debug;
    private BrainModule module;
    private boolean checked;
    /** What a fault left to do, run by the once-a-second timer (never from inside a brain tick). */
    private final Deque<Runnable> pending = new ArrayDeque<>();

    BrainService(HamletfolkPlugin plugin, SettlementService settlements) {
        this.plugin = plugin;
        this.settlements = settlements;
    }

    /**
     * At startup: the self-check runs whatever the config says (so a server the module cannot run on says so in the log at
     * once), then the module goes on if {@code brain.enabled} is true (it is off by default until it has been played).
     */
    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::everySecond, 20L, 20L);
        check();
        if (plugin.getConfig().getBoolean("brain.enabled", false)) {
            switchOn(Bukkit.getConsoleSender());
        }
    }

    /** Once: everything the module needs is in the running server, and the module itself is in the build and loads. */
    private void check() {
        if (checked) {
            return;
        }
        checked = true;
        List<String> missing = BrainSelfCheck.missing(BrainSelfCheck.REQUIRED, new ServerProbe(plugin.getClass().getClassLoader()));
        if (missing.isEmpty()) {
            module = load();
            if (module == null) {
                missing = List.of(BrainModule.IMPLEMENTATION + " (the module is not in this build, or would not load)");
            }
        }
        if (!missing.isEmpty()) {
            brainSwitch.selfCheckFailed(missing);
            plugin.getLogger().warning("Brain module stays off: " + brainSwitch.reason()
                    + ". The rest of Hamletfolk runs as usual.");
        }
    }

    /** At shutdown: take everything off, so the server's villagers are saved exactly as vanilla. */
    void stop() {
        if (module != null) {
            module.setActive(false);
            module.detachAll();
        }
    }

    // ----- the switch -----

    void switchOn(CommandSender who) {
        check();
        if (brainSwitch.state() == BrainSwitch.State.UNAVAILABLE) {
            who.sendMessage("The brain module cannot run on this server: " + brainSwitch.reason() + ".");
            return;
        }
        if (brainSwitch.requestOn() == BrainSwitch.Action.ATTACH_ALL) {
            module.setActive(true);
            int count = attachAll();
            who.sendMessage("Brain module on: behaviours added to " + count + " villagers.");
            plugin.getLogger().info("Brain module on (" + count + " villagers).");
        } else {
            who.sendMessage("The brain module is already on.");
        }
    }

    void switchOff(CommandSender who) {
        if (brainSwitch.requestOff() == BrainSwitch.Action.DETACH_ALL) {
            int count = module.attached();
            module.setActive(false);
            module.detachAll();
            who.sendMessage("Brain module off: " + count + " villagers are back to vanilla.");
            plugin.getLogger().info("Brain module off.");
        } else {
            who.sendMessage("The brain module is already off" + (brainSwitch.state() == BrainSwitch.State.UNAVAILABLE
                    ? " (" + brainSwitch.reason() + ")" : "") + ".");
        }
    }

    void status(CommandSender who) {
        who.sendMessage("Brain module: " + brainSwitch.state().name().toLowerCase(java.util.Locale.ROOT)
                + (brainSwitch.reason().isEmpty() ? "" : " (" + brainSwitch.reason() + ")") + ".");
        if (module != null) {
            who.sendMessage("Behaviours on " + module.attached() + " villagers. " + module.stats().summary().describe());
        }
        who.sendMessage("Debug logging " + (debug ? "on" : "off") + ". Config brain.enabled: " + plugin.getConfig().getBoolean("brain.enabled", false)
                + ". /settlement brain on|off switches it until the next restart; inspect, debug and report help find out why a villager does what it does.");
    }

    // ----- R9.2: looking inside -----

    /** {@code /settlement brain inspect}: the villager you are looking at, as the brain sees it. */
    void inspect(CommandSender who) {
        if (!(who instanceof org.bukkit.entity.Player player)) {
            who.sendMessage("Look at a villager in game to use this.");
            return;
        }
        if (module == null) {
            who.sendMessage("The brain module is not available" + (brainSwitch.reason().isEmpty() ? "" : " (" + brainSwitch.reason() + ")") + ".");
            return;
        }
        if (!(player.getTargetEntity(8) instanceof Villager villager)) {
            who.sendMessage("Look at a villager (within 8 blocks) first.");
            return;
        }
        for (String line : io.github.skyeberhard.hamletfolk.core.BrainReport.inspect(view(villager))) {
            who.sendMessage(line);
        }
    }

    private io.github.skyeberhard.hamletfolk.core.BrainReport.VillagerView view(Villager villager) {
        BrainModule.Snapshot s = module.inspect(villager);
        return new io.github.skyeberhard.hamletfolk.core.BrainReport.VillagerView(describe(villager.getUniqueId()), s.activity(),
                s.memories(), s.running(), s.added());
    }

    /** {@code /settlement brain debug on|off}: log each decision the added behaviours make, and keep the last 200 for the report. */
    void debug(CommandSender who, boolean on) {
        debug = on;
        if (module != null) {
            module.setDebug(on);
        }
        who.sendMessage("Brain debug logging " + (on ? "on: each decision goes to the log and the last 200 are kept for /settlement brain report."
                : "off.") + (module == null ? " (The module is not available, so there is nothing to log.)" : ""));
    }

    /** Called from inside a brain tick by the module (debug on only): log it, keep it. Only logs. */
    private void decided(UUID villager, String text) {
        String who;
        try {
            who = describe(villager);
        } catch (RuntimeException e) {
            who = "villager " + villager;
        }
        decisions.add(Bukkit.getCurrentTick(), who, text); // (the server's tick count, as the timing uses)
        plugin.getLogger().info("[brain] " + who + ": " + text);
    }

    /** {@code /settlement brain report}: writes plugins/Hamletfolk/brain-report.txt for a bug report. */
    void report(CommandSender who) {
        List<String> missing = BrainSelfCheck.missing(BrainSelfCheck.REQUIRED, new ServerProbe(plugin.getClass().getClassLoader()));
        List<io.github.skyeberhard.hamletfolk.core.BrainReport.VillagerView> shown = new java.util.ArrayList<>();
        if (module != null) {
            for (Settlement settlement : settlements.registry().settlements()) {
                for (Resident resident : List.copyOf(settlement.residents())) {
                    if (shown.size() < REPORT_VILLAGERS && Bukkit.getEntity(resident.id()) instanceof Villager villager
                            && villager.isValid()) {
                        shown.add(view(villager));
                    }
                }
            }
        }
        io.github.skyeberhard.hamletfolk.core.BrainStats.Summary stats = module == null
                ? new io.github.skyeberhard.hamletfolk.core.BrainStats().summary() : module.stats().summary();
        String text = io.github.skyeberhard.hamletfolk.core.BrainReport.report(new io.github.skyeberhard.hamletfolk.core.BrainReport.Input(
                plugin.getPluginMeta().getVersion(), Bukkit.getVersion(),
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                brainSwitch.state(), brainSwitch.reason(), plugin.getConfig().getBoolean("brain.enabled", false), debug,
                module == null ? 0 : module.attached(), stats, missing, shown, decisions.last(100), decisions.total()));
        java.nio.file.Path file = plugin.getDataFolder().toPath().resolve("brain-report.txt");
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, text, java.nio.charset.StandardCharsets.UTF_8);
            who.sendMessage("Wrote " + file + " (" + shown.size() + " villagers, " + decisions.size() + " decisions). Attach it to a bug report: "
                    + "it has villagers' and players' names, but the unique ids and the positions have been taken out.");
        } catch (java.io.IOException e) {
            who.sendMessage("Could not write the report: " + e.getMessage());
        }
    }

    private static final int REPORT_VILLAGERS = 10;

    // ----- which villagers, and when -----

    /** The residents' villagers that are loaded get the behaviours (it scales with residents, not with every villager in the worlds). */
    private int attachAll() {
        int count = 0;
        for (Settlement settlement : settlements.registry().settlements()) {
            for (Resident resident : List.copyOf(settlement.residents())) {
                if (Bukkit.getEntity(resident.id()) instanceof Villager villager && attachIfResident(villager)) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean attachIfResident(Villager villager) {
        if (!brainSwitch.on() || module == null || !villager.isValid() || !isResident(villager.getUniqueId())) {
            return false;
        }
        try {
            module.attach(villager);
            return true;
        } catch (RuntimeException | LinkageError e) {
            fault(villager.getUniqueId(), e);
            return false;
        }
    }

    private boolean isResident(UUID id) {
        return settlements.registry().settlementOf(id).isPresent();
    }

    /** A villager coming into the world gets the behaviours (a tick later, once it is fully in). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(com.destroystokyo.paper.event.entity.EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Villager villager && brainSwitch.on()) {
            Bukkit.getScheduler().runTask(plugin, () -> attachIfResident(villager));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof Villager && module != null) {
            module.forget(event.getEntity().getUniqueId());
        }
    }

    /**
     * Once a second: anything a fault left to do; and the residents' villagers are attached again, which re-adds the behaviours
     * to one whose brain the game rebuilt (it does on a change of profession) and adds them to a villager that has just become a
     * resident.
     */
    private void everySecond() {
        while (!pending.isEmpty()) {
            pending.poll().run();
        }
        if (brainSwitch.on()) {
            attachAll();
        }
    }

    // ----- faults -----

    /**
     * A behaviour threw: off at once (the behaviours stand aside now and are taken off within a second, by the timer, never from
     * inside the brain tick this may be called from), logged once. Nothing here may throw.
     */
    private void fault(UUID villager, Throwable error) {
        String who;
        try {
            who = describe(villager);
        } catch (RuntimeException e) {
            who = "villager " + villager;
        }
        Optional<String> message = brainSwitch.fault(who, error.getClass().getSimpleName() + ": " + error.getMessage());
        if (message.isEmpty()) {
            return;
        }
        if (module != null) {
            module.setActive(false);
            pending.add(module::detachAll); // not now: this may be running inside that villager's brain tick
        }
        plugin.getLogger().log(Level.WARNING, message.get(), error);
    }

    private String describe(UUID id) {
        Optional<Settlement> home = settlements.registry().settlementOf(id);
        Optional<Resident> resident = home.flatMap(s -> s.resident(id));
        return resident.map(r -> r.fullName() + " (" + r.occupation().title() + " of " + home.get().name() + ", " + id + ")")
                .orElse("villager " + id);
    }

    private BrainModule load() {
        try {
            Class<?> type = Class.forName(BrainModule.IMPLEMENTATION, true, plugin.getClass().getClassLoader());
            BrainModule loaded = (BrainModule) type.getDeclaredConstructor().newInstance();
            loaded.onFault(this::fault);
            loaded.onDecision(this::decided);
            return loaded;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING, "Could not load the brain module", e);
            return null;
        }
    }

    /**
     * Looks up the module's requirements in the running server by name, without linking anything: classes are loaded without
     * being initialised, and a member must match exactly (parameter types and the type it returns).
     */
    static final class ServerProbe implements BrainSelfCheck.Probe {
        private final ClassLoader loader;

        ServerProbe(ClassLoader loader) {
            this.loader = loader;
        }

        private Class<?> type(String name) throws ClassNotFoundException {
            return switch (name) {
                case "int" -> int.class;
                case "long" -> long.class;
                case "boolean" -> boolean.class;
                case "double" -> double.class;
                case "float" -> float.class;
                case "void" -> void.class;
                default -> Class.forName(name, false, loader);
            };
        }

        private Class<?>[] types(List<String> names) throws ClassNotFoundException {
            Class<?>[] out = new Class<?>[names.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = type(names.get(i));
            }
            return out;
        }

        @Override
        public boolean hasClass(String name) {
            try {
                type(name);
                return true;
            } catch (ClassNotFoundException | LinkageError e) {
                return false;
            }
        }

        /** Declared on the class or a superclass (not an interface: the module uses no interface constants). */
        @Override
        public boolean hasField(String owner, String name, String typeName) {
            try {
                for (Class<?> c = type(owner); c != null; c = c.getSuperclass()) {
                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        if (f.getName().equals(name)) {
                            return f.getType().getName().equals(typeName);
                        }
                    }
                }
            } catch (ClassNotFoundException | LinkageError e) {
                return false;
            }
            return false;
        }

        @Override
        public boolean hasConstructor(String owner, List<String> parameters) {
            try {
                type(owner).getDeclaredConstructor(types(parameters));
                return true;
            } catch (ReflectiveOperationException | LinkageError e) {
                return false;
            }
        }

        /**
         * Public ones (inherited, or an interface's default) and any declared on the class itself or, unless private, on a
         * superclass; the return type must match too (so the right one of several bridge methods is the one found).
         */
        @Override
        public boolean hasMethod(String owner, String name, List<String> parameters, String returns) {
            try {
                Class<?> start = type(owner);
                Class<?>[] wanted = types(parameters);
                for (Method m : start.getMethods()) {
                    if (matches(m, name, wanted, returns)) {
                        return true;
                    }
                }
                for (Class<?> c = start; c != null; c = c.getSuperclass()) {
                    for (Method m : c.getDeclaredMethods()) {
                        if ((c == start || !Modifier.isPrivate(m.getModifiers())) && matches(m, name, wanted, returns)) {
                            return true;
                        }
                    }
                }
            } catch (ClassNotFoundException | LinkageError e) {
                return false;
            }
            return false;
        }

        private static boolean matches(Method m, String name, Class<?>[] parameters, String returns) {
            return m.getName().equals(name) && java.util.Arrays.equals(m.getParameterTypes(), parameters)
                    && m.getReturnType().getName().equals(returns);
        }
    }
}
