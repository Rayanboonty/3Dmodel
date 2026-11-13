package net.vortexpvp.plus;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.api.MatchEndEvent;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.match.MatchTeam;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Discord without any library, using Java's own HTTP client:
 * - webhook posts (match results, rank-ups, streaks, seasons, tournaments, clan wars);
 * - account linking: /link gives a 6-digit code, the player sends it in the link channel and the bot (a bot token)
 *   reads the channel through the REST API, links the account and answers;
 * - rank roles: the bot gives the Discord role of the player's overall rank (PUT/DELETE member role).
 * Everything is off until a webhook or a bot token is set in plus/config.yml.
 */
public final class Discord implements Listener {
    private final VortexPvP plugin;
    private final PlusModule plus;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Deque<String> queue = new ArrayDeque<>();
    private final Map<String, UUID> codes = new ConcurrentHashMap<>();
    private final Map<String, Long> codeTimes = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final AtomicInteger sent = new AtomicInteger();
    private volatile String lastStatus = "nothing sent yet";
    private volatile String lastMessageId = "";
    private boolean sending;

    public Discord(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
    }

    private String api() {
        return this.plus.config().s("discord.api", "https://discord.com/api/v10");
    }

    public boolean webhookOn() {
        return !this.plus.config().s("discord.webhook", "").isBlank();
    }

    public boolean botOn() {
        return !this.plus.config().s("discord.bot-token", "").isBlank();
    }

    public int sent() {
        return this.sent.get();
    }

    public String lastStatus() {
        return this.lastStatus;
    }

    /** Queues a webhook message (sent one at a time, about one a second, so Discord never rate-limits us). */
    public void post(String content) {
        if (!this.plus.config().on("discord") || !this.webhookOn() || content == null || content.isBlank()) {
            return;
        }

        synchronized (this.queue) {
            if (this.queue.size() < 50) {
                this.queue.add(content.length() > 1900 ? content.substring(0, 1900) + "..." : content);
            }
        }
    }

    /** Every second: sends the next queued message. */
    public void tick() {
        String next;
        synchronized (this.queue) {
            if (this.sending || this.queue.isEmpty()) {
                return;
            }

            next = this.queue.poll();
            this.sending = true;
        }

        JsonObject body = new JsonObject();
        body.addProperty("content", next);
        body.addProperty("username", this.plus.config().s("discord.name", "Vortex Realm"));
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        body.add("allowed_mentions", mentions);
        String url = this.plus.config().s("discord.webhook", "");
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        } catch (RuntimeException badUrl) {
            this.lastStatus = "bad webhook URL";
            synchronized (this.queue) {
                this.sending = false;
            }

            return;
        }

        this.http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
            if (err != null) {
                this.lastStatus = "error: " + err.getClass().getSimpleName();
            } else {
                this.lastStatus = "HTTP " + res.statusCode();
                if (res.statusCode() / 100 == 2) {
                    this.sent.incrementAndGet();
                }
            }

            synchronized (this.queue) {
                this.sending = false;
            }
        });
    }

    // ------------------------------------------------------------------ results to the webhook

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnd(MatchEndEvent e) {
        if (!e.match().ranked || e.winner() == null || "CANCELLED".equals(e.reason())) {
            return;
        }

        MatchTeam w = e.winner();
        MatchTeam l = e.match().otherTeam(w);
        if (this.plus.config().bool("discord.post-ranked-results", true) && l != null) {
            String kit = Text.plain(Text.mm(e.match().kit.displayName));
            this.post("⚔️ **" + names(w) + "** beat **" + names(l) + "** in ranked " + kit + " (" + e.match().durationSeconds() + "s)");
        }

        if (this.botOn()) {
            for (MatchTeam t : e.match().teams) {
                for (UUID u : t.members) {
                    Tasks.later(40L, () -> this.syncRoles(u));
                }
            }
        }
    }

    private static String names(MatchTeam t) {
        StringBuilder sb = new StringBuilder();
        for (UUID u : t.members) {
            String n = Bukkit.getOfflinePlayer(u).getName();
            sb.append(sb.length() == 0 ? "" : ", ").append(n == null ? "?" : n.replace("*", "").replace("_", "\\_"));
        }

        return sb.toString();
    }

    // ------------------------------------------------------------------ account linking

    public void link(Player p) {
        PlayerData d = this.plus.store().get(p);
        if (!this.botOn()) {
            p.sendMessage(Text.mm("<red>Discord linking is not set up on this server yet."));
            return;
        }

        if (d.discordLinked) {
            p.sendMessage(Text.mm("<gray>Your account is linked to Discord ID <white>" + d.discordId + "</white>. <click:run_command:'/link unlink'><red>[unlink]</red></click>"));
            return;
        }

        this.codes.values().removeIf(u -> u.equals(p.getUniqueId()));
        String code = String.format("%06d", this.random.nextInt(1000000));
        this.codes.put(code, p.getUniqueId());
        this.codeTimes.put(code, System.currentTimeMillis());
        p.sendMessage(Text.mm("<#5865F2><b>Discord</b> <gray>Send <white><b>" + code + "</b></white> in the <white>#"
            + Text.esc(this.plus.config().s("discord.link-channel-name", "link")) + "</white> channel within 10 minutes to link your account."));
    }

    public void unlink(Player p) {
        PlayerData d = this.plus.store().get(p);
        String old = d.discordId;
        d.discordId = "";
        d.discordLinked = false;
        d.dirty = true;
        p.sendMessage(Text.mm("<gray>Discord unlinked."));
        if (!old.isBlank()) {
            for (String role : this.plus.config().list("discord.roles", java.util.List.of())) {
                int i = role.indexOf(':');
                if (i > 0) {
                    this.role(old, role.substring(i + 1), false);
                }
            }
        }
    }

    /** Every 15 seconds: reads new messages in the link channel and links the codes it finds. */
    public void pollLinks() {
        String channel = this.plus.config().s("discord.link-channel", "");
        if (!this.botOn() || channel.isBlank() || this.codes.isEmpty()) {
            return;
        }

        this.codeTimes.entrySet().removeIf(en -> {
            boolean old = System.currentTimeMillis() - en.getValue() > 600000L;
            if (old) {
                this.codes.remove(en.getKey());
            }

            return old;
        });
        String url = this.api() + "/channels/" + channel + "/messages?limit=50" + (this.lastMessageId.isEmpty() ? "" : "&after=" + this.lastMessageId);
        this.bot("GET", url, null).thenAccept(res -> {
            if (res == null || res.statusCode() != 200) {
                return;
            }

            JsonElement root;
            try {
                root = JsonParser.parseString(res.body());
            } catch (RuntimeException bad) {
                return;
            }

            if (!root.isJsonArray()) {
                return;
            }

            for (JsonElement el : root.getAsJsonArray()) {
                JsonObject m = el.getAsJsonObject();
                String id = m.get("id").getAsString();
                if (this.lastMessageId.isEmpty() || id.compareTo(this.lastMessageId) > 0 && id.length() >= this.lastMessageId.length()) {
                    this.lastMessageId = id;
                }

                String content = m.has("content") ? m.get("content").getAsString().trim() : "";
                String author = m.getAsJsonObject("author").get("id").getAsString();
                for (String word : content.split("\\s+")) {
                    UUID u = this.codes.remove(word);
                    if (u != null) {
                        this.codeTimes.remove(word);
                        Tasks.sync(() -> this.linked(u, author, channel));
                    }
                }
            }
        });
    }

    private void linked(UUID u, String discordId, String channel) {
        PlayerData d = this.plus.store().get(u);
        d.discordId = discordId;
        d.discordLinked = true;
        d.dirty = true;
        String name = Bukkit.getOfflinePlayer(u).getName();
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            p.sendMessage(Text.mm("<#5865F2><b>Discord</b> <green>Linked! <gray>Your rank role will follow your ladder rank."));
        }

        this.plus.progression().title(u, "Linked");
        JsonObject body = new JsonObject();
        body.addProperty("content", "✅ <@" + discordId + "> is now linked to **" + (name == null ? "?" : name) + "**.");
        this.bot("POST", this.api() + "/channels/" + channel + "/messages", body.toString());
        this.syncRoles(u);
    }

    /** Gives the Discord role of the player's overall ladder rank and takes the other rank roles away. */
    public void syncRoles(UUID u) {
        PlayerData d = this.plus.store().ifLoaded(u);
        if (d == null || !d.discordLinked || !this.botOn() || this.plus.config().s("discord.guild", "").isBlank()) {
            return;
        }

        PvPProfile prof = this.plugin.profiles().getIfLoaded(u);
        if (prof == null) {
            return;
        }

        String rank = Ladder.RANKS[Ladder.rankOf(this.plus.ladder().overall(prof))];
        for (String entry : this.plus.config().list("discord.roles", java.util.List.of())) {
            int i = entry.indexOf(':');
            if (i > 0) {
                this.role(d.discordId, entry.substring(i + 1), entry.substring(0, i).equalsIgnoreCase(rank));
            }
        }
    }

    private void role(String member, String role, boolean give) {
        String guild = this.plus.config().s("discord.guild", "");
        if (guild.isBlank() || role.isBlank() || member.isBlank()) {
            return;
        }

        this.bot(give ? "PUT" : "DELETE", this.api() + "/guilds/" + guild + "/members/" + member + "/roles/" + role.trim(), null);
    }

    private java.util.concurrent.CompletableFuture<HttpResponse<String>> bot(String method, String url, String json) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bot " + this.plus.config().s("discord.bot-token", ""))
                .header("User-Agent", "DiscordBot (https://vortexrealm.net, 1.0)");
            if (json != null) {
                b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
            } else {
                b.method(method, method.equals("PUT") ? HttpRequest.BodyPublishers.ofString("") : HttpRequest.BodyPublishers.noBody());
            }

            return this.http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString()).exceptionally(err -> null);
        } catch (RuntimeException bad) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    public String selfCheck() {
        String c = this.codeFormatCheck();
        return c;
    }

    private String codeFormatCheck() {
        String code = String.format("%06d", 42);
        return code.equals("000042") ? null : "code format";
    }
}
