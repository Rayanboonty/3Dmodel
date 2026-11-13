package net.vortexsmp;

import java.util.List;
import java.util.logging.Level;
import net.vortexsmp.admin.SmpAdmin;
import net.vortexsmp.config.Cfg;
import net.vortexsmp.data.OldImport;
import net.vortexsmp.dialog.Dialogs;
import net.vortexsmp.gui.MenuListener;
import net.vortexsmp.smp.CombatTag;
import net.vortexsmp.smp.Essentials;
import net.vortexsmp.smp.HubLink;
import net.vortexsmp.smp.ModeManager;
import net.vortexsmp.smp.Sidebar;
import net.vortexsmp.smp.Utils;
import net.vortexsmp.util.Items;
import net.vortexsmp.util.Tasks;
import net.vortexsmp.util.Text;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The survival server of the Vortex Realm network. It keeps nothing of the practice plugin: the server's own
 * player data is the only inventory, so nothing is ever swapped or cleared when players come and go.
 */
public final class VortexSMP extends JavaPlugin implements CommandExecutor, TabCompleter {
    private net.vortexsmp.update.AutoUpdater updater;
    private net.vortexsmp.smp.KeepOnlineBot keepOnline;
    private static VortexSMP instance;
    private final VortexSMP.Worlds worlds = new VortexSMP.Worlds();
    private ModeManager modes;
    private Dialogs dialogs;
    private Essentials essentials;
    private CombatTag combatTag;
    private SmpAdmin smpAdmin;
    private HubLink hubLink;
    private Sidebar sidebar;
    private OldImport oldImport;
    private net.vortexsmp.data.DiscordLink discord;
    private net.vortexsmp.trade.TradeManager trades;
    private net.vortexsmp.trade.Market market;

    public static VortexSMP get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        Tasks.init(this);
        Items.init(this);
        Cfg.load(this);
        PluginManager pm = this.getServer().getPluginManager();
        if (pm.getPlugin("PracticePvP") != null || pm.getPlugin("VortexRealm") != null || pm.getPlugin("VortexPvP") != null) {
            this.getLogger().warning("PracticePvP/VortexRealm is installed on this server too. VortexSMP is made for its own SMP server: "
                + "both plugins have /home, /tpa, /rtp... Remove one of them.");
        }

        this.modes = new ModeManager(this);
        this.dialogs = new Dialogs(this);
        pm.registerEvents(new MenuListener(this), this);
        this.oldImport = new OldImport(this);
        this.oldImport.prepare();
        this.essentials = new Essentials(this, this.modes);
        this.essentials.load();
        pm.registerEvents(this.essentials, this);
        this.bind(this.essentials, "spawn", "sethome", "home", "delhome", "homes", "back", "rtp", "tpa", "tpahere", "tpaccept", "tpdeny", "tpacancel", "tptoggle", "warp", "warps", "setwarp", "delwarp");
        Tasks.timer(20L, 20L, this.essentials::tick);
        this.combatTag = new CombatTag(this, this.modes);
        pm.registerEvents(this.combatTag, this);
        Tasks.timer(20L, 20L, this.combatTag::tick);
        this.smpAdmin = new SmpAdmin(this);
        this.smpAdmin.load();
        pm.registerEvents(this.smpAdmin, this);
        this.bind(this.smpAdmin, "smpadmin");
        Tasks.timer(6000L, 6000L, this.smpAdmin::save);
        Utils utils = new Utils(this);
        this.bind(utils, "craft", "enderchest", "trash", "hat", "nightvision", "near", "playtime", "setspawn");
        this.hubLink = new HubLink(this);
        this.hubLink.start();
        pm.registerEvents(this.hubLink, this);
        this.bind(this.hubLink, "hub");
        this.sidebar = new Sidebar(this);
        pm.registerEvents(this.sidebar, this);
        Tasks.timer(40L, 10L, this.sidebar::tick);
        pm.registerEvents(this.oldImport, this);
        this.bind(this.oldImport, "oldstuff");
        this.discord = new net.vortexsmp.data.DiscordLink(this);
        this.discord.start();
        this.bind(this.discord, "link");
        net.vortexsmp.smp.Drills drills = new net.vortexsmp.smp.Drills(this);
        pm.registerEvents(drills, this);
        this.bind(drills, "drill");
        this.trades = new net.vortexsmp.trade.TradeManager(this);
        pm.registerEvents(this.trades, this);
        this.bind(this.trades, "trade");
        this.market = new net.vortexsmp.trade.Market(this);
        pm.registerEvents(this.market, this);
        this.bind(this.market, "market");
        this.updater = new net.vortexsmp.update.AutoUpdater(this, new net.vortexsmp.update.AutoUpdater.Host() {
            @Override
            public boolean enabled() {
                return Cfg.bool(net.vortexsmp.config.Opt.UPDATES);
            }

            @Override
            public List<String> sources() {
                return java.util.Arrays.asList(Cfg.s(net.vortexsmp.config.Opt.UPDATE_SOURCES).split("\\s*,\\s*"));
            }

            @Override
            public int checkMinutes() {
                return Cfg.i(net.vortexsmp.config.Opt.UPDATE_CHECK);
            }

            @Override
            public String restartPolicy() {
                return Cfg.s(net.vortexsmp.config.Opt.UPDATE_RESTART);
            }

            @Override
            public int restartHour() {
                return Cfg.i(net.vortexsmp.config.Opt.UPDATE_HOUR);
            }

            @Override
            public boolean busy() {
                return false;
            }

            @Override
            public void backup() throws Exception {
                VortexSMP.this.backupData();
            }

            @Override
            public boolean isAdmin(CommandSender s) {
                return s.hasPermission("vortexsmp.admin") || s.isOp();
            }
        });
        this.updater.start();
        this.keepOnline = new net.vortexsmp.smp.KeepOnlineBot(this);
        pm.registerEvents(this.keepOnline, this);
        // after the worlds are ready; checked again every minute so it comes back if it ever drops
        Tasks.timer(100L, 1200L, this.keepOnline::refresh);
        this.bind(this, "vortexsmp");
        this.getLogger().info("VortexSMP " + this.getPluginMeta().getVersion() + " ready: homes, tpa, rtp, warps, back, combat tag"
            + (Cfg.s(net.vortexsmp.config.Opt.HUB_HOST).isBlank() ? " (set network.hub.host in config.yml for /hub)." : ", /hub -> " + Cfg.s(net.vortexsmp.config.Opt.HUB_HOST) + "."));
    }

    /** Zips this plugin's folder (homes, warps, drills, market, Discord links...) into backups/ before an update. */
    public void backupData() throws java.io.IOException {
        if (this.smpAdmin != null) {
            this.smpAdmin.save();
        }

        if (this.market != null) {
            this.market.shutdown();
        }

        java.io.File dir = new java.io.File(this.getDataFolder(), "backups");
        dir.mkdirs();
        java.nio.file.Path root = this.getDataFolder().toPath();
        java.io.File out = new java.io.File(dir, "update-" + new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date()) + ".zip");
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(out.toPath()));
             java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(root)) {
            for (java.nio.file.Path f : (Iterable<java.nio.file.Path>)files::iterator) {
                if (java.nio.file.Files.isRegularFile(f) && !f.startsWith(dir.toPath())) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(root.relativize(f).toString().replace('\\', '/')));
                    java.nio.file.Files.copy(f, zip);
                    zip.closeEntry();
                }
            }
        }

        this.getLogger().info("Backup before the update: " + out.getName());
    }

    @Override
    public void onDisable() {
        if (this.updater != null) {
            this.updater.stop();
        }

        if (this.keepOnline != null) {
            this.keepOnline.despawn();
        }

        try {
            if (this.smpAdmin != null) {
                this.smpAdmin.save();
            }

            if (this.oldImport != null) {
                this.oldImport.save();
            }

            if (this.hubLink != null) {
                this.hubLink.stop();
            }

            if (this.trades != null) {
                this.trades.shutdown();
            }

            if (this.market != null) {
                this.market.shutdown();
            }

            if (this.discord != null) {
                this.discord.stop();
            }
        } catch (RuntimeException var2) {
            this.getLogger().log(Level.SEVERE, "Saving on shutdown failed", (Throwable)var2);
        }
    }

    private void bind(Object handler, String... names) {
        for (String n : names) {
            PluginCommand c = this.getCommand(n);
            if (c == null) {
                this.getLogger().warning("Command /" + n + " is missing from plugin.yml");
                continue;
            }

            c.setExecutor((CommandExecutor)handler);
            if (handler instanceof TabCompleter t) {
                c.setTabCompleter(t);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "reload":
                Cfg.load(this);
                this.essentials.load();
                sender.sendMessage(Text.mm("<green>VortexSMP reloaded."));
                break;
            case "import":
                this.oldImport.command(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
                break;
            case "migrate":
                this.oldImport.migrateGuide(sender);
                break;
            case "bot": {
                String what = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "status";
                if (what.equals("on") || what.equals("off")) {
                    Cfg.set(net.vortexsmp.config.Opt.KEEPALIVE_BOT, what.equals("on"));
                    this.keepOnline.refresh();
                }

                sender.sendMessage(Text.mm("<gray>Keep-online bot: <white>" + Text.esc(this.keepOnline.status())));
                sender.sendMessage(Text.mm("<gray>/vortexsmp bot on <dark_gray>| <gray>off <dark_gray>• <gray>only use it if your host allows it"));
                break;
            }
            case "update": {
                String what = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "status";
                if (what.equals("check")) {
                    sender.sendMessage(Text.mm("<gray>Checking for a new version..."));
                    this.updater.check(sender);
                } else if (what.equals("now")) {
                    this.updater.restartNow(sender);
                } else if (what.equals("on") || what.equals("off")) {
                    Cfg.set(net.vortexsmp.config.Opt.UPDATES, what.equals("on"));
                    if (what.equals("on")) {
                        this.updater.start();
                    } else {
                        this.updater.stop();
                    }

                    sender.sendMessage(Text.mm("<gray>Auto-update is now " + (what.equals("on") ? "<green>on" : "<red>off") + "<gray>."));
                } else {
                    for (String line : this.updater.status()) {
                        sender.sendMessage(Text.mm(line));
                    }

                    sender.sendMessage(Text.mm("<gray>/vortexsmp update check <dark_gray>| <gray>now <dark_gray>| <gray>on <dark_gray>| <gray>off"));
                }
                break;
            }
            default:
                sender.sendMessage(Text.mm("<gold><b>VortexSMP</b> <gray>" + this.getPluginMeta().getVersion()));
                sender.sendMessage(Text.mm(" <gray>/hub → <white>" + (Cfg.s(net.vortexsmp.config.Opt.HUB_HOST).isBlank() ? "<red>not set (network.hub.host)" : Text.esc(Cfg.s(net.vortexsmp.config.Opt.HUB_HOST) + ":" + Cfg.i(net.vortexsmp.config.Opt.HUB_PORT))) + " <dark_gray>(" + Cfg.s(net.vortexsmp.config.Opt.NETWORK_MODE) + ")"));
                sender.sendMessage(Text.mm(" <gray>Import: <white>" + this.oldImport.status()));
                sender.sendMessage(Text.mm(" <gray>Discord: <white>" + Text.esc(this.discord.status())));
                sender.sendMessage(Text.mm(" <gray>Keep-online bot: <white>" + Text.esc(this.keepOnline.status())));
                sender.sendMessage(Text.mm(" <gray>Update: <white>" + (this.updater.staged() == null ? "up to date" : this.updater.staged() + " ready, installs on the next restart")));
                sender.sendMessage(Text.mm(" <gray>/vortexsmp reload <dark_gray>• <gray>/vortexsmp migrate <dark_gray>• <gray>/vortexsmp import [preview|run] <dark_gray>• <gray>/vortexsmp update"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("status", "reload", "import", "migrate", "update", "bot").stream().filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            return List.of("preview", "run").stream().filter(s -> s.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
        }

        return List.of();
    }

    public ModeManager modes() {
        return this.modes;
    }

    public Dialogs dialogs() {
        return this.dialogs;
    }

    public Essentials essentials() {
        return this.essentials;
    }

    public CombatTag combatTag() {
        return this.combatTag;
    }

    public HubLink hubLink() {
        return this.hubLink;
    }

    public net.vortexsmp.data.DiscordLink discord() {
        return this.discord;
    }

    public OldImport oldImport() {
        return this.oldImport;
    }

    public VortexSMP.Worlds worlds() {
        return this.worlds;
    }

    /** No PvP arenas here: every world is survival. */
    public static final class Worlds {
        public boolean managed(World w) {
            return false;
        }

        public boolean isMarked(Player p) {
            return false;
        }
    }
}
