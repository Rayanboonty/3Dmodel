package net.practicepvp.data;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.practicepvp.PracticePlugin;
import net.practicepvp.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Brings an old plugin folder back - PracticePvP, VortexRealm or VortexPvP, as a folder or a .zip / .tar.gz of it -
 * dropped in plugins/&lt;this plugin&gt;/import/. Read on the next start, before anything loads:
 * <ul>
 * <li>server setup comes back exactly as it was (worlds and hub, lobby, NPCs, holograms, decorations, images,
 *     arenas, kits, ranks, menus, settings...);</li>
 * <li>player data is merged and nothing current is overwritten: missing homes, claims, last locations and players
 *     are added, and players whose stats or money were reset get the old values plus what they earned since;</li>
 * <li>the database is read with its newest changes (the -wal file next to data.db);</li>
 * <li>lost ender chests and inventories are handed back in game (see InvRestore).</li>
 * </ul>
 * Everything is saved in the vault first (a "before-import" backup), and a report is written next to the import.
 */
public final class OldDataImport {
    /** Server setup: the old copy wins (the current one is in the before-import backup). */
    private static final Set<String> SETUP = Set.of(
        "config.yml", "worlds.yml", "locations.yml", "npcs.yml", "holograms.yml", "text-holograms.yml", "decorations.yml", "wall-images.yml",
        "arenas.yml", "ffa.yml", "kits.yml", "rank-kits.yml", "ranks.yml", "hotbar.yml", "tab.yml", "scoreboards.yml", "selector.yml",
        "welcome.yml", "messages.yml", "announcements.yml", "eco.yml", "countries.yml", "chat-filter.yml", "regions.yml", "bots.yml",
        "events.yml", "penalties.yml", "sentinel.yml", "links.yml", "academy.yml", "deco-models.yml", "default-kits.yml"
    );
    /** Folders of server setup copied the same way. */
    private static final List<String> SETUP_DIRS = List.of("images/", "snapshots/", "web/", "bot-hall/");
    /** Never copied: backups, logs, imports, the database and inventories (both merged separately). */
    static boolean skipped(String rel) {
        return rel.startsWith("backups/") || rel.startsWith("logs/") || rel.startsWith("import/") || rel.startsWith("inventories/") || rel.startsWith("data.db")
            || rel.endsWith(".lock") || rel.endsWith(".tmp") || rel.contains(".migrated-from-") || rel.contains("-Vault/");
    }
    /** Table prefixes of the three plugins, built in parts so the generators leave them alone. */
    private static final String[] PREFIXES = {"p" + "p_", "v" + "r_", "v" + "p_"};
    private static final String[] TABLES = {"players", "kit_stats", "layouts", "matches", "meta", "friends", "follows", "mutes", "cosmetics", "ips", "academy"};
    private final PracticePlugin plugin;
    private final File data;
    private final File importDir;
    private final List<String> report = new ArrayList<>();
    /** What the last import did, for the admins joining afterwards. */
    public static volatile List<String> lastReport = List.of();

    public OldDataImport(PracticePlugin plugin) {
        this.plugin = plugin;
        this.data = plugin.getDataFolder();
        this.importDir = new File(this.data, "import");
    }

    private void note(String s) {
        this.report.add(s);
        this.plugin.getLogger().info("[import] " + s);
    }

    /** This plugin's table prefix (the literal below is renamed by the generators along with every table). */
    static String ownPrefix() {
        return "pp_kit_stats".substring(0, 3);
    }

    /** This plugin's permission prefix ("practice." / "vortex." / "vortexpvp."), renamed the same way. */
    static String ownPermPrefix() {
        String p = "practice.admin";
        return p.substring(0, p.indexOf('.') + 1);
    }

    // ------------------------------------------------------------------ entry point (called at start-up)

    /** Runs an import if something was dropped in import/. Returns true when it did. */
    public boolean runIfPresent() {
        File[] items = this.importDir.listFiles(f -> !f.getName().startsWith("done-") && !f.getName().startsWith(".") && !f.getName().equals("README.txt")
            && !f.getName().equals("LAST-IMPORT.txt"));
        if (items == null || items.length == 0) {
            this.writeReadme();
            return false;
        }

        File work = new File(this.importDir, ".work");
        try {
            deleteTree(work.toPath());
            File src = items[0];
            File root;
            if (src.isDirectory()) {
                root = findRoot(src);
            } else {
                File out = new File(work, "unpacked");
                unpack(src, out);
                root = findRoot(out);
            }

            if (root == null) {
                this.plugin.getLogger().warning("[import] " + src.getName() + " doesn't look like a plugin folder (no config.yml or kits.yml inside) - nothing imported.");
                return false;
            }

            this.note("Importing " + src.getName() + " (" + root.getName() + ")");
            if (this.plugin.vault() != null) {
                try {
                    this.note("Current data saved first: " + this.plugin.vault().snapshotBefore("before-import"));
                } catch (IOException ex) {
                    this.plugin.getLogger().log(Level.SEVERE, "[import] Could not back up the current data - import cancelled, nothing changed.", ex);
                    return false;
                }
            }

            this.importFiles(root);
            this.importDatabase(root, work);
            this.importInventories(root);
            // done: keep what was imported, out of the way
            File done = new File(this.importDir, "done-" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + "-" + src.getName());
            Files.move(src.toPath(), done.toPath(), StandardCopyOption.REPLACE_EXISTING);
            this.note("Finished. The imported files are kept in import/" + done.getName());
            Files.writeString(new File(this.importDir, "LAST-IMPORT.txt").toPath(), String.join("\n", this.report) + "\n", StandardCharsets.UTF_8);
            lastReport = List.copyOf(this.report);
            return true;
        } catch (IOException | RuntimeException ex) {
            this.plugin.getLogger().log(Level.SEVERE, "[import] Import failed part-way - the before-import backup in the vault has the data from before.", ex);
            return false;
        } finally {
            try {
                deleteTree(work.toPath());
            } catch (IOException var13) {
            }
        }
    }

    private void writeReadme() {
        try {
            if (!this.importDir.isDirectory() && !this.importDir.mkdirs()) {
                return;
            }

            File readme = new File(this.importDir, "README.txt");
            if (!readme.exists()) {
                Files.writeString(readme.toPath(), String.join("\n",
                    "Put an old plugin folder here to bring everything back: PracticePvP, VortexRealm or VortexPvP.",
                    "The folder itself (with config.yml, kits.yml, data.db... inside), or a .zip / .tar.gz of it.",
                    "Then restart the server. Server setup (hub, worlds, NPCs, holograms, arenas, kits, ranks, settings) comes back",
                    "as it was; player data (homes, claims, last locations, stats, money, inventories, ender chests) is merged -",
                    "nothing players have now is overwritten. A full backup of the current data is saved first.",
                    "") , StandardCharsets.UTF_8);
            }
        } catch (IOException var2) {
        }
    }

    // ------------------------------------------------------------------ files

    private void importFiles(File root) throws IOException {
        Path from = root.toPath();
        Path to = this.data.toPath();
        int setup = 0;
        int added = 0;
        int merged = 0;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(from)) {
            files = walk.filter(Files::isRegularFile).toList();
        }

        for (Path f : files) {
            String rel = from.relativize(f).toString().replace('\\', '/');
            if (skipped(rel)) {
                continue;
            }

            Path target = to.resolve(rel);
            boolean isSetup = SETUP.contains(rel) || SETUP_DIRS.stream().anyMatch(rel::startsWith);
            if (rel.equals("homes.yml")) {
                merged += this.mergeYaml(f, target, "homes");
            } else if (rel.equals("claims.yml")) {
                merged += this.mergeClaims(f, target);
            } else if (rel.equals("smp-stats.yml")) {
                merged += this.mergeStats(f, target);
            } else if (rel.equals("last-locations.yml") || rel.equals("rank-kits-claims.yml") || rel.equals("smp-tracker.yml") || rel.equals("hacker-pool.yml")
                || rel.equals("orders.yml") || rel.equals("reports.yml") || rel.equals("auction-house.yml") || rel.equals("alts-salt.yml")) {
                merged += this.mergeYaml(f, target, null);
            } else if (isSetup || !Files.exists(target)) {
                Files.createDirectories(target.getParent());
                if (rel.endsWith(".yml")) {
                    Files.writeString(target, this.perms(Files.readString(f, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
                } else {
                    Files.copy(f, target, StandardCopyOption.REPLACE_EXISTING);
                }

                if (isSetup) {
                    setup++;
                } else {
                    added++;
                }
            }
        }

        this.note("Server setup restored: " + setup + " files (worlds, hub, lobby, NPCs, holograms, arenas, kits, ranks, settings...)");
        this.note("Files that were missing added: " + added + "; player files merged: " + merged + " entries brought back");
    }

    private static final Pattern OLD_PERMS = Pattern.compile("(?<![\\w.])(" + "practice" + "|" + "vortex" + "|" + "vortexpvp" + ")\\.(?=[a-z])");

    /** Permission nodes of the old plugin renamed to this plugin's ("practice.vip" -> "vortex.vip"). */
    String perms(String text) {
        String own = ownPermPrefix();
        return OLD_PERMS.matcher(text).replaceAll(m -> m.group(1).equals(own.substring(0, own.length() - 1)) ? m.group() : own.replace("$", "\\$"));
    }

    /** Adds whatever the old file has and the current one lacks (deep), never changing a current value. */
    private int mergeYaml(Path oldFile, Path target, String section) throws IOException {
        YamlConfiguration old = YamlConfiguration.loadConfiguration(oldFile.toFile());
        if (!Files.exists(target)) {
            Files.createDirectories(target.getParent());
            Files.copy(oldFile, target);
            return countLeaves(old);
        }

        YamlConfiguration cur = YamlConfiguration.loadConfiguration(target.toFile());
        int n = mergeInto(old, cur);
        if (n > 0) {
            cur.save(target.toFile());
        }

        return n;
    }

    private static int countLeaves(ConfigurationSection s) {
        int n = 0;
        for (String k : s.getKeys(false)) {
            n += s.isConfigurationSection(k) ? countLeaves(s.getConfigurationSection(k)) : 1;
        }

        return n;
    }

    static int mergeInto(ConfigurationSection old, ConfigurationSection cur) {
        int n = 0;
        for (String k : old.getKeys(false)) {
            if (old.isConfigurationSection(k)) {
                ConfigurationSection c = cur.isConfigurationSection(k) ? cur.getConfigurationSection(k) : null;
                if (c == null && !cur.contains(k)) {
                    c = cur.createSection(k);
                }

                if (c != null) {
                    n += mergeInto(old.getConfigurationSection(k), c);
                }
            } else if (!cur.contains(k)) {
                cur.set(k, old.get(k));
                n++;
            }
        }

        return n;
    }

    /** Claims: every old claim that isn't there yet (same owner, world and area) comes back with a free id. */
    private int mergeClaims(Path oldFile, Path target) throws IOException {
        if (!Files.exists(target)) {
            Files.createDirectories(target.getParent());
            Files.copy(oldFile, target);
            ConfigurationSection c = YamlConfiguration.loadConfiguration(oldFile.toFile()).getConfigurationSection("claims");
            return c == null ? 0 : c.getKeys(false).size();
        }

        YamlConfiguration old = YamlConfiguration.loadConfiguration(oldFile.toFile());
        YamlConfiguration cur = YamlConfiguration.loadConfiguration(target.toFile());
        ConfigurationSection oc = old.getConfigurationSection("claims");
        if (oc == null) {
            return 0;
        }

        ConfigurationSection cc = cur.isConfigurationSection("claims") ? cur.getConfigurationSection("claims") : cur.createSection("claims");
        Set<String> have = new java.util.HashSet<>();
        int next = cur.getInt("next-id", 1);
        for (String id : cc.getKeys(false)) {
            have.add(cc.getString(id + ".owner") + "|" + cc.getString(id + ".world") + "|" + cc.getString(id + ".box"));
            try {
                next = Math.max(next, Integer.parseInt(id) + 1);
            } catch (NumberFormatException var12) {
            }
        }

        int n = 0;
        for (String id : oc.getKeys(false)) {
            String key = oc.getString(id + ".owner") + "|" + oc.getString(id + ".world") + "|" + oc.getString(id + ".box");
            if (have.add(key)) {
                String newId = cc.contains(id) ? String.valueOf(next++) : id;
                ConfigurationSection dst = cc.createSection(newId);
                for (Map.Entry<String, Object> e : oc.getConfigurationSection(id).getValues(true).entrySet()) {
                    if (!(e.getValue() instanceof ConfigurationSection)) {
                        dst.set(e.getKey(), e.getValue());
                    }
                }

                try {
                    next = Math.max(next, Integer.parseInt(newId) + 1);
                } catch (NumberFormatException var13) {
                }

                n++;
            }
        }

        cur.set("next-id", Math.max(next, Math.max(cur.getInt("next-id", 1), old.getInt("next-id", 1))));
        if (n > 0) {
            cur.save(target.toFile());
        }

        return n;
    }

    private static final String[] ADDITIVE = {"kills", "deaths", "points", "shards", "shard-minutes"};

    /**
     * SMP stats: a player missing now gets the old entry. A player whose stats were reset by the switch (fewer kills,
     * deaths or shards than before, or the start balance where they had more) gets the old values plus what they
     * earned since.
     */
    private int mergeStats(Path oldFile, Path target) throws IOException {
        if (!Files.exists(target)) {
            Files.createDirectories(target.getParent());
            Files.copy(oldFile, target);
            return YamlConfiguration.loadConfiguration(oldFile.toFile()).getKeys(false).size();
        }

        YamlConfiguration old = YamlConfiguration.loadConfiguration(oldFile.toFile());
        YamlConfiguration cur = YamlConfiguration.loadConfiguration(target.toFile());
        double start = 100.0;
        int n = 0;
        for (String u : old.getKeys(false)) {
            ConfigurationSection o = old.getConfigurationSection(u);
            if (o == null) {
                continue;
            }

            ConfigurationSection c = cur.getConfigurationSection(u);
            if (c == null) {
                cur.set(u, null);
                ConfigurationSection dst = cur.createSection(u);
                for (Map.Entry<String, Object> e : o.getValues(true).entrySet()) {
                    if (!(e.getValue() instanceof ConfigurationSection)) {
                        dst.set(e.getKey(), e.getValue());
                    }
                }

                n++;
                continue;
            }

            boolean reset = false;
            for (String k : ADDITIVE) {
                if (c.getLong(k) < o.getLong(k)) {
                    reset = true;
                }
            }

            if (Math.abs(c.getDouble("balance", start) - start) < 0.01 && o.getDouble("balance", start) > start + 0.01) {
                reset = true;
            }

            if (!reset) {
                continue;
            }

            for (String k : ADDITIVE) {
                c.set(k, o.getLong(k) + c.getLong(k));
            }

            c.set("balance", o.getDouble("balance", start) + Math.max(0.0, c.getDouble("balance", start) - start));
            c.set("hearts", o.getInt("hearts", c.getInt("hearts", 10)));
            c.set("strength", o.getInt("strength", c.getInt("strength", 0)));
            c.set("eliminated-until", Math.max(0L, c.getLong("eliminated-until")));
            n++;
        }

        if (n > 0) {
            cur.save(target.toFile());
        }

        return n;
    }

    // ------------------------------------------------------------------ database

    private void importDatabase(File root, File work) throws IOException {
        File oldDb = new File(root, "data.db");
        if (!oldDb.isFile()) {
            return;
        }

        String type = YamlConfiguration.loadConfiguration(new File(this.data, "config.yml")).getString("database.type", "SQLITE");
        if (type.equalsIgnoreCase("MYSQL")) {
            this.note("Database: MySQL is used - player stats stay in MySQL (nothing to copy).");
            return;
        }

        // the old database with its newest changes: data.db + data.db-wal (+ -shm) read together
        File tmp = new File(work, "olddb");
        Files.createDirectories(tmp.toPath());
        for (String suffix : new String[]{"", "-wal", "-shm"}) {
            File f = new File(root, "data.db" + suffix);
            if (f.isFile()) {
                Files.copy(f.toPath(), new File(tmp, "data.db" + suffix).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        File oldCopy = new File(tmp, "data.db");
        File cur = new File(this.data, "data.db");
        String own = ownPrefix();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException var22) {
        }

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + cur.getAbsolutePath()); Statement st = c.createStatement()) {
            st.execute("ATTACH DATABASE '" + oldCopy.getAbsolutePath().replace("'", "''") + "' AS old");
            String from = null;
            for (String p : PREFIXES) {
                if (exists(c, "old", p + "players")) {
                    from = p;
                    break;
                }
            }

            if (from == null) {
                this.note("Database: no player tables in the old data.db.");
                return;
            }

            int rows = 0;
            int restored = 0;
            for (String t : TABLES) {
                String ot = from + t;
                String nt = own + t;
                if (!exists(c, "old", ot)) {
                    continue;
                }

                if (!exists(c, "main", nt)) {
                    // this table doesn't exist yet: the plugin creates it on start; copy the old one under our name
                    st.executeUpdate("CREATE TABLE \"" + nt + "\" AS SELECT * FROM old.\"" + ot + "\"");
                    rows += count(c, "main", nt);
                    continue;
                }

                List<String> cols = commonColumns(c, ot, nt);
                if (cols.isEmpty()) {
                    continue;
                }

                if (t.equals("kit_stats")) {
                    restored += this.mergeKitStats(c, ot, nt, cols);
                } else if (t.equals("players")) {
                    restored += this.mergePlayers(c, ot, nt, cols);
                }

                String list = String.join(", ", cols.stream().map(x -> "\"" + x + "\"").toList());
                if (t.equals("matches")) {
                    // history rows have their own ids: add the old ones that aren't there (by time and players)
                    rows += st.executeUpdate("INSERT INTO \"" + nt + "\" (" + list.replace("\"id\", ", "") + ") SELECT " + list.replace("\"id\", ", "") + " FROM old.\"" + ot
                        + "\" o WHERE NOT EXISTS (SELECT 1 FROM \"" + nt + "\" n WHERE n.ended_at = o.ended_at AND n.winner IS o.winner AND n.loser IS o.loser)");
                } else {
                    rows += st.executeUpdate("INSERT OR IGNORE INTO \"" + nt + "\" (" + list + ") SELECT " + list + " FROM old.\"" + ot + "\"");
                }
            }

            st.execute("DETACH DATABASE old");
            this.note("Database: " + rows + " missing rows added (players, stats, kit layouts, friends, cosmetics, matches...), " + restored
                + " reset players/stats given back their old values plus what they earned since");
        } catch (SQLException ex) {
            this.plugin.getLogger().log(Level.WARNING, "[import] Database merge failed (files were still imported)", ex);
            this.note("Database: merge failed - " + ex.getMessage());
        }
    }

    private int mergeKitStats(Connection c, String ot, String nt, List<String> cols) throws SQLException {
        int n = 0;
        // a row whose games went DOWN was reset: old values + what was earned since (stats start at 0)
        String games = "(wins + losses + unranked_wins + unranked_losses)";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
            "SELECT n.uuid, n.kit FROM \"" + nt + "\" n JOIN old.\"" + ot + "\" o ON o.uuid = n.uuid AND o.kit = n.kit WHERE " + games.replace("(", "(n.").replace(" + ", " + n.")
                + " < " + games.replace("(", "(o.").replace(" + ", " + o."))) {
            List<String[]> keys = new ArrayList<>();
            while (rs.next()) {
                keys.add(new String[]{rs.getString(1), rs.getString(2)});
            }

            for (String[] k : keys) {
                StringBuilder set = new StringBuilder();
                for (String col : List.of("elo", "wins", "losses", "ranked_games", "unranked_wins", "unranked_losses")) {
                    if (cols.contains(col)) {
                        set.append(set.length() == 0 ? "" : ", ").append(col).append(" = ").append(col).append(" + (SELECT o.").append(col).append(" FROM old.\"").append(ot)
                            .append("\" o WHERE o.uuid = ? AND o.kit = ?)");
                    }
                }

                for (String col : List.of("best_streak", "peak_elo")) {
                    if (cols.contains(col)) {
                        set.append(", ").append(col).append(" = MAX(").append(col).append(", (SELECT o.").append(col).append(" FROM old.\"").append(ot)
                            .append("\" o WHERE o.uuid = ? AND o.kit = ?))");
                    }
                }

                if (cols.contains("tier")) {
                    set.append(", tier = CASE WHEN tier < 0 THEN (SELECT o.tier FROM old.\"").append(ot).append("\" o WHERE o.uuid = ? AND o.kit = ?) ELSE tier END");
                }

                try (java.sql.PreparedStatement ps = c.prepareStatement("UPDATE \"" + nt + "\" SET " + set + " WHERE uuid = ? AND kit = ?")) {
                    int params = set.toString().split("\\?", -1).length - 1;
                    for (int i = 0; i < params; i += 2) {
                        ps.setString(i + 1, k[0]);
                        ps.setString(i + 2, k[1]);
                    }

                    ps.setString(params + 1, k[0]);
                    ps.setString(params + 2, k[1]);
                    n += ps.executeUpdate();
                }
            }
        }

        return n;
    }

    private int mergePlayers(Connection c, String ot, String nt, List<String> cols) throws SQLException {
        int n = 0;
        try (Statement st = c.createStatement()) {
            if (cols.contains("ffa_kills")) {
                n += st.executeUpdate("UPDATE \"" + nt + "\" SET ffa_kills = ffa_kills + (SELECT o.ffa_kills FROM old.\"" + ot + "\" o WHERE o.uuid = \"" + nt + "\".uuid), "
                    + "ffa_deaths = ffa_deaths + (SELECT o.ffa_deaths FROM old.\"" + ot + "\" o WHERE o.uuid = \"" + nt + "\".uuid), "
                    + "ffa_best_streak = MAX(ffa_best_streak, (SELECT o.ffa_best_streak FROM old.\"" + ot + "\" o WHERE o.uuid = \"" + nt + "\".uuid)) "
                    + "WHERE EXISTS (SELECT 1 FROM old.\"" + ot + "\" o WHERE o.uuid = \"" + nt + "\".uuid AND o.ffa_kills + o.ffa_deaths > \"" + nt + "\".ffa_kills + \"" + nt
                    + "\".ffa_deaths)");
            }

            if (cols.contains("first_join")) {
                st.executeUpdate("UPDATE \"" + nt + "\" SET first_join = (SELECT o.first_join FROM old.\"" + ot + "\" o WHERE o.uuid = \"" + nt + "\".uuid) "
                    + "WHERE EXISTS (SELECT 1 FROM old.\"" + ot + "\" o WHERE o.uuid = \"" + nt + "\".uuid AND o.first_join > 0 AND (o.first_join < \"" + nt
                    + "\".first_join OR \"" + nt + "\".first_join = 0))");
            }

            if (cols.contains("settings")) {
                st.executeUpdate("UPDATE \"" + nt + "\" SET settings = (SELECT o.settings FROM old.\"" + ot + "\" o WHERE o.uuid = \"" + nt + "\".uuid) "
                    + "WHERE (settings IS NULL OR settings = '') AND EXISTS (SELECT 1 FROM old.\"" + ot + "\" o WHERE o.uuid = \"" + nt + "\".uuid AND o.settings IS NOT NULL AND o.settings <> '')");
            }
        }

        return n;
    }

    private static boolean exists(Connection c, String schema, String table) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT 1 FROM " + schema + ".sqlite_master WHERE type='table' AND name='" + table + "'")) {
            return rs.next();
        }
    }

    private static int count(Connection c, String schema, String table) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + schema + ".\"" + table + "\"")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static List<String> columns(Connection c, String schema, String table) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA " + schema + ".table_info(\"" + table + "\")")) {
            while (rs.next()) {
                out.add(rs.getString("name"));
            }
        }

        return out;
    }

    private static List<String> commonColumns(Connection c, String ot, String nt) throws SQLException {
        List<String> mine = columns(c, "main", nt);
        List<String> theirs = columns(c, "old", ot);
        List<String> out = new ArrayList<>();
        for (String m : mine) {
            if (theirs.contains(m)) {
                out.add(m);
            }
        }

        return out;
    }

    // ------------------------------------------------------------------ inventories and ender chests

    /**
     * Saved inventories: a group file or PvP-lobby store that doesn't exist now is copied. One that exists but lost
     * its ender chest (or holds only hub items now) gets the old one queued: handed over in game the next time the
     * player is in that world, merged into the free ender chest slots, with what they hold now saved first.
     */
    private void importInventories(File root) throws IOException {
        File oldInv = new File(root, "inventories");
        if (!oldInv.isDirectory()) {
            return;
        }

        File inv = new File(this.data, "inventories");
        int copied = 0;
        int queued = 0;
        File[] stores = oldInv.listFiles(f -> f.isFile() && f.getName().endsWith(".yml"));
        if (stores != null) {
            for (File f : stores) {
                File t = new File(inv, f.getName());
                if (!t.exists()) {
                    Files.createDirectories(inv.toPath());
                    Files.copy(f.toPath(), t.toPath());
                    copied++;
                }
            }
        }

        File[] players = new File(oldInv, "groups").listFiles(File::isDirectory);
        if (players != null) {
            for (File dir : players) {
                File[] groups = dir.listFiles((d, n) -> n.endsWith(".yml") && !n.contains(".prev") && !n.contains("-"));
                if (groups == null) {
                    continue;
                }

                for (File g : groups) {
                    File t = new File(inv, "groups/" + dir.getName() + "/" + g.getName());
                    if (!t.exists()) {
                        Files.createDirectories(t.getParentFile().toPath());
                        Files.copy(g.toPath(), t.toPath());
                        copied++;
                        continue;
                    }

                    if (g.getName().equals("hub.yml")) {
                        continue;
                    }

                    YamlConfiguration o = YamlConfiguration.loadConfiguration(g);
                    YamlConfiguration c = YamlConfiguration.loadConfiguration(t);
                    boolean enderLost = items(o.getString("enderchest")) > 0 && items(c.getString("enderchest")) == 0;
                    boolean invLost = items(o.getString("contents")) > 0 && onlyHubItems(c.getString("contents"));
                    if (!enderLost && !invLost) {
                        continue;
                    }

                    File pending = new File(inv, "restore-pending/" + dir.getName() + ".yml");
                    YamlConfiguration p = YamlConfiguration.loadConfiguration(pending);
                    if (p.isConfigurationSection("entries") && !p.getConfigurationSection("entries").getKeys(false).isEmpty()) {
                        // something is already waiting for this player: don't hand items over twice
                        continue;
                    }

                    String key = "entries.import" + System.nanoTime();
                    p.set(key + ".group", g.getName().replace(".yml", "").toUpperCase(Locale.ROOT));
                    p.set(key + ".mode", invLost ? (enderLost ? "all" : "inventory") : "enderchest");
                    p.set(key + ".label", "the imported old plugin folder");
                    p.set(key + ".by", "import");
                    p.set(key + ".contents", o.getString("contents", ""));
                    p.set(key + ".enderchest", o.getString("enderchest", ""));
                    Files.createDirectories(pending.getParentFile().toPath());
                    p.save(pending);
                    queued++;
                }
            }
        }

        this.note("Inventories: " + copied + " saved inventories that were missing added, " + queued
            + " lost ender chests/inventories queued - players get them back the next time they are in that world");
    }

    private static int items(String b64) {
        if (b64 == null || b64.isEmpty()) {
            return 0;
        }

        try {
            int n = 0;
            for (org.bukkit.inventory.ItemStack it : org.bukkit.inventory.ItemStack.deserializeItemsFromBytes(java.util.Base64.getDecoder().decode(b64))) {
                if (it != null && !it.isEmpty()) {
                    n++;
                }
            }

            return n;
        } catch (RuntimeException var6) {
            return 0;
        }
    }

    /** True when an inventory is empty or only holds hub items (hub selector...): the SMP items are gone. */
    private static boolean onlyHubItems(String b64) {
        if (b64 == null || b64.isEmpty()) {
            return true;
        }

        try {
            for (org.bukkit.inventory.ItemStack it : org.bukkit.inventory.ItemStack.deserializeItemsFromBytes(java.util.Base64.getDecoder().decode(b64))) {
                if (it == null || it.isEmpty()) {
                    continue;
                }

                boolean hub = it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().getKeys().stream().anyMatch(k -> k.getKey().equals("hub_item"));
                if (!hub) {
                    return false;
                }
            }

            return true;
        } catch (RuntimeException var5) {
            return false;
        }
    }

    // ------------------------------------------------------------------ archives and folders

    /** The folder holding the plugin files (config.yml or kits.yml), searched a few levels down. */
    static File findRoot(File dir) {
        if (new File(dir, "config.yml").isFile() && (new File(dir, "kits.yml").isFile() || new File(dir, "data.db").isFile() || new File(dir, "arenas.yml").isFile())) {
            return dir;
        }

        File[] subs = dir.listFiles(File::isDirectory);
        if (subs != null) {
            for (File s : subs) {
                if (s.getName().startsWith(".") || s.getName().startsWith("__")) {
                    continue;
                }

                File r = findRoot(s);
                if (r != null) {
                    return r;
                }
            }
        }

        return null;
    }

    /** Unpacks a .zip, .tar, .tar.gz or .tgz (detected from the content, whatever the name says). */
    static void unpack(File archive, File out) throws IOException {
        Files.createDirectories(out.toPath());
        Path root = out.toPath().toAbsolutePath().normalize();
        byte[] head = new byte[4];
        try (InputStream in = new FileInputStream(archive)) {
            int r = in.read(head);
            if (r < 2) {
                throw new IOException("empty file");
            }
        }

        if (head[0] == 'P' && head[1] == 'K') {
            try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
                ZipEntry e;
                while ((e = z.getNextEntry()) != null) {
                    Path t = root.resolve(e.getName()).normalize();
                    if (!t.startsWith(root)) {
                        continue;
                    }

                    if (e.isDirectory()) {
                        Files.createDirectories(t);
                    } else {
                        Files.createDirectories(t.getParent());
                        Files.copy(z, t, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            return;
        }

        InputStream raw = new BufferedInputStream(new FileInputStream(archive));
        InputStream in = (head[0] & 0xFF) == 0x1F && (head[1] & 0xFF) == 0x8B ? new GZIPInputStream(raw, 65536) : raw;
        try (InputStream tar = in) {
            untar(tar, root);
        }
    }

    /** A small reader for the tar format (ustar and GNU long names). */
    private static void untar(InputStream in, Path root) throws IOException {
        byte[] header = new byte[512];
        String longName = null;
        while (true) {
            if (in.readNBytes(header, 0, 512) < 512) {
                return;
            }

            boolean empty = true;
            for (byte b : header) {
                if (b != 0) {
                    empty = false;
                    break;
                }
            }

            if (empty) {
                return;
            }

            String name = cstr(header, 0, 100);
            String prefix = cstr(header, 345, 155);
            long size = Long.parseLong(cstr(header, 124, 12).trim().isEmpty() ? "0" : cstr(header, 124, 12).trim(), 8);
            char type = (char)header[156];
            if (!prefix.isEmpty() && (header[257] == 'u')) {
                name = prefix + "/" + name;
            }

            if (longName != null) {
                name = longName;
                longName = null;
            }

            long padded = (size + 511) / 512 * 512;
            if (type == 'L') {
                byte[] b = in.readNBytes((int)padded);
                longName = cstr(b, 0, (int)size);
                continue;
            }

            if (type == 'x') {
                // PAX header (hosting panels make these): "len path=value" records
                String pax = new String(in.readNBytes((int)padded), 0, (int)size, StandardCharsets.UTF_8);
                for (String rec : pax.split("\n")) {
                    int sp = rec.indexOf(' ');
                    if (sp > 0 && rec.startsWith("path=", sp + 1)) {
                        longName = rec.substring(sp + 6);
                    }
                }

                continue;
            }

            Path t = root.resolve(name).normalize();
            if (!t.startsWith(root) || type == 'g') {
                in.skipNBytes(padded);
                continue;
            }

            if (type == '5' || name.endsWith("/")) {
                Files.createDirectories(t);
                in.skipNBytes(padded);
            } else if (type == '0' || type == 0) {
                Files.createDirectories(t.getParent());
                try (java.io.OutputStream o = Files.newOutputStream(t)) {
                    long left = size;
                    byte[] buf = new byte[65536];
                    while (left > 0) {
                        int r = in.read(buf, 0, (int)Math.min(buf.length, left));
                        if (r < 0) {
                            throw new IOException("archive cut short");
                        }

                        o.write(buf, 0, r);
                        left -= r;
                    }
                }

                in.skipNBytes(padded - size);
            } else {
                in.skipNBytes(padded);
            }
        }
    }

    private static String cstr(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && end < b.length && b[end] != 0) {
            end++;
        }

        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(p)) {
            for (Path x : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(x);
            }
        }
    }

    // ------------------------------------------------------------------ /pvpadmin import

    /** /pvpadmin import [from <PracticePvP|VortexRealm|VortexPvP>]: explains, or stages an old folder for the next start. */
    public static void command(PracticePlugin plugin, CommandSender s, String[] a) {
        File importDir = new File(plugin.getDataFolder(), "import");
        if (a.length >= 3 && a[1].equalsIgnoreCase("from")) {
            File plugins = plugin.getDataFolder().getAbsoluteFile().getParentFile();
            File src = new File(plugins, a[2]);
            if (!src.isDirectory() || src.getAbsoluteFile().equals(plugin.getDataFolder().getAbsoluteFile()) || findRoot(src) == null) {
                s.sendMessage(Text.mm("<red>No plugin folder called " + Text.esc(a[2]) + " in plugins/ (or it is this plugin's own)."));
                return;
            }

            try {
                Path to = new File(importDir, src.getName()).toPath();
                deleteTree(to);
                Files.createDirectories(to);
                try (Stream<Path> walk = Files.walk(src.toPath())) {
                    for (Path f : walk.toList()) {
                        String rel = src.toPath().relativize(f).toString();
                        if (rel.startsWith("logs") || rel.startsWith("backups")) {
                            continue;
                        }

                        Path t = to.resolve(rel);
                        if (Files.isDirectory(f)) {
                            Files.createDirectories(t);
                        } else {
                            Files.copy(f, t, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }

                s.sendMessage(Text.mm("<green>✔ " + Text.esc(src.getName()) + " is ready to import. <white>Restart the server</white> <green>to bring everything back."));
            } catch (IOException ex) {
                s.sendMessage(Text.mm("<red>Could not copy it: " + Text.esc(ex.getMessage())));
            }

            return;
        }

        s.sendMessage(Text.mm("<gold><b>Import an old plugin folder</b>"));
        s.sendMessage(Text.mm("<gray>1. Put the old folder (PracticePvP, VortexRealm or VortexPvP) - or a .zip / .tar.gz of it - in <white>plugins/"
            + Text.esc(plugin.getDataFolder().getName()) + "/import/</white>"));
        s.sendMessage(Text.mm("<gray>   or, if it is still in plugins/: <white>/pvpadmin import from PracticePvP"));
        s.sendMessage(Text.mm("<gray>2. Restart. Server setup comes back as it was; player data is merged (nothing current is overwritten)."));
        s.sendMessage(Text.mm("<gray>A full backup is saved first. The result is in <white>import/LAST-IMPORT.txt</white>."));
        if (!lastReport.isEmpty()) {
            s.sendMessage(Text.mm("<gold>Last import:"));
            for (String line : lastReport) {
                s.sendMessage(Text.mm("<gray>• " + Text.esc(line)));
            }
        }
    }
}
