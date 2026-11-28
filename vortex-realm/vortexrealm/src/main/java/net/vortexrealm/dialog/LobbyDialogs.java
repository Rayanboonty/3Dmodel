package net.vortexrealm.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.object.ObjectContents;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.arena.Arena;
import net.vortexrealm.board.BoardManager;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Msg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.data.Database;
import net.vortexrealm.data.PvPProfile;
import net.vortexrealm.data.Setting;
import net.vortexrealm.fx.Fx;
import net.vortexrealm.gui.menus.DuelMenu;
import net.vortexrealm.gui.menus.KitEditorMenu;
import net.vortexrealm.gui.menus.KitSelectMenu;
import net.vortexrealm.gui.menus.LeaderboardMenu;
import net.vortexrealm.gui.menus.PartyMenu;
import net.vortexrealm.gui.menus.SettingsMenu;
import net.vortexrealm.gui.menus.SpectateMenu;
import net.vortexrealm.gui.menus.StatsMenu;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.kit.KitCategory;
import net.vortexrealm.match.Match;
import net.vortexrealm.party.Party;
import net.vortexrealm.rank.Tier;
import net.vortexrealm.util.Fmt;
import net.vortexrealm.util.Icons;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Text;
import net.vortexrealm.util.TextWidth;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

public final class LobbyDialogs {
    private static final int[] ROUNDS = new int[]{1, 2, 3};
    private final VortexRealm plugin;
    private final Dialogs d;
    private static final String[] PLACE = new String[]{"<gradient:#FFE259:#FFA751>", "<gradient:#F4F4F4:#A8A8A8>", "<gradient:#E8A87C:#B26B3A>"};
    private static final String[] BADGE = new String[]{"♛", "②", "③"};

    public LobbyDialogs(VortexRealm plugin, Dialogs dialogs) {
        this.plugin = plugin;
        this.d = dialogs;
    }

    private static Component rowText(LobbyDialogs.Tile t) {
        return Component.text().append(t.name()).append(Component.newline()).append(t.line()).build();
    }

    private DialogBody row(Player p, LobbyDialogs.Tile t, int width) {
        Component hoverText = t.hover().isEmpty() ? t.name() : Component.join(JoinConfiguration.newlines(), t.hover());
        Builder b = Component.text().append(t.name()).append(Component.newline()).append(t.line()).hoverEvent(HoverEvent.showText(hoverText));
        if (t.click() != null) {
            Runnable click = this.d.safe(p, t.click());
            b.clickEvent(ClickEvent.callback(a -> click.run(), Dialogs.OPTS));
        }

        ItemStack icon = t.icon().clone();
        icon.editMeta(m -> {
            m.displayName(Text.noItalic(t.name()));
            List<Component> lore = new ArrayList<>();

            for (int i = 1; i < t.hover().size(); i++) {
                lore.add(Text.noItalic(t.hover().get(i)));
            }

            m.lore(lore);
            m.addItemFlags(ItemFlag.values());
        });
        return DialogBody.item(icon).description(DialogBody.plainMessage(b.build(), width)).showDecorations(false).showTooltip(true).build();
    }

    public void screen(
        Player p,
        Component title,
        List<LobbyDialogs.Tile> tiles,
        List<Component> header,
        Component empty,
        List<ActionButton> buttons,
        int columns,
        ActionButton exit
    ) {
        List<DialogBody> body = new ArrayList<>();

        for (Component h : header) {
            body.add(DialogBody.plainMessage(h, 300));
        }

        int width = 0;

        for (LobbyDialogs.Tile t : tiles) {
            width = Math.max(width, TextWidth.width(rowText(t)));
        }

        width = TextWidth.fit(width, 70, 280);

        for (LobbyDialogs.Tile t : tiles) {
            body.add(this.row(p, t, width));
        }

        if (tiles.isEmpty() && empty != null) {
            body.add(DialogBody.plainMessage(empty, 260));
        }

        DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false).afterAction(DialogAfterAction.NONE).body(body).build();
        if (buttons.isEmpty()) {
            Dialogs.show(p, base, DialogType.notice(exit));
        } else {
            Dialogs.show(p, base, DialogType.multiAction(buttons).columns(columns).exitAction(exit).build());
        }
    }

    public ActionButton btn(Player p, String mini, String tooltip, Runnable r) {
        return Dialogs.button(Text.mm(mini), tooltip == null ? null : Text.mm(tooltip), 150, this.d.action(p, r));
    }

    public ActionButton close(Player p) {
        return this.d.closeButton(p);
    }

    public ActionButton urlButton(String mini, String url) {
        return Dialogs.button(Text.mm(mini), Text.mm("<gray>" + Text.esc(url)), 150, DialogAction.staticAction(ClickEvent.openUrl(url)));
    }

    public static Component title(String section, String hint) {
        return Text.mm("<gold><b>" + section + "</b></gold> <dark_gray>»</dark_gray> <aqua>" + hint);
    }

    public void spectate(Player p) {
        if (!this.d.useDialog(p)) {
            new SpectateMenu(this.plugin, p).open();
        } else {
            boolean staff = this.plugin.staff().isStaff(p);
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();

            for (Match m : this.plugin.matches().active()) {
                Match.State st = m.state();
                if (m.instance() != null && st != Match.State.ENDED && st != Match.State.WAITING && st != Match.State.PREMATCH) {
                    boolean allowed = m.allowsSpectators() || staff;
                    String score = m.isOneVsOne() ? "<green>" + m.teams.get(0).score + "<dark_gray>-<red>" + m.teams.get(1).score + " <dark_gray>• " : "";
                    List<Component> hover = new ArrayList<>();
                    hover.add(Text.mm("<gold><b>" + Text.esc(m.title())));
                    hover.add(Text.mm("<gray>Kit: ").append(m.kit.label()));
                    hover.add(Text.mm("<gray>Mode: " + (m.ranked ? "<gold>Ranked" : "<green>Unranked")));
                    hover.add(Text.mm("<gray>Map: <white>" + Text.esc(m.instance().arena.displayName)));
                    hover.add(Text.mm("<gray>Spectators: <white>" + m.spectators().size()));
                    hover.add(Component.empty());
                    hover.add(Text.mm(allowed ? "<green>▶ Click to spectate" : "<red>The players disabled spectators"));
                    tiles.add(
                        new LobbyDialogs.Tile(
                            m.kit.iconStack(),
                            Text.mm("<white><b>" + Text.esc(m.title())),
                            Text.mm((m.ranked ? "<gold>Ranked" : "<green>Unranked") + " <dark_gray>• " + score + "<gray>" + Fmt.clock(m.fightSeconds())),
                            hover,
                            allowed ? () -> {
                                p.closeDialog();
                                SpectateMenu.spectate(this.plugin, p, m);
                            } : null
                        )
                    );
                }
            }

            this.screen(
                p,
                title("Spectate", "Click a match to watch it!"),
                tiles,
                List.of(),
                Text.mm("<gray>No matches right now.\n<dark_gray>Check back in a moment!"),
                List.of(this.btn(p, "<green>⟳ Refresh", null, () -> this.spectate(p))),
                1,
                this.d.closeButton(p)
            );
        }
    }

    public void leaderboards(Player p) {
        this.d.boards().open(p, "overall", 0);
    }

    public void leaderboardsClassic(Player p) {
        if (!this.d.useDialog(p)) {
            new LeaderboardMenu(this.plugin, p, null).open();
        } else {
            List<DialogBody> body = new ArrayList<>();
            body.add(DialogBody.plainMessage(Text.mm("<gray>The best players of every gamemode <dark_gray>•</dark_gray> <gray>click one for its top 10"), 300));

            for (Kit k : this.plugin.kits().sorted()) {
                if (k.enabled && k.ranked) {
                    List<Database.LbEntry> top = this.plugin.leaderboards().top(k.id);
                    int pos = this.plugin.leaderboards().position(k.id, p.getUniqueId());
                    Tier mine = this.plugin.profiles().get(p).tierIn(k.id);
                    Component text = Component.text()
                        .append(Text.mm(k.color + "<b>" + Text.esc(k.displayName.toUpperCase(Locale.ROOT)) + "</b> <dark_gray>│</dark_gray> <gray>You "))
                        .append(Text.mm(pos > 0 ? "<gold><b>#" + pos + "</b> " : "<dark_gray>- "))
                        .append((Component)(mine == null ? Component.empty() : BoardManager.tierTag(mine)))
                        .append(Component.newline())
                        .append(podium(top))
                        .build();
                    Runnable open = this.safeRun(p, () -> this.board(p, k.id));
                    Component clickable = text.clickEvent(ClickEvent.callback(a -> open.run(), Dialogs.OPTS))
                        .hoverEvent(HoverEvent.showText(Text.mm(k.color + "<b>" + Text.esc(k.displayName) + "</b>\n<green>▶ Click for the top 10")));
                    ItemStack icon = k.iconStack();
                    icon.editMeta(m -> {
                        m.displayName(Text.noItalic(Text.mm(k.color + "<b>" + Text.esc(k.displayName))));
                        m.addItemFlags(ItemFlag.values());
                    });
                    body.add(DialogBody.item(icon).description(DialogBody.plainMessage(clickable, 230)).showDecorations(false).showTooltip(false).build());
                }
            }

            List<ActionButton> buttons = new ArrayList<>();
            buttons.add(this.btn(p, "<gold>★ Overall", "<gray>Rank points summed across every gamemode", () -> this.board(p, "overall")));
            buttons.add(this.btn(p, "<green>Your Stats", "<gray>Your ranks and records", () -> {
                p.closeDialog();
                new StatsMenu(this.plugin, p, p.getUniqueId(), p.getName()).open();
            }));
            DialogBase base = DialogBase.builder(Fx.shineFrame("LEADERBOARDS", Fx.GOLD, 3.0))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogAfterAction.NONE)
                .body(body)
                .build();
            Dialogs.show(p, base, DialogType.multiAction(buttons).columns(2).exitAction(this.d.closeButton(p)).build());
        }
    }

    private Runnable safeRun(Player p, Runnable r) {
        return this.d.safe(p, r);
    }

    private static Component podium(List<Database.LbEntry> top) {
        if (top.isEmpty()) {
            return Text.mm("<dark_gray>No ranked players yet - be the first!");
        } else {
            Builder b = Component.text();

            for (int i = 0; i < Math.min(3, top.size()); i++) {
                Database.LbEntry e = top.get(i);
                if (i > 0) {
                    b.append(Component.newline());
                }

                b.append(Text.mm(PLACE[i] + "<b>" + BADGE[i] + "</b></gradient> "))
                    .append(headOf(e))
                    .append(Text.mm(" <white>" + Text.esc(e.name()) + " "))
                    .append(tag(e.tier()))
                    .append(Text.mm(" <dark_gray>" + Fmt.num(e.elo())));
            }

            return b.build();
        }
    }

    private static Component headOf(Database.LbEntry e) {
        if (!Cfg.bool(Opt.SPRITE_ICONS)) {
            return Component.empty();
        } else {
            Player online = Bukkit.getPlayer(e.uuid());
            return (Component)(online != null
                ? Icons.head(online)
                : Component.object(ObjectContents.playerHead().id(e.uuid()).name(e.name()).hat(true).build()));
        }
    }

    private static Component tag(int tierIndex) {
        Tier t = Tier.byIndex(tierIndex);
        return (Component)(t == null ? Component.empty() : BoardManager.tierTag(t));
    }

    public void board(Player p, String kitId) {
        this.d.boards().open(p, kitId, 0);
    }

    public void boardClassic(Player p, String kitId) {
        boolean overall = "overall".equals(kitId);
        Kit kit = overall ? null : this.plugin.kits().get(kitId);
        List<Database.LbEntry> list = overall ? this.plugin.leaderboards().overall() : this.plugin.leaderboards().top(kitId);
        String name = overall ? "Overall" : (kit == null ? kitId : kit.displayName);
        List<DialogBody> body = new ArrayList<>();
        if (kit != null) {
            body.add(
                DialogBody.item(kit.iconStack())
                    .description(DialogBody.plainMessage(Text.mm(kit.color + "<b>" + Text.esc(name.toUpperCase(Locale.ROOT)) + "</b>\n<gray>Top players"), 150))
                    .showDecorations(false)
                    .showTooltip(false)
                    .build()
            );
        }

        if (list.isEmpty()) {
            body.add(DialogBody.plainMessage(Text.mm("<gray>No ranked players yet.\n<dark_gray>Play ranked to get on the board!"), 260));
        }

        for (int i = 0; i < Math.min(10, list.size()); i++) {
            Database.LbEntry e = list.get(i);
            int total = e.wins() + e.losses();
            String rate = total == 0 ? "-" : Fmt.percent((double)e.wins() / total);
            Component row;
            if (i == 0) {
                row = Component.text()
                    .append(Text.mm(PLACE[0] + "<b>♛ CHAMPION ♛</b></gradient>"))
                    .append(Component.newline())
                    .append(headOf(e))
                    .append(Text.mm(" <white><b>" + Text.esc(e.name()) + "</b> "))
                    .append(tag(e.tier()))
                    .append(Component.newline())
                    .append(
                        Text.mm(
                            "<gold>"
                                + Fmt.num(e.elo())
                                + " pts <dark_gray>•</dark_gray> <green>"
                                + e.wins()
                                + "W <red>"
                                + e.losses()
                                + "L <dark_gray>•</dark_gray> <gray>"
                                + rate
                        )
                    )
                    .build();
            } else {
                String color = i < 3 ? PLACE[i] + "<b>#" + (i + 1) + "</b></gradient>" : "<gray><b>#" + (i + 1) + "</b>";
                row = Component.text()
                    .append(Text.mm(color + " "))
                    .append(headOf(e))
                    .append(Text.mm(" <white>" + Text.esc(e.name()) + " "))
                    .append(tag(e.tier()))
                    .append(Text.mm(" <dark_gray>— <gray>" + Fmt.num(e.elo()) + " pts"))
                    .build();
            }

            Runnable stats = this.safeRun(p, () -> {
                p.closeDialog();
                new StatsMenu(this.plugin, p, e.uuid(), e.name()).open();
            });
            row = row.clickEvent(ClickEvent.callback(a -> stats.run(), Dialogs.OPTS))
                .hoverEvent(
                    HoverEvent.showText(
                        Text.mm(
                            "<white><b>"
                                + Text.esc(e.name())
                                + "</b>\n<gray>"
                                + Fmt.num(e.elo())
                                + " pts • "
                                + e.wins()
                                + "W "
                                + e.losses()
                                + "L • "
                                + rate
                                + "\n<green>▶ Click for full stats"
                        )
                    )
                );
            body.add(DialogBody.plainMessage(row, 300));
        }

        int pos = overall ? 0 : this.plugin.leaderboards().position(kitId, p.getUniqueId());
        if (pos > 0) {
            body.add(DialogBody.plainMessage(Text.mm("<dark_gray>───────────</dark_gray>\n<gray>Your position: <gold><b>#" + pos), 260));
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(this.btn(p, "<gold>Full Leaderboard", "<gray>Top 100 with pages", () -> {
            p.closeDialog();
            new LeaderboardMenu(this.plugin, p, kitId).open();
        }));
        buttons.add(this.btn(p, "<gray>« All Gamemodes", null, () -> this.leaderboards(p)));
        DialogBase base = DialogBase.builder(Fx.shineFrame(name.toUpperCase(Locale.ROOT) + " TOP 10", Fx.GOLD, 2.0))
            .canCloseWithEscape(true)
            .pause(false)
            .afterAction(DialogAfterAction.NONE)
            .body(body)
            .build();
        Dialogs.show(p, base, DialogType.multiAction(buttons).columns(2).exitAction(this.d.closeButton(p)).build());
    }

    public void settings(Player p, Setting.Category tab) {
        if (!this.d.useDialog(p)) {
            new SettingsMenu(this.plugin, p, tab).open();
        } else {
            PvPProfile prof = this.plugin.profiles().get(p);
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();

            for (Setting s : Setting.values()) {
                if (s.category == tab) {
                    String current = prof.get(s);
                    String currentLabel = current;
                    List<Component> hover = new ArrayList<>();
                    hover.add(Text.mm("<gold><b>" + s.title));
                    hover.add(Text.mm("<gray>" + Text.esc(s.description)));
                    hover.add(Component.empty());
                    int shown = 0;

                    for (Setting.Option o : SettingsMenu.options(this.plugin, s)) {
                        boolean sel = o.id().equals(current);
                        if (sel) {
                            currentLabel = o.label();
                        }

                        if (shown++ < 10) {
                            hover.add(Text.mm(sel ? "<green><b>▶ " + Text.esc(o.label()) : "<gray>  " + Text.esc(o.label())));
                        }
                    }

                    hover.add(Component.empty());
                    hover.add(Text.mm("<green>▶ Click for the next option"));
                    boolean off = "off".equals(current) || "none".equals(current) || "nobody".equals(current) || "hide".equals(current);
                    tiles.add(
                        new LobbyDialogs.Tile(
                            new ItemStack(s.icon),
                            Text.mm("<gold><b>" + s.title),
                            Text.mm((off ? "<red>" : "<green>") + Text.esc(currentLabel)),
                            hover,
                            () -> {
                                if (SettingsMenu.apply(this.plugin, p, s, SettingsMenu.step(this.plugin, prof, s, 1))) {
                                    Snd.click(p);
                                }

                                this.settings(p, tab);
                            }
                        )
                    );
                }
            }

            List<ActionButton> tabs = new ArrayList<>();

            for (Setting.Category c : Setting.Category.values()) {
                if (c.hidden) {
                    continue;
                }

                String label = c == tab ? "<white><b><i>" + c.title + "</i></b>" : "<gold>" + c.title;
                tabs.add(this.btn(p, label, "<gray>Show " + c.title + " settings", () -> this.settings(p, c)));
            }

            this.screen(p, title("Settings", "Click a setting to change it!"), tiles, List.of(), null, tabs, 2, this.d.closeButton(p));
        }
    }

    public void kitEditor(Player p) {
        if (!this.d.useDialog(p)) {
            KitSelectMenu.openEditor(this.plugin, p);
        } else {
            PvPProfile prof = this.plugin.profiles().get(p);
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();

            for (KitCategory cat : KitCategory.values()) {
                for (Kit k : this.plugin.kits().sorted()) {
                    if (k.category == cat && k.enabled && k.hasContents()) {
                        boolean custom = this.plugin.kits().usable(k, prof.layouts.get(k.id));
                        List<String> modes = new ArrayList<>();
                        if (k.unranked) {
                            modes.add("Duels");
                        }

                        if (k.ranked) {
                            modes.add("Ranked");
                        }

                        if (k.ffa) {
                            modes.add("FFA");
                        }

                        modes.add("Events");
                        String where = String.join(" <dark_gray>·</dark_gray> ", modes);
                        List<Component> hover = new ArrayList<>();
                        hover.add(Text.mm(k.color + "<b>" + Text.esc(k.displayName)));
                        hover.add(Text.mm(cat.color + cat.title));

                        for (String line : k.description) {
                            hover.add(Text.mm("<gray>" + Text.esc(line)));
                        }

                        hover.add(Component.empty());
                        hover.add(Text.mm(custom ? "<#FFB547>✎ Your own layout - used in " + where : "<gray>The kit's default layout - used in " + where));
                        hover.add(Text.mm("<green>▶ Click to " + (custom ? "change" : "make") + " your layout"));
                        ItemStack icon = k.iconStack();
                        if (custom) {
                            icon.editMeta(m -> m.setEnchantmentGlintOverride(true));
                        }

                        Component name = custom
                            ? Text.mm("<#FFB547>✎ </#FFB547>" + k.color + "<b>" + Text.esc(k.displayName) + "</b> <#FFD27A><b>YOUR LAYOUT")
                            : Text.mm(k.color + "<b>" + Text.esc(k.displayName));
                        Component line = Text.mm((custom ? "<#FFD27A>" : "<dark_gray>default · <gray>") + where);
                        tiles.add(new LobbyDialogs.Tile(icon, name, line, hover, () -> {
                            p.closeDialog();
                            KitEditorMenu.start(this.plugin, p, k);
                        }));
                    }
                }
            }

            List<ActionButton> buttons = List.of(this.btn(p, "<red>Reset Layouts", "<gray>Shift-click a kit there to reset it", () -> {
                p.closeDialog();
                KitSelectMenu.openEditor(this.plugin, p);
            }));
            this.screen(
                p, title("Kit Editor", "Click a kit to arrange it!"), tiles, List.of(), Text.mm("<gray>No kits to edit."), buttons, 1, this.d.closeButton(p)
            );
        }
    }

    public void duel(Player p, Player target, int roundsIndex, int mapIndex) {
        if (!this.d.useDialog(p)) {
            new DuelMenu(this.plugin, p, target).open();
        } else if (!target.isOnline()) {
            p.closeDialog();
            Msg.error(p, "general.player-offline", "player", target.getName());
        } else {
            List<Arena> maps = new ArrayList<>();

            for (Arena a : this.plugin.arenas().all()) {
                if (a.ready()) {
                    maps.add(a);
                }
            }

            int mi = mapIndex > maps.size() ? 0 : mapIndex;
            Arena map = mi == 0 ? null : maps.get(mi - 1);
            int forced = ROUNDS[roundsIndex];
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();

            for (KitCategory cat : KitCategory.values()) {
                for (Kit k : this.plugin.kits().sorted()) {
                    if (k.category == cat && k.enabled && k.hasContents() && !this.plugin.arenas().forKit(k).isEmpty()) {
                        boolean mapOk = map == null || map.allows(k);
                        List<Component> hover = new ArrayList<>();
                        hover.add(Text.mm(k.color + "<b>" + Text.esc(k.displayName)));

                        for (String line : k.description) {
                            hover.add(Text.mm("<gray>" + Text.esc(line)));
                        }

                        hover.add(Component.empty());
                        hover.add(Text.mm(mapOk ? "<green>▶ Click to send the duel" : "<red>This map doesn't allow this kit"));
                        tiles.add(
                            new LobbyDialogs.Tile(
                                k.iconStack(),
                                Text.mm(k.color + "<b>" + Text.esc(k.displayName)),
                                Text.mm("<gray>First to <gold>" + forced),
                                hover,
                                mapOk ? () -> {
                                    p.closeDialog();
                                    this.plugin.duels().send(p, target, k, forced, map);
                                } : null
                            )
                        );
                    }
                }
            }

            String rLabel = "First to " + forced;
            List<ActionButton> buttons = new ArrayList<>();
            buttons.add(
                this.btn(p, "<gold>Rounds: <white>" + rLabel, "<gray>Click to change", () -> this.duel(p, target, (roundsIndex + 1) % ROUNDS.length, mi))
            );
            buttons.add(
                this.btn(
                    p,
                    "<gold>Map: <white>" + (map == null ? "Random" : Text.esc(map.displayName)),
                    "<gray>Click for the next map",
                    () -> this.duel(p, target, roundsIndex, (mi + 1) % (maps.size() + 1))
                )
            );
            this.screen(
                p,
                title("Duel " + Text.esc(target.getName()), "Click a kit to send the request!"),
                tiles,
                List.of(),
                Text.mm("<gray>No kits with arenas yet."),
                buttons,
                2,
                this.d.closeButton(p)
            );
        }
    }

    public void party(Player p) {
        Party party = this.plugin.parties().of(p);
        if (this.d.useDialog(p) && party != null) {
            boolean leader = party.isLeader(p);
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();

            for (UUID u : party.members) {
                Player m = Bukkit.getPlayer(u);
                if (m != null) {
                    boolean isLeader = u.equals(party.leader);
                    List<Component> hover = new ArrayList<>();
                    hover.add(Text.mm("<white><b>" + Text.esc(m.getName())));
                    hover.add(Text.mm(isLeader ? "<gold>Party leader" : "<gray>Member"));
                    if (leader && !isLeader) {
                        hover.add(Component.empty());
                        hover.add(Text.mm("<green>▶ Click to manage"));
                    }

                    tiles.add(
                        new LobbyDialogs.Tile(
                            Items.of(Material.PLAYER_HEAD).skull(m).build(),
                            Text.mm((isLeader ? "<gold><b>♛ " : "<white><b>") + Text.esc(m.getName())),
                            Text.mm(isLeader ? "<gold>Leader" : "<gray>Member").append(Text.mm(" <dark_gray>• " + Fmt.pingColored(m.getPing()))),
                            hover,
                            leader && !isLeader ? () -> this.member(p, m) : null
                        )
                    );
                }
            }

            List<ActionButton> buttons = new ArrayList<>();
            if (leader) {
                buttons.add(this.btn(p, "<gold>⚔ Party Games", "<gray>Party Split or Party FFA", () -> this.partyGames(p, false)));
                buttons.add(this.btn(p, "<green>+ Invite", "<gray>Invite a player", () -> {
                    p.closeDialog();
                    PartyMenu.invitePrompt(this.plugin, p);
                }));
                buttons.add(
                    this.btn(
                        p, party.open ? "<green>Open ✓" : "<gray>Closed", "<gray>Open parties can be joined with /party join " + Text.esc(p.getName()), () -> {
                            this.plugin.parties().setOpen(p, !party.open);
                            this.party(p);
                        }
                    )
                );
                buttons.add(this.btn(p, "<red>Disband", "<gray>Remove everyone", () -> {
                    p.closeDialog();
                    this.plugin.parties().disband(party);
                }));
            }

            buttons.add(this.btn(p, "<red>Leave Party", null, () -> {
                p.closeDialog();
                this.plugin.parties().leave(p);
            }));
            this.screen(
                p,
                title("Party", party.members.size() + " member" + (party.members.size() == 1 ? "" : "s")),
                tiles,
                List.of(),
                null,
                buttons,
                2,
                this.d.closeButton(p)
            );
        } else {
            new PartyMenu(this.plugin, p).open();
        }
    }

    private void member(Player leader, Player m) {
        List<ActionButton> buttons = List.of(this.btn(leader, "<gold>♛ Make Leader", null, () -> {
            this.plugin.parties().promote(leader, m);
            this.party(leader);
        }), this.btn(leader, "<red>Kick", null, () -> {
            this.plugin.parties().kick(leader, m);
            this.party(leader);
        }));
        this.screen(
            leader,
            title("Party", Text.esc(m.getName())),
            List.of(),
            List.of(),
            null,
            buttons,
            2,
            this.btn(leader, "<gray>« Back", null, () -> this.party(leader))
        );
    }

    public void partyGames(Player p, boolean ffa) {
        Party party = this.plugin.parties().of(p);
        if (party != null && party.isLeader(p)) {
            if (!this.d.useDialog(p)) {
                PartyMenu.openGames(this.plugin, p);
            } else {
                List<LobbyDialogs.Tile> tiles = new ArrayList<>();

                for (KitCategory cat : KitCategory.values()) {
                    for (Kit k : this.plugin.kits().sorted()) {
                        if (k.category == cat && k.enabled && k.hasContents() && !this.plugin.arenas().forKit(k).isEmpty()) {
                            tiles.add(
                                new LobbyDialogs.Tile(
                                    k.iconStack(),
                                    Text.mm(k.color + "<b>" + Text.esc(k.displayName)),
                                    Text.mm("<gray>" + (ffa ? "Party FFA" : "Party Split")),
                                    List.of(Text.mm(k.color + "<b>" + Text.esc(k.displayName)), Component.empty(), Text.mm("<green>▶ Click to start")),
                                    () -> {
                                        p.closeDialog();
                                        this.plugin.parties().startGame(p, k, ffa ? Match.Type.PARTY_FFA : Match.Type.PARTY_SPLIT);
                                    }
                                )
                            );
                        }
                    }
                }

                List<ActionButton> modes = List.of(
                    this.btn(p, ffa ? "<gold>Party Split" : "<white><b><i>Party Split</i></b>", "<gray>Two teams", () -> this.partyGames(p, false)),
                    this.btn(p, ffa ? "<white><b><i>Party FFA</i></b>" : "<gold>Party FFA", "<gray>Everyone vs everyone", () -> this.partyGames(p, true))
                );
                this.screen(
                    p,
                    title("Party Games", "Pick a mode, then click a kit!"),
                    tiles,
                    List.of(),
                    Text.mm("<gray>No kits with arenas yet."),
                    modes,
                    2,
                    this.btn(p, "<gray>« Back", null, () -> this.party(p))
                );
            }
        } else {
            Msg.error(p, "party.not-leader");
        }
    }

    public record Tile(ItemStack icon, Component name, Component line, List<Component> hover, Runnable click) {
    }
}
