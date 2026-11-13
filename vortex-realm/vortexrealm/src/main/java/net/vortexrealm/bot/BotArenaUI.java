package net.vortexrealm.bot;

import io.papermc.paper.registry.data.dialog.ActionButton;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.arena.Arena;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.dialog.LobbyDialogs;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The Bot Arena screen in the style of the big practice servers: a header with your record and the featured
 * training of the day, tabs as buttons (Fight, Sword, Air & Elytra, Spear, Crystal, Records, Academy), a grid of
 * kits or training cards with your best result on each, Quick Play and Rematch. Records are kept in bot-records.yml.
 */
public final class BotArenaUI {
    public static enum Tab {
        FIGHT("⚔ Fight", "<#FF8A3D>"),
        SWORD("🗡 Sword", "<#7FD4FF>"),
        AIR("🪂 Air & Elytra", "<#B48CFF>"),
        SPEAR("🔱 Spear", "<#5CE1B0>"),
        CRYSTAL("💎 Crystal", "<#FF5FA2>"),
        RECORDS("📜 Records", "<#FFD54A>");

        final String label;
        final String color;

        Tab(String label, String color) {
            this.label = label;
            this.color = color;
        }
    }

    private final VortexRealm plugin;
    private final BotManager bots;
    private final File file;
    private YamlConfiguration data;

    BotArenaUI(VortexRealm plugin, BotManager bots) {
        this.plugin = plugin;
        this.bots = bots;
        this.file = new File(plugin.getDataFolder(), "bot-records.yml");
        this.data = YamlConfiguration.loadConfiguration(this.file);
    }

    private void save() {
        try {
            this.data.save(this.file);
        } catch (IOException var2) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save bot-records.yml", (Throwable)var2);
        }
    }

    // ------------------------------------------------------------------ records

    /** The number a training is scored by (higher is better) and its unit. */
    static double score(PracticeBot b) {
        return switch (b.dummy) {
            case DTAP -> b.dtaps;
            case STUNSLAM, AIR_SLAM, ELYTRA_SHIELD, SPEAR_SLAM -> b.slams;
            case SPEAR_MACE -> b.spearMaces;
            case SPEAR_LUNGE -> b.bestLunge;
            case WTAP -> b.bestWtap;
            case CRIT -> b.crits;
            case BOW -> b.bestShot;
            case MACE, ELYTRA -> b.bestSmash;
            case SHIELD -> b.shieldBreaks;
            case CRYSTAL -> b.pops;
            case ANCHOR -> b.anchorDamage;
            case PEARL -> b.pearlsLanded;
            case POT -> b.potsThrown;
            case SPEAR -> b.bestSpear;
            case COMBO -> b.bestCombo;
        };
    }

    static String unit(DummyType t) {
        return switch (t) {
            case DTAP -> "d-taps";
            case STUNSLAM, AIR_SLAM, ELYTRA_SHIELD, SPEAR_SLAM -> "stun slams";
            case SPEAR_MACE -> "combos";
            case SPEAR_LUNGE, BOW -> "blocks";
            case WTAP -> "w-tap streak";
            case CRIT -> "crits";
            case MACE, ELYTRA, SPEAR -> "damage";
            case SHIELD -> "shield breaks";
            case CRYSTAL -> "pops";
            case ANCHOR -> "anchor damage";
            case PEARL -> "pearls landed";
            case POT -> "pots";
            case COMBO -> "combo";
        };
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long)v) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** After a fight or a training: wins and losses, streaks and personal bests. */
    void record(Player p, PracticeBot b, Kit kit, boolean won, String why) {
        String base = "players." + p.getUniqueId() + ".";
        this.data.set(base + "name", p.getName());
        if (b.dummy == null) {
            if (why.equals("left") || why.equals("time") || why.equals("error")) {
                return;
            }

            String d = b.diff.name().toLowerCase(Locale.ROOT);
            this.data.set(base + (won ? "wins." : "losses.") + d, this.data.getInt(base + (won ? "wins." : "losses.") + d) + 1);
            int streak = won ? this.data.getInt(base + "streak") + 1 : 0;
            this.data.set(base + "streak", streak);
            this.data.set(base + "best-streak", Math.max(streak, this.data.getInt(base + "best-streak")));
            this.data.set(base + "last", kit.id + ":" + d);
        } else {
            double sc = score(b);
            String key = base + "best." + b.dummy.name().toLowerCase(Locale.ROOT);
            if (sc > this.data.getDouble(key, 0.0)) {
                this.data.set(key, sc);
                if (sc > 0.0) {
                    p.sendMessage(Text.mm("<gold><b>★ New personal best!</b> <white>" + fmt(sc) + " <gray>" + unit(b.dummy) + " on " + b.dummy.display));
                }
            }

            this.data.set(base + "last-train", b.dummy.name());
        }

        this.save();
    }

    private double best(UUID u, DummyType t) {
        return this.data.getDouble("players." + u + ".best." + t.name().toLowerCase(Locale.ROOT), 0.0);
    }

    private List<String[]> top(DummyType t, int n) {
        List<String[]> out = new ArrayList<>();
        ConfigurationSection ps = this.data.getConfigurationSection("players");
        if (ps == null) {
            return out;
        }

        for (String u : ps.getKeys(false)) {
            double v = ps.getDouble(u + ".best." + t.name().toLowerCase(Locale.ROOT), 0.0);
            if (v > 0.0) {
                out.add(new String[]{ps.getString(u + ".name", "?"), fmt(v), String.valueOf(v)});
            }
        }

        out.sort(Comparator.comparingDouble((String[] r) -> Double.parseDouble(r[2])).reversed());
        return out.size() > n ? out.subList(0, n) : out;
    }

    /** Today's featured training (the same for everyone, changes every day). */
    static DummyType featured() {
        DummyType[] all = DummyType.values();
        return all[(int)(LocalDate.now().toEpochDay() % all.length)];
    }

    // ------------------------------------------------------------------ screens

    private LobbyDialogs ui() {
        return this.plugin.dialogs().lobby();
    }

    private List<Component> header(Player p, Tab tab) {
        String base = "players." + p.getUniqueId() + ".";
        int w = 0;
        int l = 0;
        for (Difficulty d : Difficulty.values()) {
            w += this.data.getInt(base + "wins." + d.name().toLowerCase(Locale.ROOT));
            l += this.data.getInt(base + "losses." + d.name().toLowerCase(Locale.ROOT));
        }

        int expert = this.data.getInt(base + "wins.expert");
        DummyType f = featured();
        List<Component> h = new ArrayList<>();
        h.add(Text.mm("<gray>Record <green>" + w + "W</green> <dark_gray>/</dark_gray> <red>" + l + "L</red> <dark_gray>•</dark_gray> <gray>Expert wins <gold>" + expert
            + "</gold> <dark_gray>•</dark_gray> <gray>Best streak <white>" + this.data.getInt(base + "best-streak")));
        h.add(Text.mm("<yellow>✦ Featured today: <white>" + f.display + " <dark_gray>- <gray>" + f.goal));
        if (!Cfg.bool(Opt.BOTS_ENABLED) || !Cfg.bool(Opt.BOTS_TRAINING)) {
            h.add(Text.mm("<red>" + (!Cfg.bool(Opt.BOTS_ENABLED) ? "Bot fights" : "Training") + " is turned off on this server."));
        }

        return h;
    }

    private static Component title(Tab tab) {
        return Text.mm("<b><gradient:#FFB347:#FF5E62:#C86DD7>BOT ARENA</gradient></b> <dark_gray>»</dark_gray> " + tab.color + tab.label.substring(tab.label.indexOf(' ') + 1));
    }

    /** The tab buttons (the open one is marked) plus Quick Play, Search and Academy. */
    private List<ActionButton> tabs(Player p, Tab open, Arena arena) {
        List<ActionButton> b = new ArrayList<>();
        for (Tab t : Tab.values()) {
            String label = t == open ? "<b><u>" + t.color + t.label + "</u></b>" : t.color + t.label;
            b.add(this.ui().btn(p, label, null, () -> this.open(p, t, arena)));
        }

        String last = this.data.getString("players." + p.getUniqueId() + ".last");
        b.add(this.ui().btn(p, "<green><b>▶ Quick Play</b>", last == null ? "<gray>Your last bot (play once first)" : "<gray>Again: <white>" + last.replace(":", " · "), () -> {
            String l2 = this.data.getString("players." + p.getUniqueId() + ".last");
            if (l2 == null) {
                this.open(p, Tab.FIGHT, arena);
            } else {
                String[] kd = l2.split(":");
                Kit k = this.plugin.kits().get(kd[0]);
                p.closeDialog();
                if (k != null) {
                    this.bots.fight(p, k, Difficulty.parse(kd.length > 1 ? kd[1] : "medium"), arena);
                }
            }
        }));
        b.add(this.ui().btn(p, "<yellow>🔍 Search", "<gray>Find a gamemode by name", () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Search a gamemode"), "Name", "", 24, s -> this.fightTab(p, s, arena))));
        b.add(this.ui().btn(p, "<aqua>📘 Academy", "<gray>Step-by-step lessons", () -> {
            if (this.plugin.academy() != null) {
                this.plugin.academy().courses(p, null);
            }
        }));
        return b;
    }

    public void open(Player p, Tab tab, Arena arena) {
        switch (tab) {
            case FIGHT -> this.fightTab(p, null, arena);
            case RECORDS -> this.recordsTab(p);
            default -> this.trainingTab(p, tab);
        }
    }

    void fightTab(Player p, String search, Arena arena) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        String q = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
        for (Kit k : this.plugin.kits().sorted()) {
            if (k.enabled && k.hasContents() && !k.id.equals("mole") && (arena == null || arena.allows(k))
                && (q.isEmpty() || k.displayName.toLowerCase(Locale.ROOT).contains(q) || k.id.contains(q))) {
                tiles.add(new LobbyDialogs.Tile(
                    k.iconStack(),
                    Text.mm(k.color + "<b>" + Text.esc(k.displayName)),
                    Text.mm("<gray>" + this.bots.skillsOf(k)),
                    List.of(Text.mm(k.color + "<b>" + Text.esc(k.displayName)), Text.mm("<gray>The bot uses: <white>" + this.bots.skillsOf(k)), Text.mm("<green>▶ Choose the difficulty")),
                    () -> this.difficulty(p, k, arena)
                ));
            }
        }

        List<Component> h = this.header(p, Tab.FIGHT);
        if (!q.isEmpty()) {
            h.add(Text.mm("<gray>Search: <white>" + Text.esc(q)));
        }

        this.ui().screen(p, title(Tab.FIGHT), tiles, h, Text.mm("<gray>No gamemode matches \"" + Text.esc(q) + "\"."), this.tabs(p, Tab.FIGHT, arena), 3, this.ui().close(p));
    }

    void difficulty(Player p, Kit k, Arena arena) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        String base = "players." + p.getUniqueId() + ".";
        for (Difficulty d : Difficulty.values()) {
            String dk = d.name().toLowerCase(Locale.ROOT);
            tiles.add(new LobbyDialogs.Tile(
                new ItemStack(switch (d) {
                    case EASY -> Material.LIME_DYE;
                    case MEDIUM -> Material.YELLOW_DYE;
                    case HARD -> Material.ORANGE_DYE;
                    case EXPERT -> Material.RED_DYE;
                }),
                Text.mm(d.label + " <b>Bot"),
                Text.mm("<gray>" + d.about + " <dark_gray>• <green>" + this.data.getInt(base + "wins." + dk) + "W <red>" + this.data.getInt(base + "losses." + dk) + "L"),
                List.of(Text.mm(d.label + " <b>Bot"), Text.mm("<gray>" + d.about), Text.mm("<green>▶ Fight")),
                () -> {
                    p.closeDialog();
                    this.bots.fight(p, k, d, arena);
                }
            ));
        }

        tiles.add(new LobbyDialogs.Tile(new ItemStack(Material.NETHER_STAR), Text.mm("<light_purple><b>★ Surprise me"), Text.mm("<gray>A random difficulty"),
            List.of(Text.mm("<light_purple><b>★ Surprise me")), () -> {
                p.closeDialog();
                Difficulty[] all = Difficulty.values();
                this.bots.fight(p, k, all[java.util.concurrent.ThreadLocalRandom.current().nextInt(all.length)], arena);
            }));
        List<ActionButton> b = new ArrayList<>();
        b.add(this.ui().btn(p, "<gray>« All gamemodes", null, () -> this.fightTab(p, null, arena)));
        b.add(this.ui().btn(p, "<aqua>🎯 Training", null, () -> this.open(p, Tab.SWORD, arena)));
        this.ui().screen(p, Text.mm("<b><gradient:#FFB347:#FF5E62>BOT ARENA</gradient></b> <dark_gray>»</dark_gray> ").append(k.name()), tiles,
            List.of(Text.mm("<gray>The bot uses: <white>" + this.bots.skillsOf(k))), null, b, 2, this.ui().close(p));
    }

    void trainingTab(Player p, Tab tab) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        DummyType feat = featured();
        for (DummyType t : DummyType.values()) {
            if (!t.group().equals(tab.name())) {
                continue;
            }

            double best = this.best(p.getUniqueId(), t);
            tiles.add(new LobbyDialogs.Tile(
                new ItemStack(t.icon),
                Text.mm((t == feat ? "<yellow>✦ " : "") + tab.color + "<b>" + t.display),
                Text.mm("<gray>" + t.goal + " <dark_gray>• <gray>Best <white>" + (best > 0 ? fmt(best) + " " + unit(t) : "-")),
                List.of(Text.mm(tab.color + "<b>" + t.display), Text.mm("<gray>" + t.what), Text.mm("<yellow>Goal: <white>" + t.goal),
                    Text.mm("<gray>Your best: <white>" + (best > 0 ? fmt(best) + " " + unit(t) : "none yet")), Text.mm("<green>▶ Start training")),
                () -> {
                    p.closeDialog();
                    this.bots.train(p, t);
                }
            ));
        }

        this.ui().screen(p, title(tab), tiles, this.header(p, tab), Text.mm("<gray>Nothing here yet."), this.tabs(p, tab, null), 3, this.ui().close(p));
    }

    void recordsTab(Player p) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        for (DummyType t : DummyType.values()) {
            List<String[]> top = this.top(t, 3);
            double mine = this.best(p.getUniqueId(), t);
            StringBuilder line = new StringBuilder("<gray>You <white>" + (mine > 0 ? fmt(mine) : "-"));
            String[] medal = {"<gold>①", "<gray>②", "<#CD7F32>③"};
            for (int i = 0; i < top.size(); i++) {
                line.append(" <dark_gray>• ").append(medal[i]).append(" <white>").append(Text.esc(top.get(i)[0])).append(" <gray>").append(top.get(i)[1]);
            }

            tiles.add(new LobbyDialogs.Tile(new ItemStack(t.icon), Text.mm("<gold><b>" + t.display + "</b> <dark_gray>(" + unit(t) + ")"), Text.mm(line.toString()),
                List.of(Text.mm("<gold><b>" + t.display)), () -> {
                    p.closeDialog();
                    this.bots.train(p, t);
                }));
        }

        this.ui().screen(p, title(Tab.RECORDS), tiles, this.header(p, Tab.RECORDS), null, this.tabs(p, Tab.RECORDS, null), 3, this.ui().close(p));
    }
}
