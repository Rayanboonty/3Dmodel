package net.vortexrealm.social;

import io.papermc.paper.registry.data.dialog.ActionButton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Msg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.data.KitStats;
import net.vortexrealm.data.PvPProfile;
import net.vortexrealm.data.Setting;
import net.vortexrealm.dialog.LobbyDialogs;
import net.vortexrealm.match.Match;
import net.vortexrealm.rank.Tier;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Session;
import net.vortexrealm.util.Fmt;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The MCPVP social layer: friends, follows, presence (Online / Invisible / Do Not Disturb)
 * and per-status request privacy.
 * <p>
 * Friend and follow edges live in {@code vr_friends} / {@code vr_follows} and are cached in memory.
 * Status and privacy choices are plain {@link Setting} values on the profile, so they are saved with it.
 */
public final class SocialManager implements TabExecutor, Listener {
    public static final long REQUEST_MS = 300000L;
    private final VortexRealm plugin;
    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> following = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> followers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Long>> friendRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> overallRank = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSeen = new ConcurrentHashMap<>();
    private volatile boolean ready;

    public SocialManager(VortexRealm plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ loading

    public void load() {
        this.plugin.db().run(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS vr_friends (a VARCHAR(36) NOT NULL, b VARCHAR(36) NOT NULL, since BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (a, b))"
                );
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS vr_follows (follower VARCHAR(36) NOT NULL, target VARCHAR(36) NOT NULL, since BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (follower, target))"
                );
            }

            Map<UUID, Set<UUID>> fr = new HashMap<>();
            Map<UUID, Set<UUID>> fg = new HashMap<>();
            Map<UUID, Set<UUID>> fl = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT a, b FROM vr_friends"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID a = parse(rs.getString(1));
                    UUID b = parse(rs.getString(2));
                    if (a != null && b != null) {
                        fr.computeIfAbsent(a, k -> new HashSet<>()).add(b);
                        fr.computeIfAbsent(b, k -> new HashSet<>()).add(a);
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement("SELECT follower, target FROM vr_follows"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID a = parse(rs.getString(1));
                    UUID b = parse(rs.getString(2));
                    if (a != null && b != null) {
                        fg.computeIfAbsent(a, k -> new HashSet<>()).add(b);
                        fl.computeIfAbsent(b, k -> new HashSet<>()).add(a);
                    }
                }
            }

            Tasks.ensureSync(() -> {
                fr.forEach((k, v) -> this.friends.computeIfAbsent(k, x -> ConcurrentHashMap.newKeySet()).addAll(v));
                fg.forEach((k, v) -> this.following.computeIfAbsent(k, x -> ConcurrentHashMap.newKeySet()).addAll(v));
                fl.forEach((k, v) -> this.followers.computeIfAbsent(k, x -> ConcurrentHashMap.newKeySet()).addAll(v));
                this.ready = true;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    this.refreshRank(p);
                }
            });
        }).exceptionally(err -> {
            this.plugin.getLogger().log(Level.SEVERE, "Friends / follows could not be loaded", err);
            return null;
        });
    }

    /** Name -> player without a blocking Mojang lookup (online player, else the server's name cache). */
    private static OfflinePlayer lookup(String name) {
        Player o = Bukkit.getPlayerExact(name);
        return o != null ? o : Bukkit.getOfflinePlayerIfCached(name);
    }

    /** The id in {@code ids} whose last known name is {@code name}. */
    private static UUID byName(java.util.Collection<UUID> ids, String name) {
        for (UUID u : ids) {
            String n = Bukkit.getOfflinePlayer(u).getName();
            if (n != null && n.equalsIgnoreCase(name)) {
                return u;
            }
        }

        return null;
    }

    private static UUID parse(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException var2) {
            return null;
        }
    }

    public boolean ready() {
        return this.ready;
    }

    public boolean enabled() {
        return Cfg.bool(Opt.SOCIAL_ENABLED);
    }

    // ------------------------------------------------------------------ graph queries

    public boolean areFriends(UUID a, UUID b) {
        Set<UUID> s = this.friends.get(a);
        return s != null && s.contains(b);
    }

    /** a follows b */
    public boolean follows(UUID a, UUID b) {
        Set<UUID> s = this.following.get(a);
        return s != null && s.contains(b);
    }

    public Set<UUID> friendsOf(UUID u) {
        Set<UUID> s = this.friends.get(u);
        return s == null ? Set.of() : Collections.unmodifiableSet(s);
    }

    public Set<UUID> followingOf(UUID u) {
        Set<UUID> s = this.following.get(u);
        return s == null ? Set.of() : Collections.unmodifiableSet(s);
    }

    public Set<UUID> followersOf(UUID u) {
        Set<UUID> s = this.followers.get(u);
        return s == null ? Set.of() : Collections.unmodifiableSet(s);
    }

    public List<Player> onlineFriends(Player viewer) {
        List<Player> out = new ArrayList<>();
        for (UUID u : this.friendsOf(viewer.getUniqueId())) {
            Player f = Bukkit.getPlayer(u);
            if (f != null && this.appearsOnline(viewer, f)) {
                out.add(f);
            }
        }

        out.sort((x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        return out;
    }

    // ------------------------------------------------------------------ presence

    public SocialManager.Status status(Player p) {
        PvPProfile prof = this.plugin.profiles().getIfLoaded(p.getUniqueId());
        return prof == null ? SocialManager.Status.ONLINE : SocialManager.Status.of(prof.get(Setting.STATUS));
    }

    public SocialManager.Status status(UUID u) {
        PvPProfile prof = this.plugin.profiles().getIfLoaded(u);
        return prof == null ? SocialManager.Status.ONLINE : SocialManager.Status.of(prof.get(Setting.STATUS));
    }

    public void setStatus(Player p, SocialManager.Status s) {
        PvPProfile prof = this.plugin.profiles().get(p);
        SocialManager.Status old = SocialManager.Status.of(prof.get(Setting.STATUS));
        if (old != s) {
            if (s == SocialManager.Status.INVISIBLE) {
                this.announcePresence(p, false);
                this.lastSeen.put(p.getUniqueId(), System.currentTimeMillis());
            }

            prof.set(Setting.STATUS, s.id);
            p.sendMessage(Text.mm("<gray>Your status is now " + s.color + "● <b>" + s.label + "</b>"));
            if (s == SocialManager.Status.INVISIBLE) {
                p.sendMessage(Text.mm("<dark_gray>While you're appearing offline you can't be sent duel requests, and friends see you as offline."));
            } else if (s == SocialManager.Status.DND) {
                p.sendMessage(Text.mm("<dark_gray>Only the groups you allowed in <white>/settings → Privacy → Requests</white> can reach you."));
            }

            Snd.click(p);
            if (old == SocialManager.Status.INVISIBLE) {
                this.announcePresence(p, true);
            }

            if (this.plugin.tab() != null) {
                this.plugin.tab().refreshSoon();
            }
        }
    }

    /** Whether {@code viewer} should see {@code target} as online (status + visibility privacy). */
    public boolean appearsOnline(Player viewer, Player target) {
        if (viewer == null || viewer.equals(target)) {
            return true;
        } else if (viewer.hasPermission("vortex.staff")) {
            return true;
        } else if (this.status(target) == SocialManager.Status.INVISIBLE) {
            return false;
        } else {
            PvPProfile prof = this.plugin.profiles().getIfLoaded(target.getUniqueId());
            String vis = prof == null ? "everyone" : prof.get(Setting.VISIBILITY);
            return switch (vis) {
                case "nobody" -> false;
                case "friends" -> this.areFriends(target.getUniqueId(), viewer.getUniqueId());
                default -> true;
            };
        }
    }

    private void announcePresence(Player p, boolean online) {
        if (this.enabled()) {
            for (UUID u : this.friendsOf(p.getUniqueId())) {
                Player f = Bukkit.getPlayer(u);
                if (f != null && !f.equals(p)) {
                    PvPProfile fp = this.plugin.profiles().getIfLoaded(u);
                    if (fp != null && fp.on(Setting.FRIEND_ALERTS) && this.appearsOnline(f, p)) {
                        f.sendMessage(
                            Text.mm(
                                "<#FFD84A>☺ Friend <dark_gray>»</dark_gray> <white>"
                                    + Text.esc(p.getName())
                                    + (online ? " <green>is now online." : " <gray>went offline.")
                            )
                        );
                        if (online) {
                            f.sendActionBar(Text.mm("<#FFD84A>☺ <white>" + Text.esc(p.getName()) + " <green>joined"));
                            if (fp.on(Setting.CHAT_SOUNDS)) {
                                Snd.play(f, "block.note_block.chime", 0.6F, 1.6F);
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Tasks.later(20L, () -> {
            if (p.isOnline()) {
                if (this.status(p) != SocialManager.Status.INVISIBLE) {
                    this.announcePresence(p, true);
                }

                this.refreshRank(p);
                Map<UUID, Long> pending = this.friendRequests.get(p.getUniqueId());
                if (pending != null && !pending.isEmpty()) {
                    p.sendMessage(
                        Text.mm(
                            "<#FFD84A>☺ <white>You have <gold>"
                                + pending.size()
                                + "</gold> friend request"
                                + (pending.size() == 1 ? "" : "s")
                                + ". <click:run_command:'/friend requests'><green><b>[VIEW]</b></green></click>"
                        )
                    );
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        this.lastSeen.put(p.getUniqueId(), System.currentTimeMillis());
        if (this.status(p) != SocialManager.Status.INVISIBLE) {
            this.announcePresence(p, false);
        }
    }

    // ------------------------------------------------------------------ ranks used by filters & nametags

    public int overallRank(UUID u) {
        return this.overallRank.getOrDefault(u, 0);
    }

    public static int overallPoints(PvPProfile prof) {
        int s = 0;
        for (KitStats k : prof.kits.values()) {
            if (k.ranked()) {
                s += k.elo;
            }
        }

        return s;
    }

    public static Tier bestTier(PvPProfile prof) {
        Tier best = null;
        if (prof != null) {
            for (KitStats k : prof.kits.values()) {
                Tier t = k.tier();
                if (t != null && (best == null || t.ordinal() > best.ordinal())) {
                    best = t;
                }
            }
        }

        return best;
    }

    public void refreshRank(Player p) {
        PvPProfile prof = this.plugin.profiles().getIfLoaded(p.getUniqueId());
        if (prof != null) {
            int points = overallPoints(prof);
            UUID u = p.getUniqueId();
            if (points <= 0) {
                if (this.overallRank.remove(u) != null) {
                    this.plugin.lobby().applyRankLevel(p);
                    this.plugin.boards().refreshNametag(p);
                }
            } else {
                this.plugin.db().supply(c -> this.plugin.db().rankOverall(c, points)).whenComplete((r, err) -> {
                    if (err == null && r != null) {
                        Integer old = this.overallRank.put(u, r[0]);
                        if (old == null || old != r[0]) {
                            Tasks.ensureSync(() -> {
                                Player online = Bukkit.getPlayer(u);
                                if (online != null) {
                                    this.plugin.lobby().applyRankLevel(online);
                                    this.plugin.boards().refreshNametag(online);
                                }
                            });
                        }
                    }
                });
            }
        }
    }

    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            this.refreshRank(p);
        }

        long now = System.currentTimeMillis();
        for (Map<UUID, Long> m : this.friendRequests.values()) {
            m.values().removeIf(t -> now - t > REQUEST_MS);
        }

        this.friendRequests.values().removeIf(Map::isEmpty);
    }

    // ------------------------------------------------------------------ privacy

    public SocialManager.Filter filter(PvPProfile prof, SocialManager.Status s) {
        Setting key = s == SocialManager.Status.ONLINE ? Setting.REQ_ONLINE : Setting.REQ_DND;
        return SocialManager.Filter.parse(prof == null ? key.defaultValue() : prof.get(key));
    }

    public void setFilter(Player p, SocialManager.Status s, SocialManager.Filter f) {
        this.plugin.profiles().get(p).set(s == SocialManager.Status.ONLINE ? Setting.REQ_ONLINE : Setting.REQ_DND, f.serialize());
    }

    public boolean famous(UUID u) {
        Player online = Bukkit.getPlayer(u);
        if (online != null && online.hasPermission("vortex.famous")) {
            return true;
        } else if (this.plugin.serverRanks() == null) {
            return false;
        } else {
            String rank = this.plugin.serverRanks().of(u).id.toLowerCase(Locale.ROOT);
            for (String r : Cfg.s(Opt.SOCIAL_FAMOUS_RANKS).split(",")) {
                if (r.trim().equalsIgnoreCase(rank)) {
                    return true;
                }
            }

            return false;
        }
    }

    /** The groups {@code from} belongs to, seen from {@code to}'s side. */
    public EnumSet<SocialManager.Group> groupsOf(UUID from, UUID to) {
        EnumSet<SocialManager.Group> g = EnumSet.noneOf(SocialManager.Group.class);
        if (this.areFriends(to, from)) {
            g.add(SocialManager.Group.FRIENDS);
        }

        if (this.follows(to, from)) {
            g.add(SocialManager.Group.FOLLOWING);
        }

        if (this.follows(from, to)) {
            g.add(SocialManager.Group.FOLLOWERS);
        }

        if (this.famous(from)) {
            g.add(SocialManager.Group.FAMOUS);
        }

        return g;
    }

    /**
     * Can {@code from} reach {@code to} with a request of this kind? Returns null when allowed,
     * otherwise the message to show the sender.
     */
    public Component denial(Player from, Player to, SocialManager.Kind kind) {
        if (!this.enabled() || from == null || from.equals(to) || from.hasPermission("vortex.staff")) {
            return null;
        } else {
            PvPProfile tp = this.plugin.profiles().getIfLoaded(to.getUniqueId());
            if (tp == null) {
                return null;
            } else {
                SocialManager.Status st = SocialManager.Status.of(tp.get(Setting.STATUS));
                String name = Text.esc(to.getName());
                if (st == SocialManager.Status.INVISIBLE && kind == SocialManager.Kind.DUEL) {
                    return Msg.get("general.player-offline", "player", to.getName());
                } else {
                    UUID f = from.getUniqueId();
                    UUID t = to.getUniqueId();
                    if (kind == SocialManager.Kind.TELEPORT) {
                        String who = tp.get(Setting.TELEPORTS);
                        if ("nobody".equals(who) || "friends".equals(who) && !this.areFriends(t, f)) {
                            return st == SocialManager.Status.INVISIBLE
                                ? Msg.get("general.player-offline", "player", to.getName())
                                : Text.mm("<red>" + name + " doesn't accept teleport requests from you.");
                        }
                    }

                    if (kind == SocialManager.Kind.MESSAGE) {
                        String who = tp.get(Setting.MSG_FROM);
                        if ("nobody".equals(who) || "friends".equals(who) && !this.areFriends(t, f)) {
                            return st == SocialManager.Status.INVISIBLE
                                ? Msg.get("general.player-offline", "player", to.getName())
                                : Text.mm("<red>" + name + " doesn't accept private messages from you.");
                        }
                    }

                    SocialManager.Filter filter = this.filter(tp, st == SocialManager.Status.ONLINE ? SocialManager.Status.ONLINE : SocialManager.Status.DND);
                    if (this.passes(filter, f, t)) {
                        return null;
                    } else if (st == SocialManager.Status.ONLINE) {
                        return Text.mm("<red>" + name + " doesn't accept " + kind.label + " from you.");
                    } else if (st == SocialManager.Status.DND) {
                        return Text.mm("<red>" + name + " is in <b>Do Not Disturb</b> and isn't accepting " + kind.label + " from you.");
                    } else {
                        return Msg.get("general.player-offline", "player", to.getName());
                    }
                }
            }
        }
    }

    public boolean allows(Player from, Player to, SocialManager.Kind kind) {
        return this.denial(from, to, kind) == null;
    }

    /** Sends the denial to {@code from} and returns false when blocked. */
    public boolean check(Player from, Player to, SocialManager.Kind kind) {
        Component d = this.denial(from, to, kind);
        if (d == null) {
            return true;
        } else {
            from.sendMessage(d);
            Snd.error(from);
            return false;
        }
    }

    public boolean passes(SocialManager.Filter filter, UUID from, UUID to) {
        EnumSet<SocialManager.Group> g = this.groupsOf(from, to);
        for (SocialManager.Group x : g) {
            if (filter.groups().contains(x)) {
                return true;
            }
        }

        if (!filter.groups().contains(SocialManager.Group.NOOBS)) {
            return false;
        } else {
            if (filter.minTier() >= 0) {
                Tier best = bestTier(this.plugin.profiles().getIfLoaded(from));
                if (best == null || best.ordinal() < filter.minTier()) {
                    return false;
                }
            }

            if (filter.topRank() > 0) {
                int r = this.overallRank(from);
                return r > 0 && r <= filter.topRank();
            } else {
                return true;
            }
        }
    }

    // ------------------------------------------------------------------ friend & follow edits

    public void requestFriend(Player from, OfflinePlayer target) {
        if (!this.enabled()) {
            from.sendMessage(Text.mm("<red>Friends are disabled on this server."));
        } else if (target == null || target.getName() == null) {
            from.sendMessage(Text.mm("<red>No player with that name has played here."));
        } else {
            UUID f = from.getUniqueId();
            UUID t = target.getUniqueId();
            String name = Text.esc(target.getName());
            if (f.equals(t)) {
                from.sendMessage(Text.mm("<red>You can't add yourself."));
            } else if (this.areFriends(f, t)) {
                from.sendMessage(Text.mm("<gray>You're already friends with <white>" + name + "<gray>."));
            } else if (this.friendsOf(f).size() >= Cfg.i(Opt.SOCIAL_MAX_FRIENDS) && !from.hasPermission("vortex.friends.unlimited")) {
                from.sendMessage(Text.mm("<red>You reached the friend limit (" + Cfg.i(Opt.SOCIAL_MAX_FRIENDS) + ")."));
            } else {
                Map<UUID, Long> mine = this.friendRequests.get(f);
                Long pending = mine == null ? null : mine.get(t);
                if (pending != null && System.currentTimeMillis() - pending <= REQUEST_MS) {
                    this.acceptFriend(from, t, target.getName());
                } else {
                    Player online = target.getPlayer();
                    if (online == null || !this.appearsOnline(from, online)) {
                        from.sendMessage(Text.mm("<red>" + name + " is not online. Friend requests need the player to be online."));
                    } else if (this.plugin.chat() != null && this.plugin.chat().ignores(online, from)) {
                        from.sendMessage(Text.mm("<red>" + name + " isn't accepting friend requests from you."));
                    } else if (this.check(from, online, SocialManager.Kind.FRIEND)) {
                        Map<UUID, Long> theirs = this.friendRequests.computeIfAbsent(t, k -> new ConcurrentHashMap<>());
                        Long old = theirs.get(f);
                        if (old != null && System.currentTimeMillis() - old < REQUEST_MS) {
                            from.sendMessage(Text.mm("<gray>You already sent <white>" + name + "</white> a friend request."));
                        } else {
                            theirs.put(f, System.currentTimeMillis());
                            from.sendMessage(Text.mm("<#FFD84A>☺ <green>Friend request sent to <white>" + name + "<green>."));
                            String fn = from.getName();
                            online.sendMessage(Text.mm("<dark_gray><st>                                                  </st>"));
                            online.sendMessage(
                                Text.mm(
                                    "<#FFD84A>☺ <white><b>"
                                        + Text.esc(fn)
                                        + "</b> <gray>wants to be your friend!  <click:run_command:'/friend accept "
                                        + fn
                                        + "'><hover:show_text:'<green>Accept'><green><b>[ACCEPT]</b></green></hover></click> <click:run_command:'/friend deny "
                                        + fn
                                        + "'><hover:show_text:'<red>Deny'><red><b>[DENY]</b></red></hover></click>"
                                )
                            );
                            online.sendMessage(Text.mm("<dark_gray><st>                                                  </st>"));
                            PvPProfile op = this.plugin.profiles().getIfLoaded(t);
                            if (op == null || op.on(Setting.CHAT_SOUNDS)) {
                                Snd.play(online, "block.note_block.pling", 0.8F, 1.6F);
                            }
                        }
                    }
                }
            }
        }
    }

    public void acceptFriend(Player p, UUID from, String fromName) {
        Map<UUID, Long> mine = this.friendRequests.get(p.getUniqueId());
        Long at = mine == null ? null : mine.remove(from);
        if (at == null || System.currentTimeMillis() - at > REQUEST_MS) {
            p.sendMessage(Text.mm("<red>You have no friend request from <white>" + Text.esc(fromName) + "<red>."));
        } else if (this.friendsOf(p.getUniqueId()).size() >= Cfg.i(Opt.SOCIAL_MAX_FRIENDS) && !p.hasPermission("vortex.friends.unlimited")) {
            p.sendMessage(Text.mm("<red>You reached the friend limit (" + Cfg.i(Opt.SOCIAL_MAX_FRIENDS) + ")."));
        } else {
            this.addFriend(p.getUniqueId(), from);
            p.sendMessage(Text.mm("<#FFD84A>☺ <green>You're now friends with <white>" + Text.esc(fromName) + "<green>!"));
            Snd.play(p, "entity.player.levelup", 0.6F, 1.6F);
            Player other = Bukkit.getPlayer(from);
            if (other != null) {
                other.sendMessage(Text.mm("<#FFD84A>☺ <white>" + Text.esc(p.getName()) + " <green>accepted your friend request!"));
                Snd.play(other, "entity.player.levelup", 0.6F, 1.6F);
            }

            if (this.plugin.tab() != null) {
                this.plugin.tab().refreshSoon();
            }
        }
    }

    public void denyFriend(Player p, UUID from, String fromName) {
        Map<UUID, Long> mine = this.friendRequests.get(p.getUniqueId());
        if (mine != null && mine.remove(from) != null) {
            p.sendMessage(Text.mm("<gray>Denied the friend request from <white>" + Text.esc(fromName) + "<gray>."));
        } else {
            p.sendMessage(Text.mm("<red>You have no friend request from <white>" + Text.esc(fromName) + "<red>."));
        }
    }

    private void addFriend(UUID a, UUID b) {
        this.friends.computeIfAbsent(a, k -> ConcurrentHashMap.newKeySet()).add(b);
        this.friends.computeIfAbsent(b, k -> ConcurrentHashMap.newKeySet()).add(a);
        long now = System.currentTimeMillis();
        this.plugin.db().run(c -> {
            this.insertIgnore(c, "vr_friends", "a, b, since", a.toString(), b.toString(), now);
            this.insertIgnore(c, "vr_friends", "a, b, since", b.toString(), a.toString(), now);
        });
    }

    public boolean removeFriend(UUID a, UUID b) {
        Set<UUID> sa = this.friends.get(a);
        boolean had = sa != null && sa.remove(b);
        Set<UUID> sb = this.friends.get(b);
        if (sb != null) {
            sb.remove(a);
        }

        if (had) {
            this.plugin.db().run(c -> {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM vr_friends WHERE (a=? AND b=?) OR (a=? AND b=?)")) {
                    ps.setString(1, a.toString());
                    ps.setString(2, b.toString());
                    ps.setString(3, b.toString());
                    ps.setString(4, a.toString());
                    ps.executeUpdate();
                }
            });
        }

        return had;
    }

    public boolean follow(UUID follower, UUID target) {
        if (follower.equals(target) || this.follows(follower, target)) {
            return false;
        } else {
            this.following.computeIfAbsent(follower, k -> ConcurrentHashMap.newKeySet()).add(target);
            this.followers.computeIfAbsent(target, k -> ConcurrentHashMap.newKeySet()).add(follower);
            long now = System.currentTimeMillis();
            this.plugin.db().run(c -> this.insertIgnore(c, "vr_follows", "follower, target, since", follower.toString(), target.toString(), now));
            return true;
        }
    }

    public boolean unfollow(UUID follower, UUID target) {
        Set<UUID> s = this.following.get(follower);
        boolean had = s != null && s.remove(target);
        Set<UUID> t = this.followers.get(target);
        if (t != null) {
            t.remove(follower);
        }

        if (had) {
            this.plugin.db().run(c -> {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM vr_follows WHERE follower=? AND target=?")) {
                    ps.setString(1, follower.toString());
                    ps.setString(2, target.toString());
                    ps.executeUpdate();
                }
            });
        }

        return had;
    }

    private void insertIgnore(Connection c, String table, String cols, String a, String b, long since) throws SQLException {
        String verb = this.plugin.db().mysql() ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ";
        try (PreparedStatement ps = c.prepareStatement(verb + table + " (" + cols + ") VALUES (?,?,?)")) {
            ps.setString(1, a);
            ps.setString(2, b);
            ps.setLong(3, since);
            ps.executeUpdate();
        }
    }

    public Map<UUID, Long> requestsFor(UUID u) {
        Map<UUID, Long> m = this.friendRequests.get(u);
        return m == null ? Map.of() : Collections.unmodifiableMap(m);
    }

    // ------------------------------------------------------------------ describing a player for lists

    /** One short line about where a visible player is: Lobby, Queueing, In a match, Spectating... */
    public String whereabouts(Player p) {
        Session s = this.plugin.sessions().get(p);
        return switch (s.state) {
            case LOBBY -> "<green>In the lobby";
            case QUEUE -> "<yellow>Queueing";
            case MATCH -> {
                Match m = s.match;
                yield m == null ? "<gold>In a match" : "<gold>Playing " + Text.plain(m.kit.label());
            }
            case FFA -> "<gold>In FFA";
            case SPECTATING -> "<aqua>Spectating";
            case EDITING -> "<gray>Editing a kit";
            case PREMATCH -> "<gold>Starting a match";
            default -> "<gray>Online";
        };
    }

    private static String ago(long ms) {
        return ms <= 0L ? "a while ago" : Fmt.duration(Math.max(1L, (System.currentTimeMillis() - ms) / 1000L)) + " ago";
    }

    // ------------------------------------------------------------------ dialogs

    public void openFriends(Player p) {
        if (!this.plugin.dialogs().useDialog(p)) {
            this.chatList(p);
        } else {
            LobbyDialogs ld = this.plugin.dialogs().lobby();
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();
            List<UUID> ids = new ArrayList<>(this.friendsOf(p.getUniqueId()));
            Map<UUID, String> names = new LinkedHashMap<>();
            for (UUID u : ids) {
                String n = Bukkit.getOfflinePlayer(u).getName();
                names.put(u, n == null ? u.toString().substring(0, 8) : n);
            }

            ids.sort((x, y) -> {
                Player px = Bukkit.getPlayer(x);
                Player py = Bukkit.getPlayer(y);
                boolean ox = px != null && this.appearsOnline(p, px);
                boolean oy = py != null && this.appearsOnline(p, py);
                return ox != oy ? (ox ? -1 : 1) : names.get(x).compareToIgnoreCase(names.get(y));
            });
            for (UUID u : ids) {
                Player f = Bukkit.getPlayer(u);
                boolean on = f != null && this.appearsOnline(p, f);
                String name = names.get(u);
                SocialManager.Status st = on ? this.status(f) : null;
                String dot = on ? st.color + "● " : "<dark_gray>● ";
                Component line = Text.mm(on ? this.whereabouts(f) : "<dark_gray>Offline <gray>· seen " + ago(this.seen(u)));
                List<Component> hover = new ArrayList<>();
                hover.add(Text.mm("<white><b>" + Text.esc(name)));
                hover.add(Text.mm(on ? st.color + "● " + st.label : "<dark_gray>● Offline"));
                hover.add(Component.empty());
                hover.add(Text.mm("<green>▶ Click for options"));
                tiles.add(
                    new LobbyDialogs.Tile(
                        f != null && this.appearsOnline(p, f) ? Items.of(Material.PLAYER_HEAD).skull(f).build() : Items.of(Material.SKELETON_SKULL).build(),
                        Text.mm(dot + (on ? "<white>" : "<gray>") + "<b>" + Text.esc(name)),
                        line,
                        hover,
                        () -> this.friendOptions(p, u, name)
                    )
                );
            }

            int requests = this.requestsFor(p.getUniqueId()).size();
            List<ActionButton> buttons = new ArrayList<>();
            buttons.add(ld.btn(p, "<green>+ Add Friend", "<gray>Send a friend request by name", () -> this.plugin
                .dialogs()
                .text(p, Text.mm("<#FFD84A><b>Add Friend"), "Player name", "", 16, name -> {
                    if (!name.isBlank()) {
                        this.requestFriend(p, lookup(name));
                    }
                })));
            buttons.add(
                ld.btn(p, (requests > 0 ? "<gold>" : "<gray>") + "Requests (" + requests + ")", "<gray>Friend requests waiting for you", () -> this.openRequests(p))
            );
            buttons.add(ld.btn(p, "<aqua>Following (" + this.followingOf(p.getUniqueId()).size() + ")", "<gray>Players you follow", () -> this.openFollowing(p)));
            buttons.add(ld.btn(p, "<gold>⚙ Settings", "<gray>Status and privacy", () -> this.plugin.settingsDialogs().hub(p)));
            ld.screen(
                p,
                LobbyDialogs.title("Friends", this.onlineFriends(p).size() + " online · " + ids.size() + " total"),
                tiles,
                List.of(Text.mm("<gray>Your status: " + this.status(p).color + "● <b>" + this.status(p).label)),
                Text.mm("<gray>No friends yet.\n<dark_gray>Add one with <white>/friend add <name>"),
                buttons,
                2,
                ld.close(p)
            );
        }
    }

    private long seen(UUID u) {
        Long l = this.lastSeen.get(u);
        if (l != null) {
            return l;
        } else {
            Player online = Bukkit.getPlayer(u);
            return online != null ? online.getLastLogin() : Bukkit.getOfflinePlayer(u).getLastSeen();
        }
    }

    private void friendOptions(Player p, UUID u, String name) {
        LobbyDialogs ld = this.plugin.dialogs().lobby();
        Player f = Bukkit.getPlayer(u);
        boolean on = f != null && this.appearsOnline(p, f);
        List<ActionButton> buttons = new ArrayList<>();
        if (on) {
            buttons.add(ld.btn(p, "<gold>⚔ Duel", "<gray>Send " + Text.esc(name) + " a duel request", () -> {
                if (f.isOnline()) {
                    this.plugin.duelDialogs().pickKit(p, f);
                }
            }));
            buttons.add(ld.btn(p, "<aqua>✉ Message", null, () -> {
                p.closeDialog();
                this.plugin.dialogs().text(p, Text.mm("<aqua><b>Message " + Text.esc(name)), "Message", "", 200, msg -> {
                    Player now = Bukkit.getPlayer(u);
                    if (now == null || !this.appearsOnline(p, now)) {
                        p.sendMessage(Text.mm("<red>" + Text.esc(name) + " is not online."));
                    } else if (!msg.isBlank()) {
                        p.performCommand("msg " + now.getName() + " " + msg);
                    }
                });
            }));
            Session s = this.plugin.sessions().get(f);
            if (s.state == PlayerState.MATCH && s.match != null) {
                Match m = s.match;
                buttons.add(ld.btn(p, "<aqua>👁 Spectate", null, () -> {
                    p.closeDialog();
                    net.vortexrealm.gui.menus.SpectateMenu.spectate(this.plugin, p, m);
                }));
            }

            buttons.add(ld.btn(p, "<green>+ Invite to Party", null, () -> {
                p.closeDialog();
                if (f.isOnline()) {
                    this.plugin.parties().invite(p, f);
                }
            }));
        }

        boolean followingThem = this.follows(p.getUniqueId(), u);
        buttons.add(ld.btn(p, followingThem ? "<gray>Unfollow" : "<aqua>➹ Follow", null, () -> {
            if (followingThem) {
                this.unfollow(p.getUniqueId(), u);
            } else {
                this.follow(p.getUniqueId(), u);
            }

            this.friendOptions(p, u, name);
        }));
        buttons.add(ld.btn(p, "<red>Remove Friend", null, () -> this.plugin.dialogs().confirm(
            p, Text.mm("<red><b>Remove " + Text.esc(name) + "?"), List.of(Text.mm("<gray>You can add them again later.")), () -> {
                this.removeFriend(p.getUniqueId(), u);
                p.sendMessage(Text.mm("<gray>Removed <white>" + Text.esc(name) + "</white> from your friends."));
            }
        )));
        ld.screen(
            p,
            LobbyDialogs.title("Friends", Text.esc(name)),
            List.of(),
            List.of(Text.mm(on ? this.status(f).color + "● " + this.status(f).label + " <dark_gray>· " + this.whereabouts(f) : "<dark_gray>● Offline")),
            null,
            buttons,
            2,
            ld.btn(p, "<gray>« Back", null, () -> this.openFriends(p))
        );
    }

    private void openRequests(Player p) {
        LobbyDialogs ld = this.plugin.dialogs().lobby();
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : this.requestsFor(p.getUniqueId()).entrySet()) {
            UUID u = e.getKey();
            String n = Bukkit.getOfflinePlayer(u).getName();
            String name = n == null ? u.toString().substring(0, 8) : n;
            Player f = Bukkit.getPlayer(u);
            tiles.add(
                new LobbyDialogs.Tile(
                    f != null && this.appearsOnline(p, f) ? Items.of(Material.PLAYER_HEAD).skull(f).build() : Items.of(Material.SKELETON_SKULL).build(),
                    Text.mm("<white><b>" + Text.esc(name)),
                    Text.mm("<gray>sent " + ago(e.getValue())),
                    List.of(Text.mm("<white><b>" + Text.esc(name)), Component.empty(), Text.mm("<green>▶ Click to accept or deny")),
                    () -> this.plugin
                        .dialogs()
                        .lobby()
                        .screen(
                            p,
                            LobbyDialogs.title("Friend Request", Text.esc(name)),
                            List.of(),
                            List.of(Text.mm("<white>" + Text.esc(name) + " <gray>wants to be your friend.")),
                            null,
                            List.of(ld.btn(p, "<green><b>Accept", null, () -> {
                                this.acceptFriend(p, u, name);
                                this.openRequests(p);
                            }), ld.btn(p, "<red>Deny", null, () -> {
                                this.denyFriend(p, u, name);
                                this.openRequests(p);
                            })),
                            2,
                            ld.btn(p, "<gray>« Back", null, () -> this.openRequests(p))
                        )
                )
            );
        }

        ld.screen(
            p,
            LobbyDialogs.title("Friend Requests", tiles.size() + " waiting"),
            tiles,
            List.of(),
            Text.mm("<gray>No friend requests right now."),
            List.of(),
            1,
            ld.btn(p, "<gray>« Back", null, () -> this.openFriends(p))
        );
    }

    private void openFollowing(Player p) {
        LobbyDialogs ld = this.plugin.dialogs().lobby();
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        for (UUID u : this.followingOf(p.getUniqueId())) {
            String n = Bukkit.getOfflinePlayer(u).getName();
            String name = n == null ? u.toString().substring(0, 8) : n;
            Player f = Bukkit.getPlayer(u);
            boolean on = f != null && this.appearsOnline(p, f);
            tiles.add(
                new LobbyDialogs.Tile(
                    f != null && this.appearsOnline(p, f) ? Items.of(Material.PLAYER_HEAD).skull(f).build() : Items.of(Material.SKELETON_SKULL).build(),
                    Text.mm((on ? this.status(f).color + "● <white>" : "<dark_gray>● <gray>") + "<b>" + Text.esc(name)),
                    Text.mm(on ? this.whereabouts(f) : "<dark_gray>Offline"),
                    List.of(Text.mm("<white><b>" + Text.esc(name)), Component.empty(), Text.mm("<red>▶ Click to unfollow")),
                    () -> {
                        this.unfollow(p.getUniqueId(), u);
                        this.openFollowing(p);
                    }
                )
            );
        }

        ld.screen(
            p,
            LobbyDialogs.title("Following", tiles.size() + " players · " + this.followersOf(p.getUniqueId()).size() + " followers"),
            tiles,
            List.of(),
            Text.mm("<gray>You don't follow anyone.\n<dark_gray>/follow <name> to follow a player"),
            List.of(),
            1,
            ld.btn(p, "<gray>« Back", null, () -> this.openFriends(p))
        );
    }

    private void chatList(Player p) {
        List<UUID> ids = new ArrayList<>(this.friendsOf(p.getUniqueId()));
        p.sendMessage(Text.mm("<#FFD84A><b>☺ Friends</b> <dark_gray>(" + this.onlineFriends(p).size() + " online / " + ids.size() + ")"));
        if (ids.isEmpty()) {
            p.sendMessage(Text.mm("<gray>No friends yet. <white>/friend add <name>"));
        }

        for (UUID u : ids) {
            Player f = Bukkit.getPlayer(u);
            boolean on = f != null && this.appearsOnline(p, f);
            String n = Bukkit.getOfflinePlayer(u).getName();
            String name = n == null ? u.toString().substring(0, 8) : n;
            p.sendMessage(Text.mm((on ? this.status(f).color + "● <white>" + Text.esc(name) + " <dark_gray>- " + this.whereabouts(f) : "<dark_gray>● <gray>" + Text.esc(name) + " <dark_gray>- offline")));
        }
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.error(sender, "general.players-only");
            return true;
        } else if (!this.enabled()) {
            p.sendMessage(Text.mm("<red>Friends are disabled on this server."));
            return true;
        } else {
            switch (command.getName().toLowerCase(Locale.ROOT)) {
                case "friend" -> this.friendCommand(p, args);
                case "follow" -> {
                    if (args.length < 1) {
                        p.sendMessage(Text.mm("<gray>Usage: <white>/follow <player>"));
                    } else {
                        OfflinePlayer t = lookup(args[0]);
                        if (t == null || t.getName() == null || !t.hasPlayedBefore() && !t.isOnline()) {
                            Msg.error(p, "general.player-unknown", "player", args[0]);
                        } else if (t.getUniqueId().equals(p.getUniqueId())) {
                            p.sendMessage(Text.mm("<red>You can't follow yourself."));
                        } else if (this.follow(p.getUniqueId(), t.getUniqueId())) {
                            p.sendMessage(Text.mm("<aqua>➹ You now follow <white>" + Text.esc(t.getName()) + "<aqua>."));
                            Player o = t.getPlayer();
                            if (o != null && this.appearsOnline(o, p)) {
                                o.sendMessage(Text.mm("<aqua>➹ <white>" + Text.esc(p.getName()) + " <gray>started following you."));
                            }
                        } else {
                            p.sendMessage(Text.mm("<gray>You already follow <white>" + Text.esc(t.getName()) + "<gray>."));
                        }
                    }
                }
                case "unfollow" -> {
                    if (args.length < 1) {
                        p.sendMessage(Text.mm("<gray>Usage: <white>/unfollow <player>"));
                    } else {
                        UUID t = byName(this.followingOf(p.getUniqueId()), args[0]);
                        if (t != null && this.unfollow(p.getUniqueId(), t)) {
                            p.sendMessage(Text.mm("<gray>You stopped following <white>" + Text.esc(args[0]) + "<gray>."));
                        } else {
                            p.sendMessage(Text.mm("<red>You don't follow " + Text.esc(args[0]) + "."));
                        }
                    }
                }
                case "status" -> {
                    String alias = label.substring(label.indexOf(58) + 1).toLowerCase(Locale.ROOT);
                    SocialManager.Status byAlias = alias.equals("status") ? null : SocialManager.Status.parse(alias);
                    if (args.length < 1 && byAlias != null) {
                        this.setStatus(p, byAlias);
                    } else if (args.length < 1) {
                        if (this.plugin.settingsDialogs() != null) {
                            this.plugin.settingsDialogs().hub(p);
                        } else {
                            p.sendMessage(Text.mm("<gray>Usage: <white>/status <online|invisible|dnd>"));
                        }
                    } else {
                        SocialManager.Status s = SocialManager.Status.parse(args[0]);
                        if (s == null) {
                            p.sendMessage(Text.mm("<gray>Usage: <white>/status <online|invisible|dnd>"));
                        } else {
                            this.setStatus(p, s);
                        }
                    }
                }
                default -> {
                }
            }

            return true;
        }
    }

    private void friendCommand(Player p, String[] args) {
        if (args.length == 0) {
            this.openFriends(p);
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String who = args.length > 1 ? args[1] : null;
            switch (sub) {
                case "add", "request" -> {
                    if (who == null) {
                        p.sendMessage(Text.mm("<gray>Usage: <white>/friend add <player>"));
                    } else {
                        this.requestFriend(p, lookup(who));
                    }
                }
                case "accept" -> {
                    if (who == null) {
                        this.openRequests(p);
                    } else {
                        UUID id = byName(this.requestsFor(p.getUniqueId()).keySet(), who);
                        if (id == null) {
                            p.sendMessage(Text.mm("<red>You have no friend request from <white>" + Text.esc(who) + "<red>."));
                        } else {
                            this.acceptFriend(p, id, who);
                        }
                    }
                }
                case "deny", "decline" -> {
                    if (who == null) {
                        this.openRequests(p);
                    } else {
                        UUID id = byName(this.requestsFor(p.getUniqueId()).keySet(), who);
                        if (id == null) {
                            p.sendMessage(Text.mm("<red>You have no friend request from <white>" + Text.esc(who) + "<red>."));
                        } else {
                            this.denyFriend(p, id, who);
                        }
                    }
                }
                case "remove", "delete", "unfriend" -> {
                    if (who == null) {
                        p.sendMessage(Text.mm("<gray>Usage: <white>/friend remove <player>"));
                    } else if (byName(this.friendsOf(p.getUniqueId()), who) != null && this.removeFriend(p.getUniqueId(), byName(this.friendsOf(p.getUniqueId()), who))) {
                        p.sendMessage(Text.mm("<gray>Removed <white>" + Text.esc(who) + "</white> from your friends."));
                    } else {
                        p.sendMessage(Text.mm("<red>" + Text.esc(who) + " is not your friend."));
                    }
                }
                case "list" -> {
                    if (this.plugin.dialogs().useDialog(p)) {
                        this.openFriends(p);
                    } else {
                        this.chatList(p);
                    }
                }
                case "requests" -> this.openRequests(p);
                default -> {
                    if (args.length == 1) {
                        this.requestFriend(p, lookup(args[0]));
                    } else {
                        p.sendMessage(Text.mm("<gray>Usage: <white>/friend [add|accept|deny|remove|list|requests] <player>"));
                    }
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (sender instanceof Player p) {
            String cmd = command.getName().toLowerCase(Locale.ROOT);
            if (cmd.equals("status") && args.length == 1) {
                out.addAll(List.of("online", "invisible", "dnd"));
            } else if (cmd.equals("friend") && args.length == 1) {
                out.addAll(List.of("add", "accept", "deny", "remove", "list", "requests"));
            } else if (cmd.equals("friend") && args.length == 2) {
                String sub = args[0].toLowerCase(Locale.ROOT);
                if (sub.equals("remove")) {
                    for (UUID u : this.friendsOf(p.getUniqueId())) {
                        String n = Bukkit.getOfflinePlayer(u).getName();
                        if (n != null) {
                            out.add(n);
                        }
                    }
                } else if (sub.equals("accept") || sub.equals("deny")) {
                    for (UUID u : this.requestsFor(p.getUniqueId()).keySet()) {
                        String n = Bukkit.getOfflinePlayer(u).getName();
                        if (n != null) {
                            out.add(n);
                        }
                    }
                } else {
                    this.visibleNames(p, out);
                }
            } else if ((cmd.equals("follow") || cmd.equals("unfollow")) && args.length == 1) {
                if (cmd.equals("unfollow")) {
                    for (UUID u : this.followingOf(p.getUniqueId())) {
                        String n = Bukkit.getOfflinePlayer(u).getName();
                        if (n != null) {
                            out.add(n);
                        }
                    }
                } else {
                    this.visibleNames(p, out);
                }
            }

            String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(typed));
        }

        return out;
    }

    private void visibleNames(Player p, List<String> out) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(p) && p.canSee(o) && this.appearsOnline(p, o)) {
                out.add(o.getName());
            }
        }
    }

    // ------------------------------------------------------------------ types

    public static enum Status {
        ONLINE("online", "Online", "<#55FF55>"),
        INVISIBLE("invisible", "Invisible", "<#9E9E9E>"),
        DND("dnd", "Do Not Disturb", "<#FF5555>");

        public final String id;
        public final String label;
        public final String color;

        private Status(String id, String label, String color) {
            this.id = id;
            this.label = label;
            this.color = color;
        }

        public String dot() {
            return this.color + "●";
        }

        public static SocialManager.Status of(String id) {
            SocialManager.Status s = parse(id);
            return s == null ? ONLINE : s;
        }

        public static SocialManager.Status parse(String id) {
            if (id == null) {
                return null;
            } else {
                String x = id.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
                for (SocialManager.Status s : values()) {
                    if (s.id.equals(x)) {
                        return s;
                    }
                }

                return switch (x) {
                    case "on" -> ONLINE;
                    case "offline", "invis", "hidden", "appearoffline" -> INVISIBLE;
                    case "donotdisturb", "busy" -> DND;
                    default -> null;
                };
            }
        }
    }

    public static enum Kind {
        DUEL("duel requests"),
        PARTY("party invites"),
        MESSAGE("messages"),
        FRIEND("friend requests"),
        TELEPORT("teleport requests");

        public final String label;

        private Kind(String label) {
            this.label = label;
        }
    }

    public static enum Group {
        FRIENDS("fr", "Friends", "<#FFD84A>☺", "Your friends"),
        FOLLOWING("fg", "Following", "<#FFB547>➹", "Players you follow"),
        FOLLOWERS("fl", "Followers", "<#C9C9C9>☻", "Players who follow you"),
        FAMOUS("fm", "Famous", "<#FF4D4D>▶", "Creators and partners"),
        NOOBS("nb", "Noobs", "<#C08A5A>☹", "Everyone else - limited by the Tier and Ranking filters");

        public final String code;
        public final String label;
        public final String icon;
        public final String description;

        private Group(String code, String label, String icon, String description) {
            this.code = code;
            this.label = label;
            this.icon = icon;
            this.description = description;
        }

        static SocialManager.Group byCode(String code) {
            for (SocialManager.Group g : values()) {
                if (g.code.equals(code)) {
                    return g;
                }
            }

            return null;
        }
    }

    /**
     * Who may send requests: any member of a ticked group, or - when Noobs is ticked - anyone else whose best tier
     * is at least {@code minTier} (-1 = off) and whose overall position is within {@code topRank} (0 = off).
     * Stored as {@code fr.fg.fl.fm.nb|tier|rank}.
     */
    public record Filter(EnumSet<SocialManager.Group> groups, int minTier, int topRank) {
        public static SocialManager.Filter parse(String raw) {
            EnumSet<SocialManager.Group> g = EnumSet.noneOf(SocialManager.Group.class);
            int tier = -1;
            int rank = 0;
            if (raw != null) {
                String[] parts = raw.split("\\|");
                if (parts.length > 0 && !parts[0].isBlank() && !parts[0].equals("-")) {
                    for (String c : parts[0].split("\\.")) {
                        SocialManager.Group x = SocialManager.Group.byCode(c.trim());
                        if (x != null) {
                            g.add(x);
                        }
                    }
                }

                try {
                    if (parts.length > 1) {
                        tier = Math.max(-1, Math.min(Tier.values().length - 1, Integer.parseInt(parts[1].trim())));
                    }

                    if (parts.length > 2) {
                        rank = Math.max(0, Integer.parseInt(parts[2].trim()));
                    }
                } catch (NumberFormatException var6) {
                }
            }

            return new SocialManager.Filter(g, tier, rank);
        }

        public String serialize() {
            StringBuilder sb = new StringBuilder();
            for (SocialManager.Group x : this.groups) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }

                sb.append(x.code);
            }

            return (sb.isEmpty() ? "-" : sb.toString()) + "|" + this.minTier + "|" + this.topRank;
        }

        public SocialManager.Filter with(SocialManager.Group x, boolean on) {
            EnumSet<SocialManager.Group> g = EnumSet.copyOf(this.groups.isEmpty() ? EnumSet.noneOf(SocialManager.Group.class) : this.groups);
            if (on) {
                g.add(x);
            } else {
                g.remove(x);
            }

            return new SocialManager.Filter(g, this.minTier, this.topRank);
        }

        public SocialManager.Filter withTier(int t) {
            return new SocialManager.Filter(this.groups, t, this.topRank);
        }

        public SocialManager.Filter withRank(int r) {
            return new SocialManager.Filter(this.groups, this.minTier, r);
        }
    }
}
