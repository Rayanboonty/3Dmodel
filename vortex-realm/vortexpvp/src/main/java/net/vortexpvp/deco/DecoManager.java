package net.vortexpvp.deco;

import io.papermc.paper.registry.data.dialog.ActionButton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.dialog.Dialogs;
import net.vortexpvp.dialog.LobbyDialogs;
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
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DecoManager implements Listener, TabExecutor {
    private final VortexPvP plugin;
    private final NamespacedKey key;
    private final File file;
    private final Map<String, DecoManager.Placed> placed = new LinkedHashMap<>();
    private final Map<String, List<Display>> live = new HashMap<>();
    private int counter;
    private int ticks;

    public DecoManager(VortexPvP plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "deco");
        this.file = new File(plugin.getDataFolder(), "decorations.yml");
    }

    public void load() {
        ShrineModels.refresh(this.plugin);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        this.counter = y.getInt("counter", 0);
        ConfigurationSection s = y.getConfigurationSection("placed");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                ConfigurationSection d = s.getConfigurationSection(id);
                if (d != null && DecoModels.get(d.getString("model", "")) != null) {
                    DecoManager.Placed p = new DecoManager.Placed(id);
                    p.model = d.getString("model");
                    p.world = d.getString("world");
                    p.x = d.getDouble("x");
                    p.y = d.getDouble("y");
                    p.z = d.getDouble("z");
                    p.yaw = (float)d.getDouble("yaw");
                    p.scale = (float)d.getDouble("scale", 1.0);
                    this.placed.put(id, p);
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

        for (DecoManager.Placed p : this.placed.values()) {
            this.spawnIfLoaded(p);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("3D decorations placed with /deco (edit them in game)."));
        y.set("counter", this.counter);

        for (DecoManager.Placed p : this.placed.values()) {
            String b = "placed." + p.id + ".";
            y.set(b + "model", p.model);
            y.set(b + "world", p.world);
            y.set(b + "x", p.x);
            y.set(b + "y", p.y);
            y.set(b + "z", p.z);
            y.set(b + "yaw", p.yaw);
            y.set(b + "scale", p.scale);
        }

        try {
            y.save(this.file);
        } catch (IOException var5) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save decorations.yml", (Throwable)var5);
        }
    }

    public int count() {
        return this.placed.size();
    }

    public int removeAll() {
        int n = this.placed.size();

        for (DecoManager.Placed p : this.placed.values()) {
            this.despawn(p);
        }

        this.placed.clear();
        this.live.clear();

        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(this.key, PersistentDataType.STRING)) {
                    e.remove();
                }
            }
        }

        this.save();
        return n;
    }

    public void confirmRemoveAll(Player p, Runnable back) {
        this.plugin
            .dialogs()
            .confirm(
                p,
                Text.mm("<red><b>Remove all " + this.placed.size() + " decorations?"),
                List.of(
                    Text.mm("<gray>Every 3D decoration placed with /deco disappears, in every world."),
                    Text.mm("<gray>Builds made of blocks stay. This can't be undone.")
                ),
                () -> {
                    int n = this.removeAll();
                    p.sendMessage(Text.mm("<green>Removed <white>" + n + "</white> decorations."));
                    Snd.play(p, "block.amethyst_block.break", 1.0F, 0.6F);
                    if (back != null) {
                        back.run();
                    }
                }
            );
    }

    private void spawnIfLoaded(DecoManager.Placed p) {
        Location l = p.loc();
        if (l != null && l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) {
            this.spawn(p);
        }
    }

    private void despawn(DecoManager.Placed p) {
        List<Display> list = this.live.remove(p.id);
        if (list != null) {
            for (Display d : list) {
                d.remove();
            }
        }
    }

    private void spawn(DecoManager.Placed p) {
        this.despawn(p);
        DecoModels.Model model = DecoModels.get(p.model);
        Location at = p.loc();
        if (model != null && at != null) {
            List<Display> list = new ArrayList<>();
            int interp = model.anim() == DecoModels.Anim.NONE ? 0 : 5;

            for (DecoModels.Part part : model.parts()) {
                Transformation t = transform(part, p.scale, 0.0F);

                Display d = (Display)(switch (part.kind()) {
                    case BLOCK -> (BlockDisplay)at.getWorld().spawn(at, BlockDisplay.class, e -> e.setBlock(part.material().createBlockData()));
                    case ITEM -> (ItemDisplay)at.getWorld().spawn(at, ItemDisplay.class, e -> {
                        e.setItemStack(new ItemStack(part.material()));
                        e.setItemDisplayTransform(ItemDisplayTransform.FIXED);
                    });
                    case TEXT -> (TextDisplay)at.getWorld().spawn(at, TextDisplay.class, e -> {
                        e.text(Text.mm(part.text()));
                        e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        e.setDefaultBackground(false);
                        e.setShadowed(true);
                        e.setBillboard(Billboard.FIXED);
                    });
                });
                d.setPersistent(false);
                d.setTransformation(t);
                d.setTeleportDuration(interp);
                d.setViewRange(1.5F);
                if (part.glow() || part.kind() == DecoModels.Kind.TEXT) {
                    d.setBrightness(new Brightness(15, 15));
                }

                d.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, p.id);
                list.add(d);
            }

            this.live.put(p.id, list);
        }
    }

    private static Transformation transform(DecoModels.Part part, float s, float rotorAngle) {
        Vector3f tr = new Vector3f(part.translation()).mul(s);
        Quaternionf rot = new Quaternionf(part.rotation());
        if (part.rotor()) {
            rot = new Quaternionf().rotateZ(rotorAngle).mul(rot);
        }

        Vector3f sc = new Vector3f(part.scale()).mul(s);
        if (part.kind() == DecoModels.Kind.ITEM) {
            sc = new Vector3f(part.scale()).mul(s);
        }

        return new Transformation(tr, rot, sc, new Quaternionf());
    }

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        int cx = e.getChunk().getX();
        int cz = e.getChunk().getZ();
        String w = e.getWorld().getName();
        Tasks.later(2L, () -> {
            for (DecoManager.Placed p : this.placed.values()) {
                if (p.world.equals(w) && (int)Math.floor(p.x) >> 4 == cx && (int)Math.floor(p.z) >> 4 == cz) {
                    List<Display> list = this.live.get(p.id);
                    if (list == null || list.isEmpty() || !list.get(0).isValid()) {
                        this.spawn(p);
                    }
                }
            }
        });
    }

    public void tick() {
        this.ticks++;
        float angle = (float)Math.toRadians(this.ticks * 4.5F % 360.0F);

        for (DecoManager.Placed p : this.placed.values()) {
            List<Display> list = this.live.get(p.id);
            if (list != null && !list.isEmpty()) {
                if (!list.get(0).isValid()) {
                    this.live.remove(p.id);
                } else {
                    DecoModels.Model m = DecoModels.get(p.model);
                    if (m != null && m.anim() != DecoModels.Anim.NONE) {
                        Location base = p.loc();
                        if (base != null && net.vortexpvp.util.Viewers.near(base)) {
                            if (m.anim() == DecoModels.Anim.ROTOR) {
                                List<DecoModels.Part> parts = m.parts();

                                for (int i = 0; i < list.size() && i < parts.size(); i++) {
                                    if (parts.get(i).rotor()) {
                                        Display d = list.get(i);
                                        d.setInterpolationDelay(0);
                                        d.setInterpolationDuration(5);
                                        d.setTransformation(transform(parts.get(i), p.scale, angle));
                                    }
                                }
                            } else {
                                Location to = base.clone();
                                if (m.anim() == DecoModels.Anim.SPIN || m.anim() == DecoModels.Anim.SPIN_BOB) {
                                    to.setYaw(p.yaw + this.ticks * 4.5F);
                                }

                                if (m.anim() == DecoModels.Anim.BOB || m.anim() == DecoModels.Anim.SPIN_BOB) {
                                    to.add(0.0, Math.sin(this.ticks * 0.175) * 0.18 * p.scale, 0.0);
                                }

                                for (Display d : list) {
                                    d.teleport(to);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (this.ticks % 20 == 0) {
            for (DecoManager.Placed px : this.placed.values()) {
                if (!this.live.containsKey(px.id)) {
                    this.spawnIfLoaded(px);
                }
            }
        }
    }

    public void shutdown() {
        for (DecoManager.Placed p : this.placed.values()) {
            this.despawn(p);
        }

        this.save();
    }

    private DecoManager.Placed place(Player p, String model) {
        Block target = p.getTargetBlockExact(8);
        Location at;
        if (target != null && !target.getType().isAir()) {
            at = target.getLocation().add(0.5, 1.0, 0.5);
        } else {
            at = p.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
        }

        DecoManager.Placed d = this.placeAt(at, model, Math.round((p.getLocation().getYaw() + 180.0F) / 15.0F) * 15.0F);
        Snd.play(p, "block.amethyst_block.place", 1.0F, 1.2F);
        return d;
    }

    /** Places a decoration at an exact spot (the spawn builders use it); it is saved like any other. */
    public DecoManager.Placed placeAt(Location at, String model, float yaw) {
        DecoManager.Placed d = new DecoManager.Placed("d" + ++this.counter);
        d.model = model;
        d.world = at.getWorld().getName();
        d.x = at.getX();
        d.y = at.getY();
        d.z = at.getZ();
        d.yaw = yaw;
        this.placed.put(d.id, d);
        this.spawn(d);
        this.save();
        return d;
    }

    private DecoManager.Placed nearest(Player p, double max) {
        DecoManager.Placed best = null;
        double bd = max * max;

        for (DecoManager.Placed d : this.placed.values()) {
            if (d.world.equals(p.getWorld().getName())) {
                double dx = d.x - p.getLocation().getX();
                double dy = d.y - p.getLocation().getY();
                double dz = d.z - p.getLocation().getZ();
                double dist = dx * dx + dy * dy + dz * dz;
                if (dist < bd) {
                    bd = dist;
                    best = d;
                }
            }
        }

        return best;
    }

    private LobbyDialogs ui() {
        return this.plugin.dialogs().lobby();
    }

    public void menu(Player p) {
        ShrineModels.refresh(this.plugin);
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (String cat : DecoModels.categories()) {
            List<DecoModels.Model> models = DecoModels.all().values().stream().filter(m -> m.category().equals(cat)).toList();
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(models.get(0).icon()),
                    Text.mm("<gold><b>" + cat),
                    Text.mm("<gray>" + models.size() + " models"),
                    List.of(Text.mm("<gold><b>" + cat), Component.empty(), Text.mm("<green>▶ Click to browse")),
                    () -> this.category(p, cat)
                )
            );
        }

        List<ActionButton> b = new ArrayList<>();
        b.add(this.ui().btn(p, "<aqua>Edit Nearest", "<gray>The decoration closest to you", () -> {
            DecoManager.Placed d = this.nearest(p, 12.0);
            if (d == null) {
                p.sendMessage(Text.mm("<red>No decoration within 12 blocks."));
                p.closeDialog();
            } else {
                this.edit(p, d);
            }
        }));
        b.add(this.ui().btn(p, "<yellow>Showcase", "<gray>Place one of every model in rows, starting here", () -> {
            p.closeDialog();
            this.showcase(p);
        }));
        b.add(this.ui().btn(p, "<light_purple>Holograms", "<gray>Floating text: welcome, Discord, tier tests...", () -> this.plugin.textHolograms().menu(p)));
        b.add(this.ui().btn(p, "<red>Remove All", "<gray>Every placed decoration, after asking", () -> this.confirmRemoveAll(p, () -> this.menu(p))));
        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Decorations", DecoModels.all().size() + " 3D models • " + this.placed.size() + " placed"),
                tiles,
                List.of(),
                null,
                b,
                2,
                this.ui().close(p)
            );
    }

    private void category(Player p, String cat) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (DecoModels.Model m : DecoModels.all().values()) {
            if (m.category().equals(cat)) {
                String anim = switch (m.anim()) {
                    case SPIN -> "spins";
                    case BOB -> "floats";
                    case SPIN_BOB -> "spins & floats";
                    case ROTOR -> "turning blades";
                    default -> "still";
                };
                tiles.add(
                    new LobbyDialogs.Tile(
                        new ItemStack(m.icon()),
                        Text.mm("<white><b>" + m.name()),
                        Text.mm("<gray>" + m.parts().size() + " parts <dark_gray>• <gray>" + anim),
                        List.of(Text.mm("<white><b>" + m.name()), Component.empty(), Text.mm("<green>▶ Click to place where you look")),
                        () -> this.edit(p, this.place(p, m.id()))
                    )
                );
            }
        }

        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Decorations", cat),
                tiles,
                List.of(),
                null,
                List.of(this.ui().btn(p, "<gray>« Categories", null, () -> this.menu(p))),
                1,
                this.ui().close(p)
            );
    }

    private void edit(Player p, DecoManager.Placed d) {
        DecoModels.Model m = DecoModels.get(d.model);
        List<Component> header = List.of(
            Text.mm("<white><b>" + (m == null ? d.model : m.name()) + "</b> <dark_gray>#" + d.id),
            Text.mm(
                "<gray>Turned <white>"
                    + Math.round(d.yaw)
                    + "°</white>, size <white>"
                    + Math.round(d.scale * 100.0F)
                    + "%</white>, at <white>"
                    + String.format(Locale.ROOT, "%.1f %.1f %.1f", d.x, d.y, d.z)
            )
        );
        List<ActionButton> b = new ArrayList<>();
        b.add(this.ui().btn(p, "<aqua>⟲ Turn left", "<gray>15°", () -> this.change(p, d, () -> d.yaw -= 15.0F)));
        b.add(this.ui().btn(p, "<aqua>Turn right ⟳", "<gray>15°", () -> this.change(p, d, () -> d.yaw += 15.0F)));
        b.add(this.ui().btn(p, "<green>＋ Bigger", "<gray>+10%", () -> this.change(p, d, () -> d.scale = Math.min(6.0F, d.scale * 1.1F))));
        b.add(this.ui().btn(p, "<red>－ Smaller", "<gray>-10%", () -> this.change(p, d, () -> d.scale = Math.max(0.2F, d.scale / 1.1F))));
        b.add(this.ui().btn(p, "<white>▲ Up", "<gray>0.25 block", () -> this.change(p, d, () -> d.y += 0.25)));
        b.add(this.ui().btn(p, "<white>▼ Down", "<gray>0.25 block", () -> this.change(p, d, () -> d.y -= 0.25)));
        b.add(this.ui().btn(p, "<white>Push away", "<gray>0.5 block away from you", () -> this.change(p, d, () -> this.nudge(p, d, 0.5))));
        b.add(this.ui().btn(p, "<white>Pull closer", "<gray>0.5 block towards you", () -> this.change(p, d, () -> this.nudge(p, d, -0.5))));
        b.add(this.ui().btn(p, "<yellow>Move to me", "<gray>Where you stand", () -> this.change(p, d, () -> {
            d.world = p.getWorld().getName();
            d.x = p.getLocation().getBlockX() + 0.5;
            d.y = p.getLocation().getY();
            d.z = p.getLocation().getBlockZ() + 0.5;
        })));
        b.add(this.ui().btn(p, "<gold>Copy", "<gray>Place another one where you look", () -> {
            DecoManager.Placed c = this.place(p, d.model);
            c.scale = d.scale;
            c.yaw = d.yaw;
            this.spawn(c);
            this.save();
            this.edit(p, c);
        }));
        b.add(this.ui().btn(p, "<red><b>Remove", null, () -> {
            this.despawn(d);
            this.placed.remove(d.id);
            this.save();
            Snd.play(p, "block.amethyst_block.break", 1.0F, 0.8F);
            this.menu(p);
        }));
        b.add(this.ui().btn(p, "<gray>Catalogue", null, () -> this.menu(p)));
        this.ui()
            .screen(
                p,
                LobbyDialogs.title("Decoration", "Edit"),
                List.of(),
                header,
                null,
                b,
                2,
                Dialogs.button(Text.mm("<green>Done"), null, 200, this.plugin.dialogs().action(p, p::closeDialog))
            );
    }

    private void nudge(Player p, DecoManager.Placed d, double by) {
        double dx = d.x - p.getLocation().getX();
        double dz = d.z - p.getLocation().getZ();
        double len = Math.max(0.01, Math.hypot(dx, dz));
        d.x += dx / len * by;
        d.z += dz / len * by;
    }

    private void change(Player p, DecoManager.Placed d, Runnable r) {
        r.run();
        this.spawn(d);
        this.save();
        Snd.click(p);
        this.edit(p, d);
    }

    private void showcase(Player p) {
        Location start = p.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
        int i = 0;

        for (DecoModels.Model m : DecoModels.all().values()) {
            DecoManager.Placed d = new DecoManager.Placed("d" + ++this.counter);
            d.model = m.id();
            d.world = start.getWorld().getName();
            d.x = start.getX() + i % 6 * 7;
            d.y = start.getY();
            d.z = start.getZ() + i / 6 * 7;
            d.yaw = 180.0F;
            this.placed.put(d.id, d);
            this.spawn(d);
            i++;
        }

        this.save();
        p.sendMessage(Text.mm("<green>Placed " + i + " decorations. <gray>Remove one: stand next to it, /deco -> Edit Nearest -> Remove."));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            return true;
        } else if (!p.hasPermission("vortexpvp.admin")) {
            p.sendMessage(Text.mm("<red>You don't have permission."));
            return true;
        } else if (args.length >= 1 && DecoModels.get(args[0].toLowerCase(Locale.ROOT)) != null) {
            this.edit(p, this.place(p, args[0].toLowerCase(Locale.ROOT)));
            return true;
        } else if (args.length < 1 || !args[0].equalsIgnoreCase("clear") && !args[0].equalsIgnoreCase("removeall")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("near")) {
                DecoManager.Placed d = this.nearest(p, 12.0);
                if (d == null) {
                    p.sendMessage(Text.mm("<red>No decoration within 12 blocks."));
                } else {
                    this.edit(p, d);
                }

                return true;
            } else {
                this.menu(p);
                return true;
            }
        } else {
            this.confirmRemoveAll(p, null);
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("near");
            out.add("clear");
            out.addAll(DecoModels.all().keySet());
            out.removeIf(s -> !s.startsWith(args[0].toLowerCase(Locale.ROOT)));
        }

        return out;
    }

    public List<String> models() {
        return new ArrayList<>(DecoModels.all().keySet());
    }

    Material iconOf(String model) {
        DecoModels.Model m = DecoModels.get(model);
        return m == null ? Material.BARRIER : m.icon();
    }

    static final class Placed {
        final String id;
        String model;
        String world;
        double x;
        double y;
        double z;
        float yaw;
        float scale = 1.0F;

        Placed(String id) {
            this.id = id;
        }

        Location loc() {
            World w = Bukkit.getWorld(this.world);
            return w == null ? null : new Location(w, this.x, this.y, this.z, this.yaw, 0.0F);
        }
    }
}
