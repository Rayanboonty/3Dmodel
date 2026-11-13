package net.vortexpvp.plus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.api.MatchEndEvent;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Clans: a name, a tag shown in chat and tab, an owner, officers and members, clan chat, clan rating that moves with
 * the members' ranked results, clan wars (first to 3 duel wins between the two clans within 30 minutes) and a clan
 * leaderboard. Stored in clans.yml (shared through MySQL on a network).
 */
public final class Clans implements Listener {
    private final VortexPvP plugin;
    private final PlusModule plus;
    /** invited player -> clan id */
    private final Map<UUID, String> invites = new HashMap<>();
    /** challenged clan -> challenging clan */
    private final Map<String, String> warRequests = new HashMap<>();
    private final Map<String, War> wars = new HashMap<>();

    private static final class War {
        final String a;
        final String b;
        int sa;
        int sb;
        final long until = System.currentTimeMillis() + 1800000L;

        War(String a, String b) {
            this.a = a;
            this.b = b;
        }
    }

    public Clans(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
    }

    private YamlConfiguration doc() {
        return this.plus.store().doc("clans");
    }

    private void save() {
        this.plus.store().dirty("clans");
    }

    public String clanOf(UUID u) {
        String c = this.plus.store().get(u).clan;
        return c != null && !c.isEmpty() && this.doc().contains("clans." + c) ? c : null;
    }

    public String tag(String clan) {
        return this.doc().getString("clans." + clan + ".tag", "?");
    }

    public String name(String clan) {
        return this.doc().getString("clans." + clan + ".name", clan);
    }

    public String color(String clan) {
        return this.doc().getString("clans." + clan + ".color", "#55C8FF");
    }

    public int elo(String clan) {
        return this.doc().getInt("clans." + clan + ".elo", 1000);
    }

    public List<String> members(String clan) {
        return this.doc().getStringList("clans." + clan + ".members");
    }

    public String role(String clan, UUID u) {
        if (u.toString().equals(this.doc().getString("clans." + clan + ".owner"))) {
            return "owner";
        } else {
            return this.doc().getStringList("clans." + clan + ".officers").contains(u.toString()) ? "officer" : "member";
        }
    }

    public Component badge(UUID u) {
        String c = this.clanOf(u);
        return c == null ? Component.empty() : Text.mm(" <" + this.color(c) + ">[" + Text.esc(this.tag(c)) + "]");
    }

    public boolean command(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }

        String sub = a.length == 0 ? "info" : a[0].toLowerCase(Locale.ROOT);
        String mine = this.clanOf(p.getUniqueId());
        switch (sub) {
            case "create" -> {
                if (a.length < 3) {
                    this.err(p, "Usage: /clan create <name> <tag>");
                } else if (mine != null) {
                    this.err(p, "Leave your clan first.");
                } else if (!a[1].matches("[A-Za-z0-9_]{3,16}") || !a[2].matches("[A-Za-z0-9]{2,5}")) {
                    this.err(p, "Name: 3-16 letters/numbers, tag: 2-5 letters/numbers.");
                } else if (this.doc().contains("clans." + a[1].toLowerCase(Locale.ROOT)) || this.byTag(a[2]) != null) {
                    this.err(p, "That name or tag is taken.");
                } else {
                    String id = a[1].toLowerCase(Locale.ROOT);
                    String b = "clans." + id + ".";
                    this.doc().set(b + "name", a[1]);
                    this.doc().set(b + "tag", a[2].toUpperCase(Locale.ROOT));
                    this.doc().set(b + "owner", p.getUniqueId().toString());
                    this.doc().set(b + "members", new ArrayList<>(List.of(p.getUniqueId().toString())));
                    this.doc().set(b + "elo", 1000);
                    this.doc().set(b + "created", System.currentTimeMillis());
                    this.setClan(p.getUniqueId(), id);
                    this.save();
                    Bukkit.broadcast(Text.mm("<aqua>⚑ <white>" + Text.esc(p.getName()) + "</white> <gray>founded the clan <aqua>" + Text.esc(a[1]) + " [" + Text.esc(a[2].toUpperCase(Locale.ROOT)) + "]"));
                }
            }
            case "invite" -> {
                if (mine == null || this.role(mine, p.getUniqueId()).equals("member")) {
                    this.err(p, "Only clan owners and officers can invite.");
                } else if (a.length < 2 || Bukkit.getPlayerExact(a[1]) == null) {
                    this.err(p, "That player isn't online.");
                } else if (this.members(mine).size() >= this.plus.config().i("clans.max-members", 20)) {
                    this.err(p, "Your clan is full.");
                } else {
                    Player t = Bukkit.getPlayerExact(a[1]);
                    this.invites.put(t.getUniqueId(), mine);
                    p.sendMessage(Text.mm("<green>Invited " + Text.esc(t.getName()) + "."));
                    t.sendMessage(Text.mm("<aqua>⚑ " + Text.esc(p.getName()) + " invited you to <white>" + Text.esc(this.name(mine))
                        + "</white> <click:run_command:'/clan accept'><green>[ACCEPT]</green></click> <click:run_command:'/clan deny'><red>[DENY]</red></click>"));
                }
            }
            case "accept" -> {
                String c = this.invites.remove(p.getUniqueId());
                if (c == null || !this.doc().contains("clans." + c)) {
                    this.err(p, "You have no clan invite.");
                } else if (mine != null) {
                    this.err(p, "Leave your clan first.");
                } else {
                    List<String> m = new ArrayList<>(this.members(c));
                    m.add(p.getUniqueId().toString());
                    this.doc().set("clans." + c + ".members", m);
                    this.setClan(p.getUniqueId(), c);
                    this.save();
                    this.clanSay(c, "<green>" + Text.esc(p.getName()) + " joined the clan!");
                }
            }
            case "deny" -> {
                this.invites.remove(p.getUniqueId());
                p.sendMessage(Text.mm("<gray>Invite declined."));
            }
            case "leave" -> {
                if (mine == null) {
                    this.err(p, "You're not in a clan.");
                } else if (this.role(mine, p.getUniqueId()).equals("owner")) {
                    this.err(p, "Owners use /clan disband (or promote someone with /clan owner <player>).");
                } else {
                    this.removeMember(mine, p.getUniqueId());
                    this.clanSay(mine, "<red>" + Text.esc(p.getName()) + " left the clan.");
                    p.sendMessage(Text.mm("<gray>You left the clan."));
                }
            }
            case "kick" -> {
                UUID t = a.length < 2 ? null : this.plus.store().uuidOf(a[1]);
                if (mine == null || this.role(mine, p.getUniqueId()).equals("member")) {
                    this.err(p, "Only clan owners and officers can kick.");
                } else if (t == null || !this.members(mine).contains(t.toString()) || this.role(mine, t).equals("owner")) {
                    this.err(p, "They're not a member you can kick.");
                } else {
                    this.removeMember(mine, t);
                    this.clanSay(mine, "<red>" + Text.esc(a[1]) + " was kicked by " + Text.esc(p.getName()) + ".");
                }
            }
            case "promote", "demote", "owner" -> {
                UUID t = a.length < 2 ? null : this.plus.store().uuidOf(a[1]);
                if (mine == null || !this.role(mine, p.getUniqueId()).equals("owner")) {
                    this.err(p, "Only the clan owner can do that.");
                } else if (t == null || !this.members(mine).contains(t.toString())) {
                    this.err(p, "They're not in your clan.");
                } else {
                    List<String> off = new ArrayList<>(this.doc().getStringList("clans." + mine + ".officers"));
                    if (sub.equals("promote")) {
                        if (!off.contains(t.toString())) {
                            off.add(t.toString());
                        }
                    } else if (sub.equals("demote")) {
                        off.remove(t.toString());
                    } else {
                        off.remove(t.toString());
                        off.add(p.getUniqueId().toString());
                        this.doc().set("clans." + mine + ".owner", t.toString());
                    }

                    this.doc().set("clans." + mine + ".officers", off);
                    this.save();
                    this.clanSay(mine, "<yellow>" + Text.esc(a[1]) + " is now " + (sub.equals("owner") ? "the owner" : (sub.equals("promote") ? "an officer" : "a member")) + ".");
                }
            }
            case "disband" -> {
                if (mine == null || !this.role(mine, p.getUniqueId()).equals("owner")) {
                    this.err(p, "Only the clan owner can disband it.");
                } else {
                    String name = this.name(mine);
                    for (String m : this.members(mine)) {
                        this.setClan(UUID.fromString(m), "");
                    }

                    this.doc().set("clans." + mine, null);
                    this.save();
                    Bukkit.broadcast(Text.mm("<gray>The clan <white>" + Text.esc(name) + "</white> was disbanded."));
                }
            }
            case "color" -> {
                if (mine == null || !this.role(mine, p.getUniqueId()).equals("owner") || a.length < 2 || !a[1].matches("#?[0-9A-Fa-f]{6}")) {
                    this.err(p, "Usage (owner): /clan color #RRGGBB");
                } else {
                    this.doc().set("clans." + mine + ".color", a[1].startsWith("#") ? a[1] : "#" + a[1]);
                    this.save();
                    p.sendMessage(Text.mm("<green>Clan colour set."));
                }
            }
            case "war" -> this.war(p, mine, a);
            case "chat", "c" -> this.chat(p, String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)));
            case "top", "list" -> this.top(p);
            default -> this.info(p, a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : (mine == null ? null : mine));
        }

        return true;
    }

    private String byTag(String tag) {
        ConfigurationSection s = this.doc().getConfigurationSection("clans");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                if (this.tag(id).equalsIgnoreCase(tag)) {
                    return id;
                }
            }
        }

        return null;
    }

    private void setClan(UUID u, String clan) {
        PlayerData d = this.plus.store().get(u);
        d.clan = clan;
        d.dirty = true;
        if (!clan.isEmpty()) {
            this.plus.progression().track(u, "clan_joined", null, 1L);
        }
    }

    private void removeMember(String clan, UUID u) {
        List<String> m = new ArrayList<>(this.members(clan));
        m.remove(u.toString());
        List<String> off = new ArrayList<>(this.doc().getStringList("clans." + clan + ".officers"));
        off.remove(u.toString());
        this.doc().set("clans." + clan + ".members", m);
        this.doc().set("clans." + clan + ".officers", off);
        this.setClan(u, "");
        this.save();
    }

    private void err(Player p, String s) {
        p.sendMessage(Text.mm("<red>" + s));
    }

    public void chat(Player p, String msg) {
        String c = this.clanOf(p.getUniqueId());
        if (c == null) {
            this.err(p, "You're not in a clan.");
        } else if (!msg.isBlank()) {
            this.clanSay(c, "<" + this.color(c) + ">[Clan]</" + this.color(c) + "> <white>" + Text.esc(p.getName()) + "<dark_gray>: <gray>" + Text.esc(msg));
        }
    }

    private void clanSay(String clan, String mini) {
        for (String m : this.members(clan)) {
            Player p = Bukkit.getPlayer(UUID.fromString(m));
            if (p != null) {
                p.sendMessage(Text.mm(mini));
            }
        }
    }

    private void info(Player p, String clan) {
        if (clan == null || !this.doc().contains("clans." + clan)) {
            p.sendMessage(Text.mm("<aqua>Clans <gray>- /clan create <name> <tag>, invite, accept, leave, kick, promote, demote, owner, disband, color, war, chat (/cc), top"));
            return;
        }

        String b = "clans." + clan + ".";
        p.sendMessage(Text.mm("<" + this.color(clan) + "><b>" + Text.esc(this.name(clan)) + " [" + Text.esc(this.tag(clan)) + "]</b> <gray>rating <white>" + this.elo(clan)
            + " <gray>W/L <green>" + this.doc().getInt(b + "wins") + "</green>/<red>" + this.doc().getInt(b + "losses") + "</red> <gray>wars won <white>" + this.doc().getInt(b + "wars")));
        StringBuilder sb = new StringBuilder();
        for (String m : this.members(clan)) {
            UUID u = UUID.fromString(m);
            String role = this.role(clan, u);
            sb.append(sb.length() == 0 ? "" : "<dark_gray>, ").append(Bukkit.getPlayer(u) != null ? "<green>" : "<gray>")
                .append(role.equals("owner") ? "♛" : (role.equals("officer") ? "★" : "")).append(Text.esc(this.plus.store().get(u).name));
        }

        p.sendMessage(Text.mm("<gray>Members: " + sb));
    }

    public void top(Player p) {
        ConfigurationSection s = this.doc().getConfigurationSection("clans");
        List<String> ids = s == null ? new ArrayList<>() : new ArrayList<>(s.getKeys(false));
        ids.sort((x, y) -> this.elo(y) - this.elo(x));
        Gui.open(this.plugin, p, "<dark_gray>Clan Leaderboard", 4, g -> {
            int[] slots = g.inner();
            for (int i = 0; i < slots.length && i < ids.size(); i++) {
                String c = ids.get(i);
                g.item(slots[i], Items.of(i == 0 ? Material.GOLDEN_HELMET : Material.WHITE_BANNER).name("<" + this.color(c) + ">#" + (i + 1) + " " + Text.esc(this.name(c)) + " [" + Text.esc(this.tag(c)) + "]")
                    .lore("<gray>Rating: <white>" + this.elo(c), "<gray>Members: <white>" + this.members(c).size(), "<gray>Wars won: <white>" + this.doc().getInt("clans." + c + ".wars")).build());
            }
        });
    }

    private void war(Player p, String mine, String[] a) {
        if (mine == null || this.role(mine, p.getUniqueId()).equals("member")) {
            this.err(p, "Only clan owners and officers can start wars.");
            return;
        }

        if (a.length >= 2 && a[1].equalsIgnoreCase("accept")) {
            String from = this.warRequests.remove(mine);
            if (from == null || !this.doc().contains("clans." + from)) {
                this.err(p, "No war request.");
            } else {
                this.wars.put(mine, new War(from, mine));
                this.wars.put(from, this.wars.get(mine));
                String msg = "<red>⚔ CLAN WAR: <white>" + Text.esc(this.name(from)) + "</white> vs <white>" + Text.esc(this.name(mine))
                    + "</white> <gray>- first to 3 duel wins between members (30 minutes). Duel each other!";
                this.clanSay(from, msg);
                this.clanSay(mine, msg);
            }
        } else if (a.length >= 2) {
            String target = this.doc().contains("clans." + a[1].toLowerCase(Locale.ROOT)) ? a[1].toLowerCase(Locale.ROOT) : this.byTag(a[1]);
            if (target == null || target.equals(mine)) {
                this.err(p, "Unknown clan.");
            } else if (this.wars.containsKey(mine) || this.wars.containsKey(target)) {
                this.err(p, "One of the clans is already at war.");
            } else {
                this.warRequests.put(target, mine);
                this.clanSay(mine, "<yellow>War request sent to " + Text.esc(this.name(target)) + ".");
                this.clanSay(target, "<red>⚔ " + Text.esc(this.name(mine)) + " declared war! <gray>Officers: <click:run_command:'/clan war accept'><green>[ACCEPT]</green></click>");
            }
        } else {
            this.err(p, "Usage: /clan war <clan|tag> or /clan war accept");
        }
    }

    @EventHandler
    public void onEnd(MatchEndEvent e) {
        if (e.winnerId() == null || e.loserId() == null) {
            return;
        }

        String wc = this.clanOf(e.winnerId());
        String lc = this.clanOf(e.loserId());
        if (e.match().ranked && e.outcomes() != null) {
            int delta = Math.max(1, Math.abs(e.outcomes()[0].delta()) / 4);
            if (wc != null && !wc.equals(lc)) {
                this.doc().set("clans." + wc + ".elo", this.elo(wc) + delta);
                this.doc().set("clans." + wc + ".wins", this.doc().getInt("clans." + wc + ".wins") + 1);
            }

            if (lc != null && !lc.equals(wc)) {
                this.doc().set("clans." + lc + ".elo", Math.max(0, this.elo(lc) - delta));
                this.doc().set("clans." + lc + ".losses", this.doc().getInt("clans." + lc + ".losses") + 1);
            }

            this.save();
        }

        War w = wc == null ? null : this.wars.get(wc);
        if (w != null && lc != null && (w.a.equals(wc) && w.b.equals(lc) || w.b.equals(wc) && w.a.equals(lc))) {
            if (System.currentTimeMillis() > w.until) {
                this.wars.remove(w.a);
                this.wars.remove(w.b);
                return;
            }

            if (w.a.equals(wc)) {
                w.sa++;
            } else {
                w.sb++;
            }

            String score = Text.esc(this.name(w.a)) + " <white>" + w.sa + " - " + w.sb + "</white> " + Text.esc(this.name(w.b));
            this.clanSay(w.a, "<red>⚔ War: " + score);
            this.clanSay(w.b, "<red>⚔ War: " + score);
            if (w.sa >= 3 || w.sb >= 3) {
                String win = w.sa >= 3 ? w.a : w.b;
                String lose = win.equals(w.a) ? w.b : w.a;
                this.doc().set("clans." + win + ".elo", this.elo(win) + 25);
                this.doc().set("clans." + lose + ".elo", Math.max(0, this.elo(lose) - 25));
                this.doc().set("clans." + win + ".wars", this.doc().getInt("clans." + win + ".wars") + 1);
                this.save();
                this.wars.remove(w.a);
                this.wars.remove(w.b);
                Bukkit.broadcast(Text.mm("<gold>⚔ <white>" + Text.esc(this.name(win)) + "</white> <gray>won the clan war against <white>" + Text.esc(this.name(lose)) + "</white>!"));
                for (String m : this.members(win)) {
                    this.plus.progression().track(UUID.fromString(m), "clan_war_win", null, 1L);
                }

                this.plus.discord().post("⚔ Clan **" + this.name(win) + "** won a war against **" + this.name(lose) + "**!");
            }
        }
    }

    public List<String> ids() {
        ConfigurationSection s = this.doc().getConfigurationSection("clans");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }
}
