package net.vortexsmp.smp;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.logging.Level;
import net.vortexsmp.VortexSMP;
import net.vortexsmp.config.Cfg;
import net.vortexsmp.config.Opt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The SMP on the same server as VortexRealm / PracticePvP ("same-server mode"). VortexRealm owns the worlds, the
 * selector and every inventory (each SMP world group keeps its own, saved and backed up); VortexSMP adds homes, tpa,
 * rtp, warps, back, combat tag, the sidebar, drills, /trade and /market inside the SMP worlds. Everything is read from
 * VortexRealm by name, so it works with every rebrand of it.
 */
public final class Realm {
    private static final String[] NAMES = {"VortexRealm", "PracticePvP", "VortexPvP"};
    private final VortexSMP plugin;
    private Plugin realm;
    private boolean warned;

    public Realm(VortexSMP plugin) {
        this.plugin = plugin;
    }

    /** The practice plugin on this server, or null. */
    public Plugin plugin() {
        if (this.realm == null || !this.realm.isEnabled()) {
            this.realm = null;
            for (String n : NAMES) {
                Plugin p = Bukkit.getPluginManager().getPlugin(n);
                if (p != null && p.isEnabled()) {
                    this.realm = p;
                    break;
                }
            }
        }

        return this.realm;
    }

    /** True while VortexRealm (or PracticePvP) runs on this server and network.same-server is on. */
    public boolean on() {
        return Cfg.bool(Opt.SAME_SERVER) && this.plugin() != null;
    }

    /** "vortexrealm" - for its commands ("vortexrealm:hub"). */
    public String prefix() {
        Plugin p = this.plugin();
        return p == null ? "" : p.getName().toLowerCase(Locale.ROOT);
    }

    private Object call(Object target, String name, Class<?>[] types, Object... args) {
        if (target == null) {
            return null;
        }

        try {
            Method m = target.getClass().getMethod(name, types);
            return m.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!this.warned) {
                this.warned = true;
                this.plugin.getLogger().log(Level.WARNING, "Same-server mode: couldn't ask " + this.prefix() + " for " + name + " (update both plugins)", e);
            }

            return null;
        }
    }

    private Object modes() {
        return this.call(this.plugin(), "modes", new Class<?>[0]);
    }

    private Object worlds() {
        return this.call(this.plugin(), "worlds", new Class<?>[0]);
    }

    /** VortexRealm's mode of a world (PVP, HUB, SURVIVAL, ECO..., NONE). */
    public WorldMode mode(World w) {
        Object m = this.call(this.modes(), "mode", new Class<?>[]{World.class}, w);
        return m instanceof Enum<?> e ? WorldMode.parse(e.name(), WorldMode.NONE) : WorldMode.NONE;
    }

    /** A PvP practice world (arenas, the practice lobby). */
    public boolean managed(World w) {
        return Boolean.TRUE.equals(this.call(this.worlds(), "managed", new Class<?>[]{World.class}, w));
    }

    /** The player's practice inventory is in (their own one is stored away). */
    public boolean marked(Player p) {
        return Boolean.TRUE.equals(this.call(this.worlds(), "isMarked", new Class<?>[]{Player.class}, p));
    }

    public World hubWorld() {
        return this.call(this.modes(), "hubWorld", new Class<?>[0]) instanceof World w ? w : null;
    }

    /** The main SMP world (the survival one). */
    public World smpWorld() {
        return this.call(this.modes(), "smpWorld", new Class<?>[0]) instanceof World w ? w : null;
    }

    public Location spawn(World w) {
        return this.call(this.modes(), "spawn", new Class<?>[]{World.class}, w) instanceof Location l ? l : null;
    }

    public void setSpawn(Location l) {
        this.call(this.modes(), "setSpawn", new Class<?>[]{Location.class}, l);
    }

    /** /hub on the same server: VortexRealm's own /hub (the inventory switches back to the hub one). */
    public void toHub(Player p) {
        p.performCommand(this.prefix() + ":hub");
    }

    /** True for an SMP world (any SMP mode, its nether and end included). */
    public boolean smp(World w) {
        return w != null && !this.managed(w) && this.mode(w).smp();
    }
}
