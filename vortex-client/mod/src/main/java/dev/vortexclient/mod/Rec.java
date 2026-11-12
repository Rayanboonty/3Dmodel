package dev.vortexclient.mod;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Screen recording, done by the launcher: F12 asks it to start or stop, and a REC badge shows while it records. */
final class Rec {
    private Rec() {}

    private static long since; // unix seconds the recording started, 0 when not recording
    private static int checkedAt = -100;

    static void key() {
        if (!VortexMods.on("recording")) return;
        if (!Music.available()) {
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("[Vortex] Recording works when you start Minecraft from the Vortex launcher").formatted(net.minecraft.util.Formatting.YELLOW));
            return;
        }
        Music.send("record");
    }

    static void tick() {
        if (!Music.available() || VortexMods.ticks - checkedAt < 10) return;
        checkedAt = VortexMods.ticks;
        try {
            Path p = Path.of(Music.dir(), "recording");
            since = Files.exists(p) ? Long.parseLong(Files.readString(p).trim()) : 0;
        } catch (Exception e) {
            since = 0;
        }
    }

    static void draw(DrawContext c, int sw) {
        if (since <= 0 || !VortexMods.on("recording") || !VortexMods.config.bool(Module.get("recording"), "badge")) return;
        long s = Math.max(0, System.currentTimeMillis() / 1000 - since);
        String t = "REC " + (s / 60) + ":" + String.format("%02d", s % 60);
        int w = Ui.width(t) + 18, x = (sw - w) / 2, y = 4;
        Ui.round(c, x, y, w, 14, 0xC0101114);
        boolean blink = (System.currentTimeMillis() / 600) % 2 == 0;
        c.fill(x + 6, y + 5, x + 10, y + 9, blink ? 0xFFFF4A4A : 0x80FF4A4A);
        Ui.text(c, t, x + 13, y + 3, 0xFFF2F4F7);
    }
}
