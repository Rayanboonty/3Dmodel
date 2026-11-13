package net.vortexpvp.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.util.Tasks;

public final class ConfigBackup {
    private static final String PLAYER_RANKS = "player-ranks.yml";
    private static final List<String> DATA = List.of(
        "welcome.yml", "countries.yml", "reports.yml", "orders.yml", "auction-house.yml", "last-locations.yml", "bots.yml", "penalties.yml", "events.yml"
    );
    private final VortexPvP plugin;

    public ConfigBackup(VortexPvP plugin) {
        this.plugin = plugin;
    }

    public File dir() {
        File d = new File(new File(this.plugin.getDataFolder().getParentFile(), "VortexPvP-Test-Vault"), "exports");
        if (!d.isDirectory() && !d.mkdirs()) {
            this.plugin.getLogger().warning("Could not create " + d);
        }

        return d;
    }

    public List<String> files(ConfigBackup.Kind kind) {
        String prefix = kind.name().toLowerCase(Locale.ROOT) + "-";
        File[] fs = this.dir().listFiles((d, n) -> n.startsWith(prefix) && n.endsWith(".zip"));
        if (fs == null) {
            return List.of();
        } else {
            List<String> out = new ArrayList<>();
            Arrays.stream(fs).sorted(Comparator.comparingLong(File::lastModified).reversed()).forEach(f -> out.add(f.getName()));
            return out;
        }
    }

    public String backup(ConfigBackup.Kind kind) throws IOException {
        this.plugin.saveAll();
        if (kind == ConfigBackup.Kind.KITS) {
            this.plugin.kits().save();
        }

        String name = kind.name().toLowerCase(Locale.ROOT) + "-" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date()) + ".zip";
        File out = new File(this.dir(), name);
        this.writeZip(kind, out, null);
        if (kind == ConfigBackup.Kind.FULL && this.plugin.rankBackup() != null) {
            this.plugin.rankBackup().backup().whenComplete((res, err) -> Tasks.ensureSync(() -> {
                if (err == null && res != null) {
                    File ranks = new File(this.plugin.rankBackup().dir(), String.valueOf(res[0]));
                    File tmp = new File(this.dir(), name + ".tmp");

                    try {
                        this.writeZip(kind, tmp, ranks);
                        Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException var9) {
                        this.plugin.getLogger().warning("Could not add player ranks to " + name + ": " + var9.getMessage());
                        if (!tmp.delete()) {
                            tmp.deleteOnExit();
                        }
                    }
                } else {
                    this.plugin.getLogger().warning("Full backup " + name + " was saved without player ranks: " + (err == null ? "no data" : err.getMessage()));
                }
            }));
        }

        this.prune(kind);
        return name;
    }

    private void writeZip(ConfigBackup.Kind kind, File out, File playerRanks) throws IOException {
        File folder = this.plugin.getDataFolder();

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            if (playerRanks != null && playerRanks.isFile()) {
                zip.putNextEntry(new ZipEntry("player-ranks.yml"));

                try (InputStream in = new FileInputStream(playerRanks)) {
                    in.transferTo(zip);
                }

                zip.closeEntry();
            }

            File[] files = folder.listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    if ((kind != ConfigBackup.Kind.KITS || f.getName().equals("kits.yml"))
                        && (kind != ConfigBackup.Kind.SETTINGS || !DATA.contains(f.getName()))) {
                        zip.putNextEntry(new ZipEntry(f.getName()));

                        try (InputStream in = new FileInputStream(f)) {
                            in.transferTo(zip);
                        }

                        zip.closeEntry();
                    }
                }
            }
        }
    }

    private void prune(ConfigBackup.Kind kind) {
        List<String> fs = this.files(kind);

        for (int i = 30; i < fs.size(); i++) {
            File f = new File(this.dir(), fs.get(i));
            if (!f.delete()) {
                f.deleteOnExit();
            }
        }
    }

    public int restore(ConfigBackup.Kind kind, String file) throws IOException {
        File src = new File(this.dir(), file).getCanonicalFile();
        if (src.getPath().startsWith(this.dir().getCanonicalPath()) && src.isFile()) {
            this.backup(kind == ConfigBackup.Kind.FULL ? ConfigBackup.Kind.SETTINGS : kind);
            int n = 0;
            String ranksFile = null;
            File folder = this.plugin.getDataFolder();
            ZipInputStream zip = new ZipInputStream(new FileInputStream(src));

            ZipEntry e;
            try {
                while ((e = zip.getNextEntry()) != null) {
                    String name = new File(e.getName()).getName();
                    if (kind == ConfigBackup.Kind.FULL && name.equals("player-ranks.yml")) {
                        if (this.plugin.rankBackup() != null) {
                            File rd = this.plugin.rankBackup().dir();
                            if (!rd.isDirectory() && !rd.mkdirs()) {
                                throw new IOException("Cannot create " + rd);
                            }

                            ranksFile = "ranks-restored-" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date()) + ".yml";
                            Files.copy(zip, new File(rd, ranksFile).toPath(), StandardCopyOption.REPLACE_EXISTING);
                            n++;
                        }
                    } else if (name.endsWith(".yml") && (kind != ConfigBackup.Kind.KITS || name.equals("kits.yml"))) {
                        File tmp = new File(folder, name + ".restoring");
                        Files.copy(zip, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        Files.move(tmp.toPath(), new File(folder, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
                        n++;
                    }
                }
            } catch (Throwable var12) {
                try {
                    zip.close();
                } catch (Throwable var11) {
                    var12.addSuppressed(var11);
                }

                throw var12;
            }

            zip.close();
            if (kind == ConfigBackup.Kind.KITS) {
                this.plugin.kits().load();
            } else {
                this.plugin.reloadFiles();
                if (kind == ConfigBackup.Kind.FULL) {
                    this.plugin.kits().load();
                }
            }

            if (ranksFile != null) {
                this.plugin.rankBackup().restore(ranksFile).whenComplete((count, err) -> {
                    if (err != null) {
                        this.plugin.getLogger().warning("Player ranks from the full backup could not be restored: " + err.getMessage());
                    } else {
                        this.plugin.getLogger().info("Full restore: tiers and points of " + count + " player(s) put back.");
                    }
                });
            }

            return n;
        } else {
            throw new IOException("No such backup: " + file);
        }
    }

    public static enum Kind {
        KITS,
        SETTINGS,
        FULL;
    }
}
