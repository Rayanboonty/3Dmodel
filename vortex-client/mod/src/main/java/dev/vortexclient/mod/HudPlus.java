package dev.vortexclient.mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The HUD elements and screen overlays added in 0.9. */
final class HudPlus {
    private HudPlus() {}

    static String dimension(MinecraftClient mc) {
        if (mc.world == null) return "Overworld";
        var key = mc.world.getRegistryKey();
        if (key == World.NETHER) return "Nether";
        if (key == World.END) return "The End";
        if (key == World.OVERWORLD) return "Overworld";
        return Hud.cap(key.getValue().getPath().replace('_', ' '));
    }

    /** How many of these items you carry. */
    static int count(MinecraftClient mc, Item... items) {
        if (mc.player == null) return 32;
        int n = 0;
        var inv = mc.player.getInventory();
        for (int s = 0; s < inv.size(); s++) {
            ItemStack st = inv.getStack(s);
            for (Item it : items) if (st.isOf(it)) n += st.getCount();
        }
        return n;
    }

    private static String secs(double s) {
        return String.format(Locale.ROOT, "%.1fs", Math.max(0, s));
    }

    static void register(MinecraftClient mc) {
        Hud.ELEMENTS.put("armorpts", new Bar() {
            String label(Module m) { return "Armor "; }
            String value(Module m) { return String.valueOf(mc.player != null ? mc.player.getArmor() : 15); }
            float fraction(Module m) { return (mc.player != null ? mc.player.getArmor() : 15) / 20f; }
            int color(Module m) { return 0xFF8FB4FF; }
            boolean bar(Module m) { return VortexMods.config.bool(m, "bar"); }
        });
        Hud.ELEMENTS.put("air", Hud.lines(m -> {
            if (mc.player == null) return List.<String[]>of(new String[] {"Air ", "11.5s", "FF66CCFF"});
            int air = mc.player.getAir(), max = mc.player.getMaxAir();
            if (air >= max) return List.<String[]>of();
            return List.<String[]>of(new String[] {"Air ", secs(air / 20.0), air < max / 5 ? "FFFF5555" : "FF66CCFF"});
        }));
        Hud.ELEMENTS.put("fire", Hud.lines(m -> {
            if (mc.player == null) return List.<String[]>of(new String[] {"Burning ", "3.5s", "FFFF8833"});
            if (!mc.player.isOnFire()) return List.<String[]>of();
            boolean safe = mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
            return List.<String[]>of(new String[] {"Burning ", secs(mc.player.getFireTicks() / 20.0) + (safe ? " (safe)" : ""), safe ? "FFFFAA55" : "FFFF5533"});
        }));
        Hud.ELEMENTS.put("elytra", Hud.lines(m -> {
            ItemStack st = mc.player != null ? mc.player.getEquippedStack(EquipmentSlot.CHEST) : new ItemStack(Items.ELYTRA);
            if (!st.isOf(Items.ELYTRA)) return List.<String[]>of();
            int left = st.getMaxDamage() - st.getDamage();
            float f = st.getMaxDamage() > 0 ? left / (float) st.getMaxDamage() : 1f;
            List<String[]> out = new ArrayList<>();
            out.add(new String[] {"Elytra ", Math.round(f * 100) + "%", f < 0.15f ? "FFFF5555" : f < 0.4f ? "FFFFAA00" : "FFFFFFFF"});
            boolean air = mc.player == null || !mc.player.isOnGround();
            if (VortexMods.config.bool(m, "speed") && air) out.add(new String[] {"Speed ", String.format(Locale.ROOT, "%.1f m/s", mc.player == null ? 28.4 : Math.sqrt(Stats.speed * Stats.speed + Stats.vspeed * Stats.vspeed))});
            return out;
        }));
        Hud.ELEMENTS.put("mount", Hud.lines(m -> {
            if (mc.player == null) return List.<String[]>of(new String[] {"Mount ", "22/30 ❤"});
            if (!(mc.player.getVehicle() instanceof LivingEntity v)) return List.<String[]>of();
            return List.<String[]>of(new String[] {Hud.cap(v.getName().getString()) + " ", String.format(Locale.ROOT, "%.0f/%.0f ❤", v.getHealth(), v.getMaxHealth())});
        }));
        Hud.ELEMENTS.put("lookat", Hud.lines(m -> {
            if (mc.player == null || mc.world == null) return List.<String[]>of(new String[] {"", "Oak Log (4.2m)"});
            HitResult hit = mc.crosshairTarget;
            if (hit == null || hit.getType() == HitResult.Type.MISS) return List.<String[]>of();
            String name;
            String pos = "";
            if (hit instanceof EntityHitResult eh) {
                Entity e = eh.getEntity();
                name = e.getName().getString();
                if (e instanceof LivingEntity le) name += String.format(Locale.ROOT, " %.0f❤", le.getHealth());
                pos = e.getBlockPos().getX() + ", " + e.getBlockPos().getY() + ", " + e.getBlockPos().getZ();
            } else if (hit instanceof BlockHitResult bh) {
                BlockPos p = bh.getBlockPos();
                name = mc.world.getBlockState(p).getBlock().getName().getString();
                pos = p.getX() + ", " + p.getY() + ", " + p.getZ();
            } else return List.<String[]>of();
            double d = hit.getPos().distanceTo(mc.player.getEyePos());
            String v = name + (VortexMods.config.bool(m, "dist") ? String.format(Locale.ROOT, " (%.1fm)", d) : "");
            if (VortexMods.config.bool(m, "pos")) return List.<String[]>of(new String[] {"", v}, new String[] {"At ", pos});
            return List.<String[]>of(new String[] {"", v});
        }));
        Hud.ELEMENTS.put("kills", Hud.lines(m -> {
            int k = mc.player == null ? 7 : Stats.kills, d = mc.player == null ? 2 : Stats.deaths;
            if (!VortexMods.config.bool(m, "kdr")) return List.<String[]>of(new String[] {"Kills ", String.valueOf(k)});
            return List.<String[]>of(new String[] {"Kills ", k + String.format(Locale.ROOT, "  K/D %.2f", d == 0 ? (double) k : k / (double) d)});
        }));
        Hud.ELEMENTS.put("deaths", Hud.lines(m -> List.<String[]>of(new String[] {"Deaths ", String.valueOf(mc.player == null ? 2 : Stats.deaths)})));
        Hud.ELEMENTS.put("distance", Hud.lines(m -> {
            double d = mc.player == null ? 1830 : Stats.distance;
            return List.<String[]>of(new String[] {"Travelled ", d >= 1000 ? String.format(Locale.ROOT, "%.2f km", d / 1000) : String.format(Locale.ROOT, "%.0f m", d)});
        }));
        Hud.ELEMENTS.put("fpsgraph", new Graph(true));
        Hud.ELEMENTS.put("pinggraph", new Graph(false));
        Hud.ELEMENTS.put("nearby", Hud.lines(m -> {
            List<String[]> out = new ArrayList<>();
            if (mc.player == null || mc.world == null) {
                out.add(new String[] {"Alex ", "8m"});
                out.add(new String[] {"Notch ", "21m"});
                return out;
            }
            double range = VortexMods.config.num(m, "range");
            List<PlayerEntity> ps = new ArrayList<>();
            for (PlayerEntity p : mc.world.getPlayers()) if (p != mc.player && mc.player.distanceTo(p) <= range) ps.add(p);
            ps.sort(Comparator.comparingDouble(p -> mc.player.distanceTo(p)));
            int max = (int) VortexMods.config.num(m, "count");
            for (int i = 0; i < Math.min(max, ps.size()); i++) {
                PlayerEntity p = ps.get(i);
                float d = mc.player.distanceTo(p);
                out.add(new String[] {p.getName().getString() + " ", String.format(Locale.ROOT, "%.0fm", d), d < 8 ? "FFFF7755" : "FFFFFFFF"});
            }
            return out;
        }));
        Hud.ELEMENTS.put("invhud", new Inventory());
        Hud.ELEMENTS.put("helditem", Hud.lines(m -> {
            ItemStack st = mc.player != null ? mc.player.getMainHandStack() : new ItemStack(Items.GOLDEN_APPLE, 12);
            if (st.isEmpty()) return List.<String[]>of();
            List<String[]> out = new ArrayList<>();
            String v = st.getName().getString() + (st.getCount() > 1 ? " x" + st.getCount() : "");
            out.add(new String[] {"", v});
            if (st.isDamageable() && st.getMaxDamage() > 0) {
                int left = st.getMaxDamage() - st.getDamage();
                out.add(new String[] {"Durability ", left + "/" + st.getMaxDamage()});
            } else if (VortexMods.config.bool(m, "total") && mc.player != null) {
                out.add(new String[] {"In total ", String.valueOf(count(mc, st.getItem()))});
            }
            return out;
        }));
        Hud.ELEMENTS.put("difficulty", Hud.lines(m -> List.<String[]>of(new String[] {"Difficulty ", mc.world == null ? "Normal" : Hud.cap(mc.world.getDifficulty().name().toLowerCase(Locale.ROOT))})));
        Hud.ELEMENTS.put("gamemode", Hud.lines(m -> {
            String g = mc.interactionManager != null && mc.interactionManager.getCurrentGameMode() != null
                    ? Hud.cap(mc.interactionManager.getCurrentGameMode().name().toLowerCase(Locale.ROOT)) : "Survival";
            return List.<String[]>of(new String[] {"Mode ", g});
        }));
        Hud.ELEMENTS.put("dimension", Hud.lines(m -> {
            String d = dimension(mc);
            String col = d.equals("Nether") ? "FFFF6655" : d.equals("The End") ? "FFD9B3FF" : "FF88DD77";
            return List.<String[]>of(new String[] {"", d, col});
        }));
        Hud.ELEMENTS.put("afk", Hud.lines(m -> {
            long s = mc.player == null ? 95 : (System.currentTimeMillis() - Stats.lastActive) / 1000;
            if (mc.player != null && s < VortexMods.config.num(m, "after")) return List.<String[]>of();
            return List.<String[]>of(new String[] {"AFK ", s >= 60 ? String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60) : s + "s", "FFFFAA00"});
        }));
        Hud.ELEMENTS.put("hunger", Hud.lines(m -> {
            int food = mc.player != null ? mc.player.getHungerManager().getFoodLevel() : 6;
            if (mc.player != null && (food > VortexMods.config.num(m, "below") || mc.player.isCreative())) return List.<String[]>of();
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            return List.<String[]>of(new String[] {"Eat! ", "Hunger " + food + "/20", blink ? "FFFF5555" : "FFFFAA00"});
        }));
        Hud.ELEMENTS.put("attack", new Bar() {
            float k() { return mc.player != null ? mc.player.getAttackCooldownProgress(0f) : 0.7f; }
            String label(Module m) { return "Attack "; }
            String value(Module m) { return Math.round(k() * 100) + "%"; }
            float fraction(Module m) { return k(); }
            int color(Module m) { return k() >= 1f ? 0xFF55FF55 : 0xFFFFDD55; }
            boolean bar(Module m) { return true; }
            boolean hidden(Module m) { return mc.player != null && VortexMods.config.bool(m, "hidefull") && k() >= 1f; }
        });
        Hud.ELEMENTS.put("velocity", Hud.lines(m -> {
            double v = mc.player == null ? -3.9 : Stats.vspeed;
            String arrow = v > 0.05 ? "▲ " : v < -0.05 ? "▼ " : "";
            return List.<String[]>of(new String[] {"Vertical ", arrow + String.format(Locale.ROOT, "%.1f m/s", Math.abs(v))});
        }));
        Hud.ELEMENTS.put("version", Hud.lines(m -> {
            String v = "Minecraft " + Compat.gameVersionName();
            if (!VortexMods.config.bool(m, "mods")) return List.<String[]>of(new String[] {"", v});
            return List.<String[]>of(new String[] {"", v}, new String[] {"Mods ", String.valueOf(net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().size())});
        }));
    }

    /** A label, a value and an optional bar under them. */
    abstract static class Bar implements Hud.Element {
        abstract String label(Module m);
        abstract String value(Module m);
        abstract float fraction(Module m);
        abstract int color(Module m);
        abstract boolean bar(Module m);
        boolean hidden(Module m) { return false; }

        public int[] size(Module m) {
            if (hidden(m)) return new int[] {0, 0};
            int w = Math.max(70, Hud.tw(Hud.lab(m, label(m))) + Hud.tw(Hud.tc(value(m))) + 8);
            return new int[] {w, bar(m) ? 20 : 15};
        }

        public void draw(DrawContext c, Module m, int x, int y) {
            if (hidden(m)) return;
            int[] s = size(m);
            Hud.panel(c, m, x, y, s[0], s[1]);
            Hud.line(c, m, label(m), value(m), x + 4, y + 4, Hud.textColor(m));
            if (bar(m)) {
                float f = Math.max(0, Math.min(1, fraction(m)));
                Ui.round(c, x + 4, y + 14, s[0] - 8, 3, 0x66000000);
                Ui.round(c, x + 4, y + 14, Math.max(2, Math.round((s[0] - 8) * f)), 3, color(m));
            }
        }
    }

    /** FPS or ping over time, as bars. */
    static final class Graph implements Hud.Element {
        final boolean fps;

        Graph(boolean fps) {
            this.fps = fps;
        }

        int[] data() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) {
                int[] d = new int[fps ? 80 : 60];
                for (int i = 0; i < d.length; i++) d[i] = fps ? 90 + (int) (30 * Math.sin(i / 6.0)) + (i % 13 == 0 ? -40 : 0) : 40 + (int) (12 * Math.sin(i / 4.0));
                return d;
            }
            return fps ? Stats.history(Stats.FPS_HISTORY, Stats.fpsCount) : Stats.history(Stats.PING_HISTORY, Stats.pingCount);
        }

        boolean stats(Module m) {
            return !fps || VortexMods.config.bool(m, "stats");
        }

        public int[] size(Module m) {
            return new int[] {(fps ? 80 : 60) + 8, 34 + (stats(m) ? 10 : 0)};
        }

        public void draw(DrawContext c, Module m, int x, int y) {
            int[] s = size(m);
            Hud.panel(c, m, x, y, s[0], s[1]);
            int[] d = data();
            int max = 1, min = Integer.MAX_VALUE;
            long sum = 0;
            for (int v : d) { max = Math.max(max, v); min = Math.min(min, v); sum += v; }
            if (d.length == 0) min = 0;
            int gx = x + 4, gy = y + 4, gh = 26;
            c.fill(gx, gy + gh, gx + (fps ? 80 : 60), gy + gh + 1, 0x40FFFFFF);
            for (int i = 0; i < d.length; i++) {
                int h = Math.max(1, Math.round(gh * d[i] / (float) max));
                int col;
                if (fps) col = d[i] >= 60 ? 0xFF55DD66 : d[i] >= 30 ? 0xFFFFDD55 : 0xFFFF5555;
                else col = d[i] < 80 ? 0xFF55DD66 : d[i] < 150 ? 0xFFFFDD55 : 0xFFFF5555;
                c.fill(gx + i, gy + gh - h, gx + i + 1, gy + gh, col);
            }
            String title = fps ? "FPS" : "Ping";
            Hud.str(c, Hud.tc(title), gx + 1, gy, Hud.labelColor(m), Hud.shadow(m));
            if (stats(m)) {
                int avg = d.length == 0 ? 0 : (int) (sum / d.length);
                String t = fps ? String.format(Locale.ROOT, "%d / %d / %d", min, avg, max) : String.format(Locale.ROOT, "%d ms avg", avg);
                Hud.str(c, Hud.tc(t), gx, gy + gh + 3, Hud.textColor(m), Hud.shadow(m));
            }
        }
    }

    /** The main inventory (and hotbar) as a grid. */
    static final class Inventory implements Hud.Element {
        public int[] size(Module m) {
            int rows = VortexMods.config.bool(m, "hotbar") ? 4 : 3;
            return new int[] {9 * 18 + 6, rows * 18 + 6};
        }

        public void draw(DrawContext c, Module m, int x, int y) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int[] s = size(m);
            Hud.panel(c, m, x, y, s[0], s[1]);
            boolean hotbar = VortexMods.config.bool(m, "hotbar");
            int rows = hotbar ? 4 : 3;
            for (int r = 0; r < rows; r++) {
                for (int col = 0; col < 9; col++) {
                    int sx = x + 3 + col * 18, sy = y + 3 + r * 18;
                    c.fill(sx, sy, sx + 17, sy + 17, 0x30FFFFFF);
                    int slot = r < 3 ? 9 + r * 9 + col : col;
                    ItemStack st;
                    if (mc.player == null) st = (slot * 7) % 5 == 0 ? new ItemStack(slot % 2 == 0 ? Items.COBBLESTONE : Items.TORCH, 1 + slot % 40) : ItemStack.EMPTY;
                    else st = mc.player.getInventory().getStack(slot);
                    if (st.isEmpty()) continue;
                    c.drawItem(st, sx, sy);
                    Gfx.itemOverlay(c, st, sx, sy);
                }
            }
        }
    }

    // ------------------------------------------------------------ overlays --

    /** Low Health Warning and Hit Marker, drawn over the game. */
    static void overlays(DrawContext c, MinecraftClient mc, int sw, int sh) {
        if (mc.player == null) return;
        Module low = Module.get("lowhp");
        if (VortexMods.config.enabled(low) && !mc.player.isCreative() && !mc.player.isSpectator()) {
            float hp = mc.player.getHealth();
            float limit = (float) VortexMods.config.num(low, "below") * 2f;
            if (hp > 0 && hp <= limit) {
                float k = 1f - hp / Math.max(1f, limit);
                float pulse = VortexMods.config.bool(low, "pulse") ? 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 180.0) : 1f;
                int a = Math.round((0.25f + 0.45f * k) * pulse * 255);
                int edge = (a << 24) | 0xD01818;
                int depth = Math.max(20, sh / 6);
                c.fillGradient(0, 0, sw, depth, edge, 0x00D01818);
                c.fillGradient(0, sh - depth, sw, sh, 0x00D01818, edge);
                int steps = 16;
                for (int i = 0; i < steps; i++) {
                    int alpha = Math.round(a * (1f - i / (float) steps));
                    int col = (alpha << 24) | 0xD01818;
                    int wd = depth / steps + 1;
                    c.fill(i * wd, 0, i * wd + wd, sh, col & 0x00FFFFFF | ((alpha / 3) << 24));
                    c.fill(sw - i * wd - wd, 0, sw - i * wd, sh, col & 0x00FFFFFF | ((alpha / 3) << 24));
                }
            }
        }
        Module hm = Module.get("hitmarker");
        long since = System.currentTimeMillis() - Stats.lastHitAt;
        if (VortexMods.config.enabled(hm) && since < 260 && mc.options.getPerspective().isFirstPerson()) {
            boolean kill = VortexMods.config.bool(hm, "killred") && System.currentTimeMillis() - Stats.lastKillAt < 400;
            float fade = 1f - since / 260f;
            int col = Ui.alpha(kill ? 0xFFFF4040 : 0xFFFFFFFF, fade);
            int cx = sw / 2, cy = sh / 2, n = (int) VortexMods.config.num(hm, "size");
            for (int i = 2; i < 2 + n; i++) {
                c.fill(cx - i - 1, cy - i - 1, cx - i, cy - i, col);
                c.fill(cx + i, cy - i - 1, cx + i + 1, cy - i, col);
                c.fill(cx - i - 1, cy + i, cx - i, cy + i + 1, col);
                c.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, col);
            }
        }
    }
}
