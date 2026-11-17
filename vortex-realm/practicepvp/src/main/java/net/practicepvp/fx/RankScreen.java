package net.practicepvp.fx;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.TitlePart;
import net.kyori.adventure.title.Title.Times;
import net.practicepvp.PracticePlugin;
import net.practicepvp.pack.Glyphs;
import net.practicepvp.rank.Tier;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class RankScreen {
    private static final Times HOLD = Times.times(Duration.ZERO, Duration.ofSeconds(3L), Duration.ofMillis(500L));
    private static final Times END = Times.times(Duration.ZERO, Duration.ofMillis(600L), Duration.ofMillis(900L));

    private RankScreen() {
    }

    public static int play(PracticePlugin plugin, Player p, Tier from, Tier to) {
        int off = from == null ? -20 : 0;
        int hideStart = 4;
        int revealStart = 26 + off;
        int impact = revealStart + 16;
        int shineStart = impact + 2;
        int fade = impact + 60;
        int end = fade + 20;
        int newT = to.ordinal();
        int oldT = from == null ? -1 : from.ordinal();
        Color dust = Color.fromRGB(Integer.parseInt(to.color.substring(1), 16));
        p.sendTitlePart(TitlePart.TIMES, HOLD);
        p.sendTitlePart(TitlePart.SUBTITLE, Component.empty());
        int[] t = new int[]{0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Tasks.timer(
            0L,
            1L,
            () -> {
                int now = (int)(t[0]++);
                if (p.isOnline() && !(now > end)) {
                    Component frame = null;
                    if (oldT >= 0 && now < hideStart) {
                        frame = Glyphs.rankFrame(oldT, 0, 16, -1);
                        if (now == 0) {
                            Snd.play(p, "block.beacon.ambient", 0.8F, 1.4F);
                        }
                    } else if (oldT >= 0 && now < hideStart + 16) {
                        frame = Glyphs.rankFrame(oldT, 0, 15 - (now - hideStart), -1);
                        if (now % 3 == 0) {
                            Snd.play(p, "block.amethyst_block.step", 0.6F, 1.5F - (now - hideStart) * 0.04F);
                        }
                    } else if (now < revealStart) {
                        frame = Glyphs.rankFrame(newT, now % 4 < 2 ? 0 : 1, -1, -1);
                        if (now == Math.max(0, revealStart - 4)) {
                            Snd.play(p, "block.beacon.activate", 0.9F, 1.5F);
                        }
                    } else if (now < impact) {
                        int f = now - revealStart;
                        frame = Glyphs.rankFrame(newT, 1, f, -1);
                        if (f % 2 == 0) {
                            Snd.play(p, "block.note_block.chime", 0.45F, 0.7F + f * 0.07F);
                        }
                    } else if (now == impact) {
                        frame = Glyphs.rankFrame(newT, 2, 16, -1);
                        p.sendTitlePart(
                            TitlePart.SUBTITLE, Text.mm("<white><b>RANK UP</b> <dark_gray>» <" + to.color + "><b>" + to.longName().toUpperCase() + "</b>")
                        );
                        Snd.play(p, "ui.toast.challenge_complete", 1.0F, 1.1F);
                        Snd.play(p, "entity.player.levelup", 0.8F, 1.3F);
                        Snd.play(p, "entity.firework_rocket.twinkle", 0.8F, 1.0F);
                        Location at = p.getLocation().add(0.0, 1.2, 0.0);
                        p.getWorld().spawnParticle(Particle.END_ROD, at, 40, 0.6, 0.9, 0.6, 0.08);
                        p.getWorld().spawnParticle(Particle.DUST, at, 60, 0.8, 1.0, 0.8, 0.0, new DustOptions(dust, 1.4F));
                    } else if (now < shineStart + 16) {
                        int s = now - shineStart;
                        frame = s < 0 ? Glyphs.rankFrame(newT, 2, 16, -1) : Glyphs.rankFrame(newT, s < 6 ? 2 : 1, 16, s / 2);
                    } else if (now < fade) {
                        frame = Glyphs.rankFrame(newT, now / 6 % 2 == 0 ? 1 : 0, 16, -1);
                    } else if (now == fade) {
                        p.sendTitlePart(TitlePart.TIMES, END);
                        frame = Glyphs.rankFrame(newT, 0, 16, -1);
                    }

                    if (frame != null) {
                        p.sendTitlePart(TitlePart.TITLE, frame);
                    }
                } else {
                    Tasks.cancel(task[0]);
                }
            }
        );
        return end;
    }
}
