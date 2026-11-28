package net.vortexrealm.pack;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

public final class PackBuilder {
    public static final String NS = "vortex";
    public static final String FONT_ICONS = "icons";
    public static final String FONT_RANK = "rank";
    public static final int FLAG_BASE = 57344;
    public static final int SEAL = 61184;
    public static final int PLAY = 61185;
    public static final int FRAMES = 16;
    public static final int SHINES = 8;
    public static final int RANK_BASE = 57600;
    public static final int RANK_STRIDE = 32;
    public static final int GLOW_BASE = 57376;
    public static final int GLOW_LEVELS = 3;
    public static final int SP_AFTER_GLOW = 61440;
    public static final int SP_BACK_BADGE = 61441;
    public static final int SP_TRAIL = 61442;
    public static final int SP_GLOW_EMPTY = 61443;
    public static final int SP_BADGE_EMPTY = 61444;
    public static final int BADGE_PX = 128;
    public static final int BADGE_H = 20;
    public static final int BADGE_ASCENT = 19;
    public static final int GLOW_PX = 96;
    public static final int GLOW_H = 28;
    public static final int GLOW_ASCENT = 23;

    private PackBuilder() {
    }

    public static float advance(int pixelWidth, int pixelHeight, int height) {
        float scale = (float)height / pixelHeight;
        return (int)(0.5F + pixelWidth * scale) + 1;
    }

    public static float badgeWidthUnits() {
        return BadgeArt.badgeWidth(128) * 0.15625F;
    }

    public static float glowWidthUnits() {
        return 28.0F;
    }

    public static float badgeAdvance() {
        return advance(BadgeArt.badgeWidth(128), 128, 20);
    }

    public static float glowAdvance() {
        return advance(96, 96, 28);
    }

    public static PackBuilder.Built build(String description, LinkedHashMap<String, String[]> flags, List<PackBuilder.TierArt> tiers) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(
            "pack.mcmeta",
            ("{\n  \"pack\": {\n    \"description\": \""
                    + json(description)
                    + "\",\n    \"pack_format\": 75,\n    \"min_format\": 75,\n    \"max_format\": 9999\n  }\n}\n")
                .getBytes(StandardCharsets.UTF_8)
        );
        files.put("pack.png", png(BadgeArt.packIcon(128)));
        int cols = 16;
        List<String> codes = new ArrayList<>(flags.keySet());
        int rows = Math.max(1, (codes.size() + cols - 1) / cols);
        BufferedImage grid = new BufferedImage(cols * 24, rows * 16, 2);
        Graphics2D g = grid.createGraphics();

        for (int i = 0; i < codes.size(); i++) {
            g.drawImage(FlagArt.icon(codes.get(i), flags.get(codes.get(i))), i % cols * 24, i / cols * 16, null);
        }

        g.dispose();
        files.put("assets/vortex/textures/font/flags.png", png(grid));
        files.put("assets/vortex/textures/font/seal.png", png(BadgeArt.seal(16)));
        files.put("assets/vortex/textures/font/play.png", png(BadgeArt.play(22, 16)));
        StringBuilder icons = new StringBuilder("{\n  \"providers\": [\n");
        icons.append("    {\"type\": \"bitmap\", \"file\": \"")
            .append("vortex")
            .append(":font/flags.png\", \"ascent\": 7, ")
            .append("\"height\": 8, \"chars\": [");

        for (int r = 0; r < rows; r++) {
            StringBuilder row = new StringBuilder();

            for (int cc = 0; cc < cols; cc++) {
                int i = r * cols + cc;
                row.append(i < codes.size() ? esc(57344 + i) : "\\u0000");
            }

            icons.append(r == 0 ? "" : ", ").append('"').append((CharSequence)row).append('"');
        }

        icons.append("]},\n");
        icons.append("    {\"type\": \"bitmap\", \"file\": \"")
            .append("vortex")
            .append(":font/seal.png\", \"ascent\": 7, ")
            .append("\"height\": 8, \"chars\": [\"")
            .append(esc(61184))
            .append("\"]},\n");
        icons.append("    {\"type\": \"bitmap\", \"file\": \"")
            .append("vortex")
            .append(":font/play.png\", \"ascent\": 7, ")
            .append("\"height\": 8, \"chars\": [\"")
            .append(esc(61185))
            .append("\"]},\n");
        BufferedImage logo = logo();
        if (logo != null) {
            // the server logo for the tab header: 36 px tall, hanging down over the three empty lines below it
            files.put("assets/vortex/textures/font/logo.png", png(logo));
            icons.append("    {\"type\": \"bitmap\", \"file\": \"")
                .append("vortex")
                .append(":font/logo.png\", \"ascent\": 8, ")
                .append("\"height\": 36, \"chars\": [\"")
                .append(esc(Glyphs.LOGO))
                .append("\"]},\n");
        }

        // pixel-exact spaces (1..128 px forward, and back) for smooth sliding text
        icons.append("    {\"type\": \"space\", \"advances\": {");

        for (int i = 0; i < 8; i++) {
            icons.append(i == 0 ? "" : ", ")
                .append('"')
                .append(esc(Glyphs.SPACE + i))
                .append("\": ")
                .append(1 << i)
                .append(", \"")
                .append(esc(Glyphs.BACK + i))
                .append("\": ")
                .append(-(1 << i));
        }

        icons.append("}}\n  ]\n}\n");
        files.put("assets/vortex/font/icons.json", icons.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder rank = new StringBuilder("{\n  \"providers\": [\n");
        float after = -glowAdvance() + (glowWidthUnits() - badgeWidthUnits()) / 2.0F;
        float back = -badgeAdvance();
        float used = glowAdvance() + after + badgeAdvance();
        float trail = glowWidthUnits() - used;
        rank.append("    {\"type\": \"space\", \"advances\": {\"")
            .append(esc(61440))
            .append("\": ")
            .append(fmt(after))
            .append(", \"")
            .append(esc(61441))
            .append("\": ")
            .append(fmt(back))
            .append(", \"")
            .append(esc(61442))
            .append("\": ")
            .append(fmt(trail))
            .append(", \"")
            .append(esc(61443))
            .append("\": ")
            .append(fmt(glowAdvance()))
            .append(", \"")
            .append(esc(61444))
            .append("\": ")
            .append(fmt(badgeAdvance()))
            .append("}},\n");
        int frameCols = 6;
        int bw = BadgeArt.badgeWidth(128);

        for (int t = 0; t < tiers.size(); t++) {
            PackBuilder.TierArt art = tiers.get(t);
            BufferedImage badge = BadgeArt.badge(art.label(), art.rgb(), art.number(), art.step(), 128);
            int total = 25;
            int frameRows = (total + frameCols - 1) / frameCols;
            BufferedImage sheet = new BufferedImage(frameCols * bw, frameRows * 128, 2);
            Graphics2D sg = sheet.createGraphics();

            for (int f = 0; f < total; f++) {
                BufferedImage frame = f < 16
                    ? BadgeArt.reveal(badge, (f + 1) / 16.0, art.rgb())
                    : (f == 16 ? pinned(badge) : BadgeArt.shineOver(badge, (f - 16 - 1) / 7.0));
                sg.drawImage(frame, f % frameCols * bw, f / frameCols * 128, null);
            }

            sg.dispose();
            String name = art.label().toLowerCase(Locale.ROOT);
            files.put("assets/vortex/textures/font/rank/" + name + ".png", png(sheet));
            rank.append("    {\"type\": \"bitmap\", \"file\": \"")
                .append("vortex")
                .append(":font/rank/")
                .append(name)
                .append(".png\", \"ascent\": ")
                .append(19)
                .append(", \"height\": ")
                .append(20)
                .append(", \"chars\": [");

            for (int r = 0; r < frameRows; r++) {
                StringBuilder row = new StringBuilder();

                for (int cc = 0; cc < frameCols; cc++) {
                    int f = r * frameCols + cc;
                    row.append(f < total ? esc(57600 + t * 32 + f) : "\\u0000");
                }

                rank.append(r == 0 ? "" : ", ").append('"').append((CharSequence)row).append('"');
            }

            rank.append("]},\n");

            for (int lvl = 0; lvl < 3; lvl++) {
                String gname = name + "_" + lvl;
                files.put("assets/vortex/textures/font/glow/" + gname + ".png", png(BadgeArt.glow(art.rgb(), 0.45 + lvl * 0.3, 96)));
                rank.append("    {\"type\": \"bitmap\", \"file\": \"")
                    .append("vortex")
                    .append(":font/glow/")
                    .append(gname)
                    .append(".png\", \"ascent\": ")
                    .append(23)
                    .append(", \"height\": ")
                    .append(28)
                    .append(", \"chars\": [\"")
                    .append(esc(57376 + t * 3 + lvl))
                    .append("\"]},\n");
            }
        }

        int lastComma = rank.lastIndexOf("},\n");
        rank.replace(lastComma, lastComma + 3, "}\n");
        rank.append("  ]\n}\n");
        files.put("assets/vortex/font/rank.json", rank.toString().getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Entry<String, byte[]> e : files.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTime(315532800000L);
                zip.putNextEntry(entry);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }

        return new PackBuilder.Built(bytes.toByteArray(), files.size());
    }

    private static BufferedImage logo() {
        try (InputStream in = PackBuilder.class.getResourceAsStream("/logo/vr.png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException var5) {
            return null;
        }
    }

    private static BufferedImage pinned(BufferedImage badge) {
        BufferedImage copy = new BufferedImage(badge.getWidth(), badge.getHeight(), 2);
        Graphics2D g = copy.createGraphics();
        g.drawImage(badge, 0, 0, null);
        g.dispose();
        if (copy.getRGB(copy.getWidth() - 1, 0) >>> 24 == 0) {
            copy.setRGB(copy.getWidth() - 1, 0, 16777216);
        }

        return copy;
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static String esc(int cp) {
        return String.format(Locale.ROOT, "\\u%04X", cp);
    }

    private static String fmt(float f) {
        return String.format(Locale.ROOT, "%.4f", f);
    }

    private static String json(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record Built(byte[] zip, int files) {
    }

    public record TierArt(String label, int rgb, int number, int step) {
    }
}
