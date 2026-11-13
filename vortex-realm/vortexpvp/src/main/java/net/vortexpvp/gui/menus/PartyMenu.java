package net.vortexpvp.gui.menus;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Msg;
import net.vortexpvp.dialog.Dialogs;
import net.vortexpvp.gui.Menu;
import net.vortexpvp.gui.Ui;
import net.vortexpvp.match.Match;
import net.vortexpvp.party.Party;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class PartyMenu extends Menu {
    public PartyMenu(VortexPvP plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Party");
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        Party party = this.plugin.parties().of(this.viewer);
        if (party == null) {
            this.set(22, Ui.info(Material.BARRIER, "<red>You are not in a party"));
            this.set(40, Ui.close(), c -> this.viewer.closeInventory());
        } else {
            boolean leader = party.isLeader(this.viewer);
            int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
            int i = 0;

            for (UUID u : party.members) {
                if (i >= slots.length) {
                    break;
                }

                Player m = Bukkit.getPlayer(u);
                String name = m == null ? "?" : m.getName();
                boolean isLeader = u.equals(party.leader);
                Items.Builder b = Items.of(Material.PLAYER_HEAD).name((isLeader ? "<gold><b>♛ " : "<white>") + Text.esc(name));
                if (m != null) {
                    b.skull(m);
                }

                if (leader && !isLeader) {
                    b.lore("<gray>Click: <yellow>promote to leader", "<gray>Shift-click: <red>kick");
                } else {
                    b.lore(isLeader ? "<gold>Party leader" : "<gray>Member");
                }

                this.set(slots[i++], b.build(), type -> {
                    if (leader && !isLeader && m != null) {
                        if (type.isShiftClick()) {
                            this.plugin.parties().kick(this.viewer, m);
                        } else {
                            this.plugin.parties().promote(this.viewer, m);
                        }

                        this.redraw();
                    }
                });
            }

            if (leader) {
                this.set(
                    37,
                    Items.of(Material.GOLDEN_AXE)
                        .name("<gold><b>Party Games")
                        .lore("<gray>Party FFA or Party Split", "", "<yellow>▶ Click to choose")
                        .hideAll()
                        .build(),
                    c -> openGames(this.plugin, this.viewer)
                );
                this.set(38, Items.of(Material.NAME_TAG).name("<gold><b>Invite Player").lore("<yellow>▶ Click to type a name").build(), c -> {
                    this.viewer.closeInventory();
                    invitePrompt(this.plugin, this.viewer);
                });
                this.set(39, Ui.toggle(Material.OAK_DOOR, "Open Party", party.open, "Anyone can join with", "/party join " + this.viewer.getName()), c -> {
                    this.plugin.parties().setOpen(this.viewer, !party.open);
                    this.redraw();
                });
                this.set(41, Items.of(Material.TNT).name("<red><b>Disband").lore("<gray>Remove everyone from the party.").build(), c -> {
                    this.viewer.closeInventory();
                    this.plugin.parties().disband(party);
                });
            }

            this.set(43, Items.of(Material.RED_DYE).name("<red><b>Leave Party").build(), c -> {
                this.viewer.closeInventory();
                this.plugin.parties().leave(this.viewer);
            });
            this.set(40, Ui.close(), c -> this.viewer.closeInventory());
        }
    }

    public static void invitePrompt(VortexPvP plugin, Player p) {
        plugin.dialogs().text(p, Text.mm("<gold><b>Invite to Party"), "Player name", "", 16, name -> {
            Player target = Bukkit.getPlayerExact(name);
            if (target == null) {
                Msg.error(p, "general.player-offline", "player", name);
            } else {
                plugin.parties().invite(p, target);
            }
        });
    }

    public static void openGames(VortexPvP plugin, Player p) {
        plugin.dialogs()
            .choice(
                p,
                Text.mm("<gold><b>Party Games"),
                "Game mode",
                List.of(
                    new Dialogs.Choice("split", "<gold>Party Split <gray>(2 teams)"), new Dialogs.Choice("ffa", "<gold>Party FFA <gray>(everyone vs everyone)")
                ),
                "split",
                mode -> new KitSelectMenu(
                        plugin,
                        p,
                        Text.mm("<gold><b>Party Games</b></gold> <dark_gray>» <gray>Pick a kit"),
                        k -> k.enabled && k.hasContents() && !plugin.arenas().forKit(k).isEmpty(),
                        k -> plugin.parties().startGame(p, k, "ffa".equals(mode) ? Match.Type.PARTY_FFA : Match.Type.PARTY_SPLIT),
                        null,
                        false
                    )
                    .open()
            );
    }
}
