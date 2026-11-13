package net.vortexpvp.plus;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.data.ConfigBackup;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** Every VortexPvP add-on command in one place. */
public final class PlusCommands implements TabExecutor {
    private final VortexPvP plugin;
    private final PlusModule plus;

    public PlusCommands(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
    }

    private static String[] rest(String[] a, int from) {
        return from >= a.length ? new String[0] : Arrays.copyOfRange(a, from, a.length);
    }

    private boolean admin(CommandSender s) {
        if (!s.hasPermission("vortexpvp.admin")) {
            s.sendMessage(Text.mm("<red>You don't have permission."));
            return false;
        }

        return true;
    }

    private boolean staff(CommandSender s) {
        if (!s.hasPermission("vortexpvp.staff")) {
            s.sendMessage(Text.mm("<red>Staff only."));
            return false;
        }

        return true;
    }

    private UUID uuid(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) {
            return p.getUniqueId();
        }

        UUID u = this.plus.store().uuidOf(name);
        if (u != null) {
            return u;
        }

        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        return op == null ? null : op.getUniqueId();
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        String c = command.getName().toLowerCase(Locale.ROOT);
        Player p = s instanceof Player pl ? pl : null;
        try {
            if (c.equals("plus")) {
                return this.plusAdmin(s, a);
            }

            switch (c) {
                case "clan" -> {
                    return this.plus.clans().command(s, a);
                }
                case "season" -> {
                    this.season(s, a);
                    return true;
                }
                case "creator" -> {
                    if (a.length > 0 && !a[0].equalsIgnoreCase("list") && !this.admin(s)) {
                        return true;
                    }

                    this.plus.social().creatorCommand(s, a);
                    return true;
                }
                case "sslog" -> {
                    if (this.staff(s)) {
                        this.plus.fairness().sslog(s, a);
                    }

                    return true;
                }
                case "ecoevent" -> {
                    if (this.admin(s)) {
                        String err = this.plus.eco().startEvent(a.length == 0 ? "airdrop" : a[0].toLowerCase(Locale.ROOT));
                        s.sendMessage(Text.mm(err == null ? "<green>Event started." : "<red>" + Text.esc(err)));
                    }

                    return true;
                }
                case "ecowipe" -> {
                    if (this.admin(s)) {
                        this.plus.eco().wipe(s, a);
                    }

                    return true;
                }
                case "bounty" -> {
                    this.plus.bounties().list(s);
                    return true;
                }
                case "weeklycup" -> {
                    if (a.length > 0 && a[0].equalsIgnoreCase("host")) {
                        if (this.admin(s)) {
                            this.plus.weekly().host(s);
                        }
                    } else {
                        this.plus.weekly().champions(s);
                    }

                    return true;
                }
                case "crate" -> {
                    if (a.length >= 3 && a[0].equalsIgnoreCase("give")) {
                        if (this.admin(s)) {
                            UUID u = this.uuid(a[1]);
                            long n = a.length > 3 ? Long.parseLong(a[3]) : 1L;
                            if (u == null) {
                                s.sendMessage(Text.mm("<red>Unknown player."));
                            } else {
                                this.plus.eco().giveKeys(u, a[2].toLowerCase(Locale.ROOT), n);
                                s.sendMessage(Text.mm("<green>Gave " + n + " " + Text.esc(a[2]) + " key(s) to " + Text.esc(a[1]) + "."));
                            }
                        }

                        return true;
                    }
                }
                default -> {
                }
            }

            if (p == null) {
                s.sendMessage(Text.mm("<red>Players only."));
                return true;
            }

            switch (c) {
                case "ladder" -> {
                    if (a.length >= 1 && a[0].equalsIgnoreCase("country")) {
                        String code = a.length > 1 ? a[1] : (this.plugin.countries() == null || this.plugin.countries().of(p.getUniqueId()) == null ? "" : this.plugin.countries().of(p.getUniqueId()).code());
                        List<String[]> top = this.plus.ladder().countryTop(code);
                        p.sendMessage(Text.mm("<gold><b>Top players - " + Text.esc(code.toUpperCase(Locale.ROOT))));
                        for (int i = 0; i < Math.min(10, top.size()); i++) {
                            p.sendMessage(Text.mm("<gray>#" + (i + 1) + " <white>" + Text.esc(top.get(i)[0]) + " <dark_gray>- " + Ladder.colored(Integer.parseInt(top.get(i)[1]))));
                        }

                        if (top.isEmpty()) {
                            p.sendMessage(Text.mm("<gray>Nobody from there online."));
                        }
                    } else {
                        UUID t = a.length > 0 ? this.uuid(a[0]) : p.getUniqueId();
                        if (t == null) {
                            p.sendMessage(Text.mm("<red>Unknown player."));
                        } else {
                            this.plus.ladder().card(p, t);
                        }
                    }
                }
                case "clanchat" -> {
                    if (a.length > 0) {
                        this.plus.clans().chat(p, String.join(" ", a));
                    }
                }
                case "duo" -> this.plus.duo().command(p, a);
                case "partywar" -> this.plus.duo().partyWar(p, a);
                case "lastmatch" -> this.plus.analysis().lastMatch(p, 0);
                case "replay" -> {
                    if (a.length == 0 || a[0].equalsIgnoreCase("list")) {
                        this.plus.replays().list(p);
                    } else if (a[0].equalsIgnoreCase("stop")) {
                        this.plus.replays().stop(p);
                    } else {
                        this.plus.replays().watch(p, a[0]);
                    }
                }
                case "drill" -> {
                    if (a.length == 0) {
                        this.plus.drills().menu(p);
                    } else if (a[0].equalsIgnoreCase("stop")) {
                        this.plus.drills().stop(p, false);
                    } else {
                        boolean top = a[0].equalsIgnoreCase("top");
                        String name = top ? (a.length > 1 ? a[1] : "cps") : a[0];
                        Drills.Drill d = null;
                        for (Drills.Drill x : Drills.Drill.values()) {
                            if (x.name().equalsIgnoreCase(name)) {
                                d = x;
                            }
                        }

                        if (d == null) {
                            p.sendMessage(Text.mm("<red>Drills: cps, aim, wtap, pearl, crystal, mace"));
                        } else if (top) {
                            this.plus.drills().showTop(p, d);
                        } else {
                            this.plus.drills().start(p, d);
                        }
                    }
                }
                case "coach" -> this.plus.social().coach(p, a);
                case "arcade" -> {
                    if (a.length >= 2 && a[0].equalsIgnoreCase("join")) {
                        Arcade.Mode m = null;
                        for (Arcade.Mode x : Arcade.Mode.values()) {
                            if (x.name().equalsIgnoreCase(a[1])) {
                                m = x;
                            }
                        }

                        if (m == null) {
                            p.sendMessage(Text.mm("<red>Games: sumo, boxing, spleef, oitc, koth, bridge, parkour"));
                        } else {
                            this.plus.arcade().join(p, m);
                        }
                    } else if (a.length >= 1 && a[0].equalsIgnoreCase("leave")) {
                        if (this.plus.arcade().leaveQueue(p)) {
                            p.sendMessage(Text.mm("<gray>Left the arcade queue."));
                        }
                    } else {
                        this.plus.arcade().menu(p);
                    }
                }
                case "pass" -> this.plus.progression().passMenu(p, 0);
                case "missions" -> this.plus.progression().missionsMenu(p);
                case "achievements" -> this.plus.progression().achievementsMenu(p, 0);
                case "titles" -> this.plus.progression().titlesMenu(p);
                case "daily" -> this.plus.progression().dailyMenu(p);
                case "vfx" -> this.plus.flair().menu(p, a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "win");
                case "pet" -> this.plus.flair().menu(p, "pet");
                case "gadget" -> {
                    String g = this.plus.flair().selected(p.getUniqueId(), "gadget");
                    if (a.length > 0 && a[0].equalsIgnoreCase("use") && g != null) {
                        this.plus.hub().tryGadget(p, g);
                    } else {
                        this.plus.flair().menu(p, "gadget");
                    }
                }
                case "parkour" -> this.plus.hub().parkourCommand(p, a);
                case "hud" -> {
                    PlayerData d = this.plus.store().get(p);
                    d.hud = !d.hud;
                    d.dirty = true;
                    p.sendMessage(Text.mm(d.hud ? "<green>Match HUD on <gray>(CPS, combo, opponent heals, timer)." : "<gray>Match HUD off."));
                }
                case "live" -> this.plus.social().live(p, a);
                case "streamer" -> this.plus.social().streamer(p);
                case "link" -> {
                    if (a.length > 0 && a[0].equalsIgnoreCase("unlink")) {
                        this.plus.discord().unlink(p);
                    } else {
                        this.plus.discord().link(p);
                    }
                }
                case "code" -> {
                    if (a.length == 0) {
                        String cur = this.plus.store().get(p).creatorCode;
                        p.sendMessage(Text.mm("<gray>/code <creator code> - support a creator" + (cur.isEmpty() ? "" : " <dark_gray>(now: <light_purple>" + Text.esc(cur) + "<dark_gray>)")));
                    } else {
                        this.plus.social().useCode(p, a[0]);
                    }
                }
                case "mentor" -> this.plus.social().mentor(p, a);
                case "fspec" -> {
                    if (a.length == 0) {
                        p.sendMessage(Text.mm("<gray>/fspec <friend> - watch a friend's match"));
                    } else {
                        this.plus.social().spectateFriend(p, a[0]);
                    }
                }
                case "cinematic" -> this.plus.social().cinematic(p, a);
                case "evidence" -> {
                    if (this.staff(p)) {
                        this.plus.fairness().evidence(p, a.length > 0 ? a[0] : null);
                    }
                }
                case "alerts" -> {
                    if (this.staff(p)) {
                        this.plus.fairness().toggleAlerts(p);
                    }
                }
                case "kitvote" -> this.plus.weekly().kitvote(p, a);
                case "motw" -> this.plus.weekly().motwCommand(p, a);
                case "crate" -> this.plus.eco().crates(p);
                case "bid" -> this.plus.eco().bidCommand(p, a);
                case "deal" -> this.plus.eco().dealMenu(p);
                default -> {
                    return false;
                }
            }
        } catch (RuntimeException ex) {
            s.sendMessage(Text.mm("<red>Something went wrong: " + Text.esc(String.valueOf(ex.getMessage()))));
            this.plugin.getLogger().log(java.util.logging.Level.WARNING, "VortexPvP /" + c + " failed", ex);
        }

        return true;
    }

    private void season(CommandSender s, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "hof", "halloffame" -> {
                if (s instanceof Player p) {
                    this.plus.seasons().hallOfFame(p, a.length > 1 ? Integer.parseInt(a[1]) : 0);
                }
            }
            case "end" -> {
                if (this.admin(s)) {
                    if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) {
                        s.sendMessage(Text.mm("<red>This ends season " + this.plus.seasons().number() + " now (Hall of Fame, rewards, soft reset). <white>/season end confirm"));
                    } else {
                        this.plus.seasons().endSeason(s);
                    }
                }
            }
            case "board" -> {
                if (this.admin(s) && s instanceof Player p) {
                    this.plus.seasons().placeBoard(p.getLocation().add(0.0, 2.0, 0.0));
                    s.sendMessage(Text.mm("<green>Hall of Fame board placed here."));
                }
            }
            default -> this.plus.seasons().info(s);
        }
    }

    // ------------------------------------------------------------------ /plus (admin)

    private boolean plusAdmin(CommandSender s, String[] a) {
        if (!this.admin(s)) {
            return true;
        }

        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                this.plus.reload();
                s.sendMessage(Text.mm("<green>VortexPvP add-on settings reloaded."));
            }
            case "selftest" -> {
                if (s instanceof Player p) {
                    p.performCommand("pvpadmin selftest");
                } else {
                    Bukkit.dispatchCommand(s, "pvpadmin selftest");
                }
            }
            case "web" -> s.sendMessage(Text.mm("<gray>Admin dashboard: <white>" + Text.esc(this.plus.web().adminLink()) + " <gray>(on the plugin web server, keep the key secret)"));
            case "rebuild-arcade" -> {
                this.plus.arcade().rebuildAll();
                s.sendMessage(Text.mm("<green>Arcade arenas rebuilt."));
            }
            case "xp" -> {
                UUID u = a.length > 2 ? this.uuid(a[1]) : null;
                if (u == null) {
                    s.sendMessage(Text.mm("<red>/plus xp <player> <amount>"));
                } else {
                    this.plus.progression().xp(u, Long.parseLong(a[2]), "admin");
                    s.sendMessage(Text.mm("<green>Done."));
                }
            }
            case "unlock" -> {
                UUID u = a.length > 2 ? this.uuid(a[1]) : null;
                if (u == null) {
                    s.sendMessage(Text.mm("<red>/plus unlock <player> <group:value> <gray>(e.g. win:lightning, pet:fox)"));
                } else {
                    this.plus.progression().unlock(u, a[2].toLowerCase(Locale.ROOT));
                    s.sendMessage(Text.mm("<green>Unlocked."));
                }
            }
            case "test" -> this.test(s, rest(a, 1));
            case "rollback" -> this.rollback(s, rest(a, 1));
            case "status" -> this.status(s);
            default -> {
                s.sendMessage(Text.mm("<gradient:#7B2FF7:#2FD3F7><b>VortexPvP</b></gradient> <gray>add-on systems"));
                s.sendMessage(Text.mm("<gray>/plus status • reload • selftest • web • rebuild-arcade • xp • unlock"));
                s.sendMessage(Text.mm("<gray>/plus test match [kit] • test season-end • test webhook <text> • test replay"));
                s.sendMessage(Text.mm("<gray>/plus rollback [list|<file>] <dark_gray>- restore the settings and kits from before the last update"));
            }
        }

        return true;
    }

    private void status(CommandSender s) {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("Season", this.plus.seasons().number() + " (ends in " + this.plus.seasons().timeLeft() + ")");
        rows.put("Storage", this.plus.store().shared() ? "MySQL (shared across servers)" : "files in plugins/VortexPvP/plus");
        rows.put("Players with data", String.valueOf(this.plus.store().all().size()));
        rows.put("Achievements", String.valueOf(this.plus.progression().achievements().size()));
        rows.put("Replays kept", String.valueOf(this.plus.replays().all().size()));
        rows.put("Clans", String.valueOf(this.plus.clans().ids().size()));
        rows.put("Discord", (this.plus.discord().webhookOn() ? "webhook on" : "no webhook") + ", " + (this.plus.discord().botOn() ? "bot on" : "no bot") + ", "
            + this.plus.discord().sent() + " sent, last: " + this.plus.discord().lastStatus());
        rows.put("Featured arcade", this.plus.arcade().featured() == null ? "none (weekdays)" : this.plus.arcade().featured().display);
        rows.put("Hub decorations", this.plus.hub().season() == null ? "none" : this.plus.hub().season());
        rows.put("Bounties", String.valueOf(this.plus.bounties().all().size()));
        rows.put("Daily deal", this.plus.eco().dealPrice() + " shards");
        rows.forEach((k, v) -> s.sendMessage(Text.mm("<gray>" + k + ": <white>" + Text.esc(v))));
    }

    private void rollback(CommandSender s, String[] a) {
        ConfigBackup cb = this.plugin.configBackup();
        if (cb == null) {
            s.sendMessage(Text.mm("<red>Backups are not available."));
            return;
        }

        List<String> files = cb.files(ConfigBackup.Kind.FULL);
        if (a.length == 0 || a[0].equalsIgnoreCase("list")) {
            s.sendMessage(Text.mm("<gold><b>Full backups</b> <gray>(newest first, in " + Text.esc(cb.dir().getPath()) + ")"));
            for (int i = 0; i < Math.min(10, files.size()); i++) {
                File f = new File(cb.dir(), files.get(i));
                s.sendMessage(Text.mm("<white>" + Text.esc(files.get(i)) + " <dark_gray>" + new java.util.Date(f.lastModified())
                    + " <click:suggest_command:'/plus rollback " + files.get(i) + "'><yellow>[restore]</yellow></click>"));
            }

            if (files.isEmpty()) {
                s.sendMessage(Text.mm("<gray>No backups yet - one is made before every update and every 12 hours."));
            }

            s.sendMessage(Text.mm("<gray>/plus rollback last <dark_gray>- the newest backup made before an update"));
            return;
        }

        String file = a[0].equalsIgnoreCase("last") ? (files.isEmpty() ? null : files.get(0)) : a[0];
        if (file == null || !files.contains(file)) {
            s.sendMessage(Text.mm("<red>No such backup."));
            return;
        }

        try {
            int n = cb.restore(ConfigBackup.Kind.FULL, file);
            s.sendMessage(Text.mm("<green>Restored " + n + " files from " + Text.esc(file) + ". <gray>Restart the server to load them everywhere."));
        } catch (java.io.IOException ex) {
            s.sendMessage(Text.mm("<red>Restore failed: " + Text.esc(ex.getMessage())));
        }
    }

    // ------------------------------------------------------------------ tests without players

    private void test(CommandSender s, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "match" -> {
                Kit kit = a.length > 1 ? this.plugin.kits().get(a[1]) : (this.plus.ladder().rankedKits().isEmpty() ? null : this.plus.ladder().rankedKits().get(0));
                if (kit == null) {
                    s.sendMessage(Text.mm("<red>No such kit."));
                    return;
                }

                UUID w = UUID.nameUUIDFromBytes("VortexTestWinner".getBytes());
                UUID l = UUID.nameUUIDFromBytes("VortexTestLoser".getBytes());
                PlayerData wd = this.plus.store().get(w);
                wd.name = "TestWinner";
                PlayerData ld = this.plus.store().get(l);
                ld.name = "TestLoser";
                this.plus.store().index("TestWinner", w);
                this.plus.store().index("TestLoser", l);
                long xpBefore = wd.xp;
                int achBefore = wd.achievements.size();
                long missionsBefore = wd.missions.values().stream().mapToLong(Long::longValue).sum();
                Progression pr = this.plus.progression();
                pr.track(w, "play", kit.id, 1L);
                pr.track(w, "hits", kit.id, 42L);
                pr.track(w, "win", kit.id, 1L);
                pr.track(w, "ranked_win", kit.id, 1L);
                pr.best(w, "best_combo", 12L);
                pr.xp(w, 75L, "test win");
                pr.track(l, "play", kit.id, 1L);
                pr.xp(l, 15L, "test loss");
                int[] h = wd.h2h(l);
                wd.h2h.put(l.toString(), (h[0] + 1) + ":" + h[1]);
                Map<String, String> sum = new LinkedHashMap<>();
                sum.put("id", "0");
                sum.put("kit", kit.id);
                sum.put("ranked", "true");
                sum.put("won", "true");
                sum.put("hits", "42");
                sum.put("combo", "12");
                wd.history.add(0, Analysis.encode(sum));
                wd.dirty = true;
                ld.dirty = true;
                boolean decoded = Analysis.decode(wd.history.get(0)).equals(sum);
                this.plus.discord().post("🧪 Test: **TestWinner** beat **TestLoser** in ranked " + Text.plain(Text.mm(kit.displayName)));
                long missionsAfter = wd.missions.values().stream().mapToLong(Long::longValue).sum();
                s.sendMessage(Text.mm("<green>Test match (" + kit.id + "): <gray>XP +" + (wd.xp - xpBefore) + ", level " + Progression.level(wd.xp) + ", pass level "
                    + Progression.passLevel(wd.passXp) + ", missions +" + (missionsAfter - missionsBefore) + ", achievements +" + (wd.achievements.size() - achBefore)
                    + " (" + String.join(", ", new ArrayList<>(wd.achievements).subList(Math.max(0, wd.achievements.size() - 3), wd.achievements.size())) + "), h2h "
                    + wd.h2h.get(l.toString()) + ", analysis " + (decoded ? "ok" : "BROKEN") + ", ladder " + Ladder.name(Ladder.step(1400)) + " at 1400 points, webhook queued"
                    + (this.plus.discord().webhookOn() ? "" : " (no webhook set)")));
            }
            case "season-end" -> {
                int before = this.plus.seasons().number();
                this.plus.seasons().endSeason(s);
                s.sendMessage(Text.mm("<green>Season " + before + " -> " + this.plus.seasons().number() + ". Hall of Fame: /season hof " + before));
            }
            case "webhook" -> {
                this.plus.discord().post(a.length > 1 ? String.join(" ", rest(a, 1)) : "🧪 VortexPvP webhook test");
                s.sendMessage(Text.mm("<green>Queued. <gray>Last result: " + Text.esc(this.plus.discord().lastStatus()) + " (" + this.plus.discord().sent() + " sent)"));
            }
            case "replay" -> {
                String r = Replays.codecCheck();
                s.sendMessage(Text.mm(r == null ? "<green>Replay encode/decode: 200 frames identical." : "<red>Replay codec: " + Text.esc(r)));
            }
            default -> s.sendMessage(Text.mm("<gray>/plus test match [kit] | season-end | webhook <text> | replay"));
        }
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] a) {
        String c = command.getName().toLowerCase(Locale.ROOT);
        List<String> opts = new ArrayList<>();
        if (a.length == 1) {
            switch (c) {
                case "plus" -> opts.addAll(List.of("status", "reload", "selftest", "web", "rebuild-arcade", "xp", "unlock", "test", "rollback"));
                case "season" -> opts.addAll(List.of("hof", "end", "board"));
                case "clan" -> opts.addAll(List.of("create", "invite", "accept", "deny", "leave", "kick", "promote", "demote", "owner", "disband", "color", "war", "chat", "top", "info"));
                case "duo" -> opts.addAll(List.of("invite", "accept", "queue", "unqueue", "leave", "top"));
                case "drill" -> {
                    for (Drills.Drill d : Drills.Drill.values()) {
                        opts.add(d.name().toLowerCase(Locale.ROOT));
                    }

                    opts.add("top");
                    opts.add("stop");
                }
                case "arcade" -> opts.addAll(List.of("join", "leave"));
                case "vfx" -> opts.addAll(List.of("win", "death", "pet", "gadget"));
                case "parkour" -> opts.addAll(List.of("top", "checkpoint", "quit", "build", "remove"));
                case "ecoevent" -> opts.addAll(List.of("airdrop", "meteor", "koth"));
                case "crate" -> opts.add("give");
                case "bid" -> opts.add("sell");
                case "kitvote" -> opts.add("submit");
                case "mentor" -> opts.addAll(List.of("accept", "leave"));
                case "coach" -> opts.addAll(List.of("note", "stop"));
                case "replay" -> {
                    opts.add("list");
                    opts.add("stop");
                    if (s instanceof Player p) {
                        opts.addAll(this.plus.replays().idsOf(p.getUniqueId()));
                    }
                }
                case "ladder" -> opts.add("country");
                default -> {
                }
            }

            if (opts.isEmpty() || List.of("ladder", "duo", "mentor", "coach", "fspec", "cinematic", "evidence", "sslog").contains(c)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    opts.add(p.getName());
                }
            }
        } else if (a.length == 2) {
            switch (c) {
                case "arcade" -> {
                    for (Arcade.Mode m : Arcade.Mode.values()) {
                        opts.add(m.name().toLowerCase(Locale.ROOT));
                    }
                }
                case "duo" -> {
                    if (a[0].equalsIgnoreCase("queue")) {
                        for (Kit k : this.plus.ladder().rankedKits()) {
                            opts.add(k.id);
                        }
                    } else {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            opts.add(p.getName());
                        }
                    }
                }
                case "plus" -> {
                    if (a[0].equalsIgnoreCase("test")) {
                        opts.addAll(List.of("match", "season-end", "webhook", "replay"));
                    } else if (a[0].equalsIgnoreCase("rollback")) {
                        opts.addAll(List.of("list", "last"));
                    } else {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            opts.add(p.getName());
                        }
                    }
                }
                case "drill" -> {
                    for (Drills.Drill d : Drills.Drill.values()) {
                        opts.add(d.name().toLowerCase(Locale.ROOT));
                    }
                }
                default -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        opts.add(p.getName());
                    }
                }
            }
        }

        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        opts.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return opts;
    }
}
