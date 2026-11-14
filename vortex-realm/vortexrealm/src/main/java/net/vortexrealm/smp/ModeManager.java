package net.vortexrealm.smp;

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
import net.vortexrealm.VortexRealm;
import net.vortexrealm.arena.ArenaManager;
import net.vortexrealm.build.SpawnBuilder;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.deco.DecoModels;
import net.vortexrealm.fx.Fx;
import net.vortexrealm.npc.Npc;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Tp;
import net.vortexrealm.util.Cuboid;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Locs;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import net.vortexrealm.world.VoidGenerator;
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
    private final VortexRealm plugin;
    private final File worldsFile;
    private final File groupDir;
    private final Map<String, WorldMode> modes = new HashMap<>();
    private final Map<String, String> displays = new HashMap<>();
    private final Map<String, String> spawns = new HashMap<>();
    private final Map<String, String> generators = new LinkedHashMap<>();
    private final NamespacedKey groupKey;
    private final NamespacedKey hubItemKey;
    /** Worlds worlds.yml gives an SMP mode: the SMP now runs on its own server with VortexSMP, so they are left alone. */
    private final List<String> retired = new ArrayList<>();
    private ConfigurationSection old;
    /** Last spot of each player in each SMP world (the selector brings them back there). */
    private final File locFile;
    private final Map<UUID, Map<String, String>> lastLoc = new ConcurrentHashMap<>();
    private boolean locDirty;
    private final File backupDir;

    public ModeManager(VortexRealm plugin) {
        this.plugin = plugin;
        this.worldsFile = new File(plugin.getDataFolder(), "worlds.yml");
        this.groupDir = new File(plugin.getDataFolder(), "inventories/groups");
        this.locFile = new File(plugin.getDataFolder(), "last-locations.yml");
        this.backupDir = new File(plugin.getDataFolder(), "inventories/backups");
        this.groupKey = new NamespacedKey(plugin, "inv_group");
        this.hubItemKey = new NamespacedKey(plugin, "hub_item");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.worldsFile);
        if (!this.worldsFile.exists()) {
            y.options()
                .setHeader(
                    List.of(
                        "World modes (edit in /pvpadmin -> World Manager).",
                        "modes: PVP, HUB, NONE. The SMP runs on its own server now (VortexSMP); worlds with an old",
                        "SMP mode (SURVIVAL, LIFESTEAL, STRENGTH, RANKED, ECO) are kept in this file but not loaded.",
                        "generator: normal, flat or void - worlds listed here are loaded on startup."
                    )
                );
        }

        this.old = y.getConfigurationSection("settings");
        this.retired.clear();
        this.loadLocations();
        ConfigurationSection sec = y.getConfigurationSection("worlds");
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                WorldMode wm = WorldMode.parse(sec.getString(name + ".mode"), WorldMode.NONE);
                this.modes.put(name, wm);
                if (wm.smp() && !sameServer()) {
                    this.retired.add(name);
                }
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

        if (!this.retired.isEmpty()) {
            this.plugin.getLogger().info("Worlds with an old SMP mode " + this.retired + " are left alone now (extra ones are not loaded): the SMP runs on "
                + "its own server (VortexSMP). Their files and every saved inventory are kept; see the README to move them.");
        }

        for (Entry<String, String> e : this.generators.entrySet()) {
            if (Bukkit.getWorld(e.getKey()) == null && !this.retired.contains(e.getKey())) {
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

        this.save();
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options()
            .setHeader(
                List.of(
                    "World modes (edit in /pvpadmin -> World Manager).",
                    "modes: PVP, HUB, NONE. The SMP runs on its own server now (VortexSMP); worlds with an old",
                    "SMP mode (SURVIVAL, LIFESTEAL, STRENGTH, RANKED, ECO) are kept in this file but not loaded.",
                    "generator: normal, flat or void - worlds listed here are loaded on startup."
                )
            );
        if (this.old != null) {
            // the old SMP settings are kept so nothing is lost (VortexSMP can import them)
            y.set("settings", this.old);
        }

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
            return WorldMode.NONE;
        } else {
            WorldMode m = this.modes.get(w.getName());
            if (m != null) {
                return m.smp() && !sameServer() ? WorldMode.NONE : m;
            } else {
                String n = w.getName();

                for (String suffix : List.of("_nether", "_the_end")) {
                    if (n.endsWith(suffix)) {
                        WorldMode parent = this.modes.get(n.substring(0, n.length() - suffix.length()));
                        if (parent == WorldMode.HUB) {
                            return WorldMode.NONE;
                        } else if (parent != null && parent.smp() && sameServer()) {
                            // smp_nether and smp_the_end belong to the smp
                            return parent;
                        }
                    }
                }

                return this.plugin.worlds().managed(w) ? WorldMode.PVP : WorldMode.NONE;
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
        if (mode.smp() && !sameServer()) {
            mode = WorldMode.NONE;
        }

        this.retired.remove(w.getName());
        this.modes.put(w.getName(), mode);
        this.plugin.worlds().setExcluded(w.getName(), mode != WorldMode.PVP);
        WorldRules.applyDefaults(w, mode);
        this.save();

        for (Player p : w.getPlayers()) {
            this.plugin.syncWorldState(p);
            this.stashLegacy(p);
            this.plugin.modeRules().apply(p);
        }
    }

    /** The overworld a nether or end belongs to ("smp_nether" -> "smp"), or the world itself. */
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

    /** The worlds whose old SMP mode is kept in worlds.yml but which are no longer loaded. */
    public List<String> retired() {
        return this.retired;
    }

    /**
     * The SMP moved to its own server, so this plugin no longer swaps inventories. A player who still carries the
     * items of an old SMP inventory group (they logged off in an SMP world on 2.x) gets them saved to
     * {@code inventories/groups/<uuid>/<group>.yml} before the hub gives them its items, so VortexSMP can import
     * them. Nothing is ever cleared without a saved copy.
     */
    public void stashLegacy(Player p) {
        if (sameServer()) {
            // the SMP is on this server: switch inventory groups instead
            this.syncGroup(p);
            return;
        }

        String carried = p.getPersistentDataContainer().get(this.groupKey, PersistentDataType.STRING);
        if (carried == null) {
            carried = net.vortexrealm.util.LegacyKeys.takeString(p, this.groupKey);
        }

        if (carried == null || this.plugin.worlds().managed(p.getWorld()) || this.plugin.worlds().isMarked(p)) {
            return;
        }

        if (!WorldMode.parse(carried, WorldMode.NONE).smp()) {
            p.getPersistentDataContainer().remove(this.groupKey);
            return;
        }

        if (hasItems(p)) {
            File f = this.groupFile(p.getUniqueId(), carried);
            if (f.exists()) {
                f = new File(f.getParentFile(), carried.toLowerCase(Locale.ROOT) + "-unsorted-" + System.currentTimeMillis() + ".yml");
            }

            if (!this.saveGroupTo(p, f, carried, false)) {
                return;
            }

            this.plugin.getLogger().info("Saved the old " + carried + " inventory of " + p.getName() + " to " + f.getPath() + " (VortexSMP imports it).");
            p.getInventory().clear();
            p.getEnderChest().clear();
            p.setLevel(0);
            p.setExp(0.0F);
        }

        p.getPersistentDataContainer().remove(this.groupKey);
    }

    /**
     * Safety net before the hub replaces the inventory with its items: anything that is not one of this plugin's
     * own items (hub/lobby items carry its tags) is saved to {@code inventories/groups/<uuid>/hub-unsorted-<time>.yml}
     * first. False when saving failed - the inventory must then be left alone.
     */
    public boolean stashForeign(Player p) {
        boolean foreign = false;
        String ns = this.plugin.getName().toLowerCase(Locale.ROOT);

        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.isEmpty()) {
                continue;
            }

            boolean ours = false;
            if (it.hasItemMeta()) {
                for (NamespacedKey k : it.getItemMeta().getPersistentDataContainer().getKeys()) {
                    ours |= k.getNamespace().equals(ns);
                }
            }

            if (!ours) {
                foreign = true;
                break;
            }
        }

        if (!foreign) {
            return true;
        }

        File f = new File(this.groupDir, p.getUniqueId() + "/hub-unsorted-" + System.currentTimeMillis() + ".yml");
        if (!this.saveGroupTo(p, f, "HUB", false)) {
            return false;
        }

        this.plugin.getLogger().info("Saved the items " + p.getName() + " carried into the hub to " + f.getPath() + " before giving the hub items.");
        return true;
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

    // ------------------------------------------------------------------ the SMP on this server

    public static boolean sameServer() {
        return Cfg.bool(Opt.SMP_SAME_SERVER);
    }

    /** True for a world the SMP plays in (any SMP mode, with its nether and end). */
    public boolean smpWorld(World w) {
        return sameServer() && w != null && this.mode(w).smp();
    }

    /** The first SMP world (the survival one when there is one), for the selector's SMP entry. */
    public World smpWorld() {
        World first = null;
        for (Map.Entry<String, WorldMode> e : this.modes.entrySet()) {
            World w = Bukkit.getWorld(e.getKey());
            if (w != null && e.getValue().smp()) {
                if (e.getValue() == WorldMode.SURVIVAL) {
                    return w;
                }

                first = first == null ? w : first;
            }
        }

        return first;
    }

    /** The SMP overworlds (no nethers or ends), loaded ones only. */
    public List<World> smpWorlds() {
        List<World> out = new ArrayList<>();
        for (Map.Entry<String, WorldMode> e : this.modes.entrySet()) {
            World w = Bukkit.getWorld(e.getKey());
            if (w != null && e.getValue().smp() && sameServer()) {
                out.add(w);
            }
        }

        return out;
    }

    /** The inventory group of a world: HUB, an SMP mode, or null for the PvP worlds (their own inventory system). */
    private String groupOf(World w) {
        if (w == null || this.plugin.worlds().managed(w)) {
            return null;
        }

        WorldMode m = this.mode(w);
        return m == WorldMode.HUB ? "HUB" : (m.smp() ? m.name() : null);
    }

    /** The group the player's live inventory belongs to (their marker). */
    public String carried(Player p) {
        String c = p.getPersistentDataContainer().get(this.groupKey, PersistentDataType.STRING);
        return c != null ? c : net.vortexrealm.util.LegacyKeys.takeString(p, this.groupKey);
    }

    /**
     * Keeps each world group's inventory, ender chest, XP and health apart (hub, survival SMP, eco SMP...). When the
     * player's live inventory belongs to another group than the world they are in, it is saved to that group's file
     * (the previous file kept as .prev.yml, a timestamped backup made) and the world's group is loaded exactly.
     * Items of an unknown group are never written over a saved inventory: they go to an "-unsorted-" file.
     */
    public void syncGroup(Player p) {
        if (!sameServer() || this.plugin.worlds().isMarked(p)) {
            return;
        }

        String target = this.groupOf(p.getWorld());
        if (target == null) {
            return;
        }

        String carried = this.carried(p);
        if (target.equals(carried)) {
            return;
        }

        if (carried == null && target.equals("HUB")) {
            // the hub gives its own items: anything else they carry is kept in a file first
            if (this.stashForeign(p)) {
                p.getPersistentDataContainer().set(this.groupKey, PersistentDataType.STRING, target);
            }

            return;
        }

        if (carried == null) {
            if (hasItems(p)) {
                // items of an unknown group (no marker): keep them safe, never over a saved inventory
                File existing = this.groupFile(p.getUniqueId(), target);
                File f = existing.exists()
                    ? new File(existing.getParentFile(), target.toLowerCase(Locale.ROOT) + "-unsorted-" + System.currentTimeMillis() + ".yml")
                    : existing;
                if (!this.saveGroupTo(p, f, target, false)) {
                    return;
                }

                if (existing.exists() && !f.equals(existing)) {
                    this.plugin.getLogger().warning(p.getName() + " had items without an inventory group; they were kept in " + f.getName()
                        + " (/pvpadmin invbackup " + p.getName() + ") and " + existing.getName() + " was loaded.");
                } else {
                    // nothing saved yet for this world: what they carry becomes its inventory
                    p.getPersistentDataContainer().set(this.groupKey, PersistentDataType.STRING, target);
                    return;
                }
            }
        } else {
            if (!this.saveGroup(p, carried)) {
                return;
            }
        }

        this.loadGroup(p, target);
        p.getPersistentDataContainer().set(this.groupKey, PersistentDataType.STRING, target);
    }

    private boolean saveGroup(Player p, String group) {
        boolean ok = this.saveGroupTo(p, this.groupFile(p.getUniqueId(), group), group, true);
        if (ok) {
            this.backup(p, group, false);
        }

        return ok;
    }

    /** Logout and shutdown: the group the player carries is written to its file too (and backed up if it changed). */
    public void saveCurrent(Player p) {
        if (!sameServer() || this.plugin.worlds().isMarked(p)) {
            return;
        }

        String g = this.carried(p);
        if (g != null && !g.equals("HUB") && g.equals(this.groupOf(p.getWorld()))) {
            if (this.saveGroupTo(p, this.groupFile(p.getUniqueId(), g), g, true)) {
                this.backup(p, g, true);
            }
        }
    }

    /**
     * The first time a player enters an SMP group with this version, the newest of its saved copies is used: 3.x put
     * the items of players who had logged off inside the SMP into "&lt;group&gt;-unsorted-&lt;time&gt;.yml", which is
     * newer than &lt;group&gt;.yml. Every other copy stays on disk (and in /pvpadmin invbackup).
     */
    private void pickNewest(UUID u, String group) {
        File main = this.groupFile(u, group);
        File stateFile = new File(main.getParentFile(), "state.yml");
        YamlConfiguration st = YamlConfiguration.loadConfiguration(stateFile);
        if (st.getBoolean("checked." + group)) {
            return;
        }

        String base = group.toLowerCase(Locale.ROOT);
        File[] copies = main.getParentFile().listFiles((d, n) -> n.startsWith(base + "-unsorted-") && n.endsWith(".yml"));
        File best = main.exists() ? main : null;
        long bestTime = best == null ? Long.MIN_VALUE : savedAt(best);
        for (File f : copies == null ? new File[0] : copies) {
            long t = savedAt(f);
            if (t > bestTime) {
                best = f;
                bestTime = t;
            }
        }

        try {
            if (best != null && !best.equals(main)) {
                File keep = new File(this.backupFolder(u), base + "-before-newest-" + System.currentTimeMillis() + ".yml");
                if (main.exists()) {
                    keep.getParentFile().mkdirs();
                    Files.copy(main.toPath(), keep.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                Files.copy(best.toPath(), main.toPath(), StandardCopyOption.REPLACE_EXISTING);
                this.plugin.getLogger().info("Using the newer saved " + group + " inventory " + best.getName() + " for " + u + " (the older one is a backup).");
            }

            st.set("checked." + group, true);
            main.getParentFile().mkdirs();
            st.save(stateFile);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not check the saved " + group + " inventories of " + u, e);
        }
    }

    private static long savedAt(File f) {
        long t = YamlConfiguration.loadConfiguration(f).getLong("saved", 0L);
        return t > 0L ? t : f.lastModified();
    }

    /** Loads a group's saved inventory exactly (empty when the group has nothing saved yet). */
    private void loadGroup(Player p, String group) {
        if (!group.equals("HUB")) {
            this.pickNewest(p.getUniqueId(), group);
        }

        File f = this.groupFile(p.getUniqueId(), group);
        p.getInventory().clear();
        p.getEnderChest().clear();
        p.setLevel(0);
        p.setExp(0.0F);
        if (!f.exists()) {
            p.setGameMode(group.equals("HUB") ? GameMode.ADVENTURE : GameMode.SURVIVAL);
            p.setFoodLevel(20);
            p.updateInventory();
            return;
        }

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
            try {
                GameMode gm = GameMode.valueOf(y.getString("gamemode", "SURVIVAL"));
                p.setGameMode(group.equals("HUB") || gm != GameMode.ADVENTURE ? gm : GameMode.SURVIVAL);
            } catch (IllegalArgumentException ignored) {
                p.setGameMode(GameMode.SURVIVAL);
            }

            double max = p.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.max(1.0, Math.min(max, y.getDouble("health", 20.0))));
            // what they got when entering is a backup too
            this.backup(p, group, true);
        } catch (RuntimeException ex) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not load the " + group + " inventory of " + p.getName() + "; the file was kept.", ex);
        }

        p.updateInventory();
    }

    // ------------------------------------------------------------------ inventory backups

    private File backupFolder(UUID u) {
        return new File(this.backupDir, u.toString());
    }

    /**
     * A timestamped copy of the player's live inventory, ender chest, XP and health for a group, kept in
     * inventories/backups/&lt;uuid&gt;/&lt;group&gt;-&lt;time&gt;.yml (the oldest beyond smp.backup-keep are removed).
     * {@code onlyIfChanged}: skipped when nothing changed since the last backup.
     */
    public void backup(Player p, String group, boolean onlyIfChanged) {
        if (group == null || group.equals("HUB")) {
            // the hub gives its own items every time: nothing to keep
            return;
        }

        File dir = this.backupFolder(p.getUniqueId());
        File[] mine = dir.listFiles((d, n) -> n.startsWith(group.toLowerCase(Locale.ROOT) + "-") && n.endsWith(".yml"));
        if (onlyIfChanged && mine != null && mine.length > 0) {
            File newest = java.util.Arrays.stream(mine).max(Comparator.comparingLong(File::lastModified)).get();
            YamlConfiguration last = YamlConfiguration.loadConfiguration(newest);
            if (sameItems(p.getInventory().getContents(), last.getString("contents")) && sameItems(p.getEnderChest().getContents(), last.getString("enderchest"))
                && last.getInt("level") == p.getLevel()) {
                return;
            }
        }

        File f = new File(dir, group.toLowerCase(Locale.ROOT) + "-" + new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date()) + ".yml");
        if (this.saveGroupTo(p, f, group, false) && mine != null) {
            int keep = Cfg.i(Opt.SMP_BACKUP_KEEP);
            List<File> all = new ArrayList<>(List.of(mine));
            all.add(f);
            all.sort(Comparator.comparingLong(File::lastModified).reversed());
            for (int i = keep; i < all.size(); i++) {
                all.get(i).delete();
            }
        }
    }

    /** Same items in the same slots (the saved bytes can differ for equal items). */
    private static boolean sameItems(ItemStack[] live, String saved) {
        try {
            ItemStack[] old = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(saved == null ? "" : saved));
            for (int i = 0; i < Math.max(live.length, old.length); i++) {
                ItemStack a = i < live.length && live[i] != null && !live[i].isEmpty() ? live[i] : null;
                ItemStack b = i < old.length && old[i] != null && !old[i].isEmpty() ? old[i] : null;
                if (a == null ? b != null : !a.equals(b)) {
                    return false;
                }
            }

            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Every few minutes: a backup of each player in an SMP world (only when something changed). */
    public void backupAll() {
        if (!sameServer()) {
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            String g = this.carried(p);
            if (g != null && g.equals(this.groupOf(p.getWorld()))) {
                this.backup(p, g, true);
            }
        }
    }

    /** The backups of a player, newest first. */
    public List<File> backups(UUID u) {
        File[] fs = this.backupFolder(u).listFiles((d, n) -> n.endsWith(".yml"));
        List<File> out = new ArrayList<>(fs == null ? List.of() : List.of(fs));
        out.sort(Comparator.comparingLong(File::lastModified).reversed());
        return out;
    }

    /**
     * Puts a backup back. When the player is online in that backup's world group it is loaded right away (what they
     * carry now is backed up first); otherwise the group's saved file is replaced (the current one backed up) and
     * they get it the next time they enter that world.
     */
    public String restoreBackup(java.util.UUID u, File backup) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(backup);
        String group = y.getString("group", "SURVIVAL").toUpperCase(Locale.ROOT);
        File target = this.groupFile(u, group);
        Player p = Bukkit.getPlayer(u);
        try {
            if (p != null && group.equals(this.carried(p)) && group.equals(this.groupOf(p.getWorld()))) {
                this.backup(p, group, false);
                Files.copy(backup.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                this.loadGroup(p, group);
                return "Restored " + backup.getName() + " - " + p.getName() + " has it now (what they had is in a new backup).";
            }

            if (target.exists()) {
                File keep = new File(this.backupFolder(u), group.toLowerCase(Locale.ROOT) + "-before-restore-" + System.currentTimeMillis() + ".yml");
                keep.getParentFile().mkdirs();
                Files.copy(target.toPath(), keep.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            target.getParentFile().mkdirs();
            Files.copy(backup.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (p != null && group.equals(this.carried(p))) {
                // they carry this group somewhere else (a hub?) - the switch loads the file next time
                return "Restored " + backup.getName() + " into the saved " + group + " inventory; they get it when they next enter the " + group + " world.";
            }

            return "Restored " + backup.getName() + " into the saved " + group + " inventory (they get it in the " + group + " world).";
        } catch (IOException e) {
            return "Restoring failed: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------ last spots in the SMP worlds

    public void remember(Player p, Location l) {
        if (l != null && l.getWorld() != null && this.smpWorld(l.getWorld())) {
            this.lastLoc.computeIfAbsent(p.getUniqueId(), u -> new ConcurrentHashMap<>()).put(l.getWorld().getName(), Locs.serialize(l));
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
                    Map<String, String> m = new ConcurrentHashMap<>();
                    for (String w : s.getKeys(false)) {
                        m.put(w, s.getString(w));
                    }

                    this.lastLoc.put(UUID.fromString(k), m);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void saveLocations() {
        if (!this.locDirty) {
            return;
        }

        this.locDirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (Entry<UUID, Map<String, String>> e : this.lastLoc.entrySet()) {
            for (Entry<String, String> w : e.getValue().entrySet()) {
                y.set(e.getKey() + "." + w.getKey(), w.getValue());
            }
        }

        try {
            y.save(this.locFile);
        } catch (IOException ex) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save last-locations.yml", ex);
        }
    }

    public File groupFile(UUID u, String group) {
        return new File(this.groupDir, u + "/" + group.toLowerCase(Locale.ROOT) + ".yml");
    }

    public File groupDir() {
        return this.groupDir;
    }

    /** Writes the live inventory, ender chest and XP to a file. */
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

    public Location arrival(Player p, World w) {
        if (this.mode(w) == WorldMode.PVP) {
            return this.plugin.lobby().lobby();
        } else if (this.mode(w) == WorldMode.HUB) {
            return this.spawn(w);
        } else {
            Location last = this.last(p, w);
            last = last == null ? null : safeSpot(last);
            return last != null ? last : this.spawn(w);
        }
    }

    /** The spot itself when it stands on solid ground with room to stand, else the top block there (null: none). */
    private static Location safeSpot(Location l) {
        World w = l.getWorld();
        org.bukkit.block.Block feet = l.getBlock();
        if (!feet.getType().isSolid() && !feet.getRelative(0, 1, 0).getType().isSolid()) {
            for (int i = 1; i <= 3; i++) {
                org.bukkit.block.Block under = feet.getRelative(0, -i, 0);
                if (under.getType().isSolid()) {
                    return i == 1 ? l : new Location(w, l.getX(), under.getY() + 1.0, l.getZ(), l.getYaw(), l.getPitch());
                }

                if (under.isLiquid()) {
                    break;
                }
            }
        }

        int y = w.getHighestBlockYAt(l.getBlockX(), l.getBlockZ());
        if (y <= w.getMinHeight() || !w.getBlockAt(l.getBlockX(), y, l.getBlockZ()).getType().isSolid()) {
            return null;
        }

        return new Location(w, l.getBlockX() + 0.5, y + 1.0, l.getBlockZ() + 0.5, l.getYaw(), l.getPitch());
    }

    public void travel(Player p, World w) {
        PlayerState st = this.plugin.sessions().state(p);
        if (!st.fighting() && st != PlayerState.PREMATCH && st != PlayerState.EDITING) {
            {
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
                            p.setFallDistance(0.0F);
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
                : SpawnBuilder.Style.HUB;
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
            admin.sendMessage(Text.mm("<yellow>Building the " + style.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " spawn (radius " + (style.voxel() ? radius : rad) + ")..."));
            ArenaManager.preload(at.getWorld(), area)
                .thenComposeAsync(v -> this.plugin.jobs().submit(r.job()), Tasks.MAIN)
                .whenComplete((v, err) -> Tasks.ensureSync(() -> this.finishBuild(admin, style, use, r, at, radius, err)));
        }
    }

    /** After a spawn build: sets the spawn, places NPCs and features, and remembers the build for a later upgrade. */
    private void finishBuild(Player admin, SpawnBuilder.Style style, SpawnBuilder.Style use, SpawnBuilder.Result r, Location at, int radius, Throwable err) {
        if (err != null) {
            this.plugin.getLogger().log(Level.SEVERE, "Building a spawn failed", err);
            admin.sendMessage(Text.mm("<red>Building failed - see the console."));
            return;
        }

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

        if (style.voxel()) {
            YamlConfiguration y = new YamlConfiguration();
            y.set("style", style.name());
            y.set("role", use.name());
            y.set("world", at.getWorld().getName());
            y.set("x", at.getBlockX());
            y.set("y", at.getBlockY());
            y.set("z", at.getBlockZ());
            y.set("radius", radius);
            try {
                y.save(new File(this.plugin.getDataFolder(), "hub-build.yml"));
            } catch (IOException e) {
                this.plugin.getLogger().warning("Could not save hub-build.yml: " + e.getMessage());
            }
        }

        admin.sendMessage(Text.mm("<green>Spawn built and set! <gray>NPCs placed: <white>" + r.npcSpots().size() + "</white>. Edit them with <yellow>/npc</yellow>."));
        if (admin.isOnline()) {
            Tp.to(admin, spawn);
        }
    }

    /**
     * Rebuilds the lobby in the same place with the newest version of its design (for the Town Square: the castle
     * town). A full backup is taken, players inside are kept safe in spectator mode, the old build is cleared, the new
     * one is built, and NPCs, the bots yard and the spawn are put back without duplicates. Lobbies built before this
     * version are found from the spawn they set (the Town Square spawn is the origin + (0.5, 0, 53.5)).
     */
    public void upgradeHub(Player admin) {
        File f = new File(this.plugin.getDataFolder(), "hub-build.yml");
        SpawnBuilder.Style style;
        SpawnBuilder.Style role;
        Location at;
        int radius;
        if (f.isFile()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            World w = Bukkit.getWorld(y.getString("world", ""));
            try {
                style = SpawnBuilder.Style.valueOf(y.getString("style", "TOWN_SQUARE"));
                role = SpawnBuilder.Style.valueOf(y.getString("role", "HUB"));
            } catch (IllegalArgumentException e) {
                admin.sendMessage(Text.mm("<red>hub-build.yml names an unknown style."));
                return;
            }

            if (w == null) {
                admin.sendMessage(Text.mm("<red>The lobby's world isn't loaded."));
                return;
            }

            at = new Location(w, y.getInt("x"), y.getInt("y"), y.getInt("z"));
            radius = y.getInt("radius", style.radius);
        } else {
            // an older build: work the origin out from the spawn of this world (hub) or the PvP lobby
            World w = admin.getWorld();
            boolean pvp = this.plugin.worlds().managed(w);
            Location s = pvp ? this.plugin.lobby().lobby() : this.spawn(w);
            if (s == null || s.getWorld() == null) {
                admin.sendMessage(Text.mm("<red>No lobby spawn in this world. Stand in the lobby's world and try again."));
                return;
            }

            at = new Location(s.getWorld(), Math.floor(s.getX() - 0.5), Math.floor(s.getY()), Math.floor(s.getZ() - 53.5));
            Material lamp = s.getWorld().getBlockAt(at.getBlockX(), at.getBlockY() - 4, at.getBlockZ() + 44).getType();
            if (lamp != Material.SEA_LANTERN) {
                admin.sendMessage(Text.mm("<red>Couldn't find a Town Square at this lobby's spawn. <gray>Stand on the lobby spawn pad and set it as spawn, or rebuild it from the build menu."));
                return;
            }

            style = SpawnBuilder.Style.TOWN_SQUARE;
            role = pvp ? SpawnBuilder.Style.PVP : SpawnBuilder.Style.HUB;
            radius = style.radius;
        }

        try {
            this.plugin.configBackup().backup(net.vortexrealm.data.ConfigBackup.Kind.FULL);
        } catch (IOException | RuntimeException e) {
            admin.sendMessage(Text.mm("<red>The backup failed, so nothing was changed: " + Text.esc(String.valueOf(e.getMessage()))));
            return;
        }

        SpawnBuilder.Result r = SpawnBuilder.plan(at, style, radius);
        Cuboid area = r.area();
        World w = at.getWorld();
        Map<UUID, GameMode> held = new java.util.HashMap<>();
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            if (area.contains(l.getBlockX(), l.getBlockY(), l.getBlockZ())) {
                held.put(p.getUniqueId(), p.getGameMode());
                p.setGameMode(GameMode.SPECTATOR);
                p.sendMessage(Text.mm("<gold>The lobby is being upgraded <gray>- you're in spectator mode for a moment."));
            }
        }

        admin.sendMessage(Text.mm("<yellow>Upgrading the " + style.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " (radius " + radius + "). <gray>A full backup was saved first."));
        ArenaManager.preload(w, area)
            .thenRun(() -> Tasks.ensureSync(() -> {
                if (this.plugin.botHall() != null) {
                    this.plugin.botHall().removeIn(area, w);
                }

                if (this.plugin.season() != null) {
                    // the lobby is rebuilt from scratch: the winter record no longer matches it
                    this.plugin.season().forget();
                }
            }))
            .thenComposeAsync(v -> this.plugin.jobs().submit(ArenaManager.clearJob(w, area)), Tasks.MAIN)
            .thenComposeAsync(v -> this.plugin.jobs().submit(r.job()), Tasks.MAIN)
            .whenComplete((v, err) -> Tasks.ensureSync(() -> {
                this.finishBuild(admin, style, role, r, at, radius, err);
                if (err == null && this.plugin.season() != null) {
                    this.plugin.season().refresh();
                }

                Location spawn = r.spawn();
                for (Map.Entry<UUID, GameMode> e : held.entrySet()) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null) {
                        p.setGameMode(e.getValue());
                        Tp.to(p, spawn);
                    }
                }
            }));
    }

    /**
     * Holograms and decorations a design asks for: "lb:&lt;kit&gt;:&lt;design&gt;:&lt;stat&gt;" a leaderboard hologram,
     * "holo:&lt;preset&gt;:&lt;design&gt;" a text hologram from a preset, "deco:&lt;model&gt;" a 3D decoration.
     */
    private void placeFeatures(Map<String, Location> features) {
        // the Training Grounds wing of a hub: its bot-map mobs and NPCs (bots.hall-in-hub)
        Map<String, Location> bot = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Location> f : features.entrySet()) {
            if (f.getKey().startsWith("botmap:") || f.getKey().startsWith("botnpc:")) {
                bot.put(f.getKey(), f.getValue());
            }
        }

        if (!bot.isEmpty() && this.plugin.botHall() != null && Cfg.bool(Opt.BOTS_HALL_IN_HUB)) {
            this.plugin.botHall().placeFeatures(bot);
        }

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

                if (this.plugin.serverSelector() != null) {
                    for (ServerSelector.Entry se : this.plugin.serverSelector().servers()) {
                        plans.add(
                            new Plan(
                                w + "_" + se.id,
                                List.of(se.name.isEmpty() ? "<b>" + se.id.toUpperCase(Locale.ROOT) : se.name, "{server}", "{blink}▶ Click to join"),
                                Npc.Action.SERVER,
                                se.id,
                                se.icon
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
            .name("<b><gradient:#FFE066:#FFAA00>Server Selector</gradient></b>")
            .lore("<gray>Practice, the SMP server", "<gray>and every other server.", "", "<green>▶ Click to choose")
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
}
