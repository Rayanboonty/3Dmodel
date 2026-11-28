package net.vortexpvp.bot;

import java.util.Locale;

/** How good a practice bot is: reaction time, aim, how often it lands hits and which techniques it uses. */
public enum Difficulty {
    EASY("<green>Easy", 8, 12.0, 0.7, 1, "Simple melee, no shield"),
    MEDIUM("<yellow>Medium", 5, 6.0, 0.85, 2, "Crits and shield"),
    HARD("<gold>Hard", 3, 3.0, 0.95, 3, "Every technique of the kit"),
    EXPERT("<red>Expert", 1, 1.0, 1.0, 4, "Everything, with combos");

    public final String label;
    /** Ticks before it reacts to a change. */
    public final int reaction;
    /** Aim error in degrees. */
    public final double aimError;
    /** Chance an attack in reach actually lands. */
    public final double accuracy;
    /** 1 = basics, 2 = crits/shield, 3 = kit techniques, 4 = everything. */
    public final int skill;
    public final String about;

    Difficulty(String label, int reaction, double aimError, double accuracy, int skill, String about) {
        this.label = label;
        this.reaction = reaction;
        this.aimError = aimError;
        this.accuracy = accuracy;
        this.skill = skill;
        this.about = about;
    }

    public static Difficulty parse(String s) {
        if (s == null) {
            return MEDIUM;
        } else {
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var2) {
                return MEDIUM;
            }
        }
    }
}
