package net.cmcpvp.fx;

import java.util.ArrayList;
import java.util.List;
import net.cmcpvp.rank.Tier;
import net.cmcpvp.util.Snd;
import net.cmcpvp.util.Tasks;
import net.cmcpvp.util.Text;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 1.0.17: the tier crest of a rank-up. Blocks in the tier's colours fly in from all round and lock into a ring round
 * the player, two wings unfold behind them, the tier's name rises above in big letters, then the crest bursts into
 * fireworks of its colour. Everyone near can see it.
 */
public final class TierCrest {
    private TierCrest() {
    }

    private static Material blockOf(Tier t) {
        return switch (t.number()) {
            case 1 -> Material.GOLD_BLOCK;
            case 2 -> Material.AMETHYST_BLOCK;
            case 3 -> Material.LIGHT_BLUE_CONCRETE;
            case 4 -> Material.LIME_CONCRETE;
            default -> Material.LIGHT_GRAY_CONCRETE;
        };
    }

    public static void play(Player p, Tier tier) {
        if (p == null || !p.isOnline() || tier == null) {
            return;
        }

        World w = p.getWorld();
        Location base = p.getLocation();
        BlockData main = blockOf(tier).createBlockData();
        BlockData trim = Material.WHITE_CONCRETE.createBlockData();
        List<Entity> parts = new ArrayList<>();
        int n = 16;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            float rx = (float)(Math.cos(a) * 1.6);
            float rz = (float)(Math.sin(a) * 1.6);
            BlockData d = i % 2 == 0 ? main : trim;
            BlockDisplay b = w.spawn(base, BlockDisplay.class, e -> {
                e.setPersistent(false);
                e.setBlock(d);
                e.setBrightness(new Display.Brightness(15, 15));
                // starts far out and high, flies in to its place in the ring
                e.setTransformation(new Transformation(new Vector3f(rx * 5, 6.0F, rz * 5), new Quaternionf().rotateY((float)a), new Vector3f(0.01F), new Quaternionf()));
            });
            parts.add(b);
            int delay = 2 + i;
            Tasks.later(delay, () -> {
                if (b.isValid()) {
                    b.setInterpolationDelay(0);
                    b.setInterpolationDuration(10);
                    b.setTransformation(new Transformation(new Vector3f(rx - 0.18F, 1.0F, rz - 0.18F), new Quaternionf().rotateY((float)a), new Vector3f(0.36F), new Quaternionf()));
                }
            });
        }

        // the wings: two fans of blocks behind the player
        float yaw = (float)Math.toRadians(-base.getYaw());
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 5; k++) {
                float ang = (float)(s * (0.35 + k * 0.28));
                float bx = (float)(Math.sin(yaw + Math.PI) * 0.5 + Math.cos(yaw) * s * (0.5 + k * 0.35));
                float bz = (float)(Math.cos(yaw + Math.PI) * 0.5 - Math.sin(yaw) * s * (0.5 + k * 0.35));
                float by = 1.4F + k * 0.22F;
                BlockData d = k % 2 == 0 ? main : trim;
                BlockDisplay b = w.spawn(base, BlockDisplay.class, e -> {
                    e.setPersistent(false);
                    e.setBlock(d);
                    e.setBrightness(new Display.Brightness(15, 15));
                    e.setTransformation(new Transformation(new Vector3f(0, 1.4F, 0), new Quaternionf().rotateZ(ang), new Vector3f(0.01F), new Quaternionf()));
                });
                parts.add(b);
                Tasks.later(20L + k * 2L, () -> {
                    if (b.isValid()) {
                        b.setInterpolationDelay(0);
                        b.setInterpolationDuration(8);
                        b.setTransformation(new Transformation(new Vector3f(bx, by, bz), new Quaternionf().rotateZ(ang), new Vector3f(0.25F, 0.7F, 0.08F), new Quaternionf()));
                    }
                });
            }
        }

        TextDisplay label = w.spawn(base.clone().add(0.0, 3.0, 0.0), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(true);
            t.text(Text.mm("<b>" + tier.mm() + "</b>\n<white>" + tier.longName()));
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.01F), new Quaternionf()));
        });
        parts.add(label);
        Tasks.later(24L, () -> {
            if (label.isValid()) {
                label.setInterpolationDelay(0);
                label.setInterpolationDuration(8);
                label.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2.4F), new Quaternionf()));
                Snd.at(label.getLocation(), "block.beacon.power_select", 1.0F, 1.2F);
            }
        });
        // burst
        Tasks.later(70L, () -> {
            int rgb = net.cmcpvp.fx.anim.Anim.parseRgb(tier.color, 0xFFFFFF);
            Color c = Color.fromRGB(rgb);
            for (int i = 0; i < 3; i++) {
                int k = i;
                Tasks.later(i * 4L, () -> w.spawnParticle(Particle.DUST, base.clone().add(0.0, 1.5 + k, 0.0), 60, 1.2, 0.6, 1.2, 0.0, new Particle.DustOptions(c, 1.6F)));
            }

            Snd.at(base, "entity.firework_rocket.large_blast", 1.0F, 1.0F);
            for (Entity e : parts) {
                if (e instanceof Display d && e.isValid()) {
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(6);
                    Transformation t = d.getTransformation();
                    d.setTransformation(new Transformation(t.getTranslation().mul(2.5F), t.getLeftRotation(), new Vector3f(0.01F), t.getRightRotation()));
                }
            }
        });
        Tasks.later(80L, () -> {
            for (Entity e : parts) {
                if (e.isValid()) {
                    e.remove();
                }
            }
        });
    }
}
