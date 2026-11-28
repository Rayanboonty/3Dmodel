package net.vortexrealm.gui.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.data.KitStats;
import net.vortexrealm.data.PvPProfile;
import net.vortexrealm.gui.Menu;
import net.vortexrealm.gui.Ui;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.rank.Tier;
import net.vortexrealm.util.Fmt;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class StatsMenu extends Menu {
    private final UUID target;
    private final String name;
    private PvPProfile profile;
    private boolean loading;

    public StatsMenu(VortexRealm plugin, Player viewer, UUID target, String name) {
        super(plugin, viewer);
        this.target = target;
        this.name = name;
        Player online = Bukkit.getPlayer(target);
        this.profile = online != null ? plugin.profiles().getIfLoaded(target) : null;
        if (this.profile == null) {
            this.loading = true;
            plugin.db().supply(c -> plugin.db().loadProfile(c, target, name)).thenAccept(p -> Tasks.ensureSync(() -> {
                this.profile = p;
                this.loading = false;
                if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) {
                    this.redraw();
                }
            }));
        }
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Stats</b></gold> <dark_gray>» <gray>" + Text.esc(this.name));
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        this.set(49, Ui.back(), c -> new LeaderboardMenu(this.plugin, this.viewer, null).open());
        if (!this.loading && this.profile != null) {
            boolean self = this.target.equals(this.viewer.getUniqueId());
            int totalW = 0;
            int totalL = 0;

            for (KitStats k : this.profile.kits.values()) {
                totalW += k.wins;
                totalL += k.losses;
            }

            this.set(
                4,
                Items.of(Material.PLAYER_HEAD)
                    .skull(Bukkit.getOfflinePlayer(this.target))
                    .name("<gold><b>" + Text.esc(this.name))
                    .lore(
                        "<gray>Ranked wins: <green>" + totalW,
                        "<gray>Ranked losses: <red>" + totalL,
                        "<gray>FFA kills: <green>" + this.profile.ffaKills + " <gray>deaths: <red>" + this.profile.ffaDeaths,
                        "<gray>FFA K/D: <white>"
                            + Fmt.kdr(this.profile.ffaKills, this.profile.ffaDeaths)
                            + " <gray>best streak: <gold>"
                            + this.profile.ffaBestStreak
                    )
                    .build()
            );
            int[] slots = Ui.innerSlots(6);
            int i = 0;

            for (Kit kit : this.plugin.kits().sorted()) {
                if (kit.enabled && i < slots.length) {
                    KitStats ks = this.profile.statsOrNull(kit.id);
                    List<String> lore = new ArrayList<>();
                    Tier t = ks == null ? null : ks.tier();
                    lore.add("<gray>Tier: " + (t == null ? "<white>Unranked" : t.mm()));
                    int elo = ks == null ? 0 : ks.elo;
                    lore.add("<gray>Rank points: <white>" + Fmt.num(elo) + " <dark_gray>(peak " + Fmt.num(ks == null ? 0L : ks.peakElo) + ")");
                    if (t != null && t.next() != null) {
                        int need = Math.max(0, t.next().threshold() - elo);
                        lore.add("<gray>Next tier: " + t.next().mm() + " <dark_gray>(" + need + " pts to go)");
                    }

                    int pos = this.plugin.leaderboards().position(kit.id, this.target);
                    lore.add("<gray>Leaderboard: " + (pos > 0 ? "<gold>#" + pos : "<dark_gray>-"));
                    lore.add("");
                    int w = ks == null ? 0 : ks.wins;
                    int l = ks == null ? 0 : ks.losses;
                    lore.add("<gray>Ranked: <green>" + w + "W <red>" + l + "L <dark_gray>(" + Fmt.percent(ks == null ? 0.0 : ks.winRate()) + ")");
                    lore.add("<gray>Unranked: <green>" + (ks == null ? 0 : ks.unrankedWins) + "W <red>" + (ks == null ? 0 : ks.unrankedLosses) + "L");
                    lore.add("<gray>Streak: <gold>" + (ks == null ? 0 : ks.streak) + " <dark_gray>(best " + (ks == null ? 0 : ks.bestStreak) + ")");
                    boolean offer = ks != null && (ks.promoState == 1 || ks.promoState == 2);
                    if (offer) {
                        Tier to = Tier.byIndex(ks.promoTarget);
                        lore.add("");
                        lore.add("<gold><b>⚠ Tier Test Challenge ready</b> <gray>→ " + (to == null ? "?" : to.mm()));
                        if (self) {
                            lore.add("<yellow>▶ Click to take it");
                        }
                    }

                    this.set(
                        slots[i++],
                        Items.of(kit.iconStack()).name(kit.color + "<b>" + Text.esc(kit.displayName)).lore(lore).hideAll().glint(offer).build(),
                        c -> {
                            if (self && offer) {
                                this.viewer.closeInventory();
                                this.plugin.dialogs().rankUpOffer(this.viewer, kit);
                            }
                        }
                    );
                }
            }
        } else {
            this.set(22, Ui.info(Material.CLOCK, "<gray>Loading stats..."));
        }
    }
}
