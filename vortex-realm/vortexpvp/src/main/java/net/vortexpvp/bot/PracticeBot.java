package net.vortexpvp.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * A practice bot: a player-shaped Mannequin driven every tick by the plugin. It chases, strafes, jumps for crits,
 * w-taps, blocks with its shield and uses the techniques of its kit: axe shield-breaks (stun), mace smashes from a
 * wind-charge launch, the stun slam (axe then mace in one fall), elytra mace dives, crystal PvP (obsidian + crystal,
 * re-totem, anchors at Expert), splash heals, golden apples and the bow. As a training dummy it only does the one
 * thing its type is for and shows live stats over its head.
 */
public final class PracticeBot {
    final VortexPvP plugin;
    final Mannequin body;
    final Kit kit;
    final Difficulty diff;
    final DummyType dummy;
    Player target;
    // what the kit gives it
    final boolean sword;
    final boolean axe;
    final boolean mace;
    final boolean shield;
    final boolean crystals;
    final boolean anchors;
    final boolean bow;
    final boolean elytra;
    int totems;
    int gappleCount;
    int potCount;
    final ItemStack swordItem;
    final ItemStack axeItem;
    final ItemStack maceItem;
    final ItemStack bowItem;
    // state
    int tick;
    int attackReady;
    int strafeDir = 1;
    int strafeUntil;
    int wtap;
    int shieldUntil;
    int shieldBroken;
    int eating;
    int crystalReady = 40;
    int smashReady = 60;
    int elytraReady = 200;
    int bowReady;
    int healReady;
    /** 0 = normal, 1 = rising for a smash, 2 = falling onto the target, 3 = elytra climb, 4 = glide, 5 = dive. */
    int phase;
    int phaseTick;
    final List<Location> placed = new ArrayList<>();
    final List<Entity> spawned = new ArrayList<>();
    // stats
    int hitsGiven;
    int hitsTaken;
    double damageGiven;
    double damageTaken;
    int combo;
    int bestCombo;
    long lastHitTaken;
    int shieldBreaks;
    double bestSmash;
    int pops;
    final long started = System.currentTimeMillis();
    TextDisplay label;
    Location home;
    /** Own movement: a Mannequin doesn't move by itself, so the bot is stepped every tick (gravity, walls, steps). */
    Vector vel = new Vector();
    double lastDist = 99.0;
    int stuck;
    boolean onGround;
    double peakY;
    boolean floating;

    PracticeBot(VortexPvP plugin, Location at, Kit kit, Difficulty diff, DummyType dummy, String name) {
        this.plugin = plugin;
        this.kit = kit;
        this.diff = diff;
        this.dummy = dummy;
        this.home = at.clone();
        ItemStack[] c = kit.contents;
        ItemStack sw = null;
        ItemStack ax = null;
        ItemStack mc = null;
        ItemStack bw = null;
        boolean sh = false;
        boolean cr = false;
        boolean an = false;
        boolean el = false;
        int tt = 0;
        int gp = 0;
        int pt = 0;

        for (ItemStack it : c) {
            if (!Items.isEmpty(it)) {
                String n = it.getType().name();
                if (n.endsWith("_SWORD") && sw == null) {
                    sw = it.clone();
                } else if (n.endsWith("_AXE") && ax == null) {
                    ax = it.clone();
                } else if (n.equals("MACE") && mc == null) {
                    mc = it.clone();
                } else if ((n.equals("BOW") || n.equals("CROSSBOW")) && bw == null) {
                    bw = it.clone();
                } else if (n.equals("SHIELD")) {
                    sh = true;
                } else if (n.equals("END_CRYSTAL")) {
                    cr = true;
                } else if (n.equals("RESPAWN_ANCHOR")) {
                    an = true;
                } else if (n.equals("ELYTRA")) {
                    el = true;
                } else if (n.equals("TOTEM_OF_UNDYING")) {
                    tt += it.getAmount();
                } else if (n.contains("GOLDEN_APPLE")) {
                    gp += it.getAmount();
                } else if (n.equals("SPLASH_POTION")) {
                    pt += it.getAmount();
                }
            }
        }

        if (dummy == DummyType.SHIELD) {
            sh = true;
        } else if (dummy == DummyType.CRYSTAL) {
            tt = 999;
        }

        this.swordItem = sw;
        this.axeItem = ax;
        this.maceItem = mc;
        this.bowItem = bw;
        this.sword = sw != null;
        this.axe = ax != null;
        this.mace = mc != null;
        this.shield = sh;
        this.crystals = cr;
        this.anchors = an;
        this.bow = bw != null;
        this.elytra = el || this.mace && diff.skill >= 4;
        this.totems = tt;
        this.gappleCount = gp;
        this.potCount = pt;
        String nm = name;
        this.body = at.getWorld().spawn(at, Mannequin.class, m -> {
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            m.setImmovable(false);
            m.customName(Text.mm(nm));
            m.setCustomNameVisible(true);
            m.setDescription(Text.mm(dummy == null ? diff.label + " bot" : "<gray>Training"));
            m.setCollidable(true);
            EntityEquipment eq = m.getEquipment();
            eq.setHelmet(c[39] == null ? null : c[39].clone());
            eq.setChestplate(c[38] == null ? null : c[38].clone());
            eq.setLeggings(c[37] == null ? null : c[37].clone());
            eq.setBoots(c[36] == null ? null : c[36].clone());
            eq.setItemInMainHand(this.mainWeapon());
            eq.setItemInOffHand(this.shield ? new ItemStack(Material.SHIELD) : (this.totems > 0 ? new ItemStack(Material.TOTEM_OF_UNDYING) : null));
            if (m.getAttribute(Attribute.MAX_HEALTH) != null) {
                m.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
            }

            m.setHealth(20.0);
            if (dummy == DummyType.CRYSTAL) {
                eq.setHelmet(Items.of(Material.NETHERITE_HELMET).build());
                eq.setChestplate(Items.of(Material.NETHERITE_CHESTPLATE).build());
                eq.setLeggings(blast(Material.NETHERITE_LEGGINGS));
                eq.setBoots(Items.of(Material.NETHERITE_BOOTS).build());
                eq.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            }
        });
        this.floating = dummy == DummyType.ELYTRA;
        this.peakY = at.getY();
        if (dummy != null) {
            this.label = at.getWorld().spawn(at.clone().add(0.0, 2.6, 0.0), TextDisplay.class, t -> {
                t.setBillboard(Billboard.CENTER);
                t.setPersistent(false);
                t.setBackgroundColor(org.bukkit.Color.fromARGB(150, 10, 10, 18));
                t.text(Text.mm("<gold><b>" + dummy.display));
            });
        }
    }

    private static ItemStack blast(Material m) {
        ItemStack s = new ItemStack(m);
        s.editMeta(meta -> meta.addEnchant(Enchantment.BLAST_PROTECTION, 4, true));
        return s;
    }

    ItemStack mainWeapon() {
        if (this.dummy == DummyType.SHIELD) {
            return null;
        } else if (this.swordItem != null) {
            return this.swordItem.clone();
        } else if (this.axeItem != null) {
            return this.axeItem.clone();
        } else if (this.maceItem != null) {
            return this.maceItem.clone();
        } else {
            return this.crystals ? new ItemStack(Material.END_CRYSTAL) : null;
        }
    }

    boolean valid() {
        return this.body.isValid() && !this.body.isDead();
    }

    void remove() {
        this.body.remove();
        if (this.label != null) {
            this.label.remove();
        }

        for (Entity e : this.spawned) {
            e.remove();
        }

        this.spawned.clear();
    }

    void setVel(Vector v) {
        this.vel = v.clone();
    }

    /** Fall distance since the highest point of the current jump or launch. */
    float fall() {
        return this.onGround ? 0.0F : (float)Math.max(0.0, this.peakY - this.body.getY());
    }

    private static boolean solid(World w, double x, double y, double z) {
        Block b = w.getBlockAt((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
        return b.getType().isSolid() && !b.isPassable();
    }

    /** One step of movement: gravity, walls (one-block steps are climbed), floor and ceiling. */
    void physics() {
        Location l = this.body.getLocation();
        World w = l.getWorld();
        boolean gliding = this.phase == 4 || this.phase == 5;
        if (!this.floating && !gliding && this.phase != 3) {
            this.vel.setY((this.vel.getY() - 0.08) * 0.98);
        }

        double nx = l.getX() + this.vel.getX();
        double nz = l.getZ() + this.vel.getZ();
        double ny = l.getY() + this.vel.getY();
        if (solid(w, nx, l.getY() + 0.1, nz) || solid(w, nx, l.getY() + 1.2, nz)) {
            if (this.onGround && !solid(w, nx, l.getY() + 1.1, nz) && !solid(w, nx, l.getY() + 2.1, nz)) {
                ny = Math.max(ny, Math.floor(l.getY()) + 1.0);
            } else {
                nx = l.getX();
                nz = l.getZ();
                this.vel.setX(0.0);
                this.vel.setZ(0.0);
            }
        }

        boolean was = this.onGround;
        this.onGround = false;
        if (this.vel.getY() <= 0.0 && solid(w, nx, ny - 0.001, nz)) {
            ny = Math.floor(ny - 0.001) + 1.0;
            this.vel.setY(0.0);
            this.onGround = true;
        } else if (this.vel.getY() > 0.0 && solid(w, nx, ny + 1.85, nz)) {
            this.vel.setY(0.0);
            ny = l.getY();
        }

        if (this.onGround || !was && this.vel.getY() > 0.0) {
            this.peakY = ny;
        } else {
            this.peakY = Math.max(this.peakY, ny);
        }

        Location to = new Location(w, nx, ny, nz, l.getYaw(), l.getPitch());
        if (to.distanceSquared(l) > 1.0E-6) {
            this.body.teleport(to);
        }
    }

    // ------------------------------------------------------------------ the tick

    void tick() {
        this.tick++;
        if (this.attackReady > 0) {
            this.attackReady--;
        }

        if (this.crystalReady > 0) {
            this.crystalReady--;
        }

        if (this.smashReady > 0) {
            this.smashReady--;
        }

        if (this.elytraReady > 0) {
            this.elytraReady--;
        }

        if (this.bowReady > 0) {
            this.bowReady--;
        }

        if (this.healReady > 0) {
            this.healReady--;
        }

        if (this.shieldBroken > 0) {
            this.shieldBroken--;
        }

        if (this.dummy != null) {
            this.dummyTick();
        } else if (this.target != null && this.target.isOnline() && this.target.getWorld().equals(this.body.getWorld())) {
            this.fightTick();
        }

        if (this.body.isValid()) {
            this.physics();
        }

        if (this.label != null && this.tick % 10 == 0) {
            this.label.teleport(this.body.getLocation().add(0.0, 2.55, 0.0));
            this.label.text(this.statsText());
        }
    }

    private Component statsText() {
        long secs = Math.max(1L, (System.currentTimeMillis() - this.started) / 1000L);
        String extra = switch (this.dummy) {
            case SHIELD -> "<gray>Shield breaks <white>" + this.shieldBreaks;
            case CRYSTAL -> "<gray>Totem pops <white>" + this.pops + " <dark_gray>| <gray>dmg/s <white>" + String.format("%.1f", this.damageTaken / secs);
            case MACE, ELYTRA -> "<gray>Best smash <white>" + String.format("%.1f", this.bestSmash) + " <dark_gray>dmg";
            default -> "<gray>Best combo <white>" + this.bestCombo;
        };
        return Text.mm(
            "<gold><b>" + this.dummy.display + "</b>\n<gray>Hits <white>" + this.hitsTaken + " <dark_gray>| <gray>Damage <white>"
                + String.format("%.1f", this.damageTaken) + "\n" + extra + "\n<gray>Combo <white>" + this.combo
        );
    }

    private Player nearestPlayer(double range) {
        Player best = null;
        double bd = range * range;

        for (Player p : this.body.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(this.body.getLocation());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }

        return best;
    }

    private void dummyTick() {
        Player near = this.nearestPlayer(12.0);
        Location l = this.body.getLocation();
        switch (this.dummy) {
            case SHIELD -> {
                this.setVel(new Vector(0.0, Math.min(0.0, this.vel.getY()), 0.0));
                if (near != null) {
                    this.lookAt(near.getEyeLocation(), 0.0);
                }

                if (this.shieldBroken <= 0 && !this.body.isHandRaised()) {
                    this.body.startUsingItem(EquipmentSlot.OFF_HAND);
                }
            }
            case COMBO -> {
                if (near != null) {
                    this.lookAt(near.getEyeLocation(), 0.0);
                }

                if (this.tick % 40 == 0) {
                    this.strafeDir = -this.strafeDir;
                }

                Vector side = l.getDirection().setY(0).normalize().crossProduct(new Vector(0, 1, 0)).multiply(0.12 * this.strafeDir);
                Vector back = this.home.toVector().subtract(l.toVector()).setY(0).multiply(0.05);
                this.setVel(side.add(back).setY(this.vel.getY()));
            }
            case ELYTRA -> {
                double a = this.tick * 0.02;
                Location want = this.home.clone().add(Math.cos(a) * 9.0, 12.0 + Math.sin(a * 2.0) * 2.0, Math.sin(a) * 9.0);
                this.setVel(want.toVector().subtract(l.toVector()).multiply(0.15));
                this.lookAt(want.clone().add(-Math.sin(a), 0, Math.cos(a)), 0.0);
            }
            default -> {
                this.setVel(new Vector(0.0, Math.min(0.0, this.vel.getY()), 0.0));
                if (near != null) {
                    this.lookAt(near.getEyeLocation(), 0.0);
                }
            }
        }

        if (System.currentTimeMillis() - this.lastHitTaken > 1500L) {
            this.combo = 0;
        }
    }

    private void fightTick() {
        Location me = this.body.getLocation();
        Location them = this.target.getLocation();
        double d = me.distance(them);
        double aim = this.diff.aimError * (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0;
        if (this.phase == 0) {
            this.lookAt(this.target.getEyeLocation(), aim);
        }

        if (this.eating > 0) {
            this.eating--;
            this.move(them, d, true);
            if (this.eating == 0) {
                this.body.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 100, 1));
                this.body.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION, 2400, 0));
                this.body.getEquipment().setItemInMainHand(this.mainWeapon());
                Snd.at(this.body.getLocation(), "entity.player.burp", 0.8F, 1.0F);
            }

            return;
        }

        // techniques first (they take over movement)
        if (this.phase != 0) {
            this.techniqueTick(them, d);
            return;
        }

        int skill = this.diff.skill;
        double hp = this.body.getHealth();
        if (hp < 9.0 && this.healReady <= 0) {
            if (this.potCount > 0) {
                this.splashHeal();
                return;
            } else if (this.gappleCount > 0 && d > 3.0) {
                this.gappleCount--;
                this.eating = 32;
                this.healReady = 120;
                this.body.getEquipment().setItemInMainHand(new ItemStack(Material.GOLDEN_APPLE));
                this.body.startUsingItem(EquipmentSlot.HAND);
                return;
            }
        }

        // mace: launch for a smash (wind charge), the stun slam at Hard+
        if (this.mace && skill >= 2 && this.smashReady <= 0 && d < 5.0 && this.onGround) {
            this.phase = 1;
            this.phaseTick = 0;
            this.smashReady = skill >= 4 ? 70 : 110;
            this.setVel(them.toVector().subtract(me.toVector()).setY(0).normalize().multiply(0.25).setY(1.25));
            Snd.at(me, "entity.wind_charge.wind_burst", 0.8F, 1.0F);
            me.getWorld().spawnParticle(Particle.GUST_EMITTER_SMALL, me, 1);
            return;
        }

        // elytra mace dive at Expert (or with an elytra in the kit)
        if (this.mace && this.elytra && skill >= 3 && this.elytraReady <= 0 && d > 6.0 && d < 22.0) {
            this.phase = 3;
            this.phaseTick = 0;
            this.elytraReady = 260;
            this.body.getEquipment().setChestplate(new ItemStack(Material.ELYTRA));
            Snd.at(me, "entity.firework_rocket.launch", 1.0F, 1.0F);
            return;
        }

        // crystal PvP
        if (this.crystals && skill >= 1 && this.crystalReady <= 0 && d > 2.2 && d < 6.0) {
            if (this.anchors && skill >= 4 && ThreadLocalRandom.current().nextInt(4) == 0) {
                this.anchor();
            } else {
                this.crystal();
            }

            this.crystalReady = switch (this.diff) {
                case EASY -> 40;
                case MEDIUM -> 24;
                case HARD -> 14;
                case EXPERT -> 8;
            };
        }

        // bow from range
        if (this.bow && d > 12.0 && this.bowReady <= 0 && skill >= 2) {
            this.body.getEquipment().setItemInMainHand(this.bowItem.clone());
            Vector v = this.target.getEyeLocation().toVector().subtract(this.body.getEyeLocation().toVector());
            v.setY(v.getY() + d * 0.06).normalize().multiply(2.6);
            this.body.launchProjectile(org.bukkit.entity.Arrow.class, v);
            Snd.at(me, "entity.arrow.shoot", 1.0F, 1.0F);
            this.bowReady = 30;
        } else if (this.bow && d <= 12.0 && this.body.getEquipment().getItemInMainHand().getType() == this.bowItem.getType()) {
            this.body.getEquipment().setItemInMainHand(this.mainWeapon());
        }

        // shield: up while its own weapon recharges, down to swing
        boolean wantShield = this.shield && skill >= 2 && this.shieldBroken <= 0 && d < 4.0 && this.attackReady > 6 && this.target.getAttackCooldown() > 0.8F;
        if (wantShield && !this.body.isHandRaised()) {
            this.body.startUsingItem(EquipmentSlot.OFF_HAND);
        } else if (!wantShield && this.body.isHandRaised() && this.eating <= 0) {
            this.body.clearActiveItem();
        }

        this.move(them, d, false);
        // stuck behind a cliff or in a hole (no pathfinding): after 1.5s without getting closer, hop next to them
        if (d < this.lastDist - 0.5 || d < 4.0) {
            this.lastDist = d;
            this.stuck = 0;
        } else if (++this.stuck > 30) {
            this.stuck = 0;
            this.lastDist = 99.0;
            Vector off = me.toVector().subtract(them.toVector()).setY(0);
            if (off.lengthSquared() < 1.0E-4) {
                off = new Vector(1, 0, 0);
            }

            Location to = them.clone().add(off.normalize().multiply(5.0));
            to.setY(to.getWorld().getHighestBlockYAt(to) + 1.0);
            if (Math.abs(to.getY() - them.getY()) < 6.0) {
                me.getWorld().spawnParticle(Particle.CLOUD, me.clone().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
                this.body.teleport(to);
                this.vel = new Vector();
                this.peakY = to.getY();
            }
        }

        // melee
        if (d <= 3.1 && this.attackReady <= 0 && this.tick % Math.max(1, this.diff.reaction) == 0) {
            this.melee(d);
        }
    }

    /** Walks/sprints toward the target, strafing, w-tapping after a hit, jumping over blocks and for crits. */
    private void move(Location them, double d, boolean slow) {
        Location me = this.body.getLocation();
        Vector to = them.toVector().subtract(me.toVector()).setY(0);
        if (to.lengthSquared() < 1.0E-4) {
            return;
        }

        to.normalize();
        Vector side = to.clone().crossProduct(new Vector(0, 1, 0));
        if (this.tick > this.strafeUntil) {
            this.strafeDir = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            this.strafeUntil = this.tick + 15 + ThreadLocalRandom.current().nextInt(30);
        }

        double speed = slow ? 0.12 : 0.27;
        Vector v;
        if (this.wtap > 0) {
            this.wtap--;
            v = to.clone().multiply(-0.15);
        } else if (d > 2.6) {
            v = to.clone().multiply(speed).add(side.multiply(this.diff.skill >= 2 ? 0.08 * this.strafeDir : 0.0));
        } else {
            v = side.multiply(0.14 * this.strafeDir).add(to.clone().multiply(d < 1.6 ? -0.1 : 0.02));
        }

        double vy = this.vel.getY();
        boolean ground = this.onGround;
        if (ground) {
            Block ahead = me.clone().add(to.clone().multiply(0.8)).getBlock();
            boolean wall = ahead.getType().isSolid() && !ahead.getRelative(0, 1, 0).getType().isSolid();
            boolean crit = this.diff.skill >= 2 && d < 3.6 && this.attackReady <= 6 && ThreadLocalRandom.current().nextInt(3) == 0;
            if (wall || crit) {
                vy = 0.42;
            }
        }

        this.setVel(new Vector(v.getX(), vy, v.getZ()));
    }

    private void lookAt(Location at, double errorDeg) {
        Location me = this.body.getEyeLocation();
        Vector dir = at.toVector().subtract(me.toVector());
        if (dir.lengthSquared() > 1.0E-6) {
            float yaw = (float)(Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())) + errorDeg);
            float pitch = (float)Math.toDegrees(-Math.atan2(dir.getY(), Math.sqrt(dir.getX() * dir.getX() + dir.getZ() * dir.getZ())));
            this.body.setRotation(yaw, pitch);
        }
    }

    // ------------------------------------------------------------------ hitting

    static double weaponDamage(ItemStack it) {
        if (Items.isEmpty(it)) {
            return 1.0;
        } else {
            String n = it.getType().name();
            double base = switch (n) {
                case "WOODEN_SWORD", "GOLDEN_SWORD" -> 4.0;
                case "STONE_SWORD" -> 5.0;
                case "IRON_SWORD" -> 6.0;
                case "DIAMOND_SWORD" -> 7.0;
                case "NETHERITE_SWORD" -> 8.0;
                case "WOODEN_AXE", "GOLDEN_AXE" -> 7.0;
                case "STONE_AXE", "IRON_AXE", "DIAMOND_AXE" -> 9.0;
                case "NETHERITE_AXE" -> 10.0;
                case "MACE" -> 6.0;
                case "TRIDENT" -> 9.0;
                default -> n.endsWith("_SPEAR") ? 6.0 : 1.0;
            };
            int sharp = it.getEnchantmentLevel(Enchantment.SHARPNESS);
            return base + (sharp > 0 ? 0.5 * sharp + 0.5 : 0.0);
        }
    }

    static int cooldownOf(ItemStack it) {
        if (Items.isEmpty(it)) {
            return 5;
        } else {
            String n = it.getType().name();
            return n.endsWith("_SWORD") ? 12 : (n.endsWith("_AXE") ? 20 : (n.equals("MACE") ? 33 : (n.equals("TRIDENT") ? 22 : 16)));
        }
    }

    private void melee(double d) {
        Player t = this.target;
        // axe the shield first (Hard+): five seconds without a shield
        if (this.axe && this.diff.skill >= 3 && t.isBlocking()) {
            this.body.getEquipment().setItemInMainHand(this.axeItem.clone());
            this.breakShield(t);
            this.body.swingMainHand();
            this.attackReady = 6;
            return;
        }

        ItemStack w = this.mainWeapon();
        if (this.axe && !this.sword && this.axeItem != null) {
            w = this.axeItem.clone();
        }

        this.body.getEquipment().setItemInMainHand(w);
        this.body.swingMainHand();
        this.attackReady = (int)Math.round(cooldownOf(w) * (this.diff == Difficulty.EASY ? 1.3 : 1.0));
        if (ThreadLocalRandom.current().nextDouble() > this.diff.accuracy) {
            return;
        }

        double dmg = weaponDamage(w);
        boolean crit = !this.onGround && this.vel.getY() < 0.0;
        if (crit) {
            dmg *= 1.5;
            t.getWorld().spawnParticle(Particle.CRIT, t.getLocation().add(0.0, 1.0, 0.0), 12, 0.3, 0.4, 0.3, 0.1);
        }

        this.hit(t, dmg, DamageType.MOB_ATTACK, 0.4 + (this.wtap > 0 ? 0.0 : 0.1));
        if (this.diff.skill >= 2) {
            this.wtap = 3;
        }
    }

    void breakShield(Player t) {
        t.setCooldown(Material.SHIELD, 100);
        t.clearActiveItem();
        Snd.at(t.getLocation(), "item.shield.break", 1.0F, 1.0F);
        t.sendActionBar(Text.mm("<red>⚠ Shield broken - 5s"));
    }

    void hit(Player t, double dmg, DamageType type, double kb) {
        double before = t.getHealth() + t.getAbsorptionAmount();
        DamageSource src = DamageSource.builder(type).withCausingEntity(this.body).withDirectEntity(this.body).build();
        t.damage(dmg, src);
        double dealt = Math.max(0.0, before - (t.getHealth() + t.getAbsorptionAmount()));
        if (dealt > 0.0) {
            this.hitsGiven++;
            this.damageGiven += dealt;
            Vector away = t.getLocation().toVector().subtract(this.body.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 1.0E-4) {
                away.normalize().multiply(kb);
                t.setVelocity(t.getVelocity().multiply(0.5).add(new Vector(away.getX(), 0.36, away.getZ())));
            }
        }
    }

    // ------------------------------------------------------------------ techniques

    private void techniqueTick(Location them, double d) {
        this.phaseTick++;
        Location me = this.body.getLocation();
        switch (this.phase) {
            case 1 -> {
                // rising after the launch: drift over the target
                Vector to = them.toVector().subtract(me.toVector()).setY(0);
                Vector v = this.vel;
                if (to.lengthSquared() > 0.25) {
                    to.normalize().multiply(0.18);
                    this.setVel(new Vector(to.getX(), v.getY(), to.getZ()));
                }

                this.lookAt(this.target.getLocation(), 0.0);
                if (v.getY() < 0.0) {
                    this.phase = 2;
                }

                if (this.phaseTick > 40) {
                    this.phase = 0;
                }
            }
            case 2 -> {
                this.lookAt(this.target.getLocation(), 0.0);
                Vector to = them.toVector().subtract(me.toVector()).setY(0);
                if (to.lengthSquared() > 0.25) {
                    to.normalize().multiply(0.15);
                    this.setVel(new Vector(to.getX(), this.vel.getY(), to.getZ()));
                }

                if (d < 3.4 && this.fall() > 1.5) {
                    this.smash(d);
                    this.phase = 0;
                } else if (this.onGround || this.phaseTick > 60) {
                    this.phase = 0;
                }
            }
            case 3 -> {
                // rocket climb
                this.setVel(new Vector(0.0, 1.1, 0.0));
                me.getWorld().spawnParticle(Particle.FIREWORK, me, 3, 0.1, 0.1, 0.1, 0.02);
                if (this.phaseTick > 14) {
                    this.phase = 4;
                    this.phaseTick = 0;
                    this.body.setGliding(true);
                }
            }
            case 4 -> {
                // glide over the target
                this.body.setGliding(true);
                Vector to = them.toVector().subtract(me.toVector());
                Vector flat = to.clone().setY(0);
                double h = flat.length();
                this.lookAt(them, 0.0);
                if (h > 0.1) {
                    flat.normalize().multiply(0.7);
                }

                this.setVel(new Vector(flat.getX(), -0.05, flat.getZ()));
                if (h < 5.0 || this.phaseTick > 60) {
                    this.phase = 5;
                    this.phaseTick = 0;
                }
            }
            case 5 -> {
                // dive
                Vector to = them.toVector().add(new Vector(0, 1, 0)).subtract(me.toVector());
                this.lookAt(them, 0.0);
                this.setVel(to.normalize().multiply(1.6));
                this.peakY = Math.max(this.peakY, this.body.getY() + 10.0);
                if (d < 3.2) {
                    this.smash(d);
                    this.endGlide();
                } else if (this.onGround || this.phaseTick > 30) {
                    this.endGlide();
                }
            }
            default -> this.phase = 0;
        }
    }

    private void endGlide() {
        this.phase = 0;
        this.body.setGliding(false);
        ItemStack chest = this.kit.contents[38];
        this.body.getEquipment().setChestplate(chest == null ? null : chest.clone());
        this.peakY = this.body.getY();
    }

    /** Mace smash: vanilla fall-distance damage; with an axe at Hard+ the shield is broken in the same fall (stun slam). */
    private void smash(double d) {
        Player t = this.target;
        if (t.isBlocking() && this.axe && this.diff.skill >= 3) {
            this.breakShield(t);
            t.sendActionBar(Text.mm("<red>⚡ Stun slam!"));
        }

        this.body.getEquipment().setItemInMainHand(this.maceItem.clone());
        this.body.swingMainHand();
        float fall = this.fall();
        double bonus = fall <= 3.0F ? fall * 4.0 : (fall <= 8.0F ? 12.0 + (fall - 3.0F) * 2.0 : 22.0 + (fall - 8.0F));
        int density = this.maceItem.getEnchantmentLevel(Enchantment.DENSITY);
        bonus += density * 0.5 * fall;
        double dmg = weaponDamage(this.maceItem) + bonus;
        this.hit(t, dmg, DamageType.MACE_SMASH, 0.6);
        Location l = t.getLocation();
        l.getWorld().spawnParticle(Particle.DUST_PILLAR, l, 40, 1.0, 0.1, 1.0, 0.1, l.clone().subtract(0, 1, 0).getBlock().getBlockData());
        Snd.at(l, fall > 5.0F ? "item.mace.smash_ground_heavy" : "item.mace.smash_ground", 1.0F, 1.0F);
        this.bestSmash = Math.max(this.bestSmash, dmg);
        this.peakY = this.body.getY();
        this.setVel(new Vector(0.0, 0.5, 0.0));
        this.attackReady = 10;
    }

    /** Obsidian next to the target, a crystal on it, then it is hit (sooner on harder difficulties). */
    private void crystal() {
        Location tl = this.target.getLocation();
        Location me = this.body.getLocation();
        Vector dir = me.toVector().subtract(tl.toVector()).setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            return;
        }

        dir.normalize();
        Block feet = tl.getBlock();
        Block base = feet.getRelative((int)Math.round(dir.getX()), -1, (int)Math.round(dir.getZ()));
        if (!base.getRelative(0, 1, 0).getType().isAir() || !base.getRelative(0, 2, 0).getType().isAir()) {
            return;
        }

        if (base.getType() != Material.OBSIDIAN && base.getType() != Material.BEDROCK) {
            if (!base.getType().isAir() && !base.getType().isSolid()) {
                return;
            }

            base.setType(Material.OBSIDIAN, false);
            this.placed.add(base.getLocation());
            Snd.at(base.getLocation(), "block.stone.place", 1.0F, 1.0F);
        }

        this.body.getEquipment().setItemInMainHand(new ItemStack(Material.END_CRYSTAL));
        this.body.swingMainHand();
        World w = base.getWorld();
        Location at = base.getLocation().add(0.5, 1.0, 0.5);
        EnderCrystal c = w.spawn(at, EnderCrystal.class, e -> {
            e.setShowingBottom(false);
            e.setPersistent(false);
        });
        this.spawned.add(c);
        int delay = switch (this.diff) {
            case EASY -> 14;
            case MEDIUM -> 8;
            case HARD -> 4;
            case EXPERT -> 1;
        };
        net.vortexpvp.util.Tasks.later(delay, () -> {
            if (c.isValid() && this.valid()) {
                this.body.swingMainHand();
                Location el = c.getLocation();
                c.remove();
                w.createExplosion(el, 6.0F, false, false, this.body);
            }
        });
    }

    /** Respawn anchor: placed, charged with glowstone and set off next to the target (overworld). */
    private void anchor() {
        Location tl = this.target.getLocation();
        Vector dir = this.body.getLocation().toVector().subtract(tl.toVector()).setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            return;
        }

        dir.normalize();
        Block b = tl.getBlock().getRelative((int)Math.round(dir.getX()), 0, (int)Math.round(dir.getZ()));
        if (b.getType().isAir()) {
            b.setType(Material.RESPAWN_ANCHOR, false);
            this.placed.add(b.getLocation());
            this.body.swingMainHand();
            Snd.at(b.getLocation(), "block.respawn_anchor.charge", 1.0F, 1.0F);
            net.vortexpvp.util.Tasks.later(this.diff == Difficulty.EXPERT ? 3L : 8L, () -> {
                if (this.valid() && b.getType() == Material.RESPAWN_ANCHOR) {
                    b.setType(Material.AIR, false);
                    b.getWorld().createExplosion(b.getLocation().add(0.5, 0.5, 0.5), 5.0F, false, false, this.body);
                }
            });
        }
    }

    private void splashHeal() {
        this.potCount--;
        this.healReady = 30;
        ItemStack pot = Items.of(Material.SPLASH_POTION).potion(org.bukkit.potion.PotionType.STRONG_HEALING).build();
        this.body.setRotation(this.body.getLocation().getYaw(), 90.0F);
        org.bukkit.entity.ThrownPotion tp = this.body.launchProjectile(org.bukkit.entity.ThrownPotion.class, new Vector(0.0, -0.6, 0.0));
        tp.setItem(pot);
    }

    /** After a totem pop: a new totem in hand after a delay (faster on harder difficulties). */
    void reTotem() {
        this.pops++;
        if (this.totems > 0) {
            this.totems--;
            int delay = this.dummy != null ? 2 : switch (this.diff) {
                case EASY -> 30;
                case MEDIUM -> 14;
                case HARD -> 6;
                case EXPERT -> 2;
            };
            net.vortexpvp.util.Tasks.later(delay, () -> {
                if (this.valid()) {
                    this.body.getEquipment().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
                }
            });
        }
    }

    /** The player hit the bot (stats, shield break by an axe). */
    void hitBy(Player p, double damage, boolean axeHit) {
        if (this.dummy != DummyType.SHIELD && this.dummy != DummyType.CRYSTAL && !this.floating) {
            Vector away = this.body.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 1.0E-4) {
                away.normalize().multiply(p.isSprinting() ? 0.55 : 0.4);
                this.vel = new Vector(away.getX(), 0.36, away.getZ());
                this.onGround = false;
            }
        }

        this.hitsTaken++;
        this.damageTaken += damage;
        long now = System.currentTimeMillis();
        this.combo = now - this.lastHitTaken < 1500L ? this.combo + 1 : 1;
        this.bestCombo = Math.max(this.bestCombo, this.combo);
        this.lastHitTaken = now;
        if (axeHit && this.body.isHandRaised() && this.shield) {
            this.body.clearActiveItem();
            this.shieldBroken = 100;
            this.shieldBreaks++;
            Snd.at(this.body.getLocation(), "item.shield.break", 1.0F, 1.0F);
        }

        if (p.getFallDistance() > 1.5F && Items.isEmpty(p.getInventory().getItemInMainHand()) == false
            && p.getInventory().getItemInMainHand().getType() == Material.MACE) {
            this.bestSmash = Math.max(this.bestSmash, damage);
        }
    }

    /** Back on solid ground after leaving the map (a launch or a glide that went wrong). */
    void recover(Location to) {
        this.phase = 0;
        this.body.setGliding(false);
        ItemStack chest = this.kit.contents[38];
        if (this.dummy == null) {
            this.body.getEquipment().setChestplate(chest == null ? null : chest.clone());
        }

        this.setVel(new Vector());
        this.body.teleport(to);
        this.peakY = to.getY();
    }

    // read by the Academy
    public Mannequin body() {
        return this.body;
    }

    public int hitsTaken() {
        return this.hitsTaken;
    }

    public int bestCombo() {
        return this.bestCombo;
    }

    public int shieldBreaks() {
        return this.shieldBreaks;
    }

    public double bestSmash() {
        return this.bestSmash;
    }

    public int pops() {
        return this.pops;
    }

    public double damageTaken() {
        return this.damageTaken;
    }

    /** Ticked by someone else than the bot manager (the Academy's practice part). */
    public void tickExternal() {
        if (this.valid()) {
            this.tick();
        }
    }

    static LivingEntity asLiving(Entity e) {
        return e instanceof LivingEntity l ? l : null;
    }
}
