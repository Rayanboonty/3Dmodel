package net.vortexrealm.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Keeps the plugin up to date by itself. It reads an update manifest (JSON) from the configured sources - by default
 * the update.json published with the plugin on GitHub - and when it names a newer version for this plugin it downloads
 * the jar, checks its SHA-256, and puts it in the server's update folder under this jar's file name, so the server
 * swaps it in on the next start. Then it restarts the server at a good moment: as soon as nobody is online, or at
 * the night-time hour with a 5-minute countdown. A full backup is taken first; ranks, stats, kits and everything else
 * are carried over by the normal start-up of the new version.
 *
 * <pre>{ "VortexRealm": {"version": "3.4.0", "url": "https://.../VortexRealm-3.4.0.jar", "sha256": "...", "notes": "..."} }</pre>
 */
public final class AutoUpdater {
    /** What the updater needs from the plugin it updates. */
    public interface Host {
        boolean enabled();

        /** Manifest URLs, tried in order. */
        List<String> sources();

        int checkMinutes();

        /** EMPTY_OR_NIGHT, NOW or MANUAL. */
        String restartPolicy();

        /** Hour of the day (server time) for a night-time restart. */
        int restartHour();

        /** True while restarting now would spoil something (running matches). */
        boolean busy();

        /** A full backup, taken right before the restart. */
        void backup() throws Exception;

        boolean isAdmin(CommandSender s);
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private final Host host;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private volatile String staged;
    private volatile String stagedNotes = "";
    private volatile String lastCheck = "never";
    private volatile String lastError;
    private volatile boolean checking;
    private BukkitTask checkTask;
    private BukkitTask tickTask;
    private BukkitTask countdown;
    private long busySince;

    public AutoUpdater(JavaPlugin plugin, Host host) {
        this.plugin = plugin;
        this.host = host;
    }

    public void start() {
        this.stop();
        if (!this.host.enabled()) {
            return;
        }

        long every = Math.max(5, this.host.checkMinutes()) * 1200L;
        this.checkTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, () -> this.check(null), 600L, every);
        this.tickTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 600L, 600L);
    }

    public void stop() {
        for (BukkitTask t : new BukkitTask[]{this.checkTask, this.tickTask, this.countdown}) {
            if (t != null) {
                t.cancel();
            }
        }

        this.checkTask = null;
        this.tickTask = null;
        this.countdown = null;
    }

    public String staged() {
        return this.staged;
    }

    public List<String> status() {
        List<String> out = new ArrayList<>();
        out.add("<gold><b>Auto-update</b> <gray>" + (this.host.enabled() ? "<green>on" : "<red>off") + " <dark_gray>• <gray>this version <white>" + this.version());
        out.add("<gray>Last check: <white>" + this.lastCheck + (this.lastError == null ? "" : " <red>(" + MM.escapeTags(this.lastError) + ")"));
        out.add("<gray>Ready to install: <white>" + (this.staged == null ? "nothing" : this.staged + " <gray>(applies on the next restart)"));
        out.add("<gray>Restart: <white>" + this.host.restartPolicy() + " <gray>at <white>" + this.host.restartHour() + ":00 <gray>or when empty");
        for (String s : this.host.sources()) {
            out.add("<dark_gray>• " + MM.escapeTags(s));
        }

        return out;
    }

    private String version() {
        return this.plugin.getPluginMeta().getVersion();
    }

    /** Looks for a newer version; {@code who} (may be null) is told the result. Runs off the main thread. */
    public void check(CommandSender who) {
        if (this.checking) {
            this.tell(who, "<yellow>A check is already running.");
            return;
        }

        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.check(who));
            return;
        }

        this.checking = true;
        try {
            // 3.11.1: every source is asked and the newest version any of them offers wins (one source being
            // behind - or down - never hides an update the others have)
            String found = null;
            List<String> errors = new ArrayList<>();
            JsonObject best = null;
            int answered = 0;
            for (String src : this.host.sources()) {
                if (src.isBlank()) {
                    continue;
                }

                try {
                    JsonObject e = this.entryFrom(src.trim());
                    answered++;
                    if (e != null && (best == null || compare(e.get("version").getAsString(), best.get("version").getAsString()) > 0)) {
                        best = e;
                    }
                } catch (Exception e) {
                    errors.add(e.getMessage());
                }
            }

            if (best != null) {
                try {
                    found = this.install(best);
                } catch (Exception e) {
                    errors.add(e.getMessage());
                }
            }

            if (answered > 0 && found == null && best == null) {
                errors.clear();
            }

            this.lastCheck = LocalTime.now().withNano(0).toString();
            this.lastError = errors.isEmpty() ? null : String.join("; ", errors);
            if (found != null) {
                String v = found;
                this.plugin.getLogger().info("Update " + v + " downloaded and checked; it is installed on the next restart.");
                Bukkit.getScheduler().runTask(this.plugin, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (this.host.isAdmin(p)) {
                            p.sendMessage(MM.deserialize("<gold>⬆ <yellow>" + MM.escapeTags(this.plugin.getName()) + " " + MM.escapeTags(v)
                                + " <gray>is ready" + (this.stagedNotes.isBlank() ? "" : ": <white>" + MM.escapeTags(this.stagedNotes))
                                + "<gray>. It installs on the next restart (" + this.host.restartPolicy().toLowerCase(Locale.ROOT).replace('_', ' ') + ")."));
                        }
                    }
                });
                this.tell(who, "<green>Downloaded " + v + ". It installs on the next restart.");
            } else if (this.lastError != null) {
                this.tell(who, "<red>Couldn't check for updates: " + MM.escapeTags(this.lastError));
            } else {
                this.tell(who, this.staged != null ? "<green>" + this.staged + " is already downloaded and waiting for a restart." : "<green>You have the newest version (" + this.version() + ").");
            }
        } finally {
            this.checking = false;
        }
    }

    /** The entry for this plugin in a source's manifest when it is newer than what runs (and what is staged), else null. */
    private JsonObject entryFrom(String src) throws Exception {
        if (!src.startsWith("https://") && !src.startsWith("http://127.0.0.1") && !src.startsWith("http://localhost")) {
            throw new IllegalArgumentException("only https sources are used: " + src);
        }

        HttpResponse<String> res = this.http.send(HttpRequest.newBuilder(URI.create(src)).timeout(Duration.ofSeconds(20)).header("User-Agent", "VortexUpdater").GET().build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + res.statusCode() + " from " + URI.create(src).getHost());
        }

        JsonElement root = JsonParser.parseString(res.body());
        JsonElement entry = root.isJsonObject() ? root.getAsJsonObject().get(this.plugin.getName()) : null;
        if (entry == null || !entry.isJsonObject()) {
            return null;
        }

        JsonObject e = entry.getAsJsonObject();
        String v = e.has("version") ? e.get("version").getAsString() : null;
        String url = e.has("url") ? e.get("url").getAsString() : null;
        if (v == null || url == null || compare(v, this.version()) <= 0 || this.staged != null && compare(v, this.staged) <= 0) {
            return null;
        }

        return e;
    }

    /** Downloads the version an entry names, checks its SHA-256 and stages it for the next start. */
    private String install(JsonObject e) throws Exception {
        String v = e.get("version").getAsString();
        String url = e.get("url").getAsString();
        String sha = e.has("sha256") ? e.get("sha256").getAsString().trim().toLowerCase(Locale.ROOT) : "";

        if (sha.length() != 64) {
            throw new IllegalStateException("the manifest has no SHA-256 for " + v + ", so it isn't installed");
        }

        if (!url.startsWith("https://") && !url.startsWith("http://127.0.0.1") && !url.startsWith("http://localhost")) {
            throw new IllegalStateException("the download link isn't https");
        }

        File dir = Bukkit.getUpdateFolderFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalStateException("can't create " + dir);
        }

        File jar = this.jarFile();
        File part = new File(dir, "." + jar.getName() + ".part");
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        HttpResponse<InputStream> dl = this.http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3)).header("User-Agent", "VortexUpdater").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (dl.statusCode() != 200) {
            throw new IllegalStateException("download: HTTP " + dl.statusCode());
        }

        try (InputStream in = dl.body(); OutputStream out = Files.newOutputStream(part.toPath())) {
            byte[] buf = new byte[65536];
            int n;
            long total = 0L;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
                out.write(buf, 0, n);
                total += n;
                if (total > 200L * 1024L * 1024L) {
                    throw new IllegalStateException("download too big");
                }
            }
        }

        String got = HexFormat.of().formatHex(md.digest());
        if (!got.equals(sha)) {
            Files.deleteIfExists(part.toPath());
            throw new IllegalStateException("the download of " + v + " doesn't match its SHA-256, so it wasn't installed");
        }

        // the update folder swaps a jar of the same file name in on the next start
        Files.move(part.toPath(), new File(dir, jar.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        this.staged = v;
        this.stagedNotes = e.has("notes") ? e.get("notes").getAsString() : "";
        return v;
    }

    private File jarFile() throws Exception {
        return new File(this.plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    /** Compares dotted versions numerically: 3.10.0 is newer than 3.9.2. */
    public static int compare(String a, String b) {
        String[] x = a.split("[.\\-+]");
        String[] y = b.split("[.\\-+]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0;
            int q = i < y.length ? num(y[i]) : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }

        return 0;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Every 30 s: restart when the time is right and an update waits. */
    private void tick() {
        if (this.staged == null || this.countdown != null) {
            return;
        }

        String policy = this.host.restartPolicy().toUpperCase(Locale.ROOT);
        if (policy.equals("MANUAL")) {
            return;
        }

        // 3.13.6: a restart only happens by itself when the server comes back by itself (a restart script in
        // spigot.yml). Without one, a "restart" only stops the server and many hosts leave it off: then the update
        // waits in the update folder and goes in at the next normal restart.
        if (!policy.equals("NOW") && !comesBack()) {
            if (!this.waitLogged) {
                this.waitLogged = true;
                this.plugin.getLogger().info(this.plugin.getName() + " " + this.staged + " is downloaded and goes in at the next restart (no restart script, so the server is not stopped by itself).");
            }

            return;
        }

        // keep-online bots and other NPC players don't count
        if (Bukkit.getOnlinePlayers().stream().allMatch(p -> p.hasMetadata("NPC"))) {
            this.restart();
            return;
        }

        boolean night = LocalTime.now().getHour() == this.host.restartHour();
        if (policy.equals("NOW") || night) {
            if (this.host.busy()) {
                // let running matches finish, at most 30 minutes
                if (this.busySince == 0L) {
                    this.busySince = System.currentTimeMillis();
                }

                if (System.currentTimeMillis() - this.busySince < 1800000L) {
                    return;
                }
            }

            this.startCountdown(policy.equals("NOW") ? 60 : 300);
        }
    }

    private boolean waitLogged;

    /** True when spigot.yml's restart script exists, so a restart brings the server back. */
    static boolean comesBack() {
        try {
            String script = Bukkit.spigot().getConfig().getString("settings.restart-script", "./start.sh").trim();
            if (script.isEmpty()) {
                return false;
            }

            String first = script.split("\\s+")[0];
            return new java.io.File(first).isFile();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Restarts now if an update waits, else checks first and restarts when one comes in. */
    public void restartNow(CommandSender who) {
        if (this.staged != null) {
            this.startCountdown(30);
        } else {
            this.tell(who, "<yellow>Nothing downloaded yet - checking first.");
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                this.check(who);
                if (this.staged != null) {
                    Bukkit.getScheduler().runTask(this.plugin, () -> this.startCountdown(30));
                }
            });
        }
    }

    private void startCountdown(int seconds) {
        if (this.countdown != null) {
            return;
        }

        int[] left = {seconds};
        this.countdown = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            int s = left[0]--;
            if (s <= 0) {
                this.countdown.cancel();
                this.countdown = null;
                this.restart();
                return;
            }

            if (s == 300 || s == 240 || s == 180 || s == 120 || s == 60 || s == 30 || s == 15 || s <= 5) {
                String when = s >= 60 ? s / 60 + " minute" + (s >= 120 ? "s" : "") : s + " second" + (s == 1 ? "" : "s");
                Component msg = MM.deserialize("<gold>⬆ <yellow>The server restarts in <white>" + when + "</white> for an update <gray>- everything you have is kept.");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(msg);
                    if (s <= 5 || s == 60) {
                        p.showTitle(Title.title(MM.deserialize("<gold>Update"), MM.deserialize("<yellow>Restarting in " + when), Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, s <= 5 ? 1.6F : 1.0F);
                    }
                }
            }
        }, 0L, 20L);
    }

    private void restart() {
        String v = this.staged;
        this.plugin.getLogger().info("Restarting to install " + this.plugin.getName() + " " + v + " (a full backup is taken first).");
        try {
            this.host.backup();
        } catch (Exception e) {
            // without a backup the update waits: nothing is risked
            this.plugin.getLogger().log(Level.SEVERE, "The backup before the update failed - not restarting. The update stays ready.", e);
            return;
        }

        Component kick = MM.deserialize("<gold><b>Updating to " + MM.escapeTags(String.valueOf(v)) + "</b>\n<gray>The server is back in a minute.\n<gray>Everything you have is kept.");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.kick(kick);
        }

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> Bukkit.spigot().restart(), 40L);
    }

    private void tell(CommandSender who, String mm) {
        if (who != null) {
            Bukkit.getScheduler().runTask(this.plugin, () -> who.sendMessage(MM.deserialize(mm)));
        }
    }
}
