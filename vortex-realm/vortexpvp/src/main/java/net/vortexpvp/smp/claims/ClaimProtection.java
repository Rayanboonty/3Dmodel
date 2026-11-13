package net.vortexpvp.smp.claims;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Golem;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.InventoryHolder;

public final class ClaimProtection implements Listener {
    private final ClaimManager claims;
    private final Map<UUID, Long> lastDeny = new HashMap<>();

    public ClaimProtection(ClaimManager claims) {
        this.claims = claims;
    }

    private Claim at(Location l) {
        return l != null && l.getWorld() != null && this.claims.hasClaims(l.getWorld()) ? this.claims.claimAt(l) : null;
    }

    private boolean denied(Player p, Location l, Claim.Trust need) {
        Claim c = this.at(l);
        if (c != null && !this.claims.bypassing(p) && !c.allows(p.getUniqueId(), need)) {
            long now = System.currentTimeMillis();
            Long last = this.lastDeny.get(p.getUniqueId());
            if (last == null || now - last > 1500L) {
                this.lastDeny.put(p.getUniqueId(), now);
                p.sendActionBar(Text.mm("<red>This land is claimed by <white>" + Text.esc(this.claims.ownerName(c)) + "</white>."));
                Snd.error(p);
            }

            return true;
        } else {
            return false;
        }
    }

    private static Player playerOf(Entity e) {
        if (e instanceof Player p) {
            return p;
        } else {
            return e instanceof Projectile pr && pr.getShooter() instanceof Player s ? s : null;
        }
    }

    private static Claim.Trust need(Entity e) {
        if (e instanceof ArmorStand || e instanceof Hanging) {
            return Claim.Trust.BUILD;
        } else {
            return !(e instanceof InventoryHolder) && !(e instanceof Animals) && !(e instanceof Allay) && !(e instanceof Golem) ? null : Claim.Trust.CONTAINER;
        }
    }

    private static Claim.Trust need(Block b) {
        Material m = b.getType();
        if (b.getState(false) instanceof InventoryHolder) {
            return Claim.Trust.CONTAINER;
        } else if (!Tag.DOORS.isTagged(m)
            && !Tag.TRAPDOORS.isTagged(m)
            && !Tag.FENCE_GATES.isTagged(m)
            && !Tag.BUTTONS.isTagged(m)
            && !Tag.BEDS.isTagged(m)
            && m != Material.LEVER
            && m != Material.BELL
            && m != Material.CRAFTING_TABLE
            && m != Material.ENCHANTING_TABLE
            && m != Material.ENDER_CHEST) {
            return m.isInteractable() ? Claim.Trust.BUILD : null;
        } else {
            return Claim.Trust.ACCESS;
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onBreak(BlockBreakEvent e) {
        if (this.denied(e.getPlayer(), e.getBlock().getLocation(), Claim.Trust.BUILD)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onPlace(BlockPlaceEvent e) {
        if (e instanceof BlockMultiPlaceEvent multi) {
            for (BlockState s : multi.getReplacedBlockStates()) {
                if (this.denied(e.getPlayer(), s.getLocation(), Claim.Trust.BUILD)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        if (this.denied(e.getPlayer(), e.getBlockPlaced().getLocation(), Claim.Trust.BUILD)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (this.denied(e.getPlayer(), e.getBlock().getLocation(), Claim.Trust.BUILD)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onBucket(PlayerBucketFillEvent e) {
        if (this.denied(e.getPlayer(), e.getBlock().getLocation(), Claim.Trust.BUILD)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onBucketEntity(PlayerBucketEntityEvent e) {
        if (this.denied(e.getPlayer(), e.getEntity().getLocation(), Claim.Trust.CONTAINER)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b != null && (e.getItem() == null || e.getItem().getType() != ClaimManager.TOOL)) {
            Claim.Trust need = null;
            if (e.getAction() == Action.PHYSICAL) {
                need = b.getType() == Material.FARMLAND ? Claim.Trust.BUILD : Claim.Trust.ACCESS;
            } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                need = need(b);
            }

            if (need != null && this.denied(e.getPlayer(), b.getLocation(), need)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onFertilize(BlockFertilizeEvent e) {
        if (e.getPlayer() != null && this.denied(e.getPlayer(), e.getBlock().getLocation(), Claim.Trust.BUILD)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onSign(SignChangeEvent e) {
        if (this.denied(e.getPlayer(), e.getBlock().getLocation(), Claim.Trust.BUILD)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onLectern(PlayerTakeLecternBookEvent e) {
        if (this.denied(e.getPlayer(), e.getLectern().getLocation(), Claim.Trust.CONTAINER)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onEntityPlace(EntityPlaceEvent e) {
        if (e.getPlayer() != null && this.denied(e.getPlayer(), e.getEntity().getLocation(), Claim.Trust.BUILD)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null && this.denied(e.getPlayer(), e.getEntity().getLocation(), Claim.Trust.BUILD)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Player p = e.getRemover() == null ? null : playerOf(e.getRemover());
        if (p != null) {
            if (this.denied(p, e.getEntity().getLocation(), Claim.Trust.BUILD)) {
                e.setCancelled(true);
            }
        } else if (this.at(e.getEntity().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onHangingExplode(HangingBreakEvent e) {
        if (e.getCause() == RemoveCause.EXPLOSION && Cfg.bool(Opt.CLAIM_EXPLOSIONS) && this.at(e.getEntity().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Claim.Trust need = need(e.getRightClicked());
        if (need != null && this.denied(e.getPlayer(), e.getRightClicked().getLocation(), need)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onInteractAtEntity(PlayerInteractAtEntityEvent e) {
        Claim.Trust need = need(e.getRightClicked());
        if (need != null && this.denied(e.getPlayer(), e.getRightClicked().getLocation(), need)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (this.denied(e.getPlayer(), e.getRightClicked().getLocation(), Claim.Trust.BUILD)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onShear(PlayerShearEntityEvent e) {
        if (this.denied(e.getPlayer(), e.getEntity().getLocation(), Claim.Trust.CONTAINER)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onDamage(EntityDamageByEntityEvent e) {
        Player p = playerOf(e.getDamager());
        if (p != null && !e.getEntity().equals(p)) {
            if (e.getEntity() instanceof Player) {
                if (Cfg.bool(Opt.CLAIM_PVP) && this.at(e.getEntity().getLocation()) != null && !this.claims.bypassing(p)) {
                    e.setCancelled(true);
                    p.sendActionBar(Text.mm("<red>No PvP in claims."));
                }
            } else {
                Claim.Trust need = need(e.getEntity());
                if (need != null && this.denied(p, e.getEntity().getLocation(), need)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onVehicle(VehicleDestroyEvent e) {
        Player p = e.getAttacker() == null ? null : playerOf(e.getAttacker());
        if (p != null && this.denied(p, e.getVehicle().getLocation(), Claim.Trust.BUILD)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onChangeBlock(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (this.denied(p, e.getBlock().getLocation(), Claim.Trust.BUILD)) {
                e.setCancelled(true);
            }
        } else if ((
                e.getEntity() instanceof Enderman
                    || e.getEntity() instanceof Ravager
                    || e.getEntity() instanceof Wither
                    || e.getEntity() instanceof WitherSkull
            )
            && this.at(e.getBlock().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onExplode(EntityExplodeEvent e) {
        if (Cfg.bool(Opt.CLAIM_EXPLOSIONS)) {
            e.blockList().removeIf(b -> this.at(b.getLocation()) != null);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onExplode(BlockExplodeEvent e) {
        if (Cfg.bool(Opt.CLAIM_EXPLOSIONS)) {
            e.blockList().removeIf(b -> this.at(b.getLocation()) != null);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onBurn(BlockBurnEvent e) {
        if (this.at(e.getBlock().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getPlayer() != null) {
            if (this.denied(e.getPlayer(), e.getBlock().getLocation(), Claim.Trust.BUILD)) {
                e.setCancelled(true);
            }
        } else if (e.getCause() != IgniteCause.FLINT_AND_STEEL && this.at(e.getBlock().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onPistonExtend(BlockPistonExtendEvent e) {
        Claim home = this.at(e.getBlock().getLocation());

        for (Block b : e.getBlocks()) {
            Claim from = this.at(b.getLocation());
            Claim to = this.at(b.getRelative(e.getDirection()).getLocation());
            if (from != null && from != home || to != null && to != home) {
                e.setCancelled(true);
                return;
            }
        }

        Claim head = this.at(e.getBlock().getRelative(e.getDirection()).getLocation());
        if (head != null && head != home) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onPistonRetract(BlockPistonRetractEvent e) {
        Claim home = this.at(e.getBlock().getLocation());

        for (Block b : e.getBlocks()) {
            Claim from = this.at(b.getLocation());
            if (from != null && from != home) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onFlow(BlockFromToEvent e) {
        Claim to = this.at(e.getToBlock().getLocation());
        if (to != null && to != this.at(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }
}
