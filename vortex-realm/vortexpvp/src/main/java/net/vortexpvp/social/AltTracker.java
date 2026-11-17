package net.vortexpvp.social;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Alt finder for staff. Every join stores a salted hash of the player's IP (never the IP itself); /alts &lt;player&gt;
 * lists every account that shared one of those hashes. Staff are warned when an account that shares an IP with a
 * banned player joins.
 */
public final class AltTracker implements Listener, TabExecutor {
    private final VortexPvP plugin;
    private final String salt;

    public AltTracker(VortexPvP plugin) {
        this.plugin = plugin;
        File f = new File(plugin.getDataFolder(), "alts-salt.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String s = y.getString("salt");
        if (s == null || s.length() < 16) {
            byte[] b = new byte[24];
            new SecureRandom().nextBytes(b);
            s = HexFormat.of().formatHex(b);
            y.options().setHeader(List.of("Secret used to hash IP addresses for /alts. Keep it private; changing it forgets all links."));
            y.set("salt", s);

            try {
                y.save(f);
            } catch (IOException var6) {
                plugin.getLogger().warning("Could not save alts-salt.yml: " + var6.getMessage());
            }
        }

        this.salt = s;
    }

    String hash(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((this.salt + "|" + ip).getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (NoSuchAlgorithmException var3) {
            return Integer.toHexString((this.salt + ip).hashCode());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.getAddress() == null || this.plugin.db() == null) {
            return;
        }

        String h = this.hash(p.getAddress().getAddress().getHostAddress());
        UUID u = p.getUniqueId();
        String name = p.getName();
        long now = System.currentTimeMillis();
        this.plugin.db().supply(c -> {
            try (PreparedStatement up = c.prepareStatement("UPDATE vp_ips SET name=?, last_seen=? WHERE uuid=? AND ip_hash=?")) {
                up.setString(1, name);
                up.setLong(2, now);
                up.setString(3, u.toString());
                up.setString(4, h);
                if (up.executeUpdate() == 0) {
                    try (PreparedStatement ins = c.prepareStatement("INSERT INTO vp_ips (uuid, name, ip_hash, first_seen, last_seen) VALUES (?,?,?,?,?)")) {
                        ins.setString(1, u.toString());
                        ins.setString(2, name);
                        ins.setString(3, h);
                        ins.setLong(4, now);
                        ins.setLong(5, now);
                        ins.executeUpdate();
                    }
                }
            }

            return this.linked(c, u);
        }).thenAccept(alts -> Tasks.ensureSync(() -> {
            List<String> banned = new ArrayList<>();

            for (AltTracker.Alt a : alts) {
                if (Bukkit.getOfflinePlayer(a.uuid()).isBanned()) {
                    banned.add(a.name());
                }
            }

            if (!banned.isEmpty() && p.isOnline()) {
                String msg = "<red>⚠ <white>" + Text.esc(name) + "</white> shares an IP with banned account" + (banned.size() == 1 ? "" : "s")
                    + " <white>" + Text.esc(String.join(", ", banned)) + "</white>. <click:run_command:'/alts " + name + "'><yellow>[Alts]</yellow></click>";

                for (Player s : Bukkit.getOnlinePlayers()) {
                    if (s.hasPermission("vortexpvp.staff.alts")) {
                        s.sendMessage(Text.mm(msg));
                    }
                }

                this.plugin.getLogger().info(Text.plain(Text.mm(msg)));
            }
        })).exceptionally(err -> null);
    }

    public record Alt(UUID uuid, String name, int shared, long lastSeen) {
    }

    /** Every other account that shared an IP hash with {@code u}, most shared first. */
    public static List<AltTracker.Alt> linked(java.sql.Connection c, UUID u) throws java.sql.SQLException {
        Map<UUID, AltTracker.Alt> out = new LinkedHashMap<>();

        try (PreparedStatement ps = c.prepareStatement(
            "SELECT o.uuid, o.name, COUNT(DISTINCT o.ip_hash), MAX(o.last_seen) FROM vp_ips m JOIN vp_ips o ON o.ip_hash = m.ip_hash"
                + " WHERE m.uuid = ? AND o.uuid <> ? GROUP BY o.uuid, o.name ORDER BY COUNT(DISTINCT o.ip_hash) DESC, MAX(o.last_seen) DESC"
        )) {
            ps.setString(1, u.toString());
            ps.setString(2, u.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID o = UUID.fromString(rs.getString(1));
                    AltTracker.Alt prev = out.get(o);
                    AltTracker.Alt a = new AltTracker.Alt(o, rs.getString(2), rs.getInt(3), rs.getLong(4));
                    if (prev == null || prev.lastSeen() < a.lastSeen()) {
                        out.put(o, a);
                    }
                }
            }
        }

        return new ArrayList<>(out.values());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vortexpvp.staff.alts")) {
            sender.sendMessage(Text.mm("<red>Staff only."));
        } else if (args.length == 0) {
            sender.sendMessage(Text.mm("<red>Usage: /alts \\<player>"));
        } else if (this.plugin.db() == null) {
            sender.sendMessage(Text.mm("<red>The database is not running."));
        } else {
            OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                target = Bukkit.getOfflinePlayerIfCached(args[0]);
            }

            if (target == null || target.getName() == null) {
                sender.sendMessage(Text.mm("<red>Never seen a player called " + Text.esc(args[0]) + "."));
                return true;
            }

            OfflinePlayer t = target;
            this.plugin.db().supply(c -> this.linked(c, t.getUniqueId())).thenAccept(alts -> Tasks.ensureSync(() -> this.show(sender, t, alts))).exceptionally(err -> {
                Tasks.ensureSync(() -> sender.sendMessage(Text.mm("<red>Lookup failed: " + Text.esc(String.valueOf(err.getMessage())))));
                return null;
            });
        }

        return true;
    }

    private void show(CommandSender to, OfflinePlayer t, List<AltTracker.Alt> alts) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        to.sendMessage(Text.mm(" "));
        to.sendMessage(Text.mm("<gold><b>Alts of " + Text.esc(t.getName()) + "</b></gold> <gray>(" + alts.size() + " linked account" + (alts.size() == 1 ? "" : "s") + ")"));
        if (alts.isEmpty()) {
            to.sendMessage(Text.mm("<gray>No other account has used the same IP."));
        }

        for (AltTracker.Alt a : alts) {
            OfflinePlayer o = Bukkit.getOfflinePlayer(a.uuid());
            Player online = o.getPlayer();
            boolean banned = o.isBanned();
            boolean muted = online != null && this.plugin.chatFilter() != null && this.plugin.chatFilter().muted(online);
            String state = (online != null ? "<green>● online" : "<gray>○ offline")
                + (banned ? " <red>BANNED" : "")
                + (muted ? " <yellow>MUTED" : "");
            String n = Text.esc(a.name() == null ? a.uuid().toString() : a.name());
            to.sendMessage(
                Text.mm(
                    " <dark_gray>•</dark_gray> <white>" + n + "</white> " + state
                        + " <dark_gray>|</dark_gray> <gray>" + a.shared() + " IP" + (a.shared() == 1 ? "" : "s") + " shared, last seen "
                        + fmt.format(new Date(a.lastSeen()))
                        + " <click:run_command:'/alts " + n + "'><aqua>[Check]</aqua></click>"
                        + " <click:suggest_command:'/mute " + n + " 1d '><red>[Punish]</red></click>"
                )
            );
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }

        return out;
    }
}
