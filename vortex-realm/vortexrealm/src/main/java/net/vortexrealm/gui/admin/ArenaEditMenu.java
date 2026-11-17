package net.vortexrealm.gui.admin;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.arena.Arena;
import net.vortexrealm.arena.ArenaInstance;
import net.vortexrealm.arena.ArenaTheme;
import net.vortexrealm.config.Msg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.gui.Menu;
import net.vortexrealm.gui.Ui;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.session.Session;
import net.vortexrealm.session.Tp;
import net.vortexrealm.util.Cuboid;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Pos;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public final class ArenaEditMenu extends Menu {
    private final Arena a;

    public ArenaEditMenu(VortexRealm plugin, Player viewer, Arena a) {
        super(plugin, viewer);
        this.a = a;
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Arena</b></gold> <dark_gray>» <gray>" + Text.esc(this.a.displayName));
    }

    @Override
    protected int rows() {
        return 6;
    }

    private void changed() {
        this.plugin.arenas().save();
        this.redraw();
    }

    private static String pos(Pos p) {
        return p == null ? "<red>not set" : "<white>" + p.bx() + ", " + p.by() + ", " + p.bz();
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        List<String> info = new ArrayList<>();
        info.add("<gray>ID: <white>" + this.a.id);
        info.add("<gray>World: <white>" + this.a.worldName);
        info.add("<gray>Bounds: <white>" + (this.a.bounds == null ? "<red>not set" : this.a.bounds.toString()));
        info.add("<gray>Spawn 1: " + pos(this.a.spawn1));
        info.add("<gray>Spawn 2: " + pos(this.a.spawn2));
        info.add("<gray>Center: " + pos(this.a.center));
        info.add("<gray>Snapshot: " + (this.a.snapshot != null ? "<green>saved" : "<yellow>none (tracked reset only)"));
        info.add("<gray>Instances: <white>" + this.a.instances.size() + " <gray>(" + this.a.freeInstances() + " free)");

        for (String prob : this.a.problems()) {
            info.add("<red>⚠ " + prob);
        }

        this.set(
            4,
            Items.of(this.a.iconBlock.isItem() ? this.a.iconBlock : Material.GRASS_BLOCK)
                .name((this.a.ready() ? "<green><b>" : "<red><b>") + Text.esc(this.a.displayName))
                .lore(info)
                .build()
        );
        this.set(
            10,
            Items.of(Material.NAME_TAG).name("<gold><b>Rename").lore("<gray>Shown in the map ban phase.").build(),
            c -> this.plugin.dialogs().text(this.viewer, Text.mm("<gold><b>Rename Arena"), "Display name", this.a.displayName, 32, n -> {
                if (!n.isBlank()) {
                    this.a.displayName = n;
                }

                new ArenaEditMenu(this.plugin, this.viewer, this.a).open();
                this.plugin.arenas().save();
            })
        );
        this.set(11, this.posItem(Material.RED_BANNER, "Spawn 1", this.a.spawn1), c -> this.setPos(1));
        this.set(12, this.posItem(Material.BLUE_BANNER, "Spawn 2", this.a.spawn2), c -> this.setPos(2));
        this.set(13, this.posItem(Material.BEACON, "Center / Spectators", this.a.center), c -> this.setPos(3));
        Session s = this.plugin.sessions().get(this.viewer);
        this.set(
            14,
            Items.of(Material.GOLDEN_AXE)
                .name("<gold><b>Bounds From Wand")
                .lore(
                    "<gray>Pos 1: " + loc(s.wandPos1),
                    "<gray>Pos 2: " + loc(s.wandPos2),
                    "",
                    "<yellow>▶ Click to use the wand selection",
                    "<gray>(get a wand in the main admin menu)"
                )
                .hideAll()
                .build(),
            c -> this.boundsFromWand(s)
        );
        this.set(
            15,
            Items.of(Material.STRUCTURE_VOID)
                .name("<gold><b>Auto Bounds")
                .lore("<gray>Box around both spawns with a", "<gray>horizontal margin you choose.")
                .build(),
            c -> this.autoBounds()
        );
        this.set(
            16,
            Items.of(Material.CHEST)
                .name("<gold><b>Allowed Kits")
                .lore("<gray>Current: <white>" + (this.a.kits.isEmpty() ? "all kits" : String.join(", ", this.a.kits)), "", "<yellow>▶ Click to choose")
                .build(),
            c -> new ArenaEditMenu.KitToggleMenu(this.plugin, this.viewer, this.a).open()
        );
        ItemStack held = this.viewer.getInventory().getItemInMainHand();
        this.set(
            19,
            Items.of(this.a.iconBlock.isItem() ? this.a.iconBlock : Material.GRASS_BLOCK)
                .name("<gold><b>Map Icon")
                .lore("<gray>The block that floats in the map ban.", "", "<yellow>▶ Click while holding a block")
                .build(),
            c -> {
                if (held.getType().isBlock() && !held.getType().isAir()) {
                    this.a.iconBlock = held.getType();
                    this.changed();
                } else {
                    Msg.error(this.viewer, "admin.hold-block");
                }
            }
        );
        this.set(
            20,
            Items.of(Material.PAINTING)
                .name("<gold><b>Theme: <white>" + this.a.theme.display)
                .lore("<gray>Used for display only.", "<yellow>▶ Click to cycle")
                .build(),
            c -> {
                ArenaTheme[] v = ArenaTheme.values();
                this.a.theme = v[(this.a.theme.ordinal() + 1) % v.length];
                this.changed();
            }
        );
        this.set(
            21,
            Items.of(Material.SCAFFOLDING)
                .name("<gold><b>Start Animation: <white>" + this.a.startMode)
                .lore("<gray>DEFAULT uses the global setting.", "<gray>ELEVATOR, GATES or FREEZE.", "", "<yellow>▶ Click to cycle")
                .build(),
            c -> {
                String[] modes = new String[]{"DEFAULT", "ELEVATOR", "GATES", "FREEZE"};
                int i = 0;

                for (int j = 0; j < modes.length; j++) {
                    if (modes[j].equalsIgnoreCase(this.a.startMode)) {
                        i = j;
                    }
                }

                this.a.startMode = modes[(i + 1) % modes.length];
                this.changed();
            }
        );
        this.set(
            22,
            Items.of(Material.IRON_CHAIN)
                .name("<gold><b>Elevator Depth: <white>" + (this.a.elevatorDepth > 0 ? this.a.elevatorDepth : "default"))
                .lore("<yellow>▶ Click to set")
                .build(),
            c -> this.plugin
                .dialogs()
                .number(this.viewer, Text.mm("<gold><b>Elevator Depth"), "Blocks (0 = default)", 0.0F, 20.0F, 1.0F, Math.max(0, this.a.elevatorDepth), v -> {
                    this.a.elevatorDepth = Math.round(v) <= 0 ? -1 : Math.round(v);
                    this.plugin.arenas().save();
                    new ArenaEditMenu(this.plugin, this.viewer, this.a).open();
                })
        );
        this.set(
            23,
            Items.of(Material.LADDER)
                .name("<gold><b>Build Height: <white>" + (this.a.buildHeight > 0 ? this.a.buildHeight : "default"))
                .lore("<gray>Blocks above the spawns players may build.", "<yellow>▶ Click to set")
                .build(),
            c -> this.plugin
                .dialogs()
                .number(this.viewer, Text.mm("<gold><b>Build Height"), "Blocks (0 = default)", 0.0F, 128.0F, 1.0F, Math.max(0, this.a.buildHeight), v -> {
                    this.a.buildHeight = Math.round(v) <= 0 ? -1 : Math.round(v);
                    this.plugin.arenas().save();
                    new ArenaEditMenu(this.plugin, this.viewer, this.a).open();
                })
        );
        this.set(
            24,
            Items.of(Material.STRUCTURE_BLOCK)
                .name("<gold><b>Reset Mode: <white>" + this.a.resetMode)
                .lore(
                    "<gray>TRACKED: undo recorded changes (fast)",
                    "<gray>SNAPSHOT: compare every block",
                    "<gray>BOTH: tracked between rounds,",
                    "<gray>full check after the match (safest)",
                    "",
                    "<yellow>▶ Click to cycle"
                )
                .build(),
            c -> {
                String[] modes = new String[]{"DEFAULT", Opt.ResetMode.TRACKED.name(), Opt.ResetMode.SNAPSHOT.name(), Opt.ResetMode.BOTH.name()};
                int i = 0;

                for (int j = 0; j < modes.length; j++) {
                    if (modes[j].equalsIgnoreCase(this.a.resetMode)) {
                        i = j;
                    }
                }

                this.a.resetMode = modes[(i + 1) % modes.length];
                this.changed();
            }
        );
        this.set(25, Ui.toggle(Material.LIME_DYE, "Enabled", this.a.enabled, "Disabled arenas are never picked."), c -> {
            this.a.enabled = !this.a.enabled;
            this.changed();
        });
        this.set(
            28,
            Items.of(this.a.gateMaterial.isItem() ? this.a.gateMaterial : Material.OAK_FENCE)
                .name("<gold><b>Gates (" + this.a.gates.size() + ")")
                .lore(
                    "<gray>Blocks placed during the countdown",
                    "<gray>and opened on FIGHT.",
                    "",
                    "<gray>Click: <white>add gate from wand selection",
                    "<gray>Right-click: <white>auto cages around spawns",
                    "<gray>Shift-click: <red>remove all gates",
                    "<gray>Drop key (Q) while holding a block: <white>set material"
                )
                .build(),
            type -> this.gates(type, s, held)
        );
        if (this.a.editing) {
            this.set(
                29,
                Items.of(Material.LIME_CONCRETE)
                    .name("<green><b>Save Block Changes")
                    .glint(true)
                    .lore("<gray>You are editing this arena.", "", "<gray>Click: <green>save + update every copy", "<gray>Right-click: <red>discard changes")
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
                29,
                Items.of(Material.GOLDEN_PICKAXE)
                    .name("<gold><b>Edit Blocks")
                    .hideAll()
                    .lore(
                        "<gray>Add, remove or change any block",
                        "<gray>of this arena by hand.",
                        "",
                        "<gray>1. Click: <white>creative + teleport in",
                        "<gray>2. Build inside the orange outline",
                        "<gray>3. <green>/pvpadmin arena save <gray>or click again",
                        "",
                        "<dark_gray>Copies are updated on save. No one",
                        "<dark_gray>can play it while you edit.",
                        "",
                        "<gray>Shift-click: <white>just snapshot current blocks"
                    )
                    .build(),
                type -> {
                    if (type.isShiftClick()) {
                        Msg.send(this.viewer, "admin.snapshot-started");
                        this.plugin.arenas().captureSnapshot(this.a).whenComplete((v, err) -> Tasks.ensureSync(() -> {
                            if (err == null) {
                                this.a.handEdited = true;
                                this.plugin.arenas().save();
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

        this.set(
            30,
            Items.of(Material.CHEST_MINECART)
                .name("<gold><b>Copies: <white>" + (this.a.instances.size() - 1))
                .lore(
                    "<gray>More copies = more duels at once.",
                    "",
                    "<gray>Click: <green>+1 copy",
                    "<gray>Shift-click: <green>+5 copies",
                    "<gray>Right-click: <red>remove unused copies"
                )
                .build(),
            type -> {
                if (type.isRightClick() && !type.isShiftClick()) {
                    int n = this.plugin.arenas().removeCopies(this.a);
                    Msg.send(this.viewer, "admin.copies-removed", "count", n);
                    this.changed();
                } else {
                    int n = type.isShiftClick() ? 5 : 1;
                    if (this.a.bounds == null) {
                        Msg.error(this.viewer, "admin.need-bounds");
                    } else {
                        Msg.send(this.viewer, "admin.copies-started", "count", n);
                        this.plugin
                            .arenas()
                            .addCopies(this.a, n)
                            .whenComplete(
                                (v, err) -> Tasks.ensureSync(() -> Msg.send(this.viewer, err == null ? "admin.copies-done" : "admin.copies-failed", "count", n))
                            );
                        this.redraw();
                    }
                }
            }
        );
        this.set(
            31,
            Items.of(Material.MOSSY_COBBLESTONE).name("<gold><b>Reset All Free Instances").lore("<gray>Repairs every copy that isn't in use.").build(),
            c -> {
                int n = 0;

                for (ArenaInstance i : this.a.instances) {
                    if (i.state == ArenaInstance.State.FREE || i.state == ArenaInstance.State.DISABLED) {
                        i.state = ArenaInstance.State.IN_USE;
                        this.plugin.arenas().release(i);
                        n++;
                    }
                }

                Msg.send(this.viewer, "admin.reset-started", "count", n);
            }
        );
        this.set(
            32,
            Items.of(Material.ENDER_PEARL).name("<gold><b>Teleport").lore("<gray>Click: <white>spawn 1", "<gray>Right-click: <white>center").build(),
            type -> {
                Location l = type.isRightClick() ? this.a.instances.get(0).center() : this.a.instances.get(0).spawn1();
                if (l != null && l.getWorld() != null) {
                    this.viewer.closeInventory();
                    Tp.to(this.viewer, l);
                } else {
                    Msg.error(this.viewer, "admin.not-set");
                }
            }
        );
        this.set(
            34,
            Items.of(Material.TNT).name("<red><b>Delete Arena").lore("<gray>Removes the arena and its copies", "<gray>from the rotation.").build(),
            c -> this.plugin
                .dialogs()
                .confirm(
                    this.viewer, Text.mm("<red><b>Delete " + Text.esc(this.a.displayName) + "?"), List.of(Text.mm("<gray>This cannot be undone.")), () -> {
                        for (ArenaInstance i : this.a.instances) {
                            if (i.state == ArenaInstance.State.IN_USE) {
                                Msg.error(this.viewer, "admin.arena-in-use");
                                return;
                            }
                        }

                        this.plugin.arenas().delete(this.a);
                        Msg.send(this.viewer, "admin.arena-deleted", "arena", this.a.displayName);
                        new ArenaListMenu(this.plugin, this.viewer).open();
                    }
                )
        );
        this.set(49, Ui.back(), c -> new ArenaListMenu(this.plugin, this.viewer).open());
    }

    private static String loc(Location l) {
        return l == null ? "<red>not set" : "<white>" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    private ItemStack posItem(Material m, String label, Pos p) {
        return Items.of(m).name("<gold><b>Set " + label).lore("<gray>Current: " + pos(p), "", "<yellow>▶ Click to set to where you stand").hideAll().build();
    }

    private void setPos(int which) {
        Location l = this.viewer.getLocation();
        if (this.a.worldName != null && !this.a.worldName.equals(l.getWorld().getName())) {
            Msg.error(this.viewer, "admin.wrong-world", "world", this.a.worldName);
        } else {
            this.a.worldName = l.getWorld().getName();
            this.plugin.arenas().syncTemplateWorld(this.a);
            Pos p = Pos.of(l.getBlock().getLocation().add(0.5, 0.0, 0.5)).withYaw(Math.round(l.getYaw() / 45.0F) * 45.0F);
            switch (which) {
                case 1:
                    this.a.spawn1 = p;
                    break;
                case 2:
                    this.a.spawn2 = p;
                    break;
                default:
                    this.a.center = Pos.of(l);
            }

            Msg.send(this.viewer, "admin.position-set");
            this.changed();
        }
    }

    private void boundsFromWand(Session s) {
        if (s.wandPos1 != null && s.wandPos2 != null) {
            if (!s.wandPos1.getWorld().equals(s.wandPos2.getWorld())) {
                Msg.error(this.viewer, "admin.wand-worlds");
            } else if (this.a.worldName != null && !this.a.worldName.equals(s.wandPos1.getWorld().getName())) {
                Msg.error(this.viewer, "admin.wrong-world", "world", this.a.worldName);
            } else {
                this.a.worldName = s.wandPos1.getWorld().getName();
                this.plugin.arenas().syncTemplateWorld(this.a);
                this.a.bounds = Cuboid.of(s.wandPos1, s.wandPos2);
                this.plugin.arenas().deleteSnapshot(this.a);
                Msg.send(this.viewer, "admin.bounds-set", "size", this.a.bounds.sizeX() + "x" + this.a.bounds.sizeY() + "x" + this.a.bounds.sizeZ());
                this.changed();
            }
        } else {
            Msg.error(this.viewer, "admin.need-wand");
        }
    }

    private void autoBounds() {
        if (this.a.spawn1 != null && this.a.spawn2 != null) {
            this.plugin.dialogs().number(this.viewer, Text.mm("<gold><b>Auto Bounds"), "Margin around spawns", 5.0F, 80.0F, 1.0F, 20.0F, v -> {
                int m = Math.round(v);
                int minX = (int)Math.floor(Math.min(this.a.spawn1.x(), this.a.spawn2.x())) - m;
                int maxX = (int)Math.floor(Math.max(this.a.spawn1.x(), this.a.spawn2.x())) + m;
                int minZ = (int)Math.floor(Math.min(this.a.spawn1.z(), this.a.spawn2.z())) - m;
                int maxZ = (int)Math.floor(Math.max(this.a.spawn1.z(), this.a.spawn2.z())) + m;
                int minY = (int)Math.floor(Math.min(this.a.spawn1.y(), this.a.spawn2.y())) - 20;
                int maxY = (int)Math.floor(Math.max(this.a.spawn1.y(), this.a.spawn2.y())) + 40;
                this.a.bounds = Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ);
                this.plugin.arenas().deleteSnapshot(this.a);
                this.plugin.arenas().save();
                Msg.send(this.viewer, "admin.bounds-set", "size", this.a.bounds.sizeX() + "x" + this.a.bounds.sizeY() + "x" + this.a.bounds.sizeZ());
                new ArenaEditMenu(this.plugin, this.viewer, this.a).open();
            });
        } else {
            Msg.error(this.viewer, "admin.need-spawns");
        }
    }

    private void gates(ClickType type, Session s, ItemStack held) {
        if (type != ClickType.DROP && type != ClickType.CONTROL_DROP) {
            if (type.isShiftClick()) {
                this.a.gates.clear();
                this.changed();
            } else if (!type.isRightClick()) {
                if (s.wandPos1 != null && s.wandPos2 != null) {
                    Cuboid c = Cuboid.of(s.wandPos1, s.wandPos2);
                    if (c.volume() > 2000L) {
                        Msg.error(this.viewer, "admin.gate-too-big");
                    } else {
                        this.a.gates.add(c);
                        Msg.send(this.viewer, "admin.gate-added", "count", this.a.gates.size());
                        this.changed();
                    }
                } else {
                    Msg.error(this.viewer, "admin.need-wand");
                }
            } else if (this.a.spawn1 != null && this.a.spawn2 != null) {
                for (Pos p : List.of(this.a.spawn1, this.a.spawn2)) {
                    int x = p.bx();
                    int y = p.by();
                    int z = p.bz();
                    this.a.gates.add(Cuboid.of(x - 2, y, z - 2, x + 2, y + 2, z - 2));
                    this.a.gates.add(Cuboid.of(x - 2, y, z + 2, x + 2, y + 2, z + 2));
                    this.a.gates.add(Cuboid.of(x - 2, y, z - 1, x - 2, y + 2, z + 1));
                    this.a.gates.add(Cuboid.of(x + 2, y, z - 1, x + 2, y + 2, z + 1));
                }

                if ("DEFAULT".equals(this.a.startMode)) {
                    this.a.startMode = "GATES";
                }

                Msg.send(this.viewer, "admin.cages-added");
                this.changed();
            } else {
                Msg.error(this.viewer, "admin.need-spawns");
            }
        } else {
            if (held.getType().isBlock() && !held.getType().isAir()) {
                this.a.gateMaterial = held.getType();
                this.changed();
            } else {
                Msg.error(this.viewer, "admin.hold-block");
            }
        }
    }

    static final class KitToggleMenu extends Menu {
        private final Arena a;

        KitToggleMenu(VortexRealm plugin, Player viewer, Arena a) {
            super(plugin, viewer);
            this.a = a;
        }

        @Override
        protected Component title() {
            return Text.mm("<gold><b>Arena Kits</b></gold> <dark_gray>» <gray>" + Text.esc(this.a.displayName));
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

            for (Kit k : this.plugin.kits().sorted()) {
                if (i >= slots.length) {
                    break;
                }

                boolean on = this.a.kits.isEmpty() || this.a.kits.contains(k.id);
                this.set(
                    slots[i++],
                    Items.of(k.iconStack())
                        .name(k.color + "<b>" + Text.esc(k.displayName))
                        .lore(on ? "<green>Allowed" : "<red>Not allowed", "", "<yellow>▶ Click to toggle")
                        .hideAll()
                        .glint(on)
                        .build(),
                    c -> {
                        if (this.a.kits.isEmpty()) {
                            for (Kit other : this.plugin.kits().all()) {
                                this.a.kits.add(other.id);
                            }
                        }

                        if (!this.a.kits.remove(k.id)) {
                            this.a.kits.add(k.id);
                        }

                        if (this.a.kits.size() >= this.plugin.kits().all().size()) {
                            this.a.kits.clear();
                        }

                        this.plugin.arenas().save();
                        this.redraw();
                    }
                );
            }

            this.set(48, Items.of(Material.LIME_CONCRETE).name("<green><b>Allow All").build(), c -> {
                this.a.kits.clear();
                this.plugin.arenas().save();
                this.redraw();
            });
            this.set(49, Ui.back(), c -> new ArenaEditMenu(this.plugin, this.viewer, this.a).open());
        }
    }
}
