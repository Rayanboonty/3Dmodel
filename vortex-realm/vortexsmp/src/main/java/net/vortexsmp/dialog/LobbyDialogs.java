package net.vortexsmp.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.vortexsmp.util.Text;
import net.vortexsmp.util.TextWidth;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/** List screens: icon rows that can be clicked, a header and buttons. */
public final class LobbyDialogs {
    private final Dialogs d;

    LobbyDialogs(Dialogs dialogs) {
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

    public ActionButton urlButton(String mini, String url) {
        return Dialogs.button(Text.mm(mini), Text.mm("<gray>" + Text.esc(url)), 150, io.papermc.paper.registry.data.dialog.action.DialogAction.staticAction(ClickEvent.openUrl(url)));
    }

    public ActionButton close(Player p) {
        return this.d.closeButton(p);
    }


    public static Component title(String section, String hint) {
        return Text.mm("<gold><b>" + section + "</b></gold> <dark_gray>»</dark_gray> <aqua>" + hint);
    }

    public record Tile(ItemStack icon, Component name, Component line, List<Component> hover, Runnable click) {
    }
}
