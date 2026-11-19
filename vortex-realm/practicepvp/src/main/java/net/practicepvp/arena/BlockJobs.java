package net.practicepvp.arena;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.util.Tasks;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class BlockJobs {
    private final Plugin plugin;
    private final Deque<BlockJobs.Entry> queue = new ArrayDeque<>();
    private final Deque<BlockJobs.Entry> priority = new ArrayDeque<>();
    private BukkitTask task;
    /** System.nanoTime() after which a running job should stop early this tick (checked inside long steps). */
    private static volatile long deadline = Long.MAX_VALUE;

    public BlockJobs(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.task = Tasks.timer(1L, 1L, this::tick);
    }

    public void stop() {
        Tasks.cancel(this.task);
        long deadline = System.currentTimeMillis() + 20000L;

        while ((!this.priority.isEmpty() || !this.queue.isEmpty()) && System.currentTimeMillis() < deadline) {
            this.tickWith(536870911);
        }
    }

    public CompletableFuture<Void> submit(BlockJobs.Job job) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        this.queue.add(new BlockJobs.Entry(job, f));
        return f;
    }

    public CompletableFuture<Void> submitUrgent(BlockJobs.Job job) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        this.priority.add(new BlockJobs.Entry(job, f));
        return f;
    }

    /** For logs: how many jobs wait and which one runs now. */
    public String describe() {
        BlockJobs.Entry e = !this.priority.isEmpty() ? this.priority.peek() : this.queue.peek();
        String running;
        try {
            running = e == null ? "idle" : e.job.name();
        } catch (RuntimeException var4) {
            running = "?";
        }

        return this.pending() + " job(s), running: " + running;
    }

    public int pending() {
        return this.queue.size() + this.priority.size();
    }

    private void tick() {
        this.tickWith(Cfg.i(Opt.BLOCKS_PER_TICK));
    }

    /** True once the current tick's block time budget (see budgetNanos) is used up. Jobs call it every few hundred blocks. */
    public static boolean overDeadline() {
        return System.nanoTime() > deadline;
    }

    /**
     * 3.13.5: how long block work may run this tick. It only takes the time the tick has spare (a busy server builds
     * slower instead of lagging), goes faster while nobody is online, and pauses when memory is nearly full.
     */
    static long budgetNanos() {
        double mspt;
        try {
            mspt = org.bukkit.Bukkit.getAverageTickTime();
        } catch (Throwable t) {
            mspt = 20.0;
        }

        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        if (used > rt.maxMemory() * 0.88) {
            // nearly out of memory: one short step only, so the garbage collector can catch up
            return 2_000_000L;
        }

        boolean empty = org.bukkit.Bukkit.getOnlinePlayers().isEmpty();
        double spare = (empty ? 46.0 : 42.0) - mspt;
        double ms = Math.max(3.0, Math.min(empty ? 34.0 : 22.0, spare * 0.75));
        return (long)(ms * 1_000_000L);
    }

    private void tickWith(int budget) {
        long start = System.nanoTime();
        long allowed = budgetNanos();
        deadline = budget >= 268435455 ? Long.MAX_VALUE : start + allowed;

        while (budget > 0) {
            Deque<BlockJobs.Entry> q = !this.priority.isEmpty() ? this.priority : this.queue;
            BlockJobs.Entry e = q.peek();
            if (e == null) {
                return;
            }

            try {
                int used = e.job.step(budget);
                budget -= Math.max(1, used);
                if (e.job.done()) {
                    q.poll();
                    e.future.complete(null);
                }
            } catch (Throwable var7) {
                q.poll();
                this.plugin.getLogger().log(Level.SEVERE, "Block job " + e.job.name() + " failed", var7);
                e.future.completeExceptionally(var7);
            }

            if (System.nanoTime() - start > allowed && budget < 268435455) {
                return;
            }
        }
    }

    private record Entry(BlockJobs.Job job, CompletableFuture<Void> future) {
    }

    public interface Job {
        int step(int var1);

        boolean done();

        default String name() {
            return this.getClass().getSimpleName();
        }
    }
}
