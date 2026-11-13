package net.vortexrealm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;
import net.vortexrealm.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

/**
 * Moves an existing PracticePvP install over to VortexRealm: configs, arenas, kits and the database
 * (tables pp_* become vr_*, permission nodes practice.* become vortex.*). Runs automatically on the first
 * start when plugins/VortexRealm is still empty; /vortex migrate does the MySQL table copy on demand.
 */
public final class VortexMigration implements TabExecutor {
    static final String[] TABLES = new String[]{"players", "kit_stats", "layouts", "matches", "meta", "friends", "follows", "mutes", "cosmetics", "ips", "academy"};
    private static final Pattern PERM = Pattern.compile("(?<![\\w.])practice\\.(?=[a-z])");
    private final VortexRealm plugin;

    VortexMigration(VortexRealm plugin) {
        this.plugin = plugin;
    }

    private File oldFolder() {
        return new File(this.plugin.getDataFolder().getParentFile(), "PracticePvP");
    }

    private File marker() {
        return new File(this.plugin.getDataFolder(), ".migrated-from-practicepvp");
    }

    /** Called before the database opens. Returns true when files were copied. */
    boolean autoMigrate() {
        File old = this.oldFolder();
        File folder = this.plugin.getDataFolder();
        boolean empty = !new File(folder, "config.yml").exists() && !new File(folder, "data.db").exists();
        if (old.isDirectory() && empty && !this.marker().exists()) {
            try {
                this.copyFiles(old.toPath(), folder.toPath());
                File db = new File(folder, "data.db");
                if (db.exists()) {
                    this.renameSqlite(db);
                }

                Files.writeString(this.marker().toPath(), "Migrated from " + old.getAbsolutePath() + "\n", StandardCharsets.UTF_8);
                this.plugin.getLogger().info("Moved your PracticePvP data (configs, arenas, kits, ranks, database) to VortexRealm.");
                return true;
            } catch (IOException | SQLException var5) {
                this.plugin.getLogger().log(Level.SEVERE, "Could not migrate the PracticePvP data - VortexRealm starts fresh. Your old folder is untouched.", var5);
            }
        } else if (old.isDirectory() && !empty) {
            this.fillMissing(old, folder);
        }

        return false;
    }

    /**
     * Both plugins have data (VortexRealm was tried before, or PracticePvP kept running): anything PracticePvP has that
     * VortexRealm lacks is copied over - files that don't exist yet, and database rows (players, stats, layouts,
     * friends...) VortexRealm doesn't have. Nothing on the VortexRealm side is ever overwritten.
     */
    private void fillMissing(File old, File folder) {
        try {
            int[] files = new int[1];
            Path from = old.toPath();
            Path to = folder.toPath();
            Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String name = file.getFileName().toString();
                    Path target = to.resolve(from.relativize(file).toString());
                    if (name.endsWith("-shm") || name.endsWith("-wal") || name.endsWith(".lock") || name.equals("data.db") || Files.exists(target)) {
                        return FileVisitResult.CONTINUE;
                    }

                    Files.createDirectories(target.getParent());
                    if (name.endsWith(".yml")) {
                        Files.writeString(target, PERM.matcher(Files.readString(file, StandardCharsets.UTF_8)).replaceAll("vortex."), StandardCharsets.UTF_8);
                    } else {
                        Files.copy(file, target);
                    }

                    files[0]++;
                    return FileVisitResult.CONTINUE;
                }
            });
            int rows = 0;
            File oldDb = new File(old, "data.db");
            File newDb = new File(folder, "data.db");
            if (oldDb.isFile() && newDb.isFile()) {
                try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + newDb.getAbsolutePath()); Statement st = c.createStatement()) {
                    st.execute("ATTACH DATABASE '" + oldDb.getAbsolutePath().replace("'", "''") + "' AS old");

                    for (String t : TABLES) {
                        if (existsIn(c, "old", "pp_" + t) && exists(c, "vr_" + t)) {
                            rows += st.executeUpdate("INSERT OR IGNORE INTO vr_" + t + " SELECT * FROM old.pp_" + t);
                        }
                    }

                    st.execute("DETACH DATABASE old");
                } catch (SQLException var11) {
                    this.plugin.getLogger().log(Level.WARNING, "Could not copy missing PracticePvP database rows (files were still copied)", var11);
                }
            }

            if (files[0] > 0 || rows > 0) {
                this.plugin.getLogger().info("Copied what VortexRealm was missing from PracticePvP: " + files[0] + " files, " + rows + " database rows (nothing was overwritten).");
            }
        } catch (IOException var12) {
            this.plugin.getLogger().log(Level.WARNING, "Could not compare with the PracticePvP folder", var12);
        }
    }

    private static boolean existsIn(Connection c, String schema, String table) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT name FROM " + schema + ".sqlite_master WHERE type='table' AND name='" + table + "'")) {
            return rs.next();
        }
    }

    private void copyFiles(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                // data.db-wal / -shm are copied too: the newest database changes live there until SQLite folds them in
                if (name.endsWith(".lock")) {
                    return FileVisitResult.CONTINUE;
                }

                Path target = to.resolve(from.relativize(file).toString());
                if (name.endsWith(".yml")) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    Files.writeString(target, PERM.matcher(text).replaceAll("vortex."), StandardCharsets.UTF_8);
                } else {
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void renameSqlite(File db) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath()); Statement st = c.createStatement()) {
            for (String t : TABLES) {
                if (exists(c, "pp_" + t) && !exists(c, "vr_" + t)) {
                    st.executeUpdate("ALTER TABLE pp_" + t + " RENAME TO vr_" + t);
                }
            }

            try {
                st.executeUpdate("DROP INDEX IF EXISTS idx_pp_kit_elo");
            } catch (SQLException var6) {
            }
        }
    }

    private static boolean exists(Connection c, String table) throws SQLException {
        try (ResultSet rs = c.getMetaData().getTables(null, null, table, null)) {
            return rs.next();
        }
    }

    /** MySQL installs share one database: copy pp_* rows into the vr_* tables. */
    private int copyMysql() throws Exception {
        return this.plugin.db().<Integer>supply(c -> {
            int rows = 0;
            try (Statement st = c.createStatement()) {
                for (String t : TABLES) {
                    if (exists(c, "pp_" + t)) {
                        st.executeUpdate("CREATE TABLE IF NOT EXISTS vr_" + t + " LIKE pp_" + t);
                        rows += st.executeUpdate("INSERT IGNORE INTO vr_" + t + " SELECT * FROM pp_" + t);
                    }
                }
            }

            return rows;
        }).get();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("migrate")) {
            if (!sender.hasPermission("vortex.admin")) {
                sender.sendMessage(Text.mm("<red>You don't have permission."));
            } else if (this.plugin.db() != null && this.plugin.db().mysql()) {
                try {
                    int rows = this.copyMysql();
                    sender.sendMessage(Text.mm("<green>Copied <white>" + rows + "</white> rows from the PracticePvP tables. <gray>Restart the server to load them."));
                } catch (Exception var6) {
                    sender.sendMessage(Text.mm("<red>Migration failed: " + Text.esc(String.valueOf(var6.getMessage()))));
                }
            } else if (this.marker().exists()) {
                sender.sendMessage(Text.mm("<green>Your PracticePvP data was already moved on the first start."));
            } else {
                sender.sendMessage(
                    Text.mm(
                        "<gray>To move PracticePvP data: stop the server, empty <white>plugins/VortexRealm</white> (keep <white>plugins/PracticePvP</white>), and start again - it is copied automatically."
                    )
                );
            }
        } else {
            sender.sendMessage(
                Text.mm(
                    "<gradient:#9B5CFF:#4FC3FF><b>Vortex Realm</b></gradient> <gray>v"
                        + this.plugin.getPluginMeta().getVersion()
                        + " <dark_gray>- <gray>MCPVP-style practice, SMP and more. <white>/vortex migrate"
                )
            );
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && "migrate".startsWith(args[0].toLowerCase())) {
            out.add("migrate");
        }

        return out;
    }
}
