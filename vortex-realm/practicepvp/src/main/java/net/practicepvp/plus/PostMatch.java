package net.practicepvp.plus;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.dialog.Dialogs;
import net.practicepvp.kit.Kit;
import net.practicepvp.session.PlayerState;
import net.practicepvp.util.ClientVersion;
import net.practicepvp.util.Text;
import org.bukkit.entity.Player;

/**
 * 1.0.17: the post-match screen. A moment after a 1v1 ends, each player gets a red and white screen with their stats
 * next to the opponent's (damage, hits, accuracy, combo, crits, W-taps, pots), the winner, and buttons to rematch, queue
 * the same kit again or watch the replay. The chat card stays as well.
 */
final class PostMatch {
    private PostMatch() {
    }

    private static final String[][] ROWS = {
        {"dealt", "Damage dealt"}, {"taken", "Damage taken"}, {"hits", "Hits"}, {"acc", "Accuracy %"}, {"combo", "Longest combo"},
        {"crits", "Crits"}, {"wtaps", "W-taps"}, {"blocks", "Blocks placed"}, {"pots", "Pots"}, {"gaps", "Gapples"}, {"pearls", "Pearls"}
    };

    private static String cell(String mine, String theirs, boolean lowerIsBetter) {
        try {
            double a = Double.parseDouble(mine);
            double b = Double.parseDouble(theirs);
            boolean better = lowerIsBetter ? a < b : a > b;
            boolean worse = lowerIsBetter ? a > b : a < b;
            return (better ? "<#3BE13B><b>" : worse ? "<#FF6B6B>" : "<white>") + mine;
        } catch (NumberFormatException | NullPointerException e) {
            return "<white>" + mine;
        }
    }

    /** Waits (up to 30 s) until the player is back in the lobby, then shows the screen. */
    static void show(PracticePlugin plugin, Player p, Map<String, String> me, Map<String, String> them, String opponent) {
        wait(plugin, p, me, them, opponent, 60);
    }

    private static void wait(PracticePlugin plugin, Player p, Map<String, String> me, Map<String, String> them, String opponent, int tries) {
        if (!p.isOnline() || !Cfg.bool(Opt.POST_MATCH_SCREEN) || !ClientVersion.supportsDialogs(p)) {
            return;
        }

        PlayerState st = plugin.sessions().state(p);
        if (st == PlayerState.QUEUE) {
            return;
        }

        if (st != PlayerState.LOBBY) {
            if (tries > 0) {
                net.practicepvp.util.Tasks.later(10L, () -> wait(plugin, p, me, them, opponent, tries - 1));
            }

            return;
        }

        open(plugin, p, me, them, opponent);
    }

    private static void open(PracticePlugin plugin, Player p, Map<String, String> me, Map<String, String> them, String opponent) {

        boolean won = Boolean.parseBoolean(me.get("won"));
        boolean ranked = Boolean.parseBoolean(me.get("ranked"));
        Kit kit = plugin.kits().get(me.get("kit"));
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Text.mm(won ? "<#3BE13B><b>VICTORY</b>" : "<#FF4D4D><b>DEFEAT</b>")
            .append(Text.mm(" <dark_gray>• <white>" + (kit == null ? Text.esc(String.valueOf(me.get("kit"))) : Text.esc(kit.displayName))
                + (ranked ? " <#E3172B>Ranked" : " <gray>Unranked") + " <dark_gray>• <gray>" + me.getOrDefault("secs", "0") + "s")), 320));
        StringBuilder t = new StringBuilder();
        t.append("<white><b>You</b> <dark_gray>|</dark_gray> <#E3172B><b>").append(Text.esc(opponent == null ? "Opponent" : opponent)).append("</b>");
        for (String[] r : ROWS) {
            String a = me.getOrDefault(r[0], "0");
            if (them == null) {
                t.append("\n<gray>").append(r[1]).append(": <white>").append(a);
            } else {
                String b = them.getOrDefault(r[0], "0");
                boolean low = r[0].equals("taken");
                t.append("\n<gray>").append(r[1]).append(": ").append(cell(a, b, low)).append(" <dark_gray>vs ").append(cell(b, a, low));
            }
        }

        body.add(DialogBody.plainMessage(Text.mm(t.toString()), 320));
        Dialogs d = plugin.dialogs();
        List<ActionButton> b = new ArrayList<>();
        if (opponent != null) {
            b.add(Dialogs.button(Text.mm("<#E3172B><b>⟳ Rematch"), Text.mm("<gray>Ask for a rematch (sides swapped)."), 150, d.action(p, () -> {
                p.closeDialog();
                p.performCommand("rematch");
            })));
        }

        if (kit != null) {
            b.add(Dialogs.button(Text.mm("<white><b>▶ Queue again"), Text.mm("<gray>Queue " + Text.esc(kit.displayName) + " again."), 150, d.action(p, () -> {
                p.closeDialog();
                plugin.queues().join(p, kit, ranked && Cfg.bool(Opt.RANKED_ENABLED), false);
            })));
        }

        String rid = me.get("replay");
        if (rid != null) {
            b.add(Dialogs.button(Text.mm("<white>🎬 Watch replay"), null, 150, d.action(p, () -> {
                p.closeDialog();
                p.performCommand("replay " + rid);
            })));
        }

        b.add(Dialogs.button(Text.mm("<gray>☰ Menu"), null, 150, d.action(p, () -> plugin.mainMenu().open(p))));
        DialogBase base = DialogBase.builder(Text.mm("<b><white>CMC</white><#E3172B>PVP</#E3172B></b> <dark_gray>»</dark_gray> <white>Match Summary"))
            .canCloseWithEscape(true).pause(false).afterAction(DialogAfterAction.CLOSE).body(body).build();
        Dialogs.show(p, base, DialogType.multiAction(b).columns(2).exitAction(Dialogs.button(Text.mm("<red>Close"), null, 200, d.action(p, p::closeDialog))).build());
    }
}
