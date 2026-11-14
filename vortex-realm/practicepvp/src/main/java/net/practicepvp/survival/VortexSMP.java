package net.practicepvp.survival;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.practicepvp.PracticePlugin;
import net.practicepvp.survival.admin.SmpAdmin;
import net.practicepvp.survival.config.Cfg;
import net.practicepvp.survival.data.OldImport;
import net.practicepvp.survival.data.SmpImport;
import net.practicepvp.survival.dialog.Dialogs;
import net.practicepvp.survival.gui.MenuListener;
import net.practicepvp.survival.smp.CombatTag;
import net.practicepvp.survival.smp.Essentials;
import net.practicepvp.survival.smp.ModeManager;
import net.practicepvp.survival.smp.Realm;
import net.practicepvp.survival.smp.Sidebar;
import net.practicepvp.survival.smp.Utils;
import net.practicepvp.survival.util.Items;
import net.practicepvp.survival.util.Tasks;
import net.practicepvp.survival.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

/**
 * The SMP part of the plugin (it was the separate VortexSMP plugin): homes, tpa, rtp, warps, back, combat tag, the
 * SMP sidebar, /craft /ec /hat /trash, drills, /trade and /market - all inside the SMP worlds of worlds.yml. The
 * worlds, the selector and every inventory (each world group its own, saved and backed up) are the main plugin's.
 * Its files are in plugins/&lt;this plugin&gt;/smp/ (config.yml, homes, warps, market...).
 */
public final class VortexSMP implements CommandExecutor, TabCompleter {
    private static VortexSMP instance;
    private final PracticePlugin host;
    private final File folder;
    private final File configFile;
    private YamlConfiguration config;
    private net.practicepvp.survival.smp.KeepOnlineBot keepOnline;
    private final VortexSMP.Worlds worlds = new VortexSMP.Worlds();
    private final Realm realm;
    private ModeManager modes;
    private Dialogs dialogs;
    private Essentials essentials;
    private CombatTag combatTag;
    private SmpAdmin smpAdmin;
    private Sidebar sidebar;
    private OldImport oldImport;
    private SmpImport smpImport;
    private net.practicepvp.survival.data.DiscordLink discord;
    private net.practicepvp.survival.trade.TradeManager trades;
    private net.practicepvp.survival.trade.Market market;

    public VortexSMP(PracticePlugin host) {
        this.host = host;
        this.folder = new File(host.getDataFolder(), "smp");
        this.configFile = new File(this.folder, "config.yml");
        this.realm = new Realm(this);
    }

    public static VortexSMP get() {
        return instance;
    }

    /** The main plugin (for the scheduler, events, metadata and commands). */
    public PracticePlugin host() {
        return this.host;
    }

    public Logger getLogger() {
        return this.host.getLogger();
    }

    public File getDataFolder() {
        return this.folder;
    }

    public String version() {
        return this.host.getPluginMeta().getVersion();
    }

    public YamlConfiguration getConfig() {
        if (this.config == null) {
            this.reloadConfig();
        }

        return this.config;
    }

    public void reloadConfig() {
        this.config = YamlConfiguration.loadConfiguration(this.configFile);
    }

    public void saveConfig() {
        try {
            this.folder.mkdirs();
            this.getConfig().save(this.configFile);
        } catch (IOException e) {
            this.getLogger().log(Level.WARNING, "Could not save smp/config.yml", e);
        }
    }

    public void enable() {
        instance = this;
        Tasks.init(this.host);
        Items.init();
        // the old VortexSMP plugin's files come over first (homes, warps, market, drills, Discord links)
        this.smpImport = new SmpImport(this);
        this.smpImport.copySmpPluginFolder();
        Cfg.load(this);
        PluginManager pm = Bukkit.getPluginManager();
        this.modes = new ModeManager(this);
        this.dialogs = new Dialogs(this);
        pm.registerEvents(new MenuListener(this), this.host);
        this.oldImport = new OldImport(this);
        this.oldImport.prepare();
        this.smpImport.startup();
        this.essentials = new Essentials(this, this.modes);
        this.essentials.load();
        pm.registerEvents(this.essentials, this.host);
        this.bind(this.essentials, "spawn", "sethome", "home", "delhome", "homes", "back", "rtp", "tpa", "tpahere", "tpaccept", "tpdeny", "tpacancel", "tptoggle", "warp", "warps", "setwarp", "delwarp");
        Tasks.timer(20L, 20L, this.essentials::tick);
        this.combatTag = new CombatTag(this, this.modes);
        pm.registerEvents(this.combatTag, this.host);
        Tasks.timer(20L, 20L, this.combatTag::tick);
        this.smpAdmin = new SmpAdmin(this);
        this.smpAdmin.load();
        pm.registerEvents(this.smpAdmin, this.host);
        this.bind(this.smpAdmin, "smpadmin");
        Tasks.timer(6000L, 6000L, this.smpAdmin::save);
        Utils utils = new Utils(this);
        this.bind(utils, "craft", "enderchest", "trash", "hat", "nightvision", "near", "setspawn");
        this.sidebar = new Sidebar(this);
        pm.registerEvents(this.sidebar, this.host);
        Tasks.timer(40L, 10L, this.sidebar::tick);
        pm.registerEvents(this.oldImport, this.host);
        this.bind(this.oldImport, "oldstuff");
        this.discord = new net.practicepvp.survival.data.DiscordLink(this);
        this.discord.start();
        this.bind(this.discord, "link");
        pm.registerEvents(new net.practicepvp.survival.smp.SameServer(this), this.host);
        net.practicepvp.survival.smp.Drills drills = new net.practicepvp.survival.smp.Drills(this);
        pm.registerEvents(drills, this.host);
        this.bind(drills, "drill");
        this.trades = new net.practicepvp.survival.trade.TradeManager(this);
        pm.registerEvents(this.trades, this.host);
        this.bind(this.trades, "trade");
        this.market = new net.practicepvp.survival.trade.Market(this);
        pm.registerEvents(this.market, this.host);
        this.bind(this.market, "market");
        this.keepOnline = new net.practicepvp.survival.smp.KeepOnlineBot(this);
        pm.registerEvents(this.keepOnline, this.host);
        Tasks.timer(100L, 1200L, this.keepOnline::refresh);
        this.bind(this, "smp-admin-internal");
        World smp = this.realm.smpWorld();
        this.getLogger().info("SMP ready: homes, tpa, rtp, warps, back, combat tag, trade, market"
            + (smp == null ? " - no SMP world yet (set a world to SURVIVAL in /pvpadmin -> World Manager)." : " in " + smp.getName() + "."));
    }

    /** Zips the SMP files (homes, warps, drills, market, Discord links...) into smp/backups/. */
    public void backupData() throws IOException {
        if (this.smpAdmin != null) {
            this.smpAdmin.save();
        }

        if (this.market != null) {
            this.market.shutdown();
        }

        File dir = new File(this.getDataFolder(), "backups");
        dir.mkdirs();
        java.nio.file.Path root = this.getDataFolder().toPath();
        File out = new File(dir, "smp-" + new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date()) + ".zip");
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
    }

    public void disable() {
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
            this.getLogger().log(Level.SEVERE, "Saving the SMP on shutdown failed", (Throwable)var2);
        }
    }

    private void bind(Object handler, String... names) {
        for (String n : names) {
            PluginCommand c = this.host.getCommand(n);
            if (c == null) {
                if (!n.equals("smp-admin-internal")) {
                    this.getLogger().warning("Command /" + n + " is missing from plugin.yml");
                }

                continue;
            }

            c.setExecutor((CommandExecutor)handler);
            if (handler instanceof TabCompleter t) {
                c.setTabCompleter(t);
            }
        }
    }

    /** /pvpadmin smp [status|reload|import|bot] */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "reload":
                Cfg.load(this);
                this.essentials.load();
                sender.sendMessage(Text.mm("<green>SMP settings reloaded (smp/config.yml)."));
                break;
            case "import":
                this.smpImport.command(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
                break;
            case "check":
                this.oldImport.command(sender, new String[]{"check"});
                break;
            case "bot": {
                String what = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "status";
                if (what.equals("on") || what.equals("off")) {
                    Cfg.set(net.practicepvp.survival.config.Opt.KEEPALIVE_BOT, what.equals("on"));
                    this.keepOnline.refresh();
                }

                sender.sendMessage(Text.mm("<gray>Keep-online bot: <white>" + Text.esc(this.keepOnline.status())));
                sender.sendMessage(Text.mm("<gray>/pvpadmin smp bot on <dark_gray>| <gray>off <dark_gray>• <gray>only use it if your host allows it"));
                break;
            }
            default:
                World smp = this.realm.smpWorld();
                sender.sendMessage(Text.mm("<green><b>SMP</b> <gray>(part of this plugin, settings in smp/config.yml)"));
                sender.sendMessage(Text.mm(" <gray>SMP world: <white>" + (smp == null ? "<red>none - set a world to SURVIVAL in /pvpadmin → World Manager" : Text.esc(smp.getName()))));
                sender.sendMessage(Text.mm(" <gray>Import: <white>" + Text.esc(this.smpImport.status())));
                sender.sendMessage(Text.mm(" <gray>Discord: <white>" + Text.esc(this.discord.status())));
                sender.sendMessage(Text.mm(" <gray>Keep-online bot: <white>" + Text.esc(this.keepOnline.status())));
                sender.sendMessage(Text.mm(" <gray>/pvpadmin smp reload <dark_gray>• <gray>import [preview|run] <dark_gray>• <gray>bot on|off <dark_gray>• <gray>/pvpadmin invbackup <player>"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("status", "reload", "import", "check", "bot").stream().filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            return List.of("preview", "run").stream().filter(s -> s.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
        }

        return List.of();
    }

    /** Players outside the SMP worlds (for the SMP sidebar). */
    public int lobbyPlayers() {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            n += this.realm.smp(p.getWorld()) || net.practicepvp.survival.smp.KeepOnlineBot.isBot(p) ? 0 : 1;
        }

        return n;
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

    public net.practicepvp.survival.data.DiscordLink discord() {
        return this.discord;
    }

    public OldImport oldImport() {
        return this.oldImport;
    }

    public SmpImport smpImport() {
        return this.smpImport;
    }

    public VortexSMP.Worlds worlds() {
        return this.worlds;
    }

    public Realm realm() {
        return this.realm;
    }

    /** The practice worlds (arenas, the practice lobby) are left alone by the SMP. */
    public final class Worlds {
        public boolean managed(World w) {
            return VortexSMP.this.realm.managed(w);
        }

        public boolean isMarked(Player p) {
            return VortexSMP.this.realm.marked(p);
        }
    }
}
