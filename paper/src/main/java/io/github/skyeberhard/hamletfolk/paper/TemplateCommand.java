package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.BiomeSet;
import io.github.skyeberhard.hamletfolk.core.Blueprint;
import io.github.skyeberhard.hamletfolk.core.BuildingType;
import io.github.skyeberhard.hamletfolk.core.Rampart;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.TemplateCatalog;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * R4.18: {@code /settlement admin capture ...} saves a building an admin has built as the template for a type, tier
 * and village style, and {@code /settlement admin build ...} puts a template down where the admin stands (the way to
 * look at what the generator makes before R4.8 builds with it). Both are for admins and need a player.
 */
final class TemplateCommand {
    static final String CAPTURE_USAGE = "Usage: /settlement admin capture pos1|pos2 | <type> [tier] [style] | remove <type> <tier> [style] | list  (type: a building, or gatehouse | tower for the rampart, tier 2 planks or 3 stone)";
    static final String BUILD_USAGE = "Usage: /settlement admin build <type> [tier] [style]  (type: house, farm, smithy, mine, guard_post, shop, treasury, gatehouse, tower)";

    private final SettlementService service;
    private final Map<UUID, Location[]> corners = new HashMap<>();

    TemplateCommand(SettlementService service) {
        this.service = service;
    }

    private TemplateLibrary library() {
        return service.plugin().templates();
    }

    void capture(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Capturing needs a player in game.");
            return;
        }
        String word = args.length < 3 ? "" : args[2].toLowerCase(Locale.ROOT);
        switch (word) {
            case "" -> sender.sendMessage(CAPTURE_USAGE);
            case "pos1", "pos2" -> {
                Location[] pair = corners.computeIfAbsent(player.getUniqueId(), id -> new Location[2]);
                Location at = player.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getLocation();
                pair[word.equals("pos1") ? 0 : 1] = at;
                sender.sendMessage((word.equals("pos1") ? "Corner 1 (the floor block under you, a bottom corner) set to "
                        : "Corner 2 (the opposite corner) set to ") + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ() + ".");
            }
            case "list" -> list(sender);
            case "remove" -> remove(sender, args);
            default -> save(player, args);
        }
    }

    private void save(Player player, String[] args) {
        Optional<BuildingType> type = BuildingType.fromCapture(args[2]);
        if (type.isEmpty()) {
            player.sendMessage(CAPTURE_USAGE);
            return;
        }
        Location[] pair = corners.get(player.getUniqueId());
        if (pair == null || pair[0] == null || pair[1] == null || pair[0].getWorld() != pair[1].getWorld()) {
            player.sendMessage("Set both corners first: stand on the floor at one bottom corner and run "
                    + "/settlement admin capture pos1, then at the opposite corner and run pos2.");
            return;
        }
        boolean part = type.get().isPart(); // R5.11: a gatehouse or tower is for a rampart tier, 2 (planks) or 3 (stone)
        int tier = args.length > 3 ? parseTier(args[3]) : part ? Rampart.FIRST_TIER : 1;
        if (tier < 1 || (part && (tier < Rampart.FIRST_TIER || tier > Rampart.LAST_TIER))) {
            player.sendMessage(part ? "A gatehouse or tower is for rampart tier " + Rampart.FIRST_TIER + " (planks) or "
                    + Rampart.LAST_TIER + " (stone)." : "The tier is a number from 1 up.");
            return;
        }
        String style = args.length > 4 ? BiomeSet.normalize(args[4]) : styleAt(player.getLocation());
        int top = library().catalog().ladder(type.get(), style).size() + 1;
        if (!part && tier > top) {
            player.sendMessage("The next tier to capture for that is " + top + " (tiers go in order).");
            return;
        }
        Location a = pair[0];
        Location b = pair[1];
        int sx = Math.abs(a.getBlockX() - b.getBlockX()) + 1;
        int sy = Math.abs(a.getBlockY() - b.getBlockY()) + 1;
        int sz = Math.abs(a.getBlockZ() - b.getBlockZ()) + 1;
        if (sx > TemplateLibrary.MAX_SIDE || sy > TemplateLibrary.MAX_SIDE || sz > TemplateLibrary.MAX_SIDE
                || (long) sx * sy * sz > TemplateLibrary.MAX_VOLUME) {
            player.sendMessage("That area is " + sx + " by " + sy + " by " + sz + ": too big. Most is " + TemplateLibrary.MAX_SIDE
                    + " on a side and " + TemplateLibrary.MAX_VOLUME + " blocks in all.");
            return;
        }
        Blueprint blueprint = TemplateLibrary.read(a.getWorld(), a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ(), a.getBlockY(), "captured");
        if (blueprint.blocks().isEmpty()) {
            player.sendMessage("There is nothing but air in that area.");
            return;
        }
        try {
            library().capture(type.get(), style, tier, blueprint);
        } catch (IOException e) {
            service.plugin().getLogger().warning("Could not save a template: " + e.getMessage());
            player.sendMessage("Could not save it. See the server log.");
            return;
        }
        player.sendMessage("Saved a " + type.get().label().toLowerCase(Locale.ROOT) + " for " + style + ", tier " + tier + ": "
                + blueprint.blocks().size() + " blocks, " + blueprint.width() + " by " + blueprint.depth() + ", costing "
                + describe(blueprint.cost()) + ".");
        if (part) {
            player.sendMessage(type.get() == BuildingType.GATEHOUSE
                    ? "Build a gatehouse with its passage running north to south and its outside to the south: the village turns it "
                            + "to face outward at each gate and centres it on the gate."
                    : "Build a tower as the north-west one, its outer corner at the corner you marked nearest north-west: the village "
                            + "turns it for the other three corners and puts its outer corner on the ring's corner.");
        } else if (blueprint.sign().isEmpty()) {
            player.sendMessage("It has no wall sign, so a building of this kind will need a sign placed by hand.");
        }
        player.sendMessage("It is placed as built (north is not rotated). /settlement admin build " + args[2] + " " + tier + " " + style
                + " puts it down.");
    }

    private void remove(CommandSender sender, String[] args) {
        Optional<BuildingType> type = args.length > 3 ? BuildingType.fromCapture(args[3]) : Optional.empty();
        int tier = args.length > 4 ? parseTier(args[4]) : -1;
        if (type.isEmpty() || tier < 1) {
            sender.sendMessage(CAPTURE_USAGE);
            return;
        }
        String style = args.length > 5 ? BiomeSet.normalize(args[5])
                : sender instanceof Player p ? styleAt(p.getLocation()) : BiomeSet.PLAINS;
        try {
            sender.sendMessage(library().remove(type.get(), style, tier)
                    ? "Removed the captured template; the generated or vanilla one is used again."
                    : "There is no captured template for that.");
        } catch (IOException e) {
            sender.sendMessage("Could not delete the file. See the server log.");
        }
    }

    private void list(CommandSender sender) {
        int found = 0;
        for (BuildingType type : BuildingType.values()) {
            for (String style : BiomeSet.ALL) {
                for (TemplateCatalog.Template rung : library().catalog().ladder(type, style)) {
                    if (rung.source() == TemplateCatalog.Source.CAPTURED) {
                        sender.sendMessage("  " + type.label() + " · " + style + " · tier " + rung.tier());
                        found++;
                    }
                }
                for (int tier = Rampart.FIRST_TIER; type.isPart() && tier <= Rampart.LAST_TIER; tier++) {
                    if (library().catalog().part(type, style, tier).isPresent()) {
                        sender.sendMessage("  " + type.label() + " · " + style + " · rampart tier " + tier);
                        found++;
                    }
                }
            }
        }
        sender.sendMessage(found == 0 ? "No captured templates: every building uses the generated or vanilla one."
                : found + " captured templates.");
    }

    void build(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Building needs a player in game.");
            return;
        }
        Optional<BuildingType> type = args.length > 2 ? BuildingType.fromCapture(args[2]) : Optional.empty();
        if (type.isEmpty()) {
            sender.sendMessage(BUILD_USAGE);
            return;
        }
        int tier = args.length > 3 ? parseTier(args[3]) : type.get().isPart() ? Rampart.FIRST_TIER : 1;
        String style = args.length > 4 ? BiomeSet.normalize(args[4]) : styleAt(player.getLocation());
        if (type.get().isPart()) { // R5.11: the captured gatehouse or tower as built, to look at
            Optional<Blueprint> captured = library().catalog().part(type.get(), style, tier);
            if (captured.isEmpty()) {
                sender.sendMessage("No " + type.get().label().toLowerCase(Locale.ROOT) + " is captured for " + style + ", rampart tier "
                        + tier + ": the village builds the generated one.");
                return;
            }
            Blueprint b = captured.get();
            Location origin = player.getLocation().getBlock().getLocation().add(-b.width() / 2, -1, 2);
            sender.sendMessage("Placed " + TemplateLibrary.place(origin, b) + " blocks of the captured " + type.get().label().toLowerCase(Locale.ROOT)
                    + " (" + style + ", tier " + tier + "), costing " + describe(b.cost()) + ".");
            return;
        }
        List<TemplateCatalog.Template> ladder = library().catalog().ladder(type.get(), style);
        if (tier < 1 || tier > ladder.size()) {
            sender.sendMessage("A " + type.get().label().toLowerCase(Locale.ROOT) + " has " + ladder.size() + " tier"
                    + (ladder.size() == 1 ? "" : "s") + " in the " + style + " style.");
            return;
        }
        TemplateCatalog.Template rung = ladder.get(tier - 1);
        Optional<Blueprint> blueprint = library().blueprint(rung);
        if (blueprint.isEmpty()) {
            sender.sendMessage("The server has no piece called " + rung.key() + " (see the startup log).");
            return;
        }
        Blueprint b = blueprint.get();
        // Floor level is the block under the player's feet; the building goes just south of them.
        Location origin = player.getLocation().getBlock().getLocation().add(-b.width() / 2, -1, 2);
        int placed = TemplateLibrary.place(origin, b);
        sender.sendMessage("Placed " + placed + " blocks of " + rung.key() + " (" + rung.source().name().toLowerCase(Locale.ROOT)
                + ", " + style + ", tier " + tier + "), costing " + describe(b.cost()) + ".");
        b.sign().ifPresent(sign -> signAndRegister(player, type.get(), origin, sign));
    }

    /** Writes the building's name on its sign and registers it like a player-placed one. */
    private void signAndRegister(Player player, BuildingType type, Location origin, Blueprint.Block at) {
        Block block = origin.getWorld().getBlockAt(origin.getBlockX() + at.x(), origin.getBlockY() + at.y(),
                origin.getBlockZ() + at.z());
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }
        sign.getSide(Side.FRONT).line(1, Component.text(type.signText()));
        sign.update(true, false);
        Optional<Settlement> settlement = service.settlementAt(block.getLocation());
        if (settlement.isEmpty()) {
            player.sendMessage("It is not inside a settlement, so its sign registered nothing.");
            return;
        }
        Settlement.Registration result = service.registerBuilding(settlement.get(), type, block.getLocation(), player.getName());
        player.sendMessage(result == Settlement.Registration.REGISTERED
                ? "Registered it in " + settlement.get().name() + "."
                : "Its sign was not registered (" + result.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ").");
        service.plugin().requestSave();
    }

    private static String describe(Map<io.github.skyeberhard.hamletfolk.core.ResourceType, Integer> cost) {
        if (cost.isEmpty()) {
            return "nothing";
        }
        StringBuilder out = new StringBuilder();
        cost.forEach((type, units) -> out.append(out.isEmpty() ? "" : ", ").append(units).append(' ')
                .append(type.name().toLowerCase(Locale.ROOT)));
        return out.toString();
    }

    private static int parseTier(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String styleAt(Location at) {
        return BiomeSet.forBiome(at.getWorld().getComputedBiome(at.getBlockX(), at.getBlockY(), at.getBlockZ())
                .getKey().getKey());
    }
}
