package net.vortexpvp.world;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.util.Locs;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.World.Environment;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class WorldManager {
    public static final String PRACTICE_WORLD = "practice_world";
    private final VortexPvP plugin;
    private final File invDir;
    private World practice;

    public WorldManager(VortexPvP plugin) {
        this.plugin = plugin;
        this.invDir = new File(plugin.getDataFolder(), "inventories");
    }

    public void init() {
        this.practice = Bukkit.getWorld("practice_world");
        if (this.practice == null) {
            this.plugin.getLogger().info("Loading/creating practice_world (void world for arenas)...");
            this.practice = new WorldCreator("practice_world")
                .generator(new VoidGenerator())
                .environment(Environment.NORMAL)
                .type(WorldType.FLAT)
                // valid flat settings: an empty "{}" makes the server log "No key layers" as an error on every boot
                .generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:plains\"}")
                .generateStructures(false)
                .createWorld();
        }

        if (this.practice == null) {
            throw new IllegalStateException("Could not create practice_world");
        } else {
            applyArenaRules(this.practice);
        }
    }

    public static Difficulty arenaDifficulty() {
        Difficulty d = Cfg.e(Opt.ARENA_DIFFICULTY, Difficulty.class);
        return d == Difficulty.PEACEFUL ? Difficulty.EASY : d;
    }

    public static void applyArenaRules(World w) {
        w.setDifficulty(arenaDifficulty());
        w.setGameRule(GameRules.PVP, true);
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_MONSTERS, false);
        w.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        w.setGameRule(GameRules.KEEP_INVENTORY, true);
        w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRules.MOB_GRIEFING, true);
        w.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        w.setGameRule(GameRules.SPAWN_PATROLS, false);
        w.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        w.setGameRule(GameRules.RAIDS, false);
        w.setGameRule(GameRules.LOCATOR_BAR, false);
        // vanilla terrain maps must stay exactly as cut: no grass spread, chorus growth, ice melting or leaf decay
        w.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        w.setTime(6000L);
        w.setStorm(false);
        w.setThundering(false);
    }

    public World practiceWorld() {
        return this.practice;
    }

    public boolean managed(World w) {
        if (w == null) {
            return false;
        } else {
            List<String> excluded = Cfg.get(Opt.EXCLUDED_WORLDS);
            String n = w.getName();
            if (excluded.contains(n)) {
                return false;
            } else {
                // a world's own nether and end belong to it (SMP dimensions, and world_nether when "world" is SMP)
                for (String suffix : DIMENSIONS) {
                    if (n.endsWith(suffix) && excluded.contains(n.substring(0, n.length() - suffix.length()))) {
                        return false;
                    }
                }

                return true;
            }
        }
    }

    private static final List<String> DIMENSIONS = List.of("_nether", "_the_end");

    public boolean excluded(String name) {
        return Cfg.get(Opt.EXCLUDED_WORLDS).contains(name);
    }

    public void setExcluded(String world, boolean excluded) {
        if (!world.equals("practice_world")) {
            List<String> list = new ArrayList<>(Cfg.get(Opt.EXCLUDED_WORLDS));
            list.remove(world);
            if (excluded) {
                list.add(world);
            }

            Cfg.set(Opt.EXCLUDED_WORLDS, list);
        }
    }

    private File invFile(Player p) {
        return new File(this.invDir, p.getUniqueId() + ".yml");
    }

    public boolean hasStoredOutside(Player p) {
        return this.invFile(p).exists();
    }

    private NamespacedKey markerKey() {
        return new NamespacedKey(this.plugin, "pvp_inventory");
    }

    public boolean isMarked(Player p) {
        // a marker left by VortexPvP / VortexRealm / VortexPvP counts too (switching plugins)
        return p.getPersistentDataContainer().has(this.markerKey(), PersistentDataType.BYTE) || net.vortexpvp.util.LegacyKeys.takeFlag(p, this.markerKey());
    }

    private void mark(Player p, boolean on) {
        if (on) {
            p.getPersistentDataContainer().set(this.markerKey(), PersistentDataType.BYTE, (byte)1);
        } else {
            p.getPersistentDataContainer().remove(this.markerKey());
        }
    }

    private static boolean hasStuff(Player p) {
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

        return p.getLevel() > 0 || p.getExp() > 0.0F;
    }

    public boolean enterPvP(Player p, Location from) {
        if (this.isMarked(p)) {
            return true;
        } else if (hasStuff(p) && !this.storeOutside(p, from)) {
            return false;
        } else {
            this.mark(p, true);
            return true;
        }
    }

    public void exitPvP(Player p) {
        if (this.isMarked(p)) {
            p.getInventory().clear();
            p.setItemOnCursor(null);
            p.setLevel(0);
            p.setExp(0.0F);
            p.clearActivePotionEffects();
            p.setWorldBorder(null);
            p.resetPlayerTime();
            p.setWalkSpeed(0.2F);
            p.setFlySpeed(0.1F);
            p.setGravity(true);
            if (!this.restoreOutside(p)) {
                p.setGameMode(Bukkit.getDefaultGameMode());
                p.setAllowFlight(p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);
            }

            this.mark(p, false);
            p.updateInventory();
        } else {
            this.restoreOutside(p);
        }
    }

    public boolean storeOutside(Player p, Location from) {
        if (!Cfg.bool(Opt.SEPARATE_INVENTORIES)) {
            return true;
        } else {
            File f = this.invFile(p);
            if (f.exists()) {
                f = new File(this.invDir, p.getUniqueId() + "-backup-" + System.currentTimeMillis() + ".yml");
                this.plugin.getLogger().warning(p.getName() + " already had a stored SMP inventory; current inventory backed up to " + f.getName());
            }

            YamlConfiguration y = new YamlConfiguration();
            y.set("name", p.getName());
            y.set("time", System.currentTimeMillis());
            y.set("location", Locs.serialize(from != null ? from : p.getLocation()));
            y.set("contents", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(p.getInventory().getContents())));
            y.set("enderchest", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(p.getEnderChest().getContents())));
            y.set("level", p.getLevel());
            y.set("exp", (double)p.getExp());
            y.set("health", p.getHealth());
            y.set("food", p.getFoodLevel());
            y.set("saturation", (double)p.getSaturation());
            y.set("fire", p.getFireTicks());
            y.set("gamemode", p.getGameMode().name());
            y.set("flying", p.getAllowFlight());
            List<String> effects = new ArrayList<>();

            for (PotionEffect pe : p.getActivePotionEffects()) {
                effects.add(pe.getType().getKey().getKey() + ":" + pe.getAmplifier() + ":" + pe.getDuration());
            }

            y.set("effects", effects);

            try {
                if (!this.invDir.exists() && !this.invDir.mkdirs()) {
                    throw new IOException("cannot create " + this.invDir);
                }

                File tmp = new File(f.getPath() + ".tmp");
                y.save(tmp);
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException var8) {
                this.plugin
                    .getLogger()
                    .log(Level.SEVERE, "COULD NOT SAVE SMP INVENTORY OF " + p.getName() + " - their inventory will not be cleared.", (Throwable)var8);
                return false;
            }

            p.getEnderChest().clear();
            return true;
        }
    }

    public boolean restoreOutside(Player p) {
        File f = this.invFile(p);
        if (!f.exists()) {
            return false;
        } else {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

            try {
                ItemStack[] contents = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(y.getString("contents", "")));
                p.getInventory().clear();
                ItemStack[] fixed = new ItemStack[p.getInventory().getContents().length];

                for (int i = 0; i < fixed.length && i < contents.length; i++) {
                    fixed[i] = contents[i] != null && !contents[i].isEmpty() ? contents[i] : null;
                }

                p.getInventory().setContents(fixed);
                String ec = y.getString("enderchest");
                if (ec != null && !ec.isEmpty()) {
                    ItemStack[] ender = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(ec));
                    ItemStack[] e2 = new ItemStack[p.getEnderChest().getSize()];

                    for (int i = 0; i < e2.length && i < ender.length; i++) {
                        e2[i] = ender[i] != null && !ender[i].isEmpty() ? ender[i] : null;
                    }

                    p.getEnderChest().setContents(e2);
                }

                p.setLevel(y.getInt("level"));
                p.setExp((float)Math.max(0.0, Math.min(1.0, y.getDouble("exp"))));
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                double maxHp = max == null ? 20.0 : max.getValue();
                p.setHealth(Math.max(1.0, Math.min(maxHp, y.getDouble("health", 20.0))));
                p.setFoodLevel(y.getInt("food", 20));
                p.setSaturation((float)y.getDouble("saturation", 5.0));
                p.setFireTicks(y.getInt("fire", 0));

                try {
                    p.setGameMode(GameMode.valueOf(y.getString("gamemode", "SURVIVAL")));
                } catch (IllegalArgumentException var14) {
                    p.setGameMode(GameMode.SURVIVAL);
                }

                p.setAllowFlight(y.getBoolean("flying", false) || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);

                for (PotionEffect pe : p.getActivePotionEffects()) {
                    p.removePotionEffect(pe.getType());
                }

                for (String s : y.getStringList("effects")) {
                    String[] parts = s.split(":");
                    PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
                    if (type != null && parts.length == 3) {
                        p.addPotionEffect(new PotionEffect(type, Integer.parseInt(parts[2]), Integer.parseInt(parts[1])));
                    }
                }

                p.updateInventory();
            } catch (RuntimeException var15) {
                this.plugin
                    .getLogger()
                    .log(Level.SEVERE, "Could not restore SMP inventory of " + p.getName() + "; the save file was kept at " + f.getName(), (Throwable)var15);
                return false;
            }

            if (!f.delete()) {
                this.plugin.getLogger().warning("Could not delete restored inventory file " + f.getName());
            }

            return true;
        }
    }

    public Location storedOutsideLocation(Player p) {
        File f = this.invFile(p);
        return !f.exists() ? null : Locs.parse(YamlConfiguration.loadConfiguration(f).getString("location"));
    }

    public Location firstExcludedSpawn() {
        for (String name : Cfg.get(Opt.EXCLUDED_WORLDS)) {
            World w = Bukkit.getWorld(name);
            if (w != null) {
                return w.getSpawnLocation();
            }
        }

        return null;
    }
}
