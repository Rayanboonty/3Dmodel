package net.vortexrealm.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Session;
import net.vortexrealm.util.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 3.13.10 - while fighting, a player only gets the chunks of their own arena (plus one), so the other arena copies,
 * arcade plots and practice maps nearby are out of sight; back in the lobby the server's view distance returns.
 * It also means less to send and less for the server to keep loaded.
 */
public final class ArenaView {
    private final VortexRealm plugin;
    private final Map<UUID, Integer> set = new HashMap<>();

    public ArenaView(VortexRealm plugin) {
        this.plugin = plugin;
    }

    private static int chunksFor(Cuboid c) {
        if (c == null) {
            return -1;
        }

        int half = Math.max(c.sizeX(), c.sizeZ()) / 2;
        return Math.max(3, Math.min(12, (half + 15) / 16 + 1));
    }

    private int wanted(Player p) {
        if (!Cfg.bool(Opt.ARENA_VIEW_LIMIT)) {
            return -1;
        }

        Session s = this.plugin.sessions().get(p);
        if (s == null) {
            return -1;
        }

        if ((s.state == PlayerState.MATCH || s.state == PlayerState.PREMATCH) && s.match != null && s.match.instance() != null) {
            return chunksFor(s.match.instance().bounds());
        }

        if (s.state == PlayerState.FFA && s.ffa != null) {
            return chunksFor(s.ffa.bounds);
        }

        if (s.state == PlayerState.BOT && this.plugin.bots() != null && this.plugin.bots().fightOf(p) != null) {
            return 5;
        }

        if (this.plugin.plus() != null && this.plugin.plus().arcade() != null && this.plugin.plus().arcade().inGame(p.getUniqueId())) {
            // an arcade plot is about 90 blocks wide; the next plot starts further out
            return 4;
        }

        return -1;
    }

    /** Every second: players who started or stopped fighting get their view distance set or given back. */
    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasMetadata("NPC")) {
                continue;
            }

            int want = this.wanted(p);
            Integer now = this.set.get(p.getUniqueId());
            if (now == null && want == -1 || now != null && now == want) {
                continue;
            }

            try {
                p.setSendViewDistance(want);
                if (want == -1) {
                    this.set.remove(p.getUniqueId());
                } else {
                    this.set.put(p.getUniqueId(), want);
                }
            } catch (RuntimeException ignored) {
                // an old server without per-player view distance
            }
        }

        this.set.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
    }
}
