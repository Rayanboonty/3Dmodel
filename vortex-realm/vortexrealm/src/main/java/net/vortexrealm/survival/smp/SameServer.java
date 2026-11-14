package net.vortexrealm.survival.smp;

import java.util.Locale;
import java.util.Set;
import net.vortexrealm.survival.VortexSMP;
import net.vortexrealm.survival.util.Snd;
import net.vortexrealm.survival.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Same-server mode rules: the SMP commands that touch the inventory work only inside the SMP worlds (every world
 * group has its own inventory), /spawn means the SMP spawn inside the SMP and the lobby elsewhere, and GUIs close
 * when a player leaves the SMP so nothing is carried between inventories.
 */
public final class SameServer implements Listener {
    /** VortexSMP commands that only work inside the SMP worlds. */
    private static final Set<String> SMP_ONLY = Set.of(
        "craft", "enderchest", "trash", "hat", "nightvision", "near", "trade", "market", "drill", "oldstuff"
    );
    private final VortexSMP plugin;

    public SameServer(VortexSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Realm realm = this.plugin.realm();
        if (!realm.on()) {
            return;
        }

        Player p = e.getPlayer();
        String msg = e.getMessage().startsWith("/") ? e.getMessage().substring(1) : e.getMessage();
        int sp = msg.indexOf(' ');
        String typed = (sp < 0 ? msg : msg.substring(0, sp)).toLowerCase(Locale.ROOT);
        String rest = sp < 0 ? "" : msg.substring(sp);
        String label = typed.substring(typed.indexOf(':') + 1);
        boolean inSmp = realm.smp(p.getWorld());
        if (label.equals("spawn") && !typed.contains(":")) {
            // both plugins have /spawn: the SMP spawn inside the SMP, the lobby everywhere else
            if (!inSmp) {
                e.setMessage("/hub" + rest);
            }

            return;
        }

        Command c = Bukkit.getCommandMap().getCommand(typed);
        if (c instanceof PluginCommand pc && pc.getPlugin() == this.plugin.host() && SMP_ONLY.contains(pc.getName().toLowerCase(Locale.ROOT)) && !inSmp
            && !(pc.getName().equalsIgnoreCase("drill") && p.hasPermission("vortexsmp.admin"))) {
            e.setCancelled(true);
            p.sendMessage(Text.mm("<red>That works in the SMP. <gray>Use <white>/smp</white> first."));
            Snd.error(p);
        }
    }

    /** Before leaving the SMP: menus close while the SMP inventory is still the live one. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Realm realm = this.plugin.realm();
        if (realm.on() && e.getTo() != null && e.getFrom().getWorld() != e.getTo().getWorld() && realm.smp(e.getFrom().getWorld()) && !realm.smp(e.getTo().getWorld())) {
            e.getPlayer().closeInventory();
        }
    }

    /** Leaving the SMP closes trades, the market and every other menu (their items stay with the SMP inventory). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Realm realm = this.plugin.realm();
        if (realm.on() && realm.smp(e.getFrom()) != realm.smp(e.getPlayer().getWorld())) {
            e.getPlayer().closeInventory();
        }
    }
}
