package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.Bank;
import io.github.skyeberhard.hamletfolk.core.Building;
import io.github.skyeberhard.hamletfolk.core.Commodity;
import io.github.skyeberhard.hamletfolk.core.Donation;
import io.github.skyeberhard.hamletfolk.core.HistoryEvent;
import io.github.skyeberhard.hamletfolk.core.Masterpieces;
import io.github.skyeberhard.hamletfolk.core.Quality;
import io.github.skyeberhard.hamletfolk.core.Reputation;
import io.github.skyeberhard.hamletfolk.core.ResourceMapper;
import io.github.skyeberhard.hamletfolk.core.ResourceType;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.SettlementSimulator;
import io.github.skyeberhard.hamletfolk.core.StoreDisplay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * R4.55: the visible stores. A barrel beside the sign of the village's building that fits a category of the ledger shows that
 * category as items, redrawn from the ledger while a player is near. The ledger is the only truth (docs/VISIBLE_STORES.md): taking
 * from a barrel is a theft that takes the same from the ledger, putting in is a donation, and nothing is ever dropped or piped out.
 * Main thread. Never loads a chunk.
 */
final class StoreService implements Listener {
    private static final double NEAR = 64;
    private static final long EVERY_TICKS = 100;
    private static final long REPLACE_AFTER_MS = 10 * 60 * 1000L;
    private static final long SCAN_EVERY_MS = 60 * 1000L;
    private static final int REACH = 4;

    private final HamletfolkPlugin plugin;
    private final SettlementService service;
    private final NamespacedKey markKey;
    private final NamespacedKey takenKey;
    private final NamespacedKey heirloomKey;
    private final NamespacedKey gradeKey;
    private final Map<String, Location> barrels = new HashMap<>();
    private final Map<String, Long> placedAt = new HashMap<>();
    private final Map<String, Long> scannedAt = new HashMap<>();

    StoreService(HamletfolkPlugin plugin, SettlementService service) {
        this.plugin = plugin;
        this.service = service;
        this.markKey = new NamespacedKey(plugin, "store");
        this.takenKey = new NamespacedKey(plugin, "taken");
        this.heirloomKey = new NamespacedKey(plugin, "heirloom");
        this.gradeKey = new NamespacedKey(plugin, "grade");
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 220L, EVERY_TICKS);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("stores.visible", true);
    }

    // ---------------------------------------------------------------- drawing

    private void tick() {
        if (!enabled()) {
            return;
        }
        try {
            for (Settlement settlement : new ArrayList<>(service.registry().settlements())) {
                try {
                    visit(settlement);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Store pass failed for " + settlement.name(), e);
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Store pass failed", e);
        }
    }

    /** An admin's {@code stores}: draws every store barrel now (loading the chunk of each building) and says where they are. Main thread. */
    List<String> drawNow(Settlement settlement) {
        List<String> out = new ArrayList<>();
        World world = Bukkit.getWorld(settlement.world());
        if (world == null || !service.inScope(world)) {
            out.add(settlement.name() + " is in a world that is not simulated.");
            return out;
        }
        for (ResourceType category : ResourceType.values()) {
            Optional<Building> site = StoreDisplay.site(settlement, category);
            if (site.isEmpty()) {
                continue;
            }
            world.getChunkAt(site.get().x() >> 4, site.get().z() >> 4);
            String key = key(settlement, category);
            placedAt.remove(key);
            scannedAt.remove(key);
            Block barrel = barrelOf(settlement, category, world, site.get());
            if (barrel == null) {
                barrel = place(settlement, category, world, site.get());
            }
            if (barrel == null) {
                out.add(category.name().toLowerCase(Locale.ROOT) + ": no free place by the " + site.get().type().label().toLowerCase(Locale.ROOT));
                continue;
            }
            draw(settlement, category, barrel);
            int bx = barrel.getX() >> 4;
            int bz = barrel.getZ() >> 4;
            world.addPluginChunkTicket(bx, bz, plugin); // (for a minute, so an admin can look at it)
            Bukkit.getScheduler().runTaskLater(plugin, () -> world.removePluginChunkTicket(bx, bz, plugin), 1200L);
            out.add(category.name().toLowerCase(Locale.ROOT) + " store at " + barrel.getX() + " " + barrel.getY() + " " + barrel.getZ());
        }
        if (out.isEmpty()) {
            out.add(settlement.name() + " has no building that holds a store yet.");
        }
        return out;
    }

    private void visit(Settlement settlement) {
        if (settlement.population() == 0 || settlement.isAbandoned() || settlement.buildings().isEmpty()) {
            return;
        }
        World world = Bukkit.getWorld(settlement.world());
        if (world == null || !service.inScope(world)) {
            return;
        }
        for (ResourceType category : ResourceType.values()) {
            Optional<Building> site = StoreDisplay.site(settlement, category);
            if (site.isEmpty() || !aroundLoaded(world, site.get()) || !playerNear(world, site.get())) {
                continue;
            }
            Block barrel = barrelOf(settlement, category, world, site.get());
            if (barrel == null) {
                barrel = place(settlement, category, world, site.get());
            }
            if (barrel != null) {
                draw(settlement, category, barrel);
            }
        }
    }

    private static boolean playerNear(World world, Building at) {
        for (Player player : world.getPlayers()) {
            Location l = player.getLocation();
            if (Math.hypot(l.getX() - at.x(), l.getZ() - at.z()) <= NEAR) {
                return true;
            }
        }
        return false;
    }

    private static String key(Settlement settlement, ResourceType category) {
        return settlement.id() + "|" + category.name();
    }

    /** The barrel marked as this village's store for the category: where it was, else found round the building (at most once a minute). */
    private Block barrelOf(Settlement settlement, ResourceType category, World world, Building site) {
        String key = key(settlement, category);
        Location known = barrels.get(key);
        if (known != null && world.isChunkLoaded(known.getBlockX() >> 4, known.getBlockZ() >> 4) && key.equals(markOf(known.getBlock()))) {
            return known.getBlock();
        }
        long now = System.currentTimeMillis();
        if (now - scannedAt.getOrDefault(key, 0L) < SCAN_EVERY_MS) {
            return null;
        }
        scannedAt.put(key, now);
        for (int x = site.x() - REACH - 2; x <= site.x() + REACH + 2; x++) {
            for (int z = site.z() - REACH - 2; z <= site.z() + REACH + 2; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = site.y() - 4; y <= site.y() + 4; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.BARREL && key.equals(markOf(block))) {
                        barrels.put(key, block.getLocation());
                        return block;
                    }
                }
            }
        }
        barrels.remove(key);
        return null;
    }

    /** The mark on a barrel, or null if it is not one of the village's. */
    private String markOf(Block block) {
        if (block.getType() == Material.BARREL && block.getState(false) instanceof Barrel barrel) {
            return barrel.getPersistentDataContainer().get(markKey, PersistentDataType.STRING);
        }
        return null;
    }

    private Block place(Settlement settlement, ResourceType category, World world, Building site) {
        String key = key(settlement, category);
        long now = System.currentTimeMillis();
        if (now - placedAt.getOrDefault(key, 0L) < REPLACE_AFTER_MS) {
            return null;
        }
        Block at = world.getBlockAt(site.x(), site.y(), site.z());
        for (int reach = 1; reach <= REACH; reach++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != reach) {
                        continue;
                    }
                    for (int dy : new int[] {0, -1, 1}) {
                        Block cell = at.getRelative(dx, dy, dz);
                        if (!world.isChunkLoaded(cell.getX() >> 4, cell.getZ() >> 4)) {
                            continue;
                        }
                        boolean free = (cell.getType().isAir() || (org.bukkit.Tag.REPLACEABLE.isTagged(cell.getType()) && !cell.isLiquid()))
                                && cell.getRelative(0, 1, 0).getType().isAir() && cell.getRelative(0, -1, 0).getType().isSolid()
                                && !nextToDoor(cell) && world.getNearbyEntities(cell.getLocation().add(0.5, 0.5, 0.5), 0.6, 0.6, 0.6).isEmpty();
                        if (!free) {
                            continue;
                        }
                        cell.setType(Material.BARREL, false);
                        if (cell.getState(false) instanceof Barrel) {
                            Barrel state = (Barrel) cell.getState();
                            state.getPersistentDataContainer().set(markKey, PersistentDataType.STRING, key);
                            state.update(true, false);
                        }
                        placedAt.put(key, now);
                        barrels.put(key, cell.getLocation());
                        return cell;
                    }
                }
            }
        }
        placedAt.put(key, now); // nowhere to put it: try again in ten minutes
        return null;
    }

    /** True if a door stands beside the cell (at its level or the one above): a barrel there would block the way in. */
    private static boolean nextToDoor(Block cell) {
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (org.bukkit.Tag.DOORS.isTagged(cell.getRelative(d[0], 0, d[1]).getType())
                    || org.bukkit.Tag.DOORS.isTagged(cell.getRelative(d[0], 1, d[1]).getType())) {
                return true;
            }
        }
        return false;
    }

    /** True if every chunk the barrel might be in (round the building) is loaded, so a missing barrel really is missing. */
    private static boolean aroundLoaded(World world, Building site) {
        int r = REACH + 2;
        return world.isChunkLoaded((site.x() - r) >> 4, (site.z() - r) >> 4) && world.isChunkLoaded((site.x() + r) >> 4, (site.z() - r) >> 4)
                && world.isChunkLoaded((site.x() - r) >> 4, (site.z() + r) >> 4) && world.isChunkLoaded((site.x() + r) >> 4, (site.z() + r) >> 4);
    }

    private void draw(Settlement settlement, ResourceType category, Block block) {
        if (!(block.getState() instanceof Barrel barrel)) {
            return;
        }
        List<StoreDisplay.Shown> shown = StoreDisplay.contents(settlement.ledger(), category);
        Quality.Grade grade = Quality.of(settlement, category); // R4.62
        ItemStack[] want = new ItemStack[barrel.getInventory().getSize()];
        // R4.61: the village's masterpieces stand in the last slots of its goods barrel, and cannot be taken
        List<Masterpieces.Heirloom> heirlooms = category == ResourceType.GOODS ? Masterpieces.heirlooms(settlement) : List.of();
        int reserved = Math.min(heirlooms.size(), Masterpieces.MAX_HEIRLOOMS);
        for (int i = 0; i < reserved; i++) {
            want[want.length - 1 - i] = heirloom(heirlooms.get(i));
        }
        for (int i = 0; i < shown.size() && i < want.length - reserved; i++) {
            Material material = Material.matchMaterial(shown.get(i).material());
            if (material != null) {
                want[i] = graded(new ItemStack(material, shown.get(i).count()), grade);
            }
        }
        ItemStack[] have = barrel.getInventory().getContents();
        boolean same = true;
        for (int i = 0; i < want.length && same; i++) {
            ItemStack a = want[i];
            ItemStack b = i < have.length ? have[i] : null;
            same = (a == null || a.getType().isAir()) ? (b == null || b.getType().isAir()) : sameShown(a, b);
        }
        if (!same) {
            barrel.getInventory().setContents(want);
        }
    }

    /** R4.61: a masterpiece as an item: named for the piece, lore for the maker, marked so it cannot be taken. */
    private ItemStack heirloom(Masterpieces.Heirloom heirloom) {
        Material material = Material.matchMaterial(heirloom.material());
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(heirloom.title(), NamedTextColor.GOLD).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Made by " + heirloom.maker() + ", master " + heirloom.trade(), NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                Component.text("Kept by the village since day " + heirloom.day(), NamedTextColor.DARK_GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(heirloomKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** R4.62: the items of a category made by skilled hands carry the quality and the maker; a plain village's are left as they are. */
    private ItemStack graded(ItemStack item, Quality.Grade grade) {
        if (grade.plain()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        NamedTextColor colour = grade.level() >= 5 ? NamedTextColor.GOLD : grade.level() == 4 ? NamedTextColor.AQUA : NamedTextColor.GREEN;
        meta.itemName(Component.text(grade.label() + " ", colour).append(Component.translatable(item.getType().translationKey())));
        meta.lore(List.of(Component.text("Made under " + grade.maker(), NamedTextColor.GRAY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        if (grade.level() >= 5) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.getPersistentDataContainer().set(gradeKey, PersistentDataType.STRING, grade.label() + "|" + grade.maker());
        item.setItemMeta(meta);
        return item;
    }

    /** Whether the barrel already shows this: compares what and how many, and the grade's mark (a rebuilt name would not compare equal after the barrel saved it). */
    private boolean sameShown(ItemStack want, ItemStack have) {
        if (have == null || want.getType() != have.getType() || want.getAmount() != have.getAmount()) {
            return false;
        }
        if (isHeirloom(want)) {
            return want.equals(have);
        }
        return java.util.Objects.equals(gradeOf(want), gradeOf(have));
    }

    private String gradeOf(ItemStack item) {
        return item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer().get(gradeKey, PersistentDataType.STRING) : null;
    }

    /** R4.63: an item the village gave a player: marked as from its stores (so it cannot be handed back for a reward), named and lored if a master made it. */
    ItemStack gift(Settlement settlement, io.github.skyeberhard.hamletfolk.core.Gifts.Gift gift) {
        Material material = Material.matchMaterial(gift.material());
        ItemStack item = new ItemStack(material == null ? Material.BREAD : material, gift.count());
        ItemMeta meta = item.getItemMeta();
        if (gift.name() != null) {
            meta.itemName(Component.text(gift.name(), NamedTextColor.GOLD));
            meta.lore(List.of(Component.text(gift.lore(), NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        }
        meta.getPersistentDataContainer().set(takenKey, PersistentDataType.STRING, settlement.id().toString());
        item.setItemMeta(meta);
        return item;
    }

    private boolean isHeirloom(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(heirloomKey, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------- the barrels are the village's

    private Optional<Settlement> villageOf(String mark) {
        if (mark == null) {
            return Optional.empty();
        }
        try {
            return service.registry().get(UUID.fromString(mark.substring(0, mark.indexOf('|'))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static ResourceType categoryOf(String mark) {
        return ResourceType.valueOf(mark.substring(mark.indexOf('|') + 1));
    }

    /** The mark of a store inventory, or null. */
    private String markOf(Inventory inventory) {
        if (inventory != null && inventory.getHolder(false) instanceof Barrel barrel) {
            return barrel.getPersistentDataContainer().get(markKey, PersistentDataType.STRING);
        }
        return null;
    }

    /** True for a stack taken from a store, which can only be put back. */
    boolean isTaken(ItemStack item) {
        return takenFrom(item) != null;
    }

    /** The id of the village a stack was taken from, or null if it was not taken from a store. */
    private String takenFrom(ItemStack item) {
        return item != null && item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer().get(takenKey, PersistentDataType.STRING) : null;
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        String mark = markOf(event.getInventory());
        if (mark != null) {
            event.getPlayer().sendActionBar(Component.text("The village's stores: taking from them is theft; put in to donate.", NamedTextColor.GRAY));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        String mark = markOf(top);
        if (mark == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean inTop = event.getClickedInventory() == top;
        InventoryAction action = event.getAction();
        boolean touchesTop = inTop || action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.COLLECT_TO_CURSOR
                || event.getClick() == ClickType.DOUBLE_CLICK;
        if (!touchesTop) {
            return; // moving things about in their own pockets
        }
        event.setCancelled(true);
        if (isHeirloom(event.getCurrentItem())) {
            player.sendMessage(Component.text("A masterpiece the village keeps. It is not for taking.", NamedTextColor.GOLD));
            return;
        }
        Optional<Settlement> village = villageOf(mark);
        if (village.isEmpty()) {
            return;
        }
        Settlement settlement = village.get();
        ResourceType category = categoryOf(mark);
        if (inTop) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                deposit(player, settlement, category, cursor, stack -> player.setItemOnCursor(stack));
            } else if (event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()
                    && (action == InventoryAction.PICKUP_ALL || action == InventoryAction.PICKUP_HALF || action == InventoryAction.PICKUP_ONE
                            || action == InventoryAction.PICKUP_SOME || action == InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
                steal(player, settlement, category, event.getCurrentItem(), top, event.getSlot());
            }
        } else if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY && event.getCurrentItem() != null) {
            ItemStack item = event.getCurrentItem();
            int slot = event.getSlot();
            Inventory own = event.getClickedInventory();
            deposit(player, settlement, category, item, rest -> own.setItem(slot, rest));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (markOf(top) != null && event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (markOf(event.getSource()) != null || markOf(event.getDestination()) != null) {
            event.setCancelled(true); // a hopper could not be blamed on anyone
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        String mark = markOf(event.getBlock());
        if (mark == null) {
            return;
        }
        event.setDropItems(false);
        if (event.getBlock().getState(false) instanceof Barrel barrel) {
            barrel.getInventory().clear(); // (a picture: nothing of it exists to drop)
        }
        villageOf(mark).ifPresent(settlement -> {
            long day = settlement.effectiveDay(SettlementService.day(event.getBlock().getWorld()));
            settlement.recordQuiet(day, HistoryEvent.Kind.THEFT, event.getPlayer().getName() + " broke the " + categoryOf(mark).name().toLowerCase(Locale.ROOT)
                    + " store's barrel.", 3);
            settlement.adjustReputation(event.getPlayer().getUniqueId(), -StoreDisplay.VANDALISM_LOSS);
            plugin.requestSave();
        });
        placedAt.remove(keyOf(mark));
        scannedAt.remove(keyOf(mark));
        barrels.remove(keyOf(mark));
    }

    private static String keyOf(String mark) {
        return mark;
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> markOf(block) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChange(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (markOf(event.getBlock()) != null) {
            event.setCancelled(true); // (a wither, say: its contents would drop)
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> markOf(block) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPush(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> markOf(block) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPull(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> markOf(block) != null)) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- theft and donation

    /** A player lifts a stack out of a store: the ledger loses its worth, the village remembers who did it. */
    private void steal(Player player, Settlement settlement, ResourceType category, ItemStack shown, Inventory top, int slot) {
        String material = shown.getType().getKey().getKey();
        int count = shown.getAmount();
        int units = StoreDisplay.take(settlement, material, count);
        if (units == 0) {
            player.sendMessage(Component.text("The stores have changed; look again.", NamedTextColor.GRAY));
            return;
        }
        ItemStack taken = new ItemStack(shown.getType(), count);
        ItemMeta meta = taken.getItemMeta();
        meta.getPersistentDataContainer().set(takenKey, PersistentDataType.STRING, settlement.id().toString());
        taken.setItemMeta(meta);
        top.setItem(slot, null);
        player.getInventory().addItem(taken).values().forEach(over -> player.getWorld().dropItem(player.getLocation(), over));
        long day = settlement.effectiveDay(SettlementService.day(player.getWorld()));
        settlement.recordQuiet(day, HistoryEvent.Kind.THEFT, player.getName() + " took from the " + category.name().toLowerCase(Locale.ROOT) + " stores.", 3);
        StoreDisplay.noteTheft(settlement, player.getUniqueId(), day);
        int loss = StoreDisplay.theftLoss(category, units);
        settlement.adjustReputation(player.getUniqueId(), -loss);
        player.sendMessage(Component.text("You took " + count + " " + material.replace('_', ' ') + " from the stores of " + settlement.name()
                + ". That is theft (standing -" + loss + ").", NamedTextColor.RED));
        plugin.requestSave();
    }

    /** A player puts a stack into a store: a donation, or (for what was taken from the stores) a return that earns nothing. */
    private void deposit(Player player, Settlement settlement, ResourceType category, ItemStack stack, java.util.function.Consumer<ItemStack> leave) {
        String material = stack.getType().getKey().getKey();
        String itemName = material.replace('_', ' ');
        if (ResourceMapper.isCurrency(material)) {
            player.sendMessage(Component.text("Emeralds go to the treasury, not the stores.", NamedTextColor.GRAY));
            return;
        }
        double wear = condition(stack);
        Optional<ResourceMapper.Value> value = ResourceMapper.value(material, wear);
        if (value.isEmpty() || value.get().type() != category) {
            player.sendMessage(Component.text("This is the " + category.name().toLowerCase(Locale.ROOT) + " store; "
                    + (value.isEmpty() ? settlement.name() + " has no use for " + itemName + "." : itemName + " does not belong here."), NamedTextColor.GRAY));
            return;
        }
        String from = takenFrom(stack);
        boolean returning = from != null;
        if (returning && !from.equals(settlement.id().toString())) {
            player.sendMessage(Component.text("That was taken from another village's stores.", NamedTextColor.GRAY));
            return;
        }
        if (!returning && StoreDisplay.recentThief(settlement, player.getUniqueId(), settlement.effectiveDay(SettlementService.day(player.getWorld())))) {
            player.sendMessage(Component.text(settlement.name() + " will not take gifts from you yet; you took from their stores.", NamedTextColor.GRAY));
            return;
        }
        if (!returning) {
            Location at = player.getLocation();
            Bank.Access counter = Bank.check(settlement, at.getBlockX(), at.getBlockY(), at.getBlockZ());
            if (!counter.allowed()) {
                Building bank = counter.nearest().orElseThrow();
                player.sendMessage(Component.text("Donations are taken at the treasury of " + settlement.name() + ", about "
                        + counter.distance() + " blocks away, at " + bank.x() + ", " + bank.y() + ", " + bank.z() + ".", NamedTextColor.GRAY));
                return;
            }
        }
        int offered = stack.getAmount();
        Donation.Plan plan = Donation.plan(settlement, value.get(), offered);
        if (plan.units() == 0) {
            player.sendMessage(Component.text(plan.limitedByRoom() ? "The stores are full; nothing taken."
                    : offered + " " + itemName + " is worth nothing to " + settlement.name() + " as it is.", NamedTextColor.GRAY));
            return;
        }
        int amount = plan.items();
        int credited = plan.units();
        settlement.ledger().add(ResourceMapper.commodity(material).orElse(Commodity.plainOf(value.get().type())), credited);
        ItemStack rest = amount >= offered ? null : stack.clone();
        if (rest != null) {
            rest.setAmount(offered - amount);
        }
        leave.accept(rest);
        long day = settlement.effectiveDay(SettlementService.day(player.getWorld()));
        if (returning) {
            player.sendMessage(Component.text("You put " + amount + " " + itemName + " back in the stores of " + settlement.name() + ".", NamedTextColor.GREEN));
            plugin.requestSave();
            return;
        }
        settlement.recordDonation(day, player.getName(), amount, itemName);
        settlement.adjustReputation(player.getUniqueId(), Reputation.donationGain(value.get().type(), credited, false));
        int toolCap = SettlementSimulator.maxPayout(material, wear);
        if (toolCap != Integer.MAX_VALUE) {
            toolCap = (int) Math.min(Integer.MAX_VALUE, (long) toolCap * amount);
        }
        int thanks = SettlementSimulator.thanksFor(settlement, value.get().type(), credited, toolCap);
        int paid = service.fulfilRequest(settlement, value.get().type(), credited, day, player.getName(), toolCap);
        settlement.adjustReputation(player.getUniqueId(), Reputation.requestGain(paid + thanks));
        for (int left = paid; left > 0; left -= 64) {
            player.getInventory().addItem(new ItemStack(Material.EMERALD, Math.min(64, left)))
                    .values().forEach(over -> player.getWorld().dropItem(player.getLocation(), over));
        }
        player.sendMessage(Component.text("The people of " + settlement.name() + " thank you. (+" + credited + " "
                + value.get().type().name().toLowerCase(Locale.ROOT) + (paid > 0 ? "; they pay you " + paid + " emeralds for it" : "") + ")",
                NamedTextColor.GREEN));
        plugin.requestSave();
    }

    private static double condition(ItemStack item) {
        int max = item.getType().getMaxDurability();
        if (item.getItemMeta() instanceof Damageable damageable) {
            if (damageable.hasMaxDamage()) {
                max = damageable.getMaxDamage();
            }
            if (max > 0) {
                return 1.0 - (double) damageable.getDamage() / max;
            }
        }
        return 1.0;
    }
}
