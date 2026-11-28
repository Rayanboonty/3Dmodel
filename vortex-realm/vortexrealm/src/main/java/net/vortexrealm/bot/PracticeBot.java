package net.vortexrealm.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Text;
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
    final VortexRealm plugin;
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
    final ItemStack spearItem;
    final ItemStack tridentItem;
    final boolean rod;
    final boolean flint;
    // what it can spend (from the kit's item counts)
    int pearls;
    int windCharges;
    int crystalCount;
    int anchorCount;
    int glowstone;
    int webs;
    int lavaBuckets;
    int waterBuckets;
    int blocks;
    int carts;
    int creeperEggs;
    final List<org.bukkit.potion.PotionEffect> buffs = new ArrayList<>();
    boolean buffed;
    int pearlReady = 70;
    int rodReady = 66;
    int webReady = 90;
    int lavaReady = 110;
    int cartReady = 80;
    int creeperReady = 64;
    int lungeReady = 62;
    int tridentReady = 62;
    /** The bot waits where it is until this tick (the 3-2-1 countdown). */
    int holdUntil;
    int pillarReady;
    /** Until this tick the bot chains crystals as fast as it can (d-tap after the target's totem popped). */
    int dtapUntil;
    org.bukkit.entity.EnderPearl pearl;
    Location pearlLast;
    int pearlAge;
    boolean totemHeld;
    /** Blocks the bot put down for a moment (webs, lava, water): location -> tick they are taken away. */
    final java.util.Map<Location, Integer> temp = new java.util.HashMap<>();
    // how often each technique was used (menus, tests)
    public int usedCrystals;
    public int usedDtaps;
    public int usedAnchors;
    public int usedWind;
    public int usedPearls;
    public int usedRod;
    public int usedWebs;
    public int usedLava;
    public int usedCarts;
    public int usedCreepers;
    public int usedLunges;
    public int usedTridents;
    // extra dummy stats
    long lastPopAt;
    long bestPopGap = -1L;
    int dtaps;
    double anchorDamage;
    int slams;
    long lastBreakAt;
    double bestFall;
    int pearlsLanded;
    int pearlsThrown;
    double bestPearl = -1.0;
    int potsThrown;
    int sprintHits;
    double bestSpear;
    int lunges;
    double bestLunge;
    int spearMaces;
    long lastSpearAt;
    int wtapStreak;
    int bestWtap;
    int crits;
    double bestCrit;
    int arrowHits;
    double bestShot;
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

    PracticeBot(VortexRealm plugin, Location at, Kit kit, Difficulty diff, DummyType dummy, String name) {
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
        ItemStack sp = null;
        ItemStack tr = null;
        boolean rd = false;
        boolean fl = false;
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
                    // only real healing potions count as heals
                    if (it.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta pm && pm.getBasePotionType() != null
                        && pm.getBasePotionType().name().contains("HEALING")) {
                        pt += it.getAmount();
                    }
                } else if (n.equals("POTION")) {
                    // drinkable potions (strength, speed, fire resistance...) are drunk when the fight starts
                    if (it.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta pm && pm.getBasePotionType() != null) {
                        for (org.bukkit.potion.PotionEffect pe : pm.getBasePotionType().getPotionEffects()) {
                            if (!pe.getType().equals(org.bukkit.potion.PotionEffectType.INSTANT_HEALTH) && this.buffs.stream().noneMatch(b -> b.getType().equals(pe.getType()))) {
                                this.buffs.add(pe);
                            }
                        }
                    }
                } else if (n.endsWith("_SPEAR") && sp == null) {
                    sp = it.clone();
                } else if (n.equals("TRIDENT") && tr == null) {
                    tr = it.clone();
                } else if (n.equals("FISHING_ROD")) {
                    rd = true;
                } else if (n.equals("FLINT_AND_STEEL")) {
                    fl = true;
                } else if (n.equals("ENDER_PEARL")) {
                    this.pearls += it.getAmount();
                } else if (n.equals("WIND_CHARGE")) {
                    this.windCharges += it.getAmount();
                } else if (n.equals("GLOWSTONE")) {
                    this.glowstone += it.getAmount();
                } else if (n.equals("COBWEB")) {
                    this.webs += it.getAmount();
                } else if (n.equals("LAVA_BUCKET")) {
                    this.lavaBuckets += it.getAmount();
                } else if (n.equals("WATER_BUCKET")) {
                    this.waterBuckets += it.getAmount();
                } else if (n.equals("TNT_MINECART")) {
                    this.carts += it.getAmount();
                } else if (n.equals("CREEPER_SPAWN_EGG")) {
                    this.creeperEggs += it.getAmount();
                } else if (n.equals("COBBLESTONE") || n.endsWith("_PLANKS") || n.endsWith("_LOG") || n.equals("OBSIDIAN")) {
                    this.blocks += it.getAmount();
                }

                if (n.equals("END_CRYSTAL")) {
                    this.crystalCount += it.getAmount();
                } else if (n.equals("RESPAWN_ANCHOR")) {
                    this.anchorCount += it.getAmount();
                }
            }
        }

        if (dummy != null && dummy.shield()) {
            sh = true;
        } else if (dummy != null && dummy.crystalLike()) {
            tt = 999;
        }

        this.swordItem = sw;
        this.axeItem = ax;
        this.maceItem = mc;
        this.bowItem = bw;
        this.spearItem = sp;
        this.tridentItem = tr;
        this.rod = rd;
        this.flint = fl;
        this.sword = sw != null;
        this.axe = ax != null;
        this.mace = mc != null;
        this.shield = sh;
        this.crystals = cr;
        this.anchors = an;
        this.bow = bw != null;
        this.elytra = el;
        this.totems = tt;
        this.gappleCount = gp;
        this.potCount = pt;
        String nm = name;
        this.body = at.getWorld().spawn(at, Mannequin.class, m -> {
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            // training dummies that stand still can't be pushed by anything (knockback, explosions, wind)
            m.setImmovable(dummy != null && dummy.still());
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
            if (dummy != null && dummy.crystalLike()) {
                eq.setHelmet(Items.of(Material.NETHERITE_HELMET).build());
                eq.setChestplate(Items.of(Material.NETHERITE_CHESTPLATE).build());
                eq.setLeggings(blast(Material.NETHERITE_LEGGINGS));
                eq.setBoots(Items.of(Material.NETHERITE_BOOTS).build());
                eq.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
                // no invulnerability after a hit: the second crystal of a d-tap really pops the next totem
                m.setMaximumNoDamageTicks(0);
            }

            if (dummy == DummyType.ELYTRA || dummy == DummyType.ELYTRA_SHIELD) {
                eq.setChestplate(new ItemStack(Material.ELYTRA));
            }

            if (dummy != null && dummy.floating()) {
                m.setGravity(false);
            }
        });
        this.floating = dummy != null && dummy.floating();
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
        if (this.dummy != null && this.dummy.shield()) {
            return null;
        } else if (this.swordItem != null) {
            return this.swordItem.clone();
        } else if (this.axeItem != null) {
            return this.axeItem.clone();
        } else if (this.maceItem != null) {
            return this.maceItem.clone();
        } else if (this.spearItem != null) {
            return this.spearItem.clone();
        } else if (this.tridentItem != null) {
            return this.tridentItem.clone();
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
        for (Location l : this.temp.keySet()) {
            l.getBlock().setType(Material.AIR, false);
        }

        this.temp.clear();
        if (this.pearl != null) {
            this.pearl.remove();
        }
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

        this.pearlReady = Math.max(0, this.pearlReady - 1);
        this.rodReady = Math.max(0, this.rodReady - 1);
        this.webReady = Math.max(0, this.webReady - 1);
        this.lavaReady = Math.max(0, this.lavaReady - 1);
        this.cartReady = Math.max(0, this.cartReady - 1);
        this.creeperReady = Math.max(0, this.creeperReady - 1);
        this.lungeReady = Math.max(0, this.lungeReady - 1);
        this.tridentReady = Math.max(0, this.tridentReady - 1);
        this.pillarReady = Math.max(0, this.pillarReady - 1);
        if (!this.temp.isEmpty()) {
            this.temp.entrySet().removeIf(en -> {
                if (this.tick >= en.getValue()) {
                    Material m = en.getKey().getBlock().getType();
                    if (m == Material.COBWEB || m == Material.LAVA || m == Material.WATER) {
                        en.getKey().getBlock().setType(Material.AIR, false);
                    }

                    return true;
                }

                return false;
            });
        }

        this.pearlTick();
        if (this.dummy != null) {
            this.dummyTick();
        } else if (this.target != null && this.target.isOnline() && this.target.getWorld().equals(this.body.getWorld())) {
            this.fightTick();
        }

        if (this.body.isValid()) {
            if (this.dummy != null && this.dummy.still()) {
                this.holdStill();
            } else {
                this.physics();
            }
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
            case DTAP -> "<gray>D-taps <green>" + this.dtaps + " <dark_gray>| <gray>pops <white>" + this.pops + " <dark_gray>| <gray>fastest <white>"
                + (this.bestPopGap < 0 ? "-" : this.bestPopGap + " ms");
            case ANCHOR -> "<gray>Anchor damage <white>" + String.format("%.0f", this.anchorDamage) + " <dark_gray>| <gray>pops <white>" + this.pops;
            case STUNSLAM -> "<gray>Stun slams <green>" + this.slams + " <dark_gray>| <gray>breaks <white>" + this.shieldBreaks + " <dark_gray>| <gray>best <white>"
                + String.format("%.1f", this.bestSmash);
            case MACE, ELYTRA -> "<gray>Best smash <white>" + String.format("%.1f", this.bestSmash) + " <dark_gray>dmg | <gray>height <white>"
                + String.format("%.1f", this.bestFall);
            case PEARL -> "<gray>Landed <green>" + this.pearlsLanded + "<gray>/" + this.pearlsThrown + " <dark_gray>| <gray>closest <white>"
                + (this.bestPearl < 0 ? "-" : String.format("%.1f", this.bestPearl) + " blocks");
            case POT -> "<gray>Pots thrown <white>" + this.potsThrown + " <dark_gray>| <gray>survived <white>" + secs + "s";
            case SPEAR -> "<gray>Best spear hit <white>" + String.format("%.1f", this.bestSpear);
            case AIR_SLAM, ELYTRA_SHIELD, SPEAR_SLAM -> "<gray>Stun slams <green>" + this.slams + " <dark_gray>| <gray>breaks <white>" + this.shieldBreaks + " <dark_gray>| <gray>best <white>"
                + String.format("%.1f", this.bestSmash);
            case SPEAR_MACE -> "<gray>Spear → mace <green>" + this.spearMaces + " <dark_gray>| <gray>best smash <white>" + String.format("%.1f", this.bestSmash);
            case SPEAR_LUNGE -> "<gray>Lunges <green>" + this.lunges + " <dark_gray>| <gray>longest <white>" + String.format("%.1f", this.bestLunge) + " <gray>blocks";
            case WTAP -> "<gray>W-tap streak <green>" + this.wtapStreak + " <dark_gray>| <gray>best <white>" + this.bestWtap;
            case CRIT -> "<gray>Crits <green>" + this.crits + " <dark_gray>| <gray>best <white>" + String.format("%.1f", this.bestCrit);
            case BOW -> "<gray>Arrow hits <green>" + this.arrowHits + " <dark_gray>| <gray>longest <white>" + String.format("%.0f", this.bestShot) + " <gray>blocks";
            default -> "<gray>Best combo <white>" + this.bestCombo + " <dark_gray>| <gray>sprint hits <white>" + this.sprintHits;
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

    /** Where a still dummy stands: its home, or 7 blocks above it for the mid-air dummy. */
    private Location stillSpot() {
        return this.dummy == DummyType.AIR_SLAM ? this.home.clone().add(0.0, 7.0, 0.0) : this.home;
    }

    /** Still dummies don't drift: whatever pushed them (a hit, a crystal, a wind charge), they are put right back. */
    private void holdStill() {
        this.vel = new Vector();
        this.body.setVelocity(new Vector());
        Location l = this.body.getLocation();
        Location want = this.stillSpot();
        if (l.getWorld() == want.getWorld() && l.distanceSquared(want) > 1.0E-4) {
            Location to = want.clone();
            to.setYaw(l.getYaw());
            to.setPitch(l.getPitch());
            this.body.teleport(to);
        }

        this.onGround = this.dummy != DummyType.AIR_SLAM;
    }

    private void dummyTick() {
        Player near = this.nearestPlayer(12.0);
        Location l = this.body.getLocation();
        switch (this.dummy) {
            case SHIELD, STUNSLAM, AIR_SLAM, SPEAR_SLAM -> {
                Player near2 = this.dummy == DummyType.AIR_SLAM ? this.nearestPlayer(24.0) : near;
                if (near2 != null) {
                    this.lookAt(near2.getEyeLocation(), 0.0);
                }

                if (this.shieldBroken <= 0 && !this.body.isHandRaised()) {
                    this.body.startUsingItem(EquipmentSlot.OFF_HAND);
                }
            }
            case ELYTRA_SHIELD -> {
                double a = this.tick * 0.025;
                Location want = this.home.clone().add(Math.cos(a) * 8.0, 9.0 + Math.sin(a * 2.0) * 1.5, Math.sin(a) * 8.0);
                this.setVel(want.toVector().subtract(l.toVector()).multiply(0.15));
                this.lookAt(want.clone().add(-Math.sin(a), 0, Math.cos(a)), 0.0);
                if (!this.body.isGliding()) {
                    this.body.setGliding(true);
                }

                if (this.shieldBroken <= 0 && !this.body.isHandRaised()) {
                    this.body.startUsingItem(EquipmentSlot.OFF_HAND);
                }
            }
            case SPEAR_LUNGE -> {
                if (near != null) {
                    this.lookAt(near.getEyeLocation(), 0.0);
                }

                if (this.tick % 50 == 0) {
                    this.strafeDir = -this.strafeDir;
                }

                Vector side = new Vector(0, 0, 1).multiply(0.16 * this.strafeDir);
                Vector back = this.home.toVector().subtract(l.toVector()).setY(0).multiply(0.06);
                this.setVel(side.add(back).setY(this.vel.getY()));
            }
            case WTAP -> {
                Player n2 = this.nearestPlayer(16.0);
                Vector back = this.home.toVector().subtract(l.toVector()).setY(0);
                Vector v = new Vector();
                if (n2 != null) {
                    this.lookAt(n2.getEyeLocation(), 0.0);
                    Vector away = l.toVector().subtract(n2.getLocation().toVector()).setY(0);
                    if (away.lengthSquared() > 1.0E-4 && away.lengthSquared() < 16.0) {
                        v.add(away.normalize().multiply(0.13));
                    }
                }

                if (back.lengthSquared() > 36.0) {
                    v.add(back.normalize().multiply(0.15));
                }

                this.setVel(v.setY(this.vel.getY()));
            }
            case BOW -> {
                if (this.tick % 60 == 0) {
                    this.strafeDir = -this.strafeDir;
                }

                Vector back = this.home.toVector().subtract(l.toVector()).setY(0);
                Vector v = new Vector(0, 0, 1).multiply(0.14 * this.strafeDir).add(back.lengthSquared() > 64.0 ? back.normalize().multiply(0.2) : new Vector());
                this.setVel(v.setY(this.vel.getY()));
                Player n2 = this.nearestPlayer(48.0);
                if (n2 != null) {
                    this.lookAt(n2.getEyeLocation(), 0.0);
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
            case PEARL -> {
                // runs along a line through its start, turning round every few seconds
                if (this.tick % 70 == 0) {
                    this.strafeDir = -this.strafeDir;
                }

                Vector axis = new Vector(1, 0, 0);
                Vector back = this.home.toVector().subtract(l.toVector()).setY(0);
                Vector v = axis.multiply(0.2 * this.strafeDir).add(back.lengthSquared() > 100.0 ? back.normalize().multiply(0.25) : new Vector());
                this.setVel(v.setY(this.vel.getY()));
                this.lookAt(l.clone().add(v.getX(), 1.6, v.getZ()), 0.0);
            }
            case POT -> {
                this.setVel(new Vector(0.0, Math.min(0.0, this.vel.getY()), 0.0));
                if (near != null) {
                    this.lookAt(near.getEyeLocation(), 0.0);
                    // steady chip damage so the player has to keep healing
                    if (this.tick % 14 == 0 && this.tick > 60 && this.target != null && this.target.isOnline()) {
                        this.body.swingMainHand();
                        DamageSource src = DamageSource.builder(DamageType.MAGIC).withCausingEntity(this.body).withDirectEntity(this.body).build();
                        this.target.damage(3.0, src);
                    }
                }
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

        if (this.tick < this.holdUntil) {
            this.setVel(new Vector(0.0, this.vel.getY(), 0.0));
            return;
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
        if (!this.buffed) {
            // the kit's drinkable potions (strength, speed, fire resistance...)
            this.buffed = true;
            for (org.bukkit.potion.PotionEffect pe : this.buffs) {
                this.body.addPotionEffect(pe);
            }
        }

        // a kit with a shield and totems: the totem goes in the off hand when low, the shield comes back when healed
        if (this.shield && this.totems > 0 && skill >= 2) {
            if (!this.totemHeld && hp < 8.0) {
                this.totemHeld = true;
                this.body.clearActiveItem();
                this.body.getEquipment().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            } else if (this.totemHeld && hp > 15.0) {
                this.totemHeld = false;
                this.body.getEquipment().setItemInOffHand(new ItemStack(Material.SHIELD));
            }
        }

        // on fire with a water bucket: water at its own feet for a moment
        if (this.body.getFireTicks() > 20 && this.waterBuckets > 0 && skill >= 2 && me.getBlock().getType().isAir()) {
            this.tempBlock(me.getBlock(), Material.WATER, 25);
            this.body.setFireTicks(0);
            Snd.at(me, "item.bucket.empty", 1.0F, 1.0F);
        }

        // low on health: pearl away (Hard and Expert)
        if (hp < 6.0 && d < 5.0 && this.pearls > 0 && skill >= 3 && this.pearlReady <= 0 && this.pearl == null) {
            Vector away = me.toVector().subtract(them.toVector()).setY(0);
            if (away.lengthSquared() > 1.0E-4) {
                this.throwPearl(away.normalize().multiply(1.1).setY(0.45));
                return;
            }
        }

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
        if (this.mace && this.windCharges > 0 && skill >= 2 && this.smashReady <= 0 && d < 5.0 && this.onGround) {
            // a wind charge thrown at its own feet launches it
            this.windCharges--;
            this.usedWind++;
            this.phase = 1;
            this.phaseTick = 0;
            this.smashReady = skill >= 4 ? 70 : 110;
            this.body.getEquipment().setItemInMainHand(new ItemStack(Material.WIND_CHARGE));
            this.body.swingMainHand();
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
        boolean dtap = this.tick < this.dtapUntil;
        if (this.crystals && this.crystalCount > 0 && skill >= 1 && this.crystalReady <= 0 && d > 2.2 && d < 6.0) {
            if (!dtap && this.anchors && this.anchorCount > 0 && this.glowstone > 0 && skill >= 3 && ThreadLocalRandom.current().nextInt(4) == 0) {
                this.anchor();
            } else {
                this.crystal(dtap);
            }

            // d-tap: right after the target's totem popped, the next crystals come as fast as possible
            this.crystalReady = dtap ? 3 : switch (this.diff) {
                case EASY -> 40;
                case MEDIUM -> 24;
                case HARD -> 14;
                case EXPERT -> 8;
            };
        }

        if (this.utility(me, them, d, skill)) {
            return;
        }

        // bow from range
        double bowFrom = this.mainWeapon() == null ? 4.0 : 12.0;
        if (this.bow && d > bowFrom && this.bowReady <= 0 && (skill >= 2 || this.mainWeapon() == null)) {
            this.body.getEquipment().setItemInMainHand(this.bowItem.clone());
            Vector v = this.target.getEyeLocation().toVector().subtract(this.body.getEyeLocation().toVector());
            v.setY(v.getY() + d * 0.06).normalize().multiply(2.6);
            this.body.launchProjectile(org.bukkit.entity.Arrow.class, v);
            Snd.at(me, "entity.arrow.shoot", 1.0F, 1.0F);
            this.bowReady = 30;
        } else if (this.bow && d <= bowFrom && this.body.getEquipment().getItemInMainHand().getType() == this.bowItem.getType()) {
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
            to.setY(BotManager.groundY(to, them.getY()));
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
        } else if (this.bow && this.mainWeapon() == null) {
            // a bow-only kit: keep its distance and circle
            v = side.multiply(0.14 * this.strafeDir).add(to.clone().multiply(d < 9.0 ? -0.2 : (d > 14.0 ? 0.2 : 0.0)));
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
        if (this.kit.hitDelay >= 0 && this.kit.hitDelay < 10) {
            // combo kits: hits land as fast as the kit's hit delay allows
            this.attackReady = Math.max(2, this.kit.hitDelay + (this.diff == Difficulty.EASY ? 3 : (this.diff == Difficulty.MEDIUM ? 1 : 0)));
        }

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
            case 6 -> {
                // spear lunge: a straight charge, the hit is harder the faster it arrives
                this.lookAt(this.target.getEyeLocation(), 0.0);
                Vector to = them.toVector().subtract(me.toVector()).setY(0);
                if (to.lengthSquared() > 1.0E-4) {
                    to.normalize().multiply(0.62);
                    this.setVel(new Vector(to.getX(), this.vel.getY(), to.getZ()));
                }

                me.getWorld().spawnParticle(Particle.SWEEP_ATTACK, me.clone().add(0.0, 1.0, 0.0), 1);
                if (d < 3.3) {
                    this.body.swingMainHand();
                    if (ThreadLocalRandom.current().nextDouble() <= this.diff.accuracy) {
                        this.hit(this.target, weaponDamage(this.spearItem) * 1.6, DamageType.MOB_ATTACK, 0.7);
                    }

                    this.attackReady = 14;
                    this.phase = 0;
                    this.body.getEquipment().setItemInMainHand(this.mainWeapon());
                } else if (this.phaseTick > 16) {
                    this.phase = 0;
                    this.body.getEquipment().setItemInMainHand(this.mainWeapon());
                }
            }
            default -> this.phase = 0;
        }
    }

    // ------------------------------------------------------------------ kit utilities

    private void tempBlock(Block b, Material m, int ticks) {
        b.setType(m, false);
        Location l = b.getLocation();
        this.temp.put(l, this.tick + ticks);
        this.placed.add(l);
    }

    private void throwPearl(Vector v) {
        this.pearls--;
        this.usedPearls++;
        this.pearlReady = this.diff.skill >= 4 ? 160 : 260;
        this.body.getEquipment().setItemInMainHand(new ItemStack(Material.ENDER_PEARL));
        this.body.swingMainHand();
        Snd.at(this.body.getLocation(), "entity.ender_pearl.throw", 1.0F, 1.0F);
        try {
            this.pearl = this.body.launchProjectile(org.bukkit.entity.EnderPearl.class, v);
            this.pearl.setPersistent(false);
            this.pearlLast = this.pearl.getLocation();
            this.pearlAge = 0;
        } catch (RuntimeException ex) {
            this.pearl = null;
        }
    }

    /** The bot's pearl in flight: when it lands the bot is moved there (a Mannequin is not teleported by its own pearl). */
    private void pearlTick() {
        if (this.pearl == null) {
            return;
        }

        this.pearlAge++;
        if (this.pearl.isValid() && !this.pearl.isDead() && this.pearlAge < 80) {
            this.pearlLast = this.pearl.getLocation();
            return;
        }

        this.pearl.remove();
        this.pearl = null;
        if (this.pearlLast != null && this.valid() && this.pearlLast.getWorld().equals(this.body.getWorld())) {
            Location to = this.pearlLast.clone();
            to.setY(Math.floor(to.getY()) + 0.1);
            for (int i = 0; i < 3 && solid(to.getWorld(), to.getX(), to.getY(), to.getZ()); i++) {
                to.add(0.0, 1.0, 0.0);
            }

            to.setYaw(this.body.getLocation().getYaw());
            this.body.teleport(to);
            this.vel = new Vector();
            this.peakY = to.getY();
            this.lastDist = 99.0;
            this.stuck = 0;
            to.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0.0, 1.0, 0.0), 30, 0.3, 0.6, 0.3, 0.4);
            Snd.at(to, "entity.player.teleport", 0.8F, 1.0F);
            this.body.getEquipment().setItemInMainHand(this.mainWeapon());
        }
    }

    /** What the kit's other items are for. True when the bot is busy with one of them this tick. */
    private boolean utility(Location me, Location them, double d, int skill) {
        World w = me.getWorld();
        boolean build = this.kit.build;
        // pearl in from far away
        if (this.pearls > 0 && skill >= 2 && d > 15.0 && d < 45.0 && this.pearlReady <= 0 && this.pearl == null) {
            Vector v = them.toVector().subtract(me.toVector());
            double flat = Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
            v.setY(v.getY() + flat * 0.22).normalize().multiply(Math.min(1.5, 0.8 + flat * 0.03));
            this.throwPearl(v);
            return true;
        }

        // spear lunge from a few blocks away
        if (this.spearItem != null && skill >= 2 && this.lungeReady <= 0 && d > 4.0 && d < 9.0 && this.onGround) {
            this.lungeReady = skill >= 4 ? 60 : 100;
            this.usedLunges++;
            this.phase = 6;
            this.phaseTick = 0;
            this.body.getEquipment().setItemInMainHand(this.spearItem.clone());
            Snd.at(me, "entity.player.attack.sweep", 1.0F, 0.7F);
            return true;
        }

        // trident throw from range
        if (this.tridentItem != null && skill >= 2 && this.tridentReady <= 0 && d > 7.0 && d < 24.0) {
            this.tridentReady = skill >= 4 ? 50 : 80;
            this.usedTridents++;
            this.body.getEquipment().setItemInMainHand(this.tridentItem.clone());
            this.body.swingMainHand();
            Vector v = this.target.getEyeLocation().toVector().subtract(this.body.getEyeLocation().toVector());
            v.setY(v.getY() + d * 0.05).normalize().multiply(2.4);
            try {
                org.bukkit.entity.Trident t = this.body.launchProjectile(org.bukkit.entity.Trident.class, v);
                t.setPersistent(false);
                t.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
                this.spawned.add(t);
            } catch (RuntimeException ex) {
            }

            Snd.at(me, "item.trident.throw", 1.0F, 1.0F);
            return false;
        }

        // fishing rod: a pull that breaks their sprint, then the sword follows
        if (this.rod && skill >= 2 && this.rodReady <= 0 && d > 3.4 && d < 7.5) {
            this.rodReady = skill >= 4 ? 35 : 60;
            this.usedRod++;
            this.body.getEquipment().setItemInMainHand(new ItemStack(Material.FISHING_ROD));
            this.body.swingMainHand();
            Snd.at(me, "entity.fishing_bobber.throw", 1.0F, 1.0F);
            if (ThreadLocalRandom.current().nextDouble() <= this.diff.accuracy) {
                Vector line = this.target.getEyeLocation().toVector().subtract(this.body.getEyeLocation().toVector());
                for (double t = 0.1; t < 1.0; t += 0.12) {
                    w.spawnParticle(Particle.CRIT, this.body.getEyeLocation().add(line.clone().multiply(t)), 1, 0.0, 0.0, 0.0, 0.0);
                }

                this.hit(this.target, 0.01, DamageType.MOB_ATTACK, 0.32);
                this.target.setSprinting(false);
            }

            net.vortexrealm.util.Tasks.later(6L, () -> {
                if (this.valid() && this.phase == 0) {
                    this.body.getEquipment().setItemInMainHand(this.mainWeapon());
                }
            });
            return false;
        }

        Block feet = them.getBlock();
        boolean feetFree = feet.getType().isAir() && feet.getRelative(0, -1, 0).getType().isSolid();
        // cobweb at their feet
        if (this.webs > 0 && build && skill >= 3 && this.webReady <= 0 && d < 4.5 && feetFree) {
            this.webs--;
            this.usedWebs++;
            this.webReady = skill >= 4 ? 120 : 200;
            this.body.swingMainHand();
            this.tempBlock(feet, Material.COBWEB, 70);
            Snd.at(feet.getLocation(), "block.cobweb.place", 1.0F, 1.0F);
            return false;
        }

        // lava at their feet, picked up again a moment later
        if (this.lavaBuckets > 0 && build && skill >= 3 && this.lavaReady <= 0 && d < 4.0 && feetFree) {
            this.usedLava++;
            this.lavaReady = skill >= 4 ? 160 : 260;
            this.body.getEquipment().setItemInMainHand(new ItemStack(Material.LAVA_BUCKET));
            this.body.swingMainHand();
            this.tempBlock(feet, Material.LAVA, 30);
            Snd.at(feet.getLocation(), "item.bucket.empty_lava", 1.0F, 1.0F);
            net.vortexrealm.util.Tasks.later(8L, () -> {
                if (this.valid() && this.phase == 0) {
                    this.body.getEquipment().setItemInMainHand(this.mainWeapon());
                }
            });
            return false;
        }

        // TNT minecart on a rail next to them, lit at once
        if (this.carts > 0 && build && skill >= 3 && this.cartReady <= 0 && d > 2.5 && d < 5.5) {
            Vector dir = me.toVector().subtract(them.toVector()).setY(0);
            if (dir.lengthSquared() > 1.0E-4) {
                dir.normalize();
                Block at = feet.getRelative((int)Math.round(dir.getX()), 0, (int)Math.round(dir.getZ()));
                if (at.getType().isAir() && at.getRelative(0, -1, 0).getType().isSolid()) {
                    this.carts--;
                    this.usedCarts++;
                    this.cartReady = skill >= 4 ? 90 : 150;
                    at.setType(Material.RAIL, false);
                    this.placed.add(at.getLocation());
                    this.body.swingMainHand();
                    try {
                        org.bukkit.entity.minecart.ExplosiveMinecart cart = w.spawn(at.getLocation().add(0.5, 0.1, 0.5), org.bukkit.entity.minecart.ExplosiveMinecart.class, c -> c.setPersistent(false));
                        this.spawned.add(cart);
                        cart.setFuseTicks(skill >= 4 ? 10 : 20);
                    } catch (RuntimeException ex) {
                    }

                    Snd.at(at.getLocation(), "entity.tnt.primed", 1.0F, 1.0F);
                    return false;
                }
            }
        }

        // creeper egg next to them, lit with flint and steel
        if (this.creeperEggs > 0 && skill >= 2 && this.creeperReady <= 0 && d > 1.2 && d < 6.0) {
            Vector dir = me.toVector().subtract(them.toVector()).setY(0);
            if (dir.lengthSquared() > 1.0E-4) {
                Location at = them.clone().add(dir.normalize().multiply(1.6));
                if (at.getBlock().getType().isAir()) {
                    this.creeperEggs--;
                    this.usedCreepers++;
                    this.creeperReady = skill >= 4 ? 100 : 170;
                    this.body.swingMainHand();
                    try {
                        org.bukkit.entity.Creeper c = w.spawn(at, org.bukkit.entity.Creeper.class, x -> x.setPersistent(false));
                        this.spawned.add(c);
                        if (this.flint || skill >= 3) {
                            c.ignite();
                        }
                    } catch (RuntimeException ex) {
                    }

                    return false;
                }
            }
        }

        // they are standing above: pillar up with the kit's blocks
        if (this.blocks > 0 && build && skill >= 2 && this.pillarReady <= 0 && this.onGround && them.getY() - me.getY() > 2.5
            && Math.hypot(them.getX() - me.getX(), them.getZ() - me.getZ()) < 4.0) {
            Block under = me.getBlock();
            if (under.getType().isAir() && under.getRelative(0, 1, 0).getType().isAir() && under.getRelative(0, 2, 0).getType().isAir()) {
                this.blocks--;
                this.pillarReady = 5;
                under.setType(Material.COBBLESTONE, false);
                this.placed.add(under.getLocation());
                this.body.teleport(me.clone().add(0.0, 1.0, 0.0));
                this.peakY = me.getY() + 1.0;
                Snd.at(me, "block.stone.place", 1.0F, 1.0F);
            }
        }

        return false;
    }

    /** The target's totem just popped: at Hard and Expert the bot goes for the d-tap (a second crystal straight away). */
    void onTargetPop() {
        if (this.dummy == null && this.crystals && this.diff.skill >= 3) {
            this.dtapUntil = this.tick + (this.diff.skill >= 4 ? 16 : 10);
            this.crystalReady = 0;
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
    private void crystal(boolean fast) {
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
        this.crystalCount--;
        this.usedCrystals++;
        if (fast) {
            this.usedDtaps++;
        }

        World w = base.getWorld();
        Location at = base.getLocation().add(0.5, 1.0, 0.5);
        EnderCrystal c = w.spawn(at, EnderCrystal.class, e -> {
            e.setShowingBottom(false);
            e.setPersistent(false);
        });
        this.spawned.add(c);
        int delay = fast ? 1 : switch (this.diff) {
            case EASY -> 14;
            case MEDIUM -> 8;
            case HARD -> 4;
            case EXPERT -> 1;
        };
        net.vortexrealm.util.Tasks.later(delay, () -> {
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
            this.anchorCount--;
            this.glowstone--;
            this.usedAnchors++;
            this.body.getEquipment().setItemInMainHand(new ItemStack(Material.RESPAWN_ANCHOR));
            this.body.swingMainHand();
            Snd.at(b.getLocation(), "block.stone.place", 1.0F, 1.0F);
            long charge = this.diff == Difficulty.EXPERT ? 2L : 5L;
            // charged with glowstone first, then set off
            net.vortexrealm.util.Tasks.later(charge, () -> {
                if (this.valid() && b.getType() == Material.RESPAWN_ANCHOR && b.getBlockData() instanceof org.bukkit.block.data.type.RespawnAnchor ra) {
                    ra.setCharges(1);
                    b.setBlockData(ra, false);
                    this.body.getEquipment().setItemInMainHand(new ItemStack(Material.GLOWSTONE));
                    this.body.swingMainHand();
                    Snd.at(b.getLocation(), "block.respawn_anchor.charge", 1.0F, 1.0F);
                }
            });
            net.vortexrealm.util.Tasks.later(charge + (this.diff == Difficulty.EXPERT ? 2L : 5L), () -> {
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
        long now = System.currentTimeMillis();
        if (this.lastPopAt > 0L) {
            long gap = now - this.lastPopAt;
            if (gap <= 1200L) {
                this.dtaps++;
                this.bestPopGap = this.bestPopGap < 0 ? gap : Math.min(this.bestPopGap, gap);
                if (this.dummy == DummyType.DTAP && this.target != null && this.target.isOnline()) {
                    this.target.sendActionBar(Text.mm("<green><b>D-TAP!</b> <gray>" + gap + " ms between the pops"));
                    Snd.play(this.target, "entity.experience_orb.pickup", 0.8F, 1.6F);
                }
            }
        }

        this.lastPopAt = now;
        if (this.totems > 0) {
            this.totems--;
            // training: the new totem is there on the next tick (the used one leaves the hand after the event)
            int delay = this.dummy != null ? 1 : switch (this.diff) {
                case EASY -> 30;
                case MEDIUM -> 14;
                case HARD -> 6;
                case EXPERT -> 2;
            };
            net.vortexrealm.util.Tasks.later(delay, () -> {
                if (this.valid()) {
                    this.body.getEquipment().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
                }
            });
        }
    }

    /** The player hit the bot (stats, shield break by an axe). */
    void hitBy(Player p, double damage, boolean axeHit) {
        this.hitBy(p, damage, axeHit, false);
    }

    /** {@code arrow} = the hit was an arrow the player shot. */
    void hitBy(Player p, double damage, boolean axeHit, boolean arrow) {
        boolean still = this.dummy != null && this.dummy.still();
        if (!still && !this.floating) {
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
        if (p.isSprinting()) {
            this.sprintHits++;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean spearHit = !arrow && !Items.isEmpty(hand) && hand.getType().name().endsWith("_SPEAR");
        double dist = p.getLocation().distance(this.body.getLocation());
        boolean lunge = spearHit && (p.isSprinting() || dist > 3.2 || p.getVelocity().setY(0).length() > 0.35);
        if (this.dummy == DummyType.SPEAR_SLAM && lunge && this.body.isHandRaised()) {
            // a spear lunge into the raised shield knocks it down, like an axe
            axeHit = true;
        }

        if (axeHit && this.body.isHandRaised() && this.shield) {
            this.body.clearActiveItem();
            this.shieldBroken = 100;
            this.shieldBreaks++;
            this.lastBreakAt = now;
            Snd.at(this.body.getLocation(), "item.shield.break", 1.0F, 1.0F);
        }

        if (spearHit) {
            this.bestSpear = Math.max(this.bestSpear, damage);
            this.lastSpearAt = now;
            if (lunge) {
                this.lunges++;
                this.bestLunge = Math.max(this.bestLunge, dist);
                if (this.dummy == DummyType.SPEAR_LUNGE) {
                    p.sendActionBar(Text.mm("<aqua><b>➶ LUNGE!</b> <gray>" + String.format("%.1f", dist) + " blocks, " + String.format("%.1f", damage) + " damage"));
                    Snd.play(p, "entity.experience_orb.pickup", 0.7F, 1.5F);
                }
            }
        }

        if (arrow) {
            this.arrowHits++;
            this.bestShot = Math.max(this.bestShot, dist);
            if (this.dummy == DummyType.BOW) {
                p.sendActionBar(Text.mm("<green><b>➹ HIT!</b> <gray>" + String.format("%.0f", dist) + " blocks"));
                Snd.play(p, "entity.arrow.hit_player", 0.8F, 1.2F);
            }
        } else if (!spearHit) {
            if (p.isSprinting()) {
                this.wtapStreak++;
                this.bestWtap = Math.max(this.bestWtap, this.wtapStreak);
                if (this.dummy == DummyType.WTAP) {
                    p.sendActionBar(Text.mm("<yellow><b>W-TAP x" + this.wtapStreak + "</b>"));
                }
            } else if (this.dummy == DummyType.WTAP) {
                this.wtapStreak = 0;
                p.sendActionBar(Text.mm("<red>Not sprinting - streak reset"));
            }

            @SuppressWarnings("deprecation")
            boolean crit = p.getFallDistance() > 0.0F && !p.isOnGround() && !p.isSprinting() && !p.isInWater() && !p.isClimbing();
            if (crit) {
                this.crits++;
                this.bestCrit = Math.max(this.bestCrit, damage);
                if (this.dummy == DummyType.CRIT) {
                    p.sendActionBar(Text.mm("<gold><b>✦ CRIT!</b> <gray>" + String.format("%.1f", damage) + " damage"));
                    Snd.play(p, "entity.player.attack.crit", 0.8F, 1.2F);
                }
            }
        }

        if (!Items.isEmpty(hand) && hand.getType() == Material.MACE && this.dummy == DummyType.SPEAR_MACE && now - this.lastSpearAt < 2000L && this.lastSpearAt > 0L) {
            this.spearMaces++;
            this.lastSpearAt = 0L;
            p.sendActionBar(Text.mm("<light_purple><b>⚔ SPEAR → MACE!</b> <gray>" + String.format("%.1f", damage) + " damage"));
            Snd.play(p, "entity.experience_orb.pickup", 0.8F, 1.3F);
        }

        if (p.getFallDistance() > 1.5F && !Items.isEmpty(hand) && hand.getType() == Material.MACE) {
            this.bestSmash = Math.max(this.bestSmash, damage);
            this.bestFall = Math.max(this.bestFall, p.getFallDistance());
            if (now - this.lastBreakAt < 1500L) {
                // the shield was broken moments ago in the same fall: a stun slam
                this.slams++;
                this.lastBreakAt = 0L;
                String what = this.floating ? "AIR STUN SLAM!" : (this.dummy == DummyType.SPEAR_SLAM ? "SPEAR STUN SLAM!" : "STUN SLAM!");
                p.sendActionBar(Text.mm("<gold><b>⚡ " + what + "</b> <gray>" + String.format("%.1f", damage) + " damage"));
                Snd.play(p, "entity.experience_orb.pickup", 0.8F, 1.4F);
            }
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

    public int dtaps() {
        return this.dtaps;
    }

    public int slams() {
        return this.slams;
    }

    /** A pearl the player threw landed this far from the Pearl Target. */
    void pearlLanded(double dist) {
        this.pearlsThrown++;
        this.bestPearl = this.bestPearl < 0 ? dist : Math.min(this.bestPearl, dist);
        if (dist <= 3.0) {
            this.pearlsLanded++;
        }
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
