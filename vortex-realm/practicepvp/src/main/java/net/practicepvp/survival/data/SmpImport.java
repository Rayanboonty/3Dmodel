package net.practicepvp.survival.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import net.practicepvp.survival.VortexSMP;
import net.practicepvp.survival.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Brings old SMP data into this plugin:
 * <ul>
 * <li>the old VortexSMP plugin's folder (plugins/VortexSMP): homes, warps, market, drills, Discord links - copied into
 * smp/ once, before anything starts;</li>
 * <li>an old VortexRealm / PracticePvP folder (or a .zip / .tar.gz of it) dropped in plugins/&lt;this&gt;/import/:
 * every saved SMP inventory and ender chest, their backups and last spots (homes and warps come with OldImport).</li>
 * </ul>
 * Nothing is overwritten: an inventory a player already has here stays, and the imported one is added to their
 * backups (one click to restore with /pvpadmin invbackup). The inventories folder is zipped first.
 */
public final class SmpImport {
    private final VortexSMP plugin;
    private final File importDir;
    private final File stateFile;
    private YamlConfiguration state;
    private String last = "nothing to import";

    public SmpImport(VortexSMP plugin) {
        this.plugin = plugin;
        this.importDir = new File(plugin.host().getDataFolder(), "import");
        this.stateFile = new File(plugin.getDataFolder(), "smp-import-state.yml");
    }

    public String status() {
        this.loadState();
        return this.last.equals("nothing to import") ? this.state.getString("last", this.last) : this.last;
    }

    private void loadState() {
        if (this.state == null) {
            this.state = YamlConfiguration.loadConfiguration(this.stateFile);
        }
    }

    private void saveState() {
        try {
            this.stateFile.getParentFile().mkdirs();
            this.state.save(this.stateFile);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save smp-import-state.yml", e);
        }
    }

    // ------------------------------------------------------------------ the old VortexSMP plugin's folder

    /** plugins/VortexSMP: its files are copied into smp/ once (existing ones are kept). */
    public void copySmpPluginFolder() {
        this.loadState();
        File old = new File(this.plugin.host().getDataFolder().getParentFile(), "VortexSMP");
        if (!old.isDirectory() || this.state.getBoolean("vortexsmp-folder")) {
            return;
        }

        int copied = 0;
        File[] files = old.listFiles(f -> f.isFile() && f.getName().endsWith(".yml"));
        for (File f : files == null ? new File[0] : files) {
            File to = new File(this.plugin.getDataFolder(), f.getName());
            if (!to.exists()) {
                try {
                    to.getParentFile().mkdirs();
                    Files.copy(f.toPath(), to.toPath());
                    copied++;
                } catch (IOException e) {
                    this.plugin.getLogger().log(Level.WARNING, "Import: couldn't copy " + f.getName() + " from plugins/VortexSMP", e);
                }
            }
        }

        this.state.set("vortexsmp-folder", true);
        this.saveState();
        this.last = "copied " + copied + " files from plugins/VortexSMP";
        this.plugin.getLogger().info("Import: the SMP is part of this plugin now - " + copied + " files of plugins/VortexSMP (homes, warps, market, drills, links) "
            + "were copied into " + this.plugin.getDataFolder().getName() + "/. The VortexSMP jar isn't needed any more.");
    }

    // ------------------------------------------------------------------ old VortexRealm / PracticePvP folders

    public void startup() {
        try {
            this.importAll(false, null);
        } catch (RuntimeException e) {
            this.plugin.getLogger().log(Level.WARNING, "Import of old SMP inventories failed", e);
        }
    }

    public void command(CommandSender sender, String[] args) {
        String what = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (what.equals("preview") || what.equals("run")) {
            boolean run = what.equals("run");
            if (run) {
                // force: everything is checked again (nothing already here is ever overwritten)
                this.loadState();
                this.state.set("roots", null);
                this.saveState();
            }

            this.importAll(!run, sender);
            this.plugin.oldImport().command(sender, new String[]{run ? "run" : "preview"});
        } else {
            sender.sendMessage(Text.mm("<gold><b>SMP import</b> <gray>- " + Text.esc(this.last)));
            sender.sendMessage(Text.mm("<gray>Put an old VortexRealm / PracticePvP folder (or a .zip of it) in <white>plugins/"
                + Text.esc(this.plugin.host().getDataFolder().getName()) + "/import/</white>, then <white>/pvpadmin smp import run</white>."));
            sender.sendMessage(Text.mm("<gray>Homes and warps are added, SMP inventories and ender chests are loaded when a player has none here yet - "
                + "otherwise they wait in /pvpadmin invbackup <player>. Nothing is overwritten."));
        }
    }

    /** Old plugin folders inside import/ (also ones OldImport unpacked into import/extracted/). */
    private List<File> roots() {
        java.util.ArrayList<File> out = new java.util.ArrayList<>();
        this.find(this.importDir, 0, out);
        return out;
    }

    private void find(File dir, int depth, List<File> out) {
        if (dir == null || !dir.isDirectory() || depth > 4) {
            return;
        }

        if (new File(dir, "inventories/groups").isDirectory() || new File(dir, "inventories/backups").isDirectory()) {
            out.add(dir);
            return;
        }

        File[] subs = dir.listFiles(File::isDirectory);
        for (File s : subs == null ? new File[0] : subs) {
            this.find(s, depth + 1, out);
        }
    }

    private void importAll(boolean preview, CommandSender sender) {
        this.loadState();
        List<File> roots = this.roots();
        if (roots.isEmpty()) {
            if (sender != null) {
                sender.sendMessage(Text.mm("<gray>No old plugin folder with inventories in import/."));
            }

            return;
        }

        net.practicepvp.smp.ModeManager modes = this.plugin.host().modes();
        if (modes == null) {
            return;
        }

        for (File root : roots) {
            String key = "roots." + Integer.toHexString(root.getAbsolutePath().hashCode());
            if (!preview && this.state.getBoolean(key)) {
                continue;
            }

            if (!preview) {
                this.zipInventories();
            }

            int[] n = new int[4]; // new, kept as backup, backups copied, spots
            File groups = new File(root, "inventories/groups");
            File[] players = groups.listFiles(File::isDirectory);
            for (File pd : players == null ? new File[0] : players) {
                UUID u;
                try {
                    u = UUID.fromString(pd.getName());
                } catch (IllegalArgumentException e) {
                    continue;
                }

                File[] fs = pd.listFiles((d, name) -> name.endsWith(".yml") && !name.endsWith(".prev.yml") && !name.equals("state.yml"));
                for (File f : fs == null ? new File[0] : fs) {
                    String name = f.getName();
                    boolean unsorted = name.contains("-unsorted-");
                    String group = name.substring(0, name.length() - 4).replaceAll("-unsorted-\\d+$", "").toUpperCase(Locale.ROOT);
                    File target = modes.groupFile(u, group);
                    try {
                        if (!unsorted && !target.exists()) {
                            if (!preview) {
                                target.getParentFile().mkdirs();
                                Files.copy(f.toPath(), target.toPath());
                            }

                            n[0]++;
                        } else if (!target.exists() || !Arrays.equals(Files.readAllBytes(f.toPath()), Files.readAllBytes(target.toPath()))) {
                            File keep = new File(modes.backupDir(), u + "/" + group.toLowerCase(Locale.ROOT) + "-imported-" + (unsorted ? "unsorted-" : "")
                                + new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date(f.lastModified())) + ".yml");
                            if (!keep.exists()) {
                                if (!preview) {
                                    keep.getParentFile().mkdirs();
                                    Files.copy(f.toPath(), keep.toPath());
                                }

                                n[1]++;
                            }
                        }
                    } catch (IOException e) {
                        this.plugin.getLogger().log(Level.WARNING, "Import: couldn't copy " + f, e);
                    }
                }
            }

            // their old backups
            File oldBackups = new File(root, "inventories/backups");
            File[] bdirs = oldBackups.listFiles(File::isDirectory);
            for (File bd : bdirs == null ? new File[0] : bdirs) {
                File[] fs = bd.listFiles((d, name) -> name.endsWith(".yml"));
                for (File f : fs == null ? new File[0] : fs) {
                    File to = new File(modes.backupDir(), bd.getName() + "/" + f.getName());
                    if (!to.exists()) {
                        try {
                            if (!preview) {
                                to.getParentFile().mkdirs();
                                Files.copy(f.toPath(), to.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                            }

                            n[2]++;
                        } catch (IOException e) {
                            this.plugin.getLogger().log(Level.WARNING, "Import: couldn't copy " + f, e);
                        }
                    }
                }
            }

            // where players last were in the SMP worlds
            YamlConfiguration spots = YamlConfiguration.loadConfiguration(new File(root, "last-locations.yml"));
            for (String u : spots.getKeys(false)) {
                ConfigurationSection s = spots.getConfigurationSection(u);
                if (s == null) {
                    continue;
                }

                for (String w : s.getKeys(false)) {
                    try {
                        if (preview ? Bukkit.getWorld(w) != null : modes.importSpot(UUID.fromString(u), w, s.getString(w))) {
                            n[3]++;
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            String line = (preview ? "Would import" : "Imported") + " from " + root.getName() + ": " + n[0] + " SMP inventories/ender chests, " + n[1]
                + " kept as backups (the player already had one), " + n[2] + " old backups, " + n[3] + " last spots";
            this.last = line;
            this.plugin.getLogger().info("Import: " + line + ".");
            if (sender != null) {
                sender.sendMessage(Text.mm("<green>" + Text.esc(line) + "."));
            }

            if (!preview) {
                this.state.set(key, true);
                this.state.set("last", line);
                this.saveState();
                modes.saveLocations();
            }
        }
    }

    /** A copy of the inventories folder before anything is added. */
    private void zipInventories() {
        File inv = new File(this.plugin.host().getDataFolder(), "inventories");
        if (!inv.isDirectory()) {
            return;
        }

        File dir = new File(this.plugin.host().getDataFolder(), "backups");
        dir.mkdirs();
        File out = new File(dir, "inventories-before-import-" + new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date()) + ".zip");
        if (out.exists()) {
            return;
        }

        java.nio.file.Path base = inv.toPath();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(out.toPath()));
             java.util.stream.Stream<java.nio.file.Path> files = Files.walk(base)) {
            for (java.nio.file.Path f : (Iterable<java.nio.file.Path>)files::iterator) {
                if (Files.isRegularFile(f)) {
                    zip.putNextEntry(new java.util.zip.ZipEntry("inventories/" + base.relativize(f).toString().replace('\\', '/')));
                    Files.copy(f, zip);
                    zip.closeEntry();
                }
            }
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Import: couldn't back up the inventories first", e);
        }
    }
}
