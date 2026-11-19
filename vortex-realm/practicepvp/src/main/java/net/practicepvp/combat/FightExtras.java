package net.practicepvp.combat;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.practicepvp.PracticePlugin;
import net.practicepvp.api.FfaKillEvent;
import net.practicepvp.api.MatchEndEvent;
import net.practicepvp.api.MatchStartEvent;
import net.practicepvp.data.Setting;
import net.practicepvp.match.MatchTeam;
import net.practicepvp.session.PlayerState;
import net.practicepvp.session.Session;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * 3.13.0 - small things the big practice servers have: auto GG, kill sounds, a death recap after a lost fight
 * (damage, hits, the other player's best combo, what they had left) and a fight HUD (combo, reach, clicks per second).
 */
public final class FightExtras implements Listener {
    private final PracticePlugin plugin;
    /** Recent clicks per player (ms). */
    private final Map<UUID, ArrayDeque<Long>> clicks = new HashMap<>();
    /** Current combo per attacker. */
    private final Map<UUID, Integer> combo = new HashMap<>();
    /** What each player took from each attacker in the current fight. */
    private final Map<UUID, Map<UUID, Taken>> taken = new HashMap<>();

    static final class Taken {
        double damage;
        int hits;
        int bestCombo;
        long last;
    }

    public FightExtras(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    private String setting(Player p, Setting s) {
        return this.plugin.profiles().get(p).get(s);
    }

    private boolean fighting(Player p) {
        Session s = this.plugin.sessions().get(p);
        return s != null && (s.state == PlayerState.MATCH || s.state == PlayerState.FFA || s.state == PlayerState.BOT);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent e) {
        if (this.fighting(e.getPlayer())) {
            ArrayDeque<Long> q = this.clicks.computeIfAbsent(e.getPlayer().getUniqueId(), u -> new ArrayDeque<>());
            long now = System.currentTimeMillis();
            q.addLast(now);
            while (!q.isEmpty() && now - q.peekFirst() > 1000L) {
                q.pollFirst();
            }
        }
    }

    private int cps(Player p) {
        ArrayDeque<Long> q = this.clicks.get(p.getUniqueId());
        if (q == null) {
            return 0;
        }

        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) {
            q.pollFirst();
        }

        return q.size();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v) || !(e.getDamager() instanceof Player a) || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }

        if (!this.fighting(a)) {
            return;
        }

        int c = this.combo.merge(a.getUniqueId(), 1, Integer::sum);
        this.combo.remove(v.getUniqueId());
        Taken t = this.taken.computeIfAbsent(v.getUniqueId(), u -> new LinkedHashMap<>()).computeIfAbsent(a.getUniqueId(), u -> new Taken());
        t.damage += e.getFinalDamage();
        t.hits++;
        t.bestCombo = Math.max(t.bestCombo, c);
        t.last = System.currentTimeMillis();
        String hud = this.setting(a, Setting.FIGHT_HUD);
        if (!"off".equals(hud)) {
            StringBuilder sb = new StringBuilder("<gold><b>" + c + "</b> <yellow>combo");
            if ("full".equals(hud)) {
                sb.append(" <dark_gray>• <white>").append(String.format(java.util.Locale.ROOT, "%.2f", reach(a, v))).append(" <gray>blocks <dark_gray>• <white>")
                    .append(this.cps(a)).append(" <gray>CPS");
            }

            a.sendActionBar(Text.mm(sb.toString()));
        }
    }

    /** From the eyes to the nearest point of the other player's hitbox. */
    static double reach(Player a, Player v) {
        Vector eye = a.getEyeLocation().toVector();
        BoundingBox b = v.getBoundingBox();
        double x = Math.max(b.getMinX(), Math.min(eye.getX(), b.getMaxX()));
        double y = Math.max(b.getMinY(), Math.min(eye.getY(), b.getMaxY()));
        double z = Math.max(b.getMinZ(), Math.min(eye.getZ(), b.getMaxZ()));
        return eye.distance(new Vector(x, y, z));
    }

    @EventHandler
    public void onStart(MatchStartEvent e) {
        for (Player p : e.match().participantsOnline()) {
            this.reset(p.getUniqueId());
        }
    }

    private void reset(UUID u) {
        this.taken.remove(u);
        this.combo.remove(u);
        this.clicks.remove(u);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.reset(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnd(MatchEndEvent e) {
        if ("CANCELLED".equals(e.reason())) {
            return;
        }

        MatchTeam w = e.winner();
        List<Player> all = e.match().participantsOnline();
        for (Player p : all) {
            boolean won = w != null && e.match().teamOf(p.getUniqueId()) == w;
            if (won) {
                this.killSound(p);
            } else if (!"off".equals(this.setting(p, Setting.DEATH_RECAP))) {
                this.recap(p, 600000L);
            }

            String gg = this.setting(p, Setting.AUTO_GG);
            if (!"off".equals(gg)) {
                String msg = switch (gg) {
                    case "ggwp" -> "gg wp";
                    case "gf" -> "gf";
                    default -> "gg";
                };
                Tasks.later(15L, () -> {
                    if (p.isOnline()) {
                        p.chat(msg);
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFfaKill(FfaKillEvent e) {
        if (e.killer() != null) {
            this.killSound(e.killer());
            this.combo.remove(e.killer().getUniqueId());
        }

        if (e.victim() != null && !"off".equals(this.setting(e.victim(), Setting.DEATH_RECAP))) {
            this.recap(e.victim(), 20000L);
            this.reset(e.victim().getUniqueId());
        }
    }

    private void killSound(Player p) {
        switch (this.setting(p, Setting.KILL_SOUND)) {
            case "levelup" -> Snd.play(p, "entity.player.levelup", 0.7F, 1.2F);
            case "orb" -> Snd.play(p, "entity.experience_orb.pickup", 0.8F, 0.8F);
            case "bell" -> Snd.play(p, "block.bell.use", 0.8F, 1.4F);
            case "anvil" -> Snd.play(p, "block.anvil.land", 0.5F, 1.6F);
            case "thunder" -> Snd.play(p, "entity.lightning_bolt.thunder", 0.4F, 1.4F);
            case "wither" -> Snd.play(p, "entity.wither.spawn", 0.3F, 1.8F);
            case "horn" -> Snd.play(p, "item.goat_horn.sound.0", 0.6F, 1.0F);
            case "totem" -> Snd.play(p, "item.totem.use", 0.4F, 1.4F);
            default -> {
            }
        }
    }

    /** What the player took from each attacker within the last {@code window} ms. */
    private void recap(Player p, long window) {
        Map<UUID, Taken> m = this.taken.get(p.getUniqueId());
        if (m == null || m.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("<dark_gray>━━━━━━━━ <gold><b>DEATH RECAP</b> <dark_gray>━━━━━━━━");
        boolean any = false;
        for (Map.Entry<UUID, Taken> en : m.entrySet()) {
            Taken t = en.getValue();
            if (now - t.last > window) {
                continue;
            }

            any = true;
            Player a = Bukkit.getPlayer(en.getKey());
            String name = a != null ? a.getName() : "?";
            sb.append("\n<red>").append(Text.esc(name)).append(" <gray>hit you <white>").append(t.hits).append("</white> times for <white>")
                .append(String.format(java.util.Locale.ROOT, "%.1f", t.damage / 2.0)).append(" ❤</white>, best combo <white>").append(t.bestCombo).append("</white>");
            if (a != null && a.isOnline()) {
                int pots = 0;
                for (ItemStack it : a.getInventory().getContents()) {
                    if (it != null && it.getType() == Material.SPLASH_POTION && it.getItemMeta() instanceof PotionMeta pm
                        && (pm.getBasePotionType() == PotionType.HEALING || pm.getBasePotionType() == PotionType.STRONG_HEALING)) {
                        pots += it.getAmount();
                    }
                }

                sb.append("\n  <gray>They had <white>").append(String.format(java.util.Locale.ROOT, "%.1f", a.getHealth() / 2.0)).append(" ❤</white> left")
                    .append(pots > 0 ? " and <white>" + pots + "</white> heal pots" : "").append('.');
            }
        }

        if (any) {
            p.sendMessage(Text.mm(sb.toString()));
        }
    }
}
