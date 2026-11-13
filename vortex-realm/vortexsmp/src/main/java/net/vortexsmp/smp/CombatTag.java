package net.vortexsmp.smp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.vortexsmp.VortexSMP;
import net.vortexsmp.config.Cfg;
import net.vortexsmp.config.Opt;
import net.vortexsmp.util.Snd;
import net.vortexsmp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class CombatTag implements Listener {
    private static final Set<String> ALLOWED = Set.of(
        "msg",
        "tell",
        "w",
        "whisper",
        "m",
        "t",
        "r",
        "reply",
        "pm",
        "dm",
        "pc",
        "partychat",
        "sc",
        "staffchat",
        "report",
        "helpop",
        "smpstats",
        "hearts",
        "bal",
        "balance",
        "ignore",
        "msgtoggle",
        "tpdeny",
        "tpacancel",
        "tpaccept",
        "tpyes",
        "tpno",
        "tournament",
        "event"
    );
    private final VortexSMP plugin;
    private final ModeManager modes;
    private final Map<UUID, Long> until = new HashMap<>();

    public CombatTag(VortexSMP plugin, ModeManager modes) {
        this.plugin = plugin;
        this.modes = modes;
    }

    private boolean smp(Player p) {
        return !this.plugin.worlds().managed(p.getWorld()) && this.modes.mode(p.getWorld()).smp();
    }

    public boolean tagged(Player p) {
        Long t = this.until.get(p.getUniqueId());
        return t != null && t > System.currentTimeMillis();
    }

    public int secondsLeft(Player p) {
        Long t = this.until.get(p.getUniqueId());
        return t == null ? 0 : (int)Math.max(0L, (t - System.currentTimeMillis() + 999L) / 1000L);
    }

    private void tag(Player p) {
        int seconds = Cfg.i(Opt.COMBAT_TAG);
        if (seconds > 0) {
            boolean was = this.tagged(p);
            this.until.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
            if (!was) {
                p.sendActionBar(Text.mm("<red><b>⚔ IN COMBAT</b> <gray>- don't log out (" + seconds + "s)"));
                Snd.play(p, "block.note_block.bass", 0.6F, 0.8F);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player victim) {
            Player attacker = e.getDamager() instanceof Player p
                ? p
                : (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player sh ? sh : null);
            if (attacker != null && !attacker.equals(victim) && this.smp(victim) && this.smp(attacker)) {
                this.tag(victim);
                this.tag(attacker);
            }
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Entry<UUID, Long>> it = this.until.entrySet().iterator();

        while (it.hasNext()) {
            Entry<UUID, Long> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                it.remove();
            } else if (e.getValue() <= now) {
                it.remove();
                p.sendActionBar(Text.mm("<green>You are no longer in combat."));
            } else {
                p.sendActionBar(Text.mm("<red><b>⚔ IN COMBAT</b> <gray>" + this.secondsLeft(p) + "s"));
            }
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST,
        ignoreCancelled = true
    )
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (this.tagged(p) && !p.hasPermission("vortexsmp.admin")) {
            String m = e.getMessage().startsWith("/") ? e.getMessage().substring(1) : e.getMessage();
            String label = m.split(" ")[0].toLowerCase(Locale.ROOT);
            int colon = label.indexOf(58);
            if (colon >= 0) {
                label = label.substring(colon + 1);
            }

            if (!ALLOWED.contains(label)) {
                e.setCancelled(true);
                p.sendMessage(Text.mm("<red>You can't use that in combat. <gray>(" + this.secondsLeft(p) + "s)"));
                Snd.error(p);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (this.tagged(p) && !p.hasPermission("vortexsmp.admin")) {
            switch (e.getCause()) {
                default:
                    e.setCancelled(true);
                    p.sendMessage(Text.mm("<red>You can't teleport in combat. <gray>(" + this.secondsLeft(p) + "s)"));
                case ENDER_PEARL:
                case CONSUMABLE_EFFECT:
                case NETHER_PORTAL:
                case END_PORTAL:
                case END_GATEWAY:
                case EXIT_BED:
                case DISMOUNT:
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onDeath(PlayerDeathEvent e) {
        this.until.remove(e.getEntity().getUniqueId());
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (this.tagged(p) && this.smp(p) && !p.hasPermission("vortexsmp.admin")) {
            this.until.remove(p.getUniqueId());
            p.setHealth(0.0);

            for (Player o : p.getWorld().getPlayers()) {
                o.sendMessage(Text.mm("<red>☠ <white>" + Text.esc(p.getName()) + " <gray>logged out in combat and died."));
            }
        } else {
            this.until.remove(p.getUniqueId());
        }
    }
}
