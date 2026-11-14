package net.vortexrealm.survival.data;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.vortexrealm.survival.VortexSMP;
import net.vortexrealm.survival.config.Cfg;
import net.vortexrealm.survival.config.Opt;
import net.vortexrealm.survival.session.Tp;
import net.vortexrealm.survival.util.Locs;
import net.vortexrealm.survival.util.Snd;
import net.vortexrealm.survival.util.Tasks;
import net.vortexrealm.survival.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Brings the SMP of an old VortexRealm / VortexRealm server over, once, when its plugin folder (or a .zip /
 * .tar.gz of it) is put into {@code plugins/VortexSMP/import/}: homes and warps of the
 * chosen mode, where players last were, and their old survival inventory and ender chest. Nothing is overwritten:
 * existing homes and warps win, and items are only placed into empty slots - the rest waits in /oldstuff.
 */
public final class OldImport implements Listener, CommandExecutor {
    private static final String[] OLD = {"practicepvp", "vortexrealm", "vortexpvp"};
    /** Tags of the practice plugins' hub, lobby and kit items: never real survival items. */
    private static final Set<String> JUNK_KEYS = Set.of("hub_item", "action", "gui", "wand", "kit_slot");
    private static final String[] OLD_MODES = {"SURVIVAL", "LIFESTEAL", "STRENGTH", "RANKED", "ECO"};
    private final VortexSMP plugin;
    private final File dir;
    private final File stateFile;
    private final File pendingDir;
    private YamlConfiguration state = new YamlConfiguration();
    private String summary = "nothing imported";

    public OldImport(VortexSMP plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.host().getDataFolder(), "import");
        this.stateFile = new File(plugin.getDataFolder(), "import-state.yml");
        this.pendingDir = new File(plugin.getDataFolder(), "import-pending");
    }

    public String status() {
        return this.summary;
    }

    /** At start-up, before homes and warps load: imports a dropped folder once. */
    public void prepare() {
        this.state = YamlConfiguration.loadConfiguration(this.stateFile);
        if (this.plugin.realm().on()) {
            // part of the main plugin: SmpRestore brings old homes, warps and inventories back (/pvpadmin -> SMP)
            this.summary = "see /pvpadmin -> SMP -> Bring back SMP data";
            return;
        }

        if (!this.dir.exists()) {
            this.dir.mkdirs();
            try {
                Files.writeString(new File(this.dir, "README.txt").toPath(), String.join("\n",
                    "Optional: bring the SMP of your old server over (homes, warps, last spots, inventories, ender chests).",
                    "1. Copy the old plugins/VortexRealm (or VortexRealm) folder into this folder,",
                    "   or put a .zip / .tar.gz of it here.",
                    "2. Choose which old SMP mode to bring over with import.mode in config.yml (SURVIVAL by default).",
                    "3. Restart the server (or run /vortexsmp import run). It is done once; see import-state.yml.",
                    "Existing homes and warps are never overwritten. A backup is made first."), StandardCharsets.UTF_8);
            } catch (IOException var2) {
            }
        }

        File root = this.findRoot();
        if (this.state.getBoolean("done")) {
            this.summary = this.state.getString("summary", "done");
            if (this.plugin.realm().on()) {
                return;
            }

            int had = this.state.contains("inventories") ? this.state.getInt("inventories") : parse(this.summary.replaceAll(".* (\\d+) inventories.*", "$1"));
            if (root == null || had > 0 || modeCounts(root).isEmpty()) {
                return;
            }

            this.plugin.getLogger().info("Import: the old folder with saved inventories is here now (" + root.getPath() + ") - importing the inventories again.");
        }

        if (root != null) {
            this.run(root, false, null);
        }
    }

    public void save() {
        try {
            this.state.save(this.stateFile);
        } catch (IOException var2) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save import-state.yml", (Throwable)var2);
        }
    }

    // ------------------------------------------------------------------ finding the old folder

    private File findRoot() {
        File[] files = this.dir.listFiles();
        if (files == null) {
            return null;
        }

        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && (n.endsWith(".zip") || n.endsWith(".tar") || n.endsWith(".tar.gz") || n.endsWith(".tgz") || n.endsWith(".tar.gz.tar"))) {
                File out = new File(this.dir, "extracted/" + f.getName().replaceAll("[^A-Za-z0-9._-]", "_"));
                if (!out.exists()) {
                    try {
                        extract(f, out);
                        this.plugin.getLogger().info("Import: unpacked " + f.getName());
                    } catch (IOException var8) {
                        this.plugin.getLogger().log(Level.WARNING, "Import: could not unpack " + f.getName(), (Throwable)var8);
                    }
                }
            }
        }

        File r = search(this.dir, 0);
        if (r != null) {
            return r;
        }

        // the old folder copied as it was: plugins/VortexRealm (or VortexRealm / VortexPvP) next to VortexSMP's own
        File plugins = this.plugin.host().getDataFolder().getParentFile();
        File[] siblings = plugins == null ? null : plugins.listFiles(File::isDirectory);
        if (siblings != null) {
            for (File f : siblings) {
                if (isOld(f.getName())) {
                    r = search(f, 0);
                    if (r != null) {
                        return r;
                    }
                }
            }
        }

        return null;
    }

    /** Players with saved inventories in an old folder, per old mode file name ("survival" -> 12). */
    private static Map<String, Integer> modeCounts(File root) {
        Map<String, Integer> out = new java.util.TreeMap<>();
        File[] players = new File(root, "inventories/groups").listFiles(File::isDirectory);
        if (players != null) {
            for (File pd : players) {
                File[] fs = pd.listFiles((d, n) -> n.endsWith(".yml") && !n.contains("-unsorted-") && !n.endsWith(".prev.yml"));
                if (fs != null) {
                    for (File f : fs) {
                        out.merge(f.getName().substring(0, f.getName().length() - 4).toUpperCase(Locale.ROOT), 1, Integer::sum);
                    }
                }
            }
        }

        return out;
    }

    private static File search(File d, int depth) {
        if (depth > 6 || d == null || !d.isDirectory()) {
            return null;
        }

        if (new File(d, "homes.yml").exists() || new File(d, "worlds.yml").exists() || new File(d, "inventories/groups").isDirectory()) {
            return d;
        }

        File[] kids = d.listFiles(File::isDirectory);
        if (kids == null) {
            return null;
        }

        // the plugin folders of our old plugins first
        java.util.Arrays.sort(kids, (a, b) -> Boolean.compare(!isOld(a.getName()), !isOld(b.getName())));
        for (File k : kids) {
            File r = search(k, depth + 1);
            if (r != null) {
                return r;
            }
        }

        return null;
    }

    private static boolean isOld(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        for (String o : OLD) {
            if (l.equals(o)) {
                return true;
            }
        }

        return false;
    }

    private static void extract(File archive, File out) throws IOException {
        out.mkdirs();
        String n = archive.getName().toLowerCase(Locale.ROOT);
        Path base = out.toPath().toAbsolutePath().normalize();
        if (n.endsWith(".zip")) {
            try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
                ZipEntry e;
                while ((e = z.getNextEntry()) != null) {
                    if (!e.isDirectory()) {
                        write(base, e.getName(), z);
                    }
                }
            }
        } else {
            InputStream in = new BufferedInputStream(new FileInputStream(archive));
            in.mark(4);
            int b1 = in.read();
            int b2 = in.read();
            in.reset();
            if (b1 == 31 && b2 == 139) {
                in = new BufferedInputStream(new GZIPInputStream(in));
            }

            try (InputStream tar = in) {
                untar(base, tar, 0);
            }
        }
    }

    /** A plain tar reader (ustar headers, GNU long names); a .tar.gz inside a .tar is unpacked too. */
    private static void untar(Path base, InputStream in, int depth) throws IOException {
        byte[] h = new byte[512];
        String longName = null;

        while (in.readNBytes(h, 0, 512) == 512) {
            boolean empty = true;
            for (byte b : h) {
                empty &= b == 0;
            }

            if (empty) {
                break;
            }

            String name = cstr(h, 0, 100);
            String prefix = cstr(h, 345, 155);
            long size = Long.parseLong(cstr(h, 124, 12).trim().isEmpty() ? "0" : cstr(h, 124, 12).trim(), 8);
            char type = (char)h[156];
            if (!prefix.isEmpty() && longName == null) {
                name = prefix + "/" + name;
            }

            if (longName != null) {
                name = longName;
                longName = null;
            }

            long padded = (size + 511L) / 512L * 512L;
            if (type == 'L') {
                byte[] ln = in.readNBytes((int)padded);
                longName = cstr(ln, 0, (int)size);
                continue;
            }

            if (type == '0' || type == 0) {
                byte[] data = in.readNBytes((int)size);
                in.skipNBytes(padded - size);
                String low = name.toLowerCase(Locale.ROOT);
                if (depth < 2 && (low.endsWith(".tar.gz") || low.endsWith(".tgz") || low.endsWith(".tar"))) {
                    InputStream inner = new java.io.ByteArrayInputStream(data);
                    if (data.length > 2 && (data[0] & 255) == 31 && (data[1] & 255) == 139) {
                        inner = new GZIPInputStream(inner);
                    }

                    untar(base, inner, depth + 1);
                } else {
                    write(base, name, new java.io.ByteArrayInputStream(data));
                }
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

    private static void write(Path base, String name, InputStream in) throws IOException {
        Path target = base.resolve(name).normalize();
        if (!target.startsWith(base)) {
            return;
        }

        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }

    // ------------------------------------------------------------------ the import itself

    private Set<String> modes() {
        String m = Cfg.s(Opt.IMPORT_MODE).trim().toUpperCase(Locale.ROOT);
        return m.equals("ALL") ? new HashSet<>(List.of(OLD_MODES)) : Set.of(m.isEmpty() ? "SURVIVAL" : m);
    }

    /** The old worlds of the chosen modes and the world they become here. */
    private Map<String, String> worldMap(File root, Set<String> modes) {
        YamlConfiguration wy = YamlConfiguration.loadConfiguration(new File(root, "worlds.yml"));
        Map<String, String> oldModes = new HashMap<>();
        ConfigurationSection ws = wy.getConfigurationSection("worlds");
        if (ws != null) {
            for (String w : ws.getKeys(false)) {
                oldModes.put(w, ws.getString(w + ".mode", "SURVIVAL").toUpperCase(Locale.ROOT));
            }
        }

        Map<String, String> map = new LinkedHashMap<>();
        ConfigurationSection explicit = this.plugin.getConfig().getConfigurationSection("import.world-map");
        Set<String> seen = new HashSet<>();
        this.collectWorlds(root, seen);
        List<String> overworlds = new ArrayList<>();

        for (String w : seen) {
            String parent = w.replaceFirst("_(nether|the_end)$", "");
            String mode = oldModes.getOrDefault(w, oldModes.getOrDefault(parent, w.equals("practice_world") ? "PVP" : "SURVIVAL"));
            if (modes.contains(mode)) {
                map.put(w, w);
                if (parent.equals(w)) {
                    overworlds.add(w);
                }
            }
        }

        String main = Bukkit.getWorlds().get(0).getName();
        if (overworlds.size() == 1 && Bukkit.getWorld(overworlds.get(0)) == null) {
            // the old SMP world has another name here: it becomes this server's main world (and its nether/end)
            String old = overworlds.get(0);
            for (String w : new ArrayList<>(map.keySet())) {
                if (w.equals(old)) {
                    map.put(w, main);
                } else if (w.equals(old + "_nether")) {
                    map.put(w, main + "_nether");
                } else if (w.equals(old + "_the_end")) {
                    map.put(w, main + "_the_end");
                }
            }
        }

        if (explicit != null) {
            for (String k : explicit.getKeys(false)) {
                map.put(k, explicit.getString(k));
            }
        }

        return map;
    }

    /** World names used by the old homes, warps, claims and last spots. */
    private void collectWorlds(File root, Set<String> out) {
        YamlConfiguration homes = YamlConfiguration.loadConfiguration(new File(root, "homes.yml"));
        for (String key : homes.getKeys(true)) {
            if (homes.isString(key)) {
                addWorld(homes.getString(key), out);
            }
        }

        YamlConfiguration warps = YamlConfiguration.loadConfiguration(new File(root, "warps.yml"));
        for (String key : warps.getKeys(true)) {
            if (key.endsWith(".location")) {
                addWorld(warps.getString(key), out);
            }
        }

        YamlConfiguration claims = YamlConfiguration.loadConfiguration(new File(root, "claims.yml"));
        ConfigurationSection cs = claims.getConfigurationSection("claims");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                String w = cs.getString(k + ".world");
                if (w != null) {
                    out.add(w);
                }
            }
        }

        YamlConfiguration last = YamlConfiguration.loadConfiguration(new File(root, "last-locations.yml"));
        for (String key : last.getKeys(true)) {
            if (last.isString(key)) {
                addWorld(last.getString(key), out);
            }
        }
    }

    private static void addWorld(String loc, Set<String> out) {
        if (loc != null && loc.contains(";")) {
            out.add(loc.substring(0, loc.indexOf(';')));
        }
    }

    private static String remap(String loc, Map<String, String> map) {
        if (loc == null || !loc.contains(";")) {
            return null;
        }

        String w = loc.substring(0, loc.indexOf(';'));
        String to = map.get(w);
        return to == null ? null : to + loc.substring(loc.indexOf(';'));
    }

    private void backup() {
        File b = new File(this.plugin.getDataFolder(), "backups/before-import-" + System.currentTimeMillis());
        b.mkdirs();
        for (String f : List.of("homes.yml", "warps.yml")) {
            File src = new File(this.plugin.getDataFolder(), f);
            if (src.exists()) {
                try {
                    Files.copy(src.toPath(), new File(b, f).toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException var6) {
                    this.plugin.getLogger().warning("Import: could not back up " + f + ": " + var6.getMessage());
                }
            }
        }
    }

    /** Imports from an old plugin folder. With preview nothing is written; the counts are reported. */
    public void run(File root, boolean preview, CommandSender who) {
        Set<String> modes = this.modes();
        Map<String, String> worlds = this.worldMap(root, modes);
        int homes = 0;
        int warps = 0;
        int spots = 0;
        int inventories = 0;
        if (!preview) {
            this.backup();
        }

        // homes: homes.<uuid>.<MODE>.<name> -> SURVIVAL group here
        File homesFile = new File(this.plugin.getDataFolder(), "homes.yml");
        YamlConfiguration mine = YamlConfiguration.loadConfiguration(homesFile);
        YamlConfiguration old = YamlConfiguration.loadConfiguration(new File(root, "homes.yml"));
        ConfigurationSection hs = old.getConfigurationSection("homes");
        if (hs != null) {
            for (String u : hs.getKeys(false)) {
                for (String mode : modes) {
                    ConfigurationSection m = hs.getConfigurationSection(u + "." + mode);
                    if (m == null) {
                        continue;
                    }

                    for (String name : m.getKeys(false)) {
                        String loc = remap(m.getString(name), worlds);
                        String path = "homes." + u + ".SURVIVAL." + name;
                        if (loc != null && !mine.isSet(path)) {
                            mine.set(path, loc);
                            homes++;
                        }
                    }
                }
            }
        }

        List<String> tpaOff = new ArrayList<>(mine.getStringList("tpa-off"));
        for (String s : old.getStringList("tpa-off")) {
            if (!tpaOff.contains(s)) {
                tpaOff.add(s);
            }
        }

        mine.set("tpa-off", tpaOff);
        // warps: warps.<MODE>.<name>.location/icon
        File warpsFile = new File(this.plugin.getDataFolder(), "warps.yml");
        YamlConfiguration mw = YamlConfiguration.loadConfiguration(warpsFile);
        YamlConfiguration ow = YamlConfiguration.loadConfiguration(new File(root, "warps.yml"));
        for (String mode : modes) {
            ConfigurationSection m = ow.getConfigurationSection("warps." + mode);
            if (m == null) {
                continue;
            }

            for (String name : m.getKeys(false)) {
                String loc = remap(m.getString(name + ".location"), worlds);
                String path = "warps.SURVIVAL." + name;
                if (loc != null && !mw.isSet(path)) {
                    mw.set(path + ".location", loc);
                    mw.set(path + ".icon", m.getString(name + ".icon", "ENDER_PEARL"));
                    warps++;
                }
            }
        }

        // the old SMP spawn (worlds.yml spawn of the old SMP world) becomes /spawn here when none is set yet
        if (!preview && this.plugin.getConfig().getString("spawn") == null) {
            YamlConfiguration wy = YamlConfiguration.loadConfiguration(new File(root, "worlds.yml"));
            ConfigurationSection ws = wy.getConfigurationSection("worlds");
            if (ws != null) {
                for (String w : ws.getKeys(false)) {
                    String sp = remap(ws.getString(w + ".spawn"), worlds);
                    if (sp != null && modes.contains(ws.getString(w + ".mode", "SURVIVAL").toUpperCase(Locale.ROOT))) {
                        this.plugin.getConfig().set("spawn", sp);
                        this.plugin.saveConfig();
                        break;
                    }
                }
            }
        }

        // where players last were: used once, when they first come here
        YamlConfiguration last = YamlConfiguration.loadConfiguration(new File(root, "last-locations.yml"));
        if (Cfg.bool(Opt.IMPORT_LOCATIONS) && !this.plugin.realm().on()) {
            for (String u : last.getKeys(false)) {
                ConfigurationSection s = last.getConfigurationSection(u);
                if (s == null) {
                    continue;
                }

                for (String w : s.getKeys(false)) {
                    String loc = remap(s.getString(w), worlds);
                    // the overworld spot wins over a nether or end one
                    if (loc != null && (!this.state.isSet("spots." + u) || !w.matches(".*_(nether|the_end)$"))) {
                        if (!preview) {
                            this.state.set("spots." + u, loc);
                        }

                        spots++;
                    }
                }
            }
        }

        // inventories: inventories/groups/<uuid>/<mode>.yml (and the "-unsorted-" copies) of the chosen modes
        File groups = new File(root, "inventories/groups");
        File[] players = groups.listFiles(File::isDirectory);
        // same server: VortexRealm loads the old inventories itself (each world group keeps its own)
        if (Cfg.bool(Opt.IMPORT_INVENTORIES) && !this.plugin.realm().on() && players != null) {
            for (File pd : players) {
                UUID u;
                try {
                    u = UUID.fromString(pd.getName());
                } catch (IllegalArgumentException var29) {
                    continue;
                }

                File[] files = pd.listFiles((d, n) -> n.endsWith(".yml") && !n.endsWith(".prev.yml") && !n.endsWith(".tmp"));
                if (files == null || !preview && this.state.getBoolean("imported." + u)) {
                    // already brought over once: never twice
                    continue;
                }

                // the main file of a mode is restored slot by slot; "-unsorted-" copies (items of unknown origin) wait in /oldstuff
                YamlConfiguration main = null;
                String mainMode = null;
                List<ItemStack> inv = new ArrayList<>();
                List<ItemStack> ender = new ArrayList<>();
                for (String m : modes) {
                    File f = new File(pd, m.toLowerCase(Locale.ROOT) + ".yml");
                    if (f.exists() && main == null) {
                        main = YamlConfiguration.loadConfiguration(f);
                        mainMode = m;
                    }
                }

                for (File f : files) {
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    boolean unsorted = n.startsWith("hub-unsorted-");
                    for (String m : modes) {
                        unsorted |= n.startsWith(m.toLowerCase(Locale.ROOT) + "-unsorted-");
                    }

                    // the other old SMP modes (eco, lifesteal...): their items wait in /oldstuff, so nothing is left behind
                    for (String m : OLD_MODES) {
                        String base = m.toLowerCase(Locale.ROOT);
                        unsorted |= !modes.contains(m) && (n.equals(base + ".yml") || n.startsWith(base + "-unsorted-"));
                    }

                    if (!unsorted) {
                        continue;
                    }

                    YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                    try {
                        addAll(inv, ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(y.getString("contents", ""))));
                        addAll(ender, ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(y.getString("enderchest", ""))));
                    } catch (RuntimeException var28) {
                        this.plugin.getLogger().warning("Import: could not read " + f + " (" + var28.getMessage() + ")");
                    }
                }

                if (main != null || !inv.isEmpty() || !ender.isEmpty()) {
                    inventories++;
                    if (!preview) {
                        this.state.set("imported." + u, true);
                        if (!inv.isEmpty() || !ender.isEmpty()) {
                            this.writePending(u, inv, ender, 0);
                        }

                        if (main != null) {
                            this.writeExact(u, main, mainMode);
                        }
                    }
                }
            }
        }

        String report = homes + " homes, " + warps + " warps, " + spots + " last spots, " + inventories + " inventories"
            + " (modes " + String.join("/", modes) + "; worlds " + worlds + ")";
        Map<String, Integer> counts = modeCounts(root);
        boolean anyChosen = counts.keySet().stream().anyMatch(modes::contains);
        if (!anyChosen && !counts.isEmpty()) {
            String hint = "No saved inventories for import.mode " + String.join("/", modes) + ", but there are some for " + counts
                + " - set import.mode in config.yml to one of those (or ALL) and run /vortexsmp import run again.";
            report = report + ". " + hint;
            this.plugin.getLogger().warning("Import: " + hint);
        }
        if (preview) {
            if (who != null) {
                who.sendMessage(Text.mm("<gold>Import preview <gray>from <white>" + Text.esc(root.getPath()) + "<gray>: <white>" + Text.esc(report)));
            }

            return;
        }

        try {
            mine.save(homesFile);
            mw.save(warpsFile);
        } catch (IOException var27) {
            this.plugin.getLogger().log(Level.SEVERE, "Import: could not write the imported files", (Throwable)var27);
            return;
        }

        this.summary = "imported " + report;
        this.state.set("done", true);
        this.state.set("from", root.getPath());
        this.state.set("at", System.currentTimeMillis());
        this.state.set("summary", this.summary);
        this.state.set("inventories", inventories);
        this.save();
        this.plugin.getLogger().info("Import from " + root.getPath() + ": " + report + ".");
        if (who != null) {
            who.sendMessage(Text.mm("<green>Imported: <white>" + Text.esc(report)));
        }
    }

    private static void addAll(List<ItemStack> out, ItemStack[] items) {
        for (ItemStack it : items) {
            if (it != null && !it.isEmpty() && !junk(it)) {
                out.add(it);
            }
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException var2) {
            return 0;
        }
    }

    private static int[] box(String s) {
        if (s == null) {
            return null;
        }

        String[] p = s.split(";");
        if (p.length < 4) {
            return null;
        }

        try {
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])};
        } catch (NumberFormatException var3) {
            return null;
        }
    }

    // ------------------------------------------------------------------ pending items

    private File exactFile(UUID u) {
        return new File(this.pendingDir, u + "-exact.yml");
    }

    /** The saved SMP inventory of a player as it was (every slot, ender chest, XP, health, food). */
    private void writeExact(UUID u, YamlConfiguration main, String mode) {
        YamlConfiguration y = new YamlConfiguration();
        for (String k : List.of("contents", "enderchest", "level", "exp", "health", "food", "gamemode", "saved")) {
            if (main.isSet(k)) {
                y.set(k, main.get(k));
            }
        }

        y.set("mode", mode);
        try {
            this.pendingDir.mkdirs();
            y.save(this.exactFile(u));
        } catch (IOException var6) {
            this.plugin.getLogger().log(Level.WARNING, "Import: could not save the inventory of " + u, (Throwable)var6);
        }
    }

    private File pendingFile(UUID u) {
        return new File(this.pendingDir, u + ".yml");
    }

    private void writePending(UUID u, List<ItemStack> inv, List<ItemStack> ender, int level) {
        File f = this.pendingFile(u);
        YamlConfiguration y = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        List<ItemStack> i2 = read(y, "inventory");
        i2.addAll(inv);
        List<ItemStack> e2 = read(y, "enderchest");
        e2.addAll(ender);
        y.set("inventory", enc(i2));
        y.set("enderchest", enc(e2));
        y.set("level", Math.max(level, y.getInt("level")));
        try {
            f.getParentFile().mkdirs();
            y.save(f);
        } catch (IOException var9) {
            this.plugin.getLogger().log(Level.WARNING, "Import: could not save the items of " + u, (Throwable)var9);
        }
    }

    private static List<ItemStack> read(YamlConfiguration y, String key) {
        List<ItemStack> out = new ArrayList<>();
        String s = y.getString(key, "");
        if (!s.isEmpty()) {
            for (ItemStack it : ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(s))) {
                if (it != null && !it.isEmpty()) {
                    out.add(it);
                }
            }
        }

        return out;
    }

    private static String enc(List<ItemStack> items) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items.toArray(new ItemStack[0])));
    }

    /** A hub, lobby or kit item of the practice plugins. */
    public static boolean junk(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) {
            return false;
        }

        for (NamespacedKey k : it.getItemMeta().getPersistentDataContainer().getKeys()) {
            for (String ns : OLD) {
                if (k.getNamespace().equals(ns) && JUNK_KEYS.contains(k.getKey())) {
                    return true;
                }
            }
        }

        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Tasks.later(10L, () -> {
            if (p.isOnline()) {
                this.welcome(p);
            }
        });
    }

    // ------------------------------------------------------------------ players whose ID changed

    /** Premium IDs looked up by name (offline-mode servers), so Mojang is asked once per name. */
    private final Map<String, java.util.Optional<UUID>> premium = new java.util.concurrent.ConcurrentHashMap<>();

    private static File playerData() {
        return new File(Bukkit.getWorlds().get(0).getWorldFolder(), "playerdata");
    }

    private static UUID offlineId(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private java.util.Optional<UUID> premiumId(String name) {
        return this.premium.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> {
            try {
                java.net.http.HttpResponse<String> res = java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                        .timeout(java.time.Duration.ofSeconds(5)).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    return java.util.Optional.empty();
                }

                String id = com.google.gson.JsonParser.parseString(res.body()).getAsJsonObject().get("id").getAsString();
                return java.util.Optional.of(UUID.fromString(id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")));
            } catch (Exception e) {
                return java.util.Optional.empty();
            }
        });
    }

    /** IDs from usercache.json files: this server's, the old server's (next to the old plugin folder) and the import folder's. */
    private List<UUID> cachedIds(String name) {
        List<File> caches = new ArrayList<>();
        caches.add(new File(Bukkit.getWorldContainer(), "usercache.json"));
        File root = this.findRoot();
        if (root != null && root.getParentFile() != null && root.getParentFile().getParentFile() != null) {
            caches.add(new File(root.getParentFile().getParentFile(), "usercache.json"));
        }

        try (java.util.stream.Stream<java.nio.file.Path> st = Files.walk(this.dir.toPath(), 4)) {
            st.filter(x -> x.getFileName().toString().equals("usercache.json")).forEach(x -> caches.add(x.toFile()));
        } catch (IOException | RuntimeException ignored) {
        }

        List<UUID> out = new ArrayList<>();
        // the old plugin's own player list (data.db: vr_players uuid/name)
        File db = root == null ? null : new File(root, "data.db");
        if (db != null && db.isFile()) {
            try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:file:" + db.getAbsolutePath() + "?mode=ro");
                 java.sql.PreparedStatement ps = c.prepareStatement("SELECT uuid FROM vr_players WHERE lower(name) = lower(?)")) {
                ps.setString(1, name);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(UUID.fromString(rs.getString(1)));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        for (File f : caches) {
            if (!f.isFile()) {
                continue;
            }

            try {
                for (com.google.gson.JsonElement el : com.google.gson.JsonParser.parseString(Files.readString(f.toPath())).getAsJsonArray()) {
                    com.google.gson.JsonObject o = el.getAsJsonObject();
                    if (o.get("name").getAsString().equalsIgnoreCase(name)) {
                        out.add(UUID.fromString(o.get("uuid").getAsString()));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return out;
    }

    /** The IDs this name may have had on the old server (other than the one it has now). */
    private List<UUID> oldIds(UUID now, String name) {
        java.util.LinkedHashSet<UUID> out = new java.util.LinkedHashSet<>();
        out.add(offlineId(name));
        out.addAll(this.cachedIds(name));
        if (!Bukkit.getOnlineMode()) {
            this.premiumId(name).ifPresent(out::add);
        }

        out.remove(now);
        return new ArrayList<>(out);
    }

    private boolean hasOldData(UUID u) {
        File root = this.findRoot();
        return new File(playerData(), u + ".dat").isFile() || this.exactFile(u).isFile() || this.pendingFile(u).isFile()
            || this.state.getBoolean("imported." + u) || root != null && new File(root, "inventories/groups/" + u).isDirectory();
    }

    /** The old ID of a player whose ID changed, or null. */
    private UUID findOld(UUID now, String name) {
        return this.findOld(now, name, false);
    }

    /** {@code ignoreCurrent}: an admin relink - the (empty) data they got on the new server doesn't count. */
    private UUID findOld(UUID now, String name, boolean ignoreCurrent) {
        String linked = this.state.getString("linked." + now);
        if (linked != null) {
            return UUID.fromString(linked);
        }

        if (!ignoreCurrent && this.hasOldData(now)) {
            return null; // their data is under this ID already
        }

        for (UUID o : this.oldIds(now, name)) {
            if (this.hasOldData(o)) {
                return o;
            }
        }

        return null;
    }

    /**
     * Before the server loads a player: when their ID changed with the move (the old and new host use a different
     * online-mode), their old player file (inventory, ender chest, XP, position) is copied to the new ID, and their
     * saved import files and homes follow. Also finishes an admin's /vortexsmp import relink.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.ALLOWED || !Cfg.bool(Opt.IMPORT_INVENTORIES) || this.plugin.realm().on()) {
            return;
        }

        try {
            synchronized (this) {
                this.relink(e.getUniqueId(), e.getName(), false);
            }
        } catch (Exception ex) {
            this.plugin.getLogger().log(Level.WARNING, "Import: couldn't move the old data of " + e.getName() + " to their new ID", ex);
        }
    }

    /** Links a new ID to the old one. {@code force}: replace the player file they already have (after a relink). */
    private UUID relink(UUID now, String name, boolean force) throws IOException {
        String stage = this.state.getString("relink." + now);
        boolean swap = force || "swap".equals(stage);
        if (this.state.isSet("linked." + now) && !swap) {
            return null;
        }

        UUID old = this.findOld(now, name, swap || stage != null);
        if (old == null || "save".equals(stage)) {
            return old;
        }

        File oldDat = new File(playerData(), old + ".dat");
        File newDat = new File(playerData(), now + ".dat");
        if (oldDat.isFile() && (!newDat.exists() || swap)) {
            if (newDat.exists()) {
                File keep = new File(this.plugin.getDataFolder(), "backups/relinked/" + now + "-" + System.currentTimeMillis() + ".dat");
                keep.getParentFile().mkdirs();
                Files.copy(newDat.toPath(), keep.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            Files.copy(oldDat.toPath(), newDat.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        // the saved import files and the last spot follow the player to the new ID
        if (this.exactFile(old).isFile() && !this.exactFile(now).exists()) {
            Files.move(this.exactFile(old).toPath(), this.exactFile(now).toPath());
        }

        if (this.pendingFile(old).isFile()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(this.pendingFile(old));
            this.writePending(now, read(y, "inventory"), read(y, "enderchest"), y.getInt("level"));
            Files.delete(this.pendingFile(old).toPath());
        }

        String spot = this.state.getString("spots." + old);
        if (spot != null && !this.state.isSet("spots." + now)) {
            this.state.set("spots." + now, spot);
            this.state.set("spots." + old, null);
        }

        this.state.set("linked." + now, old.toString());
        this.state.set("relink." + now, null);
        this.save();
        UUID o = old;
        Bukkit.getScheduler().runTask(this.plugin.host(), () -> this.plugin.essentials().rekeyHomes(o, now));
        this.plugin.getLogger().info("Import: " + name + " has a new ID (" + now + "); their old data (" + old + ") was moved to it"
            + (oldDat.isFile() ? " - inventory, ender chest, XP and position included." : "."));
        return old;
    }

    private void welcome(Player p) {
        if (net.vortexrealm.survival.smp.KeepOnlineBot.isBot(p) || this.plugin.realm().on()) {
            return;
        }

        if ("save".equals(this.state.getString("relink." + p.getUniqueId()))) {
            // step 1 of a relink: what they carry now goes to /oldstuff, then the old player file is put in at the next login
            List<ItemStack> inv = new ArrayList<>();
            List<ItemStack> ender = new ArrayList<>();
            addAll(inv, p.getInventory().getContents());
            addAll(ender, p.getEnderChest().getContents());
            if (!inv.isEmpty() || !ender.isEmpty()) {
                this.writePending(p.getUniqueId(), inv, ender, 0);
            }

            this.state.set("relink." + p.getUniqueId(), "swap");
            this.save();
            p.kick(Text.mm("<green><b>Your old SMP inventory is ready</b>\n<gray>Join again to get it back.\n<gray>What you had since the move is in <yellow>/oldstuff</yellow>."));
            return;
        }

        // hub/lobby items that came along in a copied world's player data are not survival items
        int removed = 0;
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) {
            if (junk(c[i])) {
                c[i] = null;
                removed++;
            }
        }

        if (removed > 0) {
            p.getInventory().setContents(c);
            this.plugin.getLogger().info("Removed " + removed + " practice lobby items from " + p.getName() + ".");
        }

        String spot = this.state.getString("spots." + p.getUniqueId());
        if (spot != null) {
            this.state.set("spots." + p.getUniqueId(), null);
            this.save();
            Location l = Locs.parse(spot);
            Location spawn = p.getWorld().getSpawnLocation();
            boolean atSpawn = !p.hasPlayedBefore() || p.getWorld().equals(spawn.getWorld()) && p.getLocation().distanceSquared(spawn) < 32 * 32;
            if (l != null && l.getWorld() != null && atSpawn) {
                // only onto solid ground: a spot over a drop (other terrain, a removed build) would kill them with their items
                World w = l.getWorld();
                boolean ground = false;
                for (int k = 1; k <= 3 && !ground; k++) {
                    ground = w.getBlockAt(l.getBlockX(), l.getBlockY() - k, l.getBlockZ()).getType().isSolid();
                }

                if (!ground) {
                    l.setY(w.getHighestBlockYAt(l.getBlockX(), l.getBlockZ()) + 1.0);
                }

                p.setFallDistance(0.0F);
                Tp.to(p, l);
                p.sendMessage(Text.mm("<green>Welcome back! <gray>You're where you last were on the old SMP."));
            }
        }

        this.restoreExact(p);
        File f = this.pendingFile(p.getUniqueId());
        if (!f.exists()) {
            return;
        }

        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<ItemStack> inv = read(y, "inventory");
        List<ItemStack> ender = read(y, "enderchest");
        boolean invEmpty = isEmpty(p.getInventory().getContents());
        boolean enderEmpty = isEmpty(p.getEnderChest().getContents());
        if (invEmpty && !inv.isEmpty()) {
            List<ItemStack> rest = new ArrayList<>(p.getInventory().addItem(inv.toArray(new ItemStack[0])).values());
            inv.clear();
            inv.addAll(rest);
            if (p.getLevel() == 0) {
                p.setLevel(y.getInt("level"));
            }
        }

        if (enderEmpty && !ender.isEmpty()) {
            List<ItemStack> rest = new ArrayList<>(p.getEnderChest().addItem(ender.toArray(new ItemStack[0])).values());
            ender.clear();
            ender.addAll(rest);
        }

        this.storePending(p.getUniqueId(), inv, ender);
        if (inv.isEmpty() && ender.isEmpty()) {
            p.sendMessage(Text.mm("<green>Your items from the old SMP are back in your inventory and ender chest!"));
        } else {
            p.sendMessage(Text.mm("<gold>You have items from the old SMP waiting. <yellow>/oldstuff</yellow> <gray>to take them."));
        }

        Snd.play(p, "entity.player.levelup", 0.7F, 1.4F);
    }

    /**
     * The old plugin's inventory marker on the player ("SURVIVAL", "HUB"...) - it stays in the player data when the
     * old world is copied over and says which inventory the player is carrying. Null when there is none.
     */
    private static String oldMarker(Player p, boolean remove) {
        String found = null;
        org.bukkit.persistence.PersistentDataContainer pdc = p.getPersistentDataContainer();
        for (NamespacedKey k : new ArrayList<>(pdc.getKeys())) {
            boolean old = false;
            for (String ns : OLD) {
                old |= k.getNamespace().equals(ns);
            }

            if (old && k.getKey().equals("inv_group")) {
                String v = pdc.get(k, org.bukkit.persistence.PersistentDataType.STRING);
                if (v != null) {
                    found = v.toUpperCase(Locale.ROOT);
                }

                if (remove) {
                    pdc.remove(k);
                }
            }
        }

        return found;
    }

    /**
     * Gives back the saved SMP inventory exactly as it was (every slot, the ender chest, XP, health and food).
     * When the player's data says they are already carrying their SMP inventory (they logged off in the SMP on the
     * old server), that one is newer and is kept. Nothing they carry is thrown away: it goes to /oldstuff.
     */
    private void restoreExact(Player p) {
        File f = this.exactFile(p.getUniqueId());
        if (!f.exists()) {
            oldMarker(p, true);
            return;
        }

        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String mode = y.getString("mode", "SURVIVAL").toUpperCase(Locale.ROOT);
        String marker = oldMarker(p, true);
        File done = new File(this.pendingDir, "given/" + p.getUniqueId() + "-exact-" + System.currentTimeMillis() + ".yml");
        done.getParentFile().mkdirs();
        boolean carriesSmp = marker != null && !marker.equals("HUB") && !marker.equals("PVP") && !marker.equals("NONE");
        if (carriesSmp && marker.equals(mode)) {
            // their player data already holds this SMP inventory, newer than the saved copy
            f.renameTo(done);
            this.plugin.getLogger().info(p.getName() + " already carries the " + mode + " inventory from the old world - kept it.");
            return;
        }

        ItemStack[] saved;
        ItemStack[] savedEnder;
        try {
            saved = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(y.getString("contents", "")));
            savedEnder = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(y.getString("enderchest", "")));
        } catch (RuntimeException var14) {
            this.plugin.getLogger().warning("Could not read the saved inventory of " + p.getName() + ": " + var14.getMessage() + " (kept in " + f.getName() + ")");
            return;
        }

        // whatever they carry now (another mode's items, PvP gear...) is kept in /oldstuff, never deleted
        List<ItemStack> keepInv = new ArrayList<>();
        List<ItemStack> keepEnder = new ArrayList<>();
        addAll(keepInv, p.getInventory().getContents());
        addAll(keepEnder, p.getEnderChest().getContents());
        if (!keepInv.isEmpty() || !keepEnder.isEmpty()) {
            this.writePending(p.getUniqueId(), keepInv, keepEnder, 0);
        }

        ItemStack[] inv = new ItemStack[p.getInventory().getSize()];
        for (int i = 0; i < inv.length && i < saved.length; i++) {
            inv[i] = saved[i] == null || saved[i].isEmpty() || junk(saved[i]) ? null : saved[i];
        }

        ItemStack[] ender = new ItemStack[p.getEnderChest().getSize()];
        for (int i = 0; i < ender.length && i < savedEnder.length; i++) {
            ender[i] = savedEnder[i] == null || savedEnder[i].isEmpty() ? null : savedEnder[i];
        }

        p.getInventory().setContents(inv);
        p.getEnderChest().setContents(ender);
        p.setLevel(y.getInt("level", p.getLevel()));
        p.setExp((float)Math.max(0.0, Math.min(1.0, y.getDouble("exp", p.getExp()))));
        p.setFoodLevel(y.getInt("food", 20));
        try {
            org.bukkit.attribute.AttributeInstance max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            p.setHealth(Math.max(1.0, Math.min(max == null ? 20.0 : max.getValue(), y.getDouble("health", 20.0))));
            String gm = y.getString("gamemode", "SURVIVAL");
            if (!p.hasPermission("vortexsmp.admin") && !"HUB".equals(mode)) {
                p.setGameMode(org.bukkit.GameMode.valueOf(gm.equals("ADVENTURE") ? "SURVIVAL" : gm));
            }
        } catch (RuntimeException var13) {
        }

        p.updateInventory();
        f.renameTo(done);
        this.plugin.getLogger().info("Gave " + p.getName() + " their old " + mode + " inventory and ender chest back (exact slots)"
            + (keepInv.isEmpty() && keepEnder.isEmpty() ? "." : "; what they carried before is in /oldstuff."));
        p.sendMessage(Text.mm("<green><b>Welcome back!</b> <gray>Your inventory, ender chest and XP from the old SMP are back, exactly as they were."));
        if (!keepInv.isEmpty() || !keepEnder.isEmpty()) {
            p.sendMessage(Text.mm("<gold>The items you carried before are safe in <yellow>/oldstuff</yellow>."));
        }

        Snd.play(p, "entity.player.levelup", 0.7F, 1.4F);
    }

    private static boolean isEmpty(ItemStack[] items) {
        for (ItemStack it : items) {
            if (it != null && !it.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private void storePending(UUID u, List<ItemStack> inv, List<ItemStack> ender) {
        File f = this.pendingFile(u);
        if (inv.isEmpty() && ender.isEmpty()) {
            File done = new File(this.pendingDir, "given/" + u + "-" + System.currentTimeMillis() + ".yml");
            done.getParentFile().mkdirs();
            if (!f.renameTo(done)) {
                f.delete();
            }

            return;
        }

        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        y.set("inventory", enc(inv));
        y.set("enderchest", enc(ender));
        try {
            y.save(f);
        } catch (IOException var6) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save the waiting items of " + u, (Throwable)var6);
        }
    }

    // ------------------------------------------------------------------ /oldstuff

    private final class Box implements InventoryHolder {
        final UUID owner;
        final boolean ender;
        Inventory inv;

        Box(UUID owner, boolean ender) {
            this.owner = owner;
            this.ender = ender;
        }

        @Override
        public Inventory getInventory() {
            return this.inv;
        }
    }

    private void open(Player p, boolean ender) {
        File f = this.pendingFile(p.getUniqueId());
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<ItemStack> items = f.exists() ? read(y, ender ? "enderchest" : "inventory") : new ArrayList<>();
        if (items.isEmpty()) {
            List<ItemStack> other = f.exists() ? read(y, ender ? "inventory" : "enderchest") : List.of();
            if (!other.isEmpty()) {
                this.open(p, !ender);
                return;
            }

            p.sendMessage(Text.mm("<gray>No items from the old SMP are waiting for you."));
            return;
        }

        Box b = new Box(p.getUniqueId(), ender);
        b.inv = Bukkit.createInventory(b, 54, Text.mm("<gold><b>Old SMP</b> <gray>" + (ender ? "ender chest" : "inventory") + " <dark_gray>- take your items"));
        for (int i = 0; i < items.size() && i < 54; i++) {
            b.inv.setItem(i, items.get(i));
        }

        p.openInventory(b.inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof Box b && e.getPlayer() instanceof Player p) {
            List<ItemStack> left = new ArrayList<>();
            for (ItemStack it : e.getInventory().getContents()) {
                if (it != null && !it.isEmpty()) {
                    left.add(it);
                }
            }

            File f = this.pendingFile(b.owner);
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            List<ItemStack> inv = b.ender ? read(y, "inventory") : left;
            List<ItemStack> ender = b.ender ? left : read(y, "enderchest");
            // items beyond the 54 shown slots stay waiting
            List<ItemStack> all = read(y, b.ender ? "enderchest" : "inventory");
            for (int i = 54; i < all.size(); i++) {
                (b.ender ? ender : inv).add(all.get(i));
            }

            this.storePending(b.owner, inv, ender);
            if (!inv.isEmpty() || !ender.isEmpty()) {
                p.sendMessage(Text.mm("<gray>The rest stays in <yellow>/oldstuff</yellow>."));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            this.open(p, args.length > 0 && args[0].toLowerCase(Locale.ROOT).startsWith("e"));
        } else {
            sender.sendMessage("Players only.");
        }

        return true;
    }

    private void relinkCommand(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        org.bukkit.OfflinePlayer t = online != null ? online : Bukkit.getOfflinePlayer(name);
        UUID now = t.getUniqueId();
        UUID old;
        synchronized (this) {
            this.state.set("linked." + now, null);
            old = this.findOld(now, name, true);
        }

        if (old == null) {
            sender.sendMessage(Text.mm("<red>No old data found for " + Text.esc(name) + " under another ID. <gray>Try <white>/vortexsmp import check " + Text.esc(name)));
            return;
        }

        boolean oldDat = new File(playerData(), old + ".dat").isFile();
        try {
            if (oldDat) {
                synchronized (this) {
                    this.state.set("relink." + now, "save");
                    this.save();
                }

                sender.sendMessage(Text.mm("<green>" + Text.esc(name) + " will get their old inventory, ender chest and XP back <gray>(old ID " + old + "). "
                    + (online != null ? "They're asked to rejoin now." : "It happens when they next join (they rejoin once).")
                    + " What they have now goes to /oldstuff."));
                if (online != null) {
                    this.welcome(online);
                }
            } else {
                synchronized (this) {
                    this.relink(now, name, true);
                }

                sender.sendMessage(Text.mm("<green>" + Text.esc(name) + "'s saved items moved to their new ID <gray>(from " + old + ")."));
                if (online != null) {
                    this.welcome(online);
                }
            }
        } catch (IOException e) {
            sender.sendMessage(Text.mm("<red>That failed: " + Text.esc(e.getMessage())));
        }
    }

    /** /vortexsmp import check [player]: where the old data is, what was found, and what to do next. */
    private void check(CommandSender s, String name) {
        File root = this.findRoot();
        s.sendMessage(Text.mm("<gold><b>Old SMP import check"));
        if (root == null) {
            s.sendMessage(Text.mm("<red>✘ No old plugin folder found. <gray>Looked in plugins/VortexSMP/import/ and plugins/VortexRealm, VortexRealm, VortexPvP."));
            s.sendMessage(Text.mm("<gray>→ Copy the old <white>plugins/VortexRealm</white> folder (with its inventories folder) from the old server into <white>plugins/</white> and restart."));
        } else {
            Map<String, Integer> counts = modeCounts(root);
            s.sendMessage(Text.mm("<green>✔ Old folder: <white>" + Text.esc(root.getPath())));
            s.sendMessage(Text.mm((counts.isEmpty() ? "<red>✘" : "<green>✔") + " <gray>Saved inventories per mode: <white>" + (counts.isEmpty() ? "none (no inventories/groups folder)" : counts)
                + " <gray>• import.mode <white>" + Text.esc(Cfg.s(Opt.IMPORT_MODE))));
            if (!counts.isEmpty() && counts.keySet().stream().noneMatch(this.modes()::contains)) {
                s.sendMessage(Text.mm("<yellow>→ import.mode doesn't match any of those: set it to one of them (or ALL), then <white>/vortexsmp import run again"));
            }
        }

        s.sendMessage(Text.mm("<gray>Import: <white>" + Text.esc(this.summary) + " <gray>• online-mode <white>" + Bukkit.getOnlineMode()
            + " <gray>• player files in the world: <white>" + java.util.Optional.ofNullable(playerData().list()).map(a -> a.length).orElse(0)));
        if (name == null) {
            s.sendMessage(Text.mm("<gray>Add a player name to check one player: <white>/vortexsmp import check <name>"));
            return;
        }

        org.bukkit.OfflinePlayer t = Bukkit.getPlayerExact(name) != null ? Bukkit.getPlayerExact(name) : Bukkit.getOfflinePlayer(name);
        UUID now = t.getUniqueId();
        s.sendMessage(Text.mm("<gray>" + Text.esc(name) + " now has ID <white>" + now));
        s.sendMessage(Text.mm("<gray> under this ID: " + this.describe(now)));
        for (UUID o : this.oldIds(now, name)) {
            s.sendMessage(Text.mm("<gray> old ID " + o + ": " + this.describe(o)));
        }

        UUID old = this.state.isSet("linked." + now) ? null : this.findOld(now, name, true);
        if (old != null) {
            s.sendMessage(Text.mm("<yellow>→ Their old data is under another ID. <white>/vortexsmp import relink " + Text.esc(name) + "</white> <yellow>gives it back."));
        } else if (this.exactFile(now).isFile() || this.pendingFile(now).isFile()) {
            s.sendMessage(Text.mm("<yellow>→ Items are saved for them: they get them when they join, or now with <white>/vortexsmp import give " + Text.esc(name)));
        } else if (this.hasOldData(now)) {
            s.sendMessage(Text.mm("<green>✔ Their data is under their current ID - nothing else to do."));
        } else {
            s.sendMessage(Text.mm("<red>✘ Nothing saved for them under any ID. <gray>Check that the old world's playerdata and the old inventories folder were copied."));
        }
    }

    private String describe(UUID u) {
        List<String> has = new ArrayList<>();
        if (new File(playerData(), u + ".dat").isFile()) {
            has.add("player file");
        }

        if (this.exactFile(u).isFile()) {
            has.add("saved inventory (not given yet)");
        }

        if (this.pendingFile(u).isFile()) {
            has.add("items for /oldstuff");
        }

        File[] given = new File(this.pendingDir, "given").listFiles((d, n) -> n.startsWith(u.toString()));
        if (given != null && given.length > 0) {
            has.add("<green>already given back</green>");
        }

        File root = this.findRoot();
        if (root != null && new File(root, "inventories/groups/" + u).isDirectory()) {
            has.add("old inventory folder");
        }

        return has.isEmpty() ? "<dark_gray>nothing" : "<white>" + String.join(", ", has);
    }

    /** /vortexsmp migrate: checks the move of an old SMP (world + plugin folder) and says what is missing. */
    public void migrateGuide(CommandSender s) {
        File root = this.findRoot();
        World main = Bukkit.getWorlds().get(0);
        File pd = new File(main.getWorldFolder(), "playerdata");
        String[] files = pd.list((d, n) -> n.endsWith(".dat"));
        int players = files == null ? 0 : files.length;
        java.util.List<String> oldSmp = new ArrayList<>();
        if (root != null) {
            YamlConfiguration wy = YamlConfiguration.loadConfiguration(new File(root, "worlds.yml"));
            ConfigurationSection ws = wy.getConfigurationSection("worlds");
            if (ws != null) {
                for (String w : ws.getKeys(false)) {
                    String m = ws.getString(w + ".mode", "SURVIVAL").toUpperCase(Locale.ROOT);
                    if (this.modes().contains(m)) {
                        oldSmp.add(w);
                    }
                }
            }
        }

        s.sendMessage(Text.mm("<gold><b>Moving the old SMP to this server</b>"));
        s.sendMessage(Text.mm((root != null ? "<green>✔" : "<red>✖") + " <gray>Old plugin folder in plugins/VortexSMP/import/"
            + (root != null ? " <dark_gray>(" + Text.esc(root.getPath()) + ")" : " <white>- copy the old plugins/VortexRealm folder there")));
        s.sendMessage(Text.mm((oldSmp.isEmpty() || oldSmp.contains(main.getName()) ? "<green>✔" : "<yellow>!") + " <gray>Old SMP world: <white>"
            + (oldSmp.isEmpty() ? "?" : String.join(", ", oldSmp)) + " <gray>· this server's main world: <white>" + Text.esc(main.getName())
            + (oldSmp.isEmpty() || oldSmp.contains(main.getName()) ? "" : " <yellow>- copy the old SMP world folder and set level-name=" + oldSmp.get(0) + " in server.properties (or keep the name: it is mapped automatically)")));
        s.sendMessage(Text.mm((players > 0 ? "<green>✔" : "<yellow>!") + " <gray>Player data: <white>" + players + " players <gray>in " + Text.esc(main.getName()) + "/playerdata"
            + (players > 0 ? "" : " <yellow>- copy the old server's main world playerdata/ (and stats/, advancements/) here so everyone keeps what they carry")));
        s.sendMessage(Text.mm("<gray>Status: <white>" + Text.esc(this.summary)));
        s.sendMessage(Text.mm("<gray>On join each player gets their SMP inventory, ender chest and XP back exactly; if their copied player data already holds the newer SMP inventory, that one is kept. Nothing is deleted: anything else they carried goes to <yellow>/oldstuff</yellow>."));
        if (root != null && !this.state.getBoolean("done")) {
            s.sendMessage(Text.mm("<gray>Next: <yellow>/vortexsmp import preview</yellow> then <yellow>/vortexsmp import run</yellow> (or just restart)."));
        }
    }

    /** /vortexsmp import [preview|run] */
    public void command(CommandSender sender, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("check")) {
            this.check(sender, args.length > 1 ? args[1] : null);
            return;
        } else if (sub.equals("relink") || sub.equals("give")) {
            if (args.length < 2) {
                sender.sendMessage(Text.mm("<red>Usage: /vortexsmp import " + sub + " <player>"));
                return;
            }

            if (sub.equals("give")) {
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    sender.sendMessage(Text.mm("<red>" + Text.esc(args[1]) + " isn't online."));
                } else {
                    this.welcome(t);
                    sender.sendMessage(Text.mm("<green>Done - " + Text.esc(t.getName()) + " got whatever was saved for them."));
                }

                return;
            }

            this.relinkCommand(sender, args[1]);
            return;
        }

        File root = this.findRoot();
        if (root == null) {
            sender.sendMessage(Text.mm("<red>Nothing to import. <gray>Put the old plugins/VortexRealm folder (or a .zip/.tar.gz of it) into plugins/VortexSMP/import/."));
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("run")) {
            if (this.state.getBoolean("done") && !(args.length > 1 && args[1].equalsIgnoreCase("again"))) {
                sender.sendMessage(Text.mm("<yellow>Already imported (" + Text.esc(this.summary) + "). <gray>/vortexsmp import run again <gray>to import once more (only missing things are added)."));
                return;
            }

            this.run(root, false, sender);
            this.plugin.essentials().load();
        } else {
            this.run(root, true, sender);
            sender.sendMessage(Text.mm("<gray>Run it with <yellow>/vortexsmp import run"));
        }
    }
}
