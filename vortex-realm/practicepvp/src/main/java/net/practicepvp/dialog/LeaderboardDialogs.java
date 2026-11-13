package net.practicepvp.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.object.ObjectContents;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.data.Database;
import net.practicepvp.data.KitStats;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.gui.menus.LeaderboardMenu;
import net.practicepvp.gui.menus.StatsMenu;
import net.practicepvp.kit.Kit;
import net.practicepvp.rank.Tier;
import net.practicepvp.util.ClientVersion;
import net.practicepvp.util.Fmt;
import net.practicepvp.util.Icons;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class LeaderboardDialogs {
    private static final int PAGE = 10;
    private final Dialogs d;

    LeaderboardDialogs(Dialogs d) {
        this.d = d;
    }

    private PracticePlugin plugin() {
        return this.d.plugin();
    }

    private List<Kit> boards() {
        List<Kit> out = new ArrayList<>();

        for (Kit k : this.plugin().kits().sorted()) {
            if (k.enabled && k.ranked) {
                out.add(k);
            }
        }

        return out;
    }

    private List<Kit> mainBoards() {
        List<Kit> all = this.boards();
        List<Kit> out = new ArrayList<>();

        for (String id : Cfg.get(Opt.LB_MAIN_KITS)) {
            for (Kit k : all) {
                if (k.id.equalsIgnoreCase(id.trim()) && !out.contains(k)) {
                    out.add(k);
                }
            }
        }

        if (out.isEmpty()) {
            out.addAll(all.subList(0, Math.min(8, all.size())));
        }

        return out;
    }

    private List<Kit> moreBoards() {
        List<Kit> main = this.mainBoards();
        List<Kit> out = new ArrayList<>();

        for (Kit k : this.boards()) {
            if (!main.contains(k)) {
                out.add(k);
            }
        }

        return out;
    }

    private void more(Player p, UUID profileOf, String name) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (Kit k : this.moreBoards()) {
            int n = this.plugin().leaderboards().top(k.id).size();
            tiles.add(
                new LobbyDialogs.Tile(
                    k.iconStack(),
                    Text.mm(k.color + "<b>" + Text.esc(k.displayName)),
                    Text.mm("<gray>" + n + " ranked"),
                    List.of(Text.mm(k.color + "<b>" + Text.esc(k.displayName)), Component.empty(), Text.mm("<green>▶ Click to open")),
                    () -> {
                        if (profileOf != null) {
                            this.profile(p, profileOf, name, k.id);
                        } else {
                            this.open(p, k.id, 0);
                        }
                    }
                )
            );
        }

        LobbyDialogs ui = this.plugin().dialogs().lobby();
        ui.screen(p, LobbyDialogs.title("More Gamemodes", tiles.size() + " more kits"), tiles, List.of(), null, List.of(ui.btn(p, "<gray>« Back", null, () -> {
            if (profileOf != null) {
                this.profile(p, profileOf, name, "overall");
            } else {
                this.open(p, "overall", 0);
            }
        })), 1, ui.close(p));
    }

    private static Component head(UUID u, String name) {
        if (!Cfg.bool(Opt.SPRITE_ICONS)) {
            return Component.empty();
        } else {
            Player online = Bukkit.getPlayer(u);
            return (Component)(online != null ? Icons.head(online) : Component.object(ObjectContents.playerHead().id(u).name(name).hat(true).build()));
        }
    }

    private static String color(int position, int tier) {
        return switch (position) {
            case 1 -> "<#FF4A4A>";
            case 2 -> "<#FF6A3D>";
            case 3 -> "<#FF9A3D>";
            default -> {
                Tier t = Tier.byIndex(tier);
                yield t == null ? "<#B07CFF>" : "<" + t.color + ">";
            }
        };
    }

    private String boardName(String board) {
        if ("overall".equals(board)) {
            return "Overall";
        } else {
            Kit k = this.plugin().kits().get(board);
            return k == null ? board : k.displayName;
        }
    }

    private Component boardLabel(String board) {
        if ("overall".equals(board)) {
            return Text.mm("<aqua>Overall");
        } else {
            Kit k = this.plugin().kits().get(board);
            return k == null
                ? Text.mm("<gray>" + Text.esc(board))
                : k.iconComponent().append((Component)Component.space()).append(Text.mm(k.color + Text.esc(k.displayName)));
        }
    }

    public void open(Player p, String board, int page) {
        if (this.plugin().welcome() != null) {
            this.plugin().welcome().done(p, "LEADERBOARD");
        }

        if (!this.d.useDialog(p)) {
            new LeaderboardMenu(this.plugin(), p, "overall".equals(board) ? null : board).open();
        } else {
            boolean overall = "overall".equals(board);
            List<Database.LbEntry> list = overall ? this.plugin().leaderboards().overall() : this.plugin().leaderboards().top(board);
            int pages = Math.max(1, (list.size() + 10 - 1) / 10);
            int pg = Math.max(0, Math.min(pages - 1, page));
            PvPProfile mine = this.plugin().profiles().get(p);
            int myPoints = overall ? sum(mine) : (mine.statsOrNull(board) == null ? -1 : mine.statsOrNull(board).elo);
            boolean ranked = overall ? myPoints > 0 : mine.statsOrNull(board) != null && mine.statsOrNull(board).ranked();
            int cached = -1;

            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).uuid().equals(p.getUniqueId())) {
                    cached = i + 1;
                }
            }

            if (cached <= 0 && ranked) {
                this.plugin()
                    .db()
                    .supply(c -> overall ? this.plugin().db().rankOverall(c, myPoints) : this.plugin().db().rankOf(c, board, myPoints))
                    .whenComplete((r, err) -> Tasks.ensureSync(() -> {
                        if (p.isOnline()) {
                            this.show(p, board, pg, pages, list, err == null && r != null ? r[0] : -1, myPoints);
                        }
                    }));
            } else {
                this.show(p, board, pg, pages, list, ranked ? cached : -1, myPoints);
            }
        }
    }

    private static int sum(PvPProfile prof) {
        int s = 0;

        for (KitStats k : prof.kits.values()) {
            if (k.ranked()) {
                s += k.elo;
            }
        }

        return s;
    }

    private void show(Player p, String board, int page, int pages, List<Database.LbEntry> list, int myPos, int myPoints) {
        List<DialogBody> body = new ArrayList<>();
        int from = page * 10;
        if (list.isEmpty()) {
            body.add(DialogBody.plainMessage(Text.mm("<gray>Nobody is ranked here yet - be the first!"), 400));
        }

        for (int i = from; i < Math.min(list.size(), from + 10); i++) {
            Database.LbEntry e = list.get(i);
            int pos = i + 1;
            String c = color(pos, e.tier());
            boolean me = e.uuid().equals(p.getUniqueId());
            Component row = Text.mm(c + (pos <= 3 ? "<b>" : "") + "#" + pos + (pos <= 3 ? "</b>" : "") + " ")
                .append(head(e.uuid(), e.name()))
                .append(Text.mm(" " + c + Text.esc(e.name()) + (me ? " <green>✔" : "") + " <dark_gray>-</dark_gray> <white>" + Fmt.num(e.elo()) + " <gray>pts"));
            int total = e.wins() + e.losses();
            String rate = total == 0 ? "-" : Fmt.percent((double)e.wins() / total);
            Tier t = Tier.byIndex(e.tier());
            Runnable open = this.d.safe(p, () -> this.profile(p, e.uuid(), e.name(), board));
            row = row.clickEvent(ClickEvent.callback(a -> open.run(), Dialogs.OPTS))
                .hoverEvent(
                    HoverEvent.showText(
                        Text.mm(
                            "<white><b>"
                                + Text.esc(e.name())
                                + "</b>\n<gray>"
                                + Fmt.num(e.elo())
                                + " pts"
                                + (t == null ? "" : " • " + t.mm() + "<gray>")
                                + " • <green>"
                                + e.wins()
                                + "W <red>"
                                + e.losses()
                                + "L <gray>• "
                                + rate
                                + "\n<green>▶ Click for the profile"
                        )
                    )
                );
            body.add(DialogBody.plainMessage(row, 400));
        }

        if (myPos > 0) {
            body.add(
                DialogBody.plainMessage(
                    Text.mm("<gray>#" + Fmt.num(myPos) + " ")
                        .append(head(p.getUniqueId(), p.getName()))
                        .append(
                            Text.mm(
                                " <gray>"
                                    + Text.esc(p.getName())
                                    + " <green>✔</green> <dark_gray>-</dark_gray> <gray>"
                                    + Fmt.num(Math.max(0, myPoints))
                                    + " pts"
                            )
                        ),
                    400
                )
            );
        } else {
            body.add(DialogBody.plainMessage(Text.mm("<dark_gray>You're not ranked here yet."), 400));
        }

        List<DialogInput> inputs = List.of(DialogInput.text("search", Text.mm("<aqua>Search")).initial("").maxLength(16).width(330).build());
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(
            Dialogs.button(
                Text.mm(page > 0 ? "<white><" : "<dark_gray><"), Text.mm("<gray>Previous page"), 110, this.d.action(p, () -> this.open(p, board, page - 1))
            )
        );
        buttons.add(Dialogs.button(Text.mm("<green>Search"), Text.mm("<gray>Search this leaderboard"), 150, this.d.viewAction(p, view -> {
            String q = view.getText("search");
            this.search(p, board, q == null ? "" : q.trim());
        })));
        buttons.add(
            Dialogs.button(
                Text.mm(page + 1 < pages ? "<white>>" : "<dark_gray>>"), Text.mm("<gray>Next page"), 110, this.d.action(p, () -> this.open(p, board, page + 1))
            )
        );
        List<String> ids = new ArrayList<>();

        for (Kit k : this.mainBoards()) {
            ids.add(k.id);
        }

        int overallAt = Math.min(ids.size(), 4);
        ids.add(overallAt, "overall");
        boolean onMore = false;

        for (Kit k : this.moreBoards()) {
            onMore |= k.id.equals(board);
        }

        for (String id : ids) {
            boolean sel = id.equals(board);
            Component label = this.boardLabel(id);
            if (sel) {
                label = Text.mm("<white><b><i>").append(label);
            }

            buttons.add(Dialogs.button(label, Text.mm("<gray>Top players: " + Text.esc(this.boardName(id))), 150, this.d.action(p, () -> this.open(p, id, 0))));
        }

        if (!this.moreBoards().isEmpty()) {
            buttons.add(
                Dialogs.button(
                    Text.mm(onMore ? "<white><b><i>" + Text.esc(this.boardName(board)) + " ▸" : "<light_purple>More Gamemodes ▸"),
                    Text.mm("<gray>" + this.moreBoards().size() + " more ranked kits"),
                    150,
                    this.d.action(p, () -> this.more(p, null, null))
                )
            );
        }

        String range = page == 0 ? "Top 10" : "Top " + (from + 1) + "-" + (from + 10);
        DialogBase base = DialogBase.builder(Text.mm("<aqua>" + range + " " + Text.esc(this.boardName(board))))
            .canCloseWithEscape(true)
            .pause(false)
            .afterAction(DialogAfterAction.NONE)
            .body(body)
            .inputs(inputs)
            .build();
        Dialogs.show(
            p,
            base,
            DialogType.multiAction(buttons).columns(3).exitAction(Dialogs.button(Text.mm("<red>Close"), null, 200, this.d.action(p, p::closeDialog))).build()
        );
    }

    private void search(Player p, String board, String q) {
        if (q.isEmpty()) {
            this.open(p, board, 0);
        } else {
            boolean overall = "overall".equals(board);
            List<Database.LbEntry> list = overall ? this.plugin().leaderboards().overall() : this.plugin().leaderboards().top(board);

            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).name().equalsIgnoreCase(q)) {
                    this.profile(p, list.get(i).uuid(), list.get(i).name(), board);
                    return;
                }
            }

            this.plugin().db().supply(c -> this.plugin().db().findUuid(c, q)).whenComplete((u, err) -> Tasks.ensureSync(() -> {
                if (p.isOnline()) {
                    if (u == null) {
                        p.sendMessage(Text.mm("<red>No player called '" + Text.esc(q) + "' has played here."));
                        this.open(p, board, 0);
                    } else {
                        this.profile(p, u, q, board);
                    }
                }
            }));
        }
    }

    public void profile(Player viewer, UUID target, String name, String board) {
        Player online = Bukkit.getPlayer(target);
        CompletableFuture<PvPProfile> prof = online != null
            ? CompletableFuture.completedFuture(this.plugin().profiles().get(online))
            : this.plugin().db().supply(c -> this.plugin().db().loadProfile(c, target, name));
        prof.whenComplete(
            (pr, err) -> Tasks.ensureSync(
                () -> {
                    if (viewer.isOnline()) {
                        if (err == null && pr != null) {
                            boolean overall = "overall".equals(board);
                            int points = overall ? sum(pr) : (pr.statsOrNull(board) == null ? 0 : pr.statsOrNull(board).elo);
                            this.plugin()
                                .db()
                                .supply(
                                    c -> new Object[]{
                                        overall ? this.plugin().db().rankOverall(c, points) : this.plugin().db().rankOf(c, board, points),
                                        this.plugin().db().rival(c, target, overall ? null : board)
                                    }
                                )
                                .whenComplete((res, err2) -> Tasks.ensureSync(() -> {
                                    if (viewer.isOnline()) {
                                        this.showProfile(viewer, pr, name, board, err2 == null ? (int[])res[0] : null, err2 == null ? (Object[])res[1] : null);
                                    }
                                }));
                        } else {
                            viewer.sendMessage(Text.mm("<red>Could not load that profile."));
                        }
                    }
                }
            )
        );
    }

    private void showProfile(Player p, PvPProfile pr, String name, String board, int[] rank, Object[] rival) {
        if (!ClientVersion.supportsDialogs(p)) {
            new StatsMenu(this.plugin(), p, pr.uuid, name).open();
        } else {
            boolean overall = "overall".equals(board);
            int wins = 0;
            int losses = 0;
            int rw = 0;
            int rl = 0;
            int cw = 0;
            int cl = 0;
            int points = 0;
            int best = 0;
            int peak = 0;
            Tier tier = null;

            for (Entry<String, KitStats> e : pr.kits.entrySet()) {
                if (overall || e.getKey().equals(board)) {
                    KitStats k = e.getValue();
                    wins += k.wins + k.unrankedWins;
                    losses += k.losses + k.unrankedLosses;
                    rw += k.roundsWon;
                    rl += k.roundsLost;
                    cw += k.challengeWins;
                    cl += k.challengeLosses;
                    if (k.ranked()) {
                        points += k.elo;
                    }

                    best = Math.max(best, k.bestStreak);
                    peak = Math.max(peak, k.peakElo);
                    if (k.tier() != null && (tier == null || k.tier().ordinal() > tier.ordinal())) {
                        tier = k.tier();
                    }
                }
            }

            List<DialogBody> body = new ArrayList<>();
            KitStats one = overall ? null : pr.statsOrNull(board);
            int need = Cfg.i(Opt.PLACEMENT_MATCHES);
            if (!overall && (one == null || !one.ranked()) && need > 0) {
                int done = one == null ? 0 : Math.min(need, one.rankedGames);
                StringBuilder bar = new StringBuilder();

                for (int i = 0; i < need; i++) {
                    bar.append(i < done ? "<#FFB800>■" : "<dark_gray>■");
                }

                body.add(line("<gray>Status: <gold><b>Placement Matches"));
                body.add(line("<gray>Progress: <aqua>" + done + " / " + need + "  " + bar));
                body.add(
                    line(
                        "<white>Play "
                            + (need - done)
                            + " more placement match"
                            + (need - done == 1 ? "" : "es")
                            + " to earn your first rank.\n<gray>Tier Rating is hidden until placements are done."
                    )
                );
            }

            body.add(line("<gray>Match Count: <white>" + Fmt.num(wins + losses)));
            body.add(line("<gray>Wins/Losses: <white>" + wins + "-" + losses + " <gray>(" + pct(wins, losses) + ")"));
            body.add(line("<gray>Round Wins/Losses: <white>" + rw + "-" + rl + " <gray>(" + pct(rw, rl) + ")"));
            body.add(line("<gray>Challenge Record: <white>" + cw + "-" + cl + " <gray>(" + pct(cw, cl) + ")"));
            body.add(
                DialogBody.plainMessage(
                    Text.mm("<gray>Tier: ")
                        .append(tier == null ? Text.mm("<white>Unranked") : Text.mm(tier.mm()))
                        .append(Text.mm(" <dark_gray>• <gray>Peak <white>" + Fmt.num(peak) + " <dark_gray>• <gray>Best streak <gold>" + best)),
                    400
                )
            );
            String sor = rank != null && points > 0
                ? "<aqua>#" + Fmt.num(rank[0]) + "/" + Fmt.num(rank[1]) + " <dark_gray>- <white>" + Fmt.num(points)
                : "<dark_gray>not ranked yet";
            body.add(line("<gray>Strength of Record: " + sor));
            body.add(
                line(
                    "<gray>Rival: "
                        + (
                            rival == null
                                ? "<white>None yet"
                                : "<white>"
                                    + Text.esc((String)rival[0])
                                    + " <dark_gray>("
                                    + rival[2]
                                    + "-"
                                    + ((Integer)rival[1] - (Integer)rival[2])
                                    + " in "
                                    + rival[1]
                                    + " games)"
                        )
                )
            );
            List<String> ids = new ArrayList<>();
            ids.add("overall");

            for (Kit kx : this.mainBoards()) {
                ids.add(kx.id);
            }

            if (!ids.contains(board)) {
                ids.add(board);
            }

            int at = Math.max(0, ids.indexOf(board));
            String prev = ids.get((at - 1 + ids.size()) % ids.size());
            String next = ids.get((at + 1) % ids.size());
            List<ActionButton> buttons = new ArrayList<>();
            buttons.add(
                Dialogs.button(
                    Text.mm("<white><"), Text.mm("<gray>").append(this.boardLabel(prev)), 110, this.d.action(p, () -> this.profile(p, pr.uuid, name, prev))
                )
            );
            buttons.add(Dialogs.button(this.boardLabel(board), Text.mm("<gray>Back to the leaderboard"), 150, this.d.action(p, () -> this.open(p, board, 0))));
            buttons.add(
                Dialogs.button(
                    Text.mm("<white>>"), Text.mm("<gray>").append(this.boardLabel(next)), 110, this.d.action(p, () -> this.profile(p, pr.uuid, name, next))
                )
            );

            for (String id : ids) {
                if (!id.equals("overall")) {
                    Component label = this.boardLabel(id);
                    if (id.equals(board)) {
                        label = Text.mm("<white><b><i>").append(label);
                    }

                    buttons.add(Dialogs.button(label, null, 150, this.d.action(p, () -> this.profile(p, pr.uuid, name, id))));
                }
            }

            if (!this.moreBoards().isEmpty()) {
                buttons.add(Dialogs.button(Text.mm("<light_purple>More Gamemodes ▸"), null, 150, this.d.action(p, () -> this.more(p, pr.uuid, name))));
            }

            Component title = head(pr.uuid, name).append(Text.mm(" <white><b>" + Text.esc(name) + "</b> <dark_gray>- ")).append(this.boardLabel(board));
            DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false).afterAction(DialogAfterAction.NONE).body(body).build();
            Dialogs.show(
                p,
                base,
                DialogType.multiAction(buttons)
                    .columns(3)
                    .exitAction(Dialogs.button(Text.mm("<red>Close"), null, 200, this.d.action(p, p::closeDialog)))
                    .build()
            );
        }
    }

    private static DialogBody line(String mini) {
        return DialogBody.plainMessage(Text.mm(mini), 400);
    }

    private static String pct(int a, int b) {
        int t = a + b;
        return t == 0 ? "0.0%" : String.format(Locale.US, "%.1f%%", a * 100.0 / t);
    }
}
