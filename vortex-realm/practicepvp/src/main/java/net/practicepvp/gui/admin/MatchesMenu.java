package net.practicepvp.gui.admin;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Msg;
import net.practicepvp.gui.Menu;
import net.practicepvp.gui.Ui;
import net.practicepvp.gui.menus.SpectateMenu;
import net.practicepvp.match.Match;
import net.practicepvp.util.Fmt;
import net.practicepvp.util.Items;
import net.practicepvp.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class MatchesMenu extends Menu {
    public MatchesMenu(PracticePlugin plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Admin</b></gold> <dark_gray>» <gray>Live Matches");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        int[] slots = Ui.innerSlots(6);
        int i = 0;

        for (Match m : this.plugin.matches().active()) {
            if (i >= slots.length) {
                break;
            }

            this.set(
                slots[i++],
                Items.of(m.kit.iconStack())
                    .name("<gold><b>#" + m.id + " " + Text.esc(m.title()))
                    .lore(
                        "<gray>State: <white>" + m.state(),
                        "<gray>Kit: <white>" + Text.esc(m.kit.displayName),
                        "<gray>Mode: " + (m.ranked ? "<gold>Ranked" : "<green>Unranked"),
                        "<gray>Map: <white>" + (m.instance() == null ? "-" : m.instance().label()),
                        "<gray>Time: <white>" + Fmt.clock(m.fightSeconds()),
                        "",
                        "<yellow>Click <gray>spectate",
                        "<red>Shift-click <gray>end as draw (ranks unchanged)"
                    )
                    .hideAll()
                    .build(),
                type -> {
                    if (type.isShiftClick()) {
                        this.plugin
                            .dialogs()
                            .confirm(
                                this.viewer,
                                Text.mm("<red><b>End match #" + m.id + "?"),
                                List.of(Text.mm("<gray>Ends as a draw, ranks don't change.")),
                                () -> {
                                    m.end(null, Match.EndReason.DRAW);
                                    Msg.send(this.viewer, "admin.match-ended", "id", m.id);
                                }
                            );
                    } else {
                        this.viewer.closeInventory();
                        SpectateMenu.spectate(this.plugin, this.viewer, m);
                    }
                }
            );
        }

        if (i == 0) {
            this.set(22, Ui.info(Material.CLOCK, "<gray>No matches running"));
        }

        this.set(53, Items.of(Material.SUNFLOWER).name("<gold><b>Refresh").build(), c -> this.redraw());
        this.set(49, Ui.back(), c -> AdminMenu.openBest(this.plugin, this.viewer));
    }
}
