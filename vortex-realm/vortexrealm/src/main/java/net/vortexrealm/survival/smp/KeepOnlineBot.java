package net.vortexrealm.survival.smp;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;
import net.vortexrealm.survival.VortexSMP;
import net.vortexrealm.survival.config.Cfg;
import net.vortexrealm.survival.config.Opt;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

/**
 * A keep-online player for hosts that put an empty server to sleep (only where the host allows it): one player that
 * lives inside the server itself - no second machine and no Minecraft account - so the server never counts as empty.
 * It sits invisible in spectator mode at the spawn, never joins or leaves in chat, is hidden from the tab list, is
 * ignored for sleeping, and is marked "NPC" so other plugins (and the auto-updater) don't count it as a real player.
 */
public final class KeepOnlineBot implements Listener {
    private final VortexSMP plugin;
    private Object handle;
    private Object channel;
    private UUID uuid;
    private BukkitTask task;
    private String lastError;

    public KeepOnlineBot(VortexSMP plugin) {
        this.plugin = plugin;
    }

    public boolean online() {
        Player p = this.player();
        return p != null && p.isOnline();
    }

    public Player player() {
        return this.uuid == null ? null : Bukkit.getPlayer(this.uuid);
    }

    public String status() {
        return this.online() ? "online as " + this.player().getName() + " (keeps the server from going to sleep)"
            : (Cfg.bool(Opt.KEEPALIVE_BOT) ? "starting / failed" + (this.lastError == null ? "" : ": " + this.lastError) : "off");
    }

    public static boolean isBot(Player p) {
        return p.hasMetadata("vortex-keepalive");
    }

    /** Starts the bot when the setting is on (and stops it when it was turned off). */
    public void refresh() {
        if (Cfg.bool(Opt.KEEPALIVE_BOT) && !this.online()) {
            this.spawn();
        } else if (!Cfg.bool(Opt.KEEPALIVE_BOT) && this.online()) {
            this.despawn();
        }
    }

    private void spawn() {
        String name = Cfg.s(Opt.KEEPALIVE_NAME).replaceAll("[^A-Za-z0-9_]", "");
        if (name.length() < 3 || name.length() > 16) {
            name = "VortexBot";
        }

        if (Bukkit.getPlayerExact(name) != null) {
            this.lastError = "a real player named " + name + " is online";
            return;
        }

        try {
            World w = Bukkit.getWorlds().get(0);
            this.uuid = UUID.nameUUIDFromBytes(("VortexKeepOnline:" + name).getBytes(StandardCharsets.UTF_8));
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            Object level = w.getClass().getMethod("getHandle").invoke(w);
            Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
            Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(this.uuid, name);
            Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
            Object info = infoClass.getMethod("createDefault").invoke(null);
            Class<?> spClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Constructor<?> spc = null;
            for (Constructor<?> c : spClass.getConstructors()) {
                if (c.getParameterCount() == 4) {
                    spc = c;
                }
            }

            this.handle = spc.newInstance(server, level, profile, info);

            // a connection with an in-memory channel: everything sent to the bot is dropped
            Class<?> flowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object serverbound = Enum.valueOf((Class<Enum>)flowClass, "SERVERBOUND");
            Class<?> connClass = Class.forName("net.minecraft.network.Connection");
            Object conn = connClass.getConstructor(flowClass).newInstance(serverbound);
            Class<?> handlerClass = Class.forName("io.netty.channel.ChannelHandler");
            Class<?> embedded = Class.forName("io.netty.channel.embedded.EmbeddedChannel");
            Object handlers = Array.newInstance(handlerClass, 0);
            this.channel = embedded.getConstructor(handlers.getClass()).newInstance(handlers);
            connClass.getField("channel").set(conn, this.channel);
            connClass.getField("address").set(conn, new InetSocketAddress("127.0.0.1", 0));
            Class<?> cookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
            Object cookie = cookieClass.getMethod("createInitial", profileClass, boolean.class).invoke(null, profile, false);
            Object playerList = server.getClass().getMethod("getPlayerList").invoke(server);
            Method place = null;
            for (Method m : playerList.getClass().getMethods()) {
                if (m.getName().equals("placeNewPlayer") && m.getParameterCount() == 3) {
                    place = m;
                }
            }

            place.invoke(playerList, conn, this.handle, cookie);
            Player p = Bukkit.getPlayer(this.uuid);
            if (p == null) {
                throw new IllegalStateException("the server didn't take the player");
            }

            p.setMetadata("vortex-keepalive", new FixedMetadataValue(this.plugin.host(), true));
            p.setMetadata("NPC", new FixedMetadataValue(this.plugin.host(), true));
            p.setGameMode(GameMode.SPECTATOR);
            p.setInvulnerable(true);
            p.setSleepingIgnored(true);
            p.setCollidable(false);
            p.setSilent(true);
            Location spawn = w.getSpawnLocation().clone().add(0.5, 20.0, 0.5);
            p.teleport(spawn);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(p) && Cfg.bool(Opt.KEEPALIVE_HIDE)) {
                    other.hidePlayer(this.plugin.host(), p);
                }
            }

            this.task = Bukkit.getScheduler().runTaskTimer(this.plugin.host(), this::upkeep, 40L, 40L);
            this.lastError = null;
            this.plugin.getLogger().info("Keep-online bot " + name + " is online: the server won't count as empty.");
        } catch (Throwable t) {
            this.lastError = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
            this.plugin.getLogger().log(Level.WARNING, "The keep-online bot couldn't start (this Paper version may not support it)", t);
            this.uuid = null;
            this.handle = null;
        }
    }

    /** Answers the server's keep-alive checks for the bot and throws away what was sent to it. */
    private void upkeep() {
        if (!this.online()) {
            if (this.task != null) {
                this.task.cancel();
                this.task = null;
            }

            this.refresh();
            return;
        }

        try {
            Object listener = this.handle.getClass().getField("connection").get(this.handle);
            Class<?> c = listener.getClass();
            Field ka = null;
            while (c != null && ka == null) {
                try {
                    ka = c.getDeclaredField("keepAlive");
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }

            if (ka != null) {
                ka.setAccessible(true);
                Object keep = ka.get(listener);
                Object queue = keep.getClass().getField("pendingKeepAlives").get(keep);
                Method poll = queue.getClass().getMethod("poll");
                while (poll.invoke(queue) != null) {
                    // nothing waits for an answer
                }

                keep.getClass().getField("lastKeepAliveTx").setLong(keep, System.nanoTime());
            }

            this.channel.getClass().getMethod("releaseOutbound").invoke(this.channel);
        } catch (Throwable t) {
            this.plugin.getLogger().log(Level.FINE, "Keep-online bot upkeep", t);
        }
    }

    public void despawn() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }

        Player p = this.player();
        if (p != null) {
            try {
                Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
                Object playerList = server.getClass().getMethod("getPlayerList").invoke(server);
                Method remove = null;
                for (Method m : playerList.getClass().getMethods()) {
                    if (m.getName().equals("remove") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(this.handle)) {
                        remove = m;
                    }
                }

                remove.invoke(playerList, this.handle);
            } catch (Throwable t) {
                p.kick();
            }
        }

        this.uuid = null;
        this.handle = null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (this.uuid != null && p.getUniqueId().equals(this.uuid)) {
            e.joinMessage(null);
            return;
        }

        Player bot = this.player();
        if (bot != null && Cfg.bool(Opt.KEEPALIVE_HIDE)) {
            p.hidePlayer(this.plugin.host(), bot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        if (this.uuid != null && e.getPlayer().getUniqueId().equals(this.uuid)) {
            e.quitMessage(null);
        }
    }
}
