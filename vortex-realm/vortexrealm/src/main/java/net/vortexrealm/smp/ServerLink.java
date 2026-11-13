package net.vortexrealm.smp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Sends players to the other servers of the network (the SMP on its own host, other modes...).
 * Without a proxy it uses the built-in server transfer ({@code accepts-transfers=true} on the other server);
 * behind Velocity or BungeeCord it asks the proxy to connect them. The other servers are pinged in the
 * background for their player counts.
 */
public final class ServerLink {
    public static final String CHANNEL = "BungeeCord";
    private final VortexRealm plugin;
    private final Map<String, ServerLink.Ping> pings = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Long> sending = new ConcurrentHashMap<>();
    private BukkitTask task;

    public ServerLink(VortexRealm plugin) {
        this.plugin = plugin;
    }

    public void start() {
        try {
            Bukkit.getMessenger().registerOutgoingPluginChannel(this.plugin, CHANNEL);
        } catch (RuntimeException var2) {
            this.plugin.getLogger().log(Level.FINE, "BungeeCord channel", (Throwable)var2);
        }

        this.task = Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, this::pingAll, 40L, 400L);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
        }
    }

    /** Last ping of a SERVER entry, or null before the first answer. */
    public ServerLink.Ping ping(ServerSelector.Entry e) {
        return this.pings.get(e.id);
    }

    private void pingAll() {
        if (!Cfg.bool(Opt.NETWORK_PING) || this.plugin.serverSelector() == null) {
            return;
        }

        for (ServerSelector.Entry e : this.plugin.serverSelector().servers()) {
            if (e.host().isBlank()) {
                continue;
            }

            this.pings.put(e.id, ping(e.host(), e.port(), 2500));
        }
    }

    /** A Minecraft server-list ping (handshake + status request). Never throws. */
    public static ServerLink.Ping ping(String host, int port, int timeoutMs) {
        long at = System.currentTimeMillis();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(hs);
            writeVarInt(d, 0);
            writeVarInt(d, 774);
            byte[] h = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(d, h.length);
            d.write(h);
            d.writeShort(port);
            writeVarInt(d, 1);
            DataOutputStream o = new DataOutputStream(out);
            writeVarInt(o, hs.size());
            o.write(hs.toByteArray());
            o.writeByte(1);
            o.writeByte(0);
            o.flush();
            DataInputStream di = new DataInputStream(in);
            readVarInt(di);
            int id = readVarInt(di);
            if (id != 0) {
                return new ServerLink.Ping(false, 0, 0, at, -1);
            }

            int len = readVarInt(di);
            if (len <= 0 || len > 1 << 20) {
                return new ServerLink.Ping(false, 0, 0, at, -1);
            }

            byte[] json = new byte[len];
            di.readFully(json);
            JsonElement root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8));
            int online = 0;
            int max = 0;
            if (root.isJsonObject() && root.getAsJsonObject().has("players")) {
                JsonObject pl = root.getAsJsonObject().getAsJsonObject("players");
                online = pl.has("online") ? pl.get("online").getAsInt() : 0;
                max = pl.has("max") ? pl.get("max").getAsInt() : 0;
            }

            return new ServerLink.Ping(true, online, max, at, (int)(System.currentTimeMillis() - at));
        } catch (IOException | RuntimeException var17) {
            return new ServerLink.Ping(false, 0, 0, at, -1);
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

    /** True while the player is on the way to another server (a few seconds). */
    public boolean sending(Player p) {
        Long t = this.sending.get(p.getUniqueId());
        return t != null && System.currentTimeMillis() - t < 8000L;
    }

    /** Sends the player to a SERVER entry, with a short animation; their fight or queue is left safely first. */
    public void send(Player p, ServerSelector.Entry e) {
        PlayerState st = this.plugin.sessions().state(p);
        if (st == PlayerState.MATCH || st == PlayerState.PREMATCH || st == PlayerState.FFA || st == PlayerState.BOT) {
            p.sendMessage(Text.mm("<red>Finish your fight first."));
            Snd.error(p);
            return;
        }

        if (this.sending(p)) {
            return;
        }

        boolean proxy = Opt.NetworkMode.PROXY.name().equals(Cfg.s(Opt.NETWORK_MODE));
        if (proxy ? e.proxy().isBlank() : e.host().isBlank()) {
            p.sendMessage(Text.mm("<red>" + Text.plain(Text.mm(e.name)) + " isn't set up yet. <gray>(" + (proxy ? "proxy server name" : "host and port") + " in /pvpadmin → Server Selector)"));
            Snd.error(p);
            return;
        }

        ServerLink.Ping ping = this.ping(e);
        if (!proxy && ping != null && !ping.online() && System.currentTimeMillis() - ping.at() < 30000L && !p.hasPermission("vortex.admin")) {
            p.sendMessage(Text.mm("<red>" + Text.plain(Text.mm(e.name)) + " is offline right now. <gray>Try again in a minute."));
            Snd.error(p);
            return;
        }

        this.sending.put(p.getUniqueId(), System.currentTimeMillis());
        p.closeInventory();
        this.plugin.leavePvP(p, false);
        this.plugin.profiles().save(this.plugin.profiles().get(p));
        String name = Text.plain(Text.mm(e.name));
        net.vortexrealm.fx.Fx.shine(p, "CONNECTING", net.vortexrealm.fx.Fx.GOLD, Text.mm("<gray>to <white>" + Text.esc(name) + " <dark_gray>• <gray>hold tight"), 40);
        Snd.play(p, "block.portal.trigger", 0.3F, 1.8F);

        for (int i = 0; i < 20; i += 2) {
            int t = i;
            Tasks.later(t, () -> {
                if (p.isOnline()) {
                    double a = t * 0.5;
                    org.bukkit.Location c = p.getLocation();

                    for (int k = 0; k < 4; k++) {
                        double ang = a + k * Math.PI / 2.0;
                        p.getWorld().spawnParticle(Particle.PORTAL, c.getX() + Math.cos(ang) * 0.9, c.getY() + t * 0.1, c.getZ() + Math.sin(ang) * 0.9, 3, 0.0, 0.0, 0.0, 0.1);
                    }

                    p.sendActionBar(Text.mm("<gray>Connecting " + bar(t / 20.0) + " <white>" + Text.esc(name)));
                }
            });
        }

        Tasks.later(20L, () -> {
            if (!p.isOnline()) {
                return;
            }

            try {
                if (proxy) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    DataOutputStream o = new DataOutputStream(b);
                    o.writeUTF("Connect");
                    o.writeUTF(e.proxy());
                    p.sendPluginMessage(this.plugin, CHANNEL, b.toByteArray());
                } else {
                    p.transfer(e.host(), e.port());
                }
            } catch (IOException | RuntimeException var5) {
                this.sending.remove(p.getUniqueId());
                p.sendMessage(Text.mm("<red>" + Text.esc(name) + " can't be reached right now."));
                this.plugin.getLogger().log(Level.WARNING, "Sending " + p.getName() + " to " + e.id + " failed", (Throwable)var5);
                return;
            }

            // a proxy that never answers (wrong server name) must not leave the player stuck in "connecting"
            Tasks.later(100L, () -> {
                if (this.sending.remove(p.getUniqueId()) != null && p.isOnline()) {
                    p.sendMessage(Text.mm("<red>Couldn't connect to " + Text.esc(name) + ". <gray>Is the server online?"));
                    this.plugin.lobby().sendToLobby(p);
                }
            });
        });
    }

    private static String bar(double f) {
        int n = (int)Math.round(Math.max(0.0, Math.min(1.0, f)) * 10.0);
        return "<gradient:#FFE259:#FFA751>" + "▮".repeat(n) + "</gradient><dark_gray>" + "▯".repeat(10 - n) + "</dark_gray>";
    }

    public void forget(Player p) {
        this.sending.remove(p.getUniqueId());
    }

    public record Ping(boolean online, int players, int max, long at, int latency) {
    }
}
