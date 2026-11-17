package net.vortexrealm.survival.smp;

import io.papermc.paper.registry.data.dialog.ActionButton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.vortexrealm.survival.VortexSMP;
import net.vortexrealm.survival.config.Cfg;
import net.vortexrealm.survival.config.Opt;
import net.vortexrealm.survival.dialog.Dialogs;
import net.vortexrealm.survival.dialog.LobbyDialogs;
import net.vortexrealm.survival.gui.Menu;
import net.vortexrealm.survival.gui.Ui;
import net.vortexrealm.survival.session.Tp;
import net.vortexrealm.survival.util.ClientVersion;
import net.vortexrealm.survival.util.Fmt;
import net.vortexrealm.survival.util.Items;
import net.vortexrealm.survival.util.Locs;
import net.vortexrealm.survival.util.Snd;
import net.vortexrealm.survival.util.Tasks;
import net.vortexrealm.survival.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.scheduler.BukkitTask;

public final class Essentials implements Listener, TabExecutor {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,16}");
    private final VortexSMP plugin;
    private final ModeManager modes;
    private final File dataFile;
    private final File warpsFile;
    private final Map<UUID, Map<String, Map<String, String>>> homes = new HashMap<>();
    private final Map<String, Map<String, Essentials.Warp>> warps = new HashMap<>();
    private final Set<UUID> tpaOff = new HashSet<>();
    private final Map<UUID, Map<String, Location>> back = new HashMap<>();
    private final Map<UUID, List<Essentials.Request>> requests = new HashMap<>();
    private final Map<UUID, Essentials.Warmup> warmups = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> rtpCooldowns = new HashMap<>();

    public Essentials(VortexSMP plugin, ModeManager modes) {
        this.plugin = plugin;
        this.modes = modes;
        this.dataFile = new File(plugin.getDataFolder(), "homes.yml");
        this.warpsFile = new File(plugin.getDataFolder(), "warps.yml");
    }

    public void load() {
        this.homes.clear();
        this.tpaOff.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.dataFile);
        ConfigurationSection root = y.getConfigurationSection("homes");
        if (root != null) {
            for (String uuid : root.getKeys(false)) {
                ConfigurationSection perMode = root.getConfigurationSection(uuid);
                if (perMode != null) {
                    try {
                        Map<String, Map<String, String>> byMode = new HashMap<>();

                        for (String mode : perMode.getKeys(false)) {
                            ConfigurationSection hs = perMode.getConfigurationSection(mode);
                            if (hs != null) {
                                Map<String, String> m = new LinkedHashMap<>();

                                for (String name : hs.getKeys(false)) {
                                    m.put(name, hs.getString(name));
                                }

                                byMode.put(mode, m);
                            }
                        }

                        this.homes.put(UUID.fromString(uuid), byMode);
                    } catch (IllegalArgumentException var14) {
                    }
                }
            }
        }

        for (String s : y.getStringList("tpa-off")) {
            try {
                this.tpaOff.add(UUID.fromString(s));
            } catch (IllegalArgumentException var13) {
            }
        }

        this.warps.clear();
        YamlConfiguration w = YamlConfiguration.loadConfiguration(this.warpsFile);
        ConfigurationSection ws = w.getConfigurationSection("warps");
        if (ws != null) {
            for (String modex : ws.getKeys(false)) {
                ConfigurationSection m = ws.getConfigurationSection(modex);
                if (m != null) {
                    Map<String, Essentials.Warp> map = new LinkedHashMap<>();

                    for (String name : m.getKeys(false)) {
                        Material icon = Material.matchMaterial(m.getString(name + ".icon", "ENDER_PEARL"));
                        map.put(name, new Essentials.Warp(name, m.getString(name + ".location"), icon != null && icon.isItem() ? icon : Material.ENDER_PEARL));
                    }

                    this.warps.put(modex, map);
                }
            }
        }
    }

    // ------------------------------------------------------------------ bringing old homes and warps back

    /**
     * Adds the homes of a homes.yml (homes.&lt;uuid&gt;.&lt;MODE&gt;.&lt;name&gt;: this plugin's, the 2.x one and VortexSMP's all
     * use it). {@code replace}: a home with the same name is set to the old spot too. {@code dry}: only counts.
     * Returns how many homes were (or would be) added or changed.
     */
    public int mergeHomes(YamlConfiguration y, boolean replace, boolean dry) {
        int n = 0;
        ConfigurationSection root = y.getConfigurationSection("homes");
        if (root == null) {
            return 0;
        }

        for (String uuid : root.getKeys(false)) {
            UUID u;
            try {
                u = UUID.fromString(uuid);
            } catch (IllegalArgumentException e) {
                continue;
            }

            ConfigurationSection perMode = root.getConfigurationSection(uuid);
            for (String mode : perMode == null ? java.util.Set.<String>of() : perMode.getKeys(false)) {
                ConfigurationSection hs = perMode.getConfigurationSection(mode);
                for (String name : hs == null ? java.util.Set.<String>of() : hs.getKeys(false)) {
                    String loc = hs.getString(name);
                    if (loc == null) {
                        continue;
                    }

                    Map<String, String> mine = dry ? this.homes.getOrDefault(u, Map.of()).getOrDefault(mode, Map.of()) : this.homesOf(u, mode);
                    String now = mine.get(name);
                    if (now == null || replace && !now.equals(loc)) {
                        if (!dry) {
                            mine.put(name, loc);
                        }

                        n++;
                    }
                }
            }
        }

        if (!dry && n > 0) {
            this.saveData();
        }

        return n;
    }

    /** Adds the warps of a warps.yml (warps.&lt;MODE&gt;.&lt;name&gt;.location/icon), the same way as {@link #mergeHomes}. */
    public int mergeWarps(YamlConfiguration y, boolean replace, boolean dry) {
        int n = 0;
        ConfigurationSection ws = y.getConfigurationSection("warps");
        for (String mode : ws == null ? java.util.Set.<String>of() : ws.getKeys(false)) {
            ConfigurationSection m = ws.getConfigurationSection(mode);
            for (String name : m == null ? java.util.Set.<String>of() : m.getKeys(false)) {
                String loc = m.getString(name + ".location");
                if (loc == null) {
                    continue;
                }

                Essentials.Warp now = this.warps.getOrDefault(mode, Map.of()).get(name);
                if (now == null || replace && !now.location().equals(loc)) {
                    if (!dry) {
                        Material icon = Material.matchMaterial(m.getString(name + ".icon", "ENDER_PEARL"));
                        this.warps.computeIfAbsent(mode, k -> new LinkedHashMap<>())
                            .put(name, new Essentials.Warp(name, loc, icon != null && icon.isItem() ? icon : Material.ENDER_PEARL));
                    }

                    n++;
                }
            }
        }

        if (!dry && n > 0) {
            this.saveWarps();
        }

        return n;
    }

    public int homeCount() {
        int n = 0;
        for (Map<String, Map<String, String>> byMode : this.homes.values()) {
            for (Map<String, String> m : byMode.values()) {
                n += m.size();
            }
        }

        return n;
    }

    public int warpCount() {
        int n = 0;
        for (Map<String, Essentials.Warp> m : this.warps.values()) {
            n += m.size();
        }

        return n;
    }

    /** Moves the homes of a player whose ID changed (moved to a host with another online-mode). */
    public void rekeyHomes(UUID from, UUID to) {
        Map<String, Map<String, String>> old = this.homes.remove(from);
        if (old == null) {
            return;
        }

        Map<String, Map<String, String>> mine = this.homes.computeIfAbsent(to, k -> new HashMap<>());
        for (Map.Entry<String, Map<String, String>> e : old.entrySet()) {
            Map<String, String> m = mine.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>());
            e.getValue().forEach(m::putIfAbsent);
        }

        this.saveData();
    }

    private void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Player homes per mode (SURVIVAL, LIFESTEAL, ...) and /tptoggle."));

        for (Entry<UUID, Map<String, Map<String, String>>> e : this.homes.entrySet()) {
            for (Entry<String, Map<String, String>> m : e.getValue().entrySet()) {
                for (Entry<String, String> h : m.getValue().entrySet()) {
                    y.set("homes." + e.getKey() + "." + m.getKey() + "." + h.getKey(), h.getValue());
                }
            }
        }

        List<String> off = new ArrayList<>();

        for (UUID u : this.tpaOff) {
            off.add(u.toString());
        }

        y.set("tpa-off", off);

        try {
            y.save(this.dataFile);
        } catch (IOException var8) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save homes.yml", (Throwable)var8);
        }
    }

    private void saveWarps() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Warps per mode. Admins: /setwarp <name> (icon = item in hand), /delwarp <name>."));

        for (Entry<String, Map<String, Essentials.Warp>> e : this.warps.entrySet()) {
            for (Essentials.Warp w : e.getValue().values()) {
                y.set("warps." + e.getKey() + "." + w.name() + ".location", w.location());
                y.set("warps." + e.getKey() + "." + w.name() + ".icon", w.icon().name());
            }
        }

        try {
            y.save(this.warpsFile);
        } catch (IOException var6) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save warps.yml", (Throwable)var6);
        }
    }

    private boolean smp(World w) {
        return w != null && !this.plugin.worlds().managed(w) && this.modes.mode(w).smp();
    }

    /**
     * The SMP world a command is about: the one the player is in, or the main SMP world from the hub (homes, /rtp and
     * /back work from the lobby too - the inventory switches when they arrive). Null in the practice worlds.
     */
    private World here(Player p) {
        World w = p.getWorld();
        if (this.smp(w)) {
            return w;
        }

        return this.plugin.worlds().managed(w) ? null : this.plugin.realm().smpWorld();
    }

    private boolean hub(World w) {
        return w != null && !this.plugin.worlds().managed(w) && this.modes.mode(w) == WorldMode.HUB;
    }

    private String group(World w) {
        return this.modes.mode(w).name();
    }

    private String modeLabel(World w) {
        WorldMode m = this.modes.mode(w);
        return m.color + Text.esc(m.display);
    }

    private static World mainWorld(World w) {
        String n = w.getName();

        for (String suffix : List.of("_nether", "_the_end")) {
            if (n.endsWith(suffix)) {
                World base = Bukkit.getWorld(n.substring(0, n.length() - suffix.length()));
                if (base != null) {
                    return base;
                }
            }
        }

        return w;
    }

    private static void msg(CommandSender s, String mini) {
        s.sendMessage(Text.mm(mini));
    }

    private static void err(Player p, String text) {
        p.sendMessage(Text.mm("<red>" + Text.esc(text)));
        Snd.error(p);
    }

    private boolean bypass(Player p) {
        return p.hasPermission("vortexsmp.teleport.bypass");
    }

    /** Homes a player may have per mode: the default, more for staff roles, or a rank's vortex.homes.N. */
    int maxHomes(Player p) {
        if (p.hasPermission("vortexsmp.homes.unlimited")) {
            return Integer.MAX_VALUE;
        } else {
            int max = Cfg.i(Opt.HOMES_MAX);
            if (p.hasPermission("vortexsmp.homes.role.admin")) {
                max = Math.max(max, Cfg.i(Opt.HOMES_ADMIN));
            } else if (p.hasPermission("vortexsmp.homes.role.mod")) {
                max = Math.max(max, Cfg.i(Opt.HOMES_MOD));
            } else if (p.hasPermission("vortexsmp.homes.role.helper")) {
                max = Math.max(max, Cfg.i(Opt.HOMES_HELPER));
            }

            if (p.isOp()) {
                max = Math.max(max, Cfg.i(Opt.HOMES_ADMIN));
            }

            for (PermissionAttachmentInfo pi : p.getEffectivePermissions()) {
                String perm = pi.getPermission();
                if (pi.getValue() && (perm.startsWith("vortexsmp.homes.") || perm.startsWith("vortex.homes."))) {
                    try {
                        max = Math.max(max, Integer.parseInt(perm.substring(perm.indexOf(".homes.") + 7)));
                    } catch (NumberFormatException var7) {
                    }
                }
            }

            return max;
        }
    }

    private Map<String, String> homesOf(UUID u, String mode) {
        return this.homes.computeIfAbsent(u, k -> new HashMap<>()).computeIfAbsent(mode, k -> new LinkedHashMap<>());
    }

    private void warmup(Player p, String what, Runnable done) {
        this.warmup(p, what, done, null);
    }

    /** {@code failed} runs (once) when the teleport does not happen: combat, cooldown, moved, damaged or replaced. */
    private void warmup(Player p, String what, Runnable done, Runnable failed) {
        // a teleport already counting down is replaced, and the player is told
        this.cancelWarmup(p, "a new teleport started");
        if (this.plugin.combatTag() != null && this.plugin.combatTag().tagged(p) && !p.hasPermission("vortexsmp.admin")) {
            err(p, "You can't teleport in combat (" + this.plugin.combatTag().secondsLeft(p) + "s).");
            if (failed != null) {
                failed.run();
            }
        } else {
            Long cd = this.cooldowns.get(p.getUniqueId());
            if (cd != null && cd > System.currentTimeMillis() && !this.bypass(p)) {
                err(p, "Wait " + ((cd - System.currentTimeMillis()) / 1000L + 1L) + "s before teleporting again.");
                if (failed != null) {
                    failed.run();
                }
            } else {
                int secs = this.bypass(p) ? 0 : Cfg.i(Opt.TP_WARMUP);
                if (secs <= 0) {
                    done.run();
                } else {
                    Essentials.Warmup w = new Essentials.Warmup(p.getLocation(), secs * 4);
                    w.failed = failed;
                    this.warmups.put(p.getUniqueId(), w);
                    msg(
                        p,
                        "<gray>Teleporting to <gold>"
                            + Text.esc(what)
                            + "</gold> in <white>"
                            + secs
                            + "s</white>"
                            + (Cfg.bool(Opt.TP_CANCEL_MOVE) ? " <dark_gray>- don't move" : "")
                            + "<gray>."
                    );
                    Snd.play(p, "block.portal.trigger", 0.25F, 1.8F);
                    w.task = Tasks.timer(
                        5L,
                        5L,
                        () -> {
                            if (p.isOnline() && this.warmups.get(p.getUniqueId()) == w) {
                                w.quarters--;
                                Location l = p.getLocation();
                                double a = w.quarters * 0.9;

                                for (int k = 0; k < 2; k++) {
                                    double ang = a + k * Math.PI;
                                    p.getWorld()
                                        .spawnParticle(
                                            Particle.PORTAL,
                                            l.getX() + Math.cos(ang) * 0.7,
                                            l.getY() + 0.2 + (secs * 4 - w.quarters) * 0.45 / secs,
                                            l.getZ() + Math.sin(ang) * 0.7,
                                            2,
                                            0.0,
                                            0.0,
                                            0.0,
                                            0.05
                                        );
                                }

                                if (w.quarters <= 0) {
                                    Tasks.cancel(w.task);
                                    this.warmups.remove(p.getUniqueId());
                                    if (this.plugin.combatTag() != null && this.plugin.combatTag().tagged(p) && !p.hasPermission("vortexsmp.admin")) {
                                        err(p, "Teleport cancelled - you are in combat.");
                                        if (w.failed != null) {
                                            w.failed.run();
                                        }
                                    } else {
                                        done.run();
                                    }
                                } else {
                                    if (w.quarters % 4 == 0) {
                                        int left = w.quarters / 4;
                                        p.sendActionBar(Text.mm("<gray>Teleporting in <gold><b>" + left + "</b></gold>..."));
                                        Snd.play(p, "block.note_block.hat", 0.5F, 1.0F + (secs - left) * 0.2F);
                                    }
                                }
                            } else {
                                Tasks.cancel(w.task);
                            }
                        }
                    );
                }
            }
        }
    }

    private void cancelWarmup(Player p, String why) {
        Essentials.Warmup w = this.warmups.remove(p.getUniqueId());
        if (w != null) {
            Tasks.cancel(w.task);
            if (why != null) {
                p.sendActionBar(Text.mm("<red>Teleport cancelled - " + why + "."));
                Snd.error(p);
            }

            if (w.failed != null) {
                w.failed.run();
            }
        }
    }

    private boolean arrive(Player p, Location to, String what) {
        if (to == null || to.getWorld() == null) {
            err(p, "That place is not loaded right now.");
            return false;
        } else if (!Tp.to(p, to)) {
            return false;
        } else {
            this.cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + Cfg.i(Opt.TP_COOLDOWN) * 1000L);
            Snd.play(p, "entity.enderman.teleport", 0.7F, 1.3F);
            p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0.0, 1.0, 0.0), 30, 0.4, 0.8, 0.4, 0.04);
            p.sendActionBar(Text.mm("<green>✔ <white>" + Text.esc(what)));
            return true;
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onMove(PlayerMoveEvent e) {
        Essentials.Warmup w = this.warmups.get(e.getPlayer().getUniqueId());
        if (w != null && Cfg.bool(Opt.TP_CANCEL_MOVE)) {
            Location to = e.getTo();
            if (!to.getWorld().equals(w.from.getWorld())
                || Math.abs(to.getX() - w.from.getX()) > 0.6
                || Math.abs(to.getZ() - w.from.getZ()) > 0.6
                || Math.abs(to.getY() - w.from.getY()) > 1.3) {
                this.cancelWarmup(e.getPlayer(), "you moved");
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && this.warmups.containsKey(p.getUniqueId()) && Cfg.bool(Opt.TP_CANCEL_DAMAGE)) {
            this.cancelWarmup(p, "you took damage");
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        this.cancelWarmup(e.getPlayer(), null);
        this.requests.remove(u);

        for (List<Essentials.Request> list : this.requests.values()) {
            list.removeIf(r -> r.from().equals(u));
        }

        this.requests.values().removeIf(List::isEmpty);
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onTeleport(PlayerTeleportEvent e) {
        switch (e.getCause()) {
            case COMMAND:
            case PLUGIN:
            case UNKNOWN:
                Location from = e.getFrom();
                if (this.smp(from.getWorld()) && e.getTo().getWorld() != null) {
                    if (from.getWorld().equals(e.getTo().getWorld()) && from.distanceSquared(e.getTo()) < 25.0) {
                        return;
                    }

                    this.back.computeIfAbsent(e.getPlayer().getUniqueId(), k -> new HashMap<>()).put(this.group(from.getWorld()), from.clone());
                    return;
                }

                return;
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (this.smp(p.getWorld()) && Cfg.bool(Opt.ESSENTIALS) && Cfg.bool(Opt.ESS_BACK)) {
            String mode = this.group(p.getWorld());
            if (modeListed(Cfg.s(Opt.BACK_DEATH_MODES), mode)) {
                this.back.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(mode, p.getLocation().clone());
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (this.smp(p.getWorld()) && Cfg.bool(Opt.ESSENTIALS) && Cfg.bool(Opt.ESS_BACK) && modeListed(Cfg.s(Opt.BACK_DEATH_MODES), this.group(p.getWorld()))) {
            Tasks.later(
                20L,
                () -> {
                    if (p.isOnline()) {
                        msg(
                            p,
                            "<gray>Use <gold><click:run_command:'/back'><hover:show_text:'<gray>Teleport back'>/back</hover></click></gold> to return to where you died."
                        );
                    }
                }
            );
        }
    }

    public static boolean modeListed(String list, String mode) {
        for (String s : list.split(",")) {
            if (s.trim().equalsIgnoreCase(mode)) {
                return true;
            }
        }

        return false;
    }

    public void tick() {
        long ttl = Cfg.i(Opt.TPA_EXPIRE) * 1000L;
        long now = System.currentTimeMillis();
        Iterator<Entry<UUID, List<Essentials.Request>>> it = this.requests.entrySet().iterator();

        while (it.hasNext()) {
            List<Essentials.Request> list = it.next().getValue();
            Iterator<Essentials.Request> ri = list.iterator();

            while (ri.hasNext()) {
                Essentials.Request r = ri.next();
                if (now - r.created() > ttl) {
                    ri.remove();
                    Player from = Bukkit.getPlayer(r.from());
                    Player to = Bukkit.getPlayer(r.to());
                    if (from != null) {
                        msg(from, "<gray>Your teleport request to <white>" + (to == null ? "?" : Text.esc(to.getName())) + "</white> expired.");
                    }

                    if (to != null && from != null) {
                        msg(to, "<gray>The teleport request from <white>" + Text.esc(from.getName()) + "</white> expired.");
                    }
                }
            }

            if (list.isEmpty()) {
                it.remove();
            }
        }

        this.cooldowns.values().removeIf(t -> t < now);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        } else if (!Cfg.bool(Opt.ESSENTIALS)) {
            err(p, "That command is disabled on this server.");
            return true;
        } else {
            switch (cmd) {
                case "spawn":
                    this.spawn(p);
                    break;
                case "sethome":
                case "home":
                case "delhome":
                case "homes":
                    if (!Cfg.bool(Opt.ESS_HOMES)) {
                        err(p, "Homes are disabled on this server.");
                    } else if (this.here(p) == null) {
                        err(p, this.plugin.realm().smpWorld() == null ? "There is no SMP world yet." : "Leave Practice first: /hub, then try again.");
                    } else if (cmd.equals("sethome") && !this.smp(p.getWorld())) {
                        err(p, "Set homes in the SMP: /smp first.");
                    } else {
                        switch (cmd) {
                            case "sethome":
                                this.setHome(p, args.length > 0 ? args[0] : "home");
                                return true;
                            case "home":
                                this.home(p, args.length > 0 ? args[0] : null);
                                return true;
                            case "delhome":
                                if (args.length == 0) {
                                    err(p, "Usage: /delhome <name>");
                                } else {
                                    this.delHome(p, args[0]);
                                }

                                return true;
                            default:
                                this.homesChest(p);
                        }
                    }
                    break;
                case "back":
                    if (!Cfg.bool(Opt.ESS_BACK)) {
                        err(p, "/back is disabled on this server.");
                    } else if (this.here(p) == null) {
                        err(p, "Leave Practice first: /hub, then try again.");
                    } else {
                        this.back(p);
                    }
                    break;
                case "rtp":
                    if (!Cfg.bool(Opt.ESS_RTP)) {
                        err(p, "Random teleport is disabled on this server.");
                    } else if (this.here(p) == null) {
                        err(p, this.plugin.realm().smpWorld() == null ? "There is no SMP world yet." : "Leave Practice first: /hub, then /rtp.");
                    } else if (!this.smp(p.getWorld()) && args.length == 0) {
                        // from the lobby: a random spot in the SMP
                        this.rtpIn(p, this.here(p));
                    } else if (args.length == 0 && this.rtpWorlds(p).size() > 1) {
                        this.rtpMenu(p);
                    } else if (args.length > 0) {
                        String want = args[0].toLowerCase(Locale.ROOT);
                        World pick = null;

                        for (World rw : this.rtpWorlds(p)) {
                            String kind = rw.getEnvironment() == Environment.NETHER ? "nether" : (rw.getEnvironment() == Environment.THE_END ? "end" : "overworld");
                            if (kind.equals(want) || rw.getName().equalsIgnoreCase(want)) {
                                pick = rw;
                            }
                        }

                        if (pick == null) {
                            err(p, "Unknown world \"" + args[0] + "\". Use /rtp overworld, nether or end.");
                        } else if (pick.equals(p.getWorld())) {
                            this.rtp(p);
                        } else {
                            this.rtpIn(p, pick);
                        }
                    } else {
                        this.rtp(p);
                    }
                    break;
                case "tpa":
                case "tpahere":
                    if (!this.tpaAllowed(p)) {
                        return true;
                    }

                    if (args.length == 0) {
                        err(p, "Usage: /" + cmd + " <player>");
                        return true;
                    }

                    Player t = this.findPlayer(p, args[0]);
                    if (t == null || !p.canSee(t)) {
                        err(p, "That player is not online.");
                        return true;
                    }

                    this.request(p, t, cmd.equals("tpahere"));
                    break;
                case "tpaccept":
                    if (this.tpaAllowed(p)) {
                        this.accept(p, args.length > 0 ? args[0] : null);
                    }
                    break;
                case "tpdeny":
                    this.deny(p, args.length > 0 ? args[0] : null);
                    break;
                case "tpacancel":
                    int n = 0;

                    for (List<Essentials.Request> list : this.requests.values()) {
                        int before = list.size();
                        list.removeIf(r -> r.from().equals(p.getUniqueId()));
                        n += before - list.size();
                    }

                    this.requests.values().removeIf(List::isEmpty);
                    msg(
                        p,
                        n == 0
                            ? "<gray>You have no pending teleport requests."
                            : "<gray>Cancelled <white>" + n + "</white> teleport request" + (n == 1 ? "" : "s") + "."
                    );
                    break;
                case "tptoggle":
                    boolean off = !this.tpaOff.remove(p.getUniqueId());
                    if (off) {
                        this.tpaOff.add(p.getUniqueId());
                    }

                    this.saveData();
                    msg(p, off ? "<gray>Teleport requests: <red><b>OFF</b></red> <dark_gray>(nobody can /tpa you)" : "<gray>Teleport requests: <green><b>ON");
                    break;
                case "warp":
                case "warps":
                case "setwarp":
                case "delwarp":
                    if (!Cfg.bool(Opt.ESS_WARPS)) {
                        err(p, "Warps are disabled on this server.");
                    } else if (!this.smp(p.getWorld()) && !this.hub(p.getWorld())) {
                        err(p, "Warps only work in the SMP worlds and the hub.");
                    } else {
                        this.warpCommand(p, cmd, args);
                    }
                    break;
                default:
                    return false;
            }

            return true;
        }
    }

    private void spawn(Player p) {
        World w = p.getWorld();
        if (this.plugin.worlds().managed(w)) {
            p.performCommand("lobby");
        } else if (!this.smp(w) && !this.hub(w)) {
            err(p, "There is no spawn here.");
        } else {
            World main = mainWorld(w);
            Location target = this.modes.spawn(main);
            this.warmup(p, "spawn", () -> this.arrive(p, target, "Spawn"));
        }
    }

    private void back(Player p) {
        Map<String, Location> m = this.back.get(p.getUniqueId());
        Location l = m == null ? null : m.get(this.group(this.here(p)));
        if (l != null && l.getWorld() != null) {
            Location target = l.clone();
            this.warmup(p, "your last location", () -> this.arrive(p, target, "Back"));
        } else {
            err(p, "You have nowhere to go back to.");
        }
    }

    private void setHome(Player p, String raw) {
        if (!NAME.matcher(raw).matches()) {
            err(p, "Home names: letters, numbers, - and _ (max 16).");
        } else {
            String name = raw.toLowerCase(Locale.ROOT);
            String mode = this.group(p.getWorld());
            Map<String, String> mine = this.homesOf(p.getUniqueId(), mode);
            int max = this.maxHomes(p);
            if (!mine.containsKey(name) && mine.size() >= max) {
                err(p, "You already have " + mine.size() + "/" + max + " homes here. Delete one with /delhome <name>.");
            } else {
                boolean moved = mine.containsKey(name);
                mine.put(name, Locs.serialize(p.getLocation()));
                this.saveData();
                Snd.play(p, "block.note_block.pling", 0.7F, 1.6F);
                msg(
                    p,
                    "<green>✔ Home <white>"
                        + name
                        + "</white> "
                        + (moved ? "moved here" : "set")
                        + "! <gray>("
                        + mine.size()
                        + "/"
                        + (max == Integer.MAX_VALUE ? "∞" : max)
                        + " in "
                        + this.modeLabel(p.getWorld())
                        + "<gray>) <dark_gray>/home "
                        + name
                );
            }
        }
    }

    private void home(Player p, String raw) {
        Map<String, String> mine = this.homesOf(p.getUniqueId(), this.group(this.here(p)));
        if (mine.isEmpty()) {
            msg(p, "<gray>You have no homes in " + this.modeLabel(p.getWorld()) + "<gray> yet. <gold>/sethome [name]");
        } else {
            String name;
            if (raw == null) {
                if (mine.containsKey("home")) {
                    name = "home";
                } else {
                    if (mine.size() != 1) {
                        this.homesMenu(p);
                        return;
                    }

                    name = mine.keySet().iterator().next();
                }
            } else {
                name = raw.toLowerCase(Locale.ROOT);
            }

            String loc = mine.get(name);
            if (loc == null) {
                err(p, "No home called '" + raw + "'. Your homes: " + String.join(", ", mine.keySet()));
            } else {
                Location target = Locs.parse(loc);
                if (target == null) {
                    err(p, "The world of that home is not loaded.");
                } else {
                    this.warmup(p, "home " + name, () -> this.arrive(p, target, "Home " + name));
                }
            }
        }
    }

    private void delHome(Player p, String raw) {
        String name = raw.toLowerCase(Locale.ROOT);
        Map<String, String> mine = this.homesOf(p.getUniqueId(), this.group(this.here(p)));
        if (mine.remove(name) == null) {
            err(p, "No home called '" + raw + "'.");
        } else {
            this.saveData();
            msg(p, "<gray>Home <white>" + Text.esc(name) + "</white> deleted.");
        }
    }

    public void homesMenu(Player p) {
        String mode = this.group(this.here(p));
        Map<String, String> mine = this.homesOf(p.getUniqueId(), mode);
        int max = this.maxHomes(p);
        if (ClientVersion.supportsDialogs(p)) {
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();

            for (Entry<String, String> e : mine.entrySet()) {
                String name = e.getKey();
                Location l = Locs.parse(e.getValue());
                String where = l == null
                    ? "<red>world not loaded"
                    : "<gray>" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + " <dark_gray>" + Text.esc(ModeManager.worldLabel(l.getWorld()));
                tiles.add(
                    new LobbyDialogs.Tile(
                        new ItemStack(homeIcon(l)),
                        Text.mm("<gold><b>" + Text.esc(name)),
                        Text.mm(where),
                        List.of(Text.mm("<gold><b>" + Text.esc(name)), Text.mm(where), Component.empty(), Text.mm("<green>▶ Click to teleport")),
                        () -> {
                            p.closeDialog();
                            this.home(p, name);
                        }
                    )
                );
            }

            LobbyDialogs ui = this.plugin.dialogs().lobby();
            List<ActionButton> buttons = new ArrayList<>();
            buttons.add(
                ui.btn(
                    p,
                    "<green>+ Set Home Here",
                    "<gray>Save this spot as a home",
                    () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>New Home"), "Name", mine.isEmpty() ? "home" : "", 16, namex -> {
                        if (!namex.isBlank()) {
                            this.setHome(p, namex);
                        }
                    })
                )
            );
            if (!mine.isEmpty()) {
                buttons.add(
                    ui.btn(
                        p,
                        "<red>✖ Delete A Home",
                        null,
                        () -> {
                            List<Dialogs.Choice> choices = new ArrayList<>();

                            for (String n : mine.keySet()) {
                                choices.add(new Dialogs.Choice(n, "<white>" + n));
                            }

                            this.plugin
                                .dialogs()
                                .choice(
                                    p,
                                    Text.mm("<red><b>Delete Home"),
                                    "Home",
                                    choices,
                                    null,
                                    nx -> this.plugin
                                        .dialogs()
                                        .confirm(
                                            p,
                                            Text.mm("<red><b>Delete " + Text.esc(nx) + "?"),
                                            List.of(Text.mm("<gray>This can't be undone.")),
                                            () -> this.delHome(p, nx)
                                        )
                                );
                        }
                    )
                );
            }

            ui.screen(
                p,
                LobbyDialogs.title("Homes", "Click a home to teleport"),
                tiles,
                List.of(
                    Text.mm(
                        "<gray>Homes in "
                            + this.modeLabel(p.getWorld())
                            + "<gray>: <white>"
                            + mine.size()
                            + "</white>/<white>"
                            + (max == Integer.MAX_VALUE ? "∞" : max)
                    )
                ),
                Text.mm("<gray>No homes yet - stand somewhere nice and click <green>+ Set Home Here</green>."),
                buttons,
                2,
                ui.close(p)
            );
        } else {
            Component line = Text.mm("<gold>Homes <gray>(" + mine.size() + "): ");

            for (String name : mine.keySet()) {
                line = line.append(Text.mm("<white><click:run_command:'/home " + name + "'><hover:show_text:'<gray>Teleport'>[" + name + "]</hover></click> "));
            }

            p.sendMessage(line);
        }
    }

    private static Material homeIcon(Location l) {
        if (l != null && l.getWorld() != null) {
            return switch (l.getWorld().getEnvironment()) {
                case NETHER -> Material.CRIMSON_NYLIUM;
                case THE_END -> Material.END_STONE;
                default -> Material.RED_BED;
            };
        } else {
            return Material.BARRIER;
        }
    }

    private void rtp(Player p) {
        World w = p.getWorld();
        {
            Long cd = this.rtpCooldowns.get(p.getUniqueId());
            if (cd != null && cd > System.currentTimeMillis() && !this.bypass(p)) {
                long s = (cd - System.currentTimeMillis()) / 1000L + 1L;
                err(p, "You can /rtp again in " + (s >= 60L ? s / 60L + "m " + s % 60L + "s" : s + "s") + ".");
            } else {
                this.warmup(p, "a random spot", () -> {
                    p.sendActionBar(Text.mm("<gray>Searching for a safe spot..."));
                    this.search(p, w, 0);
                });
            }
        }
    }

    private List<World> rtpWorlds(Player p) {
        List<World> out = new ArrayList<>();
        World at = this.here(p);
        String n = (at == null ? p.getWorld() : at).getName();
        String base = n.endsWith("_nether") ? n.substring(0, n.length() - 7) : (n.endsWith("_the_end") ? n.substring(0, n.length() - 8) : n);

        for (String s : new String[]{"", "_nether", "_the_end"}) {
            World w = Bukkit.getWorld(base + s);
            if (w != null) {
                out.add(w);
            }
        }

        return out;
    }

    private void rtpMenu(Player p) {
        final List<World> worlds = this.rtpWorlds(p);
        (new Menu(this.plugin, p) {
                @Override
                protected Component title() {
                    return Text.mm("<dark_gray>Random Teleport");
                }

                @Override
                protected int rows() {
                    return 3;
                }

                @Override
                protected void draw() {
                    int[] slots = Ui.centered(worlds.size(), 3);

                    for (int i = 0; i < slots.length; i++) {
                        World w = worlds.get(i);
                        boolean end = w.getEnvironment() == Environment.THE_END;

                        Material icon = switch (w.getEnvironment()) {
                            case NETHER -> Material.NETHERRACK;
                            case THE_END -> Material.END_STONE;
                            default -> Material.GRASS_BLOCK;
                        };

                        String label = switch (w.getEnvironment()) {
                            case NETHER -> "<red><b>Nether";
                            case THE_END -> "<light_purple><b>The End";
                            default -> "<green><b>Overworld";
                        };
                        this.set(
                            slots[i],
                            Items.of(icon)
                                .name(label)
                                .lore(
                                    "<gray>" + w.getPlayers().size() + " players here",
                                    "",
                                    end ? "<green>▶ Click to go to a random outer island" : "<green>▶ Click to teleport somewhere random"
                                )
                                .build(),
                            t -> {
                                this.viewer.closeInventory();
                                Essentials.this.rtpIn(this.viewer, w);
                            }
                        );
                    }

                    this.fill(Material.GRAY_STAINED_GLASS_PANE);
                }
            })
            .open();
    }

    private void rtpIn(Player p, World w) {
        Long cd = this.rtpCooldowns.get(p.getUniqueId());
        if (cd != null && cd > System.currentTimeMillis() && !this.bypass(p)) {
            long s = (cd - System.currentTimeMillis()) / 1000L + 1L;
            err(p, "You can /rtp again in " + (s >= 60L ? s / 60L + "m " + s % 60L + "s" : s + "s") + ".");
        } else {
            this.warmup(p, "a random spot", () -> {
                p.sendActionBar(Text.mm("<gray>Searching for a safe spot..."));
                this.search(p, w, 0);
            });
        }
    }

    public void homesChest(Player p) {
        String mode = this.group(this.here(p));
        final Map<String, String> mine = this.homesOf(p.getUniqueId(), mode);
        final int max = this.maxHomes(p);
        (new Menu(this.plugin, p) {
                @Override
                protected Component title() {
                    return Text.mm("<dark_gray>Homes <gray>(" + mine.size() + "/" + (max == Integer.MAX_VALUE ? "∞" : max) + ")");
                }

                @Override
                protected int rows() {
                    return 4;
                }

                @Override
                protected void draw() {
                    List<String> names = new ArrayList<>(mine.keySet());
                    int[] slots = Ui.fit(names.size(), 3);

                    for (int i = 0; i < names.size() && i < slots.length; i++) {
                        String name = names.get(i);
                        Location l = Locs.parse(mine.get(name));
                        String where = l == null ? "<red>world not loaded" : "<gray>" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
                        this.set(
                            slots[i],
                            Items.of(Essentials.homeIcon(l))
                                .name("<gold><b>" + Text.esc(name))
                                .lore(where, "", "<green>Click <gray>teleport", "<red>Right-click <gray>delete")
                                .build(),
                            t -> {
                                if (t.isRightClick()) {
                                    this.plugin
                                        .dialogs()
                                        .confirm(
                                            this.viewer,
                                            Text.mm("<red><b>Delete " + Text.esc(name) + "?"),
                                            List.of(Text.mm("<gray>This can't be undone.")),
                                            () -> {
                                                Essentials.this.delHome(this.viewer, name);
                                                Essentials.this.homesChest(this.viewer);
                                            }
                                        );
                                } else {
                                    this.viewer.closeInventory();
                                    Essentials.this.home(this.viewer, name);
                                }
                            }
                        );
                    }

                    this.set(31, Items.of(Material.LIME_BED).name("<green><b>+ Set Home Here").lore("<gray>Saves where you stand.").build(), t -> {
                        this.viewer.closeInventory();
                        this.plugin.dialogs().text(this.viewer, Text.mm("<gold><b>New Home"), "Name", mine.isEmpty() ? "home" : "", 16, namex -> {
                            if (!namex.isBlank()) {
                                Essentials.this.setHome(this.viewer, namex);
                            }
                        });
                    });
                    this.fill(Material.GRAY_STAINED_GLASS_PANE);
                }
            })
            .open();
    }

    private void search(Player p, World w, int attempt) {
        if (p.isOnline()) {
            boolean end = w.getEnvironment() == Environment.THE_END;
            if (end && attempt == 0 && w.getWorldBorder().getSize() / 2.0 < 1150.0) {
                // the outer islands start 1000 blocks out: a smaller border leaves only the dragon's island
                err(p, "Random teleport in the End needs the End world border to reach at least 1150 blocks from the centre.");
                for (Player op : Bukkit.getOnlinePlayers()) {
                    if (op.hasPermission("vortexsmp.admin") && !op.equals(p)) {
                        msg(op, "<yellow>[SMP] <gray>The End world border (radius " + (int)(w.getWorldBorder().getSize() / 2.0) + ") is too small for /rtp there.");
                    }
                }
            } else if (attempt >= (end ? 40 : 16)) {
                // never a fallback spot (the End's entry platform sits next to the dragon island and its portal)
                err(p, end ? "Couldn't find an outer End island this time - try again." : "Couldn't find a safe spot this time - try again.");
            } else {
                int max = Math.max(50, Cfg.i(Opt.RTP_MAX));
                int min = Math.min(Cfg.i(Opt.RTP_MIN), max - 25);
                Location c = w.getSpawnLocation();
                if (end) {
                    // the main island is the dragon's (and its exit portal sends players home): the outer islands
                    // start about 1000 blocks out
                    c = new Location(w, 0.0, 64.0, 0.0);
                    min = 1100;
                    max = Math.max(3000, Math.min(6000, max));
                }

                ThreadLocalRandom r = ThreadLocalRandom.current();
                double ang = r.nextDouble(Math.PI * 2);
                double dist = min + r.nextDouble() * (max - min);
                int x = (int)Math.round(c.getX() + Math.cos(ang) * dist);
                int z = (int)Math.round(c.getZ() + Math.sin(ang) * dist);
                if (!w.getWorldBorder().isInside(new Location(w, x + 0.5, 64.0, z + 0.5))) {
                    this.search(p, w, attempt + 1);
                } else {
                    w.getChunkAtAsync(x >> 4, z >> 4, true)
                        .whenComplete(
                            (chunk, ex) -> Tasks.ensureSync(
                                () -> {
                                    if (ex != null) {
                                        if (p.isOnline()) {
                                            this.search(p, w, attempt + 1);
                                        }
                                    } else if (p.isOnline()) {
                                        Location safe = safeSpot(w, x, z);
                                        if (safe != null) {
                                            if (this.plugin.combatTag() != null && this.plugin.combatTag().tagged(p) && !p.hasPermission("vortexsmp.admin")) {
                                                err(p, "Random teleport cancelled - you are in combat.");
                                            } else {
                                                safe.setYaw(p.getLocation().getYaw());
                                                if (this.arrive(p, safe, "Random spot " + x + ", " + z)) {
                                                    this.rtpCooldowns.put(p.getUniqueId(), System.currentTimeMillis() + Cfg.i(Opt.RTP_COOLDOWN) * 1000L);
                                                    msg(
                                                        p,
                                                        "<green>✔ <gray>Teleported to <white>"
                                                            + x
                                                            + ", "
                                                            + safe.getBlockY()
                                                            + ", "
                                                            + z
                                                            + "</white> <dark_gray>("
                                                            + Fmt.num(Math.round(dist))
                                                            + " blocks from spawn)"
                                                    );
                                                }
                                            }
                                        } else {
                                            this.search(p, w, attempt + 1);
                                        }
                                    }
                                }
                            )
                        );
                }
            }
        }
    }

    private static Location safeSpot(World w, int x, int z) {
        if (w.getEnvironment() == Environment.NETHER) {
            for (int y = 32; y < 118; y++) {
                Block feet = w.getBlockAt(x, y, z);
                if (standable(feet)) {
                    return new Location(w, x + 0.5, y, z + 0.5);
                }
            }

            return null;
        } else {
            Block top = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block feet = top.getRelative(BlockFace.UP);
            if (w.getEnvironment() == Environment.THE_END) {
                // only on End stone of an island, and never next to a portal or gateway (they send players away)
                if (top.getY() < w.getMinHeight() + 5 || top.getType() != Material.END_STONE || !top.getRelative(BlockFace.DOWN).getType().isSolid()
                    || Math.abs(x) < 1000 && Math.abs(z) < 1000) {
                    return null;
                }

                for (int dx = -8; dx <= 8; dx++) {
                    for (int dy = -4; dy <= 4; dy++) {
                        for (int dz = -8; dz <= 8; dz++) {
                            Material m = feet.getRelative(dx, dy, dz).getType();
                            if (m == Material.END_PORTAL || m == Material.END_GATEWAY) {
                                return null;
                            }
                        }
                    }
                }
            }

            return standable(feet) ? new Location(w, x + 0.5, feet.getY(), z + 0.5) : null;
        }
    }

    private static boolean standable(Block feet) {
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);
        Material g = ground.getType();
        return feet.isPassable()
            && !feet.isLiquid()
            && head.isPassable()
            && !head.isLiquid()
            && g.isSolid()
            && feet.getType() != Material.FIRE
            && feet.getType() != Material.SOUL_FIRE
            && feet.getType() != Material.POWDER_SNOW
            && feet.getType() != Material.SWEET_BERRY_BUSH
            && g != Material.MAGMA_BLOCK
            && g != Material.CACTUS
            && g != Material.CAMPFIRE
            && g != Material.SOUL_CAMPFIRE
            && g != Material.POINTED_DRIPSTONE
            && g != Material.BEDROCK
            && !Tag.LEAVES.isTagged(g);
    }

    /** The exact name only (any case): /tpa Bob never reaches Bobby. Tab completion fills in full names. */
    private Player findPlayer(Player viewer, String name) {
        return Bukkit.getPlayerExact(name);
    }

    private boolean tpaAllowed(Player p) {
        if (!Cfg.bool(Opt.ESS_TPA)) {
            err(p, "Teleport requests are disabled on this server.");
            return false;
        } else if (!this.smp(p.getWorld())) {
            err(p, "Teleport requests only work in the SMP worlds.");
            return false;
        } else {
            return true;
        }
    }

    private void request(Player from, Player to, boolean here) {
        if (from.equals(to)) {
            err(from, "You can't send a request to yourself.");
        } else if (this.smp(to.getWorld()) && this.group(to.getWorld()).equals(this.group(from.getWorld()))) {
            if (this.tpaOff.contains(to.getUniqueId()) && !from.hasPermission("vortexsmp.staff")) {
                err(from, to.getName() + " doesn't accept teleport requests.");
            } else if (false) {
                err(from, to.getName() + " doesn't accept teleport requests from you.");
            } else {
                List<Essentials.Request> list = this.requests.computeIfAbsent(to.getUniqueId(), k -> new ArrayList<>());

                for (Essentials.Request r : list) {
                    if (r.from().equals(from.getUniqueId()) && System.currentTimeMillis() - r.created() < 5000L) {
                        err(from, "Slow down - you just sent a request to " + to.getName() + ".");
                        return;
                    }
                }

                list.removeIf(rx -> rx.from().equals(from.getUniqueId()));
                list.add(new Essentials.Request(from.getUniqueId(), to.getUniqueId(), here, System.currentTimeMillis()));
                int ttl = Cfg.i(Opt.TPA_EXPIRE);
                msg(
                    from,
                    "<gray>Request sent to <white>"
                        + Text.esc(to.getName())
                        + "</white>. <dark_gray>[<red><click:run_command:'/tpacancel'><hover:show_text:'<gray>Cancel your requests'>cancel</hover></click></red>]"
                );
                String n = Text.esc(from.getName());
                to.sendMessage(Text.mm(" "));
                to.sendMessage(
                    Text.mm(
                        here
                            ? "<gold>⚡ <white><b>" + n + "</b></white> <gray>wants you to teleport <gold>to them</gold>."
                            : "<gold>⚡ <white><b>" + n + "</b></white> <gray>wants to teleport <gold>to you</gold>."
                    )
                );
                to.sendMessage(
                    Text.mm(
                        "   <green><b><click:run_command:'/tpaccept "
                            + from.getName()
                            + "'><hover:show_text:'<green>Accept'>[✔ ACCEPT]</hover></click></b></green>   <red><b><click:run_command:'/tpdeny "
                            + from.getName()
                            + "'><hover:show_text:'<red>Deny'>[✖ DENY]</hover></click></b></red>   <dark_gray>expires in "
                            + ttl
                            + "s"
                    )
                );
                to.sendMessage(Text.mm(" "));
                Snd.play(to, "block.note_block.pling", 0.8F, 1.5F);
            }
        } else {
            err(from, to.getName() + " is not in " + Text.plain(Text.mm(this.modeLabel(from.getWorld()))) + ".");
        }
    }

    /** The request (newest, or from that player) without removing it; expired ones are dropped first. */
    private Essentials.Request peek(Player to, String fromName) {
        List<Essentials.Request> list = this.requests.get(to.getUniqueId());
        if (list == null) {
            return null;
        } else {
            long ttl = Cfg.i(Opt.TPA_EXPIRE) * 1000L;
            long now = System.currentTimeMillis();
            list.removeIf(r -> now - r.created() > ttl);
            Essentials.Request found = null;

            for (Essentials.Request r : list) {
                Player f = Bukkit.getPlayer(r.from());
                if (fromName == null || f != null && f.getName().equalsIgnoreCase(fromName)) {
                    found = r;
                }
            }

            if (list.isEmpty()) {
                this.requests.remove(to.getUniqueId());
            }

            return found;
        }
    }

    private void drop(Essentials.Request r) {
        List<Essentials.Request> list = this.requests.get(r.to());
        if (list != null) {
            list.remove(r);
            if (list.isEmpty()) {
                this.requests.remove(r.to());
            }
        }
    }

    private Essentials.Request take(Player to, String fromName) {
        Essentials.Request r = this.peek(to, fromName);
        if (r != null) {
            this.drop(r);
        }

        return r;
    }

    private void accept(Player to, String fromName) {
        Essentials.Request r = this.peek(to, fromName);
        Player from = r == null ? null : Bukkit.getPlayer(r.from());
        if (r != null && from == null) {
            this.drop(r);
            err(to, "That player is not online anymore.");
        } else if (r != null) {
            if (this.smp(from.getWorld()) && this.group(from.getWorld()).equals(this.group(to.getWorld()))) {
                Player traveller = r.here() ? to : from;
                Player dest = r.here() ? from : to;
                msg(to, "<green>✔ Accepted <white>" + Text.esc(from.getName()) + "</white>'s request.");
                msg(from, "<green>✔ <white>" + Text.esc(to.getName()) + "</white> accepted your request.");
                // the request stays until the teleport really happens: if it is cancelled it can be accepted again
                this.warmup(traveller, dest.getName(), () -> {
                    if (dest.isOnline() && this.smp(dest.getWorld()) && this.group(dest.getWorld()).equals(this.group(traveller.getWorld()))) {
                        Location at = dest.getLocation();
                        if (this.arrive(traveller, at, dest.getName())) {
                            this.drop(r);
                        }
                    } else {
                        this.drop(r);
                        err(traveller, "Teleport cancelled - " + dest.getName() + " left.");
                    }
                }, () -> {
                    if (to.isOnline() && this.peek(to, from.getName()) == r) {
                        Player other = traveller.equals(to) ? from : to;
                        if (other.isOnline()) {
                            msg(other, "<gray>The teleport of <white>" + Text.esc(traveller.getName()) + "</white> was cancelled.");
                        }

                        to.sendMessage(
                            Text.mm(
                                "<gray>The request is still open: <green><b><click:run_command:'/tpaccept "
                                    + from.getName()
                                    + "'><hover:show_text:'<green>Accept again'>[✔ ACCEPT AGAIN]</hover></click></b></green>"
                                    + (traveller.equals(from) ? " <dark_gray>(" + Text.esc(from.getName()) + " must stand still)" : "")
                            )
                        );
                    }
                });
            } else {
                err(to, from.getName() + " is not in your mode anymore.");
            }
        } else {
            err(to, "You have no teleport request" + (fromName == null ? "" : " from " + fromName) + ".");
        }
    }

    private void deny(Player to, String fromName) {
        Essentials.Request r = this.take(to, fromName);
        Player from = r == null ? null : Bukkit.getPlayer(r.from());
        if (r == null) {
            err(to, "You have no teleport request" + (fromName == null ? "" : " from " + fromName) + ".");
        } else {
            msg(to, "<gray>Request denied.");
            if (from != null) {
                msg(from, "<red>✖ <white>" + Text.esc(to.getName()) + "</white> denied your teleport request.");
            }
        }
    }

    private void warpCommand(Player p, String cmd, String[] args) {
        String mode = this.group(p.getWorld());
        Map<String, Essentials.Warp> map = this.warps.computeIfAbsent(mode, k -> new LinkedHashMap<>());
        switch (cmd) {
            case "warp":
                if (args.length == 0) {
                    this.warpsMenu(p);
                    return;
                }

                Essentials.Warp w = map.get(args[0].toLowerCase(Locale.ROOT));
                if (w == null) {
                    err(p, "No warp called '" + args[0] + "'." + (map.isEmpty() ? "" : " Warps: " + String.join(", ", map.keySet())));
                    return;
                }

                Location l = Locs.parse(w.location());
                if (l == null) {
                    err(p, "That warp's world is not loaded.");
                    return;
                }

                this.warmup(p, "warp " + w.name(), () -> this.arrive(p, l, "Warp " + w.name()));
                break;
            case "warps":
                this.warpsMenu(p);
                break;
            case "setwarp":
                if (!p.hasPermission("vortexsmp.warps.admin")) {
                    err(p, "You don't have permission.");
                    return;
                }

                if (args.length == 0 || !NAME.matcher(args[0]).matches()) {
                    err(p, "Usage: /setwarp <name> (letters, numbers, - and _). Icon = the item in your hand.");
                    return;
                }

                String name = args[0].toLowerCase(Locale.ROOT);
                Material hand = p.getInventory().getItemInMainHand().getType();
                Material icon = hand.isAir() ? Material.ENDER_PEARL : hand;
                map.put(name, new Essentials.Warp(name, Locs.serialize(p.getLocation()), icon));
                this.saveWarps();
                msg(p, "<green>✔ Warp <white>" + name + "</white> set in " + this.modeLabel(p.getWorld()) + "<green>.");
                break;
            default:
                if (!p.hasPermission("vortexsmp.warps.admin")) {
                    err(p, "You don't have permission.");
                    return;
                }

                if (args.length == 0 || map.remove(args[0].toLowerCase(Locale.ROOT)) == null) {
                    err(p, "Usage: /delwarp <name>");
                    return;
                }

                this.saveWarps();
                msg(p, "<gray>Warp <white>" + Text.esc(args[0].toLowerCase(Locale.ROOT)) + "</white> deleted.");
        }
    }

    public void warpsMenu(Player p) {
        Map<String, Essentials.Warp> map = this.warps.getOrDefault(this.group(p.getWorld()), Map.of());
        if (ClientVersion.supportsDialogs(p)) {
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();

            for (Essentials.Warp w : map.values()) {
                Location l = Locs.parse(w.location());
                String where = l == null ? "<red>world not loaded" : "<gray>" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
                tiles.add(
                    new LobbyDialogs.Tile(
                        new ItemStack(w.icon()),
                        Text.mm("<aqua><b>" + Text.esc(w.name())),
                        Text.mm(where),
                        List.of(Text.mm("<aqua><b>" + Text.esc(w.name())), Text.mm(where), Component.empty(), Text.mm("<green>▶ Click to warp")),
                        () -> {
                            p.closeDialog();
                            p.performCommand("warp " + w.name());
                        }
                    )
                );
            }

            LobbyDialogs ui = this.plugin.dialogs().lobby();
            ui.screen(
                p,
                LobbyDialogs.title("Warps", "Click a warp to go there"),
                tiles,
                List.of(),
                Text.mm(
                    "<gray>No warps here yet."
                        + (p.hasPermission("vortexsmp.warps.admin") ? " <gold>/setwarp <name></gold> <gray>with an item in your hand." : "")
                ),
                List.of(),
                1,
                ui.close(p)
            );
        } else {
            Component line = Text.mm("<gold>Warps: ");

            for (String name : map.keySet()) {
                line = line.append(Text.mm("<white><click:run_command:'/warp " + name + "'>[" + name + "]</click> "));
            }

            p.sendMessage(line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (sender instanceof Player p && args.length == 1) {
            String pre = command.getName().toLowerCase(Locale.ROOT);
            switch (pre) {
                case "home":
                case "delhome":
                case "sethome":
                    out.addAll(this.homesOf(p.getUniqueId(), this.group(this.here(p))).keySet());
                    break;
                case "warp":
                case "delwarp":
                    out.addAll(this.warps.getOrDefault(this.group(p.getWorld()), Map.of()).keySet());
                    break;
                case "tpa":
                case "tpahere":
                    // everyone in the same mode (overworld, nether and end), hidden and invisible players left out
                    for (Player o : Bukkit.getOnlinePlayers()) {
                        if (!o.equals(p)
                            && p.canSee(o)
                            && this.smp(o.getWorld())
                            && this.group(o.getWorld()).equals(this.group(p.getWorld()))
                            && p.canSee(o)) {
                            out.add(o.getName());
                        }
                    }
                    break;
                case "rtp":
                    out.addAll(List.of("overworld", "nether", "end"));
                    break;
                case "tpaccept":
                case "tpdeny":
                    for (Essentials.Request r : this.requests.getOrDefault(p.getUniqueId(), List.of())) {
                        Player f = Bukkit.getPlayer(r.from());
                        if (f != null) {
                            out.add(f.getName());
                        }
                    }
            }

            String typed = args[0].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(typed));
            return out;
        } else {
            return out;
        }
    }

    private record Request(UUID from, UUID to, boolean here, long created) {
    }

    private static final class Warmup {
        final Location from;
        int quarters;
        BukkitTask task;
        Runnable failed;

        Warmup(Location from, int quarters) {
            this.from = from;
            this.quarters = quarters;
        }
    }

    private record Warp(String name, String location, Material icon) {
    }
}
