package net.practicepvp.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class DataVault {
    private static final int KEEP = 6;
    private static final int KEEP_UPDATES = 3;
    private static final Set<String> SKIP = Set.of("data.db", "data.db-wal", "data.db-shm", "data.db.vault-copy");
    private final JavaPlugin plugin;
    private final File data;
    private final File vault;
    private volatile boolean busy;
    /** Set on the first start after an update: the version it came from and the backup taken before anything loaded. */
    public String updatedFrom;
    public String updateSnapshot;

    public DataVault(JavaPlugin plugin) {
        this.plugin = plugin;
        this.data = plugin.getDataFolder();
        this.vault = new File(this.data.getAbsoluteFile().getParentFile(), "VortexRealm-Vault");
    }

    public File dir() {
        return this.vault;
    }

    public List<String> files() {
        File[] fs = this.vault.listFiles((d, n) -> n.endsWith(".zip"));
        if (fs == null) {
            return List.of();
        } else {
            Arrays.sort(fs, Comparator.comparingLong(File::lastModified).reversed());
            List<String> out = new ArrayList<>();

            for (File f : fs) {
                out.add(f.getName());
            }

            return out;
        }
    }

    public void beforeLoad() {
        try {
            if (!this.vault.exists() && !this.vault.mkdirs()) {
                this.plugin.getLogger().warning("Cannot create " + this.vault + " - update-safe snapshots are off.");
                return;
            }

            File marker = new File(this.vault, "restore-next-start.txt");
            String restore = null;
            boolean auto = false;
            if (marker.isFile()) {
                restore = Files.readString(marker.toPath(), StandardCharsets.UTF_8).trim();
                Files.deleteIfExists(marker.toPath());
            } else if (!new File(this.data, "config.yml").exists()) {
                List<String> fs = this.files();
                if (!fs.isEmpty()) {
                    restore = fs.get(0);
                    auto = true;
                }
            }

            if (restore != null && !restore.isEmpty()) {
                File zip = new File(this.vault, new File(restore).getName());
                if (zip.isFile()) {
                    if (!auto && new File(this.data, "config.yml").exists()) {
                        this.snapshotRaw("before-restore");
                    }

                    // a fresh plugin folder only gets the plugin data back: players keep what they carry now
                    int n = this.unzip(zip, !auto);
                    this.plugin.getLogger().warning("=================================================================");
                    this.plugin.getLogger().warning((auto ? "Fresh plugin folder: " : "Requested: ") + "restored " + n + " files from " + zip.getName());
                    this.plugin.getLogger().warning("Settings, kits, arenas, worlds, NPCs and player data are back" + (auto ? "." : ", with player inventories."));
                    this.plugin.getLogger().warning("=================================================================");
                } else {
                    this.plugin.getLogger().warning("Vault restore: no snapshot called " + restore);
                }
            }

            File state = new File(this.vault, "state.yml");
            YamlConfiguration y = YamlConfiguration.loadConfiguration(state);
            String last = y.getString("last-version");
            String now = this.plugin.getPluginMeta().getVersion() + "#" + this.jarStamp();
            if (last != null && !last.equals(now) && new File(this.data, "config.yml").exists()) {
                this.updatedFrom = last.contains("#") ? last.substring(0, last.indexOf('#')) : last;
                this.updateSnapshot = this.snapshotRaw("pre-update");
                this.plugin.getLogger().info("Plugin updated - the old data is saved in the vault (pre-update snapshot).");
            } else if (last == null && new File(this.data, "config.yml").exists()) {
                // first start of this plugin on existing data (for example data just moved over from another of our
                // plugins): keep a copy of it as it is now, before any player joins
                this.updateSnapshot = this.snapshotRaw("first-start");
                this.plugin.getLogger().info("First start on existing data - a copy is saved in the vault (first-start snapshot).");
            }

            y.set("last-version", now);
            y.set("plugin-folder", this.data.getAbsolutePath());
            y.save(state);
        } catch (RuntimeException | IOException var8) {
            this.plugin.getLogger().log(Level.WARNING, "Vault start-up step failed (the plugin keeps working)", (Throwable)var8);
        }
    }

    private String jarStamp() {
        try {
            File jar = new File(this.plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            return Long.toHexString(jar.length() ^ jar.lastModified() / 1000L);
        } catch (Exception var2) {
            return "?";
        }
    }

    public boolean scheduleRestore(String file) throws IOException {
        File zip = new File(this.vault, new File(file).getName());
        if (!zip.isFile()) {
            return false;
        } else {
            Files.writeString(new File(this.vault, "restore-next-start.txt").toPath(), zip.getName(), StandardCharsets.UTF_8);
            return true;
        }
    }

    public synchronized String snapshot(String reason, DataVault.DbCopier dbCopy) throws IOException {
        if (this.busy) {
            throw new IOException("a snapshot is already running");
        } else {
            this.busy = true;
            File copy = new File(this.data, "data.db.vault-copy");

            String e;
            try {
                Files.deleteIfExists(copy.toPath());
                boolean haveDb = false;
                if (dbCopy != null) {
                    try {
                        haveDb = dbCopy.copyTo(copy);
                    } catch (Exception var9) {
                        this.plugin.getLogger().log(Level.WARNING, "Vault: database copy failed, snapshot without it", (Throwable)var9);
                    }
                }

                e = this.write(reason, haveDb ? copy : null);
            } finally {
                Files.deleteIfExists(copy.toPath());
                this.busy = false;
            }

            return e;
        }
    }

    private String snapshotRaw(String reason) throws IOException {
        File db = new File(this.data, "data.db");
        if (db.isFile() && new File(this.data, "data.db-wal").isFile()) {
            // the newest changes sit in data.db-wal until SQLite folds them in: fold them in first, so the backup has them
            try {
                Class.forName("org.sqlite.JDBC");
                try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
                    java.sql.Statement st = c.createStatement()) {
                    st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                }
            } catch (Exception ex) {
                this.plugin.getLogger().log(Level.WARNING, "Vault: could not fold data.db-wal into data.db - the backup also keeps the -wal file", ex);
            }
        }

        return this.write(reason, db.isFile() ? db : null);
    }

    /** A backup of the plugin folder as it is now, before the plugin loads it (used by the import). */
    public String snapshotBefore(String reason) throws IOException {
        return this.snapshotRaw(reason);
    }

    private String write(String reason, File dbFile) throws IOException {
        if (!this.vault.exists() && !this.vault.mkdirs()) {
            throw new IOException("cannot create " + this.vault);
        } else {
            String name = "vault-"
                + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date())
                + (reason != null && !reason.isBlank() ? "-" + reason : "")
                + ".zip";
            File target = new File(this.vault, name);
            File tmp = new File(this.vault, name + ".part");
            Path root = this.data.toPath();

            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(tmp))) {
                zip.setLevel(1);

                List<Path> all;
                try (Stream<Path> walk = Files.walk(root)) {
                    all = walk.filter(x$0 -> Files.isRegularFile(x$0)).toList();
                }

                for (Path p : all) {
                    String rel = root.relativize(p).toString().replace('\\', '/');
                    if (!SKIP.contains(rel) && !rel.startsWith("logs/") && !rel.endsWith(".lock") && !rel.endsWith(".tmp")) {
                        add(zip, rel, p.toFile());
                    }
                }

                if (dbFile != null) {
                    add(zip, "data.db", dbFile);
                    if (dbFile.equals(new File(this.data, "data.db"))) {
                        // a raw copy (before the plugin opened it): keep the newest changes too
                        for (String extra : new String[]{"data.db-wal", "data.db-shm"}) {
                            File f = new File(this.data, extra);
                            if (f.isFile() && f.length() > 0) {
                                add(zip, extra, f);
                            }
                        }
                    }
                }

                this.addPlayerData(zip);
            }

            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            this.prune();
            return name;
        }
    }

    /** Folders inside a world that hold player state: inventory, ender chest, XP, advancements, statistics. */
    static final List<String> PLAYER_DIRS = List.of("playerdata", "players", "advancements", "stats");
    /** Zip folder for world player data (restored into the server folder, not the plugin folder). */
    static final String WORLDS = "worlds-playerdata/";

    /** The server folder (where the worlds are). */
    private File serverDir() {
        File plugins = this.data.getAbsoluteFile().getParentFile();
        return plugins == null ? null : plugins.getParentFile();
    }

    /** Every world's player files, so a backup also has what players are carrying right now. */
    private void addPlayerData(ZipOutputStream zip) throws IOException {
        File server = this.serverDir();
        File[] worlds = server == null ? null : server.listFiles(f -> f.isDirectory() && new File(f, "level.dat").isFile());
        if (worlds != null) {
            for (File w : worlds) {
                for (String dir : PLAYER_DIRS) {
                    File[] fs = new File(w, dir).listFiles(File::isFile);
                    if (fs != null) {
                        for (File f : fs) {
                            if (!f.getName().endsWith(".tmp")) {
                                add(zip, WORLDS + w.getName() + "/" + dir + "/" + f.getName(), f);
                            }
                        }
                    }
                }
            }
        }
    }

    /** How many player files a snapshot holds (for the Backups page). */
    public int playerFiles(String name) {
        int n = 0;

        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(new File(this.vault, new File(name).getName()))) {
            java.util.Enumeration<? extends ZipEntry> en = z.entries();

            while (en.hasMoreElements()) {
                if (en.nextElement().getName().startsWith(WORLDS)) {
                    n++;
                }
            }
        } catch (IOException var6) {
        }

        return n;
    }

    private static void add(ZipOutputStream zip, String rel, File f) throws IOException {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(f.toPath());
        } catch (IOException var5) {
            return;
        }

        zip.putNextEntry(new ZipEntry(rel));
        zip.write(bytes);
        zip.closeEntry();
    }

    private void prune() {
        List<String> regular = new ArrayList<>();
        List<String> updates = new ArrayList<>();

        for (String f : this.files()) {
            (f.contains("pre-update") || f.contains("first-start") ? updates : regular).add(f);
        }

        for (int i = 6; i < regular.size(); i++) {
            this.delete(regular.get(i));
        }

        for (int i = 3; i < updates.size(); i++) {
            this.delete(updates.get(i));
        }
    }

    private void delete(String name) {
        File f = new File(this.vault, name);
        if (f.exists() && !f.delete()) {
            this.plugin.getLogger().warning("Could not delete old snapshot " + name);
        }
    }

    private int unzip(File zipFile, boolean players) throws IOException {
        Path root = this.data.toPath().toAbsolutePath().normalize();
        File sd = this.serverDir();
        Path server = sd == null ? null : sd.toPath().toAbsolutePath().normalize();
        Files.createDirectories(root);
        int n = 0;

        ZipEntry e;
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zipFile))) {
            while ((e = zin.getNextEntry()) != null) {
                Path out;
                if (e.getName().startsWith(WORLDS)) {
                    // player files go back into the worlds, only when an admin asked for this restore
                    if (!players || server == null) {
                        continue;
                    }

                    String[] parts = e.getName().substring(WORLDS.length()).split("/");
                    if (parts.length != 3 || !PLAYER_DIRS.contains(parts[1])) {
                        continue;
                    }

                    out = server.resolve(parts[0]).resolve(parts[1]).resolve(parts[2]).normalize();
                    if (!out.startsWith(server)) {
                        continue;
                    }
                } else {
                    out = root.resolve(e.getName()).normalize();
                }

                if (out.startsWith(root) || server != null && out.startsWith(server) && e.getName().startsWith(WORLDS)) {
                    if (e.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        if (e.getName().equals("data.db")) {
                            Files.deleteIfExists(root.resolve("data.db-wal"));
                            Files.deleteIfExists(root.resolve("data.db-shm"));
                        }

                        Files.createDirectories(out.getParent());

                        try (OutputStream os = new FileOutputStream(out.toFile())) {
                            zin.transferTo(os);
                        }

                        n++;
                    }
                }
            }
        }

        return n;
    }

    @FunctionalInterface
    public interface DbCopier {
        boolean copyTo(File var1) throws Exception;
    }
}
