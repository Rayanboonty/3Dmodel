package net.vortexrealm.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.web.WebServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Keeps several region servers (USA, Europe, Asia...) the same server: one is the primary, the others (replicas) copy
 * its server ranks, kits and countries every minute through the primary's website, and send the ranks and countries
 * given on them to the primary. Every request is signed with the shared network key (HMAC-SHA256 with a timestamp),
 * so nobody else can read or change anything. Stats and ELO are shared by pointing every region at one MySQL database.
 */
public final class NetworkSync {
    /** The files the primary shares. */
    public static final List<String> FILES = List.of("ranks.yml", "kits.yml", "countries.yml");
    private final VortexRealm plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private String role = "off";
    private String primaryUrl = "";
    private String key = "";
    private int interval = 60;
    private BukkitTask task;
    private volatile String lastPull = "never";
    private volatile String lastError;

    public NetworkSync(VortexRealm plugin) {
        this.plugin = plugin;
    }

    public void configure(String role, String primaryUrl, String key, int interval) {
        this.role = role == null ? "off" : role.toLowerCase(Locale.ROOT);
        this.primaryUrl = primaryUrl == null ? "" : primaryUrl.replaceAll("/+$", "");
        this.key = key == null ? "" : key;
        this.interval = Math.max(15, interval);
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }

        if (this.replica() && !this.primaryUrl.isBlank() && !this.key.isBlank()) {
            this.task = Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, this::pull, 100L, this.interval * 20L);
        }
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
        }
    }

    public boolean primary() {
        return this.role.equals("primary");
    }

    public boolean replica() {
        return this.role.equals("replica");
    }

    public String status() {
        return "role " + this.role + (this.replica() ? ", primary " + this.primaryUrl + ", last copy " + this.lastPull + (this.lastError == null ? "" : " (" + this.lastError + ")") : "");
    }

    // ------------------------------------------------------------------ signing

    private String sign(String method, String path, long time, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(this.key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        return HexFormat.of().formatHex(mac.doFinal((method + "\n" + path + "\n" + time + "\n" + bodyHash).getBytes(StandardCharsets.UTF_8)));
    }

    private boolean verify(WebServer.Request r) {
        try {
            if (this.key.isBlank()) {
                return false;
            }

            long time = Long.parseLong(r.header("x-vortex-time"));
            if (Math.abs(System.currentTimeMillis() / 1000L - time) > 120L) {
                return false;
            }

            String want = this.sign(r.method(), r.path(), time, r.body());
            return MessageDigest.isEqual(want.getBytes(StandardCharsets.UTF_8), String.valueOf(r.header("x-vortex-sig")).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private HttpRequest.Builder signed(String method, String path, byte[] body) throws Exception {
        long time = System.currentTimeMillis() / 1000L;
        return HttpRequest.newBuilder(URI.create(this.primaryUrl + path))
            .timeout(Duration.ofSeconds(20))
            .header("X-Vortex-Time", String.valueOf(time))
            .header("X-Vortex-Sig", this.sign(method, path, time, body));
    }

    // ------------------------------------------------------------------ primary side (web routes)

    /** GET /api/net/files: the shared files, for replicas holding the key. */
    public WebServer.Response files(WebServer.Request r) throws Exception {
        if (!this.primary() || !this.verify(r)) {
            return WebServer.Response.json(403, "{\"error\":\"forbidden\"}");
        }

        JsonObject files = Bukkit.getScheduler().callSyncMethod(this.plugin, () -> {
            this.plugin.serverRanks().save();
            this.plugin.kits().save();
            this.plugin.countries().save();
            JsonObject o = new JsonObject();
            for (String name : FILES) {
                File f = new File(this.plugin.getDataFolder(), name);
                if (f.isFile()) {
                    o.addProperty(name, Files.readString(f.toPath(), StandardCharsets.UTF_8));
                }
            }

            return o;
        }).get(10L, java.util.concurrent.TimeUnit.SECONDS);
        JsonObject out = new JsonObject();
        out.add("files", files);
        return WebServer.Response.json(out.toString()).header("Cache-Control", "no-store");
    }

    /** POST /api/net/set: a rank or country given on a replica. */
    public WebServer.Response set(WebServer.Request r) throws Exception {
        if (!this.primary() || !this.verify(r)) {
            return WebServer.Response.json(403, "{\"error\":\"forbidden\"}");
        }

        JsonObject o = JsonParser.parseString(r.bodyText()).getAsJsonObject();
        UUID u = UUID.fromString(o.get("uuid").getAsString());
        String what = o.get("what").getAsString();
        String value = o.has("value") && !o.get("value").isJsonNull() ? o.get("value").getAsString() : null;
        Bukkit.getScheduler().callSyncMethod(this.plugin, () -> {
            if (what.equals("rank")) {
                this.plugin.serverRanks().assign(u, value);
            } else if (what.equals("country")) {
                this.plugin.countries().setStored(u, value);
            }

            return null;
        }).get(10L, java.util.concurrent.TimeUnit.SECONDS);
        return WebServer.Response.json("{\"ok\":true}");
    }

    // ------------------------------------------------------------------ replica side

    /** Copies the primary's files and reloads what changed. */
    private void pull() {
        try {
            HttpResponse<String> res = this.http.send(this.signed("GET", "/api/net/files", new byte[0]).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + res.statusCode() + (res.statusCode() == 403 ? " (is the network key the same as the primary's?)" : ""));
            }

            JsonObject files = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("files");
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                for (String name : FILES) {
                    if (!files.has(name)) {
                        continue;
                    }

                    String content = files.get(name).getAsString();
                    File f = new File(this.plugin.getDataFolder(), name);
                    try {
                        String old = f.isFile() ? Files.readString(f.toPath(), StandardCharsets.UTF_8) : "";
                        if (old.equals(content)) {
                            continue;
                        }

                        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
                        this.reload(name);
                        this.plugin.getLogger().info("Network sync: " + name + " updated from the primary.");
                    } catch (Exception e) {
                        this.plugin.getLogger().log(Level.WARNING, "Network sync: couldn't apply " + name, e);
                    }
                }
            });
            this.lastPull = java.time.LocalTime.now().withNano(0).toString();
            this.lastError = null;
        } catch (Exception e) {
            this.lastError = e.getMessage();
        }
    }

    private void reload(String name) {
        switch (name) {
            case "ranks.yml" -> {
                this.plugin.serverRanks().load();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    this.plugin.serverRanks().apply(p);
                }
            }
            case "kits.yml" -> {
                this.plugin.kits().load();
                this.plugin.lobby().refreshVisibilityAll();
            }
            case "countries.yml" -> this.plugin.countries().load();
            default -> {
            }
        }
    }

    /** On a replica: a rank or country given here is also given on the primary. */
    public void forward(String what, UUID u, String value) {
        if (!this.replica() || this.primaryUrl.isBlank() || this.key.isBlank()) {
            return;
        }

        JsonObject o = new JsonObject();
        o.addProperty("what", what);
        o.addProperty("uuid", u.toString());
        o.addProperty("value", value);
        byte[] body = o.toString().getBytes(StandardCharsets.UTF_8);
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                HttpResponse<String> res = this.http.send(
                    this.signed("POST", "/api/net/set", body).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                    HttpResponse.BodyHandlers.ofString()
                );
                if (res.statusCode() != 200) {
                    this.plugin.getLogger().warning("Network sync: the primary refused the " + what + " change (HTTP " + res.statusCode() + ").");
                }
            } catch (Exception e) {
                this.plugin.getLogger().warning("Network sync: couldn't send the " + what + " change to the primary: " + e.getMessage());
            }
        });
    }
}
