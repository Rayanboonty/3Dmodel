package net.vortexrealm.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class SessionManager {
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();

    public Session get(Player p) {
        return this.sessions.computeIfAbsent(p.getUniqueId(), u -> new Session(u, this.ids.incrementAndGet()));
    }

    public Session get(UUID uuid) {
        return this.sessions.get(uuid);
    }

    public Session remove(Player p) {
        return this.sessions.remove(p.getUniqueId());
    }

    public PlayerState state(Player p) {
        Session s = this.sessions.get(p.getUniqueId());
        return s == null ? PlayerState.OUTSIDE : s.state;
    }

    public Collection<Session> all() {
        return this.sessions.values();
    }

    public List<Player> playersIn(PlayerState state) {
        List<Player> out = new ArrayList<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.state(p) == state) {
                out.add(p);
            }
        }

        return out;
    }

    public int count(PlayerState state) {
        int n = 0;

        for (Session s : this.sessions.values()) {
            if (s.state == state) {
                n++;
            }
        }

        return n;
    }
}
