package net.vortexrealm.smp;

import io.papermc.paper.registry.data.dialog.ActionButton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.board.BoardManager;
import net.vortexrealm.build.SpawnBuilder;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.data.KitStats;
import net.vortexrealm.data.PvPProfile;
import net.vortexrealm.fx.Fx;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.rank.Tier;
import net.vortexrealm.session.Tp;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class Hub implements Listener {
    private static final int SLOT_SELECTOR = 0;
    private static final int SLOT_PROFILE = 1;
    private static final int SLOT_PEARL = 2;
    private static final int SLOT_SWORD = 4;
    private static final int SLOT_VISIBILITY = 7;
    private static final int SLOT_INFO = 8;
    private final VortexRealm plugin;
    private final ModeManager modes;
    private final NamespacedKey key;
    private final Set<UUID> fighting = new HashSet<>();
    private final Set<UUID> hiding = new HashSet<>();
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();

    public Hub(VortexRealm plugin, ModeManager modes) {
        this.plugin = plugin;
        this.modes = modes;
        this.key = modes.hubItemKey();
    }

    public void init() {
        for (World w : Bukkit.getWorlds()) {
            if (this.modes.mode(w) == WorldMode.HUB) {
                w.setGameRule(GameRules.PVP, true);
                w.setGameRule(GameRules.KEEP_INVENTORY, true);
                w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
            }
        }
    }

    public boolean inHub(Player p) {
        return !this.plugin.worlds().managed(p.getWorld()) && this.modes.mode(p.getWorld()) == WorldMode.HUB;
    }

    private String tag(ItemStack it) {
        return it != null && !it.isEmpty() && it.hasItemMeta() ? it.getItemMeta().getPersistentDataContainer().get(this.key, PersistentDataType.STRING) : null;
    }

    public void giveItems(Player p) {
        if (!this.fighting.contains(p.getUniqueId())) {
            PlayerInventory inv = p.getInventory();
            if (!this.modes.stashForeign(p)) {
                // could not save what they carry: never clear it
                return;
            }

            if (true) {
                inv.clear();
                inv.setItem(0, this.modes.selectorItem());
                inv.setItem(
                    1,
                    Items.of(Material.PLAYER_HEAD)
                        .skull(p)
                        .name("<gold><b>Your Profile")
                        .lore("<gray>Your ranks, tiers", "<gray>and play time.", "", "<green>▶ Click to view")
                        .tag(this.key, "profile")
                        .build()
                );
                inv.setItem(2, this.pearl());
                inv.setItem(
                    4,
                    Items.of(Material.NETHERITE_SWORD)
                        .name("<b><gradient:#FF5555:#FFAA00>PvP Sword</gradient></b>")
                        .lore("<gray>Hold it to fight other players", "<gray>holding the sword. Nothing is lost.", "", "<yellow>Hold <gray>to enable PvP")
                        .tag(this.key, "sword")
                        .unbreakable()
                        .hideAll()
                        .build()
                );
                inv.setItem(7, this.visibilityItem(this.hiding.contains(p.getUniqueId())));
                inv.setItem(
                    8,
                    Items.of(Material.KNOWLEDGE_BOOK)
                        .name("<aqua><b>Server Info")
                        .lore("<gray>Links, commands and rules.", "", "<green>▶ Click to read")
                        .tag(this.key, "info")
                        .build()
                );
                inv.setHeldItemSlot(0);
                if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                    p.setAllowFlight(true);
                }
            } else {
                boolean has = false;

                for (ItemStack it : inv.getContents()) {
                    has |= this.modes.isSelector(it);
                }

                if (!has) {
                    inv.addItem(new ItemStack[]{this.modes.selectorItem()});
                }
            }
        }
    }

    private ItemStack pearl() {
        return Items.of(Material.ENDER_PEARL).name("<light_purple><b>Lobby Pearl").lore("<gray>Infinite - fly around!").tag(this.key, "pearl").build();
    }

    private ItemStack visibilityItem(boolean hidden) {
        return Items.of(hidden ? Material.GRAY_DYE : Material.LIME_DYE)
            .name(hidden ? "<gray><b>Players: Hidden" : "<green><b>Players: Shown")
            .lore("<gray>Click to " + (hidden ? "show" : "hide") + " other players.")
            .tag(this.key, "visibility")
            .build();
    }

    public boolean hides(Player viewer, Player target) {
        return this.hiding.contains(viewer.getUniqueId()) && this.inHub(viewer) && this.inHub(target) && !target.hasPermission("vortex.staff");
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) {
            Player p = e.getPlayer();
            String t = this.tag(e.getItem());
            if (t != null && this.inHub(p)) {
                switch (t) {
                    case "selector":
                        e.setCancelled(true);
                        this.modes.selector(p);
                        break;
                    case "profile":
                        e.setCancelled(true);
                        this.profile(p);
                        break;
                    case "visibility":
                        e.setCancelled(true);
                        boolean hide = !this.hiding.remove(p.getUniqueId());
                        if (hide) {
                            this.hiding.add(p.getUniqueId());
                        }

                        p.getInventory().setItem(7, this.visibilityItem(hide));
                        this.plugin.lobby().refreshVisibility(p);
                        Snd.click(p);
                        p.sendActionBar(Text.mm(hide ? "<gray>Players hidden" : "<green>Players shown"));
                        break;
                    case "info":
                        e.setCancelled(true);
                        p.performCommand("links");
                        p.sendMessage(
                            Text.mm(
                                "<gray>• <yellow>/worlds</yellow> server selector  • <yellow>/smpstats</yellow> your stats  • <yellow>/lobby</yellow> PvP practice"
                            )
                        );
                        break;
                    case "sword":
                        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                            e.setCancelled(true);
                        }
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onPearl(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof EnderPearl && e.getEntity().getShooter() instanceof Player p && this.inHub(p) && !this.fighting.contains(p.getUniqueId())) {
            Tasks.later(1L, () -> {
                if (p.isOnline() && this.inHub(p)) {
                    p.getInventory().setItem(2, this.pearl());
                    p.setCooldown(Material.ENDER_PEARL, 10);
                }
            });
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (this.inHub(p)) {
            boolean sword = "sword".equals(this.tag(p.getInventory().getItem(e.getNewSlot())));
            BukkitTask old = this.pending.remove(p.getUniqueId());
            if (old != null) {
                Tasks.cancel(old);
            }

            boolean on = this.fighting.contains(p.getUniqueId());
            if (sword && !on) {
                this.countdown(p, 1, "<red>Enabling PvP", () -> this.enablePvp(p), true);
            } else if (!sword && on) {
                this.countdown(p, 3, "<gray>Disabling PvP", () -> this.disablePvp(p), false);
            }
        }
    }

    private void countdown(Player p, int seconds, String label, Runnable done, boolean needSword) {
        int[] left = new int[]{seconds};
        p.sendActionBar(Text.mm(label + " in <gold>" + left[0] + "</gold>..."));
        BukkitTask task = Tasks.timer(20L, 20L, () -> {
            left[0]--;
            if (!p.isOnline() || !this.inHub(p)) {
                this.cancelPending(p);
            } else if (left[0] > 0) {
                p.sendActionBar(Text.mm(label + " in <gold>" + left[0] + "</gold>..."));
                Snd.play(p, "block.note_block.hat", 0.7F, 1.4F);
            } else {
                this.cancelPending(p);
                boolean holding = "sword".equals(this.tag(p.getInventory().getItemInMainHand()));
                if (holding == needSword) {
                    done.run();
                }
            }
        });
        this.pending.put(p.getUniqueId(), task);
    }

    private void cancelPending(Player p) {
        BukkitTask t = this.pending.remove(p.getUniqueId());
        if (t != null) {
            Tasks.cancel(t);
        }
    }

    private void enablePvp(Player p) {
        this.fighting.add(p.getUniqueId());
        PlayerInventory inv = p.getInventory();
        inv.setHelmet(armor(Material.DIAMOND_HELMET));
        inv.setChestplate(armor(Material.DIAMOND_CHESTPLATE));
        inv.setLeggings(armor(Material.DIAMOND_LEGGINGS));
        inv.setBoots(armor(Material.DIAMOND_BOOTS));
        p.setAllowFlight(false);
        p.setFlying(false);
        Fx.title(p, Fx.shineFrame("PVP ON", Fx.RED, 2.0), Text.mm("<gray>Hit other sword holders"), 0, 25, 8);
        Snd.play(p, "item.armor.equip_diamond", 1.0F, 1.0F);
        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0.0, 1.0, 0.0), 20, 0.4, 0.6, 0.4, 0.1);
    }

    private static ItemStack armor(Material m) {
        return Items.of(m).unbreakable().hideAll().build();
    }

    private void disablePvp(Player p) {
        if (this.fighting.remove(p.getUniqueId())) {
            PlayerInventory inv = p.getInventory();
            inv.setArmorContents(new ItemStack[4]);
            if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                p.setAllowFlight(true);
            }

            p.sendActionBar(Text.mm("<gray>PvP disabled"));
            Snd.play(p, "item.armor.equip_generic", 1.0F, 1.0F);
        }
    }

    public boolean allowsDamage(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent ed && e.getEntity() instanceof Player victim) {
            Player attacker = ed.getDamager() instanceof Player pl
                ? pl
                : (ed.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player sh ? sh : null);
            return attacker != null
                && !attacker.equals(victim)
                && this.fighting.contains(victim.getUniqueId())
                && this.fighting.contains(attacker.getUniqueId());
        } else {
            return false;
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (this.inHub(victim)) {
            e.setKeepInventory(true);
            e.getDrops().clear();
            e.setKeepLevel(true);
            e.setDroppedExp(0);
            e.deathMessage(null);
            Player killer = victim.getKiller();
            this.streaks.remove(victim.getUniqueId());
            if (killer != null && this.inHub(killer)) {
                int streak = this.streaks.merge(killer.getUniqueId(), 1, Integer::sum);
                killer.setHealth(Math.max(1.0, killer.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : killer.getAttribute(Attribute.MAX_HEALTH).getValue()));

                for (Player o : victim.getWorld().getPlayers()) {
                    if (this.fighting.contains(o.getUniqueId()) || o.equals(victim)) {
                        o.sendMessage(
                            Text.mm(
                                "<red>⚔ <white>"
                                    + Text.esc(victim.getName())
                                    + " <gray>was slain by <white>"
                                    + Text.esc(killer.getName())
                                    + (streak >= 3 ? " <gold>(" + streak + " streak)" : "")
                            )
                        );
                    }
                }

                Snd.play(killer, "entity.experience_orb.pickup", 1.0F, 1.5F);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (this.inHub(p) || this.fighting.contains(p.getUniqueId())) {
            World hub = this.modes.hubWorld();
            if (hub != null && p.getWorld().equals(hub)) {
                e.setRespawnLocation(this.modes.spawn(hub));
            }

            this.fighting.remove(p.getUniqueId());
            this.cancelPending(p);
            Tasks.later(1L, () -> {
                if (p.isOnline() && this.inHub(p)) {
                    this.giveItems(p);
                }
            });
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (e.isFlying() && this.inHub(p) && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
            e.setCancelled(true);
            p.setAllowFlight(false);
            p.setFlying(false);
            Vector v = p.getLocation().getDirection().multiply(1.2).setY(0.95);
            p.setVelocity(v);
            Snd.play(p, "entity.breeze.jump", 0.6F, 1.3F);
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 12, 0.3, 0.05, 0.3, 0.03);
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onLand(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!p.getAllowFlight() && this.inHub(p) && !this.fighting.contains(p.getUniqueId()) && p.getGameMode() != GameMode.SPECTATOR && ((org.bukkit.entity.Entity)p).isOnGround()) {
            p.setAllowFlight(true);
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        World hub = this.modes.hubWorld();
        if (hub != null && Cfg.bool(Opt.JOIN_AT_HUB)) {
            Tasks.later(4L, () -> {
                if (p.isOnline() && !this.plugin.sessions().state(p).fighting()) {
                    if (!p.getWorld().equals(hub)) {
                        Tp.to(p, this.modes.spawn(hub));
                    }

                    Fx.title(p, Fx.shineFrame("WELCOME", Fx.GOLD, 2.0), Text.mm("<gray>Pick a mode with the <gold>Server Selector"), 5, 50, 15);
                }
            });
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (this.fighting.remove(p.getUniqueId())) {
            p.getInventory().setArmorContents(new ItemStack[4]);
        }

        this.cancelPending(p);
        boolean hubInvolved = this.modes.mode(e.getFrom()) == WorldMode.HUB || this.modes.mode(p.getWorld()) == WorldMode.HUB;
        if (!this.inHub(p) && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR && this.modes.mode(e.getFrom()) == WorldMode.HUB) {
            p.setAllowFlight(false);
            p.setFlying(false);
        }

        if (hubInvolved) {
            this.plugin.lobby().refreshVisibilityAll();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        if (this.fighting.remove(u)) {
            e.getPlayer().getInventory().setArmorContents(new ItemStack[4]);
        }

        this.cancelPending(e.getPlayer());
        this.streaks.remove(u);
    }

    public void profile(Player p) {
        PvPProfile prof = this.plugin.profiles().get(p);
        List<Component> header = new ArrayList<>();
        header.add(
            Text.mm(
                "<gold><b>"
                    + Text.esc(p.getName())
                    + "</b> <dark_gray>• <gray>playing for <white>"
                    + p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 3600
                    + "h"
            )
        );
        List<Entry<String, KitStats>> ranked = new ArrayList<>();

        for (Entry<String, KitStats> e : prof.kits.entrySet()) {
            if (e.getValue().ranked() && this.plugin.kits().get(e.getKey()) != null) {
                ranked.add(e);
            }
        }

        ranked.sort((a, bx) -> ((KitStats)bx.getValue()).tier - a.getValue().tier);
        Component pvp = Text.mm("<gray>PvP ranks: ");
        if (ranked.isEmpty()) {
            pvp = pvp.append(Text.mm("<dark_gray>none yet - play ranked!"));
        }

        for (int i = 0; i < Math.min(4, ranked.size()); i++) {
            Kit k = this.plugin.kits().get(ranked.get(i).getKey());
            Tier t = ranked.get(i).getValue().tier();
            pvp = pvp.append(k.iconComponent()).append((Component)Component.space()).append(BoardManager.tierTag(t)).append(Text.mm("  "));
        }

        header.add(pvp);
        long minutes = p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
        header.add(Text.mm("<gray>Play time here: <white>" + net.vortexrealm.util.Fmt.duration(minutes * 60L)));
        List<ActionButton> b = new ArrayList<>();
        b.add(this.plugin.dialogs().lobby().btn(p, "<gold>Server Selector", null, () -> this.modes.selector(p)));
        this.plugin.dialogs().lobby().screen(p, Fx.shineFrame("PROFILE", Fx.GOLD, 1.0), List.of(), header, null, b, 1, this.plugin.dialogs().lobby().close(p));
    }

    public void setup(Player admin) {
        World hub = this.modes.hubWorld();
        if (hub == null) {
            admin.sendMessage(Text.mm("<yellow>Creating the hub world..."));
            hub = Bukkit.getWorld("hub");
            if (hub == null) {
                hub = this.modes.create("hub", "void");
            }

            if (hub == null) {
                admin.sendMessage(Text.mm("<red>Could not create the hub world."));
                return;
            }

            this.modes.setMode(hub, WorldMode.HUB);
        }

        Location center = new Location(hub, 0.5, 100.0, 0.5);
        hub.getBlockAt(0, 99, 0).setType(Material.QUARTZ_BLOCK, false);
        Tp.to(admin, center);
        this.modes.buildSpawn(admin, SpawnBuilder.Style.ROYAL_CASTLE, SpawnBuilder.Style.ROYAL_CASTLE.radius);
        admin.sendMessage(
            Text.mm(
                "<green>Hub ready soon. <gray>Players will join here (Settings → Hub & Extras → Join at Hub). Edit the selector in <yellow>/pvpadmin → Server Selector</yellow>."
            )
        );
    }
}
