package net.cmcpvp.fx;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import net.cmcpvp.CMCPvP;
import net.cmcpvp.config.Cfg;
import net.cmcpvp.config.Opt;
import net.cmcpvp.data.PvPProfile;
import net.cmcpvp.data.Setting;
import net.cmcpvp.kit.Kit;
import net.cmcpvp.rank.Tier;
import net.cmcpvp.util.Snd;
import net.cmcpvp.util.Tasks;
import net.cmcpvp.util.Text;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;

public final class Fx {
    public static NamespacedKey FX_FIREWORK;
    public static final Fx.Palette GOLD = new Fx.Palette(16755200, 16774864, 4139520, 11565056);
    public static final Fx.Palette GREEN = new Fx.Palette(5635925, 15728623, 997903, 3054126);
    public static final Fx.Palette RED = new Fx.Palette(16733525, 16770790, 3803658, 10102053);
    public static final Fx.Palette YELLOW = new Fx.Palette(16777045, 16777200, 4144914, 10132016);
    private static final Map<UUID, Integer> TOKENS = new ConcurrentHashMap<>();
    private static final int REVEAL_FRAMES = 5;

    private Fx() {
    }

    static int nextToken(Player p) {
        return TOKENS.merge(p.getUniqueId(), 1, Integer::sum);
    }

    static boolean current(Player p, int token) {
        return p.isOnline() && TOKENS.getOrDefault(p.getUniqueId(), 0) == token;
    }

    public static void forget(Player p) {
        TOKENS.remove(p.getUniqueId());
    }

    private static int lerp(int a, int b, double t) {
        int r = (int)Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int g = (int)Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bl = (int)Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return r << 16 | g << 8 | bl;
    }

    public static Component shineFrame(String text, Fx.Palette pal, double pos) {
        Builder b = Component.text().decoration(TextDecoration.BOLD, true);

        for (int i = 0; i < text.length(); i++) {
            double d = Math.abs(i - pos);
            double t = d >= 2.4 ? 0.0 : Math.pow(1.0 - d / 2.4, 1.6);
            int color = lerp(pal.base(), pal.light(), t);
            int shadow = lerp(pal.shadow(), pal.shadowLight(), t);
            b.append(Component.text(text.charAt(i), TextColor.color(color)).shadowColor(ShadowColor.shadowColor(0xFF000000 | shadow)));
        }

        return b.build();
    }

    public static void shine(Player p, Component prefix, String text, Component suffix, Fx.Palette pal, Component sub, int holdTicks) {
        int token = nextToken(p);
        int reveal = 5;
        int frames = reveal + text.length() + 6;

        for (int f = 0; f <= frames; f++) {
            int frame = f;
            Runnable show = () -> {
                if (current(p, token)) {
                    boolean last = frame == frames;
                    Component body = frame < reveal ? blurFrame(text, pal, (double)(frame + 1) / reveal) : shineFrame(text, pal, frame - reveal - 3);
                    Component t = prefix.append(body).append(suffix);
                    rawTitle(p, t, sub, frame == 0 ? 2 : 0, last ? holdTicks : 20, last ? 10 : 0);
                }
            };
            if (f == 0) {
                show.run();
            } else {
                Tasks.later(f, show);
            }
        }
    }

    public static Component blurFrame(String text, Fx.Palette pal, double progress) {
        Builder b = Component.text().decoration(TextDecoration.BOLD, true);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            double threshold = (i * 7919 + 13) % 97 / 97.0 * 0.7;
            double t = Math.max(0.0, Math.min(1.0, (progress - threshold) / 0.3));
            if (c == ' ') {
                b.append(Component.text(' '));
            } else if (t <= 0.0) {
                b.append(Component.text(c, TextColor.color(pal.shadow())).decoration(TextDecoration.OBFUSCATED, true));
            } else {
                int color = lerp(pal.shadow(), pal.base(), t);
                b.append(Component.text(c, TextColor.color(color)).shadowColor(ShadowColor.shadowColor((int)(t * 255.0) << 24 | pal.shadow())));
            }
        }

        return b.build();
    }

    public static void shine(Player p, String text, Fx.Palette pal, Component sub, int holdTicks) {
        shine(p, Component.empty(), text, Component.empty(), pal, sub, holdTicks);
    }

    public static void init(CMCPvP plugin) {
        FX_FIREWORK = new NamespacedKey(plugin, "fx_firework");
    }

    private static boolean titles(Player p) {
        if (!Cfg.bool(Opt.TITLES)) {
            return false;
        } else {
            PvPProfile prof = CMCPvP.get().profiles().get(p);
            return prof.on(Setting.TITLES);
        }
    }

    private static boolean sounds(Player p) {
        return CMCPvP.get().profiles().get(p).on(Setting.MATCH_SOUNDS);
    }

    public static void sound(Player p, String key, float vol, float pitch) {
        if (sounds(p)) {
            Snd.play(p, key, vol, pitch);
        }
    }

    public static void title(Player p, Component title, Component sub, int fadeIn, int stay, int fadeOut) {
        nextToken(p);
        rawTitle(p, title, sub, fadeIn, stay, fadeOut);
    }

    private static void rawTitle(Player p, Component title, Component sub, int fadeIn, int stay, int fadeOut) {
        p.showTitle(Title.title(title, sub, Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))));
    }

    // ================================================================== cinematic text engine (1.0.9)

    /** Shows title frames one after the other ({@code every} ticks apart), the last one held then faded. */
    private static void play(Player p, List<Component[]> frames, int every, int hold, int fadeOut) {
        int token = nextToken(p);
        for (int i = 0; i < frames.size(); i++) {
            int f = i;
            boolean last = f == frames.size() - 1;
            Runnable show = () -> {
                if (current(p, token)) {
                    Component[] fr = frames.get(f);
                    rawTitle(p, fr[0], fr[1], 0, last ? hold : every + 8, last ? fadeOut : 0);
                }
            };
            if (f == 0) {
                show.run();
            } else {
                Tasks.later((long)f * every, show);
            }
        }
    }

    private static Component colored(String text, int rgb, int shadow, boolean bold) {
        Builder b = Component.text();
        b.append(Component.text(text, TextColor.color(rgb)).shadowColor(ShadowColor.shadowColor(0xFF000000 | shadow)));
        Component c = b.build();
        return bold ? c.decoration(TextDecoration.BOLD, true) : c;
    }

    private static String spaced(String text, int gap) {
        if (gap <= 0) {
            return text;
        }

        StringBuilder sb = new StringBuilder();
        String g = " ".repeat(gap);
        for (int i = 0; i < text.length(); i++) {
            if (i > 0) {
                sb.append(g);
            }

            sb.append(text.charAt(i));
        }

        return sb.toString();
    }

    /**
     * Slam: the word arrives wide and white, snaps together (a zoom), flashes, then takes its colour and a light band
     * sweeps through it.
     */
    private static List<Component[]> slam(Component prefix, String text, Component suffix, Fx.Palette pal, Component sub) {
        List<Component[]> out = new ArrayList<>();
        int[] gaps = {5, 3, 2, 1, 0};
        for (int i = 0; i < gaps.length; i++) {
            double t = (double)i / (gaps.length - 1);
            int c = lerp(0xFFFFFF, pal.base(), Math.max(0.0, t - 0.5) * 2.0);
            out.add(new Component[]{prefix.append(colored(spaced(text, gaps[i]), c, pal.shadow(), true)).append(suffix), Component.empty()});
        }

        out.add(new Component[]{prefix.append(colored(text, 0xFFFFFF, pal.shadowLight(), true)).append(suffix), sub});
        for (double pos = -2.0; pos <= text.length() + 2; pos += 1.5) {
            out.add(new Component[]{prefix.append(shineFrame(text, pal, pos)).append(suffix), sub});
        }

        out.add(new Component[]{prefix.append(shineFrame(text, pal, -9.0)).append(suffix), sub});
        return out;
    }

    /** Type-in: the letters appear one by one behind a glowing cursor, then the line settles and shines. */
    private static List<Component[]> typeIn(Component prefix, String text, Fx.Palette pal, Component sub) {
        List<Component[]> out = new ArrayList<>();
        for (int k = 1; k <= text.length(); k++) {
            Component typed = colored(text.substring(0, k), pal.base(), pal.shadow(), true);
            Component cursor = colored("▌", pal.light(), pal.shadowLight(), true);
            out.add(new Component[]{prefix.append(typed).append(cursor), Component.empty()});
        }

        for (double pos = -2.0; pos <= text.length() + 2; pos += 2.0) {
            out.add(new Component[]{prefix.append(shineFrame(text, pal, pos)), sub});
        }

        out.add(new Component[]{prefix.append(shineFrame(text, pal, -9.0)), sub});
        return out;
    }

    /** Split: the word opens from its middle outward, letters still unrevealed shown scrambled and dark. */
    private static List<Component[]> split(String text, Fx.Palette pal, Component sub) {
        List<Component[]> out = new ArrayList<>();
        double mid = (text.length() - 1) / 2.0;
        int steps = (int)Math.ceil(mid) + 2;
        for (int k = 0; k <= steps; k++) {
            Builder b = Component.text().decoration(TextDecoration.BOLD, true);
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                double d = Math.abs(i - mid);
                if (ch == ' ') {
                    b.append(Component.text(' '));
                } else if (d <= k - 1) {
                    b.append(Component.text(ch, TextColor.color(d >= k - 2 ? pal.light() : pal.base())).shadowColor(ShadowColor.shadowColor(0xFF000000 | pal.shadow())));
                } else {
                    b.append(Component.text(ch, TextColor.color(pal.shadow())).decoration(TextDecoration.OBFUSCATED, true));
                }
            }

            out.add(new Component[]{b.build(), k >= steps - 1 ? sub : Component.empty()});
        }

        out.add(new Component[]{shineFrame(text, pal, -9.0), sub});
        return out;
    }

    /** Shatter: the word breaks apart, its letters drifting wide and fading to dark grey. */
    private static List<Component[]> shatter(String text, Fx.Palette pal, Component sub) {
        List<Component[]> out = new ArrayList<>();
        out.add(new Component[]{colored(text, 0xFFFFFF, pal.shadow(), true), sub});
        out.add(new Component[]{colored(text, pal.base(), pal.shadow(), true), sub});
        int[] gaps = {0, 1, 1, 2, 3};
        for (int i = 0; i < gaps.length; i++) {
            double t = (double)i / (gaps.length - 1);
            int c = lerp(pal.base(), 0x3A3A3A, t);
            out.add(new Component[]{colored(spaced(text, gaps[i]), c, 0x111111, true), sub});
        }

        out.add(new Component[]{colored(spaced(text, 1), lerp(pal.base(), 0x555555, 0.5), 0x111111, true), sub});
        return out;
    }

    /** A number counting up (or down) in the action bar, ticking as it goes: "+0 ... +17 ELO". */
    public static void ticker(Player p, String prefix, int to, String suffix, int ticks) {
        int steps = Math.max(1, Math.min(Math.abs(to), ticks));
        for (int i = 1; i <= steps; i++) {
            int k = i;
            Tasks.later(k * (long)Math.max(1, ticks / steps), () -> {
                if (!p.isOnline()) {
                    return;
                }

                double t = 1.0 - Math.pow(1.0 - (double)k / steps, 3.0);
                int v = (int)Math.round(to * t);
                String c = to >= 0 ? "<green>" : "<red>";
                p.sendActionBar(Text.mm(prefix + c + "<b>" + (v >= 0 ? "+" : "") + v + "</b>" + suffix));
                if (k % 2 == 0 || k == steps) {
                    sound(p, "block.note_block.hat", 0.5F, 1.0F + (float)t);
                }

                if (k == steps) {
                    sound(p, to >= 0 ? "entity.experience_orb.pickup" : "block.note_block.bass", 0.7F, to >= 0 ? 1.3F : 0.7F);
                }
            });
        }
    }

    public static void matchFound(Player p, Component subtitle) {
        if (sounds(p) || CMCPvP.get().profiles().get(p).on(Setting.QUEUE_SOUNDS)) {
            Snd.play(p, "block.note_block.bell", 1.0F, 0.8F);
            Tasks.later(6L, () -> Snd.play(p, "entity.player.levelup", 0.8F, 1.4F));
            Tasks.later(8L, () -> Snd.play(p, "ui.toast.challenge_complete", 0.35F, 1.6F));
        }

        if (!titles(p)) {
            p.sendActionBar(Text.mm("<gold><b>MATCH FOUND</b></gold> <dark_gray>»</dark_gray> ").append(subtitle));
        } else {
            play(p, typeIn(Text.mm("<gold>⚔</gold> "), "MATCH FOUND", GOLD, subtitle), 1, 34, 10);
        }
    }

    public static void countdown(Player p, int secondsLeft, Component sub) {
        String color = secondsLeft >= 4 ? "<green>" : (secondsLeft == 3 ? "<yellow>" : (secondsLeft == 2 ? "<gold>" : "<red>"));
        float pitch = 0.6F + (5 - Math.min(5, secondsLeft)) * 0.15F;
        sound(p, "block.note_block.hat", 1.0F, pitch);
        sound(p, "block.note_block.pling", 0.6F, pitch);
        sound(p, "block.amethyst_block.chime", 0.7F, pitch);
        if (secondsLeft <= 3) {
            sound(p, "block.note_block.basedrum", 0.8F, 0.8F + (3 - secondsLeft) * 0.1F);
        }

        // 3.11.0: a ring of light closes in round you on every count
        ring(p, secondsLeft >= 4 ? 0x55FF55 : (secondsLeft == 3 ? 0xFFEE55 : (secondsLeft == 2 ? 0xFFAA00 : 0xFF5555)), false);
        if (!titles(p)) {
            p.sendActionBar(Text.mm("<gray>Starting in " + color + "<b>" + secondsLeft + "</b>"));
        } else {
            // 1.0.9: the badges close in on the number like a zoom, it flashes white, then takes its colour and shines
            Fx.Palette pal = secondsLeft >= 4 ? GREEN : (secondsLeft == 3 ? YELLOW : (secondsLeft == 2 ? GOLD : RED));
            String n = String.valueOf(secondsLeft);
            Component star = star(p);
            List<Component[]> frames = new ArrayList<>();
            int[] gaps = {6, 4, 2, 1};
            for (int i = 0; i < gaps.length; i++) {
                Component g = Component.text(" ".repeat(gaps[i]));
                int c = i < gaps.length - 1 ? 0xFFFFFF : pal.base();
                frames.add(new Component[]{star.append(g).append(colored(n, c, pal.shadow(), true)).append(g).append(star), sub});
            }

            frames.add(new Component[]{star.append(Component.space()).append(shineFrame(n, pal, 0.0)).append(Component.space()).append(star), sub});
            frames.add(new Component[]{star.append(Component.space()).append(shineFrame(n, pal, 9.0)).append(Component.space()).append(star), sub});
            play(p, frames, 1, 14, 0);
        }
    }

    /** The star badge beside the countdown (the pack's seal when the player has the pack, else a star). */
    private static Component star(Player p) {
        CMCPvP pl = CMCPvP.get();
        return pl != null && pl.hasPack(p) ? net.cmcpvp.pack.Glyphs.seal() : Text.mm("<gold>✪</gold>");
    }

    /**
     * A ring of coloured light round a player: closing in (countdown) or bursting out (fight), in 4 steps over
     * 8 ticks. Only the player and people near them see it.
     */
    public static void ring(Player p, int rgb, boolean out) {
        org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(rgb), 1.1F);
        for (int step = 0; step < 4; step++) {
            int k = step;
            Tasks.later(k * 2L, () -> {
                if (!p.isOnline()) {
                    return;
                }

                org.bukkit.Location c = p.getLocation().add(0, 0.15, 0);
                double r = out ? 0.6 + k * 0.9 : 2.8 - k * 0.6;
                int n = 18;
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n + k * 0.2;
                    c.getWorld().spawnParticle(org.bukkit.Particle.DUST, c.clone().add(Math.cos(a) * r, k * 0.12, Math.sin(a) * r), 1, 0, 0, 0, 0, dust);
                }
            });
        }
    }

    public static void fight(Player p) {
        sound(p, "block.note_block.pling", 1.0F, 2.0F);
        sound(p, "item.goat_horn.sound.0", 0.5F, 1.2F);
        sound(p, "entity.firework_rocket.blast", 0.6F, 1.1F);
        ring(p, 0x55FF55, true);
        p.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, p.getLocation().add(0, 1.0, 0), 3, 0.6, 0.2, 0.6, 0);
        p.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, p.getLocation().add(0, 1.0, 0), 14, 0.3, 0.3, 0.3, 0.12);
        if (!titles(p)) {
            p.sendActionBar(Text.mm("<green><b>FIGHT!</b>"));
        } else {
            Component star = star(p);
            play(p, slam(star.append(Component.space()), "FIGHT!", Component.space().append(star), GREEN, Text.mm("<gold>Good luck!")), 1, 16, 8);
        }
    }

    /** Match point for both sides: "DEATHMATCH" with crossed swords, next kill wins. */
    public static void deathmatch(Player p) {
        sound(p, "entity.wither.spawn", 0.35F, 1.6F);
        sound(p, "item.goat_horn.sound.2", 0.6F, 0.9F);
        if (!titles(p)) {
            p.sendActionBar(Text.mm("<red><b>⚔ DEATHMATCH ⚔</b> <gray>Next kill wins!"));
        } else {
            play(p, slam(Text.mm("<gold>⚔</gold> "), "DEATHMATCH", Text.mm(" <gold>⚔"), RED, Text.mm("<yellow>Next kill wins!")), 1, 30, 10);
        }
    }

    public static Component score(int mine, int theirs) {
        return Text.mm("<gray>Score: <green>" + mine + "</green> <dark_gray>-</dark_gray> <red>" + theirs + "</red>");
    }

    public static void round(Player p, boolean won, int mine, int theirs) {
        if (won) {
            sound(p, "entity.experience_orb.pickup", 1.0F, 1.2F);
            sound(p, "block.note_block.chime", 1.0F, 1.5F);
        } else {
            sound(p, "block.note_block.bass", 1.0F, 0.6F);
        }

        if (!titles(p)) {
            p.sendActionBar(
                Text.mm(won ? "<gold><b>ROUND WON</b>" : "<red><b>ROUND LOST</b>").append(Text.mm(" <dark_gray>|</dark_gray> ")).append(score(mine, theirs))
            );
        } else {
            play(p, won ? split("ROUND WON", GOLD, score(mine, theirs)) : shatter("ROUND LOST", RED, score(mine, theirs)), 1, 30, 10);
        }
    }

    public static void victory(Player p, int mine, int theirs) {
        sound(p, "ui.toast.challenge_complete", 0.8F, 1.0F);
        sound(p, "entity.player.levelup", 1.0F, 1.0F);
        if (!titles(p)) {
            p.sendActionBar(Text.mm("<green><b>VICTORY</b></green> ").append(score(mine, theirs)));
        } else {
            String trophy = Text.esc(Cfg.s(Opt.TROPHY));
            play(p, slam(Text.mm("<gold>" + trophy + "</gold> "), "VICTORY", Text.mm(" <gold>" + trophy), GREEN, score(mine, theirs)), 1, 50, 14);
            // a crown of light over the winner
            for (int k = 0; k < 3; k++) {
                int kk = k;
                Tasks.later(6L + k * 5L, () -> {
                    if (p.isOnline()) {
                        org.bukkit.Location top = p.getLocation().add(0, 2.4 + kk * 0.15, 0);
                        for (int i = 0; i < 16; i++) {
                            double a = Math.PI * 2 * i / 16;
                            p.getWorld().spawnParticle(org.bukkit.Particle.DUST, top.clone().add(Math.cos(a) * 0.55, (i % 2) * 0.18, Math.sin(a) * 0.55), 1, 0, 0, 0, 0,
                                new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0xFFD23F), 1.0F));
                        }
                    }
                });
            }
        }
    }

    public static void defeat(Player p, int mine, int theirs) {
        sound(p, "entity.blaze.death", 0.7F, 0.8F);
        if (!titles(p)) {
            p.sendActionBar(Text.mm("<red><b>DEFEAT</b></red> ").append(score(mine, theirs)));
        } else {
            play(p, shatter("DEFEAT", RED, score(mine, theirs)), 2, 40, 14);
        }
    }

    public static void draw(Player p) {
        sound(p, "block.note_block.bass", 1.0F, 1.0F);
        if (titles(p)) {
            play(p, split("DRAW", YELLOW, Text.mm("<gray>Time limit reached")), 1, 40, 10);
        }
    }

    public static void rankUp(Player p, Tier from, Tier to, Kit kit) {
        Snd.play(p, "item.totem.use", 0.6F, 1.2F);
        Snd.play(p, "ui.toast.challenge_complete", 1.0F, 1.1F);
        Component sub = Text.mm((from == null ? "<gray>Unranked" : from.mm()) + " <dark_gray>➜</dark_gray> " + to.mm() + " <dark_gray>•</dark_gray> ")
            .append(kit.label());
        shine(p, "RANK UP", GOLD, sub, 60);
        Tasks.later(30L, () -> {
            if (p.isOnline()) {
                shine(p, "RANK UP", GOLD, sub, 40);
            }
        });
        fireworks(p.getLocation(), 3, Color.ORANGE, Color.YELLOW);
    }

    public static void progressBar(Player p, String label, int have, int need) {
        int cells = 20;
        int filled = need <= 0 ? cells : Math.max(0, Math.min(cells, Math.round((float)(cells * have) / need)));
        StringBuilder sb = new StringBuilder("<green><b>" + have + "/" + need + " " + Text.esc(label) + "</b> ");
        sb.append("<green>").append("▌".repeat(filled)).append("</green><dark_gray>").append("▌".repeat(cells - filled)).append("</dark_gray>");
        p.sendActionBar(Text.mm(sb.toString()));
    }

    public static void fireworks(Location at, int count, Color... colors) {
        World w = at.getWorld();
        if (w != null) {
            for (int i = 0; i < count; i++) {
                int n = i;
                Tasks.later(i * 8L, () -> {
                    double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                    Location l = at.clone().add(Math.cos(a) * 2.2, 0.5, Math.sin(a) * 2.2);
                    spawnFirework(l, colors[n % colors.length], 1);
                });
            }
        }
    }

    public static Firework spawnFirework(Location l, Color color, int power) {
        return l.getWorld().spawn(l, Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(Type.BALL_LARGE).withColor(color).withFade(Color.WHITE).trail(true).flicker(true).build());
            meta.setPower(power);
            fw.setFireworkMeta(meta);
            fw.getPersistentDataContainer().set(FX_FIREWORK, PersistentDataType.BYTE, (byte)1);
        });
    }

    public static void killEffect(String effect, Location at) {
        if (Cfg.bool(Opt.KILL_EFFECTS) && effect != null && at.getWorld() != null) {
            World w = at.getWorld();
            Location c = at.clone().add(0.0, 1.0, 0.0);
            switch (effect) {
                case "lightning":
                    w.strikeLightningEffect(at);
                    break;
                case "blood":
                    w.spawnParticle(Particle.BLOCK, c, 80, 0.3, 0.6, 0.3, 0.1, Material.REDSTONE_BLOCK.createBlockData());
                    break;
                case "firework":
                    Firework fw = spawnFirework(c, Color.RED, 0);
                    Tasks.later(1L, fw::detonate);
                    break;
                case "explosion":
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 1);
                    Snd.at(c, "entity.generic.explode", 0.8F, 1.2F);
                    break;
                case "flames":
                    w.spawnParticle(Particle.FLAME, c, 60, 0.4, 0.8, 0.4, 0.05);
                    w.spawnParticle(Particle.LAVA, c, 10, 0.3, 0.5, 0.3, 0.0);
                    break;
                case "hearts":
                    w.spawnParticle(Particle.HEART, c.add(0.0, 0.6, 0.0), 14, 0.6, 0.5, 0.6, 0.0);
                    break;
                case "totem":
                    w.spawnParticle(Particle.TOTEM_OF_UNDYING, c, 120, 0.4, 0.8, 0.4, 0.6);
                    Snd.at(c, "item.totem.use", 0.5F, 1.4F);
                    break;
                case "souls":
                    w.spawnParticle(Particle.SOUL, c, 40, 0.4, 0.8, 0.4, 0.04);
                    w.spawnParticle(Particle.SCULK_SOUL, c, 20, 0.4, 0.8, 0.4, 0.04);
                    break;
                case "smoke":
                    w.spawnParticle(Particle.LARGE_SMOKE, c, 60, 0.4, 0.8, 0.4, 0.03);
            }
        }
    }

    public record Palette(int base, int light, int shadow, int shadowLight) {
    }
}
