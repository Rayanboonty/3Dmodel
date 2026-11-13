package net.vortexrealm.listener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Msg;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Session;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class CommandGuardListener implements Listener {
    private static final Set<String> DUEL_ALLOWED = Set.of(
        "msg", "tell", "w", "whisper", "m", "t", "r", "reply", "pc", "partychat", "ping", "sc", "staffchat", "report", "helpop"
    );
    private static final Set<String> STAFF_ALLOWED = Set.of(
        "pvpadmin", "pa", "practiceadmin", "staff", "kick", "ban", "mute", "tempban", "stop", "restart", "lp", "luckperms"
    );
    private static final Set<String> LEAVE = Set.of(
        "lobby", "hub", "leave", "spawn", "leavepvp", "smp", "survival", "l", "practice", "worlds", "selector", "servers"
    );
    private static final int FFA_SECONDS = 3;
    private final VortexRealm plugin;
    private final Map<UUID, CommandGuardListener.Pending> pending = new HashMap<>();

    public CommandGuardListener(VortexRealm plugin) {
        this.plugin = plugin;
    }

    private static String label(String message) {
        String m = message.startsWith("/") ? message.substring(1) : message;
        int sp = m.indexOf(32);
        String l = (sp < 0 ? m : m.substring(0, sp)).toLowerCase(Locale.ROOT);
        int colon = l.indexOf(58);
        return colon >= 0 ? l.substring(colon + 1) : l;
    }

    @EventHandler(
        priority = EventPriority.LOWEST,
        ignoreCancelled = true
    )
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        Session s = this.plugin.sessions().get(p);
        String label = label(e.getMessage());
        switch (s.state) {
            case PREMATCH:
            case MATCH:
                if (DUEL_ALLOWED.contains(label)) {
                    return;
                }

                if (STAFF_ALLOWED.contains(label) && p.hasPermission("vortex.staff")) {
                    return;
                }

                e.setCancelled(true);
                p.sendMessage(Text.mm("<red>You can't use commands during a duel. <gray>Finish the fight!"));
                Snd.error(p);
                break;
            case FFA:
                if (!LEAVE.contains(label)) {
                    return;
                }

                e.setCancelled(true);
                this.startLeave(p, e.getMessage());
        }
    }

    private void startLeave(Player p, String command) {
        if (this.pending.containsKey(p.getUniqueId())) {
            p.sendMessage(Text.mm("<yellow>You are already leaving..."));
        } else {
            Session s = this.plugin.sessions().get(p);
            if (s.inCombat()) {
                Msg.error(p, "ffa.in-combat", "seconds", s.combatSecondsLeft());
            } else {
                int[] left = new int[]{3};
                p.sendActionBar(Text.mm("<yellow>Leaving in <gold><b>" + left[0] + "</b></gold><yellow>... don't move!"));
                Snd.play(p, "block.note_block.hat", 0.8F, 1.2F);
                BukkitTask task = Tasks.timer(20L, 20L, () -> {
                    left[0]--;
                    if (!p.isOnline() || this.plugin.sessions().get(p).state != PlayerState.FFA) {
                        this.cancel(p, null);
                    } else if (left[0] > 0) {
                        p.sendActionBar(Text.mm("<yellow>Leaving in <gold><b>" + left[0] + "</b></gold><yellow>..."));
                        Snd.play(p, "block.note_block.hat", 0.8F, 1.2F + (3 - left[0]) * 0.2F);
                    } else {
                        CommandGuardListener.Pending pd = this.pending.remove(p.getUniqueId());
                        if (pd != null) {
                            Tasks.cancel(pd.task());
                        }

                        if (this.plugin.ffa().leave(p, false)) {
                            String cmd = command.startsWith("/") ? command.substring(1) : command;
                            String l = label(cmd);
                            if (!l.equals("lobby") && !l.equals("hub") && !l.equals("leave") && !l.equals("l")) {
                                p.performCommand(cmd);
                            }
                        }
                    }
                });
                this.pending.put(p.getUniqueId(), new CommandGuardListener.Pending(task, p.getLocation(), command));
            }
        }
    }

    private void cancel(Player p, String reason) {
        CommandGuardListener.Pending pd = this.pending.remove(p.getUniqueId());
        if (pd != null) {
            Tasks.cancel(pd.task());
            if (reason != null && p.isOnline()) {
                p.sendActionBar(Text.mm("<red>Leaving cancelled: " + reason));
                Snd.error(p);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onMove(PlayerMoveEvent e) {
        CommandGuardListener.Pending pd = this.pending.get(e.getPlayer().getUniqueId());
        if (pd != null) {
            Location to = e.getTo();
            if (to.getWorld() != pd.from().getWorld() || to.distanceSquared(pd.from()) > 0.36) {
                this.cancel(e.getPlayer(), "you moved");
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && this.pending.containsKey(p.getUniqueId())) {
            this.cancel(p, "you took damage");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.cancel(e.getPlayer(), null);
    }

    private record Pending(BukkitTask task, Location from, String command) {
    }
}
