package net.practicepvp.survival.smp;

import java.util.List;
import net.practicepvp.survival.VortexSMP;
import net.practicepvp.survival.util.Locs;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * On a dedicated SMP server every world is survival: homes and warps are kept under the SURVIVAL group
 * (the same layout the old VortexRealm SMP used, so its files can be imported as they are).
 */
public final class ModeManager {
    private final VortexSMP plugin;

    public ModeManager(VortexSMP plugin) {
        this.plugin = plugin;
    }

    public WorldMode mode(World w) {
        // same server as VortexRealm: its world modes (HUB, PVP, SURVIVAL, ECO...)
        return this.plugin.realm().on() ? this.plugin.realm().mode(w) : WorldMode.SURVIVAL;
    }

    /** /spawn: the spot set with /setspawn (in the main world), otherwise the world spawn. */
    public Location spawn(World w) {
        Realm realm = this.plugin.realm();
        if (realm.on()) {
            // the spawn of the SMP world the player is in (or of the main SMP world)
            World base = this.parentOf(w);
            if (base == null || !realm.smp(base)) {
                base = realm.smpWorld();
            }

            Location own = Locs.parse(this.plugin.getConfig().getString("spawn"));
            if (own != null && own.getWorld() != null && own.getWorld().equals(base)) {
                return own;
            }

            Location rl = base == null ? null : realm.spawn(base);
            if (rl != null) {
                return rl;
            }
        }

        Location l = Locs.parse(this.plugin.getConfig().getString("spawn"));
        if (l != null && l.getWorld() != null) {
            return l;
        }

        World main = Bukkit.getWorlds().get(0);
        return main.getSpawnLocation().add(0.5, 0.0, 0.5);
    }

    public void setSpawn(Location l) {
        this.plugin.getConfig().set("spawn", Locs.serialize(l));
        this.plugin.saveConfig();
        l.getWorld().setSpawnLocation(l);
        if (this.plugin.realm().on() && this.plugin.realm().smp(l.getWorld())) {
            // deaths without a bed respawn here too
            this.plugin.realm().setSpawn(l);
        }
    }

    public World hubWorld() {
        return this.plugin.realm().on() ? this.plugin.realm().hubWorld() : null;
    }

    /** The overworld a nether or end belongs to. */
    public World parentOf(World w) {
        if (w != null) {
            String n = w.getName();

            for (String suffix : List.of("_nether", "_the_end")) {
                if (n.endsWith(suffix)) {
                    World parent = Bukkit.getWorld(n.substring(0, n.length() - suffix.length()));
                    if (parent != null) {
                        return parent;
                    }
                }
            }
        }

        return w;
    }

    public static String worldLabel(World w) {
        return switch (w.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "The End";
            default -> w.getName();
        };
    }
}
