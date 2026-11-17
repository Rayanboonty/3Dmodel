package net.practicepvp.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Msg;
import net.practicepvp.config.Opt;
import net.practicepvp.data.Database;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.data.Setting;
import net.practicepvp.dialog.Dialogs;
import net.practicepvp.ffa.FfaArena;
import net.practicepvp.gui.menus.InventoryViewMenu;
import net.practicepvp.gui.menus.SpectateMenu;
import net.practicepvp.kit.Kit;
import net.practicepvp.match.InventorySnapshot;
import net.practicepvp.match.Match;
import net.practicepvp.party.Party;
import net.practicepvp.rank.Tier;
import net.practicepvp.session.PlayerState;
import net.practicepvp.session.Session;
import net.practicepvp.session.Tp;
import net.practicepvp.util.Fmt;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class PlayerCommands implements CommandExecutor, TabCompleter {
    private final PracticePlugin plugin;

    public PlayerCommands(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player p) {
            String name = cmd.getName().toLowerCase(Locale.ROOT);
            Session s = this.plugin.sessions().get(p);
            if (s.state == PlayerState.OUTSIDE && this.plugin.worlds().managed(p.getWorld())) {
                this.plugin.syncWorldState(p, true);
            }

            if (s.state == PlayerState.OUTSIDE && !name.equals("lobby") && !name.equals("stats") && !name.equals("leaderboard") && !name.equals("ping")) {
                Msg.error(p, "general.not-in-pvp");
                return true;
            } else {
                switch (name) {
                    case "lobby":
                        this.lobby(p, s);
                        break;
                    case "leave":
                        this.leave(p, s);
                        break;
                    case "leavepvp":
                        this.leavePvp(p, s);
                        break;
                    case "queue":
                        this.queue(p, args, null);
                        break;
                    case "ranked":
                        this.queue(p, args, true);
                        break;
                    case "unranked":
                        this.queue(p, args, false);
                        break;
                    case "duel":
                        this.duel(p, args);
                        break;
                    case "party":
                        this.party(p, args);
                        break;
                    case "partychat":
                        if (args.length == 0) {
                            Msg.error(p, "usage.partychat");
                        } else {
                            this.plugin.parties().chat(p, String.join(" ", args));
                        }
                        break;
                    case "spectate":
                        this.spectate(p, args);
                        break;
                    case "leaderboard":
                        this.leaderboard(p, args);
                        break;
                    case "stats":
                        this.stats(p, args);
                        break;
                    case "settings":
                        if (this.plugin.settingsDialogs() != null) {
                            this.plugin.settingsDialogs().hub(p);
                        } else {
                            this.plugin.dialogs().lobby().settings(p, Setting.Category.GAMEPLAY);
                        }
                        break;
                    case "challenge":
                        this.challenge(p, args);
                        break;
                    case "kiteditor":
                        if (s.state != PlayerState.LOBBY) {
                            Msg.error(p, "kit-editor.lobby-only");
                        } else if (args.length > 0 && this.plugin.kits().get(args[0].toLowerCase(java.util.Locale.ROOT)) != null) {
                            // straight into one kit: /kiteditor crystal
                            net.practicepvp.gui.menus.KitEditorMenu.start(this.plugin, p, this.plugin.kits().get(args[0].toLowerCase(java.util.Locale.ROOT)));
                        } else {
                            this.plugin.dialogs().lobby().kitEditor(p);
                        }
                        break;
                    case "ffa":
                        this.ffa(p, args);
                        break;
                    case "pvpinv":
                        this.inventory(p, args);
                        break;
                    case "rematch":
                        this.rematch(p, s);
                        break;
                    case "rankup":
                        this.rankup(p, args);
                        break;
                    case "ping":
                        Player t = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : p;
                        if (t == null) {
                            Msg.error(p, "general.player-offline", "player", args[0]);
                        } else {
                            Msg.send(p, "general.ping", "player", t.getName(), "ping", Text.mm(Fmt.pingColored(t.getPing())));
                        }
                        break;
                    default:
                        return false;
                }

                return true;
            }
        } else {
            Msg.send(sender, "general.players-only");
            return true;
        }
    }

    private void lobby(Player p, Session s) {
        if (s.state == PlayerState.OUTSIDE) {
            Location lobby = this.plugin.lobby().lobby();
            if (!this.plugin.worlds().managed(lobby.getWorld())) {
                Msg.error(p, "general.lobby-not-set");
            } else {
                Tp.to(p, lobby);
                if (this.plugin.sessions().state(p) == PlayerState.OUTSIDE) {
                    this.plugin.syncWorldState(p);
                }
            }
        } else {
            this.leave(p, s);
        }
    }

    private void leave(Player p, Session s) {
        switch (s.state) {
            case QUEUE:
                this.plugin.queues().leaveAll(p, true);
                Msg.send(p, "queue.left-all");
                break;
            case PREMATCH:
            case MATCH:
                if (s.match != null) {
                    s.match.forfeit(p, false);
                } else {
                    this.plugin.lobby().sendToLobby(p);
                }
                break;
            case FFA:
                this.plugin.ffa().leave(p, false);
                break;
            case SPECTATING:
                if (s.spectating != null) {
                    s.spectating.removeSpectator(p, true);
                } else {
                    this.plugin.lobby().sendToLobby(p);
                }
                break;
            case EDITING:
                p.closeInventory();
                break;
            default:
                this.plugin.lobby().sendToLobby(p);
                Msg.send(p, "general.teleported-lobby");
        }
    }

    private void leavePvp(Player p, Session s) {
        if (!s.state.fighting() && s.state != PlayerState.PREMATCH) {
            Location target = this.plugin.modes() != null && this.plugin.modes().hubWorld() != null ? this.plugin.modes().hub() : null;
            if (target == null) {
                target = this.plugin.worlds().storedOutsideLocation(p);
            }

            if (target == null || target.getWorld() == null || this.plugin.worlds().managed(target.getWorld())) {
                target = this.plugin.worlds().firstExcludedSpawn();
            }

            if (target == null) {
                Msg.error(p, "general.no-smp-world");
            } else {
                Tp.to(p, target);
            }
        } else {
            Msg.error(p, "general.leave-first");
        }
    }

    private void queue(Player p, String[] args, Boolean forcedRanked) {
        boolean ranked = forcedRanked != null ? forcedRanked : false;
        int kitArg = 0;
        if (forcedRanked == null && args.length > 0 && (args[0].equalsIgnoreCase("ranked") || args[0].equalsIgnoreCase("unranked"))) {
            ranked = args[0].equalsIgnoreCase("ranked");
            kitArg = 1;
        }

        if (args.length > kitArg) {
            Kit k = this.plugin.kits().get(args[kitArg]);
            if (k == null) {
                Msg.error(p, "general.unknown-kit", "kit", args[kitArg]);
            } else {
                this.plugin.queues().join(p, k, ranked, false);
            }
        } else {
            this.plugin.dialogs().openQueue(p, ranked, Dialogs.QueueTab.MOST_PLAYED);
        }
    }

    private void duel(Player p, String[] args) {
        if (args.length == 0) {
            Msg.error(p, "usage.duel");
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ((sub.equals("accept") || sub.equals("deny")) && args.length >= 2) {
                if (sub.equals("accept")) {
                    this.plugin.duels().accept(p, args[1]);
                } else {
                    this.plugin.duels().deny(p, args[1]);
                }
            } else {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    Msg.error(p, "general.player-offline", "player", args[0]);
                } else if (target.equals(p)) {
                    Msg.error(p, "duel.self");
                } else if (args.length >= 2) {
                    Kit k = this.plugin.kits().get(args[1]);
                    if (k == null) {
                        Msg.error(p, "general.unknown-kit", "kit", args[1]);
                    } else {
                        this.plugin.duels().send(p, target, k, k.rounds(), null);
                    }
                } else if (this.plugin.social() != null && !this.plugin.social().appearsOnline(p, target)) {
                    Msg.error(p, "general.player-offline", "player", args[0]);
                } else if (this.plugin.duelDialogs() != null && this.plugin.dialogs().useDialog(p)) {
                    this.plugin.duelDialogs().pickKit(p, target);
                } else {
                    this.plugin.dialogs().lobby().duel(p, target, 0, 0);
                }
            }
        }
    }

    /** /challenge <player> [kit] - a ranked Tier Challenge. */
    private void challenge(Player p, String[] args) {
        if (args.length == 0) {
            p.sendMessage(Text.mm("<gray>Usage: <white>/challenge <player> [kit]"));
        } else {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || this.plugin.social() != null && !this.plugin.social().appearsOnline(p, target)) {
                Msg.error(p, "general.player-offline", "player", args[0]);
            } else if (target.equals(p)) {
                Msg.error(p, "duel.self");
            } else if (!net.practicepvp.config.Cfg.bool(net.practicepvp.config.Opt.TIER_CHALLENGE)) {
                p.sendMessage(Text.mm("<red>Tier Challenges are disabled right now."));
            } else if (args.length >= 2) {
                Kit k = this.plugin.kits().get(args[1]);
                if (k == null) {
                    Msg.error(p, "general.unknown-kit", "kit", args[1]);
                } else if (this.plugin.duelDialogs() != null && this.plugin.dialogs().useDialog(p)) {
                    this.plugin.duelDialogs().challenge(p, target, k, false, false);
                } else {
                    p.sendMessage(Text.mm("<yellow>⚠ Tier Challenges have a large impact on your Tier Score. Leaving counts as a loss."));
                    this.plugin.duels().send(p, target, k, 0, null, new net.practicepvp.duel.DuelManager.Options(false, true, true));
                }
            } else if (this.plugin.duelDialogs() != null && this.plugin.dialogs().useDialog(p)) {
                this.plugin.duelDialogs().pickKit(p, target, net.practicepvp.dialog.DuelDialogs.Tab.WEAPONS, true);
            } else {
                p.sendMessage(Text.mm("<gray>Usage: <white>/challenge <player> <kit>"));
            }
        }
    }

    private void rematch(Player p, Session s) {
        if (s.lastOpponent != null && s.lastMatchKit != null) {
            Player o = Bukkit.getPlayer(s.lastOpponent);
            Kit k = this.plugin.kits().get(s.lastMatchKit);
            if (o != null && k != null) {
                this.plugin.duels().send(p, o, k, k.rounds(), null);
            } else {
                Msg.error(p, "duel.no-rematch");
            }
        } else {
            Msg.error(p, "duel.no-rematch");
        }
    }

    private void party(Player p, String[] args) {
        if (args.length == 0) {
            if (this.plugin.parties().of(p) == null) {
                Msg.plain(p, "usage.party");
            } else {
                this.plugin.dialogs().lobby().party(p);
            }
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
            switch (sub) {
                case "create":
                    this.plugin.parties().create(p);
                    break;
                case "invite":
                    if (target == null) {
                        Msg.error(p, "general.player-offline", "player", args.length > 1 ? args[1] : "?");
                    } else {
                        this.plugin.parties().invite(p, target);
                    }
                    break;
                case "accept":
                case "join":
                    if (target == null) {
                        Msg.error(p, "general.player-offline", "player", args.length > 1 ? args[1] : "?");
                    } else {
                        this.plugin.parties().accept(p, target);
                    }
                    break;
                case "leave":
                    this.plugin.parties().leave(p);
                    break;
                case "kick":
                    if (target == null) {
                        Msg.error(p, "general.player-offline", "player", args.length > 1 ? args[1] : "?");
                    } else {
                        this.plugin.parties().kick(p, target);
                    }
                    break;
                case "promote":
                case "leader":
                    if (target == null) {
                        Msg.error(p, "general.player-offline", "player", args.length > 1 ? args[1] : "?");
                    } else {
                        this.plugin.parties().promote(p, target);
                    }
                    break;
                case "disband":
                    Party party = this.plugin.parties().of(p);
                    if (party != null && party.isLeader(p)) {
                        this.plugin.parties().disband(party);
                    } else {
                        Msg.error(p, "party.not-leader");
                    }
                    break;
                case "open":
                    this.plugin.parties().setOpen(p, true);
                    break;
                case "close":
                    this.plugin.parties().setOpen(p, false);
                    break;
                case "info":
                case "list":
                    this.plugin.dialogs().lobby().party(p);
                    break;
                case "games":
                case "start":
                    this.plugin.dialogs().lobby().partyGames(p, false);
                    break;
                case "chat":
                    if (args.length < 2) {
                        Msg.error(p, "usage.partychat");
                    } else {
                        this.plugin.parties().chat(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                    }
                    break;
                default:
                    Msg.plain(p, "usage.party");
            }
        }
    }

    private void spectate(Player p, String[] args) {
        if (args.length == 0) {
            this.plugin.dialogs().lobby().spectate(p);
        } else {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Msg.error(p, "general.player-offline", "player", args[0]);
            } else {
                Session ts = this.plugin.sessions().get(target);
                Match m = ts.match != null ? ts.match : ts.spectating;
                if (m == null) {
                    Msg.error(p, "spectate.not-playing", "player", target.getName());
                } else {
                    SpectateMenu.spectate(this.plugin, p, m);
                }
            }
        }
    }

    private void ffa(Player p, String[] args) {
        if (args.length == 0) {
            this.plugin.dialogs().openFfa(p);
        } else {
            FfaArena a = this.plugin.ffa().get(args[0]);
            if (a == null) {
                Msg.error(p, "ffa.unknown", "arena", args[0]);
            } else {
                this.plugin.ffa().join(p, a);
            }
        }
    }

    private void inventory(Player p, String[] args) {
        if (args.length != 0) {
            try {
                InventorySnapshot snap = this.plugin.snapshots().get(UUID.fromString(args[0]));
                if (snap == null) {
                    Msg.error(p, "match.inventory-expired");
                    return;
                }

                new InventoryViewMenu(this.plugin, p, snap).open();
            } catch (IllegalArgumentException var4) {
                Msg.error(p, "match.inventory-expired");
            }
        }
    }

    private void openStats(Player p, UUID u, String name) {
        PlayerState st = this.plugin.sessions().state(p);
        boolean calm = st == PlayerState.LOBBY
            || st == PlayerState.QUEUE
            || st == PlayerState.OUTSIDE && this.plugin.hub() != null && this.plugin.hub().inHub(p);
        if (calm && Cfg.bool(Opt.STATS_3D) && this.plugin.statsHologram() != null) {
            this.plugin.statsHologram().open(p, u, name);
        } else {
            this.plugin.dialogs().boards().profile(p, u, name, "overall");
        }
    }

    private void stats(Player p, String[] args) {
        if (args.length == 0) {
            this.openStats(p, p.getUniqueId(), p.getName());
        } else {
            Player online = Bukkit.getPlayerExact(args[0]);
            if (online != null) {
                this.openStats(p, online.getUniqueId(), online.getName());
            } else {
                String name = args[0];
                this.plugin.db().supply(c -> this.plugin.db().findUuid(c, name)).thenAccept(u -> Tasks.ensureSync(() -> {
                    if (u == null) {
                        Msg.error(p, "general.player-unknown", "player", name);
                    } else {
                        this.openStats(p, u, name);
                    }
                }));
            }
        }
    }

    private void leaderboard(Player p, String[] args) {
        if (args.length == 0) {
            this.plugin.dialogs().lobby().leaderboards(p);
        } else {
            String board = args[0].toLowerCase(Locale.ROOT);
            Kit kit = this.plugin.kits().get(board);
            if (kit == null && !board.equals("overall")) {
                Msg.error(p, "general.unknown-kit", "kit", args[0]);
            } else {
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Math.max(1, Integer.parseInt(args[1]));
                    } catch (NumberFormatException var12) {
                    }
                }

                List<Database.LbEntry> list = kit == null ? this.plugin.leaderboards().overall() : this.plugin.leaderboards().top(kit.id);
                int pages = Math.max(1, (list.size() + 9) / 10);
                page = Math.min(page, pages);
                Component title = kit == null ? Text.mm("<gold><b>Overall") : kit.label();
                p.sendMessage(Msg.get("leaderboard.header", "board", title, "page", page, "pages", pages));
                if (list.isEmpty()) {
                    p.sendMessage(Msg.get("leaderboard.empty"));
                }

                for (int i = (page - 1) * 10; i < Math.min(list.size(), page * 10); i++) {
                    Database.LbEntry e = list.get(i);
                    Tier t = Tier.byIndex(e.tier());
                    p.sendMessage(
                        Msg.get(
                            "leaderboard.line",
                            "rank",
                            i + 1,
                            "player",
                            e.name(),
                            "tier",
                            Text.mm(t == null ? "<gray>-" : t.mm()),
                            "elo",
                            Fmt.num(e.elo()),
                            "wins",
                            e.wins(),
                            "losses",
                            e.losses()
                        )
                    );
                }

                int pos = kit == null ? -1 : this.plugin.leaderboards().position(kit.id, p.getUniqueId());
                if (pos > 0) {
                    p.sendMessage(Msg.get("leaderboard.you", "rank", pos));
                }

                Component nav = Component.empty();
                if (page > 1) {
                    nav = nav.append(Msg.get("leaderboard.prev", "board", board, "page", page - 1));
                }

                if (page < pages) {
                    nav = nav.append((Component)Component.text("  ")).append(Msg.get("leaderboard.next", "board", board, "page", page + 1));
                }

                if (!nav.equals(Component.empty())) {
                    p.sendMessage(nav);
                }
            }
        }
    }

    private void rankup(Player p, String[] args) {
        PvPProfile prof = this.plugin.profiles().get(p);
        if (args.length >= 2) {
            Kit k = this.plugin.kits().get(args[0]);
            if (k != null && this.plugin.ranks().defenseDue(prof, k) != null) {
                if (args[1].equalsIgnoreCase("defend") || args[1].equalsIgnoreCase("accept")) {
                    if (this.plugin.ranks().startDefense(prof, k) && this.plugin.queues().join(p, k, true, false)) {
                        p.sendMessage(Text.mm("<gold>⚠ Tier Defense Challenge</gold> <gray>- looking for an opponent in ").append(k.label()).append(Text.mm("<gray>...")));
                    }
                } else {
                    this.plugin.ranks().queueNormallyDefense(prof, k);
                    this.plugin.queues().join(p, k, true, false);
                }
            } else if (k != null && this.plugin.ranks().challengeReady(prof, k)) {
                if (!args[1].equalsIgnoreCase("accept") && !args[1].equalsIgnoreCase("start")) {
                    this.plugin.ranks().queueNormally(prof, k);
                    this.plugin.queues().join(p, k, true, false);
                } else if (this.plugin.ranks().startSeries(prof, k) && this.plugin.queues().join(p, k, true, false)) {
                    p.sendMessage(
                        Text.mm("<gold>⚠ Tier Test Challenge</gold> <gray>- looking for an opponent in ").append(k.label()).append(Text.mm("<gray>..."))
                    );
                }
            } else {
                Msg.error(p, "rank.no-offer");
            }
        } else {
            for (Kit k : this.plugin.kits().sorted()) {
                if ((args.length <= 0 || k.id.equalsIgnoreCase(args[0])) && this.plugin.ranks().challengeReady(prof, k)) {
                    this.plugin.dialogs().rankUpOffer(p, k);
                    return;
                }
            }

            Msg.error(p, "rank.no-offer");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        switch (name) {
            case "queue":
                if (args.length == 1) {
                    out.add("ranked");
                    out.add("unranked");
                } else if (args.length == 2) {
                    out.addAll(this.plugin.kits().ids());
                }
                break;
            case "ranked":
            case "unranked":
                if (args.length == 1) {
                    out.addAll(this.plugin.kits().ids());
                }
                break;
            case "challenge":
                if (args.length == 1) {
                    players(out);
                } else if (args.length == 2) {
                    out.addAll(this.plugin.kits().ids());
                }
                break;
            case "duel":
                if (args.length == 1) {
                    out.add("accept");
                    out.add("deny");
                    players(out);
                } else if (args.length == 2) {
                    if (!args[0].equalsIgnoreCase("accept") && !args[0].equalsIgnoreCase("deny")) {
                        out.addAll(this.plugin.kits().ids());
                    } else {
                        players(out);
                    }
                }
                break;
            case "party":
                if (args.length == 1) {
                    out.addAll(List.of("create", "invite", "accept", "join", "leave", "kick", "promote", "disband", "open", "close", "info", "games", "chat"));
                } else if (args.length == 2) {
                    players(out);
                }
                break;
            case "spectate":
            case "stats":
            case "ping":
                if (args.length == 1) {
                    players(out);
                }
                break;
            case "leaderboard":
                if (args.length == 1) {
                    out.add("overall");
                    out.addAll(this.plugin.kits().ids());
                }
                break;
            case "ffa":
                if (args.length == 1) {
                    for (FfaArena a : this.plugin.ffa().all()) {
                        out.add(a.id);
                    }
                }
                break;
            case "rankup":
                if (args.length == 1) {
                    out.addAll(this.plugin.kits().ids());
                }
        }

        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    private static void players(List<String> out) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            out.add(o.getName());
        }
    }
}
