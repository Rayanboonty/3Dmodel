package net.vortexpvp.smp;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.ClickCallback.Options;
import net.vortexpvp.arena.VanillaTerrain;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.dialog.Dialogs;
import net.vortexpvp.dialog.LobbyDialogs;
import net.vortexpvp.gui.Menu;
import net.vortexpvp.gui.Ui;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import net.vortexpvp.util.TextWidth;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

public final class ServerSelector {
    private final VortexPvP plugin;
    private final ModeManager modes;
    private final File file;
    private final List<ServerSelector.Entry> entries = new ArrayList<>();

    public ServerSelector(VortexPvP plugin, ModeManager modes) {
        this.plugin = plugin;
        this.modes = modes;
        this.file = new File(plugin.getDataFolder(), "selector.yml");
    }

    public void load() {
        this.entries.clear();
        if (!this.file.exists()) {
            this.defaults();
            this.save();
        } else {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);

            for (Object o : y.getList("entries", List.of())) {
                if (o instanceof Map<?, ?> m) {
                    ServerSelector.Entry e = new ServerSelector.Entry();
                    e.id = String.valueOf(m.get("id"));
                    e.name = String.valueOf(m.get("name"));
                    Material icon = Material.matchMaterial(String.valueOf(m.get("icon")));
                    e.icon = icon == null ? Material.GRASS_BLOCK : icon;
                    if (m.get("lore") instanceof List<?> l) {
                        l.forEach(x -> e.lore.add(String.valueOf(x)));
                    }

                    try {
                        e.action = ServerSelector.Action.valueOf(String.valueOf(m.get("action")));
                        e.status = ServerSelector.Status.valueOf(String.valueOf(m.get("status")));
                    } catch (IllegalArgumentException var9) {
                    }

                    Object t = m.get("target");
                    e.target = t == null ? "" : String.valueOf(t);
                    this.entries.add(e);
                }
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options()
            .setHeader(
                List.of(
                    "Server selector entries (edit in game: /pvpadmin -> Server Selector).",
                    "action: PRACTICE, MODE (target = a world mode, finds its world), WORLD (target = world name),",
                    "HUB, COMMAND (target = command). status: OPEN, SOON, MAINTENANCE, HIDDEN."
                )
            );
        List<Map<String, Object>> list = new ArrayList<>();

        for (ServerSelector.Entry e : this.entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id);
            m.put("name", e.name);
            m.put("icon", e.icon.name());
            m.put("lore", e.lore);
            m.put("action", e.action.name());
            m.put("target", e.target);
            m.put("status", e.status.name());
            list.add(m);
        }

        y.set("entries", list);

        try {
            y.save(this.file);
        } catch (IOException var6) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save selector.yml", (Throwable)var6);
        }
    }

    private void defaults() {
        this.add(
            "practice",
            "<b><gradient:#FFE259:#FFA751>PRACTICE</gradient></b>",
            Material.DIAMOND_SWORD,
            ServerSelector.Action.PRACTICE,
            "",
            "Duels, Ranked LT5 → HT1, FFA,",
            "parties and 20+ kits."
        );
        this.add(
            "survival",
            "<b><gradient:#7CFF6B:#2E9B3A>SURVIVAL</gradient></b>",
            Material.GRASS_BLOCK,
            ServerSelector.Action.MODE,
            "SURVIVAL",
            "Classic survival with friends.",
            "Your own inventory and homes."
        );
        this.add(
            "ranked",
            "<b><gradient:#FFF3A0:#FFB800>RANKED SMP</gradient></b>",
            Material.GOLDEN_SWORD,
            ServerSelector.Action.MODE,
            "RANKED",
            "Every kill earns rank points.",
            "Climb the tiers, show off your tag."
        );
        this.add(
            "lifesteal",
            "<b><gradient:#FF6B6B:#B3001B>LIFESTEAL</gradient></b>",
            Material.TOTEM_OF_UNDYING,
            ServerSelector.Action.MODE,
            "LIFESTEAL",
            "Kill to steal a heart.",
            "Lose them all and you're out."
        );
        this.add(
            "strength",
            "<b><gradient:#E2B5FF:#9B3DFF>STRENGTH SMP</gradient></b>",
            Material.BLAZE_POWDER,
            ServerSelector.Action.MODE,
            "STRENGTH",
            "Kills make you stronger,",
            "deaths make you weaker."
        );
        this.add(
            "eco",
            "<b><gradient:#7DFFB0:#1FAF5A>ECO SMP</gradient></b>",
            Material.EMERALD,
            ServerSelector.Action.MODE,
            "ECO",
            "Earn, trade and spend money.",
            "/shop, /sell, /pay, /baltop."
        );
    }

    private ServerSelector.Entry add(String id, String name, Material icon, ServerSelector.Action action, String target, String... lore) {
        ServerSelector.Entry e = new ServerSelector.Entry();
        e.id = id;
        e.name = name;
        e.icon = icon;
        e.action = action;
        e.target = target;
        e.lore.addAll(List.of(lore));
        this.entries.add(e);
        return e;
    }

    private World world(ServerSelector.Entry e) {
        return switch (e.action) {
            case PRACTICE -> this.plugin.lobby().lobby().getWorld();
            case MODE -> {
                WorldMode m = WorldMode.parse(e.target, null);
                if (m == null) {
                    yield null;
                } else {
                    for (World w : Bukkit.getWorlds()) {
                        String n = w.getName();
                        if (this.modes.mode(w) == m && !n.endsWith("_nether") && !n.endsWith("_the_end")) {
                            yield w;
                        }
                    }

                    yield null;
                }
            }
            case WORLD -> Bukkit.getWorld(e.target);
            case HUB -> this.modes.hubWorld();
            default -> null;
        };
    }

    private int players(ServerSelector.Entry e) {
        if (e.action == ServerSelector.Action.PRACTICE) {
            int n = 0;

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (this.plugin.sessions().state(p) != PlayerState.OUTSIDE) {
                    n++;
                }
            }

            return n;
        } else {
            World w = this.world(e);
            if (w == null) {
                return 0;
            } else {
                int n = w.getPlayers().size();

                for (String suffix : List.of("_nether", "_the_end")) {
                    World sub = Bukkit.getWorld(w.getName() + suffix);
                    if (sub != null) {
                        n += sub.getPlayers().size();
                    }
                }

                return n;
            }
        }
    }

    private ServerSelector.Status effective(ServerSelector.Entry e) {
        return e.status == ServerSelector.Status.OPEN && e.action != ServerSelector.Action.COMMAND && this.world(e) == null
            ? ServerSelector.Status.SOON
            : e.status;
    }

    public void open(Player p) {
        List<ServerSelector.Entry> shown = new ArrayList<>();

        for (ServerSelector.Entry e : this.entries) {
            if (this.effective(e) != ServerSelector.Status.HIDDEN) {
                shown.add(e);
            }
        }

        if (this.plugin.dialogs().useDialog(p)) {
            this.openDialog(p);
        } else {
            new ServerSelector.SelectorMenu(p, shown).open();
        }

        Snd.click(p);
        if (this.plugin.welcome() != null) {
            this.plugin.welcome().done(p, "SELECTOR");
        }
    }

    public void openDialog(Player p) {
        List<ItemStack> icons = new ArrayList<>();
        List<Component> texts = new ArrayList<>();

        for (ServerSelector.Entry e : this.entries) {
            ServerSelector.Status st = this.effective(e);
            if (st != ServerSelector.Status.HIDDEN) {
                int n = this.players(e);
                List<Component> hover = new ArrayList<>();
                hover.add(Text.mm(e.name));

                for (String line : e.lore) {
                    hover.add(Text.mm("<gray>" + line));
                }

                hover.add(Component.empty());
                hover.add(Text.mm("<gray>Playing: <white>" + n));
                hover.add(Text.mm(st == ServerSelector.Status.OPEN ? "<green>▶ Click to join" : st.badge));
                Component text = Component.text()
                    .append(Text.mm(e.name))
                    .append(Component.newline())
                    .append(Text.mm(st == ServerSelector.Status.OPEN ? "<gray>" + n + " playing" : st.badge))
                    .hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.newlines(), hover)))
                    .clickEvent(ClickEvent.callback(a -> Tasks.ensureSync(() -> {
                        if (!this.plugin.sessions().get(p).dialogCooldown(400L)) {
                            this.join(p, e);
                        }
                    }), Options.builder().uses(-1).lifetime(Duration.ofMinutes(10L)).build()))
                    .build();
                ItemStack icon = new ItemStack(e.icon);
                icon.setAmount(Math.max(1, Math.min(64, n)));
                icon.editMeta(m -> {
                    m.displayName(Text.noItalic(Text.mm(e.name)));
                    List<Component> lore = new ArrayList<>();

                    for (int i = 1; i < hover.size(); i++) {
                        lore.add(Text.noItalic(hover.get(i)));
                    }

                    m.lore(lore);
                    m.setEnchantmentGlintOverride(st == ServerSelector.Status.OPEN);
                    m.addItemFlags(ItemFlag.values());
                });
                icons.add(icon);
                texts.add(text);
            }
        }

        List<DialogBody> body = new ArrayList<>();
        int width = TextWidth.fit(TextWidth.widest(texts), 70, 260);

        for (int i = 0; i < icons.size(); i++) {
            body.add(DialogBody.item(icons.get(i)).description(DialogBody.plainMessage(texts.get(i), width)).showDecorations(true).showTooltip(true).build());
        }

        if (body.isEmpty()) {
            body.add(DialogBody.plainMessage(Text.mm("<gray>No modes are open right now."), 200));
        }

        List<ActionButton> buttons = new ArrayList<>();
        World hub = this.modes.hubWorld();
        if (hub != null && !p.getWorld().equals(hub)) {
            buttons.add(Dialogs.button(Text.mm("<aqua>⌂ Hub"), Text.mm("<gray>Back to the server spawn"), 150, this.plugin.dialogs().action(p, () -> {
                p.closeDialog();
                this.modes.travel(p, hub);
            })));
        }

        buttons.add(
            Dialogs.button(
                Text.mm("<gold>★ Profile"), Text.mm("<gray>Your ranks and stats"), 150, this.plugin.dialogs().action(p, () -> this.plugin.hub().profile(p))
            )
        );
        buttons.add(
            Dialogs.button(
                Text.mm("<white>" + Bukkit.getOnlinePlayers().size() + " online"),
                Text.mm("<gray>Players on " + Text.esc(Cfg.s(Opt.SERVER_NAME))),
                150,
                this.plugin.dialogs().action(p, () -> this.openDialog(p))
            )
        );
        DialogBase base = DialogBase.builder(Text.mm("<aqua>Click a mode to play it!"))
            .canCloseWithEscape(true)
            .pause(false)
            .afterAction(DialogAfterAction.NONE)
            .body(body)
            .build();
        Dialogs.show(p, base, DialogType.multiAction(buttons).columns(3).exitAction(this.plugin.dialogs().lobby().close(p)).build());
    }

    private void join(Player p, ServerSelector.Entry e) {
        if (p.isOnline()) {
            ServerSelector.Status st = this.effective(e);
            if (st == ServerSelector.Status.OPEN || st == ServerSelector.Status.MAINTENANCE && p.hasPermission("vortexpvp.admin")) {
                p.closeDialog();
                if (e.action == ServerSelector.Action.COMMAND) {
                    p.performCommand(e.target.startsWith("/") ? e.target.substring(1) : e.target);
                } else {
                    World w = this.world(e);
                    if (w != null) {
                        this.modes.travel(p, w);
                    }
                }
            } else {
                p.sendActionBar(Text.mm(st.badge));
                Snd.error(p);
            }
        }
    }

    private LobbyDialogs ui() {
        return this.plugin.dialogs().lobby();
    }

    public void editor(Player p) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (ServerSelector.Entry e : this.entries) {
            World w = this.world(e);

            String target = switch (e.action) {
                case PRACTICE -> "Practice lobby";
                default -> (w == null ? "<red>no world yet" : w.getName()) + " <dark_gray>(" + e.action.name().toLowerCase(Locale.ROOT) + " " + e.target + ")";
                case HUB -> "Hub";
                case COMMAND -> "/" + e.target;
            };
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(e.icon),
                    Text.mm(e.name),
                    Text.mm(e.status.badge + " <dark_gray>• <gray>" + target),
                    List.of(Text.mm(e.name), Component.empty(), Text.mm("<green>▶ Click to edit")),
                    () -> this.edit(p, e)
                )
            );
        }

        List<ActionButton> b = new ArrayList<>();
        b.add(
            this.ui()
                .btn(
                    p,
                    "<green>+ Add Entry",
                    null,
                    () -> this.plugin
                        .dialogs()
                        .text(
                            p,
                            Text.mm("<gold><b>New entry"),
                            "Name (MiniMessage)",
                            "<b>NEW MODE</b>",
                            128,
                            name -> {
                                ServerSelector.Entry ex = this.add(
                                    "entry" + System.currentTimeMillis() % 100000L,
                                    name,
                                    Material.GRASS_BLOCK,
                                    ServerSelector.Action.MODE,
                                    "SURVIVAL",
                                    "Describe this mode."
                                );
                                this.save();
                                this.edit(p, ex);
                            }
                        )
                )
        );
        b.add(this.ui().btn(p, "<aqua>Preview", "<gray>Open the selector as players see it", () -> this.open(p)));
        b.add(this.ui().btn(p, "<gray>« Admin Panel", null, () -> this.plugin.dialogs().admin().main(p)));
        this.ui().screen(p, LobbyDialogs.title("Server Selector", "Click an entry to edit it"), tiles, List.of(), null, b, 3, this.ui().close(p));
    }

    private void edit(Player p, ServerSelector.Entry e) {
        List<Component> header = List.of(
            Text.mm(e.name + "  " + e.status.badge),
            Text.mm("<gray>Opens: <white>" + e.action.name().toLowerCase(Locale.ROOT) + (e.target.isBlank() ? "" : " " + Text.esc(e.target))),
            Text.mm("<gray>" + Text.esc(String.join(" / ", e.lore)))
        );
        List<ActionButton> b = new ArrayList<>();
        b.add(
            this.ui()
                .btn(
                    p,
                    "<gold>Name",
                    "<gray>MiniMessage, gradients welcome",
                    () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Name"), "Name", e.name, 128, s -> {
                        e.name = s;
                        this.save();
                        this.edit(p, e);
                    })
                )
        );
        b.add(
            this.ui()
                .btn(
                    p,
                    "<gold>Description",
                    "<gray>Lines separated by |",
                    () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Description"), "Lines (| = new line)", String.join("|", e.lore), 256, s -> {
                        e.lore.clear();

                        for (String part : s.split("\\|")) {
                            if (!part.isBlank()) {
                                e.lore.add(part.trim());
                            }
                        }

                        this.save();
                        this.edit(p, e);
                    })
                )
        );
        b.add(this.ui().btn(p, "<gold>Icon = Your Hand", null, () -> {
            ItemStack h = p.getInventory().getItemInMainHand();
            if (!h.isEmpty()) {
                e.icon = h.getType();
                this.save();
            }

            this.edit(p, e);
        }));
        b.add(this.ui().btn(p, "<yellow>Opens…", "<gray>Practice, a world mode, a world, hub or a command", () -> {
            List<Dialogs.Choice> c = new ArrayList<>();
            c.add(new Dialogs.Choice("PRACTICE", "<gold>Practice lobby"));

            for (WorldMode m : WorldMode.values()) {
                if (m.smp()) {
                    c.add(new Dialogs.Choice("MODE:" + m.name(), m.mm() + " <gray>(first world with this mode)"));
                }
            }

            for (World w : Bukkit.getWorlds()) {
                if (!VanillaTerrain.isSourceWorld(w.getName())) {
                    c.add(new Dialogs.Choice("WORLD:" + w.getName(), "<white>World " + w.getName()));
                }
            }

            c.add(new Dialogs.Choice("HUB", "<aqua>Hub"));
            c.add(new Dialogs.Choice("COMMAND", "<gray>A command…"));
            this.plugin.dialogs().choice(p, Text.mm("<gold><b>Opens"), "Target", c, null, v -> {
                if (v.equals("COMMAND")) {
                    this.plugin.dialogs().text(p, Text.mm("<gold><b>Command"), "Command the player runs", e.target, 128, s -> {
                        e.action = ServerSelector.Action.COMMAND;
                        e.target = s.startsWith("/") ? s.substring(1) : s;
                        this.save();
                        this.edit(p, e);
                    });
                } else {
                    int colon = v.indexOf(58);
                    e.action = ServerSelector.Action.valueOf(colon < 0 ? v : v.substring(0, colon));
                    e.target = colon < 0 ? "" : v.substring(colon + 1);
                    this.save();
                    this.edit(p, e);
                }
            });
        }));
        b.add(this.ui().btn(p, "<yellow>Status", "<gray>Open, coming soon, maintenance, hidden", () -> {
            List<Dialogs.Choice> c = new ArrayList<>();

            for (ServerSelector.Status s : ServerSelector.Status.values()) {
                c.add(new Dialogs.Choice(s.name(), s.badge));
            }

            this.plugin.dialogs().choice(p, Text.mm("<gold><b>Status"), "Status", c, e.status.name(), v -> {
                e.status = ServerSelector.Status.valueOf(v);
                this.save();
                this.edit(p, e);
            });
        }));
        b.add(this.ui().btn(p, "<aqua>▲ Move Up", null, () -> {
            int i = this.entries.indexOf(e);
            if (i > 0) {
                this.entries.remove(i);
                this.entries.add(i - 1, e);
                this.save();
            }

            this.edit(p, e);
        }));
        b.add(this.ui().btn(p, "<aqua>▼ Move Down", null, () -> {
            int i = this.entries.indexOf(e);
            if (i >= 0 && i < this.entries.size() - 1) {
                this.entries.remove(i);
                this.entries.add(i + 1, e);
                this.save();
            }

            this.edit(p, e);
        }));
        b.add(this.ui().btn(p, "<red>Delete", null, () -> {
            this.entries.remove(e);
            this.save();
            this.editor(p);
        }));
        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Selector Entry", Text.esc(e.id)),
                List.of(),
                header,
                null,
                b,
                2,
                this.ui().btn(p, "<gray>« All Entries", null, () -> this.editor(p))
            );
    }

    public List<String[]> publicEntries() {
        List<String[]> out = new ArrayList<>();

        for (ServerSelector.Entry e : this.entries) {
            ServerSelector.Status st = this.effective(e);
            if (st != ServerSelector.Status.HIDDEN) {
                out.add(
                    new String[]{
                        e.id, Text.plain(Text.mm(e.name)), String.join(" ", e.lore), String.valueOf(this.players(e)), st.name(), e.icon.getKey().getKey()
                    }
                );
            }
        }

        return out;
    }

    public Location arrival(Player p, ServerSelector.Entry e) {
        World w = this.world(e);
        return w == null ? null : this.modes.arrival(p, w);
    }

    public static enum Action {
        PRACTICE,
        MODE,
        WORLD,
        HUB,
        COMMAND;
    }

    public static final class Entry {
        String id;
        String name;
        Material icon;
        List<String> lore = new ArrayList<>();
        ServerSelector.Action action = ServerSelector.Action.MODE;
        String target = "";
        ServerSelector.Status status = ServerSelector.Status.OPEN;
    }

    private final class SelectorMenu extends Menu {
        private final List<ServerSelector.Entry> shown;

        SelectorMenu(Player viewer, List<ServerSelector.Entry> shown) {
            super(ServerSelector.this.plugin, viewer);
            this.shown = shown;
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>» <b><gradient:#FFE066:#FFAA00>SERVER SELECTOR</gradient></b> <dark_gray>«");
        }

        @Override
        protected int rows() {
            return Math.min(6, Ui.rowsFor(this.shown.size()) + 1);
        }

        @Override
        protected void draw() {
            int rows = this.rows();
            int[] slots = Ui.centered(this.shown.size(), rows - 1);

            for (int i = 0; i < slots.length; i++) {
                ServerSelector.Entry e = this.shown.get(i);
                ServerSelector.Status st = ServerSelector.this.effective(e);
                int n = ServerSelector.this.players(e);
                List<String> lore = new ArrayList<>();
                lore.add(st.badge + " <dark_gray>• <white>" + n + " <gray>playing");
                lore.add("");

                for (String line : e.lore) {
                    lore.add("<gray>" + line);
                }

                lore.add("");
                lore.add(st == ServerSelector.Status.OPEN ? "<green>▶ Click to join" : "<gray>Not available right now");
                ItemStack icon = Items.of(e.icon).name(e.name).lore(lore).hideAll().glint(st == ServerSelector.Status.OPEN).build();
                icon.setAmount(Math.max(1, Math.min(64, n)));
                this.set(slots[i], icon, c -> {
                    this.viewer.closeInventory();
                    ServerSelector.this.join(this.viewer, e);
                });
            }

            int last = (rows - 1) * 9;
            World hub = ServerSelector.this.modes.hubWorld();
            if (hub != null && !this.viewer.getWorld().equals(hub)) {
                this.set(
                    last + 3, Items.of(Material.BEACON).name("<aqua><b>⌂ Hub").lore("<gray>Back to the server spawn.", "", "<green>▶ Click").build(), c -> {
                        this.viewer.closeInventory();
                        ServerSelector.this.modes.travel(this.viewer, hub);
                    }
                );
            }

            this.set(
                last + 4,
                Items.of(Material.PLAYER_HEAD).skull(this.viewer).name("<gold><b>★ Profile").lore("<gray>Your ranks and stats.", "", "<green>▶ Click").build(),
                c -> {
                    this.viewer.closeInventory();
                    this.plugin.hub().profile(this.viewer);
                }
            );
            this.set(last + 5, Ui.close(), c -> this.viewer.closeInventory());
            this.border(Ui.FRAME);
        }
    }

    public static enum Status {
        OPEN("<green>● OPEN"),
        SOON("<yellow>◐ COMING SOON"),
        MAINTENANCE("<red>✖ MAINTENANCE"),
        HIDDEN("<dark_gray>hidden");

        final String badge;

        private Status(String badge) {
            this.badge = badge;
        }
    }
}
