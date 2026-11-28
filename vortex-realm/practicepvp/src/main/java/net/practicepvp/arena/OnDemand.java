package net.practicepvp.arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.kit.Kit;

/**
 * On-demand arenas (1.0.9): no duel map is made at start-up. When two players are matched and their kit has no free
 * map, a map of the arena pack is built right then (or one more copy of a busy map is pasted); the players wait in a
 * waiting room styled like that map and are taken in the moment it is ready. A built map stays: it is never built twice.
 */
public final class OnDemand {
    private final PracticePlugin plugin;
    /** Maps being built now: pack id -> the arena (registered as soon as its build starts). */
    private final Map<String, Arena> building = new HashMap<>();
    /** Arenas getting one more copy now. */
    private final Map<String, Long> copying = new HashMap<>();
    /** When each build started (for the waiting room's progress). */
    private final Map<String, Long> started = new HashMap<>();

    public OnDemand(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return Cfg.bool(Opt.ARENA_ON_DEMAND);
    }

    /** The pool a pack map's arena will play in. */
    private static String poolOf(PracticePlugin.PackMap m) {
        if (m.id().startsWith("m_")) {
            return "MACE";
        }

        return m.deep() ? "CRYSTAL" : Arena.poolOf(m.theme());
    }

    private static boolean fits(Kit kit, PracticePlugin.PackMap m) {
        if (!kit.arenas.isEmpty()) {
            return kit.arenas.contains(m.id());
        }

        String pool = Arena.normPool(poolOf(m));
        if (pool.equals("ANY")) {
            return true;
        }

        Set<String> pools = kit.arenaPools();
        return pools.contains("ANY") || pools.contains(pool);
    }

    /** Pack maps for this kit that don't exist yet (and aren't being built). */
    public List<PracticePlugin.PackMap> candidates(Kit kit) {
        List<PracticePlugin.PackMap> out = new ArrayList<>();
        if (!this.enabled() || kit == null) {
            return out;
        }

        for (PracticePlugin.PackMap m : this.plugin.packMaps()) {
            if (m.theme() == ArenaTheme.TRAINING_GROUNDS || m.id().startsWith("m_") || this.plugin.arenas().pendingOrGet(m.id()) != null || this.building.containsKey(m.id())) {
                continue;
            }

            if (fits(kit, m)) {
                out.add(m);
            }
        }

        return out;
    }

    /** No map is being built right now. */
    public boolean idle() {
        return this.building.isEmpty();
    }

    /**
     * Every few seconds after start-up: a kit that has no ready map gets one built in the background (one at a time,
     * designed maps first), so matched players never wait for a first map.
     */
    public void warmUp() {
        if (!this.enabled() || !this.idle()) {
            return;
        }

        for (net.practicepvp.kit.Kit k : this.plugin.kits().all()) {
            if (this.plugin.arenas().forKit(k).isEmpty() && !this.candidates(k).isEmpty()) {
                this.buildFor(k);
                return;
            }
        }
    }

    /** True when the kit can be played: a map is ready, or one can be built for it now. */
    public boolean canPlay(Kit kit) {
        return !this.plugin.arenas().forKit(kit).isEmpty() || !this.candidates(kit).isEmpty() || this.buildingFor(kit) != null;
    }

    /** A map being built now that this kit can play on, or null. */
    public Arena buildingFor(Kit kit) {
        for (Arena a : this.building.values()) {
            if (a.allows(kit)) {
                return a;
            }
        }

        return null;
    }

    /**
     * Starts building a new map for this kit (designed maps first: they build in seconds) and returns its arena, or
     * null when there is nothing left to build. A map already being built for this kit is shared.
     */
    public Arena buildFor(Kit kit) {
        Arena shared = this.buildingFor(kit);
        if (shared != null) {
            return shared;
        }

        List<PracticePlugin.PackMap> c = this.candidates(kit);
        if (c.isEmpty()) {
            return null;
        }

        List<PracticePlugin.PackMap> fast = new ArrayList<>();
        for (PracticePlugin.PackMap m : c) {
            if (!m.theme().isVanilla()) {
                fast.add(m);
            }
        }

        List<PracticePlugin.PackMap> pick = fast.isEmpty() ? c : fast;
        PracticePlugin.PackMap m = pick.get(ThreadLocalRandom.current().nextInt(pick.size()));
        return this.build(m);
    }

    /** Builds one pack map now (its copies join after it, as each one is ready). */
    public Arena build(PracticePlugin.PackMap m) {
        Arena existing = this.building.get(m.id());
        if (existing != null) {
            return existing;
        }

        String name = m.deep() ? m.theme().display + " (Crystal)" : m.theme().display;
        if (m.id().startsWith("m_")) {
            name = name + " (Mace)";
        }

        boolean mace = m.id().startsWith("m_");
        this.started.put(m.id(), System.currentTimeMillis());
        this.plugin.getLogger().info("Building arena " + m.id() + " on demand.");
        this.plugin.arenas().generate(m.id(), name, m.theme(), m.size(), m.copies(), m.deep()).whenComplete((a, err) -> net.practicepvp.util.Tasks.ensureSync(() -> {
            this.building.remove(m.id());
            this.started.remove(m.id());
            if (err == null && a != null && mace) {
                a.pool = "MACE";
                this.plugin.arenas().save();
            }
        }));
        Arena a = this.plugin.arenas().pendingOrGet(m.id());
        if (a != null) {
            if (mace) {
                a.pool = "MACE";
            }

            this.building.put(m.id(), a);
        }

        return a;
    }

    /** Pastes one more copy of a map whose copies are all in use (once at a time per map). */
    public void requestCopy(Arena a) {
        if (a == null || !this.enabled() || this.copying.containsKey(a.id) || this.plugin.arenas().isBusy(a)) {
            return;
        }

        this.copying.put(a.id, System.currentTimeMillis());
        this.plugin.arenas().addCopies(a, 1).whenComplete((v, err) -> net.practicepvp.util.Tasks.ensureSync(() -> this.copying.remove(a.id)));
    }

    /** True while this arena's map (or a new copy of it) is being built. */
    public boolean busy(Arena a) {
        return a != null && (this.building.containsValue(a) || this.copying.containsKey(a.id) || a.generating || this.plugin.arenas().isBusy(a));
    }

    /** Seconds since this arena's build started (0 if unknown). */
    public double secondsBuilding(Arena a) {
        if (a == null) {
            return 0.0;
        }

        Long t = this.started.get(a.id);
        if (t == null) {
            t = this.copying.get(a.id);
        }

        return t == null ? 0.0 : (System.currentTimeMillis() - t) / 1000.0;
    }

    /** How many matches may still be paired for a kit without a free copy: builds wait in the waiting rooms. */
    public int spare() {
        return this.enabled() ? Math.max(0, 6 - this.building.size() - this.copying.size()) : 0;
    }
}
