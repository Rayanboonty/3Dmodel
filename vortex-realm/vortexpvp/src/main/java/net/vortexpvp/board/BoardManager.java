package net.vortexpvp.board;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.KitStats;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.data.Setting;
import net.vortexpvp.ffa.FfaArena;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.match.Match;
import net.vortexpvp.match.MatchStats;
import net.vortexpvp.match.MatchTeam;
import net.vortexpvp.queue.QueueManager;
import net.vortexpvp.rank.Tier;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Session;
import net.vortexpvp.social.Ranks;
import net.vortexpvp.util.Fmt;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

public final class BoardManager {
    private static final int MAX_LINES = 15;
    private static final String SEP = "<dark_gray><st>                         </st>";
    private final VortexPvP plugin;
    private final Map<UUID, BoardManager.Board> boards = new HashMap<>();
    private int ticks;

    public BoardManager(VortexPvP plugin) {
        this.plugin = plugin;
    }

    private static String entry(int i) {
        return "#pp" + i;
    }

    private String teamName(Player p) {
        return "pp" + this.plugin.sessions().get(p).id;
    }

    public void attach(Player p) {
        if (this.boards.containsKey(p.getUniqueId())) {
            this.refreshNametag(p);
        } else {
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective side = sb.registerNewObjective("vp_side", Criteria.DUMMY, this.title());
            side.numberFormat(NumberFormat.blank());
            Objective health = sb.registerNewObjective("vp_hp", Criteria.HEALTH, Text.mm("<red>❤"), RenderType.HEARTS);
            BoardManager.Board b = new BoardManager.Board(sb, side, health);
            this.boards.put(p.getUniqueId(), b);
            p.setScoreboard(sb);

            for (Player o : Bukkit.getOnlinePlayers()) {
                if (this.boards.containsKey(o.getUniqueId())) {
                    this.applyTeam(b.sb, p, o);
                    if (!o.equals(p)) {
                        this.applyTeam(this.boards.get(o.getUniqueId()).sb, o, p);
                    }
                }
            }

            this.update(p);
        }
    }

    public void detach(Player p) {
        BoardManager.Board b = this.boards.remove(p.getUniqueId());
        String team = this.teamName(p);

        for (BoardManager.Board other : this.boards.values()) {
            Team t = other.sb.getTeam(team);
            if (t != null) {
                t.unregister();
            }
        }

        if (b != null && p.isOnline() && p.getScoreboard() == b.sb) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void shutdown() {
        for (UUID u : new ArrayList<>(this.boards.keySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                this.detach(p);
            }
        }

        this.boards.clear();
    }

    public void refreshNametag(Player target) {
        if (this.boards.containsKey(target.getUniqueId())) {
            for (Entry<UUID, BoardManager.Board> e : this.boards.entrySet()) {
                this.applyTeam(e.getValue().sb, Bukkit.getPlayer(e.getKey()), target);
            }
        }
    }

    public void refreshViewer(Player viewer) {
        BoardManager.Board b = this.boards.get(viewer.getUniqueId());
        if (b != null) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (this.boards.containsKey(o.getUniqueId())) {
                    this.applyTeam(b.sb, viewer, o);
                }
            }
        }
    }

    public Component nameSuffix(Player viewer, Player target) {
        Component out = (Component)(this.plugin.welcome() == null ? Component.empty() : this.plugin.welcome().badge(target.getUniqueId(), viewer));
        PvPProfile tp = this.plugin.profiles().getIfLoaded(target.getUniqueId());
        if (Cfg.bool(Opt.COUNTRY_ON_NAMETAG) && this.plugin.countries() != null && (tp == null || tp.on(Setting.NAMETAG_FLAG))) {
            out = out.append(this.plugin.countries().tag(target.getUniqueId(), viewer));
        }

        return out;
    }

    private void applyTeam(Scoreboard sb, Player viewer, Player target) {
        String name = this.teamName(target);
        Team t = sb.getTeam(name);
        if (t == null) {
            t = sb.registerNewTeam(name);
        }

        if (!t.hasEntry(target.getName())) {
            t.addEntry(target.getName());
        }

        Component prefix = this.prefix(target);
        if (!Objects.equals(t.prefix(), prefix)) {
            t.prefix(prefix);
        }

        Component suffix = (Component)(this.plugin.worlds().managed(target.getWorld()) ? this.nameSuffix(viewer, target) : Component.empty());
        if (!Objects.equals(t.suffix(), suffix)) {
            t.suffix(suffix);
        }

        NamedTextColor color = this.color(target);
        if (!t.hasColor() || t.color() != color) {
            t.color(color);
        }

        OptionStatus collision = Cfg.bool(Opt.PLAYER_COLLISION) ? OptionStatus.ALWAYS : OptionStatus.NEVER;
        if (t.getOption(Option.COLLISION_RULE) != collision) {
            t.setOption(Option.COLLISION_RULE, collision);
        }
    }

    public Kit displayKit(Player p) {
        Session s = this.plugin.sessions().get(p);
        if (s.match == null || s.state != PlayerState.MATCH && s.state != PlayerState.PREMATCH) {
            if (s.state == PlayerState.FFA && s.ffa != null) {
                return this.plugin.ffa().kitOf(s.ffa);
            } else {
                PvPProfile prof = this.plugin.profiles().get(p);
                String pref = prof.get(Setting.NAMETAG_KIT);
                if (!"auto".equals(pref)) {
                    Kit k = this.plugin.kits().get(pref);
                    if (k != null) {
                        return k;
                    }
                }

                Kit best = this.plugin.kits().get(prof.bestKit());
                if (best != null) {
                    return best;
                } else {
                    Kit sword = this.plugin.kits().get("sword");
                    if (sword != null && sword.ranked) {
                        return sword;
                    } else {
                        for (Kit k : this.plugin.kits().sorted()) {
                            if (k.enabled && k.ranked) {
                                return k;
                            }
                        }

                        return null;
                    }
                }
            }
        } else {
            return s.match.kit;
        }
    }

    private Component prefix(Player p) {
        Session s = this.plugin.sessions().get(p);
        if (s.state != PlayerState.OUTSIDE && this.plugin.worlds().managed(p.getWorld())) {
            Component badge = (Component)(this.plugin.staff() == null ? Component.empty() : this.plugin.staff().badge(p));
            if (Cfg.bool(Opt.RANK_ON_NAMETAG) && this.plugin.serverRanks() != null) {
                Ranks.Rank rank = this.plugin.serverRanks().of(p);
                if (!rank.isDefault()) {
                    badge = rank.prefix();
                }
            }

            if (this.plugin.hackerPool() != null && this.plugin.hackerPool().pooled(p)) {
                return this.plugin.hackerPool().badge(p);
            } else if (!Cfg.bool(Opt.NAMETAG_RANKS)) {
                return badge;
            } else {
                PvPProfile prof = this.plugin.profiles().get(p);
                Component out = badge;
                boolean any = false;

                for (Kit kit : this.tagKits(p)) {
                    Tier tier = prof.tierIn(kit.id);
                    if (tier != null) {
                        any = true;
                        out = out.append(kit.iconComponent()).append((Component)Component.space()).append(tierTag(tier)).append((Component)Component.space());
                    }
                }

                if (any && this.plugin.social() != null && prof.on(Setting.NAMETAG_RANK)) {
                    int pos = this.plugin.social().overallRank(p.getUniqueId());
                    if (pos > 0) {
                        out = out.append(Text.mm("<#7CFC00>#" + pos + " "));
                    }
                }

                return any ? out.append(Text.mm("<dark_gray>| ")) : out;
            }
        } else {
            return Component.empty();
        }
    }

    private List<Kit> tagKits(Player p) {
        Session s = this.plugin.sessions().get(p);
        List<Kit> out = new ArrayList<>();
        Kit forced = null;
        if (s.match == null || s.state != PlayerState.MATCH && s.state != PlayerState.PREMATCH) {
            if (s.state == PlayerState.FFA && s.ffa != null) {
                forced = this.plugin.ffa().kitOf(s.ffa);
            }
        } else {
            forced = s.match.kit;
        }

        PvPProfile prof = this.plugin.profiles().get(p);
        String pref = prof.get(Setting.NAMETAG_KIT);
        if (forced == null && !"auto".equals(pref)) {
            forced = this.plugin.kits().get(pref);
        }

        if (forced != null) {
            out.add(forced);
            return out;
        } else {
            List<Entry<String, KitStats>> ranked = new ArrayList<>();

            for (Entry<String, KitStats> e : prof.kits.entrySet()) {
                Kit k = this.plugin.kits().get(e.getKey());
                if (k != null && k.enabled && k.ranked && e.getValue().ranked()) {
                    ranked.add(e);
                }
            }

            ranked.sort(
                Comparator.<Entry<String, KitStats>>comparingInt(ex -> -((KitStats)ex.getValue()).tier).thenComparingInt(ex -> -((KitStats)ex.getValue()).elo)
            );

            for (Entry<String, KitStats> ex : ranked) {
                if (out.size() < 2) {
                    out.add(this.plugin.kits().get(ex.getKey()));
                }
            }

            if (out.isEmpty()) {
                Kit fallback = this.displayKit(p);
                if (fallback != null) {
                    out.add(fallback);
                }
            }

            return out;
        }
    }

    public static Component tierTag(Tier t) {
        String from = switch (t.number()) {
            case 1 -> "#FFF6B8";
            case 2 -> "#EBCBFF";
            case 3 -> "#C2ECFF";
            default -> null;
        };
        if (from == null) {
            return Text.mm(t.step() == 2 ? "<b>" + t.mm() + "</b>" : t.mm());
        } else {
            boolean bold = t.step() == 2 || t.number() == 1;
            Component c = Text.mm((bold ? "<b>" : "") + "<gradient:" + from + ":" + t.color + ">" + t.label + "</gradient>" + (bold ? "</b>" : ""));
            if (t.number() == 1) {
                return c.shadowColor(ShadowColor.shadowColor(t.step() == 2 ? -8765952 : -9743872));
            } else {
                return t.number() == 2 && t.step() == 2 ? c.shadowColor(ShadowColor.shadowColor(-12972186)) : c;
            }
        }
    }

    private NamedTextColor color(Player p) {
        Session s = this.plugin.sessions().get(p);
        if (s.match != null && s.state == PlayerState.MATCH && !s.match.isOneVsOne()) {
            MatchTeam t = s.match.teamOf(p.getUniqueId());
            if (t != null) {
                return t.color;
            }
        }

        return NamedTextColor.WHITE;
    }

    private Component title() {
        String tpl = this.plugin.smpBoard() == null ? null : this.plugin.smpBoard().title("PRACTICE");
        if (tpl != null && !tpl.isBlank()) {
            try {
                return Text.shadow(Text.mm(tpl.replace("Vortex Realm", Text.esc(Cfg.s(Opt.SERVER_NAME)))), -12637696);
            } catch (RuntimeException var3) {
            }
        }

        return Text.shadow(Text.mm("<gold><b>" + Text.esc(Cfg.s(Opt.SERVER_NAME)) + "</b>"), -12637696);
    }

    public void tick() {
        this.ticks++;
        boolean resync = this.ticks % 10 == 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            BoardManager.Board b = this.boards.get(p.getUniqueId());
            if (b != null) {
                if (p.getScoreboard() != b.sb) {
                    p.setScoreboard(b.sb);
                    b.title = null;
                    Arrays.fill(b.last, null);
                    b.sideShown = false;
                    b.healthShown = false;
                    b.side.setDisplaySlot(null);
                    b.health.setDisplaySlot(null);
                }

                if (resync) {
                    try {
                        for (Player o : Bukkit.getOnlinePlayers()) {
                            if (this.boards.containsKey(o.getUniqueId())) {
                                this.applyTeam(b.sb, p, o);
                            }
                        }
                    } catch (RuntimeException var8) {
                        this.plugin.getLogger().log(Level.WARNING, "Nametag resync failed", (Throwable)var8);
                    }
                }
            }
        }

        for (Player px : Bukkit.getOnlinePlayers()) {
            if (this.boards.containsKey(px.getUniqueId())) {
                try {
                    this.update(px);
                } catch (RuntimeException var7) {
                    this.plugin.getLogger().log(Level.WARNING, "Scoreboard update failed for " + px.getName(), (Throwable)var7);
                }
            }
        }
    }

    private void update(Player p) {
        BoardManager.Board b = this.boards.get(p.getUniqueId());
        if (b != null) {
            PvPProfile prof = this.plugin.profiles().get(p);
            Session s = this.plugin.sessions().get(p);
            boolean wantSide = Cfg.bool(Opt.SIDEBAR) && prof.on(Setting.SCOREBOARD) && s.state != PlayerState.OUTSIDE;
            if (wantSide != b.sideShown) {
                b.side.setDisplaySlot(wantSide ? DisplaySlot.SIDEBAR : null);
                b.sideShown = wantSide;
            }

            boolean wantHealth = prof.on(Setting.HEALTH_TAGS)
                && (s.state == PlayerState.MATCH || s.state == PlayerState.FFA || s.state == PlayerState.SPECTATING);
            if (wantHealth != b.healthShown) {
                b.health.setDisplaySlot(wantHealth ? DisplaySlot.BELOW_NAME : null);
                b.healthShown = wantHealth;
            }

            if (wantHealth && b.healthWritable) {
                try {
                    for (Player o : Bukkit.getOnlinePlayers()) {
                        Score sc = b.health.getScore(o.getName());
                        int hp = (int)Math.ceil(o.getHealth());
                        if (!sc.isScoreSet() || sc.getScore() != hp) {
                            sc.setScore(hp);
                        }
                    }
                } catch (IllegalArgumentException | IllegalStateException var13) {
                    b.healthWritable = false;
                }
            }

            if (wantSide) {
                Component title = this.plugin.hackerPool() != null && this.plugin.hackerPool().pooled(p)
                    ? this.plugin.hackerPool().sidebarTitle()
                    : this.title();
                if (!title.equals(b.title)) {
                    b.side.displayName(title);
                    b.title = title;
                }

                List<Component> lines = this.lines(p, s, prof);
                int n = Math.min(15, lines.size());

                for (int i = 0; i < n; i++) {
                    Component line = lines.get(i);
                    if (!line.equals(b.last[i])) {
                        Score sc = b.side.getScore(entry(i));
                        sc.customName(line);
                        sc.setScore(15 - i);
                        b.last[i] = line;
                    }
                }

                for (int ix = n; ix < b.lines; ix++) {
                    b.sb.resetScores(entry(ix));
                    b.last[ix] = null;
                }

                b.lines = n;
            }
        }
    }

    private List<Component> lines(Player p, Session s, PvPProfile prof) {
        List<String> tpl = this.plugin.smpBoard() == null ? null : this.plugin.smpBoard().layout("PRACTICE");
        if (tpl != null && s.state == PlayerState.LOBBY) {
            return this.templatedLobby(p, prof, tpl);
        } else {
            List<Component> out = new ArrayList<>();
            out.add(Text.mm("<dark_gray><st>                         </st>"));
            switch (s.state) {
                case QUEUE:
                    this.queueLines(p, out);
                    break;
                case PREMATCH:
                    this.prematchLines(p, s, out);
                    break;
                case MATCH:
                    this.matchLines(p, s, out);
                    break;
                case FFA:
                    this.ffaLines(p, s, prof, out);
                    break;
                case SPECTATING:
                    this.spectateLines(s, out);
                    break;
                case EDITING:
                    out.add(Text.mm(" <gold><b>Kit Editor"));
                    out.add(Text.mm(" <gray>Editing:"));
                    out.add(Component.text(" ").append(s.editing == null ? Text.mm("<gray>-") : s.editing.label()));
                    out.add(Component.empty());
                    out.add(Text.mm(" <gray>Close the menu"));
                    out.add(Text.mm(" <gray>to <green>save</green>."));
                    break;
                default:
                    this.lobbyLines(prof, out);
            }

            out.add(Component.text(" "));
            out.add(Text.mm(" <gold>" + Text.esc(Cfg.s(Opt.SERVER_IP))));
            out.add(Text.mm("<dark_gray><st>                         </st> "));
            return out;
        }
    }

    private List<Component> templatedLobby(Player p, PvPProfile prof, List<String> tpl) {
        List<Entry<String, KitStats>> ranked = new ArrayList<>();

        for (Entry<String, KitStats> e : prof.kits.entrySet()) {
            if (e.getValue().tier >= 0 && this.plugin.kits().get(e.getKey()) != null) {
                ranked.add(e);
            }
        }

        ranked.sort(Comparator.<Entry<String, KitStats>>comparingInt(ex -> -ex.getValue().tier).thenComparingInt(ex -> -ex.getValue().elo));
        List<TagResolver> res = new ArrayList<>();
        res.add(Placeholder.unparsed("playing", String.valueOf(this.plugin.matches().playingTotal() + this.plugin.ffa().playing())));
        res.add(Placeholder.unparsed("queue", String.valueOf(this.plugin.queues().total())));

        for (int i = 0; i < 3; i++) {
            Component line;
            if (i < ranked.size()) {
                Kit k = this.plugin.kits().get(ranked.get(i).getKey());
                line = ((TextComponent)((TextComponent)Component.text(" ").append(k.iconComponent()))
                        .append(Text.mm(" <white>" + Text.esc(k.displayName) + " <dark_gray>» ")))
                    .append(tierTag(ranked.get(i).getValue().tier()));
            } else if (i == 0) {
                line = Text.mm(" <gray>Unranked - play a match!");
            } else {
                line = Component.empty();
            }

            res.add(Placeholder.component("rank_" + (i + 1), line));
        }

        TagResolver extra = TagResolver.resolver(res);
        List<Component> out = new ArrayList<>();

        for (String line : tpl) {
            out.add(this.plugin.smpBoard().render(p, "PVP", line, extra));
        }

        return out;
    }

    private void lobbyLines(PvPProfile prof, List<Component> out) {
        out.add(Text.mm(" <gray>Online: <green>" + Bukkit.getOnlinePlayers().size()));
        out.add(Text.mm(" <gray>Playing: <green>" + (this.plugin.matches().playingTotal() + this.plugin.ffa().playing())));
        out.add(Text.mm(" <gray>In Queue: <green>" + this.plugin.queues().total()));
        out.add(Component.empty());
        out.add(Text.mm(" <gold><b>Your Ranks"));
        List<Entry<String, KitStats>> ranked = new ArrayList<>();

        for (Entry<String, KitStats> e : prof.kits.entrySet()) {
            if (e.getValue().tier >= 0 && this.plugin.kits().get(e.getKey()) != null) {
                ranked.add(e);
            }
        }

        ranked.sort(Comparator.<Entry<String, KitStats>>comparingInt(ex -> -ex.getValue().tier).thenComparingInt(ex -> -ex.getValue().elo));
        if (ranked.isEmpty()) {
            out.add(Text.mm(" <gray>Unranked - play"));
            out.add(Text.mm(" <gray>a <gold>ranked</gold> match!"));
        }

        for (int i = 0; i < Math.min(3, ranked.size()); i++) {
            Kit k = this.plugin.kits().get(ranked.get(i).getKey());
            Tier t = ranked.get(i).getValue().tier();
            out.add(
                ((TextComponent)Component.text(" ").append(k.iconComponent()))
                    .append(Text.mm(" <white>" + Text.esc(k.displayName) + " <dark_gray>» " + t.mm()))
            );
        }
    }

    private void queueLines(Player p, List<Component> out) {
        List<QueueManager.Entry> entries = this.plugin.queues().entries(p);
        boolean ranked = !entries.isEmpty() && entries.get(0).ranked();
        out.add(Text.mm(" <gold><b>Searching..."));
        out.add(Text.mm(" <gray>Mode: " + (ranked ? "<gold>Ranked" : "<green>Unranked")));
        out.add(Component.text(" ").append(this.plugin.queues().describe(p)));
        out.add(Text.mm(" <gray>Time: <white>" + Fmt.clock(this.plugin.queues().waitedSeconds(p))));
        if (ranked) {
            Kit k = this.plugin.kits().get(entries.get(0).kit());
            KitStats ks = k == null ? null : this.plugin.profiles().get(p).statsOrNull(k.id);
            out.add(Text.mm(" <gray>Rank points: <white>" + (ks == null ? 0 : ks.elo)));
        }

        out.add(Text.mm(" <gray>Ping: " + Fmt.pingColored(p.getPing())));
    }

    private void prematchLines(Player p, Session s, List<Component> out) {
        Match m = s.match;
        out.add(Text.mm(" <gold><b>Match Found"));
        if (m != null) {
            Player opp = m.opponent(p);
            if (opp != null) {
                out.add(Text.mm(" <gray>Opponent: <green>" + Text.esc(opp.getName())));
            }

            out.add(((TextComponent)Component.text(" ").append(Text.mm("<gray>Kit: "))).append(m.kit.label()));
            if (m.preMatch() != null) {
                out.add(((TextComponent)Component.text(" ").append(Text.mm("<gray>Phase: "))).append(m.preMatch().status()));
            }
        }
    }

    private void matchLines(Player p, Session s, List<Component> out) {
        Match m = s.match;
        if (m != null) {
            if (m.isOneVsOne()) {
                Player opp = m.opponent(p);
                out.add(Text.mm(" <gray>Opponent: <red>" + (opp == null ? "?" : Text.esc(opp.getName()))));
                out.add(Text.mm(" <gray>Their Ping: " + (opp == null ? "<gray>-" : Fmt.pingColored(opp.getPing()))));
                out.add(Text.mm(" <gray>Your Ping: " + Fmt.pingColored(p.getPing())));
                out.add(Component.empty());
                int my = m.scoreOf(p);
                int their = m.opponentScore(p);
                String style = m.scorePulsing() ? "<b>" : "";
                out.add(Text.mm(" <gray>Score: " + style + "<green>" + my + "</green> <dark_gray>-</dark_gray> <red>" + their));
                out.add(Text.mm(" <gray>First To: <gold>" + m.roundsToWin()));
            } else {
                MatchTeam mine = m.teamOf(p.getUniqueId());
                int enemies = 0;

                for (MatchTeam t : m.teams) {
                    if (t != mine) {
                        enemies += t.alive.size();
                    }
                }

                out.add(Text.mm(" <gray>Team Alive: <green>" + (mine == null ? 0 : mine.alive.size())));
                out.add(Text.mm(" <gray>Enemies Alive: <red>" + enemies));
                out.add(Text.mm(" <gray>Ping: " + Fmt.pingColored(p.getPing())));
                out.add(Component.empty());
            }

            out.add(((TextComponent)Component.text(" ").append(Text.mm("<gray>Kit: "))).append(m.kit.label()));
            out.add(Text.mm(" <gray>Time: <white>" + Fmt.clock(m.fightSeconds())));
            if (m.kit.countOf(Material.TOTEM_OF_UNDYING) > 0) {
                MatchStats mine = m.stats(p.getUniqueId());
                Player opp = m.isOneVsOne() ? m.opponent(p) : null;
                String theirs = opp == null ? "" : " <dark_gray>/</dark_gray> <red>" + m.stats(opp.getUniqueId()).totemsPopped;
                out.add(Text.mm(" <gray>Pops: <green>" + mine.totemsPopped + theirs));
            }
        }
    }

    private void ffaLines(Player p, Session s, PvPProfile prof, List<Component> out) {
        FfaArena a = s.ffa;
        out.add(Text.mm(" <gray>Arena: <gold>" + (a == null ? "-" : Text.esc(a.displayName))));
        out.add(Text.mm(" <gray>Kills: <green>" + prof.ffaKills + "  <gray>Deaths: <red>" + prof.ffaDeaths));
        out.add(Text.mm(" <gray>Streak: <gold>" + prof.ffaStreak + " <dark_gray>(best " + prof.ffaBestStreak + ")"));
        out.add(Text.mm(" <gray>Players: <white>" + (a == null ? 0 : a.players.size())));
        out.add(Text.mm(" <gray>Ping: " + Fmt.pingColored(p.getPing())));
        if (s.inCombat()) {
            out.add(Text.mm(" <gray>Combat: <red>" + s.combatSecondsLeft() + "s"));
        }
    }

    private void spectateLines(Session s, List<Component> out) {
        Match m = s.spectating;
        out.add(Text.mm(" <gold><b>Spectating"));
        if (m != null) {
            out.add(Text.mm(" <white>" + Text.esc(m.title())));
            if (m.isOneVsOne()) {
                out.add(Text.mm(" <gray>Score: <green>" + m.teams.get(0).score + " <dark_gray>-</dark_gray> <red>" + m.teams.get(1).score));
            }

            out.add(((TextComponent)Component.text(" ").append(Text.mm("<gray>Kit: "))).append(m.kit.label()));
            out.add(Text.mm(" <gray>Time: <white>" + Fmt.clock(m.fightSeconds())));
            out.add(Text.mm(" <gray>/leave <dark_gray>to stop"));
        }
    }

    private static final class Board {
        final Scoreboard sb;
        final Objective side;
        final Objective health;
        final Component[] last = new Component[15];
        int lines;
        Component title;
        boolean sideShown;
        boolean healthShown;
        boolean healthWritable = true;

        Board(Scoreboard sb, Objective side, Objective health) {
            this.sb = sb;
            this.side = side;
            this.health = health;
        }
    }
}
