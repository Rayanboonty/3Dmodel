package net.practicepvp.duel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.practicepvp.PracticePlugin;
import net.practicepvp.arena.Arena;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Msg;
import net.practicepvp.config.Opt;
import net.practicepvp.data.KitStats;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.data.Setting;
import net.practicepvp.kit.Kit;
import net.practicepvp.match.Match;
import net.practicepvp.session.PlayerState;
import net.practicepvp.social.SocialManager;
import net.practicepvp.util.ClientVersion;
import net.practicepvp.util.Fmt;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class DuelManager {
    public static final long EXPIRE_MS = 30000L;
    private final PracticePlugin plugin;
    private final Map<UUID, Map<UUID, DuelManager.Request>> incoming = new HashMap<>();
    /** Last Tier Challenge between two players ("a|b" sorted) - for the cooldown. */
    private final Map<String, Long> lastChallenge = new ConcurrentHashMap<>();

    public DuelManager(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean available(Player p) {
        PlayerState st = this.plugin.sessions().state(p);
        return st == PlayerState.LOBBY || st == PlayerState.QUEUE;
    }

    public void send(Player from, Player to, Kit kit, int rounds, Arena arena) {
        this.send(from, to, kit, rounds, arena, DuelManager.Options.DEFAULT);
    }

    public void send(Player from, Player to, Kit kit, int rounds, Arena arena, DuelManager.Options opts) {
        if (opts.pingEq() && (this.plugin.pingEqualizer() == null || !this.plugin.pingEqualizer().enabled())) {
            opts = new DuelManager.Options(false, opts.spectators(), opts.challenge());
        }

        SocialManager social = this.plugin.social();
        if (Cfg.bool(Opt.MAINTENANCE) && !from.hasPermission("practice.admin")) {
            Msg.error(from, "general.maintenance");
        } else if (from.equals(to)) {
            Msg.error(from, "duel.self");
        } else if (social != null && !social.appearsOnline(from, to)) {
            Msg.error(from, "general.player-offline", "player", to.getName());
        } else if (!this.available(from)) {
            Msg.error(from, "duel.you-busy");
        } else if (this.plugin.sessions().state(to) == PlayerState.OUTSIDE) {
            Msg.error(from, "general.player-not-in-pvp", "player", to.getName());
        } else if (!this.available(to)) {
            Msg.error(from, "duel.target-busy", "player", to.getName());
        } else if (this.plugin.parties().of(from) != null || this.plugin.parties().of(to) != null) {
            Msg.error(from, "duel.party");
        } else if (this.plugin.hackerPool() != null && !this.plugin.hackerPool().samePool(from, to)) {
            from.sendMessage(
                Text.mm(
                    this.plugin.hackerPool().pooled(from)
                        ? "<dark_red>You're in the hacker pool:</dark_red> <gray>you can only duel other pooled players."
                        : "<red>You can't duel that player right now."
                )
            );
        } else {
            PvPProfile tp = this.plugin.profiles().get(to);
            if ("nobody".equals(tp.get(Setting.DUEL_REQUESTS))) {
                Msg.error(from, "duel.requests-off", "player", to.getName());
            } else if (social != null && !social.check(from, to, SocialManager.Kind.DUEL)) {
                return;
            } else if (!kit.enabled) {
                Msg.error(from, "queue.kit-disabled", "kit", kit.label());
            } else if (this.plugin.arenas().forKit(kit).isEmpty()) {
                Msg.error(from, "queue.no-arena", "kit", kit.label());
            } else if (opts.challenge() && !this.challengeAllowed(from, to, kit)) {
                return;
            } else {
                Map<UUID, DuelManager.Request> map = this.incoming.computeIfAbsent(to.getUniqueId(), k -> new HashMap<>());
                DuelManager.Request old = map.get(from.getUniqueId());
                if (old != null && !old.expired() && System.currentTimeMillis() - old.created() < 5000L) {
                    Msg.error(from, "duel.slow-down");
                } else {
                    for (Map<UUID, DuelManager.Request> other : this.incoming.values()) {
                        other.remove(from.getUniqueId());
                    }

                    this.incoming.values().removeIf(Map::isEmpty);
                    map = this.incoming.computeIfAbsent(to.getUniqueId(), k -> new HashMap<>());
                    int r = opts.challenge() ? Cfg.i(Opt.TIER_CHALLENGE_FIRST_TO) : rounds;
                    DuelManager.Request req = new DuelManager.Request(
                        from.getUniqueId(), to.getUniqueId(), kit.id, r, arena == null ? null : arena.id, System.currentTimeMillis(), opts
                    );
                    map.put(from.getUniqueId(), req);
                    if (opts.challenge()) {
                        from.sendMessage(
                            Text.mm("<gold>⚠ Tier Challenge sent to <white>" + Text.esc(to.getName()) + "</white> <dark_gray>•</dark_gray> ").append(kit.label())
                        );
                    } else {
                        Msg.send(from, "duel.sent", "player", to.getName(), "kit", kit.label());
                    }

                    this.notifyTarget(to, from, kit, r, arena, opts);
                }
            }
        }
    }

    private static String pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    /** Both players must hold a tier in the kit, and the pair must be off cooldown. */
    public boolean challengeAllowed(Player from, Player to, Kit kit) {
        if (!Cfg.bool(Opt.TIER_CHALLENGE) || !Cfg.bool(Opt.RANKED_ENABLED)) {
            from.sendMessage(Text.mm("<red>Tier Challenges are disabled right now."));
            return false;
        } else if (!kit.ranked) {
            from.sendMessage(Text.mm("<red>").append(kit.label()).append(Text.mm(" <red>is not a ranked kit.")));
            return false;
        } else {
            KitStats a = this.plugin.profiles().get(from).statsOrNull(kit.id);
            KitStats b = this.plugin.profiles().get(to).statsOrNull(kit.id);
            if (a == null || !a.ranked()) {
                from.sendMessage(Text.mm("<red>You need a tier in ").append(kit.label()).append(Text.mm(" <red>to send a Tier Challenge.")));
                return false;
            } else if (b == null || !b.ranked()) {
                from.sendMessage(Text.mm("<red>" + Text.esc(to.getName()) + " has no tier in ").append(kit.label()).append(Text.mm("<red> yet.")));
                return false;
            } else if (a.promoState == 2 || a.promoState == 4 || b.promoState == 2 || b.promoState == 4) {
                from.sendMessage(Text.mm("<red>A Tier Test or Tier Defense in ").append(kit.label()).append(Text.mm("<red> is in progress - finish it first.")));
                return false;
            } else if (this.plugin.penalties() != null && !this.plugin.penalties().checkRanked(from)) {
                return false;
            } else {
                Long last = this.lastChallenge.get(pairKey(from.getUniqueId(), to.getUniqueId()));
                long cd = Cfg.i(Opt.TIER_CHALLENGE_COOLDOWN) * 60000L;
                if (last != null && System.currentTimeMillis() - last < cd) {
                    from.sendMessage(
                        Text.mm(
                            "<red>You already played a Tier Challenge against "
                                + Text.esc(to.getName())
                                + " recently. <gray>("
                                + Fmt.duration((cd - (System.currentTimeMillis() - last)) / 1000L)
                                + " left)"
                        )
                    );
                    return false;
                } else {
                    return true;
                }
            }
        }
    }

    private void notifyTarget(Player to, Player from, Kit kit, int rounds, Arena arena, DuelManager.Options opts) {
        PvPProfile tp = this.plugin.profiles().get(to);
        PvPProfile fp = this.plugin.profiles().get(from);
        KitStats ks = fp.statsOrNull(kit.id);
        String tier = ks != null && ks.tier() != null ? ks.tier().mm() : "<gray>Unranked";
        int r = rounds > 0 ? rounds : kit.rounds();
        String map = arena == null ? "Random" : arena.displayName;
        to.sendMessage(Msg.get("duel.request-line"));
        if (opts.challenge()) {
            to.sendMessage(
                Text.mm("<gold><b>⚠ TIER CHALLENGE</b>\n<white>" + Text.esc(from.getName()) + " <dark_gray>(</dark_gray>" + tier + "<dark_gray>, </dark_gray><gray>" + from.getPing() + "ms</gray><dark_gray>)</dark_gray>\n<gray>Kit: ")
                    .append(kit.label())
                    .append(Text.mm(" <dark_gray>•</dark_gray> <gray>First to <gold>" + r + "</gold> <dark_gray>•</dark_gray> <yellow>Large Tier Score impact"))
            );
        } else {
            to.sendMessage(
                Msg.get("duel.request", "player", from.getName(), "tier", Text.mm(tier), "ping", from.getPing(), "kit", kit.label(), "rounds", r, "map", map)
            );
        }

        if (opts.pingEq() || !opts.spectators()) {
            to.sendMessage(
                Text.mm(
                    (opts.pingEq() ? "<aqua>⚖ Ping Equalizer on" : "")
                        + (opts.pingEq() && !opts.spectators() ? " <dark_gray>• " : "")
                        + (opts.spectators() ? "" : "<red>Spectators blocked")
                )
            );
        }

        to.sendMessage(Msg.get("duel.request-buttons", "player", from.getName()));
        to.sendMessage(Msg.get("duel.request-line"));
        if (tp.on(Setting.CHAT_SOUNDS)) {
            Snd.play(to, "block.note_block.pling", 1.0F, 1.4F);
        }

        if (this.plugin.sessions().state(to) == PlayerState.LOBBY) {
            if (opts.challenge() && this.plugin.duelDialogs() != null && ClientVersion.supportsDialogs(to)) {
                this.plugin.duelDialogs().challengePopup(to, from, kit, r, opts.pingEq());
            } else if ("popup".equals(tp.get(Setting.DUEL_POPUP))) {
                this.plugin.dialogs().duelPopup(to, from, kit, r, map, Text.mm(tier));
            }
        }
    }

    public void accept(Player target, String fromName) {
        Player from = fromName == null ? null : Bukkit.getPlayerExact(fromName);
        DuelManager.Request r = from == null ? null : this.get(target.getUniqueId(), from.getUniqueId());
        if (r != null && !r.expired()) {
            this.remove(target.getUniqueId(), from.getUniqueId());
            if (!this.available(target)) {
                Msg.error(target, "duel.you-busy");
            } else if (!this.available(from)) {
                Msg.error(target, "duel.target-busy", "player", from.getName());
            } else if (this.plugin.parties().of(from) == null && this.plugin.parties().of(target) == null) {
                Kit kit = this.plugin.kits().get(r.kit());
                if (kit != null && !this.plugin.arenas().forKit(kit).isEmpty()) {
                    if (r.options().challenge() && !this.challengeAllowed(from, target, kit)) {
                        target.sendMessage(Text.mm("<red>That Tier Challenge can't be played right now."));
                        return;
                    }

                    Arena arena = r.arena() == null ? null : this.plugin.arenas().get(r.arena());
                    Msg.send(from, "duel.accepted", "player", target.getName());
                    boolean ranked = r.options().challenge();
                    Match m = this.plugin.matches().startDuel(from, target, kit, ranked, arena, r.rounds(), false);
                    if (m != null) {
                        m.pingEqualizer = r.options().pingEq();
                        m.spectatorsBlocked = !r.options().spectators();
                        m.tierChallenge = ranked;
                        if (ranked) {
                            this.plugin.matches().announceTierMatch(m, from, target, "Tier Challenge");
                            String key = pairKey(from.getUniqueId(), target.getUniqueId());
                            this.lastChallenge.put(key, System.currentTimeMillis());
                            m.onFinish = (match, winner) -> {
                                if (winner == null) {
                                    this.lastChallenge.remove(key);
                                }
                            };
                        }
                    }
                } else {
                    Msg.error(target, "duel.kit-gone");
                }
            } else {
                Msg.error(target, "duel.party");
            }
        } else {
            Msg.error(target, "duel.no-request", "player", fromName == null ? "?" : fromName);
        }
    }

    public void deny(Player target, String fromName) {
        Player from = fromName == null ? null : Bukkit.getPlayerExact(fromName);
        if (from != null && this.get(target.getUniqueId(), from.getUniqueId()) != null) {
            this.remove(target.getUniqueId(), from.getUniqueId());
            Msg.send(target, "duel.denied-you", "player", from.getName());
            Msg.send(from, "duel.denied", "player", target.getName());
        } else {
            Msg.error(target, "duel.no-request", "player", fromName == null ? "?" : fromName);
        }
    }

    private DuelManager.Request get(UUID target, UUID from) {
        Map<UUID, DuelManager.Request> map = this.incoming.get(target);
        return map == null ? null : map.get(from);
    }

    private void remove(UUID target, UUID from) {
        Map<UUID, DuelManager.Request> map = this.incoming.get(target);
        if (map != null) {
            map.remove(from);
            if (map.isEmpty()) {
                this.incoming.remove(target);
            }
        }
    }

    public void cleanup() {
        Iterator<Entry<UUID, Map<UUID, DuelManager.Request>>> it = this.incoming.entrySet().iterator();

        while (it.hasNext()) {
            Map<UUID, DuelManager.Request> map = it.next().getValue();
            map.values().removeIf(DuelManager.Request::expired);
            if (map.isEmpty()) {
                it.remove();
            }
        }

        long cd = Cfg.i(Opt.TIER_CHALLENGE_COOLDOWN) * 60000L;
        long now = System.currentTimeMillis();
        this.lastChallenge.values().removeIf(t -> now - t > cd);
    }

    public void handleQuit(Player p) {
        this.incoming.remove(p.getUniqueId());

        for (Map<UUID, DuelManager.Request> map : this.incoming.values()) {
            map.remove(p.getUniqueId());
        }
    }

    /** Extra duel choices from the MCPVP duel screen. */
    public record Options(boolean pingEq, boolean spectators, boolean challenge) {
        public static final DuelManager.Options DEFAULT = new DuelManager.Options(false, true, false);
    }

    public record Request(UUID from, UUID to, String kit, int rounds, String arena, long created, DuelManager.Options options) {
        public boolean expired() {
            return System.currentTimeMillis() - this.created > 30000L;
        }
    }
}
