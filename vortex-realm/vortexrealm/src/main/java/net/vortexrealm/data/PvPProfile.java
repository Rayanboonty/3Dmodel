package net.vortexrealm.data;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.vortexrealm.kit.KitLayout;
import net.vortexrealm.rank.Tier;

public final class PvPProfile {
    public final UUID uuid;
    public String name;
    private final EnumMap<Setting, String> settings = new EnumMap<>(Setting.class);
    public int ffaKills;
    public int ffaDeaths;
    public int ffaBestStreak;
    public int ffaStreak;
    public long firstJoin;
    public long lastSeen;
    public final Map<String, KitStats> kits = new HashMap<>();
    public final Map<String, KitLayout> layouts = new HashMap<>();
    public boolean loadedOk = true;
    public boolean dirty;

    public PvPProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        long now = System.currentTimeMillis();
        this.firstJoin = now;
        this.lastSeen = now;
    }

    public String get(Setting s) {
        return this.settings.getOrDefault(s, s.defaultValue());
    }

    public boolean on(Setting s) {
        return "on".equals(this.get(s));
    }

    public void set(Setting s, String value) {
        if (value != null && !value.equals(s.defaultValue())) {
            this.settings.put(s, value);
        } else {
            this.settings.remove(s);
        }

        this.dirty = true;
    }

    public KitStats stats(String kitId) {
        return this.kits.computeIfAbsent(kitId, k -> new KitStats());
    }

    public KitStats statsOrNull(String kitId) {
        return this.kits.get(kitId);
    }

    public int totalUnrankedWins() {
        int t = 0;

        for (KitStats k : this.kits.values()) {
            t += k.unrankedWins;
        }

        return t;
    }

    public String bestKit() {
        String best = null;
        int bestTier = -1;
        int bestElo = Integer.MIN_VALUE;

        for (Entry<String, KitStats> e : this.kits.entrySet()) {
            KitStats k = e.getValue();
            if (k.tier >= 0 && (k.tier > bestTier || k.tier == bestTier && k.elo > bestElo)) {
                best = e.getKey();
                bestTier = k.tier;
                bestElo = k.elo;
            }
        }

        return best;
    }

    public Tier tierIn(String kitId) {
        KitStats k = this.kits.get(kitId);
        return k == null ? null : k.tier();
    }

    public String serializeSettings() {
        StringBuilder sb = new StringBuilder();

        for (Entry<Setting, String> e : this.settings.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(';');
            }

            sb.append(e.getKey().name()).append('=').append(e.getValue());
        }

        return sb.toString();
    }

    public void parseSettings(String raw) {
        this.settings.clear();
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split(";")) {
                int i = part.indexOf(61);
                if (i > 0) {
                    try {
                        Setting s = Setting.valueOf(part.substring(0, i));
                        this.settings.put(s, part.substring(i + 1));
                    } catch (IllegalArgumentException var8) {
                    }
                }
            }
        }
    }

    public PvPProfile copy() {
        PvPProfile p = new PvPProfile(this.uuid, this.name);
        p.settings.putAll(this.settings);
        p.ffaKills = this.ffaKills;
        p.ffaDeaths = this.ffaDeaths;
        p.ffaBestStreak = this.ffaBestStreak;
        p.firstJoin = this.firstJoin;
        p.lastSeen = this.lastSeen;

        for (Entry<String, KitStats> e : this.kits.entrySet()) {
            p.kits.put(e.getKey(), e.getValue().copy());
        }

        p.layouts.putAll(this.layouts);
        p.loadedOk = this.loadedOk;
        return p;
    }
}
