package net.practicepvp.realm;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.practicepvp.PracticePlugin;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 3.14.0 - the Unstable Realm season: an original, story-driven chaos season on the SMP.
 *
 * <ul>
 *   <li>{@link Characters}: eight one-of-a-kind characters (one owner each) with passives, actives and weaknesses.</li>
 *   <li>{@link Story}: five chapters, a personal questline per character, cutscenes, choices and server votes.</li>
 *   <li>{@link StoryNpcs}: story characters that remember what each player did.</li>
 *   <li>{@link Fighters}: AI fighters that hunt, taunt and take revenge.</li>
 *   <li>{@link RealmEvents}: rift nights, meteors, the carnival, sieges and the finale boss.</li>
 * </ul>
 *
 * Everything is off until an admin types {@code /realm start}: installing the plugin changes nothing on the SMP.
 * The story text lives in {@code realm-story.yml} (copied to the plugin folder, editable); progress in {@code realm.yml}.
 */
public final class Realm implements CommandExecutor, TabCompleter {
    /** Everything the season remembers about one player. */
    public static final class PlayerState {
        public String character;
        public int step;
        public int progress;
        public String pendingChoice;
        public final Map<String, Integer> rep = new HashMap<>();
        public final Map<String, String> flags = new HashMap<>();
        public final Set<String> memory = new HashSet<>();
        public final List<String> titles = new ArrayList<>();
        public int vendetta;
    }

    final PracticePlugin plugin;
    final File dataFile;
    final File storyFile;
    YamlConfiguration story;
    boolean active;
    boolean finished;
    int chapter = 1;
    Location capital;
    final Map<String, Integer> counters = new HashMap<>();
    final Map<String, String> globals = new HashMap<>();
    final Map<String, Map<UUID, String>> votes = new HashMap<>();
    final Map<String, Location> npcSpots = new LinkedHashMap<>();
    final Map<UUID, PlayerState> players = new HashMap<>();
    final NamespacedKey shardKey;
    final NamespacedKey riftKey;
    final NamespacedKey npcKey;
    final NamespacedKey ownerKey;
    private boolean dirty;

    Characters characters;
    Story storyEngine;
    StoryNpcs npcs;
    Fighters fighters;
    RealmEvents events;

    public Realm(PracticePlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "realm.yml");
        this.storyFile = new File(plugin.getDataFolder(), "realm-story.yml");
        this.shardKey = new NamespacedKey(plugin, "rift_shard");
        this.riftKey = new NamespacedKey(plugin, "rift_mob");
        this.npcKey = new NamespacedKey(plugin, "realm_npc");
        this.ownerKey = new NamespacedKey(plugin, "realm_owner");
        this.loadStory();
        this.load();
        this.characters = new Characters(this);
        this.storyEngine = new Story(this);
        this.npcs = new StoryNpcs(this);
        this.fighters = new Fighters(this);
        this.events = new RealmEvents(this);
        org.bukkit.plugin.PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(this.characters, plugin);
        pm.registerEvents(this.storyEngine, plugin);
        pm.registerEvents(this.npcs, plugin);
        pm.registerEvents(this.fighters, plugin);
        pm.registerEvents(this.events, plugin);
        Tasks.timer(20L, 20L, this::second);
        Tasks.timer(1200L, 1200L, () -> {
            if (this.dirty) {
                this.save();
            }
        });
    }

    // ------------------------------------------------------------------ data

    void loadStory() {
        if (!this.storyFile.exists()) {
            this.plugin.saveResource("realm-story.yml", false);
        }

        this.story = YamlConfiguration.loadConfiguration(this.storyFile);
        // story lines added in later versions appear even when the server keeps an older copy
        try (InputStreamReader r = new InputStreamReader(this.plugin.getResource("realm-story.yml"), StandardCharsets.UTF_8)) {
            this.story.setDefaults(YamlConfiguration.loadConfiguration(r));
        } catch (IOException | NullPointerException ignored) {
            // the bundled copy is always there; nothing to merge otherwise
        }
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.dataFile);
        this.active = y.getBoolean("active", false);
        this.finished = y.getBoolean("finished", false);
        this.chapter = Math.max(1, y.getInt("chapter", 1));
        this.capital = y.getLocation("capital");
        sec(y, "counters", (k, s) -> this.counters.put(k, s.getInt(k)));
        sec(y, "globals", (k, s) -> this.globals.put(k, s.getString(k)));
        sec(y, "npcs", (k, s) -> {
            Location l = s.getLocation(k);
            if (l != null) {
                this.npcSpots.put(k, l);
            }
        });
        sec(y, "votes", (k, s) -> {
            Map<UUID, String> m = new HashMap<>();
            ConfigurationSection v = s.getConfigurationSection(k);
            if (v != null) {
                for (String u : v.getKeys(false)) {
                    m.put(UUID.fromString(u), v.getString(u));
                }
            }

            this.votes.put(k, m);
        });
        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps != null) {
            for (String k : ps.getKeys(false)) {
                ConfigurationSection c = ps.getConfigurationSection(k);
                PlayerState st = new PlayerState();
                st.character = c.getString("character");
                st.step = c.getInt("step");
                st.progress = c.getInt("progress");
                st.pendingChoice = c.getString("pending-choice");
                st.vendetta = c.getInt("vendetta");
                sec(c, "rep", (a, s) -> st.rep.put(a, s.getInt(a)));
                sec(c, "flags", (a, s) -> st.flags.put(a, s.getString(a)));
                st.memory.addAll(c.getStringList("memory"));
                st.titles.addAll(c.getStringList("titles"));
                this.players.put(UUID.fromString(k), st);
            }
        }
    }

    private interface Each {
        void on(String key, ConfigurationSection sec);
    }

    private static void sec(ConfigurationSection y, String path, Each each) {
        ConfigurationSection s = y.getConfigurationSection(path);
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try {
                    each.on(k, s);
                } catch (RuntimeException ignored) {
                    // a broken line in realm.yml is skipped
                }
            }
        }
    }

    void markDirty() {
        this.dirty = true;
    }

    public void save() {
        this.dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Unstable Realm progress. Edit with /realm while the server runs, not here."));
        y.set("active", this.active);
        y.set("finished", this.finished);
        y.set("chapter", this.chapter);
        y.set("capital", this.capital);
        this.counters.forEach((k, v) -> y.set("counters." + k, v));
        this.globals.forEach((k, v) -> y.set("globals." + k, v));
        this.npcSpots.forEach((k, v) -> y.set("npcs." + k, v));
        this.votes.forEach((k, m) -> m.forEach((u, v) -> y.set("votes." + k + "." + u, v)));
        this.players.forEach((u, st) -> {
            String b = "players." + u + ".";
            y.set(b + "character", st.character);
            y.set(b + "step", st.step);
            y.set(b + "progress", st.progress);
            y.set(b + "pending-choice", st.pendingChoice);
            y.set(b + "vendetta", st.vendetta);
            st.rep.forEach((k, v) -> y.set(b + "rep." + k, v));
            st.flags.forEach((k, v) -> y.set(b + "flags." + k, v));
            y.set(b + "memory", new ArrayList<>(st.memory));
            y.set(b + "titles", st.titles);
        });
        try {
            y.save(this.dataFile);
        } catch (IOException e) {
            this.plugin.getLogger().warning("Could not save realm.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ helpers used by every part

    public PlayerState state(Player p) {
        return this.players.computeIfAbsent(p.getUniqueId(), u -> new PlayerState());
    }

    public PlayerState stateIfAny(UUID u) {
        return this.players.get(u);
    }

    /** The season is running and this world is the SMP (overworld, nether or end). */
    public boolean on(World w) {
        return this.active && w != null && this.plugin.modes() != null && this.plugin.modes().smpWorld(w);
    }

    public boolean on(Player p) {
        return this.on(p.getWorld());
    }

    /** The SMP overworld, where most of the story happens. */
    World overworld() {
        return this.plugin.modes() == null ? null : this.plugin.modes().smpWorld();
    }

    List<Player> smpPlayers() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.on(p)) {
                out.add(p);
            }
        }

        return out;
    }

    public int counter(String k) {
        return this.counters.getOrDefault(k, 0);
    }

    public void addCounter(String k, int n) {
        this.counters.merge(k, n, Integer::sum);
        this.markDirty();
        this.storyEngine.checkChapter();
    }

    public String global(String k) {
        return this.globals.getOrDefault(k, "");
    }

    void setGlobal(String k, String v) {
        this.globals.put(k, v);
        this.markDirty();
    }

    int rep(Player p, String npc) {
        return this.state(p).rep.getOrDefault(npc, 0);
    }

    static boolean admin(CommandSender s) {
        // in the SMP the lockdown switches admin permissions off, but ops stay ops
        return !(s instanceof Player) || s.isOp() || s.hasPermission("practice.admin");
    }

    /** A Rift Shard: the season's currency and quest item. */
    ItemStack shard(int n) {
        ItemStack it = new ItemStack(Material.AMETHYST_SHARD, Math.max(1, Math.min(64, n)));
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.mm("<!i><gradient:#B06BFF:#FF5FA2><b>Rift Shard"));
        m.lore(List.of(Text.mm("<!i><gray>A sliver of the broken Crown."), Text.mm("<!i><dark_purple>Trade it with Pip the Peddler.")));
        m.getPersistentDataContainer().set(this.shardKey, PersistentDataType.BYTE, (byte)1);
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    boolean isShard(ItemStack it) {
        return it != null && it.getType() == Material.AMETHYST_SHARD && it.hasItemMeta()
            && it.getItemMeta().getPersistentDataContainer().has(this.shardKey, PersistentDataType.BYTE);
    }

    int countShards(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (this.isShard(it)) {
                n += it.getAmount();
            }
        }

        return n;
    }

    boolean takeShards(Player p, int n) {
        if (this.countShards(p) < n) {
            return false;
        }

        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length && n > 0; i++) {
            if (this.isShard(inv[i])) {
                int take = Math.min(n, inv[i].getAmount());
                inv[i].setAmount(inv[i].getAmount() - take);
                n -= take;
            }
        }

        p.getInventory().setContents(inv);
        return true;
    }

    void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }

    /** Big title + chat line to everybody in the SMP (and a short chat line to the rest of the server). */
    void broadcast(String title, String subtitle, String chat, String sound) {
        Title t = Title.title(Text.mm(title), Text.mm(subtitle), Title.Times.times(java.time.Duration.ofMillis(500),
            java.time.Duration.ofSeconds(4), java.time.Duration.ofSeconds(1)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.on(p)) {
                p.showTitle(t);
                if (sound != null) {
                    Snd.play(p, sound, 1.0F, 1.0F);
                }
            }

            if (chat != null) {
                p.sendMessage(Text.mm(chat));
            }
        }
    }

    private void second() {
        if (!this.active) {
            return;
        }

        this.characters.second();
        this.storyEngine.second();
        this.npcs.second();
        this.fighters.second();
        this.events.second();
    }

    // ------------------------------------------------------------------ /realm

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (cmd.getName().equalsIgnoreCase("character")) {
            if (sender instanceof Player p) {
                this.characters.open(p);
            }

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("ability")) {
            if (sender instanceof Player p) {
                this.characters.use(p);
            }

            return true;
        }

        Player p = sender instanceof Player pl ? pl : null;
        switch (sub) {
            case "", "me" -> {
                if (p != null) {
                    this.storyEngine.card(p);
                }

                return true;
            }
            case "journal", "quest", "quests" -> {
                if (p != null) {
                    this.storyEngine.journal(p);
                }

                return true;
            }
            case "choose" -> {
                if (p != null && args.length > 1) {
                    this.storyEngine.choose(p, args[1]);
                }

                return true;
            }
            case "vote" -> {
                if (p != null) {
                    this.storyEngine.vote(p, args.length > 1 ? args[1] : null);
                }

                return true;
            }
            case "buy", "shop", "trade" -> {
                if (p != null) {
                    this.npcs.shop(p, args.length > 1 ? args[1] : null);
                }

                return true;
            }
            case "who", "characters" -> {
                this.characters.who(sender);
                return true;
            }
            case "replay" -> {
                if (p != null) {
                    this.storyEngine.replay(p);
                }

                return true;
            }
            default -> {
            }
        }

        if (!admin(sender)) {
            sender.sendMessage(Text.mm("<gray>/realm, /realm journal, /realm vote, /realm who, /realm replay, /character, /ability"));
            return true;
        }

        return this.adminCommand(sender, p, sub, args);
    }

    private boolean adminCommand(CommandSender sender, Player p, String sub, String[] args) {
        String a1 = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "start" -> {
                if (this.plugin.modes() == null || this.overworld() == null) {
                    sender.sendMessage(Text.mm("<red>No SMP world is set up."));
                    return true;
                }

                this.active = true;
                this.finished = false;
                this.save();
                this.storyEngine.seasonStart();
                sender.sendMessage(Text.mm("<green>The Unstable Realm has begun. <gray>Next: /realm capital, then /realm npc place <id> for each story character."));
            }
            case "stop", "pause" -> {
                this.active = false;
                this.save();
                this.fighters.clearAll();
                this.events.stopAll();
                sender.sendMessage(Text.mm("<yellow>The Unstable Realm is paused. Progress is kept; /realm start resumes it."));
            }
            case "status" -> {
                sender.sendMessage(Text.mm("<gold><b>Unstable Realm</b> <gray>" + (this.active ? "<green>running" : "<red>off") + " <gray>• chapter <white>" + this.chapter
                    + " <gray>• capital <white>" + (this.capital == null ? "not set" : this.capital.getBlockX() + ", " + this.capital.getBlockZ())));
                sender.sendMessage(Text.mm("<gray>Counters: <white>" + this.counters + " <gray>Globals: <white>" + this.globals));
                sender.sendMessage(Text.mm("<gray>NPCs placed: <white>" + this.npcSpots.keySet() + " <gray>of <white>" + this.npcs.ids()));
            }
            case "chapter" -> {
                if (a1.equals("next")) {
                    this.storyEngine.advance();
                } else {
                    try {
                        this.storyEngine.setChapter(Integer.parseInt(a1));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Text.mm("<red>/realm chapter next | <1-5>"));
                    }
                }
            }
            case "capital" -> {
                if (p == null) {
                    return true;
                }

                this.capital = p.getLocation().toBlockLocation();
                this.save();
                sender.sendMessage(Text.mm("<green>The capital is here. Sieges and the finale happen around it. <gray>/realm shrine builds the Rift Shrine here."));
            }
            case "shrine" -> {
                if (this.capital == null) {
                    sender.sendMessage(Text.mm("<red>Set the capital first: /realm capital"));
                } else {
                    Shrine.build(this.capital);
                    sender.sendMessage(Text.mm("<green>The Rift Shrine is built at the capital."));
                }
            }
            case "npc" -> this.npcs.admin(sender, p, args);
            case "event" -> this.events.force(sender, a1);
            case "release" -> {
                OfflinePlayer o = args.length > 1 ? Bukkit.getOfflinePlayerIfCached(args[1]) : null;
                PlayerState st = o == null ? null : this.players.get(o.getUniqueId());
                if (st == null || st.character == null) {
                    sender.sendMessage(Text.mm("<red>That player has no character."));
                } else {
                    sender.sendMessage(Text.mm("<yellow>" + Text.esc(args[1]) + " is no longer " + this.characters.name(st.character) + "."));
                    st.character = null;
                    st.step = 0;
                    st.progress = 0;
                    st.pendingChoice = null;
                    this.save();
                }
            }
            case "counter" -> {
                if (args.length > 2) {
                    try {
                        this.counters.put(args[1], Integer.parseInt(args[2]));
                        this.save();
                        this.storyEngine.checkChapter();
                        sender.sendMessage(Text.mm("<green>" + args[1] + " = " + args[2]));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Text.mm("<red>/realm counter <name> <number>"));
                    }
                }
            }
            case "reload" -> {
                this.loadStory();
                this.characters.reloadTexts();
                sender.sendMessage(Text.mm("<green>realm-story.yml reloaded."));
            }
            case "reset" -> {
                if (!a1.equals("confirm")) {
                    sender.sendMessage(Text.mm("<red>This wipes every character, quest and vote. <white>/realm reset confirm"));
                    return true;
                }

                this.players.clear();
                this.counters.clear();
                this.globals.clear();
                this.votes.clear();
                this.chapter = 1;
                this.finished = false;
                this.save();
                sender.sendMessage(Text.mm("<yellow>The Unstable Realm is reset (NPC spots and the capital are kept)."));
            }
            default -> {
                sender.sendMessage(Text.mm("<gold><b>/realm</b> <gray>start | stop | status | chapter next|<n> | capital | shrine | npc place|remove|list <id>"));
                sender.sendMessage(Text.mm("<gray>event riftnight|meteor|carnival|siege|finale|stop | release <player> | counter <name> <n> | reload | reset"));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] args) {
        List<String> out = new ArrayList<>();
        if (c.getName().equalsIgnoreCase("character") || c.getName().equalsIgnoreCase("ability")) {
            return out;
        }

        if (args.length == 1) {
            out.addAll(List.of("journal", "vote", "who", "replay", "buy"));
            if (admin(s)) {
                out.addAll(List.of("start", "stop", "status", "chapter", "capital", "shrine", "npc", "event", "release", "counter", "reload", "reset"));
            }
        } else if (args.length == 2 && admin(s)) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "event" -> out.addAll(List.of("riftnight", "meteor", "carnival", "siege", "finale", "stop"));
                case "chapter" -> out.addAll(List.of("next", "1", "2", "3", "4", "5"));
                case "npc" -> out.addAll(List.of("place", "remove", "list"));
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("npc")) {
            out.addAll(this.npcs.ids());
        }

        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    static Component c(String mm) {
        return Text.mm(mm);
    }
}
