package net.vortexrealm.gui.menus;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.gui.Menu;
import net.vortexrealm.gui.Ui;
import net.vortexrealm.match.InventorySnapshot;
import net.vortexrealm.match.MatchStats;
import net.vortexrealm.util.Fmt;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public final class InventoryViewMenu extends Menu {
    private final InventorySnapshot snap;

    public InventoryViewMenu(VortexRealm plugin, Player viewer, InventorySnapshot snap) {
        super(plugin, viewer);
        this.snap = snap;
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Inventory</b></gold> <dark_gray>» <gray>" + Text.esc(this.snap.name));
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        for (int i = 9; i < 36; i++) {
            if (this.snap.storage[i] != null) {
                this.set(i - 9, this.snap.storage[i]);
            }
        }

        for (int ix = 0; ix < 9; ix++) {
            if (this.snap.storage[ix] != null) {
                this.set(27 + ix, this.snap.storage[ix]);
            }
        }

        ItemStack[] armor = this.snap.armor;
        this.piece(36, armor.length > 3 ? armor[3] : null, "Helmet");
        this.piece(37, armor.length > 2 ? armor[2] : null, "Chestplate");
        this.piece(38, armor.length > 1 ? armor[1] : null, "Leggings");
        this.piece(39, armor.length > 0 ? armor[0] : null, "Boots");
        this.piece(40, this.snap.offhand, "Offhand");
        double hearts = this.snap.health / 2.0;
        this.set(
            42,
            Items.of(this.snap.health > 0.5 ? Material.GLISTERING_MELON_SLICE : Material.SKELETON_SKULL)
                .name("<red><b>❤ " + Fmt.oneDecimal(hearts) + "</b> <gray>/ " + Fmt.oneDecimal(this.snap.maxHealth / 2.0))
                .lore("<gray>Health when the fight ended.")
                .build()
        );
        this.set(
            43,
            Items.of(Material.COOKED_BEEF)
                .name("<gold><b>Hunger")
                .lore("<gray>Food: <white>" + this.snap.food + "/20", "<gray>Saturation: <white>" + Fmt.oneDecimal(this.snap.saturation))
                .build()
        );
        List<String> effects = new ArrayList<>();

        for (PotionEffect pe : this.snap.effects) {
            effects.add(
                "<gray>"
                    + Fmt.capitalize(pe.getType().getKey().getKey())
                    + " "
                    + roman(pe.getAmplifier() + 1)
                    + " <dark_gray>("
                    + (pe.isInfinite() ? "∞" : Fmt.clock(pe.getDuration() / 20))
                    + ")"
            );
        }

        if (effects.isEmpty()) {
            effects.add("<dark_gray>No active effects.");
        }

        this.set(44, Items.of(Material.POTION).name("<light_purple><b>Effects").lore(effects).hideAll().build());
        MatchStats s = this.snap.stats;
        this.set(
            49,
            Items.of(Material.PAPER)
                .name("<gold><b>Fight Stats")
                .lore(
                    "<gray>Hits: <white>" + s.hits + " <dark_gray>(crits " + s.criticalHits + ")",
                    "<gray>Longest combo: <white>" + s.longestCombo,
                    "<gray>Damage dealt: <white>" + Fmt.oneDecimal(s.damageDealt / 2.0) + "❤",
                    "<gray>Damage taken: <white>" + Fmt.oneDecimal(s.damageTaken / 2.0) + "❤",
                    "<gray>Potions used: <white>" + s.potionsUsed,
                    "<gray>Healing potions left: <white>" + this.snap.healingPotions(),
                    "<gray>Totems popped: <white>" + s.totemsPopped,
                    "<gray>Golden apples eaten: <white>" + s.gapplesEaten,
                    "<gray>Pearls thrown: <white>" + s.pearlsThrown
                )
                .build()
        );
        if (this.snap.opponentSnapshot != null) {
            InventorySnapshot other = this.plugin.snapshots().get(this.snap.opponentSnapshot);
            if (other != null) {
                this.set(
                    53,
                    Items.of(Material.PLAYER_HEAD).name("<gold><b>View " + Text.esc(other.name)).lore("<yellow>▶ Click to switch").build(),
                    c -> new InventoryViewMenu(this.plugin, this.viewer, other).open()
                );
            }
        }

        this.set(45, Ui.close(), c -> this.viewer.closeInventory());
        this.fill(Ui.FRAME);
    }

    private void piece(int slot, ItemStack it, String label) {
        if (it != null && !it.isEmpty()) {
            this.set(slot, it);
        } else {
            this.set(slot, Ui.info(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>No " + label));
        }
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
