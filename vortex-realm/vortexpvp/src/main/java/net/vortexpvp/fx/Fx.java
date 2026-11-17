package net.vortexpvp.fx;

import java.time.Duration;
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
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.data.Setting;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.rank.Tier;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
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

    public static void init(VortexPvP plugin) {
        FX_FIREWORK = new NamespacedKey(plugin, "fx_firework");
    }

    private static boolean titles(Player p) {
        if (!Cfg.bool(Opt.TITLES)) {
            return false;
        } else {
            PvPProfile prof = VortexPvP.get().profiles().get(p);
            return prof.on(Setting.TITLES);
        }
    }

    private static boolean sounds(Player p) {
        return VortexPvP.get().profiles().get(p).on(Setting.MATCH_SOUNDS);
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

    public static void matchFound(Player p, Component subtitle) {
        if (sounds(p) || VortexPvP.get().profiles().get(p).on(Setting.QUEUE_SOUNDS)) {
            Snd.play(p, "block.note_block.bell", 1.0F, 0.8F);
            Tasks.later(6L, () -> Snd.play(p, "entity.player.levelup", 0.8F, 1.4F));
            Tasks.later(8L, () -> Snd.play(p, "ui.toast.challenge_complete", 0.35F, 1.6F));
        }

        if (!titles(p)) {
            p.sendActionBar(Text.mm("<gold><b>MATCH FOUND</b></gold> <dark_gray>»</dark_gray> ").append(subtitle));
        } else {
            shine(p, "MATCH FOUND", GOLD, subtitle, 30);
        }
    }

    public static void countdown(Player p, int secondsLeft, Component sub) {
        String color = secondsLeft >= 4 ? "<green>" : (secondsLeft == 3 ? "<yellow>" : (secondsLeft == 2 ? "<gold>" : "<red>"));
        float pitch = 0.6F + (5 - Math.min(5, secondsLeft)) * 0.15F;
        sound(p, "block.note_block.hat", 1.0F, pitch);
        sound(p, "block.note_block.pling", 0.6F, pitch);
        if (!titles(p)) {
            p.sendActionBar(Text.mm("<gray>Starting in " + color + "<b>" + secondsLeft + "</b>"));
        } else {
            Fx.Palette pal = secondsLeft >= 4 ? GREEN : (secondsLeft == 3 ? YELLOW : (secondsLeft == 2 ? GOLD : RED));
            int token = nextToken(p);
            String n = String.valueOf(secondsLeft);
            rawTitle(p, shineFrame(n, pal, 0.0), sub, 0, 22, 0);
            Tasks.later(4L, () -> {
                if (current(p, token)) {
                    rawTitle(p, shineFrame(n, pal, 9.0), sub, 0, 18, 0);
                }
            });
        }
    }

    public static void fight(Player p) {
        sound(p, "block.note_block.pling", 1.0F, 2.0F);
        sound(p, "item.goat_horn.sound.0", 0.5F, 1.2F);
        if (!titles(p)) {
            p.sendActionBar(Text.mm("<green><b>FIGHT!</b>"));
        } else {
            shine(p, "FIGHT!", GREEN, Text.mm("<gold>Good luck!"), 14);
        }
    }

    /** Match point for both sides: "DEATHMATCH" with crossed swords, next kill wins. */
    public static void deathmatch(Player p) {
        sound(p, "entity.wither.spawn", 0.35F, 1.6F);
        sound(p, "item.goat_horn.sound.2", 0.6F, 0.9F);
        if (!titles(p)) {
            p.sendActionBar(Text.mm("<red><b>⚔ DEATHMATCH ⚔</b> <gray>Next kill wins!"));
        } else {
            shine(p, Text.mm("<gold>⚔</gold> "), "DEATHMATCH", Text.mm(" <gold>⚔"), RED, Text.mm("<yellow>Next kill wins!"), 30);
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
            shine(p, won ? "ROUND WON" : "ROUND LOST", won ? GOLD : RED, score(mine, theirs), 30);
        }
    }

    public static void victory(Player p, int mine, int theirs) {
        sound(p, "ui.toast.challenge_complete", 0.8F, 1.0F);
        sound(p, "entity.player.levelup", 1.0F, 1.0F);
        if (!titles(p)) {
            p.sendActionBar(Text.mm("<green><b>VICTORY</b></green> ").append(score(mine, theirs)));
        } else {
            String trophy = Text.esc(Cfg.s(Opt.TROPHY));
            shine(p, Text.mm("<gold>" + trophy + "</gold> "), "VICTORY", Text.mm(" <gold>" + trophy), GREEN, score(mine, theirs), 50);
        }
    }

    public static void defeat(Player p, int mine, int theirs) {
        sound(p, "entity.blaze.death", 0.7F, 0.8F);
        if (!titles(p)) {
            p.sendActionBar(Text.mm("<red><b>DEFEAT</b></red> ").append(score(mine, theirs)));
        } else {
            shine(p, "DEFEAT", RED, score(mine, theirs), 50);
        }
    }

    public static void draw(Player p) {
        sound(p, "block.note_block.bass", 1.0F, 1.0F);
        if (titles(p)) {
            shine(p, "DRAW", YELLOW, Text.mm("<gray>Time limit reached"), 40);
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
