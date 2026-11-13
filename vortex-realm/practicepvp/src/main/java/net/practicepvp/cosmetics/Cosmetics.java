package net.practicepvp.cosmetics;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.dialog.LobbyDialogs;
import net.practicepvp.kit.Kit;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Text;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ShieldMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

/**
 * Practice cosmetics: an armor trim (pattern + material) put on every trimmable armor piece of the kits, with an
 * optional different trim per kit, and a shield design (base colour + up to 6 banner layers) put on every shield.
 * Stored per player in the cosmetics table; applied when a kit is given (matches, FFA, refills).
 */
public final class Cosmetics implements Listener, CommandExecutor, org.bukkit.command.TabCompleter {
    public static final int MAX_LAYERS = 6;
    private static final String ALL = "*";
    private final PracticePlugin plugin;
    private final Map<UUID, Cosmetics.Data> data = new ConcurrentHashMap<>();
    /** Ready-made shield designs: name, base colour, then pattern:colour layers. */
    private static final String[][] PRESETS = new String[][]{
        {"Vortex", "PURPLE", "gradient:CYAN", "circle:MAGENTA", "border:BLACK"},
        {"Flame", "ORANGE", "gradient_up:RED", "triangles_bottom:YELLOW", "border:RED"},
        {"Royal", "BLUE", "rhombus:YELLOW", "circle:BLUE", "curly_border:YELLOW"},
        {"Skull", "BLACK", "skull:WHITE", "border:GRAY"},
        {"Creeper", "LIME", "creeper:BLACK", "border:GREEN"},
        {"Frost", "LIGHT_BLUE", "flower:WHITE", "gradient:BLUE", "border:WHITE"},
        {"Crusader", "WHITE", "straight_cross:RED", "border:RED"},
        {"Nether", "RED", "piglin:BLACK", "gradient_up:BLACK"}
    };

    public Cosmetics(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ data

    /** One player's choices. Trims are "pattern:material" per kit id, "*" = every kit; "none" = no trim. */
    public static final class Data {
        final Map<String, String> trims = new HashMap<>();
        DyeColor shieldBase;
        final List<String[]> layers = new ArrayList<>();

        String serialize() {
            StringBuilder sb = new StringBuilder();

            for (Map.Entry<String, String> e : this.trims.entrySet()) {
                sb.append("trim.").append(e.getKey()).append('=').append(e.getValue()).append(';');
            }

            if (this.shieldBase != null) {
                sb.append("shield=").append(this.shieldBase.name());

                for (String[] l : this.layers) {
                    sb.append('|').append(l[0]).append(':').append(l[1]);
                }

                sb.append(';');
            }

            return sb.toString();
        }

        static Cosmetics.Data parse(String s) {
            Cosmetics.Data d = new Cosmetics.Data();
            if (s != null) {
                for (String part : s.split(";")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) {
                        String k = part.substring(0, eq);
                        String v = part.substring(eq + 1);
                        if (k.startsWith("trim.")) {
                            d.trims.put(k.substring(5), v);
                        } else if (k.equals("shield")) {
                            String[] bits = v.split("\\|");

                            try {
                                d.shieldBase = DyeColor.valueOf(bits[0]);
                            } catch (IllegalArgumentException var11) {
                                d.shieldBase = null;
                            }

                            for (int i = 1; i < bits.length && d.layers.size() < MAX_LAYERS; i++) {
                                String[] pc = bits[i].split(":");
                                if (pc.length == 2) {
                                    d.layers.add(pc);
                                }
                            }
                        }
                    }
                }
            }

            return d;
        }
    }

    public Cosmetics.Data of(UUID u) {
        return this.data.computeIfAbsent(u, k -> new Cosmetics.Data());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        this.load(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.data.remove(e.getPlayer().getUniqueId());
    }

    public void load(UUID u) {
        if (this.plugin.db() != null) {
            this.plugin.db().supply(c -> {
                try (PreparedStatement ps = c.prepareStatement("SELECT data FROM pp_cosmetics WHERE uuid=?")) {
                    ps.setString(1, u.toString());

                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getString(1) : null;
                    }
                }
            }).thenAccept(s -> {
                if (s != null) {
                    this.data.put(u, Cosmetics.Data.parse(s));
                }
            }).exceptionally(err -> null);
        }
    }

    private void save(UUID u) {
        Cosmetics.Data d = this.data.get(u);
        if (d != null && this.plugin.db() != null) {
            String s = d.serialize();
            this.plugin.db().run(c -> {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM pp_cosmetics WHERE uuid=?")) {
                    del.setString(1, u.toString());
                    del.executeUpdate();
                }

                if (!s.isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO pp_cosmetics (uuid, data) VALUES (?,?)")) {
                        ps.setString(1, u.toString());
                        ps.setString(2, s);
                        ps.executeUpdate();
                    }
                }
            }).exceptionally(err -> null);
        }
    }

    /** Sets a player's trim for one kit ("*" = every kit): "pattern:material", "none", or null to follow every kit. */
    public void setTrim(UUID u, String target, String value) {
        Cosmetics.Data d = this.of(u);
        if (value == null) {
            d.trims.remove(target);
        } else {
            d.trims.put(target, value);
        }

        this.save(u);
    }

    /** True when the kit has its own trim (or "none"), false when it follows the trim of every kit. */
    public boolean ownTrim(UUID u, String kitId) {
        Cosmetics.Data d = this.data.get(u);
        return d != null && d.trims.containsKey(kitId);
    }

    public static String label(String key) {
        return nice(key);
    }

    // ------------------------------------------------------------------ registries

    private static TrimPattern trimPattern(String key) {
        try {
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN).get(NamespacedKey.minecraft(key));
        } catch (RuntimeException var2) {
            return null;
        }
    }

    private static TrimMaterial trimMaterial(String key) {
        try {
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL).get(NamespacedKey.minecraft(key));
        } catch (RuntimeException var2) {
            return null;
        }
    }

    private static PatternType bannerPattern(String key) {
        try {
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN).get(NamespacedKey.minecraft(key));
        } catch (RuntimeException var2) {
            return null;
        }
    }

    private static <T extends org.bukkit.Keyed> List<String> keys(RegistryKey<T> key) {
        List<String> out = new ArrayList<>();

        try {
            Registry<T> reg = RegistryAccess.registryAccess().getRegistry(key);

            for (T o : reg) {
                NamespacedKey k = reg.getKey(o);
                if (k != null && k.getNamespace().equals("minecraft")) {
                    out.add(k.getKey());
                }
            }
        } catch (RuntimeException var6) {
        }

        out.sort(null);
        return out;
    }

    public static List<String> trimPatterns() {
        return keys(RegistryKey.TRIM_PATTERN);
    }

    public static List<String> trimMaterials() {
        return keys(RegistryKey.TRIM_MATERIAL);
    }

    public static List<String> bannerPatterns() {
        List<String> out = keys(RegistryKey.BANNER_PATTERN);
        out.remove("base");
        return out;
    }

    // ------------------------------------------------------------------ applying

    /** The trim a player uses for a kit: {pattern, material}, or null for none. */
    public String[] trimFor(UUID u, String kitId) {
        Cosmetics.Data d = this.data.get(u);
        if (d == null) {
            return null;
        } else {
            String v = d.trims.get(kitId);
            if (v == null) {
                v = d.trims.get(ALL);
            }

            if (v != null && !v.equals("none")) {
                String[] pm = v.split(":");
                return pm.length == 2 ? pm : null;
            } else {
                return null;
            }
        }
    }

    /** Puts the player's trim and shield on the items of a kit (armor 36-39, everything else for shields). */
    public void apply(Player p, String kitId, ItemStack[] c) {
        Cosmetics.Data d = this.data.get(p.getUniqueId());
        if (d != null) {
            String[] trim = p.hasPermission("practice.cosmetics.trims") ? this.trimFor(p.getUniqueId(), kitId) : null;
            boolean shield = d.shieldBase != null && p.hasPermission("practice.cosmetics.shield");

            for (int i = 0; i < c.length; i++) {
                ItemStack it = c[i];
                if (it != null && !it.isEmpty()) {
                    if (trim != null && Tag.ITEMS_TRIMMABLE_ARMOR.isTagged(it.getType())) {
                        c[i] = withTrim(it, trim[0], trim[1]);
                    } else if (shield && it.getType() == Material.SHIELD) {
                        c[i] = withShield(it, d);
                    }
                }
            }
        }
    }

    public static ItemStack withTrim(ItemStack it, String pattern, String material) {
        TrimPattern tp = trimPattern(pattern);
        TrimMaterial tm = trimMaterial(material);
        if (tp != null && tm != null) {
            ItemStack out = it.clone();
            ItemMeta m = out.getItemMeta();
            if (m instanceof ArmorMeta am) {
                am.setTrim(new ArmorTrim(tm, tp));
                out.setItemMeta(am);
            }

            return out;
        } else {
            return it;
        }
    }

    static ItemStack withShield(ItemStack it, Cosmetics.Data d) {
        return withShield(it, d.shieldBase, d.layers);
    }

    static ItemStack withShield(ItemStack it, DyeColor base, List<String[]> layers) {
        ItemStack out = it.clone();
        if (out.getItemMeta() instanceof ShieldMeta sm) {
            sm.setBaseColor(base);
            List<Pattern> pats = new ArrayList<>();

            for (String[] l : layers) {
                PatternType pt = bannerPattern(l[0]);
                if (pt != null) {
                    try {
                        pats.add(new Pattern(DyeColor.valueOf(l[1]), pt));
                    } catch (IllegalArgumentException var9) {
                    }
                }
            }

            sm.setPatterns(pats);
            out.setItemMeta(sm);
        }

        return out;
    }

    // ------------------------------------------------------------------ menus

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
        } else if (args.length == 0) {
            this.open(p);
        } else {
            Cosmetics.Data d = this.of(p.getUniqueId());
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("trim") && args.length >= 2) {
                String target = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : ALL;
                if (args[1].equalsIgnoreCase("none")) {
                    d.trims.put(args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : ALL, "none");
                    p.sendMessage(Text.mm("<gray>Trim removed."));
                } else if (args.length >= 3 && trimPatterns().contains(args[1].toLowerCase(Locale.ROOT)) && trimMaterials().contains(args[2].toLowerCase(Locale.ROOT))) {
                    d.trims.put(target, args[1].toLowerCase(Locale.ROOT) + ":" + args[2].toLowerCase(Locale.ROOT));
                    p.sendMessage(Text.mm("<green>Trim set: <white>" + nice(args[1]) + "</white> in <white>" + nice(args[2]) + "</white>" + (target.equals(ALL) ? " for every kit." : " for " + Text.esc(target) + ".")));
                } else {
                    p.sendMessage(Text.mm("<red>Usage: /cosmetics trim \\<pattern> \\<material> [kit]  or  /cosmetics trim none"));
                    p.sendMessage(Text.mm("<gray>Patterns: <white>" + String.join(", ", trimPatterns())));
                    p.sendMessage(Text.mm("<gray>Materials: <white>" + String.join(", ", trimMaterials())));
                    return true;
                }

                this.save(p.getUniqueId());
            } else if (sub.equals("shield") && args.length >= 2) {
                if (args[1].equalsIgnoreCase("none")) {
                    d.shieldBase = null;
                    d.layers.clear();
                    p.sendMessage(Text.mm("<gray>Plain shield."));
                } else {
                    String[] found = null;

                    for (String[] pr : PRESETS) {
                        if (pr[0].equalsIgnoreCase(args[1])) {
                            found = pr;
                        }
                    }

                    if (found == null) {
                        List<String> names = new ArrayList<>();

                        for (String[] pr : PRESETS) {
                            names.add(pr[0].toLowerCase(Locale.ROOT));
                        }

                        p.sendMessage(Text.mm("<red>Usage: /cosmetics shield \\<" + String.join("|", names) + "|none>"));
                        return true;
                    }

                    d.shieldBase = DyeColor.valueOf(found[1]);
                    d.layers.clear();

                    for (int i = 2; i < found.length; i++) {
                        d.layers.add(found[i].split(":"));
                    }

                    p.sendMessage(Text.mm("<green>Shield design: <white>" + found[0]));
                }

                this.save(p.getUniqueId());
            } else {
                this.open(p);
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("trim", "shield"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("trim")) {
            out.add("none");
            out.addAll(trimPatterns());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("trim")) {
            out.addAll(trimMaterials());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("shield")) {
            out.add("none");

            for (String[] pr : PRESETS) {
                out.add(pr[0].toLowerCase(Locale.ROOT));
            }
        }

        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.startsWith(typed));
        return out;
    }

    private LobbyDialogs ui() {
        return this.plugin.dialogs().lobby();
    }

    private static String nice(String key) {
        String[] w = key.replace("minecraft:", "").split("_");
        StringBuilder sb = new StringBuilder();

        for (String s : w) {
            if (!s.isEmpty()) {
                sb.append(sb.length() == 0 ? "" : " ").append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
            }
        }

        return sb.toString();
    }

    private static String nice(DyeColor c) {
        return nice(c.name().toLowerCase(Locale.ROOT));
    }

    private static ItemStack hide(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(m);
        }

        return it;
    }

    private ItemStack shieldIcon(Player p) {
        Cosmetics.Data d = this.of(p.getUniqueId());
        return d.shieldBase == null ? new ItemStack(Material.SHIELD) : withShield(new ItemStack(Material.SHIELD), d);
    }

    private ItemStack trimIcon(String[] trim) {
        ItemStack chest = hide(new ItemStack(Material.DIAMOND_CHESTPLATE));
        return trim == null ? chest : withTrim(chest, trim[0], trim[1]);
    }

    private String trimLabel(String[] trim) {
        return trim == null ? "<gray>None" : "<white>" + nice(trim[0]) + " <gray>in <white>" + nice(trim[1]);
    }

    private static LobbyDialogs.Tile tile(ItemStack icon, String name, String line, List<String> hover, Runnable click) {
        List<Component> h = new ArrayList<>();
        h.add(Text.mm(name));

        for (String s : hover) {
            h.add(Text.mm(s));
        }

        return new LobbyDialogs.Tile(icon, Text.mm(name), Text.mm(line), h, click);
    }

    /** The cosmetics hub. */
    public void open(Player p) {
        Cosmetics.Data d = this.of(p.getUniqueId());
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        String[] all = this.trimFor(p.getUniqueId(), ALL);
        tiles.add(
            tile(
                this.trimIcon(all),
                "<gold><b>Armor Trim",
                "<gray>Every kit: " + this.trimLabel(all),
                List.of("<gray>A trim pattern and material on all", "<gray>the armor of your kits.", "", "<green>▶ Click to choose"),
                () -> this.patterns(p, ALL)
            )
        );
        int custom = 0;

        for (String k : d.trims.keySet()) {
            if (!k.equals(ALL)) {
                custom++;
            }
        }

        tiles.add(
            tile(
                new ItemStack(Material.ARMOR_STAND),
                "<gold><b>Trim per Kit",
                "<gray>" + (custom == 0 ? "All kits use the same trim" : custom + " kit" + (custom == 1 ? "" : "s") + " with their own trim"),
                List.of("<gray>Give one kit a different trim", "<gray>(or none).", "", "<green>▶ Click to pick a kit"),
                () -> this.kits(p)
            )
        );
        tiles.add(
            tile(
                this.shieldIcon(p),
                "<gold><b>Shield Design",
                d.shieldBase == null ? "<gray>Plain shield" : "<white>" + nice(d.shieldBase) + " <gray>with " + d.layers.size() + " layer" + (d.layers.size() == 1 ? "" : "s"),
                List.of("<gray>Base colour and up to " + MAX_LAYERS + " patterns,", "<gray>or a ready-made design.", "", "<green>▶ Click to design"),
                () -> this.shield(p)
            )
        );
        tiles.add(
            tile(new ItemStack(Material.BARRIER), "<red><b>Reset Everything", "<gray>No trims, plain shield", List.of("<red>Removes all your cosmetics."), () -> {
                this.data.put(p.getUniqueId(), new Cosmetics.Data());
                this.save(p.getUniqueId());
                Snd.play(p, "block.anvil.land", 0.4F, 1.4F);
                this.open(p);
            })
        );
        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Cosmetics", "Trims and shield for your kits"),
                tiles,
                List.of(Text.mm("<gray>Shown on your kits in duels, FFA and events.")),
                null,
                List.of(),
                1,
                this.ui().close(p)
            );
    }

    private void kits(Player p) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (Kit k : this.plugin.kits().all()) {
            if (k.enabled && hasTrimmable(k)) {
                Cosmetics.Data d = this.of(p.getUniqueId());
                String own = d.trims.get(k.id);
                String[] t = this.trimFor(p.getUniqueId(), k.id);
                tiles.add(
                    tile(
                        k.iconStack(),
                        k.color + "<b>" + Text.esc(k.displayName),
                        own == null ? "<gray>Same as every kit" : this.trimLabel(t),
                        List.of("<green>▶ Click to choose a trim for this kit"),
                        () -> this.patterns(p, k.id)
                    )
                );
            }
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Trim per Kit", "Pick a kit"),
                tiles,
                List.of(),
                Text.mm("<gray>No kit has armor that can be trimmed."),
                List.of(this.ui().btn(p, "<gray>« Back", null, () -> this.open(p))),
                1,
                this.ui().close(p)
            );
    }

    private static boolean hasTrimmable(Kit k) {
        for (int i = 36; i <= 39 && i < k.contents.length; i++) {
            if (k.contents[i] != null && Tag.ITEMS_TRIMMABLE_ARMOR.isTagged(k.contents[i].getType())) {
                return true;
            }
        }

        return false;
    }

    private void patterns(Player p, String target) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        Cosmetics.Data d = this.of(p.getUniqueId());
        String[] now = this.trimFor(p.getUniqueId(), target);
        String mat = now == null ? "quartz" : now[1];
        if (!target.equals(ALL)) {
            tiles.add(tile(new ItemStack(Material.PAPER), "<white><b>Same as every kit", "<gray>Use your main trim", List.of(), () -> {
                d.trims.remove(target);
                this.save(p.getUniqueId());
                this.kits(p);
            }));
        }

        tiles.add(tile(hide(new ItemStack(Material.DIAMOND_CHESTPLATE)), "<white><b>No Trim", "<gray>Plain armor", List.of(), () -> {
            d.trims.put(target, "none");
            this.save(p.getUniqueId());
            Snd.play(p, "item.armor.equip_diamond", 0.7F, 1.0F);
            this.open(p);
        }));

        for (String pat : trimPatterns()) {
            boolean on = now != null && now[0].equals(pat);
            tiles.add(
                tile(
                    this.trimIcon(new String[]{pat, mat}),
                    (on ? "<green>✔ " : "") + "<gold><b>" + nice(pat),
                    "<gray>Then pick the material",
                    List.of("<green>▶ Click to choose its material"),
                    () -> this.materials(p, target, pat)
                )
            );
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Trim Pattern", target.equals(ALL) ? "Every kit" : target),
                tiles,
                List.of(),
                null,
                List.of(this.ui().btn(p, "<gray>« Back", null, () -> this.open(p))),
                1,
                this.ui().close(p)
            );
    }

    private void materials(Player p, String target, String pattern) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        Cosmetics.Data d = this.of(p.getUniqueId());

        for (String m : trimMaterials()) {
            tiles.add(
                tile(
                    this.trimIcon(new String[]{pattern, m}),
                    "<gold><b>" + nice(m),
                    "<gray>" + nice(pattern) + " in " + nice(m),
                    List.of("<green>▶ Click to use it"),
                    () -> {
                        d.trims.put(target, pattern + ":" + m);
                        this.save(p.getUniqueId());
                        Snd.play(p, "block.smithing_table.use", 0.7F, 1.2F);
                        p.sendMessage(
                            Text.mm(
                                "<green>Trim set: <white>"
                                    + nice(pattern)
                                    + "</white> in <white>"
                                    + nice(m)
                                    + "</white>"
                                    + (target.equals(ALL) ? " for every kit." : " for " + Text.esc(target) + ".")
                            )
                        );
                        this.open(p);
                    }
                )
            );
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Trim Material", nice(pattern)),
                tiles,
                List.of(),
                null,
                List.of(this.ui().btn(p, "<gray>« Back", null, () -> this.patterns(p, target))),
                1,
                this.ui().close(p)
            );
    }

    // ------------------------------------------------------------------ shield designer

    private void shield(Player p) {
        Cosmetics.Data d = this.of(p.getUniqueId());
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        tiles.add(
            tile(
                this.shieldIcon(p),
                "<gold><b>Base Colour",
                d.shieldBase == null ? "<gray>Plain" : "<white>" + nice(d.shieldBase),
                List.of("<green>▶ Click to change"),
                () -> this.baseColours(p)
            )
        );
        if (d.shieldBase != null && d.layers.size() < MAX_LAYERS) {
            tiles.add(
                tile(
                    new ItemStack(Material.LOOM),
                    "<gold><b>Add a Layer",
                    "<gray>" + d.layers.size() + "/" + MAX_LAYERS + " layers",
                    List.of("<gray>A banner pattern on top.", "", "<green>▶ Click to add"),
                    () -> this.layerPatterns(p)
                )
            );
        }

        if (!d.layers.isEmpty()) {
            tiles.add(tile(new ItemStack(Material.WATER_BUCKET), "<yellow><b>Remove Top Layer", "<gray>" + nice(d.layers.get(d.layers.size() - 1)[0]), List.of(), () -> {
                d.layers.remove(d.layers.size() - 1);
                this.save(p.getUniqueId());
                this.shield(p);
            }));
        }

        for (String[] preset : PRESETS) {
            List<String[]> layers = new ArrayList<>();

            for (int i = 2; i < preset.length; i++) {
                layers.add(preset[i].split(":"));
            }

            DyeColor base = DyeColor.valueOf(preset[1]);
            tiles.add(
                tile(
                    withShield(new ItemStack(Material.SHIELD), base, layers),
                    "<aqua><b>" + preset[0],
                    "<gray>Ready-made design",
                    List.of("<green>▶ Click to use it"),
                    () -> {
                        d.shieldBase = base;
                        d.layers.clear();
                        d.layers.addAll(layers);
                        this.save(p.getUniqueId());
                        Snd.play(p, "ui.loom.take_result", 0.8F, 1.0F);
                        this.shield(p);
                    }
                )
            );
        }

        tiles.add(tile(new ItemStack(Material.SHIELD), "<red><b>Plain Shield", "<gray>No design", List.of(), () -> {
            d.shieldBase = null;
            d.layers.clear();
            this.save(p.getUniqueId());
            this.shield(p);
        }));
        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Shield Design", "Every shield in your kits"),
                tiles,
                List.of(),
                null,
                List.of(this.ui().btn(p, "<gray>« Back", null, () -> this.open(p))),
                1,
                this.ui().close(p)
            );
    }

    private void baseColours(Player p) {
        Cosmetics.Data d = this.of(p.getUniqueId());
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (DyeColor c : DyeColor.values()) {
            tiles.add(tile(withShield(new ItemStack(Material.SHIELD), c, d.layers), "<white><b>" + nice(c), "<gray>Base colour", List.of(), () -> {
                d.shieldBase = c;
                this.save(p.getUniqueId());
                this.shield(p);
            }));
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Shield", "Base colour"),
                tiles,
                List.of(),
                null,
                List.of(this.ui().btn(p, "<gray>« Back", null, () -> this.shield(p))),
                1,
                this.ui().close(p)
            );
    }

    private void layerPatterns(Player p) {
        Cosmetics.Data d = this.of(p.getUniqueId());
        DyeColor contrast = d.shieldBase == DyeColor.BLACK ? DyeColor.WHITE : DyeColor.BLACK;
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (String pat : bannerPatterns()) {
            List<String[]> preview = new ArrayList<>(d.layers);
            preview.add(new String[]{pat, contrast.name()});
            tiles.add(
                tile(
                    withShield(new ItemStack(Material.SHIELD), d.shieldBase, preview),
                    "<gold><b>" + nice(pat),
                    "<gray>Then pick its colour",
                    List.of(),
                    () -> this.layerColours(p, pat)
                )
            );
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Shield", "Layer pattern"),
                tiles,
                List.of(),
                null,
                List.of(this.ui().btn(p, "<gray>« Back", null, () -> this.shield(p))),
                1,
                this.ui().close(p)
            );
    }

    private void layerColours(Player p, String pat) {
        Cosmetics.Data d = this.of(p.getUniqueId());
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (DyeColor c : DyeColor.values()) {
            List<String[]> preview = new ArrayList<>(d.layers);
            preview.add(new String[]{pat, c.name()});
            tiles.add(
                tile(withShield(new ItemStack(Material.SHIELD), d.shieldBase, preview), "<white><b>" + nice(c), "<gray>" + nice(pat), List.of(), () -> {
                    if (d.layers.size() < MAX_LAYERS) {
                        d.layers.add(new String[]{pat, c.name()});
                        this.save(p.getUniqueId());
                        Snd.play(p, "ui.loom.select_pattern", 0.8F, 1.0F);
                    }

                    this.shield(p);
                })
            );
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Shield", "Layer colour"),
                tiles,
                List.of(),
                null,
                List.of(this.ui().btn(p, "<gray>« Back", null, () -> this.layerPatterns(p))),
                1,
                this.ui().close(p)
            );
    }

    /** Self test: a trim and a shield design end up on the items. */
    public String selfCheck() {
        try {
            List<String> pats = trimPatterns();
            List<String> mats = trimMaterials();
            if (pats.isEmpty() || mats.isEmpty()) {
                return "no trim patterns/materials in the registry";
            } else {
                ItemStack t = withTrim(new ItemStack(Material.DIAMOND_HELMET), pats.get(0), mats.get(0));
                if (!(t.getItemMeta() instanceof ArmorMeta am) || am.getTrim() == null) {
                    return "trim not applied";
                } else {
                    Cosmetics.Data d = Cosmetics.Data.parse("trim.*=" + pats.get(0) + ":" + mats.get(0) + ";shield=RED|skull:WHITE|border:BLACK;");
                    if (d.layers.size() != 2 || d.shieldBase != DyeColor.RED || !Cosmetics.Data.parse(d.serialize()).serialize().equals(d.serialize())) {
                        return "data round trip failed: " + d.serialize();
                    } else {
                        ItemStack s = withShield(new ItemStack(Material.SHIELD), d);
                        return s.getItemMeta() instanceof ShieldMeta sm && sm.getBaseColor() == DyeColor.RED && sm.getPatterns().size() == 2
                            ? null
                            : "shield design not applied";
                    }
                }
            }
        } catch (RuntimeException var8) {
            return var8.toString();
        }
    }
}
