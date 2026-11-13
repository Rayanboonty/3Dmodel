package net.vortexrealm.board;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.data.PvPProfile;
import net.vortexrealm.data.Setting;
import net.vortexrealm.rank.Tier;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.social.Countries;
import net.vortexrealm.util.Fmt;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class SmpBoard {
    private static final int MAX = 15;
    private final VortexRealm plugin;
    private final File file;
    private final Map<String, String> titles = new LinkedHashMap<>();
    private final Map<String, List<String>> layouts = new LinkedHashMap<>();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Component[]> last = new HashMap<>();

    public SmpBoard(VortexRealm plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "scoreboards.yml");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        if (!this.file.exists()) {
            y.options()
                .setHeader(
                    List.of(
                        "Sidebar of the hub (PvP has its own).",
                        "Placeholders: <player> <rank> <country> <online> <here> <ping> <smp> (players on the SMP server)",
                        "<playtime> <world> <ip> <discord>  -  <mood:TEXT> = the mode's",
                        "gradient (tab.yml moods). A line '' is an empty line. Up to 15 lines."
                    )
                );
            String sep = "<dark_gray><st>                       </st>";
            def(
                y,
                "PRACTICE",
                "<gold><b>Vortex Realm</b>",
                List.of(
                    sep,
                    " <gray>Online: <green><online>",
                    " <gray>Playing: <green><playing>",
                    " <gray>In Queue: <green><queue>",
                    "",
                    " <gold><b>Your Ranks",
                    "<rank_1>",
                    "<rank_2>",
                    "<rank_3>",
                    "",
                    " <gold><ip>",
                    sep + " "
                )
            );
            def(
                y,
                "HUB",
                "<b><mood:VORTEX REALM></b>",
                List.of(
                    sep,
                    " <gray>Rank: <rank>",
                    " <gray>Country: <country>",
                    "",
                    " <mood:Online> <white><online>",
                    " <gray>Ping: <white><ping>ms",
                    "",
                    " <gray>SMP: <white><smp> <gray>playing",
                    " <gray>Compass <white>→ <gray>play",
                    "",
                    " <mood:<ip>>",
                    sep + " "
                )
            );

            try {
                y.save(this.file);
            } catch (IOException var5) {
                this.plugin.getLogger().log(Level.WARNING, "Could not write scoreboards.yml", (Throwable)var5);
            }
        }

        this.titles.clear();
        this.layouts.clear();
        ConfigurationSection s = y.getConfigurationSection("boards");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                this.titles.put(k.toUpperCase(Locale.ROOT), s.getString(k + ".title", k));
                this.layouts.put(k.toUpperCase(Locale.ROOT), s.getStringList(k + ".lines"));
            }
        }
    }

    public String design() {
        return YamlConfiguration.loadConfiguration(this.file).getString("design", "CLASSIC");
    }

    public void applyDesign(String id) {
        try {
            if (this.file.exists()) {
                Files.copy(this.file.toPath(), new File(this.plugin.getDataFolder(), "scoreboards-backup.yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException var6) {
            this.plugin.getLogger().log(Level.WARNING, "Could not back up scoreboards.yml", (Throwable)var6);
        }

        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);

        for (Entry<String, BoardDesigns.Board> e : BoardDesigns.build(id).entrySet()) {
            def(y, e.getKey(), e.getValue().title(), e.getValue().lines());
        }

        y.set("design", id);

        try {
            y.save(this.file);
        } catch (IOException var5) {
            this.plugin.getLogger().log(Level.WARNING, "Could not write scoreboards.yml", (Throwable)var5);
        }

        this.load();

        for (Component[] arr : this.last.values()) {
            Arrays.fill(arr, null);
        }
    }

    private static void def(YamlConfiguration y, String key, String title, List<String> lines) {
        y.set("boards." + key + ".title", title);
        y.set("boards." + key + ".lines", lines);
    }

    private String moods(String group, String text) {
        int i;
        while ((i = text.indexOf("<mood:")) >= 0) {
            int depth = 0;
            int end = -1;

            for (int j = i + 1; j < text.length(); j++) {
                char c = text.charAt(j);
                if (c == '<') {
                    depth++;
                } else if (c == '>') {
                    if (depth == 0) {
                        end = j;
                        break;
                    }

                    depth--;
                }
            }

            if (end < 0) {
                break;
            }

            String inner = text.substring(i + 6, end);
            String painted = this.plugin.tab() != null ? this.plugin.tab().paint(group, inner) : inner;
            text = text.substring(0, i) + painted + text.substring(end + 1);
        }

        return text;
    }

    private TagResolver placeholders(Player p, String group, int here) {
        Component rank = this.plugin.serverRanks() == null
            ? Text.mm("<gray>Member")
            : (this.plugin.serverRanks().of(p).isDefault() ? Text.mm("<gray>Member") : this.plugin.serverRanks().of(p).prefix());
        Countries.Country c = this.plugin.countries() == null ? null : this.plugin.countries().of(p.getUniqueId());
        long minutes = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60;
        String discord = Cfg.s(Opt.LINK_DISCORD).replaceFirst("^https?://", "");
        return TagResolver.resolver(
            Placeholder.unparsed("player", p.getName()),
            Placeholder.component("rank", rank),
            Placeholder.component("country", c == null ? Text.mm("<gray>-") : this.plugin.countries().flag(c, p)),
            Placeholder.unparsed("online", String.valueOf(Bukkit.getOnlinePlayers().size())),
            Placeholder.unparsed("here", String.valueOf(here)),
            Placeholder.unparsed("ping", String.valueOf(p.getPing())),
            Placeholder.unparsed("smp", this.smpCount()),
            Placeholder.unparsed("playtime", Fmt.duration(minutes * 60L)),
            Placeholder.unparsed("world", p.getWorld().getName()),
            Placeholder.unparsed("ip", Cfg.s(Opt.SERVER_IP)),
            Placeholder.unparsed("discord", discord)
        );
    }

    private String smpCount() {
        if (this.plugin.serverSelector() == null || this.plugin.serverLink() == null) {
            return "-";
        }

        net.vortexrealm.smp.ServerSelector.Entry e = this.plugin.serverSelector().get("smp");
        net.vortexrealm.smp.ServerLink.Ping ping = e == null ? null : this.plugin.serverLink().ping(e);
        return ping == null || !ping.online() ? "-" : String.valueOf(ping.players());
    }

    public List<String> layout(String key) {
        return this.layouts.get(key);
    }

    public String title(String key) {
        return this.titles.get(key);
    }

    public Component render(Player p, String group, String line, TagResolver extra) {
        try {
            return Text.mm(this.moods(group, line), TagResolver.resolver(this.placeholders(p, group, 0), extra));
        } catch (RuntimeException var6) {
            return Component.text(line);
        }
    }

    public void tick() {
        Map<String, Integer> count = new HashMap<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.plugin.tab() != null) {
                count.merge(this.plugin.tab().group(p), 1, Integer::sum);
            }
        }

        for (Player px : Bukkit.getOnlinePlayers()) {
            try {
                this.update(px, count);
            } catch (RuntimeException var5) {
                this.plugin.getLogger().log(Level.FINE, "Hub sidebar failed for " + px.getName(), (Throwable)var5);
            }
        }

        this.boards.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
        this.last.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
    }

    private void update(Player p, Map<String, Integer> count) {
        boolean outside = this.plugin.sessions().state(p) == PlayerState.OUTSIDE;
        String group = this.plugin.tab() == null ? "HUB" : this.plugin.tab().group(p);
        PvPProfile prof = this.plugin.profiles().getIfLoaded(p.getUniqueId());
        boolean want = outside && Cfg.bool(Opt.SMP_SIDEBAR) && this.layouts.containsKey(group) && (prof == null || prof.on(Setting.SCOREBOARD));
        Scoreboard sb = this.boards.get(p.getUniqueId());
        if (!want) {
            if (sb != null && p.getScoreboard() == sb) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }

            this.boards.remove(p.getUniqueId());
            this.last.remove(p.getUniqueId());
        } else {
            if (sb == null) {
                sb = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = sb.registerNewObjective("vr_side", Criteria.DUMMY, Component.text(" "));
                o.numberFormat(NumberFormat.blank());
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                this.boards.put(p.getUniqueId(), sb);
                this.last.put(p.getUniqueId(), new Component[15]);
            }

            if (p.getScoreboard() != sb) {
                p.setScoreboard(sb);
            }

            mirrorTeams(sb);
            this.flagTeams(sb, p);
            Objective side = sb.getObjective("vr_side");
            if (side != null) {
                TagResolver ph = this.placeholders(p, group, count.getOrDefault(group, 0));
                side.displayName(Text.mm(this.moods(group, this.titles.getOrDefault(group, group)), ph));
                List<String> lines = this.layouts.get(group);
                Component[] prev = this.last.get(p.getUniqueId());
                int n = Math.min(15, lines.size());

                for (int i = 0; i < n; i++) {
                    String raw = lines.get(i);
                    int split = raw.indexOf("||");
                    Component right = null;

                    Component line;
                    try {
                        line = Text.mm(this.moods(group, split < 0 ? raw : raw.substring(0, split)), ph);
                        if (split >= 0) {
                            right = Text.mm(this.moods(group, raw.substring(split + 2)), ph);
                        }
                    } catch (RuntimeException var20) {
                        line = Component.text(raw);
                    }

                    Component key = right == null ? line : line.append((Component)Component.text("\u0000")).append(right);
                    if (!key.equals(prev[i])) {
                        Score sc = side.getScore("#vr" + i);
                        sc.customName(line);
                        sc.numberFormat(right == null ? null : NumberFormat.fixed(right));
                        sc.setScore(15 - i);
                        prev[i] = key;
                    }
                }

                for (int i = n; i < 15; i++) {
                    if (prev[i] != null) {
                        sb.resetScores("#vr" + i);
                        prev[i] = null;
                    }
                }
            }
        }
    }

    private void flagTeams(Scoreboard sb, Player viewer) {
        if (this.plugin.boards() != null) {
            Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
            Set<String> here = new HashSet<>();

            for (Player o : viewer.getWorld().getPlayers()) {
                here.add(o.getName());
                Component tail = this.plugin.boards().nameSuffix(viewer, o);
                Team mainTeam = main.getEntryTeam(o.getName());
                if (mainTeam != null) {
                    Team mine = sb.getTeam(mainTeam.getName());
                    if (mine != null && mainTeam.getEntries().size() == 1) {
                        Component s = mainTeam.suffix().append(tail);
                        if (!mine.suffix().equals(s)) {
                            mine.suffix(s);
                        }
                    }
                } else {
                    String name = "vrf" + o.getUniqueId().toString().substring(0, 12);
                    Team t = sb.getTeam(name);
                    if (t == null) {
                        t = sb.registerNewTeam(name);
                    }

                    if (!t.hasEntry(o.getName())) {
                        t.addEntry(o.getName());
                    }

                    if (!t.suffix().equals(tail)) {
                        t.suffix(tail);
                    }
                }
            }

            for (Team tx : new ArrayList<>(sb.getTeams())) {
                if (tx.getName().startsWith("vrf") && tx.getEntries().stream().noneMatch(here::contains)) {
                    tx.unregister();
                }
            }
        }
    }

    private static void mirrorTeams(Scoreboard sb) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();

        for (Team t : main.getTeams()) {
            Team mine = sb.getTeam(t.getName());
            if (mine == null) {
                mine = sb.registerNewTeam(t.getName());
            }

            if (!mine.prefix().equals(t.prefix())) {
                mine.prefix(t.prefix());
            }

            if (t.getEntries().size() != 1 && !mine.suffix().equals(t.suffix())) {
                mine.suffix(t.suffix());
            }

            if (t.hasColor() && (!mine.hasColor() || mine.color() != t.color())) {
                mine.color(NamedTextColor.nearestTo(t.color()));
            }

            for (String e : t.getEntries()) {
                if (!mine.hasEntry(e)) {
                    mine.addEntry(e);
                }
            }

            for (String ex : new ArrayList<>(mine.getEntries())) {
                if (!t.hasEntry(ex)) {
                    mine.removeEntry(ex);
                }
            }
        }
    }
}
