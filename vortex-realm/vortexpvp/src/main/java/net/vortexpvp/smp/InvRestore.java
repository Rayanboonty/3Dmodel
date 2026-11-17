package net.vortexpvp.smp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.gui.Menu;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /smpadmin restore: gives players back an inventory or ender chest that was lost, from what the plugin kept -
 * the folder of the plugin used before (VortexPvP / VortexRealm / VortexPvP), earlier saves (.prev, unsorted,
 * before-restore) and the vault backups. A restore is queued and handed over when the player is carrying that
 * inventory group (so it lands in the right world's inventory, online or not); the current items are saved first.
 */
public final class InvRestore {
    /** Built from two parts so the plugin generators (which rename the plugin) leave the old folder names alone. */
    private static final String[] SIBLINGS = {"Practice" + "PvP", "Vortex" + "Realm", "Vortex" + "PvP"};
    /** The backup folders of the three plugins (VortexPvP, VortexRealm, VortexPvP), split the same way. */
    private static final String[] VAULTS = {"VortexRealm" + "-Vault", "VortexRealm" + "-2-Vault", "VortexPvP" + "-Test-Vault"};
    private final VortexPvP plugin;
    private final ModeManager modes;
    private final File pendingDir;
    private final Set<UUID> pending = new HashSet<>();
    private final Map<String, List<Source>> lastList = new HashMap<>();

    /** One place a lost inventory can come from. */
    public record Source(String label, String group, String origin, ItemStack[] contents, ItemStack[] ender, long time) {
        int inv() {
            return count(this.contents);
        }

        int enderCount() {
            return count(this.ender);
        }
    }

    InvRestore(VortexPvP plugin, ModeManager modes) {
        this.plugin = plugin;
        this.modes = modes;
        this.pendingDir = new File(plugin.getDataFolder(), "inventories/restore-pending");
        File[] fs = this.pendingDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (fs != null) {
            for (File f : fs) {
                try {
                    this.pending.add(UUID.fromString(f.getName().replace(".yml", "")));
                } catch (IllegalArgumentException var8) {
                }
            }
        }
    }

    // ------------------------------------------------------------------ reading saved inventories

    static int count(ItemStack[] items) {
        int n = 0;
        if (items != null) {
            for (ItemStack it : items) {
                if (it != null && !it.isEmpty()) {
                    n += it.getAmount();
                }
            }
        }

        return n;
    }

    static ItemStack[] decode(String b64) {
        if (b64 == null || b64.isEmpty()) {
            return new ItemStack[0];
        }

        try {
            return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(b64));
        } catch (RuntimeException var2) {
            return new ItemStack[0];
        }
    }

    static String encode(ItemStack[] items) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }

    /** "survival.yml", "survival.prev.yml", "survival-unsorted-123.yml", "survival-before-restore-123.yml" -> SURVIVAL. */
    static String groupOfFile(String name) {
        String g = name.replace(".yml", "").replace(".prev", "");
        int cut = g.indexOf('-');
        return (cut > 0 ? g.substring(0, cut) : g).toUpperCase(Locale.ROOT);
    }

    private String storeGroup(YamlConfiguration y) {
        // a PvP-lobby store keeps the SMP items of the world the player came from
        try {
            Location l = net.vortexpvp.util.Locs.parse(y.getString("location", ""));
            if (l != null && l.getWorld() != null) {
                WorldMode m = this.modes.mode(l.getWorld());
                if (m != WorldMode.PVP && m != WorldMode.NONE) {
                    return m.name();
                }
            }
        } catch (RuntimeException var3) {
        }

        return y.getString("group", "SURVIVAL").toUpperCase(Locale.ROOT);
    }

    private Source source(String label, String group, String origin, YamlConfiguration y) {
        return new Source(label, group, origin, decode(y.getString("contents")), decode(y.getString("enderchest")), y.getLong("saved", y.getLong("time", 0L)));
    }

    /** Every saved inventory of one player the plugin can find, newest backups last. */
    public List<Source> sources(UUID u) {
        List<Source> out = new ArrayList<>();
        File plugins = this.plugin.getDataFolder().getAbsoluteFile().getParentFile();
        String own = this.plugin.getDataFolder().getName();
        // 1. the folder of the plugin used before
        for (String sib : SIBLINGS) {
            if (sib.equalsIgnoreCase(own)) {
                continue;
            }

            File base = new File(plugins, sib + "/inventories");
            this.addFolder(out, new File(base, "groups/" + u), sib + " folder", "folder:" + sib);
            File store = new File(base, u + ".yml");
            if (store.isFile()) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(store);
                out.add(this.source(sib + " folder (PvP lobby store)", this.storeGroup(y), "folder:" + sib, y));
            }
        }

        // 2. earlier saves in this plugin's folder
        File groups = new File(this.modes.groupDir(), u.toString());
        File[] fs = groups.listFiles((d, n) -> n.endsWith(".yml") && (n.contains(".prev") || n.contains("-unsorted-") || n.contains("-before-restore-")));
        if (fs != null) {
            for (File f : fs) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                String kind = f.getName().contains(".prev") ? "previous save" : (f.getName().contains("-unsorted-") ? "unsorted items" : "before a restore");
                out.add(this.source("This plugin (" + kind + ")", groupOfFile(f.getName()), "own", y));
            }
        }

        File[] stores = new File(this.plugin.getDataFolder(), "inventories").listFiles((d, n) -> n.startsWith(u + "-backup-") && n.endsWith(".yml"));
        if (stores != null) {
            for (File f : stores) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                out.add(this.source("This plugin (PvP lobby backup)", this.storeGroup(y), "own", y));
            }
        }

        // 3. backups: this plugin's vault and the other plugins' vaults (a switch leaves the good copy in the old one)
        java.util.Set<File> vaults = new java.util.LinkedHashSet<>();
        if (this.plugin.vault() != null) {
            vaults.add(this.plugin.vault().dir().getAbsoluteFile());
        }

        for (String v : VAULTS) {
            vaults.add(new File(plugins, v).getAbsoluteFile());
        }

        for (File vault : vaults) {
            File[] all = vault.listFiles((d, n) -> n.endsWith(".zip"));
            if (all == null) {
                continue;
            }

            java.util.Arrays.sort(all, java.util.Comparator.comparingLong(File::lastModified).reversed());
            boolean ownVault = this.plugin.vault() != null && vault.equals(this.plugin.vault().dir().getAbsoluteFile());
            int updates = 0;
            int regular = 0;
            for (File z : all) {
                // the special ones (before an update, first start, before a restore) and the newest few regular ones
                boolean special = z.getName().contains("pre-update") || z.getName().contains("first-start") || z.getName().contains("before-restore");
                if (special ? updates++ < 3 : regular++ < 3) {
                    this.addZip(out, z, u, ownVault ? "" : " [" + vault.getName() + "]");
                }
            }
        }

        out.removeIf(s -> s.inv() == 0 && s.enderCount() == 0);
        return out;
    }

    private void addFolder(List<Source> out, File dir, String label, String origin) {
        File[] fs = dir.listFiles((d, n) -> n.endsWith(".yml") && !n.endsWith(".tmp"));
        if (fs != null) {
            for (File f : fs) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                String extra = f.getName().contains(".prev") ? ", previous save" : (f.getName().contains("-") ? ", " + f.getName().replace(".yml", "") : "");
                out.add(this.source(label + extra, groupOfFile(f.getName()), origin, y));
            }
        }
    }

    private void addZip(List<Source> out, File zip, UUID u, String where) {
        String label = "Backup " + zip.getName().replace("vault-", "").replace(".zip", "") + where;
        try (ZipFile z = new ZipFile(zip)) {
            java.util.Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                boolean group = n.startsWith("inventories/groups/" + u + "/") && n.endsWith(".yml");
                boolean store = n.equals("inventories/" + u + ".yml");
                if (!group && !store) {
                    continue;
                }

                YamlConfiguration y = new YamlConfiguration();
                try (InputStream in = z.getInputStream(e)) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    in.transferTo(b);
                    y.loadFromString(b.toString(StandardCharsets.UTF_8));
                } catch (InvalidConfigurationException var13) {
                    continue;
                }

                String file = n.substring(n.lastIndexOf('/') + 1);
                out.add(this.source(label + (store ? " (PvP lobby store)" : ""), store ? this.storeGroup(y) : groupOfFile(file), "vault:" + zip.getName(), y));
            }
        } catch (IOException var15) {
            this.plugin.getLogger().log(Level.WARNING, "Could not read " + zip.getName(), var15);
        }
    }

    // ------------------------------------------------------------------ queue and hand over

    private File pendingFile(UUID u) {
        return new File(this.pendingDir, u + ".yml");
    }

    /** Queues a restore; it is handed over as soon as the player carries that group (right away if they do). */
    public void queue(UUID u, Source s, String mode, String by) throws IOException {
        File f = this.pendingFile(u);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String key = "entries.e" + System.nanoTime();
        y.set(key + ".group", s.group());
        y.set(key + ".mode", mode);
        y.set(key + ".label", s.label());
        y.set(key + ".by", by);
        y.set(key + ".contents", encode(s.contents()));
        y.set(key + ".enderchest", encode(s.ender()));
        this.pendingDir.mkdirs();
        y.save(f);
        this.pending.add(u);
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            this.applyPending(p);
        }
    }

    public boolean hasPending(UUID u) {
        return this.pending.contains(u);
    }

    /** Called after every inventory-group check: hands over the queued restores for the group the player holds now. */
    public void applyPending(Player p) {
        if (!this.pending.contains(p.getUniqueId())) {
            return;
        }

        String live = this.modes.liveGroup(p);
        if (live == null) {
            return;
        }

        File f = this.pendingFile(p.getUniqueId());
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection entries = y.getConfigurationSection("entries");
        if (entries == null || entries.getKeys(false).isEmpty()) {
            this.pending.remove(p.getUniqueId());
            f.delete();
            return;
        }

        boolean changed = false;
        for (String k : new ArrayList<>(entries.getKeys(false))) {
            ConfigurationSection e = entries.getConfigurationSection(k);
            String group = e.getString("group", "SURVIVAL");
            if (!live.equals("*") && !live.equalsIgnoreCase(group)) {
                continue;
            }

            if (this.handOver(p, group, e.getString("mode", "enderchest"), decode(e.getString("contents")), decode(e.getString("enderchest")), e.getString("label", "a backup"))) {
                entries.set(k, null);
                changed = true;
            }
        }

        if (changed) {
            try {
                if (entries.getKeys(false).isEmpty()) {
                    f.delete();
                    this.pending.remove(p.getUniqueId());
                } else {
                    y.save(f);
                }
            } catch (IOException var11) {
                this.plugin.getLogger().log(Level.WARNING, "Could not update " + f, var11);
            }
        }
    }

    private boolean handOver(Player p, String group, String mode, ItemStack[] contents, ItemStack[] ender, String label) {
        // what the player holds now is saved first, so a restore can always be undone
        File before = new File(this.modes.groupDir(), p.getUniqueId() + "/" + group.toLowerCase(Locale.ROOT) + "-before-restore-" + System.currentTimeMillis() + ".yml");
        if (!this.modes.saveGroupTo(p, before, group, false)) {
            return false;
        }

        int given = 0;
        int dropped = 0;
        if (mode.equals("inventory") || mode.equals("all")) {
            ItemStack[] fixed = new ItemStack[p.getInventory().getContents().length];
            for (int i = 0; i < fixed.length && i < contents.length; i++) {
                fixed[i] = contents[i] != null && !contents[i].isEmpty() ? contents[i] : null;
            }

            p.getInventory().setContents(fixed);
            given += count(contents);
        }

        if (mode.equals("all")) {
            ItemStack[] e2 = new ItemStack[p.getEnderChest().getSize()];
            for (int i = 0; i < e2.length && i < ender.length; i++) {
                e2[i] = ender[i] != null && !ender[i].isEmpty() ? ender[i] : null;
            }

            p.getEnderChest().setContents(e2);
            given += count(ender);
        } else if (mode.equals("enderchest")) {
            // merged: old items go into the empty slots, nothing the player has now is replaced
            for (ItemStack it : ender) {
                if (it == null || it.isEmpty()) {
                    continue;
                }

                given += it.getAmount();
                for (ItemStack left : p.getEnderChest().addItem(it.clone()).values()) {
                    for (ItemStack left2 : p.getInventory().addItem(left).values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), left2);
                        dropped += left2.getAmount();
                    }
                }
            }
        }

        p.updateInventory();
        p.sendMessage(Text.mm("<green>✔ Your lost items were given back (" + given + " items from " + Text.esc(label) + ")"
            + (dropped > 0 ? " <yellow>- " + dropped + " didn't fit and were dropped at your feet" : "") + "."));
        this.plugin.getLogger().info("Restored " + mode + " of " + p.getName() + " (" + group + ") from " + label + ": " + given + " items, before-restore save " + before.getName());
        return true;
    }

    // ------------------------------------------------------------------ the command

    private OfflinePlayer find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }

        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        return op != null ? op : null;
    }

    /** /smpadmin restore <player> [n] [preview|enderchest|inventory|all] • /smpadmin restore all-from-old [confirm] */
    public void command(CommandSender s, String[] a) {
        if (a.length == 0) {
            s.sendMessage(Text.mm("<gold><b>Restore lost inventories / ender chests</b>"));
            s.sendMessage(Text.mm("<gray>/smpadmin restore <player> <dark_gray>- list what can be restored"));
            s.sendMessage(Text.mm("<gray>/smpadmin restore <player> <#> preview|enderchest|inventory|all"));
            s.sendMessage(Text.mm("<gray>/smpadmin restore all-from-old [confirm] <dark_gray>- give back every ender chest lost when switching plugins"));
            return;
        }

        if (a[0].equalsIgnoreCase("all-from-old")) {
            this.bulk(s, a.length > 1 && a[1].equalsIgnoreCase("confirm"));
            return;
        }

        OfflinePlayer t = this.find(a[0]);
        if (t == null) {
            s.sendMessage(Text.mm("<red>Unknown player " + Text.esc(a[0]) + " (they must have joined this server before)."));
            return;
        }

        String name = t.getName() == null ? a[0] : t.getName();
        List<Source> list = this.sources(t.getUniqueId());
        this.lastList.put(t.getUniqueId().toString(), list);
        if (a.length == 1) {
            if (list.isEmpty()) {
                s.sendMessage(Text.mm("<gray>Nothing saved for " + Text.esc(name) + " that could be restored."));
                return;
            }

            s.sendMessage(Text.mm("<gold><b>Saved items of " + Text.esc(name) + "</b> <gray>(" + list.size() + ")"));
            for (int i = 0; i < list.size(); i++) {
                Source src = list.get(i);
                String n = String.valueOf(i + 1);
                String base = "/smpadmin restore " + name + " " + n + " ";
                s.sendMessage(Text.mm("<white>#" + n + " <yellow>" + Progress.nice(src.group()) + " <gray>• " + Text.esc(src.label())
                    + " <dark_gray>• <gray>inventory <white>" + src.inv() + "</white> • ender chest <light_purple>" + src.enderCount() + "</light_purple> "
                    + "<click:run_command:'" + base + "preview'><aqua>[preview]</aqua></click> "
                    + "<click:suggest_command:'" + base + "enderchest'><light_purple>[ender chest]</light_purple></click> "
                    + "<click:suggest_command:'" + base + "all'><red>[all]</red></click>"));
            }

            s.sendMessage(Text.mm("<gray>enderchest = old ender chest items added to the free slots (nothing replaced) • inventory/all = replaced (the current items are saved first)"));
            if (this.hasPending(t.getUniqueId())) {
                s.sendMessage(Text.mm("<yellow>A restore is already waiting for " + Text.esc(name) + " (handed over when they are in that world)."));
            }

            return;
        }

        int idx;
        try {
            idx = Integer.parseInt(a[1].replace("#", "")) - 1;
        } catch (NumberFormatException var12) {
            idx = -1;
        }

        if (idx < 0 || idx >= list.size()) {
            s.sendMessage(Text.mm("<red>No source #" + Text.esc(a[1]) + " - see /smpadmin restore " + Text.esc(name)));
            return;
        }

        Source src = list.get(idx);
        String mode = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "preview";
        switch (mode) {
            case "preview" -> {
                if (s instanceof Player admin) {
                    new Preview(this.plugin, admin, name, src, true).open();
                } else {
                    s.sendMessage(Text.mm("<gray>" + Text.esc(src.label()) + ": inventory " + src.inv() + " items, ender chest " + src.enderCount() + " items."));
                }
            }
            case "enderchest", "inventory", "all" -> {
                try {
                    this.queue(t.getUniqueId(), src, mode, s.getName());
                } catch (IOException ex) {
                    s.sendMessage(Text.mm("<red>Could not save the restore: " + Text.esc(ex.getMessage())));
                    return;
                }

                boolean done = !this.hasPending(t.getUniqueId());
                s.sendMessage(Text.mm(done ? "<green>✔ Restored " + mode + " of " + Text.esc(name) + " from " + Text.esc(src.label()) + " (their items before are saved too)."
                    : "<green>✔ Queued: " + Text.esc(name) + " gets it the next time they are in the " + Progress.nice(src.group()) + " world (online or after joining)."));
            }
            default -> s.sendMessage(Text.mm("<red>Use preview, enderchest, inventory or all."));
        }
    }

    /**
     * The switch-plugins fix in one go: every player whose saved ender chest in this plugin is empty while the old
     * plugin's folder still has one for the same world gets those items back (merged, nothing replaced).
     */
    private void bulk(CommandSender s, boolean confirm) {
        File plugins = this.plugin.getDataFolder().getAbsoluteFile().getParentFile();
        String own = this.plugin.getDataFolder().getName();
        int found = 0;
        int items = 0;
        List<String> names = new ArrayList<>();
        for (String sib : SIBLINGS) {
            if (sib.equalsIgnoreCase(own)) {
                continue;
            }

            File[] players = new File(plugins, sib + "/inventories/groups").listFiles(File::isDirectory);
            if (players == null) {
                continue;
            }

            for (File dir : players) {
                UUID u;
                try {
                    u = UUID.fromString(dir.getName());
                } catch (IllegalArgumentException var22) {
                    continue;
                }

                File[] files = dir.listFiles((d, n) -> n.endsWith(".yml") && !n.contains(".prev") && !n.contains("-"));
                if (files == null || this.hasPending(u)) {
                    // a restore is already waiting for this player: never hand the same items over twice
                    continue;
                }

                for (File old : files) {
                    String group = groupOfFile(old.getName());
                    if (group.equals("HUB")) {
                        continue;
                    }

                    YamlConfiguration oy = YamlConfiguration.loadConfiguration(old);
                    ItemStack[] oldEnder = decode(oy.getString("enderchest"));
                    if (count(oldEnder) == 0) {
                        continue;
                    }

                    File mine = this.modes.groupFile(u, group);
                    ItemStack[] nowEnder = mine.isFile() ? decode(YamlConfiguration.loadConfiguration(mine).getString("enderchest")) : null;
                    Player online = Bukkit.getPlayer(u);
                    if (online != null && group.equalsIgnoreCase(String.valueOf(this.modes.liveGroup(online)))) {
                        nowEnder = online.getEnderChest().getContents();
                    }

                    // only the exact loss pattern: our copy of that world's ender chest is empty now
                    if (nowEnder == null || count(nowEnder) > 0) {
                        continue;
                    }

                    found++;
                    items += count(oldEnder);
                    OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                    names.add((op.getName() == null ? u.toString().substring(0, 8) : op.getName()) + " (" + Progress.nice(group) + ", " + count(oldEnder) + ")");
                    if (confirm) {
                        try {
                            this.queue(u, new Source(sib + " folder", group, "folder:" + sib, new ItemStack[0], oldEnder, old.lastModified()), "enderchest", s.getName());
                        } catch (IOException ex) {
                            s.sendMessage(Text.mm("<red>Could not queue " + u + ": " + Text.esc(ex.getMessage())));
                        }
                    }
                }
            }
        }

        if (found == 0) {
            s.sendMessage(Text.mm("<gray>No lost ender chests found (no old plugin folder, or every ender chest is already there)."));
            return;
        }

        s.sendMessage(Text.mm((confirm ? "<green>✔ Giving back " : "<yellow>Would give back ") + items + " items to " + found + " ender chest" + (found == 1 ? "" : "s") + ":"));
        s.sendMessage(Text.mm("<gray>" + Text.esc(String.join(", ", names.subList(0, Math.min(40, names.size())))) + (names.size() > 40 ? " ..." : "")));
        s.sendMessage(Text.mm(confirm ? "<gray>Online players got them now; the others get them when they next enter that world."
            : "<gray>Nothing changed yet. Run <white>/smpadmin restore all-from-old confirm</white> to do it."));
    }

    /** Read-only look at a saved inventory: the ender chest, then the inventory. */
    private static final class Preview extends Menu {
        private final String name;
        private final Source src;
        private final boolean ender;

        Preview(VortexPvP plugin, Player viewer, String name, Source src, boolean ender) {
            super(plugin, viewer);
            this.name = name;
            this.src = src;
            this.ender = ender;
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>" + Text.esc(this.name) + " - " + (this.ender ? "ender chest" : "inventory") + " (" + Progress.nice(this.src.group()) + ")");
        }

        @Override
        protected int rows() {
            return 6;
        }

        @Override
        protected void draw() {
            ItemStack[] items = this.ender ? this.src.ender() : this.src.contents();
            for (int i = 0; i < items.length && i < 45; i++) {
                if (items[i] != null && !items[i].isEmpty()) {
                    this.set(i, items[i].clone());
                }
            }

            this.set(49, Items.of(Material.PAPER).name("<yellow>" + Text.esc(this.src.label()))
                .lore("<gray>Inventory: <white>" + this.src.inv() + " items", "<gray>Ender chest: <light_purple>" + this.src.enderCount() + " items", "",
                    "<gray>Restore with <white>/smpadmin restore " + this.name + " # enderchest|all")
                .build());
            this.set(53, Items.of(this.ender ? Material.CHEST : Material.ENDER_CHEST).name(this.ender ? "<aqua>Show the inventory »" : "<light_purple>Show the ender chest »").build(),
                c -> new Preview(this.plugin, this.viewer, this.name, this.src, !this.ender).open());
        }
    }

    /** Group names for messages: SURVIVAL -> Survival. */
    static final class Progress {
        private Progress() {
        }

        static String nice(String g) {
            String s = g.toLowerCase(Locale.ROOT).replace('_', ' ');
            return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }
}
