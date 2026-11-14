package net.practicepvp.survival.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.practicepvp.survival.VortexSMP;
import net.practicepvp.survival.config.Cfg;
import net.practicepvp.survival.config.Opt;
import net.practicepvp.survival.util.Snd;
import net.practicepvp.survival.util.Tasks;
import net.practicepvp.survival.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Links Minecraft players to their Discord account and counts how many members they invited, with a plain Discord
 * bot (REST only, no extra library): {@code /link} gives a code, the player posts it in the link channel, the bot
 * finds it and links the two. Invites are counted from the server's invite links (uses per inviter). The bot keeps
 * an invite leaderboard post up to date in the invites channel.
 */
public final class DiscordLink implements CommandExecutor {
    private static final String API = "https://discord.com/api/v10";
    private final VortexSMP plugin;
    private final File file;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final Map<String, UUID> pending = new ConcurrentHashMap<>();
    private final Map<UUID, String> codes = new ConcurrentHashMap<>();
    private final Map<String, Integer> invites = new ConcurrentHashMap<>();
    private final Map<String, String> names = new ConcurrentHashMap<>();
    private YamlConfiguration data;
    private String lastSeen;
    private BukkitTask task;
    private volatile String status = "not set up";
    private int ticks;

    public DiscordLink(VortexSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "discord.yml");
        this.data = YamlConfiguration.loadConfiguration(this.file);
        this.lastSeen = this.data.getString("last-message");
        ConfigurationSection inv = this.data.getConfigurationSection("invites");
        if (inv != null) {
            for (String k : inv.getKeys(false)) {
                this.invites.put(k, inv.getInt(k));
            }
        }
    }

    public void start() {
        this.task = Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin.host(), this::poll, 100L, 200L);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
        }

        this.save();
    }

    private boolean configured() {
        return !Cfg.s(Opt.DISCORD_TOKEN).isBlank() && !Cfg.s(Opt.DISCORD_GUILD).isBlank();
    }

    public String status() {
        return this.status;
    }

    private synchronized void save() {
        this.data.set("last-message", this.lastSeen);
        for (Map.Entry<String, Integer> e : this.invites.entrySet()) {
            this.data.set("invites." + e.getKey(), e.getValue());
        }

        try {
            this.data.save(this.file);
        } catch (IOException var3) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save discord.yml", (Throwable)var3);
        }
    }

    /** The Discord id linked to a player, or null. */
    public String discordOf(UUID u) {
        return this.data.getString("links." + u);
    }

    /** Members the player invited to the Discord (0 when not linked). */
    public int invitesOf(UUID u) {
        String d = this.discordOf(u);
        return d == null ? 0 : this.invites.getOrDefault(d, 0) + this.data.getInt("bonus." + u);
    }

    public synchronized void addBonus(UUID u, int n) {
        this.data.set("bonus." + u, this.data.getInt("bonus." + u) + n);
        this.save();
    }

    // ------------------------------------------------------------------ REST

    private JsonElement call(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + path))
            .timeout(Duration.ofSeconds(12))
            .header("Authorization", "Bot " + Cfg.s(Opt.DISCORD_TOKEN))
            .header("User-Agent", "DiscordBot (https://papermc.io, 1.0)");
        if (body != null) {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> r = this.http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 429) {
            return null;
        } else if (r.statusCode() >= 300) {
            throw new IOException("Discord answered " + r.statusCode() + " for " + path + ": " + r.body().substring(0, Math.min(160, r.body().length())));
        }

        return r.body().isBlank() ? null : JsonParser.parseString(r.body());
    }

    private void poll() {
        if (!this.configured()) {
            this.status = "not set up (discord.bot-token and discord.guild-id in config.yml)";
            return;
        }

        try {
            this.readLinks();
            if (this.ticks++ % 3 == 0) {
                this.countInvites();
                this.updatePost();
            }

            this.status = "connected - " + this.linkedCount() + " players linked, " + this.invites.size() + " inviters counted";
        } catch (IOException | RuntimeException var2) {
            this.status = "error: " + var2.getMessage();
            this.plugin.getLogger().log(Level.FINE, "Discord poll failed", (Throwable)var2);
        } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
        }
    }

    private int linkedCount() {
        ConfigurationSection s = this.data.getConfigurationSection("links");
        return s == null ? 0 : s.getKeys(false).size();
    }

    /** New messages in the link channel: a posted /link code links that Discord user. */
    private void readLinks() throws IOException, InterruptedException {
        String ch = Cfg.s(Opt.DISCORD_LINK_CHANNEL);
        if (ch.isBlank() || this.pending.isEmpty() && this.lastSeen != null) {
            return;
        }

        JsonElement e = this.call("GET", "/channels/" + ch + "/messages?limit=50" + (this.lastSeen == null ? "" : "&after=" + this.lastSeen), null);
        if (e == null || !e.isJsonArray()) {
            return;
        }

        JsonArray arr = e.getAsJsonArray();
        String newest = this.lastSeen;
        for (JsonElement m : arr) {
            JsonObject o = m.getAsJsonObject();
            String id = o.get("id").getAsString();
            if (newest == null || Long.parseUnsignedLong(id) > Long.parseUnsignedLong(newest)) {
                newest = id;
            }

            String content = o.has("content") ? o.get("content").getAsString().trim().toUpperCase(Locale.ROOT) : "";
            UUID u = this.pending.get(content);
            if (u != null) {
                JsonObject author = o.getAsJsonObject("author");
                String did = author.get("id").getAsString();
                String dname = author.has("username") ? author.get("username").getAsString() : did;
                this.link(u, did, dname);
                try {
                    this.call("PUT", "/channels/" + ch + "/messages/" + id + "/reactions/%E2%9C%85/@me", null);
                } catch (IOException var13) {
                }
            }
        }

        if (newest != null && !newest.equals(this.lastSeen)) {
            this.lastSeen = newest;
            this.save();
        }
    }

    private synchronized void link(UUID u, String did, String dname) {
        String code = this.codes.remove(u);
        if (code != null) {
            this.pending.remove(code);
        }

        // one Discord account belongs to one player
        ConfigurationSection s = this.data.getConfigurationSection("links");
        if (s != null) {
            for (String k : new ArrayList<>(s.getKeys(false))) {
                if (did.equals(s.getString(k))) {
                    this.data.set("links." + k, null);
                }
            }
        }

        this.data.set("links." + u, did);
        this.data.set("names." + did, dname);
        this.save();
        Tasks.sync(() -> {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.sendMessage(Text.mm("<#5865F2><b>Discord linked!</b> <gray>You are <white>" + Text.esc(dname) + "<gray>. Invites counted: <white>" + this.invitesOf(u)
                    + " <dark_gray>• <yellow>/drill</yellow> <gray>for your rewards"));
                Snd.play(p, "entity.player.levelup", 0.7F, 1.6F);
            }
        });
    }

    /** Uses of every invite link, added up per member who made it. */
    private void countInvites() throws IOException, InterruptedException {
        JsonElement e = this.call("GET", "/guilds/" + Cfg.s(Opt.DISCORD_GUILD) + "/invites", null);
        if (e == null || !e.isJsonArray()) {
            return;
        }

        Map<String, Integer> now = new HashMap<>();
        for (JsonElement i : e.getAsJsonArray()) {
            JsonObject o = i.getAsJsonObject();
            if (o.has("inviter") && !o.get("inviter").isJsonNull()) {
                JsonObject inv = o.getAsJsonObject("inviter");
                String id = inv.get("id").getAsString();
                now.merge(id, o.has("uses") ? o.get("uses").getAsInt() : 0, Integer::sum);
                if (inv.has("username")) {
                    this.names.put(id, inv.get("username").getAsString());
                }
            }
        }

        // an expired or deleted invite link must not take invites away: the highest count seen is kept
        for (Map.Entry<String, Integer> x : now.entrySet()) {
            this.invites.merge(x.getKey(), x.getValue(), Math::max);
        }

        this.save();
    }

    /** The invite leaderboard post in the invites channel (created once, then edited). */
    private void updatePost() throws IOException, InterruptedException {
        String ch = Cfg.s(Opt.DISCORD_INVITES_CHANNEL);
        if (ch.isBlank() || this.invites.isEmpty()) {
            return;
        }

        List<Map.Entry<String, Integer>> top = new ArrayList<>(this.invites.entrySet());
        top.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder("**🏆 Invite leaderboard**\\nInvite friends to earn drill pickaxes on the SMP: ");
        sb.append(Cfg.s(Opt.DRILL_TIERS).replace(":", " invites = ").replace(",", "x, ")).append("x.\\n\\n");
        for (int i = 0; i < Math.min(15, top.size()); i++) {
            String name = this.names.getOrDefault(top.get(i).getKey(), this.data.getString("names." + top.get(i).getKey(), "?"));
            sb.append(i + 1).append(". **").append(name.replace("\"", "").replace("\\", "")).append("** - ").append(top.get(i).getValue()).append(" invites\\n");
        }

        sb.append("\\nLink your account in game with `/link`, then claim with `/drill claim`.");
        String body = "{\"content\":\"" + sb + "\"}";
        String msg = this.data.getString("post-id");
        try {
            if (msg != null) {
                this.call("PATCH", "/channels/" + ch + "/messages/" + msg, body);
                return;
            }
        } catch (IOException var7) {
            // deleted: post a new one
        }

        JsonElement e = this.call("POST", "/channels/" + ch + "/messages", body);
        if (e != null && e.isJsonObject()) {
            synchronized (this) {
                this.data.set("post-id", e.getAsJsonObject().get("id").getAsString());
                this.save();
            }
        }
    }

    // ------------------------------------------------------------------ /link

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Discord: " + this.status);
            return true;
        }

        if (!this.configured()) {
            p.sendMessage(Text.mm("<red>Discord linking isn't set up on this server yet."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("unlink")) {
            this.data.set("links." + p.getUniqueId(), null);
            this.save();
            p.sendMessage(Text.mm("<gray>Your Discord account is unlinked."));
            return true;
        }

        String old = this.codes.remove(p.getUniqueId());
        if (old != null) {
            this.pending.remove(old);
        }

        String code = "VR-" + (1000 + ThreadLocalRandom.current().nextInt(9000));
        this.codes.put(p.getUniqueId(), code);
        this.pending.put(code, p.getUniqueId());
        String linked = this.discordOf(p.getUniqueId());
        p.sendMessage(Text.mm(" "));
        p.sendMessage(Text.mm("<#5865F2><b>Link your Discord</b>" + (linked != null ? " <gray>(linked to <white>" + Text.esc(this.data.getString("names." + linked, linked)) + "<gray>)" : "")));
        p.sendMessage(Text.mm("<gray>Post this code in the <white>#link</white> channel of our Discord: <click:copy_to_clipboard:" + code + "><hover:show_text:'Click to copy'><yellow><b>" + code + "</b></yellow></hover></click>"));
        p.sendMessage(Text.mm("<gray>It's checked every 10 seconds. Your invites then count for <yellow>/drill</yellow>."));
        p.sendMessage(Text.mm(" "));
        return true;
    }
}
