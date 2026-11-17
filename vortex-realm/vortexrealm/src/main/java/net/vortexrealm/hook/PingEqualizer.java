package net.vortexrealm.hook;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.util.Nms;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Ping Equalizer: in a match where both players opted in, the player with the lower ping gets a small, capped
 * outbound delay so both play with a similar round trip. The delay follows the live pings every couple of seconds
 * and is removed the moment the match ends or the player leaves.
 */
public final class PingEqualizer {
    public static final String HANDLER = "vortex_ping_eq";
    private final VortexRealm plugin;
    private final Map<Integer, List<UUID>> matches = new ConcurrentHashMap<>();
    private final Map<UUID, PingEqualizer.DelayHandler> handlers = new ConcurrentHashMap<>();

    public PingEqualizer(VortexRealm plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return Cfg.bool(Opt.PING_EQUALIZER);
    }

    /** Start equalizing the given players of a match. */
    public void start(int matchId, List<Player> players) {
        if (this.enabled() && players.size() >= 2) {
            List<UUID> ids = new ArrayList<>();
            for (Player p : players) {
                ids.add(p.getUniqueId());
                this.attach(p);
            }

            this.matches.put(matchId, ids);
            this.rebalance(ids);
            for (Player p : players) {
                p.sendMessage(Text.mm("<aqua>⚖ Ping Equalizer <gray>is on for this match."));
            }
        }
    }

    public void stop(int matchId) {
        List<UUID> ids = this.matches.remove(matchId);
        if (ids != null) {
            for (UUID u : ids) {
                this.detach(u);
            }
        }
    }

    public boolean active(int matchId) {
        return this.matches.containsKey(matchId);
    }

    public int addedDelay(Player p) {
        PingEqualizer.DelayHandler h = this.handlers.get(p.getUniqueId());
        return h == null ? 0 : (int)TimeUnit.NANOSECONDS.toMillis(h.delayNanos);
    }

    /** Called every 40 ticks. */
    public void tick() {
        for (List<UUID> ids : this.matches.values()) {
            this.rebalance(ids);
        }
    }

    public void handleQuit(Player p) {
        this.handlers.remove(p.getUniqueId());
    }

    public void shutdown() {
        for (Integer id : new ArrayList<>(this.matches.keySet())) {
            this.stop(id);
        }
    }

    private void rebalance(List<UUID> ids) {
        int cap = Math.max(0, Cfg.i(Opt.PING_EQUALIZER_MAX));
        int highest = 0;
        Map<UUID, Integer> base = new java.util.HashMap<>();
        for (UUID u : ids) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                int b = Math.max(0, p.getPing() - this.addedDelay(p));
                base.put(u, b);
                highest = Math.max(highest, b);
            }
        }

        for (Map.Entry<UUID, Integer> e : base.entrySet()) {
            PingEqualizer.DelayHandler h = this.handlers.get(e.getKey());
            if (h != null) {
                int want = Math.min(cap, highest - e.getValue());
                int now = (int)TimeUnit.NANOSECONDS.toMillis(h.delayNanos);
                int next = Math.abs(want - now) < 8 ? now : (now + want) / 2;
                if (want == 0) {
                    next = 0;
                }

                h.delayNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, next));
            }
        }
    }

    private void attach(Player p) {
        Channel ch = Nms.channel(p);
        if (ch == null) {
            this.plugin.getLogger().fine("Ping Equalizer: no channel for " + p.getName());
        } else {
            PingEqualizer.DelayHandler h = new PingEqualizer.DelayHandler();
            this.handlers.put(p.getUniqueId(), h);
            ch.eventLoop().execute(() -> {
                try {
                    if (ch.isOpen() && ch.pipeline().get(HANDLER) == null) {
                        ch.pipeline().addFirst(HANDLER, h);
                    }
                } catch (RuntimeException var3) {
                    this.plugin.getLogger().log(Level.WARNING, "Ping Equalizer could not attach to " + p.getName(), (Throwable)var3);
                }
            });
        }
    }

    private void detach(UUID u) {
        PingEqualizer.DelayHandler h = this.handlers.remove(u);
        Player p = Bukkit.getPlayer(u);
        Channel ch = p == null ? null : Nms.channel(p);
        if (h != null) {
            h.delayNanos = 0L;
        }

        if (ch != null) {
            ch.eventLoop().execute(() -> {
                try {
                    if (ch.pipeline().get(HANDLER) != null) {
                        ch.pipeline().remove(HANDLER);
                    }
                } catch (RuntimeException var2) {
                }
            });
        }
    }

    /** Delays outbound writes by {@link #delayNanos} while keeping their order. Runs on the channel's event loop. */
    static final class DelayHandler extends ChannelOutboundHandlerAdapter {
        volatile long delayNanos;
        private final ArrayDeque<PingEqualizer.Pending> queue = new ArrayDeque<>();
        private ScheduledFuture<?> scheduled;
        private ChannelHandlerContext ctx;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            long d = this.delayNanos;
            if (d <= 0L && this.queue.isEmpty()) {
                ctx.write(msg, promise);
            } else {
                long release = System.nanoTime() + Math.max(0L, d);
                PingEqualizer.Pending last = this.queue.peekLast();
                if (last != null && last.release() > release) {
                    release = last.release();
                }

                this.queue.add(new PingEqualizer.Pending(msg, promise, release));
                this.schedule();
            }
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            if (this.queue.isEmpty()) {
                ctx.flush();
            }
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
            this.drainAll();
            ctx.close(promise);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            if (this.scheduled != null) {
                this.scheduled.cancel(false);
                this.scheduled = null;
            }

            this.drainAll();
        }

        private void schedule() {
            if (this.scheduled == null && !this.queue.isEmpty() && this.ctx != null) {
                long wait = Math.max(0L, this.queue.peek().release() - System.nanoTime());
                this.scheduled = this.ctx.executor().schedule(this::drain, wait, TimeUnit.NANOSECONDS);
            }
        }

        private void drain() {
            this.scheduled = null;
            long now = System.nanoTime();
            boolean wrote = false;

            while (!this.queue.isEmpty() && this.queue.peek().release() <= now) {
                PingEqualizer.Pending p = this.queue.poll();
                this.ctx.write(p.msg(), p.promise());
                wrote = true;
            }

            if (wrote) {
                this.ctx.flush();
            }

            this.schedule();
        }

        private void drainAll() {
            if (this.ctx != null && !this.queue.isEmpty()) {
                while (!this.queue.isEmpty()) {
                    PingEqualizer.Pending p = this.queue.poll();
                    this.ctx.write(p.msg(), p.promise());
                }

                this.ctx.flush();
            }
        }
    }

    record Pending(Object msg, ChannelPromise promise, long release) {
    }
}
