package net.cmcpvp.cosmetics;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import net.cmcpvp.CMCPvP;
import net.cmcpvp.cosmetics.CosmeticModels.Kind;
import net.cmcpvp.cosmetics.CosmeticModels.Model;
import net.cmcpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Cosmetic showcases for the lobby / spawn: a pet doing its tricks, a shoulder pet, or wings on a stand, each with a
 * name and "unlocks at N hours played" above it. Placed with /pvpadmin showcase add &lt;cosmetic&gt; where you stand,
 * saved in showcases.yml, made again on every start (and when their chunk loads).
 */
public final class Showcases implements Listener {
    private final CMCPvP plugin;
    private final File file;
    private final List<Spot> spots = new ArrayList<>();
    private BukkitTask task;
    private long tick;

    private static final class Spot {
        String id;
        Location at;
        CosmeticRig rig;
        TextDisplay label;
        ArmorStand stand;
    }

    public Showcases(CMCPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "showcases.yml");
    }

    public void start() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection sec = y.getConfigurationSection("showcases");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(k);
                World w = s == null ? null : Bukkit.getWorld(s.getString("world", ""));
                if (w != null && CosmeticModels.get(s.getString("id")) != null) {
                    Spot sp = new Spot();
                    sp.id = s.getString("id");
                    sp.at = new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float)s.getDouble("yaw"), 0);
                    this.spots.add(sp);
                }
            }
        }

        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::update, 40L, 3L);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
        }

        for (Spot s : this.spots) {
            despawn(s);
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (Spot s : this.spots) {
            String b = "showcases.s" + i++ + ".";
            y.set(b + "id", s.id);
            y.set(b + "world", s.at.getWorld().getName());
            y.set(b + "x", s.at.getX());
            y.set(b + "y", s.at.getY());
            y.set(b + "z", s.at.getZ());
            y.set(b + "yaw", s.at.getYaw());
        }

        try {
            y.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save showcases.yml", e);
        }
    }

    /** Adds a showcase of a cosmetic where the player stands, facing them. */
    public String add(Location at, String id) {
        Model m = CosmeticModels.get(id);
        if (m == null) {
            return "No cosmetic called " + id + ". Pick one of: " + String.join(", ", CosmeticModels.all().keySet());
        }

        Spot s = new Spot();
        s.id = m.id();
        s.at = at.getBlock().getLocation().add(0.5, 0, 0.5);
        s.at.setYaw(Math.round((at.getYaw() + 180f) / 45f) * 45f);
        this.spots.add(s);
        this.save();
        return null;
    }

    /** Removes the showcase nearest to the spot (within 5 blocks). */
    public boolean removeNear(Location at) {
        Spot best = null;
        double bd = 25;
        for (Spot s : this.spots) {
            if (s.at.getWorld() == at.getWorld() && s.at.distanceSquared(at) < bd) {
                bd = s.at.distanceSquared(at);
                best = s;
            }
        }

        if (best == null) {
            return false;
        }

        despawn(best);
        this.spots.remove(best);
        this.save();
        return true;
    }

    public List<String> list() {
        List<String> out = new ArrayList<>();
        for (Spot s : this.spots) {
            out.add(s.id + " @ " + s.at.getWorld().getName() + " " + s.at.getBlockX() + " " + s.at.getBlockY() + " " + s.at.getBlockZ());
        }

        return out;
    }

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        // made again on the next update when their chunk comes back
        for (Spot s : this.spots) {
            if (s.rig != null && !s.rig.valid()) {
                despawn(s);
            }
        }
    }

    private static void despawn(Spot s) {
        if (s.rig != null) {
            s.rig.remove();
            s.rig = null;
        }

        if (s.label != null) {
            s.label.remove();
            s.label = null;
        }

        if (s.stand != null) {
            s.stand.remove();
            s.stand = null;
        }
    }

    private void update() {
        this.tick += 3;
        for (Spot s : this.spots) {
            World w = s.at.getWorld();
            if (w == null || !w.isChunkLoaded(s.at.getBlockX() >> 4, s.at.getBlockZ() >> 4)) {
                if (s.rig != null) {
                    despawn(s);
                }

                continue;
            }

            Model m = CosmeticModels.get(s.id);
            if (m == null) {
                continue;
            }

            if (s.rig == null || !s.rig.valid()) {
                despawn(s);
                s.rig = Companions.showcase(s.at, m, s.at.getYaw(), this.tick);
                if (m.kind() == Kind.WINGS) {
                    s.stand = w.spawn(s.at, ArmorStand.class, a -> {
                        a.setPersistent(false);
                        a.setInvulnerable(true);
                        a.setGravity(false);
                        a.setBasePlate(false);
                        a.setArms(true);
                        a.setRotation(s.at.getYaw(), 0);
                        a.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));
                        a.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                        a.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                        a.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));
                        a.addEquipmentLock(org.bukkit.inventory.EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);
                        a.addEquipmentLock(org.bukkit.inventory.EquipmentSlot.CHEST, ArmorStand.LockType.REMOVING_OR_CHANGING);
                        a.addEquipmentLock(org.bukkit.inventory.EquipmentSlot.LEGS, ArmorStand.LockType.REMOVING_OR_CHANGING);
                        a.addEquipmentLock(org.bukkit.inventory.EquipmentSlot.FEET, ArmorStand.LockType.REMOVING_OR_CHANGING);
                    });
                }

                double h = m.kind() == Kind.WINGS ? 2.6 : m.height() / 16.0 * m.scale() * (m.kind() == Kind.SHOULDER ? 1.8 : 1.0) + 0.5;
                s.label = w.spawn(s.at.clone().add(0, h, 0), TextDisplay.class, t -> {
                    t.setPersistent(false);
                    t.setBillboard(Display.Billboard.CENTER);
                    t.text(Text.mm("<gold><b>" + m.name() + "</b>\n<gray>" + (m.kind() == Kind.PET ? "Pet" : m.kind() == Kind.WINGS ? "Wings" : "Shoulder Pet")
                        + " <dark_gray>• <gray>unlocks at <white>" + (m.hours() < 1 ? Math.round(m.hours() * 60) + "m" : Math.round(m.hours()) + "h") + "</white> played\n<yellow>/cosmetics"));
                    t.setShadowed(true);
                });
            } else {
                s.rig.pose(Companions.showcasePose(m, s.at.getYaw(), this.tick));
            }
        }
    }

    /** True for an entity that belongs to a showcase (protects the armor stands). */
    public boolean owns(Entity e) {
        for (Spot s : this.spots) {
            if (s.stand == e) {
                return true;
            }
        }

        return false;
    }
}
