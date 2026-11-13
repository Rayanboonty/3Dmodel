package net.vortexpvp.fx;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DeathProtection;
import net.vortexpvp.util.Tasks;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class TotemPop {
    private static NamespacedKey key;

    private TotemPop() {
    }

    public static void init(Plugin plugin) {
        key = new NamespacedKey(plugin, "totem_pop");
    }

    public static void play(Player p, ItemStack shown) {
        if (key != null && p != null && p.isOnline()) {
            ItemStack show = shown == null ? new ItemStack(Material.TOTEM_OF_UNDYING) : shown.clone();
            show.setAmount(1);

            try {
                if (show.getType() != Material.TOTEM_OF_UNDYING) {
                    show.setData(DataComponentTypes.DEATH_PROTECTION, DeathProtection.deathProtection().build());
                }
            } catch (Throwable var5) {
                show = new ItemStack(Material.TOTEM_OF_UNDYING);
            }

            show.editMeta(m -> m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte)1));
            ItemStack before = p.getInventory().getItemInOffHand();
            ItemStack keep = before == null ? null : before.clone();
            p.getInventory().setItemInOffHand(show);
            Tasks.later(1L, () -> {
                if (p.isOnline() && ours(p.getInventory().getItemInOffHand())) {
                    p.playEffect(EntityEffect.PROTECTED_FROM_DEATH);
                }
            });
            Tasks.later(3L, () -> {
                if (p.isOnline() && ours(p.getInventory().getItemInOffHand())) {
                    p.getInventory().setItemInOffHand(keep);
                }
            });
        }
    }

    public static boolean ours(ItemStack it) {
        return key != null
            && it != null
            && !it.isEmpty()
            && it.hasItemMeta()
            && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
