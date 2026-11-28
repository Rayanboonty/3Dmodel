package net.vortexpvp.plus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.vortexpvp.SelfTest;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.api.NameDecor;
import net.vortexpvp.data.KitStats;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

/**
 * Starts and stops every VortexPvP add-on system: storage, settings, listeners, commands, timers, the name badges
 * in chat and tab, and one self-test check per system (run with /pvpadmin selftest or /plus selftest).
 */
public final class PlusModule {
    public static final String[] COMMANDS = {
        "ladder", "season", "clan", "clanchat", "duo", "partywar", "lastmatch", "replay", "drill", "coach", "arcade", "pass", "missions", "achievements", "titles",
        "daily", "vfx", "pet", "gadget", "parkour", "hud", "live", "streamer", "link", "creator", "code", "mentor", "fspec", "cinematic", "evidence", "sslog", "alerts",
        "bounty", "kitvote", "motw", "weeklycup", "crate", "bid", "deal", "ecoevent", "ecowipe", "plus"
    };
    private final VortexPvP plugin;
    private final File dir;
    private PlusConfig config;
    private PlusStore store;
    private Progression progression;
    private Seasons seasons;
    private Ladder ladder;
    private Clans clans;
    private Analysis analysis;
    private Discord discord;
    private Replays replays;
    private Drills drills;
    private Arcade arcade;
    private Bounties bounties;
    private Flair flair;
    private HubFun hub;
    private Social social;
    private Fairness fairness;
    private EcoPlus eco;
    private WebPlus web;
    private Weekly weekly;
    private Duo duo;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<Function<UUID, Component>> chatDecor = new ArrayList<>();
    private final List<Function<UUID, Component>> tabDecor = new ArrayList<>();
    private Consumer<SelfTest> selfTest;
    private int ticks;

    public PlusModule(VortexPvP plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "plus");
        this.dir.mkdirs();
    }

    public void enable() {
        long t0 = System.currentTimeMillis();
        this.config = new PlusConfig(this.dir);
        this.store = new PlusStore(this.plugin, this.dir);
        this.seasons = new Seasons(this.plugin, this);
        this.progression = new Progression(this.plugin, this);
        this.ladder = new Ladder(this.plugin, this);
        this.clans = new Clans(this.plugin, this);
        this.analysis = new Analysis(this.plugin, this);
        this.discord = new Discord(this.plugin, this);
        this.replays = new Replays(this.plugin, this);
        this.drills = new Drills(this.plugin, this);
        this.arcade = new Arcade(this.plugin, this);
        this.flair = new Flair(this.plugin, this);
        this.bounties = new Bounties(this.plugin, this);
        this.hub = new HubFun(this.plugin, this);
        this.social = new Social(this.plugin, this);
        this.fairness = new Fairness(this.plugin, this);
        this.eco = new EcoPlus(this.plugin, this);
        this.web = new WebPlus(this.plugin, this);
        this.weekly = new Weekly(this.plugin, this);
        this.duo = new Duo(this.plugin, this);
        for (Listener l : List.of(this.store, this.progression, this.ladder, this.clans, this.analysis, this.discord, this.replays, this.drills, this.arcade, this.flair,
            this.bounties, this.hub, this.social, this.fairness, this.eco, this.duo)) {
            Bukkit.getPluginManager().registerEvents(l, this.plugin);
            this.listeners.add(l);
        }

        PlusCommands cmds = new PlusCommands(this.plugin, this);
        int missing = 0;
        for (String c : COMMANDS) {
            PluginCommand pc = this.plugin.getCommand(c);
            if (pc == null) {
                missing++;
                this.plugin.getLogger().warning("VortexPvP command /" + c + " is missing from plugin.yml");
                continue;
            }

            pc.setExecutor(cmds);
            pc.setTabCompleter(cmds);
        }

        this.decor();
        this.timers();
        this.selfTest = this::selfTest;
        SelfTest.EXTRA.add(this.selfTest);
        try {
            this.web.install();
        } catch (RuntimeException ex) {
            this.plugin.getLogger().log(Level.WARNING, "VortexPvP web pages could not be added", ex);
        }

        Tasks.later(100L, () -> {
            this.seasons.refreshBoard();
            this.replays.purge();
        });
        this.config.save();
        this.plugin.getLogger().info("VortexPvP add-on systems ready in " + (System.currentTimeMillis() - t0) + " ms (" + this.listeners.size() + " listeners, "
            + (COMMANDS.length - missing) + " commands, season " + this.seasons.number() + ")");
    }

    private void decor() {
        if (this.config.bool("ladder.tab-badge", true)) {
            this.tabDecor.add(u -> this.ladder.tabBadge(u));
        }

        this.tabDecor.add(u -> this.clans.badge(u));
        this.tabDecor.add(this::streakFlair);
        this.tabDecor.add(this::liveBadge);
        this.chatDecor.add(this::titleTag);
        this.chatDecor.add(u -> this.clans.badge(u));
        if (this.config.bool("ladder.chat-badge", true)) {
            this.chatDecor.add(u -> this.ladder.tabBadge(u));
        }

        this.chatDecor.add(this::liveBadge);
        NameDecor.TAB.addAll(this.tabDecor);
        NameDecor.CHAT.addAll(this.chatDecor);
    }

    private Component titleTag(UUID u) {
        PlayerData d = this.store.ifLoaded(u);
        return d == null || d.title.isEmpty() ? Component.empty() : Text.mm(" <dark_gray>«<light_purple>" + Text.esc(d.title) + "<dark_gray>»");
    }

    private Component liveBadge(UUID u) {
        PlayerData d = this.store.ifLoaded(u);
        if (d == null) {
            return Component.empty();
        }

        Component c = Component.empty();
        if (!d.live.isEmpty()) {
            c = c.append(Text.mm(" <red><b>●LIVE</b>"));
        }

        if (this.social.isCreator(u)) {
            c = c.append(Text.mm(" <light_purple>✪"));
        }

        return c;
    }

    private Component streakFlair(UUID u) {
        PvPProfile prof = this.plugin.profiles().getIfLoaded(u);
        if (prof == null) {
            return Component.empty();
        }

        int best = 0;
        for (KitStats k : prof.kits.values()) {
            best = Math.max(best, k.streak);
        }

        return best >= this.config.i("ladder.flair-streak", 5) ? Text.mm(" <#FF7A1A>✷" + best) : Component.empty();
    }

    private void every(long period, Runnable r) {
        this.tasks.add(Tasks.timer(period, period, () -> {
            try {
                r.run();
            } catch (RuntimeException ex) {
                this.plugin.getLogger().log(Level.WARNING, "VortexPvP timer error", ex);
            }
        }));
    }

    private void timers() {
        this.every(1L, () -> {
            this.ticks++;
            this.replays.tick();
            this.drills.tick();
            this.social.tick(this.ticks);
            if (this.ticks % 2 == 0) {
                this.arcade.tick();
            }

            if (this.ticks % 4 == 0) {
                this.analysis.hud();
            }

            if (this.ticks % 5 == 0) {
                this.hub.tick();
            }
        });
        this.every(20L, () -> {
            this.arcade.second();
            this.duo.second();
            this.discord.tick();
            this.eco.second();
            this.fairness.clicks();
            this.hub.decorations();
        });
        this.every(40L, this.bounties::check);
        this.every(200L, () -> {
            this.eco.bidTick();
            this.eco.deliverPending();
        });
        this.every(300L, this.discord::pollLinks);
        this.every(1200L, () -> {
            this.ladder.minute();
            this.weekly.minute();
            this.store.saveDirty();
            this.config.save();
        });
        this.every(12000L, this.seasons::check);
        this.every(72000L, this.replays::purge);
    }

    public void disable() {
        for (BukkitTask t : this.tasks) {
            t.cancel();
        }

        this.tasks.clear();
        if (this.selfTest != null) {
            SelfTest.EXTRA.remove(this.selfTest);
        }

        NameDecor.TAB.removeAll(this.tabDecor);
        NameDecor.CHAT.removeAll(this.chatDecor);
        this.tabDecor.clear();
        this.chatDecor.clear();
        for (Runnable r : new Runnable[]{() -> this.replays.stopAll(), () -> this.drills.stopAll(), () -> this.arcade.stopAll(), () -> this.hub.removeAll(),
            () -> this.bounties.clearGlow(), () -> this.eco.cleanup(), () -> this.seasons.remove()}) {
            try {
                r.run();
            } catch (RuntimeException ex) {
                this.plugin.getLogger().log(Level.WARNING, "VortexPvP shutdown step failed", ex);
            }
        }

        for (Listener l : this.listeners) {
            HandlerList.unregisterAll(l);
        }

        this.listeners.clear();
        this.store.saveAllNow();
        this.config.save();
    }

    /** /plus reload: settings and documents are read again. */
    public void reload() {
        this.config.reload();
        this.store.saveAllNow();
    }

    // ------------------------------------------------------------------ self test

    private void check(SelfTest t, String name, String problem) {
        t.extra("Plus: " + name, problem == null, problem == null ? "ok" : problem);
    }

    private void selfTest(SelfTest t) {
        this.check(t, "rank ladder", Ladder.selfCheck());
        this.check(t, "missions, pass and achievements", this.progression.selfCheck());
        this.check(t, "season", this.seasons.number() >= 1 && this.seasons.end() > 0 ? null : "no season");
        this.check(t, "replay codec", Replays.codecCheck());
        this.check(t, "discord", this.discord.selfCheck());
        this.check(t, "arcade", this.arcade.selfCheck());
        this.check(t, "drills", this.drills.selfCheck());
        this.check(t, "hub fun", this.hub.selfCheck());
        this.check(t, "social", this.social.selfCheck());
        this.check(t, "fairness", this.fairness.selfCheck());
        this.check(t, "eco extras", this.eco.selfCheck());
        this.check(t, "weekly", this.weekly.selfCheck());
        this.check(t, "duo", this.duo.selfCheck());
        this.check(t, "flair", this.flair.selfCheck());
        this.check(t, "web", this.web.selfCheck());
        int missing = 0;
        for (String c : COMMANDS) {
            PluginCommand pc = this.plugin.getCommand(c);
            if (pc == null || !(pc.getExecutor() instanceof PlusCommands)) {
                missing++;
            }
        }

        this.check(t, "commands", missing == 0 ? null : missing + " missing");
        String store;
        try {
            this.store.doc("selftest").set("at", System.currentTimeMillis());
            this.store.dirty("selftest");
            this.store.saveDirty();
            store = null;
        } catch (RuntimeException ex) {
            store = ex.toString();
        }

        this.check(t, "storage" + (this.store.shared() ? " (MySQL, shared)" : " (files)"), store);
        this.check(t, "name badges", NameDecor.TAB.containsAll(this.tabDecor) && NameDecor.CHAT.containsAll(this.chatDecor) ? null : "not registered");
    }

    // ------------------------------------------------------------------ accessors

    public VortexPvP plugin() {
        return this.plugin;
    }

    public PlusConfig config() {
        return this.config;
    }

    public PlusStore store() {
        return this.store;
    }

    public Progression progression() {
        return this.progression;
    }

    public Seasons seasons() {
        return this.seasons;
    }

    public Ladder ladder() {
        return this.ladder;
    }

    public Clans clans() {
        return this.clans;
    }

    public Analysis analysis() {
        return this.analysis;
    }

    public Discord discord() {
        return this.discord;
    }

    public Replays replays() {
        return this.replays;
    }

    public Drills drills() {
        return this.drills;
    }

    public Arcade arcade() {
        return this.arcade;
    }

    public Bounties bounties() {
        return this.bounties;
    }

    public Flair flair() {
        return this.flair;
    }

    public HubFun hub() {
        return this.hub;
    }

    public Social social() {
        return this.social;
    }

    public Fairness fairness() {
        return this.fairness;
    }

    public EcoPlus eco() {
        return this.eco;
    }

    public WebPlus web() {
        return this.web;
    }

    public Weekly weekly() {
        return this.weekly;
    }

    public Duo duo() {
        return this.duo;
    }
}
