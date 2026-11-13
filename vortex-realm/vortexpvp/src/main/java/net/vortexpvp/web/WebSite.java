package net.vortexpvp.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.vortexpvp.arena.VanillaTerrain;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.ai.AiAgent;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.ConfigBackup;
import net.vortexpvp.data.Database;
import net.vortexpvp.data.KitStats;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.match.Match;
import net.vortexpvp.match.MatchTeam;
import net.vortexpvp.rank.Tier;
import net.vortexpvp.smp.WorldMode;
import net.vortexpvp.social.Countries;
import net.vortexpvp.social.RankAccess;
import net.vortexpvp.social.Ranks;
import net.vortexpvp.staff.Reports;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class WebSite implements TabExecutor {
    private static final Gson GSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long TOKEN_MS = 600000L;
    private static final long SESSION_MS = 7200000L;
    private final VortexPvP plugin;
    private final WebServer web;
    private final Map<String, WebSite.Login> tokens = new ConcurrentHashMap<>();
    private final Map<String, WebSite.Login> sessions = new ConcurrentHashMap<>();
    private final Map<String, int[]> failures = new ConcurrentHashMap<>();
    private final long started = System.currentTimeMillis();
    private byte[] indexHtml;
    private byte[] adminHtml;
    private volatile JsonObject liveCache;
    private volatile long liveAt;
    private volatile List<Database.RecentMatch> recentCache = List.of();
    private volatile long recentAt;
    private static final String FAVICON = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'><circle cx='32' cy='32' r='30' fill='#1a1030'/><path d='M32 10a22 22 0 1 1-20 13' fill='none' stroke='#ffb020' stroke-width='6' stroke-linecap='round'/><circle cx='32' cy='32' r='5' fill='#ffe9b0'/></svg>";

    public WebSite(VortexPvP plugin, WebServer web) {
        this.plugin = plugin;
        this.web = web;
    }

    public void install() {
        this.indexHtml = page(this.resource("web/index.html"));
        this.adminHtml = page(this.resource("web/admin.html"));
        this.web.exact("GET", "/", r -> WebServer.Response.of(200, "text/html; charset=utf-8", this.indexHtml));
        this.web.exact("GET", "/admin", r -> WebServer.Response.of(200, "text/html; charset=utf-8", this.adminHtml).header("Cache-Control", "no-store"));
        this.web
            .exact(
                "GET",
                "/favicon.svg",
                r -> WebServer.Response.text(
                    200,
                    "image/svg+xml",
                    "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'><circle cx='32' cy='32' r='30' fill='#1a1030'/><path d='M32 10a22 22 0 1 1-20 13' fill='none' stroke='#ffb020' stroke-width='6' stroke-linecap='round'/><circle cx='32' cy='32' r='5' fill='#ffe9b0'/></svg>"
                )
            );

        for (String logo : List.of("logo.png", "logo-mark.png")) {
            byte[] png = this.resource("web/" + logo);
            this.web.exact("GET", "/" + logo, r -> WebServer.Response.of(200, "image/png", png).header("Cache-Control", "public, max-age=86400"));
        }

        this.web.prefix("GET", "/flag/", r -> {
            String code = r.path().substring("/flag/".length()).replace(".png", "").toLowerCase(Locale.ROOT);
            if (!code.matches("[a-z]{2}")) {
                return WebServer.Response.notFound();
            } else {
                WebServer.Response var3x;
                try (InputStream in = WebSite.class.getResourceAsStream("/flags/" + code + ".png")) {
                    if (in == null) {
                        return WebServer.Response.notFound();
                    }

                    var3x = WebServer.Response.of(200, "image/png", in.readAllBytes()).header("Cache-Control", "public, max-age=604800");
                }

                return var3x;
            }
        });
        this.web.exact("GET", "/api/server", r -> json(this.server()));
        this.web.exact("GET", "/api/site", r -> json(this.sync(this::site)));
        this.web.exact("GET", "/api/kits", r -> json(this.kits()));
        this.web.exact("GET", "/api/leaderboard", r -> json(this.board(r.param("kit"))));
        this.web.exact("GET", "/api/player", r -> json(this.player(r.param("name"))));
        this.web.exact("GET", "/api/ranks", r -> json(this.sync(this::ranksPublic)));
        this.web.exact("GET", "/api/staff", r -> json(this.sync(this::staff)));
        this.web.exact("GET", "/api/live", r -> json(this.live()));
        this.web
            .exact(
                "GET", "/api/events", r -> json(this.sync(() -> (Map)(this.plugin.events() == null ? new LinkedHashMap() : this.plugin.events().snapshot())))
            );
        this.web.exact("POST", "/api/admin/login", this::login);
        this.web.prefix(null, "/api/admin/", this::admin);
    }

    private byte[] resource(String path) {
        try {
            byte[] var3;
            try (InputStream in = this.plugin.getResource(path)) {
                var3 = in == null ? "Missing page".getBytes(StandardCharsets.UTF_8) : in.readAllBytes();
            }

            return var3;
        } catch (IOException var7) {
            return "Missing page".getBytes(StandardCharsets.UTF_8);
        }
    }

    private static byte[] page(byte[] body) {
        String s = new String(body, StandardCharsets.UTF_8);
        if (s.stripLeading().toLowerCase(Locale.ROOT).startsWith("<!doctype")) {
            return body;
        } else {
            String head = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n";
            return (head + s + "\n</html>").getBytes(StandardCharsets.UTF_8);
        }
    }

    private static WebServer.Response json(Object o) {
        return WebServer.Response.json(GSON.toJson(o)).header("Cache-Control", "no-store").header("Access-Control-Allow-Origin", "*");
    }

    private static WebServer.Response error(int status, String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("error", msg);
        return WebServer.Response.json(status, GSON.toJson((JsonElement)o));
    }

    private <T> T sync(Callable<T> c) throws Exception {
        return Bukkit.isPrimaryThread() ? c.call() : Bukkit.getScheduler().callSyncMethod(this.plugin, c).get(10L, TimeUnit.SECONDS);
    }

    private static String hex(String mini) {
        Component c = Text.mm(mini + "x");
        TextColor col = find(c);
        return col == null ? "#FFFFFF" : col.asHexString();
    }

    private static TextColor find(Component c) {
        if (c.color() != null) {
            return c.color();
        } else {
            for (Component ch : c.children()) {
                TextColor t = find(ch);
                if (t != null) {
                    return t;
                }
            }

            return null;
        }
    }

    private JsonObject server() throws Exception {
        return this.sync(() -> {
            JsonObject o = new JsonObject();
            o.addProperty("name", Cfg.s(Opt.SERVER_NAME));
            o.addProperty("ip", Cfg.s(Opt.SERVER_IP));
            o.addProperty("discord", Cfg.s(Opt.LINK_DISCORD));
            o.addProperty("online", Bukkit.getOnlinePlayers().size());
            o.addProperty("max", Bukkit.getMaxPlayers());
            o.addProperty("playing", this.plugin.matches().playingTotal() + this.plugin.ffa().playing());
            o.addProperty("queued", this.plugin.queues().total());
            JsonArray brief = new JsonArray();

            for (String line : Cfg.get(Opt.WELCOME_BRIEF)) {
                brief.add(Text.plain(Text.mm(line)));
            }

            o.add("about", brief);
            JsonArray players = new JsonArray();

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (players.size() < 80) {
                    JsonObject pj = new JsonObject();
                    pj.addProperty("name", p.getName());
                    pj.addProperty("uuid", p.getUniqueId().toString());
                    players.add(pj);
                }
            }

            o.add("players", players);
            return o;
        });
    }

    private JsonObject site() {
        JsonObject o = new JsonObject();
        o.addProperty("name", Cfg.s(Opt.SERVER_NAME));
        o.addProperty("ip", Cfg.s(Opt.SERVER_IP));
        o.addProperty("discord", Cfg.s(Opt.LINK_DISCORD));
        o.addProperty("store", Cfg.s(Opt.STORE_LINK).isBlank() ? Cfg.s(Opt.LINK_DISCORD) : Cfg.s(Opt.STORE_LINK));
        o.addProperty("tagline", Cfg.s(Opt.WEB_TAGLINE));
        String accent = Cfg.s(Opt.WEB_ACCENT).trim();
        o.addProperty("accent", accent.matches("#[0-9A-Fa-f]{6}") ? accent : "#FFB020");
        JsonArray pages = new JsonArray();
        Cfg.get(Opt.WEB_PAGES).forEach(s -> pages.add(s.trim().toLowerCase(Locale.ROOT)));
        o.add("pages", pages);
        o.add("news", split(Cfg.get(Opt.WEB_NEWS), "date", "title", "text"));
        o.add("rules", split(Cfg.get(Opt.WEB_RULES), "title", "text"));
        o.add("links", split(Cfg.get(Opt.WEB_LINKS), "name", "url"));
        JsonArray about = new JsonArray();

        for (String line : Cfg.get(Opt.WELCOME_BRIEF)) {
            about.add(Text.plain(Text.mm(line)));
        }

        o.add("about", about);
        JsonArray modes = new JsonArray();
        if (this.plugin.serverSelector() != null) {
            for (String[] e : this.plugin.serverSelector().publicEntries()) {
                JsonObject m = new JsonObject();
                m.addProperty("id", e[0]);
                m.addProperty("name", e[1]);
                m.addProperty("text", e[2]);
                m.addProperty("players", Integer.parseInt(e[3]));
                m.addProperty("status", e[4]);
                m.addProperty("icon", e[5]);
                modes.add(m);
            }
        }

        o.add("modes", modes);
        return o;
    }

    private JsonObject live() throws Exception {
        long now = System.currentTimeMillis();
        JsonObject cached = this.liveCache;
        if (cached != null && now - this.liveAt < 2000L) {
            return cached;
        } else {
            if (now - this.recentAt > 8000L) {
                this.recentAt = now;
                this.plugin
                    .db()
                    .supply(c -> this.plugin.db().recentMatches(c, 14))
                    .thenAccept(l -> this.recentCache = (List<Database.RecentMatch>)l)
                    .exceptionally(err -> null);
            }

            JsonObject o = this.sync(() -> {
                JsonObject j = new JsonObject();
                j.addProperty("online", Bukkit.getOnlinePlayers().size());
                j.addProperty("max", Bukkit.getMaxPlayers());
                j.addProperty("playing", this.plugin.matches().playingTotal() + this.plugin.ffa().playing());
                j.addProperty("queued", this.plugin.queues().total());
                j.addProperty("at", now);
                JsonArray players = new JsonArray();

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (players.size() >= 80) {
                        break;
                    }

                    JsonObject pj = new JsonObject();
                    pj.addProperty("name", p.getName());
                    pj.addProperty("state", this.plugin.sessions().state(p).name());
                    players.add(pj);
                }

                j.add("players", players);
                JsonArray matches = new JsonArray();

                for (Match m : this.plugin.matches().active()) {
                    if (m.state() != Match.State.ENDED && m.teams.size() >= 2 && matches.size() < 20) {
                        JsonObject mj = new JsonObject();
                        mj.addProperty("id", m.id);
                        mj.addProperty("kit", m.kit.displayName);
                        mj.addProperty("kitColor", hex(m.kit.color));
                        mj.addProperty("icon", m.kit.iconStack().getType().getKey().getKey());
                        mj.addProperty("ranked", m.ranked);
                        mj.addProperty("round", m.round());
                        mj.addProperty("firstTo", m.roundsToWin());
                        mj.addProperty("seconds", m.fightSeconds());
                        mj.addProperty("state", m.state().name());

                        for (int t = 0; t < 2; t++) {
                            MatchTeam team = m.teams.get(t);
                            JsonObject side = new JsonObject();
                            String first = team.members.isEmpty() ? "?" : String.valueOf(Bukkit.getOfflinePlayer(team.members.get(0)).getName());
                            side.addProperty("name", first + (team.members.size() > 1 ? " +" + (team.members.size() - 1) : ""));
                            side.addProperty("head", first);
                            side.addProperty("score", team.score);
                            mj.add(t == 0 ? "a" : "b", side);
                        }

                        matches.add(mj);
                    }
                }

                j.add("matches", matches);
                JsonArray queues = new JsonArray();

                for (Kit k : this.plugin.kits().sorted()) {
                    if (k.enabled) {
                        JsonObject q = new JsonObject();
                        q.addProperty("id", k.id);
                        q.addProperty("name", k.displayName);
                        q.addProperty("color", hex(k.color));
                        q.addProperty("icon", k.iconStack().getType().getKey().getKey());
                        q.addProperty("ranked", this.plugin.queues().size(k, true));
                        q.addProperty("unranked", this.plugin.queues().size(k, false));
                        q.addProperty("playing", this.plugin.matches().playing(k));
                        queues.add(q);
                    }
                }

                j.add("queues", queues);
                List<JsonObject> feed = new ArrayList<>();

                for (Database.RecentMatch r : this.recentCache) {
                    JsonObject f = new JsonObject();
                    Kit kx = this.plugin.kits().get(r.kit());
                    f.addProperty("type", "match");
                    f.addProperty("winner", r.winner() == null ? "?" : r.winner());
                    f.addProperty("loser", r.loser() == null ? "?" : r.loser());
                    f.addProperty("kit", kx == null ? r.kit() : kx.displayName);
                    f.addProperty("kitColor", kx == null ? "#FFB547" : hex(kx.color));
                    f.addProperty("score", r.score());
                    f.addProperty("delta", r.eloChange());
                    f.addProperty("ranked", r.ranked());
                    f.addProperty("at", r.endedAt());
                    feed.add(f);
                }

                for (LiveFeed.Event e : LiveFeed.recent()) {
                    JsonObject f = new JsonObject();
                    f.addProperty("type", e.type());
                    f.addProperty("player", e.player());
                    f.addProperty("tier", e.tier());
                    f.addProperty("tierColor", e.tierColor());
                    f.addProperty("kit", e.kit());
                    f.addProperty("at", e.at());
                    feed.add(f);
                }

                feed.sort((a, b) -> Long.compare(b.get("at").getAsLong(), a.get("at").getAsLong()));
                JsonArray fa = new JsonArray();
                feed.stream().limit(14L).forEach(fa::add);
                j.add("feed", fa);
                return j;
            });
            this.liveCache = o;
            this.liveAt = now;
            return o;
        }
    }

    private static JsonArray split(List<String> lines, String... keys) {
        JsonArray a = new JsonArray();

        for (String line : lines) {
            String[] parts = line.split("\\|", keys.length);
            JsonObject o = new JsonObject();

            for (int i = 0; i < keys.length; i++) {
                o.addProperty(keys[i], i < parts.length ? parts[i].trim() : "");
            }

            a.add(o);
        }

        return a;
    }

    private JsonArray kits() throws Exception {
        return this.sync(() -> {
            JsonArray a = new JsonArray();

            for (Kit k : this.plugin.kits().sorted()) {
                if (k.enabled && k.ranked) {
                    JsonObject o = new JsonObject();
                    o.addProperty("id", k.id);
                    o.addProperty("name", k.displayName);
                    o.addProperty("color", hex(k.color));
                    o.addProperty("icon", k.iconStack().getType().getKey().getKey());
                    a.add(o);
                }
            }

            return a;
        });
    }

    private JsonObject tier(int index) {
        Tier t = Tier.byIndex(index);
        JsonObject o = new JsonObject();
        o.addProperty("label", t == null ? "-" : t.label);
        o.addProperty("color", t == null ? "#888888" : t.color);
        o.addProperty("number", t == null ? 0 : t.number());
        o.addProperty("step", t == null ? 0 : t.step());
        o.addProperty("index", index);
        return o;
    }

    private void who(JsonObject o, UUID u) {
        Countries.Country c = this.plugin.countries() == null ? null : this.plugin.countries().of(u);
        o.addProperty("country", c == null ? "" : c.code());
        o.addProperty("countryName", c == null ? "" : c.name());
        o.addProperty("verified", this.plugin.welcome() != null && !this.plugin.welcome().badge(u).equals(Component.empty()));
        if (this.plugin.serverRanks() != null) {
            Ranks.Rank r = this.plugin.serverRanks().of(u);
            if (!r.isDefault()) {
                o.add("rank", this.rankJson(r, false));
            }
        }
    }

    private JsonArray board(String kit) throws Exception {
        List<Database.LbEntry> list = !"overall".equals(kit) && kit != null ? this.plugin.leaderboards().top(kit) : this.plugin.leaderboards().overall();
        return this.sync(() -> {
            JsonArray a = new JsonArray();
            int i = 0;

            for (Database.LbEntry e : list) {
                JsonObject o = new JsonObject();
                o.addProperty("pos", ++i);
                o.addProperty("uuid", e.uuid().toString());
                o.addProperty("name", e.name());
                o.addProperty("elo", e.elo());
                o.add("tier", this.tier(e.tier()));
                o.addProperty("wins", e.wins());
                o.addProperty("losses", e.losses());
                this.who(o, e.uuid());
                a.add(o);
            }

            return a;
        });
    }

    private JsonObject player(String name) throws Exception {
        if (name != null && name.matches("[A-Za-z0-9_]{1,16}")) {
            Object[] res = this.plugin.db().supply(c -> {
                UUID ux = this.plugin.db().findUuid(c, name);
                return ux == null ? null : new Object[]{ux, this.plugin.db().loadProfile(c, ux, name)};
            }).get(10L, TimeUnit.SECONDS);
            if (res == null) {
                JsonObject o = new JsonObject();
                o.addProperty("error", "No player called " + name + " has played here.");
                return o;
            } else {
                UUID u = (UUID)res[0];
                PvPProfile prof = (PvPProfile)res[1];
                return this.sync(() -> {
                    PvPProfile live = this.plugin.profiles().getIfLoaded(u);
                    PvPProfile p = live != null ? live : prof;
                    JsonObject o = new JsonObject();
                    o.addProperty("uuid", u.toString());
                    o.addProperty("name", p.name == null ? name : p.name);
                    o.addProperty("online", Bukkit.getPlayer(u) != null);
                    o.addProperty("ffaKills", p.ffaKills);
                    o.addProperty("ffaDeaths", p.ffaDeaths);
                    o.addProperty("firstJoin", p.firstJoin);
                    o.addProperty("lastSeen", p.lastSeen);
                    this.who(o, u);
                    JsonArray kits = new JsonArray();

                    for (Kit k : this.plugin.kits().sorted()) {
                        KitStats ks = p.statsOrNull(k.id);
                        if (ks != null && k.enabled) {
                            JsonObject kj = new JsonObject();
                            kj.addProperty("id", k.id);
                            kj.addProperty("name", k.displayName);
                            kj.addProperty("color", hex(k.color));
                            kj.addProperty("elo", ks.elo);
                            kj.addProperty("peak", ks.peakElo);
                            kj.add("tier", this.tier(ks.tier));
                            kj.addProperty("wins", ks.wins);
                            kj.addProperty("losses", ks.losses);
                            kj.addProperty("casualWins", ks.unrankedWins);
                            kj.addProperty("casualLosses", ks.unrankedLosses);
                            kj.addProperty("bestStreak", ks.bestStreak);
                            kj.addProperty("position", this.plugin.leaderboards().position(k.id, u));
                            kits.add(kj);
                        }
                    }

                    o.add("kits", kits);
                    return o;
                });
            }
        } else {
            JsonObject o = new JsonObject();
            o.addProperty("error", "Type a player name.");
            return o;
        }
    }

    private JsonObject rankJson(Ranks.Rank r, boolean full) {
        JsonObject o = new JsonObject();
        o.addProperty("id", r.id);
        o.addProperty("label", r.label);
        o.addProperty("from", r.from);
        o.addProperty("to", r.to);
        o.addProperty("nameColor", r.nameColor);
        o.addProperty("icon", r.icon == null ? "" : r.icon.replaceFirst("^(item|block)/", ""));
        o.addProperty("weight", r.weight);
        o.addProperty("staff", r.staff);
        o.addProperty("price", r.price);
        o.addProperty("description", r.description);
        JsonArray perks = new JsonArray();
        r.perks.forEach(perks::add);
        o.add("perks", perks);
        if (full) {
            o.addProperty("bold", r.bold);
            o.addProperty("inherit", r.inherit);
            JsonArray perms = new JsonArray();
            r.permissions.forEach(perms::add);
            o.add("permissions", perms);
            JsonArray all = new JsonArray();
            this.plugin.serverRanks().permissionsOf(r).forEach(all::add);
            o.add("allPermissions", all);
        }

        return o;
    }

    private JsonObject ranksPublic() {
        JsonObject o = new JsonObject();
        JsonArray store = new JsonArray();
        JsonArray staff = new JsonArray();
        if (this.plugin.serverRanks() != null) {
            for (Ranks.Rank r : this.plugin.serverRanks().all()) {
                if (!r.isDefault()) {
                    (r.staff ? staff : store).add(this.rankJson(r, false));
                }
            }
        }

        o.add("store", store);
        o.add("staff", staff);
        o.addProperty("storeLink", Cfg.s(Opt.STORE_LINK).isBlank() ? Cfg.s(Opt.LINK_DISCORD) : Cfg.s(Opt.STORE_LINK));
        return o;
    }

    private JsonArray staff() {
        JsonArray a = new JsonArray();
        if (this.plugin.serverRanks() == null) {
            return a;
        } else {
            for (Entry<UUID, String> e : this.plugin.serverRanks().assignments().entrySet()) {
                Ranks.Rank r = this.plugin.serverRanks().get(e.getValue());
                if (r != null && r.staff) {
                    String n = Bukkit.getOfflinePlayer(e.getKey()).getName();
                    JsonObject o = new JsonObject();
                    o.addProperty("uuid", e.getKey().toString());
                    o.addProperty("name", n == null ? "?" : n);
                    o.addProperty("online", Bukkit.getPlayer(e.getKey()) != null);
                    o.add("rank", this.rankJson(r, false));
                    a.add(o);
                }
            }

            return a;
        }
    }

    private static String randomToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    public String link(Player p) {
        String t = randomToken();
        this.tokens.put(t, new WebSite.Login(p.getUniqueId(), p.getName(), p.isOp(), System.currentTimeMillis() + 600000L));
        return this.plugin.webBase(p) + "/admin#t=" + t;
    }

    private WebServer.Response login(WebServer.Request r) {
        int[] f = this.failures.computeIfAbsent(r.ip(), k -> new int[2]);
        long now = System.currentTimeMillis();
        if (f[0] >= 8 && now / 60000L == f[1]) {
            return error(429, "Too many tries - wait a minute.");
        } else {
            String token;
            try {
                token = JsonParser.parseString(r.bodyText()).getAsJsonObject().get("token").getAsString();
            } catch (RuntimeException var9) {
                token = "";
            }

            WebSite.Login l = this.tokens.remove(token);
            if (l != null && l.until >= now) {
                String session = randomToken() + randomToken();
                this.sessions.put(session, new WebSite.Login(l.uuid, l.name, l.op, now + 7200000L));
                this.plugin.getLogger().info("Web admin login: " + l.name + " from " + r.ip());
                JsonObject o = new JsonObject();
                o.addProperty("name", l.name);
                return WebServer.Response.json(GSON.toJson((JsonElement)o))
                    .header("Set-Cookie", "vr_admin=" + session + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=7200");
            } else {
                if (now / 60000L != f[1]) {
                    f[0] = 0;
                    f[1] = (int)(now / 60000L);
                }

                f[0]++;
                return error(401, "This link has expired. Type /webadmin in game for a new one.");
            }
        }
    }

    private WebSite.Login session(WebServer.Request r) {
        String cookie = r.header("cookie");
        if (cookie == null) {
            return null;
        } else {
            for (String part : cookie.split(";")) {
                String p = part.trim();
                if (p.startsWith("vr_admin=")) {
                    WebSite.Login l = this.sessions.get(p.substring(9));
                    if (l != null && l.until > System.currentTimeMillis()) {
                        return l;
                    }
                }
            }

            return null;
        }
    }

    private WebServer.Response admin(WebServer.Request r) throws Exception {
        WebSite.Login l = this.session(r);
        if (l == null) {
            return error(401, "Not logged in. Type /webadmin in game.");
        } else if (r.method().equals("POST") && !"vortex".equals(r.header("x-requested-with"))) {
            return error(403, "Bad request.");
        } else {
            JsonObject body;
            try {
                body = r.method().equals("POST") && r.body().length > 0 ? JsonParser.parseString(r.bodyText()).getAsJsonObject() : new JsonObject();
            } catch (RuntimeException var10) {
                return error(400, "Bad JSON.");
            }

            String path = r.path().substring("/api/admin/".length());

            try {
                return switch (path) {
                    case "me" -> {
                        JsonObject o = new JsonObject();
                        o.addProperty("name", l.name);
                        o.addProperty("op", l.op);
                        yield json(o);
                    }
                    case "logout" -> {
                        this.sessions.values().removeIf(s -> s.uuid.equals(l.uuid));
                        yield json(new JsonObject()).header("Set-Cookie", "vr_admin=; Path=/; Max-Age=0");
                    }
                    case "status" -> json(this.sync(this::status));
                    case "players" -> json(this.sync(this::players));
                    case "player" -> json(this.sync(() -> this.playerAction(l, body)));
                    case "settings" -> json(this.sync(this::settings));
                    case "setting" -> json(this.sync(() -> this.setting(l, body)));
                    case "kits" -> json(this.sync(this::kitsAdmin));
                    case "kit" -> json(this.sync(() -> this.kit(l, body)));
                    case "ranks" -> json(this.sync(this::ranksAdmin));
                    case "rank" -> json(this.sync(() -> this.rank(l, body)));
                    case "rankset" -> json(this.sync(() -> this.rankSet(l, body)));
                    case "permissions" -> json(this.sync(this::permissionCatalog));
                    case "rankaccess" -> json(this.sync(() -> this.rankAccess(body)));
                    case "rankperm" -> json(this.sync(() -> this.rankPerm(l, body)));
                    case "rankmember" -> json(this.sync(() -> this.rankMember(l, body)));
                    case "backups" -> json(this.sync(this::backups));
                    case "backup" -> json(this.sync(() -> this.backup(l, body)));
                    case "restore" -> json(this.sync(() -> this.restore(l, body)));
                    case "reports" -> json(this.sync(this::reports));
                    case "broadcast" -> json(this.sync(() -> {
                        String msg = str(body, "message");
                        if (!msg.isBlank()) {
                            Bukkit.broadcast(Text.mm("<gold><b>" + Text.esc(Cfg.s(Opt.SERVER_NAME)) + "</b> <dark_gray>» <white>" + Text.esc(msg)));
                            this.log(l, "broadcast: " + msg);
                        }

                        return ok("Sent.");
                    }));
                    case "command" -> !l.op ? error(403, "Only operators can run console commands.") : json(this.sync(() -> {
                        String cmd = str(body, "command").replaceFirst("^/", "");
                        StringBuilder out = new StringBuilder();
                        CommandSender s = Bukkit.createCommandSender(c -> out.append(Text.plain(c)).append('\n'));
                        boolean found = !cmd.isBlank() && Bukkit.dispatchCommand(s, cmd);
                        this.log(l, "command: " + cmd);
                        JsonObject ox = new JsonObject();
                        ox.addProperty("ok", found);
                        ox.addProperty("output", out.length() == 0 ? (found ? "Done." : "Unknown command.") : out.toString());
                        return ox;
                    }));
                    case "ai" -> json(this.aiChat(l, body));
                    case "scoreboard" -> json(this.sync(() -> {
                        if (this.plugin.smpBoard() != null) {
                            this.plugin.smpBoard().applyDesign(str(body, "design").toUpperCase(Locale.ROOT));
                            this.log(l, "scoreboard design " + str(body, "design"));
                        }

                        return ok("Applied.");
                    }));
                    default -> error(404, "Unknown action.");
                };
            } catch (TimeoutException var8) {
                return error(503, "The server is busy, try again.");
            } catch (Exception var9) {
                this.plugin.getLogger().log(Level.WARNING, "Web admin action " + path + " failed", (Throwable)var9);
                return error(500, "That failed: " + var9.getMessage());
            }
        }
    }

    public JsonElement perform(String who, boolean op, String action, JsonObject body) throws Exception {
        WebSite.Login l = new WebSite.Login(new UUID(0L, 0L), "AI for " + who, op, Long.MAX_VALUE);

        return (JsonElement)(switch (action) {
            case "status" -> (JsonObject)this.sync(this::status);
            case "settings" -> (JsonArray)this.sync(this::settings);
            case "setting" -> (JsonObject)this.sync(() -> this.setting(l, body));
            case "kits" -> (JsonArray)this.sync(this::kitsAdmin);
            case "kit" -> (JsonObject)this.sync(() -> this.kit(l, body));
            case "ranks" -> (JsonArray)this.sync(this::ranksAdmin);
            case "rank" -> (JsonObject)this.sync(() -> this.rank(l, body));
            case "rankset" -> (JsonObject)this.sync(() -> this.rankSet(l, body));
            case "permissions" -> (JsonArray)this.sync(this::permissionCatalog);
            case "rankaccess" -> (JsonObject)this.sync(() -> this.rankAccess(body));
            case "rankperm" -> (JsonObject)this.sync(() -> this.rankPerm(l, body));
            case "rankmember" -> (JsonObject)this.sync(() -> this.rankMember(l, body));
            case "backup" -> (JsonObject)this.sync(() -> this.backup(l, body));
            case "backups" -> (JsonObject)this.sync(this::backups);
            case "restore" -> (JsonObject)this.sync(() -> this.restore(l, body));
            case "players" -> (JsonArray)this.sync(this::players);
            case "player" -> (JsonObject)this.sync(() -> this.playerAction(l, body));
            case "reports" -> (JsonArray)this.sync(this::reports);
            case "broadcast" -> (JsonObject)this.sync(() -> {
                String msg = str(body, "message");
                if (!msg.isBlank()) {
                    Bukkit.broadcast(Text.mm("<gold><b>" + Text.esc(Cfg.s(Opt.SERVER_NAME)) + "</b> <dark_gray>» <white>" + Text.esc(msg)));
                    this.log(l, "broadcast: " + msg);
                }

                return ok("Sent.");
            });
            case "scoreboard" -> (JsonObject)this.sync(() -> {
                if (this.plugin.smpBoard() != null) {
                    this.plugin.smpBoard().applyDesign(str(body, "design").toUpperCase(Locale.ROOT));
                    this.log(l, "scoreboard design " + str(body, "design"));
                }

                return ok("Applied.");
            });
            case "worlds" -> (JsonArray)this.sync(() -> {
                JsonArray a = new JsonArray();

                for (World w : Bukkit.getWorlds()) {
                    if (VanillaTerrain.isSourceWorld(w.getName())) {
                        continue;
                    }

                    JsonObject o = new JsonObject();
                    o.addProperty("name", w.getName());
                    o.addProperty("mode", this.plugin.modes() == null ? "?" : this.plugin.modes().mode(w).name());
                    o.addProperty("players", w.getPlayers().size());
                    a.add(o);
                }

                return a;
            });
            case "worldmode" -> (JsonObject)this.sync(() -> {
                World w = Bukkit.getWorld(str(body, "world"));
                if (w != null && this.plugin.modes() != null) {
                    WorldMode m = WorldMode.parse(str(body, "mode"), null);
                    if (m == null) {
                        return ok("Modes: PVP, HUB, SURVIVAL, LIFESTEAL, STRENGTH, RANKED, ECO, NONE.");
                    } else {
                        this.plugin.modes().setMode(w, m);
                        this.log(l, "world " + w.getName() + " mode " + m);
                        return ok("Saved.");
                    }
                } else {
                    return ok("No such world.");
                }
            });
            default -> ok("Unknown action.");
        });
    }

    private JsonObject aiChat(WebSite.Login l, JsonObject body) {
        JsonObject o = new JsonObject();
        if (this.plugin.ai() == null) {
            o.addProperty("reply", "The AI assistant isn't available.");
            return o;
        } else {
            String session = "web:" + l.uuid;
            if (body.has("reset") && body.get("reset").getAsBoolean()) {
                this.plugin.ai().reset(session);
                o.addProperty("reply", "New conversation.");
                return o;
            } else {
                String msg = str(body, "message").trim();
                if (msg.isEmpty()) {
                    o.addProperty("reply", "Type what you want to change.");
                    return o;
                } else {
                    if (msg.length() > 2000) {
                        msg = msg.substring(0, 2000);
                    }

                    AiAgent.Reply r = this.plugin.ai().chat(session, l.name, l.op, msg);
                    o.addProperty("reply", r.text());
                    o.addProperty("ok", r.ok());
                    JsonArray changes = new JsonArray();
                    r.changes().forEach(changes::add);
                    o.add("changes", changes);
                    o.addProperty("hasKey", this.plugin.ai().hasKey());
                    return o;
                }
            }
        }
    }

    private void log(WebSite.Login l, String what) {
        this.plugin.getLogger().info("[web admin] " + l.name + " - " + what);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static JsonObject ok(String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.addProperty("message", msg);
        return o;
    }

    private JsonObject status() {
        JsonObject o = new JsonObject();
        double[] tps = Bukkit.getTPS();
        o.addProperty("tps", Math.min(20.0, tps.length > 0 ? tps[0] : 20.0));
        o.addProperty("mspt", Bukkit.getAverageTickTime());
        Runtime rt = Runtime.getRuntime();
        o.addProperty("memUsed", (rt.totalMemory() - rt.freeMemory()) / 1048576L);
        o.addProperty("memMax", rt.maxMemory() / 1048576L);
        o.addProperty("uptime", (System.currentTimeMillis() - this.started) / 1000L);
        o.addProperty("online", Bukkit.getOnlinePlayers().size());
        o.addProperty("max", Bukkit.getMaxPlayers());
        o.addProperty("matches", this.plugin.matches().active().size());
        o.addProperty("queued", this.plugin.queues().total());
        o.addProperty("ffa", this.plugin.ffa().playing());
        o.addProperty("arenas", this.plugin.arenas().all().size());
        o.addProperty("kits", this.plugin.kits().all().size());
        o.addProperty("version", this.plugin.getPluginMeta().getVersion());
        o.addProperty("server", Bukkit.getName() + " " + Bukkit.getMinecraftVersion());
        o.addProperty("packReady", this.plugin.packs() != null && this.plugin.packs().ready());
        o.addProperty("reports", this.plugin.reports() == null ? 0 : this.plugin.reports().open().size());
        JsonArray failedParts = new JsonArray();
        this.plugin.failedParts().forEach((k, v) -> failedParts.add(k + " - " + v));
        o.add("failed", failedParts);
        return o;
    }

    private JsonArray players() {
        JsonArray a = new JsonArray();

        for (Player p : Bukkit.getOnlinePlayers()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.getName());
            o.addProperty("uuid", p.getUniqueId().toString());
            o.addProperty("state", this.plugin.sessions().state(p).name());
            o.addProperty("world", p.getWorld().getName());
            o.addProperty("ping", p.getPing());
            o.addProperty("pooled", this.plugin.hackerPool() != null && this.plugin.hackerPool().pooled(p));
            this.who(o, p.getUniqueId());
            a.add(o);
        }

        return a;
    }

    private JsonObject playerAction(WebSite.Login l, JsonObject body) {
        Player t = Bukkit.getPlayerExact(str(body, "name"));
        String action = str(body, "action");
        if (t == null) {
            return ok("That player isn't online.");
        } else {
            switch (action) {
                case "kick":
                    t.kick(Text.mm("<red>Kicked by " + Text.esc(l.name) + (str(body, "reason").isBlank() ? "" : ": " + Text.esc(str(body, "reason")))));
                    break;
                case "lobby":
                    this.plugin.forceLeave(t);
                    break;
                case "verify":
                    this.plugin.welcome().setVerified(t.getUniqueId(), true);
                    break;
                case "unverify":
                    this.plugin.welcome().setVerified(t.getUniqueId(), false);
                    break;
                case "heal":
                    t.setHealth(t.getAttribute(Attribute.MAX_HEALTH).getValue());
                    t.setFoodLevel(20);
                    break;
                default:
                    return ok("Unknown action.");
            }

            this.log(l, action + " " + t.getName());
            return ok("Done: " + action + " " + t.getName());
        }
    }

    private JsonArray settings() {
        JsonArray a = new JsonArray();

        for (Opt<?> o : Opt.all()) {
            if (o.gui) {
                JsonObject j = new JsonObject();
                j.addProperty("path", o.path);
                j.addProperty("section", o.section.title);
                j.addProperty("name", o.name);
                j.addProperty("desc", o.desc);
                j.addProperty("type", o.type.name());
                j.addProperty("min", o.min);
                j.addProperty("max", o.max);
                Object v = Cfg.get(o);
                if (o.path.equals("pack.external-url") || o.path.contains("key")) {
                    j.addProperty("secret", true);
                }

                j.add("value", GSON.toJsonTree(v));
                if (o.enumClass != null) {
                    JsonArray e = new JsonArray();

                    for (Enum<?> c : o.enumClass.getEnumConstants()) {
                        e.add(c.name());
                    }

                    j.add("options", e);
                }

                a.add(j);
            }
        }

        return a;
    }

    private JsonObject setting(WebSite.Login l, JsonObject body) {
        String path = str(body, "path");

        for (Opt<?> o : Opt.all()) {
            if (o.path.equals(path)) {
                Object value = switch (o.type) {
                    case BOOL -> body.get("value").getAsBoolean();
                    case INT -> body.get("value").getAsInt();
                    case DOUBLE -> body.get("value").getAsDouble();
                    case STRING_LIST, INT_LIST -> (List)GSON.fromJson(body.get("value"), List.class);
                    default -> body.get("value").getAsString();
                };
                Object stored = Cfg.set(o, value);
                this.log(l, "setting " + path + " = " + stored);
                JsonObject res = ok("Saved.");
                res.add("value", GSON.toJsonTree(stored));
                return res;
            }
        }

        return ok("Unknown setting.");
    }

    private JsonArray kitsAdmin() {
        JsonArray a = new JsonArray();

        for (Kit k : this.plugin.kits().sorted()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", k.id);
            o.addProperty("name", k.displayName);
            o.addProperty("color", hex(k.color));
            o.addProperty("icon", k.iconStack().getType().getKey().getKey());
            o.addProperty("category", k.category.name());
            o.addProperty("enabled", k.enabled);
            o.addProperty("ranked", k.ranked);
            o.addProperty("unranked", k.unranked);
            o.addProperty("ffa", k.ffa);
            o.addProperty("build", k.build);
            o.addProperty("rounds", k.rounds());
            o.addProperty("arenas", this.plugin.arenas().forKit(k).size());
            o.addProperty("playing", this.plugin.matches().playing(k));
            o.addProperty("queued", this.plugin.queues().size(k));
            a.add(o);
        }

        return a;
    }

    private JsonObject kit(WebSite.Login l, JsonObject body) {
        Kit k = this.plugin.kits().get(str(body, "id"));
        if (k == null) {
            return ok("Unknown kit.");
        } else {
            String field = str(body, "field");
            boolean v = body.has("value") && body.get("value").getAsBoolean();
            switch (field) {
                case "enabled":
                    k.enabled = v;
                    break;
                case "ranked":
                    k.ranked = v;
                    break;
                case "unranked":
                    k.unranked = v;
                    break;
                case "ffa":
                    k.ffa = v;
                    this.plugin.ffa().ensureArena(k);
                    break;
                default:
                    return ok("Unknown field.");
            }

            this.plugin.kits().save();
            this.log(l, "kit " + k.id + " " + field + " = " + v);
            return ok("Saved.");
        }
    }

    private JsonArray ranksAdmin() {
        JsonArray a = new JsonArray();
        if (this.plugin.serverRanks() != null) {
            for (Ranks.Rank r : this.plugin.serverRanks().all()) {
                a.add(this.rankJson(r, true));
            }
        }

        return a;
    }

    private JsonObject rank(WebSite.Login l, JsonObject body) {
        Ranks ranks = this.plugin.serverRanks();
        String id = str(body, "id").toLowerCase(Locale.ROOT);
        if (str(body, "field").equals("create")) {
            if (id.matches("[a-z0-9_-]{1,16}") && ranks.get(id) == null) {
                ranks.create(id);
                this.log(l, "rank create " + id);
                return ok("Created.");
            } else {
                return ok("Pick a new id (letters, numbers).");
            }
        } else {
            Ranks.Rank r = ranks.get(id);
            if (r == null) {
                return ok("Unknown rank.");
            } else {
                String field = str(body, "field");
                String value = str(body, "value");
                switch (field) {
                    case "label":
                        r.label = value;
                        break;
                    case "from":
                        r.from = value.matches("#[0-9A-Fa-f]{6}") ? value : r.from;
                        break;
                    case "to":
                        r.to = value.matches("#[0-9A-Fa-f]{6}") ? value : r.to;
                        break;
                    case "nameColor":
                        r.nameColor = value.matches("#[0-9A-Fa-f]{6}") ? value : r.nameColor;
                        break;
                    case "icon":
                        r.icon = value.isBlank() ? "" : (value.startsWith("glyph:") ? value : "item/" + value.replaceFirst("^(item|block)/", ""));
                        break;
                    case "weight":
                        r.weight = Integer.parseInt(value.trim());
                        break;
                    case "price":
                        r.price = value;
                        break;
                    case "description":
                        r.description = value;
                        break;
                    case "inherit":
                        r.inherit = value;
                        break;
                    case "staff":
                        r.staff = Boolean.parseBoolean(value);
                        break;
                    case "bold":
                        r.bold = Boolean.parseBoolean(value);
                        break;
                    case "permissions":
                    case "perks":
                        List<String> list = new ArrayList<>();

                        for (String s : value.split(field.equals("perks") ? "\n" : "[,\\s]+")) {
                            if (!s.isBlank()) {
                                list.add(s.trim());
                            }
                        }

                        if (field.equals("perks")) {
                            r.perks = list;
                        } else {
                            r.permissions = list;
                        }
                        break;
                    case "delete":
                        ranks.delete(id);
                        this.log(l, "rank delete " + id);
                        return ok("Deleted.");
                    default:
                        return ok("Unknown field.");
                }

                ranks.changed();
                this.log(l, "rank " + id + " " + field + " = " + value);
                return ok("Saved.");
            }
        }
    }

    private JsonArray permissionCatalog() {
        JsonArray groups = new JsonArray();
        RankAccess acc = this.plugin.serverRanks().access();

        for (String g : acc.groups()) {
            JsonObject go = new JsonObject();
            go.addProperty("name", g);
            JsonArray nodes = new JsonArray();

            for (RankAccess.Node n : acc.group(g)) {
                JsonObject no = new JsonObject();
                no.addProperty("node", n.node());
                no.addProperty("desc", n.description());
                no.addProperty("def", n.def().name());
                nodes.add(no);
            }

            go.add("nodes", nodes);
            groups.add(go);
        }

        return groups;
    }

    private JsonObject rankAccess(JsonObject body) {
        Ranks ranks = this.plugin.serverRanks();
        Ranks.Rank r = ranks.get(str(body, "id"));
        JsonObject o = new JsonObject();
        if (r == null) {
            o.addProperty("error", "Unknown rank.");
            return o;
        } else {
            RankAccess acc = ranks.access();
            JsonObject gives = new JsonObject();

            for (Entry<String, Boolean> e : acc.gives(r).entrySet()) {
                if (e.getValue()) {
                    gives.addProperty(e.getKey(), acc.source(r, e.getKey()));
                }
            }

            o.add("gives", gives);
            JsonArray denies = new JsonArray();
            r.permissions.stream().filter(s -> s.startsWith("-")).forEach(s -> denies.add(s.substring(1)));
            o.add("denies", denies);
            JsonArray members = new JsonArray();

            for (Ranks.Member m : ranks.members(r)) {
                JsonObject mo = new JsonObject();
                mo.addProperty("name", m.name());
                mo.addProperty("online", m.online());
                mo.addProperty("how", m.how());
                members.add(mo);
            }

            o.add("members", members);
            RankAccess.Access a = acc.access(r);
            o.addProperty("allCommands", a.allCommands());
            JsonArray cmds = new JsonArray();
            a.extraCommands().forEach(c -> cmds.add("/" + c.name()));
            o.add("commands", cmds);
            JsonArray perms = new JsonArray();

            for (RankAccess.Node n : a.extraPermissions()) {
                JsonObject no = new JsonObject();
                no.addProperty("node", n.node());
                no.addProperty("desc", n.description());
                no.addProperty("group", n.group());
                perms.add(no);
            }

            o.add("permissions", perms);
            JsonArray unknown = new JsonArray();
            a.unknownPermissions().forEach(unknown::add);
            o.add("unknown", unknown);
            return o;
        }
    }

    private JsonObject rankPerm(WebSite.Login l, JsonObject body) {
        Ranks ranks = this.plugin.serverRanks();
        Ranks.Rank r = ranks.get(str(body, "id"));
        String node = str(body, "node").trim();
        if (r != null && !node.isEmpty() && !node.contains(" ")) {
            Boolean value = body.has("value") && !body.get("value").isJsonNull() ? body.get("value").getAsBoolean() : null;
            ranks.access().set(r, node, value);
            ranks.changed();
            this.log(l, "rank " + r.id + " permission " + node + " -> " + (value == null ? "cleared" : (value ? "give" : "take away")));
            return ok(value == null ? "Cleared." : (value ? "Given." : "Taken away."));
        } else {
            return ok("Check the rank and the permission.");
        }
    }

    private JsonObject rankMember(WebSite.Login l, JsonObject body) {
        String name = str(body, "player");
        Ranks.Rank r = this.plugin.serverRanks().get(str(body, "id"));
        if (name.matches("[A-Za-z0-9_]{1,16}") && r != null) {
            OfflinePlayer t = Bukkit.getOfflinePlayer(name);
            boolean remove = body.has("remove") && body.get("remove").getAsBoolean();
            this.plugin.serverRanks().assign(t.getUniqueId(), remove ? null : r.id);
            this.log(l, "rank " + r.id + (remove ? " removed from " : " given to ") + name);
            return ok(remove ? name + " has the default rank again." : name + " is now " + r.id + ".");
        } else {
            return ok("Check the player name.");
        }
    }

    private JsonObject rankSet(WebSite.Login l, JsonObject body) {
        String name = str(body, "player");
        String id = str(body, "rank");
        if (name.matches("[A-Za-z0-9_]{1,16}") && this.plugin.serverRanks().get(id) != null) {
            OfflinePlayer t = Bukkit.getOfflinePlayer(name);
            this.plugin.serverRanks().assign(t.getUniqueId(), id);
            this.log(l, "rank set " + name + " " + id);
            return ok(name + " is now " + id + ".");
        } else {
            return ok("Check the player name and the rank.");
        }
    }

    private JsonObject backups() {
        JsonObject o = new JsonObject();
        ConfigBackup cb = this.plugin.configBackup();
        JsonArray kits = new JsonArray();
        JsonArray settings = new JsonArray();
        JsonArray full = new JsonArray();
        if (cb != null) {
            cb.files(ConfigBackup.Kind.FULL).forEach(full::add);
            cb.files(ConfigBackup.Kind.KITS).forEach(kits::add);
            cb.files(ConfigBackup.Kind.SETTINGS).forEach(settings::add);
        }

        JsonArray vault = new JsonArray();
        this.plugin.vault().files().forEach(vault::add);
        JsonArray ranks = new JsonArray();
        if (this.plugin.rankBackup() != null) {
            this.plugin.rankBackup().files().forEach(ranks::add);
        }

        o.add("kits", kits);
        o.add("settings", settings);
        o.add("full", full);
        o.add("vault", vault);
        o.add("ranks", ranks);
        return o;
    }

    private JsonObject backup(WebSite.Login l, JsonObject body) throws Exception {
        String kind = str(body, "kind");
        this.log(l, "backup " + kind);
        switch (kind) {
            case "kits":
                return ok("Saved " + this.plugin.configBackup().backup(ConfigBackup.Kind.KITS));
            case "settings":
                return ok("Saved " + this.plugin.configBackup().backup(ConfigBackup.Kind.SETTINGS));
            case "full":
                return ok("Saved " + this.plugin.configBackup().backup(ConfigBackup.Kind.FULL) + " (player tiers are added in a few seconds).");
            case "vault":
                this.plugin.vaultSnapshot("web", null);
                return ok("Full snapshot started (a few seconds).");
            case "ranks":
                if (this.plugin.rankBackup() != null) {
                    this.plugin.rankBackup().backup();
                }

                return ok("Rank backup started.");
            default:
                return ok("Unknown kind.");
        }
    }

    private JsonObject restore(WebSite.Login l, JsonObject body) throws Exception {
        String kind = str(body, "kind");
        String file = str(body, "file");
        this.log(l, "restore " + kind + " " + file);
        switch (kind) {
            case "kits":
                return ok("Restored " + this.plugin.configBackup().restore(ConfigBackup.Kind.KITS, file) + " file(s), kits reloaded.");
            case "settings":
                return ok("Restored " + this.plugin.configBackup().restore(ConfigBackup.Kind.SETTINGS, file) + " file(s), settings reloaded.");
            case "full":
                return ok(
                    "Restored "
                        + this.plugin.configBackup().restore(ConfigBackup.Kind.FULL, file)
                        + " file(s): settings, kits and ranks reloaded; player tiers are being put back."
                );
            case "vault":
                return ok(this.plugin.vault().scheduleRestore(file) ? "Restart the server to put this snapshot back." : "Couldn't schedule that snapshot.");
            default:
                return ok("Unknown kind.");
        }
    }

    private JsonArray reports() {
        JsonArray a = new JsonArray();
        if (this.plugin.reports() == null) {
            return a;
        } else {
            for (Reports.Report rep : this.plugin.reports().open()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", rep.id());
                o.addProperty("target", rep.targetName());
                o.addProperty("by", rep.byName());
                o.addProperty("reason", rep.reason());
                o.addProperty("at", rep.at());
                o.addProperty("where", rep.where());
                a.add(o);
            }

            return a;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = command.getName().equalsIgnoreCase("webadmin");
        if (!this.web.available() || !Cfg.bool(Opt.WEB_ENABLED)) {
            sender.sendMessage(Text.mm("<red>The website is off. <gray>Turn it on in Settings → Website & Resource Pack."));
            return true;
        } else if (sender instanceof Player p) {
            if (admin) {
                if (!p.hasPermission("vortexpvp.admin")) {
                    p.sendMessage(Text.mm("<red>You don't have permission."));
                    return true;
                }

                String url = this.link(p);
                p.sendMessage(Text.mm("<gold><b>Admin panel</b> <gray>- your private link (works once, 10 minutes):"));
                p.sendMessage(Text.mm("<aqua><u><click:open_url:'" + url + "'>Open the admin panel</click></u> <dark_gray>(don't share it)"));
            } else {
                String url = this.plugin.webBase(p) + "/";
                p.sendMessage(
                    Text.mm(
                        "<gold><b>"
                            + Text.esc(Cfg.s(Opt.SERVER_NAME))
                            + " website</b> <gray>- leaderboards, tier list, ranks: <aqua><u><click:open_url:'"
                            + url
                            + "'>"
                            + Text.esc(url)
                            + "</click></u>"
                    )
                );
            }

            return true;
        } else {
            sender.sendMessage(
                "Website "
                    + (this.web.onSamePort() ? "on the Minecraft port (http://<your server address>/)" : "on port " + this.web.port())
                    + ". Admins: use /webadmin in game for a login link."
            );
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    private record Login(UUID uuid, String name, boolean op, long until) {
    }
}
