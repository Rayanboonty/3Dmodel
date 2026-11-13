package net.vortexpvp.listener;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.arena.Arena;
import net.vortexpvp.arena.ArenaGenerator;
import net.vortexpvp.arena.ArenaInstance;
import net.vortexpvp.arena.ChangeTracker;
import net.vortexpvp.config.Msg;
import net.vortexpvp.ffa.FfaArena;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.match.Match;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Session;
import net.vortexpvp.util.Cuboid;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;

public final class ArenaListener implements Listener {
    private final VortexPvP plugin;
    private Set<String> arenaWorlds = new HashSet<>();
    private long worldsBuiltAt;

    public ArenaListener(VortexPvP plugin) {
        this.plugin = plugin;
    }

    private boolean arenaWorld(World w) {
        long now = System.currentTimeMillis();
        if (now - this.worldsBuiltAt > 5000L) {
            Set<String> set = new HashSet<>();

            for (Arena a : this.plugin.arenas().all()) {
                for (ArenaInstance i : a.instances) {
                    set.add(i.worldName);
                }
            }

            for (FfaArena f : this.plugin.ffa().all()) {
                if (f.worldName != null) {
                    set.add(f.worldName);
                }
            }

            this.arenaWorlds = set;
            this.worldsBuiltAt = now;
        }

        return this.arenaWorlds.contains(w.getName());
    }

    private ChangeTracker tracker(Block b) {
        World w = b.getWorld();
        if (!this.arenaWorld(w)) {
            return null;
        } else {
            ArenaInstance inst = this.plugin.arenas().instanceAt(w, b.getX(), b.getY(), b.getZ());
            if (inst != null) {
                return inst.tracker;
            } else {
                FfaArena f = this.plugin.ffa().arenaAt(w, b.getX(), b.getY(), b.getZ());
                return f == null ? null : f.tracker;
            }
        }
    }

    private void record(Block b) {
        ChangeTracker t = this.tracker(b);
        if (t != null) {
            t.record(b);
        }
    }

    private void record(BlockState s) {
        ChangeTracker t = this.tracker(s.getBlock());
        if (t != null) {
            t.record(s);
        }
    }

    private Boolean mayBuild(Player p, Block b) {
        Session s = this.plugin.sessions().get(p);
        if (s.state == PlayerState.MATCH) {
            Match m = s.match;
            if (m != null && m.instance() != null && m.state() == Match.State.FIGHTING && m.kit.build && m.isAlive(p.getUniqueId())) {
                Cuboid c = m.instance().bounds();
                ArenaInstance inst = m.instance();
                return inst.contains(b.getLocation())
                    && b.getY() <= inst.maxBuildY()
                    && c != null
                    && ArenaGenerator.onFloor(inst.arena.theme, inst.arena.size, inst.arena.bounds, b.getX() - inst.dx, b.getZ() - inst.dz);
            } else {
                return false;
            }
        } else if (s.state == PlayerState.BOT) {
            return this.plugin.bots() == null ? Boolean.FALSE : this.plugin.bots().mayBuild(p, b);
        } else if (s.state == PlayerState.FFA) {
            FfaArena a = s.ffa;
            Kit k = a == null ? null : this.plugin.ffa().kitOf(a);
            return a != null && k != null && k.build && a.contains(b.getLocation()) && ArenaGenerator.onFloor(a.theme, a.size, a.bounds, b.getX(), b.getZ());
        } else {
            return null;
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onPlace(BlockPlaceEvent e) {
        Boolean ok = this.mayBuild(e.getPlayer(), e.getBlockPlaced());
        if (ok != null) {
            if (ok) {
                e.setBuild(true);
                e.setCancelled(false);
            } else {
                e.setCancelled(true);
                Session s = this.plugin.sessions().get(e.getPlayer());
                if (s.match != null && s.match.state() == Match.State.FIGHTING && s.match.kit.build) {
                    e.getPlayer().sendActionBar(Msg.get("match.build-limit"));
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onPlaceMonitor(BlockPlaceEvent e) {
        if (e instanceof BlockMultiPlaceEvent multi) {
            for (BlockState st : multi.getReplacedBlockStates()) {
                this.record(st);
            }
        } else {
            this.record(e.getBlockReplacedState());
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onBreak(BlockBreakEvent e) {
        Boolean ok = this.mayBuild(e.getPlayer(), e.getBlock());
        if (ok != null) {
            e.setCancelled(!ok);
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onBreakMonitor(BlockBreakEvent e) {
        this.record(e.getBlock());
        Block above = e.getBlock().getRelative(0, 1, 0);
        if (!above.getType().isSolid()) {
            this.record(above);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Boolean ok = this.mayBuild(e.getPlayer(), e.getBlock());
        if (ok != null) {
            e.setCancelled(!ok);
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onBucketEmptyMonitor(PlayerBucketEmptyEvent e) {
        this.record(e.getBlock());
        this.record(e.getBlockClicked());
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onBucketFill(PlayerBucketFillEvent e) {
        Boolean ok = this.mayBuild(e.getPlayer(), e.getBlock());
        if (ok != null) {
            e.setCancelled(!ok);
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onBucketFillMonitor(PlayerBucketFillEvent e) {
        this.record(e.getBlock());
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onInteractBlock(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.useInteractedBlock() != Result.DENY) {
            if (this.plugin.sessions().state(e.getPlayer()).fighting()) {
                this.record(e.getClickedBlock());
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onEntityPlace(EntityPlaceEvent e) {
        Player p = e.getPlayer();
        if (p != null) {
            Session s = this.plugin.sessions().get(p);
            if (s.state == PlayerState.MATCH) {
                Match m = s.match;
                if (m == null || m.state() != Match.State.FIGHTING || m.instance() == null || !m.instance().contains(e.getEntity().getLocation())) {
                    e.setCancelled(true);
                }
            } else if (s.state == PlayerState.FFA) {
                if (s.ffa == null || !s.ffa.contains(e.getEntity().getLocation())) {
                    e.setCancelled(true);
                }
            } else if (s.state != PlayerState.OUTSIDE && (!s.buildMode || s.state != PlayerState.LOBBY)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onEggSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == SpawnReason.SPAWNER_EGG) {
            Location l = e.getLocation();
            if (this.plugin.worlds().managed(l.getWorld())) {
                ArenaInstance inst = this.plugin.arenas().instanceAt(l);
                if (inst == null || !(inst.owner instanceof Match m && m.state() == Match.State.FIGHTING)) {
                    if (this.plugin.ffa().arenaAt(l) == null) {
                        e.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onEntityExplode(EntityExplodeEvent e) {
        this.handleExplosion(e.getLocation(), e.blockList());
        if (this.inArena(e.getLocation())) {
            e.setYield(0.0F);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onBlockExplode(BlockExplodeEvent e) {
        this.handleExplosion(e.getBlock().getLocation(), e.blockList());
        if (this.inArena(e.getBlock().getLocation())) {
            e.setYield(0.0F);
            this.record(e.getExplodedBlockState());
        }
    }

    private boolean inArena(Location l) {
        return this.arenaWorld(l.getWorld()) && (this.plugin.arenas().instanceAt(l) != null || this.plugin.ffa().arenaAt(l) != null);
    }

    private void handleExplosion(Location origin, List<Block> blocks) {
        if (this.arenaWorld(origin.getWorld())) {
            ArenaInstance inst = this.plugin.arenas().instanceAt(origin);
            FfaArena f = inst == null ? this.plugin.ffa().arenaAt(origin) : null;
            if (inst != null || f != null) {
                Iterator<Block> it = blocks.iterator();

                while (it.hasNext()) {
                    Block b = it.next();
                    boolean inside = inst != null ? inst.contains(b.getLocation()) : f.contains(b.getLocation());
                    if (inside && inst != null) {
                        inside = ArenaGenerator.onFloor(inst.arena.theme, inst.arena.size, inst.arena.bounds, b.getX() - inst.dx, b.getZ() - inst.dz);
                    } else if (inside) {
                        inside = ArenaGenerator.onFloor(f.theme, f.size, f.bounds, b.getX(), b.getZ());
                    }

                    if (!inside) {
                        it.remove();
                    } else {
                        (inst != null ? inst.tracker : f.tracker).record(b);
                    }
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onFlow(BlockFromToEvent e) {
        Block to = e.getToBlock();
        if (this.arenaWorld(to.getWorld())) {
            ChangeTracker fromT = this.tracker(e.getBlock());
            ChangeTracker toT = this.tracker(to);
            if (fromT != null && toT == null) {
                e.setCancelled(true);
            } else {
                if (toT != null) {
                    toT.record(to);
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onIgnite(BlockIgniteEvent e) {
        this.record(e.getBlock());
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onBurn(BlockBurnEvent e) {
        this.record(e.getBlock());
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onSpread(BlockSpreadEvent e) {
        this.record(e.getBlock());
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onForm(BlockFormEvent e) {
        this.record(e.getBlock());
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onFade(BlockFadeEvent e) {
        this.record(e.getBlock());
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onGrow(BlockGrowEvent e) {
        this.record(e.getBlock());
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onFertilize(BlockFertilizeEvent e) {
        this.record(e.getBlock());

        for (BlockState st : e.getBlocks()) {
            this.record(st.getBlock());
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onStructure(StructureGrowEvent e) {
        for (BlockState st : e.getBlocks()) {
            this.record(st.getBlock());
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onDecay(LeavesDecayEvent e) {
        if (this.tracker(e.getBlock()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onChangeBlock(EntityChangeBlockEvent e) {
        this.record(e.getBlock());
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onPistonExtend(BlockPistonExtendEvent e) {
        this.record(e.getBlock());

        for (Block b : e.getBlocks()) {
            this.record(b);
            this.record(b.getRelative(e.getDirection()));
        }

        this.record(e.getBlock().getRelative(e.getDirection()));
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onPistonRetract(BlockPistonRetractEvent e) {
        this.record(e.getBlock());

        for (Block b : e.getBlocks()) {
            this.record(b);
            this.record(b.getRelative(e.getDirection()));
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onSponge(SpongeAbsorbEvent e) {
        this.record(e.getBlock());

        for (BlockState st : e.getBlocks()) {
            this.record(st.getBlock());
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onTntPrime(TNTPrimeEvent e) {
        this.record(e.getBlock());
    }
}
