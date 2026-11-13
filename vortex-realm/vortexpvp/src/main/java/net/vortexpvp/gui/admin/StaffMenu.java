package net.vortexpvp.gui.admin;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.dialog.Dialogs;
import net.vortexpvp.gui.Menu;
import net.vortexpvp.gui.Ui;
import net.vortexpvp.staff.StaffManager;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class StaffMenu extends Menu {
    private final boolean picking;

    public StaffMenu(VortexPvP plugin, Player viewer) {
        this(plugin, viewer, false);
    }

    private StaffMenu(VortexPvP plugin, Player viewer, boolean picking) {
        super(plugin, viewer);
        this.picking = picking;
    }

    @Override
    protected Component title() {
        return Text.mm(this.picking ? "<gold><b>Pick New Staff" : "<gold><b>PvP Staff");
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
        StaffManager sm = this.plugin.staff();
        if (this.picking) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (i >= slots.length) {
                    break;
                }

                if (sm.roleOf(o) == null) {
                    this.set(
                        slots[i++],
                        Items.of(Material.PLAYER_HEAD).skull(o).name("<white><b>" + Text.esc(o.getName())).lore("", "<yellow>▶ Click to choose a role").build(),
                        c -> this.chooseRole(o)
                    );
                }
            }

            if (i == 0) {
                this.set(22, Ui.info(Material.CLOCK, "<gray>Nobody to add", "<dark_gray>Every online player is staff."));
            }

            this.set(49, Ui.back(), c -> new StaffMenu(this.plugin, this.viewer).open());
        } else {
            this.set(
                4,
                Items.of(Material.GOLDEN_HELMET)
                    .name("<gold><b>PvP Staff")
                    .lore(
                        "<aqua><b>HELPER</b> <gray>staff chat, spectate any match",
                        "<blue><b>MOD</b> <gray>+ end matches, send to lobby, lobby fly",
                        "<red><b>ADMIN</b> <gray>+ full /pvpadmin access",
                        "",
                        "<gray>Staff use <yellow>/staff</yellow> and <yellow>/sc</yellow>."
                    )
                    .hideAll()
                    .build()
            );

            for (StaffManager.Member m : sm.all()) {
                if (i >= slots.length) {
                    break;
                }

                boolean on = Bukkit.getPlayer(m.uuid()) != null;
                this.set(
                    slots[i++],
                    Items.of(Material.PLAYER_HEAD)
                        .skull(Bukkit.getOfflinePlayer(m.uuid()))
                        .name(m.role().badge() + " <white>" + Text.esc(m.name()))
                        .lore(
                            on ? "<green>● Online" : "<dark_gray>● Offline",
                            "",
                            "<yellow>Click <gray>change role → " + m.role().next().badge(),
                            "<red>Shift-click <gray>remove from staff"
                        )
                        .build(),
                    type -> {
                        if (type.isShiftClick()) {
                            sm.remove(m.uuid());
                        } else {
                            sm.set(Bukkit.getOfflinePlayer(m.uuid()), m.role().next());
                        }

                        Snd.click(this.viewer);
                        this.redraw();
                    }
                );
            }

            this.set(
                48,
                Items.of(Material.LIME_DYE).name("<green><b>Add Staff").lore("<gray>Pick an online player.", "", "<yellow>▶ Click").build(),
                c -> new StaffMenu(this.plugin, this.viewer, true).open()
            );
            this.set(49, Ui.back(), c -> AdminMenu.openBest(this.plugin, this.viewer));
            this.set(
                50,
                Items.of(Material.NAME_TAG)
                    .name("<green><b>Add By Name")
                    .lore("<gray>For players who already joined", "<gray>the server once.", "", "<yellow>▶ Click")
                    .build(),
                c -> this.plugin.dialogs().text(this.viewer, Text.mm("<gold><b>Add Staff"), "Player name", "", 16, name -> {
                    OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(name.trim());
                    if (off == null) {
                        this.viewer.sendMessage(Text.mm("<red>Unknown player: " + Text.esc(name)));
                    } else {
                        this.chooseRole(off);
                    }
                })
            );
        }
    }

    private void chooseRole(OfflinePlayer target) {
        List<Dialogs.Choice> roles = new ArrayList<>();

        for (StaffManager.Role r : StaffManager.Role.values()) {
            roles.add(new Dialogs.Choice(r.name(), r.badge()));
        }

        this.plugin.dialogs().choice(this.viewer, Text.mm("<gold><b>Staff Role"), "Role", roles, StaffManager.Role.HELPER.name(), v -> {
            this.plugin.staff().set(target, StaffManager.Role.valueOf(v));
            Snd.play(this.viewer, "entity.player.levelup", 0.6F, 1.4F);
            new StaffMenu(this.plugin, this.viewer).open();
        });
    }
}
