package dev.vortexclient.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Finding other Vortex players, for 1.20.1 to 1.20.4 (channels and buffers). */
public final class Net {
    private Net() {}

    public static final Identifier HELLO = new Identifier("vortex", "hello");
    public static final Identifier PLAYERS = new Identifier("vortex", "players");

    public static void common() {
        ServerPlayNetworking.registerGlobalReceiver(HELLO, (server, player, handler, buf, sender) -> {
            UUID id = player.getUuid();
            server.execute(() -> VortexServer.hello(server, id));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VortexServer.left(server, handler.player.getUuid()));
    }

    public static void broadcast(MinecraftServer server, List<UUID> ids) {
        server.execute(() -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!ServerPlayNetworking.canSend(p, PLAYERS)) continue;
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(ids.size());
                for (UUID id : ids) buf.writeUuid(id);
                ServerPlayNetworking.send(p, PLAYERS, buf);
            }
        });
    }

    public static List<UUID> read(PacketByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 4096);
        List<UUID> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add(buf.readUuid());
        return ids;
    }
}
