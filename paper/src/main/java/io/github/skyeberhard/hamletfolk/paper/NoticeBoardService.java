package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.BuildingType;
import io.github.skyeberhard.hamletfolk.core.NoticeBoard;
import io.github.skyeberhard.hamletfolk.core.Rect;
import io.github.skyeberhard.hamletfolk.core.Settlement;
import io.github.skyeberhard.hamletfolk.core.VillagePlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lectern;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * R4.53: a notice board by the bell of each village that has a town square: a lectern holding a book of what the village asks for, what it is
 * building and its latest news ({@link NoticeBoard}, in core). Only while a player is near the square, and only in loaded ground: nothing is
 * loaded or generated for it. The lectern is placed beside the bell once (and again, after ten minutes, if it is taken away); the book is
 * rewritten only when its words change. A lectern a player placed is never touched: ours is the one holding a book by "Notice Board" with the
 * village's name as its author.
 */
final class NoticeBoardService {
    private static final long PERIOD_TICKS = 200; // ten seconds
    /** Blocks from the square within which a player makes the board worth keeping up to date. */
    private static final double NEAR = 64;
    /** How long after it was placed a missing board is put back, so a player who removed it is not fought. */
    private static final long REPLACE_AFTER_MS = 10 * 60_000L;
    /** Rate of looking for a board that is not where it was (a scan of the square). */
    private static final long SCAN_EVERY_MS = 60_000L;

    private final HamletfolkPlugin plugin;
    private final SettlementService service;
    /** Where each village's board is, as far as this run knows. */
    private final Map<UUID, Location> boards = new HashMap<>();
    /** What its book said last, so it is rewritten only when it changes. */
    private final Map<UUID, Integer> said = new HashMap<>();
    private final Map<UUID, Long> placedAt = new HashMap<>();
    private final Map<UUID, Long> scannedAt = new HashMap<>();

    NoticeBoardService(HamletfolkPlugin plugin, SettlementService service) {
        this.plugin = plugin;
        this.service = service;
    }

    void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 400L, PERIOD_TICKS);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("noticeboard.enabled", true)) {
            return;
        }
        for (Settlement settlement : new ArrayList<>(service.registry().settlements())) {
            try {
                visit(settlement);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "The notice board of " + settlement.name() + " failed", e);
            }
        }
    }

    /**
     * An admin's {@code noticeboard}: puts the village's board by its bell (loading the square's chunk if it is not loaded), writes the book now
     * whether or not a player is near, and says where it is. Main thread.
     */
    String placeNow(Settlement settlement) {
        VillagePlan plan = settlement.plan();
        if (plan == null || plan.square() == null || settlement.buildingCount(BuildingType.SQUARE) == 0) {
            return settlement.name() + " has no town square yet, so no bell to put a notice board by.";
        }
        World world = Bukkit.getWorld(settlement.world());
        if (world == null || !service.inScope(world)) {
            return settlement.name() + " is in a world that is not simulated.";
        }
        Rect square = plan.square();
        // (an admin asked: every chunk the square and its surroundings stand on is loaded, and kept for a minute, so the bell can be found and the board seen)
        Rect wide = square.inflated(3);
        List<int[]> held = new ArrayList<>();
        for (int cx = wide.x() >> 4; cx <= wide.maxX() >> 4; cx++) {
            for (int cz = wide.z() >> 4; cz <= wide.maxZ() >> 4; cz++) {
                world.getChunkAt(cx, cz);
                world.addPluginChunkTicket(cx, cz, plugin);
                held.add(new int[] {cx, cz});
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> held.forEach(c -> world.removePluginChunkTicket(c[0], c[1], plugin)), 60 * 20L);
        placedAt.remove(settlement.id());
        scannedAt.remove(settlement.id());
        Block board = boardOf(settlement, world, square);
        if (board == null) {
            board = place(settlement, world, square);
        }
        if (board == null) {
            return "Could not find a bell, or a free place beside it, in " + settlement.name() + "'s square.";
        }
        said.remove(settlement.id());
        write(settlement, board);
        return "The notice board of " + settlement.name() + " is at " + board.getX() + " " + board.getY() + " " + board.getZ() + ".";
    }

    private void visit(Settlement settlement) {
        VillagePlan plan = settlement.plan();
        if (plan == null || plan.square() == null || settlement.population() == 0 || settlement.isAbandoned()
                || settlement.buildingCount(BuildingType.SQUARE) == 0) {
            return;
        }
        World world = Bukkit.getWorld(settlement.world());
        Rect square = plan.square();
        if (world == null || !service.inScope(world) || !world.isChunkLoaded(square.centerX() >> 4, square.centerZ() >> 4)
                || !playerNear(world, square)) {
            return;
        }
        Block board = boardOf(settlement, world, square);
        if (board == null) {
            board = place(settlement, world, square);
            if (board == null) {
                return;
            }
        }
        write(settlement, board);
    }

    private static boolean playerNear(World world, Rect square) {
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (Math.hypot(at.getX() - square.centerX(), at.getZ() - square.centerZ()) <= NEAR) {
                return true;
            }
        }
        return false;
    }

    /** The village's notice board: where it was last, else found by scanning round the square (at most once a minute). */
    private Block boardOf(Settlement settlement, World world, Rect square) {
        Location known = boards.get(settlement.id());
        if (known != null && world.isChunkLoaded(known.getBlockX() >> 4, known.getBlockZ() >> 4) && known.getBlock().getType() == Material.LECTERN) {
            return known.getBlock();
        }
        long now = System.currentTimeMillis();
        if (now - scannedAt.getOrDefault(settlement.id(), 0L) < SCAN_EVERY_MS) {
            return null;
        }
        scannedAt.put(settlement.id(), now);
        Rect around = square.inflated(4);
        int top = world.getHighestBlockYAt(square.centerX(), square.centerZ());
        for (int x = around.x(); x <= around.maxX(); x++) {
            for (int z = around.z(); z <= around.maxZ(); z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = top - 5; y <= top + 5; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.LECTERN && isOurs(block, settlement)) {
                        boards.put(settlement.id(), block.getLocation());
                        return block;
                    }
                }
            }
        }
        boards.remove(settlement.id());
        return null;
    }

    /** True if the lectern holds our book: a book by "Notice Board" whose author is the village. */
    private static boolean isOurs(Block block, Settlement settlement) {
        return isNoticeBoard(block);
    }

    /** True if the block is a lectern holding a notice board book (titled "Notice Board"; the author is the village, which may have been renamed since). */
    static boolean isNoticeBoard(Block block) {
        if (block.getType() == Material.LECTERN && block.getState(false) instanceof Lectern lectern) {
            ItemStack item = lectern.getInventory().getItem(0);
            if (item != null && item.getItemMeta() instanceof BookMeta meta && meta.hasTitle()) {
                return "Notice Board".equals(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.title()));
            }
        }
        return false;
    }

    /** Puts a lectern beside the bell, facing it, if there is a bell and a free place (and it was not placed lately). */
    private Block place(Settlement settlement, World world, Rect square) {
        long now = System.currentTimeMillis();
        if (now - placedAt.getOrDefault(settlement.id(), 0L) < REPLACE_AFTER_MS) {
            return null;
        }
        Block bell = findBell(world, settlement, square);
        if (bell == null) {
            return null;
        }
        // the nearest free place to the bell, ring by ring out to five blocks (a bell is often boxed in by its gazebo), at its level or a block either way
        for (int reach = 1; reach <= 5; reach++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != reach) {
                        continue;
                    }
                    for (int dy : new int[] {0, 1, -1}) {
                        Block cell = bell.getRelative(dx, dy, dz);
                        if (!world.isChunkLoaded(cell.getX() >> 4, cell.getZ() >> 4)) {
                            continue;
                        }
                        Block above = cell.getRelative(0, 1, 0);
                        Block below = cell.getRelative(0, -1, 0);
                        boolean free = (cell.getType().isAir() || org.bukkit.Tag.REPLACEABLE.isTagged(cell.getType())) && above.getType().isAir();
                        if (!free || !below.getType().isSolid()) {
                            continue;
                        }
                        cell.setType(Material.LECTERN, false);
                        if (cell.getBlockData() instanceof org.bukkit.block.data.type.Lectern data) {
                            data.setFacing(towards(cell, bell));
                            cell.setBlockData(data, false);
                        }
                        placedAt.put(settlement.id(), now);
                        boards.put(settlement.id(), cell.getLocation());
                        said.remove(settlement.id());
                        return cell;
                    }
                }
            }
        }
        return null;
    }

    private static BlockFace towards(Block from, Block to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? BlockFace.EAST : BlockFace.WEST) : (dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
    }

    /**
     * The bell of the town centre: round where the square's building is registered (its place, and a few blocks of height either way, as the
     * bell stands under a roof), else anywhere in the planned square. Only loaded ground is looked at.
     */
    private static Block findBell(World world, Settlement settlement, Rect square) {
        Rect area = square.inflated(3);
        int low;
        int high;
        var registered = settlement.buildings().stream().filter(b -> b.type() == BuildingType.SQUARE).findFirst();
        if (registered.isPresent()) {
            low = registered.get().y() - 3;
            high = registered.get().y() + 12;
        } else {
            int top = world.getHighestBlockYAt(square.centerX(), square.centerZ());
            low = top - 12;
            high = top + 3;
        }
        for (int x = area.x(); x <= area.maxX(); x++) {
            for (int z = area.z(); z <= area.maxZ(); z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = low; y <= high; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.BELL) {
                        return block;
                    }
                }
            }
        }
        return null;
    }

    /** Puts the village's book in the lectern if its words changed or the book was taken. */
    private void write(Settlement settlement, Block board) {
        List<String> pages = NoticeBoard.pages(settlement);
        int hash = pages.hashCode();
        BlockState state = board.getState();
        if (!(state instanceof Lectern lectern)) {
            return;
        }
        ItemStack present = lectern.getInventory().getItem(0);
        if (present != null && !isNoticeBoard(board)) {
            return; // a player put their own book there: leave it
        }
        if (present != null && said.getOrDefault(settlement.id(), 0) == hash) {
            return; // up to date
        }
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.title(Component.text("Notice Board"));
        meta.author(Component.text(settlement.name()));
        List<Component> text = new ArrayList<>();
        pages.forEach(page -> text.add(Component.text(page)));
        meta.pages(text);
        book.setItemMeta(meta);
        lectern.getSnapshotInventory().setItem(0, book);
        lectern.update(true, false);
        if (board.getBlockData() instanceof org.bukkit.block.data.type.Lectern data && !data.hasBook()) {
            data.setHasBook(true);
            board.setBlockData(data, false);
        }
        said.put(settlement.id(), hash);
    }
}
