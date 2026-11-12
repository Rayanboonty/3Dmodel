package dev.vortexclient.mod;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Draws the HUD elements and works out where each one goes. */
public final class Hud {
    private Hud() {}

    public record Rect(int x, int y, int w, int h, float scale) {
        public boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    /** Size of an element before scaling: {width, height}. */
    interface Element {
        int[] size(Module m);

        void draw(DrawContext c, Module m, int x, int y);
    }

    static final Map<String, Element> ELEMENTS = new LinkedHashMap<>();
    /** Elements that stack down the left edge by default, in order. */
    private static final List<String> LEFT = List.of("fps", "cps", "ping", "memory", "server", "players", "speed", "reach", "combo", "clock", "day", "saturation", "health", "xp", "light", "biome", "rotation", "chunk", "entities", "forecast", "moon", "fall", "bow", "session", "pack",
            "kills", "distance", "armorpts", "stopwatch", "tps", "tnttimer", "cooldowns", "resources", "blocks", "waypoints", "fire", "air", "attack", "hunger", "elytra", "mount", "lookat", "helditem",
            "coords", "deathpos", "durability", "sprint");
    /** Graphs and lists, under the right-hand stack by default. */
    private static final List<String> BOTTOM_LEFT = List.of("fpsgraph", "pinggraph", "nearby");
    /** Elements that stack down the right edge by default, in order. */
    private static final List<String> RIGHT = List.of("keystrokes", "potions");

    private static final Map<String, Float> KEY_ANIM = new java.util.HashMap<>();
    private static long lastFrame = System.nanoTime();
    private static float frameDt = 0.016f;

    // ------------------------------------------------------------- colours --

    static int textColor(Module m) {
        return switch (VortexMods.config.str(m, "color")) {
            case "Accent" -> VortexMods.accent();
            case "Aqua" -> 0xFF55FFFF;
            case "Gold" -> 0xFFFFAA00;
            case "Green" -> 0xFF55FF55;
            case "Pink" -> 0xFFFF77CC;
            case "Rainbow" -> rainbow(0);
            case "Custom" -> 0xFF000000 | java.awt.Color.HSBtoRGB((float) (VortexMods.config.num(m, "hue") / 360.0), 0.6f, 1f);
            default -> 0xFFFFFFFF;
        };
    }

    // ---- HUD Style (0.9): one look for every element.
    static Module style() {
        return Module.get("hudstyle");
    }

    static boolean styled() {
        return VortexMods.config.enabled(style());
    }

    static String theme() {
        return styled() ? VortexMods.config.str(style(), "theme") : "Glass";
    }

    static int rainbow(long offsetMs) {
        double speed = styled() ? VortexMods.config.num(style(), "rainbow") : 4;
        long period = Math.round(16000 / Math.max(1, speed));
        return 0xFF000000 | java.awt.Color.HSBtoRGB(((System.currentTimeMillis() + offsetMs) % period) / (float) period, 0.65f, 1f);
    }

    static int labelColor(Module m) {
        if (!styled()) return VortexMods.accent();
        return switch (VortexMods.config.str(style(), "labelcolor")) {
            case "White" -> 0xFFFFFFFF;
            case "Gray" -> 0xFFAAAAAA;
            case "Same as text" -> textColor(m);
            default -> VortexMods.accent();
        };
    }

    /** Text as the style wants it (UPPER, lower). */
    static String tc(String s) {
        if (!styled()) return s;
        return switch (VortexMods.config.str(style(), "textcase")) {
            case "UPPER" -> s.toUpperCase(Locale.ROOT);
            case "lower" -> s.toLowerCase(Locale.ROOT);
            default -> s;
        };
    }

    static boolean bold() {
        return styled() && VortexMods.config.bool(style(), "bold");
    }

    static int tw(String s) {
        return bold() ? Ui.font().getWidth(Text.literal(s).formatted(net.minecraft.util.Formatting.BOLD)) : Ui.width(s);
    }

    static void str(DrawContext c, String s, int x, int y, int color, boolean shadow) {
        if (bold()) c.drawText(Ui.font(), Text.literal(s).formatted(net.minecraft.util.Formatting.BOLD), x, y, color, shadow);
        else c.drawText(Ui.font(), s, x, y, color, shadow);
    }

    static String lab(Module m, String label) {
        return VortexMods.config.bool(m, "labels") || m.option("labels") == null ? tc(label) : "";
    }

    /** The world's time of day as a clock (6:00 at sunrise). */
    static String worldClock(MinecraftClient mc, boolean h24) {
        long t = mc.world != null ? ((mc.world.getTimeOfDay() % 24000L) + 24000L) % 24000L : 6000L;
        int mins = (int) (((t + 6000L) % 24000L) * 60L / 1000L);
        int h = mins / 60, mi = mins % 60;
        if (h24) return String.format(Locale.ROOT, "%02d:%02d", h, mi);
        return String.format(Locale.ROOT, "%d:%02d %s", h % 12 == 0 ? 12 : h % 12, mi, h < 12 ? "AM" : "PM");
    }

    static boolean shadow(Module m) {
        return VortexMods.config.bool(m, "shadow");
    }

    static void panel(DrawContext c, Module m, int x, int y, int w, int h) {
        int accent = VortexMods.accent();
        int want = styled() ? (int) VortexMods.config.num(style(), "radius") : 4;
        int r = Math.max(0, Math.min(want, Math.min(w, h) / 3));
        if (VortexMods.config.bool(m, "background")) {
            double op = VortexMods.config.num(m, "opacity");
            float k = (float) Math.max(0, Math.min(1, Double.isNaN(op) ? 0.45 : op));
            int a = Math.round(k * 255);
            switch (theme()) {
                case "Solid" -> {
                    Ui.rounded(c, x, y, w, h, r, (Math.max(a, 200) << 24) | 0x14171D);
                    c.fill(x + r, y + h - 1, x + w - r, y + h, Ui.alpha(accent, 0.9f));
                }
                case "Minimal" -> c.fill(x, y + h - 1, x + w, y + h, Ui.alpha(accent, 0.6f + 0.4f * k));
                case "Outline" -> {
                    Ui.rounded(c, x, y, w, h, r, Math.round(a * 0.35f) << 24);
                    Ui.roundedOutline(c, x, y, w, h, r, 0x90FFFFFF);
                }
                case "Neon" -> {
                    Ui.rounded(c, x, y, w, h, r, (a << 24) | 0x0A0612);
                    Ui.roundedOutline(c, x - 1, y - 1, w + 2, h + 2, r + 1, Ui.alpha(accent, 0.25f));
                    Ui.roundedOutline(c, x, y, w, h, r, Ui.alpha(accent, 0.95f));
                }
                case "Clear" -> { }
                default -> {
                    // Rounded glass, like the launcher's panels.
                    Ui.rounded(c, x, y, w, h, r, a << 24);
                    c.fill(x + r, y, x + w - r, y + 1, 0x30FFFFFF);
                }
            }
        }
        if (VortexMods.config.bool(m, "border")) Ui.roundedOutline(c, x, y, w, h, r, Ui.alpha(accent, 0.85f));
        if (styled() && VortexMods.config.bool(style(), "accentbar")) c.fill(x, y + Math.min(2, h / 4), x + 2, y + h - Math.min(2, h / 4), accent);
    }

    static void line(DrawContext c, Module m, String label, String value, int x, int y) {
        line(c, m, label, value, x, y, textColor(m));
    }

    static String bracket(Module m, String value) {
        if (value.isEmpty()) return value;
        return switch (VortexMods.config.str(m, "brackets")) {
            case "[ ]" -> "[" + value + "]";
            case "( )" -> "(" + value + ")";
            default -> value;
        };
    }

    static void line(DrawContext c, Module m, String label, String value, int x, int y, int color) {
        String l = lab(m, label), v = tc(value);
        int lw = tw(l);
        if (!l.isEmpty()) str(c, l, x, y, labelColor(m), shadow(m));
        str(c, v, x + lw, y, color, shadow(m));
    }

    /** A simple element made of label/value lines. */
    static Element lines(java.util.function.Function<Module, List<String[]>> content) {
        return new Element() {
            // Worked out once a tick and reused for every frame in between.
            private List<String[]> cache;
            private int cachedAt = -1;

            private List<String[]> get(Module m) {
                if (cache == null || cachedAt != VortexMods.ticks) {
                    cache = content.apply(m);
                    cachedAt = VortexMods.ticks;
                }
                return cache;
            }

            public int[] size(Module m) {
                List<String[]> ls = get(m);
                int w = 0;
                for (String[] l : ls) w = Math.max(w, tw(lab(m, l[0])) + tw(tc(bracket(m, l[1]))));
                return new int[] {w + 8 + (styled() && VortexMods.config.bool(style(), "accentbar") ? 2 : 0), ls.size() * 10 + 5};
            }

            public void draw(DrawContext c, Module m, int x, int y) {
                List<String[]> ls = get(m);
                int[] s = size(m);
                panel(c, m, x, y, s[0], s[1]);
                int ty = y + 4;
                int tx = x + 4 + (styled() && VortexMods.config.bool(style(), "accentbar") ? 2 : 0);
                for (String[] l : ls) {
                    int col = l.length > 2 ? (int) Long.parseLong(l[2], 16) : textColor(m);
                    line(c, m, l[0], bracket(m, l[1]), tx, ty, col);
                    ty += 10;
                }
            }
        };
    }

    static {
        MinecraftClient mc = MinecraftClient.getInstance();
        ELEMENTS.put("fps", lines(m -> {
            int fps = mc.getCurrentFps();
            String v = fps + " FPS";
            if (VortexMods.config.bool(m, "frametime")) v += String.format(Locale.ROOT, " %.1f ms", 1000.0 / Math.max(1, fps));
            List<String[]> out = new ArrayList<>();
            String col = fps >= 120 ? "FF55FF55" : fps >= 60 ? "FFB6F36B" : fps >= 30 ? "FFFFFF55" : "FFFF5555";
            out.add(VortexMods.config.bool(m, "fpscolor") ? new String[] {"", v, col} : new String[] {"", v});
            if (VortexMods.config.bool(m, "minmax")) {
                int[] h = Stats.history(Stats.FPS_HISTORY, Stats.fpsCount);
                int lo = Integer.MAX_VALUE, hi = 0;
                for (int f : h) { lo = Math.min(lo, f); hi = Math.max(hi, f); }
                if (h.length == 0) { lo = fps; hi = fps; }
                out.add(new String[] {"Low ", lo + "  High " + hi});
            }
            return out;
        }));
        ELEMENTS.put("cps", lines(m -> {
            String v = VortexMods.config.bool(m, "right") ? Stats.cps(0) + " | " + Stats.cps(1) : String.valueOf(Stats.cps(0));
            if (VortexMods.config.bool(m, "peak")) return List.<String[]>of(new String[] {"", v + " CPS"}, new String[] {"Best ", String.valueOf(Stats.peakCps)});
            return List.<String[]>of(new String[] {"", v + " CPS"});
        }));
        ELEMENTS.put("ping", lines(m -> {
            int p = ping(mc);
            if (!VortexMods.config.bool(m, "quality")) return List.<String[]>of(new String[] {"", p + " ms"});
            String col = p <= 0 ? "FFAAAAAA" : p < 80 ? "FF55FF55" : p < 150 ? "FFFFFF55" : p < 250 ? "FFFFAA00" : "FFFF5555";
            return List.<String[]>of(new String[] {"", p + " ms", col});
        }));
        ELEMENTS.put("speed", lines(m -> {
            String unit = VortexMods.config.str(m, "unit");
            boolean kmh = unit.equals("km/h");
            double base = Stats.speed;
            if (VortexMods.config.bool(m, "vertical")) base = Math.sqrt(base * base + Stats.vspeed * Stats.vspeed);
            double v = kmh ? base * 3.6 : base;
            return List.<String[]>of(new String[] {"", String.format(Locale.ROOT, "%.2f %s", v, unit)});
        }));
        ELEMENTS.put("reach", lines(m -> {
            boolean recent = Stats.lastReach >= 0 && System.currentTimeMillis() - Stats.lastReachAt < 3000;
            String fmt = "%." + VortexMods.config.str(m, "decimals") + "f blocks";
            String v = recent ? String.format(Locale.ROOT, fmt, Stats.lastReach) : (mc.player == null ? String.format(Locale.ROOT, fmt, 3.0) : "Hit something");
            if (recent && VortexMods.config.bool(m, "rangecolor")) {
                String col = Stats.lastReach >= 2.9 ? "FFFF5555" : Stats.lastReach >= 2.5 ? "FFFFAA00" : "FF55FF55";
                return List.<String[]>of(new String[] {"", v, col});
            }
            return List.<String[]>of(new String[] {"", v});
        }));
        ELEMENTS.put("combo", lines(m -> {
            int n = mc.player == null ? 3 : Stats.combo;
            if (n == 0 && VortexMods.config.bool(m, "hidezero")) return List.<String[]>of();
            String v = n == 0 ? "No combo" : n + " combo";
            if (VortexMods.config.bool(m, "best")) return List.<String[]>of(new String[] {"", v}, new String[] {"Best ", String.valueOf(Math.max(Stats.bestCombo, mc.player == null ? 5 : 0))});
            return List.<String[]>of(new String[] {"", v});
        }));
        ELEMENTS.put("clock", lines(m -> {
            boolean sec = VortexMods.config.bool(m, "seconds");
            String f = VortexMods.config.bool(m, "h24") ? (sec ? "HH:mm:ss" : "HH:mm") : (sec ? "h:mm:ss a" : "h:mm a");
            String v = LocalTime.now().format(DateTimeFormatter.ofPattern(f, Locale.ROOT));
            if (VortexMods.config.bool(m, "date")) v = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM", Locale.ROOT)) + " " + v;
            if (VortexMods.config.bool(m, "weekday")) v = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("EEE", Locale.ROOT)) + " " + v;
            return List.<String[]>of(new String[] {"", v});
        }));
        ELEMENTS.put("coords", lines(m -> {
            List<String[]> out = new ArrayList<>();
            BlockPos p = mc.player != null ? mc.player.getBlockPos() : new BlockPos(128, 64, -340);
            int dec = Integer.parseInt(VortexMods.config.str(m, "decimals").isEmpty() ? "0" : VortexMods.config.str(m, "decimals"));
            double px = mc.player != null ? mc.player.getX() : p.getX(), py = mc.player != null ? mc.player.getY() : p.getY(), pz = mc.player != null ? mc.player.getZ() : p.getZ();
            String nf = "%." + dec + "f";
            out.add(new String[] {"X ", dec == 0 ? String.valueOf(p.getX()) : String.format(Locale.ROOT, nf, px)});
            out.add(new String[] {"Y ", dec == 0 ? String.valueOf(p.getY()) : String.format(Locale.ROOT, nf, py)});
            out.add(new String[] {"Z ", dec == 0 ? String.valueOf(p.getZ()) : String.format(Locale.ROOT, nf, pz)});
            if (VortexMods.config.bool(m, "nether")) {
                boolean inNether = mc.world != null && mc.world.getRegistryKey() == net.minecraft.world.World.NETHER;
                double k = inNether ? 8 : 0.125;
                out.add(new String[] {inNether ? "Overworld " : "Nether ", String.format(Locale.ROOT, "%d, %d", (int) Math.floor(px * k), (int) Math.floor(pz * k))});
            }
            if (VortexMods.config.bool(m, "facing")) {
                String dir = mc.player != null ? cap(mc.player.getHorizontalFacing().asString()) : "North";
                out.add(new String[] {"F ", dir});
            }
            if (VortexMods.config.bool(m, "dimension")) out.add(new String[] {"D ", HudPlus.dimension(mc)});
            if (VortexMods.config.bool(m, "biome")) {
                String biome = "Plains";
                if (mc.player != null && mc.world != null) {
                    biome = mc.world.getBiome(mc.player.getBlockPos()).getKey()
                            .map(k -> cap(k.getValue().getPath().replace('_', ' '))).orElse("?");
                }
                out.add(new String[] {"B ", biome});
            }
            return out;
        }));
        ELEMENTS.put("sprint", lines(m -> {
            String s;
            if (mc.player == null) s = "[Sprinting (Toggled)]";
            else if (mc.player.isSneaking()) s = mc.options.getSneakToggled().getValue() ? "[Sneaking (Toggled)]" : "[Sneaking]";
            else if (mc.options.sprintKey.isPressed()) s = mc.options.getSprintToggled().getValue() ? "[Sprinting (Toggled)]" : "[Sprinting]";
            else if (mc.player.isSprinting()) s = "[Sprinting]";
            else s = "";
            return s.isEmpty() ? List.<String[]>of() : List.<String[]>of(new String[] {"", s});
        }));
        ELEMENTS.put("keystrokes", new Keystrokes());
        ELEMENTS.put("armor", new Armor());
        ELEMENTS.put("potions", new Potions());
        ELEMENTS.put("memory", lines(m -> {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
            long pct = used * 100 / Math.max(1, max);
            String col = !VortexMods.config.bool(m, "usagecolor") ? null : pct >= 85 ? "FFFF5555" : pct >= 65 ? "FFFFAA00" : "FF55FF55";
            String v = VortexMods.config.bool(m, "percent") ? pct + "%" : String.format(Locale.ROOT, "%d%% %d/%d MB", pct, used, max);
            return List.<String[]>of(col == null ? new String[] {"Mem ", v} : new String[] {"Mem ", v, col});
        }));
        ELEMENTS.put("server", lines(m -> {
            String s = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : (mc.isInSingleplayer() || mc.player == null ? "Singleplayer" : "LAN");
            return List.<String[]>of(new String[] {"", s});
        }));
        ELEMENTS.put("day", lines(m -> {
            long d = mc.world != null ? mc.world.getTimeOfDay() / 24000L : 12;
            if (VortexMods.config.bool(m, "time")) return List.<String[]>of(new String[] {"Day ", d + ", " + worldClock(mc, true)});
            return List.<String[]>of(new String[] {"Day ", String.valueOf(d)});
        }));
        ELEMENTS.put("saturation", lines(m -> {
            float s = mc.player != null ? mc.player.getHungerManager().getSaturationLevel() : 5.0f;
            return List.<String[]>of(new String[] {"Saturation ", String.format(Locale.ROOT, "%.1f", s)});
        }));
        ELEMENTS.put("session", lines(m -> {
            long s = Math.max(0, (System.currentTimeMillis() - VortexMods.sessionStart) / 1000);
            String t = s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
            return List.<String[]>of(new String[] {"Session ", t});
        }));
        ELEMENTS.put("pack", lines(m -> {
            String name = "Default";
            for (var p : mc.getResourcePackManager().getEnabledProfiles()) {
                String id = Compat.packId(p);
                if (id.equals("vanilla") || id.startsWith("fabric")) continue;
                name = p.getDisplayName().getString();
            }
            return List.<String[]>of(new String[] {"Pack ", name});
        }));
        ELEMENTS.put("direction", new Direction());
        // ---- New in 0.8: world and player info.
        ELEMENTS.put("light", lines(m -> {
            if (mc.player == null || mc.world == null) return List.<String[]>of(new String[] {"Light ", "7"});
            BlockPos p = mc.player.getBlockPos();
            int block = mc.world.getLightLevel(net.minecraft.world.LightType.BLOCK, p);
            String col = block == 0 ? "FFFF5555" : block < 8 ? "FFFFFF55" : "FF55FF55";
            String v = String.valueOf(block);
            if (VortexMods.config.bool(m, "sky")) v += " (sky " + mc.world.getLightLevel(net.minecraft.world.LightType.SKY, p) + ")";
            return List.<String[]>of(new String[] {"Light ", v, col});
        }));
        ELEMENTS.put("players", lines(m -> {
            int n = mc.getNetworkHandler() != null ? mc.getNetworkHandler().getPlayerList().size() : 1;
            return List.<String[]>of(new String[] {"Players ", String.valueOf(n)});
        }));
        ELEMENTS.put("biome", lines(m -> {
            String biome = "Plains";
            if (mc.player != null && mc.world != null) {
                biome = mc.world.getBiome(mc.player.getBlockPos()).getKey().map(k -> cap(k.getValue().getPath().replace('_', ' '))).orElse("?");
            }
            return List.<String[]>of(new String[] {"Biome ", biome});
        }));
        ELEMENTS.put("rotation", lines(m -> {
            float yaw = mc.player != null ? net.minecraft.util.math.MathHelper.wrapDegrees(mc.player.getYaw()) : 90f;
            float pitch = mc.player != null ? mc.player.getPitch() : 12f;
            return List.<String[]>of(new String[] {"Yaw ", String.format(Locale.ROOT, "%.1f", yaw)}, new String[] {"Pitch ", String.format(Locale.ROOT, "%.1f", pitch)});
        }));
        ELEMENTS.put("chunk", lines(m -> {
            if (mc.player == null) return List.<String[]>of(new String[] {"Chunk ", "8, -22 (4, 12)"});
            var cp = mc.player.getChunkPos();
            BlockPos p = mc.player.getBlockPos();
            return List.<String[]>of(new String[] {"Chunk ", cp.x + ", " + cp.z + " (" + (p.getX() & 15) + ", " + (p.getZ() & 15) + ")"});
        }));
        ELEMENTS.put("entities", lines(m -> {
            int n = 0;
            if (mc.world != null) for (var e : mc.world.getEntities()) n++;
            return List.<String[]>of(new String[] {"Entities ", String.valueOf(mc.world == null ? 42 : n)});
        }));
        ELEMENTS.put("durability", lines(m -> {
            if (mc.player == null) return List.<String[]>of(new String[] {"Low ", "Diamond Sword 8%", "FFFF5555"});
            double limit = VortexMods.config.num(m, "threshold") / 100.0;
            List<String[]> out = new ArrayList<>();
            List<net.minecraft.item.ItemStack> stacks = new ArrayList<>();
            stacks.add(mc.player.getMainHandStack());
            for (EquipmentSlot s : Armor.SLOTS) stacks.add(mc.player.getEquippedStack(s));
            for (net.minecraft.item.ItemStack st : stacks) {
                if (st.isEmpty() || !st.isDamageable() || st.getMaxDamage() <= 0) continue;
                double left = 1.0 - (double) st.getDamage() / st.getMaxDamage();
                if (left > limit) continue;
                boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
                out.add(new String[] {"Low ", st.getName().getString() + " " + Math.round(left * 100) + "%", blink ? "FFFF5555" : "FFFFAA00"});
            }
            return out;
        }));
        ELEMENTS.put("deathpos", lines(m -> {
            if (VortexMods.deathPos == null) return mc.player == null ? List.<String[]>of(new String[] {"Died at ", "120, 64, -32"}) : List.<String[]>of();
            BlockPos d = VortexMods.deathPos;
            String v = d.getX() + ", " + d.getY() + ", " + d.getZ();
            if (VortexMods.config.bool(m, "distance") && mc.player != null) v += String.format(Locale.ROOT, " (%.0fm)", Math.sqrt(mc.player.getBlockPos().getSquaredDistance(d)));
            return List.<String[]>of(new String[] {"Died at ", v});
        }));
        ELEMENTS.put("bow", lines(m -> {
            if (mc.player == null) return List.<String[]>of(new String[] {"Bow ", "100%"});
            if (!mc.player.isUsingItem() || !(mc.player.getActiveItem().getItem() instanceof net.minecraft.item.BowItem)) return List.<String[]>of();
            float k = net.minecraft.item.BowItem.getPullProgress(mc.player.getItemUseTime());
            String v = Math.round(k * 100) + "%";
            if (VortexMods.config.bool(m, "arrows")) v += "  " + HudPlus.count(mc, Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW) + " arrows";
            return List.<String[]>of(new String[] {"Bow ", v, k >= 1f ? "FF55FF55" : "FFFFFF55"});
        }));
        ELEMENTS.put("fall", lines(m -> {
            double f = mc.player != null ? (double) mc.player.fallDistance : 0;
            if (mc.player != null && f < 0.5) return List.<String[]>of();
            return List.<String[]>of(new String[] {"Falling ", String.format(Locale.ROOT, "%.1f blocks", f), f >= VortexMods.config.num(m, "warn") ? "FFFF5555" : "FFFFFFFF"});
        }));
        ELEMENTS.put("health", lines(m -> {
            float hp = mc.player != null ? mc.player.getHealth() : 20f, max = mc.player != null ? mc.player.getMaxHealth() : 20f;
            float abs = mc.player != null ? mc.player.getAbsorptionAmount() : 0f;
            String v = String.format(Locale.ROOT, "%.0f/%.0f", hp, max) + (abs > 0 ? String.format(Locale.ROOT, " +%.0f", abs) : "");
            List<String[]> out = new ArrayList<>();
            out.add(new String[] {"Health ", v, hp <= max * 0.3f ? "FFFF5555" : "FFFFFFFF"});
            if (VortexMods.config.bool(m, "food")) out.add(new String[] {"Food ", String.valueOf(mc.player != null ? mc.player.getHungerManager().getFoodLevel() : 20)});
            return out;
        }));
        ELEMENTS.put("xp", lines(m -> {
            int lvl = mc.player != null ? mc.player.experienceLevel : 30;
            float k = mc.player != null ? mc.player.experienceProgress : 0.5f;
            if (VortexMods.config.bool(m, "points")) return List.<String[]>of(new String[] {"Level ", lvl + " (" + Math.round(k * 100) + "%)"}, new String[] {"Points ", String.valueOf(mc.player != null ? mc.player.totalExperience : 1395)});
            return List.<String[]>of(new String[] {"Level ", lvl + " (" + Math.round(k * 100) + "%)"});
        }));
        ELEMENTS.put("forecast", lines(m -> {
            String w = mc.world == null ? "Clear" : mc.world.isThundering() ? "Thunder" : mc.world.isRaining() ? "Rain" : "Clear";
            return List.<String[]>of(new String[] {"Weather ", w});
        }));
        ELEMENTS.put("moon", lines(m -> {
            String[] names = {"Full moon", "Waning gibbous", "Last quarter", "Waning crescent", "New moon", "Waxing crescent", "First quarter", "Waxing gibbous"};
            int phase = mc.world != null ? (int) ((mc.world.getTimeOfDay() / 24000L) % 8L) : 0;
            return List.<String[]>of(new String[] {"Moon ", names[(phase + 8) % 8]});
        }));
        ELEMENTS.put("target", new Target());
        ELEMENTS.put("items", new ItemCounter());
        HudPlus.register(mc);
    }

    /** A compass strip that slides as you turn. */
    static final class Direction implements Element {
        static final String[] MARKS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

        int w(Module m) {
            int w = (int) VortexMods.config.num(m, "width");
            return w < 100 ? 150 : w;
        }

        public int[] size(Module m) {
            return new int[] {w(m), 16};
        }

        public void draw(DrawContext c, Module m, int x, int y) {
            MinecraftClient mc = MinecraftClient.getInstance();
            float yaw = mc.player != null ? mc.player.getYaw() : 180f;
            yaw = ((yaw % 360) + 360) % 360;
            int W = w(m), half = W / 2;
            panel(c, m, x, y, W, 16);
            c.enableScissor(x + 2, y, x + W - 2, y + 16);
            float pxPerDeg = 1.2f;
            for (int i = -2; i < 10; i++) {
                float deg = i * 45f;
                float rel = deg - yaw;
                while (rel < -180) rel += 360;
                while (rel > 180) rel -= 360;
                int px = x + half + Math.round(rel * pxPerDeg);
                if (px < x - 20 || px > x + W + 20) continue;
                String s = MARKS[((i % 8) + 8) % 8];
                int col = s.length() == 1 ? (s.equals("N") ? 0xFFFF5555 : textColor(m)) : 0xFFAAAAAA;
                c.drawText(Ui.font(), s, px - Ui.width(s) / 2, y + 4, col, shadow(m));
            }
            for (int k = -60; k <= 60; k += 15) {
                float rel = (float) ((Math.round(yaw / 15f) * 15 + k) - yaw);
                int px = x + half + Math.round(rel * pxPerDeg);
                if (px > x + 2 && px < x + W - 2) c.fill(px, y + 13, px + 1, y + 15, 0x66FFFFFF);
            }
            c.disableScissor();
            if (VortexMods.config.bool(m, "degrees")) {
                String deg = Math.round(yaw) + "\u00b0";
                c.drawText(Ui.font(), deg, x + half - Ui.width(deg) / 2, y + 17, textColor(m), shadow(m));
            }
            c.fill(x + half - 1, y, x + half + 1, y + 3, VortexMods.accent());
        }
    }

    /** Health of the entity you're looking at, or last hit. */
    static final class Target implements Element {
        net.minecraft.entity.LivingEntity target() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return null;
            if (mc.targetedEntity instanceof net.minecraft.entity.LivingEntity le && le.isAlive()) return le;
            if (Stats.lastTarget != null && Stats.lastTarget.isAlive() && System.currentTimeMillis() - Stats.lastReachAt < 3000) return Stats.lastTarget;
            return null;
        }

        public int[] size(Module m) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && target() == null) return new int[] {0, 0};
            return new int[] {120, VortexMods.config.bool(m, "armor") ? 50 : 32};
        }

        public void draw(DrawContext c, Module m, int x, int y) {
            MinecraftClient mc = MinecraftClient.getInstance();
            net.minecraft.entity.LivingEntity t = target();
            String name = t != null ? t.getName().getString() : "Steve";
            float hp = t != null ? t.getHealth() : 14f, max = t != null ? t.getMaxHealth() : 20f;
            double dist = t != null && mc.player != null ? mc.player.distanceTo(t) : 3.1;
            panel(c, m, x, y, 120, size(m)[1]);
            if (name.length() > 16) name = name.substring(0, 15) + "…";
            c.drawText(Ui.font(), name, x + 5, y + 4, textColor(m), shadow(m));
            String d = String.format(Locale.ROOT, "%.1fm", dist);
            c.drawText(Ui.font(), d, x + 115 - Ui.width(d), y + 4, 0xFFAAAAAA, shadow(m));
            float f = Math.max(0, Math.min(1, hp / Math.max(1, max)));
            int col = f > 0.6f ? 0xFF55DD55 : f > 0.3f ? 0xFFFFAA33 : 0xFFFF5555;
            Ui.round(c, x + 5, y + 16, 110, 5, 0x66000000);
            Ui.round(c, x + 5, y + 16, Math.max(2, Math.round(110 * f)), 5, col);
            String h = VortexMods.config.bool(m, "hearts") ? String.format(Locale.ROOT, "%.1f / %.0f hearts", hp / 2f, max / 2f) : String.format(Locale.ROOT, "%.1f / %.0f ❤", hp, max);
            Gfx.push(c);
            Gfx.translate(c, x + 5, y + 23);
            Gfx.scale(c, 0.7f, 0.7f);
            c.drawText(Ui.font(), h, 0, 0, 0xFFDDDDDD, false);
            Gfx.pop(c);
            if (VortexMods.config.bool(m, "armor")) {
                int ax = x + 5;
                if (t == null) {
                    for (net.minecraft.item.Item it : new net.minecraft.item.Item[] {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.DIAMOND_SWORD}) {
                        c.drawItem(new ItemStack(it), ax, y + 31);
                        ax += 18;
                    }
                } else {
                    for (EquipmentSlot sl : Armor.SLOTS) {
                        ItemStack st = t.getEquippedStack(sl);
                        if (!st.isEmpty()) { c.drawItem(st, ax, y + 31); ax += 18; }
                    }
                    if (!t.getMainHandStack().isEmpty()) c.drawItem(t.getMainHandStack(), ax, y + 31);
                }
            }
        }
    }

    /** Counts of the items PvP players watch. */
    static final class ItemCounter implements Element {
        static final net.minecraft.item.Item[] TRACKED = {net.minecraft.item.Items.ARROW, net.minecraft.item.Items.ENDER_PEARL, net.minecraft.item.Items.GOLDEN_APPLE, net.minecraft.item.Items.TOTEM_OF_UNDYING};

        List<int[]> counts() {
            MinecraftClient mc = MinecraftClient.getInstance();
            List<int[]> out = new ArrayList<>();
            for (int i = 0; i < TRACKED.length; i++) {
                int n = 0;
                if (mc.player == null) n = new int[] {64, 16, 8, 1}[i];
                else {
                    var inv = mc.player.getInventory();
                    for (int s = 0; s < inv.size(); s++) {
                        ItemStack st = inv.getStack(s);
                        if (st.isOf(TRACKED[i])) n += st.getCount();
                    }
                }
                if (n > 0 || !hideZero) out.add(new int[] {i, n});
            }
            return out;
        }

        boolean hideZero = true;

        public int[] size(Module m) {
            hideZero = VortexMods.config.bool(m, "hidezero");
            List<int[]> cs = counts();
            if (cs.isEmpty()) return new int[] {0, 0};
            int w = 0;
            for (int[] e : cs) w = Math.max(w, 20 + Ui.width(String.valueOf(e[1])));
            return new int[] {w + 6, cs.size() * 17 + 5};
        }

        public void draw(DrawContext c, Module m, int x, int y) {
            int[] s = size(m);
            panel(c, m, x, y, s[0], s[1]);
            int ty = y + 3;
            for (int[] e : counts()) {
                c.drawItem(new ItemStack(TRACKED[e[0]]), x + 3, ty);
                c.drawText(Ui.font(), String.valueOf(e[1]), x + 22, ty + 4, e[1] == 0 ? 0xFFFF5555 : textColor(m), shadow(m));
                ty += 17;
            }
        }
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static int ping(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return 24;
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return e == null ? 0 : e.getLatency();
    }

    // ------------------------------------------------------------ elements --

    static final class Keystrokes implements Element {
        static final int K = 18, G = 2;

        public int[] size(Module m) {
            int h = K * 3 + G * 2;
            if (VortexMods.config.bool(m, "mouse")) h += K + G;
            if (VortexMods.config.bool(m, "space")) h += 8 + G;
            return new int[] {K * 3 + G * 2, h};
        }

        void key(DrawContext c, Module m, String id, String label, KeyBinding kb, int x, int y, int w, int h, String sub) {
            boolean down = kb != null && kb.isPressed();
            float a = KEY_ANIM.getOrDefault(id, 0f);
            a = Ui.approach(a, down ? 1f : 0f, 22f, frameDt);
            KEY_ANIM.put(id, a);
            int bg = VortexMods.config.bool(m, "background") ? 0x78000000 : 0x00000000;
            Ui.round(c, x, y, w, h, Ui.mix(bg, 0xE6FFFFFF, a));
            int tc = Ui.mix(textColor(m), 0xFF101010, a);
            boolean sh = shadow(m) && a < 0.5f;
            if (sub == null) {
                c.drawText(Ui.font(), label, x + (w - Ui.width(label)) / 2 + 1, y + (h - 8) / 2 + 1, tc, sh);
            } else {
                c.drawText(Ui.font(), label, x + (w - Ui.width(label)) / 2 + 1, y + 2, tc, sh);
                Gfx.push(c);
                Gfx.translate(c, x + w / 2f, y + h - 7);
                Gfx.scale(c, 0.6f, 0.6f);
                c.drawText(Ui.font(), sub, -Ui.width(sub) / 2, 0, tc, false);
                Gfx.pop(c);
            }
        }

        public void draw(DrawContext c, Module m, int x, int y) {
            var o = MinecraftClient.getInstance().options;
            key(c, m, "w", "W", o.forwardKey, x + K + G, y, K, K, null);
            int y2 = y + K + G;
            key(c, m, "a", "A", o.leftKey, x, y2, K, K, null);
            key(c, m, "s", "S", o.backKey, x + K + G, y2, K, K, null);
            key(c, m, "d", "D", o.rightKey, x + 2 * (K + G), y2, K, K, null);
            int y3 = y2 + K + G;
            int full = K * 3 + G * 2;
            if (VortexMods.config.bool(m, "mouse")) {
                int half = (full - G) / 2;
                boolean cps = VortexMods.config.bool(m, "cps");
                key(c, m, "lmb", "LMB", o.attackKey, x, y3, half, K, cps ? Stats.cps(0) + " CPS" : null);
                key(c, m, "rmb", "RMB", o.useKey, x + half + G, y3, full - half - G, K, cps ? Stats.cps(1) + " CPS" : null);
                y3 += K + G;
            }
            if (VortexMods.config.bool(m, "space")) {
                boolean down = o.jumpKey.isPressed();
                float a = Ui.approach(KEY_ANIM.getOrDefault("space", 0f), down ? 1f : 0f, 22f, frameDt);
                KEY_ANIM.put("space", a);
                int bg = VortexMods.config.bool(m, "background") ? 0x78000000 : 0x00000000;
                Ui.round(c, x, y3, full, 8, Ui.mix(bg, 0xE6FFFFFF, a));
                c.fill(x + full / 2 - 9, y3 + 3, x + full / 2 + 9, y3 + 4, Ui.mix(textColor(m), 0xFF101010, a));
            }
        }
    }

    static final class Armor implements Element {
        static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        List<ItemStack> items(Module m) {
            MinecraftClient mc = MinecraftClient.getInstance();
            List<ItemStack> out = new ArrayList<>();
            if (mc.player == null) {
                out.add(new ItemStack(Items.DIAMOND_HELMET));
                out.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
                out.add(new ItemStack(Items.DIAMOND_LEGGINGS));
                out.add(new ItemStack(Items.DIAMOND_BOOTS));
                if (VortexMods.config.bool(m, "held")) out.add(new ItemStack(Items.DIAMOND_SWORD));
                return out;
            }
            for (EquipmentSlot s : SLOTS) {
                ItemStack st = mc.player.getEquippedStack(s);
                if (!st.isEmpty()) out.add(st);
            }
            if (VortexMods.config.bool(m, "held") && !mc.player.getMainHandStack().isEmpty()) out.add(mc.player.getMainHandStack());
            return out;
        }

        String label(Module m, ItemStack st) {
            String mode = VortexMods.config.str(m, "durability");
            if (mode.equals("Hidden")) return "";
            if (!st.isDamageable()) return st.getCount() > 1 ? String.valueOf(st.getCount()) : "";
            int left = st.getMaxDamage() - st.getDamage();
            if (mode.equals("Percent")) return Math.round(left * 100f / st.getMaxDamage()) + "%";
            return String.valueOf(left);
        }

        boolean horizontal(Module m) {
            return VortexMods.config.str(m, "layout").equals("Horizontal");
        }

        public int[] size(Module m) {
            List<ItemStack> it = items(m);
            if (horizontal(m)) {
                int w = 4;
                for (ItemStack st : it) w += 16 + 3 + Ui.width(label(m, st)) + 4;
                return new int[] {w + 2, 22};
            }
            int w = 16;
            for (ItemStack st : it) w = Math.max(w, 16 + 3 + Ui.width(label(m, st)));
            return new int[] {w + 6, Math.max(1, it.size()) * 17 + 5};
        }

        public void draw(DrawContext c, Module m, int x, int y) {
            int[] s = size(m);
            panel(c, m, x, y, s[0], s[1]);
            if (horizontal(m)) {
                int tx = x + 3;
                for (ItemStack st : items(m)) {
                    c.drawItem(st, tx, y + 3);
                    String l = label(m, st);
                    int color = textColor(m);
                    if (st.isDamageable() && st.getMaxDamage() > 0) {
                        float f = (st.getMaxDamage() - st.getDamage()) / (float) st.getMaxDamage();
                        if (f < 0.25f) color = 0xFFFF5555;
                        else if (f < 0.5f) color = 0xFFFFAA00;
                    }
                    c.drawText(Ui.font(), l, tx + 19, y + 7, color, shadow(m));
                    tx += 16 + 3 + Ui.width(l) + 4;
                }
                return;
            }
            int ty = y + 3;
            for (ItemStack st : items(m)) {
                c.drawItem(st, x + 3, ty);
                String l = label(m, st);
                int color = textColor(m);
                if (st.isDamageable()) {
                    float f = (st.getMaxDamage() - st.getDamage()) / (float) st.getMaxDamage();
                    if (f < 0.25f) color = 0xFFFF5555;
                    else if (f < 0.5f) color = 0xFFFFAA00;
                }
                c.drawText(Ui.font(), l, x + 3 + 16 + 3, ty + 4, color, shadow(m));
                ty += 17;
            }
        }
    }

    static final class Potions implements Element {
        List<StatusEffectInstance> effects() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) {
                return List.of(new StatusEffectInstance(StatusEffects.SPEED, 20 * 95, 1),
                        new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 42, 0));
            }
            return new ArrayList<>(mc.player.getStatusEffects());
        }

        String name(StatusEffectInstance e) {
            Text n = Compat.effectName(e);
            String s = n.getString();
            if (e.getAmplifier() > 0) s += " " + Text.translatable("enchantment.level." + (e.getAmplifier() + 1)).getString();
            return s;
        }

        String time(StatusEffectInstance e) {
            return Compat.durationText(e);
        }

        public int[] size(Module m) {
            List<StatusEffectInstance> es = effects();
            boolean icons = VortexMods.config.bool(m, "icons");
            int w = 40;
            for (StatusEffectInstance e : es) w = Math.max(w, Math.max(Ui.width(name(e)), Ui.width(time(e))) + (icons ? 22 : 0));
            return new int[] {w + 8, Math.max(1, es.size()) * 22 + 4};
        }

        public void draw(DrawContext c, Module m, int x, int y) {
            int[] s = size(m);
            List<StatusEffectInstance> es = effects();
            if (es.isEmpty()) return;
            panel(c, m, x, y, s[0], s[1]);
            boolean icons = VortexMods.config.bool(m, "icons");
            int ty = y + 4;
            for (StatusEffectInstance e : es) {
                int tx = x + 4;
                if (icons) {
                    Gfx.effectIcon(c, e, tx, ty + 1, 18);
                    tx += 22;
                }
                int nameColor = VortexMods.config.bool(m, "kind") ? (Compat.beneficial(e) ? 0xFF66EE77 : 0xFFFF6666) : textColor(m);
                int timeColor = 0xFFAAAAAA;
                // Ending soon: the time blinks.
                if (VortexMods.config.bool(m, "blink") && e.getDuration() > 0 && e.getDuration() < 200 && (System.currentTimeMillis() / 350) % 2 == 0) timeColor = 0xFFFF5555;
                c.drawText(Ui.font(), name(e), tx, ty + 1, nameColor, shadow(m));
                c.drawText(Ui.font(), time(e), tx, ty + 11, timeColor, shadow(m));
                ty += 22;
            }
        }
    }

    // -------------------------------------------------------------- layout --

    public static boolean has(Module m) {
        return ELEMENTS.containsKey(m.id);
    }

    public static int[] size(Module m) {
        Element e = ELEMENTS.get(m.id);
        return e == null ? new int[] {0, 0} : e.size(m);
    }

    public static void drawAt(DrawContext c, Module m, int x, int y, float scale) {
        Element e = ELEMENTS.get(m.id);
        if (e == null) return;
        Gfx.push(c);
        Gfx.translate(c, x, y);
        Gfx.scale(c, scale, scale);
        e.draw(c, m, 0, 0);
        Gfx.pop(c);
    }

    /** Where every enabled HUD element goes on a screen of this size. */
    private static final boolean MINIMAP = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("xaerominimap")
            || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("xaerominimapfair")
            || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("journeymap");

    /** Whether the next left-hand element still fits on the left third. */
    private static boolean fitsLeft(int leftX, int colW, int leftY, int leftTop, int leftBottom, int w, int h, int margin, int sw) {
        int x = leftY + h > leftBottom && leftY > leftTop ? leftX + colW + 4 : leftX;
        return x <= margin || x + w <= sw * 0.36f;
    }

    public static Map<Module, Rect> layout(int sw, int sh) {
        Map<Module, Rect> out = new LinkedHashMap<>();
        // A minimap mod sits in the top-left corner: start the left column
        // under it (elements you placed yourself stay where you put them).
        int margin = styled() ? (int) VortexMods.config.num(style(), "margin") : 4;
        int gapY = styled() ? (int) VortexMods.config.num(style(), "spacing") : 3;
        float global = styled() ? (float) VortexMods.config.num(style(), "globalscale") : 1f;
        int leftTop = MINIMAP ? Math.max(margin, Math.round(sh * 0.40f)) : margin;
        int leftY = leftTop, rightY = Math.max(30, sh / 4);
        // The left column wraps into a second (and third) one when it would
        // run past the hotbar, instead of piling up off the screen.
        int leftX = margin, colW = 0, leftBottom = sh - 30;
        // The right-hand stack wraps leftwards too, above the armor corner.
        int rightTop = rightY, rightX = sw - margin, rightColW = 0, rightBottom = sh - 70;

        List<Module> order = new ArrayList<>();
        order.add(Module.get("music"));
        for (List<String> ids : List.of(RIGHT, BOTTOM_LEFT, LEFT)) {
            for (String id : ids) {
                Module m = Module.find(id);
                if (m != null) order.add(m);
            }
        }
        for (Module m : Module.ALL) if (!order.contains(m)) order.add(m);
        for (Module m : order) {
            if (!m.hud || !has(m) || !VortexMods.config.enabled(m)) continue;
            int[] s = size(m);
            // Nothing to show right now (no target, not burning...): no empty box.
            if (s[0] <= 0 || s[1] <= 5) continue;
            float scale = (float) VortexMods.config.num(m, "scale") * global;
            int w = Math.round(s[0] * scale), h = Math.round(s[1] * scale);
            Config.ModState st = VortexMods.config.state(m);
            int x, y;
            if (st.x >= 0 && st.y >= 0) {
                x = (int) Math.round(st.x * sw - w / 2.0);
                y = (int) Math.round(st.y * sh - h / 2.0);
            } else if (LEFT.contains(m.id) && fitsLeft(leftX, colW, leftY, leftTop, leftBottom, w, h, margin, sw)) {
                if (leftY + h > leftBottom && leftY > leftTop) {
                    leftX += colW + 4;
                    // Later columns start under the compass strip at the top.
                    leftY = Math.max(leftTop, 22);
                    colW = 0;
                }
                x = leftX;
                y = leftY;
                leftY += h + gapY;
                colW = Math.max(colW, w);
            } else if (RIGHT.contains(m.id) || BOTTOM_LEFT.contains(m.id) || LEFT.contains(m.id)) {
                // The right-hand stack, and anything the left side has no room for.
                if (rightY + h > rightBottom && rightY > rightTop) {
                    rightX -= rightColW + 4;
                    rightY = rightTop;
                    rightColW = 0;
                }
                x = rightX - w;
                y = rightY;
                rightY += h + gapY;
                rightColW = Math.max(rightColW, w);
            } else if (m.id.equals("music")) {
                // Top-right corner; the right-hand stack starts under it.
                x = sw - w - margin;
                y = margin;
                rightY = Math.max(rightY, y + h + gapY);
                rightTop = rightY;
            } else if (m.id.equals("invhud")) {
                // Centred above the hotbar, health and hunger.
                x = (sw - w) / 2;
                y = sh - h - 62;
            } else if (m.id.equals("direction")) {
                x = (sw - w) / 2;
                y = 3;
            } else if (m.id.equals("target")) {
                x = sw / 2 + 14;
                y = sh / 2 + 10;
            } else if (m.id.equals("items")) {
                // Bottom-right, just left of the armor list.
                Rect armor = out.get(Module.get("armor"));
                x = (armor != null ? armor.x() : sw) - w - 4;
                y = sh - h - 4;
            } else {
                // Armor: bottom-right corner, clear of the hotbar and chat.
                x = sw - w - 4;
                y = sh - h - 4;
            }
            x = Math.max(0, Math.min(sw - w, x));
            y = Math.max(0, Math.min(sh - h, y));
            out.put(m, new Rect(x, y, w, h, scale));
        }
        return out;
    }

    public static void render(DrawContext c) {
        long now = System.nanoTime();
        frameDt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || Compat.debugShown()) return;
        if (mc.currentScreen instanceof HudEditorScreen) return;
        Module client = Module.find("client");
        if (client != null && mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)
                && VortexMods.config.bool(client, "hudscreens")) return;
        int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
        HudPlus.overlays(c, mc, sw, sh);
        Rec.draw(c, sw);
        if (VortexMods.hudOff) return;
        if (styled() && VortexMods.config.bool(style(), "hidechat") && mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) return;
        if (VortexMods.zoomHidesHud()) return;
        for (Map.Entry<Module, Rect> e : layout(sw, sh).entrySet()) {
            Rect r = e.getValue();
            drawAt(c, e.getKey(), r.x(), r.y(), r.scale());
        }
    }

    /** Frame timing for previews drawn outside the HUD pass. */
    public static void tickFrame() {
        long now = System.nanoTime();
        frameDt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
    }
}
