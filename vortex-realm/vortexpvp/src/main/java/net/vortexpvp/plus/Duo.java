package net.vortexpvp.plus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.api.MatchEndEvent;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.match.Match;
import net.vortexpvp.match.MatchTeam;
import net.vortexpvp.party.Party;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Ranked 2v2: two players team up as a duo (/duo invite), queue together for a kit and are matched with a duo of a
 * similar team rating (the range widens while waiting). Each duo has its own rating, wins and losses and a top list.
 * Also party vs party: a party leader challenges another party to a team fight (/partywar).
 */
public final class Duo implements Listener {
    private final VortexPvP plugin;
    private final PlusModule plus;
    private final Map<UUID, UUID> invites = new HashMap<>();
    private final Map<UUID, UUID> partner = new HashMap<>();
    private final List<Entry> queue = new ArrayList<>();
    private final Map<Integer, String[]> duoMatches = new HashMap<>();
    private final Map<UUID, UUID> warInvites = new HashMap<>();
    private final Map<UUID, String> warKits = new HashMap<>();

    private record Entry(UUID a, UUID b, Kit kit, long since) {
    }

    public Duo(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
    }

    static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    private YamlConfiguration doc() {
        return this.plus.store().doc("duos");
    }

    public int elo(String key) {
        return this.doc().getInt(key.replace("|", "_") + ".elo", 1000);
    }

    public void command(Player p, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "invite" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (t == null || t == p) {
                    p.sendMessage(Text.mm("<red>/duo invite <online player>"));
                    return;
                }

                this.invites.put(t.getUniqueId(), p.getUniqueId());
                p.sendMessage(Text.mm("<green>Duo invite sent to " + Text.esc(t.getName()) + "."));
                t.sendMessage(Text.mm("<aqua>" + Text.esc(p.getName()) + " <gray>wants to be your ranked 2v2 duo. <click:run_command:'/duo accept'><green>[accept]</green></click>"));
            }
            case "accept" -> {
                UUID from = this.invites.remove(p.getUniqueId());
                Player f = from == null ? null : Bukkit.getPlayer(from);
                if (f == null) {
                    p.sendMessage(Text.mm("<red>No duo invite."));
                    return;
                }

                this.leave(p, true);
                this.leave(f, true);
                this.partner.put(p.getUniqueId(), f.getUniqueId());
                this.partner.put(f.getUniqueId(), p.getUniqueId());
                String k = key(p.getUniqueId(), f.getUniqueId());
                String msg = "<green>✔ Duo formed: <white>" + Text.esc(f.getName()) + " & " + Text.esc(p.getName()) + " <gray>(team rating " + this.elo(k) + "). <white>/duo queue <kit>";
                p.sendMessage(Text.mm(msg));
                f.sendMessage(Text.mm(msg));
            }
            case "leave" -> this.leave(p, false);
            case "queue" -> {
                UUID mate = this.partner.get(p.getUniqueId());
                Player m = mate == null ? null : Bukkit.getPlayer(mate);
                if (m == null) {
                    p.sendMessage(Text.mm("<red>Make a duo first: /duo invite <player>"));
                    return;
                }

                Kit kit = a.length > 1 ? this.plugin.kits().get(a[1].toLowerCase(Locale.ROOT)) : null;
                if (kit == null || !kit.enabled || !kit.ranked) {
                    StringBuilder sb = new StringBuilder();
                    for (Kit k : this.plus.ladder().rankedKits()) {
                        sb.append(sb.length() == 0 ? "" : ", ").append(k.id);
                    }

                    p.sendMessage(Text.mm("<red>/duo queue <kit> <gray>- " + sb));
                    return;
                }

                if (this.plugin.sessions().state(p) != PlayerState.LOBBY || this.plugin.sessions().state(m) != PlayerState.LOBBY) {
                    p.sendMessage(Text.mm("<red>Both of you must be in the lobby."));
                    return;
                }

                this.queue.removeIf(e -> e.a.equals(p.getUniqueId()) || e.b.equals(p.getUniqueId()));
                this.queue.add(new Entry(p.getUniqueId(), m.getUniqueId(), kit, System.currentTimeMillis()));
                String msg = "<green>Queued for ranked 2v2 " + Text.plain(Text.mm(kit.displayName)) + " <gray>(team rating " + this.elo(key(p.getUniqueId(), m.getUniqueId()))
                    + ") <click:run_command:'/duo unqueue'><red>[leave]</red></click>";
                p.sendMessage(Text.mm(msg));
                m.sendMessage(Text.mm(msg));
            }
            case "unqueue" -> {
                if (this.queue.removeIf(e -> e.a.equals(p.getUniqueId()) || e.b.equals(p.getUniqueId()))) {
                    p.sendMessage(Text.mm("<gray>Left the 2v2 queue."));
                }
            }
            case "top" -> this.top(p);
            default -> {
                UUID mate = this.partner.get(p.getUniqueId());
                if (mate != null) {
                    String k = key(p.getUniqueId(), mate);
                    String n = Bukkit.getOfflinePlayer(mate).getName();
                    p.sendMessage(Text.mm("<aqua>Your duo: <white>" + Text.esc(n == null ? "?" : n) + " <gray>rating <white>" + this.elo(k) + " <gray>(" + this.doc().getInt(k.replace("|", "_") + ".wins")
                        + "W " + this.doc().getInt(k.replace("|", "_") + ".losses") + "L)"));
                }

                p.sendMessage(Text.mm("<gray>/duo invite <player> • /duo accept • /duo queue <kit> • /duo unqueue • /duo leave • /duo top"));
            }
        }
    }

    private void leave(Player p, boolean quiet) {
        UUID mate = this.partner.remove(p.getUniqueId());
        this.queue.removeIf(e -> e.a.equals(p.getUniqueId()) || e.b.equals(p.getUniqueId()));
        if (mate != null) {
            this.partner.remove(mate);
            Player m = Bukkit.getPlayer(mate);
            if (m != null && !quiet) {
                m.sendMessage(Text.mm("<gray>" + Text.esc(p.getName()) + " left your duo."));
            }

            if (!quiet) {
                p.sendMessage(Text.mm("<gray>You left your duo."));
            }
        }
    }

    /** Every second: pairs duos of the same kit with close ratings. */
    public void second() {
        this.queue.removeIf(e -> {
            Player a = Bukkit.getPlayer(e.a);
            Player b = Bukkit.getPlayer(e.b);
            return a == null || b == null || this.plugin.sessions().state(a) != PlayerState.LOBBY || this.plugin.sessions().state(b) != PlayerState.LOBBY;
        });
        for (int i = 0; i < this.queue.size(); i++) {
            Entry x = this.queue.get(i);
            int ex = this.elo(key(x.a, x.b));
            for (int j = i + 1; j < this.queue.size(); j++) {
                Entry y = this.queue.get(j);
                if (y.kit != x.kit) {
                    continue;
                }

                long waited = (System.currentTimeMillis() - Math.min(x.since, y.since)) / 1000L;
                int range = 100 + (int)(waited / 5L) * 25;
                if (Math.abs(ex - this.elo(key(y.a, y.b))) <= range) {
                    this.queue.remove(j);
                    this.queue.remove(i);
                    this.start(x, y);
                    return;
                }
            }
        }
    }

    private void start(Entry x, Entry y) {
        Match m = this.plugin.matches().startParty(List.of(List.of(x.a, x.b), List.of(y.a, y.b)), x.kit, Match.Type.PARTY_SPLIT, null);
        if (m != null) {
            this.duoMatches.put(m.id, new String[]{key(x.a, x.b), key(y.a, y.b)});
            for (UUID u : List.of(x.a, x.b, y.a, y.b)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    p.sendMessage(Text.mm("<gold>⚔ Ranked 2v2 found! <gray>" + this.elo(key(x.a, x.b)) + " vs " + this.elo(key(y.a, y.b))));
                }
            }
        }
    }

    @EventHandler
    public void onEnd(MatchEndEvent e) {
        String[] keys = this.duoMatches.remove(e.match().id);
        if (keys == null || e.winner() == null || "CANCELLED".equals(e.reason())) {
            return;
        }

        int wi = e.match().teams.indexOf(e.winner());
        if (wi < 0 || wi > 1) {
            return;
        }

        String w = keys[wi];
        String l = keys[1 - wi];
        int ew = this.elo(w);
        int el = this.elo(l);
        double expected = 1.0 / (1.0 + Math.pow(10.0, (el - ew) / 400.0));
        int delta = Math.max(1, (int)Math.round(32.0 * (1.0 - expected)));
        YamlConfiguration y = this.doc();
        y.set(w.replace("|", "_") + ".elo", ew + delta);
        y.set(w.replace("|", "_") + ".wins", y.getInt(w.replace("|", "_") + ".wins") + 1);
        y.set(l.replace("|", "_") + ".elo", Math.max(0, el - delta));
        y.set(l.replace("|", "_") + ".losses", y.getInt(l.replace("|", "_") + ".losses") + 1);
        for (String k : List.of(w, l)) {
            List<String> names = new ArrayList<>();
            for (String id : k.split("\\|")) {
                String n = Bukkit.getOfflinePlayer(UUID.fromString(id)).getName();
                names.add(n == null ? "?" : n);
            }

            y.set(k.replace("|", "_") + ".names", String.join(" & ", names));
        }

        this.plus.store().dirty("duos");
        for (MatchTeam t : e.match().teams) {
            boolean won = t == e.winner();
            for (UUID u : t.members) {
                this.plus.progression().track(u, won ? "duo_win" : "duo_play", e.match().kit.id, 1L);
                if (won) {
                    this.plus.progression().track(u, "ranked_win", e.match().kit.id, 1L);
                }

                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    p.sendMessage(Text.mm(won ? "<green>2v2 team rating +" + delta + " <gray>(" + (ew + delta) + ")" : "<red>2v2 team rating -" + delta + " <gray>(" + Math.max(0, el - delta) + ")"));
                }
            }
        }
    }

    public void top(Player p) {
        YamlConfiguration y = this.doc();
        List<String[]> rows = new ArrayList<>();
        for (String k : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(k);
            if (s != null) {
                rows.add(new String[]{s.getString("names", "?"), String.valueOf(s.getInt("elo", 1000)), s.getInt("wins") + "W " + s.getInt("losses") + "L"});
            }
        }

        rows.sort((x, z) -> Integer.parseInt(z[1]) - Integer.parseInt(x[1]));
        p.sendMessage(Text.mm("<gold><b>Ranked 2v2 - top duos"));
        for (int i = 0; i < Math.min(10, rows.size()); i++) {
            p.sendMessage(Text.mm("<gray>#" + (i + 1) + " <white>" + Text.esc(rows.get(i)[0]) + " <dark_gray>- <gold>" + rows.get(i)[1] + " <gray>" + rows.get(i)[2]));
        }

        if (rows.isEmpty()) {
            p.sendMessage(Text.mm("<gray>No 2v2 games yet."));
        }
    }

    // ------------------------------------------------------------------ party vs party

    public void partyWar(Player p, String[] a) {
        Party mine = this.plugin.parties().of(p);
        if (a.length >= 1 && a[0].equalsIgnoreCase("accept")) {
            UUID from = this.warInvites.remove(p.getUniqueId());
            Player leader = from == null ? null : Bukkit.getPlayer(from);
            Party theirs = leader == null ? null : this.plugin.parties().of(leader);
            if (theirs == null || mine == null || !mine.isLeader(p)) {
                p.sendMessage(Text.mm("<red>No party war invite (or you are not the party leader)."));
                return;
            }

            Kit kit = this.plugin.kits().get(this.warKits.getOrDefault(from, "sword"));
            if (kit == null) {
                kit = this.plus.ladder().rankedKits().isEmpty() ? null : this.plus.ladder().rankedKits().get(0);
            }

            List<UUID> t1 = new ArrayList<>();
            List<UUID> t2 = new ArrayList<>();
            for (Player x : theirs.online()) {
                if (this.plugin.sessions().state(x) == PlayerState.LOBBY) {
                    t1.add(x.getUniqueId());
                }
            }

            for (Player x : mine.online()) {
                if (this.plugin.sessions().state(x) == PlayerState.LOBBY) {
                    t2.add(x.getUniqueId());
                }
            }

            if (t1.isEmpty() || t2.isEmpty() || kit == null) {
                p.sendMessage(Text.mm("<red>Both parties need players in the lobby."));
                return;
            }

            this.plugin.matches().startParty(List.of(t1, t2), kit, Match.Type.PARTY_SPLIT, null);
            return;
        }

        if (mine == null || !mine.isLeader(p)) {
            p.sendMessage(Text.mm("<red>Lead a party first (/party create)."));
            return;
        }

        Player target = a.length > 0 ? Bukkit.getPlayerExact(a[0]) : null;
        Party theirs = target == null ? null : this.plugin.parties().of(target);
        if (theirs == null || theirs == mine || !theirs.isLeader(target)) {
            p.sendMessage(Text.mm("<red>/partywar <other party leader> [kit]"));
            return;
        }

        this.warInvites.put(target.getUniqueId(), p.getUniqueId());
        this.warKits.put(p.getUniqueId(), a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "sword");
        p.sendMessage(Text.mm("<green>Party war challenge sent to " + Text.esc(target.getName()) + "'s party."));
        target.sendMessage(Text.mm("<gold>⚔ " + Text.esc(p.getName()) + "'s party (" + mine.online().size() + ") challenges yours to a party war! "
            + "<click:run_command:'/partywar accept'><green>[accept]</green></click>"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.leave(e.getPlayer(), false);
        this.invites.remove(e.getPlayer().getUniqueId());
        this.warInvites.remove(e.getPlayer().getUniqueId());
        Iterator<Map.Entry<UUID, UUID>> it = this.invites.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().equals(e.getPlayer().getUniqueId())) {
                it.remove();
            }
        }
    }

    public String selfCheck() {
        UUID a = new UUID(1, 2);
        UUID b = new UUID(3, 4);
        return key(a, b).equals(key(b, a)) ? null : "duo key";
    }
}
