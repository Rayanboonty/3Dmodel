package dev.vortexclient.mod;

import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The client's part of finding other Vortex players (1.20.5 and later). */
public final class NetClient {
    private NetClient() {}

    private static boolean said;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Net.Players.ID, (payload, context) -> VortexPlayers.set(payload.ids()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> { said = false; VortexPlayers.clear(); hello(); });
        // The server can announce what it takes a moment after joining.
        C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> hello());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { said = false; VortexPlayers.clear(); });
    }

    private static void hello() {
        if (said || !ClientPlayNetworking.canSend(Net.Hello.ID)) return;
        said = true;
        ClientPlayNetworking.send(new Net.Hello());
    }
}
