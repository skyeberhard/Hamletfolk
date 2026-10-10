package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.BrainBehaviours;
import io.github.skyeberhard.hamletfolk.core.BrainFamily;
import io.github.skyeberhard.hamletfolk.core.BrainSelfCheck;
import io.github.skyeberhard.hamletfolk.core.BrainSwitch;
import io.github.skyeberhard.hamletfolk.core.Resident;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
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
 *
 * <p>R9.4: which behaviours run is {@link BrainBehaviours} (read from {@code brain.families} and {@code brain.behaviours} in the
 * config, changed live by command), and a resident has them only while a player is within {@code brain.watch-distance} blocks
 * of it; the others stay simulation-only. Switching a behaviour or its family off takes it off every villager at once.
 */
final class BrainService implements Listener {
    static final String PERMISSION = AdminCommand.PERMISSION;

    private final HamletfolkPlugin plugin;
    private final SettlementService settlements;
    private final BrainSwitch brainSwitch = new BrainSwitch();
    private final BrainBehaviours behaviours = new BrainBehaviours(BrainBehaviours.ALL);
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
        readConfig();
        Bukkit.getScheduler().runTaskTimer(plugin, this::everySecond, 20L, 20L);
        check();
        if (plugin.getConfig().getBoolean("brain.enabled", false)) {
            switchOn(Bukkit.getConsoleSender());
        }
    }

    /** R9.4: the watch distance, which families and behaviours are on, and each behaviour's budget. */
    private void readConfig() {
        var config = plugin.getConfig();
        behaviours.setWatchDistance(config.getInt("brain.watch-distance", BrainBehaviours.DEFAULT_WATCH));
        for (BrainFamily family : BrainFamily.values()) {
            behaviours.setFamily(family, config.getBoolean("brain.families." + family.key(), true));
        }
        for (BrainBehaviours.Spec spec : behaviours.specs()) {
            String at = "brain.behaviours." + spec.name();
            behaviours.setBehaviour(spec.name(), config.getBoolean(at + ".enabled", true));
            behaviours.setBudgetMicros(spec.name(), config.getInt(at + ".budget-microseconds", spec.budgetMicros()));
        }
        ConfigurationSection listed = config.getConfigurationSection("brain.behaviours");
        if (listed != null) {
            for (String name : listed.getKeys(false)) {
                if (behaviours.spec(name).isEmpty()) {
                    plugin.getLogger().warning("brain.behaviours." + name + " in the config: there is no such behaviour (there are: "
                            + String.join(", ", names()) + ").");
                }
            }
        }
    }

    private List<String> names() {
        return behaviours.specs().stream().map(BrainBehaviours.Spec::name).toList();
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
            } else {
                for (BrainBehaviours.Spec spec : behaviours.specs()) {
                    module.setBudget(spec.name(), behaviours.budgetMicros(spec.name()));
                    if (!module.known().contains(spec.name())) {
                        plugin.getLogger().warning("Brain behaviour " + spec.debugName() + " is listed but this build of the module cannot make it.");
                    }
                }
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
            who.sendMessage("Brain module on: behaviours added to " + count + " villagers near players (within "
                    + behaviours.watchDistance() + " blocks); the rest stay simulation-only until a player comes near.");
            plugin.getLogger().info("Brain module on (" + count + " villagers near players).");
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
            who.sendMessage("Behaviours on " + module.attached() + " villagers near players (within " + behaviours.watchDistance()
                    + " blocks). " + module.stats().summary().describe());
        }
        for (String line : behaviourLines()) {
            who.sendMessage("  " + line);
        }
        who.sendMessage("Debug logging " + (debug ? "on" : "off") + ". Config brain.enabled: " + plugin.getConfig().getBoolean("brain.enabled", false)
                + ". /settlement brain on|off switches it until the next restart, and so do behaviour <name> on|off and family <family> on|off;"
                + " inspect, debug and report help find out why a villager does what it does.");
    }

    /** R9.4: one line per behaviour: its debug name, on or why not, its budget, and (with the module loaded) where it is and its cost. */
    private List<String> behaviourLines() {
        List<String> out = new ArrayList<>();
        List<String> described = behaviours.describe();
        List<BrainBehaviours.Spec> specs = behaviours.specs();
        for (int i = 0; i < specs.size(); i++) {
            String name = specs.get(i).name();
            io.github.skyeberhard.hamletfolk.core.BrainStats.Summary cost = module == null ? null : module.stats(name).summary();
            out.add(described.get(i) + (module == null ? "" : "; on " + module.attached(name) + " villagers; "
                    + (cost.calls() == 0 ? "not run yet" : "cost " + cost.describe())));
        }
        return out;
    }

    // ----- R9.4: behaviours and families -----

    /** {@code /settlement brain behaviour <name> on|off}: until the next restart; off takes it off every villager now. */
    void behaviour(CommandSender who, String name, boolean on) {
        Optional<BrainBehaviours.Spec> spec = behaviours.spec(name.toLowerCase(java.util.Locale.ROOT));
        if (spec.isEmpty()) {
            who.sendMessage("There is no behaviour called " + name + ". There are: " + String.join(", ", names()) + ".");
            return;
        }
        behaviours.setBehaviour(spec.get().name(), on);
        applySwitches(List.of(spec.get()));
        String why = behaviours.whyNot(spec.get().name());
        who.sendMessage(spec.get().debugName() + " switched " + (on ? "on" : "off") + " until the next restart"
                + (on && !why.isEmpty() ? ", but it still does not run: " + why : "") + ".");
    }

    /** {@code /settlement brain family <family> on|off}: until the next restart; off takes all its behaviours off every villager now. */
    void family(CommandSender who, String key, boolean on) {
        Optional<BrainFamily> family = BrainFamily.fromKey(key);
        if (family.isEmpty()) {
            who.sendMessage("There is no family called " + key + ". There are: " + String.join(", ", families()) + ".");
            return;
        }
        List<BrainBehaviours.Spec> members = behaviours.setFamily(family.get(), on);
        applySwitches(members);
        who.sendMessage("The " + family.get().key() + " family switched " + (on ? "on" : "off") + " until the next restart ("
                + (members.isEmpty() ? "it has no behaviours yet" : members.size() + (members.size() == 1 ? " behaviour: " : " behaviours: ")
                        + String.join(", ", members.stream().map(BrainBehaviours.Spec::debugName).toList())) + ").");
    }

    static List<String> families() {
        return java.util.Arrays.stream(BrainFamily.values()).map(BrainFamily::key).toList();
    }

    List<String> behaviourNames() {
        return names();
    }

    /** Each behaviour that no longer runs comes off every villager now; each that does may act again, and goes on with the next pass. */
    private void applySwitches(List<BrainBehaviours.Spec> changed) {
        if (module == null) {
            return;
        }
        for (BrainBehaviours.Spec spec : changed) {
            if (behaviours.runs(spec.name())) {
                module.setRunning(spec.name(), true);
            } else {
                module.detachEverywhere(spec.name());
            }
        }
        if (brainSwitch.on()) {
            attachAll();
        }
    }

    /**
     * Called from inside a brain tick by the module: a behaviour went over its budget and already stands aside. Logged once;
     * it comes off every villager within a second, by the timer. Only logs and queues.
     */
    private void overBudget(String name, String figures) {
        Optional<String> message = behaviours.overBudget(name, figures);
        if (message.isPresent()) {
            pending.add(() -> module.detachEverywhere(name));
            plugin.getLogger().warning(message.get());
        }
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
                module == null ? 0 : module.attached(), stats, missing, shown, decisions.last(100), decisions.total(),
                withWatch(behaviourLines())));
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

    private List<String> withWatch(List<String> lines) {
        List<String> out = new ArrayList<>();
        out.add("watch distance: " + behaviours.watchDistance() + " blocks (kept to " + (behaviours.watchDistance() + BrainBehaviours.WATCH_MARGIN) + ")");
        out.addAll(lines);
        return out;
    }

    // ----- which villagers, and when -----

    /**
     * The residents' villagers that are loaded get the behaviours that run if a player is near them, and lose them when none
     * is (R9.4); it scales with residents, not with every villager in the worlds. A villager that has them but is no longer a
     * resident (it left, or its village is gone) loses them. Returns how many have them.
     */
    private int attachAll() {
        if (!brainSwitch.on() || module == null) {
            return 0;
        }
        Set<String> running = behaviours.running();
        Map<World, List<Location>> players = new HashMap<>();
        Set<UUID> residents = new HashSet<>();
        int count = 0;
        for (Settlement settlement : settlements.registry().settlements()) {
            for (Resident resident : List.copyOf(settlement.residents())) {
                if (Bukkit.getEntity(resident.id()) instanceof Villager villager) {
                    residents.add(resident.id());
                    count += fit(villager, running, players) ? 1 : 0;
                }
            }
        }
        for (UUID id : module.villagers()) {
            if (!residents.contains(id)) {
                if (Bukkit.getEntity(id) instanceof Villager villager) {
                    fit(villager, Set.of(), players);
                } else {
                    module.forget(id);
                }
            }
        }
        return count;
    }

    /**
     * Gives a resident's villager the behaviours that run if a player is near it (none otherwise, and none if it is not a
     * resident). True if it has them afterwards.
     */
    private boolean fit(Villager villager, Set<String> running, Map<World, List<Location>> players) {
        if (!brainSwitch.on() || module == null || !villager.isValid()) {
            return false;
        }
        UUID id = villager.getUniqueId();
        if (!isResident(id) && !module.has(id)) {
            return false; // not a resident and has nothing to take off: the module need not touch it
        }
        try {
            boolean near = isResident(id) && behaviours.keepWatching(module.has(id), nearestPlayer(villager, players));
            module.attach(villager, near ? running : Set.of());
            return module.has(id);
        } catch (RuntimeException | LinkageError e) {
            fault(id, e);
            return false;
        }
    }

    /** How far the nearest player in the villager's world is (infinity if there is none); each world's players are looked up once a pass. */
    private static double nearestPlayer(Villager villager, Map<World, List<Location>> players) {
        Location at = villager.getLocation();
        List<Location> there = players.computeIfAbsent(at.getWorld(),
                w -> w.getPlayers().stream().map(org.bukkit.entity.Player::getLocation).toList());
        double best = Double.POSITIVE_INFINITY;
        for (Location p : there) {
            best = Math.min(best, p.distanceSquared(at));
        }
        return Math.sqrt(best);
    }

    private boolean isResident(UUID id) {
        return settlements.registry().settlementOf(id).isPresent();
    }

    /** A villager coming into the world gets the behaviours (a tick later, once it is fully in). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(com.destroystokyo.paper.event.entity.EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Villager villager && brainSwitch.on()) {
            Bukkit.getScheduler().runTask(plugin, () -> fit(villager, behaviours.running(), new HashMap<>()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof Villager && module != null) {
            module.forget(event.getEntity().getUniqueId());
        }
    }

    /**
     * Once a second: anything a fault or a budget left to do; and the residents' villagers are fitted again, which re-adds the
     * behaviours to one whose brain the game rebuilt (it does on a change of profession), adds them to a villager that has just
     * become a resident or that a player has come near, and takes them off one no player is near any more (R9.4).
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
            loaded.onOverBudget(this::overBudget);
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
