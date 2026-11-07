package dev.vortexclient.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Finding other Vortex players: each Vortex client says hello, and the server
 * (built-in or with Vortex) sends everyone the list. Vanilla servers never
 * hear the hello (the client only sends it where the server takes it).
 * This one is for 1.20.5 and later (payloads); 1.20.1 and 1.20.4 use
 * channels, in versions/<mc>/java.
 */
public final class Net {
    private Net() {}

    public record Hello() implements CustomPayload {
        public static final CustomPayload.Id<Hello> ID = new CustomPayload.Id<>(Identifier.of("vortex", "hello"));
        public static final PacketCodec<PacketByteBuf, Hello> CODEC = PacketCodec.unit(new Hello());

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record Players(List<UUID> ids) implements CustomPayload {
        public static final CustomPayload.Id<Players> ID = new CustomPayload.Id<>(Identifier.of("vortex", "players"));
        public static final PacketCodec<PacketByteBuf, Players> CODEC = PacketCodec.of(Players::write, Players::read);

        private void write(PacketByteBuf buf) {
            buf.writeVarInt(ids.size());
            for (UUID id : ids) buf.writeUuid(id);
        }

        private static Players read(PacketByteBuf buf) {
            int n = Math.min(buf.readVarInt(), 4096);
            List<UUID> ids = new ArrayList<>(n);
            for (int i = 0; i < n; i++) ids.add(buf.readUuid());
            return new Players(ids);
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Both sides: the payload types and the server's part. */
    public static void common() {
        PayloadTypeRegistry.playC2S().register(Hello.ID, Hello.CODEC);
        PayloadTypeRegistry.playS2C().register(Players.ID, Players.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Hello.ID, (payload, context) -> VortexServer.hello(context.server(), context.player().getUuid()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VortexServer.left(server, handler.player.getUuid()));
    }

    public static void broadcast(MinecraftServer server, List<UUID> ids) {
        server.execute(() -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (ServerPlayNetworking.canSend(p, Players.ID)) ServerPlayNetworking.send(p, new Players(ids));
            }
        });
    }
}
