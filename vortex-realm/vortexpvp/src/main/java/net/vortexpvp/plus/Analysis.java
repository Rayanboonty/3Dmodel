package net.vortexpvp.plus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.api.MatchEndEvent;
import net.vortexpvp.api.MatchStartEvent;
import net.vortexpvp.match.Match;
import net.vortexpvp.match.MatchStats;
import net.vortexpvp.match.MatchTeam;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;

/**
 * Post-match analysis (hits, accuracy, combos, damage, potions, pearls, W-taps, blocks, length) compared with the
 * player's last 20 matches, the /lastmatch screen, the optional match HUD, and click tracking (CPS) that the drills,
 * coach mode and the anti-cheat read. Also counts matches for XP, missions and achievements.
 */
public final class Analysis implements Listener {
    private final VortexPvP plugin;
    private final PlusModule plus;
    private final Map<UUID, Deque<Long>> clicks = new HashMap<>();
    private final Map<UUID, int[]> live = new HashMap<>();
    private final Map<UUID, Long> sprintStart = new HashMap<>();

    public Analysis(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
    }

    /** Clicks in the last second. */
    public int cps(UUID u) {
        Deque<Long> d = this.clicks.get(u);
        if (d == null) {
            return 0;
        }

        long now = System.currentTimeMillis();
        while (!d.isEmpty() && now - d.peekFirst() > 1000L) {
            d.pollFirst();
        }

        return d.size();
    }

    public List<Long> recentClicks(UUID u) {
        Deque<Long> d = this.clicks.get(u);
        return d == null ? List.of() : new ArrayList<>(d);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            UUID u = e.getPlayer().getUniqueId();
            Deque<Long> d = this.clicks.computeIfAbsent(u, k -> new ArrayDeque<>());
            d.addLast(System.currentTimeMillis());
            while (d.size() > 60) {
                d.pollFirst();
            }

            int[] s = this.live.get(u);
            if (s != null) {
                s[0]++;
            }
        }
    }

    @EventHandler
    public void onSprint(PlayerToggleSprintEvent e) {
        if (e.isSprinting()) {
            this.sprintStart.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    /** True if the player started sprinting again within the last half second (a W-tap / sprint reset). */
    public boolean freshSprint(Player p) {
        Long t = this.sprintStart.get(p.getUniqueId());
        return p.isSprinting() && t != null && System.currentTimeMillis() - t < 500L;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player a) {
            int[] s = this.live.get(a.getUniqueId());
            if (s != null && this.freshSprint(a)) {
                s[1]++;
            }
        }

        if (e.getEntity() instanceof Player v && v.isBlocking()) {
            int[] s = this.live.get(v.getUniqueId());
            if (s != null) {
                s[2]++;
            }
        }
    }

    @EventHandler
    public void onStart(MatchStartEvent e) {
        for (Player p : e.match().participantsOnline()) {
            // swings, w-taps, blocks
            this.live.put(p.getUniqueId(), new int[3]);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.clicks.remove(e.getPlayer().getUniqueId());
        this.live.remove(e.getPlayer().getUniqueId());
        this.sprintStart.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onEnd(MatchEndEvent e) {
        Match m = e.match();
        if ("CANCELLED".equals(e.reason())) {
            for (MatchTeam t : m.teams) {
                for (UUID u : t.members) {
                    this.live.remove(u);
                }
            }

            return;
        }

        long seconds = (long)m.durationSeconds();
        for (MatchTeam t : m.teams) {
            for (UUID u : t.members) {
                MatchStats st = m.stats(u);
                int[] extra = this.live.remove(u);
                if (st == null) {
                    continue;
                }

                boolean won = e.winner() == t;
                Progression pr = this.plus.progression();
                pr.track(u, "play", m.kit.id, 1L);
                pr.track(u, "hits", m.kit.id, st.hits);
                pr.track(u, "pearls", null, st.pearlsThrown);
                if (st.longestCombo >= 5) {
                    pr.track(u, "combo_match", null, 1L);
                }

                pr.best(u, "best_combo", st.longestCombo);
                long xp = 10L + Math.min(20, st.hits / 5);
                if (won) {
                    pr.track(u, "win", m.kit.id, 1L);
                    xp += 15L;
                    if (m.ranked) {
                        pr.track(u, "ranked_win", m.kit.id, 1L);
                        xp += 25L;
                    }
                }

                pr.xp(u, xp, won ? "win" : "match played");
                int swings = extra == null ? 0 : extra[0];
                int wtaps = extra == null ? 0 : extra[1];
                int blocks = extra == null ? 0 : extra[2];
                int acc = swings <= 0 ? 0 : (int)Math.min(100L, Math.round(100.0 * st.hits / swings));
                Map<String, String> sum = new LinkedHashMap<>();
                sum.put("id", String.valueOf(m.id));
                sum.put("kit", m.kit.id);
                sum.put("ranked", String.valueOf(m.ranked));
                sum.put("won", String.valueOf(won));
                sum.put("hits", String.valueOf(st.hits));
                sum.put("swings", String.valueOf(swings));
                sum.put("acc", String.valueOf(acc));
                sum.put("crits", String.valueOf(st.criticalHits));
                sum.put("combo", String.valueOf(st.longestCombo));
                sum.put("dealt", String.valueOf(Math.round(st.damageDealt)));
                sum.put("taken", String.valueOf(Math.round(st.damageTaken)));
                sum.put("pots", String.valueOf(st.potionsUsed));
                sum.put("gaps", String.valueOf(st.gapplesEaten));
                sum.put("pearls", String.valueOf(st.pearlsThrown));
                sum.put("totems", String.valueOf(st.totemsPopped));
                sum.put("wtaps", String.valueOf(wtaps));
                sum.put("blocks", String.valueOf(blocks));
                sum.put("secs", String.valueOf(seconds));
                String replay = this.plus.replays() == null ? null : this.plus.replays().idFor(m.id);
                if (replay != null) {
                    sum.put("replay", replay);
                }

                PlayerData d = this.plus.store().get(u);
                Map<String, Double> avg = this.average(d.history);
                d.history.add(0, encode(sum));
                while (d.history.size() > 20) {
                    d.history.remove(d.history.size() - 1);
                }

                d.dirty = true;
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    net.vortexpvp.util.Tasks.later(40L, () -> this.sendCard(p, sum, avg));
                }
            }
        }
    }

    static String encode(Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        m.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : ";").append(k).append('=').append(v.replace(";", ",")));
        return sb.toString();
    }

    static Map<String, String> decode(String s) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String part : s.split(";")) {
            int i = part.indexOf('=');
            if (i > 0) {
                m.put(part.substring(0, i), part.substring(i + 1));
            }
        }

        return m;
    }

    private Map<String, Double> average(List<String> history) {
        Map<String, Double> sum = new HashMap<>();
        int n = 0;
        for (String h : history) {
            Map<String, String> m = decode(h);
            n++;
            for (String k : List.of("hits", "acc", "combo", "dealt", "wtaps")) {
                try {
                    sum.merge(k, Double.parseDouble(m.getOrDefault(k, "0")), Double::sum);
                } catch (NumberFormatException var9) {
                }
            }
        }

        if (n > 0) {
            int count = n;
            sum.replaceAll((k, v) -> v / count);
        }

        return sum;
    }

    private static String vs(String key, Map<String, String> s, Map<String, Double> avg) {
        Double a = avg.get(key);
        if (a == null) {
            return "";
        }

        double v = Double.parseDouble(s.getOrDefault(key, "0"));
        double diff = v - a;
        if (Math.abs(diff) < 0.5) {
            return " <dark_gray>(=avg)";
        } else {
            return diff > 0 ? " <green>(+" + Math.round(diff) + " vs avg)" : " <red>(" + Math.round(diff) + " vs avg)";
        }
    }

    private void sendCard(Player p, Map<String, String> s, Map<String, Double> avg) {
        if (!p.isOnline()) {
            return;
        }

        List<String> lines = List.of(
            "<gold><b>Match analysis</b> <gray>(" + s.get("kit") + (Boolean.parseBoolean(s.get("ranked")) ? ", ranked" : "") + ")",
            "<gray>Hits: <white>" + s.get("hits") + vs("hits", s, avg) + " <dark_gray>• <gray>Accuracy: <white>" + s.get("acc") + "%" + vs("acc", s, avg),
            "<gray>Longest combo: <white>" + s.get("combo") + vs("combo", s, avg) + " <dark_gray>• <gray>Crits: <white>" + s.get("crits"),
            "<gray>Damage dealt: <white>" + s.get("dealt") + vs("dealt", s, avg) + " <dark_gray>• <gray>taken: <white>" + s.get("taken"),
            "<gray>W-taps: <white>" + s.get("wtaps") + vs("wtaps", s, avg) + " <dark_gray>• <gray>Blocks: <white>" + s.get("blocks"),
            "<gray>Pots: <white>" + s.get("pots") + " <dark_gray>• <gray>Gapples: <white>" + s.get("gaps") + " <dark_gray>• <gray>Pearls: <white>" + s.get("pearls")
                + " <dark_gray>• <gray>Length: <white>" + s.get("secs") + "s"
        );
        Component hover = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            hover = hover.append(Text.mm(lines.get(i)));
            if (i < lines.size() - 1) {
                hover = hover.append(Component.newline());
            }
        }

        Component msg = Text.mm("<gold>📊 <white>Hits " + s.get("hits") + " <dark_gray>• <white>Acc " + s.get("acc") + "% <dark_gray>• <white>Combo " + s.get("combo")
                + " <dark_gray>• <white>W-taps " + s.get("wtaps") + " <yellow>[details]")
            .hoverEvent(HoverEvent.showText(hover))
            .clickEvent(ClickEvent.runCommand("/lastmatch"));
        p.sendMessage(msg);
        String rid = s.get("replay");
        if (rid != null) {
            p.sendMessage(Text.mm("<gray>🎬 Replay saved: <click:run_command:'/replay " + rid + "'><aqua>[watch #" + rid + "]</aqua></click>"));
        }
    }

    public void lastMatch(Player p, int index) {
        PlayerData d = this.plus.store().get(p);
        if (d.history.isEmpty()) {
            p.sendMessage(Text.mm("<gray>No matches yet."));
            return;
        }

        Gui.open(this.plugin, p, "<dark_gray>Match History", 4, g -> {
            int[] slots = g.inner();
            for (int i = 0; i < slots.length && i < d.history.size(); i++) {
                Map<String, String> s = decode(d.history.get(i));
                boolean won = Boolean.parseBoolean(s.get("won"));
                String rid = s.get("replay");
                g.item(slots[i], Items.of(won ? Material.LIME_DYE : Material.RED_DYE)
                    .name((won ? "<green>Win" : "<red>Loss") + " <gray>- " + s.getOrDefault("kit", "?") + (Boolean.parseBoolean(s.get("ranked")) ? " <gold>(ranked)" : ""))
                    .lore("<gray>Hits <white>" + s.get("hits") + " <gray>Accuracy <white>" + s.get("acc") + "%", "<gray>Combo <white>" + s.get("combo") + " <gray>Crits <white>" + s.get("crits"),
                        "<gray>Damage <white>" + s.get("dealt") + "<gray>/<white>" + s.get("taken"), "<gray>W-taps <white>" + s.get("wtaps") + " <gray>Blocks <white>" + s.get("blocks"),
                        "<gray>Pots <white>" + s.get("pots") + " <gray>Gaps <white>" + s.get("gaps") + " <gray>Pearls <white>" + s.get("pearls"),
                        "<gray>Length <white>" + s.get("secs") + "s", "", rid == null ? "<dark_gray>No replay" : "<aqua>▶ Click to watch the replay")
                    .build(), c -> {
                        if (rid != null) {
                            p.closeInventory();
                            p.performCommand("replay " + rid);
                        }
                    });
            }
        });
    }

    // ------------------------------------------------------------------ HUD

    /** Every 4 ticks: the action-bar HUD for players who switched it on (/hud). */
    public void hud() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.plugin.sessions().state(p) != PlayerState.MATCH) {
                continue;
            }

            PlayerData d = this.plus.store().ifLoaded(p.getUniqueId());
            if (d == null || !d.hud) {
                continue;
            }

            Match m = this.plugin.matches().ofPlayer(p);
            if (m == null || m.state() != Match.State.FIGHTING) {
                continue;
            }

            MatchStats st = m.stats(p.getUniqueId());
            int oppPots = 0;
            MatchTeam mine = m.teamOf(p.getUniqueId());
            MatchTeam other = m.otherTeam(mine);
            if (other != null) {
                for (UUID u : other.members) {
                    Player o = Bukkit.getPlayer(u);
                    if (o != null) {
                        for (ItemStack it : o.getInventory().getContents()) {
                            if (it != null && (it.getType() == Material.SPLASH_POTION || it.getType() == Material.POTION || it.getType() == Material.GOLDEN_APPLE)) {
                                oppPots += it.getAmount();
                            }
                        }
                    }
                }
            }

            long secs = (long)m.durationSeconds();
            p.sendActionBar(Text.mm("<gray>CPS <white>" + this.cps(p.getUniqueId()) + " <dark_gray>• <gray>Combo <gold>" + (st == null ? 0 : st.combo)
                + " <dark_gray>• <gray>Opp heals <red>" + oppPots + " <dark_gray>• <gray>" + secs / 60 + ":" + String.format("%02d", secs % 60)));
        }
    }

    static boolean healPot(ItemStack it) {
        return it != null && it.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta pm && pm.getBasePotionType() == PotionType.STRONG_HEALING;
    }
}
