package net.vortexpvp.smp;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.permissions.PermissionAttachment;

/**
 * 3.11.0 - everyone is equal in the SMP. Inside SMP worlds nobody has admin powers or rank perks, not even operators
 * or the owner: only normal player commands work (homes, tpa, rtp, warps, trade, market, chat...), the game mode is
 * always survival, nobody can fly, and perk permissions (more homes, teleport bypass, warp and claim admin) are switched
 * off while there. Rank names still show in chat and tab. Admin work is done from the lobby, the panel or the console.
 * Settings -> SMP -> "Everyone Equal in SMP" turns it off.
 */
public final class SmpLockdown implements Listener {
    /** What everybody can use in the SMP. */
    private static final Set<String> ALLOWED = Set.of(
        "home", "homes", "sethome", "delhome", "tpa", "tpahere", "tpaccept", "tpdeny", "tpacancel", "tptoggle", "rtp", "spawn", "back",
        "warp", "warps", "trade", "market", "ah", "auction", "trash", "smp", "hub", "lobby", "practice", "worlds", "leave", "leavepvp",
        "msg", "tell", "w", "whisper", "m", "r", "reply", "msgtoggle", "ignore", "report", "friend", "friends", "f", "party", "p", "partychat",
        "pc", "status", "settings", "cosmetics", "namecolor", "color", "colour", "season", "stats", "playtime", "ping", "rank", "country",
        "link", "links", "website", "guide", "help", "rules", "discord", "verify", "list", "near", "drill", "leaderboard", "lb", "region"
    );
    /** Perk and admin permissions switched off inside the SMP (also for operators). */
    private static final List<String> PERKS = List.of(
        "vortexsmp.admin", "vortexsmp.staff", "vortexsmp.homes.unlimited", "vortexsmp.teleport.bypass", "vortexsmp.warps.admin",
        "vortexpvp.homes.unlimited", "vortexpvp.homes.4", "vortexpvp.homes.6", "vortexpvp.homes.8", "vortexpvp.homes.10", "vortexpvp.homes.12",
        "vortexpvp.homes.16", "vortexpvp.homes.20", "vortexpvp.teleport.bypass", "vortexpvp.warps.admin", "vortexpvp.claims.admin", "vortexpvp.smpadmin",
        "vortexpvp.ah.unlimited", "vortexpvp.lobbyfly", "vortexpvp.admin", "vortexpvp.staff", "vortexpvp.staff.mod", "vortexpvp.staff.alts",
        "minecraft.command.gamemode", "minecraft.command.give", "minecraft.command.tp", "minecraft.command.teleport", "minecraft.command.gamerule",
        "minecraft.command.effect", "minecraft.command.enchant", "minecraft.command.xp", "minecraft.command.time", "minecraft.command.weather",
        "minecraft.command.kill", "minecraft.command.summon", "minecraft.command.fill", "minecraft.command.setblock", "minecraft.command.clone",
        "minecraft.command.item", "minecraft.command.attribute", "bukkit.command.reload"
    );
    private final VortexPvP plugin;
    private final Map<UUID, PermissionAttachment> off = new HashMap<>();

    public SmpLockdown(VortexPvP plugin) {
        this.plugin = plugin;
    }

    private boolean on() {
        return Cfg.bool(Opt.SMP_EQUAL);
    }

    private boolean inSmp(Player p) {
        return this.plugin.modes() != null && this.plugin.modes().smpWorld(p.getWorld());
    }

    /** Makes a player a normal survival player for the SMP (or gives the perks back outside it). */
    public void apply(Player p) {
        boolean lock = this.on() && this.inSmp(p);
        PermissionAttachment a = this.off.get(p.getUniqueId());
        if (lock) {
            if (a == null) {
                a = p.addAttachment(this.plugin);
                for (String perm : PERKS) {
                    a.setPermission(perm, false);
                }

                this.off.put(p.getUniqueId(), a);
                p.recalculatePermissions();
                p.updateCommands();
            }

            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.SPECTATOR) {
                p.setGameMode(GameMode.SURVIVAL);
            }

            if (p.getGameMode() == GameMode.SPECTATOR && !p.isDead()) {
                p.setGameMode(GameMode.SURVIVAL);
            }

            p.setFlying(false);
            p.setAllowFlight(false);
        } else if (a != null) {
            this.off.remove(p.getUniqueId());
            try {
                p.removeAttachment(a);
            } catch (IllegalArgumentException ignored) {
                // already gone (quit)
            }

            p.recalculatePermissions();
            p.updateCommands();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (e.getPlayer().isOnline()) {
                this.apply(e.getPlayer());
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        this.apply(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.off.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!this.on() || !this.inSmp(p)) {
            return;
        }

        String m = e.getMessage().startsWith("/") ? e.getMessage().substring(1) : e.getMessage();
        int sp = m.indexOf(' ');
        String label = (sp < 0 ? m : m.substring(0, sp)).toLowerCase(Locale.ROOT);
        label = label.substring(label.indexOf(':') + 1);
        if (!ALLOWED.contains(label)) {
            e.setCancelled(true);
            p.sendMessage(Text.mm("<red>Everyone plays the SMP as a normal player.</red> <gray>/" + Text.esc(label) + " doesn't work here (ranks and admin commands are for Practice and the lobby)."));
            Snd.error(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        if (this.on() && this.inSmp(e.getPlayer()) && e.getNewGameMode() != GameMode.SURVIVAL
            && e.getCause() != PlayerGameModeChangeEvent.Cause.HARDCORE_DEATH) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFly(PlayerToggleFlightEvent e) {
        if (e.isFlying() && this.on() && this.inSmp(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().setAllowFlight(false);
        }
    }

    /** Every few seconds: anyone in the SMP is checked again (settings changed, worlds linked...). */
    public void sweep() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            this.apply(p);
        }
    }
}
