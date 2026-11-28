package net.vortexsmp.gui;

import java.util.logging.Level;
import net.vortexsmp.VortexSMP;
import net.vortexsmp.util.Snd;
import net.vortexsmp.util.Tasks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class MenuListener implements Listener {
    private final VortexSMP plugin;
    private final java.util.Map<java.util.UUID, Long> clicks = new java.util.HashMap<>();

    public MenuListener(VortexSMP plugin) {
        this.plugin = plugin;
    }

    private static Menu menuOf(Inventory top) {
        return top != null && top.getHolder(false) instanceof Menu m ? m : null;
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onClick(InventoryClickEvent e) {
        Menu menu = menuOf(e.getView().getTopInventory());
        if (menu != null && e.getWhoClicked() instanceof Player p) {
            int var10 = e.getRawSlot();
            int topSize = e.getView().getTopInventory().getSize();
            boolean inTop = var10 >= 0 && var10 < topSize;
            if (inTop) {
                if (!menu.editable(var10)) {
                    e.setCancelled(true);
                    Menu.Click click = menu.button(var10);
                    if (click != null) {
                        long now = System.currentTimeMillis();
                        Long last = this.clicks.get(p.getUniqueId());
                        if (last == null || now - last >= 120L) {
                            this.clicks.put(p.getUniqueId(), now);
                            Snd.click(p);
                            ClickType type = e.getClick();
                            Tasks.sync(() -> {
                                if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder(false) == menu) {
                                    try {
                                        click.run(type);
                                    } catch (RuntimeException var6x) {
                                        this.plugin.getLogger().log(Level.SEVERE, "Menu button failed in " + menu.getClass().getSimpleName(), (Throwable)var6x);
                                        p.closeInventory();
                                    }
                                }
                            });
                        }
                    }
                } else if (e.getClick() != ClickType.SWAP_OFFHAND
                    && e.getAction() != InventoryAction.CLONE_STACK
                    && e.getAction() != InventoryAction.COLLECT_TO_CURSOR) {
                    Tasks.later(0L, () -> menu.afterEdit(e));
                } else {
                    e.setCancelled(true);
                }
            } else if (e.getClick() == ClickType.SWAP_OFFHAND || e.getAction() == InventoryAction.CLONE_STACK) {
                e.setCancelled(true);
            } else if (!menu.allowBottomClicks()) {
                e.setCancelled(true);
            } else {
                InventoryAction a = e.getAction();
                if (a == InventoryAction.MOVE_TO_OTHER_INVENTORY && menu.allowShiftInto()) {
                    Tasks.later(0L, () -> menu.afterEdit(e));
                } else if (a != InventoryAction.MOVE_TO_OTHER_INVENTORY
                    && a != InventoryAction.COLLECT_TO_CURSOR
                    && a != InventoryAction.DROP_ALL_CURSOR
                    && a != InventoryAction.DROP_ONE_CURSOR
                    && a != InventoryAction.DROP_ALL_SLOT
                    && a != InventoryAction.DROP_ONE_SLOT
                    && a != InventoryAction.CLONE_STACK
                    && e.getClick() != ClickType.SWAP_OFFHAND) {
                    Tasks.later(0L, () -> menu.afterEdit(e));
                } else {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onDrag(InventoryDragEvent e) {
        Menu menu = menuOf(e.getView().getTopInventory());
        if (menu != null) {
            int topSize = e.getView().getTopInventory().getSize();

            for (int raw : e.getRawSlots()) {
                if (raw < topSize && !menu.editable(raw)) {
                    e.setCancelled(true);
                    return;
                }

                if (raw >= topSize && !menu.allowBottomClicks()) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onClose(InventoryCloseEvent e) {
        Menu menu = menuOf(e.getInventory());
        if (menu != null && !menu.closedByCode()) {
            try {
                menu.onClose();
            } catch (RuntimeException var4) {
                this.plugin.getLogger().log(Level.SEVERE, "Menu close handler failed in " + menu.getClass().getSimpleName(), (Throwable)var4);
            }
        }
    }
}
