package net.vortexpvp.data;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * The first start after an update: the vault has already saved everything (plugin data, database and every player's
 * inventory) before anything loaded; config, kits, messages and the database were brought forward while loading. This
 * puts it all in one report for the console, for admins when they join, and in VortexPvP-Test-Vault/last-update.txt.
 */
public final class Upgrader implements Listener {
    private final VortexPvP plugin;
    private final List<String> report = new ArrayList<>();
    private final Set<UUID> told = ConcurrentHashMap.newKeySet();

    public Upgrader(VortexPvP plugin) {
        this.plugin = plugin;
    }

    public List<String> report() {
        return List.copyOf(this.report);
    }

    public boolean updated() {
        return !this.report.isEmpty();
    }

    /** Called at the end of start-up. */
    public void finish() {
        DataVault v = this.plugin.vault();
        if (v != null && v.updatedFrom != null) {
            String to = this.plugin.getPluginMeta().getVersion();
            this.report.add("<gold><b>Updated " + Text.esc(v.updatedFrom) + " → " + Text.esc(to) + "</b> <gray>- everything was carried over:");
            this.report.add("<green>✔ <gray>Backup before the update: <white>" + Text.esc(v.updateSnapshot == null ? "(failed - see console)" : v.updateSnapshot)
                + (v.updateSnapshot == null ? "" : " <gray>(" + v.playerFiles(v.updateSnapshot) + " player inventory files)"));
            this.report.add("<green>✔ <gray>Settings kept; <white>" + Cfg.added + "</white> new options added with their defaults"
                + (Cfg.kept > 0 ? "; " + Cfg.kept + " unreadable old values saved under old-settings" : "") + ".");
            List<String> kits = this.plugin.kits() == null ? List.of() : this.plugin.kits().mergeReport;
            this.report.add("<green>✔ <gray>Kits kept" + (kits.isEmpty() ? " (no kit changes in this update)." : ": " + Text.esc(String.join(", ", kits))));
            this.report.add("<green>✔ <gray>Players: stats, ranks, kit layouts, friends, inventories and economy kept (database and files are updated in place).");
            this.report.add("<green>✔ <gray>NPCs, holograms, worlds, hub, regions and messages kept; new message lines added.");
            this.report.add("<green>✔ <gray>Arenas made with older settings are rebuilt in the background over the next minutes (hand-edited ones are left alone).");
            if (!this.plugin.failedParts().isEmpty()) {
                this.report.add("<red>✘ <gray>These parts failed to start: <white>" + Text.esc(String.join(", ", this.plugin.failedParts().keySet()))
                    + "<gray>. Nothing was lost: /pvpadmin vault restore " + Text.esc(v.updateSnapshot == null ? "latest" : v.updateSnapshot) + " brings the old data back.");
            }

            for (String line : this.report) {
                this.plugin.getLogger().info(Text.plain(Text.mm(line)));
            }

            try {
                List<String> plain = new ArrayList<>();

                for (String line : this.report) {
                    plain.add(Text.plain(Text.mm(line)));
                }

                Files.write(new File(v.dir(), "last-update.txt").toPath(), plain, StandardCharsets.UTF_8);
            } catch (IOException var6) {
                this.plugin.getLogger().log(Level.FINE, "last-update.txt", var6);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (this.updated() && p.hasPermission("vortexpvp.admin") && this.told.add(p.getUniqueId())) {
            Tasks.later(60L, () -> {
                if (p.isOnline()) {
                    this.tell(p);
                }
            });
        }
    }

    public void tell(Player p) {
        for (String line : this.report) {
            p.sendMessage(Text.mm(line));
        }
    }
}
