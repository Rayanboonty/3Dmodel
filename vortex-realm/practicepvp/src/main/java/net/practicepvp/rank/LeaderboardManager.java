package net.practicepvp.rank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.practicepvp.PracticePlugin;
import net.practicepvp.data.Database;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.kit.Kit;
import net.practicepvp.util.Tasks;

public final class LeaderboardManager {
    public static final int SIZE = 100;
    private final PracticePlugin plugin;
    private volatile Map<String, List<Database.LbEntry>> perKit = new HashMap<>();
    private volatile List<Database.LbEntry> overall = new ArrayList<>();
    private long lastRefresh;

    public LeaderboardManager(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public void refresh() {
        for (PvPProfile p : this.plugin.profiles().loaded()) {
            if (p.dirty) {
                this.plugin.profiles().save(p);
            }
        }

        List<String> kits = new ArrayList<>();

        for (Kit k : this.plugin.kits().all()) {
            kits.add(k.id);
        }

        this.plugin.db().supply(c -> {
            Map<String, List<Database.LbEntry>> map = new HashMap<>();

            for (String kx : kits) {
                map.put(kx, this.plugin.db().top(c, kx, 100));
            }

            List<Database.LbEntry> all = this.plugin.db().topOverall(c, 100);
            return new Object[]{map, all};
        }).thenAccept(res -> Tasks.ensureSync(() -> {
            Map<String, List<Database.LbEntry>> map = (Map<String, List<Database.LbEntry>>)res[0];
            List<Database.LbEntry> all = (List<Database.LbEntry>)res[1];
            if (this.plugin.hackerPool() != null) {
                Predicate<Database.LbEntry> pooled = ex -> this.plugin.hackerPool().pooled(ex.uuid());

                for (Entry<String, List<Database.LbEntry>> e : map.entrySet()) {
                    List<Database.LbEntry> l = new ArrayList<>(e.getValue());
                    l.removeIf(pooled);
                    e.setValue(l);
                }

                all = new ArrayList<>(all);
                all.removeIf(pooled);
            }

            this.perKit = map;
            this.overall = all;
            this.lastRefresh = System.currentTimeMillis();
        }));
    }

    public List<Database.LbEntry> top(String kitId) {
        return this.perKit.getOrDefault(kitId, List.of());
    }

    public List<Database.LbEntry> overall() {
        return this.overall;
    }

    public int position(String kitId, UUID uuid) {
        List<Database.LbEntry> l = this.top(kitId);

        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).uuid().equals(uuid)) {
                return i + 1;
            }
        }

        return -1;
    }

    public long lastRefresh() {
        return this.lastRefresh;
    }
}
