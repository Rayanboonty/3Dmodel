package net.practicepvp.cosmetics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.cosmetics.CosmeticModels.Kind;
import net.practicepvp.cosmetics.CosmeticModels.Model;
import net.practicepvp.session.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Pets that follow you, a pet on your shoulder and wings on your back - one of each per player, chosen in /cosmetics
 * and unlocked by time played. Shown in the lobby, the hub and the SMP (and in fights when Settings allow it). All
 * built from block displays (no resource pack needed, no hitboxes, nothing for other plugins to trip on).
 *
 * <p>Chubby pets walk behind you with a little waddle, wag their tail and look at you; when you run off they roll after
 * you like a ball, they roll out when you summon them, and now and then they do a roll or a hop with hearts.
 */
public final class Companions implements Listener {
    private final PracticePlugin plugin;
    private final Map<UUID, Worn> worn = new HashMap<>();
    private BukkitTask task;
    private long tick;

    /** What one player has out. */
    private static final class Worn {
        CosmeticRig pet;
        CosmeticRig shoulder;
        CosmeticRig wings;
        /** 3.13.3: the invisible little animal at the pet's collar the lead is tied to. */
        org.bukkit.entity.Chicken lead;
        /** No riding the player before this time (ms): after a portal or a world change. */
        long noMountUntil;
        /** When the player was first seen logged in (AuthMe), ms. */
        long authedSince;
        /** The player's own wings and shoulder pet are hidden from them. */
        boolean selfHidden;
        /** Where the player was at the last update (are they walking?). */
        double lastX;
        double lastZ;
        // pet state
        Vector pos;
        float yaw;
        float roll;
        float phase;
        boolean rolling;
        int idleTicks;
        int trick;
        int trickTicks;
        int nextTrick = 300;
        float headYaw;
        long born;
    }

    public Companions(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int every = Math.max(1, Cfg.i(Opt.COSMETIC_UPDATE_TICKS));
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::update, 20L, every);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
        }

        for (Worn w : this.worn.values()) {
            clear(w);
        }

        this.worn.clear();
    }

    private static void clear(Worn w) {
        if (w.pet != null) {
            w.pet.remove();
            w.pet = null;
        }

        if (w.shoulder != null) {
            w.shoulder.remove();
            w.shoulder = null;
        }

        if (w.wings != null) {
            w.wings.remove();
            w.wings = null;
        }

        if (w.lead != null) {
            w.lead.setLeashHolder(null);
            w.lead.remove();
            w.lead = null;
        }
    }

    /** Hours played on this server (from the server's own statistics). */
    public static double hoursPlayed(Player p) {
        return p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600.0;
    }

    public boolean unlocked(Player p, Model m) {
        return m.hours() <= 0 || p.hasPermission("practice.cosmetics.all") || hoursPlayed(p) >= m.hours();
    }

    /** Removes a player's models so they are made again (after choosing another one). */
    public void refresh(Player p) {
        Worn w = this.worn.remove(p.getUniqueId());
        if (w != null) {
            clear(w);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.refresh(e.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        this.refresh(e.getPlayer());
    }

    /** Where cosmetics may show for this player right now. */
    private boolean allowedHere(Player p, boolean pet) {
        if (p.getGameMode() == GameMode.SPECTATOR || p.isDead() || p.hasPotionEffect(PotionEffectType.INVISIBILITY) || p.isInvisible()) {
            return false;
        }

        if (this.plugin.worlds() != null && this.plugin.worlds().managed(p.getWorld())) {
            PlayerState s = this.plugin.sessions().get(p).state;
            if (s == null || !s.inLobbyArea()) {
                return !pet && Cfg.bool(Opt.COSMETICS_IN_FIGHTS);
            }
        }

        return true;
    }

    // ------------------------------------------------------------------ every update

    private void update() {
        this.tick++;
        if (!Cfg.bool(Opt.COSMETICS_ENABLED)) {
            for (Worn w : this.worn.values()) {
                clear(w);
            }

            this.worn.clear();
            return;
        }

        int every = Math.max(1, Cfg.i(Opt.COSMETIC_UPDATE_TICKS));
        for (Player p : Bukkit.getOnlinePlayers()) {
            Cosmetics.Data d = this.plugin.cosmetics() == null ? null : this.plugin.cosmetics().of(p.getUniqueId());
            Model petM = d == null ? null : CosmeticModels.get(d.extra.get("pet"));
            Model shM = d == null ? null : CosmeticModels.get(d.extra.get("shoulder"));
            Model wingM = d == null ? null : CosmeticModels.get(d.extra.get("wings"));
            Worn w = this.worn.get(p.getUniqueId());
            if (petM == null && shM == null && wingM == null) {
                if (w != null) {
                    clear(w);
                    this.worn.remove(p.getUniqueId());
                }

                continue;
            }

            if (w == null) {
                w = new Worn();
                w.born = this.tick;
                this.worn.put(p.getUniqueId(), w);
            }

            boolean hide = "hidden".equals(this.plugin.profiles().get(p).get(net.practicepvp.data.Setting.OWN_COSMETICS));
            if (hide != w.selfHidden && (w.wings != null || w.shoulder != null)) {
                if (w.wings != null) {
                    w.wings.remove();
                    w.wings = null;
                }

                if (w.shoulder != null) {
                    w.shoulder.remove();
                    w.shoulder = null;
                }

                w.selfHidden = hide;
            }

            try {
                this.pet(p, w, petM != null && this.unlocked(p, petM) && this.allowedHere(p, true) ? petM : null, every);
                this.shoulder(p, w, shM != null && this.unlocked(p, shM) && this.allowedHere(p, false) ? shM : null, every);
                this.wings(p, w, wingM != null && this.unlocked(p, wingM) && this.allowedHere(p, false) && !p.isGliding() && !p.isSwimming() && !p.isSleeping() ? wingM : null, every);
            } catch (RuntimeException ex) {
                // a broken model never stops the others
                clear(w);
            }
        }

        this.worn.keySet().removeIf(u -> {
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) {
                clear(this.worn.get(u));
                return true;
            }

            return false;
        });
    }

    // ------------------------------------------------------------------ wings

    private void wings(Player p, Worn w, Model m, int every) {
        if (m == null) {
            if (w.wings != null) {
                w.wings.remove();
                w.wings = null;
            }

            return;
        }

        if (w.wings != null && (w.wings.model() != m || !w.wings.valid() || w.wings.location().getWorld() != p.getWorld())) {
            w.wings.remove();
            w.wings = null;
        }

        CosmeticRig.Pose pose = new CosmeticRig.Pose();
        pose.yaw = p.getBodyYaw();
        // 3.13.5: wings follow what the player does; membrane wings (bat, dragon) sit lower and move half as much
        boolean membrane = m.id().startsWith("wings_bat") || m.id().startsWith("wings_dragon");
        float y = membrane ? 1.12f : 1.3f;
        pose.offset.set(0, p.isSneaking() ? y - 0.25f : y, p.isSneaking() ? -0.32f : (membrane ? -0.18f : -0.14f));
        double t = this.tick * every / 20.0;
        boolean air = !((org.bukkit.entity.Entity)p).isOnGround() && !p.isInsideVehicle();
        Location now = p.getLocation();
        boolean moving = Math.abs(now.getX() - w.lastX) + Math.abs(now.getZ() - w.lastZ) > 0.03 * every || p.isSprinting();
        w.lastX = now.getX();
        w.lastZ = now.getZ();
        double k = membrane ? 0.5 : 1.0;
        double open;
        double lift;
        if (air) {
            // in the air: real flaps
            open = 30 + 18 * k * Math.sin(t * 9.0);
            lift = 12 * k * Math.sin(t * 9.0 + 0.6);
        } else if (p.isSprinting()) {
            // sprinting: swept back, a small flutter
            open = 6 + 2 * k * Math.sin(t * 6.0);
            lift = -8 * k;
        } else if (moving) {
            // walking: tucked in, swaying with the steps
            open = 12 + 3 * k * Math.sin(t * 5.0);
            lift = 2 * k * Math.sin(t * 5.0);
        } else if (p.isSneaking()) {
            open = 8;
            lift = -4 * k;
        } else {
            // standing: slow breathing
            open = 18 + 3 * k * Math.sin(t * 1.6);
            lift = 1.5 * k * Math.sin(t * 1.6 + 0.6);
        }
        pose.group("wingL", new org.joml.Quaternionf().rotateY((float)Math.toRadians(open)).rotateZ((float)Math.toRadians(lift)));
        pose.group("wingR", new org.joml.Quaternionf().rotateY((float)Math.toRadians(-open)).rotateZ((float)Math.toRadians(-lift)));
        Location at = p.getLocation();
        boolean ride = this.mayRide(p, w);
        if (ride) {
            pose.offset.y -= (float)p.getHeight();
        }

        if (w.wings == null) {
            w.wings = CosmeticRig.spawn(at, m, pose, every, viewRange());
            this.own(p, w, w.wings);
            if (ride) {
                w.wings.mountOn(p);
            }
        } else {
            if (ride) {
                if (!w.wings.mountOn(p)) {
                    pose.offset.y += (float)p.getHeight();
                    w.wings.moveTo(at);
                }
            } else {
                w.wings.dismount();
                w.wings.moveTo(at);
            }

            w.wings.pose(pose);
        }
    }

    // ------------------------------------------------------------------ shoulder pet

    private void shoulder(Player p, Worn w, Model m, int every) {
        if (m == null) {
            if (w.shoulder != null) {
                w.shoulder.remove();
                w.shoulder = null;
            }

            return;
        }

        if (w.shoulder != null && (w.shoulder.model() != m || !w.shoulder.valid() || w.shoulder.location().getWorld() != p.getWorld())) {
            w.shoulder.remove();
            w.shoulder = null;
        }

        CosmeticRig.Pose pose = new CosmeticRig.Pose();
        pose.yaw = p.getBodyYaw();
        pose.offset.set(0.36f, p.isSneaking() ? 1.14f : 1.40f, -0.08f);
        double t = this.tick * every / 20.0;
        pose.lift = (float)(Math.sin(t * 2.4) * 0.35);
        pose.group("tail", CosmeticRig.rotY(Math.sin(t * 5.0) * 22));
        float look = (float)(Math.sin(t * 0.7) * 25 + Math.sin(t * 1.9) * 10);
        pose.group("head", CosmeticRig.rotY(look).rotateX((float)Math.toRadians(Math.sin(t * 1.3) * 8)));
        boolean flutter = !((org.bukkit.entity.Entity)p).isOnGround();
        double flap = flutter ? 35 * Math.sin(t * 22) : 0;
        pose.group("legFL", CosmeticRig.rotZ(flap));
        pose.group("legFR", CosmeticRig.rotZ(-flap));
        Location at = p.getLocation();
        boolean ride = this.mayRide(p, w);
        if (ride) {
            pose.offset.y -= (float)p.getHeight();
        }

        if (w.shoulder == null) {
            w.shoulder = CosmeticRig.spawn(at, m, pose, every, viewRange());
            this.own(p, w, w.shoulder);
            if (ride) {
                w.shoulder.mountOn(p);
            }
        } else {
            if (ride) {
                if (!w.shoulder.mountOn(p)) {
                    pose.offset.y += (float)p.getHeight();
                    w.shoulder.moveTo(at);
                }
            } else {
                w.shoulder.dismount();
                w.shoulder.moveTo(at);
            }

            w.shoulder.pose(pose);
        }
    }

    // ------------------------------------------------------------------ pet that follows

    private void pet(Player p, Worn w, Model m, int every) {
        Cosmetics.Data cd = this.plugin.cosmetics() == null ? null : this.plugin.cosmetics().of(p.getUniqueId());
        m = CosmeticModels.withCollar(m, cd == null ? null : cd.extra.get("collar"));
        boolean leadOn = m != null && cd != null && "on".equals(cd.extra.get("lead"));
        if (!leadOn && w.lead != null) {
            w.lead.setLeashHolder(null);
            w.lead.remove();
            w.lead = null;
        }

        if (m == null) {
            if (w.pet != null) {
                w.pet.remove();
                w.pet = null;
            }

            return;
        }

        if (w.pet != null && (w.pet.model() != m || !w.pet.valid() || w.pet.location().getWorld() != p.getWorld())) {
            w.pet.remove();
            w.pet = null;
        }

        Location pl = p.getLocation();
        double yawRad = Math.toRadians(pl.getYaw());
        // a spot behind you and a bit to the side
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        Vector target = new Vector(pl.getX() - fx * 1.7 + fz * 0.9, pl.getY(), pl.getZ() - fz * 1.7 - fx * 0.9);
        target.setY(groundY(pl.getWorld(), target.getX(), pl.getY(), target.getZ()));
        boolean fresh = w.pet == null;
        if (fresh || w.pos == null || w.pos.distanceSquared(pl.toVector()) > 26 * 26) {
            // summoned (or left far behind): it rolls out from your feet
            w.pos = pl.toVector();
            w.rolling = m.fat();
            w.roll = 0;
        }

        Vector to = target.clone().subtract(w.pos);
        double flat = Math.hypot(to.getX(), to.getZ());
        double step = 0;
        if (flat > 0.55) {
            boolean far = flat > 5.0 || p.isSprinting();
            if (m.fat() && (far || w.rolling && flat > 1.2)) {
                w.rolling = true;
            } else if (flat < 1.2) {
                w.rolling = false;
            }

            double speed = (w.rolling ? 0.32 : (far ? 0.36 : 0.2)) * every;
            step = Math.min(speed, flat - 0.45);
            w.pos.add(new Vector(to.getX() / flat * step, 0, to.getZ() / flat * step));
            float want = (float)Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
            w.yaw = turn(w.yaw, want, 28f * every);
            w.idleTicks = 0;
        } else {
            w.rolling = false;
            w.idleTicks += every;
            Vector look = pl.toVector().subtract(w.pos);
            float want = (float)Math.toDegrees(Math.atan2(-look.getX(), look.getZ()));
            w.yaw = turn(w.yaw, want, 6f * every);
        }

        // follow the ground smoothly
        double dy = target.getY() - w.pos.getY();
        w.pos.setY(w.pos.getY() + Math.max(-0.5, Math.min(0.5, dy)));
        CosmeticRig.Pose pose = new CosmeticRig.Pose();
        pose.yaw = w.yaw;
        double radius = m.height() / 2.0 / 16.0 * m.scale();
        if (w.rolling) {
            w.roll += (float)Math.min(1.1, step / Math.max(0.2, radius));
        } else if (w.roll != 0) {
            // finish the roll to land upright
            float full = (float)(Math.PI * 2);
            float rest = (float)Math.ceil(w.roll / full) * full;
            w.roll = Math.min(rest, w.roll + 0.9f * every / 2f);
            if (w.roll >= rest - 1.0E-3f) {
                w.roll = 0;
            }
        }

        pose.roll = w.roll;
        double t = this.tick * every / 20.0;
        if (step > 0 && !w.rolling) {
            w.phase += (float)(step * 9.0);
            double sw = Math.sin(w.phase) * 30;
            pose.group("legFL", CosmeticRig.rotX(sw));
            pose.group("legBR", CosmeticRig.rotX(sw));
            pose.group("legFR", CosmeticRig.rotX(-sw));
            pose.group("legBL", CosmeticRig.rotX(-sw));
            pose.lift = (float)Math.abs(Math.sin(w.phase)) * 0.9f;
            pose.group("body", CosmeticRig.rotZ(Math.sin(w.phase) * 3));
        }

        // tail wags faster when you are close; the head looks at you
        pose.group("tail", CosmeticRig.rotY(Math.sin(t * (w.idleTicks > 0 ? 9 : 5)) * 24));
        if (!w.rolling) {
            Vector look = pl.toVector().subtract(w.pos);
            float toYou = (float)Math.toDegrees(Math.atan2(-look.getX(), look.getZ()));
            float rel = wrap(toYou - w.yaw);
            w.headYaw += (Math.max(-40, Math.min(40, -rel)) - w.headYaw) * 0.3f;
            pose.group("head", CosmeticRig.rotY(w.headYaw).rotateZ((float)Math.toRadians(Math.sin(t * 0.9) * 6)));
        }

        // tricks while waiting: a roll with hearts (chubby pets) or a happy hop
        if (w.idleTicks > 0 && w.trick == 0 && --w.nextTrick <= 0) {
            w.trick = m.fat() && ThreadLocalRandom.current().nextBoolean() ? 1 : 2;
            w.trickTicks = 0;
            w.nextTrick = (240 + ThreadLocalRandom.current().nextInt(360)) / every;
        }

        if (w.trick != 0) {
            w.trickTicks += every;
            float k = Math.min(1f, w.trickTicks / 18f);
            if (w.trick == 1) {
                pose.roll = (float)(k * Math.PI * 2);
            } else {
                pose.lift = (float)(Math.sin(k * Math.PI) * 5);
            }

            if (w.trickTicks == every || w.trickTicks >= 18 && w.trickTicks < 18 + every) {
                Location h = new Location(pl.getWorld(), w.pos.getX(), w.pos.getY() + m.height() / 16.0 * m.scale() + 0.2, w.pos.getZ());
                pl.getWorld().spawnParticle(Particle.HEART, h, 2, 0.25, 0.1, 0.25, 0);
            }

            if (w.trickTicks >= 18) {
                w.trick = 0;
            }
        }

        if (fresh && w.rolling) {
            pl.getWorld().spawnParticle(Particle.CLOUD, pl.clone().add(0, 0.2, 0), 6, 0.2, 0.05, 0.2, 0.02);
        }

        Location at = new Location(pl.getWorld(), w.pos.getX(), w.pos.getY(), w.pos.getZ());
        if (w.pet == null) {
            w.pet = CosmeticRig.spawn(at, m, pose, every, viewRange());
        } else {
            w.pet.moveTo(at);
            w.pet.pose(pose);
        }

        if (leadOn) {
            this.lead(p, w, m, at, pose);
        }
    }

    // ------------------------------------------------------------------ 3.13.3: the lead

    /** A lead from the player's hand to the pet's collar: tied to an invisible, silent little chicken at the neck. */
    private void lead(Player p, Worn w, Model m, Location at, CosmeticRig.Pose pose) {
        float[] head = m.pivot("head");
        double k = m.scale() / 16.0;
        double yaw = Math.toRadians(pose.yaw);
        double fwd = head[2] * k;
        Location knot = at.clone().add(-Math.sin(yaw) * fwd, head[1] * k + pose.lift * k - 0.35, Math.cos(yaw) * fwd);
        if (w.lead != null && (!w.lead.isValid() || w.lead.getWorld() != p.getWorld())) {
            w.lead.remove();
            w.lead = null;
        }

        if (w.lead == null) {
            w.lead = p.getWorld().spawn(knot, org.bukkit.entity.Chicken.class, c -> {
                c.setPersistent(false);
                c.setRemoveWhenFarAway(false);
                c.setAI(false);
                c.setSilent(true);
                c.setInvulnerable(true);
                c.setInvisible(true);
                c.setGravity(false);
                c.setCollidable(false);
                c.setBaby();
                c.setAgeLock(true);
                c.setEggLayTime(Integer.MAX_VALUE);
                c.getPersistentDataContainer().set(this.leadKey(), org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);
            });
        } else {
            w.lead.teleport(knot);
        }

        if (!w.lead.isLeashed() || w.lead.getLeashHolder() != p) {
            w.lead.setLeashHolder(p);
        }
    }

    private org.bukkit.NamespacedKey leadKey;

    private org.bukkit.NamespacedKey leadKey() {
        if (this.leadKey == null) {
            this.leadKey = new org.bukkit.NamespacedKey(this.plugin, "pet_lead");
        }

        return this.leadKey;
    }

    private boolean isLead(org.bukkit.entity.Entity e) {
        return e instanceof org.bukkit.entity.Chicken && e.getPersistentDataContainer().has(this.leadKey(), org.bukkit.persistence.PersistentDataType.BYTE);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onUnleash(org.bukkit.event.player.PlayerUnleashEntityEvent e) {
        if (this.isLead(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onLeadBreak(org.bukkit.event.entity.EntityUnleashEvent e) {
        if (this.isLead(e.getEntity())) {
            // never drops a lead item; it is tied again on the next update
            e.setDropLeash(false);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onLeadHit(org.bukkit.event.entity.EntityDamageEvent e) {
        if (this.isLead(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onLeadClick(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (this.isLead(e.getRightClicked())) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ 3.13.3: riding the player

    /** Wings and shoulder pets ride the player (stuck to the body) unless that could get in the way of a teleport. */
    private boolean mayRide(Player p, Worn w) {
        if (!Cfg.bool(Opt.COSMETICS_ATTACH) || System.currentTimeMillis() < w.noMountUntil || p.isDead()) {
            return false;
        }

        if (!loggedIn(p)) {
            w.authedSince = 0;
            return false;
        }

        if (w.authedSince == 0) {
            w.authedSince = System.currentTimeMillis();
        }

        // a few seconds after logging in: login plugins move the player back to where they logged out
        return System.currentTimeMillis() - w.authedSince > 4000L;
    }

    private static java.lang.reflect.Method authCheck;
    private static Object authApi;
    private static boolean authLooked;

    /** True when AuthMe is not installed or the player has logged in. */
    private static boolean loggedIn(Player p) {
        if (!authLooked) {
            authLooked = true;
            org.bukkit.plugin.Plugin a = Bukkit.getPluginManager().getPlugin("AuthMe");
            if (a != null && a.isEnabled()) {
                try {
                    Class<?> c = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, a.getClass().getClassLoader());
                    authApi = c.getMethod("getInstance").invoke(null);
                    authCheck = c.getMethod("isAuthenticated", Player.class);
                } catch (Throwable t) {
                    authCheck = null;
                }
            }
        }

        if (authCheck == null) {
            return true;
        }

        try {
            return (Boolean)authCheck.invoke(authApi, p);
        } catch (Throwable t) {
            return true;
        }
    }

    /** Takes the wings and shoulder pet off the player for a few seconds (a teleport to another world, a portal). */
    public void detach(Player p) {
        Worn w = this.worn.get(p.getUniqueId());
        if (w != null) {
            w.noMountUntil = System.currentTimeMillis() + 3000L;
            if (w.wings != null) {
                w.wings.dismount();
            }

            if (w.shoulder != null) {
                w.shoulder.dismount();
            }
        }

        // anything else of ours riding the player (left over) gets off too
        for (org.bukkit.entity.Entity e : p.getPassengers()) {
            if (e instanceof org.bukkit.entity.ItemDisplay) {
                p.removePassenger(e);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onPortal(org.bukkit.event.entity.EntityPortalEnterEvent e) {
        if (e.getEntity() instanceof Player p) {
            this.detach(p);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent e) {
        if (e.getTo() != null && e.getTo().getWorld() != e.getFrom().getWorld()) {
            this.detach(e.getPlayer());
        }
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        this.detach(e.getPlayer());
    }

    /** Hides a player's own wings or shoulder pet from them when they chose that in Settings. */
    private void own(Player p, Worn w, CosmeticRig rig) {
        boolean hide = "hidden".equals(this.plugin.profiles().get(p).get(net.practicepvp.data.Setting.OWN_COSMETICS));
        w.selfHidden = hide;
        if (hide) {
            for (org.bukkit.entity.Entity e : rig.entities()) {
                p.hideEntity(this.plugin, e);
            }
        }
    }

    private static float viewRange() {
        return (float)Math.max(0.2, Math.min(1.0, Cfg.i(Opt.COSMETIC_VIEW_DISTANCE) / 64.0));
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a > 180f) {
            a -= 360f;
        } else if (a < -180f) {
            a += 360f;
        }

        return a;
    }

    private static float turn(float from, float to, float max) {
        float d = wrap(to - from);
        return from + Math.max(-max, Math.min(max, d));
    }

    /** The top of the ground near y at (x, z), looking a few blocks up and down; y itself when nothing is found. */
    private static double groundY(World w, double x, double y, double z) {
        int bx = (int)Math.floor(x);
        int bz = (int)Math.floor(z);
        int by = (int)Math.floor(y);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) {
            return y;
        }

        for (int k = 2; k >= -4; k--) {
            Block b = w.getBlockAt(bx, by + k, bz);
            Block above = b.getRelative(0, 1, 0);
            if (b.getType().isSolid() && !above.getType().isSolid()) {
                return b.getBoundingBox().getMaxY();
            }
        }

        return y;
    }

    // ------------------------------------------------------------------ showcases (statues in the lobby)

    /** A model standing still for show (lobby decorations): pets do their tricks, wings flap slowly. */
    public static CosmeticRig showcase(Location at, Model m, float yaw, long tick) {
        CosmeticRig.Pose pose = showcasePose(m, yaw, tick);
        return CosmeticRig.spawn(at, m, pose, 3, 1.0f);
    }

    public static CosmeticRig.Pose showcasePose(Model m, float yaw, long tick) {
        CosmeticRig.Pose pose = new CosmeticRig.Pose();
        pose.yaw = yaw;
        double t = tick / 20.0;
        if (m.kind() == Kind.WINGS) {
            pose.offset.set(0, 1.3f, -0.14f);
            double open = 26 + 10 * Math.sin(t * 2.6);
            pose.group("wingL", CosmeticRig.rotY(open));
            pose.group("wingR", CosmeticRig.rotY(-open));
            return pose;
        }

        if (m.kind() == Kind.SHOULDER) {
            pose.scale = 1.8f;
        }

        pose.group("tail", CosmeticRig.rotY(Math.sin(t * 6) * 24));
        pose.group("head", CosmeticRig.rotY(Math.sin(t * 0.8) * 20));
        double cycle = t % 6.0;
        if (cycle > 5.0) {
            double k = cycle - 5.0;
            if (m.fat()) {
                pose.roll = (float)(k * Math.PI * 2);
            } else {
                pose.lift = (float)(Math.sin(k * Math.PI) * 5);
            }
        }

        return pose;
    }
}
