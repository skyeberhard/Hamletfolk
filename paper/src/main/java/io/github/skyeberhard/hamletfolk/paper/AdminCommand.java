package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.SettlementInspector;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * R1.4: /settlement admin list|inspect|rename|save. Plain-text output, and no location
 * needed when a settlement is named, so it works from the server console.
 */
final class AdminCommand {
    static final String PERMISSION = "hamletfolk.admin";
    private static final List<String> SUBCOMMANDS = List.of("list", "inspect", "rename", "save", "ignore", "capture", "build", "cancelproject", "found", "replan", "warp", "job", "unstick", "fastforward", "dialogue");
    private static final String USAGE = "Usage: /settlement admin list | inspect [name|id] | rename <name|id> <new name> | save | ignore (look at a villager) | capture ... | build ... | cancelproject [name] | found [name] [residents] | replan [name] | warp [name] <days> | job <occupation>|unpin (look at a villager) | unstick [name] | fastforward [name|all] [speed|stop] | dialogue (reload the lines in plugins/Hamletfolk/dialogue/)";

    private final SettlementService service;
    private final TemplateCommand templates;

    AdminCommand(SettlementService service) {
        this.service = service;
        this.templates = new TemplateCommand(service);
    }

    /** R4.30: /settlement admin dialogue reads the dialogue folder again, so lines can be changed without a restart. */
    private void dialogue(CommandSender sender) {
        java.util.List<String> problems = service.plugin().loadDialogue();
        sender.sendMessage("Dialogue reloaded from plugins/Hamletfolk/dialogue/ over the built-in lines"
                + (problems.isEmpty() ? "." : ", with " + problems.size() + " problem(s):"));
        problems.stream().limit(10).forEach(p -> sender.sendMessage(" - " + p));
    }

    /** Args are the full command args, starting with "admin". */
    void run(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("You don't have permission to do that.");
            return;
        }
        String sub = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(sender);
            case "inspect" -> inspect(sender, args);
            case "rename" -> rename(sender, args);
            case "save" -> save(sender);
            case "ignore" -> ignore(sender);
            case "capture" -> templates.capture(sender, args);
            case "build" -> templates.build(sender, args);
            case "cancelproject" -> cancelProject(sender, args);
            case "found" -> found(sender, args);
            case "replan" -> replan(sender, args);
            case "warp" -> warp(sender, args);
            case "job" -> job(sender, args);
            case "unstick" -> unstick(sender, args);
            case "fastforward", "ff" -> fastForward(sender, args);
            case "dialogue" -> dialogue(sender);
            default -> sender.sendMessage(USAGE);
        }
    }

    List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("inspect") || args[1].equalsIgnoreCase("rename"))) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return service.registry().settlements().stream().map(Settlement::name)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }

    /** R1.30: toggles leaving the villager you are looking at alone, whatever village it is in. */
    private void ignore(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Look at a villager in game to use this.");
            return;
        }
        if (!(player.getTargetEntity(6) instanceof org.bukkit.entity.Villager villager)) {
            sender.sendMessage("Look at a villager (within 6 blocks) first.");
            return;
        }
        sender.sendMessage(service.toggleIgnoreTag(villager)
                ? "That villager is now left alone: not in any village, not re-priced, not moved."
                : "That villager is no longer exempt (it may still be inside an [Exempt] sign's area).");
    }

    /** R8.10: /settlement admin found [name] [residents]: a new village where the admin stands. */
    private void found(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Stand where the village should go: this needs a player in game.");
            return;
        }
        int to = args.length;
        int founders = 6;
        // A last word of digits is the number of founders, so a name that ends in a number needs a count after it.
        if (to > 2 && !args[to - 1].isEmpty() && args[to - 1].chars().allMatch(Character::isDigit)) {
            founders = Math.max(1, Math.min(SettlementService.MAX_FOUNDERS, Integer.parseInt(args[to - 1].length() > 3 ? "999" : args[to - 1])));
            to--;
        }
        String name = to > 2 ? words(args, 2, to) : null;
        SettlementService.Founding result = service.foundVillage(player.getLocation(), name, founders);
        if (result.problem() != null) {
            sender.sendMessage(result.problem());
            return;
        }
        if (result.nameNote() != null) {
            sender.sendMessage("Could not use that name (" + result.nameNote() + "), so it has a generated one.");
        }
        sender.sendMessage("Founded " + result.settlement().name() + " here with " + result.settlement().population()
                + " villagers, food, wood and stone."
                + (result.settlement().plan() == null ? " Its layout will be planned once the ground around it is loaded."
                        : " Its layout is planned: /settlement lots shows it. The builders start when it needs something."));
    }

    /**
     * R4.22: /settlement admin warp [name] &lt;days&gt;: runs a village that many days ahead at once. The last word is the
     * number of days; any words before it name the village (from the console a name is needed; in game, the one you are in).
     */
    private void warp(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[args.length - 1].chars().allMatch(Character::isDigit) || args[args.length - 1].isEmpty()) {
            sender.sendMessage("Usage: /settlement admin warp [name] <days>   (1 to " + SettlementService.MAX_WARP_DAYS + ")");
            return;
        }
        int days = args[args.length - 1].length() > 4 ? SettlementService.MAX_WARP_DAYS : Integer.parseInt(args[args.length - 1]);
        if (days < 1) {
            sender.sendMessage("Give at least one day: /settlement admin warp [name] <days>   (1 to " + SettlementService.MAX_WARP_DAYS + ")");
            return;
        }
        Optional<Settlement> target = args.length >= 4 ? lookup(sender, words(args, 2, args.length - 1)) : here(sender);
        if (target.isEmpty()) {
            return;
        }
        Settlement settlement = target.get();
        long before = settlement.lastSimulatedDay();
        int ran = service.warp(settlement, days);
        if (ran == 0) {
            sender.sendMessage(settlement.name() + " is abandoned or in a world that is not simulated, so there is nothing to run.");
            return;
        }
        sender.sendMessage(settlement.name() + " ran " + ran + (ran == 1 ? " day" : " days") + " ahead (day " + before + " to day "
                + settlement.lastSimulatedDay() + "). Its own clock now runs that far ahead of the world's, and keeps pace with it. "
                + settlement.population() + " residents.");
    }

    /**
     * R1.31: /settlement admin job &lt;occupation&gt; | unpin, looking at a villager: sets its trade and pins it, so the
     * simulation leaves it alone; {@code unpin} hands the resident back to the simulation.
     */
    private void job(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Look at a villager in game to use this.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /settlement admin job <occupation>|unpin, looking at a villager. Occupations: "
                    + String.join(", ", Arrays.stream(io.github.skyeberhard.hamletfolk.core.Occupation.values())
                            .map(o -> o.name().toLowerCase(Locale.ROOT)).toList()));
            return;
        }
        if (!(player.getTargetEntity(6) instanceof org.bukkit.entity.Villager villager)) {
            sender.sendMessage("Look at a villager (within 6 blocks) first.");
            return;
        }
        var resident = service.track(villager);
        Optional<Settlement> home = resident == null ? Optional.empty() : service.registry().settlementOf(resident.id());
        if (home.isEmpty()) {
            sender.sendMessage("That villager is not part of a village (it may be exempt, or in a world that is not tracked).");
            return;
        }
        Settlement settlement = home.get();
        long day = settlement.lastSimulatedDay();
        if (args[2].equalsIgnoreCase("unpin")) {
            settlement.setPinned(resident.id(), false, day);
            sender.sendMessage(resident.fullName() + " is no longer pinned: the village decides their work again.");
            service.plugin().requestSave();
            return;
        }
        io.github.skyeberhard.hamletfolk.core.Occupation job;
        try {
            job = io.github.skyeberhard.hamletfolk.core.Occupation.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage("No occupation called \"" + args[2] + "\". Run /settlement admin job for the list.");
            return;
        }
        String was = resident.occupation().title();
        resident.setOccupation(job);
        settlement.setPinned(resident.id(), true, day);
        settlement.record(day, io.github.skyeberhard.hamletfolk.core.HistoryEvent.Kind.MILESTONE, resident.fullName()
                + " was set to work as a " + job.title() + " by an admin.");
        service.plugin().requestSave();
        sender.sendMessage(resident.fullName() + " of " + settlement.name() + " is now a " + job.title() + " (was a " + was
                + "), pinned until /settlement admin job unpin.");
    }

    /** R1.31: /settlement admin unstick [name]: frees every loaded resident of a village that is walled in. */
    private void unstick(CommandSender sender, String[] args) {
        Optional<Settlement> target = args.length >= 3 ? lookup(sender, words(args, 2, args.length)) : here(sender);
        target.ifPresent(settlement -> {
            int freed = service.unstick(settlement);
            sender.sendMessage(freed == 0 ? "Nobody in " + settlement.name() + " is walled in (only loaded villagers are checked)."
                    : "Freed " + freed + (freed == 1 ? " villager" : " villagers") + " of " + settlement.name() + " to its centre.");
        });
    }

    /**
     * R4.27: /settlement admin fastforward [name|all] [speed|stop]. With no arguments, lists what is running. A speed of 2 to
     * 100 runs the village (or every village) that many times game speed until stopped or the server restarts.
     */
    private void fastForward(CommandSender sender, String[] args) {
        if (args.length < 3) {
            var running = service.fastForwarding();
            if (running.isEmpty()) {
                sender.sendMessage("Nothing is fast-forwarding. Usage: /settlement admin fastforward [name|all] <2-"
                        + io.github.skyeberhard.hamletfolk.core.FastForward.MAX_SPEED + "|stop>");
                return;
            }
            for (Settlement s : service.registry().settlements()) {
                Integer speed = running.get(s.id());
                if (speed != null) {
                    sender.sendMessage("  " + s.name() + ": " + speed + "x, day " + s.lastSimulatedDay() + ", " + s.clockAhead()
                            + " days ahead of the world, " + s.population() + " residents");
                }
            }
            return;
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        int speed;
        if (last.equals("stop") || last.equals("off")) {
            speed = 1;
        } else if (!last.isEmpty() && last.chars().allMatch(Character::isDigit) && last.length() <= 4) {
            speed = Integer.parseInt(last);
            if (speed < io.github.skyeberhard.hamletfolk.core.FastForward.MIN_SPEED) {
                sender.sendMessage("A speed is 2 to " + io.github.skyeberhard.hamletfolk.core.FastForward.MAX_SPEED + ", or stop.");
                return;
            }
        } else {
            sender.sendMessage("Usage: /settlement admin fastforward [name|all] <2-" + io.github.skyeberhard.hamletfolk.core.FastForward.MAX_SPEED + "|stop>");
            return;
        }
        int capped = Math.min(speed, io.github.skyeberhard.hamletfolk.core.FastForward.MAX_SPEED);
        List<Settlement> targets;
        if (args.length >= 4 && args[2].equalsIgnoreCase("all") && args.length == 4) {
            targets = service.registry().settlements().stream().filter(s -> !s.isAbandoned()).toList();
        } else {
            Optional<Settlement> one = args.length >= 4 ? lookup(sender, words(args, 2, args.length - 1)) : here(sender);
            if (one.isEmpty()) {
                return;
            }
            targets = List.of(one.get());
        }
        for (Settlement s : targets) {
            service.setFastForward(s, capped);
        }
        String which = targets.size() == 1 ? targets.get(0).name() : targets.size() + " villages";
        sender.sendMessage(capped <= 1 ? which + " back to normal speed."
                : which + " now running at " + capped + "x (about " + Math.max(1, 1200 / capped) + " seconds a game day). "
                        + "/settlement admin fastforward " + (targets.size() == 1 ? targets.get(0).name() : "all") + " stop to end it.");
    }

    /** R8.10: forgets a village's plan and plans it again. */
    private void replan(CommandSender sender, String[] args) {
        Optional<Settlement> target = args.length >= 3 ? lookup(sender, words(args, 2, args.length)) : here(sender);
        target.ifPresent(settlement -> sender.sendMessage(service.replan(settlement)));
    }

    /** R4.7: gives up the village's open building project (the builder goes back to being jobless). */
    private void cancelProject(CommandSender sender, String[] args) {
        Optional<Settlement> target = args.length >= 3 ? lookup(sender, words(args, 2, args.length)) : here(sender);
        if (target.isEmpty()) {
            return;
        }
        Settlement settlement = target.get();
        var open = settlement.openProject();
        if (open.isEmpty()) {
            sender.sendMessage(settlement.name() + " has no open building project.");
            return;
        }
        io.github.skyeberhard.hamletfolk.core.Construction.cancel(settlement, open.get(), settlement.lastSimulatedDay(),
                "an admin stopped it");
        service.plugin().requestSave();
        sender.sendMessage("Cancelled the " + open.get().type().label().toLowerCase(java.util.Locale.ROOT) + " project in " + settlement.name() + ".");
    }

    private void list(CommandSender sender) {
        var settlements = service.registry().settlements();
        sender.sendMessage(settlements.size() + " settlements:");
        for (Settlement s : settlements) {
            sender.sendMessage("  " + s.name() + " [" + s.id().toString().substring(0, 8) + "] " + s.world()
                    + " " + s.centerX() + ", " + s.centerZ() + " · " + s.population() + " residents"
                    + (s.isAbandoned() ? " · abandoned" : ""));
        }
    }

    private void inspect(CommandSender sender, String[] args) {
        Optional<Settlement> target = args.length >= 3 ? lookup(sender, words(args, 2, args.length)) : here(sender);
        if (target.isEmpty()) {
            return;
        }
        service.simulate(target.get());
        SettlementInspector.report(target.get()).forEach(sender::sendMessage);
    }

    private void rename(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(USAGE);
            return;
        }
        // Names can have spaces: the old name is the longest run of words that names a settlement.
        int split = -1;
        for (int end = args.length - 1; end > 2; end--) {
            if (!service.registry().findAll(words(args, 2, end)).isEmpty()) {
                split = end;
                break;
            }
        }
        Optional<Settlement> target = lookup(sender, words(args, 2, split < 0 ? 3 : split));
        if (target.isEmpty()) {
            return;
        }
        String newName = words(args, split, args.length);
        String oldName = target.get().name();
        try {
            service.registry().rename(target.get(), newName, target.get().lastSimulatedDay());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(e.getMessage());
            return;
        }
        sender.sendMessage(oldName + " is now " + target.get().name() + ".");
        service.plugin().requestSave();
    }

    private void save(CommandSender sender) {
        sender.sendMessage(service.plugin().saveNow()
                ? "Saved " + service.registry().settlements().size() + " settlements."
                : "Save failed. See the server log.");
    }

    private static String words(String[] args, int from, int to) {
        return String.join(" ", Arrays.copyOfRange(args, from, to));
    }

    /** One settlement by name or id prefix; otherwise tells the sender why not (none, or which ones). */
    private Optional<Settlement> lookup(CommandSender sender, String query) {
        List<Settlement> matches = service.registry().findAll(query);
        if (matches.isEmpty()) {
            sender.sendMessage("No settlement matches \"" + query + "\". Try /settlement admin list.");
            return Optional.empty();
        }
        if (matches.size() > 1) {
            sender.sendMessage(matches.size() + " settlements match \"" + query + "\". Use an id instead:");
            for (Settlement s : matches) {
                sender.sendMessage("  " + s.name() + " [" + s.id().toString().substring(0, 8) + "] " + s.world()
                        + " " + s.centerX() + ", " + s.centerZ());
            }
            return Optional.empty();
        }
        return Optional.of(matches.get(0));
    }

    /** The settlement a player is standing in; console users have to name one. */
    private Optional<Settlement> here(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("From the console, name a settlement: /settlement admin inspect <name|id>");
            return Optional.empty();
        }
        Optional<Settlement> found = service.settlementAt(player.getLocation());
        if (found.isEmpty()) {
            sender.sendMessage("You're not in any settlement. Name one, or use /settlement admin list.");
        }
        return found;
    }
}
