package net.practicepvp.gui.admin;

import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.arena.Arena;
import net.practicepvp.arena.ArenaInstance;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Msg;
import net.practicepvp.config.Opt;
import net.practicepvp.gui.Menu;
import net.practicepvp.gui.Ui;
import net.practicepvp.session.PlayerState;
import net.practicepvp.session.Session;
import net.practicepvp.session.Tp;
import net.practicepvp.util.Items;
import net.practicepvp.util.Locs;
import net.practicepvp.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AdminMenu extends Menu {
    public AdminMenu(PracticePlugin plugin, Player viewer) {
        super(plugin, viewer);
    }

    public static void openBest(PracticePlugin plugin, Player p) {
        if (plugin.dialogs().useDialog(p)) {
            p.closeInventory();
            plugin.dialogs().admin().main(p);
        } else {
            new AdminMenu(plugin, p).open();
        }
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>PvP Admin");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
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

        this.set(
            4,
            Items.of(Material.NETHER_STAR)
                .name("<gold><b>PracticePvP <gray>v" + this.plugin.getPluginMeta().getVersion())
                .lore(
                    "<gray>Online: <white>" + this.plugin.getServer().getOnlinePlayers().size(),
                    "<gray>Matches: <white>" + this.plugin.matches().active().size(),
                    "<gray>Queued: <white>" + this.plugin.queues().total(),
                    "<gray>FFA players: <white>" + this.plugin.ffa().playing(),
                    "<gray>Arena instances: <white>" + free + " free / " + instances,
                    "<gray>Block jobs pending: <white>" + this.plugin.jobs().pending(),
                    "<gray>Lobby: <white>" + (this.plugin.lobby().lobbySet() ? Locs.pretty(this.plugin.lobby().lobby()) : "<red>not set (using world spawn)")
                )
                .glint(true)
                .build()
        );
        this.set(
            10,
            Items.of(Material.GRASS_BLOCK)
                .name("<gold><b>Duel Arenas")
                .lore("<gray>Create, edit, copy and reset", "<gray>duel maps.", "", "<yellow>▶ Click to open")
                .build(),
            c -> new ArenaListMenu(this.plugin, this.viewer).open()
        );
        this.set(
            11,
            Items.of(Material.NETHERITE_AXE)
                .name("<gold><b>FFA Arenas")
                .lore("<gray>Free-for-all arenas, spawns, regen.", "", "<yellow>▶ Click to open")
                .hideAll()
                .build(),
            c -> new FfaListMenu(this.plugin, this.viewer).open()
        );
        this.set(
            12,
            Items.of(Material.DIAMOND_SWORD)
                .name("<gold><b>Kits")
                .lore("<gray>Contents (with armor), icons, rules,", "<gray>ranked / FFA availability.", "", "<yellow>▶ Click to open")
                .hideAll()
                .build(),
            c -> new KitListMenu(this.plugin, this.viewer).open()
        );
        this.set(
            13,
            Items.of(Material.COMPARATOR)
                .name("<gold><b>Settings")
                .lore("<gray>Every option of the plugin:", "<gray>matches, ranks, queues, FFA, visuals...", "", "<yellow>▶ Click to open")
                .build(),
            c -> new ConfigMenu(this.plugin, this.viewer, null).open()
        );
        this.set(
            14,
            Items.of(Material.FILLED_MAP)
                .name("<gold><b>World Manager")
                .lore("<gray>Choose which worlds are PvP and", "<gray>which are SMP (separate inventories).", "", "<yellow>▶ Click to open")
                .hideAll()
                .build(),
            c -> new WorldsMenu(this.plugin, this.viewer).open()
        );
        this.set(
            15,
            Items.of(Material.ENDER_EYE).name("<gold><b>Live Matches").lore("<gray>Spectate or end running matches.", "", "<yellow>▶ Click to open").build(),
            c -> new MatchesMenu(this.plugin, this.viewer).open()
        );
        this.set(
            16,
            Items.of(Material.PLAYER_HEAD)
                .name("<gold><b>Players")
                .lore("<gray>Look up a player: ranks,", "<gray>reset stats.", "", "<yellow>▶ Click to search")
                .build(),
            c -> PlayerAdminMenu.prompt(this.plugin, this.viewer)
        );
        this.set(
            19,
            Items.of(Material.OAK_SAPLING)
                .name("<green><b>Generate Duel Arena")
                .lore(
                    "<gray>Builds a biome arena (Plains,",
                    "<gray>Badlands Mountains, Desert...)",
                    "<gray>with copies, ready to play.",
                    "",
                    "<yellow>▶ Click to configure"
                )
                .build(),
            c -> ArenaListMenu.generatePrompt(this.plugin, this.viewer, false)
        );
        this.set(
            20,
            Items.of(Material.CHERRY_SAPLING)
                .name("<green><b>Generate FFA Arena")
                .lore("<gray>Builds a biome FFA arena with", "<gray>spawn points for a kit.", "", "<yellow>▶ Click to configure")
                .build(),
            c -> ArenaListMenu.generatePrompt(this.plugin, this.viewer, true)
        );
        this.set(
            21,
            Items.of(Material.RED_BED)
                .name("<gold><b>Set Lobby Here")
                .lore(
                    "<gray>Current: <white>" + (this.plugin.lobby().lobbySet() ? Locs.pretty(this.plugin.lobby().lobby()) : "not set"),
                    "",
                    "<yellow>▶ Click to set to your position"
                )
                .build(),
            c -> {
                if (!this.plugin.worlds().managed(this.viewer.getWorld())) {
                    Msg.error(this.viewer, "admin.lobby-excluded-world");
                } else {
                    this.plugin.lobby().setLobby(this.viewer.getLocation());
                    Msg.send(this.viewer, "admin.lobby-set");
                    this.redraw();
                }
            }
        );
        this.set(22, Items.of(Material.ENDER_PEARL).name("<gold><b>Teleport to Lobby").build(), c -> Tp.to(this.viewer, this.plugin.lobby().lobby()));
        this.set(
            23,
            Items.of(Material.GOLDEN_AXE)
                .name("<gold><b>Region Wand")
                .lore(
                    "<gray>Left-click block: <white>position 1",
                    "<gray>Right-click block: <white>position 2",
                    "<gray>Used for arena bounds and gates.",
                    "",
                    "<yellow>▶ Click to get one"
                )
                .hideAll()
                .build(),
            c -> giveWand(this.viewer)
        );
        Session s = this.plugin.sessions().get(this.viewer);
        this.set(24, Ui.toggle(Material.BRICKS, "Build Mode", s.buildMode, "Creative mode + build in the lobby", "and arenas (you only)."), c -> {
            s.buildMode = !s.buildMode;
            Msg.send(this.viewer, s.buildMode ? "admin.build-on" : "admin.build-off");
            if (this.plugin.sessions().state(this.viewer) == PlayerState.LOBBY) {
                this.plugin.lobby().sendToLobby(this.viewer);
            }

            this.redraw();
        });
        this.set(25, Ui.toggle(Material.BARRIER, "Maintenance", Cfg.bool(Opt.MAINTENANCE), "Blocks queues, duels and FFA", "for non-admins."), c -> {
            Cfg.set(Opt.MAINTENANCE, !Cfg.bool(Opt.MAINTENANCE));
            this.redraw();
        });
        this.set(28, Ui.toggle(Material.NETHERITE_SWORD, "Ranked Queues", Cfg.bool(Opt.RANKED_ENABLED)), c -> {
            Cfg.set(Opt.RANKED_ENABLED, !Cfg.bool(Opt.RANKED_ENABLED));
            this.redraw();
        });
        this.set(29, Ui.toggle(Material.DIAMOND_SWORD, "Unranked Queues", Cfg.bool(Opt.UNRANKED_ENABLED)), c -> {
            Cfg.set(Opt.UNRANKED_ENABLED, !Cfg.bool(Opt.UNRANKED_ENABLED));
            this.redraw();
        });
        this.set(30, Ui.toggle(Material.NETHER_STAR, "FFA", Cfg.bool(Opt.FFA_ENABLED)), c -> {
            Cfg.set(Opt.FFA_ENABLED, !Cfg.bool(Opt.FFA_ENABLED));
            if (!Cfg.bool(Opt.FFA_ENABLED)) {
                this.plugin.ffa().kickAll();
            }

            this.redraw();
        });
        this.set(31, Ui.toggle(Material.CAKE, "Parties", Cfg.bool(Opt.PARTY_ENABLED)), c -> {
            Cfg.set(Opt.PARTY_ENABLED, !Cfg.bool(Opt.PARTY_ENABLED));
            this.redraw();
        });
        this.set(
            32,
            Items.of(Material.OAK_SIGN)
                .name("<gold><b>Leaderboard Hologram")
                .lore(
                    "<gray>Click: place one here (choose kit)",
                    "<gray>Shift-click: remove the nearest one",
                    "<gray>Holograms: <white>" + this.plugin.holograms().count()
                )
                .build(),
            type -> {
                if (type.isShiftClick()) {
                    Msg.send(this.viewer, this.plugin.holograms().removeNearest(this.viewer.getLocation()) ? "admin.hologram-removed" : "admin.hologram-none");
                    this.redraw();
                } else {
                    this.viewer.closeInventory();
                    this.plugin.holograms().prompt(this.viewer);
                }
            }
        );
        this.set(
            33,
            Items.of(Material.WRITABLE_BOOK)
                .name("<gold><b>Reload Files")
                .lore("<gray>Re-reads config.yml, messages.yml,", "<gray>hotbar.yml and kits.yml.", "", "<yellow>▶ Click to reload")
                .build(),
            c -> {
                this.plugin.reloadFiles();
                Msg.send(this.viewer, "admin.reloaded");
                this.redraw();
            }
        );
        this.set(
            34,
            Items.of(Material.CHEST).name("<gold><b>Save Everything").lore("<gray>Writes arenas, kits, FFA and", "<gray>all player data now.").build(),
            c -> {
                this.plugin.saveAll();
                Msg.send(this.viewer, "admin.saved");
            }
        );
        this.set(
            40,
            Items.of(Material.GOLDEN_HELMET)
                .name("<gold><b>PvP Staff")
                .lore(
                    "<gray>Choose who is staff: Helper,",
                    "<gray>Mod or Admin. Staff: <white>" + this.plugin.staff().all().size(),
                    "",
                    "<yellow>▶ Click to open"
                )
                .hideAll()
                .build(),
            c -> {
                if (!this.viewer.isOp()) {
                    this.viewer.sendMessage(Text.mm("<red>Only operators can choose PvP staff."));
                } else {
                    new StaffMenu(this.plugin, this.viewer).open();
                }
            }
        );
        this.set(49, Ui.close(), c -> this.viewer.closeInventory());
    }

    public static void giveWand(Player p) {
        p.getInventory()
            .addItem(
                new ItemStack[]{
                    Items.of(Material.GOLDEN_AXE)
                        .name("<gold><b>Region Wand")
                        .lore("<gray>Left-click: position 1", "<gray>Right-click: position 2")
                        .tag(Items.WAND, "wand")
                        .glint(true)
                        .hideAll()
                        .build()
                }
            );
        Msg.send(p, "admin.wand-given");
    }
}
