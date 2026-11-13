package net.practicepvp.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.practicepvp.arena.VanillaTerrain;
import net.practicepvp.PracticePlugin;
import net.practicepvp.arena.Arena;
import net.practicepvp.arena.ArenaInstance;
import net.practicepvp.board.BoardDesigns;
import net.practicepvp.build.SmallBuilds;
import net.practicepvp.build.SpawnBuilder;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Msg;
import net.practicepvp.config.Opt;
import net.practicepvp.data.ConfigBackup;
import net.practicepvp.data.DataVault;
import net.practicepvp.data.RankBackup;
import net.practicepvp.fx.Fx;
import net.practicepvp.gui.admin.AdminMenu;
import net.practicepvp.gui.admin.ArenaListMenu;
import net.practicepvp.extra.ChatFilter;
import net.practicepvp.gui.admin.ConfigMenu;
import net.practicepvp.gui.admin.FfaListMenu;
import net.practicepvp.gui.admin.KitListMenu;
import net.practicepvp.gui.admin.MatchesMenu;
import net.practicepvp.gui.admin.PlayerAdminMenu;
import net.practicepvp.gui.admin.StaffMenu;
import net.practicepvp.kit.Kit;
import net.practicepvp.session.PlayerState;
import net.practicepvp.session.Session;
import net.practicepvp.session.Tp;
import net.practicepvp.smp.WorldMode;
import net.practicepvp.social.RankAccess;
import net.practicepvp.social.Ranks;
import net.practicepvp.util.Locs;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class AdminDialogs {
    private final PracticePlugin plugin;
    private final LobbyDialogs ui;
    private static final int PERM_PAGE = 18;

    AdminDialogs(PracticePlugin plugin, LobbyDialogs ui) {
        this.plugin = plugin;
        this.ui = ui;
    }

    private static LobbyDialogs.Tile tile(Material icon, String name, String line, Runnable click, String... hover) {
        List<Component> h = new ArrayList<>();
        h.add(Text.mm(name));

        for (String s : hover) {
            h.add(Text.mm("<gray>" + s));
        }

        h.add(Component.empty());
        h.add(Text.mm("<green>▶ Click to open"));
        return new LobbyDialogs.Tile(new ItemStack(icon), Text.mm(name), Text.mm(line), h, click);
    }

    private static String onOff(boolean on) {
        return on ? "<green>ON" : "<red>OFF";
    }

    private void boardDesigns(Player p) {
        String now = this.plugin.smpBoard() == null ? "" : this.plugin.smpBoard().design();
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        Material[] icons = new Material[]{
            Material.PAPER,
            Material.WHITE_BANNER,
            Material.ITEM_FRAME,
            Material.GOLD_INGOT,
            Material.GLOW_INK_SAC,
            Material.FEATHER,
            Material.QUARTZ,
            Material.SUNFLOWER
        };
        int i = 0;

        for (BoardDesigns.Design d : BoardDesigns.DESIGNS) {
            boolean on = d.id().equals(now);
            List<Component> hover = new ArrayList<>();
            hover.add(Text.mm("<gold><b>" + d.name()));
            hover.add(Text.mm("<gray>" + d.description()));
            hover.add(Component.empty());
            hover.add(Text.mm("<gray>Eco sidebar preview:"));
            BoardDesigns.Board eco = BoardDesigns.build(d.id()).get("ECO");
            hover.add(Text.mm("<white><b>" + Text.plain(Text.mm(eco.title().replaceAll("<mood:(.*?)>", "$1")))));

            for (String line : eco.lines()) {
                String shown = line.replace("||", "  ").replaceAll("<mood:(.*?)>", "<gold>$1</gold>");

                try {
                    hover.add(
                        Text.mm(
                            shown.replace("<balance>", "12,500")
                                .replace("<kills>", "42")
                                .replace("<deaths>", "17")
                                .replace("<kdr>", "2.47")
                                .replace("<here>", "18")
                                .replace("<ip>", "play.vortexrealm.net")
                        )
                    );
                } catch (RuntimeException var15) {
                    hover.add(Component.text(line));
                }
            }

            hover.add(Component.empty());
            hover.add(Text.mm(on ? "<green>✔ In use" : "<green>▶ Click to use it everywhere"));
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(icons[i++ % icons.length]),
                    Text.mm((on ? "<green>✔ " : "") + "<gold><b>" + d.name()),
                    Text.mm("<gray>" + d.description()),
                    hover,
                    () -> {
                        if (this.plugin.smpBoard() != null) {
                            this.plugin.smpBoard().applyDesign(d.id());
                            p.sendMessage(
                                Text.mm("<green>Scoreboards now use the <white>" + d.name() + "</white> design. <gray>(old file: scoreboards-backup.yml)")
                            );
                        }

                        this.boardDesigns(p);
                    }
                )
            );
        }

        this.ui
            .screen(
                p,
                LobbyDialogs.title("Scoreboard Designs", "Hover for a preview, click to use"),
                tiles,
                List.of(),
                null,
                List.of(this.ui.btn(p, "<gray>« Admin Panel", null, () -> this.main(p))),
                1,
                this.ui.close(p)
            );
    }

    private void tabDesigns(Player p) {
        String now = this.plugin.tab() == null ? "" : this.plugin.tab().design();
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (net.practicepvp.social.TabDesigns.Design d : net.practicepvp.social.TabDesigns.DESIGNS) {
            boolean on = d.id().equals(now);
            List<Component> hover = new ArrayList<>();
            hover.add(Text.mm("<gold><b>" + d.name()));
            hover.add(Text.mm("<gray>" + d.desc()));
            hover.add(Component.empty());

            for (String line : d.preview()) {
                hover.add(Text.mm("<dark_gray>▪ <white>" + Text.esc(line)));
            }

            hover.add(Component.empty());
            hover.add(Text.mm("<gray>Layout: <white>" + (!d.columns() ? "normal player list" : (d.sections().contains(",") ? "columns" : "one column"))));
            hover.add(Component.empty());
            hover.add(Text.mm(on ? "<green>✔ In use" : "<green>▶ Click to use it for everyone"));
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(d.icon()),
                    Text.mm((on ? "<green>✔ " : "") + "<gold><b>" + d.name()),
                    Text.mm("<gray>" + d.desc()),
                    hover,
                    () -> {
                        if (this.plugin.tab() != null) {
                            this.plugin.tab().applyDesign(d.id());
                            p.sendMessage(Text.mm("<green>The tab list now uses the <white>" + d.name() + "</white> design. <gray>(old file: tab-backup.yml)"));
                        }

                        this.tabDesigns(p);
                    }
                )
            );
        }

        this.ui
            .screen(
                p,
                LobbyDialogs.title("Tab Designs", "Hover for a preview, click to use"),
                tiles,
                List.of(),
                null,
                List.of(this.ui.btn(p, "<gray>« Admin Panel", null, () -> this.main(p))),
                1,
                this.ui.close(p)
            );
    }

    public void main(Player p) {
        int instances = 0;
        int free = 0;

        for (Arena a : this.plugin.arenas().all()) {
            for (ArenaInstance i : a.instances) {
                instances++;
                if (i.state == ArenaInstance.State.FREE) {
                    free++;
                }
            }
        }

        List<Component> header = List.of(
            Text.mm(
                "<gray>Online <white>"
                    + this.plugin.getServer().getOnlinePlayers().size()
                    + "</white>  <dark_gray>│</dark_gray>  Matches <white>"
                    + this.plugin.matches().active().size()
                    + "</white>  <dark_gray>│</dark_gray>  Queued <white>"
                    + this.plugin.queues().total()
                    + "</white>  <dark_gray>│</dark_gray>  FFA <white>"
                    + this.plugin.ffa().playing()
            ),
            Text.mm(
                "<gray>Arenas <white>"
                    + free
                    + "/"
                    + instances
                    + "</white> free  <dark_gray>│</dark_gray>  Jobs <white>"
                    + this.plugin.jobs().pending()
                    + "</white>  <dark_gray>│</dark_gray>  Lobby "
                    + (this.plugin.lobby().lobbySet() ? "<green>set" : "<red>not set")
            )
        );
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        tiles.add(
            tile(
                Material.COMPARATOR,
                "<gold><b>Settings",
                "<gray>Every option of the plugin",
                () -> this.settings(p, null),
                "Matches, ranks, queues, FFA, visuals, SMP..."
            )
        );
        tiles.add(
            tile(
                Material.GRASS_BLOCK,
                "<gold><b>Duel Arenas",
                "<gray>" + this.plugin.arenas().all().size() + " maps",
                () -> chest(p, () -> new ArenaListMenu(this.plugin, p).open()),
                "Create, edit, copy and reset maps."
            )
        );
        tiles.add(
            tile(
                Material.NETHERITE_AXE,
                "<gold><b>FFA Arenas",
                "<gray>" + this.plugin.ffa().all().size() + " arenas",
                () -> chest(p, () -> new FfaListMenu(this.plugin, p).open()),
                "Spawns, kits and regeneration."
            )
        );
        tiles.add(
            tile(
                Material.DIAMOND_SWORD,
                "<gold><b>Kits",
                "<gray>" + this.plugin.kits().all().size() + " kits",
                () -> chest(p, () -> new KitListMenu(this.plugin, p).open()),
                "Contents, icons, rules, queues."
            )
        );
        tiles.add(
            tile(
                Material.FILLED_MAP,
                "<gold><b>Kit Arenas",
                "<gray>Pick the maps of every kit",
                () -> this.kitArenas(p),
                "Which arenas each gamemode plays on (automatic by default)."
            )
        );
        tiles.add(
            tile(
                Material.ENDER_EYE,
                "<gold><b>Live Matches",
                "<gray>" + this.plugin.matches().active().size() + " running",
                () -> chest(p, () -> new MatchesMenu(this.plugin, p).open()),
                "Spectate or end matches."
            )
        );
        tiles.add(
            tile(
                Material.PLAYER_HEAD,
                "<gold><b>Players",
                "<gray>Ranks, reset",
                () -> PlayerAdminMenu.prompt(this.plugin, p),
                "Look up any player by name,",
                "online or offline.",
                "Set rank points or tier per kit."
            )
        );
        tiles.add(
            tile(
                Material.ENCHANTED_BOOK,
                "<gold><b>Rank Backups",
                "<gray>" + (this.plugin.rankBackup() == null ? 0 : this.plugin.rankBackup().files().size()) + " kept",
                () -> this.backups(p),
                "Back up or restore every player's",
                "ranks (for moving to a new version)."
            )
        );
        tiles.add(
            tile(
                Material.SHULKER_BOX,
                "<gold><b>All-in-One Backup",
                "<gray>" + (this.plugin.configBackup() == null ? 0 : this.plugin.configBackup().files(ConfigBackup.Kind.FULL).size()) + " kept",
                () -> this.fullBackups(p),
                "Ranks, kits and settings",
                "in one file. Restores live."
            )
        );
        tiles.add(
            tile(
                Material.NAME_TAG,
                "<gold><b>Ranks",
                "<gray>Owner, Admin, MVP, VIP...",
                () -> this.ranks(p),
                "Icons, gradients, tab order.",
                "/rank set <player> <rank>"
            )
        );
        tiles.add(
            tile(
                Material.ENDER_CHEST,
                "<gold><b>Data Vault",
                "<gray>" + this.plugin.vault().files().size() + " snapshots",
                () -> this.vault(p),
                "Full snapshots of all settings and data,",
                "kept outside the plugin folder: updates",
                "never lose anything."
            )
        );
        tiles.add(tile(Material.GOLDEN_HELMET, "<gold><b>PvP Staff", "<gray>" + this.plugin.staff().all().size() + " staff", () -> {
            if (!p.isOp()) {
                p.sendMessage(Text.mm("<red>Only operators can choose PvP staff."));
            } else {
                chest(p, () -> new StaffMenu(this.plugin, p).open());
            }
        }, "Helper, Mod and Admin roles."));
        tiles.add(
            tile(
                Material.FILLED_MAP,
                "<gold><b>World Manager",
                "<gray>Modes, spawns, new worlds",
                () -> this.worlds(p),
                "Hub and PvP worlds. The SMP runs",
                "on its own server (VortexSMP)."
            )
        );
        tiles.add(tile(Material.ARMOR_STAND, "<gold><b>NPCs", "<gray>Skins, actions, auras", () -> this.plugin.npcs().list(p), "Travel NPCs and menu NPCs."));
        if (this.plugin.chatFilter() != null) {
            tiles.add(
                tile(
                    Material.WRITABLE_BOOK,
                    "<gold><b>Chat Moderation",
                    "<gray>Swear filter <white>" + (Cfg.bool(Opt.CHAT_FILTER) ? "ON" : "OFF") + "<gray> - <white>" + this.plugin.chatFilter().activeMutes().size() + "<gray> muted",
                    () -> this.chatModeration(p),
                    "Blocked words, auto-mutes, muted players,",
                    "recent offences and a message tester."
                )
            );
        }

        tiles.add(
            tile(
                Material.COMPASS,
                "<gold><b>Server Selector",
                "<gray>Practice, the SMP server, more servers...",
                () -> this.plugin.serverSelector().editor(p),
                "Names, icons, server addresses,",
                "status badges and order."
            )
        );
        tiles.add(
            tile(
                Material.BEACON,
                "<gold><b>Hub Setup",
                this.plugin.modes().hubWorld() == null ? "<yellow>No hub yet - one click" : "<green>Hub: " + this.plugin.modes().hubWorld().getName(),
                () -> {
                    p.closeDialog();
                    this.plugin.hub().setup(p);
                },
                "Creates a hub world (if needed), builds",
                "the spawn with NPCs. Players join there."
            )
        );
        tiles.add(
            tile(
                Material.GOLDEN_HELMET,
                "<gold><b>Builds",
                "<gray>Hubs, spawns and small builds, with 3D previews",
                () -> this.buildsHome(p),
                "Preview any design in 3D in front",
                "of you, then build it where you stand."
            )
        );
        tiles.add(
            tile(
                Material.OAK_SAPLING,
                "<green><b>Generate Duel Arena",
                "<gray>Biome map with copies",
                () -> ArenaListMenu.generatePrompt(this.plugin, p, false),
                "Plains, Crystal Flats, Badlands..."
            )
        );
        tiles.add(
            tile(
                Material.CHERRY_SAPLING,
                "<green><b>Generate FFA Arena",
                "<gray>Biome FFA for a kit",
                () -> ArenaListMenu.generatePrompt(this.plugin, p, true),
                "With spawn points."
            )
        );
        tiles.add(
            tile(
                Material.MOSS_BLOCK,
                "<green><b>Rebuild Arenas",
                "<gray>New terrain, same maps",
                () -> {
                    p.closeDialog();
                    this.plugin
                        .dialogs()
                        .confirm(
                            p,
                            Text.mm("<gold><b>Rebuild generated arenas?"),
                            List.of(
                                Text.mm("<gray>Every generated duel arena gets fresh terrain from the current"),
                                Text.mm("<gray>generator (clean grass, no stone lines). Names, settings and copies"),
                                Text.mm("<gray>stay. Arenas being played are skipped. Runs in the background.")
                            ),
                            () -> {
                                p.sendMessage(Text.mm("<yellow>Rebuilding generated arenas..."));
                                this.plugin
                                    .arenas()
                                    .rebuildAll()
                                    .whenComplete(
                                        (n, err) -> Tasks.ensureSync(
                                            () -> p.sendMessage(
                                                Text.mm(
                                                    err != null
                                                        ? "<red>Rebuilding failed - see the console."
                                                        : "<green>Rebuilt <white>" + n + "</white> arena(s)."
                                                )
                                            )
                                        )
                                    );
                            }
                        );
                },
                "Regenerates every generated duel arena",
                "with the improved generator."
            )
        );
        tiles.add(
            tile(
                Material.PAINTING,
                "<gold><b>Scoreboard Designs",
                "<gray>Now: <white>" + (this.plugin.smpBoard() == null ? "?" : this.plugin.smpBoard().design()),
                () -> this.boardDesigns(p),
                "8 ready-made looks for every sidebar",
                "(PvP lobby, hub and each SMP mode)."
            )
        );
        tiles.add(
            tile(
                Material.OAK_SIGN,
                "<gold><b>Tab Design",
                "<gray>Now: <white>" + (this.plugin.tab() == null ? "?" : net.practicepvp.social.TabDesigns.get(this.plugin.tab().design()).name()),
                () -> this.tabDesigns(p),
                "11 complete tab list looks: Classic, Competitive,",
                "Network, Minimal, Neon, Stats Wall, Royal, Ocean..."
            )
        );
        tiles.add(
            tile(
                Material.AMETHYST_CLUSTER,
                "<gold><b>Decorations",
                "<gray>" + (this.plugin.decos() == null ? 0 : this.plugin.decos().count()) + " placed • 3D models",
                () -> {
                    if (this.plugin.decos() != null) {
                        this.plugin.decos().menu(p);
                    }
                },
                "Swords, trophies, lamps, trees, portals...",
                "placed as 3D models, never as blocks."
            )
        );
        tiles.add(tile(Material.ITEM_FRAME, "<gold><b>Wall Pictures", "<gray>/wallimage <link> [width]", () -> {
            p.closeDialog();
            p.performCommand("wallimage");
        }, "Put any picture on a wall", "as glowing map frames."));
        tiles.add(
            tile(
                Material.FILLED_MAP,
                "<gold><b>Website & Admin Panel",
                "<gray>"
                    + (
                        this.plugin.web() != null && this.plugin.web().available()
                            ? "<green>" + (this.plugin.web().onSamePort() ? "on the Minecraft port" : "running on port " + this.plugin.web().port())
                            : "<red>off"
                    ),
                () -> {
                    p.closeDialog();
                    p.performCommand("webadmin");
                },
                "Leaderboards, tier list, profiles,",
                "and this admin panel in a browser."
            )
        );
        tiles.add(tile(Material.OAK_SIGN, "<gold><b>Leaderboard Hologram", "<gray>" + this.plugin.holograms().count() + " placed", () -> this.plugin.dialogs().choice(
            p, LobbyDialogs.title("Leaderboard Hologram", "What to do"), "Action", List.of(
                new Dialogs.Choice("new", "<green>+ New, in front of me"), new Dialogs.Choice("edit", "<yellow>Edit the nearest one"), new Dialogs.Choice("remove", "<red>Remove the nearest one")
            ), "new", v -> {
                switch (v) {
                    case "edit" -> {
                        if (!this.plugin.holograms().editNearest(p)) {
                            Msg.send(p, "admin.hologram-none");
                        }
                    }
                    case "remove" -> Msg.send(p, this.plugin.holograms().removeNearest(p.getLocation()) ? "admin.hologram-removed" : "admin.hologram-none");
                    default -> this.plugin.holograms().prompt(p);
                }
            }), "Live top lists by any stat and kit,", "9 designs, colours, size, facing.", "Stand near one to edit it."));
        Session s = this.plugin.sessions().get(p);
        List<ActionButton> b = new ArrayList<>();
        b.add(
            this.ui
                .btn(
                    p,
                    "<gold>Set Lobby Here",
                    "<gray>Current: " + (this.plugin.lobby().lobbySet() ? Text.esc(Text.plain(Text.mm(Locs.pretty(this.plugin.lobby().lobby())))) : "not set"),
                    () -> {
                        if (!this.plugin.worlds().managed(p.getWorld())) {
                            Msg.error(p, "admin.lobby-excluded-world");
                        } else {
                            this.plugin.lobby().setLobby(p.getLocation());
                            Msg.send(p, "admin.lobby-set");
                        }

                        this.main(p);
                    }
                )
        );
        b.add(this.ui.btn(p, "<gold>Go To Lobby", null, () -> {
            p.closeDialog();
            Tp.to(p, this.plugin.lobby().lobby());
        }));
        b.add(this.ui.btn(p, "<gold>Region Wand", "<gray>Arena bounds and gates", () -> {
            p.closeDialog();
            AdminMenu.giveWand(p);
        }));
        b.add(this.ui.btn(p, "<white>Build Mode " + onOff(s.buildMode), "<gray>Creative + build in the lobby (you only)", () -> {
            s.buildMode = !s.buildMode;
            Msg.send(p, s.buildMode ? "admin.build-on" : "admin.build-off");
            if (this.plugin.sessions().state(p) == PlayerState.LOBBY) {
                this.plugin.lobby().sendToLobby(p);
            }

            this.main(p);
        }));
        b.add(this.toggle(p, "Maintenance", Opt.MAINTENANCE));
        b.add(this.toggle(p, "Ranked", Opt.RANKED_ENABLED));
        b.add(this.toggle(p, "Unranked", Opt.UNRANKED_ENABLED));
        b.add(this.toggle(p, "FFA", Opt.FFA_ENABLED));
        b.add(this.toggle(p, "Parties", Opt.PARTY_ENABLED));
        b.add(this.ui.btn(p, "<yellow>Remove Hologram", "<gray>The nearest one (5 blocks)", () -> {
            Msg.send(p, this.plugin.holograms().removeNearest(p.getLocation()) ? "admin.hologram-removed" : "admin.hologram-none");
            this.main(p);
        }));
        b.add(this.ui.btn(p, "<aqua>Reload Files", "<gray>config, messages, hotbar, kits", () -> {
            this.plugin.reloadFiles();
            Msg.send(p, "admin.reloaded");
            this.main(p);
        }));
        b.add(this.ui.btn(p, "<aqua>Save Everything", null, () -> {
            this.plugin.saveAll();
            Msg.send(p, "admin.saved");
            this.main(p);
        }));
        this.ui
            .screen(
                p,
                Fx.shineFrame("PVP ADMIN", Fx.GOLD, 2.0).append(Text.mm(" <dark_gray>»</dark_gray> <gray>v" + this.plugin.getPluginMeta().getVersion())),
                tiles,
                header,
                null,
                b,
                3,
                this.ui.close(p)
            );
    }

    private ActionButton toggle(Player p, String name, Opt<Boolean> opt) {
        return this.ui.btn(p, "<white>" + name + " " + onOff(Cfg.bool(opt)), null, () -> {
            Cfg.set(opt, !Cfg.bool(opt));
            ConfigMenu.applied(this.plugin, opt);
            Snd.click(p);
            this.main(p);
        });
    }

    public void worlds(Player p) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (World w : Bukkit.getWorlds()) {
            if (VanillaTerrain.isSourceWorld(w.getName())) {
                continue;
            }

            WorldMode m = this.plugin.modes().mode(w);
            boolean internal = w.getName().equals("practice_world");
            tiles.add(
                tile(
                    internal ? Material.STRUCTURE_VOID : m.icon,
                    m.color + "<b>" + Text.esc(w.getName()),
                    m.mm() + " <dark_gray>• <gray>" + w.getPlayers().size() + " players" + (internal ? " <dark_gray>(arenas)" : ""),
                    () -> this.world(p, w),
                    m.description
                )
            );
        }

        List<ActionButton> b = new ArrayList<>();
        b.add(
            this.ui
                .btn(
                    p,
                    "<green>+ Create World",
                    "<gray>Normal, flat or void",
                    () -> this.plugin
                        .dialogs()
                        .text(
                            p,
                            Text.mm("<gold><b>New world"),
                            "World name",
                            "",
                            32,
                            name -> {
                                String clean = name.replaceAll("[^A-Za-z0-9_-]", "");
                                if (!clean.isEmpty() && Bukkit.getWorld(clean) == null) {
                                    this.plugin
                                        .dialogs()
                                        .choice(
                                            p,
                                            Text.mm("<gold><b>World type"),
                                            "Type",
                                            List.of(
                                                new Dialogs.Choice("normal", "<green>Normal terrain"),
                                                new Dialogs.Choice("flat", "<yellow>Flat"),
                                                new Dialogs.Choice("void", "<gray>Void (empty)")
                                            ),
                                            "normal",
                                            type -> this.plugin
                                                .dialogs()
                                                .choice(p, Text.mm("<gold><b>World mode"), "Mode", modeChoices(), "SURVIVAL", mode -> {
                                                    p.sendMessage(Text.mm("<yellow>Creating " + clean + "... (the server may pause a moment)"));
                                                    World w = this.plugin.modes().create(clean, type);
                                                    if (w == null) {
                                                        p.sendMessage(Text.mm("<red>Could not create the world."));
                                                    } else {
                                                        this.plugin.modes().setMode(w, WorldMode.valueOf(mode));
                                                        this.world(p, w);
                                                    }
                                                })
                                        );
                                } else {
                                    p.sendMessage(Text.mm("<red>Invalid or existing name."));
                                }
                            }
                        )
                )
        );
        List<String> unloaded = unloadedWorlds();
        b.add(
            this.ui
                .btn(
                    p,
                    "<aqua>⇪ Load Existing World" + (unloaded.isEmpty() ? "" : " <white>(" + unloaded.size() + ")"),
                    "<gray>A world folder on the server that isn't loaded<newline><gray>(after an update or copied from another server)",
                    () -> {
                        if (unloaded.isEmpty()) {
                            p.sendMessage(Text.mm("<gray>Every world folder on the server is already loaded."));
                        } else {
                            List<Dialogs.Choice> c = new ArrayList<>();

                            for (String n : unloaded) {
                                c.add(new Dialogs.Choice(n, "<white>" + Text.esc(n)));
                            }

                            this.plugin
                                .dialogs()
                                .choice(
                                    p,
                                    Text.mm("<gold><b>Load world"),
                                    "World folder",
                                    c,
                                    unloaded.get(0),
                                    name -> this.plugin.dialogs().choice(p, Text.mm("<gold><b>World mode"), "Mode", modeChoices(), "SURVIVAL", mode -> {
                                        p.sendMessage(Text.mm("<yellow>Loading " + Text.esc(name) + "..."));
                                        World w = this.plugin.modes().create(name, "normal");
                                        if (w == null) {
                                            p.sendMessage(Text.mm("<red>Could not load that world."));
                                        } else {
                                            this.plugin.modes().setMode(w, WorldMode.valueOf(mode));
                                            p.sendMessage(Text.mm("<green>Loaded <white>" + Text.esc(name) + "</white> - it loads again on every start."));
                                            this.world(p, w);
                                        }
                                    })
                                );
                        }
                    }
                )
        );
        b.add(this.ui.btn(p, "<gold>Build Spawn Here", "<gray>A plaza with NPCs where you stand", () -> this.buildPrompt(p)));
        b.add(this.ui.btn(p, "<gold>Small Builds & Walls", "<gray>Fountains, statues, towers, stalls, walls...", () -> this.smallBuilds(p)));
        b.add(this.ui.btn(p, "<gray>« Admin Panel", null, () -> this.main(p)));
        this.ui.screen(p, LobbyDialogs.title("World Manager", "Click a world to set it up"), tiles, List.of(), null, b, 2, this.ui.close(p));
    }

    private static List<String> unloadedWorlds() {
        List<String> out = new ArrayList<>();
        File[] dirs = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (dirs == null) {
            return out;
        } else {
            for (File d : dirs) {
                if (new File(d, "level.dat").isFile()
                    && Bukkit.getWorld(d.getName()) == null
                    && !d.getName().endsWith("_nether")
                    && !d.getName().endsWith("_the_end")) {
                    out.add(d.getName());
                }
            }

            Collections.sort(out);
            return out;
        }
    }

    private static List<Dialogs.Choice> modeChoices() {
        List<Dialogs.Choice> c = new ArrayList<>();

        for (WorldMode m : WorldMode.values()) {
            if (!m.smp()) {
                c.add(new Dialogs.Choice(m.name(), m.mm()));
            }
        }

        return c;
    }

    public void world(Player p, World w) {
        WorldMode current = this.plugin.modes().mode(w);
        String lobbyWorld = this.plugin.lobby().lobby().getWorld() == null ? "" : this.plugin.lobby().lobby().getWorld().getName();
        boolean locked = w.getName().equals("practice_world") || w.getName().equals(lobbyWorld);
        Location sp = this.plugin.modes().spawn(w);
        List<Component> header = List.of(
            Text.mm(
                current.color
                    + "<b>"
                    + Text.esc(w.getName())
                    + "</b> <dark_gray>• "
                    + current.mm()
                    + " <dark_gray>• <gray>"
                    + w.getPlayers().size()
                    + " players"
            ),
            Text.mm("<gray>Name shown to players: <white>" + Text.esc(this.plugin.modes().display(w))),
            Text.mm(
                "<gray>Spawn: <white>"
                    + sp.getBlockX()
                    + " "
                    + sp.getBlockY()
                    + " "
                    + sp.getBlockZ()
                    + (locked ? " <dark_gray>• lobby / arena world: stays PvP" : "")
            )
        );
        List<ActionButton> b = new ArrayList<>();

        for (WorldMode m : WorldMode.values()) {
            if (m.smp()) {
                continue;
            }

            boolean sel = m == current;
            b.add(this.ui.btn(p, sel ? "<white><b><i>" + m.display + "</i></b>" : m.mm(), "<gray>" + m.description, () -> {
                if (locked && m != WorldMode.PVP) {
                    Msg.error(p, "admin.world-locked");
                } else if (m != current) {
                    this.plugin.modes().setMode(w, m);
                    Snd.click(p);
                }

                this.world(p, w);
            }));
        }

        b.add(this.ui.btn(p, "<aqua>Teleport", null, () -> {
            p.closeDialog();
            Tp.to(p, this.plugin.modes().spawn(w));
        }));
        b.add(this.ui.btn(p, "<aqua>Set Spawn Here", "<gray>You must stand in this world", () -> {
            if (!p.getWorld().equals(w)) {
                p.sendMessage(Text.mm("<red>Stand in " + Text.esc(w.getName()) + " first."));
            } else {
                this.plugin.modes().setSpawn(p.getLocation());
                p.sendMessage(Text.mm("<green>Spawn of " + Text.esc(w.getName()) + " set."));
            }

            this.world(p, w);
        }));
        b.add(
            this.ui
                .btn(
                    p,
                    "<aqua>Rename",
                    "<gray>The name players see",
                    () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Display name"), "Name", this.plugin.modes().display(w), 32, s -> {
                        this.plugin.modes().setDisplay(w, s);
                        this.world(p, w);
                    })
                )
        );
        b.add(this.ui.btn(p, "<gold>Build Spawn Here", "<gray>Plaza sized for this mode", () -> this.buildPrompt(p)));
        b.add(this.rule(p, w, "Keep Inventory", GameRules.KEEP_INVENTORY));
        b.add(this.rule(p, w, "PvP", GameRules.PVP));
        b.add(this.rule(p, w, "Mob Spawning", GameRules.SPAWN_MOBS));
        b.add(this.rule(p, w, "Day/Night Cycle", GameRules.ADVANCE_TIME));
        this.ui
            .screen(
                p,
                LobbyDialogs.title("World", Text.esc(w.getName())),
                List.of(),
                header,
                null,
                b,
                3,
                this.ui.btn(p, "<gray>« All Worlds", null, () -> this.worlds(p))
            );
    }

    private ActionButton rule(Player p, World w, String name, GameRule<Boolean> rule) {
        Boolean v = w.getGameRuleValue(rule);
        boolean on = v != null && v;
        return this.ui.btn(p, "<white>" + name + " " + onOff(on), null, () -> {
            w.setGameRule(rule, !on);
            this.world(p, w);
        });
    }

    public void smallBuilds(Player p) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (SmallBuilds.Type t : SmallBuilds.Type.values()) {
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(t.wall() ? Material.STONE_BRICK_WALL : Material.BRICKS),
                    Text.mm((t.wall() ? "<aqua>▬ " : "<gold>✦ ") + "<b>" + t.display),
                    Text.mm("<gray>" + (t.wall() ? "Wall, any length" : "Small build")),
                    List.of(Text.mm("<gold><b>" + t.display), Text.mm("<green>▶ Click: preview or build")),
                    () -> this.smallBuild(p, t)
                )
            );
        }

        this.ui
            .screen(
                p,
                LobbyDialogs.title("Small Builds", "Click one to preview or build it"),
                tiles,
                List.of(),
                null,
                List.of(this.ui.btn(p, "<gray>« Admin Panel", null, () -> this.main(p))),
                1,
                this.ui.close(p)
            );
    }

    private void smallBuild(Player p, SmallBuilds.Type t) {
        List<ActionButton> b = new ArrayList<>();
        b.add(this.ui.btn(p, "<aqua>👁 Preview (3D)", "<gray>A floating miniature in front of you, 30s", () -> {
            p.closeDialog();
            net.practicepvp.build.BuildPreview.preview(
                this.plugin,
                p,
                "small:" + t.name(),
                t.display,
                out -> {
                    SmallBuilds d = new SmallBuilds(0, 100, 0, t, true, 24, "south", 7L);

                    for (int x = d.minX(); x <= d.maxX(); x++) {
                        for (int z = d.minZ(); z <= d.maxZ(); z++) {
                            d.column(x, z, out::block);
                        }
                    }
                },
                true,
                m -> {}
            );
        }));
        b.add(this.ui.btn(p, "<green>🔨 Build here", "<gray>Where you stand, facing where you look", () -> {
            if (t.wall()) {
                this.plugin.dialogs().number(p, Text.mm("<gold><b>Wall length"), "Blocks", 8.0F, 200.0F, 1.0F, 32.0F, len -> this.placeSmall(p, t, Math.round(len)));
            } else {
                this.placeSmall(p, t, 0);
            }
        }));
        b.add(this.ui.btn(p, "<gray>« Back", null, () -> this.smallBuilds(p)));
        this.ui.screen(p, LobbyDialogs.title(t.display, "Preview or build"), List.of(), List.of(Text.mm("<gray>Preview it first to see how it looks.")), null, b, 1, this.ui.close(p));
    }

    private void placeSmall(Player p, SmallBuilds.Type t, int length) {
        Location l = p.getLocation();
        float yaw = (l.getYaw() % 360.0F + 360.0F) % 360.0F;
        String look = !(yaw >= 315.0F) && !(yaw < 45.0F) ? (yaw < 135.0F ? "west" : (yaw < 225.0F ? "north" : "east")) : "south";
        boolean alongX = look.equals("east") || look.equals("west");
        int cx = l.getBlockX();
        int cz = l.getBlockZ();
        if (t.wall()) {
            int dx = look.equals("east") ? 1 : (look.equals("west") ? -1 : 0);
            int dz = look.equals("south") ? 1 : (look.equals("north") ? -1 : 0);
            cx += dx * (length / 2 + 2);
            cz += dz * (length / 2 + 2);
        }
        String face = switch (look) {
            case "south" -> "north";
            case "north" -> "south";
            case "east" -> "west";
            default -> "east";
        };
        SmallBuilds d = new SmallBuilds(cx, l.getBlockY() - 1, cz, t, alongX, length, face, System.nanoTime());
        p.closeDialog();
        p.sendMessage(Text.mm("<yellow>Building " + t.display + "..."));
        SpawnBuilder.build(this.plugin, p.getWorld(), d)
            .whenComplete(
                (v, err) -> Tasks.ensureSync(
                    () -> p.sendMessage(Text.mm(err == null ? "<green>" + t.display + " built." : "<red>Building failed - see the console."))
                )
            );
    }

    private void buildsHome(Player p) {
        List<ActionButton> b = new ArrayList<>();
        b.add(this.ui.btn(p, "<gold>🏰 Hubs & Spawns", "<gray>Castles, towns, islands and plazas", () -> this.buildPrompt(p)));
        b.add(this.ui.btn(p, "<gold>🧱 Small Builds & Walls", "<gray>Fountains, statues, towers, stalls, walls...", () -> this.smallBuilds(p)));
        b.add(this.ui.btn(p, "<gold>🤖 Bot Practice Hall", "<gray>Map alcoves with mobs on pedestals: click one to fight a bot there.<newline><gray>Built where you stand (preview first).", () -> this.botHallPrompt(p)));
        b.add(this.ui.btn(p, "<gray>« Admin Panel", null, () -> this.main(p)));
        this.ui
            .screen(
                p,
                LobbyDialogs.title("Builds", "Pick a kind"),
                List.of(),
                List.of(Text.mm("<gray>Every build can be previewed in 3D before you build it.")),
                null,
                b,
                1,
                this.ui.close(p)
            );
    }

    private void botHallPrompt(Player p) {
        List<ActionButton> b = new ArrayList<>();
        b.add(this.ui.btn(p, "<aqua>👁 Preview (3D)", "<gray>A floating miniature in front of you, 30s", () -> {
            p.closeDialog();
            net.practicepvp.build.BuildPreview.preview(this.plugin, p, "bothall", "Bot Practice Hall", out -> {
                net.practicepvp.build.BotHallDesign d = new net.practicepvp.build.BotHallDesign(0, 100, 0, 7L);

                for (int x = d.minX(); x <= d.maxX(); x++) {
                    for (int z = d.minZ(); z <= d.maxZ(); z++) {
                        d.column(x, z, out::block);
                    }
                }
            }, true, m -> {});
        }));
        b.add(this.ui.btn(p, "<green>🔨 Build here", "<gray>The hall opens to the south of where you stand", () -> {
            p.closeDialog();
            if (this.plugin.botHall() != null) {
                this.plugin.botHall().build(p);
            }
        }));
        b.add(this.ui.btn(p, "<gray>« Back", null, () -> this.buildsHome(p)));
        this.ui.screen(p, LobbyDialogs.title("Bot Practice Hall", "Preview or build"), List.of(), List.of(Text.mm("<gray>A map alcove per biome with its mob, plus search, training and Academy NPCs.")), null, b, 1, this.ui.close(p));
    }

    /** What each spawn style is, for the builds panel. */
    private static String styleInfo(SpawnBuilder.Style s) {
        if (s.theme != null) {
            return s.theme.display + " - an island lobby with houses and a plaza";
        } else {
            return switch (s) {
                case ROYAL_CASTLE -> "Royal Castle - a walled castle with towers, a keep and a courtyard on a hilly island: lake, waterfall, a fort with a bridge, a watchtower and a small market";
                case CITADEL -> "Vortex Citadel - the grand hub";
                case ARCHIPELAGO -> "Sky Archipelago - floating islands with bridges";
                case NEON -> "Neon City";
                case KEEP -> "Royal Keep - a small castle";
                case GRAND_HUB -> "Vortex Grand Hub - huge";
                case TOWN_SQUARE -> "Town Square - a medieval town hub";
                case CASTLE_MARKET -> "Castle Market - castles, market stalls and a fountain";
                case PRACTICE_TOWN -> "Practice Town - a hall for every mode, shops, market and parkour";
                case COLOSSEUM_CITY -> "Colosseum City - 500 blocks, for practice";
                case SKY_EMPIRE -> "Sky Empire - 500 blocks";
                case VORTEX_ISLES -> "Vortex Isles - a floating island hub with the Vortex Spire, 8 gamemode shrines, hills, trees and a waterfall (pro-style)";
                case JUNGLE -> "Jungle Temple";
                case COLOSSEUM -> "Colosseum lobby - a round arena plaza";
                default -> s.name().charAt(0) + s.name().substring(1).toLowerCase(Locale.ROOT) + " plaza - a small spawn with NPC pedestals";
            };
        }
    }

    private static Material styleIcon(SpawnBuilder.Style s) {
        return switch (s) {
            case ROYAL_CASTLE -> Material.GOLDEN_HELMET;
            case CITADEL, GRAND_HUB -> Material.BEACON;
            case ARCHIPELAGO, SKY_EMPIRE -> Material.FEATHER;
            case VORTEX_ISLES -> Material.AMETHYST_CLUSTER;
            case NEON -> Material.GLOW_INK_SAC;
            case KEEP, CASTLE_MARKET -> Material.STONE_BRICKS;
            case TOWN_SQUARE, PRACTICE_TOWN -> Material.BELL;
            case COLOSSEUM, COLOSSEUM_CITY -> Material.CHISELED_SANDSTONE;
            case JUNGLE -> Material.MOSSY_COBBLESTONE;
            case TOWN -> Material.OAK_PLANKS;
            case DESERT -> Material.SANDSTONE;
            case SAKURA -> Material.CHERRY_LEAVES;
            case NETHER -> Material.NETHER_BRICKS;
            case WINTER -> Material.SNOW_BLOCK;
            case CELESTIAL -> Material.END_STONE;
            default -> Material.SMOOTH_STONE;
        };
    }

    /** The builds panel: every spawn / hub style as a tile; each can be previewed in 3D before building it. */
    private void buildPrompt(Player p) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        List<SpawnBuilder.Style> order = new ArrayList<>(List.of(SpawnBuilder.Style.values()));
        order.remove(SpawnBuilder.Style.ROYAL_CASTLE);
        order.add(0, SpawnBuilder.Style.ROYAL_CASTLE);

        for (SpawnBuilder.Style s : order) {
            String info = styleInfo(s);
            int dash = info.indexOf(" - ");
            String name = dash > 0 ? info.substring(0, dash) : info;
            String desc = dash > 0 ? info.substring(dash + 3) : "";
            List<Component> hover = new ArrayList<>();
            hover.add(Text.mm("<gold><b>" + name));

            for (String line : wrap(desc, 38)) {
                hover.add(Text.mm("<gray>" + line));
            }

            hover.add(Component.empty());
            hover.add(Text.mm("<gray>Size: <white>radius " + s.radius));
            hover.add(Text.mm("<green>▶ Click: 3D preview or build"));
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(styleIcon(s)),
                    Text.mm((s == SpawnBuilder.Style.ROYAL_CASTLE ? "<yellow>★ " : "") + "<gold><b>" + name),
                    Text.mm("<gray>Radius " + s.radius + (s.big() ? "" : " <dark_gray>• small plaza")),
                    hover,
                    () -> this.buildStyle(p, s)
                )
            );
        }

        this.ui
            .screen(
                p,
                LobbyDialogs.title("Builds", "Preview a design in 3D, then build it"),
                tiles,
                List.of(Text.mm("<gray>Builds are made where you stand.")),
                null,
                List.of(this.ui.btn(p, "<gray>« Admin Panel", null, () -> this.main(p))),
                1,
                this.ui.close(p)
            );
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String w : text.split(" ")) {
            if (line.length() > 0 && line.length() + w.length() + 1 > width) {
                out.add(line.toString());
                line.setLength(0);
            }

            line.append(line.length() == 0 ? "" : " ").append(w);
        }

        if (line.length() > 0) {
            out.add(line.toString());
        }

        return out;
    }

    private void buildStyle(Player p, SpawnBuilder.Style sx) {
        List<ActionButton> b = new ArrayList<>();
        b.add(this.ui.btn(p, "<aqua>👁 Preview (3D)", "<gray>A floating miniature in front of you for 30s.<newline><gray>Only you see it.", () -> {
            p.closeDialog();
            org.bukkit.World w = p.getWorld();
            net.practicepvp.build.BuildPreview.preview(
                this.plugin,
                p,
                "style:" + sx.name() + ":" + sx.radius,
                styleInfo(sx).split(" - ")[0],
                out -> SpawnBuilder.feed(sx, w, sx.radius, out),
                sx.big(),
                m -> {}
            );
        }));
        b.add(this.ui.btn(p, "<green>🔨 Build here", "<gray>Choose the size, then it is built where you stand", () -> this.buildSize(p, sx)));
        if (sx == SpawnBuilder.Style.TOWN_SQUARE || sx.voxel() && new java.io.File(this.plugin.getDataFolder(), "hub-build.yml").isFile()) {
            b.add(this.ui.btn(p, "<gold>⬆ Upgrade lobby", "<gray>Rebuild your lobby in the same place with<newline><gray>the newest version of its design.<newline><gray>NPCs, holograms and the spawn stay.", () -> {
                p.closeDialog();
                this.plugin.dialogs().confirm(
                    p,
                    Text.mm("<gold><b>Upgrade the lobby?"),
                    List.of(
                        Text.mm("<gray>It is rebuilt in the same place with the newest design."),
                        Text.mm("<gray>A full backup is saved first. NPCs, holograms and the spawn stay."),
                        Text.mm("<red>Anything you built by hand inside the lobby area is replaced.")
                    ),
                    () -> this.plugin.modes().upgradeHub(p)
                );
            }));
        }
        b.add(this.ui.btn(p, "<gray>« Back", null, () -> this.buildPrompt(p)));
        List<Component> header = new ArrayList<>();

        for (String line : wrap(styleInfo(sx), 60)) {
            header.add(Text.mm("<gray>" + line));
        }

        this.ui.screen(p, LobbyDialogs.title(styleInfo(sx).split(" - ")[0], "Preview or build"), List.of(), header, null, b, 1, this.ui.close(p));
    }

    private void buildSize(Player p, SpawnBuilder.Style sx) {
        boolean big = sx == SpawnBuilder.Style.COLOSSEUM;
        boolean island = sx.theme != null;
        if (sx.voxel()) {
            this.plugin
                .dialogs()
                .number(p, Text.mm("<gold><b>Spawn size"), "Radius", sx.minRadius(), sx.maxRadius(), 1.0F, sx.radius, r -> this.plugin.modes().buildSpawn(p, sx, Math.round(r)));
        } else {
            this.plugin
                .dialogs()
                .number(
                    p,
                    Text.mm("<gold><b>Spawn size"),
                    island ? "Island radius" : (big ? "Plaza radius" : "Radius"),
                    island ? 40.0F : (big ? 26.0F : 6.0F),
                    island ? 80.0F : (big ? 70.0F : 56.0F),
                    1.0F,
                    sx.radius,
                    r -> this.plugin.modes().buildSpawn(p, sx, Math.round(r))
                );
        }
    }

    public void kitArenas(Player p) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (Kit k : this.plugin.kits().sorted()) {
            int n = this.plugin.arenas().forKit(k).size();
            String mode = k.arenas.isEmpty() ? "<gray>Automatic <dark_gray>(" + k.arenaKind().toLowerCase(Locale.ROOT) + " maps)" : "<gold>Picked by hand";
            tiles.add(
                new LobbyDialogs.Tile(
                    k.iconStack(),
                    Text.mm(k.color + "<b>" + Text.esc(k.displayName)),
                    Text.mm("<white>" + n + "</white> <gray>map" + (n == 1 ? "" : "s") + " <dark_gray>• ").append(Text.mm(mode)),
                    List.of(
                        Text.mm(k.color + "<b>" + Text.esc(k.displayName)), Text.mm(mode), Component.empty(), Text.mm("<green>▶ Click to choose its arenas")
                    ),
                    () -> this.kitArenas(p, k)
                )
            );
        }

        this.ui
            .screen(
                p,
                LobbyDialogs.title("Kit Arenas", "Pick a kit"),
                tiles,
                List.of(
                    Text.mm(
                        "<gray>By default every kit plays on the maps of its family (colosseums, grand colosseum, crystal flats, biomes). Pick maps by hand to override it."
                    )
                ),
                null,
                List.of(this.ui.btn(p, "<gray>« Admin Panel", null, () -> this.main(p))),
                1,
                this.ui.close(p)
            );
    }

    public void backups(Player p) {
        RankBackup rb = this.plugin.rankBackup();
        if (rb != null) {
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();
            List<String> files = rb.files();

            for (int i = 0; i < files.size(); i++) {
                String f = files.get(i);
                File file = new File(rb.dir(), f);
                String when = f.substring("ranks-".length(), f.length() - ".yml".length()).replace('_', ' ');
                long kb = Math.max(1L, file.length() / 1024L);
                tiles.add(
                    new LobbyDialogs.Tile(
                        new ItemStack(i == 0 ? Material.ENCHANTED_BOOK : Material.BOOK),
                        Text.mm((i == 0 ? "<green><b>" : "<white><b>") + when),
                        Text.mm("<gray>" + kb + " KB" + (i == 0 ? " <dark_gray>• <green>newest" : "")),
                        List.of(Text.mm("<white><b>" + f), Component.empty(), Text.mm("<yellow>▶ Click to restore it")),
                        () -> {
                            p.closeDialog();
                            this.plugin
                                .dialogs()
                                .confirm(
                                    p,
                                    Text.mm("<gold><b>Restore " + when + "?"),
                                    List.of(
                                        Text.mm("<gray>Every player in this backup gets back the points, tier and record"),
                                        Text.mm("<gray>of each kit it holds. Changes made after it are overwritten for"),
                                        Text.mm("<gray>those kits. Online players update at once.")
                                    ),
                                    () -> {
                                        p.sendMessage(Text.mm("<gray>Restoring <white>" + f + "<gray>..."));
                                        rb.restore(f)
                                            .whenComplete(
                                                (n, err) -> Tasks.ensureSync(
                                                    () -> p.sendMessage(
                                                        Text.mm(
                                                            err != null
                                                                ? "<red>Restore failed - see the console."
                                                                : "<green>Restored the ranks of <white>" + n + "</white> players."
                                                        )
                                                    )
                                                )
                                            );
                                    }
                                );
                        }
                    )
                );
            }

            this.ui
                .screen(
                    p,
                    LobbyDialogs.title("Rank Backups", files.size() + " kept"),
                    tiles,
                    List.of(
                        Text.mm(
                            "<gray>Every player's points, tier and record per kit, saved to <white>plugins/PracticePvP/backups</white> on each start and once a day (the last 20 are kept). Copy a file to a new server or plugin version and restore it there."
                        )
                    ),
                    null,
                    List.of(this.ui.btn(p, "<green>Back Up Now", "<gray>Saves a new backup", () -> {
                        p.sendMessage(Text.mm("<gray>Backing up every player's ranks..."));
                        rb.backup().whenComplete((r, err) -> Tasks.ensureSync(() -> {
                            if (err != null) {
                                p.sendMessage(Text.mm("<red>Backup failed - see the console."));
                            } else {
                                p.sendMessage(Text.mm("<green>Saved <white>" + r[1] + "</white> players to <white>backups/" + r[0]));
                                this.backups(p);
                            }
                        }));
                    }), this.ui.btn(p, "<gray>« Admin Panel", null, () -> this.main(p))),
                    2,
                    this.ui.close(p)
                );
        }
    }

    public void fullBackups(Player p) {
        ConfigBackup cb = this.plugin.configBackup();
        if (cb != null) {
            ConfigBackup.Kind kind = ConfigBackup.Kind.FULL;
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();
            List<String> files = cb.files(kind);

            for (int i = 0; i < Math.min(18, files.size()); i++) {
                String f = files.get(i);
                File file = new File(cb.dir(), f);
                String when = f.replace("full-", "").replace(".zip", "").replace('_', ' ');
                long kb = Math.max(1L, file.length() / 1024L);
                boolean newest = i == 0;
                tiles.add(
                    new LobbyDialogs.Tile(
                        new ItemStack(newest ? Material.SHULKER_BOX : Material.CHEST),
                        Text.mm((newest ? "<green><b>" : "<white><b>") + when),
                        Text.mm("<gray>" + kb + " KB" + (newest ? " <dark_gray>• <green>newest" : "")),
                        List.of(Text.mm("<white><b>" + f), Component.empty(), Text.mm("<yellow>▶ Click to restore it")),
                        () -> {
                            p.closeDialog();
                            this.plugin
                                .dialogs()
                                .confirm(
                                    p,
                                    Text.mm("<gold><b>Restore " + when + "?"),
                                    List.of(
                                        Text.mm("<gray>Every setting, kit and server rank goes back to this backup,"),
                                        Text.mm("<gray>and every player gets back their tiers and points from it."),
                                        Text.mm("<gray>The current settings are backed up first.")
                                    ),
                                    () -> {
                                        try {
                                            int n = cb.restore(kind, f);
                                            p.sendMessage(
                                                Text.mm(
                                                    "<green>Restored <white>"
                                                        + n
                                                        + "</white> file(s) from "
                                                        + Text.esc(f)
                                                        + "<gray> - player tiers are being put back."
                                                )
                                            );
                                        } catch (Exception var5x) {
                                            p.sendMessage(Text.mm("<red>Restore failed: " + Text.esc(String.valueOf(var5x.getMessage()))));
                                        }
                                    }
                                );
                        }
                    )
                );
            }

            this.ui
                .screen(
                    p,
                    LobbyDialogs.title("All-in-One Backup", files.size() + " kept"),
                    tiles,
                    List.of(
                        Text.mm(
                            "<gray>Ranks, kits and settings in <white>one file</white>: every settings file, every kit, the server ranks with their permissions and members, and every player's tier and points. Saved in <white>plugins/VortexRealm-Vault/exports</white> every 12 hours."
                        )
                    ),
                    null,
                    List.of(this.ui.btn(p, "<green>Back Up Now", "<gray>Saves everything in one file", () -> {
                        try {
                            String name = cb.backup(kind);
                            p.sendMessage(Text.mm("<green>Saved <white>" + name + "</white> <gray>(player tiers are added in a few seconds)"));
                        } catch (Exception var5x) {
                            p.sendMessage(Text.mm("<red>Backup failed: " + Text.esc(String.valueOf(var5x.getMessage()))));
                        }

                        this.fullBackups(p);
                    }), this.ui.btn(p, "<gray>« Admin Panel", null, () -> this.main(p))),
                    2,
                    this.ui.close(p)
                );
        }
    }

    public void ranks(Player p) {
        Ranks ranks = this.plugin.serverRanks();
        if (ranks != null) {
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();

            for (Ranks.Rank r : ranks.all()) {
                Material icon = Material.PAPER;
                if (r.icon != null && r.icon.startsWith("item/")) {
                    Material m = Material.matchMaterial(r.icon.substring(5));
                    if (m != null && m.isItem()) {
                        icon = m;
                    }
                }

                Component name = r.isDefault() ? Text.mm("<gray><b>default") : r.prefix();
                tiles.add(
                    new LobbyDialogs.Tile(
                        new ItemStack(icon),
                        name,
                        Text.mm("<gray>weight " + r.weight + " <dark_gray>• " + ranks.permissionsOf(r).size() + " perms • ").append(r.name("Steve")),
                        List.of(
                            name,
                            Text.mm("<gray>Given by: <white>/rank set</white> or <white>practice.rank." + r.id),
                            Text.mm("<gray>Gives: <white>" + Text.esc(String.join(", ", ranks.permissionsOf(r)))),
                            Component.empty(),
                            Text.mm("<green>▶ Click to edit")
                        ),
                        () -> this.rank(p, r)
                    )
                );
            }

            this.ui
                .screen(
                    p,
                    LobbyDialogs.title("Ranks", ranks.all().size() + " ranks"),
                    tiles,
                    List.of(
                        Text.mm(
                            "<gray>Give a rank: <white>/rank set <player> <rank></white> or the permission <white>practice.rank.<id></white> (LuckPerms groups). Higher weight = higher in the tab list."
                        )
                    ),
                    null,
                    List.of(
                        this.ui
                            .btn(
                                p,
                                "<green>+ New Rank",
                                null,
                                () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>New rank"), "Rank id (e.g. elite)", "", 16, id -> {
                                    if (id.isBlank()) {
                                        this.ranks(p);
                                    } else {
                                        this.rank(p, ranks.create(id));
                                    }
                                })
                            ),
                        this.ui.btn(p, "<gray>« Admin Panel", null, () -> this.main(p))
                    ),
                    2,
                    this.ui.close(p)
                );
        }
    }

    private void rank(Player p, Ranks.Rank r) {
        Ranks ranks = this.plugin.serverRanks();
        List<Component> header = List.of(
            r.isDefault() ? Text.mm("<gray>Default rank (no prefix)") : r.prefix().append(r.name(p.getName())),
            Text.mm(
                "<gray>Gradient <white>"
                    + r.from
                    + " → "
                    + r.to
                    + " <dark_gray>• <gray>icon <white>"
                    + Text.esc(r.icon)
                    + " <dark_gray>• <gray>weight <white>"
                    + r.weight
            )
        );
        List<ActionButton> b = new ArrayList<>();
        b.add(
            this.ui
                .btn(
                    p,
                    "<gold>Label",
                    "<gray>Text of the rank (e.g. MVP+)",
                    () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Label"), "Label", r.label, 24, v -> {
                        r.label = v;
                        ranks.save();
                        this.rank(p, r);
                    })
                )
        );
        b.add(
            this.ui
                .btn(
                    p,
                    "<gold>Gradient",
                    "<gray>Two colours, e.g. #FF5470 #B3001B",
                    () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Gradient"), "From and to (hex)", r.from + " " + r.to, 20, v -> {
                        String[] parts = v.trim().split("[ ,]+");
                        if (parts.length >= 1 && parts[0].matches("#[0-9A-Fa-f]{6}")) {
                            r.from = parts[0];
                            r.to = parts.length >= 2 && parts[1].matches("#[0-9A-Fa-f]{6}") ? parts[1] : parts[0];
                            ranks.save();
                        } else {
                            p.sendMessage(Text.mm("<red>Use hex colours like #FF5470 #B3001B"));
                        }

                        this.rank(p, r);
                    })
                )
        );
        b.add(
            this.ui
                .btn(
                    p,
                    "<gold>Name Colour",
                    "<gray>Colour of the player's name",
                    () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Name colour"), "Hex colour", r.nameColor, 7, v -> {
                        if (v.trim().matches("#[0-9A-Fa-f]{6}")) {
                            r.nameColor = v.trim();
                            ranks.save();
                        }

                        this.rank(p, r);
                    })
                )
        );
        b.add(this.ui.btn(p, "<gold>Icon = Your Hand", "<gray>The item you hold becomes the icon", () -> {
            ItemStack h = p.getInventory().getItemInMainHand();
            if (!h.isEmpty()) {
                r.icon = "item/" + h.getType().getKey().getKey();
                ranks.save();
            }

            this.rank(p, r);
        }));
        b.add(
            this.ui
                .btn(
                    p,
                    "<gold>Weight",
                    "<gray>Order in tab and which rank wins",
                    () -> this.plugin.dialogs().number(p, Text.mm("<gold><b>Weight"), "Weight", 0.0F, 2000.0F, 10.0F, r.weight, v -> {
                        r.weight = Math.round(v);
                        ranks.save();
                        this.rank(p, r);
                    })
                )
        );
        b.add(this.ui.btn(p, "<white>Bold " + onOff(r.bold), null, () -> {
            r.bold = !r.bold;
            ranks.save();
            this.rank(p, r);
        }));
        long given = ranks.access().gives(r).values().stream().filter(Boolean::booleanValue).count();
        b.add(
            this.ui.btn(p, "<aqua>Permissions <gray>(" + given + ")", "<gray>Tick any permission on the server, grouped by plugin", () -> this.rankPerms(p, r))
        );
        b.add(
            this.ui
                .btn(p, "<aqua>Members <gray>(" + ranks.members(r).size() + ")", "<gray>Who has this rank; add or take it away", () -> this.rankMembers(p, r))
        );
        b.add(this.ui.btn(p, "<aqua>What They Can Access", "<gray>The commands and permissions this rank's players get", () -> this.rankAccess(p, r)));
        b.add(
            this.ui
                .btn(
                    p,
                    "<aqua>Inherits: <white>" + (r.inherit != null && !r.inherit.isBlank() ? r.inherit : "none"),
                    "<gray>Also give another rank's permissions",
                    () -> {
                        List<Dialogs.Choice> c = new ArrayList<>();
                        c.add(new Dialogs.Choice("-", "<gray>none"));

                        for (Ranks.Rank o : ranks.all()) {
                            if (!o.id.equals(r.id)) {
                                c.add(new Dialogs.Choice(o.id, "<white>" + o.id));
                            }
                        }

                        this.plugin
                            .dialogs()
                            .choice(p, Text.mm("<gold><b>Inherit"), "Rank", c, r.inherit != null && !r.inherit.isBlank() ? r.inherit : "-", v -> {
                                r.inherit = "-".equals(v) ? "" : v;
                                ranks.changed();
                                this.rank(p, r);
                            });
                    }
                )
        );
        b.add(
            this.ui
                .btn(
                    p,
                    "<light_purple>Perks <gray>(website)",
                    "<gray>" + Text.esc(String.join(" | ", r.perks)),
                    () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Perks</b> <gray>(| between lines)"), "Perks", String.join("|", r.perks), 1024, v -> {
                        r.perks = new ArrayList<>();

                        for (String s : v.split("\\|")) {
                            if (!s.isBlank()) {
                                r.perks.add(s.trim());
                            }
                        }

                        ranks.changed();
                        this.rank(p, r);
                    })
                )
        );
        b.add(
            this.ui
                .btn(
                    p,
                    "<light_purple>Price: <white>" + (r.price.isBlank() ? "not sold" : Text.esc(r.price)),
                    "<gray>Shown on the website (empty = not for sale)",
                    () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Price"), "Price (e.g. $9.99)", r.price, 24, v -> {
                        r.price = v.trim();
                        ranks.changed();
                        this.rank(p, r);
                    })
                )
        );
        b.add(
            this.ui
                .btn(
                    p,
                    "<light_purple>Description",
                    "<gray>" + Text.esc(r.description),
                    () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Description"), "One sentence", r.description, 200, v -> {
                        r.description = v.trim();
                        ranks.changed();
                        this.rank(p, r);
                    })
                )
        );
        b.add(this.ui.btn(p, "<white>Staff rank " + onOff(r.staff), "<gray>Listed in the staff team on the website", () -> {
            r.staff = !r.staff;
            ranks.changed();
            this.rank(p, r);
        }));
        if (!r.isDefault() && !"default".equals(r.id)) {
            b.add(this.ui.btn(p, "<red>Delete", null, () -> {
                ranks.delete(r.id);
                this.ranks(p);
            }));
        }

        this.ui
            .screen(
                p, LobbyDialogs.title("Rank", Text.esc(r.id)), List.of(), header, null, b, 2, this.ui.btn(p, "<gray>« All Ranks", null, () -> this.ranks(p))
            );
    }

    private static Material groupIcon(String group) {
        return switch (group) {
            case "PracticePvP" -> Material.DIAMOND_SWORD;
            case "Minecraft" -> Material.GRASS_BLOCK;
            case "Bukkit", "Paper" -> Material.WRITABLE_BOOK;
            default -> Material.BOOK;
        };
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private void rankPerms(Player p, Ranks.Rank r) {
        Ranks ranks = this.plugin.serverRanks();
        RankAccess acc = ranks.access();
        Map<String, Boolean> gives = acc.gives(r);
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (String g : acc.groups()) {
            List<RankAccess.Node> nodes = acc.group(g);
            List<Component> hover = new ArrayList<>();
            hover.add(Text.mm("<white><b>" + Text.esc(g)));
            int on = 0;

            for (RankAccess.Node n : nodes) {
                if (Boolean.TRUE.equals(gives.get(n.node()))) {
                    if (on < 8) {
                        hover.add(Text.mm("<green>✔ <white>" + Text.esc(n.node())));
                    }

                    on++;
                }
            }

            if (on > 8) {
                hover.add(Text.mm("<gray>... and " + (on - 8) + " more"));
            }

            hover.add(Component.empty());
            hover.add(Text.mm("<green>▶ Click to tick permissions"));
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(groupIcon(g)),
                    Text.mm("<white><b>" + Text.esc(g)),
                    Text.mm((on > 0 ? "<green>" : "<gray>") + on + " <gray>of " + nodes.size() + " given"),
                    hover,
                    () -> this.rankPermPage(p, r, g, 0)
                )
            );
        }

        boolean everything = r.permissions.contains("*");
        String from = r.inherit != null && !r.inherit.isBlank() ? " <gray>Also gets everything <white>" + Text.esc(r.inherit) + "</white> has." : "";
        List<Component> header = List.of(
            (r.isDefault() ? Text.mm("<gray><b>default</b> ") : r.prefix()).append(Text.mm("<gray>players get what you tick." + from)),
            Text.mm("<gray>Every plugin's permissions, every command and vanilla Minecraft are here. <white>*</white> = everything.")
        );
        List<ActionButton> b = new ArrayList<>();
        b.add(
            this.ui
                .btn(
                    p,
                    everything ? "<red>Take Away Everything (*)" : "<gold>Give Everything (*)",
                    "<gray>Every permission on the server (operator-like)",
                    () -> {
                        acc.set(r, "*", everything ? null : Boolean.TRUE);
                        ranks.changed();
                        this.rankPerms(p, r);
                    }
                )
        );
        b.add(
            this.ui
                .btn(
                    p,
                    "<aqua>Type Permissions",
                    "<gray>The rank's own list, comma separated. -node takes one away, plugin.* gives a whole plugin",
                    () -> this.plugin
                        .dialogs()
                        .text(
                            p,
                            Text.mm("<gold><b>Permissions</b> <gray>(comma separated, -node takes away)"),
                            "Permissions",
                            String.join(", ", r.permissions),
                            2048,
                            v -> {
                                r.permissions = new ArrayList<>();

                                for (String s : v.split("[,\\s]+")) {
                                    if (!s.isBlank()) {
                                        r.permissions.add(s.trim());
                                    }
                                }

                                ranks.changed();
                                this.rankPerms(p, r);
                            }
                        )
                )
        );
        b.add(this.ui.btn(p, "<aqua>What They Can Access", null, () -> this.rankAccess(p, r)));
        this.ui
            .screen(
                p, LobbyDialogs.title("Permissions", Text.esc(r.id)), tiles, header, null, b, 2, this.ui.btn(p, "<gray>« Rank", null, () -> this.rank(p, r))
            );
    }

    private void rankPermPage(Player p, Ranks.Rank r, String group, int page) {
        Ranks ranks = this.plugin.serverRanks();
        RankAccess acc = ranks.access();
        List<RankAccess.Node> nodes = acc.group(group);
        int pages = Math.max(1, (nodes.size() + 18 - 1) / 18);
        int pg = Math.max(0, Math.min(page, pages - 1));
        List<RankAccess.Node> slice = nodes.subList(pg * 18, Math.min(nodes.size(), (pg + 1) * 18));
        Map<String, Boolean> gives = acc.gives(r);
        List<Dialogs.Check> checks = new ArrayList<>();

        for (RankAccess.Node n : slice) {
            boolean on = Boolean.TRUE.equals(gives.get(n.node()));
            String src = on ? acc.source(r, n.node()) : "";
            String desc = n.description().isBlank() ? "" : " <gray>" + Text.esc(cut(n.description(), 46));
            checks.add(
                new Dialogs.Check(
                    n.node(), Text.mm("<white>" + Text.esc(n.node()) + (src.isEmpty() ? "" : " <dark_gray>(from " + Text.esc(src) + ")") + desc), on
                )
            );
        }

        Consumer<Set<String>> save = ticked -> {
            Map<String, Boolean> now = acc.gives(r);
            boolean changed = false;

            for (RankAccess.Node n : slice) {
                boolean was = Boolean.TRUE.equals(now.get(n.node()));
                boolean want = ticked.contains(n.node());
                if (was != want) {
                    acc.set(r, n.node(), want);
                    changed = true;
                }
            }

            if (changed) {
                ranks.changed();
            }
        };
        List<Dialogs.CheckButton> b = new ArrayList<>();
        b.add(new Dialogs.CheckButton(Text.mm("<green><b>Save"), null, t -> {
            save.accept(t);
            p.sendMessage(Text.mm("<green>Saved <white>" + Text.esc(r.id) + "</white> permissions in " + Text.esc(group) + "."));
            this.rankPermPage(p, r, group, pg);
        }));
        if (pg < pages - 1) {
            b.add(new Dialogs.CheckButton(Text.mm("<green>Save & Next ›"), null, t -> {
                save.accept(t);
                this.rankPermPage(p, r, group, pg + 1);
            }));
        }

        if (pg > 0) {
            b.add(new Dialogs.CheckButton(Text.mm("<gray>‹ Save & Back"), null, t -> {
                save.accept(t);
                this.rankPermPage(p, r, group, pg - 1);
            }));
        }

        b.add(new Dialogs.CheckButton(Text.mm("<yellow>Tick All on This Page"), null, t -> {
            Set<String> all = new HashSet<>();
            slice.forEach(n -> all.add(n.node()));
            save.accept(all);
            this.rankPermPage(p, r, group, pg);
        }));
        b.add(new Dialogs.CheckButton(Text.mm("<gray>Untick All on This Page"), null, t -> {
            save.accept(Set.of());
            this.rankPermPage(p, r, group, pg);
        }));
        List<Component> header = List.of(
            (r.isDefault() ? Text.mm("<gray><b>default</b> ") : r.prefix())
                .append(Text.mm("<gray>in <white>" + Text.esc(group) + "</white> <dark_gray>• <gray>page " + (pg + 1) + "/" + pages)),
            Text.mm("<gray>Ticked = players of this rank get it. Unticking one an inherited rank gives takes it away for this rank only.")
        );
        this.plugin
            .dialogs()
            .checks(
                p,
                LobbyDialogs.title(group, Text.esc(r.id)),
                header,
                checks,
                b,
                this.ui.btn(p, "<gray>« All Plugins", "<gray>Without saving this page", () -> this.rankPerms(p, r))
            );
    }

    private void rankMembers(Player p, Ranks.Rank r) {
        Ranks ranks = this.plugin.serverRanks();
        List<Ranks.Member> ms = ranks.members(r);
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (int i = 0; i < Math.min(45, ms.size()); i++) {
            Ranks.Member m = ms.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            Player online = Bukkit.getPlayer(m.uuid());
            if (online != null) {
                head.editMeta(SkullMeta.class, sm -> sm.setOwningPlayer(online));
            }

            boolean byCommand = "/rank set".equals(m.how());
            tiles.add(
                new LobbyDialogs.Tile(
                    head,
                    Text.mm((m.online() ? "<green>● " : "<dark_gray>● ") + "<white><b>" + Text.esc(m.name())),
                    Text.mm("<gray>" + (m.online() ? "online" : "offline") + " <dark_gray>• <gray>" + Text.esc(m.how())),
                    List.of(
                        Text.mm("<white><b>" + Text.esc(m.name())),
                        Text.mm("<gray>Has it through: <white>" + Text.esc(m.how())),
                        Component.empty(),
                        Text.mm(byCommand ? "<red>▶ Click to take the rank away" : "<gray>Change it where it comes from")
                    ),
                    () -> {
                        if (!byCommand) {
                            p.sendMessage(
                                Text.mm(
                                    "<gray>"
                                        + Text.esc(m.name())
                                        + " gets "
                                        + Text.esc(r.id)
                                        + " through <white>"
                                        + Text.esc(m.how())
                                        + "</white> - change it there."
                                )
                            );
                        } else {
                            this.plugin
                                .dialogs()
                                .confirm(
                                    p,
                                    Text.mm("<gold><b>Take " + Text.esc(r.id) + " from " + Text.esc(m.name()) + "?"),
                                    List.of(Text.mm("<gray>They go back to the default rank.")),
                                    () -> {
                                        ranks.assign(m.uuid(), null);
                                        this.rankMembers(p, r);
                                    }
                                );
                        }
                    }
                )
            );
        }

        List<Component> header = List.of(
            (r.isDefault() ? Text.mm("<gray><b>default</b> ") : r.prefix())
                .append(
                    Text.mm(
                        "<gray>"
                            + ms.size()
                            + " player"
                            + (ms.size() == 1 ? "" : "s")
                            + (ms.size() > 45 ? " <dark_gray>(first 45 shown, /rank members " + r.id + " for all)" : "")
                    )
                )
        );
        List<ActionButton> b = new ArrayList<>();
        if (!r.isDefault()) {
            b.add(
                this.ui
                    .btn(
                        p,
                        "<green>+ Add Player",
                        "<gray>Give this rank to a player (online or not)",
                        () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Give " + Text.esc(r.id)), "Player name", "", 16, v -> {
                            if (!v.matches("[A-Za-z0-9_]{1,16}")) {
                                p.sendMessage(Text.mm("<red>That isn't a Minecraft name."));
                            } else {
                                OfflinePlayer t = Bukkit.getOfflinePlayer(v);
                                ranks.assign(t.getUniqueId(), r.id);
                                p.sendMessage(Text.mm("<green>" + Text.esc(v) + " <gray>is now ").append(r.prefix()));
                            }

                            this.rankMembers(p, r);
                        })
                    )
            );
        }

        this.ui
            .screen(
                p,
                LobbyDialogs.title("Members", Text.esc(r.id)),
                tiles,
                header,
                Text.mm("<gray>Nobody has this rank yet. Add a player below."),
                b,
                2,
                this.ui.btn(p, "<gray>« Rank", null, () -> this.rank(p, r))
            );
    }

    private void rankAccess(Player p, Ranks.Rank r) {
        RankAccess acc = this.plugin.serverRanks().access();
        RankAccess.Access a = acc.access(r);
        List<Component> lines = new ArrayList<>();
        lines.add(
            (r.isDefault() ? Text.mm("<gray><b>default</b> ") : r.prefix())
                .append(Text.mm("<gray>players can use <white>" + a.allCommands() + "</white> commands in total."))
        );
        List<String> cmds = new ArrayList<>();
        a.extraCommands().forEach(c -> cmds.add("/" + c.name()));
        lines.add(Text.mm("<gold><b>Commands this rank adds (" + cmds.size() + ")"));
        lines.add(Text.mm(cmds.isEmpty() ? "<gray>None - the same commands as everyone." : "<white>" + Text.esc(cut(String.join("  ", cmds), 900))));
        lines.add(Text.mm("<gold><b>Permissions this rank adds (" + a.extraPermissions().size() + ")"));
        Map<String, List<String>> others = new LinkedHashMap<>();
        int shown = 0;

        for (RankAccess.Node n : a.extraPermissions()) {
            if (n.group().equals("PracticePvP") && !n.description().isBlank()) {
                if (shown++ < 30) {
                    lines.add(Text.mm("<green>✔ <white>" + Text.esc(n.description()) + " <dark_gray>" + Text.esc(n.node())));
                }
            } else {
                others.computeIfAbsent(n.group(), k -> new ArrayList<>()).add(n.node());
            }
        }

        others.forEach(
            (g, list) -> lines.add(Text.mm("<aqua>" + Text.esc(g) + " <gray>(" + list.size() + "): <white>" + Text.esc(cut(String.join(", ", list), 500))))
        );
        if (a.extraPermissions().isEmpty()) {
            lines.add(Text.mm("<gray>Nothing more than a default player."));
        }

        if (!a.unknownPermissions().isEmpty()) {
            lines.add(Text.mm("<gray>Not on this server (plugin missing?): <white>" + Text.esc(cut(String.join(", ", a.unknownPermissions()), 300))));
        }

        this.ui
            .screen(
                p,
                LobbyDialogs.title("Access", Text.esc(r.id)),
                List.of(),
                lines,
                null,
                List.of(
                    this.ui.btn(p, "<aqua>Permissions", null, () -> this.rankPerms(p, r)), this.ui.btn(p, "<aqua>Members", null, () -> this.rankMembers(p, r))
                ),
                2,
                this.ui.btn(p, "<gray>« Rank", null, () -> this.rank(p, r))
            );
    }

    public void vault(Player p) {
        DataVault v = this.plugin.vault();
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        List<String> files = v.files();

        for (int i = 0; i < Math.min(24, files.size()); i++) {
            String f = files.get(i);
            File file = new File(v.dir(), f);
            long mb = Math.max(1L, file.length() / 1024L);
            String label = f.replace("vault-", "").replace(".zip", "").replace('_', ' ');
            Material icon = f.contains("pre-update") ? Material.ENDER_CHEST : (f.contains("shutdown") ? Material.BARREL : Material.CHEST);
            tiles.add(
                new LobbyDialogs.Tile(
                    new ItemStack(icon),
                    Text.mm((i == 0 ? "<green><b>" : "<white><b>") + label),
                    Text.mm("<gray>" + (mb >= 1024L ? mb / 1024L + " MB" : mb + " KB") + (i == 0 ? " <dark_gray>• <green>newest" : "")),
                    List.of(
                        Text.mm("<white><b>" + f),
                        Text.mm("<gray>" + v.playerFiles(f) + " player inventory files"),
                        Component.empty(),
                        Text.mm("<yellow>▶ Click to restore it at the next start")
                    ),
                    () -> {
                        p.closeDialog();
                        this.plugin
                            .dialogs()
                            .confirm(
                                p,
                                Text.mm("<gold><b>Restore " + label + "?"),
                                List.of(
                                    Text.mm("<gray>At the next server start the whole plugin folder (settings, kits,"),
                                    Text.mm("<gray>arenas, worlds, NPCs, inventories, ranks) and every player's inventory"),
                                    Text.mm("<gray>(ender chest, XP) go back to this snapshot."),
                                    Text.mm("<gray>The current data is snapshotted first.")
                                ),
                                () -> {
                                    try {
                                        v.scheduleRestore(f);
                                        p.sendMessage(Text.mm("<green>Done. <yellow>Restart the server</yellow> to apply it."));
                                    } catch (IOException var4x) {
                                        p.sendMessage(Text.mm("<red>Could not schedule it - see the console."));
                                    }
                                }
                            );
                    }
                )
            );
        }

        this.ui
            .screen(
                p,
                LobbyDialogs.title("Data Vault", files.size() + " snapshots"),
                tiles,
                List.of(
                    Text.mm(
                        "<gray>Everything the plugin keeps is saved to <white>plugins/VortexRealm-Vault</white> (outside the plugin folder) every 6 hours, at shutdown and before every update: settings, kits, arenas, worlds, NPCs, holograms, ranks, the database and every player's inventory, ender chest and XP. If the plugin folder is ever deleted or replaced, the newest snapshot comes back by itself."
                    )
                ),
                null,
                List.of(this.ui.btn(p, "<green>Snapshot Now", "<gray>Saves everything right now", () -> {
                    p.sendMessage(Text.mm("<gray>Saving a full snapshot..."));
                    this.plugin.vaultSnapshot("manual", (name, err) -> {
                        p.sendMessage(Text.mm(err != null ? "<red>Snapshot failed - see the console." : "<green>Saved <white>" + name));
                        if (err == null) {
                            this.vault(p);
                        }
                    });
                }), this.ui.btn(p, "<white>Last Update Report", "<gray>What the last update carried over", () -> {
                    p.closeDialog();
                    if (this.plugin.upgrader() != null && this.plugin.upgrader().updated()) {
                        this.plugin.upgrader().tell(p);
                    } else {
                        File last = new File(v.dir(), "last-update.txt");
                        try {
                            for (String line : last.isFile() ? java.nio.file.Files.readAllLines(last.toPath()) : List.of("No update since this server started.")) {
                                p.sendMessage(Text.mm("<gray>" + Text.esc(line)));
                            }
                        } catch (IOException var5) {
                            p.sendMessage(Text.mm("<red>Could not read the report."));
                        }
                    }
                }), this.ui.btn(p, "<gray>« Admin Panel", null, () -> this.main(p))),
                2,
                this.ui.close(p)
            );
    }

    public void kitArenas(Player p, Kit k) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (Arena a : this.plugin.arenas().all()) {
            boolean on = k.arenas.isEmpty() ? a.allows(k) : k.arenas.contains(a.id);
            ItemStack icon = new ItemStack(a.iconBlock == null ? Material.GRASS_BLOCK : a.iconBlock);
            String theme = a.theme == null ? "custom" : a.theme.display;
            tiles.add(
                new LobbyDialogs.Tile(
                    icon,
                    Text.mm((on ? "<green>✔ " : "<red>✖ ") + "<white><b>" + Text.esc(a.displayName)),
                    Text.mm(
                        "<gray>"
                            + Text.esc(theme)
                            + " <dark_gray>• <gray>"
                            + a.instances.size()
                            + " cop"
                            + (a.instances.size() == 1 ? "y" : "ies")
                            + (a.ready() ? "" : " <red>(not ready)")
                    ),
                    List.of(
                        Text.mm("<white><b>" + Text.esc(a.displayName)),
                        Text.mm("<gray>Family: <white>" + a.pool.toLowerCase(Locale.ROOT)),
                        Component.empty(),
                        Text.mm(on ? "<red>▶ Click to remove it from " + Text.esc(k.displayName) : "<green>▶ Click to add it to " + Text.esc(k.displayName))
                    ),
                    () -> {
                        if (k.arenas.isEmpty()) {
                            for (Arena x : this.plugin.arenas().all()) {
                                if (x.allows(k)) {
                                    k.arenas.add(x.id);
                                }
                            }
                        }

                        if (!k.arenas.remove(a.id)) {
                            k.arenas.add(a.id);
                        }

                        if (k.arenas.isEmpty()) {
                            p.sendMessage(Text.mm("<yellow>No arena left - " + Text.esc(k.displayName) + " is back to automatic."));
                        }

                        this.plugin.kits().save();
                        this.kitArenas(p, k);
                    }
                )
            );
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(this.ui.btn(p, "<aqua>⟲ Automatic", "<gray>Use the maps of the kit's family again", () -> {
            k.arenas.clear();
            this.plugin.kits().save();
            this.kitArenas(p, k);
        }));
        buttons.add(this.ui.btn(p, "<gray>« All Kits", null, () -> this.kitArenas(p)));
        this.ui
            .screen(
                p,
                LobbyDialogs.title(Text.esc(k.displayName), "Click maps to toggle them"),
                tiles,
                List.of(
                    Text.mm(
                        k.arenas.isEmpty()
                            ? "<gray>Mode: <white>automatic <dark_gray>(" + k.arenaKind().toLowerCase(Locale.ROOT) + " maps)"
                            : "<gray>Mode: <gold>picked by hand <dark_gray>(" + k.arenas.size() + " maps)"
                    )
                ),
                Text.mm("<gray>No arenas exist yet."),
                buttons,
                2,
                this.ui.close(p)
            );
    }

    private static void chest(Player p, Runnable open) {
        p.closeDialog();
        open.run();
    }

    // ------------------------------------------------------------------ chat moderation

    /** Swear filter and mutes: everything editable without touching a file. */
    public void chatModeration(Player p) {
        ChatFilter f = this.plugin.chatFilter();
        if (f == null) {
            return;
        }

        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        tiles.add(tile(Material.BARRIER, "<red><b>Blocked Words", "<white>" + f.blocked().size() + "<gray> words", () -> this.filterWords(p, true, 0), "Add or remove swear words.", "=word only matches the whole word."));
        tiles.add(tile(Material.LIME_DYE, "<green><b>Allowed Words", "<white>" + f.whitelist().size() + "<gray> words", () -> this.filterWords(p, false, 0), "Words that are never blocked,", "like class or assassin."));
        tiles.add(tile(Material.CLOCK, "<gold><b>Muted Players", "<white>" + f.activeMutes().size() + "<gray> muted now", () -> this.mutedPlayers(p), "Unmute or extend mutes."));
        tiles.add(tile(Material.BOOK, "<gold><b>Recent Offences", "<white>" + f.recent().size() + "<gray> logged", () -> this.offences(p), "What was blocked, by whom, and the mute."));
        tiles.add(tile(Material.SPYGLASS, "<aqua><b>Test a Message", "<gray>See what the filter does", () -> this.plugin.dialogs().text(p, LobbyDialogs.title("Chat Moderation", "Test a message"), "Message", "", 200, msg -> {
            boolean hit = f.swears(msg);
            p.sendMessage(Text.mm(hit ? "<red><b>Blocked:</b> <white>" + Text.esc(f.censor(msg)) : "<green><b>Allowed:</b> <white>" + Text.esc(msg)));
            this.chatModeration(p);
        }), "Nothing is sent and nobody is muted."));
        List<Component> header = List.of(
            Text.mm("<gray>Filter " + onOff(Cfg.bool(Opt.CHAT_FILTER)) + "  <dark_gray>│</dark_gray>  <gray>Action <white>" + Cfg.s(Opt.CHAT_FILTER_ACTION)
                + "  <dark_gray>│</dark_gray>  <gray>Auto-mute " + onOff(Cfg.bool(Opt.CHAT_FILTER_MUTE))),
            Text.mm("<gray>Mute steps <white>" + ChatFilter.steps().stream().map(m -> m + "m").collect(java.util.stream.Collectors.joining(" → "))
                + "</white>  <dark_gray>│</dark_gray>  <gray>Reset after <white>" + Cfg.i(Opt.CHAT_FILTER_RESET) + "h")
        );
        List<ActionButton> buttons = List.of(
            this.modToggle(p, "Swear Filter", Opt.CHAT_FILTER),
            this.modToggle(p, "Auto-Mute", Opt.CHAT_FILTER_MUTE),
            this.modToggle(p, "Alert Staff", Opt.CHAT_FILTER_NOTIFY),
            this.ui.btn(p, "<white>Action: <yellow>" + Cfg.s(Opt.CHAT_FILTER_ACTION), "BLOCK = not sent, CENSOR = sent as ****", () -> {
                Cfg.set(Opt.CHAT_FILTER_ACTION, "BLOCK".equals(Cfg.s(Opt.CHAT_FILTER_ACTION)) ? "CENSOR" : "BLOCK");
                Snd.click(p);
                this.chatModeration(p);
            }),
            this.ui.btn(p, "<white>Mute Steps…", "Minutes per offence, e.g. 2,10,30", () -> this.plugin.dialogs().text(
                p, LobbyDialogs.title("Chat Moderation", "Mute steps (minutes)"), "1st, 2nd, 3rd... offence", Cfg.s(Opt.CHAT_FILTER_STEPS), 64, v -> {
                    Cfg.set(Opt.CHAT_FILTER_STEPS, v.replaceAll("[^0-9,]", ""));
                    this.chatModeration(p);
                })),
            this.ui.btn(p, "<white>Reset Window…", "Hours until offences are forgotten", () -> this.plugin.dialogs().number(
                p, LobbyDialogs.title("Chat Moderation", "Offence reset"), "Hours", 1.0F, 168.0F, 1.0F, Cfg.i(Opt.CHAT_FILTER_RESET), v -> {
                    Cfg.set(Opt.CHAT_FILTER_RESET, Math.round(v));
                    this.chatModeration(p);
                })),
            this.ui.btn(p, "<white>Mute a Player…", "Name, time (10m, 2h, 1d, perm), reason", () -> this.plugin.dialogs().text(
                p, LobbyDialogs.title("Chat Moderation", "Mute a player"), "name time reason", "", 120, v -> {
                    p.performCommand("mute " + v.trim());
                    this.chatModeration(p);
                })),
            this.ui.btn(p, "<gray>« Admin Panel", null, () -> this.main(p))
        );
        this.ui.screen(p, LobbyDialogs.title("Chat Moderation", "Swear filter and mutes"), tiles, header, null, buttons, 2, this.ui.close(p));
    }

    private ActionButton modToggle(Player p, String name, Opt<Boolean> opt) {
        return this.ui.btn(p, "<white>" + name + " " + onOff(Cfg.bool(opt)), null, () -> {
            Cfg.set(opt, !Cfg.bool(opt));
            Snd.click(p);
            this.chatModeration(p);
        });
    }

    private void filterWords(Player p, boolean blocked, int page) {
        ChatFilter f = this.plugin.chatFilter();
        List<String> all = blocked ? f.blocked() : f.whitelist();
        int per = 24;
        int pages = Math.max(1, (all.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (String w : all.subList(pg * per, Math.min(all.size(), pg * per + per))) {
            List<Component> hover = List.of(Text.mm("<white>" + Text.esc(w)), Text.mm(w.startsWith("=") ? "<gray>Whole word only" : "<gray>Also inside longer words"), Component.empty(), Text.mm("<red>▶ Click to remove"));
            tiles.add(new LobbyDialogs.Tile(new ItemStack(blocked ? Material.RED_DYE : Material.LIME_DYE), Text.mm("<white>" + Text.esc(w)), Component.empty(), hover, () -> {
                if (blocked) {
                    f.removeBlocked(w);
                } else {
                    f.removeWhitelist(w);
                }

                Snd.click(p);
                this.filterWords(p, blocked, pg);
            }));
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(this.ui.btn(p, "<green>+ Add Word…", blocked ? "Start with = to only match the whole word" : null, () -> this.plugin.dialogs().text(
            p, LobbyDialogs.title("Chat Moderation", blocked ? "Block a word" : "Allow a word"), "Word (or several, separated by commas)", "", 200, v -> {
                for (String w : v.split(",")) {
                    if (blocked) {
                        f.addBlocked(w);
                    } else {
                        f.addWhitelist(w);
                    }
                }

                this.filterWords(p, blocked, pg);
            })));
        if (pg > 0) {
            buttons.add(this.ui.btn(p, "<white>‹ Page " + pg, null, () -> this.filterWords(p, blocked, pg - 1)));
        }

        if (pg < pages - 1) {
            buttons.add(this.ui.btn(p, "<white>Page " + (pg + 2) + " ›", null, () -> this.filterWords(p, blocked, pg + 1)));
        }

        buttons.add(this.ui.btn(p, "<gray>« Chat Moderation", null, () -> this.chatModeration(p)));
        this.ui.screen(
            p, LobbyDialogs.title("Chat Moderation", (blocked ? "Blocked" : "Allowed") + " words (page " + (pg + 1) + "/" + pages + ")"), tiles, List.of(),
            Text.mm("<gray>No words yet."), buttons, 4, this.ui.close(p)
        );
    }

    private void mutedPlayers(Player p) {
        ChatFilter f = this.plugin.chatFilter();
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (ChatFilter.Mute m : f.activeMutes()) {
            List<Component> hover = List.of(
                Text.mm("<white>" + Text.esc(m.name())),
                Text.mm("<gray>" + ChatFilter.fmt(m.left()) + " left"),
                Text.mm("<gray>Reason: <white>" + Text.esc(m.reason() == null ? "-" : m.reason())),
                Text.mm("<gray>By: <white>" + Text.esc(m.by() == null ? "-" : m.by())),
                Component.empty(),
                Text.mm("<green>▶ Click to unmute or extend")
            );
            tiles.add(new LobbyDialogs.Tile(new ItemStack(Material.PLAYER_HEAD), Text.mm("<white>" + Text.esc(m.name())), Text.mm("<gray>" + ChatFilter.fmt(m.left()) + " left"), hover, () -> this.plugin.dialogs().choice(
                p, LobbyDialogs.title("Muted Players", m.name()), "What to do", List.of(
                    new Dialogs.Choice("unmute", "Unmute"), new Dialogs.Choice("10", "+10 minutes"), new Dialogs.Choice("60", "+1 hour"), new Dialogs.Choice("1440", "+1 day")
                ), "unmute", v -> {
                    if (v.equals("unmute")) {
                        f.unmute(m.uuid());
                    } else {
                        f.mute(m.uuid(), m.name(), m.left() + Long.parseLong(v) * 60000L, m.reason(), p.getName());
                    }

                    this.mutedPlayers(p);
                })));
        }

        this.ui.screen(
            p, LobbyDialogs.title("Chat Moderation", "Muted players"), tiles, List.of(), Text.mm("<gray>Nobody is muted."),
            List.of(this.ui.btn(p, "<gray>« Chat Moderation", null, () -> this.chatModeration(p))), 3, this.ui.close(p)
        );
    }

    private void offences(Player p) {
        ChatFilter f = this.plugin.chatFilter();
        List<Component> lines = new ArrayList<>();
        java.text.SimpleDateFormat time = new java.text.SimpleDateFormat("HH:mm");

        for (ChatFilter.Offence o : f.recent().stream().limit(20).toList()) {
            lines.add(Text.mm("<dark_gray>" + time.format(new java.util.Date(o.at())) + " <white>" + Text.esc(o.name()) + " <gray>(" + Text.esc(o.where()) + ")"
                + (o.mutedFor() > 0L ? " <red>" + ChatFilter.fmt(o.mutedFor()) : "") + "<gray>: <white>" + Text.esc(f.censor(o.text()))));
        }

        this.ui.screen(
            p, LobbyDialogs.title("Chat Moderation", "Recent offences"), List.of(), lines.isEmpty() ? List.of(Text.mm("<gray>Nothing blocked yet.")) : lines, null,
            List.of(this.ui.btn(p, "<gray>« Chat Moderation", null, () -> this.chatModeration(p))), 1, this.ui.close(p)
        );
    }

    public void settings(Player p, Opt.Section section) {
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        if (section == null) {
            for (Opt.Section s : Opt.Section.values()) {
                long count = Opt.all().stream().filter(ox -> ox.gui && ox.section == s).count();
                if (count != 0L) {
                    tiles.add(tile(s.icon, "<gold><b>" + s.title, "<gray>" + count + " options", () -> this.settings(p, s)));
                }
            }

            this.ui
                .screen(
                    p,
                    LobbyDialogs.title("Settings", "Pick a category"),
                    tiles,
                    List.of(),
                    null,
                    List.of(this.ui.btn(p, "<gray>« Admin Panel", null, () -> this.main(p))),
                    1,
                    this.ui.close(p)
                );
        } else {
            for (Opt<?> o : Opt.all()) {
                if (o.gui && o.section == section) {
                    Object v = Cfg.get(o);
                    List<Component> hover = new ArrayList<>();
                    hover.add(Text.mm("<gold><b>" + Text.esc(o.name)));
                    hover.add(Text.mm("<gray>" + Text.esc(o.desc)));
                    hover.add(Component.empty());
                    if (!v.equals(o.def)) {
                        hover.add(Text.mm("<dark_gray>Default: " + Text.esc(String.valueOf(o.def))));
                    }

                    hover.add(Text.mm("<dark_gray>" + o.path));

                    hover.add(Text.mm(switch (o.type) {
                        case BOOL -> "<green>▶ Click to toggle";
                        case ENUM -> "<green>▶ Click for the next value";
                        default -> "<green>▶ Click to change";
                    }));
                    tiles.add(
                        new LobbyDialogs.Tile(
                            new ItemStack(o.icon), Text.mm("<gold><b>" + Text.esc(o.name)), Text.mm(value(o, v)), hover, () -> this.edit(p, o)
                        )
                    );
                }
            }

            this.ui
                .screen(
                    p,
                    LobbyDialogs.title("Settings", section.title),
                    tiles,
                    List.of(),
                    null,
                    List.of(
                        this.ui.btn(p, "<gray>« Categories", null, () -> this.settings(p, null)),
                        this.ui.btn(p, "<gray>« Admin Panel", null, () -> this.main(p))
                    ),
                    2,
                    this.ui.close(p)
                );
        }
    }

    private static String value(Opt<?> o, Object v) {
        return switch (o.type) {
            case BOOL -> Boolean.TRUE.equals(v) ? "<green><b>ON" : "<red><b>OFF";
            case STRING -> String.valueOf(v).isEmpty() ? "<dark_gray>(empty)" : "<white>" + Text.esc(String.valueOf(v));
            default -> "<white>" + Text.esc(String.valueOf(v));
        };
    }

    private void edit(Player p, Opt<?> o) {
        Object v = Cfg.get(o);
        Opt.Section back = o.section;
        switch (o.type) {
            case BOOL:
                Cfg.set(o, !Boolean.TRUE.equals(v));
                this.done(p, o, back);
                break;
            case ENUM:
                Enum<?>[] values = (Enum<?>[])o.enumClass.getEnumConstants();
                int i = 0;

                for (int j = 0; j < values.length; j++) {
                    if (values[j].name().equals(v)) {
                        i = j;
                    }
                }

                Cfg.set(o, values[(i + 1) % values.length].name());
                this.done(p, o, back);
                break;
            case STRING:
                this.plugin.dialogs().text(p, Text.mm("<gold><b>" + Text.esc(o.name)), o.name, String.valueOf(v), 256, s -> {
                    Cfg.set(o, s);
                    this.done(p, o, back);
                });
                break;
            case INT:
            case DOUBLE:
                double cur = ((Number)v).doubleValue();
                double steps = (o.max - o.min) / Math.max(1.0E-9, o.step);
                if (steps <= 400.0) {
                    this.plugin
                        .dialogs()
                        .number(p, Text.mm("<gold><b>" + Text.esc(o.name)), o.name, (float)o.min, (float)o.max, (float)o.step, (float)cur, f -> {
                            Cfg.set(o, o.type == Opt.Type.INT ? (Object)Math.round(f) : (Object)(double)f.floatValue());
                            this.done(p, o, back);
                        });
                } else {
                    this.plugin
                        .dialogs()
                        .text(p, Text.mm("<gold><b>" + Text.esc(o.name)), "Value (" + num(o, o.min) + " - " + num(o, o.max) + ")", num(o, cur), 20, s -> {
                            try {
                                double d = Double.parseDouble(s.trim().replace(',', '.'));
                                d = Math.max(o.min, Math.min(o.max, d));
                                Cfg.set(o, o.type == Opt.Type.INT ? (Object)(int)Math.round(d) : (Object)d);
                            } catch (NumberFormatException var7x) {
                                Msg.error(p, "admin.bad-number");
                            }

                            this.done(p, o, back);
                        });
                }
                break;
            default:
                p.closeDialog();
                new ConfigMenu(this.plugin, p, back).open();
        }
    }

    private static String num(Opt<?> o, double d) {
        return o.type == Opt.Type.INT ? String.valueOf(Math.round(d)) : String.format(Locale.US, "%.2f", d);
    }

    private void done(Player p, Opt<?> o, Opt.Section back) {
        ConfigMenu.applied(this.plugin, o);
        Snd.click(p);
        this.settings(p, back);
        if (o == Opt.DUEL_DEPTH || o == Opt.FFA_DEPTH || o == Opt.CRYSTAL_DEPTH || o == Opt.ARENA_WALLS || o == Opt.ARENA_PLANTS || o == Opt.ARENA_ORES) {
            int n = this.plugin.arenas().outdated().size();
            if (n > 0) {
                p.sendMessage(
                    Text.mm(
                        "<gold>⛏ "
                            + n
                            + " arena(s) use the old setting. <green><b><click:run_command:'/pvpadmin rebuild outdated'><hover:show_text:'<gray>Rebuild them now (same layouts)'>[APPLY TO ARENAS NOW]</hover></click></b> <dark_gray>(or it happens after the next restart)"
                    )
                );
            }
        }
    }
}
