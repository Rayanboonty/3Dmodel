package net.practicepvp.match;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import net.practicepvp.arena.ChangeTracker;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class Cage {
    static final NamespacedKey KEY = new NamespacedKey("vortexrealm", "cage");
    final Player player;
    private final Map<Block, BlockData> original = new LinkedHashMap<>();
    final Set<Block> gate = new LinkedHashSet<>();
    Block chest;
    ItemFrame leave;
    private TextDisplay count;
    private TextDisplay hint;
    private final List<Entity> entities = new ArrayList<>();
    boolean ready;
    private boolean removed;
    private int fx;
    private int fz;
    private int rx;
    private int rz;
    private int bx;
    private int by;
    private int bz;
    private World world;

    Cage(Player player) {
        this.player = player;
    }

    private Block at(int a, int b, int y) {
        return this.world.getBlockAt(this.bx + this.fx * a + this.rx * b, this.by + y, this.bz + this.fz * a + this.rz * b);
    }

    private Location point(double a, double b, double y) {
        return new Location(this.world, this.bx + 0.5 + this.fx * a + this.rx * b, this.by + y, this.bz + 0.5 + this.fz * a + this.rz * b);
    }

    private void set(ChangeTracker tracker, Block block, BlockData data) {
        if (!this.original.containsKey(block)) {
            if (tracker != null) {
                tracker.record(block);
            }

            this.original.put(block, block.getBlockData());
        }

        block.setBlockData(data, false);
    }

    private static BlockFace face(int x, int z) {
        if (x > 0) {
            return BlockFace.EAST;
        } else if (x < 0) {
            return BlockFace.WEST;
        } else {
            return z > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        }
    }

    private static String wallBlock(int a, int b, int y) {
        int h = Math.floorMod(a * 73 + b * 31 + y * 17, 11);
        return h == 0 ? "cracked_stone_bricks" : (h == 1 ? "mossy_stone_bricks" : "stone_bricks");
    }

    void build(Location spawn, Location toward, ChangeTracker tracker) {
        this.world = spawn.getWorld();
        this.bx = spawn.getBlockX();
        this.by = spawn.getBlockY();
        this.bz = spawn.getBlockZ();
        double dx = toward == null ? 0.0 : toward.getX() - spawn.getX();
        double dz = toward == null ? 1.0 : toward.getZ() - spawn.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            this.fx = dx >= 0.0 ? 1 : -1;
            this.fz = 0;
        } else {
            this.fx = 0;
            this.fz = dz >= 0.0 ? 1 : -1;
        }

        this.rx = -this.fz;
        this.rz = this.fx;
        BlockData air = Material.AIR.createBlockData();

        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) {
                boolean wall = Math.abs(a) == 2 || Math.abs(b) == 2;
                this.set(tracker, this.at(a, b, -1), Material.STONE_BRICKS.createBlockData());
                this.set(tracker, this.at(a, b, 4), (wall ? Material.STONE_BRICKS : Material.CHISELED_STONE_BRICKS).createBlockData());

                for (int y = 0; y <= 3; y++) {
                    Block bl = this.at(a, b, y);
                    if (!wall) {
                        this.set(tracker, bl, air);
                    } else if (a == 2 && Math.abs(b) <= 1 && y <= 2) {
                        this.set(tracker, bl, Material.DARK_OAK_FENCE.createBlockData());
                        this.gate.add(bl);
                    } else if (a == 2 && y == 3) {
                        this.set(tracker, bl, (b == 0 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS).createBlockData());
                    } else {
                        this.set(tracker, bl, Bukkit.createBlockData(wallBlock(a, b, y)));
                    }
                }
            }
        }

        for (Block g : this.gate) {
            g.setType(Material.DARK_OAK_FENCE, true);
        }

        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a != 0 || b != -1) {
                    this.set(tracker, this.at(a, b, 0), Material.RED_CARPET.createBlockData());
                }
            }
        }

        this.chest = this.at(0, -1, 0);
        BlockData chestData = Material.CHEST.createBlockData();
        if (chestData instanceof Directional d) {
            d.setFacing(face(this.rx, this.rz));
        }

        this.set(tracker, this.chest, chestData);
        Block frameAt = this.at(0, 1, 1);

        try {
            this.leave = this.world.spawn(frameAt.getLocation(), ItemFrame.class, f -> {
                f.setFacingDirection(face(-this.rx, -this.rz), true);
                f.setItem(new ItemStack(Material.BARRIER), false);
                f.setVisible(false);
                f.setFixed(true);
                f.setPersistent(false);
                f.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte)1);
            });
            this.entities.add(this.leave);
        } catch (RuntimeException var14) {
        }

        this.entities.add(this.label(this.point(0.0, -1.0, 1.55), "<#FFE24D><b>Kit Layout</b>\n<gray>Crouch + Right Click", 0.75F));
        this.entities.add(this.label(this.point(0.0, 1.0, 2.1), "<#FF5555><b>Leave Duel</b>\n<gray>Crouch + Right Click", 0.75F));
        this.count = this.label(this.point(1.45, 0.0, 2.05), "", 2.2F);
        this.hint = this.label(this.point(1.45, 0.0, 1.45), "<#FFE24D>Hit the gate to ready up!", 0.85F);
        this.entities.add(this.count);
        this.entities.add(this.hint);
        Snd.at(this.point(0.0, 0.0, 1.0), "block.stone.place", 1.0F, 0.7F);
    }

    private TextDisplay label(Location l, String mini, float scale) {
        return this.world.spawn(l, TextDisplay.class, t -> {
            t.text(Text.mm(mini));
            t.setBillboard(Billboard.CENTER);
            t.setAlignment(TextAlignment.CENTER);
            t.setShadowed(true);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setBrightness(new Brightness(15, 15));
            t.setPersistent(false);
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
            t.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte)1);
        });
    }

    void countdown(int left) {
        if (this.count != null && this.count.isValid()) {
            this.count.text(Text.mm((left <= 3 ? "<#FF5A2A>" : "<#FFA726>") + "<b>" + left));
        }
    }

    void markReady(String waitingFor) {
        this.ready = true;
        if (this.hint != null && this.hint.isValid()) {
            this.hint.text(Text.mm(waitingFor == null ? "<#3DDC84>✔ Ready!" : "<#3DDC84>✔ Ready <gray>- waiting for <white>" + Text.esc(waitingFor)));
        }
    }

    boolean isGate(Block b) {
        return this.gate.contains(b);
    }

    void open() {
        TreeMap<Integer, List<Block>> layers = new TreeMap<>();

        for (Block b : this.gate) {
            layers.computeIfAbsent(-b.getY(), k -> new ArrayList<>()).add(b);
        }

        int delay = 0;

        for (List<Block> layer : layers.values()) {
            Tasks.later(
                delay,
                () -> {
                    if (!this.removed) {
                        for (Block bx : layer) {
                            BlockData was = this.original.get(bx);
                            bx.setBlockData(was != null ? was : Material.AIR.createBlockData(), false);
                            bx.getWorld()
                                .spawnParticle(
                                    Particle.BLOCK, bx.getLocation().add(0.5, 0.5, 0.5), 6, 0.25, 0.25, 0.25, 0.0, Material.DARK_OAK_FENCE.createBlockData()
                                );
                        }

                        Snd.at(layer.get(0).getLocation(), "block.fence_gate.open", 1.0F, 0.8F);
                    }
                }
            );
            delay += 3;
        }

        for (Entity e : this.entities) {
            if (e instanceof TextDisplay) {
                e.remove();
            }
        }

        Tasks.later(50L, this::remove);
    }

    void remove() {
        if (!this.removed) {
            this.removed = true;

            for (Entity e : this.entities) {
                if (e.isValid()) {
                    e.remove();
                }
            }

            this.entities.clear();

            for (Entry<Block, BlockData> ex : this.original.entrySet()) {
                ex.getKey().setBlockData(ex.getValue(), false);
            }

            this.original.clear();
            this.gate.clear();
        }
    }
}
