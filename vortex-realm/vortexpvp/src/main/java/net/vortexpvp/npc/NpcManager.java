package net.vortexpvp.npc;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
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
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.data.Setting;
import net.vortexpvp.dialog.Dialogs;
import net.vortexpvp.dialog.LobbyDialogs;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Tp;
import net.vortexpvp.smp.WorldMode;
import net.vortexpvp.util.Locs;
import net.vortexpvp.util.Look;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class NpcManager implements Listener, CommandExecutor, TabCompleter {
    private final VortexPvP plugin;
    private final File file;
    private final NamespacedKey key;
    private final Map<String, Npc> npcs = new LinkedHashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private int ticks;
    private String topNpc;

    public NpcManager(VortexPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
        this.key = new NamespacedKey(plugin, "npc");
    }

    public void load() {
        this.despawnAll();
        this.npcs.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection sec = y.getConfigurationSection("npcs");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(id);
                Location l = s == null ? null : Locs.parse(s.getString("location"));
                if (s != null && s.getString("location") != null) {
                    Npc n = new Npc(id, l);
                    n.rawLocation = s.getString("location");
                    n.skin = s.getString("skin", "");
                    n.lines.addAll(s.getStringList("lines"));

                    try {
                        n.action = Npc.Action.valueOf(s.getString("action", "NONE"));
                    } catch (IllegalArgumentException var9) {
                        n.action = Npc.Action.NONE;
                    }

                    n.value = s.getString("value", "");
                    n.lookAt = s.getBoolean("look-at-players", true);
                    n.glow = s.getString("glow", "none");
                    n.hand = Material.matchMaterial(s.getString("hand", ""));
                    n.particle = s.getString("particle", "none");
                    n.sneak = s.getBoolean("sneak", false);
                    n.floatItem = s.getBoolean("float-item", true);
                    n.bar = s.getBoolean("player-bar", true);
                    readExtras(n, s);
                    this.npcs.put(id, n);
                }
            }
        }

        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(this.key, PersistentDataType.STRING)) {
                    e.remove();
                }
            }
        }

        for (Npc n : this.npcs.values()) {
            this.spawn(n);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options()
            .setHeader(
                List.of(
                    "Lobby NPCs. Edit them in game with /npc (dialog menus).",
                    "actions: NONE, WORLD, LOBBY, HUB, SELECTOR, MENU, COMMAND, CONSOLE, MESSAGE.",
                    "Lines are MiniMessage; {players} = players in the target world, {online} = everyone online,",
                    "{bar} = the players bar (also added under the lines when player-bar is true)."
                )
            );

        for (Npc n : this.npcs.values()) {
            String b = "npcs." + n.id + ".";
            y.set(b + "location", n.loc != null ? Locs.serialize(n.loc) : n.rawLocation);
            y.set(b + "skin", n.skin);
            y.set(b + "lines", n.lines);
            y.set(b + "action", n.action.name());
            y.set(b + "value", n.value);
            y.set(b + "look-at-players", n.lookAt);
            y.set(b + "glow", n.glow);
            y.set(b + "hand", n.hand == null ? "" : n.hand.name());
            y.set(b + "particle", n.particle);
            y.set(b + "sneak", n.sneak);
            y.set(b + "float-item", n.floatItem);
            y.set(b + "player-bar", n.bar);
            writeExtras(n, y, b);
        }

        try {
            y.save(this.file);
        } catch (IOException var5) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save npcs.yml", (Throwable)var5);
        }
    }

    /** The 1.3.0 options; an NPC saved by an older version gets the defaults (it looks the same as before). */
    private static void readExtras(Npc n, ConfigurationSection s) {
        n.helmet = s.getItemStack("armor.helmet");
        n.chest = s.getItemStack("armor.chestplate");
        n.legs = s.getItemStack("armor.leggings");
        n.boots = s.getItemStack("armor.boots");
        n.offhand = s.getItemStack("off-hand");
        n.scale = Math.max(0.5, Math.min(2.0, s.getDouble("scale", 1.0)));
        n.pose = parse(Npc.Pose.class, s.getString("pose"), n.sneak ? Npc.Pose.SNEAK : Npc.Pose.STAND);
        n.skinValue = s.getString("skin-texture.value", "");
        n.skinSignature = s.getString("skin-texture.signature", "");
        n.idle = parse(Npc.Idle.class, s.getString("idle"), Npc.Idle.NONE);
        n.labelStyle = parse(Npc.LabelStyle.class, s.getString("label.style"), Npc.LabelStyle.CLASSIC);
        n.labelColor = s.getString("label.color", "");
        n.labelAlpha = s.getInt("label.alpha", -1);
        n.labelScale = Math.max(0.3, Math.min(4.0, s.getDouble("label.scale", 1.0)));
        n.labelHeight = s.getDouble("label.height", 0.0);
        n.hideLabel = s.getBoolean("label.hidden", false);
        n.animatedTitle = s.getBoolean("label.animated-title", false);
        n.titleColors = s.getString("label.title-colors", "#FFB547:#FF4A1C");
        n.floatMaterial = Material.matchMaterial(s.getString("float-item-type", ""));
        n.clickSound = s.getString("click-sound", "ui.button.click");
        n.cooldownMs = Math.max(0, s.getInt("cooldown-ms", 700));
        n.permission = s.getString("permission", "");
        n.greetText = s.getString("greet.text", "");
        n.greetRadius = Math.max(0, s.getInt("greet.radius", 4));
    }

    private static void writeExtras(Npc n, YamlConfiguration y, String b) {
        y.set(b + "armor.helmet", n.helmet);
        y.set(b + "armor.chestplate", n.chest);
        y.set(b + "armor.leggings", n.legs);
        y.set(b + "armor.boots", n.boots);
        y.set(b + "off-hand", n.offhand);
        y.set(b + "scale", n.scale);
        y.set(b + "pose", n.pose.name());
        y.set(b + "skin-texture.value", n.skinValue);
        y.set(b + "skin-texture.signature", n.skinSignature);
        y.set(b + "idle", n.idle.name());
        y.set(b + "label.style", n.labelStyle.name());
        y.set(b + "label.color", n.labelColor);
        y.set(b + "label.alpha", n.labelAlpha);
        y.set(b + "label.scale", n.labelScale);
        y.set(b + "label.height", n.labelHeight);
        y.set(b + "label.hidden", n.hideLabel);
        y.set(b + "label.animated-title", n.animatedTitle);
        y.set(b + "label.title-colors", n.titleColors);
        y.set(b + "float-item-type", n.floatMaterial == null ? "" : n.floatMaterial.name());
        y.set(b + "click-sound", n.clickSound);
        y.set(b + "cooldown-ms", n.cooldownMs);
        y.set(b + "permission", n.permission);
        y.set(b + "greet.text", n.greetText);
        y.set(b + "greet.radius", n.greetRadius);
    }

    private static <E extends Enum<E>> E parse(Class<E> cls, String v, E def) {
        if (v == null) {
            return def;
        } else {
            try {
                return Enum.valueOf(cls, v.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var4) {
                return def;
            }
        }
    }

    /** Height of the name label above the feet. */
    private static double labelY(Npc n) {
        return n.pose.head() * n.scale + n.labelHeight;
    }

    private static int labelBg(Npc n) {
        int alpha = n.labelAlpha >= 0 ? n.labelAlpha : n.labelStyle.alpha;
        int rgb = n.labelStyle.rgb;
        if (n.labelColor != null && n.labelColor.matches("#?[0-9A-Fa-f]{6}")) {
            rgb = Integer.parseInt(n.labelColor.replace("#", ""), 16);
        }

        return Math.max(0, Math.min(255, alpha)) << 24 | rgb;
    }

    public void shutdown() {
        this.despawnAll();
    }

    private void despawnAll() {
        for (Npc n : this.npcs.values()) {
            this.despawn(n);
        }
    }

    private void despawn(Npc n) {
        if (n.body != null) {
            n.body.remove();
            n.body = null;
        }

        if (n.label != null) {
            n.label.remove();
            n.label = null;
        }

        if (n.floater != null) {
            n.floater.remove();
            n.floater = null;
        }
    }

    private void spawn(Npc n) {
        try {
            this.spawnUnsafe(n);
        } catch (RuntimeException var3) {
            this.plugin.getLogger().log(Level.WARNING, "Could not spawn NPC " + n.id, (Throwable)var3);
        }
    }

    private void spawnUnsafe(Npc n) {
        this.despawn(n);
        if (n.loc == null && n.rawLocation != null) {
            n.loc = Locs.parse(n.rawLocation);
        }

        if (n.loc != null && n.loc.getWorld() != null && n.loc.isChunkLoaded() && net.vortexpvp.deco.HoloFx.clientsReady(n.loc)) {
            World w = n.loc.getWorld();
            n.body = w.spawn(n.loc, Mannequin.class, m -> {
                m.setPersistent(false);
                m.setInvulnerable(true);
                m.setImmovable(true);
                m.setSilent(true);
                m.setCollidable(false);
                m.setRemoveWhenFarAway(false);

                try {
                    m.setDescription(null);
                } catch (RuntimeException var4) {
                    m.setDescription(Component.empty());
                }

                m.setCustomNameVisible(false);
                if (n.skinValue != null && !n.skinValue.isBlank()) {
                    // a copied skin: textures are in the file, nothing to look up online
                    m.setProfile(
                        ResolvableProfile.resolvableProfile()
                            .name(n.skin == null || n.skin.isBlank() ? "NPC" : n.skin)
                            .addProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", n.skinValue, n.skinSignature.isBlank() ? null : n.skinSignature))
                            .build()
                    );
                } else if (n.skin != null && !n.skin.isBlank()) {
                    m.setProfile(ResolvableProfile.resolvableProfile().name(n.skin).build());
                }

                if (n.hand != null && n.hand.isItem()) {
                    m.getEquipment().setItemInMainHand(new ItemStack(n.hand));
                }

                m.getEquipment().setHelmet(n.helmet == null ? null : n.helmet.clone());
                m.getEquipment().setChestplate(n.chest == null ? null : n.chest.clone());
                m.getEquipment().setLeggings(n.legs == null ? null : n.legs.clone());
                m.getEquipment().setBoots(n.boots == null ? null : n.boots.clone());
                m.getEquipment().setItemInOffHand(n.offhand == null ? null : n.offhand.clone());
                if (n.scale != 1.0) {
                    org.bukkit.attribute.AttributeInstance a = m.getAttribute(org.bukkit.attribute.Attribute.SCALE);
                    if (a != null) {
                        a.setBaseValue(n.scale);
                    }
                }

                if (n.pose != Npc.Pose.STAND) {
                    try {
                        m.setPose(n.pose.bukkit, true);
                    } catch (RuntimeException var5) {
                        m.setPose(Pose.SNEAKING, true);
                    }
                }

                m.setGlowing(!"none".equals(n.glow));
                m.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, n.id);
            });
            this.glowColor(n);
            n.label = n.hideLabel ? null : w.spawn(n.loc.clone().add(0.0, labelY(n), 0.0), TextDisplay.class, t -> {
                t.setPersistent(false);
                t.setBillboard(Billboard.CENTER);
                t.setShadowed(true);
                t.setBackgroundColor(Color.fromARGB(labelBg(n)));
                if (n.labelScale != 1.0) {
                    float sc = (float)n.labelScale;
                    t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(sc, sc, sc), new Quaternionf()));
                }

                t.setInterpolationDuration(6);
                t.setAlignment(TextAlignment.CENTER);
                t.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, n.id);
            });
            this.refreshLabel(n);
            Material floatType = n.floatMaterial != null ? n.floatMaterial : n.hand;
            if (n.floatItem && floatType != null && floatType.isItem() && !floatType.isAir()) {
                int lines = n.hideLabel ? 0 : Math.max(1, n.lines.size()) + (n.bar && this.count(n) >= 0 ? 2 : 0);
                n.floater = w.spawn(n.loc.clone().add(0.0, labelY(n) + lines * 0.27 * n.labelScale + 0.45, 0.0), ItemDisplay.class, d -> {
                    d.setItemStack(new ItemStack(floatType));
                    d.setPersistent(false);
                    d.setBillboard(Billboard.FIXED);
                    d.setBrightness(new Brightness(15, 15));
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.55F, 0.55F, 0.55F), new Quaternionf()));
                    d.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, n.id);
                });
            }
        }
    }

    private void glowColor(Npc n) {
        if (n.body != null && !"none".equals(n.glow)) {
            NamedTextColor c = NamedTextColor.NAMES.value(n.glow);
            if (c != null) {
                Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
                Team t = sb.getTeam("ppnpc_" + n.glow);
                if (t == null) {
                    t = sb.registerNewTeam("ppnpc_" + n.glow);
                }

                t.color(c);
                t.addEntry(n.body.getUniqueId().toString());
            }
        }
    }

    private void refreshLabel(Npc n) {
        if (n.label != null && n.label.isValid()) {
            if (n.lines.isEmpty()) {
                n.label.text(Component.empty());
            } else {
                int count = this.count(n);
                String players = String.valueOf(Math.max(0, count));
                int all = Bukkit.getOnlinePlayers().size();
                String online = String.valueOf(all);
                String barLine = "";
                if (count >= 0) {
                    double share = all == 0 ? 0.0 : (double)count / all;
                    n.barShown = n.barShown < 0.0 ? share : n.barShown + (share - n.barShown) * 0.35;
                    if (Math.abs(n.barShown - share) < 0.004) {
                        n.barShown = share;
                    }

                    barLine = bar(n.barShown)
                        + " <white>"
                        + count
                        + "</white> <gray>playing <dark_gray>(<#FFB547>"
                        + Math.round(share * 100.0)
                        + "%</#FFB547>)";
                }

                String phase = String.format(Locale.US, "%.2f", Math.sin(this.ticks * 0.06) * 0.95);
                String blink = this.ticks / 10 % 2 == 0 ? "<#FFE066>" : "<#FFAA00>";
                StringBuilder sb = new StringBuilder();
                boolean first = true;

                for (String line : n.lines) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }

                    if (first && n.animatedTitle) {
                        // a moving gradient through the title, like the VS screen
                        String plain = Text.plain(Text.mm(line));
                        line = "<b><gradient:" + n.titleColors + ":" + n.titleColors.split(":")[0] + ":" + phase + ">" + Text.esc(plain) + "</gradient></b>";
                    }

                    first = false;

                    sb.append(
                        line.replace("{players}", players)
                            .replace("{online}", online)
                            .replace("{phase}", phase)
                            .replace("{blink}", blink)
                            .replace("{bar}", barLine)
                    );
                }

                boolean hasBarTag = false;

                for (String line : n.lines) {
                    hasBarTag |= line.contains("{bar}");
                }

                if (n.bar && !barLine.isEmpty() && !hasBarTag) {
                    sb.append('\n').append(barLine);
                }

                if (n.id.equals(this.topNpc) && count > 0) {
                    sb.insert(0, "<b><gradient:#FF4A1C:#FFB547:" + phase + ">\ud83d\udd25 MOST PLAYED</gradient></b>\n");
                }

                try {
                    n.label.text(Text.mm(sb.toString()));
                } catch (RuntimeException var13) {
                    n.label.text(Component.text(sb.toString()));
                }
            }
        }
    }

    private int count(Npc n) {
        switch (n.action) {
            case WORLD: {
                    World w = Bukkit.getWorld(n.value);
                    if (w == null) {
                        return 0;
                    }

                    int c = w.getPlayers().size();

                    for (String suffix : List.of("_nether", "_the_end")) {
                        World o = Bukkit.getWorld(n.value + suffix);
                        c += o == null ? 0 : o.getPlayers().size();
                    }

                    return c;
            }
            case HUB: {
                    World h = this.plugin.modes().hubWorld();
                    return h == null ? 0 : h.getPlayers().size();
            }
            case LOBBY:
            case MENU: {
                    String menu = n.action == Npc.Action.MENU ? n.value.toLowerCase(Locale.ROOT) : "";
                    int c = 0;

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        PlayerState st = this.plugin.sessions().state(p);

                        boolean counts = switch (menu) {
                            case "ffa" -> st == PlayerState.FFA;
                            case "duels", "ranked" -> st == PlayerState.MATCH || st == PlayerState.QUEUE || st == PlayerState.PREMATCH;
                            default -> st != PlayerState.OUTSIDE;
                        };
                        if (counts) {
                            c++;
                        }
                    }

                    return c;
            }
            default: {
                    return -1;
            }
        }
    }

    private static String bar(double share) {
        int seg = 24;
        int filled = (int)Math.round(Math.max(0.0, Math.min(1.0, share)) * seg);
        StringBuilder b = new StringBuilder("<b>");

        for (int i = 0; i < seg; i++) {
            if (i < filled) {
                double f = seg == 1 ? 0.0 : (double)i / (seg - 1);
                int r = 255;
                int g = (int)Math.round(74.0 + 107.0 * f);
                int bl = (int)Math.round(28.0 + 43.0 * f);
                b.append(String.format("<#%02X%02X%02X>|", r, g, bl));
            } else {
                b.append("<#3A3330>|");
            }
        }

        return b.append("</b>").toString();
    }

    public void tick() {
        this.ticks += 2;
        if (this.ticks % 20 == 0) {
            String best = null;
            int bestCount = 0;
            int modes = 0;

            for (Npc n : this.npcs.values()) {
                int c = this.count(n);
                if (c >= 0) {
                    modes++;
                }

                if (c > bestCount) {
                    bestCount = c;
                    best = n.id;
                }
            }

            this.topNpc = modes >= 2 ? best : null;
        }

        for (Npc n : this.npcs.values()) {
            if (n.body != null && n.body.isValid()) {
                if (n.lookAt) {
                    Player near = null;
                    double best = 64.0;

                    for (Player p : n.body.getWorld().getPlayers()) {
                        double d = p.getLocation().distanceSquared(n.body.getLocation());
                        if (d < best) {
                            best = d;
                            near = p;
                        }
                    }

                    if (near != null) {
                        Location eye = near.getEyeLocation();
                        Look.at(n.body, eye);
                    } else if (this.ticks % 20 == 0 && n.idle != Npc.Idle.SPIN && n.idle != Npc.Idle.LOOK_AROUND) {
                        n.body.setRotation(n.loc.getYaw(), 0.0F);
                    }
                }

                this.idle(n);
                if (this.ticks % 10 == 0) {
                    this.aura(n);
                    this.greet(n);
                }

                boolean animated = n.animatedTitle;

                for (String line : n.lines) {
                    animated |= line.contains("{phase}") || line.contains("{blink}");
                }

                boolean easing = n.barShown >= 0.0 && n.bar && this.ticks % 20 != 0;
                if (this.ticks % (!animated && !easing && !n.id.equals(this.topNpc) ? 20 : 4) == 0) {
                    this.refreshLabel(n);
                }

                // small steps, each interpolated a tick past the next one, so the spin and the bob never pause
                if (n.floater != null && n.floater.isValid() && this.ticks % 5 == 0) {
                    float ang = (float)(this.ticks / 5 % 16 * Math.PI / 8.0);
                    float bob = (float)(Math.sin(this.ticks / 5 * 0.3) * 0.07);
                    n.floater.setInterpolationDelay(0);
                    n.floater.setInterpolationDuration(6);
                    n.floater
                        .setTransformation(
                            new Transformation(
                                new Vector3f(0.0F, bob, 0.0F), new Quaternionf().rotateY(ang), new Vector3f(0.55F, 0.55F, 0.55F), new Quaternionf()
                            )
                        );
                }
            } else if (this.ticks % 20 == 0) {
                this.spawn(n);
            }
        }
    }

    /** Idle animations; look-at-players still wins when someone is close. */
    private void idle(Npc n) {
        switch (n.idle) {
            case WAVE:
                if (this.ticks % 80 == 0) {
                    n.body.swingMainHand();
                }
                break;
            case CHEER:
                if (this.ticks % 60 == 0) {
                    n.body.swingMainHand();
                    n.body.swingOffHand();
                    n.body.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, n.body.getLocation().add(0.0, 2.1 * n.scale, 0.0), 3, 0.3, 0.1, 0.3, 0.0);
                }
                break;
            case LOOK_AROUND:
                if (this.ticks % 40 == 0 && (!n.lookAt || !this.someoneNear(n))) {
                    float yaw = n.loc.getYaw() + (float)(Math.sin(this.ticks * 0.013) * 55.0);
                    n.body.setRotation(yaw, (float)(Math.sin(this.ticks * 0.021) * 15.0));
                }
                break;
            case SPIN:
                if (!n.lookAt || !this.someoneNear(n)) {
                    n.body.setRotation(n.loc.getYaw() + this.ticks * 1.5F % 360.0F, 0.0F);
                }
            case NONE:
        }
    }

    private boolean someoneNear(Npc n) {
        for (Player p : n.body.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(n.body.getLocation()) < 64.0) {
                return true;
            }
        }

        return false;
    }

    private void greet(Npc n) {
        if (n.action != Npc.Action.NONE && n.greetRadius > 0 && !"none".equalsIgnoreCase(n.greetText)) {
            long now = System.currentTimeMillis();

            for (Player p : n.body.getWorld().getPlayers()) {
                if (!(p.getLocation().distanceSquared(n.body.getLocation()) > (double)(n.greetRadius * n.greetRadius))) {
                    Long last = n.greeted.get(p.getUniqueId());
                    if (last == null || now - last >= 60000L) {
                        n.greeted.put(p.getUniqueId(), now);
                        n.body.swingMainHand();
                        p.spawnParticle(Particle.HAPPY_VILLAGER, n.body.getLocation().add(0.0, 2.0, 0.0), 4, 0.3, 0.2, 0.3, 0.0);

                        String what = switch (n.action) {
                            case WORLD -> "travel to <white>"
                                + Text.esc(this.plugin.modes().display(Bukkit.getWorld(n.value) == null ? n.body.getWorld() : Bukkit.getWorld(n.value)));
                            case HUB -> "go to the <white>Hub";
                            case LOBBY -> "play <white>Practice PvP";
                            case MENU -> "open <white>" + Text.esc(n.value);
                            case SELECTOR -> "pick a <white>server";
                            default -> "interact";
                        };
                        p.sendActionBar(n.greetText.isBlank() ? Text.mm("<#FFB547>➜ <gray>Click to " + what) : Text.mm(n.greetText.replace("{player}", Text.esc(p.getName()))));
                    }
                }
            }

            if (n.greeted.size() > 200) {
                n.greeted.entrySet().removeIf(e -> now - e.getValue() > 60000L);
            }
        }
    }

    private void aura(Npc n) {
        Location c = n.body.getLocation();
        World w = c.getWorld();
        double a = this.ticks * 0.08;
        String var6 = n.particle;
        switch (var6) {
            case "ring":
                for (int i = 0; i < 8; i++) {
                    double ang = a + i * Math.PI / 4.0;
                    w.spawnParticle(
                        Particle.DUST,
                        c.getX() + Math.cos(ang) * 0.7,
                        c.getY() + 0.1,
                        c.getZ() + Math.sin(ang) * 0.7,
                        1,
                        new DustOptions(Color.fromRGB(16763210), 0.9F)
                    );
                }
                break;
            case "hearts":
                w.spawnParticle(Particle.HEART, c.clone().add(0.0, 2.4, 0.0), 1, 0.3, 0.1, 0.3, 0.0);
                break;
            case "flames":
                for (int i = 0; i < 3; i++) {
                    double ang = a * 2.0 + i * Math.PI * 2.0 / 3.0;
                    w.spawnParticle(Particle.FLAME, c.getX() + Math.cos(ang) * 0.6, c.getY() + 0.2, c.getZ() + Math.sin(ang) * 0.6, 1, 0.0, 0.02, 0.0, 0.01);
                }
                break;
            case "portal":
                w.spawnParticle(Particle.PORTAL, c.clone().add(0.0, 1.0, 0.0), 10, 0.3, 0.6, 0.3, 0.3);
                break;
            case "enchant":
                w.spawnParticle(Particle.ENCHANT, c.clone().add(0.0, 1.8, 0.0), 8, 0.4, 0.4, 0.4, 0.6);
                break;
            case "end_rod":
                w.spawnParticle(Particle.END_ROD, c.clone().add(0.0, 1.0, 0.0), 2, 0.4, 0.7, 0.4, 0.01);
                break;
            case "notes":
                w.spawnParticle(Particle.NOTE, c.clone().add(0.0, 2.3, 0.0), 1, 0.3, 0.1, 0.3, 1.0);
                break;
            case "soul":
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, c.clone().add(0.0, 0.1, 0.0), 3, 0.4, 0.0, 0.4, 0.01);
        }
    }

    private Npc npcOf(Entity e) {
        String id = e.getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
        return id == null ? null : this.npcs.get(id);
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onRightClick(PlayerInteractEntityEvent e) {
        Npc n = this.npcOf(e.getRightClicked());
        if (n != null) {
            e.setCancelled(true);
            this.click(e.getPlayer(), n);
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onLeftClick(EntityDamageByEntityEvent e) {
        Npc n = this.npcOf(e.getEntity());
        if (n != null) {
            e.setCancelled(true);
            if (e.getDamager() instanceof Player p) {
                this.click(p, n);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onDamage(EntityDamageEvent e) {
        if (this.npcOf(e.getEntity()) != null) {
            e.setCancelled(true);
        }
    }

    private void click(Player p, Npc n) {
        long now = System.currentTimeMillis();
        Long last = this.lastClick.get(p.getUniqueId());
        if (!n.permission.isBlank() && !p.hasPermission(n.permission)) {
            p.sendActionBar(Text.mm("<red>You can't use this yet."));
        } else if (last == null || now - last >= n.cooldownMs) {
            this.lastClick.put(p.getUniqueId(), now);
            if (n.body != null) {
                n.body.swingMainHand();
                n.body.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, n.body.getLocation().add(0.0, 1.2, 0.0), 6, 0.3, 0.4, 0.3, 0.0);
            }

            if (!n.clickSound.isBlank() && !"none".equalsIgnoreCase(n.clickSound)) {
                Snd.play(p, n.clickSound, 0.8F, 1.2F);
            }
            switch (n.action) {
                case WORLD:
                    World w = Bukkit.getWorld(n.value);
                    if (w == null) {
                        p.sendMessage(Text.mm("<red>That world isn't loaded."));
                    } else {
                        this.plugin.modes().travel(p, w);
                    }
                    break;
                case HUB:
                    World h = this.plugin.modes().hubWorld();
                    if (h != null) {
                        this.plugin.modes().travel(p, h);
                    }
                    break;
                case LOBBY:
                    Location l = this.plugin.lobby().lobby();
                    if (l.getWorld() != null) {
                        this.plugin.modes().travel(p, l.getWorld());
                    }
                    break;
                case MENU:
                    this.openMenu(p, n.value);
                    break;
                case SELECTOR:
                    this.plugin.modes().selector(p);
                    break;
                case COMMAND:
                    p.performCommand(n.value.replace("{player}", p.getName()));
                    break;
                case CONSOLE:
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), n.value.replace("{player}", p.getName()));
                    break;
                case MESSAGE:
                    p.sendMessage(Text.mm(n.value.replace("{player}", Text.esc(p.getName()))));
            }
        }
    }

    private void openMenu(Player p, String menu) {
        if (menu.equals("worlds")) {
            this.plugin.modes().selector(p);
        } else if (this.plugin.sessions().state(p) == PlayerState.OUTSIDE) {
            Location l = this.plugin.lobby().lobby();
            if (l.getWorld() != null) {
                this.plugin.modes().travel(p, l.getWorld());
                Tasks.later(30L, () -> {
                    if (p.isOnline() && this.plugin.sessions().state(p) == PlayerState.LOBBY) {
                        this.openMenu(p, menu);
                    }
                });
            }
        } else {
            switch (menu) {
                case "duels":
                    this.plugin.dialogs().openQueue(p, false, Dialogs.QueueTab.MOST_PLAYED);
                    break;
                case "ranked":
                    this.plugin.dialogs().openQueue(p, true, Dialogs.QueueTab.MOST_PLAYED);
                    break;
                case "ffa":
                    this.plugin.dialogs().openFfa(p);
                    break;
                case "leaderboard":
                    this.plugin.dialogs().lobby().leaderboards(p);
                    break;
                case "settings":
                    this.plugin.dialogs().lobby().settings(p, Setting.Category.GAMEPLAY);
                    break;
                case "spectate":
                    this.plugin.dialogs().lobby().spectate(p);
                    break;
                case "events":
                    if (this.plugin.events() != null) {
                        this.plugin.events().menu(p);
                    }
                    break;
                case "stats":
                    p.performCommand("stats");
                    break;
                case "welcome":
                    p.performCommand("welcome");
                    break;
                case "kiteditor":
                    this.plugin.dialogs().lobby().kitEditor(p);
                    break;
                case "party":
                    if (this.plugin.parties().of(p) == null) {
                        this.plugin.parties().create(p);
                    } else {
                        this.plugin.dialogs().lobby().party(p);
                    }
                    break;
                default:
                    p.sendMessage(Text.mm("<red>Unknown menu: " + Text.esc(menu)));
            }
        }
    }

    public Npc create(String id, Location l, String skin, List<String> lines, Npc.Action action, String value) {
        Npc n = new Npc(id.toLowerCase(Locale.ROOT), l);
        n.skin = skin;
        n.lines.addAll(lines);
        n.action = action;
        n.value = value;
        Npc old = this.npcs.put(n.id, n);
        if (old != null) {
            this.despawn(old);
        }

        this.spawn(n);
        this.save();
        return n;
    }

    public Npc get(String id) {
        return this.npcs.get(id.toLowerCase(Locale.ROOT));
    }

    public void delete(Npc n) {
        this.despawn(n);
        this.npcs.remove(n.id);
        this.save();
    }

    public void update(Npc n) {
        this.spawn(n);
        this.save();
    }

    private LobbyDialogs ui() {
        return this.plugin.dialogs().lobby();
    }

    public void list(Player p) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (Npc n : this.npcs.values()) {
            String name = n.lines.isEmpty() ? n.id : n.lines.get(0);
            String where = n.loc != null && n.loc.getWorld() != null
                ? n.loc.getWorld().getName() + " " + n.loc.getBlockX() + " " + n.loc.getBlockY() + " " + n.loc.getBlockZ()
                : "unloaded world";
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(Material.PLAYER_HEAD),
                    Text.mm(name),
                    Text.mm("<gray>" + Text.esc(n.describe()) + " <dark_gray>• " + Text.esc(where)),
                    List.of(Text.mm(name), Text.mm("<dark_gray>id: " + n.id), Component.empty(), Text.mm("<green>▶ Click to edit")),
                    () -> this.edit(p, n)
                )
            );
        }

        List<ActionButton> b = new ArrayList<>();
        b.add(
            this.ui()
                .btn(
                    p,
                    "<green>+ Create NPC Here",
                    "<gray>At your position, facing your direction",
                    () -> this.plugin
                        .dialogs()
                        .text(
                            p,
                            Text.mm("<gold><b>New NPC"),
                            "NPC id (letters/numbers)",
                            "",
                            24,
                            id -> {
                                String clean = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
                                if (clean.isEmpty()) {
                                    p.sendMessage(Text.mm("<red>Invalid id."));
                                } else {
                                    Npc nx = this.create(
                                        clean,
                                        p.getLocation(),
                                        p.getName(),
                                        List.of("<gold><b>" + clean.toUpperCase(Locale.ROOT), "<gray>Click me!"),
                                        Npc.Action.NONE,
                                        ""
                                    );
                                    this.edit(p, nx);
                                }
                            }
                        )
                )
        );
        this.ui()
            .screen(
                p,
                LobbyDialogs.title("NPCs", this.npcs.size() + " NPC" + (this.npcs.size() == 1 ? "" : "s")),
                tiles,
                List.of(),
                Text.mm("<gray>No NPCs yet - create one where you stand."),
                b,
                1,
                this.ui().close(p)
            );
    }

    public void edit(Player p, Npc n) {
        List<Component> header = List.of(
            Text.mm("<gold><b>" + Text.esc(n.id) + "</b> <dark_gray>• <gray>skin <white>" + Text.esc(n.skin.isBlank() ? "default" : n.skin)),
            Text.mm("<gray>Action: <white>" + Text.esc(n.describe())),
            Text.mm(
                "<gray>Look at players "
                    + (n.lookAt ? "<green>on" : "<red>off")
                    + " <dark_gray>• <gray>Glow <white>"
                    + n.glow
                    + " <dark_gray>• <gray>Aura <white>"
                    + n.particle
                    + " <dark_gray>• <gray>Held <white>"
                    + (n.hand == null ? "-" : n.hand.getKey().getKey())
            )
        );
        List<ActionButton> b = new ArrayList<>();
        b.add(
            this.ui()
                .btn(
                    p,
                    "<gold>Name Lines",
                    "<gray>Separate lines with |",
                    () -> this.plugin
                        .dialogs()
                        .text(p, Text.mm("<gold><b>Name lines"), "Lines (MiniMessage, | = new line)", String.join("|", n.lines), 512, s -> {
                            n.lines.clear();

                            for (String part : s.split("\\|")) {
                                if (!part.isBlank()) {
                                    n.lines.add(part.trim());
                                }
                            }

                            this.update(n);
                            this.edit(p, n);
                        })
                )
        );
        b.add(
            this.ui()
                .btn(
                    p,
                    "<gold>Skin",
                    "<gray>Any Minecraft player name",
                    () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Skin"), "Player name", n.skin, 16, s -> {
                        n.skin = s.trim();
                        this.update(n);
                        this.edit(p, n);
                    })
                )
        );
        b.add(this.ui().btn(p, "<gold>Action", "<gray>What clicking does", () -> this.chooseAction(p, n)));
        b.add(this.ui().btn(p, "<yellow>Look At Players " + (n.lookAt ? "<green>ON" : "<red>OFF"), null, () -> {
            n.lookAt = !n.lookAt;
            this.update(n);
            this.edit(p, n);
        }));
        b.add(this.ui().btn(p, "<yellow>Glow", null, () -> this.choose(p, "Glow", Npc.GLOWS, n.glow, v -> {
            n.glow = v;
            this.update(n);
            this.edit(p, n);
        })));
        b.add(this.ui().btn(p, "<yellow>Aura", "<gray>Particles around the NPC", () -> this.choose(p, "Aura", Npc.PARTICLES, n.particle, v -> {
            n.particle = v;
            this.update(n);
            this.edit(p, n);
        })));
        b.add(this.ui().btn(p, "<yellow>Held Item = Your Hand", null, () -> {
            ItemStack h = p.getInventory().getItemInMainHand();
            n.hand = h.isEmpty() ? null : h.getType();
            this.update(n);
            this.edit(p, n);
        }));
        b.add(this.ui().btn(p, "<yellow>Players Bar " + (n.bar ? "<green>ON" : "<red>OFF"), "<gray>A bar of how many players play this mode", () -> {
            n.bar = !n.bar;
            this.update(n);
            this.edit(p, n);
        }));
        b.add(this.ui().btn(p, "<light_purple>Look…", "<gray>Armor, off-hand, size, pose, skin, idle animation", () -> this.lookPage(p, n)));
        b.add(this.ui().btn(p, "<light_purple>Name Tag…", "<gray>Style, colour, size, height, animated title", () -> this.labelPage(p, n)));
        b.add(this.ui().btn(p, "<light_purple>Behaviour…", "<gray>Sound, cooldown, permission, greeting, floating item", () -> this.behaviourPage(p, n)));
        b.add(this.ui().btn(p, "<aqua>Copy NPC Here", "<gray>A copy with every setting, where you stand", () -> {
            Npc c = this.copy(n, p.getLocation());
            this.edit(p, c);
        }));
        b.add(this.ui().btn(p, "<aqua>Move Here", "<gray>Your position and direction", () -> {
            n.loc = p.getLocation();
            this.update(n);
            this.edit(p, n);
        }));
        b.add(this.ui().btn(p, "<red>Delete", null, () -> {
            this.delete(n);
            this.list(p);
        }));
        this.ui()
            .screen(p, LobbyDialogs.title("NPC", Text.esc(n.id)), List.of(), header, null, b, 2, this.ui().btn(p, "<gray>« All NPCs", null, () -> this.list(p)));
    }

    // ------------------------------------------------------------------ editor pages

    private static String item(ItemStack i) {
        return i == null || i.isEmpty() ? "<dark_gray>none" : "<white>" + i.getType().getKey().getKey();
    }

    private void lookPage(Player p, Npc n) {
        List<Component> header = List.of(
            Text.mm("<gray>Helmet " + item(n.helmet) + " <dark_gray>• <gray>Chest " + item(n.chest) + " <dark_gray>• <gray>Legs " + item(n.legs) + " <dark_gray>• <gray>Boots " + item(n.boots)),
            Text.mm("<gray>Off-hand " + item(n.offhand) + " <dark_gray>• <gray>Size <white>" + String.format(Locale.US, "%.2f", n.scale) + " <dark_gray>• <gray>Pose <white>" + n.pose.label
                + " <dark_gray>• <gray>Idle <white>" + n.idle.label),
            Text.mm("<gray>Skin <white>" + (n.skinValue.isBlank() ? (n.skin.isBlank() ? "default" : Text.esc(n.skin) + " (looked up online)") : "copied textures"))
        );
        List<ActionButton> b = new ArrayList<>();
        b.add(this.ui().btn(p, "<yellow>Armor = Your Armor", "<gray>Copies what you wear (empty slots clear it)", () -> {
            org.bukkit.inventory.PlayerInventory inv = p.getInventory();
            n.helmet = clone(inv.getHelmet());
            n.chest = clone(inv.getChestplate());
            n.legs = clone(inv.getLeggings());
            n.boots = clone(inv.getBoots());
            this.update(n);
            this.lookPage(p, n);
        }));
        b.add(this.ui().btn(p, "<yellow>Off-hand = Your Off-hand", null, () -> {
            n.offhand = clone(p.getInventory().getItemInOffHand());
            this.update(n);
            this.lookPage(p, n);
        }));
        b.add(this.ui().btn(p, "<yellow>Size…", "<gray>0.5 (small) to 2.0 (giant)", () -> this.plugin.dialogs().number(p, Text.mm("<gold><b>Size"), "Scale", 0.5F, 2.0F, 0.05F, (float)n.scale, v -> {
            n.scale = Math.round(v * 20.0F) / 20.0;
            this.update(n);
            this.lookPage(p, n);
        })));
        b.add(this.ui().btn(p, "<yellow>Pose", null, () -> this.chooseEnum(p, "Pose", Npc.Pose.values(), n.pose, v -> {
            n.pose = v;
            n.sneak = v == Npc.Pose.SNEAK;
            this.update(n);
            this.lookPage(p, n);
        }, v -> v.label)));
        b.add(this.ui().btn(p, "<yellow>Idle Animation", null, () -> this.chooseEnum(p, "Idle animation", Npc.Idle.values(), n.idle, v -> {
            n.idle = v;
            this.update(n);
            this.lookPage(p, n);
        }, v -> v.label)));
        b.add(this.ui().btn(p, "<yellow>Skin = My Skin", "<gray>Saved in the file, no online lookup needed", () -> {
            for (com.destroystokyo.paper.profile.ProfileProperty pr : p.getPlayerProfile().getProperties()) {
                if (pr.getName().equals("textures")) {
                    n.skinValue = pr.getValue();
                    n.skinSignature = pr.getSignature() == null ? "" : pr.getSignature();
                }
            }

            if (n.skinValue.isBlank()) {
                p.sendMessage(Text.mm("<red>Your skin has no textures on this server (offline mode?) - use a skin name instead."));
            }

            n.skin = p.getName();
            this.update(n);
            this.lookPage(p, n);
        }));
        b.add(this.ui().btn(p, "<yellow>Skin by Name…", "<gray>Any Minecraft player name (looked up online)", () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Skin"), "Player name", n.skin, 16, v -> {
            n.skin = v.trim();
            n.skinValue = "";
            n.skinSignature = "";
            this.update(n);
            this.lookPage(p, n);
        })));
        this.ui().screen(p, LobbyDialogs.title("NPC", Text.esc(n.id) + " - look"), List.of(), header, null, b, 2, this.ui().btn(p, "<gray>« NPC", null, () -> this.edit(p, n)));
    }

    private void labelPage(Player p, Npc n) {
        List<Component> header = List.of(
            Text.mm("<gray>Style <white>" + n.labelStyle.label + " <dark_gray>• <gray>Colour <white>" + (n.labelColor.isBlank() ? "style" : n.labelColor) + " <dark_gray>• <gray>Opacity <white>"
                + (n.labelAlpha < 0 ? "style" : n.labelAlpha)),
            Text.mm("<gray>Size <white>" + String.format(Locale.US, "%.2f", n.labelScale) + " <dark_gray>• <gray>Height <white>+" + String.format(Locale.US, "%.2f", n.labelHeight)
                + " <dark_gray>• <gray>Shown " + (n.hideLabel ? "<red>no" : "<green>yes") + " <dark_gray>• <gray>Animated title " + (n.animatedTitle ? "<green>on" : "<red>off"))
        );
        List<ActionButton> b = new ArrayList<>();
        b.add(this.ui().btn(p, "<yellow>Style", null, () -> this.chooseEnum(p, "Name tag style", Npc.LabelStyle.values(), n.labelStyle, v -> {
            n.labelStyle = v;
            this.update(n);
            this.labelPage(p, n);
        }, v -> v.label)));
        b.add(this.ui().btn(p, "<yellow>Background Colour…", "<gray>#RRGGBB, empty = the style's", () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Background colour"), "#RRGGBB", n.labelColor, 7, v -> {
            n.labelColor = v.trim().matches("#?[0-9A-Fa-f]{6}") ? (v.trim().startsWith("#") ? v.trim() : "#" + v.trim()) : "";
            this.update(n);
            this.labelPage(p, n);
        })));
        b.add(this.ui().btn(p, "<yellow>Opacity…", "<gray>0 = clear, 255 = solid", () -> this.plugin.dialogs().number(p, Text.mm("<gold><b>Opacity"), "Alpha", 0.0F, 255.0F, 5.0F, n.labelAlpha < 0 ? n.labelStyle.alpha : n.labelAlpha, v -> {
            n.labelAlpha = Math.round(v);
            this.update(n);
            this.labelPage(p, n);
        })));
        b.add(this.ui().btn(p, "<yellow>Text Size…", null, () -> this.plugin.dialogs().number(p, Text.mm("<gold><b>Text size"), "Scale", 0.3F, 3.0F, 0.05F, (float)n.labelScale, v -> {
            n.labelScale = Math.round(v * 20.0F) / 20.0;
            this.update(n);
            this.labelPage(p, n);
        })));
        b.add(this.ui().btn(p, "<yellow>Height…", "<gray>Blocks above the head", () -> this.plugin.dialogs().number(p, Text.mm("<gold><b>Height"), "Extra height", -1.0F, 3.0F, 0.05F, (float)n.labelHeight, v -> {
            n.labelHeight = Math.round(v * 20.0F) / 20.0;
            this.update(n);
            this.labelPage(p, n);
        })));
        b.add(this.ui().btn(p, "<yellow>Animated Title " + (n.animatedTitle ? "<green>ON" : "<red>OFF"), "<gray>A moving gradient through the first line", () -> {
            n.animatedTitle = !n.animatedTitle;
            this.update(n);
            this.labelPage(p, n);
        }));
        b.add(this.ui().btn(p, "<yellow>Title Colours…", "<gray>Two hex colours, e.g. #FFB547:#FF4A1C", () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Title colours"), "#AAAAAA:#BBBBBB", n.titleColors, 32, v -> {
            if (v.trim().matches("#[0-9A-Fa-f]{6}(:#[0-9A-Fa-f]{6})+")) {
                n.titleColors = v.trim();
            }

            this.update(n);
            this.labelPage(p, n);
        })));
        b.add(this.ui().btn(p, "<yellow>Show Name Tag " + (n.hideLabel ? "<red>OFF" : "<green>ON"), null, () -> {
            n.hideLabel = !n.hideLabel;
            this.update(n);
            this.labelPage(p, n);
        }));
        this.ui().screen(p, LobbyDialogs.title("NPC", Text.esc(n.id) + " - name tag"), List.of(), header, null, b, 2, this.ui().btn(p, "<gray>« NPC", null, () -> this.edit(p, n)));
    }

    private void behaviourPage(Player p, Npc n) {
        List<Component> header = List.of(
            Text.mm("<gray>Click sound <white>" + Text.esc(n.clickSound) + " <dark_gray>• <gray>Cooldown <white>" + n.cooldownMs + "ms <dark_gray>• <gray>Permission <white>"
                + (n.permission.isBlank() ? "everyone" : Text.esc(n.permission))),
            Text.mm("<gray>Greeting <white>" + (n.greetText.isBlank() ? "default" : Text.esc(n.greetText)) + " <dark_gray>• <gray>radius <white>" + n.greetRadius
                + " <dark_gray>• <gray>Floating item " + (n.floatItem ? "<green>" + (n.floatMaterial == null ? "held item" : n.floatMaterial.getKey().getKey()) : "<red>off"))
        );
        List<ActionButton> b = new ArrayList<>();
        b.add(this.ui().btn(p, "<yellow>Click Sound", null, () -> this.choose(p, "Click sound", List.of(
            "ui.button.click", "entity.experience_orb.pickup", "block.note_block.pling", "block.note_block.bell", "entity.villager.yes", "block.amethyst_block.chime",
            "entity.player.levelup", "none"
        ), n.clickSound, v -> {
            n.clickSound = v;
            this.update(n);
            this.behaviourPage(p, n);
        })));
        b.add(this.ui().btn(p, "<yellow>Click Cooldown…", null, () -> this.plugin.dialogs().number(p, Text.mm("<gold><b>Cooldown"), "Milliseconds", 0.0F, 10000.0F, 100.0F, n.cooldownMs, v -> {
            n.cooldownMs = Math.round(v);
            this.update(n);
            this.behaviourPage(p, n);
        })));
        b.add(this.ui().btn(p, "<yellow>Permission…", "<gray>Empty = everyone can use it", () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Permission"), "Permission node", n.permission, 64, v -> {
            n.permission = v.trim();
            this.update(n);
            this.behaviourPage(p, n);
        })));
        b.add(this.ui().btn(p, "<yellow>Greeting…", "<gray>Action bar when walking up. Empty = default, none = off", () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Greeting"), "MiniMessage, {player}", n.greetText, 160, v -> {
            n.greetText = v.trim();
            this.update(n);
            this.behaviourPage(p, n);
        })));
        b.add(this.ui().btn(p, "<yellow>Greeting Radius…", null, () -> this.plugin.dialogs().number(p, Text.mm("<gold><b>Greeting radius"), "Blocks", 0.0F, 16.0F, 1.0F, n.greetRadius, v -> {
            n.greetRadius = Math.round(v);
            this.update(n);
            this.behaviourPage(p, n);
        })));
        b.add(this.ui().btn(p, "<yellow>Floating Item " + (n.floatItem ? "<green>ON" : "<red>OFF"), null, () -> {
            n.floatItem = !n.floatItem;
            this.update(n);
            this.behaviourPage(p, n);
        }));
        b.add(this.ui().btn(p, "<yellow>Floating Item = Your Hand", "<gray>Empty hand = use the held item", () -> {
            ItemStack h = p.getInventory().getItemInMainHand();
            n.floatMaterial = h.isEmpty() ? null : h.getType();
            this.update(n);
            this.behaviourPage(p, n);
        }));
        this.ui().screen(p, LobbyDialogs.title("NPC", Text.esc(n.id) + " - behaviour"), List.of(), header, null, b, 2, this.ui().btn(p, "<gray>« NPC", null, () -> this.edit(p, n)));
    }

    private static ItemStack clone(ItemStack i) {
        return i == null || i.isEmpty() ? null : i.clone();
    }

    private <E extends Enum<E>> void chooseEnum(Player p, String title, E[] values, E current, Consumer<E> done, java.util.function.Function<E, String> label) {
        List<Dialogs.Choice> c = new ArrayList<>();

        for (E e : values) {
            c.add(new Dialogs.Choice(e.name(), "<white>" + label.apply(e)));
        }

        this.plugin.dialogs().choice(p, Text.mm("<gold><b>" + title), title, c, current.name(), v -> done.accept(Enum.valueOf(current.getDeclaringClass(), v)));
    }

    /** A copy of an NPC with every setting, at {@code at}. */
    public Npc copy(Npc n, Location at) {
        String id = n.id + "_copy";
        int i = 2;

        while (this.npcs.containsKey(id)) {
            id = n.id + "_copy" + i++;
        }

        YamlConfiguration y = new YamlConfiguration();
        writeExtras(n, y, "n.");
        Npc c = new Npc(id, at);
        c.skin = n.skin;
        c.lines.addAll(n.lines);
        c.action = n.action;
        c.value = n.value;
        c.lookAt = n.lookAt;
        c.glow = n.glow;
        c.hand = n.hand;
        c.particle = n.particle;
        c.sneak = n.sneak;
        c.floatItem = n.floatItem;
        c.bar = n.bar;
        readExtras(c, y.getConfigurationSection("n"));
        this.npcs.put(id, c);
        this.save();
        this.spawn(c);
        return c;
    }

    private void choose(Player p, String title, List<String> options, String current, Consumer<String> done) {
        List<Dialogs.Choice> c = new ArrayList<>();

        for (String o : options) {
            c.add(new Dialogs.Choice(o, "<white>" + o));
        }

        this.plugin.dialogs().choice(p, Text.mm("<gold><b>" + title), title, c, current, done);
    }

    private void chooseAction(Player p, Npc n) {
        List<Dialogs.Choice> c = new ArrayList<>();

        for (Npc.Action a : Npc.Action.values()) {
            c.add(new Dialogs.Choice(a.name(), "<white>" + a.label));
        }

        this.plugin
            .dialogs()
            .choice(
                p,
                Text.mm("<gold><b>Action"),
                "When clicked",
                c,
                n.action.name(),
                v -> {
                    n.action = Npc.Action.valueOf(v);
                    switch (n.action) {
                        case WORLD:
                            List<Dialogs.Choice> worlds = new ArrayList<>();

                            for (World w : Bukkit.getWorlds()) {
                                WorldMode m = this.plugin.modes().mode(w);
                                if (m.smp() || m == WorldMode.HUB) {
                                    worlds.add(new Dialogs.Choice(w.getName(), m.color + w.getName() + " <gray>(" + m.display + ")"));
                                }
                            }

                            if (worlds.isEmpty()) {
                                p.sendMessage(Text.mm("<red>No SMP / hub worlds yet - set modes in the World Manager."));
                                this.edit(p, n);
                                return;
                            }

                            this.plugin.dialogs().choice(p, Text.mm("<gold><b>Target world"), "World", worlds, n.value, wx -> {
                                n.value = wx;
                                this.update(n);
                                this.edit(p, n);
                            });
                            break;
                        case HUB:
                        case LOBBY:
                        case SELECTOR:
                        default:
                            n.value = "";
                            this.update(n);
                            this.edit(p, n);
                            break;
                        case MENU:
                            this.choose(p, "Menu", Npc.MENUS, n.value, mx -> {
                                n.value = mx;
                                this.update(n);
                                this.edit(p, n);
                            });
                            break;
                        case COMMAND:
                        case CONSOLE:
                        case MESSAGE:
                            this.plugin
                                .dialogs()
                                .text(
                                    p,
                                    Text.mm("<gold><b>" + n.action.label),
                                    n.action == Npc.Action.MESSAGE ? "Message (MiniMessage, {player})" : "Command without / ({player})",
                                    n.value,
                                    256,
                                    s -> {
                                        n.value = s.startsWith("/") ? s.substring(1) : s;
                                        this.update(n);
                                        this.edit(p, n);
                                    }
                                );
                    }
                }
            );
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            if (!p.hasPermission("vortexpvp.admin")) {
                p.sendMessage(Text.mm("<red>You don't have permission."));
                return true;
            } else if (args.length >= 2) {
                Npc n = this.get(args[1]);
                String var7 = args[0].toLowerCase(Locale.ROOT);
                switch (var7) {
                    case "create":
                        this.edit(p, this.create(args[1], p.getLocation(), p.getName(), List.of("<gold><b>" + args[1]), Npc.Action.NONE, ""));
                        return true;
                    case "edit":
                        if (n != null) {
                            this.edit(p, n);
                            return true;
                        }
                        break;
                    case "tp":
                        if (n != null && n.loc != null) {
                            Tp.to(p, n.loc);
                            return true;
                        }
                        break;
                    case "delete":
                        if (n != null) {
                            this.delete(n);
                            p.sendMessage(Text.mm("<gray>NPC deleted."));
                            return true;
                        }
                }

                p.sendMessage(Text.mm("<red>Unknown NPC."));
                return true;
            } else {
                this.list(p);
                return true;
            }
        } else {
            sender.sendMessage("Players only.");
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("create", "edit", "tp", "delete"));
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("create")) {
            out.addAll(this.npcs.keySet());
        }

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(last));
        return out;
    }
}
