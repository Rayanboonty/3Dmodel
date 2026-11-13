package net.practicepvp.hook;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.session.PlayerState;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

public final class ClientGuard implements Listener, TabExecutor {
    private final PracticePlugin plugin;
    private final Map<UUID, ClientGuard.Clicks> clicks = new HashMap<>();
    private final Map<UUID, Set<String>> channels = new HashMap<>();

    public ClientGuard(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    private static List<String> blocked() {
        List<String> out = new ArrayList<>();

        for (String s : Cfg.s(Opt.BLOCKED_CLIENTS).split(",")) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) {
                out.add(t);
            }
        }

        return out;
    }

    private static String match(String value) {
        if (value == null) {
            return null;
        } else {
            String v = value.toLowerCase(Locale.ROOT);

            for (String b : blocked()) {
                if (v.contains(b)) {
                    return b;
                }
            }

            return null;
        }
    }

    private void alert(String mini) {
        Component c = Text.mm("<dark_red>[<red>AC</red>]</dark_red> " + mini);

        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.hasPermission("practice.staff")) {
                o.sendMessage(c);
            }
        }

        this.plugin.getLogger().info("[AC] " + Text.plain(Text.mm(mini)));
    }

    private void flagClient(Player p, String what, String word) {
        if (!p.hasPermission("practice.admin")) {
            this.alert(
                "<white>"
                    + Text.esc(p.getName())
                    + "</white> <gray>uses a blocked client/mod: <red>"
                    + Text.esc(what)
                    + "</red> <dark_gray>("
                    + Text.esc(word)
                    + ")"
            );
            if (Cfg.e(Opt.CLIENT_ACTION, Opt.ClientAction.class) == Opt.ClientAction.KICK) {
                p.kick(Text.mm("<red><b>Blocked client or mod detected</b>\n\n<gray>" + Text.esc(what) + "\n<gray>Remove it to play on this server."));
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Tasks.later(60L, () -> {
            if (p.isOnline() && Cfg.bool(Opt.CLIENT_CHECK)) {
                String brand = p.getClientBrandName();
                String hit = match(brand);
                if (hit != null) {
                    this.flagClient(p, "client brand '" + brand + "'", hit);
                }
            }
        });
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onChannel(PlayerRegisterChannelEvent e) {
        Player p = e.getPlayer();
        this.channels.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashSet<>()).add(e.getChannel());
        if (Cfg.bool(Opt.CLIENT_CHECK)) {
            String hit = match(e.getChannel());
            if (hit != null) {
                Tasks.later(1L, () -> {
                    if (p.isOnline()) {
                        this.flagClient(p, "mod channel '" + e.getChannel() + "'", hit);
                    }
                });
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onQuit(PlayerQuitEvent e) {
        this.clicks.remove(e.getPlayer().getUniqueId());
        this.channels.remove(e.getPlayer().getUniqueId());
    }

    private boolean fighting(Player p) {
        PlayerState st = this.plugin.sessions().state(p);
        return st.fighting();
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() == PlayerAnimationType.ARM_SWING && Cfg.bool(Opt.CLICK_CHECK)) {
            Player p = e.getPlayer();
            if (this.fighting(p)) {
                Block target = p.getTargetBlockExact(5);
                if (target == null || target.getType().isAir()) {
                    long now = System.currentTimeMillis();
                    ClientGuard.Clicks c = this.clicks.computeIfAbsent(p.getUniqueId(), k -> new ClientGuard.Clicks());
                    c.times.addLast(now);

                    while (c.times.size() > 60) {
                        c.times.removeFirst();
                    }

                    this.analyse(p, c, now);
                }
            }
        }
    }

    private void analyse(Player p, ClientGuard.Clicks c, long now) {
        int inSecond = 0;

        for (long t : c.times) {
            if (now - t <= 1000L) {
                inSecond++;
            }
        }

        c.lastCps = inSecond;
        if (now - c.lastSecond >= 1000L) {
            c.lastSecond = now;
            c.highSeconds = inSecond >= Cfg.i(Opt.MAX_CPS) ? c.highSeconds + 1 : 0;
        }

        String finding = null;
        if (c.times.size() >= 40) {
            List<Long> list = new ArrayList<>(c.times);
            List<Long> gaps = new ArrayList<>();

            for (int i = list.size() - 40; i < list.size() - 1; i++) {
                long g = list.get(i + 1) - list.get(i);
                if (g < 400L) {
                    gaps.add(g);
                }
            }

            if (gaps.size() >= 30) {
                double mean = gaps.stream().mapToLong(Long::longValue).average().orElse(0.0);
                double var = 0.0;

                for (long g : gaps) {
                    var += (g - mean) * (g - mean);
                }

                double sd = Math.sqrt(var / gaps.size());
                c.lastJitter = sd;
                if (mean < 105.0 && sd < 7.0) {
                    finding = "robot-perfect clicking ("
                        + String.format(Locale.US, "%.1f", 1000.0 / mean)
                        + " CPS, ±"
                        + String.format(Locale.US, "%.1f", sd)
                        + " ms) - autoclicker/macro?";
                }
            }
        }

        if (finding == null && c.highSeconds >= 3) {
            finding = (int)c.lastCps + " CPS for " + c.highSeconds + " seconds";
        }

        if (finding != null && now - c.lastAlert > 30000L) {
            c.lastAlert = now;
            this.alert(
                "<white><click:run_command:'/spectate "
                    + p.getName()
                    + "'><hover:show_text:'<gray>Click to spectate'>"
                    + Text.esc(p.getName())
                    + "</hover></click></white> <gray>"
                    + Text.esc(finding)
            );
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("practice.staff")) {
            sender.sendMessage(Text.mm("<red>You don't have permission."));
            return true;
        } else if (args.length == 0) {
            sender.sendMessage(Text.mm("<red>Usage: /clientinfo <player>"));
            return true;
        } else {
            Player p = Bukkit.getPlayer(args[0]);
            if (p == null) {
                sender.sendMessage(Text.mm("<red>That player is not online."));
                return true;
            } else {
                ClientGuard.Clicks c = this.clicks.get(p.getUniqueId());
                Set<String> ch = this.channels.getOrDefault(p.getUniqueId(), Set.of());
                sender.sendMessage(Text.mm("<gold><b>" + Text.esc(p.getName()) + "</b> <gray>client info"));
                sender.sendMessage(
                    Text.mm(
                        "<gray>Brand: <white>"
                            + Text.esc(String.valueOf(p.getClientBrandName()))
                            + " <dark_gray>• <gray>protocol <white>"
                            + p.getProtocolVersion()
                            + " <dark_gray>• <gray>ping <white>"
                            + p.getPing()
                            + "ms"
                    )
                );
                sender.sendMessage(Text.mm("<gray>Mod channels (" + ch.size() + "): <white>" + Text.esc(ch.isEmpty() ? "none" : String.join(", ", ch))));
                sender.sendMessage(
                    Text.mm(
                        "<gray>Clicks: <white>"
                            + (
                                c == null
                                    ? "no fight clicks yet"
                                    : (int)c.lastCps + " CPS <dark_gray>• <gray>timing spread <white>" + String.format(Locale.US, "%.1f", c.lastJitter) + " ms"
                            )
                    )
                );
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (o.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(o.getName());
                }
            }
        }

        return out;
    }

    private static final class Clicks {
        final Deque<Long> times = new ArrayDeque<>();
        int highSeconds;
        long lastSecond;
        long lastAlert;
        double lastCps;
        double lastJitter;
    }
}
