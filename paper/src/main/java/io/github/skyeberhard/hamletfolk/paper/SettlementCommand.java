package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.HistoryEvent;
import io.github.skyeberhard.hamletfolk.core.Resident;
import io.github.skyeberhard.hamletfolk.core.ResourceMapper;
import io.github.skyeberhard.hamletfolk.core.ResourceType;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** /settlement [info|history|residents|donate] — about the settlement you're standing in; admin works anywhere. */
final class SettlementCommand implements TabExecutor {
    private static final List<String> SUBCOMMANDS = List.of("info", "history", "residents", "donate");
    private static final int EVENTS_PER_PAGE = 3;
    /** Vanilla's limit for a written book; more and the client refuses it. */
    private static final int MAX_BOOK_PAGES = 100;

    private final SettlementService service;
    private final AdminCommand admin;

    SettlementCommand(SettlementService service) {
        this.service = service;
        this.admin = new AdminCommand(service);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            admin.run(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Optional<Settlement> found = service.settlementAt(player.getLocation());
        if (found.isEmpty()) {
            player.sendMessage(Component.text("You're not in any settlement.", NamedTextColor.GRAY));
            return true;
        }
        Settlement settlement = found.get();
        service.simulate(settlement);

        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> info(player, settlement);
            case "history" -> history(player, settlement);
            case "residents" -> residents(player, settlement);
            case "donate" -> donate(player, settlement);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length > 1 && args[0].equalsIgnoreCase("admin")) {
            return admin.complete(sender, args);
        }
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>(SUBCOMMANDS);
        if (sender.hasPermission(AdminCommand.PERMISSION)) {
            options.add("admin");
        }
        return options.stream().filter(s -> s.startsWith(prefix)).toList();
    }

    private void info(Player player, Settlement s) {
        long today = s.effectiveDay(SettlementService.day(player.getWorld())); // R1.23
        long children = s.residents().stream().filter(r -> !r.adult()).count();

        player.sendMessage(Component.text(s.name(), NamedTextColor.GOLD)
                .append(Component.text(" · founded day " + s.foundedDay() + " (" + (today - s.foundedDay())
                        + " days ago)", NamedTextColor.GRAY)));
        line(player, "Population", s.population() + " (" + children + " children)");
        if (s.turnedCount() > 0) {
            line(player, "Lost to zombies", s.turnedCount() + " (they can still be cured)");
        }

        StringBuilder stock = new StringBuilder();
        for (ResourceType type : ResourceType.values()) {
            if (!stock.isEmpty()) {
                stock.append(", ");
            }
            stock.append(s.ledger().get(type)).append(' ').append(type.name().toLowerCase(Locale.ROOT));
        }
        line(player, "Stores", stock.toString());
        line(player, "Treasury", s.ledger().treasury() + " emeralds");
        String flow = flowSummary(s);
        if (!flow.isEmpty()) {
            line(player, "Last " + s.flowDays() + " days", flow);
        }
        line(player, "Danger", threatLabel(s.threat()));

        List<String> troubles = new ArrayList<>();
        if (s.hasCondition("famine")) {
            troubles.add("famine");
        }
        for (ResourceType type : ResourceType.values()) {
            String name = type.name().toLowerCase(Locale.ROOT);
            if (s.hasCondition("shortage:" + name)) {
                troubles.add("no " + name);
            }
        }
        if (!troubles.isEmpty()) {
            player.sendMessage(Component.text("Troubles: " + String.join(", ", troubles), NamedTextColor.RED));
        }
    }

    private void history(Player player, Settlement s) {
        List<HistoryEvent> all = s.history();
        // A written book holds at most MAX_BOOK_PAGES pages; one is the title page.
        int room = (MAX_BOOK_PAGES - 1) * EVENTS_PER_PAGE;
        List<HistoryEvent> events = all.subList(Math.max(0, all.size() - room), all.size());
        String count = events.size() == all.size()
                ? all.size() + " recorded events"
                : "the latest " + events.size() + " of " + all.size() + " recorded events";

        List<Component> pages = new ArrayList<>();
        pages.add(Component.text("The History of\n", NamedTextColor.DARK_GRAY)
                .append(Component.text(s.name() + "\n\n", NamedTextColor.DARK_BLUE))
                .append(Component.text(s.population() + " residents\n" + count, NamedTextColor.BLACK)));

        for (int i = 0; i < events.size(); i += EVENTS_PER_PAGE) {
            Component page = Component.empty();
            for (HistoryEvent event : events.subList(i, Math.min(events.size(), i + EVENTS_PER_PAGE))) {
                page = page.append(Component.text("Day " + event.day() + "\n", NamedTextColor.DARK_RED))
                        .append(Component.text(event.text() + "\n\n", NamedTextColor.BLACK));
            }
            pages.add(page);
        }
        player.openBook(Book.book(Component.text(s.name()), Component.text("The villagers"), pages));
    }

    private void residents(Player player, Settlement s) {
        player.sendMessage(Component.text("Residents of " + s.name() + ":", NamedTextColor.GOLD));
        int shown = 0;
        for (Resident r : s.residents()) {
            if (shown++ == 20) {
                player.sendMessage(Component.text("...and " + (s.population() - 20) + " more.", NamedTextColor.GRAY));
                break;
            }
            String role = r.adult() ? r.occupation().title() : "child";
            player.sendMessage(Component.text(" " + r.fullName(), NamedTextColor.WHITE)
                    .append(Component.text(" — " + role, NamedTextColor.GRAY)));
        }
    }

    private void donate(Player player, Settlement s) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage(Component.text("Hold the items you want to donate.", NamedTextColor.GRAY));
            return;
        }
        String material = item.getType().getKey().getKey();
        int amount = item.getAmount();
        String itemName = material.replace('_', ' ');

        if (ResourceMapper.isCurrency(material)) {
            s.ledger().addTreasury(amount);
        } else {
            Optional<ResourceType> type = ResourceMapper.classify(material);
            if (type.isEmpty()) {
                player.sendMessage(Component.text(s.name() + " has no use for " + itemName + ".", NamedTextColor.GRAY));
                return;
            }
            s.ledger().add(type.get(), amount);
        }
        player.getInventory().setItemInMainHand(null);
        s.recordDonation(SettlementService.day(player.getWorld()), player.getName(), amount, itemName);
        player.sendMessage(Component.text("The people of " + s.name() + " thank you.", NamedTextColor.GREEN));
        service.plugin().requestSave();
    }

    /** R3.7: per-day production and use over the recent window, e.g. "food +12/day made, -15/day eaten". */
    private static String flowSummary(Settlement s) {
        long today = s.lastSimulatedDay();
        int span = s.flowDays();
        List<String> parts = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            int made = Math.round((float) s.flow().produced(type, today) / span);
            int used = Math.round((float) s.flow().consumed(type, today) / span);
            if (made == 0 && used == 0) {
                continue;
            }
            String name = type.name().toLowerCase(Locale.ROOT);
            parts.add(name + " +" + made + "/day made, -" + used + "/day "
                    + (type == ResourceType.FOOD ? "eaten" : "used"));
        }
        return String.join("; ", parts);
    }

    private static void line(Player player, String label, String value) {
        player.sendMessage(Component.text(label + ": ", NamedTextColor.YELLOW)
                .append(Component.text(value, NamedTextColor.WHITE)));
    }

    private static String threatLabel(double threat) {
        if (threat < 10) {
            return "peaceful";
        }
        if (threat < 35) {
            return "uneasy";
        }
        if (threat < 65) {
            return "frightened";
        }
        return "under siege";
    }
}
