package net.vortexpvp.bot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.arena.Arena;
import net.vortexpvp.arena.ArenaInstance;
import net.vortexpvp.dialog.LobbyDialogs;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Session;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Bot fights and training. /bot [kit] [difficulty] fights a practice bot on a map of the kit; /train [dummy] puts the
 * player on a map with a training dummy and the kit for that skill. Each session holds an arena copy (released and
 * reset afterwards, so blocks the bot placed disappear) and ends on death, /lobby, quitting or the time limit.
 */
public final class BotManager implements Listener, TabExecutor {
    private final VortexPvP plugin;
    private final Map<UUID, BotManager.Fight> fights = new HashMap<>();
    private final Map<UUID, PracticeBot> byBody = new HashMap<>();

    public BotManager(VortexPvP plugin) {
        this.plugin = plugin;
    }

    /** One player with one bot (or dummy) on one arena copy. */
    public static final class Fight {
        final Player player;
        final Kit kit;
        final PracticeBot bot;
        final ArenaInstance inst;
        final int limit;
        int ticks;
        int swings;
        boolean over;

        Fight(Player player, Kit kit, PracticeBot bot, ArenaInstance inst, int limit) {
            this.player = player;
            this.kit = kit;
            this.bot = bot;
            this.inst = inst;
            this.limit = limit;
        }

        public PracticeBot bot() {
            return this.bot;
        }
    }

    public Fight fightOf(Player p) {
        return this.fights.get(p.getUniqueId());
    }

    public PracticeBot botOf(Entity e) {
        return this.byBody.get(e.getUniqueId());
    }

    // ------------------------------------------------------------------ starting

    private boolean ready(Player p) {
        Session s = this.plugin.sessions().get(p);
        if (s.state != PlayerState.LOBBY) {
            p.sendMessage(Text.mm("<red>Bots and training are started from the lobby."));
            return false;
        } else {
            return true;
        }
    }

    /** Fights a bot. {@code arena} picks the map (Bot Practice hall), null = any map of the kit. */
    public boolean fight(Player p, Kit kit, Difficulty diff, Arena arena) {
        if (!this.ready(p)) {
            return false;
        }

        ArenaInstance inst = arena != null && arena.allows(kit) ? this.plugin.arenas().acquire(arena, p) : this.plugin.arenas().acquireAny(kit, p);
        if (inst == null) {
            inst = this.anyFree(p);
        }

        if (inst == null) {
            p.sendMessage(Text.mm("<red>No free map for " + Text.esc(kit.displayName) + " right now - try again in a moment."));
            return false;
        }

        Session s = this.plugin.sessions().get(p);
        s.state = PlayerState.BOT;
        Tp.to(p, inst.spawn1());
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        this.plugin.kits().apply(p, kit, this.plugin.profiles().get(p).layouts.get(kit.id), List.of());
        // the map's spawns can be far apart on big biome maps: the bot starts 12 blocks in front of the player
        Location p1 = inst.spawn1();
        Vector toward = inst.spawn2().toVector().subtract(p1.toVector()).setY(0);
        if (toward.lengthSquared() < 1.0) {
            toward = p1.getDirection().setY(0);
        }

        Location start = p1.clone().add(toward.normalize().multiply(12.0));
        start.setY(start.getWorld().getHighestBlockYAt(start) + 1.0);
        if (!inst.contains(start) || start.getY() - p1.getY() > 12.0 || p1.getY() - start.getY() > 12.0) {
            start = inst.spawn2();
        }

        start.setDirection(p1.toVector().subtract(start.toVector()));
        PracticeBot bot = new PracticeBot(this.plugin, start, kit, diff, null, diff.label + " Bot");
        bot.target = p;
        bot.attackReady = 60;
        bot.smashReady = 100;
        bot.crystalReady = 80;
        Fight f = new Fight(p, kit, bot, inst, 20 * 60 * 5);
        this.fights.put(p.getUniqueId(), f);
        this.byBody.put(bot.body.getUniqueId(), bot);
        this.countdown(p, kit.displayName + " vs " + Text.plain(Text.mm(diff.label)) + " Bot");
        return true;
    }

    /** Training with a dummy on a map of the dummy's kit. */
    public boolean train(Player p, DummyType type) {
        if (!this.ready(p)) {
            return false;
        }

        Kit kit = this.plugin.kits().get(type.kit);
        if (kit == null) {
            kit = this.plugin.kits().all().stream().filter(k -> k.enabled).findFirst().orElse(null);
        }

        ArenaInstance inst = kit == null ? null : this.plugin.arenas().acquireAny(kit, p);
        if (inst == null) {
            inst = this.anyFree(p);
        }

        if (inst == null) {
            p.sendMessage(Text.mm("<red>No free map for training right now - try again in a moment."));
            return false;
        }

        Session s = this.plugin.sessions().get(p);
        s.state = PlayerState.BOT;
        Location spawn = inst.spawn1();
        Tp.to(p, spawn);
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        this.plugin.kits().apply(p, kit, this.plugin.profiles().get(p).layouts.get(kit.id), List.of());
        if (type == DummyType.MACE || type == DummyType.ELYTRA) {
            p.getInventory().addItem(new ItemStack(Material.WIND_CHARGE, 32));
            if (type == DummyType.ELYTRA) {
                p.getInventory().setChestplate(new ItemStack(Material.ELYTRA));
                p.getInventory().addItem(Items.of(Material.FIREWORK_ROCKET).build().asQuantity(32));
            }
        }

        Vector dir = spawn.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(1, 0, 0);
        }

        Location at = spawn.clone().add(dir.normalize().multiply(6.0));
        at.setY(at.getWorld().getHighestBlockYAt(at) + 1.0);
        PracticeBot bot = new PracticeBot(this.plugin, at, kit, Difficulty.MEDIUM, type, "<gold>" + type.display);
        bot.target = p;
        Fight f = new Fight(p, kit, bot, inst, 20 * 60 * 10);
        this.fights.put(p.getUniqueId(), f);
        this.byBody.put(bot.body.getUniqueId(), bot);
        p.sendMessage(Text.mm(" "));
        p.sendMessage(Text.mm("<gold><b>" + type.display + "</b> <dark_gray>- <gray>" + type.what));
        p.sendMessage(Text.mm("<yellow>Goal: <white>" + type.goal));
        p.sendMessage(Text.mm("<gray>Stats float over the dummy. <click:run_command:'/bot leave'><red>[Leave]</red></click>"));
        p.sendMessage(Text.mm(" "));
        Snd.play(p, "block.note_block.chime", 0.8F, 1.2F);
        return true;
    }

    /** Every map of the kit is busy: a bot doesn't mind which map, so any free biome or designed map will do. */
    private ArenaInstance anyFree(Player p) {
        for (Arena a : this.plugin.arenas().all()) {
            String pool = Arena.poolOf(a.theme);
            if (a.ready() && a.freeInstances() > 0L && !pool.equals("MOLE")) {
                ArenaInstance i = this.plugin.arenas().acquire(a, p);
                if (i != null) {
                    return i;
                }
            }
        }

        return null;
    }

    private void countdown(Player p, String what) {
        for (int i = 3; i >= 1; i--) {
            int n = i;
            Tasks.later((3 - i) * 20L, () -> {
                if (p.isOnline() && this.fights.containsKey(p.getUniqueId())) {
                    p.showTitle(Title.title(Text.mm("<gold><b>" + n), Text.mm("<gray>" + Text.esc(what))));
                    Snd.play(p, "block.note_block.hat", 0.8F, 1.0F + (3 - n) * 0.2F);
                }
            });
        }

        Tasks.later(60L, () -> {
            if (p.isOnline() && this.fights.containsKey(p.getUniqueId())) {
                p.showTitle(Title.title(Text.mm("<green><b>FIGHT!"), Component.empty()));
                Snd.play(p, "entity.ender_dragon.growl", 0.4F, 1.6F);
            }
        });
    }

    /** A dummy for someone else to tick (the Academy); its damage events are handled here. */
    public PracticeBot spawnDummy(Location at, Kit kit, DummyType type, Player target) {
        PracticeBot b = new PracticeBot(this.plugin, at, kit, Difficulty.MEDIUM, type, "<gold>" + type.display);
        b.target = target;
        this.byBody.put(b.body.getUniqueId(), b);
        return b;
    }

    public void despawn(PracticeBot b) {
        this.byBody.remove(b.body.getUniqueId());
        b.remove();
    }

    // ------------------------------------------------------------------ ticking and ending

    public void tick() {
        for (Fight f : new ArrayList<>(this.fights.values())) {
            f.ticks++;
            Session s = this.plugin.sessions().get(f.player);
            if (!f.player.isOnline() || s.state != PlayerState.BOT) {
                this.end(f, null, "left");
            } else if (!f.bot.valid()) {
                this.end(f, f.player, "bot down");
            } else if (f.ticks > f.limit) {
                this.end(f, null, "time");
            } else if (f.ticks > 60 || f.bot.dummy != null) {
                // never off the map: below it, or far from the player, the bot is put back on its spawn
                net.vortexpvp.util.Cuboid c = f.inst.bounds();
                Location bl = f.bot.body.getLocation();
                if (c != null && (bl.getY() < c.minY() + 1 || bl.getWorld() != f.player.getWorld() || bl.distanceSquared(f.player.getLocation()) > 3600.0)) {
                    f.bot.recover(f.bot.dummy == null ? f.inst.spawn2() : f.bot.home);
                }

                try {
                    f.bot.tick();
                } catch (RuntimeException var5) {
                    this.plugin.getLogger().log(java.util.logging.Level.WARNING, "Practice bot tick failed", (Throwable)var5);
                    this.end(f, null, "error");
                }
            }
        }
    }

    public void end(Fight f, Player winner, String why) {
        if (f.over) {
            return;
        }

        f.over = true;
        this.fights.remove(f.player.getUniqueId());
        this.byBody.remove(f.bot.body.getUniqueId());
        PracticeBot b = f.bot;
        b.remove();
        this.plugin.arenas().release(f.inst);
        Player p = f.player;
        if (p.isOnline()) {
            if (b.dummy == null && !why.equals("left")) {
                boolean won = winner != null;
                p.showTitle(Title.title(Text.mm(won ? "<green><b>VICTORY" : "<red><b>DEFEAT"), Text.mm("<gray>vs " + b.diff.label + " Bot")));
                Snd.play(p, won ? "ui.toast.challenge_complete" : "entity.wither.death", 0.6F, 1.0F);
            }

            p.sendMessage(Text.mm(" "));
            p.sendMessage(Text.mm(b.dummy == null ? "<gold><b>Bot fight</b> <gray>(" + Text.esc(f.kit.displayName) + ", " + b.diff.label + "<gray>)" : "<gold><b>Training: " + b.dummy.display));
            int acc = f.swings == 0 ? 0 : (int)Math.round(100.0 * b.hitsTaken / Math.max(f.swings, b.hitsTaken));
            p.sendMessage(Text.mm("<gray>Your hits <white>" + b.hitsTaken + "</white> <dark_gray>|</dark_gray> damage dealt <white>" + String.format("%.1f", b.damageTaken)
                + "</white> <dark_gray>|</dark_gray> best combo <white>" + b.bestCombo + "</white> <dark_gray>|</dark_gray> accuracy <white>" + acc + "%"));
            if (b.dummy == null) {
                p.sendMessage(Text.mm("<gray>Bot hits <white>" + b.hitsGiven + "</white> <dark_gray>|</dark_gray> damage taken <white>" + String.format("%.1f", b.damageGiven)
                    + "</white> <dark_gray>|</dark_gray> totem pops <white>" + b.pops));
                p.sendMessage(Text.mm("<click:run_command:'/bot " + f.kit.id + " " + b.diff.name().toLowerCase(Locale.ROOT) + "'><green><b>[Rematch]</b></green></click>  <click:run_command:'/bot'><aqua>[Other bot]</aqua></click>"));
            } else {
                p.sendMessage(Text.mm("<gray>Shield breaks <white>" + b.shieldBreaks + "</white> <dark_gray>|</dark_gray> best smash <white>" + String.format("%.1f", b.bestSmash)
                    + "</white> <dark_gray>|</dark_gray> totem pops <white>" + b.pops));
                p.sendMessage(Text.mm("<click:run_command:'/train " + b.dummy.name().toLowerCase(Locale.ROOT) + "'><green><b>[Again]</b></green></click>  <click:run_command:'/train'><aqua>[Other dummy]</aqua></click>"));
            }

            p.sendMessage(Text.mm(" "));
            if (this.plugin.sessions().get(p).state == PlayerState.BOT) {
                Tasks.later(why.equals("bot down") ? 40L : 1L, () -> {
                    if (p.isOnline() && this.plugin.sessions().get(p).state == PlayerState.BOT) {
                        this.plugin.lobby().sendToLobby(p);
                    }
                });
            }
        }
    }

    public void shutdown() {
        for (Fight f : new ArrayList<>(this.fights.values())) {
            this.end(f, null, "left");
        }
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            PracticeBot b = this.botOf(e.getEntity());
            if (b != null) {
                this.onBotDamage(e, b);
            }

            return;
        }

        Fight f = this.fights.get(p.getUniqueId());
        if (f == null) {
            return;
        }

        if (f.bot.dummy != null && !(e instanceof EntityDamageByEntityEvent ed && ed.getDamager().equals(p))) {
            // training: nothing hurts you except yourself (crystals you set off)
            if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION && e.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                && e.getCause() != EntityDamageEvent.DamageCause.FALL) {
                e.setCancelled(true);
            }
        }

        e.setCancelled(e.isCancelled() && e.getCause() != EntityDamageEvent.DamageCause.VOID);
        boolean lethal = e.getCause() == EntityDamageEvent.DamageCause.VOID
            || p.getHealth() - e.getFinalDamage() <= 0.0
                && p.getInventory().getItemInMainHand().getType() != Material.TOTEM_OF_UNDYING
                && p.getInventory().getItemInOffHand().getType() != Material.TOTEM_OF_UNDYING;
        if (!e.isCancelled() && lethal) {
            e.setCancelled(true);
            if (f.bot.dummy != null) {
                // training never ends in death: back to full health at the start
                p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
                Tp.to(p, f.inst.spawn1());
            } else {
                this.end(f, null, "player down");
            }
        }
    }

    private void onBotDamage(EntityDamageEvent e, PracticeBot b) {
        // its own crystals and anchors don't hurt it
        if (e instanceof EntityDamageByEntityEvent ed && ed.getDamager().equals(b.body)) {
            e.setCancelled(true);
            return;
        }

        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && b.phase != 0) {
            e.setCancelled(true);
            return;
        }

        if (b.dummy != null && b.body.getHealth() - e.getFinalDamage() <= 0.0 && b.dummy != DummyType.CRYSTAL) {
            // dummies don't die: full health again
            e.setDamage(0.0);
            Tasks.later(1L, () -> {
                if (b.valid()) {
                    b.body.setHealth(20.0);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitBot(EntityDamageByEntityEvent e) {
        PracticeBot b = this.botOf(e.getEntity());
        if (b != null) {
            Player p = e.getDamager() instanceof Player pl ? pl : (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player sh ? sh : null);
            if (e.getDamager() instanceof org.bukkit.entity.EnderCrystal || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                Fight f = this.fightForBot(b);
                p = f == null ? null : f.player;
            }

            if (p != null) {
                ItemStack hand = p.getInventory().getItemInMainHand();
                b.hitBy(p, e.getFinalDamage(), hand.getType().name().endsWith("_AXE"));
            }
        }
    }

    private Fight fightForBot(PracticeBot b) {
        for (Fight f : this.fights.values()) {
            if (f.bot == b) {
                return f;
            }
        }

        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent e) {
        PracticeBot b = this.botOf(e.getEntity());
        if (b != null) {
            b.reTotem();
        }
    }

    @EventHandler
    public void onBotDeath(EntityDeathEvent e) {
        if (this.botOf(e.getEntity()) != null) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onSwing(org.bukkit.event.player.PlayerAnimationEvent e) {
        Fight f = this.fights.get(e.getPlayer().getUniqueId());
        if (f != null) {
            f.swings++;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Fight f = this.fights.get(e.getPlayer().getUniqueId());
        if (f != null) {
            this.end(f, null, "left");
        }
    }

    /** Building during a bot fight or training: inside the map copy, for building kits and crystal kits. */
    public Boolean mayBuild(Player p, Block b) {
        Fight f = this.fights.get(p.getUniqueId());
        if (f == null) {
            return null;
        } else {
            boolean kitBuilds = f.kit.build || f.kit.playsOn("CRYSTAL");
            return kitBuilds && f.inst.contains(b.getLocation()) && b.getY() <= f.inst.maxBuildY();
        }
    }

    // ------------------------------------------------------------------ menus and commands

    private LobbyDialogs ui() {
        return this.plugin.dialogs().lobby();
    }

    /** Kit tiles (optionally filtered by a search), each opening the difficulty choice. */
    public void kitMenu(Player p, String search, Arena arena) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        String q = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();

        for (Kit k : this.plugin.kits().sorted()) {
            if (k.enabled && k.hasContents() && (arena == null || arena.allows(k))
                && (q.isEmpty() || k.displayName.toLowerCase(Locale.ROOT).contains(q) || k.id.contains(q))) {
                tiles.add(
                    new LobbyDialogs.Tile(
                        k.iconStack(),
                        Text.mm(k.color + "<b>" + Text.esc(k.displayName)),
                        Text.mm("<gray>" + this.skillsOf(k)),
                        List.of(Text.mm(k.color + "<b>" + Text.esc(k.displayName)), Text.mm("<gray>The bot uses: <white>" + this.skillsOf(k)), Text.mm("<green>▶ Pick a difficulty")),
                        () -> this.difficultyMenu(p, k, arena)
                    )
                );
            }
        }

        List<net.kyori.adventure.text.event.ClickEvent> none = List.of();
        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Practice Bots", arena == null ? "Pick a gamemode" : arena.displayName),
                tiles,
                List.of(Text.mm(q.isEmpty() ? "<gray>Fight a bot that plays the kit like a real player." : "<gray>Search: <white>" + Text.esc(q))),
                Text.mm("<gray>No gamemode matches \"" + Text.esc(q) + "\"."),
                List.of(
                    this.ui().btn(p, "<yellow>🔍 Search", "<gray>Find a gamemode by name", () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Search a gamemode"), "Name", "", 24, s -> this.kitMenu(p, s, arena))),
                    this.ui().btn(p, "<aqua>🎯 Training Dummies", "<gray>Shield, crystal, combo, mace and elytra", () -> this.dummyMenu(p))
                ),
                2,
                this.ui().close(p)
            );
    }

    private String skillsOf(Kit k) {
        List<String> s = new ArrayList<>();
        boolean mace = k.countOf(Material.MACE) > 0;
        if (k.countOf(Material.END_CRYSTAL) > 0) {
            s.add("crystals");
        }

        if (mace) {
            s.add("mace smash");
        }

        if (mace && k.countOf(Material.SHIELD) + k.countOf(Material.DIAMOND_AXE) + k.countOf(Material.NETHERITE_AXE) > 0) {
            s.add("stun slam");
        }

        if (k.countOf(Material.SHIELD) > 0) {
            s.add("shield");
        }

        if (k.countOf(Material.SPLASH_POTION) > 0) {
            s.add("pots");
        }

        if (k.countOf(Material.BOW) > 0) {
            s.add("bow");
        }

        s.add("crits, strafing");
        return String.join(", ", s);
    }

    public void difficultyMenu(Player p, Kit k, Arena arena) {
        List<net.vortexpvp.dialog.LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (Difficulty d : Difficulty.values()) {
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(switch (d) {
                        case EASY -> Material.LIME_DYE;
                        case MEDIUM -> Material.YELLOW_DYE;
                        case HARD -> Material.ORANGE_DYE;
                        case EXPERT -> Material.RED_DYE;
                    }),
                    Text.mm(d.label + " <b>Bot"),
                    Text.mm("<gray>" + d.about),
                    List.of(Text.mm(d.label + " <b>Bot"), Text.mm("<gray>" + d.about), Text.mm("<green>▶ Fight")),
                    () -> {
                        p.closeDialog();
                        this.fight(p, k, d, arena);
                    }
                )
            );
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title(Text.plain(k.name()), "Bot difficulty"),
                tiles,
                List.of(),
                null,
                List.of(this.ui().btn(p, "<gray>« Back", null, () -> this.kitMenu(p, null, arena))),
                1,
                this.ui().close(p)
            );
    }

    public void dummyMenu(Player p) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (DummyType t : DummyType.values()) {
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(t.icon),
                    Text.mm("<gold><b>" + t.display),
                    Text.mm("<gray>" + t.what),
                    List.of(Text.mm("<gold><b>" + t.display), Text.mm("<gray>" + t.what), Text.mm("<yellow>Goal: <white>" + t.goal), Text.mm("<green>▶ Train")),
                    () -> {
                        p.closeDialog();
                        this.train(p, t);
                    }
                )
            );
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Training Dummies", "Learn one skill at a time"),
                tiles,
                List.of(),
                null,
                List.of(this.ui().btn(p, "<gray>« Bots", null, () -> this.kitMenu(p, null, null))),
                1,
                this.ui().close(p)
            );
    }

    /** Practice bots and training dummies are not open yet. */
    public static final boolean COMING_SOON = false;

    public static void comingSoon(Player p) {
        p.sendMessage(Text.mm("<gold><b>Practice Bots</b> <dark_gray>» <yellow>Coming soon!</yellow> <gray>Bots for every gamemode and training dummies are on the way."));
        Snd.play(p, "block.note_block.chime", 0.7F, 1.2F);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (COMING_SOON) {
            comingSoon(p);
            return true;
        }

        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("train")) {
            DummyType t = args.length > 0 ? DummyType.parse(args[0]) : null;
            if (t == null) {
                this.dummyMenu(p);
            } else {
                this.train(p, t);
            }

            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("leave") || args[0].equalsIgnoreCase("stop"))) {
            Fight f = this.fights.get(p.getUniqueId());
            if (f != null) {
                this.end(f, null, "left");
            }

            return true;
        }

        Kit k = args.length > 0 ? this.plugin.kits().get(args[0].toLowerCase(Locale.ROOT)) : null;
        if (k == null) {
            this.kitMenu(p, args.length > 0 ? args[0] : null, null);
        } else if (args.length > 1) {
            this.fight(p, k, Difficulty.parse(args[1]), null);
        } else {
            this.difficultyMenu(p, k, null);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("train")) {
            for (DummyType t : DummyType.values()) {
                out.add(t.name().toLowerCase(Locale.ROOT));
            }
        } else if (args.length == 1) {
            out.add("leave");

            for (Kit k : this.plugin.kits().all()) {
                if (k.enabled) {
                    out.add(k.id);
                }
            }
        } else if (args.length == 2) {
            for (Difficulty d : Difficulty.values()) {
                out.add(d.name().toLowerCase(Locale.ROOT));
            }
        }

        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(typed));
        return out;
    }

    public Map<String, Integer> counts() {
        Map<String, Integer> m = new HashMap<>();
        m.put("fights", this.fights.size());
        return m;
    }
}
