package net.vortexpvp.extra;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * The swear filter and player mutes. Words are matched after undoing the usual tricks (f.u.c.k, f u c k, fuuuck, f4ck,
 * accents), with a whitelist so words like "class" or "assassin" are never hit. Swearing blocks the message and mutes the
 * player for 2, then 10, then 30 minutes (chat.filter.mute-steps); mutes live in the database and survive restarts.
 */
public final class ChatFilter implements Listener, TabExecutor {
    /** Bumped when the bundled list changes: new default words are added to chat-filter.yml, removed ones stay removed. */
    static final int LIST_VERSION = 2;
    /** Default words dropped from the list because they hit normal words (douche = shower, communiquer); removed on update. */
    static final List<String> RETIRED = List.of("douche", "niquer");
    /**
     * Bundled words. "=word" only matches the whole word (plus simple endings like -s, -er, -ing), for words that hide
     * inside normal ones (ass in "class", pute in "computer"); the rest also match inside a longer word.
     */
    static final List<String> DEFAULT_BLOCKED = List.of(
        // English
        "fuck", "fck", "fuk", "motherfucker", "shit", "bitch", "biatch", "cunt", "=dick", "dickhead", "pussy", "asshole", "=ass", "dumbass",
        "jackass", "bastard", "slut", "whore", "=fag", "faggot", "nigger", "nigga", "retard", "=cock", "cocksucker", "wanker", "twat",
        "=prick", "bollocks", "douchebag", "=kys", "killyourself", "=cum", "=tits", "dildo", "porn",
        // French
        "merde", "putain", "=pute", "salope", "connard", "connasse", "encule", "=nique", "=niquer", "=ntm", "=fdp", "batard", "salaud",
        "=pd", "=tg", "ferme ta gueule", "ta gueule", "=conne", "abruti", "=zebi", "=zeb", "=nik", "kahba", "sharmouta", "=tfou",
        // Spanish
        "=puta", "=puto", "mierda", "cabron", "pendejo", "joder", "gilipollas", "maricon", "=verga", "chinga", "=culero"
    );
    static final List<String> DEFAULT_WHITELIST = List.of(
        "class", "classic", "classes", "assassin", "assist", "assault", "assume", "pass", "passed", "grass", "bass", "mass", "glass",
        "compass", "embassy", "cocktail", "peacock", "hitchcock", "scunthorpe", "document", "cumulative", "title", "titan", "dickens",
        "computer", "compute", "dispute", "reputation", "amputate", "unique", "technique", "clinique", "shitake", "therapist", "skills",
        "assets", "asset", "bassist", "massive", "harass", "sassy", "classy"
    );
    static final Set<String> CHAT_COMMANDS = Set.of(
        "msg", "tell", "w", "whisper", "m", "t", "pm", "dm", "emsg", "etell", "ewhisper", "r", "reply", "er", "ereply", "pc", "partychat",
        "me", "say", "shout", "ac", "gc", "global", "local", "l", "g"
    );
    /** Commands whose first argument is a player name, not text. */
    static final Set<String> TARGETED = Set.of("msg", "tell", "w", "whisper", "m", "t", "pm", "dm", "emsg", "etell", "ewhisper");
    private final VortexPvP plugin;
    private final File file;
    private final Set<String> blocked = ConcurrentHashMap.newKeySet();
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();
    private final Set<String> removedDefaults = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ChatFilter.Mute> mutes = new ConcurrentHashMap<>();
    private final Deque<ChatFilter.Offence> recent = new ArrayDeque<>();

    public ChatFilter(VortexPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chat-filter.yml");
        this.load();
        this.loadMutes();
    }

    /** One mute: active while {@code until} is in the future. Offences count filter hits inside the reset window. */
    public record Mute(UUID uuid, String name, long until, String reason, String by, int offences, long lastOffence) {
        public boolean active() {
            return this.until > System.currentTimeMillis();
        }

        public long left() {
            return Math.max(0L, this.until - System.currentTimeMillis());
        }
    }

    public record Offence(long at, String name, String text, String where, long mutedFor) {
    }

    // ------------------------------------------------------------------ word list

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        boolean fresh = !this.file.exists();
        this.blocked.clear();
        this.whitelist.clear();
        this.removedDefaults.clear();
        if (fresh) {
            this.blocked.addAll(DEFAULT_BLOCKED);
            this.whitelist.addAll(DEFAULT_WHITELIST);
        } else {
            for (String w : y.getStringList("blocked")) {
                this.blocked.add(w.toLowerCase(Locale.ROOT).trim());
            }

            for (String w : y.getStringList("whitelist")) {
                this.whitelist.add(w.toLowerCase(Locale.ROOT).trim());
            }

            for (String w : y.getStringList("removed-defaults")) {
                this.removedDefaults.add(w.toLowerCase(Locale.ROOT).trim());
            }

            if (y.getInt("version", 0) < LIST_VERSION) {
                this.blocked.removeAll(RETIRED);
                // an update brought new default words: add them, but never the ones the admin removed
                for (String w : DEFAULT_BLOCKED) {
                    if (!this.removedDefaults.contains(w)) {
                        this.blocked.add(w);
                    }
                }

                this.whitelist.addAll(DEFAULT_WHITELIST);
            }
        }

        this.blocked.remove("");
        this.whitelist.remove("");
        if (fresh || y.getInt("version", 0) < LIST_VERSION) {
            this.save();
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
            "Swear filter words. Edit in-game: /pvpadmin -> Chat Moderation (or /chatfilter).",
            "A word starting with = only matches as a whole word (with endings like -s, -er, -ing).",
            "Other words also match inside longer words. The whitelist always wins."
        ));
        y.set("version", LIST_VERSION);
        y.set("blocked", this.blocked.stream().sorted().toList());
        y.set("whitelist", this.whitelist.stream().sorted().toList());
        y.set("removed-defaults", this.removedDefaults.stream().sorted().toList());

        try {
            y.save(this.file);
        } catch (IOException var3) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save chat-filter.yml", var3);
        }
    }

    public List<String> blocked() {
        return this.blocked.stream().sorted().toList();
    }

    public List<String> whitelist() {
        return this.whitelist.stream().sorted().toList();
    }

    public boolean addBlocked(String w) {
        w = w.toLowerCase(Locale.ROOT).trim();
        if (w.isEmpty() || w.equals("=")) {
            return false;
        } else {
            this.removedDefaults.remove(w);
            boolean added = this.blocked.add(w);
            this.save();
            return added;
        }
    }

    public boolean removeBlocked(String w) {
        w = w.toLowerCase(Locale.ROOT).trim();
        boolean removed = this.blocked.remove(w);
        if (removed && DEFAULT_BLOCKED.contains(w)) {
            this.removedDefaults.add(w);
        }

        this.save();
        return removed;
    }

    public boolean addWhitelist(String w) {
        w = w.toLowerCase(Locale.ROOT).trim();
        boolean added = !w.isEmpty() && this.whitelist.add(w);
        this.save();
        return added;
    }

    public boolean removeWhitelist(String w) {
        boolean removed = this.whitelist.remove(w.toLowerCase(Locale.ROOT).trim());
        this.save();
        return removed;
    }

    // ------------------------------------------------------------------ matching

    /** Lowercase letters only: accents dropped, leetspeak undone (sh1t -> shit, b!tch -> bitch, $lut -> slut). */
    static String letters(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder(n.length());

        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            char m = switch (c) {
                case '0' -> 'o';
                case '1', '!', '|' -> 'i';
                case '3' -> 'e';
                case '4', '@' -> 'a';
                case '5', '$' -> 's';
                case '7' -> 't';
                case '8' -> 'b';
                case '9' -> 'g';
                default -> c;
            };
            if (m >= 'a' && m <= 'z') {
                b.append(m);
            }
        }

        return b.toString();
    }

    private final Map<String, java.util.regex.Pattern> patterns = new ConcurrentHashMap<>();

    /**
     * A blocked term as a pattern where each letter may repeat: "fuck" -> f+u+c+k+, so "fuuuck" is caught but "niger"
     * never matches "nigger" (it needs both g's). Whole-word terms ("=ass") allow only simple endings.
     */
    private java.util.regex.Pattern pattern(String raw) {
        return this.patterns.computeIfAbsent(raw, r -> {
            boolean exact = r.startsWith("=");
            String term = letters(exact ? r.substring(1) : r);
            StringBuilder b = new StringBuilder();

            for (int i = 0; i < term.length(); i++) {
                if (i > 0 && term.charAt(i) == term.charAt(i - 1)) {
                    b.append(term.charAt(i));
                } else {
                    b.append(term.charAt(i)).append('+');
                }
            }

            return java.util.regex.Pattern.compile(exact ? "^" + b + "(s|es|er|ers|ed|ing|ez)?$" : b.toString());
        });
    }

    /** Does one word (letters only) hit a blocked term? The whitelist always wins. */
    boolean wordHits(String word) {
        if (word.isEmpty() || this.whitelist.contains(word)) {
            return false;
        } else {
            for (String raw : this.blocked) {
                if (!raw.contains(" ") && !letters(raw).isEmpty()) {
                    java.util.regex.Matcher m = this.pattern(raw).matcher(word);
                    if (raw.startsWith("=") ? m.matches() : m.find()) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    /** Multi-word terms ("ta gueule") are matched against the whole message with spaces normalised. */
    private boolean phraseHits(String text) {
        String flat = " " + String.join(" ", Arrays.stream(text.toLowerCase(Locale.ROOT).split("\\s+")).map(ChatFilter::letters).toList()) + " ";

        for (String raw : this.blocked) {
            if (raw.contains(" ")) {
                String phrase = " " + String.join(" ", Arrays.stream(raw.replace("=", "").split("\\s+")).map(ChatFilter::letters).toList()) + " ";
                if (flat.contains(phrase)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Which whitespace tokens of {@code text} are swearing. Single letters in a row ("f u c k") are joined first, and
     * separators inside a token (f.u.c.k, f_u-c*k) never count.
     */
    public boolean[] hits(String text) {
        String[] tok = text.split("\\s+");
        boolean[] hit = new boolean[tok.length];

        for (int i = 0; i < tok.length; i++) {
            hit[i] = this.wordHits(letters(tok[i]));
        }

        int i = 0;

        while (i < tok.length) {
            if (letters(tok[i]).length() == 1) {
                int j = i;
                StringBuilder joined = new StringBuilder();

                while (j < tok.length && letters(tok[j]).length() == 1) {
                    joined.append(letters(tok[j]));
                    j++;
                }

                if (j - i >= 3 && this.wordHits(joined.toString())) {
                    for (int k = i; k < j; k++) {
                        hit[k] = true;
                    }
                }

                i = j;
            } else {
                i++;
            }
        }

        return hit;
    }

    /** True when the text contains swearing. */
    public boolean swears(String text) {
        if (text == null || text.isBlank()) {
            return false;
        } else {
            for (boolean h : this.hits(text)) {
                if (h) {
                    return true;
                }
            }

            return this.phraseHits(text);
        }
    }

    /** The text with every swear token replaced by stars of the same length. */
    public String censor(String text) {
        String[] tok = text.split("(?<=\\s)|(?=\\s)");
        List<String> words = new ArrayList<>();

        for (String t : tok) {
            if (!t.isBlank()) {
                words.add(t);
            }
        }

        boolean[] hit = this.hits(String.join(" ", words));
        StringBuilder out = new StringBuilder();
        int w = 0;

        for (String t : tok) {
            if (t.isBlank()) {
                out.append(t);
            } else {
                out.append(w < hit.length && hit[w] ? "*".repeat(t.length()) : t);
                w++;
            }
        }

        return out.toString();
    }

    // ------------------------------------------------------------------ mutes

    private void loadMutes() {
        if (this.plugin.db() != null) {
            this.plugin.db().supply(c -> {
                try (PreparedStatement ps = c.prepareStatement("SELECT uuid, name, until, reason, muted_by, offences, last_offence FROM vp_mutes"); ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID u = UUID.fromString(rs.getString(1));
                        this.mutes.put(u, new ChatFilter.Mute(u, rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getLong(7)));
                    }
                }

                return null;
            }).exceptionally(err -> null);
        }
    }

    private void store(ChatFilter.Mute m) {
        this.mutes.put(m.uuid(), m);
        if (this.plugin.db() != null) {
            this.plugin.db().run(c -> {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM vp_mutes WHERE uuid=?")) {
                    del.setString(1, m.uuid().toString());
                    del.executeUpdate();
                }

                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO vp_mutes (uuid, name, until, reason, muted_by, offences, last_offence) VALUES (?,?,?,?,?,?,?)"
                )) {
                    ps.setString(1, m.uuid().toString());
                    ps.setString(2, m.name());
                    ps.setLong(3, m.until());
                    ps.setString(4, m.reason());
                    ps.setString(5, m.by());
                    ps.setInt(6, m.offences());
                    ps.setLong(7, m.lastOffence());
                    ps.executeUpdate();
                }
            }).exceptionally(err -> null);
        }
    }

    public ChatFilter.Mute muteOf(UUID u) {
        ChatFilter.Mute m = this.mutes.get(u);
        return m != null && m.active() ? m : null;
    }

    public boolean muted(Player p) {
        return this.muteOf(p.getUniqueId()) != null;
    }

    public List<ChatFilter.Mute> activeMutes() {
        return this.mutes.values().stream().filter(ChatFilter.Mute::active).sorted((a, b) -> Long.compare(a.until(), b.until())).toList();
    }

    public List<ChatFilter.Offence> recent() {
        synchronized (this.recent) {
            return List.copyOf(this.recent);
        }
    }

    public void mute(UUID u, String name, long ms, String reason, String by) {
        ChatFilter.Mute old = this.mutes.get(u);
        long until = ms <= 0L ? Long.MAX_VALUE / 2L : System.currentTimeMillis() + ms;
        this.store(new ChatFilter.Mute(u, name, until, reason, by, old == null ? 0 : old.offences(), old == null ? 0L : old.lastOffence()));
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            p.sendMessage(Text.mm("<red><b>You have been muted</b> <gray>for <white>" + (ms <= 0L ? "ever" : fmt(ms)) + "<gray>. Reason: <white>" + Text.esc(reason)));
        }
    }

    public boolean unmute(UUID u) {
        ChatFilter.Mute m = this.mutes.get(u);
        if (m != null && m.active()) {
            this.store(new ChatFilter.Mute(u, m.name(), 0L, m.reason(), m.by(), m.offences(), m.lastOffence()));
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.sendMessage(Text.mm("<green>You can chat again."));
            }

            return true;
        } else {
            return false;
        }
    }

    /** Mute steps in minutes from chat.filter.mute-steps ("2,10,30"); bad entries are skipped. */
    public static List<Integer> steps() {
        List<Integer> out = new ArrayList<>();

        for (String part : Cfg.s(Opt.CHAT_FILTER_STEPS).split("[,; ]+")) {
            try {
                int v = Integer.parseInt(part.trim());
                if (v > 0) {
                    out.add(v);
                }
            } catch (NumberFormatException var5) {
            }
        }

        return out.isEmpty() ? List.of(2, 10, 30) : out;
    }

    /** Records a filter hit: counts the offence and mutes for the next step. Returns the mute length (0 = no mute). */
    long offence(Player p, String text, String where) {
        long now = System.currentTimeMillis();
        ChatFilter.Mute old = this.mutes.get(p.getUniqueId());
        long window = Cfg.i(Opt.CHAT_FILTER_RESET) * 3600000L;
        int count = old == null || now - old.lastOffence() > window ? 1 : old.offences() + 1;
        long ms = 0L;
        if (Cfg.bool(Opt.CHAT_FILTER_MUTE)) {
            List<Integer> steps = steps();
            ms = steps.get(Math.min(count, steps.size()) - 1) * 60000L;
        }

        long until = ms > 0L ? Math.max(now + ms, old != null && old.active() ? old.until() : 0L) : (old == null ? 0L : old.until());
        this.store(new ChatFilter.Mute(p.getUniqueId(), p.getName(), until, "Swearing (" + count + ")", "Filter", count, now));
        synchronized (this.recent) {
            this.recent.addFirst(new ChatFilter.Offence(now, p.getName(), text, where, ms));

            while (this.recent.size() > 60) {
                this.recent.removeLast();
            }
        }

        long muted = ms;
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (muted > 0L) {
                p.sendMessage(Text.mm("<red><b>No swearing!</b> <gray>You are muted for <white>" + fmt(muted) + "<gray>. Next time it's longer."));
            } else {
                p.sendMessage(Text.mm("<red><b>No swearing!</b> <gray>Your message was not sent."));
            }

            if (Cfg.bool(Opt.CHAT_FILTER_NOTIFY)) {
                Component alert = Text.mm(
                    "<dark_red>[Filter] <white>" + Text.esc(p.getName()) + " <gray>(" + where + ")" + (muted > 0L ? " <red>muted " + fmt(muted) : "") + "<gray>: <white>" + Text.esc(text)
                );

                for (Player s : Bukkit.getOnlinePlayers()) {
                    if (s.hasPermission("vortexpvp.staff") && !s.equals(p)) {
                        s.sendMessage(alert);
                    }
                }
            }
        });
        return ms;
    }

    public static String fmt(long ms) {
        long s = (ms + 999L) / 1000L;
        if (s >= 86400L * 365L) {
            return "ever";
        } else if (s >= 3600L) {
            return s / 3600L + "h " + s % 3600L / 60L + "m";
        } else {
            return s >= 60L ? s / 60L + "m " + s % 60L + "s" : s + "s";
        }
    }

    private boolean filters(Player p) {
        return Cfg.bool(Opt.CHAT_FILTER) && !p.hasPermission("vortexpvp.chat.filter.bypass");
    }

    private void tellMuted(Player p, ChatFilter.Mute m) {
        p.sendActionBar(Text.mm("<red>You are muted <gray>(" + fmt(m.left()) + " left)"));
        p.sendMessage(Text.mm("<red>You are muted for <white>" + fmt(m.left()) + "<red>. <gray>Reason: <white>" + Text.esc(m.reason() == null ? "-" : m.reason())));
    }

    // ------------------------------------------------------------------ listeners

    /** Runs before every other chat handler (formatting, spectator chat), so a muted or swearing message never goes out. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        ChatFilter.Mute m = this.muteOf(p.getUniqueId());
        if (m != null) {
            e.setCancelled(true);
            this.tellMuted(p, m);
        } else if (this.filters(p)) {
            String plain = PlainTextComponentSerializer.plainText().serialize(e.message());
            if (this.swears(plain)) {
                if (Cfg.e(Opt.CHAT_FILTER_ACTION, Opt.FilterAction.class) == Opt.FilterAction.CENSOR) {
                    e.message(Component.text(this.censor(plain)));
                } else {
                    e.setCancelled(true);
                }

                this.offence(p, plain, "chat");
            }
        }
    }

    /** /msg, /r, party chat and the chat commands of other plugins. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String[] parts = e.getMessage().substring(1).split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        cmd = cmd.substring(cmd.indexOf(':') + 1);
        if (CHAT_COMMANDS.contains(cmd)) {
            Player p = e.getPlayer();
            ChatFilter.Mute m = this.muteOf(p.getUniqueId());
            if (m != null) {
                e.setCancelled(true);
                this.tellMuted(p, m);
            } else if (this.filters(p)) {
                int from = TARGETED.contains(cmd) ? 2 : 1;
                String text = parts.length > from ? String.join(" ", Arrays.copyOfRange(parts, from, parts.length)) : "";
                if (this.swears(text)) {
                    e.setCancelled(true);
                    this.offence(p, text, "/" + cmd);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) {
        Player p = e.getPlayer();
        if (this.filters(p)) {
            StringBuilder all = new StringBuilder();

            for (Component line : e.lines()) {
                all.append(PlainTextComponentSerializer.plainText().serialize(line)).append(' ');
            }

            if (this.swears(all.toString())) {
                for (int i = 0; i < e.lines().size(); i++) {
                    e.line(i, Component.text(this.censor(PlainTextComponentSerializer.plainText().serialize(e.line(i)))));
                }

                this.offence(p, all.toString().trim(), "sign");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent e) {
        String name = e.getView().getRenameText();
        if (name != null && e.getView().getPlayer() instanceof Player p && this.filters(p) && this.swears(name)) {
            e.setResult(null);
        }
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vortexpvp.staff")) {
            sender.sendMessage(Text.mm("<red>You don't have permission."));
            return true;
        } else {
            switch (command.getName().toLowerCase(Locale.ROOT)) {
                case "mute": {
                    if (args.length < 1) {
                        sender.sendMessage(Text.mm("<red>Usage: /mute <player> [time like 10m, 2h, 1d or perm] [reason]"));
                        return true;
                    }

                    OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(args[0]);
                    if (t == null) {
                        t = Bukkit.getPlayerExact(args[0]);
                    }

                    if (t == null) {
                        sender.sendMessage(Text.mm("<red>Unknown player."));
                        return true;
                    }

                    long ms = args.length >= 2 ? parseTime(args[1]) : 600000L;
                    int reasonFrom = args.length >= 2 && ms != -2L ? 2 : 1;
                    if (ms == -2L) {
                        ms = 600000L;
                    }

                    String reason = args.length > reasonFrom ? String.join(" ", Arrays.copyOfRange(args, reasonFrom, args.length)) : "Muted by staff";
                    String name = t.getName() == null ? args[0] : t.getName();
                    this.mute(t.getUniqueId(), name, ms, reason, sender.getName());
                    sender.sendMessage(Text.mm("<green>Muted <white>" + Text.esc(name) + "<green> for " + (ms <= 0L ? "ever" : fmt(ms)) + "."));
                    return true;
                }
                case "unmute": {
                    if (args.length < 1) {
                        sender.sendMessage(Text.mm("<red>Usage: /unmute <player>"));
                        return true;
                    }

                    UUID u = this.find(args[0]);
                    sender.sendMessage(u != null && this.unmute(u) ? Text.mm("<green>Unmuted " + Text.esc(args[0]) + ".") : Text.mm("<red>That player is not muted."));
                    return true;
                }
                case "mutes": {
                    List<ChatFilter.Mute> list = this.activeMutes();
                    sender.sendMessage(Text.mm("<gold><b>Muted players</b> <gray>(" + list.size() + ")"));

                    for (ChatFilter.Mute m : list) {
                        sender.sendMessage(Text.mm("<white>" + Text.esc(m.name()) + " <gray>- " + fmt(m.left()) + " left - " + Text.esc(m.reason()) + " <dark_gray>(" + Text.esc(m.by()) + ")"));
                    }

                    return true;
                }
                default:
                    if (sender instanceof Player p && this.plugin.dialogs() != null) {
                        this.plugin.dialogs().admin().chatModeration(p);
                    }

                    return true;
            }
        }
    }

    private UUID find(String name) {
        for (ChatFilter.Mute m : this.mutes.values()) {
            if (m.name() != null && m.name().equalsIgnoreCase(name)) {
                return m.uuid();
            }
        }

        OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(name);
        return t == null ? null : t.getUniqueId();
    }

    /** "10m", "2h", "1d", "30s", "perm" -> ms (0 = forever); -2 when it isn't a time at all (then it's the reason). */
    public static long parseTime(String s) {
        s = s.toLowerCase(Locale.ROOT);
        if (s.equals("perm") || s.equals("permanent") || s.equals("forever")) {
            return 0L;
        } else {
            try {
                long n = Long.parseLong(s.replaceAll("[a-z]+$", ""));
                return switch (s.replaceAll("^[0-9]+", "")) {
                    case "s" -> n * 1000L;
                    case "", "m", "min" -> n * 60000L;
                    case "h" -> n * 3600000L;
                    case "d" -> n * 86400000L;
                    default -> -2L;
                };
            } catch (NumberFormatException var3) {
                return -2L;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            Collection<String> names = command.getName().equalsIgnoreCase("unmute")
                ? this.activeMutes().stream().map(ChatFilter.Mute::name).toList()
                : Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return names.stream().filter(n -> n != null && n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        } else {
            return args.length == 2 && command.getName().equalsIgnoreCase("mute") ? List.of("2m", "10m", "30m", "1h", "1d", "perm") : List.of();
        }
    }

    /** For the self test: these must be caught / must pass. */
    public static List<String> selfTestSwears() {
        return List.of("fuck", "f.u.c.k", "f u c k", "fuuuuck", "FUCKING", "you are a b1tch", "sh1t", "putain", "fdp", "ta gueule");
    }

    public static List<String> selfTestClean() {
        return List.of(
            "nice class", "assassin build", "glass and grass", "computer", "unique technique", "pass the ball", "gg wp", "as you like", "je vais prendre une douche",
            "il faut communiquer", "scunthorpe"
        );
    }

    public Set<String> defaults() {
        return new LinkedHashSet<>(DEFAULT_BLOCKED);
    }
}
