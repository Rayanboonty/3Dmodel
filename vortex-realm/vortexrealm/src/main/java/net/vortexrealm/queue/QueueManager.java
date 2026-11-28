package net.vortexrealm.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.vortexrealm.match.Match;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Msg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.data.KitStats;
import net.vortexrealm.data.PvPProfile;
import net.vortexrealm.data.Setting;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.rank.Tier;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Session;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class QueueManager {
    private final VortexRealm plugin;
    private final Map<QueueManager.Key, LinkedHashMap<UUID, QueueManager.Entry>> queues = new HashMap<>();
    private final Map<UUID, List<QueueManager.Entry>> byPlayer = new HashMap<>();
    private int tickCount;

    public QueueManager(VortexRealm plugin) {
        this.plugin = plugin;
    }

    public boolean inQueue(Player p) {
        List<QueueManager.Entry> l = this.byPlayer.get(p.getUniqueId());
        return l != null && !l.isEmpty();
    }

    public boolean inQueue(Player p, Kit kit, boolean ranked) {
        Map<UUID, QueueManager.Entry> q = this.queues.get(new QueueManager.Key(kit.id, ranked));
        return q != null && q.containsKey(p.getUniqueId());
    }

    public int size(Kit kit, boolean ranked) {
        Map<UUID, QueueManager.Entry> q = this.queues.get(new QueueManager.Key(kit.id, ranked));
        if (q == null) {
            return 0;
        } else {
            int n = 0;

            for (UUID u : q.keySet()) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && this.plugin.sessions().state(p) == PlayerState.QUEUE) {
                    n++;
                }
            }

            return n;
        }
    }

    public int size(Kit kit) {
        return this.size(kit, true) + this.size(kit, false);
    }

    public int total() {
        return this.byPlayer.size();
    }

    public List<QueueManager.Entry> entries(Player p) {
        List<QueueManager.Entry> l = this.byPlayer.get(p.getUniqueId());
        return l == null ? List.of() : List.copyOf(l);
    }

    public long waitedSeconds(Player p) {
        long oldest = Long.MAX_VALUE;

        for (QueueManager.Entry e : this.entries(p)) {
            oldest = Math.min(oldest, e.joinedAt());
        }

        return oldest == Long.MAX_VALUE ? 0L : (System.currentTimeMillis() - oldest) / 1000L;
    }

    public boolean toggle(Player p, Kit kit, boolean ranked) {
        if (this.inQueue(p, kit, ranked)) {
            this.leave(p, kit, ranked);
            return false;
        } else {
            return this.join(p, kit, ranked, false);
        }
    }

    private static boolean isDefense(Matchmaker.Candidate c) {
        return c.promo() && c.promoTarget() >= 0 && c.promoTarget() < Math.max(0, c.tier());
    }

    public boolean join(Player p, Kit kit, boolean ranked, boolean quiet) {
        Session s = this.plugin.sessions().get(p);
        if (s.state != PlayerState.LOBBY && s.state != PlayerState.QUEUE) {
            if (!quiet) {
                Msg.error(p, "queue.not-in-lobby");
            }

            return false;
        } else if (Cfg.bool(Opt.MAINTENANCE) && !p.hasPermission("vortex.admin")) {
            if (!quiet) {
                Msg.error(p, "general.maintenance");
            }

            return false;
        } else if (kit.enabled && (!ranked || kit.ranked) && (ranked || kit.unranked)) {
            if ((!ranked || Cfg.bool(Opt.RANKED_ENABLED)) && (ranked || Cfg.bool(Opt.UNRANKED_ENABLED))) {
                if (ranked && this.plugin.penalties() != null && !this.plugin.penalties().checkRanked(p)) {
                    return false;
                } else {
                    PvPProfile prof = this.plugin.profiles().get(p);
                    int need = Cfg.i(Opt.RANKED_MIN_WINS);
                    if (ranked && prof.totalUnrankedWins() < need) {
                        if (!quiet) {
                            Msg.error(p, "queue.ranked-locked", "need", need, "have", prof.totalUnrankedWins());
                        }

                        return false;
                    } else if (this.plugin.parties().of(p) != null) {
                        if (!quiet) {
                            Msg.error(p, "queue.in-party");
                        }

                        return false;
                    } else if (this.plugin.arenas().forKit(kit).isEmpty()) {
                        if (!quiet) {
                            Msg.error(p, "queue.no-arena", "kit", kit.label());
                        }

                        return false;
                    } else if (this.inQueue(p, kit, ranked)) {
                        return true;
                    } else {
                        if (this.inQueue(p)) {
                            this.leaveAll(p, false);
                        }

                        QueueManager.Entry e = new QueueManager.Entry(p.getUniqueId(), kit.id, ranked, System.currentTimeMillis());
                        this.queues.computeIfAbsent(new QueueManager.Key(kit.id, ranked), k -> new LinkedHashMap<>()).put(p.getUniqueId(), e);
                        this.byPlayer.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(e);
                        s.lastQueueKit = kit.id;
                        s.lastQueueRanked = ranked;
                        if (s.state == PlayerState.LOBBY) {
                            s.state = PlayerState.QUEUE;
                            this.plugin.lobby().giveQueueItems(p);
                        }

                        if (!quiet) {
                            Msg.send(p, "queue.joined", "kit", kit.label(), "mode", ranked ? Msg.get("summary.ranked") : Msg.get("summary.unranked"));
                        }

                        if (prof.on(Setting.QUEUE_SOUNDS)) {
                            Snd.play(p, "block.note_block.pling", 0.7F, 1.6F);
                        }

                        return true;
                    }
                }
            } else {
                if (!quiet) {
                    Msg.error(p, "queue.type-disabled");
                }

                return false;
            }
        } else {
            if (!quiet) {
                Msg.error(p, "queue.kit-disabled", "kit", kit.label());
            }

            return false;
        }
    }

    public void leave(Player p, Kit kit, boolean ranked) {
        QueueManager.Key key = new QueueManager.Key(kit.id, ranked);
        Map<UUID, QueueManager.Entry> q = this.queues.get(key);
        if (q != null) {
            q.remove(p.getUniqueId());
        }

        List<QueueManager.Entry> l = this.byPlayer.get(p.getUniqueId());
        if (l != null) {
            l.removeIf(e -> e.kit().equals(kit.id) && e.ranked() == ranked);
            if (l.isEmpty()) {
                this.byPlayer.remove(p.getUniqueId());
            }
        }

        Msg.send(p, "queue.left", "kit", kit.label());
        if (!this.inQueue(p)) {
            this.backToLobbyState(p);
        }
    }

    public void leaveAll(Player p, boolean restoreLobby) {
        List<QueueManager.Entry> l = this.byPlayer.remove(p.getUniqueId());
        if (l != null) {
            for (QueueManager.Entry e : l) {
                Map<UUID, QueueManager.Entry> q = this.queues.get(new QueueManager.Key(e.kit(), e.ranked()));
                if (q != null) {
                    q.remove(p.getUniqueId());
                }
            }
        }

        if (restoreLobby) {
            this.backToLobbyState(p);
        }
    }

    private void backToLobbyState(Player p) {
        Session s = this.plugin.sessions().get(p);
        if (s.state == PlayerState.QUEUE) {
            s.state = PlayerState.LOBBY;
            this.plugin.lobby().giveLobbyItems(p);
        }
    }

    public void purgeKit(String kitId) {
        Iterator<Map.Entry<QueueManager.Key, LinkedHashMap<UUID, QueueManager.Entry>>> it = this.queues.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<QueueManager.Key, LinkedHashMap<UUID, QueueManager.Entry>> e = it.next();
            if (e.getKey().kit().equals(kitId)) {
                for (UUID u : new ArrayList<>(e.getValue().keySet())) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null) {
                        this.leaveAll(p, true);
                    }
                }

                it.remove();
            }
        }
    }

    public void tick() {
        this.tickCount++;
        long now = System.currentTimeMillis();
        Set<UUID> taken = new HashSet<>();

        for (Map.Entry<QueueManager.Key, LinkedHashMap<UUID, QueueManager.Entry>> qe : new ArrayList<>(this.queues.entrySet())) {
            Map<UUID, QueueManager.Entry> q = qe.getValue();
            Kit kit = this.plugin.kits().get(qe.getKey().kit());
            if (kit == null) {
                for (UUID u : new ArrayList<>(q.keySet())) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null) {
                        this.leaveAll(p, true);
                    }
                }
            } else if (q.size() >= 2) {
                int capacity = this.plugin.arenas().freeCount(kit) - this.plugin.matches().pending(kit);
                if (capacity > 0) {
                    boolean ranked = qe.getKey().ranked();
                    List<Matchmaker.Candidate> cands = new ArrayList<>();

                    for (QueueManager.Entry e : q.values()) {
                        Player p = Bukkit.getPlayer(e.uuid());
                        if (p != null && this.plugin.sessions().state(p) == PlayerState.QUEUE) {
                            PvPProfile prof = this.plugin.profiles().get(p);
                            KitStats ks = prof.statsOrNull(kit.id);
                            Session s = this.plugin.sessions().get(p);
                            int rating = ranked || ks != null && ks.tier >= 0 ? (ks == null ? 0 : ks.elo) : this.plugin.ranks().skillRating(prof, kit);
                            cands.add(
                                new Matchmaker.Candidate(
                                    p.getUniqueId(),
                                    rating,
                                    p.getPing(),
                                    e.joinedAt(),
                                    pingLimit(prof),
                                    eloMultiplier(prof),
                                    s.lastOpponent,
                                    s.lastOpponentTime + Cfg.i(Opt.AVOID_REMATCH) * 1000L,
                                    ks == null ? -1 : ks.tier,
                                    qe.getKey().ranked() && ks != null && (ks.promoState == 2 || this.plugin.ranks().defenseActive(prof, kit)),
                                    ks == null ? -1 : ks.promoTarget
                                )
                            );
                        }
                    }

                    cands.sort(Comparator.comparingLong(Matchmaker.Candidate::joinedAt));
                    List<Matchmaker.Candidate> pooled = new ArrayList<>();
                    if (this.plugin.hackerPool() != null) {
                        cands.removeIf(candx -> {
                            if (this.plugin.hackerPool().pooled(candx.id())) {
                                pooled.add(candx);
                                return true;
                            } else {
                                return false;
                            }
                        });
                    }

                    List<Matchmaker.Pair> pairs = new ArrayList<>(Matchmaker.pair(cands, now, params(ranked, cands.size()), taken));
                    pairs.addAll(Matchmaker.pair(pooled, now, params(ranked, pooled.size()), taken));

                    for (Matchmaker.Pair pair : pairs) {
                        if (capacity-- <= 0) {
                            break;
                        }

                        Player a = Bukkit.getPlayer(pair.a().id());
                        Player b = Bukkit.getPlayer(pair.b().id());
                        if (a != null && b != null) {
                            taken.add(a.getUniqueId());
                            taken.add(b.getUniqueId());
                            boolean challenge = ranked && (pair.a().promo() || pair.b().promo());
                            boolean defense = challenge && (isDefense(pair.a()) || isDefense(pair.b()));
                            int rounds = defense ? Cfg.i(Opt.TIER_DEFENSE_FIRST_TO) : (challenge ? Cfg.i(Opt.TIER_TEST_FIRST_TO) : 0);
                            Match started = this.plugin.matches().startDuel(a, b, kit, ranked, null, rounds, true);
                            if (challenge && started != null) {
                                this.plugin.matches().announceTierMatch(started, a, b, defense ? "Tier Defense" : "Tier Test");
                            }

                            if (challenge) {
                                for (Matchmaker.Candidate cand : List.of(pair.a(), pair.b())) {
                                    Player cp = Bukkit.getPlayer(cand.id());
                                    if (cp != null && cand.promo()) {
                                        if (isDefense(cand)) {
                                            Tier lower = Tier.byIndex(cand.promoTarget());
                                            cp.sendMessage(
                                                Text.mm(
                                                    "<gold>⚠ <b>Tier Defense Challenge found!</b></gold> <gray>First to "
                                                        + rounds
                                                        + " - win to defend your tier"
                                                        + (lower == null ? "." : ", lose and you rank down to " + lower.mm() + "<gray>.")
                                                )
                                            );
                                        } else {
                                            cp.sendMessage(
                                                Text.mm("<gold>⚠ <b>Tier Test Challenge found!</b></gold> <gray>First to " + rounds + " - win to rank up.")
                                            );
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (this.tickCount % 5 == 0) {
            this.actionBars();
        }
    }

    private static Matchmaker.Params params(boolean ranked, int waiting) {
        return new Matchmaker.Params(
            Cfg.i(Opt.ELO_RANGE_START),
            Cfg.i(Opt.ELO_RANGE_EXPAND),
            Cfg.i(Opt.ELO_RANGE_MAX),
            Cfg.i(Opt.PING_RANGE_EXPAND),
            ranked,
            Cfg.i(Opt.MAX_TIER_GAP),
            Cfg.bool(Opt.TIER_TEST_ABOVE),
            Cfg.i(Opt.TIER_TEST_FALLBACK),
            Cfg.i(Opt.SAME_TIER_SECONDS),
            Matchmaker.speedFor(waiting)
        );
    }

    private static int pingLimit(PvPProfile prof) {
        String v = prof.get(Setting.PING_RANGE);
        if ("any".equals(v)) {
            return Integer.MAX_VALUE;
        } else {
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException var3) {
                return Integer.MAX_VALUE;
            }
        }
    }

    private static double eloMultiplier(PvPProfile prof) {
        String var1 = prof.get(Setting.MATCHMAKING);

        return switch (var1) {
            case "fast" -> 2.0;
            case "strict" -> 0.5;
            default -> 1.0;
        };
    }

    private void actionBars() {
        Iterator var1 = this.byPlayer.entrySet().iterator();

        while (true) {
            Player p;
            Component bar;
            while (true) {
                if (!var1.hasNext()) {
                    return;
                }

                Map.Entry<UUID, List<QueueManager.Entry>> e = (Map.Entry<UUID, List<QueueManager.Entry>>)var1.next();
                p = Bukkit.getPlayer(e.getKey());
                if (p != null && !e.getValue().isEmpty()) {
                    List<QueueManager.Entry> list = e.getValue();
                    long secs = this.waitedSeconds(p);
                    if (list.size() == 1) {
                        QueueManager.Entry only = list.get(0);
                        Kit k = this.plugin.kits().get(only.kit());
                        if (k == null) {
                            continue;
                        }

                        boolean waitingArena = this.size(k, only.ranked()) >= 2 && !this.plugin.arenas().hasFree(k);
                        KitStats ks = only.ranked() ? this.plugin.profiles().get(p).statsOrNull(k.id) : null;
                        if (ks != null && this.plugin.ranks().defenseActive(this.plugin.profiles().get(p), k)) {
                            bar = Text.mm("<gold>⚠ Waiting on a Tier Defense... <#5B7CFF>" + secs + "s <dark_gray>• ").append(k.label());
                            break;
                        }

                        if (ks != null && ks.promoState == 2) {
                            Tier want = Tier.byIndex(ks.promoTarget);
                            bar = Text.mm("<gold>Waiting on a Tier Calibration... <#5B7CFF>" + secs + "s <dark_gray>• ")
                                .append(k.label())
                                .append(Text.mm(want == null ? "" : " <gray>vs " + want.mm() + " <gray>player"));
                            break;
                        }

                        bar = Msg.get(
                            waitingArena ? "queue.actionbar-arena" : "queue.actionbar", "mode", Component.empty(), "kit", k.label(), "time", secs + "s"
                        );
                        break;
                    }

                    Component icons = Component.empty();

                    for (QueueManager.Entry en : list) {
                        Kit kx = this.plugin.kits().get(en.kit());
                        if (kx != null) {
                            icons = icons.append(kx.iconComponent());
                        }
                    }

                    bar = Msg.get("queue.actionbar-multi", "count", list.size(), "icons", icons, "time", secs + "s");
                    break;
                }
            }

            p.sendActionBar(bar);
        }
    }

    public Component describe(Player p) {
        List<QueueManager.Entry> l = this.entries(p);
        if (l.isEmpty()) {
            return Text.mm("<gray>-");
        } else {
            Kit k = this.plugin.kits().get(l.get(0).kit());
            Component c = k == null ? Text.mm("<gray>?") : k.label();
            if (l.size() > 1) {
                c = c.append(Text.mm(" <gray>+" + (l.size() - 1)));
            }

            return c;
        }
    }

    public record Entry(UUID uuid, String kit, boolean ranked, long joinedAt) {
    }

    public record Key(String kit, boolean ranked) {
    }
}
