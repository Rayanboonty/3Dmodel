package net.practicepvp.realm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The story: five chapters that advance when the server reaches a goal, a personal questline for each character,
 * cutscenes (timed dialogue, titles, sounds), choices with consequences, server votes that decide the branch, and
 * the ending. All the text is in realm-story.yml.
 *
 * <p>Line format: {@code [conditions] Speaker: text}. Conditions (comma separated, {@code !} negates):
 * {@code char=jester}, {@code flag:key=value}, {@code g:key=value}, {@code rep:npc>=n}, {@code rep:npc<n},
 * {@code mem:npc:key}. A line starting with {@code #} is a title ({@code #Title|Subtitle}), {@code !sound:name}
 * plays a sound, {@code ...} is a pause. Lines without a speaker are narration.
 */
public final class Story implements Listener {
    private final Realm realm;
    private final Map<UUID, Deque<Runnable>> queue = new HashMap<>();
    private final Map<UUID, Boolean> playing = new HashMap<>();
    private final Map<UUID, List<String>> last = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private int tick;

    Story(Realm realm) {
        this.realm = realm;
    }

    // ------------------------------------------------------------------ story data

    ConfigurationSection chapterSec(int n) {
        return this.realm.story.getConfigurationSection("chapters." + n);
    }

    String chapterName(int n) {
        ConfigurationSection c = this.chapterSec(n);
        return c == null ? "Chapter " + n : c.getString("name", "Chapter " + n);
    }

    int chapters() {
        ConfigurationSection c = this.realm.story.getConfigurationSection("chapters");
        return c == null ? 5 : c.getKeys(false).size();
    }

    /** The questline of a character: a list of steps. */
    List<Map<?, ?>> steps(String character) {
        return character == null ? List.of() : this.realm.story.getMapList("quests." + character);
    }

    Map<?, ?> step(Realm.PlayerState st) {
        List<Map<?, ?>> s = this.steps(st.character);
        return st.step >= 0 && st.step < s.size() ? s.get(st.step) : null;
    }

    static String str(Map<?, ?> m, String k, String def) {
        Object o = m == null ? null : m.get(k);
        return o == null ? def : String.valueOf(o);
    }

    static int num(Map<?, ?> m, String k, int def) {
        Object o = m == null ? null : m.get(k);
        if (o instanceof Number n) {
            return n.intValue();
        }

        try {
            return o == null ? def : Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @SuppressWarnings("unchecked")
    static List<String> lines(Map<?, ?> m, String k) {
        Object o = m == null ? null : m.get(k);
        if (o instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object x : l) {
                out.add(String.valueOf(x));
            }

            return out;
        }

        return List.of();
    }

    static Map<?, ?> map(Map<?, ?> m, String k) {
        Object o = m == null ? null : m.get(k);
        return o instanceof Map<?, ?> x ? x : null;
    }

    // ------------------------------------------------------------------ conditions

    boolean test(Player p, String conds) {
        Realm.PlayerState st = this.realm.state(p);
        for (String raw : conds.split(",")) {
            String c = raw.trim();
            boolean not = c.startsWith("!");
            if (not) {
                c = c.substring(1);
            }

            boolean ok;
            if (c.startsWith("char=")) {
                ok = c.substring(5).equalsIgnoreCase(st.character == null ? "" : st.character);
            } else if (c.startsWith("flag:")) {
                String[] kv = c.substring(5).split("=", 2);
                ok = kv.length == 2 && kv[1].equalsIgnoreCase(st.flags.getOrDefault(kv[0], ""));
            } else if (c.startsWith("g:")) {
                String[] kv = c.substring(2).split("=", 2);
                ok = kv.length == 2 && kv[1].equalsIgnoreCase(this.realm.global(kv[0]));
            } else if (c.startsWith("rep:")) {
                String r = c.substring(4);
                int i = r.indexOf(">=") >= 0 ? r.indexOf(">=") : r.indexOf('<');
                if (i < 0) {
                    ok = false;
                } else {
                    String npc = r.substring(0, i);
                    boolean ge = r.startsWith(">=", i);
                    int n = Integer.parseInt(r.substring(i + (ge ? 2 : 1)).trim());
                    int have = this.realm.rep(p, npc);
                    ok = ge ? have >= n : have < n;
                }
            } else if (c.startsWith("mem:")) {
                ok = st.memory.contains(c.substring(4));
            } else if (c.startsWith("chapter>=")) {
                ok = this.realm.chapter >= Integer.parseInt(c.substring(9).trim());
            } else {
                ok = true;
            }

            if (ok == not) {
                return false;
            }
        }

        return true;
    }

    /** Keeps the lines whose conditions match, without the condition prefix. */
    List<String> filter(Player p, List<String> in) {
        List<String> out = new ArrayList<>();
        for (String l : in) {
            String s = l.trim();
            if (s.startsWith("[")) {
                int e = s.indexOf(']');
                if (e > 0) {
                    if (!this.test(p, s.substring(1, e))) {
                        continue;
                    }

                    s = s.substring(e + 1).trim();
                }
            }

            out.add(s.replace("{player}", p.getName()));
        }

        return out;
    }

    // ------------------------------------------------------------------ cutscenes

    /** Plays lines to one player, one after another; queued behind any cutscene already playing. */
    void play(Player p, List<String> raw, Runnable after) {
        List<String> ls = this.filter(p, raw);
        if (ls.isEmpty()) {
            if (after != null) {
                after.run();
            }

            return;
        }

        Deque<Runnable> q = this.queue.computeIfAbsent(p.getUniqueId(), u -> new ArrayDeque<>());
        q.add(() -> this.run(p, ls, 0, after));
        if (!this.playing.getOrDefault(p.getUniqueId(), false)) {
            this.next(p);
        }
    }

    private void next(Player p) {
        Deque<Runnable> q = this.queue.get(p.getUniqueId());
        Runnable r = q == null ? null : q.poll();
        this.playing.put(p.getUniqueId(), r != null);
        if (r != null) {
            r.run();
        }
    }

    private void run(Player p, List<String> ls, int i, Runnable after) {
        if (!p.isOnline()) {
            this.queue.remove(p.getUniqueId());
            this.playing.remove(p.getUniqueId());
            return;
        }

        if (i == 0) {
            this.last.put(p.getUniqueId(), ls);
            p.sendMessage(Text.mm("<dark_gray><st>                                             </st>"));
        }

        if (i >= ls.size()) {
            p.sendMessage(Text.mm("<dark_gray><st>                                             </st>"));
            if (after != null) {
                after.run();
            }

            this.next(p);
            return;
        }

        String l = ls.get(i);
        long wait = this.show(p, l);
        Tasks.later(wait, () -> this.run(p, ls, i + 1, after));
    }

    /** Shows one line; returns how long to wait before the next one. */
    private long show(Player p, String l) {
        if (l.equals("...")) {
            return 30L;
        }

        if (l.startsWith("!sound:")) {
            Snd.play(p, l.substring(7).trim(), 1.0F, 1.0F);
            return 5L;
        }

        if (l.startsWith("#")) {
            String[] ts = l.substring(1).split("\\|", 2);
            p.showTitle(Title.title(Text.mm("<gradient:#B06BFF:#FF5FA2><b>" + ts[0].trim()), Text.mm("<gray>" + (ts.length > 1 ? ts[1].trim() : "")),
                Title.Times.times(java.time.Duration.ofMillis(400), java.time.Duration.ofMillis(2800), java.time.Duration.ofMillis(600))));
            Snd.play(p, "block.end_portal_frame.fill", 0.8F, 0.7F);
            return 70L;
        }

        int c = l.indexOf(": ");
        if (c > 0 && c < 28 && Character.isUpperCase(l.charAt(0)) && l.substring(0, c).split(" ").length <= 3) {
            String who = l.substring(0, c);
            String what = l.substring(c + 2);
            String col = this.realm.story.getString("speakers." + who, "#FFD166");
            p.sendMessage(Text.mm("<color:" + col + "><b>" + Text.esc(who) + "</b></color> <dark_gray>» <white>" + Text.esc(what)));
            Snd.play(p, "entity.villager.ambient", 0.4F, 0.8F + (who.hashCode() & 7) * 0.08F);
            return Math.max(40L, Math.min(110L, 25L + what.length() * 2L));
        }

        p.sendMessage(Text.mm("<gray><i>" + Text.esc(l)));
        return Math.max(35L, Math.min(100L, 20L + l.length() * 2L));
    }

    void replay(Player p) {
        List<String> ls = this.last.get(p.getUniqueId());
        if (ls == null) {
            p.sendMessage(Text.mm("<gray>Nothing to replay yet."));
        } else {
            this.play(p, ls, null);
        }
    }

    // ------------------------------------------------------------------ season, chapters, votes

    void seasonStart() {
        List<String> intro = this.realm.story.getStringList("season-start");
        for (Player p : this.realm.smpPlayers()) {
            this.play(p, intro, () -> p.sendMessage(Text.mm("<light_purple>✦ Choose your character: <white><click:run_command:'/character'>[/character]</click>")));
        }

        this.openVote(this.realm.chapter);
    }

    void checkChapter() {
        if (!this.realm.running() || this.realm.finished) {
            return;
        }

        ConfigurationSection c = this.chapterSec(this.realm.chapter);
        if (c == null || c.getString("goal.counter") == null) {
            return;
        }

        if (this.realm.counter(c.getString("goal.counter")) >= c.getInt("goal.target", 1) && this.realm.chapter < this.chapters()) {
            Tasks.later(40L, this::advance);
        }
    }

    void advance() {
        this.closeVote(this.realm.chapter);
        List<String> outro = this.chapterSec(this.realm.chapter) == null ? List.of() : this.chapterSec(this.realm.chapter).getStringList("outro");
        if (this.realm.chapter >= this.chapters()) {
            return;
        }

        this.realm.chapter++;
        this.realm.save();
        this.enterChapter(outro);
    }

    void setChapter(int n) {
        this.realm.chapter = Math.max(1, Math.min(this.chapters(), n));
        this.realm.save();
        this.enterChapter(List.of());
    }

    private void enterChapter(List<String> outro) {
        int n = this.realm.chapter;
        ConfigurationSection c = this.chapterSec(n);
        List<String> all = new ArrayList<>(outro);
        all.add("#Chapter " + n + "|" + this.chapterName(n));
        if (c != null) {
            all.addAll(c.getStringList("intro"));
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.realm.on(p)) {
                this.play(p, all, () -> this.unlock(p));
            } else if (this.realm.sees(p)) {
                p.sendMessage(Text.mm("<dark_purple>✦ <light_purple>The Unstable Realm enters <white>Chapter " + n + ": " + Text.esc(this.chapterName(n))));
            }
        }

        this.openVote(n);
        this.realm.events.chapterStarted(n);
    }

    private void openVote(int n) {
        ConfigurationSection v = this.chapterSec(n) == null ? null : this.chapterSec(n).getConfigurationSection("vote");
        if (v == null) {
            return;
        }

        this.realm.votes.putIfAbsent(v.getString("id", "ch" + n), new HashMap<>());
        Tasks.later(400L, () -> {
            for (Player p : this.realm.smpPlayers()) {
                this.showVote(p, v);
            }
        });
    }

    private void showVote(Player p, ConfigurationSection v) {
        p.sendMessage(Text.mm("<gold><b>🗳 THE REALM DECIDES:</b> <white>" + Text.esc(v.getString("question", "?"))));
        ConfigurationSection o = v.getConfigurationSection("options");
        if (o != null) {
            for (String k : o.getKeys(false)) {
                p.sendMessage(Text.mm("  <click:run_command:'/realm vote " + k + "'><hover:show_text:'<gray>Click to vote'><yellow>[" + Text.esc(o.getString(k)) + "]</hover></click>"));
            }
        }
    }

    void vote(Player p, String option) {
        ConfigurationSection v = this.chapterSec(this.realm.chapter) == null ? null : this.chapterSec(this.realm.chapter).getConfigurationSection("vote");
        if (v == null || !this.realm.on(p)) {
            p.sendMessage(Text.mm("<gray>There is no vote right now."));
            return;
        }

        String id = v.getString("id", "ch" + this.realm.chapter);
        ConfigurationSection o = v.getConfigurationSection("options");
        if (option == null || o == null || !o.contains(option)) {
            this.showVote(p, v);
            Map<String, Integer> tally = this.tally(id);
            p.sendMessage(Text.mm("<gray>So far: <white>" + tally));
            return;
        }

        this.realm.votes.computeIfAbsent(id, k -> new HashMap<>()).put(p.getUniqueId(), option);
        this.realm.markDirty();
        p.sendMessage(Text.mm("<green>Your voice is heard: <white>" + Text.esc(o.getString(option)) + "<gray>. You can change it until the chapter ends."));
        Snd.play(p, Snd.CHIME, 1.0F, 1.2F);
    }

    private Map<String, Integer> tally(String id) {
        Map<String, Integer> t = new LinkedHashMap<>();
        for (String o : this.realm.votes.getOrDefault(id, Map.of()).values()) {
            t.merge(o, 1, Integer::sum);
        }

        return t;
    }

    private void closeVote(int n) {
        ConfigurationSection v = this.chapterSec(n) == null ? null : this.chapterSec(n).getConfigurationSection("vote");
        if (v == null) {
            return;
        }

        String id = v.getString("id", "ch" + n);
        if (!this.realm.global(id).isEmpty()) {
            return;
        }

        ConfigurationSection o = v.getConfigurationSection("options");
        if (o == null || o.getKeys(false).isEmpty()) {
            return;
        }

        String win = o.getKeys(false).iterator().next();
        int best = -1;
        for (Map.Entry<String, Integer> e : this.tally(id).entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                win = e.getKey();
            }
        }

        this.realm.setGlobal(id, win);
        this.realm.broadcast("<gold><b>THE REALM HAS CHOSEN", "<white>" + Text.esc(o.getString(win, win)),
            "<gold>🗳 <white>" + Text.esc(v.getString("question", "")) + " <dark_gray>» <yellow>" + Text.esc(o.getString(win, win)), Snd.CHALLENGE);
    }

    // ------------------------------------------------------------------ quests

    /** Right after picking a character: the personal intro, then the first step. */
    void beginCharacter(Player p) {
        Realm.PlayerState st = this.realm.state(p);
        List<String> intro = this.realm.story.getStringList("intros." + st.character);
        this.play(p, intro, () -> this.unlock(p));
    }

    /** Starts the current step if its chapter is open and it hasn't started yet. */
    void unlock(Player p) {
        Realm.PlayerState st = this.realm.state(p);
        Map<?, ?> s = this.step(st);
        if (s == null || st.pendingChoice != null) {
            return;
        }

        String started = st.flags.get("_started");
        if (String.valueOf(st.step).equals(started)) {
            return;
        }

        if (num(s, "chapter", 1) > this.realm.chapter) {
            p.sendMessage(Text.mm("<gray>Your story continues in <white>Chapter " + num(s, "chapter", 1) + "<gray>. The realm must move forward first."));
            return;
        }

        st.flags.put("_started", String.valueOf(st.step));
        st.progress = 0;
        this.realm.markDirty();
        List<String> start = new ArrayList<>();
        start.add("#" + str(s, "title", "A new path") + "|" + this.objectiveText(s, 0));
        start.addAll(lines(s, "start"));
        this.play(p, start, () -> {
            p.sendMessage(Text.mm("<light_purple>✦ Quest: <white>" + Text.esc(str(s, "title", "?")) + " <dark_gray>» <gray>" + Text.esc(this.objectiveText(s, 0))));
            Map<?, ?> o = map(s, "objective");
            if ("CHOICE".equalsIgnoreCase(str(o, "type", ""))) {
                this.complete(p);
            }
        });
    }

    String objectiveText(Map<?, ?> s, int have) {
        Map<?, ?> o = map(s, "objective");
        int need = num(o, "amount", 1);
        String t = str(o, "text", str(o, "type", "?"));
        return need > 1 ? t + " (" + Math.min(have, need) + "/" + need + ")" : t;
    }

    /** Progress on the current objective of this type and target. */
    void progress(Player p, String type, String target, int n) {
        if (!this.realm.on(p)) {
            return;
        }

        Realm.PlayerState st = this.realm.state(p);
        Map<?, ?> s = this.step(st);
        if (s == null || st.pendingChoice != null || !String.valueOf(st.step).equals(st.flags.get("_started"))
            || String.valueOf(st.step).equals(st.flags.get("_done"))) {
            return;
        }

        Map<?, ?> o = map(s, "objective");
        if (!type.equalsIgnoreCase(str(o, "type", "")) || !target.equalsIgnoreCase(str(o, "target", ""))) {
            return;
        }

        st.progress += n;
        this.realm.markDirty();
        int need = num(o, "amount", 1);
        if (st.progress >= need) {
            this.complete(p);
        } else {
            p.sendActionBar(Text.mm("<light_purple>✦ <white>" + Text.esc(this.objectiveText(s, st.progress))));
            Snd.play(p, Snd.ORB, 0.6F, 1.4F);
        }
    }

    void event(Player p, String key) {
        this.progress(p, "EVENT", key, 1);
    }

    /** Called by StoryNpcs; true when the talk finished a quest objective. */
    boolean talked(Player p, String npc) {
        Realm.PlayerState st = this.realm.state(p);
        Map<?, ?> s = this.step(st);
        Map<?, ?> o = map(s, "objective");
        if (s != null && "TALK".equalsIgnoreCase(str(o, "type", "")) && npc.equalsIgnoreCase(str(o, "target", ""))
            && String.valueOf(st.step).equals(st.flags.get("_started")) && st.pendingChoice == null) {
            this.progress(p, "TALK", npc, 1);
            return true;
        }

        return false;
    }

    private void complete(Player p) {
        Realm.PlayerState st = this.realm.state(p);
        Map<?, ?> s = this.step(st);
        if (s == null) {
            return;
        }

        if (String.valueOf(st.step).equals(st.flags.get("_done"))) {
            return;
        }

        Map<?, ?> o = map(s, "objective");
        if ("COLLECT".equalsIgnoreCase(str(o, "type", "")) && !this.takeCollected(p, str(o, "target", ""), num(o, "amount", 1))) {
            return;
        }

        st.flags.put("_done", String.valueOf(st.step));

        Map<?, ?> choice = map(s, "choice");
        if (choice != null) {
            st.pendingChoice = String.valueOf(st.step);
        }

        this.realm.markDirty();
        Snd.play(p, Snd.CHALLENGE, 0.8F, 1.0F);
        this.play(p, lines(s, "done"), () -> {
            if (choice != null) {
                this.showChoice(p, choice);
            } else {
                this.finish(p);
            }
        });
    }

    private void showChoice(Player p, Map<?, ?> choice) {
        p.sendMessage(Text.mm("<gold><b>⚖ " + Text.esc(str(choice, "prompt", "Choose")) + "</b>"));
        Map<?, ?> opts = map(choice, "options");
        if (opts != null) {
            for (Object k : opts.keySet()) {
                Map<?, ?> op = map(opts, String.valueOf(k));
                p.sendMessage(Text.mm("  <click:run_command:'/realm choose " + k + "'><hover:show_text:'<gray>Click to choose. Choices are forever.'><yellow>[" + Text.esc(str(op, "text", String.valueOf(k))) + "]</hover></click>"));
            }
        }

        p.sendMessage(Text.mm("<dark_gray>Choices are forever. Take your time."));
    }

    void choose(Player p, String key) {
        Realm.PlayerState st = this.realm.state(p);
        Map<?, ?> s = this.step(st);
        if (st.pendingChoice == null || s == null) {
            p.sendMessage(Text.mm("<gray>You have no choice to make right now."));
            return;
        }

        Map<?, ?> opts = map(map(s, "choice"), "options");
        Map<?, ?> op = map(opts, key);
        if (op == null) {
            this.showChoice(p, map(s, "choice"));
            return;
        }

        st.pendingChoice = null;
        st.flags.put("choice_" + st.character + "_" + st.step, key);
        this.apply(p, lines(op, "effects"));
        this.realm.markDirty();
        this.play(p, lines(op, "lines"), () -> this.finish(p));
    }

    private void finish(Player p) {
        Realm.PlayerState st = this.realm.state(p);
        Map<?, ?> s = this.step(st);
        if (s == null) {
            return;
        }

        this.apply(p, lines(s, "reward"));
        st.step++;
        st.progress = 0;
        this.realm.save();
        this.realm.addCounter("quests_done", 1);
        this.realm.addCounter("ch" + this.realm.chapter + "_quests", 1);
        if (this.step(st) == null) {
            this.play(p, this.realm.story.getStringList("tale-complete"), null);
        } else {
            Tasks.later(60L, () -> this.unlock(p));
        }
    }

    private boolean takeCollected(Player p, String target, int n) {
        if ("shards".equalsIgnoreCase(target)) {
            return this.realm.takeShards(p, n);
        }

        Material m = Material.matchMaterial(target);
        if (m == null || !p.getInventory().containsAtLeast(new ItemStack(m), n)) {
            return false;
        }

        p.getInventory().removeItem(new ItemStack(m, n));
        return true;
    }

    private int collected(Player p, String target) {
        if ("shards".equalsIgnoreCase(target)) {
            return this.realm.countShards(p);
        }

        Material m = Material.matchMaterial(target);
        if (m == null) {
            return 0;
        }

        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == m) {
                n += it.getAmount();
            }
        }

        return n;
    }

    /** Effects: flag:k=v, rep:npc+5, mem:npc:key, shards:10, item:MATERIAL:n, title:Text, counter:key+1, effect:TYPE:seconds:amp, xp:n. */
    void apply(Player p, List<String> effects) {
        Realm.PlayerState st = this.realm.state(p);
        for (String e : effects) {
            try {
                if (e.startsWith("flag:")) {
                    String[] kv = e.substring(5).split("=", 2);
                    st.flags.put(kv[0], kv.length > 1 ? kv[1] : "true");
                } else if (e.startsWith("rep:")) {
                    String r = e.substring(4);
                    int i = Math.max(r.lastIndexOf('+'), r.lastIndexOf('-'));
                    st.rep.merge(r.substring(0, i), Integer.parseInt(r.substring(i)), Integer::sum);
                    p.sendActionBar(Text.mm((r.charAt(i) == '+' ? "<green>" : "<red>") + Text.esc(this.realm.npcs.name(r.substring(0, i))) + " will remember that."));
                } else if (e.startsWith("mem:")) {
                    st.memory.add(e.substring(4));
                } else if (e.startsWith("shards:")) {
                    this.realm.give(p, this.realm.shard(Integer.parseInt(e.substring(7))));
                } else if (e.startsWith("item:")) {
                    String[] x = e.substring(5).split(":");
                    Material m = Material.matchMaterial(x[0]);
                    if (m != null) {
                        this.realm.give(p, new ItemStack(m, x.length > 1 ? Integer.parseInt(x[1]) : 1));
                    }
                } else if (e.startsWith("title:")) {
                    String t = e.substring(6);
                    if (!st.titles.contains(t)) {
                        st.titles.add(t);
                        p.sendMessage(Text.mm("<gold>🏅 New title: <yellow><b>" + Text.esc(t)));
                    }
                } else if (e.startsWith("counter:")) {
                    String r = e.substring(8);
                    int i = Math.max(r.lastIndexOf('+'), r.lastIndexOf('-'));
                    this.realm.addCounter(r.substring(0, i), Integer.parseInt(r.substring(i)));
                } else if (e.startsWith("effect:")) {
                    String[] x = e.substring(7).split(":");
                    PotionEffectType t = org.bukkit.Registry.EFFECT.get(org.bukkit.NamespacedKey.minecraft(x[0].toLowerCase(Locale.ROOT)));
                    if (t != null) {
                        p.addPotionEffect(new PotionEffect(t, Integer.parseInt(x[1]) * 20, x.length > 2 ? Integer.parseInt(x[2]) : 0));
                    }
                } else if (e.startsWith("xp:")) {
                    p.giveExp(Integer.parseInt(e.substring(3)));
                }
            } catch (RuntimeException bad) {
                this.realm.plugin.getLogger().warning("Bad story effect '" + e + "': " + bad.getMessage());
            }
        }

        this.realm.markDirty();
    }

    // ------------------------------------------------------------------ the ending

    void ending(Map<UUID, Double> damage) {
        if (this.realm.finished) {
            return;
        }

        this.realm.finished = true;
        String fate = this.realm.global("fate");
        if (fate.isEmpty()) {
            this.closeVote(this.realm.chapter);
            fate = this.realm.global("fate");
        }

        UUID top = null;
        double best = 0;
        for (Map.Entry<UUID, Double> e : damage.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                top = e.getKey();
            }
        }

        String hero = top == null ? "the bearers" : Bukkit.getOfflinePlayer(top).getName();
        List<String> end = new ArrayList<>(this.realm.story.getStringList("endings." + (fate.isEmpty() ? "seal" : fate)));
        end.replaceAll(l -> l.replace("{hero}", hero == null ? "a hero" : hero));
        for (Player p : this.realm.smpPlayers()) {
            Realm.PlayerState st = this.realm.state(p);
            if (damage.containsKey(p.getUniqueId())) {
                this.apply(p, List.of("title:Survivor of the Unstable Realm", "shards:32"));
            }

            if (p.getUniqueId().equals(top)) {
                this.apply(p, List.of("title:" + ("claim".equals(fate) ? "Rift Sovereign" : "Saviour of the Realm")));
            }

            this.play(p, end, null);
            st.flags.put("ending", fate);
        }

        this.realm.save();
    }

    // ------------------------------------------------------------------ cards, journal, bars

    void card(Player p) {
        p.sendMessage(Text.mm("<dark_gray><st>                                             </st>"));
        p.sendMessage(Text.mm("<gradient:#B06BFF:#FF5FA2><b>THE UNSTABLE REALM</b></gradient> <dark_gray>» " + (this.realm.running() && this.realm.sees(p) ? "<white>Chapter " + this.realm.chapter
            + ": <yellow>" + Text.esc(this.chapterName(this.realm.chapter)) : "<gray>coming soon")));
        ConfigurationSection c = this.chapterSec(this.realm.chapter);
        if (this.realm.running() && this.realm.sees(p) && c != null && c.getString("goal.counter") != null) {
            p.sendMessage(Text.mm("<gray>The realm's goal: <white>" + Text.esc(c.getString("goal.text", "?")) + " <aqua>"
                + this.realm.counter(c.getString("goal.counter")) + "/" + c.getInt("goal.target", 1)));
        }

        Realm.PlayerState st = this.realm.state(p);
        Characters.Role r = Characters.Role.of(st.character);
        p.sendMessage(Text.mm("<gray>You are: " + (r == null ? "<white>nobody yet <dark_gray>(<click:run_command:'/character'><yellow>/character</yellow></click>)" : r.mm())));
        Map<?, ?> s = this.step(st);
        if (s != null) {
            p.sendMessage(Text.mm("<gray>Quest: <white>" + Text.esc(str(s, "title", "?")) + " <dark_gray>» <gray>" + Text.esc(this.objectiveText(s, st.progress))));
        }

        if (!st.titles.isEmpty()) {
            p.sendMessage(Text.mm("<gray>Titles: <yellow>" + Text.esc(String.join(", ", st.titles))));
        }

        p.sendMessage(Text.mm("<dark_gray>/realm journal • /realm vote • /realm who • /realm replay"));
        p.sendMessage(Text.mm("<dark_gray><st>                                             </st>"));
    }

    void journal(Player p) {
        Realm.PlayerState st = this.realm.state(p);
        Characters.Role r = Characters.Role.of(st.character);
        if (r == null) {
            p.sendMessage(Text.mm("<gray>You have no story yet. Choose a character: <white>/character"));
            return;
        }

        p.sendMessage(Text.mm("<gold><b>📖 Journal of " + Text.esc(p.getName()) + "</b> <dark_gray>» " + r.mm()));
        List<Map<?, ?>> all = this.steps(st.character);
        for (int i = 0; i < all.size(); i++) {
            Map<?, ?> s = all.get(i);
            String mark = i < st.step ? "<green>✔" : i == st.step ? (num(s, "chapter", 1) <= this.realm.chapter ? "<yellow>▶" : "<dark_gray>🔒") : "<dark_gray>🔒";
            String text = i <= st.step || num(s, "chapter", 1) <= this.realm.chapter ? Text.esc(str(s, "title", "?")) : "???";
            p.sendMessage(Text.mm(mark + " <white>" + text + " <dark_gray>(chapter " + num(s, "chapter", 1) + ")"));
        }
    }

    void second() {
        this.tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Realm.PlayerState st = this.realm.stateIfAny(p.getUniqueId());
            Map<?, ?> s = st == null ? null : this.step(st);
            boolean show = this.realm.on(p) && s != null && String.valueOf(st.step).equals(st.flags.get("_started"));
            BossBar bar = this.bars.get(p.getUniqueId());
            if (!show) {
                if (bar != null) {
                    p.hideBossBar(bar);
                    this.bars.remove(p.getUniqueId());
                }

                continue;
            }

            Map<?, ?> o = map(s, "objective");
            String type = str(o, "type", "");
            boolean doneAlready = String.valueOf(st.step).equals(st.flags.get("_done"));
            if ("COLLECT".equalsIgnoreCase(type) && st.pendingChoice == null && !doneAlready) {
                st.progress = this.collected(p, str(o, "target", ""));
                if (st.progress >= num(o, "amount", 1)) {
                    this.complete(p);
                }
            } else if ("CAPITAL".equalsIgnoreCase(type) && st.pendingChoice == null && !doneAlready && this.realm.capital != null
                && p.getWorld().equals(this.realm.capital.getWorld()) && p.getLocation().distanceSquared(this.realm.capital) < 30 * 30) {
                this.progress(p, "CAPITAL", str(o, "target", ""), 1);
            }

            String label = st.pendingChoice != null ? "⚖ A choice awaits (check chat)" : str(s, "title", "?") + " » " + this.objectiveText(s, st.progress);
            float pr = st.pendingChoice != null ? 1.0F : Math.min(1.0F, st.progress / (float)Math.max(1, num(o, "amount", 1)));
            if (bar == null) {
                bar = BossBar.bossBar(Text.mm("<light_purple>" + Text.esc(label)), pr, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
                this.bars.put(p.getUniqueId(), bar);
                p.showBossBar(bar);
            } else if (this.tick % 2 == 0) {
                bar.name(Text.mm("<light_purple>" + Text.esc(label)));
                bar.progress(pr);
            }
        }
    }

    // ------------------------------------------------------------------ listeners

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || e.getEntity() instanceof Player) {
            return;
        }

        Entity v = e.getEntity();
        if (v.getPersistentDataContainer().has(this.realm.riftKey)) {
            this.progress(k, "KILL", "rift", 1);
        }

        this.progress(k, "KILL", v.getType().name(), 1);
        if (v instanceof Monster) {
            this.progress(k, "KILL", "hostile", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKill(PlayerDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null && k != e.getEntity()) {
            this.progress(k, "KILL", "player", 1);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Tasks.later(100L, () -> this.remind(e.getPlayer()));
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        if (this.realm.on(e.getPlayer()) && !this.realm.on(e.getFrom())) {
            Tasks.later(40L, () -> this.remind(e.getPlayer()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.bars.remove(e.getPlayer().getUniqueId());
        this.queue.remove(e.getPlayer().getUniqueId());
        this.playing.remove(e.getPlayer().getUniqueId());
    }

    private void remind(Player p) {
        if (!p.isOnline() || !this.realm.on(p) || this.realm.finished) {
            return;
        }

        Realm.PlayerState st = this.realm.state(p);
        if (st.character == null) {
            p.sendMessage(Text.mm("<gradient:#B06BFF:#FF5FA2><b>The Unstable Realm</b></gradient> <gray>is alive. Chapter " + this.realm.chapter
                + ". <white><click:run_command:'/character'>[Choose your character]</click>"));
        } else if (st.pendingChoice != null) {
            Map<?, ?> s = this.step(st);
            if (s != null) {
                this.showChoice(p, map(s, "choice"));
            }
        } else {
            this.unlock(p);
        }
    }
}
