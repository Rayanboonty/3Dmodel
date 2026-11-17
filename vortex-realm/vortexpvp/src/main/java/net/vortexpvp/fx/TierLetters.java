package net.vortexpvp.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.rank.Tier;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TierLetters {
    private static final int[][] FONT = new int[128][];
    private static final float PX = 0.55F;

    private TierLetters() {
    }

    public static Material block(Tier t) {
        return switch (t) {
            case LT5, MT5 -> Material.LIGHT_GRAY_CONCRETE;
            case HT5 -> Material.WHITE_CONCRETE;
            case LT4, MT4 -> Material.LIME_CONCRETE;
            case HT4 -> Material.GREEN_CONCRETE;
            case LT3, MT3 -> Material.LIGHT_BLUE_CONCRETE;
            case HT3 -> Material.BLUE_CONCRETE;
            case LT2, MT2 -> Material.MAGENTA_CONCRETE;
            case HT2 -> Material.PURPLE_CONCRETE;
            case LT1, MT1 -> Material.YELLOW_CONCRETE;
            case HT1 -> Material.GOLD_BLOCK;
        };
    }

    public static String longName(Tier t) {
        return t.longName();
    }

    private static List<TierLetters.Pixel> layout(String text) {
        List<TierLetters.Pixel> out = new ArrayList<>();
        int chars = text.length();
        double width = (chars * 5 + (chars - 1)) * 0.55F;
        double cursor = -width / 2.0;

        for (int i = 0; i < chars; i++) {
            int[] g = FONT[text.charAt(i)];
            if (g != null) {
                for (int row = 0; row < 7; row++) {
                    for (int col = 0; col < 5; col++) {
                        if ((g[row] >> 4 - col & 1) == 1) {
                            out.add(new TierLetters.Pixel(cursor + col * 0.55F, (double)((6 - row) * 0.55F)));
                        }
                    }
                }
            }

            cursor += 3.3000002F;
        }

        return out;
    }

    private static Transformation at(float dx, float dy, float dz, float scale, Quaternionf rot) {
        return new Transformation(new Vector3f(dx, dy, dz), rot, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private static Quaternionf randomRot(ThreadLocalRandom r) {
        return new Quaternionf().rotateXYZ((float)(r.nextDouble() * 6.28), (float)(r.nextDouble() * 6.28), (float)(r.nextDouble() * 6.28));
    }

    public static int play(VortexPvP plugin, Player p, Tier from, Tier to, Vector facing) {
        World w = p.getWorld();
        Vector dir = facing.clone().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = p.getEyeLocation().getDirection().setY(0);
        }

        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }

        dir.normalize();
        Vector right = new Vector(-dir.getZ(), 0.0, dir.getX());
        Location base = p.getLocation().add(dir.clone().multiply(8.5)).add(0.0, 0.6, 0.0);
        // turn the camera smoothly toward the letters instead of snapping
        RankDrop.turn(p, base.clone().add(0.0, 2.0, 0.0), 12);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<BlockDisplay> oldLetters = new ArrayList<>();
        List<BlockDisplay> newLetters = new ArrayList<>();
        List<TierLetters.Pixel> oldPx = from == null ? List.of() : layout(from.label);
        List<TierLetters.Pixel> newPx = layout(to.label);
        BlockData oldData = (from == null ? Material.GRAY_CONCRETE : block(from)).createBlockData();
        BlockData newData = block(to).createBlockData();
        Color glow = Color.fromRGB(Integer.parseInt(to.color.substring(1), 16));
        int[] t = new int[]{0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Tasks.timer(0L, 1L, () -> {
            int now = (int)(t[0]++);
            if (now == 0) {
                for (TierLetters.Pixel px : oldPx) {
                    Location l = base.clone().add(right.clone().multiply(px.x())).add(0.0, px.y(), 0.0);
                    BlockDisplay d = spawn(w, l, oldData, at(-0.275F, 0.0F, -0.275F, 0.01F, new Quaternionf()));
                    if (d != null) {
                        oldLetters.add(d);
                        int delay = (int)Math.round((px.x() + 4.0) * 1.2);
                        Tasks.later(Math.max(1, delay), () -> anim(d, 6, at(-0.275F, 0.0F, -0.275F, 0.55F, new Quaternionf())));
                    }
                }

                Snd.at(base, "block.beacon.activate", 1.0F, 1.4F);
            } else if (now == 30) {
                for (BlockDisplay d : oldLetters) {
                    Vector out = new Vector(r.nextDouble() - 0.5, r.nextDouble() * 0.8, r.nextDouble() - 0.5).normalize().multiply(1.5 + r.nextDouble() * 2.5);
                    anim(d, 12, at((float)out.getX(), (float)out.getY(), (float)out.getZ(), 0.08F, randomRot(r)));
                }

                w.spawnParticle(Particle.EXPLOSION, base.clone().add(0.0, 2.0, 0.0), 3, 1.5, 1.0, 0.3, 0.0);
                w.spawnParticle(Particle.BLOCK, base.clone().add(0.0, 2.0, 0.0), 120, 2.0, 1.5, 0.4, 0.0, oldData);
                Snd.at(base, "entity.generic.explode", 0.6F, 1.3F);
                Snd.at(base, "block.amethyst_block.break", 1.0F, 0.7F);
            } else if (now == 34) {
                for (TierLetters.Pixel pxx : newPx) {
                    Location l = base.clone().add(right.clone().multiply(pxx.x())).add(0.0, pxx.y(), 0.0);
                    Vector off = new Vector(r.nextDouble() - 0.5, r.nextDouble() - 0.3, r.nextDouble() - 0.5).normalize().multiply(3.0 + r.nextDouble() * 4.0);
                    BlockDisplay d = spawn(w, l, newData, at((float)off.getX(), (float)off.getY(), (float)off.getZ(), 0.12F, randomRot(r)));
                    if (d != null) {
                        d.setGlowing(true);
                        d.setGlowColorOverride(glow);
                        newLetters.add(d);
                        Tasks.later(2 + r.nextInt(6), () -> anim(d, 16, at(-0.275F, 0.0F, -0.275F, 0.55F, new Quaternionf())));
                    }
                }

                Snd.at(base, "item.trident.riptide_3", 0.8F, 1.2F);
            } else if (now == 44) {
                for (BlockDisplay d : oldLetters) {
                    d.remove();
                }

                oldLetters.clear();
            } else if (now == 56) {
                w.spawnParticle(Particle.FLAME, base.clone().add(0.0, 0.3, 0.0), 160, 2.8, 0.2, 0.3, 0.06);
                w.spawnParticle(Particle.LAVA, base.clone().add(0.0, 0.5, 0.0), 30, 2.5, 0.3, 0.3, 0.0);
                w.spawnParticle(Particle.FIREWORK, base.clone().add(0.0, 2.2, 0.0), 80, 2.5, 1.5, 0.4, 0.12);
                Snd.at(base, "entity.firework_rocket.large_blast", 1.0F, 0.9F);
                Snd.at(base, "entity.blaze.shoot", 0.8F, 0.7F);
                if (p.isOnline()) {
                    Snd.play(p, "ui.toast.challenge_complete", 1.0F, 1.0F);
                    Fx.shine(p, "RANK UP", Fx.GOLD, Text.mm("<" + to.color + "><b>" + longName(to)), 60);
                }
            } else if (now > 56 && now < 150) {
                if (now % 3 == 0 && !newPx.isEmpty()) {
                    for (int i = 0; i < 4; i++) {
                        TierLetters.Pixel pxxx = newPx.get(r.nextInt(newPx.size()));
                        Location l = base.clone().add(right.clone().multiply(pxxx.x() + 0.275F)).add(0.0, pxxx.y() + 0.55F, 0.0);
                        w.spawnParticle(r.nextInt(4) == 0 ? Particle.LAVA : Particle.FLAME, l, 2, 0.1, 0.05, 0.1, 0.01);
                    }

                    if (now % 9 == 0) {
                        w.spawnParticle(Particle.SMALL_FLAME, base.clone().add(right.clone().multiply(r.nextDouble() * 6.0 - 3.0)), 6, 0.3, 0.1, 0.3, 0.01);
                    }
                }

                if (now % 20 == 0) {
                    float bob = now / 20 % 2 == 0 ? 0.12F : 0.0F;

                    for (BlockDisplay d : newLetters) {
                        anim(d, 20, at(-0.275F, bob, -0.275F, 0.55F, new Quaternionf()));
                    }
                }
            } else if (now == 150) {
                for (BlockDisplay d : newLetters) {
                    anim(d, 8, at(0.0F, 0.3F, 0.0F, 0.01F, randomRot(r)));
                }

                w.spawnParticle(Particle.CLOUD, base.clone().add(0.0, 2.0, 0.0), 40, 2.5, 1.5, 0.3, 0.02);
            } else if (now >= 160) {
                for (BlockDisplay d : newLetters) {
                    d.remove();
                }

                for (BlockDisplay d : oldLetters) {
                    d.remove();
                }

                Tasks.cancel(task[0]);
            }
        });
        return 160;
    }

    private static BlockDisplay spawn(World w, Location l, BlockData data, Transformation tf) {
        try {
            return w.spawn(l, BlockDisplay.class, d -> {
                d.setBlock(data);
                d.setTransformation(tf);
                d.setBrightness(new Brightness(15, 15));
                d.setPersistent(false);
                d.setViewRange(1.5F);
            });
        } catch (RuntimeException var5) {
            return null;
        }
    }

    private static void anim(Display d, int ticks, Transformation t) {
        if (d.isValid()) {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(ticks);
            d.setTransformation(t);
        }
    }

    static {
        FONT[76] = new int[]{16, 16, 16, 16, 16, 16, 31};
        FONT[77] = new int[]{17, 27, 21, 17, 17, 17, 17};
        FONT[72] = new int[]{17, 17, 17, 31, 17, 17, 17};
        FONT[84] = new int[]{31, 4, 4, 4, 4, 4, 4};
        FONT[49] = new int[]{4, 12, 4, 4, 4, 4, 14};
        FONT[50] = new int[]{14, 17, 1, 2, 4, 8, 31};
        FONT[51] = new int[]{30, 1, 1, 14, 1, 1, 30};
        FONT[52] = new int[]{2, 6, 10, 18, 31, 2, 2};
        FONT[53] = new int[]{31, 16, 30, 1, 1, 17, 14};
    }

    private record Pixel(double x, double y) {
    }
}
