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

    private static final Map<String, Element> ELEMENTS = new LinkedHashMap<>();
    /** Elements that stack down the left edge by default, in order. */
    private static final List<String> LEFT = List.of("fps", "cps", "ping", "memory", "server", "speed", "reach", "combo", "clock", "day", "saturation", "session", "pack", "coords", "sprint");
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
            default -> 0xFFFFFFFF;
        };
    }

    static boolean shadow(Module m) {
        return VortexMods.config.bool(m, "shadow");
    }

    static void panel(DrawContext c, Module m, int x, int y, int w, int h) {
        // Rounded glass, like the launcher's panels.
        if (VortexMods.config.bool(m, "background")) {
            int r = Math.min(4, Math.min(w, h) / 3);
            Ui.rounded(c, x, y, w, h, r, 0x70000000);
            c.fill(x + r, y, x + w - r, y + 1, 0x30FFFFFF);
        }
    }

    static void line(DrawContext c, Module m, String label, String value, int x, int y) {
        int color = textColor(m);
        int lw = Ui.width(label);
        c.drawText(Ui.font(), label, x, y, VortexMods.accent(), shadow(m));
        c.drawText(Ui.font(), value, x + lw, y, color, shadow(m));
    }

    /** A simple element made of label/value lines. */
    static Element lines(java.util.function.Function<Module, List<String[]>> content) {
        return new Element() {
            public int[] size(Module m) {
                List<String[]> ls = content.apply(m);
                int w = 0;
                for (String[] l : ls) w = Math.max(w, Ui.width(l[0] + l[1]));
                return new int[] {w + 8, ls.size() * 10 + 5};
            }

            public void draw(DrawContext c, Module m, int x, int y) {
                List<String[]> ls = content.apply(m);
                int[] s = size(m);
                panel(c, m, x, y, s[0], s[1]);
                int ty = y + 4;
                for (String[] l : ls) {
                    line(c, m, l[0], l[1], x + 4, ty);
                    ty += 10;
                }
            }
        };
    }

    static {
        MinecraftClient mc = MinecraftClient.getInstance();
        ELEMENTS.put("fps", lines(m -> List.<String[]>of(new String[] {"", mc.getCurrentFps() + " FPS"})));
        ELEMENTS.put("cps", lines(m -> {
            String v = VortexMods.config.bool(m, "right") ? Stats.cps(0) + " | " + Stats.cps(1) : String.valueOf(Stats.cps(0));
            return List.<String[]>of(new String[] {"", v + " CPS"});
        }));
        ELEMENTS.put("ping", lines(m -> List.<String[]>of(new String[] {"", ping(mc) + " ms"})));
        ELEMENTS.put("speed", lines(m -> {
            boolean kmh = VortexMods.config.str(m, "unit").equals("km/h");
            double v = kmh ? Stats.speed * 3.6 : Stats.speed;
            return List.<String[]>of(new String[] {"", String.format(Locale.ROOT, "%.2f %s", v, kmh ? "km/h" : "m/s")});
        }));
        ELEMENTS.put("reach", lines(m -> {
            boolean recent = Stats.lastReach >= 0 && System.currentTimeMillis() - Stats.lastReachAt < 3000;
            String v = recent ? String.format(Locale.ROOT, "%.2f blocks", Stats.lastReach) : (mc.player == null ? "3.00 blocks" : "Hit something");
            return List.<String[]>of(new String[] {"", v});
        }));
        ELEMENTS.put("combo", lines(m -> {
            int n = mc.player == null ? 3 : Stats.combo;
            return List.<String[]>of(new String[] {"", n == 0 ? "No combo" : n + " combo"});
        }));
        ELEMENTS.put("clock", lines(m -> {
            String f = VortexMods.config.bool(m, "h24") ? "HH:mm" : "h:mm a";
            return List.<String[]>of(new String[] {"", LocalTime.now().format(DateTimeFormatter.ofPattern(f, Locale.ROOT))});
        }));
        ELEMENTS.put("coords", lines(m -> {
            List<String[]> out = new ArrayList<>();
            BlockPos p = mc.player != null ? mc.player.getBlockPos() : new BlockPos(128, 64, -340);
            out.add(new String[] {"X ", String.valueOf(p.getX())});
            out.add(new String[] {"Y ", String.valueOf(p.getY())});
            out.add(new String[] {"Z ", String.valueOf(p.getZ())});
            if (VortexMods.config.bool(m, "facing")) {
                String dir = mc.player != null ? cap(mc.player.getHorizontalFacing().asString()) : "North";
                out.add(new String[] {"F ", dir});
            }
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
            return List.<String[]>of(new String[] {"Mem ", String.format(Locale.ROOT, "%d%% %d/%d MB", used * 100 / Math.max(1, max), used, max)});
        }));
        ELEMENTS.put("server", lines(m -> {
            String s = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : (mc.isInSingleplayer() || mc.player == null ? "Singleplayer" : "LAN");
            return List.<String[]>of(new String[] {"", s});
        }));
        ELEMENTS.put("day", lines(m -> {
            long d = mc.world != null ? mc.world.getTimeOfDay() / 24000L : 12;
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
        ELEMENTS.put("target", new Target());
        ELEMENTS.put("items", new ItemCounter());
    }

    /** A compass strip that slides as you turn. */
    static final class Direction implements Element {
        static final String[] MARKS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

        public int[] size(Module m) {
            return new int[] {150, 16};
        }

        public void draw(DrawContext c, Module m, int x, int y) {
            MinecraftClient mc = MinecraftClient.getInstance();
            float yaw = mc.player != null ? mc.player.getYaw() : 180f;
            yaw = ((yaw % 360) + 360) % 360;
            panel(c, m, x, y, 150, 16);
            c.enableScissor(x + 2, y, x + 148, y + 16);
            float pxPerDeg = 1.2f;
            for (int i = -2; i < 10; i++) {
                float deg = i * 45f;
                float rel = deg - yaw;
                while (rel < -180) rel += 360;
                while (rel > 180) rel -= 360;
                int px = x + 75 + Math.round(rel * pxPerDeg);
                if (px < x - 20 || px > x + 170) continue;
                String s = MARKS[((i % 8) + 8) % 8];
                int col = s.length() == 1 ? (s.equals("N") ? 0xFFFF5555 : textColor(m)) : 0xFFAAAAAA;
                c.drawText(Ui.font(), s, px - Ui.width(s) / 2, y + 4, col, shadow(m));
            }
            for (int k = -60; k <= 60; k += 15) {
                float rel = (float) ((Math.round(yaw / 15f) * 15 + k) - yaw);
                int px = x + 75 + Math.round(rel * pxPerDeg);
                if (px > x + 2 && px < x + 148) c.fill(px, y + 13, px + 1, y + 15, 0x66FFFFFF);
            }
            c.disableScissor();
            c.fill(x + 74, y, x + 76, y + 3, VortexMods.accent());
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
            return new int[] {120, 32};
        }

        public void draw(DrawContext c, Module m, int x, int y) {
            MinecraftClient mc = MinecraftClient.getInstance();
            net.minecraft.entity.LivingEntity t = target();
            String name = t != null ? t.getName().getString() : "Steve";
            float hp = t != null ? t.getHealth() : 14f, max = t != null ? t.getMaxHealth() : 20f;
            double dist = t != null && mc.player != null ? mc.player.distanceTo(t) : 3.1;
            panel(c, m, x, y, 120, 32);
            if (name.length() > 16) name = name.substring(0, 15) + "…";
            c.drawText(Ui.font(), name, x + 5, y + 4, textColor(m), shadow(m));
            String d = String.format(Locale.ROOT, "%.1fm", dist);
            c.drawText(Ui.font(), d, x + 115 - Ui.width(d), y + 4, 0xFFAAAAAA, shadow(m));
            float f = Math.max(0, Math.min(1, hp / Math.max(1, max)));
            int col = f > 0.6f ? 0xFF55DD55 : f > 0.3f ? 0xFFFFAA33 : 0xFFFF5555;
            Ui.round(c, x + 5, y + 16, 110, 5, 0x66000000);
            Ui.round(c, x + 5, y + 16, Math.max(2, Math.round(110 * f)), 5, col);
            String h = String.format(Locale.ROOT, "%.1f / %.0f ❤", hp, max);
            Gfx.push(c);
            Gfx.translate(c, x + 5, y + 23);
            Gfx.scale(c, 0.7f, 0.7f);
            c.drawText(Ui.font(), h, 0, 0, 0xFFDDDDDD, false);
            Gfx.pop(c);
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
                if (n > 0) out.add(new int[] {i, n});
            }
            return out;
        }

        public int[] size(Module m) {
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
                c.drawText(Ui.font(), String.valueOf(e[1]), x + 22, ty + 4, textColor(m), shadow(m));
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

        public int[] size(Module m) {
            List<ItemStack> it = items(m);
            int w = 16;
            for (ItemStack st : it) w = Math.max(w, 16 + 3 + Ui.width(label(m, st)));
            return new int[] {w + 6, Math.max(1, it.size()) * 17 + 5};
        }

        public void draw(DrawContext c, Module m, int x, int y) {
            int[] s = size(m);
            panel(c, m, x, y, s[0], s[1]);
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
                c.drawText(Ui.font(), name(e), tx, ty + 1, textColor(m), shadow(m));
                c.drawText(Ui.font(), time(e), tx, ty + 11, 0xFFAAAAAA, shadow(m));
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

    public static Map<Module, Rect> layout(int sw, int sh) {
        Map<Module, Rect> out = new LinkedHashMap<>();
        // A minimap mod sits in the top-left corner: start the left column
        // under it (elements you placed yourself stay where you put them).
        int leftY = MINIMAP ? Math.max(4, Math.round(sh * 0.40f)) : 4, rightY = Math.max(30, sh / 4);
        List<Module> order = new ArrayList<>();
        for (String id : LEFT) order.add(Module.get(id));
        for (String id : RIGHT) order.add(Module.get(id));
        for (Module m : Module.ALL) if (!order.contains(m)) order.add(m);
        for (Module m : order) {
            if (!m.hud || !has(m) || !VortexMods.config.enabled(m)) continue;
            int[] s = size(m);
            if (s[0] <= 0 || s[1] <= 0 || (m.id.equals("sprint") && s[1] <= 5)) continue;
            float scale = (float) VortexMods.config.num(m, "scale");
            int w = Math.round(s[0] * scale), h = Math.round(s[1] * scale);
            Config.ModState st = VortexMods.config.state(m);
            int x, y;
            if (st.x >= 0 && st.y >= 0) {
                x = (int) Math.round(st.x * sw - w / 2.0);
                y = (int) Math.round(st.y * sh - h / 2.0);
            } else if (LEFT.contains(m.id)) {
                x = 4;
                y = leftY;
                leftY += h + 3;
            } else if (RIGHT.contains(m.id)) {
                x = sw - w - 4;
                y = rightY;
                rightY += h + 3;
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
        int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
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
