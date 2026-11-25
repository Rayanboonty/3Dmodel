package net.practicepvp;

import java.io.InputStream;
import java.util.Properties;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;

/**
 * Which server this build is for. The normal build (PracticePvP / VortexRealm) has everything. A practice-only build
 * (C MCPvP) is the same code with {@code edition.properties} saying {@code practice-only=true}: no SMP, no hub, no
 * server selector, no seasons; only the practice lobby, duels, FFA, parties, bots and events.
 */
public final class Edition {
    /** True in the practice-only build. */
    public static final boolean PRACTICE_ONLY;
    /** The network's name in this build. */
    public static final String NAME;

    /** Parts of the plugin that never start in a practice-only build. */
    private static final Set<String> SMP_PARTS = Set.of(
        "smp", "SMP seasons", "everyone equal in the SMP", "SMP kits", "SMP claims", "Unstable Realm season",
        "SMP elytra wall rescue", "SMP portals", "VexBot bridge", "regions");

    static {
        Properties p = new Properties();
        try (InputStream in = Edition.class.getResourceAsStream("/edition.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
            // no file: the normal build
        }

        PRACTICE_ONLY = Boolean.parseBoolean(p.getProperty("practice-only", "false"));
        NAME = p.getProperty("name", "Vortex Realm");
    }

    private Edition() {
    }

    /** Commands a practice-only server never offers (SMP, hub travel, server selector, regions). */
    private static final Set<String> GONE = Set.of("worlds", "smp", "region", "survival", "leavepvp");

    /** Should this part of the plugin start in this build? */
    public static boolean starts(String part) {
        return !PRACTICE_ONLY || !SMP_PARTS.contains(part);
    }

    /**
     * Practice-only build: the commands of the parts that didn't start (homes, tpa, claims, seasons...) are taken out
     * of the server's command list, so players never see or tab-complete them.
     */
    public static void hideUnusedCommands(PracticePlugin plugin) {
        if (!PRACTICE_ONLY) {
            return;
        }

        int hidden = 0;
        java.util.Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        for (String name : plugin.getDescription().getCommands().keySet()) {
            PluginCommand c = plugin.getCommand(name);
            if (c == null || c.getExecutor() != plugin && !GONE.contains(name)) {
                continue;
            }

            // nothing took this command: it belongs to a part this build doesn't have
            known.values().removeIf(cmd -> cmd == c);
            hidden++;
        }

        if (hidden > 0) {
            plugin.getLogger().info(NAME + " is practice only: " + hidden + " SMP / hub commands are switched off.");
            Bukkit.getOnlinePlayers().forEach(org.bukkit.entity.Player::updateCommands);
        }
    }
}
