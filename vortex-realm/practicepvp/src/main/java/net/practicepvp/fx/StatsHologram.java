package net.practicepvp.fx;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.board.BoardManager;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.data.KitStats;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.kit.Kit;
import net.practicepvp.rank.Tier;
import net.practicepvp.util.Fmt;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class StatsHologram implements Listener {
    private static final int PER_PAGE = 5;
    private final PracticePlugin plugin;
    private final Map<UUID, StatsHologram.View> views = new HashMap<>();

    public StatsHologram(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, UUID target, String name) {
        this.close(viewer);
        Player online = Bukkit.getPlayer(target);
        CompletableFuture<PvPProfile> prof = online != null
            ? CompletableFuture.completedFuture(this.plugin.profiles().get(online))
            : this.plugin.db().supply(c -> this.plugin.db().loadProfile(c, target, name));
        prof.whenComplete(
            (pr, err) -> {
                if (err == null && pr != null) {
                    int points = sum(pr);
                    this.plugin
                        .db()
                        .supply(c -> new Object[]{this.plugin.db().rankOverall(c, points), this.plugin.db().rival(c, target, null)})
                        .whenComplete(
                            (res, err2) -> Tasks.ensureSync(
                                () -> {
                                    if (viewer.isOnline()) {
                                        this.build(
                                            new StatsHologram.View(
                                                viewer, target, name, pr, err2 == null ? (int[])res[0] : null, err2 == null ? (Object[])res[1] : null
                                            )
                                        );
                                    }
                                }
                            )
                        );
                } else {
                    Tasks.ensureSync(() -> viewer.sendMessage(Text.mm("<red>Could not load that profile.")));
                }
            }
        );
    }

    public void close(Player viewer) {
        StatsHologram.View v = this.views.remove(viewer.getUniqueId());
        if (v != null) {
            if (v.task != null) {
                Tasks.cancel(v.task);
            }

            for (Entity e : v.all) {
                if (e.isValid()) {
                    e.remove();
                }
            }
        }
    }

    public void closeAll() {
        for (UUID u : new ArrayList<>(this.views.keySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                this.close(p);
            }
        }

        this.views.clear();
    }

    private static int sum(PvPProfile prof) {
        int s = 0;

        for (KitStats k : prof.kits.values()) {
            if (k.ranked()) {
                s += k.elo;
            }
        }

        return s;
    }

    private <T extends Entity> T spawn(StatsHologram.View v, Location at, Class<T> type, Consumer<T> setup) {
        T e = at.getWorld().spawn(at, type, x -> {
            x.setVisibleByDefault(false);
            x.setPersistent(false);
            setup.accept((T)x);
        });
        v.viewer.showEntity(this.plugin, e);
        v.all.add(e);
        return e;
    }

    private TextDisplay text(StatsHologram.View v, Location at, Component text, float scale, int bg, int width) {
        return this.spawn(v, at, TextDisplay.class, t -> {
            t.text(text);
            t.setBillboard(Billboard.VERTICAL);
            t.setBackgroundColor(Color.fromARGB(bg));
            t.setDefaultBackground(false);
            t.setShadowed(true);
            t.setLineWidth(width);
            t.setAlignment(TextAlignment.CENTER);
            t.setBrightness(new Brightness(15, 15));
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
        });
    }

    private void button(StatsHologram.View v, Location at, float w, float h, Runnable click) {
        Interaction i = this.spawn(v, at.clone().subtract(0.0, h / 2.0F, 0.0), Interaction.class, x -> {
            x.setInteractionWidth(w);
            x.setInteractionHeight(h);
            x.setResponsive(true);
        });
        v.clicks.put(i.getUniqueId(), click);
    }

    private void build(StatsHologram.View v) {
        Player p = v.viewer;
        World w = p.getWorld();
        Vector f = p.getEyeLocation().getDirection().setY(0);
        if (f.lengthSquared() < 1.0E-4) {
            f = new Vector(0, 0, 1);
        }

        f.normalize();
        Vector r = new Vector(-f.getZ(), 0.0, f.getX());
        Location feet = p.getLocation().clone();
        double eye = p.getEyeLocation().getY();
        Location center = feet.clone().add(f.clone().multiply(5.2));
        center.setY(eye - 0.35);

        for (Kit k : this.plugin.kits().sorted()) {
            if (k.enabled && k.ranked) {
                v.kits.add(k);
            }
        }

        this.text(
            v,
            center.clone().add(0.0, 1.85, 0.0),
            Text.mm("<#FF4A4A><b>" + Text.esc(Cfg.s(Opt.SERVER_NAME)).toUpperCase(Locale.ROOT) + "</b> <dark_gray>/ <white>PLAYER STATS"),
            0.9F,
            -435286510,
            400
        );
        TextDisplay close = this.text(v, center.clone().add(r.clone().multiply(2.7)).add(0.0, 1.85, 0.0), Text.mm("<gray>CLOSE"), 0.7F, -433057776, 80);
        this.button(v, close.getLocation().clone().add(0.0, 0.2, 0.0), 0.8F, 0.4F, () -> this.close(p));
        Location stand = feet.clone().add(f.clone().multiply(6.4));
        stand.setYaw(feet.getYaw() + 180.0F);
        stand.setPitch(0.0F);
        this.spawn(v, stand.clone().subtract(0.0, 0.55, 0.0), BlockDisplay.class, b -> {
            b.setBlock(Material.POLISHED_BLACKSTONE.createBlockData());
            b.setTransformation(new Transformation(new Vector3f(-0.6F, 0.0F, -0.6F), new Quaternionf(), new Vector3f(1.2F, 0.55F, 1.2F), new Quaternionf()));
        });
        this.spawn(v, stand, Mannequin.class, m -> {
            m.setInvulnerable(true);
            m.setImmovable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setGravity(false);

            try {
                m.setDescription(null);
            } catch (RuntimeException var4x) {
                m.setDescription(Component.empty());
            }

            m.setProfile(ResolvableProfile.resolvableProfile().name(v.name).build());
            m.setRotation(stand.getYaw(), 0.0F);
        });
        boolean on = Bukkit.getPlayer(v.target) != null;
        Tier best = null;

        for (KitStats kx : v.prof.kits.values()) {
            if (kx.tier() != null && (best == null || kx.tier().ordinal() > best.ordinal())) {
                best = kx.tier();
            }
        }

        Component nameTag = Text.mm("<white><b>" + Text.esc(v.name) + "</b>")
            .append((Component)(this.plugin.countries() == null ? Component.empty() : this.plugin.countries().tag(v.target, v.viewer)));
        Component status = Text.mm(on ? "<green>● ONLINE" : "<gray>● OFFLINE");
        Component tierLine = best == null ? Text.mm("<gray>UNRANKED") : BoardManager.tierTag(best);
        this.text(
            v,
            stand.clone().add(0.0, 2.55, 0.0),
            nameTag.append((Component)Component.newline()).append(status).append((Component)Component.newline()).append(tierLine),
            1.0F,
            0,
            200
        );
        v.left = this.text(v, center.clone().subtract(r.clone().multiply(2.75)).add(0.0, 0.35, 0.0), Component.empty(), 0.85F, -351334636, 150);
        v.right = this.text(v, center.clone().add(r.clone().multiply(2.75)).add(0.0, 0.35, 0.0), Component.empty(), 0.85F, -351334636, 170);
        v.pageLabel = this.text(v, center.clone().add(0.0, -1.75, 0.0), Component.empty(), 0.6F, 0, 120);
        Location prev = center.clone().subtract(r.clone().multiply(3.5)).add(0.0, -1.05, 0.0);
        Location next = center.clone().add(r.clone().multiply(3.5)).add(0.0, -1.05, 0.0);
        this.text(v, prev, Text.mm("<#FF4A4A><b>‹"), 2.0F, 0, 40);
        this.text(v, next, Text.mm("<#FF4A4A><b>›"), 2.0F, 0, 40);
        this.button(v, prev.clone().add(0.0, 0.3, 0.0), 0.7F, 0.7F, () -> this.page(v, center, r, v.page - 1));
        this.button(v, next.clone().add(0.0, 0.3, 0.0), 0.7F, 0.7F, () -> this.page(v, center, r, v.page + 1));
        this.views.put(p.getUniqueId(), v);
        this.page(v, center, r, 0);
        this.showStats(v);
        Snd.play(p, "block.beacon.power_select", 0.6F, 1.6F);
        v.task = Tasks.timer(
            10L,
            10L,
            () -> {
                if (!p.isOnline()
                    || !p.getWorld().equals(v.origin.getWorld())
                    || p.getLocation().distanceSquared(v.origin) > 9.0
                    || System.currentTimeMillis() - v.openedAt > 60000L) {
                    this.close(p);
                }
            }
        );
    }

    private void page(StatsHologram.View v, Location center, Vector r, int page) {
        int pages = Math.max(1, (v.kits.size() + 5) / 5);
        v.page = Math.floorMod(page, pages);

        for (Entity e : v.row) {
            v.clicks.remove(e.getUniqueId());
            if (e.isValid()) {
                e.remove();
            }

            v.all.remove(e);
        }

        v.row.clear();
        List<Object> items = new ArrayList<>();
        items.add("overall");
        items.addAll(v.kits);
        int from = v.page * 5;

        for (int i = 0; i < 5 && from + i < items.size(); i++) {
            Object o = items.get(from + i);
            double off = (i - 2.0) * 1.45;
            Location at = center.clone().add(r.clone().multiply(off)).add(0.0, -1.05, 0.0);
            ItemStack icon = o instanceof Kit k ? k.iconStack() : new ItemStack(Material.NETHER_STAR);
            String label = o instanceof Kit kx ? kx.displayName : "Overall";
            String id;
            String var26 = id = o instanceof Kit kxx ? kxx.id : "overall";
            boolean sel = id.equals(v.board);
            ItemDisplay d = this.spawn(v, at.clone().add(0.0, 0.25, 0.0), ItemDisplay.class, x -> {
                x.setItemStack(icon);
                x.setBillboard(Billboard.VERTICAL);
                x.setBrightness(new Brightness(15, 15));
                float s = sel ? 0.75F : 0.55F;
                x.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
                if (sel) {
                    x.setGlowing(true);
                    x.setGlowColorOverride(Color.fromRGB(16730698));
                }
            });
            TextDisplay t = this.text(
                v,
                at.clone().add(0.0, -0.35, 0.0),
                Text.mm((sel ? "<white><b>" : "<gray>") + Text.esc(label).toUpperCase(Locale.ROOT)),
                0.55F,
                sel ? -1069543408 : 0,
                120
            );
            v.row.add(d);
            v.row.add(t);
            Interaction hit = this.spawn(v, at.clone().add(0.0, -0.25, 0.0), Interaction.class, x -> {
                x.setInteractionWidth(1.1F);
                x.setInteractionHeight(1.0F);
                x.setResponsive(true);
            });
            v.row.add(hit);
            v.clicks.put(hit.getUniqueId(), () -> {
                v.board = id;
                this.showStats(v);
                this.page(v, center, r, v.page);
            });
        }

        if (v.pageLabel != null && v.pageLabel.isValid()) {
            v.pageLabel.text(Text.mm("<gray>PAGE " + (v.page + 1) + " / " + pages));
        }
    }

    private void showStats(StatsHologram.View v) {
        boolean overall = "overall".equals(v.board);
        int wins = 0;
        int losses = 0;
        int rw = 0;
        int rl = 0;
        int cw = 0;
        int cl = 0;
        int points = 0;
        Tier tier = null;

        for (Entry<String, KitStats> e : v.prof.kits.entrySet()) {
            if (overall || e.getKey().equals(v.board)) {
                KitStats k = e.getValue();
                wins += k.wins + k.unrankedWins;
                losses += k.losses + k.unrankedLosses;
                rw += k.roundsWon;
                rl += k.roundsLost;
                cw += k.challengeWins;
                cl += k.challengeLosses;
                if (k.ranked()) {
                    points += k.elo;
                }

                if (k.tier() != null && (tier == null || k.tier().ordinal() > tier.ordinal())) {
                    tier = k.tier();
                }
            }
        }

        Kit kit = overall ? null : this.plugin.kits().get(v.board);
        String scope = kit == null ? "CAREER" : kit.displayName.toUpperCase(Locale.ROOT);
        int total = wins + losses;
        String rate = total == 0 ? "0.0%" : String.format(Locale.US, "%.1f%%", wins * 100.0 / total);
        Component strength = tier == null ? Text.mm("<white><b>UNRANKED") : BoardManager.tierTag(tier);
        Component left = Text.mm(
                "<#FF4A4A><b>"
                    + scope
                    + " OVERVIEW</b>\n\n<gray><i>MATCHES</i>\n<white>"
                    + total
                    + "\n\n<gray><i>MATCH RECORD</i>\n<white>"
                    + wins
                    + " / "
                    + losses
                    + "\n\n<gray><i>ROUND RECORD</i>\n<white>"
                    + rw
                    + " / "
                    + rl
                    + "\n\n<gray><i>STRENGTH</i>\n"
            )
            .append(strength);
        String sor = v.rank != null && points > 0 ? "#" + Fmt.num(v.rank[0]) + " / " + Fmt.num(v.rank[1]) : "UNRANKED";
        String rival = v.rival == null ? "NONE YET" : Text.esc(String.valueOf(v.rival[0]));
        int bars = total == 0 ? 0 : (int)Math.round(wins * 20.0 / total);
        Component right = Text.mm(
            "<#FF4A4A><b>"
                + scope
                + " TOTAL</b>\n\n<gray><i>MATCHES</i>\n<white>"
                + total
                + "\n\n<gray><i>MATCH RECORD</i>\n<white>"
                + wins
                + " / "
                + losses
                + "\n\n<gray><i>ROUND RECORD</i>\n<white>"
                + rw
                + " / "
                + rl
                + "\n\n<gray>CHALLENGES <white>"
                + cw
                + " / "
                + cl
                + "\n<gray>SOR SCORE <white>"
                + sor
                + "\n<gray>RIVAL <white>"
                + rival
                + "\n\n<#3D8BFF>"
                + "▌".repeat(bars)
                + "<dark_gray>"
                + "▌".repeat(20 - bars)
                + "\n<gray>WIN RATE <white><b>"
                + rate
        );
        if (v.left != null && v.left.isValid()) {
            v.left.text(left);
        }

        if (v.right != null && v.right.isValid()) {
            v.right.text(right);
        }
    }

    private boolean click(Player p, Entity e) {
        StatsHologram.View v = this.views.get(p.getUniqueId());
        if (v == null) {
            return false;
        } else {
            Runnable r = v.clicks.get(e.getUniqueId());
            if (r == null) {
                return false;
            } else {
                Snd.click(p);
                v.openedAt = System.currentTimeMillis();
                r.run();
                return true;
            }
        }
    }

    @EventHandler(
        ignoreCancelled = false
    )
    public void onRight(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Interaction && this.click(e.getPlayer(), e.getRightClicked())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        ignoreCancelled = false
    )
    public void onLeft(PrePlayerAttackEntityEvent e) {
        if (e.getAttacked() instanceof Interaction && this.click(e.getPlayer(), e.getAttacked())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.close(e.getPlayer());
    }

    private final class View {
        final Player viewer;
        final UUID target;
        final String name;
        final PvPProfile prof;
        final int[] rank;
        final Object[] rival;
        final Location origin;
        final List<Entity> all = new ArrayList<>();
        final Map<UUID, Runnable> clicks = new HashMap<>();
        final List<Kit> kits = new ArrayList<>();
        TextDisplay left;
        TextDisplay right;
        TextDisplay pageLabel;
        final List<Entity> row = new ArrayList<>();
        String board = "overall";
        int page;
        long openedAt = System.currentTimeMillis();
        BukkitTask task;

        View(Player viewer, UUID target, String name, PvPProfile prof, int[] rank, Object[] rival) {
            this.viewer = viewer;
            this.target = target;
            this.name = name;
            this.prof = prof;
            this.rank = rank;
            this.rival = rival;
            this.origin = viewer.getLocation().clone();
        }
    }
}
