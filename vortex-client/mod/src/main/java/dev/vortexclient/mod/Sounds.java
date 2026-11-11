package dev.vortexclient.mod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

/** Menu sounds (the factory method's name differs between versions). */
public final class Sounds {
    private Sounds() {}

    public static void click(MinecraftClient client) {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
