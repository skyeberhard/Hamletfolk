package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.Building;
import io.github.skyeberhard.hamletfolk.core.Donation;
import io.github.skyeberhard.hamletfolk.core.HistoryEvent;
import io.github.skyeberhard.hamletfolk.core.LifeStage;
import io.github.skyeberhard.hamletfolk.core.Reputation;
import io.github.skyeberhard.hamletfolk.core.Request;
import io.github.skyeberhard.hamletfolk.core.Resident;
import io.github.skyeberhard.hamletfolk.core.ResourceMapper;
import io.github.skyeberhard.hamletfolk.core.ResourceType;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.SettlementSimulator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntUnaryOperator;
import java.util.Optional;
import java.util.OptionalInt;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/** /settlement [info|history|residents|donate [amount|all]] — about the settlement you're standing in; admin works anywhere. */
final class SettlementCommand implements TabExecutor {
    private static final List<String> SUBCOMMANDS = List.of("info", "history", "residents", "donate", "buildings");
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
            case "donate" -> donate(player, settlement, args);
            case "buildings" -> buildings(player, settlement);
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
        if (args.length == 2 && args[0].equalsIgnoreCase("donate")) {
            return "all".startsWith(args[1].toLowerCase(Locale.ROOT)) ? List.of("all") : List.of();
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
        service.refreshHousing(s);
        line(player, "Population", s.population() + " (" + children + " children)");
        line(player, "Housing", s.housingCapacity() + " beds (" + s.freeBeds() + " free)");
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
        for (Request request : s.requests()) {
            line(player, "Wanted", request.remaining() + " more " + request.type().name().toLowerCase(Locale.ROOT)
                    + " (" + request.unpaid() + " emeralds on offer), /settlement donate");
        }
        String flow = flowSummary(s);
        if (!flow.isEmpty()) {
            line(player, "Last " + s.flowDays() + " days", flow);
        }
        line(player, "Danger", threatLabel(s.threat()));
        line(player, "Regard", regardLabel(s.reputationOf(player.getUniqueId())));

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

    /** R3.4: how the settlement sees the player, e.g. "friendly (+32)". */
    private static String regardLabel(int score) {
        return Reputation.standing(score).name().toLowerCase(Locale.ROOT) + " (" + (score > 0 ? "+" : "") + score + ")";
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
            String role = r.adult() ? r.occupation().title() + ", " + r.age(s.lastSimulatedDay()) + " days"
                    + (r.stage(s.lastSimulatedDay()) == LifeStage.ELDER ? " (elder)" : "")
                    : "child";
            player.sendMessage(Component.text(" " + r.fullName(), NamedTextColor.WHITE)
                    .append(Component.text(" — " + role + " (" + r.gender().pronouns() + ")", NamedTextColor.GRAY)));
        }
    }

    /** R2.1: /settlement buildings lists what players have registered with signs. */
    private void buildings(Player player, Settlement s) {
        if (s.buildings().isEmpty()) {
            player.sendMessage(Component.text("No buildings are registered in " + s.name() + ". Place a sign reading "
                    + "[Farm], [Smithy], [Mine], [House] or [Guard Post] inside the village to register one.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("Buildings of " + s.name() + ":", NamedTextColor.GOLD));
        int shown = 0;
        for (Building b : s.buildings()) {
            if (shown++ == 25) {
                player.sendMessage(Component.text("...and " + (s.buildings().size() - 25) + " more.", NamedTextColor.GRAY));
                break;
            }
            player.sendMessage(Component.text(" " + b.type().label(), NamedTextColor.WHITE)
                    .append(Component.text(" at " + b.x() + ", " + b.y() + ", " + b.z() + " (day " + b.registeredDay()
                            + ", " + b.registeredBy() + ")", NamedTextColor.GRAY)));
        }
    }

    /**
     * /settlement donate [amount|all]. Without an amount it only says what you're holding is
     * worth, so nothing is taken by accident (R3.8). Items are credited by value: a storage
     * block counts as what it holds, and a better tool as more than a worse one.
     */
    private void donate(Player player, Settlement s, String[] args) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage(Component.text("Hold the items you want to donate.", NamedTextColor.GRAY));
            return;
        }
        String material = item.getType().getKey().getKey();
        int held = item.getAmount();
        String itemName = material.replace('_', ' ');

        int emeralds = ResourceMapper.currencyValue(material);
        // Read the item's wear once, before anything is taken from the hand.
        double wear = condition(item);
        Optional<ResourceMapper.Value> value = emeralds > 0 ? Optional.empty()
                : ResourceMapper.value(material, wear);
        if (emeralds == 0 && value.isEmpty()) {
            player.sendMessage(Component.text(s.name() + " has no use for " + itemName + ".", NamedTextColor.GRAY));
            return;
        }
        // Worth of a stack of n: emeralds by count, anything else by its valuation (R3.12), rounded down.
        IntUnaryOperator worth = n -> emeralds > 0 ? n * emeralds : value.get().unitsFor(n);
        String unitName = emeralds > 0 ? "emeralds" : value.get().type().name().toLowerCase(Locale.ROOT);

        // R3.11: how much the stores can still hold, so a gift to a full store is never taken and wasted.
        String roomNote = emeralds > 0 ? "" : " They have room for "
                + SettlementSimulator.room(s, value.get().type()) + " more " + unitName + ".";
        if (args.length < 2) {
            player.sendMessage(Component.text("You're holding " + held + " " + itemName + ", worth "
                    + worth.applyAsInt(held) + " " + unitName + " to " + s.name() + "." + roomNote + " Use ", NamedTextColor.GRAY)
                    .append(Component.text("/settlement donate <amount>", NamedTextColor.YELLOW))
                    .append(Component.text(" or ", NamedTextColor.GRAY))
                    .append(Component.text("/settlement donate all", NamedTextColor.YELLOW))
                    .append(Component.text(".", NamedTextColor.GRAY)));
            return;
        }
        OptionalInt quantity = ResourceMapper.parseQuantity(args[1], held);
        if (quantity.isEmpty()) {
            player.sendMessage(Component.text("Say how many to donate: a number from 1 to " + held
                    + ", or \"all\".", NamedTextColor.GRAY));
            return;
        }
        int offered = quantity.getAsInt();
        int amount = offered;
        int credited;
        boolean limitedByRoom = false;
        if (emeralds > 0) {
            credited = offered * emeralds;
        } else {
            // Only as many items as are worth something and fit in the room left: the rest stay with the player.
            Donation.Plan plan = Donation.plan(s, value.get(), offered);
            amount = plan.items();
            credited = plan.units();
            limitedByRoom = plan.limitedByRoom();
            if (credited == 0) {
                if (plan.limitedByRoom()) {
                    // Full, or with less room left than one of these is worth: giving more will not help.
                    player.sendMessage(Component.text(plan.room() == 0
                            ? "The " + unitName + " stores of " + s.name() + " are full. Nothing taken."
                            : s.name() + " only has room for " + plan.room() + " more " + unitName
                                    + ", less than one " + itemName + " is worth. Nothing taken.", NamedTextColor.GRAY));
                } else {
                    // A lone stick, or a tool worn to nothing, is worth less than one unit.
                    player.sendMessage(Component.text(offered + " " + itemName + " is worth nothing to " + s.name()
                            + (offered < held ? "; try giving more at once." : " as it is."), NamedTextColor.GRAY));
                }
                return;
            }
        }

        if (emeralds > 0) {
            s.ledger().addTreasury(credited);
        } else {
            s.ledger().add(value.get().type(), credited);
        }
        if (amount == held) {
            player.getInventory().setItemInMainHand(null);
        } else {
            ItemStack rest = item.clone();
            rest.setAmount(held - amount);
            player.getInventory().setItemInMainHand(rest);
        }
        long today = SettlementService.day(player.getWorld());
        s.recordDonation(today, player.getName(), amount, itemName);
        s.adjustReputation(player.getUniqueId(), Reputation.donationGain(
                emeralds > 0 ? ResourceType.GOODS : value.get().type(), credited, emeralds > 0)); // R3.4
        // R3.3: a donation of something the village asked for is paid for out of the request.
        // R3.13: a crafted tool never pays more than the raw materials in it would.
        int toolCap = SettlementSimulator.maxPayout(material, wear);
        if (toolCap != Integer.MAX_VALUE) {
            toolCap = (int) Math.min(Integer.MAX_VALUE, (long) toolCap * amount); // the cap is per tool; a stack is several
        }
        int paid = emeralds > 0 ? 0 : service.fulfilRequest(s, value.get().type(), credited, today, player.getName(),
                toolCap);
        s.adjustReputation(player.getUniqueId(), Reputation.requestGain(paid)); // R3.4
        // In stacks of at most 64: a big request can pay more than a stack, and an oversized item is not safe to drop.
        for (int left = paid; left > 0; left -= 64) {
            player.getInventory().addItem(new ItemStack(Material.EMERALD, Math.min(64, left)))
                    .values().forEach(over -> player.getWorld().dropItem(player.getLocation(), over));
        }
        player.sendMessage(Component.text("The people of " + s.name() + " thank you. (+" + credited + " "
                + unitName + (paid > 0 ? "; they pay you " + paid + " emeralds for it" : "")
                + (limitedByRoom ? "; the stores cannot hold the rest, so you keep the other " + (offered - amount) : "") + ")",
                NamedTextColor.GREEN));
        service.plugin().requestSave();
    }

    /** 1.0 for a new item, falling to 0.0 as a tool wears out; anything that does not wear counts as new. */
    private static double condition(ItemStack item) {
        int max = item.getType().getMaxDurability();
        if (item.getItemMeta() instanceof Damageable damageable) {
            // An item can carry its own maximum durability, which is what its wear is measured against.
            if (damageable.hasMaxDamage()) {
                max = damageable.getMaxDamage();
            }
            if (max > 0) {
                return 1.0 - (double) damageable.getDamage() / max;
            }
        }
        return 1.0;
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
