package net.vortexpvp.listener;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.EnumSet;
import java.util.Set;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Msg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.data.Setting;
import net.vortexpvp.dialog.Dialogs;
import net.vortexpvp.gui.Menu;
import net.vortexpvp.gui.menus.PartyMenu;
import net.vortexpvp.lobby.Hotbar;
import net.vortexpvp.match.Match;
import net.vortexpvp.match.PreMatch;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Session;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class LobbyListener implements Listener {
    private static final Set<Material> UTILITY = EnumSet.of(
        Material.ANVIL,
        Material.CHIPPED_ANVIL,
        Material.DAMAGED_ANVIL,
        Material.CRAFTING_TABLE,
        Material.ENCHANTING_TABLE,
        Material.ENDER_CHEST,
        Material.BEACON,
        Material.LOOM,
        Material.STONECUTTER,
        Material.GRINDSTONE,
        Material.SMITHING_TABLE,
        Material.CARTOGRAPHY_TABLE,
        Material.LECTERN,
        Material.RESPAWN_ANCHOR,
        Material.FLOWER_POT,
        Material.CRAFTER,
        Material.COMPOSTER,
        Material.CAULDRON,
        Material.WATER_CAULDRON,
        Material.LAVA_CAULDRON,
        Material.CAKE,
        Material.JUKEBOX,
        Material.NOTE_BLOCK
    );
    private final VortexPvP plugin;

    public LobbyListener(VortexPvP plugin) {
        this.plugin = plugin;
    }

    private static boolean protectedState(PlayerState st) {
        return st == PlayerState.LOBBY || st == PlayerState.QUEUE || st == PlayerState.EDITING || st == PlayerState.PREMATCH || st == PlayerState.SPECTATING;
    }

    private boolean builder(Session s) {
        return s.buildMode && s.state == PlayerState.LOBBY;
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Session s = this.plugin.sessions().get(p);
        ItemStack item = e.getItem();
        if (Items.tag(item, Items.WAND) != null && p.hasPermission("vortexpvp.admin") && e.getClickedBlock() != null) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) {
                Location l = e.getClickedBlock().getLocation();
                boolean first = e.getAction() == Action.LEFT_CLICK_BLOCK;
                if (first) {
                    s.wandPos1 = l;
                } else {
                    s.wandPos2 = l;
                }

                Msg.send(p, first ? "admin.wand-pos1" : "admin.wand-pos2", "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ());
                p.spawnParticle(Particle.HAPPY_VILLAGER, l.clone().add(0.5, 1.1, 0.5), 10, 0.3, 0.1, 0.3, 0.0);
            }
        } else if (s.state == PlayerState.PREMATCH) {
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.OFF_HAND) {
                PreMatch pm = s.match == null ? null : s.match.preMatch();
                if (pm == null || e.getAction() == Action.PHYSICAL || !pm.useItem(p, Items.tag(item, Items.ACTION))) {
                    this.clickPreMatch(p, s);
                }
            }
        } else if (protectedState(s.state)) {
            if (System.currentTimeMillis() < s.clickLockUntil && !this.builder(s)) {
                e.setCancelled(true);
            } else if (e.getHand() == EquipmentSlot.OFF_HAND) {
                if (!this.builder(s)) {
                    e.setCancelled(true);
                }
            } else {
                Hotbar.Action action = Hotbar.actionOf(item);
                if (action != null) {
                    e.setCancelled(true);
                    e.setUseItemInHand(Result.DENY);
                    e.setUseInteractedBlock(Result.DENY);
                    if (e.getAction() != Action.PHYSICAL && !s.clickCooldown(250L)) {
                        this.handle(p, action);
                    }
                } else if (!this.builder(s) && Cfg.bool(Opt.LOBBY_BUILD_BLOCK)) {
                    if (e.getAction() == Action.PHYSICAL) {
                        Block b = e.getClickedBlock();
                        if (b != null && b.getType() == Material.FARMLAND) {
                            e.setCancelled(true);
                        }
                    } else {
                        Block b = e.getClickedBlock();
                        if (b != null && (b.getState() instanceof Container || UTILITY.contains(b.getType()) || b.getType().name().endsWith("_BED"))) {
                            e.setUseInteractedBlock(Result.DENY);
                        }

                        if (item != null && !item.getType().isAir()) {
                            e.setUseItemInHand(Result.DENY);
                        }
                    }
                }
            }
        }
    }

    private void clickPreMatch(Player p, Session s) {
        Match m = s.match;
        PreMatch pm = m == null ? null : m.preMatch();
        if (pm != null) {
            pm.click(p);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onSwing(PlayerAnimationEvent e) {
        Session s = this.plugin.sessions().get(e.getPlayer());
        if (s.state == PlayerState.PREMATCH) {
            this.clickPreMatch(e.getPlayer(), s);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        Session s = this.plugin.sessions().get(p);
        if (s.state == PlayerState.PREMATCH) {
            e.setCancelled(true);
            this.clickPreMatch(p, s);
        } else if (protectedState(s.state) && !this.builder(s)) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND && !(e.getRightClicked() instanceof Interaction)) {
                if (e.getRightClicked() instanceof Player t && p.isSneaking() && this.plugin.statsHologram() != null && Cfg.bool(Opt.STATS_3D)) {
                    this.plugin.statsHologram().open(p, t.getUniqueId(), t.getName());
                } else {
                    Hotbar.Action action = Hotbar.actionOf(p.getInventory().getItemInMainHand());
                    if (action != null && !s.clickCooldown(250L)) {
                        this.handle(p, action);
                    }
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onAttackEntity(PrePlayerAttackEntityEvent e) {
        Player p = e.getPlayer();
        Session s = this.plugin.sessions().get(p);
        if (s.state == PlayerState.PREMATCH) {
            e.setCancelled(true);
            this.clickPreMatch(p, s);
        } else if (protectedState(s.state) && !this.builder(s)) {
            e.setCancelled(true);
            if (!(e.getAttacked() instanceof Interaction)) {
                Hotbar.Action action = Hotbar.actionOf(p.getInventory().getItemInMainHand());
                if (action != null && !s.clickCooldown(250L)) {
                    this.handle(p, action);
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.NORMAL
    )
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu)) {
                Session s = this.plugin.sessions().get(p);
                if (protectedState(s.state) && !this.builder(s)) {
                    e.setCancelled(true);
                    Hotbar.Action action = Hotbar.actionOf(e.getCurrentItem());
                    if (action == null && e.getClick() == ClickType.NUMBER_KEY) {
                        action = Hotbar.actionOf(p.getInventory().getItem(e.getHotbarButton()));
                    }

                    if (action != null && !s.clickCooldown(250L)) {
                        Hotbar.Action a = action;
                        Tasks.sync(() -> {
                            p.closeInventory();
                            this.handle(p, a);
                        });
                    }
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.NORMAL
    )
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && !(e.getView().getTopInventory().getHolder(false) instanceof Menu)) {
            Session s = this.plugin.sessions().get(p);
            if (protectedState(s.state) && !this.builder(s)) {
                e.setCancelled(true);
            }
        }
    }

    public void handle(Player p, Hotbar.Action action) {
        Snd.click(p);
        switch (action) {
            case RANKED:
                this.plugin.dialogs().openQueue(p, true, Dialogs.QueueTab.MOST_PLAYED);
                break;
            case UNRANKED:
                this.plugin.dialogs().openQueue(p, this.plugin.sessions().get(p).menuRanked && Cfg.bool(Opt.RANKED_ENABLED), Dialogs.QueueTab.MOST_PLAYED);
                break;
            case FFA:
                this.plugin.dialogs().openFfa(p);
                break;
            case PARTY_CREATE:
                this.plugin.parties().create(p);
                break;
            case SPECTATE:
                this.plugin.dialogs().lobby().spectate(p);
                break;
            case LEADERBOARDS:
                this.plugin.dialogs().lobby().leaderboards(p);
                break;
            case KIT_EDITOR:
                this.plugin.dialogs().lobby().kitEditor(p);
                break;
            case SETTINGS:
                if (this.plugin.settingsDialogs() != null) {
                    this.plugin.settingsDialogs().hub(p);
                } else {
                    this.plugin.dialogs().lobby().settings(p, Setting.Category.GAMEPLAY);
                }
                break;
            case LEAVE_QUEUE:
                this.plugin.queues().leaveAll(p, true);
                Msg.send(p, "queue.left-all");
                break;
            case PARTY_GAMES:
                this.plugin.dialogs().lobby().partyGames(p, false);
                break;
            case PARTY_INFO:
                this.plugin.dialogs().lobby().party(p);
                break;
            case PARTY_INVITE:
                PartyMenu.invitePrompt(this.plugin, p);
                break;
            case PARTY_LEAVE:
                this.plugin.parties().leave(p);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onDrop(PlayerDropItemEvent e) {
        Session s = this.plugin.sessions().get(e.getPlayer());
        if (protectedState(s.state) && !this.builder(s)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            Session s = this.plugin.sessions().get(p);
            if (protectedState(s.state) && !this.builder(s)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Session s = this.plugin.sessions().get(e.getPlayer());
        if (protectedState(s.state) && !this.builder(s)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onBreak(BlockBreakEvent e) {
        Session s = this.plugin.sessions().get(e.getPlayer());
        if (protectedState(s.state) && !this.builder(s)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onPlace(BlockPlaceEvent e) {
        Session s = this.plugin.sessions().get(e.getPlayer());
        if (protectedState(s.state) && !this.builder(s)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onItemDamage(PlayerItemDamageEvent e) {
        if (protectedState(this.plugin.sessions().state(e.getPlayer()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && protectedState(this.plugin.sessions().state(p))) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            Session s = this.plugin.sessions().get(p);
            if (protectedState(s.state)) {
                e.setCancelled(true);
                if (e.getCause() == DamageCause.VOID) {
                    this.rescue(p, s);
                }

                p.setFireTicks(0);
            }
        }
    }

    private void rescue(Player p, Session s) {
        switch (s.state) {
            case SPECTATING:
                if (s.spectating != null && s.spectating.instance() != null) {
                    Tp.to(p, s.spectating.instance().center());
                }
            case PREMATCH:
                break;
            default:
                Tp.to(p, this.plugin.lobby().lobby());
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Session s = this.plugin.sessions().get(p);
        if (s.state == PlayerState.LOBBY || s.state == PlayerState.QUEUE) {
            Location to = e.getTo();
            Location lobby = this.plugin.lobby().lobby();
            boolean editingArena = this.plugin.arenaEditor() != null && this.plugin.arenaEditor().isEditing(p);
            if (!editingArena && to.getWorld() == lobby.getWorld() && to.getY() < lobby.getY() - Cfg.i(Opt.LOBBY_VOID_DEPTH)) {
                Tasks.sync(() -> Tp.to(p, lobby));
            } else {
                if (p.getGameMode() != GameMode.CREATIVE
                    && p.getGameMode() != GameMode.SPECTATOR
                    && !p.getAllowFlight()
                    && to.clone().subtract(0.0, 0.2, 0.0).getBlock().getType().isSolid()
                    && this.canDoubleJump(p)) {
                    p.setAllowFlight(true);
                }
            }
        }
    }

    private boolean canDoubleJump(Player p) {
        return Cfg.bool(Opt.LOBBY_DOUBLE_JUMP) && this.plugin.profiles().get(p).on(Setting.DOUBLE_JUMP);
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        Session s = this.plugin.sessions().get(p);
        if (s.state == PlayerState.LOBBY || s.state == PlayerState.QUEUE) {
            if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                PvPProfile prof = this.plugin.profiles().get(p);
                boolean fly = prof.on(Setting.LOBBY_FLY) && p.hasPermission("vortexpvp.lobbyfly");
                if (!fly && e.isFlying()) {
                    e.setCancelled(true);
                    p.setFlying(false);
                    if (!this.canDoubleJump(p)) {
                        p.setAllowFlight(false);
                    } else {
                        p.setAllowFlight(false);
                        Vector v = p.getLocation().getDirection().multiply(Cfg.d(Opt.LOBBY_JUMP_POWER)).setY(0.95);
                        p.setVelocity(v);
                        Snd.play(p, "entity.breeze.jump", 0.6F, 1.2F);
                        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 12, 0.3, 0.05, 0.3, 0.02);
                    }
                }
            }
        }
    }
}
