package net.vortexpvp.match;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.data.Setting;
import net.vortexpvp.session.Session;
import net.vortexpvp.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * MCPVP spectator lines: "👁 X started spectating." / "stopped spectating." for everyone in the match, and
 * spectator chat shown to the match as {@code <👁 name> message}.
 */
public final class SpectatorFeed implements Listener {
    private final VortexPvP plugin;

    public SpectatorFeed(VortexPvP plugin) {
        this.plugin = plugin;
    }

    private boolean on(Player p, Setting s) {
        PvPProfile prof = this.plugin.profiles().getIfLoaded(p.getUniqueId());
        return prof == null || prof.on(s);
    }

    /** Staff can watch silently (spectator.hide-staff). */
    private boolean hidden(Player spectator) {
        return Cfg.bool(Opt.SPECTATOR_HIDE_STAFF) && this.plugin.staff() != null && this.plugin.staff().isStaff(spectator);
    }

    public void joined(Match m, Player spectator) {
        if (Cfg.bool(Opt.SPECTATOR_FEED) && !this.hidden(spectator)) {
            Component line = Text.mm("<#55FF55>👁 <gray>" + Text.esc(spectator.getName()) + " started spectating.");
            for (Player p : m.audience()) {
                if (!p.equals(spectator) && this.on(p, Setting.SPEC_NOTIFY) && this.seesOnline(p, spectator)) {
                    p.sendMessage(line);
                }
            }
        }
    }

    public void left(Match m, Player spectator) {
        if (Cfg.bool(Opt.SPECTATOR_FEED) && !this.hidden(spectator) && m.state() != Match.State.ENDED) {
            Component line = Text.mm("<#FF5555>👁 <gray>" + Text.esc(spectator.getName()) + " stopped spectating.");
            for (Player p : m.audience()) {
                if (!p.equals(spectator) && this.on(p, Setting.SPEC_NOTIFY) && this.seesOnline(p, spectator)) {
                    p.sendMessage(line);
                }
            }
        }
    }

    /** Spectators talk to the match they watch. Runs after the normal chat formatting so it wins. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (Cfg.bool(Opt.SPECTATOR_CHAT)) {
            Player sender = e.getPlayer();
            Session s = this.plugin.sessions().get(sender);
            Match m = s.spectating;
            if (m != null && m.state() != Match.State.ENDED) {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.originalMessage());
                if (!plain.startsWith("!")) {
                    Set<UUID> allowed = new HashSet<>();
                    try {
                        for (Player p : m.audience()) {
                            if (p.equals(sender) || this.on(p, Setting.SPEC_CHAT)) {
                                allowed.add(p.getUniqueId());
                            }
                        }
                    } catch (java.util.ConcurrentModificationException var12) {
                        return;
                    }

                    e.viewers().removeIf(a -> a instanceof Player v && !allowed.contains(v.getUniqueId()) && !this.spy(v));
                    String name = sender.getName();
                    e.renderer(new ChatRenderer() {
                        @Override
                        public Component render(Player source, Component display, Component msg, Audience viewer) {
                            return Text.mm("<gray><i><<#AAAAAA>👁</#AAAAAA> " + Text.esc(name) + "></i></gray> ")
                                .append(msg.colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
                        }
                    });
                }
            }
        }
    }

    /** Invisible / hidden spectators don't announce themselves to players who can't see them online. */
    private boolean seesOnline(Player viewer, Player spectator) {
        return this.plugin.social() == null || this.plugin.social().appearsOnline(viewer, spectator);
    }

    private boolean spy(Player v) {
        return v.hasPermission("vortexpvp.staff") && this.plugin.chat() != null && this.plugin.chat().spying(v);
    }
}
