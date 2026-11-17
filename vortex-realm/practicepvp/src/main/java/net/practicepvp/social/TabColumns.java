package net.practicepvp.social;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.data.Database;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.data.Setting;
import net.practicepvp.kit.Kit;
import net.practicepvp.rank.Tier;
import net.practicepvp.session.PlayerState;
import net.practicepvp.util.Nms;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The MCPVP tab list: fake entries in sections (Staff, Friends, Creators, Online, Top Players and the top players of
 * chosen kits), each with the player's head, flag and status. It adapts to the server: with few players online it is
 * a single short column (leaderboards shrink, kit boards hide); it grows to up to 4 columns of 20, sections aligned.
 * During the map vote it shows no rows at all. Real players (the viewer too) are unlisted for viewers that see the
 * columns. Built with vanilla packets through reflection; if anything is missing the feature switches itself off and
 * the normal tab list stays.
 */
public final class TabColumns implements Listener {
    public static final int ROWS = 20;
    public static final int COLUMNS = 4;
    public static final int SLOTS = ROWS * COLUMNS;
    private final PracticePlugin plugin;
    private final UUID[] slotIds = new UUID[SLOTS];
    private final String[] slotNames = new String[SLOTS];
    private final Map<UUID, TabColumns.ViewerState> viewers = new ConcurrentHashMap<>();
    private final Map<UUID, ProfileProperty> skins = new ConcurrentHashMap<>();
    private final Set<UUID> skinLookups = ConcurrentHashMap.newKeySet();
    private boolean available;
    private String failure = "not started";
    private Constructor<?> entryCtor;
    private Constructor<?> updateCtor;
    private Constructor<?> removeCtor;
    private Constructor<?> profileCtor;
    private Constructor<?> propertyMapCtor;
    private Constructor<?> propertyCtor;
    private Method asVanilla;
    private Object survival;
    private EnumSet<?> addActions;
    private EnumSet<?> displayActions;
    private EnumSet<?> updateActions;
    /** The design's row and title style, read at the start of each layout. */
    private TabDesigns.Style style = TabDesigns.DESIGNS.get(0).style();
    private final Map<String, List<Database.StatRow>> stats = new ConcurrentHashMap<>();
    private final Map<String, Long> statsAt = new ConcurrentHashMap<>();

    public TabColumns(PracticePlugin plugin) {
        this.plugin = plugin;
        for (int i = 0; i < SLOTS; i++) {
            this.slotIds[i] = UUID.nameUUIDFromBytes(("vortex-tab-slot-" + i).getBytes(StandardCharsets.UTF_8));
            this.slotNames[i] = String.format(Locale.ROOT, "~vr%02d", i);
        }
    }

    // ------------------------------------------------------------------ reflection

    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean init() {
        try {
            Class<?> pkt = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            Class action = Class.forName(pkt.getName() + "$Action");
            Class<?> entry = Class.forName(pkt.getName() + "$Entry");
            Class gameType = Class.forName("net.minecraft.world.level.GameType");
            Class<?> nmsComponent = Class.forName("net.minecraft.network.chat.Component");
            Class<?> chatData = Class.forName("net.minecraft.network.chat.RemoteChatSession$Data");
            Class<?> gp = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> pm = Class.forName("com.mojang.authlib.properties.PropertyMap");
            Class<?> prop = Class.forName("com.mojang.authlib.properties.Property");
            this.entryCtor = entry.getConstructor(UUID.class, gp, boolean.class, int.class, gameType, nmsComponent, boolean.class, int.class, chatData);
            this.updateCtor = pkt.getConstructor(EnumSet.class, List.class);
            this.removeCtor = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket").getConstructor(List.class);
            this.profileCtor = gp.getConstructor(UUID.class, String.class, pm);
            this.propertyMapCtor = pm.getConstructor(Multimap.class);
            this.propertyCtor = prop.getConstructor(String.class, String.class, String.class);
            this.asVanilla = Class.forName("io.papermc.paper.adventure.PaperAdventure").getMethod("asVanilla", Component.class);
            this.survival = Enum.valueOf(gameType, "SURVIVAL");
            EnumSet add = EnumSet.noneOf(action);
            for (String a : List.of("ADD_PLAYER", "UPDATE_LISTED", "UPDATE_LATENCY", "UPDATE_DISPLAY_NAME", "UPDATE_LIST_ORDER", "UPDATE_GAME_MODE", "UPDATE_HAT")) {
                add.add(Enum.valueOf(action, a));
            }

            EnumSet upd = EnumSet.noneOf(action);
            upd.add(Enum.valueOf(action, "UPDATE_LATENCY"));
            upd.add(Enum.valueOf(action, "UPDATE_DISPLAY_NAME"));
            this.addActions = add;
            this.updateActions = upd;
            EnumSet disp = EnumSet.noneOf(action);
            disp.add(Enum.valueOf(action, "UPDATE_DISPLAY_NAME"));
            this.displayActions = disp;
            this.buildEntry(0, new TabColumns.Cell(Component.text("test"), null, 0));
            this.available = true;
            this.failure = null;
        } catch (Throwable var13) {
            this.available = false;
            this.failure = var13.getClass().getSimpleName() + ": " + var13.getMessage();
            this.plugin.getLogger().log(Level.WARNING, "MCPVP tab columns unavailable on this server version - using the normal tab list.", var13);
        }

        return this.available;
    }

    public boolean available() {
        return this.available;
    }

    public String failure() {
        return this.failure;
    }

    private Object profile(int slot, TabColumns.Cell c) throws ReflectiveOperationException {
        Multimap<String, Object> props = LinkedHashMultimap.create();
        if (c.skin() != null) {
            props.put("textures", this.propertyCtor.newInstance("textures", c.skin().getValue(), c.skin().getSignature()));
        }

        Object map = this.propertyMapCtor.newInstance(props);
        return this.profileCtor.newInstance(this.slotIds[slot], this.slotNames[slot], map);
    }

    private Object buildEntry(int slot, TabColumns.Cell c) throws ReflectiveOperationException {
        Object name = this.asVanilla.invoke(null, c.text());
        return this.entryCtor.newInstance(this.slotIds[slot], this.profile(slot, c), true, c.ping(), this.survival, name, true, 1000000 + SLOTS - slot, null);
    }

    /**
     * Shows real players under other names to one viewer only (the server shows everyone the same tab name): used to
     * give viewers that have the resource pack the flag images.
     */
    public void sendDisplayNames(Player viewer, Map<UUID, Component> names) {
        if (!this.available || names.isEmpty()) {
            return;
        }

        try {
            List<Object> entries = new ArrayList<>();
            for (Map.Entry<UUID, Component> e : names.entrySet()) {
                Object name = this.asVanilla.invoke(null, e.getValue());
                entries.add(this.entryCtor.newInstance(e.getKey(), null, true, 0, this.survival, name, true, 0, null));
            }

            Nms.send(viewer, this.updatePacket(this.displayActions, entries));
        } catch (ReflectiveOperationException | RuntimeException var6) {
            this.plugin.getLogger().log(Level.FINE, "Per-viewer tab names failed", var6);
        }
    }

    /** Self test: the per-viewer name packet can be built on this server version. */
    public String checkDisplayNames() {
        try {
            Object name = this.asVanilla.invoke(null, Component.text("test"));
            Object entry = this.entryCtor.newInstance(UUID.randomUUID(), null, true, 0, this.survival, name, true, 0, null);
            this.updatePacket(this.displayActions, List.of(entry));
            return null;
        } catch (ReflectiveOperationException | RuntimeException var3) {
            return var3.getClass().getSimpleName() + ": " + var3.getMessage();
        }
    }

    private Object updatePacket(EnumSet<?> actions, List<Object> entries) throws ReflectiveOperationException {
        return this.updateCtor.newInstance(actions, entries);
    }

    // ------------------------------------------------------------------ lifecycle

    public boolean enabled() {
        return this.available && Cfg.bool(Opt.TAB_COLUMNS);
    }

    private boolean wants(Player p) {
        if (!this.enabled()) {
            return false;
        } else {
            String where = Cfg.s(Opt.TAB_COLUMNS_WHERE).toLowerCase(Locale.ROOT);
            return where.equals("everywhere") || this.plugin.sessions().state(p) != PlayerState.OUTSIDE;
        }
    }

    public boolean showing(Player p) {
        return this.viewers.containsKey(p.getUniqueId());
    }

    /** Every 40 ticks: refresh every viewer. */
    public void tick() {
        if (this.available) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    if (this.wants(p)) {
                        this.refresh(p);
                    } else if (this.showing(p)) {
                        this.clear(p);
                    }
                } catch (RuntimeException var4) {
                    this.plugin.getLogger().log(Level.WARNING, "Tab columns failed for " + p.getName(), (Throwable)var4);
                    this.clear(p);
                }
            }
        }
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.showing(p)) {
                this.clear(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player joined = e.getPlayer();
        // straight away (no flash of the real entry in the column layout), and once more after the join packets
        for (UUID u : this.viewers.keySet()) {
            Player v = Bukkit.getPlayer(u);
            if (v != null && !v.equals(joined)) {
                v.unlistPlayer(joined);
            }
        }

        Tasks.later(2L, () -> {
            if (joined.isOnline()) {
                for (UUID u : this.viewers.keySet()) {
                    Player v = Bukkit.getPlayer(u);
                    if (v != null && !v.equals(joined)) {
                        v.unlistPlayer(joined);
                    }
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        this.viewers.remove(e.getPlayer().getUniqueId());
    }

    /** Stops showing the columns to a viewer and lists the real players again. */
    public void clear(Player p) {
        TabColumns.ViewerState st = this.viewers.remove(p.getUniqueId());
        if (st != null) {
            try {
                Nms.send(p, this.removeCtor.newInstance(List.of(this.slotIds)));
            } catch (ReflectiveOperationException var5) {
            }

            for (Player o : Bukkit.getOnlinePlayers()) {
                if (p.canSee(o)) {
                    p.listPlayer(o);
                }
            }
        }
    }

    private void refresh(Player viewer) {
        List<TabColumns.Cell> cells = this.layout(viewer);
        TabColumns.ViewerState st = this.viewers.get(viewer.getUniqueId());
        boolean fresh = st == null;
        if (fresh) {
            st = new TabColumns.ViewerState();
            this.viewers.put(viewer.getUniqueId(), st);
        }

        for (Player o : Bukkit.getOnlinePlayers()) {
            if (viewer.isListed(o)) {
                // the viewer too: with only the fake entries listed, a short list stays one short column
                viewer.unlistPlayer(o);
            }
        }

        List<Integer> reAdd = new ArrayList<>();
        List<Integer> update = new ArrayList<>();
        List<UUID> dropped = new ArrayList<>();
        int count = cells.size();
        for (int i = 0; i < count; i++) {
            TabColumns.Cell c = cells.get(i);
            String skinKey = c.skin() == null ? "" : c.skin().getValue();
            String textKey = Text.MM.serialize(c.text()) + "|" + c.ping();
            if (fresh || i >= st.count || !skinKey.equals(st.skinKeys[i])) {
                reAdd.add(i);
            } else if (!textKey.equals(st.textKeys[i])) {
                update.add(i);
            }

            st.skinKeys[i] = skinKey;
            st.textKeys[i] = textKey;
        }

        for (int i = count; i < st.count; i++) {
            dropped.add(this.slotIds[i]);
            st.skinKeys[i] = null;
            st.textKeys[i] = null;
        }

        int before = st.count;
        st.count = count;

        try {
            if (!dropped.isEmpty()) {
                Nms.send(viewer, this.removeCtor.newInstance(dropped));
            }

            if (!reAdd.isEmpty()) {
                if (!fresh) {
                    List<UUID> ids = new ArrayList<>();
                    for (int i : reAdd) {
                        if (i < before) {
                            ids.add(this.slotIds[i]);
                        }
                    }

                    if (!ids.isEmpty()) {
                        Nms.send(viewer, this.removeCtor.newInstance(ids));
                    }
                }

                List<Object> entries = new ArrayList<>();
                for (int i : reAdd) {
                    entries.add(this.buildEntry(i, cells.get(i)));
                }

                Nms.send(viewer, this.updatePacket(this.addActions, entries));
            }

            if (!update.isEmpty()) {
                List<Object> entries = new ArrayList<>();
                for (int i : update) {
                    entries.add(this.buildEntry(i, cells.get(i)));
                }

                Nms.send(viewer, this.updatePacket(this.updateActions, entries));
            }
        } catch (ReflectiveOperationException var11) {
            throw new IllegalStateException(var11);
        }
    }

    // ------------------------------------------------------------------ layout

    /**
     * The cells for a viewer, column after column. Up to 20 cells stay one column of exactly that length; above that
     * every column is padded to 20 rows so the sections line up (the client splits n entries into ceil(n/20) columns).
     */
    public List<TabColumns.Cell> layout(Player viewer) {
        if (this.plugin.sessions().state(viewer) == PlayerState.PREMATCH) {
            // map vote: nothing but the header and footer, like MCPVP
            return new ArrayList<>();
        }

        this.style = TabDesigns.active().style();
        List<TabColumns.Section> sections = new ArrayList<>();
        int online = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (this.visible(viewer, o)) {
                online++;
            }
        }

        // leaderboards grow with the server: 4 rows with one player online, up to the configured size
        int cap = Math.max(3, Math.min(Cfg.i(Opt.TAB_COLUMNS_SECTION_SIZE), 2 + online * 2));
        String[] ids = Cfg.s(Opt.TAB_COLUMNS_SECTIONS).split(",");
        boolean wall = true;
        for (String raw : ids) {
            wall &= raw.trim().toLowerCase(Locale.ROOT).startsWith("top");
        }

        if (wall) {
            // a wall of leaderboards keeps its full height however few players are online
            cap = Cfg.i(Opt.TAB_COLUMNS_SECTION_SIZE);
        }

        for (String raw : ids) {
            String id = raw.trim().toLowerCase(Locale.ROOT);
            TabColumns.Section s = this.section(viewer, id, cap, online);
            if (s != null) {
                sections.add(s);
            }
        }

        if (sections.isEmpty()) {
            // nothing to show (new server, no leaderboards yet): at least who is online, the viewer included
            TabColumns.Section here = this.section(viewer, "online", cap, online);
            if (here != null) {
                sections.add(here);
            }
        }

        int total = 0;
        for (TabColumns.Section s : sections) {
            total += (total == 0 ? 0 : 1) + 1 + s.rows().size();
        }

        if (total <= ROWS) {
            List<TabColumns.Cell> out = new ArrayList<>(total);
            for (TabColumns.Section s : sections) {
                if (!out.isEmpty()) {
                    out.add(this.spacer());
                }

                out.add(new TabColumns.Cell(s.title(), null, 0));
                out.addAll(s.rows());
            }

            return out;
        }

        // columns of 20: a section never breaks across two columns, so every column starts with a title on the top
        // row and the sections line up side by side
        List<TabColumns.Cell> out = new ArrayList<>(SLOTS);
        for (TabColumns.Section s : sections) {
            if (out.size() >= SLOTS) {
                break;
            }

            List<TabColumns.Cell> rows = s.rows().size() > ROWS - 1 ? s.rows().subList(0, ROWS - 1) : s.rows();
            int row = out.size() % ROWS;
            if (row != 0) {
                if (row + 2 + rows.size() > ROWS) {
                    while (out.size() % ROWS != 0) {
                        out.add(TabColumns.Cell.blank());
                    }
                } else {
                    out.add(this.spacer());
                }
            }

            if (out.size() + 1 + rows.size() > SLOTS) {
                break;
            }

            out.add(new TabColumns.Cell(s.title(), null, 0));
            out.addAll(rows);
        }

        while (out.size() % ROWS != 0) {
            out.add(TabColumns.Cell.blank());
        }

        return out;
    }

    private TabColumns.Section section(Player viewer, String id, int cap, int online) {
        SocialManager social = this.plugin.social();
        switch (id) {
            case "staff": {
                List<TabColumns.Row> rows = new ArrayList<>();
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (rows.size() < cap && this.plugin.staff() != null && this.plugin.staff().isStaff(o) && this.visible(viewer, o)) {
                        rows.add(this.person(viewer, o, true));
                    }
                }

                return rows.isEmpty() ? null : new TabColumns.Section(this.title("staff", "<#55FF55>✔ <b><gradient:#7CFF7C:#2ED62E>Staff</gradient>", rows.size(), ""), this.align(viewer, rows));
            }
            case "friends": {
                List<TabColumns.Row> rows = new ArrayList<>();
                if (social != null) {
                    for (Player f : social.onlineFriends(viewer)) {
                        if (rows.size() < Math.max(cap, 12)) {
                            rows.add(this.person(viewer, f, true));
                        }
                    }
                }

                // only when some are online: an empty section would just make the list longer
                return rows.isEmpty() ? null : new TabColumns.Section(this.title("friends", "<#FFD84A>☺ <b><gradient:#7C8CFF:#4D5BFF>Friends</gradient>", rows.size(), ""), this.align(viewer, rows));
            }
            case "online": {
                List<TabColumns.Row> rows = new ArrayList<>();
                rows.add(this.person(viewer, viewer, true));
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (rows.size() < ROWS - 1 && !o.equals(viewer) && this.visible(viewer, o)) {
                        rows.add(this.person(viewer, o, true));
                    }
                }

                List<TabColumns.Cell> cells = this.align(viewer, rows);
                if (online > cells.size()) {
                    cells.set(cells.size() - 1, new TabColumns.Cell(Text.mm("<gray>+ " + (online - cells.size() + 1) + " more"), null, 0));
                }

                return new TabColumns.Section(this.title("online", "<#55FF55>● <b><gradient:#9CFF7A:#2EB82E>Online</gradient></b> <gray>({count})", online, ""), cells);
            }
            case "creators": {
                List<TabColumns.Row> rows = new ArrayList<>();
                if (social != null) {
                    for (Player o : Bukkit.getOnlinePlayers()) {
                        if (rows.size() < cap && social.famous(o.getUniqueId()) && this.visible(viewer, o)) {
                            rows.add(this.person(viewer, o, false));
                        }
                    }
                }

                return rows.isEmpty() ? null : new TabColumns.Section(this.title("creators", "<#FF3B3B>▶ <b><gradient:#FF6B6B:#E01010>Creators</gradient>", rows.size(), ""), this.align(viewer, rows));
            }
            case "top": {
                List<TabColumns.Row> rows = new ArrayList<>();
                for (Database.LbEntry e : this.plugin.leaderboards().overall()) {
                    if (rows.size() >= cap) {
                        break;
                    }

                    rows.add(this.ranked(viewer, e));
                }

                return rows.isEmpty() ? null : new TabColumns.Section(this.title("top", "<#FFB547>🏆 <b><gradient:#FFE066:#FF9F00>Top Players</gradient>", rows.size(), ""), this.align(viewer, rows));
            }
            case "top_wins":
                return this.statSection(viewer, "top_wins", "WINS", "<#7CFF9B>🏆 <b>Most Wins", cap);
            case "top_streak":
                return this.statSection(viewer, "top_streak", "STREAK", "<#FF8A3D>🔥 <b>Best Streak", cap);
            case "top_ffa":
                return this.statSection(viewer, "top_ffa", "FFA_KILLS", "<#FF5B5B>⚔ <b>FFA Kills", cap);
            default: {
                String kitId = id.startsWith("kit:") ? id.substring(4) : id;
                Kit k = this.plugin.kits().get(kitId);
                // kit boards only once the server is busy enough to fill more than one column
                if (k != null && k.enabled && online >= 4) {
                    List<TabColumns.Row> rows = new ArrayList<>();
                    for (Database.LbEntry e : this.plugin.leaderboards().top(k.id)) {
                        if (rows.size() >= cap) {
                            break;
                        }

                        rows.add(this.ranked(viewer, e));
                    }

                    if (rows.isEmpty()) {
                        return null;
                    } else if (this.style.title("kit") != null) {
                        return new TabColumns.Section(this.title("kit", "", rows.size(), k.displayName), this.align(viewer, rows));
                    } else {
                        return new TabColumns.Section(Text.mm(k.color + Text.esc(k.glyph) + " <b>").append(Text.mm(k.color + "<b>" + Text.esc(k.displayName))), this.align(viewer, rows));
                    }
                } else {
                    return null;
                }
            }
        }
    }

    private boolean visible(Player viewer, Player o) {
        SocialManager social = this.plugin.social();
        return (viewer.equals(o) || viewer.canSee(o)) && (social == null || social.appearsOnline(viewer, o));
    }

    private Component flag(Player viewer, UUID u) {
        if (this.plugin.countries() == null || !this.plugin.countries().picked(u)) {
            return Component.empty();
        } else {
            PvPProfile prof = this.plugin.profiles().getIfLoaded(u);
            Countries.Country c = this.plugin.countries().of(u);
            return c == null || prof != null && !prof.on(Setting.SHOW_FLAG) ? Component.empty() : this.plugin.countries().flag(c, viewer).append(Component.space());
        }
    }

    private TabColumns.Row person(Player viewer, Player o, boolean dot) {
        SocialManager social = this.plugin.social();
        TabDesigns.Style st = this.style;
        Component status = dot && st.dots() && social != null ? Text.mm(social.status(o).dot() + " ") : Component.empty();
        Ranks.Rank r = null;

        try {
            r = this.plugin.serverRanks() == null ? null : this.plugin.serverRanks().of(o);
        } catch (RuntimeException var8) {
        }

        // dot, flag and the server rank in its colours go before the name; names line up after the widest of them
        Component prefix = status.append(st.flags() ? this.flag(viewer, o.getUniqueId()) : Component.empty());
        Component name;
        if (r == null || !st.ranks()) {
            name = Text.mm("<white>" + Text.esc(o.getName()));
        } else if (r.isDefault()) {
            name = r.name(o.getName());
        } else {
            prefix = prefix.append(r.prefix(viewer));
            name = r.name(o.getName());
        }

        return new TabColumns.Row(prefix, name, Component.empty(), st.heads() ? this.skinOf(o) : null, st.ping() ? o.getPing() : 0);
    }

    private TabColumns.Row ranked(Player viewer, Database.LbEntry e) {
        Player online = Bukkit.getPlayer(e.uuid());
        SocialManager social = this.plugin.social();
        if (online != null && social != null && !social.appearsOnline(viewer, online)) {
            online = null;
        }

        TabDesigns.Style st = this.style;
        ProfileProperty skin = !st.heads() ? null : (online != null ? this.skinOf(online) : this.cachedSkin(e.uuid(), e.name()));
        Component prefix = st.flags() ? this.flag(viewer, e.uuid()) : Component.empty();
        Component name = Text.mm((online != null ? "<white>" : "<gray>") + Text.esc(e.name()));
        Component suffix = Component.empty();
        if (st.stats()) {
            Tier t = Tier.byIndex(e.tier());
            suffix = Text.mm(" <dark_gray>·</dark_gray> " + (t == null ? "" : t.mm() + " ") + "<#C8D2E8>" + String.format(Locale.US, "%,d", e.elo()));
        }

        return new TabColumns.Row(prefix, name, suffix, skin, st.ping() && online != null ? online.getPing() : 0);
    }

    /** One row before alignment: what goes before the name, the name, and what follows it. */
    private record Row(Component prefix, Component name, Component suffix, ProfileProperty skin, int ping) {
    }

    /** An exact gap: pixel spaces with the resource pack, plain spaces (4 px) without it. */
    private Component pad(Player viewer, int px) {
        if (px <= 0) {
            return Component.empty();
        } else if (this.plugin.hasPack(viewer)) {
            return net.practicepvp.pack.Glyphs.space(px);
        } else {
            int n = Math.round(px / 4.0F);
            return n <= 0 ? Component.empty() : Component.text(" ".repeat(n));
        }
    }

    /** Pads the rows of one section so names start, and values end, at the same place. */
    private List<TabColumns.Cell> align(Player viewer, List<TabColumns.Row> rows) {
        int pre = 0;
        int name = 0;

        for (TabColumns.Row r : rows) {
            pre = Math.max(pre, net.practicepvp.util.TextWidth.width(r.prefix()));
            name = Math.max(name, net.practicepvp.util.TextWidth.width(r.name()));
        }

        List<TabColumns.Cell> out = new ArrayList<>(rows.size());

        for (TabColumns.Row r : rows) {
            Component text = r.prefix()
                .append(this.pad(viewer, pre - net.practicepvp.util.TextWidth.width(r.prefix())))
                .append(r.name());
            if (!r.suffix().equals(Component.empty())) {
                text = text.append(this.pad(viewer, name - net.practicepvp.util.TextWidth.width(r.name()))).append(r.suffix());
            }

            out.add(new TabColumns.Cell(text, r.skin(), r.ping()));
        }

        return out;
    }

    /** A section title: the design's (with {count} and {kit}), else the Classic one. */
    private Component title(String id, String classic, int count, String kit) {
        String t = this.style.title(id);
        String raw = t != null ? t : classic;
        return Text.mm(raw.replace("{count}", String.valueOf(count)).replace("{kit}", Text.esc(kit)));
    }

    private TabColumns.Cell spacer() {
        String sp = this.style.spacer();
        return sp == null || sp.isEmpty() ? TabColumns.Cell.blank() : new TabColumns.Cell(Text.mm(sp), null, 0);
    }

    /** Wins / best streak / FFA kills board, read from the database once a minute in the background. */
    private TabColumns.Section statSection(Player viewer, String id, String stat, String classic, int cap) {
        long now = System.currentTimeMillis();
        Long at = this.statsAt.get(stat);
        if ((at == null || now - at > 60000L) && this.plugin.db() != null) {
            this.statsAt.put(stat, now);
            this.plugin.db().supply(c -> this.plugin.db().topStat(c, "overall", stat, 20)).thenAccept(list -> {
                if (this.plugin.hackerPool() != null) {
                    list = new ArrayList<>(list);
                    list.removeIf(r -> this.plugin.hackerPool().pooled(r.uuid()));
                }

                this.stats.put(stat, list);
            }).exceptionally(err -> null);
        }

        TabDesigns.Style st = this.style;
        List<TabColumns.Row> rows = new ArrayList<>();
        for (Database.StatRow r : this.stats.getOrDefault(stat, List.of())) {
            if (rows.size() >= cap) {
                break;
            }

            Player online = Bukkit.getPlayer(r.uuid());
            SocialManager social = this.plugin.social();
            if (online != null && social != null && !social.appearsOnline(viewer, online)) {
                online = null;
            }

            ProfileProperty skin = !st.heads() ? null : (online != null ? this.skinOf(online) : this.cachedSkin(r.uuid(), r.name()));
            Component suffix = st.stats() ? Text.mm(" <dark_gray>·</dark_gray> <#C8D2E8>" + String.format(Locale.US, "%,d", Math.round(r.value()))) : Component.empty();
            rows.add(
                new TabColumns.Row(
                    st.flags() ? this.flag(viewer, r.uuid()) : Component.empty(),
                    Text.mm((online != null ? "<white>" : "<gray>") + Text.esc(r.name())),
                    suffix,
                    skin,
                    st.ping() && online != null ? online.getPing() : 0
                )
            );
        }

        List<TabColumns.Cell> cells = this.align(viewer, rows);
        if (cells.isEmpty()) {
            cells.add(new TabColumns.Cell(Text.mm("<dark_gray>No games yet"), null, 0));
        }

        return new TabColumns.Section(this.title(id, classic, cells.size(), ""), cells);
    }

    private ProfileProperty skinOf(Player p) {
        for (ProfileProperty pp : p.getPlayerProfile().getProperties()) {
            if (pp.getName().equals("textures")) {
                this.skins.put(p.getUniqueId(), pp);
                return pp;
            }
        }

        return this.skins.get(p.getUniqueId());
    }

    /** Skin of an offline player, fetched once in the background (online-mode servers only). */
    private ProfileProperty cachedSkin(UUID u, String name) {
        ProfileProperty pp = this.skins.get(u);
        if (pp == null && Bukkit.getOnlineMode() && this.skinLookups.size() < 512 && this.skinLookups.add(u)) {
            Tasks.async(() -> {
                try {
                    PlayerProfile prof = Bukkit.createProfile(u, name);
                    if (prof.complete(true)) {
                        for (ProfileProperty x : prof.getProperties()) {
                            if (x.getName().equals("textures")) {
                                this.skins.put(u, x);
                            }
                        }
                    }
                } catch (RuntimeException var5) {
                }
            });
        }

        return pp;
    }

    /** One line of the self test. */
    public String selfCheck() {
        if (!this.available) {
            return this.failure == null ? "off" : this.failure;
        } else {
            try {
                Map<Integer, Object> built = new HashMap<>();
                for (int i = 0; i < 3; i++) {
                    built.put(i, this.buildEntry(i, new TabColumns.Cell(Text.mm("<gold>Slot " + i), null, 0)));
                }

                this.updatePacket(this.addActions, new ArrayList<>(built.values()));
                this.removeCtor.newInstance(List.of(this.slotIds[0]));
                return null;
            } catch (ReflectiveOperationException | RuntimeException var3) {
                return var3.getClass().getSimpleName() + ": " + var3.getMessage();
            }
        }
    }

    public record Cell(Component text, ProfileProperty skin, int ping) {
        static TabColumns.Cell blank() {
            return new TabColumns.Cell(Component.empty(), null, 0);
        }
    }

    record Section(Component title, List<TabColumns.Cell> rows) {
    }

    static final class ViewerState {
        final String[] skinKeys = new String[TabColumns.SLOTS];
        final String[] textKeys = new String[TabColumns.SLOTS];
        /** Fake entries this viewer has right now. */
        int count;
    }
}
