package net.vortexsmp.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.vortexsmp.VortexSMP;
import net.vortexsmp.config.Cfg;
import net.vortexsmp.config.Opt;
import net.vortexsmp.util.Items;
import net.vortexsmp.util.Snd;
import net.vortexsmp.util.Tasks;
import net.vortexsmp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * /trade: a safe trade window between two players. Each sees their own offer on the left and the other's on the
 * right; both press Ready, and after a 3 second countdown the items swap. Changing anything un-readies both, and
 * closing the window or leaving gives everything back.
 */
public final class TradeManager implements Listener, TabExecutor {
    private static final int[] OWN = slots(0);
    private static final int[] OTHER = slots(5);
    private static final int READY = 45;
    private static final int CANCEL = 49;
    private static final int THEIRS = 53;
    private final VortexSMP plugin;
    private final Map<UUID, UUID> requests = new HashMap<>();
    private final Map<UUID, Long> requestAt = new HashMap<>();
    private final Map<UUID, Trade> trades = new HashMap<>();

    public TradeManager(VortexSMP plugin) {
        this.plugin = plugin;
    }

    private static int[] slots(int col0) {
        int[] s = new int[20];
        int i = 0;
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 4; c++) {
                s[i++] = r * 9 + col0 + c;
            }
        }

        return s;
    }

    private static int indexOf(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == v) {
                return i;
            }
        }

        return -1;
    }

    final class Side implements InventoryHolder {
        final Player player;
        final Inventory inv;
        boolean ready;
        Side other;
        final Trade trade;

        Side(Player player, Trade trade, String otherName) {
            this.player = player;
            this.trade = trade;
            this.inv = Bukkit.createInventory(this, 54, Text.mm("<gold><b>Trade</b> <dark_gray>» <gray>You <dark_gray>⇄ <white>" + Text.esc(otherName)));
            ItemStack pane = Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
            for (int r = 0; r < 5; r++) {
                this.inv.setItem(r * 9 + 4, pane);
            }

            for (int i = 45; i < 54; i++) {
                this.inv.setItem(i, pane);
            }
        }

        @Override
        public Inventory getInventory() {
            return this.inv;
        }

        List<ItemStack> offer() {
            List<ItemStack> out = new ArrayList<>();
            for (int s : OWN) {
                ItemStack it = this.inv.getItem(s);
                if (it != null && !it.isEmpty()) {
                    out.add(it.clone());
                }
            }

            return out;
        }
    }

    final class Trade {
        Side a;
        Side b;
        boolean done;
        int countdown = -1;
    }

    private void paint(Side s) {
        Side o = s.other;
        // the other side's offer, read-only, on the right
        List<ItemStack> theirs = new ArrayList<>();
        for (int sl : OWN) {
            theirs.add(o.inv.getItem(sl));
        }

        for (int i = 0; i < OTHER.length; i++) {
            ItemStack it = theirs.get(i);
            s.inv.setItem(OTHER[i], it == null ? null : it.clone());
        }

        s.inv.setItem(READY, Items.of(s.ready ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
            .name(s.ready ? "<green><b>✔ You are ready</b>" : "<red><b>Not ready</b>")
            .lore("<gray>Click when your offer is right.", s.trade.countdown >= 0 ? "<yellow>Trading in " + s.trade.countdown + "s..." : "")
            .build());
        s.inv.setItem(THEIRS, Items.of(o.ready ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE)
            .name(o.ready ? "<green><b>✔ " + Text.esc(o.player.getName()) + " is ready</b>" : "<gray>" + Text.esc(o.player.getName()) + " is not ready")
            .build());
        s.inv.setItem(CANCEL, Items.of(Material.BARRIER).name("<red><b>Cancel trade</b>").build());
    }

    private void changed(Trade t) {
        t.a.ready = false;
        t.b.ready = false;
        t.countdown = -1;
        Tasks.later(1L, () -> {
            if (!t.done) {
                this.paint(t.a);
                this.paint(t.b);
            }
        });
    }

    public void start(Player a, Player b) {
        Trade t = new Trade();
        t.a = new Side(a, t, b.getName());
        t.b = new Side(b, t, a.getName());
        t.a.other = t.b;
        t.b.other = t.a;
        this.trades.put(a.getUniqueId(), t);
        this.trades.put(b.getUniqueId(), t);
        this.paint(t.a);
        this.paint(t.b);
        a.openInventory(t.a.inv);
        b.openInventory(t.b.inv);
        Snd.play(a, "block.chest.open", 0.6F, 1.2F);
        Snd.play(b, "block.chest.open", 0.6F, 1.2F);
    }

    private void tick(Trade t) {
        if (t.done || !t.a.ready || !t.b.ready) {
            t.countdown = -1;
            return;
        }

        if (t.countdown < 0) {
            t.countdown = 3;
        }

        if (t.countdown == 0) {
            this.complete(t);
            return;
        }

        this.paint(t.a);
        this.paint(t.b);
        Snd.play(t.a.player, "block.note_block.hat", 0.7F, 1.0F + (3 - t.countdown) * 0.2F);
        Snd.play(t.b.player, "block.note_block.hat", 0.7F, 1.0F + (3 - t.countdown) * 0.2F);
        t.countdown--;
        Tasks.later(20L, () -> this.tick(t));
    }

    private void complete(Trade t) {
        t.done = true;
        List<ItemStack> fromA = t.a.offer();
        List<ItemStack> fromB = t.b.offer();
        for (int s : OWN) {
            t.a.inv.setItem(s, null);
            t.b.inv.setItem(s, null);
        }

        this.finish(t);
        give(t.a.player, fromB);
        give(t.b.player, fromA);
        for (Side s : new Side[]{t.a, t.b}) {
            s.player.sendMessage(Text.mm("<green><b>✔ Trade done</b> <gray>with <white>" + Text.esc(s.other.player.getName()) + "<gray>."));
            Snd.play(s.player, "entity.villager.yes", 0.8F, 1.2F);
        }

        this.plugin.getLogger().info("Trade " + t.a.player.getName() + " <-> " + t.b.player.getName() + ": " + describe(fromA) + " for " + describe(fromB));
    }

    private static String describe(List<ItemStack> items) {
        List<String> s = new ArrayList<>();
        for (ItemStack it : items) {
            s.add(it.getAmount() + "x" + it.getType().name().toLowerCase(Locale.ROOT));
        }

        return s.isEmpty() ? "nothing" : String.join(", ", s);
    }

    private static void give(Player p, List<ItemStack> items) {
        for (ItemStack it : items) {
            p.getInventory().addItem(it).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
    }

    /** Ends the trade: every item still offered goes back to its owner. */
    private void cancel(Trade t, String why) {
        if (t.done) {
            return;
        }

        t.done = true;
        List<ItemStack> backA = t.a.offer();
        List<ItemStack> backB = t.b.offer();
        for (int s : OWN) {
            t.a.inv.setItem(s, null);
            t.b.inv.setItem(s, null);
        }

        this.finish(t);
        give(t.a.player, backA);
        give(t.b.player, backB);
        for (Side s : new Side[]{t.a, t.b}) {
            if (s.player.isOnline()) {
                s.player.sendMessage(Text.mm("<red>Trade cancelled" + (why == null ? "." : ": " + why)));
            }
        }
    }

    private void finish(Trade t) {
        this.trades.remove(t.a.player.getUniqueId());
        this.trades.remove(t.b.player.getUniqueId());
        Tasks.later(1L, () -> {
            for (Side s : new Side[]{t.a, t.b}) {
                if (s.player.isOnline() && s.player.getOpenInventory().getTopInventory().getHolder(false) == s) {
                    s.player.closeInventory();
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Side s) || s.trade.done) {
            return;
        }

        Trade t = s.trade;
        int raw = e.getRawSlot();
        if (e.getClick() == ClickType.DOUBLE_CLICK || e.getAction() == InventoryAction.COLLECT_TO_CURSOR || e.getClick() == ClickType.NUMBER_KEY
            || e.getClick() == ClickType.SWAP_OFFHAND) {
            e.setCancelled(true);
            return;
        }

        if (raw >= 54) {
            // from your own inventory: shift-click puts the item into your offer
            if (e.isShiftClick()) {
                e.setCancelled(true);
                ItemStack it = e.getCurrentItem();
                if (it != null && !it.isEmpty()) {
                    for (int sl : OWN) {
                        if (s.inv.getItem(sl) == null) {
                            s.inv.setItem(sl, it.clone());
                            e.setCurrentItem(null);
                            this.changed(t);
                            break;
                        }
                    }
                }
            }

            return;
        }

        if (indexOf(OWN, raw) >= 0) {
            if (e.isShiftClick()) {
                e.setCancelled(true);
                ItemStack it = e.getCurrentItem();
                if (it != null && !it.isEmpty()) {
                    s.player.getInventory().addItem(it.clone()).values().forEach(left -> s.player.getWorld().dropItemNaturally(s.player.getLocation(), left));
                    s.inv.setItem(raw, null);
                }
            }

            this.changed(t);
            return;
        }

        e.setCancelled(true);
        if (raw == READY) {
            s.ready = !s.ready;
            Snd.play(s.player, "ui.button.click", 0.7F, s.ready ? 1.4F : 0.8F);
            this.paint(s);
            this.paint(s.other);
            if (s.ready && s.other.ready) {
                this.tick(t);
            }
        } else if (raw == CANCEL) {
            this.cancel(t, s.player.getName() + " cancelled");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Side s && !s.trade.done) {
            for (int raw : e.getRawSlots()) {
                if (raw < 54 && indexOf(OWN, raw) < 0) {
                    e.setCancelled(true);
                    return;
                }
            }

            this.changed(s.trade);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof Side s && !s.trade.done) {
            this.cancel(s.trade, Text.plain(Component.text(s.player.getName())) + " closed the window");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Trade t = this.trades.get(e.getPlayer().getUniqueId());
        if (t != null) {
            this.cancel(t, e.getPlayer().getName() + " left");
        }

        this.requests.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!Cfg.bool(Opt.TRADE)) {
            p.sendMessage(Text.mm("<red>Trading is turned off on this server."));
            return true;
        }

        if (args.length == 0) {
            p.sendMessage(Text.mm("<gray>Usage: <yellow>/trade <player></yellow> <dark_gray>• <yellow>/trade accept"));
            return true;
        }

        if (this.trades.containsKey(p.getUniqueId())) {
            p.sendMessage(Text.mm("<red>You are already trading."));
            return true;
        }

        if (args[0].equalsIgnoreCase("accept")) {
            UUID from = null;
            for (Map.Entry<UUID, UUID> r : this.requests.entrySet()) {
                if (r.getValue().equals(p.getUniqueId()) && System.currentTimeMillis() - this.requestAt.getOrDefault(r.getKey(), 0L) < 60000L) {
                    from = r.getKey();
                }
            }

            Player a = from == null ? null : Bukkit.getPlayer(from);
            if (a == null) {
                p.sendMessage(Text.mm("<red>No trade request to accept."));
            } else if (a.getWorld() != p.getWorld() || a.getLocation().distance(p.getLocation()) > 32.0) {
                p.sendMessage(Text.mm("<red>You need to be within 32 blocks of " + Text.esc(a.getName()) + " to trade."));
            } else if (this.plugin.combatTag() != null && (this.plugin.combatTag().tagged(a) || this.plugin.combatTag().tagged(p))) {
                p.sendMessage(Text.mm("<red>No trading in combat."));
            } else {
                this.requests.remove(from);
                this.start(a, p);
            }

            return true;
        }

        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null || t.equals(p) || !p.canSee(t)) {
            p.sendMessage(Text.mm("<red>That player is not online."));
        } else if (t.getWorld() != p.getWorld() || t.getLocation().distance(p.getLocation()) > 32.0) {
            p.sendMessage(Text.mm("<red>You need to be within 32 blocks of " + Text.esc(t.getName()) + " to trade."));
        } else {
            this.requests.put(p.getUniqueId(), t.getUniqueId());
            this.requestAt.put(p.getUniqueId(), System.currentTimeMillis());
            p.sendMessage(Text.mm("<gray>Trade request sent to <white>" + Text.esc(t.getName()) + "<gray>."));
            t.sendMessage(Text.mm("<gold>⇄ <white>" + Text.esc(p.getName()) + " <gray>wants to trade. <click:run_command:'/trade accept'><green><b>[Accept]</b></green></click>"));
            Snd.play(t, "block.note_block.pling", 0.7F, 1.4F);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("accept");
            Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
        }

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    public void shutdown() {
        for (Trade t : new ArrayList<>(this.trades.values())) {
            this.cancel(t, "the server is stopping");
        }
    }
}
