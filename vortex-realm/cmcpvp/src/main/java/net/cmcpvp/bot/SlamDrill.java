package net.cmcpvp.bot;

import java.util.Locale;
import org.bukkit.Material;

/**
 * 1.0.16: the drills of the Stun Slam Lab. Each one places shield dummies (and the platforms or pillars they stand on)
 * in the player's cell of the flat training world, so every kind of stun slam can be practised: on the ground, from
 * below onto a dummy above, from ledge to ledge, two at once, in the air, on a gliding target, after a spear lunge, or a
 * new random layout after every slam.
 */
public enum SlamDrill {
    GROUND("Ground Slam", Material.SHIELD, "One shield dummy on the floor.", "Axe the shield, wind-charge up, slam it with the mace."),
    UPPERCUT("Uppercut", Material.GLASS, "A dummy on a platform 5 blocks above you, at an angle.",
        "Break its shield from below, launch up past it, then slam down onto it."),
    LEDGES("Ledge Run", Material.STONE_BRICK_WALL, "Three dummies on pillars of different heights round you.",
        "Slam one, wind-charge to the next ledge, slam again - all three."),
    DOUBLE("Double Slam", Material.MACE, "Two shield dummies side by side.", "Break one shield, slam so the smash hits both."),
    AIR("Mid-Air", Material.WIND_CHARGE, "A shield dummy floating 7 blocks up.", "Launch, break the shield in the air, slam it."),
    ELYTRA("Elytra Flyer", Material.ELYTRA, "A dummy gliding in circles with a shield.", "Fly up, break the shield and slam it mid-flight."),
    SPEAR("Spear Lunge", Material.TRIDENT, "A shield dummy for the spear.", "Break the shield with a spear lunge, then mace it in the same fall."),
    RANDOM("Random Lab", Material.NETHER_STAR, "1 to 3 dummies at random heights and sides.", "Every slam builds a new layout: read it and react.");

    public final String display;
    public final Material icon;
    public final String what;
    public final String goal;

    SlamDrill(String display, Material icon, String what, String goal) {
        this.display = display;
        this.icon = icon;
        this.what = what;
        this.goal = goal;
    }

    /** The dummy used for this drill's targets. */
    public DummyType dummy() {
        return switch (this) {
            case AIR -> DummyType.AIR_SLAM;
            case ELYTRA -> DummyType.ELYTRA_SHIELD;
            case SPEAR -> DummyType.SPEAR_SLAM;
            default -> DummyType.STUNSLAM;
        };
    }

    public static SlamDrill parse(String s) {
        if (s == null) {
            return null;
        }

        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT).replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
