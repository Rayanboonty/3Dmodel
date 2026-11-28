package net.vortexrealm.survival.smp;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.vortexrealm.survival.VortexSMP;
import net.vortexrealm.survival.config.Cfg;
import net.vortexrealm.survival.config.Opt;
import net.vortexrealm.survival.util.Fmt;
import net.vortexrealm.survival.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

/** The SMP sidebar, the tab header/footer and the join/leave messages. */
public final class Sidebar implements Listener {
    private static final String SEP = "<dark_gray><st>                        </st>";
    private final VortexSMP plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Component[]> last = new HashMap<>();
    private int frame;

    public Sidebar(VortexSMP plugin) {
        this.plugin = plugin;
    }

    /** A gradient that slowly moves through the server name. */
    private String title() {
        double phase = Math.sin(this.frame * 0.12) * 0.9;
        return "<b><gradient:#7CFF6B:#2E9B3A:#B8FFB0:" + String.format(java.util.Locale.ROOT, "%.2f", phase) + ">"
            + Text.esc(Cfg.s(Opt.SERVER_NAME).toUpperCase(java.util.Locale.ROOT)) + " SMP</gradient></b>";
    }

    private List<String> lines(Player p) {
        long minutes = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
        int lobby = this.plugin.lobbyPlayers();
        List<String> l = new ArrayList<>();
        l.add(SEP);
        l.add(" <gray>Player: <white>" + Text.esc(p.getName()));
        l.add(" <gray>Playtime: <white>" + Fmt.duration(minutes * 60L));
        if (this.plugin.combatTag() != null && this.plugin.combatTag().tagged(p)) {
            l.add(" <red><b>⚔ In combat</b> <white>" + this.plugin.combatTag().secondsLeft(p) + "s");
        }

        l.add("");
        l.add(" <gray>Online: <white>" + Bukkit.getOnlinePlayers().size());
        l.add(" <gray>Ping: <white>" + p.getPing() + "ms");
        if (lobby >= 0) {
            l.add(" <gray>Lobby: <white>" + lobby + " <dark_gray>(/hub)");
        }

        l.add("");
        l.add(" <#7CFF6B>" + Text.esc(Cfg.s(Opt.SERVER_IP)));
        l.add(SEP + " ");
        return l;
    }

    public void tick() {
        this.frame++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                this.update(p);
            } catch (RuntimeException var4) {
                this.plugin.getLogger().log(java.util.logging.Level.FINE, "Sidebar failed for " + p.getName(), (Throwable)var4);
            }
        }
    }

    private void update(Player p) {
        Realm realm = this.plugin.realm();
        if (realm.on() && !realm.smp(p.getWorld())) {
            // outside the SMP, VortexRealm's own sidebar and tab show
            Scoreboard own = this.boards.remove(p.getUniqueId());
            this.last.remove(p.getUniqueId());
            if (own != null && p.getScoreboard() == own) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }

            return;
        }

        if (Cfg.bool(Opt.TAB) && !realm.on()) {
            String discord = Cfg.s(Opt.LINK_DISCORD).replaceFirst("^https?://", "");
            p.sendPlayerListHeaderAndFooter(
                Text.mm("\n" + this.title() + "\n<gray>Survival <dark_gray>• <white>" + Bukkit.getOnlinePlayers().size() + " <gray>online\n"),
                Text.mm("\n<gray>/hub <dark_gray>» <white>back to the lobby" + (discord.isBlank() ? "" : "\n<#5865F2>" + Text.esc(discord)) + "\n<#7CFF6B>" + Text.esc(Cfg.s(Opt.SERVER_IP)) + "\n")
            );
        }

        Scoreboard sb = this.boards.get(p.getUniqueId());
        if (!Cfg.bool(Opt.SMP_SIDEBAR)) {
            if (sb != null && p.getScoreboard() == sb) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }

            this.boards.remove(p.getUniqueId());
            return;
        }

        if (sb == null) {
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective o = sb.registerNewObjective("vsmp_side", Criteria.DUMMY, Component.text(" "));
            o.numberFormat(NumberFormat.blank());
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            this.boards.put(p.getUniqueId(), sb);
            this.last.put(p.getUniqueId(), new Component[15]);
        }

        if (p.getScoreboard() != sb) {
            p.setScoreboard(sb);
        }

        Objective side = sb.getObjective("vsmp_side");
        if (side == null) {
            return;
        }

        side.displayName(Text.mm(this.title()));
        List<String> lines = this.lines(p);
        Component[] prev = this.last.get(p.getUniqueId());
        int n = Math.min(15, lines.size());

        for (int i = 0; i < n; i++) {
            Component line = Text.mm(lines.get(i));
            if (!line.equals(prev[i])) {
                Score sc = side.getScore("#v" + i);
                sc.customName(line);
                sc.setScore(15 - i);
                prev[i] = line;
            }
        }

        for (int i = n; i < 15; i++) {
            if (prev[i] != null) {
                sb.resetScores("#v" + i);
                prev[i] = null;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        if (Cfg.bool(Opt.JOIN_MESSAGES) && !this.plugin.realm().on()) {
            Player p = e.getPlayer();
            e.joinMessage(p.hasPlayedBefore()
                ? Text.mm("<dark_gray>[<green>+</green>]</dark_gray> <white>" + Text.esc(p.getName()))
                : Text.mm("<dark_gray>[<gold>★</gold>]</dark_gray> <gold>" + Text.esc(p.getName()) + " <gray>joined the SMP for the first time! Welcome!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent e) {
        this.boards.remove(e.getPlayer().getUniqueId());
        this.last.remove(e.getPlayer().getUniqueId());
        if (Cfg.bool(Opt.JOIN_MESSAGES) && !this.plugin.realm().on()) {
            e.quitMessage(Text.mm("<dark_gray>[<red>-</red>]</dark_gray> <gray>" + Text.esc(e.getPlayer().getName())));
        }
    }
}
