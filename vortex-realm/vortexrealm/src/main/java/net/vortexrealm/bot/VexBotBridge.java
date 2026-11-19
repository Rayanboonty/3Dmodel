package net.vortexrealm.bot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.title.Title;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.arena.ArenaInstance;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

/**
 * 3.13.0 - uses the VexBot plugin's fake-player bots for bot fights, when Settings -> Bots -> "VexBot Bots" is on and
 * a VexBot build made for this Minecraft version is installed. Everything goes through VexBot's public API by
 * reflection (no VexBot code is copied); if VexBot is missing, made for another version or fails, the built-in bots
 * are used as always.
 */
public final class VexBotBridge implements Listener {
    private final VortexRealm plugin;
    private Object server;
    private Constructor<?> vec3;
    private Method spawn;
    private Method despawn;
    private Method hunt;
    private Method isBot;
    private boolean ready;
    private String why = "not started";
    private final Map<String, VFight> byBot = new HashMap<>();
    private final Map<UUID, VFight> byPlayer = new HashMap<>();

    static final class VFight {
        final Player player;
        final String bot;
        final Kit kit;
        final ArenaInstance inst;
        final Location start;
        int seconds;
        boolean over;

        VFight(Player player, String bot, Kit kit, ArenaInstance inst, Location start) {
            this.player = player;
            this.bot = bot;
            this.kit = kit;
            this.inst = inst;
            this.start = start;
        }
    }

    public VexBotBridge(VortexRealm plugin) {
        this.plugin = plugin;
    }

    /** Looks VexBot up (again): call on start and when the setting changes. */
    public void connect() {
        this.ready = false;
        Plugin vb = Bukkit.getPluginManager().getPlugin("VexBot");
        if (vb == null || !vb.isEnabled()) {
            this.why = "VexBot is not installed";
            return;
        }

        try {
            ClassLoader cl = vb.getClass().getClassLoader();
            Class<?> api = Class.forName("vexbot.api.VexBotApi", true, cl);
            Class<?> events = Class.forName("vexbot.api.VexBotEvents", true, cl);
            Class<?> listener = Class.forName("vexbot.api.VexBotEvents$PlayerDeathListener", true, cl);
            this.server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            Class<?> mcServer = Class.forName("net.minecraft.server.MinecraftServer", true, this.server.getClass().getClassLoader());
            Class<?> vec = Class.forName("net.minecraft.world.phys.Vec3", true, this.server.getClass().getClassLoader());
            this.vec3 = vec.getConstructor(double.class, double.class, double.class);
            this.spawn = api.getMethod("spawnBot", mcServer, String.class, vec, float.class, String.class);
            this.despawn = api.getMethod("despawnBot", mcServer, String.class);
            this.hunt = api.getMethod("hunt", String.class, String.class);
            this.isBot = api.getMethod("isBot", mcServer, String.class);
            Object proxy = Proxy.newProxyInstance(cl, new Class<?>[]{listener}, (o, m, args) -> {
                if (m.getName().equals("onDeath") && args != null && args.length >= 1 && args[0] != null) {
                    Object bukkit = args[0].getClass().getMethod("getBukkitEntity").invoke(args[0]);
                    if (bukkit instanceof Player dead) {
                        Tasks.ensureSync(() -> this.died(dead.getName()));
                    }
                } else if (m.getName().equals("hashCode")) {
                    return System.identityHashCode(o);
                } else if (m.getName().equals("equals")) {
                    return o == args[0];
                } else if (m.getName().equals("toString")) {
                    return "VortexRealm VexBot bridge";
                }

                return null;
            });
            events.getMethod("registerDeathListener", listener).invoke(null, proxy);
            this.ready = true;
            this.why = "connected (VexBot " + vb.getPluginMeta().getVersion() + ")";
            this.plugin.getLogger().info("VexBot bots: " + this.why);
        } catch (Throwable t) {
            this.why = "VexBot does not fit this server (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ") - install the VexBot build for this Minecraft version";
            this.plugin.getLogger().warning("VexBot bots off: " + this.why);
        }
    }

    public boolean active() {
        return this.ready && Cfg.bool(Opt.BOTS_VEXBOT);
    }

    public String status() {
        return this.ready ? this.why : "off: " + this.why;
    }

    /** Starts a fight against a VexBot bot on a map that is already the player's. False = use a built-in bot. */
    public boolean fight(Player p, Kit kit, ArenaInstance inst, Location start) {
        if (!this.active()) {
            return false;
        }

        String name = "Bot" + ThreadLocalRandom.current().nextInt(1000, 9999);
        try {
            Object pos = this.vec3.newInstance(start.getX(), start.getY(), start.getZ());
            String dim = start.getWorld().getKey().toString();
            VFight f = new VFight(p, name, kit, inst, start);
            this.byBot.put(name.toLowerCase(java.util.Locale.ROOT), f);
            boolean ok = (Boolean)this.spawn.invoke(null, this.server, name, pos, start.getYaw(), dim);
            if (!ok) {
                this.byBot.remove(name.toLowerCase(java.util.Locale.ROOT));
                return false;
            }

            this.byPlayer.put(p.getUniqueId(), f);
            // VexBot's player joins like a real one: put it back on its spot, give it the kit, then let it hunt
            Tasks.later(3L, () -> {
                Player b = Bukkit.getPlayerExact(name);
                if (b != null && !f.over) {
                    b.teleport(start);
                    this.plugin.kits().apply(b, kit, null, List.of());
                    b.setHealth(b.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
                }
            });
            Tasks.later(60L, () -> {
                if (!f.over) {
                    try {
                        this.hunt.invoke(null, name, p.getName());
                    } catch (ReflectiveOperationException e) {
                        this.plugin.getLogger().warning("VexBot hunt failed: " + e.getMessage());
                    }
                }
            });
            return true;
        } catch (Throwable t) {
            this.plugin.getLogger().warning("VexBot spawn failed, using a built-in bot: " + t.getMessage());
            this.byBot.remove(name.toLowerCase(java.util.Locale.ROOT));
            return false;
        }
    }

    public boolean inFight(Player p) {
        return this.byPlayer.containsKey(p.getUniqueId());
    }

    private void died(String name) {
        VFight f = this.byBot.get(name.toLowerCase(java.util.Locale.ROOT));
        if (f != null) {
            this.end(f, true, "bot down");
        }
    }

    /** Every second: fights whose player left or ran out of time end. */
    public void second() {
        for (VFight f : List.copyOf(this.byPlayer.values())) {
            f.seconds++;
            if (!f.player.isOnline() || this.plugin.sessions().get(f.player).state != PlayerState.BOT) {
                this.end(f, false, "left");
            } else if (f.seconds > 300) {
                this.end(f, false, "time");
            }
        }
    }

    public void end(VFight f, boolean won, String why) {
        if (f.over) {
            return;
        }

        f.over = true;
        this.byPlayer.remove(f.player.getUniqueId());
        Tasks.later(why.equals("bot down") ? 30L : 1L, () -> {
            this.byBot.remove(f.bot.toLowerCase(java.util.Locale.ROOT));
            try {
                this.despawn.invoke(null, this.server, f.bot);
            } catch (Throwable t) {
                Player b = Bukkit.getPlayerExact(f.bot);
                if (b != null) {
                    b.kick();
                }
            }
        });
        this.plugin.arenas().release(f.inst);
        Player p = f.player;
        if (p.isOnline()) {
            if (!why.equals("left")) {
                p.showTitle(Title.title(Text.mm(won ? "<green><b>VICTORY" : "<red><b>DEFEAT"), Text.mm("<gray>vs VexBot")));
                Snd.play(p, won ? "ui.toast.challenge_complete" : "entity.wither.death", 0.6F, 1.0F);
                p.sendMessage(Text.mm("<click:run_command:'/bot " + f.kit.id + "'><green><b>[Rematch]</b></green></click>"));
            }

            Tasks.later(40L, () -> {
                if (p.isOnline() && this.plugin.sessions().get(p).state == PlayerState.BOT) {
                    this.plugin.lobby().sendToLobby(p);
                }
            });
        }
    }

    /** The bot's join: marked as an NPC (other plugins skip it) and no join message. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBotJoin(PlayerJoinEvent e) {
        if (this.byBot.containsKey(e.getPlayer().getName().toLowerCase(java.util.Locale.ROOT)) || this.isVexBot(e.getPlayer())) {
            e.getPlayer().setMetadata("NPC", new FixedMetadataValue(this.plugin, true));
            e.joinMessage(null);
        }
    }

    public boolean isVexBot(Player p) {
        if (!this.ready) {
            return false;
        }

        try {
            return (Boolean)this.isBot.invoke(null, this.server, p.getName());
        } catch (Throwable t) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBotQuit(PlayerQuitEvent e) {
        if (e.getPlayer().hasMetadata("NPC") && this.byBot.containsKey(e.getPlayer().getName().toLowerCase(java.util.Locale.ROOT))) {
            e.quitMessage(null);
        }
    }

    /** The player is never really killed by a VexBot: a deadly hit ends the fight. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && this.byPlayer.containsKey(p.getUniqueId()) && e.getFinalDamage() >= p.getHealth()
            && p.getInventory().getItemInMainHand().getType() != org.bukkit.Material.TOTEM_OF_UNDYING
            && p.getInventory().getItemInOffHand().getType() != org.bukkit.Material.TOTEM_OF_UNDYING) {
            e.setCancelled(true);
            this.end(this.byPlayer.get(p.getUniqueId()), false, "killed");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBotDeath(PlayerDeathEvent e) {
        this.died(e.getPlayer().getName());
    }

    public void shutdown() {
        for (VFight f : List.copyOf(this.byPlayer.values())) {
            this.end(f, false, "left");
        }
    }
}
