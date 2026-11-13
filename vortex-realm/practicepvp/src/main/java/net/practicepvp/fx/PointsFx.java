package net.practicepvp.fx;

import net.practicepvp.PracticePlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class PointsFx {
    private PointsFx() {
    }

    public static void show(Player p, int delta) {
        show(p, delta, null);
    }

    /** Rank points in front of {@code p}, kept clear of {@code avoid} (the other player). */
    public static void show(Player p, int delta, Location avoid) {
        if (delta != 0) {
            XpOrbText.inFront(PracticePlugin.get(), p, (delta > 0 ? "+" : "-") + Math.abs(delta), avoid);
        }
    }

    public static void show(Player p, String text, boolean won) {
        XpOrbText.inFront(PracticePlugin.get(), p, text);
    }
}
