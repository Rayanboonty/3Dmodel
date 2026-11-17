package net.vortexpvp.plus;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.api.MatchEndEvent;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.match.Match;
import net.vortexpvp.match.MatchStats;
import net.vortexpvp.match.MatchTeam;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Community features: the creator program (creator tag, creator codes that pay creators a share of what their
 * supporters earn), LIVE badges (/live &lt;link&gt;), streamer mode (IPs and coordinates are masked in the chat you see),
 * mentors (a high-rank player helps a new one; both earn when the mentee wins), coach mode (/coach spectates a
 * player with a live CPS/combo overlay and saves timestamped notes into the replay), one-click friend spectating and
 * the cinematic spectator camera.
 */
public final class Social implements Listener {
    private static final Pattern IP = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{2,5})?\\b");
    private static final Pattern COORDS = Pattern.compile("-?\\d{2,7}[ ,/]+-?\\d{1,3}[ ,/]+-?\\d{2,7}");
    private final VortexPvP plugin;
    private final PlusModule plus;
    private final Map<UUID, UUID> coaching = new HashMap<>();
    private final Map<UUID, UUID> mentorOffers = new HashMap<>();
    private final Map<UUID, Cine> cinematic = new HashMap<>();

    private static final class Cine {
        final int match;
        double angle;

        Cine(int match) {
            this.match = match;
        }
    }

    public Social(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
    }

    private YamlConfiguration creators() {
        return this.plus.store().doc("creators");
    }

    // ------------------------------------------------------------------ creators

    public boolean isCreator(UUID u) {
        return this.creators().getString("by-uuid." + u) != null;
    }

    public String creatorCode(UUID u) {
        return this.creators().getString("by-uuid." + u);
    }

    public void creatorCommand(org.bukkit.command.CommandSender s, String[] a) {
        YamlConfiguration y = this.creators();
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add" -> {
                if (a.length < 3) {
                    s.sendMessage(Text.mm("<red>/creator add <player> <code>"));
                    return;
                }

                UUID u = this.plus.store().uuidOf(a[1]);
                if (u == null) {
                    Player p = Bukkit.getPlayerExact(a[1]);
                    u = p == null ? null : p.getUniqueId();
                }

                if (u == null) {
                    s.sendMessage(Text.mm("<red>Unknown player."));
                    return;
                }

                String code = a[2].toUpperCase(Locale.ROOT);
                y.set("codes." + code + ".uuid", u.toString());
                y.set("codes." + code + ".name", a[1]);
                y.set("by-uuid." + u, code);
                this.plus.store().dirty("creators");
                this.plus.progression().title(u, "Creator");
                s.sendMessage(Text.mm("<green>" + Text.esc(a[1]) + " is now a creator with code <white>" + code));
            }
            case "remove" -> {
                if (a.length < 2) {
                    return;
                }

                String code = a[1].toUpperCase(Locale.ROOT);
                String u = y.getString("codes." + code + ".uuid");
                if (u != null) {
                    y.set("by-uuid." + u, null);
                }

                y.set("codes." + code, null);
                this.plus.store().dirty("creators");
                s.sendMessage(Text.mm("<gray>Creator code " + code + " removed."));
            }
            default -> {
                org.bukkit.configuration.ConfigurationSection c = y.getConfigurationSection("codes");
                s.sendMessage(Text.mm("<light_purple><b>Creators"));
                if (c != null) {
                    for (String code : c.getKeys(false)) {
                        s.sendMessage(Text.mm("<white>" + Text.esc(y.getString("codes." + code + ".name", "?")) + " <dark_gray>- <light_purple>" + code
                            + " <gray>(" + y.getInt("codes." + code + ".supporters") + " supporters, " + y.getLong("codes." + code + ".earned") + " shards earned)"));
                    }
                }
            }
        }
    }

    public void useCode(Player p, String code) {
        String c = code.toUpperCase(Locale.ROOT);
        YamlConfiguration y = this.creators();
        if (!y.contains("codes." + c)) {
            p.sendMessage(Text.mm("<red>No creator code " + Text.esc(c) + ". <gray>Creators: /creator list"));
            return;
        }

        if (p.getUniqueId().toString().equals(y.getString("codes." + c + ".uuid"))) {
            p.sendMessage(Text.mm("<red>You can't support yourself."));
            return;
        }

        PlayerData d = this.plus.store().get(p);
        if (!d.creatorCode.isEmpty() && y.contains("codes." + d.creatorCode)) {
            y.set("codes." + d.creatorCode + ".supporters", Math.max(0, y.getInt("codes." + d.creatorCode + ".supporters") - 1));
        }

        d.creatorCode = c;
        d.dirty = true;
        y.set("codes." + c + ".supporters", y.getInt("codes." + c + ".supporters") + 1);
        this.plus.store().dirty("creators");
        p.sendMessage(Text.mm("<light_purple>❤ You now support <white>" + Text.esc(y.getString("codes." + c + ".name", c)) + "</white>! <gray>They get a share of the shards you earn."));
    }

    /** A supporter earned shards: their creator gets a share (5% by default). */
    public void credit(UUID supporter, long shards) {
        PlayerData d = this.plus.store().ifLoaded(supporter);
        if (d == null || d.creatorCode.isEmpty()) {
            return;
        }

        YamlConfiguration y = this.creators();
        String u = y.getString("codes." + d.creatorCode + ".uuid");
        long share = Math.round(shards * this.plus.config().d("creators.share", 0.05));
        if (u == null || share <= 0) {
            return;
        }

        y.set("codes." + d.creatorCode + ".earned", y.getLong("codes." + d.creatorCode + ".earned") + share);
        this.plus.store().dirty("creators");
        UUID cu = UUID.fromString(u);
        if (this.plugin.economy() != null) {
            this.plugin.economy().giveShards(cu, share);
        } else {
            PlayerData cd = this.plus.store().get(cu);
            cd.coins += share;
            cd.dirty = true;
        }
    }

    // ------------------------------------------------------------------ live and streamer mode

    public void live(Player p, String[] a) {
        PlayerData d = this.plus.store().get(p);
        if (a.length == 0 || a[0].equalsIgnoreCase("off")) {
            d.live = "";
            d.dirty = true;
            p.sendMessage(Text.mm("<gray>LIVE badge off."));
            this.plugin.tab().refreshSoon();
            return;
        }

        String url = a[0];
        if (!url.matches("(?i)https?://(www\\.)?(twitch\\.tv|youtube\\.com|youtu\\.be|kick\\.com|tiktok\\.com)/\\S+")) {
            p.sendMessage(Text.mm("<red>Use a Twitch, YouTube, Kick or TikTok link."));
            return;
        }

        if (!p.hasPermission("vortexpvp.live")) {
            p.sendMessage(Text.mm("<red>You need the creator or media rank to go live."));
            return;
        }

        d.live = url;
        d.dirty = true;
        this.plugin.tab().refreshSoon();
        Bukkit.broadcast(Text.mm("<red>● LIVE <white>" + Text.esc(p.getName()) + " <gray>is streaming now: <click:open_url:'" + url.replace("'", "") + "'><aqua>[watch]</aqua></click>"));
    }

    public void streamer(Player p) {
        PlayerData d = this.plus.store().get(p);
        d.streamer = !d.streamer;
        d.dirty = true;
        p.sendMessage(Text.mm(d.streamer ? "<green>Streamer mode on: <gray>IP addresses and coordinates in the chat you see are hidden, private messages from strangers are blocked."
            : "<gray>Streamer mode off."));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        String plain = Text.plain(e.message());
        if (!IP.matcher(plain).find() && !COORDS.matcher(plain).find()) {
            return;
        }

        List<Player> streamers = new ArrayList<>();
        for (Audience a : e.viewers()) {
            if (a instanceof Player p) {
                PlayerData d = this.plus.store().ifLoaded(p.getUniqueId());
                if (d != null && d.streamer && p != e.getPlayer()) {
                    streamers.add(p);
                }
            }
        }

        if (streamers.isEmpty()) {
            return;
        }

        Component masked = e.message().replaceText(TextReplacementConfig.builder().match(IP).replacement("[hidden]").build())
            .replaceText(TextReplacementConfig.builder().match(COORDS).replacement("[coords]").build());
        for (Player s : streamers) {
            e.viewers().remove(s);
            Component line = e.renderer().render(e.getPlayer(), e.getPlayer().displayName(), masked, s);
            s.sendMessage(line);
        }
    }

    public boolean blocksMessagesFrom(Player to, Player from) {
        PlayerData d = this.plus.store().ifLoaded(to.getUniqueId());
        return d != null && d.streamer && !this.plugin.social().areFriends(to.getUniqueId(), from.getUniqueId()) && !from.hasPermission("vortexpvp.staff");
    }

    // ------------------------------------------------------------------ mentors

    public void mentor(Player p, String[] a) {
        PlayerData d = this.plus.store().get(p);
        if (a.length == 0) {
            if (!d.mentor.isEmpty()) {
                String n = Bukkit.getOfflinePlayer(UUID.fromString(d.mentor)).getName();
                p.sendMessage(Text.mm("<aqua>Your mentor: <white>" + Text.esc(n == null ? "?" : n) + " <gray>(/mentor leave)"));
            }

            p.sendMessage(Text.mm("<gray>/mentor <player> - offer to mentor a new player (Diamond overall or higher)"));
            p.sendMessage(Text.mm("<gray>/mentor accept - accept an offer • /mentor leave - stop"));
            return;
        }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "accept" -> {
                UUID from = this.mentorOffers.remove(p.getUniqueId());
                if (from == null) {
                    p.sendMessage(Text.mm("<red>No mentor offer."));
                    return;
                }

                d.mentor = from.toString();
                d.dirty = true;
                String n = Bukkit.getOfflinePlayer(from).getName();
                p.sendMessage(Text.mm("<green>✔ " + Text.esc(n == null ? "?" : n) + " is now your mentor. <gray>You both earn XP when you win."));
                Player m = Bukkit.getPlayer(from);
                if (m != null) {
                    m.sendMessage(Text.mm("<green>✔ " + Text.esc(p.getName()) + " accepted you as their mentor."));
                }
            }
            case "leave" -> {
                d.mentor = "";
                d.dirty = true;
                p.sendMessage(Text.mm("<gray>You no longer have a mentor."));
            }
            default -> {
                Player t = Bukkit.getPlayerExact(a[0]);
                if (t == null || t == p) {
                    p.sendMessage(Text.mm("<red>That player is not online."));
                    return;
                }

                PvPProfile mine = this.plugin.profiles().get(p);
                PvPProfile theirs = this.plugin.profiles().get(t);
                int need = this.plus.config().i("mentors.min-step", 18);
                if (this.plus.ladder().overall(mine) < need && !p.hasPermission("vortexpvp.admin")) {
                    p.sendMessage(Text.mm("<red>Mentors need an overall rank of " + Ladder.name(need) + " or higher."));
                    return;
                }

                if (this.plus.ladder().overall(theirs) >= 9 || this.plus.store().get(t).count("play") > 300) {
                    p.sendMessage(Text.mm("<red>Mentees must be new players (below Gold overall)."));
                    return;
                }

                this.mentorOffers.put(t.getUniqueId(), p.getUniqueId());
                p.sendMessage(Text.mm("<green>Mentor offer sent to " + Text.esc(t.getName()) + "."));
                t.sendMessage(Text.mm("<aqua>" + Text.esc(p.getName()) + " <gray>(" + Ladder.colored(this.plus.ladder().overall(mine)) + "<gray>) offers to be your mentor. "
                    + "<click:run_command:'/mentor accept'><green>[accept]</green></click>"));
            }
        }
    }

    @EventHandler
    public void onEnd(MatchEndEvent e) {
        MatchTeam w = e.winner();
        if (w == null || "CANCELLED".equals(e.reason())) {
            return;
        }

        for (UUID u : w.members) {
            PlayerData d = this.plus.store().ifLoaded(u);
            if (d != null && !d.mentor.isEmpty()) {
                UUID m = UUID.fromString(d.mentor);
                this.plus.progression().xp(u, 15L, "mentee bonus");
                this.plus.progression().xp(m, 20L, "your mentee won");
                this.plus.progression().track(m, "mentee_win", null, 1L);
            }
        }
    }

    // ------------------------------------------------------------------ coach, friend spectate, cinematic camera

    public void coach(Player coach, String[] a) {
        if (a.length == 0) {
            coach.sendMessage(Text.mm("<gray>/coach <player> - spectate with a live overlay • /coach note <text> • /coach stop"));
            return;
        }

        if (a[0].equalsIgnoreCase("stop")) {
            this.coaching.remove(coach.getUniqueId());
            coach.sendMessage(Text.mm("<gray>Coach mode off."));
            return;
        }

        if (a[0].equalsIgnoreCase("note")) {
            UUID t = this.coaching.get(coach.getUniqueId());
            Player tp = t == null ? null : Bukkit.getPlayer(t);
            Match m = tp == null ? null : this.plugin.matches().ofPlayer(tp);
            if (m == null || a.length < 2) {
                coach.sendMessage(Text.mm("<red>Coach a player in a recorded match first, then /coach note <text>."));
                return;
            }

            String text = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
            if (this.plus.replays() != null && this.plus.replays().note(m.id, coach.getName(), text)) {
                coach.sendMessage(Text.mm("<green>📝 Note saved into the replay at " + m.durationSeconds() + "s."));
            } else {
                coach.sendMessage(Text.mm("<red>This match is not being recorded (only ranked matches are, by default)."));
            }

            return;
        }

        Player t = Bukkit.getPlayerExact(a[0]);
        Match m = t == null ? null : this.plugin.matches().ofPlayer(t);
        if (m == null) {
            coach.sendMessage(Text.mm("<red>That player is not in a match."));
            return;
        }

        if (!coach.hasPermission("vortexpvp.coach") && !this.plugin.social().areFriends(coach.getUniqueId(), t.getUniqueId())) {
            coach.sendMessage(Text.mm("<red>You can coach your friends (or get the coach rank)."));
            return;
        }

        if (this.plugin.sessions().state(coach) != PlayerState.SPECTATING && !m.addSpectator(coach)) {
            coach.sendMessage(Text.mm("<red>You can't spectate that match right now."));
            return;
        }

        this.coaching.put(coach.getUniqueId(), t.getUniqueId());
        coach.sendMessage(Text.mm("<aqua>Coaching " + Text.esc(t.getName()) + ". <gray>/coach note <text> saves a note at this moment of the replay."));
    }

    public void spectateFriend(Player p, String name) {
        Player t = Bukkit.getPlayerExact(name);
        if (t == null || !this.plugin.social().areFriends(p.getUniqueId(), t.getUniqueId())) {
            p.sendMessage(Text.mm("<red>That is not an online friend."));
            return;
        }

        Match m = this.plugin.matches().ofPlayer(t);
        if (m == null) {
            p.sendMessage(Text.mm("<gray>" + Text.esc(t.getName()) + " is not in a match right now."));
            return;
        }

        if (!m.addSpectator(p)) {
            p.sendMessage(Text.mm("<red>That match can't be spectated."));
        }
    }

    public void cinematic(Player p, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("off")) {
            if (this.cinematic.remove(p.getUniqueId()) != null) {
                p.sendMessage(Text.mm("<gray>Cinematic camera off."));
            } else {
                p.sendMessage(Text.mm("<gray>/cinematic <player> - a slow orbit camera around their match (great for streams)."));
            }

            return;
        }

        Player t = Bukkit.getPlayerExact(a[0]);
        Match m = t == null ? null : this.plugin.matches().ofPlayer(t);
        if (m == null) {
            p.sendMessage(Text.mm("<red>That player is not in a match."));
            return;
        }

        if (this.plugin.sessions().state(p) != PlayerState.SPECTATING && !m.addSpectator(p)) {
            p.sendMessage(Text.mm("<red>You can't spectate that match."));
            return;
        }

        this.cinematic.put(p.getUniqueId(), new Cine(m.id));
        p.sendMessage(Text.mm("<aqua>🎥 Cinematic camera on. <gray>/cinematic off to stop."));
    }

    /** Every tick: coach overlays (every 5 ticks) and cinematic cameras. */
    public void tick(int tick) {
        if (tick % 5 == 0) {
            for (Map.Entry<UUID, UUID> en : new ArrayList<>(this.coaching.entrySet())) {
                Player c = Bukkit.getPlayer(en.getKey());
                Player t = Bukkit.getPlayer(en.getValue());
                Match m = t == null ? null : this.plugin.matches().ofPlayer(t);
                if (c == null || m == null || this.plugin.sessions().state(c) != PlayerState.SPECTATING) {
                    this.coaching.remove(en.getKey());
                    continue;
                }

                MatchStats st = m.stats(t.getUniqueId());
                c.sendActionBar(Text.mm("<aqua>Coach <dark_gray>» <white>" + Text.esc(t.getName()) + " <gray>CPS <white>" + this.plus.analysis().cps(t.getUniqueId())
                    + " <gray>Combo <gold>" + (st == null ? 0 : st.combo) + " <gray>Hits <white>" + (st == null ? 0 : st.hits) + " <gray>HP <red>" + Math.round(t.getHealth())
                    + (this.plus.analysis().freshSprint(t) ? " <green>W-TAP" : "")));
            }
        }

        for (Map.Entry<UUID, Cine> en : new ArrayList<>(this.cinematic.entrySet())) {
            Player p = Bukkit.getPlayer(en.getKey());
            Match m = this.plugin.matches().byId(en.getValue().match);
            if (p == null || m == null || this.plugin.sessions().state(p) != PlayerState.SPECTATING || m.state() == Match.State.ENDED) {
                this.cinematic.remove(en.getKey());
                continue;
            }

            List<Player> fighters = m.alivePlayers();
            if (fighters.isEmpty()) {
                continue;
            }

            double x = 0;
            double y = 0;
            double z = 0;
            for (Player f : fighters) {
                x += f.getLocation().getX();
                y += f.getLocation().getY();
                z += f.getLocation().getZ();
            }

            Location mid = new Location(fighters.get(0).getWorld(), x / fighters.size(), y / fighters.size() + 1.0, z / fighters.size());
            Cine c = en.getValue();
            c.angle += 0.006;
            double r = 9.0;
            Location cam = mid.clone().add(Math.cos(c.angle) * r, 4.5, Math.sin(c.angle) * r);
            cam.setDirection(mid.toVector().subtract(cam.toVector()));
            Tp.to(p, cam);
            if (this.plugin.events() != null && this.plugin.events().current() != null && tick % 20 == 0) {
                p.sendActionBar(Text.mm("<gold>🏆 " + Text.esc(Text.plain(Text.mm(this.plugin.events().status())))));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.coaching.remove(e.getPlayer().getUniqueId());
        this.cinematic.remove(e.getPlayer().getUniqueId());
        this.mentorOffers.remove(e.getPlayer().getUniqueId());
    }

    public String selfCheck() {
        if (!IP.matcher("join 123.45.67.89:25565 now").find() || !COORDS.matcher("base at 1200 64 -3400").find() || IP.matcher("I won 3 2 1").find()) {
            return "masks";
        }

        return null;
    }
}
