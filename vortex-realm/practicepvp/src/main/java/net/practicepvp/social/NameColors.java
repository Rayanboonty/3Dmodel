package net.practicepvp.social;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.cosmetics.Cosmetics;
import net.practicepvp.dialog.LobbyDialogs;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 3.11.0 - everyone picks the colour of their name in chat: /namecolor opens a palette, /namecolor &lt;#hex&gt; sets
 * any colour, /namecolor reset goes back to the rank's colour. Admins set anyone's: /namecolor &lt;player&gt; &lt;#hex|reset&gt;.
 * Kept with the player's cosmetics.
 */
public final class NameColors implements CommandExecutor, TabCompleter {
    /** name, hex, wool icon */
    static final String[][] PALETTE = {
        {"White", "#FFFFFF", "WHITE_WOOL"}, {"Silver", "#C0C0C0", "LIGHT_GRAY_WOOL"}, {"Gold", "#FFC83D", "YELLOW_WOOL"},
        {"Orange", "#FF8C1A", "ORANGE_WOOL"}, {"Red", "#FF4B4B", "RED_WOOL"}, {"Rose", "#FF6FAE", "PINK_WOOL"},
        {"Magenta", "#E05CFF", "MAGENTA_WOOL"}, {"Purple", "#9B5CFF", "PURPLE_WOOL"}, {"Blue", "#4B7BFF", "BLUE_WOOL"},
        {"Sky", "#5CC8FF", "LIGHT_BLUE_WOOL"}, {"Aqua", "#3DE0D0", "CYAN_WOOL"}, {"Mint", "#5CFFA8", "LIME_WOOL"},
        {"Green", "#3DBE4B", "GREEN_WOOL"}, {"Lime", "#B6FF3D", "LIME_CONCRETE"}, {"Sand", "#E8D49B", "SAND"},
        {"Brown", "#B07A4A", "BROWN_WOOL"}, {"Grey", "#8A8F98", "GRAY_WOOL"}, {"Crimson", "#C8102E", "RED_CONCRETE"}
    };
    private final PracticePlugin plugin;

    public NameColors(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    /** The colour a player chose, or null (then the rank's colour is used). */
    public static String chosen(Player p) {
        PracticePlugin pl = PracticePlugin.get();
        if (pl == null || pl.cosmetics() == null || !Cfg.bool(Opt.NAME_COLORS)) {
            return null;
        }

        String c = pl.cosmetics().of(p.getUniqueId()).extra.get("color");
        return c != null && c.matches("#[0-9A-Fa-f]{6}") ? c : null;
    }

    /** The colour to show a player's name in: their own choice, else their rank's. */
    public static String colorOf(Player p, String rankColor) {
        String c = chosen(p);
        return c != null ? c : rankColor;
    }

    private void set(Player target, String hex) {
        Cosmetics.Data d = this.plugin.cosmetics().of(target.getUniqueId());
        if (hex == null) {
            d.extra.remove("color");
        } else {
            d.extra.put("color", hex.toUpperCase(Locale.ROOT));
        }

        this.plugin.cosmetics().saveNow(target.getUniqueId());
    }

    private static String parse(String s) {
        if (s == null) {
            return null;
        }

        String v = s.trim();
        for (String[] c : PALETTE) {
            if (c[0].equalsIgnoreCase(v)) {
                return c[1];
            }
        }

        if (!v.startsWith("#")) {
            v = "#" + v;
        }

        return v.matches("#[0-9A-Fa-f]{6}") ? v : null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (this.plugin.cosmetics() == null) {
            sender.sendMessage(Text.mm("<red>Cosmetics are not running."));
            return true;
        }

        if (!Cfg.bool(Opt.NAME_COLORS)) {
            sender.sendMessage(Text.mm("<red>Name colours are turned off on this server."));
            return true;
        }

        boolean admin = sender.hasPermission("practice.admin");
        if (args.length >= 2 && admin) {
            Player t = Bukkit.getPlayerExact(args[0]);
            if (t == null) {
                sender.sendMessage(Text.mm("<red>" + Text.esc(args[0]) + " is not online."));
                return true;
            }

            String hex = args[1].equalsIgnoreCase("reset") ? null : parse(args[1]);
            if (hex == null && !args[1].equalsIgnoreCase("reset")) {
                sender.sendMessage(Text.mm("<red>Give a colour like #FF8C1A or a name: " + names()));
                return true;
            }

            this.set(t, hex);
            sender.sendMessage(Text.mm("<green>" + Text.esc(t.getName()) + "'s name is now " + (hex == null ? "back to their rank colour." : "<" + hex + ">" + Text.esc(t.getName()) + "</" + hex + ">.")));
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Usage: /namecolor <player> <#hex|reset>");
            return true;
        }

        if (args.length == 1) {
            String hex = args[0].equalsIgnoreCase("reset") ? null : parse(args[0]);
            if (hex == null && !args[0].equalsIgnoreCase("reset")) {
                p.sendMessage(Text.mm("<red>Give a colour like #FF8C1A or a name: " + names()));
                return true;
            }

            this.set(p, hex);
            p.sendMessage(Text.mm(hex == null ? "<gray>Your name is back to your rank's colour." : "<gray>Your name is now <" + hex + ">" + Text.esc(p.getName()) + "</" + hex + "><gray>."));
            Snd.play(p, "entity.experience_orb.pickup", 0.6F, 1.4F);
            return true;
        }

        this.open(p);
        return true;
    }

    private static String names() {
        List<String> n = new ArrayList<>();
        for (String[] c : PALETTE) {
            n.add(c[0].toLowerCase(Locale.ROOT));
        }

        return String.join(", ", n);
    }

    /** The palette. */
    public void open(Player p) {
        String cur = chosen(p);
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        for (String[] c : PALETTE) {
            Material m = Material.matchMaterial(c[2]);
            boolean on = c[1].equalsIgnoreCase(cur);
            tiles.add(new LobbyDialogs.Tile(new ItemStack(m == null ? Material.WHITE_WOOL : m), Text.mm("<" + c[1] + "><b>" + c[0]),
                Text.mm(on ? "<green>✔ Chosen" : "<" + c[1] + ">" + Text.esc(p.getName())), List.of(Text.mm("<" + c[1] + ">" + Text.esc(p.getName()))), () -> {
                    this.set(p, c[1]);
                    Snd.play(p, "entity.experience_orb.pickup", 0.6F, 1.4F);
                    this.open(p);
                }));
        }

        tiles.add(new LobbyDialogs.Tile(new ItemStack(Material.BARRIER), Text.mm("<gray><b>Rank colour"), Text.mm(cur == null ? "<green>✔ Chosen" : "<gray>Use your rank's colour"),
            List.of(), () -> {
                this.set(p, null);
                this.open(p);
            }));
        this.plugin.dialogs().lobby().screen(p, LobbyDialogs.title("Name Colour", "How your name looks in chat"), tiles,
            List.of(Text.mm("<gray>Pick a colour, or type <white>/namecolor #hex</white> for any colour.")), null, List.of(), 1,
            this.plugin.dialogs().lobby().close(p));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String[] c : PALETTE) {
                out.add(c[0].toLowerCase(Locale.ROOT));
            }

            out.add("reset");
            if (sender.hasPermission("practice.admin")) {
                for (Player o : Bukkit.getOnlinePlayers()) {
                    out.add(o.getName());
                }
            }
        } else if (args.length == 2 && sender.hasPermission("practice.admin")) {
            for (String[] c : PALETTE) {
                out.add(c[0].toLowerCase(Locale.ROOT));
            }

            out.add("reset");
        }

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
