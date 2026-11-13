package net.vortexrealm.match;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.vortexrealm.util.TextWidth;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.arena.Arena;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Msg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.deco.HoloFx;
import net.vortexrealm.fx.anim.Anim;
import net.vortexrealm.fx.anim.Elem;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.kit.KitVote;
import net.vortexrealm.session.PlayerReset;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Session;
import net.vortexrealm.session.Tp;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

/**
 * The MCPVP map vote. Each player gets their own board floating in front of them, wrapped around the opponent:
 * an "A VS B" title with a light sweeping through both names in their colours, a state header with the turn timer,
 * and the maps as small tilted cubes on two arcs. Looking at a map grows it and turns its label into "CLICK TO BAN";
 * the opponent's hover shows up live as "THEIR HOVER". Banned maps shrink and join a slow orbit around the opponent,
 * the rest slide into the free places, the last player picks the final map (or it is rolled), the winner tumbles in
 * the middle, preference votes follow, and at the end the orbit widens, falls back and fades away.
 * <p>Every moving part is an {@link Elem}: state is computed per tick and streamed with interpolation, so nothing
 * snaps or skips.
 */
public final class PreMatch {
    private static final BitSet SLOTS = new BitSet();
    public static final String ITEM_BAN = "pm_ban";
    public static final String ITEM_YES = "pm_yes";
    public static final String ITEM_NO = "pm_no";
    private static final double BOARD = 2.6;
    private static final double ORBIT_DIST = 3.7;
    private static final double ORBIT_RADIUS = 0.46;
    private static final double ORBIT_SPEED = Math.toRadians(30.0) / 20.0;
    private static final float CUBE = 0.34F;
    private static final float CUBE_HOVER = 0.68F;
    private static final float CUBE_ORBIT = 0.12F;
    /** Height of one line of a map label (scale 0.42). */
    private static final double LABEL_LINE = 0.11;
    /** Wrap width of a map label in pixels: the longest wrapped half of a name fits, nothing wraps a second time. */
    private static final int LABEL_WIDTH = 120;
    private static final float LABEL_SCALE = 0.42F;
    // 3D map previews: footprint sizes in blocks
    private static final float MODEL = 0.52F;
    private static final float MODEL_HOVER = 0.8F;
    private static final float MODEL_THEIRS = 0.7F;
    private static final float MODEL_FINAL = 0.66F;
    private static final float MODEL_PICKED = 1.0F;
    private static final float MODEL_BADGE = 0.3F;
    private static final int BG_IDLE = argb(150, 0, 0, 0);
    private static final int BG_HOVER_FROM = argb(255, 25, 52, 27);
    private static final int BG_HOVER = argb(255, 88, 231, 17);
    private static final int BG_THEIRS = argb(255, 61, 166, 100);
    private static final int BG_TURN = argb(255, 77, 198, 17);
    private static final int BG_WAIT = argb(192, 0, 0, 0);
    private static final int BG_LOW = argb(255, 120, 29, 34);
    private static final int BG_LOCKED = argb(255, 35, 146, 77);
    private static final int BG_TITLE = argb(255, 52, 148, 51);
    private final Match match;
    private final VortexRealm plugin;
    private final List<Arena> maps;
    private final List<Player> players;
    private final List<PreMatch.Icon> icons = new ArrayList<>();
    private final Map<UUID, PreMatch.Board> boards = new LinkedHashMap<>();
    private final Map<UUID, Integer> bansLeft = new HashMap<>();
    private final Map<UUID, Integer> bansTotal = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final List<KitVote> passed = new ArrayList<>();
    private final Map<UUID, Boolean> votes = new HashMap<>();
    private List<KitVote> pending = List.of();
    private int voteIndex = -1;
    private PreMatch.Phase phase = PreMatch.Phase.BAN;
    private int phaseStart;
    private int ticks;
    private int slot = -1;
    private Location base;
    private BukkitTask task;
    private Arena chosen;
    private PreMatch.Icon pickIcon;
    private boolean failed;
    private boolean turns;
    private int turnIndex;
    private int turnStart;
    private int turnSeconds;
    private boolean firstBan = true;
    private Player picker;
    private UUID lastBanner;
    private int banOrder;
    private List<PreMatch.Icon> rollPool = List.of();
    private int rollIndex;
    private int rollNext;
    private int rollStep;
    private PreMatch.Icon rollLit;
    private Boolean voteResult;
    private int exitAt = -1;
    /** The boards are spawned (after the clients have the chunks around their seats). */
    private boolean boardsUp;
    private int startBans;
    /** Everything orbiting (banned maps and lost vote options), in the order they joined. */
    private final List<PreMatch.Icon> orbiters = new ArrayList<>();

    PreMatch(Match match, List<Arena> maps, List<Player> players) {
        this.match = match;
        this.plugin = VortexRealm.get();
        this.maps = maps;
        this.players = players;
    }

    private static int argb(int a, int r, int g, int b) {
        return a << 24 | r << 16 | g << 8 | b;
    }

    // ------------------------------------------------------------------ lifecycle

    void start() {
        World w = this.plugin.worlds().practiceWorld();
        synchronized (SLOTS) {
            this.slot = SLOTS.nextClearBit(0);
            SLOTS.set(this.slot);
        }

        this.base = new Location(w, this.slot * 96 + 48.5, 180.0, -4096.5);
        this.buildPlatform(this.base.clone().add(-5.0, -1.0, 0.0));
        this.buildPlatform(this.base.clone().add(5.0, -1.0, 0.0));
        int bans = Math.min(Cfg.i(Opt.BANS_PER_PLAYER), Math.max(0, (this.maps.size() - 2) / 2));

        for (int i = 0; i < this.players.size(); i++) {
            Player p = this.players.get(i);
            Location pos = this.seat(p);
            pos.setPitch(-6.0F);
            PlayerReset.full(p, GameMode.ADVENTURE);
            Tp.to(p, pos);
            PlayerReset.freeze(p, this.plugin.sessions().get(p));
            this.bansLeft.put(p.getUniqueId(), bans);
            this.bansTotal.put(p.getUniqueId(), bans);
            this.giveBanItems(p);
        }

        for (int i = 0; i < this.maps.size(); i++) {
            this.icons.add(new PreMatch.Icon(this.maps.get(i), i));
        }

        this.turns = Cfg.bool(Opt.MAP_BAN_TURNS) && this.players.size() == 2;
        this.turnIndex = ThreadLocalRandom.current().nextInt(Math.max(1, this.players.size()));
        this.turnSeconds = Cfg.i(Opt.MAP_BAN_TURN_SECONDS) + 2;

        for (Player p : this.players) {
            this.boards.put(p.getUniqueId(), new PreMatch.Board(p, this.players.indexOf(p)));
        }

        this.phase = PreMatch.Phase.BAN;
        this.phaseStart = 0;
        this.startBans = bans;
        this.task = Tasks.timer(1L, 1L, this::tick);
    }

    /**
     * Spawns the boards once every client has the chunks around its seat. A display spawned in a chunk the client
     * hasn't received yet is never sent to it: the board doesn't move off its spot and the frozen player doesn't either,
     * so nothing makes the server look again.
     */
    private void raiseBoards() {
        if (this.ticks < 100) {
            for (Player p : this.players) {
                if (!this.chunksSent(p)) {
                    return;
                }
            }
        }

        this.boardsUp = true;
        this.phaseStart = this.ticks;
        this.turnStart = this.ticks;
        World w = this.base.getWorld();

        for (Player p : this.players) {
            this.boards.get(p.getUniqueId()).spawn(w);
            Msg.send(p, "prematch.ban-hint", "bans", this.bansLeft.get(p.getUniqueId()));
            Snd.play(p, "block.amethyst_block.chime", 1.0F, 1.0F);
        }

        if (this.maps.size() <= 1) {
            this.chosen = this.maps.get(0);
            this.pickIcon = this.icons.get(0);
            this.lock(null);
        } else if (this.startBans <= 0) {
            this.afterBans();
        }
    }

    /** The client has every chunk within 8 blocks of its seat (the whole board, the orbit and the exit). */
    private boolean chunksSent(Player p) {
        Location seat = this.seat(p);
        if (seat != null && p.getWorld().equals(seat.getWorld())) {
            for (int cx = seat.getBlockX() - 8 >> 4; cx <= seat.getBlockX() + 8 >> 4; cx++) {
                for (int cz = seat.getBlockZ() - 8 >> 4; cz <= seat.getBlockZ() + 8 >> 4; cz++) {
                    if (!p.isChunkSent(Chunk.getChunkKey(cx, cz))) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private void buildPlatform(Location center) {
        World w = center.getWorld();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block b = w.getBlockAt(center.getBlockX() + dx, center.getBlockY(), center.getBlockZ() + dz);
                if (b.getType() != Material.BARRIER) {
                    b.setType(Material.BARRIER, false);
                }
            }
        }
    }

    public Location seat(Player p) {
        int i = this.players.indexOf(p);
        if (i >= 0 && this.base != null) {
            Location pos = this.base.clone().add(i == 0 ? -5.0 : 5.0, 0.0, 0.0);
            pos.setYaw(i == 0 ? -90.0F : 90.0F);
            return pos;
        } else {
            return null;
        }
    }

    void cancel() {
        this.cleanup();
        this.releaseSlot();
    }

    private void abortMissing() {
        this.cleanup();
        Tasks.later(1L, () -> this.match.cancel("match.cancel-left", true));
    }

    private void cleanup() {
        this.phase = PreMatch.Phase.DONE;
        Tasks.cancel(this.task);

        for (PreMatch.Board b : this.boards.values()) {
            b.removeAll();
        }
    }

    void releaseSlot() {
        if (this.slot >= 0) {
            synchronized (SLOTS) {
                SLOTS.clear(this.slot);
            }

            this.slot = -1;
        }
    }

    private void tick() {
        if (this.phase != PreMatch.Phase.DONE) {
            try {
                this.tickSafe();
            } catch (RuntimeException var3) {
                if (!this.failed) {
                    this.failed = true;
                    this.plugin.getLogger().log(Level.SEVERE, "Map vote of match #" + this.match.id + " failed; picking a map and continuing", (Throwable)var3);
                }

                if (this.chosen == null) {
                    List<PreMatch.Icon> open = this.unbanned();
                    this.chosen = (open.isEmpty() ? this.icons.get(0) : open.get(0)).arena;
                }

                this.finish();
            }
        }
    }

    private void tickSafe() {
        this.ticks++;

        for (Player p : this.players) {
            Session s = p.isOnline() ? this.plugin.sessions().get(p.getUniqueId()) : null;
            if (s == null || s.match != this.match || s.state != PlayerState.PREMATCH || p.isDead()) {
                this.abortMissing();
                return;
            }

            if (this.ticks % 10 == 0) {
                Location seat = this.seat(p);
                if (seat != null && (!p.getWorld().equals(seat.getWorld()) || p.getLocation().distanceSquared(seat) > 4.0)) {
                    seat.setPitch(p.getLocation().getPitch());
                    seat.setYaw(p.getLocation().getYaw());
                    Tp.to(p, seat);
                }
            }
        }

        if (!this.boardsUp) {
            if (this.ticks >= 2) {
                this.raiseBoards();
            }

            if (!this.boardsUp) {
                return;
            }
        }

        int limit = (Cfg.i(Opt.MAP_BAN_SECONDS) + (this.bansTotal.values().stream().mapToInt(Integer::intValue).sum() + 2) * (this.turnSeconds + 1) + 3 * Cfg.i(Opt.VOTE_SECONDS) + 30)
            * 20;
        if (this.ticks > limit && this.exitAt < 0) {
            this.plugin.getLogger().warning("Map vote of match #" + this.match.id + " ran too long; continuing.");
            if (this.chosen == null) {
                List<PreMatch.Icon> open = this.unbanned();
                this.chosen = (open.isEmpty() ? this.icons.get(0) : open.get(0)).arena;
            }

            this.finish();
        } else {
            switch (this.phase) {
                case BAN:
                    this.tickBan();
                    break;
                case PICK:
                    this.tickPick();
                    break;
                case ROLL:
                    this.tickRoll();
                    break;
                case LOCK:
                    if (this.ticks - this.phaseStart >= 20) {
                        this.startVotes();
                    }
                    break;
                case VOTE:
                    this.tickVote();
                    break;
                case RESULT:
                    if (this.ticks - this.phaseStart >= 20) {
                        this.nextVote();
                    }
                    break;
                case LOCKED_IN:
                    if (this.ticks - this.phaseStart >= (this.pending.isEmpty() ? 14 : 30)) {
                        this.exit();
                    }
                    break;
                case EXIT:
                    if (this.ticks >= this.exitAt) {
                        this.finish();
                        return;
                    }
            }

            for (PreMatch.Board b : this.boards.values()) {
                b.tick();
            }
        }
    }

    // ------------------------------------------------------------------ bans

    private Player currentTurn() {
        if (this.players.size() != 2) {
            return null;
        } else {
            for (int k = 0; k < 2; k++) {
                Player q = this.players.get(Math.floorMod(this.turnIndex + k, 2));
                if (this.bansLeft.getOrDefault(q.getUniqueId(), 0) > 0) {
                    return q;
                }
            }

            return null;
        }
    }

    private int secondsLeft(int start, int seconds) {
        return Math.max(0, (seconds * 20 - (this.ticks - start) + 19) / 20);
    }

    private void tickBan() {
        boolean allUsed = this.bansLeft.values().stream().allMatch(v -> v <= 0);
        if (this.unbanned().size() <= 2 && this.turns || this.unbanned().size() <= 1 || allUsed) {
            this.afterBans();
        } else if (this.turns) {
            Player cur = this.currentTurn();
            if (cur == null) {
                this.afterBans();
            } else if (this.ticks - this.turnStart >= this.turnSeconds * 20) {
                // ran out of time: MCPVP forfeits every remaining ban of that player
                this.bansLeft.put(cur.getUniqueId(), 0);
                cur.getInventory().clear();

                for (Player p : this.players) {
                    p.sendMessage(Text.mm("<gray>" + Text.esc(cur.getName()) + " ran out of time - their bans are skipped."));
                }

                this.advanceTurn();
            }
        } else if (this.ticks - this.phaseStart >= Cfg.i(Opt.MAP_BAN_SECONDS) * 20) {
            this.afterBans();
        }
    }

    private void advanceTurn() {
        this.turnIndex++;
        this.turnStart = this.ticks;
        this.turnSeconds = Cfg.i(Opt.MAP_BAN_TURN_SECONDS);
        Player next = this.currentTurn();
        if (next != null) {
            next.sendActionBar(Text.mm("<green><b>YOUR TURN</b> <gray>- look at a map and click to ban it"));
            Snd.play(next, "block.note_block.bell", 0.7F, 1.4F);
        }

        this.dirtyHeaders();
    }

    private void tryBan(Player p, PreMatch.Icon icon) {
        if (this.phase == PreMatch.Phase.BAN && this.boardsUp && icon != null && !icon.banned) {
            int left = this.bansLeft.getOrDefault(p.getUniqueId(), 0);
            Player cur = this.turns ? this.currentTurn() : null;
            if (left <= 0) {
                Msg.error(p, "prematch.no-bans-left");
            } else if (this.turns && cur != null && !cur.equals(p)) {
                p.sendActionBar(Text.mm("<red>It's not your turn <gray>- wait for <white>" + Text.esc(cur.getName())));
                Snd.error(p);
            } else if (this.unbanned().size() > 1) {
                this.bansLeft.put(p.getUniqueId(), left - 1);
                this.lastBanner = p.getUniqueId();
                this.ban(icon, p);
                if (left - 1 <= 0) {
                    p.getInventory().clear();
                }

                if (this.turns) {
                    this.advanceTurn();
                } else {
                    this.dirtyHeaders();
                }
            }
        }
    }

    /** Joins the orbit: every orbiter eases to an even spacing around the ring. */
    private void addOrbiter(PreMatch.Icon icon) {
        int now = this.ticks;
        this.orbiters.add(icon);
        icon.orbitIndex = this.banOrder++;
        int n = this.orbiters.size();

        for (int k = 0; k < n; k++) {
            PreMatch.Icon o = this.orbiters.get(k);
            double current = o.offset(now);
            o.offFrom = current;
            o.offTo = (Math.PI * 2.0) * k / n;
            o.offT0 = now;
        }
    }

    private void ban(PreMatch.Icon icon, Player by) {
        icon.banned = true;
        this.addOrbiter(icon);
        this.firstBan = false;

        for (PreMatch.Board b : this.boards.values()) {
            b.onBan(icon);
        }

        for (Player p : this.players) {
            Msg.send(p, "prematch.banned", "player", by.getName(), "map", mapName(icon.arena));
            Snd.play(p, "block.anvil.land", 0.35F, 1.7F);
            Snd.play(p, "entity.breeze.wind_burst", 0.4F, 1.4F);
        }

        this.reflow();
    }

    /** Remaining maps slide into the freed places. */
    private void reflow() {
        for (PreMatch.Board b : this.boards.values()) {
            b.layout(14);
        }
    }

    private void afterBans() {
        List<PreMatch.Icon> open = this.unbanned();
        for (Player p : this.players) {
            p.getInventory().clear();
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof MapBanMenu) {
                p.closeInventory();
            }
        }

        if (open.size() <= 1) {
            this.pickIcon = open.isEmpty() ? this.icons.get(0) : open.get(0);
            this.chosen = this.pickIcon.arena;
            this.lock(null);
        } else if (this.turns) {
            // MCPVP: the player who didn't ban last clicks the final map
            Player last = this.lastBanner == null ? null : this.byId(this.lastBanner);
            this.picker = last == null ? this.players.get(ThreadLocalRandom.current().nextInt(2)) : this.other(last);
            this.phase = PreMatch.Phase.PICK;
            this.phaseStart = this.ticks;

            for (Player p : this.players) {
                p.sendMessage(Text.mm("<green>" + Text.esc(this.picker.getName()) + " gets the final pick."));
                Snd.play(p, "block.note_block.chime", 0.8F, p.equals(this.picker) ? 1.5F : 1.0F);
            }

            for (PreMatch.Board b : this.boards.values()) {
                b.layout(30);
            }

            this.dirtyHeaders();
        } else {
            this.startRoll(open);
        }
    }

    private void tickPick() {
        if (this.ticks - this.phaseStart >= Cfg.i(Opt.MAP_BAN_TURN_SECONDS) * 20) {
            List<PreMatch.Icon> open = this.unbanned();
            this.pickIcon = open.get(ThreadLocalRandom.current().nextInt(open.size()));
            this.chosen = this.pickIcon.arena;
            this.lock(null);
        }
    }

    private void tryPick(Player p, PreMatch.Icon icon) {
        if (this.phase == PreMatch.Phase.PICK && icon != null && !icon.banned) {
            if (!p.equals(this.picker)) {
                p.sendActionBar(Text.mm("<red>" + Text.esc(this.picker.getName()) + " picks the final map"));
                Snd.error(p);
            } else {
                this.pickIcon = icon;
                this.chosen = icon.arena;
                this.lock(p);
            }
        }
    }

    private void startRoll(List<PreMatch.Icon> open) {
        List<PreMatch.Icon> withFree = new ArrayList<>();

        for (PreMatch.Icon i : open) {
            if (i.arena.freeInstances() > 0L) {
                withFree.add(i);
            }
        }

        List<PreMatch.Icon> pool = withFree.isEmpty() ? open : withFree;
        this.pickIcon = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        this.chosen = this.pickIcon.arena;
        this.rollPool = open;
        this.phase = PreMatch.Phase.ROLL;
        this.phaseStart = this.ticks;
        int target = Math.max(0, open.indexOf(this.pickIcon));
        this.rollIndex = Math.floorMod(target - 13, open.size());
        this.rollStep = 0;
        this.rollNext = this.ticks;
        this.dirtyHeaders();
    }

    private void tickRoll() {
        if (this.ticks >= this.rollNext) {
            this.rollIndex++;
            this.rollStep++;
            this.rollLit = this.rollPool.get(Math.floorMod(this.rollIndex, this.rollPool.size()));
            float slow = this.rollStep / 13.0F;

            for (Player p : this.players) {
                Snd.play(p, "block.note_block.hat", 0.6F, 2.0F - slow);
            }

            if (this.rollStep >= 13) {
                this.rollLit = null;
                this.lock(null);
            } else {
                this.rollNext = this.ticks + 2 + this.rollStep;
            }
        }
    }

    private void lock(Player by) {
        this.phase = PreMatch.Phase.LOCK;
        this.phaseStart = this.ticks;
        this.pickIcon.picked = true;
        if (by != null) {
            for (Player p : this.players) {
                p.sendMessage(Text.mm("<green>" + Text.esc(by.getName()) + " picked " + Text.esc(mapName(this.chosen)) + "."));
            }
        }

        for (PreMatch.Icon i : this.icons) {
            if (!i.banned && i != this.pickIcon) {
                i.banned = true;
                this.addOrbiter(i);
            }
        }

        for (PreMatch.Board b : this.boards.values()) {
            b.onLock();
        }

        World w = this.base.getWorld();

        for (Player p : this.players) {
            Snd.play(p, "entity.player.levelup", 0.7F, 1.3F);
            Snd.play(p, "block.beacon.power_select", 0.5F, 1.6F);
            PreMatch.Board b = this.boards.get(p.getUniqueId());
            Vector c = b.center();
            p.spawnParticle(Particle.END_ROD, c.getX(), c.getY(), c.getZ(), 18, 0.25, 0.25, 0.25, 0.02);
        }

        this.dirtyHeaders();
    }

    // ------------------------------------------------------------------ votes

    private void startVotes() {
        this.pending = Cfg.bool(Opt.VOTES) ? votesFor(this.match.kit) : List.of();
        this.voteIndex = -1;
        this.nextVote();
    }

    private void nextVote() {
        for (PreMatch.Board b : this.boards.values()) {
            b.clearVote();
        }

        this.voteIndex++;
        if (this.voteIndex >= this.pending.size()) {
            this.phase = PreMatch.Phase.LOCKED_IN;
            this.phaseStart = this.ticks;

            for (PreMatch.Board b : this.boards.values()) {
                b.onLockedIn();
            }

            this.dirtyHeaders();
        } else {
            this.phase = PreMatch.Phase.VOTE;
            this.phaseStart = this.ticks;
            this.votes.clear();
            this.voteResult = null;
            KitVote v = this.pending.get(this.voteIndex);

            for (Player p : this.players) {
                this.giveVoteItems(p, v);
                Snd.play(p, "block.note_block.bell", 0.8F, 1.2F);
            }

            for (PreMatch.Board b : this.boards.values()) {
                b.spawnVote(v);
            }

            this.dirtyHeaders();
        }
    }

    private void tickVote() {
        boolean allVoted = this.votes.size() >= this.players.size();
        if (this.ticks - this.phaseStart >= Cfg.i(Opt.VOTE_SECONDS) * 20 || allVoted) {
            this.resolveVote();
        } else if (this.ticks % 20 == 0) {
            this.dirtyHeaders();
        }
    }

    private void castVote(Player p, boolean yes) {
        if (this.phase == PreMatch.Phase.VOTE) {
            this.votes.put(p.getUniqueId(), yes);
            p.sendActionBar(Text.mm(yes ? "<green>You voted <b>YES" : "<red>You voted <b>NO"));
            Snd.play(p, "ui.button.click", 0.8F, yes ? 1.6F : 0.9F);

            for (PreMatch.Board b : this.boards.values()) {
                b.onVote(p, yes);
            }
        }
    }

    private void resolveVote() {
        KitVote v = this.pending.get(this.voteIndex);
        boolean pass = true;

        for (Player p : this.players) {
            if (!Boolean.TRUE.equals(this.votes.get(p.getUniqueId()))) {
                pass = false;
            }
        }

        if (pass) {
            this.passed.add(v);
        }

        this.voteResult = pass;

        for (Player px : this.players) {
            Msg.send(px, pass ? "prematch.vote-passed" : "prematch.vote-failed", "vote", v.question());
            Snd.play(px, pass ? "entity.experience_orb.pickup" : "block.note_block.bass", 0.8F, pass ? 1.4F : 0.8F);
        }

        this.phase = PreMatch.Phase.RESULT;
        this.phaseStart = this.ticks;

        for (PreMatch.Board b : this.boards.values()) {
            b.onVoteResult(pass);
        }

        this.dirtyHeaders();
    }

    // ------------------------------------------------------------------ the end

    private void exit() {
        this.phase = PreMatch.Phase.EXIT;
        this.exitAt = this.ticks + 10;

        for (PreMatch.Board b : this.boards.values()) {
            b.onExit();
        }

        for (Player p : this.players) {
            Snd.play(p, "entity.illusioner.mirror_move", 0.5F, 1.4F);
        }
    }

    private void finish() {
        if (this.phase != PreMatch.Phase.DONE) {
            Arena result = this.chosen;
            List<KitVote> result2 = new ArrayList<>(this.passed);
            this.cleanup();
            this.match.onArenaChosen(result, result2);
        }
    }

    // ------------------------------------------------------------------ input

    public void click(Player p) {
        long now = System.currentTimeMillis();
        Long last = this.lastClick.get(p.getUniqueId());
        if (last == null || now - last >= 200L) {
            this.lastClick.put(p.getUniqueId(), now);
            PreMatch.Board b = this.boards.get(p.getUniqueId());
            if (b != null) {
                if (this.phase == PreMatch.Phase.BAN) {
                    this.tryBan(p, b.hovered);
                } else if (this.phase == PreMatch.Phase.PICK) {
                    this.tryPick(p, b.hovered);
                } else if (this.phase == PreMatch.Phase.VOTE && b.hoveredVote != null) {
                    this.castVote(p, b.hoveredVote);
                }
            }
        }
    }

    private void giveBanItems(Player p) {
        p.getInventory().clear();
        if (this.bansLeft.getOrDefault(p.getUniqueId(), 0) > 0) {
            p.getInventory()
                .setItem(
                    4,
                    Items.of(Material.FILLED_MAP)
                        .name("<red><b>Ban a Map")
                        .lore("<gray>Open the list of maps and", "<gray>click the one you want to ban.", "", "<yellow>▶ Click")
                        .action("pm_ban")
                        .hideAll()
                        .build()
                );
            p.getInventory().setHeldItemSlot(4);
        }
    }

    private void giveVoteItems(Player p, KitVote v) {
        p.getInventory().clear();
        p.getInventory().setItem(2, Items.of(Material.LIME_DYE).name("<green><b>Vote YES").lore("<gray>" + Text.esc(v.question())).action("pm_yes").build());
        Material shown = v.material().isItem() && !v.material().isAir() ? v.material() : Material.PAPER;
        p.getInventory()
            .setItem(4, Items.of(shown).name("<white><b>" + Text.esc(v.question())).lore("<gray>Applies only if both players vote yes.").hideAll().build());
        p.getInventory().setItem(6, Items.of(Material.RED_DYE).name("<red><b>Vote NO").lore("<gray>" + Text.esc(v.question())).action("pm_no").build());
        p.getInventory().setHeldItemSlot(4);
    }

    public boolean useItem(Player p, String action) {
        if (action == null) {
            return false;
        } else {
            switch (action) {
                case "pm_ban":
                    if (this.banPhase()) {
                        new MapBanMenu(this.plugin, p, this).open();
                    }

                    return true;
                case "pm_yes":
                case "pm_no":
                    this.castVote(p, "pm_yes".equals(action));
                    return true;
                default:
                    return false;
            }
        }
    }

    boolean banPhase() {
        return this.phase == PreMatch.Phase.BAN && this.boardsUp;
    }

    List<Arena> mapList() {
        List<Arena> out = new ArrayList<>();

        for (PreMatch.Icon i : this.icons) {
            out.add(i.arena);
        }

        return out;
    }

    boolean isBanned(Arena a) {
        for (PreMatch.Icon i : this.icons) {
            if (i.arena == a) {
                return i.banned;
            }
        }

        return true;
    }

    int bansLeft(Player p) {
        return this.bansLeft.getOrDefault(p.getUniqueId(), 0);
    }

    void banFromMenu(Player p, Arena a) {
        for (PreMatch.Icon i : this.icons) {
            if (i.arena == a) {
                this.tryBan(p, i);
                return;
            }
        }
    }

    public static List<KitVote> votesFor(Kit kit) {
        List<KitVote> out = new ArrayList<>(kit.votes);
        Set<Material> used = new HashSet<>();

        for (KitVote v : out) {
            used.add(v.material());
        }

        Set<Material> core = new HashSet<>();
        core.add(kit.icon.getType());
        core.add(Material.SHIELD);
        if (kit.contents[0] != null) {
            core.add(kit.contents[0].getType());
        }

        int totems = kit.countOf(Material.TOTEM_OF_UNDYING);
        if (totems >= 4) {
            limit(out, used, core, Material.TOTEM_OF_UNDYING, (totems + 1) / 2, "Totems");
        }

        remove(out, used, core, kit, Material.RESPAWN_ANCHOR, "No Respawn Anchors");
        if (kit.countOf(Material.FIREWORK_ROCKET) > 32) {
            limit(out, used, core, Material.FIREWORK_ROCKET, 32, "Rockets");
        }

        remove(out, used, core, kit, Material.ENDER_PEARL, "No Ender Pearls");
        if (kit.countOf(Material.WIND_CHARGE) > 32) {
            limit(out, used, core, Material.WIND_CHARGE, 32, "Wind Charges");
        }

        if (kit.countOf(Material.GOLDEN_APPLE) > 16) {
            limit(out, used, core, Material.GOLDEN_APPLE, 16, "Golden Apples");
        }

        remove(out, used, core, kit, Material.COBWEB, "No Cobwebs");
        remove(out, used, core, kit, Material.LAVA_BUCKET, "No Lava Buckets");
        if (kit.countOf(Material.EXPERIENCE_BOTTLE) > 64) {
            limit(out, used, core, Material.EXPERIENCE_BOTTLE, 64, "XP Bottles");
        }

        // one more round: the kit's own rounds vote, else the automatic one for short duels
        boolean hasRounds = false;

        for (KitVote v : out) {
            hasRounds |= v.action() == KitVote.Action.ROUNDS;
        }

        int max = Math.max(1, Cfg.i(Opt.VOTE_MAX));
        if (!hasRounds && kit.roundsVote && Cfg.bool(Opt.VOTE_ROUNDS) && kit.rounds() < 3) {
            int n = kit.rounds() + 1;
            KitVote r = new KitVote("auto_rounds", "Play First To " + n + " Rounds", KitVote.Action.ROUNDS, Material.CLOCK, n);
            if (out.size() >= max) {
                out.set(max - 1, r);
            } else {
                out.add(r);
            }
        }

        return (List<KitVote>)(out.size() > max ? new ArrayList<>(out.subList(0, max)) : out);
    }

    private static void limit(List<KitVote> out, Set<Material> used, Set<Material> core, Material m, int amount, String label) {
        if (!core.contains(m) && used.add(m)) {
            out.add(new KitVote("auto_limit_" + m.getKey().getKey(), "Limit to " + amount + " " + label, KitVote.Action.LIMIT, m, amount));
        }
    }

    private static void remove(List<KitVote> out, Set<Material> used, Set<Material> core, Kit kit, Material m, String question) {
        if (kit.countOf(m) > 0 && !core.contains(m) && used.add(m)) {
            out.add(new KitVote("auto_no_" + m.getKey().getKey(), question, KitVote.Action.REMOVE, m, 0));
        }
    }

    public boolean owns(Entity e) {
        for (PreMatch.Board b : this.boards.values()) {
            if (b.owns(e)) {
                return true;
            }
        }

        return false;
    }

    public Component status() {
        return Text.mm(this.phase == PreMatch.Phase.BAN ? "<yellow>Map ban" : (this.phase == PreMatch.Phase.VOTE ? "<red>Voting" : "<green>Starting"));
    }

    private List<PreMatch.Icon> unbanned() {
        List<PreMatch.Icon> out = new ArrayList<>();

        for (PreMatch.Icon i : this.icons) {
            if (!i.banned) {
                out.add(i);
            }
        }

        return out;
    }

    private Player byId(UUID u) {
        for (Player p : this.players) {
            if (p.getUniqueId().equals(u)) {
                return p;
            }
        }

        return null;
    }

    private Player other(Player p) {
        for (Player q : this.players) {
            if (!q.equals(p)) {
                return q;
            }
        }

        return p;
    }

    private void dirtyHeaders() {
        for (PreMatch.Board b : this.boards.values()) {
            b.headerDirty = true;
        }
    }

    /**
     * The name a map shows in the vote: biome maps show their biome ("Plains", also for the deep crystal copy "Plains
     * (Crystal)"), unless an admin gave the arena a name of its own.
     */
    static String mapName(Arena a) {
        if (a.theme != null && a.theme.vanilla != null && a.displayName != null && a.displayName.startsWith(a.theme.display)) {
            return a.theme.display;
        } else {
            return a.displayName;
        }
    }

    /**
     * A map's name for its label: names longer than 11 characters wrap onto two lines at the space nearest the middle
     * ("Badlands / Red Desert"), so neighbouring labels never run into each other.
     */
    static String labelName(Arena a) {
        String name = mapName(a);
        if (TextWidth.width(Component.text(name)) > 66 && name.indexOf(' ') > 0) {
            int mid = name.length() / 2;
            int best = -1;

            for (int i = 0; i < name.length(); i++) {
                if (name.charAt(i) == ' ' && (best < 0 || Math.abs(i - mid) < Math.abs(best - mid))) {
                    best = i;
                }
            }

            return Text.esc(name.substring(0, best)) + "\n<white>" + Text.esc(name.substring(best + 1));
        } else {
            return Text.esc(name);
        }
    }

    private static int labelLines(Arena a) {
        return labelName(a).contains("\n") ? 2 : 1;
    }

    /**
     * Degrees between maps so the widest label (plus a margin) fits between its neighbours: at least 21 (about a block
     * from cube to cube), at most 26 so a row of four stays within the view.
     */
    static double stepFor(List<PreMatch.Icon> icons) {
        double widest = 0.0;

        for (PreMatch.Icon i : icons) {
            if (i.arena != null) {
                widest = Math.max(widest, labelWidth(i.arena));
            }
        }

        double need = Math.toDegrees(2.0 * Math.asin(Math.min(0.99, (widest + 0.18) / (2.0 * BOARD))));
        return Math.max(21.0, Math.min(26.0, need));
    }

    /** Width in blocks of a map's label card at its shown scale, from the real font widths. */
    private static double labelWidth(Arena a) {
        return (TextWidth.width(Text.mm("<white>" + labelName(a))) + 4) * 0.025 * LABEL_SCALE;
    }

    /** Self test: {label width, room between neighbours} in blocks for a map's label at the spacing its board would use. */
    public static double[] labelFit(Arena a) {
        double w = labelWidth(a);
        double need = Math.toDegrees(2.0 * Math.asin(Math.min(0.99, (w + 0.18) / (2.0 * BOARD))));
        double step = Math.max(21.0, Math.min(26.0, need));
        return new double[]{w, 2.0 * BOARD * Math.sin(Math.toRadians(step) / 2.0)};
    }

    private static ItemStack iconItem(Arena a) {
        Material m = a.iconBlock != null && a.iconBlock.isItem() && !a.iconBlock.isAir() ? a.iconBlock : Material.GRASS_BLOCK;
        return new ItemStack(m);
    }

    private static int nameColor(VortexRealm plugin, Player p) {
        try {
            return Anim.parseRgb(plugin.serverRanks().of(p).nameColor, 0xFFFFFF);
        } catch (RuntimeException var3) {
            return 0xFFFFFF;
        }
    }

    // ------------------------------------------------------------------ one player's board

    private final class Board {
        final Player viewer;
        final int idx;
        final Vector eye;
        final Vector f;
        final Vector r;
        final Vector up = new Vector(0, 1, 0);
        final Map<PreMatch.Icon, PreMatch.IconView> views = new LinkedHashMap<>();
        final List<Elem> elems = new ArrayList<>();
        final List<MapModelView> models = new ArrayList<>();
        /** Some maps show as 3D previews, which need more room. */
        boolean wide;
        /** Degrees between neighbouring maps on the arc: wide enough for the widest label. */
        double step = 16.0;
        Elem title;
        Elem header;
        boolean headerDirty = true;
        String lastHeader = "";
        int headerBg = BG_TURN;
        PreMatch.Icon hovered;
        PreMatch.Icon theirHover;
        Boolean hoveredVote;
        PreMatch.VoteView yes;
        PreMatch.VoteView no;
        Elem topic;
        final int meColor;
        final int themColor;
        final String meName;
        final String themName;
        int exitStart = -1;

        Board(Player viewer, int idx) {
            this.viewer = viewer;
            this.idx = idx;
            Location seat = PreMatch.this.seat(viewer);
            this.eye = seat.toVector().add(new Vector(0.0, 1.62, 0.0));
            this.f = new Vector(idx == 0 ? 1 : -1, 0, 0);
            this.r = new Vector(0, 0, idx == 0 ? 1 : -1);
            Player them = PreMatch.this.other(viewer);
            this.meName = viewer.getName();
            this.themName = them.getName();
            this.meColor = PreMatch.nameColor(PreMatch.this.plugin, viewer);
            this.themColor = PreMatch.nameColor(PreMatch.this.plugin, them);
        }

        Vector at(double angleDeg, double h, double dist) {
            double a = Math.toRadians(angleDeg);
            return this.eye.clone().add(this.f.clone().multiply(Math.cos(a) * dist)).add(this.r.clone().multiply(Math.sin(a) * dist)).add(this.up.clone().multiply(h));
        }

        Vector center() {
            return this.at(0.0, 0.02, 2.35);
        }

        void spawn(World w) {
            int now = PreMatch.this.ticks;
            this.title = Elem.spawn(PreMatch.this.plugin, w, this.at(0.0, 1.06, BOARD), TextDisplay.class, this.viewer, 0.01F, now, t -> {
                t.setBillboard(Billboard.CENTER);
                t.setBackgroundColor(Color.fromARGB(BG_TITLE));
                t.setLineWidth(400);
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
                t.text(this.titleText(2.0));
            });
            this.title.scaleTo(0.62F, now + 2, 8, Anim::easeOutBack);
            this.header = Elem.spawn(PreMatch.this.plugin, w, this.at(0.0, 0.72, BOARD), TextDisplay.class, this.viewer, 0.01F, now, t -> {
                t.setBillboard(Billboard.CENTER);
                t.setBackgroundColor(Color.fromARGB(BG_TURN));
                t.setLineWidth(400);
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
            });
            this.header.scaleTo(0.5F, now + 4, 8, Anim::easeOutBack);
            this.elems.add(this.title);
            this.elems.add(this.header);
            List<PreMatch.Icon> list = PreMatch.this.icons;
            // designed arenas show as detailed 3D miniatures, biome maps as their biome block
            List<MapModel> models = new ArrayList<>();

            for (PreMatch.Icon icon : list) {
                MapModel m = MapModel.wanted(icon.arena) ? MapModel.get(icon.arena) : null;
                models.add(m != null && !m.parts.isEmpty() ? m : null);
                this.wide |= m != null && !m.parts.isEmpty();
            }

            this.step = this.wide ? 19.0 : stepFor(list);

            for (int i = 0; i < list.size(); i++) {
                PreMatch.Icon icon = list.get(i);
                Vector pos = this.slot(i, list.size());
                int delay = 6 + i * 2;
                PreMatch.IconView v = new PreMatch.IconView();
                v.lines = labelLines(icon.arena);
                v.cube = Elem.spawn(PreMatch.this.plugin, w, pos, ItemDisplay.class, this.viewer, 0.001F, now, d -> {
                    d.setItemStack(PreMatch.iconItem(icon.arena));
                    d.setItemDisplayTransform(ItemDisplayTransform.NONE);
                    d.setBrightness(new Brightness(15, 15));
                    d.setViewRange(1.0F);
                });
                v.cube.rot = t -> this.cubeRotation(icon, v, t);
                if (models.get(i) != null) {
                    // the cube stays hidden: it only carries the position until the map is banned
                    v.model = new MapModelView(PreMatch.this.plugin, w, this.viewer, models.get(i), this.eye, t -> this.modelPos(v, t), now + delay);
                    v.model.sizeTo(MODEL, now + delay, 9, Anim::easeOutBack);
                    this.models.add(v.model);
                } else {
                    v.cube.scaleTo(CUBE, now + delay, 7, Anim::easeOutBack);
                }

                v.label = Elem.spawn(PreMatch.this.plugin, w, this.labelAt(v, pos, v.model != null ? MODEL : CUBE), TextDisplay.class, this.viewer, 0.001F, now, t -> {
                    t.setBillboard(Billboard.CENTER);
                    t.setBackgroundColor(Color.fromARGB(BG_IDLE));
                    t.setLineWidth(LABEL_WIDTH);
                    t.setAlignment(TextDisplay.TextAlignment.CENTER);
                    t.text(Text.mm("<white>" + labelName(icon.arena)));
                });
                v.label.scaleTo(0.42F, now + delay + 2, 6);
                this.views.put(icon, v);
                this.elems.add(v.cube);
                this.elems.add(v.label);
            }
        }

        /**
         * Arc slot {@code i} of {@code n}: two rows (top gets the extra one), spread around the line of sight. Boards
         * with 3D previews space them wider.
         */
        Vector slot(int i, int n) {
            if (n <= 4) {
                double angle = (i - (n - 1) / 2.0) * this.step;
                return this.at(angle, this.wide ? -0.02 : 0.05, BOARD);
            } else {
                int top = (n + 1) / 2;
                int row = i < top ? 0 : 1;
                int col = row == 0 ? i : i - top;
                int inRow = row == 0 ? top : n - top;
                double angle = (col - (inRow - 1) / 2.0) * this.step;
                // rows far enough apart that a hovered (bigger) top map and its label stay clear of the row below
                return this.at(angle, row == 0 ? (this.wide ? 0.3 : 0.46) : -0.5, BOARD);
            }
        }

        Vector labelPos(Vector cube, float scale) {
            // a turning block reaches about 0.87 x its size below its centre (its diagonal): the label hangs under that
            return cube.clone().add(new Vector(0.0, -(0.1 + scale * 0.9), 0.0));
        }

        /** Where an icon's label goes when its cube (or 3D preview) is at {@code pos} with size {@code size}. */
        Vector labelAt(PreMatch.IconView v, Vector pos, float size) {
            // a two-line name grows upward from its anchor, so it starts a line lower and never touches its map
            double extra = (v.lines - 1) * LABEL_LINE;
            return v.model == null
                ? this.labelPos(pos, size).add(new Vector(0.0, -extra, 0.0))
                : pos.clone().add(new Vector(0.0, -(0.12 + v.model.drop(size) + size * 0.2 + extra), 0.0));
        }

        /** A 3D preview sits on its (hidden) cube and leans a little toward the viewer while hovered. */
        Vector modelPos(PreMatch.IconView v, int t) {
            Vector p = v.cube.pos(t);
            double k = v.hoverAmount(t);
            return k <= 0.0 ? p : p.add(this.eye.clone().subtract(p).normalize().multiply(0.12 * k));
        }

        /** Spot of the picked map in the middle. */
        Vector pickedSpot(PreMatch.IconView v) {
            return v.model == null ? this.center() : this.at(0.0, -0.1, 2.35);
        }

        Quaternionf cubeRotation(PreMatch.Icon icon, PreMatch.IconView v, int t) {
            Vector p = v.cube.pos(t);
            Vector d = this.eye.clone().subtract(p);
            float face = (float)Math.atan2(d.getX(), d.getZ());
            if (icon.banned && !icon.picked) {
                return new Quaternionf().rotateY(t * 0.15F).rotateX(t * 0.09F);
            } else if (icon.picked && PreMatch.this.phase != PreMatch.Phase.PICK) {
                // the winner tumbles on two axes
                return new Quaternionf().rotateY(face).rotateY(t * 0.105F).rotateX(t * 0.052F);
            } else {
                double k = v.hoverAmount(t);
                Quaternionf idle = new Quaternionf().rotateY(face).rotateX(0.45F).rotateY((float)(Math.PI / 4.0));
                Quaternionf hover = new Quaternionf().rotateY(face).rotateX(0.16F);
                return idle.slerp(hover, (float)k);
            }
        }

        double titlePhase() {
            return Anim.sweepPhase(PreMatch.this.ticks, 60, 34, this.idx * 17);
        }

        Component titleText(double phase) {
            double other = phase > 1.0 ? 2.0 : Anim.clamp01(phase * 1.15 - 0.15);
            return Component.empty()
                .append(Anim.sweep(this.meName, this.meColor, phase, true))
                .append(Component.text("  VS  ", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true))
                .append(Anim.sweep(this.themName, this.themColor, other, true));
        }

        /** Re-targets every icon: open ones to their arc (or final-pick) slots, banned ones to the orbit. */
        void layout(int ticks) {
            int now = PreMatch.this.ticks;
            List<PreMatch.Icon> open = PreMatch.this.unbanned();
            boolean pick = PreMatch.this.phase == PreMatch.Phase.PICK;

            for (int i = 0; i < open.size(); i++) {
                PreMatch.IconView v = this.views.get(open.get(i));
                Vector target = pick
                    ? this.at((i - (open.size() - 1) / 2.0) * (this.wide ? 24.0 : Math.max(22.0, this.step + 4.0)), this.wide ? -0.06 : 0.04, 2.45)
                    : this.slot(i, open.size());
                float scale = v.model != null ? (pick ? MODEL_FINAL : MODEL) : (pick ? CUBE_HOVER : CUBE);
                v.cube.moveTo(target, now, ticks, Anim::easeInOutCubic);
                v.label.moveTo(this.labelAt(v, target, scale), now, ticks, Anim::easeInOutCubic);
                if (pick && v.model != null) {
                    v.model.sizeTo(scale, now, ticks, Anim::easeInOutCubic);
                } else if (pick) {
                    v.cube.scaleTo(scale, now, ticks);
                }
            }
        }

        /** 0 until the exit starts, then eases to 1 over 14 ticks. */
        double exitK(int t) {
            return this.exitStart < 0 ? 0.0 : Anim.easeInCubic((t - this.exitStart) / 14.0);
        }

        Vector orbitCenter(int t) {
            return this.at(0.0, -0.06, ORBIT_DIST + 3.5 * this.exitK(t));
        }

        /** Pure function of time: the ring turns at 30 degrees a second, members ease to an even spacing. */
        Vector orbit(PreMatch.Icon icon, int t) {
            double a = t * ORBIT_SPEED + icon.offset(t);
            double radius = ORBIT_RADIUS * (1.0 + 1.8 * this.exitK(t));
            return this.orbitCenter(t).add(this.r.clone().multiply(Math.cos(a) * radius)).add(this.up.clone().multiply(Math.sin(a) * radius));
        }

        void onBan(PreMatch.Icon icon) {
            int now = PreMatch.this.ticks;
            PreMatch.IconView v = this.views.get(icon);
            if (this.hovered == icon) {
                this.hovered = null;
            }

            if (this.theirHover == icon) {
                this.theirHover = null;
            }

            // label pops away, cube shrinks and glides into the orbit (a 3D preview folds into its biome block on the way)
            v.label.scaleTo(0.001F, now, 4, Anim::easeInCubic);
            Vector start = v.cube.pos(now);
            int joinAt = now;
            if (v.model != null && v.model.alive()) {
                v.model.collapse(now, 9);
                v.cube.scaleTo(CUBE_ORBIT, now + 2, 10, Anim::easeOutBack);
            } else {
                v.cube.scaleTo(CUBE_ORBIT, now, 12);
            }
            v.cube.path = t -> {
                Vector o = this.orbit(icon, t);
                double k = Anim.easeInOutSine((t - joinAt) / 14.0);
                return k >= 1.0 ? o : start.clone().add(o.subtract(start).multiply(k));
            };
            Vector c = v.cube.pos(now);
            this.viewer.spawnParticle(Particle.CLOUD, c.getX(), c.getY(), c.getZ(), 6, 0.08, 0.08, 0.08, 0.01);
        }

        void onLock() {
            int now = PreMatch.this.ticks;

            for (Map.Entry<PreMatch.Icon, PreMatch.IconView> en : this.views.entrySet()) {
                PreMatch.Icon icon = en.getKey();
                PreMatch.IconView v = en.getValue();
                if (v.hoverMine || v.hoverTheirs) {
                    // the hover lean and tilt ease back instead of snapping
                    v.hoverSince = now;
                }

                v.hoverMine = false;
                v.hoverTheirs = false;
                if (icon == PreMatch.this.pickIcon) {
                    Vector mid = this.pickedSpot(v);
                    v.cube.moveTo(mid, now, 16, Anim::easeInOutCubic);
                    if (v.model != null) {
                        // the picked preview takes the middle and turns slowly
                        v.model.sizeTo(MODEL_PICKED, now, 16, Anim::easeOutBack);
                        v.model.spin(true, 36.0F, now);
                    } else {
                        v.cube.scaleTo(0.82F, now, 16, Anim::easeOutBack);
                    }

                    v.label.moveTo(this.labelAt(v, mid, v.model != null ? MODEL_PICKED : 0.82F), now, 16, Anim::easeInOutCubic);
                    v.label.scaleTo(0.62F, now, 10);
                    v.setLabel(this.viewer, Text.mm("<white><b>PICKED</b>\n<white>" + Text.esc(mapName(icon.arena))), BG_LOCKED, 4);
                } else if (v.cube.path == null) {
                    this.onBan(icon);
                }
            }

            this.hovered = null;
            this.theirHover = null;
        }

        void spawnVote(KitVote vote) {
            int now = PreMatch.this.ticks;
            World w = PreMatch.this.base.getWorld();
            PreMatch.IconView picked = this.views.get(PreMatch.this.pickIcon);
            if (picked != null) {
                // the map moves up out of the way as a small badge
                Vector badge = this.at(0.0, picked.model != null ? 0.46 : 0.4, BOARD);
                picked.cube.moveTo(badge, now, 12, Anim::easeInOutCubic);
                if (picked.model != null) {
                    picked.model.sizeTo(MODEL_BADGE, now, 12, Anim::easeInOutCubic);
                } else {
                    picked.cube.scaleTo(0.22F, now, 12);
                }

                picked.label.scaleTo(0.001F, now, 6);
            }

            Material m = vote.material().isItem() && !vote.material().isAir() ? vote.material() : Material.PAPER;
            Vector mid = this.at(0.0, 0.0, 2.4);
            this.topic = Elem.spawn(PreMatch.this.plugin, w, mid, ItemDisplay.class, this.viewer, 0.001F, now, d -> {
                d.setItemStack(new ItemStack(m));
                d.setItemDisplayTransform(ItemDisplayTransform.NONE);
                d.setBrightness(new Brightness(15, 15));
            });
            this.topic.scaleTo(0.5F, now + 2, 8, Anim::easeOutBack);
            this.topic.rot = t -> new Quaternionf().rotateY(t * 0.11F).rotateX((float)Math.sin(t * 0.05) * 0.5F);
            this.elems.add(this.topic);
            this.no = this.voteOption(w, false, this.at(-14.0, -0.02, 2.5), now + 4);
            this.yes = this.voteOption(w, true, this.at(14.0, -0.02, 2.5), now + 6);
        }

        PreMatch.VoteView voteOption(World w, boolean yes, Vector pos, int when) {
            PreMatch.VoteView v = new PreMatch.VoteView();
            v.yes = yes;
            v.home = pos;
            v.item = Elem.spawn(PreMatch.this.plugin, w, pos, ItemDisplay.class, this.viewer, 0.001F, PreMatch.this.ticks, d -> {
                d.setItemStack(new ItemStack(yes ? Material.SLIME_BLOCK : Material.BARRIER));
                d.setItemDisplayTransform(yes ? ItemDisplayTransform.NONE : ItemDisplayTransform.FIXED);
                d.setBrightness(new Brightness(15, 15));
            });
            v.item.scaleTo(yes ? 0.36F : 0.42F, when, 8, Anim::easeOutBack);
            v.item.rot = yes ? t -> new Quaternionf().rotateY(t * 0.08F).rotateX(0.4F) : t -> new Quaternionf().rotateY(t * 0.105F);
            v.label = Elem.spawn(PreMatch.this.plugin, w, this.labelPos(pos, 0.36F), TextDisplay.class, this.viewer, 0.001F, PreMatch.this.ticks, t -> {
                t.setBillboard(Billboard.CENTER);
                t.setBackgroundColor(Color.fromARGB(BG_IDLE));
                t.text(Text.mm("<white>" + (yes ? "Yes" : "No")));
            });
            v.label.scaleTo(0.42F, when + 2, 6);
            this.elems.add(v.item);
            this.elems.add(v.label);
            return v;
        }

        void onVote(Player who, boolean yesVote) {
            if (!who.equals(this.viewer)) {
                PreMatch.VoteView v = yesVote ? this.yes : this.no;
                PreMatch.VoteView o = yesVote ? this.no : this.yes;
                if (v != null && o != null) {
                    v.theirs = true;
                    o.theirs = false;
                    this.refreshVoteLabels();
                }
            } else {
                this.refreshVoteLabels();
            }
        }

        void refreshVoteLabels() {
            for (PreMatch.VoteView v : new PreMatch.VoteView[]{this.yes, this.no}) {
                if (v != null && v.label.valid()) {
                    String opt = v.yes ? "Yes" : "No";
                    Boolean mine = PreMatch.this.votes.get(this.viewer.getUniqueId());
                    boolean hover = this.hoveredVote != null && this.hoveredVote == v.yes;
                    Component text;
                    int bg;
                    if (mine != null && mine == v.yes) {
                        text = Text.mm("<white><b>YOUR VOTE</b>\n<white>" + opt);
                        bg = BG_HOVER;
                    } else if (v.theirs) {
                        text = Text.mm("<yellow><b>THEIR VOTE</b>\n<white>" + opt);
                        bg = BG_THEIRS;
                    } else if (hover) {
                        text = Text.mm("<white>" + opt + "\n<white><b>CLICK TO VOTE");
                        bg = BG_HOVER;
                    } else {
                        text = Text.mm("<white>" + opt);
                        bg = BG_IDLE;
                    }

                    v.setLabel(this.viewer, text, bg);
                }
            }
        }

        void onVoteResult(boolean pass) {
            int now = PreMatch.this.ticks;
            PreMatch.VoteView win = pass ? this.yes : this.no;
            PreMatch.VoteView lose = pass ? this.no : this.yes;
            if (win != null && lose != null) {
                Vector mid = this.at(0.0, -0.02, 2.4);
                win.item.moveTo(mid, now, 14);
                win.item.scaleTo(0.6F, now, 14, Anim::easeOutBack);
                win.label.moveTo(this.labelPos(mid, 0.6F), now, 14);
                win.label.scaleTo(0.6F, now, 10);
                boolean agreed = PreMatch.this.votes.size() >= 2 && PreMatch.this.votes.values().stream().distinct().count() == 1L;
                win.setLabel(this.viewer, Text.mm("<white><b>" + (agreed ? "AGREED" : "DECIDED") + "</b>\n<white>" + (pass ? "Yes" : "No")), BG_LOCKED);
                lose.label.scaleTo(0.001F, now, 4, Anim::easeInCubic);
                Vector start = lose.item.pos(now);
                PreMatch.Icon ghost = new PreMatch.Icon(null, -1);
                PreMatch.this.addOrbiter(ghost);
                lose.item.scaleTo(CUBE_ORBIT, now, 14);
                lose.item.path = t -> {
                    Vector o = this.orbit(ghost, t);
                    double k = Anim.easeInOutSine((t - now) / 16.0);
                    return k >= 1.0 ? o : start.clone().add(o.subtract(start).multiply(k));
                };
                lose.item.rot = t -> new Quaternionf().rotateY(t * 0.15F).rotateX(t * 0.09F);
                lose.orbiting = true;
            }

            if (this.topic != null) {
                this.topic.scaleTo(0.001F, now, 8, Anim::easeInCubic);
            }
        }

        void clearVote() {
            int now = PreMatch.this.ticks;

            for (PreMatch.VoteView v : new PreMatch.VoteView[]{this.yes, this.no}) {
                if (v != null && !v.orbiting) {
                    v.item.scaleTo(0.001F, now, 6, Anim::easeInCubic);
                    v.label.scaleTo(0.001F, now, 4, Anim::easeInCubic);
                    Elem item = v.item;
                    Elem label = v.label;
                    Tasks.later(8L, () -> {
                        item.remove();
                        label.remove();
                    });
                } else if (v != null) {
                    v.label.remove();
                }
            }

            if (this.topic != null) {
                Elem t = this.topic;
                Tasks.later(8L, t::remove);
                this.topic = null;
            }

            this.yes = null;
            this.no = null;
            this.hoveredVote = null;
        }

        void onLockedIn() {
            int now = PreMatch.this.ticks;
            PreMatch.IconView v = this.views.get(PreMatch.this.pickIcon);
            if (v != null) {
                Vector mid = this.pickedSpot(v);
                v.cube.moveTo(mid, now, 14, Anim::easeInOutCubic);
                if (v.model != null) {
                    v.model.sizeTo(MODEL_PICKED, now, 14, Anim::easeOutBack);
                } else {
                    v.cube.scaleTo(0.82F, now, 14, Anim::easeOutBack);
                }

                v.label.moveTo(this.labelAt(v, mid, v.model != null ? MODEL_PICKED : 0.82F), now, 14, Anim::easeInOutCubic);
                v.label.scaleTo(0.62F, now, 10);
                v.setLabel(this.viewer, Text.mm("<white><b>PICKED</b>\n<white>" + Text.esc(mapName(PreMatch.this.chosen))), BG_LOCKED, 4);
            }
        }

        void onExit() {
            int now = PreMatch.this.ticks;
            // the orbit widens, falls back and shrinks away; everything else pops out
            this.exitStart = now;
            Vector c0 = this.orbitCenter(now);

            for (Elem e : this.elems) {
                if (e.path != null) {
                    e.scaleTo(0.001F, now, 14, Anim::easeInCubic);
                } else if (e != this.title && e != this.header) {
                    e.scaleTo(e.scale(now) * 1.25F, now, 4, Anim::easeOutCubic);
                    Elem ee = e;
                    Tasks.later(4L, () -> ee.scaleTo(0.001F, now + 4, 10, Anim::easeInCubic));
                }
            }

            for (MapModelView m : this.models) {
                if (m.alive()) {
                    m.sizeTo(m.size(now) * 1.15F, now, 4, Anim::easeOutCubic);
                    Tasks.later(4L, () -> m.collapse(PreMatch.this.ticks, 10));
                }
            }

            this.title.scaleTo(0.001F, now + 4, 10, Anim::easeInCubic);
            this.header.scaleTo(0.001F, now + 2, 10, Anim::easeInCubic);
            this.viewer.spawnParticle(Particle.END_ROD, c0.getX(), c0.getY(), c0.getZ(), 24, 0.6, 0.6, 0.6, 0.04);
        }

        // ---------------------------------------------------------- per tick

        void tick() {
            int now = PreMatch.this.ticks;
            if (PreMatch.this.phase == PreMatch.Phase.BAN || PreMatch.this.phase == PreMatch.Phase.PICK) {
                this.updateHover();
            } else if (PreMatch.this.phase == PreMatch.Phase.VOTE) {
                this.updateVoteHover();
            }

            if (PreMatch.this.phase == PreMatch.Phase.ROLL) {
                for (Map.Entry<PreMatch.Icon, PreMatch.IconView> en : this.views.entrySet()) {
                    boolean lit = en.getKey() == PreMatch.this.rollLit;
                    PreMatch.IconView v = en.getValue();
                    if (lit != v.lit) {
                        v.lit = lit;
                        if (v.model != null) {
                            v.model.sizeTo(lit ? MODEL * 1.3F : MODEL, now, 3);
                        } else {
                            v.cube.scaleTo(lit ? CUBE_HOVER * 0.85F : CUBE, now, 3);
                        }

                        v.setLabel(this.viewer, Text.mm("<white>" + labelName(en.getKey().arena)), lit ? BG_LOCKED : BG_IDLE);
                    }
                }
            }

            // light sweep through the names (text only changes after the pop-in is over)
            if (now > 12 && this.title.valid() && PreMatch.this.phase != PreMatch.Phase.EXIT) {
                double phase = this.titlePhase();
                if (phase <= 1.0 || this.lastPhase <= 1.0) {
                    ((TextDisplay)this.title.entity).text(this.titleText(phase));
                }

                this.lastPhase = phase;
            }

            if (this.headerDirty || now % 20 == 0) {
                this.updateHeader();
            }

            for (PreMatch.IconView v : this.views.values()) {
                v.syncLabelToCube(now, this);
            }

            for (Elem e : this.elems) {
                e.tick(now);
            }

            for (MapModelView m : this.models) {
                m.tick(now);
            }
        }

        double lastPhase = 2.0;

        void updateHover() {
            PreMatch.Icon mine = this.hit(this.viewer);
            PreMatch.Board their = PreMatch.this.boards.get(PreMatch.this.other(this.viewer).getUniqueId());
            PreMatch.Icon theirs = their == null || their == this ? null : their.hovered;
            if (mine != this.hovered || theirs != this.theirHover) {
                PreMatch.Icon oldMine = this.hovered;
                PreMatch.Icon oldTheirs = this.theirHover;
                this.hovered = mine;
                this.theirHover = theirs;

                for (PreMatch.Icon i : new PreMatch.Icon[]{oldMine, oldTheirs, mine, theirs}) {
                    if (i != null) {
                        this.restyle(i);
                    }
                }

                if (mine != null && mine != oldMine) {
                    Snd.play(this.viewer, "block.note_block.hat", 0.4F, 1.8F);
                }
            }
        }

        void restyle(PreMatch.Icon icon) {
            PreMatch.IconView v = this.views.get(icon);
            if (v != null && !icon.banned && !icon.picked) {
                int now = PreMatch.this.ticks;
                boolean mine = icon == this.hovered;
                boolean theirs = icon == this.theirHover;
                boolean pick = PreMatch.this.phase == PreMatch.Phase.PICK;
                float base = pick ? CUBE_HOVER : CUBE;
                float s = mine || theirs ? (pick ? CUBE_HOVER * 1.25F : CUBE_HOVER) : base;
                if (v.hoverMine != mine || v.hoverTheirs != theirs) {
                    v.hoverMine = mine;
                    v.hoverTheirs = theirs;
                    v.hoverSince = now;
                    if (v.model != null) {
                        // hovered previews grow, lean in and turn so every side shows
                        float ms = mine ? MODEL_HOVER : (theirs ? MODEL_THEIRS : (pick ? MODEL_FINAL : MODEL));
                        v.model.sizeTo(pick && (mine || theirs) ? ms * 1.1F : ms, now, 6, mine || theirs ? Anim::easeOutBack : Anim::easeOutCubic);
                        v.model.spin(mine, 60.0F, now);
                    } else {
                        v.cube.scaleTo(s, now, mine || theirs ? 5 : 4, mine || theirs ? Anim::easeOutBack : Anim::easeOutCubic);
                    }
                    String name = labelName(icon.arena);
                    if (mine) {
                        boolean canAct = pick ? this.viewer.equals(PreMatch.this.picker) : PreMatch.this.bansLeft(this.viewer) > 0;
                        String action = pick ? "CLICK TO PICK" : "CLICK TO BAN";
                        v.setLabel(this.viewer, Text.mm("<white>" + name + (canAct ? "\n<white><b>" + action : "")), BG_HOVER_FROM);
                        v.fadeTo = canAct ? BG_HOVER : BG_IDLE;
                        v.fadeAt = now + 1;
                    } else if (theirs) {
                        v.setLabel(this.viewer, Text.mm("<white><b>THEIR HOVER</b>\n<white>" + name), BG_THEIRS);
                    } else {
                        v.setLabel(this.viewer, Text.mm("<white>" + name), BG_IDLE);
                    }
                }
            }
        }

        PreMatch.Icon hit(Player p) {
            Location eyeLoc = p.getEyeLocation();
            Vector e = eyeLoc.toVector();
            Vector dir = eyeLoc.getDirection();
            PreMatch.Icon best = null;
            double bestAngle = Double.MAX_VALUE;
            int now = PreMatch.this.ticks;

            for (Map.Entry<PreMatch.Icon, PreMatch.IconView> en : this.views.entrySet()) {
                PreMatch.Icon i = en.getKey();
                if (!i.banned && en.getValue().cube.valid()) {
                    Vector to = en.getValue().cube.pos(now).subtract(e);
                    double dist = to.length();
                    if (dist > 0.01) {
                        double angle = to.angle(dir);
                        PreMatch.IconView v = en.getValue();
                        double radius = v.model != null ? 0.12 + v.model.size(now) * 0.6 : 0.2 + v.cube.scale(now) * 0.45;
                        double allowed = Math.atan2(radius, dist);
                        if (angle <= allowed && angle < bestAngle) {
                            bestAngle = angle;
                            best = i;
                        }
                    }
                }
            }

            return best;
        }

        void updateVoteHover() {
            if (this.yes != null && this.no != null) {
                Location eyeLoc = this.viewer.getEyeLocation();
                Vector e = eyeLoc.toVector();
                Vector dir = eyeLoc.getDirection();
                Boolean h = null;
                double best = Math.toRadians(8.0);
                int now = PreMatch.this.ticks;

                for (PreMatch.VoteView v : new PreMatch.VoteView[]{this.yes, this.no}) {
                    double angle = v.item.pos(now).subtract(e).angle(dir);
                    if (angle < best) {
                        best = angle;
                        h = v.yes;
                    }
                }

                if (h != this.hoveredVote) {
                    Boolean old = this.hoveredVote;
                    this.hoveredVote = h;

                    for (PreMatch.VoteView v : new PreMatch.VoteView[]{this.yes, this.no}) {
                        boolean on = h != null && h == v.yes;
                        boolean was = old != null && old == v.yes;
                        if (on != was) {
                            v.item.scaleTo(on ? (v.yes ? 0.5F : 0.58F) : (v.yes ? 0.36F : 0.42F), now, 4);
                        }
                    }

                    this.refreshVoteLabels();
                    if (h != null) {
                        Snd.play(this.viewer, "block.note_block.hat", 0.4F, 1.8F);
                    }
                }
            }
        }

        void updateHeader() {
            this.headerDirty = false;
            PreMatch pm = PreMatch.this;
            String line1;
            String line2;
            String line3 = null;
            int bg;
            switch (pm.phase) {
                case BAN: {
                    Player cur = pm.turns ? pm.currentTurn() : null;
                    int total = pm.bansTotal.getOrDefault(this.viewer.getUniqueId(), 0);
                    int done = total - pm.bansLeft(this.viewer);
                    if (pm.turns && cur != null && !cur.equals(this.viewer)) {
                        int left = pm.secondsLeft(pm.turnStart, pm.turnSeconds);
                        int theirTotal = pm.bansTotal.getOrDefault(cur.getUniqueId(), 0);
                        int theirN = theirTotal - pm.bansLeft(cur) + 1;
                        line1 = "MAP VOTE - WAITING ON " + Text.esc(cur.getName().toUpperCase());
                        line2 = "They must ban " + theirN + "/" + theirTotal + "  <gray>" + left + "s";
                        bg = BG_WAIT;
                    } else if (pm.bansLeft(this.viewer) <= 0) {
                        line1 = "MAP VOTE - WAITING";
                        line2 = "Waiting for the other bans";
                        bg = BG_WAIT;
                    } else {
                        int left = pm.turns ? pm.secondsLeft(pm.turnStart, pm.turnSeconds) : pm.secondsLeft(pm.phaseStart, Cfg.i(Opt.MAP_BAN_SECONDS));
                        boolean low = left <= 3;
                        line1 = "MAP VOTE - YOUR TURN: BAN " + Math.min(total, done + 1) + "/" + total;
                        line2 = "Look at a map and click to ban it  " + (low ? "<red>" : "<gray>") + left + "s";
                        bg = low ? BG_LOW : BG_TURN;
                    }
                    break;
                }
                case PICK: {
                    int left = pm.secondsLeft(pm.phaseStart, Cfg.i(Opt.MAP_BAN_TURN_SECONDS));
                    if (this.viewer.equals(pm.picker)) {
                        boolean low = left <= 3;
                        line1 = "MAP VOTE - YOUR TURN: PICK THE MAP";
                        line2 = "Look at a map and click to pick it  " + (low ? "<red>" : "<gray>") + left + "s";
                        bg = low ? BG_LOW : BG_TURN;
                    } else {
                        line1 = "MAP VOTE - WAITING ON " + Text.esc(pm.picker.getName().toUpperCase());
                        line2 = "They must click the final map  <gray>" + left + "s";
                        bg = BG_WAIT;
                    }
                    break;
                }
                case ROLL:
                    line1 = "MAP VOTE - PICKING A MAP";
                    line2 = "Rolling the remaining maps...";
                    bg = BG_WAIT;
                    break;
                case LOCK:
                    line1 = "MAP VOTE - MAP LOCKED IN";
                    line2 = Text.esc(mapName(pm.chosen)) + (votesFor(pm.match.kit).isEmpty() || !Cfg.bool(Opt.VOTES) ? " selected." : " selected. Voting preferences next.");
                    bg = BG_LOCKED;
                    break;
                case VOTE: {
                    KitVote v = pm.pending.get(pm.voteIndex);
                    int left = pm.secondsLeft(pm.phaseStart, Cfg.i(Opt.VOTE_SECONDS));
                    boolean low = left <= 5;
                    line1 = "PREFERENCE VOTE - " + Text.esc(v.question());
                    line2 = "Look at an option and click  " + (low ? "<red>" : "<gray>") + left + "s";
                    bg = low ? BG_LOW : BG_WAIT;
                    break;
                }
                case RESULT: {
                    KitVote v = pm.pending.get(pm.voteIndex);
                    boolean agreed = pm.votes.size() >= 2 && pm.votes.values().stream().distinct().count() == 1L;
                    line1 = "PREFERENCE VOTE - RESULT";
                    line2 = agreed ? "<#9CFF9C>Both agreed" : "<#FFB4B4>No agreement";
                    line3 = "<green>Selected:</green> " + Text.esc(v.question()) + (Boolean.TRUE.equals(pm.voteResult) ? " Enabled" : " Disabled");
                    bg = BG_LOCKED;
                    break;
                }
                default:
                    line1 = "MAP VOTE - LOCKED IN";
                    line2 = "Starting on " + Text.esc(pm.chosen == null ? "?" : mapName(pm.chosen));
                    bg = BG_LOCKED;
            }

            String text = "<white><b>" + line1 + "</b>\n<white>" + line2 + (line3 == null ? "" : "\n<white>" + line3);
            if (!text.equals(this.lastHeader) || bg != this.headerBg) {
                TextDisplay t = (TextDisplay)this.header.entity;
                if (t.isValid()) {
                    t.text(Text.mm(text));
                    if (bg != this.headerBg) {
                        // background colour is interpolated by the client
                        t.setInterpolationDelay(0);
                        t.setInterpolationDuration(4);
                        t.setBackgroundColor(Color.fromARGB(bg));
                    }
                }

                this.lastHeader = text;
                this.headerBg = bg;
            }
        }

        boolean owns(Entity e) {
            for (Elem x : this.elems) {
                if (x.entity.equals(e)) {
                    return true;
                }
            }

            for (MapModelView m : this.models) {
                if (m.owns(e)) {
                    return true;
                }
            }

            return false;
        }

        void removeAll() {
            for (Elem e : this.elems) {
                e.remove();
            }

            for (MapModelView m : this.models) {
                m.remove();
            }

            this.elems.clear();
            this.models.clear();
        }
    }

    private static double angleDiff(double a, double b) {
        double d = (a - b) % (Math.PI * 2.0);
        if (d > Math.PI) {
            d -= Math.PI * 2.0;
        } else if (d < -Math.PI) {
            d += Math.PI * 2.0;
        }

        return d;
    }

    private final class IconView {
        Elem cube;
        Elem label;
        /** Lines of the map name (long names wrap). */
        int lines = 1;
        /** The 3D preview, or null when the map shows as its block. */
        MapModelView model;
        boolean hoverMine;
        boolean hoverTheirs;
        boolean lit;
        int hoverSince;
        int fadeTo = -1;
        int fadeAt = -1;
        Component labelText;
        int labelBg = BG_IDLE;

        double hoverAmount(int t) {
            boolean on = this.hoverMine || this.hoverTheirs;
            double k = Anim.easeOutCubic((t - this.hoverSince) / 4.0);
            if (on) {
                return k;
            } else {
                return this.hoverSince == 0 ? 0.0 : 1.0 - k;
            }
        }

        void setLabel(Player viewer, Component text, int bg) {
            this.setLabel(viewer, text, bg, 3);
        }

        void setLabel(Player viewer, Component text, int bg, int fadeTicks) {
            TextDisplay t = (TextDisplay)this.label.entity;
            if (t.isValid()) {
                if (!text.equals(this.labelText)) {
                    t.text(text);
                    this.labelText = text;
                    // the label grows upward as lines are added ("CLICK TO BAN", "THEIR HOVER"), so it moves down by the
                    // same amount and never covers its map
                    this.lines = Math.max(1, Text.plain(text).split("\n", -1).length);
                }

                if (bg != this.labelBg) {
                    t.setInterpolationDelay(0);
                    t.setInterpolationDuration(fadeTicks);
                    t.setBackgroundColor(Color.fromARGB(bg));
                    this.labelBg = bg;
                }
            }
        }

        /** Keeps the label under the cube while the cube grows or moves, and runs the hover background fade. */
        void syncLabelToCube(int now, PreMatch.Board b) {
            if (this.fadeTo != -1 && now >= this.fadeAt) {
                TextDisplay t = (TextDisplay)this.label.entity;
                if (t.isValid() && this.hoverMine) {
                    t.setInterpolationDelay(0);
                    t.setInterpolationDuration(4);
                    t.setBackgroundColor(Color.fromARGB(this.fadeTo));
                    this.labelBg = this.fadeTo;
                }

                this.fadeTo = -1;
            }

            if (this.cube.path == null && this.label.valid() && this.label.scaleTarget() > 0.01F) {
                int t = now + Elem.PERIOD;
                Vector want = b.labelAt(this, this.cube.pos(t), this.model != null ? this.model.size(t) : this.cube.scale(t));
                Vector have = this.label.target();
                if (want.distanceSquared(have) > 1.0E-4) {
                    this.label.moveTo(want, now, Elem.PERIOD + 1, Anim::easeInOutSine);
                }
            }
        }
    }

    private final class VoteView {
        boolean yes;
        Vector home;
        Elem item;
        Elem label;
        boolean theirs;
        boolean orbiting;

        void setLabel(Player viewer, Component text, int bg) {
            TextDisplay t = (TextDisplay)this.label.entity;
            if (t.isValid()) {
                t.text(text);
                t.setInterpolationDelay(0);
                t.setInterpolationDuration(3);
                t.setBackgroundColor(Color.fromARGB(bg));
            }
        }
    }

    private static final class Icon {
        final Arena arena;
        final int index;
        boolean banned;
        boolean picked;
        int orbitIndex;
        double offFrom;
        double offTo;
        int offT0 = Integer.MIN_VALUE;

        Icon(Arena arena, int index) {
            this.arena = arena;
            this.index = index;
        }

        /** Angular offset on the ring at tick t (eases over 24 ticks when the spacing changes). */
        double offset(int t) {
            if (this.offT0 == Integer.MIN_VALUE) {
                return this.offTo;
            } else {
                double k = Anim.easeInOutSine((t - this.offT0) / 24.0);
                return this.offFrom + angleDiff(this.offTo, this.offFrom) * k;
            }
        }
    }

    private static enum Phase {
        BAN,
        PICK,
        ROLL,
        LOCK,
        VOTE,
        RESULT,
        LOCKED_IN,
        EXIT,
        DONE;
    }
}
