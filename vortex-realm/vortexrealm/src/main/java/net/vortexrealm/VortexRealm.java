package net.vortexrealm;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.vortexrealm.ai.AiAgent;
import net.vortexrealm.dialog.DuelDialogs;
import net.vortexrealm.dialog.SettingsDialogs;
import net.vortexrealm.hook.PingEqualizer;
import net.vortexrealm.match.SpectatorFeed;
import net.vortexrealm.social.Regions;
import net.vortexrealm.social.SocialManager;
import net.vortexrealm.social.TabColumns;
import net.vortexrealm.ai.AiCommand;
import net.vortexrealm.arena.Arena;
import net.vortexrealm.arena.ArenaEditor;
import net.vortexrealm.arena.ArenaManager;
import net.vortexrealm.arena.ArenaTheme;
import net.vortexrealm.arena.BlockJobs;
import net.vortexrealm.arena.VanillaTerrain;
import net.vortexrealm.board.BoardManager;
import net.vortexrealm.board.HologramManager;
import net.vortexrealm.board.SmpBoard;
import net.vortexrealm.command.AdminCommands;
import net.vortexrealm.command.PlayerCommands;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Msg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.data.ConfigBackup;
import net.vortexrealm.data.DataVault;
import net.vortexrealm.data.Upgrader;
import net.vortexrealm.data.Database;
import net.vortexrealm.data.ProfileManager;
import net.vortexrealm.data.RankBackup;
import net.vortexrealm.deco.DecoManager;
import net.vortexrealm.deco.Holograms;
import net.vortexrealm.deco.ImageWalls;
import net.vortexrealm.dialog.Dialogs;
import net.vortexrealm.duel.DuelManager;
import net.vortexrealm.event.EventManager;
import net.vortexrealm.extra.ChatFilter;
import net.vortexrealm.extra.ChatManager;
import net.vortexrealm.extra.Extras;
import net.vortexrealm.ffa.FfaArena;
import net.vortexrealm.ffa.FfaManager;
import net.vortexrealm.fx.Fx;
import net.vortexrealm.fx.StatsHologram;
import net.vortexrealm.fx.Toasts;
import net.vortexrealm.fx.TotemPop;
import net.vortexrealm.fx.TrailManager;
import net.vortexrealm.fx.XpOrbText;
import net.vortexrealm.gui.MenuListener;
import net.vortexrealm.hook.AntiCheatHook;
import net.vortexrealm.hook.ClientGuard;
import net.vortexrealm.hook.PlaceholderHook;
import net.vortexrealm.hook.Sentinel;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.kit.KitManager;
import net.vortexrealm.listener.ArenaAuthorityListener;
import net.vortexrealm.listener.ArenaListener;
import net.vortexrealm.listener.CombatListener;
import net.vortexrealm.listener.CommandGuardListener;
import net.vortexrealm.listener.ConnectionListener;
import net.vortexrealm.listener.LobbyListener;
import net.vortexrealm.lobby.LobbyManager;
import net.vortexrealm.match.MapModel;
import net.vortexrealm.match.CageListener;
import net.vortexrealm.match.MatchManager;
import net.vortexrealm.match.Penalties;
import net.vortexrealm.match.SnapshotManager;
import net.vortexrealm.npc.NpcManager;
import net.vortexrealm.pack.ResourcePacks;
import net.vortexrealm.party.PartyManager;
import net.vortexrealm.queue.QueueManager;
import net.vortexrealm.rank.LeaderboardManager;
import net.vortexrealm.rank.RankManager;
import net.vortexrealm.session.PlayerReset;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Session;
import net.vortexrealm.session.SessionManager;
import net.vortexrealm.smp.Hub;
import net.vortexrealm.smp.ModeManager;
import net.vortexrealm.smp.ModeRules;
import net.vortexrealm.smp.ServerLink;
import net.vortexrealm.smp.ServerSelector;
import net.vortexrealm.social.Countries;
import net.vortexrealm.social.LinksDialog;
import net.vortexrealm.social.Ranks;
import net.vortexrealm.social.TabManager;
import net.vortexrealm.social.Welcome;
import net.vortexrealm.staff.HackerPool;
import net.vortexrealm.staff.Reports;
import net.vortexrealm.staff.ScreenShare;
import net.vortexrealm.staff.StaffManager;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.web.SamePortHttp;
import net.vortexrealm.web.WebServer;
import net.vortexrealm.web.WebSite;
import net.vortexrealm.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class VortexRealm extends JavaPlugin {
    private static VortexRealm instance;
    private Database db;
    private ProfileManager profiles;
    private SessionManager sessions;
    private KitManager kits;
    private ArenaManager arenas;
    private BlockJobs jobs;
    private VanillaTerrain vanilla;
    private WorldManager worlds;
    private LobbyManager lobby;
    private QueueManager queues;
    private MatchManager matches;
    private RankManager ranks;
    private LeaderboardManager leaderboards;
    private FfaManager ffa;
    private PartyManager parties;
    private DuelManager duels;
    private BoardManager boards;
    private HologramManager holograms;
    private Dialogs dialogs;
    private SnapshotManager snapshots;
    private TrailManager trails;
    private AntiCheatHook antiCheat;
    private CombatListener combat;
    private StaffManager staff;
    private Extras extras;
    private ModeManager modes;
    private ModeRules modeRules;
    private NpcManager npcs;
    private ServerSelector serverSelector;
    private ServerLink serverLink;
    private Hub hub;
    private Penalties penalties;
    private ArenaEditor arenaEditor;
    private ChatManager chat;
    private ChatFilter chatFilter;
    private net.vortexrealm.cosmetics.Cosmetics cosmetics;
    private net.vortexrealm.bot.BotManager bots;
    private net.vortexrealm.academy.Academy academy;
    private net.vortexrealm.bot.BotHall botHall;
    private RankBackup rankBackup;
    private DataVault vault;
    private Upgrader upgrader;
    private HackerPool hackerPool;
    private Reports reports;
    private Ranks ranksSocial;
    private Countries countries;
    private TabManager tab;
    private Welcome welcome;
    private SmpBoard smpBoard;
    private StatsHologram statsHologram;
    private EventManager events;
    private WebServer web;
    private SamePortHttp sameHttp;
    private ScreenShare screenShare;
    private AiAgent ai;
    private WebSite webSite;
    private DecoManager decos;
    private Holograms textHolograms;
    private ConfigBackup configBackup;
    private ResourcePacks packs;
    private SocialManager social;
    private SettingsDialogs settingsDialogs;
    private DuelDialogs duelDialogs;
    private Regions regions;
    private net.vortexrealm.update.AutoUpdater updater;
    private net.vortexrealm.social.NetworkSync netSync;
    private net.vortexrealm.lobby.Season season;
    private PingEqualizer pingEqualizer;
    private TabColumns tabColumns;
    private volatile boolean packRunning;
    /** Set once the arena snapshots have loaded (they load in the background at start-up). */
    private volatile boolean arenasReady;
    private volatile int packDone;
    /** The arena the pack is making right now, and the ones still waiting their turn. */
    private volatile String packCurrent;
    private final java.util.Set<String> packQueued = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile int packTotal;
    private SpectatorFeed spectatorFeed;
    private boolean enabledOk;
    private final Map<String, String> failed = new LinkedHashMap<>();

    public static VortexRealm get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();
        if (!this.getDataFolder().exists() && !this.getDataFolder().mkdirs()) {
            this.getLogger().severe("Cannot create the plugin folder.");
        }

        if (this.getServer().getPluginManager().getPlugin("PracticePvP") != null) {
            this.getLogger()
                .severe(
                    "PracticePvP is installed too. VortexRealm replaces it: remove PracticePvP-*.jar from plugins/ and restart. Your PracticePvP data is moved over automatically on the first start. VortexRealm stays off until then."
                );
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        new VortexMigration(this).autoMigrate();
        this.vault = new DataVault(this);
        this.vault.beforeLoad();
        // an old plugin folder dropped in import/ (VortexRealm, VortexRealm, VortexPvP) is brought back before anything loads
        new net.vortexrealm.data.OldDataImport(this).runIfPresent();
        Tasks.init(this);
        Items.init(this);
        PlayerReset.init(this);
        Fx.init(this);
        TotemPop.init(this);
        Cfg.load(this);
        Msg.load(this);
        this.db = new Database(this);

        try {
            this.db.open();
        } catch (Exception var10) {
            this.getLogger().log(Level.SEVERE, "Database could not be opened - disabling VortexRealm.", (Throwable)var10);
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.sessions = new SessionManager();
        this.profiles = new ProfileManager(this, this.db);
        this.kits = new KitManager(this);
        this.kits.load();

        try {
            Toasts.init(this, this.kits.all());
        } catch (Throwable var9) {
            this.getLogger().log(Level.WARNING, "Advancement toasts unavailable", var9);
        }

        VanillaTerrain.cleanLeftovers();
        this.worlds = new WorldManager(this);
        this.worlds.init();
        // tags left by the plugin used before (bot hall, image walls, spawners, hub items...) are taken over as things load
        net.vortexrealm.util.NamespaceBridge bridge = new net.vortexrealm.util.NamespaceBridge(this);
        this.getServer().getPluginManager().registerEvents(bridge, this);
        bridge.scanLoaded();
        this.jobs = new BlockJobs(this);
        this.jobs.start();
        this.vanilla = new VanillaTerrain(this);
        this.arenas = new ArenaManager(this);
        this.arenas.load();
        this.ffa = new FfaManager(this);
        this.ffa.load();
        this.lobby = new LobbyManager(this);
        this.lobby.load();
        this.queues = new QueueManager(this);
        this.matches = new MatchManager(this);
        this.ranks = new RankManager(this);
        this.leaderboards = new LeaderboardManager(this);
        this.parties = new PartyManager(this);
        this.duels = new DuelManager(this);
        this.boards = new BoardManager(this);
        this.holograms = new HologramManager(this);
        this.holograms.load();
        this.dialogs = new Dialogs(this);
        this.snapshots = new SnapshotManager();
        this.trails = new TrailManager(this);
        this.antiCheat = new AntiCheatHook(this);
        this.antiCheat.init();
        this.combat = new CombatListener(this);
        this.staff = new StaffManager(this);
        this.staff.load();
        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(this.staff, this);
        pm.registerEvents(this.profiles, this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(new LobbyListener(this), this);
        pm.registerEvents(this.combat, this);
        pm.registerEvents(new ArenaListener(this), this);
        pm.registerEvents(new ArenaAuthorityListener(this), this);
        pm.registerEvents(new CommandGuardListener(this), this);
        PlayerCommands playerCommands = new PlayerCommands(this);

        for (String c : List.of(
            "lobby",
            "leave",
            "leavepvp",
            "queue",
            "ranked",
            "unranked",
            "duel",
            "party",
            "partychat",
            "spectate",
            "leaderboard",
            "stats",
            "settings",
            "kiteditor",
            "ffa",
            "pvpinv",
            "rematch",
            "rankup",
            "ping",
            "challenge"
        )) {
            this.bind(c, playerCommands);
        }

        AdminCommands adminCommands = new AdminCommands(this);
        this.bind("pvpadmin", adminCommands);
        this.bind("vortex", new VortexMigration(this));
        this.bind("kits", adminCommands);
        this.bind("staff", this.staff);
        this.bind("staffchat", this.staff);
        this.module("extras", () -> {
            this.extras = new Extras(this);
            this.extras.load();
            pm.registerEvents(this.extras, this);

            for (String c : List.of("playtime", "links")) {
                this.bind(c, this.extras);
            }

            Tasks.timer(400L, 400L, this.extras::tick);
        });
        this.module("hub / selector / server link", () -> {
            this.modes = new ModeManager(this);
            this.modes.load();
            this.modeRules = new ModeRules(this, this.modes);
            pm.registerEvents(this.modeRules, this);
            this.hub = new Hub(this, this.modes);
            this.hub.init();
            pm.registerEvents(this.hub, this);
            this.serverSelector = new ServerSelector(this, this.modes);
            this.serverSelector.load();
            this.serverLink = new ServerLink(this);
            this.serverLink.start();

            for (String c : List.of("worlds", "practice", "hub", "smp")) {
                this.bind(c, this.modeRules);
            }
        });
        this.module("penalties", () -> {
            this.penalties = new Penalties(this);
            this.penalties.load();
            pm.registerEvents(this.penalties, this);
        });
        this.module("arena editor", () -> {
            this.arenaEditor = new ArenaEditor(this);
            Tasks.timer(40L, 10L, this.arenaEditor::tick);
        });
        this.module("rank backups", () -> {
            this.rankBackup = new RankBackup(this);
            Tasks.timer(1200L, 1728000L, () -> this.rankBackup.backup().whenComplete((r, err) -> {
                if (err != null) {
                    this.getLogger().log(Level.WARNING, "Rank backup failed", err);
                } else {
                    this.getLogger().info("Rank backup " + r[0] + " (" + r[1] + " players).");
                }
            }));
        });
        this.module("hacker pool / reports", () -> {
            this.hackerPool = new HackerPool(this);
            this.hackerPool.load();
            pm.registerEvents(this.hackerPool, this);
            this.bind("hackerpool", this.hackerPool);
            Tasks.timer(40L, 40L, this.hackerPool::tick);
            this.reports = new Reports(this);
            this.reports.load();
            this.bind("report", this.reports);
            this.bind("reports", this.reports);
        });
        this.module("ranks / countries / tab / welcome", () -> {
            this.ranksSocial = new Ranks(this);
            this.ranksSocial.load();
            pm.registerEvents(this.ranksSocial, this);
            this.bind("rank", this.ranksSocial);
            this.countries = new Countries(this);
            this.countries.load();
            Tasks.timer(2400L, 2400L, this.countries::save);
            this.tab = new TabManager(this);
            this.tab.load();
            Tasks.timer(30L, 10L, this.tab::tick);
            Tasks.timer(31L, Math.max(2, Cfg.i(Opt.TAB_ANIMATION_TICKS)), this.tab::animate);
            Tasks.timer(100L, 12000L, LinksDialog::refresh);
            this.statsHologram = new StatsHologram(this);
            pm.registerEvents(this.statsHologram, this);
            this.smpBoard = new SmpBoard(this);
            this.smpBoard.load();
            Tasks.timer(35L, 10L, this.smpBoard::tick);
            this.welcome = new Welcome(this);
            this.welcome.load();
            pm.registerEvents(this.welcome, this);
            this.bind("welcome", this.welcome);
            this.bind("guide", this.welcome);
            this.bind("country", this.welcome);
            this.bind("verify", this.welcome);
            Tasks.timer(40L, 20L, this.welcome::tick);
        });
        this.module("decorations", () -> {
            this.decos = new DecoManager(this);
            pm.registerEvents(this.decos, this);
            this.bind("deco", this.decos);
            Tasks.later(40L, this.decos::load);
            Tasks.timer(60L, 5L, this.decos::tick);
            this.textHolograms = new Holograms(this);
            pm.registerEvents(this.textHolograms, this);
            this.bind("holo", this.textHolograms);
            Tasks.later(45L, this.textHolograms::load);
            Tasks.timer(60L, 2L, this.textHolograms::tick);
            ImageWalls images = new ImageWalls(this);
            this.bind("wallimage", images);
            Tasks.later(60L, images::load);
        });
        this.module("xp orb score", () -> pm.registerEvents(new XpOrbText(), this));
        this.module("duel cages", () -> pm.registerEvents(new CageListener(this), this));
        this.module("website / resource pack", () -> {
            this.web = new WebServer(this);
            this.packs = new ResourcePacks(this);
            pm.registerEvents(this.packs, this);
            this.packs.start(this.web);
            WebSite site = new WebSite(this, this.web);
            site.install();
            this.webSite = site;
            this.bind("webadmin", site);
            this.bind("website", site);
            this.sameHttp = new SamePortHttp(this, this.web);
            if (this.sameHttp.install()) {
                this.sameHttp.enabled(Cfg.bool(Opt.WEB_ENABLED) && Cfg.bool(Opt.WEB_SAME_PORT));
            }

            if (Cfg.bool(Opt.WEB_ENABLED)) {
                this.web.start(Cfg.i(Opt.WEB_PORT));
            }

            Cfg.onChange(o -> {
                if (o == Opt.WEB_ENABLED || o == Opt.WEB_PORT || o == Opt.WEB_SAME_PORT) {
                    this.refreshWeb();
                }
            });
        });
        this.module("events / tournaments", () -> {
            this.events = new EventManager(this);
            this.events.load();
            this.bind("event", this.events);
            Tasks.timer(60L, 20L, this.events::tick);
        });
        this.module("kits & settings backups", () -> {
            this.configBackup = new ConfigBackup(this);
            Tasks.timer(12000L, 864000L, () -> {
                for (ConfigBackup.Kind k : ConfigBackup.Kind.values()) {
                    try {
                        this.configBackup.backup(k);
                    } catch (Exception var6x) {
                        this.getLogger().log(Level.WARNING, "Automatic " + k + " backup failed", (Throwable)var6x);
                    }
                }
            });
        });
        this.module("data vault", () -> Tasks.timer(6000L, 432000L, () -> this.vaultSnapshot("auto", null)));
        this.module("ai assistant", () -> {
            this.ai = new AiAgent(this);
            this.bind("ai", new AiCommand(this, this.ai));
        });
        this.module("screenshare", () -> {
            this.screenShare = new ScreenShare(this);
            pm.registerEvents(this.screenShare, this);
            this.bind("ss", this.screenShare);
            Tasks.timer(20L, 20L, this.screenShare::tick);
        });
        this.module("sentinel anti-cheat", () -> {
            Sentinel sentinel = new Sentinel(this);
            pm.registerEvents(sentinel, this);
            this.bind("sentinel", sentinel);
            Tasks.timer(1L, 1L, sentinel::tick);
        });
        this.module("client guard", () -> {
            ClientGuard guard = new ClientGuard(this);
            pm.registerEvents(guard, this);
            this.bind("clientinfo", guard);
        });
        this.module("chat", () -> {
            this.chat = new ChatManager(this);
            this.chat.load();
            pm.registerEvents(this.chat, this);

            for (String c : List.of("msg", "reply", "ignore", "msgtoggle", "socialspy", "mutechat", "clearchat")) {
                this.bind(c, this.chat);
            }

            Tasks.timer(20L, 20L, this.chat::tick);
        });
        this.module("swear filter / mutes", () -> {
            this.chatFilter = new ChatFilter(this);
            pm.registerEvents(this.chatFilter, this);

            for (String c : List.of("mute", "unmute", "mutes", "chatfilter")) {
                this.bind(c, this.chatFilter);
            }
        });
        this.module("practice bots and training", () -> {
            this.bots = new net.vortexrealm.bot.BotManager(this);
            pm.registerEvents(this.bots, this);
            this.bind("bot", this.bots);
            this.bind("train", this.bots);
            Tasks.timer(1L, 1L, this.bots::tick);
            this.botHall = new net.vortexrealm.bot.BotHall(this);
            pm.registerEvents(this.botHall, this);
        });
        this.module("academy (lessons)", () -> {
            this.academy = new net.vortexrealm.academy.Academy(this);
            pm.registerEvents(this.academy, this);
            this.bind("learn", this.academy);
            Tasks.timer(1L, 1L, this.academy::tick);
            pm.registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                    VortexRealm.this.academy.load(e.getPlayer().getUniqueId());
                }
            }, this);

            for (Player online : Bukkit.getOnlinePlayers()) {
                this.academy.load(online.getUniqueId());
            }
        });
        this.module("arena check on first join", () -> pm.registerEvents(new org.bukkit.event.Listener() {
            private boolean checked;

            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                Player p = e.getPlayer();
                Tasks.later(60L, () -> {
                    if (!this.checked && VortexRealm.this.arenasReady) {
                        // the first player of this run: every duel and FFA map is made and switched on if it isn't
                        this.checked = true;
                        VortexRealm.this.firstRunSetup();
                    }

                    if (p.isOnline() && p.hasPermission("vortex.admin") && VortexRealm.this.packRunning) {
                        p.sendMessage(net.vortexrealm.util.Text.mm(
                            "<yellow>[Arenas] <white>Generating arenas in the background: <green>" + VortexRealm.this.packDone + "/" + VortexRealm.this.packTotal
                                + "</green> ready. <gray>They switch on by themselves when done; don't restart until then."
                        ));
                    }
                });
            }
        }, this));
        this.module("mole pvp rules", () -> {
            net.vortexrealm.match.MoleRules mole = new net.vortexrealm.match.MoleRules(this);
            Tasks.timer(20L, 20L, mole::tick);
        });
        this.module("alt finder", () -> {
            net.vortexrealm.social.AltTracker alts = new net.vortexrealm.social.AltTracker(this);
            pm.registerEvents(alts, this);
            this.bind("alts", alts);
        });
        this.module("cosmetics (trims, shields)", () -> {
            this.cosmetics = new net.vortexrealm.cosmetics.Cosmetics(this);
            pm.registerEvents(this.cosmetics, this);
            this.bind("cosmetics", this.cosmetics);

            for (Player online : Bukkit.getOnlinePlayers()) {
                this.cosmetics.load(online.getUniqueId());
            }
        });
        this.module("friends / status / privacy", () -> {
            this.social = new SocialManager(this);
            this.social.load();
            pm.registerEvents(this.social, this);

            for (String c : List.of("friend", "follow", "unfollow", "status")) {
                this.bind(c, this.social);
            }

            Tasks.timer(200L, 2400L, this.social::tick);
        });
        this.module("settings & duel dialogs", () -> {
            this.settingsDialogs = new SettingsDialogs(this);
            this.duelDialogs = new DuelDialogs(this);
        });
        this.module("regions", () -> {
            this.netSync = new net.vortexrealm.social.NetworkSync(this);
            this.regions = new Regions(this);
            this.regions.load();
            this.bind("region", this.regions);
            pm.registerEvents(this.regions, this);
        });
        this.module("winter lobby", () -> {
            this.season = new net.vortexrealm.lobby.Season(this);
            this.season.start();
        });
        this.module("auto-update", () -> {
            this.updater = new net.vortexrealm.update.AutoUpdater(this, new net.vortexrealm.update.AutoUpdater.Host() {
                @Override
                public boolean enabled() {
                    return Cfg.bool(Opt.UPDATES);
                }

                @Override
                public List<String> sources() {
                    return java.util.Arrays.asList(Cfg.s(Opt.UPDATE_SOURCES).split("\\s*,\\s*"));
                }

                @Override
                public int checkMinutes() {
                    return Cfg.i(Opt.UPDATE_CHECK);
                }

                @Override
                public String restartPolicy() {
                    return Cfg.s(Opt.UPDATE_RESTART);
                }

                @Override
                public int restartHour() {
                    return Cfg.i(Opt.UPDATE_HOUR);
                }

                @Override
                public boolean busy() {
                    return VortexRealm.this.matches() != null && !VortexRealm.this.matches().active().isEmpty();
                }

                @Override
                public void backup() throws Exception {
                    VortexRealm.this.configBackup().backup(net.vortexrealm.data.ConfigBackup.Kind.FULL);
                }

                @Override
                public boolean isAdmin(org.bukkit.command.CommandSender s) {
                    return s.hasPermission("vortex.admin");
                }
            });
            this.updater.start();
        });
        this.module("ping equalizer", () -> {
            this.pingEqualizer = new PingEqualizer(this);
            Tasks.timer(40L, 40L, this.pingEqualizer::tick);
        });
        this.module("spectator feed", () -> {
            this.spectatorFeed = new SpectatorFeed(this);
            pm.registerEvents(this.spectatorFeed, this);
        });
        this.module("mcpvp tab columns", () -> {
            this.tabColumns = new TabColumns(this);
            if (this.tabColumns.init()) {
                pm.registerEvents(this.tabColumns, this);
                Tasks.timer(60L, 40L, this.tabColumns::tick);
            }
        });
        this.module("npcs", () -> {
            this.npcs = new NpcManager(this);
            pm.registerEvents(this.npcs, this);
            this.bind("npc", this.npcs);
            Tasks.later(20L, this.npcs::load);
            Tasks.timer(40L, 2L, this.npcs::tick);
        });
        this.module("hub (online players)", () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                this.modes.stashLegacy(online);
                this.modeRules.apply(online);
            }
        });
        if (pm.isPluginEnabled("PlaceholderAPI")) {
            try {
                PlaceholderHook.install(this);
                this.getLogger().info("PlaceholderAPI placeholders registered (%vortex_...%).");
            } catch (Throwable var8) {
                this.getLogger().log(Level.WARNING, "PlaceholderAPI hook failed", var8);
            }
        }

        Tasks.timer(10L, 4L, this.queues::tick);
        Tasks.timer(20L, 20L, this.matches::tickAll);
        Tasks.timer(5L, 10L, this.boards::tick);
        Tasks.timer(20L, 20L, this.ffa::tick);
        Tasks.timer(200L, 100L, this.arenas::autoScale);
        Tasks.timer(1L, 1L, this.trails::tick);
        Tasks.timer(100L, 100L, this.duels::cleanup);
        Tasks.timer(1200L, 1200L, this.snapshots::cleanup);
        Tasks.timer(1200L, 1200L, this.profiles::autosave);
        Tasks.timer(60L, 1200L, () -> {
            this.leaderboards.refresh();
            Tasks.later(60L, this.holograms::refresh);
        });
        Tasks.timer(40L, 2L, this.holograms::tick);
        this.profiles.loadOnline();

        for (Player p : Bukkit.getOnlinePlayers()) {
            this.sessions.get(p);
            this.syncWorldState(p, true);
        }

        // the arena checks need every snapshot, which load in the background
        CompletableFuture.allOf(this.arenas.snapshotsReady(), this.ffa.snapshotsReady()).whenComplete((v, err) -> Tasks.ensureSync(() -> {
            this.arenasReady = true;
            Tasks.later(40L, this::firstRunSetup);
            Tasks.later(100L, this.arenas::verifyAll);
        }));
        MapModel.warmLater(this);
        Tasks.later(1200L, () -> {
            if (Cfg.bool(Opt.ARENA_AUTO_UPDATE)) {
                this.arenas.updateOutdated();
            }
        });
        this.upgrader = new Upgrader(this);
        pm.registerEvents(this.upgrader, this);
        this.upgrader.finish();
        this.enabledOk = true;
        this.getLogger()
            .info(
                "VortexRealm enabled in "
                    + (System.currentTimeMillis() - start)
                    + " ms ("
                    + this.kits().all().size()
                    + " kits, "
                    + this.arenas.all().size()
                    + " arenas, "
                    + this.ffa.all().size()
                    + " FFA)."
            );
    }

    public Map<String, String> failedParts() {
        return Collections.unmodifiableMap(this.failed);
    }

    private void module(String name, Runnable start) {
        try {
            start.run();
        } catch (Throwable var4) {
            this.failed.put(name, var4.getClass().getSimpleName() + ": " + var4.getMessage());
            this.getLogger().log(Level.SEVERE, "Part '" + name + "' failed to start - the rest of VortexRealm keeps working. Please send this error:", var4);
        }
    }

    private void bind(String name, Object executor) {
        PluginCommand cmd = this.getCommand(name);
        if (cmd == null) {
            this.getLogger().warning("Command '" + name + "' missing from plugin.yml");
        } else {
            cmd.setExecutor((CommandExecutor)executor);
            cmd.setTabCompleter((TabCompleter)executor);
        }
    }

    @Override
    public void onDisable() {
        if (this.updater != null) {
            this.updater.stop();
        }

        if (this.netSync != null) {
            this.netSync.stop();
        }

        if (this.academy != null) {
            try {
                this.academy.shutdown();
            } catch (RuntimeException var2) {
            }
        }

        if (this.bots != null) {
            try {
                this.bots.shutdown();
            } catch (RuntimeException var1) {
            }
        }

        if (!this.enabledOk) {
            if (this.db != null) {
                this.db.close();
            }
        } else {
            try {
                if (this.vanilla != null) {
                    this.vanilla.releaseSources();
                }

                this.matches.shutdown();
                if (this.pingEqualizer != null) {
                    this.pingEqualizer.shutdown();
                }

                if (this.tabColumns != null) {
                    this.tabColumns.shutdown();
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    Session s = this.sessions.get(p);
                    if (s.state == PlayerState.FFA) {
                        this.ffa.leave(p, true);
                    } else if (s.state == PlayerState.QUEUE) {
                        this.queues.leaveAll(p, true);
                    } else if (s.state == PlayerState.SPECTATING || s.state == PlayerState.EDITING) {
                        if (s.state == PlayerState.EDITING) {
                            p.closeInventory();
                        }

                        this.lobby.sendToLobby(p);
                    }

                    PlayerReset.unfreeze(p, s);
                }

                this.holograms.shutdown();
                if (this.statsHologram != null) {
                    this.statsHologram.closeAll();
                }

                this.boards.shutdown();
                if (this.npcs != null) {
                    this.npcs.shutdown();
                }

                if (this.serverLink != null) {
                    this.serverLink.stop();
                }

                if (this.countries != null) {
                    this.countries.save();
                }

                if (this.sameHttp != null) {
                    this.sameHttp.shutdown();
                }

                if (this.screenShare != null) {
                    this.screenShare.shutdown();
                }

                XpOrbText.clearAll();
                if (this.web != null) {
                    this.web.stop();
                }

                if (this.decos != null) {
                    this.decos.shutdown();
                    if (this.textHolograms != null) {
                        this.textHolograms.shutdown();
                    }
                }

                this.jobs.stop();
                this.arenas.save();
                this.ffa.save();
            } catch (Throwable var5) {
                this.getLogger().log(Level.SEVERE, "Error while shutting down", var5);
            }

            this.profiles.saveAllNow();

            try {
                String name = this.vault.snapshot("shutdown", this.db::vaultCopy);
                this.getLogger().info("Data vault: saved " + name);
            } catch (Throwable var4) {
                this.getLogger().log(Level.WARNING, "Data vault snapshot at shutdown failed", var4);
            }

            this.db.close();
            instance = null;
        }
    }

    public void syncWorldState(Player p) {
        this.syncWorldState(p, false);
    }

    public void syncWorldState(Player p, boolean joining) {
        Session s = this.sessions.get(p);
        if (this.worlds.managed(p.getWorld())) {
            if (s.state == PlayerState.OUTSIDE) {
                try {
                    boolean inArenaWorld = p.getWorld().equals(this.worlds.practiceWorld());
                    this.lobby.join(p, !joining || inArenaWorld);
                } catch (RuntimeException var6) {
                    this.getLogger().log(Level.SEVERE, "Lobby setup failed for " + p.getName(), (Throwable)var6);
                    s.state = PlayerState.LOBBY;
                }

                try {
                    this.boards.attach(p);
                } catch (RuntimeException var5) {
                    this.getLogger().log(Level.SEVERE, "Scoreboard setup failed for " + p.getName(), (Throwable)var5);
                }
            }
        } else {
            if (s.state != PlayerState.OUTSIDE) {
                this.leavePvP(p, false);
            }

            this.boards.detach(p);
            this.worlds.exitPvP(p);
            s.state = PlayerState.OUTSIDE;
        }
    }

    public void leavePvP(Player p, boolean quitting) {
        Session s = this.sessions.get(p);
        PlayerState st = s.state;
        switch (st) {
            case QUEUE:
                this.queues.leaveAll(p, false);
                break;
            case PREMATCH:
            case MATCH:
                if (s.match != null) {
                    s.match.forfeit(p, true);
                }
                break;
            case SPECTATING:
                if (s.spectating != null) {
                    s.spectating.removeSpectator(p, false);
                }
                break;
            case FFA:
                this.ffa.handleQuit(p);
                break;
            case EDITING:
                s.state = PlayerState.OUTSIDE;
                p.closeInventory();
        }

        if (!quitting && s.state != PlayerState.OUTSIDE && this.parties.of(p) != null) {
            this.parties.leave(p);
        }

        s.match = null;
        s.spectating = null;
        s.ffa = null;
        s.editing = null;
        s.state = PlayerState.OUTSIDE;
        PlayerReset.unfreeze(p, s);
        p.setGravity(true);
        p.setWorldBorder(null);
        this.antiCheat.forget(p.getUniqueId());
    }

    public void forceLeave(Player p) {
        Session s = this.sessions.get(p);
        switch (s.state) {
            case QUEUE:
                this.queues.leaveAll(p, true);
                break;
            case PREMATCH:
            case MATCH:
                if (s.match != null) {
                    s.match.forfeit(p, false);
                } else {
                    this.lobby.sendToLobby(p);
                }
                break;
            case SPECTATING:
                if (s.spectating != null) {
                    s.spectating.removeSpectator(p, true);
                } else {
                    this.lobby.sendToLobby(p);
                }
                break;
            case FFA:
                this.ffa.leave(p, true);
                break;
            case EDITING:
                p.closeInventory();
                break;
            case LOBBY:
                this.lobby.sendToLobby(p);
        }
    }

    public void reloadFiles() {
        Cfg.load(this);
        Msg.load(this);
        this.lobby.load();
        this.kits.load();
        this.holograms.refresh();
        if (this.extras != null) {
            this.extras.load();
        }

        if (this.serverSelector != null) {
            this.serverSelector.load();
        }

        if (this.ranksSocial != null) {
            this.ranksSocial.load();
        }

        if (this.smpBoard != null) {
            this.smpBoard.load();
        }

        if (this.tab != null) {
            this.tab.load();
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            Session s = this.sessions.get(p);
            if (s.state == PlayerState.LOBBY) {
                this.lobby.giveLobbyItems(p);
                this.lobby.applyComforts(p);
            } else if (s.state == PlayerState.QUEUE) {
                this.lobby.giveQueueItems(p);
            }

            this.boards.refreshNametag(p);
        }
    }

    public void saveAll() {
        this.arenas.save();
        this.ffa.save();
        this.kits.save();
        this.profiles.saveAllNow();
    }

    /** One map of the default arena pack: id, theme, size, copies, deep (crystal variant of a vanilla map). */
    private record PackMap(String id, ArenaTheme theme, int size, int copies, boolean deep) {
    }

    private void firstRunSetup() {
        if (this.packRunning) {
            return;
        }

        File flagFile = new File(this.getDataFolder(), "locations.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(flagFile);
        // with auto-generate off, the small compact duel maps and the Mole map are still made (seconds each, little memory)
        boolean light = !Cfg.bool(Opt.AUTO_GENERATE);
        if (true) {
            boolean fresh = !light && !y.getBoolean("first-run-generated", false) && this.arenas.all().isEmpty() && this.ffa.all().isEmpty();
            boolean pack = !y.getBoolean("arena-pack-8", false);
            boolean ffaPack = !y.getBoolean("ffa-pack-8", false);
            java.util.Set<String> skipped = new java.util.HashSet<>(y.getStringList("pack-skipped"));
            // 2.3.0: the open arenas and the small themed duel maps are gone; their FFA arenas get a new look
            java.util.Set<String> retiredFfa = new java.util.HashSet<>();
            List<String> retiredNames = new ArrayList<>();
            for (Arena a : new ArrayList<>(this.arenas.all())) {
                if (a.generated && a.theme != null && a.theme.retired() && !a.handEdited && !a.editing && a.instances.stream().noneMatch(ix -> ix.state == net.vortexrealm.arena.ArenaInstance.State.IN_USE)) {
                    retiredNames.add(a.displayName);
                    this.arenas.delete(a);
                }
            }

            for (FfaArena f : new ArrayList<>(this.ffa.all())) {
                if (f.generated && f.theme != null && f.theme.retired() && f.players.isEmpty()) {
                    retiredNames.add(f.displayName);
                    if (f.kitId != null) {
                        retiredFfa.add(f.kitId.toLowerCase(java.util.Locale.ROOT));
                    }

                    this.ffa.delete(f);
                }
            }

            if (!retiredNames.isEmpty()) {
                this.getLogger().info("Removed arenas of the old looks (open arenas, small duel maps): " + String.join(", ", retiredNames));
            }

            // 1.5.2: a generated map whose build was cut short by a restart has no blocks (or only some): it is
            // removed here and made again below, instead of being left void or switched off
            List<VortexRealm.PackMap> remake = new ArrayList<>();
            List<String> voided = new ArrayList<>();

            for (Arena a : new ArrayList<>(this.arenas.all())) {
                if (this.arenas.isBroken(a) && (!light || a.theme != null && (a.theme.duel != null || a.theme == ArenaTheme.MOLE_MINES))) {
                    int cp = Math.max(1, a.instances.size() - 1);
                    remake.add(new VortexRealm.PackMap(a.id, a.theme, ArenaManager.packSize(a.theme), cp, a.deep));
                    voided.add(a.displayName);
                    this.arenas.delete(a);
                }
            }

            for (FfaArena f : new ArrayList<>(light ? List.<FfaArena>of() : this.ffa.all())) {
                if (ArenaManager.broken(f)) {
                    voided.add(f.displayName + " (FFA)");
                    this.ffa.delete(f);
                } else if (f.generating && f.snapshot != null && !f.building) {
                    // a vanilla FFA map cut short while its blocks were placed: put the missing blocks back
                    this.ffa.fullRegen(f);
                }
            }

            if (!voided.isEmpty()) {
                this.getLogger().warning("Unfinished (void) arenas found, they are made again now: " + String.join(", ", voided));
            }

            // every arena (duel and FFA) whose map is there is switched on; a missing snapshot is taken again
            // with auto-generate off this is skipped: it loads every arena chunk at once (the small maps switch on by themselves)
            ArenaManager.Report opened = light ? new ArenaManager.Report() : this.arenas.enableAll(false);
            if (opened.on > 0) {
                String msg = "Switched on " + opened.on + " arena(s).";
                this.getLogger().info(msg);
                Tasks.later(200L, () -> {
                    for (Player op : Bukkit.getOnlinePlayers()) {
                        if (op.hasPermission("vortex.admin")) {
                            op.sendMessage(net.vortexrealm.util.Text.mm("<green>[Arenas] <white>" + msg));
                        }
                    }
                });
            }

            for (String why : opened.setup) {
                this.getLogger().warning("Arena not switched on: " + why);
            }

            {
                // every start (and the first join): every map of the pack and an FFA arena for every FFA kit
                int duelSize = Cfg.i(Opt.DUEL_ARENA_SIZE);
                int ffaSize = Cfg.i(Opt.FFA_ARENA_SIZE);
                int vanillaSize = Cfg.i(Opt.VANILLA_SIZE);
                int copies = Cfg.i(Opt.DEFAULT_COPIES);
                int vCopies = Cfg.i(Opt.VANILLA_COPIES);
                int dCopies = Math.max(1, copies - 1);
                List<VortexRealm.PackMap> maps = new ArrayList<>();
                // one map of every family first, so every kit can play as soon as possible
                maps.add(new VortexRealm.PackMap("plains", ArenaTheme.PLAINS, vanillaSize, vCopies, false));
                maps.add(new VortexRealm.PackMap("c_plains", ArenaTheme.PLAINS, vanillaSize, vCopies, true));
                maps.add(new VortexRealm.PackMap("colosseum", ArenaTheme.COLOSSEUM_DESERT, duelSize, dCopies, false));
                // 2.1.0: the compact detailed duel maps of the sword kits, and the UHC maps with hills and cover
                for (ArenaTheme t : ArenaTheme.values()) {
                    if (t == ArenaTheme.TRAINING_GROUNDS) {
                        maps.add(new VortexRealm.PackMap((t.duel.build ? "uhc_" : "duel_") + t.duel.name().toLowerCase(java.util.Locale.ROOT).replace("uhc_", ""),
                            t, net.vortexrealm.arena.ArenaManager.targetSize(t, false), t == ArenaTheme.TRAINING_GROUNDS ? Math.max(4, copies) : Math.max(2, copies), false));
                    }
                }

                maps.add(new VortexRealm.PackMap("grand", ArenaTheme.GRAND_COLOSSEUM, Cfg.i(Opt.MACE_ARENA_SIZE), 1, false));
                // MCPVP's natural maps, all real vanilla terrain
                maps.add(new VortexRealm.PackMap("snowy", ArenaTheme.SNOWY, vanillaSize, vCopies, false));
                maps.add(new VortexRealm.PackMap("desert", ArenaTheme.DESERT, vanillaSize, vCopies, false));
                maps.add(new VortexRealm.PackMap("redbadlands", ArenaTheme.BADLANDS_DESERT, vanillaSize, vCopies, false));
                maps.add(new VortexRealm.PackMap("badlands", ArenaTheme.BADLANDS, vanillaSize, vCopies, false));
                maps.add(new VortexRealm.PackMap("end", ArenaTheme.END, vanillaSize, vCopies, false));
                maps.add(new VortexRealm.PackMap("outerend", ArenaTheme.END_OUTER, vanillaSize, vCopies, false));
                // deep crystal maps
                maps.add(new VortexRealm.PackMap("c_snowy", ArenaTheme.SNOWY, vanillaSize, vCopies, true));
                maps.add(new VortexRealm.PackMap("c_desert", ArenaTheme.DESERT, vanillaSize, vCopies, true));
                maps.add(new VortexRealm.PackMap("c_redbadlands", ArenaTheme.BADLANDS_DESERT, vanillaSize, vCopies, true));
                maps.add(new VortexRealm.PackMap("crystalflats", ArenaTheme.FLATLANDS, duelSize, vCopies, false));
                // designed arenas for the sword kits
                maps.add(new VortexRealm.PackMap("temple", ArenaTheme.COLOSSEUM_TEMPLE, duelSize, dCopies, false));
                maps.add(new VortexRealm.PackMap("spires", ArenaTheme.COLOSSEUM_SPIRES, duelSize, dCopies, false));
                maps.add(new VortexRealm.PackMap("inferno", ArenaTheme.COLOSSEUM_NETHER, duelSize, dCopies, false));
                maps.add(new VortexRealm.PackMap("frozen", ArenaTheme.COLOSSEUM_FROST, duelSize, dCopies, false));
                maps.add(new VortexRealm.PackMap("blossom", ArenaTheme.COLOSSEUM_CHERRY, duelSize, dCopies, false));
                maps.add(new VortexRealm.PackMap("gladiator", ArenaTheme.COLOSSEUM_GLADIATOR, duelSize, dCopies, false));
                // 1.4.0: the show arenas and every biome that was only made on request before
                maps.add(new VortexRealm.PackMap("palace", ArenaTheme.DESERT_PALACE, duelSize, dCopies, false));
                maps.add(new VortexRealm.PackMap("olympus", ArenaTheme.OLYMPUS, duelSize, dCopies, false));
                maps.add(new VortexRealm.PackMap("timberfort", ArenaTheme.TIMBER_FORT, duelSize, dCopies, false));
                maps.add(new VortexRealm.PackMap("champions", ArenaTheme.CHAMPIONS_COLOSSEUM, duelSize, dCopies, false));
                maps.add(new VortexRealm.PackMap("cherry", ArenaTheme.CHERRY, vanillaSize, vCopies, false));
                maps.add(new VortexRealm.PackMap("palegarden", ArenaTheme.PALE, vanillaSize, vCopies, false));
                maps.add(new VortexRealm.PackMap("crimson", ArenaTheme.CRIMSON, vanillaSize, vCopies, false));
                maps.add(new VortexRealm.PackMap("mushroomfields", ArenaTheme.MUSHROOM, vanillaSize, vCopies, false));
                // 2.3.0: big vanilla maps for the mace kits (MACE pool, next to the grand colosseum)
                int maceVanilla = Cfg.i(Opt.MACE_VANILLA_SIZE);
                maps.add(new VortexRealm.PackMap("m_plains", ArenaTheme.PLAINS, maceVanilla, vCopies, false));
                maps.add(new VortexRealm.PackMap("m_badlands", ArenaTheme.BADLANDS, maceVanilla, vCopies, false));
                maps.add(new VortexRealm.PackMap("m_snowy", ArenaTheme.SNOWY, maceVanilla, vCopies, false));
                // 1.5.0: Mole PvP's underground map, the biggest one
                maps.add(new VortexRealm.PackMap("molemines", ArenaTheme.MOLE_MINES, net.vortexrealm.build.MoleMinesDesign.RADIUS * 2 + 1, Math.max(2, copies), false));
                for (VortexRealm.PackMap r : remake) {
                    if (maps.stream().noneMatch(m -> m.id().equals(r.id()))) {
                        maps.add(r);
                    }
                }

                List<Supplier<CompletableFuture<?>>> steps = new ArrayList<>();
                List<String> stepIds = new ArrayList<>();
                // order: every FFA arena first (designed ones take seconds, vanilla ones minutes), then the designed duel
                // maps, then the vanilla-terrain duel maps
                List<Integer> stepOrder = new ArrayList<>();

                for (VortexRealm.PackMap m : maps) {
                    if (skipped.contains(m.id()) || light && m.theme().duel == null && m.theme() != ArenaTheme.MOLE_MINES) {
                        continue;
                    }

                    Arena existing = this.arenas.get(m.id());
                    if (existing != null && this.arenas.isBroken(existing)) {
                        // a map whose generation was cut short by a restart: start it again
                        this.arenas.delete(existing);
                        existing = null;
                    }

                    // a void map that was removed above is always made again, even if another map has its theme
                    boolean have = existing != null
                        || !m.id().startsWith("m_") && remake.stream().noneMatch(r -> r.id().equals(m.id())) && this.arenas.all().stream().anyMatch(a -> a.theme == m.theme() && a.deep == m.deep() && (a.bounds != null || a.editing));
                    if (!have) {
                        String name = m.deep() ? m.theme().display + " (Crystal)" : m.theme().display;
                        if (m.id().startsWith("m_")) {
                            // the big mace copies of a biome map play in the MACE pool
                            steps.add(() -> this.arenas.generate(m.id(), name + " (Mace)", m.theme(), m.size(), m.copies(), m.deep()).thenApply(a -> {
                                a.pool = "MACE";
                                this.arenas.save();
                                return a;
                            }));
                        } else {
                            steps.add(() -> this.arenas.generate(m.id(), name, m.theme(), m.size(), m.copies(), m.deep()));
                        }
                        stepIds.add(m.id());
                        stepOrder.add(m.theme().isVanilla() ? 3 : 2);
                    }
                }

                // FFA arenas come right after the first maps (one per family), not after the whole duel pack
                int ffaAt = steps.size();

                {
                    // every FFA kit of the list that has no FFA arena yet (also when a first start was interrupted)
                    String[][] ffaDefs = new String[][]{
                        {"crystal", "PLAINS"},
                        {"sword", "COLOSSEUM_CHERRY"},
                        {"mace", "GRAND_COLOSSEUM"},
                        {"spearmace", "BADLANDS"},
                        {"spear_mace", "BADLANDS"},
                        {"uhc", "TIMBER_FORT"},
                        {"nethpot", "COLOSSEUM_NETHER"},
                        {"pot", "OLYMPUS"},
                        {"combo", "DESERT_PALACE"},
                        {"axe", "COLOSSEUM_GLADIATOR"},
                        {"trident", "COLOSSEUM_FROST"},
                        {"gapple", "COLOSSEUM_SPIRES"},
                        {"nodebuff", "COLOSSEUM_TEMPLE"},
                        {"builduhc", "PLAINS"},
                        {"mole", "MOLE_MINES"}
                    };
                    java.util.Set<String> listed = new java.util.HashSet<>();
                    for (String[] defx : ffaDefs) {
                        listed.add(defx[0]);
                    }

                    // any other FFA kit (added by the admin) gets a decorated map too
                    String[] spare = new String[]{"DESERT_PALACE", "OLYMPUS", "TIMBER_FORT", "COLOSSEUM_DESERT", "COLOSSEUM_TEMPLE", "COLOSSEUM_SPIRES"};
                    List<String[]> extra = new ArrayList<>();
                    int si = 0;

                    for (Kit k : this.kits().all()) {
                        if (k.ffa && !listed.contains(k.id)) {
                            extra.add(new String[]{k.id, spare[si++ % spare.length]});
                        }
                    }

                    if (!extra.isEmpty()) {
                        List<String[]> all = new ArrayList<>(java.util.Arrays.asList(ffaDefs));
                        all.addAll(extra);
                        ffaDefs = all.toArray(new String[0][]);
                    }

                    for (String[] defx : light ? java.util.Arrays.stream(ffaDefs).filter(d -> retiredFfa.contains(d[0])).toArray(String[][]::new) : ffaDefs) {
                        Kit k = this.kits().get(defx[0]);
                        FfaArena half = this.ffa.get(defx[0] + "_ffa");
                        if (half != null && (half.generated && half.bounds == null || half.generating && (half.bounds == null || half.spawns.isEmpty()))) {
                            this.ffa.delete(half);
                        }

                        boolean hasFfa = k != null && this.ffa.all().stream().anyMatch(f -> k.id.equalsIgnoreCase(f.kitId));
                        if (k != null && k.ffa && !hasFfa) {
                            ArenaTheme t = ArenaTheme.parse(defx[1]);
                            int size = t == ArenaTheme.MOLE_MINES ? net.vortexrealm.arena.ArenaManager.targetSize(t, true) : (t == ArenaTheme.GRAND_COLOSSEUM ? Cfg.i(Opt.MACE_ARENA_SIZE) : (t.isVanilla() ? vanillaSize : ffaSize));
                            steps.add(ffaAt, () -> this.ffa.generate(defx[0] + "_ffa", k.displayName + " FFA", t, size, k.id));
                            stepIds.add(ffaAt, defx[0] + "_ffa");
                            stepOrder.add(ffaAt++, t.isVanilla() ? 1 : 0);
                        }
                    }
                }

                if (fresh) {
                    YamlConfiguration first = YamlConfiguration.loadConfiguration(flagFile);
                    first.set("first-run-generated", true);
                    this.saveFlags(first, flagFile);
                }

                {
                    List<Integer> idx = new ArrayList<>();
                    for (int i = 0; i < steps.size(); i++) {
                        idx.add(i);
                    }

                    idx.sort(java.util.Comparator.comparingInt(stepOrder::get));
                    List<Supplier<CompletableFuture<?>>> sortedSteps = new ArrayList<>();
                    List<String> sortedIds = new ArrayList<>();
                    for (int i : idx) {
                        sortedSteps.add(steps.get(i));
                        sortedIds.add(stepIds.get(i));
                    }

                    steps.clear();
                    steps.addAll(sortedSteps);
                    stepIds.clear();
                    stepIds.addAll(sortedIds);
                }

                if (steps.isEmpty()) {
                    this.markPackDone(flagFile);
                } else {
                    long started = System.currentTimeMillis();
                    this.getLogger()
                        .info(
                            (fresh ? "First start" : "Arena pack (every duel map and an FFA arena for every FFA kit)")
                                + ": generating "
                                + steps.size()
                                + " arenas one after another in the background. Players can keep playing meanwhile."
                        );
                    int[] failed = new int[1];
                    int[] done = new int[1];
                    this.packRunning = true;
                    this.packDone = 0;
                    this.packTotal = steps.size();
                    this.packQueued.clear();
                    this.packQueued.addAll(stepIds);
                    CompletableFuture<Object> chain = CompletableFuture.completedFuture(null);

                    for (int si = 0; si < steps.size(); si++) {
                        Supplier<CompletableFuture<?>> step = steps.get(si);
                        String stepId = stepIds.get(si);
                        int stepNo = si + 1;
                        chain = chain.thenComposeAsync(v -> {
                            this.packCurrent = stepId;
                            this.packQueued.remove(stepId);
                            long stepStart = System.currentTimeMillis();
                            this.getLogger().info("Arena pack: making " + stepId + " (" + stepNo + "/" + steps.size() + ")...");
                            CompletableFuture<?> f;
                            try {
                                f = step.get();
                            } catch (RuntimeException var5) {
                                f = CompletableFuture.failedFuture(var5);
                            }

                            // a step that hangs must not hold up every map after it: after 25 minutes the pack moves on
                            // (the map is made again on the next start)
                            return f.copy().orTimeout(25L, java.util.concurrent.TimeUnit.MINUTES).handle((r, err) -> {
                                this.packCurrent = null;
                                if (err instanceof java.util.concurrent.TimeoutException
                                    || err != null && err.getCause() instanceof java.util.concurrent.TimeoutException) {
                                    this.getLogger().warning("Arena pack: " + stepId + " has been generating for 25 minutes - moving on to the next map. "
                                        + "Block queue: " + this.jobs.describe() + ". It is made again on the next start.");
                                    FfaArena stuck = this.ffa.get(stepId);
                                    if (stuck != null) {
                                        stuck.building = false;
                                    }

                                    failed[0]++;
                                    return null;
                                }

                                if (err == null) {
                                    this.getLogger().info("Arena pack: " + stepId + " done in " + (System.currentTimeMillis() - stepStart) / 1000L + "s.");
                                }

                                Throwable root = err;
                                while (root != null && root.getCause() != null && root.getCause() != root) {
                                    root = root.getCause();
                                }

                                if (err != null && root != null && String.valueOf(root.getMessage()).contains("terrain found")) {
                                    // a biome the source seed doesn't have: skipped for good, not retried every start
                                    this.getLogger().warning("Arena pack: " + root.getMessage() + " (skipped).");
                                    YamlConfiguration sk = YamlConfiguration.loadConfiguration(flagFile);
                                    List<String> list = new ArrayList<>(sk.getStringList("pack-skipped"));
                                    if (!list.contains(stepId)) {
                                        list.add(stepId);
                                        sk.set("pack-skipped", list);
                                        this.saveFlags(sk, flagFile);
                                    }
                                } else if (err != null) {
                                    failed[0]++;
                                    this.getLogger().log(Level.WARNING, "An arena of the pack failed to generate (it is retried on the next start)", err);
                                } else {
                                    done[0]++;
                                    this.packDone = done[0];
                                    this.getLogger().info("Arena pack: " + done[0] + "/" + steps.size() + " ready.");
                                }

                                return null;
                            });
                        }, Tasks.MAIN);
                    }

                    chain.whenComplete((v, err) -> Tasks.ensureSync(() -> {
                        this.packQueued.clear();
                        this.packCurrent = null;
                        this.getLogger()
                            .info(
                                "Arena pack finished in "
                                    + (System.currentTimeMillis() - started) / 1000L
                                    + "s: "
                                    + this.arenas.all().size()
                                    + " duel maps, "
                                    + this.ffa.all().size()
                                    + " FFA arenas"
                                    + (failed[0] > 0 ? ", " + failed[0] + " failed (retrying next start)." : ".")
                            );
                        this.packRunning = false;
                        // maps that could not be made (a biome the seed doesn't have) leave no empty entry behind
                        for (Arena stub : new ArrayList<>(this.arenas.all())) {
                            if (stub.generated && stub.bounds == null && !stub.ready() && !stub.editing) {
                                this.arenas.delete(stub);
                            }
                        }

                        if (failed[0] == 0) {
                            this.markPackDone(flagFile);
                        }

                        if (Cfg.bool(Opt.ARENA_AUTO_UPDATE)) {
                            this.arenas.updateOutdated();
                        }
                    }));
                }
            }
        }
    }

    /** True while the arena pack generates, or before it has run once on this version. */
    public boolean packPending() {
        if (this.packRunning) {
            return true;
        } else {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(this.getDataFolder(), "locations.yml"));
            return Cfg.bool(Opt.AUTO_GENERATE) && !(y.getBoolean("arena-pack-8", false) && y.getBoolean("ffa-pack-8", false));
        }
    }

    /** What the arena pack is doing with this arena id: "now", "queued", or null (not in the pack). */
    public String packState(String id) {
        if (id == null) {
            return null;
        } else if (id.equalsIgnoreCase(this.packCurrent)) {
            return "now";
        } else {
            return this.packQueued.contains(id.toLowerCase(java.util.Locale.ROOT)) ? "queued" : null;
        }
    }

    /** "3/12" while the arena pack generates, else null. */
    public String packProgress() {
        return this.packRunning ? this.packDone + "/" + this.packTotal : null;
    }

    /** True while the arena pack generates (arena updates wait for it). */
    public boolean packRunning() {
        return this.packRunning;
    }

    private void markPackDone(File f) {
        YamlConfiguration fresh = YamlConfiguration.loadConfiguration(f);
        fresh.set("first-run-generated", true);
        fresh.set("arena-pack-2", true);
        fresh.set("arena-pack-3", true);
        fresh.set("arena-pack-4", true);
        fresh.set("arena-pack-5", true);
        fresh.set("ffa-pack-5", true);
        fresh.set("arena-pack-6", true);
        fresh.set("ffa-pack-6", true);
        fresh.set("arena-pack-8", true);
        fresh.set("ffa-pack-8", true);
        this.saveFlags(fresh, f);
    }

    private void saveFlags(YamlConfiguration y, File f) {
        try {
            y.save(f);
        } catch (IOException var4) {
            this.getLogger().log(Level.WARNING, "Could not write locations.yml", (Throwable)var4);
        }
    }

    public void selfTest(CommandSender sender) {
        new SelfTest(this).run(sender);
    }

    public Database db() {
        return this.db;
    }

    public VanillaTerrain vanilla() {
        return this.vanilla;
    }

    public ProfileManager profiles() {
        return this.profiles;
    }

    public SessionManager sessions() {
        return this.sessions;
    }

    public KitManager kits() {
        return this.kits;
    }

    public ArenaManager arenas() {
        return this.arenas;
    }

    public BlockJobs jobs() {
        return this.jobs;
    }

    public WorldManager worlds() {
        return this.worlds;
    }

    public LobbyManager lobby() {
        return this.lobby;
    }

    public QueueManager queues() {
        return this.queues;
    }

    public MatchManager matches() {
        return this.matches;
    }

    public RankManager ranks() {
        return this.ranks;
    }

    public LeaderboardManager leaderboards() {
        return this.leaderboards;
    }

    public FfaManager ffa() {
        return this.ffa;
    }

    public PartyManager parties() {
        return this.parties;
    }

    public DuelManager duels() {
        return this.duels;
    }

    public BoardManager boards() {
        return this.boards;
    }

    public HologramManager holograms() {
        return this.holograms;
    }

    public Dialogs dialogs() {
        return this.dialogs;
    }

    public SnapshotManager snapshots() {
        return this.snapshots;
    }

    public TrailManager trails() {
        return this.trails;
    }

    public AntiCheatHook antiCheat() {
        return this.antiCheat;
    }

    public CombatListener combat() {
        return this.combat;
    }

    public StaffManager staff() {
        return this.staff;
    }

    public ModeManager modes() {
        return this.modes;
    }

    public ModeRules modeRules() {
        return this.modeRules;
    }

    public NpcManager npcs() {
        return this.npcs;
    }

    public ServerSelector serverSelector() {
        return this.serverSelector;
    }

    public ServerLink serverLink() {
        return this.serverLink;
    }

    public Hub hub() {
        return this.hub;
    }

    public Penalties penalties() {
        return this.penalties;
    }

    public ArenaEditor arenaEditor() {
        return this.arenaEditor;
    }

    public Ranks serverRanks() {
        return this.ranksSocial;
    }

    public Countries countries() {
        return this.countries;
    }

    public TabManager tab() {
        return this.tab;
    }

    public EventManager events() {
        return this.events;
    }

    public StatsHologram statsHologram() {
        return this.statsHologram;
    }

    public SmpBoard smpBoard() {
        return this.smpBoard;
    }

    public ConfigBackup configBackup() {
        return this.configBackup;
    }

    public DecoManager decos() {
        return this.decos;
    }

    public Holograms textHolograms() {
        return this.textHolograms;
    }

    public AiAgent ai() {
        return this.ai;
    }

    public WebSite webSite() {
        return this.webSite;
    }

    public ScreenShare screenShare() {
        return this.screenShare;
    }

    public WebServer web() {
        return this.web;
    }

    public ResourcePacks packs() {
        return this.packs;
    }

    public boolean hasPack(Player viewer) {
        return this.packs != null && this.packs.has(viewer);
    }

    public String webBase(Player p) {
        String host = Cfg.s(Opt.WEB_HOST).trim();
        if (host.isEmpty()) {
            host = ResourcePacks.joinHost(p);
        }

        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            int port = Cfg.i(Opt.WEB_PORT);
            if (this.web != null && this.web.onSamePort()) {
                InetSocketAddress vh = p == null ? null : p.getVirtualHost();
                port = vh != null && vh.getPort() > 0 ? vh.getPort() : this.getServer().getPort();
            }

            return "http://" + host + (port == 80 ? "" : ":" + port);
        } else {
            return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
        }
    }

    public void refreshWeb() {
        if (this.web != null) {
            if (this.sameHttp != null && this.sameHttp.installed()) {
                this.sameHttp.enabled(Cfg.bool(Opt.WEB_ENABLED) && Cfg.bool(Opt.WEB_SAME_PORT));
            }

            boolean want = Cfg.bool(Opt.WEB_ENABLED);
            if (!want && this.web.running()) {
                this.web.stop();
            } else if (want && (!this.web.running() || this.web.port() != Cfg.i(Opt.WEB_PORT))) {
                this.web.stop();
                this.web.start(Cfg.i(Opt.WEB_PORT));
            }
        }
    }

    public Welcome welcome() {
        return this.welcome;
    }

    public HackerPool hackerPool() {
        return this.hackerPool;
    }

    public Reports reports() {
        return this.reports;
    }

    public Upgrader upgrader() {
        return this.upgrader;
    }

    public DataVault vault() {
        return this.vault;
    }

    public void vaultSnapshot(String reason, BiConsumer<String, Throwable> done) {
        this.saveAll();
        CompletableFuture.<String>supplyAsync(() -> {
            try {
                return this.vault.snapshot(reason, this.db::vaultCopy);
            } catch (IOException var3) {
                throw new CompletionException(var3);
            }
        }).whenComplete((name, err) -> Tasks.ensureSync(() -> {
            if (err != null) {
                this.getLogger().log(Level.WARNING, "Data vault snapshot failed", err);
            } else {
                this.getLogger().info("Data vault: saved " + name);
            }

            if (done != null) {
                done.accept(name, err == null ? null : (err.getCause() != null ? err.getCause() : err));
            }
        }));
    }

    public RankBackup rankBackup() {
        return this.rankBackup;
    }

    public net.vortexrealm.bot.BotHall botHall() {
        return this.botHall;
    }

    public net.vortexrealm.academy.Academy academy() {
        return this.academy;
    }

    public net.vortexrealm.bot.BotManager bots() {
        return this.bots;
    }

    public net.vortexrealm.cosmetics.Cosmetics cosmetics() {
        return this.cosmetics;
    }

    public ChatFilter chatFilter() {
        return this.chatFilter;
    }

    public ChatManager chat() {
        return this.chat;
    }

    public SocialManager social() {
        return this.social;
    }

    public SettingsDialogs settingsDialogs() {
        return this.settingsDialogs;
    }

    public DuelDialogs duelDialogs() {
        return this.duelDialogs;
    }

    public Regions regions() {
        return this.regions;
    }

    public net.vortexrealm.update.AutoUpdater updater() {
        return this.updater;
    }

    public net.vortexrealm.lobby.Season season() {
        return this.season;
    }

    public net.vortexrealm.social.NetworkSync netSync() {
        return this.netSync;
    }

    public PingEqualizer pingEqualizer() {
        return this.pingEqualizer;
    }

    public TabColumns tabColumns() {
        return this.tabColumns;
    }

    public SpectatorFeed spectatorFeed() {
        return this.spectatorFeed;
    }
}
