package net.practicepvp.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.smp.ModeManager;
import net.practicepvp.smp.SmpRestore;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /pvpadmin → SMP: everything about the SMP in one place - on/off, its settings, the worlds, bringing old SMP data
 * back from a Data Vault snapshot or old plugin files, and every player's saved inventories and backups.
 */
public final class SmpDialogs {
    private final PracticePlugin plugin;

    public SmpDialogs(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    private LobbyDialogs ui() {
        return this.plugin.dialogs().lobby();
    }

    private static LobbyDialogs.Tile tile(Material icon, String name, String line, Runnable click, String... hover) {
        List<Component> h = new ArrayList<>();
        h.add(Text.mm(name));
        for (String s : hover) {
            h.add(Text.mm("<gray>" + s));
        }

        h.add(Component.empty());
        h.add(Text.mm("<yellow>▶ Click"));
        return new LobbyDialogs.Tile(new ItemStack(icon), Text.mm(name), Text.mm(line), h, click);
    }

    private ActionButton back(Player p, Runnable to) {
        return this.ui().btn(p, "<gray>« Back", null, to);
    }

    // ------------------------------------------------------------------ main page

    public void open(Player p) {
        ModeManager modes = this.plugin.modes();
        net.practicepvp.survival.VortexSMP smp = this.plugin.survival();
        World world = modes == null ? null : modes.smpWorld();
        int inSmp = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            inSmp += modes != null && modes.smpWorld(o.getWorld()) ? 1 : 0;
        }

        boolean on = Cfg.bool(Opt.SMP_SAME_SERVER);
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        tiles.add(tile(on ? Material.LIME_DYE : Material.GRAY_DYE, on ? "<green><b>SMP is ON" : "<red><b>SMP is OFF", "<gray>Click to turn it " + (on ? "off" : "on"), () -> {
            Cfg.set(Opt.SMP_SAME_SERVER, !on);
            p.sendMessage(Text.mm("<gray>SMP is now " + (!on ? "<green>on" : "<red>off") + "<gray>. <yellow>Restart the server</yellow> so the SMP worlds load / unload."));
            this.open(p);
        }, "The SMP worlds of the World Manager (SURVIVAL, ECO...)", "run on this server with their own inventories."));
        tiles.add(tile(Material.COMPARATOR, "<gold><b>SMP Settings", "<gray>Homes, tpa, rtp, warps, combat...", () -> this.plugin.dialogs().admin().settings(p, Opt.Section.SMP),
            "Homes per player, /rtp distance and cooldown,", "teleport warmup, combat tag, market, trade,", "drills, backups..."));
        tiles.add(tile(Material.GRASS_BLOCK, "<gold><b>SMP Worlds", "<gray>" + (world == null ? "<red>none set" : world.getName() + " • " + inSmp + " playing"),
            () -> this.plugin.dialogs().admin().worlds(p), "Set a world to SURVIVAL (or ECO) in the", "World Manager: it becomes an SMP world."));
        tiles.add(tile(Material.ENDER_CHEST, "<gold><b>Bring Back SMP Data", "<gray>From a backup or the old plugin files", () -> this.sources(p),
            "Homes, warps, inventories, ender chests and", "last spots from a Data Vault snapshot (made", "before every update), the old plugin files",
            "or the old VortexSMP folder. Nothing current", "is lost: it goes into backups first."));
        tiles.add(tile(Material.CHEST, "<gold><b>Player Inventories", "<gray>Every player's saved SMP items", () -> this.players(p, 0),
            "See and restore any player's SMP / ECO", "inventory and ender chest backups."));
        if (smp != null && smp.essentials() != null) {
            tiles.add(tile(Material.RED_BED, "<gold><b>Homes & Warps", "<gray>" + smp.essentials().homeCount() + " homes • " + smp.essentials().warpCount() + " warps", () -> {
                p.closeDialog();
                p.performCommand("smpadmin");
            }, "The SMP control panel: players, homes,", "logout spots, inventories."));
        }

        List<Component> head = new ArrayList<>();
        head.add(Text.mm("<gray>The SMP is part of this plugin: the selector's SMP button and /smp take players to the SMP world, each world keeps its own inventory and ender chest, and they are backed up on every switch, logout and every " + Cfg.i(Opt.SMP_BACKUP_MINUTES) + " minutes."));
        if (smp == null) {
            head.add(Text.mm("<red>The SMP part didn't start - see the console."));
        }

        this.ui().screen(p, LobbyDialogs.title("SMP", world == null ? "no SMP world yet" : world.getName()), tiles, head, null,
            List.of(this.ui().btn(p, "<gray>« Admin Panel", null, () -> this.plugin.dialogs().admin().main(p))), 2, this.ui().close(p));
    }

    // ------------------------------------------------------------------ bring back

    public void sources(Player p) {
        SmpRestore r = this.restore();
        if (r == null) {
            p.sendMessage(Text.mm("<red>The SMP part didn't start - see the console."));
            return;
        }

        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        List<SmpRestore.Source> src = r.sources();
        for (int i = 0; i < Math.min(40, src.size()); i++) {
            SmpRestore.Source s = src.get(i);
            Material icon = s.id().startsWith("vault:") ? (s.id().contains("pre-update") ? Material.ENDER_CHEST : Material.CHEST)
                : (s.id().equals("own") ? Material.BOOKSHELF : (s.id().startsWith("vsmp") || s.id().equals("vortexsmp") ? Material.GRASS_BLOCK : Material.BARREL));
            String when = s.time() > 0L ? new SimpleDateFormat("dd MMM HH:mm").format(new Date(s.time())) : "";
            tiles.add(tile(icon, "<white><b>" + Text.esc(s.label()), "<gray>" + Text.esc(s.detail()) + (when.isEmpty() ? "" : " <dark_gray>• <gray>" + when),
                () -> this.preview(p, s), "Click to see what it has."));
        }

        this.ui().screen(p, LobbyDialogs.title("Bring Back SMP Data", "pick a backup"), tiles,
            List.of(Text.mm("<gray>Pick where the old SMP data is. <white>Data Vault</white> snapshots are made before every update (the ones marked <white>before an update</white> are from just before it), every 6 hours and at shutdown. Put an old plugin folder or zip in <white>plugins/"
                + Text.esc(this.plugin.getDataFolder().getName()) + "/smp/import/</white> to see it here too.")),
            Text.mm("<gray>Nothing found."), List.of(this.back(p, () -> this.open(p))), 1, this.ui().close(p));
    }

    private SmpRestore restore() {
        return this.plugin.survival() == null ? null : this.plugin.survival().restore();
    }

    private void preview(Player p, SmpRestore.Source s) {
        SmpRestore r = this.restore();
        p.closeDialog();
        p.sendActionBar(Text.mm("<gray>Reading " + Text.esc(s.label()) + "..."));
        CompletableFuture.supplyAsync(() -> {
            try {
                return r.preview(s);
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }).whenComplete((pv, err) -> Tasks.ensureSync(() -> {
            if (!p.isOnline()) {
                return;
            }

            if (err != null) {
                p.sendMessage(Text.mm("<red>Couldn't read it: " + Text.esc(String.valueOf(err.getCause() == null ? err.getMessage() : err.getCause().getMessage()))));
                return;
            }

            List<LobbyDialogs.Tile> tiles = new ArrayList<>();
            tiles.add(tile(Material.LIME_CONCRETE, "<green><b>Restore everything as it was", "<gray>Inventories, ender chests, XP, homes, warps",
                () -> this.apply(p, s, EnumSet.allOf(SmpRestore.Part.class), true),
                "Every player gets the SMP inventory, ender", "chest and XP from this backup - online players", "right away, the others when they join.",
                "Old homes and warps come back. What players", "have now is kept in their backups."));
            tiles.add(tile(Material.ENDER_CHEST, "<white><b>Inventories & ender chests only", "<gray>As they were in this backup",
                () -> this.apply(p, s, EnumSet.of(SmpRestore.Part.INVENTORIES), true)));
            tiles.add(tile(Material.RED_BED, "<white><b>Homes & warps only", "<gray>As they were in this backup", () -> this.apply(p, s, EnumSet.of(SmpRestore.Part.HOMES), true)));
            tiles.add(tile(Material.LIGHT_GRAY_CONCRETE, "<gray><b>Only add what's missing", "<gray>Never changes what players have",
                () -> this.apply(p, s, EnumSet.allOf(SmpRestore.Part.class), false),
                "Only players with nothing in the SMP get the", "old inventory; the others find it in their", "backups (SMP → Player Inventories)."));
            List<Component> head = new ArrayList<>();
            head.add(Text.mm("<white><b>" + Text.esc(s.label())));
            head.add(Text.mm("<gray>Homes: <white>" + pv.homes + "</white> (<green>" + pv.homesNew + " not here</green>) <dark_gray>• <gray>Warps: <white>" + pv.warps + "</white> (<green>" + pv.warpsNew + " not here</green>)"));
            head.add(Text.mm("<gray>Players' SMP inventories: <white>" + pv.inventories + "</white> (<green>" + pv.inventoriesNew + " different from what they have now</green>) <dark_gray>• <gray>Players carrying items: <white>" + pv.carried));
            head.add(Text.mm("<gray>Old backups: <white>" + pv.backups + " <dark_gray>• <gray>Last spots: <white>" + pv.spots));
            head.add(Text.mm("<gray>A full Data Vault snapshot is taken before anything changes."));
            this.ui().screen(p, LobbyDialogs.title("Bring Back", s.label()), tiles, head, null, List.of(this.back(p, () -> this.sources(p))), 2, this.ui().close(p));
        }));
    }

    private void apply(Player p, SmpRestore.Source s, Set<SmpRestore.Part> parts, boolean replace) {
        this.plugin.dialogs().confirm(p, Text.mm("<gold><b>Bring back from " + Text.esc(s.label()) + "?"), List.of(
            Text.mm("<gray>" + (replace ? "<gold>Replace:</gold> the old data goes over the current one; what players have now goes into their backups."
                : "Only what's missing: nothing players have now is changed.")),
            Text.mm("<gray>Parts: <white>" + parts.stream().map(x -> x.label).reduce((a, b) -> a + ", " + b).orElse("")),
            Text.mm("<gray>A full Data Vault snapshot is taken first.")
        ), () -> {
            p.sendMessage(Text.mm("<gray>Taking a Data Vault snapshot first..."));
            this.plugin.vaultSnapshot("before-smp-restore", (name, err) -> {
                try {
                    SmpRestore.Preview pv = this.restore().run(s, parts, replace, false);
                    p.sendMessage(Text.mm("<green><b>Done.</b> <gray>From <white>" + Text.esc(s.label()) + "</white>:"));
                    p.sendMessage(Text.mm("<gray>• <white>" + Text.esc(pv.result())));
                    p.sendMessage(Text.mm("<gray>• Homes: <white>" + pv.homes + "</white> in the backup (" + pv.homesNew + " were missing) <dark_gray>• <gray>warps: <white>" + pv.warps + " <dark_gray>• <gray>old backups copied: <white>" + pv.backups));
                    p.sendMessage(Text.mm("<gray>What players had before is in <white>SMP → Player Inventories</white>."));
                } catch (Exception e) {
                    p.sendMessage(Text.mm("<red>Failed: " + Text.esc(String.valueOf(e.getMessage())) + " <gray>(nothing else changed; see the console)"));
                    this.plugin.getLogger().log(java.util.logging.Level.WARNING, "SMP restore failed", e);
                }
            });
        });
    }

    // ------------------------------------------------------------------ players

    public void players(Player p, int page) {
        ModeManager modes = this.plugin.modes();
        Set<UUID> ids = new LinkedHashSet<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            ids.add(o.getUniqueId());
        }

        for (File dir : List.of(modes.backupDir(), modes.groupDir())) {
            File[] fs = dir.listFiles(File::isDirectory);
            if (fs != null) {
                java.util.Arrays.sort(fs, java.util.Comparator.comparingLong(File::lastModified).reversed());
                for (File f : fs) {
                    try {
                        ids.add(UUID.fromString(f.getName()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }

        List<UUID> all = new ArrayList<>(ids);
        int per = 40;
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        for (int i = page * per; i < Math.min(all.size(), (page + 1) * per); i++) {
            UUID u = all.get(i);
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            String name = op.getName() == null ? u.toString().substring(0, 8) : op.getName();
            int n = modes.backups(u).size();
            tiles.add(tile(op.isOnline() ? Material.PLAYER_HEAD : Material.SKELETON_SKULL, (op.isOnline() ? "<green><b>" : "<white><b>") + Text.esc(name),
                "<gray>" + n + " backups", () -> this.player(p, u, name)));
        }

        List<ActionButton> b = new ArrayList<>();
        if (page > 0) {
            b.add(this.ui().btn(p, "<white>« Page " + page, null, () -> this.players(p, page - 1)));
        }

        if ((page + 1) * per < all.size()) {
            b.add(this.ui().btn(p, "<white>Page " + (page + 2) + " »", null, () -> this.players(p, page + 1)));
        }

        b.add(this.back(p, () -> this.open(p)));
        this.ui().screen(p, LobbyDialogs.title("Player Inventories", all.size() + " players"), tiles,
            List.of(Text.mm("<gray>Pick a player to see their saved SMP inventories and backups.")), Text.mm("<gray>No saved inventories yet."), b, 2, this.ui().close(p));
    }

    public void player(Player p, UUID u, String name) {
        ModeManager modes = this.plugin.modes();
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        SimpleDateFormat df = new SimpleDateFormat("dd MMM HH:mm");
        List<File> list = modes.backups(u);
        for (int i = 0; i < Math.min(36, list.size()); i++) {
            File f = list.get(i);
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            int[] c = counts(y);
            String group = y.getString("group", "?");
            Material icon = c[0] + c[1] == 0 ? Material.GLASS_BOTTLE : (group.equalsIgnoreCase("ECO") ? Material.EMERALD : Material.CHEST);
            tiles.add(tile(icon, "<white><b>" + Text.esc(group) + " <gray>" + df.format(new Date(f.lastModified())),
                "<gray>" + c[0] + " stacks • " + c[1] + " in ender chest • lvl " + y.getInt("level"),
                () -> this.plugin.dialogs().confirm(p, Text.mm("<gold><b>Give " + Text.esc(name) + " this " + Text.esc(group) + " inventory?"), List.of(
                    Text.mm("<gray>" + c[0] + " stacks, " + c[1] + " in the ender chest, level " + y.getInt("level") + "."),
                    Text.mm("<gray>What they have now in " + Text.esc(group) + " goes into a new backup first.")
                ), () -> {
                    String res = modes.restoreBackup(u, f);
                    p.sendMessage(Text.mm(res.equals("now") ? "<green>" + Text.esc(name) + " has it now (what they had is in a backup)." : res.equals("pending") ? "<green>Saved - " + Text.esc(name) + " gets it the next time they are in that world." : "<red>" + Text.esc(res)));
                    this.player(p, u, name);
                }),
                Text.esc(f.getName())));
        }

        this.ui().screen(p, LobbyDialogs.title(name, list.size() + " backups"), tiles,
            List.of(Text.mm("<gray>Newest first. Click one to give it back (online: right away; offline: when they enter that world).")),
            Text.mm("<gray>No backups for this player."), List.of(this.back(p, () -> this.players(p, 0))), 2, this.ui().close(p));
    }

    private static int[] counts(YamlConfiguration y) {
        int[] out = new int[2];
        String[] keys = {"contents", "enderchest"};
        for (int k = 0; k < 2; k++) {
            try {
                for (ItemStack it : ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(y.getString(keys[k], "")))) {
                    out[k] += it != null && !it.isEmpty() ? 1 : 0;
                }
            } catch (RuntimeException ignored) {
            }
        }

        return out;
    }
}
