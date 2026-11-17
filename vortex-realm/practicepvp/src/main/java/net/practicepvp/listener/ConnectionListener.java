package net.practicepvp.listener;

import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Msg;
import net.practicepvp.ffa.FfaArena;
import net.practicepvp.fx.Fx;
import net.practicepvp.match.Match;
import net.practicepvp.session.PlayerState;
import net.practicepvp.session.Session;
import net.practicepvp.util.Cuboid;
import net.practicepvp.util.Icons;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public final class ConnectionListener implements Listener {
    private final PracticePlugin plugin;

    public ConnectionListener(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(
        priority = EventPriority.NORMAL
    )
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        this.plugin.sessions().get(p);
        this.plugin.syncWorldState(p, true);
        this.plugin.lobby().refreshVisibilityAll();
    }

    @EventHandler(
        priority = EventPriority.LOW
    )
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        this.plugin.leavePvP(p, true);
        this.plugin.parties().handleQuit(p);
        this.plugin.duels().handleQuit(p);
        this.plugin.boards().detach(p);
        this.plugin.sessions().remove(p);
        Icons.forget(p.getUniqueId());
        Fx.forget(p);
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        boolean was = this.plugin.worlds().managed(e.getFrom());
        boolean now = this.plugin.worlds().managed(p.getWorld());
        if (was != now) {
            this.plugin.syncWorldState(p);
            this.plugin.lobby().refreshVisibilityAll();
        }
    }

    public static boolean over(Cuboid b, Location to) {
        return b != null && to != null && b.containsXZ(to.getX(), to.getZ()) && to.getY() >= b.minY() - 1;
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        Session s = this.plugin.sessions().get(p);
        if (!s.internalTeleport && s.state != PlayerState.OUTSIDE) {
            Location to = e.getTo();
            TeleportCause cause = e.getCause();
            boolean pearl = cause == TeleportCause.ENDER_PEARL || cause == TeleportCause.CONSUMABLE_EFFECT;
            switch (s.state) {
                case MATCH:
                    Match m = s.match;
                    boolean inside = m != null && m.instance() != null && (pearl ? over(m.instance().bounds(), to) : m.instance().contains(to));
                    if (pearl) {
                        if (!inside || m.state() != Match.State.FIGHTING) {
                            e.setCancelled(true);
                        }

                        return;
                    }

                    if (cause == TeleportCause.SPECTATE) {
                        if (!inside) {
                            e.setCancelled(true);
                        }

                        return;
                    }

                    if (!inside && !p.hasPermission("practice.admin")) {
                        e.setCancelled(true);
                        Msg.error(p, "match.no-teleport");
                    }
                    break;
                case FFA:
                    FfaArena a = s.ffa;
                    boolean insidex = a != null && (pearl ? over(a.bounds, to) : a.contains(to));
                    if (!insidex) {
                        e.setCancelled(true);
                        if (!pearl) {
                            Msg.error(p, "ffa.no-teleport");
                        }
                    }
                    break;
                case PREMATCH:
                    e.setCancelled(true);
                    break;
                case SPECTATING:
                    Match mx = s.spectating;
                    boolean insidexx = mx != null && mx.instance() != null && mx.instance().contains(to);
                    if (!insidexx && !p.hasPermission("practice.admin")) {
                        e.setCancelled(true);
                        if (cause != TeleportCause.SPECTATE) {
                            Msg.error(p, "spectate.no-teleport");
                        }
                    }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onRespawn(PlayerRespawnEvent e) {
        Location l = this.plugin.combat().takeRespawn(e.getPlayer());
        if (l != null) {
            e.setRespawnLocation(l);
        }
    }
}
