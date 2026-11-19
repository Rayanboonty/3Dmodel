package net.practicepvp.lobby;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.practicepvp.PracticePlugin;
import net.practicepvp.build.ClubSpawnDesign;
import net.practicepvp.build.LegacyHallDesign;
import net.practicepvp.build.SpawnBuilder;
import net.practicepvp.build.Voxels;
import net.practicepvp.kit.Kit;
import net.practicepvp.npc.Npc;
import net.practicepvp.session.Tp;
import net.practicepvp.util.Items;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * 3.13.0 - the 1.8 Legacy Hall: places its NPCs (one per 1.8 kit, the arcade modes on the stage) and builds it.
 * /legacyhall teleports to it; /legacyhall build (admin) stamps it into the Club Spawn at the end of the path, or in
 * front of the admin when the lobby is another build.
 */
public final class LegacyHall implements CommandExecutor, org.bukkit.command.TabCompleter {
    private final PracticePlugin plugin;
    private final File file;

    public LegacyHall(PracticePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "legacy-hall.yml");
    }

    /** Places the hall's NPCs from a build's feature spots (keys legacy:...). */
    public void place(Map<String, Location> features) {
        if (this.plugin.npcs() == null) {
            return;
        }

        Location spawn = null;
        for (Map.Entry<String, Location> f : features.entrySet()) {
            String key = f.getKey();
            if (!key.startsWith("legacy:")) {
                continue;
            }

            Location l = f.getValue();
            String id = key.substring(7);
            if (id.equals("spawn")) {
                spawn = l;
            } else if (id.equals("title")) {
                continue;
            } else if (id.startsWith("arcade:")) {
                String mode = id.substring(7);
                Npc n = this.plugin.npcs().create("legacy_arcade_" + mode, l, "",
                    List.of("<b><gradient:#FFE29A:#FFB547:{phase}>" + mode.toUpperCase(Locale.ROOT) + "</gradient></b>", "<gray>1.8 arcade", "{blink}▶ Click to queue"),
                    Npc.Action.COMMAND, "arcade join " + mode);
                n.hand = switch (mode) {
                    case "bridge" -> Material.BLUE_TERRACOTTA;
                    case "sumo" -> Material.SLIME_BALL;
                    case "boxing" -> Material.LEATHER_CHESTPLATE;
                    default -> Material.DIAMOND_SHOVEL;
                };
                this.plugin.npcs().update(n);
            } else {
                Kit k = this.plugin.kits().get(id);
                String name = k == null ? id : k.displayName;
                String color = k == null ? "<gold>" : k.color;
                Npc n = this.plugin.npcs().create("legacy_" + id, l, "",
                    List.of("<b>" + color + Text.esc(name.toUpperCase(Locale.ROOT)) + "</b>", "<gray>1.8 combat", "{blink}▶ Click to play"),
                    Npc.Action.COMMAND, "legacyhall play " + id);
                if (k != null) {
                    n.hand = Items.isEmpty(k.contents[0]) ? Material.DIAMOND_SWORD : k.contents[0].getType();
                    n.helmet = Items.isEmpty(k.contents[Kit.HELMET]) ? null : k.contents[Kit.HELMET].clone();
                    n.chest = Items.isEmpty(k.contents[Kit.CHESTPLATE]) ? null : k.contents[Kit.CHESTPLATE].clone();
                    n.legs = Items.isEmpty(k.contents[Kit.LEGGINGS]) ? null : k.contents[Kit.LEGGINGS].clone();
                    n.boots = Items.isEmpty(k.contents[Kit.BOOTS]) ? null : k.contents[Kit.BOOTS].clone();
                }

                this.plugin.npcs().update(n);
            }
        }

        if (spawn != null) {
            YamlConfiguration y = new YamlConfiguration();
            y.set("spawn", spawn);
            try {
                y.save(this.file);
            } catch (java.io.IOException e) {
                this.plugin.getLogger().warning("Could not save legacy-hall.yml");
            }
        }
    }

    /** Builds the hall: into the Club Spawn if that is the lobby build of this world, else in front of the admin. */
    public void build(Player admin) {
        YamlConfiguration hub = YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), "hub-build.yml"));
        World w = admin.getWorld();
        Voxels d;
        if ("CLUB_SPAWN".equals(hub.getString("style")) && w.getName().equals(hub.getString("world"))) {
            int x = hub.getInt("x");
            int y = hub.getInt("y");
            int z = hub.getInt("z");
            ClubSpawnDesign club = new ClubSpawnDesign(x, y - 1, z, x * 31L + z);
            int hx = x + ClubSpawnDesign.HALL_X;
            int hz = z + ClubSpawnDesign.HALL_Z;
            d = new net.practicepvp.build.VoxelsRegion(club, hx - 43, hz - 47, hx + 43, Math.min(club.maxZ(), hz + 40), "1.8 Legacy Hall");
            admin.sendMessage(Text.mm("<yellow>Building the 1.8 Legacy Hall into the Club Spawn (end of the south-west path)..."));
        } else {
            Location at = admin.getLocation();
            d = new LegacyHallDesign(at.getBlockX(), at.getBlockY() + 3, at.getBlockZ() + 29, at.getBlockX() * 31L + at.getBlockZ());
            admin.sendMessage(Text.mm("<yellow>Building the 1.8 Legacy Hall south of you (you stand at the foot of its steps)..."));
        }

        Voxels design = d;
        SpawnBuilder.build(this.plugin, w, design).whenComplete((v, err) -> Tasks.ensureSync(() -> {
            if (err != null) {
                this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "Building the Legacy Hall failed", err);
                admin.sendMessage(Text.mm("<red>Building failed - see the console."));
                return;
            }

            Map<String, Location> features = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, double[]> f : design.features().entrySet()) {
                double[] p = f.getValue();
                features.put(f.getKey(), new Location(w, p[0], p[1], p[2], (float)p[3], 0.0F));
            }

            this.place(features);
            admin.sendMessage(Text.mm("<green>1.8 Legacy Hall built. <gray>Players get there with <white>/legacyhall</white>."));
        }));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("play");
            if (sender.hasPermission("practice.admin")) {
                out.add("build");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("play")) {
            for (Kit k : this.plugin.kits().all()) {
                if (k.legacy) {
                    out.add(k.id);
                }
            }
        }

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(last));
        return out;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("build")) {
            if (!(sender instanceof Player p) || !sender.hasPermission("practice.admin")) {
                sender.sendMessage(Text.mm("<red>Admins only, in game."));
            } else {
                this.build(p);
            }

            return true;
        }

        if (!(sender instanceof Player p)) {
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("play")) {
            Kit k = this.plugin.kits().get(args[1].toLowerCase(Locale.ROOT));
            if (k == null || !k.enabled) {
                p.sendMessage(Text.mm("<red>That kit is not available."));
                return true;
            }

            List<net.practicepvp.dialog.LobbyDialogs.Tile> tiles = new ArrayList<>();
            if (k.unranked) {
                tiles.add(new net.practicepvp.dialog.LobbyDialogs.Tile(new org.bukkit.inventory.ItemStack(Material.IRON_SWORD), Text.mm("<aqua><b>Unranked"), Text.mm("<gray>Casual 1v1"), List.of(),
                    () -> p.performCommand("queue unranked " + k.id)));
            }

            if (k.ranked) {
                tiles.add(new net.practicepvp.dialog.LobbyDialogs.Tile(new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD), Text.mm("<gold><b>Ranked"), Text.mm("<gray>Elo on the line"), List.of(),
                    () -> p.performCommand("queue ranked " + k.id)));
            }

            if (this.plugin.bots() != null) {
                tiles.add(new net.practicepvp.dialog.LobbyDialogs.Tile(new org.bukkit.inventory.ItemStack(Material.ARMOR_STAND), Text.mm("<green><b>Practice Bot"), Text.mm("<gray>Fight a bot"), List.of(),
                    () -> this.plugin.bots().difficultyMenu(p, k, null)));
            }

            tiles.add(new net.practicepvp.dialog.LobbyDialogs.Tile(new org.bukkit.inventory.ItemStack(Material.WRITABLE_BOOK), Text.mm("<light_purple><b>Edit Layout"), Text.mm("<gray>Arrange the kit"), List.of(),
                () -> p.performCommand("kiteditor " + k.id)));
            this.plugin.dialogs().lobby().screen(p, net.practicepvp.dialog.LobbyDialogs.title(k.displayName, "1.8 combat"), tiles,
                List.of(Text.mm("<gray>No hit cooldown, 1.8 knockback, sword blocking (hold right click).")), null, List.of(), 2,
                this.plugin.dialogs().lobby().close(p));
            return true;
        }

        Location spawn = YamlConfiguration.loadConfiguration(this.file).getLocation("spawn");
        if (spawn == null || spawn.getWorld() == null) {
            p.sendMessage(Text.mm("<red>The 1.8 Legacy Hall is not built yet."));
        } else {
            Tp.to(p, spawn);
        }

        return true;
    }
}
