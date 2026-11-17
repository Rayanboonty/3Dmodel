package net.vortexpvp.plus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.api.MatchEndEvent;
import net.vortexpvp.api.MatchStartEvent;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.KitStats;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.match.Match;
import net.vortexpvp.rank.RankManager;
import net.vortexpvp.social.AltTracker;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * The MCPVP-style rank ladder on top of the rank points: Coal, Copper, Iron, Gold, Lapis, Emerald, Diamond, Netherite,
 * three divisions each, per kit, and an overall rank averaged over every ranked kit. Going up a whole rank takes a
 * promotion series (win 2 of 3); a fresh promotion is protected by a 3-match demotion shield. Ranked results are
 * damped for repeat opponents, voided between alt accounts and below the minimum playtime. Also: win streak flair,
 * head-to-head records, rivals and big-moment announcements.
 */
public final class Ladder implements Listener {
    public static final String[] RANKS = {"Coal", "Copper", "Iron", "Gold", "Lapis", "Emerald", "Diamond", "Netherite"};
    public static final String[] COLORS = {"#5A5A5A", "#D27D4A", "#D8D8D8", "#F2C53D", "#2F5FD0", "#2ECC71", "#5CE1E6", "#7A4E5C"};
    public static final Material[] ICONS = {Material.COAL, Material.COPPER_INGOT, Material.IRON_INGOT, Material.GOLD_INGOT, Material.LAPIS_LAZULI,
        Material.EMERALD, Material.DIAMOND, Material.NETHERITE_INGOT};
    public static final String[] DIV = {"I", "II", "III"};
    public static final int STEP = 280;
    private final VortexPvP plugin;
    private final PlusModule plus;
    /** "winner>loser" -> times of the recent wins (repeat-opponent damping). */
    private final Map<String, LinkedList<Long>> recent = new HashMap<>();
    /** Last known leaderboard place per "uuid:kit" (top-10 announcements). */
    private final Map<String, Integer> places = new HashMap<>();

    public Ladder(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
    }

    // ------------------------------------------------------------------ the ladder itself

    /** 0 = Coal I ... 23 = Netherite III. */
    public static int step(int elo) {
        return Math.max(0, Math.min(23, elo / STEP));
    }

    public static int rankOf(int step) {
        return step / 3;
    }

    public static String name(int step) {
        return RANKS[rankOf(step)] + " " + DIV[step % 3];
    }

    public static String colored(int step) {
        return "<" + COLORS[rankOf(step)] + ">" + name(step) + "</" + COLORS[rankOf(step)] + ">";
    }

    /** Short badge for tab and chat, e.g. "◆ Gold II". */
    public static String badge(int step) {
        return "<" + COLORS[rankOf(step)] + ">◆" + RANKS[rankOf(step)].charAt(0) + (step % 3 + 1) + "</" + COLORS[rankOf(step)] + ">";
    }

    public static int floorOf(int step) {
        return step * STEP;
    }

    public boolean placed(KitStats k) {
        return k.rankedGames >= Cfg.i(Opt.PLACEMENT_MATCHES);
    }

    public List<Kit> rankedKits() {
        List<Kit> out = new ArrayList<>();
        for (Kit k : this.plugin.kits().all()) {
            if (k.enabled && k.ranked) {
                out.add(k);
            }
        }

        out.sort(Comparator.comparingInt(k -> k.order));
        return out;
    }

    /** -1 while still in placement. */
    public int step(PvPProfile prof, String kit) {
        KitStats k = prof.kits.get(kit);
        return k == null || !this.placed(k) ? -1 : step(k.elo);
    }

    /** The overall rank: average over every ranked kit, unplaced kits count as Coal I (like MCPVP). */
    public int overall(PvPProfile prof) {
        List<Kit> kits = this.rankedKits();
        if (kits.isEmpty() || prof == null) {
            return 0;
        }

        int sum = 0;
        for (Kit k : kits) {
            sum += Math.max(0, this.step(prof, k.id));
        }

        return Math.round((float)sum / kits.size());
    }

    public Component tabBadge(UUID u) {
        PvPProfile prof = this.plugin.profiles().getIfLoaded(u);
        return prof == null ? Component.empty() : Text.mm(" " + badge(this.overall(prof)));
    }

    // ------------------------------------------------------------------ ranked results

    /** Runs first: voids, damps and holds rating changes before every other add-on reads them. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEndAdjust(MatchEndEvent e) {
        Match m = e.match();
        RankManager.Outcome[] o = e.outcomes();
        if (!m.ranked || o == null || o.length < 2 || e.winnerId() == null || e.loserId() == null) {
            return;
        }

        PvPProfile wp = this.plugin.profiles().getIfLoaded(e.winnerId());
        PvPProfile lp = this.plugin.profiles().getIfLoaded(e.loserId());
        if (wp == null || lp == null) {
            return;
        }

        KitStats w = wp.stats(m.kit.id);
        KitStats l = lp.stats(m.kit.id);
        PlusConfig c = this.plus.config();
        String why = null;
        // minimum playtime for ranked (minutes the add-on counted)
        int minMinutes = c.i("ranked.min-playtime-minutes", 0);
        if (minMinutes > 0) {
            long a = this.plus.store().get(e.winnerId()).count("playtime");
            long b = this.plus.store().get(e.loserId()).count("playtime");
            if (a < minMinutes || b < minMinutes) {
                why = "a player has less than " + minMinutes + " minutes played";
            }
        }

        if (why == null && c.bool("ranked.block-alts", true) && this.altsKnown(e.winnerId(), e.loserId())) {
            why = "the two accounts share an IP (alts)";
        }

        double keep = 1.0;
        if (why == null && c.bool("ranked.repeat-damping", true)) {
            String key = e.winnerId() + ">" + e.loserId();
            LinkedList<Long> times = this.recent.computeIfAbsent(key, k -> new LinkedList<>());
            long now = System.currentTimeMillis();
            times.removeIf(t -> now - t > 3600000L);
            times.add(now);
            if (times.size() >= 4) {
                keep = 0.0;
            } else if (times.size() == 3) {
                keep = 0.5;
            }
        }

        if (why != null || keep < 1.0) {
            int gain = o[0].newElo() - o[0].oldElo();
            int loss = o[1].oldElo() - o[1].newElo();
            int back = why != null ? gain : (int)Math.round(gain * (1.0 - keep));
            int backL = why != null ? loss : (int)Math.round(loss * (1.0 - keep));
            w.elo = Math.max(0, w.elo - back);
            l.elo = l.elo + backL;
            wp.dirty = true;
            lp.dirty = true;
            String msg = why != null ? "<yellow>⚠ This ranked match didn't count: " + Text.esc(why) + "."
                : "<yellow>⚠ You played each other a lot this hour - rank points " + (keep == 0.0 ? "frozen" : "halved") + " for this match.";
            for (UUID u : new UUID[]{e.winnerId(), e.loserId()}) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    Tasks.later(20L, () -> p.sendMessage(Text.mm(msg)));
                }
            }

            if (why != null) {
                return;
            }
        }

        this.series(e.winnerId(), wp, w, m.kit, true, o[0].oldElo());
        this.series(e.loserId(), lp, l, m.kit, false, o[1].oldElo());
    }

    private boolean altsKnown(UUID a, UUID b) {
        try {
            return this.plugin.db().supply(c -> {
                for (AltTracker.Alt alt : AltTracker.linked(c, a)) {
                    if (alt.uuid().equals(b)) {
                        return true;
                    }
                }

                return false;
            }).get(2L, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception var4) {
            return false;
        }
    }

    /** Promotion series into a new rank (win 2 of 3) and the demotion shield after a promotion. */
    private void series(UUID u, PvPProfile prof, KitStats k, Kit kit, boolean won, int oldElo) {
        if (!this.placed(k)) {
            return;
        }

        PlayerData d = this.plus.store().get(u);
        Player p = Bukkit.getPlayer(u);
        String s = d.series.get(kit.id);
        int oldStep = step(oldElo);
        if (s != null) {
            String[] part = s.split(":");
            int target = Integer.parseInt(part[0]);
            int wins = Integer.parseInt(part[1]) + (won ? 1 : 0);
            int losses = Integer.parseInt(part[2]) + (won ? 0 : 1);
            int boundary = floorOf(target * 3);
            if (wins >= 2) {
                d.series.remove(kit.id);
                k.elo = Math.max(k.elo, boundary);
                d.shield.put(kit.id, 3);
                this.say(p, "<gradient:#FFD700:#FF6B00><b>PROMOTED!</b></gradient> <gray>You are now " + colored(target * 3) + " <gray>in " + kit.displayName
                    + " <dark_gray>(3-match demotion shield)");
                this.bigMoment(u, p, target, kit);
            } else if (losses >= 2) {
                d.series.remove(kit.id);
                this.say(p, "<red>Promotion series lost <gray>(" + wins + "-" + losses + ") - win your way back to try again.");
            } else {
                d.series.put(kit.id, target + ":" + wins + ":" + losses);
                k.elo = Math.min(k.elo, boundary - 1);
                this.say(p, "<gold>Promotion series to " + colored(target * 3) + "<gold>: <white>" + wins + "W - " + losses + "L <gray>(first to 2 wins)");
            }

            d.dirty = true;
            prof.dirty = true;
            return;
        }

        int newStep = step(k.elo);
        if (won && rankOf(newStep) > rankOf(oldStep)) {
            // crossing into a new rank: hold at the line and start the series
            int target = rankOf(oldStep) + 1;
            k.elo = floorOf(target * 3) - 1;
            prof.dirty = true;
            d.series.put(kit.id, target + ":1:0");
            d.dirty = true;
            this.say(p, "<gold>⚔ Promotion series to " + colored(target * 3) + "<gold> started! <white>1W - 0L <gray>- win 1 more of the next 2.");
            Snd.play(p, "block.note_block.pling", 1.0F, 1.6F);
            return;
        }

        Integer shield = d.shield.get(kit.id);
        if (shield != null && shield > 0) {
            if (!won && newStep < oldStep) {
                k.elo = floorOf(oldStep);
                prof.dirty = true;
                this.say(p, "<aqua>🛡 Demotion shield kept you in " + colored(oldStep) + " <gray>(" + (shield - 1) + " left)");
            }

            d.shield.put(kit.id, shield - 1);
            d.dirty = true;
        } else if (won && newStep > oldStep) {
            this.say(p, "<green>▲ " + colored(newStep) + " <gray>in " + kit.displayName);
        } else if (!won && newStep < oldStep) {
            this.say(p, "<red>▼ " + colored(newStep) + " <gray>in " + kit.displayName);
        }
    }

    private void say(Player p, String mini) {
        if (p != null) {
            Tasks.later(30L, () -> p.sendMessage(Text.mm(mini)));
        }
    }

    private void bigMoment(UUID u, Player p, int rank, Kit kit) {
        PlayerData d = this.plus.store().get(u);
        String key = "reached." + RANKS[rank].toLowerCase();
        if (rank >= 6 && d.count(key) == 0) {
            d.add(key, 1);
            String name = p == null ? d.name : p.getName();
            Bukkit.broadcast(Text.mm("<gradient:#FFD700:#FF6B00><b>★ " + Text.esc(name) + " reached " + RANKS[rank].toUpperCase() + "</b></gradient> <gray>for the first time ("
                + kit.displayName + "<gray>)!"));
            if (p != null) {
                net.vortexpvp.fx.Fx.fireworks(p.getLocation(), 5, Color.fromRGB(Integer.parseInt(COLORS[rank].substring(1), 16)), Color.WHITE);
            }

            this.plus.discord().post("🏆 **" + name + "** reached **" + RANKS[rank] + "** in " + Text.plain(Text.mm(kit.displayName)) + " for the first time!");
        }
    }

    /** Runs after the adjustments: streaks, head-to-head, rivals, season peaks, progression. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEnd(MatchEndEvent e) {
        Match m = e.match();
        if (e.winnerId() == null || e.loserId() == null) {
            return;
        }

        PlayerData wd = this.plus.store().get(e.winnerId());
        PlayerData ld = this.plus.store().get(e.loserId());
        int[] a = wd.h2h(e.loserId());
        wd.h2h.put(e.loserId().toString(), (a[0] + 1) + ":" + a[1]);
        int[] b = ld.h2h(e.winnerId());
        ld.h2h.put(e.winnerId().toString(), b[0] + ":" + (b[1] + 1));
        wd.dirty = true;
        ld.dirty = true;
        UUID rival = this.rival(wd);
        if (e.loserId().equals(rival)) {
            this.plus.progression().track(e.winnerId(), "rival_win", null, 1L);
            this.plus.progression().xp(e.winnerId(), 50L, "beat your rival");
        }

        if (!m.ranked) {
            return;
        }

        // ranked win streak (any kit) and the streak-ender announcement
        long streak = wd.count("ranked_streak") + 1;
        wd.counters.put("ranked_streak", streak);
        this.plus.progression().best(e.winnerId(), "best_ranked_streak", streak);
        long lost = ld.count("ranked_streak");
        ld.counters.put("ranked_streak", 0L);
        Player wpl = Bukkit.getPlayer(e.winnerId());
        String wn = wpl == null ? wd.name : wpl.getName();
        if (lost >= 5) {
            Bukkit.broadcast(Text.mm("<gold>🔥 <white>" + Text.esc(wn) + "</white> <gray>ended <white>" + Text.esc(ld.name) + "</white><gray>'s <red>" + lost + "-win</red> ranked streak!"));
        }

        if (streak == 5 || streak == 10 || streak == 20) {
            Bukkit.broadcast(Text.mm("<gold>🔥 <white>" + Text.esc(wn) + "</white> <gray>is on a <red>" + streak + "-win</red> ranked streak!"));
            this.plus.discord().post("🔥 **" + wn + "** is on a " + streak + "-win ranked streak!");
        }

        for (UUID u : new UUID[]{e.winnerId(), e.loserId()}) {
            PvPProfile prof = this.plugin.profiles().getIfLoaded(u);
            if (prof != null) {
                int st = this.step(prof, m.kit.id);
                PlayerData d = this.plus.store().get(u);
                if (st >= 0) {
                    d.seasonPeak.merge(m.kit.id, st, Math::max);
                    this.plus.progression().best(u, "ladder." + m.kit.id, st);
                    d.dirty = true;
                }
            }
        }
    }

    /** The opponent this player has played most (at least 5 games). */
    public UUID rival(PlayerData d) {
        UUID best = null;
        int most = 4;
        for (Map.Entry<String, String> en : d.h2h.entrySet()) {
            String[] p = en.getValue().split(":");
            int n = Integer.parseInt(p[0]) + Integer.parseInt(p[1]);
            if (n > most) {
                most = n;
                try {
                    best = UUID.fromString(en.getKey());
                } catch (IllegalArgumentException var8) {
                }
            }
        }

        return best;
    }

    @EventHandler
    public void onStart(MatchStartEvent e) {
        Match m = e.match();
        if (!m.isOneVsOne()) {
            return;
        }

        UUID a = m.teams.get(0).members.get(0);
        UUID b = m.teams.get(1).members.get(0);
        this.tellH2h(a, b);
        this.tellH2h(b, a);
        // ranked matches between players with very different pings get the ping equalizer
        int maxGap = this.plus.config().i("ranked.equalize-ping-gap", 80);
        Player pa = Bukkit.getPlayer(a);
        Player pb = Bukkit.getPlayer(b);
        if (m.ranked && maxGap > 0 && pa != null && pb != null && Math.abs(pa.getPing() - pb.getPing()) > maxGap) {
            m.pingEqualizer = true;
        }
    }

    private void tellH2h(UUID me, UUID other) {
        Player p = Bukkit.getPlayer(me);
        if (p == null) {
            return;
        }

        PlayerData d = this.plus.store().get(me);
        int[] r = d.h2h(other);
        if (r[0] + r[1] > 0) {
            String name = this.plus.store().get(other).name;
            boolean rival = other.equals(this.rival(d));
            p.sendMessage(Text.mm("<gray>Head-to-head vs <white>" + Text.esc(name) + "</white>: <green>" + r[0] + "</green> <dark_gray>-</dark_gray> <red>" + r[1]
                + "</red>" + (rival ? " <gold>⚔ your rival" : "")));
        }
    }

    /** Every minute: playtime, and top-10 entries since the last leaderboard refresh. */
    public void minute() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = this.plus.store().get(p);
            d.add("playtime", 1L);
            for (Kit k : this.rankedKits()) {
                int pos;
                try {
                    pos = this.plugin.leaderboards().position(k.id, p.getUniqueId());
                } catch (RuntimeException var8) {
                    continue;
                }

                String key = p.getUniqueId() + ":" + k.id;
                Integer before = this.places.put(key, pos);
                if (pos > 0 && pos <= 10 && before != null && (before <= 0 || before > 10)) {
                    Bukkit.broadcast(Text.mm("<gold>★ <white>" + Text.esc(p.getName()) + "</white> <gray>entered the <gold>top 10</gold> of " + k.displayName + " <gray>(#" + pos + ")"));
                }
            }
        }
    }

    // ------------------------------------------------------------------ the rank card

    public void card(Player viewer, UUID target) {
        PvPProfile prof = this.plugin.profiles().getIfLoaded(target);
        if (prof == null) {
            try {
                prof = this.plugin.db().supply(c -> this.plugin.db().loadProfile(c, target, this.plus.store().get(target).name)).get(3L, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception var6) {
                this.plugin.getLogger().log(Level.FINE, "rank card", var6);
            }
        }

        PvPProfile pr = prof;
        PlayerData d = this.plus.store().get(target);
        List<Kit> kits = this.rankedKits();
        int overall = pr == null ? 0 : this.overall(pr);
        Gui.open(this.plugin, viewer, "<dark_gray>Rank of " + Text.esc(d.name), 6, g -> {
            UUID rival = this.rival(d);
            g.item(4, Items.of(ICONS[rankOf(overall)]).name("<b>" + colored(overall) + "</b> <gray>overall")
                .lore("<gray>Player: <white>" + Text.esc(d.name), "<gray>Level: <white>" + Progression.level(d.xp),
                    "<gray>Ranked streak: <white>" + d.count("ranked_streak") + " <dark_gray>(best " + d.count("best_ranked_streak") + ")",
                    "<gray>Clan: <white>" + (d.clan.isEmpty() ? "-" : Text.esc(d.clan)),
                    "<gray>Rival: <white>" + (rival == null ? "-" : Text.esc(this.plus.store().get(rival).name)),
                    "<gray>Title: <light_purple>" + (d.title.isEmpty() ? "-" : Text.esc(d.title)))
                .build());
            int[] slots = g.inner();
            for (int i = 0; i < kits.size() && i < slots.length; i++) {
                Kit k = kits.get(i);
                KitStats ks = pr == null ? null : pr.kits.get(k.id);
                List<String> lore = new ArrayList<>();
                if (ks == null || !this.placed(ks)) {
                    lore.add("<gray>Placement: <white>" + (ks == null ? 0 : ks.rankedGames) + "/" + Cfg.i(Opt.PLACEMENT_MATCHES));
                } else {
                    int st = step(ks.elo);
                    lore.add(colored(st) + " <dark_gray>• <gray>" + ks.elo + " pts");
                    int next = st < 23 ? floorOf(st + 1) - ks.elo : 0;
                    lore.add(st < 23 ? "<gray>Next division in <white>" + next + " pts" : "<gold>Top of the ladder");
                    lore.add("<gray>Wins <green>" + ks.wins + " <gray>Losses <red>" + ks.losses);
                    String s = d.series.get(k.id);
                    if (s != null) {
                        String[] p = s.split(":");
                        lore.add("<gold>Promotion series: " + p[1] + "W-" + p[2] + "L");
                    }

                    Integer sh = d.shield.get(k.id);
                    if (sh != null && sh > 0) {
                        lore.add("<aqua>🛡 Shield: " + sh + " matches");
                    }

                    Integer peak = d.seasonPeak.get(k.id);
                    if (peak != null) {
                        lore.add("<gray>Season peak: " + colored(peak));
                    }
                }

                g.item(slots[i], Items.of(ks != null && this.placed(ks) ? ICONS[rankOf(step(ks.elo))] : Material.GRAY_DYE).name(k.displayName).lore(lore).build());
            }
        });
    }

    /** Country leaderboard: the best overall ranks among players of one country (online and cached). */
    public List<String[]> countryTop(String code) {
        List<String[]> out = new ArrayList<>();
        for (PvPProfile prof : this.plugin.profiles().loaded()) {
            net.vortexpvp.social.Countries.Country c = this.plugin.countries() == null ? null : this.plugin.countries().of(prof.uuid);
            if (c != null && c.code().equalsIgnoreCase(code)) {
                out.add(new String[]{prof.name, String.valueOf(this.overall(prof))});
            }
        }

        out.sort((x, y) -> Integer.parseInt(y[1]) - Integer.parseInt(x[1]));
        return out;
    }

    public static String selfCheck() {
        if (step(0) != 0 || step(STEP * 9) != 9 || !name(9).equals("Gold I") || step(99999) != 23 || !name(23).equals("Netherite III")) {
            return "ladder mapping";
        }

        return null;
    }
}
