package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.IgnoreZones;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
 * R1.30: a sign reading {@code [Exempt]} (or {@code [Ignore]}) leaves the villagers around it alone: they are not enrolled,
 * named, re-priced, refused a sale or moved. The radius is the number on the next line (24 by default). Breaking the sign
 * ends the exemption; signs destroyed any other way are caught by {@code SettlementService#pruneIgnoreZones}.
 */
final class IgnoreListener implements Listener {
    static final String PERMISSION = "hamletfolk.ignore";
    /** Also exempts villagers that already belong to a village (by default, ops only). */
    static final String VILLAGE_PERMISSION = "hamletfolk.ignore.village";
    private final SettlementService service;

    IgnoreListener(SettlementService service) {
        this.service = service;
    }

    private static String plain(Component line) {
        return PlainTextComponentSerializer.plainText().serialize(line);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSignChanged(SignChangeEvent event) {
        Block block = event.getBlock();
        if (!service.inScope(block.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        IgnoreZones zones = service.zonesOf(block.getWorld());
        int radius = radiusFrom(event.lines());
        if (radius < 0) {
            // The edited side no longer says so; the exemption ends unless the other side still does.
            boolean otherSide = false;
            if (block.getState(false) instanceof Sign sign) {
                Side other = event.getSide() == Side.FRONT ? Side.BACK : Side.FRONT;
                otherSide = radiusFrom(sign.getSide(other).lines()) >= 0;
            }
            if (!otherSide && zones.removeAt(block.getX(), block.getY(), block.getZ())) {
                service.saveZones(block.getWorld());
                player.sendMessage(Component.text("That sign no longer exempts villagers.", NamedTextColor.GRAY));
            }
            return;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(Component.text("You may not exempt villagers.", NamedTextColor.GRAY));
            return;
        }
        // An ordinary player's sign leaves out villagers that already belong to a village; an admin's does not.
        boolean admin = player.hasPermission(VILLAGE_PERMISSION);
        IgnoreZones.Zone zone = new IgnoreZones.Zone(block.getX(), block.getY(), block.getZ(), radius,
                player.getUniqueId().toString(), admin);
        switch (zones.add(zone)) {
            case TOO_MANY_FOR_PLAYER -> player.sendMessage(Component.text("You already have " + IgnoreZones.MAX_PER_PLAYER
                    + " exempt signs. Break one first.", NamedTextColor.GRAY));
            case FULL -> player.sendMessage(Component.text("This world already has as many exempt signs as it allows.",
                    NamedTextColor.GRAY));
            default -> {
                service.saveZones(block.getWorld());
                int released = service.releaseWithin(block.getWorld(), zone);
                int staying = zone.overridesVillages() ? 0 : service.registeredWithin(block.getWorld(), zone);
                player.sendMessage(Component.text("Villagers within " + radius + " blocks are now left alone: not part of any "
                        + "village, not re-priced, not moved." + (released > 0 ? " (" + released + " were in a village and have been released.)" : ""),
                        NamedTextColor.GREEN));
                if (staying > 0) {
                    player.sendMessage(Component.text(staying + " villagers here already belong to a village and stay in it. "
                            + "Only an admin's sign can exempt those.", NamedTextColor.GRAY));
                }
            }
        }
    }

    /** The radius a sign's lines ask for, or -1 if none of them names an exempt zone. */
    private static int radiusFrom(List<Component> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i) != null && IgnoreZones.isSign(plain(lines.get(i)))) {
                Component next = i + 1 < lines.size() ? lines.get(i + 1) : null;
                return IgnoreZones.radius(next == null ? "" : plain(next));
            }
        }
        return -1;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!service.inScope(block.getWorld())) {
            return;
        }
        if (service.zonesOf(block.getWorld()).removeAt(block.getX(), block.getY(), block.getZ())) {
            service.saveZones(block.getWorld());
            event.getPlayer().sendMessage(Component.text("Villagers there are no longer exempt.", NamedTextColor.GRAY));
        }
    }
}
