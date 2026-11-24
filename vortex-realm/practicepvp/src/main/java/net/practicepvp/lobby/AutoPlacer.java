package net.practicepvp.lobby;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.npc.Npc;
import net.practicepvp.smp.ModeManager;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

/**
 * Places what a lobby build had no spot for, so nobody has to do it by hand: every PvP lobby NPC that is missing
 * (tournaments, cosmetics, bot practice, stats and the rest) gets a small booth built for it on free flat ground round
 * the spawn, facing the spawn, and a lobby without leaderboards or welcome holograms gets those too.
 *
 * <p>It runs after a PvP lobby is built, and once on start-up for a lobby that already stands. It only adds: an NPC
 * that already exists (under its id, or one anywhere near the spawn that does the same thing) is left where it is.
 */
public final class AutoPlacer {
    /** Closest and farthest a booth may stand from the spawn, and the space it keeps round other NPCs. */
    private static final int MIN_R = 9;
    private static final int MAX_R = 46;
    private static final double GAP = 7.0;
    /** How far above or below the spawn's floor a booth's floor may be. */
    private static final int DY = 3;

    private record Style(Material trim, Material inner, Material center, Material post, Material top) {
    }

    private AutoPlacer() {
    }

    /** Once per lobby (world and spot), on start-up: fills an existing lobby. */
    public static void startup(PracticePlugin plugin) {
        if (!Cfg.bool(Opt.LOBBY_AUTO_PLACE) || plugin.lobby() == null) {
            return;
        }

        Location spawn = plugin.lobby().lobby();
        if (spawn == null || spawn.getWorld() == null) {
            return;
        }

        File f = new File(plugin.getDataFolder(), "lobby-autoplace.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String key = (spawn.getWorld().getName() + "_" + spawn.getBlockX() + "_" + spawn.getBlockY() + "_" + spawn.getBlockZ()).replace('.', '_');
        if (y.getBoolean("done." + key)) {
            return;
        }

        String skin = "";
        for (Npc n : plugin.npcs().all()) {
            if (n.loc != null && n.loc.getWorld() == spawn.getWorld() && !n.skin.isEmpty()) {
                skin = n.skin;
                break;
            }
        }

        int added = run(plugin, spawn, skin);
        y = YamlConfiguration.loadConfiguration(f);
        y.set("done." + key, true);
        try {
            y.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save lobby-autoplace.yml: " + e.getMessage());
        }

        if (added > 0) {
            plugin.getLogger().info("Lobby: placed " + added + " NPC booths and holograms that the lobby had no spot for.");
        }
    }

    /** Places every missing PvP lobby NPC, leaderboard and welcome hologram round {@code spawn}; returns how many. */
    public static int run(PracticePlugin plugin, Location spawn, String skin) {
        if (!Cfg.bool(Opt.LOBBY_AUTO_PLACE) || spawn == null || spawn.getWorld() == null || plugin.npcs() == null) {
            return 0;
        }

        World w = spawn.getWorld();
        List<Location> taken = new ArrayList<>();
        for (Npc n : plugin.npcs().all()) {
            if (n.loc != null && n.loc.getWorld() == w) {
                taken.add(n.loc);
            }
        }

        if (plugin.holograms() != null) {
            taken.addAll(plugin.holograms().spots(w));
        }

        if (plugin.textHolograms() != null) {
            taken.addAll(plugin.textHolograms().spots(w));
        }

        File f = new File(plugin.getDataFolder(), "lobby-autoplace.yml");
        YamlConfiguration state = YamlConfiguration.loadConfiguration(f);
        List<String> booths = new ArrayList<>(state.getStringList("booths"));
        List<Location> free = candidates(spawn);
        int added = 0;
        for (ModeManager.NpcPlan p : ModeManager.pvpPlans(w.getName())) {
            String id = p.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
            Npc had = plugin.npcs().get(id);
            if (had != null && booths.contains(id) && had.loc != null && had.loc.getWorld() == w) {
                // a booth this placed: build it again if the lobby was rebuilt round it
                Block under = had.loc.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
                if (under.getType() != style(id).center() && floor(under)) {
                    booth(under.getLocation(), style(id));
                }

                continue;
            }

            // the parkour NPC belongs at the foot of a parkour, which only some lobbies have
            if (id.endsWith("_parkour") || had != null || hasSame(plugin, spawn, p)) {
                continue;
            }

            Location at = take(free, taken, spawn);
            if (at == null) {
                break;
            }

            booth(at, style(id));
            Location stand = at.clone().add(0.0, 1.0, 0.0);
            stand.setYaw(yawTo(stand, spawn));
            Npc n = plugin.npcs().create(id, stand, skin, p.lines(), p.action(), p.value());
            n.hand = p.hand();
            n.particle = "ring";
            plugin.npcs().update(n);
            booths.add(id);
            added++;
        }

        state.set("booths", booths);
        try {
            state.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save lobby-autoplace.yml: " + e.getMessage());
        }

        // leaderboards: the overall Elo podium and two framed boards, if the lobby has none
        if (plugin.holograms() != null && near(plugin.holograms().spots(w), spawn, 90.0) == 0) {
            String[][] boards = {{"PODIUM", "ELO"}, {"FRAME", "WINS"}, {"FRAME", "WINRATE"}};
            for (String[] b : boards) {
                Location at = take(free, taken, spawn);
                if (at == null) {
                    break;
                }

                Location l = at.clone().add(0.0, 1.0, 0.0);
                l.setYaw(yawTo(l, spawn));
                plugin.holograms().place(l, "overall", b[0], b[1], l.getYaw());
                added++;
            }
        }

        // welcome, tiers, tournaments and discord text holograms, if the lobby has none
        if (plugin.textHolograms() != null && near(plugin.textHolograms().spots(w), spawn, 90.0) == 0) {
            String[][] holos = {{"welcome", "FRAME"}, {"tiers", "HOLO"}, {"tournaments", "ROYAL"}, {"discord", "MARQUEE"}};
            for (String[] h : holos) {
                Location at = take(free, taken, spawn);
                if (at == null) {
                    break;
                }

                Location l = at.clone().add(0.0, 2.2, 0.0);
                l.setYaw(yawTo(l, spawn));
                plugin.textHolograms().placePreset(h[0], l, h[1]);
                added++;
            }
        }

        return added;
    }

    /** Is an NPC doing the same thing (same action and value) already standing near the spawn? */
    private static boolean hasSame(PracticePlugin plugin, Location spawn, ModeManager.NpcPlan p) {
        for (Npc n : plugin.npcs().all()) {
            if (n.loc != null && n.loc.getWorld() == spawn.getWorld() && n.loc.distanceSquared(spawn) < 120.0 * 120.0
                && n.action == p.action() && p.value() != null && p.value().equalsIgnoreCase(n.value)
                && p.action() != Npc.Action.MESSAGE) {
                return true;
            }
        }

        return false;
    }

    private static int near(List<Location> spots, Location spawn, double r) {
        int c = 0;
        for (Location l : spots) {
            if (l.getWorld() == spawn.getWorld() && l.distanceSquared(spawn) < r * r) {
                c++;
            }
        }

        return c;
    }

    /** The closest free spot that keeps its distance from everything placed; it is then taken. */
    private static Location take(List<Location> free, List<Location> taken, Location spawn) {
        // a spot the spawn can see first, else any
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < free.size(); i++) {
                Location c = free.get(i);
                if (!clear(c, taken) || pass == 0 && !visible(spawn, c)) {
                    continue;
                }

                free.remove(i);
                taken.add(c.clone().add(0.0, 1.0, 0.0));
                return c;
            }
        }

        return null;
    }

    private static boolean clear(Location c, List<Location> taken) {
        for (Location t : taken) {
            double dx = t.getX() - (c.getX());
            double dz = t.getZ() - (c.getZ());
            if (dx * dx + dz * dz < GAP * GAP && Math.abs(t.getY() - c.getY()) < 8.0) {
                return false;
            }
        }

        return true;
    }

    private static boolean visible(Location spawn, Location c) {
        Location eye = spawn.clone().add(0.0, 1.6, 0.0);
        Location head = c.clone().add(0.0, 2.6, 0.0);
        Vector d = head.toVector().subtract(eye.toVector());
        double len = d.length();
        if (len < 0.5) {
            return true;
        }

        return spawn.getWorld().rayTraceBlocks(eye, d.normalize(), len - 0.5, FluidCollisionMode.NEVER, true) == null;
    }

    /** Booth centres (the ground block, at the middle of the block), nearest to the spawn first. */
    private static List<Location> candidates(Location spawn) {
        World w = spawn.getWorld();
        int sx = spawn.getBlockX();
        int sz = spawn.getBlockZ();
        int sy = spawn.getBlockY() - 1;
        List<Location> out = new ArrayList<>();
        int ring = 0;
        for (int r = MIN_R; r <= MAX_R; r += 3) {
            int steps = Math.max(12, (int)Math.round(2.0 * Math.PI * r / 6.0));
            double off = ring++ % 2 == 0 ? 0.0 : Math.PI / steps;
            for (int i = 0; i < steps; i++) {
                double a = off + 2.0 * Math.PI * i / steps;
                int x = sx + (int)Math.round(Math.cos(a) * r);
                int z = sz + (int)Math.round(Math.sin(a) * r);
                int gy = ground(w, x, sy, z);
                if (gy != Integer.MIN_VALUE && flat(w, x, gy, z)) {
                    out.add(new Location(w, x + 0.5, gy, z + 0.5));
                }
            }
        }

        return out;
    }

    /** The floor block under (x, z) within a few blocks of the spawn's floor, or MIN_VALUE. */
    private static int ground(World w, int x, int sy, int z) {
        for (int y = sy + DY; y >= sy - DY; y--) {
            if (floor(w.getBlockAt(x, y, z)) && open(w.getBlockAt(x, y + 1, z)) && open(w.getBlockAt(x, y + 2, z))) {
                return y;
            }
        }

        return Integer.MIN_VALUE;
    }

    /** A 5 by 5 floor at one height with 4 blocks of room over all of it. */
    private static boolean flat(World w, int x, int gy, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!floor(w.getBlockAt(x + dx, gy, z + dz))) {
                    return false;
                }

                for (int dy = 1; dy <= 4; dy++) {
                    if (!open(w.getBlockAt(x + dx, gy + dy, z + dz))) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static boolean floor(Block b) {
        Material m = b.getType();
        return m.isSolid() && m.isOccluding() && !Tag.LEAVES.isTagged(m) && m != Material.BARRIER && m != Material.BEDROCK
            && !m.name().contains("GLASS") && !m.name().contains("ICE") && !m.name().endsWith("_ORE") && m != Material.GOLD_BLOCK
            && m.isBlock();
    }

    private static boolean open(Block b) {
        Material m = b.getType();
        return m.isAir() || Tag.REPLACEABLE.isTagged(m) && !b.isLiquid();
    }

    private static float yawTo(Location from, Location to) {
        return (float)Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
    }

    private static Style style(String id) {
        if (id.endsWith("_cosmetics")) {
            return new Style(Material.AMETHYST_BLOCK, Material.SMOOTH_QUARTZ, Material.PURPUR_BLOCK, Material.PURPUR_PILLAR, Material.AMETHYST_CLUSTER);
        } else if (id.endsWith("_events")) {
            return new Style(Material.POLISHED_BLACKSTONE_BRICKS, Material.SMOOTH_STONE, Material.GOLD_BLOCK, Material.POLISHED_BLACKSTONE_WALL, Material.LANTERN);
        } else if (id.endsWith("_bots")) {
            return new Style(Material.MOSSY_STONE_BRICKS, Material.STONE_BRICKS, Material.EMERALD_BLOCK, Material.MOSSY_STONE_BRICK_WALL, Material.LANTERN);
        } else if (id.endsWith("_stats")) {
            return new Style(Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE, Material.SEA_LANTERN, Material.PRISMARINE_WALL, Material.SEA_LANTERN);
        } else if (id.endsWith("_academy")) {
            return new Style(Material.DARK_OAK_PLANKS, Material.POLISHED_DEEPSLATE, Material.LAPIS_BLOCK, Material.DARK_OAK_FENCE, Material.LANTERN);
        } else if (id.endsWith("_welcome")) {
            return new Style(Material.QUARTZ_BRICKS, Material.SMOOTH_QUARTZ, Material.CHISELED_QUARTZ_BLOCK, Material.END_STONE_BRICK_WALL, Material.LANTERN);
        }

        return new Style(Material.POLISHED_ANDESITE, Material.SMOOTH_STONE, Material.CHISELED_STONE_BRICKS, Material.ANDESITE_WALL, Material.LANTERN);
    }

    /** A 5 by 5 inlaid floor with a post and a light at each corner; the NPC stands in the middle. */
    private static void booth(Location c, Style s) {
        World w = c.getWorld();
        int x = c.getBlockX();
        int y = c.getBlockY();
        int z = c.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                Material m = dx == 0 && dz == 0 ? s.center() : edge ? s.trim() : s.inner();
                w.getBlockAt(x + dx, y, z + dz).setType(m, false);
                Block above = w.getBlockAt(x + dx, y + 1, z + dz);
                if (!above.getType().isAir() && Tag.REPLACEABLE.isTagged(above.getType())) {
                    above.setType(Material.AIR, false);
                }
            }
        }

        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dz = -2; dz <= 2; dz += 4) {
                w.getBlockAt(x + dx, y + 1, z + dz).setType(s.post(), true);
                w.getBlockAt(x + dx, y + 2, z + dz).setType(s.post(), true);
                w.getBlockAt(x + dx, y + 3, z + dz).setType(s.top(), true);
            }
        }
    }
}
