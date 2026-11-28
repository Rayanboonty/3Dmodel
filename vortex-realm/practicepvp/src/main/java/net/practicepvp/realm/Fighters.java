package net.practicepvp.realm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The Rift's fighters: they come out at night (much more during a Rift Night), get tougher every chapter and match
 * the gear of the player they hunt, taunt their prey, call nearby kin when one of them is hit, and remember: kill too
 * many of them and a Rift Avenger comes hunting you by name.
 */
public final class Fighters implements Listener {
    enum Kind {
        STALKER("Rift Stalker", EntityType.ZOMBIE, 24, "#B06BFF",
            "The Rift remembers your name, {player}...", "You smell like a shard. Give it to me.", "Run. It's more fun when you run."),
        ARCHER("Hollow Archer", EntityType.SKELETON, 20, "#C8B6FF",
            "I never miss. Well. Almost never.", "Stand still, little bearer.", "My arrows sing for the Choir."),
        KNIGHT("Rift Knight", EntityType.WITHER_SKELETON, 34, "#7B2CBF",
            "Kneel before the broken Crown!", "Your shard will look good on my sword.", "The Unmaker is coming. I am only the first."),
        DUELIST("Mercenary Duelist", EntityType.VINDICATOR, 30, "#FF5FA2",
            "You fight like a farmer, {player}.", "One on one. No tricks. Ha! Just kidding.", "Someone paid good shards for your head."),
        BRUTE("Rift Brute", EntityType.PIGLIN_BRUTE, 44, "#E63946",
            "SMASH THE BEARER!", "Crown... shard... MINE!", "Little bearer. Big axe."),
        AVENGER("Rift Avenger", EntityType.WITHER_SKELETON, 80, "#FF0054",
            "You killed my kin, {player}. Every one of them.", "I've followed your trail for days.", "This ends now, {player}.");

        final String display;
        final EntityType type;
        final double health;
        final String color;
        final String[] taunts;

        Kind(String display, EntityType type, double health, String color, String... taunts) {
            this.display = display;
            this.type = type;
            this.health = health;
            this.color = color;
            this.taunts = taunts;
        }
    }

    private final Realm realm;
    private final Random rnd = new Random();
    private final NamespacedKey kindKey;
    private final NamespacedKey huntKey;
    private final Map<UUID, Long> taunted = new HashMap<>();
    private int tick;

    Fighters(Realm realm) {
        this.realm = realm;
        this.kindKey = new NamespacedKey(realm.plugin, "rift_kind");
        this.huntKey = new NamespacedKey(realm.plugin, "rift_hunts");
    }

    /** Spawning chance multiplier: Rift Nights set this to 3. */
    double surge = 1.0;

    void second() {
        if (++this.tick % 20 != 0) {
            return;
        }

        World w = this.realm.overworld();
        if (w == null || !this.realm.on(w)) {
            return;
        }

        boolean night = w.getTime() > 13000 && w.getTime() < 23000;
        double chance = (night ? 0.30 : 0.04) * this.surge * (1 + (this.realm.chapter - 1) * 0.15);
        for (Player p : w.getPlayers()) {
            if (!this.realm.on(p)) {
                continue;
            }

            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.getGameMode() == org.bukkit.GameMode.CREATIVE || this.rnd.nextDouble() > chance) {
                continue;
            }

            if (this.near(p.getLocation(), 48) >= 3 + (this.surge > 1 ? 2 : 0) || p.getLocation().distanceSquared(w.getSpawnLocation()) < 80 * 80) {
                continue;
            }

            Location at = this.spot(p.getLocation(), 18, 30);
            if (at != null) {
                this.spawn(this.pickKind(), at, p);
            }
        }
    }

    private Kind pickKind() {
        int r = this.rnd.nextInt(100);
        int ch = this.realm.chapter;
        if (ch >= 3 && r < 12) {
            return Kind.BRUTE;
        }

        if (ch >= 2 && r < 32) {
            return Kind.KNIGHT;
        }

        if (r < 50) {
            return Kind.DUELIST;
        }

        return r < 75 ? Kind.ARCHER : Kind.STALKER;
    }

    int near(Location l, double r) {
        int n = 0;
        for (Entity e : l.getWorld().getNearbyEntities(l, r, r, r)) {
            if (e.getPersistentDataContainer().has(this.realm.riftKey)) {
                n++;
            }
        }

        return n;
    }

    /** A safe spot on the surface between min and max blocks away. */
    Location spot(Location c, int min, int max) {
        for (int i = 0; i < 8; i++) {
            double a = this.rnd.nextDouble() * Math.PI * 2;
            double d = min + this.rnd.nextDouble() * (max - min);
            int x = (int)(c.getX() + Math.cos(a) * d);
            int z = (int)(c.getZ() + Math.sin(a) * d);
            if (!c.getWorld().isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }

            Block top = c.getWorld().getHighestBlockAt(x, z);
            if (top.isLiquid() || top.getType() == Material.LAVA || Math.abs(top.getY() - c.getY()) > 20 && c.getY() > top.getY() - 3) {
                continue;
            }

            Block feet = top.getRelative(0, 1, 0);
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable()) {
                return feet.getLocation().add(0.5, 0, 0.5);
            }
        }

        return null;
    }

    LivingEntity spawn(Kind k, Location at, Player prey) {
        LivingEntity e = (LivingEntity)at.getWorld().spawnEntity(at, k.type);
        e.getPersistentDataContainer().set(this.realm.riftKey, PersistentDataType.BYTE, (byte)1);
        e.getPersistentDataContainer().set(this.kindKey, PersistentDataType.STRING, k.name());
        e.customName(Text.mm("<color:" + k.color + "><b>" + k.display + (k == Kind.AVENGER && prey != null ? " <white>⚔ hunting " + Text.esc(prey.getName()) : "")));
        e.setCustomNameVisible(k == Kind.AVENGER);
        e.setRemoveWhenFarAway(true);
        if (e instanceof Zombie z) {
            z.setShouldBurnInDay(false);
            z.setAdult();
        }

        if (e instanceof org.bukkit.entity.AbstractSkeleton s) {
            s.setShouldBurnInDay(false);
        }

        if (e instanceof PiglinAbstract pa) {
            pa.setImmuneToZombification(true);
        }

        double hp = k.health * (1 + (this.realm.chapter - 1) * 0.2);
        set(e, Attribute.MAX_HEALTH, hp);
        e.setHealth(hp);
        set(e, Attribute.KNOCKBACK_RESISTANCE, 0.3);
        set(e, Attribute.FOLLOW_RANGE, 40);
        this.gear(e, k, prey);
        e.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, true, false));
        if (prey != null && e instanceof Mob m) {
            m.setTarget(prey);
            if (k == Kind.AVENGER) {
                e.getPersistentDataContainer().set(this.huntKey, PersistentDataType.STRING, prey.getUniqueId().toString());
            }
        }

        at.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
        at.getWorld().playSound(at, "block.respawn_anchor.charge", 0.8F, 0.7F);
        this.realm.hideFromOthers(e);
        return e;
    }

    private static void set(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) {
            i.setBaseValue(v);
        }
    }

    /** Armor matching the prey: leather/chain early, iron, then diamond; netherite blades late in the season. */
    private void gear(LivingEntity e, Kind k, Player prey) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return;
        }

        double armor = 0;
        if (prey != null && prey.getAttribute(Attribute.ARMOR) != null) {
            armor = prey.getAttribute(Attribute.ARMOR).getValue();
        }

        int tier = armor >= 16 ? 3 : armor >= 10 ? 2 : armor >= 5 ? 1 : 0;
        if (k == Kind.AVENGER) {
            tier = Math.max(tier, 2);
        }

        Material[][] sets = {
            {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS},
            {Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS},
            {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS},
            {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}};
        ItemStack[] a = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            a[i] = new ItemStack(sets[tier][i]);
            if (a[i].getItemMeta() instanceof LeatherArmorMeta lm) {
                lm.setColor(Color.fromRGB(0x7B2CBF));
                a[i].setItemMeta(lm);
            }
        }

        if (k != Kind.BRUTE) {
            eq.setHelmet(a[0]);
            eq.setChestplate(a[1]);
            eq.setLeggings(a[2]);
            eq.setBoots(a[3]);
        }

        if (k == Kind.KNIGHT || k == Kind.AVENGER || k == Kind.STALKER) {
            Material sword = this.realm.chapter >= 4 || k == Kind.AVENGER ? Material.NETHERITE_SWORD : tier >= 2 ? Material.DIAMOND_SWORD : Material.IRON_SWORD;
            ItemStack s = new ItemStack(sword);
            if (k == Kind.AVENGER) {
                s.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
            }

            eq.setItemInMainHand(s);
        }

        eq.setHelmetDropChance(0);
        eq.setChestplateDropChance(0);
        eq.setLeggingsDropChance(0);
        eq.setBootsDropChance(0);
        eq.setItemInMainHandDropChance(0.02F);
    }

    // ------------------------------------------------------------------ behaviour

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player p) || !e.getEntity().getPersistentDataContainer().has(this.kindKey)) {
            return;
        }

        if (!this.realm.sees(p)) {
            // in preview, the season's fighters leave everyone else alone
            e.setCancelled(true);
            return;
        }

        String hunts = e.getEntity().getPersistentDataContainer().get(this.huntKey, PersistentDataType.STRING);
        if (hunts != null && !hunts.equals(p.getUniqueId().toString())) {
            // the Avenger only wants one person
            Player prey = Bukkit.getPlayer(UUID.fromString(hunts));
            if (prey != null && prey.getWorld().equals(e.getEntity().getWorld()) && prey.getLocation().distanceSquared(e.getEntity().getLocation()) < 60 * 60) {
                e.setTarget(prey);
                p = prey;
            }
        }

        Long last = this.taunted.get(e.getEntity().getUniqueId());
        if (last == null && this.rnd.nextInt(3) == 0) {
            this.taunted.put(e.getEntity().getUniqueId(), System.currentTimeMillis());
            Kind k = Kind.valueOf(e.getEntity().getPersistentDataContainer().get(this.kindKey, PersistentDataType.STRING));
            String line = k.taunts[this.rnd.nextInt(k.taunts.length)].replace("{player}", p.getName());
            p.sendMessage(Text.mm("<color:" + k.color + "><b>" + k.display + "</b></color> <dark_gray>» <gray><i>" + Text.esc(line)));
        }
    }

    /** Hit one, and its kin nearby come for you. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(this.realm.riftKey)) {
            return;
        }

        Entity d = e.getDamager();
        Player p = d instanceof Player pl ? pl : d instanceof Projectile pr && pr.getShooter() instanceof Player pl ? pl : null;
        if (p == null) {
            return;
        }

        for (Entity o : e.getEntity().getNearbyEntities(16, 8, 16)) {
            if (o instanceof Mob m && o.getPersistentDataContainer().has(this.realm.riftKey) && m.getTarget() == null) {
                m.setTarget(p);
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (!v.getPersistentDataContainer().has(this.realm.riftKey) || v.getPersistentDataContainer().has(RealmEvents.bossKey(this.realm))) {
            return;
        }

        this.taunted.remove(v.getUniqueId());
        e.getDrops().removeIf(it -> it.getType() == Material.ROTTEN_FLESH || it.getType() == Material.BONE || it.getType() == Material.COAL);
        Player k = v.getKiller();
        String kindName = v.getPersistentDataContainer().get(this.kindKey, PersistentDataType.STRING);
        Kind kind = kindName == null ? Kind.STALKER : Kind.valueOf(kindName);
        int shards = 1 + this.rnd.nextInt(kind == Kind.AVENGER ? 8 : kind == Kind.BRUTE ? 4 : 2);
        if (this.surge > 1) {
            shards *= 2;
        }

        e.getDrops().add(this.realm.shard(shards));
        e.setDroppedExp(e.getDroppedExp() + 8);
        if (k == null || !this.realm.on(k)) {
            return;
        }

        this.realm.addCounter("rift_kills", 1);
        Realm.PlayerState st = this.realm.state(k);
        if (kind == Kind.AVENGER) {
            st.vendetta = 0;
            k.sendMessage(Text.mm("<gold>⚔ You slew the Avenger. <gray>The Rift will think twice before hunting you again."));
            this.realm.storyEngine.apply(k, List.of("title:Avenger's Bane", "shards:10"));
            Snd.play(k, Snd.CHALLENGE, 1.0F, 1.0F);
            return;
        }

        st.vendetta++;
        this.realm.markDirty();
        if (st.vendetta == 12) {
            k.sendMessage(Text.mm("<dark_purple><i>The Rift whispers your name. Something is following your trail..."));
        } else if (st.vendetta >= 18) {
            st.vendetta = 0;
            Location at = this.spot(k.getLocation(), 12, 20);
            if (at != null) {
                this.spawn(Kind.AVENGER, at, k);
                k.showTitle(net.kyori.adventure.title.Title.title(Text.mm("<color:#FF0054><b>YOU ARE HUNTED"), Text.mm("<gray>A Rift Avenger has found you")));
                Snd.play(k, "entity.wither.ambient", 1.0F, 0.8F);
            }
        }
    }

    void clearAll() {
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity e : w.getLivingEntities()) {
                if (e.getPersistentDataContainer().has(this.realm.riftKey) && !e.getPersistentDataContainer().has(RealmEvents.bossKey(this.realm))) {
                    e.remove();
                }
            }
        }
    }
}
