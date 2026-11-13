package net.practicepvp.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.practicepvp.PracticePlugin;
import net.practicepvp.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProfileManager implements Listener {
    private final JavaPlugin plugin;
    private final Database db;
    private final Map<UUID, PvPProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> loadedAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingLogin = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> connections = new ConcurrentHashMap<>();

    public ProfileManager(JavaPlugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == Result.ALLOWED) {
            UUID uuid = e.getUniqueId();
            String name = e.getName();
            this.pendingLogin.put(uuid, System.currentTimeMillis());
            PvPProfile current = this.profiles.get(uuid);
            if (current == null || !current.loadedOk) {
                PvPProfile loaded = this.loadBlocking(uuid, name);
                if (current == null) {
                    this.profiles.putIfAbsent(uuid, loaded);
                } else {
                    this.profiles.put(uuid, loaded);
                }
            }

            this.loadedAt.put(uuid, System.currentTimeMillis());
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onPreLoginResult(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != Result.ALLOWED) {
            UUID u = e.getUniqueId();
            this.pendingLogin.remove(u);
            if (this.connections.getOrDefault(u, 0) <= 0) {
                this.profiles.remove(u);
                this.loadedAt.remove(u);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onJoin(PlayerJoinEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        this.pendingLogin.remove(u);
        this.connections.merge(u, 1, Integer::sum);
        PvPProfile prof = this.profiles.get(u);
        if (prof == null || !prof.loadedOk) {
            String name = e.getPlayer().getName();
            this.db.<PvPProfile>supply(c -> this.db.loadProfile(c, u, name)).whenComplete((loaded, err) -> {
                if (loaded != null) {
                    Tasks.ensureSync(() -> {
                        Player p = Bukkit.getPlayer(u);
                        PvPProfile now = this.profiles.get(u);
                        if (p != null && (now == null || !now.loadedOk)) {
                            this.profiles.put(u, loaded);
                            this.loadedAt.put(u, System.currentTimeMillis());
                            PracticePlugin pl = PracticePlugin.get();
                            if (pl != null && pl.boards() != null) {
                                pl.boards().refreshNametag(p);
                            }
                        }
                    });
                }
            });
        }
    }

    private PvPProfile loadBlocking(UUID uuid, String name) {
        try {
            return this.db.<PvPProfile>supply(c -> this.db.loadProfile(c, uuid, name)).get(10L, TimeUnit.SECONDS);
        } catch (Exception var5) {
            this.plugin
                .getLogger()
                .log(Level.WARNING, "Could not load profile of " + name + "; using a temporary profile that will NOT be saved.", (Throwable)var5);
            PvPProfile p = new PvPProfile(uuid, name);
            p.loadedOk = false;
            return p;
        }
    }

    public void loadOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!this.profiles.containsKey(p.getUniqueId())) {
                this.profiles.put(p.getUniqueId(), this.loadBlocking(p.getUniqueId(), p.getName()));
                this.loadedAt.put(p.getUniqueId(), System.currentTimeMillis());
            }

            this.connections.put(p.getUniqueId(), 1);
        }
    }

    public PvPProfile get(Player p) {
        PvPProfile prof = this.profiles.get(p.getUniqueId());
        if (prof == null) {
            prof = new PvPProfile(p.getUniqueId(), p.getName());
            prof.loadedOk = false;
            this.profiles.put(p.getUniqueId(), prof);
        }

        return prof;
    }

    public PvPProfile getIfLoaded(UUID uuid) {
        return this.profiles.get(uuid);
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        int left = this.connections.merge(u, -1, Integer::sum);
        if (left <= 0) {
            this.connections.remove(u);
        }

        boolean keep = left > 0 || this.pendingLogin.containsKey(u);
        PvPProfile p = keep ? this.profiles.get(u) : this.profiles.remove(u);
        if (!keep) {
            this.loadedAt.remove(u);
        }

        if (p != null) {
            p.lastSeen = System.currentTimeMillis();
            p.name = e.getPlayer().getName();
            this.save(p);
        }
    }

    public void save(PvPProfile p) {
        if (p.loadedOk) {
            PvPProfile copy = p.copy();
            p.dirty = false;
            this.db.run(c -> this.db.saveProfile(c, copy));
        }
    }

    public void autosave() {
        long now = System.currentTimeMillis();
        List<UUID> stale = new ArrayList<>();

        for (Entry<UUID, PvPProfile> e : this.profiles.entrySet()) {
            Player online = Bukkit.getPlayer(e.getKey());
            if (online == null) {
                Long at = this.loadedAt.get(e.getKey());
                if (at != null && now - at > 60000L) {
                    stale.add(e.getKey());
                }
            } else if (e.getValue().dirty) {
                this.save(e.getValue());
            }
        }

        this.pendingLogin.values().removeIf(atx -> now - atx > 60000L);

        for (UUID u : stale) {
            if (!this.pendingLogin.containsKey(u)) {
                PvPProfile p = this.profiles.remove(u);
                this.loadedAt.remove(u);
                if (p != null && p.dirty) {
                    this.save(p);
                }
            }
        }
    }

    public void saveAllNow() {
        for (PvPProfile p : this.profiles.values()) {
            this.save(p);
        }
    }

    public Collection<PvPProfile> loaded() {
        return this.profiles.values();
    }
}
