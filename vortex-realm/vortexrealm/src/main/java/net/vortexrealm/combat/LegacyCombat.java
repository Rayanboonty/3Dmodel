package net.vortexrealm.combat;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.bot.BotManager;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Session;
import net.vortexrealm.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

/**
 * 3.13.0 - 1.8 combat for the legacy kits (and the arcade), on a 1.21 server with a normal 1.21 client:
 * <ul>
 * <li>no attack cooldown (click as fast as in 1.8) and no sweep attacks;</li>
 * <li>1.8 knockback: the push away from the hitter, the extra push of a sprint hit along the hitter's look, and the
 * lift also while in the air (that keeps combos going), with the numbers in Settings so the feel can be tuned;</li>
 * <li>sword blocking: hold right click with a sword for the 1.8 block pose; blocked hits deal half damage;</li>
 * <li>fishing rods knock players back like in 1.8 (rod combos), soup heals instantly, natural regeneration is as slow
 * as in 1.8, and the off-hand (F) is off.</li>
 * </ul>
 * A kit uses it when "1.8 Combat" is on in the kit editor; everything else plays 1.21 combat.
 */
public final class LegacyCombat implements Listener {
    private static final Key SILENT = Key.key("minecraft", "intentionally_empty");
    private final VortexRealm plugin;
    private final NamespacedKey speedKey;
    /** Players who have the 1.8 state applied right now. */
    private final Set<UUID> active = new HashSet<>();
    /** Tick of the last push of a hit, per player: the second push of the same tick is the sprint / knockback push. */
    private final Map<UUID, Integer> pushTick = new HashMap<>();

    public LegacyCombat(VortexRealm plugin) {
        this.plugin = plugin;
        this.speedKey = new NamespacedKey(plugin, "legacy_attack_speed");
    }

    // ------------------------------------------------------------------ who plays 1.8

    /** The kit a player fights with right now (duel, FFA or bot), or null. */
    public Kit kitOf(Player p) {
        Session s = this.plugin.sessions().get(p);
        if (s == null) {
            return null;
        }

        if ((s.state == PlayerState.MATCH || s.state == PlayerState.PREMATCH) && s.match != null) {
            return s.match.kit;
        }

        if (s.state == PlayerState.FFA && s.ffa != null && this.plugin.ffa() != null) {
            return this.plugin.ffa().kitOf(s.ffa);
        }

        if (s.state == PlayerState.BOT && this.plugin.bots() != null) {
            BotManager.Fight f = this.plugin.bots().fightOf(p);
            return f == null ? null : f.kit();
        }

        return null;
    }

    /** True while the player fights with a 1.8 kit, or plays an arcade game with 1.8 combat on. */
    public boolean legacy(Player p) {
        if (!Cfg.bool(Opt.LEGACY_ENABLED)) {
            return false;
        }

        Kit k = this.kitOf(p);
        if (k != null) {
            return k.legacy;
        }

        return Cfg.bool(Opt.LEGACY_ARCADE) && this.plugin.plus() != null && this.plugin.plus().arcade() != null
            && this.plugin.plus().arcade().inGame(p.getUniqueId());
    }

    // ------------------------------------------------------------------ the 1.8 state

    /** Applies or removes the 1.8 state (attack speed, regeneration, sword blocking) to match where the player is. */
    public void refresh(Player p) {
        boolean want = this.legacy(p);
        boolean has = this.active.contains(p.getUniqueId());
        if (want) {
            if (!has) {
                this.active.add(p.getUniqueId());
                AttributeInstance a = p.getAttribute(Attribute.ATTACK_SPEED);
                if (a != null && a.getModifier(this.speedKey) == null) {
                    a.addTransientModifier(new AttributeModifier(this.speedKey, 1000.0, AttributeModifier.Operation.ADD_NUMBER));
                }

                if (Cfg.bool(Opt.LEGACY_SLOW_REGEN)) {
                    p.setSaturatedRegenRate(80);
                }

                this.blockable(p.getInventory(), 41);
            } else {
                // new items (a respawn, an arcade round): the hotbar and off-hand get the block pose
                this.blockable(p.getInventory(), 9);
            }
        } else if (has) {
            this.clear(p);
        }
    }

    private void clear(Player p) {
        this.active.remove(p.getUniqueId());
        this.pushTick.remove(p.getUniqueId());
        AttributeInstance a = p.getAttribute(Attribute.ATTACK_SPEED);
        if (a != null && a.getModifier(this.speedKey) != null) {
            a.removeModifier(this.speedKey);
        }

        p.setSaturatedRegenRate(10);
    }

    /** Swords that can be held up to block (the 1.8 pose), in the first {@code slots} slots. */
    private void blockable(PlayerInventory inv, int slots) {
        if (!Cfg.bool(Opt.LEGACY_BLOCKING)) {
            return;
        }

        for (int i = 0; i < slots && i < 41; i++) {
            ItemStack it = i < 36 ? inv.getItem(i) : (i == 40 ? inv.getItemInOffHand() : null);
            if (!Items.isEmpty(it) && Tag.ITEMS_SWORDS.isTagged(it.getType()) && !it.hasData(DataComponentTypes.CONSUMABLE)) {
                it.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                    .consumeSeconds(86400.0F)
                    .animation(ItemUseAnimation.BLOCK)
                    .sound(SILENT)
                    .hasConsumeParticles(false));
                if (i < 36) {
                    inv.setItem(i, it);
                } else {
                    inv.setItemInOffHand(it);
                }
            }
        }
    }

    /** Every half second: players who started or stopped playing 1.8 get the state put on or taken off. */
    public void sweep() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.active.contains(p.getUniqueId()) || this.legacy(p)) {
                this.refresh(p);
            }
        }
    }

    public void shutdown() {
        for (UUID u : new HashSet<>(this.active)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                this.clear(p);
            }
        }

        this.active.clear();
    }

    public boolean isActive(Player p) {
        return this.active.contains(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (this.active.contains(e.getPlayer().getUniqueId())) {
            this.clear(e.getPlayer());
        }
    }

    // ------------------------------------------------------------------ combat

    /** No sweep attacks in 1.8. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSweep(EntityDamageByEntityEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK && e.getDamager() instanceof Player p && this.isActive(p)) {
            e.setCancelled(true);
        }
    }

    /** A raised sword takes (1 + damage) / 2 of a hit, like 1.8 blocking (the share is a setting). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockedHit(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !this.isActive(p) || !p.isHandRaised()) {
            return;
        }

        ItemStack held = p.getActiveItem();
        if (Items.isEmpty(held) || !Tag.ITEMS_SWORDS.isTagged(held.getType())) {
            return;
        }

        switch (e.getCause()) {
            case ENTITY_ATTACK, PROJECTILE, ENTITY_EXPLOSION, BLOCK_EXPLOSION, THORNS -> {
                double cut = Cfg.i(Opt.LEGACY_BLOCK_REDUCTION) / 100.0;
                double d = e.getDamage();
                if (d > 0.0 && cut > 0.0) {
                    e.setDamage(Math.min(d, (1.0 + d) * (1.0 - cut)));
                }
            }
            default -> {
            }
        }
    }

    /**
     * 1.8 knockback. The first push of a hit sends the player away from the hitter: the old speed is divided by the
     * friction and the lift is added even in the air (capped). A second push in the same tick is the sprint or
     * Knockback enchantment push, along the hitter's look. A kit's knockback percent scales both.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent e) {
        if (!(e.getEntity() instanceof Player v) || !this.isActive(v)) {
            return;
        }

        EntityKnockbackEvent.Cause cause = e.getCause();
        if (cause == EntityKnockbackEvent.Cause.SWEEP_ATTACK || cause == EntityKnockbackEvent.Cause.SHIELD_BLOCK) {
            e.setCancelled(true);
            return;
        }

        if (cause != EntityKnockbackEvent.Cause.ENTITY_ATTACK || !(e instanceof EntityPushedByEntityAttackEvent pe)) {
            return;
        }

        Entity by = pe.getPushedBy();
        if (by == null || by.getWorld() != v.getWorld()) {
            return;
        }

        double strength = -1.0;
        if (e instanceof com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent kb) {
            strength = kb.getKnockbackStrength();
        }

        int tick = Bukkit.getCurrentTick();
        Integer last = this.pushTick.get(v.getUniqueId());
        boolean extra = strength >= 0.0 ? strength > 0.45 : last != null && last == tick;
        double m = this.multiplier(v);
        Vector cur = v.getVelocity();
        if (!extra) {
            this.pushTick.put(v.getUniqueId(), tick);
            double dx = by.getX() - v.getX();
            double dz = by.getZ() - v.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-4) {
                dx = Math.random() - Math.random();
                dz = Math.random() - Math.random();
                len = Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
            }

            double f = Math.max(1.0, Cfg.d(Opt.LEGACY_KB_FRICTION));
            double h = Cfg.d(Opt.LEGACY_KB_HORIZONTAL) * m;
            double up = Cfg.d(Opt.LEGACY_KB_VERTICAL) * Math.min(1.0, m);
            double nx = cur.getX() / f - dx / len * h;
            double ny = Math.min(Cfg.d(Opt.LEGACY_KB_VERTICAL_LIMIT), cur.getY() / f + up);
            double nz = cur.getZ() / f - dz / len * h;
            e.setKnockback(new Vector(nx - cur.getX(), ny - cur.getY(), nz - cur.getZ()));
        } else {
            int level = strength > 0.0 ? Math.max(1, (int)Math.round(strength / 0.5)) : 1;
            double yaw = Math.toRadians(by.getLocation().getYaw());
            double h = Cfg.d(Opt.LEGACY_KB_EXTRA_HORIZONTAL) * level * m;
            e.setKnockback(new Vector(-Math.sin(yaw) * h, Cfg.d(Opt.LEGACY_KB_EXTRA_VERTICAL), Math.cos(yaw) * h));
        }
    }

    /** The kit's knockback percent, or the one a match voted for. */
    private double multiplier(Player p) {
        Session s = this.plugin.sessions().get(p);
        if (s != null && s.state == PlayerState.MATCH && s.match != null) {
            return s.match.knockback();
        }

        Kit k = this.kitOf(p);
        return k == null ? 1.0 : k.knockback / 100.0;
    }

    /** A fishing rod hook pushes the player it hits, like in 1.8: rod, then hit. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRodHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof FishHook) || !(e.getHitEntity() instanceof Player v) || !(e.getEntity().getShooter() instanceof Player shooter)
            || shooter.equals(v) || !Cfg.bool(Opt.LEGACY_ROD_KNOCKBACK) || !this.isActive(v) || !this.isActive(shooter)) {
            return;
        }

        if (v.getNoDamageTicks() > v.getMaximumNoDamageTicks() / 2 || v.isInvulnerable()) {
            return;
        }

        double dx = shooter.getX() - v.getX();
        double dz = shooter.getZ() - v.getZ();
        double len = Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
        Vector cur = v.getVelocity();
        double f = Math.max(1.0, Cfg.d(Opt.LEGACY_KB_FRICTION));
        double h = Cfg.d(Opt.LEGACY_KB_HORIZONTAL);
        v.setVelocity(new Vector(cur.getX() / f - dx / len * h, Math.min(Cfg.d(Opt.LEGACY_KB_VERTICAL_LIMIT), cur.getY() / f + Cfg.d(Opt.LEGACY_KB_VERTICAL)), cur.getZ() / f - dz / len * h));
        v.playHurtAnimation((float)Math.toDegrees(Math.atan2(dz, dx)) - v.getLocation().getYaw());
        v.getWorld().playSound(v.getLocation(), "entity.player.hurt", 0.8F, 1.0F);
        // like 1.8: the rod counts as a hit of 0, so a sword hit right after does its full damage
        v.setNoDamageTicks(v.getMaximumNoDamageTicks());
        v.setLastDamage(0.0);
    }

    /** Soup heals at once (the classic soup kits); the bowl stays for refills. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSoup(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player p = e.getPlayer();
        ItemStack it = e.getItem();
        int heal = Cfg.i(Opt.LEGACY_SOUP_HEAL);
        if (it == null || it.getType() != Material.MUSHROOM_STEW || heal <= 0 || !this.isActive(p)) {
            return;
        }

        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20.0 : max.getValue();
        boolean hungry = p.getFoodLevel() < 20;
        if (p.getHealth() >= cap && !hungry) {
            return;
        }

        e.setCancelled(true);
        if (p.getHealth() < cap) {
            p.setHealth(Math.min(cap, p.getHealth() + heal));
        } else {
            p.setFoodLevel(Math.min(20, p.getFoodLevel() + 6));
            p.setSaturation(Math.min(20.0F, p.getSaturation() + 7.2F));
        }

        p.getInventory().setItemInMainHand(new ItemStack(Material.BOWL));
        p.getWorld().playSound(p.getLocation(), "entity.generic.drink", 0.5F, 1.4F);
    }

    /** No off-hand in 1.8. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (Cfg.bool(Opt.LEGACY_NO_OFFHAND) && this.isActive(e.getPlayer())) {
            e.setCancelled(true);
        }
    }
}
