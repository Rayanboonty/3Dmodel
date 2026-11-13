package net.practicepvp.match;

import java.util.List;
import net.practicepvp.PracticePlugin;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Mole PvP rules, for every fight whose kit plays on the MOLE pool: a sonar ping every 20 seconds tells each player
 * roughly where the nearest enemy is (so nobody can hide forever), and after 8 minutes sudden death starts: everyone
 * glows and the sonar pings every 5 seconds.
 */
public final class MoleRules {
    private static final int SONAR = 20;
    private static final int SUDDEN = 480;
    private final PracticePlugin plugin;

    public MoleRules(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean mole(Match m) {
        return m.kit != null && m.kit.playsOn("MOLE");
    }

    /** Every second. */
    public void tick() {
        for (Match m : this.plugin.matches().active()) {
            if (mole(m) && m.state() == Match.State.FIGHTING) {
                long t = m.fightSeconds();
                boolean sudden = t >= SUDDEN;
                if (t == SUDDEN) {
                    for (Player p : m.alivePlayers()) {
                        p.showTitle(net.kyori.adventure.title.Title.title(Text.mm("<red><b>SUDDEN DEATH"), Text.mm("<gray>Everyone glows - the sonar pings every 5s")));
                        Snd.play(p, "entity.wither.spawn", 0.5F, 1.4F);
                    }
                }

                if (sudden) {
                    for (Player p : m.alivePlayers()) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
                    }
                }

                if (t > 0 && t % (sudden ? 5 : SONAR) == 0) {
                    this.ping(m);
                }
            }
        }
    }

    private void ping(Match m) {
        List<Player> alive = m.alivePlayers();

        for (Player p : alive) {
            Player near = null;
            double best = Double.MAX_VALUE;

            for (Player o : alive) {
                if (!o.equals(p) && m.teamOf(o.getUniqueId()) != m.teamOf(p.getUniqueId()) && o.getWorld().equals(p.getWorld())) {
                    double d = o.getLocation().distanceSquared(p.getLocation());
                    if (d < best) {
                        best = d;
                        near = o;
                    }
                }
            }

            if (near != null) {
                p.sendActionBar(Text.mm("<#C9A27E>⛏ Sonar: <white>" + describe(p.getLocation(), near.getLocation())));
                Snd.play(p, "block.note_block.chime", 0.8F, 0.6F);
            }
        }
    }

    /** "~40 blocks north-east, 12 below" (rounded so it helps without giving the exact spot). */
    static String describe(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        int dist = (int)Math.round(Math.sqrt(dx * dx + dz * dz) / 5.0) * 5;
        int dy = (int)Math.round(to.getY() - from.getY());
        String[] dirs = new String[]{"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        double ang = Math.toDegrees(Math.atan2(-dx, dz));
        int i = Math.floorMod((int)Math.round(ang / 45.0), 8);
        String level = Math.abs(dy) < 3 ? "same level" : Math.abs(dy) + (dy > 0 ? " above" : " below");
        return "~" + Math.max(5, dist) + " blocks " + dirs[i] + ", " + level;
    }
}
