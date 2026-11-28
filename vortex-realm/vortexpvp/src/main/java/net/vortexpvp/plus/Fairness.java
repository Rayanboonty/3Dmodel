package net.vortexpvp.plus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.match.Match;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Extra fairness checks next to the built-in anti-cheat: reach (with ping compensation), autoclicker (click timing
 * too regular or too fast), velocity (no knockback taken) and crystal aura (crystals broken the moment they appear).
 * Each flag has a confidence score and the replay id of the match, and goes to staff with /alerts on. Reports get
 * the replay of the reported player's match attached as evidence, and screenshares are written to a log.
 */
public final class Fairness implements Listener {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final VortexPvP plugin;
    private final PlusModule plus;
    private final Map<String, Double> vl = new HashMap<>();
    private final Map<UUID, Long> lastAlert = new HashMap<>();
    private final Map<UUID, Deque<Long>> crystalBreaks = new HashMap<>();
    private final Map<UUID, Long> lastVelocityCheck = new HashMap<>();

    public Fairness(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
    }

    private boolean fighting(Player p) {
        PlayerState s = this.plugin.sessions().state(p);
        return s == PlayerState.MATCH || s == PlayerState.FFA;
    }

    // ------------------------------------------------------------------ checks

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!this.plus.config().on("fairness")) {
            return;
        }

        if (e.getDamager() instanceof Player a && e.getEntity() instanceof EnderCrystal c) {
            if (c.getTicksLived() <= 2 && this.fighting(a)) {
                Deque<Long> d = this.crystalBreaks.computeIfAbsent(a.getUniqueId(), k -> new ArrayDeque<>());
                d.add(System.currentTimeMillis());
                while (d.size() > 10) {
                    d.poll();
                }

                long recent = d.stream().filter(t -> System.currentTimeMillis() - t < 10000L).count();
                if (recent >= 4) {
                    this.flag(a, "Crystal aura", 3.0, recent + " crystals broken within 2 ticks of spawning (10s)");
                }
            }

            return;
        }

        if (!(e.getDamager() instanceof Player a) || !(e.getEntity() instanceof Player v) || e.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }

        if (!this.fighting(a) || a.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }

        // reach: eye to the nearest point of the victim's box, minus what both pings can explain
        double dist = reach(a.getEyeLocation(), v.getBoundingBox());
        double limit = this.plus.config().d("fairness.reach", 3.05) + Math.min(0.6, (a.getPing() + v.getPing()) * 0.0015) + (v.getVelocity().length() > 0.3 ? 0.25 : 0.0);
        if (dist > limit) {
            this.flag(a, "Reach", (dist - limit) * 12.0, String.format(Locale.ROOT, "%.2f blocks (limit %.2f, ping %d/%d)", dist, limit, a.getPing(), v.getPing()));
        } else {
            this.decay(a, "Reach");
        }

        // velocity: the victim should move with the knockback within a few ticks (checked at most once a second)
        long now0 = System.currentTimeMillis();
        if (now0 - this.lastVelocityCheck.getOrDefault(v.getUniqueId(), 0L) < 1000L) {
            return;
        }

        this.lastVelocityCheck.put(v.getUniqueId(), now0);
        Location before = v.getLocation();
        Vector kbDir = v.getLocation().toVector().subtract(a.getLocation().toVector()).setY(0);
        Tasks.later(5L, () -> {
            if (!v.isOnline() || !this.fighting(v) || v.isDead() || v.isInsideVehicle() || v.isBlocking()) {
                return;
            }

            Location now = v.getLocation();
            double moved = now.toVector().subtract(before.toVector()).setY(0).length();
            boolean wall = kbDir.lengthSquared() > 0.01 && now.clone().add(kbDir.normalize().multiply(0.6)).getBlock().getType().isSolid();
            Material in = now.getBlock().getType();
            if (moved < 0.03 && !wall && in != Material.COBWEB && in != Material.WATER && in != Material.LAVA && in != Material.LADDER) {
                this.flag(v, "Velocity", 1.5, String.format(Locale.ROOT, "moved %.3f blocks after a hit", moved));
            }
        });
    }

    static double reach(Location eye, BoundingBox box) {
        double x = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double y = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double z = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        return Math.sqrt((x - eye.getX()) * (x - eye.getX()) + (y - eye.getY()) * (y - eye.getY()) + (z - eye.getZ()) * (z - eye.getZ()));
    }

    /** Every second: click timing of fighting players. */
    public void clicks() {
        if (!this.plus.config().on("fairness")) {
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!this.fighting(p)) {
                continue;
            }

            List<Long> c = this.plus.analysis().recentClicks(p.getUniqueId());
            int cps = this.plus.analysis().cps(p.getUniqueId());
            if (cps >= this.plus.config().i("fairness.max-cps", 22)) {
                this.flag(p, "Autoclicker", 2.0, cps + " CPS");
                continue;
            }

            if (cps >= 12 && c.size() >= 20) {
                double sd = intervalDeviation(c.subList(c.size() - 20, c.size()));
                if (sd < this.plus.config().d("fairness.min-click-deviation", 8.0)) {
                    this.flag(p, "Autoclicker", 1.5, String.format(Locale.ROOT, "%d CPS, click timing deviation only %.1f ms", cps, sd));
                }
            }
        }
    }

    static double intervalDeviation(List<Long> times) {
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < times.size(); i++) {
            gaps.add(times.get(i) - times.get(i - 1));
        }

        double mean = gaps.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double var = gaps.stream().mapToDouble(g -> (g - mean) * (g - mean)).average().orElse(0.0);
        return Math.sqrt(var);
    }

    private void decay(Player p, String check) {
        this.vl.computeIfPresent(p.getUniqueId() + ":" + check, (k, v) -> Math.max(0.0, v - 0.25));
    }

    private void flag(Player p, String check, double weight, String info) {
        String key = p.getUniqueId() + ":" + check;
        double v = this.vl.merge(key, weight, Double::sum);
        int confidence = (int)Math.min(99.0, v * 8.0);
        if (v < this.plus.config().d("fairness.alert-at", 4.0)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastAlert.getOrDefault(p.getUniqueId(), 0L) < 3000L) {
            return;
        }

        this.lastAlert.put(p.getUniqueId(), now);
        Match m = this.plugin.matches().ofPlayer(p);
        String replay = m == null || this.plus.replays() == null ? null : this.plus.replays().idFor(m.id);
        String msg = "<dark_red>[Fair+]</dark_red> <white>" + Text.esc(p.getName()) + " <red>" + check + " <gray>(" + Text.esc(info) + ") <yellow>" + confidence + "%"
            + (replay == null ? "" : " <click:run_command:'/replay " + replay + "'><aqua>[replay #" + replay + "]</aqua></click>")
            + " <click:run_command:'/spectate " + p.getName() + "'><green>[watch]</green></click>";
        for (Player s : Bukkit.getOnlinePlayers()) {
            if (s.hasPermission("vortexpvp.alerts") && !this.plus.store().get(s).counters.containsKey("alerts_off")) {
                s.sendMessage(Text.mm(msg));
            }
        }

        YamlConfiguration y = this.plus.store().doc("fairness");
        List<String> flags = new ArrayList<>(y.getStringList("flags"));
        flags.add(0, now + "|" + p.getUniqueId() + "|" + p.getName() + "|" + check + "|" + confidence + "|" + info.replace("|", "/") + "|" + (replay == null ? "" : replay));
        while (flags.size() > 300) {
            flags.remove(flags.size() - 1);
        }

        y.set("flags", flags);
        this.plus.store().dirty("fairness");
        if (replay != null) {
            this.plus.replays().keep(replay, true);
        }

        this.vl.put(key, v * 0.5);
    }

    public void toggleAlerts(Player p) {
        PlayerData d = this.plus.store().get(p);
        if (d.counters.remove("alerts_off") == null) {
            d.counters.put("alerts_off", 1L);
            p.sendMessage(Text.mm("<gray>Fair+ alerts off."));
        } else {
            p.sendMessage(Text.mm("<green>Fair+ alerts on."));
        }

        d.dirty = true;
    }

    // ------------------------------------------------------------------ report evidence

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String[] a = e.getMessage().substring(1).split("\\s+");
        if (a.length == 0) {
            return;
        }

        String cmd = a[0].toLowerCase(Locale.ROOT);
        if (cmd.contains(":")) {
            cmd = cmd.substring(cmd.indexOf(':') + 1);
        }

        Player p = e.getPlayer();
        if (cmd.equals("report") && a.length >= 2) {
            Player t = Bukkit.getPlayerExact(a[1]);
            UUID tu = t == null ? this.plus.store().uuidOf(a[1]) : t.getUniqueId();
            if (tu == null) {
                return;
            }

            String replay = null;
            if (t != null) {
                Match m = this.plugin.matches().ofPlayer(t);
                if (m != null && this.plus.replays() != null) {
                    replay = this.plus.replays().idFor(m.id);
                }
            }

            if (replay == null && this.plus.replays() != null) {
                List<String> ids = this.plus.replays().idsOf(tu);
                if (!ids.isEmpty() && System.currentTimeMillis() - this.plus.replays().info(ids.get(0)).getLong("at") < 600000L) {
                    replay = ids.get(0);
                }
            }

            YamlConfiguration y = this.plus.store().doc("fairness");
            List<String> ev = new ArrayList<>(y.getStringList("evidence"));
            String reason = a.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)) : "";
            ev.add(0, System.currentTimeMillis() + "|" + p.getName() + "|" + a[1] + "|" + tu + "|" + (replay == null ? "" : replay) + "|" + reason.replace("|", "/"));
            while (ev.size() > 200) {
                ev.remove(ev.size() - 1);
            }

            y.set("evidence", ev);
            this.plus.store().dirty("fairness");
            if (replay != null) {
                this.plus.replays().keep(replay, true);
                String r = replay;
                Tasks.later(2L, () -> p.sendMessage(Text.mm("<gray>📎 Replay <aqua>#" + r + "</aqua> was attached to your report as evidence.")));
            }
        } else if ((cmd.equals("ss") || cmd.equals("screenshare") || cmd.equals("freeze")) && p.hasPermission("vortexpvp.staff") && a.length >= 2) {
            String action = a[1].toLowerCase(Locale.ROOT);
            if (action.equals("list") || action.equals("admit")) {
                return;
            }

            String target = action.equals("end") && a.length >= 3 ? a[2] : a[1];
            String result = action.equals("end") && a.length >= 4 ? a[3] : (action.equals("end") ? "ended" : "started");
            this.log(p.getName(), target, result, "");
        }
    }

    public void log(String staff, String target, String result, String note) {
        YamlConfiguration y = this.plus.store().doc("fairness");
        List<String> log = new ArrayList<>(y.getStringList("sslog"));
        log.add(0, System.currentTimeMillis() + "|" + staff + "|" + target + "|" + result + "|" + note.replace("|", "/"));
        while (log.size() > 500) {
            log.remove(log.size() - 1);
        }

        y.set("sslog", log);
        this.plus.store().dirty("fairness");
    }

    public void sslog(CommandSender s, String[] a) {
        if (a.length >= 3 && a[0].equalsIgnoreCase("note")) {
            this.log(s.getName(), a[1], "note", String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)));
            s.sendMessage(Text.mm("<green>Note added to the screenshare log."));
            return;
        }

        String filter = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : null;
        s.sendMessage(Text.mm("<gold><b>Screenshare log" + (filter == null ? "" : " - " + Text.esc(filter))));
        int n = 0;
        for (String line : this.plus.store().doc("fairness").getStringList("sslog")) {
            String[] p = line.split("\\|", 5);
            if (p.length < 5 || filter != null && !p[2].toLowerCase(Locale.ROOT).equals(filter)) {
                continue;
            }

            s.sendMessage(Text.mm("<dark_gray>" + WHEN.format(Instant.ofEpochMilli(Long.parseLong(p[0]))) + " <white>" + Text.esc(p[1]) + " <gray>→ <white>" + Text.esc(p[2])
                + " <yellow>" + Text.esc(p[3]) + (p[4].isEmpty() ? "" : " <gray>" + Text.esc(p[4]))));
            if (++n >= 15) {
                break;
            }
        }

        if (n == 0) {
            s.sendMessage(Text.mm("<gray>Nothing logged."));
        }
    }

    public void evidence(Player staff, String target) {
        YamlConfiguration y = this.plus.store().doc("fairness");
        Gui.open(this.plugin, staff, "<dark_gray>Evidence" + (target == null ? "" : " - " + Text.esc(target)), 6, g -> {
            int[] slots = g.inner();
            int i = 0;
            for (String line : y.getStringList("evidence")) {
                String[] p = line.split("\\|", 6);
                if (p.length < 6 || target != null && !p[2].equalsIgnoreCase(target)) {
                    continue;
                }

                if (i >= slots.length) {
                    break;
                }

                String replay = p[4];
                g.item(slots[i++], net.vortexpvp.util.Items.of(replay.isEmpty() ? Material.PAPER : Material.ENDER_EYE)
                    .name("<red>" + Text.esc(p[2]) + " <gray>reported by <white>" + Text.esc(p[1]))
                    .lore("<gray>" + WHEN.format(Instant.ofEpochMilli(Long.parseLong(p[0]))), "<gray>Reason: <white>" + Text.esc(p[5].isEmpty() ? "-" : p[5]),
                        replay.isEmpty() ? "<dark_gray>No replay" : "<aqua>▶ Click to watch replay #" + replay)
                    .build(), c -> {
                        if (!replay.isEmpty()) {
                            staff.closeInventory();
                            this.plus.replays().watch(staff, replay);
                        }
                    });
            }

            for (String line : y.getStringList("flags")) {
                String[] p = line.split("\\|", 7);
                if (p.length < 7 || target != null && !p[2].equalsIgnoreCase(target)) {
                    continue;
                }

                if (i >= slots.length) {
                    break;
                }

                String replay = p[6];
                g.item(slots[i++], net.vortexpvp.util.Items.of(Material.REDSTONE).name("<dark_red>" + Text.esc(p[3]) + " <gray>- <white>" + Text.esc(p[2]))
                    .lore("<gray>" + WHEN.format(Instant.ofEpochMilli(Long.parseLong(p[0]))), "<gray>" + Text.esc(p[5]), "<yellow>Confidence " + p[4] + "%",
                        replay.isEmpty() ? "<dark_gray>No replay" : "<aqua>▶ Click to watch replay #" + replay)
                    .build(), c -> {
                        if (!replay.isEmpty()) {
                            staff.closeInventory();
                            this.plus.replays().watch(staff, replay);
                        }
                    });
            }

            if (i == 0) {
                g.item(22, net.vortexpvp.util.Items.of(Material.BARRIER).name("<gray>No reports or flags").build());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String prefix = e.getPlayer().getUniqueId() + ":";
        this.vl.keySet().removeIf(k -> k.startsWith(prefix));
        this.crystalBreaks.remove(e.getPlayer().getUniqueId());
        this.lastAlert.remove(e.getPlayer().getUniqueId());
        this.lastVelocityCheck.remove(e.getPlayer().getUniqueId());
    }

    public String selfCheck() {
        BoundingBox box = new BoundingBox(3.7, 0, -0.3, 4.3, 1.8, 0.3);
        double r = reach(new Location(null, 0, 1.62, 0), box);
        if (Math.abs(r - 3.7) > 0.01) {
            return "reach math " + r;
        }

        List<Long> even = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            even.add(i * 70L);
        }

        return intervalDeviation(even) < 0.01 ? null : "deviation math";
    }
}
