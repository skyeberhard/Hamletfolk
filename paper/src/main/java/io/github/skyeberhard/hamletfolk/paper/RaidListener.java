package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.HistoryEvent;
import java.util.stream.Collectors;
import org.bukkit.Raid;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.raid.RaidFinishEvent;
import org.bukkit.event.raid.RaidStopEvent;
import org.bukkit.event.raid.RaidTriggerEvent;

/** Records raids in the history of the settlement they hit. */
final class RaidListener implements Listener {
    private final SettlementService service;

    RaidListener(SettlementService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTrigger(RaidTriggerEvent event) {
        service.settlementAt(event.getRaid().getLocation()).ifPresent(settlement -> settlement.raiseThreat(30));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVictory(RaidFinishEvent event) {
        service.settlementAt(event.getRaid().getLocation()).ifPresent(settlement -> {
            String heroes = event.getWinners().stream().map(Player::getName).collect(Collectors.joining(", "));
            String text = "Raiders attacked " + settlement.name() + " and were driven off"
                    + (heroes.isEmpty() ? "." : " with the help of " + heroes + ".");
            settlement.record(SettlementService.day(event.getWorld()), HistoryEvent.Kind.RAID, text);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStop(RaidStopEvent event) {
        if (event.getRaid().getStatus() != Raid.RaidStatus.LOSS) {
            return;
        }
        service.settlementAt(event.getRaid().getLocation()).ifPresent(settlement -> {
            settlement.raiseThreat(40);
            settlement.record(SettlementService.day(event.getWorld()), HistoryEvent.Kind.RAID,
                    "Raiders overran " + settlement.name() + ".");
        });
    }
}
