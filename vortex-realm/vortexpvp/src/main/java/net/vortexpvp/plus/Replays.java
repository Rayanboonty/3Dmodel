package net.vortexpvp.plus;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.api.MatchEndEvent;
import net.vortexpvp.api.MatchStartEvent;
import net.vortexpvp.match.Match;
import net.vortexpvp.match.MatchTeam;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Session;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Replays: ranked matches (or every match, a setting) are recorded tick by tick - position, rotation, sneaking,
 * sprinting, swings, hits, blocking, held item and armour - gzip-compressed into plugins/VortexPvP/plus/replays and
 * kept for 7 days. /replay &lt;id&gt; plays one back for the viewer only, with Mannequins wearing the players' skins in
 * the arena, and hotbar controls: play/pause, -5s / +5s, speed 0.5x/1x/2x, a follow camera and exit. Coach notes
 * and report evidence point at replay ids.
 */
public final class Replays implements Listener {
    public static final int VERSION = 1;
    private final VortexPvP plugin;
    private final PlusModule plus;
    private final NamespacedKey ctl;
    private final Map<Integer, Recording> recording = new HashMap<>();
    private final Map<UUID, Playback> watching = new HashMap<>();
    /** Replays of matches that ended since the server started (match ids start again at 1 after a restart). */
    private final Map<Integer, String> finished = new HashMap<>();

    public Replays(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
        this.ctl = new NamespacedKey(plugin, "vp_replay_ctl");
        this.dir().mkdirs();
    }

    private File dir() {
        return new File(this.plus.store().dir(), "replays");
    }

    private YamlConfiguration index() {
        return this.plus.store().doc("replays");
    }

    // ------------------------------------------------------------------ the format

    /** One recorded match in memory: actor names and ids, then per tick per actor 6 floats (x y z yaw pitch flags). */
    public static final class Tape {
        public final List<UUID> ids = new ArrayList<>();
        public final List<String> names = new ArrayList<>();
        public String world = "";
        public String kit = "";
        public double ox;
        public double oy;
        public double oz;
        /** frames.get(tick)[actor] = {x, y, z, yaw, pitch, flags} or null when the actor was not there. */
        public final List<float[][]> frames = new ArrayList<>();
        /** "tick:actor" -> material names: held item, or the 4 armour pieces joined by ','. */
        public final Map<String, String> held = new HashMap<>();
        public final Map<String, String> armor = new HashMap<>();

        public int length() {
            return this.frames.size();
        }
    }

    public static final int F_SNEAK = 1;
    public static final int F_SPRINT = 2;
    public static final int F_SWING = 4;
    public static final int F_HURT = 8;
    public static final int F_BLOCK = 16;
    public static final int F_GONE = 32;

    public static byte[] encode(Tape t) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(0x56525031);
            out.writeShort(VERSION);
            out.writeUTF(t.world);
            out.writeUTF(t.kit);
            out.writeDouble(t.ox);
            out.writeDouble(t.oy);
            out.writeDouble(t.oz);
            out.writeByte(t.ids.size());
            for (int i = 0; i < t.ids.size(); i++) {
                out.writeLong(t.ids.get(i).getMostSignificantBits());
                out.writeLong(t.ids.get(i).getLeastSignificantBits());
                out.writeUTF(t.names.get(i));
            }

            out.writeInt(t.frames.size());
            for (int tick = 0; tick < t.frames.size(); tick++) {
                float[][] f = t.frames.get(tick);
                for (int a = 0; a < t.ids.size(); a++) {
                    float[] v = f[a];
                    if (v == null) {
                        out.writeByte(0);
                        continue;
                    }

                    String h = t.held.get(tick + ":" + a);
                    String ar = t.armor.get(tick + ":" + a);
                    out.writeByte(1 | (h != null ? 2 : 0) | (ar != null ? 4 : 0));
                    out.writeFloat(v[0]);
                    out.writeFloat(v[1]);
                    out.writeFloat(v[2]);
                    out.writeShort(Math.round(v[3] * 100.0F));
                    out.writeShort(Math.round(v[4] * 100.0F));
                    out.writeByte((int)v[5]);
                    if (h != null) {
                        out.writeUTF(h);
                    }

                    if (ar != null) {
                        out.writeUTF(ar);
                    }
                }
            }
        }

        return bytes.toByteArray();
    }

    public static Tape decode(byte[] data) throws IOException {
        Tape t = new Tape();
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
            if (in.readInt() != 0x56525031) {
                throw new IOException("not a replay");
            }

            in.readShort();
            t.world = in.readUTF();
            t.kit = in.readUTF();
            t.ox = in.readDouble();
            t.oy = in.readDouble();
            t.oz = in.readDouble();
            int n = in.readUnsignedByte();
            for (int i = 0; i < n; i++) {
                t.ids.add(new UUID(in.readLong(), in.readLong()));
                t.names.add(in.readUTF());
            }

            int ticks = in.readInt();
            for (int tick = 0; tick < ticks; tick++) {
                float[][] f = new float[n][];
                for (int a = 0; a < n; a++) {
                    int has = in.readUnsignedByte();
                    if ((has & 1) == 0) {
                        continue;
                    }

                    float[] v = new float[6];
                    v[0] = in.readFloat();
                    v[1] = in.readFloat();
                    v[2] = in.readFloat();
                    v[3] = in.readShort() / 100.0F;
                    v[4] = in.readShort() / 100.0F;
                    v[5] = in.readUnsignedByte();
                    f[a] = v;
                    if ((has & 2) != 0) {
                        t.held.put(tick + ":" + a, in.readUTF());
                    }

                    if ((has & 4) != 0) {
                        t.armor.put(tick + ":" + a, in.readUTF());
                    }
                }

                t.frames.add(f);
            }
        }

        return t;
    }

    /** Self test: a made-up tape survives encode + decode unchanged. */
    public static String codecCheck() {
        try {
            Tape t = new Tape();
            t.world = "w";
            t.kit = "sword";
            t.ox = 100.5;
            t.oy = 64.0;
            t.oz = -30.25;
            t.ids.add(UUID.randomUUID());
            t.ids.add(UUID.randomUUID());
            t.names.add("Alpha");
            t.names.add("Beta");
            for (int i = 0; i < 200; i++) {
                t.frames.add(new float[][]{{i * 0.1F, 1.5F, -i * 0.05F, (i * 7) % 360 - 180, 12.5F, i % 2 == 0 ? F_SPRINT : F_SWING | F_HURT}, i == 50 ? null : new float[]{2.0F, 0.0F, 3.0F, 90.0F, -10.0F, F_SNEAK}});
            }

            t.held.put("0:0", "DIAMOND_SWORD");
            t.armor.put("0:1", "DIAMOND_HELMET,DIAMOND_CHESTPLATE,DIAMOND_LEGGINGS,DIAMOND_BOOTS");
            Tape b = decode(encode(t));
            if (b.length() != 200 || !b.names.equals(t.names) || !b.ids.equals(t.ids) || b.ox != t.ox || !b.kit.equals("sword")) {
                return "header differs";
            }

            for (int i = 0; i < 200; i++) {
                for (int a = 0; a < 2; a++) {
                    float[] x = t.frames.get(i)[a];
                    float[] y = b.frames.get(i)[a];
                    if (x == null != (y == null)) {
                        return "frame " + i + " presence";
                    }

                    if (x != null) {
                        for (int k = 0; k < 6; k++) {
                            if (Math.abs(x[k] - y[k]) > 0.01F) {
                                return "frame " + i + " value " + k;
                            }
                        }
                    }
                }
            }

            return Objects.equals(b.held.get("0:0"), "DIAMOND_SWORD") && b.armor.get("0:1").startsWith("DIAMOND_HELMET") ? null : "items";
        } catch (IOException e) {
            return e.toString();
        }
    }

    // ------------------------------------------------------------------ recording

    private static final class Recording {
        final Match match;
        final String id;
        final Tape tape = new Tape();
        final List<UUID> actors;
        final Map<UUID, Integer> flags = new HashMap<>();
        final Map<UUID, String> lastHeld = new HashMap<>();
        final Map<UUID, String> lastArmor = new HashMap<>();
        int hits;

        Recording(Match m, String id, List<UUID> actors) {
            this.match = m;
            this.id = id;
            this.actors = actors;
        }
    }

    private boolean shouldRecord(Match m) {
        String mode = this.plus.config().s("replays.record", "ranked");
        return this.plus.config().on("replays") && ("all".equalsIgnoreCase(mode) || "ranked".equalsIgnoreCase(mode) && m.ranked);
    }

    private String nextId() {
        YamlConfiguration y = this.index();
        int n = y.getInt("next", 1);
        y.set("next", n + 1);
        this.plus.store().dirty("replays");
        return Integer.toString(n + 1295, 36).toUpperCase(java.util.Locale.ROOT);
    }

    /** The replay id of a match being recorded (or just finished), or null. */
    public String idFor(int matchId) {
        Recording r = this.recording.get(matchId);
        if (r != null) {
            return r.id;
        }

        return this.finished.get(matchId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStart(MatchStartEvent e) {
        Match m = e.match();
        if (!this.shouldRecord(m) || m.instance() == null || this.recording.containsKey(m.id)) {
            return;
        }

        List<UUID> actors = new ArrayList<>();
        for (MatchTeam t : m.teams) {
            actors.addAll(t.members);
        }

        if (actors.size() > 16) {
            return;
        }

        Recording r = new Recording(m, this.nextId(), actors);
        Location c = m.instance().center();
        r.tape.world = c.getWorld().getName();
        r.tape.kit = m.kit.id;
        r.tape.ox = c.getBlockX();
        r.tape.oy = c.getBlockY();
        r.tape.oz = c.getBlockZ();
        for (UUID u : actors) {
            r.tape.ids.add(u);
            String n = Bukkit.getOfflinePlayer(u).getName();
            r.tape.names.add(n == null ? "?" : n);
        }

        this.recording.put(m.id, r);
    }

    /** Every tick: one frame for each match being recorded. */
    public void tick() {
        Iterator<Recording> it = this.recording.values().iterator();
        while (it.hasNext()) {
            Recording r = it.next();
            if (r.match.state() == Match.State.ENDED) {
                continue;
            }

            if (r.tape.length() > this.plus.config().i("replays.max-minutes", 15) * 1200) {
                it.remove();
                continue;
            }

            int tick = r.tape.length();
            float[][] f = new float[r.actors.size()][];
            for (int a = 0; a < r.actors.size(); a++) {
                UUID u = r.actors.get(a);
                Player p = Bukkit.getPlayer(u);
                if (p == null || !p.getWorld().getName().equals(r.tape.world)) {
                    continue;
                }

                Location l = p.getLocation();
                int fl = r.flags.getOrDefault(u, 0);
                r.flags.remove(u);
                if (p.isSneaking()) {
                    fl |= F_SNEAK;
                }

                if (p.isSprinting()) {
                    fl |= F_SPRINT;
                }

                if (p.isBlocking()) {
                    fl |= F_BLOCK;
                }

                if (p.getGameMode() == GameMode.SPECTATOR || !r.match.isAlive(u)) {
                    fl |= F_GONE;
                }

                f[a] = new float[]{(float)(l.getX() - r.tape.ox), (float)(l.getY() - r.tape.oy), (float)(l.getZ() - r.tape.oz), l.getYaw(), l.getPitch(), fl};
                String held = p.getInventory().getItemInMainHand().getType().name();
                if (!held.equals(r.lastHeld.get(u))) {
                    r.lastHeld.put(u, held);
                    r.tape.held.put(tick + ":" + a, held);
                }

                ItemStack[] arm = p.getInventory().getArmorContents();
                StringBuilder sb = new StringBuilder();
                for (int i = 3; i >= 0; i--) {
                    sb.append(i == 3 ? "" : ",").append(arm[i] == null ? "AIR" : arm[i].getType().name());
                }

                String armor = sb.toString();
                if (!armor.equals(r.lastArmor.get(u))) {
                    r.lastArmor.put(u, armor);
                    r.tape.armor.put(tick + ":" + a, armor);
                }
            }

            r.tape.frames.add(f);
        }

        for (Playback pb : new ArrayList<>(this.watching.values())) {
            pb.tick();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() == PlayerAnimationType.ARM_SWING && !this.recording.isEmpty()) {
            this.flag(e.getPlayer().getUniqueId(), F_SWING);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player v && !this.recording.isEmpty()) {
            Recording r = this.flag(v.getUniqueId(), F_HURT);
            if (r != null) {
                r.hits++;
            }
        }
    }

    private Recording flag(UUID u, int bit) {
        for (Recording r : this.recording.values()) {
            if (r.actors.contains(u)) {
                r.flags.merge(u, bit, (a, b) -> a | b);
                return r;
            }
        }

        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnd(MatchEndEvent e) {
        Recording r = this.recording.remove(e.match().id);
        if (r == null) {
            return;
        }

        if ("CANCELLED".equals(e.reason()) || r.tape.length() < 40) {
            return;
        }

        YamlConfiguration y = this.index();
        String base = "list." + r.id;
        y.set(base + ".match", e.match().id);
        y.set(base + ".kit", r.tape.kit);
        y.set(base + ".ranked", e.match().ranked);
        y.set(base + ".at", System.currentTimeMillis());
        y.set(base + ".secs", r.tape.length() / 20);
        y.set(base + ".players", r.tape.names);
        List<String> ids = new ArrayList<>();
        for (UUID u : r.tape.ids) {
            ids.add(u.toString());
        }

        y.set(base + ".uuids", ids);
        String winner = "";
        if (e.winner() != null && !e.winner().members.isEmpty()) {
            String n = Bukkit.getOfflinePlayer(e.winner().members.get(0)).getName();
            winner = n == null ? "" : n;
        }

        y.set(base + ".winner", winner);
        int bestCombo = 0;
        for (UUID u : r.tape.ids) {
            net.vortexpvp.match.MatchStats st = e.match().stats(u);
            if (st != null) {
                bestCombo = Math.max(bestCombo, st.longestCombo);
            }
        }

        y.set(base + ".combo", bestCombo);
        y.set(base + ".hits", r.hits);
        // the match-of-the-week score: big combos, long close fights, ranked
        y.set(base + ".score", bestCombo * 3 + Math.min(120, r.tape.length() / 20) / 4 + r.hits / 10 + (e.match().ranked ? 10 : 0));
        this.finished.put(e.match().id, r.id);
        this.plus.store().dirty("replays");
        String id = r.id;
        Tape tape = r.tape;
        Tasks.async(() -> {
            try {
                Files.write(new File(this.dir(), id + ".vrp").toPath(), encode(tape));
            } catch (IOException ex) {
                this.plugin.getLogger().warning("Could not save replay " + id + ": " + ex);
            }
        });
    }

    /** Deletes replays older than the keep time (7 days by default). */
    public void purge() {
        YamlConfiguration y = this.index();
        ConfigurationSection list = y.getConfigurationSection("list");
        long keep = this.plus.config().i("replays.keep-days", 7) * 86400000L;
        int removed = 0;
        if (list != null) {
            for (String id : new ArrayList<>(list.getKeys(false))) {
                if (y.getStringList("list." + id + ".pinned").isEmpty() && !y.getBoolean("list." + id + ".keep")
                    && System.currentTimeMillis() - y.getLong("list." + id + ".at") > keep) {
                    y.set("list." + id, null);
                    new File(this.dir(), id + ".vrp").delete();
                    removed++;
                }
            }
        }

        if (removed > 0) {
            this.plus.store().dirty("replays");
        }
    }

    public List<String> idsOf(UUID u) {
        List<String> out = new ArrayList<>();
        ConfigurationSection list = this.index().getConfigurationSection("list");
        if (list != null) {
            for (String id : list.getKeys(false)) {
                if (list.getStringList(id + ".uuids").contains(u.toString())) {
                    out.add(id);
                }
            }
        }

        out.sort((a, b) -> Long.compare(this.index().getLong("list." + b + ".at"), this.index().getLong("list." + a + ".at")));
        return out;
    }

    public ConfigurationSection info(String id) {
        return this.index().getConfigurationSection("list." + id.toUpperCase(java.util.Locale.ROOT));
    }

    public Map<String, ConfigurationSection> all() {
        Map<String, ConfigurationSection> out = new LinkedHashMap<>();
        ConfigurationSection list = this.index().getConfigurationSection("list");
        if (list != null) {
            for (String id : list.getKeys(false)) {
                out.put(id, list.getConfigurationSection(id));
            }
        }

        return out;
    }

    /** A timestamped coach note saved into the replay of a match. */
    public boolean note(int matchId, String author, String text) {
        String id = this.idFor(matchId);
        if (id == null) {
            return false;
        }

        Recording r = this.recording.get(matchId);
        int secs = r == null ? 0 : r.tape.length() / 20;
        YamlConfiguration y = this.index();
        List<String> notes = new ArrayList<>(y.getStringList("notes." + id));
        notes.add(secs + "|" + author + "|" + text);
        y.set("notes." + id, notes);
        this.plus.store().dirty("replays");
        return true;
    }

    public void keep(String id, boolean keep) {
        this.index().set("list." + id + ".keep", keep);
        this.plus.store().dirty("replays");
    }

    // ------------------------------------------------------------------ playback

    public void list(Player p) {
        List<String> mine = this.idsOf(p.getUniqueId());
        if (mine.isEmpty()) {
            p.sendMessage(Text.mm("<gray>No replays of your matches yet. Ranked matches are recorded and kept for "
                + this.plus.config().i("replays.keep-days", 7) + " days."));
            return;
        }

        p.sendMessage(Text.mm("<gold><b>Your replays</b>"));
        for (int i = 0; i < Math.min(10, mine.size()); i++) {
            String id = mine.get(i);
            ConfigurationSection s = this.info(id);
            p.sendMessage(Text.mm("<click:run_command:'/replay " + id + "'><aqua>[#" + id + "]</aqua></click> <white>" + Text.esc(String.join(" vs ", s.getStringList("players")))
                + " <dark_gray>• <gray>" + s.getString("kit") + " <dark_gray>• <gray>" + s.getInt("secs") + "s"));
        }
    }

    public void watch(Player p, String rawId) {
        String id = rawId.toUpperCase(java.util.Locale.ROOT).replace("#", "");
        if (this.watching.containsKey(p.getUniqueId())) {
            this.stop(p);
        }

        Session s = this.plugin.sessions().get(p);
        if (s.state != PlayerState.LOBBY) {
            p.sendMessage(Text.mm("<red>Go to the lobby first to watch a replay."));
            return;
        }

        ConfigurationSection info = this.info(id);
        File f = new File(this.dir(), id + ".vrp");
        if (info == null || !f.isFile()) {
            p.sendMessage(Text.mm("<red>No replay #" + Text.esc(id) + " (they are kept for " + this.plus.config().i("replays.keep-days", 7) + " days)."));
            return;
        }

        p.sendMessage(Text.mm("<gray>Loading replay <aqua>#" + id + "</aqua>..."));
        Tasks.async(() -> {
            try {
                Tape t = decode(Files.readAllBytes(f.toPath()));
                Tasks.sync(() -> this.begin(p, id, t));
            } catch (IOException ex) {
                Tasks.sync(() -> p.sendMessage(Text.mm("<red>That replay could not be read.")));
            }
        });
    }

    private void begin(Player p, String id, Tape t) {
        World w = Bukkit.getWorld(t.world);
        if (!p.isOnline() || w == null || t.length() == 0) {
            if (p.isOnline()) {
                p.sendMessage(Text.mm("<red>The arena of that replay is gone."));
            }

            return;
        }

        Session s = this.plugin.sessions().get(p);
        if (s.state != PlayerState.LOBBY) {
            return;
        }

        Playback pb = new Playback(p, id, t, w);
        this.watching.put(p.getUniqueId(), pb);
        s.state = PlayerState.BOT;
        p.getInventory().clear();
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o != p) {
                o.hidePlayer(this.plugin, p);
            }
        }

        pb.spawn();
        pb.controls();
        // start above the middle of all players, looking down at the first one, so everybody is in view
        Location first = pb.at(0, 0);
        if (first != null) {
            double x = 0.0;
            double y = 0.0;
            double z = 0.0;
            int n = 0;
            for (int a = 0; a < t.ids.size(); a++) {
                Location l = pb.at(0, a);
                if (l != null) {
                    x += l.getX();
                    y += l.getY();
                    z += l.getZ();
                    n++;
                }
            }

            double spread = 0.0;
            for (int a = 0; a < t.ids.size(); a++) {
                Location l = pb.at(0, a);
                if (l != null) {
                    spread = Math.max(spread, Math.hypot(l.getX() - x / n, l.getZ() - z / n));
                }
            }

            Location cam = new Location(w, x / n, y / n + Math.max(4.0, Math.min(18.0, spread * 0.7)), z / n);
            org.bukkit.util.Vector back = first.clone().subtract(cam).toVector().setY(0);
            if (back.lengthSquared() < 0.01) {
                back = new org.bukkit.util.Vector(0, 0, 1);
            }

            cam.add(back.normalize().multiply(-Math.min(10.0, spread * 0.4 + 2.0)));
            cam.setDirection(new Location(w, x / n, y / n, z / n).toVector().subtract(cam.toVector()));
            Tp.to(p, cam);
        }

        p.showBossBar(pb.bar);
        this.plus.progression().track(p.getUniqueId(), "replays_watched", null, 1L);
        p.sendMessage(Text.mm("<aqua>🎬 Watching replay #" + id + " <gray>- use the hotbar to control it."));
    }

    public boolean watchingReplay(UUID u) {
        return this.watching.containsKey(u);
    }

    public void stop(Player p) {
        Playback pb = this.watching.remove(p.getUniqueId());
        if (pb == null) {
            return;
        }

        pb.clear();
        p.hideBossBar(pb.bar);
        if (p.isOnline()) {
            p.setFlying(false);
            p.setAllowFlight(false);
            this.plugin.lobby().sendToLobby(p);
            this.plugin.lobby().refreshVisibility(p);
            for (Player o : Bukkit.getOnlinePlayers()) {
                this.plugin.lobby().refreshVisibility(o);
            }
        }
    }

    public void stopAll() {
        for (UUID u : new ArrayList<>(this.watching.keySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                this.stop(p);
            } else {
                Playback pb = this.watching.remove(u);
                if (pb != null) {
                    pb.clear();
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Playback pb = this.watching.remove(e.getPlayer().getUniqueId());
        if (pb != null) {
            pb.clear();
            this.plugin.sessions().get(e.getPlayer()).state = PlayerState.LOBBY;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onControl(PlayerInteractEvent e) {
        Playback pb = this.watching.get(e.getPlayer().getUniqueId());
        if (pb == null) {
            return;
        }

        e.setCancelled(true);
        if (e.getAction() == Action.PHYSICAL || e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }

        ItemStack it = e.getItem();
        String a = it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(this.ctl, PersistentDataType.STRING);
        if (a == null) {
            return;
        }

        Player p = e.getPlayer();
        switch (a) {
            case "pause" -> pb.paused = !pb.paused;
            case "back" -> pb.pos = Math.max(0.0, pb.pos - 100.0);
            case "fwd" -> pb.pos = Math.min(pb.tape.length() - 1, pb.pos + 100.0);
            case "speed" -> pb.speed = pb.speed >= 2.0 ? 0.5 : pb.speed * 2.0;
            case "follow" -> pb.follow = pb.follow + 1 >= pb.tape.ids.size() ? -1 : pb.follow + 1;
            case "exit" -> {
                this.stop(p);
                return;
            }
            default -> {
            }
        }

        Snd.click(p);
        pb.controls();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (this.watching.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onViewerDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && this.watching.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                Playback pb = this.watching.get(p.getUniqueId());
                Location l = pb.at((int)pb.pos, 0);
                if (l != null) {
                    Tp.to(p, l.clone().add(0.0, 3.0, 0.0));
                }
            }
        } else if (e.getEntity() instanceof Mannequin m && m.getPersistentDataContainer().has(this.ctl)) {
            e.setCancelled(true);
        }
    }

    private final class Playback {
        final Player viewer;
        final String id;
        final Tape tape;
        final World world;
        final List<Mannequin> actors = new ArrayList<>();
        final BossBar bar = BossBar.bossBar(Component.empty(), 0.0F, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        final List<String[]> notes = new ArrayList<>();
        double pos;
        double speed = 1.0;
        boolean paused;
        int follow = -1;
        int lastShown = -1;
        int endedFor;

        Playback(Player viewer, String id, Tape tape, World world) {
            this.viewer = viewer;
            this.id = id;
            this.tape = tape;
            this.world = world;
            for (String n : Replays.this.index().getStringList("notes." + id)) {
                String[] parts = n.split("\\|", 3);
                if (parts.length == 3) {
                    this.notes.add(parts);
                }
            }
        }

        Location at(int tick, int actor) {
            if (tick < 0 || tick >= this.tape.length()) {
                return null;
            }

            float[] v = this.tape.frames.get(tick)[actor];
            if (v == null) {
                for (int t = tick; t < Math.min(this.tape.length(), tick + 200); t++) {
                    if (this.tape.frames.get(t)[actor] != null) {
                        v = this.tape.frames.get(t)[actor];
                        break;
                    }
                }
            }

            return v == null ? null : new Location(this.world, this.tape.ox + v[0], this.tape.oy + v[1], this.tape.oz + v[2], v[3], v[4]);
        }

        void spawn() {
            for (int a = 0; a < this.tape.ids.size(); a++) {
                Location l = this.at(0, a);
                if (l == null) {
                    l = new Location(this.world, this.tape.ox, this.tape.oy, this.tape.oz);
                }

                UUID u = this.tape.ids.get(a);
                String name = this.tape.names.get(a);
                Mannequin m = this.world.spawn(l, Mannequin.class, x -> {
                    x.setPersistent(false);
                    x.setVisibleByDefault(false);
                    x.setGravity(false);
                    x.setSilent(true);
                    x.setCollidable(false);
                    x.setImmovable(true);
                    x.getPersistentDataContainer().set(Replays.this.ctl, PersistentDataType.STRING, "actor");
                    x.setProfile(ResolvableProfile.resolvableProfile().name(name).uuid(u).build());
                    x.customName(Text.mm("<white>" + Text.esc(name)));
                    x.setCustomNameVisible(true);
                    try {
                        x.setDescription(null);
                    } catch (RuntimeException var4) {
                        x.setDescription(Component.empty());
                    }
                });
                this.viewer.showEntity(Replays.this.plugin, m);
                this.actors.add(m);
            }
        }

        void controls() {
            org.bukkit.inventory.PlayerInventory inv = this.viewer.getInventory();
            inv.setItem(0, this.ctl(this.paused ? Material.LIME_DYE : Material.GRAY_DYE, this.paused ? "<green>▶ Play" : "<yellow>⏸ Pause", "pause"));
            inv.setItem(1, this.ctl(Material.ARROW, "<white>⏪ Back 5 seconds", "back"));
            inv.setItem(2, this.ctl(Material.SPECTRAL_ARROW, "<white>⏩ Forward 5 seconds", "fwd"));
            inv.setItem(3, this.ctl(Material.CLOCK, "<aqua>Speed: <white>" + this.speed + "x", "speed"));
            inv.setItem(4, this.ctl(Material.ENDER_EYE, "<light_purple>Camera: <white>" + (this.follow < 0 ? "free" : "follow " + this.tape.names.get(this.follow)), "follow"));
            inv.setItem(8, this.ctl(Material.BARRIER, "<red>Exit replay", "exit"));
        }

        ItemStack ctl(Material m, String name, String action) {
            ItemStack it = Items.of(m).name(name).build();
            it.editMeta(meta -> meta.getPersistentDataContainer().set(Replays.this.ctl, PersistentDataType.STRING, action));
            return it;
        }

        void tick() {
            if (!this.viewer.isOnline()) {
                return;
            }

            int len = this.tape.length();
            if (!this.paused) {
                this.pos = Math.min(len - 1, this.pos + this.speed);
            }

            int t = (int)this.pos;
            boolean hit = false;
            for (int a = 0; a < this.actors.size(); a++) {
                Mannequin m = this.actors.get(a);
                if (!m.isValid()) {
                    continue;
                }

                float[] v = this.tape.frames.get(t)[a];
                if (v == null) {
                    continue;
                }

                int fl = (int)v[5];
                Location l = new Location(this.world, this.tape.ox + v[0], this.tape.oy + v[1], this.tape.oz + v[2], v[3], v[4]);
                m.teleport(l);
                m.setPose((fl & F_SNEAK) != 0 ? Pose.SNEAKING : Pose.STANDING, true);
                m.setInvisible((fl & F_GONE) != 0);
                if (!this.paused && t != this.lastShown) {
                    if ((fl & F_SWING) != 0) {
                        m.swingMainHand();
                    }

                    if ((fl & F_HURT) != 0) {
                        m.playHurtAnimation(0.0F);
                        hit = true;
                    }
                }

                // items: the last change at or before this tick
                if (t != this.lastShown) {
                    this.equip(m, a, t);
                }
            }

            if (this.follow >= 0 && this.follow < this.actors.size() && t != this.lastShown) {
                Location l = this.at(t, this.follow);
                if (l != null) {
                    Location cam = l.clone().add(l.getDirection().setY(0).normalize().multiply(-3.5)).add(0.0, 1.6, 0.0);
                    cam.setYaw(l.getYaw());
                    cam.setPitch(15.0F);
                    Tp.to(this.viewer, cam);
                }
            }

            int secs = t / 20;
            for (String[] n : this.notes) {
                try {
                    int at = Integer.parseInt(n[0]);
                    if (at == secs && this.lastShown / 20 != secs) {
                        this.viewer.sendMessage(Text.mm("<gold>📝 Coach " + Text.esc(n[1]) + ": <white>" + Text.esc(n[2])));
                    }
                } catch (NumberFormatException var7) {
                }
            }

            this.lastShown = t;
            this.bar.progress(Math.max(0.0F, Math.min(1.0F, (float)t / Math.max(1, len - 1))));
            this.bar.name(Text.mm("<aqua>🎬 #" + this.id + " <white>" + secs / 60 + ":" + String.format("%02d", secs % 60) + " <gray>/ " + len / 20 / 60 + ":"
                + String.format("%02d", len / 20 % 60) + " <dark_gray>• <white>" + this.speed + "x" + (this.paused ? " <yellow>⏸" : "") + (hit ? " <red>✦" : "")));
            if (t >= len - 1) {
                if (++this.endedFor > 200) {
                    Replays.this.stop(this.viewer);
                }
            } else {
                this.endedFor = 0;
            }
        }

        void equip(Mannequin m, int a, int tick) {
            EntityEquipment eq = m.getEquipment();
            String held = null;
            String armor = null;
            for (int t = tick; t >= 0 && (held == null || armor == null); t--) {
                if (held == null) {
                    held = this.tape.held.get(t + ":" + a);
                }

                if (armor == null) {
                    armor = this.tape.armor.get(t + ":" + a);
                }
            }

            Material h = held == null ? null : Material.matchMaterial(held);
            eq.setItemInMainHand(h == null || !h.isItem() ? null : new ItemStack(h));
            if (armor != null) {
                String[] parts = armor.split(",");
                ItemStack[] pieces = new ItemStack[4];
                for (int i = 0; i < 4 && i < parts.length; i++) {
                    Material am = Material.matchMaterial(parts[i]);
                    pieces[3 - i] = am == null || am == Material.AIR ? null : new ItemStack(am);
                }

                eq.setArmorContents(pieces);
            }
        }

        void clear() {
            for (Mannequin m : this.actors) {
                m.remove();
            }

            this.actors.clear();
        }
    }
}
