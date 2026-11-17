package net.vortexpvp.smp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.stream.Stream;
import net.kyori.adventure.util.TriState;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.arena.ArenaManager;
import net.vortexpvp.build.SpawnBuilder;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.deco.DecoModels;
import net.vortexpvp.fx.Fx;
import net.vortexpvp.npc.Npc;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Cuboid;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Locs;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import net.vortexpvp.world.VoidGenerator;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class ModeManager {
    private final VortexPvP plugin;
    private final File worldsFile;
    private final File statsFile;
    private final File locFile;
    private final File groupDir;
    private final Map<String, WorldMode> modes = new HashMap<>();
    private final Map<String, String> displays = new HashMap<>();
    private final Map<String, String> spawns = new HashMap<>();
    private final Map<String, String> generators = new LinkedHashMap<>();
    // concurrent: other plugins read balances through Vault from their own threads
    private final Map<UUID, ModeManager.Stats> stats = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> lastLoc = new HashMap<>();
    private final NamespacedKey groupKey;
    private final InvRestore restore;
    private final NamespacedKey hubItemKey;
    private boolean statsDirty;
    private boolean locDirty;
    public int startHearts = 10;
    public int maxHearts = 20;
    public int eliminationHours = 12;
    public int reviveHearts = 5;
    public int maxStrength = 5;
    public int killPoints = 20;
    public int deathPoints = 15;
    public double startBalance = 100.0;

    public ModeManager(VortexPvP plugin) {
        this.plugin = plugin;
        this.worldsFile = new File(plugin.getDataFolder(), "worlds.yml");
        this.statsFile = new File(plugin.getDataFolder(), "smp-stats.yml");
        this.locFile = new File(plugin.getDataFolder(), "last-locations.yml");
        this.groupDir = new File(plugin.getDataFolder(), "inventories/groups");
        this.groupKey = new NamespacedKey(plugin, "inv_group");
        this.restore = new InvRestore(plugin, this);
        this.hubItemKey = new NamespacedKey(plugin, "hub_item");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.worldsFile);
        if (!this.worldsFile.exists()) {
            y.options()
                .setHeader(
                    List.of(
                        "World modes (edit in /pvpadmin -> World Manager).",
                        "modes: PVP, HUB, SURVIVAL, LIFESTEAL, STRENGTH, RANKED, ECO.",
                        "generator: normal, flat or void - worlds listed here are loaded on startup."
                    )
                );
        }

        this.startHearts = y.getInt("settings.lifesteal.start-hearts", 10);
        this.maxHearts = y.getInt("settings.lifesteal.max-hearts", 20);
        this.eliminationHours = y.getInt("settings.lifesteal.elimination-hours", 12);
        this.reviveHearts = y.getInt("settings.lifesteal.revive-hearts", 5);
        this.maxStrength = y.getInt("settings.strength.max-level", 5);
        this.killPoints = y.getInt("settings.ranked.kill-points", 20);
        this.deathPoints = y.getInt("settings.ranked.death-points", 15);
        this.startBalance = y.getDouble("settings.eco.start-balance", 100.0);
        ConfigurationSection sec = y.getConfigurationSection("worlds");
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                this.modes.put(name, WorldMode.parse(sec.getString(name + ".mode"), WorldMode.SURVIVAL));
                String d = sec.getString(name + ".display");
                if (d != null) {
                    this.displays.put(name, d);
                }

                String s = sec.getString(name + ".spawn");
                if (s != null) {
                    this.spawns.put(name, s);
                }

                String g = sec.getString(name + ".generator");
                if (g != null) {
                    this.generators.put(name, g);
                }
            }
        }

        for (Entry<String, String> e : this.generators.entrySet()) {
            if (Bukkit.getWorld(e.getKey()) == null) {
                try {
                    this.create(e.getKey(), e.getValue());
                } catch (RuntimeException var8) {
                    this.plugin.getLogger().log(Level.WARNING, "Could not load world " + e.getKey(), (Throwable)var8);
                }
            }
        }

        for (Entry<String, WorldMode> ex : this.modes.entrySet()) {
            this.plugin.worlds().setExcluded(ex.getKey(), ex.getValue() != WorldMode.PVP);
        }

        for (World w : new ArrayList<>(Bukkit.getWorlds())) {
            this.ensureDimensions(w);
        }

        this.loadStats();
        this.loadLocations();
        this.save();
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options()
            .setHeader(
                List.of(
                    "World modes (edit in /pvpadmin -> World Manager).",
                    "modes: PVP, HUB, SURVIVAL, LIFESTEAL, STRENGTH, RANKED, ECO.",
                    "generator: normal, flat or void - worlds listed here are loaded on startup."
                )
            );
        y.set("settings.lifesteal.start-hearts", this.startHearts);
        y.set("settings.lifesteal.max-hearts", this.maxHearts);
        y.set("settings.lifesteal.elimination-hours", this.eliminationHours);
        y.set("settings.lifesteal.revive-hearts", this.reviveHearts);
        y.set("settings.strength.max-level", this.maxStrength);
        y.set("settings.ranked.kill-points", this.killPoints);
        y.set("settings.ranked.death-points", this.deathPoints);
        y.set("settings.eco.start-balance", this.startBalance);

        for (Entry<String, WorldMode> e : this.modes.entrySet()) {
            String b = "worlds." + e.getKey() + ".";
            y.set(b + "mode", e.getValue().name());
            y.set(b + "display", this.displays.get(e.getKey()));
            y.set(b + "spawn", this.spawns.get(e.getKey()));
            y.set(b + "generator", this.generators.get(e.getKey()));
        }

        try {
            y.save(this.worldsFile);
        } catch (IOException var5) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save worlds.yml", (Throwable)var5);
        }
    }

    public World create(String name, String type) {
        WorldCreator wc = new WorldCreator(name);
        switch (type.toLowerCase(Locale.ROOT)) {
            case "flat":
                // the classic superflat layers (an empty "{}" makes the server log "No key layers" as an error)
                wc.type(WorldType.FLAT)
                    .generatorSettings(
                        "{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:dirt\",\"height\":2},"
                            + "{\"block\":\"minecraft:grass_block\",\"height\":1}],\"biome\":\"minecraft:plains\"}"
                    );
                break;
            case "void":
                wc.generator(new VoidGenerator()).generateStructures(false);
                break;
            default:
                wc.type(WorldType.NORMAL);
        }

        World w = wc.createWorld();
        if (w != null) {
            this.generators.put(name, type.toLowerCase(Locale.ROOT));
        }

        return w;
    }

    public WorldMode mode(World w) {
        if (w == null) {
            return WorldMode.SURVIVAL;
        } else {
            WorldMode m = this.modes.get(w.getName());
            if (m != null) {
                return m;
            } else {
                String n = w.getName();

                for (String suffix : List.of("_nether", "_the_end")) {
                    if (n.endsWith(suffix)) {
                        WorldMode parent = this.modes.get(n.substring(0, n.length() - suffix.length()));
                        if (parent != null && parent != WorldMode.PVP && parent != WorldMode.HUB) {
                            return parent;
                        }
                    }
                }

                return this.plugin.worlds().managed(w) ? WorldMode.PVP : WorldMode.SURVIVAL;
            }
        }
    }

    public String delete(World w) {
        if (w == null) {
            return "No such world.";
        } else {
            String name = w.getName();
            Location lobby = this.plugin.lobby().lobby();
            if (Bukkit.getWorlds().get(0).equals(w)) {
                return "The server's main world can't be deleted while the server runs. Set it to Nothing instead, or change level-name in server.properties and restart.";
            } else if (name.equals("practice_world")) {
                return "The arena world can't be deleted.";
            } else if (lobby != null && w.equals(lobby.getWorld())) {
                return "The PvP lobby is in this world: set the lobby somewhere else first.";
            } else {
                Location away = this.hubWorld() != null && !this.hubWorld().equals(w) ? this.hub() : lobby;
                if (away == null || w.equals(away.getWorld())) {
                    away = Bukkit.getWorlds().get(0).getSpawnLocation();
                }

                for (Player p : new ArrayList<>(w.getPlayers())) {
                    Tp.to(p, away);
                }

                File folder = w.getWorldFolder();
                if (!Bukkit.unloadWorld(w, false)) {
                    return "The server refused to unload the world (players or chunks still in use) - try again.";
                } else {
                    this.modes.remove(name);
                    this.displays.remove(name);
                    this.spawns.remove(name);
                    this.generators.remove(name);

                    for (Map<String, String> m : this.lastLoc.values()) {
                        m.remove(name);
                    }

                    this.plugin.worlds().setExcluded(name, false);
                    this.save();
                    Tasks.async(() -> {
                        try (Stream<Path> walk = Files.walk(folder.toPath())) {
                            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException var3x) {
                                    this.plugin.getLogger().warning("Could not delete " + path + ": " + var3x.getMessage());
                                }
                            });
                            this.plugin.getLogger().info("World " + name + " deleted.");
                        } catch (IOException var8x) {
                            this.plugin.getLogger().warning("Deleting world " + name + " failed: " + var8x.getMessage());
                        }
                    });
                    return null;
                }
            }
        }
    }

    public void setMode(World w, WorldMode mode) {
        this.modes.put(w.getName(), mode);
        this.plugin.worlds().setExcluded(w.getName(), mode != WorldMode.PVP);
        WorldRules.applyDefaults(w, mode);
        this.save();
        this.ensureDimensions(w);

        for (Player p : w.getPlayers()) {
            this.plugin.syncWorldState(p);
            this.syncGroup(p);
            this.plugin.modeRules().apply(p);
        }
    }

    /** Loads (or creates once) {@code <name>_nether} and {@code <name>_the_end} for an SMP overworld. */
    public void ensureDimensions(World w) {
        if (w != null && Cfg.bool(Opt.SMP_DIMENSIONS) && w.getEnvironment() == World.Environment.NORMAL && this.mode(w).smp()) {
            for (World.Environment env : List.of(World.Environment.NETHER, World.Environment.THE_END)) {
                String name = w.getName() + (env == World.Environment.NETHER ? "_nether" : "_the_end");
                if (Bukkit.getWorld(name) == null) {
                    try {
                        WorldCreator wc = new WorldCreator(name).environment(env).seed(w.getSeed());
                        wc.keepSpawnLoaded(TriState.FALSE);
                        World d = wc.createWorld();
                        if (d != null) {
                            WorldRules.applyDefaults(d, this.mode(w));
                            this.plugin.getLogger().info("Loaded " + name + " for SMP world " + w.getName() + ".");
                        }
                    } catch (RuntimeException var6) {
                        this.plugin.getLogger().log(Level.WARNING, "Could not load " + name, (Throwable)var6);
                    }
                }
            }
        }
    }

    /** The overworld an SMP nether or end belongs to ("smp_nether" -> "smp"), or the world itself. */
    public World parentOf(World w) {
        if (w != null) {
            String n = w.getName();

            for (String suffix : List.of("_nether", "_the_end")) {
                if (n.endsWith(suffix)) {
                    World parent = Bukkit.getWorld(n.substring(0, n.length() - suffix.length()));
                    if (parent != null) {
                        return parent;
                    }
                }
            }
        }

        return w;
    }

    public String display(World w) {
        String d = this.displays.get(w.getName());
        return d != null ? d : this.mode(w).display;
    }

    public void setDisplay(World w, String display) {
        this.displays.put(w.getName(), display);
        this.save();
    }

    public Location spawn(World w) {
        Location l = Locs.parse(this.spawns.get(w.getName()));
        return l != null && l.getWorld() != null ? l : w.getSpawnLocation().add(0.5, 0.0, 0.5);
    }

    public void setSpawn(Location l) {
        this.spawns.put(l.getWorld().getName(), Locs.serialize(l));
        l.getWorld().setSpawnLocation(l);
        this.save();
    }

    public World hubWorld() {
        for (World w : Bukkit.getWorlds()) {
            if (this.mode(w) == WorldMode.HUB) {
                return w;
            }
        }

        return null;
    }

    public Location hub() {
        World h = this.hubWorld();
        return h != null ? this.spawn(h) : this.plugin.lobby().lobby();
    }

    private String groupOf(World w) {
        WorldMode m = this.mode(w);
        return m != WorldMode.PVP && m != WorldMode.NONE ? m.name() : null;
    }

    public void syncGroup(Player p) {
        try {
            this.syncGroupNow(p);
        } finally {
            // items an admin restored with /smpadmin restore are handed over once the player carries that group
            this.restore.applyPending(p);
        }
    }

    private void syncGroupNow(Player p) {
        if (Cfg.bool(Opt.SEPARATE_INVENTORIES) && !this.plugin.worlds().managed(p.getWorld())) {
            if (!this.plugin.worlds().isMarked(p)) {
                String target = this.groupOf(p.getWorld());
                String carried = this.carried(p);
                if (target != null && !target.equals(carried)) {
                    if (carried == null) {
                        if (target.equals(WorldMode.HUB.name()) && hasItems(p)) {
                            String smp = this.defaultSmpGroup();
                            File existing = this.groupFile(p.getUniqueId(), smp);
                            if (existing.exists()) {
                                // we don't know where these items come from: never write them over a saved inventory
                                File unsorted = new File(existing.getParentFile(), smp.toLowerCase(Locale.ROOT) + "-unsorted-" + System.currentTimeMillis() + ".yml");
                                if (!this.saveGroupTo(p, unsorted, smp, false)) {
                                    return;
                                }

                                this.plugin.getLogger().warning(p.getName() + " had items of an unknown inventory group; they were kept in " + unsorted.getName()
                                    + " (restore with /smpadmin restore " + p.getName() + ") and " + existing.getName() + " was left untouched.");
                            } else if (!this.saveGroup(p, smp)) {
                                return;
                            }

                            this.loadGroup(p, target);
                        }

                        p.getPersistentDataContainer().set(this.groupKey, PersistentDataType.STRING, target);
                    } else if (this.saveGroup(p, carried)) {
                        this.loadGroup(p, target);
                        p.getPersistentDataContainer().set(this.groupKey, PersistentDataType.STRING, target);
                    }
                }
            }
        }
    }

    private static boolean hasItems(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && !it.isEmpty()) {
                return true;
            }
        }

        for (ItemStack itx : p.getEnderChest().getContents()) {
            if (itx != null && !itx.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private String defaultSmpGroup() {
        WorldMode first = null;

        for (World w : Bukkit.getWorlds()) {
            WorldMode m = this.mode(w);
            if (m == WorldMode.SURVIVAL) {
                return m.name();
            }

            if (first == null && m.smp()) {
                first = m;
            }
        }

        return (first == null ? WorldMode.SURVIVAL : first).name();
    }

    public File groupFile(UUID u, String group) {
        return new File(this.groupDir, u + "/" + group.toLowerCase(Locale.ROOT) + ".yml");
    }

    public File groupDir() {
        return this.groupDir;
    }

    public InvRestore restore() {
        return this.restore;
    }

    /**
     * The inventory group the player carries right now (marker on the player). A marker left by VortexPvP,
     * VortexRealm or VortexPvP is taken over, so switching plugins never mixes up saved inventories.
     */
    public String carried(Player p) {
        String c = p.getPersistentDataContainer().get(this.groupKey, PersistentDataType.STRING);
        return c != null ? c : net.vortexpvp.util.LegacyKeys.takeString(p, this.groupKey);
    }

    /** The group whose items the player is holding live (null in the PvP worlds, "*" without separate inventories). */
    public String liveGroup(Player p) {
        if (this.plugin.worlds().managed(p.getWorld()) || this.plugin.worlds().isMarked(p)) {
            return null;
        } else if (!Cfg.bool(Opt.SEPARATE_INVENTORIES)) {
            return "*";
        } else {
            String c = this.carried(p);
            String target = this.groupOf(p.getWorld());
            return c != null && c.equals(target) ? c : null;
        }
    }

    private boolean saveGroup(Player p, String group) {
        return this.saveGroupTo(p, this.groupFile(p.getUniqueId(), group), group, true);
    }

    /** Writes the live inventory, ender chest and XP to a file; keepPrevious keeps the old file as name.prev.yml. */
    boolean saveGroupTo(Player p, File f, String group, boolean keepPrevious) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("contents", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(p.getInventory().getContents())));
        y.set("enderchest", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(p.getEnderChest().getContents())));
        y.set("level", p.getLevel());
        y.set("exp", (double)p.getExp());
        y.set("health", p.getHealth());
        y.set("food", p.getFoodLevel());
        y.set("gamemode", p.getGameMode().name());
        y.set("group", group);
        y.set("saved", System.currentTimeMillis());

        try {
            if (!f.getParentFile().exists() && !f.getParentFile().mkdirs()) {
                throw new IOException("cannot create " + f.getParentFile());
            } else {
                File tmp = new File(f.getPath() + ".tmp");
                y.save(tmp);
                if (keepPrevious && f.exists()) {
                    Files.copy(f.toPath(), new File(f.getParentFile(), f.getName().replace(".yml", ".prev.yml")).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        } catch (IOException var6) {
            this.plugin
                .getLogger()
                .log(Level.SEVERE, "COULD NOT SAVE " + group + " INVENTORY OF " + p.getName() + " - not switching inventories.", (Throwable)var6);
            return false;
        }
    }

    private void loadGroup(Player p, String group) {
        File f = this.groupFile(p.getUniqueId(), group);
        p.getInventory().clear();
        p.getEnderChest().clear();
        p.setLevel(0);
        p.setExp(0.0F);
        if (!f.exists()) {
            p.setGameMode(group.equals(WorldMode.HUB.name()) ? GameMode.ADVENTURE : GameMode.SURVIVAL);
            p.setFoodLevel(20);
        } else {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

            try {
                ItemStack[] c = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(y.getString("contents", "")));
                ItemStack[] fixed = new ItemStack[p.getInventory().getContents().length];

                for (int i = 0; i < fixed.length && i < c.length; i++) {
                    fixed[i] = c[i] != null && !c[i].isEmpty() ? c[i] : null;
                }

                p.getInventory().setContents(fixed);
                ItemStack[] e = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(y.getString("enderchest", "")));
                ItemStack[] e2 = new ItemStack[p.getEnderChest().getSize()];

                for (int i = 0; i < e2.length && i < e.length; i++) {
                    e2[i] = e[i] != null && !e[i].isEmpty() ? e[i] : null;
                }

                p.getEnderChest().setContents(e2);
                p.setLevel(y.getInt("level"));
                p.setExp((float)Math.max(0.0, Math.min(1.0, y.getDouble("exp"))));
                p.setFoodLevel(y.getInt("food", 20));
                p.setGameMode(GameMode.valueOf(y.getString("gamemode", "SURVIVAL")));
                double max = p.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : p.getAttribute(Attribute.MAX_HEALTH).getValue();
                p.setHealth(Math.max(1.0, Math.min(max, y.getDouble("health", 20.0))));
            } catch (RuntimeException var11) {
                this.plugin
                    .getLogger()
                    .log(Level.SEVERE, "Could not load " + group + " inventory of " + p.getName() + "; the file was kept.", (Throwable)var11);
            }

            p.updateInventory();
        }
    }

    public void remember(Player p, Location l) {
        if (l != null && l.getWorld() != null && this.mode(l.getWorld()).smp()) {
            this.lastLoc.computeIfAbsent(p.getUniqueId(), u -> new HashMap<>()).put(l.getWorld().getName(), Locs.serialize(l));
            this.locDirty = true;
        }
    }

    public Location last(Player p, World w) {
        Map<String, String> m = this.lastLoc.get(p.getUniqueId());
        Location l = m == null ? null : Locs.parse(m.get(w.getName()));
        return l != null && l.getWorld() != null ? l : null;
    }

    private void loadLocations() {
        this.lastLoc.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.locFile);

        for (String k : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(k);
            if (s != null) {
                try {
                    Map<String, String> m = new HashMap<>();

                    for (String w : s.getKeys(false)) {
                        m.put(w, s.getString(w));
                    }

                    this.lastLoc.put(UUID.fromString(k), m);
                } catch (IllegalArgumentException var8) {
                }
            }
        }
    }

    public ModeManager.Stats stats(UUID u) {
        ModeManager.Stats s = this.stats.computeIfAbsent(u, k -> new ModeManager.Stats());
        if (s.hearts < 0) {
            s.hearts = this.startHearts;
        }

        if (s.balance < 0.0) {
            s.balance = this.startBalance;
        }

        return s;
    }

    public void dirty() {
        this.statsDirty = true;
    }

    public Map<UUID, ModeManager.Stats> allStats() {
        return this.stats;
    }

    private void loadStats() {
        this.stats.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.statsFile);

        for (String k : y.getKeys(false)) {
            try {
                ModeManager.Stats s = new ModeManager.Stats();
                s.hearts = y.getInt(k + ".hearts", this.startHearts);
                s.strength = y.getInt(k + ".strength");
                s.points = y.getInt(k + ".points");
                s.kills = y.getInt(k + ".kills");
                s.deaths = y.getInt(k + ".deaths");
                s.eliminatedUntil = y.getLong(k + ".eliminated-until");
                s.balance = y.getDouble(k + ".balance", -1.0);
                s.shards = y.getLong(k + ".shards");
                s.shardMinutes = y.getInt(k + ".shard-minutes");
                this.stats.put(UUID.fromString(k), s);
            } catch (IllegalArgumentException var5) {
            }
        }
    }

    public void saveData() {
        if (this.statsDirty) {
            this.statsDirty = false;
            YamlConfiguration y = new YamlConfiguration();

            for (Entry<UUID, ModeManager.Stats> e : this.stats.entrySet()) {
                String k = e.getKey().toString();
                ModeManager.Stats s = e.getValue();
                y.set(k + ".hearts", s.hearts);
                y.set(k + ".strength", s.strength);
                y.set(k + ".points", s.points);
                y.set(k + ".kills", s.kills);
                y.set(k + ".deaths", s.deaths);
                y.set(k + ".eliminated-until", s.eliminatedUntil);
                y.set(k + ".balance", Math.round(s.balance * 100.0) / 100.0);
                if (s.shards != 0L || s.shardMinutes != 0) {
                    y.set(k + ".shards", s.shards);
                    y.set(k + ".shard-minutes", s.shardMinutes);
                }
            }

            try {
                y.save(this.statsFile);
            } catch (IOException var7) {
                this.plugin.getLogger().log(Level.WARNING, "Could not save smp-stats.yml", (Throwable)var7);
            }
        }

        if (this.locDirty) {
            this.locDirty = false;
            YamlConfiguration y = new YamlConfiguration();

            for (Entry<UUID, Map<String, String>> e : this.lastLoc.entrySet()) {
                for (Entry<String, String> w : e.getValue().entrySet()) {
                    y.set(e.getKey() + "." + w.getKey(), w.getValue());
                }
            }

            try {
                y.save(this.locFile);
            } catch (IOException var6) {
                this.plugin.getLogger().log(Level.WARNING, "Could not save last-locations.yml", (Throwable)var6);
            }
        }
    }

    public Location arrival(Player p, World w) {
        if (this.mode(w) == WorldMode.PVP) {
            return this.plugin.lobby().lobby();
        } else if (this.mode(w) == WorldMode.HUB) {
            return this.spawn(w);
        } else {
            Location last = this.last(p, w);
            return last != null ? last : this.spawn(w);
        }
    }

    public void travel(Player p, World w) {
        PlayerState st = this.plugin.sessions().state(p);
        if (!st.fighting() && st != PlayerState.PREMATCH && st != PlayerState.EDITING) {
            if (this.plugin.combatTag() != null && this.plugin.combatTag().tagged(p)) {
                p.sendMessage(Text.mm("<red>You can't leave in combat. <gray>(" + this.plugin.combatTag().secondsLeft(p) + "s)"));
                Snd.error(p);
            } else {
                if (this.mode(w) == WorldMode.LIFESTEAL) {
                    ModeManager.Stats s = this.stats(p.getUniqueId());
                    if (s.eliminatedUntil > System.currentTimeMillis() && !p.hasPermission("vortexpvp.admin")) {
                        long min = (s.eliminatedUntil - System.currentTimeMillis()) / 60000L + 1L;
                        p.sendMessage(Text.mm("<red>You were eliminated from Lifesteal. Back in <gold>" + min / 60L + "h " + min % 60L + "m</gold>."));
                        Snd.error(p);
                        return;
                    }
                }

                String name = this.display(w);
                Location target = this.arrival(p, w);
                Fx.shine(p, "TRAVELLING", Fx.GOLD, Text.mm("<gray>to " + this.mode(w).color + "<b>" + Text.esc(name)), 20);
                Snd.play(p, "block.portal.trigger", 0.35F, 1.6F);

                for (int i = 0; i < 14; i += 2) {
                    int t = i;
                    Tasks.later(
                        t,
                        () -> {
                            if (p.isOnline()) {
                                double a = t * 0.6;
                                Location c = p.getLocation();

                                for (int k = 0; k < 3; k++) {
                                    double ang = a + k * Math.PI * 2.0 / 3.0;
                                    p.getWorld()
                                        .spawnParticle(
                                            Particle.PORTAL,
                                            c.getX() + Math.cos(ang) * 0.8,
                                            c.getY() + t * 0.1,
                                            c.getZ() + Math.sin(ang) * 0.8,
                                            3,
                                            0.0,
                                            0.0,
                                            0.0,
                                            0.1
                                        );
                                }
                            }
                        }
                    );
                }

                Tasks.later(14L, () -> {
                    if (p.isOnline()) {
                        PlayerState now = this.plugin.sessions().state(p);
                        if (!now.fighting() && now != PlayerState.PREMATCH) {
                            Tp.to(p, target);
                            Snd.play(p, "entity.enderman.teleport", 0.7F, 1.2F);
                            p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0.0, 1.0, 0.0), 40, 0.5, 0.8, 0.5, 0.05);
                            Fx.title(p, Text.mm(this.mode(w).color + "<b>" + Text.esc(name)), Text.mm("<gray>Welcome!"), 5, 30, 10);
                        }
                    }
                });
            }
        } else {
            p.sendMessage(Text.mm("<red>Finish what you're doing first."));
            Snd.error(p);
        }
    }

    public void buildSpawn(Player admin, SpawnBuilder.Style style, int radius) {
        Location at = admin.getLocation();
        SpawnBuilder.Style role = style;
        if (style.big()) {
            role = this.plugin.worlds().managed(at.getWorld())
                ? SpawnBuilder.Style.PVP
                : (this.mode(at.getWorld()) == WorldMode.HUB ? SpawnBuilder.Style.HUB : SpawnBuilder.Style.SURVIVAL);
        }

        SpawnBuilder.Style use = role;
        if (style == SpawnBuilder.Style.PVP && !this.plugin.worlds().managed(at.getWorld())) {
            admin.sendMessage(Text.mm("<red>The PvP lobby must be in a PvP world (World Manager → PvP Practice)."));
        } else {
            SpawnBuilder.Result r = SpawnBuilder.plan(at, style, radius);
            int rad = Math.max(6, Math.min(style.theme != null ? 80 : (style == SpawnBuilder.Style.COLOSSEUM ? 70 : 56), radius));
            Cuboid area = r.area() != null
                ? r.area()
                : Cuboid.of(
                    at.getBlockX() - rad - 2,
                    at.getBlockY() - 6,
                    at.getBlockZ() - rad - 2,
                    at.getBlockX() + rad + 2,
                    at.getBlockY() + 16,
                    at.getBlockZ() + rad + 2
                );
            admin.sendMessage(Text.mm("<yellow>Building the " + style.name().toLowerCase(Locale.ROOT) + " spawn (radius " + rad + ")..."));
            ArenaManager.preload(at.getWorld(), area)
                .thenComposeAsync(v -> this.plugin.jobs().submit(r.job()), Tasks.MAIN)
                .whenComplete(
                    (v, err) -> Tasks.ensureSync(
                        () -> {
                            if (err != null) {
                                this.plugin.getLogger().log(Level.SEVERE, "Building a spawn failed", err);
                                admin.sendMessage(Text.mm("<red>Building failed - see the console."));
                            } else {
                                Location spawn = r.spawn();
                                switch (use) {
                                    case PVP:
                                        this.plugin.lobby().setLobby(spawn);
                                        break;
                                    default:
                                        this.setSpawn(spawn);
                                }

                                this.placeNpcs(admin, use, r.npcSpots());
                                this.placeFeatureNpcs(admin, r.features());
                                this.placeFeatures(r.features());
                                if (style == SpawnBuilder.Style.CASTLE_MARKET && this.plugin.decos() != null && DecoModels.get("crown") != null) {
                                    // a slowly spinning crown floats over the fountain, like the big items of the MCPVP lobby
                                    this.plugin.decos().placeAt(new Location(at.getWorld(), at.getBlockX() + 0.5, at.getBlockY() + 10.0, at.getBlockZ() + 0.5), "crown", 0.0F);
                                }
                                admin.sendMessage(
                                    Text.mm(
                                        "<green>Spawn built and set! <gray>NPCs placed: <white>"
                                            + r.npcSpots().size()
                                            + "</white>. Edit them with <yellow>/npc</yellow>."
                                    )
                                );
                                if (admin.isOnline()) {
                                    Tp.to(admin, spawn);
                                }
                            }
                        }
                    )
                );
        }
    }

    /**
     * Holograms and decorations a design asks for: "lb:&lt;kit&gt;:&lt;design&gt;:&lt;stat&gt;" a leaderboard hologram,
     * "holo:&lt;preset&gt;:&lt;design&gt;" a text hologram from a preset, "deco:&lt;model&gt;" a 3D decoration.
     */
    private void placeFeatures(Map<String, Location> features) {
        for (Map.Entry<String, Location> f : features.entrySet()) {
            String[] k = f.getKey().split(":");
            Location at = f.getValue();
            try {
                switch (k[0]) {
                    case "lb":
                        if (this.plugin.holograms() != null && k.length >= 4) {
                            this.plugin.holograms().place(at, k[1], k[2], k[3], at.getYaw());
                        }
                        break;
                    case "holo":
                        if (this.plugin.textHolograms() != null && k.length >= 3) {
                            this.plugin.textHolograms().placePreset(k[1], at, k[2]);
                        }
                        break;
                    case "deco":
                        if (this.plugin.decos() != null && DecoModels.get(k[1]) != null) {
                            this.plugin.decos().placeAt(at, k[1], at.getYaw());
                        }
                }
            } catch (RuntimeException var6) {
                this.plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not place " + f.getKey(), var6);
            }
        }
    }

    private void placeFeatureNpcs(Player admin, Map<String, Location> features) {
        Location spec = features.get("spectate");
        if (spec != null) {
            String w = spec.getWorld().getName();
            Npc n = this.plugin
                .npcs()
                .create(
                    (w + "_spectate").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", ""),
                    spec,
                    admin.getName(),
                    List.of("<b><gradient:#B5F2FF:#33C3FF:{phase}>SPECTATE</gradient></b>", "<gray>Watch live matches", "{blink}▶ Click"),
                    Npc.Action.MENU,
                    "spectate"
                );
            n.hand = Material.ENDER_EYE;
            n.particle = "end_rod";
            this.plugin.npcs().update(n);
        }
    }

    private void placeNpcs(Player admin, SpawnBuilder.Style style, List<Location> spots) {
        record Plan(String id, List<String> lines, Npc.Action action, String value, Material hand) {
        }

        List<Plan> plans = new ArrayList<>();
        String w = spots.isEmpty() ? "spawn" : spots.get(0).getWorld().getName();
        switch (style) {
            case HUB:
                plans.add(
                    new Plan(
                        w + "_selector",
                        List.of("<b><gradient:#FFE259:#FFA751:{phase}>SERVER SELECTOR</gradient></b>", "<gray>{online} online", "{blink}▶ Click to play"),
                        Npc.Action.SELECTOR,
                        "",
                        Material.COMPASS
                    )
                );
                plans.add(
                    new Plan(
                        w + "_pvp",
                        List.of("<b><gradient:#FFD54A:#FF8A00:{phase}>PvP PRACTICE</gradient></b>", "<gray>{players} playing", "{blink}▶ Click to play"),
                        Npc.Action.LOBBY,
                        "",
                        Material.DIAMOND_SWORD
                    )
                );

                for (World world : Bukkit.getWorlds()) {
                    WorldMode m = this.mode(world);
                    if (m.smp() && !world.getName().endsWith("_nether") && !world.getName().endsWith("_the_end")) {
                        plans.add(
                            new Plan(
                                w + "_" + world.getName(),
                                List.of(
                                    m.color + "<b>" + Text.esc(this.display(world)).toUpperCase(Locale.ROOT),
                                    "<gray>{players} playing",
                                    "{blink}▶ Click to play"
                                ),
                                Npc.Action.WORLD,
                                world.getName(),
                                m.icon
                            )
                        );
                    }
                }
                break;
            case PVP:
                plans.add(
                    new Plan(
                        w + "_duels",
                        List.of("<b><gradient:#8FE9FF:#2E9BFF:{phase}>DUELS</gradient></b>", "<gray>{players} in PvP", "{blink}▶ Click to queue"),
                        Npc.Action.MENU,
                        "duels",
                        Material.DIAMOND_SWORD
                    )
                );
                plans.add(
                    new Plan(
                        w + "_ranked",
                        List.of("<b><gradient:#FFF3A0:#FF9F00:{phase}>RANKED</gradient></b>", "<gray>LT5 → HT1", "{blink}▶ Click to queue"),
                        Npc.Action.MENU,
                        "ranked",
                        Material.NETHERITE_SWORD
                    )
                );
                plans.add(
                    new Plan(
                        w + "_ffa",
                        List.of("<b><gradient:#FF8A8A:#D90000:{phase}>FREE FOR ALL</gradient></b>", "{blink}▶ Click to fight"),
                        Npc.Action.MENU,
                        "ffa",
                        Material.NETHERITE_AXE
                    )
                );
                plans.add(
                    new Plan(
                        w + "_leaderboard",
                        List.of("<b><gradient:#A8FF8A:#1FAF5A:{phase}>LEADERBOARDS</gradient></b>", "{blink}▶ Top players"),
                        Npc.Action.MENU,
                        "leaderboard",
                        Material.EMERALD
                    )
                );
                plans.add(
                    new Plan(
                        w + "_kiteditor",
                        List.of("<b><gradient:#F3B5FF:#B04DFF:{phase}>KIT EDITOR</gradient></b>", "{blink}▶ Arrange kits"),
                        Npc.Action.MENU,
                        "kiteditor",
                        Material.BOOK
                    )
                );
                plans.add(
                    new Plan(
                        w + "_party",
                        List.of("<b><gradient:#FFF07A:#FFB300:{phase}>PARTIES</gradient></b>", "<gray>Play with friends", "{blink}▶ Click"),
                        Npc.Action.MENU,
                        "party",
                        Material.CAKE
                    )
                );
                plans.add(
                    new Plan(
                        w + "_settings",
                        List.of("<b><gradient:#F0F0F0:#8A8A8A:{phase}>SETTINGS</gradient></b>", "{blink}▶ Your options"),
                        Npc.Action.MENU,
                        "settings",
                        Material.COMPARATOR
                    )
                );
                plans.add(
                    new Plan(
                        w + "_spectate",
                        List.of("<b><gradient:#B5F2FF:#33C3FF:{phase}>SPECTATE</gradient></b>", "<gray>Watch live matches", "{blink}▶ Click"),
                        Npc.Action.MENU,
                        "spectate",
                        Material.ENDER_EYE
                    )
                );
                break;
            default:
                if (this.hubWorld() != null) {
                    plans.add(
                        new Plan(
                            w + "_hub",
                            List.of("<b><gradient:#B5F2FF:#7F5CFF:{phase}>HUB</gradient></b>", "{blink}▶ Back to the hub"),
                            Npc.Action.HUB,
                            "",
                            Material.NETHER_STAR
                        )
                    );
                }

                plans.add(
                    new Plan(
                        w + "_worlds",
                        List.of("<b><gradient:#FFE259:#FFA751:{phase}>WORLDS</gradient></b>", "<gray>{online} online", "{blink}▶ Pick a world"),
                        Npc.Action.SELECTOR,
                        "",
                        Material.COMPASS
                    )
                );
        }

        for (int i = 0; i < plans.size() && i < spots.size(); i++) {
            Plan p = plans.get(i);
            Npc n = this.plugin
                .npcs()
                .create(p.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", ""), spots.get(i), admin.getName(), p.lines(), p.action(), p.value());
            n.hand = p.hand();
            n.particle = "ring";
            this.plugin.npcs().update(n);
        }
    }

    public ItemStack selectorItem() {
        return Items.of(Material.COMPASS)
            .name("<b><gradient:#FFE066:#FFAA00>World Selector</gradient></b>")
            .lore("<gray>Travel to every world and the", "<gray>PvP practice lobby.", "", "<green>▶ Click to choose")
            .tag(this.hubItemKey, "selector")
            .glint(true)
            .hideAll()
            .build();
    }

    public boolean isSelector(ItemStack it) {
        return it != null
            && !it.isEmpty()
            && it.hasItemMeta()
            && "selector".equals(it.getItemMeta().getPersistentDataContainer().get(this.hubItemKey, PersistentDataType.STRING));
    }

    public void selector(Player p) {
        this.plugin.serverSelector().open(p);
    }

    public NamespacedKey hubItemKey() {
        return this.hubItemKey;
    }

    public static String worldLabel(World w) {
        return w.getName().equals("practice_world") ? "arenas (internal)" : w.getName();
    }

    public static final class Stats {
        public int hearts = -1;
        public int strength;
        public int points;
        public int kills;
        public int deaths;
        public long eliminatedUntil;
        public volatile double balance = -1.0;
        /** Second currency (DonutSMP style), earned by playing and by kills. */
        public long shards;
        /** Minutes played in the economy toward the next shard. */
        public int shardMinutes;
    }
}
