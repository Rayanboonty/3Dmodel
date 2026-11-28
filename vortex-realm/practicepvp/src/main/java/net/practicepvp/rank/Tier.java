package net.practicepvp.rank;

import java.util.List;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;

public enum Tier {
    LT5("LT5", "#9E9E9E"),
    MT5("MT5", "#B4B4B4"),
    HT5("HT5", "#D0D0D0"),
    LT4("LT4", "#8BE08B"),
    MT4("MT4", "#62D962"),
    HT4("HT4", "#3BE13B"),
    LT3("LT3", "#8FDCFF"),
    MT3("MT3", "#55C8FF"),
    HT3("HT3", "#2E9BFF"),
    LT2("LT2", "#DDB0FF"),
    MT2("MT2", "#C77DFF"),
    HT2("HT2", "#A64DFF"),
    LT1("LT1", "#FFE58A"),
    MT1("MT1", "#FFD54A"),
    HT1("HT1", "#FFAA00");

    public final String label;
    public final String color;

    private Tier(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public int number() {
        return 5 - this.ordinal() / 3;
    }

    public int step() {
        return this.ordinal() % 3;
    }

    public String longName() {
        return (this.step() == 0 ? "Low" : (this.step() == 1 ? "Mid" : "High")) + " Tier " + this.number();
    }

    public int threshold() {
        List<Integer> t = Cfg.get(Opt.TIER_THRESHOLDS);
        return this.ordinal() < t.size() ? t.get(this.ordinal()) : t.get(t.size() - 1) + (this.ordinal() - t.size() + 1) * 500;
    }

    public Tier next() {
        return this.ordinal() + 1 < values().length ? values()[this.ordinal() + 1] : null;
    }

    public Tier prev() {
        return this.ordinal() > 0 ? values()[this.ordinal() - 1] : null;
    }

    /** MCTiers points of a peak tier: HT1 60, LT1 45, HT2 30, LT2 20, HT3 10, LT3 6, HT4 4, LT4 3, HT5 2, LT5 1 (MT counts as LT). */
    public int points() {
        boolean high = this.step() == 2;
        return switch (this.number()) {
            case 1 -> high ? 60 : 45;
            case 2 -> high ? 30 : 20;
            case 3 -> high ? 10 : 6;
            case 4 -> high ? 4 : 3;
            default -> high ? 2 : 1;
        };
    }

    /** The overall title of a points total (sum of every kit's peak-tier points). */
    public static String title(int points) {
        if (points >= 400) {
            return "Combat Grandmaster";
        } else if (points >= 250) {
            return "Combat Master";
        } else if (points >= 100) {
            return "Combat Ace";
        } else if (points >= 50) {
            return "Combat Specialist";
        } else if (points >= 10) {
            return "Combat Cadet";
        } else {
            return points > 0 ? "Combat Novice" : "Unranked";
        }
    }

    public static String titleColor(int points) {
        return points >= 400 ? "#FFAA00" : points >= 250 ? "#E3172B" : points >= 100 ? "#C77DFF" : points >= 50 ? "#2E9BFF" : points >= 10 ? "#3BE13B" : "#B4B4B4";
    }

    public static Tier forElo(int elo) {
        Tier result = LT5;

        for (Tier t : values()) {
            if (elo >= t.threshold()) {
                result = t;
            }
        }

        return result;
    }

    public static Tier byIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : null;
    }

    public static int fromOldIndex(int old) {
        return old < 0 ? old : Math.min(values().length - 1, old / 2 * 3 + old % 2 * 2);
    }

    public String mm() {
        boolean top = this.ordinal() >= LT1.ordinal();
        return "<" + this.color + ">" + (top ? "<b>" : "") + this.label + (top ? "</b>" : "") + "</" + this.color + ">";
    }
}
