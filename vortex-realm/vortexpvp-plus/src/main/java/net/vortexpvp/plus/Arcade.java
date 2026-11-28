package net.vortexpvp.plus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Session;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

/**
 * Arcade games with their own small arenas (built once on the add-on's ground): Sumo, Boxing, Spleef, One in the
 * Chamber, King of the Hill, a Parkour race and Bridge. Queue from /arcade; a game starts as soon as enough players
 * are waiting. On weekends one mode is featured (double XP) and announced.
 */
public final class Arcade implements Listener {
    public static final int BUILD_VERSION = 1;
    private final VortexPvP plugin;
    private final PlusModule plus;
    private final Map<Mode, List<UUID>> queues = new LinkedHashMap<>();
    private final Map<Mode, Integer> countdown = new HashMap<>();
    private final Map<Mode, Game> games = new HashMap<>();
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    private long lastAnnounce;

    public enum Mode {
        SUMO("Sumo", Material.SLIME_BALL, 2, 8, 0, 0, "Knock everyone off the platform. Last one standing wins."),
        BOXING("Boxing", Material.LEATHER_CHESTPLATE, 2, 2, 1, 0, "No damage, just hits: first to 100 hits wins."),
        SPLEEF("Spleef", Material.DIAMOND_SHOVEL, 2, 8, 2, 0, "Dig the snow under the others. Last one standing wins."),
        OITC("One in the Chamber", Material.BOW, 2, 8, 3, 0, "One arrow kills. A kill gives you another. First to 10 kills."),
        KOTH("King of the Hill", Material.GOLDEN_HELMET, 2, 8, 0, 1, "Hold the gold hill alone to score. First to 60 points."),
        BRIDGE("Bridge", Material.BLUE_TERRACOTTA, 2, 2, 1, 1, "Bridge over and jump into the other goal. First to 3."),
        PARKOUR("Parkour Race", Material.FEATHER, 2, 8, 3, 1, "First to the diamond block wins. Gold blocks are checkpoints.");

        public final String display;
        public final Material icon;
        public final int min;
        public final int max;
        final int px;
        final int pz;
        public final String help;

        Mode(String display, Material icon, int min, int max, int px, int pz, String help) {
            this.display = display;
            this.icon = icon;
            this.min = min;
            this.max = max;
            this.px = px;
            this.pz = pz;
            this.help = help;
        }
    }

    final class Game {
        final Mode mode;
        final Location c;
        final List<UUID> players = new ArrayList<>();
        final Set<UUID> out = new HashSet<>();
        final Map<UUID, Integer> score = new HashMap<>();
        final Map<UUID, Integer> team = new HashMap<>();
        final Map<UUID, Location> checkpoint = new HashMap<>();
        final Map<UUID, UUID> lastHit = new HashMap<>();
        final Map<UUID, Long> lastHitAt = new HashMap<>();
        final Set<Block> placed = new HashSet<>();
        int ticks;
        int count = 100;
        boolean running;
        boolean ended;

        Game(Mode mode, Location c) {
            this.mode = mode;
            this.c = c;
        }

        int score(UUID u) {
            return this.score.getOrDefault(u, 0);
        }

        List<Player> online() {
            List<Player> out = new ArrayList<>();
            for (UUID u : this.players) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    out.add(p);
                }
            }

            return out;
        }
    }

    public Arcade(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
        for (Mode m : Mode.values()) {
            this.queues.put(m, new ArrayList<>());
        }
    }

    // ------------------------------------------------------------------ featured mode

    public Mode featured() {
        LocalDate d = LocalDate.now();
        List<String> days = this.plus.config().list("arcade.featured-days", List.of("SATURDAY", "SUNDAY"));
        if (!days.contains(d.getDayOfWeek().name())) {
            return null;
        }

        String forced = this.plus.config().s("arcade.featured-mode", "auto");
        for (Mode m : Mode.values()) {
            if (m.name().equalsIgnoreCase(forced)) {
                return m;
            }
        }

        int week = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return Mode.values()[week % Mode.values().length];
    }

    // ------------------------------------------------------------------ queue

    public void menu(Player p) {
        Mode feat = this.featured();
        Gui.open(this.plugin, p, "<dark_gray>Arcade", 4, g -> {
            int[] slots = {10, 11, 12, 13, 14, 15, 16};
            int i = 0;
            for (Mode m : Mode.values()) {
                boolean in = this.queues.get(m).contains(p.getUniqueId());
                Game running = this.games.get(m);
                g.item(slots[i++], Items.of(m.icon).name((m == feat ? "<gradient:#FFD700:#FF6B00><b>★ " : "<yellow><b>") + m.display + (m == feat ? " ★" : "") + "</b>")
                    .lore("<gray>" + m.help, "", "<gray>Players: <white>" + m.min + (m.max > m.min ? "-" + m.max : ""),
                        "<gray>Waiting: <white>" + this.queues.get(m).size() + (running != null ? " <dark_gray>(a game is running)" : ""),
                        m == feat ? "<gold>Featured this weekend: double XP!" : "", "", in ? "<red>▶ Click to leave the queue" : "<green>▶ Click to join")
                    .glint(m == feat).hideAll().build(), c -> {
                        if (in) {
                            this.leaveQueue(p);
                        } else {
                            this.join(p, m);
                        }

                        this.menu(p);
                    });
            }

            g.item(31, Items.of(Material.BOOK).name("<aqua>How it works").lore("<gray>Pick a game; it starts when enough", "<gray>players are waiting. Winners get XP", "<gray>and shards, and it counts for missions.").build());
        });
    }

    public void join(Player p, Mode m) {
        if (!this.plus.config().on("arcade")) {
            p.sendMessage(Text.mm("<red>The arcade is switched off."));
            return;
        }

        if (this.byPlayer.containsKey(p.getUniqueId())) {
            p.sendMessage(Text.mm("<red>You are already in an arcade game."));
            return;
        }

        if (this.plugin.sessions().state(p) != PlayerState.LOBBY) {
            p.sendMessage(Text.mm("<red>Join from the lobby."));
            return;
        }

        this.leaveQueue(p);
        this.queues.get(m).add(p.getUniqueId());
        p.sendMessage(Text.mm("<green>✔ Waiting for <white>" + m.display + "</white> <gray>(" + this.queues.get(m).size() + "/" + m.min + " to start) <click:run_command:'/arcade leave'><red>[leave]</red></click>"));
        Snd.click(p);
    }

    public boolean leaveQueue(Player p) {
        boolean any = false;
        for (List<UUID> q : this.queues.values()) {
            any |= q.remove(p.getUniqueId());
        }

        return any;
    }

    public boolean inGame(UUID u) {
        return this.byPlayer.containsKey(u);
    }

    /** Every second: starts games from the queues, runs countdowns, announces the featured mode. */
    public void second() {
        for (Mode m : Mode.values()) {
            List<UUID> q = this.queues.get(m);
            q.removeIf(u -> {
                Player p = Bukkit.getPlayer(u);
                return p == null || this.plugin.sessions().state(p) != PlayerState.LOBBY;
            });
            if (this.games.containsKey(m) || q.size() < m.min) {
                this.countdown.remove(m);
                continue;
            }

            int left = this.countdown.getOrDefault(m, q.size() >= m.max ? 3 : 10) - 1;
            if (left <= 0) {
                this.countdown.remove(m);
                List<UUID> take = new ArrayList<>(q.subList(0, Math.min(m.max, q.size())));
                q.removeAll(take);
                this.start(m, take);
            } else {
                this.countdown.put(m, left);
                for (UUID u : q) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null) {
                        p.sendActionBar(Text.mm("<yellow>" + m.display + " <gray>starts in <white>" + left + "s <dark_gray>(" + q.size() + " players)"));
                    }
                }
            }
        }

        Mode feat = this.featured();
        if (feat != null && System.currentTimeMillis() - this.lastAnnounce > this.plus.config().i("arcade.announce-minutes", 30) * 60000L) {
            this.lastAnnounce = System.currentTimeMillis();
            Bukkit.broadcast(Text.mm("<gradient:#FFD700:#FF6B00><b>WEEKEND ARCADE</b></gradient> <white>" + feat.display
                + " <gray>is featured - double XP! <click:run_command:'/arcade join " + feat.name().toLowerCase(Locale.ROOT) + "'><green>[play]</green></click>"));
        }
    }

    // ------------------------------------------------------------------ arenas

    private Location center(Mode m) {
        return Grounds.plot(this.plugin, this.plus, m.px, m.pz);
    }

    private void ensureBuilt(Mode m, Location c) {
        String key = "arcade." + m.name();
        if (this.plus.store().doc("ground").getInt(key, 0) == BUILD_VERSION) {
            return;
        }

        this.build(m, c);
        this.plus.store().doc("ground").set(key, BUILD_VERSION);
        this.plus.store().dirty("ground");
    }

    public void rebuildAll() {
        for (Mode m : Mode.values()) {
            if (!this.games.containsKey(m)) {
                this.build(m, this.center(m));
                this.plus.store().doc("ground").set("arcade." + m.name(), BUILD_VERSION);
            }
        }

        this.plus.store().dirty("ground");
    }

    void build(Mode m, Location c) {
        World w = c.getWorld();
        int x = c.getBlockX();
        int y = c.getBlockY();
        int z = c.getBlockZ();
        if (m != Mode.PARKOUR) {
            Grounds.box(w, x - 24, y - 4, z - 24, x + 24, y + 8, z + 24, Material.AIR);
        }

        switch (m) {
            case SUMO -> {
                for (int dx = -9; dx <= 9; dx++) {
                    for (int dz = -9; dz <= 9; dz++) {
                        double d = Math.sqrt(dx * dx + dz * dz);
                        if (d <= 8.5) {
                            w.getBlockAt(x + dx, y - 1, z + dz).setType(d > 7.5 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE, false);
                            w.getBlockAt(x + dx, y - 2, z + dz).setType(Material.SMOOTH_QUARTZ, false);
                        }
                    }
                }
            }
            case BOXING -> {
                Grounds.box(w, x - 10, y - 1, z - 10, x + 10, y - 1, z + 10, Material.BLACK_CONCRETE);
                Grounds.walls(w, x, z, 10, y - 1, y - 1, Material.GRAY_CONCRETE);
                Grounds.walls(w, x, z, 11, y - 1, y + 3, Material.LIGHT_GRAY_STAINED_GLASS);
                for (int i = -1; i <= 1; i += 2) {
                    for (int j = -1; j <= 1; j += 2) {
                        w.getBlockAt(x + i * 10, y, z + j * 10).setType(Material.RED_WOOL, false);
                    }
                }
            }
            case SPLEEF -> {
                this.snow(c);
                Grounds.walls(w, x, z, 13, y - 1, y + 3, Material.WHITE_STAINED_GLASS);
                Grounds.box(w, x - 13, y - 7, z - 13, x + 13, y - 7, z + 13, Material.BLUE_ICE);
            }
            case OITC, KOTH -> {
                Random r = new Random(m.ordinal() * 31L);
                for (int dx = -15; dx <= 15; dx++) {
                    for (int dz = -15; dz <= 15; dz++) {
                        Material floor = m == Mode.KOTH ? (r.nextInt(6) == 0 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK)
                            : (r.nextInt(5) == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                        w.getBlockAt(x + dx, y - 1, z + dz).setType(floor, false);
                    }
                }

                Grounds.walls(w, x, z, 16, y - 1, y + 4, m == Mode.KOTH ? Material.GLASS : Material.STONE_BRICKS);
                if (m == Mode.OITC) {
                    int[][] pillars = {{-8, -8}, {8, -8}, {-8, 8}, {8, 8}, {0, -9}, {0, 9}, {-9, 0}, {9, 0}};
                    for (int[] pl : pillars) {
                        Grounds.box(w, x + pl[0], y, z + pl[1], x + pl[0] + 1, y + 2, z + pl[1] + 1, Material.POLISHED_ANDESITE);
                    }

                    Grounds.box(w, x - 3, y, z - 1, x + 3, y, z + 1, Material.COBBLESTONE_WALL);
                } else {
                    Grounds.box(w, x - 3, y, z - 3, x + 3, y, z + 3, Material.STONE_BRICKS);
                    Grounds.box(w, x - 2, y + 1, z - 2, x + 2, y + 1, z + 2, Material.GOLD_BLOCK);
                    for (int i = -3; i <= 3; i += 6) {
                        Grounds.box(w, x + i, y + 1, z - 3, x + i, y + 1, z + 3, Material.STONE_BRICK_SLAB);
                        Grounds.box(w, x - 3, y + 1, z + i, x + 3, y + 1, z + i, Material.STONE_BRICK_SLAB);
                    }
                }
            }
            case BRIDGE -> {
                Grounds.box(w, x - 22, y - 2, z - 3, x - 16, y - 1, z + 3, Material.RED_TERRACOTTA);
                Grounds.box(w, x + 16, y - 2, z - 3, x + 22, y - 1, z + 3, Material.BLUE_TERRACOTTA);
                Grounds.box(w, x - 22, y - 1, z - 1, x - 20, y - 1, z + 1, Material.RED_STAINED_GLASS);
                Grounds.box(w, x + 20, y - 1, z - 1, x + 22, y - 1, z + 1, Material.BLUE_STAINED_GLASS);
                Grounds.box(w, x - 2, y - 1, z - 2, x + 2, y - 1, z + 2, Material.WHITE_TERRACOTTA);
            }
            case PARKOUR -> {
                Grounds.box(w, x - 44, y - 10, z - 12, x + 70, y + 10, z + 12, Material.AIR);
                Grounds.box(w, x - 42, y - 1, z - 2, x - 38, y - 1, z + 2, Material.LIME_CONCRETE);
                Random r = new Random(7L);
                int px = x - 38;
                int py = y - 1;
                int pz = z;
                for (int i = 1; i <= 30; i++) {
                    int dy = r.nextInt(3) - 1;
                    if (py + dy > y + 5 || py + dy < y - 5) {
                        dy = -dy;
                    }

                    int gap = dy > 0 ? 2 : 2 + r.nextInt(2);
                    int dz = r.nextInt(3) - 1;
                    px += gap + 1;
                    py += dy;
                    pz = Math.max(z - 8, Math.min(z + 8, pz + dz));
                    w.getBlockAt(px, py, pz).setType(i % 10 == 0 ? Material.GOLD_BLOCK : (i % 2 == 0 ? Material.QUARTZ_BLOCK : Material.LIGHT_BLUE_CONCRETE), false);
                }

                Grounds.box(w, px + 3, py, pz - 1, px + 5, py, pz + 1, Material.DIAMOND_BLOCK);
            }
        }
    }

    private void snow(Location c) {
        Grounds.box(c.getWorld(), c.getBlockX() - 12, c.getBlockY() - 1, c.getBlockZ() - 12, c.getBlockX() + 12, c.getBlockY() - 1, c.getBlockZ() + 12, Material.SNOW_BLOCK);
    }

    private List<Location> spawns(Game g, int n) {
        List<Location> out = new ArrayList<>();
        Location c = g.c;
        switch (g.mode) {
            case BOXING -> {
                out.add(Grounds.facing(c.clone().add(-6, 0, 0), c));
                out.add(Grounds.facing(c.clone().add(6, 0, 0), c));
            }
            case BRIDGE -> {
                out.add(Grounds.facing(c.clone().add(-17, 0, 0), c));
                out.add(Grounds.facing(c.clone().add(17, 0, 0), c));
            }
            case PARKOUR -> {
                for (int i = 0; i < n; i++) {
                    Location l = c.clone().add(-40 + (i % 3) - 1, 0, i / 3 - 1);
                    l.setYaw(-90.0F);
                    out.add(l);
                }
            }
            default -> {
                double r = g.mode == Mode.SUMO ? 4.5 : (g.mode == Mode.SPLEEF ? 8.0 : 12.0);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / Math.max(2, n);
                    out.add(Grounds.facing(c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), c));
                }
            }
        }

        while (out.size() < n) {
            out.add(out.get(out.size() % Math.max(1, out.size())));
        }

        return out;
    }

    // ------------------------------------------------------------------ the game

    public void start(Mode m, List<UUID> who) {
        Location c = this.center(m);
        this.ensureBuilt(m, c);
        if (m == Mode.SPLEEF) {
            this.snow(c);
        }

        Game g = new Game(m, c);
        for (UUID u : who) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && this.plugin.sessions().state(p) == PlayerState.LOBBY) {
                g.players.add(u);
            }
        }

        if (g.players.size() < m.min) {
            return;
        }

        this.games.put(m, g);
        List<Location> sp = this.spawns(g, g.players.size());
        for (int i = 0; i < g.players.size(); i++) {
            UUID u = g.players.get(i);
            Player p = Bukkit.getPlayer(u);
            this.byPlayer.put(u, g);
            g.team.put(u, i % 2);
            Session s = this.plugin.sessions().get(p);
            s.state = PlayerState.BOT;
            this.reset(p, m == Mode.SPLEEF || m == Mode.BRIDGE ? GameMode.SURVIVAL : GameMode.ADVENTURE);
            Tp.to(p, sp.get(i));
            g.checkpoint.put(u, sp.get(i));
            p.showTitle(Title.title(Text.mm("<gold><b>" + m.display), Text.mm("<gray>" + m.help)));
        }
    }

    private void reset(Player p, GameMode gm) {
        p.getInventory().clear();
        p.setGameMode(gm);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setFireTicks(0);
        p.setFoodLevel(20);
        p.setSaturation(10.0F);
        for (PotionEffect pe : p.getActivePotionEffects()) {
            p.removePotionEffect(pe.getType());
        }

        org.bukkit.attribute.AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20.0 : hp.getValue());
    }

    private void kit(Game g, Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        switch (g.mode) {
            case SPLEEF -> inv.setItem(0, Items.of(Material.DIAMOND_SHOVEL).ench(Enchantment.EFFICIENCY, 5).unbreakable().build());
            case OITC -> {
                inv.setItem(0, Items.of(Material.WOODEN_SWORD).unbreakable().build());
                inv.setItem(1, Items.of(Material.BOW).unbreakable().build());
                inv.setItem(8, new ItemStack(Material.ARROW, 1));
            }
            case KOTH -> {
                inv.setItem(0, Items.of(Material.STONE_SWORD).unbreakable().build());
                inv.setArmorContents(new ItemStack[]{Items.of(Material.LEATHER_BOOTS).unbreakable().build(), Items.of(Material.LEATHER_LEGGINGS).unbreakable().build(),
                    Items.of(Material.LEATHER_CHESTPLATE).unbreakable().build(), Items.of(Material.GOLDEN_HELMET).unbreakable().build()});
            }
            case BRIDGE -> {
                boolean red = g.team.getOrDefault(p.getUniqueId(), 0) == 0;
                inv.setItem(0, Items.of(Material.IRON_SWORD).unbreakable().build());
                inv.setItem(1, new ItemStack(red ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA, 64));
                inv.setItem(2, new ItemStack(red ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA, 64));
                inv.setItem(3, Items.of(Material.IRON_PICKAXE).unbreakable().build());
                inv.setArmorContents(new ItemStack[]{Items.of(Material.LEATHER_BOOTS).color(red ? org.bukkit.Color.RED : org.bukkit.Color.BLUE).unbreakable().build(), null,
                    Items.of(Material.LEATHER_CHESTPLATE).color(red ? org.bukkit.Color.RED : org.bukkit.Color.BLUE).unbreakable().build(), null});
            }
            default -> {
            }
        }
    }

    /** Every 2 ticks: countdowns, falls, the hill, goals, checkpoints, time limits. */
    public void tick() {
        for (Game g : new ArrayList<>(this.games.values())) {
            if (g.ended) {
                continue;
            }

            List<Player> on = g.online();
            if (on.size() < (g.mode == Mode.PARKOUR ? 1 : 2) && g.running || on.isEmpty()) {
                this.finish(g, on.isEmpty() ? null : on.get(0).getUniqueId());
                continue;
            }

            if (!g.running) {
                g.count -= 2;
                if (g.count % 20 == 0 && g.count > 0) {
                    for (Player p : on) {
                        p.showTitle(Title.title(Text.mm("<yellow><b>" + g.count / 20), Text.mm("<gray>" + g.mode.display), Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(900), java.time.Duration.ZERO)));
                        Snd.play(p, "block.note_block.hat", 1.0F, 1.0F);
                    }
                }

                if (g.count <= 0) {
                    g.running = true;
                    for (Player p : on) {
                        this.kit(g, p);
                        p.showTitle(Title.title(Text.mm("<green><b>GO!"), net.kyori.adventure.text.Component.empty()));
                        Snd.play(p, "entity.player.levelup", 1.0F, 1.6F);
                    }
                }

                continue;
            }

            g.ticks += 2;
            int floorY = g.c.getBlockY();
            for (Player p : on) {
                UUID u = p.getUniqueId();
                if (g.out.contains(u)) {
                    continue;
                }

                Location l = p.getLocation();
                if (l.getY() < floorY - (g.mode == Mode.PARKOUR ? 8 : 4)) {
                    this.fell(g, p);
                    continue;
                }

                Material under = l.clone().subtract(0.0, 0.2, 0.0).getBlock().getType();
                if (g.mode == Mode.PARKOUR) {
                    if (under == Material.GOLD_BLOCK) {
                        Location cp = l.getBlock().getLocation().add(0.5, 0.0, 0.5);
                        cp.setYaw(-90.0F);
                        Location old = g.checkpoint.get(u);
                        if (old == null || old.distanceSquared(cp) > 1.0) {
                            g.checkpoint.put(u, cp);
                            g.score.merge(u, 1, Integer::sum);
                            p.sendActionBar(Text.mm("<gold>✔ Checkpoint " + g.score(u)));
                            Snd.play(p, "entity.experience_orb.pickup", 1.0F, 1.2F);
                        }
                    } else if (under == Material.DIAMOND_BLOCK) {
                        this.finish(g, u);
                        break;
                    }
                } else if (g.mode == Mode.BRIDGE) {
                    boolean red = g.team.getOrDefault(u, 0) == 0;
                    if (red && under == Material.BLUE_STAINED_GLASS || !red && under == Material.RED_STAINED_GLASS) {
                        g.score.merge(u, 1, Integer::sum);
                        this.say(g, "<gold>⚑ " + (red ? "<red>" : "<blue>") + Text.esc(p.getName()) + " <white>scored! <gray>(" + this.scoreLine(g) + ")");
                        if (g.score(u) >= 3) {
                            this.finish(g, u);
                            break;
                        }

                        List<Location> sp = this.spawns(g, 2);
                        for (Player o : on) {
                            this.reset(o, GameMode.SURVIVAL);
                            this.kit(g, o);
                            Tp.to(o, sp.get(g.team.getOrDefault(o.getUniqueId(), 0)));
                        }
                    } else if (red && under == Material.RED_STAINED_GLASS || !red && under == Material.BLUE_STAINED_GLASS) {
                        Tp.to(p, this.spawns(g, 2).get(red ? 0 : 1));
                    }
                }
            }

            if (g.ended) {
                continue;
            }

            if (g.mode == Mode.KOTH && g.ticks % 20 == 0) {
                List<Player> onHill = new ArrayList<>();
                for (Player p : on) {
                    Location l = p.getLocation();
                    if (Math.abs(l.getX() - g.c.getX()) <= 2.6 && Math.abs(l.getZ() - g.c.getZ()) <= 2.6 && l.getY() >= floorY + 1.9 && l.getY() <= floorY + 3.5) {
                        onHill.add(p);
                    }
                }

                if (onHill.size() == 1) {
                    UUID u = onHill.get(0).getUniqueId();
                    g.score.merge(u, 1, Integer::sum);
                    if (g.score(u) >= this.plus.config().i("arcade.koth-points", 60)) {
                        this.finish(g, u);
                        continue;
                    }
                }

                for (Player p : on) {
                    p.sendActionBar(Text.mm((onHill.size() > 1 ? "<red>Contested! " : "") + "<gold>You: " + g.score(p.getUniqueId()) + " <dark_gray>• <gray>" + this.scoreLine(g)));
                }
            }

            if (g.mode == Mode.SUMO || g.mode == Mode.SPLEEF) {
                List<UUID> alive = new ArrayList<>();
                for (Player p : on) {
                    if (!g.out.contains(p.getUniqueId())) {
                        alive.add(p.getUniqueId());
                    }
                }

                if (alive.size() <= 1) {
                    this.finish(g, alive.isEmpty() ? null : alive.get(0));
                    continue;
                }
            }

            int limit = switch (g.mode) {
                case BOXING -> 180;
                case OITC -> 240;
                case SUMO, SPLEEF -> 240;
                default -> 300;
            };
            if (g.ticks >= limit * 20) {
                UUID best = null;
                int top = -1;
                for (UUID u : g.players) {
                    if (g.score(u) > top && Bukkit.getPlayer(u) != null) {
                        top = g.score(u);
                        best = u;
                    }
                }

                this.finish(g, top > 0 ? best : null);
            }
        }
    }

    private String scoreLine(Game g) {
        StringBuilder sb = new StringBuilder();
        for (UUID u : g.players) {
            String n = Bukkit.getOfflinePlayer(u).getName();
            sb.append(sb.length() == 0 ? "" : " <dark_gray>| ").append("<white>").append(Text.esc(n == null ? "?" : n)).append(" <gold>").append(g.score(u));
        }

        return sb.toString();
    }

    private void say(Game g, String mini) {
        for (Player p : g.online()) {
            p.sendMessage(Text.mm(mini));
        }
    }

    private void fell(Game g, Player p) {
        UUID u = p.getUniqueId();
        switch (g.mode) {
            case SUMO, SPLEEF -> this.eliminate(g, p);
            case PARKOUR -> {
                Location cp = g.checkpoint.get(u);
                Tp.to(p, cp == null ? this.spawns(g, 1).get(0) : cp);
            }
            case BOXING -> Tp.to(p, this.spawns(g, 2).get(g.players.indexOf(u) % 2));
            default -> this.death(g, p, this.recentAttacker(g, u));
        }
    }

    private Player recentAttacker(Game g, UUID victim) {
        UUID a = g.lastHit.get(victim);
        Long t = g.lastHitAt.get(victim);
        return a != null && t != null && System.currentTimeMillis() - t < 6000L ? Bukkit.getPlayer(a) : null;
    }

    private void eliminate(Game g, Player p) {
        g.out.add(p.getUniqueId());
        p.setGameMode(GameMode.SPECTATOR);
        Tp.to(p, g.c.clone().add(0.0, 8.0, 0.0));
        this.say(g, "<red>✖ <white>" + Text.esc(p.getName()) + " <gray>is out!");
    }

    private void death(Game g, Player victim, Player killer) {
        UUID v = victim.getUniqueId();
        if (killer != null && g.players.contains(killer.getUniqueId()) && !killer.equals(victim)) {
            if (g.mode == Mode.OITC) {
                g.score.merge(killer.getUniqueId(), 1, Integer::sum);
                killer.getInventory().addItem(new ItemStack(Material.ARROW, 1));
                killer.sendActionBar(Text.mm("<green>+1 kill <gray>(" + g.score(killer.getUniqueId()) + "/10) <white>+1 arrow"));
                if (g.score(killer.getUniqueId()) >= this.plus.config().i("arcade.oitc-kills", 10)) {
                    this.finish(g, killer.getUniqueId());
                    return;
                }
            }

            this.say(g, "<gray>☠ <white>" + Text.esc(victim.getName()) + " <gray>was killed by <white>" + Text.esc(killer.getName()));
        }

        this.reset(victim, g.mode == Mode.BRIDGE ? GameMode.SURVIVAL : GameMode.ADVENTURE);
        this.kit(g, victim);
        List<Location> sp = this.spawns(g, Math.max(2, g.players.size()));
        Location to = g.mode == Mode.BRIDGE ? sp.get(g.team.getOrDefault(v, 0)) : sp.get(new Random().nextInt(sp.size()));
        Tp.to(victim, to);
        g.lastHit.remove(v);
    }

    public void finish(Game g, UUID winner) {
        if (g.ended) {
            return;
        }

        g.ended = true;
        Mode feat = this.featured();
        int mult = g.mode == feat ? 2 : 1;
        String wn = winner == null ? null : Bukkit.getOfflinePlayer(winner).getName();
        for (UUID u : g.players) {
            this.plus.progression().track(u, "arcade_play", g.mode.name().toLowerCase(Locale.ROOT), 1L);
            this.plus.progression().xp(u, 20L * mult, g.mode.display);
        }

        if (winner != null) {
            this.plus.progression().track(winner, "arcade_win", g.mode.name().toLowerCase(Locale.ROOT), 1L);
            this.plus.progression().xp(winner, 60L * mult, g.mode.display + " win");
            this.plus.progression().shards(winner, this.plus.config().i("arcade.win-shards", 25));
        }

        for (Player p : g.online()) {
            p.showTitle(Title.title(Text.mm(winner == null ? "<gray><b>DRAW" : (p.getUniqueId().equals(winner) ? "<gold><b>VICTORY!" : "<red><b>GAME OVER")),
                Text.mm(wn == null ? "<gray>Nobody won" : "<white>" + Text.esc(wn) + " <gray>won " + g.mode.display)));
            if (p.getUniqueId().equals(winner)) {
                Snd.play(p, "ui.toast.challenge_complete", 1.0F, 1.0F);
            }
        }

        net.vortexpvp.util.Tasks.later(60L, () -> {
            for (UUID u : g.players) {
                this.byPlayer.remove(u);
                Player p = Bukkit.getPlayer(u);
                if (p != null && this.plugin.sessions().state(p) == PlayerState.BOT) {
                    this.plugin.lobby().sendToLobby(p);
                }
            }

            for (Block b : g.placed) {
                b.setType(Material.AIR, false);
            }

            if (g.mode == Mode.SPLEEF) {
                this.snow(g.c);
            }

            for (org.bukkit.entity.Entity en : g.c.getWorld().getNearbyEntities(g.c, 30, 20, 30)) {
                if (en instanceof Arrow || en instanceof org.bukkit.entity.Item) {
                    en.remove();
                }
            }

            this.games.remove(g.mode);
        });
    }

    public void stopAll() {
        for (Game g : new ArrayList<>(this.games.values())) {
            g.ended = true;
            for (UUID u : g.players) {
                this.byPlayer.remove(u);
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    this.plugin.lobby().sendToLobby(p);
                }
            }

            for (Block b : g.placed) {
                b.setType(Material.AIR, false);
            }
        }

        this.games.clear();
    }

    // ------------------------------------------------------------------ rules

    private Game gameOf(Player p) {
        return this.byPlayer.get(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player v)) {
            return;
        }

        Game g = this.gameOf(v);
        if (g == null) {
            return;
        }

        if (!g.running || g.ended || g.out.contains(v.getUniqueId()) || e.getCause() == EntityDamageEvent.DamageCause.FALL || g.mode == Mode.PARKOUR) {
            e.setCancelled(true);
            return;
        }

        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            return;
        }

        Player attacker = null;
        boolean arrow = false;
        if (e instanceof EntityDamageByEntityEvent ed) {
            if (ed.getDamager() instanceof Player a) {
                attacker = a;
            } else if (ed.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player a) {
                attacker = a;
                arrow = pr instanceof Arrow;
            }

            if (attacker == null || this.gameOf(attacker) != g || g.out.contains(attacker.getUniqueId()) || attacker.equals(v)) {
                e.setCancelled(true);
                return;
            }

            if (g.mode == Mode.BRIDGE && g.team.get(attacker.getUniqueId()).equals(g.team.get(v.getUniqueId()))) {
                e.setCancelled(true);
                return;
            }

            g.lastHit.put(v.getUniqueId(), attacker.getUniqueId());
            g.lastHitAt.put(v.getUniqueId(), System.currentTimeMillis());
        }

        e.setCancelled(false);
        switch (g.mode) {
            case SUMO, SPLEEF, BOXING -> {
                if (attacker == null) {
                    e.setCancelled(true);
                    return;
                }

                e.setDamage(0.0);
                if (g.mode == Mode.BOXING) {
                    g.score.merge(attacker.getUniqueId(), 1, Integer::sum);
                    int goal = this.plus.config().i("arcade.boxing-hits", 100);
                    attacker.sendActionBar(Text.mm("<gold>Hits: <white>" + g.score(attacker.getUniqueId()) + "/" + goal + " <dark_gray>• <gray>" + this.scoreLine(g)));
                    if (g.score(attacker.getUniqueId()) >= goal) {
                        this.finish(g, attacker.getUniqueId());
                    }
                }
            }
            case OITC -> {
                if (arrow) {
                    e.setCancelled(true);
                    this.death(g, v, attacker);
                    return;
                }

                this.lethal(e, g, v, attacker);
            }
            default -> this.lethal(e, g, v, attacker);
        }
    }

    private void lethal(EntityDamageEvent e, Game g, Player v, Player attacker) {
        if (v.getHealth() - e.getFinalDamage() <= 0.0) {
            e.setCancelled(true);
            this.death(g, v, attacker == null ? this.recentAttacker(g, v.getUniqueId()) : attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent e) {
        Game g = this.gameOf(e.getPlayer());
        if (g == null) {
            return;
        }

        Block b = e.getBlockPlaced();
        boolean ok = g.mode == Mode.BRIDGE && g.running && !g.ended && Math.abs(b.getX() - g.c.getBlockX()) <= 15 && Math.abs(b.getZ() - g.c.getBlockZ()) <= 5
            && b.getY() >= g.c.getBlockY() - 4 && b.getY() <= g.c.getBlockY() + 3;
        e.setCancelled(!ok);
        if (ok) {
            g.placed.add(b);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        Game g = this.gameOf(e.getPlayer());
        if (g == null) {
            return;
        }

        Block b = e.getBlock();
        boolean ok = g.running && !g.ended
            && (g.mode == Mode.SPLEEF && b.getType() == Material.SNOW_BLOCK && Math.abs(b.getX() - g.c.getBlockX()) <= 12 && Math.abs(b.getZ() - g.c.getBlockZ()) <= 12
                || g.mode == Mode.BRIDGE && g.placed.remove(b));
        e.setCancelled(!ok);
        if (ok) {
            e.setDropItems(false);
            e.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent e) {
        Game g = this.gameOf(e.getPlayer());
        if (g != null && !g.running && !g.ended) {
            Location to = e.getTo();
            if (to.getX() != e.getFrom().getX() || to.getZ() != e.getFrom().getZ()) {
                Location f = e.getFrom().clone();
                f.setYaw(to.getYaw());
                f.setPitch(to.getPitch());
                e.setTo(f);
            }
        }
    }

    @EventHandler
    public void onArrow(ProjectileHitEvent e) {
        if (e.getEntity() instanceof Arrow a && a.getShooter() instanceof Player p && this.gameOf(p) != null) {
            net.vortexpvp.util.Tasks.later(1L, a::remove);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(PlayerPickupArrowEvent e) {
        if (this.gameOf(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (this.gameOf(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && this.gameOf(p) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.leaveQueue(e.getPlayer());
        Game g = this.byPlayer.remove(e.getPlayer().getUniqueId());
        if (g != null) {
            g.players.remove(e.getPlayer().getUniqueId());
            this.plugin.sessions().get(e.getPlayer()).state = PlayerState.LOBBY;
        }
    }

    public String selfCheck() {
        for (Mode m : Mode.values()) {
            if (this.spawns(new Game(m, new Location(null, 0, 100, 0)), m.max).size() != m.max) {
                return "spawns for " + m;
            }
        }

        return null;
    }
}
