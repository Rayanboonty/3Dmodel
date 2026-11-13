package net.vortexrealm.hook;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class Sentinel implements Listener, TabExecutor {
    private static final int HISTORY = 30;
    private final VortexRealm plugin;
    private final Map<UUID, Sentinel.Data> data = new ConcurrentHashMap<>();
    private final Set<UUID> alertsOff = new HashSet<>();
    private long tick;
    private static Field connField;
    private static Field netField;
    private static Field chanField;

    public Sentinel(VortexRealm plugin) {
        this.plugin = plugin;
    }

    private Sentinel.Data d(Player p) {
        return this.data.computeIfAbsent(p.getUniqueId(), k -> new Sentinel.Data());
    }

    private static boolean enabled() {
        return Cfg.bool(Opt.SENTINEL_ENABLED);
    }

    public void tick() {
        this.tick++;

        for (Player p : Bukkit.getOnlinePlayers()) {
            Sentinel.Data dt = this.d(p);
            dt.boxes.addFirst(p.getBoundingBox());

            while (dt.boxes.size() > 30) {
                dt.boxes.removeLast();
            }

            if (dt.pendingAttackTick >= 0L && this.tick - dt.pendingAttackTick > 4L) {
                if (!dt.swungSinceAttack && this.fair(p)) {
                    this.flag(p, "NoSwing", 1.0, "hit without swinging");
                }

                dt.pendingAttackTick = -1L;
            }

            if (dt.kb != null && this.tick - dt.kbTick > (long)Math.ceil(p.getPing() / 50.0) + 7L) {
                this.judgeKnockback(p, dt);
            }

            if (dt.timerFlags > 0L) {
                long n = dt.timerFlags;
                dt.timerFlags = 0L;
                if (this.fair(p)) {
                    this.flag(p, "Timer", Math.min(3L, n), "moving faster than 20 updates/s");
                }
            }

            if (this.tick % 20L == 0L) {
                dt.vl.replaceAll((k, v) -> Math.max(0.0, v - 0.08));
                dt.vl.values().removeIf(v -> v <= 0.0);
            }
        }
    }

    private boolean fair(Player p) {
        if (!enabled() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
            return false;
        } else {
            return p.getPing() <= Cfg.i(Opt.SENTINEL_MAX_PING) && !(Bukkit.getTPS()[0] < 17.5)
                ? p.getVehicle() == null && !p.isFlying() && !p.isGliding()
                : false;
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player a && e.getEntity() instanceof LivingEntity target && e.getCause() == DamageCause.ENTITY_ATTACK) {
            Sentinel.Data da = this.d(a);
            da.pendingAttackTick = this.tick;
            da.swungSinceAttack = false;
            if (this.fair(a)) {
                if (a.isHandRaised() && a.getActiveItem() != null && !a.getActiveItem().isEmpty()) {
                    this.flag(a, "UseHit", 1.0, "hit while using " + a.getActiveItem().getType().getKey().getKey());
                }

                if (da.lastHitTick == this.tick && da.lastTarget != null && !da.lastTarget.equals(target.getUniqueId())) {
                    this.flag(a, "MultiAura", 1.5, "two targets in one tick");
                }

                da.lastHitTick = this.tick;
                da.lastTarget = target.getUniqueId();
                List<BoundingBox> seen = new ArrayList<>();
                if (target instanceof Player tp) {
                    Sentinel.Data dt = this.d(tp);
                    int back = (int)Math.ceil(a.getPing() / 50.0) + 3;
                    int i = 0;

                    for (BoundingBox b : dt.boxes) {
                        if (i++ > back) {
                            break;
                        }

                        seen.add(b);
                    }
                }

                if (seen.isEmpty()) {
                    seen.add(target.getBoundingBox());
                }

                Location eye = a.getEyeLocation();
                Vector from = eye.toVector();
                double range = 3.0;
                AttributeInstance r = a.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
                if (r != null) {
                    range = r.getValue();
                }

                double best = Double.MAX_VALUE;
                boolean onTarget = false;
                Vector dir = eye.getDirection();

                for (BoundingBox b : seen) {
                    BoundingBox grown = b.clone().expand(0.1);
                    best = Math.min(best, distance(from, grown));
                    RayTraceResult hit = b.clone().expand(0.45).rayTrace(from, dir, range + 3.0);
                    onTarget |= hit != null;
                }

                double limit = range + Cfg.d(Opt.SENTINEL_REACH_TOLERANCE);
                if (best > limit) {
                    this.flag(a, "Reach", Math.min(3.0, 1.0 + (best - limit) * 4.0), String.format(Locale.ROOT, "%.2f blocks", best));
                }

                if (!onTarget && best < range + 1.0) {
                    this.flag(a, "Hitbox", 0.8, "crosshair off the target");
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            this.d(e.getPlayer()).swungSinceAttack = true;
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onVelocity(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        if (p.getLastDamageCause() instanceof EntityDamageByEntityEvent ed && ed.getDamager() instanceof Player) {
            Vector v = e.getVelocity().clone().setY(0);
            if (!(v.lengthSquared() < 0.09)) {
                Sentinel.Data dt = this.d(p);
                dt.kb = v;
                dt.kbTick = this.tick;
                dt.kbMoved = 0.0;
                dt.kbSamples = 0;
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Sentinel.Data dt = this.d(p);
        if (dt.kb != null) {
            long lag = (long)Math.ceil(p.getPing() / 50.0) + 1L;
            long since = this.tick - dt.kbTick;
            if (since >= lag) {
                Vector moved = e.getTo().toVector().subtract(e.getFrom().toVector()).setY(0);
                dt.kbMoved = dt.kbMoved + moved.dot(dt.kb.clone().normalize());
                dt.kbSamples++;
                if (dt.kbSamples >= 4) {
                    this.judgeKnockback(p, dt);
                }
            }
        }
    }

    private void judgeKnockback(Player p, Sentinel.Data dt) {
        double expected = dt.kb.length();
        Vector kbDir = dt.kb.clone().normalize();
        dt.kb = null;
        if (this.fair(p) && !blocked(p, kbDir) && !p.isInWater() && !p.isClimbing() && !inWeb(p)) {
            if (dt.kbMoved < expected * 0.25) {
                this.flag(p, "Velocity", 1.0, String.format(Locale.ROOT, "took %.0f%% knockback", Math.max(0.0, dt.kbMoved / (expected * 1.6)) * 100.0));
            }
        }
    }

    private static boolean blocked(Player p, Vector dir) {
        Location l = p.getLocation();

        for (double step = 0.4; step <= 1.0; step += 0.3) {
            Location at = l.clone().add(dir.clone().multiply(step));
            if (at.getBlock().getType().isSolid() || at.clone().add(0.0, 1.0, 0.0).getBlock().getType().isSolid()) {
                return true;
            }
        }

        return false;
    }

    private static boolean inWeb(Player p) {
        Block b = p.getLocation().getBlock();
        return b.getType() == Material.COBWEB || b.getType() == Material.POWDER_SNOW || b.getType() == Material.SWEET_BERRY_BUSH;
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onTeleport(PlayerTeleportEvent e) {
        Sentinel.Data dt = this.d(e.getPlayer());
        dt.kb = null;
        dt.balance = -1000L;
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Sentinel.Data dt = this.d(p);
        long now = System.currentTimeMillis();
        dt.places.addLast(now);

        while (!dt.places.isEmpty() && now - dt.places.peekFirst() > 1000L) {
            dt.places.removeFirst();
        }

        if (dt.places.size() > Cfg.i(Opt.SENTINEL_MAX_PLACE) && this.fair(p)) {
            this.flag(p, "FastPlace", 1.0, dt.places.size() + " blocks/s");
            dt.places.clear();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Sentinel.Data dt = this.d(p);
        dt.balance = -1000L;
        Tasks.later(20L, () -> {
            if (p.isOnline()) {
                Channel ch = channel(p);
                if (ch != null) {
                    ch.eventLoop().execute(() -> {
                        try {
                            if (ch.pipeline().get("vortex_sentinel") == null && ch.pipeline().get("decoder") != null) {
                                ch.pipeline().addAfter("decoder", "vortex_sentinel", new Sentinel.Counter(dt));
                            }
                        } catch (RuntimeException var4) {
                        }
                    });
                }
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.data.remove(e.getPlayer().getUniqueId());
    }

    private static Channel channel(Player p) {
        try {
            Object handle = p.getClass().getMethod("getHandle").invoke(p);
            if (connField == null) {
                connField = find(handle.getClass(), "connection");
            }

            Object listener = connField.get(handle);
            if (netField == null) {
                netField = find(listener.getClass(), "connection");
            }

            Object conn = netField.get(listener);
            if (chanField == null) {
                chanField = find(conn.getClass(), "channel");
            }

            return (Channel)chanField.get(conn);
        } catch (RuntimeException | LinkageError | ReflectiveOperationException var4) {
            return null;
        }
    }

    private static Field find(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException var4) {
            }
        }

        throw new NoSuchFieldException(name);
    }

    private static double distance(Vector p, BoundingBox b) {
        double dx = Math.max(Math.max(b.getMinX() - p.getX(), 0.0), p.getX() - b.getMaxX());
        double dy = Math.max(Math.max(b.getMinY() - p.getY(), 0.0), p.getY() - b.getMaxY());
        double dz = Math.max(Math.max(b.getMinZ() - p.getZ(), 0.0), p.getZ() - b.getMaxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void flag(Player p, String check, double weight, String info) {
        if (this.plugin.screenShare() == null || !this.plugin.screenShare().frozen(p)) {
            Sentinel.Data dt = this.d(p);
            double v = dt.vl.merge(check, weight, Double::sum);
            double total = 0.0;

            for (double x : dt.vl.values()) {
                total += x;
            }

            this.log(p.getName() + " " + check + " vl=" + String.format(Locale.ROOT, "%.1f", v) + " (" + info + ") ping=" + p.getPing());
            if (v >= 2.0 && System.currentTimeMillis() - dt.lastAlert > 1500L) {
                dt.lastAlert = System.currentTimeMillis();
                Component msg = Text.mm(
                    "<dark_red>[<red>Sentinel</red>]</dark_red> <white>"
                        + Text.esc(p.getName())
                        + " <gray>failed <#FF7A1A>"
                        + check
                        + "</#FF7A1A> <dark_gray>("
                        + Text.esc(info)
                        + ")</dark_gray> <gray>VL <white>"
                        + String.format(Locale.ROOT, "%.0f", v)
                        + " <dark_gray>• <gray>"
                        + p.getPing()
                        + "ms <dark_gray>[<aqua><click:run_command:'/ss "
                        + p.getName()
                        + "'>SS</click></aqua>] [<aqua><click:run_command:'/sentinel info "
                        + p.getName()
                        + "'>INFO</click></aqua>]"
                );

                for (Player s : Bukkit.getOnlinePlayers()) {
                    if (s.hasPermission("vortex.staff") && !this.alertsOff.contains(s.getUniqueId())) {
                        s.sendMessage(msg);
                    }
                }
            }

            int pool = Cfg.i(Opt.SENTINEL_POOL_VL);
            if (pool > 0 && total >= pool && this.plugin.hackerPool() != null && !this.plugin.hackerPool().pooled(p)) {
                this.plugin.hackerPool().add(p, "Sentinel: " + check, "Sentinel", 604800000L);
                dt.vl.clear();
            }
        }
    }

    private void log(String line) {
        Tasks.async(() -> {
            File f = new File(this.plugin.getDataFolder(), "sentinel.log");

            try (FileWriter w = new FileWriter(f, true)) {
                w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()) + " " + line + System.lineSeparator());
            } catch (IOException var8) {
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vortex.staff")) {
            sender.sendMessage(Text.mm("<red>You don't have permission."));
            return true;
        } else if (args.length == 0) {
            sender.sendMessage(
                Text.mm(
                    "<dark_red><b>Sentinel</b> <gray>"
                        + (enabled() ? "<green>on" : "<red>off")
                        + " <dark_gray>• <gray>/sentinel alerts <dark_gray>• <gray>/sentinel info <player>"
                )
            );
            return true;
        } else if (args[0].equalsIgnoreCase("alerts") && sender instanceof Player p) {
            boolean off = !this.alertsOff.remove(p.getUniqueId());
            if (off) {
                this.alertsOff.add(p.getUniqueId());
            }

            p.sendMessage(Text.mm("<gray>Sentinel alerts " + (off ? "<red>off" : "<green>on")));
            return true;
        } else if (args[0].equalsIgnoreCase("info") && args.length > 1) {
            Player t = Bukkit.getPlayerExact(args[1]);
            if (t == null) {
                sender.sendMessage(Text.mm("<red>Player not online."));
                return true;
            } else {
                Sentinel.Data dt = this.d(t);
                sender.sendMessage(
                    Text.mm(
                        "<dark_red><b>Sentinel</b> <white>"
                            + Text.esc(t.getName())
                            + " <dark_gray>• <gray>"
                            + t.getPing()
                            + "ms <dark_gray>• <gray>client <white>"
                            + Text.esc(String.valueOf(t.getClientBrandName()))
                    )
                );
                if (dt.vl.isEmpty()) {
                    sender.sendMessage(Text.mm(" <green>No violations right now."));
                }

                dt.vl
                    .forEach((k, v) -> sender.sendMessage(Text.mm(" <dark_gray>» <#FF7A1A>" + k + " <gray>VL <white>" + String.format(Locale.ROOT, "%.1f", v))));
                return true;
            }
        } else {
            sender.sendMessage(Text.mm("<gray>/sentinel alerts <dark_gray>• <gray>/sentinel info <player>"));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("alerts", "info"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        }

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    private final class Counter extends ChannelInboundHandlerAdapter {
        private final Sentinel.Data dt;

        Counter(Sentinel.Data dt) {
            this.dt = dt;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            String n = msg.getClass().getName();
            if (n.contains("ServerboundMovePlayerPacket") || n.contains("PacketPlayInFlying")) {
                long now = System.nanoTime() / 1000000L;
                if (this.dt.lastPacket > 0L) {
                    long b = this.dt.balance + 50L - (now - this.dt.lastPacket);
                    b = Math.max(-1000L, b);
                    if (b > Cfg.i(Opt.SENTINEL_TIMER_MS)) {
                        this.dt.timerFlags++;
                        b = 0L;
                    }

                    this.dt.balance = b;
                }

                this.dt.lastPacket = now;
            }

            ctx.fireChannelRead(msg);
        }
    }

    private static final class Data {
        final Deque<BoundingBox> boxes = new ArrayDeque<>();
        final Map<String, Double> vl = new ConcurrentHashMap<>();
        long lastAlert;
        long pendingAttackTick = -1L;
        boolean swungSinceAttack = true;
        long lastHitTick = -1L;
        UUID lastTarget;
        Vector kb;
        long kbTick;
        double kbMoved;
        int kbSamples;
        final Deque<Long> places = new ArrayDeque<>();
        volatile long balance;
        volatile long lastPacket;
        volatile long timerFlags;
    }
}
