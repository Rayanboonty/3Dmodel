package net.vortexpvp.gui.menus;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.arena.Arena;
import net.vortexpvp.gui.Menu;
import net.vortexpvp.gui.Ui;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.kit.KitCategory;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class DuelMenu extends Menu {
    private static final int[] ROUNDS = new int[]{1, 2, 3};
    private final Player target;
    private int roundsIndex;
    private int mapIndex;

    public DuelMenu(VortexPvP plugin, Player viewer, Player target) {
        super(plugin, viewer);
        this.target = target;
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Duel</b></gold> <dark_gray>» <gray>" + Text.esc(this.target.getName()));
    }

    @Override
    protected int rows() {
        return 6;
    }

    private List<Arena> maps() {
        List<Arena> out = new ArrayList<>();

        for (Arena a : this.plugin.arenas().all()) {
            if (a.ready()) {
                out.add(a);
            }
        }

        return out;
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        List<Arena> maps = this.maps();
        if (this.mapIndex > maps.size()) {
            this.mapIndex = 0;
        }

        Arena map = this.mapIndex == 0 ? null : maps.get(this.mapIndex - 1);
        int[] slots = Ui.fit((int)this.plugin.kits().sorted().stream().filter(kx -> kx.enabled && kx.hasContents()).count(), 6);
        int i = 0;

        for (KitCategory cat : KitCategory.values()) {
            for (Kit k : this.plugin.kits().sorted()) {
                if (k.category == cat && k.enabled && k.hasContents() && i < slots.length) {
                    boolean mapOk = map == null || map.allows(k);
                    boolean hasArena = !this.plugin.arenas().forKit(k).isEmpty();
                    List<String> lore = new ArrayList<>();

                    for (String d : k.description) {
                        lore.add("<gray>" + Text.esc(d));
                    }

                    lore.add("");
                    lore.add("<gray>First to: <gold>" + (ROUNDS[this.roundsIndex] == 0 ? k.rounds() : ROUNDS[this.roundsIndex]));
                    if (!hasArena) {
                        lore.add("<red>No arena supports this kit.");
                    } else if (!mapOk) {
                        lore.add("<red>The selected map doesn't allow this kit.");
                    } else {
                        lore.add("<yellow>▶ Click to send the duel");
                    }

                    this.set(slots[i++], Items.of(k.iconStack()).name(k.color + "<b>" + Text.esc(k.displayName)).lore(lore).hideAll().build(), c -> {
                        if (hasArena && mapOk) {
                            this.viewer.closeInventory();
                            int r = ROUNDS[this.roundsIndex] == 0 ? k.rounds() : ROUNDS[this.roundsIndex];
                            this.plugin.duels().send(this.viewer, this.target, k, r, map);
                        }
                    });
                }
            }
        }

        String rLabel = ROUNDS[this.roundsIndex] == 0 ? "Kit default" : "First to " + ROUNDS[this.roundsIndex];
        this.set(47, Items.of(Material.TARGET).name("<gold><b>Rounds: <white>" + rLabel).lore("<yellow>▶ Click to change").build(), c -> {
            this.roundsIndex = (this.roundsIndex + 1) % ROUNDS.length;
            this.redraw();
        });
        this.set(
            51,
            Items.of(map == null ? Material.MAP : map.iconBlock)
                .name("<gold><b>Map: <white>" + (map == null ? "Random" : Text.esc(map.displayName)))
                .lore("<gray>Click: next map", "<gray>Right-click: previous map")
                .build(),
            c -> {
                int n = maps.size() + 1;
                this.mapIndex = Math.floorMod(this.mapIndex + (c.isRightClick() ? -1 : 1), n);
                this.redraw();
            }
        );
        this.set(49, Ui.close(), c -> this.viewer.closeInventory());
    }
}
