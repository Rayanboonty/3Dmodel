package net.vortexpvp.match;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.arena.Arena;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Msg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.KitStats;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.fx.Fx;
import net.vortexpvp.fx.Versus;
import net.vortexpvp.fx.SwordPop;
import net.vortexpvp.fx.TotemPop;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.rank.Tier;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Session;
import net.vortexpvp.util.Fmt;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import net.vortexpvp.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class MatchManager {
    private final VortexPvP plugin;
    private final Map<Integer, Match> matches = new LinkedHashMap<>();

    public MatchManager(VortexPvP plugin) {
        this.plugin = plugin;
    }

    public Match startDuel(Player a, Player b, Kit kit, boolean ranked, Arena forced, int rounds, boolean fromQueue) {
        Match m = new Match(this.plugin, kit, ranked, Match.Type.DUEL, List.of(List.of(a.getUniqueId()), List.of(b.getUniqueId())), forced, fromQueue, rounds);
        this.matches.put(m.id, m);
        this.prepare(a, m);
        this.prepare(b, m);
        this.announceFound(a, b, kit, ranked);
        this.announceFound(b, a, kit, ranked);
        this.foundEffect(a, kit);
        this.foundEffect(b, kit);
        this.foundCooldown(m);
        Tasks.later(26L, () -> {
            if (m.state() == Match.State.WAITING) {
                Component sub = kit.iconComponent()
                    .append(Text.mm(" " + kit.color + Text.esc(kit.displayName) + " <dark_gray>• "))
                    .append(m.tierChallenge ? Text.mm("<gold>Tier Challenge") : (ranked ? Msg.get("summary.ranked") : Msg.get("summary.unranked")));
                if (a.isOnline()) {
                    Versus.play(a, b, sub);
                }

                if (b.isOnline()) {
                    Versus.play(b, a, sub);
                }
            }
        });
        return m;
    }

    /** MCPVP lobby broadcast for tier tests and challenges: "(kit) Tier Test: HT3 | A VS LT2 | B [Spectate]". */
    public void announceTierMatch(Match m, Player a, Player b, String what) {
        Component line = m.kit.iconComponent()
            .append(Text.mm(" <gold><b>" + Text.esc(what) + "</b></gold><dark_gray>: "))
            .append(this.tagged(m, a))
            .append(Text.mm(" <gray><b>VS</b></gray> "))
            .append(this.tagged(m, b));
        if (Cfg.bool(Opt.ALLOW_SPECTATORS) && !m.spectatorsBlocked) {
            line = line.append(
                Text.mm(" <click:run_command:'/spectate " + a.getName() + "'><hover:show_text:'<gray>Watch this match'><aqua>[Spectate]</aqua></hover></click>")
            );
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != a && p != b && this.plugin.sessions().state(p).inLobbyArea()) {
                p.sendMessage(line);
            }
        }
    }

    private Component tagged(Match m, Player p) {
        Tier t = null;

        try {
            t = this.plugin.profiles().get(p).stats(m.kit.id).tier();
        } catch (RuntimeException var5) {
        }

        return Text.mm((t == null ? "<gray>Unranked" : t.mm()) + " <dark_gray>| <white>" + Text.esc(p.getName()));
    }

    private void foundEffect(Player p, Kit kit) {
        Opt.FoundEffect fx = Cfg.e(Opt.FOUND_EFFECT, Opt.FoundEffect.class);
        if (fx == Opt.FoundEffect.ICON) {
            SwordPop.play(this.plugin, p, kit.iconStack(), SwordPop.iconSize(kit.iconStack()));
            return;
        }

        if (fx == Opt.FoundEffect.TOTEM || fx == Opt.FoundEffect.BOTH) {
            TotemPop.play(p, kit.iconStack());
        }

        if (fx == Opt.FoundEffect.SWORD || fx == Opt.FoundEffect.BOTH) {
            Tasks.later(fx == Opt.FoundEffect.BOTH ? 22L : 0L, () -> {
                if (p.isOnline()) {
                    SwordPop.play(this.plugin, p, popItem(kit));
                }
            });
        }
    }

    private static ItemStack popItem(Kit kit) {
        ItemStack icon = kit.iconStack();
        String n = icon.getType().name();
        boolean weapon = n.endsWith("_SWORD")
            || n.endsWith("_AXE")
            || n.endsWith("_SPEAR")
            || n.equals("MACE")
            || n.equals("TRIDENT")
            || n.equals("BOW")
            || n.equals("CROSSBOW");
        return weapon ? new ItemStack(icon.getType()) : new ItemStack(Material.DIAMOND_SWORD);
    }

    private void foundCooldown(Match m) {
        int seconds = Math.max(1, Cfg.i(Opt.MATCH_FOUND_DELAY));

        for (int i = 0; i < seconds; i++) {
            int left = seconds - i;
            Tasks.later(20L * i + 10L, () -> {
                if (m.state() == Match.State.WAITING) {
                    for (Player p : m.participantsOnline()) {
                        p.sendActionBar(Text.mm("<gray>Teleporting in <gold><b>" + left + "</b></gold><gray>..."));
                        Snd.play(p, "block.note_block.hat", 0.6F, 1.0F + (seconds - left) * 0.15F);
                    }
                }
            });
        }

        Tasks.later(20L * seconds + 10L, m::begin);
    }

    public Match startParty(List<List<UUID>> teams, Kit kit, Match.Type type, Arena forced) {
        Match m = new Match(this.plugin, kit, false, type, teams, forced, false, type == Match.Type.PARTY_FFA ? 1 : 0);
        this.matches.put(m.id, m);

        for (Player p : m.participantsOnline()) {
            this.prepare(p, m);
            Fx.matchFound(p, Text.mm("<green>" + Fmt.capitalize(type.name()) + " <dark_gray>•</dark_gray> ").append(kit.label()));
        }

        this.foundCooldown(m);
        return m;
    }

    private void prepare(Player p, Match m) {
        Session s = this.plugin.sessions().get(p);
        this.plugin.queues().leaveAll(p, false);
        s.state = PlayerState.PREMATCH;
        s.match = m;
        p.closeInventory();
        p.closeDialog();
        p.getInventory().clear();
        p.setItemOnCursor(null);
    }

    private void announceFound(Player p, Player opp, Kit kit, boolean ranked) {
        PvPProfile op = this.plugin.profiles().get(opp);
        KitStats ks = op.statsOrNull(kit.id);
        Tier t = ks == null ? null : ks.tier();
        Component tier = t == null ? Text.mm("<gray>Unranked") : Text.mm(t.mm());
        Component sub = Text.mm("<gray>vs <green>" + Text.esc(opp.getName()) + "</green> <dark_gray>•</dark_gray> ")
            .append(kit.iconComponent())
            .append((Component)Component.space())
            .append(tier);
        Fx.matchFound(p, sub);
        Msg.send(
            p,
            "match.found",
            "opponent",
            opp.getName(),
            "kit",
            kit.label(),
            "mode",
            ranked ? Msg.get("summary.ranked") : Msg.get("summary.unranked"),
            "tier",
            tier,
            "ping",
            opp.getPing()
        );
    }

    void remove(Match m) {
        this.matches.remove(m.id);
    }

    public Collection<Match> active() {
        return Collections.unmodifiableCollection(this.matches.values());
    }

    public Match byId(int id) {
        return this.matches.get(id);
    }

    public int playing(Kit kit) {
        int n = 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            Session s = this.plugin.sessions().get(p.getUniqueId());
            if (s != null
                && s.match != null
                && (s.state == PlayerState.MATCH || s.state == PlayerState.PREMATCH)
                && s.match.kit.id.equals(kit.id)
                && s.match.state() != Match.State.ENDED
                && s.match.isParticipant(p.getUniqueId())) {
                n++;
            }
        }

        return n;
    }

    public int pending(Kit kit) {
        int n = 0;

        for (Match m : this.matches.values()) {
            if (m.kit == kit && m.instance() == null && m.state() != Match.State.ENDED) {
                n++;
            }
        }

        return n;
    }

    public int playingTotal() {
        int n = 0;

        for (Match m : this.matches.values()) {
            if (m.state() != Match.State.ENDED) {
                n += m.participantsOnline().size();
            }
        }

        return n;
    }

    public void tickAll() {
        World pw = this.plugin.worlds().practiceWorld();
        Difficulty want = WorldManager.arenaDifficulty();
        if (pw != null && pw.getDifficulty() != want) {
            pw.setDifficulty(want);
        }

        long staleAfter = Cfg.i(Opt.END_DELAY) * 50L + 10000L;

        for (Match m : new ArrayList<>(this.matches.values())) {
            try {
                m.forceCleanupIfStale(staleAfter);
                m.tick();
            } catch (RuntimeException var8) {
                this.plugin.getLogger().log(Level.SEVERE, "Error ticking match " + m.id, (Throwable)var8);
            }
        }
    }

    public void shutdown() {
        for (Match m : new ArrayList<>(this.matches.values())) {
            m.cancel("match.server-stop", false);
        }

        this.matches.clear();
    }

    public Match ofPlayer(Player p) {
        return this.plugin.sessions().get(p).match;
    }
}
