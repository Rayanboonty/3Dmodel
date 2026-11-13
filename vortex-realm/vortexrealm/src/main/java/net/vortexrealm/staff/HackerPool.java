package net.vortexrealm.staff;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.util.Fmt;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.WeatherType;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class HackerPool implements TabExecutor, Listener {
    private final VortexRealm plugin;
    private final File file;
    private final Map<UUID, HackerPool.Entry> pool = new LinkedHashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Set<UUID> styled = new HashSet<>();

    public HackerPool(VortexRealm plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "hacker-pool.yml");
    }

    public void load() {
        this.pool.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection s = y.getConfigurationSection("pool");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                try {
                    UUID u = UUID.fromString(id);
                    this.pool
                        .put(
                            u,
                            new HackerPool.Entry(
                                u,
                                s.getString(id + ".name", "?"),
                                s.getString(id + ".reason", "Cheating"),
                                s.getString(id + ".by", "Staff"),
                                s.getLong(id + ".since"),
                                s.getLong(id + ".until")
                            )
                        );
                } catch (IllegalArgumentException var6) {
                }
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Hacker pool (/hackerpool). until: 0 = permanent, else epoch millis."));

        for (HackerPool.Entry e : this.pool.values()) {
            String b = "pool." + e.uuid() + ".";
            y.set(b + "name", e.name());
            y.set(b + "reason", e.reason());
            y.set(b + "by", e.by());
            y.set(b + "since", e.since());
            y.set(b + "until", e.until());
        }

        try {
            y.save(this.file);
        } catch (IOException var5) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save hacker-pool.yml", (Throwable)var5);
        }
    }

    public boolean pooled(UUID u) {
        HackerPool.Entry e = this.pool.get(u);
        if (e == null) {
            return false;
        } else if (!e.active()) {
            this.pool.remove(u);
            this.save();
            return false;
        } else {
            return true;
        }
    }

    public boolean pooled(Player p) {
        return p != null && this.pooled(p.getUniqueId());
    }

    public boolean samePool(Player a, Player b) {
        return this.pooled(a) == this.pooled(b);
    }

    public List<HackerPool.Entry> all() {
        this.pool.values().removeIf(e -> !e.active());
        return new ArrayList<>(this.pool.values());
    }

    public HackerPool.Entry entry(UUID u) {
        return this.pooled(u) ? this.pool.get(u) : null;
    }

    public void add(OfflinePlayer t, String reason, String by, long millis) {
        long now = System.currentTimeMillis();
        this.pool
            .put(
                t.getUniqueId(),
                new HackerPool.Entry(t.getUniqueId(), t.getName() == null ? "?" : t.getName(), reason, by, now, millis <= 0L ? 0L : now + millis)
            );
        this.save();
        Player online = t.getPlayer();
        if (online != null) {
            this.plugin.queues().leaveAll(online, true);
            online.sendMessage(
                Text.mm(
                    "<dark_red><b>⚠ HACKER POOL</b></dark_red> <gray>You were placed in the hacker pool"
                        + (millis > 0L ? " for <white>" + Fmt.duration(millis / 1000L) + "</white>" : "")
                        + ". <gray>Reason: <white>"
                        + Text.esc(reason)
                )
            );
            online.sendMessage(Text.mm("<gray>You can keep playing - but only against other players in the pool."));
            this.refresh(online);
        }
    }

    public boolean remove(UUID u) {
        HackerPool.Entry e = this.pool.remove(u);
        if (e == null) {
            return false;
        } else {
            this.save();
            Player online = Bukkit.getPlayer(u);
            if (online != null) {
                online.sendMessage(Text.mm("<green>You were taken out of the hacker pool. Play fair!"));
                this.refresh(online);
            }

            return true;
        }
    }

    private void refresh(Player p) {
        try {
            this.plugin.boards().refreshNametag(p);
        } catch (RuntimeException var3) {
        }

        this.look(p);
    }

    public Component badge(Player p) {
        return (Component)(this.pooled(p) ? Text.mm("<dark_red><b>✖ HACKER</b></dark_red> ") : Component.empty());
    }

    public Component sidebarTitle() {
        return Text.mm("<dark_red><b>⚠</b></dark_red> <#FF2A2A><b>HACKER POOL</b></#FF2A2A> <dark_red><b>⚠");
    }

    private void style(Player p) {
        HackerPool.Entry e = this.pool.get(p.getUniqueId());
        if (e != null) {
            String left = e.until() <= 0L
                ? "<red>permanent"
                : "<white>" + Fmt.duration(Math.max(0L, (e.until() - System.currentTimeMillis()) / 1000L)) + " <gray>left";
            Component name = Text.mm("<dark_red><b>⚠ HACKER POOL</b></dark_red> <dark_gray>• <gray>you only play other pooled players <dark_gray>• " + left);
            BossBar bar = this.bars.get(p.getUniqueId());
            float progress = 1.0F;
            if (e.until() > 0L && e.until() > e.since()) {
                progress = (float)Math.max(0.0, Math.min(1.0, (double)(e.until() - System.currentTimeMillis()) / (e.until() - e.since())));
            }

            if (bar == null) {
                bar = BossBar.bossBar(name, progress, Color.RED, Overlay.NOTCHED_10);
                this.bars.put(p.getUniqueId(), bar);
                p.showBossBar(bar);
            } else {
                bar.name(name);
                bar.progress(progress);
            }

            if (this.styled.add(p.getUniqueId()) || p.isPlayerTimeRelative() || p.getPlayerWeather() == null) {
                p.setPlayerTime(18000L, false);
                p.setPlayerWeather(WeatherType.DOWNFALL);
            }
        }
    }

    private void unstyle(Player p) {
        BossBar bar = this.bars.remove(p.getUniqueId());
        if (bar != null) {
            p.hideBossBar(bar);
        }

        if (this.styled.remove(p.getUniqueId())) {
            p.resetPlayerTime();
            p.resetPlayerWeather();
        }
    }

    public void forget(Player p) {
        this.bars.remove(p.getUniqueId());
        this.styled.remove(p.getUniqueId());
    }

    public void look(Player p) {
        if (this.pooled(p)) {
            this.style(p);
        } else {
            this.unstyle(p);
        }

        PlayerState st = this.plugin.sessions().state(p);
        boolean lobby = st == PlayerState.LOBBY || st == PlayerState.QUEUE;
        if (this.pooled(p) && lobby) {
            if (p.getWorldBorder() == null) {
                WorldBorder wb = Bukkit.createWorldBorder();
                wb.setCenter(p.getLocation());
                wb.setSize(5.9E7);
                wb.setWarningDistance(536870911);
                p.setWorldBorder(wb);
            }
        } else if (p.getWorldBorder() != null && !this.pooled(p) && lobby) {
            p.setWorldBorder(null);
        }
    }

    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.pooled(p) || this.bars.containsKey(p.getUniqueId()) || this.styled.contains(p.getUniqueId())) {
                this.look(p);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.forget(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (this.pooled(p)) {
            HackerPool.Entry en = this.pool.get(p.getUniqueId());
            Tasks.later(
                40L,
                () -> {
                    if (p.isOnline()) {
                        p.sendMessage(
                            Text.mm(
                                "<dark_red><b>⚠ HACKER POOL</b></dark_red> <gray>You only play against other pooled players"
                                    + (
                                        en.until() > 0L
                                            ? " for another <white>" + Fmt.duration((en.until() - System.currentTimeMillis()) / 1000L) + "</white>"
                                            : ""
                                    )
                                    + "."
                            )
                        );
                        this.look(p);
                    }
                }
            );
        }
    }

    private static long parseTime(String s) {
        try {
            long n = Long.parseLong(s.substring(0, s.length() - 1));

            return switch (Character.toLowerCase(s.charAt(s.length() - 1))) {
                case 'd' -> n * 86400000L;
                case 'h' -> n * 3600000L;
                case 'm' -> n * 60000L;
                case 'w' -> n * 7L * 86400000L;
                default -> -1L;
            };
        } catch (RuntimeException var3) {
            return -1L;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vortex.staff")) {
            sender.sendMessage(Text.mm("<red>You don't have permission."));
            return true;
        } else if (args.length != 0 && !args[0].equalsIgnoreCase("list")) {
            if (args[0].equalsIgnoreCase("remove") && args.length >= 2) {
                OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                sender.sendMessage(
                    Text.mm(
                        this.remove(t.getUniqueId())
                            ? "<green>" + Text.esc(args[1]) + " is out of the hacker pool."
                            : "<red>" + Text.esc(args[1]) + " isn't in the hacker pool."
                    )
                );
                return true;
            } else {
                OfflinePlayer t = Bukkit.getOfflinePlayer(args[0]);
                if (!t.hasPlayedBefore() && !t.isOnline()) {
                    sender.sendMessage(Text.mm("<red>Unknown player " + Text.esc(args[0])));
                    return true;
                } else {
                    long time = 0L;
                    int reasonFrom = 1;
                    if (args.length >= 2) {
                        if (!args[1].equalsIgnoreCase("perm") && !args[1].equalsIgnoreCase("permanent")) {
                            long parsed = parseTime(args[1]);
                            if (parsed > 0L) {
                                time = parsed;
                                reasonFrom = 2;
                            }
                        } else {
                            reasonFrom = 2;
                        }
                    }

                    String reason = args.length > reasonFrom ? String.join(" ", Arrays.copyOfRange(args, reasonFrom, args.length)) : "Cheating";
                    this.add(t, reason, sender.getName(), time);
                    if (this.plugin.staff() != null) {
                        this.plugin
                            .staff()
                            .staffBroadcast(
                                "<dark_red>✖ <white>"
                                    + Text.esc(String.valueOf(t.getName()))
                                    + " <gray>was put in the hacker pool by <white>"
                                    + Text.esc(sender.getName())
                                    + " <gray>("
                                    + Text.esc(reason)
                                    + ")"
                            );
                    }

                    return true;
                }
            }
        } else {
            List<HackerPool.Entry> list = this.all();
            sender.sendMessage(Text.mm("<dark_red><b>Hacker pool</b> <gray>(" + list.size() + ")"));

            for (HackerPool.Entry e : list) {
                sender.sendMessage(
                    Text.mm(
                        " <dark_gray>» <white>"
                            + Text.esc(e.name())
                            + " <gray>- "
                            + Text.esc(e.reason())
                            + " <dark_gray>("
                            + (e.until() <= 0L ? "permanent" : Fmt.duration((e.until() - System.currentTimeMillis()) / 1000L) + " left")
                            + ", by "
                            + Text.esc(e.by())
                            + ") <red><click:run_command:'/"
                            + label
                            + " remove "
                            + e.name()
                            + "'>[remove]</click>"
                    )
                );
            }

            sender.sendMessage(Text.mm("<gray>/" + label + " <player> [30m|12h|7d|perm] [reason]"));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("vortex.staff")) {
            return out;
        } else {
            if (args.length == 1) {
                out.add("list");
                out.add("remove");
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
                this.all().forEach(e -> out.add(e.name()));
            } else if (args.length == 2) {
                out.addAll(List.of("30m", "12h", "1d", "7d", "30d", "perm"));
            } else if (args.length == 3) {
                out.addAll(List.of("KillAura", "Reach", "Autoclicker", "Macro", "Fly", "Speed", "Velocity", "Cheating"));
            }

            String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
            return out;
        }
    }

    public record Entry(UUID uuid, String name, String reason, String by, long since, long until) {
        public boolean active() {
            return this.until <= 0L || System.currentTimeMillis() < this.until;
        }
    }
}
