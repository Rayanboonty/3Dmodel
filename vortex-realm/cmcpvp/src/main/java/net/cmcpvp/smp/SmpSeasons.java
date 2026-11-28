package net.cmcpvp.smp;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import net.kyori.adventure.title.Title;
import net.cmcpvp.CMCPvP;
import net.cmcpvp.util.Snd;
import net.cmcpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * 3.11.0 - SMP seasons the owner writes. Each season has a number, a name, a theme line, rules / features and a length;
 * starting one announces it to everybody (title, sound, chat card) and players see it with /season and when they come
 * into the SMP. Ten ready-made ideas can be loaded with /season idea &lt;1-10&gt;. Kept in smp-seasons.yml.
 *
 * <pre>
 * /season                          the current season (everyone)
 * /season new &lt;name&gt;              writes the next season (admins)
 * /season theme &lt;text&gt;            one line under the name
 * /season rule &lt;text&gt;             adds a rule / feature line   (/season rule clear)
 * /season days &lt;n&gt;                how long it lasts
 * /season start                    starts it now and announces it
 * /season list | ideas | idea &lt;n&gt;
 * </pre>
 */
public final class SmpSeasons implements CommandExecutor, TabCompleter, Listener {
    /** Ready-made seasons: name, theme, rules. */
    public static final String[][] IDEAS = {
        {"New Beginnings", "A fresh world, a fresh start - build the first town together",
            "Fresh survival world|Community spawn build in week 1|First to kill the Ender Dragon gets the Dragonslayer title|30 days"},
        {"Frozen Frontier", "An endless winter: survive the cold, build the warmest base",
            "Snowy world, frozen oceans|Campfires and hot springs heal you|Ice-boat race event every weekend|Igloo build contest"},
        {"Lifesteal Lords", "Every kill steals a heart - every death costs one",
            "Kill a player: +1 heart (max 20)|Die to a player: -1 heart|Revive Beacon brings a friend back|Weekly bounty board"},
        {"Kingdoms & Clans", "Found a kingdom with your friends and claim the land",
            "Teams of up to 6|Claim land around your capital|Siege Night every Saturday|Strongest kingdom wins team cosmetics"},
        {"Sky Islands", "The world is islands in the sky - nothing is free",
            "Void world with floating islands|Rare ores only on far islands|Trading is king (/market)|Bridges and airships"},
        {"Hardcore Heroes", "One life per week. Make it count",
            "Die and you watch until the weekly reset|Survivors of the season get the Hero badge|Double loot in dungeons"},
        {"Economy Empire", "The richest player at the end wins",
            "Shops, market and auctions everywhere|Weekly price events|Top 3 richest get a gold statue at spawn"},
        {"Dungeon Delvers", "Ancient ruins and bosses hide everywhere",
            "Custom ruins with loot|Weekly boss fight at spawn|Trial chambers count for the leaderboard"},
        {"Wild West", "Desert towns, gold rushes and wanted posters",
            "Desert / badlands world|Bounties on the most wanted|Gold is the money|Train-robbery event"},
        {"Anniversary Reunion", "The best rules of every season, voted by the players",
            "Players vote the rules in week 1|Old season badges come back|Big finale tournament"}
    };
    private final CMCPvP plugin;
    private final File file;
    private final List<Season> seasons = new ArrayList<>();
    private int current = -1;
    /** 3.13.17: the season teased as "coming soon" (shown with /season and when players enter the SMP). */
    private String soonName = "Unstable Realm";
    private String soonTheme = "A story-driven chaos season: one-of-a-kind characters, AI fighters and a world that reacts to you";
    private final List<String> soonRules = new ArrayList<>(List.of(
        "Pick a one-of-a-kind character: once someone takes it, it's gone",
        "Each character has its own powers, story and weaknesses",
        "AI fighters and story characters roam the world and remember what you did",
        "Your choices change the world: alliances, wars, events and the ending",
        "In development - follow #announcements for news"));

    public static final class Season {
        public int number;
        public String name;
        public String theme = "";
        public final List<String> rules = new ArrayList<>();
        public int days = 30;
        public long started;
    }

    public SmpSeasons(CMCPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "smp-seasons.yml");
        this.load();
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        this.current = y.getInt("current", -1);
        if (y.isConfigurationSection("coming-soon")) {
            this.soonName = y.getString("coming-soon.name", "");
            this.soonTheme = y.getString("coming-soon.theme", "");
            this.soonRules.clear();
            this.soonRules.addAll(y.getStringList("coming-soon.rules"));
        }
        ConfigurationSection sec = y.getConfigurationSection("seasons");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(k);
                if (s == null) {
                    continue;
                }

                Season x = new Season();
                x.number = s.getInt("number", this.seasons.size() + 1);
                x.name = s.getString("name", "Season " + x.number);
                x.theme = s.getString("theme", "");
                x.rules.addAll(s.getStringList("rules"));
                x.days = s.getInt("days", 30);
                x.started = s.getLong("started", 0L);
                this.seasons.add(x);
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("SMP seasons - write them in game with /season (see /season help)."));
        y.set("current", this.current);
        y.set("coming-soon.name", this.soonName);
        y.set("coming-soon.theme", this.soonTheme);
        y.set("coming-soon.rules", this.soonRules);
        for (int i = 0; i < this.seasons.size(); i++) {
            Season s = this.seasons.get(i);
            String b = "seasons.s" + (i + 1) + ".";
            y.set(b + "number", s.number);
            y.set(b + "name", s.name);
            y.set(b + "theme", s.theme);
            y.set(b + "rules", s.rules);
            y.set(b + "days", s.days);
            y.set(b + "started", s.started);
        }

        try {
            y.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save smp-seasons.yml", e);
        }
    }

    public Season current() {
        return this.current >= 0 && this.current < this.seasons.size() ? this.seasons.get(this.current) : null;
    }

    /** The season being written (the last one, not started yet), made if needed. */
    private Season draft() {
        Season last = this.seasons.isEmpty() ? null : this.seasons.get(this.seasons.size() - 1);
        if (last == null || last.started > 0) {
            last = new Season();
            last.number = this.seasons.size() + 1;
            last.name = "Season " + last.number;
            this.seasons.add(last);
        }

        return last;
    }

    private static String left(Season s) {
        if (s.started <= 0) {
            return "not started";
        }

        Duration d = Duration.between(Instant.now(), Instant.ofEpochMilli(s.started).plus(Duration.ofDays(s.days)));
        if (d.isNegative()) {
            return "ended";
        }

        long days = d.toDays();
        return days > 0 ? days + " day" + (days == 1 ? "" : "s") + " left" : d.toHours() + "h left";
    }

    private void card(CommandSender to, Season s) {
        to.sendMessage(Text.mm("<dark_gray><st>                                        </st>"));
        to.sendMessage(Text.mm("<gold><b>SEASON " + s.number + "</b> <dark_gray>» <yellow><b>" + Text.esc(s.name)));
        if (!s.theme.isBlank()) {
            to.sendMessage(Text.mm("<gray><i>" + Text.esc(s.theme)));
        }

        for (String r : s.rules) {
            to.sendMessage(Text.mm("<dark_gray>• <white>" + Text.esc(r)));
        }

        to.sendMessage(Text.mm("<gray>" + s.days + " days <dark_gray>• <aqua>" + left(s)));
        to.sendMessage(Text.mm("<dark_gray><st>                                        </st>"));
    }

    private boolean soon() {
        return this.soonName != null && !this.soonName.isBlank();
    }

    private void soonCard(CommandSender to) {
        to.sendMessage(Text.mm("<dark_gray><st>                                        </st>"));
        to.sendMessage(Text.mm("<light_purple><b>COMING SOON</b> <dark_gray>» <gradient:#B06BFF:#FF5FA2><b>" + Text.esc(this.soonName)));
        if (!this.soonTheme.isBlank()) {
            to.sendMessage(Text.mm("<gray><i>" + Text.esc(this.soonTheme)));
        }

        for (String r : this.soonRules) {
            to.sendMessage(Text.mm("<dark_gray>• <white>" + Text.esc(r)));
        }

        to.sendMessage(Text.mm("<dark_gray><st>                                        </st>"));
    }

    /** Coming into the SMP: a short line about the season. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        Season s = this.current();
        Player p = e.getPlayer();
        if (s != null && this.plugin.modes() != null && this.plugin.modes().smpWorld(p.getWorld()) && !this.plugin.modes().smpWorld(e.getFrom())) {
            p.sendMessage(Text.mm("<gold>Season " + s.number + ": <yellow><b>" + Text.esc(s.name) + "</b> <dark_gray>• <aqua>" + left(s) + " <dark_gray>• <gray>/season"));
        }

        if (this.soon() && this.plugin.modes() != null && this.plugin.modes().smpWorld(p.getWorld()) && !this.plugin.modes().smpWorld(e.getFrom())) {
            p.sendMessage(Text.mm("<light_purple>Coming soon: <gradient:#B06BFF:#FF5FA2><b>" + Text.esc(this.soonName) + "</b></gradient> <dark_gray>• <gray>/season soon"));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        boolean admin = sender.hasPermission("cmcpvp.admin");
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String rest = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "";
        if (sub.equals("soon") && (!admin || rest.isBlank())) {
            if (this.soon()) {
                this.soonCard(sender);
            } else {
                sender.sendMessage(Text.mm("<gray>Nothing announced yet."));
            }

            return true;
        }

        if (sub.isEmpty() || !admin) {
            Season s = this.current();
            if (s == null) {
                sender.sendMessage(Text.mm("<gray>No SMP season has started yet."));
            } else {
                this.card(sender, s);
            }

            if (this.soon()) {
                this.soonCard(sender);
            }

            return true;
        }

        switch (sub) {
            case "new" -> {
                Season d = this.draft();
                if (!rest.isBlank()) {
                    d.name = rest;
                }

                this.save();
                sender.sendMessage(Text.mm("<green>Writing season " + d.number + ": <white>" + Text.esc(d.name) + "</white>. <gray>Next: /season theme, /season rule, /season days, /season start"));
            }
            case "theme" -> {
                Season d = this.draft();
                d.theme = rest;
                this.save();
                sender.sendMessage(Text.mm("<green>Theme of season " + d.number + ": <white>" + Text.esc(rest)));
            }
            case "rule" -> {
                Season d = this.draft();
                if (rest.equalsIgnoreCase("clear")) {
                    d.rules.clear();
                } else if (!rest.isBlank()) {
                    d.rules.add(rest);
                }

                this.save();
                sender.sendMessage(Text.mm("<green>Season " + d.number + " has " + d.rules.size() + " rule line(s)."));
            }
            case "days" -> {
                Season d = this.draft();
                try {
                    d.days = Math.max(1, Integer.parseInt(rest.trim()));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Text.mm("<red>Usage: /season days <number>"));
                    return true;
                }

                this.save();
                sender.sendMessage(Text.mm("<green>Season " + d.number + " lasts " + d.days + " days."));
            }
            case "idea" -> {
                int n;
                try {
                    n = Integer.parseInt(rest.trim()) - 1;
                } catch (NumberFormatException e) {
                    n = -1;
                }

                if (n < 0 || n >= IDEAS.length) {
                    sender.sendMessage(Text.mm("<red>Usage: /season idea <1-" + IDEAS.length + "> <gray>(see /season ideas)"));
                    return true;
                }

                Season d = this.draft();
                d.name = IDEAS[n][0];
                d.theme = IDEAS[n][1];
                d.rules.clear();
                d.rules.addAll(List.of(IDEAS[n][2].split("\\|")));
                this.save();
                sender.sendMessage(Text.mm("<green>Season " + d.number + " is now written from idea " + (n + 1) + ". <gray>Check it with /season preview, start it with /season start."));
            }
            case "ideas" -> {
                sender.sendMessage(Text.mm("<gold><b>Season ideas</b> <gray>(/season idea <n> to use one)"));
                for (int i = 0; i < IDEAS.length; i++) {
                    sender.sendMessage(Text.mm("<yellow>" + (i + 1) + ". <white>" + IDEAS[i][0] + " <dark_gray>- <gray>" + IDEAS[i][1]));
                }
            }
            case "soon" -> {
                // /season soon <name> | soon theme <text> | soon rule <text|clear> | soon off
                String[] w = rest.split(" ", 2);
                String what = w[0].toLowerCase(Locale.ROOT);
                String val = w.length > 1 ? w[1] : "";
                switch (what) {
                    case "off" -> this.soonName = "";
                    case "theme" -> this.soonTheme = val;
                    case "rule" -> {
                        if (val.equalsIgnoreCase("clear")) {
                            this.soonRules.clear();
                        } else if (!val.isBlank()) {
                            this.soonRules.add(val);
                        }
                    }
                    default -> this.soonName = rest;
                }

                this.save();
                if (this.soon()) {
                    this.soonCard(sender);
                } else {
                    sender.sendMessage(Text.mm("<yellow>Coming-soon teaser hidden."));
                }
            }
            case "preview" -> this.card(sender, this.draft());
            case "list" -> {
                for (int i = 0; i < this.seasons.size(); i++) {
                    Season s = this.seasons.get(i);
                    sender.sendMessage(Text.mm((i == this.current ? "<green>▶ " : "<gray>- ") + "Season " + s.number + ": <white>" + Text.esc(s.name) + " <dark_gray>(" + left(s) + ")"));
                }
            }
            case "start" -> {
                Season d = this.draft();
                d.started = System.currentTimeMillis();
                this.current = this.seasons.indexOf(d);
                this.save();
                Title t = Title.title(Text.mm("<gold><b>SEASON " + d.number), Text.mm("<yellow>" + Text.esc(d.name)),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofSeconds(1)));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(t);
                    Snd.play(p, "ui.toast.challenge_complete", 0.8F, 1.0F);
                    this.card(p, d);
                }

                sender.sendMessage(Text.mm("<green>Season " + d.number + " started."));
            }
            default -> {
                sender.sendMessage(Text.mm("<gold><b>/season</b> <gray>new <name> | theme <text> | rule <text|clear> | days <n> | idea <n> | ideas | preview | start | list | soon <name|theme|rule|off>"));
                Season s = this.current();
                if (s != null) {
                    this.card(sender, s);
                }
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1 && !sender.hasPermission("cmcpvp.admin")) {
            return "soon".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("soon") : List.of();
        }

        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("new", "theme", "rule", "days", "idea", "ideas", "preview", "start", "list", "soon"));
            out.removeIf(s -> !s.startsWith(args[0].toLowerCase(Locale.ROOT)));
            return out;
        }

        return List.of();
    }
}
