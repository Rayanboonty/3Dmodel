package net.practicepvp.gui.admin;

import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Msg;
import net.practicepvp.gui.Menu;
import net.practicepvp.gui.Ui;
import net.practicepvp.kit.Kit;
import net.practicepvp.kit.KitManager;
import net.practicepvp.util.Items;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class KitContentsMenu extends Menu {
    private final Kit kit;
    private boolean done;
    private boolean loaded;

    public KitContentsMenu(PracticePlugin plugin, Player viewer, Kit kit) {
        super(plugin, viewer);
        this.kit = kit;
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Kit Contents</b></gold> <dark_gray>» ").append(this.kit.name());
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    public boolean editable(int slot) {
        return slot >= 0 && slot <= 40;
    }

    @Override
    public boolean allowBottomClicks() {
        return true;
    }

    @Override
    public boolean allowShiftInto() {
        return true;
    }

    private static int kitIndex(int menuSlot) {
        if (menuSlot < 27) {
            return menuSlot + 9;
        } else if (menuSlot < 36) {
            return menuSlot - 27;
        } else {
            return switch (menuSlot) {
                case 36 -> 39;
                case 37 -> 38;
                case 38 -> 37;
                case 39 -> 36;
                default -> 40;
            };
        }
    }

    @Override
    protected void draw() {
        if (!this.loaded) {
            this.loaded = true;
            this.load(this.kit.contents);
        }

        String[] labels = new String[]{"Helmet", "Chestplate", "Leggings", "Boots", "Offhand"};

        for (int i = 0; i < 5; i++) {
            this.set(45 + i, Ui.info(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "<aqua><b>▲ " + labels[i], "<gray>Put the " + labels[i].toLowerCase() + " above."));
        }

        for (int i = 41; i <= 44; i++) {
            this.set(i, Items.filler(Ui.FRAME));
        }

        this.set(
            50,
            Items.of(Material.HOPPER).name("<gold><b>Copy My Inventory").lore("<gray>Fill from what you carry and wear.").build(),
            c -> this.load(KitManager.copyFrom(this.viewer.getInventory()))
        );
        this.set(51, Items.of(Material.LAVA_BUCKET).name("<red><b>Clear").build(), c -> {
            for (int ix = 0; ix <= 40; ix++) {
                this.getInventory().setItem(ix, null);
            }
        });
        this.set(52, Items.of(Material.RED_DYE).name("<red><b>Cancel").lore("<gray>Discard changes.").build(), c -> {
            this.done = true;
            this.closeQuietly();
            new KitListMenu.KitEditMenu(this.plugin, this.viewer, this.kit).open();
        });
        this.set(
            53, Items.of(Material.LIME_DYE).name("<green><b>Save").lore("<gray>Changing contents resets", "<gray>players' custom layouts.").build(), c -> {
                this.save();
                this.closeQuietly();
                new KitListMenu.KitEditMenu(this.plugin, this.viewer, this.kit).open();
            }
        );
    }

    private void load(ItemStack[] contents) {
        for (int slot = 0; slot <= 40; slot++) {
            ItemStack it = contents[kitIndex(slot)];
            this.getInventory().setItem(slot, it == null ? null : it.clone());
        }
    }

    private void save() {
        if (!this.done) {
            this.done = true;
            ItemStack[] out = new ItemStack[41];

            for (int slot = 0; slot <= 40; slot++) {
                ItemStack it = this.getInventory().getItem(slot);
                out[kitIndex(slot)] = Items.isEmpty(it) ? null : Items.withoutTag(it.clone(), Items.KIT_SLOT);
            }

            this.kit.contents = out;
            this.kit.bumpVersion();
            this.plugin.kits().save();
            Msg.send(this.viewer, "admin.kit-saved", "kit", this.kit.label());
            Snd.play(this.viewer, "entity.player.levelup", 0.6F, 1.5F);
        }
    }

    @Override
    public void onClose() {
        this.save();
    }
}
