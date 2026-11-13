package net.vortexpvp.plus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.event.EventManager;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The weekly rhythm: an automatic tournament (day, hour and a rotating kit), the champion getting a title and
 * shards, the player-made "kit of the week" vote (the winning kit becomes a featured unranked queue), and the
 * match of the week (the best replay of the last 7 days, featured on the website and with /motw).
 */
public final class Weekly {
    public static final String KOTW_ID = "kotw";
    private final VortexPvP plugin;
    private final PlusModule plus;

    public Weekly(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
    }

    private YamlConfiguration doc() {
        return this.plus.store().doc("weekly");
    }

    public static int week() {
        LocalDate d = LocalDate.now();
        return d.get(IsoFields.WEEK_BASED_YEAR) * 100 + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /** Every minute. */
    public void minute() {
        YamlConfiguration y = this.doc();
        int week = week();
        if (y.getInt("week", 0) != week) {
            int old = y.getInt("week", 0);
            y.set("week", week);
            this.plus.store().dirty("weekly");
            if (old != 0) {
                this.finishKitVote(old);
                this.pickMotw();
            }
        }

        this.autoTournament();
        this.checkChampion();
    }

    // ------------------------------------------------------------------ tournaments

    private List<Kit> rotation() {
        List<String> ids = this.plus.config().list("weekly.tournament.kits", List.of());
        List<Kit> out = new ArrayList<>();
        for (String id : ids) {
            Kit k = this.plugin.kits().get(id);
            if (k != null && k.enabled) {
                out.add(k);
            }
        }

        if (out.isEmpty()) {
            out.addAll(this.plus.ladder().rankedKits());
        }

        return out;
    }

    private void autoTournament() {
        if (!this.plus.config().on("weekly.tournament") || this.plugin.events() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String day = this.plus.config().s("weekly.tournament.day", "SATURDAY");
        int hour = this.plus.config().i("weekly.tournament.hour", 18);
        YamlConfiguration y = this.doc();
        if (!now.getDayOfWeek().name().equalsIgnoreCase(day) || now.getHour() != hour || now.getMinute() > 10 || y.getInt("tour-week", 0) == week()) {
            return;
        }

        if (Bukkit.getOnlinePlayers().size() < this.plus.config().i("weekly.tournament.min-online", 4)) {
            return;
        }

        this.host(Bukkit.getConsoleSender());
    }

    public boolean host(CommandSender by) {
        List<Kit> kits = this.rotation();
        if (kits.isEmpty() || this.plugin.events() == null) {
            by.sendMessage(Text.mm("<red>No kit for the tournament."));
            return false;
        }

        YamlConfiguration y = this.doc();
        int n = y.getInt("tour-count", 0) + 1;
        Kit k = kits.get(Math.floorMod(week(), kits.size()));
        EventManager.Event ev = new EventManager.Event();
        ev.name = "Weekly Cup #" + n;
        ev.kit = k;
        ev.host = "Vortex Realm";
        ev.prize = this.plus.config().i("weekly.tournament.shards", 500) + " shards + Weekly Champion title";
        ev.joinSeconds = this.plus.config().i("weekly.tournament.join-seconds", 120);
        if (!this.plugin.events().host(ev)) {
            by.sendMessage(Text.mm("<red>Another tournament is running."));
            return false;
        }

        y.set("tour-week", week());
        y.set("tour-count", n);
        y.set("tour-name", ev.name);
        y.set("tour-at", System.currentTimeMillis());
        y.set("tour-done", false);
        this.plus.store().dirty("weekly");
        this.plus.discord().post("🏆 **" + ev.name + "** (" + Text.plain(Text.mm(k.displayName)) + ") has started - join in game with /event join!");
        by.sendMessage(Text.mm("<green>Hosted " + ev.name + "."));
        return true;
    }

    private void checkChampion() {
        YamlConfiguration y = this.doc();
        String name = y.getString("tour-name");
        if (name == null || y.getBoolean("tour-done", true) || this.plugin.events() == null) {
            return;
        }

        for (EventManager.Past p : this.plugin.events().history()) {
            if (p.name().equals(name) && p.at() >= y.getLong("tour-at") - 60000L && p.winner() != null && !p.winner().isBlank()) {
                y.set("tour-done", true);
                List<String> champs = new ArrayList<>(y.getStringList("champions"));
                champs.add(0, name + "|" + p.winner() + "|" + p.kit() + "|" + p.at());
                y.set("champions", champs.subList(0, Math.min(50, champs.size())));
                this.plus.store().dirty("weekly");
                UUID u = this.plus.store().uuidOf(p.winner());
                if (u == null) {
                    Player online = Bukkit.getPlayerExact(p.winner());
                    u = online == null ? null : online.getUniqueId();
                }

                if (u != null) {
                    this.plus.progression().title(u, "Weekly Champion");
                    this.plus.progression().shards(u, this.plus.config().i("weekly.tournament.shards", 500));
                    this.plus.progression().track(u, "tournament_win", null, 1L);
                    this.plus.progression().xp(u, 500L, "tournament win");
                }

                Bukkit.broadcast(Text.mm("<gradient:#FFD700:#FF6B00><b>🏆 " + Text.esc(name) + " CHAMPION:</b></gradient> <white>" + Text.esc(p.winner())));
                this.plus.discord().post("🏆 **" + p.winner() + "** won **" + name + "**!");
                return;
            }
        }
    }

    public void champions(CommandSender s) {
        List<String> c = this.doc().getStringList("champions");
        s.sendMessage(Text.mm("<gold><b>Weekly champions"));
        if (c.isEmpty()) {
            s.sendMessage(Text.mm("<gray>No weekly cup played yet. It runs every " + Progression.nice(this.plus.config().s("weekly.tournament.day", "SATURDAY").toLowerCase(Locale.ROOT))
                + " at " + this.plus.config().i("weekly.tournament.hour", 18) + ":00."));
        }

        for (int i = 0; i < Math.min(10, c.size()); i++) {
            String[] p = c.get(i).split("\\|");
            s.sendMessage(Text.mm("<yellow>" + Text.esc(p[0]) + " <dark_gray>- <white>" + Text.esc(p[1]) + " <gray>(" + Text.esc(p.length > 2 ? p[2] : "?") + ")"));
        }
    }

    // ------------------------------------------------------------------ kit of the week

    private String base() {
        return "kitvote." + week();
    }

    public void kitvote(Player p, String[] a) {
        if (!this.plus.config().on("kitvote")) {
            p.sendMessage(Text.mm("<red>The kit vote is switched off."));
            return;
        }

        if (a.length >= 1 && a[0].equalsIgnoreCase("submit")) {
            if (a.length < 2) {
                p.sendMessage(Text.mm("<red>/kitvote submit <name> <gray>- submits the items in your inventory as a kit"));
                return;
            }

            String name = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
            if (name.length() > 24 || !name.matches("[A-Za-z0-9 _'!-]+")) {
                p.sendMessage(Text.mm("<red>Use a short name (letters, numbers, spaces)."));
                return;
            }

            ItemStack[] inv = p.getInventory().getContents();
            int weapons = 0;
            List<String> items = new ArrayList<>();
            for (int i = 0; i < Math.min(41, inv.length); i++) {
                ItemStack it = inv[i];
                if (it != null && it.getType() != Material.AIR) {
                    String t = it.getType().name();
                    if (t.endsWith("_SWORD") || t.endsWith("_AXE") || t.equals("BOW") || t.equals("CROSSBOW") || t.equals("MACE") || t.equals("TRIDENT") || t.endsWith("_CHESTPLATE")) {
                        weapons++;
                    }

                    if (it.getItemMeta() != null && it.getItemMeta().getPersistentDataContainer().getKeys().stream().anyMatch(k -> k.getNamespace().equals("vortexpvp"))) {
                        continue;
                    }

                    items.add(i + "=" + Base64.getEncoder().encodeToString(it.serializeAsBytes()));
                }
            }

            if (weapons == 0) {
                p.sendMessage(Text.mm("<red>Put the kit in your inventory first (at least a weapon or armour). Tip: do it in creative or with /kiteditor items."));
                return;
            }

            YamlConfiguration y = this.doc();
            String key = this.base() + ".subs." + p.getUniqueId();
            y.set(key + ".name", name);
            y.set(key + ".owner", p.getName());
            y.set(key + ".items", items);
            this.plus.store().dirty("weekly");
            p.sendMessage(Text.mm("<green>Your kit <white>" + Text.esc(name) + "</white> is in this week's vote! <gray>The winner becomes a featured queue next week."));
            return;
        }

        this.voteMenu(p);
    }

    public void voteMenu(Player p) {
        YamlConfiguration y = this.doc();
        ConfigurationSection subs = y.getConfigurationSection(this.base() + ".subs");
        String myVote = y.getString(this.base() + ".votes." + p.getUniqueId());
        Map<String, Integer> counts = this.counts(week());
        Gui.open(this.plugin, p, "<dark_gray>Kit of the Week - vote", 6, g -> {
            int[] slots = g.inner();
            int i = 0;
            if (subs != null) {
                for (String owner : subs.getKeys(false)) {
                    if (i >= slots.length) {
                        break;
                    }

                    ConfigurationSection s = subs.getConfigurationSection(owner);
                    boolean mine = owner.equals(myVote);
                    g.item(slots[i++], Items.of(this.icon(s)).name("<yellow>" + Text.esc(s.getString("name", "?")))
                        .lore("<gray>By <white>" + Text.esc(s.getString("owner", "?")), "<gray>Votes: <gold>" + counts.getOrDefault(owner, 0), "",
                            mine ? "<green>✔ Your vote" : "<green>▶ Left-click to vote", "<aqua>▶ Right-click to see the items")
                        .glint(mine).hideAll().build(), c -> {
                            if (c.isRightClick()) {
                                this.preview(p, s);
                                return;
                            }

                            if (owner.equals(p.getUniqueId().toString())) {
                                p.sendMessage(Text.mm("<red>You can't vote for your own kit."));
                                return;
                            }

                            y.set(this.base() + ".votes." + p.getUniqueId(), owner);
                            this.plus.store().dirty("weekly");
                            Snd.click(p);
                            this.voteMenu(p);
                        });
                }
            }

            Kit cur = this.plugin.kits().get(KOTW_ID);
            g.item(49, Items.of(Material.NETHER_STAR).name("<gold>Kit of the Week")
                .lore(cur == null ? "<gray>No winner yet" : "<gray>This week: <white>" + Text.plain(Text.mm(cur.displayName)), "",
                    "<gray>Submit yours: <white>/kitvote submit <name>", "<gray>(the items in your inventory)")
                .build());
        });
    }

    private Material icon(ConfigurationSection s) {
        for (ItemStack it : this.items(s)) {
            if (it != null && (it.getType().name().endsWith("_SWORD") || it.getType().name().endsWith("_AXE") || it.getType() == Material.MACE || it.getType() == Material.BOW)) {
                return it.getType();
            }
        }

        return Material.CHEST;
    }

    private ItemStack[] items(ConfigurationSection s) {
        ItemStack[] out = new ItemStack[41];
        for (String e : s.getStringList("items")) {
            int i = e.indexOf('=');
            try {
                int slot = Integer.parseInt(e.substring(0, i));
                if (slot >= 0 && slot < 41) {
                    out[slot] = ItemStack.deserializeBytes(Base64.getDecoder().decode(e.substring(i + 1)));
                }
            } catch (RuntimeException var6) {
            }
        }

        return out;
    }

    private void preview(Player p, ConfigurationSection s) {
        ItemStack[] it = this.items(s);
        Gui.open(this.plugin, p, "<dark_gray>" + Text.esc(s.getString("name", "Kit")), 6, g -> {
            for (int i = 0; i < 36; i++) {
                if (it[i] != null) {
                    g.item(i < 9 ? 45 + i : i, it[i]);
                }
            }

            for (int i = 36; i < 41; i++) {
                if (it[i] != null) {
                    g.item(i - 36, it[i]);
                }
            }

            g.item(8, Items.of(Material.ARROW).name("<yellow>« Back").build(), c -> this.voteMenu(p));
        });
    }

    private Map<String, Integer> counts(int week) {
        Map<String, Integer> counts = new HashMap<>();
        ConfigurationSection votes = this.doc().getConfigurationSection("kitvote." + week + ".votes");
        if (votes != null) {
            for (String voter : votes.getKeys(false)) {
                counts.merge(votes.getString(voter), 1, Integer::sum);
            }
        }

        return counts;
    }

    /** At the start of a new week: the most voted kit of last week becomes the featured "kotw" kit. */
    public void finishKitVote(int week) {
        YamlConfiguration y = this.doc();
        ConfigurationSection subs = y.getConfigurationSection("kitvote." + week + ".subs");
        if (subs == null || subs.getKeys(false).isEmpty()) {
            return;
        }

        Map<String, Integer> counts = this.counts(week);
        String best = null;
        int top = -1;
        for (String owner : subs.getKeys(false)) {
            int c = counts.getOrDefault(owner, 0);
            if (c > top) {
                top = c;
                best = owner;
            }
        }

        if (best == null) {
            return;
        }

        ConfigurationSection s = subs.getConfigurationSection(best);
        Kit k = this.plugin.kits().get(KOTW_ID);
        if (k == null) {
            k = this.plugin.kits().create(KOTW_ID, "<gold>" + Text.esc(s.getString("name", "Kit of the Week")), null);
        }

        k.displayName = "<gold>" + Text.esc(s.getString("name", "Kit of the Week"));
        k.contents = this.items(s);
        k.enabled = true;
        k.ranked = false;
        k.unranked = true;
        k.version++;
        this.plugin.kits().save();
        y.set("kotw-owner", s.getString("owner"));
        y.set("kitvote." + (week - 2), null);
        this.plus.store().dirty("weekly");
        try {
            UUID u = UUID.fromString(best);
            this.plus.progression().title(u, "Kit Designer");
            this.plus.progression().shards(u, 200L);
        } catch (IllegalArgumentException var10) {
        }

        Bukkit.broadcast(Text.mm("<gold>⭐ Kit of the Week: <white>" + Text.esc(s.getString("name")) + " <gray>by " + Text.esc(s.getString("owner", "?")) + " (" + top
            + " votes) is now a featured unranked queue!"));
    }

    // ------------------------------------------------------------------ match of the week

    public String pickMotw() {
        if (this.plus.replays() == null) {
            return null;
        }

        String best = null;
        int top = -1;
        long since = System.currentTimeMillis() - 7L * 86400000L;
        for (Map.Entry<String, ConfigurationSection> en : this.plus.replays().all().entrySet()) {
            ConfigurationSection s = en.getValue();
            if (s.getLong("at") >= since && s.getInt("score") > top) {
                top = s.getInt("score");
                best = en.getKey();
            }
        }

        if (best != null) {
            this.doc().set("motw", best);
            this.plus.store().dirty("weekly");
            this.plus.replays().keep(best, true);
            ConfigurationSection s = this.plus.replays().info(best);
            Bukkit.broadcast(Text.mm("<aqua>🎬 Match of the Week: <white>" + Text.esc(String.join(" vs ", s.getStringList("players"))) + " <gray>(" + s.getString("kit") + ") "
                + "<click:run_command:'/replay " + best + "'><yellow>[watch]</yellow></click>"));
        }

        return best;
    }

    public String motw() {
        return this.doc().getString("motw");
    }

    public void motwCommand(Player p, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("pick") && p.hasPermission("vortexpvp.admin")) {
            if (this.pickMotw() == null) {
                p.sendMessage(Text.mm("<gray>No replays this week yet."));
            }

            return;
        }

        String id = this.motw();
        if (id == null || this.plus.replays().info(id) == null) {
            p.sendMessage(Text.mm("<gray>No Match of the Week yet - it is picked every Monday from the best replay of the week."));
            return;
        }

        ConfigurationSection s = this.plus.replays().info(id);
        p.sendMessage(Text.mm("<aqua>🎬 Match of the Week: <white>" + Text.esc(String.join(" vs ", s.getStringList("players"))) + " <gray>(" + s.getString("kit") + ", "
            + s.getInt("secs") + "s, best combo " + s.getInt("combo") + ") <click:run_command:'/replay " + id + "'><yellow>[watch]</yellow></click>"));
    }

    public String selfCheck() {
        return week() > 202000 ? null : "week number";
    }
}
