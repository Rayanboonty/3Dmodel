package net.vortexpvp.plus;

import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.data.Database;
import net.vortexpvp.data.KitStats;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

/**
 * Seasons: a number and an end date (a month by default, ended automatically). Ending one saves the Hall of Fame
 * (top 10 of every ranked kit), rewards everyone by their best rank of the season (a title and shards), and soft
 * resets the rank points (halved by default) so the next season starts fresh without wiping anyone.
 */
public final class Seasons {
    private final VortexPvP plugin;
    private final PlusModule plus;
    private TextDisplay board;

    public Seasons(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
        YamlConfiguration y = this.doc();
        if (y.getInt("number", 0) == 0) {
            y.set("number", 1);
            y.set("start", System.currentTimeMillis());
            y.set("end", System.currentTimeMillis() + this.plus.config().i("seasons.days", 30) * 86400000L);
            this.plus.store().dirty("seasons");
        }
    }

    private YamlConfiguration doc() {
        return this.plus.store().doc("seasons");
    }

    public int number() {
        return this.doc().getInt("number", 1);
    }

    public long end() {
        return this.doc().getLong("end");
    }

    public String timeLeft() {
        long ms = Math.max(0L, this.end() - System.currentTimeMillis());
        Duration d = Duration.ofMillis(ms);
        return d.toDays() + "d " + d.toHoursPart() + "h " + d.toMinutesPart() + "m";
    }

    /** Every 10 minutes: ends the season when its time is up (if automatic). */
    public void check() {
        if (this.plus.config().bool("seasons.auto", true) && System.currentTimeMillis() >= this.end()) {
            this.endSeason(Bukkit.getConsoleSender());
        }
    }

    public void info(CommandSender to) {
        to.sendMessage(Text.mm("<gradient:#7B2FF7:#2FD3F7><b>Season " + this.number() + "</b></gradient> <gray>ends in <white>" + this.timeLeft()));
        to.sendMessage(Text.mm("<gray>Your best rank of the season gives a title and shards when it ends. <white>/season hof</white> <gray>for the Hall of Fame."));
        if (to instanceof Player p) {
            PlayerData d = this.plus.store().get(p);
            int best = d.seasonPeak.values().stream().mapToInt(Integer::intValue).max().orElse(-1);
            to.sendMessage(Text.mm("<gray>Your season peak: " + (best < 0 ? "<white>none yet" : Ladder.colored(best))));
        }
    }

    public void endSeason(CommandSender by) {
        int n = this.number();
        YamlConfiguration y = this.doc();
        // 1. Hall of Fame
        for (Kit k : this.plus.ladder().rankedKits()) {
            List<String> top = new ArrayList<>();
            try {
                for (Database.LbEntry e : this.plugin.leaderboards().top(k.id)) {
                    if (top.size() >= 10) {
                        break;
                    }

                    top.add(e.name() + ":" + e.elo());
                }
            } catch (RuntimeException var13) {
            }

            y.set("hof." + n + "." + k.id, top);
        }

        // 2. Rewards by season peak
        int rewarded = 0;
        for (UUID u : this.plus.store().all()) {
            PlayerData d = this.plus.store().get(u);
            int best = d.seasonPeak.values().stream().mapToInt(Integer::intValue).max().orElse(-1);
            if (best >= 0) {
                int rank = Ladder.rankOf(best);
                this.plus.progression().title(u, "S" + n + " " + Ladder.RANKS[rank]);
                this.plus.progression().shards(u, 100L * (rank + 1));
                this.plus.progression().track(u, "season_reward", null, 1L);
                rewarded++;
            }

            d.seasonPeak.clear();
            d.series.clear();
            d.shield.clear();
            d.season = n + 1;
            d.dirty = true;
        }

        // 3. Soft reset of the rank points: online/cached profiles here, everyone else in the database
        double f = this.plus.config().d("seasons.reset-factor", 0.5);
        for (PvPProfile p : this.plugin.profiles().loaded()) {
            for (KitStats k : p.kits.values()) {
                k.elo = (int)Math.round(k.elo * f);
                k.streak = 0;
            }

            p.dirty = true;
        }

        List<String> online = new ArrayList<>();
        for (PvPProfile p : this.plugin.profiles().loaded()) {
            online.add(p.uuid.toString());
        }

        this.plugin.db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE vp_kit_stats SET elo = CAST(elo * ? AS INTEGER), streak = 0")) {
                ps.setDouble(1, f);
                ps.executeUpdate();
            } catch (java.sql.SQLException mysqlCast) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE vp_kit_stats SET elo = ROUND(elo * ?), streak = 0")) {
                    ps.setDouble(1, f);
                    ps.executeUpdate();
                }
            }
        }).thenRun(() -> net.vortexpvp.util.Tasks.sync(() -> {
            // the loaded profiles were already reset in memory; save them so the database matches
            for (PvPProfile p : this.plugin.profiles().loaded()) {
                this.plugin.profiles().save(p);
            }
        }));
        y.set("number", n + 1);
        y.set("start", System.currentTimeMillis());
        y.set("end", System.currentTimeMillis() + this.plus.config().i("seasons.days", 30) * 86400000L);
        this.plus.store().dirty("seasons");
        this.plus.store().saveDirty();
        Bukkit.broadcast(Text.mm("<gradient:#FFD700:#FF6B00><b>SEASON " + n + " IS OVER!</b></gradient> <gray>" + rewarded
            + " players got their season rewards. <white>Season " + (n + 1) + "</white> <gray>starts now - rank points were soft reset."));
        this.plus.discord().post("🏁 **Season " + n + "** is over - " + rewarded + " players rewarded. Season " + (n + 1) + " has started!");
        by.sendMessage(Text.mm("<green>Season " + n + " ended: Hall of Fame saved, " + rewarded + " rewarded, rank points x" + f + "."));
        this.refreshBoard();
    }

    public void hallOfFame(Player p, int season) {
        YamlConfiguration y = this.doc();
        int s = season > 0 ? season : this.number() - 1;
        Gui.open(this.plugin, p, "<dark_gray>Hall of Fame - Season " + s, 6, g -> {
            int[] slots = g.inner();
            int i = 0;
            for (Kit k : this.plus.ladder().rankedKits()) {
                List<String> top = y.getStringList("hof." + s + "." + k.id);
                if (i >= slots.length) {
                    break;
                }

                List<String> lore = new ArrayList<>();
                for (int r = 0; r < top.size(); r++) {
                    String[] e = top.get(r).split(":");
                    lore.add((r == 0 ? "<gold>" : (r < 3 ? "<yellow>" : "<gray>")) + "#" + (r + 1) + " <white>" + Text.esc(e[0]) + " <dark_gray>(" + (e.length > 1 ? e[1] : "?") + ")");
                }

                if (lore.isEmpty()) {
                    lore.add("<gray>Nobody yet");
                }

                g.item(slots[i++], Items.of(Material.GOLDEN_HELMET).name(k.displayName).lore(lore).build());
            }

            if (s > 1) {
                g.item(45, Items.of(Material.ARROW).name("<yellow>« Season " + (s - 1)).build(), c -> this.hallOfFame(p, s - 1));
            }

            if (s < this.number() - 1) {
                g.item(53, Items.of(Material.ARROW).name("<yellow>Season " + (s + 1) + " »").build(), c -> this.hallOfFame(p, s + 1));
            }
        });
    }

    /** A floating top-10 board of the last season's overall champions, placed by an admin. */
    public void placeBoard(Location at) {
        YamlConfiguration y = this.doc();
        y.set("board", at.getWorld().getName() + ";" + at.getX() + ";" + at.getY() + ";" + at.getZ());
        this.plus.store().dirty("seasons");
        this.refreshBoard();
    }

    public void refreshBoard() {
        if (this.board != null && this.board.isValid()) {
            this.board.remove();
        }

        String at = this.doc().getString("board");
        if (at == null) {
            return;
        }

        String[] p = at.split(";");
        org.bukkit.World w = Bukkit.getWorld(p[0]);
        if (w == null) {
            return;
        }

        Location l = new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
        int s = this.number() - 1;
        StringBuilder sb = new StringBuilder("<gradient:#FFD700:#FF6B00><b>HALL OF FAME</b></gradient>\n<gray>Season " + Math.max(1, s) + "\n");
        for (Kit k : this.plus.ladder().rankedKits()) {
            List<String> top = this.doc().getStringList("hof." + s + "." + k.id);
            if (!top.isEmpty()) {
                sb.append("\n").append(k.displayName).append(" <dark_gray>» <white>").append(Text.esc(top.get(0).split(":")[0]));
            }
        }

        this.board = w.spawn(l, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            t.text(Text.mm(sb.toString()));
        });
    }

    public void remove() {
        if (this.board != null) {
            this.board.remove();
        }
    }
}
