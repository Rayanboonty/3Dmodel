package net.vortexrealm.gui.admin;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.vortexrealm.arena.VanillaTerrain;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Msg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.gui.Menu;
import net.vortexrealm.gui.Ui;
import net.vortexrealm.session.Tp;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class WorldsMenu extends Menu {
    public WorldsMenu(VortexRealm plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Admin</b></gold> <dark_gray>» <gray>World Manager");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        List<World> worlds = new ArrayList<>(Bukkit.getWorlds());
        // temporary vanilla terrain worlds (arena generation) are not for admins to touch
        worlds.removeIf(w -> VanillaTerrain.isSourceWorld(w.getName()));
        int[] slots = Ui.innerSlots(6);
        String lobbyWorld = this.plugin.lobby().lobby().getWorld() == null ? "" : this.plugin.lobby().lobby().getWorld().getName();

        for (int i = 0; i < worlds.size() && i < slots.length; i++) {
            World w = worlds.get(i);
            boolean pvp = this.plugin.worlds().managed(w);
            boolean locked = w.getName().equals("practice_world") || w.getName().equals(lobbyWorld);

            Material icon = switch (w.getEnvironment()) {
                case NETHER -> Material.NETHERRACK;
                case THE_END -> Material.END_STONE;
                default -> Material.GRASS_BLOCK;
            };
            this.set(
                slots[i],
                Items.of(icon)
                    .name("<gold><b>" + Text.esc(w.getName()))
                    .lore(
                        "<gray>Type: " + (pvp ? "<green><b>PvP" : "<aqua><b>SMP / excluded"),
                        "<gray>Players: <white>" + w.getPlayers().size(),
                        "",
                        pvp ? "<gray>Players here get the PvP lobby, items" : "<gray>The plugin ignores this world;",
                        pvp ? "<gray>and kits." : "<gray>players keep their own inventory.",
                        "",
                        locked ? "<dark_gray>Locked (lobby / arena world)" : "<yellow>Click <gray>toggle PvP / SMP",
                        "<yellow>Shift-click <gray>teleport to spawn"
                    )
                    .glint(pvp)
                    .build(),
                type -> {
                    if (type.isShiftClick()) {
                        this.viewer.closeInventory();
                        Tp.to(this.viewer, w.getSpawnLocation());
                    } else if (locked) {
                        Msg.error(this.viewer, "admin.world-locked");
                    } else {
                        this.plugin.worlds().setExcluded(w.getName(), pvp);
                        Msg.send(this.viewer, pvp ? "admin.world-excluded" : "admin.world-included", "world", w.getName());

                        for (Player p : w.getPlayers()) {
                            this.plugin.syncWorldState(p);
                        }

                        this.redraw();
                    }
                }
            );
        }

        this.set(49, Ui.back(), c -> AdminMenu.openBest(this.plugin, this.viewer));
    }
}
