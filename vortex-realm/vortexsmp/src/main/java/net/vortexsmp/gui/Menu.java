package net.vortexsmp.gui;

import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.vortexsmp.VortexSMP;
import net.vortexsmp.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public abstract class Menu implements InventoryHolder {
    protected final VortexSMP plugin;
    protected final Player viewer;
    private final Map<Integer, Menu.Click> buttons = new HashMap<>();
    private Inventory inv;
    private boolean closedByCode;

    protected Menu(VortexSMP plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    protected abstract Component title();

    protected abstract int rows();

    protected abstract void draw();

    public final void open() {
        this.inv = Bukkit.createInventory(this, this.rows() * 9, this.title());
        this.draw();
        this.viewer.openInventory(this.inv);
    }

    public final void redraw() {
        if (this.inv != null) {
            this.buttons.clear();

            for (int i = 0; i < this.inv.getSize(); i++) {
                if (!this.editable(i)) {
                    this.inv.setItem(i, null);
                }
            }

            this.draw();
        }
    }

    @Override
    public Inventory getInventory() {
        return this.inv;
    }

    protected void set(int slot, ItemStack item, Menu.Click click) {
        if (slot >= 0 && slot < this.inv.getSize()) {
            this.inv.setItem(slot, item);
            if (click != null) {
                this.buttons.put(slot, click);
            } else {
                this.buttons.remove(slot);
            }
        }
    }

    protected void set(int slot, ItemStack item) {
        this.set(slot, item, null);
    }

    protected void fill(Material pane) {
        ItemStack f = Items.filler(pane);

        for (int i = 0; i < this.inv.getSize(); i++) {
            if (this.inv.getItem(i) == null && !this.editable(i)) {
                this.inv.setItem(i, f);
            }
        }
    }

    protected void border(Material pane) {
        ItemStack f = Items.filler(pane);
        int size = this.inv.getSize();

        for (int i = 0; i < size; i++) {
            int col = i % 9;
            if ((i < 9 || i >= size - 9 || col == 0 || col == 8) && this.inv.getItem(i) == null) {
                this.inv.setItem(i, f);
            }
        }
    }

    Menu.Click button(int slot) {
        return this.buttons.get(slot);
    }

    public boolean editable(int slot) {
        return false;
    }

    public boolean allowBottomClicks() {
        return false;
    }

    public boolean allowShiftInto() {
        return false;
    }

    public void afterEdit(InventoryClickEvent e) {
    }

    public void onClose() {
    }

    public final void closeQuietly() {
        this.closedByCode = true;
        this.viewer.closeInventory();
    }

    boolean closedByCode() {
        return this.closedByCode;
    }

    public Player viewer() {
        return this.viewer;
    }

    @FunctionalInterface
    public interface Click {
        void run(ClickType var1);
    }
}
