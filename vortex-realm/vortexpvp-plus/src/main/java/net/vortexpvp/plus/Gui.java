package net.vortexpvp.plus;

import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.gui.Menu;
import net.vortexpvp.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** A chest menu drawn by a lambda, so each system can open its screens in a few lines. */
public final class Gui extends Menu {
    private final String title;
    private final int rows;
    private final Consumer<Gui> drawer;
    public int page;

    private Gui(VortexPvP plugin, Player viewer, String title, int rows, Consumer<Gui> drawer) {
        super(plugin, viewer);
        this.title = title;
        this.rows = Math.max(1, Math.min(6, rows));
        this.drawer = drawer;
    }

    public static Gui open(VortexPvP plugin, Player viewer, String title, int rows, Consumer<Gui> drawer) {
        Gui g = new Gui(plugin, viewer, title, rows, drawer);
        g.open();
        return g;
    }

    @Override
    protected Component title() {
        return Text.mm(this.title);
    }

    @Override
    protected int rows() {
        return this.rows;
    }

    @Override
    protected void draw() {
        this.drawer.accept(this);
        this.fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    public Player viewer() {
        return this.viewer;
    }

    public void item(int slot, ItemStack item, Menu.Click click) {
        this.set(slot, item, click);
    }

    public void item(int slot, ItemStack item) {
        this.set(slot, item);
    }

    /** The inner slots of the menu (no border), in reading order. */
    public int[] inner() {
        int r = this.rows;
        int[] out = new int[Math.max(0, (r - 2) * 7)];
        int n = 0;
        for (int row = 1; row < r - 1; row++) {
            for (int c = 1; c < 8; c++) {
                out[n++] = row * 9 + c;
            }
        }

        return out;
    }
}
