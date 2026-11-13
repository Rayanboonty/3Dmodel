package net.vortexrealm.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.arena.Arena;
import net.vortexrealm.arena.ArenaInstance;
import net.vortexrealm.arena.ArenaManager;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Msg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.data.KitStats;
import net.vortexrealm.data.PvPProfile;
import net.vortexrealm.data.Setting;
import net.vortexrealm.fx.Fx;
import net.vortexrealm.fx.PointsFx;
import net.vortexrealm.fx.RankDrop;
import net.vortexrealm.fx.RankReveal;
import net.vortexrealm.fx.RankScreen;
import net.vortexrealm.fx.TierLetters;
import net.vortexrealm.fx.Toasts;
import net.vortexrealm.fx.TotemPop;
import net.vortexrealm.fx.XpOrbText;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.kit.KitVote;
import net.vortexrealm.rank.RankManager;
import net.vortexrealm.rank.Tier;
import net.vortexrealm.session.PlayerReset;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Session;
import net.vortexrealm.session.Tp;
import net.vortexrealm.util.ChatCenter;
import net.vortexrealm.util.Cuboid;
import net.vortexrealm.util.Fmt;
import net.vortexrealm.util.Icons;
import net.vortexrealm.util.Locs;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import net.vortexrealm.web.LiveFeed;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class Match {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final NamedTextColor[] TEAM_COLORS = new NamedTextColor[]{
        NamedTextColor.BLUE,
        NamedTextColor.RED,
        NamedTextColor.GREEN,
        NamedTextColor.YELLOW,
        NamedTextColor.LIGHT_PURPLE,
        NamedTextColor.AQUA,
        NamedTextColor.GOLD,
        NamedTextColor.WHITE
    };
    public final int id = IDS.incrementAndGet();
    public final Kit kit;
    public final boolean ranked;
    public final Match.Type type;
    public final List<MatchTeam> teams;
    private final VortexRealm plugin;
    private final Arena forcedArena;
    private final boolean fromQueue;
    private int roundsToWin;
    /** Rules both players voted for: no natural regen, knockback percent. */
    private boolean votedNoRegen;
    private int votedKnockback = -1;
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Map<UUID, MatchStats> stats = new HashMap<>();
    private final Map<UUID, InventorySnapshot> snapshots = new HashMap<>();
    private final List<KitVote> votes = new ArrayList<>();
    private ArenaInstance instance;
    private Match.State state = Match.State.WAITING;
    private int round;
    private long createdAt = System.currentTimeMillis();
    private long startedAt;
    private long fightStartedAt;
    private PreMatch preMatch;
    private PreMatch usedPreMatch;
    private StartSequence sequence;
    private int arenaWaitAttempts;
    private long startingSince;
    private long endedAt;
    private int scorePulse;
    private static final int THROW_TICKS = 28;
    public BiConsumer<Match, MatchTeam> onFinish;
    private MatchTeam finalWinner;
    private final Set<UUID> celebratedHere = new HashSet<>();
    private Location lastDeath;
    private int skillExtra;
    private boolean cleaned;
    /** Ticks between the end and the scheduled cleanup (so the stale-match check never fires first). */
    private long cleanupDueMs;
    /** The team that won the round being shown; its players may walk around until the next round. */
    private MatchTeam roundWinner;
    /** Tournament start override (null = cage kits / arena / global setting). */
    public Opt.StartMode forcedStart;
    /** No cages, fences or gates at all: GATES/CAGE starts become the elevator. */
    public boolean noGates;
    /** Both players agreed to the Ping Equalizer (duel / Tier Challenge option). */
    public boolean pingEqualizer;
    /** The duel was sent with spectators blocked. */
    public boolean spectatorsBlocked;
    /** A ranked Tier Challenge: bigger rating swing. */
    public boolean tierChallenge;
    private boolean equalizing;

    Match(VortexRealm plugin, Kit kit, boolean ranked, Match.Type type, List<List<UUID>> teamMembers, Arena forced, boolean fromQueue, int rounds) {
        this.plugin = plugin;
        this.kit = kit;
        this.ranked = ranked;
        this.type = type;
        this.forcedArena = forced;
        this.fromQueue = fromQueue;
        List<MatchTeam> list = new ArrayList<>();

        for (int i = 0; i < teamMembers.size(); i++) {
            list.add(new MatchTeam(i, teamMembers.get(i), TEAM_COLORS[i % TEAM_COLORS.length]));

            for (UUID u : teamMembers.get(i)) {
                this.stats.put(u, new MatchStats());
            }
        }

        this.teams = Collections.unmodifiableList(list);
        this.roundsToWin = rounds > 0 ? rounds : (type == Match.Type.DUEL ? kit.rounds() : 1);
    }

    public Match.State state() {
        return this.state;
    }

    public ArenaInstance instance() {
        return this.instance;
    }

    public int round() {
        return this.round;
    }

    public int roundsToWin() {
        return this.roundsToWin;
    }

    /** True when natural regeneration is off in this match (kit setting or a passed vote). */
    public boolean noRegen() {
        return !this.kit.regen || this.votedNoRegen;
    }

    /** Knockback multiplier for this match (kit setting, or a passed vote). */
    public double knockback() {
        return (this.votedKnockback >= 0 ? this.votedKnockback : this.kit.knockback) / 100.0;
    }

    public boolean isOneVsOne() {
        return this.teams.size() == 2 && this.teams.get(0).members.size() == 1 && this.teams.get(1).members.size() == 1;
    }

    public PreMatch preMatch() {
        return this.preMatch;
    }

    public MatchTeam teamOf(UUID u) {
        for (MatchTeam t : this.teams) {
            if (t.has(u)) {
                return t;
            }
        }

        return null;
    }

    public boolean isParticipant(UUID u) {
        return this.teamOf(u) != null;
    }

    public boolean isAlive(UUID u) {
        MatchTeam t = this.teamOf(u);
        return t != null && t.alive.contains(u);
    }

    public MatchStats stats(UUID u) {
        return this.stats.computeIfAbsent(u, k -> new MatchStats());
    }

    public Set<UUID> spectators() {
        return Collections.unmodifiableSet(this.spectators);
    }

    public long fightSeconds() {
        return this.fightStartedAt == 0L ? 0L : (System.currentTimeMillis() - this.fightStartedAt) / 1000L;
    }

    public long durationSeconds() {
        return this.startedAt == 0L ? 0L : (System.currentTimeMillis() - this.startedAt) / 1000L;
    }

    public Player opponent(Player p) {
        MatchTeam mine = this.teamOf(p.getUniqueId());

        for (MatchTeam t : this.teams) {
            if (t != mine && !t.members.isEmpty()) {
                return Bukkit.getPlayer(t.members.get(0));
            }
        }

        return null;
    }

    public MatchTeam otherTeam(MatchTeam t) {
        for (MatchTeam o : this.teams) {
            if (o != t) {
                return o;
            }
        }

        return null;
    }

    public List<Player> participantsOnline() {
        List<Player> out = new ArrayList<>();

        for (MatchTeam t : this.teams) {
            for (UUID u : t.members) {
                if (!t.left.contains(u)) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null && p.isOnline()) {
                        out.add(p);
                    }
                }
            }
        }

        return out;
    }

    public List<Player> alivePlayers() {
        List<Player> out = new ArrayList<>();

        for (MatchTeam t : this.teams) {
            for (UUID u : t.alive) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && p.isOnline()) {
                    out.add(p);
                }
            }
        }

        return out;
    }

    public List<Player> audience() {
        List<Player> out = this.participantsOnline();

        for (UUID u : this.spectators) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                out.add(p);
            }
        }

        return out;
    }

    public String title() {
        return this.isOneVsOne()
            ? name(this.teams.get(0).members.get(0)) + " vs " + name(this.teams.get(1).members.get(0))
            : Fmt.capitalize(this.type.name()) + " (" + this.participantsOnline().size() + " players)";
    }

    private static String name(UUID u) {
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            return p.getName();
        } else {
            String n = Bukkit.getOfflinePlayer(u).getName();
            return n == null ? "?" : n;
        }
    }

    void begin() {
        if (this.state == Match.State.WAITING) {
            if (this.participantsOnline().size() < this.countMembers()) {
                this.cancel("match.cancel-left", true);
            } else {
                List<Arena> maps = new ArrayList<>();
                if (this.forcedArena != null && this.forcedArena.ready()) {
                    maps.add(this.forcedArena);
                } else {
                    maps.addAll(this.plugin.arenas().forKit(this.kit));
                }

                if (maps.isEmpty()) {
                    this.cancel("match.no-arena", false);
                } else {
                    Collections.shuffle(maps);
                    // MCPVP offers seven maps (four on top, three below)
                    if (maps.size() > 7) {
                        maps = new ArrayList<>(maps.subList(0, 7));
                    }

                    boolean wantsVotes = Cfg.bool(Opt.VOTES) && !PreMatch.votesFor(this.kit).isEmpty();
                    boolean banPhase = Cfg.bool(Opt.MAP_BAN) && this.isOneVsOne() && this.forcedArena == null && (maps.size() > 1 || wantsVotes);
                    if (banPhase) {
                        this.state = Match.State.PREMATCH;
                        List<Player> players = this.participantsOnline();

                        for (Player p : players) {
                            Session s = this.plugin.sessions().get(p);
                            s.state = PlayerState.PREMATCH;
                            s.match = this;
                        }

                        this.plugin.lobby().refreshVisibilityAll();
                        this.preMatch = new PreMatch(this, maps, players);
                        this.usedPreMatch = this.preMatch;
                        this.preMatch.start();
                    } else {
                        this.onArenaChosen(maps.get(ThreadLocalRandom.current().nextInt(maps.size())), List.of());
                    }
                }
            }
        }
    }

    private int countMembers() {
        int n = 0;

        for (MatchTeam t : this.teams) {
            n += t.members.size();
        }

        return n;
    }

    void onArenaChosen(Arena arena, List<KitVote> passedVotes) {
        if (this.state != Match.State.ENDED) {
            this.preMatch = null;
            this.votes.clear();
            this.votes.addAll(passedVotes);

            for (KitVote v : passedVotes) {
                switch (v.action()) {
                    case ROUNDS -> {
                        if (this.type == Match.Type.DUEL && this.teams.size() == 2) {
                            this.roundsToWin = Math.max(this.roundsToWin, Math.min(10, v.amount()));
                        }
                    }
                    case NO_REGEN -> this.votedNoRegen = true;
                    case KNOCKBACK -> this.votedKnockback = Math.max(0, Math.min(400, v.amount()));
                    default -> {
                    }
                }
            }
            this.instance = arena != null ? this.plugin.arenas().acquire(arena, this) : null;
            if (this.instance == null) {
                this.instance = this.plugin.arenas().acquireAny(this.kit, this);
            }

            if (this.instance == null) {
                this.waitForArena(arena);
            } else {
                this.state = Match.State.STARTING;
                this.startedAt = System.currentTimeMillis();
                ArenaInstance inst = this.instance;
                ArenaManager.preload(inst.world(), inst.bounds()).orTimeout(6L, TimeUnit.SECONDS).whenComplete((v, err) -> Tasks.ensureSync(() -> {
                    if (this.state != Match.State.ENDED) {
                        this.enterArena();
                    }
                }));
            }
        }
    }

    private void waitForArena(Arena preferred) {
        this.state = Match.State.PREMATCH;
        if (this.arenaWaitAttempts++ >= 30) {
            this.cancel("match.no-free-arena", true);
        } else {
            for (Player p : this.participantsOnline()) {
                p.sendActionBar(Msg.get("match.waiting-arena"));
            }

            Tasks.later(20L, () -> {
                if (this.state == Match.State.PREMATCH && this.preMatch == null) {
                    this.onArenaChosen(preferred, new ArrayList<>(this.votes));
                }
            });
        }
    }

    private void releasePreMatchSlot() {
        if (this.usedPreMatch != null) {
            this.usedPreMatch.releaseSlot();
            this.usedPreMatch = null;
        }
    }

    private void enterArena() {
        this.releasePreMatchSlot();

        for (Player p : this.participantsOnline()) {
            Session s = this.plugin.sessions().get(p);
            s.state = PlayerState.MATCH;
            s.match = this;

            try {
                PlayerReset.full(p, GameMode.SURVIVAL);
                PlayerReset.unfreeze(p, s);
            } catch (RuntimeException var6) {
                this.plugin.getLogger().log(Level.SEVERE, "Resetting " + p.getName() + " failed", (Throwable)var6);
            }

            Tp.to(p, this.spawnOf(p));

            try {
                this.border(p);
            } catch (RuntimeException var5) {
                this.plugin.getLogger().log(Level.WARNING, "Arena border failed for " + p.getName(), (Throwable)var5);
            }
        }

        try {
            this.plugin.lobby().refreshVisibilityAll();

            for (Player p : this.participantsOnline()) {
                this.plugin.boards().refreshNametag(p);
            }
        } catch (RuntimeException var7) {
            this.plugin.getLogger().log(Level.WARNING, "Nametag/visibility update failed", (Throwable)var7);
        }

        this.startRound(true);
    }

    public Location spawnOf(Player p) {
        MatchTeam t = this.teamOf(p.getUniqueId());
        if (t != null && this.instance != null) {
            Location s1 = this.instance.spawn1();
            Location s2 = this.instance.spawn2();
            if (this.type != Match.Type.PARTY_FFA) {
                Location base = (t.index % 2 == 0 ? s1 : s2).clone();
                int n = t.members.size();
                if (n <= 1) {
                    return base;
                } else {
                    int idx = t.members.indexOf(p.getUniqueId());
                    Vector dir = s2.toVector().subtract(s1.toVector()).setY(0);
                    if (dir.lengthSquared() < 0.01) {
                        dir = new Vector(1, 0, 0);
                    }

                    dir.normalize();
                    Vector perp = new Vector(-dir.getZ(), 0.0, dir.getX());
                    double offset = (idx - (n - 1) / 2.0) * 1.6;
                    return base.add(perp.multiply(offset));
                }
            } else {
                List<UUID> all = new ArrayList<>();

                for (MatchTeam mt : this.teams) {
                    all.addAll(mt.members);
                }

                int idx = all.indexOf(p.getUniqueId());
                Location c = this.instance.center();
                double radius = Math.max(4.0, Math.hypot(s1.getX() - c.getX(), s1.getZ() - c.getZ()) * 0.9);
                double a = (Math.PI * 2) * idx / Math.max(1, all.size());
                double x = c.getX() + Math.cos(a) * radius;
                double z = c.getZ() + Math.sin(a) * radius;
                World w = this.instance.world();
                int y = w.getHighestBlockYAt((int)Math.floor(x), (int)Math.floor(z)) + 1;
                Cuboid b = this.instance.bounds();
                if (y > b.maxY() - 2 || y < b.minY()) {
                    y = s1.getBlockY();
                }

                Location l = new Location(w, Math.floor(x) + 0.5, y, Math.floor(z) + 0.5);
                l.setYaw(Locs.yawTowards(l, c));
                return l;
            }
        } else {
            return null;
        }
    }

    private void border(Player p) {
        Cuboid b = this.instance.bounds();
        WorldBorder wb = Bukkit.createWorldBorder();
        wb.setCenter(b.centerX(), b.centerZ());
        wb.setSize(Math.max(b.sizeX(), b.sizeZ()));
        wb.setWarningDistance(0);
        wb.setDamageAmount(0.0);
        p.setWorldBorder(wb);
    }

    private void startRound(boolean first) {
        if (this.state != Match.State.ENDED) {
            this.round++;
            this.state = Match.State.STARTING;
            this.startingSince = System.currentTimeMillis();
            this.roundWinner = null;

            for (MatchTeam t : this.teams) {
                t.resetAlive();
            }

            for (Player p : this.alivePlayers()) {
                Session s = this.plugin.sessions().get(p);
                if (!first) {
                    PlayerReset.full(p, GameMode.SURVIVAL);
                    PlayerReset.unfreeze(p, s);
                    Tp.to(p, this.spawnOf(p));
                    this.border(p);
                }

                try {
                    PlayerReset.stats(p);
                    p.setGameMode(GameMode.SURVIVAL);
                } catch (RuntimeException var7) {
                    this.plugin.getLogger().log(Level.SEVERE, "Resetting " + p.getName() + " failed", (Throwable)var7);
                }

                this.giveKit(p);
                s.combatUntil = 0L;
                s.lastAttacker = null;
            }

            Opt.StartMode configured = this.forcedStart != null ? this.forcedStart : this.startMode();
            if (this.noGates && (configured == Opt.StartMode.CAGE || configured == Opt.StartMode.GATES)) {
                configured = Opt.StartMode.ELEVATOR;
            }

            Opt.StartMode mode;
            int seconds;
            if (this.isOneVsOne()
                && (this.forcedStart == Opt.StartMode.CAGE
                    || this.forcedStart == null && !this.noGates && Cfg.get(Opt.CAGE_KITS).stream().anyMatch(k -> k.equalsIgnoreCase(this.kit.id)))) {
                mode = Opt.StartMode.CAGE;
                seconds = first ? Cfg.i(Opt.CAGE_SECONDS) : Cfg.i(Opt.COUNTDOWN);
            } else if (configured != Opt.StartMode.ELEVATOR || !first && !Cfg.bool(Opt.ELEVATOR_EVERY_ROUND)) {
                if (configured == Opt.StartMode.GATES) {
                    mode = Opt.StartMode.GATES;
                    seconds = Cfg.i(Opt.COUNTDOWN);
                } else {
                    mode = Opt.StartMode.FREEZE;
                    seconds = Cfg.i(Opt.COUNTDOWN);
                }
            } else {
                mode = Opt.StartMode.ELEVATOR;
                seconds = Cfg.i(Opt.ELEVATOR_SECONDS);
            }

            this.sequence = new StartSequence(this, mode, seconds);

            try {
                this.sequence.start();
            } catch (RuntimeException var6) {
                this.plugin.getLogger().log(Level.SEVERE, "Start animation " + mode + " failed, using a frozen countdown instead", (Throwable)var6);
                this.sequence.cancel();
                this.sequence = new StartSequence(this, Opt.StartMode.FREEZE, Cfg.i(Opt.COUNTDOWN));
                this.sequence.start();
            }
        }
    }

    void giveKit(Player p) {
        PvPProfile prof = this.plugin.profiles().get(p);

        try {
            this.plugin.kits().apply(p, this.kit, prof.layouts.get(this.kit.id), this.votes);
        } catch (RuntimeException var4) {
            this.plugin.getLogger().log(Level.SEVERE, "Custom layout of " + p.getName() + " failed, giving the default kit", (Throwable)var4);
            this.plugin.kits().apply(p, this.kit, null, this.votes);
        }
    }

    private static boolean emptyInventory(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && !it.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private Opt.StartMode startMode() {
        String m = this.instance.arena.startMode;
        if (m != null && !"DEFAULT".equalsIgnoreCase(m)) {
            try {
                return Opt.StartMode.valueOf(m.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var3) {
            }
        }

        return Cfg.e(Opt.START_MODE, Opt.StartMode.class);
    }

    StartSequence startSequence() {
        return this.state == Match.State.STARTING ? this.sequence : null;
    }

    void onFight() {
        if (this.state == Match.State.STARTING) {
            this.sequence = null;
            this.state = Match.State.FIGHTING;
            if (this.fightStartedAt == 0L) {
                this.fightStartedAt = System.currentTimeMillis();
                try {
                    org.bukkit.Bukkit.getPluginManager().callEvent(new net.vortexrealm.api.MatchStartEvent(this));
                } catch (RuntimeException var9) {
                    this.plugin.getLogger().log(Level.WARNING, "A MatchStartEvent listener failed", (Throwable)var9);
                }
            }

            if (this.pingEqualizer && !this.equalizing && this.plugin.pingEqualizer() != null) {
                this.equalizing = true;
                this.plugin.pingEqualizer().start(this.id, this.participantsOnline());
            }

            boolean deathmatch = this.deathmatch();

            for (Player p : this.alivePlayers()) {
                if (emptyInventory(p)) {
                    this.giveKit(p);
                }

                if (deathmatch) {
                    Fx.deathmatch(p);
                } else {
                    Fx.fight(p);
                }
            }

            if (deathmatch) {
                for (Player p : this.audience()) {
                    p.sendMessage(Msg.prefix().append(Text.mm("<red><b>\u2694 DEATHMATCH</b> <gray>- next kill wins!")));
                }
            }
        }
    }

    public void handleDeath(Player victim, Player killer, DamageCause cause) {
        if (this.state == Match.State.FIGHTING) {
            MatchTeam vt = this.teamOf(victim.getUniqueId());
            if (vt != null && vt.alive.contains(victim.getUniqueId())) {
                if (killer != null && (!this.isParticipant(killer.getUniqueId()) || killer.equals(victim))) {
                    killer = null;
                }

                InventorySnapshot snap = new InventorySnapshot(victim, this.stats(victim.getUniqueId()), this.kit.displayName);
                this.snapshots.put(victim.getUniqueId(), snap);
                vt.alive.remove(victim.getUniqueId());
                this.stats(victim.getUniqueId()).deaths++;
                if (killer != null) {
                    this.stats(killer.getUniqueId()).kills++;
                }

                Location at = victim.getLocation();
                if (killer != null) {
                    PvPProfile kp = this.plugin.profiles().get(killer);
                    Fx.killEffect(kp.get(Setting.KILL_EFFECT), at);
                    Fx.sound(killer, "entity.experience_orb.pickup", 1.0F, 1.6F);
                }

                Component msg = killer != null
                    ? Msg.get("match.death-killed", "victim", victim.getName(), "killer", killer.getName(), "hp", Fmt.hp(killer.getHealth()))
                    : Msg.get("match.death", "victim", victim.getName(), "cause", Fmt.capitalize(cause.name()));

                for (Player p : this.audience()) {
                    p.sendMessage(Msg.prefix().append(msg));
                }

                victim.closeInventory();
                PlayerReset.stats(victim);
                victim.clearActivePotionEffects();
                victim.setVelocity(new Vector());
                List<MatchTeam> aliveTeams = this.aliveTeams();
                this.lastDeath = at.clone();
                if (aliveTeams.size() <= 1) {
                    this.endRound(aliveTeams.isEmpty() ? null : aliveTeams.get(0));
                } else {
                    victim.getInventory().clear();
                    victim.setGameMode(GameMode.SPECTATOR);
                    Location safe = at.getY() < this.instance.bounds().minY() + 2 ? this.instance.center() : at;
                    Tp.to(victim, safe);
                    this.border(victim);
                    Msg.send(victim, "match.spectating-team");
                }
            }
        }
    }

    private List<MatchTeam> aliveTeams() {
        List<MatchTeam> out = new ArrayList<>();

        for (MatchTeam t : this.teams) {
            if (!t.eliminated()) {
                out.add(t);
            }
        }

        return out;
    }

    /** Where the camera of the losing side should look: the first winner still in the arena. */
    private Location focusOf(MatchTeam winner) {
        if (winner != null) {
            for (UUID u : winner.members) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && p.getGameMode() != GameMode.SPECTATOR && this.instance != null && p.getWorld().equals(this.instance.center().getWorld())) {
                    return p.getLocation();
                }
            }
        }

        return null;
    }

    /** True while {@code p} may walk or fly between rounds (the round winner, or a loser spectating). */
    public boolean mayMove(Player p) {
        return this.state == Match.State.ROUND_END
            && (p.getGameMode() == GameMode.SPECTATOR || this.roundWinner != null && this.roundWinner.has(p.getUniqueId()));
    }

    /** The loser turns into a spectator and their camera turns smoothly toward the winner. */
    private void spectateWinner(Player p, Location focus) {
        PlayerReset.unfreeze(p, this.plugin.sessions().get(p));
        p.getInventory().clear();
        p.setVelocity(new Vector());
        p.setGameMode(GameMode.SPECTATOR);
        if (focus != null && this.instance != null && focus.getWorld().equals(p.getWorld())) {
            Location at = p.getLocation();
            Cuboid b = this.instance.bounds();
            Location look = focus.clone().add(0.0, 1.2, 0.0);
            double dist = Math.hypot(at.getX() - focus.getX(), at.getZ() - focus.getZ());
            if (!(dist > 12.0) && !(dist < 2.0) && !(at.getY() < b.minY() + 2) && !(Math.abs(at.getY() - focus.getY()) > 6.0)) {
                RankDrop.turn(p, look, 12);
            } else {
                // too far, too close or fell out: watch from a few blocks behind where they were, a little above
                Vector away = at.toVector().subtract(focus.toVector()).setY(0);
                if (away.lengthSquared() < 0.01) {
                    away = focus.getDirection().setY(0).multiply(-1);
                }

                if (away.lengthSquared() < 0.01) {
                    away = new Vector(0, 0, 1);
                }

                away.normalize();
                Location spot = focus.clone().add(away.multiply(4.5)).add(0.0, 2.2, 0.0);
                spot.setX(Math.max(b.minX() + 1.5, Math.min(b.maxX() - 0.5, spot.getX())));
                spot.setZ(Math.max(b.minZ() + 1.5, Math.min(b.maxZ() - 0.5, spot.getZ())));

                for (int i = 0; i < 6 && !spot.getBlock().isPassable(); i++) {
                    spot.add(0.0, 1.0, 0.0);
                }

                spot.setDirection(look.toVector().subtract(spot.toVector()));
                Tp.to(p, spot);
            }
        }
    }

    private void endRound(MatchTeam winner) {
        if (this.state == Match.State.FIGHTING) {
            this.state = Match.State.ROUND_END;
            if (winner != null) {
                winner.score++;
                this.scorePulse = 3;
            }

            boolean over = winner != null && winner.score >= this.roundsToWin || this.type == Match.Type.PARTY_FFA;
            this.roundWinner = winner;
            Location focus = this.focusOf(winner);
            Location death = this.lastDeath;

            for (Player p : this.participantsOnline()) {
                Session s = this.plugin.sessions().get(p);
                p.setFireTicks(0);
                if (winner == null) {
                    PlayerReset.freeze(p, s);
                } else if (winner.has(p.getUniqueId())) {
                    // the winner keeps moving freely (still invulnerable: there is no fight any more)
                    PlayerReset.unfreeze(p, s);
                } else {
                    this.spectateWinner(p, focus);
                }
            }

            if (winner != null) {
                for (Player p : this.participantsOnline()) {
                    MatchTeam mine = this.teamOf(p.getUniqueId());
                    MatchTeam other = this.otherTeam(mine);
                    String text = (mine == null ? 0 : mine.score) + "-" + (other == null ? 0 : other.score);
                    boolean won = mine == winner;
                    // the text keeps clear of the other player; the loser gets it once their camera has turned
                    Location avoid = won ? death : focus;
                    Tasks.later(won ? 4L : 14L, () -> XpOrbText.score(this.plugin, p, death, text, avoid));
                }
            }

            if (over) {
                this.end(winner, Match.EndReason.NORMAL);
            } else {
                for (Player p : this.participantsOnline()) {
                    MatchTeam mine = this.teamOf(p.getUniqueId());
                    MatchTeam other = this.otherTeam(mine);
                    boolean won = mine == winner;
                    Fx.round(p, won, mine.score, other == null ? 0 : other.score);
                }

                if (this.isOneVsOne() && winner != null) {
                    this.sendRoundReport(winner);
                } else {
                    for (Player p : this.audience()) {
                        p.sendMessage(
                            Msg.prefix()
                                .append(Msg.get("match.round-over", "round", this.round, "winner", winner == null ? "-" : this.teamName(winner)))
                                .append((Component)Component.space())
                                .append(this.scoreLine())
                        );
                    }
                }

                if (this.kit.throwBetweenRounds()) {
                    Tasks.later(Math.max(12, Cfg.i(Opt.ROUND_END_DELAY) - 28), () -> {
                        if (this.state == Match.State.ROUND_END) {
                            this.plugin.arenas().resetFast(this.instance);
                            this.throwToSpawns(() -> {
                                if (this.state == Match.State.ROUND_END) {
                                    this.startRound(false);
                                }
                            });
                        }
                    });
                } else {
                    Tasks.later(Cfg.i(Opt.ROUND_END_DELAY), () -> {
                        if (this.state == Match.State.ROUND_END) {
                            this.plugin.arenas().resetFast(this.instance);
                            this.startRound(false);
                        }
                    });
                }
            }
        }
    }

    /** MCPVP "ROUND REPORT" card: winner with the hearts they had left, loser, score and first-to, with inventories. */
    private void sendRoundReport(MatchTeam winner) {
        MatchTeam loser = this.otherTeam(winner);
        UUID w = winner.members.get(0);
        UUID l = loser.members.get(0);
        Player wp = Bukkit.getPlayer(w);
        InventorySnapshot ws = wp == null ? null : new InventorySnapshot(wp, this.stats(w), this.kit.displayName);
        InventorySnapshot ls = this.snapshots.get(l);
        if (ws != null) {
            this.plugin.snapshots().add(ws);
        }

        if (ls != null) {
            this.plugin.snapshots().add(ls);
        }

        String hearts = wp == null ? "" : " <red>" + Fmt.hp(wp.getHealth()) + "\u2764</red>";
        List<Component> lines = new ArrayList<>();
        lines.add(Msg.get("summary.line"));
        lines.add(Text.mm("<gold><b>ROUND " + this.round + " REPORT"));
        lines.add(
            Text.mm("<green>\u2714 Winner: <white>" + Text.esc(name(w)) + "</white>" + hearts + " ")
                .append(ws == null ? Component.empty() : Msg.get("summary.inventory-winner", "player", "Inventory", "id", ws.id.toString()))
        );
        lines.add(
            Text.mm("<red>\u2716 Loser: <white>" + Text.esc(name(l)) + "</white> ")
                .append(ls == null ? Component.empty() : Msg.get("summary.inventory-loser", "player", "Inventory", "id", ls.id.toString()))
        );
        lines.add(Text.mm("<gray>Score: ").append(this.scoreLine()).append(Text.mm(" <dark_gray>\u2022 <gray>First to <white>" + this.roundsToWin)));
        lines.add(Msg.get("summary.line"));
        boolean center = Cfg.bool(Opt.CENTER_CHAT);

        for (Player p : this.audience()) {
            for (int i = 0; i < lines.size(); i++) {
                Component line = lines.get(i);
                p.sendMessage(center && i > 0 && i < lines.size() - 1 ? ChatCenter.center(line) : line);
            }
        }
    }

    /** Both sides one round away from winning a first-to-N match: the next kill decides it. */
    boolean deathmatch() {
        if (this.roundsToWin <= 1 || this.teams.size() != 2) {
            return false;
        } else {
            for (MatchTeam t : this.teams) {
                if (t.score != this.roundsToWin - 1) {
                    return false;
                }
            }

            return true;
        }
    }

    private void throwToSpawns(Runnable then) {
        record Flight(Player p, Location from, Location to, double height) {
        }

        List<Flight> flights = new ArrayList<>();

        for (Player p : this.participantsOnline()) {
            Location to = this.spawnOf(p);
            if (to != null && p.getGameMode() != GameMode.SPECTATOR && to.getWorld().equals(p.getWorld())) {
                Location from = p.getLocation();
                double dist = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
                if (!(dist < 1.5)) {
                    Session s = this.plugin.sessions().get(p);
                    s.thrown = true;
                    Location facing = from.clone();
                    facing.setYaw(Locs.yawTowards(from, to));
                    facing.setPitch(-10.0F);
                    Tp.to(p, facing);
                    flights.add(new Flight(p, facing, to, Math.min(14.0, 4.0 + dist * 0.18)));
                    Snd.play(p, "entity.wind_charge.wind_burst", 0.8F, 0.9F);
                    p.getWorld().spawnParticle(Particle.GUST, from.clone().add(0.0, 0.3, 0.0), 1);
                }
            }
        }

        if (flights.isEmpty()) {
            then.run();
        } else {
            int[] tick = new int[]{0};
            BukkitTask[] task = new BukkitTask[1];
            task[0] = Tasks.timer(1L, 1L, () -> {
                int k = (int)(tick[0]++);
                boolean done = !(k < 28) || this.state != Match.State.ROUND_END;

                for (Flight f : flights) {
                    Player px = f.p();
                    if (px.isOnline()) {
                        if (done) {
                            px.setVelocity(new Vector());
                            px.setFallDistance(0.0F);
                            this.plugin.sessions().get(px).thrown = false;
                        } else {
                            this.plugin.antiCheat().exempt(px);
                            px.setFallDistance(0.0F);
                            Vector a = pathAt(f.from(), f.to(), f.height(), (double)k / 28.0);
                            Vector b = pathAt(f.from(), f.to(), f.height(), (k + 1) / 28.0);
                            Vector v = b.subtract(a);
                            if (v.length() > 3.8) {
                                v.normalize().multiply(3.8);
                            }

                            px.setVelocity(v);
                            if (k % 2 == 0) {
                                px.getWorld().spawnParticle(Particle.CLOUD, px.getLocation().add(0.0, 0.5, 0.0), 2, 0.15, 0.15, 0.15, 0.01);
                            }
                        }
                    }
                }

                if (done) {
                    Tasks.cancel(task[0]);
                    then.run();
                }
            });
        }
    }

    private static Vector pathAt(Location from, Location to, double height, double u) {
        double e = u * u * (3.0 - 2.0 * u) * 0.35 + u * 0.65;
        double x = from.getX() + (to.getX() - from.getX()) * e;
        double z = from.getZ() + (to.getZ() - from.getZ()) * e;
        double y = from.getY() + (to.getY() - from.getY()) * e + height * 4.0 * u * (1.0 - u);
        return new Vector(x, y, z);
    }

    private String teamName(MatchTeam t) {
        return t.members.size() == 1 ? name(t.members.get(0)) : Fmt.capitalize(t.color.toString()) + " Team";
    }

    private Component scoreLine() {
        StringBuilder sb = new StringBuilder("<dark_gray>(");

        for (int i = 0; i < this.teams.size(); i++) {
            if (i > 0) {
                sb.append("<dark_gray> - ");
            }

            sb.append(i == 0 ? "<green>" : "<red>").append(this.teams.get(i).score);
        }

        sb.append("<dark_gray>)");
        return Text.mm(sb.toString());
    }

    void tick() {
        if (this.state != Match.State.ENDED && this.state != Match.State.WAITING && this.state != Match.State.PREMATCH) {
            // the longest start there is (the cage start lets players edit their kit for match.cage-seconds), plus slack
            long limit = (Math.max(Math.max(Cfg.i(Opt.ELEVATOR_SECONDS), Cfg.i(Opt.COUNTDOWN)), Cfg.i(Opt.CAGE_SECONDS)) + 8) * 1000L;
            if (this.state == Match.State.STARTING && this.startingSince > 0L && System.currentTimeMillis() - this.startingSince > limit) {
                this.plugin.getLogger().warning("Match #" + this.id + " countdown got stuck; starting the fight.");
                if (this.sequence != null) {
                    this.sequence.cancel();
                }

                for (Player p : this.participantsOnline()) {
                    PlayerReset.unfreeze(p, this.plugin.sessions().get(p));
                }

                this.onFight();
            } else if (this.startedAt > 0L
                && System.currentTimeMillis() - this.startedAt > Cfg.i(Opt.MAX_DURATION) * 60000L
                && this.state == Match.State.FIGHTING) {
                this.end(null, Match.EndReason.DRAW);
            } else {
                if (this.isOneVsOne() && (this.state == Match.State.FIGHTING || this.state == Match.State.STARTING || this.state == Match.State.ROUND_END)) {
                    Player a = Bukkit.getPlayer(this.teams.get(0).members.get(0));
                    Player b = Bukkit.getPlayer(this.teams.get(1).members.get(0));

                    for (Player p : this.participantsOnline()) {
                        if (this.plugin.profiles().get(p).on(Setting.SCORE_BAR)) {
                            boolean first = this.teams.get(0).has(p.getUniqueId());
                            Player me = first ? a : b;
                            Player them = first ? b : a;
                            int my = this.teams.get(first ? 0 : 1).score;
                            int their = this.teams.get(first ? 1 : 0).score;
                            String style = this.scorePulse > 0 ? "<b>" : "";
                            Component bar = (me == null ? Text.mm("<gray>?") : Icons.head(me))
                                .append(Text.mm(" " + style + "<green>" + my + "</green> <dark_gray>-</dark_gray> <red>" + their + "</red> "))
                                .append(them == null ? Text.mm("<gray>?") : Icons.head(them));
                            p.sendActionBar(bar);
                        }
                    }

                    if (this.scorePulse > 0) {
                        this.scorePulse--;
                    }
                }

                for (UUID u : this.spectators) {
                    Player px = Bukkit.getPlayer(u);
                    if (px != null) {
                        px.sendActionBar(Msg.get("spectate.actionbar"));
                    }
                }
            }
        }
    }

    public int scoreOf(Player p) {
        MatchTeam t = this.teamOf(p.getUniqueId());
        return t == null ? 0 : t.score;
    }

    public int opponentScore(Player p) {
        MatchTeam t = this.teamOf(p.getUniqueId());
        MatchTeam o = this.otherTeam(t);
        return o == null ? 0 : o.score;
    }

    public boolean scorePulsing() {
        return this.scorePulse > 0;
    }

    public void end(MatchTeam winner, Match.EndReason reason) {
        if (this.state != Match.State.ENDED) {
            this.finalWinner = reason != Match.EndReason.CANCELLED && reason != Match.EndReason.DRAW ? winner : null;
            Match.State before = this.state;
            this.state = Match.State.ENDED;
            this.endedAt = System.currentTimeMillis();
            if (this.sequence != null) {
                this.sequence.cancel();
                this.sequence = null;
            }

            if (this.preMatch != null) {
                this.preMatch.cancel();
                this.preMatch = null;
            }

            this.releasePreMatchSlot();
            if (before == Match.State.WAITING || before == Match.State.PREMATCH) {
                reason = Match.EndReason.CANCELLED;
            }

            if (reason != Match.EndReason.CANCELLED) {
                for (Player p : this.participantsOnline()) {
                    if (!this.snapshots.containsKey(p.getUniqueId())) {
                        this.snapshots.put(p.getUniqueId(), new InventorySnapshot(p, this.stats(p.getUniqueId()), this.kit.displayName));
                    }
                }

                for (InventorySnapshot s : this.snapshots.values()) {
                    this.plugin.snapshots().add(s);
                }
            }

            RankManager.Outcome[] outcomes = null;
            UUID winnerId = null;
            UUID loserId = null;
            if (this.isOneVsOne() && winner != null && reason != Match.EndReason.CANCELLED && reason != Match.EndReason.DRAW) {
                MatchTeam loser = this.otherTeam(winner);
                winnerId = winner.members.get(0);
                loserId = loser.members.get(0);
                PvPProfile wp = this.plugin.profiles().getIfLoaded(winnerId);
                PvPProfile lp = this.plugin.profiles().getIfLoaded(loserId);
                if (wp != null) {
                    KitStats var10000 = wp.stats(this.kit.id);
                    var10000.roundsWon = var10000.roundsWon + winner.score;
                    var10000 = wp.stats(this.kit.id);
                    var10000.roundsLost = var10000.roundsLost + loser.score;
                    wp.dirty = true;
                }

                if (lp != null) {
                    KitStats var41 = lp.stats(this.kit.id);
                    var41.roundsWon = var41.roundsWon + loser.score;
                    var41 = lp.stats(this.kit.id);
                    var41.roundsLost = var41.roundsLost + winner.score;
                    lp.dirty = true;
                }

                if (wp != null && lp != null) {
                    if (this.ranked) {
                        outcomes = this.plugin
                            .ranks()
                            .applyRanked(wp, lp, this.kit, winner.score, loser.score, this.tierChallenge ? Cfg.d(Opt.TIER_CHALLENGE_MULTIPLIER) : 1.0);
                    } else {
                        wp.stats(this.kit.id).unrankedWins++;
                        lp.stats(this.kit.id).unrankedLosses++;
                        wp.dirty = true;
                        lp.dirty = true;

                        try {
                            this.skillCheck(wp, lp, winnerId, loserId, winner.score, loser.score);
                        } catch (RuntimeException var19) {
                            this.plugin.getLogger().log(Level.WARNING, "Skill check failed", (Throwable)var19);
                        }
                    }
                }

                InventorySnapshot ws = this.snapshots.get(winnerId);
                InventorySnapshot ls = this.snapshots.get(loserId);
                if (ws != null && ls != null) {
                    ws.opponentSnapshot = ls.id;
                    ls.opponentSnapshot = ws.id;
                }

                String score = winner.score + "-" + loser.score;
                int delta = outcomes == null ? 0 : outcomes[0].delta();
                int dur = (int)this.durationSeconds();
                UUID logWinner = winnerId;
                UUID logLoser = loserId;
                this.plugin.db().run(c -> this.plugin.db().logMatch(c, this.kit.id, this.ranked, logWinner, logLoser, score, delta, dur));
            }

            if (this.isOneVsOne()) {
                UUID a = this.teams.get(0).members.get(0);
                UUID b = this.teams.get(1).members.get(0);
                Session sa = this.plugin.sessions().get(a);
                Session sb = this.plugin.sessions().get(b);
                long now = System.currentTimeMillis();
                if (sa != null) {
                    sa.lastOpponent = b;
                    sa.lastOpponentTime = now;
                    sa.lastMatchKit = this.kit.id;
                }

                if (sb != null) {
                    sb.lastOpponent = a;
                    sb.lastOpponentTime = now;
                    sb.lastMatchKit = this.kit.id;
                }
            }

            try {
                org.bukkit.Bukkit.getPluginManager()
                    .callEvent(new net.vortexrealm.api.MatchEndEvent(this, this.finalWinner, reason.name(), winnerId, loserId, outcomes));
            } catch (RuntimeException var40) {
                this.plugin.getLogger().log(Level.WARNING, "A MatchEndEvent listener failed", (Throwable)var40);
            }

            for (Player px : this.participantsOnline()) {
                try {
                    MatchTeam mine = this.teamOf(px.getUniqueId());
                    MatchTeam other = this.otherTeam(mine);
                    int my = mine.score;
                    int their = other == null ? 0 : other.score;
                    boolean free = winner != null && mine == winner && reason != Match.EndReason.CANCELLED && reason != Match.EndReason.DRAW;
                    if (free) {
                        PlayerReset.unfreeze(px, this.plugin.sessions().get(px));
                    } else if (px.getGameMode() != GameMode.SPECTATOR) {
                        PlayerReset.freeze(px, this.plugin.sessions().get(px));
                    }

                    px.setFireTicks(0);
                    if (reason != Match.EndReason.DRAW && (winner != null || reason == Match.EndReason.CANCELLED)) {
                        if (reason != Match.EndReason.CANCELLED) {
                            if (mine == winner) {
                                Fx.victory(px, my, their);
                                Toasts.win(px, this.kit, this.ranked);
                                if (Cfg.bool(Opt.TOTEM_ON_WIN)) {
                                    Tasks.later(6L, () -> TotemPop.play(px, new ItemStack(Material.TOTEM_OF_UNDYING)));
                                }

                                if (Cfg.bool(Opt.VICTORY_FIREWORKS) && this.plugin.profiles().get(px).on(Setting.FIREWORKS)) {
                                    Fx.fireworks(px.getLocation(), 4, Color.LIME, Color.YELLOW, Color.ORANGE);
                                }
                            } else {
                                Fx.defeat(px, my, their);
                                if (px.getGameMode() != GameMode.SPECTATOR) {
                                    this.spectateWinner(px, this.focusOf(winner));
                                }
                            }
                        }
                    } else {
                        Fx.draw(px);
                    }
                } catch (RuntimeException var20) {
                    this.plugin.getLogger().log(Level.SEVERE, "End-of-match effects failed for " + px.getName(), (Throwable)var20);
                }
            }

            int extra = 0;
            if (outcomes != null && reason != Match.EndReason.CANCELLED) {
                try {
                    extra = this.celebrate(outcomes, winnerId, loserId);
                } catch (RuntimeException var18) {
                    this.plugin.getLogger().log(Level.SEVERE, "End-of-match animations failed", (Throwable)var18);
                }
            }

            extra = Math.max(extra, this.skillExtra);
            if (reason != Match.EndReason.CANCELLED) {
                try {
                    this.sendSummary(winner, reason, outcomes, winnerId, loserId);
                } catch (RuntimeException var17) {
                    this.plugin.getLogger().log(Level.SEVERE, "Match summary failed", (Throwable)var17);
                }
            }

            RankManager.Outcome[] fo = outcomes;
            UUID fw = winnerId;
            UUID fl = loserId;
            if (reason == Match.EndReason.CANCELLED) {
                this.finishAndCleanup(null, null, null, true);
            } else {
                long delay = Cfg.i(Opt.END_DELAY) + extra;
                this.cleanupDueMs = delay * 50L;
                Tasks.later(delay, () -> this.finishAndCleanup(fo, fw, fl, false));
            }
        }
    }

    private void skillCheck(PvPProfile wp, PvPProfile lp, UUID w, UUID l, int ws, int ls) {
        int wRating = this.plugin.ranks().skillRating(wp, this.kit);
        int lRating = this.plugin.ranks().skillRating(lp, this.kit);
        this.report(w, this.plugin.ranks().skillCheck(wp, this.kit, this.performance(w, l, ws, ls, true), lRating));
        this.report(l, this.plugin.ranks().skillCheck(lp, this.kit, this.performance(l, w, ls, ws, false), wRating));
    }

    private double performance(UUID me, UUID them, int my, int their, boolean won) {
        MatchStats a = this.stats(me);
        MatchStats b = this.stats(them);
        double dmg = a.damageDealt + b.damageDealt > 0.0 ? a.damageDealt / (a.damageDealt + b.damageDealt) : 0.5;
        double hits = a.hits + b.hits > 0 ? (double)a.hits / (a.hits + b.hits) : 0.5;
        double combo = Math.min(1.0, a.longestCombo / 6.0);
        double rounds = my + their > 0 ? (double)my / (my + their) : (won ? 1.0 : 0.0);
        return 0.35 * (won ? 1 : 0) + 0.15 * rounds + 0.2 * dmg + 0.15 * hits + 0.15 * combo;
    }

    private void report(UUID u, RankManager.SkillResult r) {
        Player p = Bukkit.getPlayer(u);
        if (p != null && r != null) {
            if (r.placed() == null) {
                StringBuilder bar = new StringBuilder();

                for (int i = 0; i < r.need(); i++) {
                    bar.append(i < r.done() ? "<#FFB547>■" : "<dark_gray>■");
                }

                int left = r.need() - r.done();
                Tasks.later(
                    30L,
                    () -> {
                        if (p.isOnline()) {
                            p.sendMessage(
                                Text.mm("<gold><b>Skill check</b> <gray>")
                                    .append(this.kit.label())
                                    .append(
                                        Text.mm(
                                            " <white>"
                                                + r.done()
                                                + "/"
                                                + r.need()
                                                + " "
                                                + bar
                                                + " <gray>- "
                                                + left
                                                + " more unranked "
                                                + (left == 1 ? "duel" : "duels")
                                                + " and we'll place you at your tier."
                                        )
                                    )
                            );
                        }
                    }
                );
            } else {
                this.skillExtra = Math.max(this.skillExtra, 200);
                Tier tier = r.placed();
                LiveFeed.add("placed", p.getName(), tier.label, tier.color, this.kit.displayName);
                Vector facing = this.instance != null && this.instance.center() != null
                    ? this.instance.center().toVector().subtract(p.getLocation().toVector())
                    : p.getEyeLocation().getDirection();
                Tasks.later(
                    28L,
                    () -> {
                        if (p.isOnline() && this.plugin.sessions().get(p).match == this) {
                            Toasts.rankUp(p, tier, true);
                            p.sendMessage(
                                Text.mm("<gold><b>Skill check done!</b></gold> <gray>From how you played, you start at ")
                                    .append(Text.mm(tier.mm()))
                                    .append(Text.mm(" <gray>in "))
                                    .append(this.kit.label())
                                    .append(Text.mm(" <dark_gray>(" + r.points() + " pts)</dark_gray><gray>. Play ranked to climb!"))
                            );
                            Opt.RankUpStyle style = Cfg.e(Opt.RANKUP_STYLE, Opt.RankUpStyle.class);
                            if (style == Opt.RankUpStyle.SCREEN && this.plugin.hasPack(p)) {
                                RankScreen.play(this.plugin, p, null, tier);
                            } else if (style == Opt.RankUpStyle.BLOCKS) {
                                TierLetters.play(this.plugin, p, null, tier, facing);
                            } else if (style == Opt.RankUpStyle.SMOOTH) {
                                RankReveal.play(this.plugin, p, null, tier, facing);
                            } else {
                                RankDrop.play(this.plugin, p, null, tier, facing);
                            }

                            try {
                                this.plugin.boards().refreshNametag(p);
                            } catch (RuntimeException var7x) {
                            }
                        }
                    }
                );
            }
        }
    }

    private int celebrate(RankManager.Outcome[] outcomes, UUID winnerId, UUID loserId) {
        Player wp = Bukkit.getPlayer(winnerId);
        Player lp = Bukkit.getPlayer(loserId);
        boolean rankUp = false;

        for (RankManager.RankEvent e : outcomes[0].events()) {
            rankUp |= e.type() == RankManager.EventType.RANK_UP || e.type() == RankManager.EventType.PLACED;
        }

        UUID[] who = new UUID[]{winnerId, loserId};

        for (int i = 0; i < 2; i++) {
            for (RankManager.RankEvent e : outcomes[i].events()) {
                if ((e.type() == RankManager.EventType.RANK_UP || e.type() == RankManager.EventType.PLACED) && e.to() != null) {
                    LiveFeed.add(e.type() == RankManager.EventType.PLACED ? "placed" : "rankup", name(who[i]), e.to().label, e.to().color, this.kit.displayName);
                }
            }
        }

        if (wp != null && !rankUp) {
            Tasks.later(30L, () -> PointsFx.show(wp, outcomes[0].delta(), lp == null ? null : lp.getLocation()));
        }

        if (lp != null) {
            Tasks.later(36L, () -> PointsFx.show(lp, outcomes[1].delta(), wp == null ? null : wp.getLocation()));
        }

        if (wp == null) {
            return 0;
        } else {
            for (RankManager.RankEvent ex : outcomes[0].events()) {
                if (ex.type() == RankManager.EventType.RANK_UP || ex.type() == RankManager.EventType.PLACED) {
                    this.celebratedHere.add(winnerId);
                    Tasks.later(28L, () -> {
                        if (wp.isOnline() && this.plugin.sessions().get(wp).match == this) {
                            // the winner walked freely until now; the rank-up plays where they stand
                            PlayerReset.freeze(wp, this.plugin.sessions().get(wp));
                            wp.setVelocity(new Vector());
                            Vector facing = this.instance != null && this.instance.center() != null
                                ? this.instance.center().toVector().subtract(wp.getLocation().toVector())
                                : wp.getEyeLocation().getDirection();
                            Opt.RankUpStyle style = Cfg.e(Opt.RANKUP_STYLE, Opt.RankUpStyle.class);
                            if (style == Opt.RankUpStyle.DROP) {
                                RankDrop.play(this.plugin, wp, ex.from(), ex.to(), facing);
                            } else if (style == Opt.RankUpStyle.SCREEN && this.plugin.hasPack(wp)) {
                                RankScreen.play(this.plugin, wp, ex.from(), ex.to());
                            } else if (style == Opt.RankUpStyle.BLOCKS) {
                                TierLetters.play(this.plugin, wp, ex.from(), ex.to(), facing);
                            } else {
                                RankReveal.play(this.plugin, wp, ex.from(), ex.to(), facing);
                            }
                        }
                    });
                    return Cfg.e(Opt.RANKUP_STYLE, Opt.RankUpStyle.class) == Opt.RankUpStyle.DROP ? 200 : 140;
                }
            }

            return 0;
        }
    }

    private void sendSummary(MatchTeam winner, Match.EndReason reason, RankManager.Outcome[] outcomes, UUID winnerId, UUID loserId) {
        List<Component> lines = new ArrayList<>();
        lines.add(Msg.get("summary.line"));
        lines.add(Msg.get("summary.header"));
        if (winner == null) {
            lines.add(Msg.get("summary.draw"));
        } else if (winnerId != null) {
            lines.add(Msg.get(reason == Match.EndReason.FORFEIT ? "summary.forfeit" : "summary.result", "winner", name(winnerId), "loser", name(loserId)));
        } else {
            lines.add(Msg.get("summary.team-result", "winner", this.teamName(winner)));
        }

        lines.add(
            Msg.get(
                "summary.details",
                "kit",
                this.kit.label(),
                "mode",
                this.ranked ? Msg.get("summary.ranked") : Msg.get("summary.unranked"),
                "score",
                this.scoreLine(),
                "time",
                Fmt.clock(this.fightSeconds())
            )
        );
        if (outcomes != null) {
            lines.add(
                Msg.get(
                    "summary.elo",
                    "winner",
                    name(winnerId),
                    "wdelta",
                    Fmt.signed(outcomes[0].delta()),
                    "welo",
                    Fmt.num(outcomes[0].newElo()),
                    "loser",
                    name(loserId),
                    "ldelta",
                    Fmt.signed(outcomes[1].delta()),
                    "lelo",
                    Fmt.num(outcomes[1].newElo())
                )
            );
        }

        Component inv = Msg.get("summary.inventories");

        for (MatchTeam t : this.teams) {
            for (UUID u : t.members) {
                InventorySnapshot s = this.snapshots.get(u);
                if (s != null) {
                    inv = inv.append((Component)Component.space())
                        .append(Msg.get(t == winner ? "summary.inventory-winner" : "summary.inventory-loser", "player", s.name, "id", s.id.toString()));
                }
            }
        }

        lines.add(inv);
        lines.add(Msg.get("summary.line"));
        boolean center = Cfg.bool(Opt.CENTER_CHAT);

        for (Player p : this.audience()) {
            for (int i = 0; i < lines.size(); i++) {
                Component line = lines.get(i);
                p.sendMessage(center && i > 0 && i < lines.size() - 1 ? ChatCenter.center(line) : line);
            }

            if (this.isOneVsOne() && this.isParticipant(p.getUniqueId())) {
                Component buttons = Component.empty();
                if (Cfg.bool(Opt.REMATCH)) {
                    buttons = buttons.append(Msg.get("summary.rematch-button"));
                }

                if (this.fromQueue) {
                    buttons = buttons.append((Component)Component.text("   "))
                        .append(Msg.get("summary.requeue-button", "kit", this.kit.id, "mode", this.ranked ? "ranked" : "unranked"));
                }

                if (!buttons.equals(Component.empty())) {
                    p.sendMessage(center ? ChatCenter.center(buttons) : buttons);
                }
            }
        }

        if (outcomes != null) {
            Player wp = Bukkit.getPlayer(winnerId);
            Player lp = Bukkit.getPlayer(loserId);
            if (wp != null) {
                this.plugin.ranks().announce(wp, this.kit, outcomes[0], this.celebratedHere.contains(winnerId));
            }

            if (lp != null) {
                this.plugin.ranks().announce(lp, this.kit, outcomes[1], false);
            }
        }
    }

    private void finishAndCleanup(RankManager.Outcome[] outcomes, UUID winnerId, UUID loserId, boolean cancelled) {
        if (!this.cleaned) {
            this.cleaned = true;
            List<Player> players = this.participantsOnline();

            for (Player p : players) {
                Session s = this.plugin.sessions().get(p);
                if (s.match == this) {
                    this.safeLobby(p);
                }
            }

            for (UUID u : new ArrayList<>(this.spectators)) {
                Player px = Bukkit.getPlayer(u);
                if (px != null && this.plugin.sessions().get(px).spectating == this) {
                    this.safeLobby(px);
                }
            }

            this.spectators.clear();

            try {
                if (this.instance != null) {
                    this.plugin.arenas().release(this.instance);
                }
            } catch (RuntimeException var16) {
                this.plugin.getLogger().log(Level.SEVERE, "Releasing arena of match #" + this.id + " failed", (Throwable)var16);
            }

            this.instance = null;
            if (this.equalizing && this.plugin.pingEqualizer() != null) {
                this.plugin.pingEqualizer().stop(this.id);
            }

            this.plugin.matches().remove(this);
            if (this.onFinish != null) {
                try {
                    this.onFinish.accept(this, cancelled ? null : this.finalWinner);
                } catch (RuntimeException var15) {
                    this.plugin.getLogger().log(Level.SEVERE, "Match #" + this.id + " finish hook failed", (Throwable)var15);
                }
            }

            if (!cancelled) {
                for (Player px : players) {
                    if (this.plugin.sessions().state(px) == PlayerState.LOBBY) {
                        PvPProfile prof = this.plugin.profiles().get(px);
                        RankManager.Outcome o = null;
                        if (outcomes != null) {
                            o = px.getUniqueId().equals(winnerId) ? outcomes[0] : (px.getUniqueId().equals(loserId) ? outcomes[1] : null);
                        }

                        KitStats ks = prof.statsOrNull(this.kit.id);
                        if (ks == null) {
                            boolean var10000 = false;
                        } else {
                            int var24 = ks.unrankedWins + ks.unrankedLosses;
                        }

                        if (this.isOneVsOne()) {
                            boolean won = px.getUniqueId().equals(winnerId);
                            Tasks.later(25L, () -> this.motivate(px, won));
                        }

                        boolean unlocked = o != null && RankManager.hasOffer(o.events());
                        if (unlocked && Cfg.bool(Opt.PROMOTION)) {
                            KitStats kst = prof.statsOrNull(this.kit.id);
                            Tier to = kst == null ? null : Tier.byIndex(kst.promoTarget);
                            Tasks.later(
                                30L,
                                () -> {
                                    if (px.isOnline() && to != null) {
                                        Fx.shine(
                                            px,
                                            "TIER TEST UNLOCKED",
                                            Fx.GOLD,
                                            Text.mm("<gray>Queue ")
                                                .append(this.kit.label())
                                                .append(Text.mm("<gray> and accept the challenge for "))
                                                .append(Text.mm(to.mm())),
                                            50
                                        );
                                        px.sendMessage(
                                            Text.mm(
                                                    "<gold>⚠ <b>Tier Test Challenge unlocked!</b></gold> <gray>Win one match (first to "
                                                        + Cfg.i(Opt.TIER_TEST_FIRST_TO)
                                                        + ") against a player "
                                                        + (Cfg.bool(Opt.TIER_TEST_ABOVE) ? "who already has it" : "of your tier")
                                                        + " to reach "
                                                )
                                                .append(Text.mm(to.mm()))
                                                .append(
                                                    Text.mm(
                                                        "<gray>. <green><b><click:run_command:'/rankup "
                                                            + this.kit.id
                                                            + "'><hover:show_text:'<gray>Open the challenge'>[TAKE IT]</hover></click>"
                                                    )
                                                )
                                        );
                                        Snd.play(px, "ui.toast.challenge_complete", 0.7F, 1.3F);
                                    }
                                }
                            );
                        } else if (o != null && o.events().stream().anyMatch(ev -> ev.type() == RankManager.EventType.DEFENSE_DUE)) {
                            Tasks.later(30L, () -> {
                                if (px.isOnline() && this.plugin.sessions().state(px) == PlayerState.LOBBY) {
                                    Fx.shine(px, "TIER DEFENSE", Fx.GOLD, Text.mm("<gray>Queue ranked ").append(this.kit.label()).append(Text.mm(" <gray>to defend your tier")), 50);
                                    Snd.play(px, "block.bell.use", 0.7F, 0.8F);
                                }
                            });
                        } else if (this.fromQueue && prof.on(Setting.AUTO_REQUEUE) && this.plugin.parties().of(px) == null) {
                            Tasks.later(5L, () -> {
                                if (this.plugin.sessions().state(px) == PlayerState.LOBBY) {
                                    this.plugin.queues().join(px, this.kit, this.ranked, true);
                                }
                            });
                        }
                    }
                }
            }
        }
    }

    private void motivate(Player p, boolean won) {
        if (p.isOnline() && this.plugin.sessions().state(p) != PlayerState.OUTSIDE) {
            PvPProfile prof = this.plugin.profiles().get(p);
            KitStats ks = prof.statsOrNull(this.kit.id);
            int pick = 1 + ThreadLocalRandom.current().nextInt(3);
            Msg.send(p, (won ? "motivate.win-" : "motivate.loss-") + pick, "kit", this.kit.label(), "streak", ks == null ? 0 : ks.streak);
            if (this.ranked && ks != null && ks.tier() != null) {
                Tier t = ks.tier();
                if (ks.promoState == 1 || ks.promoState == 2) {
                    Tier to = Tier.byIndex(ks.promoTarget);
                    p.sendMessage(
                        Text.mm("<gold>⚠ Tier Test Challenge ready</gold> <gray>→ ")
                            .append(Text.mm(to == null ? "?" : to.mm()))
                            .append(Text.mm(" <dark_gray>(queue "))
                            .append(this.kit.label())
                            .append(Text.mm("<dark_gray>)"))
                    );
                } else if (t.next() != null) {
                    int need = Math.max(0, t.next().threshold() - ks.elo);
                    Msg.send(p, "motivate.next-tier", "need", need, "to", Text.mm(t.next().mm()), "kit", this.kit.label());
                    Fx.progressBar(p, "TO " + t.next().label, ks.elo - t.threshold(), t.next().threshold() - t.threshold());
                } else {
                    Msg.send(p, "motivate.top", "kit", this.kit.label());
                }
            } else if (!this.ranked && this.kit.ranked && Cfg.bool(Opt.RANKED_ENABLED)) {
                int games = ks == null ? 0 : ks.unrankedWins + ks.unrankedLosses;
                int every = Cfg.i(Opt.RANKED_INVITE_EVERY);
                int left = every - games % every;
                if (left > 0 && left < every && (ks == null || ks.tier() == null)) {
                    Msg.send(p, "motivate.test-soon", "left", left, "kit", this.kit.label());
                    Fx.progressBar(p, "TO TIER TEST", every - left, every);
                }
            }
        }
    }

    private void safeLobby(Player p) {
        try {
            this.plugin.lobby().sendToLobby(p);
        } catch (RuntimeException var6) {
            this.plugin.getLogger().log(Level.SEVERE, "Sending " + p.getName() + " to the lobby failed", (Throwable)var6);
            Session s = this.plugin.sessions().get(p);
            s.match = null;
            s.spectating = null;
            s.state = PlayerState.LOBBY;
            PlayerReset.unfreeze(p, s);
            p.setGameMode(GameMode.ADVENTURE);
            Tp.to(p, this.plugin.lobby().lobby());

            try {
                this.plugin.lobby().giveLobbyItems(p);
            } catch (RuntimeException var5) {
            }
        }
    }

    void forceCleanupIfStale(long endedForMs) {
        // never before the scheduled cleanup (rank-up and skill animations add time to the victory screen)
        long limit = Math.max(endedForMs, this.cleanupDueMs + 10000L);
        if (this.state == Match.State.ENDED && !this.cleaned && this.endedAt > 0L && System.currentTimeMillis() - this.endedAt > limit) {
            this.plugin.getLogger().warning("Match #" + this.id + " cleanup was late; forcing it.");
            this.finishAndCleanup(null, null, null, true);
        }
    }

    public void cancel(String messageKey, boolean requeue) {
        if (this.state != Match.State.ENDED) {
            List<Player> players = this.participantsOnline();
            this.end(null, Match.EndReason.CANCELLED);

            for (Player p : players) {
                Msg.send(p, messageKey);
                if (requeue
                    && this.fromQueue
                    && this.plugin.sessions().state(p) == PlayerState.LOBBY
                    && this.plugin.queues().join(p, this.kit, this.ranked, true)) {
                    Msg.send(p, "queue.requeued");
                }
            }
        }
    }

    public void forfeit(Player p, boolean quitting) {
        MatchTeam t = this.teamOf(p.getUniqueId());
        if (t != null) {
            if (quitting && this.state != Match.State.ENDED && this.state != Match.State.WAITING && this.isOneVsOne()) {
                String opp = null;

                for (Player o : this.participantsOnline()) {
                    if (!o.equals(p)) {
                        opp = o.getName();
                    }
                }

                if (this.plugin.penalties() != null) {
                    this.plugin.penalties().leftMatch(p, this.ranked, opp);
                }
            }

            Session s = this.plugin.sessions().get(p);
            if (this.state == Match.State.ENDED) {
                if (!quitting && s.match == this) {
                    this.plugin.lobby().sendToLobby(p);
                }
            } else if (this.state != Match.State.WAITING && this.state != Match.State.PREMATCH) {
                t.left.add(p.getUniqueId());
                t.alive.remove(p.getUniqueId());
                if (p.isOnline() && !this.snapshots.containsKey(p.getUniqueId())) {
                    this.snapshots.put(p.getUniqueId(), new InventorySnapshot(p, this.stats(p.getUniqueId()), this.kit.displayName));
                }

                for (Player ox : this.audience()) {
                    if (!ox.equals(p)) {
                        Msg.send(ox, "match.player-left", "player", p.getName());
                    }
                }

                s.match = null;
                if (!quitting) {
                    this.plugin.lobby().sendToLobby(p);
                }

                List<MatchTeam> remaining = new ArrayList<>();

                for (MatchTeam mt : this.teams) {
                    if (!mt.allLeft()) {
                        remaining.add(mt);
                    }
                }

                if (remaining.size() <= 1) {
                    this.end(remaining.isEmpty() ? null : remaining.get(0), Match.EndReason.FORFEIT);
                } else {
                    if (this.state == Match.State.FIGHTING && this.aliveTeams().size() <= 1) {
                        List<MatchTeam> alive = this.aliveTeams();
                        this.endRound(alive.isEmpty() ? null : alive.get(0));
                    }
                }
            } else {
                t.left.add(p.getUniqueId());

                for (Player other : this.participantsOnline()) {
                    if (!other.equals(p)) {
                        Msg.send(other, "match.opponent-left-early");
                    }
                }

                s.match = null;
                if (!quitting) {
                    this.plugin.lobby().sendToLobby(p);
                }

                this.cancel("match.cancelled", true);
            }
        }
    }

    public boolean addSpectator(Player p) {
        if (this.instance != null && this.state != Match.State.ENDED && this.state != Match.State.WAITING && this.state != Match.State.PREMATCH) {
            Session s = this.plugin.sessions().get(p);
            this.spectators.add(p.getUniqueId());
            s.state = PlayerState.SPECTATING;
            s.spectating = this;
            PlayerReset.full(p, GameMode.SPECTATOR);
            Tp.to(p, this.instance.center());
            this.border(p);
            this.plugin.lobby().refreshVisibilityAll();
            if (this.plugin.spectatorFeed() != null) {
                this.plugin.spectatorFeed().joined(this, p);
            } else {
                for (Player o : this.participantsOnline()) {
                    Msg.send(o, "spectate.joined-notify", "player", p.getName());
                }
            }

            return true;
        } else {
            return false;
        }
    }

    public void removeSpectator(Player p, boolean toLobby) {
        boolean was = this.spectators.remove(p.getUniqueId());
        if (was && this.plugin.spectatorFeed() != null) {
            this.plugin.spectatorFeed().left(this, p);
        }

        Session s = this.plugin.sessions().get(p);
        if (s.spectating == this) {
            s.spectating = null;
        }

        if (toLobby) {
            this.plugin.lobby().sendToLobby(p);
        }
    }

    public boolean allowsSpectators() {
        if (!Cfg.bool(Opt.ALLOW_SPECTATORS) || this.spectatorsBlocked) {
            return false;
        } else {
            for (Player p : this.participantsOnline()) {
                if (!this.plugin.profiles().get(p).on(Setting.ALLOW_SPECTATORS)) {
                    return false;
                }
            }

            return true;
        }
    }

    public List<KitVote> votes() {
        return Collections.unmodifiableList(this.votes);
    }

    public long createdAt() {
        return this.createdAt;
    }

    public static enum EndReason {
        NORMAL,
        FORFEIT,
        DRAW,
        CANCELLED;
    }

    public static enum State {
        WAITING,
        PREMATCH,
        STARTING,
        FIGHTING,
        ROUND_END,
        ENDED;
    }

    public static enum Type {
        DUEL,
        PARTY_SPLIT,
        PARTY_FFA;
    }
}
