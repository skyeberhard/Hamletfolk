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
    private static final List<String> SUBCOMMANDS = List.of("list", "inspect", "rename", "save", "ignore", "capture", "build", "cancelproject", "found", "replan");
    private static final String USAGE = "Usage: /settlement admin list | inspect [name|id] | rename <name|id> <new name> | save | ignore (look at a villager) | capture ... | build ... | cancelproject [name] | found [name] [residents] | replan [name]";

    private final SettlementService service;
    private final TemplateCommand templates;

    AdminCommand(SettlementService service) {
        this.service = service;
        this.templates = new TemplateCommand(service);
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
