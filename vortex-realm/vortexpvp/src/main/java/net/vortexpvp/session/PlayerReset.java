package net.vortexpvp.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public final class PlayerReset {
    private static NamespacedKey freezeKey;
    private static String namespace;
    private static final Material[] COOLDOWNS = new Material[]{
        Material.ENDER_PEARL, Material.CHORUS_FRUIT, Material.WIND_CHARGE, Material.SHIELD, Material.GOAT_HORN, Material.TRIDENT, Material.MACE
    };
    private static final Object[][] BASES = new Object[][]{
        {Attribute.MAX_HEALTH, 20.0},
        {Attribute.ATTACK_DAMAGE, 1.0},
        {Attribute.ATTACK_SPEED, 4.0},
        {Attribute.ARMOR, 0.0},
        {Attribute.ARMOR_TOUGHNESS, 0.0},
        {Attribute.KNOCKBACK_RESISTANCE, 0.0},
        {Attribute.SCALE, 1.0},
        {Attribute.GRAVITY, 0.08},
        {Attribute.JUMP_STRENGTH, (double)0.42F},
        {Attribute.SAFE_FALL_DISTANCE, 3.0},
        {Attribute.FALL_DAMAGE_MULTIPLIER, 1.0},
        {Attribute.STEP_HEIGHT, 0.6},
        {Attribute.BLOCK_INTERACTION_RANGE, 4.5},
        {Attribute.ENTITY_INTERACTION_RANGE, 3.0},
        {Attribute.MAX_ABSORPTION, 0.0},
        {Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, 0.0}
    };

    private PlayerReset() {
    }

    public static void init(Plugin plugin) {
        freezeKey = new NamespacedKey(plugin, "freeze_jump");
        namespace = plugin.getName().toLowerCase(Locale.ROOT);
    }

    public static void full(Player p, GameMode mode) {
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }

        p.closeInventory();
        p.getInventory().clear();
        p.setItemOnCursor(null);
        p.clearActivePotionEffects();
        resetAttributes(p);
        stats(p);
        p.setLevel(0);
        p.setExp(0.0F);
        p.setGameMode(mode);
        p.setFlying(false);
        p.setAllowFlight(mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR);
        p.setGlowing(false);
        p.setInvisible(false);
        p.setInvulnerable(false);
        p.setGravity(true);
        p.setWalkSpeed(0.2F);
        p.setFlySpeed(0.1F);
        p.setVelocity(new Vector());

        for (Material m : COOLDOWNS) {
            p.setCooldown(m, 0);
        }

        p.setWorldBorder(null);
        p.updateInventory();
    }

    public static void stats(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double hp = max == null ? 20.0 : max.getValue();
        p.setHealth(Math.max(1.0, hp));
        p.setAbsorptionAmount(0.0);
        p.setFoodLevel(20);
        p.setSaturation(20.0F);
        p.setExhaustion(0.0F);
        p.setFireTicks(0);
        p.setFreezeTicks(0);
        p.setFallDistance(0.0F);
        p.setArrowsInBody(0, false);
        p.setRemainingAir(p.getMaximumAir());
        p.setMaximumNoDamageTicks(Cfg.i(Opt.HIT_DELAY));
        p.setNoDamageTicks(0);
    }

    public static void resetAttributes(Player p) {
        for (Object[] row : BASES) {
            AttributeInstance ai = p.getAttribute((Attribute)row[0]);
            if (ai != null) {
                ai.setBaseValue((Double)row[1]);
                List<AttributeModifier> remove = new ArrayList<>();

                for (AttributeModifier m : ai.getModifiers()) {
                    if (!"minecraft".equals(m.getKey().getNamespace())) {
                        remove.add(m);
                    }
                }

                for (AttributeModifier mx : remove) {
                    ai.removeModifier(mx);
                }
            }
        }

        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            List<AttributeModifier> remove = new ArrayList<>();

            for (AttributeModifier mx : speed.getModifiers()) {
                if (!"minecraft".equals(mx.getKey().getNamespace())) {
                    remove.add(mx);
                }
            }

            for (AttributeModifier mxx : remove) {
                speed.removeModifier(mxx);
            }
        }
    }

    public static void freeze(Player p, Session s) {
        p.setWalkSpeed(0.0F);
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null && jump.getModifier(freezeKey) == null) {
            jump.addModifier(new AttributeModifier(freezeKey, -1.0, Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }

        p.setSprinting(false);
        if (s != null) {
            s.frozen = true;
        }
    }

    public static void noJump(Player p) {
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null && jump.getModifier(freezeKey) == null) {
            jump.addModifier(new AttributeModifier(freezeKey, -1.0, Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }

    public static void unfreeze(Player p, Session s) {
        p.setWalkSpeed(0.2F);
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(freezeKey);
        }

        if (s != null) {
            s.frozen = false;
            if (s.noGravity) {
                p.setGravity(true);
                s.noGravity = false;
            }
        }
    }

    public static String namespace() {
        return namespace;
    }
}
