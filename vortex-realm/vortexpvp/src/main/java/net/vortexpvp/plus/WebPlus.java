package net.vortexpvp.plus;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.arena.Arena;
import net.vortexpvp.data.KitStats;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.smp.ModeManager;
import net.vortexpvp.util.Text;
import net.vortexpvp.web.WebServer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Website additions on the plugin's own web server: player profile pages (/p/&lt;name&gt;) with the ladder rank of every
 * kit, match history, replays, achievements, clan and season peak; JSON for the add-on systems (/api/plus/...);
 * the Match of the Week and weekly champions; and an admin dashboard (/plus/admin?key=...) with live TPS, players,
 * queues, matches, reports and shards, plus one-click arena rebuilds.
 */
public final class WebPlus {
    private static final Gson GSON = new Gson();
    private final VortexPvP plugin;
    private final PlusModule plus;

    public WebPlus(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
    }

    private String key() {
        String k = this.plus.config().s("web.admin-key", "");
        if (k.isBlank()) {
            byte[] b = new byte[18];
            new SecureRandom().nextBytes(b);
            k = java.util.HexFormat.of().formatHex(b);
            this.plus.config().set("web.admin-key", k);
        }

        return k;
    }

    public String adminLink() {
        return "/plus/admin?key=" + this.key();
    }

    private <T> T sync(Callable<T> c) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            return c.call();
        }

        return Bukkit.getScheduler().callSyncMethod(this.plugin, c).get(5L, TimeUnit.SECONDS);
    }

    private static WebServer.Response json(Object o) {
        return WebServer.Response.json(GSON.toJson(o));
    }

    private static String h(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public void install() {
        WebServer web = this.plugin.web();
        if (web == null) {
            return;
        }

        this.key();
        web.prefix("GET", "/p/", r -> {
            String name = r.path().substring(3);
            return WebServer.Response.html(this.sync(() -> this.profilePage(name)));
        });
        web.exact("GET", "/api/plus/player", r -> json(this.sync(() -> this.playerJson(r.param("name")))));
        web.exact("GET", "/api/plus/clans", r -> json(this.sync(this::clansJson)));
        web.exact("GET", "/api/plus/replays", r -> json(this.sync(this::replaysJson)));
        web.exact("GET", "/api/plus/weekly", r -> json(this.sync(this::weeklyJson)));
        web.exact("GET", "/plus/weekly", r -> WebServer.Response.html(this.sync(this::weeklyPage)));
        web.exact("GET", "/plus/admin", r -> {
            if (!this.key().equals(r.param("key"))) {
                return WebServer.Response.text(403, "text/plain", "Wrong or missing key. Get the link with /plus web in game.");
            }

            return WebServer.Response.html(this.adminPage());
        });
        web.exact("GET", "/api/plus/admin/stats", r -> {
            if (!this.key().equals(r.param("key"))) {
                return WebServer.Response.json(403, "{\"error\":\"key\"}");
            }

            return json(this.sync(this::adminStats));
        });
        web.exact("POST", "/api/plus/admin/rebuild", r -> {
            if (!this.key().equals(r.param("key"))) {
                return WebServer.Response.json(403, "{\"error\":\"key\"}");
            }

            String id = r.param("arena");
            return json(this.sync(() -> {
                Arena a = this.plugin.arenas().get(id);
                JsonObject o = new JsonObject();
                if (a == null) {
                    o.addProperty("ok", false);
                    o.addProperty("error", "no arena " + id);
                } else {
                    this.plugin.arenas().rebuild(a);
                    o.addProperty("ok", true);
                }

                return o;
            }));
        });
    }

    // ------------------------------------------------------------------ JSON

    private JsonObject playerJson(String name) {
        JsonObject o = new JsonObject();
        UUID u = name == null ? null : this.plus.store().uuidOf(name);
        if (u == null) {
            o.addProperty("error", "unknown player");
            return o;
        }

        PlayerData d = this.plus.store().get(u);
        o.addProperty("name", d.name);
        o.addProperty("level", Progression.level(d.xp));
        o.addProperty("xp", d.xp);
        o.addProperty("passLevel", Progression.passLevel(d.passXp));
        o.addProperty("achievements", d.achievements.size());
        o.addProperty("title", d.title);
        o.addProperty("clan", this.plus.clans() == null || d.clan.isEmpty() ? "" : this.plus.clans().name(d.clan));
        o.addProperty("loginStreak", d.loginStreak);
        o.addProperty("live", d.live);
        PvPProfile prof = this.plugin.profiles().getIfLoaded(u);
        if (prof != null) {
            JsonObject kits = new JsonObject();
            for (Kit k : this.plus.ladder().rankedKits()) {
                KitStats ks = prof.kits.get(k.id);
                if (ks != null) {
                    JsonObject x = new JsonObject();
                    x.addProperty("points", ks.elo);
                    x.addProperty("rank", Ladder.name(Ladder.step(ks.elo)));
                    x.addProperty("wins", ks.wins);
                    x.addProperty("losses", ks.losses);
                    kits.add(k.id, x);
                }
            }

            o.add("kits", kits);
            o.addProperty("overall", Ladder.name(this.plus.ladder().overall(prof)));
        }

        JsonArray hist = new JsonArray();
        for (String s : d.history) {
            hist.add(GSON.toJsonTree(Analysis.decode(s)));
        }

        o.add("history", hist);
        JsonArray reps = new JsonArray();
        if (this.plus.replays() != null) {
            for (String id : this.plus.replays().idsOf(u)) {
                reps.add(id);
            }
        }

        o.add("replays", reps);
        return o;
    }

    private JsonArray clansJson() {
        JsonArray a = new JsonArray();
        if (this.plus.clans() == null) {
            return a;
        }

        for (String id : this.plus.clans().ids()) {
            JsonObject c = new JsonObject();
            c.addProperty("tag", this.plus.clans().tag(id));
            c.addProperty("name", this.plus.clans().name(id));
            c.addProperty("elo", this.plus.clans().elo(id));
            c.addProperty("members", this.plus.clans().members(id).size());
            a.add(c);
        }

        return a;
    }

    private JsonArray replaysJson() {
        JsonArray a = new JsonArray();
        if (this.plus.replays() == null) {
            return a;
        }

        for (Map.Entry<String, ConfigurationSection> en : this.plus.replays().all().entrySet()) {
            JsonObject r = new JsonObject();
            ConfigurationSection s = en.getValue();
            r.addProperty("id", en.getKey());
            r.addProperty("kit", s.getString("kit"));
            r.addProperty("players", String.join(" vs ", s.getStringList("players")));
            r.addProperty("winner", s.getString("winner"));
            r.addProperty("seconds", s.getInt("secs"));
            r.addProperty("combo", s.getInt("combo"));
            r.addProperty("at", s.getLong("at"));
            a.add(r);
        }

        return a;
    }

    private JsonObject weeklyJson() {
        JsonObject o = new JsonObject();
        o.addProperty("season", this.plus.seasons().number());
        o.addProperty("seasonEnds", this.plus.seasons().end());
        String motw = this.plus.weekly() == null ? null : this.plus.weekly().motw();
        o.addProperty("motw", motw);
        if (motw != null && this.plus.replays() != null && this.plus.replays().info(motw) != null) {
            o.addProperty("motwPlayers", String.join(" vs ", this.plus.replays().info(motw).getStringList("players")));
        }

        JsonArray champs = new JsonArray();
        for (String c : this.plus.store().doc("weekly").getStringList("champions")) {
            champs.add(c);
        }

        o.add("champions", champs);
        return o;
    }

    private JsonObject adminStats() {
        JsonObject o = new JsonObject();
        double[] tps = Bukkit.getTPS();
        o.addProperty("tps", Math.round(Math.min(20.0, tps[0]) * 10.0) / 10.0);
        o.addProperty("players", Bukkit.getOnlinePlayers().size());
        o.addProperty("queue", this.plugin.sessions().count(PlayerState.QUEUE));
        o.addProperty("matches", this.plugin.matches().active().size());
        o.addProperty("ffa", this.plugin.ffa().playing());
        o.addProperty("reports", this.plugin.reports() == null ? 0 : this.plugin.reports().open().size());
        long shards = 0L;
        if (this.plugin.modes() != null) {
            for (ModeManager.Stats s : this.plugin.modes().allStats().values()) {
                shards += s.shards;
            }
        }

        o.addProperty("shards", shards);
        o.addProperty("memoryMb", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576L);
        JsonArray arenas = new JsonArray();
        for (Arena a : this.plugin.arenas().all()) {
            JsonObject x = new JsonObject();
            x.addProperty("id", a.id);
            x.addProperty("name", Text.plain(Text.mm(a.displayName == null ? a.id : a.displayName)));
            x.addProperty("enabled", a.enabled);
            arenas.add(x);
        }

        o.add("arenas", arenas);
        return o;
    }

    // ------------------------------------------------------------------ pages

    private static final String STYLE = "<style>:root{--bg:#0d0f1a;--card:#161a2b;--text:#e8ecff;--muted:#9aa3c7;--accent:#7B2FF7;--accent2:#2FD3F7}"
        + "@media (prefers-color-scheme: light){:root{--bg:#f4f6fb;--card:#fff;--text:#14182b;--muted:#5b6385}}"
        + "body{margin:0;background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,Segoe UI,sans-serif}"
        + ".wrap{max-width:980px;margin:0 auto;padding:24px 16px}h1{margin:0 0 4px;font-size:28px}"
        + ".muted{color:var(--muted)}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:12px;margin:16px 0}"
        + ".card{background:var(--card);border-radius:12px;padding:14px;box-shadow:0 1px 3px #0003}.big{font-size:22px;font-weight:700}"
        + "table{width:100%;border-collapse:collapse}td,th{padding:6px 8px;text-align:left;border-bottom:1px solid #8882}"
        + ".pill{display:inline-block;padding:2px 8px;border-radius:99px;background:linear-gradient(90deg,var(--accent),var(--accent2));color:#fff;font-size:12px}"
        + "button{background:var(--accent);color:#fff;border:0;border-radius:8px;padding:6px 10px;cursor:pointer}a{color:var(--accent2)}</style>";

    private String page(String title, String body) {
        return "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>" + h(title) + "</title>" + STYLE
            + "</head><body><div class=wrap>" + body + "<p class=muted style='margin-top:32px'>Vortex Realm • <a href='/'>home</a> • <a href='/plus/weekly'>weekly</a></p></div></body></html>";
    }

    private String profilePage(String rawName) {
        String name = java.net.URLDecoder.decode(rawName, java.nio.charset.StandardCharsets.UTF_8);
        UUID u = this.plus.store().uuidOf(name);
        if (u == null) {
            return this.page("Not found", "<h1>No player called " + h(name) + "</h1><p class=muted>They have to join the server once.</p>");
        }

        PlayerData d = this.plus.store().get(u);
        PvPProfile prof = this.plugin.profiles().getIfLoaded(u);
        StringBuilder b = new StringBuilder();
        b.append("<h1>").append(h(d.name)).append(" ").append(d.live.isEmpty() ? "" : "<span class=pill>● LIVE</span>").append("</h1>");
        b.append("<p class=muted>").append(d.title.isEmpty() ? "" : h(d.title) + " • ").append("Level ").append(Progression.level(d.xp))
            .append(d.clan.isEmpty() || this.plus.clans() == null ? "" : " • Clan " + h(this.plus.clans().name(d.clan))).append("</p>");
        b.append("<div class=grid>");
        if (prof != null) {
            b.append("<div class=card><div class=muted>Overall rank</div><div class=big>").append(h(Ladder.name(this.plus.ladder().overall(prof)))).append("</div></div>");
        }

        b.append("<div class=card><div class=muted>Battle pass</div><div class=big>").append(Progression.passLevel(d.passXp)).append("/").append(Progression.PASS_LEVELS).append("</div></div>");
        b.append("<div class=card><div class=muted>Achievements</div><div class=big>").append(d.achievements.size()).append("/").append(this.plus.progression().achievements().size()).append("</div></div>");
        b.append("<div class=card><div class=muted>Login streak</div><div class=big>").append(d.loginStreak).append(" days</div></div>");
        b.append("</div>");
        if (prof != null) {
            b.append("<div class=card><h3>Ladder</h3><table><tr><th>Kit</th><th>Rank</th><th>Points</th><th>W/L</th></tr>");
            for (Kit k : this.plus.ladder().rankedKits()) {
                KitStats ks = prof.kits.get(k.id);
                if (ks != null) {
                    b.append("<tr><td>").append(h(Text.plain(Text.mm(k.displayName)))).append("</td><td>").append(h(Ladder.name(Ladder.step(ks.elo)))).append("</td><td>")
                        .append(ks.elo).append("</td><td>").append(ks.wins).append("/").append(ks.losses).append("</td></tr>");
                }
            }

            b.append("</table></div>");
        } else {
            b.append("<p class=muted>Ranks show while the player is online or recently played.</p>");
        }

        b.append("<div class=card style='margin-top:12px'><h3>Recent matches</h3><table><tr><th></th><th>Kit</th><th>Hits</th><th>Acc</th><th>Combo</th><th>Replay</th></tr>");
        for (String s : d.history) {
            Map<String, String> m = Analysis.decode(s);
            b.append("<tr><td>").append("true".equals(m.get("won")) ? "✅" : "❌").append("</td><td>").append(h(m.get("kit"))).append("</td><td>").append(h(m.get("hits")))
                .append("</td><td>").append(h(m.get("acc"))).append("%</td><td>").append(h(m.get("combo"))).append("</td><td>")
                .append(m.get("replay") == null ? "-" : "<code>/replay " + h(m.get("replay")) + "</code>").append("</td></tr>");
        }

        b.append("</table></div>");
        return this.page(d.name + " - Vortex Realm", b.toString());
    }

    private String weeklyPage() {
        StringBuilder b = new StringBuilder("<h1>This week on Vortex Realm</h1>");
        b.append("<p class=muted>Season ").append(this.plus.seasons().number()).append(" ends in ").append(h(this.plus.seasons().timeLeft())).append("</p>");
        String motw = this.plus.weekly() == null ? null : this.plus.weekly().motw();
        b.append("<div class=card><h3>🎬 Match of the Week</h3>");
        if (motw != null && this.plus.replays() != null && this.plus.replays().info(motw) != null) {
            ConfigurationSection s = this.plus.replays().info(motw);
            b.append("<p>").append(h(String.join(" vs ", s.getStringList("players")))).append(" • ").append(h(s.getString("kit"))).append(" • best combo ").append(s.getInt("combo"))
                .append("</p><p>Watch it in game: <code>/replay ").append(h(motw)).append("</code></p>");
        } else {
            b.append("<p class=muted>Picked every Monday.</p>");
        }

        b.append("</div><div class=card style='margin-top:12px'><h3>🏆 Weekly champions</h3><table>");
        for (String c : this.plus.store().doc("weekly").getStringList("champions")) {
            String[] p = c.split("\\|");
            b.append("<tr><td>").append(h(p[0])).append("</td><td>").append(h(p.length > 1 ? p[1] : "")).append("</td><td>").append(h(p.length > 2 ? p[2] : "")).append("</td></tr>");
        }

        b.append("</table></div>");
        return this.page("Weekly - Vortex Realm", b.toString());
    }

    private String adminPage() {
        String k = this.key();
        String body = "<h1>Admin dashboard</h1><p class=muted>Live numbers, refreshed every 3 seconds.</p>"
            + "<div class=grid id=stats></div><div class=card><h3>Arenas</h3><table id=arenas></table></div>"
            + "<script>const K=" + GSON.toJson(k) + ";const L={tps:'TPS',players:'Players online',queue:'In queue',matches:'Matches',ffa:'In FFA',reports:'Open reports',shards:'Shards in circulation',memoryMb:'Memory (MB)'};"
            + "function esc(s){return String(s).replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]))}"
            + "async function load(){try{const r=await fetch('/api/plus/admin/stats?key='+K);const j=await r.json();"
            + "document.getElementById('stats').innerHTML=Object.keys(L).map(x=>'<div class=card><div class=muted>'+L[x]+'</div><div class=big>'+esc(j[x])+'</div></div>').join('');"
            + "document.getElementById('arenas').innerHTML='<tr><th>Arena</th><th>On</th><th></th></tr>'+j.arenas.map(a=>'<tr><td>'+esc(a.name)+'</td><td>'+(a.enabled?'✅':'⛔')+'</td><td><button onclick=\"rb(\\''+esc(a.id)+'\\')\">Rebuild</button></td></tr>').join('');"
            + "}catch(e){}}async function rb(id){await fetch('/api/plus/admin/rebuild?key='+K+'&arena='+encodeURIComponent(id),{method:'POST'});alert('Rebuilding '+id)}"
            + "load();setInterval(load,3000);</script>";
        return this.page("Admin - Vortex Realm", body);
    }

    public String selfCheck() {
        return h("<b>&").equals("&lt;b&gt;&amp;") ? null : "html escape";
    }
}
