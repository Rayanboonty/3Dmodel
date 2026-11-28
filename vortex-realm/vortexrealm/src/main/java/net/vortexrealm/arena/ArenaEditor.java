package net.vortexrealm.arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.ffa.FfaArena;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Session;
import net.vortexrealm.session.Tp;
import net.vortexrealm.util.Cuboid;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ArenaEditor {
    private static final DustOptions EDGE = new DustOptions(Color.fromRGB(255, 190, 40), 1.4F);
    private final VortexRealm plugin;
    private final Map<UUID, String> editors = new HashMap<>();
    private final Map<ArenaInstance, ArenaInstance.State> before = new HashMap<>();

    public ArenaEditor(VortexRealm plugin) {
        this.plugin = plugin;
    }

    public void start(Player p, Arena a) {
        if (a.editing) {
            this.enter(p, a.instances.get(0).center(), a.displayName, "duel:" + a.id);
        } else if (a.bounds == null || a.world() == null || a.instances.isEmpty()) {
            err(p, "This arena has no bounds yet. Set them first (Auto Bounds / Bounds From Wand).");
        } else if (this.canEnter(p)) {
            for (ArenaInstance i : a.instances) {
                if (i.state == ArenaInstance.State.IN_USE || i.state == ArenaInstance.State.RESETTING) {
                    err(p, "A match is running in " + a.displayName + ". Try again when it ends.");
                    return;
                }

                if (i.state == ArenaInstance.State.BUILDING) {
                    err(p, a.displayName + " is still being built. Wait a few seconds.");
                    return;
                }
            }

            a.editing = true;

            for (ArenaInstance i : a.instances) {
                this.before.put(i, i.state);
                i.state = ArenaInstance.State.BUILDING;
                i.tracker.drain();
            }

            this.enter(p, a.instances.get(0).center(), a.displayName, "duel:" + a.id);
        }
    }

    public void save(CommandSender who, Arena a) {
        if (!a.editing) {
            err(who, a.displayName + " is not in edit mode.");
        } else {
            World w = a.world();
            if (w != null && a.bounds != null) {
                int copies = a.instances.size() - 1;
                msg(
                    who,
                    "<yellow>Saving <white>"
                        + Text.esc(a.displayName)
                        + "<yellow>"
                        + (copies > 0 ? " and updating <white>" + copies + "</white> cop" + (copies == 1 ? "y" : "ies") : "")
                        + "..."
                );

                for (ArenaInstance i : a.instances) {
                    i.tracker.drain();
                }

                Cuboid bounds = a.bounds;
                CompletableFuture<Void> chain = this.plugin.arenas().captureSnapshot(a);

                for (ArenaInstance i : a.instances) {
                    if (i.dx != 0 || i.dz != 0) {
                        chain = chain.<Void>thenComposeAsync(v -> ArenaManager.preload(w, bounds.shift(i.dx, 0, i.dz)), Tasks.MAIN)
                            .thenComposeAsync(v -> this.plugin.jobs().submit(a.snapshot.pasteJob(w, i.dx, i.dz)), Tasks.MAIN);
                    }
                }

                chain.whenComplete((v, e) -> Tasks.ensureSync(() -> {
                    if (e == null) {
                        a.handEdited = true;
                    }

                    this.finish(a);
                    if (e != null) {
                        this.plugin.getLogger().log(Level.SEVERE, "Saving arena edits of " + a.id + " failed", e);
                        err(who, "Saving failed: " + e.getMessage());
                    } else {
                        msg(who, "<green>✔ Saved <white>" + Text.esc(a.displayName) + "<green>. Every copy now has your changes and resets back to them.");
                    }
                }));
            } else {
                err(who, "The arena world is not loaded.");
            }
        }
    }

    public void cancel(CommandSender who, Arena a) {
        if (!a.editing) {
            err(who, a.displayName + " is not in edit mode.");
        } else {
            World w = a.world();
            if (a.snapshot != null && w != null) {
                msg(who, "<yellow>Discarding your changes...");
                a.instances.get(0).tracker.drain();
                ArenaManager.preload(w, a.bounds)
                    .thenComposeAsync(v -> this.plugin.jobs().submit(a.snapshot.pasteJob(w, 0, 0)), Tasks.MAIN)
                    .whenComplete((v, e) -> Tasks.ensureSync(() -> {
                        this.finish(a);
                        msg(who, "<gray>Changes discarded, " + Text.esc(a.displayName) + " is back in rotation.");
                    }));
            } else {
                this.finish(a);
                msg(who, "<gray>Edit mode closed (there was no saved version to restore).");
            }
        }
    }

    private void finish(Arena a) {
        for (ArenaInstance i : a.instances) {
            ArenaInstance.State st = this.before.remove(i);
            i.state = st != null && st != ArenaInstance.State.BUILDING ? st : ArenaInstance.State.FREE;
            i.tracker.drain();
        }

        a.editing = false;
        this.editors.values().removeIf(k -> k.equals("duel:" + a.id));
        this.plugin.arenas().save();
    }

    public void start(Player p, FfaArena f) {
        if (f.editing) {
            this.enter(p, ffaSpot(f), f.displayName, "ffa:" + f.id);
        } else if (f.bounds != null && f.world() != null) {
            if (f.building) {
                err(p, f.displayName + " is being built or regenerated. Try again in a few seconds.");
            } else if (this.canEnter(p)) {
                for (UUID u : new ArrayList<>(f.players)) {
                    Player o = Bukkit.getPlayer(u);
                    if (o != null) {
                        this.plugin.ffa().leave(o, true);
                        msg(o, "<yellow>This FFA arena is being edited by staff, you were sent to the lobby.");
                    }
                }

                f.editing = true;
                f.building = true;
                f.tracker.drain();
                this.enter(p, ffaSpot(f), f.displayName, "ffa:" + f.id);
            }
        } else {
            err(p, "This FFA arena has no bounds yet.");
        }
    }

    public void save(CommandSender who, FfaArena f) {
        if (!f.editing) {
            err(who, f.displayName + " is not in edit mode.");
        } else {
            msg(who, "<yellow>Saving <white>" + Text.esc(f.displayName) + "<yellow>...");
            f.tracker.drain();
            this.plugin.ffa().captureSnapshot(f).whenComplete((v, e) -> Tasks.ensureSync(() -> {
                if (e == null) {
                    f.handEdited = true;
                }

                this.finish(f);
                if (e != null) {
                    err(who, "Saving failed: " + e.getMessage());
                } else {
                    msg(who, "<green>✔ Saved <white>" + Text.esc(f.displayName) + "<green>. It regenerates back to your version from now on.");
                }
            }));
        }
    }

    public void cancel(CommandSender who, FfaArena f) {
        if (!f.editing) {
            err(who, f.displayName + " is not in edit mode.");
        } else {
            World w = f.world();
            if (f.snapshot != null && w != null) {
                msg(who, "<yellow>Discarding your changes...");
                f.tracker.drain();
                ArenaManager.preload(w, f.bounds)
                    .thenComposeAsync(v -> this.plugin.jobs().submit(f.snapshot.pasteJob(w, 0, 0)), Tasks.MAIN)
                    .whenComplete((v, e) -> Tasks.ensureSync(() -> {
                        this.finish(f);
                        msg(who, "<gray>Changes discarded, " + Text.esc(f.displayName) + " is open again.");
                    }));
            } else {
                this.finish(f);
                msg(who, "<gray>Edit mode closed (there was no saved version to restore).");
            }
        }
    }

    private void finish(FfaArena f) {
        f.tracker.drain();
        f.editing = false;
        f.building = false;
        f.lastFullRegen = System.currentTimeMillis();
        this.editors.values().removeIf(k -> k.equals("ffa:" + f.id));
        this.plugin.ffa().save();
    }

    private static Location ffaSpot(FfaArena f) {
        if (!f.spawns.isEmpty()) {
            return f.spawns.get(0).at(f.world());
        } else {
            Cuboid b = f.bounds;
            return new Location(f.world(), (b.minX() + b.maxX()) / 2.0 + 0.5, b.maxY() - 2, (b.minZ() + b.maxZ()) / 2.0 + 0.5);
        }
    }

    public boolean isEditing(Player p) {
        return this.editors.containsKey(p.getUniqueId());
    }

    public Object editingOf(Player p) {
        String key = this.editors.get(p.getUniqueId());
        if (key == null) {
            return null;
        } else {
            String id = key.substring(key.indexOf(58) + 1);
            Object o = key.startsWith("duel:") ? this.plugin.arenas().get(id) : this.plugin.ffa().get(id);
            boolean live = o instanceof Arena a ? a.editing : o instanceof FfaArena f && f.editing;
            if (!live) {
                this.editors.remove(p.getUniqueId());
                return null;
            } else {
                return o;
            }
        }
    }

    private boolean canEnter(Player p) {
        Session s = this.plugin.sessions().get(p);
        if (s.state == PlayerState.OUTSIDE) {
            err(p, "Go to the practice lobby first (/practice), then start editing.");
            return false;
        } else if (s.state != PlayerState.LOBBY) {
            err(p, "Leave your queue / match / FFA first (/leave).");
            return false;
        } else {
            return true;
        }
    }

    private void enter(Player p, Location at, String name, String key) {
        this.editors.put(p.getUniqueId(), key);
        Session s = this.plugin.sessions().get(p);
        if (s.state == PlayerState.LOBBY) {
            s.buildMode = true;
            p.closeInventory();
            if (at != null) {
                Tp.to(p, at);
            }

            p.setGameMode(GameMode.CREATIVE);
            p.setAllowFlight(true);
            p.setFlying(true);
        }

        p.sendMessage(Text.mm("<gold><b>⚒ EDITING " + Text.esc(name).toUpperCase(Locale.ROOT)));
        p.sendMessage(
            Text.mm(
                "<gray>Build and break freely in <gold>creative<gray>. Only blocks inside the <gold>orange outline<gray> are saved. Nobody can play here until you finish."
            )
        );
        p.sendMessage(
            Text.mm(
                "<green><click:run_command:'/pvpadmin arena save'><hover:show_text:'Save and update every copy'>[✔ SAVE CHANGES]</hover></click></green>  <red><click:run_command:'/pvpadmin arena cancel'><hover:show_text:'Restore the last saved version'>[✖ DISCARD]</hover></click></red>  <dark_gray>(/pvpadmin arena save | cancel)"
            )
        );
    }

    public void tick() {
        if (!this.editors.isEmpty()) {
            for (UUID u : new ArrayList<>(this.editors.keySet())) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    Object o = this.editingOf(p);
                    Cuboid b = o instanceof Arena a ? a.bounds : (o instanceof FfaArena f ? f.bounds : null);
                    World w = o instanceof Arena ax ? ax.world() : (o instanceof FfaArena fx ? fx.world() : null);
                    if (b != null && w != null && p.getWorld().equals(w)) {
                        outline(p, b);
                        if (System.currentTimeMillis() / 500L % 6L == 0L) {
                            p.sendActionBar(Text.mm("<gold>⚒ Editing <gray>· <green>/pvpadmin arena save <gray>· <red>/pvpadmin arena cancel"));
                        }
                    }
                }
            }
        }
    }

    private static void outline(Player p, Cuboid b) {
        Location me = p.getLocation();
        double x0 = b.minX();
        double y0 = b.minY();
        double z0 = b.minZ();
        double x1 = b.maxX() + 1;
        double y1 = b.maxY() + 1;
        double z1 = b.maxZ() + 1;
        List<double[]> edges = List.of(
            new double[]{x0, y0, z0, x1, y0, z0},
            new double[]{x0, y0, z1, x1, y0, z1},
            new double[]{x0, y1, z0, x1, y1, z0},
            new double[]{x0, y1, z1, x1, y1, z1},
            new double[]{x0, y0, z0, x0, y0, z1},
            new double[]{x1, y0, z0, x1, y0, z1},
            new double[]{x0, y1, z0, x0, y1, z1},
            new double[]{x1, y1, z0, x1, y1, z1},
            new double[]{x0, y0, z0, x0, y1, z0},
            new double[]{x1, y0, z0, x1, y1, z0},
            new double[]{x0, y0, z1, x0, y1, z1},
            new double[]{x1, y0, z1, x1, y1, z1}
        );
        double max2 = 2304.0;

        for (double[] e : edges) {
            double len = Math.max(Math.abs(e[3] - e[0]), Math.max(Math.abs(e[4] - e[1]), Math.abs(e[5] - e[2])));
            int steps = (int)Math.ceil(len);

            for (int i = 0; i <= steps; i++) {
                double t = steps == 0 ? 0.0 : (double)i / steps;
                double x = e[0] + (e[3] - e[0]) * t;
                double y = e[1] + (e[4] - e[1]) * t;
                double z = e[2] + (e[5] - e[2]) * t;
                double dx = x - me.getX();
                double dy = y - me.getY();
                double dz = z - me.getZ();
                if (dx * dx + dy * dy + dz * dz <= max2) {
                    p.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, EDGE);
                }
            }
        }
    }

    private static void msg(CommandSender s, String mini) {
        if (s != null) {
            s.sendMessage(Text.mm(mini));
        }
    }

    private static void err(CommandSender s, String text) {
        msg(s, "<red>" + Text.esc(text));
    }
}
