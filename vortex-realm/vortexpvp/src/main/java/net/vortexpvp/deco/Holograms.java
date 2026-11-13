package net.vortexpvp.deco;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.dialog.Dialogs;
import net.vortexpvp.dialog.LobbyDialogs;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class Holograms implements TabExecutor, Listener {
    private static final double LINE = 0.27;
    private final VortexPvP plugin;
    private final NamespacedKey key;
    private final File file;
    private final Map<String, Holograms.Holo> holos = new LinkedHashMap<>();
    private final Map<String, Holograms.Live> live = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private int counter;
    private int ticks;
    private static final List<Holograms.Preset> PRESETS = List.of(
        new Holograms.Preset(
            "welcome",
            "Welcome",
            Holograms.Design.EMBER,
            "",
            "",
            "#FF7A1A",
            List.of(
                "WELCOME TO <server>",
                "<#B9A89A>Competitive Minecraft PvP",
                "<#B9A89A>Ranked duels <dark_gray>•</dark_gray> tier tests <dark_gray>•</dark_gray> tournaments",
                "<#FFB547><online></#FFB547> <#B9A89A>players online",
                "<#B9A89A>IP <white><ip>"
            )
        ),
        new Holograms.Preset(
            "discord",
            "Join our Discord",
            Holograms.Design.GLASS,
            "blue_ice",
            "discord",
            "#5865F2",
            List.of(
                "<#7A86FF>JOIN OUR DISCORD",
                "<white><discord>",
                "<gray>Events, giveaways, tier test results",
                "<gray>and support - every day",
                "<blink>▶ Click for the link"
            )
        ),
        new Holograms.Preset(
            "tiers",
            "Tier Tests",
            Holograms.Design.SHOWCASE,
            "diamond_sword",
            "rankup",
            "#FFB547",
            List.of(
                "<gradient:#FFE27A:#FF6F00>SEE OUR TIER TESTS</gradient>",
                "<gray>Win ranked duels to earn points",
                "<gray>Reach a new tier, then win the <white>Tier Test",
                "<#9E9E9E>LT5 <dark_gray>→ <#8BE08B>LT4 <dark_gray>→ <#2E9BFF>HT3 <dark_gray>→ <#C77DFF>MT2 <dark_gray>→ <#FFAA00>HT1",
                "<blink>▶ Click to see your tier tests"
            )
        ),
        new Holograms.Preset(
            "tournaments",
            "Tournaments",
            Holograms.Design.NEON,
            "golden_helmet",
            "event",
            "#FF3D1F",
            List.of("<white>⚔ TOURNAMENTS ⚔", "<event>", "<#FFE3D6>Qualifiers, a bracket in the colosseum", "<blink>▶ Click to join or watch")
        ),
        new Holograms.Preset(
            "practice",
            "Practice PvP",
            Holograms.Design.FLOAT,
            "netherite_sword",
            "lobby",
            "#FF7A1A",
            List.of(
                "<gradient:#FF4A1C:#FFB547>PRACTICE PVP</gradient>",
                "<gray><playing> fighting <dark_gray>•</dark_gray> <queued> in queue <dark_gray>•</dark_gray> <ffa> in FFA",
                "<gray>Sword, Axe, Mace, Crystal, UHC and more",
                "<blink>▶ Click to play"
            )
        ),
        new Holograms.Preset(
            "website",
            "Our Website",
            Holograms.Design.GLASS,
            "",
            "website",
            "#FFB547",
            List.of("<#FFB547>OUR WEBSITE", "<gray>Live leaderboards, tier lists,", "<gray>ranks and every player's profile", "<blink>▶ Click for the link")
        ),
        new Holograms.Preset(
            "ranks",
            "Ranks",
            Holograms.Design.SHOWCASE,
            "nether_star",
            "website",
            "#FFC14D",
            List.of(
                "<gradient:#FFC14D:#FF7A00>SERVER RANKS</gradient>",
                "<#7CFF6B>VIP <dark_gray>•</dark_gray> <#FFB347>MVP <dark_gray>•</dark_gray> <#FFCB5C>LEGEND <dark_gray>•</dark_gray> <#A6C8FF>FROST <dark_gray>•</dark_gray> <#FF6B81>SUPREME",
                "<gray>Tags, homes, lobby fly and more",
                "<blink>▶ Click to see them"
            )
        )
    );

    public Holograms(VortexPvP plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "holo");
        this.file = new File(plugin.getDataFolder(), "text-holograms.yml");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        this.counter = y.getInt("counter", 0);
        ConfigurationSection s = y.getConfigurationSection("holograms");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection d = s.getConfigurationSection(id);
                if (d != null) {
                    Holograms.Holo h = new Holograms.Holo(id);
                    h.world = d.getString("world", "world");
                    h.x = d.getDouble("x");
                    h.y = d.getDouble("y");
                    h.z = d.getDouble("z");

                    try {
                        h.design = Holograms.Design.valueOf(d.getString("design", "GLASS").toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException var8) {
                        h.design = Holograms.Design.GLASS;
                    }

                    h.lines = new ArrayList<>(d.getStringList("lines"));
                    h.icon = d.getString("icon", "");
                    h.click = d.getString("click", "");
                    h.accent = d.getString("accent", "#FF7A1A");
                    h.scale = (float)d.getDouble("scale", 1.0);
                    h.yaw = (float)d.getDouble("yaw", 0.0);
                    this.holos.put(id, h);
                }
            }
        }

        this.clearLeftovers();

        for (Holograms.Holo h : this.holos.values()) {
            this.spawnIfLoaded(h);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options()
            .setHeader(
                List.of(
                    "Holograms placed with /holo (edit them in game).",
                    "Placeholders: <online> <max> <playing> <queued> <ffa> <event> <discord> <ip> <server> <website>",
                    "<blink> (flashes) - designs: CLASSIC GLASS EMBER NEON FLOAT SHOWCASE - click: a command, no slash."
                )
            );
        y.set("counter", this.counter);

        for (Holograms.Holo h : this.holos.values()) {
            String b = "holograms." + h.id + ".";
            y.set(b + "world", h.world);
            y.set(b + "x", h.x);
            y.set(b + "y", h.y);
            y.set(b + "z", h.z);
            y.set(b + "design", h.design.name());
            y.set(b + "lines", h.lines);
            y.set(b + "icon", h.icon);
            y.set(b + "click", h.click);
            y.set(b + "accent", h.accent);
            y.set(b + "scale", h.scale);
            y.set(b + "yaw", h.yaw);
        }

        try {
            y.save(this.file);
        } catch (IOException var5) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save holograms.yml", (Throwable)var5);
        }
    }

    public int count() {
        return this.holos.size();
    }

    private void clearLeftovers() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(this.key, PersistentDataType.STRING)) {
                    e.remove();
                }
            }
        }
    }

    public void shutdown() {
        for (Holograms.Holo h : this.holos.values()) {
            this.despawn(h);
        }

        this.save();
    }

    private String placeholders(String line) {
        String out = line;
        if (line.contains("<online>")) {
            out = line.replace("<online>", String.valueOf(Bukkit.getOnlinePlayers().size()));
        }

        if (out.contains("<max>")) {
            out = out.replace("<max>", String.valueOf(Bukkit.getMaxPlayers()));
        }

        if (out.contains("<playing>")) {
            int n = this.plugin.sessions().playersIn(PlayerState.MATCH).size();
            out = out.replace("<playing>", String.valueOf(n));
        }

        if (out.contains("<queued>")) {
            out = out.replace("<queued>", String.valueOf(this.plugin.queues().total()));
        }

        if (out.contains("<ffa>")) {
            out = out.replace("<ffa>", String.valueOf(this.plugin.ffa() == null ? 0 : this.plugin.ffa().playing()));
        }

        if (out.contains("<event>")) {
            out = out.replace("<event>", this.plugin.events() == null ? "<gray>No tournament right now" : this.plugin.events().status());
        }

        if (out.contains("<discord>")) {
            out = out.replace("<discord>", Text.esc(Cfg.s(Opt.LINK_DISCORD).replaceFirst("^https?://", "")));
        }

        if (out.contains("<ip>")) {
            out = out.replace("<ip>", Text.esc(Cfg.s(Opt.SERVER_IP)));
        }

        if (out.contains("<server>")) {
            out = out.replace("<server>", Text.esc(Cfg.s(Opt.SERVER_NAME)));
        }

        if (out.contains("<website>")) {
            out = out.replace("<website>", "/website");
        }

        if (out.contains("<blink>")) {
            out = out.replace("<blink>", this.ticks / 10 % 2 == 0 ? "<#FFD27A>" : "<white>");
        }

        return out;
    }

    private Component render(String mini) {
        try {
            return Text.mm(mini, Placeholder.unparsed("none", ""));
        } catch (RuntimeException var3) {
            return Component.text(mini);
        }
    }

    private String emberTitle(String raw) {
        double phase = Math.sin(this.ticks * 0.08) * 0.95;
        return "<b><gradient:#C0141E:#FF4E1F:#FF7A1A:#FFB547:" + String.format(Locale.US, "%.2f", phase) + ">" + raw + "</gradient></b>";
    }

    private void spawnIfLoaded(Holograms.Holo h) {
        Location l = h.loc();
        if (l != null && l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4) && HoloFx.clientsReady(l)) {
            this.spawn(h);
        }
    }

    private void despawn(Holograms.Holo h) {
        Holograms.Live l = this.live.remove(h.id);
        if (l != null) {
            for (Entity e : l.all()) {
                e.remove();
            }
        }
    }

    private <T extends Entity> T tag(T e, Holograms.Holo h) {
        e.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, h.id);
        e.setPersistent(false);
        return e;
    }

    private TextDisplay text(Location at, Holograms.Holo h, float scale, Color background) {
        return this.tag(at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.setBillboard(Billboard.CENTER);
            t.setAlignment(TextAlignment.CENTER);
            t.setLineWidth(320);
            t.setShadowed(background == null);
            t.setSeeThrough(false);
            t.setDefaultBackground(false);
            t.setBackgroundColor(background == null ? Color.fromARGB(0, 0, 0, 0) : background);
            t.setBrightness(new Brightness(15, 15));
            t.setViewRange(0.6F);
            t.setTeleportDuration(3);
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
        }), h);
    }

    private static Color accent(Holograms.Holo h, int alpha) {
        try {
            int rgb = Integer.parseInt(h.accent.replace("#", ""), 16);
            return Color.fromARGB(alpha, rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
        } catch (RuntimeException var3) {
            return Color.fromARGB(alpha, 255, 122, 26);
        }
    }

    private void spawn(Holograms.Holo h) {
        this.despawn(h);
        Location at = h.loc();
        if (at != null) {
            Holograms.Live l = new Holograms.Live();
            float s = h.scale;
            List<String> lines = h.lines.isEmpty() ? List.of("<gray>(empty hologram)") : h.lines;
            int n = lines.size();

            double height = switch (h.design) {
                case CLASSIC -> {
                    l.lines.add(this.text(at, h, s, null));
                    yield n * 0.27 * s;
                }
                default -> {
                    l.lines.add(this.text(at, h, s, Color.fromARGB(160, 9, 12, 24)));
                    yield n * 0.27 * s;
                }
                case EMBER -> {
                    Location card = at.clone();
                    if (n > 1) {
                        l.lines.add(this.text(card, h, s, Color.fromARGB(150, 38, 10, 6)));
                    }

                    l.title = this.text(at.clone().add(0.0, (n - 1) * 0.27 * s + 0.18 * s, 0.0), h, s * 1.45F, null);
                    yield (n - 1) * 0.27 * s + 0.6 * s;
                }
                case NEON -> {
                    l.lines.add(this.text(at, h, s * 1.1F, accent(h, 110)));
                    yield n * 0.27 * s * 1.1;
                }
                case FRAME, HOLO, ROYAL -> {
                    HoloFx.Kind kind = HoloFx.Kind.valueOf(h.design.name());
                    TextDisplay card = this.tag(HoloFx.card(at, h.yaw, true, s, HoloFx.background(kind, 0, -1), 320, e -> {}), h);
                    l.lines.add(card);
                    double[] size = HoloFx.measure(lines, 320);
                    l.decor = HoloFx.build(kind, at, h.yaw, size[0] * s, size[1] * s, s, 0, null, e -> this.tag(e, h));
                    yield size[1] * s + (kind == HoloFx.Kind.ROYAL ? 0.7 * s : 0.1);
                }
                case ORBIT -> {
                    l.lines.add(this.text(at, h, s, Color.fromARGB(160, 9, 12, 24)));
                    double[] size = HoloFx.measure(lines, 320);
                    Material im = h.icon.isBlank() ? null : Material.matchMaterial(h.icon);
                    l.decor = HoloFx.build(HoloFx.Kind.ORBIT, at, 0.0F, size[0] * s, size[1] * s, s, 0, im == null ? null : List.of(im, im, im), e -> this.tag(e, h));
                    yield size[1] * s;
                }
                case FLOAT -> {
                    for (int i = 0; i < n; i++) {
                        Location li = at.clone().add(0.0, (n - 1 - i) * 0.27 * s * 1.15, 0.0);
                        l.lines.add(this.text(li, h, s * (i == 0 ? 1.25F : 1.0F), null));
                    }

                    yield n * 0.27 * s * 1.15;
                }
            };

            String icon = h.design == Holograms.Design.ORBIT ? "" : (!h.icon.isBlank() ? h.icon : (h.design == Holograms.Design.SHOWCASE ? "nether_star" : ""));
            Material m = icon.isBlank() ? null : Material.matchMaterial(icon);
            if (m != null && m.isItem()) {
                Location top = at.clone().add(0.0, height + 0.45 * s, 0.0);
                l.item = this.tag(at.getWorld().spawn(top, ItemDisplay.class, d -> {
                    d.setItemStack(new ItemStack(m));
                    d.setItemDisplayTransform(ItemDisplayTransform.FIXED);
                    d.setBillboard(Billboard.FIXED);
                    d.setBrightness(new Brightness(15, 15));
                    d.setTeleportDuration(3);
                    d.setViewRange(0.6F);
                    float is = 0.75F * s;
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(is, is, is), new Quaternionf()));
                }), h);
            }

            if (!h.click.isBlank()) {
                double hh = height + 0.2;
                l.hitbox = this.tag(at.getWorld().spawn(at.clone().add(0.0, -0.1, 0.0), Interaction.class, i -> {
                    i.setInteractionWidth((float)Math.max(1.6, 2.4 * s));
                    i.setInteractionHeight((float)Math.max(0.6, hh));
                    i.setResponsive(true);
                }), h);
            }

            this.live.put(h.id, l);
            this.refreshText(h, l, true);
        }
    }

    private void refreshText(Holograms.Holo h, Holograms.Live l, boolean force) {
        List<String> lines = h.lines.isEmpty() ? List.of("<gray>(empty hologram)") : h.lines;
        switch (h.design) {
            case EMBER:
                if (l.title != null) {
                    this.set(l, l.title, this.emberTitle(this.placeholders(lines.get(0))), force);
                }

                if (!l.lines.isEmpty()) {
                    List<String> rest = new ArrayList<>();
                    rest.add("<gradient:#5A1A0E:#FF7A1A:#5A1A0E>▬▬▬▬▬▬▬▬▬▬▬▬</gradient>");

                    for (int i = 1; i < lines.size(); i++) {
                        rest.add(this.placeholders(lines.get(i)));
                    }

                    this.set(l, l.lines.get(0), String.join("\n", rest), force);
                }
                break;
            case FLOAT:
                for (int i = 0; i < l.lines.size() && i < lines.size(); i++) {
                    this.set(l, l.lines.get(i), this.placeholders(lines.get(i)), force);
                }
                break;
            default:
                List<String> all = new ArrayList<>();

                for (int i = 0; i < lines.size(); i++) {
                    String line = this.placeholders(lines.get(i));
                    if (i == 0 && h.design == Holograms.Design.MARQUEE) {
                        line = "<" + h.accent + ">" + HoloFx.marquee(line, this.ticks, 24);
                    } else if (h.design == Holograms.Design.MARQUEE && i % 2 == 0) {
                        line = "<#D8D8D8>" + line;
                    }

                    all.add(i == 0 && h.design != Holograms.Design.CLASSIC ? "<b>" + line + "</b>" : line);
                }

                if (!l.lines.isEmpty()) {
                    this.set(l, l.lines.get(0), String.join("\n", all), force);
                }
        }
    }

    private void set(Holograms.Live l, TextDisplay d, String mini, boolean force) {
        if (d != null && d.isValid()) {
            if (force || !mini.equals(l.shown.get(d))) {
                l.shown.put(d, mini);
                d.text(this.render(mini));
            }
        }
    }

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        int cx = e.getChunk().getX();
        int cz = e.getChunk().getZ();
        String w = e.getWorld().getName();
        Tasks.later(2L, () -> {
            for (Holograms.Holo h : this.holos.values()) {
                if (h.world.equals(w) && (int)Math.floor(h.x) >> 4 == cx && (int)Math.floor(h.z) >> 4 == cz) {
                    Holograms.Live l = this.live.get(h.id);
                    if (l == null || !l.valid()) {
                        this.spawnIfLoaded(h);
                    }
                }
            }
        });
    }

    public void tick() {
        this.ticks++;

        for (Holograms.Holo h : this.holos.values()) {
            Holograms.Live l = this.live.get(h.id);
            if (l != null) {
                if (!l.valid()) {
                    this.live.remove(h.id);
                } else {
                    Location base = h.loc();
                    if (base != null && net.vortexpvp.util.Viewers.near(base)) {
                        if (l.item != null && l.item.isValid()) {
                            Location il = l.item.getLocation();
                            il.setYaw(this.ticks * 6.0F % 360.0F);
                            l.item.teleport(il);
                            float is = 0.75F * h.scale;
                            float bob = (float)Math.sin(this.ticks * 0.12) * 0.06F;
                            l.item.setInterpolationDelay(0);
                            l.item.setInterpolationDuration(3);
                            l.item
                                .setTransformation(
                                    new Transformation(new Vector3f(0.0F, bob, 0.0F), new Quaternionf(), new Vector3f(is, is, is), new Quaternionf())
                                );
                        }

                        if (l.decor != null) {
                            l.decor.tick(this.ticks);
                        }

                        if (h.design == Holograms.Design.MARQUEE && this.ticks % 4 == 0) {
                            this.refreshText(h, l, false);
                        }

                        switch (h.design) {
                            case EMBER:
                                if (l.title != null && !h.lines.isEmpty()) {
                                    this.set(l, l.title, this.emberTitle(this.placeholders(h.lines.get(0))), false);
                                }
                                break;
                            case NEON:
                                int alpha = 80 + (int)Math.round((Math.sin(this.ticks * 0.15) * 0.5 + 0.5) * 80.0);
                                if (!l.lines.isEmpty()) {
                                    l.lines.get(0).setBackgroundColor(accent(h, alpha));
                                }
                                break;
                            case FLOAT:
                                for (int i = 0; i < l.lines.size(); i++) {
                                    TextDisplay d = l.lines.get(i);
                                    float sc = h.scale * (i == 0 ? 1.25F : 1.0F);
                                    float off = (float)Math.sin(this.ticks * 0.1 + i * 0.9) * 0.05F * h.scale;
                                    d.setInterpolationDelay(0);
                                    d.setInterpolationDuration(3);
                                    d.setTransformation(
                                        new Transformation(new Vector3f(0.0F, off, 0.0F), new Quaternionf(), new Vector3f(sc, sc, sc), new Quaternionf())
                                    );
                                }
                        }

                        if (this.ticks % 10 == 0) {
                            this.refreshText(h, l, false);
                        }
                    }
                }
            }
        }

        if (this.ticks % 10 == 0) {
            for (Holograms.Holo hx : this.holos.values()) {
                if (!this.live.containsKey(hx.id)) {
                    this.spawnIfLoaded(hx);
                }
            }
        }
    }

    private Holograms.Holo of(Entity e) {
        String id = e.getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
        return id == null ? null : this.holos.get(id);
    }

    private void clicked(Player p, Holograms.Holo h) {
        long now = System.currentTimeMillis();
        Long last = this.lastClick.get(p.getUniqueId());
        if (last == null || now - last >= 600L) {
            this.lastClick.put(p.getUniqueId(), now);
            if (!h.click.isBlank()) {
                Snd.click(p);
                p.performCommand(h.click.startsWith("/") ? h.click.substring(1) : h.click);
            }
        }
    }

    @EventHandler(
        ignoreCancelled = false
    )
    public void onRightClick(PlayerInteractEntityEvent e) {
        Holograms.Holo h = this.of(e.getRightClicked());
        if (h != null) {
            e.setCancelled(true);
            this.clicked(e.getPlayer(), h);
        }
    }

    @EventHandler(
        ignoreCancelled = false
    )
    public void onPunch(PrePlayerAttackEntityEvent e) {
        Holograms.Holo h = this.of(e.getAttacked());
        if (h != null) {
            e.setCancelled(true);
            this.clicked(e.getPlayer(), h);
        }
    }

    @EventHandler(
        ignoreCancelled = false
    )
    public void onLeftClick(EntityDamageByEntityEvent e) {
        Holograms.Holo h = this.of(e.getEntity());
        if (h != null) {
            e.setCancelled(true);
            if (e.getDamager() instanceof Player p) {
                this.clicked(p, h);
            }
        }
    }

    private Holograms.Holo create(Location at, Holograms.Preset p) {
        Holograms.Holo h = new Holograms.Holo("h" + ++this.counter);
        h.world = at.getWorld().getName();
        h.x = at.getX();
        h.y = at.getY();
        h.z = at.getZ();
        // framed designs face whoever placed them (the location carries their look direction)
        h.yaw = at.getYaw() + 180.0F;
        if (p != null) {
            h.design = p.design();
            h.lines = new ArrayList<>(p.lines());
            h.icon = p.icon();
            h.click = p.click();
            h.accent = p.accent();
        } else {
            h.lines = new ArrayList<>(List.of("<gold>NEW HOLOGRAM", "<gray>Edit the lines in /holo"));
        }

        this.holos.put(h.id, h);
        this.spawn(h);
        this.save();
        return h;
    }

    private static Location placeAt(Player p) {
        Block b = p.getTargetBlockExact(6);
        Location at;
        if (b != null && !b.getType().isAir()) {
            at = b.getLocation().add(0.5, 1.6, 0.5);
        } else {
            at = p.getEyeLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(3));
        }

        at.setYaw(p.getLocation().getYaw());
        return at;
    }

    /** A preset hologram placed by a builder (the hub), facing the location's yaw, in a design. */
    public void placePreset(String preset, Location at, String design) {
        for (Holograms.Preset pr : PRESETS) {
            if (pr.id().equals(preset)) {
                Location l = at.clone();
                l.setYaw(at.getYaw() + 180.0F);
                Holograms.Holo h = this.create(l, pr);
                try {
                    h.design = Holograms.Design.valueOf(design);
                } catch (IllegalArgumentException var8) {
                }

                this.save();
                this.spawn(h);
                return;
            }
        }
    }

    public int hubPack(Player p) {
        Location c = p.getLocation();
        double yaw = Math.toRadians(c.getYaw());
        int n = PRESETS.size();

        for (int i = 0; i < n; i++) {
            double a = yaw + Math.toRadians(-75.0 + 150.0 * i / Math.max(1, n - 1));
            Location at = c.clone().add(-Math.sin(a) * 7.0, 1.4, Math.cos(a) * 7.0);
            // each one faces the middle of the arc (create() turns it around to face the placer's spot)
            at.setYaw((float)Math.toDegrees(a));
            this.create(at, PRESETS.get(i));
        }

        return n;
    }

    public int removeAll() {
        int n = this.holos.size();
        if (this.plugin.holograms() != null) {
            n += this.plugin.holograms().removeAll();
        }

        for (Holograms.Holo h : this.holos.values()) {
            this.despawn(h);
        }

        this.holos.clear();
        this.live.clear();
        this.clearLeftovers();
        this.save();
        return n;
    }

    private Holograms.Holo nearest(Player p, double max) {
        Holograms.Holo best = null;
        double bd = max * max;

        for (Holograms.Holo h : this.holos.values()) {
            if (h.world.equals(p.getWorld().getName())) {
                double d = p.getLocation().distanceSquared(new Location(p.getWorld(), h.x, h.y, h.z));
                if (d < bd) {
                    bd = d;
                    best = h;
                }
            }
        }

        return best;
    }

    private LobbyDialogs ui() {
        return this.plugin.dialogs().lobby();
    }

    private static Material designIcon(Holograms.Design d) {
        return switch (d) {
            case CLASSIC -> Material.PAPER;
            case GLASS -> Material.TINTED_GLASS;
            case EMBER -> Material.BLAZE_POWDER;
            case NEON -> Material.GLOWSTONE_DUST;
            case FLOAT -> Material.FEATHER;
            case SHOWCASE -> Material.NETHER_STAR;
            case FRAME -> Material.ITEM_FRAME;
            case HOLO -> Material.BEACON;
            case ROYAL -> Material.GOLDEN_HELMET;
            case MARQUEE -> Material.REDSTONE_LAMP;
            case ORBIT -> Material.ENDER_EYE;
        };
    }

    public void menu(Player p) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (Holograms.Holo h : this.holos.values()) {
            String first = h.lines.isEmpty() ? "(empty)" : Text.plain(this.render(this.placeholders(h.lines.get(0))));
            double dist = h.world.equals(p.getWorld().getName()) ? p.getLocation().distance(new Location(p.getWorld(), h.x, h.y, h.z)) : -1.0;
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(designIcon(h.design)),
                    Text.mm("<white><b>" + Text.esc(cut(first, 30))),
                    Text.mm(
                        "<gray>"
                            + h.design.display
                            + " <dark_gray>• <gray>"
                            + (dist < 0.0 ? Text.esc(h.world) : Math.round(dist) + " blocks away")
                            + (h.click.isBlank() ? "" : " <dark_gray>• <aqua>/" + Text.esc(h.click))
                    ),
                    List.of(Text.mm("<white><b>" + Text.esc(first)), Component.empty(), Text.mm("<green>▶ Click to edit")),
                    () -> this.edit(p, h)
                )
            );
        }

        List<ActionButton> b = new ArrayList<>();
        b.add(
            this.ui()
                .btn(
                    p,
                    "<gold><b>Hub Pack",
                    "<gray>Welcome, Discord, tier tests, tournaments, practice, website and ranks in a half circle in front of you",
                    () -> {
                        int n = this.hubPack(p);
                        p.sendMessage(Text.mm("<green>Placed <white>" + n + "</white> holograms around you."));
                        this.menu(p);
                    }
                )
        );
        b.add(this.ui().btn(p, "<green>+ From a Preset", "<gray>One ready-made hologram where you look", () -> this.presets(p)));
        b.add(this.ui().btn(p, "<white>+ Blank", "<gray>A new hologram to write yourself", () -> this.edit(p, this.create(placeAt(p), null))));
        b.add(this.ui().btn(p, "<yellow>+ Leaderboard", "<gray>A live top-10 of a game mode, here", () -> this.plugin.holograms().prompt(p)));
        b.add(this.ui().btn(p, "<aqua>Edit Nearest", "<gray>The hologram closest to you", () -> {
            Holograms.Holo hx = this.nearest(p, 16.0);
            if (hx == null) {
                p.sendMessage(Text.mm("<red>No hologram within 16 blocks."));
            } else {
                this.edit(p, hx);
            }
        }));
        b.add(
            this.ui()
                .btn(
                    p,
                    "<red>Remove All",
                    "<gray>Every hologram, after asking",
                    () -> this.plugin
                        .dialogs()
                        .confirm(
                            p,
                            Text.mm(
                                "<red><b>Remove all "
                                    + (this.holos.size() + (this.plugin.holograms() == null ? 0 : this.plugin.holograms().count()))
                                    + " holograms?"
                            ),
                            List.of(Text.mm("<gray>Text and leaderboard holograms, in every world. This can't be undone.")),
                            () -> {
                                int n = this.removeAll();
                                p.sendMessage(Text.mm("<green>Removed <white>" + n + "</white> holograms."));
                                this.menu(p);
                            }
                        )
                )
        );
        if (this.plugin.decos() != null) {
            b.add(this.ui().btn(p, "<gray>3D Decorations", null, () -> this.plugin.decos().menu(p)));
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Holograms", this.holos.size() + " placed"),
                tiles,
                List.of(Text.mm("<gray>Floating text with live numbers. Click a hologram in the world to run its command.")),
                Text.mm("<gray>No holograms yet - try the <gold>Hub Pack</gold>."),
                b,
                2,
                this.ui().close(p)
            );
    }

    private void presets(Player p) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (Holograms.Preset pr : PRESETS) {
            List<Component> hover = new ArrayList<>();

            for (String line : pr.lines()) {
                hover.add(this.render(this.placeholders(line)));
            }

            hover.add(Component.empty());
            hover.add(Text.mm("<green>▶ Click to place where you look"));
            Material icon = pr.icon().isBlank() ? designIcon(pr.design()) : Material.matchMaterial(pr.icon());
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(icon == null ? Material.PAPER : icon),
                    Text.mm("<white><b>" + pr.name()),
                    Text.mm("<gray>" + pr.design().display + (pr.click().isBlank() ? "" : " <dark_gray>• <aqua>click: /" + pr.click())),
                    hover,
                    () -> this.edit(p, this.create(placeAt(p), pr))
                )
            );
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Holograms", "Presets"),
                tiles,
                List.of(),
                null,
                List.of(this.ui().btn(p, "<gray>« Holograms", null, () -> this.menu(p))),
                1,
                this.ui().close(p)
            );
    }

    private void edit(Player p, Holograms.Holo h) {
        List<Component> header = new ArrayList<>();
        header.add(
            Text.mm(
                "<white><b>"
                    + h.design.display
                    + "</b> <dark_gray>#"
                    + h.id
                    + " <gray>at <white>"
                    + String.format(Locale.ROOT, "%.1f %.1f %.1f", h.x, h.y, h.z)
                    + " <gray>size <white>"
                    + Math.round(h.scale * 100.0F)
                    + "%"
            )
        );

        for (String line : h.lines) {
            header.add(this.render(this.placeholders(line)));
        }

        List<ActionButton> b = new ArrayList<>();
        b.add(
            this.ui()
                .btn(
                    p,
                    "<gold>Design: <white>" + h.design.display,
                    "<gray>" + h.design.desc,
                    () -> {
                        List<Dialogs.Choice> c = new ArrayList<>();

                        for (Holograms.Design d : Holograms.Design.values()) {
                            c.add(new Dialogs.Choice(d.name(), "<white>" + d.display + " <gray>- " + d.desc));
                        }

                        this.plugin
                            .dialogs()
                            .choice(
                                p,
                                Text.mm("<gold><b>Design"),
                                "Design",
                                c,
                                h.design.name(),
                                v -> this.change(p, h, () -> h.design = Holograms.Design.valueOf(v))
                            );
                    }
                )
        );
        b.add(
            this.ui()
                .btn(
                    p,
                    "<gold>Lines",
                    "<gray>MiniMessage, | between lines. <online> <event> <discord> <ip> ...",
                    () -> this.plugin
                        .dialogs()
                        .text(
                            p,
                            Text.mm("<gold><b>Lines</b> <gray>(| between lines)"),
                            "Lines",
                            String.join(" | ", h.lines),
                            1024,
                            v -> this.change(p, h, () -> {
                                h.lines = new ArrayList<>();

                                for (String s : v.split("\\|")) {
                                    if (!s.isBlank()) {
                                        h.lines.add(s.trim());
                                    }
                                }
                            })
                        )
                )
        );
        b.add(
            this.ui()
                .btn(
                    p,
                    "<aqua>Click Command: <white>" + (h.click.isBlank() ? "none" : "/" + Text.esc(h.click)),
                    "<gray>Run for the player who clicks it (empty = none)",
                    () -> this.plugin
                        .dialogs()
                        .text(
                            p,
                            Text.mm("<gold><b>Click command</b> <gray>(no slash)"),
                            "Command",
                            h.click,
                            128,
                            v -> this.change(p, h, () -> h.click = v.replaceFirst("^/", "").trim())
                        )
                )
        );
        b.add(this.ui().btn(p, "<aqua>Item = Your Hand", "<gray>Spins above it (empty hand = none)", () -> this.change(p, h, () -> {
            ItemStack hand = p.getInventory().getItemInMainHand();
            h.icon = hand.isEmpty() ? "" : hand.getType().getKey().getKey();
        })));
        b.add(
            this.ui()
                .btn(
                    p,
                    "<aqua>Accent: <white>" + Text.esc(h.accent),
                    "<gray>Neon colour (hex)",
                    () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Accent colour"), "Hex", h.accent, 7, v -> this.change(p, h, () -> {
                        if (v.matches("#?[0-9A-Fa-f]{6}")) {
                            h.accent = v.startsWith("#") ? v : "#" + v;
                        }
                    }))
                )
        );
        b.add(this.ui().btn(p, "<green>＋ Bigger", null, () -> this.change(p, h, () -> h.scale = Math.min(4.0F, h.scale * 1.15F))));
        b.add(this.ui().btn(p, "<red>－ Smaller", null, () -> this.change(p, h, () -> h.scale = Math.max(0.3F, h.scale / 1.15F))));
        b.add(this.ui().btn(p, "<white>▲ Up", "<gray>0.25 block", () -> this.change(p, h, () -> h.y += 0.25)));
        b.add(this.ui().btn(p, "<white>▼ Down", "<gray>0.25 block", () -> this.change(p, h, () -> h.y -= 0.25)));
        b.add(this.ui().btn(p, "<yellow>Face Me", "<gray>Framed designs face one way: turn it toward you", () -> this.change(p, h, () -> {
            org.bukkit.util.Vector d = p.getLocation().toVector().subtract(new org.bukkit.util.Vector(h.x, h.y, h.z));
            h.yaw = (float)Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        })));
        b.add(this.ui().btn(p, "<yellow>Move to Me", "<gray>Above where you stand", () -> this.change(p, h, () -> {
            h.world = p.getWorld().getName();
            h.x = p.getLocation().getX();
            h.y = p.getLocation().getY() + 1.6;
            h.z = p.getLocation().getZ();
        })));
        b.add(this.ui().btn(p, "<gold>Copy", "<gray>Another one where you look", () -> {
            Holograms.Holo c = this.create(placeAt(p), null);
            c.design = h.design;
            c.lines = new ArrayList<>(h.lines);
            c.icon = h.icon;
            c.click = h.click;
            c.accent = h.accent;
            c.scale = h.scale;
            c.yaw = h.yaw;
            this.spawn(c);
            this.save();
            this.edit(p, c);
        }));
        b.add(this.ui().btn(p, "<red><b>Remove", null, () -> {
            this.despawn(h);
            this.holos.remove(h.id);
            this.save();
            this.menu(p);
        }));
        this.ui()
            .screen(p, LobbyDialogs.title("Hologram", "Edit"), List.of(), header, null, b, 2, this.ui().btn(p, "<gray>« Holograms", null, () -> this.menu(p)));
    }

    private void change(Player p, Holograms.Holo h, Runnable r) {
        r.run();
        this.spawn(h);
        this.save();
        Snd.click(p);
        this.edit(p, h);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            if (!p.hasPermission("vortexpvp.admin")) {
                p.sendMessage(Text.mm("<red>You don't have permission."));
                return true;
            } else {
                String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "hubpack":
                    case "pack":
                        p.sendMessage(Text.mm("<green>Placed <white>" + this.hubPack(p) + "</white> holograms around you."));
                        break;
                    case "near":
                        Holograms.Holo h = this.nearest(p, 16.0);
                        if (h == null) {
                            p.sendMessage(Text.mm("<red>No hologram within 16 blocks."));
                        } else {
                            this.edit(p, h);
                        }
                        break;
                    case "clear":
                    case "removeall":
                        this.plugin
                            .dialogs()
                            .confirm(
                                p,
                                Text.mm("<red><b>Remove all " + this.holos.size() + " holograms?"),
                                List.of(Text.mm("<gray>This can't be undone.")),
                                () -> p.sendMessage(Text.mm("<green>Removed <white>" + this.removeAll() + "</white> holograms."))
                            );
                        break;
                    case "preset":
                        Holograms.Preset pr = args.length >= 2 ? PRESETS.stream().filter(x -> x.id().equalsIgnoreCase(args[1])).findFirst().orElse(null) : null;
                        if (pr == null) {
                            this.presets(p);
                        } else {
                            this.edit(p, this.create(placeAt(p), pr));
                        }
                        break;
                    default:
                        this.menu(p);
                }

                return true;
            }
        } else {
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("hubpack", "preset", "near", "clear"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("preset")) {
            PRESETS.forEach(pr -> out.add(pr.id()));
        }

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(last));
        return out;
    }

    public static enum Design {
        CLASSIC("Classic", "Plain lines with a shadow"),
        GLASS("Glass Card", "A dark see-through card"),
        EMBER("Ember", "Flowing red-orange title on a warm card"),
        NEON("Neon Sign", "A coloured sign that pulses"),
        FLOAT("Floating", "Every line floats on its own wave"),
        SHOWCASE("Showcase", "A spinning item over a glass card"),
        FRAME("Framed Board", "Dark oak frame, gold corners, lanterns"),
        HOLO("Sci-Fi Hologram", "Cyan projection with a scanning line"),
        ROYAL("Royal", "Gold trim, purple card, spinning crown"),
        MARQUEE("Marquee", "The title scrolls like a stadium screen"),
        ORBIT("Orbit", "Items orbit the card");

        public final String display;
        public final String desc;

        private Design(String display, String desc) {
            this.display = display;
            this.desc = desc;
        }
    }

    static final class Holo {
        final String id;
        String world;
        double x;
        double y;
        double z;
        Holograms.Design design = Holograms.Design.GLASS;
        List<String> lines = new ArrayList<>();
        String icon = "";
        String click = "";
        String accent = "#FF7A1A";
        float scale = 1.0F;
        /** The side it is read from, for the framed designs (the others turn to the viewer). */
        float yaw;

        Holo(String id) {
            this.id = id;
        }

        Location loc() {
            World w = Bukkit.getWorld(this.world);
            return w == null ? null : new Location(w, this.x, this.y, this.z);
        }
    }

    private static final class Live {
        final List<TextDisplay> lines = new ArrayList<>();
        TextDisplay title;
        ItemDisplay item;
        Interaction hitbox;
        final Map<TextDisplay, String> shown = new HashMap<>();
        HoloFx.Decor decor;

        boolean valid() {
            return this.title != null && this.title.isValid() || !this.lines.isEmpty() && this.lines.get(0).isValid();
        }

        List<Entity> all() {
            List<Entity> out = new ArrayList<>(this.lines);
            if (this.title != null) {
                out.add(this.title);
            }

            if (this.item != null) {
                out.add(this.item);
            }

            if (this.hitbox != null) {
                out.add(this.hitbox);
            }

            if (this.decor != null) {
                out.addAll(this.decor.parts());
            }

            return out;
        }
    }

    private record Preset(String id, String name, Holograms.Design design, String icon, String click, String accent, List<String> lines) {
    }
}
