package net.vortexpvp.smp;

import io.papermc.paper.event.entity.EntityPortalReadyEvent;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent.RespawnReason;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/**
 * Portals of SMP worlds lead to that world's own nether and end ({@code <world>_nether}, {@code <world>_the_end}) and
 * back, for players and for items and mobs. Deaths in an SMP world (or its nether/end) respawn at the player's bed or
 * anchor, otherwise at the hub, never in the PvP lobby.
 */
public final class Portals implements Listener {
    private final VortexPvP plugin;
    private final ModeManager modes;

    public Portals(VortexPvP plugin, ModeManager modes) {
        this.plugin = plugin;
        this.modes = modes;
    }

    private boolean smp(World w) {
        return w != null && !this.plugin.worlds().managed(w) && this.modes.mode(w).smp();
    }

    private static World dimension(World parent, World.Environment env) {
        return Bukkit.getWorld(parent.getName() + (env == World.Environment.NETHER ? "_nether" : "_the_end"));
    }

    /** Where a nether portal in {@code from} leads, or null for vanilla behaviour. */
    private World netherTarget(World from) {
        if (!Cfg.bool(Opt.SMP_DIMENSIONS) || !this.smp(from)) {
            return null;
        } else if (from.getEnvironment() == World.Environment.NETHER) {
            World parent = this.modes.parentOf(from);
            return parent == from ? null : parent;
        } else {
            return from.getEnvironment() == World.Environment.NORMAL ? dimension(from, World.Environment.NETHER) : null;
        }
    }

    /** Vanilla's obsidian platform at (100, 49, 0) with air above it. */
    public static Location endPlatform(World end) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 2; y++) {
                    Block b = end.getBlockAt(100 + x, 50 + y, z);
                    Material want = y == -1 ? Material.OBSIDIAN : Material.AIR;
                    if (b.getType() != want) {
                        b.setType(want, false);
                    }
                }
            }
        }

        return new Location(end, 100.5, 50.0, 0.5, 90.0F, 0.0F);
    }

    /** Leaving an SMP end: the player's bed in that SMP world, else its spawn. */
    private Location home(Player p, World parent) {
        Location bed = p.getRespawnLocation();
        return bed != null && bed.getWorld() != null && this.modes.parentOf(bed.getWorld()).equals(parent) ? bed : this.modes.spawn(parent);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onReady(EntityPortalReadyEvent e) {
        if (e.getPortalType() == PortalType.NETHER) {
            World from = e.getEntity().getWorld();
            World target = this.netherTarget(from);
            if (target != null) {
                // the server scales the coordinates, finds or builds the portal on the other side
                e.setTargetWorld(target);
            } else if (Cfg.bool(Opt.SMP_DIMENSIONS) && this.smp(from) && from.getEnvironment() == World.Environment.NORMAL) {
                e.setCancelled(true);
                if (e.getEntity() instanceof Player p) {
                    p.sendActionBar(Text.mm("<red>This world's nether isn't loaded."));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        World from = e.getFrom().getWorld();
        if (Cfg.bool(Opt.SMP_DIMENSIONS) && this.smp(from)) {
            if (e.getCause() == TeleportCause.END_PORTAL) {
                if (from.getEnvironment() == World.Environment.THE_END) {
                    World parent = this.modes.parentOf(from);
                    if (parent != from) {
                        e.setTo(this.home(e.getPlayer(), parent));
                    }
                } else if (from.getEnvironment() == World.Environment.NORMAL) {
                    World end = dimension(from, World.Environment.THE_END);
                    if (end == null) {
                        e.setCancelled(true);
                        e.getPlayer().sendActionBar(Text.mm("<red>This world's end isn't loaded."));
                    } else {
                        e.setTo(endPlatform(end));
                    }
                }
            } else if (e.getCause() == TeleportCause.NETHER_PORTAL) {
                // fallback for when the ready event didn't route it: keep the player inside their SMP world
                World target = this.netherTarget(from);
                Location to = e.getTo();
                if (target != null && (to == null || !target.equals(to.getWorld()))) {
                    double scale = target.getEnvironment() == World.Environment.NETHER ? 0.125 : 8.0;
                    Location at = e.getFrom();
                    double y = Math.max(target.getMinHeight() + 2, Math.min(target.getEnvironment() == World.Environment.NETHER ? 120 : 250, at.getY()));
                    e.setTo(new Location(target, at.getX() * scale, y, at.getZ() * scale, at.getYaw(), at.getPitch()));
                    e.setCanCreatePortal(true);
                    e.setSearchRadius(target.getEnvironment() == World.Environment.NETHER ? 16 : 128);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        World from = e.getFrom().getWorld();
        if (Cfg.bool(Opt.SMP_DIMENSIONS) && this.smp(from) && e.getPortalType() == PortalType.ENDER) {
            if (from.getEnvironment() == World.Environment.THE_END) {
                World parent = this.modes.parentOf(from);
                if (parent != from) {
                    e.setTo(this.modes.spawn(parent));
                }
            } else if (from.getEnvironment() == World.Environment.NORMAL) {
                World end = dimension(from, World.Environment.THE_END);
                if (end == null) {
                    e.setCancelled(true);
                } else {
                    e.setTo(endPlatform(end));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        World died = p.getWorld();
        if (this.smp(died)) {
            World parent = this.modes.parentOf(died);
            if (e.getRespawnReason() == RespawnReason.END_PORTAL) {
                // the credits after the dragon: back to this SMP world, not the server's main world
                if (parent != died) {
                    e.setRespawnLocation(this.home(p, parent));
                }
            } else if (e.getRespawnReason() == RespawnReason.DEATH) {
                Location at = e.getRespawnLocation();
                // a bed only counts in the same SMP mode (another mode would swap the inventory group)
                boolean bed = (e.isBedSpawn() || e.isAnchorSpawn()) && at != null && this.smp(at.getWorld())
                    && this.modes.mode(at.getWorld()) == this.modes.mode(died);
                if (!bed || !Cfg.bool(Opt.SMP_RESPAWN_BED)) {
                    World hub = this.modes.hubWorld();
                    e.setRespawnLocation(hub != null ? this.modes.spawn(hub) : this.modes.spawn(parent));
                }
            }
        }
    }
}
