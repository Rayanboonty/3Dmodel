package net.practicepvp.survival.smp;

import net.practicepvp.PracticePlugin;
import net.practicepvp.survival.VortexSMP;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * The SMP's view of the main plugin: its world modes (HUB, PVP, SURVIVAL, ECO...), the practice worlds and the
 * hub. The SMP always runs on this server, next to Practice.
 */
public final class Realm {
    private final VortexSMP plugin;

    public Realm(VortexSMP plugin) {
        this.plugin = plugin;
    }

    private PracticePlugin host() {
        return this.plugin.host();
    }

    public PracticePlugin plugin() {
        return this.host();
    }

    public boolean on() {
        return true;
    }

    public String prefix() {
        return this.host().getName().toLowerCase(java.util.Locale.ROOT);
    }

    public WorldMode mode(World w) {
        if (w == null || this.host().modes() == null) {
            return WorldMode.NONE;
        }

        return WorldMode.parse(this.host().modes().mode(w).name(), WorldMode.NONE);
    }

    public boolean managed(World w) {
        return w != null && this.host().worlds().managed(w);
    }

    public boolean marked(Player p) {
        return this.host().worlds().isMarked(p);
    }

    public World hubWorld() {
        return this.host().modes() == null ? null : this.host().modes().hubWorld();
    }

    public World smpWorld() {
        return this.host().modes() == null ? null : this.host().modes().smpWorld();
    }

    public Location spawn(World w) {
        return this.host().modes() == null ? null : this.host().modes().spawn(w);
    }

    public void setSpawn(Location l) {
        if (this.host().modes() != null) {
            this.host().modes().setSpawn(l);
        }
    }

    /** /hub: the main plugin's lobby (the inventory switches back to the hub one). */
    public void toHub(Player p) {
        p.performCommand("hub");
    }

    public boolean smp(World w) {
        return w != null && !this.managed(w) && this.mode(w).smp();
    }
}
