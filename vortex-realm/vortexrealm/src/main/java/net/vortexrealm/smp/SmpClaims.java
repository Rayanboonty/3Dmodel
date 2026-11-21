package net.vortexrealm.smp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 3.13.16 - land claims are back on the SMP. /claim protects a square base around you (radius set in
 * smp-claims.yml, 40 by default = 81 x 81 blocks, room for farms). Inside a claim only the owner and trusted
 * players can break, place, open chests, use doors, buckets, frames and animals. Nothing blows up there: creepers,
 * TNT, crystals, anchors, withers and fire never break claimed blocks. PvP still works everywhere.
 */
public final class SmpClaims implements CommandExecutor, TabCompleter, Listener {
    static final class Claim {
        final UUID owner;
        String name;
        final String world;
        final int x;
        final int z;
        final int r;
        final Set<UUID> trusted = new HashSet<>();

        Claim(UUID owner, String name, String world, int x, int z, int r) {
            this.owner = owner;
            this.name = name;
            this.world = world;
            this.x = x;
            this.z = z;
            this.r = r;
        }

        boolean has(String w, int bx, int bz) {
            return this.world.equals(w) && Math.abs(bx - this.x) <= this.r && Math.abs(bz - this.z) <= this.r;
        }

        boolean overlaps(String w, int ox, int oz, int or, int gap) {
            return this.world.equals(w) && Math.abs(ox - this.x) <= this.r + or + gap && Math.abs(oz - this.z) <= this.r + or + gap;
        }

        boolean allows(UUID u) {
            return this.owner.equals(u) || this.trusted.contains(u);
        }
    }

    private final VortexRealm plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Claim> claims = new HashMap<>();
    private final Set<UUID> bypass = new HashSet<>();
    private final Map<UUID, UUID> lastArea = new HashMap<>();
    private int radius;
    private int gap;
    private int spawnGap;

    public SmpClaims(VortexRealm plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "smp-claims.yml");
        this.data = YamlConfiguration.loadConfiguration(this.file);
        this.data.addDefault("settings.radius", 40);
        this.data.addDefault("settings.gap-between-claims", 10);
        this.data.addDefault("settings.no-claims-near-spawn", 150);
        this.data.options().copyDefaults(true);
        this.radius = Math.max(5, this.data.getInt("settings.radius"));
        this.gap = Math.max(0, this.data.getInt("settings.gap-between-claims"));
        this.spawnGap = Math.max(0, this.data.getInt("settings.no-claims-near-spawn"));
        ConfigurationSection cs = this.data.getConfigurationSection("claims");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                try {
                    ConfigurationSection c = cs.getConfigurationSection(k);
                    Claim cl = new Claim(UUID.fromString(k), c.getString("name", "?"), c.getString("world"), c.getInt("x"), c.getInt("z"), c.getInt("r", this.radius));
                    for (String t : c.getStringList("trusted")) {
                        cl.trusted.add(UUID.fromString(t));
                    }

                    this.claims.put(cl.owner, cl);
                } catch (RuntimeException bad) {
                    plugin.getLogger().warning("Skipped a broken claim: " + k);
                }
            }
        }

        this.save();
    }

    private void save() {
        this.data.set("claims", null);
        for (Claim c : this.claims.values()) {
            String k = "claims." + c.owner;
            this.data.set(k + ".name", c.name);
            this.data.set(k + ".world", c.world);
            this.data.set(k + ".x", c.x);
            this.data.set(k + ".z", c.z);
            this.data.set(k + ".r", c.r);
            List<String> t = new ArrayList<>();
            for (UUID u : c.trusted) {
                t.add(u.toString());
            }

            this.data.set(k + ".trusted", t);
        }

        try {
            this.data.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().warning("Could not save smp-claims.yml: " + e.getMessage());
        }
    }

    private boolean smp(World w) {
        ModeManager m = this.plugin.modes();
        return w != null && m != null && m.smpWorld(w);
    }

    /** The claim at this spot, or null (also null outside the SMP). */
    Claim at(Location l) {
        if (l == null || !this.smp(l.getWorld())) {
            return null;
        }

        String w = l.getWorld().getName();
        int bx = l.getBlockX();
        int bz = l.getBlockZ();
        for (Claim c : this.claims.values()) {
            if (c.has(w, bx, bz)) {
                return c;
            }
        }

        return null;
    }

    private Claim at(Block b) {
        return this.at(b.getLocation());
    }

    /** May this player change things here? */
    private boolean can(Player p, Location l) {
        Claim c = this.at(l);
        return c == null || c.allows(p.getUniqueId()) || this.bypass.contains(p.getUniqueId());
    }

    private void deny(Player p, Claim c) {
        p.sendActionBar(Text.mm("<red>🔒 This is <white>" + Text.esc(c.name) + "</white>'s base. <gray>You can't do that here."));
    }

    private boolean check(Player p, Location l) {
        if (this.can(p, l)) {
            return true;
        }

        this.deny(p, this.at(l));
        return false;
    }

    private static Player playerOf(Entity e) {
        if (e instanceof Player p) {
            return p;
        }

        if (e instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            return p;
        }

        return null;
    }

    // ---------------------------------------------------------------- players

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!this.check(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!this.check(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (!this.check(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent e) {
        if (!this.check(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    /** Chests, furnaces, shulkers, doors, buttons, beds, crops (trampling)... */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.PHYSICAL) {
            return;
        }

        if (!this.can(e.getPlayer(), b.getLocation())) {
            if (e.getAction() == Action.PHYSICAL) {
                e.setCancelled(true);
                return;
            }

            // the block does nothing, but eating, pearls and shields still work
            e.setUseInteractedBlock(Event.Result.DENY);
            if (b.getType().isInteractable()) {
                this.deny(e.getPlayer(), this.at(b));
            }
        }
    }

    /** Item frames, armor stands, animals, villagers, minecarts with chests... */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityUse(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Player) && !this.check(e.getPlayer(), e.getRightClicked().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStand(PlayerArmorStandManipulateEvent e) {
        if (!this.check(e.getPlayer(), e.getRightClicked().getLocation())) {
            e.setCancelled(true);
        }
    }

    /** Strangers can't kill the base's animals or break its frames and stands. PvP is not touched. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity v = e.getEntity();
        if (v instanceof Player || v instanceof Monster) {
            return;
        }

        Player p = playerOf(e.getDamager());
        if (p != null && !this.check(p, v.getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Player p = playerOf(e.getRemover());
        if (p != null ? !this.check(p, e.getEntity().getLocation()) : this.at(e.getEntity().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- explosions, fire, mobs

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> this.at(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> this.at(b) != null);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHanging(HangingBreakEvent e) {
        if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION && this.at(e.getEntity().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    /** Armor stands, frames, minecarts and animals don't die from explosions in a base either. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlast(EntityDamageEvent e) {
        EntityDamageEvent.DamageCause c = e.getCause();
        if ((c == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || c == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)
            && !(e.getEntity() instanceof Player) && !(e.getEntity() instanceof Monster) && this.at(e.getEntity().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    /** Endermen, withers, ravagers and other mobs can't take or break blocks in a base. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMobBlock(EntityChangeBlockEvent e) {
        Entity en = e.getEntity();
        if (en instanceof Player p) {
            if (!this.check(p, e.getBlock().getLocation())) {
                e.setCancelled(true);
            }
        } else if (this.at(e.getBlock()) != null && !(en instanceof org.bukkit.entity.FallingBlock)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (this.at(e.getBlock()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        Claim c = this.at(e.getBlock());
        if (c == null) {
            return;
        }

        Player p = e.getPlayer();
        if (p == null ? e.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL : !this.can(p, e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (e.getSource().getType() == org.bukkit.Material.FIRE && this.at(e.getBlock()) != null) {
            e.setCancelled(true);
        }
    }

    /** Lava and water poured outside can't flow into a base. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Claim to = this.at(e.getToBlock());
        if (to != null && to != this.at(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        if (this.crosses(e.getBlock(), e.getBlocks(), e.getDirection())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent e) {
        if (this.crosses(e.getBlock(), e.getBlocks(), e.getDirection())) {
            e.setCancelled(true);
        }
    }

    private boolean crosses(Block piston, List<Block> moved, BlockFace dir) {
        Claim home = this.at(piston);
        for (Block b : moved) {
            if (this.at(b) != home || this.at(b.getRelative(dir)) != home) {
                return true;
            }
        }

        return false;
    }

    // ---------------------------------------------------------------- enter / leave message

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location t = e.getTo();
        if (t.getBlockX() == e.getFrom().getBlockX() && t.getBlockZ() == e.getFrom().getBlockZ()) {
            return;
        }

        Player p = e.getPlayer();
        Claim c = this.at(t);
        UUID now = c == null ? null : c.owner;
        UUID before = this.lastArea.get(p.getUniqueId());
        if (now == null ? before == null : now.equals(before)) {
            return;
        }

        if (now == null) {
            this.lastArea.remove(p.getUniqueId());
            p.sendActionBar(Text.mm("<gray>🌲 Wilderness"));
        } else {
            this.lastArea.put(p.getUniqueId(), now);
            p.sendActionBar(Text.mm(c.allows(p.getUniqueId()) ? "<green>🏠 Your base" : "<gold>🔒 " + Text.esc(c.name) + "'s base <gray>(protected)"));
        }
    }

    // ---------------------------------------------------------------- commands

    private static final List<String> SUBS = List.of("info", "trust", "untrust", "list", "show", "remove", "help");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("bypass") && p.isOp()) {
            boolean on = this.bypass.add(p.getUniqueId()) || !this.bypass.remove(p.getUniqueId());
            p.sendMessage(Text.mm(on ? "<yellow>Claim bypass ON (you can edit every base)." : "<yellow>Claim bypass OFF."));
            return true;
        }

        if (sub.equals("delete") && p.isOp()) {
            Claim c = this.at(p.getLocation());
            if (c == null) {
                p.sendMessage(Text.mm("<red>You're not standing in a claim."));
            } else {
                this.claims.remove(c.owner);
                this.save();
                p.sendMessage(Text.mm("<yellow>Deleted " + Text.esc(c.name) + "'s claim."));
            }

            return true;
        }

        if (!this.smp(p.getWorld())) {
            p.sendMessage(Text.mm("<red>Claims only work on the Survival SMP."));
            return true;
        }

        Claim mine = this.claims.get(p.getUniqueId());
        switch (sub) {
            case "" -> {
                if (mine != null) {
                    this.info(p, mine);
                    return true;
                }

                this.create(p);
            }
            case "info" -> {
                Claim c = this.at(p.getLocation());
                if (c == null && mine == null) {
                    p.sendMessage(Text.mm("<gray>Nobody owns this land. Type <white>/claim</white> to protect a base here."));
                } else {
                    this.info(p, c != null ? c : mine);
                }
            }
            case "trust", "untrust" -> {
                if (mine == null || args.length < 2) {
                    p.sendMessage(Text.mm(mine == null ? "<red>You don't have a claim yet. Type <white>/claim</white>." : "<red>Use /claim " + sub + " <player>"));
                    return true;
                }

                @SuppressWarnings("deprecation")
                OfflinePlayer o = Bukkit.getOfflinePlayer(args[1]);
                if (!o.hasPlayedBefore() && !o.isOnline()) {
                    p.sendMessage(Text.mm("<red>No player called " + Text.esc(args[1]) + " has joined."));
                    return true;
                }

                if (sub.equals("trust")) {
                    mine.trusted.add(o.getUniqueId());
                    p.sendMessage(Text.mm("<green>" + Text.esc(args[1]) + " can now build and open chests in your base."));
                } else {
                    mine.trusted.remove(o.getUniqueId());
                    p.sendMessage(Text.mm("<yellow>" + Text.esc(args[1]) + " is no longer trusted."));
                }

                this.save();
            }
            case "list" -> {
                if (mine == null) {
                    p.sendMessage(Text.mm("<red>You don't have a claim yet."));
                    return true;
                }

                List<String> names = new ArrayList<>();
                for (UUID u : mine.trusted) {
                    String n = Bukkit.getOfflinePlayer(u).getName();
                    names.add(n == null ? "?" : n);
                }

                p.sendMessage(Text.mm("<gold>Trusted: <white>" + (names.isEmpty() ? "nobody" : Text.esc(String.join(", ", names)))));
            }
            case "show" -> {
                Claim c = this.at(p.getLocation());
                this.show(p, c != null ? c : mine);
            }
            case "remove", "unclaim" -> {
                if (mine == null) {
                    p.sendMessage(Text.mm("<red>You don't have a claim."));
                    return true;
                }

                this.claims.remove(p.getUniqueId());
                this.save();
                p.sendMessage(Text.mm("<yellow>Your claim is removed. Your base is no longer protected."));
            }
            default -> this.help(p);
        }

        return true;
    }

    private void create(Player p) {
        Location l = p.getLocation();
        World w = l.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL) {
            p.sendMessage(Text.mm("<red>Claims only work in the SMP overworld."));
            return;
        }

        Location spawn = this.plugin.modes().spawn(w);
        if (spawn != null && Math.abs(l.getBlockX() - spawn.getBlockX()) <= this.spawnGap + this.radius
            && Math.abs(l.getBlockZ() - spawn.getBlockZ()) <= this.spawnGap + this.radius) {
            p.sendMessage(Text.mm("<red>Too close to spawn. <gray>Go at least " + (this.spawnGap + this.radius) + " blocks away."));
            return;
        }

        for (Claim c : this.claims.values()) {
            if (c.overlaps(w.getName(), l.getBlockX(), l.getBlockZ(), this.radius, this.gap)) {
                p.sendMessage(Text.mm("<red>Too close to <white>" + Text.esc(c.name) + "</white>'s base. <gray>Move a bit further away."));
                return;
            }
        }

        Claim c = new Claim(p.getUniqueId(), p.getName(), w.getName(), l.getBlockX(), l.getBlockZ(), this.radius);
        this.claims.put(c.owner, c);
        this.save();
        int size = this.radius * 2 + 1;
        p.sendMessage(Text.mm("<green><b>Base claimed!</b> <gray>" + size + " x " + size + " blocks around you, from bedrock to the sky."));
        p.sendMessage(Text.mm("<gray>Nobody can break, build, open chests or blow anything up here. <white>/claim trust <player></white> to add friends."));
        this.show(p, c);
    }

    private void info(Player p, Claim c) {
        int size = c.r * 2 + 1;
        p.sendMessage(Text.mm("<gold><b>" + Text.esc(c.name) + "'s base</b> <gray>• center " + c.x + ", " + c.z + " • " + size + " x " + size
            + " • " + c.trusted.size() + " trusted"));
        if (c.owner.equals(p.getUniqueId())) {
            p.sendMessage(Text.mm("<gray>/claim show • /claim trust <player> • /claim untrust <player> • /claim remove"));
        }
    }

    private void help(Player p) {
        p.sendMessage(Text.mm("<gold><b>Claims</b> <gray>(SMP)"));
        p.sendMessage(Text.mm("<white>/claim</white> <gray>protect a " + (this.radius * 2 + 1) + "x" + (this.radius * 2 + 1) + " base around you"));
        p.sendMessage(Text.mm("<white>/claim show</white> <gray>see the border • <white>/claim info</white> <gray>who owns this land"));
        p.sendMessage(Text.mm("<white>/claim trust|untrust <player></white> <gray>• <white>/claim list</white> <gray>• <white>/claim remove"));
    }

    /** Green particles along the border for 15 seconds. */
    private void show(Player p, Claim c) {
        if (c == null) {
            p.sendMessage(Text.mm("<red>No claim here."));
            return;
        }

        int[] left = {15};
        Bukkit.getScheduler().runTaskTimer(this.plugin, task -> {
            if (!p.isOnline() || left[0]-- <= 0 || !p.getWorld().getName().equals(c.world)) {
                task.cancel();
                return;
            }

            double y = p.getLocation().getY() + 1;
            for (int i = -c.r; i <= c.r; i += 2) {
                for (int[] xz : new int[][] {{c.x + i, c.z - c.r}, {c.x + i, c.z + c.r + 1}, {c.x - c.r, c.z + i}, {c.x + c.r + 1, c.z + i}}) {
                    Location at = new Location(p.getWorld(), xz[0], y, xz[1]);
                    if (at.distanceSquared(p.getLocation()) < 64 * 64) {
                        p.spawnParticle(Particle.HAPPY_VILLAGER, at, 1, 0, 0.3, 0, 0);
                    }
                }
            }
        }, 0L, 20L);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }

        return out;
    }
}
