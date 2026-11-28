package net.practicepvp.gui.menus;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.data.Database;
import net.practicepvp.gui.Menu;
import net.practicepvp.gui.Ui;
import net.practicepvp.kit.Kit;
import net.practicepvp.rank.Tier;
import net.practicepvp.util.Fmt;
import net.practicepvp.util.Items;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class LeaderboardMenu extends Menu {
    private final String kitId;
    private int page;

    public LeaderboardMenu(PracticePlugin plugin, Player viewer, String kitId) {
        super(plugin, viewer);
        this.kitId = kitId;
    }

    @Override
    protected Component title() {
        if (this.kitId == null) {
            return Text.mm("<gold><b>Leaderboards");
        } else if ("overall".equals(this.kitId)) {
            return Text.mm("<gold><b>Leaderboard</b></gold> <dark_gray>» <gray>Overall");
        } else {
            Kit k = this.plugin.kits().get(this.kitId);
            return Text.mm("<gold><b>Leaderboard</b></gold> <dark_gray>» <gray>" + (k == null ? this.kitId : Text.esc(k.displayName)));
        }
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        if (this.kitId == null) {
            this.drawOverview();
        } else {
            this.drawBoard();
        }
    }

    private void drawOverview() {
        int[] slots = Ui.innerSlots(6);
        int i = 0;

        for (Kit k : this.plugin.kits().sorted()) {
            if (k.ranked && i < slots.length - 1) {
                List<String> lore = topLines(this.plugin.leaderboards().top(k.id), 10);
                int pos = this.plugin.leaderboards().position(k.id, this.viewer.getUniqueId());
                lore.add("");
                lore.add("<gray>Your position: " + (pos > 0 ? "<gold>#" + pos : "<dark_gray>unranked"));
                lore.add("");
                lore.add("<yellow>▶ Click for the full leaderboard");
                this.set(
                    slots[i++],
                    Items.of(k.iconStack()).name(k.color + "<b>" + Text.esc(k.displayName)).lore(lore).hideAll().build(),
                    t -> new LeaderboardMenu(this.plugin, this.viewer, k.id).open()
                );
            }
        }

        List<String> lore = topLines(this.plugin.leaderboards().overall(), 10);
        lore.add("");
        lore.add("<gray>Rank points summed across every kit.");
        lore.add("<yellow>▶ Click for the full leaderboard");
        this.set(
            4,
            Items.of(Material.GOLDEN_HELMET).name("<gold><b>Overall").lore(lore).hideAll().glint(true).build(),
            t -> new LeaderboardMenu(this.plugin, this.viewer, "overall").open()
        );
        this.set(
            49,
            Items.of(Material.PLAYER_HEAD).skull(this.viewer).name("<gold><b>Your Stats").lore("<yellow>▶ Click to view").build(),
            t -> new StatsMenu(this.plugin, this.viewer, this.viewer.getUniqueId(), this.viewer.getName()).open()
        );
    }

    private static List<String> topLines(List<Database.LbEntry> list, int n) {
        List<String> lore = new ArrayList<>();
        if (list.isEmpty()) {
            lore.add("<dark_gray>No ranked players yet.");
            return lore;
        } else {
            for (int i = 0; i < Math.min(n, list.size()); i++) {
                Database.LbEntry e = list.get(i);
                Tier t = Tier.byIndex(e.tier());
                String place = i == 0 ? "<gold>" : (i == 1 ? "<#C0C0C0>" : (i == 2 ? "<#CD7F32>" : "<gray>"));
                lore.add(
                    place + "#" + (i + 1) + " <white>" + Text.esc(e.name()) + " <dark_gray>- " + (t == null ? "" : t.mm() + " ") + "<gray>" + Fmt.num(e.elo())
                );
            }

            return lore;
        }
    }

    private void drawBoard() {
        List<Database.LbEntry> list = "overall".equals(this.kitId) ? this.plugin.leaderboards().overall() : this.plugin.leaderboards().top(this.kitId);
        int[] slots = Ui.innerSlots(6);
        int per = slots.length;
        int start = this.page * per;

        for (int i = 0; i < per && start + i < list.size(); i++) {
            Database.LbEntry e = list.get(start + i);
            int rank = start + i + 1;
            Tier t = Tier.byIndex(e.tier());
            int total = e.wins() + e.losses();
            this.set(
                slots[i],
                Items.of(Material.PLAYER_HEAD)
                    .skull(Bukkit.getOfflinePlayer(e.uuid()))
                    .name("<gold>#" + rank + " <white><b>" + Text.esc(e.name()))
                    .lore(
                        "<gray>Tier: " + (t == null ? "<dark_gray>-" : t.mm()),
                        "<gray>Rank points: <white>" + Fmt.num(e.elo()),
                        "<gray>Wins: <green>" + e.wins() + " <gray>Losses: <red>" + e.losses(),
                        "<gray>Win rate: <white>" + (total == 0 ? "-" : Fmt.percent((double)e.wins() / total)),
                        "",
                        "<yellow>▶ Click for full stats"
                    )
                    .build(),
                c -> this.openStats(e)
            );
        }

        if (list.isEmpty()) {
            this.set(22, Ui.info(Material.PAPER, "<gray>No ranked players yet", "<dark_gray>Play ranked to get on the board!"));
        }

        if (this.page > 0) {
            this.set(45, Ui.prev(this.page), c -> {
                this.page--;
                this.redraw();
            });
        }

        if (start + per < list.size()) {
            this.set(53, Ui.next(this.page), c -> {
                this.page++;
                this.redraw();
            });
        }

        this.set(49, Ui.back(), c -> new LeaderboardMenu(this.plugin, this.viewer, null).open());
    }

    private void openStats(Database.LbEntry e) {
        new StatsMenu(this.plugin, this.viewer, e.uuid(), e.name()).open();
    }
}
