package net.practicepvp.match;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.practicepvp.PracticePlugin;
import net.practicepvp.arena.Arena;
import net.practicepvp.arena.ArenaGenerator;
import net.practicepvp.arena.ArenaTheme;
import net.practicepvp.build.ProArenaDesign;
import net.practicepvp.session.Tp;
import net.practicepvp.util.Text;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * The waiting room (1.0.9): where matched players stand while their map, or one more copy of it, is built. Two pods face
 * each other through a glass wall, built from the blocks of the map being prepared (its floor, walls, pillars, roof,
 * lights and banners), with a progress bar, animated titles and the opponent's name. The moment the map is ready the
 * players are taken in and the room is taken down.
 */
final class WaitingRoom {
    private static final AtomicInteger SLOTS = new AtomicInteger();
    private final PracticePlugin plugin;
    private final Match match;
    private final List<Player> players;
    private final Map<Block, BlockData> changed = new LinkedHashMap<>();
    private final List<Chunk> tickets = new ArrayList<>();
    private final Map<Player, BossBar> bars = new LinkedHashMap<>();
    private Arena arena;
    private String[] look;
    private World world;
    private int bx;
    private int by;
    private int bz;
    private int ticks;
    private boolean closed;

    WaitingRoom(PracticePlugin plugin, Match match, Arena arena, List<Player> players) {
        this.plugin = plugin;
        this.match = match;
        this.arena = arena;
        this.players = new ArrayList<>(players);
    }

    /** The pro theme whose look a map's room takes. */
    static ProArenaDesign.Theme lookOf(ArenaTheme t) {
        if (t == null) {
            return ProArenaDesign.Theme.MEDIEVAL;
        }

        if (t.pro != null) {
            return t.pro;
        }

        if (t.court != null) {
            return switch (t.court) {
                case FROSTHOLD -> ProArenaDesign.Theme.FROST;
                case SULTAN_PALACE -> ProArenaDesign.Theme.DESERT;
                case TITAN_COLOSSEUM -> ProArenaDesign.Theme.GLADIATOR;
                case HIGHLAND_PLAZA -> ProArenaDesign.Theme.HIGHLAND;
                case SAKURA_TERRACE -> ProArenaDesign.Theme.SAKURA;
                case EMBER_SANDS -> ProArenaDesign.Theme.EMBER;
                case GOTHIC_HARBOR -> ProArenaDesign.Theme.GOTHIC;
                case TROPICAL_BEACH -> ProArenaDesign.Theme.TROPICAL;
                case JUNGLE_TEMPLE -> ProArenaDesign.Theme.JUNGLE;
                case VOLCANO_FORGE -> ProArenaDesign.Theme.VOLCANO;
                case PIRATE_COVE -> ProArenaDesign.Theme.HARBOR;
                case PHARAOH_TOMB -> ProArenaDesign.Theme.PHARAOH;
                default -> ProArenaDesign.Theme.MEDIEVAL;
            };
        }

        if (t.colosseum != null) {
            return t == ArenaTheme.GRAND_COLOSSEUM ? ProArenaDesign.Theme.GRAND : ProArenaDesign.Theme.ROMAN;
        }

        return switch (t) {
            case FLATLANDS, MOLE_MINES -> ProArenaDesign.Theme.HIGHLAND;
            case BADLANDS, BADLANDS_DESERT -> ProArenaDesign.Theme.EMBER;
            case DESERT, DESERT_PALACE -> ProArenaDesign.Theme.DESERT;
            case SNOWY -> ProArenaDesign.Theme.FROST;
            case CHERRY -> ProArenaDesign.Theme.SAKURA;
            case PALE -> ProArenaDesign.Theme.GOTHIC;
            case CRIMSON -> ProArenaDesign.Theme.VOLCANO;
            case END, END_OUTER -> ProArenaDesign.Theme.STARFALL;
            case MUSHROOM -> ProArenaDesign.Theme.HARBOR;
            case OLYMPUS -> ProArenaDesign.Theme.ROMAN;
            default -> ProArenaDesign.Theme.MEDIEVAL;
        };
    }

    private String name() {
        return this.arena == null ? "your arena" : Text.esc(this.arena.displayName);
    }

    void open() {
        this.world = this.plugin.worlds().practiceWorld();
        if (this.world == null) {
            return;
        }

        int slot = Math.floorMod(SLOTS.getAndIncrement(), 256);
        this.bx = -60000 + slot % 16 * 48;
        this.bz = -60000 + slot / 16 * 48;
        this.by = Math.min(this.world.getMaxHeight() - 12, 200);
        this.look = ProArenaDesign.roomBlocks(lookOf(this.arena == null ? null : this.arena.theme));
        for (int cx = (this.bx - 8) >> 4; cx <= (this.bx + 8) >> 4; cx++) {
            for (int cz = (this.bz - 5) >> 4; cz <= (this.bz + 5) >> 4; cz++) {
                Chunk c = this.world.getChunkAt(cx, cz);
                c.addPluginChunkTicket(this.plugin);
                this.tickets.add(c);
            }
        }

        this.build();
        for (int i = 0; i < this.players.size(); i++) {
            Player p = this.players.get(i);
            boolean left = i % 2 == 0;
            int row = i / 2;
            double x = this.bx + (left ? -3.5 : 4.5);
            double z = this.bz + 0.5 + (row % 2 == 0 ? 0 : (row % 4 == 1 ? 1.5 : -1.5));
            Location at = new Location(this.world, x, this.by + 1, z, left ? -90.0F : 90.0F, 0.0F);
            p.setFallDistance(0.0F);
            Tp.to(p, at);
            p.showTitle(Title.title(Text.mm("<gradient:#FFD36A:#FF8A3D><b>PREPARING YOUR ARENA"),
                Text.mm("<gray>" + this.name() + " <dark_gray>• <white>a few seconds"),
                Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofMillis(2200), java.time.Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7F, 1.4F);
            BossBar bar = BossBar.bossBar(Component.empty(), 0.0F, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_20);
            p.showBossBar(bar);
            this.bars.put(p, bar);
        }

        this.tick(this.arena);
    }

    private void put(int x, int y, int z, String block) {
        Block b = this.world.getBlockAt(x, y, z);
        this.changed.putIfAbsent(b, b.getBlockData());
        b.setBlockData(ArenaGenerator.state("minecraft:" + block), false);
    }

    /** Two pods side by side, a glass wall between them; walls, pillars, roof, lights and banners in the map's style. */
    private void build() {
        String[] l = this.look;
        String floor = l[0];
        String wall = l[1];
        String accent = l[2];
        String pillar = l[3];
        String trim = l[4];
        String roof = l[5];
        String glass = l[6];
        String light = l[7];
        String banner = l[8];
        int y = this.by;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean rim = Math.abs(dx) == 6 || Math.abs(dz) == 3;
                this.put(this.bx + dx, y, this.bz + dz, rim ? trim : (Math.abs(dx) >= 2 && Math.abs(dx) <= 4 && Math.abs(dz) <= 1 ? accent : floor));
                this.put(this.bx + dx, y + 5, this.bz + dz, rim ? trim : roof);
                for (int k = 1; k <= 4; k++) {
                    boolean corner = Math.abs(dx) == 6 && Math.abs(dz) == 3 || dx == 0 && Math.abs(dz) == 3 || Math.abs(dz) == 3 && Math.abs(dx) == 3;
                    String b = null;
                    if (dx == 0 && Math.abs(dz) < 3) {
                        b = glass;
                    } else if (corner) {
                        b = pillar;
                    } else if (Math.abs(dx) == 6 || Math.abs(dz) == 3) {
                        boolean window = Math.abs(dz) == 3 && (k == 2 || k == 3) && Math.abs(dx) != 3;
                        b = window ? glass : (k == 4 ? trim : wall);
                    }

                    if (b != null) {
                        this.put(this.bx + dx, y + k, this.bz + dz, b);
                    }
                }
            }
        }

        for (int s = -1; s <= 1; s += 2) {
            this.put(this.bx + s * 3, y + 4, this.bz, light + "[hanging=true]");
            this.put(this.bx + s * 5, y + 3, this.bz, banner + "_wall_banner[facing=" + (s < 0 ? "east" : "west") + "]");
            this.put(this.bx + s * 5, y + 1, this.bz - 2, light + "[hanging=false]");
            this.put(this.bx + s * 5, y + 1, this.bz + 2, light + "[hanging=false]");
        }
    }

    /** Called every second while the players wait: progress bar, animated text, particles. */
    void tick(Arena building) {
        if (this.closed || this.world == null) {
            return;
        }

        if (building != null) {
            this.arena = building;
        }

        this.ticks++;
        net.practicepvp.arena.OnDemand od = this.plugin.arenas().onDemand();
        double t = od.secondsBuilding(this.arena);
        boolean vanilla = this.arena != null && this.arena.theme != null && this.arena.theme.isVanilla();
        double expected = vanilla ? 90.0 : 14.0;
        float progress = (float)Math.min(0.95, 1.0 - Math.exp(-Math.max(t, this.ticks * 0.8) / (expected / 2.2)));
        int pct = Math.round(progress * 100.0F);
        String dots = ".".repeat(1 + this.ticks % 3);
        for (Map.Entry<Player, BossBar> e : this.bars.entrySet()) {
            Player p = e.getKey();
            if (!p.isOnline()) {
                continue;
            }

            BossBar bar = e.getValue();
            bar.progress(progress);
            bar.name(Text.mm("<gold>⚒ <white>Building <yellow>" + this.name() + " <dark_gray>• <white>" + pct + "%"));
            List<String> others = new ArrayList<>();
            for (Player o : this.players) {
                if (o != p && o.isOnline()) {
                    others.add(o.getName());
                }
            }

            String opp = others.isEmpty() ? "your opponent" : String.join(", ", others);
            p.sendActionBar(Text.mm("<green>✔ <white>" + Text.esc(opp) + " <gray>is ready <dark_gray>• <gray>the arena opens in a moment" + dots));
            Location c = p.getLocation().add(0.0, 1.0, 0.0);
            double a = this.ticks * 0.9;
            for (int k = 0; k < 6; k++) {
                double ang = a + k * Math.PI / 3.0;
                p.spawnParticle(Particle.END_ROD, c.clone().add(Math.cos(ang) * 1.3, Math.sin(this.ticks * 0.6 + k) * 0.6, Math.sin(ang) * 1.3), 1, 0, 0, 0, 0);
            }

            if (this.ticks % 4 == 0) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35F, 1.2F + (this.ticks % 8) * 0.05F);
            }
        }
    }

    /** The map is ready: a full bar and a flash, then the match takes them in. */
    void ready(Arena a) {
        if (this.closed) {
            return;
        }

        this.arena = a;
        for (Map.Entry<Player, BossBar> e : this.bars.entrySet()) {
            Player p = e.getKey();
            if (!p.isOnline()) {
                continue;
            }

            e.getValue().progress(1.0F);
            e.getValue().color(BossBar.Color.GREEN);
            e.getValue().name(Text.mm("<green><b>✔ " + this.name() + " is ready"));
            p.showTitle(Title.title(Text.mm("<gradient:#7CFF9B:#3DD6FF><b>ARENA READY"), Text.mm("<gray>" + this.name()),
                Title.Times.times(java.time.Duration.ofMillis(150), java.time.Duration.ofMillis(900), java.time.Duration.ofMillis(250))));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.6F);
            p.spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.25);
        }
    }

    /** Takes the room down and puts back what was there. */
    void close() {
        if (this.closed) {
            return;
        }

        this.closed = true;
        for (Map.Entry<Player, BossBar> e : this.bars.entrySet()) {
            e.getKey().hideBossBar(e.getValue());
        }

        this.bars.clear();
        if (this.world == null) {
            return;
        }

        for (Map.Entry<Block, BlockData> e : this.changed.entrySet()) {
            e.getKey().setBlockData(e.getValue(), false);
        }

        this.changed.clear();
        for (Chunk c : this.tickets) {
            c.removePluginChunkTicket(this.plugin);
        }

        this.tickets.clear();
    }
}
