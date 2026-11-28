package net.cmcpvp.smp;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.cmcpvp.CMCPvP;
import net.cmcpvp.config.Cfg;
import net.cmcpvp.config.Opt;
import net.cmcpvp.util.Snd;
import net.cmcpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.permissions.PermissionAttachment;

/**
 * 3.11.0 - everyone is equal in the SMP. Inside SMP worlds nobody has admin powers or rank perks, not even operators
 * or the owner: only normal player commands work (homes, tpa, rtp, warps, trade, market, chat...), the game mode is
 * always survival, nobody can fly, and perk permissions (more homes, teleport bypass, warp and claim admin) are switched
 * off while there. Rank names still show in chat and tab. Admin work is done from the lobby, the panel or the console.
 * Settings -> SMP -> "Everyone Equal in SMP" turns it off.
 */
public final class SmpLockdown implements Listener {
    /**
     * 3.13.2: what nobody can use in the SMP (a block-list: every normal player command works, /enderchest, /craft,
     * /hat, /homes, clans, daily rewards...). Cheats and world commands of the game and the usual admin plugins, our
     * staff tools, and (found at start) every command of this plugin that needs an admin or staff permission.
     */
    private static final Set<String> BLOCKED = Set.of(
        "gamemode", "gm", "gmc", "gms", "gma", "gmsp", "creative", "adventure", "spectator", "gamerule", "defaultgamemode", "difficulty",
        "give", "i", "item", "more", "tp", "teleport", "tphere", "tpall", "tpo", "tppos", "tpohere", "top", "jump", "j", "put", "effect", "enchant",
        "xp", "exp", "experience", "time", "day", "night", "ptime", "weather", "sun", "rain", "thunder", "kill", "killall", "butcher", "summon",
        "spawnmob", "mob", "fill", "setblock", "clone", "attribute", "data", "execute", "function", "scoreboard", "loot", "place", "spreadplayers",
        "setworldspawn", "spawnpoint", "forceload", "worldborder", "tick", "damage", "ride", "fly", "god", "heal", "feed", "speed", "flyspeed",
        "walkspeed", "vanish", "v", "invsee", "repair", "fix", "unlimited", "sudo", "op", "deop", "reload", "rl", "stop", "restart", "whitelist",
        "ban", "ban-ip", "pardon", "kick", "lp", "luckperms", "perm", "perms", "pex", "we", "worldedit", "fawe", "wand", "schem", "schematic",
        "broadcast", "bc", "staff", "staffchat", "sc", "socialspy", "spy", "mutechat", "clearchat", "cc", "mute", "unmute", "mutes", "chatfilter",
        "swearfilter", "chatmod", "alts", "altcheck", "ss", "screenshare", "freeze", "admit", "sentinel", "ac", "anticheat", "ai", "assistant",
        "setspawn", "setwarp", "delwarp"
    );
    private final Set<String> adminCommands = new java.util.HashSet<>();

    /** Perk and admin permissions switched off inside the SMP (also for operators). */
    private static final List<String> PERKS = List.of(
        "cmcsmp.admin", "cmcsmp.staff", "cmcsmp.homes.unlimited", "cmcsmp.teleport.bypass", "cmcsmp.warps.admin",
        "cmcpvp.homes.unlimited", "cmcpvp.homes.4", "cmcpvp.homes.6", "cmcpvp.homes.8", "cmcpvp.homes.10", "cmcpvp.homes.12",
        "cmcpvp.homes.16", "cmcpvp.homes.20", "cmcpvp.teleport.bypass", "cmcpvp.warps.admin", "cmcpvp.claims.admin", "cmcpvp.smpadmin",
        "cmcpvp.ah.unlimited", "cmcpvp.lobbyfly", "cmcpvp.admin", "cmcpvp.staff", "cmcpvp.staff.mod", "cmcpvp.staff.alts",
        "minecraft.command.gamemode", "minecraft.command.give", "minecraft.command.tp", "minecraft.command.teleport", "minecraft.command.gamerule",
        "minecraft.command.effect", "minecraft.command.enchant", "minecraft.command.xp", "minecraft.command.time", "minecraft.command.weather",
        "minecraft.command.kill", "minecraft.command.summon", "minecraft.command.fill", "minecraft.command.setblock", "minecraft.command.clone",
        "minecraft.command.item", "minecraft.command.attribute", "bukkit.command.reload"
    );
    private final CMCPvP plugin;
    private final Map<UUID, PermissionAttachment> off = new HashMap<>();
    /** 3.13.5: who is staff (seen before the SMP takes the perms away). */
    private final Map<UUID, Boolean> staff = new HashMap<>();
    /** Staff spectating in the SMP: where they were. */
    private final Map<UUID, org.bukkit.Location> spectating = new HashMap<>();
    /** 3.14.2: who each staff member is watching (the camera follows them through portals). */
    private final Map<UUID, UUID> watching = new HashMap<>();
    /** 3.14.2: staff -> (player they spectated -> until when they may not hurt or loot that player). */
    private final Map<UUID, Map<UUID, Long>> noHarm = new HashMap<>();
    private static final long NO_HARM_MS = 30L * 60_000L;

    public SmpLockdown(CMCPvP plugin) {
        // 3.13.11: every 2 seconds, anyone still locked outside the SMP gets their perks back
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID u : new java.util.ArrayList<>(this.off.keySet())) {
                Player p = Bukkit.getPlayer(u);
                if (p == null) {
                    this.off.remove(u);
                } else if (!this.on() || !this.inSmp(p)) {
                    this.apply(p);
                }
            }
        }, 40L, 40L);
        this.plugin = plugin;
        // this plugin's admin and staff commands, with their aliases
        for (Map.Entry<String, Map<String, Object>> c : plugin.getDescription().getCommands().entrySet()) {
            Object perm = c.getValue().get("permission");
            String ps = perm == null ? "" : perm.toString();
            if (ps.contains(".admin") || ps.contains(".staff") || ps.contains("smpadmin")) {
                this.adminCommands.add(c.getKey().toLowerCase(Locale.ROOT));
                Object al = c.getValue().get("aliases");
                if (al instanceof List<?> l) {
                    for (Object o : l) {
                        this.adminCommands.add(String.valueOf(o).toLowerCase(Locale.ROOT));
                    }
                } else if (al != null) {
                    this.adminCommands.add(al.toString().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    private boolean on() {
        return Cfg.bool(Opt.SMP_EQUAL);
    }

    private boolean inSmp(Player p) {
        return this.plugin.modes() != null && this.plugin.modes().smpWorld(p.getWorld());
    }

    /** Makes a player a normal survival player for the SMP (or gives the perks back outside it). */
    public void apply(Player p) {
        if (p.hasMetadata("NPC") || p.hasMetadata("cmcpvp-keepalive")) {
            // the keep-online bot and other bots are left alone
            return;
        }

        boolean lock = this.on() && this.inSmp(p);
        PermissionAttachment a = this.off.get(p.getUniqueId());
        if (a == null) {
            this.staff.put(p.getUniqueId(), this.staffNow(p));
        }

        if (!lock && this.spectating.containsKey(p.getUniqueId())) {
            // 3.13.11: left the SMP while spectating (/hub, /lobby...): end the spectate so the perks come back
            org.bukkit.Location startedAt = this.spectating.remove(p.getUniqueId());
            this.watching.remove(p.getUniqueId());
            p.setSpectatorTarget(null);
            if (startedAt != null && this.plugin.modes() != null && this.plugin.modes().smpWorld(startedAt.getWorld())) {
                // left the SMP while spectating: the spot they were watching is not kept (/back, coming back...)
                this.plugin.modes().remember(p, startedAt);
            }

            if (p.getGameMode() == GameMode.SPECTATOR) {
                p.setGameMode(GameMode.SURVIVAL);
            }

            for (Player o : Bukkit.getOnlinePlayers()) {
                o.showPlayer(this.plugin, p);
            }
        }

        if (this.spectating.containsKey(p.getUniqueId())) {
            return;
        }

        if (lock) {
            if (a == null) {
                a = p.addAttachment(this.plugin);
                for (String perm : PERKS) {
                    a.setPermission(perm, false);
                }

                this.off.put(p.getUniqueId(), a);
                p.recalculatePermissions();
                p.updateCommands();
            }

            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.SPECTATOR) {
                p.setGameMode(GameMode.SURVIVAL);
            }

            if (p.getGameMode() == GameMode.SPECTATOR && !p.isDead()) {
                p.setGameMode(GameMode.SURVIVAL);
            }

            p.setFlying(false);
            p.setAllowFlight(false);
        } else if (a != null) {
            this.off.remove(p.getUniqueId());
            try {
                p.removeAttachment(a);
            } catch (IllegalArgumentException ignored) {
                // already gone (quit)
            }

            p.recalculatePermissions();
            p.updateCommands();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (e.getPlayer().isOnline()) {
                this.apply(e.getPlayer());
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        this.apply(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.off.remove(e.getPlayer().getUniqueId());
        org.bukkit.Location back = this.spectating.remove(e.getPlayer().getUniqueId());
        this.watching.remove(e.getPlayer().getUniqueId());
        if (back != null) {
            e.getPlayer().setSpectatorTarget(null);
            e.getPlayer().teleport(back);
            e.getPlayer().setGameMode(GameMode.SURVIVAL);
        }
    }

    // ------------------------------------------------------------------ 3.13.5: staff spectate in the SMP

    private boolean isStaff(Player p) {
        Boolean b = this.staff.get(p.getUniqueId());
        return b != null && b || this.staffNow(p);
    }

    /** 3.13.11: helpers, mods, admins and the owner by server rank, /staff members, or the staff permissions. */
    private boolean staffNow(Player p) {
        if (p.isOp() || p.hasPermission("cmcpvp.staff") || p.hasPermission("cmcpvp.admin") || p.hasPermission("cmcsmp.staff")) {
            return true;
        }

        if (this.plugin.staff() != null && this.plugin.staff().isStaff(p)) {
            return true;
        }

        net.cmcpvp.social.Ranks r = this.plugin.serverRanks();
        net.cmcpvp.social.Ranks.Rank rank = r == null ? null : r.of(p);
        return rank != null && java.util.Set.of("owner", "admin", "dev", "mod", "helper").contains(rank.id);
    }

    /** Spectator mode, hidden from everyone who isn't staff (also from the tab list); again to come back. */
    public void spectate(Player p, String targetName) {
        if (this.spectating.containsKey(p.getUniqueId()) && (targetName == null || targetName.equalsIgnoreCase("stop"))) {
            this.stopSpectate(p);
            return;
        }

        Player t = targetName == null ? null : Bukkit.getPlayerExact(targetName);
        if (targetName != null && t == null) {
            p.sendMessage(Text.mm("<red>" + Text.esc(targetName) + " is not online."));
            return;
        }

        if (!this.spectating.containsKey(p.getUniqueId())) {
            this.spectating.put(p.getUniqueId(), p.getLocation());
            p.setGameMode(GameMode.SPECTATOR);
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (!o.equals(p) && !this.isStaff(o)) {
                    o.hidePlayer(this.plugin, p);
                }
            }
        }

        if (t != null) {
            this.watching.put(p.getUniqueId(), t.getUniqueId());
            this.noHarm.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(t.getUniqueId(), System.currentTimeMillis() + NO_HARM_MS);
            this.follow(p, t);
            this.log(p, "started spectating " + t.getName());
        }

        p.sendMessage(Text.mm("<aqua>Spectating" + (t == null ? "" : " <white>" + Text.esc(t.getName())) + "<aqua>. Only staff can see you. <gray>Shift to look around freely, <white>/spectate</white> to come back."));
        Snd.play(p, "block.beacon.activate", 0.5F, 1.8F);
    }

    /** Puts the camera on the target, in any SMP dimension (overworld, nether, end); retries while the chunk loads. */
    private void follow(Player p, Player t) {
        // a camera locked on someone blocks the teleport: let go first
        p.setSpectatorTarget(null);
        p.teleport(t.getLocation(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        int[] tries = {0};
        Bukkit.getScheduler().runTaskTimer(this.plugin, task -> {
            if (!p.isOnline() || !t.isOnline() || !this.spectating.containsKey(p.getUniqueId()) || ++tries[0] > 10) {
                task.cancel();
                return;
            }

            if (p.getGameMode() != GameMode.SPECTATOR) {
                p.setGameMode(GameMode.SPECTATOR);
            }

            if (!p.getWorld().equals(t.getWorld()) || p.getLocation().distanceSquared(t.getLocation()) > 64) {
                p.teleport(t.getLocation(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                return;
            }

            p.setSpectatorTarget(t);
            if (p.getSpectatorTarget() != null) {
                task.cancel();
            }
        }, 2L, 2L);
    }

    /** The watched player went through a portal or teleported to another world: the camera follows. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTargetWorld(PlayerChangedWorldEvent e) {
        UUID moved = e.getPlayer().getUniqueId();
        for (Map.Entry<UUID, UUID> w : new java.util.ArrayList<>(this.watching.entrySet())) {
            Player staff = Bukkit.getPlayer(w.getKey());
            if (moved.equals(w.getValue()) && staff != null && this.spectating.containsKey(w.getKey())) {
                Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.follow(staff, e.getPlayer()), 5L);
            }
        }
    }

    /** Staff can't use what they saw while spectating: no hurting the players they watched for 30 minutes. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStaffHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }

        org.bukkit.entity.Entity d = e.getDamager();
        Player a = d instanceof Player pl ? pl : d instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player pl ? pl
            : d instanceof org.bukkit.entity.TNTPrimed tnt && tnt.getSource() instanceof Player pl ? pl : null;
        if (a == null || a.equals(victim) || !this.inSmp(victim)) {
            return;
        }

        Map<UUID, Long> m = this.noHarm.get(a.getUniqueId());
        Long until = m == null ? null : m.get(victim.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            e.setCancelled(true);
            a.sendActionBar(Text.mm("<red>You spectated " + Text.esc(victim.getName()) + " recently. <gray>Staff can't fight players they watched ("
                + ((until - System.currentTimeMillis()) / 60_000L + 1) + " min left)."));
        }
    }

    /** ...and can't pick up what those players drop. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStaffLoot(org.bukkit.event.entity.EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || !this.inSmp(p)) {
            return;
        }

        Map<UUID, Long> m = this.noHarm.get(p.getUniqueId());
        UUID thrower = e.getItem().getThrower();
        UUID owner = e.getItem().getOwner();
        if (m == null) {
            return;
        }

        for (UUID u : new UUID[] {thrower, owner, this.lastDeathOf(e.getItem())}) {
            Long until = u == null ? null : m.get(u);
            if (until != null && until > System.currentTimeMillis()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private final Map<UUID, UUID> deathDrops = new HashMap<>();

    private UUID lastDeathOf(org.bukkit.entity.Item it) {
        return this.deathDrops.get(it.getUniqueId());
    }

    /** Remember whose death dropped which items, for the loot rule above. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathDrops(org.bukkit.event.entity.PlayerDeathEvent e) {
        if (!this.inSmp(e.getEntity()) || this.noHarm.isEmpty()) {
            return;
        }

        UUID dead = e.getEntity().getUniqueId();
        org.bukkit.Location l = e.getEntity().getLocation();
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            for (org.bukkit.entity.Entity en : l.getWorld().getNearbyEntities(l, 4, 4, 4)) {
                if (en instanceof org.bukkit.entity.Item it && it.getTicksLived() < 40) {
                    this.deathDrops.put(it.getUniqueId(), dead);
                }
            }
        }, 1L);
        if (this.deathDrops.size() > 5000) {
            this.deathDrops.clear();
        }
    }

    /** Only /spectate and chat-type commands work while spectating (no /back, /home, /tpa, /sethome from a camera spot). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpecCommand(PlayerCommandPreprocessEvent e) {
        if (!this.spectating.containsKey(e.getPlayer().getUniqueId())) {
            return;
        }

        String m = e.getMessage().substring(1);
        String label = (m.indexOf(' ') < 0 ? m : m.substring(0, m.indexOf(' '))).toLowerCase(Locale.ROOT);
        label = label.substring(label.indexOf(':') + 1);
        if (!java.util.Set.of("spectate", "spec", "smpspec", "msg", "tell", "w", "r", "reply", "list", "staffchat", "sc", "report", "ban", "kick", "mute",
            "warn", "tempban", "unmute").contains(label)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Text.mm("<red>Not while spectating. <gray>Type <white>/spectate</white> to go back first."));
        }
    }

    /** Every spectate is written down (plugins/CMCPvP/smp-spectate-log.txt) and shown to online ops. */
    private void log(Player staff, String what) {
        String line = java.time.LocalDateTime.now().withNano(0) + "  " + staff.getName() + " " + what;
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                java.nio.file.Files.writeString(new java.io.File(this.plugin.getDataFolder(), "smp-spectate-log.txt").toPath(), line + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (java.io.IOException ignored) {
                // the log is a nice-to-have
            }
        });
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.isOp() && !o.equals(staff)) {
                o.sendMessage(Text.mm("<dark_gray>[Spectate] <gray>" + Text.esc(staff.getName()) + " " + Text.esc(what)));
            }
        }
    }

    private void stopSpectate(Player p) {
        org.bukkit.Location back = this.spectating.remove(p.getUniqueId());
        this.watching.remove(p.getUniqueId());
        this.log(p, "stopped spectating");
        p.setSpectatorTarget(null);
        if (back != null) {
            p.teleport(back);
        }

        p.setGameMode(GameMode.SURVIVAL);
        for (Player o : Bukkit.getOnlinePlayers()) {
            o.showPlayer(this.plugin, p);
        }

        p.sendMessage(Text.mm("<green>Back where you were."));
    }

    @EventHandler
    public void onJoinHide(PlayerJoinEvent e) {
        if (this.isStaff(e.getPlayer())) {
            return;
        }

        for (UUID u : this.spectating.keySet()) {
            Player s = Bukkit.getPlayer(u);
            if (s != null) {
                e.getPlayer().hidePlayer(this.plugin, s);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!this.on() || !this.inSmp(p)) {
            // 3.13.11: outside the SMP nobody may stay locked: give the perks back before the command runs
            if (this.off.containsKey(p.getUniqueId()) || this.spectating.containsKey(p.getUniqueId())) {
                this.apply(p);
            }

            return;
        }

        String m = e.getMessage().startsWith("/") ? e.getMessage().substring(1) : e.getMessage();
        int sp = m.indexOf(' ');
        String label = (sp < 0 ? m : m.substring(0, sp)).toLowerCase(Locale.ROOT);
        label = label.substring(label.indexOf(':') + 1);
        if ((label.equals("spectate") || label.equals("spec") || label.equals("smpspec")) && this.isStaff(p)) {
            e.setCancelled(true);
            String[] parts = m.split(" ");
            this.spectate(p, parts.length > 1 ? parts[1] : null);
            return;
        }

        // WorldEdit's //wand, //set... start with a second slash
        if (label.startsWith("/") || BLOCKED.contains(label) || this.adminCommands.contains(label)) {
            e.setCancelled(true);
            p.sendMessage(Text.mm("<red>Everyone plays the SMP as a normal player.</red> <gray>/" + Text.esc(label) + " doesn't work here (admin and cheat commands are for Practice and the lobby)."));
            Snd.error(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        if (this.on() && this.inSmp(e.getPlayer()) && e.getNewGameMode() != GameMode.SURVIVAL && !this.spectating.containsKey(e.getPlayer().getUniqueId())
            && e.getCause() != PlayerGameModeChangeEvent.Cause.HARDCORE_DEATH) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFly(PlayerToggleFlightEvent e) {
        if (e.isFlying() && this.on() && this.inSmp(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().setAllowFlight(false);
        }
    }

    /** Every few seconds: anyone in the SMP is checked again (settings changed, worlds linked...). */
    public void sweep() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            this.apply(p);
        }
    }
}
