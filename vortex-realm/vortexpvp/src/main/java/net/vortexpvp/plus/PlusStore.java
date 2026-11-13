package net.vortexpvp.plus;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Where the add-on data lives. With SQLite (one server) it is plain YAML files under plugins/VortexPvP/plus; with
 * MySQL the same YAML goes into two tables, so every server of a network shares players, clans and seasons.
 */
public final class PlusStore implements Listener {
    private final VortexPvP plugin;
    private final File dir;
    private final File players;
    private final boolean sql;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> names = new ConcurrentHashMap<>();
    private final Map<String, YamlConfiguration> docs = new ConcurrentHashMap<>();

    public PlusStore(VortexPvP plugin, File dir) {
        this.plugin = plugin;
        this.dir = dir;
        this.players = new File(dir, "players");
        this.players.mkdirs();
        this.sql = plugin.db() != null && plugin.db().mysql();
        if (this.sql) {
            try {
                plugin.db().run(c -> {
                    try (Statement st = c.createStatement()) {
                        st.executeUpdate("CREATE TABLE IF NOT EXISTS vp_plus_players (uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(16), data MEDIUMTEXT)");
                        st.executeUpdate("CREATE TABLE IF NOT EXISTS vp_plus_docs (name VARCHAR(64) NOT NULL PRIMARY KEY, data MEDIUMTEXT)");
                    }
                }).join();
            } catch (RuntimeException var4) {
                plugin.getLogger().log(Level.WARNING, "VortexPvP: could not create the MySQL tables, using files", var4);
            }
        }

        YamlConfiguration n = this.doc("names");
        for (String k : n.getKeys(false)) {
            try {
                this.names.put(k, UUID.fromString(n.getString(k, "")));
            } catch (IllegalArgumentException var5) {
            }
        }
    }

    public boolean shared() {
        return this.sql;
    }

    public File dir() {
        return this.dir;
    }

    // ------------------------------------------------------------------ players

    public PlayerData get(Player p) {
        PlayerData d = this.get(p.getUniqueId());
        if (!p.getName().equals(d.name)) {
            d.name = p.getName();
            d.dirty = true;
        }

        return d;
    }

    /** The data of any player, loaded from storage when not cached (fine for one-off lookups). */
    public PlayerData get(UUID u) {
        return this.cache.computeIfAbsent(u, this::read);
    }

    public PlayerData ifLoaded(UUID u) {
        return this.cache.get(u);
    }

    public Collection<PlayerData> loaded() {
        return this.cache.values();
    }

    public UUID uuidOf(String name) {
        Player p = Bukkit.getPlayerExact(name);
        return p != null ? p.getUniqueId() : this.names.get(name.toLowerCase(Locale.ROOT));
    }

    /** Every player with stored data (files or rows), for season rewards and leaderboards. */
    public List<UUID> all() {
        List<UUID> out = new ArrayList<>(this.names.values());
        for (UUID u : this.cache.keySet()) {
            if (!out.contains(u)) {
                out.add(u);
            }
        }

        return out;
    }

    private PlayerData read(UUID u) {
        String text = null;
        if (this.sql) {
            try {
                text = this.plugin.db().supply(c -> {
                    try (PreparedStatement ps = c.prepareStatement("SELECT data FROM vp_plus_players WHERE uuid = ?")) {
                        ps.setString(1, u.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            return rs.next() ? rs.getString(1) : null;
                        }
                    }
                }).join();
            } catch (RuntimeException var5) {
                this.plugin.getLogger().log(Level.WARNING, "VortexPvP: could not read " + u, var5);
            }
        } else {
            File f = new File(this.players, u + ".yml");
            if (f.isFile()) {
                try {
                    text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                } catch (IOException var4) {
                }
            }
        }

        return PlayerData.parse(u, text);
    }

    private void write(PlayerData d) {
        String text = d.serialize();
        d.dirty = false;
        if (this.sql) {
            this.plugin.db().run(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                    "REPLACE INTO vp_plus_players (uuid, name, data) VALUES (?, ?, ?)")) {
                    ps.setString(1, d.uuid.toString());
                    ps.setString(2, d.name);
                    ps.setString(3, text);
                    ps.executeUpdate();
                }
            });
        } else {
            File f = new File(this.players, d.uuid + ".yml");
            Tasks.async(() -> {
                try {
                    Files.writeString(f.toPath(), text, StandardCharsets.UTF_8);
                } catch (IOException var3) {
                    this.plugin.getLogger().warning("VortexPvP: could not save " + f.getName());
                }
            });
        }
    }

    public void saveDirty() {
        for (PlayerData d : this.cache.values()) {
            if (d.dirty) {
                this.write(d);
            }
        }

        for (Map.Entry<String, YamlConfiguration> e : this.docs.entrySet()) {
            if (e.getValue().getBoolean("__dirty", false)) {
                e.getValue().set("__dirty", null);
                this.writeDoc(e.getKey(), e.getValue());
            }
        }
    }

    /** Writes everything now, on this thread (shutdown). */
    public void saveAllNow() {
        for (PlayerData d : this.cache.values()) {
            if (d.dirty) {
                d.dirty = false;
                if (!this.sql) {
                    try {
                        Files.writeString(new File(this.players, d.uuid + ".yml").toPath(), d.serialize(), StandardCharsets.UTF_8);
                    } catch (IOException var4) {
                    }
                } else {
                    this.write(d);
                }
            }
        }

        for (Map.Entry<String, YamlConfiguration> e : this.docs.entrySet()) {
            e.getValue().set("__dirty", null);
            this.writeDoc(e.getKey(), e.getValue());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            this.cache.computeIfAbsent(e.getUniqueId(), this::read);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = this.get(p);
        this.index(p.getName(), p.getUniqueId());
        d.dirty = true;
    }

    /** Remembers which uuid a name belongs to (for /ladder, /p/name, creator codes... while the player is offline). */
    public void index(String name, UUID u) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!u.equals(this.names.get(key))) {
            this.names.put(key, u);
            YamlConfiguration n = this.doc("names");
            n.set(key, u.toString());
            this.dirty("names");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        PlayerData d = this.cache.get(e.getPlayer().getUniqueId());
        if (d != null) {
            this.write(d);
            // keep it cached a moment (other systems still read it this tick), drop it later
            UUID u = d.uuid;
            Tasks.later(200L, () -> {
                if (Bukkit.getPlayer(u) == null) {
                    PlayerData gone = this.cache.remove(u);
                    if (gone != null && gone.dirty) {
                        this.write(gone);
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ shared documents (clans, seasons, boards...)

    public YamlConfiguration doc(String name) {
        return this.docs.computeIfAbsent(name, this::readDoc);
    }

    public void dirty(String name) {
        this.doc(name).set("__dirty", true);
    }

    private YamlConfiguration readDoc(String name) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            if (this.sql) {
                String text = this.plugin.db().supply(c -> {
                    try (PreparedStatement ps = c.prepareStatement("SELECT data FROM vp_plus_docs WHERE name = ?")) {
                        ps.setString(1, name);
                        try (ResultSet rs = ps.executeQuery()) {
                            return rs.next() ? rs.getString(1) : null;
                        }
                    }
                }).join();
                if (text != null) {
                    y.loadFromString(text);
                }
            } else {
                File f = new File(this.dir, name + ".yml");
                if (f.isFile()) {
                    y.load(f);
                }
            }
        } catch (Exception var5) {
            this.plugin.getLogger().log(Level.WARNING, "VortexPvP: could not read " + name, var5);
        }

        return y;
    }

    private void writeDoc(String name, YamlConfiguration y) {
        String text = y.saveToString();
        if (this.sql) {
            this.plugin.db().run(c -> {
                try (PreparedStatement ps = c.prepareStatement("REPLACE INTO vp_plus_docs (name, data) VALUES (?, ?)")) {
                    ps.setString(1, name);
                    ps.setString(2, text);
                    ps.executeUpdate();
                }
            });
        } else {
            try {
                Files.writeString(new File(this.dir, name + ".yml").toPath(), text, StandardCharsets.UTF_8);
            } catch (IOException var5) {
                this.plugin.getLogger().warning("VortexPvP: could not save " + name + ".yml");
            }
        }
    }

    /** For a fresh read of a shared document written by another server (MySQL). */
    public void forget(String name) {
        this.docs.remove(name);
    }
}
