package net.practicepvp.social;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;

public final class RankAccess {
    public static final String OWN = "PracticePvP";
    private static final long CACHE_MS = 30000L;
    private final Ranks ranks;
    private List<RankAccess.Node> cache = List.of();
    private Map<String, RankAccess.Node> byNode = Map.of();
    private List<RankAccess.Cmd> commands = List.of();
    private long cachedAt;

    RankAccess(Ranks ranks) {
        this.ranks = ranks;
    }

    public synchronized List<RankAccess.Node> catalog() {
        if (System.currentTimeMillis() - this.cachedAt > 30000L || this.cache.isEmpty()) {
            this.build();
        }

        return this.cache;
    }

    public RankAccess.Node find(String node) {
        this.catalog();
        return node == null ? null : this.byNode.get(node.toLowerCase(Locale.ROOT));
    }

    public List<String> groups() {
        List<String> out = new ArrayList<>();

        for (RankAccess.Node n : this.catalog()) {
            if (!out.contains(n.group())) {
                out.add(n.group());
            }
        }

        return out;
    }

    public List<RankAccess.Node> group(String group) {
        List<RankAccess.Node> out = new ArrayList<>();

        for (RankAccess.Node n : this.catalog()) {
            if (n.group().equals(group)) {
                out.add(n);
            }
        }

        return out;
    }

    public synchronized List<RankAccess.Cmd> commands() {
        this.catalog();
        return this.commands;
    }

    private void build() {
        Map<String, RankAccess.Node> out = new LinkedHashMap<>();
        this.own(out);

        for (Plugin pl : Bukkit.getPluginManager().getPlugins()) {
            try {
                for (Permission perm : pl.getPluginMeta().getPermissions()) {
                    put(out, perm.getName(), perm.getDescription(), pl.getName(), perm.getDefault());
                }
            } catch (RuntimeException var16) {
            }
        }

        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        Map<Command, String> owner = new IdentityHashMap<>();

        for (Entry<String, Command> e : known.entrySet()) {
            int c = e.getKey().indexOf(58);
            if (c > 0) {
                owner.putIfAbsent(e.getValue(), pretty(e.getKey().substring(0, c)));
            }
        }

        List<RankAccess.Cmd> cmds = new ArrayList<>();
        Map<Command, Boolean> seen = new IdentityHashMap<>();

        for (Entry<String, Command> ex : known.entrySet()) {
            Command cmd = ex.getValue();
            if (!ex.getKey().contains(":") && seen.put(cmd, true) == null) {
                String grp = owner.getOrDefault(cmd, cmd instanceof PluginIdentifiableCommand pic ? pic.getPlugin().getName() : "Minecraft");
                String perm = cmd.getPermission();
                cmds.add(
                    new RankAccess.Cmd(
                        cmd.getName(), cmd.getDescription() == null ? "" : cmd.getDescription(), perm != null && !perm.isBlank() ? perm : null, grp
                    )
                );
                if (perm != null && !perm.isBlank()) {
                    for (String p : perm.split(";")) {
                        Permission reg = Bukkit.getPluginManager().getPermission(p.trim());
                        put(
                            out,
                            p.trim(),
                            "Use /" + cmd.getName() + (cmd.getDescription() != null && !cmd.getDescription().isBlank() ? " - " + cmd.getDescription() : ""),
                            grp,
                            reg == null ? Permission.DEFAULT_PERMISSION : reg.getDefault()
                        );
                    }
                }
            }
        }

        cmds.sort(Comparator.comparing(RankAccess.Cmd::name));

        for (Permission perm : Bukkit.getPluginManager().getPermissions()) {
            put(out, perm.getName(), perm.getDescription(), groupOf(perm.getName()), perm.getDefault());
        }

        List<RankAccess.Node> list = new ArrayList<>(out.values());
        list.sort(
            Comparator.<RankAccess.Node>comparingInt(nx -> rank(nx.group()))
                .thenComparing(RankAccess.Node::group, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RankAccess.Node::node)
        );
        Map<String, RankAccess.Node> idx = new HashMap<>();

        for (RankAccess.Node n : list) {
            idx.put(n.node().toLowerCase(Locale.ROOT), n);
        }

        this.cache = List.copyOf(list);
        this.byNode = idx;
        this.commands = List.copyOf(cmds);
        this.cachedAt = System.currentTimeMillis();
    }

    private static int rank(String group) {
        return switch (group) {
            case "PracticePvP" -> 0;
            case "Minecraft" -> 2;
            case "Bukkit", "Paper" -> 3;
            default -> 1;
        };
    }

    private void own(Map<String, RankAccess.Node> out) {
        PermissionDefault op = PermissionDefault.OP;
        put(out, "practice.admin", "Everything admin: /pvpadmin, kits, arenas, settings, web admin, building", "PracticePvP", op);
        put(out, "practice.staff", "Staff: staff chat, spectate any match, reports, hacker pool, /ss, see all tabs", "PracticePvP", op);
        put(out, "practice.staff.mod", "Moderator tools: end matches, send players to the lobby", "PracticePvP", op);
        put(out, "practice.chat.bypass", "No chat cooldown, anti-spam or chat mute", "PracticePvP", op);
        put(out, "practice.teleport.bypass", "No teleport warmup or cooldowns", "PracticePvP", op);
        put(out, "practice.lobbyfly", "Fly in the PvP lobby", "PracticePvP", op);
        put(out, "practice.cosmetics", "Kill effects and projectile trails", "PracticePvP", PermissionDefault.TRUE);
        put(out, "practice.warps.admin", "Create and delete warps", "PracticePvP", op);
        put(out, "practice.claims.admin", "Claim admin tools, edit any claim", "PracticePvP", op);
        put(out, "practice.ah.unlimited", "Unlimited auction house listings", "PracticePvP", op);
        put(out, "practice.homes.unlimited", "No home limit", "PracticePvP", op);

        for (int n : new int[]{2, 3, 4, 5, 6, 8, 10, 15, 20}) {
            put(out, "practice.homes." + n, "Up to " + n + " homes", "PracticePvP", op);
        }

        for (Ranks.Rank r : this.ranks.all()) {
            if (!r.isDefault()) {
                put(out, "practice.rank." + r.id, "Have the " + r.label + " rank (for LuckPerms groups)", "PracticePvP", op);
                put(out, "vortex.rank." + r.id, "Marks " + r.label + " players (given by the rank, for other plugins)", "PracticePvP", op);
            }
        }
    }

    private static void put(Map<String, RankAccess.Node> out, String node, String desc, String group, PermissionDefault def) {
        if (node != null && !node.isBlank()) {
            String key = node.trim();
            RankAccess.Node old = out.get(key);
            if (old == null) {
                out.put(key, new RankAccess.Node(key, desc == null ? "" : desc.trim(), group, def == null ? PermissionDefault.OP : def));
            } else if (old.description().isBlank() && desc != null && !desc.isBlank()) {
                out.put(key, new RankAccess.Node(key, desc.trim(), old.group(), old.def()));
            }
        }
    }

    private static String groupOf(String node) {
        String n = node.toLowerCase(Locale.ROOT);
        if (n.startsWith("minecraft.")) {
            return "Minecraft";
        } else if (n.startsWith("bukkit.")) {
            return "Bukkit";
        } else if (n.startsWith("paper.") || n.startsWith("spigot.")) {
            return "Paper";
        } else if (!n.startsWith("practice.") && !n.startsWith("vortex.")) {
            int dot = n.indexOf(46);
            return pretty(dot > 0 ? n.substring(0, dot) : n);
        } else {
            return "PracticePvP";
        }
    }

    private static String pretty(String ns) {
        for (Plugin pl : Bukkit.getPluginManager().getPlugins()) {
            if (pl.getName().equalsIgnoreCase(ns)) {
                return pl.getName();
            }
        }

        if (ns.equalsIgnoreCase("minecraft")) {
            return "Minecraft";
        } else if (ns.equalsIgnoreCase("bukkit")) {
            return "Bukkit";
        } else if (!ns.equalsIgnoreCase("paper") && !ns.equalsIgnoreCase("spigot")) {
            return ns.isEmpty() ? ns : Character.toUpperCase(ns.charAt(0)) + ns.substring(1);
        } else {
            return "Paper";
        }
    }

    public Map<String, Boolean> expand(List<String> list) {
        List<String> wild = new ArrayList<>();
        List<String> exact = new ArrayList<>();

        for (String raw : list) {
            String s = raw == null ? "" : raw.trim();
            if (!s.isEmpty() && !s.equals("-")) {
                (s.endsWith("*") ? wild : exact).add(s);
            }
        }

        wild.sort(Comparator.comparingInt(String::length));
        Map<String, Boolean> out = new LinkedHashMap<>();
        if (!wild.isEmpty()) {
            List<RankAccess.Node> all = this.catalog();

            for (String w : wild) {
                boolean value = !w.startsWith("-");
                String node = value ? w : w.substring(1);
                String prefix = node.substring(0, node.length() - 1).toLowerCase(Locale.ROOT);

                for (RankAccess.Node n : all) {
                    if (n.node().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.put(n.node(), value);
                    }
                }

                out.put(node, value);
            }
        }

        for (String s : exact) {
            boolean value = !s.startsWith("-");
            out.put(value ? s : s.substring(1), value);
        }

        return out;
    }

    public Map<String, Boolean> effective(Ranks.Rank r) {
        Map<String, Boolean> set = this.expand(this.ranks.permissionsOf(r));
        Map<String, Boolean> out = new LinkedHashMap<>();

        for (Entry<String, Boolean> e : set.entrySet()) {
            children(out, e.getKey(), e.getValue(), 0);
        }

        out.putAll(set);
        return out;
    }

    private static void children(Map<String, Boolean> out, String node, boolean value, int depth) {
        out.put(node, value);
        if (depth <= 8) {
            Permission perm = Bukkit.getPluginManager().getPermission(node);
            if (perm != null) {
                for (Entry<String, Boolean> c : perm.getChildren().entrySet()) {
                    children(out, c.getKey(), c.getValue() == value, depth + 1);
                }
            }
        }
    }

    public boolean allows(Map<String, Boolean> eff, String node) {
        Boolean v = eff.get(node);
        if (v != null) {
            return v;
        } else {
            Permission perm = Bukkit.getPluginManager().getPermission(node);
            PermissionDefault def = perm == null ? Permission.DEFAULT_PERMISSION : perm.getDefault();
            return def.getValue(false);
        }
    }

    public boolean canUse(Map<String, Boolean> eff, RankAccess.Cmd c) {
        if (c.permission() == null) {
            return true;
        } else {
            for (String p : c.permission().split(";")) {
                if (this.allows(eff, p.trim())) {
                    return true;
                }
            }

            return false;
        }
    }

    public RankAccess.Access access(Ranks.Rank r) {
        Map<String, Boolean> eff = this.effective(r);
        Ranks.Rank base = this.ranks.byDefault();
        Map<String, Boolean> baseEff = base != null && base != r ? this.effective(base) : Map.of();
        List<RankAccess.Cmd> extra = new ArrayList<>();
        int all = 0;

        for (RankAccess.Cmd c : this.commands()) {
            if (this.canUse(eff, c)) {
                all++;
                if (!this.canUse(baseEff, c)) {
                    extra.add(c);
                }
            }
        }

        List<RankAccess.Node> perms = new ArrayList<>();
        List<String> unknown = new ArrayList<>();

        for (Entry<String, Boolean> e : eff.entrySet()) {
            if (e.getValue() && !this.allows(baseEff, e.getKey())) {
                RankAccess.Node n = this.find(e.getKey());
                if (n != null) {
                    perms.add(n);
                } else if (!e.getKey().endsWith("*")) {
                    unknown.add(e.getKey());
                }
            }
        }

        perms.sort(Comparator.<RankAccess.Node>comparingInt(nx -> rank(nx.group())).thenComparing(RankAccess.Node::group).thenComparing(RankAccess.Node::node));
        return new RankAccess.Access(extra, all, perms, unknown);
    }

    public Map<String, Boolean> gives(Ranks.Rank r) {
        return this.expand(this.ranks.permissionsOf(r));
    }

    public String source(Ranks.Rank r, String node) {
        if (Boolean.TRUE.equals(this.expand(r.permissions).get(node))) {
            return "";
        } else {
            Set<String> seen = new HashSet<>();

            for (Ranks.Rank cur = this.ranks.get(r.inherit); cur != null && seen.add(cur.id); cur = this.ranks.get(cur.inherit)) {
                if (Boolean.TRUE.equals(this.expand(cur.permissions).get(node)) || node.equals("vortex.rank." + cur.id)) {
                    return cur.id;
                }
            }

            return "";
        }
    }

    public void set(Ranks.Rank r, String node, Boolean value) {
        r.permissions.remove(node);
        r.permissions.remove("-" + node);
        if (Boolean.TRUE.equals(value)) {
            r.permissions.add(node);
        } else if (Boolean.FALSE.equals(value) && Boolean.TRUE.equals(this.gives(r).get(node))) {
            r.permissions.add("-" + node);
        }
    }

    public record Access(List<RankAccess.Cmd> extraCommands, int allCommands, List<RankAccess.Node> extraPermissions, List<String> unknownPermissions) {
    }

    public record Cmd(String name, String description, String permission, String group) {
    }

    public record Node(String node, String description, String group, PermissionDefault def) {
    }
}
