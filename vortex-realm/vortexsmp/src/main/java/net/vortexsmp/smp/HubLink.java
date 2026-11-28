package net.vortexsmp.smp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.vortexsmp.VortexSMP;
import net.vortexsmp.config.Cfg;
import net.vortexsmp.config.Opt;
import net.vortexsmp.util.Snd;
import net.vortexsmp.util.Tasks;
import net.vortexsmp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/** /hub and /lobby: back to the Practice server, by the built-in transfer or through Velocity/BungeeCord. */
public final class HubLink implements CommandExecutor, Listener {
    private static final String CHANNEL = "BungeeCord";
    private final VortexSMP plugin;
    private final Map<UUID, Long> sending = new ConcurrentHashMap<>();
    private volatile int[] lobby = null;
    private BukkitTask task;

    public HubLink(VortexSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        try {
            Bukkit.getMessenger().registerOutgoingPluginChannel(this.plugin, CHANNEL);
        } catch (RuntimeException var2) {
            this.plugin.getLogger().log(Level.FINE, "BungeeCord channel", (Throwable)var2);
        }

        this.task = Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, () -> {
            String host = Cfg.s(Opt.HUB_HOST);
            this.lobby = host.isBlank() ? null : ping(host, Cfg.i(Opt.HUB_PORT), 2500);
        }, 60L, 400L);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
        }
    }

    /** Players on the lobby server, or -1 when it doesn't answer. */
    public int lobbyPlayers() {
        Realm realm = this.plugin.realm();
        if (realm.on()) {
            int n = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                n += realm.smp(p.getWorld()) || KeepOnlineBot.isBot(p) ? 0 : 1;
            }

            return n;
        }

        int[] l = this.lobby;
        return l == null ? -1 : l[0];
    }

    /** {online, max} from a server-list ping, or null without an answer. */
    public static int[] ping(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(hs);
            writeVarInt(d, 0);
            writeVarInt(d, 774);
            byte[] h = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(d, h.length);
            d.write(h);
            d.writeShort(port);
            writeVarInt(d, 1);
            DataOutputStream o = new DataOutputStream(s.getOutputStream());
            writeVarInt(o, hs.size());
            o.write(hs.toByteArray());
            o.writeByte(1);
            o.writeByte(0);
            o.flush();
            DataInputStream in = new DataInputStream(s.getInputStream());
            readVarInt(in);
            if (readVarInt(in) != 0) {
                return null;
            }

            int len = readVarInt(in);
            if (len <= 0 || len > 1 << 20) {
                return null;
            }

            byte[] json = new byte[len];
            in.readFully(json);
            JsonElement root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8));
            if (root.isJsonObject() && root.getAsJsonObject().has("players")) {
                JsonObject pl = root.getAsJsonObject().getAsJsonObject("players");
                return new int[]{pl.has("online") ? pl.get("online").getAsInt() : 0, pl.has("max") ? pl.get("max").getAsInt() : 0};
            }

            return new int[]{0, 0};
        } catch (IOException | RuntimeException var14) {
            return null;
        }
    }

    private static void writeVarInt(DataOutputStream o, int v) throws IOException {
        while ((v & -128) != 0) {
            o.writeByte(v & 127 | 128);
            v >>>= 7;
        }

        o.writeByte(v);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int v = 0;
        int n = 0;

        byte b;
        do {
            b = in.readByte();
            v |= (b & 127) << n++ * 7;
            if (n > 5) {
                throw new IOException("VarInt too big");
            }
        } while ((b & 128) == 128);

        return v;
    }

    public void send(Player p) {
        boolean proxy = "PROXY".equalsIgnoreCase(Cfg.s(Opt.NETWORK_MODE));
        String host = Cfg.s(Opt.HUB_HOST);
        if (proxy ? Cfg.s(Opt.HUB_PROXY).isBlank() : host.isBlank()) {
            p.sendMessage(Text.mm("<red>The lobby isn't set up yet. <gray>(network.hub in plugins/VortexSMP/config.yml)"));
            Snd.error(p);
            return;
        }

        if (this.plugin.combatTag() != null && this.plugin.combatTag().tagged(p)) {
            p.sendMessage(Text.mm("<red>You can't leave in combat. <gray>(" + this.plugin.combatTag().secondsLeft(p) + "s)"));
            Snd.error(p);
            return;
        }

        Long t = this.sending.get(p.getUniqueId());
        if (t != null && System.currentTimeMillis() - t < 8000L) {
            return;
        }

        this.sending.put(p.getUniqueId(), System.currentTimeMillis());
        p.showTitle(net.kyori.adventure.title.Title.title(
            Text.mm("<b><gradient:#FFE259:#FFA751>LOBBY</gradient></b>"),
            Text.mm("<gray>Connecting..."),
            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(150L), java.time.Duration.ofMillis(1500L), java.time.Duration.ofMillis(300L))
        ));
        Snd.play(p, "block.portal.trigger", 0.3F, 1.8F);

        for (int i = 0; i < 20; i += 2) {
            int k = i;
            Tasks.later(k, () -> {
                if (p.isOnline()) {
                    Location c = p.getLocation();

                    for (int j = 0; j < 4; j++) {
                        double a = k * 0.5 + j * Math.PI / 2.0;
                        p.getWorld().spawnParticle(Particle.PORTAL, c.getX() + Math.cos(a) * 0.9, c.getY() + k * 0.1, c.getZ() + Math.sin(a) * 0.9, 3, 0.0, 0.0, 0.0, 0.1);
                    }

                    int n = (int)Math.round(k / 20.0 * 10.0);
                    p.sendActionBar(Text.mm("<gray>Connecting <gradient:#FFE259:#FFA751>" + "▮".repeat(n) + "</gradient><dark_gray>" + "▯".repeat(10 - n)));
                }
            });
        }

        Tasks.later(20L, () -> {
            if (!p.isOnline()) {
                return;
            }

            try {
                p.saveData();
                if (proxy) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    DataOutputStream o = new DataOutputStream(b);
                    o.writeUTF("Connect");
                    o.writeUTF(Cfg.s(Opt.HUB_PROXY));
                    p.sendPluginMessage(this.plugin, CHANNEL, b.toByteArray());
                } else {
                    p.transfer(host, Cfg.i(Opt.HUB_PORT));
                }
            } catch (IOException | RuntimeException var4) {
                this.sending.remove(p.getUniqueId());
                p.sendMessage(Text.mm("<red>The lobby can't be reached right now."));
                this.plugin.getLogger().log(Level.WARNING, "Sending " + p.getName() + " to the lobby failed", (Throwable)var4);
                return;
            }

            Tasks.later(100L, () -> {
                if (this.sending.remove(p.getUniqueId()) != null && p.isOnline()) {
                    p.sendMessage(Text.mm("<red>Couldn't connect to the lobby. <gray>Is it online?"));
                }
            });
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.sending.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p && this.plugin.realm().on()) {
            // same server: VortexRealm's lobby (combat tag is checked before this runs)
            this.plugin.realm().toHub(p);
        } else if (sender instanceof Player p) {
            this.send(p);
        } else {
            sender.sendMessage("Players only.");
        }

        return true;
    }

    public List<String> info() {
        int n = this.lobbyPlayers();
        return List.of(n < 0 ? "offline" : String.valueOf(n));
    }
}
