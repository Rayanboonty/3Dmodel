package net.practicepvp.plus;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.practicepvp.PracticePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * 3.13.15: a live "who is online" panel in a Discord channel, kept up to date by the server itself (no bot needed).
 * Set plus/config.yml -> discord.status-webhook to a webhook of the channel (Channel settings -> Integrations ->
 * Webhooks -> New Webhook -> Copy URL). The plugin posts one message and edits it every minute: online count, the
 * IP, and a card with the skin of each player online (up to 9; the rest are listed by name).
 */
public final class StatusPanel {
    private final PracticePlugin plugin;
    private final PlusModule plus;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final File file;
    private String messageId;
    private String lastHash = "";
    private boolean busy;
    private long lastSent;

    StatusPanel(PracticePlugin plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
        this.file = new File(plugin.getDataFolder(), "plus/status-panel.yml");
        this.messageId = YamlConfiguration.loadConfiguration(this.file).getString("message", null);
    }

    private String webhook() {
        return this.plus.config().s("discord.status-webhook", "").trim();
    }

    /** Called every 20 seconds; sends at most once a minute, and only when something changed (or every 5 min). */
    void tick() {
        String url = this.webhook();
        if (url.isEmpty() || this.busy) {
            return;
        }

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.removeIf(p -> p.hasMetadata("NPC") || p.hasMetadata("vortex-keepalive"));
        StringBuilder key = new StringBuilder().append(online.size());
        for (Player p : online) {
            key.append(',').append(p.getName());
        }

        long now = System.currentTimeMillis();
        if (now - this.lastSent < 60_000L || key.toString().equals(this.lastHash) && now - this.lastSent < 300_000L) {
            return;
        }

        this.lastHash = key.toString();
        this.lastSent = now;
        JsonObject body = this.payload(online);
        this.busy = true;
        this.send(url, body, this.messageId != null);
    }

    private JsonObject payload(List<Player> online) {
        String ip = this.plus.config().s("discord.status-ip", "vortexsmps.playwithbao.com");
        int max = Bukkit.getMaxPlayers();
        long ts = System.currentTimeMillis() / 1000L;
        JsonArray embeds = new JsonArray();
        JsonObject head = new JsonObject();
        head.addProperty("color", 0x3DBE4B);
        JsonObject author = new JsonObject();
        author.addProperty("name", "VORTEX REALM  •  LIVE SERVER STATUS");
        head.add("author", author);
        head.addProperty("title", "🟢  ONLINE  •  " + online.size() + " / " + max + " players");
        StringBuilder d = new StringBuilder();
        d.append("**IP:** `").append(ip).append("`\n**Version:** Java ").append(Bukkit.getMinecraftVersion()).append("\n\n");
        if (online.isEmpty()) {
            d.append("**Nobody online right now — be the first!**");
        } else {
            d.append("**Playing right now:**\n");
            List<String> names = new ArrayList<>();
            for (Player p : online) {
                names.add("`" + p.getName() + "`");
            }

            d.append(String.join("  ", names));
        }

        d.append("\n\n*Updated <t:").append(ts).append(":R>*");
        head.addProperty("description", d.toString());
        JsonObject foot = new JsonObject();
        foot.addProperty("text", "Live from the server • updates every minute");
        head.add("footer", foot);
        embeds.add(head);
        int n = 0;
        for (Player p : online) {
            if (n++ >= 9) {
                break;
            }

            JsonObject card = new JsonObject();
            card.addProperty("color", 0x8B5CF6);
            JsonObject a = new JsonObject();
            a.addProperty("name", p.getName());
            a.addProperty("icon_url", "https://mc-heads.net/avatar/" + p.getName() + "/64");
            card.add("author", a);
            JsonObject thumb = new JsonObject();
            thumb.addProperty("url", "https://mc-heads.net/body/" + p.getName() + "/110");
            card.add("thumbnail", thumb);
            card.addProperty("description", "🎮 In game • " + this.where(p));
            embeds.add(card);
        }

        JsonObject body = new JsonObject();
        body.addProperty("username", "Vortex Realm Status");
        body.add("embeds", embeds);
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        body.add("allowed_mentions", mentions);
        return body;
    }

    /** "SMP", "Lobby", "PvP" from the world the player is in (never coordinates). */
    private String where(Player p) {
        String w = p.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
        if (this.plugin.modes() != null && this.plugin.modes().smpWorld(p.getWorld())) {
            return "Survival SMP";
        }

        return w.contains("arena") || w.contains("pvp") || w.contains("ffa") ? "PvP" : "Lobby";
    }

    private void send(String url, JsonObject body, boolean edit) {
        String base = url.replaceAll("\\?.*$", "");
        String target = edit ? base + "/messages/" + this.messageId : base + "?wait=true";
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(target)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .method(edit ? "PATCH" : "POST", HttpRequest.BodyPublishers.ofString(body.toString())).build();
        } catch (RuntimeException bad) {
            this.busy = false;
            return;
        }

        this.http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
            try {
                if (err != null) {
                    return;
                }

                if (edit && res.statusCode() == 404) {
                    // the message was deleted: post a new one next time
                    this.messageId = null;
                    this.lastSent = 0L;
                    return;
                }

                if (!edit && res.statusCode() / 100 == 2) {
                    String id = JsonParser.parseString(res.body()).getAsJsonObject().get("id").getAsString();
                    this.messageId = id;
                    YamlConfiguration y = new YamlConfiguration();
                    y.set("message", id);
                    try {
                        y.save(this.file);
                    } catch (java.io.IOException ignored) {
                        // the panel is reposted after a restart
                    }
                }
            } catch (RuntimeException ignored) {
                // a bad answer from Discord: try again next minute
            } finally {
                this.busy = false;
            }
        });
    }

    /** Server stopping: show OFFLINE on the panel. */
    void offline() {
        String url = this.webhook();
        if (url.isEmpty() || this.messageId == null) {
            return;
        }

        JsonObject e = new JsonObject();
        e.addProperty("color", 0xE5484D);
        e.addProperty("title", "🔴  OFFLINE");
        e.addProperty("description", "The server is restarting or under maintenance. Check back in a few minutes.\n\n*Since <t:"
            + System.currentTimeMillis() / 1000L + ":R>*");
        JsonArray embeds = new JsonArray();
        embeds.add(e);
        JsonObject body = new JsonObject();
        body.add("embeds", embeds);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.replaceAll("\\?.*$", "") + "/messages/" + this.messageId))
                .timeout(Duration.ofSeconds(3)).header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString())).build();
            this.http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // shutting down anyway
        }
    }
}
