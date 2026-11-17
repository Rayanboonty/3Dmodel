package net.practicepvp.ffa;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.arena.ArenaGenerator;
import net.practicepvp.arena.ArenaManager;
import net.practicepvp.arena.ArenaTheme;
import net.practicepvp.arena.ChangeTracker;
import net.practicepvp.arena.RegionSnapshot;
import net.practicepvp.arena.VanillaTerrain;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Msg;
import net.practicepvp.config.Opt;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.data.Setting;
import net.practicepvp.fx.Fx;
import net.practicepvp.kit.Kit;
import net.practicepvp.match.InventorySnapshot;
import net.practicepvp.session.PlayerReset;
import net.practicepvp.session.PlayerState;
import net.practicepvp.session.Session;
import net.practicepvp.session.Tp;
import net.practicepvp.util.Cuboid;
import net.practicepvp.util.Fmt;
import net.practicepvp.util.Pos;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class FfaManager {
    private final PracticePlugin plugin;
    private final File file;
    private final File snapDir;
    private final Map<String, FfaArena> arenas = new LinkedHashMap<>();

    public FfaManager(PracticePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ffa.yml");
        this.snapDir = new File(plugin.getDataFolder(), "snapshots");
    }

    /** Done once every snapshot read at start-up is in place (start-up checks wait for it). */
    private volatile CompletableFuture<Void> snapshotsReady = CompletableFuture.completedFuture(null);

    public CompletableFuture<Void> snapshotsReady() {
        return this.snapshotsReady;
    }

    private CompletableFuture<Void> loadSnapshot(FfaArena a, File snap) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return RegionSnapshot.load(snap);
            } catch (IOException | RuntimeException var3) {
                this.plugin.getLogger().warning("Snapshot of FFA arena " + a.id + " is unreadable: " + var3.getMessage());
                return null;
            }
        }).thenAcceptAsync(r -> {
            if (r != null && a.snapshot == null) {
                a.snapshot = r;
            }
        }, Tasks.MAIN).exceptionally(e -> null);
    }

    public void load() {
        List<CompletableFuture<Void>> snaps = new ArrayList<>();
        this.arenas.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection root = y.getConfigurationSection("ffa");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s != null) {
                    FfaArena a = new FfaArena(id, s.getString("name", id));
                    a.kitId = s.getString("kit");
                    a.worldName = s.getString("world");
                    a.bounds = Cuboid.parse(s.getString("bounds"));

                    for (String sp : s.getStringList("spawns")) {
                        Pos p = Pos.parse(sp);
                        if (p != null) {
                            a.spawns.add(p);
                        }
                    }

                    a.enabled = s.getBoolean("enabled", true);
                    a.generating = s.getBoolean("generating", false);
                    a.theme = ArenaTheme.parse(s.getString("theme"));
                    Material icon = Material.matchMaterial(s.getString("icon", a.theme.icon.name()));
                    a.iconBlock = icon == null ? a.theme.icon : icon;
                    a.generated = s.getBoolean("generated", false);
                    a.seed = s.getLong("seed");
                    a.size = s.getInt("size");
                    a.genSignature = s.getString("gen-signature");
                    a.handEdited = s.getBoolean("hand-edited", false);
                    a.site = s.getString("vanilla-site");
                    File snap = this.snapshotFile(a);
                    if (snap.exists()) {
                        snaps.add(this.loadSnapshot(a, snap));
                    }

                    this.arenas.put(id, a);
                }
            }

            this.plugin.getLogger().info("Loaded " + this.arenas.size() + " FFA arenas.");
        }
        // snapshots are read off the main thread, all at once (they were most of the start-up time)
        this.snapshotsReady = CompletableFuture.allOf(snaps.toArray(new CompletableFuture[0]));
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("PracticePvP FFA arenas. Use /pvpadmin -> FFA Arenas to edit."));

        for (FfaArena a : this.arenas.values()) {
            String b = "ffa." + a.id + ".";
            y.set(b + "name", a.displayName);
            y.set(b + "kit", a.kitId);
            y.set(b + "world", a.worldName);
            y.set(b + "bounds", a.bounds == null ? null : a.bounds.serialize());
            List<String> spawns = new ArrayList<>();

            for (Pos p : a.spawns) {
                spawns.add(p.serialize());
            }

            y.set(b + "spawns", spawns);
            y.set(b + "enabled", a.enabled);
            y.set(b + "generating", a.generating ? true : null);
            y.set(b + "theme", a.theme.name());
            y.set(b + "icon", a.iconBlock.name());
            y.set(b + "generated", a.generated);
            y.set(b + "seed", a.seed);
            y.set(b + "size", a.size);
            y.set(b + "gen-signature", a.genSignature);
            y.set(b + "hand-edited", a.handEdited);
            y.set(b + "vanilla-site", a.site);
        }

        try {
            y.save(this.file);
        } catch (IOException var8) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save ffa.yml", (Throwable)var8);
        }
    }

    private File snapshotFile(FfaArena a) {
        return new File(this.snapDir, "ffa_" + a.id + ".snap");
    }

    public Collection<FfaArena> all() {
        return Collections.unmodifiableCollection(this.arenas.values());
    }

    public FfaArena get(String id) {
        return id == null ? null : this.arenas.get(id.toLowerCase(Locale.ROOT));
    }

    public FfaArena arenaAt(Location l) {
        for (FfaArena a : this.arenas.values()) {
            if (a.contains(l)) {
                return a;
            }
        }

        return null;
    }

    public FfaArena arenaAt(World w, int x, int y, int z) {
        for (FfaArena a : this.arenas.values()) {
            if (a.bounds != null && w.getName().equals(a.worldName) && a.bounds.contains(x, y, z)) {
                return a;
            }
        }

        return null;
    }

    public int playing() {
        int n = 0;

        for (FfaArena a : this.arenas.values()) {
            n += a.players.size();
        }

        return n;
    }

    public Kit kitOf(FfaArena a) {
        Kit k = this.plugin.kits().get(a.kitId);
        if (k == null && "elytramace".equals(a.kitId) && this.plugin.kits().get("mace") != null) {
            a.kitId = "mace";
            k = this.plugin.kits().get("mace");
        }

        return k;
    }

    public boolean join(Player p, FfaArena a) {
        Session s = this.plugin.sessions().get(p);
        if (!Cfg.bool(Opt.FFA_ENABLED)) {
            Msg.error(p, "ffa.disabled");
            return false;
        } else if (Cfg.bool(Opt.MAINTENANCE) && !p.hasPermission("practice.admin")) {
            Msg.error(p, "general.maintenance");
            return false;
        } else if (s.state != PlayerState.LOBBY && s.state != PlayerState.QUEUE) {
            Msg.error(p, "ffa.not-in-lobby");
            return false;
        } else if (this.plugin.hackerPool() != null && this.plugin.hackerPool().pooled(p)) {
            p.sendMessage(Text.mm("<dark_red>You're in the hacker pool:</dark_red> <gray>FFA is closed for you. Queue a duel to play other pooled players."));
            return false;
        } else if (this.plugin.parties().of(p) != null) {
            Msg.error(p, "ffa.in-party");
            return false;
        } else {
            Kit kit = this.kitOf(a);
            if (kit != null && a.ready()) {
                Location spawn = a.bestSpawn();
                if (spawn == null) {
                    Msg.error(p, "ffa.unavailable");
                    return false;
                } else {
                    this.plugin.queues().leaveAll(p, false);
                    s.state = PlayerState.FFA;
                    s.ffa = a;
                    s.ffaJoinTime = System.currentTimeMillis();
                    a.players.add(p.getUniqueId());
                    this.spawnInto(p, a, kit, spawn);
                    this.plugin.lobby().refreshVisibilityAll();
                    this.plugin.boards().refreshNametag(p);
                    Msg.send(p, "ffa.joined", "arena", a.displayName, "kit", kit.label());
                    Fx.title(
                        p,
                        Text.shadow(Text.mm("<gold><b>FREE FOR ALL"), -12637696),
                        Text.mm("<green>" + Text.esc(a.displayName) + " <dark_gray>•</dark_gray> ").append(kit.label()),
                        4,
                        30,
                        10
                    );
                    Snd.play(p, "entity.player.levelup", 0.6F, 1.6F);
                    return true;
                }
            } else {
                Msg.error(p, "ffa.unavailable");
                return false;
            }
        }
    }

    private void spawnInto(Player p, FfaArena a, Kit kit, Location spawn) {
        Session s = this.plugin.sessions().get(p);
        PlayerReset.full(p, GameMode.SURVIVAL);
        PlayerReset.unfreeze(p, s);
        Tp.to(p, spawn);
        this.border(p, a);
        this.plugin.kits().apply(p, kit, this.plugin.profiles().get(p).layouts.get(kit.id), List.of());
        s.spawnProtectionUntil = System.currentTimeMillis() + Cfg.i(Opt.FFA_SPAWN_PROT) * 1000L;
        s.combatUntil = 0L;
        s.lastAttacker = null;
        if (Cfg.bool(Opt.FFA_SPAWN_ANIMATION)) {
            this.dropIn(p, spawn, kit);
        }
    }

    private void dropIn(Player p, Location spawn, Kit kit) {
        World w = spawn.getWorld();
        int clear = 0;
        int dy = 2;

        while (dy <= 9 && w.getBlockAt(spawn.getBlockX(), spawn.getBlockY() + dy, spawn.getBlockZ()).getType().isAir()) {
            clear = dy++;
        }

        dy = Math.min(7, clear - 1);
        if (dy >= 3) {
            Location high = spawn.clone().add(0.0, dy, 0.0);
            Tp.to(p, high);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, false, false, false));
        }

        Snd.play(p, "block.beacon.activate", 0.6F, 1.8F);
        Fx.title(p, Text.mm("<b>").append(kit.label()), Text.mm("<gray>Free For All"), 0, 26, 8);
        int[] t = new int[]{0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Tasks.timer(
            1L,
            1L,
            () -> {
                int now = (int)(t[0]++);
                if (p.isOnline() && this.plugin.sessions().state(p) == PlayerState.FFA && !(now > 60)) {
                    Location l = p.getLocation();
                    if (now % 2 == 0) {
                        for (int i = 0; i < 3; i++) {
                            double a = (double)now * 0.4 + i * Math.PI * 2.0 / 3.0;
                            w.spawnParticle(Particle.END_ROD, l.getX() + Math.cos(a) * 0.7, l.getY() + 0.2, l.getZ() + Math.sin(a) * 0.7, 1, 0.0, 0.0, 0.0, 0.0);
                        }
                    }

                    if (p.isOnGround() || now == 60) {
                        for (int i = 0; i < 28; i++) {
                            double a = i * Math.PI * 2.0 / 28.0;
                            w.spawnParticle(
                                Particle.DUST,
                                l.getX() + Math.cos(a) * 1.4,
                                l.getY() + 0.1,
                                l.getZ() + Math.sin(a) * 1.4,
                                1,
                                new DustOptions(Color.fromRGB(16766282), 1.4F)
                            );
                        }

                        w.spawnParticle(Particle.CLOUD, l.clone().add(0.0, 0.1, 0.0), 10, 0.4, 0.05, 0.4, 0.04);
                        Snd.at(l, "entity.player.attack.sweep", 0.6F, 1.4F);
                        p.removePotionEffect(PotionEffectType.SLOW_FALLING);
                        Tasks.cancel(task[0]);
                    }
                } else {
                    Tasks.cancel(task[0]);
                }
            }
        );
    }

    private void border(Player p, FfaArena a) {
        WorldBorder wb = Bukkit.createWorldBorder();
        wb.setCenter(a.bounds.centerX(), a.bounds.centerZ());
        wb.setSize(Math.max(a.bounds.sizeX(), a.bounds.sizeZ()));
        wb.setWarningDistance(0);
        wb.setDamageAmount(0.0);
        p.setWorldBorder(wb);
    }

    public boolean leave(Player p, boolean force) {
        Session s = this.plugin.sessions().get(p);
        if (s.state != PlayerState.FFA || s.ffa == null) {
            return false;
        } else if (!force && s.inCombat()) {
            Msg.error(p, "ffa.in-combat", "seconds", s.combatSecondsLeft());
            return false;
        } else {
            s.ffa.players.remove(p.getUniqueId());
            s.ffa = null;
            this.plugin.lobby().sendToLobby(p);
            return true;
        }
    }

    public void handleQuit(Player p) {
        Session s = this.plugin.sessions().get(p);
        if (s.state == PlayerState.FFA && s.ffa != null) {
            FfaArena a = s.ffa;
            if (s.inCombat()) {
                Player killer = s.lastAttacker == null ? null : Bukkit.getPlayer(s.lastAttacker);
                this.creditKill(p, killer, a, true);
            }

            a.players.remove(p.getUniqueId());
            s.ffa = null;
        }
    }

    public void handleDeath(Player victim, Player killer, DamageCause cause) {
        Session s = this.plugin.sessions().get(victim);
        FfaArena a = s.ffa;
        if (a != null) {
            if (killer != null && (killer.equals(victim) || this.plugin.sessions().get(killer).ffa != a)) {
                killer = null;
            }

            this.creditKill(victim, killer, a, false);
            if (Cfg.bool(Opt.FFA_RESPAWN_IN_ARENA)) {
                Kit kit = this.kitOf(a);
                Location spawn = a.bestSpawn();
                if (kit != null && spawn != null) {
                    this.spawnInto(victim, a, kit, spawn);
                    return;
                }
            }

            a.players.remove(victim.getUniqueId());
            s.ffa = null;
            this.plugin.lobby().sendToLobby(victim);
        }
    }

    private void creditKill(Player victim, Player killer, FfaArena a, boolean loggedOut) {
        InventorySnapshot snap = new InventorySnapshot(victim, null, a.displayName);
        this.plugin.snapshots().add(snap);
        PvPProfile vp = this.plugin.profiles().get(victim);
        int lostStreak = vp.ffaStreak;
        vp.ffaDeaths++;
        vp.ffaStreak = 0;
        vp.dirty = true;
        Location at = victim.getLocation();
        Component msg;
        if (killer != null) {
            PvPProfile kp = this.plugin.profiles().get(killer);
            kp.ffaKills++;
            kp.ffaStreak++;
            kp.ffaBestStreak = Math.max(kp.ffaBestStreak, kp.ffaStreak);
            kp.dirty = true;
            try {
                Bukkit.getPluginManager().callEvent(new net.practicepvp.api.FfaKillEvent(killer, victim, a, kp.ffaStreak, lostStreak));
            } catch (RuntimeException var30) {
                this.plugin.getLogger().log(Level.WARNING, "An FfaKillEvent listener failed", (Throwable)var30);
            }
            Fx.killEffect(kp.get(Setting.KILL_EFFECT), at);
            Snd.play(killer, "entity.experience_orb.pickup", 1.0F, 1.6F);
            if (Cfg.bool(Opt.FFA_KILLER_HEAL)) {
                PlayerReset.stats(killer);
                Kit healed = this.kitOf(a);
                if (healed != null) {
                    killer.setMaximumNoDamageTicks(healed.hitDelayTicks());
                }
            }

            if (Cfg.bool(Opt.FFA_KILLER_REFILL)) {
                Kit kit = this.kitOf(a);
                if (kit != null) {
                    this.plugin.kits().apply(killer, kit, kp.layouts.get(kit.id), List.of());
                }
            }

            msg = Msg.get(
                loggedOut ? "ffa.combat-log" : "ffa.kill",
                "victim",
                victim.getName(),
                "killer",
                killer.getName(),
                "hp",
                Fmt.hp(killer.getHealth()),
                "streak",
                kp.ffaStreak
            );
            int streak = kp.ffaStreak;
            if (streak >= 5 && streak % 5 == 0) {
                for (UUID u : a.players) {
                    Player o = Bukkit.getPlayer(u);
                    if (o != null) {
                        Msg.send(o, "ffa.streak", "player", killer.getName(), "streak", streak);
                    }
                }
            }

            killer.sendMessage(Msg.prefix().append(Msg.get("ffa.view-inventory", "player", victim.getName(), "id", snap.id.toString())));
        } else {
            msg = Msg.get(loggedOut ? "ffa.logged-out" : "ffa.died", "victim", victim.getName());
        }

        for (UUID ux : a.players) {
            Player o = Bukkit.getPlayer(ux);
            if (o != null) {
                String mode = this.plugin.profiles().get(o).get(Setting.DEATH_MESSAGES);
                boolean mine = o.equals(victim) || o.equals(killer);
                if ("all".equals(mode) || mine && "mine".equals(mode)) {
                    o.sendMessage(Msg.prefix().append(msg));
                }
            }
        }

        if (!loggedOut) {
            victim.sendMessage(Msg.prefix().append(Msg.get("ffa.view-inventory", "player", victim.getName(), "id", snap.id.toString())));
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        long delay = Cfg.i(Opt.FFA_REGEN_DELAY) * 1000L;

        for (FfaArena a : this.arenas.values()) {
            World w = a.world();
            if (w != null && a.bounds != null && !a.building) {
                List<ChangeTracker.Change> due = a.tracker.drainOlderThan(delay);
                List<ChangeTracker.Change> restore = new ArrayList<>();

                for (ChangeTracker.Change c : due) {
                    if (this.playerNear(a, w, c.x(), c.y(), c.z())) {
                        a.tracker.requeue(c);
                    } else {
                        restore.add(c);
                    }
                }

                if (!restore.isEmpty()) {
                    ChangeTracker.restore(w, restore);
                }

                if (now / 1000L % 5L == 0L) {
                    this.cleanEntities(a, w, false);
                }

                int fullMinutes = Cfg.i(Opt.FFA_FULL_REGEN);
                if (fullMinutes > 0) {
                    long due2 = a.lastFullRegen + fullMinutes * 60000L;
                    if (!a.regenWarned && now >= due2 - 10000L) {
                        a.regenWarned = true;

                        for (UUID u : a.players) {
                            Player o = Bukkit.getPlayer(u);
                            if (o != null) {
                                Msg.send(o, "ffa.regen-warning");
                            }
                        }
                    }

                    if (now >= due2) {
                        this.fullRegen(a);
                    }
                }
            }
        }
    }

    private boolean playerNear(FfaArena a, World w, int x, int y, int z) {
        for (UUID u : a.players) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.getWorld().equals(w)) {
                Location l = p.getLocation();
                if (Math.abs(l.getX() - (x + 0.5)) < 1.6 && Math.abs(l.getZ() - (z + 0.5)) < 1.6 && l.getY() > y - 2.5 && l.getY() < y + 1.5) {
                    return true;
                }
            }
        }

        return false;
    }

    private void cleanEntities(FfaArena a, World w, boolean all) {
        for (Entity e : w.getNearbyEntities(a.bounds.toBox())) {
            if (!(e instanceof Player) && (all || e instanceof Item || e instanceof AbstractArrow arrow && arrow.isInBlock() && arrow.getTicksLived() > 100)) {
                e.remove();
            }
        }
    }

    public CompletableFuture<Void> fullRegen(FfaArena a) {
        a.lastFullRegen = System.currentTimeMillis();
        a.regenWarned = false;
        World w = a.world();
        if (w != null && a.bounds != null) {
            ChangeTracker.restore(w, a.tracker.drain());
            this.cleanEntities(a, w, true);
            CompletableFuture<Void> done = new CompletableFuture<>();
            if (a.snapshot == null) {
                done.complete(null);
            } else {
                a.snapshot.diff(w, 0, 0).whenComplete((fixes, err) -> Tasks.ensureSync(() -> {
                    if (err == null && !fixes.isEmpty()) {
                        this.plugin.jobs().submit(RegionSnapshot.fixJob(w, fixes)).whenComplete((v, e2) -> done.complete(null));
                    } else {
                        done.complete(null);
                    }
                }));
            }

            done.thenRun(() -> Tasks.ensureSync(() -> {
                for (UUID u : a.players) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null) {
                        Block feet = p.getLocation().getBlock();
                        if (feet.getType().isSolid() || feet.getRelative(0, 1, 0).getType().isSolid()) {
                            Location spawn = a.bestSpawn();
                            if (spawn != null) {
                                Tp.to(p, spawn);
                            }
                        }

                        Msg.send(p, "ffa.regenerated");
                    }
                }
            }));
            return done;
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    public boolean ensureArena(Kit kit) {
        if (kit != null && kit.ffa) {
            for (FfaArena a : this.arenas.values()) {
                if (kit.id.equals(a.kitId)) {
                    return false;
                }
            }

            String id = "ffa-" + kit.id;
            int n = 2;

            while (this.arenas.containsKey(id)) {
                id = "ffa-" + kit.id + "-" + n++;
            }

            ArenaTheme theme = kit.playsOn("CRYSTAL") && !ArenaTheme.PLAINS.isVanilla() ? ArenaTheme.FLATLANDS : ArenaTheme.PLAINS;
            this.plugin.getLogger().info("Generating an FFA arena for the kit " + kit.id + " (it had none).");
            this.generate(id, kit.displayName + " FFA", theme, Cfg.i(Opt.FFA_ARENA_SIZE), kit.id);
            return true;
        } else {
            return false;
        }
    }

    public FfaArena create(String id, String name, World world, String kitId) {
        FfaArena a = new FfaArena(id.toLowerCase(Locale.ROOT), name);
        a.worldName = world.getName();
        a.kitId = kitId;
        this.arenas.put(a.id, a);
        this.save();
        return a;
    }

    public CompletableFuture<FfaArena> generate(String id, String name, ArenaTheme theme, int size, String kitId) {
        World w = this.plugin.worlds().practiceWorld();
        int[] origin = this.plugin.arenas().allocateOrigin();
        int ox = origin[0];
        int oz = origin[1];
        long seed = ThreadLocalRandom.current().nextLong();
        Kit genKit = this.plugin.kits().get(kitId);
        boolean crystal = theme == ArenaTheme.FLATLANDS || genKit != null && genKit.playsOn("CRYSTAL");
        FfaArena a = new FfaArena(id.toLowerCase(Locale.ROOT), name);
        a.kitId = kitId;
        a.worldName = w.getName();
        a.theme = theme;
        a.iconBlock = theme.icon;
        a.generated = true;
        a.seed = seed;
        a.size = size;
        a.building = true;
        a.enabled = false;
        a.generating = true;
        CompletableFuture<Void> build;
        try {
        if (theme.isVanilla()) {
            this.arenas.put(a.id, a);
            this.save();
            build = this.plugin.vanilla().cut(theme, Math.max(size, ArenaManager.targetSize(theme, true)), ox, oz, true, crystal, null).thenComposeAsync(cut -> {
                this.applyCut(a, cut, crystal);
                return ArenaManager.preload(w, a.bounds).thenComposeAsync(v -> this.plugin.jobs().submit(cut.snapshot().pasteJob(w, 0, 0, true)), Tasks.MAIN);
            }, Tasks.MAIN);
        } else {
            ArenaGenerator.Plan plan = ArenaGenerator.plan(theme, size, seed, ox, oz, true, Cfg.i(Opt.BUILD_HEIGHT), crystal);
            a.bounds = plan.bounds();
            a.spawns.addAll(plan.ffaSpawns());
            a.genSignature = ArenaGenerator.signature(theme, true, crystal);
            this.arenas.put(a.id, a);
            this.save();
            build = ArenaManager.preload(w, plan.bounds())
                .thenComposeAsync(v -> this.plugin.jobs().submit(ArenaGenerator.buildJob(w, plan)), Tasks.MAIN)
                .thenComposeAsync(v -> this.captureSnapshot(a), Tasks.MAIN);
        }
        } catch (RuntimeException ex) {
            // anything that fails before the build starts must not leave the arena "Generating..." for ever
            build = CompletableFuture.failedFuture(ex);
        }

        CompletableFuture<FfaArena> result = new CompletableFuture<>();
        build.whenComplete((v, err) -> Tasks.ensureSync(() -> {
            a.building = false;
            if (err != null) {
                this.plugin.getLogger().log(Level.SEVERE, "Generating FFA arena " + id + " failed", err);
                if (a.bounds == null || a.spawns.isEmpty() || !ArenaManager.hasBlocks(a)) {
                    // nothing usable was made: no dead entry in the list, the arena pack makes it again next start
                    if (this.arenas.get(a.id) == a) {
                        this.delete(a);
                    }
                } else {
                    a.enabled = true;
                    a.generating = false;
                    this.save();
                }

                result.completeExceptionally(err);
            } else {
                a.enabled = true;
                a.generating = false;
                this.save();
                result.complete(a);
            }
        }));
        return result;
    }

    /** Stores a vanilla cut on the FFA arena and writes its snapshot off-thread. */
    private void applyCut(FfaArena a, VanillaTerrain.Cut cut, boolean crystal) {
        a.bounds = cut.bounds();
        a.spawns.clear();
        a.spawns.addAll(cut.ffaSpawns());
        a.site = cut.site().serialize();
        a.seed = cut.site().seed();
        a.size = cut.site().size();
        a.genSignature = VanillaTerrain.signature(crystal ? Cfg.i(Opt.CRYSTAL_DEPTH) : Cfg.i(Opt.FFA_DEPTH), crystal);
        a.snapshot = cut.snapshot();
        RegionSnapshot snap = cut.snapshot();
        File f = this.snapshotFile(a);
        Tasks.async(() -> {
            try {
                snap.save(f);
            } catch (IOException var4) {
                this.plugin.getLogger().log(Level.WARNING, "Could not save FFA snapshot " + a.id, (Throwable)var4);
            }
        });
        this.save();
    }

    public boolean isOutdated(FfaArena f) {
        if (!f.generated || f.theme == null || f.theme == ArenaTheme.CUSTOM || f.handEdited || f.editing || f.bounds == null || f.world() == null) {
            return false;
        } else {
            boolean crystal = this.crystal(f);
            String want = f.theme.isVanilla()
                ? VanillaTerrain.signature(crystal ? Cfg.i(Opt.CRYSTAL_DEPTH) : Cfg.i(Opt.FFA_DEPTH), crystal)
                : ArenaGenerator.signature(f.theme, true, crystal);
            int target = ArenaManager.targetSize(f.theme, true);
            boolean fixed = f.theme == net.practicepvp.arena.ArenaTheme.MOLE_MINES || f.theme.duel != null;
            return !want.equals(f.genSignature) || f.size > 0 && (fixed ? f.size != target : f.size < target);
        }
    }

    public CompletableFuture<Void> rebuild(FfaArena a) {
        return this.rebuild(a, false);
    }

    public CompletableFuture<Void> rebuild(FfaArena a, boolean sameLayout) {
        boolean wasBuilding = a.building;
        try {
            return this.rebuildNow(a, sameLayout);
        } catch (RuntimeException ex) {
            // a failure before the rebuild starts must not leave the arena "Generating..." for ever
            if (!wasBuilding) {
                a.building = false;
            }

            this.plugin.getLogger().log(Level.SEVERE, "Rebuilding FFA arena " + a.id + " failed", ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    private CompletableFuture<Void> rebuildNow(FfaArena a, boolean sameLayout) {
        World w = a.world();
        if (!a.generated || a.theme == null || a.bounds == null || w == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(a.id + " is not a generated FFA arena"));
        } else if (!a.building && !a.editing) {
            for (UUID u : new ArrayList<>(a.players)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    this.leave(p, true);
                }
            }

            a.building = true;
            a.tracker.drain();
            Cuboid old = a.bounds;
            long seed = sameLayout && a.seed != 0L ? a.seed : ThreadLocalRandom.current().nextLong();
            boolean crystal = this.crystal(a);
            int target = ArenaManager.targetSize(a.theme, true);
            if (a.size > 0 && a.size < target || (a.theme == net.practicepvp.arena.ArenaTheme.MOLE_MINES || a.theme.duel != null) && target > 0) {
                a.size = target;
            }

            if (a.theme.isVanilla()) {
                return this.rebuildVanilla(a, w, old, sameLayout, crystal);
            }

            ArenaGenerator.Plan plan = ArenaGenerator.plan(
                a.theme, a.size > 0 ? a.size : old.sizeX(), seed, old.minX(), old.minZ(), true, Cfg.i(Opt.BUILD_HEIGHT), crystal
            );
            Cuboid nb = plan.bounds();
            Cuboid union = new Cuboid(
                nb.minX(),
                Math.min(old.minY(), nb.minY()),
                nb.minZ(),
                Math.max(old.maxX(), nb.maxX()),
                Math.max(old.maxY(), nb.maxY()),
                Math.max(old.maxZ(), nb.maxZ())
            );
            a.bounds = nb;
            a.spawns.clear();
            a.spawns.addAll(plan.ffaSpawns());
            a.seed = seed;
            this.save();
            Cuboid wipe = ArenaManager.wipeArea(w, union);
            CompletableFuture<Void> result = new CompletableFuture<>();
            ArenaManager.preload(w, wipe)
                .thenComposeAsync(v -> this.plugin.jobs().submit(ArenaManager.clearJob(w, wipe)), Tasks.MAIN)
                .thenComposeAsync(v -> this.plugin.jobs().submit(ArenaGenerator.buildJob(w, plan)), Tasks.MAIN)
                .thenComposeAsync(v -> this.captureSnapshot(a), Tasks.MAIN)
                .whenComplete((v, err) -> Tasks.ensureSync(() -> {
                    a.building = false;
                    a.lastFullRegen = System.currentTimeMillis();
                    a.tracker.drain();
                    if (err == null) {
                        a.genSignature = ArenaGenerator.signature(a.theme, true, crystal);
                        a.handEdited = false;
                    }

                    this.save();
                    if (err != null) {
                        this.plugin.getLogger().log(Level.SEVERE, "Rebuilding FFA arena " + a.id + " failed", err);
                        result.completeExceptionally(err);
                    } else {
                        result.complete(null);
                    }
                }));
            return result;
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException(a.id + " is busy"));
        }
    }

    public boolean crystal(FfaArena a) {
        Kit k = this.kitOf(a);
        return a.theme == ArenaTheme.FLATLANDS || k != null && k.playsOn("CRYSTAL");
    }

    private CompletableFuture<Void> rebuildVanilla(FfaArena a, World w, Cuboid old, boolean sameLayout, boolean crystal) {
        int size = Math.max(a.site == null ? 0 : a.size, ArenaManager.targetSize(a.theme, true));
        VanillaTerrain.Site hint = sameLayout && VanillaTerrain.sameVersion(a.genSignature) ? VanillaTerrain.Site.parse(a.site) : null;
        CompletableFuture<Void> result = new CompletableFuture<>();
        this.plugin.vanilla().cut(a.theme, size, old.minX(), old.minZ(), true, crystal, hint).thenComposeAsync(cut -> {
            Cuboid nb = cut.bounds();
            Cuboid union = new Cuboid(
                Math.min(old.minX(), nb.minX()),
                Math.min(old.minY(), nb.minY()),
                Math.min(old.minZ(), nb.minZ()),
                Math.max(old.maxX(), nb.maxX()),
                Math.max(old.maxY(), nb.maxY()),
                Math.max(old.maxZ(), nb.maxZ())
            );
            this.applyCut(a, cut, crystal);
            Cuboid wipe = ArenaManager.wipeArea(w, union);
            return ArenaManager.preload(w, wipe)
                .thenComposeAsync(v -> this.plugin.jobs().submit(ArenaManager.clearJob(w, wipe)), Tasks.MAIN)
                .thenComposeAsync(v -> this.plugin.jobs().submit(cut.snapshot().pasteJob(w, 0, 0, true)), Tasks.MAIN);
        }, Tasks.MAIN).whenComplete((v, err) -> Tasks.ensureSync(() -> {
            a.building = false;
            a.lastFullRegen = System.currentTimeMillis();
            a.tracker.drain();
            if (err == null) {
                a.handEdited = false;
            }

            this.save();
            if (err != null) {
                this.plugin.getLogger().log(Level.SEVERE, "Rebuilding FFA arena " + a.id + " failed", err);
                result.completeExceptionally(err);
            } else {
                result.complete(null);
            }
        }));
        return result;
    }

    public CompletableFuture<Void> captureSnapshot(FfaArena a) {
        World w = a.world();
        return w != null && a.bounds != null ? RegionSnapshot.capture(this.plugin, w, a.bounds).thenAccept(snap -> {
            a.snapshot = snap;

            try {
                snap.save(this.snapshotFile(a));
            } catch (IOException var4) {
                this.plugin.getLogger().log(Level.WARNING, "Could not save FFA snapshot " + a.id, (Throwable)var4);
            }
        }) : CompletableFuture.failedFuture(new IllegalStateException("FFA arena world/bounds missing"));
    }

    public void delete(FfaArena a) {
        for (UUID u : new ArrayList<>(a.players)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                this.leave(p, true);
            }
        }

        this.arenas.remove(a.id);
        File f = this.snapshotFile(a);
        if (f.exists() && !f.delete()) {
            this.plugin.getLogger().warning("Could not delete " + f);
        }

        this.save();
    }

    public void kickAll() {
        for (FfaArena a : this.arenas.values()) {
            for (UUID u : new ArrayList<>(a.players)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    this.leave(p, true);
                }
            }
        }
    }

    public boolean exists(String id) {
        return this.arenas.containsKey(id.toLowerCase(Locale.ROOT));
    }
}
