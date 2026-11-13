package net.vortexpvp.bot;

import java.util.Locale;
import org.bukkit.Material;

/** Training dummies: bots that don't fight back but let you practise one skill, with live stats over their head. */
public enum DummyType {
    SHIELD("Shield Dummy", Material.SHIELD, "axe", "Holds its shield up and turns to you.", "Break it with an axe, then follow up (stun slam)."),
    CRYSTAL("Crystal Dummy", Material.END_CRYSTAL, "crystal", "Netherite armor, totems refilled.", "Practise fast obsidian + crystal placing."),
    COMBO("Combo Dummy", Material.DIAMOND_SWORD, "sword", "Strafes left and right.", "Practise combos, crits and w-tapping."),
    MACE("Mace Dummy", Material.MACE, "mace", "Stands still under the sky.", "Practise smash height and timing (wind charges)."),
    ELYTRA("Elytra Target", Material.ELYTRA, "mace", "Floats in slow circles.", "Practise elytra dives and air hits.");

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

    public static DummyType parse(String s) {
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException var2) {
            return null;
        }
    }
}
