package net.cmcpvp.rank;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.cmcpvp.CMCPvP;
import net.cmcpvp.api.MatchEndEvent;
import net.cmcpvp.config.Cfg;
import net.cmcpvp.config.Opt;
import net.cmcpvp.data.KitStats;
import net.cmcpvp.data.PvPProfile;
import net.cmcpvp.dialog.Dialogs;
import net.cmcpvp.kit.Kit;
import net.cmcpvp.match.Match;
import net.cmcpvp.session.PlayerState;
import net.cmcpvp.util.ClientVersion;
import net.cmcpvp.util.Snd;
import net.cmcpvp.util.Tasks;
import net.cmcpvp.util.Text;
import net.cmcpvp.web.LiveFeed;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Tier Testers (MCPvP style), next to the automatic Tier Test Challenges. A player joins a kit's waitlist with
 * /tiertest &lt;kit&gt;; a Tier Tester (cmcpvp.tiertester) opens /testqueue, duels them (unranked, first to the
 * tier test length) and then sets the tier. The retest cooldown applies to both kinds of test.
 */
public final class TierTests implements Listener, CommandExecutor, TabCompleter {
    public static final String PERM = "cmcpvp.tiertester";
    private final CMCPvP plugin;
    /** kit id -> waiting players (in order) -> time they joined. */
    private final Map<String, LinkedHashMap<UUID, Long>> waiting = new ConcurrentHashMap<>();
    /** match id -> the test it is. */
    private final Map<Integer, TierTests.Test> running = new ConcurrentHashMap<>();
    /** tester -> the finished test waiting for a result. */
    private final Map<UUID, TierTests.Test> results = new ConcurrentHashMap<>();

    public TierTests(CMCPvP plugin) {
        this.plugin = plugin;
    }

    public record Test(UUID tester, UUID player, String playerName, String kit) {
    }

    /** True while this match is a Tier Tester's test. */
    public boolean isTest(int matchId) {
        return this.running.containsKey(matchId);
    }

    // ---------------------------------------------------------------- players

    private boolean join(Player p, Kit kit) {
        if (!Cfg.bool(Opt.TESTER_TESTS)) {
            p.sendMessage(Text.mm("<red>Tier Testers are turned off on this server."));
            return false;
        }

        if (!kit.enabled || !kit.ranked) {
            p.sendMessage(Text.mm("<red>That kit has no tiers."));
            return false;
        }

        PvPProfile prof = this.plugin.profiles().get(p);
        KitStats ks = prof.statsOrNull(kit.id);
        if (ks == null || ks.tier < 0) {
            p.sendMessage(Text.mm("<red>Finish your <white>" + Cfg.i(Opt.PLACEMENT_MATCHES) + "</white> placement matches in ").append(kit.label())
                .append(Text.mm(" <red>first (ranked queue).")));
            return false;
        }

        if (this.plugin.ranks().refuseCooldown(p, prof, kit)) {
            return false;
        }

        LinkedHashMap<UUID, Long> list = this.waiting.computeIfAbsent(kit.id, k -> new LinkedHashMap<>());
        synchronized (list) {
            if (!list.containsKey(p.getUniqueId())) {
                list.put(p.getUniqueId(), System.currentTimeMillis());
            }
        }

        int pos = this.position(kit.id, p.getUniqueId());
        p.sendMessage(Text.mm("<#E3172B><b>TIER TEST</b></#E3172B> <gray>You joined the ").append(kit.label())
            .append(Text.mm(" <gray>waitlist - <white>#" + pos + "</white> in line. A Tier Tester will duel you. <dark_gray>(/tiertest leave)")));
        Snd.play(p, "block.note_block.pling", 0.7F, 1.4F);
        int testers = 0;
        for (Player t : Bukkit.getOnlinePlayers()) {
            if (t.hasPermission(PERM) && !t.equals(p)) {
                testers++;
                t.sendMessage(Text.mm("<#E3172B>▶ <white>" + Text.esc(p.getName()) + "</white> <gray>(").append(Text.mm(ks.tier().mm()))
                    .append(Text.mm("<gray>) joined the ")).append(kit.label())
                    .append(Text.mm(" <gray>test waitlist. <#E3172B><click:run_command:'/testqueue'>[OPEN QUEUE]</click>")));
            }
        }

        if (testers == 0) {
            p.sendMessage(Text.mm("<gray>No Tier Tester is online right now - you keep your place until one is."));
        }

        return true;
    }

    private void leave(UUID u) {
        for (LinkedHashMap<UUID, Long> list : this.waiting.values()) {
            synchronized (list) {
                list.remove(u);
            }
        }
    }

    private int position(String kit, UUID u) {
        LinkedHashMap<UUID, Long> list = this.waiting.get(kit);
        if (list == null) {
            return 0;
        }

        synchronized (list) {
            int i = 1;
            for (UUID x : list.keySet()) {
                if (x.equals(u)) {
                    return i;
                }

                i++;
            }
        }

        return 0;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        this.leave(e.getPlayer().getUniqueId());
        this.results.remove(e.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- testers

    private void openQueue(Player t) {
        List<String[]> rows = new ArrayList<>();
        for (Entry<String, LinkedHashMap<UUID, Long>> e : this.waiting.entrySet()) {
            Kit kit = this.plugin.kits().get(e.getKey());
            if (kit == null) {
                continue;
            }

            synchronized (e.getValue()) {
                for (Entry<UUID, Long> w : e.getValue().entrySet()) {
                    Player p = Bukkit.getPlayer(w.getKey());
                    if (p != null) {
                        rows.add(new String[]{kit.id, p.getName(), String.valueOf(w.getValue())});
                    }
                }
            }
        }

        if (!ClientVersion.supportsDialogs(t)) {
            t.sendMessage(Text.mm("<#E3172B><b>TEST QUEUE</b></#E3172B> <gray>" + rows.size() + " waiting"));
            for (String[] r : rows) {
                t.sendMessage(Text.mm(" <white>" + r[1] + " <gray>" + r[0] + " <#E3172B><click:run_command:'/testqueue accept " + r[1] + " " + r[0] + "'>[TEST]</click>"));
            }

            return;
        }

        Dialogs d = this.plugin.dialogs();
        List<ActionButton> buttons = new ArrayList<>();
        for (String[] r : rows) {
            Kit kit = this.plugin.kits().get(r[0]);
            Player p = Bukkit.getPlayerExact(r[1]);
            if (kit == null || p == null) {
                continue;
            }

            KitStats ks = this.plugin.profiles().get(p).statsOrNull(kit.id);
            String tier = ks == null || ks.tier() == null ? "<gray>-" : ks.tier().mm();
            String peak = ks == null || ks.peak() == null ? "<gray>-" : ks.peak().mm();
            long mins = (System.currentTimeMillis() - Long.parseLong(r[2])) / 60000L;
            Component label = Component.empty().append(kit.iconComponent())
                .append(Text.mm(" <white><b>" + Text.esc(p.getName()) + "</b> ")).append(Text.mm(tier))
                .append(Text.mm(" <dark_gray>|</dark_gray> <gray>" + mins + "m"));
            Component tip = Text.mm(kit.color + "<b>" + Text.esc(kit.displayName) + "</b>\n<gray>Tier ").append(Text.mm(tier))
                .append(Text.mm("<gray>, peak ")).append(Text.mm(peak))
                .append(Text.mm("\n<gray>Ping <white>" + p.getPing() + "ms</white>\n\n<#E3172B>▶ <white>Click to start the test duel"));
            buttons.add(Dialogs.button(label, tip, 260, d.action(t, () -> {
                t.closeDialog();
                this.accept(t, p, kit);
            })));
        }

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Text.mm("<#E3172B><b>TIER TEST QUEUE</b></#E3172B>  <dark_gray>▪</dark_gray>  <white>" + rows.size()
            + "</white> <gray>waiting"), 300));
        body.add(DialogBody.plainMessage(Text.mm("<gray>Duel the player, then pick the tier they showed. Tests are unranked - your own tier never changes."), 300));
        if (buttons.isEmpty()) {
            body.add(DialogBody.plainMessage(Text.mm("<gray>Nobody is waiting for a test."), 300));
        }

        DialogBase base = DialogBase.builder(Component.empty()).canCloseWithEscape(true).pause(false).afterAction(DialogAfterAction.NONE).body(body).build();
        if (buttons.isEmpty()) {
            Dialogs.show(t, base, DialogType.notice(d.closeButton(t)));
        } else {
            Dialogs.show(t, base, DialogType.multiAction(buttons).columns(1).exitAction(d.closeButton(t)).build());
        }
    }

    private void accept(Player t, Player p, Kit kit) {
        if (t.equals(p)) {
            t.sendMessage(Text.mm("<red>You can't test yourself."));
            return;
        }

        if (this.position(kit.id, p.getUniqueId()) == 0) {
            t.sendMessage(Text.mm("<red>" + Text.esc(p.getName()) + " is not waiting for a " + Text.esc(kit.displayName) + " test."));
            return;
        }

        if (this.plugin.sessions().state(t) != PlayerState.LOBBY || this.plugin.sessions().state(p) != PlayerState.LOBBY) {
            t.sendMessage(Text.mm("<red>You both need to be in the lobby (not in a match or queue)."));
            return;
        }

        LinkedHashMap<UUID, Long> list = this.waiting.get(kit.id);
        synchronized (list) {
            list.remove(p.getUniqueId());
        }

        Match m = this.plugin.matches().startDuel(t, p, kit, false, null, Cfg.i(Opt.TIER_TEST_FIRST_TO), false);
        if (m == null) {
            t.sendMessage(Text.mm("<red>Could not start the test duel - try again."));
            return;
        }

        this.running.put(m.id, new TierTests.Test(t.getUniqueId(), p.getUniqueId(), p.getName(), kit.id));
        p.sendMessage(Text.mm("<#E3172B><b>TIER TEST</b></#E3172B> <white>" + Text.esc(t.getName()) + "</white> <gray>is testing you in ").append(kit.label())
            .append(Text.mm("<gray>. Show what you've got!")));
        Snd.play(p, "ui.toast.challenge_complete", 0.6F, 1.2F);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnd(MatchEndEvent e) {
        TierTests.Test test = this.running.remove(e.match().id);
        if (test != null) {
            this.results.put(test.tester(), test);
            Tasks.later(60L, () -> {
                Player t = Bukkit.getPlayer(test.tester());
                if (t != null && this.results.get(test.tester()) == test) {
                    this.openResult(t, test);
                }
            });
        }
    }

    /** The tiers this tester may give in this test (lowest first). */
    private List<Tier> allowed(Player t, TierTests.Test test) {
        Kit kit = this.plugin.kits().get(test.kit());
        Player p = Bukkit.getPlayer(test.player());
        KitStats ks = p == null || kit == null ? null : this.plugin.profiles().get(p).statsOrNull(kit.id);
        int from = ks == null || ks.tier < 0 ? 0 : ks.tier;
        int max = Math.min(Tier.values().length - 1, from + Cfg.i(Opt.TESTER_MAX_ABOVE));
        if (!t.hasPermission("cmcpvp.admin") && kit != null) {
            KitStats mine = this.plugin.profiles().get(t).statsOrNull(kit.id);
            int own = mine == null ? -1 : Math.max(mine.peakTier, mine.tier);
            if (own >= 0) {
                // a tester can't place anyone above the best tier they reached in this kit themselves
                max = Math.min(max, Math.max(own, from));
            }
        }

        List<Tier> out = new ArrayList<>();
        for (Tier x : Tier.values()) {
            if (x.ordinal() <= max) {
                out.add(x);
            }
        }

        return out;
    }

    private void openResult(Player t, TierTests.Test test) {
        Kit kit = this.plugin.kits().get(test.kit());
        if (kit == null) {
            return;
        }

        List<Tier> tiers = this.allowed(t, test);
        if (!ClientVersion.supportsDialogs(t)) {
            StringBuilder sb = new StringBuilder("<#E3172B><b>RESULT</b></#E3172B> <gray>for " + Text.esc(test.playerName()) + ": ");
            for (Tier x : tiers) {
                sb.append("<click:run_command:'/testqueue result ").append(test.playerName()).append(' ').append(x.label).append("'>").append(x.mm())
                    .append("</click> ");
            }

            t.sendMessage(Text.mm(sb.toString()));
            return;
        }

        Dialogs d = this.plugin.dialogs();
        List<ActionButton> buttons = new ArrayList<>();
        for (Tier x : tiers) {
            buttons.add(Dialogs.button(Text.mm(x.mm()), Text.mm("<gray>Place " + Text.esc(test.playerName()) + " at " + x.longName()), 60,
                d.action(t, () -> {
                    t.closeDialog();
                    this.result(t, test.playerName(), x);
                })));
        }

        buttons.add(Dialogs.button(Text.mm("<gray>Keep tier"), Text.mm("<gray>The test changes nothing (the retest cooldown still starts)"), 60,
            d.action(t, () -> {
                t.closeDialog();
                this.result(t, test.playerName(), null);
            })));
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Text.mm("<#E3172B><b>TEST RESULT</b></#E3172B>  <dark_gray>▪</dark_gray>  <white>" + Text.esc(test.playerName()))
            .append(Text.mm(" <gray>in ")).append(kit.label()), 300));
        body.add(DialogBody.plainMessage(Text.mm("<gray>Pick the tier they played at."), 300));
        DialogBase base = DialogBase.builder(Component.empty()).canCloseWithEscape(true).pause(false).afterAction(DialogAfterAction.NONE).body(body).build();
        Dialogs.show(t, base, DialogType.multiAction(buttons).columns(3).exitAction(d.closeButton(t)).build());
    }

    private void result(Player t, String name, Tier tier) {
        TierTests.Test test = this.results.get(t.getUniqueId());
        if (test == null || !test.playerName().equalsIgnoreCase(name)) {
            t.sendMessage(Text.mm("<red>You have no finished test with " + Text.esc(name) + "."));
            return;
        }

        Kit kit = this.plugin.kits().get(test.kit());
        Player p = Bukkit.getPlayer(test.player());
        if (kit == null || p == null) {
            this.results.remove(t.getUniqueId());
            t.sendMessage(Text.mm("<red>" + Text.esc(name) + " left - the result was not saved."));
            return;
        }

        if (tier != null && !this.allowed(t, test).contains(tier)) {
            t.sendMessage(Text.mm("<red>You can't give that tier in this test."));
            return;
        }

        this.results.remove(t.getUniqueId());
        PvPProfile prof = this.plugin.profiles().get(p);
        KitStats ks = prof.stats(kit.id);
        Tier before = ks.tier();
        if (tier != null && tier != before) {
            this.plugin.ranks().adminSetTier(prof, kit, tier);
        }

        ks.lastTest = System.currentTimeMillis();
        prof.dirty = true;
        this.plugin.getLogger().info("Tier test: " + t.getName() + " tested " + p.getName() + " in " + kit.id + ": "
            + (before == null ? "-" : before.label) + " -> " + (tier == null ? "kept" : tier.label));
        Tier now = ks.tier();
        boolean up = now != null && (before == null || now.ordinal() > before.ordinal());
        Component res = tier == null || tier == before ? Text.mm("<gray>kept ").append(Text.mm(now == null ? "<gray>-" : now.mm())) : Text.mm(tier.mm());
        p.sendMessage(Text.mm("<#E3172B><b>TIER TEST</b></#E3172B> <gray>Result in ").append(kit.label()).append(Text.mm("<gray>: ")).append(res)
            .append(Text.mm(" <dark_gray>(tested by " + Text.esc(t.getName()) + ")")));
        t.sendMessage(Text.mm("<green>Saved: <white>" + Text.esc(p.getName()) + "</white> ").append(res));
        if (up) {
            Bukkit.broadcast(Text.mm("<#E3172B>▲ <white>" + Text.esc(p.getName()) + "</white> <gray>was tested ").append(Text.mm(now.mm()))
                .append(Text.mm(" <gray>in ")).append(kit.label()).append(Text.mm(" <gray>by <white>" + Text.esc(t.getName()))));
            LiveFeed.add("rankup", p.getName(), now.label, now.color, kit.displayName);
            Snd.play(p, "ui.toast.challenge_complete", 0.8F, 1.0F);
        }

        this.plugin.tab().refreshSoon();
    }

    // ---------------------------------------------------------------- commands

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("tiertest")) {
            if (args.length == 0) {
                p.sendMessage(Text.mm("<gray>Use <white>/tiertest <kit></white> to wait for a Tier Tester, <white>/tiertest leave</white> to stop waiting."));
            } else if (args[0].equalsIgnoreCase("leave")) {
                this.leave(p.getUniqueId());
                p.sendMessage(Text.mm("<gray>You left the tier test waitlist."));
            } else {
                Kit kit = this.plugin.kits().get(args[0]);
                if (kit == null) {
                    p.sendMessage(Text.mm("<red>No kit called " + Text.esc(args[0]) + "."));
                } else {
                    this.join(p, kit);
                }
            }

            return true;
        }

        if (!p.hasPermission(PERM)) {
            p.sendMessage(Text.mm("<red>Only Tier Testers can do that."));
            return true;
        }

        if (args.length == 0) {
            this.openQueue(p);
        } else if (args[0].equalsIgnoreCase("accept") && args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            Kit kit = null;
            if (args.length >= 3) {
                kit = this.plugin.kits().get(args[2]);
            } else if (target != null) {
                for (String k : this.waiting.keySet()) {
                    if (this.position(k, target.getUniqueId()) > 0) {
                        kit = this.plugin.kits().get(k);
                        break;
                    }
                }
            }

            if (target == null || kit == null) {
                p.sendMessage(Text.mm("<red>That player isn't waiting for a test."));
            } else {
                this.accept(p, target, kit);
            }
        } else if (args[0].equalsIgnoreCase("result") && args.length >= 3) {
            Tier tier = null;
            for (Tier x : Tier.values()) {
                if (x.label.equalsIgnoreCase(args[2])) {
                    tier = x;
                }
            }

            if (tier == null && !args[2].equalsIgnoreCase("keep")) {
                p.sendMessage(Text.mm("<red>Unknown tier " + Text.esc(args[2]) + " (LT5 ... HT1 or keep)."));
            } else {
                this.result(p, args[1], tier);
            }
        } else {
            p.sendMessage(Text.mm("<gray>/testqueue, /testqueue accept <player> [kit], /testqueue result <player> <tier|keep>"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("tiertest") && args.length == 1) {
            out.add("leave");
            for (Kit k : this.plugin.kits().sorted()) {
                if (k.enabled && k.ranked) {
                    out.add(k.id);
                }
            }
        } else if (args.length == 1) {
            out.addAll(List.of("accept", "result"));
        } else if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("result")) {
            out.add("keep");
            for (Tier x : Tier.values()) {
                out.add(x.label);
            }
        }

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
