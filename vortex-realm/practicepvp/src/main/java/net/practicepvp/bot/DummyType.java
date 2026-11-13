package net.practicepvp.bot;

import java.util.Locale;
import org.bukkit.Material;

/** Training dummies: bots that don't fight back but let you practise one skill, with live stats over their head. */
public enum DummyType {
    SHIELD("Shield Dummy", Material.SHIELD, "axe", "Holds its shield up and turns to you.", "Break it with an axe, then follow up (stun slam)."),
    CRYSTAL("Crystal Dummy", Material.END_CRYSTAL, "crystal", "Netherite armor, totems refilled.", "Practise fast obsidian + crystal placing."),
    COMBO("Combo Dummy", Material.DIAMOND_SWORD, "sword", "Strafes left and right.", "Practise combos, crits and w-tapping."),
    MACE("Mace Dummy", Material.MACE, "mace", "Stands still under the sky.", "Practise smash height and timing (wind charges)."),
    ELYTRA("Elytra Target", Material.ELYTRA, "mace", "Floats in slow circles.", "Practise elytra dives and air hits."),
    DTAP("D-Tap Dummy", Material.TOTEM_OF_UNDYING, "crystal", "Netherite armor, a new totem right after every pop.", "Pop its totem twice in a row as fast as you can (two crystals back to back)."),
    ANCHOR("Anchor Dummy", Material.RESPAWN_ANCHOR, "crystal", "Netherite armor, totems refilled. You get anchors and glowstone.", "Place an anchor, charge it with glowstone and set it off next to the dummy."),
    STUNSLAM("Stun Slam Dummy", Material.MACE, "mace", "Holds its shield up and turns to you.", "In one fall: break the shield with your axe, then smash with the mace."),
    PEARL("Pearl Target", Material.ENDER_PEARL, "nethpot", "Runs from side to side.", "Land your ender pearls right next to it."),
    POT("Pot Refill Dummy", Material.SPLASH_POTION, "nodebuff", "Hits you for a little damage all the time.", "Stay alive: heal with splash potions at your feet at the right moment."),
    SPEAR("Spear Dummy", Material.DIAMOND_SWORD, "spear", "Stands still.", "Practise the spear charge: hit it at full speed for the most damage."),
    AIR_SLAM("Mid-Air Stun Slam", Material.WIND_CHARGE, "mace", "Hovers 7 blocks up with its shield raised.", "Wind-charge up, break the shield with your axe in the air, then slam it with the mace."),
    ELYTRA_SHIELD("Elytra Shield Flyer", Material.ELYTRA, "mace", "Glides in circles with an elytra, holding up a shield.", "Get up to it, break the shield and mace-slam it in the air."),
    SPEAR_SLAM("Spear Stun Slam", Material.SHIELD, "spear_mace", "Holds its shield up and turns to you.", "Break the shield with a spear lunge, then smash it with the mace in the same fall."),
    SPEAR_MACE("Spear + Mace Combo", Material.MACE, "spear_mace", "Stands still.", "Lunge into it with the spear, then hit it with the mace within 2 seconds."),
    SPEAR_LUNGE("Lunge Target", Material.TARGET, "spear", "Strafes from side to side.", "Hit it with a full-speed spear lunge from as far as you can."),
    WTAP("W-Tap Target", Material.LEATHER_BOOTS, "sword", "Backs away from you.", "Keep sprint-hitting it: every hit after a sprint reset counts."),
    CRIT("Crit Dummy", Material.IRON_SWORD, "sword", "Stands still.", "Land critical hits: jump, then hit it while you fall."),
    BOW("Archery Target", Material.BOW, "bow", "Walks from side to side, far away.", "Hit it with arrows - the longer the shot, the better.");

    public final String display;
    public final Material icon;
    /** Kit given to the player to practise with. */
    public final String kit;
    public final String what;
    public final String goal;

    DummyType(String display, Material icon, String kit, String what, String goal) {
        this.display = display;
        this.icon = icon;
        this.kit = kit;
        this.what = what;
        this.goal = goal;
    }

    /** Dummies that never move: they are put back on their spot every tick, whatever hits them. */
    public boolean still() {
        return switch (this) {
            case SHIELD, STUNSLAM, MACE, CRYSTAL, DTAP, ANCHOR, POT, SPEAR, SPEAR_SLAM, SPEAR_MACE, CRIT, AIR_SLAM -> true;
            default -> false;
        };
    }

    /** Dummies that hold a raised shield. */
    public boolean shield() {
        return this == SHIELD || this == STUNSLAM || this == AIR_SLAM || this == ELYTRA_SHIELD || this == SPEAR_SLAM;
    }

    /** Dummies up in the air (no gravity). */
    public boolean floating() {
        return this == ELYTRA || this == AIR_SLAM || this == ELYTRA_SHIELD;
    }

    /** Groups of the training menu. */
    public String group() {
        return switch (this) {
            case CRYSTAL, DTAP, ANCHOR -> "CRYSTAL";
            case MACE, ELYTRA, AIR_SLAM, ELYTRA_SHIELD, STUNSLAM, SHIELD -> "AIR";
            case SPEAR, SPEAR_SLAM, SPEAR_MACE, SPEAR_LUNGE -> "SPEAR";
            default -> "SWORD";
        };
    }

    /** Dummies for crystal PvP: netherite with blast protection and a new totem after every pop. */
    public boolean crystalLike() {
        return this == CRYSTAL || this == DTAP || this == ANCHOR;
    }

    public static DummyType parse(String s) {
        if (s != null) {
            String k = s.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
            for (DummyType t : values()) {
                if (t.name().replace("_", "").equals(k)) {
                    return t;
                }
            }

            switch (k) {
                case "DOUBLETAP":
                    return DTAP;
                case "SLAM":
                case "STUN":
                    return STUNSLAM;
                case "WIND":
                case "SMASH":
                    return MACE;
                case "POTS":
                case "REFILL":
                    return POT;
                case "PEARLS":
                    return PEARL;
                case "AIRSLAM":
                case "AIRSTUN":
                    return AIR_SLAM;
                case "ELYTRASHIELD":
                case "FLYER":
                    return ELYTRA_SHIELD;
                case "SPEARSLAM":
                case "SPEARSTUN":
                    return SPEAR_SLAM;
                case "SPEARMACE":
                    return SPEAR_MACE;
                case "LUNGE":
                case "SPEARLUNGE":
                    return SPEAR_LUNGE;
                case "WTAP":
                case "WTAPPING":
                    return WTAP;
                case "CRITS":
                    return CRIT;
                case "ARCHERY":
                case "ARROWS":
                    return BOW;
                default:
            }
        }

        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException var2) {
            return null;
        }
    }
}
