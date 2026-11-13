package net.vortexrealm.gui.admin;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Msg;
import net.vortexrealm.data.KitStats;
import net.vortexrealm.data.PvPProfile;
import net.vortexrealm.dialog.Dialogs;
import net.vortexrealm.gui.Menu;
import net.vortexrealm.gui.Ui;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.rank.Tier;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class PlayerAdminMenu extends Menu {
    private final PvPProfile prof;
    private final boolean online;

    private PlayerAdminMenu(VortexRealm plugin, Player viewer, PvPProfile prof, boolean online) {
        super(plugin, viewer);
        this.prof = prof;
        this.online = online;
    }

    public static void prompt(VortexRealm plugin, Player viewer) {
        plugin.dialogs().text(viewer, Text.mm("<gold><b>Find Player"), "Player name", "", 16, name -> open(plugin, viewer, name));
    }

    public static void open(VortexRealm plugin, Player viewer, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target != null) {
            new PlayerAdminMenu(plugin, viewer, plugin.profiles().get(target), true).open();
        } else {
            plugin.db().supply(c -> {
                UUID u = plugin.db().findUuid(c, name);
                return u == null ? null : plugin.db().loadProfile(c, u, name);
            }).whenComplete((p, err) -> Tasks.ensureSync(() -> {
                if (p == null) {
                    Msg.error(viewer, "general.player-unknown", "player", name);
                } else {
                    new PlayerAdminMenu(plugin, viewer, p, false).open();
                }
            }));
        }
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Player</b></gold> <dark_gray>» <gray>" + Text.esc(this.prof.name));
    }

    @Override
    protected int rows() {
        return 6;
    }

    private void persist() {
        if (this.online) {
            this.prof.dirty = true;
        } else {
            this.plugin.profiles().save(this.prof);
        }
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        this.set(
            4,
            Items.of(Material.PLAYER_HEAD)
                .skull(Bukkit.getOfflinePlayer(this.prof.uuid))
                .name("<gold><b>" + Text.esc(this.prof.name))
                .lore(this.online ? "<green>Online" : "<gray>Offline", "<gray>FFA: <green>" + this.prof.ffaKills + "K <red>" + this.prof.ffaDeaths + "D")
                .build()
        );
        int[] slots = Ui.innerSlots(6);
        int i = 0;

        for (Kit k : this.plugin.kits().sorted()) {
            if (i >= slots.length) {
                break;
            }

            KitStats ks = this.prof.statsOrNull(k.id);
            Tier t = ks == null ? null : ks.tier();
            this.set(
                slots[i++],
                Items.of(k.iconStack())
                    .name(k.color + "<b>" + Text.esc(k.displayName))
                    .lore(
                        "<gray>Tier: " + (t == null ? "<white>Unranked" : t.mm()),
                        "<gray>Rank points: <white>" + (ks == null ? 0 : ks.elo),
                        "<gray>W/L: <green>" + (ks == null ? 0 : ks.wins) + "<gray>/<red>" + (ks == null ? 0 : ks.losses),
                        "",
                        "<yellow>Click <gray>set rank points (tier follows)",
                        "<yellow>Right-click <gray>set tier",
                        "<red>Shift-click <gray>reset this kit"
                    )
                    .hideAll()
                    .build(),
                type -> {
                    if (type.isShiftClick()) {
                        this.prof.kits.remove(k.id);
                        this.persist();
                        this.plugin.db().run(c -> {
                            try (PreparedStatement ps = c.prepareStatement("DELETE FROM vr_kit_stats WHERE uuid=? AND kit=?")) {
                                ps.setString(1, this.prof.uuid.toString());
                                ps.setString(2, k.id);
                                ps.executeUpdate();
                            }
                        });
                        this.refreshTag();
                        this.redraw();
                    } else if (type.isRightClick()) {
                        List<Dialogs.Choice> tiers = new ArrayList<>();

                        for (Tier tier : Tier.values()) {
                            tiers.add(new Dialogs.Choice(tier.name(), tier.mm()));
                        }

                        this.plugin.dialogs().choice(this.viewer, Text.mm("<gold><b>Set Tier"), "Tier", tiers, t == null ? Tier.LT5.name() : t.name(), v -> {
                            this.plugin.ranks().adminSetTier(this.prof, k, Tier.valueOf(v));
                            this.persist();
                            this.refreshTag();
                            new PlayerAdminMenu(this.plugin, this.viewer, this.prof, this.online).open();
                        });
                    } else {
                        this.plugin
                            .dialogs()
                            .text(
                                this.viewer,
                                Text.mm("<gold><b>Set Rank Points"),
                                "Rank points for " + k.displayName,
                                String.valueOf(ks == null ? 0 : ks.elo),
                                7,
                                v -> {
                                    try {
                                        this.plugin.ranks().adminSet(this.prof, k, Integer.parseInt(v.trim()));
                                        this.persist();
                                        this.refreshTag();
                                    } catch (NumberFormatException var4x) {
                                        Msg.error(this.viewer, "admin.bad-number");
                                    }

                                    new PlayerAdminMenu(this.plugin, this.viewer, this.prof, this.online).open();
                                }
                            );
                    }
                }
            );
        }

        this.set(
            48,
            Items.of(Material.TNT).name("<red><b>Reset All Stats").build(),
            c -> this.plugin
                .dialogs()
                .confirm(
                    this.viewer,
                    Text.mm("<red><b>Reset " + Text.esc(this.prof.name) + "?"),
                    List.of(Text.mm("<gray>Every kit's rank and record plus FFA stats.")),
                    () -> {
                        this.prof.kits.clear();
                        this.prof.ffaKills = 0;
                        this.prof.ffaDeaths = 0;
                        this.prof.ffaBestStreak = 0;
                        this.plugin.db().run(c2 -> this.plugin.db().resetPlayer(c2, this.prof.uuid));
                        this.persist();
                        this.refreshTag();
                        Msg.send(this.viewer, "admin.player-reset", "player", this.prof.name);
                    }
                )
        );
        Player target = Bukkit.getPlayer(this.prof.uuid);
        if (target != null) {
            this.set(50, Items.of(Material.RED_BED).name("<gold><b>Send To Lobby").build(), c -> {
                this.plugin.forceLeave(target);
                Msg.send(this.viewer, "admin.sent-to-lobby", "player", target.getName());
            });
        }

        this.set(49, Ui.back(), c -> AdminMenu.openBest(this.plugin, this.viewer));
    }

    private void refreshTag() {
        Player p = Bukkit.getPlayer(this.prof.uuid);
        if (p != null) {
            this.plugin.boards().refreshNametag(p);
        }
    }
}
