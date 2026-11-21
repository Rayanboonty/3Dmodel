package net.vortexrealm.realm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * World events: Rift Nights (the sky turns violet, the Rift's fighters triple, shards double), falling meteors with
 * loot and guards, the Jester's Carnival (random blessings and shard rain), sieges on the capital in waves, and the
 * finale boss, the Unmaker, with three phases. Rolled automatically; admins can force any of them with /realm event.
 */
public final class RealmEvents implements Listener {
    private static NamespacedKey boss;

    static NamespacedKey bossKey(Realm r) {
        if (boss == null) {
            boss = new NamespacedKey(r.plugin, "rift_boss");
        }

        return boss;
    }

    private final Realm realm;
    private final Random rnd = new Random();
    private boolean riftNight;
    private boolean rolledTonight;
    private long lastMeteor = System.currentTimeMillis();
    private long lastCarnival = System.currentTimeMillis();
    private long lastSiege = System.currentTimeMillis();
    private final BossBar riftBar = BossBar.bossBar(Text.mm("<dark_purple><b>THE RIFT IS OPEN</b> <gray>Fighters x3 • Shards x2"), 1.0F, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
    private final Set<UUID> barShown = new HashSet<>();

    // carnival
    private int carnivalLeft;
    private final Set<UUID> carnivalPlayers = new HashSet<>();

    // siege
    private int siegeWave;
    private int siegeLeftSec;
    private final Set<UUID> siegeMobs = new HashSet<>();
    private final Set<UUID> defenders = new HashSet<>();
    private BossBar siegeBar;

    // finale
    private LivingEntity unmaker;
    private final Map<UUID, Double> damage = new HashMap<>();
    private BossBar bossBar;
    private int phase;
    private int bossTick;

    RealmEvents(Realm realm) {
        this.realm = realm;
    }

    void second() {
        World w = this.realm.overworld();
        if (w == null || !this.realm.on(w)) {
            return;
        }

        long t = w.getTime();
        // ---- rift nights: rolled once at nightfall
        if (t >= 13000 && t < 13200 && !this.rolledTonight) {
            this.rolledTonight = true;
            double[] odds = {0.15, 0.25, 0.30, 0.35, 0.45};
            if (this.rnd.nextDouble() < odds[Math.min(4, this.realm.chapter - 1)]) {
                this.startRiftNight();
            }
        } else if (t < 13000 && t > 200) {
            this.rolledTonight = false;
        }

        if (this.riftNight) {
            if (t < 13000 || t >= 23000) {
                this.endRiftNight();
            } else {
                this.riftFx(w);
            }
        }

        // ---- timed rolls (only with people online in the SMP)
        long now = System.currentTimeMillis();
        int online = this.realm.smpPlayers().size();
        if (online > 0 && this.realm.chapter >= 2 && now - this.lastMeteor > 50 * 60_000L) {
            this.lastMeteor = now;
            if (this.rnd.nextInt(100) < 40) {
                this.meteor(null);
            }
        }

        if (online > 0 && this.realm.chapter >= 2 && now - this.lastCarnival > 80 * 60_000L) {
            this.lastCarnival = now;
            if (this.rnd.nextInt(100) < 30) {
                this.carnival();
            }
        }

        if (online >= 2 && this.realm.chapter == 4 && this.realm.capital != null && this.siegeWave == 0 && now - this.lastSiege > 45 * 60_000L) {
            this.lastSiege = now;
            if (this.rnd.nextInt(100) < 50) {
                this.siege();
            }
        }

        this.carnivalTick();
        this.siegeTick();
        this.bossTick();
    }

    void chapterStarted(int n) {
        if (n == 5) {
            if (this.realm.capital == null) {
                this.realm.plugin.getLogger().warning("Unstable Realm chapter 5: set /realm capital, then /realm event finale");
                return;
            }

            this.realm.broadcast("<dark_red><b>THE GROUND TREMBLES", "<gray>The Unmaker rises at the capital in 10 minutes",
                "<dark_red>☠ <red>The Unmaker rises at the capital in <white>10 minutes<red>. Gather every bearer.", "entity.warden.emerge");
            Tasks.later(20L * 60 * 5, () -> this.realm.broadcast("<dark_red><b>5 MINUTES", "<gray>The Unmaker is almost here", null, "entity.warden.heartbeat"));
            Tasks.later(20L * 60 * 10, this::finale);
        }
    }

    void force(CommandSender s, String what) {
        switch (what) {
            case "riftnight" -> this.startRiftNight();
            case "meteor" -> this.meteor(s instanceof Player p ? p.getLocation() : null);
            case "carnival" -> this.carnival();
            case "siege" -> {
                if (this.realm.capital == null) {
                    s.sendMessage(Text.mm("<red>Set the capital first: /realm capital"));
                    return;
                }

                this.siege();
            }
            case "finale" -> {
                if (this.realm.capital == null) {
                    s.sendMessage(Text.mm("<red>Set the capital first: /realm capital"));
                    return;
                }

                this.finale();
            }
            case "stop" -> this.stopAll();
            default -> {
                s.sendMessage(Text.mm("<red>/realm event riftnight|meteor|carnival|siege|finale|stop"));
                return;
            }
        }

        s.sendMessage(Text.mm("<green>Event: " + what));
    }

    void stopAll() {
        if (this.riftNight) {
            this.endRiftNight();
        }

        this.carnivalLeft = 0;
        this.endSiege(false, true);
        if (this.unmaker != null && this.unmaker.isValid()) {
            this.unmaker.remove();
        }

        this.unmaker = null;
        if (this.bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(this.bossBar);
            }
        }
    }

    private void credit(java.util.Collection<UUID> who, String key, List<String> effects) {
        for (UUID u : who) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && this.realm.on(p)) {
                this.realm.storyEngine.event(p, key);
                this.realm.storyEngine.apply(p, effects);
            }
        }
    }

    // ------------------------------------------------------------------ rift night

    private void startRiftNight() {
        if (this.riftNight) {
            return;
        }

        this.riftNight = true;
        this.realm.fighters.surge = 3.0;
        World w = this.realm.overworld();
        if (w != null && (w.getTime() < 13000 || w.getTime() > 22000)) {
            w.setTime(13500);
        }

        this.realm.broadcast("<dark_purple><b>THE RIFT OPENS", "<light_purple>Survive until dawn",
            "<dark_purple>✦ <light_purple><b>RIFT NIGHT!</b> <gray>The Rift's fighters pour out until dawn. Shards drop double. Stay together.", "entity.wither.spawn");
    }

    private void riftFx(World w) {
        for (Player p : w.getPlayers()) {
            if (this.barShown.add(p.getUniqueId())) {
                p.showBossBar(this.riftBar);
            }

            if (this.rnd.nextInt(3) == 0) {
                Location l = p.getLocation().add(this.rnd.nextInt(21) - 10, 2 + this.rnd.nextInt(6), this.rnd.nextInt(21) - 10);
                p.spawnParticle(Particle.REVERSE_PORTAL, l, 30, 1.5, 1.5, 1.5, 0.02);
            }

            if (this.rnd.nextInt(25) == 0) {
                Snd.play(p, "ambient.soul_sand_valley.mood", 0.7F, 0.7F);
            }
        }
    }

    private void endRiftNight() {
        this.riftNight = false;
        this.realm.fighters.surge = 1.0;
        Set<UUID> survivors = new HashSet<>();
        for (Player p : this.realm.smpPlayers()) {
            survivors.add(p.getUniqueId());
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(this.riftBar);
        }

        this.barShown.clear();
        this.realm.addCounter("rift_nights", 1);
        this.realm.broadcast("<gold><b>DAWN", "<gray>You survived the Rift Night", "<gold>☀ The Rift closes for now. Survivors earn <light_purple>5 shards<gold>.", Snd.LEVELUP);
        this.credit(survivors, "riftnight", List.of("shards:5"));
    }

    // ------------------------------------------------------------------ meteor

    private void meteor(Location near) {
        World w = this.realm.overworld();
        if (w == null) {
            return;
        }

        Location base = near != null ? near : this.realm.capital != null ? this.realm.capital : w.getSpawnLocation();
        double a = this.rnd.nextDouble() * Math.PI * 2;
        double d = near != null ? 25 : 150 + this.rnd.nextInt(250);
        int x = (int)(base.getX() + Math.cos(a) * d);
        int z = (int)(base.getZ() + Math.sin(a) * d);
        this.realm.broadcast("<gold><b>A STAR IS FALLING", "<yellow>Near " + x + ", " + z + " in 60 seconds",
            "<gold>☄ <yellow>A meteor will land near <white>" + x + ", " + z + "<yellow> in 60 seconds. Loot for whoever gets there first... and guards.", "entity.firework_rocket.launch");
        Tasks.later(20L * 50, () -> {
            w.getChunkAt(x >> 4, z >> 4).load();
            Location ground = w.getHighestBlockAt(x, z).getLocation().add(0.5, 1, 0.5);
            int[] step = {0};
            org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
            task[0] = Tasks.timer(0L, 4L, () -> {
                step[0]++;
                Location trail = ground.clone().add(0, 60 - step[0] * 2.4, 0);
                w.spawnParticle(Particle.FLAME, trail, 40, 0.6, 0.6, 0.6, 0.05);
                w.spawnParticle(Particle.LARGE_SMOKE, trail, 20, 0.8, 0.8, 0.8, 0.02);
                if (step[0] >= 25) {
                    task[0].cancel();
                    this.impact(ground);
                }
            });
        });
    }

    private void impact(Location g) {
        World w = g.getWorld();
        w.createExplosion(g, 4.0F, false, false);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, g, 3, 1, 1, 1, 0);
        w.spawnParticle(Particle.LAVA, g, 60, 2, 1, 2, 0);
        w.dropItemNaturally(g, this.realm.shard(10 + this.rnd.nextInt(12)));
        w.dropItemNaturally(g, new ItemStack(Material.DIAMOND, 2 + this.rnd.nextInt(3)));
        if (this.rnd.nextInt(3) == 0) {
            w.dropItemNaturally(g, new ItemStack(Material.NETHERITE_SCRAP, 1));
        }

        for (int i = 0; i < 2 + this.realm.chapter / 2; i++) {
            Location at = this.realm.fighters.spot(g, 3, 8);
            if (at != null) {
                this.realm.fighters.spawn(Fighters.Kind.KNIGHT, at, null).customName(Text.mm("<gold><b>Star-touched Knight"));
            }
        }

        Set<UUID> near = new HashSet<>();
        Tasks.later(20L * 30, () -> {
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(g) < 50 * 50) {
                    near.add(p.getUniqueId());
                }
            }

            this.credit(near, "meteor", List.of());
        });
    }

    // ------------------------------------------------------------------ carnival

    private static final PotionEffectType[] JOY = {PotionEffectType.SPEED, PotionEffectType.JUMP_BOOST, PotionEffectType.REGENERATION,
        PotionEffectType.STRENGTH, PotionEffectType.LUCK, PotionEffectType.HASTE, PotionEffectType.SLOW_FALLING, PotionEffectType.GLOWING, PotionEffectType.LEVITATION};

    private void carnival() {
        if (this.carnivalLeft > 0) {
            return;
        }

        this.carnivalLeft = 300;
        this.carnivalPlayers.clear();
        this.realm.broadcast("<gradient:#FFD23F:#FF3FA4><b>THE JESTER'S CARNIVAL", "<yellow>Five minutes of madness",
            "<gradient:#FFD23F:#FF3FA4><b>🎪 THE CARNIVAL IS IN TOWN!</b></gradient> <gray>Random blessings, shard rain and fireworks for 5 minutes.", "entity.villager.celebrate");
    }

    private void carnivalTick() {
        if (this.carnivalLeft <= 0) {
            return;
        }

        this.carnivalLeft--;
        List<Player> ps = this.realm.smpPlayers();
        for (Player p : ps) {
            this.carnivalPlayers.add(p.getUniqueId());
            if (this.carnivalLeft % 60 == 0) {
                PotionEffectType e = JOY[this.rnd.nextInt(JOY.length)];
                p.addPotionEffect(new PotionEffect(e, e == PotionEffectType.LEVITATION ? 60 : 1200, 0));
                this.firework(p.getLocation().add(this.rnd.nextInt(9) - 4, 1, this.rnd.nextInt(9) - 4));
                Location drop = p.getLocation().add(this.rnd.nextInt(7) - 3, 8, this.rnd.nextInt(7) - 3);
                p.getWorld().dropItem(drop, this.realm.shard(1 + this.rnd.nextInt(2)));
            }
        }

        if (this.carnivalLeft == 0) {
            this.realm.broadcast("<gold>The carnival leaves town", "<gray>...until next time", null, Snd.TWINKLE);
            this.credit(this.carnivalPlayers, "carnival", List.of());
        }
    }

    private void firework(Location l) {
        Firework f = l.getWorld().spawn(l, Firework.class);
        FireworkMeta m = f.getFireworkMeta();
        m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(org.bukkit.Color.FUCHSIA, org.bukkit.Color.YELLOW).withFlicker().build());
        m.setPower(1);
        f.setFireworkMeta(m);
        f.getPersistentDataContainer().set(this.realm.npcKey, PersistentDataType.STRING, "firework");
    }

    @EventHandler(ignoreCancelled = true)
    public void onFireworkHurt(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Firework f && f.getPersistentDataContainer().has(this.realm.npcKey)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ siege

    private void siege() {
        if (this.siegeWave > 0 || this.realm.capital == null) {
            return;
        }

        this.siegeWave = 1;
        this.siegeLeftSec = 12 * 60;
        this.defenders.clear();
        this.siegeBar = BossBar.bossBar(Text.mm("<red><b>SIEGE</b> <gray>Wave 1/3"), 1.0F, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        Location c = this.realm.capital;
        this.realm.broadcast("<red><b>THE CAPITAL IS UNDER SIEGE", "<gray>Defend it: 3 waves, 12 minutes",
            "<red>⚔ <b>SIEGE!</b> <gray>The Rift's army attacks the capital at <white>" + c.getBlockX() + ", " + c.getBlockZ() + "<gray>. Every bearer to the walls!", "event.raid.horn");
        Tasks.later(200L, this::wave);
    }

    private void wave() {
        if (this.siegeWave == 0 || this.realm.capital == null) {
            return;
        }

        Location c = this.realm.capital;
        c.getWorld().getChunkAt(c).load();
        int n = 4 + this.realm.smpPlayers().size() * 2 + this.siegeWave * 2;
        for (int i = 0; i < n; i++) {
            Location at = this.realm.fighters.spot(c, 22, 34);
            if (at == null) {
                continue;
            }

            Fighters.Kind k = this.siegeWave == 3 && i == 0 ? Fighters.Kind.BRUTE : Fighters.Kind.values()[this.rnd.nextInt(4)];
            LivingEntity e = this.realm.fighters.spawn(k, at, null);
            e.setRemoveWhenFarAway(false);
            this.siegeMobs.add(e.getUniqueId());
            if (e instanceof org.bukkit.entity.Mob m) {
                Player nearest = null;
                double best = Double.MAX_VALUE;
                for (Player p : c.getWorld().getPlayers()) {
                    double d = p.getLocation().distanceSquared(c);
                    if (d < best && d < 100 * 100) {
                        best = d;
                        nearest = p;
                    }
                }

                if (nearest != null) {
                    m.setTarget(nearest);
                } else {
                    m.getPathfinder().moveTo(c);
                }
            }
        }

        this.realm.broadcast("<red><b>WAVE " + this.siegeWave + "/3", "<gray>" + this.siegeMobs.size() + " attackers", null, "entity.ravager.roar");
    }

    private void siegeTick() {
        if (this.siegeWave == 0) {
            return;
        }

        this.siegeLeftSec--;
        Location c = this.realm.capital;
        this.siegeMobs.removeIf(u -> {
            Entity e = Bukkit.getEntity(u);
            return e == null || !e.isValid() || e.isDead();
        });
        for (Player p : this.realm.smpPlayers()) {
            boolean near = c != null && p.getWorld().equals(c.getWorld()) && p.getLocation().distanceSquared(c) < 100 * 100;
            if (near) {
                this.defenders.add(p.getUniqueId());
                p.showBossBar(this.siegeBar);
            }
        }

        this.siegeBar.name(Text.mm("<red><b>SIEGE</b> <gray>Wave " + this.siegeWave + "/3 • <white>" + this.siegeMobs.size() + " <gray>attackers • <white>"
            + this.siegeLeftSec / 60 + ":" + String.format("%02d", this.siegeLeftSec % 60)));
        this.siegeBar.progress(Math.max(0F, Math.min(1F, this.siegeLeftSec / (12F * 60))));
        if (this.siegeLeftSec <= 0) {
            this.endSiege(false, false);
        } else if (this.siegeMobs.isEmpty() && this.siegeLeftSec % 3 == 0) {
            if (this.siegeWave >= 3) {
                this.endSiege(true, false);
            } else {
                this.siegeWave++;
                this.wave();
            }
        }
    }

    private void endSiege(boolean won, boolean quiet) {
        if (this.siegeWave == 0) {
            return;
        }

        this.siegeWave = 0;
        for (UUID u : this.siegeMobs) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) {
                e.remove();
            }
        }

        this.siegeMobs.clear();
        if (this.siegeBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(this.siegeBar);
            }
        }

        if (quiet) {
            return;
        }

        if (won) {
            this.realm.broadcast("<green><b>THE CAPITAL HOLDS", "<gray>The siege is broken", "<green>⚔ The siege is broken! Defenders earn <light_purple>15 shards<green>.", Snd.CHALLENGE);
            this.credit(this.defenders, "siege", List.of("shards:15", "xp:300"));
            this.realm.addCounter("sieges_won", 1);
        } else {
            this.realm.broadcast("<dark_red><b>THE WALLS ARE BREACHED", "<gray>The capital is scarred...", "<dark_red>⚔ The siege was lost. The capital will remember.", "entity.wither.death");
            this.realm.setGlobal("capital_scarred", "true");
            this.realm.addCounter("sieges_lost", 1);
        }
    }

    // ------------------------------------------------------------------ finale: the Unmaker

    private void finale() {
        if (this.unmaker != null && this.unmaker.isValid() || this.realm.finished || this.realm.capital == null) {
            return;
        }

        Location c = this.realm.capital.clone().add(0.5, 1, 0.5);
        c.getWorld().getChunkAt(c).load();
        c.getWorld().strikeLightningEffect(c);
        c.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, c, 4, 2, 1, 2, 0);
        WitherSkeleton b = c.getWorld().spawn(c, WitherSkeleton.class, s -> {
            s.customName(Text.mm("<color:#E63946><b>☠ THE UNMAKER ☠"));
            s.setCustomNameVisible(true);
            s.setPersistent(true);
            s.setRemoveWhenFarAway(false);
            s.getPersistentDataContainer().set(this.realm.riftKey, PersistentDataType.BYTE, (byte)1);
            s.getPersistentDataContainer().set(bossKey(this.realm), PersistentDataType.BYTE, (byte)1);
        });
        int ps = Math.max(1, this.realm.smpPlayers().size());
        double hp = Math.min(1024, 350 + ps * 120);
        set(b, Attribute.MAX_HEALTH, hp);
        b.setHealth(hp);
        set(b, Attribute.SCALE, 2.6);
        set(b, Attribute.ATTACK_DAMAGE, 12);
        set(b, Attribute.KNOCKBACK_RESISTANCE, 1.0);
        set(b, Attribute.FOLLOW_RANGE, 64);
        set(b, Attribute.ARMOR, 14);
        b.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        b.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        b.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
        b.getEquipment().setHelmetDropChance(0);
        b.getEquipment().setChestplateDropChance(0);
        b.getEquipment().setItemInMainHandDropChance(0);
        this.unmaker = b;
        this.phase = 1;
        this.damage.clear();
        this.bossBar = BossBar.bossBar(Text.mm("<color:#E63946><b>THE UNMAKER</b>"), 1.0F, BossBar.Color.RED, BossBar.Overlay.NOTCHED_20);
        this.realm.broadcast("<color:#E63946><b>THE UNMAKER", "<gray>has risen at the capital",
            "<color:#E63946>☠ <b>THE UNMAKER HAS RISEN</b></color> <gray>at the capital (<white>" + c.getBlockX() + ", " + c.getBlockZ() + "<gray>). This is the end of the story. Fight!", "entity.wither.spawn");
        for (Player p : this.realm.smpPlayers()) {
            this.realm.storyEngine.play(p, List.of("The Unmaker: Eight keys. Eight fools. I will unmake every one of you."), null);
        }
    }

    private static void set(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) {
            i.setBaseValue(v);
        }
    }

    private void bossTick() {
        LivingEntity b = this.unmaker;
        if (b == null) {
            return;
        }

        if (!b.isValid()) {
            // unloaded with its chunk: pick it up again when it reloads
            Entity again = b.isDead() ? null : Bukkit.getEntity(b.getUniqueId());
            if (again instanceof LivingEntity le && le.isValid()) {
                this.unmaker = le;
            }

            return;
        }

        this.bossTick++;
        double max = b.getAttribute(Attribute.MAX_HEALTH).getValue();
        float pct = (float)(b.getHealth() / max);
        this.bossBar.progress(Math.max(0F, Math.min(1F, pct)));
        for (Player p : b.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(b.getLocation()) < 90 * 90) {
                p.showBossBar(this.bossBar);
            } else {
                p.hideBossBar(this.bossBar);
            }
        }

        if (this.phase == 1 && pct < 0.66F) {
            this.phase = 2;
            this.bossBar.name(Text.mm("<color:#E63946><b>THE UNMAKER</b> <gray>• phase 2: the Crown's knights"));
            this.say("The Unmaker: You think you're winning? Rise, my knights!");
            for (int i = 0; i < 4; i++) {
                Location at = this.realm.fighters.spot(b.getLocation(), 4, 9);
                if (at != null) {
                    this.realm.fighters.spawn(Fighters.Kind.KNIGHT, at, null);
                }
            }

            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                b.getWorld().strikeLightningEffect(b.getLocation().add(Math.cos(a) * 7, 0, Math.sin(a) * 7));
            }
        } else if (this.phase == 2 && pct < 0.33F) {
            this.phase = 3;
            this.bossBar.name(Text.mm("<dark_red><b>THE UNMAKER</b> <gray>• phase 3: unmade"));
            this.bossBar.color(BossBar.Color.PURPLE);
            this.say("The Unmaker: ENOUGH! I will unmake this whole realm!");
            b.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1));
            b.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0));
        }

        // every few seconds: a shockwave at a random nearby player (phase 3 more often)
        if (this.bossTick % (this.phase == 3 ? 5 : 9) == 0) {
            List<Player> near = b.getWorld().getPlayers().stream().filter(p -> p.getLocation().distanceSquared(b.getLocation()) < 22 * 22
                && p.getGameMode() == org.bukkit.GameMode.SURVIVAL).toList();
            if (!near.isEmpty()) {
                Player t = near.get(this.rnd.nextInt(near.size()));
                b.getWorld().spawnParticle(Particle.SONIC_BOOM, t.getLocation().add(0, 1, 0), 1);
                b.getWorld().playSound(t.getLocation(), "entity.warden.sonic_boom", 1.2F, 0.7F);
                t.damage(this.phase == 3 ? 9 : 6, b);
                t.setVelocity(t.getLocation().toVector().subtract(b.getLocation().toVector()).normalize().multiply(1.2).setY(0.6));
            }
        }

        if (this.bossTick % 20 == 0) {
            b.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, b.getLocation().add(0, 2, 0), 60, 1.5, 2, 1.5, 0.02);
        }
    }

    private void say(String line) {
        for (Player p : this.realm.smpPlayers()) {
            this.realm.storyEngine.play(p, List.of(line), null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossHit(EntityDamageByEntityEvent e) {
        if (this.unmaker == null || !e.getEntity().getUniqueId().equals(this.unmaker.getUniqueId())) {
            return;
        }

        Entity d = e.getDamager();
        Player p = d instanceof Player pl ? pl : d instanceof Projectile pr && pr.getShooter() instanceof Player pl ? pl : null;
        if (p != null) {
            this.damage.merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(bossKey(this.realm))) {
            return;
        }

        e.getDrops().clear();
        e.getDrops().add(new ItemStack(Material.NETHER_STAR, 1));
        e.getDrops().add(this.realm.shard(64));
        Location l = e.getEntity().getLocation();
        for (int i = 0; i < 6; i++) {
            Tasks.later(i * 10L, () -> this.firework(l.clone().add(this.rnd.nextInt(11) - 5, 1, this.rnd.nextInt(11) - 5)));
        }

        if (this.bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(this.bossBar);
            }
        }

        this.unmaker = null;
        this.credit(this.damage.keySet(), "boss", List.of());
        this.realm.addCounter("boss_slain", 1);
        Map<UUID, Double> dmg = new HashMap<>(this.damage);
        Tasks.later(100L, () -> this.realm.storyEngine.ending(dmg));
    }
}
