package net.vortexpvp.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.kit.KitLayout;
import net.vortexpvp.rank.Tier;
import org.bukkit.plugin.java.JavaPlugin;

public final class Database {
    private final JavaPlugin plugin;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VortexPvP-DB");
        t.setDaemon(true);
        return t;
    });
    private Connection conn;
    private boolean mysql;
    private volatile boolean recordColumns;
    private volatile boolean snoozeColumns;
    private volatile boolean skillColumns;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open() throws Exception {
        this.mysql = "MYSQL".equalsIgnoreCase(Cfg.s(Opt.DB_TYPE));
        this.supply(c -> {
            this.createTables();
            return null;
        }).get(30L, TimeUnit.SECONDS);
        this.plugin.getLogger().info("Database ready (" + (this.mysql ? "MySQL" : "SQLite") + ").");
    }

    private Connection connection() throws SQLException {
        if (this.conn != null) {
            try {
                if (this.conn.isValid(2)) {
                    return this.conn;
                }
            } catch (SQLException var9) {
            }

            try {
                this.conn.close();
            } catch (SQLException var8) {
            }
        }

        if (this.mysql) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException var7) {
                throw new SQLException("MySQL driver not found", var7);
            }

            String url = "jdbc:mysql://"
                + Cfg.s(Opt.DB_HOST)
                + ":"
                + Cfg.i(Opt.DB_PORT)
                + "/"
                + Cfg.s(Opt.DB_NAME)
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&tcpKeepAlive=true";
            this.conn = DriverManager.getConnection(url, Cfg.s(Opt.DB_USER), Cfg.s(Opt.DB_PASSWORD));
        } else {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException var6) {
                throw new SQLException("SQLite driver not found", var6);
            }

            File f = new File(this.plugin.getDataFolder(), "data.db");
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());

            try (Statement st = this.conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA busy_timeout=5000");
            }
        }

        this.conn.setAutoCommit(true);
        return this.conn;
    }

    private void createTables() throws SQLException {
        Connection c = this.connection();

        try (Statement st = c.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS vp_players (uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(16) NOT NULL, settings TEXT, ffa_kills INT NOT NULL DEFAULT 0, ffa_deaths INT NOT NULL DEFAULT 0, ffa_best_streak INT NOT NULL DEFAULT 0, first_join BIGINT NOT NULL DEFAULT 0, last_seen BIGINT NOT NULL DEFAULT 0)"
            );
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS vp_kit_stats (uuid VARCHAR(36) NOT NULL, kit VARCHAR(48) NOT NULL, elo INT NOT NULL DEFAULT 0, tier INT NOT NULL DEFAULT -1, wins INT NOT NULL DEFAULT 0, losses INT NOT NULL DEFAULT 0, ranked_games INT NOT NULL DEFAULT 0, unranked_wins INT NOT NULL DEFAULT 0, unranked_losses INT NOT NULL DEFAULT 0, streak INT NOT NULL DEFAULT 0, best_streak INT NOT NULL DEFAULT 0, peak_elo INT NOT NULL DEFAULT 0, promo_state INT NOT NULL DEFAULT 0, promo_wins INT NOT NULL DEFAULT 0, promo_losses INT NOT NULL DEFAULT 0, promo_target INT NOT NULL DEFAULT -1, since_remind INT NOT NULL DEFAULT 0, PRIMARY KEY (uuid, kit))"
            );
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS vp_layouts (uuid VARCHAR(36) NOT NULL, kit VARCHAR(48) NOT NULL, version INT NOT NULL, data TEXT NOT NULL, PRIMARY KEY (uuid, kit))"
            );
            // chat mutes: active until 'until' (ms); offences counts filter hits inside the reset window
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS vp_mutes (uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(16), until BIGINT NOT NULL DEFAULT 0, reason VARCHAR(255), muted_by VARCHAR(32), offences INT NOT NULL DEFAULT 0, last_offence BIGINT NOT NULL DEFAULT 0)"
            );
            // practice cosmetics: armor trims and the shield design (Cosmetics.Data)
            st.executeUpdate("CREATE TABLE IF NOT EXISTS vp_cosmetics (uuid VARCHAR(36) NOT NULL PRIMARY KEY, data TEXT NOT NULL)");
            // /alts: salted hashes of the addresses each account joined from (never the address itself)
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS vp_ips (uuid VARCHAR(36) NOT NULL, name VARCHAR(16), ip_hash VARCHAR(64) NOT NULL, first_seen BIGINT NOT NULL DEFAULT 0, last_seen BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (uuid, ip_hash))"
            );
            // the Academy: finished lessons per player
            st.executeUpdate("CREATE TABLE IF NOT EXISTS vp_academy (uuid VARCHAR(36) NOT NULL, lesson VARCHAR(64) NOT NULL, done_at BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (uuid, lesson))");
            if (this.mysql) {
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS vp_matches (id BIGINT AUTO_INCREMENT PRIMARY KEY, kit VARCHAR(48), ranked TINYINT, winner VARCHAR(36), loser VARCHAR(36), score VARCHAR(16), elo_change INT, duration_s INT, ended_at BIGINT)"
                );
            } else {
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS vp_matches (id INTEGER PRIMARY KEY AUTOINCREMENT, kit VARCHAR(48), ranked INT, winner VARCHAR(36), loser VARCHAR(36), score VARCHAR(16), elo_change INT, duration_s INT, ended_at BIGINT)"
                );
            }
        }

        try (Statement stx = c.createStatement()) {
            stx.executeUpdate("CREATE INDEX idx_vp_kit_elo ON vp_kit_stats (kit, elo)");
        } catch (SQLException var29) {
        }

        for (String col : List.of("promo_wait", "promo_declines")) {
            try (Statement stx = c.createStatement()) {
                stx.executeUpdate("ALTER TABLE vp_kit_stats ADD COLUMN " + col + " INT NOT NULL DEFAULT 0");
            } catch (SQLException var27) {
            }
        }

        try (Statement stx = c.createStatement()) {
            stx.executeQuery("SELECT promo_wait, promo_declines FROM vp_kit_stats LIMIT 1").close();
            this.snoozeColumns = true;
        } catch (SQLException var25) {
            this.snoozeColumns = false;
            this.plugin.getLogger().warning("Rank-up snooze columns unavailable; pop-up waits won't survive restarts.");
        }

        this.migrateTiers(c);

        for (String col : List.of("rounds_won", "rounds_lost", "challenge_wins", "challenge_losses")) {
            try (Statement stx = c.createStatement()) {
                stx.executeUpdate("ALTER TABLE vp_kit_stats ADD COLUMN " + col + " INT NOT NULL DEFAULT 0");
            } catch (SQLException var23) {
            }
        }

        try (Statement stx = c.createStatement()) {
            stx.executeQuery("SELECT rounds_won, rounds_lost, challenge_wins, challenge_losses FROM vp_kit_stats LIMIT 1").close();
            this.recordColumns = true;
        } catch (SQLException var21) {
            this.recordColumns = false;
            this.plugin.getLogger().warning("Round / challenge record columns unavailable; those stats won't be saved.");
        }

        for (String col : List.of("skill_games", "skill_points")) {
            try (Statement stx = c.createStatement()) {
                stx.executeUpdate("ALTER TABLE vp_kit_stats ADD COLUMN " + col + " INT NOT NULL DEFAULT 0");
            } catch (SQLException var19) {
            }
        }

        try (Statement stx = c.createStatement()) {
            stx.executeQuery("SELECT skill_games, skill_points FROM vp_kit_stats LIMIT 1").close();
            this.skillColumns = true;
        } catch (SQLException var17) {
            this.skillColumns = false;
            this.plugin.getLogger().warning("Skill check columns unavailable; skill-check progress won't survive restarts.");
        }
    }

    private void migrateTiers(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS vp_meta (k VARCHAR(64) NOT NULL PRIMARY KEY, v VARCHAR(255))");
        }

        try (
            PreparedStatement ps = c.prepareStatement("SELECT v FROM vp_meta WHERE k='tiers'");
            ResultSet rs = ps.executeQuery();
        ) {
            if (rs.next()) {
                return;
            }
        }

        StringBuilder map = new StringBuilder("CASE %s");

        for (int old = 0; old < 10; old++) {
            map.append(" WHEN ").append(old).append(" THEN ").append(Tier.fromOldIndex(old));
        }

        map.append(" ELSE %s END");
        String tierCase = String.format(map.toString(), "tier", "tier");
        String targetCase = String.format(map.toString(), "promo_target", "promo_target");
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);

        try (Statement st = c.createStatement()) {
            int n = st.executeUpdate("UPDATE vp_kit_stats SET tier = " + tierCase + ", promo_target = " + targetCase);
            st.executeUpdate("INSERT INTO vp_meta (k, v) VALUES ('tiers', '15')");
            c.commit();
            if (n > 0) {
                this.plugin.getLogger().info("Ranks moved to the new tier ladder with Mid tiers (" + n + " rows).");
            }
        } catch (SQLException var26) {
            c.rollback();
            throw var26;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    public boolean vaultCopy(File target) throws Exception {
        return this.mysql ? false : this.<Boolean>supply(c -> {
            try (Statement st = c.createStatement()) {
                st.execute("VACUUM INTO '" + target.getAbsolutePath().replace("'", "''") + "'");
            } catch (SQLException var12) {
                try (Statement stx = c.createStatement()) {
                    stx.execute("PRAGMA wal_checkpoint(FULL)");
                }

                try {
                    Files.copy(new File(this.plugin.getDataFolder(), "data.db").toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException var8) {
                    throw new SQLException(var8);
                }
            }

            return true;
        }).get(120L, TimeUnit.SECONDS);
    }

    public boolean mysql() {
        return this.mysql;
    }

    public <T> CompletableFuture<T> supply(Database.SqlFn<T> fn) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fn.apply(this.connection());
            } catch (SQLException var3) {
                this.plugin.getLogger().log(Level.SEVERE, "Database error", (Throwable)var3);
                throw new RuntimeException(var3);
            }
        }, this.exec);
    }

    public CompletableFuture<Void> run(Database.SqlRun r) {
        return this.supply(c -> {
            r.run(c);
            return null;
        });
    }

    public void close() {
        this.exec.shutdown();

        try {
            if (!this.exec.awaitTermination(15L, TimeUnit.SECONDS)) {
                this.plugin.getLogger().warning("Database queue did not drain within 15s.");
            }
        } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
        }

        if (this.conn != null) {
            try {
                this.conn.close();
            } catch (SQLException var2) {
            }
        }
    }

    public PvPProfile loadProfile(Connection c, UUID uuid, String name) throws SQLException {
        PvPProfile p = new PvPProfile(uuid, name);

        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM vp_players WHERE uuid=?")) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    p.parseSettings(rs.getString("settings"));
                    p.ffaKills = rs.getInt("ffa_kills");
                    p.ffaDeaths = rs.getInt("ffa_deaths");
                    p.ffaBestStreak = rs.getInt("ffa_best_streak");
                    p.firstJoin = rs.getLong("first_join");
                } else {
                    p.dirty = true;
                }
            }
        }

        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM vp_kit_stats WHERE uuid=?")) {
            ps.setString(1, uuid.toString());

            try (ResultSet rsx = ps.executeQuery()) {
                while (rsx.next()) {
                    KitStats k = new KitStats();
                    k.elo = rsx.getInt("elo");
                    k.tier = rsx.getInt("tier");
                    k.wins = rsx.getInt("wins");
                    k.losses = rsx.getInt("losses");
                    k.rankedGames = rsx.getInt("ranked_games");
                    k.unrankedWins = rsx.getInt("unranked_wins");
                    k.unrankedLosses = rsx.getInt("unranked_losses");
                    k.streak = rsx.getInt("streak");
                    k.bestStreak = rsx.getInt("best_streak");
                    k.peakElo = rsx.getInt("peak_elo");
                    k.promoState = rsx.getInt("promo_state");
                    k.promoWins = rsx.getInt("promo_wins");
                    k.promoLosses = rsx.getInt("promo_losses");
                    k.promoTarget = rsx.getInt("promo_target");
                    k.sinceRemind = rsx.getInt("since_remind");
                    if (this.snoozeColumns) {
                        k.promoWait = rsx.getInt("promo_wait");
                        k.promoDeclines = rsx.getInt("promo_declines");
                    }

                    if (this.recordColumns) {
                        k.roundsWon = rsx.getInt("rounds_won");
                        k.roundsLost = rsx.getInt("rounds_lost");
                        k.challengeWins = rsx.getInt("challenge_wins");
                        k.challengeLosses = rsx.getInt("challenge_losses");
                    }

                    if (this.skillColumns) {
                        k.skillGames = rsx.getInt("skill_games");
                        k.skillPoints = rsx.getInt("skill_points");
                    }

                    p.kits.put(rsx.getString("kit"), k);
                }
            }
        }

        try (PreparedStatement ps = c.prepareStatement("SELECT kit, version, data FROM vp_layouts WHERE uuid=?")) {
            ps.setString(1, uuid.toString());

            try (ResultSet rsx = ps.executeQuery()) {
                while (rsx.next()) {
                    KitLayout layout = KitLayout.parse(rsx.getInt("version"), rsx.getString("data"));
                    if (layout != null) {
                        p.layouts.put(rsx.getString("kit"), layout);
                    }
                }
            }
        }

        p.name = name;
        return p;
    }

    public void saveProfile(Connection c, PvPProfile p) throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);

        try {
            try (PreparedStatement ps = c.prepareStatement(
                    "REPLACE INTO vp_players (uuid, name, settings, ffa_kills, ffa_deaths, ffa_best_streak, first_join, last_seen) VALUES (?,?,?,?,?,?,?,?)"
                )) {
                ps.setString(1, p.uuid.toString());
                ps.setString(2, p.name);
                ps.setString(3, p.serializeSettings());
                ps.setInt(4, p.ffaKills);
                ps.setInt(5, p.ffaDeaths);
                ps.setInt(6, p.ffaBestStreak);
                ps.setLong(7, p.firstJoin);
                ps.setLong(8, p.lastSeen);
                ps.executeUpdate();
            }

            String cols = "uuid, kit, elo, tier, wins, losses, ranked_games, unranked_wins, unranked_losses, streak, best_streak, peak_elo, promo_state, promo_wins, promo_losses, promo_target, since_remind"
                + (this.snoozeColumns ? ", promo_wait, promo_declines" : "")
                + (this.recordColumns ? ", rounds_won, rounds_lost, challenge_wins, challenge_losses" : "")
                + (this.skillColumns ? ", skill_games, skill_points" : "");
            String marks = "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?"
                + (this.snoozeColumns ? ",?,?" : "")
                + (this.recordColumns ? ",?,?,?,?" : "")
                + (this.skillColumns ? ",?,?" : "");

            try (PreparedStatement ps = c.prepareStatement("REPLACE INTO vp_kit_stats (" + cols + ") VALUES (" + marks + ")")) {
                for (Entry<String, KitStats> e : p.kits.entrySet()) {
                    KitStats k = e.getValue();
                    ps.setString(1, p.uuid.toString());
                    ps.setString(2, e.getKey());
                    ps.setInt(3, k.elo);
                    ps.setInt(4, k.tier);
                    ps.setInt(5, k.wins);
                    ps.setInt(6, k.losses);
                    ps.setInt(7, k.rankedGames);
                    ps.setInt(8, k.unrankedWins);
                    ps.setInt(9, k.unrankedLosses);
                    ps.setInt(10, k.streak);
                    ps.setInt(11, k.bestStreak);
                    ps.setInt(12, k.peakElo);
                    ps.setInt(13, k.promoState);
                    ps.setInt(14, k.promoWins);
                    ps.setInt(15, k.promoLosses);
                    ps.setInt(16, k.promoTarget);
                    ps.setInt(17, k.sinceRemind);
                    int i = 18;
                    if (this.snoozeColumns) {
                        ps.setInt(i++, k.promoWait);
                        ps.setInt(i++, k.promoDeclines);
                    }

                    if (this.recordColumns) {
                        ps.setInt(i++, k.roundsWon);
                        ps.setInt(i++, k.roundsLost);
                        ps.setInt(i++, k.challengeWins);
                        ps.setInt(i++, k.challengeLosses);
                    }

                    if (this.skillColumns) {
                        ps.setInt(i++, k.skillGames);
                        ps.setInt(i, k.skillPoints);
                    }

                    ps.addBatch();
                }

                ps.executeBatch();
            }

            try (PreparedStatement ps = c.prepareStatement("REPLACE INTO vp_layouts (uuid, kit, version, data) VALUES (?,?,?,?)")) {
                for (Entry<String, KitLayout> e : p.layouts.entrySet()) {
                    ps.setString(1, p.uuid.toString());
                    ps.setString(2, e.getKey());
                    ps.setInt(3, e.getValue().version);
                    ps.setString(4, e.getValue().serialize());
                    ps.addBatch();
                }

                ps.executeBatch();
            }

            c.commit();
        } catch (SQLException var26) {
            c.rollback();
            throw var26;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    public void deleteLayout(Connection c, UUID uuid, String kit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM vp_layouts WHERE uuid=? AND kit=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            ps.executeUpdate();
        }
    }

    public List<Database.LbEntry> top(Connection c, String kit, int limit) throws SQLException {
        List<Database.LbEntry> out = new ArrayList<>();

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT s.uuid, p.name, s.elo, s.tier, s.wins, s.losses FROM vp_kit_stats s JOIN vp_players p ON p.uuid = s.uuid WHERE s.kit=? AND s.tier >= 0 ORDER BY s.elo DESC, s.wins DESC LIMIT ?"
            )) {
            ps.setString(1, kit);
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Database.LbEntry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6)));
                }
            }
        }

        return out;
    }

    /** A leaderboard row for any stat: value is what the board ranks by (elo, wins, win %, streak, kills, K/D). */
    public record StatRow(UUID uuid, String name, double value, int tier, int wins, int losses) {
    }

    /**
     * Top players of one kit ("overall" = all kits together) by a stat: ELO, WINS, WINRATE (10+ games), STREAK (best),
     * FFA_KILLS, FFA_KD (10+ deaths... or kills). FFA stats are server-wide and ignore the kit.
     */
    public List<Database.StatRow> topStat(Connection c, String kit, String stat, int limit) throws SQLException {
        boolean overall = "overall".equals(kit);
        String sql = switch (stat) {
            case "FFA_KILLS" -> "SELECT uuid, name, ffa_kills AS v, -1, 0, 0 FROM vp_players WHERE ffa_kills > 0 ORDER BY v DESC LIMIT ?";
            case "FFA_KD" -> "SELECT uuid, name, ffa_kills * 1.0 / (CASE WHEN ffa_deaths < 1 THEN 1 ELSE ffa_deaths END) AS v, -1, 0, 0 FROM vp_players WHERE ffa_kills >= 10 ORDER BY v DESC LIMIT ?";
            default -> {
                String value = switch (stat) {
                    case "WINS" -> overall ? "SUM(s.wins + s.unranked_wins)" : "(s.wins + s.unranked_wins)";
                    case "WINRATE" -> overall
                        ? "SUM(s.wins + s.unranked_wins) * 100.0 / SUM(s.wins + s.losses + s.unranked_wins + s.unranked_losses)"
                        : "(s.wins + s.unranked_wins) * 100.0 / (s.wins + s.losses + s.unranked_wins + s.unranked_losses)";
                    case "STREAK" -> overall ? "MAX(s.best_streak)" : "s.best_streak";
                    default -> overall ? "SUM(s.elo)" : "s.elo";
                };
                String games = overall ? "SUM(s.wins + s.losses + s.unranked_wins + s.unranked_losses)" : "(s.wins + s.losses + s.unranked_wins + s.unranked_losses)";
                String where = "WINRATE".equals(stat) ? games + " >= 10" : ("ELO".equals(stat) || stat.isEmpty() ? (overall ? "MAX(s.tier) >= 0" : "s.tier >= 0") : "1=1");
                yield overall
                    ? "SELECT s.uuid, p.name, " + value + " AS v, MAX(s.tier), SUM(s.wins), SUM(s.losses) FROM vp_kit_stats s JOIN vp_players p ON p.uuid = s.uuid GROUP BY s.uuid, p.name HAVING " + where + " ORDER BY v DESC LIMIT ?"
                    : "SELECT s.uuid, p.name, " + value + " AS v, s.tier, s.wins, s.losses FROM vp_kit_stats s JOIN vp_players p ON p.uuid = s.uuid WHERE s.kit=? AND " + where + " ORDER BY v DESC LIMIT ?";
            }
        };
        List<Database.StatRow> out = new ArrayList<>();

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (!overall && !stat.startsWith("FFA")) {
                ps.setString(i++, kit);
            }

            ps.setInt(i, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Database.StatRow(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getDouble(3), rs.getInt(4), rs.getInt(5), rs.getInt(6)));
                }
            }
        }

        return out;
    }

    public List<Database.LbEntry> topOverall(Connection c, int limit) throws SQLException {
        List<Database.LbEntry> out = new ArrayList<>();

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT s.uuid, p.name, SUM(s.elo) AS total, MAX(s.tier) AS mt, SUM(s.wins) AS w, SUM(s.losses) AS l FROM vp_kit_stats s JOIN vp_players p ON p.uuid = s.uuid WHERE s.tier >= 0 GROUP BY s.uuid, p.name ORDER BY total DESC LIMIT ?"
            )) {
            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Database.LbEntry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6)));
                }
            }
        }

        return out;
    }

    public int[] rankOf(Connection c, String kit, int elo) throws SQLException {
        int above;
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM vp_kit_stats WHERE kit=? AND tier >= 0 AND elo > ?")) {
            ps.setString(1, kit);
            ps.setInt(2, elo);

            try (ResultSet rs = ps.executeQuery()) {
                above = rs.next() ? rs.getInt(1) : 0;
            }
        }

        int total;
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM vp_kit_stats WHERE kit=? AND tier >= 0")) {
            ps.setString(1, kit);

            try (ResultSet rs = ps.executeQuery()) {
                total = rs.next() ? rs.getInt(1) : 0;
            }
        }

        return new int[]{above + 1, total};
    }

    public int[] rankOverall(Connection c, int sum) throws SQLException {
        int above;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM (SELECT uuid, SUM(elo) AS t FROM vp_kit_stats WHERE tier >= 0 GROUP BY uuid) x WHERE x.t > ?"
            )) {
            ps.setInt(1, sum);

            try (ResultSet rs = ps.executeQuery()) {
                above = rs.next() ? rs.getInt(1) : 0;
            }
        }

        int total;
        try (
            PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM (SELECT uuid FROM vp_kit_stats WHERE tier >= 0 GROUP BY uuid) x");
            ResultSet rs = ps.executeQuery();
        ) {
            total = rs.next() ? rs.getInt(1) : 0;
        }

        return new int[]{above + 1, total};
    }

    public Object[] rival(Connection c, UUID u, String kit) throws SQLException {
        String id = u.toString();
        String sql = "SELECT m.opp, COUNT(*) AS n, SUM(m.won) AS w FROM (SELECT CASE WHEN winner=? THEN loser ELSE winner END AS opp, CASE WHEN winner=? THEN 1 ELSE 0 END AS won FROM vp_matches WHERE (winner=? OR loser=?)"
            + (kit == null ? "" : " AND kit=?")
            + ") m GROUP BY m.opp ORDER BY n DESC LIMIT 1";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, id);
            ps.setString(3, id);
            ps.setString(4, id);
            if (kit != null) {
                ps.setString(5, kit);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString(1) != null) {
                    String opp = rs.getString(1);
                    int n = rs.getInt(2);
                    int w = rs.getInt(3);
                    String name = opp.substring(0, 8);

                    try (PreparedStatement p2 = c.prepareStatement("SELECT name FROM vp_players WHERE uuid=?")) {
                        p2.setString(1, opp);

                        try (ResultSet r2 = p2.executeQuery()) {
                            if (r2.next()) {
                                name = r2.getString(1);
                            }
                        }
                    }

                    return new Object[]{name, n, w};
                }
            }
        }

        return null;
    }

    public Map<UUID, Object[]> allRanks(Connection c) throws SQLException {
        Map<UUID, Object[]> out = new LinkedHashMap<>();

        try (
            PreparedStatement ps = c.prepareStatement("SELECT uuid, name FROM vp_players");
            ResultSet rs = ps.executeQuery();
        ) {
            while (rs.next()) {
                try {
                    out.put(UUID.fromString(rs.getString(1)), new Object[]{rs.getString(2), new LinkedHashMap()});
                } catch (IllegalArgumentException var9) {
                }
            }
        }

        for (UUID u : out.keySet()) {
            PvPProfile p = this.loadProfile(c, u, (String)out.get(u)[0]);
            Map<String, KitStats> kits = (Map<String, KitStats>)((Object[])out.get(u))[1];
            kits.putAll(p.kits);
        }

        return out;
    }

    public UUID findUuid(Connection c, String name) throws SQLException {
        UUID var5;
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid FROM vp_players WHERE LOWER(name)=LOWER(?) ORDER BY last_seen DESC LIMIT 1")) {
            ps.setString(1, name);

            try (ResultSet rs = ps.executeQuery()) {
                var5 = rs.next() ? UUID.fromString(rs.getString(1)) : null;
            }
        }

        return var5;
    }

    public void logMatch(Connection c, String kit, boolean ranked, UUID winner, UUID loser, String score, int eloChange, int durationS) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO vp_matches (kit, ranked, winner, loser, score, elo_change, duration_s, ended_at) VALUES (?,?,?,?,?,?,?,?)"
            )) {
            ps.setString(1, kit);
            ps.setInt(2, ranked ? 1 : 0);
            ps.setString(3, winner == null ? null : winner.toString());
            ps.setString(4, loser == null ? null : loser.toString());
            ps.setString(5, score);
            ps.setInt(6, eloChange);
            ps.setInt(7, durationS);
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public List<Database.RecentMatch> recentMatches(Connection c, int limit) throws SQLException {
        List<Database.RecentMatch> out = new ArrayList<>();

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT m.kit, m.ranked, m.score, m.elo_change, m.duration_s, m.ended_at, w.name AS wn, l.name AS ln FROM vp_matches m LEFT JOIN vp_players w ON w.uuid = m.winner LEFT JOIN vp_players l ON l.uuid = m.loser ORDER BY m.id DESC LIMIT ?"
            )) {
            ps.setInt(1, Math.max(1, Math.min(100, limit)));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(
                        new Database.RecentMatch(
                            rs.getString("kit"),
                            rs.getInt("ranked") == 1,
                            rs.getString("wn"),
                            rs.getString("ln"),
                            rs.getString("score"),
                            rs.getInt("elo_change"),
                            rs.getInt("duration_s"),
                            rs.getLong("ended_at")
                        )
                    );
                }
            }
        }

        return out;
    }

    public void resetKitStats(Connection c, String kit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(kit == null ? "DELETE FROM vp_kit_stats" : "DELETE FROM vp_kit_stats WHERE kit=?")) {
            if (kit != null) {
                ps.setString(1, kit);
            }

            ps.executeUpdate();
        }
    }

    public void resetPlayer(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM vp_kit_stats WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }

        try (PreparedStatement ps = c.prepareStatement("UPDATE vp_players SET ffa_kills=0, ffa_deaths=0, ffa_best_streak=0 WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public record LbEntry(UUID uuid, String name, int elo, int tier, int wins, int losses) {
    }

    public record RecentMatch(String kit, boolean ranked, String winner, String loser, String score, int eloChange, int durationS, long endedAt) {
    }

    @FunctionalInterface
    public interface SqlFn<T> {
        T apply(Connection var1) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlRun {
        void run(Connection var1) throws SQLException;
    }
}
