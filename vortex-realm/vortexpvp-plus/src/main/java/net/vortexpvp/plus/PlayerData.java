package net.vortexpvp.plus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Everything the add-on systems keep about one player. Saved as YAML (a file, or a text column with MySQL). */
public final class PlayerData {
    public final UUID uuid;
    public String name = "?";
    /** Lifetime XP, and this season's battle-pass XP. */
    public long xp;
    public long passXp;
    public int passSeason;
    public final Set<String> passClaimed = new LinkedHashSet<>();
    /** Counters for missions and achievements: "wins", "wins.sword", "ffa_kills", "best_combo"... */
    public final Map<String, Long> counters = new HashMap<>();
    public final Set<String> achievements = new LinkedHashSet<>();
    public final Set<String> titles = new LinkedHashSet<>();
    public String title = "";
    /** Unlocked cosmetics ("win:lightning", "pet:fox"...) and the one picked in each group ("win" -> "lightning"). */
    public final Set<String> unlocks = new LinkedHashSet<>();
    public final Map<String, String> selected = new HashMap<>();
    /** Mission progress by instance ("d20391:win3" = daily mission win3 of day 20391) and the ones paid out. */
    public final Map<String, Long> missions = new HashMap<>();
    public final Set<String> missionsDone = new LinkedHashSet<>();
    public long dailyLastDay = -1L;
    public int dailyDay;
    public long loginLastDay = -1L;
    public int loginStreak;
    public int season;
    /** Best ladder step reached this season, per kit. */
    public final Map<String, Integer> seasonPeak = new HashMap<>();
    public final Map<String, Integer> shield = new HashMap<>();
    /** Promotion series per kit: "targetRank:wins:losses". */
    public final Map<String, String> series = new HashMap<>();
    /** Head-to-head against each opponent: "wins:losses". */
    public final Map<String, String> h2h = new HashMap<>();
    /** The last match summaries (newest first), each "key=value;key=value". */
    public final List<String> history = new ArrayList<>();
    public final Map<String, Double> drillBest = new HashMap<>();
    public String clan = "";
    public String discordId = "";
    public boolean discordLinked;
    public String live = "";
    public boolean streamer;
    public boolean hud;
    public String mentor = "";
    public String creatorCode = "";
    public long coins;
    public boolean dirty;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public long count(String key) {
        return this.counters.getOrDefault(key, 0L);
    }

    public void add(String key, long n) {
        this.counters.merge(key, n, Long::sum);
        this.dirty = true;
    }

    public void max(String key, long v) {
        if (v > this.count(key)) {
            this.counters.put(key, v);
            this.dirty = true;
        }
    }

    public int[] h2h(UUID other) {
        String s = this.h2h.get(other.toString());
        if (s == null) {
            return new int[]{0, 0};
        }

        String[] p = s.split(":");
        try {
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        } catch (RuntimeException var4) {
            return new int[]{0, 0};
        }
    }

    public String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", this.name);
        y.set("xp", this.xp);
        y.set("pass.xp", this.passXp);
        y.set("pass.season", this.passSeason);
        y.set("pass.claimed", new ArrayList<>(this.passClaimed));
        this.counters.forEach((k, v) -> y.set("counters." + k.replace('.', '|'), v));
        y.set("achievements", new ArrayList<>(this.achievements));
        y.set("titles", new ArrayList<>(this.titles));
        y.set("title", this.title);
        y.set("unlocks", new ArrayList<>(this.unlocks));
        this.selected.forEach((k, v) -> y.set("selected." + k, v));
        this.missions.forEach((k, v) -> y.set("missions." + k.replace('.', '|'), v));
        y.set("missions-done", new ArrayList<>(this.missionsDone));
        y.set("daily.last", this.dailyLastDay);
        y.set("daily.day", this.dailyDay);
        y.set("login.last", this.loginLastDay);
        y.set("login.streak", this.loginStreak);
        y.set("season", this.season);
        this.seasonPeak.forEach((k, v) -> y.set("season-peak." + k, v));
        this.shield.forEach((k, v) -> y.set("shield." + k, v));
        this.series.forEach((k, v) -> y.set("series." + k, v));
        this.h2h.forEach((k, v) -> y.set("h2h." + k, v));
        y.set("history", this.history);
        this.drillBest.forEach((k, v) -> y.set("drills." + k, v));
        y.set("clan", this.clan);
        y.set("discord.id", this.discordId);
        y.set("discord.linked", this.discordLinked);
        y.set("live", this.live);
        y.set("streamer", this.streamer);
        y.set("hud", this.hud);
        y.set("mentor", this.mentor);
        y.set("creator-code", this.creatorCode);
        y.set("coins", this.coins);
        return y.saveToString();
    }

    public static PlayerData parse(UUID uuid, String text) {
        PlayerData d = new PlayerData(uuid);
        if (text == null || text.isBlank()) {
            return d;
        }

        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (Exception var5) {
            return d;
        }

        d.name = y.getString("name", "?");
        d.xp = y.getLong("xp");
        d.passXp = y.getLong("pass.xp");
        d.passSeason = y.getInt("pass.season");
        d.passClaimed.addAll(y.getStringList("pass.claimed"));
        longs(y, "counters", d.counters);
        d.achievements.addAll(y.getStringList("achievements"));
        d.titles.addAll(y.getStringList("titles"));
        d.title = y.getString("title", "");
        d.unlocks.addAll(y.getStringList("unlocks"));
        strings(y, "selected", d.selected);
        longs(y, "missions", d.missions);
        d.missionsDone.addAll(y.getStringList("missions-done"));
        d.dailyLastDay = y.getLong("daily.last", -1L);
        d.dailyDay = y.getInt("daily.day");
        d.loginLastDay = y.getLong("login.last", -1L);
        d.loginStreak = y.getInt("login.streak");
        d.season = y.getInt("season");
        ints(y, "season-peak", d.seasonPeak);
        ints(y, "shield", d.shield);
        strings(y, "series", d.series);
        strings(y, "h2h", d.h2h);
        d.history.addAll(y.getStringList("history"));
        ConfigurationSection dr = y.getConfigurationSection("drills");
        if (dr != null) {
            for (String k : dr.getKeys(false)) {
                d.drillBest.put(k, dr.getDouble(k));
            }
        }

        d.clan = y.getString("clan", "");
        d.discordId = y.getString("discord.id", "");
        d.discordLinked = y.getBoolean("discord.linked");
        d.live = y.getString("live", "");
        d.streamer = y.getBoolean("streamer");
        d.hud = y.getBoolean("hud");
        d.mentor = y.getString("mentor", "");
        d.creatorCode = y.getString("creator-code", "");
        d.coins = y.getLong("coins");
        return d;
    }

    private static void longs(YamlConfiguration y, String path, Map<String, Long> out) {
        ConfigurationSection s = y.getConfigurationSection(path);
        if (s != null) {
            for (String k : s.getKeys(false)) {
                out.put(k.replace('|', '.'), s.getLong(k));
            }
        }
    }

    private static void ints(YamlConfiguration y, String path, Map<String, Integer> out) {
        ConfigurationSection s = y.getConfigurationSection(path);
        if (s != null) {
            for (String k : s.getKeys(false)) {
                out.put(k, s.getInt(k));
            }
        }
    }

    private static void strings(YamlConfiguration y, String path, Map<String, String> out) {
        ConfigurationSection s = y.getConfigurationSection(path);
        if (s != null) {
            for (String k : s.getKeys(false)) {
                out.put(k, s.getString(k, ""));
            }
        }
    }
}
