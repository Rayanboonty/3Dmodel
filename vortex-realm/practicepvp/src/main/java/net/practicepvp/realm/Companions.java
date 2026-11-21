package net.practicepvp.realm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.practicepvp.ai.LlmClient;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vindicator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

/**
 * 3.14.7 - companions: every player in the Unstable Realm can summon a sidekick that obeys orders typed in chat
 * ("Bran, follow me", "guard here", "attack that", "defend me", "go there", "stay", "be quiet").
 *
 * <p>Orders are understood two ways: a built-in parser for common orders (instant, works with no AI key), and, when an
 * AI key is set in /ai (Claude, OpenAI, Groq, a local Ollama...), free-form speech: the companion understands anything,
 * answers in character and picks the action itself. The AI only ever chooses from the actions below; it can't run
 * commands. Voice orders need Simple Voice Chat plus speech-to-text and come later.
 */
public final class Companions implements Listener, CommandExecutor {
    enum Mode { FOLLOW, STAY, GUARD, DEFEND, ATTACK, MOVE }

    private static final class Comp {
        final UUID owner;
        Vindicator body;
        String name;
        Mode mode = Mode.FOLLOW;
        Location anchor;
        LivingEntity target;
        Location moveTo;
        boolean passive;
        final List<LlmClient.Msg> memory = new ArrayList<>();
        long lastBark;
        long thinking;

        Comp(UUID owner) {
            this.owner = owner;
        }
    }

    private static final String[] NAMES = {"Bran", "Wren", "Thorne", "Mira", "Kael", "Juno", "Rook", "Sable", "Fenn", "Ivy"};
    private static final Pattern ATTACK = Pattern.compile("\\b(attack|kill|hit|fight|get|destroy)\\b\\s*(\\w+)?");

    private final Realm realm;
    private final NamespacedKey key;
    private final Map<UUID, Comp> comps = new HashMap<>();
    private final Map<UUID, Long> deadUntil = new HashMap<>();
    private final Random rnd = new Random();

    Companions(Realm realm) {
        this.realm = realm;
        this.key = new NamespacedKey(realm.plugin, "companion_of");
    }

    // ------------------------------------------------------------------ /companion

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!(s instanceof Player p)) {
            return true;
        }

        String sub = args.length == 0 ? "summon" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "summon", "call" -> this.summon(p);
            case "dismiss", "bye" -> this.dismiss(p, true);
            case "name" -> {
                if (args.length < 2 || !args[1].matches("[A-Za-z]{2,14}")) {
                    p.sendMessage(Text.mm("<red>/companion name <2-14 letters>"));
                    return true;
                }

                this.realm.state(p).flags.put("companion_name", args[1]);
                this.realm.markDirty();
                Comp c = this.comps.get(p.getUniqueId());
                if (c != null) {
                    c.name = args[1];
                    this.nameTag(c);
                    this.say(c, "From now on, call me " + args[1] + ".");
                } else {
                    p.sendMessage(Text.mm("<green>Your companion will be called <white>" + args[1] + "<green>."));
                }
            }
            default -> this.help(p);
        }

        return true;
    }

    private void help(Player p) {
        p.sendMessage(Text.mm("<gold><b>Companion</b> <gray>/companion summon | dismiss | name <name>"));
        p.sendMessage(Text.mm("<gray>Talk to it in chat, starting with its name: <white>\"Bran, follow me\"</white>, <white>\"guard here\"</white>, "
            + "<white>\"attack that\"</white> (what you look at), <white>\"attack Steve\"</white>, <white>\"defend me\"</white>, <white>\"go there\"</white>, "
            + "<white>\"stay\"</white>, <white>\"be peaceful\"</white> / <white>\"be aggressive\"</white>, <white>\"go home\"</white>."));
        p.sendMessage(Text.mm(this.realm.plugin.ai() != null && this.realm.plugin.ai().hasKey()
            ? "<light_purple>AI is on: you can also just talk to it normally, it understands and answers in character."
            : "<dark_gray>Tip: with an AI key set in /ai, your companion understands anything you say and talks back."));
    }

    private void summon(Player p) {
        if (!this.realm.on(p)) {
            p.sendMessage(Text.mm("<gray>Companions only answer in the Unstable Realm while the season runs."));
            return;
        }

        Long dead = this.deadUntil.get(p.getUniqueId());
        if (dead != null && dead > System.currentTimeMillis()) {
            p.sendMessage(Text.mm("<red>Your companion is still recovering. <gray>(" + ((dead - System.currentTimeMillis()) / 1000 + 1) + "s)"));
            return;
        }

        Comp c = this.comps.get(p.getUniqueId());
        if (c != null && c.body != null && c.body.isValid()) {
            c.body.teleport(p.getLocation());
            this.say(c, "I'm here.");
            return;
        }

        c = new Comp(p.getUniqueId());
        String saved = this.realm.state(p).flags.get("companion_name");
        c.name = saved != null ? saved : NAMES[Math.abs(p.getUniqueId().hashCode()) % NAMES.length];
        Location at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(-1.5));
        Comp fc = c;
        c.body = p.getWorld().spawn(at, Vindicator.class, v -> {
            v.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, p.getUniqueId().toString());
            v.setPersistent(false);
            v.setRemoveWhenFarAway(false);
            v.setCanJoinRaid(false);
            v.setPatrolLeader(false);
            v.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            v.getEquipment().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
            v.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            v.getEquipment().setItemInMainHandDropChance(0);
            v.getEquipment().setHelmetDropChance(0);
            v.getEquipment().setChestplateDropChance(0);
            set(v, Attribute.MAX_HEALTH, 50);
            v.setHealth(50);
            set(v, Attribute.ATTACK_DAMAGE, 6);
            set(v, Attribute.MOVEMENT_SPEED, 0.36);
            set(v, Attribute.FOLLOW_RANGE, 32);
        });
        this.comps.put(p.getUniqueId(), c);
        this.realm.hideFromOthers(c.body);
        this.nameTag(fc);
        p.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.02);
        Snd.play(p, "entity.evoker.prepare_summon", 0.7F, 1.4F);
        this.say(c, saved == null ? "I'm " + c.name + ". Tell me what to do, and I'll do it." : "Reporting for duty.");
        if (this.realm.state(p).flags.get("companion_tip") == null) {
            this.realm.state(p).flags.put("companion_tip", "1");
            Tasks.later(40L, () -> this.help(p));
        }
    }

    private void nameTag(Comp c) {
        Player o = Bukkit.getPlayer(c.owner);
        if (c.body != null && c.body.isValid()) {
            c.body.customName(Text.mm("<gold><b>" + Text.esc(c.name) + "</b> <gray>(" + Text.esc(o == null ? "?" : o.getName()) + ")"));
            c.body.setCustomNameVisible(true);
        }
    }

    private void dismiss(Player p, boolean talk) {
        Comp c = this.comps.remove(p.getUniqueId());
        if (c == null) {
            if (talk) {
                p.sendMessage(Text.mm("<gray>You have no companion out."));
            }

            return;
        }

        if (talk) {
            this.say(c, "Until next time.");
        }

        if (c.body != null && c.body.isValid()) {
            c.body.getWorld().spawnParticle(Particle.CLOUD, c.body.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.02);
            c.body.remove();
        }
    }

    private static void set(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) {
            i.setBaseValue(v);
        }
    }

    // ------------------------------------------------------------------ talking: chat orders

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Comp c = this.comps.get(p.getUniqueId());
        if (c == null) {
            return;
        }

        String raw = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        String low = raw.toLowerCase(Locale.ROOT);
        String n = c.name.toLowerCase(Locale.ROOT);
        boolean addressed = low.startsWith(n) || low.startsWith("@" + n) || low.startsWith("companion") || low.startsWith("hey " + n);
        if (!addressed) {
            return;
        }

        // talking to your companion stays between you two (and whoever is close enough to hear)
        e.setCancelled(true);
        String text = raw.replaceFirst("(?i)^(hey\\s+)?@?(" + Pattern.quote(c.name) + "|companion)[,:!\\s]*", "").trim();
        Tasks.sync(() -> this.heard(p, c, text));
    }

    private void heard(Player p, Comp c, String text) {
        if (c.body == null || !c.body.isValid() || !p.isOnline()) {
            return;
        }

        for (Player o : this.listeners(c)) {
            o.sendMessage(Text.mm("<gray>" + Text.esc(p.getName()) + " <dark_gray>→ <gold>" + Text.esc(c.name) + "</gold><dark_gray>: <white>" + Text.esc(text)));
        }

        if (this.quickOrder(p, c, text.toLowerCase(Locale.ROOT))) {
            return;
        }

        if (this.realm.plugin.ai() == null || !this.realm.plugin.ai().hasKey()) {
            this.say(c, this.confused());
            return;
        }

        if (System.currentTimeMillis() - c.thinking < 2500) {
            return;
        }

        c.thinking = System.currentTimeMillis();
        String system = this.persona(p, c);
        c.memory.add(LlmClient.Msg.user(p.getName() + ": " + text));
        while (c.memory.size() > 12) {
            c.memory.remove(0);
        }

        List<LlmClient.Msg> msgs = new ArrayList<>(c.memory);
        Tasks.async(() -> {
            String answer = this.realm.plugin.ai().quick(system, msgs, 300);
            Tasks.sync(() -> this.act(p, c, answer));
        });
    }

    /** The common orders, understood instantly without AI. True when the text was an order. */
    private boolean quickOrder(Player p, Comp c, String t) {
        if (t.matches(".*\\b(follow|come( here| with me)?|with me|let'?s go|heel)\\b.*") && !t.contains("don't")) {
            this.order(p, c, Mode.FOLLOW, null, "Right behind you.");
        } else if (t.matches(".*\\b(defend|protect|cover|watch) (me|my back)\\b.*")) {
            this.order(p, c, Mode.DEFEND, null, "Nobody touches you while I'm here.");
        } else if (t.matches(".*\\b(guard|defend|protect) (here|this|this place|the base|base)\\b.*") || t.equals("guard")) {
            this.order(p, c, Mode.GUARD, null, "I'll hold this spot.");
        } else if (t.matches(".*\\b(stay|wait|stop|hold|halt|sit)\\b.*")) {
            this.order(p, c, Mode.STAY, null, "Holding position.");
        } else if (t.matches(".*\\b(go|move|walk|run) (there|over there|to that)\\b.*")) {
            this.order(p, c, Mode.MOVE, null, "On my way.");
        } else if (t.matches(".*\\b(peaceful|passive|calm down|stand down|don't fight)\\b.*")) {
            c.passive = true;
            c.target = null;
            c.body.setTarget(null);
            this.say(c, "Weapons down. I'll only fight if you're hit.");
        } else if (t.matches(".*\\b(aggressive|fight everything|go wild)\\b.*")) {
            c.passive = false;
            this.say(c, "Finally. Point me at something.");
        } else if (t.matches(".*\\b(go home|dismiss|leave|go away|bye)\\b.*")) {
            this.dismiss(p, true);
        } else {
            Matcher m = ATTACK.matcher(t);
            if (!m.find()) {
                return false;
            }

            String who = m.group(2);
            LivingEntity target = this.resolve(p, who);
            if (target == null) {
                this.say(c, who == null || who.matches("that|it|him|her|them|this") ? "I don't see what you mean. Look right at it." : "I can't find " + who + " around here.");
            } else {
                this.order(p, c, Mode.ATTACK, target, this.attackLine(target));
            }
        }

        return true;
    }

    private void order(Player p, Comp c, Mode m, LivingEntity target, String line) {
        c.mode = m;
        c.target = target;
        c.anchor = c.body.getLocation();
        if (m == Mode.MOVE) {
            RayTraceResult r = p.rayTraceBlocks(48);
            c.moveTo = r == null || r.getHitBlock() == null ? null : r.getHitBlock().getLocation().add(0.5, 1, 0.5);
            if (c.moveTo == null) {
                this.say(c, "Where? Look at the spot.");
                c.mode = Mode.STAY;
                return;
            }
        }

        if (m == Mode.ATTACK && target != null) {
            c.body.setTarget(target);
        } else if (m != Mode.DEFEND && m != Mode.GUARD) {
            c.body.setTarget(null);
        }

        this.say(c, line);
        Snd.play(p, "entity.vindicator.celebrate", 0.5F, 1.3F);
    }

    /** "that" = what the owner looks at; otherwise an online player's name, or a mob type close by. */
    private LivingEntity resolve(Player p, String who) {
        if (who == null || who.matches("that|it|him|her|them|this")) {
            RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 40, 0.6,
                e -> e != p && e instanceof LivingEntity && !this.mine(e, p));
            return r != null && r.getHitEntity() instanceof LivingEntity le ? le : null;
        }

        Player t = Bukkit.getPlayerExact(who);
        if (t == null) {
            for (Player o : p.getWorld().getPlayers()) {
                if (o.getName().toLowerCase(Locale.ROOT).startsWith(who.toLowerCase(Locale.ROOT))) {
                    t = o;
                }
            }
        }

        if (t != null && t != p && t.getWorld().equals(p.getWorld()) && t.getLocation().distanceSquared(p.getLocation()) < 64 * 64) {
            return t;
        }

        LivingEntity best = null;
        double bd = 40 * 40;
        for (Entity e : p.getNearbyEntities(40, 16, 40)) {
            if (e instanceof LivingEntity le && !(e instanceof Player) && !this.mine(e, p) && e.getType().name().toLowerCase(Locale.ROOT).startsWith(who.replaceAll("s$", ""))) {
                double d = e.getLocation().distanceSquared(p.getLocation());
                if (d < bd) {
                    bd = d;
                    best = le;
                }
            }
        }

        return best;
    }

    private boolean mine(Entity e, Player p) {
        return p.getUniqueId().toString().equals(e.getPersistentDataContainer().get(this.key, PersistentDataType.STRING));
    }

    private String attackLine(LivingEntity t) {
        String what = t instanceof Player pl ? pl.getName() : t.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] l = {"Going for the " + what + ".", "Leave the " + what + " to me.", what + "? Consider it done.", "Charging!"};
        return t instanceof Player pl ? new String[] {"Going after " + pl.getName() + ".", pl.getName() + " picked the wrong fight.", "On it."}[this.rnd.nextInt(3)]
            : l[this.rnd.nextInt(l.length)];
    }

    private String confused() {
        String[] l = {"Not sure what you mean. Try: follow, stay, guard here, defend me, attack that.", "Hm? Give me an order: follow, guard, attack...",
            "I'm a fighter, not a poet. Follow, stay, guard, attack?"};
        return l[this.rnd.nextInt(l.length)];
    }

    // ------------------------------------------------------------------ the AI brain

    private String persona(Player p, Comp c) {
        Realm.PlayerState st = this.realm.state(p);
        Characters.Role r = Characters.Role.of(st.character);
        String nearby = this.nearbyText(c);
        return "You are " + c.name + ", a loyal sword-for-hire companion in a Minecraft survival season called the Unstable Realm. You follow "
            + p.getName() + (r == null ? "" : ", who is " + r.display) + ". The realm is in chapter " + this.realm.chapter + " ("
            + this.realm.storyEngine.chapterName(this.realm.chapter) + "): a broken Crown, a Rift spilling monsters, the Order of the Gate against the Hollow Choir. "
            + "You speak like a gruff, warm, slightly funny medieval soldier, in 1-2 short sentences, in the player's language. Never mention being an AI. "
            + "You are currently: " + c.mode.name().toLowerCase(Locale.ROOT) + (c.passive ? " (peaceful)" : "") + ". Your health: " + (int)c.body.getHealth() + "/50. "
            + "Nearby: " + nearby + ".\n\n"
            + "Reply ONLY with JSON: {\"say\": \"what you say\", \"action\": \"none|follow|stay|guard|defend|attack|move|peaceful|aggressive|dismiss\", "
            + "\"target\": \"player name or mob type to attack, or 'that' for what they look at, else null\"}. Pick an action only when the player asks for one. "
            + "Never attack " + p.getName() + ". You can't run commands, give items or teleport; if asked, say so in character.";
    }

    private String nearbyText(Comp c) {
        Map<String, Integer> count = new HashMap<>();
        for (Entity e : c.body.getNearbyEntities(24, 10, 24)) {
            if (e instanceof Player pl) {
                count.merge("player " + pl.getName(), 1, Integer::sum);
            } else if (e instanceof LivingEntity && !(e instanceof org.bukkit.entity.ArmorStand)) {
                count.merge(e.getType().name().toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        }

        if (count.isEmpty()) {
            return "nothing";
        }

        List<String> out = new ArrayList<>();
        count.forEach((k, v) -> out.add(v > 1 ? v + "x " + k : k));
        return String.join(", ", out.subList(0, Math.min(10, out.size())));
    }

    private void act(Player p, Comp c, String answer) {
        if (c.body == null || !c.body.isValid() || !p.isOnline()) {
            return;
        }

        if (answer == null) {
            this.say(c, this.confused());
            return;
        }

        String say = answer;
        String action = "none";
        String target = null;
        try {
            int a = answer.indexOf('{');
            int b = answer.lastIndexOf('}');
            JsonObject j = JsonParser.parseString(a >= 0 && b > a ? answer.substring(a, b + 1) : answer).getAsJsonObject();
            say = j.has("say") && !j.get("say").isJsonNull() ? j.get("say").getAsString() : "";
            action = j.has("action") && !j.get("action").isJsonNull() ? j.get("action").getAsString().toLowerCase(Locale.ROOT) : "none";
            target = j.has("target") && !j.get("target").isJsonNull() ? j.get("target").getAsString() : null;
        } catch (RuntimeException notJson) {
            // a plain answer: just say it
        }

        if (say.length() > 220) {
            say = say.substring(0, 220) + "...";
        }

        c.memory.add(LlmClient.Msg.assistant(answer));
        String line = say.isBlank() ? null : say;
        switch (action) {
            case "follow" -> this.order(p, c, Mode.FOLLOW, null, line == null ? "Right behind you." : line);
            case "stay" -> this.order(p, c, Mode.STAY, null, line == null ? "Holding." : line);
            case "guard" -> this.order(p, c, Mode.GUARD, null, line == null ? "I'll hold this spot." : line);
            case "defend" -> this.order(p, c, Mode.DEFEND, null, line == null ? "I've got your back." : line);
            case "move" -> this.order(p, c, Mode.MOVE, null, line == null ? "On my way." : line);
            case "peaceful" -> {
                c.passive = true;
                c.target = null;
                c.body.setTarget(null);
                this.say(c, line == null ? "Weapons down." : line);
            }
            case "aggressive" -> {
                c.passive = false;
                this.say(c, line == null ? "Ready to fight." : line);
            }
            case "dismiss" -> {
                if (line != null) {
                    this.say(c, line);
                }

                this.dismiss(p, false);
            }
            case "attack" -> {
                LivingEntity t = this.resolve(p, target == null ? "that" : target.toLowerCase(Locale.ROOT).replace("player ", "").trim());
                if (t == null || t == p) {
                    this.say(c, line == null ? "I don't see them." : line + " ...but I don't see them.");
                } else {
                    this.order(p, c, Mode.ATTACK, t, line == null ? this.attackLine(t) : line);
                }
            }
            default -> {
                if (line != null) {
                    this.say(c, line);
                }
            }
        }
    }

    // ------------------------------------------------------------------ speaking

    /** The owner and anyone within 24 blocks who sees the season hears the companion. */
    private List<Player> listeners(Comp c) {
        List<Player> out = new ArrayList<>();
        Player o = Bukkit.getPlayer(c.owner);
        if (o != null) {
            out.add(o);
        }

        if (c.body != null && c.body.isValid()) {
            for (Player p : c.body.getWorld().getPlayers()) {
                if (p != o && this.realm.sees(p) && p.getLocation().distanceSquared(c.body.getLocation()) < 24 * 24) {
                    out.add(p);
                }
            }
        }

        return out;
    }

    private void say(Comp c, String line) {
        for (Player p : this.listeners(c)) {
            p.sendMessage(Text.mm("<gold><b>" + Text.esc(c.name) + "</b></gold> <dark_gray>» <white>" + Text.esc(line)));
        }

        if (c.body != null && c.body.isValid()) {
            c.body.getWorld().playSound(c.body.getLocation(), "entity.vindicator.ambient", 0.6F, 1.2F + this.rnd.nextFloat() * 0.2F);
        }
    }

    // ------------------------------------------------------------------ behaviour, every second

    void second() {
        for (Comp c : new ArrayList<>(this.comps.values())) {
            Player o = Bukkit.getPlayer(c.owner);
            if (o == null || c.body == null || !c.body.isValid()) {
                this.comps.remove(c.owner);
                continue;
            }

            if (!this.realm.on(o)) {
                this.dismiss(o, false);
                continue;
            }

            Vindicator v = c.body;
            if (!v.getWorld().equals(o.getWorld()) || v.getLocation().distanceSquared(o.getLocation()) > 48 * 48 && (c.mode == Mode.FOLLOW || c.mode == Mode.DEFEND)) {
                v.teleport(o.getLocation());
                continue;
            }

            if (c.target != null && (!c.target.isValid() || c.target.isDead())) {
                boolean killed = c.mode == Mode.ATTACK;
                c.target = null;
                v.setTarget(null);
                if (killed) {
                    c.mode = Mode.FOLLOW;
                    this.say(c, new String[] {"Done. Back to you.", "Handled.", "That one won't bother us again."}[this.rnd.nextInt(3)]);
                }
            }

            switch (c.mode) {
                case FOLLOW -> {
                    if (v.getLocation().distanceSquared(o.getLocation()) > 16) {
                        v.getPathfinder().moveTo(o.getLocation(), 1.2);
                    }
                }
                case STAY -> {
                    if (c.anchor != null && v.getLocation().distanceSquared(c.anchor) > 4) {
                        v.getPathfinder().moveTo(c.anchor, 1.0);
                    }
                }
                case MOVE -> {
                    if (c.moveTo != null) {
                        if (v.getLocation().distanceSquared(c.moveTo) < 4) {
                            c.mode = Mode.STAY;
                            c.anchor = c.moveTo;
                            this.say(c, "In position.");
                        } else {
                            v.getPathfinder().moveTo(c.moveTo, 1.2);
                        }
                    }
                }
                case GUARD -> {
                    LivingEntity threat = c.passive ? null : this.threat(v, o, 12);
                    if (threat != null && c.target == null) {
                        c.target = threat;
                        v.setTarget(threat);
                        this.bark(c, "Intruder!");
                    } else if (c.target == null && c.anchor != null && v.getLocation().distanceSquared(c.anchor) > 9) {
                        v.getPathfinder().moveTo(c.anchor, 1.0);
                    }
                }
                case DEFEND -> {
                    LivingEntity threat = this.threat(o, o, 10);
                    if (threat != null && c.target == null && !c.passive) {
                        c.target = threat;
                        v.setTarget(threat);
                        this.bark(c, "Stay behind me!");
                    } else if (c.target == null && v.getLocation().distanceSquared(o.getLocation()) > 9) {
                        v.getPathfinder().moveTo(o.getLocation(), 1.2);
                    }
                }
                case ATTACK -> {
                    if (c.target != null) {
                        v.setTarget(c.target);
                    } else {
                        c.mode = Mode.FOLLOW;
                    }
                }
                default -> {
                }
            }

            // a little life: now and then it comments on what it sees
            if (System.currentTimeMillis() - c.lastBark > 240_000 && this.rnd.nextInt(60) == 0) {
                this.ambient(c, o);
            }
        }
    }

    /** The closest hostile (a monster aiming at the owner, or any monster when not peaceful) near a spot. */
    private LivingEntity threat(LivingEntity around, Player owner, double r) {
        LivingEntity best = null;
        double bd = r * r;
        for (Entity e : around.getNearbyEntities(r, 6, r)) {
            if (e instanceof Monster m && !this.mine(e, owner) && !e.getPersistentDataContainer().has(this.key)) {
                boolean aiming = m.getTarget() == owner;
                double d = e.getLocation().distanceSquared(around.getLocation()) - (aiming ? 50 : 0);
                if (d < bd) {
                    bd = d;
                    best = m;
                }
            }
        }

        return best;
    }

    private void bark(Comp c, String line) {
        if (System.currentTimeMillis() - c.lastBark > 8000) {
            c.lastBark = System.currentTimeMillis();
            this.say(c, line);
        }
    }

    private void ambient(Comp c, Player o) {
        c.lastBark = System.currentTimeMillis();
        long t = o.getWorld().getTime();
        List<String> l = new ArrayList<>(List.of("Quiet out here. Too quiet.", "My feet hurt. Not that you asked.", "If the Rift opens tonight, stay close."));
        if (t > 13000 && t < 23000) {
            l.add("Night's here. That's when they come out.");
        }

        if (o.getHealth() < 8) {
            l.add("You're hurt. Eat something, I'll cover you.");
        }

        if (c.body.getHealth() < 20) {
            l.add("I've taken a beating. Don't push me too hard.");
        }

        this.say(c, l.get(this.rnd.nextInt(l.size())));
    }

    // ------------------------------------------------------------------ rules

    /** It never turns on its owner (or the owner's own companion), and only targets what it was told to or what threatens. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        String owner = e.getEntity().getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
        if (owner == null) {
            // mobs leave a companion alone only if they're its owner's... no: monsters can fight companions
            return;
        }

        Comp c = this.comps.get(UUID.fromString(owner));
        if (c == null || e.getTarget() == null) {
            return;
        }

        if (e.getTarget().getUniqueId().toString().equals(owner) || c.target == null || !e.getTarget().equals(c.target)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        Player attacker = d instanceof Player pl ? pl : d instanceof Projectile pr && pr.getShooter() instanceof Player pl ? pl : null;
        String owner = e.getEntity().getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
        // owners can't hurt their own companion
        if (owner != null && attacker != null && attacker.getUniqueId().toString().equals(owner)) {
            e.setCancelled(true);
            return;
        }

        // the owner got hit: a defending or following companion strikes back
        if (e.getEntity() instanceof Player victim && d instanceof LivingEntity src) {
            Comp c = this.comps.get(victim.getUniqueId());
            LivingEntity who = attacker != null ? attacker : src;
            if (c != null && c.body != null && c.body.isValid() && who != c.body && (c.mode == Mode.DEFEND || c.mode == Mode.FOLLOW || c.passive && c.mode != Mode.STAY)) {
                c.target = who;
                c.body.setTarget(who);
                this.bark(c, "Hey! Back off!");
            }
        }

        // the companion got hit: it fights back unless peaceful
        if (owner != null && d instanceof LivingEntity src) {
            Comp c = this.comps.get(UUID.fromString(owner));
            LivingEntity who = attacker != null ? attacker : src;
            if (c != null && !c.passive && c.target == null) {
                c.target = who;
                c.body.setTarget(who);
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        String owner = e.getEntity().getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
        if (owner == null) {
            return;
        }

        e.getDrops().clear();
        UUID u = UUID.fromString(owner);
        Comp c = this.comps.remove(u);
        this.deadUntil.put(u, System.currentTimeMillis() + 120_000L);
        Player o = Bukkit.getPlayer(u);
        if (o != null && c != null) {
            o.sendMessage(Text.mm("<gold><b>" + Text.esc(c.name) + "</b></gold> <dark_gray>» <gray><i>...sorry. I'll be back."));
            o.sendMessage(Text.mm("<red>Your companion fell. <gray>Summon again in 2 minutes."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.dismiss(e.getPlayer(), false);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        Comp c = this.comps.get(e.getPlayer().getUniqueId());
        if (c != null && c.body != null && c.body.isValid() && this.realm.on(e.getPlayer())) {
            Tasks.later(10L, () -> {
                if (c.body.isValid() && e.getPlayer().isOnline()) {
                    c.body.teleport(e.getPlayer().getLocation());
                }
            });
        }
    }

    void clearAll() {
        for (Comp c : this.comps.values()) {
            if (c.body != null) {
                c.body.remove();
            }
        }

        this.comps.clear();
    }
}
