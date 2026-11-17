package net.vortexpvp.academy;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.title.Title;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.arena.ArenaInstance;
import net.vortexpvp.bot.DummyType;
import net.vortexpvp.bot.PracticeBot;
import net.vortexpvp.dialog.LobbyDialogs;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Session;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * The Academy (/learn): a course per gamemode, a lesson per tip or skill. A lesson plays like a short film - the
 * player watches through a camera that glides along a smooth path while a demo player acts the move out and the tips
 * appear one by one (titles and a floating panel), then a slow-motion replay of the key moment from close up - and
 * ends with "your turn": the kit, a training dummy and a goal to reach in time. Passing saves the lesson.
 */
public final class Academy implements Listener, TabExecutor {
    private static final int TIP_TICKS = 70;
    private final VortexPvP plugin;
    private final Map<UUID, Academy.Run> runs = new HashMap<>();
    private final Map<UUID, Set<String>> done = new ConcurrentHashMap<>();

    public Academy(VortexPvP plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ progress

    public Set<String> done(UUID u) {
        return this.done.computeIfAbsent(u, k -> ConcurrentHashMap.newKeySet());
    }

    public void load(UUID u) {
        if (this.plugin.db() != null) {
            this.plugin.db().supply(c -> {
                Set<String> s = new HashSet<>();

                try (PreparedStatement ps = c.prepareStatement("SELECT lesson FROM vp_academy WHERE uuid=?")) {
                    ps.setString(1, u.toString());

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            s.add(rs.getString(1));
                        }
                    }
                }

                return s;
            }).thenAccept(s -> this.done(u).addAll(s)).exceptionally(err -> null);
        }
    }

    private void markDone(UUID u, String lesson) {
        if (this.done(u).add(lesson) && this.plugin.db() != null) {
            this.plugin.db().run(c -> {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO vp_academy (uuid, lesson, done_at) VALUES (?,?,?)")) {
                    ps.setString(1, u.toString());
                    ps.setString(2, lesson);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            }).exceptionally(err -> null);
        }
    }

    // ------------------------------------------------------------------ a running lesson

    enum Phase {
        FILM,
        REPLAY,
        PRACTICE
    }

    static final class Run {
        final Player player;
        final Courses.Course course;
        final Courses.Lesson lesson;
        final Kit kit;
        final ArenaInstance inst;
        final Location stage;
        final Vector fwd;
        Academy.Phase phase = Academy.Phase.FILM;
        int t;
        ArmorStand camera;
        Mannequin actor;
        Mannequin target;
        TextDisplay panel;
        PracticeBot dummy;
        final List<Entity> extra = new ArrayList<>();
        final List<Block> placed = new ArrayList<>();
        int progress;
        int practiceEnd;
        boolean over;

        Run(Player player, Courses.Course course, Courses.Lesson lesson, Kit kit, ArenaInstance inst, Location stage, Vector fwd) {
            this.player = player;
            this.course = course;
            this.lesson = lesson;
            this.kit = kit;
            this.inst = inst;
            this.stage = stage;
            this.fwd = fwd;
        }

        int filmLength() {
            return Math.max(160, this.lesson.tips().size() * TIP_TICKS + 30);
        }
    }

    public boolean busy(Player p) {
        return this.runs.containsKey(p.getUniqueId());
    }

    public void start(Player p, Courses.Lesson lesson) {
        Session s = this.plugin.sessions().get(p);
        if (s.state != PlayerState.LOBBY) {
            p.sendMessage(Text.mm("<red>Lessons start from the lobby."));
            return;
        }

        Courses.Course course = Courses.courseOf(lesson);
        Kit kit = this.plugin.kits().get(course.kit());
        if (kit == null) {
            kit = this.plugin.kits().all().stream().filter(k -> k.enabled).findFirst().orElse(null);
        }

        ArenaInstance inst = kit == null ? null : this.plugin.arenas().acquireAny(kit, p);
        if (inst == null) {
            for (net.vortexpvp.arena.Arena a : this.plugin.arenas().all()) {
                if (inst == null && a.ready() && a.freeInstances() > 0L && !net.vortexpvp.arena.Arena.poolOf(a.theme).equals("MOLE")) {
                    inst = this.plugin.arenas().acquire(a, p);
                }
            }
        }

        if (inst == null) {
            p.sendMessage(Text.mm("<red>No free map for the lesson right now - try again in a moment."));
            return;
        }

        Location a = inst.spawn1();
        Location b = inst.spawn2();
        Vector fwd = b.toVector().subtract(a.toVector()).setY(0);
        if (fwd.lengthSquared() < 1.0) {
            fwd = new Vector(1, 0, 0);
        }

        fwd.normalize();
        Location stage = a.clone().add(fwd.clone().multiply(6.0));
        stage.setY(stage.getWorld().getHighestBlockYAt(stage) + 1.0);
        Academy.Run r = new Academy.Run(p, course, lesson, kit, inst, stage, fwd);
        this.runs.put(p.getUniqueId(), r);
        s.state = PlayerState.BOT;
        p.getInventory().clear();
        Tp.to(p, stage.clone().add(fwd.clone().multiply(-6.0)).add(0.0, 3.0, 0.0));
        this.setUp(r);
        p.setGameMode(GameMode.SPECTATOR);
        Tasks.later(2L, () -> {
            if (p.isOnline() && r.camera != null && r.camera.isValid()) {
                p.setSpectatorTarget(r.camera);
            }
        });
        p.showTitle(Title.title(Text.mm(course.color() + "<b>" + Text.esc(lesson.title())), Text.mm("<gray>" + Text.esc(course.name()) + " Academy")));
        Snd.play(p, "ui.toast.in", 1.0F, 1.0F);
    }

    private void setUp(Academy.Run r) {
        World w = r.stage.getWorld();
        Location actorAt = r.stage.clone();
        Location targetAt = r.stage.clone().add(r.fwd.clone().multiply(3.2));
        targetAt.setY(w.getHighestBlockYAt(targetAt) + 1.0);
        actorAt.setDirection(r.fwd);
        targetAt.setDirection(r.fwd.clone().multiply(-1));
        r.actor = this.mannequin(w, actorAt, "<aqua>Coach", r.kit, false);
        r.target = this.mannequin(w, targetAt, "<gray>Opponent", r.kit, r.lesson.demo() == Courses.Demo.SHIELD_BREAK || r.lesson.demo() == Courses.Demo.STUN_SLAM
            || r.lesson.demo() == Courses.Demo.BLOCK);
        r.camera = w.spawn(this.camPoint(r, 0.0), ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setMarker(true);
            a.setGravity(false);
            a.setPersistent(false);
            a.setSilent(true);
        });
        Location panelAt = r.stage.clone().add(r.fwd.clone().multiply(1.6)).add(0.0, 2.8, 0.0);
        r.panel = w.spawn(panelAt, TextDisplay.class, t -> {
            t.setBillboard(Billboard.CENTER);
            t.setPersistent(false);
            t.setLineWidth(220);
            t.setBackgroundColor(Color.fromARGB(170, 8, 10, 20));
            t.text(Text.mm(r.course.color() + "<b>" + Text.esc(r.lesson.title())));
        });
    }

    private Mannequin mannequin(World w, Location at, String name, Kit kit, boolean shield) {
        return w.spawn(at, Mannequin.class, m -> {
            m.setPersistent(false);
            m.customName(Text.mm(name));
            m.setCustomNameVisible(true);
            m.setInvulnerable(true);
            ItemStack[] c = kit.contents;
            m.getEquipment().setHelmet(c[39] == null ? null : c[39].clone());
            m.getEquipment().setChestplate(c[38] == null ? null : c[38].clone());
            m.getEquipment().setLeggings(c[37] == null ? null : c[37].clone());
            m.getEquipment().setBoots(c[36] == null ? null : c[36].clone());
            if (shield) {
                m.getEquipment().setItemInOffHand(new ItemStack(Material.SHIELD));
            }
        });
    }

    /** The camera path: a slow arc around the two players, closer during the replay (s from 0 to 1). */
    private Location camPoint(Academy.Run r, double s) {
        Location mid = r.stage.clone().add(r.fwd.clone().multiply(1.6));
        Vector side = r.fwd.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        double ang = Math.toRadians(-70.0 + 140.0 * s);
        double radius = r.phase == Academy.Phase.REPLAY ? 4.2 : 7.5;
        double height = r.phase == Academy.Phase.REPLAY ? 1.6 : 3.4 - 1.2 * s;
        Vector off = side.clone().multiply(Math.sin(ang) * radius).add(r.fwd.clone().multiply(-Math.cos(ang) * radius)).add(new Vector(0.0, height, 0.0));
        Location cam = mid.clone().add(off);
        Vector look = mid.clone().add(0.0, 1.1, 0.0).toVector().subtract(cam.toVector());
        cam.setDirection(look);
        return cam;
    }

    // ------------------------------------------------------------------ ticking

    public void tick() {
        for (Academy.Run r : new ArrayList<>(this.runs.values())) {
            try {
                this.tick(r);
            } catch (RuntimeException var4) {
                this.plugin.getLogger().log(java.util.logging.Level.WARNING, "Academy lesson failed", (Throwable)var4);
                this.finish(r, false, "error");
            }
        }
    }

    private void tick(Academy.Run r) {
        Player p = r.player;
        if (!p.isOnline() || this.plugin.sessions().get(p).state != PlayerState.BOT) {
            this.finish(r, false, "left");
            return;
        }

        r.t++;
        switch (r.phase) {
            case FILM -> {
                int len = r.filmLength();
                double s = Math.min(1.0, (double)r.t / len);
                this.moveCamera(r, s);
                int tip = r.t / TIP_TICKS;
                if (r.t % TIP_TICKS == 1 && tip < r.lesson.tips().size()) {
                    String text = r.lesson.tips().get(tip);
                    p.showTitle(Title.title(net.kyori.adventure.text.Component.empty(), Text.mm("<white>" + Text.esc(text)),
                        Title.Times.times(java.time.Duration.ofMillis(250), java.time.Duration.ofMillis(TIP_TICKS * 50L - 600), java.time.Duration.ofMillis(300))));
                    r.panel.text(Text.mm(r.course.color() + "<b>" + Text.esc(r.lesson.title()) + "</b>\n<gray>Tip " + (tip + 1) + "/" + r.lesson.tips().size() + "\n<white>" + Text.esc(text)));
                    Snd.play(p, "block.note_block.pling", 0.5F, 1.4F + tip * 0.1F);
                }

                this.demo(r, r.t % 80, 1);
                this.airTick(r.actor);
                p.sendActionBar(Text.mm("<gray>Lesson film <dark_gray>• <white>" + (len - r.t) / 20 + "s <dark_gray>• <gray>/learn skip to practise now"));
                if (r.t >= len) {
                    r.phase = Academy.Phase.REPLAY;
                    r.t = 0;
                    this.resetActors(r);
                    p.showTitle(Title.title(Text.mm("<yellow><b>Slow motion"), Text.mm("<gray>Watch the key moment")));
                }
            }
            case REPLAY -> {
                this.moveCamera(r, Math.min(1.0, r.t / 160.0));
                // the same move at half speed
                if (r.t % 2 == 0) {
                    this.demo(r, (r.t / 2) % 80, 2);
                    this.airTick(r.actor);
                }

                if (r.t % 4 == 0) {
                    r.actor.getWorld().spawnParticle(Particle.END_ROD, r.actor.getLocation().add(0.0, 2.2, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
                }

                if (r.t >= 160) {
                    this.beginPractice(r);
                }
            }
            case PRACTICE -> {
                if (r.dummy != null) {
                    r.dummy.tickExternal();
                    this.readDummy(r);
                }

                int left = Math.max(0, (r.practiceEnd - r.t) / 20);
                p.sendActionBar(Text.mm("<gold>Your turn: <white>" + r.lesson.goal().describe(r.lesson.amount()) + " <dark_gray>• <yellow>" + Math.min(r.progress, r.lesson.amount())
                    + "/" + r.lesson.amount() + " <dark_gray>• <gray>" + left + "s"));
                if (r.progress >= r.lesson.amount()) {
                    this.finish(r, true, "passed");
                } else if (r.t >= r.practiceEnd) {
                    this.finish(r, false, "time");
                }
            }
        }
    }

    private void moveCamera(Academy.Run r, double s) {
        // eased so the camera starts and stops softly
        double e = s * s * (3.0 - 2.0 * s);
        Location c = this.camPoint(r, e);
        r.camera.teleport(c);
        if (r.player.getSpectatorTarget() != r.camera && r.player.getGameMode() == GameMode.SPECTATOR) {
            r.player.setSpectatorTarget(r.camera);
        }
    }

    private void resetActors(Academy.Run r) {
        Location a = r.stage.clone();
        a.setDirection(r.fwd);
        r.actor.teleport(a);
        r.actor.setGliding(false);
        for (Block b : r.placed) {
            b.setType(Material.AIR, false);
        }

        r.placed.clear();
    }

    /** One frame (0..79) of the demo; {@code slow} 2 = replay at half speed. */
    private void demo(Academy.Run r, int f, int slow) {
        Mannequin a = r.actor;
        Mannequin t = r.target;
        Location base = r.stage.clone();
        base.setDirection(r.fwd);
        Location tl = t.getLocation();
        a.setRotation(base.getYaw(), 0.0F);
        Vector side = r.fwd.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        switch (r.lesson.demo()) {
            case CRIT, GAPPLE -> {
                this.hand(a, r.kit, "_SWORD");
                if (f == 10) {
                    this.launch(a, 0.0, 0.42);
                } else if (f == 20) {
                    a.swingMainHand();
                    tl.getWorld().spawnParticle(Particle.CRIT, tl.clone().add(0.0, 1.0, 0.0), 20, 0.3, 0.4, 0.3, 0.2);
                    Snd.at(tl, "entity.player.attack.crit", 1.0F, 1.0F);
                    t.playHurtAnimation(0.0F);
                }
            }
            case WTAP, STRAFE, POTS, BOW, AMBUSH -> {
                this.hand(a, r.kit, r.lesson.demo() == Courses.Demo.BOW ? "BOW" : "_SWORD");
                double phase = f / 80.0 * Math.PI * 2.0;
                Vector move = r.lesson.demo() == Courses.Demo.STRAFE ? side.clone().multiply(Math.sin(phase) * 1.2) : r.fwd.clone().multiply(Math.sin(phase) * 0.8);
                Location to = base.clone().add(move);
                to.setY(a.getLocation().getY());
                a.teleport(to);
                if (f % 20 == 5) {
                    a.swingMainHand();
                    t.playHurtAnimation(0.0F);
                    Snd.at(tl, "entity.player.attack.sweep", 0.6F, 1.0F);
                }

                if (r.lesson.demo() == Courses.Demo.POTS && f == 40) {
                    a.getWorld().spawnParticle(Particle.EFFECT, a.getLocation().add(0, 1, 0), 40, 0.6, 0.6, 0.6, 1.0, null);
                    Snd.at(a.getLocation(), "entity.splash_potion.break", 1.0F, 1.0F);
                }
            }
            case BLOCK, SHIELD_BREAK, STUN_SLAM -> {
                if (f == 2) {
                    t.startUsingItem(EquipmentSlot.OFF_HAND);
                }

                this.hand(a, r.kit, f < 30 ? "_AXE" : (r.lesson.demo() == Courses.Demo.STUN_SLAM ? "MACE" : "_SWORD"));
                if (r.lesson.demo() == Courses.Demo.STUN_SLAM && f == 8) {
                    this.launch(a, 0.0, 1.0);
                    Snd.at(a.getLocation(), "entity.wind_charge.wind_burst", 0.8F, 1.0F);
                }

                if (f == 24) {
                    a.swingMainHand();
                    t.clearActiveItem();
                    Snd.at(tl, "item.shield.break", 1.0F, 1.0F);
                } else if (f == 32) {
                    a.swingMainHand();
                    t.playHurtAnimation(0.0F);
                    if (r.lesson.demo() == Courses.Demo.STUN_SLAM) {
                        tl.getWorld().spawnParticle(Particle.DUST_PILLAR, tl, 40, 1.0, 0.1, 1.0, 0.1, Material.STONE.createBlockData());
                        Snd.at(tl, "item.mace.smash_ground_heavy", 1.0F, 1.0F);
                    }
                }
            }
            case WIND_SMASH, DENSITY -> {
                this.hand(a, r.kit, "MACE");
                if (f == 5) {
                    this.launch(a, 0.0, 1.25);
                    a.getWorld().spawnParticle(Particle.GUST_EMITTER_SMALL, a.getLocation(), 1);
                    Snd.at(a.getLocation(), "entity.wind_charge.wind_burst", 0.8F, 1.0F);
                } else if (f == 34) {
                    a.swingMainHand();
                    t.playHurtAnimation(0.0F);
                    tl.getWorld().spawnParticle(Particle.DUST_PILLAR, tl, 40, 1.0, 0.1, 1.0, 0.1, Material.STONE.createBlockData());
                    Snd.at(tl, "item.mace.smash_ground_heavy", 1.0F, 1.0F);
                } else if (f == 60) {
                    Location back = base.clone();
                    back.setY(a.getLocation().getY());
                    a.teleport(back);
                }
            }
            case ELYTRA_DIVE -> {
                this.hand(a, r.kit, "MACE");
                double k = f / 80.0;
                Location sky = base.clone().add(side.clone().multiply(Math.cos(k * Math.PI * 2.0) * 5.0)).add(0.0, f < 60 ? 8.0 + Math.sin(k * Math.PI) * 6.0 : Math.max(0.0, (75 - f) * 0.9), 0.0);
                a.setGravity(false);
                a.teleport(sky);
                a.setGliding(f < 74);
                if (f == 74) {
                    a.swingMainHand();
                    t.playHurtAnimation(0.0F);
                    Snd.at(tl, "item.mace.smash_ground_heavy", 1.0F, 1.0F);
                }

                if (f == 79) {
                    a.setGravity(true);
                }
            }
            case OBSIDIAN, CRYSTAL, ANCHOR, TOTEM -> {
                Block baseBlock = tl.clone().add(r.fwd.clone().multiply(-1.0)).getBlock().getRelative(0, -1, 0);
                if (f == 10) {
                    if (r.lesson.demo() == Courses.Demo.ANCHOR) {
                        Block an = baseBlock.getRelative(0, 1, 0);
                        an.setType(Material.RESPAWN_ANCHOR, false);
                        r.placed.add(an);
                    } else {
                        baseBlock.setType(Material.OBSIDIAN, false);
                        r.placed.add(baseBlock);
                    }

                    a.swingMainHand();
                    Snd.at(baseBlock.getLocation(), "block.stone.place", 1.0F, 1.0F);
                } else if (f == 22 && r.lesson.demo() != Courses.Demo.OBSIDIAN) {
                    if (r.lesson.demo() == Courses.Demo.ANCHOR) {
                        Snd.at(baseBlock.getLocation(), "block.respawn_anchor.charge", 1.0F, 1.0F);
                    } else {
                        EnderCrystal c = baseBlock.getWorld().spawn(baseBlock.getLocation().add(0.5, 1.0, 0.5), EnderCrystal.class, e -> {
                            e.setShowingBottom(false);
                            e.setPersistent(false);
                        });
                        r.extra.add(c);
                    }

                    a.swingMainHand();
                } else if (f == 22 + 6 * slow && r.lesson.demo() != Courses.Demo.OBSIDIAN) {
                    for (Entity e : r.extra) {
                        e.remove();
                    }

                    r.extra.clear();
                    Location boom = baseBlock.getLocation().add(0.5, 1.0, 0.5);
                    boom.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boom, 1);
                    Snd.at(boom, "entity.generic.explode", 1.0F, 1.0F);
                    t.playHurtAnimation(0.0F);
                    if (r.lesson.demo() == Courses.Demo.TOTEM || r.lesson.demo() == Courses.Demo.CRYSTAL) {
                        t.playEffect(org.bukkit.EntityEffect.PROTECTED_FROM_DEATH);
                    }

                    for (Block b : r.placed) {
                        if (b.getType() == Material.RESPAWN_ANCHOR) {
                            b.setType(Material.AIR, false);
                        }
                    }
                }
            }
            case TUNNEL, SONAR -> {
                this.hand(a, r.kit, "_PICKAXE");
                if (f % 10 == 0) {
                    a.swingMainHand();
                    Block b = a.getLocation().add(r.fwd.clone().multiply(1.0 + f / 20)).getBlock();
                    a.getWorld().spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, Material.STONE.createBlockData());
                    Snd.at(b.getLocation(), "block.stone.break", 0.8F, 1.0F);
                }

                if (r.lesson.demo() == Courses.Demo.SONAR && f == 40) {
                    r.player.sendActionBar(Text.mm("<#C9A27E>⛏ Sonar: <white>~35 blocks north-east, 8 below"));
                    Snd.play(r.player, "block.note_block.chime", 0.8F, 0.6F);
                }
            }
        }
    }

    /** Vertical speed of each demo actor in the air (Mannequins don't move by themselves). */
    private final Map<UUID, double[]> air = new HashMap<>();

    private void launch(Mannequin m, double unused, double vy) {
        this.air.put(m.getUniqueId(), new double[]{vy, m.getLocation().getY()});
    }

    /** Moves airborne actors one tick along their jump and lands them where they started. */
    private void airTick(Mannequin m) {
        double[] st = this.air.get(m.getUniqueId());
        if (st != null) {
            Location l = m.getLocation();
            double y = l.getY() + st[0];
            st[0] = (st[0] - 0.08) * 0.98;
            if (y <= st[1]) {
                y = st[1];
                this.air.remove(m.getUniqueId());
            }

            l.setY(y);
            m.teleport(l);
        }
    }

    private void hand(Mannequin m, Kit kit, String suffix) {
        ItemStack cur = m.getEquipment().getItemInMainHand();
        if (cur != null && cur.getType().name().endsWith(suffix)) {
            return;
        }

        for (ItemStack it : kit.contents) {
            if (it != null && it.getType().name().endsWith(suffix)) {
                m.getEquipment().setItemInMainHand(it.clone());
                return;
            }
        }

        Material fallback = switch (suffix) {
            case "_AXE" -> Material.DIAMOND_AXE;
            case "MACE" -> Material.MACE;
            case "BOW" -> Material.BOW;
            case "_PICKAXE" -> Material.NETHERITE_PICKAXE;
            default -> Material.DIAMOND_SWORD;
        };
        m.getEquipment().setItemInMainHand(new ItemStack(fallback));
    }

    private void beginPractice(Academy.Run r) {
        Player p = r.player;
        r.phase = Academy.Phase.PRACTICE;
        r.t = 0;
        r.practiceEnd = r.lesson.seconds() * 20;
        p.setSpectatorTarget(null);
        p.setGameMode(GameMode.SURVIVAL);
        Location stand = r.stage.clone();
        stand.setDirection(r.fwd);
        Tp.to(p, stand);
        r.actor.remove();
        r.target.remove();
        r.camera.remove();
        this.plugin.kits().apply(p, r.kit, this.plugin.profiles().get(p).layouts.get(r.kit.id), List.of());
        switch (r.lesson.dummy()) {
            case MACE, ELYTRA -> p.getInventory().addItem(new ItemStack(Material.WIND_CHARGE, 32));
            default -> {
            }
        }

        if (r.lesson.demo() == Courses.Demo.ELYTRA_DIVE) {
            p.getInventory().setChestplate(new ItemStack(Material.ELYTRA));
            p.getInventory().addItem(new ItemStack(Material.FIREWORK_ROCKET, 32));
        }

        if (r.lesson.demo() == Courses.Demo.SHIELD_BREAK || r.lesson.demo() == Courses.Demo.STUN_SLAM) {
            p.getInventory().addItem(new ItemStack(Material.DIAMOND_AXE));
        }

        if (r.lesson.goal() == Courses.Goal.POTS) {
            p.getInventory().addItem(net.vortexpvp.util.Items.of(Material.SPLASH_POTION).potion(org.bukkit.potion.PotionType.STRONG_HEALING).build().asQuantity(1));
        }

        if (r.lesson.goal() == Courses.Goal.ARROWS) {
            p.getInventory().addItem(new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 32));
        }

        if (r.lesson.goal() != Courses.Goal.BLOCKS) {
            Location at = r.stage.clone().add(r.fwd.clone().multiply(4.0));
            at.setY(at.getWorld().getHighestBlockYAt(at) + 1.0);
            r.dummy = this.plugin.bots().spawnDummy(at, r.kit, r.lesson.dummy(), p);
        } else {
            p.getInventory().addItem(new ItemStack(Material.NETHERITE_PICKAXE), new ItemStack(Material.DIAMOND_SHOVEL));
        }

        r.panel.teleport(r.stage.clone().add(r.fwd.clone().multiply(2.0)).add(0.0, 3.2, 0.0));
        r.panel.text(Text.mm("<gold><b>Your turn!</b>\n<white>" + Text.esc(r.lesson.goal().describe(r.lesson.amount())) + "\n<gray>" + r.lesson.seconds() + " seconds"));
        p.showTitle(Title.title(Text.mm("<gold><b>Your turn!"), Text.mm("<white>" + Text.esc(r.lesson.goal().describe(r.lesson.amount())))));
        Snd.play(p, "entity.player.levelup", 0.7F, 1.2F);
    }

    /** Practice progress read from the dummy's stats. */
    private void readDummy(Academy.Run r) {
        PracticeBot d = r.dummy;
        switch (r.lesson.goal()) {
            case HITS -> r.progress = d.hitsTaken();
            case COMBO -> r.progress = d.bestCombo();
            case SHIELD_BREAKS -> r.progress = d.shieldBreaks();
            case SMASH -> r.progress = (int)Math.floor(d.bestSmash());
            case POPS -> r.progress = d.pops();
            case CRYSTAL_DAMAGE -> r.progress = (int)Math.floor(d.damageTaken());
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ events for the goals

    private Academy.Run runOf(Player p) {
        Academy.Run r = this.runs.get(p.getUniqueId());
        return r != null && r.phase == Academy.Phase.PRACTICE ? r : null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && e.isCritical()) {
            Academy.Run r = this.runOf(p);
            if (r != null && r.lesson.goal() == Courses.Goal.CRITS && r.dummy != null && e.getEntity().equals(r.dummy.body())) {
                r.progress++;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArrow(ProjectileHitEvent e) {
        if (e.getEntity().getShooter() instanceof Player p && e.getHitEntity() != null) {
            Academy.Run r = this.runOf(p);
            if (r != null && r.lesson.goal() == Courses.Goal.ARROWS && r.dummy != null && e.getHitEntity().equals(r.dummy.body())) {
                r.progress++;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPot(PotionSplashEvent e) {
        if (e.getEntity().getShooter() instanceof Player p) {
            Academy.Run r = this.runOf(p);
            if (r != null && r.lesson.goal() == Courses.Goal.POTS) {
                r.progress++;
                Tasks.later(10L, () -> {
                    if (p.isOnline() && this.runs.get(p.getUniqueId()) == r) {
                        p.getInventory().addItem(net.vortexpvp.util.Items.of(Material.SPLASH_POTION).potion(org.bukkit.potion.PotionType.STRONG_HEALING).build());
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDig(BlockBreakEvent e) {
        Academy.Run r = this.runOf(e.getPlayer());
        if (r != null && r.lesson.goal() == Courses.Goal.BLOCKS) {
            r.progress++;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && this.runs.containsKey(p.getUniqueId())) {
            Academy.Run r = this.runs.get(p.getUniqueId());
            if (r.phase != Academy.Phase.PRACTICE) {
                e.setCancelled(true);
            } else if (p.getHealth() - e.getFinalDamage() <= 1.0 || e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                e.setCancelled(true);
                p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
                if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                    Tp.to(p, r.stage);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Academy.Run r = this.runs.get(e.getPlayer().getUniqueId());
        if (r != null) {
            this.finish(r, false, "left");
        }
    }

    // ------------------------------------------------------------------ ending

    private void finish(Academy.Run r, boolean passed, String why) {
        if (r.over) {
            return;
        }

        r.over = true;
        this.runs.remove(r.player.getUniqueId());
        for (Entity e : new Entity[]{r.camera, r.actor, r.target, r.panel}) {
            if (e != null) {
                e.remove();
            }
        }

        for (Entity e : r.extra) {
            e.remove();
        }

        if (r.dummy != null) {
            this.plugin.bots().despawn(r.dummy);
        }

        this.plugin.arenas().release(r.inst);
        Player p = r.player;
        if (p.isOnline()) {
            if (p.getGameMode() == GameMode.SPECTATOR) {
                p.setSpectatorTarget(null);
            }

            if (passed) {
                this.markDone(p.getUniqueId(), r.lesson.id());
                p.showTitle(Title.title(Text.mm("<green><b>✔ Lesson passed"), Text.mm("<white>" + Text.esc(r.lesson.title()))));
                Snd.play(p, "ui.toast.challenge_complete", 0.7F, 1.0F);
                Courses.Lesson next = this.next(r.course, r.lesson);
                p.sendMessage(Text.mm("<green>✔ <white>" + Text.esc(r.lesson.title()) + "</white> passed!"
                    + (next == null ? " <gold>Course complete!" : " <click:run_command:'/learn " + next.id() + "'><gold>[Next: " + Text.esc(next.title()) + "]</gold></click>")
                    + " <click:run_command:'/learn'><aqua>[All courses]</aqua></click>"));
            } else if (!why.equals("left")) {
                p.showTitle(Title.title(Text.mm("<red><b>Not yet"), Text.mm("<gray>" + Text.esc(r.lesson.goal().describe(r.lesson.amount())) + " - try again")));
                p.sendMessage(Text.mm("<gray>You reached <white>" + r.progress + "/" + r.lesson.amount() + "</white>. <click:run_command:'/learn " + r.lesson.id() + "'><yellow>[Try again]</yellow></click>"));
            }

            if (this.plugin.sessions().get(p).state == PlayerState.BOT) {
                Tasks.later(passed ? 30L : 1L, () -> {
                    if (p.isOnline() && this.plugin.sessions().get(p).state == PlayerState.BOT && !this.runs.containsKey(p.getUniqueId())) {
                        this.plugin.lobby().sendToLobby(p);
                    }
                });
            }
        }
    }

    private Courses.Lesson next(Courses.Course c, Courses.Lesson l) {
        int i = c.lessons().indexOf(l);
        return i >= 0 && i + 1 < c.lessons().size() ? c.lessons().get(i + 1) : null;
    }

    public void shutdown() {
        for (Academy.Run r : new ArrayList<>(this.runs.values())) {
            this.finish(r, false, "left");
        }
    }

    // ------------------------------------------------------------------ menus

    private LobbyDialogs ui() {
        return this.plugin.dialogs().lobby();
    }

    public void courses(Player p, String search) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        String q = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
        Set<String> mine = this.done(p.getUniqueId());

        for (Courses.Course c : Courses.ALL) {
            boolean match = q.isEmpty() || c.name().toLowerCase(Locale.ROOT).contains(q)
                || c.lessons().stream().anyMatch(l -> l.title().toLowerCase(Locale.ROOT).contains(q));
            if (match) {
                long n = c.lessons().stream().filter(l -> mine.contains(l.id())).count();
                int total = c.lessons().size();
                int bars = (int)Math.round(10.0 * n / total);
                String bar = "<green>" + "▮".repeat(bars) + "<dark_gray>" + "▮".repeat(10 - bars);
                List<net.kyori.adventure.text.Component> hover = new ArrayList<>();
                hover.add(Text.mm(c.color() + "<b>" + c.name()));

                for (Courses.Lesson l : c.lessons()) {
                    hover.add(Text.mm((mine.contains(l.id()) ? "<green>✔ " : "<gray>• ") + "<white>" + Text.esc(l.title())));
                }

                tiles.add(new LobbyDialogs.Tile(new ItemStack(c.icon()), Text.mm(c.color() + "<b>" + c.name()), Text.mm(bar + " <gray>" + n + "/" + total), hover, () -> this.lessons(p, c)));
            }
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Academy", "Learn every gamemode"),
                tiles,
                List.of(Text.mm(q.isEmpty() ? "<gray>Watch the move, then do it yourself." : "<gray>Search: <white>" + Text.esc(q))),
                Text.mm("<gray>Nothing matches \"" + Text.esc(q) + "\"."),
                List.of(this.ui().btn(p, "<yellow>🔍 Search", "<gray>Find a gamemode or a skill", () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Search the Academy"), "Gamemode or skill", "", 24, s -> this.courses(p, s)))),
                1,
                this.ui().close(p)
            );
    }

    public void lessons(Player p, Courses.Course c) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        Set<String> mine = this.done(p.getUniqueId());

        for (Courses.Lesson l : c.lessons()) {
            boolean ok = mine.contains(l.id());
            List<net.kyori.adventure.text.Component> hover = new ArrayList<>();
            hover.add(Text.mm(c.color() + "<b>" + Text.esc(l.title())));

            for (String tip : l.tips()) {
                hover.add(Text.mm("<gray>• " + Text.esc(tip)));
            }

            hover.add(Text.mm("<yellow>Goal: <white>" + Text.esc(l.goal().describe(l.amount())) + " in " + l.seconds() + "s"));
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(ok ? Material.LIME_DYE : Material.BOOK),
                    Text.mm((ok ? "<green>✔ " : "") + "<white><b>" + Text.esc(l.title())),
                    Text.mm("<gray>" + l.tips().size() + " tips <dark_gray>• <gray>" + Text.esc(l.goal().describe(l.amount()))),
                    hover,
                    () -> {
                        p.closeDialog();
                        this.start(p, l);
                    }
                )
            );
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title(c.name() + " Academy", "Pick a lesson"),
                tiles,
                List.of(),
                null,
                List.of(this.ui().btn(p, "<gray>« Courses", null, () -> this.courses(p, null))),
                1,
                this.ui().close(p)
            );
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Academy.Run r = this.runs.get(p.getUniqueId());
        if (args.length > 0 && (args[0].equalsIgnoreCase("skip") || args[0].equalsIgnoreCase("practice")) && r != null && r.phase != Academy.Phase.PRACTICE) {
            this.resetActors(r);
            this.beginPractice(r);
        } else if (args.length > 0 && args[0].equalsIgnoreCase("stop") && r != null) {
            this.finish(r, false, "left");
        } else if (args.length > 0 && Courses.lesson(args[0]) != null) {
            this.start(p, Courses.lesson(args[0]));
        } else if (args.length > 0 && Courses.course(args[0]) != null) {
            this.lessons(p, Courses.course(args[0]));
        } else {
            this.courses(p, args.length > 0 ? String.join(" ", args) : null);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>(List.of("skip", "stop"));

        for (Courses.Course c : Courses.ALL) {
            out.add(c.id());

            for (Courses.Lesson l : c.lessons()) {
                out.add(l.id());
            }
        }

        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(typed));
        return out;
    }
}
