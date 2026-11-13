package net.vortexrealm.data;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.rank.Tier;
import net.vortexrealm.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class RankBackup {
    private static final int KEEP = 20;
    private final VortexRealm plugin;
    private final File dir;

    public RankBackup(VortexRealm plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "backups");
    }

    public File dir() {
        return this.dir;
    }

    public List<String> files() {
        File[] fs = this.dir.listFiles((d, n) -> n.startsWith("ranks-") && n.endsWith(".yml"));
        if (fs == null) {
            return List.of();
        } else {
            Arrays.sort(fs, Comparator.comparing(File::getName).reversed());
            List<String> out = new ArrayList<>();

            for (File f : fs) {
                out.add(f.getName());
            }

            return out;
        }
    }

    public CompletableFuture<Object[]> backup() {
        for (PvPProfile p : this.plugin.profiles().loaded()) {
            if (p.dirty) {
                this.plugin.profiles().save(p);
            }
        }

        String name = "ranks-" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".yml";
        return this.plugin.db().supply(c -> {
            Map<UUID, Object[]> all = this.plugin.db().allRanks(c);
            YamlConfiguration y = new YamlConfiguration();
            y.options().setHeader(List.of("VortexRealm rank backup. Restore with /pvpadmin restore " + name));
            y.set("version", 2);
            y.set("created", System.currentTimeMillis());
            int players = 0;

            for (Entry<UUID, Object[]> e : all.entrySet()) {
                Map<String, KitStats> kits = (Map<String, KitStats>)((Object[])e.getValue())[1];
                if (!kits.isEmpty()) {
                    players++;
                    String b = "players." + e.getKey() + ".";
                    y.set(b + "name", e.getValue()[0]);

                    for (Entry<String, KitStats> k : kits.entrySet()) {
                        write(y, b + "kits." + k.getKey() + ".", k.getValue());
                    }
                }
            }

            try {
                if (!this.dir.exists() && !this.dir.mkdirs()) {
                    throw new IOException("cannot create " + this.dir);
                }

                y.save(new File(this.dir, name));
            } catch (IOException var12) {
                throw new RuntimeException(var12);
            }

            this.prune();
            return new Object[]{name, players};
        });
    }

    private void prune() {
        List<String> fs = this.files();

        for (int i = 20; i < fs.size(); i++) {
            File f = new File(this.dir, fs.get(i));
            if (!f.delete()) {
                this.plugin.getLogger().warning("Could not delete old backup " + f.getName());
            }
        }
    }

    private static void write(YamlConfiguration y, String b, KitStats k) {
        y.set(b + "points", k.elo);
        y.set(b + "tier", k.tier);
        y.set(b + "peak", k.peakElo);
        y.set(b + "wins", k.wins);
        y.set(b + "losses", k.losses);
        y.set(b + "ranked-games", k.rankedGames);
        y.set(b + "unranked-wins", k.unrankedWins);
        y.set(b + "unranked-losses", k.unrankedLosses);
        y.set(b + "streak", k.streak);
        y.set(b + "best-streak", k.bestStreak);
        y.set(b + "rounds-won", k.roundsWon);
        y.set(b + "rounds-lost", k.roundsLost);
        y.set(b + "challenge-wins", k.challengeWins);
        y.set(b + "challenge-losses", k.challengeLosses);
    }

    private static KitStats read(ConfigurationSection s) {
        KitStats k = new KitStats();
        k.elo = s.getInt("points");
        k.tier = s.getInt("tier", -1);
        k.peakElo = s.getInt("peak", k.elo);
        k.wins = s.getInt("wins");
        k.losses = s.getInt("losses");
        k.rankedGames = s.getInt("ranked-games");
        k.unrankedWins = s.getInt("unranked-wins");
        k.unrankedLosses = s.getInt("unranked-losses");
        k.streak = s.getInt("streak");
        k.bestStreak = s.getInt("best-streak");
        k.roundsWon = s.getInt("rounds-won");
        k.roundsLost = s.getInt("rounds-lost");
        k.challengeWins = s.getInt("challenge-wins");
        k.challengeLosses = s.getInt("challenge-losses");
        return k;
    }

    public CompletableFuture<Integer> restore(String fileName) {
        File f = new File(this.dir, new File(fileName).getName());
        if (!f.isFile()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("No backup called " + fileName));
        } else {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection ps = y.getConfigurationSection("players");
            if (ps == null) {
                return CompletableFuture.completedFuture(0);
            } else {
                boolean oldTiers = y.getInt("version", 1) < 2;
                Map<UUID, String> names = new HashMap<>();
                Map<UUID, Map<String, KitStats>> data = new HashMap<>();

                for (String id : ps.getKeys(false)) {
                    try {
                        UUID u = UUID.fromString(id);
                        ConfigurationSection ks = ps.getConfigurationSection(id + ".kits");
                        if (ks != null) {
                            Map<String, KitStats> kits = new HashMap<>();

                            for (String kit : ks.getKeys(false)) {
                                ConfigurationSection s = ks.getConfigurationSection(kit);
                                if (s != null) {
                                    KitStats st = read(s);
                                    if (oldTiers) {
                                        st.tier = Tier.fromOldIndex(st.tier);
                                    }

                                    kits.put(kit, st);
                                }
                            }

                            names.put(u, ps.getString(id + ".name", id.substring(0, 8)));
                            data.put(u, kits);
                        }
                    } catch (IllegalArgumentException var18) {
                    }
                }

                int[] n = new int[]{0};

                for (Entry<UUID, Map<String, KitStats>> e : new ArrayList<>(data.entrySet())) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null) {
                        PvPProfile prof = this.plugin.profiles().get(p);
                        prof.kits.putAll(e.getValue());
                        prof.dirty = true;
                        this.plugin.profiles().save(prof);

                        try {
                            this.plugin.boards().refreshNametag(p);
                        } catch (RuntimeException var17) {
                        }

                        data.remove(e.getKey());
                        n[0]++;
                    }
                }

                return this.plugin.db().supply(c -> {
                    int done = 0;

                    for (Entry<UUID, Map<String, KitStats>> ex : data.entrySet()) {
                        try {
                            PvPProfile profx = this.plugin.db().loadProfile(c, ex.getKey(), names.get(ex.getKey()));
                            profx.kits.putAll(ex.getValue());
                            this.plugin.db().saveProfile(c, profx);
                            done++;
                        } catch (SQLException var9x) {
                            this.plugin.getLogger().log(Level.WARNING, "Restoring " + names.get(ex.getKey()) + " failed", (Throwable)var9x);
                        }
                    }

                    return done + n[0];
                }).whenComplete((v, err) -> Tasks.ensureSync(() -> this.plugin.leaderboards().refresh()));
            }
        }
    }
}
