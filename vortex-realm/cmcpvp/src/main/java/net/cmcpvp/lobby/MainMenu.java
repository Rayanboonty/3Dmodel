package net.cmcpvp.lobby;

import io.papermc.paper.registry.data.dialog.ActionButton;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.cmcpvp.CMCPvP;
import net.cmcpvp.config.Cfg;
import net.cmcpvp.config.Opt;
import net.cmcpvp.data.PvPProfile;
import net.cmcpvp.dialog.Dialogs;
import net.cmcpvp.dialog.LobbyDialogs;
import net.cmcpvp.rank.Tier;
import net.cmcpvp.social.SocialManager;
import net.cmcpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 1.0.17: one main menu for everything (the nether star in the hotbar, or /menu). A red and white screen with your
 * tier, points and title on top and a big button for every mode and screen of the server.
 */
public final class MainMenu implements CommandExecutor {
    private final CMCPvP plugin;

    public MainMenu(CMCPvP plugin) {
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
        if (Cfg.bool(Opt.RANKED_ENABLED)) {
            b.add(ui.btn(p, "<#E3172B><b>⚔ Ranked", "<gray>Climb the tiers. Elo and points.", () -> this.plugin.dialogs().openQueue(p, true, Dialogs.QueueTab.MOST_PLAYED)));
        }

        b.add(ui.btn(p, "<white><b>⚔ Unranked", "<gray>Every kit, no Elo on the line.", () -> this.plugin.dialogs().openQueue(p, false, Dialogs.QueueTab.MOST_PLAYED)));
        b.add(ui.btn(p, "<#E3172B><b>☠ Free For All", "<gray>Drop into an FFA arena.", () -> this.plugin.dialogs().openFfa(p)));
        b.add(ui.btn(p, "<white><b>⚑ Party", "<gray>Make or manage a party.", () -> {
            if (this.plugin.parties().of(p) == null) {
                this.plugin.parties().create(p);
            } else {
                ui.party(p);
            }
        }));
        b.add(ui.btn(p, "<#E3172B><b>🤖 Bots & Training", "<gray>Bots, drills and the Stun Slam Lab.", () -> p.performCommand("train")));
        b.add(ui.btn(p, "<white><b>★ Tier Test", "<gray>Get tested for a higher tier.", () -> p.performCommand("tiertest")));
        b.add(ui.btn(p, "<#E3172B><b>☺ Profile", "<gray>Your tiers, peaks and last matches.", () -> p.performCommand("stats")));
        b.add(ui.btn(p, "<white><b>☰ Leaderboards", "<gray>The best players of every kit.", () -> ui.leaderboards(p)));
        b.add(ui.btn(p, "<#E3172B><b>✦ Cosmetics", "<gray>Kill effects, win poses and more.", () -> p.performCommand("cosmetics")));
        b.add(ui.btn(p, "<white><b>⚙ Settings", "<gray>Everything you can change.", () -> {
            if (this.plugin.settingsDialogs() != null) {
                this.plugin.settingsDialogs().hub(p);
            } else {
                p.performCommand("settings");
            }
        }));
        b.add(ui.btn(p, "<#E3172B><b>🏆 Tournaments", "<gray>Live brackets and the next event.", () -> p.performCommand("event")));
        b.add(ui.btn(p, "<white><b>👁 Spectate", "<gray>Watch a live match.", () -> ui.spectate(p)));
        b.add(ui.btn(p, "<gray>🎥 Lobby Tour", "<gray>A camera tour of the lobby.", () -> {
            p.closeDialog();
            p.performCommand("tour");
        }));
        ui.screen(p, Text.mm("<b><white>CMC</white><#E3172B>PVP</#E3172B></b> <dark_gray>»</dark_gray> <white>Main Menu"), List.of(), header, null, b, 2, ui.close(p));
    }
}
