package net.practicepvp.gui.menus;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Msg;
import net.practicepvp.gui.Menu;
import net.practicepvp.gui.Ui;
import net.practicepvp.match.Match;
import net.practicepvp.session.PlayerState;
import net.practicepvp.util.Fmt;
import net.practicepvp.util.Items;
import net.practicepvp.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class SpectateMenu extends Menu {
    public SpectateMenu(PracticePlugin plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Spectate</b></gold> <dark_gray>» <gray>Live matches");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        int live = 0;

        for (Match m : this.plugin.matches().active()) {
            Match.State st = m.state();
            if (m.instance() != null && st != Match.State.ENDED && st != Match.State.WAITING && st != Match.State.PREMATCH) {
                live++;
            }
        }

        int[] slots = Ui.fit(live, 6);
        int i = 0;

        for (Match mx : this.plugin.matches().active()) {
            Match.State st = mx.state();
            if (i < slots.length && mx.instance() != null && st != Match.State.ENDED && st != Match.State.WAITING && st != Match.State.PREMATCH) {
                List<String> lore = new ArrayList<>();
                lore.add("<gray>Kit: <white>" + Text.esc(mx.kit.displayName));
                lore.add("<gray>Mode: " + (mx.ranked ? "<gold>Ranked" : "<green>Unranked"));
                if (mx.isOneVsOne()) {
                    lore.add("<gray>Score: <green>" + mx.teams.get(0).score + " <dark_gray>-</dark_gray> <red>" + mx.teams.get(1).score);
                }

                lore.add("<gray>Time: <white>" + Fmt.clock(mx.fightSeconds()));
                lore.add("<gray>Map: <white>" + Text.esc(mx.instance().arena.displayName));
                lore.add("<gray>Spectators: <white>" + mx.spectators().size());
                lore.add("");
                lore.add(!mx.allowsSpectators() && !this.plugin.staff().isStaff(this.viewer) ? "<red>Spectating disabled" : "<yellow>▶ Click to spectate");
                this.set(slots[i++], Items.of(mx.kit.iconStack()).name("<gold><b>" + Text.esc(mx.title())).lore(lore).hideAll().build(), c -> {
                    this.viewer.closeInventory();
                    spectate(this.plugin, this.viewer, mx);
                });
            }
        }

        if (i == 0) {
            this.set(22, Ui.info(Material.CLOCK, "<gray>No matches right now", "<dark_gray>Check back in a moment."));
        }

        this.set(49, Ui.close(), c -> this.viewer.closeInventory());
        this.set(53, Items.of(Material.SUNFLOWER).name("<gold><b>Refresh").build(), c -> this.redraw());
    }

    public static void spectate(PracticePlugin plugin, Player p, Match m) {
        PlayerState st = plugin.sessions().state(p);
        if (st != PlayerState.LOBBY && st != PlayerState.QUEUE && st != PlayerState.SPECTATING) {
            Msg.error(p, "spectate.busy");
        } else if (!m.allowsSpectators() && !plugin.staff().isStaff(p)) {
            Msg.error(p, "spectate.disabled");
        } else {
            if (st == PlayerState.SPECTATING) {
                Match old = plugin.sessions().get(p).spectating;
                if (old != null) {
                    old.removeSpectator(p, false);
                }
            }

            plugin.queues().leaveAll(p, false);
            if (!m.addSpectator(p)) {
                Msg.error(p, "spectate.unavailable");
                plugin.lobby().sendToLobby(p);
            } else {
                Msg.send(p, "spectate.started", "match", m.title());
            }
        }
    }
}
