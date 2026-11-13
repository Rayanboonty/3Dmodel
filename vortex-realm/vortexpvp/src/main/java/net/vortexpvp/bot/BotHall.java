package net.vortexpvp.bot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.arena.Arena;
import net.vortexpvp.arena.ArenaTheme;
import net.vortexpvp.build.BotHallDesign;
import net.vortexpvp.build.SpawnBuilder;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The Bot Practice hall: a room of map alcoves, each with the biome's mob on a pedestal and the map's name over it.
 * Click a mob to fight a bot on that map (kit, then difficulty); the NPCs in the middle open the gamemode search, the
 * training dummies and the Academy. Built with /pvpadmin bothall (or Builds -> Bot Practice Hall).
 */
public final class BotHall implements Listener {
    private final VortexPvP plugin;
    private final NamespacedKey mapKey;
    private final NamespacedKey npcKey;
    private final File file;

    public BotHall(VortexPvP plugin) {
        this.plugin = plugin;
        this.mapKey = new NamespacedKey(plugin, "botmap");
        this.npcKey = new NamespacedKey(plugin, "botnpc");
        this.file = new File(plugin.getDataFolder(), "bot-hall.yml");
    }

    private static EntityType mobOf(String theme) {
        return switch (theme) {
            case "BADLANDS" -> EntityType.ARMADILLO;
            case "BADLANDS_DESERT" -> EntityType.CAMEL;
            case "DESERT" -> EntityType.HUSK;
            case "PLAINS" -> EntityType.FROG;
            case "SNOWY" -> EntityType.POLAR_BEAR;
            case "CHERRY" -> EntityType.RABBIT;
            case "MUSHROOM" -> EntityType.MOOSHROOM;
            case "CRIMSON" -> EntityType.STRIDER;
            case "END" -> EntityType.ENDERMAN;
            default -> EntityType.SHEEP;
        };
    }

    private static String colourOf(String theme) {
        return switch (theme) {
            case "BADLANDS" -> "<#FF7A4D>";
            case "BADLANDS_DESERT" -> "<#E0573A>";
            case "DESERT" -> "<#F5D58A>";
            case "PLAINS" -> "<#7CFC7C>";
            case "SNOWY" -> "<#E8F6FF>";
            case "CHERRY" -> "<#FFB7E1>";
            case "MUSHROOM" -> "<#C77DFF>";
            case "CRIMSON" -> "<#FF4D4D>";
            case "END" -> "<#E8E3A6>";
            default -> "<white>";
        };
    }

    /** Builds the hall where the admin stands and places its mobs, names and NPCs. */
    public void build(Player admin) {
        Location at = admin.getLocation();
        BotHallDesign d = new BotHallDesign(at.getBlockX(), at.getBlockY() - 1, at.getBlockZ(), at.getBlockX() * 31L + at.getBlockZ());
        World w = at.getWorld();
        this.remove();
        admin.sendMessage(Text.mm("<yellow>Building the Bot Practice hall..."));
        SpawnBuilder.build(this.plugin, w, d).whenComplete((v, err) -> Tasks.ensureSync(() -> {
            if (err != null) {
                admin.sendMessage(Text.mm("<red>Building failed - see the console."));
                return;
            }

            List<String> ids = new ArrayList<>();

            for (Map.Entry<String, double[]> f : d.features().entrySet()) {
                double[] p = f.getValue();
                Location l = new Location(w, p[0], p[1], p[2], (float)p[3], 0.0F);
                String key = f.getKey();
                if (key.startsWith("botmap:")) {
                    this.placeMap(l, key.substring(7), ids);
                } else if (key.startsWith("botnpc:")) {
                    this.placeNpc(l, key.substring(7), ids);
                }
            }

            YamlConfiguration y = new YamlConfiguration();
            y.set("entities", ids);
            y.set("world", w.getName());

            try {
                y.save(this.file);
            } catch (IOException var9) {
                this.plugin.getLogger().warning("Could not save bot-hall.yml");
            }

            admin.sendMessage(Text.mm("<green>Bot Practice hall built. <gray>Click a mob to fight on its map."));
        }));
    }

    private void placeMap(Location l, String theme, List<String> ids) {
        World w = l.getWorld();
        Entity mob = w.spawnEntity(l, mobOf(theme));
        if (mob instanceof LivingEntity le) {
            le.setAI(false);
            le.setSilent(true);
            le.setInvulnerable(true);
            le.setRemoveWhenFarAway(false);
            le.setCollidable(false);
        }

        mob.setPersistent(true);
        mob.getPersistentDataContainer().set(this.mapKey, PersistentDataType.STRING, theme);
        ids.add(mob.getUniqueId().toString());
        ArenaTheme t = ArenaTheme.parse(theme);
        double h = mob.getHeight();
        TextDisplay name = w.spawn(l.clone().add(0.0, h + 0.55, 0.0), TextDisplay.class, td -> {
            td.setBillboard(Billboard.CENTER);
            td.setPersistent(true);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.setShadowed(true);
            td.text(Text.mm(colourOf(theme) + "<b>" + Text.esc(t.display) + "</b>\n<yellow>Bots coming soon"));
        });
        name.getPersistentDataContainer().set(this.mapKey, PersistentDataType.STRING, theme);
        ids.add(name.getUniqueId().toString());
    }

    private void placeNpc(Location l, String what, List<String> ids) {
        World w = l.getWorld();
        String title = switch (what) {
            case "search" -> "<yellow><b>🔍 Find a Gamemode";
            case "train" -> "<aqua><b>🎯 Training Dummies";
            default -> "<gold><b>📖 Academy";
        };
        Material hand = switch (what) {
            case "search" -> Material.COMPASS;
            case "train" -> Material.SHIELD;
            default -> Material.BOOK;
        };
        Mannequin m = w.spawn(l, Mannequin.class, mm -> {
            mm.setPersistent(true);
            mm.setInvulnerable(true);
            mm.setImmovable(true);
            mm.setGravity(false);
            mm.getEquipment().setItemInMainHand(new ItemStack(hand));
            mm.setDescription(Text.mm("<gray>Click"));
        });
        m.getPersistentDataContainer().set(this.npcKey, PersistentDataType.STRING, what);
        ids.add(m.getUniqueId().toString());
        TextDisplay name = w.spawn(l.clone().add(0.0, 2.35, 0.0), TextDisplay.class, td -> {
            td.setBillboard(Billboard.CENTER);
            td.setPersistent(true);
            td.text(Text.mm(title + "\n<gray>▶ Click"));
        });
        ids.add(name.getUniqueId().toString());
    }

    /** Removes the hall's mobs, names and NPCs (the blocks stay). */
    public int remove() {
        if (!this.file.exists()) {
            return 0;
        }

        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        int n = 0;

        for (String s : y.getStringList("entities")) {
            try {
                Entity e = Bukkit.getEntity(UUID.fromString(s));
                if (e != null) {
                    e.remove();
                    n++;
                }
            } catch (IllegalArgumentException var6) {
            }
        }

        this.file.delete();
        return n;
    }

    // ------------------------------------------------------------------ clicks

    private boolean open(Player p, Entity e) {
        String theme = e.getPersistentDataContainer().get(this.mapKey, PersistentDataType.STRING);
        String npc = e.getPersistentDataContainer().get(this.npcKey, PersistentDataType.STRING);
        if (theme == null && npc == null) {
            return false;
        }

        BotManager bots = this.plugin.bots();
        if (bots == null) {
            return true;
        }

        Snd.play(p, "ui.button.click", 0.6F, 1.2F);
        if (BotManager.COMING_SOON && !"learn".equals(npc)) {
            BotManager.comingSoon(p);
            return true;
        }

        if (theme != null) {
            Arena best = null;

            for (Arena a : this.plugin.arenas().all()) {
                if (a.theme != null && a.theme.name().equals(theme) && a.ready() && !a.deep && (best == null || a.freeInstances() > best.freeInstances())) {
                    best = a;
                }
            }

            if (best == null) {
                p.sendMessage(Text.mm("<red>The " + Text.esc(ArenaTheme.parse(theme).display) + " map isn't ready yet."));
            } else {
                bots.kitMenu(p, null, best);
            }
        } else {
            switch (npc) {
                case "search" -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Find a gamemode"), "Gamemode", "", 24, s -> bots.kitMenu(p, s, null));
                case "train" -> bots.dummyMenu(p);
                default -> {
                    if (this.plugin.academy() != null) {
                        this.plugin.academy().courses(p, null);
                    }
                }
            }
        }

        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent e) {
        if (e.getHand() == EquipmentSlot.HAND && this.open(e.getPlayer(), e.getRightClicked())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && this.open(p, e.getEntity())) {
            e.setCancelled(true);
        }
    }

    public static String help() {
        return "/pvpadmin bothall [remove]".toLowerCase(Locale.ROOT);
    }
}
