package net.vortexrealm.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.arena.Arena;
import net.vortexrealm.arena.ArenaEditor;
import net.vortexrealm.arena.ArenaTheme;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Msg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.data.ConfigBackup;
import net.vortexrealm.data.DataVault;
import net.vortexrealm.data.PvPProfile;
import net.vortexrealm.data.RankBackup;
import net.vortexrealm.ffa.FfaArena;
import net.vortexrealm.fx.RankDrop;
import net.vortexrealm.fx.RankReveal;
import net.vortexrealm.fx.RankScreen;
import net.vortexrealm.fx.TierLetters;
import net.vortexrealm.gui.admin.AdminMenu;
import net.vortexrealm.gui.admin.ArenaEditMenu;
import net.vortexrealm.gui.admin.FfaListMenu;
import net.vortexrealm.gui.admin.KitListMenu;
import net.vortexrealm.gui.admin.PlayerAdminMenu;
import net.vortexrealm.gui.admin.StaffMenu;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.kit.KitCategory;
import net.vortexrealm.kit.KitManager;
import net.vortexrealm.match.Match;
import net.vortexrealm.rank.Tier;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Session;
import net.vortexrealm.session.Tp;
import net.vortexrealm.staff.StaffManager;
import net.vortexrealm.util.Fmt;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AdminCommands implements CommandExecutor, TabCompleter {
    private final VortexRealm plugin;

    public AdminCommands(VortexRealm plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("vortex.admin")) {
            Msg.error(sender, "general.no-permission");
            return true;
        } else {
            if (cmd.getName().equalsIgnoreCase("kits")) {
                this.kits(sender, args);
            } else if (args.length >= 1 && args[0].equalsIgnoreCase("import")) {
                net.vortexrealm.data.OldDataImport.command(this.plugin, sender, args);
            } else {
                this.admin(sender, args);
            }

            return true;
        }
    }

    private void staffCommand(CommandSender sender, Player p, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Text.mm("<red>Only operators can choose PvP staff."));
        } else if (args.length >= 3) {
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[2]);
            if (target == null) {
                sender.sendMessage(Text.mm("<red>Unknown player: " + Text.esc(args[2])));
            } else if (args[1].equalsIgnoreCase("remove")) {
                sender.sendMessage(Text.mm(this.plugin.staff().remove(target.getUniqueId()) ? "<green>Removed from staff." : "<red>That player is not staff."));
            } else {
                StaffManager.Role role = StaffManager.Role.HELPER;
                if (args.length >= 4) {
                    try {
                        role = StaffManager.Role.valueOf(args[3].toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException var7) {
                        sender.sendMessage(Text.mm("<red>Role must be helper, mod or admin."));
                        return;
                    }
                }

                this.plugin.staff().set(target, role);
                sender.sendMessage(Text.mm("<green>" + Text.esc(args[2]) + " is now " + role.badge()));
            }
        } else {
            if (p != null && args.length == 1) {
                new StaffMenu(this.plugin, p).open();
            } else {
                sender.sendMessage(Text.mm("<red>Usage: /pvpadmin staff add|remove \\<player> [helper|mod|admin]"));
            }
        }
    }

    private void admin(CommandSender sender, String[] args) {
        Player p = sender instanceof Player pl ? pl : null;
        if (args.length == 0) {
            if (p == null) {
                Msg.plain(sender, "usage.pvpadmin");
            } else {
                AdminMenu.openBest(this.plugin, p);
            }
        } else {
            String var20 = args[0].toLowerCase(Locale.ROOT);
            switch (var20) {
                case "staff": {
                        this.staffCommand(sender, p, args);
                        break;
                }
                case "rebuild": {
                        if (args.length < 2) {
                            sender.sendMessage(Text.mm("<red>Usage: /pvpadmin rebuild \\<arena|all|outdated>"));
                        } else if (args[1].equalsIgnoreCase("outdated")) {
                            int n = this.plugin.arenas().outdated().size();
                            if (n == 0) {
                                sender.sendMessage(Text.mm("<green>Every generated arena already uses the current arena settings."));
                            } else {
                                sender.sendMessage(
                                    Text.mm("<yellow>Updating <white>" + n + "</white> arena(s) to the current settings (same layouts, one at a time)...")
                                );
                                this.plugin
                                    .arenas()
                                    .updateOutdated()
                                    .whenComplete(
                                        (done, err) -> Tasks.ensureSync(
                                            () -> sender.sendMessage(Text.mm(err != null ? "<red>Update failed." : "<green>Updated " + done + " arena(s)."))
                                        )
                                    );
                            }
                        } else if (args[1].equalsIgnoreCase("all")) {
                            sender.sendMessage(Text.mm("<yellow>Rebuilding generated arenas..."));
                            this.plugin
                                .arenas()
                                .rebuildAll()
                                .whenComplete(
                                    (n, err) -> Tasks.ensureSync(
                                        () -> sender.sendMessage(Text.mm(err != null ? "<red>Rebuild failed." : "<green>Rebuilt " + n + " arena(s)."))
                                    )
                                );
                        } else {
                            Arena a = this.plugin.arenas().get(args[1]);
                            FfaArena f = a == null ? this.plugin.ffa().get(args[1]) : null;
                            if (f != null) {
                                sender.sendMessage(Text.mm("<yellow>Rebuilding FFA " + f.id + "..."));
                                this.plugin
                                    .ffa()
                                    .rebuild(f)
                                    .whenComplete(
                                        (v, err) -> Tasks.ensureSync(
                                            () -> sender.sendMessage(
                                                Text.mm(
                                                    err != null
                                                        ? "<red>Rebuild failed: " + Text.esc(String.valueOf(err.getMessage()))
                                                        : "<green>" + f.id + " rebuilt."
                                                )
                                            )
                                        )
                                    );
                            } else if (a == null) {
                                Msg.error(sender, "admin.unknown-arena", "arena", args[1]);
                            } else {
                                sender.sendMessage(Text.mm("<yellow>Rebuilding " + a.id + "..."));
                                this.plugin
                                    .arenas()
                                    .rebuild(a)
                                    .whenComplete(
                                        (v, err) -> Tasks.ensureSync(
                                            () -> sender.sendMessage(
                                                Text.mm(
                                                    err != null
                                                        ? "<red>Rebuild failed: " + Text.esc(String.valueOf(err.getMessage()))
                                                        : "<green>" + a.id + " rebuilt."
                                                )
                                            )
                                        )
                                    );
                            }
                        }
                        break;
                }
                case "reload": {
                        this.plugin.reloadFiles();
                        Msg.send(sender, "admin.reloaded");
                        break;
                }
                case "save": {
                        this.plugin.saveAll();
                        Msg.send(sender, "admin.saved");
                        break;
                }
                case "setlobby": {
                        if (p == null) {
                            Msg.send(sender, "general.players-only");
                        } else if (!this.plugin.worlds().managed(p.getWorld())) {
                            Msg.error(p, "admin.lobby-excluded-world");
                        } else {
                            this.plugin.lobby().setLobby(p.getLocation());
                            Msg.send(p, "admin.lobby-set");
                        }
                        break;
                }
                case "wand": {
                        if (p != null) {
                            AdminMenu.giveWand(p);
                        }
                        break;
                }
                case "update": {
                        net.vortexrealm.update.AutoUpdater u = this.plugin.updater();
                        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
                        if (u == null) {
                            sender.sendMessage(Text.mm("<red>The updater didn't start - see the console."));
                        } else if (what.equals("check")) {
                            sender.sendMessage(Text.mm("<gray>Checking for a new version..."));
                            u.check(sender);
                        } else if (what.equals("now")) {
                            u.restartNow(sender);
                        } else if (what.equals("off") || what.equals("on")) {
                            Cfg.set(Opt.UPDATES, what.equals("on"));
                            if (what.equals("on")) {
                                u.start();
                            } else {
                                u.stop();
                            }

                            sender.sendMessage(Text.mm("<gray>Auto-update is now " + (what.equals("on") ? "<green>on" : "<red>off") + "<gray>."));
                        } else {
                            for (String line : u.status()) {
                                sender.sendMessage(Text.mm(line));
                            }

                            sender.sendMessage(Text.mm("<gray>/pvpadmin update check <dark_gray>| <gray>now <dark_gray>| <gray>on <dark_gray>| <gray>off"));
                        }
                        break;
                }
                case "winter": {
                        net.vortexrealm.lobby.Season se = this.plugin.season();
                        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
                        if (se == null) {
                            sender.sendMessage(Text.mm("<red>The winter lobby didn't start - see the console."));
                        } else if (what.equals("on") || what.equals("off") || what.equals("auto")) {
                            Cfg.set(Opt.LOBBY_SEASON, what.equals("on") ? "WINTER" : what.toUpperCase(Locale.ROOT));
                            if (se.wanted() && !se.applied()) {
                                se.apply(sender);
                            } else if (!se.wanted() && se.applied()) {
                                se.revert(sender);
                            } else {
                                sender.sendMessage(Text.mm("<gray>Winter lobby: " + Text.esc(se.status())));
                            }
                        } else {
                            sender.sendMessage(Text.mm("<aqua>❄ <gray>Winter lobby: <white>" + Text.esc(se.status())));
                            sender.sendMessage(Text.mm("<gray>/pvpadmin winter on <dark_gray>| <gray>off <dark_gray>| <gray>auto <dark_gray>(1 Dec - 15 Jan)"));
                        }
                        break;
                }
                case "hub": {
                        if (p == null) {
                            Msg.send(sender, "general.players-only");
                        } else if (args.length > 1 && args[1].equalsIgnoreCase("upgrade")) {
                            this.plugin.modes().upgradeHub(p);
                        } else if (args.length > 2 && args[1].equalsIgnoreCase("build")) {
                            try {
                                net.vortexrealm.build.SpawnBuilder.Style st = net.vortexrealm.build.SpawnBuilder.Style.valueOf(args[2].toUpperCase(Locale.ROOT));
                                int rad = args.length > 3 ? Integer.parseInt(args[3]) : st.radius;
                                this.plugin.modes().buildSpawn(p, st, rad);
                            } catch (IllegalArgumentException e) {
                                sender.sendMessage(Text.mm("<red>Unknown style or size. <gray>Styles: " + java.util.Arrays.toString(net.vortexrealm.build.SpawnBuilder.Style.values())));
                            }
                        } else {
                            sender.sendMessage(Text.mm("<gray>/pvpadmin hub upgrade <dark_gray>- <gray>rebuild the lobby in place with the newest design"));
                            sender.sendMessage(Text.mm("<gray>/pvpadmin hub build <style> [radius] <dark_gray>- <gray>build a lobby where you stand"));
                        }
                        break;
                }
                case "build": {
                        if (p != null) {
                            Session s = this.plugin.sessions().get(p);
                            s.buildMode = !s.buildMode;
                            Msg.send(p, s.buildMode ? "admin.build-on" : "admin.build-off");
                            if (this.plugin.sessions().state(p) == PlayerState.LOBBY) {
                                this.plugin.lobby().sendToLobby(p);
                            }
                        }
                        break;
                }
                case "maintenance": {
                        Cfg.set(Opt.MAINTENANCE, !Cfg.bool(Opt.MAINTENANCE));
                        Msg.send(sender, "admin.maintenance", "state", Cfg.bool(Opt.MAINTENANCE) ? "ON" : "OFF");
                        break;
                }
                case "generate": {
                        this.generate(sender, args);
                        break;
                }
                case "arena": {
                        if (p == null || args.length < 2) {
                            Msg.plain(sender, "usage.pvpadmin");
                            return;
                        }

                        ArenaEditor editor = this.plugin.arenaEditor();
                        String sub1 = args[1].toLowerCase(Locale.ROOT);
                        if ((sub1.equals("save") || sub1.equals("cancel")) && this.plugin.arenas().get(sub1) == null && this.plugin.ffa().get(sub1) == null) {
                            Object cur = editor == null ? null : editor.editingOf(p);
                            if (cur == null) {
                                p.sendMessage(Text.mm("<red>You are not editing an arena. Use /pvpadmin arena \\<id> edit"));
                            } else if (cur instanceof Arena ea) {
                                if (sub1.equals("save")) {
                                    editor.save(p, ea);
                                } else {
                                    editor.cancel(p, ea);
                                }
                            } else if (cur instanceof FfaArena ef) {
                                if (sub1.equals("save")) {
                                    editor.save(p, ef);
                                } else {
                                    editor.cancel(p, ef);
                                }
                            }

                            return;
                        }

                        Arena a = this.plugin.arenas().get(args[1]);
                        FfaArena fa = a == null ? this.plugin.ffa().get(args[1]) : null;
                        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
                        if (a == null && fa == null) {
                            Msg.error(p, "admin.unknown-arena", "arena", args[1]);
                        } else if (editor != null && (action.equals("edit") || action.equals("save") || action.equals("cancel"))) {
                            if (a != null) {
                                switch (action) {
                                    case "edit":
                                        editor.start(p, a);
                                        return;
                                    case "save":
                                        editor.save(p, a);
                                        return;
                                    default:
                                        editor.cancel(p, a);
                                }
                            } else {
                                switch (action) {
                                    case "edit":
                                        editor.start(p, fa);
                                        return;
                                    case "save":
                                        editor.save(p, fa);
                                        return;
                                    default:
                                        editor.cancel(p, fa);
                                }
                            }
                        } else if (a != null) {
                            new ArenaEditMenu(this.plugin, p, a).open();
                        } else {
                            new FfaListMenu(this.plugin, p).open();
                        }
                        break;
                }
                case "tp": {
                        if (p == null || args.length < 2) {
                            Msg.plain(sender, "usage.pvpadmin");
                            return;
                        }

                        Arena a = this.plugin.arenas().get(args[1]);
                        if (a != null && !a.instances.isEmpty() && a.instances.get(0).spawn1() != null) {
                            Tp.to(p, a.instances.get(0).spawn1());
                        } else {
                            Msg.error(p, "admin.unknown-arena", "arena", args[1]);
                        }
                        break;
                }
                case "player":
                case "ranks": {
                        if (p == null) {
                            return;
                        }

                        if (args.length >= 2) {
                            PlayerAdminMenu.open(this.plugin, p, args[1]);
                        } else {
                            PlayerAdminMenu.prompt(this.plugin, p);
                        }
                        break;
                }
                case "elo": {
                        if (args.length < 4) {
                            Msg.plain(sender, "usage.pvpadmin");
                            return;
                        }

                        Kit k = this.plugin.kits().get(args[2]);

                        int value;
                        try {
                            value = Integer.parseInt(args[3]);
                        } catch (NumberFormatException var19) {
                            Msg.error(sender, "admin.bad-number");
                            return;
                        }

                        if (k == null) {
                            Msg.error(sender, "admin.online-and-kit");
                            return;
                        }

                        this.editRank(
                            sender,
                            args[1],
                            prof -> this.plugin.ranks().adminSet(prof, k, value),
                            name -> Msg.send(sender, "admin.elo-set", "player", name, "kit", k.label(), "elo", value)
                        );
                        break;
                }
                case "tier": {
                        if (args.length < 4) {
                            Msg.plain(sender, "usage.pvpadmin");
                            return;
                        }

                        Kit k = this.plugin.kits().get(args[2]);

                        Tier tier;
                        try {
                            tier = Tier.valueOf(args[3].toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException var18) {
                            Msg.error(sender, "admin.bad-tier");
                            return;
                        }

                        if (k == null) {
                            Msg.error(sender, "admin.online-and-kit");
                            return;
                        }

                        this.editRank(
                            sender,
                            args[1],
                            prof -> this.plugin.ranks().adminSetTier(prof, k, tier),
                            name -> Msg.send(sender, "admin.tier-set", "player", name, "kit", k.label(), "tier", tier.label)
                        );
                        break;
                }
                case "backup": {
                        RankBackup rbx = this.plugin.rankBackup();
                        if (rbx == null) {
                            return;
                        }

                        note(sender, "<gray>Backing up every player's ranks...");
                        rbx.backup().whenComplete((r, err) -> Tasks.ensureSync(() -> {
                            if (err != null) {
                                note(sender, "<red>Backup failed: " + Text.esc(String.valueOf(err.getMessage())));
                            } else {
                                note(sender, "<green>Saved <white>" + r[1] + "</white> players to <white>backups/" + r[0]);
                            }
                        }));
                        break;
                }
                case "vault": {
                        DataVault v = this.plugin.vault();
                        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
                        switch (sub) {
                            case "save": {
                                    note(sender, "<gray>Saving a full snapshot of the plugin data...");
                                    this.plugin
                                        .vaultSnapshot(
                                            "manual",
                                            (name, err) -> note(
                                                sender,
                                                err != null
                                                    ? "<red>Snapshot failed: " + Text.esc(String.valueOf(err.getMessage()))
                                                    : "<green>Saved <white>" + name + "</white> in <white>plugins/VortexRealm-Vault"
                                            )
                                        );
                                    return;
                            }
                            case "restore": {
                                    if (args.length < 3) {
                                        note(sender, "<gray>Usage: <white>/pvpadmin vault restore <file|latest>");
                                        return;
                                    }

                                    List<String> fs = v.files();
                                    String file = args[2].equalsIgnoreCase("latest") ? (fs.isEmpty() ? "" : fs.get(0)) : args[2];

                                    try {
                                        if (v.scheduleRestore(file)) {
                                            note(
                                                sender,
                                                "<green>"
                                                    + Text.esc(file)
                                                    + " <gray>will be put back at the next start. <yellow>Restart the server</yellow> to apply it (the current data is snapshotted first)."
                                            );
                                        } else {
                                            note(sender, "<red>No snapshot called " + Text.esc(file));
                                        }

                                        return;
                                    } catch (IOException var17) {
                                        note(sender, "<red>Could not schedule it: " + Text.esc(String.valueOf(var17.getMessage())));
                                        return;
                                    }
                            }
                            default: {
                                    List<String> fs = v.files();
                                    note(
                                        sender,
                                        "<gold><b>Data vault</b> <gray>(plugins/VortexRealm-Vault, newest first) - settings, kits, arenas, worlds, NPCs, inventories, database"
                                    );
                                    if (fs.isEmpty()) {
                                        note(sender, "<gray>Nothing yet. <white>/pvpadmin vault save");
                                    }

                                    for (String f : fs.subList(0, Math.min(15, fs.size()))) {
                                        sender.sendMessage(
                                            Text.mm(
                                                " <dark_gray>» <click:suggest_command:'/pvpadmin vault restore "
                                                    + f
                                                    + "'><hover:show_text:'<yellow>Restore at next start'><white>"
                                                    + f
                                                    + "</hover></click>"
                                            )
                                        );
                                    }

                                    return;
                            }
                        }
                }
                case "backups": {
                        RankBackup rbx = this.plugin.rankBackup();
                        if (rbx == null) {
                            return;
                        }

                        List<String> fs = rbx.files();
                        if (fs.isEmpty()) {
                            note(sender, "<gray>No rank backups yet. Make one with <white>/pvpadmin backup");
                            return;
                        }

                        note(sender, "<gold><b>Rank backups</b> <gray>(newest first, click to restore)");

                        for (String f : fs) {
                            sender.sendMessage(
                                Text.mm(
                                    " <dark_gray>» <click:suggest_command:'/pvpadmin restore "
                                        + f
                                        + "'><hover:show_text:'<yellow>Restore this backup'><white>"
                                        + f
                                        + "</hover></click>"
                                )
                            );
                        }
                        break;
                }
                case "restore": {
                        RankBackup rb = this.plugin.rankBackup();
                        if (rb == null) {
                            return;
                        }

                        if (args.length < 2) {
                            note(sender, "<gray>Usage: <white>/pvpadmin restore <file|latest> <gray>(see /pvpadmin backups)");
                            return;
                        }

                        String file = args[1];
                        if (file.equalsIgnoreCase("latest")) {
                            List<String> fs = rb.files();
                            if (fs.isEmpty()) {
                                note(sender, "<red>There are no backups.");
                                return;
                            }

                            file = fs.get(0);
                        }

                        note(sender, "<gray>Restoring <white>" + Text.esc(file) + "<gray>...");
                        String chosen = file;
                        rb.restore(chosen).whenComplete((n, err) -> Tasks.ensureSync(() -> {
                            if (err != null) {
                                Throwable c = err.getCause() != null ? err.getCause() : err;
                                note(sender, "<red>Restore failed: " + Text.esc(String.valueOf(c.getMessage())));
                            } else {
                                note(sender, "<green>Restored the ranks of <white>" + n + "</white> players from <white>" + Text.esc(chosen));
                            }
                        }));
                        break;
                }
                case "end": {
                        if (args.length < 2) {
                            Msg.plain(sender, "usage.pvpadmin");
                            return;
                        }

                        try {
                            Match m = this.plugin.matches().byId(Integer.parseInt(args[1]));
                            if (m == null) {
                                Msg.error(sender, "admin.unknown-match");
                            } else {
                                m.end(null, Match.EndReason.DRAW);
                                Msg.send(sender, "admin.match-ended", "id", m.id);
                            }
                        } catch (NumberFormatException var16) {
                            Msg.error(sender, "admin.bad-number");
                        }
                        break;
                }
                case "forcelobby": {
                        Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                        if (t == null) {
                            Msg.error(sender, "general.player-offline", "player", args.length > 1 ? args[1] : "?");
                        } else {
                            this.plugin.forceLeave(t);
                            Msg.send(sender, "admin.sent-to-lobby", "player", t.getName());
                        }
                        break;
                }
                case "selftest": {
                        this.plugin.selfTest(sender);
                        break;
                }
                case "preview": {
                        net.vortexrealm.build.SpawnBuilder.Style st = args.length > 1 ? net.vortexrealm.build.SpawnBuilder.Style.parse(args[1]) : null;
                        if (p == null) {
                            sender.sendMessage("Players only.");
                        } else if (st == null) {
                            sender.sendMessage(Text.mm("<red>Usage: /pvpadmin preview \\<style> <gray>(royal_castle, practice_town, castle_market, hub...)"));
                        } else {
                            org.bukkit.World pw = p.getWorld();
                            net.vortexrealm.build.BuildPreview.preview(
                                this.plugin, p, "style:" + st.name() + ":" + st.radius, st.name(), out -> net.vortexrealm.build.SpawnBuilder.feed(st, pw, st.radius, out), st.big(), m -> {}
                            );
                        }
                        break;
                }
                case "arenas": {
                        if (args.length > 1 && args[1].equalsIgnoreCase("enableall")) {
                            for (String line : this.plugin.arenas().enableAll(false).lines(this.plugin.packProgress())) {
                                sender.sendMessage(Text.mm(line));
                            }
                        } else {
                            sender.sendMessage(Text.mm("<gray>Usage: /pvpadmin arenas enableall"));
                        }
                        break;
                }
                case "bothall": {
                        if (this.plugin.botHall() == null) {
                            sender.sendMessage(Text.mm("<red>Practice bots are not running."));
                        } else if (args.length > 1 && args[1].equalsIgnoreCase("remove")) {
                            sender.sendMessage(Text.mm("<gray>Removed " + this.plugin.botHall().remove() + " hall entities (the blocks stay)."));
                        } else if (p == null) {
                            sender.sendMessage("Players only.");
                        } else {
                            this.plugin.botHall().build(p);
                        }
                        break;
                }
                case "stoppreview": {
                        if (p != null) {
                            net.vortexrealm.build.BuildPreview.stop(p);
                        }
                        break;
                }
                case "tabdesign": {
                        if (this.plugin.tab() == null) {
                            sender.sendMessage(Text.mm("<red>The tab list is not running."));
                        } else if (args.length < 2) {
                            StringBuilder sb = new StringBuilder();
                            for (net.vortexrealm.social.TabDesigns.Design d : net.vortexrealm.social.TabDesigns.DESIGNS) {
                                sb.append(sb.length() == 0 ? "" : ", ").append(d.id().toLowerCase(Locale.ROOT));
                            }

                            sender.sendMessage(Text.mm("<gray>Tab design now: <white>" + this.plugin.tab().design() + "</white>. Usage: /pvpadmin tabdesign \\<" + sb + ">"));
                        } else {
                            net.vortexrealm.social.TabDesigns.Design d = null;
                            for (net.vortexrealm.social.TabDesigns.Design x : net.vortexrealm.social.TabDesigns.DESIGNS) {
                                if (x.id().equalsIgnoreCase(args[1]) || x.name().replace(" ", "").equalsIgnoreCase(args[1])) {
                                    d = x;
                                }
                            }

                            if (d == null) {
                                sender.sendMessage(Text.mm("<red>Unknown tab design. Try /pvpadmin tabdesign"));
                            } else {
                                this.plugin.tab().applyDesign(d.id());
                                sender.sendMessage(Text.mm("<green>The tab list now uses the <white>" + d.name() + "</white> design. <gray>(old file: tab-backup.yml)"));
                            }
                        }
                        break;
                }
                case "health": {
                        Map<String, String> f = this.plugin.failedParts();
                        note(
                            sender,
                            "<gold><b>Health</b> <gray>"
                                + Bukkit.getName()
                                + " "
                                + Bukkit.getMinecraftVersion()
                                + " (made for 1.21.11), TPS <white>"
                                + String.format(Locale.ROOT, "%.1f", Bukkit.getTPS()[0])
                        );
                        if (f.isEmpty()) {
                            note(sender, "<green>✔ Every part started fine.");
                        } else {
                            for (Entry<String, String> e : f.entrySet()) {
                                note(sender, "<red>✖ " + Text.esc(e.getKey()) + " <gray>- " + Text.esc(e.getValue()));
                            }

                            note(sender, "<gray>The full errors are in the console / logs/latest.log.");
                        }

                        note(
                            sender,
                            "<gray>Website "
                                + (
                                    this.plugin.web() != null && this.plugin.web().available()
                                        ? "<green>"
                                            + (
                                                this.plugin.web().onSamePort()
                                                    ? "on the Minecraft port" + (this.plugin.web().running() ? " + port " + this.plugin.web().port() : "")
                                                    : "on port " + this.plugin.web().port()
                                            )
                                        : "<red>off"
                                )
                                + " <dark_gray>• <gray>pack "
                                + (this.plugin.packs() != null && this.plugin.packs().ready() ? "<green>ready" : "<red>not ready")
                        );
                        break;
                }
                case "kitbackup":
                case "settingsbackup":
                case "fullbackup": {
                        ConfigBackup.Kind kind = backupKind(args[0]);

                        try {
                            String f = this.plugin.configBackup().backup(kind);
                            note(sender, "<green>Saved <white>" + f + "</white> <gray>(plugins/VortexRealm-Vault/exports)");
                        } catch (Exception var15) {
                            note(sender, "<red>Backup failed: " + Text.esc(String.valueOf(var15.getMessage())));
                        }
                        break;
                }
                case "kitrestore":
                case "settingsrestore":
                case "fullrestore": {
                        ConfigBackup.Kind kind = backupKind(args[0]);
                        List<String> fs = this.plugin.configBackup().files(kind);
                        if (args.length < 2) {
                            String sub = args[0].toLowerCase(Locale.ROOT);
                            note(sender, "<gray>Usage: <white>/pvpadmin " + sub + " <file|latest>");

                            for (int i = 0; i < Math.min(8, fs.size()); i++) {
                                sender.sendMessage(
                                    Text.mm(" <dark_gray>» <click:suggest_command:'/pvpadmin " + sub + " " + fs.get(i) + "'><white>" + fs.get(i) + "</click>")
                                );
                            }

                            return;
                        }

                        String file = args[1].equalsIgnoreCase("latest") ? (fs.isEmpty() ? "" : fs.get(0)) : args[1];

                        try {
                            int n = this.plugin.configBackup().restore(kind, file);
                            note(
                                sender,
                                "<green>Restored <white>" + n + "</white> file(s) from " + Text.esc(file) + " <gray>(the files before were backed up first)"
                            );
                        } catch (Exception var14) {
                            note(sender, "<red>Restore failed: " + Text.esc(String.valueOf(var14.getMessage())));
                        }
                        break;
                }
                case "rankfx": {
                        if (!(sender instanceof Player me)) {
                            return;
                        }

                        Tier to = Tier.HT3;
                        if (args.length > 1) {
                            try {
                                to = Tier.valueOf(args[1].toUpperCase(Locale.ROOT));
                            } catch (IllegalArgumentException var13) {
                                sender.sendMessage(Text.mm("<red>Unknown tier. <gray>LT5 ... HT1"));
                                return;
                            }
                        }

                        String style = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "drop";
                        Tier from = to.prev();
                        if (style.equals("drop")) {
                            RankDrop.play(this.plugin, me, from, to, null);
                        } else if (style.equals("screen")) {
                            if (!this.plugin.hasPack(me)) {
                                sender.sendMessage(Text.mm("<yellow>You don't have the server pack loaded - showing the in-world version."));
                                RankReveal.play(this.plugin, me, from, to, null);
                            } else {
                                RankScreen.play(this.plugin, me, from, to);
                            }
                        } else if (style.equals("blocks")) {
                            TierLetters.play(this.plugin, me, from, to, null);
                        } else {
                            RankReveal.play(this.plugin, me, from, to, null);
                        }
                        break;
                }
                default: {
                        Msg.plain(sender, "usage.pvpadmin");
                }
            }
        }
    }

    private static void note(CommandSender to, String mm) {
        to.sendMessage(Msg.prefix().append(Text.mm(mm)));
    }

    private void editRank(CommandSender sender, String name, Consumer<PvPProfile> change, Consumer<String> done) {
        Player t = Bukkit.getPlayerExact(name);
        if (t != null) {
            change.accept(this.plugin.profiles().get(t));
            this.plugin.boards().refreshNametag(t);
            done.accept(t.getName());
        } else {
            this.plugin.db().supply(c -> {
                UUID u = this.plugin.db().findUuid(c, name);
                return u == null ? null : this.plugin.db().loadProfile(c, u, name);
            }).whenComplete((prof, err) -> Tasks.ensureSync(() -> {
                if (prof == null) {
                    Msg.error(sender, "general.player-unknown", "player", name);
                } else {
                    change.accept(prof);
                    this.plugin.profiles().save(prof);
                    done.accept(prof.name + " (offline)");
                }
            }));
        }
    }

    private void generate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Msg.plain(sender, "usage.pvpadmin");
        } else {
            boolean ffa = args[1].equalsIgnoreCase("ffa");
            ArenaTheme theme = ArenaTheme.parse(args[2]);
            if (theme == ArenaTheme.CUSTOM) {
                Msg.error(sender, "admin.unknown-theme");
            } else {
                int size = ffa ? Cfg.i(Opt.FFA_ARENA_SIZE) : Cfg.i(Opt.DUEL_ARENA_SIZE);
                if (args.length > 3) {
                    try {
                        size = Math.max(32, Math.min(448, Integer.parseInt(args[3])));
                    } catch (NumberFormatException var13) {
                    }
                }

                String display = theme.display;
                long started = System.currentTimeMillis();
                if (ffa) {
                    String kit = args.length > 4 ? args[4] : (this.plugin.kits().ffaKits().isEmpty() ? "sword" : this.plugin.kits().ffaKits().get(0).id);
                    Kit k = this.plugin.kits().get(kit);
                    if (k == null) {
                        Msg.error(sender, "general.unknown-kit", "kit", kit);
                        return;
                    }

                    String id = this.uniqueId(k.id + "_ffa", true);
                    Msg.send(sender, "admin.generating", "arena", k.displayName + " FFA");
                    this.plugin
                        .ffa()
                        .generate(id, k.displayName + " FFA", theme, size, k.id)
                        .whenComplete(
                            (a, err) -> Tasks.ensureSync(
                                () -> Msg.send(
                                    sender,
                                    err == null ? "admin.generated" : "admin.generate-failed",
                                    "arena",
                                    k.displayName + " FFA",
                                    "seconds",
                                    (System.currentTimeMillis() - started) / 1000L
                                )
                            )
                        );
                } else {
                    int copies = Cfg.i(Opt.DEFAULT_COPIES);
                    if (args.length > 4) {
                        try {
                            copies = Math.max(0, Math.min(20, Integer.parseInt(args[4])));
                        } catch (NumberFormatException var12) {
                        }
                    }

                    String id = this.uniqueId(theme.name().toLowerCase(Locale.ROOT), false);
                    Msg.send(sender, "admin.generating", "arena", display);
                    this.plugin
                        .arenas()
                        .generate(id, display, theme, size, copies)
                        .whenComplete(
                            (a, err) -> Tasks.ensureSync(
                                () -> Msg.send(
                                    sender,
                                    err == null ? "admin.generated" : "admin.generate-failed",
                                    "arena",
                                    display,
                                    "seconds",
                                    (System.currentTimeMillis() - started) / 1000L
                                )
                            )
                        );
                }
            }
        }
    }

    private String uniqueId(String base, boolean ffa) {
        String id = base;
        int n = 2;

        while (ffa ? this.plugin.ffa().exists(id) : this.plugin.arenas().exists(id)) {
            id = base + "_" + n++;
        }

        return id;
    }

    private void kits(CommandSender sender, String[] args) {
        Player p = sender instanceof Player pl ? pl : null;
        if (args.length == 0) {
            if (p != null) {
                new KitListMenu(this.plugin, p).open();
            } else {
                Msg.plain(sender, "usage.kits");
            }
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("list")) {
                for (Kit k : this.plugin.kits().sorted()) {
                    sender.sendMessage(
                        Msg.get(
                            "admin.kit-list-line",
                            "kit",
                            k.label(),
                            "id",
                            k.id,
                            "tab",
                            k.category.title,
                            "flags",
                            (k.ranked ? "ranked " : "") + (k.unranked ? "unranked " : "") + (k.ffa ? "ffa " : "") + (k.enabled ? "" : "DISABLED")
                        )
                    );
                }
            } else if (args.length < 2) {
                Msg.plain(sender, "usage.kits");
            } else {
                String id = args[1].toLowerCase(Locale.ROOT);
                if (sub.equals("copy")) {
                    Kit src = this.plugin.kits().get(id);
                    String nid = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : null;
                    if (src == null) {
                        Msg.error(sender, "general.unknown-kit", "kit", id);
                    } else if (nid == null || !KitManager.ID.matcher(nid).matches()) {
                        sender.sendMessage(Text.mm("<red>Usage: /kits copy <from> <new-id> [name]"));
                    } else if (this.plugin.kits().get(nid) != null) {
                        Msg.error(sender, "admin.kit-exists", "kit", nid);
                    } else {
                        String display = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : Fmt.capitalize(nid);
                        Kit k = this.plugin.kits().copy(src, nid, display);
                        Msg.send(sender, "admin.kit-created", "kit", k.label());
                    }
                } else if (sub.equals("create")) {
                    if (p == null) {
                        Msg.send(sender, "general.players-only");
                    } else if (!KitManager.ID.matcher(id).matches()) {
                        Msg.error(p, "admin.bad-name");
                    } else if (this.plugin.kits().get(id) != null) {
                        Msg.error(p, "admin.kit-exists", "kit", id);
                    } else {
                        String display = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : Fmt.capitalize(id);
                        Kit k = this.plugin.kits().create(id, display, p);
                        Msg.send(p, "admin.kit-created", "kit", k.label());
                    }
                } else {
                    Kit k = this.plugin.kits().get(id);
                    if (k == null) {
                        Msg.error(sender, "general.unknown-kit", "kit", id);
                    } else {
                        switch (sub) {
                            case "add":
                            case "remove":
                                if (args.length < 3) {
                                    Msg.plain(sender, "usage.kits");
                                    return;
                                }

                                boolean add = sub.equals("add");
                                String what = args[2].toLowerCase(Locale.ROOT);
                                switch (what) {
                                    case "ranked":
                                        k.ranked = add;
                                        break;
                                    case "unranked":
                                        k.unranked = add;
                                        break;
                                    case "ffa":
                                        k.ffa = add;
                                        if (this.plugin.ffa().ensureArena(k) && p != null) {
                                            p.sendMessage(Text.mm("<green>Building an FFA arena for this kit..."));
                                        }
                                        break;
                                    case "skill":
                                    case "vanilla":
                                    case "other":
                                        if (add) {
                                            k.category = KitCategory.parse(what, KitCategory.OTHER);
                                        } else {
                                            k.category = KitCategory.OTHER;
                                        }
                                        break;
                                    case "duels":
                                    case "all":
                                        k.ranked = add;
                                        k.unranked = add;
                                        break;
                                    default:
                                        Msg.plain(sender, "usage.kits");
                                        return;
                                }

                                this.plugin.kits().save();
                                Msg.send(sender, "admin.kit-updated", "kit", k.label());
                                break;
                            case "edit":
                                if (p != null) {
                                    new KitListMenu.KitEditMenu(this.plugin, p, k).open();
                                }
                                break;
                            case "delete":
                                this.plugin.queues().purgeKit(k.id);
                                this.plugin.kits().delete(k.id);
                                Msg.send(sender, "admin.kit-deleted", "kit", k.displayName);
                                break;
                            case "setinv":
                                if (p == null) {
                                    return;
                                }

                                k.contents = KitManager.copyFrom(p.getInventory());
                                k.bumpVersion();
                                this.plugin.kits().save();
                                Msg.send(p, "admin.kit-saved", "kit", k.label());
                                break;
                            case "load":
                                if (p != null) {
                                    this.plugin.kits().apply(p, k, null, List.of());
                                }
                                break;
                            case "seticon":
                                if (p == null) {
                                    return;
                                }

                                ItemStack hand = p.getInventory().getItemInMainHand();
                                if (hand.getType().isAir()) {
                                    Msg.error(p, "admin.hold-item");
                                    return;
                                }

                                ItemStack icon = hand.clone();
                                icon.setAmount(1);
                                k.icon = icon;
                                this.plugin.kits().save();
                                Msg.send(p, "admin.kit-updated", "kit", k.label());
                                break;
                            default:
                                Msg.plain(sender, "usage.kits");
                        }
                    }
                }
            }
        }
    }

    private static ConfigBackup.Kind backupKind(String sub) {
        String s = sub.toLowerCase(Locale.ROOT);
        if (s.startsWith("kit")) {
            return ConfigBackup.Kind.KITS;
        } else {
            return s.startsWith("full") ? ConfigBackup.Kind.FULL : ConfigBackup.Kind.SETTINGS;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("vortex.admin")) {
            return out;
        } else {
            String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
            if (cmd.getName().equalsIgnoreCase("kits")) {
                if (args.length == 1) {
                    out.addAll(List.of("create", "copy", "add", "remove", "edit", "delete", "list", "setinv", "load", "seticon"));
                } else if (args.length == 2 && !args[0].equalsIgnoreCase("create")) {
                    out.addAll(this.plugin.kits().ids());
                } else if (args.length == 3 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
                    out.addAll(List.of("ranked", "unranked", "ffa", "skill", "vanilla", "other", "duels"));
                }
            } else if (args.length == 1) {
                out.addAll(
                    List.of(
                        "staff",
                        "rebuild",
                        "reload",
                        "save",
                        "setlobby",
                        "wand",
                        "build",
                        "maintenance",
                        "generate",
                        "arena",
                        "tp",
                        "player",
                        "ranks",
                        "elo",
                        "tier",
                        "end",
                        "forcelobby",
                        "selftest",
                        "tabdesign",
                        "preview",
                        "stoppreview",
                        "bothall",
                        "arenas",
                        "backup",
                        "backups",
                        "restore",
                        "vault",
                        "rankfx",
                        "kitbackup",
                        "settingsbackup",
                        "fullbackup",
                        "kitrestore",
                        "settingsrestore",
                        "fullrestore",
                        "health"
                    )
                );
            } else if (args.length == 2) {
                String var7 = args[0].toLowerCase(Locale.ROOT);
                switch (var7) {
                    case "generate":
                        out.addAll(List.of("duel", "ffa"));
                        break;
                    case "arena":
                    case "tp":
                    case "rebuild":
                        for (Arena a : this.plugin.arenas().all()) {
                            out.add(a.id);
                        }

                        if (!args[0].equalsIgnoreCase("tp")) {
                            for (FfaArena f : this.plugin.ffa().all()) {
                                out.add(f.id);
                            }
                        }

                        if (args[0].equalsIgnoreCase("arena")) {
                            out.addAll(List.of("save", "cancel"));
                        } else if (args[0].equalsIgnoreCase("rebuild")) {
                            out.add("all");
                            out.add("outdated");
                        }
                        break;
                    case "player":
                    case "ranks":
                    case "elo":
                    case "tier":
                    case "forcelobby":
                        for (Player o : Bukkit.getOnlinePlayers()) {
                            out.add(o.getName());
                        }
                        break;
                    case "preview":
                        for (net.vortexrealm.build.SpawnBuilder.Style st : net.vortexrealm.build.SpawnBuilder.Style.values()) {
                            out.add(st.name().toLowerCase(Locale.ROOT));
                        }
                        break;
                    case "tabdesign":
                        for (net.vortexrealm.social.TabDesigns.Design d : net.vortexrealm.social.TabDesigns.DESIGNS) {
                            out.add(d.id().toLowerCase(Locale.ROOT));
                        }
                        break;
                    case "vault":
                        out.addAll(List.of("list", "save", "restore"));
                        break;
                    case "kitrestore":
                        out.add("latest");
                        out.addAll(this.plugin.configBackup().files(ConfigBackup.Kind.KITS));
                        break;
                    case "settingsrestore":
                        out.add("latest");
                        out.addAll(this.plugin.configBackup().files(ConfigBackup.Kind.SETTINGS));
                        break;
                    case "fullrestore":
                        out.add("latest");
                        out.addAll(this.plugin.configBackup().files(ConfigBackup.Kind.FULL));
                        break;
                    case "rankfx":
                        for (Tier t : Tier.values()) {
                            out.add(t.name());
                        }
                        break;
                    case "restore":
                        if (this.plugin.rankBackup() != null) {
                            out.add("latest");
                            out.addAll(this.plugin.rankBackup().files());
                        }
                }
            } else if (args.length == 3) {
                String var13 = args[0].toLowerCase(Locale.ROOT);
                switch (var13) {
                    case "generate":
                        for (ArenaTheme t : ArenaTheme.values()) {
                            if (t != ArenaTheme.CUSTOM) {
                                out.add(t.name().toLowerCase(Locale.ROOT));
                            }
                        }
                        break;
                    case "elo":
                    case "tier":
                        out.addAll(this.plugin.kits().ids());
                        break;
                    case "vault":
                        if (args[1].equalsIgnoreCase("restore")) {
                            out.add("latest");
                            out.addAll(this.plugin.vault().files());
                        }
                        break;
                    case "arena":
                        out.addAll(List.of("edit", "save", "cancel"));
                }
            } else if (args.length == 4 && args[0].equalsIgnoreCase("tier")) {
                for (Tier tx : Tier.values()) {
                    out.add(tx.name());
                }
            }

            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
            return out;
        }
    }
}
