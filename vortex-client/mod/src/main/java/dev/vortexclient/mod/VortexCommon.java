package dev.vortexclient.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Runs on both sides: the badge's server part (no client classes here). */
public final class VortexCommon implements ModInitializer {
    @Override
    public void onInitialize() {
        Net.common();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> VortexServer.reset());
    }
}
