package net.practicepvp.fx;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.ClickCallback.Options;
import net.practicepvp.PracticePlugin;
import net.practicepvp.board.BoardManager;
import net.practicepvp.rank.Tier;
import net.practicepvp.session.PlayerState;
import net.practicepvp.util.ChatCenter;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Player;

public final class RankUpCelebration {
    private static final List<String> HAPPY = List.of(
        "The grind is paying off. Keep climbing!",
        "Insane! You're getting better every match.",
        "That's what practice does. The next tier is calling!",
        "Your opponents should start to worry.",
        "Clean. Earned. Deserved.",
        "Look at you go! Don't stop now.",
        "Another step up the ladder - one more?"
    );
    private static final List<String> ELITE = List.of(
        "You're among the elite now. Legendary.", "Only a few players ever see this tier.", "Welcome to the top. Defend it!"
    );

    private RankUpCelebration() {
    }

    public static void play(PracticePlugin plugin, Player p, Tier from, Tier to, Component modeLabel, int points, String kitId, boolean placement) {
        if (p.isOnline()) {
            PlayerState st = plugin.sessions().state(p);
            boolean busy = st == PlayerState.MATCH || st == PlayerState.PREMATCH;
            Fx.Palette pal = palette(to);
            int rgb = rgb(to);
            chatCard(p, from, to, modeLabel, points, kitId, placement);
            chime(p);
            if (!busy) {
                Component sub = (from == null ? Text.mm("<gray>Unranked") : BoardManager.tierTag(from))
                    .append(Text.mm(" <dark_gray>➜</dark_gray> "))
                    .append(BoardManager.tierTag(to))
                    .append(Text.mm(" <dark_gray>•</dark_gray> "))
                    .append(modeLabel);
                Fx.shine(p, placement ? "PLACED!" : "RANK UP!", pal, sub, 40);
                Tasks.later(38L, () -> {
                    if (p.isOnline()) {
                        Fx.shine(p, to.label, pal, Text.mm("<gray>Your new tier in ").append(modeLabel), 50);
                    }
                });
                helix(p, rgb);
                bossBar(p, to, modeLabel, placement);
                Tasks.later(12L, () -> {
                    if (p.isOnline()) {
                        Color c = Color.fromRGB(rgb);
                        Fx.fireworks(p.getLocation(), top(to) ? 5 : 3, c, Color.WHITE, c);
                    }
                });
            }
        }
    }

    private static boolean top(Tier t) {
        return t.ordinal() >= Tier.LT2.ordinal();
    }

    private static int rgb(Tier t) {
        try {
            return Integer.parseInt(t.color.substring(1), 16);
        } catch (RuntimeException var2) {
            return 16755200;
        }
    }

    private static int mix(int a, int b, double t) {
        int r = (int)Math.round((a >> 16 & 0xFF) * (1.0 - t) + (b >> 16 & 0xFF) * t);
        int g = (int)Math.round((a >> 8 & 0xFF) * (1.0 - t) + (b >> 8 & 0xFF) * t);
        int bl = (int)Math.round((a & 0xFF) * (1.0 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }

    private static Fx.Palette palette(Tier t) {
        int base = rgb(t);
        return new Fx.Palette(base, mix(base, 16777215, 0.8), mix(base, 0, 0.75), mix(base, 0, 0.35));
    }

    private static void chime(Player p) {
        float[] pitches = new float[]{0.75F, 0.94F, 1.12F, 1.5F};

        for (int i = 0; i < pitches.length; i++) {
            float pitch = pitches[i];
            Tasks.later(i * 3L, () -> {
                Snd.play(p, "block.note_block.chime", 1.0F, pitch);
                Snd.play(p, "block.note_block.bell", 0.5F, pitch);
            });
        }

        Tasks.later(13L, () -> {
            Snd.play(p, "ui.toast.challenge_complete", 0.9F, 1.1F);
            Snd.play(p, "item.totem.use", 0.45F, 1.3F);
        });
    }

    private static void helix(Player p, int rgb) {
        DustOptions dust = new DustOptions(Color.fromRGB(rgb), 1.3F);
        DustOptions white = new DustOptions(Color.WHITE, 0.9F);

        for (int t = 0; t < 40; t += 2) {
            int tick = t;
            Tasks.later(
                t,
                () -> {
                    if (p.isOnline()) {
                        Location c = p.getLocation();
                        double y = tick / 40.0 * 2.6;

                        for (int k = 0; k < 2; k++) {
                            double a = tick * 0.42 + k * Math.PI;
                            p.getWorld()
                                .spawnParticle(
                                    Particle.DUST,
                                    c.getX() + Math.cos(a) * 0.9,
                                    c.getY() + y,
                                    c.getZ() + Math.sin(a) * 0.9,
                                    2,
                                    0.02,
                                    0.02,
                                    0.02,
                                    0.0,
                                    k == 0 ? dust : white
                                );
                        }
                    }
                }
            );
        }

        Tasks.later(40L, () -> {
            if (p.isOnline()) {
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0.0, 1.4, 0.0), 80, 0.4, 0.6, 0.4, 0.45);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0.0, 2.6, 0.0), 20, 0.2, 0.2, 0.2, 0.08);
            }
        });
    }

    private static net.kyori.adventure.bossbar.BossBar.Color barColor(Tier t) {
        return switch (t) {
            case LT4, HT4 -> net.kyori.adventure.bossbar.BossBar.Color.GREEN;
            case LT3, HT3 -> net.kyori.adventure.bossbar.BossBar.Color.BLUE;
            case LT2, HT2 -> net.kyori.adventure.bossbar.BossBar.Color.PURPLE;
            case LT1, HT1 -> net.kyori.adventure.bossbar.BossBar.Color.YELLOW;
            default -> net.kyori.adventure.bossbar.BossBar.Color.WHITE;
        };
    }

    private static void bossBar(Player p, Tier to, Component modeLabel, boolean placement) {
        BossBar bar = BossBar.bossBar(
            Text.mm("<white><b>" + (placement ? "PLACED" : "RANK UP") + "</b> <dark_gray>» ")
                .append(BoardManager.tierTag(to))
                .append(Text.mm(" <gray>in "))
                .append(modeLabel),
            0.0F,
            barColor(to),
            Overlay.NOTCHED_10
        );
        p.showBossBar(bar);

        for (int i = 1; i <= 15; i++) {
            float progress = i / 15.0F;
            Tasks.later(i * 2L, () -> bar.progress(progress));
        }

        Tasks.later(110L, () -> p.hideBossBar(bar));
    }

    private static String bar(int have, int need, int cells, String color) {
        int filled = need <= 0 ? cells : Math.max(0, Math.min(cells, Math.round((float)(cells * have) / need)));
        return color + "▌".repeat(filled) + "<dark_gray>" + "▌".repeat(cells - filled);
    }

    private static void chatCard(Player p, Tier from, Tier to, Component modeLabel, int points, String kitId, boolean placement) {
        String color = to.color;
        Component line = Text.mm(
            "<gradient:" + color + ":#FFFFFF:" + color + "><st>                                                                  </st></gradient>"
        );
        String happy = (top(to) ? ELITE : HAPPY).get(ThreadLocalRandom.current().nextInt((top(to) ? ELITE : HAPPY).size()));
        p.sendMessage(line);
        p.sendMessage(ChatCenter.center(Fx.shineFrame(placement ? "★ PLACED ★" : "★ RANK UP ★", palette(to), 5.0)));
        p.sendMessage(
            ChatCenter.center(
                Text.mm("<gray>You are now ")
                    .append(BoardManager.tierTag(to))
                    .append(Text.mm(" <gray>in "))
                    .append(modeLabel)
                    .append(
                        Text.mm(from == null ? "" : " <dark_gray>(was ")
                            .append((Component)(from == null ? Component.empty() : BoardManager.tierTag(from).append(Text.mm("<dark_gray>)"))))
                    )
            )
        );
        p.sendMessage(ChatCenter.center(Text.mm("<white><i>" + Text.esc(happy))));
        Tier next = to.next();
        if (next == null) {
            p.sendMessage(ChatCenter.center(Text.mm("<gold><b>♛ You reached the highest tier. Defend your crown! ♛")));
        } else if (points >= 0) {
            int span = Math.max(1, next.threshold() - to.threshold());
            int have = Math.max(0, points - to.threshold());
            int left = Math.max(0, next.threshold() - points);
            p.sendMessage(
                ChatCenter.center(
                    Text.mm("<gray>Next: ")
                        .append(BoardManager.tierTag(next))
                        .append(Text.mm(" " + bar(have, span, 16, "<" + next.color + ">") + " <gray>" + left + " pts to go"))
                )
            );
        }

        Component buttons = Component.empty();
        if (kitId != null) {
            buttons = buttons.append(
                    Text.mm("<green><b>[⚔ Play Ranked]</b>")
                        .clickEvent(ClickEvent.runCommand("/queue ranked " + kitId))
                        .hoverEvent(HoverEvent.showText(Text.mm("<gray>Keep the streak going!")))
                )
                .append(Text.mm("    "));
        }

        buttons = buttons.append(
            Text.mm("<gold><b>[★ Your Stats]</b>")
                .clickEvent(ClickEvent.runCommand("/stats"))
                .hoverEvent(HoverEvent.showText(Text.mm("<gray>See all your tiers")))
        );
        p.sendMessage(ChatCenter.center(buttons));
        p.sendMessage(line);
    }

    public static void announce(Player p, Tier to, Component modeLabel) {
        Set<UUID> gg = new HashSet<>();
        String name = p.getName();
        Component ggButton = Text.mm(" <yellow><b>[GG]</b>")
            .hoverEvent(HoverEvent.showText(Text.mm("<gray>Congratulate <white>" + Text.esc(name))))
            .clickEvent(ClickEvent.callback(audience -> {
                if (audience instanceof Player clicker && !clicker.getName().equals(name) && gg.add(clicker.getUniqueId())) {
                    Tasks.ensureSync(() -> clicker.chat("GG " + name + "! Congrats on " + to.label + "!"));
                }
            }, Options.builder().uses(-1).lifetime(Duration.ofMinutes(3L)).build()));
        Component msg = Text.mm("<gold><b>⬆ RANK UP</b> <white>" + Text.esc(name) + " <gray>reached ")
            .append(BoardManager.tierTag(to))
            .append(Text.mm(" <gray>in "))
            .append(modeLabel)
            .append(Text.mm("<gray>!"))
            .append(ggButton);
        Bukkit.getServer().sendMessage(msg);

        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(p)) {
                Snd.play(o, "block.note_block.pling", 0.4F, 1.6F);
            }
        }
    }
}
