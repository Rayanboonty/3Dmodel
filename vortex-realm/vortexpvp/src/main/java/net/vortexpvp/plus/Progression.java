package net.vortexpvp.plus;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * XP and the battle pass, missions (3 daily, 3 weekly, 4 for the season), 150+ achievements, titles, cosmetic
 * unlocks, the 7-day reward calendar and the login streak. Other systems report what players do with
 * {@link #track}, {@link #best} and {@link #xp}.
 */
public final class Progression implements Listener {
    public static final int XP_PER_LEVEL = 500;
    public static final int PASS_LEVELS = 50;
    public static final String[] WIN_FX = {"fireworks", "lightning", "totem", "dragon", "hearts", "souls", "flames", "snow"};
    public static final String[] DEATH_MSG = {"dramatic", "pirate", "knight", "robot", "chef"};
    public static final String[] PETS = {"wolf", "cat", "fox", "axolotl", "parrot", "bee", "frog", "rabbit", "panda", "sniffer"};
    public static final String[] GADGETS = {"leap", "grapple", "fireworks", "paint"};
    private final VortexPvP plugin;
    private final PlusModule plus;
    private final List<Mission> pool = new ArrayList<>();
    private final Map<String, Ach> achievements = new LinkedHashMap<>();

    public record Mission(String id, String type, String kit, long goal, int xp, int shards, String text) {
    }

    public record Ach(String id, String name, String desc, String counter, long goal, int rarity, String title) {
    }

    public Progression(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
        this.buildMissions();
        this.buildAchievements();
    }

    // ------------------------------------------------------------------ XP and rewards

    public static int passLevel(long passXp) {
        return (int)Math.min(PASS_LEVELS, passXp / XP_PER_LEVEL);
    }

    public static int level(long xp) {
        return (int)(xp / XP_PER_LEVEL) + 1;
    }

    public void xp(UUID u, long amount, String why) {
        if (amount <= 0) {
            return;
        }

        PlayerData d = this.plus.store().get(u);
        int season = this.plus.seasons() == null ? 0 : this.plus.seasons().number();
        if (d.passSeason != season) {
            d.passSeason = season;
            d.passXp = 0L;
            d.passClaimed.clear();
        }

        int oldLevel = level(d.xp);
        int oldPass = passLevel(d.passXp);
        d.xp += amount;
        d.passXp += amount;
        d.dirty = true;
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            p.sendActionBar(Text.mm("<#7FE3FF>+" + amount + " XP <dark_gray>• <gray>" + Text.esc(why)));
            if (level(d.xp) > oldLevel) {
                p.sendMessage(Text.mm("<gradient:#7B2FF7:#2FD3F7><b>LEVEL UP!</b></gradient> <gray>You are now level <white>" + level(d.xp)));
                Snd.play(p, "entity.player.levelup", 1.0F, 1.2F);
            }

            if (passLevel(d.passXp) > oldPass) {
                p.sendMessage(Text.mm("<gold>✦ Battle pass level <white>" + passLevel(d.passXp) + "</white> reached - <click:run_command:'/pass'><yellow>[claim rewards]</yellow></click>"));
            }
        }

        this.track(u, "xp", null, amount);
    }

    public void shards(UUID u, long n) {
        if (n <= 0) {
            return;
        }

        if (this.plugin.economy() != null) {
            this.plugin.economy().giveShards(u, n);
        } else {
            PlayerData d = this.plus.store().get(u);
            d.coins += n;
            d.dirty = true;
        }
    }

    public void title(UUID u, String title) {
        PlayerData d = this.plus.store().get(u);
        if (d.titles.add(title)) {
            d.dirty = true;
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.sendMessage(Text.mm("<light_purple>✦ New title: <white>" + Text.esc(title) + "</white> <gray>- pick it in <white>/titles"));
            }
        }
    }

    public void unlock(UUID u, String key) {
        PlayerData d = this.plus.store().get(u);
        if (d.unlocks.add(key)) {
            d.dirty = true;
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.sendMessage(Text.mm("<aqua>✦ Unlocked: <white>" + Text.esc(unlockName(key)) + "</white> <gray>- use it in <white>/vfx"));
            }
        }
    }

    public static String unlockName(String key) {
        int i = key.indexOf(':');
        String group = i < 0 ? key : key.substring(0, i);
        String v = i < 0 ? "" : key.substring(i + 1);
        String g = switch (group) {
            case "win" -> "Win effect";
            case "death" -> "Death message";
            case "pet" -> "Pet";
            case "gadget" -> "Gadget";
            default -> group;
        };
        return g + ": " + nice(v);
    }

    public static String nice(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.replace('_', ' ').split(" ")) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
        }

        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ counters, missions, achievements

    /** Something happened: counts it (also per kit), moves missions on, checks achievements. */
    public void track(UUID u, String type, String kit, long amount) {
        PlayerData d = this.plus.store().get(u);
        d.add(type, amount);
        if (kit != null) {
            d.add(type + "." + kit, amount);
        }

        if (!type.equals("xp")) {
            this.missions(u, d, type, kit, amount);
        }

        this.checkAchievements(u, d);
    }

    /** A personal best (longest combo, best streak...): kept as the highest value seen. */
    public void best(UUID u, String key, long value) {
        PlayerData d = this.plus.store().get(u);
        d.max(key, value);
        this.checkAchievements(u, d);
    }

    private void buildMissions() {
        // id, type, kit ("*" = any; "@" = today's kit), goal, xp, shards, text (# = goal, % = kit)
        String[][] defs = {
            {"play3", "play", "*", "3", "120", "20", "Play # matches"},
            {"play8", "play", "*", "8", "250", "40", "Play # matches"},
            {"win2", "win", "*", "2", "150", "25", "Win # matches"},
            {"win5", "win", "*", "5", "300", "50", "Win # matches"},
            {"rwin2", "ranked_win", "*", "2", "220", "40", "Win # ranked matches"},
            {"rwin4", "ranked_win", "*", "4", "380", "70", "Win # ranked matches"},
            {"kit3", "play", "@", "3", "200", "30", "Play # matches of %"},
            {"kitwin2", "win", "@", "2", "260", "45", "Win # matches of %"},
            {"hits150", "hits", "*", "150", "160", "25", "Land # hits"},
            {"hits400", "hits", "*", "400", "300", "50", "Land # hits"},
            {"combo", "combo_match", "*", "2", "200", "30", "Get a 5+ combo in # matches"},
            {"ffa5", "ffa_kill", "*", "5", "160", "30", "Get # FFA kills"},
            {"ffa15", "ffa_kill", "*", "15", "350", "60", "Get # FFA kills"},
            {"drill2", "drill", "*", "2", "150", "20", "Finish # training drills"},
            {"arcade1", "arcade_play", "*", "1", "150", "25", "Play # arcade game"},
            {"arcadewin", "arcade_win", "*", "1", "220", "40", "Win # arcade game"},
            {"pearls10", "pearls", "*", "10", "120", "20", "Throw # ender pearls in matches"},
            {"bounty", "bounty", "*", "1", "300", "60", "Claim # bounty in FFA"}
        };
        for (String[] m : defs) {
            this.pool.add(new Mission(m[0], m[1], m[2], Long.parseLong(m[3]), Integer.parseInt(m[4]), Integer.parseInt(m[5]), m[6]));
        }
    }

    private List<Kit> missionKits() {
        List<Kit> ks = new ArrayList<>();
        for (Kit k : this.plugin.kits().all()) {
            if (k.enabled && (k.ranked || k.unranked)) {
                ks.add(k);
            }
        }

        return ks;
    }

    /** The active missions: [period, id, goal-multiplier] with today's kit for "@" ones. */
    public List<Active> active() {
        List<Active> out = new ArrayList<>();
        long day = LocalDate.now().toEpochDay();
        LocalDate now = LocalDate.now();
        int week = now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + now.get(IsoFields.WEEK_BASED_YEAR) * 100;
        int season = this.plus.seasons() == null ? 0 : this.plus.seasons().number();
        this.pick(out, "d" + day, day * 31L, 3, 1);
        this.pick(out, "w" + week, week * 977L, 3, 4);
        this.pick(out, "s" + season, season * 7919L + 13L, 4, 15);
        return out;
    }

    public record Active(String key, String period, Mission m, String kit, long goal, int xp, int shards) {
        public String text() {
            return this.m.text().replace("#", String.valueOf(this.goal)).replace("%", this.kit == null ? "any kit" : this.kit);
        }
    }

    private void pick(List<Active> out, String period, long seed, int n, int scale) {
        Random r = new Random(seed);
        List<Mission> copy = new ArrayList<>(this.pool);
        List<Kit> kits = this.missionKits();
        for (int i = 0; i < n && !copy.isEmpty(); i++) {
            Mission m = copy.remove(r.nextInt(copy.size()));
            String kit = null;
            if ("@".equals(m.kit()) && !kits.isEmpty()) {
                kit = kits.get(r.nextInt(kits.size())).id;
            }

            long goal = m.goal() * scale;
            out.add(new Active(period + ":" + m.id(), period, m, kit, goal, m.xp() * Math.max(1, scale / 2 + 1), m.shards() * Math.max(1, scale / 2 + 1)));
        }
    }

    private void missions(UUID u, PlayerData d, String type, String kit, long amount) {
        for (Active a : this.active()) {
            if (!a.m().type().equals(type) || d.missionsDone.contains(a.key())) {
                continue;
            }

            if (a.kit() != null && !a.kit().equalsIgnoreCase(kit)) {
                continue;
            }

            long now = d.missions.merge(a.key(), amount, Long::sum);
            d.dirty = true;
            if (now >= a.goal()) {
                d.missionsDone.add(a.key());
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    p.sendMessage(Text.mm("<green>✔ Mission complete: <white>" + Text.esc(a.text()) + " <gray>(+" + a.xp() + " XP, +" + a.shards() + " shards)"));
                    Snd.play(p, "ui.toast.challenge_complete", 0.7F, 1.4F);
                }

                this.shards(u, a.shards());
                this.xp(u, a.xp(), "mission");
                this.track(u, "missions", null, 1L);
            }
        }

        // forget old periods so the file stays small
        if (d.missions.size() > 60) {
            List<String> keep = new ArrayList<>();
            for (Active a : this.active()) {
                keep.add(a.key());
            }

            d.missions.keySet().removeIf(k -> !keep.contains(k));
            d.missionsDone.removeIf(k -> !keep.contains(k));
        }
    }

    private void buildAchievements() {
        String[][] tiers = {{"1", "First Blood", "1"}, {"10", "Fighter", "1"}, {"50", "Veteran", "2"}, {"100", "Master", "3"}, {"500", "Legend", "4"}};
        for (Kit k : this.plugin.kits().all()) {
            if (!k.enabled) {
                continue;
            }

            String kn = Text.plain(Text.mm(k.displayName));
            for (String[] t : tiers) {
                int rarity = Integer.parseInt(t[2]);
                this.ach(k.id + "_w" + t[0], kn + " " + t[1], "Win " + t[0] + " " + kn + " match" + ("1".equals(t[0]) ? "" : "es"), "win." + k.id, Long.parseLong(t[0]), rarity,
                    rarity >= 4 ? kn + " Legend" : null);
            }

            if (k.ranked) {
                this.ach(k.id + "_r10", kn + " Contender", "Win 10 ranked " + kn + " matches", "ranked_win." + k.id, 10, 2, null);
                this.ach(k.id + "_r100", kn + " Elite", "Win 100 ranked " + kn + " matches", "ranked_win." + k.id, 100, 3, null);
                this.ach(k.id + "_gold", kn + " Gold", "Reach Gold in " + kn, "ladder." + k.id, 9, 2, null);
                this.ach(k.id + "_dia", kn + " Diamond", "Reach Diamond in " + kn, "ladder." + k.id, 18, 3, null);
                this.ach(k.id + "_neth", kn + " Netherite", "Reach Netherite in " + kn, "ladder." + k.id, 21, 4, kn + " Netherite");
            }
        }

        long[][] g = {{10, 1}, {100, 2}, {500, 3}, {1000, 3}, {5000, 4}};
        for (long[] t : g) {
            this.ach("play" + t[0], "Played " + t[0], "Play " + t[0] + " matches", "play", t[0], (int)t[1], t[0] == 5000 ? "No-Lifer" : null);
            this.ach("win" + t[0], "Won " + t[0], "Win " + t[0] + " matches", "win", t[0], (int)t[1], t[0] == 1000 ? "Champion" : null);
        }

        long[] ffa = {10, 100, 500, 1000};
        for (long n : ffa) {
            this.ach("ffa" + n, "FFA " + n, "Get " + n + " FFA kills", "ffa_kill", n, n >= 500 ? 3 : (n >= 100 ? 2 : 1), n == 1000 ? "Slayer" : null);
        }

        this.ach("streak5", "On Fire", "Win 5 ranked matches in a row", "best_ranked_streak", 5, 2, null);
        this.ach("streak10", "Unstoppable", "Win 10 ranked matches in a row", "best_ranked_streak", 10, 3, "Unstoppable");
        this.ach("streak20", "Godlike", "Win 20 ranked matches in a row", "best_ranked_streak", 20, 4, "Godlike");
        this.ach("combo10", "Combo Starter", "Land a 10-hit combo", "best_combo", 10, 1, null);
        this.ach("combo20", "Combo Artist", "Land a 20-hit combo", "best_combo", 20, 2, null);
        this.ach("combo35", "Combo King", "Land a 35-hit combo", "best_combo", 35, 4, "Combo King");
        this.ach("ffastreak10", "Rampage", "Get a 10-kill FFA streak", "best_ffa_streak", 10, 2, null);
        this.ach("ffastreak25", "Massacre", "Get a 25-kill FFA streak", "best_ffa_streak", 25, 4, "Massacre");
        this.ach("bounty1", "Bounty Hunter", "Claim a bounty", "bounty", 1, 1, null);
        this.ach("bounty10", "Head Collector", "Claim 10 bounties", "bounty", 10, 3, "Bounty Hunter");
        this.ach("drill10", "Trainee", "Finish 10 training drills", "drill", 10, 1, null);
        this.ach("drill100", "Drill Sergeant", "Finish 100 training drills", "drill", 100, 3, "Drill Sergeant");
        this.ach("arcade1", "Arcade Kid", "Win an arcade game", "arcade_win", 1, 1, null);
        this.ach("arcade50", "Arcade Master", "Win 50 arcade games", "arcade_win", 50, 3, "Arcade Master");
        this.ach("login7", "Regular", "Log in 7 days in a row", "best_login_streak", 7, 1, null);
        this.ach("login30", "Dedicated", "Log in 30 days in a row", "best_login_streak", 30, 3, "Dedicated");
        this.ach("login100", "Resident", "Log in 100 days in a row", "best_login_streak", 100, 4, "Resident");
        this.ach("missions10", "Busy", "Complete 10 missions", "missions", 10, 1, null);
        this.ach("missions100", "Workaholic", "Complete 100 missions", "missions", 100, 3, "Workaholic");
        this.ach("replay1", "Film Critic", "Watch a replay", "replays_watched", 1, 1, null);
        this.ach("clan1", "Better Together", "Join or found a clan", "clan_joined", 1, 1, null);
        this.ach("war1", "Warlord", "Win a clan war", "clan_war_win", 1, 3, "Warlord");
        this.ach("tour1", "Cup Winner", "Win a weekly tournament", "tournament_win", 1, 4, "Weekly Champion");
        this.ach("season1", "Seasoned", "Get a season reward", "season_reward", 1, 2, null);
        this.ach("mentor1", "Teacher", "Have your mentee win 10 matches", "mentee_win", 10, 2, "Mentor");
        this.ach("rival1", "Rivalry", "Beat your rival", "rival_win", 1, 1, null);
        this.ach("level10", "Level 10", "Reach level 10", "level", 10, 1, null);
        this.ach("level50", "Level 50", "Reach level 50", "level", 50, 3, null);
        this.ach("level100", "Level 100", "Reach level 100", "level", 100, 4, "Centurion");
        this.ach("cps12", "Fast Fingers", "Reach 12 CPS in the CPS test", "best_cps", 12, 2, null);
        this.ach("parkour1", "Free Runner", "Finish the hub parkour", "parkour", 1, 1, null);
        this.ach("daily7", "Calendar Complete", "Claim all 7 days of the reward calendar", "daily_full", 1, 2, null);
    }

    private void ach(String id, String name, String desc, String counter, long goal, int rarity, String title) {
        this.achievements.put(id, new Ach(id, name, desc, counter, goal, rarity, title));
    }

    public Map<String, Ach> achievements() {
        return this.achievements;
    }

    private void checkAchievements(UUID u, PlayerData d) {
        d.max("level", level(d.xp));
        for (Ach a : this.achievements.values()) {
            if (!d.achievements.contains(a.id()) && d.count(a.counter()) >= a.goal()) {
                d.achievements.add(a.id());
                d.dirty = true;
                int xp = 50 * a.rarity() * a.rarity();
                Player p = Bukkit.getPlayer(u);
                String color = switch (a.rarity()) {
                    case 4 -> "<gradient:#FFD700:#FF6B00>";
                    case 3 -> "<#C77DFF>";
                    case 2 -> "<#55C8FF>";
                    default -> "<green>";
                };
                if (p != null) {
                    p.sendMessage(Text.mm("<gold>🏆 Achievement: " + color + "<b>" + Text.esc(a.name()) + "</b></" + (a.rarity() == 4 ? "gradient" : "color") + "> <gray>- " + Text.esc(a.desc())));
                    Snd.play(p, "ui.toast.challenge_complete", 0.8F, 1.0F);
                    if (a.rarity() >= 3) {
                        Bukkit.broadcast(Text.mm("<gold>🏆 <white>" + Text.esc(p.getName()) + "</white> <gray>unlocked " + color + Text.esc(a.name())));
                    }
                }

                if (a.title() != null) {
                    this.title(u, a.title());
                }

                // the XP reward goes on after this loop (it can unlock level achievements)
                long gain = xp;
                net.vortexpvp.util.Tasks.later(1L, () -> this.xp(u, gain, "achievement"));
            }
        }
    }

    // ------------------------------------------------------------------ battle pass

    /** The reward of one level on one track: "shards:120", "unlock:win:lightning", "title:Season 3 Legend". */
    public String reward(int level, boolean premium) {
        int season = this.plus.seasons() == null ? 1 : Math.max(1, this.plus.seasons().number());
        if (!premium) {
            if (level % 10 == 0) {
                return "title:S" + season + " Grinder " + (level / 10);
            } else if (level % 5 == 0) {
                return "unlock:" + this.cosmeticFor(level / 5 - 1);
            } else {
                return "shards:" + (20 + level * 2);
            }
        } else if (level == PASS_LEVELS) {
            return "title:Season " + season + " Legend";
        } else if (level % 10 == 5) {
            return "title:S" + season + " Elite " + (level / 10 + 1);
        } else if (level % 3 == 0) {
            return "unlock:" + this.cosmeticFor(7 + level / 3);
        } else {
            return "shards:" + (40 + level * 3);
        }
    }

    private String cosmeticFor(int i) {
        List<String> all = new ArrayList<>();
        for (String s : WIN_FX) {
            all.add("win:" + s);
        }

        for (String s : PETS) {
            all.add("pet:" + s);
        }

        for (String s : DEATH_MSG) {
            all.add("death:" + s);
        }

        for (String s : GADGETS) {
            all.add("gadget:" + s);
        }

        return all.get(Math.floorMod(i, all.size()));
    }

    public static String rewardText(String r) {
        if (r.startsWith("shards:")) {
            return r.substring(7) + " shards";
        } else if (r.startsWith("title:")) {
            return "Title: " + r.substring(6);
        } else {
            return r.startsWith("unlock:") ? unlockName(r.substring(7)) : r;
        }
    }

    public void grant(UUID u, String r) {
        if (r.startsWith("shards:")) {
            this.shards(u, Long.parseLong(r.substring(7)));
        } else if (r.startsWith("title:")) {
            this.title(u, r.substring(6));
        } else if (r.startsWith("unlock:")) {
            this.unlock(u, r.substring(7));
        }
    }

    public boolean premium(Player p) {
        return p.hasPermission("vortexpvp.pass.premium");
    }

    public void passMenu(Player p, int page) {
        PlayerData d = this.plus.store().get(p);
        int lvl = passLevel(d.passXp);
        boolean prem = this.premium(p);
        Gui.open(this.plugin, p, "<dark_gray>Battle Pass <gray>(" + lvl + "/" + PASS_LEVELS + ")", 6, g -> {
            int from = page * 9 + 1;
            for (int i = 0; i < 9; i++) {
                int level = from + i;
                if (level > PASS_LEVELS) {
                    break;
                }

                for (int t = 0; t < 2; t++) {
                    boolean premium = t == 1;
                    String r = this.reward(level, premium);
                    String key = (premium ? "p" : "f") + level;
                    boolean claimed = d.passClaimed.contains(key);
                    boolean reached = lvl >= level;
                    boolean allowed = !premium || prem;
                    Material m = claimed ? Material.LIME_STAINED_GLASS_PANE
                        : (!reached ? Material.GRAY_STAINED_GLASS_PANE : (allowed ? (premium ? Material.GOLD_BLOCK : Material.CHEST) : Material.RED_STAINED_GLASS_PANE));
                    g.item(t == 0 ? 18 + i : 36 + i, Items.of(m)
                        .name((premium ? "<gold>Premium" : "<aqua>Free") + " <white>- Level " + level)
                        .lore("<gray>" + Text.esc(rewardText(r)), "",
                            claimed ? "<green>✔ Claimed" : (!reached ? "<red>Reach level " + level : (allowed ? "<yellow>▶ Click to claim" : "<red>Premium pass only")))
                        .build(), c -> {
                            if (!claimed && reached && allowed && d.passClaimed.add(key)) {
                                d.dirty = true;
                                this.grant(p.getUniqueId(), r);
                                Snd.play(p, "entity.player.levelup", 0.8F, 1.6F);
                                this.passMenu(p, page);
                            }
                        });
                }
            }

            long into = d.passXp % XP_PER_LEVEL;
            g.item(4, Items.of(Material.NETHER_STAR).name("<gradient:#7B2FF7:#2FD3F7><b>Battle Pass</b></gradient>")
                .lore("<gray>Level <white>" + lvl + "<gray>/" + PASS_LEVELS, "<gray>Next level: <white>" + into + "<gray>/" + XP_PER_LEVEL + " XP",
                    "<gray>Premium: " + (prem ? "<green>yes" : "<red>no"), "", "<gray>Top row: free • bottom row: premium").build());
            if (page > 0) {
                g.item(45, Items.of(Material.ARROW).name("<yellow>« Levels " + ((page - 1) * 9 + 1) + "-" + (page * 9)).build(), c -> this.passMenu(p, page - 1));
            }

            if ((page + 1) * 9 < PASS_LEVELS) {
                g.item(53, Items.of(Material.ARROW).name("<yellow>Levels " + ((page + 1) * 9 + 1) + "-" + Math.min(PASS_LEVELS, (page + 2) * 9) + " »").build(),
                    c -> this.passMenu(p, page + 1));
            }
        });
    }

    // ------------------------------------------------------------------ menus

    public void missionsMenu(Player p) {
        PlayerData d = this.plus.store().get(p);
        List<Active> list = this.active();
        Gui.open(this.plugin, p, "<dark_gray>Missions", 4, g -> {
            int[] slots = {10, 11, 12, 14, 15, 16, 19, 20, 21, 22};
            for (int i = 0; i < list.size() && i < slots.length; i++) {
                Active a = list.get(i);
                long have = Math.min(a.goal(), d.missions.getOrDefault(a.key(), 0L));
                boolean done = d.missionsDone.contains(a.key());
                String period = a.period().startsWith("d") ? "<aqua>Daily" : (a.period().startsWith("w") ? "<gold>Weekly" : "<light_purple>Season");
                g.item(slots[i], Items.of(done ? Material.LIME_DYE : Material.PAPER).name(period + " <white>- " + Text.esc(a.text()))
                    .lore("<gray>Progress: <white>" + have + "/" + a.goal() + " " + bar(have, a.goal()), "<gray>Reward: <aqua>" + a.xp() + " XP <gray>+ <light_purple>" + a.shards() + " shards",
                        done ? "<green>✔ Done" : "")
                    .build());
            }
        });
    }

    static String bar(long have, long goal) {
        int n = 10;
        int f = goal <= 0 ? n : (int)Math.min(n, have * n / goal);
        return "<green>" + "|".repeat(f) + "<dark_gray>" + "|".repeat(n - f);
    }

    public void achievementsMenu(Player p, int page) {
        PlayerData d = this.plus.store().get(p);
        List<Ach> all = new ArrayList<>(this.achievements.values());
        Gui.open(this.plugin, p, "<dark_gray>Achievements <gray>(" + d.achievements.size() + "/" + all.size() + ")", 6, g -> {
            int[] slots = g.inner();
            int from = page * slots.length;
            for (int i = 0; i < slots.length && from + i < all.size(); i++) {
                Ach a = all.get(from + i);
                boolean has = d.achievements.contains(a.id());
                long cur = Math.min(a.goal(), d.count(a.counter()));
                g.item(slots[i], Items.of(has ? Material.GOLD_INGOT : Material.IRON_NUGGET).name((has ? "<gold>" : "<gray>") + Text.esc(a.name()))
                    .lore("<gray>" + Text.esc(a.desc()), "<gray>Rarity: " + "★".repeat(a.rarity()), has ? "<green>✔ Unlocked" : "<gray>" + cur + "/" + a.goal() + " " + bar(cur, a.goal()),
                        a.title() == null ? "" : "<light_purple>Title: " + Text.esc(a.title()))
                    .build());
            }

            if (page > 0) {
                g.item(45, Items.of(Material.ARROW).name("<yellow>« Back").build(), c -> this.achievementsMenu(p, page - 1));
            }

            if (from + slots.length < all.size()) {
                g.item(53, Items.of(Material.ARROW).name("<yellow>Next »").build(), c -> this.achievementsMenu(p, page + 1));
            }
        });
    }

    public void titlesMenu(Player p) {
        PlayerData d = this.plus.store().get(p);
        List<String> list = new ArrayList<>(d.titles);
        Gui.open(this.plugin, p, "<dark_gray>Titles", 6, g -> {
            int[] slots = g.inner();
            for (int i = 0; i < slots.length && i < list.size(); i++) {
                String t = list.get(i);
                boolean on = t.equals(d.title);
                g.item(slots[i], Items.of(on ? Material.NAME_TAG : Material.PAPER).name("<light_purple>" + Text.esc(t)).glint(on)
                    .lore(on ? "<green>✔ Showing" : "<yellow>▶ Click to show it").build(), c -> {
                        d.title = on ? "" : t;
                        d.dirty = true;
                        this.titlesMenu(p);
                    });
            }

            if (list.isEmpty()) {
                g.item(22, Items.of(Material.BARRIER).name("<gray>No titles yet").lore("<gray>Earn them from achievements,", "<gray>the battle pass and seasons.").build());
            }
        });
    }

    // ------------------------------------------------------------------ daily calendar and login streak

    public static final int[] DAILY = {50, 75, 100, 150, 200, 300, 500};

    public void dailyMenu(Player p) {
        PlayerData d = this.plus.store().get(p);
        long today = LocalDate.now().toEpochDay();
        if (d.dailyLastDay >= 0 && today - d.dailyLastDay > 1) {
            d.dailyDay = 0;
        }

        boolean claimedToday = d.dailyLastDay == today;
        Gui.open(this.plugin, p, "<dark_gray>Daily Rewards", 3, g -> {
            for (int i = 0; i < 7; i++) {
                int day = i;
                boolean past = day < d.dailyDay;
                boolean now = day == d.dailyDay && !claimedToday;
                g.item(10 + i, Items.of(past ? Material.LIME_STAINED_GLASS_PANE : (now ? Material.CHEST_MINECART : Material.MINECART))
                    .name("<gold>Day " + (day + 1)).lore("<light_purple>" + DAILY[day] + " shards" + (day == 6 ? " + a title" : ""), "",
                        past ? "<green>✔ Claimed" : (now ? "<yellow>▶ Click to claim" : "<gray>Come back tomorrow"))
                    .build(), c -> {
                        if (now) {
                            d.dailyLastDay = today;
                            d.dailyDay = (day + 1) % 7;
                            d.dirty = true;
                            this.shards(p.getUniqueId(), DAILY[day]);
                            this.xp(p.getUniqueId(), 25L, "daily reward");
                            if (day == 6) {
                                this.title(p.getUniqueId(), "Daily Devotee");
                                this.track(p.getUniqueId(), "daily_full", null, 1L);
                            }

                            Snd.play(p, "entity.player.levelup", 0.8F, 1.4F);
                            this.dailyMenu(p);
                        }
                    });
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = this.plus.store().get(p);
        long today = LocalDate.now().toEpochDay();
        if (d.loginLastDay != today) {
            d.loginStreak = d.loginLastDay == today - 1 ? d.loginStreak + 1 : 1;
            d.loginLastDay = today;
            d.dirty = true;
            this.best(p.getUniqueId(), "best_login_streak", d.loginStreak);
            long gain = 20L * Math.min(7, d.loginStreak);
            net.vortexpvp.util.Tasks.later(60L, () -> {
                if (p.isOnline()) {
                    p.sendMessage(Text.mm("<gold>☀ Login streak: <white>" + d.loginStreak + " day" + (d.loginStreak == 1 ? "" : "s")
                        + "</white> <gray>(+" + gain + " XP) <dark_gray>• <click:run_command:'/daily'><yellow>[daily reward]</yellow></click>"));
                    this.xp(p.getUniqueId(), gain, "login streak");
                }
            });
        }
    }

    public String selfCheck() {
        if (this.achievements.size() < 100) {
            return "only " + this.achievements.size() + " achievements";
        } else if (this.active().size() != 10) {
            return "missions: " + this.active().size();
        } else {
            return this.reward(PASS_LEVELS, true).startsWith("title:") ? null : "pass level 50";
        }
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
