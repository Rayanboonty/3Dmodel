package net.vortexpvp.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.arena.ArenaInstance;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.fx.Fx;
import net.vortexpvp.session.PlayerReset;
import net.vortexpvp.session.Session;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Cuboid;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

final class StartSequence {
    private final Match match;
    private Opt.StartMode mode;
    private final int seconds;
    private final List<Elevator> elevators = new ArrayList<>();
    private final List<Block> gateBlocks = new ArrayList<>();
    private final List<Cage> cages = new ArrayList<>();
    private BukkitTask task;
    private int ticks;
    private int endTick;
    private boolean done;

    StartSequence(Match match, Opt.StartMode mode, int seconds) {
        this.match = match;
        this.mode = mode;
        this.seconds = Math.max(1, seconds);
    }

    void start() {
        VortexPvP plugin = VortexPvP.get();
        List<Player> players = this.match.alivePlayers();
        ArenaInstance inst = this.match.instance();
        if (this.mode == Opt.StartMode.GATES && inst.arena.gates.isEmpty()) {
            this.mode = Opt.StartMode.FREEZE;
        }

        if ((this.mode == Opt.StartMode.ELEVATOR || this.mode == Opt.StartMode.CAGE) && !this.match.isOneVsOne()) {
            this.mode = Opt.StartMode.FREEZE;
        }

        this.endTick = this.seconds * 20;
        switch (this.mode) {
            case ELEVATOR:
                int depth = inst.arena.elevatorDepth > 0 ? inst.arena.elevatorDepth : Cfg.i(Opt.ELEVATOR_DEPTH);

                for (Player p : players) {
                    Session s = plugin.sessions().get(p);
                    Elevator e = new Elevator(p, s, this.match.spawnOf(p), this.opponentSpawn(p), this.opponent(p), depth, inst.tracker);
                    this.elevators.add(e);
                    e.begin();
                }
                break;
            case GATES:
                this.faceOpponents(players);
                this.placeGates(inst);
                break;
            case CAGE:
                this.faceOpponents(players);

                for (Player p : players) {
                    Cage c = new Cage(p);

                    try {
                        c.build(this.match.spawnOf(p), this.opponentSpawn(p), inst.tracker);
                    } catch (RuntimeException var9) {
                        c.remove();
                        throw var9;
                    }

                    this.cages.add(c);
                    c.countdown(this.seconds);
                    PvPProfile prof = plugin.profiles().get(p);
                    ItemStack[] ed = plugin.kits().editorContents(this.match.kit, prof.layouts.get(this.match.kit.id));
                    p.getInventory().setStorageContents(Arrays.copyOfRange(ed, 0, 36));
                    p.getInventory().setItemInOffHand(ed[40]);
                    p.sendMessage(
                        Text.mm(
                            "<gold>⚔ <gray>Arrange your kit and <white>crouch + right-click the chest</white> to save it. <white>Hit the gate</white> when you're ready."
                        )
                    );
                }
                break;
            default:
                this.faceOpponents(players);

                for (Player p : players) {
                    PlayerReset.freeze(p, plugin.sessions().get(p));
                }
        }

        if (this.mode != Opt.StartMode.CAGE) {
            this.announce(this.seconds);
        }

        this.task = Tasks.timer(1L, 1L, this::tick);
    }

    private Location opponentSpawn(Player p) {
        if (!this.match.isOneVsOne()) {
            return null;
        } else {
            for (Player o : this.match.alivePlayers()) {
                if (!o.equals(p)) {
                    return this.match.spawnOf(o);
                }
            }

            return null;
        }
    }

    private Player opponent(Player p) {
        if (!this.match.isOneVsOne()) {
            return null;
        } else {
            for (Player o : this.match.alivePlayers()) {
                if (!o.equals(p)) {
                    return o;
                }
            }

            return null;
        }
    }

    private void faceOpponents(List<Player> players) {
        for (Player p : players) {
            Location opp = this.opponentSpawn(p);
            Location own = this.match.spawnOf(p);
            if (opp != null && own != null) {
                Location l = own.clone();
                Elevator.face(l, opp);
                Tp.to(p, l);
            }
        }
    }

    private void tick() {
        if (!this.done) {
            this.ticks++;
            double progress = this.ticks / (this.seconds * 20.0);

            for (Elevator e : this.elevators) {
                e.tick(progress);
            }

            if (this.mode == Opt.StartMode.CAGE) {
                if (this.ticks >= this.endTick) {
                    this.finish();
                } else {
                    if ((this.endTick - this.ticks) % 20 == 0) {
                        int left = (this.endTick - this.ticks) / 20;

                        for (Cage c : this.cages) {
                            c.countdown(left);
                        }

                        if (left <= 3) {
                            this.announce(left);
                        }
                    }
                }
            } else {
                if (this.ticks % 20 == 0) {
                    int left = this.seconds - this.ticks / 20;
                    if (left > 0) {
                        this.announce(left);
                    } else {
                        this.finish();
                    }
                }
            }
        }
    }

    Cage cageOf(Player p) {
        for (Cage c : this.cages) {
            if (c.player.equals(p)) {
                return c;
            }
        }

        return null;
    }

    void ready(Player p) {
        Cage mine = this.cageOf(p);
        if (!this.done && mine != null && !mine.ready) {
            String waiting = null;

            for (Cage c : this.cages) {
                if (c != mine && !c.ready) {
                    waiting = c.player.getName();
                }
            }

            mine.markReady(waiting);

            for (Player o : this.match.participantsOnline()) {
                o.sendMessage(Text.mm("<green>✔ <white>" + Text.esc(p.getName()) + "</white> is ready!"));
                Snd.play(o, "block.note_block.pling", 0.6F, o.equals(p) ? 1.6F : 1.2F);
            }

            if (waiting == null) {
                for (Cage cx : this.cages) {
                    cx.markReady(null);
                }

                int left = this.endTick - this.ticks;
                if (left > 60) {
                    this.endTick = this.ticks + 60;

                    for (Cage cx : this.cages) {
                        cx.countdown(3);
                    }

                    this.announce(3);
                }
            }
        }
    }

    private void announce(int left) {
        Component sub = Text.mm(
            "<gray>Round <gold>" + this.match.round() + "</gold> <dark_gray>•</dark_gray> First to <gold>" + this.match.roundsToWin() + "</gold>"
        );

        for (Player p : this.match.participantsOnline()) {
            Fx.countdown(p, left, sub);
        }
    }

    private void finish() {
        if (!this.done) {
            this.done = true;
            Tasks.cancel(this.task);

            for (Elevator e : this.elevators) {
                e.arrive();
            }

            this.openGates();
            if (!this.cages.isEmpty()) {
                for (Cage c : this.cages) {
                    c.open();
                }

                for (Player p : this.match.alivePlayers()) {
                    this.match.giveKit(p);
                }
            }

            VortexPvP plugin = VortexPvP.get();

            for (Player p : this.match.alivePlayers()) {
                PlayerReset.unfreeze(p, plugin.sessions().get(p));
            }

            this.match.onFight();
        }
    }

    void cancel() {
        if (!this.done) {
            this.done = true;
            Tasks.cancel(this.task);

            for (Elevator e : this.elevators) {
                e.cancel();
            }

            for (Cage c : this.cages) {
                c.remove();
            }

            this.cages.clear();

            for (Block b : this.gateBlocks) {
                if (b.getType() == this.match.instance().arena.gateMaterial) {
                    b.setType(Material.AIR, false);
                }
            }

            this.gateBlocks.clear();
            VortexPvP plugin = VortexPvP.get();

            for (Player p : this.match.participantsOnline()) {
                PlayerReset.unfreeze(p, plugin.sessions().get(p));
            }
        }
    }

    private void placeGates(ArenaInstance inst) {
        World w = inst.world();
        Material mat = inst.arena.gateMaterial;

        for (Cuboid g : inst.arena.gates) {
            Cuboid c = g.shift(inst.dx, 0, inst.dz);

            for (int x = c.minX(); x <= c.maxX(); x++) {
                for (int y = c.minY(); y <= c.maxY(); y++) {
                    for (int z = c.minZ(); z <= c.maxZ(); z++) {
                        Block b = w.getBlockAt(x, y, z);
                        if (b.getType().isAir() || b.isReplaceable()) {
                            inst.tracker.record(b);
                            b.setType(mat, true);
                            this.gateBlocks.add(b);
                        }
                    }
                }
            }
        }

        if (!this.gateBlocks.isEmpty()) {
            Snd.at(this.gateBlocks.get(0).getLocation(), "block.piston.extend", 1.0F, 0.8F);
        }
    }

    private void openGates() {
        if (!this.gateBlocks.isEmpty()) {
            Material mat = this.match.instance().arena.gateMaterial;
            TreeMap<Integer, List<Block>> layers = new TreeMap<>();

            for (Block b : this.gateBlocks) {
                layers.computeIfAbsent(-b.getY(), k -> new ArrayList<>()).add(b);
            }

            this.gateBlocks.clear();
            int delay = 0;

            for (List<Block> layer : layers.values()) {
                Tasks.later(delay, () -> {
                    for (Block bx : layer) {
                        if (bx.getType() == mat) {
                            Location c = bx.getLocation().add(0.5, 0.5, 0.5);
                            bx.setType(Material.AIR, false);
                            bx.getWorld().spawnParticle(Particle.BLOCK, c, 8, 0.25, 0.25, 0.25, 0.0, mat.createBlockData());
                        }
                    }

                    if (!layer.isEmpty()) {
                        Snd.at(layer.get(0).getLocation(), "block.fence_gate.open", 1.0F, 0.9F);
                    }
                });
                delay += 2;
            }
        }
    }
}
