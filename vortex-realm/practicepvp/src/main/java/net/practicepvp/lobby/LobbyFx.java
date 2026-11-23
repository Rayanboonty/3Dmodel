package net.practicepvp.lobby;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.practicepvp.PracticePlugin;
import net.practicepvp.session.PlayerState;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

/**
 * Lobby extras of the big spawns (3.15.0). A spawn build hands over its spots (features "pad:name" with
 * "padto:name", "fx:kind:n" and "pk:..."), they are kept in lobby-fx.yml:
 *
 * <ul>
 *   <li>launch pads: a gold plate that throws you in an arc to another part of the lobby (across The Fracture's
 *   chasm to the Hall of Champions, to the FFA Colosseum, the Training Yard, the Arrival), no fall damage;</li>
 *   <li>ambience: glowing motes rising out of the chasm, sparks round the crystal heart, a shimmer over the pads -
 *   sent only to the players near them;</li>
 *   <li>the spawn's parkour course becomes the hub parkour (timer, checkpoints, top 10) of the plus module.</li>
 * </ul>
 */
public final class LobbyFx implements Listener {
    private final PracticePlugin plugin;
    private final File file;
    private final Random random = new Random();
    private String world;
    private final Map<Long, Vector> pads = new HashMap<>();
    private final List<Location> padSpots = new ArrayList<>();
    /** {x, y, z, kind}: 0 chasm, 1 crystal heart. */
    private final List<double[]> fx = new ArrayList<>();
    private final Map<UUID, Long> launched = new HashMap<>();

    public LobbyFx(PracticePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "lobby-fx.yml");
        this.load();
        Tasks.timer(60L, 10L, this::tick);
    }

    private static long key(int x, int y, int z) {
        return ((long)x & 0x3FFFFFFL) << 38 | ((long)z & 0x3FFFFFFL) << 12 | (long)y & 0xFFFL;
    }

    // ------------------------------------------------------------------ setup

    /** Takes the spots of a fresh spawn build (replacing the ones of an older build of this lobby). */
    public void install(World w, Map<String, Location> features) {
        boolean any = features.keySet().stream().anyMatch(k -> k.startsWith("pad:") || k.startsWith("fx:") || k.startsWith("pk:"));
        if (!any) {
            return;
        }

        this.world = w.getName();
        this.pads.clear();
        this.padSpots.clear();
        this.fx.clear();
        YamlConfiguration y = new YamlConfiguration();
        y.set("world", this.world);
        Location pkStart = null;
        List<Location> course = new ArrayList<>();
        for (Map.Entry<String, Location> f : features.entrySet()) {
            String k = f.getKey();
            Location l = f.getValue();
            if (k.startsWith("pad:")) {
                Location to = features.get("padto:" + k.substring(4));
                if (to != null) {
                    String base = "pads." + k.substring(4);
                    y.set(base + ".at", l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
                    y.set(base + ".to", to.getBlockX() + "," + to.getBlockY() + "," + to.getBlockZ());
                }
            } else if (k.startsWith("fx:")) {
                y.set("fx." + k.substring(3).replace(':', '_'), l.getX() + "," + l.getY() + "," + l.getZ() + "," + (k.startsWith("fx:heart") ? 1 : 0));
            } else if (k.equals("pk:start")) {
                pkStart = l;
            } else if (k.startsWith("pk:")) {
                course.add(l);
            }
        }

        try {
            y.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().warning("Could not save lobby-fx.yml: " + e.getMessage());
        }

        this.load();
        if (pkStart != null && this.plugin.plus() != null && this.plugin.plus().hub() != null) {
            course.add(pkStart);
            this.plugin.plus().hub().adoptCourse(w, pkStart, course);
        }
    }

    private void load() {
        this.pads.clear();
        this.padSpots.clear();
        this.fx.clear();
        if (!this.file.isFile()) {
            return;
        }

        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        this.world = y.getString("world");
        ConfigurationSection ps = y.getConfigurationSection("pads");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                int[] a = ints(ps.getString(id + ".at"));
                int[] b = ints(ps.getString(id + ".to"));
                if (a != null && b != null) {
                    this.pads.put(key(a[0], a[1], a[2]), launch(a, b));
                    this.padSpots.add(new Location(null, a[0] + 0.5, a[1] + 0.2, a[2] + 0.5));
                }
            }
        }

        ConfigurationSection fs = y.getConfigurationSection("fx");
        if (fs != null) {
            for (String id : fs.getKeys(false)) {
                String[] p = fs.getString(id, "").split(",");
                if (p.length == 4) {
                    try {
                        this.fx.add(new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])});
                    } catch (NumberFormatException ignored) {
                        // a bad line: skip it
                    }
                }
            }
        }
    }

    private static int[] ints(String s) {
        if (s == null) {
            return null;
        }

        String[] p = s.split(",");
        if (p.length != 3) {
            return null;
        }

        try {
            return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The push that lands a player from one pad on the target: a fixed lift for the distance, then the forward speed
     * that the game's air drag (x0.91 a tick) and gravity carry over that distance, found by simulating the flight.
     */
    static Vector launch(int[] from, int[] to) {
        double dx = to[0] - from[0];
        double dz = to[2] - from[2];
        double dy = to[1] - from[1];
        double dist = Math.max(1.0, Math.hypot(dx, dz));
        double vy = Math.min(2.6, 1.0 + dist * 0.03);
        double lo = 0.05;
        double hi = 3.9;
        for (int i = 0; i < 40; i++) {
            double vh = (lo + hi) / 2.0;
            if (reach(vh, vy, dy) < dist) {
                lo = vh;
            } else {
                hi = vh;
            }
        }

        double vh = (lo + hi) / 2.0;
        return new Vector(dx / dist * vh, vy, dz / dist * vh);
    }

    private static double reach(double vh, double vy, double dy) {
        double x = 0.0;
        double y = 0.0;
        for (int t = 0; t < 400; t++) {
            x += vh;
            y += vy;
            vy = (vy - 0.08) * 0.98;
            vh *= 0.91;
            if (vy < 0.0 && y <= dy) {
                return x;
            }
        }

        return x;
    }

    // ------------------------------------------------------------------ pads

    private boolean inLobby(Player p) {
        PlayerState s = this.plugin.sessions().state(p);
        return s == PlayerState.LOBBY || s == PlayerState.QUEUE;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onStep(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL || e.getClickedBlock() == null || this.pads.isEmpty()) {
            return;
        }

        Block b = e.getClickedBlock();
        if (b.getType() != Material.LIGHT_WEIGHTED_PRESSURE_PLATE || !b.getWorld().getName().equals(this.world)) {
            return;
        }

        Vector v = this.pads.get(key(b.getX(), b.getY(), b.getZ()));
        Player p = e.getPlayer();
        if (v == null || !this.inLobby(p)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = this.launched.get(p.getUniqueId());
        if (last != null && now - last < 1500L) {
            return;
        }

        this.launched.put(p.getUniqueId(), now);
        Tasks.sync(() -> {
            p.setVelocity(v.clone());
            Snd.play(p, "entity.firework_rocket.launch", 1.0F, 0.9F);
            Snd.play(p, "entity.breeze.jump", 0.8F, 1.2F);
            p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation().add(0.0, 0.3, 0.0), 24, 0.35, 0.1, 0.35, 0.08);
        });
    }

    /** No fall damage for 8 seconds after a launch. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p) {
            Long t = this.launched.get(p.getUniqueId());
            if (t != null && System.currentTimeMillis() - t < 8000L) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.launched.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ ambience

    private void tick() {
        if (this.world == null || this.fx.isEmpty() && this.padSpots.isEmpty()) {
            return;
        }

        World w = Bukkit.getWorld(this.world);
        if (w == null) {
            return;
        }

        for (Player p : w.getPlayers()) {
            if (!this.inLobby(p)) {
                continue;
            }

            Location at = p.getLocation();
            for (double[] f : this.fx) {
                double dx = f[0] - at.getX();
                double dz = f[2] - at.getZ();
                if (dx * dx + dz * dz > 64.0 * 64.0) {
                    continue;
                }

                if (f[3] == 1.0) {
                    // the crystal heart: sparks circling and rising
                    double a = this.random.nextDouble() * Math.PI * 2.0;
                    p.spawnParticle(Particle.END_ROD, f[0] + Math.cos(a) * 5.0, f[1] + this.random.nextDouble() * 3.0, f[2] + Math.sin(a) * 5.0, 0, 0.0, 1.0, 0.0, 0.04);
                    p.spawnParticle(Particle.ENCHANT, f[0], f[1] + 1.0, f[2], 6, 2.5, 1.5, 2.5, 0.6);
                } else {
                    // the chasm: glowing motes drifting up out of the dark
                    for (int k = 0; k < 2; k++) {
                        double ox = (this.random.nextDouble() - 0.5) * 10.0;
                        double oz = (this.random.nextDouble() - 0.5) * 8.0;
                        p.spawnParticle(this.random.nextInt(4) == 0 ? Particle.SOUL_FIRE_FLAME : Particle.END_ROD, f[0] + ox, f[1], f[2] + oz, 0, 0.0, 1.0, 0.0, 0.12);
                    }
                }
            }

            for (Location pad : this.padSpots) {
                double dx = pad.getX() - at.getX();
                double dz = pad.getZ() - at.getZ();
                if (dx * dx + dz * dz < 40.0 * 40.0) {
                    p.spawnParticle(Particle.HAPPY_VILLAGER, pad.getX(), pad.getY() + 0.3, pad.getZ(), 3, 0.4, 0.2, 0.4, 0.0);
                }
            }
        }
    }
}
