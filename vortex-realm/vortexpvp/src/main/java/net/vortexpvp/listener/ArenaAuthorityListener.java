package net.vortexpvp.listener;

import net.vortexpvp.VortexPvP;
import net.vortexpvp.arena.ArenaInstance;
import net.vortexpvp.ffa.FfaArena;
import net.vortexpvp.match.Match;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Session;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public final class ArenaAuthorityListener implements Listener {
    private final VortexPvP plugin;

    public ArenaAuthorityListener(VortexPvP plugin) {
        this.plugin = plugin;
    }

    private boolean fightingAt(Player p, Location at) {
        Session s = this.plugin.sessions().get(p);
        if (s.state == PlayerState.MATCH) {
            Match m = s.match;
            ArenaInstance inst = m == null ? null : m.instance();
            return m != null && inst != null && m.state() == Match.State.FIGHTING && m.isAlive(p.getUniqueId()) && (at == null || inst.contains(at));
        } else if (s.state != PlayerState.FFA) {
            return false;
        } else {
            FfaArena a = s.ffa;
            return a != null && (at == null || a.contains(at));
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (this.fightingAt(p, e.getClickedBlock() == null ? null : e.getClickedBlock().getLocation())) {
            if (e.getItem() != null && e.useItemInHand() == Result.DENY) {
                e.setUseItemInHand(Result.ALLOW);
            }

            if (e.getClickedBlock() != null && e.useInteractedBlock() == Result.DENY && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                e.setUseInteractedBlock(Result.ALLOW);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onLaunch(ProjectileLaunchEvent e) {
        if (e.isCancelled() && e.getEntity().getShooter() instanceof Player p && this.fightingAt(p, null)) {
            e.setCancelled(false);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onBow(EntityShootBowEvent e) {
        if (e.isCancelled() && e.getEntity() instanceof Player p && this.fightingAt(p, null)) {
            e.setCancelled(false);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onConsume(PlayerItemConsumeEvent e) {
        if (e.isCancelled() && this.fightingAt(e.getPlayer(), null)) {
            e.setCancelled(false);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onSplash(PotionSplashEvent e) {
        if (e.isCancelled() && e.getEntity().getShooter() instanceof Player p && this.fightingAt(p, null)) {
            e.setCancelled(false);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onPearl(PlayerTeleportEvent e) {
        TeleportCause c = e.getCause();
        if (e.isCancelled() && (c == TeleportCause.ENDER_PEARL || c == TeleportCause.CONSUMABLE_EFFECT)) {
            Player p = e.getPlayer();
            Session s = this.plugin.sessions().get(p);
            boolean ok = false;
            if (s.state == PlayerState.MATCH
                && s.match != null
                && s.match.instance() != null
                && s.match.state() == Match.State.FIGHTING
                && s.match.isAlive(p.getUniqueId())) {
                ok = ConnectionListener.over(s.match.instance().bounds(), e.getTo());
            } else if (s.state == PlayerState.FFA && s.ffa != null) {
                ok = ConnectionListener.over(s.ffa.bounds, e.getTo());
            }

            if (ok) {
                e.setCancelled(false);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onExplode(EntityExplodeEvent e) {
        if (e.isCancelled()) {
            Location l = e.getLocation();
            ArenaInstance inst = this.plugin.arenas().instanceAt(l);
            boolean running = inst != null && inst.owner instanceof Match m && m.state() == Match.State.FIGHTING;
            if (running || this.plugin.ffa().arenaAt(l) != null) {
                e.setCancelled(false);
            }
        }
    }
}
