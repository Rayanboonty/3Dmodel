package net.practicepvp.pack;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.rank.Tier;
import net.practicepvp.social.Countries;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import net.practicepvp.web.WebServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public final class ResourcePacks implements Listener {
    private final PracticePlugin plugin;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private volatile byte[] zip;
    private volatile String sha1;
    private volatile UUID packId;
    private final Set<UUID> retried = ConcurrentHashMap.newKeySet();

    public ResourcePacks(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public void start(WebServer web) {
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true");
        }

        if (web != null) {
            web.prefix(
                "GET",
                "/pack/",
                r -> this.zip == null
                    ? WebServer.Response.notFound()
                    : WebServer.Response.of(200, "application/zip", this.zip)
                        .header("Content-Disposition", "attachment; filename=\"vortex-pack.zip\"")
                        .header("Cache-Control", "public, max-age=86400")
            );
            web.exact("HEAD", "/pack", r -> WebServer.Response.notFound());
        }

        LinkedHashMap<String, String[]> flags = new LinkedHashMap<>();

        for (Countries.Country c : Countries.ALL.values()) {
            flags.put(c.code(), c.colors());
        }

        List<PackBuilder.TierArt> tiers = new ArrayList<>();

        for (Tier t : Tier.values()) {
            tiers.add(new PackBuilder.TierArt(t.label, Integer.parseInt(t.color.substring(1), 16), t.number(), t.step()));
        }

        Glyphs.flagOrder(new ArrayList<>(flags.keySet()));
        String desc = "§6§l" + Cfg.s(Opt.SERVER_NAME) + "§r§7 - flags, badges, rank-ups";
        Thread t = new Thread(
            () -> {
                try {
                    long start = System.currentTimeMillis();
                    PackBuilder.Built built = PackBuilder.build(desc, flags, tiers);
                    byte[] digest = MessageDigest.getInstance("SHA-1").digest(built.zip());
                    String hex = HexFormat.of().formatHex(digest);
                    File dir = new File(this.plugin.getDataFolder(), "web");
                    if (dir.isDirectory() || dir.mkdirs()) {
                        Files.write(new File(dir, "vortex-pack.zip").toPath(), built.zip());
                    }

                    this.zip = built.zip();
                    this.sha1 = hex;
                    this.packId = UUID.nameUUIDFromBytes(digest);
                    this.plugin
                        .getLogger()
                        .info(
                            "Resource pack ready: "
                                + built.files()
                                + " files, "
                                + built.zip().length / 1024
                                + " KB, built in "
                                + (System.currentTimeMillis() - start)
                                + " ms."
                        );
                    Tasks.ensureSync(() -> {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            this.send(p);
                        }
                    });
                } catch (NoSuchAlgorithmException | RuntimeException | Error | IOException var10x) {
                    this.plugin
                        .getLogger()
                        .log(Level.WARNING, "Could not build the resource pack; flags and the rank-up screen fall back to plain text.", (Throwable)var10x);
                }
            },
            "VortexPackBuilder"
        );
        t.setDaemon(true);
        t.start();
    }

    public boolean ready() {
        return this.zip != null;
    }

    public boolean has(Player p) {
        return p != null && this.loaded.contains(p.getUniqueId());
    }

    public String url(Player p) {
        String ext = Cfg.s(Opt.PACK_URL).trim();
        return !ext.isEmpty() ? ext : this.plugin.webBase(p) + "/pack/" + this.sha1 + ".zip";
    }

    public void send(Player p) {
        if (Cfg.bool(Opt.PACK_ENABLED) && this.zip != null && p != null && p.isOnline()) {
            if (!Cfg.s(Opt.PACK_URL).isBlank() || this.plugin.web() != null && this.plugin.web().available()) {
                this.send(p, this.url(p));
            }
        }
    }

    private void send(Player p, String address) {
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(this.packId, URI.create(address), this.sha1);
            ResourcePackRequest req = ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(Cfg.bool(Opt.PACK_REQUIRED))
                .replace(false)
                .prompt(Text.mm("<gold><b>" + Text.esc(Cfg.s(Opt.SERVER_NAME)) + "</b> <gray>pack: country flags, verified badges and the rank-up animation."))
                .build();
            p.sendResourcePacks(req);
        } catch (RuntimeException var5) {
            this.plugin.getLogger().log(Level.WARNING, "Could not send the resource pack to " + p.getName(), (Throwable)var5);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        this.loaded.remove(p.getUniqueId());
        Tasks.later(30L, () -> this.send(p));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.loaded.remove(e.getPlayer().getUniqueId());
        this.retried.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (this.packId != null && this.packId.equals(e.getID())) {
            Player p = e.getPlayer();
            switch (e.getStatus()) {
                case SUCCESSFULLY_LOADED:
                    this.loaded.add(p.getUniqueId());
                    if (this.plugin.tab() != null) {
                        this.plugin.tab().packLoaded(p);
                    }
                    Tasks.later(2L, () -> {
                        if (p.isOnline()) {
                            this.plugin.boards().refreshViewer(p);
                        }
                    });
                    break;
                case FAILED_DOWNLOAD:
                case INVALID_URL:
                    this.loaded.remove(p.getUniqueId());
                    if (Cfg.s(Opt.PACK_URL).isBlank()
                        && this.plugin.web() != null
                        && this.plugin.web().onSamePort()
                        && this.plugin.web().running()
                        && this.retried.add(p.getUniqueId())) {
                        String host = Cfg.s(Opt.WEB_HOST).trim();
                        if (host.isEmpty()) {
                            host = joinHost(p);
                        }

                        if (!host.contains("://")) {
                            String alt = "http://" + host + ":" + this.plugin.web().port() + "/pack/" + this.sha1 + ".zip";
                            Tasks.later(10L, () -> {
                                if (p.isOnline()) {
                                    this.send(p, alt);
                                }
                            });
                        }
                    }
                    break;
                case DECLINED:
                case FAILED_RELOAD:
                case DISCARDED:
                    this.loaded.remove(p.getUniqueId());
            }
        }
    }

    public static String joinHost(Player p) {
        InetSocketAddress vh = p == null ? null : p.getVirtualHost();
        if (vh != null && vh.getHostString() != null && !vh.getHostString().isBlank()) {
            return vh.getHostString();
        } else {
            String ip = Bukkit.getIp();
            return ip != null && !ip.isBlank() ? ip : "localhost";
        }
    }
}
