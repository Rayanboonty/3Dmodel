package net.vortexpvp.social;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.pack.Glyphs;
import net.vortexpvp.rank.Tier;
import net.vortexpvp.smp.WorldMode;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class TabManager {
    private final VortexPvP plugin;
    private final File file;
    private final Map<String, TabManager.Mood> moods = new LinkedHashMap<>();
    private List<String> header = new ArrayList<>();
    private List<String> footer = new ArrayList<>();
    private int tick;
    private final TabBrand brand = new TabBrand();
    private final TabBrand poolBrand = new TabBrand();
    private static final List<String> POOL_HEADER = List.of("", "<brand>", "<line>", "<#B98A8A>You only play against other pooled players", "");
    private static final List<String> POOL_FOOTER = List.of(
        "",
        "<#B98A8A>Ping <#FF3B3B><ping>ms</#FF3B3B> <dark_gray>•</dark_gray> <#B98A8A>Pooled here <#FF3B3B><here>",
        "<dark_gray>Play fair to get out <dark_gray>•</dark_gray> <#8A5A5A>appeal on <discord>",
        "<line_short>",
        ""
    );
    private String brandText = "";
    /** The Vortex Realm logo gradient (deep purple -> violet -> cyan). */
    private static final List<String> VORTEX_COLORS = List.of("#3B0F7A", "#6A2BE0", "#9B5CFF", "#4FC3FF", "#8FF6FF");
    private static final List<String> OLD_COLORS = List.of("#C0141E", "#E8261C", "#FF4E1F", "#FF7A1A", "#FFB547");
    private boolean refreshQueued;
    private static final List<String> OLD_HEADER = List.of("", "<b><mood:✦ <server> ✦></b>", "<gray><mode>", "");
    private static final List<String> PREV_HEADER = List.of(
        "", "<brand>", "<line>", "<#B9A89A>Playing <mode> <dark_gray>•</dark_gray> <#FFB547><online></#FFB547> <#B9A89A>online", ""
    );
    /** {@code <logo>} on a line of its own: the server logo for players with the pack, the line is left out otherwise. */
    private static final List<String> NEW_HEADER = List.of(
        "", "<logo>", "<brand>", "<line>", "<#B9A89A>Playing <mode> <dark_gray>•</dark_gray> <#FFB547><online></#FFB547> <#B9A89A>online", ""
    );
    private static final List<String> NEW_FOOTER = List.of(
        "",
        "<#B9A89A>Ping <#FF8A3D><ping>ms</#FF8A3D> <dark_gray>•</dark_gray> <#B9A89A>TPS <#FF8A3D><tps></#FF8A3D> <dark_gray>•</dark_gray> <#B9A89A>Here <#FF8A3D><here></#FF8A3D>",
        "<#FF7A1A><discord></#FF7A1A> <dark_gray>•</dark_gray> <#FFB547><ip></#FFB547>",
        "<line_short>",
        ""
    );

    public TabManager(VortexPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tab.yml");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        boolean fresh = !this.file.exists();
        if (fresh) {
            y.options()
                .setHeader(
                    List.of(
                        "Tab list. Placeholders: <server> <mode> <mode_name> <online> <here> <ping>",
                        "<tps> <discord> <ip>  -  <mood:TEXT> paints TEXT in the mode's gradient. MiniMessage everywhere.",
                        "<brand> = the animated logo (brand-text, brand-colors, brand-speed), <line> / <line_short> = glowing",
                        "dividers. <logo> on its own line = the server logo (players with the resource pack only).",
                        "moods: gradient of each mode (animated = the colours flow)."
                    )
                );
            y.set("header", NEW_HEADER);
            y.set("footer", NEW_FOOTER);
            setMood(y, "PVP", "#FF4A1C", "#FFB547", true);
            setMood(y, "HUB", "#FF6A13", "#FFD08A", true);
            setMood(y, "SURVIVAL", "#9CFF7A", "#2E9B3A", true);
            setMood(y, "LIFESTEAL", "#FF7A7A", "#B3001B", true);
            setMood(y, "STRENGTH", "#E9C2FF", "#8A2BE2", true);
            setMood(y, "RANKED", "#FFF3A0", "#FF9F00", true);
            setMood(y, "ECO", "#8CFFC1", "#00A86B", true);

            try {
                y.save(this.file);
            } catch (IOException var8) {
                this.plugin.getLogger().log(Level.WARNING, "Could not write tab.yml", (Throwable)var8);
            }
        }

        boolean changed = false;
        if (!fresh && (y.getStringList("header").equals(OLD_HEADER) || y.getStringList("header").equals(PREV_HEADER))) {
            y.set("header", NEW_HEADER);
            y.set("footer", NEW_FOOTER);
            changed = true;
        }

        if ("#FFC83D".equalsIgnoreCase(y.getString("moods.PVP.from")) && "#FFC83D".equalsIgnoreCase(y.getString("moods.PVP.to"))) {
            setMood(y, "PVP", "#FF4A1C", "#FFB547", true);
            changed = true;
        }

        if ("#9B6BFF".equalsIgnoreCase(y.getString("moods.HUB.from")) && "#33D6FF".equalsIgnoreCase(y.getString("moods.HUB.to"))) {
            setMood(y, "HUB", "#FF6A13", "#FFD08A", true);
            changed = true;
        }

        if (!y.contains("brand-colors")) {
            y.set("brand-text", "");
            y.set("brand-colors", VORTEX_COLORS);
            y.set("brand-speed", 1.0);
            changed = true;
        } else if (y.getStringList("brand-colors").equals(OLD_COLORS)) {
            y.set("brand-colors", VORTEX_COLORS);
            changed = true;
        }

        if ("Practice PvP".equalsIgnoreCase(y.getString("brand-text", ""))) {
            y.set("brand-text", "");
            changed = true;
        }

        if (changed) {
            try {
                y.save(this.file);
            } catch (IOException var7) {
                this.plugin.getLogger().log(Level.WARNING, "Could not update tab.yml", (Throwable)var7);
            }
        }

        this.brand.colors(y.getStringList("brand-colors"));
        this.brand.speed(y.getDouble("brand-speed", 1.0));
        this.brandText = y.getString("brand-text", "");
        this.header = y.getStringList("header");
        this.footer = y.getStringList("footer");
        this.moods.clear();
        ConfigurationSection m = y.getConfigurationSection("moods");
        if (m != null) {
            for (String k : m.getKeys(false)) {
                this.moods
                    .put(
                        k.toUpperCase(Locale.ROOT),
                        new TabManager.Mood(m.getString(k + ".from", "#FFFFFF"), m.getString(k + ".to", "#AAAAAA"), m.getBoolean(k + ".animated", false))
                    );
            }
        }
    }

    private static void setMood(YamlConfiguration y, String key, String from, String to, boolean animated) {
        y.set("moods." + key + ".from", from);
        y.set("moods." + key + ".to", to);
        y.set("moods." + key + ".animated", animated);
    }

    public String group(Player p) {
        if (this.plugin.worlds().managed(p.getWorld())) {
            return "PVP";
        } else if (this.plugin.modes() == null) {
            return "SURVIVAL";
        } else {
            WorldMode m = this.plugin.modes().mode(p.getWorld());
            return m == WorldMode.PVP ? "PVP" : m.name();
        }
    }

    public TabManager.Mood mood(String group) {
        TabManager.Mood m = this.moods.get(group);
        return m != null ? m : new TabManager.Mood("#FFC83D", "#FFC83D", false);
    }

    public String paint(String group, String text) {
        TabManager.Mood m = this.mood(group);
        if (m.from().equalsIgnoreCase(m.to())) {
            return "<" + m.from() + ">" + text + "</" + m.from() + ">";
        } else {
            double phase = m.animated() ? Math.sin(Bukkit.getCurrentTick() * 0.07) * 0.9 : 0.0;
            return "<gradient:" + m.from() + ":" + m.to() + ":" + String.format(Locale.US, "%.2f", phase) + ">" + text + "</gradient>";
        }
    }

    private String modeName(String group) {
        if ("PVP".equals(group)) {
            return "Practice";
        } else {
            try {
                return WorldMode.valueOf(group).display;
            } catch (IllegalArgumentException var3) {
                return group;
            }
        }
    }

    /** Renders lines with dividers as wide as the widest text line (so they frame the text evenly). */
    /** Divider length per player and list, measured at most every 2 seconds (measuring costs a full render). */
    private final Map<String, long[]> widthCache = new java.util.concurrent.ConcurrentHashMap<>();

    private Component render(List<String> lines, Player p, String group, int here, Component logo, TabBrand br) {
        long now = Bukkit.getCurrentTick();
        String key = (p == null ? "-" : p.getUniqueId().toString()) + "|" + System.identityHashCode(lines) + "|" + group;
        long[] cached = this.widthCache.get(key);
        int n;
        if (cached != null && now - cached[1] < 40L) {
            n = (int)cached[0];
        } else {
            Component none = Component.empty();
            Component probe = this.render(lines, p, group, here, logo, none, none);
            int widest = 0;

            for (Component line : probe.children().isEmpty() ? List.of(probe) : probe.children()) {
                widest = Math.max(widest, net.vortexpvp.util.TextWidth.width(line));
            }

            n = Math.max(12, Math.min(40, Math.round(widest / 9.0F) + 2));
            if (this.widthCache.size() > 2000) {
                this.widthCache.clear();
            }

            this.widthCache.put(key, new long[]{n, now});
        }

        return this.render(lines, p, group, here, logo, br.line(n, now), br.line(Math.max(8, n * 11 / 20), now));
    }

    private Component render(List<String> lines, Player p, String group, int here, Component logo, Component divider, Component shortLine) {
        List<Component> out = new ArrayList<>();
        double tps = Bukkit.getTPS()[0];

        boolean pack = p != null && this.plugin.hasPack(p);

        for (String raw : lines) {
            String line = raw;
            if (line.contains("<logo>")) {
                if (!pack) {
                    line = line.replace("<logo>", "");
                    if (line.isBlank()) {
                        continue;
                    }
                } else {
                    // the logo is alone on its line (any text around it goes under it), so the client centres it
                    // exactly; the glyph hangs over the three lines below it, so they are kept empty
                    String rest = line.replace("<logo>", "").trim();
                    line = "<logo_glyph>\n\n\n" + (rest.isEmpty() ? "" : "\n" + rest);
                }
            }

            int i;
            while ((i = line.indexOf("<mood:")) >= 0) {
                int depth = 0;
                int end = -1;

                for (int j = i + 1; j < line.length(); j++) {
                    char c = line.charAt(j);
                    if (c == '<') {
                        depth++;
                    } else if (c == '>') {
                        if (depth == 0) {
                            end = j;
                            break;
                        }

                        depth--;
                    }
                }

                if (end < 0) {
                    break;
                }

                line = line.substring(0, i) + this.paint(group, line.substring(i + 6, end)) + line.substring(end + 1);
            }

            try {
                String[] me = p != null && line.contains("<my_") ? this.myStats(p) : NO_STATS;
                out.add(
                    Text.mm(
                        line,
                        Placeholder.unparsed("my_elo", me[0]),
                        Placeholder.parsed("my_tier", me[1]),
                        Placeholder.unparsed("my_rank", me[2]),
                        Placeholder.unparsed("matches", String.valueOf(this.plugin.matches() == null ? 0 : this.plugin.matches().active().size())),
                        Placeholder.unparsed("queued", String.valueOf(this.plugin.queues() == null ? 0 : this.plugin.queues().total())),
                        Placeholder.unparsed("server", Cfg.s(Opt.SERVER_NAME)),
                        Placeholder.component("brand", logo),
                        Placeholder.component("logo_glyph", logoGlyph()),
                        Placeholder.component("line", divider),
                        Placeholder.component("line_short", shortLine),
                        Placeholder.parsed("mode", this.paint(group, this.modeName(group))),
                        Placeholder.unparsed("mode_name", this.modeName(group)),
                        Placeholder.unparsed("online", String.valueOf(Bukkit.getOnlinePlayers().size())),
                        Placeholder.unparsed("here", String.valueOf(here)),
                        Placeholder.unparsed("ping", String.valueOf(p == null ? 0 : p.getPing())),
                        Placeholder.unparsed("tps", String.format(Locale.US, "%.1f", Math.min(20.0, tps))),
                        Placeholder.unparsed("discord", Cfg.s(Opt.LINK_DISCORD).replaceFirst("^https?://", "")),
                        Placeholder.unparsed("ip", Cfg.s(Opt.SERVER_IP))
                    )
                );
            } catch (RuntimeException var19) {
                out.add(Component.text(raw));
            }
        }

        return Component.join(JoinConfiguration.newlines(), out);
    }

    /**
     * The logo, centred: a bitmap glyph is drawn 1 px narrower than it advances, so a 1 px lead-in balances it
     * (the client centres the whole line). tab.logo-offset moves it further (2 px of lead-in = 1 px to the right).
     */
    private static Component logoGlyph() {
        int shift = 1 + Cfg.i(Opt.TAB_LOGO_OFFSET) * 2;
        Component logo = Glyphs.logo();
        if (shift > 0) {
            return Component.empty().append(Glyphs.space(shift)).append(logo);
        } else if (shift < 0) {
            return Component.empty().append(logo).append(Glyphs.space(-shift));
        } else {
            return logo;
        }
    }

    private static final String[] NO_STATS = new String[]{"-", "<gray>-", "-"};

    /** Elo, tier and leaderboard place of the viewer in their best kit (Competitive design footer). */
    private String[] myStats(Player p) {
        try {
            PvPProfile prof = this.plugin.profiles().get(p);
            String kit = prof == null ? null : prof.bestKit();
            if (kit == null) {
                return new String[]{"-", "<gray>Unranked", "-"};
            } else {
                Tier t = prof.tierIn(kit);
                int pos = this.plugin.leaderboards() == null ? -1 : this.plugin.leaderboards().position(kit, p.getUniqueId());
                return new String[]{String.format(Locale.US, "%,d", prof.stats(kit).elo), t == null ? "<gray>Unranked" : t.mm(), pos > 0 ? "#" + pos : "-"};
            }
        } catch (RuntimeException var6) {
            return NO_STATS;
        }
    }

    /** Self test: renders a design's header and footer with no viewer; returns the first line left with a raw tag, or null. */
    public String check(TabDesigns.Design d) {
        Component logo = this.brand.brand("TEST", 0L);
        Component line = this.brand.line(22, 0L);
        for (List<String> part : List.of(d.header(), d.footer())) {
            String text = Text.plain(this.render(part, null, "PVP", 1, logo, line, line));
            for (String l : text.split("\n")) {
                if (l.contains("<") && l.contains(">")) {
                    return l;
                }
            }
        }

        return null;
    }

    public String design() {
        return TabDesigns.active().id();
    }

    /**
     * Uses one of the ready-made designs: header, footer, logo colours and mode gradients go into tab.yml (the old file
     * is kept as tab-backup.yml), the column layout into config.yml. The logo text stays as the admin set it.
     */
    public void applyDesign(String id) {
        TabDesigns.Design d = TabDesigns.get(id);

        try {
            if (this.file.exists()) {
                java.nio.file.Files.copy(
                    this.file.toPath(),
                    new File(this.plugin.getDataFolder(), "tab-backup.yml").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException var6) {
            this.plugin.getLogger().log(Level.WARNING, "Could not back up tab.yml", (Throwable)var6);
        }

        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        y.set("header", d.header());
        y.set("footer", d.footer());
        y.set("brand-colors", d.brandColors());
        y.set("brand-speed", d.brandSpeed());
        if (d.moods() != null) {
            for (Map.Entry<String, String[]> e : d.moods().entrySet()) {
                setMood(y, e.getKey(), e.getValue()[0], e.getValue()[1], true);
            }
        } else {
            // Classic: the gradients every mode had
            setMood(y, "PVP", "#FF4A1C", "#FFB547", true);
            setMood(y, "HUB", "#FF6A13", "#FFD08A", true);
            setMood(y, "SURVIVAL", "#9CFF7A", "#2E9B3A", true);
            setMood(y, "LIFESTEAL", "#FF7A7A", "#B3001B", true);
            setMood(y, "STRENGTH", "#E9C2FF", "#8A2BE2", true);
            setMood(y, "RANKED", "#FFF3A0", "#FF9F00", true);
            setMood(y, "ECO", "#8CFFC1", "#00A86B", true);
        }

        try {
            y.save(this.file);
        } catch (IOException var5) {
            this.plugin.getLogger().log(Level.WARNING, "Could not write tab.yml", (Throwable)var5);
        }

        Cfg.set(Opt.TAB_COLUMNS, d.columns());
        Cfg.set(Opt.TAB_COLUMNS_WHERE, d.where());
        Cfg.set(Opt.TAB_COLUMNS_SECTIONS, d.sections());
        Cfg.set(Opt.TAB_COLUMNS_SECTION_SIZE, d.sectionSize());
        Cfg.set(Opt.TAB_DESIGN, d.id());
        this.load();
        this.refreshSoon();
    }

    /** "3706 Online · Lowest Ping Host: Miami, Florida" (MCPVP footer). */
    private Component hostLine() {
        String host = this.plugin.regions() == null ? "" : this.plugin.regions().hostLabel();
        String line = "<#FF5555>" + Bukkit.getOnlinePlayers().size() + "</#FF5555> <gray>Online";
        if (!host.isBlank()) {
            line = line + " <dark_gray>·</dark_gray> <gray>Lowest Ping Host: <#FF5555>" + Text.esc(host);
        }

        return Text.mm(line);
    }

    /** Re-renders names and the column tab on the next tick (status / friend changes). */
    public void refreshSoon() {
        if (!this.refreshQueued) {
            this.refreshQueued = true;
            net.vortexpvp.util.Tasks.later(2L, () -> {
                this.refreshQueued = false;
                this.tick();
                if (this.plugin.tabColumns() != null) {
                    this.plugin.tabColumns().tick();
                }
            });
        }
    }

    public void animate() {
        if (Cfg.bool(Opt.TAB_ENABLED)) {
            long now = Bukkit.getCurrentTick();
            String text = this.brandText != null && !this.brandText.isBlank() ? this.brandText : TabBrand.spaced(Cfg.s(Opt.SERVER_NAME));
            Component logo = this.brand.brand(text, now);
            Component line = this.brand.line(22, now);
            Component shortLine = this.brand.line(12, now);
            this.poolBrand.colors(List.of("#3A0000", "#8B0000", "#E01010", "#FF4040"));
            Component poolLogo = this.poolBrand.brand("⚠  " + TabBrand.spaced("Hacker Pool") + "  ⚠", now);
            Component poolLine = this.poolBrand.line(22, now);
            Component poolShort = this.poolBrand.line(12, now);
            int pooledOnline = 0;
            Map<String, Integer> count = new LinkedHashMap<>();
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            Map<Player, String> groups = new LinkedHashMap<>();

            for (Player p : online) {
                String g = this.group(p);
                groups.put(p, g);
                count.merge(g, 1, Integer::sum);
            }

            for (Player p : online) {
                if (this.plugin.hackerPool() != null && this.plugin.hackerPool().pooled(p)) {
                    pooledOnline++;
                }
            }

            for (Player px : online) {
                String g = groups.get(px);

                try {
                    if (this.plugin.hackerPool() != null && this.plugin.hackerPool().pooled(px)) {
                        px.sendPlayerListHeaderAndFooter(
                            this.render(POOL_HEADER, px, g, pooledOnline, poolLogo, this.poolBrand),
                            this.render(POOL_FOOTER, px, g, pooledOnline, poolLogo, this.poolBrand)
                        );
                    } else {
                        Component foot = this.render(this.footer, px, g, count.getOrDefault(g, 0), logo, this.brand);
                        if (this.plugin.tabColumns() != null && this.plugin.tabColumns().showing(px)) {
                            foot = foot.append(Component.newline()).append(this.hostLine());
                        }

                        px.sendPlayerListHeaderAndFooter(this.render(this.header, px, g, count.getOrDefault(g, 0), logo, this.brand), foot);
                    }
                } catch (RuntimeException var18) {
                    this.plugin.getLogger().log(Level.FINE, "Tab header failed for " + px.getName(), (Throwable)var18);
                }
            }
        }
    }

    private Component tierTag(Player p) {
        try {
            PvPProfile prof = this.plugin.profiles().get(p);
            String kit = prof == null ? null : prof.bestKit();
            Tier t = kit == null ? null : prof.tierIn(kit);
            return (Component)(t == null ? Component.empty() : Component.space().append(Text.mm(t.mm())));
        } catch (RuntimeException var5) {
            return Component.empty();
        }
    }

    public void tick() {
        if (Cfg.bool(Opt.TAB_ENABLED)) {
            this.tick++;
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            Map<String, Integer> count = new LinkedHashMap<>();
            Map<Player, String> groups = new LinkedHashMap<>();

            for (Player p : online) {
                String g = this.group(p);
                groups.put(p, g);
                count.merge(g, 1, Integer::sum);
            }

            boolean perWorld = Cfg.bool(Opt.TAB_PER_WORLD);
            // flag images for viewers that have the pack (the shared tab name keeps the text tag for the others)
            Map<java.util.UUID, Component> glyphNames = new LinkedHashMap<>();
            boolean namesChanged = false;

            for (Player p : online) {
                String g = groups.get(p);

                try {
                    Ranks.Rank r = this.plugin.serverRanks() == null ? null : this.plugin.serverRanks().of(p);
                    Component name;
                    if (this.plugin.hackerPool() != null && this.plugin.hackerPool().pooled(p)) {
                        name = this.plugin.hackerPool().badge(p).append(Text.mm("<red>" + Text.esc(p.getName())));
                    } else if (r != null && r.isDefault()) {
                        String label = Cfg.s(Opt.TAB_DEFAULT_RANK);
                        name = (label != null && !label.isBlank() ? Text.mm("<#8E98B6><b>" + Text.esc(label) + "</b> ") : Component.empty())
                            .append(r.name(p.getName()));
                    } else if (r != null) {
                        name = r.prefix().append(r.name(p.getName()));
                    } else {
                        name = Text.mm("<white>" + Text.esc(p.getName()));
                    }

                    if (this.plugin.welcome() != null) {
                        name = name.append(this.plugin.welcome().badge(p.getUniqueId()));
                    }

                    if (Cfg.bool(Opt.TAB_SHOW_TIER)) {
                        name = name.append(this.tierTag(p));
                    }

                    name = name.append(net.vortexpvp.api.NameDecor.tab(p.getUniqueId()));

                    if (this.plugin.countries() != null) {
                        Component base = name;
                        boolean allHavePack = Cfg.bool(Opt.PACK_REQUIRED) && this.plugin.packs() != null && this.plugin.packs().ready();
                        Component text = this.plugin.countries().tag(p.getUniqueId());
                        Component glyph = this.plugin.countries().glyphTag(p.getUniqueId());
                        name = base.append(allHavePack ? glyph : text);
                        if (!allHavePack && !glyph.equals(text)) {
                            glyphNames.put(p.getUniqueId(), base.append(glyph));
                        }
                    }

                    if (!name.equals(p.playerListName())) {
                        p.playerListName(name);
                        namesChanged = true;
                    }

                    int order = r == null ? 0 : r.weight;
                    if (p.getPlayerListOrder() != order) {
                        p.setPlayerListOrder(order);
                    }

                    if (this.tick % 2 == 0 && (this.plugin.tabColumns() == null || !this.plugin.tabColumns().showing(p))) {
                        boolean seesAll = !perWorld || p.hasPermission("vortexpvp.staff");

                        for (Player o : online) {
                            if (!o.equals(p)) {
                                boolean want = (seesAll || g.equals(groups.get(o)))
                                    && (this.plugin.social() == null || this.plugin.social().appearsOnline(p, o));
                                if (want && !p.isListed(o) && p.canSee(o)) {
                                    p.listPlayer(o);
                                    this.flagSynced.remove(p.getUniqueId());
                                } else if (!want && p.isListed(o)) {
                                    p.unlistPlayer(o);
                                }
                            }
                        }
                    }
                } catch (RuntimeException var15) {
                    this.plugin.getLogger().log(Level.FINE, "Tab update failed for " + p.getName(), (Throwable)var15);
                }
            }

            if (namesChanged) {
                this.flagSynced.clear();
            }

            this.flagSynced.retainAll(online.stream().map(Player::getUniqueId).toList());
            TabColumns cols = this.plugin.tabColumns();
            if (cols != null && cols.available() && !glyphNames.isEmpty()) {
                for (Player v : online) {
                    if (this.plugin.hasPack(v) && !cols.showing(v) && this.flagSynced.add(v.getUniqueId())) {
                        cols.sendDisplayNames(v, glyphNames);
                    }
                }
            }
        }
    }

    /** Viewers (with the pack) that already got the flag-image tab names since they last changed. */
    private final java.util.Set<java.util.UUID> flagSynced = new java.util.HashSet<>();

    /** A viewer just loaded the pack: send them the flag images on the next tab update. */
    public void packLoaded(Player p) {
        this.flagSynced.remove(p.getUniqueId());
    }

    public record Mood(String from, String to, boolean animated) {
    }
}
