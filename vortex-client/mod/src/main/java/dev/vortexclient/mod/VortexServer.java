package dev.vortexclient.mod;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;

/**
 * The server side of the Vortex badge: who on this server plays with Vortex.
 * Runs in singleplayer's built-in server (so LAN friends see each other) and
 * on any server that has the Vortex mod installed. Nothing here touches the
 * client, so it also loads on a dedicated server.
 */
public final class VortexServer {
    private VortexServer() {}

    private static final Set<UUID> PLAYERS = ConcurrentHashMap.newKeySet();

    /** A player said hello: remember them and tell every Vortex player. */
    public static void hello(MinecraftServer server, UUID id) {
        PLAYERS.add(id);
        Net.broadcast(server, List.copyOf(PLAYERS));
    }

    public static void left(MinecraftServer server, UUID id) {
        if (PLAYERS.remove(id)) Net.broadcast(server, List.copyOf(PLAYERS));
    }

    public static void reset() {
        PLAYERS.clear();
    }
}
