package net.vortexpvp.plus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.api.FfaKillEvent;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Session;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * FFA bounties and kill-streak rewards: a 5+ kill streak puts a growing bounty (in shards) on your head, makes you
 * glow and is announced; whoever ends the streak collects it. Streaks also pay rewards on the way: a golden apple at
 * 3, Speed at 5, Strength at 7, Regeneration and Absorption at 10 (every 5 after that).
 */
public final class Bounties implements Listener {
    private final VortexPvP plugin;
    private final PlusModule plus;
    private final Map<UUID, Integer> bounty = new HashMap<>();

    public Bounties(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
    }

    public int bountyOf(UUID u) {
        return this.bounty.getOrDefault(u, 0);
    }

    public Map<UUID, Integer> all() {
        return this.bounty;
    }

    @EventHandler
    public void onKill(FfaKillEvent e) {
        Player v = e.victim();
        Player k = e.killer();
        Integer owed = this.bounty.remove(v.getUniqueId());
        v.setGlowing(false);
        if (k != null) {
            UUID ku = k.getUniqueId();
            this.plus.progression().track(ku, "ffa_kill", e.arena() == null ? null : this.kitOf(e), 1L);
            this.plus.progression().best(ku, "best_ffa_streak", e.killerStreak());
            this.plus.progression().xp(ku, 8L + Math.min(12, e.killerStreak()), "FFA kill");
            if (owed != null && owed > 0 && this.plus.config().on("bounties")) {
                this.plus.progression().shards(ku, owed);
                this.plus.progression().track(ku, "bounty", null, 1L);
                Bukkit.broadcast(Text.mm("<gold>💰 <white>" + Text.esc(k.getName()) + " <gray>claimed the <gold>" + owed + " shard</gold> bounty on <white>"
                    + Text.esc(v.getName()) + " <gray>and ended a <white>" + e.victimStreak() + "</white>-kill streak!"));
                Snd.play(k, "entity.player.levelup", 1.0F, 0.8F);
            }

            this.streakRewards(k, e.killerStreak());
            int min = this.plus.config().i("bounties.min-streak", 5);
            if (this.plus.config().on("bounties") && e.killerStreak() >= min) {
                int value = this.plus.config().i("bounties.base", 50) + (e.killerStreak() - min) * this.plus.config().i("bounties.per-kill", 15);
                this.bounty.put(ku, value);
                k.setGlowing(true);
                if (e.killerStreak() == min || e.killerStreak() % 5 == 0) {
                    Bukkit.broadcast(Text.mm("<red>🎯 <white>" + Text.esc(k.getName()) + " <gray>is on a <white>" + e.killerStreak()
                        + "</white>-kill FFA streak - bounty: <gold>" + value + " shards</gold>! <click:run_command:'/ffa'><yellow>[hunt]</yellow></click>"));
                }
            }
        }

        this.plus.flair().ffaDeathMessage(e);
    }

    private String kitOf(FfaKillEvent e) {
        net.vortexpvp.kit.Kit kit = this.plugin.ffa().kitOf(e.arena());
        return kit == null ? null : kit.id;
    }

    private void streakRewards(Player k, int streak) {
        if (!this.plus.config().on("ffa-streak-rewards")) {
            return;
        }

        switch (streak) {
            case 3 -> {
                k.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
                k.sendActionBar(Text.mm("<gold>3 kills: <white>+1 golden apple"));
            }
            case 5 -> {
                k.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
                k.sendActionBar(Text.mm("<aqua>5 kills: <white>Speed II for 10s"));
            }
            case 7 -> {
                k.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, 0));
                k.sendActionBar(Text.mm("<red>7 kills: <white>Strength for 6s"));
            }
            default -> {
                if (streak >= 10 && streak % 5 == 0) {
                    k.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                    k.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 1));
                    k.sendActionBar(Text.mm("<light_purple>" + streak + " kills: <white>Regeneration + Absorption"));
                }
            }
        }
    }

    /** Every 2 seconds: bounties of players who left the FFA are dropped. */
    public void check() {
        this.bounty.keySet().removeIf(u -> {
            Player p = Bukkit.getPlayer(u);
            if (p == null) {
                return true;
            }

            Session s = this.plugin.sessions().get(p);
            if (s.state != PlayerState.FFA) {
                p.setGlowing(false);
                return true;
            }

            return false;
        });
    }

    public void list(org.bukkit.command.CommandSender to) {
        if (this.bounty.isEmpty()) {
            to.sendMessage(Text.mm("<gray>No bounties right now. Get a 5-kill FFA streak to have one on your head!"));
            return;
        }

        to.sendMessage(Text.mm("<gold><b>Bounties"));
        this.bounty.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(10).forEach(en -> {
            String n = Bukkit.getOfflinePlayer(en.getKey()).getName();
            to.sendMessage(Text.mm("<white>" + Text.esc(n == null ? "?" : n) + " <dark_gray>- <gold>" + en.getValue() + " shards"));
        });
    }

    public void clearGlow() {
        for (UUID u : this.bounty.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.setGlowing(false);
            }
        }

        this.bounty.clear();
    }

    static List<Integer> rewardStreaks() {
        return List.of(3, 5, 7, 10);
    }
}
