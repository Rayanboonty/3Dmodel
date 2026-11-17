package net.vortexpvp.plus;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Training drills on private lanes of the add-on ground, each with its own leaderboard: CPS test, aim trainer
 * (moving target), W-tap trainer (sprint resets on a knockback dummy), pearl landing, crystal speed and mace timing.
 */
public final class Drills implements Listener {
    public static final int LANES = 15;
    private final VortexPvP plugin;
    private final PlusModule plus;
    private final Map<UUID, Run> runs = new HashMap<>();
    private final Random random = new Random();

    public enum Drill {
        CPS("CPS Test", Material.STONE_BUTTON, true, "Click as fast as you can for 10 seconds.", "CPS"),
        AIM("Aim Trainer", Material.TARGET, true, "Hit the moving dummy as often as you can in 30 seconds.", "hits"),
        WTAP("W-Tap Trainer", Material.LEATHER_BOOTS, true, "20 hits on a dummy: reset your sprint (W-tap) before each one.", "%"),
        PEARL("Pearl Landing", Material.ENDER_PEARL, true, "5 pearls: land them on the target pads. 5 points for a bullseye.", "pts"),
        CRYSTAL("Crystal Speed", Material.END_CRYSTAL, false, "Break 10 crystals as fast as you can.", "s"),
        MACE("Mace Timing", Material.MACE, true, "5 launches: smash the dummy while falling. Higher fall = more points.", "pts");

        public final String display;
        public final Material icon;
        public final boolean higherBetter;
        public final String help;
        public final String unit;

        Drill(String display, Material icon, boolean higherBetter, String help, String unit) {
            this.display = display;
            this.icon = icon;
            this.higherBetter = higherBetter;
            this.help = help;
            this.unit = unit;
        }
    }

    private final class Run {
        final Player p;
        final Drill d;
        final Location c;
        final List<Entity> ents = new ArrayList<>();
        int ticks;
        int countdown = 60;
        boolean going;
        boolean done;
        int count;
        int good;
        double score;
        int round;
        int roundTicks;
        Mannequin target;
        Location pad;
        EnderPearl pearl;
        EnderCrystal crystal;
        boolean launched;

        Run(Player p, Drill d, Location c) {
            this.p = p;
            this.d = d;
            this.c = c;
        }
    }

    public Drills(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
    }

    public boolean inDrill(UUID u) {
        return this.runs.containsKey(u);
    }

    // ------------------------------------------------------------------ lanes

    private Location lane(int i) {
        Location c = Grounds.plot(this.plugin, this.plus, i % 5, 2 + i / 5);
        String key = "lane." + i;
        if (this.plus.store().doc("ground").getInt(key, 0) != 1) {
            World w = c.getWorld();
            int x = c.getBlockX();
            int y = c.getBlockY();
            int z = c.getBlockZ();
            Grounds.box(w, x - 14, y - 2, z - 14, x + 14, y + 6, z + 14, Material.AIR);
            Grounds.box(w, x - 12, y - 1, z - 12, x + 12, y - 1, z + 12, Material.SMOOTH_STONE);
            Grounds.walls(w, x, z, 12, y - 1, y - 1, Material.POLISHED_BLACKSTONE);
            for (int dx = -12; dx <= 12; dx += 6) {
                for (int dz = -12; dz <= 12; dz += 6) {
                    if (Math.abs(dx) == 12 || Math.abs(dz) == 12) {
                        w.getBlockAt(x + dx, y, z + dz).setType(Material.SEA_LANTERN, false);
                    }
                }
            }

            this.plus.store().doc("ground").set(key, 1);
            this.plus.store().dirty("ground");
        }

        return c;
    }

    private int freeLane() {
        boolean[] used = new boolean[LANES];
        for (Run r : this.runs.values()) {
            for (int i = 0; i < LANES; i++) {
                if (r.c.getBlockX() == Grounds.plot(this.plugin, this.plus, i % 5, 2 + i / 5).getBlockX()
                    && r.c.getBlockZ() == Grounds.plot(this.plugin, this.plus, i % 5, 2 + i / 5).getBlockZ()) {
                    used[i] = true;
                }
            }
        }

        for (int i = 0; i < LANES; i++) {
            if (!used[i]) {
                return i;
            }
        }

        return -1;
    }

    // ------------------------------------------------------------------ menu and start

    public void menu(Player p) {
        PlayerData d = this.plus.store().get(p);
        Gui.open(this.plugin, p, "<dark_gray>Training Drills", 4, g -> {
            int[] slots = {10, 11, 12, 14, 15, 16};
            int i = 0;
            for (Drill dr : Drill.values()) {
                Double best = d.drillBest.get(dr.name());
                List<String> top = this.top(dr);
                g.item(slots[i++], Items.of(dr.icon).name("<yellow><b>" + dr.display).lore("<gray>" + dr.help, "",
                        "<gray>Your best: <white>" + (best == null ? "-" : fmt(best) + " " + dr.unit),
                        "<gray>Server best: <gold>" + (top.isEmpty() ? "-" : top.get(0).split("\\|")[1] + " (" + top.get(0).split("\\|")[2] + ")"),
                        "", "<green>▶ Left-click to start", "<aqua>▶ Right-click for the top 10")
                    .hideAll().build(), click -> {
                        if (click.isRightClick()) {
                            p.closeInventory();
                            this.showTop(p, dr);
                        } else {
                            p.closeInventory();
                            this.start(p, dr);
                        }
                    });
            }
        });
    }

    static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long)v) : String.format(Locale.ROOT, "%.1f", v);
    }

    public void start(Player p, Drill d) {
        if (!this.plus.config().on("drills")) {
            p.sendMessage(Text.mm("<red>Drills are switched off."));
            return;
        }

        if (this.plugin.sessions().state(p) != PlayerState.LOBBY) {
            p.sendMessage(Text.mm("<red>Start a drill from the lobby."));
            return;
        }

        int lane = this.freeLane();
        if (lane < 0) {
            p.sendMessage(Text.mm("<red>All training lanes are busy, try again in a moment."));
            return;
        }

        if (this.plus.arcade() != null) {
            this.plus.arcade().leaveQueue(p);
        }

        Location c = this.lane(lane);
        Run r = new Run(p, d, c);
        this.runs.put(p.getUniqueId(), r);
        this.plugin.sessions().get(p).state = PlayerState.BOT;
        p.getInventory().clear();
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        Location at = c.clone().add(0.0, 0.0, 4.0);
        at.setYaw(180.0F);
        Tp.to(p, at);
        p.showTitle(Title.title(Text.mm("<yellow><b>" + d.display), Text.mm("<gray>" + d.help)));
    }

    public void stop(Player p, boolean quiet) {
        Run r = this.runs.remove(p.getUniqueId());
        if (r == null) {
            return;
        }

        this.clear(r);
        if (p.isOnline()) {
            p.setFallDistance(0.0F);
            this.plugin.lobby().sendToLobby(p);
            if (!quiet) {
                p.sendMessage(Text.mm("<gray>Drill stopped."));
            }
        }
    }

    public void stopAll() {
        for (Run r : new ArrayList<>(this.runs.values())) {
            this.stop(r.p, true);
        }
    }

    private void clear(Run r) {
        for (Entity e : r.ents) {
            e.remove();
        }

        r.ents.clear();
        if (r.pad != null) {
            Grounds.box(r.pad.getWorld(), r.pad.getBlockX() - 1, r.pad.getBlockY(), r.pad.getBlockZ() - 1, r.pad.getBlockX() + 1, r.pad.getBlockY(), r.pad.getBlockZ() + 1, Material.AIR);
            r.pad = null;
        }
    }

    // ------------------------------------------------------------------ running

    /** Every tick. */
    public void tick() {
        for (Run r : new ArrayList<>(this.runs.values())) {
            Player p = r.p;
            if (!p.isOnline()) {
                this.runs.remove(p.getUniqueId());
                this.clear(r);
                continue;
            }

            if (r.done) {
                continue;
            }

            if (!r.going) {
                if (--r.countdown % 20 == 0 && r.countdown > 0) {
                    p.showTitle(Title.title(Text.mm("<yellow><b>" + r.countdown / 20), Text.mm("<gray>" + r.d.display),
                        Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(900), java.time.Duration.ZERO)));
                    Snd.play(p, "block.note_block.hat", 1.0F, 1.0F);
                }

                if (r.countdown <= 0) {
                    r.going = true;
                    p.showTitle(Title.title(Text.mm("<green><b>GO!"), net.kyori.adventure.text.Component.empty()));
                    Snd.play(p, "entity.player.levelup", 1.0F, 1.8F);
                    this.begin(r);
                }

                continue;
            }

            r.ticks++;
            r.roundTicks++;
            switch (r.d) {
                case CPS -> {
                    p.sendActionBar(Text.mm("<gray>Clicks: <white>" + r.count + " <dark_gray>• <gray>" + fmt(Math.max(0, 200 - r.ticks) / 20.0) + "s left"));
                    if (r.ticks >= 200) {
                        this.end(r, r.count / 10.0);
                    }
                }
                case AIM -> {
                    if (r.roundTicks >= 16) {
                        this.moveTarget(r);
                    }

                    p.sendActionBar(Text.mm("<gray>Hits: <white>" + r.count + " <dark_gray>• <gray>" + Math.max(0, (600 - r.ticks) / 20) + "s left"));
                    if (r.ticks >= 600) {
                        this.end(r, r.count);
                    }
                }
                case WTAP -> {
                    if (r.target != null && r.target.getLocation().distanceSquared(r.c) > 64.0) {
                        r.target.teleport(r.c.clone());
                        r.target.setVelocity(new Vector());
                    }

                    p.sendActionBar(Text.mm("<gray>Hits: <white>" + r.count + "/20 <dark_gray>• <green>W-taps: " + r.good));
                    if (r.ticks > 2400) {
                        this.end(r, r.count == 0 ? 0 : r.good * 100.0 / Math.max(20, r.count));
                    }
                }
                case PEARL -> {
                    if (r.pearl != null && (!r.pearl.isValid() || r.pearl.getLocation().getY() < r.c.getY() - 25)) {
                        r.pearl = null;
                        this.pearlResult(r, -1.0);
                    } else if (r.roundTicks > 160 && r.pearl == null) {
                        this.pearlResult(r, -1.0);
                    }
                }
                case CRYSTAL -> {
                    p.sendActionBar(Text.mm("<gray>Crystals: <white>" + r.count + "/10 <dark_gray>• <white>" + fmt(r.ticks / 20.0) + "s"));
                    if (r.ticks > 1200) {
                        this.end(r, 60.0);
                    }
                }
                case MACE -> {
                    if (!r.launched && r.roundTicks > 20) {
                        r.launched = true;
                        r.roundTicks = 0;
                        p.setVelocity(new Vector(0.0, 1.6 + r.round * 0.15, 0.0));
                    } else if (r.launched && r.roundTicks > 15 && ((org.bukkit.entity.Entity)p).isOnGround()) {
                        this.maceRound(r, 0.0);
                    }

                    p.sendActionBar(Text.mm("<gray>Launch <white>" + (r.round + 1) + "/5 <dark_gray>• <gray>Points: <gold>" + fmt(r.score)));
                }
            }
        }
    }

    private void begin(Run r) {
        Player p = r.p;
        switch (r.d) {
            case AIM -> {
                r.target = this.dummy(r, r.c.clone().add(0.0, 0.0, -3.0), false);
                this.moveTarget(r);
            }
            case WTAP -> {
                p.getInventory().setItem(0, Items.of(Material.DIAMOND_SWORD).unbreakable().build());
                r.target = this.dummy(r, r.c.clone().add(0.0, 0.0, -1.0), true);
            }
            case PEARL -> this.pearlRound(r);
            case CRYSTAL -> this.nextCrystal(r);
            case MACE -> {
                p.getInventory().setItem(0, Items.of(Material.MACE).unbreakable().build());
                r.target = this.dummy(r, r.c.clone().add(0.0, 0.0, 2.6), false);
                r.target.setGravity(true);
                r.roundTicks = 0;
            }
            default -> {
            }
        }
    }

    private Mannequin dummy(Run r, Location at, boolean pushable) {
        Location l = at.clone();
        l.setYaw(0.0F);
        Mannequin m = l.getWorld().spawn(l, Mannequin.class, x -> {
            x.setPersistent(false);
            x.setVisibleByDefault(false);
            x.setSilent(true);
            x.setGravity(pushable);
            x.setImmovable(!pushable);
            x.setProfile(ResolvableProfile.resolvableProfile().name("Steve").build());
            x.customName(Text.mm("<yellow>Training Dummy"));
            x.setCustomNameVisible(true);
            try {
                x.setDescription(null);
            } catch (RuntimeException var3) {
            }
        });
        r.p.showEntity(this.plugin, m);
        r.ents.add(m);
        return m;
    }

    private void moveTarget(Run r) {
        r.roundTicks = 0;
        if (r.target == null) {
            return;
        }

        double a = this.random.nextDouble() * Math.PI - Math.PI;
        double dist = 3.0 + this.random.nextDouble() * 2.5;
        Location to = r.c.clone().add(Math.sin(a) * dist, this.random.nextInt(3) * 0.6, -Math.abs(Math.cos(a)) * dist);
        to.setDirection(r.p.getLocation().toVector().subtract(to.toVector()));
        r.target.teleport(to);
        r.p.spawnParticle(Particle.CLOUD, to.clone().add(0.0, 1.0, 0.0), 6, 0.2, 0.4, 0.2, 0.01);
    }

    private void pearlRound(Run r) {
        if (r.pad != null) {
            Grounds.box(r.pad.getWorld(), r.pad.getBlockX() - 1, r.pad.getBlockY(), r.pad.getBlockZ() - 1, r.pad.getBlockX() + 1, r.pad.getBlockY(), r.pad.getBlockZ() + 1, Material.AIR);
        }

        if (r.round >= 5) {
            this.end(r, r.score);
            return;
        }

        double a = (this.random.nextDouble() - 0.5) * 1.6;
        double dist = 14.0 + this.random.nextDouble() * 14.0;
        Location pad = r.c.clone().add(Math.sin(a) * dist, -1 + this.random.nextInt(3) - 1, -Math.cos(a) * dist - 4);
        r.pad = pad.getBlock().getLocation();
        Grounds.box(pad.getWorld(), r.pad.getBlockX() - 1, r.pad.getBlockY(), r.pad.getBlockZ() - 1, r.pad.getBlockX() + 1, r.pad.getBlockY(), r.pad.getBlockZ() + 1, Material.TARGET);
        r.roundTicks = 0;
        r.p.getInventory().setItem(0, new ItemStack(Material.ENDER_PEARL, 1));
        r.p.sendActionBar(Text.mm("<gray>Pearl <white>" + (r.round + 1) + "/5 <dark_gray>• <gray>Points: <gold>" + fmt(r.score) + " <dark_gray>• <gray>Target: <white>" + Math.round(dist) + " blocks"));
    }

    private void pearlResult(Run r, double dist) {
        double pts = dist < 0 ? 0.0 : Math.max(0.0, Math.round((5.0 - dist) * 10.0) / 10.0);
        r.score += pts;
        r.round++;
        r.p.sendMessage(Text.mm(pts > 0 ? "<green>🎯 +" + fmt(pts) + " <gray>(" + fmt(Math.round(dist * 10) / 10.0) + " blocks off)" : "<red>✖ Missed"));
        Tasks.later(10L, () -> {
            if (this.runs.get(r.p.getUniqueId()) == r && !r.done) {
                this.pearlRound(r);
            }
        });
    }

    private void nextCrystal(Run r) {
        if (r.crystal != null) {
            r.crystal.remove();
        }

        if (r.count >= 10) {
            this.end(r, Math.round(r.ticks / 2.0) / 10.0);
            return;
        }

        double a = this.random.nextDouble() * Math.PI * 2;
        double d = 2.0 + this.random.nextDouble() * 2.0;
        Location at = r.p.getLocation().clone();
        at.setY(r.c.getY());
        at.add(Math.cos(a) * d, 0.0, Math.sin(a) * d);
        r.crystal = at.getWorld().spawn(at, EnderCrystal.class, cr -> {
            cr.setPersistent(false);
            cr.setShowingBottom(true);
            cr.setVisibleByDefault(false);
        });
        r.p.showEntity(this.plugin, r.crystal);
        r.ents.add(r.crystal);
    }

    private void maceRound(Run r, double pts) {
        r.score += pts;
        r.round++;
        r.launched = false;
        r.roundTicks = 0;
        r.p.setFallDistance(0.0F);
        if (pts > 0) {
            r.p.sendMessage(Text.mm("<gold>💥 Smash! <white>+" + fmt(pts) + " <gray>points"));
        } else {
            r.p.sendMessage(Text.mm("<red>✖ Missed the dummy"));
        }

        if (r.round >= 5) {
            this.end(r, r.score);
            return;
        }

        Location back = r.c.clone().add(0.0, 0.0, 4.0);
        back.setYaw(180.0F);
        Tp.to(r.p, back);
    }

    private void end(Run r, double score) {
        r.done = true;
        Player p = r.p;
        PlayerData d = this.plus.store().get(p);
        Double best = d.drillBest.get(r.d.name());
        boolean pb = best == null || (r.d.higherBetter ? score > best : score < best);
        if (pb) {
            d.drillBest.put(r.d.name(), score);
            d.dirty = true;
        }

        this.plus.progression().track(p.getUniqueId(), "drill", r.d.name().toLowerCase(Locale.ROOT), 1L);
        this.plus.progression().xp(p.getUniqueId(), 30L, r.d.display);
        if (r.d == Drill.CPS) {
            this.plus.progression().best(p.getUniqueId(), "best_cps", (long)Math.floor(score));
        }

        int place = this.submit(r.d, p, score);
        p.showTitle(Title.title(Text.mm("<gold><b>" + fmt(score) + " " + r.d.unit), Text.mm(pb ? "<green>New personal best!" : "<gray>Best: " + fmt(best) + " " + r.d.unit)));
        p.sendMessage(Text.mm("<yellow>" + r.d.display + ": <white>" + fmt(score) + " " + r.d.unit + (pb ? " <green>(personal best)" : "")
            + (place > 0 ? " <gold>#" + place + " on the server" : "") + " <click:run_command:'/drill " + r.d.name().toLowerCase(Locale.ROOT) + "'><aqua>[again]</aqua></click>"));
        Snd.play(p, pb ? "ui.toast.challenge_complete" : "entity.experience_orb.pickup", 1.0F, 1.0F);
        Tasks.later(40L, () -> {
            if (this.runs.get(p.getUniqueId()) == r) {
                this.stop(p, true);
            }
        });
    }

    // ------------------------------------------------------------------ leaderboards

    private List<String> top(Drill d) {
        return new ArrayList<>(this.plus.store().doc("drills").getStringList(d.name()));
    }

    /** Puts a score on the top 10 (one entry per player); returns the place, or 0. */
    int submit(Drill d, Player p, double score) {
        YamlConfiguration y = this.plus.store().doc("drills");
        List<String> list = this.top(d);
        list.removeIf(s -> s.startsWith(p.getUniqueId() + "|") && (d.higherBetter ? Double.parseDouble(s.split("\\|")[2]) <= score : Double.parseDouble(s.split("\\|")[2]) >= score));
        boolean has = list.stream().anyMatch(s -> s.startsWith(p.getUniqueId() + "|"));
        if (!has) {
            list.add(p.getUniqueId() + "|" + p.getName() + "|" + fmt(score));
        }

        list.sort((a, b) -> {
            double x = Double.parseDouble(a.split("\\|")[2]);
            double z = Double.parseDouble(b.split("\\|")[2]);
            return d.higherBetter ? Double.compare(z, x) : Double.compare(x, z);
        });
        while (list.size() > 10) {
            list.remove(list.size() - 1);
        }

        y.set(d.name(), list);
        this.plus.store().dirty("drills");
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).startsWith(p.getUniqueId() + "|")) {
                return i + 1;
            }
        }

        return 0;
    }

    public void showTop(Player p, Drill d) {
        List<String> list = this.top(d);
        p.sendMessage(Text.mm("<gold><b>" + d.display + " - top 10"));
        if (list.isEmpty()) {
            p.sendMessage(Text.mm("<gray>Nobody yet - be the first!"));
        }

        for (int i = 0; i < list.size(); i++) {
            String[] s = list.get(i).split("\\|");
            p.sendMessage(Text.mm((i == 0 ? "<gold>" : i < 3 ? "<yellow>" : "<gray>") + "#" + (i + 1) + " <white>" + Text.esc(s[1]) + " <dark_gray>- <white>" + s[2] + " " + d.unit));
        }
    }

    // ------------------------------------------------------------------ events

    @EventHandler(ignoreCancelled = false)
    public void onSwing(PlayerAnimationEvent e) {
        Run r = this.runs.get(e.getPlayer().getUniqueId());
        if (r != null && r.going && !r.done && r.d == Drill.CPS && e.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            r.count++;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHitEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) {
            return;
        }

        Run r = this.runs.get(p.getUniqueId());
        if (r == null) {
            if (r == null && e.getEntity() instanceof Mannequin m && this.owned(m)) {
                e.setCancelled(true);
            }

            return;
        }

        e.setCancelled(true);
        if (!r.going || r.done) {
            return;
        }

        Entity hit = e.getEntity();
        switch (r.d) {
            case AIM -> {
                if (hit == r.target) {
                    r.count++;
                    r.target.playHurtAnimation(0.0F);
                    Snd.play(p, "entity.arrow.hit_player", 0.6F, 1.6F);
                    this.moveTarget(r);
                }
            }
            case WTAP -> {
                if (hit == r.target) {
                    r.count++;
                    boolean tap = this.plus.analysis().freshSprint(p);
                    if (tap) {
                        r.good++;
                    }

                    r.target.playHurtAnimation(0.0F);
                    Vector kb = r.target.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (kb.lengthSquared() < 0.01) {
                        kb = p.getLocation().getDirection().setY(0);
                    }

                    r.target.setVelocity(kb.normalize().multiply(tap ? 0.9 : 0.45).setY(0.36));
                    p.sendActionBar(Text.mm(tap ? "<green>✔ W-tap!" : "<red>✖ No sprint reset"));
                    if (r.count >= 20) {
                        this.end(r, r.good * 5.0);
                    }
                }
            }
            case CRYSTAL -> {
                if (hit == r.crystal) {
                    r.count++;
                    hit.getWorld().spawnParticle(Particle.EXPLOSION, hit.getLocation(), 1);
                    Snd.play(p, "entity.generic.explode", 0.4F, 1.6F);
                    this.nextCrystal(r);
                }
            }
            case MACE -> {
                if (hit == r.target && r.launched) {
                    float fall = p.getFallDistance();
                    if (fall >= 1.5F) {
                        r.target.playHurtAnimation(0.0F);
                        hit.getWorld().spawnParticle(Particle.EXPLOSION, hit.getLocation().add(0.0, 1.0, 0.0), 2);
                        Snd.play(p, "item.mace.smash_ground_heavy", 1.0F, 1.0F);
                        this.maceRound(r, Math.round(fall * 10.0F) / 10.0);
                    }
                }
            }
            default -> {
            }
        }
    }

    private boolean owned(Entity e) {
        for (Run r : this.runs.values()) {
            if (r.ents.contains(e)) {
                return true;
            }
        }

        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && this.runs.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Run r = this.runs.get(p.getUniqueId());
                Tp.to(p, r.c.clone().add(0.0, 0.0, 4.0));
            }
        } else if (!(e instanceof EntityDamageByEntityEvent) && (e.getEntity() instanceof Mannequin || e.getEntity() instanceof EnderCrystal) && this.owned(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent e) {
        if (e.getEntity() instanceof EnderCrystal && this.owned(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPearlHit(ProjectileHitEvent e) {
        if (e.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player p) {
            Run r = this.runs.get(p.getUniqueId());
            if (r != null && r.d == Drill.PEARL && r.pad != null && !r.done) {
                r.pearl = null;
                Location hit = pearl.getLocation();
                Location center = r.pad.clone().add(0.5, 1.0, 0.5);
                double dist = Math.hypot(hit.getX() - center.getX(), hit.getZ() - center.getZ());
                boolean onPad = Math.abs(hit.getY() - center.getY()) < 2.5;
                this.pearlResult(r, onPad ? dist : -1.0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPearlLaunch(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player p) {
            Run r = this.runs.get(p.getUniqueId());
            if (r != null) {
                r.pearl = pearl;
                r.roundTicks = 0;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPearlTp(PlayerTeleportEvent e) {
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && this.runs.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (this.runs.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Run r = this.runs.remove(e.getPlayer().getUniqueId());
        if (r != null) {
            this.clear(r);
            this.plugin.sessions().get(e.getPlayer()).state = PlayerState.LOBBY;
        }
    }

    public String selfCheck() {
        if (!fmt(12.0).equals("12") || !fmt(12.34).equals("12.3")) {
            return "format";
        }

        return Drill.values().length == 6 ? null : "drill count";
    }
}
