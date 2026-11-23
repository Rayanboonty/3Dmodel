package net.practicepvp.event;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.practicepvp.PracticePlugin;
import net.practicepvp.arena.Arena;
import net.practicepvp.arena.ArenaTheme;
import net.practicepvp.gui.Menu;
import net.practicepvp.kit.Kit;
import net.practicepvp.session.PlayerState;
import net.practicepvp.util.Items;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Where tournament matches are played (3.15.0). No vote: the host picks one of four ways when hosting.
 *
 * <ul>
 *   <li><b>Road to the Colosseum</b>: every round has its own arena. The whole road is shown when the bracket
 *   starts, each stop is revealed to the tournament with a title when its round begins, and the final is played in
 *   the Champions Colosseum.</li>
 *   <li><b>Champion's Choice</b>: the higher-ranked player of each match earns the pick. They choose the arena from
 *   five; their opponent watches the pick live. Ten seconds, then fate picks.</li>
 *   <li><b>Arena Roulette</b>: both players watch a slot machine spin through the arenas, slow down and stop on one.</li>
 *   <li><b>Colosseum only</b>: every match in the Champions Colosseum (the old way).</li>
 * </ul>
 * The final is in the Champions Colosseum whenever it is built and the host left that on.
 */
public final class TournamentArenas {
    public enum Mode {
        ROAD("Road to the Colosseum", "Every round in a new arena, revealed live"),
        CHOICE("Champion's Choice", "The higher-ranked player picks the arena"),
        ROULETTE("Arena Roulette", "A slot machine spins and picks the arena"),
        COLOSSEUM("Colosseum only", "Every match in the Champions Colosseum");

        public final String display;
        public final String info;

        Mode(String display, String info) {
            this.display = display;
            this.info = info;
        }

        public static Mode parse(String s) {
            try {
                return valueOf(String.valueOf(s).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ROAD;
            }
        }
    }

    private static final int PICK_SECONDS = 10;
    private final PracticePlugin plugin;

    TournamentArenas(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ arenas

    /** The arenas a tournament of this kit can be played in right now (free copies), the colosseum kept for the final. */
    List<Arena> pool(Kit kit) {
        List<Arena> out = new ArrayList<>();
        for (Arena a : this.plugin.arenas().available(kit)) {
            if (!EventManager.COLOSSEUM_ID.equals(a.id)) {
                out.add(a);
            }
        }

        return out;
    }

    /** Every arena the kit plays on, free or not (to plan the road). */
    private List<Arena> all(Kit kit) {
        List<Arena> out = new ArrayList<>();
        for (Arena a : this.plugin.arenas().forKit(kit)) {
            if (!EventManager.COLOSSEUM_ID.equals(a.id)) {
                out.add(a);
            }
        }

        return out;
    }

    static String nameOf(Arena a) {
        return a.displayName != null && !a.displayName.isBlank() ? a.displayName : (a.theme != null && a.theme != ArenaTheme.CUSTOM ? a.theme.display : a.id);
    }

    private static Material iconOf(Arena a) {
        return a.theme != null && a.theme.icon != null && a.theme.icon.isItem() ? a.theme.icon : Material.FILLED_MAP;
    }

    /** A free copy of this arena, or of another one of the kit when it is full (null = none free at all). */
    Arena freeOr(Arena want, Kit kit) {
        if (want != null && want.ready() && want.freeInstances() > 0L) {
            return want;
        }

        List<Arena> pool = this.pool(kit);
        return pool.isEmpty() ? null : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    // ------------------------------------------------------------------ the road

    /**
     * Plans the road once the bracket is drawn: a different arena for every round before the final, the final in the
     * Champions Colosseum (when it is on), and shows the whole road to the tournament.
     */
    void planRoad(EventManager.Event e, int rounds, List<String> stages, boolean colosseumFinal, Consumer<Component> announce) {
        List<Arena> arenas = this.all(e.kit);
        Collections.shuffle(arenas);
        StringBuilder road = new StringBuilder("<gold><b>⚔ THE ROAD TO THE COLOSSEUM</b>");
        for (int r = 0; r < rounds; r++) {
            boolean fin = r == rounds - 1;
            String id;
            if (fin && colosseumFinal) {
                id = EventManager.COLOSSEUM_ID;
            } else if (!arenas.isEmpty()) {
                id = arenas.get(r % arenas.size()).id;
            } else {
                continue;
            }

            e.road.put(r, id);
            Arena a = this.plugin.arenas().get(id);
            road.append("\n<dark_gray> » <yellow>").append(Text.esc(stages.get(r))).append("</yellow> <gray>in</gray> <white>")
                .append(Text.esc(a == null ? id : nameOf(a))).append("</white>");
        }

        if (!e.road.isEmpty()) {
            announce.accept(Text.mm(road.toString()));
        }
    }

    /** The arena of a road round: revealed to everyone still in the tournament the first time a match of it starts. */
    Arena road(EventManager.Event e, int round, String stage, List<Player> audience) {
        String id = e.road.get(round);
        Arena a = id == null ? null : this.plugin.arenas().get(id);
        if (a != null && e.revealed.add(round)) {
            reveal(audience, stage, a, "<gray>the road leads to");
        }

        return this.freeOr(a, e.kit);
    }

    /** A big title for the players: the stage on top, the arena under it, and a fanfare. */
    static void reveal(List<Player> to, String stage, Arena a, String lead) {
        Title t = Title.title(
            Text.mm("<gold><b>⚔ " + Text.esc(stage.toUpperCase(Locale.ROOT)) + "</b>"),
            Text.mm(lead + " <white><b>" + Text.esc(nameOf(a)) + "</b>"),
            Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2200), Duration.ofMillis(500))
        );
        for (Player p : to) {
            if (p != null && p.isOnline()) {
                p.showTitle(t);
                Snd.play(p, "ui.toast.challenge_complete", 0.8F, 1.0F);
                Snd.play(p, "block.beacon.activate", 0.6F, 1.4F);
            }
        }
    }

    // ------------------------------------------------------------------ choice and roulette

    /** Starts the pick of a match (Champion's Choice or Arena Roulette); {@code done} gets the arena on the main thread. */
    void pick(EventManager.Event e, Mode mode, Player a, Player b, String stage, int eloA, int eloB, Consumer<Component> announce, Consumer<Arena> done) {
        List<Arena> pool = this.pool(e.kit);
        Collections.shuffle(pool);
        if (pool.size() > (mode == Mode.CHOICE ? 5 : 7)) {
            pool = new ArrayList<>(pool.subList(0, mode == Mode.CHOICE ? 5 : 7));
        }

        if (pool.isEmpty()) {
            done.accept(null);
            return;
        }

        if (pool.size() == 1) {
            reveal(List.of(a, b), stage, pool.get(0), "<gray>you fight in");
            Arena only = pool.get(0);
            Tasks.later(40L, () -> done.accept(only));
            return;
        }

        if (mode == Mode.CHOICE) {
            Player chooser = eloA > eloB ? a : (eloB > eloA ? b : (ThreadLocalRandom.current().nextBoolean() ? a : b));
            new Choice(this, e, pool, chooser, chooser == a ? b : a, stage, announce, done).begin();
        } else {
            new Roulette(this, pool, a, b, stage, announce, done).begin();
        }
    }

    private boolean available(Player p) {
        if (p == null || !p.isOnline()) {
            return false;
        }

        PlayerState st = this.plugin.sessions().state(p);
        return st == PlayerState.LOBBY || st == PlayerState.QUEUE;
    }

    private static ItemStack arenaItem(Arena a, String... lore) {
        return Items.of(iconOf(a)).name("<gold><b>" + Text.esc(nameOf(a))).lore(lore).hideAll().build();
    }

    /** Champion's Choice: the chooser clicks one of five arenas; the opponent watches the same board. */
    private static final class Choice {
        private final TournamentArenas owner;
        private final List<Arena> pool;
        private final Player chooser;
        private final Player other;
        private final String stage;
        private final Consumer<Component> announce;
        private final Consumer<Arena> done;
        private final List<Board> boards = new ArrayList<>();
        private BukkitTask timer;
        private int left = PICK_SECONDS;
        private boolean over;

        Choice(TournamentArenas owner, EventManager.Event e, List<Arena> pool, Player chooser, Player other, String stage, Consumer<Component> announce, Consumer<Arena> done) {
            this.owner = owner;
            this.pool = pool;
            this.chooser = chooser;
            this.other = other;
            this.stage = stage;
            this.announce = announce;
            this.done = done;
        }

        void begin() {
            this.announce.accept(Text.mm("<gold>⚔ <white>" + Text.esc(this.chooser.getName()) + "</white> <gray>earned the pick for the "
                + Text.esc(this.stage) + " vs <white>" + Text.esc(this.other.getName()) + "</white>..."));
            for (Player p : List.of(this.chooser, this.other)) {
                Board b = new Board(this, p);
                this.boards.add(b);
                b.open();
                Snd.play(p, "block.note_block.bell", 1.0F, 1.2F);
            }

            this.timer = Tasks.timer(20L, 20L, this::second);
        }

        private void second() {
            if (this.over) {
                return;
            }

            if (!this.owner.available(this.chooser) || !this.owner.available(this.other)) {
                this.end(null, false);
                return;
            }

            this.left--;
            if (this.left <= 0) {
                this.end(this.pool.get(ThreadLocalRandom.current().nextInt(this.pool.size())), true);
                return;
            }

            for (Board b : this.boards) {
                b.redraw();
                if (this.left <= 3) {
                    Snd.play(b.viewer(), "block.note_block.hat", 1.0F, 1.6F);
                }
            }
        }

        void choose(Player by, Arena a) {
            if (!this.over && by == this.chooser) {
                this.end(a, false);
            }
        }

        void reopen(Board b) {
            if (!this.over) {
                Tasks.later(2L, () -> {
                    if (!this.over && b.viewer().isOnline()) {
                        b.open();
                    }
                });
            }
        }

        private void end(Arena a, boolean timedOut) {
            this.over = true;
            Tasks.cancel(this.timer);
            for (Board b : this.boards) {
                if (b.viewer().isOnline() && b.viewer().getOpenInventory().getTopInventory().getHolder(false) == b) {
                    b.closeQuietly();
                }
            }

            if (a == null) {
                this.done.accept(null);
                return;
            }

            this.announce.accept(Text.mm("<gold>⚔ <white>" + Text.esc(this.chooser.getName()) + "</white> <gray>"
                + (timedOut ? "ran out of time - fate picked" : "picked") + " <yellow>" + Text.esc(nameOf(a)) + "</yellow> for the " + Text.esc(this.stage) + "."));
            reveal(List.of(this.chooser, this.other), this.stage, a, timedOut ? "<gray>fate picked" : "<gray>" + Text.esc(this.chooser.getName()) + " picked");
            Tasks.later(40L, () -> this.done.accept(a));
        }

        private static final class Board extends Menu {
            private final Choice c;

            Board(Choice c, Player viewer) {
                super(c.owner.plugin, viewer);
                this.c = c;
            }

            @Override
            protected Component title() {
                return Text.mm("<gold><b>⚔ Champion's Choice</b> <dark_gray>» <gray>" + Text.esc(this.c.stage));
            }

            @Override
            protected int rows() {
                return 3;
            }

            @Override
            protected void draw() {
                boolean picks = this.viewer == this.c.chooser;
                int start = 13 - this.c.pool.size() / 2;
                for (int i = 0; i < this.c.pool.size(); i++) {
                    Arena a = this.c.pool.get(i);
                    ItemStack it = picks ? arenaItem(a, "<gray>Your opponent: <white>" + Text.esc(this.c.other.getName()), "", "<green>▶ Click to fight here")
                        : arenaItem(a, "<gray>" + Text.esc(this.c.chooser.getName()) + " is picking...");
                    this.set(start + i, it, picks ? t -> this.c.choose(this.viewer, a) : null);
                }

                this.set(4, Items.of(Material.GOLDEN_HELMET).name("<gold><b>" + Text.esc(this.c.chooser.getName()) + " picks")
                    .lore("<gray>The higher-ranked player of the match", "<gray>earns the choice of arena.").hideAll().build());
                this.set(22, Items.of(Material.CLOCK).name("<yellow><b>" + this.c.left + "s</b> <gray>left").amount(Math.max(1, this.c.left))
                    .lore("<gray>No pick in time: fate picks.").build());
                this.fill(Material.BLACK_STAINED_GLASS_PANE);
            }

            @Override
            public void onClose() {
                this.c.reopen(this);
            }
        }
    }

    /** Arena Roulette: a slot machine of arena icons, spinning past a pointer, slowing down, stopping on one. */
    private static final class Roulette {
        private final TournamentArenas owner;
        private final List<Arena> pool;
        private final Player a;
        private final Player b;
        private final String stage;
        private final Consumer<Component> announce;
        private final Consumer<Arena> done;
        private final List<Strip> strips = new ArrayList<>();
        private final int target;
        private final int steps;
        private int offset;
        private int wait;
        private int step;
        private BukkitTask task;
        private boolean over;

        Roulette(TournamentArenas owner, List<Arena> pool, Player a, Player b, String stage, Consumer<Component> announce, Consumer<Arena> done) {
            this.owner = owner;
            this.pool = pool;
            this.a = a;
            this.b = b;
            this.stage = stage;
            this.announce = announce;
            this.done = done;
            this.target = ThreadLocalRandom.current().nextInt(pool.size());
            // spin at least three times round the pool, ending with the target under the pointer (slot 4 of the strip)
            int n = pool.size();
            int base = 3 * n + 14;
            int s = base;
            while (Math.floorMod(s + 4, n) != this.target) {
                s++;
            }

            this.steps = s;
        }

        void begin() {
            this.announce.accept(Text.mm("<gold>⚔ <gray>The arena roulette spins for <white>" + Text.esc(this.a.getName()) + "</white> vs <white>"
                + Text.esc(this.b.getName()) + "</white> <dark_gray>(" + Text.esc(this.stage) + ")"));
            for (Player p : List.of(this.a, this.b)) {
                Strip s = new Strip(this, p);
                this.strips.add(s);
                s.open();
            }

            this.task = Tasks.timer(10L, 1L, this::tick);
        }

        Arena at(int slot) {
            return this.pool.get(Math.floorMod(this.offset + slot, this.pool.size()));
        }

        private void tick() {
            if (this.over) {
                return;
            }

            if (!this.owner.available(this.a) || !this.owner.available(this.b)) {
                this.stop(null);
                return;
            }

            if (this.wait > 0) {
                this.wait--;
                return;
            }

            this.offset++;
            this.step++;
            // fast at first, then each click a little slower
            int remaining = this.steps - this.step;
            this.wait = remaining > 14 ? 0 : (remaining > 8 ? 1 : (remaining > 4 ? 3 : 5 + (4 - remaining)));
            float pitch = 0.8F + 1.2F * Math.min(1.0F, (float)this.step / this.steps);
            for (Strip s : this.strips) {
                s.redraw();
                Snd.play(s.viewer(), "block.note_block.hat", 0.9F, pitch);
            }

            if (this.step >= this.steps) {
                this.stop(this.pool.get(this.target));
            }
        }

        private void stop(Arena won) {
            this.over = true;
            Tasks.cancel(this.task);
            if (won == null) {
                for (Strip s : this.strips) {
                    if (s.viewer().isOnline()) {
                        s.closeQuietly();
                    }
                }

                this.done.accept(null);
                return;
            }

            for (Strip s : this.strips) {
                s.won = true;
                s.redraw();
                Snd.play(s.viewer(), "entity.player.levelup", 1.0F, 1.0F);
            }

            Tasks.later(30L, () -> {
                for (Strip s : this.strips) {
                    if (s.viewer().isOnline() && s.viewer().getOpenInventory().getTopInventory().getHolder(false) == s) {
                        s.closeQuietly();
                    }
                }

                this.announce.accept(Text.mm("<gold>⚔ <gray>The roulette sends <white>" + Text.esc(this.a.getName()) + "</white> and <white>"
                    + Text.esc(this.b.getName()) + "</white> to <yellow>" + Text.esc(nameOf(won)) + "</yellow>."));
                reveal(List.of(this.a, this.b), this.stage, won, "<gray>the roulette stops on");
                Tasks.later(40L, () -> this.done.accept(won));
            });
        }

        private static final class Strip extends Menu {
            private final Roulette r;
            boolean won;

            Strip(Roulette r, Player viewer) {
                super(r.owner.plugin, viewer);
                this.r = r;
            }

            @Override
            protected Component title() {
                return Text.mm("<gold><b>⚔ Arena Roulette</b> <dark_gray>» <gray>" + Text.esc(this.r.stage));
            }

            @Override
            protected int rows() {
                return 3;
            }

            @Override
            protected void draw() {
                for (int i = 0; i < 9; i++) {
                    Arena a = this.r.at(i);
                    boolean mid = i == 4;
                    ItemStack it = Items.of(iconOf(a)).name((mid ? "<gold><b>" : "<gray>") + Text.esc(nameOf(a)))
                        .glint(mid && this.won).hideAll().build();
                    this.set(9 + i, it);
                }

                this.set(4, Items.of(this.won ? Material.NETHER_STAR : Material.HOPPER).name(this.won ? "<gold><b>" + Text.esc(nameOf(this.r.at(4))) : "<yellow><b>▼").build());
                this.set(22, Items.of(this.won ? Material.LIME_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE).name(this.won ? "<green><b>Arena picked!" : "<yellow>Spinning...").build());
                this.fill(this.won ? Material.LIME_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE);
            }

            @Override
            public void onClose() {
                if (!this.r.over) {
                    Tasks.later(2L, () -> {
                        if (!this.r.over && this.viewer.isOnline()) {
                            this.open();
                        }
                    });
                }
            }
        }
    }
}
