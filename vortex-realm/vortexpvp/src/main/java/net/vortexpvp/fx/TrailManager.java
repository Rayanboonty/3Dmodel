package net.vortexpvp.fx;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.Setting;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

public final class TrailManager {
    private final VortexPvP plugin;
    private final Map<Projectile, TrailManager.Trail> active = new LinkedHashMap<>();
    private int tick;

    public TrailManager(VortexPvP plugin) {
        this.plugin = plugin;
    }

    public void track(Player shooter, Projectile proj) {
        if (Cfg.bool(Opt.TRAILS) && shooter.hasPermission("vortexpvp.cosmetics")) {
            String var4 = this.plugin.profiles().get(shooter).get(Setting.TRAIL);

            Particle particle = switch (var4) {
                case "hearts" -> Particle.HEART;
                case "flames" -> Particle.FLAME;
                case "notes" -> Particle.NOTE;
                case "crit" -> Particle.CRIT;
                case "magic" -> Particle.WITCH;
                case "snow" -> Particle.SNOWFLAKE;
                case "end_rod" -> Particle.END_ROD;
                case "cherry" -> Particle.CHERRY_LEAVES;
                case "soul" -> Particle.SOUL_FIRE_FLAME;
                default -> null;
            };
            if (particle != null) {
                this.active.put(proj, new TrailManager.Trail(particle, this.tick));
            }
        }
    }

    public void tick() {
        this.tick++;
        Iterator<Entry<Projectile, TrailManager.Trail>> it = this.active.entrySet().iterator();

        while (it.hasNext()) {
            Entry<Projectile, TrailManager.Trail> e = it.next();
            Projectile p = e.getKey();
            boolean stuck = p instanceof AbstractArrow a && a.isInBlock();
            if (p.isValid() && !stuck && this.tick - e.getValue().born() <= 200) {
                Location l = p.getLocation();
                l.getWorld().spawnParticle(e.getValue().particle(), l, 1, 0.0, 0.0, 0.0, 0.0);
            } else {
                it.remove();
            }
        }
    }

    private record Trail(Particle particle, int born) {
    }
}
