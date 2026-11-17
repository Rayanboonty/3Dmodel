package net.vortexrealm.match;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class Penalties implements Listener {
    private final VortexRealm plugin;
    private final File file;
    private final Map<UUID, Long> rankedBan = new HashMap<>();
    private final Map<UUID, Integer> warnings = new HashMap<>();
    private final Map<UUID, String> notice = new HashMap<>();

    public Penalties(VortexRealm plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "penalties.yml");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        long now = System.currentTimeMillis();

        for (String k : y.getKeys(false)) {
            try {
                UUID u = UUID.fromString(k);
                int w = y.getInt(k + ".warnings");
                if (w > 0) {
                    this.warnings.put(u, w);
                }

                String n = y.getString(k + ".notice");
                if (n != null) {
                    this.notice.put(u, n);
                }
            } catch (IllegalArgumentException var9) {
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        long now = System.currentTimeMillis();
        this.rankedBan.values().removeIf(t -> t <= now);
        Set<UUID> all = new HashSet<>(this.rankedBan.keySet());
        all.addAll(this.warnings.keySet());
        all.addAll(this.notice.keySet());

        for (UUID u : all) {
            if (this.rankedBan.containsKey(u)) {
                y.set(u + ".ranked-ban-until", this.rankedBan.get(u));
            }

            if (this.warnings.containsKey(u)) {
                y.set(u + ".warnings", this.warnings.get(u));
            }

            if (this.notice.containsKey(u)) {
                y.set(u + ".notice", this.notice.get(u));
            }
        }

        try {
            y.save(this.file);
        } catch (IOException var7) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save penalties.yml", (Throwable)var7);
        }
    }

    public void leftMatch(Player p, boolean ranked, String opponent) {
        UUID u = p.getUniqueId();
        int w = this.warnings.merge(u, 1, Integer::sum);
        this.notice
            .put(
                u,
                "<yellow><b>WARNING</b> <gray>You left a "
                    + (ranked ? "ranked match" : "duel")
                    + (opponent == null ? "" : " against <white>" + Text.esc(opponent) + "</white>")
                    + ". It counted as a loss - please finish your matches. <dark_gray>("
                    + w
                    + " warning"
                    + (w == 1 ? "" : "s")
                    + ")"
            );
        this.save();
    }

    public long rankedBanMinutes(Player p) {
        Long until = this.rankedBan.get(p.getUniqueId());
        long left = until == null ? 0L : until - System.currentTimeMillis();
        return left <= 0L ? 0L : left / 60000L + 1L;
    }

    public boolean checkRanked(Player p) {
        long m = this.rankedBanMinutes(p);
        if (m <= 0L) {
            return true;
        } else {
            p.sendMessage(Text.mm("<red>You left a ranked match - ranked unlocks in <gold>" + m + " minute" + (m == 1L ? "" : "s") + "</gold>."));
            Snd.error(p);
            return false;
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String n = this.notice.remove(p.getUniqueId());
        if (n != null) {
            this.save();
            Tasks.later(60L, () -> {
                if (p.isOnline()) {
                    p.sendMessage(Text.mm(n));
                    Snd.play(p, "block.note_block.bass", 1.0F, 0.7F);
                }
            });
        }
    }
}
