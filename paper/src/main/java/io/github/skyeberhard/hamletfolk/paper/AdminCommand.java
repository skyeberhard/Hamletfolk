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
    private static final List<String> SUBCOMMANDS = List.of("list", "inspect", "rename", "save");
    private static final String USAGE = "Usage: /settlement admin list | inspect [name|id] | rename <name|id> <new name> | save";

    private final SettlementService service;

    AdminCommand(SettlementService service) {
        this.service = service;
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
        Optional<Settlement> target = args.length >= 3 ? lookup(sender, args[2]) : here(sender);
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
        Optional<Settlement> target = lookup(sender, args[2]);
        if (target.isEmpty()) {
            return;
        }
        String newName = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
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

    private Optional<Settlement> lookup(CommandSender sender, String query) {
        Optional<Settlement> found = service.registry().find(query);
        if (found.isEmpty()) {
            sender.sendMessage("No settlement matches \"" + query + "\". Try /settlement admin list.");
        }
        return found;
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
