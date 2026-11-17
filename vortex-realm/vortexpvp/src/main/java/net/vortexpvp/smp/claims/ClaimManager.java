package net.vortexpvp.smp.claims;

import io.papermc.paper.registry.data.dialog.ActionButton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.dialog.Dialogs;
import net.vortexpvp.dialog.LobbyDialogs;
import net.vortexpvp.smp.Essentials;
import net.vortexpvp.smp.ModeManager;
import net.vortexpvp.smp.WorldMode;
import net.vortexpvp.util.ClientVersion;
import net.vortexpvp.util.Fmt;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class ClaimManager implements Listener, TabExecutor {
    public static final Material TOOL = Material.GOLDEN_SHOVEL;
    private static final DustOptions OWN = new DustOptions(Color.fromRGB(255, 190, 40), 1.3F);
    private static final DustOptions TRUSTED = new DustOptions(Color.fromRGB(80, 230, 80), 1.3F);
    private static final DustOptions OTHER = new DustOptions(Color.fromRGB(255, 70, 70), 1.3F);
    private static final DustOptions ADMIN = new DustOptions(Color.fromRGB(80, 200, 255), 1.3F);
    private static final DustOptions CORNER = new DustOptions(Color.fromRGB(255, 255, 255), 1.6F);
    private final VortexPvP plugin;
    private final ModeManager modes;
    private final File file;
    private final Map<Integer, Claim> claims = new LinkedHashMap<>();
    private final Map<String, Map<Long, List<Claim>>> index = new HashMap<>();
    private final Map<UUID, ClaimManager.Blocks> blocks = new HashMap<>();
    private final Map<UUID, Location> corner = new HashMap<>();
    private final Map<UUID, int[]> resizing = new HashMap<>();
    private final Map<UUID, Integer> resizingId = new HashMap<>();
    private final Map<UUID, ClaimManager.Showing> showing = new HashMap<>();
    private final Map<UUID, Integer> inClaim = new HashMap<>();
    private final Map<UUID, Location> lastSeen = new HashMap<>();
    private final Set<UUID> bypass = new HashSet<>();
    private final Set<UUID> adminMode = new HashSet<>();
    private int nextId = 1;
    private boolean dirty;

    public ClaimManager(VortexPvP plugin, ModeManager modes) {
        this.plugin = plugin;
        this.modes = modes;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
    }

    public void load() {
        this.claims.clear();
        this.index.clear();
        this.blocks.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        this.nextId = y.getInt("next-id", 1);
        ConfigurationSection cs = y.getConfigurationSection("claims");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                ConfigurationSection s = cs.getConfigurationSection(k);
                if (s != null) {
                    try {
                        String[] b = s.getString("box", "").split(";");
                        String owner = s.getString("owner");
                        Claim c = new Claim(
                            Integer.parseInt(k),
                            owner != null && !owner.isEmpty() ? UUID.fromString(owner) : null,
                            s.getString("world"),
                            Integer.parseInt(b[0]),
                            Integer.parseInt(b[1]),
                            Integer.parseInt(b[2]),
                            Integer.parseInt(b[3])
                        );
                        c.name = s.getString("name", "");
                        c.created = s.getLong("created", System.currentTimeMillis());
                        ConfigurationSection t = s.getConfigurationSection("trust");
                        if (t != null) {
                            for (String u : t.getKeys(false)) {
                                c.trusted.put(UUID.fromString(u), Claim.Trust.parse(t.getString(u), Claim.Trust.BUILD));
                            }
                        }

                        this.claims.put(c.id, c);
                        this.addIndex(c);
                        this.nextId = Math.max(this.nextId, c.id + 1);
                    } catch (RuntimeException var13) {
                        this.plugin.getLogger().warning("claims.yml: claim " + k + " is invalid and was skipped (" + var13 + ")");
                    }
                }
            }
        }

        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps != null) {
            for (String kx : ps.getKeys(false)) {
                try {
                    ClaimManager.Blocks bl = new ClaimManager.Blocks();
                    bl.earned = ps.getInt(kx + ".earned");
                    bl.bonus = ps.getInt(kx + ".bonus");
                    this.blocks.put(UUID.fromString(kx), bl);
                } catch (IllegalArgumentException var12) {
                }
            }
        }

        this.plugin.getLogger().info("Loaded " + this.claims.size() + " land claims.");
    }

    public void save() {
        if (this.dirty) {
            this.dirty = false;
            YamlConfiguration y = new YamlConfiguration();
            y.options().setHeader(List.of("Land claims. box = minX;minZ;maxX;maxZ (full height). Edit in game."));
            y.set("next-id", this.nextId);

            for (Claim c : this.claims.values()) {
                String b = "claims." + c.id + ".";
                y.set(b + "owner", c.owner == null ? "" : c.owner.toString());
                y.set(b + "world", c.world);
                y.set(b + "box", c.serializeBox());
                y.set(b + "name", c.name);
                y.set(b + "created", c.created);

                for (Entry<UUID, Claim.Trust> t : c.trusted.entrySet()) {
                    y.set(b + "trust." + t.getKey(), t.getValue().name());
                }
            }

            for (Entry<UUID, ClaimManager.Blocks> e : this.blocks.entrySet()) {
                y.set("players." + e.getKey() + ".earned", e.getValue().earned);
                y.set("players." + e.getKey() + ".bonus", e.getValue().bonus);
            }

            try {
                y.save(this.file);
            } catch (IOException var7) {
                this.dirty = true;
                this.plugin.getLogger().log(Level.WARNING, "Could not save claims.yml", (Throwable)var7);
            }
        }
    }

    private static long key(int cx, int cz) {
        return (long)cx << 32 ^ cz & 4294967295L;
    }

    private void addIndex(Claim c) {
        Map<Long, List<Claim>> m = this.index.computeIfAbsent(c.world, k -> new HashMap<>());

        for (int cx = c.minX >> 4; cx <= c.maxX >> 4; cx++) {
            for (int cz = c.minZ >> 4; cz <= c.maxZ >> 4; cz++) {
                m.computeIfAbsent(key(cx, cz), k -> new ArrayList<>(2)).add(c);
            }
        }
    }

    private void removeIndex(Claim c) {
        Map<Long, List<Claim>> m = this.index.get(c.world);
        if (m != null) {
            for (int cx = c.minX >> 4; cx <= c.maxX >> 4; cx++) {
                for (int cz = c.minZ >> 4; cz <= c.maxZ >> 4; cz++) {
                    List<Claim> l = m.get(key(cx, cz));
                    if (l != null) {
                        l.remove(c);
                        if (l.isEmpty()) {
                            m.remove(key(cx, cz));
                        }
                    }
                }
            }
        }
    }

    public boolean hasClaims(World w) {
        Map<Long, List<Claim>> m = this.index.get(w.getName());
        return m != null && !m.isEmpty();
    }

    public Claim claimAt(World w, int x, int z) {
        Map<Long, List<Claim>> m = this.index.get(w.getName());
        if (m == null) {
            return null;
        } else {
            List<Claim> l = m.get(key(x >> 4, z >> 4));
            if (l == null) {
                return null;
            } else {
                for (Claim c : l) {
                    if (c.contains(x, z)) {
                        return c;
                    }
                }

                return null;
            }
        }
    }

    public Claim claimAt(Location l) {
        return l != null && l.getWorld() != null ? this.claimAt(l.getWorld(), l.getBlockX(), l.getBlockZ()) : null;
    }

    public boolean active(World w) {
        if (Cfg.bool(Opt.CLAIMS) && w != null && !this.plugin.worlds().managed(w)) {
            WorldMode m = this.modes.mode(w);
            return m.smp() && Essentials.modeListed(Cfg.s(Opt.CLAIM_MODES), m.name());
        } else {
            return false;
        }
    }

    public boolean bypassing(Player p) {
        return this.bypass.contains(p.getUniqueId());
    }

    public boolean mayUse(Player p, Location l) {
        Claim c = this.claimAt(l);
        return c == null || this.bypassing(p) || c.allows(p.getUniqueId(), Claim.Trust.ACCESS);
    }

    public Collection<Claim> all() {
        return this.claims.values();
    }

    private List<Claim> ownedBy(UUID u) {
        List<Claim> out = new ArrayList<>();

        for (Claim c : this.claims.values()) {
            if (u.equals(c.owner)) {
                out.add(c);
            }
        }

        return out;
    }

    private String modeOf(String world) {
        World w = Bukkit.getWorld(world);
        return w == null ? "?" : this.modes.mode(w).name();
    }

    private ClaimManager.Blocks blocksOf(UUID u) {
        return this.blocks.computeIfAbsent(u, k -> new ClaimManager.Blocks());
    }

    public int total(UUID u) {
        ClaimManager.Blocks b = this.blocksOf(u);
        return Cfg.i(Opt.CLAIM_START_BLOCKS) + b.earned + b.bonus;
    }

    public int used(UUID u) {
        int n = 0;

        for (Claim c : this.claims.values()) {
            if (u.equals(c.owner)) {
                n += c.area();
            }
        }

        return n;
    }

    public int remaining(UUID u) {
        return this.total(u) - this.used(u);
    }

    public void accrue() {
        double perMinute = Cfg.i(Opt.CLAIM_BLOCKS_HOUR) / 60.0;
        int cap = Cfg.i(Opt.CLAIM_MAX_EARNED);

        for (Player p : Bukkit.getOnlinePlayers()) {
            Location now = p.getLocation();
            Location before = this.lastSeen.put(p.getUniqueId(), now);
            if (this.active(p.getWorld()) && before != null && before.getWorld().equals(now.getWorld()) && !(before.distanceSquared(now) < 4.0)) {
                ClaimManager.Blocks b = this.blocksOf(p.getUniqueId());
                if (b.earned < cap) {
                    b.partial += perMinute;
                    int whole = (int)b.partial;
                    if (whole > 0) {
                        b.partial -= whole;
                        b.earned = Math.min(cap, b.earned + whole);
                        this.dirty = true;
                    }
                }
            }
        }
    }

    private boolean nearSpawn(World w, int x1, int z1, int x2, int z2) {
        int d = Cfg.i(Opt.CLAIM_SPAWN_DISTANCE);
        if (d <= 0) {
            return false;
        } else {
            String n = w.getName();

            for (String suffix : List.of("_nether", "_the_end")) {
                if (n.endsWith(suffix) && Bukkit.getWorld(n.substring(0, n.length() - suffix.length())) != null) {
                    return false;
                }
            }

            Location s = this.modes.spawn(w);
            double cx = Math.max((double)Math.min(x1, x2), Math.min(s.getX(), (double)Math.max(x1, x2)));
            double cz = Math.max((double)Math.min(z1, z2), Math.min(s.getZ(), (double)Math.max(z1, z2)));
            return (cx - s.getX()) * (cx - s.getX()) + (cz - s.getZ()) * (cz - s.getZ()) < (double)d * d;
        }
    }

    private Claim overlapping(String world, int x1, int z1, int x2, int z2, Claim ignore) {
        Map<Long, List<Claim>> m = this.index.get(world);
        if (m == null) {
            return null;
        } else {
            for (int cx = Math.min(x1, x2) >> 4; cx <= Math.max(x1, x2) >> 4; cx++) {
                for (int cz = Math.min(z1, z2) >> 4; cz <= Math.max(z1, z2) >> 4; cz++) {
                    List<Claim> l = m.get(key(cx, cz));
                    if (l != null) {
                        for (Claim c : l) {
                            if (c != ignore && c.overlaps(x1, z1, x2, z2)) {
                                return c;
                            }
                        }
                    }
                }
            }

            return null;
        }
    }

    public Claim create(Player p, World w, int x1, int z1, int x2, int z2) {
        if (!this.active(w)) {
            err(p, "You can't claim land in this world.");
            return null;
        } else {
            boolean admin = this.adminMode.contains(p.getUniqueId()) && p.hasPermission("vortexpvp.claims.admin");
            int width = Math.abs(x2 - x1) + 1;
            int length = Math.abs(z2 - z1) + 1;
            int min = Cfg.i(Opt.CLAIM_MIN_WIDTH);
            if (admin || width >= min && length >= min) {
                Claim hit = this.overlapping(w.getName(), x1, z1, x2, z2, null);
                if (hit != null) {
                    err(p, "That overlaps " + this.ownerName(hit) + "'s claim.");
                    this.show(p, List.of(hit), 10);
                    return null;
                } else if (!admin && this.nearSpawn(w, x1, z1, x2, z2) && !p.hasPermission("vortexpvp.claims.admin")) {
                    err(p, "Too close to spawn (" + Cfg.i(Opt.CLAIM_SPAWN_DISTANCE) + " blocks are protected).");
                    return null;
                } else {
                    if (!admin) {
                        int area = width * length;
                        int left = this.remaining(p.getUniqueId());
                        if (area > left) {
                            err(
                                p,
                                "You need "
                                    + Fmt.num(area)
                                    + " claim blocks but have "
                                    + Fmt.num(Math.max(0, left))
                                    + ". Play more to earn blocks, or claim a smaller area."
                            );
                            return null;
                        }

                        String mode = this.modes.mode(w).name();
                        long inMode = this.ownedBy(p.getUniqueId()).stream().filter(cx -> mode.equals(this.modeOf(cx.world))).count();
                        if (inMode >= Cfg.i(Opt.CLAIM_MAX_CLAIMS)) {
                            err(p, "You already have " + inMode + " claims here (max " + Cfg.i(Opt.CLAIM_MAX_CLAIMS) + ").");
                            return null;
                        }
                    }

                    Claim c = new Claim(this.nextId++, admin ? null : p.getUniqueId(), w.getName(), x1, z1, x2, z2);
                    this.claims.put(c.id, c);
                    this.addIndex(c);
                    this.dirty = true;
                    this.save();
                    Snd.play(p, "entity.player.levelup", 0.6F, 1.6F);
                    p.sendMessage(
                        Text.mm(
                            "<green>✔ <b>Land claimed!</b> <gray>"
                                + c.width()
                                + "x"
                                + c.length()
                                + " ("
                                + Fmt.num(c.area())
                                + " blocks)"
                                + (admin ? " <aqua>[admin claim]" : " <dark_gray>• <gray>" + Fmt.num(this.remaining(p.getUniqueId())) + " blocks left")
                        )
                    );
                    p.sendMessage(
                        Text.mm(
                            "<gray>Trust friends with <gold>/trust <name></gold> <dark_gray>• <gray>manage: <gold><click:run_command:'/claims'>/claims</click>"
                        )
                    );
                    this.show(p, List.of(c), 15);
                    return c;
                }
            } else {
                err(p, "Claims must be at least " + min + "x" + min + " (this one is " + width + "x" + length + ").");
                return null;
            }
        }
    }

    private void resize(Player p, Claim c, int[] fixed, int x, int z) {
        int x1 = fixed[0];
        int z1 = fixed[1];
        int width = Math.abs(x - x1) + 1;
        int length = Math.abs(z - z1) + 1;
        int min = Cfg.i(Opt.CLAIM_MIN_WIDTH);
        if (c.admin() || width >= min && length >= min) {
            Claim hit = this.overlapping(c.world, x1, z1, x, z, c);
            if (hit != null) {
                err(p, "That overlaps " + this.ownerName(hit) + "'s claim.");
            } else {
                if (!c.admin()) {
                    int extra = width * length - c.area();
                    if (extra > this.remaining(p.getUniqueId())) {
                        err(p, "You need " + Fmt.num(extra) + " more claim blocks for that.");
                        return;
                    }
                }

                this.removeIndex(c);
                c.setBox(x1, z1, x, z);
                this.addIndex(c);
                this.dirty = true;
                this.save();
                Snd.play(p, "block.note_block.pling", 0.7F, 1.5F);
                p.sendMessage(
                    Text.mm(
                        "<green>✔ Claim resized to <white>"
                            + c.width()
                            + "x"
                            + c.length()
                            + "</white>."
                            + (c.admin() ? "" : " <gray>" + Fmt.num(this.remaining(p.getUniqueId())) + " blocks left")
                    )
                );
                this.show(p, List.of(c), 15);
            }
        } else {
            err(p, "Claims must be at least " + min + "x" + min + ".");
        }
    }

    public void abandon(Player p, Claim c) {
        this.removeIndex(c);
        this.claims.remove(c.id);
        this.dirty = true;
        this.save();
        Snd.play(p, "block.note_block.bass", 0.7F, 0.8F);
        p.sendMessage(Text.mm("<gray>Claim abandoned. <dark_gray>(" + Fmt.num(c.area()) + " blocks back)"));
    }

    public String ownerName(Claim c) {
        if (c.owner == null) {
            return "the server";
        } else {
            OfflinePlayer o = Bukkit.getOfflinePlayer(c.owner);
            return o.getName() == null ? c.owner.toString().substring(0, 8) : o.getName();
        }
    }

    private static void err(Player p, String text) {
        p.sendMessage(Text.mm("<red>" + Text.esc(text)));
        Snd.error(p);
    }

    @EventHandler(
        priority = EventPriority.LOW
    )
    public void onTool(PlayerInteractEvent e) {
        if (e.getHand() == EquipmentSlot.HAND && e.getItem() != null && e.getItem().getType() == TOOL) {
            Player p = e.getPlayer();
            if (this.active(p.getWorld())) {
                if (e.getAction() != Action.LEFT_CLICK_AIR && e.getAction() != Action.LEFT_CLICK_BLOCK) {
                    if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_AIR) {
                        e.setCancelled(true);
                        Block b = e.getClickedBlock();
                        if (b == null) {
                            b = p.getTargetBlockExact(80);
                            if (b == null) {
                                p.sendActionBar(Text.mm("<gray>Right-click the ground (or aim at it) to pick a corner."));
                                return;
                            }
                        }

                        int x = b.getX();
                        int z = b.getZ();
                        Integer resizeId = this.resizingId.get(p.getUniqueId());
                        if (resizeId != null) {
                            Claim c = this.claims.get(resizeId);
                            int[] fixed = this.resizing.remove(p.getUniqueId());
                            this.resizingId.remove(p.getUniqueId());
                            if (c != null && fixed != null) {
                                this.resize(p, c, fixed, x, z);
                            }
                        } else {
                            Location first = this.corner.get(p.getUniqueId());
                            if (first == null) {
                                Claim here = this.claimAt(p.getWorld(), x, z);
                                if (here == null) {
                                    this.corner.put(p.getUniqueId(), b.getLocation());
                                    Snd.play(p, "block.note_block.hat", 0.8F, 1.6F);
                                    p.sendMessage(
                                        Text.mm(
                                            "<gold>Corner 1 set <gray>("
                                                + x
                                                + ", "
                                                + z
                                                + "). Now right-click the opposite corner. <dark_gray>• <gray>You have <white>"
                                                + Fmt.num(Math.max(0, this.remaining(p.getUniqueId())))
                                                + "</white> claim blocks."
                                        )
                                    );
                                } else {
                                    boolean mine = p.getUniqueId().equals(here.owner) || here.admin() && p.hasPermission("vortexpvp.claims.admin");
                                    if (mine && here.isCorner(x, z)) {
                                        int fx = x == here.minX ? here.maxX : here.minX;
                                        int fz = z == here.minZ ? here.maxZ : here.minZ;
                                        this.resizing.put(p.getUniqueId(), new int[]{fx, fz});
                                        this.resizingId.put(p.getUniqueId(), here.id);
                                        p.sendMessage(
                                            Text.mm(
                                                "<gold>Resizing your claim: <gray>right-click where this corner should go. <dark_gray>(left-click to cancel)"
                                            )
                                        );
                                        Snd.click(p);
                                    } else {
                                        this.info(p, here);
                                    }

                                    this.show(p, List.of(here), 10);
                                }
                            } else {
                                this.corner.remove(p.getUniqueId());
                                if (!first.getWorld().equals(p.getWorld())) {
                                    err(p, "Both corners must be in the same world.");
                                } else {
                                    this.create(p, p.getWorld(), first.getBlockX(), first.getBlockZ(), x, z);
                                }
                            }
                        }
                    }
                } else {
                    if (this.corner.remove(p.getUniqueId()) != null || this.resizingId.remove(p.getUniqueId()) != null) {
                        this.resizing.remove(p.getUniqueId());
                        p.sendActionBar(Text.mm("<gray>Selection cleared."));
                    }
                }
            }
        }
    }

    public void show(Player p, List<Claim> list, int seconds) {
        this.showing.put(p.getUniqueId(), new ClaimManager.Showing(new ArrayList<>(list), System.currentTimeMillis() + seconds * 1000L));
    }

    public void tick() {
        long now = System.currentTimeMillis();

        for (Player p : Bukkit.getOnlinePlayers()) {
            ClaimManager.Showing s = this.showing.get(p.getUniqueId());
            if (s != null && s.until() < now) {
                this.showing.remove(p.getUniqueId());
                s = null;
            }

            boolean holding = p.getInventory().getItemInMainHand().getType() == TOOL && this.active(p.getWorld());
            List<Claim> draw = new ArrayList<>();
            if (s != null) {
                draw.addAll(s.claims());
            }

            if (holding) {
                for (Claim c : this.near(p.getLocation(), 64)) {
                    if (!draw.contains(c)) {
                        draw.add(c);
                    }
                }

                Location first = this.corner.get(p.getUniqueId());
                p.sendActionBar(
                    Text.mm(
                        "<gold>⛏ Claim blocks: <white>"
                            + Fmt.num(Math.max(0, this.remaining(p.getUniqueId())))
                            + "</white>"
                            + (
                                first != null
                                    ? " <dark_gray>• <yellow>corner 1: " + first.getBlockX() + ", " + first.getBlockZ()
                                    : " <dark_gray>• <gray>right-click 2 corners"
                            )
                    )
                );
                Location first2 = this.corner.get(p.getUniqueId());
                if (first2 != null && first2.getWorld().equals(p.getWorld())) {
                    p.spawnParticle(Particle.DUST, first2.getX() + 0.5, first2.getY() + 1.2, first2.getZ() + 0.5, 6, 0.15, 0.4, 0.15, 0.0, CORNER);
                }
            }

            for (Claim cx : draw) {
                this.outline(p, cx);
            }
        }
    }

    private List<Claim> near(Location l, int radius) {
        List<Claim> out = new ArrayList<>();
        Map<Long, List<Claim>> m = this.index.get(l.getWorld().getName());
        if (m == null) {
            return out;
        } else {
            int r = (radius >> 4) + 1;
            int cx = l.getBlockX() >> 4;
            int cz = l.getBlockZ() >> 4;

            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    List<Claim> list = m.get(key(x, z));
                    if (list != null) {
                        for (Claim c : list) {
                            if (!out.contains(c)) {
                                out.add(c);
                            }
                        }
                    }
                }
            }

            return out;
        }
    }

    private void outline(Player p, Claim c) {
        World w = p.getWorld();
        if (w.getName().equals(c.world)) {
            DustOptions color = c.admin() ? ADMIN : (p.getUniqueId().equals(c.owner) ? OWN : (c.trustOf(p.getUniqueId()) != null ? TRUSTED : OTHER));
            Location me = p.getLocation();
            double max2 = 1600.0;
            List<int[]> pts = new ArrayList<>();

            for (int x = c.minX; x <= c.maxX; x += 2) {
                pts.add(new int[]{x, c.minZ});
                pts.add(new int[]{x, c.maxZ});
            }

            for (int z = c.minZ; z <= c.maxZ; z += 2) {
                pts.add(new int[]{c.minX, z});
                pts.add(new int[]{c.maxX, z});
            }

            for (int[] pt : pts) {
                double dx = pt[0] + 0.5 - me.getX();
                double dz = pt[1] + 0.5 - me.getZ();
                if (!(dx * dx + dz * dz > max2) && w.isChunkLoaded(pt[0] >> 4, pt[1] >> 4)) {
                    int y = w.getHighestBlockYAt(pt[0], pt[1], HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                    if (Math.abs(y - me.getY()) > 24.0) {
                        y = (int)me.getY();
                    }

                    boolean isCorner = c.isCorner(pt[0], pt[1]);
                    p.spawnParticle(
                        Particle.DUST, pt[0] + 0.5, y + 0.15, pt[1] + 0.5, isCorner ? 4 : 1, 0.0, isCorner ? 0.6 : 0.0, 0.0, 0.0, isCorner ? CORNER : color
                    );
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        Location from = e.getFrom();
        if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ() || !from.getWorld().equals(to.getWorld())) {
            Player p = e.getPlayer();
            Claim c = this.hasClaims(to.getWorld()) ? this.claimAt(to) : null;
            Integer was = this.inClaim.get(p.getUniqueId());
            int now = c == null ? -1 : c.id;
            if (was == null || was != now) {
                this.inClaim.put(p.getUniqueId(), now);
                if (Cfg.bool(Opt.CLAIM_ENTER_MESSAGE) && (was != null || c != null)) {
                    if (c != null) {
                        String who = c.admin()
                            ? "<aqua>Protected area"
                            : (p.getUniqueId().equals(c.owner) ? "<gold>Your claim" : "<white>" + Text.esc(this.ownerName(c)) + "<gray>'s claim");
                        p.sendActionBar(Text.mm("<gray>Entering " + who + (c.name.isEmpty() ? "" : " <dark_gray>(" + Text.esc(c.name) + ")")));
                    } else if (was != -1) {
                        p.sendActionBar(Text.mm("<gray>Leaving the claim - <green>wilderness"));
                    }
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        this.corner.remove(u);
        this.resizing.remove(u);
        this.resizingId.remove(u);
        this.showing.remove(u);
        this.inClaim.remove(u);
        this.lastSeen.remove(u);
    }

    private void info(Player p, Claim c) {
        p.sendMessage(
            Text.mm(
                "<gold><b>Claim #"
                    + c.id
                    + "</b> <gray>by <white>"
                    + Text.esc(this.ownerName(c))
                    + "</white> <dark_gray>• <gray>"
                    + c.width()
                    + "x"
                    + c.length()
                    + " <dark_gray>• <gray>"
                    + c.minX
                    + ", "
                    + c.minZ
                    + " → "
                    + c.maxX
                    + ", "
                    + c.maxZ
            )
        );
        Claim.Trust t = c.trustOf(p.getUniqueId());
        p.sendMessage(Text.mm("<gray>Your access: " + (t == null ? "<red>none" : "<green>" + t.name().toLowerCase(Locale.ROOT))));
    }

    public void menu(Player p) {
        List<Claim> mine = this.ownedBy(p.getUniqueId());
        if (ClientVersion.supportsDialogs(p)) {
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();

            for (Claim c : mine) {
                World w = Bukkit.getWorld(c.world);
                String where = (w == null ? c.world : ModeManager.worldLabel(w)) + " " + (c.minX + c.maxX) / 2 + ", " + (c.minZ + c.maxZ) / 2;
                List<Component> hover = new ArrayList<>();
                hover.add(Text.mm("<gold><b>" + (c.name.isEmpty() ? "Claim #" + c.id : Text.esc(c.name))));
                hover.add(Text.mm("<gray>" + Text.esc(where)));
                hover.add(Text.mm("<gray>Size: <white>" + c.width() + "x" + c.length() + " <dark_gray>(" + Fmt.num(c.area()) + " blocks)"));
                hover.add(Text.mm("<gray>Trusted: <white>" + c.trusted.size()));
                hover.add(Component.empty());
                hover.add(Text.mm("<green>▶ Click to manage"));
                tiles.add(
                    new LobbyDialogs.Tile(
                        new ItemStack(Material.GRASS_BLOCK),
                        Text.mm("<gold><b>" + (c.name.isEmpty() ? "Claim #" + c.id : Text.esc(c.name))),
                        Text.mm("<gray>" + Text.esc(where) + " <dark_gray>• <white>" + c.width() + "x" + c.length()),
                        hover,
                        () -> this.manage(p, c)
                    )
                );
            }

            LobbyDialogs ui = this.plugin.dialogs().lobby();
            List<ActionButton> buttons = new ArrayList<>();
            buttons.add(ui.btn(p, "<gold>⛏ How To Claim", null, () -> {
                p.closeDialog();
                this.howTo(p);
            }));
            Claim here = this.claimAt(p.getLocation());
            if (here != null) {
                buttons.add(ui.btn(p, "<aqua>ⓘ Claim Here", null, () -> {
                    p.closeDialog();
                    this.info(p, here);
                    this.show(p, List.of(here), 15);
                }));
            }

            int total = this.total(p.getUniqueId());
            int used = this.used(p.getUniqueId());
            ui.screen(
                p,
                LobbyDialogs.title("Claims", "Your protected land"),
                tiles,
                List.of(
                    Text.mm(
                        "<gray>Claim blocks: <white>"
                            + Fmt.num(total - used)
                            + "</white> left <dark_gray>("
                            + Fmt.num(used)
                            + " used of "
                            + Fmt.num(total)
                            + ") <dark_gray>• <gray>+"
                            + Cfg.i(Opt.CLAIM_BLOCKS_HOUR)
                            + "/hour played"
                    )
                ),
                Text.mm("<gray>No claims yet. Right-click two corners with a <gold>golden shovel</gold> or use <gold>/claim</gold>."),
                buttons,
                2,
                ui.close(p)
            );
        } else {
            p.sendMessage(Text.mm("<gold>Your claims (" + mine.size() + ") <dark_gray>• <gray>blocks left: <white>" + Fmt.num(this.remaining(p.getUniqueId()))));

            for (Claim c : mine) {
                p.sendMessage(Text.mm("<gray>#" + c.id + " " + c.world + " " + c.minX + ", " + c.minZ + " (" + c.width() + "x" + c.length() + ")"));
            }
        }
    }

    private void manage(Player p, Claim c) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (Entry<UUID, Claim.Trust> t : c.trusted.entrySet()) {
            OfflinePlayer o = Bukkit.getOfflinePlayer(t.getKey());
            String name = o.getName() == null ? t.getKey().toString().substring(0, 8) : o.getName();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(m -> {
                if (m instanceof SkullMeta sm) {
                    sm.setOwningPlayer(o);
                }
            });
            tiles.add(
                new LobbyDialogs.Tile(
                    head,
                    Text.mm("<white><b>" + Text.esc(name)),
                    Text.mm("<gray>" + t.getValue().name().toLowerCase(Locale.ROOT)),
                    List.of(
                        Text.mm("<white><b>" + Text.esc(name)),
                        Text.mm("<gray>Trust: <green>" + t.getValue().name().toLowerCase(Locale.ROOT)),
                        Component.empty(),
                        Text.mm("<red>▶ Click to remove")
                    ),
                    () -> {
                        c.trusted.remove(t.getKey());
                        this.dirty = true;
                        this.save();
                        p.sendMessage(Text.mm("<gray>" + Text.esc(name) + " is no longer trusted here."));
                        this.manage(p, c);
                    }
                )
            );
        }

        LobbyDialogs ui = this.plugin.dialogs().lobby();
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(
            ui.btn(
                p,
                "<green>+ Trust Player",
                "<gray>Let a friend build here",
                () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Trust a player"), "Player name", "", 16, namex -> {
                    if (!namex.isBlank()) {
                        this.trust(p, List.of(c), namex, Claim.Trust.BUILD);
                    }

                    this.manage(p, c);
                })
            )
        );
        buttons.add(ui.btn(p, "<gold>✎ Rename", null, () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Claim name"), "Name", c.name, 24, namex -> {
            c.name = namex.replaceAll("[^A-Za-z0-9 _'-]", "");
            this.dirty = true;
            this.save();
            this.manage(p, c);
        })));
        buttons.add(ui.btn(p, "<aqua>◎ Show Border", null, () -> {
            p.closeDialog();
            this.show(p, List.of(c), 20);
        }));
        buttons.add(
            ui.btn(
                p,
                "<red>✖ Abandon",
                "<gray>Give the land (and its claim blocks) back",
                () -> this.plugin
                    .dialogs()
                    .confirm(
                        p,
                        Text.mm("<red><b>Abandon this claim?"),
                        List.of(Text.mm("<gray>Anyone will be able to build there again.")),
                        () -> this.abandon(p, c)
                    )
            )
        );
        buttons.add(ui.btn(p, "<gray>« Back", null, () -> this.menu(p)));
        ui.screen(
            p,
            LobbyDialogs.title(c.name.isEmpty() ? "Claim #" + c.id : Text.esc(c.name), "Trusted players"),
            tiles,
            List.of(Text.mm("<gray>" + c.width() + "x" + c.length() + " <dark_gray>• <gray>" + c.minX + ", " + c.minZ + " → " + c.maxX + ", " + c.maxZ)),
            Text.mm("<gray>Nobody is trusted yet."),
            buttons,
            2,
            ui.close(p)
        );
    }

    private void howTo(Player p) {
        p.sendMessage(Text.mm("<gold><b>⛏ Claiming land</b>"));
        p.sendMessage(Text.mm("<gray>1. Hold a <gold>golden shovel</gold> and right-click two opposite corners."));
        p.sendMessage(Text.mm("<gray>   or stand in the middle and type <gold>/claim [radius]</gold>."));
        p.sendMessage(Text.mm("<gray>2. Right-click a corner of your claim with the shovel to resize it."));
        p.sendMessage(Text.mm("<gray>3. <gold>/trust <name> [build|container|access|manage]</gold>, <gold>/untrust</gold>, <gold>/trustlist</gold>."));
        p.sendMessage(
            Text.mm(
                "<gray>4. <gold>/unclaim</gold> gives the land back. You earn <white>"
                    + Cfg.i(Opt.CLAIM_BLOCKS_HOUR)
                    + "</white> claim blocks per hour played."
            )
        );
    }

    private void trust(Player p, List<Claim> targets, String name, Claim.Trust level) {
        OfflinePlayer o = Bukkit.getOfflinePlayer(name);
        if (o.getName() != null && (o.hasPlayedBefore() || o.isOnline())) {
            if (o.getUniqueId().equals(p.getUniqueId())) {
                err(p, "You can't trust yourself.");
            } else {
                int n = 0;

                for (Claim c : targets) {
                    if (this.canManage(p, c) && (level != Claim.Trust.MANAGE || p.getUniqueId().equals(c.owner) || p.hasPermission("vortexpvp.claims.admin"))) {
                        c.trusted.put(o.getUniqueId(), level);
                        n++;
                    }
                }

                if (n == 0) {
                    err(p, "You can't change trust here.");
                } else {
                    this.dirty = true;
                    this.save();
                    p.sendMessage(
                        Text.mm(
                            "<green>✔ <white>"
                                + Text.esc(o.getName())
                                + "</white> <gray>now has <green>"
                                + level.name().toLowerCase(Locale.ROOT)
                                + "</green> trust in "
                                + (n == 1 ? "this claim" : n + " claims")
                                + "."
                        )
                    );
                    Player op = o.getPlayer();
                    if (op != null) {
                        op.sendMessage(
                            Text.mm(
                                "<gray><white>"
                                    + Text.esc(p.getName())
                                    + "</white> trusted you (<green>"
                                    + level.name().toLowerCase(Locale.ROOT)
                                    + "</green>) in their claim."
                            )
                        );
                    }
                }
            }
        } else {
            err(p, "Unknown player '" + name + "'.");
        }
    }

    private boolean canManage(Player p, Claim c) {
        return !p.hasPermission("vortexpvp.claims.admin") || !c.admin() && !this.bypassing(p) ? c.allows(p.getUniqueId(), Claim.Trust.MANAGE) : true;
    }

    private List<Claim> targets(Player p) {
        Claim here = this.claimAt(p.getLocation());
        if (here != null) {
            return this.canManage(p, here) ? List.of(here) : List.of();
        } else {
            String mode = this.modes.mode(p.getWorld()).name();
            List<Claim> out = new ArrayList<>();

            for (Claim c : this.ownedBy(p.getUniqueId())) {
                if (mode.equals(this.modeOf(c.world))) {
                    out.add(c);
                }
            }

            return out;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        } else {
            String cmd = command.getName().toLowerCase(Locale.ROOT);
            if (!Cfg.bool(Opt.CLAIMS)) {
                err(p, "Land claims are disabled on this server.");
                return true;
            } else if (cmd.equals("claimadmin")) {
                this.admin(p, args);
                return true;
            } else if (!this.active(p.getWorld()) && !cmd.equals("claims")) {
                err(p, "Land claims don't work in this world.");
                return true;
            } else {
                switch (cmd) {
                    case "claim": {
                            int r = 10;
                            if (args.length > 0) {
                                try {
                                    r = Math.max(1, Math.min(250, Integer.parseInt(args[0])));
                                } catch (NumberFormatException var14) {
                                    err(p, "Usage: /claim [radius]");
                                    return true;
                                }
                            }

                            Location l = p.getLocation();
                            this.create(p, p.getWorld(), l.getBlockX() - r, l.getBlockZ() - r, l.getBlockX() + r, l.getBlockZ() + r);
                            break;
                    }
                    case "claims": {
                            this.menu(p);
                            break;
                    }
                    case "unclaim": {
                            Claim cx = this.claimAt(p.getLocation());
                            if (cx == null) {
                                err(p, "Stand inside the claim you want to abandon.");
                            } else if (p.getUniqueId().equals(cx.owner) || p.hasPermission("vortexpvp.claims.admin") && (cx.admin() || this.bypassing(p))) {
                                if (ClientVersion.supportsDialogs(p)) {
                                    this.plugin
                                        .dialogs()
                                        .confirm(
                                            p,
                                            Text.mm("<red><b>Abandon this claim?"),
                                            List.of(Text.mm("<gray>" + cx.width() + "x" + cx.length() + " - anyone will be able to build here again.")),
                                            () -> this.abandon(p, cx)
                                        );
                                } else {
                                    this.abandon(p, cx);
                                }
                            } else {
                                err(p, "This is not your claim.");
                            }
                            break;
                    }
                    case "trust": {
                            if (args.length == 0) {
                                err(p, "Usage: /trust <player> [build|container|access|manage]");
                                return true;
                            }

                            List<Claim> t = this.targets(p);
                            if (t.isEmpty()) {
                                err(p, "Stand in your claim (or have one in this mode) to trust someone.");
                                return true;
                            }

                            this.trust(p, t, args[0], Claim.Trust.parse(args.length > 1 ? args[1] : null, Claim.Trust.BUILD));
                            break;
                    }
                    case "untrust": {
                            if (args.length == 0) {
                                err(p, "Usage: /untrust <player>");
                                return true;
                            }

                            OfflinePlayer o = Bukkit.getOfflinePlayer(args[0]);
                            int n = 0;

                            for (Claim c : this.targets(p)) {
                                if (this.canManage(p, c) && c.trusted.remove(o.getUniqueId()) != null) {
                                    n++;
                                }
                            }

                            if (n == 0) {
                                err(p, args[0] + " isn't trusted here.");
                            } else {
                                this.dirty = true;
                                this.save();
                                p.sendMessage(Text.mm("<gray>" + Text.esc(args[0]) + " is no longer trusted in " + (n == 1 ? "this claim" : n + " claims") + "."));
                            }
                            break;
                    }
                    case "trustlist":
                    case "claiminfo": {
                            Claim cx = this.claimAt(p.getLocation());
                            if (cx == null) {
                                p.sendMessage(
                                    Text.mm(
                                        "<gray>You're in the <green>wilderness</green>. <dark_gray>• <gray>Your claim blocks: <white>"
                                            + Fmt.num(this.remaining(p.getUniqueId()))
                                    )
                                );
                                return true;
                            }

                            this.info(p, cx);
                            List<String> names = new ArrayList<>();

                            for (Entry<UUID, Claim.Trust> t : cx.trusted.entrySet()) {
                                String n = Bukkit.getOfflinePlayer(t.getKey()).getName();
                                names.add((n == null ? "?" : n) + " (" + t.getValue().name().toLowerCase(Locale.ROOT) + ")");
                            }

                            p.sendMessage(Text.mm("<gray>Trusted: <white>" + (names.isEmpty() ? "nobody" : Text.esc(String.join(", ", names)))));
                            this.show(p, List.of(cx), 10);
                            break;
                    }
                    default: {
                            return false;
                    }
                }

                return true;
            }
        }
    }

    private void admin(Player p, String[] args) {
        if (!p.hasPermission("vortexpvp.claims.admin")) {
            err(p, "You don't have permission.");
        } else {
            String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "bypass": {
                        boolean on = !this.bypass.remove(p.getUniqueId());
                        if (on) {
                            this.bypass.add(p.getUniqueId());
                        }

                        p.sendMessage(Text.mm("<gray>Ignore claims: " + (on ? "<green><b>ON" : "<red><b>OFF")));
                        break;
                }
                case "admin": {
                        boolean on = !this.adminMode.remove(p.getUniqueId());
                        if (on) {
                            this.adminMode.add(p.getUniqueId());
                        }

                        p.sendMessage(
                            Text.mm(
                                "<gray>New claims are "
                                    + (on ? "<aqua><b>admin claims</b> <gray>(protected by the server, no blocks needed)" : "<white>normal claims")
                            )
                        );
                        break;
                }
                case "delete": {
                        Claim c = this.claimAt(p.getLocation());
                        if (c == null) {
                            err(p, "Stand in the claim to delete.");
                        } else {
                            this.abandon(p, c);
                        }
                        break;
                }
                case "blocks": {
                        if (args.length < 3) {
                            err(p, "Usage: /claimadmin blocks <player> <+/-amount>");
                            return;
                        }

                        OfflinePlayer o = Bukkit.getOfflinePlayer(args[1]);

                        try {
                            int n = Integer.parseInt(args[2]);
                            this.blocksOf(o.getUniqueId()).bonus += n;
                            this.dirty = true;
                            this.save();
                            p.sendMessage(
                                Text.mm(
                                    "<green>✔ <white>"
                                        + Text.esc(args[1])
                                        + "</white> now has <white>"
                                        + Fmt.num(this.remaining(o.getUniqueId()))
                                        + "</white> claim blocks left."
                                )
                            );
                        } catch (NumberFormatException var10) {
                            err(p, "Not a number: " + args[2]);
                        }
                        break;
                }
                case "list": {
                        if (args.length < 2) {
                            err(p, "Usage: /claimadmin list <player>");
                            return;
                        }

                        OfflinePlayer o = Bukkit.getOfflinePlayer(args[1]);
                        List<Claim> list = this.ownedBy(o.getUniqueId());
                        p.sendMessage(
                            Text.mm(
                                "<gold>"
                                    + Text.esc(args[1])
                                    + "'s claims ("
                                    + list.size()
                                    + ") <dark_gray>• <gray>blocks left: <white>"
                                    + Fmt.num(this.remaining(o.getUniqueId()))
                            )
                        );

                        for (Claim cx : list) {
                            p.sendMessage(
                                Text.mm(
                                    "<gray>#"
                                        + cx.id
                                        + " <white>"
                                        + cx.world
                                        + "</white> "
                                        + cx.minX
                                        + ", "
                                        + cx.minZ
                                        + " ("
                                        + cx.width()
                                        + "x"
                                        + cx.length()
                                        + ")"
                                )
                            );
                        }
                        break;
                }
                default: {
                        p.sendMessage(
                            Text.mm(
                                "<gold>/claimadmin <white>bypass</white> <gray>(ignore claims) <dark_gray>• <white>admin</white> <gray>(next claims = server claims) <dark_gray>• <white>delete</white> <dark_gray>• <white>blocks <player> <amount></white> <dark_gray>• <white>list <player>"
                            )
                        );
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            if (cmd.equals("claimadmin")) {
                out.addAll(List.of("bypass", "admin", "delete", "blocks", "list"));
            } else if (cmd.equals("trust") || cmd.equals("untrust")) {
                for (Player o : Bukkit.getOnlinePlayers()) {
                    out.add(o.getName());
                }
            }
        } else if (args.length == 2 && cmd.equals("trust")) {
            out.addAll(List.of("build", "container", "access", "manage"));
        } else if (args.length == 2 && cmd.equals("claimadmin")) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                out.add(o.getName());
            }
        }

        String pre = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(pre));
        return out;
    }

    static Dialogs.Choice choice(String id, String label) {
        return new Dialogs.Choice(id, label);
    }

    private static final class Blocks {
        int earned;
        int bonus;
        double partial;
    }

    private record Showing(List<Claim> claims, long until) {
    }
}
