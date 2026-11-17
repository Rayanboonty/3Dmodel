package net.vortexpvp.listener;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Msg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.Setting;
import net.vortexpvp.ffa.FfaArena;
import net.vortexpvp.fx.Fx;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.match.Match;
import net.vortexpvp.match.MatchStats;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Session;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Cuboid;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class CombatListener implements Listener {
    private final VortexPvP plugin;
    private final Map<UUID, Location> respawns = new HashMap<>();

    public CombatListener(VortexPvP plugin) {
        this.plugin = plugin;
    }

    public Location takeRespawn(Player p) {
        return this.respawns.remove(p.getUniqueId());
    }

    private static Player attacker(EntityDamageEvent e) {
        if (e.getDamageSource().getCausingEntity() instanceof Player p) {
            return p;
        } else {
            if (e instanceof EntityDamageByEntityEvent ed) {
                Entity d = ed.getDamager();
                if (d instanceof Player p) {
                    return p;
                }

                if (d instanceof Projectile proj && proj.getShooter() instanceof Player p) {
                    return p;
                }
            }

            return null;
        }
    }

    private Player killerOf(Player victim, EntityDamageEvent e) {
        Player a = attacker(e);
        if (a != null && !a.equals(victim)) {
            return a;
        } else {
            Session s = this.plugin.sessions().get(victim);
            return s.lastAttacker != null && System.currentTimeMillis() - s.lastAttackTime < 15000L ? Bukkit.getPlayer(s.lastAttacker) : null;
        }
    }

    private static boolean hasTotem(Player p) {
        return saves(p.getInventory().getItemInMainHand()) || saves(p.getInventory().getItemInOffHand());
    }

    private static boolean saves(ItemStack it) {
        return it != null && !it.isEmpty() && (it.getType() == Material.TOTEM_OF_UNDYING || it.hasData(DataComponentTypes.DEATH_PROTECTION));
    }

    private Kit kitOf(Session s) {
        if (s.state == PlayerState.MATCH && s.match != null) {
            return s.match.kit;
        } else {
            return s.state == PlayerState.FFA && s.ffa != null ? this.plugin.ffa().kitOf(s.ffa) : null;
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onFxFirework(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Firework fw && fw.getPersistentDataContainer().has(Fx.FX_FIREWORK, PersistentDataType.BYTE)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            Session var6 = this.plugin.sessions().get(p);
            if (e.isCancelled()) {
                if (!this.fightBetweenPlayers(e, p, var6)) {
                    return;
                }

                e.setCancelled(false);
            }

            if ((var6.state == PlayerState.MATCH || var6.state == PlayerState.FFA)
                && (e.getCause() == DamageCause.ENTITY_EXPLOSION || e.getCause() == DamageCause.BLOCK_EXPLOSION)) {
                double mult = Cfg.d(Opt.EXPLOSION_DAMAGE);
                if (Math.abs(mult - 1.0) > 0.001) {
                    e.setDamage(e.getDamage() * mult);
                }
            }

            if (var6.state == PlayerState.MATCH) {
                this.matchDamage(e, p, var6);
            } else if (var6.state == PlayerState.FFA) {
                this.ffaDamage(e, p, var6);
            } else if (var6.state == PlayerState.PREMATCH
                || var6.state == PlayerState.SPECTATING
                || var6.state == PlayerState.LOBBY
                || var6.state == PlayerState.QUEUE
                || var6.state == PlayerState.EDITING) {
                e.setCancelled(true);
                if (e.getCause() == DamageCause.VOID) {
                    Location back = null;
                    if (var6.state == PlayerState.PREMATCH && var6.match != null && var6.match.preMatch() != null) {
                        back = var6.match.preMatch().seat(p);
                    } else if (var6.state != PlayerState.PREMATCH && var6.state != PlayerState.SPECTATING) {
                        back = this.plugin.lobby().lobby();
                    }

                    if (back != null) {
                        Location to = back;
                        Tasks.sync(() -> Tp.to(p, to));
                    }
                }
            }
        }
    }

    private boolean fightBetweenPlayers(EntityDamageEvent e, Player victim, Session vs) {
        Player a = attacker(e);
        if (a != null && !a.equals(victim)) {
            Session as = this.plugin.sessions().get(a);
            return vs.state == PlayerState.MATCH
                    && as.state == PlayerState.MATCH
                    && vs.match != null
                    && vs.match == as.match
                    && vs.match.state() == Match.State.FIGHTING
                ? vs.match.isAlive(victim.getUniqueId()) && vs.match.isAlive(a.getUniqueId())
                : vs.state == PlayerState.FFA && as.state == PlayerState.FFA && vs.ffa != null && vs.ffa == as.ffa;
        } else {
            return false;
        }
    }

    private void matchDamage(EntityDamageEvent e, Player p, Session s) {
        Match m = s.match;
        if (m != null && m.state() == Match.State.FIGHTING && m.isAlive(p.getUniqueId())) {
            Player attacker = attacker(e);
            if (attacker == null
                || attacker.equals(p)
                || m.isAlive(attacker.getUniqueId()) && (m.teamOf(attacker.getUniqueId()) != m.teamOf(p.getUniqueId()) || m.isOneVsOne())) {
                if (isLethal(e, p)) {
                    e.setCancelled(true);
                    m.handleDeath(p, this.killerOf(p, e), e.getCause());
                }
            } else {
                e.setCancelled(true);
            }
        } else {
            e.setCancelled(true);
            if (e.getCause() == DamageCause.VOID && m != null && m.instance() != null) {
                Location back = m.isAlive(p.getUniqueId()) ? m.spawnOf(p) : m.instance().center();
                Tasks.sync(() -> Tp.to(p, back));
            }
        }
    }

    private void ffaDamage(EntityDamageEvent e, Player p, Session s) {
        FfaArena a = s.ffa;
        long now = System.currentTimeMillis();
        if (a == null) {
            e.setCancelled(true);
        } else if (now < s.spawnProtectionUntil && e.getCause() != DamageCause.VOID) {
            e.setCancelled(true);
        } else {
            Player attacker = attacker(e);
            if (attacker != null && !attacker.equals(p)) {
                Session as = this.plugin.sessions().get(attacker);
                if (as.state != PlayerState.FFA || as.ffa != a) {
                    e.setCancelled(true);
                    return;
                }

                as.spawnProtectionUntil = 0L;
            }

            if (isLethal(e, p)) {
                e.setCancelled(true);
                this.plugin.ffa().handleDeath(p, this.killerOf(p, e), e.getCause());
            }
        }
    }

    private static boolean isLethal(EntityDamageEvent e, Player p) {
        if (e.getCause() == DamageCause.VOID) {
            return true;
        } else {
            return p.getHealth() - e.getFinalDamage() > 0.0 ? false : e.getCause() == DamageCause.KILL || !hasTotem(p);
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onDamageMonitor(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            Session s = this.plugin.sessions().get(p);
            if (s.state.fighting()) {
                DamageCause cause = e.getCause();
                if (cause == DamageCause.BLOCK_EXPLOSION || cause == DamageCause.ENTITY_EXPLOSION) {
                    this.plugin.antiCheat().exempt(p);
                }

                Player attacker = attacker(e);
                long now = System.currentTimeMillis();
                long tag = Cfg.i(Opt.FFA_COMBAT_TAG) * 1000L;
                if (attacker != null && !attacker.equals(p)) {
                    s.lastAttacker = attacker.getUniqueId();
                    s.lastAttackTime = now;
                    s.combatUntil = now + tag;
                    Session as = this.plugin.sessions().get(attacker);
                    as.combatUntil = now + tag;
                    if (s.state == PlayerState.MATCH && s.match != null) {
                        boolean melee = e instanceof EntityDamageByEntityEvent ed && ed.getDamager().equals(attacker);
                        if (melee) {
                            boolean crit = ((EntityDamageByEntityEvent)e).isCritical();
                            s.match.stats(attacker.getUniqueId()).hit(crit, e.getFinalDamage());
                            if (attacker.getInventory().getItemInMainHand().getType() == Material.MACE && attacker.getFallDistance() > 1.5F) {
                                this.plugin.antiCheat().exempt(attacker);
                                this.plugin.antiCheat().exempt(p);
                            }
                        }

                        s.match.stats(p.getUniqueId()).gotHit(e.getFinalDamage());
                    }

                    if ("ding".equals(this.plugin.profiles().get(attacker).get(Setting.HIT_SOUND))) {
                        Snd.play(attacker, "entity.arrow.hit_player", 0.5F, 1.2F);
                    }
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onResurrect(EntityResurrectEvent e) {
        if (e.getEntity() instanceof Player p) {
            Session s = this.plugin.sessions().get(p);
            if (s.state == PlayerState.MATCH && s.match != null) {
                MatchStats st = s.match.stats(p.getUniqueId());
                st.totemsPopped++;

                for (Player o : s.match.participantsOnline()) {
                    if (!o.equals(p)) {
                        o.sendActionBar(Msg.get("match.totem-popped", "player", p.getName(), "count", st.totemsPopped));
                    }
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Session s = this.plugin.sessions().get(p);
        if (s.state != PlayerState.OUTSIDE) {
            e.setKeepInventory(true);
            e.getDrops().clear();
            e.setKeepLevel(true);
            e.setDroppedExp(0);
            e.deathMessage(null);
            Player killer = p.getKiller();
            if (killer == null && s.lastAttacker != null && System.currentTimeMillis() - s.lastAttackTime < 15000L) {
                killer = Bukkit.getPlayer(s.lastAttacker);
            }

            Player k = killer;
            EntityDamageEvent last = p.getLastDamageCause();
            DamageCause cause = last == null ? DamageCause.CUSTOM : last.getCause();
            Location at = p.getLocation();
            PlayerState state = s.state;
            if (state == PlayerState.MATCH && s.match != null && s.match.instance() != null) {
                this.respawns
                    .put(p.getUniqueId(), s.match.instance().contains(at) && at.getY() > s.match.instance().bounds().minY() ? at : s.match.instance().center());
            } else if (state == PlayerState.FFA && s.ffa != null) {
                this.respawns.put(p.getUniqueId(), this.plugin.lobby().lobby());
            } else {
                this.respawns.put(p.getUniqueId(), this.plugin.lobby().lobby());
            }

            Tasks.later(1L, () -> {
                if (p.isOnline()) {
                    if (p.isDead()) {
                        p.spigot().respawn();
                    }

                    Tasks.later(1L, () -> {
                        if (p.isOnline()) {
                            Session now = this.plugin.sessions().get(p);
                            if (now.state == PlayerState.MATCH && now.match != null) {
                                if (now.match.state() == Match.State.FIGHTING && now.match.isAlive(p.getUniqueId())) {
                                    now.match.handleDeath(p, k, cause);
                                }
                            } else if (now.state == PlayerState.FFA) {
                                this.plugin.ffa().handleDeath(p, k, cause);
                            } else if (now.state != PlayerState.OUTSIDE) {
                                this.plugin.lobby().sendToLobby(p);
                            }
                        }
                    });
                }
            });
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onUse(PlayerInteractEvent e) {
        Session s = this.plugin.sessions().get(e.getPlayer());
        if (s.state == PlayerState.MATCH && (s.match == null || s.match.state() != Match.State.FIGHTING)) {
            e.setUseItemInHand(Result.DENY);
            e.setUseInteractedBlock(Result.DENY);
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player p) {
            Session s = this.plugin.sessions().get(p);
            if (s.state != PlayerState.MATCH || s.match != null && s.match.state() == Match.State.FIGHTING) {
                if (s.state.fighting()) {
                    if (e.getEntity() instanceof WindCharge) {
                        this.plugin.antiCheat().exempt(p);
                    }

                    boolean pearl = e.getEntity() instanceof EnderPearl;
                    if (pearl || e.getEntity() instanceof WindCharge) {
                        Kit kit = this.kitOf(s);
                        if (kit != null) {
                            Material m = pearl ? Material.ENDER_PEARL : Material.WIND_CHARGE;
                            int ticks = pearl ? kit.pearlCooldownTicks() : kit.windCooldownTicks();
                            Tasks.later(1L, () -> {
                                if (p.isOnline() && p.getCooldown(m) > ticks) {
                                    p.setCooldown(m, ticks);
                                }
                            });
                        }
                    }

                    if (s.state == PlayerState.MATCH && s.match != null) {
                        if (e.getEntity() instanceof EnderPearl) {
                            s.match.stats(p.getUniqueId()).pearlsThrown++;
                        } else if (e.getEntity() instanceof ThrownPotion) {
                            s.match.stats(p.getUniqueId()).potionsUsed++;
                        }
                    }

                    this.plugin.trails().track(p, e.getEntity());
                }
            } else {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onSplash(PotionSplashEvent e) {
        for (LivingEntity affected : e.getAffectedEntities()) {
            if (affected instanceof Player p && this.plugin.sessions().state(p).fighting()) {
                this.plugin.antiCheat().exempt(p);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        Session s = this.plugin.sessions().get(p);
        if (s.state.fighting()) {
            Material m = e.getItem().getType();
            if (s.state == PlayerState.MATCH && s.match != null) {
                MatchStats st = s.match.stats(p.getUniqueId());
                if (m == Material.GOLDEN_APPLE || m == Material.ENCHANTED_GOLDEN_APPLE) {
                    st.gapplesEaten++;
                } else if (m == Material.POTION) {
                    st.potionsUsed++;
                }
            }

            if (m == Material.POTION && Cfg.bool(Opt.REMOVE_BOTTLES)) {
                Tasks.later(1L, () -> p.getInventory().remove(Material.GLASS_BOTTLE));
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onDrop(PlayerDropItemEvent e) {
        Session s = this.plugin.sessions().get(e.getPlayer());
        if (s.state.fighting()) {
            Material m = e.getItemDrop().getItemStack().getType();
            if (m != Material.GLASS_BOTTLE && m != Material.BOWL) {
                if (!Cfg.bool(Opt.ALLOW_DROPS)) {
                    e.setCancelled(true);
                }
            } else {
                e.getItemDrop().remove();
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p) {
            Kit k = this.kitOf(this.plugin.sessions().get(p));
            if (k != null && !k.hunger && e.getFoodLevel() < p.getFoodLevel()) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onRegen(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p) {
            Kit k = this.kitOf(this.plugin.sessions().get(p));
            if (k != null && !k.regen && (e.getRegainReason() == RegainReason.SATIATED || e.getRegainReason() == RegainReason.REGEN)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onElevatorFly(PlayerToggleFlightEvent e) {
        if (e.isFlying() && this.plugin.sessions().get(e.getPlayer()).noGravity) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onDismount(EntityDismountEvent e) {
        if (e.getEntity() instanceof Player p && this.plugin.sessions().get(p).noGravity) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onFrozenMove(PlayerMoveEvent e) {
        Session s = this.plugin.sessions().get(e.getPlayer());
        if (s.state == PlayerState.MATCH && s.match != null && s.match.state() != Match.State.FIGHTING && !s.noGravity && !s.thrown) {
            if (s.match.state() == Match.State.STARTING || s.match.state() == Match.State.ROUND_END && !s.match.mayMove(e.getPlayer())) {
                Location from = e.getFrom();
                Location to = e.getTo();
                double dx = to.getX() - from.getX();
                double dz = to.getZ() - from.getZ();
                if (dx * dx + dz * dz > 1.0E-4 || to.getY() > from.getY() + 0.01) {
                    Location back = from.clone();
                    back.setYaw(to.getYaw());
                    back.setPitch(to.getPitch());
                    e.setTo(back);
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            Player p = e.getPlayer();
            Session s = this.plugin.sessions().get(p);
            switch (s.state) {
                case MATCH:
                    Match m = s.match;
                    if (m == null || m.instance() == null) {
                        return;
                    }

                    Cuboid b = m.instance().bounds();
                    if (to.getY() < b.minY() - 2) {
                        if (m.state() == Match.State.FIGHTING && m.isAlive(p.getUniqueId())) {
                            m.handleDeath(p, this.killerOfVoid(p), DamageCause.VOID);
                        } else {
                            Location back = m.isAlive(p.getUniqueId()) ? m.spawnOf(p) : m.instance().center();
                            Tasks.sync(() -> Tp.to(p, back));
                        }

                        return;
                    }

                    if (!b.containsXZ(to.getX(), to.getZ()) && !s.noGravity) {
                        e.setTo(from.clone().setDirection(to.getDirection()));
                    }
                    break;
                case FFA:
                    FfaArena a = s.ffa;
                    if (a == null || a.bounds == null) {
                        return;
                    }

                    if (to.getY() < a.bounds.minY() - 2) {
                        this.plugin.ffa().handleDeath(p, this.killerOfVoid(p), DamageCause.VOID);
                        return;
                    }

                    if (!a.bounds.containsXZ(to.getX(), to.getZ())) {
                        e.setTo(from.clone().setDirection(to.getDirection()));
                    }
                    break;
                case SPECTATING:
                    Match mx = s.spectating;
                    if (mx == null || mx.instance() == null) {
                        return;
                    }

                    Cuboid bx = mx.instance().bounds();
                    if (!bx.containsXZ(to.getX(), to.getZ()) || to.getY() < bx.minY() - 10 || to.getY() > bx.maxY() + 30) {
                        Location c = mx.instance().center();
                        Tasks.sync(() -> Tp.to(p, c));
                    }
            }
        }
    }

    private Player killerOfVoid(Player victim) {
        Session s = this.plugin.sessions().get(victim);
        return s.lastAttacker != null && System.currentTimeMillis() - s.lastAttackTime < 15000L ? Bukkit.getPlayer(s.lastAttacker) : null;
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onProtectedHit(EntityDamageByEntityEvent e) {
        if (e.isCancelled() && e.getEntity() instanceof Player victim) {
            Player attacker = attacker(e);
            if (attacker != null) {
                Session vs = this.plugin.sessions().get(victim);
                if (vs.state == PlayerState.FFA && System.currentTimeMillis() < vs.spawnProtectionUntil) {
                    attacker.sendActionBar(Text.mm("<red>" + Text.esc(victim.getName()) + " has spawn protection"));
                }
            }
        }
    }
}
