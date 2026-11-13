package net.vortexrealm.arena;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.util.Cuboid;
import net.vortexrealm.util.Pos;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

public final class Arena {
    public final String id;
    public String displayName;
    public ArenaTheme theme = ArenaTheme.CUSTOM;
    public Material iconBlock = Material.GRASS_BLOCK;
    public String worldName;
    public Cuboid bounds;
    public Pos spawn1;
    public Pos spawn2;
    public Pos center;
    public final Set<String> kits = new LinkedHashSet<>();
    public boolean enabled = true;
    public String startMode = "DEFAULT";
    public int elevatorDepth = -1;
    public int buildHeight = -1;
    public String resetMode = "DEFAULT";
    public final List<Cuboid> gates = new ArrayList<>();
    public Material gateMaterial = Material.OAK_FENCE;
    public boolean generated;
    /** Set while the plugin generates it (saved), so an interrupted generation is noticed on the next start. */
    public boolean generating;
    public transient boolean editing;
    public long seed;
    public int size;
    public String genSignature;
    public boolean handEdited;
    public final List<ArenaInstance> instances = new ArrayList<>();
    public RegionSnapshot snapshot;
    public String pool = "ANY";
    /** Vanilla terrain site this map was cut from (VanillaTerrain.Site), null for built maps. */
    public String site;
    /** Deep crystal variant: no bedrock, crystal depth. */
    public boolean deep;
    /** When the last free instance was taken (auto copies). 0 = a copy is free. */
    public transient long busySince;

    public Arena(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public World world() {
        return this.worldName == null ? null : Bukkit.getWorld(this.worldName);
    }

    public boolean allows(Kit kit) {
        if (!kit.arenas.isEmpty()) {
            return kit.arenas.contains(this.id);
        } else if (!this.kits.isEmpty()) {
            return this.kits.contains(kit.id);
        } else {
            String pool = normPool(this.pool);
            if (pool.equals("ANY")) {
                return true;
            } else {
                Set<String> pools = kit.arenaPools();
                return pools.contains("ANY") || pools.contains(pool);
            }
        }
    }

    /** CRYSTAL, VANILLA, DESIGNED or MACE (ANY for hand-made maps). */
    public static String poolOf(ArenaTheme theme) {
        if (theme == null || theme == ArenaTheme.CUSTOM) {
            return "ANY";
        } else if (theme == ArenaTheme.FLATLANDS) {
            return "CRYSTAL";
        } else if (theme.colosseum != null) {
            return theme.colosseum == ColosseumDesign.Style.GRAND ? "MACE" : "DESIGNED";
        } else if (theme == ArenaTheme.MOLE_MINES) {
            return "MOLE";
        } else if (theme == ArenaTheme.TRAINING_GROUNDS) {
            return "BOT";
        } else if (theme.duel != null) {
            return theme.duel.build ? "DUEL_BUILD" : "DUEL";
        } else if (theme.open != null || theme.show != null) {
            return "DESIGNED";
        } else {
            return "VANILLA";
        }
    }

    /** Old pool names: FIELD (terrain) is now VANILLA, FLAT (colosseums) is DESIGNED. */
    public static String normPool(String p) {
        if (p == null || p.isBlank()) {
            return "ANY";
        } else {
            String u = p.trim().toUpperCase(java.util.Locale.ROOT);
            return switch (u) {
                case "FIELD" -> "VANILLA";
                case "FLAT" -> "DESIGNED";
                default -> u;
            };
        }
    }

    public static String normPools(String list) {
        StringBuilder sb = new StringBuilder();

        for (String part : list.split(",")) {
            if (!part.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(',');
                }

                sb.append(normPool(part));
            }
        }

        return sb.length() == 0 ? "ANY" : sb.toString();
    }

    public boolean vanilla() {
        return this.site != null;
    }

    public boolean ready() {
        return this.enabled && this.bounds != null && this.spawn1 != null && this.spawn2 != null && this.world() != null;
    }

    public List<String> problems() {
        List<String> out = new ArrayList<>();
        if (this.bounds == null) {
            out.add("bounds (pos1/pos2) not set");
        }

        if (this.spawn1 == null) {
            out.add("spawn 1 not set");
        }

        if (this.spawn2 == null) {
            out.add("spawn 2 not set");
        }

        if (this.worldName == null || this.world() == null) {
            out.add("world not loaded");
        }

        if (this.bounds != null && this.spawn1 != null && !this.bounds.contains(this.spawn1.bx(), this.spawn1.by(), this.spawn1.bz())) {
            out.add("spawn 1 is outside the bounds");
        }

        if (this.bounds != null && this.spawn2 != null && !this.bounds.contains(this.spawn2.bx(), this.spawn2.by(), this.spawn2.bz())) {
            out.add("spawn 2 is outside the bounds");
        }

        return out;
    }

    public Pos centerOrMid() {
        if (this.center != null) {
            return this.center;
        } else {
            return this.spawn1 != null && this.spawn2 != null
                ? new Pos(
                    (this.spawn1.x() + this.spawn2.x()) / 2.0,
                    Math.max(this.spawn1.y(), this.spawn2.y()) + 2.0,
                    (this.spawn1.z() + this.spawn2.z()) / 2.0,
                    0.0F,
                    0.0F
                )
                : this.spawn1;
        }
    }

    public int maxBuildY() {
        double top = Math.max(this.spawn1 == null ? 0.0 : this.spawn1.y(), this.spawn2 == null ? 0.0 : this.spawn2.y());
        int h = this.buildHeight > 0 ? this.buildHeight : Cfg.i(Opt.BUILD_HEIGHT);
        int y = (int)Math.floor(top) + h;
        return this.bounds == null ? y : Math.min(y, this.bounds.maxY());
    }

    public long freeInstances() {
        return this.instances.stream().filter(i -> i.state == ArenaInstance.State.FREE).count();
    }
}
