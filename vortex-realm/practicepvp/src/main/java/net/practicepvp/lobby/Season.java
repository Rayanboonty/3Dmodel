package net.practicepvp.lobby;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.practicepvp.PracticePlugin;
import net.practicepvp.arena.ArenaManager;
import net.practicepvp.arena.BlockJobs;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.util.Cuboid;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Snowable;
import org.bukkit.block.data.type.Snow;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * The winter lobby. When winter is on ({@code lobby.season}: WINTER, or AUTO from 1 December to 15 January), the
 * whole lobby is snowed in: snow on the ground, roofs and trees, snowy grass, frozen water, plants under the snow and
 * a few snowmen, with snow falling around the players. Every block it changes is written down first, so turning
 * winter off puts each one back exactly as it was.
 */
public final class Season {
    /** The lobby box and its world. */
    private record Area(String world, Cuboid box) {
    }

    private final PracticePlugin plugin;
    private final File record;
    private volatile boolean busy;

    public Season(PracticePlugin plugin) {
        this.plugin = plugin;
        this.record = new File(plugin.getDataFolder(), "winter-blocks.txt.gz");
    }

    public void start() {
        Tasks.timer(400L, 6000L, this::refresh);
        Tasks.timer(60L, 8L, this::snowfall);
    }

    public boolean applied() {
        return this.record.isFile();
    }

    /** Whether the lobby should be wintry now. */
    public boolean wanted() {
        String mode = Cfg.s(Opt.LOBBY_SEASON).trim().toUpperCase(Locale.ROOT);
        if (mode.equals("WINTER") || mode.equals("ON")) {
            return true;
        } else if (mode.equals("AUTO")) {
            LocalDate d = LocalDate.now();
            return d.getMonthValue() == 12 || d.getMonthValue() == 1 && d.getDayOfMonth() <= 15;
        } else {
            return false;
        }
    }

    /** Snows the lobby in, or thaws it, to match the setting. */
    public void refresh() {
        if (this.busy) {
            return;
        }

        if (this.wanted() && !this.applied()) {
            this.apply(null);
        } else if (!this.wanted() && this.applied()) {
            this.revert(null);
        }
    }

    /** After the lobby was rebuilt: the old record no longer matches the blocks there. */
    public void forget() {
        try {
            Files.deleteIfExists(this.record.toPath());
        } catch (IOException ignored) {
        }
    }

    public String status() {
        return "winter " + (this.applied() ? "on" : "off") + " (setting " + Cfg.s(Opt.LOBBY_SEASON) + (this.busy ? ", working..." : "") + ")";
    }

    /** The lobby: the box of the last lobby build, else a square around the lobby spawn. */
    private Area area() {
        File f = new File(this.plugin.getDataFolder(), "hub-build.yml");
        int r = Cfg.i(Opt.LOBBY_SEASON_RADIUS);
        if (f.isFile()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            World w = Bukkit.getWorld(y.getString("world", ""));
            if (w != null) {
                int x = y.getInt("x");
                int yy = y.getInt("y");
                int z = y.getInt("z");
                int rad = Math.max(r, y.getInt("radius", r));
                return new Area(w.getName(), Cuboid.of(x - rad, yy - 30, z - rad, x + rad, yy + 70, z + rad));
            }
        }

        Location l = this.plugin.lobby() == null ? null : this.plugin.lobby().lobby();
        if (l == null || l.getWorld() == null) {
            return null;
        }

        return new Area(l.getWorld().getName(), Cuboid.of(l.getBlockX() - r, l.getBlockY() - 30, l.getBlockZ() - r, l.getBlockX() + r, l.getBlockY() + 70, l.getBlockZ() + r));
    }

    private static boolean plant(Material m) {
        String n = m.name();
        return m == Material.SHORT_GRASS || m == Material.TALL_GRASS || m == Material.FERN || m == Material.LARGE_FERN || m == Material.DEAD_BUSH
            || m == Material.PINK_PETALS || m == Material.WILDFLOWERS || m == Material.LEAF_LITTER
            || n.endsWith("_TULIP") || m == Material.POPPY || m == Material.DANDELION || m == Material.CORNFLOWER || m == Material.OXEYE_DAISY
            || m == Material.ALLIUM || m == Material.AZURE_BLUET || m == Material.BLUE_ORCHID || m == Material.LILY_OF_THE_VALLEY;
    }

    public void apply(CommandSender who) {
        Area a = this.area();
        if (a == null) {
            this.tell(who, "<red>No lobby found to snow in (build one, or set the lobby spawn).");
            return;
        }

        Cuboid c = a.box();
        World w = Bukkit.getWorld(a.world());
        if (w == null) {
            return;
        }

        this.busy = true;
        List<String> changed = new ArrayList<>();
        this.tell(who, "<aqua>❄ <gray>Snowing the lobby in...");
        ArenaManager.preload(w, c).thenComposeAsync(v -> this.plugin.jobs().submit(new BlockJobs.Job() {
            int x = c.minX();
            int z = c.minZ();
            int snowmen;

            @Override
            public int step(int budget) {
                int used = 0;
                while (this.x <= c.maxX() && used < budget) {
                    used += Season.this.winterColumn(w, this.x, this.z, c, changed);
                    if (this.snowmen < 10 && Math.floorMod(this.x * 31 + this.z * 17, 997) == 0) {
                        this.snowmen += Season.this.snowman(w, this.x, this.z, c, changed) ? 1 : 0;
                    }

                    if (++this.z > c.maxZ()) {
                        this.z = c.minZ();
                        this.x++;
                    }

                    if ((used & 63) == 0 && BlockJobs.overDeadline()) {
                        break;
                    }
                }

                return used;
            }

            @Override
            public boolean done() {
                return this.x > c.maxX();
            }

            @Override
            public String name() {
                return "winter";
            }
        }), Tasks.MAIN).whenComplete((v, err) -> Tasks.ensureSync(() -> {
            this.busy = false;
            if (err != null) {
                this.plugin.getLogger().log(Level.WARNING, "Snowing the lobby in failed", err);
                this.tell(who, "<red>Snowing the lobby in failed - see the console.");
                return;
            }

            try {
                this.write(w.getName(), changed);
                this.tell(who, "<aqua>❄ <white>Winter is here! <gray>" + changed.size() + " blocks snowed in (all of them come back when winter ends).");
                this.plugin.getLogger().info("Winter lobby: " + changed.size() + " blocks changed.");
            } catch (IOException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Couldn't save the winter record - thawing now so nothing is lost", e);
                this.restore(w, changed, who);
            }
        }));
    }

    private void record(List<String> out, Block b) {
        out.add(b.getX() + " " + b.getY() + " " + b.getZ() + " " + b.getBlockData().getAsString());
    }

    /** Snows one column in; returns the work done. */
    private int winterColumn(World w, int x, int z, Cuboid c, List<String> out) {
        int top = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        if (top < c.minY() || top > c.maxY()) {
            return 1;
        }

        Block b = w.getBlockAt(x, top, z);
        Block above = b.getRelative(BlockFace.UP);
        Material m = b.getType();
        Material am = above.getType();
        int layers = 1 + (Math.floorMod(x * 7 + z * 13, 5) == 0 ? 1 : 0);
        boolean groundOk = m.isOccluding() || m.name().endsWith("_LEAVES") || m == Material.FARMLAND || m == Material.DIRT_PATH;
        if (m == Material.WATER) {
            this.record(out, b);
            b.setType(Material.ICE, false);
            return 4;
        }

        if (!groundOk || !(am.isAir() || plant(am))) {
            return 2;
        }

        if (b.getBlockData() instanceof Snowable s) {
            this.record(out, b);
            s.setSnowy(true);
            b.setBlockData(s, false);
        }

        if (m == Material.FARMLAND) {
            // crops keep growing under winter? no: the bed is left alone, so the gardens still show their crops
            return 2;
        }

        this.record(out, above);
        Snow snow = (Snow)Material.SNOW.createBlockData();
        snow.setLayers(layers);
        above.setBlockData(snow, false);
        return 4;
    }

    /** A snowman on open snowy ground: two snow blocks and a carved pumpkin. */
    private boolean snowman(World w, int x, int z, Cuboid c, List<String> out) {
        int top = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        Block g = w.getBlockAt(x, top, z);
        if (g.getType() != Material.GRASS_BLOCK || top < c.minY() || top + 4 > c.maxY()) {
            return false;
        }

        for (int k = 1; k <= 3; k++) {
            Material at = g.getRelative(BlockFace.UP, k).getType();
            if (!at.isAir() && at != Material.SNOW && !plant(at)) {
                return false;
            }
        }

        String[] faces = {"north", "south", "east", "west"};
        for (int k = 1; k <= 3; k++) {
            Block b = g.getRelative(BlockFace.UP, k);
            // the snow layer placed a moment ago is already in the record; keep its original (air) and replace it
            if (k > 1) {
                this.record(out, b);
            }

            b.setBlockData(k < 3 ? Material.SNOW_BLOCK.createBlockData() : Bukkit.createBlockData("minecraft:carved_pumpkin[facing=" + faces[Math.floorMod(x + z, 4)] + "]"), false);
        }

        return true;
    }

    public void revert(CommandSender who) {
        List<String> lines;
        String world;
        try {
            lines = this.read();
            world = lines.isEmpty() ? null : lines.remove(0);
        } catch (IOException e) {
            this.tell(who, "<red>Couldn't read the winter record: " + Text.esc(e.getMessage()));
            return;
        }

        World w = world == null ? null : Bukkit.getWorld(world);
        if (w == null) {
            this.forget();
            return;
        }

        this.restore(w, lines, who);
    }

    /** Puts the recorded blocks back, last change first. */
    private void restore(World w, List<String> lines, CommandSender who) {
        if (lines.isEmpty()) {
            this.forget();
            return;
        }

        this.busy = true;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (String l : lines) {
            String[] p = l.split(" ", 4);
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int z = Integer.parseInt(p[2]);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        Cuboid c = Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ);
        this.tell(who, "<gray>Thawing the lobby...");
        ArenaManager.preload(w, c).thenComposeAsync(v -> this.plugin.jobs().submit(new BlockJobs.Job() {
            int i = lines.size() - 1;

            @Override
            public int step(int budget) {
                int used = 0;
                while (this.i >= 0 && used < budget) {
                    String[] p = lines.get(this.i--).split(" ", 4);
                    try {
                        w.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])).setBlockData(Bukkit.createBlockData(p[3]), false);
                    } catch (IllegalArgumentException ignored) {
                    }

                    used += 3;
                }

                return used;
            }

            @Override
            public boolean done() {
                return this.i < 0;
            }

            @Override
            public String name() {
                return "thaw";
            }
        }), Tasks.MAIN).whenComplete((v, err) -> Tasks.ensureSync(() -> {
            this.busy = false;
            if (err != null) {
                this.plugin.getLogger().log(Level.WARNING, "Thawing the lobby failed", err);
                this.tell(who, "<red>Thawing failed - see the console. The record is kept, try again with /pvpadmin winter off.");
                return;
            }

            this.forget();
            this.tell(who, "<green>☀ <gray>Winter is over - the lobby is back to how it was.");
        }));
    }

    private void write(String world, List<String> lines) throws IOException {
        File tmp = new File(this.record.getPath() + ".tmp");
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(tmp.toPath())), StandardCharsets.UTF_8))) {
            out.write(world);
            out.newLine();
            for (String l : lines) {
                out.write(l);
                out.newLine();
            }
        }

        Files.move(tmp.toPath(), this.record.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private List<String> read() throws IOException {
        List<String> out = new ArrayList<>();
        if (!this.record.isFile()) {
            return out;
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(this.record.toPath())), StandardCharsets.UTF_8))) {
            String l;
            while ((l = in.readLine()) != null) {
                if (!l.isBlank()) {
                    out.add(l);
                }
            }
        }

        return out;
    }

    /** Snow falling around the players in a wintry lobby. */
    private void snowfall() {
        if (!this.applied() || !Cfg.bool(Opt.LOBBY_SNOWFALL)) {
            return;
        }

        Area a = this.area();
        if (a == null) {
            return;
        }

        Cuboid c = a.box();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            if (!p.getWorld().getName().equals(a.world()) || !c.contains(l.getBlockX(), l.getBlockY(), l.getBlockZ())) {
                continue;
            }

            for (int i = 0; i < 14; i++) {
                p.spawnParticle(Particle.SNOWFLAKE, l.getX() + r.nextDouble(-12.0, 12.0), l.getY() + r.nextDouble(4.0, 12.0), l.getZ() + r.nextDouble(-12.0, 12.0), 0, 0.0, -0.4, 0.0, 0.3);
            }
        }
    }

    private void tell(CommandSender who, String mm) {
        if (who != null) {
            who.sendMessage(Text.mm(mm));
        }
    }
}
