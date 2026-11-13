package net.vortexrealm.smp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Tp;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
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
    private final VortexRealm plugin;
    private final ModeManager modes;
    private final NamespacedKey healthKey;

    public ModeRules(VortexRealm plugin, ModeManager modes) {
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
        // first thing: a player who logged off in an old SMP world keeps those items in a saved file
        this.modes.stashLegacy(p);
        Tasks.later(2L, () -> {
            if (p.isOnline()) {
                this.modes.stashLegacy(p);
                World w = p.getWorld();
                if (!this.plugin.worlds().managed(w) && this.modes.retired().contains(this.modes.parentOf(w).getName())) {
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
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
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
        return p.hasPermission("vortex.admin") && p.getGameMode() == GameMode.CREATIVE;
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
