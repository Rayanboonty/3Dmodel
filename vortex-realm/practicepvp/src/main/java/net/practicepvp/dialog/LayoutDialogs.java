package net.practicepvp.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.lobby.Hotbar;
import net.practicepvp.lobby.MainMenu;
import net.practicepvp.session.PlayerState;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 1.0.18: /pvpadmin -> Layout & Placement. Everything players see can be moved and changed from here: the /menu
 * buttons (order, shown or hidden, columns), the lobby hotbar (slot, item, name, on/off for every layout), the
 * scoreboard (title, colours, which lines show) and where holograms, leaderboards, NPCs and decorations stand.
 */
public final class LayoutDialogs {
    private final PracticePlugin plugin;
    private final Dialogs d;

    public LayoutDialogs(PracticePlugin plugin, Dialogs d) {
        this.plugin = plugin;
        this.d = d;
    }

    private ActionButton btn(Player p, String mini, String tip, int width, Runnable r) {
        return Dialogs.button(Text.mm(mini), tip == null ? null : Text.mm(tip), width, this.d.action(p, r));
    }

    private void show(Player p, String title, List<Component> lines, List<ActionButton> buttons, int columns, Runnable back) {
        List<DialogBody> body = new ArrayList<>();
        for (Component c : lines) {
            body.add(DialogBody.plainMessage(c, 320));
        }

        DialogBase base = DialogBase.builder(Text.mm("<gold><b>Layout</b></gold> <dark_gray>»</dark_gray> <white>" + title))
            .canCloseWithEscape(true).pause(false).afterAction(DialogAfterAction.NONE).body(body).build();
        ActionButton exit = back == null ? this.btn(p, "<red>Close", null, 200, p::closeDialog) : this.btn(p, "<gray>« Back", null, 200, back);
        Dialogs.show(p, base, DialogType.multiAction(buttons).columns(columns).exitAction(exit).build());
    }

    // ------------------------------------------------------------------ hub

    public void open(Player p) {
        List<ActionButton> b = new ArrayList<>();
        b.add(this.btn(p, "<#E3172B><b>☰ Main Menu", "<gray>The /menu buttons: order, hide, columns", 150, () -> this.menu(p)));
        b.add(this.btn(p, "<white><b>⚒ Lobby Hotbar", "<gray>Slot, item and name of every hotbar item", 150, () -> this.hotbar(p, Hotbar.Layout.LOBBY)));
        b.add(this.btn(p, "<#E3172B><b>▤ Scoreboard", "<gray>Title, colours and which lines show", 150, () -> this.board(p)));
        b.add(this.btn(p, "<white><b>✎ Holograms", "<gray>Text holograms: move, edit, face, size", 150, () -> this.plugin.textHolograms().menu(p)));
        b.add(this.btn(p, "<#E3172B><b>🏆 Leaderboard Nearest", "<gray>Edit or move the leaderboard closest to you", 150, () -> {
            if (!this.plugin.holograms().editNearest(p)) {
                p.sendMessage(Text.mm("<gray>No leaderboard hologram near you. <white>Holograms → + Leaderboard</white> places one."));
            }
        }));
        b.add(this.btn(p, "<white><b>☺ NPCs", "<gray>Every NPC: move here, face, text, skin", 150, () -> this.plugin.npcs().list(p)));
        if (this.plugin.decos() != null) {
            b.add(this.btn(p, "<#E3172B><b>✦ Decorations", "<gray>3D decorations in the lobby", 150, () -> this.plugin.decos().menu(p)));
        }

        b.add(this.btn(p, "<white><b>⚔ Kits", "<gray>Contents, group and order in the menus", 150, () -> {
            p.closeDialog();
            new net.practicepvp.gui.admin.KitListMenu(this.plugin, p).open();
        }));
        b.add(this.btn(p, "<gray>⚙ All Settings", "<gray>Every option, with search", 150, () -> this.plugin.dialogs().admin().settings(p, null)));
        this.show(p, "Layout & Placement", List.of(Text.mm("<gray>Change how everything looks and where it stands.")), b, 2, null);
    }

    // ------------------------------------------------------------------ main menu

    public void menu(Player p) {
        List<String> order = new ArrayList<>(MainMenu.layout());
        order.removeIf(id -> !MainMenu.ENTRIES.containsKey(id));
        List<ActionButton> b = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            String id = order.get(i);
            int at = i;
            b.add(this.btn(p, MainMenu.ENTRIES.get(id).label(), MainMenu.ENTRIES.get(id).tip(), 150, () -> {
            }));
            b.add(this.btn(p, "<white>▲", "<gray>Earlier", 30, () -> this.saveMenu(p, order, at, -1)));
            b.add(this.btn(p, "<white>▼", "<gray>Later", 30, () -> this.saveMenu(p, order, at, 1)));
            b.add(this.btn(p, "<red>✖", "<gray>Hide this button", 30, () -> {
                order.remove(at);
                Cfg.set(Opt.MENU_LAYOUT, order);
                this.menu(p);
            }));
        }

        for (String id : MainMenu.ENTRIES.keySet()) {
            if (!order.contains(id)) {
                b.add(this.btn(p, "<dark_gray>" + Text.plain(Text.mm(MainMenu.ENTRIES.get(id).label())), "<gray>Hidden", 150, () -> {
                }));
                b.add(this.btn(p, "<green>+", "<gray>Show it again (at the end)", 30, () -> {
                    order.add(id);
                    Cfg.set(Opt.MENU_LAYOUT, order);
                    this.menu(p);
                }));
                b.add(this.btn(p, " ", null, 30, () -> {
                }));
                b.add(this.btn(p, " ", null, 30, () -> {
                }));
            }
        }

        int cols = Cfg.i(Opt.MENU_COLUMNS);
        b.add(this.btn(p, "<aqua>Columns: <white>" + cols, "<gray>Buttons side by side in /menu", 150, () -> {
            Cfg.set(Opt.MENU_COLUMNS, cols % 3 + 1);
            this.menu(p);
        }));
        b.add(this.btn(p, "<yellow>Preview", "<gray>Open /menu as players see it", 30, () -> this.plugin.mainMenu().open(p)));
        b.add(this.btn(p, "<gold>⟲", "<gray>Back to the default order", 30, () -> {
            Cfg.set(Opt.MENU_LAYOUT, new ArrayList<>(Opt.MENU_LAYOUT.def));
            this.menu(p);
        }));
        b.add(this.btn(p, " ", null, 30, () -> {
        }));
        this.show(p, "Main Menu", List.of(Text.mm("<gray>▲ ▼ move a button, <red>✖</red> hides it, <green>+</green> shows it again.")), b, 4, () -> this.open(p));
    }

    private void saveMenu(Player p, List<String> order, int at, int dir) {
        int to = at + dir;
        if (to >= 0 && to < order.size()) {
            String x = order.get(at);
            order.set(at, order.get(to));
            order.set(to, x);
            Cfg.set(Opt.MENU_LAYOUT, order);
            Snd.click(p);
        }

        this.menu(p);
    }

    // ------------------------------------------------------------------ hotbar

    private static String nice(Enum<?> e) {
        String s = e.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void applyHotbars() {
        for (Player o : Bukkit.getOnlinePlayers()) {
            PlayerState st = this.plugin.sessions().state(o);
            if (st == PlayerState.LOBBY) {
                this.plugin.lobby().giveLobbyItems(o);
            } else if (st == PlayerState.QUEUE) {
                this.plugin.lobby().giveQueueItems(o);
            }
        }
    }

    public void hotbar(Player p, Hotbar.Layout layout) {
        Hotbar hb = this.plugin.lobby().hotbar();
        List<ActionButton> b = new ArrayList<>();
        for (Hotbar.Layout l : Hotbar.Layout.values()) {
            b.add(this.btn(p, (l == layout ? "<white><b><u>" : "<gray>") + nice(l), null, 75, () -> this.hotbar(p, l)));
        }

        List<Component> lines = new ArrayList<>();
        lines.add(Text.mm("<gray>Slots 1-9 from the left. <white>◀ ▶</white> move, <aqua>Item</aqua> = the item in your hand, <red>✖</red> removes."));
        StringBuilder bar = new StringBuilder("<dark_gray>");
        List<Hotbar.Entry> entries = new ArrayList<>(hb.layout(layout));
        entries.sort(java.util.Comparator.comparingInt(Hotbar.Entry::slot));
        for (int s = 0; s < 9; s++) {
            String name = "·";
            for (Hotbar.Entry e : entries) {
                if (e.slot() == s) {
                    name = "<white>" + (s + 1) + "</white>";
                }
            }

            bar.append("[").append(name).append("<dark_gray>] ");
        }

        lines.add(Text.mm(bar.toString()));
        for (Hotbar.Entry e : entries) {
            Hotbar.Action a = e.action();
            int slot = e.slot();
            Component label = e.item().getItemMeta() != null && e.item().getItemMeta().hasDisplayName() ? e.item().getItemMeta().displayName() : Text.mm(nice(a));
            b.add(Dialogs.button(Text.mm("<white>" + (slot + 1) + ". ").append(label), Text.mm("<gray>" + nice(a)), 150, this.d.action(p, () -> {
            })));
            b.add(this.btn(p, "<white>◀", "<gray>One slot left", 30, () -> {
                hb.change(layout, a, "slot", Math.max(0, slot - 1));
                this.applyHotbars();
                this.hotbar(p, layout);
            }));
            b.add(this.btn(p, "<white>▶", "<gray>One slot right", 30, () -> {
                hb.change(layout, a, "slot", Math.min(8, slot + 1));
                this.applyHotbars();
                this.hotbar(p, layout);
            }));
            b.add(this.btn(p, "<aqua>Item", "<gray>Use the item in your main hand", 45, () -> {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) {
                    p.sendMessage(Text.mm("<red>Hold the item you want first."));
                } else {
                    hb.change(layout, a, "material", hand.getType().name());
                    this.applyHotbars();
                }

                this.hotbar(p, layout);
            }));
            b.add(this.btn(p, "<yellow>Name", "<gray>Rename (MiniMessage)", 45, () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Item name"),
                "Name (MiniMessage)", e.item().getItemMeta() != null && e.item().getItemMeta().hasDisplayName()
                    ? net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(e.item().getItemMeta().displayName()) : nice(a), 200, s -> {
                    if (!s.isBlank()) {
                        hb.change(layout, a, "name", s);
                        this.applyHotbars();
                    }

                    this.hotbar(p, layout);
                })));
            b.add(this.btn(p, "<red>✖", "<gray>Remove this item", 30, () -> {
                hb.change(layout, a, null, null);
                this.applyHotbars();
                this.hotbar(p, layout);
            }));
        }

        for (Hotbar.Action a : hb.missing(layout)) {
            b.add(this.btn(p, "<green>+ " + nice(a), "<gray>Put this default item back", 150, () -> {
                hb.restore(layout, a);
                this.applyHotbars();
                this.hotbar(p, layout);
            }));
        }

        this.show(p, "Hotbar - " + nice(layout), lines, b, 6, () -> this.open(p));
    }

    // ------------------------------------------------------------------ scoreboard

    private static String keyOf(String plain) {
        String t = plain.trim();
        int colon = t.indexOf(':');
        String k = colon > 0 ? t.substring(0, colon) : t;
        return k.length() > 24 ? k.substring(0, 24) : k;
    }

    public void board(Player p) {
        List<Component> lines = new ArrayList<>();
        lines.add(Text.mm("<gray>Your scoreboard right now. Click a line to hide it for everyone (lines with the same words in"
            + " the lobby, queue and matches go too)."));
        List<ActionButton> b = new ArrayList<>();
        List<String> hidden = new ArrayList<>(Cfg.get(Opt.BOARD_HIDDEN));
        for (Component c : this.plugin.boards().preview(p)) {
            String plain = Text.plain(c).trim();
            if (plain.isEmpty() || plain.chars().allMatch(ch -> ch == ' ' || ch == '-' || ch == '─' || ch == '_')) {
                continue;
            }

            String key = keyOf(plain);
            boolean off = false;
            for (String h : hidden) {
                off |= plain.toLowerCase(Locale.ROOT).contains(h.toLowerCase(Locale.ROOT));
            }

            boolean isOff = off;
            b.add(Dialogs.button(isOff ? Text.mm("<dark_gray><st>" + Text.esc(plain)) : c, Text.mm(isOff ? "<green>Click to show again" : "<red>Click to hide"), 300,
                this.d.action(p, () -> {
                    if (isOff) {
                        hidden.removeIf(h -> plain.toLowerCase(Locale.ROOT).contains(h.toLowerCase(Locale.ROOT)));
                    } else {
                        hidden.add(key.toLowerCase(Locale.ROOT));
                    }

                    Cfg.set(Opt.BOARD_HIDDEN, hidden);
                    this.board(p);
                })));
        }

        for (String h : hidden) {
            b.add(this.btn(p, "<dark_gray>Hidden: <gray>" + Text.esc(h), "<green>Click to show it again", 300, () -> {
                hidden.remove(h);
                Cfg.set(Opt.BOARD_HIDDEN, hidden);
                this.board(p);
            }));
        }

        b.add(this.btn(p, "<yellow>Title: <white>" + Text.esc(Cfg.s(Opt.SERVER_NAME)), "<gray>The scoreboard title", 300,
            () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Scoreboard title"), "Title", Cfg.s(Opt.SERVER_NAME), 32, s -> {
                if (!s.isBlank()) {
                    Cfg.set(Opt.SERVER_NAME, s);
                }

                this.board(p);
            })));
        boolean rw = Cfg.bool(Opt.BOARD_RED_WHITE);
        b.add(this.btn(p, "<aqua>Red & white: " + (rw ? "<green>ON" : "<red>OFF"), "<gray>The CMCPVP colours", 300, () -> {
            Cfg.set(Opt.BOARD_RED_WHITE, !rw);
            this.board(p);
        }));
        boolean side = Cfg.bool(Opt.SIDEBAR);
        b.add(this.btn(p, "<aqua>Scoreboard: " + (side ? "<green>shown" : "<red>hidden"), "<gray>The whole sidebar", 300, () -> {
            Cfg.set(Opt.SIDEBAR, !side);
            this.board(p);
        }));
        this.show(p, "Scoreboard", lines, b, 1, () -> this.open(p));
    }
}
