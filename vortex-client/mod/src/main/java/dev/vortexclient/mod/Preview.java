package dev.vortexclient.mod;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** The live preview next to a mod's options: what the setting looks like. */
final class Preview {
    private Preview() {}

    private static final int TEXT = 0xFFF2F4F7, DIM = 0xFF8D96A5;

    /** A little sky-and-grass scene, like a screenshot of the game. */
    static void scene(DrawContext c, int x, int y, int w, int h, int sky1, int sky2) {
        c.fillGradient(x, y, x + w, y + h, sky1, sky2);
        int ground = y + h * 2 / 3;
        c.fill(x, ground, x + w, ground + 3, 0xFF6DAA3C);
        c.fill(x, ground + 3, x + w, y + h, 0xFF7A5537);
        for (int i = 0; i < w; i += 6) c.fill(x + i, ground + 3 + (i * 7 % 5), x + i + 2, ground + 5 + (i * 7 % 5), 0xFF6A472D);
    }

    static void draw(DrawContext c, Module m, int x, int y, int w, int h) {
        Config cfg = VortexMods.config;
        int cx = x + w / 2, cy = y + h / 2;
        c.enableScissor(x, y, x + w, y + h);
        if (m.hud && Hud.has(m)) {
            scene(c, x, y, w, h, 0xFF7FA6D9, 0xFFB9D1EA);
            int[] s = Hud.size(m);
            float scale = (float) cfg.num(m, "scale");
            float fit = Math.min(scale, Math.min((w - 16f) / Math.max(1, s[0]), (h - 16f) / Math.max(1, s[1])));
            int ew = Math.round(s[0] * fit), eh = Math.round(s[1] * fit);
            Hud.drawAt(c, m, x + (w - ew) / 2, y + (h - eh) / 2, fit);
        } else {
            switch (m.id) {
                case "crosshair" -> {
                    scene(c, x, y, w, h, 0xFF7FA6D9, 0xFFB9D1EA);
                    Crosshair.drawAt(c, cx, cy, true);
                }
                case "hitmarker" -> {
                    scene(c, x, y, w, h, 0xFF7FA6D9, 0xFFB9D1EA);
                    boolean red = cfg.bool(m, "killred") && (System.currentTimeMillis() / 900) % 2 == 0;
                    int col = red ? 0xFFFF4040 : 0xFFFFFFFF;
                    int n = (int) cfg.num(m, "size");
                    for (int i = 2; i < 2 + n; i++) {
                        c.fill(cx - i - 1, cy - i - 1, cx - i, cy - i, col);
                        c.fill(cx + i, cy - i - 1, cx + i + 1, cy - i, col);
                        c.fill(cx - i - 1, cy + i, cx - i, cy + i + 1, col);
                        c.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, col);
                    }
                    caption(c, x, y, w, h, red ? "On a kill" : "On a hit");
                }
                case "lowhp" -> {
                    scene(c, x, y, w, h, 0xFF7FA6D9, 0xFFB9D1EA);
                    float pulse = cfg.bool(m, "pulse") ? 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 180.0) : 1f;
                    int a = Math.round(0.6f * pulse * 255);
                    int edge = (a << 24) | 0xD01818;
                    int d = Math.max(12, h / 4);
                    c.fillGradient(x, y, x + w, y + d, edge, 0x00D01818);
                    c.fillGradient(x, y + h - d, x + w, y + h, 0x00D01818, edge);
                    caption(c, x, y, w, h, "Below " + fmt(cfg.num(m, "below")) + " hearts");
                }
                case "blockoutline" -> {
                    scene(c, x, y, w, h, 0xFF7FA6D9, 0xFFB9D1EA);
                    int s = Math.min(w, h) / 3;
                    c.drawItem(new ItemStack(net.minecraft.item.Items.GRASS_BLOCK), cx - 8, cy - 8);
                    int col = VortexMods.outlineColor(0xFF000000);
                    Ui.outline(c, cx - s / 2, cy - s / 2, s, s, col);
                    Ui.outline(c, cx - s / 2 + 1, cy - s / 2 + 1, s - 2, s - 2, col);
                }
                case "fullbright", "boost" -> {
                    boolean on = cfg.enabled(m);
                    int half = w / 2;
                    scene(c, x, y, half, h, 0xFF1B2333, 0xFF2A3345);
                    c.fill(x, y, x + half, y + h, 0x90000000);
                    scene(c, x + half, y, w - half, h, 0xFF7FA6D9, 0xFFB9D1EA);
                    c.fill(x + half, y, x + half + 1, y + h, 0xFFFFFFFF);
                    if (m.id.equals("boost")) {
                        Ui.text(c, "Your settings", x + 6, y + h - 12, TEXT);
                        Ui.text(c, "FPS Boost", x + half + 6, y + h - 12, 0xFF16110A);
                        int ty = y + 6;
                        for (String l : boostLines(m)) {
                            Ui.text(c, l, x + half + 6, ty, 0xFF16110A);
                            ty += 10;
                        }
                    } else {
                        Ui.text(c, "Night", x + 6, y + h - 12, TEXT);
                        Ui.text(c, on ? "With Fullbright" : "Turn on to see", x + half + 6, y + h - 12, 0xFF16110A);
                    }
                }
                case "timechanger", "weather" -> {
                    String t = m.id.equals("weather") ? "Day" : cfg.str(m, "time");
                    int[] sky = switch (t) {
                        case "Sunrise" -> new int[] {0xFFF4A96B, 0xFFFCD9A4};
                        case "Sunset" -> new int[] {0xFF7A4B8C, 0xFFF28C5A};
                        case "Night", "Midnight" -> new int[] {0xFF0B1022, 0xFF1C2440};
                        default -> new int[] {0xFF7FA6D9, 0xFFB9D1EA};
                    };
                    scene(c, x, y, w, h, sky[0], sky[1]);
                    if (m.id.equals("weather") && !cfg.enabled(m)) {
                        for (int i = 0; i < 60; i++) {
                            int rx = x + (i * 37 + (int) (System.currentTimeMillis() / 6)) % w;
                            int ry = y + (i * 53 + (int) (System.currentTimeMillis() / 3)) % h;
                            c.fill(rx, ry, rx + 1, ry + 5, 0x8099B4FF);
                        }
                    }
                    caption(c, x, y, w, h, m.id.equals("weather") ? (cfg.enabled(m) ? "No rain or snow" : "Rain") : t.equals("Custom") ? (int) cfg.num(m, "hour") + ":00" : t);
                }
                case "zoom" -> {
                    scene(c, x, y, w, h, 0xFF7FA6D9, 0xFFB9D1EA);
                    float z = (float) cfg.num(m, "level");
                    Gfx.push(c);
                    Gfx.translate(c, cx, cy);
                    Gfx.scale(c, Math.min(4f, z / 2f), Math.min(4f, z / 2f));
                    c.drawItem(new ItemStack(net.minecraft.item.Items.OAK_SAPLING), -8, -8);
                    Gfx.pop(c);
                    caption(c, x, y, w, h, fmt(z) + "x, hold C");
                }
                case "timestamps", "mentions", "nickhider", "autogg" -> chat(c, m, x, y, w, h);
                case "tabping", "badge" -> tab(c, m, x, y, w, h);
                case "music" -> {
                    scene(c, x, y, w, h, 0xFF7FA6D9, 0xFFB9D1EA);
                    Ui.text(c, "Shown while music plays", x + 6, y + 6, 0xFF16110A);
                }
                default -> icon(c, m, x, y, w, h);
            }
        }
        c.disableScissor();
        Ui.round(c, x + 4, y + 4, Ui.width("Preview") + 8, 11, 0xA0000000);
        Ui.text(c, "Preview", x + 8, y + 6, 0xFFFFFFFF);
    }

    private static List<String> boostLines(Module m) {
        Config cfg = VortexMods.config;
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("View " + (int) cfg.num(m, "view") + " chunks");
        if (cfg.bool(m, "graphics")) out.add("Fast graphics");
        if (cfg.bool(m, "particles")) out.add("Fewest particles");
        if (cfg.bool(m, "clouds")) out.add("No clouds");
        if (cfg.bool(m, "shadows")) out.add("No shadows");
        if (cfg.bool(m, "lighting")) out.add("Flat lighting");
        if (cfg.bool(m, "entities")) out.add("Fewer far entities");
        return out;
    }

    private static void chat(DrawContext c, Module m, int x, int y, int w, int h) {
        Config cfg = VortexMods.config;
        c.fill(x, y, x + w, y + h, 0xFF1B1E24);
        int ty = y + h - 40;
        String me = net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername();
        String[] lines;
        switch (m.id) {
            case "timestamps" -> {
                boolean sec = cfg.bool(m, "seconds");
                String f = cfg.bool(m, "h24") ? (sec ? "HH:mm:ss" : "HH:mm") : (sec ? "h:mm:ss" : "h:mm");
                String t = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern(f, Locale.ROOT));
                lines = new String[] {"§7[" + t + "] §f<Alex> gg", "§7[" + t + "] §f<Steve> rematch?"};
            }
            case "mentions" -> lines = new String[] {"§f<Alex> hi all", cfg.bool(m, "highlight") ? "§6§l» §e<Alex> " + me + " come here" : "§f<Alex> " + me + " come here"};
            case "nickhider" -> lines = new String[] {"§f<" + VortexMods.nick() + "> hello", "§fAlex: hi " + VortexMods.nick()};
            default -> lines = new String[] {"§61st Killer §f- Alex", "§f<" + me + "> " + cfg.str(m, "msg")};
        }
        for (String l : lines) {
            c.fill(x + 4, ty - 1, x + w - 4, ty + 9, 0x60000000);
            c.drawText(Ui.font(), l, x + 6, ty, 0xFFFFFFFF, true);
            ty += 12;
        }
    }

    private static void tab(DrawContext c, Module m, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, 0xFF1B1E24);
        int rw = Math.min(w - 12, 150), rx = x + (w - rw) / 2, ty = y + 22;
        String[][] rows = {{"Steve", "24"}, {"Alex", "86"}, {"Notch", "171"}};
        for (String[] r : rows) {
            c.fill(rx, ty, rx + rw, ty + 10, 0x80000000);
            String name = r[0];
            if (m.id.equals("badge") && !name.equals("Notch")) name = "VC " + name;
            c.drawText(Ui.font(), name, rx + 2, ty + 1, name.startsWith("VC") ? 0xFFFFB547 : 0xFFFFFFFF, false);
            if (m.id.equals("tabping")) {
                int ms = Integer.parseInt(r[1]);
                int col = !VortexMods.config.bool(m, "color") ? 0xFFAAAAAA : ms < 80 ? 0xFF55FF55 : ms < 150 ? 0xFFFFFF55 : 0xFFFFAA00;
                String t = ms + "ms";
                c.drawText(Ui.font(), t, rx + rw - Ui.width(t) - 2, ty + 1, col, false);
            }
            ty += 11;
        }
    }

    /** No picture of its own: its icon, big, and what it does. */
    private static void icon(DrawContext c, Module m, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, 0xFF16181D);
        Gfx.push(c);
        Gfx.translate(c, x + w / 2f - 24, y + h / 2f - 34);
        Gfx.scale(c, 3f, 3f);
        c.drawItem(new ItemStack(m.icon), 0, 0);
        Gfx.pop(c);
        String d = m.description;
        int ty = y + h / 2 + 22;
        for (String line : wrap(d, w - 16)) {
            Ui.centered(c, line, x + w / 2, ty, DIM);
            ty += 10;
            if (ty > y + h - 10) break;
        }
    }

    static List<String> wrap(String s, int width) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (Ui.width(next) > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private static void caption(DrawContext c, int x, int y, int w, int h, String t) {
        int tw = Ui.width(t) + 8;
        Ui.round(c, x + (w - tw) / 2, y + h - 16, tw, 12, 0xA0000000);
        Ui.centered(c, t, x + w / 2, y + h - 14, TEXT);
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
