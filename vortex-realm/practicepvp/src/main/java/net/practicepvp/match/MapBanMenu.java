package net.practicepvp.match;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.arena.Arena;
import net.practicepvp.gui.Menu;
import net.practicepvp.gui.Ui;
import net.practicepvp.util.Items;
import net.practicepvp.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

final class MapBanMenu extends Menu {
    private final PreMatch pm;

    MapBanMenu(PracticePlugin plugin, Player viewer, PreMatch pm) {
        super(plugin, viewer);
        this.pm = pm;
    }

    @Override
    protected Component title() {
        return Text.mm("<red><b>Ban a Map</b> <dark_gray>» <gray>" + this.pm.bansLeft(this.viewer) + " ban(s) left");
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void draw() {
        this.fill(Ui.FRAME);
        List<Arena> maps = this.pm.mapList();
        int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 4, 22};

        for (int i = 0; i < maps.size() && i < slots.length; i++) {
            Arena a = maps.get(i);
            boolean banned = this.pm.isBanned(a);
            Material icon = banned ? Material.RED_STAINED_GLASS : (a.iconBlock.isItem() ? a.iconBlock : Material.GRASS_BLOCK);
            this.set(
                slots[i],
                Items.of(icon)
                    .name(banned ? "<red><st>" + Text.esc(PreMatch.mapName(a)) : "<gold><b>" + Text.esc(PreMatch.mapName(a)))
                    .lore(banned ? new String[]{"<dark_red><b>BANNED"} : new String[]{"<gray>Biome: <white>" + a.theme.display, "", "<red>▶ Click to ban"})
                    .hideAll()
                    .build(),
                c -> {
                    if (!this.pm.banPhase()) {
                        this.viewer.closeInventory();
                    } else {
                        this.pm.banFromMenu(this.viewer, a);
                        if (this.pm.bansLeft(this.viewer) <= 0) {
                            this.viewer.closeInventory();
                        } else {
                            this.redraw();
                        }
                    }
                }
            );
        }
    }
}
