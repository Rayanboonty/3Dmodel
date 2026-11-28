package net.practicepvp.lobby;

import io.papermc.paper.registry.data.dialog.ActionButton;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.dialog.Dialogs;
import net.practicepvp.dialog.LobbyDialogs;
import net.practicepvp.rank.Tier;
import net.practicepvp.social.SocialManager;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 1.0.17: one main menu for everything (/menu). A red and white screen with your
 * tier, points and title on top and a big button for every mode and screen of the server.
 */
public final class MainMenu implements CommandExecutor {
    private final PracticePlugin plugin;

    public MainMenu(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            this.open(p);
        } else {
            sender.sendMessage("Players only.");
        }

        return true;
    }

    /** One button of the menu. */
    public record Entry(String label, String tip, java.util.function.BiConsumer<MainMenu, Player> run) {
    }

    public static final java.util.LinkedHashMap<String, Entry> ENTRIES = new java.util.LinkedHashMap<>();

    static {
        ENTRIES.put("ranked", new Entry("<#E3172B><b>⚔ Ranked", "<gray>Climb the tiers. Elo and points.",
            (m, p) -> m.plugin.dialogs().openQueue(p, true, Dialogs.QueueTab.MOST_PLAYED)));
        ENTRIES.put("unranked", new Entry("<white><b>⚔ Unranked", "<gray>Every kit, no Elo on the line.",
            (m, p) -> m.plugin.dialogs().openQueue(p, false, Dialogs.QueueTab.MOST_PLAYED)));
        ENTRIES.put("ffa", new Entry("<#E3172B><b>☠ Free For All", "<gray>Drop into an FFA arena.", (m, p) -> m.plugin.dialogs().openFfa(p)));
        ENTRIES.put("party", new Entry("<white><b>⚑ Party", "<gray>Make or manage a party.", (m, p) -> {
            if (m.plugin.parties().of(p) == null) {
                m.plugin.parties().create(p);
            } else {
                m.plugin.dialogs().lobby().party(p);
            }
        }));
        ENTRIES.put("bots", new Entry("<#E3172B><b>🤖 Bots & Training", "<gray>Bots, drills and the Stun Slam Lab.", (m, p) -> p.performCommand("train")));
        ENTRIES.put("tiertest", new Entry("<white><b>★ Tier Test", "<gray>Get tested for a higher tier.", (m, p) -> p.performCommand("tiertest")));
        ENTRIES.put("profile", new Entry("<#E3172B><b>☺ Profile", "<gray>Your tiers, peaks and last matches.", (m, p) -> p.performCommand("stats")));
        ENTRIES.put("leaderboards", new Entry("<white><b>☰ Leaderboards", "<gray>The best players of every kit.", (m, p) -> m.plugin.dialogs().lobby().leaderboards(p)));
        ENTRIES.put("cosmetics", new Entry("<#E3172B><b>✦ Cosmetics", "<gray>Kill effects, win effects and more.", (m, p) -> p.performCommand("cosmetics")));
        ENTRIES.put("settings", new Entry("<white><b>⚙ Settings", "<gray>Everything you can change.", (m, p) -> {
            if (m.plugin.settingsDialogs() != null) {
                m.plugin.settingsDialogs().hub(p);
            } else {
                p.performCommand("settings");
            }
        }));
        ENTRIES.put("tournaments", new Entry("<#E3172B><b>🏆 Tournaments", "<gray>Live brackets and the next event.", (m, p) -> p.performCommand("event")));
        ENTRIES.put("spectate", new Entry("<white><b>👁 Spectate", "<gray>Watch a live match.", (m, p) -> m.plugin.dialogs().lobby().spectate(p)));
        ENTRIES.put("kiteditor", new Entry("<white><b>✎ Kit Editor", "<gray>Move your kit items where you want them.", (m, p) -> m.plugin.dialogs().lobby().kitEditor(p)));
        ENTRIES.put("tour", new Entry("<gray>🎥 Lobby Tour", "<gray>A camera tour of the lobby.", (m, p) -> {
            p.closeDialog();
            p.performCommand("tour");
        }));
    }

    /** The button ids in the admin's order (unknown ids are skipped). */
    public static List<String> layout() {
        List<String> l = Cfg.get(Opt.MENU_LAYOUT);
        return l == null || l.isEmpty() ? new ArrayList<>(ENTRIES.keySet()) : l;
    }

    public void open(Player p) {
        LobbyDialogs ui = this.plugin.dialogs().lobby();
        PvPProfile prof = this.plugin.profiles().getIfLoaded(p.getUniqueId());
        int points = prof == null ? 0 : prof.tierPoints();
        Tier best = SocialManager.bestTier(prof);
        String title = Tier.title(points);
        List<Component> header = new ArrayList<>();
        header.add(Text.mm("<b><white>CMC</white><#E3172B>PVP</#E3172B></b> <dark_gray>|</dark_gray> <white>" + Text.esc(p.getName())));
        header.add(Text.mm((best == null ? "<gray>Unranked" : best.mm()) + " <dark_gray>• <" + Tier.titleColor(points) + ">" + title
            + " <dark_gray>• <white>" + points + " <gray>points"));
        header.add(Text.mm("<gray>" + Bukkit.getOnlinePlayers().size() + " online <dark_gray>• <gray>" + this.plugin.matches().active().size() + " matches live"));
        List<ActionButton> b = new ArrayList<>();
        // 1.0.18: the buttons, their order and the columns come from /pvpadmin -> Layout -> Main Menu
        for (String id : layout()) {
            Entry e = ENTRIES.get(id);
            if (e != null && (!id.equals("ranked") || Cfg.bool(Opt.RANKED_ENABLED))) {
                b.add(ui.btn(p, e.label(), e.tip(), () -> e.run().accept(this, p)));
            }
        }

        int cols = Math.max(1, Math.min(3, Cfg.i(Opt.MENU_COLUMNS)));
        ui.screen(p, Text.mm("<b><white>CMC</white><#E3172B>PVP</#E3172B></b> <dark_gray>»</dark_gray> <white>Main Menu"), List.of(), header, null, b, cols, ui.close(p));
    }
}
