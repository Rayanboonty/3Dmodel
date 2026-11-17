package net.vortexrealm.social;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.pack.Glyphs;
import net.vortexrealm.staff.StaffManager;
import net.vortexrealm.util.Icons;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

public final class Ranks implements TabExecutor, Listener {
    private final VortexRealm plugin;
    private final File file;
    private final Map<String, Ranks.Rank> ranks = new LinkedHashMap<>();
    private final Map<UUID, String> assigned = new HashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final RankAccess access = new RankAccess(this);
    private boolean needsSave;

    public Ranks(VortexRealm plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ranks.yml");
    }

    public RankAccess access() {
        return this.access;
    }

    private void def(String id, String label, String from, String to, String icon, String name, int weight) {
        Ranks.Rank r = new Ranks.Rank();
        r.id = id;
        r.label = label;
        r.from = from;
        r.to = to;
        r.icon = icon;
        r.nameColor = name;
        r.weight = weight;
        this.ranks.put(id, r);
    }

    private void defaults() {
        this.def("owner", "OWNER", "#FF5F5F", "#FF9A3D", "item/nether_star", "#FF7070", 1000);
        this.def("admin", "ADMIN", "#FF4D4D", "#C40000", "item/redstone", "#FF5555", 900);
        this.def("dev", "DEV", "#7FF6FF", "#2E9BFF", "item/command_block_minecart", "#55FFFF", 850);
        this.def("mod", "MOD", "#6BFF8C", "#1FAF5A", "item/iron_sword", "#55FF55", 800);
        this.def("helper", "HELPER", "#8FD8FF", "#3D8BFF", "item/book", "#7FBFFF", 700);
        this.def("support", "SUPPORT", "#FFE066", "#FFAA00", "item/writable_book", "#FFD54A", 650);
        this.def("builder", "BUILDER", "#FFC48A", "#E07B00", "item/brick", "#FFB86B", 600);
        this.def("media", "MEDIA", "#FF4D4D", "#D40000", "glyph:play", "#FF6B6B", 550);
        this.def("partner", "PARTNER", "#C9A6FF", "#7C4DFF", "item/amethyst_shard", "#B388FF", 500);
        this.def("supreme", "SUPREME", "#FF5470", "#B3001B", "item/totem_of_undying", "#FF6B81", 400);
        this.def("frost", "FROST", "#9FE8FF", "#7A5CFF", "item/snowball", "#A6C8FF", 350);
        this.def("legend", "LEGEND", "#FFE27A", "#FF6F00", "item/blaze_powder", "#FFCB5C", 300);
        this.def("mvp", "MVP", "#FFC14D", "#FF7A00", "item/diamond", "#FFB347", 200);
        this.def("vip", "VIP", "#8CFF7A", "#2E9B3A", "item/emerald", "#7CFF6B", 100);
        this.def("default", "", "#FFFFFF", "#FFFFFF", "", "#E0E0E0", 0);
        String[] adminPerms = new String[]{
            "vortex.admin",
            "vortex.smpadmin",
            "vortex.staff",
            "vortex.staff.mod",
            "vortex.chat.bypass",
            "vortex.teleport.bypass",
            "vortex.warps.admin",
            "vortex.claims.admin",
            "vortex.homes.unlimited",
            "vortex.lobbyfly",
            "vortex.ah.unlimited"
        };
        this.perms("owner", true, "", "Runs Vortex Realm.", adminPerms);
        this.perms("admin", true, "", "Manages the server and the staff team.", adminPerms);
        this.perms(
            "dev",
            true,
            "",
            "Builds the plugins and the website.",
            "vortex.admin",
            "vortex.staff",
            "vortex.staff.mod",
            "vortex.chat.bypass",
            "vortex.lobbyfly"
        );
        this.perms(
            "mod",
            true,
            "",
            "Keeps games fair: reports, the hacker pool, chat.",
            "vortex.staff",
            "vortex.staff.mod",
            "vortex.chat.bypass",
            "vortex.lobbyfly",
            "vortex.teleport.bypass"
        );
        this.perms("helper", true, "", "Answers questions and helps new players.", "vortex.staff", "vortex.lobbyfly");
        this.perms("support", true, "", "Handles tickets and appeals on Discord.", "vortex.staff");
        this.perms("builder", true, "", "Builds the hub, lobbies and arenas.", "vortex.lobbyfly", "vortex.warps.admin", "vortex.homes.unlimited");
        this.perms("media", false, "", "Creators who make videos and streams on the server.", "vortex.lobbyfly", "vortex.homes.8");
        this.perms("partner", false, "", "Partnered communities and creators.", "vortex.lobbyfly", "vortex.homes.8");
        this.get("vip").permissions.addAll(List.of("vortex.homes.4"));
        this.get("mvp").inherit = "vip";
        this.get("mvp").permissions.addAll(List.of("vortex.homes.6", "vortex.lobbyfly"));
        this.get("legend").inherit = "mvp";
        this.get("legend").permissions.addAll(List.of("vortex.homes.8", "vortex.teleport.bypass"));
        this.get("frost").inherit = "legend";
        this.get("frost").permissions.addAll(List.of("vortex.homes.10"));
        this.get("supreme").inherit = "frost";
        this.get("supreme").permissions.addAll(List.of("vortex.homes.unlimited", "vortex.ah.unlimited"));
        this.sell(
            "vip", "$4.99", "The first step: a green tag and more homes.", "Green VIP tag in chat, tab and nametag", "4 homes", "Priority support on Discord"
        );
        this.sell("mvp", "$9.99", "Stand out in the lobby.", "Everything in VIP", "Orange MVP tag", "6 homes", "Fly in the PvP lobby");
        this.sell("legend", "$19.99", "For the regulars.", "Everything in MVP", "Golden LEGEND tag", "8 homes", "No teleport cooldowns");
        this.sell("frost", "$29.99", "A cold, rare look.", "Everything in LEGEND", "Icy FROST gradient tag", "10 homes");
        this.sell("supreme", "$49.99", "The top rank.", "Everything in FROST", "Red SUPREME tag", "Unlimited homes", "Unlimited auction listings");
    }

    private void perms(String id, boolean staff, String price, String desc, String... perms) {
        Ranks.Rank r = this.get(id);
        r.staff = staff;
        r.price = price;
        r.description = desc;
        r.permissions.addAll(List.of(perms));
    }

    private void sell(String id, String price, String desc, String... perks) {
        Ranks.Rank r = this.get(id);
        r.price = price;
        r.description = desc;
        r.perks.addAll(List.of(perks));
    }

    public void load() {
        this.ranks.clear();
        this.assigned.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection s = y.getConfigurationSection("ranks");
        if (s == null) {
            this.defaults();
            this.save();
        } else {
            for (String id : s.getKeys(false)) {
                ConfigurationSection r = s.getConfigurationSection(id);
                if (r != null) {
                    Ranks.Rank k = new Ranks.Rank();
                    k.id = id.toLowerCase(Locale.ROOT);
                    k.label = r.getString("label", id.toUpperCase(Locale.ROOT));
                    k.from = r.getString("gradient-from", "#FFFFFF");
                    k.to = r.getString("gradient-to", "#AAAAAA");
                    k.icon = r.getString("icon", "");
                    k.nameColor = r.getString("name-color", "#FFFFFF");
                    k.weight = r.getInt("weight", 0);
                    k.bold = r.getBoolean("bold", true);
                    k.permissions = new ArrayList<>(r.getStringList("permissions"));
                    k.perks = new ArrayList<>(r.getStringList("perks"));
                    k.description = r.getString("description", "");
                    k.price = r.getString("price", "");
                    k.inherit = r.getString("inherit", "");
                    k.staff = r.getBoolean("staff", false);
                    this.ranks.put(k.id, k);
                }
            }

            if (!this.ranks.containsKey("default")) {
                this.def("default", "", "#FFFFFF", "#FFFFFF", "", "#E0E0E0", 0);
            }

            if (!y.contains("version") || y.getInt("version") < 2) {
                Map<String, Ranks.Rank> mine = new LinkedHashMap<>(this.ranks);
                this.ranks.clear();
                this.defaults();

                for (Entry<String, Ranks.Rank> e : mine.entrySet()) {
                    Ranks.Rank fresh = this.ranks.get(e.getKey());
                    Ranks.Rank old = e.getValue();
                    if (fresh != null) {
                        old.permissions = fresh.permissions;
                        old.perks = fresh.perks;
                        old.description = fresh.description;
                        old.price = fresh.price;
                        old.inherit = fresh.inherit;
                        old.staff = fresh.staff;
                    }
                }

                this.ranks.clear();
                this.ranks.putAll(mine);
                this.needsSave = true;
            }

            if (y.getInt("version", 1) < 3) {
                Ranks.Rank media = this.ranks.get("media");
                if (media != null && "item/spyglass".equals(media.icon)) {
                    media.icon = "glyph:play";
                    if ("#FF7AB0".equalsIgnoreCase(media.from) && "#C2185B".equalsIgnoreCase(media.to)) {
                        media.from = "#FF4D4D";
                        media.to = "#D40000";
                        media.nameColor = "#FF6B6B";
                    }
                }

                this.needsSave = true;
            }

            ConfigurationSection pl = y.getConfigurationSection("players");
            if (pl != null) {
                for (String u : pl.getKeys(false)) {
                    try {
                        this.assigned.put(UUID.fromString(u), pl.getString(u, "default").toLowerCase(Locale.ROOT));
                    } catch (IllegalArgumentException var8) {
                    }
                }
            }

            if (this.needsSave) {
                this.needsSave = false;
                this.save();
            }

            for (Player p : Bukkit.getOnlinePlayers()) {
                this.apply(p);
            }
        }
    }

    public List<String> permissionsOf(Ranks.Rank r) {
        List<List<String>> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Ranks.Rank cur = r;

        while (cur != null && seen.add(cur.id)) {
            List<String> own = new ArrayList<>();
            own.add("vortex.rank." + cur.id);
            own.addAll(cur.permissions);
            chain.add(own);
            cur = cur.inherit != null && !cur.inherit.isBlank() ? this.ranks.get(cur.inherit.toLowerCase(Locale.ROOT)) : null;
        }

        List<String> out = new ArrayList<>();

        for (int i = chain.size() - 1; i >= 0; i--) {
            out.addAll(chain.get(i));
        }

        return out;
    }

    public List<Ranks.Member> members(Ranks.Rank r) {
        List<Ranks.Member> out = new ArrayList<>();
        Set<UUID> listed = new HashSet<>();

        for (Entry<UUID, String> e : this.assigned.entrySet()) {
            if (e.getValue().equals(r.id)) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
                Player on = op.getPlayer();
                out.add(new Ranks.Member(e.getKey(), op.getName() == null ? e.getKey().toString().substring(0, 8) : op.getName(), on != null, "/rank set"));
                listed.add(e.getKey());
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!listed.contains(p.getUniqueId()) && this.of(p) == r) {
                String how = p.hasPermission("vortex.rank." + r.id) && !p.isOp()
                    ? "permission vortex.rank." + r.id
                    : (r.isDefault() ? "default" : "staff role");
                out.add(new Ranks.Member(p.getUniqueId(), p.getName(), true, how));
            }
        }

        out.sort(Comparator.<Ranks.Member, Boolean>comparing(m -> !m.online()).thenComparing(Ranks.Member::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public void apply(Player p) {
        PermissionAttachment old = this.attachments.remove(p.getUniqueId());
        if (old != null) {
            try {
                p.removeAttachment(old);
            } catch (IllegalArgumentException var8) {
            }
        }

        Ranks.Rank r = this.of(p);
        PermissionAttachment a = p.addAttachment(this.plugin);

        for (Entry<String, Boolean> e : this.access.expand(this.permissionsOf(r)).entrySet()) {
            a.setPermission(e.getKey(), e.getValue());
        }

        this.attachments.put(p.getUniqueId(), a);
        p.recalculatePermissions();

        try {
            p.updateCommands();
        } catch (RuntimeException var7) {
        }
    }

    @EventHandler(
        priority = EventPriority.LOW
    )
    public void onJoin(PlayerJoinEvent e) {
        this.apply(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.attachments.remove(e.getPlayer().getUniqueId());
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("version", 3);
        y.options()
            .setHeader(
                List.of(
                    "Server ranks. icon = an item/block sprite (item/diamond, block/blue_ice...),",
                    "weight = order in tab (higher first). Players get a rank with /rank set, or with the permission",
                    "vortex.rank.<id> (e.g. from a LuckPerms group); the highest weight wins.",
                    "permissions = what the rank gives (plus vortex.rank.<id>); '-node' takes a permission away.",
                    "inherit = also give another rank's permissions. perks/price/description show on the website."
                )
            );

        for (Ranks.Rank r : this.ranks.values()) {
            String b = "ranks." + r.id + ".";
            y.set(b + "label", r.label);
            y.set(b + "gradient-from", r.from);
            y.set(b + "gradient-to", r.to);
            y.set(b + "icon", r.icon);
            y.set(b + "name-color", r.nameColor);
            y.set(b + "weight", r.weight);
            y.set(b + "bold", r.bold);
            y.set(b + "staff", r.staff);
            y.set(b + "inherit", r.inherit);
            y.set(b + "permissions", r.permissions);
            y.set(b + "description", r.description);
            y.set(b + "price", r.price);
            y.set(b + "perks", r.perks);
        }

        for (Entry<UUID, String> e : this.assigned.entrySet()) {
            y.set("players." + e.getKey(), e.getValue());
        }

        try {
            y.save(this.file);
        } catch (IOException var5) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save ranks.yml", (Throwable)var5);
        }
    }

    public List<Ranks.Rank> all() {
        List<Ranks.Rank> out = new ArrayList<>(this.ranks.values());
        out.sort(Comparator.comparingInt(r -> -r.weight));
        return out;
    }

    public Ranks.Rank get(String id) {
        return id == null ? null : this.ranks.get(id.toLowerCase(Locale.ROOT));
    }

    public Ranks.Rank byDefault() {
        Ranks.Rank d = this.ranks.get("default");
        return d != null ? d : this.all().get(this.all().size() - 1);
    }

    public Ranks.Rank of(Player p) {
        String set = this.assigned.get(p.getUniqueId());
        Ranks.Rank best = set == null ? null : this.ranks.get(set);

        for (Ranks.Rank r : this.ranks.values()) {
            if (!r.isDefault() && (best == null || r.weight > best.weight) && p.hasPermission("vortex.rank." + r.id) && !p.isOp()) {
                best = r;
            }
        }

        if (best == null && this.plugin.staff() != null) {
            StaffManager.Role role = this.plugin.staff().roleOf(p);
            if (role != null) {
                best = this.ranks.get(role.name().toLowerCase(Locale.ROOT));
            }
        }

        return best == null ? this.byDefault() : best;
    }

    public Ranks.Rank of(UUID u) {
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            return this.of(p);
        } else {
            Ranks.Rank r = this.get(this.assigned.get(u));
            return r == null ? this.byDefault() : r;
        }
    }

    public void assign(UUID u, String rank) {
        if (rank != null && !rank.equalsIgnoreCase("default")) {
            this.assigned.put(u, rank.toLowerCase(Locale.ROOT));
        } else {
            this.assigned.remove(u);
        }

        this.save();
        if (this.plugin.netSync() != null) {
            // on a region server the rank is also given on the primary, so every region has it
            this.plugin.netSync().forward("rank", u, rank);
        }
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            this.apply(p);

            try {
                this.plugin.boards().refreshNametag(p);
            } catch (RuntimeException var5) {
            }
        }
    }

    public Ranks.Rank create(String id) {
        Ranks.Rank r = new Ranks.Rank();
        r.id = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        r.label = id.toUpperCase(Locale.ROOT);
        r.from = "#FFFFFF";
        r.to = "#AAAAAA";
        r.icon = "item/paper";
        r.nameColor = "#FFFFFF";
        r.weight = 50;
        this.ranks.put(r.id, r);
        this.save();
        return r;
    }

    public void delete(String id) {
        if (!"default".equals(id)) {
            this.ranks.remove(id);
            this.assigned.values().removeIf(id::equals);
            this.save();

            for (Player p : Bukkit.getOnlinePlayers()) {
                this.apply(p);
            }
        }
    }

    public void changed() {
        this.save();

        for (Player p : Bukkit.getOnlinePlayers()) {
            this.apply(p);
        }
    }

    public Map<UUID, String> assignments() {
        return Collections.unmodifiableMap(this.assigned);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vortex.admin")) {
            sender.sendMessage(Text.mm("<red>You don't have permission."));
            return true;
        } else {
            String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "set": {
                        if (args.length < 3 || this.get(args[2]) == null) {
                            sender.sendMessage(Text.mm("<red>Usage: /rank set <player> <" + String.join("|", this.ranks.keySet()) + ">"));
                            return true;
                        }

                        OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                        this.assign(t.getUniqueId(), args[2]);
                        sender.sendMessage(
                            Text.mm("<green>" + Text.esc(args[1]) + " <gray>is now ")
                                .append(this.get(args[2]).prefix().append(Text.mm(this.get(args[2]).isDefault() ? "<gray>a default player" : "")))
                        );
                        break;
                }
                case "remove": {
                        if (args.length < 2) {
                            sender.sendMessage(Text.mm("<red>Usage: /rank remove <player>"));
                            return true;
                        }

                        OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                        this.assign(t.getUniqueId(), null);
                        sender.sendMessage(Text.mm("<gray>" + Text.esc(args[1]) + " has the default rank again."));
                        break;
                }
                case "info": {
                        if (args.length < 2) {
                            return true;
                        }

                        OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                        Ranks.Rank r = this.of(t.getUniqueId());
                        sender.sendMessage(Text.mm("<gray>" + Text.esc(args[1]) + ": ").append(r.prefix()));
                        sender.sendMessage(Text.mm("<gray>Permissions: <white>" + Text.esc(String.join(", ", this.permissionsOf(r)))));
                        break;
                }
                case "perm":
                case "perms": {
                        if (args.length >= 4 && this.get(args[1]) != null) {
                            Ranks.Rank r = this.get(args[1]);
                            String node = args[3];
                            String how = args[2].toLowerCase(Locale.ROOT);
                            this.access
                                .set(
                                    r,
                                    node,
                                    how.equals("add") || how.equals("give") ? Boolean.TRUE : (!how.equals("deny") && !how.equals("take") ? null : Boolean.FALSE)
                                );
                            this.changed();
                            RankAccess.Node n = this.access.find(node);
                            sender.sendMessage(
                                Text.mm(
                                    "<green>"
                                        + Text.esc(r.id)
                                        + " <gray>"
                                        + (
                                            how.equals("add") || how.equals("give")
                                                ? "now gives"
                                                : (!how.equals("deny") && !how.equals("take") ? "no longer lists" : "takes away")
                                        )
                                        + " <white>"
                                        + Text.esc(node)
                                        + (n != null && !n.description().isBlank() ? " <dark_gray>- <gray>" + Text.esc(n.description()) : "")
                                )
                            );
                            break;
                        }

                        if (args.length >= 2 && this.get(args[1]) != null) {
                            sender.sendMessage(Text.mm("<gray>" + args[1] + " gives: <white>" + Text.esc(String.join(", ", this.permissionsOf(this.get(args[1]))))));
                        } else {
                            sender.sendMessage(
                                Text.mm("<red>Usage: /rank perm <rank> add|deny|remove <permission> <gray>(* = everything, plugin.* = all of a plugin)")
                            );
                        }

                        return true;
                }
                case "members":
                case "users": {
                        Ranks.Rank r = args.length >= 2 ? this.get(args[1]) : null;
                        if (r == null) {
                            sender.sendMessage(Text.mm("<red>Usage: /rank members <rank>"));
                            return true;
                        }

                        List<Ranks.Member> ms = this.members(r);
                        sender.sendMessage(Text.mm("<gold><b>" + Text.esc(r.id) + "</b> <gray>(" + ms.size() + " players)"));

                        for (Ranks.Member m : ms) {
                            sender.sendMessage(
                                Text.mm(
                                    " " + (m.online() ? "<green>●" : "<dark_gray>●") + " <white>" + Text.esc(m.name()) + " <dark_gray>(" + Text.esc(m.how()) + ")"
                                )
                            );
                        }
                        break;
                }
                case "access":
                case "can": {
                        Ranks.Rank r = args.length >= 2 ? this.get(args[1]) : null;
                        if (r == null) {
                            sender.sendMessage(Text.mm("<red>Usage: /rank access <rank>"));
                            return true;
                        }

                        RankAccess.Access a = this.access.access(r);
                        sender.sendMessage(Text.mm("<gold><b>What " + Text.esc(r.id) + " players can do</b> <gray>(" + a.allCommands() + " commands in total)"));
                        List<String> cmds = new ArrayList<>();
                        a.extraCommands().forEach(c -> cmds.add("/" + c.name()));
                        sender.sendMessage(
                            Text.mm(
                                "<gray>Commands only this rank has (" + cmds.size() + "): <white>" + Text.esc(cmds.isEmpty() ? "none" : String.join(", ", cmds))
                            )
                        );

                        for (RankAccess.Node n : a.extraPermissions()) {
                            sender.sendMessage(
                                Text.mm(
                                    " <green>✔ <white>"
                                        + Text.esc(n.node())
                                        + (n.description().isBlank() ? "" : " <dark_gray>- <gray>" + Text.esc(n.description()))
                                )
                            );
                        }
                        break;
                }
                case "edit": {
                        if (sender instanceof Player p) {
                            this.plugin.dialogs().admin().ranks(p);
                        }
                        break;
                }
                default: {
                        sender.sendMessage(
                            Text.mm(
                                "<gold><b>Ranks</b> <gray>/rank edit <dark_gray>(menu)</dark_gray>, /rank set <player> <rank>, /rank perm <rank> add|deny|remove <permission>, /rank members <rank>, /rank access <rank>"
                            )
                        );

                        for (Ranks.Rank r : this.all()) {
                            sender.sendMessage(
                                Text.mm(" <dark_gray>» <white>" + r.id + " <dark_gray>(" + r.weight + ") ")
                                    .append(r.isDefault() ? Text.mm("<gray>default") : r.prefix())
                            );
                        }
                }
            }

            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("vortex.admin")) {
            return out;
        } else {
            if (args.length == 1) {
                out.addAll(List.of("set", "remove", "info", "list", "edit", "perm", "members", "access"));
            } else if (args.length == 2 && List.of("perm", "perms", "members", "users", "access", "can").contains(args[0].toLowerCase(Locale.ROOT))) {
                out.addAll(this.ranks.keySet());
            } else if (args.length == 3 && args[0].equalsIgnoreCase("perm")) {
                out.addAll(List.of("add", "deny", "remove"));
            } else {
                if (args.length == 4 && args[0].equalsIgnoreCase("perm")) {
                    String typed = args[3].toLowerCase(Locale.ROOT);
                    Ranks.Rank r = this.get(args[1]);
                    if (args[2].equalsIgnoreCase("remove") && r != null) {
                        r.permissions.forEach(s -> out.add(s.startsWith("-") ? s.substring(1) : s));
                    } else {
                        out.add("*");
                        Set<String> roots = new LinkedHashSet<>();

                        for (RankAccess.Node n : this.access.catalog()) {
                            if (n.node().toLowerCase(Locale.ROOT).startsWith(typed)) {
                                out.add(n.node());
                            }

                            int dot = n.node().indexOf(46);
                            if (dot > 0) {
                                roots.add(n.node().substring(0, dot) + ".*");
                            }
                        }

                        out.addAll(roots);
                    }

                    out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(typed));
                    return out.size() > 200 ? out.subList(0, 200) : out;
                }

                if (args.length == 2) {
                    Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                    out.addAll(this.ranks.keySet());
                }
            }

            String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
            return out;
        }
    }

    public record Member(UUID uuid, String name, boolean online, String how) {
    }

    public static final class Rank {
        public String id;
        public String label;
        public String from;
        public String to;
        public String icon;
        public String nameColor;
        public int weight;
        public boolean bold = true;
        public List<String> permissions = new ArrayList<>();
        public List<String> perks = new ArrayList<>();
        public String description = "";
        public String price = "";
        public String inherit = "";
        public boolean staff;

        public Component prefix() {
            return this.prefix(null);
        }

        public Component prefix(Player viewer) {
            if (this.label != null && !this.label.isBlank()) {
                Component ic = this.icon(viewer);
                return ic.append(
                        Text.mm(
                            (this.bold ? "<b>" : "")
                                + "<gradient:"
                                + this.from
                                + ":"
                                + this.to
                                + ">"
                                + Text.esc(this.label)
                                + "</gradient>"
                                + (this.bold ? "</b>" : "")
                        )
                    )
                    .append((Component)Component.space());
            } else {
                return Component.empty();
            }
        }

        private Component icon(Player viewer) {
            if (this.icon == null || this.icon.isBlank()) {
                return Component.empty();
            } else if (!this.icon.startsWith("glyph:")) {
                return Icons.spriteOr(this.icon, "").append((Component)Component.space());
            } else {
                VortexRealm pl = VortexRealm.get();
                boolean pack = viewer != null ? pl.hasPack(viewer) : Cfg.bool(Opt.PACK_REQUIRED) && pl.packs() != null && pl.packs().ready();
                String var5 = this.icon.substring(6);

                Component g = (Component)(switch (var5) {
                    case "play" -> pack ? Glyphs.play() : Text.mm("<#FF1A1A>▶");
                    case "verified" -> Icons.spriteOr("gui:icon/checkmark", "<#3DDC84>✔");
                    default -> Component.empty();
                });
                return g.append((Component)Component.space());
            }
        }

        public Component name(String player) {
            return Text.mm("<" + this.nameColor + ">" + Text.esc(player));
        }

        public boolean isDefault() {
            return this.label == null || this.label.isBlank();
        }
    }
}
