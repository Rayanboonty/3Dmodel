package net.vortexpvp.gui.menus;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.data.KitStats;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.dialog.Dialogs;
import net.vortexpvp.ffa.FfaArena;
import net.vortexpvp.gui.Menu;
import net.vortexpvp.gui.Ui;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.rank.Tier;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class QueueMenu extends Menu {
    private final boolean ranked;
    private Dialogs.QueueTab tab;

    public QueueMenu(VortexPvP plugin, Player viewer, boolean ranked, Dialogs.QueueTab tab) {
        super(plugin, viewer);
        this.ranked = ranked;
        this.tab = tab;
    }

    @Override
    protected Component title() {
        return Text.mm((this.ranked ? "<gold><b>Ranked</b>" : "<green><b>Unranked</b>") + " <dark_gray>» <gray>Pick kits");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        this.tab(1, Dialogs.QueueTab.SKILL, Material.DIAMOND_SWORD, "<aqua>Skill Kits");
        this.tab(3, Dialogs.QueueTab.VANILLA, Material.END_CRYSTAL, "<gold>Vanilla Kits");
        this.tab(5, Dialogs.QueueTab.OTHER, Material.AMETHYST_SHARD, "<light_purple>Other Kits");
        this.tab(7, Dialogs.QueueTab.MOST_PLAYED, Material.BLAZE_POWDER, "<white>Most Played Kits");
        PvPProfile prof = this.plugin.profiles().get(this.viewer);
        List<Kit> shown = this.plugin.dialogs().kitsFor(this.ranked, this.tab);
        int[] slots = Ui.fit(shown.size(), 6);
        int i = 0;

        for (Kit k : shown) {
            if (i >= slots.length) {
                break;
            }

            int players = this.plugin.queues().size(k, this.ranked) + this.plugin.matches().playing(k);
            boolean queued = this.plugin.queues().inQueue(this.viewer, k, this.ranked);
            KitStats ks = prof.statsOrNull(k.id);
            List<String> lore = new ArrayList<>();
            lore.add(queued ? "<green>" + players + " players ✓" : "<gray>" + players + " players");

            for (String d : k.description) {
                lore.add("<dark_gray>" + Text.esc(d));
            }

            if (this.ranked) {
                Tier t = ks == null ? null : ks.tier();
                lore.add("");
                lore.add("<gray>Your tier: " + (t == null ? "<white>Unranked" : t.mm()));
            }

            lore.add("");
            lore.add(queued ? "<red>▶ Click to leave this queue" : "<green>▶ Click to join this queue");
            ItemStack icon = Items.of(k.iconStack()).name(k.color + "<b>" + Text.esc(k.displayName)).lore(lore).hideAll().glint(queued).build();
            icon.setAmount(Math.max(1, Math.min(icon.getMaxStackSize(), players)));
            this.set(slots[i++], icon, c -> {
                if (this.plugin.sessions().state(this.viewer) != PlayerState.LOBBY && this.plugin.sessions().state(this.viewer) != PlayerState.QUEUE) {
                    this.viewer.closeInventory();
                } else {
                    this.plugin.queues().toggle(this.viewer, k, this.ranked);
                    this.redraw();
                }
            });
        }

        if (this.plugin.queues().inQueue(this.viewer)) {
            this.set(45, Items.of(Material.RED_DYE).name("<red><b>Leave All Queues").build(), c -> {
                this.plugin.queues().leaveAll(this.viewer, true);
                this.redraw();
            });
        }

        this.set(49, Ui.close(), c -> this.viewer.closeInventory());
        this.set(53, Items.of(Material.SUNFLOWER).name("<gold><b>Refresh").build(), c -> this.redraw());
    }

    private void tab(int slot, Dialogs.QueueTab t, Material icon, String label) {
        boolean sel = t == this.tab;
        this.set(
            slot,
            Items.of(icon)
                .name(
                    sel ? "<white><b><i>" + Text.plain(Text.mm(label)) : String.valueOf(label)
                )
                .lore(sel ? "<gray>Showing" : "<yellow>▶ Click to show")
                .hideAll()
                .glint(sel)
                .build(),
            c -> {
                this.tab = t;
                this.redraw();
            }
        );
    }

    public static final class Ffa extends Menu {
        public Ffa(VortexPvP plugin, Player viewer) {
            super(plugin, viewer);
        }

        @Override
        protected Component title() {
            return Text.mm("<gold><b>Free For All</b> <dark_gray>» <gray>Pick an arena");
        }

        @Override
        protected int rows() {
            return 4;
        }

        @Override
        protected void draw() {
            this.border(Ui.FRAME);
            int ready = 0;

            for (FfaArena a : this.plugin.ffa().all()) {
                if (this.plugin.ffa().kitOf(a) != null && a.ready()) {
                    ready++;
                }
            }

            int[] slots = Ui.fit(ready, 4);
            int i = 0;

            for (FfaArena ax : this.plugin.ffa().all()) {
                Kit k = this.plugin.ffa().kitOf(ax);
                if (k != null && ax.ready() && i < slots.length) {
                    ItemStack icon = Items.of(k.iconStack())
                        .name(k.color + "<b>" + Text.esc(ax.displayName))
                        .lore("<gray>" + ax.players.size() + " players", "<gray>Kit: <white>" + Text.esc(k.displayName), "", "<green>▶ Click to join")
                        .hideAll()
                        .build();
                    icon.setAmount(Math.max(1, Math.min(icon.getMaxStackSize(), ax.players.size())));
                    this.set(slots[i++], icon, c -> {
                        this.viewer.closeInventory();
                        this.plugin.ffa().join(this.viewer, ax);
                    });
                }
            }

            if (i == 0) {
                this.set(13, Ui.info(Material.CLOCK, "<gray>No FFA arenas are open right now"));
            }

            this.set(31, Ui.close(), c -> this.viewer.closeInventory());
        }
    }
}
