package net.vortexpvp.board;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.registry.data.dialog.ActionButton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Msg;
import net.vortexpvp.data.Database;
import net.vortexpvp.deco.HoloFx;
import net.vortexpvp.dialog.Dialogs;
import net.vortexpvp.dialog.LobbyDialogs;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.rank.Tier;
import net.vortexpvp.util.Fmt;
import net.vortexpvp.util.Locs;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Leaderboard holograms: live top lists of a kit (or several, cycling) by any stat, in any hologram design, with the
 * title, colours, size, rows, facing and refresh rate set per hologram from an edit dialog. Old holograms.yml entries
 * (kit + location only) load with the look they always had.
 */
public final class HologramManager {
    public static final List<String> STATS = List.of("ELO", "WINS", "WINRATE", "STREAK", "FFA_KILLS", "FFA_KD");
    private final VortexPvP plugin;
    private final File file;
    private final NamespacedKey key;
    private final Map<String, HologramManager.Holo> holos = new LinkedHashMap<>();
    private final Map<String, HologramManager.Live> live = new LinkedHashMap<>();
    /** Stat lists by "kit|stat", refreshed off the main thread. */
    private final Map<String, List<Database.StatRow>> data = new ConcurrentHashMap<>();
    private final Map<String, Long> fetched = new ConcurrentHashMap<>();
    private int ticks;

    public HologramManager(VortexPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "holograms.yml");
        this.key = new NamespacedKey(plugin, "lb_holo");
    }

    public static enum Design {
        CLASSIC("Classic", "Plain lines with a shadow", HoloFx.Kind.NONE),
        GLASS("Glass Card", "A dark see-through card", HoloFx.Kind.NONE),
        NEON("Neon Sign", "A card in the accent colour that pulses", HoloFx.Kind.NONE),
        FRAME("Framed Board", "Dark oak frame, gold corners, lanterns", HoloFx.Kind.FRAME),
        HOLO("Sci-Fi Hologram", "Cyan projection with a scanning line", HoloFx.Kind.HOLO),
        ROYAL("Royal", "Gold trim, purple card, spinning crown", HoloFx.Kind.ROYAL),
        MARQUEE("Marquee", "Scrolling title, striped rows", HoloFx.Kind.NONE),
        ORBIT("Orbit", "Kit items orbit the card", HoloFx.Kind.ORBIT),
        PODIUM("Podium", "Top 3 stand on gold, silver and bronze pedestals");

        public final String display;
        public final String desc;
        public final HoloFx.Kind fx;

        private Design(String display, String desc) {
            this(display, desc, HoloFx.Kind.NONE);
        }

        private Design(String display, String desc, HoloFx.Kind fx) {
            this.display = display;
            this.desc = desc;
            this.fx = fx;
        }

        /** Designs with parts on the card plane always face one way (they can't turn to the viewer). */
        boolean fixed() {
            return this == FRAME || this == HOLO || this == ROYAL || this == PODIUM;
        }
    }

    /** One leaderboard hologram and all its settings. */
    public static final class Holo {
        final String id;
        String location;
        /** One kit, or several that take turns. "overall" = all kits together. */
        List<String> kits = new ArrayList<>(List.of("overall"));
        int cycleSeconds = 10;
        String stat = "ELO";
        int rows = 10;
        HologramManager.Design design = HologramManager.Design.CLASSIC;
        String title = "";
        String top1 = "#FFD700";
        String top2 = "#C0C0C0";
        String top3 = "#CD7F32";
        String rest = "#AAAAAA";
        String accent = "#FF7A1A";
        int bgAlpha = -1;
        float scale = 1.0F;
        int lineWidth = 220;
        boolean showTier = true;
        boolean showWL;
        /** Fixed facing (the side it is read from); billboard = always turns to the viewer. */
        boolean billboard = true;
        float yaw;
        /** Height of the card above the stored location (old entries were stored at card height). */
        double offset;
        int refreshSeconds = 60;

        Holo(String id) {
            this.id = id;
        }

        Location loc() {
            return Locs.parse(this.location);
        }
    }

    private static final class Live {
        TextDisplay card;
        HoloFx.Decor decor;
        final List<Entity> extra = new ArrayList<>();
        int kitIndex;
        long kitSince = System.currentTimeMillis();
        String shown = "";
        /** Who stands on the podium (rebuilt when the top 3 change, e.g. when the first data arrives). */
        String podium = "";

        void remove() {
            if (this.card != null) {
                this.card.remove();
            }

            if (this.decor != null) {
                this.decor.remove();
            }

            for (Entity e : this.extra) {
                e.remove();
            }

            this.extra.clear();
        }

        boolean valid() {
            return this.card != null && this.card.isValid();
        }
    }

    // ------------------------------------------------------------------ storage

    public void load() {
        this.shutdown();
        this.holos.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection sec = y.getConfigurationSection("holograms");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(id);
                if (s != null) {
                    HologramManager.Holo h = new HologramManager.Holo(id);
                    h.location = s.getString("location");
                    List<String> kits = s.getStringList("kits");
                    h.kits = new ArrayList<>(kits.isEmpty() ? List.of(s.getString("kit", "overall")) : kits);
                    h.cycleSeconds = Math.max(3, s.getInt("cycle-seconds", 10));
                    h.stat = STATS.contains(s.getString("stat", "ELO")) ? s.getString("stat", "ELO") : "ELO";
                    h.rows = Math.max(3, Math.min(20, s.getInt("rows", 10)));
                    try {
                        h.design = HologramManager.Design.valueOf(s.getString("design", "CLASSIC"));
                    } catch (IllegalArgumentException var9) {
                        h.design = HologramManager.Design.CLASSIC;
                    }

                    h.title = s.getString("title", "");
                    h.top1 = s.getString("colors.top1", h.top1);
                    h.top2 = s.getString("colors.top2", h.top2);
                    h.top3 = s.getString("colors.top3", h.top3);
                    h.rest = s.getString("colors.rest", h.rest);
                    h.accent = s.getString("colors.accent", h.accent);
                    h.bgAlpha = s.getInt("background-alpha", -1);
                    h.scale = (float)Math.max(0.3, Math.min(4.0, s.getDouble("scale", 1.0)));
                    h.lineWidth = Math.max(100, Math.min(600, s.getInt("line-width", 220)));
                    h.showTier = s.getBoolean("show-tier", true);
                    h.showWL = s.getBoolean("show-wins-losses", false);
                    h.billboard = s.getBoolean("billboard", true);
                    h.yaw = (float)s.getDouble("yaw", 0.0);
                    h.offset = s.getDouble("offset", 0.0);
                    h.refreshSeconds = Math.max(10, s.getInt("refresh-seconds", 60));
                    this.holos.put(id, h);
                }
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
            "Leaderboard holograms. Edit them in game: stand next to one, /pvpadmin -> Leaderboard Hologram -> Edit Nearest.",
            "stat: ELO, WINS, WINRATE, STREAK, FFA_KILLS, FFA_KD. design: CLASSIC, GLASS, NEON, FRAME, HOLO, ROYAL, MARQUEE, ORBIT, PODIUM.",
            "title: MiniMessage, <kit> and <stat> are replaced. kits: several = they take turns every cycle-seconds."
        ));
        y.set("version", 2);

        for (HologramManager.Holo h : this.holos.values()) {
            String b = "holograms." + h.id + ".";
            y.set(b + "location", h.location);
            y.set(b + "kit", h.kits.get(0));
            y.set(b + "kits", h.kits);
            y.set(b + "cycle-seconds", h.cycleSeconds);
            y.set(b + "stat", h.stat);
            y.set(b + "rows", h.rows);
            y.set(b + "design", h.design.name());
            y.set(b + "title", h.title);
            y.set(b + "colors.top1", h.top1);
            y.set(b + "colors.top2", h.top2);
            y.set(b + "colors.top3", h.top3);
            y.set(b + "colors.rest", h.rest);
            y.set(b + "colors.accent", h.accent);
            y.set(b + "background-alpha", h.bgAlpha);
            y.set(b + "scale", h.scale);
            y.set(b + "line-width", h.lineWidth);
            y.set(b + "show-tier", h.showTier);
            y.set(b + "show-wins-losses", h.showWL);
            y.set(b + "billboard", h.billboard);
            y.set(b + "yaw", h.yaw);
            y.set(b + "offset", h.offset);
            y.set(b + "refresh-seconds", h.refreshSeconds);
        }

        try {
            y.save(this.file);
        } catch (IOException var5) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save holograms.yml", (Throwable)var5);
        }
    }

    public int count() {
        return this.holos.size();
    }

    // ------------------------------------------------------------------ placing and editing

    /** New leaderboard: pick the kit, it appears in front of you facing you, then the editor opens. */
    public void prompt(Player p) {
        this.plugin.dialogs().choice(p, Text.mm("<gold><b>Leaderboard Hologram"), "Leaderboard", this.kitChoices(), "overall", kit -> {
            HologramManager.Holo h = new HologramManager.Holo(UUID.randomUUID().toString().substring(0, 8));
            Location feet = p.getLocation();
            Vector ahead = feet.getDirection().setY(0);
            if (ahead.lengthSquared() < 0.01) {
                ahead = new Vector(0, 0, 1);
            }

            Location at = feet.clone().add(ahead.normalize().multiply(3.0));
            at.setY(feet.getY());
            h.location = Locs.serialize(at);
            h.offset = 1.6;
            h.kits = new ArrayList<>(List.of(kit));
            h.design = HologramManager.Design.GLASS;
            h.yaw = feet.getYaw() + 180.0F;
            this.holos.put(h.id, h);
            this.save();
            this.respawn(h);
            Msg.send(p, "admin.hologram-created");
            this.edit(p, h);
        });
    }

    private List<Dialogs.Choice> kitChoices() {
        List<Dialogs.Choice> choices = new ArrayList<>();
        choices.add(new Dialogs.Choice("overall", "<gold>Overall"));

        for (Kit k : this.plugin.kits().sorted()) {
            if (k.ranked || k.unranked) {
                choices.add(new Dialogs.Choice(k.id, k.nameMM()));
            }
        }

        return choices;
    }

    private HologramManager.Holo nearest(Location l, double max) {
        HologramManager.Holo best = null;
        double bestD = max * max;

        for (HologramManager.Holo h : this.holos.values()) {
            Location hl = h.loc();
            if (hl != null && hl.getWorld() == l.getWorld()) {
                double d = hl.distanceSquared(l);
                if (d < bestD) {
                    bestD = d;
                    best = h;
                }
            }
        }

        return best;
    }

    /** Opens the editor of the leaderboard hologram nearest to the player (8 blocks). */
    public boolean editNearest(Player p) {
        HologramManager.Holo h = this.nearest(p.getLocation(), 8.0);
        if (h != null) {
            this.edit(p, h);
        }

        return h != null;
    }

    public boolean removeNearest(Location l) {
        HologramManager.Holo best = this.nearest(l, 5.0);
        if (best == null) {
            return false;
        } else {
            this.remove(best);
            return true;
        }
    }

    private void remove(HologramManager.Holo h) {
        this.holos.remove(h.id);
        HologramManager.Live l = this.live.remove(h.id);
        if (l != null) {
            l.remove();
        }

        this.save();
    }

    private void change(Player p, HologramManager.Holo h) {
        this.save();
        this.respawn(h);
        Snd.click(p);
        this.edit(p, h);
    }

    public void edit(Player p, HologramManager.Holo h) {
        LobbyDialogs ui = this.plugin.dialogs().lobby();
        List<Component> header = List.of(
            Text.mm("<gray>Kits <white>" + Text.esc(String.join(", ", h.kits)) + (h.kits.size() > 1 ? " <gray>(every " + h.cycleSeconds + "s)" : "") + " <dark_gray>• <gray>Stat <white>"
                + statName(h.stat) + " <dark_gray>• <gray>Rows <white>" + h.rows),
            Text.mm("<gray>Design <white>" + h.design.display + " <dark_gray>• <gray>Size <white>" + String.format(Locale.US, "%.2f", h.scale) + " <dark_gray>• <gray>Facing <white>"
                + (h.billboard && !h.design.fixed() ? "turns to the viewer" : Math.round(h.yaw) + "°") + " <dark_gray>• <gray>Refresh <white>" + h.refreshSeconds + "s"),
            Text.mm("<gray>Colours <" + h.top1 + ">#1 <" + h.top2 + ">#2 <" + h.top3 + ">#3 <" + h.rest + ">rest <dark_gray>• <gray>Accent <" + h.accent + ">■ <dark_gray>• <gray>Tier "
                + (h.showTier ? "<green>on" : "<red>off") + " <dark_gray>• <gray>W/L " + (h.showWL ? "<green>on" : "<red>off"))
        );
        List<ActionButton> b = new ArrayList<>();
        b.add(ui.btn(p, "<gold>Kit", "<gray>Which leaderboard", () -> this.plugin.dialogs().choice(p, Text.mm("<gold><b>Kit"), "Kit", this.kitChoices(), h.kits.get(0), v -> {
            h.kits = new ArrayList<>(List.of(v));
            this.change(p, h);
        })));
        b.add(ui.btn(p, "<gold>+ Cycle Kit", "<gray>Add a kit: the board takes turns", () -> this.plugin.dialogs().choice(p, Text.mm("<gold><b>Add a kit"), "Kit", this.kitChoices(), "overall", v -> {
            if (!h.kits.contains(v)) {
                h.kits.add(v);
            }

            this.change(p, h);
        })));
        b.add(ui.btn(p, "<gold>Stat", null, () -> {
            List<Dialogs.Choice> c = new ArrayList<>();

            for (String s : STATS) {
                c.add(new Dialogs.Choice(s, "<white>" + statName(s)));
            }

            this.plugin.dialogs().choice(p, Text.mm("<gold><b>Stat"), "Ranked by", c, h.stat, v -> {
                h.stat = v;
                this.change(p, h);
            });
        }));
        b.add(ui.btn(p, "<gold>Rows…", null, () -> this.plugin.dialogs().number(p, Text.mm("<gold><b>Rows"), "Players shown", 3.0F, 20.0F, 1.0F, h.rows, v -> {
            h.rows = Math.round(v);
            this.change(p, h);
        })));
        b.add(ui.btn(p, "<light_purple>Design", null, () -> {
            List<Dialogs.Choice> c = new ArrayList<>();

            for (HologramManager.Design d : HologramManager.Design.values()) {
                c.add(new Dialogs.Choice(d.name(), "<white>" + d.display + " <gray>- " + d.desc));
            }

            this.plugin.dialogs().choice(p, Text.mm("<gold><b>Design"), "Design", c, h.design.name(), v -> {
                h.design = HologramManager.Design.valueOf(v);
                this.change(p, h);
            });
        }));
        b.add(ui.btn(p, "<light_purple>Title…", "<gray>MiniMessage; <kit> and <stat> work. Empty = automatic", () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Title"), "Title", h.title, 200, v -> {
            h.title = v;
            this.change(p, h);
        })));
        b.add(ui.btn(p, "<light_purple>Colours…", "<gray>#1 #2 #3 rest, e.g. #FFD700 #C0C0C0 #CD7F32 #AAAAAA", () -> this.plugin.dialogs().text(
            p, Text.mm("<gold><b>Colours"), "#1 #2 #3 rest", h.top1 + " " + h.top2 + " " + h.top3 + " " + h.rest, 40, v -> {
                String[] c = v.trim().split("[ ,]+");
                if (c.length == 4 && String.join(" ", c).matches("(#[0-9A-Fa-f]{6} ?){4}")) {
                    h.top1 = c[0];
                    h.top2 = c[1];
                    h.top3 = c[2];
                    h.rest = c[3];
                }

                this.change(p, h);
            })));
        b.add(ui.btn(p, "<light_purple>Accent…", "<gray>Neon card and title colour", () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Accent"), "#RRGGBB", h.accent, 7, v -> {
            if (v.trim().matches("#?[0-9A-Fa-f]{6}")) {
                h.accent = v.trim().startsWith("#") ? v.trim() : "#" + v.trim();
            }

            this.change(p, h);
        })));
        b.add(ui.btn(p, "<light_purple>Background…", "<gray>Opacity 0-255, -1 = the design's", () -> this.plugin.dialogs().number(p, Text.mm("<gold><b>Background"), "Opacity", -1.0F, 255.0F, 5.0F, h.bgAlpha, v -> {
            h.bgAlpha = Math.round(v);
            this.change(p, h);
        })));
        b.add(ui.btn(p, "<aqua>Size…", null, () -> this.plugin.dialogs().number(p, Text.mm("<gold><b>Size"), "Scale", 0.3F, 4.0F, 0.05F, h.scale, v -> {
            h.scale = Math.round(v * 20.0F) / 20.0F;
            this.change(p, h);
        })));
        b.add(ui.btn(p, "<aqua>Line Width…", "<gray>Text wraps after this many pixels", () -> this.plugin.dialogs().number(p, Text.mm("<gold><b>Line width"), "Pixels", 100.0F, 600.0F, 10.0F, h.lineWidth, v -> {
            h.lineWidth = Math.round(v);
            this.change(p, h);
        })));
        b.add(ui.btn(p, "<aqua>Tier " + (h.showTier ? "<green>ON" : "<red>OFF"), null, () -> {
            h.showTier = !h.showTier;
            this.change(p, h);
        }));
        b.add(ui.btn(p, "<aqua>Wins/Losses " + (h.showWL ? "<green>ON" : "<red>OFF"), null, () -> {
            h.showWL = !h.showWL;
            this.change(p, h);
        }));
        b.add(ui.btn(p, "<aqua>Turns to Viewer " + (h.billboard ? "<green>ON" : "<red>OFF"), "<gray>Off = faces one way (framed designs always do)", () -> {
            h.billboard = !h.billboard;
            this.change(p, h);
        }));
        b.add(ui.btn(p, "<aqua>Face Me", "<gray>Turns it to face where you stand", () -> {
            Location hl = h.loc();
            if (hl != null) {
                Vector d = p.getLocation().toVector().subtract(hl.toVector());
                h.yaw = (float)Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
                h.billboard = false;
            }

            this.change(p, h);
        }));
        b.add(ui.btn(p, "<aqua>Move Here", "<gray>To your feet", () -> {
            h.location = Locs.serialize(p.getLocation());
            h.offset = 1.6;
            this.change(p, h);
        }));
        b.add(ui.btn(p, "<aqua>Up 0.25", null, () -> {
            h.offset += 0.25;
            this.change(p, h);
        }));
        b.add(ui.btn(p, "<aqua>Down 0.25", null, () -> {
            h.offset -= 0.25;
            this.change(p, h);
        }));
        b.add(ui.btn(p, "<aqua>Timing…", "<gray>Refresh and kit cycle seconds", () -> this.plugin.dialogs().number(p, Text.mm("<gold><b>Refresh"), "Refresh every (seconds)", 10.0F, 600.0F, 10.0F, h.refreshSeconds, v -> {
            h.refreshSeconds = Math.round(v);
            this.plugin.dialogs().number(p, Text.mm("<gold><b>Kit cycle"), "Next kit every (seconds)", 3.0F, 120.0F, 1.0F, h.cycleSeconds, c -> {
                h.cycleSeconds = Math.round(c);
                this.change(p, h);
            });
        })));
        b.add(ui.btn(p, "<green>Copy Here", "<gray>A copy with every setting, in front of you", () -> {
            HologramManager.Holo c = this.copy(h);
            c.location = Locs.serialize(p.getLocation());
            c.offset = 1.6;
            this.holos.put(c.id, c);
            this.change(p, c);
        }));
        b.add(ui.btn(p, "<red>Remove", null, () -> {
            this.remove(h);
            Msg.send(p, "admin.hologram-removed");
            p.closeDialog();
        }));
        ui.screen(p, LobbyDialogs.title("Leaderboard Hologram", h.id), List.of(), header, null, b, 3, ui.close(p));
    }

    private HologramManager.Holo copy(HologramManager.Holo h) {
        HologramManager.Holo c = new HologramManager.Holo(UUID.randomUUID().toString().substring(0, 8));
        c.location = h.location;
        c.kits = new ArrayList<>(h.kits);
        c.cycleSeconds = h.cycleSeconds;
        c.stat = h.stat;
        c.rows = h.rows;
        c.design = h.design;
        c.title = h.title;
        c.top1 = h.top1;
        c.top2 = h.top2;
        c.top3 = h.top3;
        c.rest = h.rest;
        c.accent = h.accent;
        c.bgAlpha = h.bgAlpha;
        c.scale = h.scale;
        c.lineWidth = h.lineWidth;
        c.showTier = h.showTier;
        c.showWL = h.showWL;
        c.billboard = h.billboard;
        c.yaw = h.yaw;
        c.offset = h.offset;
        c.refreshSeconds = h.refreshSeconds;
        return c;
    }

    /** A leaderboard hologram placed by a builder (the hub), in front of a spot, with a design. */
    public void place(Location at, String kit, String design, String stat, float yaw) {
        HologramManager.Holo h = new HologramManager.Holo(UUID.randomUUID().toString().substring(0, 8));
        h.location = Locs.serialize(at);
        h.offset = 1.6;
        h.kits = new ArrayList<>(List.of(kit));
        try {
            h.design = HologramManager.Design.valueOf(design);
        } catch (IllegalArgumentException var7) {
            h.design = HologramManager.Design.GLASS;
        }

        h.yaw = yaw;
        h.billboard = false;
        h.stat = STATS.contains(stat) ? stat : "ELO";
        this.holos.put(h.id, h);
        this.save();
        this.respawn(h);
    }

    // ------------------------------------------------------------------ data

    static String statName(String s) {
        return switch (s) {
            case "WINS" -> "Wins";
            case "WINRATE" -> "Win %";
            case "STREAK" -> "Best Streak";
            case "FFA_KILLS" -> "FFA Kills";
            case "FFA_KD" -> "FFA K/D";
            default -> "Elo";
        };
    }

    private static String value(String stat, double v) {
        return switch (stat) {
            case "WINRATE" -> String.format(Locale.US, "%.1f%%", v);
            case "FFA_KD" -> String.format(Locale.US, "%.2f", v);
            default -> Fmt.num(Math.round(v));
        };
    }

    private List<Database.StatRow> rows(String kit, String stat, int refreshSeconds) {
        String k = kit + "|" + stat;
        long now = System.currentTimeMillis();
        Long at = this.fetched.get(k);
        if (at == null || now - at > refreshSeconds * 1000L) {
            this.fetched.put(k, now);
            if (this.plugin.db() != null) {
                this.plugin.db().supply(c -> this.plugin.db().topStat(c, kit, stat, 20)).thenAccept(list -> this.data.put(k, list)).exceptionally(err -> null);
            }
        }

        return this.data.getOrDefault(k, List.of());
    }

    private String kitOf(HologramManager.Holo h, HologramManager.Live l) {
        return h.kits.get(Math.floorMod(l.kitIndex, h.kits.size()));
    }

    private String title(HologramManager.Holo h, String kitId) {
        Kit kit = "overall".equals(kitId) ? null : this.plugin.kits().get(kitId);
        String name = kit == null ? ("overall".equals(kitId) ? "Overall" : kitId) : kit.displayName;
        if (!h.title.isBlank()) {
            return h.title.replace("<kit>", Text.esc(name)).replace("<stat>", statName(h.stat));
        } else {
            String color = h.design == HologramManager.Design.CLASSIC ? "<gold>" : "<" + h.accent + ">";
            return color + "<b>" + Text.esc(name) + (h.stat.equals("ELO") ? " Leaderboard" : " - " + statName(h.stat));
        }
    }

    private String row(HologramManager.Holo h, int i, Database.StatRow e, boolean stripe) {
        String color = i == 0 ? h.top1 : (i == 1 ? h.top2 : (i == 2 ? h.top3 : h.rest));
        Tier t = h.showTier && e.tier() >= 0 ? Tier.byIndex(e.tier()) : null;
        String wl = h.showWL && !h.stat.startsWith("FFA") ? " <dark_gray>(" + "<green>" + e.wins() + "<dark_gray>/<red>" + e.losses() + "<dark_gray>)" : "";
        String line = "<" + color + ">#" + (i + 1) + " <white>" + Text.esc(e.name()) + " <dark_gray>- " + (t == null ? "" : t.mm() + " ") + "<gray>" + value(h.stat, e.value()) + wl;
        return stripe && i % 2 == 1 ? "<#D8D8D8>" + line : line;
    }

    // ------------------------------------------------------------------ rendering

    private void tag(Entity e, HologramManager.Holo h) {
        e.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, h.id);
    }

    private void respawn(HologramManager.Holo h) {
        HologramManager.Live old = this.live.remove(h.id);
        int kitIndex = old == null ? 0 : old.kitIndex;
        if (old != null) {
            old.remove();
        }

        Location base = h.loc();
        if (base != null && base.isChunkLoaded() && HoloFx.clientsReady(base)) {
            HologramManager.Live l = new HologramManager.Live();
            l.kitIndex = kitIndex;
            this.live.put(h.id, l);
            this.build(h, l, base);
        }
    }

    private void build(HologramManager.Holo h, HologramManager.Live l, Location base) {
        String kit = this.kitOf(h, l);
        List<Database.StatRow> rows = this.rows(kit, h.stat, h.refreshSeconds);
        List<String> lines = this.lines(h, kit, rows, h.design == HologramManager.Design.PODIUM ? 3 : 0);
        boolean fixed = h.design.fixed() || !h.billboard;
        float s = h.scale;
        Location cardAt = base.clone().add(0.0, h.offset, 0.0);
        if (h.design == HologramManager.Design.PODIUM) {
            this.podium(h, l, base, rows);
            cardAt = base.clone().add(0.0, h.offset - 1.6 + 3.7 * s, 0.0);
        }

        int rgb = rgb(h.accent);

        Color bg = switch (h.design) {
            case CLASSIC -> h.bgAlpha >= 0 ? Color.fromARGB(h.bgAlpha, 0, 0, 0) : Color.fromARGB(140, 0, 0, 0);
            case NEON -> HoloFx.background(HoloFx.Kind.NONE, rgb, h.bgAlpha < 0 ? 110 : h.bgAlpha);
            case GLASS, MARQUEE, ORBIT, PODIUM -> Color.fromARGB(h.bgAlpha < 0 ? 160 : h.bgAlpha, 9, 12, 24);
            default -> HoloFx.background(h.design.fx, rgb, h.bgAlpha);
        };
        l.card = HoloFx.card(cardAt, h.yaw, fixed, s, bg, h.lineWidth, e -> this.tag(e, h));
        l.shown = String.join("\n", lines);
        l.card.text(Text.mm(l.shown));
        if (h.design.fx != HoloFx.Kind.NONE) {
            double[] size = HoloFx.measure(lines, h.lineWidth);
            List<Material> orbit = new ArrayList<>();

            for (String k : h.kits) {
                Kit kk = this.plugin.kits().get(k);
                if (kk != null && kk.icon != null) {
                    orbit.add(kk.icon.getType());
                }
            }

            l.decor = HoloFx.build(h.design.fx, cardAt, h.yaw, size[0] * s, size[1] * s, s, rgb, orbit.size() >= 3 ? orbit : null, e -> this.tag(e, h));
        }
    }

    private List<String> lines(HologramManager.Holo h, String kit, List<Database.StatRow> rows, int skip) {
        List<String> out = new ArrayList<>();
        String title = this.title(h, kit);
        out.add(h.design == HologramManager.Design.MARQUEE ? "<b><" + h.accent + ">" + HoloFx.marquee(title, this.ticks, 22) : title);
        if (h.design != HologramManager.Design.CLASSIC) {
            out.add("<" + h.accent + ">▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        }

        if (rows.isEmpty()) {
            out.add("<gray>No players yet");
        }

        for (int i = skip; i < Math.min(h.rows, rows.size()); i++) {
            out.add(this.row(h, i, rows.get(i), h.design == HologramManager.Design.MARQUEE));
        }

        if (h.kits.size() > 1) {
            StringBuilder dots = new StringBuilder();

            for (int i = 0; i < h.kits.size(); i++) {
                dots.append(i == Math.floorMod(h.kits.indexOf(kit), h.kits.size()) ? "<white>● " : "<dark_gray>● ");
            }

            out.add(dots.toString().trim());
        }

        return out;
    }

    /** Top 3 on gold, silver and bronze pedestals, as mannequins wearing their skins, facing the board's way. */
    private static String podiumKey(List<Database.StatRow> rows) {
        StringBuilder b = new StringBuilder();

        for (int i = 0; i < Math.min(3, rows.size()); i++) {
            b.append(rows.get(i).uuid()).append(';');
        }

        return b.toString();
    }

    private void podium(HologramManager.Holo h, HologramManager.Live l, Location base, List<Database.StatRow> rows) {
        l.podium = podiumKey(rows);
        float s = h.scale;
        Location ground = base.clone().add(0.0, h.offset - 1.6, 0.0);
        double yr = Math.toRadians(h.yaw);
        // the reader stands in front (entity forward = (-sin, 0, cos)) looking back: their right is (cos, 0, sin)
        Vector right = new Vector(Math.cos(yr), 0.0, Math.sin(yr));
        double[][] spots = new double[][]{{0.0, 1.0}, {-1.25, 0.7}, {1.25, 0.45}};
        Material[] blocks = new Material[]{Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.COPPER_BLOCK};
        String[] colors = new String[]{h.top1, h.top2, h.top3};

        for (int i = 0; i < 3; i++) {
            double side = spots[i][0] * s;
            double height = spots[i][1] * s;
            Location at = ground.clone().add(right.clone().multiply(side));
            at.setYaw(h.yaw);
            at.setPitch(0.0F);
            Material block = blocks[i];
            l.extra.add(at.getWorld().spawn(at, BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setBlock(block.createBlockData());
                d.setBrightness(new Brightness(15, 15));
                d.setTransformation(new Transformation(new Vector3f(-0.45F * s, 0.0F, -0.45F * s), new Quaternionf(), new Vector3f(0.9F * s, (float)height, 0.9F * s), new Quaternionf()));
                this.tag(d, h);
            }));
            Location top = at.clone().add(0.0, height, 0.0);
            if (i < rows.size()) {
                Database.StatRow e = rows.get(i);
                l.extra.add(top.getWorld().spawn(top, Mannequin.class, m -> {
                    m.setPersistent(false);
                    m.setInvulnerable(true);
                    m.setImmovable(true);
                    m.setGravity(false);
                    m.setSilent(true);
                    m.setCollidable(false);
                    try {
                        m.setDescription(null);
                    } catch (RuntimeException var5) {
                        m.setDescription(Component.empty());
                    }

                    m.setProfile(ResolvableProfile.resolvableProfile().name(e.name()).uuid(e.uuid()).build());
                    org.bukkit.attribute.AttributeInstance a = m.getAttribute(org.bukkit.attribute.Attribute.SCALE);
                    if (a != null && s != 1.0F) {
                        a.setBaseValue(Math.max(0.3, Math.min(2.0, s)));
                    }

                    this.tag(m, h);
                }));
                String plate = "<" + colors[i] + "><b>#" + (i + 1) + "</b> <white>" + Text.esc(e.name()) + "\n<gray>" + value(h.stat, e.value());
                l.extra.add(HoloFx.card(top.clone().add(0.0, 2.05 * s, 0.0), h.yaw, false, 0.8F * s, Color.fromARGB(110, 0, 0, 0), 200, x -> this.tag(x, h)));
                ((TextDisplay)l.extra.get(l.extra.size() - 1)).text(Text.mm(plate));
            }
        }
    }

    private static int rgb(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16);
        } catch (RuntimeException var2) {
            return 0xFF7A1A;
        }
    }

    /** Called every minute and after a reload: rebuilds what is missing and refreshes the lists. */
    public void refresh() {
        for (HologramManager.Holo h : this.holos.values()) {
            HologramManager.Live l = this.live.get(h.id);
            if (l == null || !l.valid()) {
                this.respawn(h);
            }
        }
    }

    /** Every 2 ticks: animations, kit cycling, text updates when the data changed. */
    public void tick() {
        this.ticks += 2;

        for (HologramManager.Holo h : this.holos.values()) {
            HologramManager.Live l = this.live.get(h.id);
            if (l == null) {
                if (this.ticks % 20 == 0) {
                    this.respawn(h);
                }
            } else if (!l.valid()) {
                this.live.remove(h.id);
                l.remove();
            } else if (net.vortexpvp.util.Viewers.near(h.loc())) {
                if (l.decor != null) {
                    l.decor.tick(this.ticks);
                }

                if (h.design == HologramManager.Design.NEON) {
                    int a = 70 + (int)Math.round((Math.sin(this.ticks * 0.08) * 0.5 + 0.5) * 90.0);
                    int rgb = rgb(h.accent);
                    l.card.setBackgroundColor(Color.fromARGB(a, rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF));
                } else if (h.design == HologramManager.Design.HOLO && this.ticks % 46 == 0) {
                    // a quick flicker now and then
                    l.card.setTextOpacity((byte)90);
                    net.vortexpvp.util.Tasks.later(2L, () -> l.card.setTextOpacity((byte)-1));
                }

                boolean cycle = h.kits.size() > 1 && System.currentTimeMillis() - l.kitSince > h.cycleSeconds * 1000L;
                if (cycle) {
                    l.kitIndex++;
                    l.kitSince = System.currentTimeMillis();
                    if (h.design == HologramManager.Design.PODIUM) {
                        this.respawn(h);
                        continue;
                    }
                }

                if (cycle || this.ticks % (h.design == HologramManager.Design.MARQUEE ? 4 : 20) == 0) {
                    String kit = this.kitOf(h, l);
                    List<Database.StatRow> rows = this.rows(kit, h.stat, h.refreshSeconds);
                    if (h.design == HologramManager.Design.PODIUM && !podiumKey(rows).equals(l.podium)) {
                        this.respawn(h);
                        continue;
                    }

                    String text = String.join("\n", this.lines(h, kit, rows, h.design == HologramManager.Design.PODIUM ? 3 : 0));
                    if (!text.equals(l.shown)) {
                        if (cycle) {
                            // the page flip: the card squeezes shut, the new list opens
                            this.flip(l.card, h.scale, text);
                        } else {
                            l.card.text(Text.mm(text));
                        }

                        l.shown = text;
                    }
                }
            }
        }
    }

    private void flip(TextDisplay card, float s, String text) {
        card.setInterpolationDelay(0);
        card.setInterpolationDuration(4);
        card.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, 0.02F, s), new Quaternionf()));
        net.vortexpvp.util.Tasks.later(4L, () -> {
            if (card.isValid()) {
                card.text(Text.mm(text));
                card.setInterpolationDelay(0);
                card.setInterpolationDuration(5);
                card.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            }
        });
    }

    public int removeAll() {
        int n = this.holos.size();
        this.holos.clear();
        this.shutdown();
        this.save();
        return n;
    }

    public void shutdown() {
        for (HologramManager.Live l : this.live.values()) {
            l.remove();
        }

        this.live.clear();
    }
}
