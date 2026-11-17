package net.vortexrealm.gui.menus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Msg;
import net.vortexrealm.data.PvPProfile;
import net.vortexrealm.gui.Menu;
import net.vortexrealm.gui.Ui;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.kit.KitLayout;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Session;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class KitEditorMenu extends Menu {
    private static final int OFFHAND_SLOT = 15;
    private final Kit kit;
    private boolean finished;
    /** 0 = arranging the kit, 1 = picking a trim pattern, 2 = picking its material. */
    private int mode;
    private String pickPattern;
    /** The offhand item while the trim pages cover the menu. */
    private ItemStack keptOffhand;

    private KitEditorMenu(VortexRealm plugin, Player viewer, Kit kit) {
        super(plugin, viewer);
        this.kit = kit;
    }

    public static void start(VortexRealm plugin, Player p, Kit kit) {
        Session s = plugin.sessions().get(p);
        if (s.state != PlayerState.LOBBY) {
            Msg.error(p, "kit-editor.lobby-only");
        } else if (!kit.hasContents()) {
            Msg.error(p, "kit-editor.empty-kit");
        } else {
            s.state = PlayerState.EDITING;
            s.editing = kit;
            KitEditorMenu menu = new KitEditorMenu(plugin, p, kit);
            menu.load(plugin.profiles().get(p).layouts.get(kit.id));
            menu.open();
            menu.placeOffhand(plugin.profiles().get(p).layouts.get(kit.id));
            Msg.send(p, "kit-editor.started", "kit", kit.label());
            Snd.play(p, "block.note_block.chime", 0.7F, 1.4F);
            Tasks.later(2L, () -> {
                if (p.isOnline() && plugin.sessions().state(p) == PlayerState.EDITING && p.getOpenInventory().getTopInventory().getHolder(false) != menu) {
                    plugin.lobby().sendToLobby(p);
                }
            });
        }
    }

    private void load(KitLayout layout) {
        ItemStack[] c = this.plugin.kits().editorContents(this.kit, layout);
        PlayerInventory inv = this.viewer.getInventory();
        inv.clear();
        this.viewer.setItemOnCursor(null);
        inv.setStorageContents(Arrays.copyOfRange(c, 0, 36));
        inv.setHeldItemSlot(0);
    }

    private void placeOffhand(KitLayout layout) {
        ItemStack[] c = this.plugin.kits().editorContents(this.kit, layout);
        this.getInventory().setItem(15, c[40]);
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Kit Editor</b></gold> <dark_gray>» ").append(this.kit.name());
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    public boolean editable(int slot) {
        return slot == 15 && this.mode == 0;
    }

    @Override
    public boolean allowBottomClicks() {
        return true;
    }

    @Override
    protected void draw() {
        if (this.mode == 1) {
            this.drawPatterns();
            return;
        } else if (this.mode == 2) {
            this.drawMaterials();
            return;
        }

        this.fill(Ui.FRAME);
        this.set(
            4,
            Items.of(this.kit.iconStack())
                .name(this.kit.color + "<b>" + Text.esc(this.kit.displayName))
                .lore(
                    "<gray>Drag items around in <white>your inventory</white>.",
                    "<gray>Split stacks if you like.",
                    "<gray>Put an item in the <gold>offhand</gold> slot.",
                    "",
                    "<green>Close this menu to save."
                )
                .hideAll()
                .build()
        );
        ItemStack[] c = this.kit.contents;
        this.armor(10, c[39], "Helmet");
        this.armor(11, c[38], "Chestplate");
        this.armor(12, c[37], "Leggings");
        this.armor(13, c[36], "Boots");
        this.set(16, Ui.info(Material.SHIELD, "<gold><b>◀ Offhand", "<gray>Place the item you want", "<gray>in your offhand here."));
        this.set(18, Items.of(Material.LIME_DYE).name("<green><b>Save & Exit").lore("<gray>Save this layout.").build(), t -> {
            this.save();
            this.exit();
        });
        this.set(
            22,
            Items.of(Material.YELLOW_DYE)
                .name("<yellow><b>Reset to Default")
                .lore("<gray>Put every item back where", "<gray>the kit has it by default.")
                .build(),
            t -> {
                this.viewer.setItemOnCursor(null);
                this.load(null);
                this.placeOffhand(null);
                Snd.play(this.viewer, "block.note_block.chime", 0.6F, 0.8F);
            }
        );
        if (this.plugin.cosmetics() != null) {
            this.set(
                20,
                Items.of(Material.SMITHING_TABLE).name("<light_purple><b>Cosmetics").lore("<gray>Armor trims and your shield design.", "<gray>Saves this layout first.").build(),
                t -> {
                    this.save();
                    this.exit();
                    net.vortexrealm.util.Tasks.later(2L, () -> {
                        if (this.viewer.isOnline()) {
                            this.plugin.cosmetics().open(this.viewer);
                        }
                    });
                }
            );
        }

        this.set(26, Items.of(Material.RED_DYE).name("<red><b>Cancel").lore("<gray>Leave without saving.").build(), t -> {
            this.finished = true;
            this.exit();
        });
    }

    private void armor(int slot, ItemStack item, String label) {
        if (Items.isEmpty(item)) {
            this.set(slot, Ui.info(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>No " + label));
        } else {
            net.vortexrealm.cosmetics.Cosmetics cos = this.plugin.cosmetics();
            boolean trimmable = cos != null && org.bukkit.Tag.ITEMS_TRIMMABLE_ARMOR.isTagged(item.getType());
            String[] trim = cos == null ? null : cos.trimFor(this.viewer.getUniqueId(), this.kit.id);
            ItemStack copy = trimmable && trim != null ? net.vortexrealm.cosmetics.Cosmetics.withTrim(item, trim[0], trim[1]) : item.clone();
            copy.editMeta(m -> {
                List<Component> lore = m.hasLore() && m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                if (trimmable) {
                    lore.add(Text.item("<gray>Trim: " + (trim == null ? "<white>none" : "<white>" + net.vortexrealm.cosmetics.Cosmetics.label(trim[0]) + " <gray>in <white>" + net.vortexrealm.cosmetics.Cosmetics.label(trim[1]))));
                    lore.add(Text.item("<green>▶ Click to change the trim of this kit"));
                } else {
                    lore.add(Text.item("<dark_gray>Armor is fixed by the kit."));
                }

                m.lore(lore);
            });
            this.set(slot, copy, trimmable ? t -> this.openTrims() : null);
        }
    }

    // ------------------------------------------------------------------ trims for this kit

    private void openTrims() {
        this.keptOffhand = this.getInventory().getItem(15);
        this.getInventory().setItem(15, null);
        this.mode = 1;
        this.redraw();
        Snd.play(this.viewer, "block.smithing_table.use", 0.5F, 1.4F);
    }

    private void backToKit() {
        this.mode = 0;
        this.redraw();
        this.getInventory().setItem(15, this.keptOffhand);
        this.keptOffhand = null;
    }

    /** The kit's chestplate (or its first trimmable armor) for the trim previews. */
    private ItemStack sample() {
        ItemStack[] c = this.kit.contents;

        for (int i : new int[]{38, 37, 39, 36}) {
            if (i < c.length && !Items.isEmpty(c[i]) && org.bukkit.Tag.ITEMS_TRIMMABLE_ARMOR.isTagged(c[i].getType())) {
                return new ItemStack(c[i].getType());
            }
        }

        return new ItemStack(Material.DIAMOND_CHESTPLATE);
    }

    private void drawPatterns() {
        net.vortexrealm.cosmetics.Cosmetics cos = this.plugin.cosmetics();
        String[] now = cos.trimFor(this.viewer.getUniqueId(), this.kit.id);
        String mat = now == null ? "quartz" : now[1];
        List<String> pats = net.vortexrealm.cosmetics.Cosmetics.trimPatterns();
        int slot = 0;

        for (String pat : pats) {
            if (slot >= 18) {
                break;
            }

            boolean on = now != null && now[0].equals(pat);
            ItemStack icon = net.vortexrealm.cosmetics.Cosmetics.withTrim(this.sample(), pat, mat);
            this.set(
                slot++,
                Items.of(icon).name((on ? "<green>✔ " : "") + "<gold><b>" + net.vortexrealm.cosmetics.Cosmetics.label(pat)).lore("<gray>Then pick the material.").hideAll().build(),
                t -> {
                    this.pickPattern = pat;
                    this.mode = 2;
                    this.redraw();
                }
            );
        }

        this.set(19, Items.of(this.sample()).name("<white><b>No Trim").lore("<gray>Plain armor in this kit.").hideAll().build(), t -> {
            cos.setTrim(this.viewer.getUniqueId(), this.kit.id, "none");
            Snd.play(this.viewer, "item.armor.equip_diamond", 0.7F, 1.0F);
            this.backToKit();
        });
        this.set(
            20,
            Items.of(Material.PAPER)
                .name("<white><b>Same as Every Kit")
                .lore("<gray>Use your main trim", "<gray>(set it in /cosmetics).", cos.ownTrim(this.viewer.getUniqueId(), this.kit.id) ? "" : "<green>✔ Used now")
                .build(),
            t -> {
                cos.setTrim(this.viewer.getUniqueId(), this.kit.id, null);
                this.backToKit();
            }
        );
        this.set(26, Items.of(Material.ARROW).name("<gray><b>« Back to the kit").build(), t -> this.backToKit());
        this.fill(Ui.FRAME);
    }

    private void drawMaterials() {
        net.vortexrealm.cosmetics.Cosmetics cos = this.plugin.cosmetics();
        String pat = this.pickPattern;
        int slot = 0;

        for (String m : net.vortexrealm.cosmetics.Cosmetics.trimMaterials()) {
            if (slot >= 18) {
                break;
            }

            ItemStack icon = net.vortexrealm.cosmetics.Cosmetics.withTrim(this.sample(), pat, m);
            this.set(
                slot++,
                Items.of(icon).name("<gold><b>" + net.vortexrealm.cosmetics.Cosmetics.label(m)).lore("<gray>" + net.vortexrealm.cosmetics.Cosmetics.label(pat) + " in " + net.vortexrealm.cosmetics.Cosmetics.label(m), "<green>▶ Click to use it").hideAll().build(),
                t -> {
                    cos.setTrim(this.viewer.getUniqueId(), this.kit.id, pat + ":" + m);
                    Snd.play(this.viewer, "block.smithing_table.use", 0.7F, 1.2F);
                    this.viewer.sendMessage(Text.mm("<green>Trim for " + Text.esc(this.kit.displayName) + ": <white>" + net.vortexrealm.cosmetics.Cosmetics.label(pat) + "</white> in <white>" + net.vortexrealm.cosmetics.Cosmetics.label(m)));
                    this.backToKit();
                }
            );
        }

        this.set(26, Items.of(Material.ARROW).name("<gray><b>« Back to patterns").build(), t -> {
            this.mode = 1;
            this.redraw();
        });
        this.fill(Ui.FRAME);
    }

    private void save() {
        if (!this.finished) {
            this.finished = true;
            ItemStack[] storage = this.viewer.getInventory().getStorageContents();
            ItemStack cursor = this.viewer.getItemOnCursor();
            if (!Items.isEmpty(cursor)) {
                for (int i = 0; i < storage.length; i++) {
                    if (Items.isEmpty(storage[i])) {
                        storage[i] = cursor;
                        break;
                    }
                }
            }

            ItemStack offhand = this.mode == 0 ? this.getInventory().getItem(15) : this.keptOffhand;
            KitLayout layout = this.plugin.kits().layoutFrom(this.kit, storage, offhand);
            if (layout == null) {
                Msg.error(this.viewer, "kit-editor.invalid");
            } else {
                PvPProfile prof = this.plugin.profiles().get(this.viewer);
                prof.layouts.put(this.kit.id, layout);
                prof.dirty = true;
                Msg.send(this.viewer, "kit-editor.saved", "kit", this.kit.label());
                Snd.play(this.viewer, "entity.player.levelup", 0.6F, 1.6F);
            }
        }
    }

    private void exit() {
        this.viewer.setItemOnCursor(null);
        this.getInventory().clear();
        this.closeQuietly();
        Tasks.later(1L, () -> {
            if (this.viewer.isOnline() && this.plugin.sessions().state(this.viewer) == PlayerState.EDITING) {
                this.plugin.lobby().sendToLobby(this.viewer);
            }
        });
    }

    @Override
    public void onClose() {
        this.save();
        this.viewer.setItemOnCursor(null);
        this.getInventory().clear();
        Tasks.later(1L, () -> {
            if (this.viewer.isOnline() && this.plugin.sessions().state(this.viewer) == PlayerState.EDITING) {
                this.plugin.lobby().sendToLobby(this.viewer);
            }
        });
    }
}
