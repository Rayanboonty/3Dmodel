package net.practicepvp.smp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.practicepvp.PracticePlugin;
import net.practicepvp.util.SafeSpot;
import net.practicepvp.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * 3.14.1: a player who gets stuck inside blocks right after flying with an elytra (or right after being pulled back
 * by a lag or anti-cheat correction) is moved to the nearest free spot instead of suffocating. Normal suffocation
 * (sand falling on you, standing in a wall on purpose) is not touched.
 */
public final class ElytraRescue implements Listener {
    private final PracticePlugin plugin;
    private final Map<UUID, Long> risky = new HashMap<>();

    public ElytraRescue(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean smp(Player p) {
        return this.plugin.modes() != null && this.plugin.modes().smpWorld(p.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p) {
            this.risky.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        // corrections from lag or anti-cheat are short teleports the player didn't ask for
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN && e.getFrom().getWorld() == e.getTo().getWorld()
            && e.getFrom().distanceSquared(e.getTo()) < 100) {
            this.risky.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSuffocate(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION || !(e.getEntity() instanceof Player p) || !this.smp(p)) {
            return;
        }

        Long at = this.risky.get(p.getUniqueId());
        if (!p.isGliding() && (at == null || System.currentTimeMillis() - at > 8000)) {
            return;
        }

        Location safe = SafeSpot.near(p.getLocation(), 6);
        if (safe == null) {
            return;
        }

        e.setCancelled(true);
        p.setGliding(false);
        p.teleport(safe, PlayerTeleportEvent.TeleportCause.PLUGIN);
        p.sendActionBar(Text.mm("<yellow>You got stuck in a wall, so you were moved to a safe spot."));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.risky.remove(e.getPlayer().getUniqueId());
    }
}
