package net.vortexpvp.smp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.build.EcoSpawnDesign;
import net.vortexpvp.build.SpawnBuilder;
import net.vortexpvp.smp.eco.EcoMenus;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The Eco SMP spawn (DonutSMP style): a protected plaza with the Shop, Sell, Shard Shop, Auction House and Orders
 * NPCs, a Warzone where PvP is always on, and an AFK garden that pays shards while you stand in it. Built with
 * /ecospawn where the admin stands; /spawn then lands on it and /afk goes to the AFK garden.
 */
public final class EcoSpawn implements Listener, CommandExecutor, org.bukkit.command.TabCompleter {
    private final VortexPvP plugin;
    private final File file;
    private final NamespacedKey npcKey;
    private String world;
    private int[] spawnBox;
    private int[] afkBox;
    private int[] warBox;
    private Location afkSpot;
    private final List<String> entities = new ArrayList<>();
    /** Seconds each player has stood in the AFK garden since the last shard. */
    private final Map<UUID, Integer> afkTime = new HashMap<>();
    private final Map<UUID, String> zoneOf = new HashMap<>();

    public EcoSpawn(VortexPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "eco-spawn.yml");
        this.npcKey = new NamespacedKey(plugin, "econpc");
    }

    public void load() {
        if (!this.file.exists()) {
            return;
        }

        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        this.world = y.getString("world");
        this.spawnBox = box(y.getIntegerList("spawn"));
        this.afkBox = box(y.getIntegerList("afk"));
        this.warBox = box(y.getIntegerList("warzone"));
        this.entities.clear();
        this.entities.addAll(y.getStringList("entities"));
        List<Double> a = y.getDoubleList("afk-spot");
        World w = this.world == null ? null : Bukkit.getWorld(this.world);
        this.afkSpot = w != null && a.size() >= 4 ? new Location(w, a.get(0), a.get(1), a.get(2), a.get(3).floatValue(), 0.0F) : null;
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("world", this.world);
        y.set("spawn", list(this.spawnBox));
        y.set("afk", list(this.afkBox));
        y.set("warzone", list(this.warBox));
        y.set("entities", this.entities);
        if (this.afkSpot != null) {
            y.set("afk-spot", List.of(this.afkSpot.getX(), this.afkSpot.getY(), this.afkSpot.getZ(), (double)this.afkSpot.getYaw()));
        }

        try {
            y.save(this.file);
        } catch (IOException var3) {
            this.plugin.getLogger().warning("Could not save eco-spawn.yml");
        }
    }

    private static int[] box(List<Integer> l) {
        return l.size() == 6 ? new int[]{l.get(0), l.get(1), l.get(2), l.get(3), l.get(4), l.get(5)} : null;
    }

    private static List<Integer> list(int[] b) {
        return b == null ? List.of() : List.of(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    private boolean in(int[] b, Location l) {
        return b != null
            && l != null
            && l.getWorld() != null
            && l.getWorld().getName().equals(this.world)
            && l.getBlockX() >= b[0] && l.getBlockX() <= b[3]
            && l.getBlockY() >= b[1] && l.getBlockY() <= b[4]
            && l.getBlockZ() >= b[2] && l.getBlockZ() <= b[5];
    }

    public boolean built() {
        return this.spawnBox != null;
    }

    public boolean inSpawn(Location l) {
        return this.in(this.spawnBox, l);
    }

    public boolean inWarzone(Location l) {
        return this.in(this.warBox, l);
    }

    public boolean inAfk(Location l) {
        return this.in(this.afkBox, l);
    }

    /** PvP between two players at these spots: off at spawn, always on in the warzone, untouched elsewhere (null). */
    public Boolean pvp(Location attacker, Location victim) {
        if (!this.inSpawn(victim) && !this.inSpawn(attacker)) {
            return null;
        } else {
            return this.inWarzone(victim) && this.inWarzone(attacker);
        }
    }

    /** Self test: PvP is on in the middle of the warzone, off on the plaza, the AFK garden is AFK and not warzone. */
    public String check() {
        World w = this.world == null ? null : Bukkit.getWorld(this.world);
        if (w == null || this.warBox == null || this.afkBox == null || this.spawnBox == null) {
            return "zones missing";
        }

        Location war = new Location(w, (this.warBox[0] + this.warBox[3]) / 2.0, this.warBox[1] + 8, (this.warBox[2] + this.warBox[5]) / 2.0);
        Location afk = new Location(w, (this.afkBox[0] + this.afkBox[3]) / 2.0, this.afkBox[1] + 8, (this.afkBox[2] + this.afkBox[5]) / 2.0);
        Location plaza = new Location(w, (this.spawnBox[0] + this.spawnBox[3]) / 2.0 + 0.5, this.spawnBox[1] + 8, (this.spawnBox[2] + this.spawnBox[5]) / 2.0 + 14.5);
        Location outside = new Location(w, this.spawnBox[3] + 50, 70, this.spawnBox[5] + 50);
        if (!Boolean.TRUE.equals(this.pvp(war, war))) {
            return "no PvP in the warzone";
        } else if (!Boolean.FALSE.equals(this.pvp(plaza, plaza))) {
            return "PvP on the plaza";
        } else if (!Boolean.FALSE.equals(this.pvp(war, plaza))) {
            return "PvP from the warzone onto the plaza";
        } else if (!this.inAfk(afk) || this.inWarzone(afk)) {
            return "AFK zone wrong";
        } else {
            return this.pvp(outside, outside) == null ? null : "spawn rules outside the spawn";
        }
    }

    // ------------------------------------------------------------------ building

    /** Builds the spawn at {@code at} (the floor is the block below); tells {@code to} how it went. */
    public void build(Location at, CommandSender to) {
        World w = at.getWorld();
        EcoSpawnDesign d = new EcoSpawnDesign(at.getBlockX(), at.getBlockY() - 1, at.getBlockZ(), at.getBlockX() * 31L + at.getBlockZ());
        this.removeEntities();
        to.sendMessage(Text.mm("<yellow>Building the Eco Spawn (a floating island about 150 blocks wide)... <gray>it takes a minute or two."));
        SpawnBuilder.build(this.plugin, w, d).whenComplete((v, err) -> Tasks.ensureSync(() -> {
            if (err != null) {
                to.sendMessage(Text.mm("<red>Building failed - see the console."));
                this.plugin.getLogger().log(java.util.logging.Level.SEVERE, "Building the Eco Spawn failed", err);
                return;
            }

            Map<String, double[]> f = d.features();
            this.world = w.getName();
            this.spawnBox = zone(f, "spawn");
            this.afkBox = zone(f, "afk");
            this.warBox = zone(f, "warzone");
            double[] a = f.get("afk:centre");
            this.afkSpot = a == null ? null : new Location(w, a[0], a[1], a[2], (float)a[3], 0.0F);
            this.entities.clear();

            for (Map.Entry<String, double[]> e : f.entrySet()) {
                double[] p = e.getValue();
                Location l = new Location(w, p[0], p[1], p[2], (float)p[3], 0.0F);
                if (e.getKey().startsWith("econpc:")) {
                    this.npc(l, e.getKey().substring(7));
                } else if (e.getKey().equals("sign:afk")) {
                    this.holo(l, "<#B57CFF><b>✦ AFK ZONE ✦</b>\n<white>Stand inside to earn shards\n<gray>+" + this.eco().afkAmount + " every " + this.eco().afkSeconds + "s", 1.4F);
                } else if (e.getKey().equals("sign:warzone")) {
                    this.holo(l, "<red><b>⚔ WARZONE ⚔</b>\n<gray>PvP is always on past this gate", 1.6F);
                } else if (e.getKey().equals("sign:spawn")) {
                    this.holo(l, "<gradient:#7B2FF7:#2FD3F7><b>ECO SPAWN</b></gradient>\n<gray>/shop • /sell • /shards • /afk", 1.6F);
                }
            }

            double[] s = d.spawn();
            Location spawn = new Location(w, s[0], s[1], s[2], (float)s[3], 0.0F);
            if (this.plugin.modes() != null) {
                this.plugin.modes().setSpawn(spawn);
            }

            this.save();
            to.sendMessage(Text.mm("<green>Eco Spawn built. <gray>/spawn lands on it now; the warzone (north) has PvP on, the AFK garden (south-east) pays shards."));
        }));
    }

    private static int[] zone(Map<String, double[]> f, String key) {
        double[] a = f.get("zone:" + key + ":min");
        double[] b = f.get("zone:" + key + ":max");
        return a != null && b != null
            ? new int[]{
                (int)Math.floor(Math.min(a[0], b[0])), (int)Math.floor(Math.min(a[1], b[1])), (int)Math.floor(Math.min(a[2], b[2])),
                (int)Math.floor(Math.max(a[0], b[0])), (int)Math.floor(Math.max(a[1], b[1])), (int)Math.floor(Math.max(a[2], b[2]))
            }
            : null;
    }

    private void npc(Location l, String what) {
        World w = l.getWorld();
        String title = switch (what) {
            case "shop" -> "<green><b>🛒 Shop";
            case "sell" -> "<gold><b>💰 Sell";
            case "shards" -> "<#B57CFF><b>✦ Shard Shop";
            case "ah" -> "<#FF9F1C><b>⚖ Auction House";
            default -> "<aqua><b>📦 Orders";
        };
        Material hand = switch (what) {
            case "shop" -> Material.EMERALD;
            case "sell" -> Material.GOLD_INGOT;
            case "shards" -> Material.AMETHYST_SHARD;
            case "ah" -> Material.GOLDEN_HELMET;
            default -> Material.CHEST;
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
        this.entities.add(m.getUniqueId().toString());
        this.holo(l.clone().add(0.0, 2.35, 0.0), title + "\n<gray>▶ Click", 1.0F);
    }

    private void holo(Location l, String text, float scale) {
        TextDisplay td = l.getWorld().spawn(l, TextDisplay.class, t -> {
            t.setBillboard(Billboard.CENTER);
            t.setPersistent(true);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            t.text(Text.mm(text));
            t.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(), new org.joml.Quaternionf(), new org.joml.Vector3f(scale, scale, scale), new org.joml.Quaternionf()
            ));
        });
        this.entities.add(td.getUniqueId().toString());
    }

    private int removeEntities() {
        int n = 0;

        for (String s : this.entities) {
            try {
                Entity e = Bukkit.getEntity(UUID.fromString(s));
                if (e != null) {
                    e.remove();
                    n++;
                }
            } catch (IllegalArgumentException var5) {
            }
        }

        this.entities.clear();
        return n;
    }

    /** Takes the NPCs, holograms and zones away (the blocks stay). */
    public int remove() {
        int n = this.removeEntities();
        this.spawnBox = null;
        this.afkBox = null;
        this.warBox = null;
        this.afkSpot = null;
        this.file.delete();
        return n;
    }

    private Economy eco() {
        return this.plugin.economy();
    }

    // ------------------------------------------------------------------ AFK shards and zone titles

    /** Every second: AFK garden shards, and a title when someone walks into the warzone or the AFK garden. */
    public void tick() {
        if (!this.built() || this.eco() == null) {
            return;
        }

        Economy eco = this.eco();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            String zone = this.inWarzone(l) ? "war" : (this.inAfk(l) ? "afk" : (this.inSpawn(l) ? "spawn" : null));
            String was = this.zoneOf.get(p.getUniqueId());
            if (!java.util.Objects.equals(zone, was)) {
                if (zone == null) {
                    this.zoneOf.remove(p.getUniqueId());
                } else {
                    this.zoneOf.put(p.getUniqueId(), zone);
                }

                if ("war".equals(zone)) {
                    p.showTitle(net.kyori.adventure.title.Title.title(Text.mm("<red><b>⚔ WARZONE"), Text.mm("<gray>PvP is on - fight or flee"),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(150L), java.time.Duration.ofMillis(1200L), java.time.Duration.ofMillis(300L))));
                    Snd.play(p, "item.goat_horn.sound.0", 0.6F, 1.0F);
                } else if ("war".equals(was)) {
                    p.sendActionBar(Text.mm("<green>You left the warzone <gray>(PvP off)"));
                } else if ("afk".equals(zone)) {
                    p.showTitle(net.kyori.adventure.title.Title.title(Text.mm("<#B57CFF><b>ᴀꜰᴋ"), Text.mm("<white>Earn " + eco.afkAmount + " shard every " + eco.afkSeconds + "s"),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(150L), java.time.Duration.ofMillis(1500L), java.time.Duration.ofMillis(300L))));
                    Snd.play(p, "block.amethyst_block.chime", 1.0F, 1.0F);
                }
            }

            if ("afk".equals(zone) && !p.isDead()) {
                int t = this.afkTime.merge(p.getUniqueId(), 1, Integer::sum);
                if (t >= eco.afkSeconds) {
                    this.afkTime.put(p.getUniqueId(), 0);
                    eco.giveShards(p.getUniqueId(), eco.afkAmount);
                    p.sendActionBar(Text.mm("<#B57CFF>+" + eco.afkAmount + " Shard" + (eco.afkAmount == 1 ? "" : "s") + " <dark_gray>• <gray>" + eco.shards(p.getUniqueId()) + " total"));
                    Snd.play(p, "block.amethyst_block.chime", 1.0F, 1.4F);
                } else {
                    p.sendActionBar(Text.mm("<gray>Next shard in <#B57CFF>" + (eco.afkSeconds - t) + "s"));
                }
            } else {
                this.afkTime.remove(p.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.afkTime.remove(e.getPlayer().getUniqueId());
        this.zoneOf.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ protection and PvP

    private boolean bypass(Player p) {
        return p.hasPermission("vortexpvp.admin") && p.getGameMode() == org.bukkit.GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (this.inSpawn(e.getBlock().getLocation()) && !this.bypass(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (this.inSpawn(e.getBlock().getLocation()) && !this.bypass(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (this.inSpawn(e.getBlock().getLocation()) && !this.bypass(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (this.built()) {
            e.blockList().removeIf(b -> this.inSpawn(b.getLocation()));
        }
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        } else {
            return damager instanceof Projectile pr && pr.getShooter() instanceof Player p ? p : null;
        }
    }

    /** No PvP at spawn, except in the warzone, where it is always on (even if spawn rules would stop it). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!this.built() || !(e.getEntity() instanceof Player victim)) {
            return;
        }

        Player a = attacker(e.getDamager());
        if (a == null || a.equals(victim)) {
            return;
        }

        Boolean allowed = this.pvp(a.getLocation(), victim.getLocation());
        if (allowed != null) {
            e.setCancelled(!allowed);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpc(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        String what = e.getRightClicked().getPersistentDataContainer().get(this.npcKey, PersistentDataType.STRING);
        if (what != null) {
            e.setCancelled(true);
            this.open(e.getPlayer(), what);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            String what = e.getEntity().getPersistentDataContainer().get(this.npcKey, PersistentDataType.STRING);
            if (what != null) {
                e.setCancelled(true);
                this.open(p, what);
            }
        }
    }

    private void open(Player p, String what) {
        Economy eco = this.eco();
        if (eco == null) {
            return;
        }

        Snd.play(p, "ui.button.click", 0.6F, 1.2F);
        EcoMenus menus = new EcoMenus(this.plugin, eco);
        switch (what) {
            case "shop" -> menus.shop(p);
            case "sell" -> menus.sell(p);
            case "shards" -> menus.shards(p);
            case "ah" -> p.performCommand("ah");
            default -> p.performCommand("orders");
        }
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("afk")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
            } else if (this.afkSpot == null || this.afkSpot.getWorld() == null) {
                p.sendMessage(Text.mm("<red>There is no AFK zone yet. <gray>(an admin builds it with /ecospawn)"));
            } else if (this.plugin.combatTag() != null && this.plugin.combatTag().tagged(p)) {
                p.sendMessage(Text.mm("<red>You can't go AFK in combat."));
            } else {
                net.vortexpvp.session.Tp.to(p, this.afkSpot);
                p.sendMessage(Text.mm("<#B57CFF>✦ <gray>Welcome to the AFK zone: <white>+" + this.eco().afkAmount + " shard every " + this.eco().afkSeconds + "s</white> while you stay inside."));
            }

            return true;
        }

        if (!sender.hasPermission("vortexpvp.admin")) {
            sender.sendMessage(Text.mm("<red>You don't have permission."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("remove")) {
            sender.sendMessage(Text.mm("<gray>Removed " + this.remove() + " NPCs and holograms (the blocks stay)."));
        } else if (args.length >= 4) {
            World w = Bukkit.getWorld(args[0]);
            try {
                if (w == null) {
                    sender.sendMessage(Text.mm("<red>Unknown world."));
                } else {
                    this.build(new Location(w, Integer.parseInt(args[1]) + 0.5, Integer.parseInt(args[2]), Integer.parseInt(args[3]) + 0.5), sender);
                }
            } catch (NumberFormatException var7) {
                sender.sendMessage(Text.mm("<red>Usage: /ecospawn [remove | <world> <x> <y> <z>]"));
            }
        } else if (sender instanceof Player p) {
            this.build(p.getLocation(), p);
        } else {
            sender.sendMessage("Usage: /ecospawn <world> <x> <y> <z>");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("ecospawn") && args.length == 1 && sender.hasPermission("vortexpvp.admin")) {
            List<String> out = new ArrayList<>(List.of("remove"));
            for (World w : Bukkit.getWorlds()) {
                out.add(w.getName());
            }

            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)));
            return out;
        }

        return List.of();
    }

    public static String help() {
        return "/ecospawn [remove]".toLowerCase(Locale.ROOT);
    }
}
