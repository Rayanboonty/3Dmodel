package net.practicepvp.fx;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.data.Setting;
import net.practicepvp.fx.anim.Anim;
import net.practicepvp.pack.Glyphs;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Match intro: the two names glide in from the sides of the screen (pixel by pixel with the pack, eased), meet at a
 * "VS" that pops in white and settles gold, then a light band with a trailing shadow sweeps through each name in the
 * name's own colour, one after the other, until the title fades.
 */
public final class Versus {
    /** Ticks the names take to slide in. */
    private static final int SLIDE = 12;
    /** Total ticks before the fade out (it ends just as the map vote opens). */
    private static final int LENGTH = 42;
    /** Starting gap between a name and the VS, in pixels. */
    private static final int GAP = 150;

    private Versus() {
    }

    private static boolean titles(Player p) {
        if (!Cfg.bool(Opt.TITLES)) {
            return false;
        } else {
            PvPProfile prof = PracticePlugin.get().profiles().getIfLoaded(p.getUniqueId());
            return prof == null || prof.on(Setting.TITLES);
        }
    }

    /** The name colour of {@code p}'s server rank (white without one). */
    public static int colorOf(Player p) {
        try {
            return Anim.parseRgb(PracticePlugin.get().serverRanks().of(p).nameColor, 0xFFFFFF);
        } catch (RuntimeException var2) {
            return 0xFFFFFF;
        }
    }

    private static Component gap(int px, boolean pack) {
        return pack ? Glyphs.space(px) : Component.text(" ".repeat(Math.round(px / 4.0F)));
    }

    /** Frame {@code f} of the intro. */
    static Component frame(String left, int leftRgb, String right, int rightRgb, int f, boolean pack) {
        double slide = Anim.easeOutCubic((double)f / SLIDE);
        int px = (int)Math.round(GAP * (1.0 - slide));
        // VS: white flash on impact, then settles to gold with a soft pulse
        int vsColor;
        if (f < SLIDE - 1) {
            vsColor = Anim.mix(0x6B4A00, 0xFFAA00, (double)f / (SLIDE - 1));
        } else if (f < SLIDE + 4) {
            vsColor = Anim.mix(0xFFFFFF, 0xFFC633, (f - SLIDE + 1) / 5.0);
        } else {
            vsColor = Anim.mix(0xFFAA00, 0xFFE27A, 0.5 + 0.5 * Math.sin((f - SLIDE) * 0.25));
        }

        Component vs = Component.text(" VS ", TextColor.color(vsColor))
            .decoration(TextDecoration.BOLD, true)
            .shadowColor(ShadowColor.shadowColor(0xFF3F2A00));
        // the light runs through the left name first, then the right one, then a pause
        double l = f < SLIDE ? 2.0 : Anim.sweepPhase(f - SLIDE, 40, 18, 0);
        double r = f < SLIDE ? 2.0 : Anim.sweepPhase(f - SLIDE, 40, 18, -10);
        return Component.empty()
            .append(Anim.sweep(left, leftRgb, l, true))
            .append(gap(px, pack))
            .append(vs)
            .append(gap(px, pack))
            .append(Anim.sweep(right, rightRgb, r, true));
    }

    /** Plays the intro for {@code viewer} fighting {@code opponent}. */
    public static void play(Player viewer, Player opponent, Component subtitle) {
        play(viewer, viewer.getName(), colorOf(viewer), opponent.getName(), colorOf(opponent), subtitle);
    }

    public static void play(Player viewer, String me, int meRgb, String opponent, int themRgb, Component subtitle) {
        if (!titles(viewer)) {
            viewer.sendActionBar(
                Component.empty()
                    .append(Component.text(me, TextColor.color(meRgb)).decoration(TextDecoration.BOLD, true))
                    .append(Text.mm(" <gold><b>VS</b> "))
                    .append(Component.text(opponent, TextColor.color(themRgb)).decoration(TextDecoration.BOLD, true))
            );
        } else {
            boolean pack = PracticePlugin.get().hasPack(viewer);
            int token = Fx.nextToken(viewer);
            int[] t = new int[]{0};
            BukkitTask[] task = new BukkitTask[1];
            Runnable step = () -> {
                int f = t[0]++;
                if (!Fx.current(viewer, token) || f > LENGTH) {
                    Tasks.cancel(task[0]);
                } else {
                    boolean last = f == LENGTH;
                    viewer.showTitle(
                        Title.title(
                            frame(me, meRgb, opponent, themRgb, f, pack),
                            f >= SLIDE - 3 ? subtitle : Component.empty(),
                            Times.times(Duration.ofMillis(f == 0 ? 100L : 0L), Duration.ofMillis(last ? 700L : 1000L), Duration.ofMillis(last ? 450L : 0L))
                        )
                    );
                    if (f == SLIDE - 1) {
                        Snd.play(viewer, "item.mace.smash_ground", 0.6F, 1.2F);
                        Snd.play(viewer, "block.note_block.bell", 0.8F, 0.7F);
                    } else if (f < SLIDE - 1 && f % 3 == 0) {
                        Snd.play(viewer, "entity.breeze.wind_burst", 0.25F, 1.5F + f * 0.04F);
                    } else if (f > SLIDE && (f - SLIDE) % 40 == 0) {
                        Snd.play(viewer, "block.amethyst_block.chime", 0.35F, 1.6F);
                    }
                }
            };
            step.run();
            task[0] = Tasks.timer(1L, 1L, step);
        }
    }
}
