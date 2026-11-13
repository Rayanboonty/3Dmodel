package net.vortexpvp.extra;

import io.papermc.paper.registry.data.dialog.ActionButton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.dialog.LobbyDialogs;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Fmt;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class SmpAdmin implements CommandExecutor, TabCompleter, Listener {
    private final VortexPvP plugin;
    private final File file;
    private final Map<UUID, SmpAdmin.Seen> seen = new HashMap<>();
    private final Set<UUID> frozen = new HashSet<>();
    private boolean dirty;

    public SmpAdmin(VortexPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "smp-tracker.yml");
    }

    public void load() {
        this.seen.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                try {
                    UUID u = UUID.fromString(k);
                    this.seen
                        .put(
                            u,
                            new SmpAdmin.Seen(
                                u,
                                sec.getString(k + ".name", "?"),
                                sec.getString(k + ".world", "world"),
                                sec.getDouble(k + ".x"),
                                sec.getDouble(k + ".y"),
                                sec.getDouble(k + ".z"),
                                sec.getLong(k + ".time")
                            )
                        );
                } catch (IllegalArgumentException var6) {
                }
            }
        }
    }

    public void save() {
        if (this.dirty) {
            this.dirty = false;
            YamlConfiguration y = new YamlConfiguration();

            for (SmpAdmin.Seen s : this.seen.values()) {
                String b = "players." + s.uuid() + ".";
                y.set(b + "name", s.name());
                y.set(b + "world", s.world());
                y.set(b + "x", Math.round(s.x() * 10.0) / 10.0);
                y.set(b + "y", Math.round(s.y() * 10.0) / 10.0);
                y.set(b + "z", Math.round(s.z() * 10.0) / 10.0);
                y.set(b + "time", s.time());
            }

            try {
                y.save(this.file);
            } catch (IOException var5) {
                this.plugin.getLogger().log(Level.WARNING, "Could not save smp-tracker.yml", (Throwable)var5);
            }
        }
    }

    private boolean isSmp(Player p) {
        return !this.plugin.worlds().managed(p.getWorld());
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (this.isSmp(p)) {
            Location l = p.getLocation();
            this.seen
                .put(
                    p.getUniqueId(),
                    new SmpAdmin.Seen(p.getUniqueId(), p.getName(), l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), System.currentTimeMillis())
                );
            this.dirty = true;
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onMove(PlayerMoveEvent e) {
        if (this.frozen.contains(e.getPlayer().getUniqueId())) {
            Location f = e.getFrom();
            Location t = e.getTo();
            if (f.getX() != t.getX() || f.getY() != t.getY() || f.getZ() != t.getZ()) {
                Location back = f.clone();
                back.setYaw(t.getYaw());
                back.setPitch(t.getPitch());
                e.setTo(back);
                e.getPlayer().sendActionBar(Text.mm("<red><b>You are frozen by staff.</b> <gray>Don't log out."));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("restore")) {
            if (!sender.hasPermission("vortexpvp.smpadmin")) {
                sender.sendMessage(Text.mm("<red>You don't have permission."));
            } else {
                this.plugin.modes().restore().command(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            }

            return true;
        }

        if (sender instanceof Player p) {
            if (!p.hasPermission("vortexpvp.smpadmin")) {
                p.sendMessage(Text.mm("<red>You don't have permission."));
                return true;
            } else if (args.length >= 1) {
                Player t = Bukkit.getPlayerExact(args[0]);
                if (t != null) {
                    this.player(p, t);
                    return true;
                } else {
                    p.sendMessage(Text.mm("<red>That player is not online."));
                    return true;
                }
            } else {
                this.overview(p);
                return true;
            }
        } else {
            sender.sendMessage("Players only.");
            return true;
        }
    }

    private LobbyDialogs ui() {
        return this.plugin.dialogs().lobby();
    }

    private static String pos(Location l) {
        return l.getWorld().getName() + " <dark_gray>•</dark_gray> " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    public void overview(Player admin) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        List<Player> list = new ArrayList<>();

        for (Player o : Bukkit.getOnlinePlayers()) {
            if (this.isSmp(o)) {
                list.add(o);
            }
        }

        list.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        for (Player ox : list) {
            boolean fz = this.frozen.contains(ox.getUniqueId());
            List<Component> hover = List.of(
                Text.mm("<white><b>" + Text.esc(ox.getName())),
                Text.mm("<gray>" + pos(ox.getLocation())),
                Text.mm("<gray>Health: <red>" + Math.round(ox.getHealth()) + " ❤ <gray>Food: <gold>" + ox.getFoodLevel()),
                Text.mm("<gray>Mode: <white>" + ox.getGameMode().name().toLowerCase(Locale.ROOT) + " <gray>Ping: " + Fmt.pingColored(ox.getPing())),
                Component.empty(),
                Text.mm("<green>▶ Click to manage")
            );
            tiles.add(
                new LobbyDialogs.Tile(
                    Items.of(Material.PLAYER_HEAD).skull(ox).build(),
                    Text.mm((fz ? "<aqua>❄ " : "<white>") + "<b>" + Text.esc(ox.getName())),
                    Text.mm("<gray>" + pos(ox.getLocation())),
                    hover,
                    () -> this.player(admin, ox)
                )
            );
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(this.ui().btn(admin, "<gold>Logout Spots", "<gray>Where offline players logged out", () -> this.logouts(admin)));
        buttons.add(this.ui().btn(admin, "<green>⟳ Refresh", null, () -> this.overview(admin)));
        String url = Cfg.s(Opt.SMP_MAP_URL);
        if (url != null && !url.isBlank()) {
            buttons.add(this.ui().urlButton("<aqua>\ud83d\uddfa Web Map", url));
        } else {
            buttons.add(
                this.ui()
                    .btn(
                        admin,
                        "<dark_gray>\ud83d\uddfa Web Map (not set)",
                        "<gray>Install BlueMap or squaremap, then set the URL in\n<gray>/pvpadmin → Settings → SMP & Extras",
                        () -> this.overview(admin)
                    )
            );
        }

        this.ui()
            .screen(
                admin,
                LobbyDialogs.title("SMP Admin", list.size() + " player" + (list.size() == 1 ? "" : "s") + " in the SMP"),
                tiles,
                List.of(),
                Text.mm("<gray>Nobody is in the SMP right now."),
                buttons,
                3,
                this.ui().close(admin)
            );
    }

    private void player(Player admin, Player t) {
        if (!t.isOnline()) {
            this.overview(admin);
        } else {
            boolean fz = this.frozen.contains(t.getUniqueId());
            AttributeInstance maxHp = t.getAttribute(Attribute.MAX_HEALTH);
            long minutes = t.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
            List<Component> header = List.of(
                Text.mm("<white><b>" + Text.esc(t.getName()) + "</b> " + (this.isSmp(t) ? "<green>SMP" : "<gold>PvP") + (fz ? " <aqua>❄ frozen" : "")),
                Text.mm("<gray>" + pos(t.getLocation())),
                Text.mm(
                    "<gray>Health <red>"
                        + Math.round(t.getHealth())
                        + "/"
                        + Math.round(maxHp == null ? 20.0 : maxHp.getValue())
                        + " <gray>Food <gold>"
                        + t.getFoodLevel()
                        + " <gray>Level <green>"
                        + t.getLevel()
                ),
                Text.mm("<gray>Playtime <white>" + minutes / 60L + "h " + minutes % 60L + "m <gray>Ping " + Fmt.pingColored(t.getPing()))
            );
            List<ActionButton> b = new ArrayList<>();
            b.add(this.ui().btn(admin, "<green>Teleport To", null, () -> {
                admin.closeDialog();
                Tp.to(admin, t.getLocation());
            }));
            b.add(this.ui().btn(admin, "<yellow>Bring Here", "<gray>Only works for SMP players", () -> {
                admin.closeDialog();
                if (this.isSmp(t) && !this.plugin.worlds().managed(admin.getWorld())) {
                    Tp.to(t, admin.getLocation());
                } else {
                    admin.sendMessage(Text.mm("<red>Both of you must be in the SMP."));
                }
            }));
            b.add(this.ui().btn(admin, "<gold>Inventory", "<gray>Live, editable", () -> {
                admin.closeDialog();
                admin.openInventory(t.getInventory());
            }));
            b.add(this.ui().btn(admin, "<light_purple>Ender Chest", "<gray>Live, editable", () -> {
                admin.closeDialog();
                admin.openInventory(t.getEnderChest());
            }));
            b.add(this.ui().btn(admin, fz ? "<aqua>Unfreeze" : "<aqua>❄ Freeze", null, () -> {
                if (this.frozen.remove(t.getUniqueId())) {
                    t.sendMessage(Text.mm("<green>You were unfrozen."));
                } else {
                    this.frozen.add(t.getUniqueId());
                    t.sendMessage(Text.mm("<red><b>You were frozen by staff.</b> <gray>Don't log out."));
                    Snd.play(t, "block.anvil.land", 0.6F, 1.4F);
                }

                this.player(admin, t);
            }));
            b.add(this.ui().btn(admin, "<red>Heal & Feed", null, () -> {
                AttributeInstance max = t.getAttribute(Attribute.MAX_HEALTH);
                t.setHealth(max == null ? 20.0 : max.getValue());
                t.setFoodLevel(20);
                t.setSaturation(10.0F);
                t.setFireTicks(0);
                this.player(admin, t);
            }));
            this.ui()
                .screen(
                    admin,
                    LobbyDialogs.title("SMP Admin", Text.esc(t.getName())),
                    List.of(),
                    header,
                    null,
                    b,
                    2,
                    this.ui().btn(admin, "<gray>« Back", null, () -> this.overview(admin))
                );
        }
    }

    private void logouts(Player admin) {
        List<SmpAdmin.Seen> list = new ArrayList<>(this.seen.values());
        list.removeIf(sx -> Bukkit.getPlayer(sx.uuid()) != null);
        list.sort(Comparator.comparingLong(SmpAdmin.Seen::time).reversed());
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (SmpAdmin.Seen s : list.subList(0, Math.min(30, list.size()))) {
            ItemStack head = Items.of(Material.PLAYER_HEAD).skull(Bukkit.getOfflinePlayer(s.uuid())).build();
            String where = s.world() + " <dark_gray>•</dark_gray> " + Math.round(s.x()) + " " + Math.round(s.y()) + " " + Math.round(s.z());
            tiles.add(
                new LobbyDialogs.Tile(
                    head,
                    Text.mm("<white><b>" + Text.esc(s.name())),
                    Text.mm("<gray>" + where + " <dark_gray>• <gray>" + ago(now - s.time())),
                    List.of(
                        Text.mm("<white><b>" + Text.esc(s.name())),
                        Text.mm("<gray>Logged out at " + where),
                        Component.empty(),
                        Text.mm("<green>▶ Click to teleport there")
                    ),
                    () -> {
                        World w = Bukkit.getWorld(s.world());
                        admin.closeDialog();
                        if (w == null) {
                            admin.sendMessage(Text.mm("<red>That world isn't loaded."));
                        } else {
                            Tp.to(admin, new Location(w, s.x(), s.y(), s.z()));
                        }
                    }
                )
            );
        }

        this.ui()
            .screen(
                admin,
                LobbyDialogs.title("SMP Admin", "Logout spots"),
                tiles,
                List.of(),
                Text.mm("<gray>No logouts recorded yet."),
                List.of(),
                1,
                this.ui().btn(admin, "<gray>« Back", null, () -> this.overview(admin))
            );
    }

    private static String ago(long ms) {
        long m = ms / 60000L;
        if (m < 60L) {
            return m + "m ago";
        } else {
            long h = m / 60L;
            return h < 48L ? h + "h ago" : h / 24L + "d ago";
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (o.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(o.getName());
                }
            }

            if ("restore".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add("restore");
            }
        } else if (args[0].equalsIgnoreCase("restore")) {
            List<String> opts = new ArrayList<>();
            if (args.length == 2) {
                opts.add("all-from-old");
                for (Player o : Bukkit.getOnlinePlayers()) {
                    opts.add(o.getName());
                }
            } else if (args.length == 3 && args[1].equalsIgnoreCase("all-from-old")) {
                opts.add("confirm");
            } else if (args.length == 4) {
                opts.addAll(List.of("preview", "enderchest", "inventory", "all"));
            }

            String last = args[args.length - 1].toLowerCase(Locale.ROOT);
            for (String o : opts) {
                if (o.toLowerCase(Locale.ROOT).startsWith(last)) {
                    out.add(o);
                }
            }
        }

        return out;
    }

    public record Seen(UUID uuid, String name, String world, double x, double y, double z, long time) {
    }
}
