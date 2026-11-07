package dev.vortexclient.mod;

import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

/** The client's part of finding other Vortex players (1.20.1 to 1.20.4). */
public final class NetClient {
    private NetClient() {}

    private static boolean said;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Net.PLAYERS, (client, handler, buf, sender) -> {
            List<UUID> ids = Net.read(buf);
            client.execute(() -> VortexPlayers.set(ids));
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> { said = false; VortexPlayers.clear(); hello(); });
        C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> hello());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { said = false; VortexPlayers.clear(); });
    }

    private static void hello() {
        if (said || !ClientPlayNetworking.canSend(Net.HELLO)) return;
        said = true;
        ClientPlayNetworking.send(Net.HELLO, PacketByteBufs.create());
    }
}
