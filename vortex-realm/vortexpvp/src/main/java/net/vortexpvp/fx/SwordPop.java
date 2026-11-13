package net.vortexpvp.fx;

import net.vortexpvp.VortexPvP;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SwordPop {
    private SwordPop() {
    }

    private static Transformation scale(float s, float spin) {
        return new Transformation(new Vector3f(), new Quaternionf().rotateZ(spin), new Vector3f(s, s, s), new Quaternionf());
    }

    private static Location front(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        return eye.add(dir.multiply(1.9)).add(0.0, -0.05, 0.0);
    }

    public static void play(VortexPvP plugin, Player p, ItemStack item) {
        play(plugin, p, item, 1.25F);
    }

    /**
     * Medium size for a kit icon: items whose model is a big 3D model (end crystal, shield, trident, mace, heads...)
     * are drawn smaller so they look the same size as a sword.
     */
    public static float iconSize(ItemStack item) {
        String n = item.getType().name();
        boolean big = n.equals("END_CRYSTAL")
            || n.equals("SHIELD")
            || n.equals("TRIDENT")
            || n.equals("MACE")
            || n.equals("CONDUIT")
            || n.equals("DECORATED_POT")
            || n.endsWith("_HEAD")
            || n.endsWith("_SKULL")
            || n.endsWith("_BANNER")
            || n.endsWith("CHEST")
            || n.endsWith("_BED");
        return big ? 0.5F : 0.72F;
    }

    /** {@code hold} = size the item settles at (1.25 = the big sword pop, about 0.7 = medium). */
    public static void play(VortexPvP plugin, Player p, ItemStack item, float hold) {
        if (p.isOnline()) {
            ItemDisplay d;
            try {
                d = p.getWorld().spawn(front(p), ItemDisplay.class, e -> {
                    e.setItemStack(item.clone());
                    e.setItemDisplayTransform(ItemDisplayTransform.GUI);
                    e.setBillboard(Billboard.CENTER);
                    e.setBrightness(new Brightness(15, 15));
                    e.setTransformation(scale(0.01F, 0.0F));
                    e.setVisibleByDefault(false);
                    e.setPersistent(false);
                    e.setTeleportDuration(2);
                    e.setViewRange(0.5F);
                });
            } catch (RuntimeException var6) {
                return;
            }

            p.showEntity(plugin, d);
            Snd.play(p, "entity.player.attack.sweep", 0.9F, 0.8F);
            Snd.play(p, "item.armor.equip_netherite", 1.0F, 1.2F);
            int[] t = new int[]{0};
            BukkitTask[] task = new BukkitTask[1];
            task[0] = Tasks.timer(1L, 1L, () -> {
                t[0]++;
                if (p.isOnline() && d.isValid() && p.getWorld().equals(d.getWorld()) && t[0] <= 40) {
                    d.teleport(front(p));
                    switch (t[0]) {
                        case 1:
                            anim(d, 5, scale(hold * 1.24F, 0.35F));
                            break;
                        case 6:
                            anim(d, 5, scale(hold * 0.92F, -0.08F));
                            break;
                        case 11:
                            anim(d, 16, scale(hold, 0.06F));
                            break;
                        case 12:
                            Snd.play(p, "block.note_block.chime", 0.7F, 1.6F);
                            break;
                        case 28:
                            anim(d, 7, scale(0.01F, 0.9F));
                    }
                } else {
                    d.remove();
                    Tasks.cancel(task[0]);
                }
            });
        }
    }

    private static void anim(Display d, int ticks, Transformation t) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(t);
    }
}
