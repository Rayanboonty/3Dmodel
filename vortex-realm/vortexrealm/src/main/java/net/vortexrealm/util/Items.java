package net.vortexrealm.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;

public final class Items {
    public static NamespacedKey ACTION;
    public static NamespacedKey KIT_SLOT;
    public static NamespacedKey GUI;
    public static NamespacedKey WAND;

    private Items() {
    }

    public static void init(Plugin plugin) {
        ACTION = new NamespacedKey(plugin, "action");
        KIT_SLOT = new NamespacedKey(plugin, "kit_slot");
        GUI = new NamespacedKey(plugin, "gui");
        WAND = new NamespacedKey(plugin, "wand");
    }

    public static Items.Builder of(Material m) {
        return new Items.Builder(new ItemStack(m));
    }

    public static Items.Builder of(ItemStack base) {
        return new Items.Builder(base.clone());
    }

    public static String tag(ItemStack item, NamespacedKey key) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
            ? item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING)
            : null;
    }

    public static Integer intTag(ItemStack item, NamespacedKey key) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
            ? item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER)
            : null;
    }

    public static ItemStack withIntTag(ItemStack item, NamespacedKey key, int value) {
        ItemStack copy = item.clone();
        copy.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value));
        return copy;
    }

    public static ItemStack withoutTag(ItemStack item, NamespacedKey key) {
        if (item != null && item.hasItemMeta()) {
            ItemStack copy = item.clone();
            copy.editMeta(meta -> meta.getPersistentDataContainer().remove(key));
            return copy;
        } else {
            return item;
        }
    }

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    public static ItemStack filler(Material pane) {
        ItemStack it = new ItemStack(pane);
        it.editMeta(m -> {
            m.setHideTooltip(true);
            m.getPersistentDataContainer().set(GUI, PersistentDataType.STRING, "filler");
        });
        return it;
    }

    public static String pretty(Material m) {
        return Fmt.capitalize(m.name());
    }

    public static ItemMeta meta(ItemStack it) {
        return it.getItemMeta();
    }

    public static final class Builder {
        private final ItemStack item;
        private final List<Component> lore = new ArrayList<>();
        private boolean loreTouched;

        private Builder(ItemStack item) {
            this.item = item;
        }

        public Items.Builder amount(int amount) {
            this.item.setAmount(Math.max(1, Math.min(amount, this.item.getMaxStackSize())));
            return this;
        }

        public Items.Builder name(String mini) {
            Component c = Text.item(mini);
            this.item.editMeta(m -> m.displayName(c));
            return this;
        }

        public Items.Builder name(Component c) {
            this.item.editMeta(m -> m.displayName(Text.noItalic(c)));
            return this;
        }

        public Items.Builder lore(String... lines) {
            this.loreTouched = true;

            for (String l : lines) {
                this.lore.add(Text.item(l));
            }

            return this;
        }

        public Items.Builder lore(List<String> lines) {
            this.loreTouched = true;

            for (String l : lines) {
                this.lore.add(Text.item(l));
            }

            return this;
        }

        public Items.Builder loreComponents(List<Component> lines) {
            this.loreTouched = true;

            for (Component c : lines) {
                this.lore.add(Text.noItalic(c));
            }

            return this;
        }

        public Items.Builder ench(Enchantment e, int level) {
            this.item.editMeta(m -> m.addEnchant(e, level, true));
            return this;
        }

        public Items.Builder glint(boolean on) {
            this.item.editMeta(m -> m.setEnchantmentGlintOverride(on));
            return this;
        }

        public Items.Builder unbreakable() {
            this.item.editMeta(m -> m.setUnbreakable(true));
            return this;
        }

        public Items.Builder hideAll() {
            this.item.editMeta(m -> m.addItemFlags(ItemFlag.values()));
            return this;
        }

        public Items.Builder tag(NamespacedKey key, String value) {
            this.item.editMeta(m -> m.getPersistentDataContainer().set(key, PersistentDataType.STRING, value));
            return this;
        }

        public Items.Builder action(String action) {
            return this.tag(Items.ACTION, action);
        }

        public Items.Builder gui(String id) {
            return this.tag(Items.GUI, id);
        }

        public Items.Builder potion(PotionType type) {
            this.item.editMeta(PotionMeta.class, pm -> pm.setBasePotionType(type));
            return this;
        }

        public Items.Builder color(Color color) {
            if (this.item.getItemMeta() instanceof LeatherArmorMeta) {
                this.item.editMeta(LeatherArmorMeta.class, lm -> lm.setColor(color));
            } else if (this.item.getItemMeta() instanceof PotionMeta) {
                this.item.editMeta(PotionMeta.class, pm -> pm.setColor(color));
            }

            return this;
        }

        public Items.Builder skull(OfflinePlayer owner) {
            if (this.item.getType() == Material.PLAYER_HEAD) {
                this.item.editMeta(SkullMeta.class, sm -> sm.setOwningPlayer(owner));
            }

            return this;
        }

        public Items.Builder skull(PlayerProfile profile) {
            if (this.item.getType() == Material.PLAYER_HEAD && profile != null) {
                this.item.editMeta(SkullMeta.class, sm -> sm.setPlayerProfile(profile));
            }

            return this;
        }

        public Items.Builder hideTooltip() {
            this.item.editMeta(m -> m.setHideTooltip(true));
            return this;
        }

        public ItemStack build() {
            if (this.loreTouched) {
                this.item.editMeta(m -> m.lore(this.lore));
            }

            return this.item;
        }
    }
}
