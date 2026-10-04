package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.Building;
import io.github.skyeberhard.hamletfolk.core.BuildingType;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;

/**
 * R2.1: a sign reading {@code [Farm]}, {@code [Smithy]}, {@code [Mine]}, {@code [Shop]}, {@code [House]} or
 * {@code [Guard Post]} inside a settlement registers a building; breaking the sign removes it.
 * A sign has two sides: the front names the building if both do. Signs destroyed any other way
 * (physics, explosions, pistons) are caught by {@code SettlementService#pruneBuildings}.
 */
final class BuildingListener implements Listener {
    private final SettlementService service;

    BuildingListener(SettlementService service) {
        this.service = service;
    }

    /** The building a set of sign lines names, and the line it is on, or null. */
    private static Match match(List<Component> lines) {
        for (int i = 0; i < lines.size(); i++) {
            Optional<BuildingType> found = BuildingType.fromSign(PlainTextComponentSerializer.plainText().serialize(lines.get(i)));
            if (found.isPresent()) {
                return new Match(found.get(), i);
            }
        }
        return null;
    }

    private record Match(BuildingType type, int line) {
    }

    /**
     * Tidies the sign text to the standard spelling. Done late enough that other plugins have had their
     * say, but registration itself waits for the final outcome (see {@link #onSignChanged}).
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSignTidy(SignChangeEvent event) {
        if (!service.inScope(event.getBlock().getWorld()) || !event.getPlayer().hasPermission("hamletfolk.use")) {
            return;
        }
        Match match = match(event.lines());
        if (match != null && service.settlementAt(event.getBlock().getLocation()).isPresent()) {
            event.line(match.line(), Component.text(match.type().signText()));
        }
    }

    /** Registers (or unregisters) once the edit has not been cancelled by anything else. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSignChanged(SignChangeEvent event) {
        Block block = event.getBlock();
        if (!service.inScope(block.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        // The front wins if both sides name a building. This event only carries the side that was edited,
        // so the other side is read from the sign as it stands.
        Match front = null;
        Match back = null;
        if (block.getState(false) instanceof Sign sign) {
            front = match(event.getSide() == Side.FRONT ? event.lines() : sign.getSide(Side.FRONT).lines());
            back = match(event.getSide() == Side.BACK ? event.lines() : sign.getSide(Side.BACK).lines());
        } else {
            front = match(event.lines());
        }
        Match named = front != null ? front : back;
        if (named == null) {
            // Neither side names a building any more.
            if (service.removeBuildingAt(block.getWorld(), block.getX(), block.getY(), block.getZ()).isPresent()) {
                player.sendMessage(Component.text("That sign no longer marks a building.", NamedTextColor.GRAY));
                service.plugin().requestSave();
            }
            return;
        }
        if (!player.hasPermission("hamletfolk.use")) {
            return;
        }
        Optional<Settlement> settlement = service.settlementAt(block.getLocation());
        if (settlement.isEmpty()) {
            player.sendMessage(Component.text("This sign is not inside a settlement, so it registers nothing.", NamedTextColor.GRAY));
            return;
        }
        BuildingType type = named.type();
        String kind = type.label().toLowerCase(Locale.ROOT);
        switch (service.registerBuilding(settlement.get(), type, block.getLocation(), player.getName())) {
            case REGISTERED -> {
                player.sendMessage(Component.text("Registered a " + kind + " in " + settlement.get().name() + ".", NamedTextColor.GREEN));
                service.plugin().requestSave();
            }
            case ALREADY_REGISTERED -> player.sendMessage(Component.text("This " + kind + " is already registered.", NamedTextColor.GRAY));
            case TOO_MANY -> player.sendMessage(Component.text(settlement.get().name() + " already has "
                    + Settlement.MAX_BUILDINGS + " registered buildings.", NamedTextColor.GRAY));
            default -> { }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!service.inScope(block.getWorld()) || !Tag.ALL_SIGNS.isTagged(block.getType())) {
            return;
        }
        Optional<Building> removed = service.removeBuildingAt(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (removed.isPresent()) {
            event.getPlayer().sendMessage(Component.text("Unregistered the "
                    + removed.get().type().label().toLowerCase(Locale.ROOT) + ".", NamedTextColor.GRAY));
            service.plugin().requestSave();
        }
    }
}
