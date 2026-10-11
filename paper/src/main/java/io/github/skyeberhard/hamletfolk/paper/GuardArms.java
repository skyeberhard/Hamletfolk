package io.github.skyeberhard.hamletfolk.paper;

import io.github.skyeberhard.hamletfolk.core.GuardDuty;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * R9.7: the sword a guard on duty carries, so a player can see who the guards are. It matches the best tools in the village's
 * stores ({@link GuardDuty#blade}) and is held in the villager's main hand; it is marked so only our own is ever taken away
 * (a villager some other plugin or a player gave an item is left alone), it never drops, and it is taken away again whenever the
 * guard stops being on duty, the guard behaviour is switched off, the module goes off or the server stops, and from anything a
 * guard turns into. If one is saved into the world anyway (a crash), the next time that villager loads it is cleared. A
 * villager holding a sword is still a vanilla villager, so nothing is left behind that the game cannot read. The sword is for
 * show: what a guard's strike does is the same with any of them (GuardDuty.DAMAGE).
 */
final class GuardArms {
    /** What the game gives a mob's held item (it was 0 while we armed it). */
    private static final float DEFAULT_DROP_CHANCE = 0.085f;

    private final NamespacedKey mark;
    /** Villagers we have armed, so shutting down or switching off can disarm them all. */
    private final Set<UUID> armed = new HashSet<>();

    GuardArms(Plugin plugin) {
        this.mark = new NamespacedKey(plugin, "guard_sword");
    }

    static Material material(GuardDuty.Blade blade) {
        return switch (blade) {
            case NONE -> null;
            case WOOD -> Material.WOODEN_SWORD;
            case STONE -> Material.STONE_SWORD;
            case IRON -> Material.IRON_SWORD;
            case DIAMOND -> Material.DIAMOND_SWORD;
        };
    }

    /** Gives the villager the sword that matches {@code blade} (changing ours if the tools have changed), or takes ours away if none. */
    void set(Villager villager, GuardDuty.Blade blade) {
        Material wanted = material(blade);
        if (wanted == null) {
            clear(villager);
            return;
        }
        EntityEquipment equipment = villager.getEquipment();
        ItemStack held = equipment.getItemInMainHand();
        if (isOurs(held)) {
            if (held.getType() == wanted) {
                armed.add(villager.getUniqueId());
                return;
            }
        } else if (!held.getType().isAir()) {
            return; // something else put it there: not ours to replace
        }
        ItemStack sword = new ItemStack(wanted);
        sword.editMeta(meta -> meta.getPersistentDataContainer().set(mark, PersistentDataType.BYTE, (byte) 1));
        equipment.setItemInMainHand(sword);
        equipment.setItemInMainHandDropChance(0f); // (saved with the villager: a killed guard drops nothing)
        armed.add(villager.getUniqueId());
    }

    /**
     * Takes our sword out of the hand of this villager (or of whatever it became: a villager killed by a zombie that turns keeps
     * its equipment, and we must not leave a zombie with a diamond sword), if that is what is there, and gives back the
     * game's own chance of dropping what is held.
     */
    void clear(org.bukkit.entity.LivingEntity entity) {
        armed.remove(entity.getUniqueId());
        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null && isOurs(equipment.getItemInMainHand())) {
            equipment.setItemInMainHand(null);
            equipment.setItemInMainHandDropChance(DEFAULT_DROP_CHANCE);
        }
    }

    /** Disarms every guard we armed that is still loaded. */
    void clearAll() {
        for (UUID id : Set.copyOf(armed)) {
            if (Bukkit.getEntity(id) instanceof org.bukkit.entity.LivingEntity entity) {
                clear(entity);
            }
        }
        armed.clear();
    }

    private boolean isOurs(ItemStack item) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(mark, PersistentDataType.BYTE);
    }
}
