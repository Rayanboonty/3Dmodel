package net.practicepvp.smp;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.practicepvp.PracticePlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Brings SMP data back from a chosen source: a Data Vault snapshot (taken before every update, every 6 hours and at
 * shutdown), this plugin's own old files, the old VortexSMP plugin's folder or one of its backups, or an old plugin
 * folder / zip put in smp/import/. What comes back: homes and warps, every saved SMP inventory and ender chest (also
 * what players were carrying at the time, read from the player files in the snapshot), their backups and last spots.
 * <p>
 * "Only what's missing" never touches anything players have now: an old inventory is loaded only where the player has
 * none (or an empty one) in that world, otherwise it waits in their backups (/pvpadmin invbackup, one click). "Replace"
 * puts the old one in place and keeps the current one as a backup. A full Data Vault snapshot is taken first.
 */
public final class SmpRestore {
    public enum Part {
        HOMES("Homes & warps"),
        INVENTORIES("Inventories & ender chests"),
        SPOTS("Last spots");

        public final String label;

        Part(String label) {
            this.label = label;
        }
    }

    /** A place old SMP data can come from. */
    public record Source(String id, String label, String detail, File file, long time) {
    }

    /** What a source has, compared with now. */
    public static final class Preview {
        public int homes;
        public int homesNew;
        public int warps;
        public int warpsNew;
        public int inventories;
        public int inventoriesNew;
        public int carried;
        public int backups;
        public int spots;
        /** Inventories given right away, waiting for the next login, kept only as backups. */
        public int now;
        public int pending;
        public int kept;
        public final List<String> notes = new ArrayList<>();

        public String result() {
            return this.now + " inventories given back right away, " + this.pending + " given at their next login / when they enter the SMP"
                + (this.kept > 0 ? ", " + this.kept + " put in backups (the player already had items)" : "");
        }

        public String summary() {
            return this.homes + " homes (" + this.homesNew + " not here), " + this.warps + " warps (" + this.warpsNew + " not here), " + this.inventories
                + " saved SMP inventories (" + this.inventoriesNew + " differ from now), " + this.carried + " players' carried items, " + this.backups
                + " old backups, " + this.spots + " last spots";
        }
    }

    private static final Pattern GROUP = Pattern.compile("inventories/groups/([0-9a-fA-F-]{36})/([a-z]+)\\.yml");
    private static final Pattern BACKUP = Pattern.compile("inventories/backups/([0-9a-fA-F-]{36})/([^/]+\\.yml)");
    private static final Pattern DAT = Pattern.compile("worlds-playerdata/([^/]+)/playerdata/([0-9a-fA-F-]{36})\\.dat");
    private final PracticePlugin plugin;

    public SmpRestore(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    private ModeManager modes() {
        return this.plugin.modes();
    }

    public File importDir() {
        return new File(this.plugin.getDataFolder(), "smp/import");
    }

    // ------------------------------------------------------------------ sources

    public List<Source> sources() {
        List<Source> old = new ArrayList<>();
        File data = this.plugin.getDataFolder().getAbsoluteFile();
        File plugins = data.getParentFile();
        File server = plugins == null ? null : plugins.getParentFile();
        File vaultDir = this.plugin.vault() == null ? null : this.plugin.vault().dir().getAbsoluteFile();
        Set<String> seen = new java.util.HashSet<>();
        // uploaded old plugin zips and folders, wherever they are: found by what is inside, not by their name
        List<File> look = new ArrayList<>();
        for (File dir : new File[]{server, plugins, data, new File(data, "import"), this.importDir()}) {
            File[] fs = dir == null ? null : dir.listFiles();
            if (fs != null) {
                look.addAll(List.of(fs));
            }
        }

        for (File f : look) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (!seen.add(f.getAbsolutePath()) || f.equals(data) || f.equals(vaultDir) || n.equals("readme.txt") || n.startsWith(".")
                || f.isDirectory() && data.equals(f.getAbsoluteFile().getParentFile())) {
                continue;
            }

            boolean archive = f.isFile() && (n.endsWith(".zip") || n.endsWith(".tar.gz") || n.endsWith(".tgz") || n.endsWith(".tar"));
            if (archive && f.length() > 0L) {
                String what = this.describe(f);
                if (what != null) {
                    old.add(new Source("file:" + f.getAbsolutePath(), "Old plugin: " + f.getName(), what + " • " + rel(server, f), f, f.lastModified()));
                }
            } else if (f.isDirectory() && looksOld(f)) {
                old.add(new Source("dir:" + f.getAbsolutePath(), "Old plugin folder: " + f.getName(), describeDir(f) + " • " + rel(server, f), f, f.lastModified()));
            }
        }

        old.sort(Comparator.comparingLong(Source::time).reversed());
        List<Source> out = new ArrayList<>(old);
        File[] zips = vaultDir == null ? null : vaultDir.listFiles((d, n) -> n.endsWith(".zip"));
        if (zips != null) {
            java.util.Arrays.sort(zips, Comparator.comparingLong(File::lastModified).reversed());
            for (File z : zips) {
                String n = z.getName().replace("vault-", "").replace(".zip", "");
                String why = n.contains("pre-update") ? "before an update" : (n.contains("shutdown") ? "at a shutdown" : (n.contains("manual") ? "made by hand" : "automatic"));
                out.add(new Source("vault:" + z.getName(), "Data Vault " + n.substring(0, Math.min(19, n.length())).replace('_', ' '), "Backup " + why, z, z.lastModified()));
            }
        }

        out.add(new Source("own", "This plugin's own old files", "homes.yml / warps.yml left from the old plugin in this folder", data, 0L));
        return out;
    }

    private static String rel(File server, File f) {
        String s = f.getAbsolutePath();
        String base = server == null ? "" : server.getAbsolutePath();
        return s.startsWith(base) ? s.substring(base.length()).replaceFirst("^[/\\\\]", "") : s;
    }

    private static boolean looksOld(File dir) {
        return new File(dir, "inventories/groups").isDirectory() || new File(dir, "homes.yml").isFile() && new File(dir, "config.yml").isFile();
    }

    private static String describeDir(File dir) {
        File[] ps = new File(dir, "inventories/groups").listFiles(File::isDirectory);
        return (ps == null ? 0 : ps.length) + " players' inventories" + (new File(dir, "homes.yml").isFile() ? ", homes" : "");
    }

    /** What an archive has, or null when it isn't an old plugin folder. */
    private String describe(File f) {
        try (Reader r = this.reader(f)) {
            Set<String> players = new java.util.HashSet<>();
            boolean homes = false;
            for (String n : r.names()) {
                Matcher m = Pattern.compile("inventories/groups/([0-9a-fA-F-]{36})/").matcher(n);
                if (m.find()) {
                    players.add(m.group(1));
                }

                homes |= n.endsWith("homes.yml");
            }

            return players.isEmpty() && !homes ? null : players.size() + " players' inventories" + (homes ? ", homes" : "");
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public Source source(String id) {
        for (Source s : this.sources()) {
            if (s.id().equals(id)) {
                return s;
            }
        }

        return null;
    }

    // ------------------------------------------------------------------ reading a source (folder or zip)

    private interface Reader extends Closeable {
        List<String> names();

        InputStream open(String name) throws IOException;
    }

    private Reader reader(File f) throws IOException {
        String ln = f.getName().toLowerCase(Locale.ROOT);
        if (f.isFile() && (ln.endsWith(".tar.gz") || ln.endsWith(".tgz") || ln.endsWith(".tar"))) {
            // unpacked once next to the other imports
            File out = new File(this.importDir(), ".unpacked/" + f.getName().replaceAll("[^A-Za-z0-9._-]", "_") + "-" + Long.toHexString(f.lastModified()));
            if (!out.isDirectory()) {
                net.practicepvp.data.OldDataImport.unpack(f, out);
            }

            f = out;
        }

        if (f.isDirectory()) {
            Path root = f.toPath();
            List<String> names = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(root, 8)) {
                walk.filter(Files::isRegularFile).forEach(x -> {
                    String rel = root.relativize(x).toString().replace('\\', '/');
                    if (!rel.startsWith("backups/") && !rel.startsWith("logs/") && !rel.startsWith("import/") && !rel.startsWith("smp/import/")) {
                        names.add(rel);
                    }
                });
            }

            return new Reader() {
                public List<String> names() {
                    return names;
                }

                public InputStream open(String name) throws IOException {
                    return Files.newInputStream(root.resolve(name));
                }

                public void close() {
                }
            };
        }

        ZipFile z = new ZipFile(f);
        List<String> names = new ArrayList<>();
        Map<String, String> raw = new java.util.HashMap<>();
        z.stream().filter(e -> !e.isDirectory()).forEach(e -> {
            String n = e.getName().replace('\\', '/');
            names.add(n);
            raw.put(n, e.getName());
        });
        return new Reader() {
            public List<String> names() {
                return names;
            }

            public InputStream open(String name) throws IOException {
                ZipEntry e = z.getEntry(raw.getOrDefault(name, name));
                return e == null ? InputStream.nullInputStream() : z.getInputStream(e);
            }

            public void close() throws IOException {
                z.close();
            }
        };
    }

    /** The folder inside a source that holds the plugin files ("" or "VortexRealm/" in a zip of the folder). */
    private static String prefix(List<String> names) {
        for (String n : names) {
            int i = n.indexOf("inventories/groups/");
            if (i >= 0 && (i == 0 || n.charAt(i - 1) == '/')) {
                return n.substring(0, i);
            }
        }

        String best = null;
        for (String n : names) {
            for (String marker : List.of("inventories/groups/", "smp/homes.yml", "homes.yml", "warps.yml", "last-locations.yml", "worlds.yml")) {
                int i = n.indexOf(marker);
                if (i >= 0 && (i == 0 || n.charAt(i - 1) == '/') && (best == null || i < best.length())) {
                    best = n.substring(0, i);
                }
            }
        }

        return best == null ? "" : best;
    }

    private static YamlConfiguration yaml(Reader r, String name) throws IOException {
        if (!r.names().contains(name)) {
            return null;
        }

        try (InputStream in = r.open(name)) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------ preview and bring back

    public Preview preview(Source s) throws IOException {
        return this.run(s, EnumSet.allOf(Part.class), false, true);
    }

    /** Applies (or with {@code dry} only counts). Call on the main thread. */
    public Preview run(Source s, Set<Part> parts, boolean replace, boolean dry) throws IOException {
        Preview pv = new Preview();
        net.practicepvp.survival.VortexSMP smp = this.plugin.survival();
        ModeManager modes = this.modes();
        String tag = s.id().replaceAll("[^A-Za-z0-9_-]+", "-").replaceAll("-+$", "");
        try (Reader r = this.reader(s.file())) {
            String pre = prefix(r.names());
            if (parts.contains(Part.HOMES) && smp != null && smp.essentials() != null) {
                for (String f : List.of("smp/homes.yml", "homes.yml")) {
                    YamlConfiguration y = yaml(r, pre + f);
                    if (y != null) {
                        pv.homes += totalHomes(y);
                        pv.homesNew += smp.essentials().mergeHomes(y, false, true);
                        if (!dry) {
                            smp.essentials().mergeHomes(y, replace, false);
                        }
                    }
                }

                for (String f : List.of("smp/warps.yml", "warps.yml")) {
                    YamlConfiguration y = yaml(r, pre + f);
                    if (y != null) {
                        ConfigurationSection ws = y.getConfigurationSection("warps");
                        for (String m : ws == null ? Set.<String>of() : ws.getKeys(false)) {
                            ConfigurationSection c = ws.getConfigurationSection(m);
                            pv.warps += c == null ? 0 : c.getKeys(false).size();
                        }

                        pv.warpsNew += smp.essentials().mergeWarps(y, false, true);
                        if (!dry) {
                            smp.essentials().mergeWarps(y, replace, false);
                        }
                    }
                }
            }

            if (parts.contains(Part.INVENTORIES) && modes != null) {
                // "replace": the saved inventories first, then what players carried (newer) on top;
                // "only missing": what they carried first, the saved ones only where nothing is there yet
                if (replace) {
                    this.groupFiles(r, pre, tag, replace, dry, pv);
                    this.carried(r, tag, replace, dry, pv);
                } else {
                    this.carried(r, tag, replace, dry, pv);
                    this.groupFiles(r, pre, tag, replace, dry, pv);
                }
            }

            if (parts.contains(Part.SPOTS) && modes != null) {
                YamlConfiguration spots = yaml(r, pre + "last-locations.yml");
                for (String u : spots == null ? Set.<String>of() : spots.getKeys(false)) {
                    ConfigurationSection sec = spots.getConfigurationSection(u);
                    for (String w : sec == null ? Set.<String>of() : sec.getKeys(false)) {
                        try {
                            boolean added = dry ? Bukkit.getWorld(w) != null : modes.importSpot(UUID.fromString(u), w, sec.getString(w));
                            pv.spots += added ? 1 : 0;
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }

                if (!dry) {
                    modes.saveLocations();
                }
            }
        }

        if (!dry) {
            this.plugin.getLogger().info("SMP restore from " + s.label() + (replace ? " (replace)" : " (only what's missing)") + ": " + pv.summary());
        }

        return pv;
    }

    private void count(String result, Preview pv) {
        if ("now".equals(result)) {
            pv.now++;
        } else if ("pending".equals(result)) {
            pv.pending++;
        }
    }

    private void carried(Reader r, String tag, boolean replace, boolean dry, Preview pv) {
        ModeManager modes = this.modes();
        for (String n : r.names()) {
            Matcher m = DAT.matcher(n);
            if (!m.matches()) {
                continue;
            }

            UUID u = UUID.fromString(m.group(2));
            PlayerDat d;
            try (InputStream in = r.open(n)) {
                d = PlayerDat.read(in);
            } catch (IOException | RuntimeException e) {
                pv.notes.add("couldn't read " + n);
                continue;
            }

            if (!this.hasRealItems(d.inventory) && !this.hasRealItems(d.ender)) {
                continue;
            }

            String group = d.group != null && !d.group.equalsIgnoreCase("HUB") ? d.group.toUpperCase(Locale.ROOT) : null;
            if (group == null && d.world != null && modes.smpWorld(d.world)) {
                group = modes.mode(d.world).name();
            }

            pv.carried++;
            if (!dry) {
                File b = this.writeBackup(u, group == null ? "SURVIVAL" : group, "carried-" + tag, d, m.group(1));
                if (group != null && b != null && (replace || modes.groupEmpty(u, group))) {
                    this.count(modes.restoreBackup(u, b), pv);
                } else {
                    pv.kept++;
                }
            }
        }
    }

    private void groupFiles(Reader r, String pre, String tag, boolean replace, boolean dry, Preview pv) throws IOException {
        ModeManager modes = this.modes();
        for (String n : r.names()) {
            if (!n.startsWith(pre)) {
                continue;
            }

            String rel = n.substring(pre.length());
            Matcher g = GROUP.matcher(rel);
            Matcher b = BACKUP.matcher(rel);
            if (g.matches() && !g.group(2).equals("hub")) {
                UUID u = UUID.fromString(g.group(1));
                String group = g.group(2).toUpperCase(Locale.ROOT);
                byte[] data;
                try (InputStream in = r.open(n)) {
                    data = in.readAllBytes();
                }

                if (emptyItems(data)) {
                    continue;
                }

                pv.inventories++;
                File now = modes.groupFile(u, group);
                boolean differs = !now.isFile() || !java.util.Arrays.equals(Files.readAllBytes(now.toPath()), data) || modes.groupEmpty(u, group);
                pv.inventoriesNew += differs ? 1 : 0;
                if (!dry) {
                    File keep = new File(modes.backupDir(), u + "/" + group.toLowerCase(Locale.ROOT) + "-from-" + tag + ".yml");
                    keep.getParentFile().mkdirs();
                    Files.write(keep.toPath(), data);
                    if (replace || modes.groupEmpty(u, group)) {
                        this.count(modes.restoreBackup(u, keep), pv);
                    } else {
                        pv.kept++;
                    }
                }
            } else if (b.matches()) {
                File to = new File(modes.backupDir(), b.group(1) + "/" + b.group(2));
                if (!to.exists()) {
                    pv.backups++;
                    if (!dry) {
                        to.getParentFile().mkdirs();
                        try (InputStream in = r.open(n)) {
                            Files.copy(in, to.toPath());
                        }
                    }
                }
            }
        }
    }

    private static int totalHomes(YamlConfiguration y) {
        int n = 0;
        ConfigurationSection root = y.getConfigurationSection("homes");
        for (String u : root == null ? Set.<String>of() : root.getKeys(false)) {
            ConfigurationSection per = root.getConfigurationSection(u);
            for (String m : per == null ? Set.<String>of() : per.getKeys(false)) {
                ConfigurationSection hs = per.getConfigurationSection(m);
                n += hs == null ? 0 : hs.getKeys(false).size();
            }
        }

        return n;
    }

    /** Items that aren't this plugin's own (hub, lobby and kit items carry its tags). */
    private boolean hasRealItems(ItemStack[] items) {
        String ns = this.plugin.getName().toLowerCase(Locale.ROOT);
        for (ItemStack it : items) {
            if (it == null || it.isEmpty()) {
                continue;
            }

            boolean ours = false;
            if (it.hasItemMeta()) {
                for (NamespacedKey k : it.getItemMeta().getPersistentDataContainer().getKeys()) {
                    ours |= k.getNamespace().equals(ns);
                }
            }

            if (!ours) {
                return true;
            }
        }

        return false;
    }

    private boolean emptyOrMissing(File f) {
        try {
            return !f.isFile() || emptyItems(Files.readAllBytes(f.toPath()));
        } catch (IOException e) {
            return false;
        }
    }

    /** True when a saved inventory file has nothing in the inventory and the ender chest. */
    private static boolean emptyItems(byte[] yml) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new InputStreamReader(new ByteArrayInputStream(yml), StandardCharsets.UTF_8));
        for (String key : List.of("contents", "enderchest")) {
            try {
                for (ItemStack it : ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(y.getString(key, "")))) {
                    if (it != null && !it.isEmpty()) {
                        return false;
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }

        return true;
    }

    /** A carried inventory from a player file, saved as a backup the restore tools can load. */
    private File writeBackup(UUID u, String group, String tag, PlayerDat d, String world) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("contents", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(d.inventory)));
        y.set("enderchest", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(d.ender)));
        y.set("level", d.level);
        y.set("exp", (double)d.exp);
        y.set("health", 20.0);
        y.set("food", 20);
        y.set("gamemode", "SURVIVAL");
        y.set("group", group);
        y.set("saved", System.currentTimeMillis());
        y.set("from", "player file in " + world);
        File f = new File(this.modes().backupDir(), u + "/" + group.toLowerCase(Locale.ROOT) + "-" + tag + ".yml");
        try {
            f.getParentFile().mkdirs();
            y.save(f);
            return f;
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save " + f, e);
            return null;
        }
    }

    // ------------------------------------------------------------------ at start-up

    /**
     * Once per source and version of its files: homes and warps of this plugin's old files and of the old VortexSMP
     * plugin, and everything in smp/import/ - only what's missing.
     */
    public void startup() {
        File stateFile = new File(this.plugin.getDataFolder(), "smp/restore-state.yml");
        YamlConfiguration st = YamlConfiguration.loadConfiguration(stateFile);
        Map<String, Set<Part>> auto = new LinkedHashMap<>();
        auto.put("own", EnumSet.of(Part.HOMES));
        auto.put("vortexsmp", EnumSet.of(Part.HOMES));
        for (Source s : this.sources()) {
            Set<Part> parts = s.id().startsWith("drop:") ? EnumSet.allOf(Part.class) : auto.get(s.id());
            if (parts == null) {
                continue;
            }

            long stamp = stamp(s.file());
            String key = "done." + s.id().replaceAll("[^A-Za-z0-9_-]", "_");
            if (st.getLong(key) == stamp) {
                continue;
            }

            try {
                this.run(s, parts, false, false);
                st.set(key, stamp);
            } catch (IOException | RuntimeException e) {
                this.plugin.getLogger().log(Level.WARNING, "SMP restore from " + s.label() + " failed", e);
            }
        }

        try {
            stateFile.getParentFile().mkdirs();
            st.save(stateFile);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save smp/restore-state.yml", e);
        }
    }

    private static long stamp(File f) {
        if (!f.isDirectory()) {
            return f.lastModified() ^ f.length();
        }

        long t = 0L;
        for (String n : List.of("homes.yml", "warps.yml", "smp/homes.yml")) {
            File x = new File(f, n);
            t = t * 31L + (x.isFile() ? x.lastModified() ^ x.length() : 0L);
        }

        return t;
    }

    public static World world(String name) {
        return Bukkit.getWorld(name);
    }
}
