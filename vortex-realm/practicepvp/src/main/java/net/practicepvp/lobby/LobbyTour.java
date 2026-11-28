package net.practicepvp.lobby;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import net.practicepvp.PracticePlugin;
import net.practicepvp.npc.Npc;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

/**
 * 1.0.17: the lobby tour. On a first join (and with /tour) the camera glides from one place of the lobby to the next -
 * the duels, FFA, training, leaderboards, events NPCs - with a caption for each, and ends back at spawn. Sneaking skips
 * it. Places come from the lobby's NPCs; with none, the camera circles the spawn.
 */
public final class LobbyTour implements Listener, CommandExecutor {
    private static final int GLIDE = 50;
    private static final int HOLD = 40;
    private final PracticePlugin plugin;
    private final Map<UUID, Run> runs = new HashMap<>();

    private static final class Run {
        final ItemDisplay cam;
        final Location back;
        final GameMode mode;
        final Runnable after;
        boolean done;

        Run(ItemDisplay cam, Location back, GameMode mode, Runnable after) {
            this.cam = cam;
            this.back = back;
            this.mode = mode;
            this.after = after;
        }
    }

    private record Stop(Location look, String title, String caption) {
    }

    public LobbyTour(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            if (this.plugin.sessions().state(p) != net.practicepvp.session.PlayerState.LOBBY) {
                p.sendMessage(Text.mm("<red>The tour is only in the lobby."));
            } else {
                this.play(p, null);
            }
        }

        return true;
    }

    private static String[] caption(String value) {
        return switch (value == null ? "" : value.toLowerCase(Locale.ROOT)) {
            case "duels", "ranked" -> new String[]{"<#E3172B><b>DUELS", "Ranked and unranked 1v1s in every kit"};
            case "ffa" -> new String[]{"<#E3172B><b>FREE FOR ALL", "Jump in, kill streaks, heal on kill"};
            case "leaderboard", "stats" -> new String[]{"<white><b>LEADERBOARDS", "The best players of every kit"};
            case "spectate" -> new String[]{"<white><b>SPECTATE", "Watch the live matches"};
            case "kiteditor" -> new String[]{"<white><b>KIT EDITOR", "Put your items where you want them"};
            case "party" -> new String[]{"<#E3172B><b>PARTIES", "Team up with friends"};
            case "events" -> new String[]{"<#E3172B><b>TOURNAMENTS", "Brackets, prizes and the daily cup"};
            case "train", "bots", "bot" -> new String[]{"<white><b>TRAINING", "Bots, drills and the Stun Slam Lab"};
            case "settings" -> new String[]{"<white><b>SETTINGS", "Make the server yours"};
            default -> null;
        };
    }

    private List<Stop> stops(Location spawn) {
        List<Stop> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        if (this.plugin.npcs() != null) {
            for (Npc n : this.plugin.npcs().all()) {
                if (n.loc == null || n.loc.getWorld() != spawn.getWorld() || n.loc.distanceSquared(spawn) > 160 * 160) {
                    continue;
                }

                String[] c = caption(n.value);
                if (c != null && !seen.contains(c[0]) && out.size() < 7) {
                    seen.add(c[0]);
                    out.add(new Stop(n.loc.clone().add(0.0, 1.2, 0.0), c[0], c[1]));
                }
            }
        }

        if (out.isEmpty()) {
            String[][] gen = {
                {"<b><white>CMC</white><#E3172B>PVP</#E3172B>", "Welcome to the lobby"}, {"<#E3172B><b>QUEUE UP", "Type /menu to see everything"},
                {"<white><b>CLIMB", "Win to climb from LT5 to HT1"}
            };
            for (int i = 0; i < gen.length; i++) {
                double a = i * Math.PI * 2 / gen.length;
                out.add(new Stop(spawn.clone().add(Math.cos(a) * 14, 2.0, Math.sin(a) * 14), gen[i][0], gen[i][1]));
            }
        }

        return out;
    }

    private static Location eye(Location look, Location spawn) {
        Vector away = look.toVector().subtract(spawn.toVector()).setY(0);
        if (away.lengthSquared() < 1.0) {
            away = new Vector(0, 0, 1);
        }

        // stand between the place and the spawn, a bit above, looking at it
        Location at = look.clone().subtract(away.normalize().multiply(7.0)).add(0.0, 3.5, 0.0);
        at.setDirection(look.toVector().subtract(at.toVector()));
        return at;
    }

    public void play(Player p, Runnable after) {
        if (this.runs.containsKey(p.getUniqueId())) {
            return;
        }

        Location spawn = this.plugin.lobby().lobby();
        if (spawn == null || spawn.getWorld() != p.getWorld()) {
            spawn = p.getLocation();
        }

        List<Stop> stops = this.stops(spawn);
        Location start = spawn.clone().add(0.0, 22.0, 0.0);
        start.setPitch(60.0F);
        ItemDisplay cam = start.getWorld().spawn(start, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setTeleportDuration(GLIDE);
        });
        Run run = new Run(cam, p.getLocation(), p.getGameMode(), after);
        this.runs.put(p.getUniqueId(), run);
        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(start);
        p.sendActionBar(Text.mm("<gray>Sneak to skip the tour"));
        Location sp = spawn;
        Tasks.later(2L, () -> {
            if (!run.done && p.isOnline()) {
                p.setSpectatorTarget(cam);
            }
        });
        long t = 6L;
        for (Stop s : stops) {
            Location to = eye(s.look(), sp);
            Tasks.later(t, () -> {
                // 1.0.18: a queue pop or a duel ends the tour at once
                if (!run.done && p.isOnline() && this.plugin.sessions().state(p) != net.practicepvp.session.PlayerState.LOBBY) {
                    this.finish(p, false);
                    return;
                }

                if (!run.done && cam.isValid()) {
                    cam.teleport(to);
                }
            });
            Tasks.later(t + GLIDE - 10, () -> {
                if (!run.done && p.isOnline()) {
                    p.showTitle(Title.title(Text.mm(s.title()), Text.mm("<gray>" + s.caption()),
                        Title.Times.times(java.time.Duration.ofMillis(250), java.time.Duration.ofMillis(1800), java.time.Duration.ofMillis(400))));
                    Snd.play(p, "block.note_block.chime", 0.6F, 1.4F);
                }
            });
            t += GLIDE + HOLD;
        }

        Tasks.later(t, () -> this.finish(p, true));
    }

    private void finish(Player p, boolean natural) {
        Run run = this.runs.remove(p.getUniqueId());
        if (run == null || run.done) {
            return;
        }

        run.done = true;
        if (p.isOnline()) {
            if (p.getSpectatorTarget() != null) {
                p.setSpectatorTarget(null);
            }

            p.setGameMode(run.mode == GameMode.SPECTATOR ? GameMode.ADVENTURE : run.mode);
            p.teleport(run.back);
            p.clearTitle();
            if (natural) {
                p.showTitle(Title.title(Text.mm("<b><white>CMC</white><#E3172B>PVP</#E3172B>"), Text.mm("<gray>Type <white>/menu</white> to play"),
                    Title.Times.times(java.time.Duration.ofMillis(250), java.time.Duration.ofMillis(2000), java.time.Duration.ofMillis(500))));
            }
        }

        run.cam.remove();
        if (run.after != null && p.isOnline()) {
            Tasks.later(natural ? 30L : 5L, run.after);
        }
    }

    /** Puts everyone on a tour back (plugin stop / reload). */
    public void shutdown() {
        for (UUID u : new ArrayList<>(this.runs.keySet())) {
            Player p = org.bukkit.Bukkit.getPlayer(u);
            Run run = this.runs.remove(u);
            if (run != null) {
                run.done = true;
                run.cam.remove();
                if (p != null) {
                    p.setSpectatorTarget(null);
                    p.setGameMode(run.mode == GameMode.SPECTATOR ? GameMode.ADVENTURE : run.mode);
                    p.teleport(run.back);
                }
            }
        }
    }

    @EventHandler
    public void onStopSpectating(PlayerStopSpectatingEntityEvent e) {
        Run run = this.runs.get(e.getPlayer().getUniqueId());
        if (run != null && !run.done && e.getSpectatorTarget() == run.cam) {
            // sneaking leaves the camera: skip the tour
            Tasks.later(1L, () -> this.finish(e.getPlayer(), false));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Run run = this.runs.get(e.getPlayer().getUniqueId());
        if (run != null) {
            run.done = true;
            this.runs.remove(e.getPlayer().getUniqueId());
            e.getPlayer().setGameMode(run.mode == GameMode.SPECTATOR ? GameMode.ADVENTURE : run.mode);
            e.getPlayer().teleport(run.back);
            run.cam.remove();
        }
    }
}
