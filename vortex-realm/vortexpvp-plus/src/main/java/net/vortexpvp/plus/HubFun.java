package net.vortexpvp.plus;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Things to do in the hub: pets that follow you (no AI, nobody can hurt them), gadgets (leap, grapple, fireworks,
 * paint gun - paint is only shown to players, the hub never changes), a timed parkour course with checkpoints and a
 * top 10, seasonal decorations (Halloween, winter) switched on by the date, and a punching dummy while you queue.
 */
public final class HubFun implements Listener {
    private final VortexPvP plugin;
    private final PlusModule plus;
    private final NamespacedKey key;
    private final Map<UUID, Entity> pets = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Map<UUID, Long> parkourStart = new HashMap<>();
    private final Map<UUID, Location> parkourCheckpoint = new HashMap<>();
    private final Map<UUID, Mannequin> dummies = new HashMap<>();
    private final Map<UUID, int[]> combo = new HashMap<>();
    private final Map<UUID, Long> comboAt = new HashMap<>();
    private final Random random = new Random();

    public HubFun(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
        this.key = new NamespacedKey(plugin, "vp_hubfun");
    }

    private boolean inHub(Player p) {
        PlayerState s = this.plugin.sessions().state(p);
        return s == PlayerState.LOBBY || s == PlayerState.QUEUE;
    }

    // ------------------------------------------------------------------ pets

    private static EntityType petType(String pet) {
        return switch (pet) {
            case "wolf" -> EntityType.WOLF;
            case "cat" -> EntityType.CAT;
            case "fox" -> EntityType.FOX;
            case "axolotl" -> EntityType.AXOLOTL;
            case "parrot" -> EntityType.PARROT;
            case "bee" -> EntityType.BEE;
            case "frog" -> EntityType.FROG;
            case "rabbit" -> EntityType.RABBIT;
            case "panda" -> EntityType.PANDA;
            case "sniffer" -> EntityType.SNIFFER;
            default -> null;
        };
    }

    public void refreshPet(Player p) {
        Entity old = this.pets.remove(p.getUniqueId());
        if (old != null) {
            old.remove();
        }
    }

    /** Every 5 ticks: pets follow their owners, gadgets stay in the hotbar, dummies follow queues. */
    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID u = p.getUniqueId();
            boolean hub = this.inHub(p);
            String pet = this.plus.flair().selected(u, "pet");
            Entity e = this.pets.get(u);
            if (!this.plus.config().on("pets") || !hub || pet == null || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                if (e != null) {
                    e.remove();
                    this.pets.remove(u);
                }
            } else {
                EntityType t = petType(pet);
                if (e == null || !e.isValid() || e.getType() != t || e.getWorld() != p.getWorld()) {
                    if (e != null) {
                        e.remove();
                    }

                    if (t != null) {
                        e = p.getWorld().spawnEntity(p.getLocation().add(p.getLocation().getDirection().setY(0).multiply(-1.5)), t, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, x -> {
                            x.setPersistent(false);
                            x.setSilent(true);
                            x.setInvulnerable(true);
                            x.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, "pet");
                            x.customName(Text.mm("<gray>" + Text.esc(p.getName()) + "'s " + Progression.nice(pet)));
                            x.setCustomNameVisible(false);
                            if (x instanceof Mob m) {
                                m.setAware(false);
                            }

                            if (x instanceof Ageable a) {
                                a.setBaby();
                                a.setAgeLock(true);
                            }

                            if (x instanceof LivingEntity le) {
                                le.setCollidable(false);
                                le.setRemoveWhenFarAway(false);
                            }
                        });
                        this.pets.put(u, e);
                    }
                } else if (e.getLocation().distanceSquared(p.getLocation()) > 6.25) {
                    Location behind = p.getLocation().clone().add(p.getLocation().getDirection().setY(0).normalize().multiply(-1.6));
                    behind.setY(p.getLocation().getY());
                    behind.setDirection(p.getLocation().toVector().subtract(behind.toVector()));
                    if (e.getLocation().distanceSquared(p.getLocation()) > 100.0) {
                        e.teleport(behind);
                    } else {
                        Location step = e.getLocation().clone().add(behind.toVector().subtract(e.getLocation().toVector()).multiply(0.35));
                        step.setDirection(p.getLocation().toVector().subtract(step.toVector()));
                        e.teleport(step);
                    }
                }
            }

            // gadget in slot 7 while in the lobby, if the slot is free
            // the gadget item sits in the first free hotbar slot while in the lobby (the lobby items keep theirs)
            String gadget = this.plus.flair().selected(u, "gadget");
            if (this.plugin.sessions().state(p) == PlayerState.LOBBY) {
                boolean want = this.plus.config().on("gadgets") && gadget != null && !this.parkourStart.containsKey(u);
                int at = -1;
                int free = -1;
                String tag = null;
                for (int i = 0; i < 9; i++) {
                    ItemStack it = p.getInventory().getItem(i);
                    String t = it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
                    if (t != null && t.startsWith("gadget:")) {
                        at = i;
                        tag = t;
                        break;
                    }

                    if (free < 0 && (it == null || it.getType() == Material.AIR)) {
                        free = i;
                    }
                }

                if (!want) {
                    if (at >= 0) {
                        p.getInventory().setItem(at, null);
                    }
                } else if (at >= 0) {
                    if (!tag.equals("gadget:" + gadget)) {
                        p.getInventory().setItem(at, this.gadgetItem(gadget));
                    }
                } else if (free >= 0) {
                    p.getInventory().setItem(free, this.gadgetItem(gadget));
                }
            }

            this.queueDummy(p);
        }
    }

    // ------------------------------------------------------------------ gadgets

    private ItemStack gadgetItem(String g) {
        Material m = switch (g) {
            case "leap" -> Material.RABBIT_FOOT;
            case "grapple" -> Material.TRIPWIRE_HOOK;
            case "fireworks" -> Material.FIREWORK_STAR;
            default -> Material.BRUSH;
        };
        ItemStack it = Items.of(m).name("<light_purple>Gadget: <white>" + Progression.nice(g) + " <gray>(right-click)").hideAll().build();
        it.editMeta(meta -> meta.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, "gadget:" + g));
        return it;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta() || e.getAction() == Action.PHYSICAL) {
            return;
        }

        String tag = it.getItemMeta().getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
        if (tag == null || !tag.startsWith("gadget:")) {
            return;
        }

        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!this.inHub(p) || e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        this.tryGadget(p, tag.substring(7));
    }

    /** Uses a gadget if it has recharged (from the hotbar item, the F key or /gadget use). */
    public void tryGadget(Player p, String g) {
        if (!this.inHub(p)) {
            p.sendActionBar(Text.mm("<red>Gadgets work in the hub"));
            return;
        }

        if (System.currentTimeMillis() < this.cooldown.getOrDefault(p.getUniqueId(), 0L)) {
            p.sendActionBar(Text.mm("<red>Gadget recharging..."));
            return;
        }

        this.useGadget(p, g);
    }

    /** F (swap hands) in the hub uses the picked gadget - it works even when the hotbar is full. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapKey(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        String g = this.plus.flair().selected(p.getUniqueId(), "gadget");
        if (g == null || !this.plus.config().on("gadgets") || this.plugin.sessions().state(p) != PlayerState.LOBBY) {
            return;
        }

        e.setCancelled(true);
        this.tryGadget(p, g);
    }

    public void useGadget(Player p, String g) {
        long now = System.currentTimeMillis();
        switch (g) {
            case "leap" -> {
                p.setVelocity(p.getLocation().getDirection().multiply(1.6).setY(0.9));
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 20, 0.3, 0.1, 0.3, 0.05);
                Snd.play(p, "entity.breeze.jump", 1.0F, 1.2F);
                this.cooldown.put(p.getUniqueId(), now + 3000L);
            }
            case "grapple" -> {
                RayTraceResult hit = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getLocation().getDirection(), 28.0);
                if (hit == null) {
                    p.sendActionBar(Text.mm("<gray>Nothing to grab"));
                    return;
                }

                Vector to = hit.getHitPosition().subtract(p.getLocation().toVector());
                p.setVelocity(to.multiply(0.13).setY(Math.min(1.4, to.getY() * 0.13 + 0.6)));
                for (double d = 0; d < 1.0; d += 0.05) {
                    p.getWorld().spawnParticle(Particle.CRIT, p.getEyeLocation().toVector().add(to.clone().multiply(d)).toLocation(p.getWorld()), 1, 0, 0, 0, 0);
                }

                Snd.play(p, "entity.fishing_bobber.retrieve", 1.0F, 0.8F);
                this.cooldown.put(p.getUniqueId(), now + 4000L);
            }
            case "fireworks" -> {
                this.plus.flair().play("fireworks", p.getLocation());
                this.cooldown.put(p.getUniqueId(), now + 8000L);
            }
            case "paint" -> {
                RayTraceResult hit = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getLocation().getDirection(), 30.0);
                if (hit == null || hit.getHitBlock() == null) {
                    return;
                }

                Material[] wool = {Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL, Material.LIGHT_BLUE_WOOL, Material.PURPLE_WOOL, Material.MAGENTA_WOOL};
                BlockData paint = wool[this.random.nextInt(wool.length)].createBlockData();
                Block c = hit.getHitBlock();
                List<Block> blocks = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            Block b = c.getRelative(dx, dy, dz);
                            if (b.getType().isSolid() && this.random.nextInt(3) > 0) {
                                blocks.add(b);
                            }
                        }
                    }
                }

                List<Player> viewers = new ArrayList<>();
                for (Player o : p.getWorld().getPlayers()) {
                    if (o.getLocation().distanceSquared(c.getLocation()) < 4096.0) {
                        viewers.add(o);
                    }
                }

                for (Player o : viewers) {
                    for (Block b : blocks) {
                        o.sendBlockChange(b.getLocation(), paint);
                    }
                }

                Snd.play(p, "entity.slime.squish", 1.0F, 1.4F);
                Tasks.later(100L, () -> {
                    for (Player o : viewers) {
                        if (o.isOnline()) {
                            for (Block b : blocks) {
                                o.sendBlockChange(b.getLocation(), b.getBlockData());
                            }
                        }
                    }
                });
                this.cooldown.put(p.getUniqueId(), now + 1500L);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ parkour

    private YamlConfiguration pk() {
        return this.plus.store().doc("parkour");
    }

    /** Builds a 24-jump course starting where the admin stands, going the way they look. */
    public void buildParkour(Player admin) {
        this.removeParkour();
        Location start = admin.getLocation().getBlock().getLocation();
        Vector f = admin.getLocation().getDirection().setY(0);
        boolean alongX = Math.abs(f.getX()) >= Math.abs(f.getZ());
        int sx = alongX ? (int)Math.signum(f.getX()) : 0;
        int sz = alongX ? 0 : (int)Math.signum(f.getZ());
        if (sx == 0 && sz == 0) {
            sx = 1;
        }

        World w = start.getWorld();
        List<String> placed = new ArrayList<>();
        Random r = new Random(11L);
        int x = start.getBlockX();
        int y = start.getBlockY() + 1;
        int z = start.getBlockZ();
        this.set(w, x, y, z, Material.LIME_CONCRETE, placed);
        for (int i = 1; i <= 24; i++) {
            int dy = r.nextInt(3) - 1;
            int gap = dy > 0 ? 2 : 2 + r.nextInt(2);
            int side = r.nextInt(3) - 1;
            x += sx * (gap + 1) + (alongX ? 0 : side);
            z += sz * (gap + 1) + (alongX ? side : 0);
            y += dy;
            Material m = i == 24 ? Material.DIAMOND_BLOCK : (i % 6 == 0 ? Material.GOLD_BLOCK : (i % 2 == 0 ? Material.WHITE_CONCRETE : Material.LIGHT_BLUE_CONCRETE));
            this.set(w, x, y, z, m, placed);
        }

        YamlConfiguration y2 = this.pk();
        y2.set("world", w.getName());
        y2.set("start", start.getBlockX() + "," + (start.getBlockY() + 2) + "," + start.getBlockZ());
        y2.set("start-y", start.getBlockY() + 1);
        y2.set("blocks", placed);
        this.plus.store().dirty("parkour");
        admin.sendMessage(Text.mm("<green>Parkour built: 24 jumps, a checkpoint every 6, a diamond finish. <gray>Stand on the lime block to start."));
    }

    private void set(World w, int x, int y, int z, Material m, List<String> placed) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getType().isAir()) {
            b.setType(m, false);
            placed.add(x + "," + y + "," + z);
        }
    }

    public void removeParkour() {
        YamlConfiguration y = this.pk();
        World w = Bukkit.getWorld(y.getString("world", ""));
        if (w != null) {
            for (String s : y.getStringList("blocks")) {
                String[] p = s.split(",");
                w.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])).setType(Material.AIR, false);
            }
        }

        y.set("blocks", null);
        y.set("start", null);
        this.plus.store().dirty("parkour");
        this.parkourStart.clear();
        this.parkourCheckpoint.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockY() == e.getTo().getBlockY() && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        Player p = e.getPlayer();
        if (!this.plus.config().on("parkour") || this.plugin.sessions().state(p) != PlayerState.LOBBY || this.pk().getString("start") == null) {
            return;
        }

        if (!p.getWorld().getName().equals(this.pk().getString("world"))) {
            return;
        }

        Material under = e.getTo().clone().subtract(0.0, 0.2, 0.0).getBlock().getType();
        UUID u = p.getUniqueId();
        Long started = this.parkourStart.get(u);
        if (under == Material.LIME_CONCRETE && this.isCourse(e.getTo().clone().subtract(0.0, 0.2, 0.0).getBlock())) {
            if (started == null || System.currentTimeMillis() - started > 1500L) {
                this.parkourStart.put(u, System.currentTimeMillis());
                this.parkourCheckpoint.put(u, e.getTo().getBlock().getLocation().add(0.5, 0.0, 0.5));
                p.setAllowFlight(false);
                p.setFlying(false);
                p.sendActionBar(Text.mm("<green>⏱ Parkour started! <gray>Gold = checkpoint, diamond = finish. /parkour quit"));
                Snd.play(p, "block.note_block.pling", 1.0F, 1.5F);
            }

            return;
        }

        if (started == null) {
            return;
        }

        if (under == Material.GOLD_BLOCK && this.isCourse(e.getTo().clone().subtract(0.0, 0.2, 0.0).getBlock())) {
            Location cp = e.getTo().getBlock().getLocation().add(0.5, 0.0, 0.5);
            Location old = this.parkourCheckpoint.get(u);
            if (old == null || old.distanceSquared(cp) > 1.0) {
                this.parkourCheckpoint.put(u, cp);
                p.sendActionBar(Text.mm("<gold>✔ Checkpoint <gray>" + time(System.currentTimeMillis() - started)));
                Snd.play(p, "entity.experience_orb.pickup", 1.0F, 1.2F);
            }
        } else if (under == Material.DIAMOND_BLOCK && this.isCourse(e.getTo().clone().subtract(0.0, 0.2, 0.0).getBlock())) {
            long ms = System.currentTimeMillis() - started;
            this.parkourStart.remove(u);
            this.parkourCheckpoint.remove(u);
            this.finishParkour(p, ms);
        } else if (e.getTo().getY() < this.pk().getInt("start-y") - 6) {
            Location cp = this.parkourCheckpoint.get(u);
            if (cp != null) {
                Tasks.sync(() -> Tp.to(p, cp));
            }
        }

        if (started != null && this.parkourStart.containsKey(u)) {
            p.sendActionBar(Text.mm("<white>⏱ " + time(System.currentTimeMillis() - started)));
        }
    }

    private boolean isCourse(Block b) {
        return this.pk().getStringList("blocks").contains(b.getX() + "," + b.getY() + "," + b.getZ());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDoubleJump(PlayerToggleFlightEvent e) {
        if (this.parkourStart.remove(e.getPlayer().getUniqueId()) != null) {
            this.parkourCheckpoint.remove(e.getPlayer().getUniqueId());
            e.getPlayer().sendActionBar(Text.mm("<red>Parkour run stopped - no double jumps or flying!"));
        }
    }

    static String time(long ms) {
        return String.format(Locale.ROOT, "%d:%02d.%02d", ms / 60000, ms / 1000 % 60, ms / 10 % 100);
    }

    private void finishParkour(Player p, long ms) {
        PlayerData d = this.plus.store().get(p);
        long best = d.count("parkour_best_ms");
        boolean pb = best == 0 || ms < best;
        if (pb) {
            d.counters.put("parkour_best_ms", ms);
            d.dirty = true;
        }

        this.plus.progression().track(p.getUniqueId(), "parkour", null, 1L);
        this.plus.progression().xp(p.getUniqueId(), pb ? 60L : 25L, "parkour");
        YamlConfiguration y = this.pk();
        List<String> top = new ArrayList<>(y.getStringList("top"));
        top.removeIf(s -> s.startsWith(p.getUniqueId() + "|") && Long.parseLong(s.split("\\|")[2]) > ms);
        if (top.stream().noneMatch(s -> s.startsWith(p.getUniqueId() + "|"))) {
            top.add(p.getUniqueId() + "|" + p.getName() + "|" + ms);
        }

        top.sort((a, b) -> Long.compare(Long.parseLong(a.split("\\|")[2]), Long.parseLong(b.split("\\|")[2])));
        while (top.size() > 10) {
            top.remove(top.size() - 1);
        }

        y.set("top", top);
        this.plus.store().dirty("parkour");
        p.sendMessage(Text.mm("<aqua>🏁 Parkour finished in <white>" + time(ms) + (pb ? " <green>(personal best!)" : " <gray>(best " + time(best) + ")")));
        Snd.play(p, "ui.toast.challenge_complete", 1.0F, 1.0F);
        if (!top.isEmpty() && top.get(0).startsWith(p.getUniqueId() + "|") && pb) {
            Bukkit.broadcast(Text.mm("<aqua>🏁 <white>" + Text.esc(p.getName()) + " <gray>set the hub parkour record: <white>" + time(ms)));
        }
    }

    public void parkourCommand(Player p, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "build" -> {
                if (p.hasPermission("vortexpvp.admin")) {
                    this.buildParkour(p);
                }
            }
            case "remove" -> {
                if (p.hasPermission("vortexpvp.admin")) {
                    this.removeParkour();
                    p.sendMessage(Text.mm("<gray>Parkour removed."));
                }
            }
            case "quit" -> {
                this.parkourStart.remove(p.getUniqueId());
                this.parkourCheckpoint.remove(p.getUniqueId());
                p.sendMessage(Text.mm("<gray>Parkour run stopped."));
            }
            case "checkpoint", "cp" -> {
                Location cp = this.parkourCheckpoint.get(p.getUniqueId());
                if (cp != null) {
                    Tp.to(p, cp);
                }
            }
            case "top" -> {
                List<String> top = this.pk().getStringList("top");
                p.sendMessage(Text.mm("<aqua><b>Hub parkour - top 10"));
                for (int i = 0; i < top.size(); i++) {
                    String[] s = top.get(i).split("\\|");
                    p.sendMessage(Text.mm("<gray>#" + (i + 1) + " <white>" + Text.esc(s[1]) + " <dark_gray>- <white>" + time(Long.parseLong(s[2]))));
                }
            }
            default -> {
                String st = this.pk().getString("start");
                if (st == null) {
                    p.sendMessage(Text.mm("<gray>No parkour yet." + (p.hasPermission("vortexpvp.admin") ? " Build one with <white>/parkour build</white> (it starts where you stand)." : "")));
                    return;
                }

                if (this.plugin.sessions().state(p) != PlayerState.LOBBY) {
                    p.sendMessage(Text.mm("<red>Go to the lobby first."));
                    return;
                }

                String[] c = st.split(",");
                World w = Bukkit.getWorld(this.pk().getString("world", ""));
                if (w != null) {
                    Tp.to(p, new Location(w, Integer.parseInt(c[0]) + 0.5, Integer.parseInt(c[1]), Integer.parseInt(c[2]) + 0.5, p.getLocation().getYaw(), 0.0F));
                }

                p.sendMessage(Text.mm("<gray>/parkour top • /parkour checkpoint • /parkour quit"));
            }
        }
    }

    // ------------------------------------------------------------------ seasonal hub decorations

    public String season() {
        String forced = this.plus.config().s("hub.season-deco", "auto").toLowerCase(Locale.ROOT);
        if (!forced.equals("auto")) {
            return forced.equals("off") ? null : forced;
        }

        LocalDate d = LocalDate.now();
        if (d.getMonth() == Month.OCTOBER && d.getDayOfMonth() >= 15 || d.getMonth() == Month.NOVEMBER && d.getDayOfMonth() <= 7) {
            return "halloween";
        }

        if (d.getMonth() == Month.DECEMBER || d.getMonth() == Month.JANUARY && d.getDayOfMonth() <= 6) {
            return "winter";
        }

        if (d.getMonth() == Month.FEBRUARY && d.getDayOfMonth() >= 10 && d.getDayOfMonth() <= 16) {
            return "valentine";
        }

        return null;
    }

    /** Every second: the particles of the season around hub players. */
    public void decorations() {
        String s = this.season();
        if (s == null) {
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!this.inHub(p)) {
                continue;
            }

            Location l = p.getLocation();
            switch (s) {
                case "halloween" -> {
                    p.spawnParticle(Particle.SOUL, l.clone().add(0.0, 2.0, 0.0), 4, 8.0, 3.0, 8.0, 0.01);
                    p.spawnParticle(Particle.SMOKE, l.clone().add(0.0, 0.5, 0.0), 6, 8.0, 0.5, 8.0, 0.0);
                    p.spawnParticle(Particle.FALLING_SPORE_BLOSSOM, l.clone().add(0.0, 6.0, 0.0), 3, 8.0, 2.0, 8.0, 0.0);
                }
                case "winter" -> p.spawnParticle(Particle.SNOWFLAKE, l.clone().add(0.0, 7.0, 0.0), 25, 10.0, 3.0, 10.0, 0.0);
                case "valentine" -> p.spawnParticle(Particle.HEART, l.clone().add(0.0, 4.0, 0.0), 2, 8.0, 2.0, 8.0, 0.0);
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------ queue dummy

    private void queueDummy(Player p) {
        UUID u = p.getUniqueId();
        Mannequin m = this.dummies.get(u);
        boolean want = this.plus.config().on("queue-dummy") && this.plugin.sessions().state(p) == PlayerState.QUEUE;
        if (!want) {
            if (m != null) {
                m.remove();
                this.dummies.remove(u);
                this.combo.remove(u);
            }

            return;
        }

        if (m == null || !m.isValid() || m.getWorld() != p.getWorld() || m.getLocation().distanceSquared(p.getLocation()) > 400.0) {
            if (m != null) {
                m.remove();
            }

            Location at = p.getLocation().clone().add(p.getLocation().getDirection().setY(0).normalize().multiply(2.5));
            at.setDirection(p.getLocation().toVector().subtract(at.toVector()));
            at.setPitch(0.0F);
            m = at.getWorld().spawn(at, Mannequin.class, x -> {
                x.setPersistent(false);
                x.setVisibleByDefault(false);
                x.setSilent(true);
                x.setImmovable(true);
                x.setGravity(false);
                x.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, "dummy");
                x.setProfile(ResolvableProfile.resolvableProfile().name("Steve").build());
                x.customName(Text.mm("<yellow>Punch me while you wait!"));
                x.setCustomNameVisible(true);
                try {
                    x.setDescription(null);
                } catch (RuntimeException var2) {
                }
            });
            p.showEntity(this.plugin, m);
            this.dummies.put(u, m);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDummyHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Mannequin m) || !"dummy".equals(m.getPersistentDataContainer().get(this.key, PersistentDataType.STRING))) {
            return;
        }

        e.setCancelled(true);
        if (e.getDamager() instanceof Player p && this.dummies.get(p.getUniqueId()) == m) {
            long now = System.currentTimeMillis();
            int[] c = this.combo.computeIfAbsent(p.getUniqueId(), k -> new int[2]);
            if (now - this.comboAt.getOrDefault(p.getUniqueId(), 0L) > 1200L) {
                c[0] = 0;
            }

            c[0]++;
            c[1] = Math.max(c[1], c[0]);
            this.comboAt.put(p.getUniqueId(), now);
            m.playHurtAnimation(0.0F);
            p.sendActionBar(Text.mm("<gold>Combo <white>" + c[0] + " <dark_gray>• <gray>best <white>" + c[1]));
            this.plus.progression().best(p.getUniqueId(), "best_queue_combo", c[1]);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPetDamage(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(this.key)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        Entity pet = this.pets.remove(u);
        if (pet != null) {
            pet.remove();
        }

        Mannequin m = this.dummies.remove(u);
        if (m != null) {
            m.remove();
        }

        this.parkourStart.remove(u);
        this.parkourCheckpoint.remove(u);
        this.cooldown.remove(u);
        this.combo.remove(u);
    }

    public void removeAll() {
        for (Entity e : this.pets.values()) {
            e.remove();
        }

        for (Mannequin m : this.dummies.values()) {
            m.remove();
        }

        this.pets.clear();
        this.dummies.clear();
    }

    public String selfCheck() {
        for (String pet : Progression.PETS) {
            if (petType(pet) == null) {
                return "no entity for pet " + pet;
            }
        }

        return time(61234L).equals("1:01.23") ? null : "time format " + time(61234L);
    }
}
