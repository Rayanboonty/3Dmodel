package net.cmcpvp.bot;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.cmcpvp.CMCPvP;
import net.cmcpvp.arena.Arena;
import net.cmcpvp.arena.ArenaInstance;
import net.cmcpvp.arena.ChangeTracker;
import net.cmcpvp.util.Cuboid;
import net.cmcpvp.util.Pos;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

/**
 * 1.0.16: the flat training world. Bot fights, training dummies and the Stun Slam Lab happen on a plain superflat
 * world (grass on dirt on deep stone, always day, no mobs). Every session gets its own 256-block cell with an invisible
 * barrier ring and a training pad: nothing is generated, so a session starts at once and costs almost no memory.
 * Every block changed in a cell (blocks placed and broken, explosions, liquids) is written down and put back when the
 * session ends.
 */
public final class TrainingWorld implements Listener {
    public static final String WORLD = "pvp_training";
    static final int CELL = 256;
    /** Half-size of a cell's play area (inside its barrier ring). */
    static final int R = 48;
    /** Height of the grass surface: one block of bedrock, 40 of stone, 3 of dirt, the grass. */
    static final int GROUND = -64 + 44;
    private final CMCPvP plugin;
    private World world;
    private final BitSet used = new BitSet();
    private final Map<Integer, ChangeTracker> changes = new HashMap<>();
    private final Map<Integer, UUID> owners = new HashMap<>();

    public TrainingWorld(CMCPvP plugin) {
        this.plugin = plugin;
    }

    /** The world (made the first time it is needed). */
    public World world() {
        if (this.world == null) {
            this.world = Bukkit.getWorld(WORLD);
            if (this.world == null) {
                this.plugin.getLogger().info("Creating " + WORLD + " (the flat world for bots and training)...");
                this.world = new WorldCreator(WORLD)
                    .environment(Environment.NORMAL)
                    .type(WorldType.FLAT)
                    .generatorSettings("{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:stone\",\"height\":40},"
                        + "{\"block\":\"minecraft:dirt\",\"height\":3},{\"block\":\"minecraft:grass_block\",\"height\":1}],\"biome\":\"minecraft:plains\"}")
                    .generateStructures(false)
                    .createWorld();
            }

            if (this.world != null) {
                net.cmcpvp.world.WorldManager.applyArenaRules(this.world);
                this.world.setGameRule(org.bukkit.GameRules.MOB_GRIEFING, false);
            }
        }

        return this.world;
    }

    public boolean isTraining(World w) {
        return w != null && w.getName().equals(WORLD);
    }

    /** True for a session cell handed out by this class (not a real arena). */
    public static boolean isSlot(ArenaInstance i) {
        return i != null && i.arena != null && i.arena.id.startsWith("training_slot_");
    }

    private static int slotOf(ArenaInstance i) {
        return Integer.parseInt(i.arena.id.substring("training_slot_".length()));
    }

    /** Middle of a cell. */
    static int cx(int slot) {
        return slot * CELL;
    }

    /** A cell for one session: the player's spot on the west side looking east, the far spot on the east side. */
    public ArenaInstance acquire(org.bukkit.entity.Player p) {
        World w = this.world();
        if (w == null) {
            return null;
        }

        int slot = this.used.nextClearBit(0);
        this.used.set(slot);
        this.owners.put(slot, p.getUniqueId());
        this.changes.put(slot, new ChangeTracker());
        int cx = cx(slot);
        Arena a = new Arena("training_slot_" + slot, "Training");
        a.worldName = WORLD;
        a.theme = net.cmcpvp.arena.ArenaTheme.TRAINING_GROUNDS;
        a.bounds = new Cuboid(cx - R, GROUND - 6, -R, cx + R, GROUND + 60, R);
        a.spawn1 = new Pos(cx - 10 + 0.5, GROUND + 1, 0.5, -90.0F, 0.0F);
        a.spawn2 = new Pos(cx + 10 + 0.5, GROUND + 1, 0.5, 90.0F, 0.0F);
        a.center = new Pos(cx + 0.5, GROUND + 4, 0.5, 0.0F, 20.0F);
        a.enabled = true;
        a.buildHeight = 40;
        ArenaInstance inst = new ArenaInstance(a, 0, WORLD, 0, 0);
        inst.state = ArenaInstance.State.IN_USE;
        // the cell stays loaded for the whole session (the bot is spawned before the player has arrived)
        this.tickets(w, slot, true);
        this.prepare(w, slot);
        return inst;
    }

    /** Puts back every block the session changed and frees the cell. */
    public void release(ArenaInstance i) {
        if (!isSlot(i)) {
            return;
        }

        int slot = slotOf(i);
        ChangeTracker t = this.changes.remove(slot);
        if (t != null && this.world != null) {
            ChangeTracker.restore(this.world, t.drain());
        }

        this.owners.remove(slot);
        if (this.world != null) {
            this.tickets(this.world, slot, false);
        }

        this.used.clear(slot);
    }

    private void tickets(World w, int slot, boolean add) {
        int cx = cx(slot);
        for (int x = (cx - R - 2) >> 4; x <= (cx + R + 2) >> 4; x++) {
            for (int z = (-R - 2) >> 4; z <= (R + 2) >> 4; z++) {
                if (add) {
                    w.getChunkAt(x, z).addPluginChunkTicket(this.plugin);
                } else {
                    w.removePluginChunkTicket(x, z, this.plugin);
                }
            }
        }
    }

    /** Places a block for a session (Stun Slam Lab platforms...): written down so it goes away afterwards. */
    public void place(ArenaInstance i, Block b, Material m) {
        if (isSlot(i)) {
            ChangeTracker t = this.changes.get(slotOf(i));
            if (t != null) {
                t.record(b);
            }

            b.setType(m, false);
        }
    }

    /** The barrier ring (once per cell, kept) and the training pad round the player's spot. */
    private void prepare(World w, int slot) {
        int cx = cx(slot);
        Block corner = w.getBlockAt(cx - R - 1, GROUND + 2, -R - 1);
        if (corner.getType() != Material.BARRIER) {
            for (int i = -R - 1; i <= R + 1; i++) {
                for (int y = GROUND + 1; y <= GROUND + 60; y++) {
                    w.getBlockAt(cx + i, y, -R - 1).setType(Material.BARRIER, false);
                    w.getBlockAt(cx + i, y, R + 1).setType(Material.BARRIER, false);
                    w.getBlockAt(cx - R - 1, y, i).setType(Material.BARRIER, false);
                    w.getBlockAt(cx + R + 1, y, i).setType(Material.BARRIER, false);
                }
            }

            // the pad: a grid of light stone with a red ring and a lamp in the middle, lines to the far spot
            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    double r = Math.hypot(dx, dz);
                    Material m;
                    if (r < 1.0) {
                        m = Material.SEA_LANTERN;
                    } else if (Math.abs(r - 14.0) < 0.6) {
                        m = Material.RED_CONCRETE;
                    } else if (Math.abs(r - 28.0) < 0.6) {
                        m = Material.WHITE_CONCRETE;
                    } else if (r < 14.0) {
                        m = Math.floorMod(dx, 4) == 0 || Math.floorMod(dz, 4) == 0 ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE;
                    } else {
                        continue;
                    }

                    w.getBlockAt(cx + dx, GROUND, dz).setType(m, false);
                }
            }
        }
    }

    private void record(Block b) {
        if (!this.isTraining(b.getWorld())) {
            return;
        }

        int slot = Math.floorDiv(b.getX() + CELL / 2, CELL);
        ChangeTracker t = this.changes.get(slot);
        if (t != null) {
            t.record(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (this.isTraining(e.getBlock().getWorld())) {
            int slot = Math.floorDiv(e.getBlock().getX() + CELL / 2, CELL);
            ChangeTracker t = this.changes.get(slot);
            if (t != null) {
                t.record(e.getBlockReplacedState());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        this.record(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        for (Block b : e.blockList()) {
            this.record(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        this.record(e.getBlock());
        for (Block b : e.blockList()) {
            this.record(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        this.record(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        this.record(e.getToBlock());
    }
}
