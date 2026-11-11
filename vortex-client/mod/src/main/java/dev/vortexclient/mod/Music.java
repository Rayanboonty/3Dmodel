package dev.vortexclient.mod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The launcher's music player, seen from the game: what's playing and its
 * lyrics (music/now-playing.json, written by the launcher), and the play,
 * next and previous keys (music/commands.txt, read by the launcher).
 */
public final class Music {
    private Music() {}

    private static final String DIR = System.getProperty("vortex.music", "");
    private static long modified = -1;
    private static int readAt = -100;

    public static String title = "", artist = "";
    public static boolean playing, radio;
    private static long positionMs, durationMs, at;
    /** (time in ms, line), in order. */
    public static List<Long> lyricTimes = new ArrayList<>();
    public static List<String> lyricLines = new ArrayList<>();

    public static boolean available() {
        return !DIR.isEmpty();
    }

    /** Re-reads the launcher's state twice a second, when it changed. */
    static void tick() {
        if (!available() || VortexMods.ticks - readAt < 10) return;
        readAt = VortexMods.ticks;
        try {
            Path p = Path.of(DIR, "now-playing.json");
            if (!Files.exists(p)) {
                title = "";
                playing = false;
                return;
            }
            long m = Files.getLastModifiedTime(p).toMillis();
            if (m == modified) return;
            modified = m;
            JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            title = str(o, "title");
            artist = str(o, "artist");
            playing = o.has("playing") && o.get("playing").getAsBoolean();
            radio = "radio".equals(str(o, "kind"));
            positionMs = num(o, "positionMs");
            durationMs = num(o, "durationMs");
            at = num(o, "at");
            List<Long> times = new ArrayList<>();
            List<String> lines = new ArrayList<>();
            if (o.has("lyrics") && o.get("lyrics").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("lyrics")) {
                    JsonArray pair = e.getAsJsonArray();
                    times.add(pair.get(0).getAsLong());
                    lines.add(pair.get(1).getAsString());
                }
            }
            lyricTimes = times;
            lyricLines = lines;
        } catch (Exception ignored) {
            // Half-written or missing: next time.
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static long num(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : 0;
    }

    public static boolean active() {
        return !title.isEmpty();
    }

    /** Where the song is now, in ms. */
    public static long position() {
        long p = positionMs + (playing ? Math.max(0, System.currentTimeMillis() - at) : 0);
        return durationMs > 0 ? Math.min(p, durationMs) : p;
    }

    public static long duration() {
        return durationMs;
    }

    /** The lyric line playing now, or -1. */
    public static int line() {
        long p = position() + 150;
        int i = -1;
        for (int k = 0; k < lyricTimes.size(); k++) {
            if (lyricTimes.get(k) <= p) i = k;
            else break;
        }
        return i;
    }

    /** Tell the launcher a key was pressed: toggle, next or prev. */
    static void send(String command) {
        if (!available()) return;
        try {
            Path d = Path.of(DIR);
            Files.createDirectories(d);
            Files.writeString(d.resolve("commands.txt"), command + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            // Show the change straight away; the launcher confirms it shortly.
            if (command.equals("toggle") && active()) {
                positionMs = position();
                at = System.currentTimeMillis();
                playing = !playing;
            }
        } catch (Exception e) {
            VortexMods.LOG.warn("Couldn't reach the launcher's music player", e);
        }
    }
}
