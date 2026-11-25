package net.practicepvp.rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.data.KitStats;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.kit.Kit;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Rank decay (PvPHQ style). A kit at or above ranking.decay-from-tier with no ranked match for
 * ranking.decay-after-days loses ranking.decay-per-week points for every week after that. It is worked out from the
 * time of the last ranked match, so an offline player's decay lands when they come back. Decay never takes a tier
 * away: points stop at the tier's own threshold.
 */
public final class Decay implements Listener {
    private static final long DAY = 86_400_000L;
    private static final long WEEK = 7L * DAY;
    private final PracticePlugin plugin;

    public Decay(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Tasks.timer(2400L, 36000L, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                this.apply(p, false);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Tasks.later(100L, () -> {
            if (p.isOnline()) {
                this.apply(p, true);
            }
        });
    }

    /** Applies the decay due for this player; tells them what changed. */
    public void apply(Player p, boolean joined) {
        PvPProfile prof = this.plugin.profiles().getIfLoaded(p.getUniqueId());
        if (prof == null || !prof.loadedOk) {
            return;
        }

        List<String> lost = new ArrayList<>();
        for (Entry<String, KitStats> e : prof.kits.entrySet()) {
            Kit kit = this.plugin.kits().get(e.getKey());
            int n = this.decay(prof, e.getValue());
            if (n > 0 && kit != null) {
                lost.add(kit.color + Text.esc(kit.displayName) + " <#E3172B>-" + n + "</#E3172B>");
            }
        }

        if (!lost.isEmpty()) {
            p.sendMessage(Text.mm("<#E3172B>⌛ Rank decay</#E3172B> <gray>(no ranked games) " + String.join("<dark_gray>, ", lost)
                + "<gray>. Play ranked in a kit to stop it."));
        }
    }

    /** Points taken now from one kit (0 = none). */
    int decay(PvPProfile prof, KitStats k) {
        long now = System.currentTimeMillis();
        if (k.lastRanked <= 0) {
            // stats from before decay existed: the clock starts now
            if (k.tier >= 0) {
                k.lastRanked = now;
                prof.dirty = true;
            }

            return 0;
        }

        Tier t = k.tier();
        Tier from = Cfg.e(Opt.DECAY_FROM, Tier.class);
        if (!Cfg.bool(Opt.DECAY) || t == null || from != null && t.ordinal() < from.ordinal()) {
            return 0;
        }

        long start = k.lastRanked + Cfg.i(Opt.DECAY_AFTER_DAYS) * DAY;
        if (now < start) {
            return 0;
        }

        long due = (now - start) / WEEK + 1;
        long done = k.lastDecay < start ? 0 : (k.lastDecay - start) / WEEK + 1;
        long steps = due - done;
        if (steps <= 0) {
            return 0;
        }

        int floor = t.threshold();
        int before = k.elo;
        k.elo = (int)Math.max(Math.min(k.elo, floor), k.elo - steps * Cfg.i(Opt.DECAY_PER_WEEK));
        k.lastDecay = now;
        prof.dirty = true;
        return before - k.elo;
    }
}
