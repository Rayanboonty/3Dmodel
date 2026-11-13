package net.practicepvp.staff;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.gui.menus.SpectateMenu;
import net.practicepvp.match.Match;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

public final class StaffManager implements Listener, CommandExecutor, TabCompleter {
    private final PracticePlugin plugin;
    private final File file;
    private final Map<UUID, StaffManager.Member> staff = new LinkedHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public StaffManager(PracticePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "staff.yml");
    }

    public void load() {
        this.staff.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection sec = y.getConfigurationSection("staff");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    UUID u = UUID.fromString(key);
                    StaffManager.Role r = StaffManager.Role.valueOf(sec.getString(key + ".role", "HELPER").toUpperCase(Locale.ROOT));
                    this.staff.put(u, new StaffManager.Member(u, sec.getString(key + ".name", "?"), r));
                } catch (IllegalArgumentException var7) {
                    this.plugin.getLogger().warning("staff.yml: bad entry " + key);
                }
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            this.apply(p);
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();

        for (StaffManager.Member m : this.staff.values()) {
            y.set("staff." + m.uuid() + ".name", m.name());
            y.set("staff." + m.uuid() + ".role", m.role().name());
        }

        try {
            y.save(this.file);
        } catch (IOException var4) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save staff.yml", (Throwable)var4);
        }
    }

    public List<StaffManager.Member> all() {
        return new ArrayList<>(this.staff.values());
    }

    public StaffManager.Role roleOf(Player p) {
        StaffManager.Member m = this.staff.get(p.getUniqueId());
        return m == null ? null : m.role();
    }

    public boolean isStaff(Player p) {
        return this.staff.containsKey(p.getUniqueId()) || p.hasPermission("practice.staff");
    }

    public void set(OfflinePlayer target, StaffManager.Role role) {
        String name = target.getName() == null ? target.getUniqueId().toString().substring(0, 8) : target.getName();
        this.staff.put(target.getUniqueId(), new StaffManager.Member(target.getUniqueId(), name, role));
        this.save();
        Player online = target.getPlayer();
        if (online != null) {
            this.apply(online);
            online.sendMessage(
                Text.mm("<gold><b>STAFF</b> <dark_gray>» <gray>You are now PvP " + role.badge() + "<gray>. Use <yellow>/staff</yellow> to see your tools.")
            );
            Snd.play(online, "ui.toast.challenge_complete", 0.8F, 1.2F);
        }

        this.staffBroadcast("<gray>" + Text.esc(name) + " is now " + role.badge());
    }

    public boolean remove(UUID uuid) {
        StaffManager.Member m = this.staff.remove(uuid);
        if (m == null) {
            return false;
        } else {
            this.save();
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                this.apply(online);
                online.sendMessage(Text.mm("<gold><b>STAFF</b> <dark_gray>» <gray>You are no longer PvP staff."));
            }

            this.staffBroadcast("<gray>" + Text.esc(m.name()) + " was removed from the staff");
            return true;
        }
    }

    private void apply(Player p) {
        PermissionAttachment old = this.attachments.remove(p.getUniqueId());
        if (old != null) {
            try {
                p.removeAttachment(old);
            } catch (IllegalArgumentException var10) {
            }
        }

        StaffManager.Member m = this.staff.get(p.getUniqueId());
        if (m != null) {
            PermissionAttachment a = p.addAttachment(this.plugin);

            for (String perm : m.role().perms) {
                a.setPermission(perm, true);
            }

            this.attachments.put(p.getUniqueId(), a);
            p.recalculatePermissions();
            p.updateCommands();
        }

        if (this.plugin.boards() != null) {
            try {
                this.plugin.boards().refreshNametag(p);
            } catch (RuntimeException var9) {
            }
        }
    }

    public Component badge(Player p) {
        StaffManager.Role r = this.roleOf(p);
        return (Component)(r == null ? Component.empty() : Text.mm(r.badge() + " "));
    }

    public void staffBroadcast(String mm) {
        Component msg = Text.mm("<gold><b>STAFF</b> <dark_gray>» ").append(Text.mm(mm));

        for (Player o : Bukkit.getOnlinePlayers()) {
            if (this.isStaff(o)) {
                o.sendMessage(msg);
            }
        }

        Bukkit.getConsoleSender().sendMessage(msg);
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        StaffManager.Member m = this.staff.get(p.getUniqueId());
        if (m != null) {
            if (!m.name().equals(p.getName())) {
                this.staff.put(p.getUniqueId(), new StaffManager.Member(p.getUniqueId(), p.getName(), m.role()));
                this.save();
            }

            this.apply(p);
            Tasks.later(20L, () -> {
                if (p.isOnline()) {
                    this.staffBroadcast(m.role().badge() + " <white>" + Text.esc(p.getName()) + " <green>joined");
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PermissionAttachment a = this.attachments.remove(e.getPlayer().getUniqueId());
        if (a != null) {
            try {
                e.getPlayer().removeAttachment(a);
            } catch (IllegalArgumentException var4) {
            }
        }

        StaffManager.Role r = this.roleOf(e.getPlayer());
        if (r != null) {
            this.staffBroadcast(r.badge() + " <white>" + Text.esc(e.getPlayer().getName()) + " <red>left");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p && this.isStaff(p))) {
            sender.sendMessage(Text.mm("<red>Only PvP staff can use this."));
            return true;
        } else if (!label.equalsIgnoreCase("sc") && !label.equalsIgnoreCase("staffchat")) {
            String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
            boolean mod = p.hasPermission("practice.staff.mod");
            switch (sub) {
                case "chat":
                case "c": {
                        if (args.length < 2) {
                            p.sendMessage(Text.mm("<red>Usage: /staff chat \\<message>"));
                        } else {
                            this.chat(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                        }
                        break;
                }
                case "list": {
                        p.sendMessage(Text.mm("<gold><b>PvP Staff</b> <gray>(" + this.staff.size() + ")"));

                        for (StaffManager.Member m : this.staff.values()) {
                            boolean on = Bukkit.getPlayer(m.uuid()) != null;
                            p.sendMessage(Text.mm(" " + (on ? "<green>●" : "<dark_gray>●") + " " + m.role().badge() + " <white>" + Text.esc(m.name())));
                        }
                        break;
                }
                case "spectate":
                case "spec": {
                        Match m = this.matchOf(p, args);
                        if (m == null) {
                            return true;
                        }

                        SpectateMenu.spectate(this.plugin, p, m);
                        break;
                }
                case "end": {
                        if (!mod) {
                            p.sendMessage(Text.mm("<red>Mods only."));
                            return true;
                        }

                        Match m = this.matchOf(p, args);
                        if (m != null) {
                            m.end(null, Match.EndReason.DRAW);
                            this.staffBroadcast("<white>" + Text.esc(p.getName()) + " <gray>ended <white>" + Text.esc(m.title()));
                        }
                        break;
                }
                case "lobby": {
                        if (!mod) {
                            p.sendMessage(Text.mm("<red>Mods only."));
                            return true;
                        }

                        Player t = args.length < 2 ? null : Bukkit.getPlayerExact(args[1]);
                        if (t == null) {
                            p.sendMessage(Text.mm("<red>Usage: /staff lobby \\<online player>"));
                            return true;
                        }

                        this.plugin.forceLeave(t);
                        this.staffBroadcast("<white>" + Text.esc(p.getName()) + " <gray>sent <white>" + Text.esc(t.getName()) + " <gray>to the lobby");
                        break;
                }
                default: {
                        StaffManager.Role r = this.roleOf(p);
                        p.sendMessage(Text.mm("<gold><b>Staff Tools</b> " + (r == null ? "" : r.badge())));
                        p.sendMessage(Text.mm(" <yellow>/sc \\<msg> <gray>- staff chat"));
                        p.sendMessage(Text.mm(" <yellow>/staff list <gray>- online staff"));
                        p.sendMessage(Text.mm(" <yellow>/staff spectate \\<player> <gray>- watch any match"));
                        if (mod) {
                            p.sendMessage(Text.mm(" <yellow>/staff end \\<player> <gray>- end their match (draw)"));
                            p.sendMessage(Text.mm(" <yellow>/staff lobby \\<player> <gray>- send to lobby"));
                        }
                }
            }

            return true;
        } else if (args.length == 0) {
            p.sendMessage(Text.mm("<red>Usage: /sc \\<message>"));
            return true;
        } else {
            this.chat(p, String.join(" ", args));
            return true;
        }
    }

    private Match matchOf(Player p, String[] args) {
        Player t = args.length < 2 ? null : Bukkit.getPlayerExact(args[1]);
        if (t == null) {
            p.sendMessage(Text.mm("<red>Give an online player in a match."));
            return null;
        } else {
            Match m = this.plugin.sessions().get(t).match;
            if (m == null) {
                p.sendMessage(Text.mm("<red>" + Text.esc(t.getName()) + " is not in a match."));
            }

            return m;
        }
    }

    private void chat(Player p, String msg) {
        StaffManager.Role r = this.roleOf(p);
        this.staffBroadcast((r == null ? "<gray>" : r.badge() + " ") + "<white>" + Text.esc(p.getName()) + "<dark_gray>: <yellow>" + Text.esc(msg));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("sc") && !label.equalsIgnoreCase("staffchat")) {
            if (args.length == 1) {
                return filter(List.of("chat", "list", "spectate", "end", "lobby"), args[0]);
            } else if (args.length != 2) {
                return List.of();
            } else {
                List<String> names = new ArrayList<>();

                for (Player o : Bukkit.getOnlinePlayers()) {
                    names.add(o.getName());
                }

                return filter(names, args[1]);
            }
        } else {
            return List.of();
        }
    }

    private static List<String> filter(List<String> in, String prefix) {
        List<String> out = new ArrayList<>();

        for (String s : in) {
            if (s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                out.add(s);
            }
        }

        return out;
    }

    public record Member(UUID uuid, String name, StaffManager.Role role) {
    }

    public static enum Role {
        HELPER("<aqua>", "Helper", "practice.staff"),
        MOD("<blue>", "Mod", "practice.staff", "practice.staff.mod", "practice.lobbyfly", "practice.staff.alts"),
        ADMIN("<red>", "Admin", "practice.staff", "practice.staff.mod", "practice.lobbyfly", "practice.admin", "practice.staff.alts");

        public final String color;
        public final String label;
        final String[] perms;

        private Role(String color, String label, String... perms) {
            this.color = color;
            this.label = label;
            this.perms = perms;
        }

        public String badge() {
            return this.color + "<b>" + this.label.toUpperCase(Locale.ROOT) + "</b>";
        }

        public StaffManager.Role next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }
}
