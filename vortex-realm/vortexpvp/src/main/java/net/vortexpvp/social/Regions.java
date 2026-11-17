package net.vortexpvp.social;

import io.papermc.paper.registry.data.dialog.ActionButton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.data.Setting;
import net.vortexpvp.dialog.LobbyDialogs;
import net.vortexpvp.util.Fmt;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Server regions (MCPVP "Regions" / "Lowest Ping Host"). Each region is a name, a city label and optionally another
 * server address; picking a remote region transfers the player there with the vanilla transfer packet.
 */
public final class Regions implements TabExecutor {
    private final VortexPvP plugin;
    private final File file;
    private final Map<String, Regions.Region> regions = new LinkedHashMap<>();
    private String current = "main";

    public Regions(VortexPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "regions.yml");
    }

    public void load() {
        if (!this.file.exists()) {
            YamlConfiguration y = new YamlConfiguration();
            y.options()
                .setHeader(
                    List.of(
                        "Regions - shown in /settings -> Regions, /region and the tab footer (\"Lowest Ping Host\").",
                        "current: the id of THIS server's region.",
                        "host/port: leave host empty for this server. Other servers need 'accepts-transfers=true' in server.properties.",
                        "flag: a country code (us, de, sg...) used for the flag next to the region."
                    )
                );
            y.set("current", "na");
            y.set("regions.na.name", "North America");
            y.set("regions.na.location", "Miami, Florida");
            y.set("regions.na.host", "");
            y.set("regions.na.port", 25565);
            y.set("regions.na.flag", "us");
            y.set("regions.eu.name", "Europe");
            y.set("regions.eu.location", "Frankfurt, Germany");
            y.set("regions.eu.host", "");
            y.set("regions.eu.port", 25565);
            y.set("regions.eu.flag", "de");
            y.set("regions.eu.enabled", false);
            y.set("regions.as.name", "Asia");
            y.set("regions.as.location", "Singapore");
            y.set("regions.as.host", "");
            y.set("regions.as.port", 25565);
            y.set("regions.as.flag", "sg");
            y.set("regions.as.enabled", false);

            try {
                y.save(this.file);
            } catch (IOException var3) {
                this.plugin.getLogger().log(Level.WARNING, "Could not write regions.yml", (Throwable)var3);
            }
        }

        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        this.regions.clear();
        ConfigurationSection sec = y.getConfigurationSection("regions");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection r = sec.getConfigurationSection(id);
                if (r != null && r.getBoolean("enabled", true)) {
                    this.regions.put(
                        id.toLowerCase(Locale.ROOT),
                        new Regions.Region(
                            id.toLowerCase(Locale.ROOT),
                            r.getString("name", id),
                            r.getString("location", ""),
                            r.getString("host", ""),
                            r.getInt("port", 25565),
                            r.getString("flag", "")
                        )
                    );
                }
            }
        }

        this.current = y.getString("current", "na").toLowerCase(Locale.ROOT);
        if (!this.regions.containsKey(this.current) && !this.regions.isEmpty()) {
            this.current = this.regions.keySet().iterator().next();
        }
    }

    public List<Regions.Region> all() {
        return new ArrayList<>(this.regions.values());
    }

    public Regions.Region current() {
        return this.regions.get(this.current);
    }

    public Regions.Region get(String id) {
        return id == null ? null : this.regions.get(id.toLowerCase(Locale.ROOT));
    }

    /** "Miami, Florida" - the label used in the tab footer. */
    public String hostLabel() {
        Regions.Region r = this.current();
        return r == null ? "" : (r.location.isBlank() ? r.name : r.location);
    }

    public boolean isLocal(Regions.Region r) {
        return r.host.isBlank() || r.id.equals(this.current);
    }

    public void open(Player p, Runnable back) {
        LobbyDialogs ld = this.plugin.dialogs().lobby();
        PvPProfile prof = this.plugin.profiles().get(p);
        String preferred = prof.get(Setting.REGION);
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        for (Regions.Region r : this.regions.values()) {
            boolean here = r.id.equals(this.current);
            boolean fav = r.id.equals(preferred);
            List<Component> hover = new ArrayList<>();
            hover.add(Text.mm("<aqua><b>" + Text.esc(r.name)));
            if (!r.location.isBlank()) {
                hover.add(Text.mm("<gray>Host: <white>" + Text.esc(r.location)));
            }

            hover.add(Text.mm(here ? "<green>● You are connected here <gray>(" + Fmt.pingColored(p.getPing()) + "<gray>)" : "<gray>Another server"));
            hover.add(Component.empty());
            hover.add(Text.mm(here ? "<yellow>▶ Click to make it your preferred region" : "<green>▶ Click to move to this region"));
            Component flag = this.flag(r, p);
            tiles.add(
                new LobbyDialogs.Tile(
                    Items.of(here ? Material.RECOVERY_COMPASS : Material.COMPASS).glint(fav).build(),
                    flag.append(Text.mm((here ? "<green>" : "<white>") + "<b>" + Text.esc(r.name) + "</b>" + (fav ? " <gold>★" : ""))),
                    Text.mm(
                        "<gray>"
                            + Text.esc(r.location.isBlank() ? r.name : r.location)
                            + (here ? " <dark_gray>· <green>Connected " + Fmt.pingColored(p.getPing()) : "")
                    ),
                    hover,
                    () -> this.pick(p, r, back)
                )
            );
        }

        List<ActionButton> buttons = new ArrayList<>();
        ld.screen(
            p,
            LobbyDialogs.title("Regions", "Lowest Ping Host: " + Text.esc(this.hostLabel())),
            tiles,
            List.of(Text.mm("<gray>Play on the server closest to you for the lowest ping.")),
            Text.mm("<gray>No regions are set up. <dark_gray>(regions.yml)"),
            buttons,
            1,
            ld.btn(p, "<gray>« Back", null, back)
        );
    }

    private Component flag(Regions.Region r, Player viewer) {
        if (r.flag.isBlank() || this.plugin.countries() == null) {
            return Component.empty();
        } else {
            Countries.Country c = Countries.ALL.get(r.flag.toUpperCase(Locale.ROOT));
            return c == null ? Component.empty() : this.plugin.countries().flag(c, viewer).append(Component.space());
        }
    }

    private void pick(Player p, Regions.Region r, Runnable back) {
        if (this.isLocal(r)) {
            this.plugin.profiles().get(p).set(Setting.REGION, r.id);
            p.sendMessage(Text.mm("<aqua>◆ <gray>Preferred region set to <white>" + Text.esc(r.name) + "<gray>."));
            Snd.click(p);
            this.open(p, back);
        } else {
            this.plugin
                .dialogs()
                .confirm(
                    p,
                    Text.mm("<aqua><b>Move to " + Text.esc(r.name) + "?"),
                    List.of(Text.mm("<gray>You'll be sent to <white>" + Text.esc(r.location.isBlank() ? r.name : r.location) + "<gray>. Your ranks, kits and friends come with you.")),
                    () -> {
                        this.plugin.profiles().get(p).set(Setting.REGION, r.id);
                        this.transfer(p, r);
                    }
                );
        }
    }

    public void transfer(Player p, Regions.Region r) {
        net.vortexpvp.session.PlayerState st = this.plugin.sessions().state(p);
        if (st == net.vortexpvp.session.PlayerState.MATCH || st == net.vortexpvp.session.PlayerState.PREMATCH || st == net.vortexpvp.session.PlayerState.FFA) {
            p.sendMessage(Text.mm("<red>Finish your fight before changing regions."));
        } else if (this.isLocal(r)) {
            p.sendMessage(Text.mm("<gray>You're already on <white>" + Text.esc(r.name) + "<gray>."));
        } else {
            p.sendMessage(Text.mm("<aqua>◆ <gray>Moving you to <white>" + Text.esc(r.name) + "<gray>..."));
            this.plugin.leavePvP(p, false);
            this.plugin.profiles().save(this.plugin.profiles().get(p));

            try {
                p.transfer(r.host, r.port);
            } catch (RuntimeException var4) {
                p.sendMessage(Text.mm("<red>That region can't be reached right now."));
                this.plugin.getLogger().log(Level.WARNING, "Transfer to " + r.host + ":" + r.port + " failed", (Throwable)var4);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Text.mm("<gray>Regions: <white>" + String.join(", ", this.regions.keySet()) + " <gray>(current: " + this.current + ")"));
            return true;
        } else {
            if (args.length == 0) {
                this.open(p, p::closeDialog);
            } else {
                Regions.Region r = this.get(args[0]);
                if (r == null) {
                    p.sendMessage(Text.mm("<red>Unknown region. <gray>Regions: <white>" + String.join(", ", this.regions.keySet())));
                } else {
                    this.pick(p, r, p::closeDialog);
                }
            }

            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String id : this.regions.keySet()) {
                if (id.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(id);
                }
            }
        }

        return out;
    }

    public record Region(String id, String name, String location, String host, int port, String flag) {
    }
}
