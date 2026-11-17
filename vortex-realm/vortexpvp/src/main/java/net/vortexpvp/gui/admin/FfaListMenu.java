package net.vortexpvp.gui.admin;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Msg;
import net.vortexpvp.ffa.FfaArena;
import net.vortexpvp.gui.Menu;
import net.vortexpvp.gui.Ui;
import net.vortexpvp.gui.menus.KitSelectMenu;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.session.Session;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Cuboid;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Pos;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class FfaListMenu extends Menu {
    public FfaListMenu(VortexPvP plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Admin</b></gold> <dark_gray>» <gray>FFA Arenas");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        int[] slots = Ui.innerSlots(6);
        int i = 0;

        for (FfaArena a : this.plugin.ffa().all()) {
            if (i >= slots.length) {
                break;
            }

            Kit k = this.plugin.ffa().kitOf(a);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Kit: <white>" + (k == null ? "<red>missing" : Text.esc(k.displayName)));
            lore.add("<gray>Players: <white>" + a.players.size());
            lore.add("<gray>Spawns: <white>" + a.spawns.size());
            lore.add("<gray>Bounds: " + (a.bounds == null ? "<red>not set" : "<white>" + a.bounds));
            lore.add("<gray>Enabled: " + (a.enabled ? "<green>yes" : "<red>no"));
            String pack = this.plugin.packState(a.id);
            if (a.building) {
                lore.add("<yellow>Generating now" + (this.plugin.packProgress() == null ? "" : " <gray>(arena pack " + this.plugin.packProgress() + ")"));
            } else if ("queued".equals(pack)) {
                lore.add("<yellow>Waiting its turn in the arena pack " + this.plugin.packProgress());
            } else if (!a.enabled) {
                lore.add("<red>Off <gray>- Enable All (in the FFA list) switches it on");
            }

            lore.add("");
            lore.add("<yellow>▶ Click to edit");
            this.set(
                slots[i++],
                Items.of(a.iconBlock.isItem() ? a.iconBlock : Material.GRASS_BLOCK)
                    .name((a.ready() ? "<gold><b>" : "<red><b>") + Text.esc(a.displayName))
                    .lore(lore)
                    .build(),
                c -> new FfaListMenu.FfaEditMenu(this.plugin, this.viewer, a).open()
            );
        }

        this.set(
            48,
            Items.of(Material.LIME_CONCRETE).name("<green><b>Create FFA Arena Here").lore("<gray>Then add spawns and bounds.").build(),
            c -> this.plugin.dialogs().text(this.viewer, Text.mm("<gold><b>Create FFA Arena"), "Name", "", 32, name -> {
                if (!name.isBlank()) {
                    String id = ArenaListMenu.uniqueId(this.plugin, ArenaListMenu.slug(name), true);
                    String kit = this.plugin.kits().ffaKits().isEmpty() ? this.plugin.kits().ids().get(0) : this.plugin.kits().ffaKits().get(0).id;
                    FfaArena ax = this.plugin.ffa().create(id, name, this.viewer.getWorld(), kit);
                    new FfaListMenu.FfaEditMenu(this.plugin, this.viewer, ax).open();
                }
            })
        );
        this.set(
            50,
            Items.of(Material.CHERRY_SAPLING).name("<green><b>Generate Biome FFA Arena").build(),
            c -> ArenaListMenu.generatePrompt(this.plugin, this.viewer, true)
        );
        this.set(52, ArenaListMenu.enableAllButton(), c -> {
            ArenaListMenu.enableAll(this.plugin, this.viewer);
            this.redraw();
        });
        this.set(49, Ui.back(), c -> AdminMenu.openBest(this.plugin, this.viewer));
    }

    static final class FfaEditMenu extends Menu {
        private final FfaArena a;

        FfaEditMenu(VortexPvP plugin, Player viewer, FfaArena a) {
            super(plugin, viewer);
            this.a = a;
        }

        @Override
        protected Component title() {
            return Text.mm("<gold><b>FFA</b></gold> <dark_gray>» <gray>" + Text.esc(this.a.displayName));
        }

        @Override
        protected int rows() {
            return 5;
        }

        private void changed() {
            this.plugin.ffa().save();
            this.redraw();
        }

        @Override
        protected void draw() {
            this.border(Ui.FRAME);
            Kit kit = this.plugin.ffa().kitOf(this.a);
            this.set(
                4,
                Items.of(this.a.iconBlock.isItem() ? this.a.iconBlock : Material.GRASS_BLOCK)
                    .name((this.a.ready() ? "<green><b>" : "<red><b>") + Text.esc(this.a.displayName))
                    .lore(
                        "<gray>ID: <white>" + this.a.id,
                        "<gray>World: <white>" + this.a.worldName,
                        "<gray>Bounds: <white>" + (this.a.bounds == null ? "<red>not set" : this.a.bounds.toString()),
                        "<gray>Spawns: <white>" + this.a.spawns.size(),
                        "<gray>Snapshot: " + (this.a.snapshot == null ? "<yellow>none" : "<green>saved")
                    )
                    .build()
            );
            this.set(
                10,
                Items.of(Material.NAME_TAG).name("<gold><b>Rename").build(),
                c -> this.plugin.dialogs().text(this.viewer, Text.mm("<gold><b>Rename"), "Name", this.a.displayName, 32, n -> {
                    if (!n.isBlank()) {
                        this.a.displayName = n;
                    }

                    this.plugin.ffa().save();
                    new FfaListMenu.FfaEditMenu(this.plugin, this.viewer, this.a).open();
                })
            );
            this.set(
                11,
                Items.of(kit == null ? Material.BARRIER : kit.iconStack().getType())
                    .name("<gold><b>Kit: <white>" + (kit == null ? "none" : Text.esc(kit.displayName)))
                    .lore("<yellow>▶ Click to choose")
                    .hideAll()
                    .build(),
                c -> new KitSelectMenu(this.plugin, this.viewer, Text.mm("<gold><b>FFA Kit"), k -> k.enabled, k -> {
                    this.a.kitId = k.id;
                    if (!k.ffa) {
                        k.ffa = true;
                        this.plugin.kits().save();
                    }

                    this.plugin.ffa().save();
                    new FfaListMenu.FfaEditMenu(this.plugin, this.viewer, this.a).open();
                }, () -> new FfaListMenu.FfaEditMenu(this.plugin, this.viewer, this.a).open(), false).open()
            );
            this.set(
                12,
                Items.of(Material.RED_BED)
                    .name("<gold><b>Add Spawn Here")
                    .lore(
                        "<gray>Spawns: <white>" + this.a.spawns.size(), "", "<gray>Click: <green>add your position", "<gray>Shift-click: <red>clear all spawns"
                    )
                    .build(),
                type -> {
                    if (type.isShiftClick()) {
                        this.a.spawns.clear();
                    } else {
                        Location l = this.viewer.getLocation();
                        if (this.a.worldName != null && !this.a.worldName.equals(l.getWorld().getName())) {
                            Msg.error(this.viewer, "admin.wrong-world", "world", this.a.worldName);
                            return;
                        }

                        this.a.worldName = l.getWorld().getName();
                        this.a.spawns.add(Pos.of(l));
                        Msg.send(this.viewer, "admin.position-set");
                    }

                    this.changed();
                }
            );
            Session s = this.plugin.sessions().get(this.viewer);
            this.set(13, Items.of(Material.GOLDEN_AXE).name("<gold><b>Bounds From Wand").hideAll().build(), c -> {
                if (s.wandPos1 != null && s.wandPos2 != null && s.wandPos1.getWorld().equals(s.wandPos2.getWorld())) {
                    this.a.worldName = s.wandPos1.getWorld().getName();
                    this.a.bounds = Cuboid.of(s.wandPos1, s.wandPos2);
                    this.a.snapshot = null;
                    Msg.send(this.viewer, "admin.bounds-set", "size", this.a.bounds.sizeX() + "x" + this.a.bounds.sizeY() + "x" + this.a.bounds.sizeZ());
                    this.changed();
                } else {
                    Msg.error(this.viewer, "admin.need-wand");
                }
            });
            ItemStack held = this.viewer.getInventory().getItemInMainHand();
            this.set(14, Items.of(Material.GRASS_BLOCK).name("<gold><b>Icon From Held Block").build(), c -> {
                if (held.getType().isBlock() && !held.getType().isAir()) {
                    this.a.iconBlock = held.getType();
                    this.changed();
                } else {
                    Msg.error(this.viewer, "admin.hold-block");
                }
            });
            if (this.a.editing) {
                this.set(
                    15,
                    Items.of(Material.LIME_CONCRETE)
                        .name("<green><b>Save Block Changes")
                        .glint(true)
                        .lore("<gray>You are editing this arena.", "", "<gray>Click: <green>save", "<gray>Right-click: <red>discard changes")
                        .build(),
                    type -> {
                        this.viewer.closeInventory();
                        if (type.isRightClick()) {
                            this.plugin.arenaEditor().cancel(this.viewer, this.a);
                        } else {
                            this.plugin.arenaEditor().save(this.viewer, this.a);
                        }
                    }
                );
            } else {
                this.set(
                    15,
                    Items.of(Material.GOLDEN_PICKAXE)
                        .name("<gold><b>Edit Blocks")
                        .hideAll()
                        .lore(
                            "<gray>Add, remove or change blocks by hand.",
                            "",
                            "<gray>1. Click: <white>creative + teleport in",
                            "<gray>2. Build inside the orange outline",
                            "<gray>3. <green>/pvpadmin arena save <gray>or click again",
                            "",
                            "<dark_gray>Players inside go to the lobby.",
                            "",
                            "<gray>Shift-click: <white>just snapshot current blocks"
                        )
                        .build(),
                    type -> {
                        if (type.isShiftClick()) {
                            Msg.send(this.viewer, "admin.snapshot-started");
                            this.plugin.ffa().captureSnapshot(this.a).whenComplete((v, err) -> Tasks.ensureSync(() -> {
                                if (err == null) {
                                    this.a.handEdited = true;
                                    this.plugin.ffa().save();
                                }

                                Msg.send(this.viewer, err == null ? "admin.snapshot-done" : "admin.snapshot-failed", "arena", this.a.displayName);
                            }));
                        } else {
                            this.viewer.closeInventory();
                            this.plugin.arenaEditor().start(this.viewer, this.a);
                        }
                    }
                );
            }

            this.set(16, Items.of(Material.MOSSY_COBBLESTONE).name("<gold><b>Regenerate Now").build(), c -> {
                this.plugin.ffa().fullRegen(this.a);
                Msg.send(this.viewer, "admin.reset-started", "count", 1);
            });
            this.set(20, Ui.toggle(Material.LIME_DYE, "Enabled", this.a.enabled), c -> {
                this.a.enabled = !this.a.enabled;
                this.changed();
            });
            this.set(22, Items.of(Material.ENDER_PEARL).name("<gold><b>Teleport To A Spawn").build(), c -> {
                if (!this.a.spawns.isEmpty() && this.a.world() != null) {
                    this.viewer.closeInventory();
                    Tp.to(this.viewer, this.a.spawns.get(0).at(this.a.world()));
                } else {
                    Msg.error(this.viewer, "admin.not-set");
                }
            });
            this.set(
                24,
                Items.of(Material.TNT).name("<red><b>Delete").build(),
                c -> this.plugin
                    .dialogs()
                    .confirm(
                        this.viewer,
                        Text.mm("<red><b>Delete " + Text.esc(this.a.displayName) + "?"),
                        List.of(Text.mm("<gray>Players inside are sent to the lobby.")),
                        () -> {
                            this.plugin.ffa().delete(this.a);
                            new FfaListMenu(this.plugin, this.viewer).open();
                        }
                    )
            );
            this.set(40, Ui.back(), c -> new FfaListMenu(this.plugin, this.viewer).open());
        }
    }
}
