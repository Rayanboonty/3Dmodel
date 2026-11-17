package net.vortexrealm.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.vortexrealm.util.Locs;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected.Half;

public final class ChangeTracker {
    private final Map<Long, ChangeTracker.Change> changes = new LinkedHashMap<>();
    private static final BlockFace[] SIDES = new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    public void record(Block b) {
        this.record(b.getX(), b.getY(), b.getZ(), b.getBlockData());
        if (b.getBlockData() instanceof Bisected bi) {
            Block other = b.getRelative(bi.getHalf() == Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN);
            if (other.getType() == b.getType()) {
                this.record(other.getX(), other.getY(), other.getZ(), other.getBlockData());
            }
        }

        Block up = b;

        for (int i = 0; i < 4; i++) {
            up = up.getRelative(BlockFace.UP);
            Material m = up.getType();
            if (m.isAir() || !dependent(m) && !m.hasGravity()) {
                break;
            }

            this.record(up.getX(), up.getY(), up.getZ(), up.getBlockData());
        }

        for (BlockFace f : SIDES) {
            Block s = b.getRelative(f);
            if (!s.getType().isAir() && dependent(s.getType())) {
                this.record(s.getX(), s.getY(), s.getZ(), s.getBlockData());
            }
        }
    }

    private static boolean dependent(Material m) {
        return !m.isSolid() && m != Material.WATER && m != Material.LAVA && m != Material.BUBBLE_COLUMN && m != Material.FIRE;
    }

    public void record(BlockState state) {
        this.record(state.getX(), state.getY(), state.getZ(), state.getBlockData());
    }

    public void record(int x, int y, int z, BlockData original) {
        long key = Locs.pack(x, y, z);
        if (!this.changes.containsKey(key)) {
            this.changes.put(key, new ChangeTracker.Change(x, y, z, original.clone(), System.currentTimeMillis()));
        }
    }

    public boolean isEmpty() {
        return this.changes.isEmpty();
    }

    public int size() {
        return this.changes.size();
    }

    public List<ChangeTracker.Change> drain() {
        List<ChangeTracker.Change> out = new ArrayList<>(this.changes.values());
        this.changes.clear();
        return out;
    }

    public List<ChangeTracker.Change> drainOlderThan(long maxAgeMs) {
        long now = System.currentTimeMillis();
        List<ChangeTracker.Change> out = new ArrayList<>();
        this.changes.values().removeIf(c -> {
            if (now - c.time() >= maxAgeMs) {
                out.add(c);
                return true;
            } else {
                return false;
            }
        });
        return out;
    }

    public void requeue(ChangeTracker.Change c) {
        this.changes.putIfAbsent(Locs.pack(c.x(), c.y(), c.z()), c);
    }

    public static void restore(World w, List<ChangeTracker.Change> list) {
        for (ChangeTracker.Change c : list) {
            Block b = w.getBlockAt(c.x(), c.y(), c.z());
            if (!b.getBlockData().equals(c.original())) {
                b.setBlockData(c.original(), false);
            }
        }
    }

    public record Change(int x, int y, int z, BlockData original, long time) {
    }
}
