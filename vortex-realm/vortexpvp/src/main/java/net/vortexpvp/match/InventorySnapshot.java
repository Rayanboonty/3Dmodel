package net.vortexpvp.match;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public final class InventorySnapshot {
    public final UUID id = UUID.randomUUID();
    public final UUID owner;
    public final String name;
    public final ItemStack[] storage;
    public final ItemStack[] armor;
    public final ItemStack offhand;
    public final double health;
    public final double maxHealth;
    public final int food;
    public final float saturation;
    public final List<PotionEffect> effects;
    public final MatchStats stats;
    public final String kitName;
    public final long created = System.currentTimeMillis();
    public UUID opponentSnapshot;

    public InventorySnapshot(Player p, MatchStats stats, String kitName) {
        this.owner = p.getUniqueId();
        this.name = p.getName();
        this.storage = cloneAll(p.getInventory().getStorageContents());
        this.armor = cloneAll(p.getInventory().getArmorContents());
        ItemStack off = p.getInventory().getItemInOffHand();
        this.offhand = off.isEmpty() ? null : off.clone();
        this.health = p.getHealth();
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        this.maxHealth = max == null ? 20.0 : max.getValue();
        this.food = p.getFoodLevel();
        this.saturation = p.getSaturation();
        this.effects = new ArrayList<>(p.getActivePotionEffects());
        this.stats = stats == null ? new MatchStats() : stats.copy();
        this.kitName = kitName;
    }

    private static ItemStack[] cloneAll(ItemStack[] in) {
        ItemStack[] out = new ItemStack[in.length];

        for (int i = 0; i < in.length; i++) {
            out[i] = in[i] != null && !in[i].isEmpty() ? in[i].clone() : null;
        }

        return out;
    }

    public int count(Material m) {
        int c = 0;

        for (ItemStack it : this.storage) {
            if (it != null && it.getType() == m) {
                c += it.getAmount();
            }
        }

        if (this.offhand != null && this.offhand.getType() == m) {
            c += this.offhand.getAmount();
        }

        return c;
    }

    public int healingPotions() {
        int c = 0;

        for (ItemStack it : this.storage) {
            if (it != null && it.getType() == Material.SPLASH_POTION) {
                c += it.getAmount();
            }
        }

        return c;
    }
}
