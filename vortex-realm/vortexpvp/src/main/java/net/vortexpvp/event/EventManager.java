package net.vortexpvp.event;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.arena.Arena;
import net.vortexpvp.arena.ArenaTheme;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.KitStats;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.dialog.Dialogs;
import net.vortexpvp.dialog.LobbyDialogs;
import net.vortexpvp.fx.Fx;
import net.vortexpvp.fx.TotemPop;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.match.Match;
import net.vortexpvp.match.MatchTeam;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class EventManager implements TabExecutor {
    public static final String COLOSSEUM_ID = "champions";
    private final VortexPvP plugin;
    private final File file;
    private final Map<UUID, Integer> wins = new LinkedHashMap<>();
    private final List<EventManager.Past> history = new ArrayList<>();
    private EventManager.Event current;
    private long lastAuto = System.currentTimeMillis();

    public EventManager(VortexPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "events.yml");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection s = y.getConfigurationSection("wins");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try {
                    this.wins.put(UUID.fromString(k), s.getInt(k));
                } catch (IllegalArgumentException var7) {
                }
            }
        }

        for (Map<?, ?> m : y.getMapList("history")) {
            try {
                this.history
                    .add(
                        new EventManager.Past(
                            String.valueOf(m.get("name")),
                            String.valueOf(m.get("kit")),
                            String.valueOf(m.get("winner")),
                            String.valueOf(m.get("runner-up")),
                            Integer.parseInt(String.valueOf(m.get("players"))),
                            String.valueOf(m.get("format")),
                            Long.parseLong(String.valueOf(m.get("at")))
                        )
                    );
            } catch (RuntimeException var6) {
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        this.wins.forEach((u, n) -> y.set("wins." + u, n));
        List<Map<String, Object>> list = new ArrayList<>();

        for (EventManager.Past p : this.history) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.name());
            m.put("kit", p.kit());
            m.put("winner", p.winner());
            m.put("runner-up", p.runnerUp());
            m.put("players", p.players());
            m.put("format", p.format());
            m.put("at", p.at());
            list.add(m);
        }

        y.set("history", list);

        try {
            y.save(this.file);
        } catch (IOException var6) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save events.yml", (Throwable)var6);
        }
    }

    public int winsOf(UUID u) {
        return this.wins.getOrDefault(u, 0);
    }

    public List<EventManager.Past> history() {
        return Collections.unmodifiableList(this.history);
    }

    public EventManager.Event current() {
        return this.current != null && this.current.state != EventManager.State.ENDED ? this.current : null;
    }

    public boolean inEvent(UUID u) {
        EventManager.Event e = this.current();
        return e != null && e.players.contains(u) && !this.eliminated(e, u);
    }

    public String status() {
        EventManager.Event e = this.current;
        if (e != null && (e.state != EventManager.State.ENDED || System.currentTimeMillis() - e.endedAt <= 180000L)) {
            String n = "<#FFB547>" + Text.esc(e.name) + "</#FFB547>";

            return switch (e.state) {
                case JOINING -> n + " <gray>open: <white>" + e.players.size() + "/" + e.max + "</white>, starts in <white>" + this.secondsLeft(e) + "s";
                case QUALIFYING -> n + " <gray>qualifiers round <white>" + e.quals.size() + "/" + e.qualRounds;
                case RUNNING -> n + " <gray>" + this.roundName(e, e.rounds.size() - 1) + " <dark_gray>• <white>" + this.alive(e) + "</white> left";
                case ENDED -> "<gold>\ud83c\udfc6 " + Text.esc(this.name(e, e.champion)) + "</gold> <gray>won " + n;
            };
        } else {
            return "<gray>No tournament right now - /event";
        }
    }

    private int alive(EventManager.Event e) {
        int n = 0;

        for (UUID u : e.players) {
            if (!this.eliminated(e, u)) {
                n++;
            }
        }

        return n;
    }

    public boolean host(EventManager.Event e) {
        if (this.current() != null) {
            return false;
        } else {
            this.current = e;
            e.joinUntil = System.currentTimeMillis() + e.joinSeconds * 1000L;
            e.bar = BossBar.bossBar((Component)Component.empty(), 1.0F, Color.YELLOW, Overlay.PROGRESS);
            this.announce(
                Text.mm("<gold><b>⚔ TOURNAMENT</b> <white>" + Text.esc(e.name) + " <dark_gray>• ")
                    .append(e.kit.label())
                    .append(
                        Text.mm(
                            " <dark_gray>• <gray>"
                                + e.format.display
                                + ", first to "
                                + e.firstTo
                                + (e.prize.isBlank() ? "" : " <dark_gray>• <yellow>Prize: " + Text.esc(e.prize))
                                + " <dark_gray>• <gray>hosted by <white>"
                                + Text.esc(e.host)
                                + "  <green><b><click:run_command:'/event join'><hover:show_text:'<green>Join the tournament'>[JOIN]</hover></click>"
                        )
                    )
            );
            Snd.broadcast("event.raid.horn", 0.6F, 1.2F);
            return true;
        }
    }

    private void announce(Component c) {
        boolean everywhere = Cfg.bool(Opt.EVENT_BROADCAST_ALL);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (everywhere || this.plugin.sessions().state(p) != PlayerState.OUTSIDE) {
                p.sendMessage(c);
            }
        }
    }

    public void join(Player p) {
        EventManager.Event e = this.current();
        if (e == null) {
            p.sendMessage(Text.mm("<gray>There is no tournament right now. <white>/event</white> shows when one starts."));
        } else if (e.state != EventManager.State.JOINING) {
            p.sendMessage(Text.mm("<red>The tournament already started - <white>/event</white> to watch it."));
        } else if (e.players.contains(p.getUniqueId())) {
            p.sendMessage(Text.mm("<gray>You're already in. It starts in <white>" + this.secondsLeft(e) + "s</white>."));
        } else if (e.players.size() >= e.max) {
            p.sendMessage(Text.mm("<red>The tournament is full."));
        } else if (this.plugin.hackerPool() != null && this.plugin.hackerPool().pooled(p)) {
            p.sendMessage(Text.mm("<dark_red>Players in the hacker pool can't join tournaments."));
        } else {
            if (this.plugin.sessions().state(p) == PlayerState.OUTSIDE) {
                this.plugin.lobby().join(p, true);
            }

            PlayerState st = this.plugin.sessions().state(p);
            if (st != PlayerState.LOBBY && st != PlayerState.QUEUE) {
                p.sendMessage(Text.mm("<red>Finish what you're doing first, then join."));
            } else {
                e.players.add(p.getUniqueId());
                e.names.put(p.getUniqueId(), p.getName());
                p.showBossBar(e.bar);
                Snd.play(p, "block.note_block.pling", 0.7F, 1.4F);
                this.announce(
                    Text.mm(
                        "<gold>⚔ <white>"
                            + Text.esc(p.getName())
                            + " <gray>joined <white>"
                            + Text.esc(e.name)
                            + " <dark_gray>("
                            + e.players.size()
                            + "/"
                            + e.max
                            + ")"
                    )
                );
            }
        }
    }

    public void leave(Player p) {
        EventManager.Event e = this.current();
        if (e != null && e.players.contains(p.getUniqueId())) {
            if (e.state == EventManager.State.JOINING) {
                e.players.remove(p.getUniqueId());
                p.hideBossBar(e.bar);
                p.sendMessage(Text.mm("<gray>You left the tournament."));
            } else {
                p.sendMessage(Text.mm("<gray>You forfeit the tournament."));
                this.forfeit(e, p.getUniqueId());
            }
        } else {
            p.sendMessage(Text.mm("<gray>You're not in a tournament."));
        }
    }

    public void cancel(String why) {
        EventManager.Event e = this.current();
        if (e != null) {
            e.state = EventManager.State.ENDED;
            e.endedAt = System.currentTimeMillis();
            this.hideBar(e);
            this.announce(Text.mm("<red>⚔ The tournament <white>" + Text.esc(e.name) + "</white> was cancelled" + (why == null ? "." : ": " + Text.esc(why))));
            this.current = null;
        }
    }

    private void hideBar(EventManager.Event e) {
        for (UUID u : e.players) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.hideBossBar(e.bar);
            }
        }
    }

    private long secondsLeft(EventManager.Event e) {
        return Math.max(0L, (e.joinUntil - System.currentTimeMillis() + 999L) / 1000L);
    }

    public void tick() {
        this.autoEvent();
        EventManager.Event e = this.current();
        if (e != null) {
            switch (e.state) {
                case JOINING:
                    long left = this.secondsLeft(e);
                    e.bar
                        .name(
                            Text.mm("<gold><b>⚔ " + Text.esc(e.name.toUpperCase(Locale.ROOT)) + "</b> <dark_gray>• ")
                                .append(e.kit.label())
                                .append(Text.mm(" <dark_gray>• <white>" + e.players.size() + "/" + e.max + " <dark_gray>• <yellow>starts in " + left + "s"))
                        );
                    e.bar.progress(Math.max(0.0F, Math.min(1.0F, (float)left / Math.max(1, e.joinSeconds))));
                    if (left == 30L || left == 10L || left <= 5L && left > 0L) {
                        this.announce(
                            Text.mm(
                                "<gold>⚔ <white>"
                                    + Text.esc(e.name)
                                    + " <gray>starts in <yellow>"
                                    + left
                                    + "s <dark_gray>("
                                    + e.players.size()
                                    + " players) <green><b><click:run_command:'/event join'>[JOIN]</click>"
                            )
                        );
                    }

                    if (left <= 0L || e.players.size() >= e.max) {
                        e.players.removeIf(u -> Bukkit.getPlayer(u) == null);
                        if (e.players.size() < Math.max(2, e.min)) {
                            this.cancel("not enough players (" + e.players.size() + "/" + e.min + ")");
                            return;
                        }

                        this.begin(e);
                    }
                    break;
                case QUALIFYING:
                    this.runQualifiers(e);
                    break;
                case RUNNING:
                    this.runRound(e);
            }
        }
    }

    private void begin(EventManager.Event e) {
        e.bar.color(Color.RED);
        boolean quals = e.format == EventManager.Format.QUALIFIERS && e.players.size() > Math.max(2, e.advance);

        for (UUID u : e.players) {
            e.standings.put(u, new EventManager.Stand());
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                Fx.shine(p, e.name.toUpperCase(Locale.ROOT), Fx.GOLD, Text.mm(quals ? "<gray>Qualifiers - round 1" : "<gray>Round 1 - get ready!"), 40);
            }
        }

        if (quals) {
            e.state = EventManager.State.QUALIFYING;
            this.announce(
                Text.mm(
                    "<gold><b>⚔ "
                        + Text.esc(e.name)
                        + " STARTED</b> <gray>- "
                        + e.players.size()
                        + " players, "
                        + e.qualRounds
                        + " qualifier rounds, the best "
                        + e.advance
                        + " go to the bracket. <aqua><click:run_command:'/event results'>[RESULTS]</click>"
                )
            );
            this.nextQualifier(e);
        } else {
            List<UUID> seeds = new ArrayList<>(e.players);
            if (e.seeding == EventManager.Seeding.RANK) {
                seeds.sort((x, y) -> Integer.compare(this.points(y, e.kit), this.points(x, e.kit)));
            } else {
                Collections.shuffle(seeds, (Random)ThreadLocalRandom.current());
            }

            this.startBracket(e, seeds);
        }
    }

    private List<UUID> standingOrder(EventManager.Event e) {
        List<UUID> order = new ArrayList<>();

        for (UUID u : e.players) {
            if (!e.withdrawn.contains(u)) {
                order.add(u);
            }
        }

        Collections.shuffle(order, (Random)ThreadLocalRandom.current());
        order.sort(
            Comparator.<UUID>comparingInt(ux -> -e.standings.get(ux).wins)
                .thenComparingInt(ux -> -(e.standings.get(ux).roundsFor - e.standings.get(ux).roundsAgainst))
                .thenComparingInt(ux -> -this.points(ux, e.kit))
        );
        return order;
    }

    private static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    private void nextQualifier(EventManager.Event e) {
        List<UUID> order = this.standingOrder(e);
        List<EventManager.Pair> round = new ArrayList<>();
        if (order.size() % 2 == 1) {
            UUID bye = null;

            for (int i = order.size() - 1; i >= 0; i--) {
                if (!e.standings.get(order.get(i)).bye) {
                    bye = order.get(i);
                    break;
                }
            }

            if (bye == null) {
                bye = order.get(order.size() - 1);
            }

            order.remove(bye);
            EventManager.Stand s = e.standings.get(bye);
            s.bye = true;
            s.wins++;
            round.add(new EventManager.Pair(bye, null));
        }

        List<UUID> left = new ArrayList<>(order);

        while (left.size() >= 2) {
            UUID a = left.remove(0);
            int pick = 0;

            for (int j = 0; j < left.size(); j++) {
                if (!e.met.contains(key(a, left.get(j)))) {
                    pick = j;
                    break;
                }
            }

            UUID b = left.remove(pick);
            e.met.add(key(a, b));
            round.add(new EventManager.Pair(a, b));
        }

        e.quals.add(round);

        for (EventManager.Pair p : round) {
            if (p.b != null) {
                for (UUID u : List.of(p.a, p.b)) {
                    Player pl = Bukkit.getPlayer(u);
                    if (pl != null) {
                        pl.sendMessage(
                            Text.mm(
                                "<gold>⚔ <b>Qualifiers "
                                    + e.quals.size()
                                    + "/"
                                    + e.qualRounds
                                    + "</b> <gray>vs <white>"
                                    + Text.esc(this.name(e, u.equals(p.a) ? p.b : p.a))
                            )
                        );
                    }
                }
            } else {
                Player pl = Bukkit.getPlayer(p.a);
                if (pl != null) {
                    pl.sendMessage(Text.mm("<gray>⚔ No opponent this round - you get a free win."));
                }
            }
        }
    }

    private void runQualifiers(EventManager.Event e) {
        List<EventManager.Pair> round = e.quals.get(e.quals.size() - 1);
        e.bar
            .name(
                Text.mm(
                    "<gold><b>⚔ "
                        + Text.esc(e.name.toUpperCase(Locale.ROOT))
                        + "</b> <dark_gray>• <white>Qualifiers "
                        + e.quals.size()
                        + "/"
                        + e.qualRounds
                        + " <dark_gray>• <gray>best "
                        + e.advance
                        + " go through"
                )
            );
        e.bar.progress(Math.min(1.0F, (float)e.quals.size() / Math.max(1, e.qualRounds)));
        if (this.play(e, round, e.firstTo, "Qualifiers " + e.quals.size())) {
            if (e.quals.size() < e.qualRounds && this.standingOrder(e).size() > 1) {
                this.announce(
                    Text.mm(
                        "<gold>⚔ <b>"
                            + Text.esc(e.name)
                            + "</b> <gray>- qualifiers round "
                            + (e.quals.size() + 1)
                            + "/"
                            + e.qualRounds
                            + " <aqua><click:run_command:'/event results'>[STANDINGS]</click>"
                    )
                );
                this.nextQualifier(e);
            } else {
                List<UUID> order = this.standingOrder(e);
                List<UUID> top = new ArrayList<>(order.subList(0, Math.min(order.size(), Math.max(2, e.advance))));
                StringBuilder sb = new StringBuilder();

                for (UUID u : top) {
                    sb.append(sb.isEmpty() ? "" : ", ").append(Text.esc(this.name(e, u)));
                }

                this.announce(Text.mm("<gold>⚔ <b>Qualified for the bracket:</b> <white>" + sb));

                for (UUID u : order.subList(top.size(), order.size())) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null) {
                        p.hideBossBar(e.bar);
                        p.sendMessage(
                            Text.mm("<gray>⚔ You didn't make the top " + top.size() + " this time. GG! <aqua><click:run_command:'/event watch'>[WATCH]</click>")
                        );
                    }

                    e.withdrawn.add(u);
                }

                this.startBracket(e, top);
            }
        }
    }

    private void startBracket(EventManager.Event e, List<UUID> seeds) {
        e.state = EventManager.State.RUNNING;
        if (seeds.size() < 2) {
            if (seeds.isEmpty()) {
                this.cancel("everyone left");
            } else {
                this.finish(e, seeds.get(0), null);
            }
        } else {
            int size = 1;

            while (size < seeds.size()) {
                size *= 2;
            }

            int byes = size - seeds.size();
            List<EventManager.Pair> first = new ArrayList<>();
            int i = 0;

            for (int b = 0; b < byes; b++) {
                first.add(new EventManager.Pair(seeds.get(i++), null));
            }

            while (i + 1 < seeds.size()) {
                first.add(new EventManager.Pair(seeds.get(i), seeds.get(i + 1)));
                i += 2;
            }

            e.rounds.add(first);
            this.announce(
                Text.mm(
                    "<gold><b>⚔ "
                        + Text.esc(e.name)
                        + " - THE BRACKET</b> <gray>"
                        + seeds.size()
                        + " players, "
                        + this.roundsTotal(e)
                        + " rounds. <aqua><click:run_command:'/event results'>[BRACKET]</click>"
                )
            );
        }
    }

    private int points(UUID u, Kit kit) {
        PvPProfile prof = this.plugin.profiles().getIfLoaded(u);
        KitStats ks = prof == null ? null : prof.statsOrNull(kit.id);
        return ks == null ? 0 : ks.elo;
    }

    private int roundsTotal(EventManager.Event e) {
        int n = e.rounds.isEmpty() ? e.players.size() : e.rounds.get(0).size() * 2;

        int r;
        for (r = 0; n > 1; r++) {
            n = (n + 1) / 2;
        }

        return Math.max(1, r);
    }

    private String roundName(EventManager.Event e, int index) {
        if (index < 0) {
            return "Qualifiers";
        } else {
            int left = this.roundsTotal(e) - index;

            return switch (left) {
                case 1 -> "Final";
                case 2 -> "Semi-finals";
                case 3 -> "Quarter-finals";
                default -> "Round of " + (1 << left);
            };
        }
    }

    private boolean available(Player p) {
        PlayerState st = this.plugin.sessions().state(p);
        return st == PlayerState.LOBBY || st == PlayerState.QUEUE;
    }

    private Arena colosseum(EventManager.Event e) {
        if (!e.colosseum) {
            return null;
        } else {
            Arena a = this.plugin.arenas().get("champions");
            return a != null && a.ready() && a.freeInstances() > 0L ? a : null;
        }
    }

    public boolean colosseumBuilt() {
        Arena a = this.plugin.arenas().get("champions");
        return a != null;
    }

    private boolean play(EventManager.Event e, List<EventManager.Pair> round, int firstTo, String stage) {
        long now = System.currentTimeMillis();

        for (EventManager.Pair p : round) {
            if (p.winner == null && !p.started) {
                Player a = Bukkit.getPlayer(p.a);
                Player b = Bukkit.getPlayer(p.b);
                boolean aOk = a != null && this.available(a);
                boolean bOk = b != null && this.available(b);
                if (aOk && bOk) {
                    this.plugin.queues().leaveAll(a, true);
                    this.plugin.queues().leaveAll(b, true);
                    Match m = this.plugin.matches().startDuel(a, b, e.kit, false, this.colosseum(e), firstTo, false);
                    if (m != null) {
                        m.forcedStart = e.start;
                        m.noGates = !e.gates;
                        p.match = m;
                        p.started = true;
                        m.onFinish = (match, winner) -> Tasks.ensureSync(() -> this.finished(e, p, match, winner, stage));

                        for (Player x : List.of(a, b)) {
                            x.sendMessage(
                                Text.mm(
                                    "<gold>⚔ <b>"
                                        + Text.esc(stage)
                                        + "</b> <gray>vs <white>"
                                        + Text.esc((x == a ? b : a).getName())
                                        + " <gray>(first to "
                                        + firstTo
                                        + ")"
                                )
                            );
                        }
                    }
                } else {
                    if (p.readySince == 0L) {
                        p.readySince = now;
                    }

                    long waited = now - p.readySince;
                    long limit = a != null && b != null ? 45000L : 20000L;
                    if (waited <= limit) {
                        if (waited > 0L && waited % 15000L < 1000L) {
                            for (Player x : new Player[]{a, b}) {
                                if (x != null && !this.available(x)) {
                                    x.sendMessage(
                                        Text.mm(
                                            "<gold>⚔ Your tournament match is waiting! <gray>Finish up - you forfeit in <white>"
                                                + (limit - waited) / 1000L
                                                + "s</white>."
                                        )
                                    );
                                }
                            }
                        }
                    } else {
                        if (!aOk && (a == null || b != null)) {
                            if (!bOk && (b == null || a != null)) {
                                p.winner = ThreadLocalRandom.current().nextBoolean() ? p.a : p.b;
                            } else {
                                p.winner = p.b;
                            }
                        } else {
                            p.winner = p.a;
                        }

                        this.record(e, p, stage, true);
                        this.announce(Text.mm("<gray>⚔ " + Text.esc(this.name(e, p.winner)) + " advances (opponent didn't show up)."));
                    }
                }
            }
        }

        for (EventManager.Pair px : round) {
            if (px.winner == null) {
                return false;
            }
        }

        return true;
    }

    private void runRound(EventManager.Event e) {
        List<EventManager.Pair> round = e.rounds.get(e.rounds.size() - 1);
        boolean last = round.size() == 1;
        String stage = this.roundName(e, e.rounds.size() - 1);
        e.bar
            .name(
                Text.mm(
                    "<gold><b>⚔ "
                        + Text.esc(e.name.toUpperCase(Locale.ROOT))
                        + "</b> <dark_gray>• <white>"
                        + stage
                        + " <dark_gray>• <gray>"
                        + this.alive(e)
                        + " left"
                )
            );
        e.bar.progress(Math.min(1.0F, (float)e.rounds.size() / Math.max(1, this.roundsTotal(e))));
        if (this.play(e, round, last ? Math.max(e.firstTo, e.finalFirstTo) : e.firstTo, stage)) {
            List<UUID> winners = new ArrayList<>();

            for (EventManager.Pair p : round) {
                winners.add(p.winner);
            }

            if (winners.isEmpty()) {
                this.cancel("no players left");
            } else if (winners.size() == 1) {
                EventManager.Pair fin = round.get(0);
                this.finish(e, winners.get(0), fin.b == null ? null : (fin.winner.equals(fin.a) ? fin.b : fin.a));
            } else {
                List<EventManager.Pair> next = new ArrayList<>();

                for (int i = 0; i + 1 < winners.size(); i += 2) {
                    next.add(new EventManager.Pair(winners.get(i), winners.get(i + 1)));
                }

                if (winners.size() % 2 == 1) {
                    next.add(new EventManager.Pair(winners.get(winners.size() - 1), null));
                }

                e.rounds.add(next);
                this.announce(
                    Text.mm(
                        "<gold>⚔ <b>"
                            + Text.esc(e.name)
                            + "</b> <gray>- "
                            + this.roundName(e, e.rounds.size() - 1)
                            + ": "
                            + winners.size()
                            + " players left. <aqua><click:run_command:'/event results'>[BRACKET]</click>"
                    )
                );
            }
        }
    }

    private void finished(EventManager.Event e, EventManager.Pair p, Match match, MatchTeam winner, String stage) {
        if (e.state != EventManager.State.ENDED && p.winner == null) {
            if (winner != null && !winner.members.isEmpty()) {
                for (MatchTeam t : match.teams) {
                    if (t.members.contains(p.a)) {
                        p.scoreA = t.score;
                    } else if (p.b != null && t.members.contains(p.b)) {
                        p.scoreB = t.score;
                    }
                }

                p.winner = winner.members.get(0);
                this.record(e, p, stage, false);
                UUID loser = p.winner.equals(p.a) ? p.b : p.a;
                Player lp = loser == null ? null : Bukkit.getPlayer(loser);
                boolean knockout = e.state == EventManager.State.RUNNING;
                if (lp != null) {
                    if (knockout) {
                        lp.hideBossBar(e.bar);
                        lp.sendMessage(
                            Text.mm(
                                "<gray>⚔ You're out of <white>" + Text.esc(e.name) + "</white>. GG! <aqua><click:run_command:'/event watch'>[WATCH]</click>"
                            )
                        );
                    } else {
                        EventManager.Stand s = e.standings.get(loser);
                        lp.sendMessage(
                            Text.mm(
                                "<gray>⚔ Lost this one - you're on <white>"
                                    + (s == null ? 0 : s.wins)
                                    + "-"
                                    + (s == null ? 0 : s.losses)
                                    + "</white>. Next qualifier round soon."
                            )
                        );
                    }
                }
            } else {
                p.started = false;
                p.match = null;
                p.readySince = 0L;
            }
        }
    }

    private void record(EventManager.Event e, EventManager.Pair p, String stage, boolean forfeit) {
        if (p.b != null) {
            UUID loser = p.winner.equals(p.a) ? p.b : p.a;
            int ws = p.winner.equals(p.a) ? p.scoreA : p.scoreB;
            int ls = p.winner.equals(p.a) ? p.scoreB : p.scoreA;
            e.results.add(new EventManager.Result(stage, p.winner, loser, ws, ls, forfeit, System.currentTimeMillis()));
            if (e.state == EventManager.State.QUALIFYING) {
                EventManager.Stand w = e.standings.get(p.winner);
                EventManager.Stand l = e.standings.get(loser);
                if (w != null) {
                    w.wins++;
                    w.roundsFor += ws;
                    w.roundsAgainst += ls;
                }

                if (l != null) {
                    l.losses++;
                    l.roundsFor += ls;
                    l.roundsAgainst += ws;
                }
            }
        }
    }

    private boolean eliminated(EventManager.Event e, UUID u) {
        if (e.withdrawn.contains(u)) {
            return true;
        } else {
            for (List<EventManager.Pair> round : e.rounds) {
                for (EventManager.Pair p : round) {
                    if ((u.equals(p.a) || u.equals(p.b)) && p.winner != null && !p.winner.equals(u)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    private void forfeit(EventManager.Event e, UUID u) {
        if (e.state == EventManager.State.JOINING) {
            e.players.remove(u);
        } else {
            e.withdrawn.add(u);
            List<List<EventManager.Pair>> stage = e.state == EventManager.State.QUALIFYING ? e.quals : e.rounds;
            if (!stage.isEmpty()) {
                for (EventManager.Pair p : stage.get(stage.size() - 1)) {
                    if (p.winner == null && (u.equals(p.a) || u.equals(p.b))) {
                        if (p.match != null) {
                            Player pl = Bukkit.getPlayer(u);
                            if (pl != null) {
                                p.match.forfeit(pl, false);
                            }
                        }

                        p.winner = u.equals(p.a) ? p.b : p.a;
                        this.record(
                            e, p, e.state == EventManager.State.QUALIFYING ? "Qualifiers " + e.quals.size() : this.roundName(e, e.rounds.size() - 1), true
                        );
                    }
                }
            }
        }
    }

    private String name(EventManager.Event e, UUID u) {
        if (u == null) {
            return "-";
        } else {
            String n = e.names.get(u);
            return n != null ? n : String.valueOf(Bukkit.getOfflinePlayer(u).getName());
        }
    }

    private void finish(EventManager.Event e, UUID winner, UUID runnerUp) {
        e.state = EventManager.State.ENDED;
        e.endedAt = System.currentTimeMillis();
        e.champion = winner;
        this.hideBar(e);
        String wn = this.name(e, winner);
        this.wins.merge(winner, 1, Integer::sum);
        this.history
            .add(
                0,
                new EventManager.Past(
                    e.name, e.kit.displayName, wn, runnerUp == null ? "-" : this.name(e, runnerUp), e.players.size(), e.format.display, e.endedAt
                )
            );

        while (this.history.size() > 30) {
            this.history.remove(this.history.size() - 1);
        }

        this.save();
        this.announce(
            Text.mm("<gold><b>\ud83c\udfc6 " + Text.esc(wn) + " WON " + Text.esc(e.name.toUpperCase(Locale.ROOT)) + "!</b> <gray>(")
                .append(e.kit.label())
                .append(
                    Text.mm(
                        "<gray>)"
                            + (runnerUp == null ? "" : " <gray>beating <white>" + Text.esc(this.name(e, runnerUp)) + "</white> in the final")
                            + (e.prize.isBlank() ? "" : " <yellow>Prize: " + Text.esc(e.prize))
                    )
                )
        );
        Player wp = Bukkit.getPlayer(winner);
        if (wp != null) {
            Tasks.later(40L, () -> {
                if (wp.isOnline()) {
                    Fx.shine(wp, "CHAMPION", Fx.GOLD, Text.mm("<gray>You won <white>" + Text.esc(e.name)), 80);
                    Fx.fireworks(wp.getLocation(), 6, org.bukkit.Color.YELLOW, org.bukkit.Color.ORANGE, org.bukkit.Color.WHITE);
                    Snd.play(wp, "ui.toast.challenge_complete", 1.0F, 1.0F);
                    TotemPop.play(wp, new ItemStack(Material.TOTEM_OF_UNDYING));
                }
            });
        }

        String cmds = e.reward.isBlank() ? Cfg.s(Opt.EVENT_REWARD) : e.reward;

        for (String c : cmds.split(";")) {
            String cmd = c.trim().replace("<player>", wn);
            if (!cmd.isEmpty()) {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
                } catch (RuntimeException var13) {
                    this.plugin.getLogger().log(Level.WARNING, "Tournament reward command failed: " + cmd, (Throwable)var13);
                }
            }
        }
    }

    private void autoEvent() {
        int every = Cfg.i(Opt.EVENT_AUTO_MINUTES);
        if (every > 0 && this.current() == null && Cfg.bool(Opt.EVENTS_ENABLED)) {
            if (System.currentTimeMillis() - this.lastAuto >= every * 60000L) {
                this.lastAuto = System.currentTimeMillis();
                int lobby = this.plugin.sessions().playersIn(PlayerState.LOBBY).size() + this.plugin.sessions().playersIn(PlayerState.QUEUE).size();
                if (lobby >= Cfg.i(Opt.EVENT_MIN_PLAYERS)) {
                    List<Kit> kits = this.hostableKits();
                    if (!kits.isEmpty()) {
                        EventManager.Event e = defaults();
                        e.kit = kits.get(ThreadLocalRandom.current().nextInt(kits.size()));
                        e.name = e.kit.displayName + " Cup";
                        e.host = "Auto";
                        this.host(e);
                    }
                }
            }
        }
    }

    private List<Kit> hostableKits() {
        List<Kit> kits = new ArrayList<>();

        for (Kit k : this.plugin.kits().sorted()) {
            if (k.enabled && k.hasContents() && !this.plugin.arenas().forKit(k).isEmpty()) {
                kits.add(k);
            }
        }

        return kits;
    }

    private static EventManager.Event defaults() {
        EventManager.Event e = new EventManager.Event();
        e.firstTo = Cfg.i(Opt.EVENT_ROUNDS);
        e.finalFirstTo = e.firstTo + 1;
        e.min = Cfg.i(Opt.EVENT_MIN_PLAYERS);
        e.max = Cfg.i(Opt.EVENT_MAX_PLAYERS);
        e.joinSeconds = Cfg.i(Opt.EVENT_JOIN_SECONDS);
        return e;
    }

    public void buildColosseum(Player p) {
        if (this.colosseumBuilt()) {
            p.sendMessage(Text.mm("<gray>The Champions Colosseum is already built."));
        } else {
            p.sendMessage(Text.mm("<gold>⚔ Building the <b>Champions Colosseum</b>... <gray>(a minute or two, you can keep playing)"));
            this.plugin
                .arenas()
                .generate("champions", "Champions Colosseum", ArenaTheme.CHAMPIONS_COLOSSEUM, 224, 3)
                .whenComplete((a, err) -> Tasks.ensureSync(() -> {
                    if (err == null && a != null) {
                        a.kits.add("tournament");
                        this.plugin.arenas().save();
                        if (p.isOnline()) {
                            p.sendMessage(Text.mm("<green>The Champions Colosseum is ready. Tournaments are played there now."));
                        }
                    } else {
                        p.sendMessage(Text.mm("<red>The colosseum couldn't be built - see the console."));
                    }
                }));
        }
    }

    private LobbyDialogs ui() {
        return this.plugin.dialogs().lobby();
    }

    public void menu(Player p) {
        EventManager.Event e = this.current();
        List<Component> header = new ArrayList<>();
        List<ActionButton> b = new ArrayList<>();
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        if (e == null) {
            header.add(Text.mm("<gray>No tournament is running right now."));
            header.add(
                Text.mm("<gray>They're announced in chat - one click on <green>[JOIN]</green> and you're in. Join our Discord to know when the next one is!")
            );
        } else {
            List<Component> hover = new ArrayList<>();
            hover.add(Text.mm("<gold><b>" + Text.esc(e.name)));
            hover.add(
                Text.mm(
                    "<gray>" + e.format.display + (e.format == EventManager.Format.QUALIFIERS ? " (" + e.qualRounds + " rounds, best " + e.advance + ")" : "")
                )
            );
            hover.add(Text.mm("<gray>First to " + e.firstTo + ", final first to " + Math.max(e.firstTo, e.finalFirstTo)));
            hover.add(Text.mm("<gray>Host: <white>" + Text.esc(e.host)));
            if (!e.prize.isBlank()) {
                hover.add(Text.mm("<yellow>Prize: <white>" + Text.esc(e.prize)));
            }

            tiles.add(
                new LobbyDialogs.Tile(
                    e.kit.iconStack(),
                    Text.mm("<gold><b>" + Text.esc(e.name)),
                    e.kit.label().append(Text.mm(" <dark_gray>• <white>" + e.players.size() + "/" + e.max + " <dark_gray>• ").append(Text.mm(this.status()))),
                    hover,
                    () -> this.results(p, false)
                )
            );
            boolean in = e.players.contains(p.getUniqueId());
            if (e.state == EventManager.State.JOINING) {
                b.add(this.ui().btn(p, in ? "<red>Leave" : "<green><b>Join", null, () -> {
                    p.closeDialog();
                    if (in) {
                        this.leave(p);
                    } else {
                        this.join(p);
                    }
                }));
            }

            b.add(this.ui().btn(p, "<aqua>Results & Bracket", "<gray>Who beat who, standings, the bracket", () -> this.results(p, false)));
            if (in && e.state != EventManager.State.JOINING) {
                b.add(this.ui().btn(p, "<aqua>My Matches", null, () -> this.results(p, true)));
            }

            if (e.state == EventManager.State.RUNNING || e.state == EventManager.State.QUALIFYING) {
                b.add(this.ui().btn(p, "<white>Watch Live", null, () -> {
                    p.closeDialog();
                    this.spectate(p);
                }));
            }
        }

        if (p.hasPermission("vortexpvp.staff")) {
            if (e == null) {
                b.add(this.ui().btn(p, "<gold><b>Host a Tournament", "<gray>Any game mode, qualifiers, colosseum...", () -> this.hostKit(p)));
            } else {
                if (e.state == EventManager.State.JOINING) {
                    b.add(this.ui().btn(p, "<yellow>Start Now", null, () -> {
                        e.joinUntil = System.currentTimeMillis();
                        p.closeDialog();
                    }));
                }

                b.add(
                    this.ui()
                        .btn(
                            p,
                            "<red>Cancel Tournament",
                            null,
                            () -> this.plugin
                                .dialogs()
                                .confirm(
                                    p,
                                    Text.mm("<red><b>Cancel " + Text.esc(e.name) + "?"),
                                    List.of(Text.mm("<gray>Running matches finish, nobody wins.")),
                                    () -> this.cancel("cancelled by " + p.getName())
                                )
                        )
                );
            }

            if (p.hasPermission("vortexpvp.admin") && !this.colosseumBuilt()) {
                b.add(this.ui().btn(p, "<gold>Build Champions Colosseum", "<gray>The tournament arena (3 copies)", () -> {
                    p.closeDialog();
                    this.buildColosseum(p);
                }));
            }
        }

        b.add(this.ui().btn(p, "<gold>Past Winners", null, () -> this.past(p)));
        header.add(Text.mm("<dark_gray>Your tournament wins: <gold>" + this.winsOf(p.getUniqueId())));
        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Tournaments", e == null ? "none running" : (e.state == EventManager.State.JOINING ? "join now!" : "live")),
                tiles,
                header,
                null,
                b,
                2,
                this.ui().close(p)
            );
    }

    private void hostKit(Player p) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (Kit k : this.hostableKits()) {
            int playing = this.plugin.matches().playing(k);
            tiles.add(
                new LobbyDialogs.Tile(
                    k.iconStack(),
                    Text.mm(k.color + "<b>" + Text.esc(k.displayName)),
                    Text.mm("<gray>" + playing + " playing now <dark_gray>• <gray>" + this.plugin.arenas().forKit(k).size() + " maps"),
                    List.of(Text.mm(k.color + "<b>" + Text.esc(k.displayName)), Component.empty(), Text.mm("<green>▶ Click to set the rules")),
                    () -> this.hostForm(p, k)
                )
            );
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Host a Tournament", "pick the game mode"),
                tiles,
                List.of(Text.mm("<gray>Every game mode with maps can be played as a tournament.")),
                Text.mm("<red>No game mode has maps yet."),
                List.of(this.ui().btn(p, "<gray>« Tournaments", null, () -> this.menu(p))),
                1,
                this.ui().close(p)
            );
    }

    private void hostForm(Player p, Kit kit) {
        Dialogs d = this.plugin.dialogs();
        List<DialogInput> in = new ArrayList<>();
        in.add(DialogInput.text("name", Text.mm("<gold>Tournament name")).initial(kit.displayName + " Cup").maxLength(32).width(300).build());
        in.add(
            DialogInput.singleOption(
                    "format",
                    Text.mm("<gold>Format"),
                    List.of(
                        OptionEntry.create("KNOCKOUT", Text.mm("Knockout bracket"), true),
                        OptionEntry.create("QUALIFIERS", Text.mm("Qualifiers, then a bracket"), false)
                    )
                )
                .width(300)
                .build()
        );
        in.add(DialogInput.numberRange("quals", Text.mm("<gold>Qualifier rounds"), 1.0F, 6.0F).step(1.0F).initial(3.0F).width(300).build());
        in.add(
            DialogInput.singleOption(
                    "advance",
                    Text.mm("<gold>Best players into the bracket"),
                    List.of(
                        OptionEntry.create("4", Text.mm("Top 4"), false),
                        OptionEntry.create("8", Text.mm("Top 8"), true),
                        OptionEntry.create("16", Text.mm("Top 16"), false),
                        OptionEntry.create("32", Text.mm("Top 32"), false)
                    )
                )
                .width(300)
                .build()
        );
        in.add(DialogInput.numberRange("first", Text.mm("<gold>First to"), 1.0F, 5.0F).step(1.0F).initial((float)Cfg.i(Opt.EVENT_ROUNDS)).width(300).build());
        in.add(
            DialogInput.numberRange("final", Text.mm("<gold>Final: first to"), 1.0F, 7.0F)
                .step(1.0F)
                .initial((float)Math.min(7, Cfg.i(Opt.EVENT_ROUNDS) + 1))
                .width(300)
                .build()
        );
        in.add(
            DialogInput.numberRange("max", Text.mm("<gold>Max players"), 2.0F, 128.0F)
                .step(2.0F)
                .initial((float)Cfg.i(Opt.EVENT_MAX_PLAYERS))
                .width(300)
                .build()
        );
        in.add(
            DialogInput.numberRange("min", Text.mm("<gold>Min players"), 2.0F, 64.0F)
                .step(1.0F)
                .initial((float)Cfg.i(Opt.EVENT_MIN_PLAYERS))
                .width(300)
                .build()
        );
        in.add(
            DialogInput.numberRange("join", Text.mm("<gold>Join time (s)"), 15.0F, 600.0F)
                .step(15.0F)
                .initial((float)Cfg.i(Opt.EVENT_JOIN_SECONDS))
                .width(300)
                .build()
        );
        in.add(
            DialogInput.singleOption(
                    "seed",
                    Text.mm("<gold>Seeding"),
                    List.of(OptionEntry.create("RANDOM", Text.mm("Random"), true), OptionEntry.create("RANK", Text.mm("By rank points"), false))
                )
                .width(300)
                .build()
        );
        in.add(
            DialogInput.bool("colosseum", Text.mm("<gold>Play in the Champions Colosseum" + (this.colosseumBuilt() ? "" : " <gray>(not built yet)")))
                .initial(this.colosseumBuilt())
                .build()
        );
        String startDef = Cfg.s(Opt.TOURNAMENT_START_MODE);
        in.add(
            DialogInput.singleOption(
                    "start",
                    Text.mm("<gold>Start"),
                    List.of(
                        OptionEntry.create("ELEVATOR", Text.mm("Elevator rises you up"), "ELEVATOR".equals(startDef)),
                        OptionEntry.create("CAGE", Text.mm("Stone cage with a gate"), "CAGE".equals(startDef)),
                        OptionEntry.create("FREEZE", Text.mm("Countdown on the spot"), "FREEZE".equals(startDef) || "GATES".equals(startDef))
                    )
                )
                .width(300)
                .build()
        );
        in.add(
            DialogInput.bool("gates", Text.mm("<gold>Cages, fences & gates <gray>(off = none at all)"))
                .initial(Cfg.bool(Opt.TOURNAMENT_GATES))
                .build()
        );
        in.add(DialogInput.text("prize", Text.mm("<gold>Prize (shown)")).initial("").maxLength(64).width(300).build());
        if (p.hasPermission("vortexpvp.admin")) {
            in.add(DialogInput.text("reward", Text.mm("<gold>Reward commands (; between, <player>)")).initial("").maxLength(256).width(300).build());
        }

        ActionButton go = Dialogs.button(Text.mm("<green><b>Host"), null, 150, d.viewAction(p, view -> {
            EventManager.Event e = defaults();
            e.kit = kit;
            String n = view.getText("name");
            e.name = n != null && !n.isBlank() ? n.trim() : kit.displayName + " Cup";
            e.format = "QUALIFIERS".equals(view.getText("format")) ? EventManager.Format.QUALIFIERS : EventManager.Format.KNOCKOUT;
            e.qualRounds = Math.round(view.getFloat("quals"));

            try {
                e.advance = Integer.parseInt(String.valueOf(view.getText("advance")));
            } catch (NumberFormatException var7x) {
                e.advance = 8;
            }

            e.firstTo = Math.round(view.getFloat("first"));
            e.finalFirstTo = Math.max(e.firstTo, Math.round(view.getFloat("final")));
            e.max = Math.round(view.getFloat("max"));
            e.min = Math.min(e.max, Math.round(view.getFloat("min")));
            e.joinSeconds = Math.round(view.getFloat("join"));
            e.seeding = "RANK".equals(view.getText("seed")) ? EventManager.Seeding.RANK : EventManager.Seeding.RANDOM;
            e.colosseum = Boolean.TRUE.equals(view.getBoolean("colosseum"));
            try {
                e.start = Opt.StartMode.valueOf(String.valueOf(view.getText("start")));
            } catch (IllegalArgumentException var8x) {
                e.start = Opt.StartMode.ELEVATOR;
            }

            e.gates = Boolean.TRUE.equals(view.getBoolean("gates"));
            e.prize = String.valueOf(view.getText("prize")).trim();
            e.reward = p.hasPermission("vortexpvp.admin") && view.getText("reward") != null ? String.valueOf(view.getText("reward")).trim() : "";
            e.host = p.getName();
            p.closeDialog();
            if (!this.host(e)) {
                p.sendMessage(Text.mm("<red>A tournament is already running."));
            }
        }));
        ActionButton back = Dialogs.button(Text.mm("<gray>« Game Modes"), null, 150, d.action(p, () -> this.hostKit(p)));
        DialogBase base = DialogBase.builder(Text.mm("<gold><b>Host a Tournament</b> <dark_gray>» ").append(kit.label()))
            .canCloseWithEscape(true)
            .pause(false)
            .afterAction(DialogAfterAction.NONE)
            .inputs(in)
            .build();
        Dialogs.show(p, base, DialogType.confirmation(go, back));
    }

    private Component resultLine(EventManager.Event e, EventManager.Result r) {
        return Text.mm(
            "<green><b>"
                + Text.esc(this.name(e, r.winner()))
                + "</b></green> <gray>beat <red>"
                + Text.esc(this.name(e, r.loser()))
                + "</red> "
                + (r.forfeit() ? "<dark_gray>(no-show)" : "<white>" + r.winnerScore() + "-" + r.loserScore())
                + " <dark_gray>• "
                + Text.esc(r.stage())
        );
    }

    private Component pairLine(EventManager.Event e, EventManager.Pair pair) {
        String a = Text.esc(this.name(e, pair.a));
        if (pair.b == null) {
            return Text.mm("<white>" + a + " <dark_gray>- bye (free win)");
        } else {
            String bName = Text.esc(this.name(e, pair.b));
            if (pair.winner != null) {
                boolean aw = pair.winner.equals(pair.a);
                int ws = aw ? pair.scoreA : pair.scoreB;
                int ls = aw ? pair.scoreB : pair.scoreA;
                return Text.mm(
                    (aw ? "<green><b>" + a + "</b>" : "<dark_gray><st>" + a + "</st>")
                        + " <white>"
                        + (aw ? ws : ls)
                        + "<dark_gray>-<white>"
                        + (aw ? ls : ws)
                        + " "
                        + (!aw ? "<green><b>" + bName + "</b>" : "<dark_gray><st>" + bName + "</st>")
                );
            } else {
                String live = "";
                if (pair.started && pair.match != null) {
                    int sa = 0;
                    int sb = 0;

                    for (MatchTeam t : pair.match.teams) {
                        if (t.members.contains(pair.a)) {
                            sa = t.score;
                        } else if (t.members.contains(pair.b)) {
                            sb = t.score;
                        }
                    }

                    live = " <red>● live <white>" + sa + "-" + sb;
                } else {
                    live = " <gray>waiting";
                }

                return Text.mm("<white>" + a + " <dark_gray>vs <white>" + bName + live);
            }
        }
    }

    public void results(Player p, boolean mine) {
        EventManager.Event e = this.current != null ? this.current : null;
        if (e == null) {
            p.sendMessage(Text.mm("<gray>No tournament to show. <white>/event</white> -> Past Winners."));
        } else {
            UUID me = p.getUniqueId();
            List<Component> lines = new ArrayList<>();
            lines.add(
                Text.mm("<gold><b>" + Text.esc(e.name) + "</b> <dark_gray>• ")
                    .append(e.kit.label())
                    .append(Text.mm(" <dark_gray>• <gray>" + e.format.display + " <dark_gray>• ").append(Text.mm(this.status())))
            );
            if (e.state == EventManager.State.JOINING) {
                StringBuilder sb = new StringBuilder("<gray>Signed up (" + e.players.size() + "):<white>");

                for (UUID u : e.players) {
                    sb.append(" ").append(Text.esc(this.name(e, u)));
                }

                lines.add(Text.mm(sb.toString()));
            }

            if (!mine && !e.quals.isEmpty()) {
                lines.add(Text.mm("<gold><b>Qualifier standings</b> <gray>(best " + e.advance + " go through)"));
                List<UUID> order = new ArrayList<>(e.players);
                order.sort(
                    Comparator.<UUID>comparingInt(u -> -e.standings.getOrDefault(u, new EventManager.Stand()).wins)
                        .thenComparingInt(
                            u -> -(
                                e.standings.getOrDefault(u, new EventManager.Stand()).roundsFor
                                    - e.standings.getOrDefault(u, new EventManager.Stand()).roundsAgainst
                            )
                        )
                );
                int pos = 0;

                for (UUID u : order) {
                    EventManager.Stand s = e.standings.getOrDefault(u, new EventManager.Stand());
                    pos++;
                    boolean through = pos <= e.advance;
                    lines.add(
                        Text.mm(
                            (through ? "<green>" : "<gray>")
                                + pos
                                + ". <white>"
                                + Text.esc(this.name(e, u))
                                + " <gray>"
                                + s.wins
                                + "W "
                                + s.losses
                                + "L <dark_gray>(rounds "
                                + s.roundsFor
                                + "-"
                                + s.roundsAgainst
                                + ")"
                                + (u.equals(me) ? " <gold>◀ you" : "")
                        )
                    );
                    if (pos >= 40) {
                        break;
                    }
                }
            }

            if (!mine) {
                for (int r = 0; r < e.rounds.size(); r++) {
                    lines.add(Text.mm("<gold><b>" + this.roundName(e, r)));

                    for (EventManager.Pair pair : e.rounds.get(r)) {
                        lines.add(this.pairLine(e, pair));
                    }
                }

                if (!e.quals.isEmpty() && e.state == EventManager.State.QUALIFYING) {
                    lines.add(Text.mm("<gold><b>Qualifiers round " + e.quals.size()));

                    for (EventManager.Pair pair : e.quals.get(e.quals.size() - 1)) {
                        lines.add(this.pairLine(e, pair));
                    }
                }
            }

            List<EventManager.Result> res = new ArrayList<>(e.results);
            Collections.reverse(res);
            lines.add(Text.mm("<gold><b>" + (mine ? "Your matches" : "Who beat who") + "</b> <gray>(latest first)"));
            int shown = 0;

            for (EventManager.Result r : res) {
                if (!mine || r.winner().equals(me) || r.loser().equals(me)) {
                    lines.add(this.resultLine(e, r));
                    if (++shown >= 40) {
                        break;
                    }
                }
            }

            if (shown == 0) {
                lines.add(Text.mm("<gray>No finished matches yet."));
            }

            List<ActionButton> b = new ArrayList<>();
            b.add(this.ui().btn(p, mine ? "<aqua>Everything" : "<aqua>My Matches", null, () -> this.results(p, !mine)));
            b.add(this.ui().btn(p, "<white>Refresh", null, () -> this.results(p, mine)));
            this.ui()
                .screen(
                    p,
                    LobbyDialogs.title("Tournament", mine ? "your matches" : "results"),
                    List.of(),
                    lines,
                    null,
                    b,
                    2,
                    this.ui().btn(p, "<gray>« Tournaments", null, () -> this.menu(p))
                );
        }
    }

    private void past(Player p) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        SimpleDateFormat df = new SimpleDateFormat("d MMM HH:mm", Locale.ENGLISH);

        for (EventManager.Past h : this.history) {
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(Material.GOLDEN_HELMET),
                    Text.mm("<gold><b>\ud83c\udfc6 " + Text.esc(h.winner())),
                    Text.mm("<white>" + Text.esc(h.name()) + " <dark_gray>• <gray>" + Text.esc(h.kit()) + " <dark_gray>• <gray>" + df.format(new Date(h.at()))),
                    List.of(
                        Text.mm("<gold><b>" + Text.esc(h.name())),
                        Text.mm("<gray>Winner: <white>" + Text.esc(h.winner())),
                        Text.mm("<gray>Final against: <white>" + Text.esc(h.runnerUp())),
                        Text.mm("<gray>" + h.players() + " players, " + Text.esc(h.format()))
                    ),
                    null
                )
            );
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Tournaments", "past winners"),
                tiles,
                List.of(),
                Text.mm("<gray>No tournament has finished yet."),
                List.of(this.ui().btn(p, "<gray>« Tournaments", null, () -> this.menu(p))),
                1,
                this.ui().close(p)
            );
    }

    private void spectate(Player p) {
        EventManager.Event e = this.current();
        if (e == null) {
            p.sendMessage(Text.mm("<gray>Nothing to watch yet."));
        } else {
            List<List<EventManager.Pair>> stage = e.state == EventManager.State.QUALIFYING ? e.quals : e.rounds;
            if (!stage.isEmpty()) {
                for (EventManager.Pair pair : stage.get(stage.size() - 1)) {
                    if (pair.started && pair.winner == null && pair.match != null) {
                        Player a = Bukkit.getPlayer(pair.a);
                        if (a != null) {
                            p.performCommand("spectate " + a.getName());
                            return;
                        }
                    }
                }
            }

            p.sendMessage(Text.mm("<gray>No tournament match is live right now."));
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        EventManager.Event e = this.current == null
                || this.current.state == EventManager.State.ENDED && System.currentTimeMillis() - this.current.endedAt >= 600000L
            ? null
            : this.current;
        if (e != null) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("name", e.name);
            c.put("kit", e.kit.displayName);
            c.put("kitId", e.kit.id);
            c.put("format", e.format.display);
            c.put("state", e.state.name());
            c.put("status", Text.plain(Text.mm(this.status())));
            c.put("players", e.players.size());
            c.put("max", e.max);
            c.put("firstTo", e.firstTo);
            c.put("prize", e.prize);
            c.put("host", e.host);
            c.put("startsIn", e.state == EventManager.State.JOINING ? this.secondsLeft(e) : 0L);
            c.put("champion", e.champion == null ? null : this.name(e, e.champion));
            List<Map<String, Object>> rounds = new ArrayList<>();

            for (int r = 0; r < e.rounds.size(); r++) {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("name", this.roundName(e, r));
                List<Map<String, Object>> pairs = new ArrayList<>();

                for (EventManager.Pair pair : e.rounds.get(r)) {
                    Map<String, Object> pm = new LinkedHashMap<>();
                    pm.put("a", this.name(e, pair.a));
                    pm.put("b", pair.b == null ? null : this.name(e, pair.b));
                    pm.put("winner", pair.winner == null ? null : this.name(e, pair.winner));
                    pm.put("scoreA", pair.scoreA);
                    pm.put("scoreB", pair.scoreB);
                    pm.put("live", pair.started && pair.winner == null);
                    pairs.add(pm);
                }

                rm.put("pairs", pairs);
                rounds.add(rm);
            }

            c.put("rounds", rounds);
            List<Map<String, Object>> stand = new ArrayList<>();
            if (!e.quals.isEmpty()) {
                List<UUID> order = new ArrayList<>(e.players);
                order.sort(Comparator.comparingInt(u -> -e.standings.getOrDefault(u, new EventManager.Stand()).wins));

                for (UUID u : order) {
                    EventManager.Stand s = e.standings.getOrDefault(u, new EventManager.Stand());
                    stand.add(Map.of("name", this.name(e, u), "wins", s.wins, "losses", s.losses, "diff", s.roundsFor - s.roundsAgainst));
                }
            }

            c.put("standings", stand);
            c.put("advance", e.advance);
            List<Map<String, Object>> res = new ArrayList<>();

            for (int i = e.results.size() - 1; i >= 0 && res.size() < 30; i--) {
                EventManager.Result r = e.results.get(i);
                res.add(
                    Map.of(
                        "winner",
                        this.name(e, r.winner()),
                        "loser",
                        this.name(e, r.loser()),
                        "score",
                        r.forfeit() ? "no-show" : r.winnerScore() + "-" + r.loserScore(),
                        "stage",
                        r.stage()
                    )
                );
            }

            c.put("results", res);
            out.put("current", c);
        }

        List<Map<String, Object>> past = new ArrayList<>();

        for (EventManager.Past h : this.history) {
            past.add(
                Map.of(
                    "name",
                    h.name(),
                    "kit",
                    h.kit(),
                    "winner",
                    h.winner(),
                    "runnerUp",
                    h.runnerUp(),
                    "players",
                    h.players(),
                    "format",
                    h.format(),
                    "at",
                    h.at()
                )
            );
        }

        out.put("past", past);
        return out;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            if (!Cfg.bool(Opt.EVENTS_ENABLED) && !p.hasPermission("vortexpvp.admin")) {
                p.sendMessage(Text.mm("<red>Tournaments are turned off."));
                return true;
            } else {
                String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "join":
                        this.join(p);
                        break;
                    case "leave":
                        this.leave(p);
                        break;
                    case "bracket":
                    case "results":
                    case "standings":
                        this.results(p, false);
                        break;
                    case "mine":
                    case "me":
                        this.results(p, true);
                        break;
                    case "spectate":
                    case "watch":
                        this.spectate(p);
                        break;
                    case "history":
                    case "winners":
                        this.past(p);
                        break;
                    case "colosseum":
                        if (p.hasPermission("vortexpvp.admin")) {
                            this.buildColosseum(p);
                        }
                        break;
                    case "host":
                        if (!p.hasPermission("vortexpvp.staff")) {
                            p.sendMessage(Text.mm("<red>Only staff can host tournaments."));
                        } else if (args.length >= 2) {
                            Kit k = this.plugin.kits().get(args[1]);
                            if (k == null) {
                                p.sendMessage(Text.mm("<red>Unknown kit."));
                                return true;
                            }

                            this.hostForm(p, k);
                        } else {
                            this.hostKit(p);
                        }
                        break;
                    case "start":
                        EventManager.Event e = this.current();
                        if (p.hasPermission("vortexpvp.staff") && e != null && e.state == EventManager.State.JOINING) {
                            e.joinUntil = System.currentTimeMillis();
                        }
                        break;
                    case "cancel":
                        if (p.hasPermission("vortexpvp.staff")) {
                            this.cancel("cancelled by " + p.getName());
                        }
                        break;
                    default:
                        this.menu(p);
                }

                return true;
            }
        } else {
            if (args.length > 0 && args[0].equalsIgnoreCase("cancel")) {
                this.cancel("cancelled by console");
            }

            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("join", "leave", "results", "mine", "watch", "history"));
            if (sender.hasPermission("vortexpvp.staff")) {
                out.addAll(List.of("host", "start", "cancel"));
            }

            if (sender.hasPermission("vortexpvp.admin")) {
                out.add("colosseum");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("host")) {
            out.addAll(this.plugin.kits().ids());
        }

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    public static final class Event {
        public String name = "Tournament";
        public Kit kit;
        public EventManager.Format format = EventManager.Format.KNOCKOUT;
        public int firstTo = 1;
        public int finalFirstTo = 2;
        public int qualRounds = 3;
        public int advance = 8;
        public int min = 4;
        public int max = 64;
        public int joinSeconds = 60;
        public EventManager.Seeding seeding = EventManager.Seeding.RANDOM;
        public boolean colosseum = true;
        /** How tournament duels start (Opt.TOURNAMENT_START_MODE by default). */
        public Opt.StartMode start = Cfg.e(Opt.TOURNAMENT_START_MODE, Opt.StartMode.class);
        /** False = no cages, fences or gates at all. */
        public boolean gates = Cfg.bool(Opt.TOURNAMENT_GATES);
        public String prize = "";
        public String reward = "";
        public String host = "Server";
        EventManager.State state = EventManager.State.JOINING;
        long joinUntil;
        long endedAt;
        UUID champion;
        final LinkedHashSet<UUID> players = new LinkedHashSet<>();
        final Map<UUID, String> names = new HashMap<>();
        final List<List<EventManager.Pair>> quals = new ArrayList<>();
        final Map<UUID, EventManager.Stand> standings = new LinkedHashMap<>();
        final Set<String> met = new HashSet<>();
        final Set<UUID> withdrawn = new HashSet<>();
        final List<List<EventManager.Pair>> rounds = new ArrayList<>();
        final List<EventManager.Result> results = new ArrayList<>();
        BossBar bar;
    }

    public static enum Format {
        KNOCKOUT("Knockout"),
        QUALIFIERS("Qualifiers + Knockout");

        public final String display;

        private Format(String display) {
            this.display = display;
        }
    }

    public static final class Pair {
        final UUID a;
        final UUID b;
        UUID winner;
        Match match;
        long readySince;
        boolean started;
        int scoreA;
        int scoreB;

        Pair(UUID a, UUID b) {
            this.a = a;
            this.b = b;
            if (b == null) {
                this.winner = a;
            }
        }
    }

    public record Past(String name, String kit, String winner, String runnerUp, int players, String format, long at) {
    }

    public record Result(String stage, UUID winner, UUID loser, int winnerScore, int loserScore, boolean forfeit, long at) {
    }

    public static enum Seeding {
        RANDOM,
        RANK;
    }

    static final class Stand {
        int wins;
        int losses;
        int roundsFor;
        int roundsAgainst;
        boolean bye;
    }

    public static enum State {
        JOINING,
        QUALIFYING,
        RUNNING,
        ENDED;
    }
}
