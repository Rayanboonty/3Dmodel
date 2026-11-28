package net.cmcpvp.fx;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.cmcpvp.CMCPvP;
import net.cmcpvp.data.KitStats;
import net.cmcpvp.data.PvPProfile;
import net.cmcpvp.kit.Kit;
import net.cmcpvp.util.Tasks;
import net.cmcpvp.util.Text;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 1.0.17: the 3D VS card of a match. In front of each player (and only for them) both heads slide in from the sides,
 * each over a plate with the name, the tier, the Elo and the record in the kit; a big "VS" pops in between. It plays
 * while the match-found title runs and is gone when the map vote opens.
 */
public final class VersusCard {
    private static final int LENGTH = 44;

    private VersusCard() {
    }

    private static ItemStack head(Player p) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        it.editMeta(SkullMeta.class, m -> m.setPlayerProfile(p.getPlayerProfile()));
        return it;
    }

    private static String plate(Player p, Kit kit) {
        PvPProfile prof = CMCPvP.get().profiles().getIfLoaded(p.getUniqueId());
        KitStats ks = prof == null ? null : prof.statsOrNull(kit.id);
        String tier = ks == null || ks.tier() == null ? "<gray>Unranked" : ks.tier().mm();
        int elo = ks == null ? 0 : ks.elo;
        int w = ks == null ? 0 : ks.wins + ks.unrankedWins;
        int l = ks == null ? 0 : ks.losses + ks.unrankedLosses;
        return "<white><b>" + Text.esc(p.getName()) + "</b>\n" + tier + " <dark_gray>• <white>" + String.format("%,d", elo) + " <gray>Elo\n<#7CFF7C>" + w + "W <#FF6B6B>" + l + "L";
    }

    /** Plays the card for {@code viewer} against {@code opponent}. */
    public static void play(Player viewer, Player opponent, Kit kit) {
        if (!viewer.isOnline() || opponent == null) {
            return;
        }

        CMCPvP plugin = CMCPvP.get();
        Location eye = viewer.getEyeLocation();
        Vector dir = eye.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }

        dir.normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Location mid = eye.clone().add(dir.clone().multiply(3.2)).add(0.0, 0.25, 0.0);
        mid.setDirection(dir.clone().multiply(-1));
        List<Entity> parts = new ArrayList<>();
        float yaw = mid.getYaw();
        // the two heads: you on the left, the opponent on the right; they start far out and slide in
        Player[] who = {viewer, opponent};
        for (int i = 0; i < 2; i++) {
            int side = i == 0 ? -1 : 1;
            Location at = mid.clone().add(right.clone().multiply(side * 1.15));
            ItemDisplay h = at.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setItemStack(head(who[side < 0 ? 0 : 1]));
                d.setRotation(yaw, 0.0F);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(new Transformation(new Vector3f(side * 2.5F, 0.0F, 0.0F), new Quaternionf(), new Vector3f(0.9F), new Quaternionf()));
            });
            TextDisplay plate = at.getWorld().spawn(at.clone().add(0.0, -0.95, 0.0), TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setVisibleByDefault(false);
                t.setBillboard(Display.Billboard.FIXED);
                t.setRotation(yaw, 0.0F);
                t.setBackgroundColor(Color.fromARGB(190, 20, 6, 8));
                t.setLineWidth(160);
                t.text(Text.mm(plate(who[side < 0 ? 0 : 1], kit)));
                t.setTransformation(new Transformation(new Vector3f(side * 2.5F, 0.0F, 0.0F), new Quaternionf(), new Vector3f(0.5F), new Quaternionf()));
            });
            parts.add(h);
            parts.add(plate);
        }

        TextDisplay vs = mid.getWorld().spawn(mid.clone().add(0.0, -0.1, 0.0), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.setBillboard(Display.Billboard.FIXED);
            t.setRotation(yaw, 0.0F);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(true);
            t.text(Text.mm("<b><white>V</white><#E3172B>S</#E3172B></b>"));
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.01F), new Quaternionf()));
        });
        TextDisplay kitLine = mid.getWorld().spawn(mid.clone().add(0.0, -1.05, 0.0), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.setBillboard(Display.Billboard.FIXED);
            t.setRotation(yaw, 0.0F);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.text(kit.iconComponent().append(Text.mm(" " + kit.color + "<b>" + Text.esc(kit.displayName))));
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.6F), new Quaternionf()));
        });
        parts.add(vs);
        parts.add(kitLine);
        for (Entity e : parts) {
            viewer.showEntity(plugin, e);
        }

        // slide the heads and plates in, then pop the VS
        Tasks.later(2L, () -> {
            for (Entity e : parts) {
                if (e instanceof Display d && e != vs && e != kitLine) {
                    Transformation t = d.getTransformation();
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(10);
                    d.setTransformation(new Transformation(new Vector3f(), t.getLeftRotation(), t.getScale(), t.getRightRotation()));
                }
            }
        });
        Tasks.later(11L, () -> {
            if (vs.isValid()) {
                vs.setInterpolationDelay(0);
                vs.setInterpolationDuration(4);
                vs.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2.6F), new Quaternionf()));
            }
        });
        Tasks.later(16L, () -> {
            if (vs.isValid()) {
                vs.setInterpolationDelay(0);
                vs.setInterpolationDuration(6);
                vs.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f(0.0F, 0, 1, 0)), new Vector3f(2.0F), new Quaternionf()));
            }
        });
        // a slow turn of the heads, then everything shrinks away
        Tasks.later(LENGTH - 8L, () -> {
            for (Entity e : parts) {
                if (e instanceof Display d) {
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(7);
                    Transformation t = d.getTransformation();
                    d.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(), new Vector3f(0.01F), t.getRightRotation()));
                }
            }
        });
        Tasks.later(LENGTH, () -> {
            for (Entity e : parts) {
                if (e.isValid()) {
                    e.remove();
                }
            }
        });
    }
}
