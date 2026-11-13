package net.vortexrealm.bot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.arena.Arena;
import net.vortexrealm.arena.ArenaInstance;
import net.vortexrealm.dialog.LobbyDialogs;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Session;
import net.vortexrealm.session.Tp;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
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
    private final VortexRealm plugin;
    private final Map<UUID, BotManager.Fight> fights = new HashMap<>();
    private final Map<UUID, PracticeBot> byBody = new HashMap<>();
    private BotArenaUI arenaUi;

    public BotManager(VortexRealm plugin) {
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

        if (kit.id.equals("mole")) {
            p.sendMessage(Text.mm("<red>There is no bot for Mole PvP yet (bots can't dig). <gray>Try <white>/learn mole</white> for the lessons."));
            return false;
        }

        if (!Cfg.bool(Opt.BOTS_ENABLED)) {
            p.sendMessage(Text.mm("<red>Bot fights are turned off on this server."));
            return false;
        }

        ArenaInstance inst = this.flat(p);
        if (inst == null) {
            inst = arena != null && arena.allows(kit) ? this.plugin.arenas().acquire(arena, p) : this.plugin.arenas().acquireAny(kit, p);
        }

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
        start.setY(groundY(start, p1.getY()));
        if (!inst.contains(start) || start.getY() - p1.getY() > 12.0 || p1.getY() - start.getY() > 12.0) {
            start = inst.spawn2();
        }

        start.setDirection(p1.toVector().subtract(start.toVector()));
        PracticeBot bot = new PracticeBot(this.plugin, start, kit, diff, null, diff.label + " Bot");
        bot.target = p;
        bot.attackReady = 60;
        bot.holdUntil = 60;
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

        if (!Cfg.bool(Opt.BOTS_TRAINING)) {
            p.sendMessage(Text.mm("<red>Training is turned off on this server."));
            return false;
        }

        Kit kit = this.plugin.kits().get(type.kit);
        if (kit == null && type.kit.equals("spear_mace")) {
            kit = this.plugin.kits().get("spearmace");
        }

        if (kit == null) {
            kit = this.plugin.kits().all().stream().filter(k -> k.enabled).findFirst().orElse(null);
        }

        ArenaInstance inst = this.flat(p);
        if (inst == null) {
            inst = kit == null ? null : this.plugin.arenas().acquireAny(kit, p);
        }
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
        if (type == DummyType.ANCHOR) {
            p.getInventory().addItem(new ItemStack(Material.RESPAWN_ANCHOR, 32), new ItemStack(Material.GLOWSTONE, 64));
        } else if (type == DummyType.PEARL) {
            p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 16), new ItemStack(Material.ENDER_PEARL, 16));
        } else if (type == DummyType.POT) {
            p.setMaximumNoDamageTicks(10);
        }

        this.trainingItems(p, type);
        Vector dir = spawn.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(1, 0, 0);
        }

        // the archery target walks far away; every other dummy stands on the pad 6 blocks in front of you
        Location at = spawn.clone().add(dir.normalize().multiply(type == DummyType.BOW ? 22.0 : 6.0));
        at.setY(groundY(at, spawn.getY()));
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

    /**
     * The floor under a spot near the height {@code near}: the first solid block found going down from a little
     * above it. Barriers don't count (the maps have an invisible roof, which is the "highest block").
     */
    public static double groundY(Location at, double near) {
        org.bukkit.World w = at.getWorld();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        int top = (int)Math.floor(near) + 6;
        for (int y = top; y > Math.max(w.getMinHeight(), (int)near - 30); y--) {
            org.bukkit.block.Block b = w.getBlockAt(x, y, z);
            Material m = b.getType();
            if (m.isSolid() && m != Material.BARRIER && w.getBlockAt(x, y + 1, z).isPassable() && w.getBlockAt(x, y + 2, z).isPassable()) {
                return y + 1.0;
            }
        }

        return near;
    }

    /** A copy of the flat Training Grounds map (bots.flat-maps), or null to use the kit's maps. */
    private ArenaInstance flat(Player p) {
        if (!Cfg.bool(Opt.BOTS_FLAT_MAPS)) {
            return null;
        }

        for (Arena a : this.plugin.arenas().all()) {
            if (a.theme == net.vortexrealm.arena.ArenaTheme.TRAINING_GROUNDS && a.ready() && a.freeInstances() > 0L) {
                ArenaInstance i = this.plugin.arenas().acquire(a, p);
                if (i != null) {
                    return i;
                }
            }
        }

        return null;
    }

    /** What each training needs on top of its kit. */
    private void trainingItems(Player p, DummyType type) {
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        switch (type) {
            case ANCHOR -> inv.addItem(new ItemStack(Material.RESPAWN_ANCHOR, 32), new ItemStack(Material.GLOWSTONE, 64));
            case PEARL -> inv.addItem(new ItemStack(Material.ENDER_PEARL, 16), new ItemStack(Material.ENDER_PEARL, 16));
            case POT -> p.setMaximumNoDamageTicks(10);
            case DTAP -> {
                // the d-tap sword knocks back (Knockback I), like the practice servers' d-tap kits
                for (ItemStack it : inv.getContents()) {
                    if (!Items.isEmpty(it) && it.getType().name().endsWith("_SWORD")) {
                        it.editMeta(m -> m.addEnchant(org.bukkit.enchantments.Enchantment.KNOCKBACK, 1, true));
                    }
                }

                inv.addItem(new ItemStack(Material.OBSIDIAN, 64), new ItemStack(Material.END_CRYSTAL, 64));
            }
            case BOW -> inv.addItem(new ItemStack(Material.ARROW, 64));
            default -> {
            }
        }

        if (type == DummyType.MACE || type == DummyType.ELYTRA || type == DummyType.STUNSLAM || type == DummyType.AIR_SLAM || type == DummyType.ELYTRA_SHIELD
            || type == DummyType.SPEAR_SLAM || type == DummyType.SPEAR_MACE) {
            inv.addItem(new ItemStack(Material.WIND_CHARGE, 64));
        }

        if (type == DummyType.ELYTRA || type == DummyType.ELYTRA_SHIELD) {
            inv.setChestplate(new ItemStack(Material.ELYTRA));
            inv.addItem(Items.of(Material.FIREWORK_ROCKET).build().asQuantity(32));
        }

        if (type == DummyType.STUNSLAM || type == DummyType.AIR_SLAM || type == DummyType.ELYTRA_SHIELD) {
            // the axe has to be in the hotbar to break the shield (the mace kit keeps it in the inventory)
            this.toHotbar(inv, "_AXE", Material.NETHERITE_AXE);
            this.toHotbar(inv, "MACE", Material.MACE);
        }

        if (type == DummyType.SPEAR_SLAM || type == DummyType.SPEAR_MACE || type == DummyType.SPEAR_LUNGE) {
            this.toHotbar(inv, "_SPEAR", Material.NETHERITE_SPEAR);
        }

        if (type == DummyType.SPEAR_SLAM || type == DummyType.SPEAR_MACE) {
            this.toHotbar(inv, "MACE", Material.MACE);
        }

        p.updateInventory();
    }

    /** Puts the first item whose type ends with {@code suffix} into the hotbar (or gives a new one). */
    private void toHotbar(org.bukkit.inventory.PlayerInventory inv, String suffix, Material fallback) {
        int at = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (!Items.isEmpty(it) && it.getType().name().endsWith(suffix)) {
                at = i;
                break;
            }
        }

        if (at >= 0 && at < 9) {
            return;
        }

        int free = -1;
        for (int i = 8; i >= 0; i--) {
            if (Items.isEmpty(inv.getItem(i))) {
                free = i;
                break;
            }
        }

        int slot = free >= 0 ? free : 8;
        ItemStack moving = at >= 0 ? inv.getItem(at) : new ItemStack(fallback);
        ItemStack old = inv.getItem(slot);
        inv.setItem(slot, moving);
        if (at >= 0) {
            inv.setItem(at, old);
        } else if (!Items.isEmpty(old)) {
            inv.addItem(old);
        }
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
                net.vortexrealm.util.Cuboid c = f.inst.bounds();
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
        try {
            if (f.player.isOnline()) {
                this.arenaUi().record(f.player, f.bot, f.kit, winner != null, why);
            }
        } catch (RuntimeException var6) {
            this.plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not save the bot record", (Throwable)var6);
        }

        this.fights.remove(f.player.getUniqueId());
        this.byBody.remove(f.bot.body.getUniqueId());
        PracticeBot b = f.bot;
        if (b.dummy == null) {
            this.plugin.getLogger().info("Bot fight over (" + f.kit.id + ", " + b.diff.name() + ", " + why + "): hits " + b.hitsGiven + ", crystals " + b.usedCrystals + ", d-taps "
                + b.usedDtaps + ", anchors " + b.usedAnchors + ", wind " + b.usedWind + ", pearls " + b.usedPearls + ", rod " + b.usedRod + ", webs " + b.usedWebs + ", lava "
                + b.usedLava + ", carts " + b.usedCarts + ", creepers " + b.usedCreepers + ", lunges " + b.usedLunges + ", tridents " + b.usedTridents);
        }

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
                String special = switch (b.dummy) {
                    case DTAP -> "<gray>D-taps <green>" + b.dtaps + "</green> <dark_gray>|</dark_gray> fastest <white>" + (b.bestPopGap < 0 ? "-" : b.bestPopGap + " ms");
                    case ANCHOR -> "<gray>Anchor damage <white>" + String.format("%.0f", b.anchorDamage);
                    case STUNSLAM -> "<gray>Stun slams <green>" + b.slams;
                    case PEARL -> "<gray>Pearls landed <green>" + b.pearlsLanded + "</green>/" + b.pearlsThrown;
                    case POT -> "<gray>Pots thrown <white>" + b.potsThrown;
                    case SPEAR -> "<gray>Best spear hit <white>" + String.format("%.1f", b.bestSpear);
                    case AIR_SLAM, ELYTRA_SHIELD, SPEAR_SLAM -> "<gray>Stun slams <green>" + b.slams + "</green> <dark_gray>|</dark_gray> shield breaks <white>" + b.shieldBreaks;
                    case SPEAR_MACE -> "<gray>Spear → mace combos <green>" + b.spearMaces;
                    case SPEAR_LUNGE -> "<gray>Lunges <green>" + b.lunges + "</green> <dark_gray>|</dark_gray> longest <white>" + String.format("%.1f", b.bestLunge) + " blocks";
                    case WTAP -> "<gray>Best w-tap streak <green>" + b.bestWtap;
                    case CRIT -> "<gray>Crits <green>" + b.crits + "</green> <dark_gray>|</dark_gray> best <white>" + String.format("%.1f", b.bestCrit);
                    case BOW -> "<gray>Arrow hits <green>" + b.arrowHits + "</green> <dark_gray>|</dark_gray> longest <white>" + String.format("%.0f", b.bestShot) + " blocks";
                    default -> null;
                };
                if (special != null) {
                    p.sendMessage(Text.mm(special));
                }

                p.setMaximumNoDamageTicks(20);
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

        if (b.dummy == DummyType.ANCHOR && e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            b.anchorDamage += e.getFinalDamage();
            b.hitsTaken++;
            b.damageTaken += e.getFinalDamage();
        }

        boolean totem = b.body.getEquipment().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING
            || b.body.getEquipment().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING;
        if (b.dummy != null && b.body.getHealth() - e.getFinalDamage() <= 0.0 && (!b.dummy.crystalLike() || !totem)) {
            // dummies don't die: full health again
            e.setDamage(0.0);
            Tasks.later(1L, () -> {
                if (b.valid()) {
                    b.body.setHealth(20.0);
                }
            });
        }
    }

    // counted even when another plugin cancels the hit: a training stat must not depend on that
    @EventHandler(priority = EventPriority.MONITOR)
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
                boolean arrow = e.getDamager() instanceof org.bukkit.entity.AbstractArrow;
                b.hitBy(p, e.getFinalDamage(), !arrow && hand.getType().name().endsWith("_AXE"), arrow);
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
        } else if (e.getEntity() instanceof Player p) {
            // the player's totem popped: the bot may go for the d-tap
            Fight f = this.fights.get(p.getUniqueId());
            if (f != null) {
                f.bot.onTargetPop();
            }
        }
    }

    @EventHandler
    public void onPearlLand(org.bukkit.event.entity.ProjectileHitEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.EnderPearl pearl && pearl.getShooter() instanceof Player p) {
            Fight f = this.fights.get(p.getUniqueId());
            if (f != null && f.bot.dummy == DummyType.PEARL && f.bot.valid()) {
                double dist = pearl.getLocation().distance(f.bot.body.getLocation());
                f.bot.pearlLanded(dist);
                p.sendActionBar(Text.mm(dist <= 3.0 ? "<green>✔ " + String.format("%.1f", dist) + " blocks from the target" : "<red>✖ " + String.format("%.1f", dist) + " blocks away"));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotion(org.bukkit.event.entity.PotionSplashEvent e) {
        if (e.getEntity().getShooter() instanceof Player p) {
            Fight f = this.fights.get(p.getUniqueId());
            if (f != null && f.bot.dummy != null) {
                f.bot.potsThrown++;
            }
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

    /** The Bot Arena screen on the Fight tab (optionally filtered by a search; {@code arena} = a Bot Hall map). */
    public void kitMenu(Player p, String search, Arena arena) {
        this.arenaUi().fightTab(p, search, arena);
    }

    public BotArenaUI arenaUi() {
        if (this.arenaUi == null) {
            this.arenaUi = new BotArenaUI(this.plugin, this);
        }

        return this.arenaUi;
    }

    /** What the bot does with this kit, read from the same items its AI looks at. */
    String skillsOf(Kit k) {
        List<String> s = new ArrayList<>();
        boolean mace = k.countOf(Material.MACE) > 0;
        boolean axe = false;
        boolean spear = false;
        boolean heal = false;
        boolean webs = false;
        boolean blocksOk = k.build;
        for (ItemStack it : k.contents) {
            if (!Items.isEmpty(it)) {
                String n = it.getType().name();
                axe |= n.endsWith("_AXE");
                spear |= n.endsWith("_SPEAR");
                webs |= n.equals("COBWEB");
                heal |= n.equals("SPLASH_POTION") && it.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta pm && pm.getBasePotionType() != null
                    && pm.getBasePotionType().name().contains("HEALING");
            }
        }

        if (k.countOf(Material.END_CRYSTAL) > 0) {
            s.add("crystals + d-tap");
        }

        if (k.countOf(Material.RESPAWN_ANCHOR) > 0 && k.countOf(Material.GLOWSTONE) > 0) {
            s.add("anchors");
        }

        if (mace && k.countOf(Material.WIND_CHARGE) > 0) {
            s.add("wind smash");
        }

        if (mace && axe) {
            s.add("stun slam");
        }

        if (mace && k.countOf(Material.ELYTRA) > 0) {
            s.add("elytra dive");
        }

        if (spear) {
            s.add("spear lunge");
        }

        if (k.countOf(Material.TRIDENT) > 0) {
            s.add("trident throws");
        }

        if (axe && !mace) {
            s.add("axe shield-break");
        }

        if (k.countOf(Material.SHIELD) > 0) {
            s.add("shield");
        }

        if (k.countOf(Material.ENDER_PEARL) > 0) {
            s.add("pearls");
        }

        if (k.countOf(Material.FISHING_ROD) > 0) {
            s.add("rod");
        }

        if (blocksOk && k.countOf(Material.LAVA_BUCKET) > 0) {
            s.add("lava");
        }

        if (blocksOk && webs) {
            s.add("cobwebs");
        }

        if (blocksOk && k.countOf(Material.TNT_MINECART) > 0) {
            s.add("TNT carts");
        }

        if (k.countOf(Material.CREEPER_SPAWN_EGG) > 0) {
            s.add("creepers");
        }

        if (heal) {
            s.add("pots");
        }

        if (k.countOf(Material.GOLDEN_APPLE) + k.countOf(Material.ENCHANTED_GOLDEN_APPLE) > 0) {
            s.add("gapples");
        }

        if (k.countOf(Material.BOW) + k.countOf(Material.CROSSBOW) > 0) {
            s.add("bow");
        }

        if (k.countOf(Material.TOTEM_OF_UNDYING) > 0) {
            s.add("totems");
        }

        s.add("crits, strafing, w-tap");
        return String.join(", ", s);
    }

    public void difficultyMenu(Player p, Kit k, Arena arena) {
        this.arenaUi().difficulty(p, k, arena);
    }

    /** The training tabs, opened on the group of today's featured training. */
    public void dummyMenu(Player p) {
        String g = BotArenaUI.featured().group();
        this.arenaUi().open(p, BotArenaUI.Tab.valueOf(g), null);
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

        if (COMING_SOON || !Cfg.bool(Opt.BOTS_ENABLED) && !Cfg.bool(Opt.BOTS_TRAINING)) {
            p.sendMessage(Text.mm("<red>Practice bots and training are turned off on this server."));
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
