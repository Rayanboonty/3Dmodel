package net.practicepvp.smp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.session.PlayerState;
import net.practicepvp.session.Tp;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

/**
 * Rules of the hub world (no damage, no building, the selector items) and the travel commands
 * /hub /practice /worlds /smp. The SMP itself runs on its own server now (VortexSMP).
 */
public final class ModeRules implements Listener, CommandExecutor, TabCompleter {
    private final PracticePlugin plugin;
    private final ModeManager modes;
    private final NamespacedKey healthKey;
    /** Practice commands that would pull a player out of the SMP into a fight (they use /practice first). */
    private static final java.util.Set<String> PRACTICE_ONLY = java.util.Set.of(
        "queue", "q", "ranked", "unranked", "duel", "1v1", "fight", "ffa", "spectate", "spec", "kiteditor", "editkit", "layout",
        "rematch", "bot", "bots", "practicebot", "train", "dummy", "training", "event", "events", "tournament", "tourney", "learn",
        "academy", "lessons", "tutorial", "challenge", "tierchallenge", "tc"
    );

    public ModeRules(PracticePlugin plugin, ModeManager modes) {
        this.plugin = plugin;
        this.modes = modes;
        // left on players by the old Lifesteal mode; removed so nobody keeps extra or fewer hearts
        this.healthKey = new NamespacedKey(plugin, "lifesteal_hearts");
    }

    private WorldMode mode(Player p) {
        return this.modes.mode(p.getWorld());
    }

    public void apply(Player p) {
        this.cleanLegacy(p);
        if (!this.plugin.worlds().managed(p.getWorld()) && this.mode(p) == WorldMode.HUB) {
            boolean builder = builder(p);
            if (!builder) {
                p.setGameMode(GameMode.ADVENTURE);
                p.setHealth(Math.max(1.0, p.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : p.getAttribute(Attribute.MAX_HEALTH).getValue()));
                p.setFoodLevel(20);
            }

            if (!this.hasSelector(p)) {
                this.plugin.hub().giveItems(p);
            }
        }
    }

    /** Lifesteal hearts, Strength effects and the Ranked SMP name tag of the old SMP modes. */
    private void cleanLegacy(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && max.getModifier(this.healthKey) != null) {
            max.removeModifier(this.healthKey);
            if (p.getHealth() > max.getValue()) {
                p.setHealth(max.getValue());
            }
        }

        if (!this.plugin.worlds().managed(p.getWorld())) {
            for (PotionEffectType t : List.of(PotionEffectType.STRENGTH, PotionEffectType.WEAKNESS)) {
                PotionEffect pe = p.getPotionEffect(t);
                if (pe != null && pe.isInfinite()) {
                    p.removePotionEffect(t);
                }
            }
        }

        Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam("ppr" + p.getUniqueId().toString().replace("-", "").substring(0, 12));
        if (t != null) {
            t.unregister();
        }
    }

    private boolean hasSelector(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (this.modes.isSelector(it)) {
                return true;
            }
        }

        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        this.modes.stashLegacy(p);
        this.apply(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // a copy of everything they carry before this version touches it
        try {
            this.modes.firstSeen(p);
        } catch (RuntimeException ex) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING, "Join backup of " + p.getName() + " failed", ex);
        }

        // first thing: a player who logged off in an old SMP world keeps those items in a saved file
        this.modes.stashLegacy(p);
        Tasks.later(2L, () -> {
            if (p.isOnline()) {
                this.modes.stashLegacy(p);
                World w = p.getWorld();
                if (!ModeManager.sameServer() && !this.plugin.worlds().managed(w) && this.modes.retired().contains(this.modes.parentOf(w).getName())) {
                    // they logged off in an old SMP world that is still on this server's disk
                    Tp.to(p, this.modes.hub());
                }

                this.apply(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        if (this.plugin.serverLink() != null) {
            this.plugin.serverLink().forget(e.getPlayer());
        }

        Player p = e.getPlayer();
        this.modes.remember(p, p.getLocation());
        try {
            this.modes.saveCurrent(p);
        } catch (RuntimeException ex) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not save the SMP inventory of " + p.getName() + " on logout", ex);
        }
    }

    /** Where a player was in an SMP world when they left it (the selector brings them back there). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() != null && e.getFrom().getWorld() != e.getTo().getWorld()) {
            this.modes.remember(e.getPlayer(), e.getFrom());
        }
    }

    /** Practice fights start from /practice, not from inside the SMP. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSmpCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!this.modes.smpWorld(p.getWorld())) {
            return;
        }

        String m = e.getMessage().startsWith("/") ? e.getMessage().substring(1) : e.getMessage();
        int sp = m.indexOf(' ');
        String label = (sp < 0 ? m : m.substring(0, sp)).toLowerCase(Locale.ROOT);
        label = label.substring(label.indexOf(':') + 1);
        if (PRACTICE_ONLY.contains(label)) {
            e.setCancelled(true);
            p.sendMessage(Text.mm("<red>That's a Practice command. <gray>Go to <white>/practice</white> first - your SMP items stay saved."));
            Snd.error(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (this.modes.smpWorld(p.getWorld()) && !e.isBedSpawn() && !e.isAnchorSpawn()) {
            // no bed or anchor: back to the SMP spawn, not the server's first world
            World smp = this.modes.parentOf(p.getWorld());
            e.setRespawnLocation(this.modes.spawn(smp != null && this.modes.smpWorld(smp) ? smp : p.getWorld()));
        }

        if (!this.plugin.worlds().managed(p.getWorld())) {
            Tasks.later(1L, () -> {
                if (p.isOnline()) {
                    this.apply(p);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player p = e.getPlayer();
            if (this.modes.isSelector(e.getItem()) && !this.plugin.hub().inHub(p)) {
                e.setCancelled(true);
                this.modes.selector(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHubInventory(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.hub(p) && !builder(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (this.mode(e.getPlayer()) == WorldMode.HUB) {
            e.setCancelled(true);
        }
    }

    private boolean hub(Player p) {
        return !this.plugin.worlds().managed(p.getWorld()) && this.mode(p) == WorldMode.HUB;
    }

    private static boolean builder(Player p) {
        return p.hasPermission("practice.admin") && p.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHubDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && this.hub(p)) {
            if (this.plugin.hub().allowsDamage(e)) {
                return;
            }

            e.setCancelled(true);
            if (e.getCause() == DamageCause.VOID) {
                Tasks.sync(() -> Tp.to(p, this.modes.spawn(p.getWorld())));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHubFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && this.hub(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHubBreak(BlockBreakEvent e) {
        if (this.hub(e.getPlayer()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHubPlace(BlockPlaceEvent e) {
        if (this.hub(e.getPlayer()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHubDrop(PlayerDropItemEvent e) {
        if (this.hub(e.getPlayer()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        switch (name) {
            case "worlds":
                this.modes.selector(p);
                break;
            case "practice":
                World lobby = this.plugin.lobby().lobby().getWorld();
                if (this.plugin.worlds().managed(p.getWorld()) && this.plugin.sessions().state(p) != PlayerState.OUTSIDE) {
                    p.sendMessage(Text.mm("<gray>You're already in Practice."));
                } else if (lobby != null) {
                    this.modes.travel(p, lobby);
                }
                break;
            case "hub":
                World hub = this.modes.hubWorld();
                if (hub == null) {
                    this.plugin.lobby().sendToLobby(p);
                } else {
                    this.modes.travel(p, hub);
                }
                break;
            case "smp":
                String id = args.length > 0 ? args[0] : "smp";
                if (!Cfg.bool(Opt.NETWORK_SMP_COMMAND) && args.length == 0) {
                    this.modes.selector(p);
                } else if (this.plugin.serverSelector() == null || !this.plugin.serverSelector().join(p, id)) {
                    p.sendMessage(Text.mm("<red>The SMP isn't set up yet. <gray>Admins: add its address in /pvpadmin → Server Selector."));
                    Snd.error(p);
                }
                break;
            default:
                return false;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("smp") && args.length == 1 && this.plugin.serverSelector() != null) {
            for (ServerSelector.Entry e : this.plugin.serverSelector().servers()) {
                out.add(e.id);
            }
        }

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    public org.bukkit.Location hubSpawn() {
        return this.modes.hub();
    }
}
