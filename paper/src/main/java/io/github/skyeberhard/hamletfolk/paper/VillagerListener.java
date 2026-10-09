package io.github.skyeberhard.hamletfolk.paper;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import io.github.skyeberhard.hamletfolk.core.Dialogue;
import io.github.skyeberhard.hamletfolk.core.HistoryEvent;
import io.github.skyeberhard.hamletfolk.core.Occupation;
import io.github.skyeberhard.hamletfolk.core.Planner;
import io.github.skyeberhard.hamletfolk.core.Reputation;
import io.github.skyeberhard.hamletfolk.core.Resident;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Villager;
import org.bukkit.entity.ZombieVillager;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.MerchantInventory;

/** Keeps resident records in step with villager entities, and lets players talk to them. */
final class VillagerListener implements Listener {
    private final HamletfolkPlugin plugin;
    private final SettlementService service;
    private final Random chatter = new Random();

    VillagerListener(HamletfolkPlugin plugin, SettlementService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            // Defer a tick: the entity is mid-insertion and shouldn't be modified yet.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (villager.isValid()) {
                    service.track(villager);
                }
            });
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreed(EntityBreedEvent event) {
        if (event.getEntity() instanceof Villager child && service.inScope(child.getWorld())) {
            service.expectBirth(child.getUniqueId(), event.getMother().getUniqueId(), event.getFather().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCareerChange(VillagerCareerChangeEvent event) {
        // R4.3: seeds an unemployed resident only; never replaces an occupation the simulation gave.
        service.registry().resident(event.getEntity().getUniqueId())
                .filter(resident -> service.registry().settlementOf(resident.id()).filter(s -> s.isPinned(resident.id())).isEmpty()) // R1.31
                .ifPresent(resident -> resident.seedOccupation(Occupation.fromVanillaKey(event.getProfession().getKey().getKey())));
    }

    /** R3.1: a villager's trade window is opening; set its prices from the settlement's stores. */
    @EventHandler(ignoreCancelled = true)
    public void onOpenTrade(InventoryOpenEvent event) {
        if (event.getInventory() instanceof MerchantInventory trade && trade.getMerchant() instanceof Villager villager
                && service.inScope(villager.getWorld())) {
            service.applyTradePrices(villager, event.getPlayer().getUniqueId());
        }
    }

    /** R3.14: refuse a sale the stores can no longer cover (several offers can share one stock). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTradeStock(PlayerTradeEvent event) {
        if (!(event.getVillager() instanceof Villager villager) || !service.inScope(villager.getWorld())) {
            return;
        }
        MerchantRecipe recipe = event.getTrade();
        ItemStack given = recipe.getAdjustedIngredient1();
        if (given != null && !service.storesCover(villager, given.getType().getKey().getKey(),
                recipe.getResult().getType().getKey().getKey(), recipe.getResult().getAmount())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "The village has no more of that to spare right now.", NamedTextColor.GRAY));
        }
    }

    /** R3.2: a completed trade moves goods into or out of the settlement's stores. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTrade(PlayerTradeEvent event) {
        if (!(event.getVillager() instanceof Villager villager) || !service.inScope(villager.getWorld())) {
            return;
        }
        // The game fires this just before it takes payment; in the rare case it then refuses, the stores have moved anyway.
        MerchantRecipe recipe = event.getTrade();
        ItemStack given = recipe.getAdjustedIngredient1(); // what the player actually hands over, price changes included
        if (given == null) {
            return;
        }
        service.applyTrade(villager, given.getType().getKey().getKey(), given.getAmount(),
                recipe.getResult().getType().getKey().getKey(), recipe.getResult().getAmount());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTalk(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.hasPermission("hamletfolk.use") || !service.inScope(villager.getWorld())
                || service.isIgnored(villager)) { // R1.30: an exempt villager is left to vanilla
            return; // A normal right-click still opens trading (its prices are set when the window opens, below).
        }
        event.setCancelled(true);

        Resident resident = service.track(villager);
        if (resident == null) {
            return; // removed: they had died of old age (R4.15)
        }
        Settlement settlement = service.registry().settlementOf(resident.id()).orElseThrow();
        service.simulate(settlement);
        if (settlement.resident(resident.id()).isEmpty()) {
            return; // simulating just now showed they died of old age (R4.15); their villager was removed
        }
        long day = settlement.effectiveDay(SettlementService.day(villager.getWorld())); // R1.23
        Planner.visited(settlement, day); // R8.1: someone is minding the village

        String title = resident.adult() ? resident.occupation().title() : "child";
        player.sendMessage(Component.text(resident.fullName(), NamedTextColor.GOLD)
                .append(Component.text(" · " + title + " of " + settlement.name() + " · " + mood(resident),
                        NamedTextColor.GRAY)));
        String line = Dialogue.greeting(resident, settlement, player.getUniqueId(), player.getName()) + " "
                + Dialogue.speak(resident, settlement, day, chatter, ambientAt(villager)); // R4.31
        player.sendMessage(Component.text("\"" + line + "\"", NamedTextColor.WHITE, TextDecoration.ITALIC));
        resident.recordConversation(player.getUniqueId());
        sayGoodbyeWhenTheyWalkAway(player, villager, resident, settlement);
    }

    // ----- R4.31: the weather, and goodbyes -----

    /** What the sky and the clock look like where the villager stands, for a remark on the weather. */
    private static Dialogue.Ambient ambientAt(Villager villager) {
        org.bukkit.World world = villager.getWorld();
        if (world.getEnvironment() != org.bukkit.World.Environment.NORMAL) {
            return null; // no weather or day and night in the Nether or the End
        }
        String sky = world.isThundering() ? "thunder rolling" : world.hasStorm() ? "rain coming down" : "a clear sky";
        long ticks = world.getTime(); // 0 is six in the morning
        String time = ticks < 6000 || ticks >= 23000 ? "morning" : ticks < 12000 ? "afternoon" : ticks < 13800 ? "evening" : "night";
        return new Dialogue.Ambient(sky, time);
    }

    /** Players who are in a conversation, and the check that says goodbye when they walk away. */
    private final java.util.Map<UUID, org.bukkit.scheduler.BukkitTask> goodbyes = new java.util.HashMap<>();
    /** How far (blocks) a player may be before the conversation is over, and the longest (ticks) one is waited on. */
    private static final double WALK_AWAY_BLOCKS = 8;
    private static final int GOODBYE_PATIENCE_TICKS = 20 * 45;

    /**
     * R4.31: once a player has talked to a villager, the villager says goodbye in their voice when the player walks more than
     * eight blocks away (or leaves). Only the latest conversation of each player is followed, and none for longer than 45 seconds.
     */
    private void sayGoodbyeWhenTheyWalkAway(Player player, Villager villager, Resident resident, Settlement settlement) {
        org.bukkit.scheduler.BukkitTask earlier = goodbyes.remove(player.getUniqueId());
        if (earlier != null) {
            earlier.cancel();
        }
        int[] waited = {0};
        org.bukkit.scheduler.BukkitTask[] self = new org.bukkit.scheduler.BukkitTask[1];
        self[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            waited[0] += 10;
            boolean gone = !player.isOnline() || !villager.isValid() || !player.getWorld().equals(villager.getWorld())
                    || player.getLocation().distanceSquared(villager.getLocation()) > WALK_AWAY_BLOCKS * WALK_AWAY_BLOCKS;
            if (gone || waited[0] >= GOODBYE_PATIENCE_TICKS) {
                self[0].cancel();
                goodbyes.remove(player.getUniqueId(), self[0]);
                if (gone && player.isOnline() && villager.isValid() && player.getWorld().equals(villager.getWorld())
                        && settlement.resident(resident.id()).isPresent()) {
                    player.sendMessage(Component.text(resident.givenName() + ": ", NamedTextColor.GOLD).append(Component.text(
                            "\"" + Dialogue.farewell(resident, settlement, player.getName(), chatter) + "\"", NamedTextColor.WHITE,
                            TextDecoration.ITALIC)));
                }
            }
        }, 10, 10);
        goodbyes.put(player.getUniqueId(), self[0]);
    }

    /** R5.9: a village's golem that goes for any reason but being unloaded (killed, despawned, removed) is lost. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGolemRemoved(org.bukkit.event.entity.EntityRemoveEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.IronGolem golem
                && event.getCause() != org.bukkit.event.entity.EntityRemoveEvent.Cause.UNLOAD
                && event.getCause() != org.bukkit.event.entity.EntityRemoveEvent.Cause.PLAYER_QUIT) {
            service.golemLost(golem);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof ZombieVillager zombie) {
            onZombieDeath(zombie);
            return;
        }

        if (!(event.getEntity() instanceof Villager villager) || !service.inScope(villager.getWorld())) {
            return;
        }
        Settlement settlement = service.registry().settlementOf(villager.getUniqueId()).orElse(null);
        Resident resident = service.registry().remove(villager.getUniqueId()).orElse(null);
        if (settlement == null || resident == null) {
            return;
        }
        Entity killer = killerOf(villager);
        if (killer instanceof Monster || killer instanceof Raider) {
            settlement.raiseThreat(15);
            settlement.recordIncident(settlement.effectiveDay(SettlementService.day(villager.getWorld()))); // R5.5
        }
        Player culprit = killer instanceof Player player ? player : villager.getKiller(); // R3.4: or whoever last hurt it
        if (culprit != null) {
            settlement.adjustReputation(culprit.getUniqueId(), -Reputation.KILLING);
        }
        settlement.record(SettlementService.day(villager.getWorld()), HistoryEvent.Kind.DEATH,
                describe(resident) + " " + causeOfDeath(villager, killer) + ".");
    }

    /** A zombie villager that used to be a resident died, so they can no longer be cured. */
    private void onZombieDeath(ZombieVillager zombie) {
        Settlement settlement = service.registry().settlementOfTurned(zombie.getUniqueId()).orElse(null);
        Resident resident = service.registry().forgetTurned(zombie.getUniqueId()).orElse(null);
        if (settlement != null && resident != null) {
            settlement.record(SettlementService.day(zombie.getWorld()), HistoryEvent.Kind.DEATH,
                    "The zombie that was once " + resident.fullName() + " was laid to rest.");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTransform(EntityTransformEvent event) {
        if (event.getEntity() instanceof ZombieVillager zombie
                && event.getTransformReason() == EntityTransformEvent.TransformReason.CURED
                && event.getTransformedEntity() instanceof Villager cured) {
            onCure(zombie, cured);
            return;
        }
        if (!(event.getEntity() instanceof Villager villager) || !service.inScope(villager.getWorld())) {
            return;
        }
        UUID id = villager.getUniqueId();
        Settlement settlement = service.registry().settlementOf(id).orElse(null);
        if (settlement == null) {
            return;
        }
        long day = SettlementService.day(villager.getWorld());
        if (event.getTransformReason() == EntityTransformEvent.TransformReason.INFECTION) {
            // Keep them on record under the zombie's id so a cure can bring them back (R1.2).
            Resident resident = service.registry().turn(id, event.getTransformedEntity().getUniqueId()).orElseThrow();
            settlement.raiseThreat(20);
            settlement.recordIncident(settlement.effectiveDay(day)); // R5.5
            settlement.record(day, HistoryEvent.Kind.DEATH, describe(resident) + " was turned by zombies.");
            return;
        }
        Resident resident = service.registry().remove(id).orElseThrow();
        if (event.getTransformReason() == EntityTransformEvent.TransformReason.LIGHTNING) {
            settlement.record(day, HistoryEvent.Kind.DEATH, describe(resident) + " was struck by lightning and became a witch.");
        } else {
            settlement.record(day, HistoryEvent.Kind.DEATH, describe(resident) + " was lost to strange magic.");
        }
    }

    /**
     * Runs before the cured villager is added to the world, so by the time it is tracked it
     * already has its old identity and isn't enrolled as a newcomer.
     */
    private void onCure(ZombieVillager zombie, Villager cured) {
        Settlement settlement = service.registry().settlementOfTurned(zombie.getUniqueId()).orElse(null);
        Resident resident = service.registry().cure(zombie.getUniqueId(), cured.getUniqueId()).orElse(null);
        if (settlement == null || resident == null) {
            return; // A zombie villager we never knew as a villager; it arrives as a newcomer.
        }
        OfflinePlayer healer = zombie.getConversionPlayer();
        String by = healer != null && healer.getName() != null ? " by " + healer.getName() : "";
        settlement.record(SettlementService.day(zombie.getWorld()), HistoryEvent.Kind.CURE,
                resident.fullName() + " was cured" + by + " and came home to " + settlement.name() + ".");
    }

    private static String describe(Resident resident) {
        return resident.adult()
                ? resident.fullName() + ", the " + resident.occupation().title() + ","
                : "Young " + resident.fullName();
    }

    private static String mood(Resident resident) {
        int mood = resident.needs().mood();
        if (mood >= 75) {
            return "content";
        }
        if (mood >= 50) {
            return "getting by";
        }
        if (mood >= 25) {
            return "troubled";
        }
        return "desperate";
    }

    private static Entity killerOf(LivingEntity victim) {
        if (!(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    private static String causeOfDeath(LivingEntity victim, Entity killer) {
        if (killer instanceof Player player) {
            return "was killed by " + player.getName();
        }
        if (killer != null) {
            String type = killer.getType().getKey().getKey().replace('_', ' ');
            return "was killed by " + ("aeiou".indexOf(type.charAt(0)) >= 0 ? "an " : "a ") + type;
        }
        EntityDamageEvent last = victim.getLastDamageCause();
        if (last == null) {
            return "passed away";
        }
        return switch (last.getCause()) {
            case FALL -> "fell to their death";
            case DROWNING -> "drowned";
            case FIRE, FIRE_TICK, LAVA -> "burned to death";
            case LIGHTNING -> "was struck by lightning";
            case SUFFOCATION -> "suffocated";
            case STARVATION -> "starved";
            default -> "passed away";
        };
    }
}
