package net.practicepvp.bot;

import java.util.Locale;

/**
 * How good a practice bot is (1.0.16: six levels matched to the tiers). A bot sees the player as they were
 * {@code reaction} ticks ago, turns its head at most {@code turn} degrees a tick, can only hit what its eye-ray
 * touches within {@code reach} blocks (never more than vanilla's 3), clicks up to {@code cps} times a second, and
 * uses w-taps, jump-resets, strafes and combo play as often as its level allows.
 */
public enum Difficulty {
    BEGINNER("<gray>Beginner", 8, 9.0, 0.6, 1, "LT5: learns the basics", 2.8, 45.0, 6, 0.0, 0.0, 0.25),
    EASY("<green>Easy", 6, 6.5, 0.7, 1, "HT5 - LT4: simple melee", 2.85, 60.0, 8, 0.15, 0.1, 0.45),
    MEDIUM("<yellow>Medium", 4, 4.5, 0.85, 2, "HT4 - LT3: crits, w-taps and shield", 2.9, 90.0, 10, 0.45, 0.35, 0.65),
    HARD("<gold>Hard", 3, 3.0, 0.95, 3, "HT3 - LT2: every technique of the kit", 2.95, 120.0, 12, 0.7, 0.6, 0.8),
    EXPERT("<red>Expert", 2, 1.8, 1.0, 4, "HT2 - LT1: combos and resets", 3.0, 160.0, 14, 0.85, 0.8, 0.9),
    PRO("<dark_red><b>Pro</b>", 1, 1.0, 1.0, 4, "HT1: tournament level", 3.0, 180.0, 16, 0.95, 0.92, 1.0);

    public final String label;
    /** Ticks behind what the bot sees (its reaction time). */
    public final int reaction;
    /** Size of its (slowly drifting) aim error, in degrees. */
    public final double aimError;
    /** Chance a technique that isn't a melee hit (a bow shot, a lunge) is done right. */
    public final double accuracy;
    /** 1 = basics, 2 = crits/shield, 3 = kit techniques, 4 = everything. */
    public final int skill;
    public final String about;
    /** Eye-to-hitbox reach in blocks. */
    public final double reach;
    /** Most degrees its head turns in one tick. */
    public final double turn;
    /** Clicks per second at most. */
    public final int cps;
    /** Chance of a w-tap / s-tap reset after a hit. */
    public final double wtap;
    /** Chance of a jump-reset when it gets hit on the ground. */
    public final double jumpReset;
    /** How well it strafes and keeps its distance (0..1). */
    public final double strafe;

    Difficulty(String label, int reaction, double aimError, double accuracy, int skill, String about, double reach, double turn, int cps, double wtap,
        double jumpReset, double strafe) {
        this.label = label;
        this.reaction = reaction;
        this.aimError = aimError;
        this.accuracy = accuracy;
        this.skill = skill;
        this.about = about;
        this.reach = Math.min(3.0, reach);
        this.turn = turn;
        this.cps = cps;
        this.wtap = wtap;
        this.jumpReset = jumpReset;
        this.strafe = strafe;
    }

    /** The level that matches a tier (LT5 = Beginner ... HT1 = Pro); null tier = Easy. */
    public static Difficulty forTier(net.practicepvp.rank.Tier t) {
        if (t == null) {
            return EASY;
        }

        int n = t.number();
        boolean high = t.step() == 2;
        return switch (n) {
            case 5 -> high ? EASY : BEGINNER;
            case 4 -> high ? MEDIUM : EASY;
            case 3 -> high ? HARD : MEDIUM;
            case 2 -> high ? EXPERT : HARD;
            default -> high ? PRO : EXPERT;
        };
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
